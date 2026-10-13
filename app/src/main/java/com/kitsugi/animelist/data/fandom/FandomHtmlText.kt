package com.kitsugi.animelist.data.fandom

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Fandom HTML parçalarını sade metne çevirir (saf fonksiyonlar, Jsoup).
 *
 *  - [inline]: bilgi kutusu değerleri gibi kısa parçalar; liste maddeleri ve `<br>` → " · ".
 *  - [blocks]: bölüm gövdesi; başlık / paragraf / madde / alıntı blokları.
 *
 * Kaynak işaretleri (`[1]`), düzenleme bağlantıları, furigana (`<rt>`), tablolar,
 * gezinti kutuları ve bakım uyarıları atılır.
 */
object FandomHtmlText {

    private const val INLINE_SEPARATOR = " · "

    private const val JUNK_SELECTOR =
        "sup.reference, .reference, .mw-ref, sup[id^=cite_ref], .mw-editsection, " +
            "style, script, noscript, rt, rp, .noprint, .error, .printfooter, .mw-cite-backlink"

    private const val SECTION_JUNK_SELECTOR = JUNK_SELECTOR +
        ", ol.references, .mw-references-wrap, .references, .navbox, .toc, #toc, table, .notice, " +
        ".mbox, .ambox, .portable-infobox, aside, figure, .thumb, .gallery, .wikia-gallery, " +
        ".wds-tabs__wrapper, .mw-collapsible-toggle, .dablink, .hatnote, .mw-empty-elt, " +
        "img, audio, video, iframe, .reference-text"

    private val REF_MARKERS = Regex(
        "\\[\\s*(?:\\d+|[a-z]|note\\s*\\d+|citation needed|kaynak belirtilmeli)\\s*]",
        RegexOption.IGNORE_CASE
    )
    private val HORIZONTAL_SPACE = Regex("[ \\t\\u00A0\\u2009\\u200A]+")
    private val REPEATED_SEPARATORS = Regex("(\\s*·\\s*)+")

    private val INLINE_TAGS = setOf(
        "a", "b", "i", "span", "em", "strong", "small", "sup", "sub", "font", "abbr", "code",
        "s", "u", "q", "cite", "mark", "time", "big", "bdi", "bdo", "kbd", "var", "wbr", "ruby",
        "label", "nobr", "tt", "del", "ins"
    )

    private val BLOCKISH_INLINE_TAGS = setOf("li", "p", "div", "dd", "dt", "tr", "ul", "ol", "dl", "blockquote")

    // ── Kısa (satır içi) metin ───────────────────────────────────────────────

    fun inline(html: String?): String {
        if (html.isNullOrBlank()) return ""
        if (html.indexOf('<') < 0 && html.indexOf('&') < 0) return tidyInline(html)
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(JUNK_SELECTOR).remove()
        return inlineOf(body)
    }

    /** Extracts real internal article links while retaining the row/list context for role labels. */
    fun wikiLinks(
        html: String?,
        defaultWikiId: String,
        role: FandomRelationType = FandomRelationType.RELATED,
        language: String? = null
    ): List<FandomWikiLink> {
        if (html.isNullOrBlank()) return emptyList()
        val baseUrl = FandomUrls.baseUrl(defaultWikiId).orEmpty()
        val body = Jsoup.parseBodyFragment(html, baseUrl).body()
        body.select(
            JUNK_SELECTOR + ", .navbox, .toc, #toc, .portable-infobox, .wds-tabs__wrapper, " +
                ".mw-editsection, .dablink, .hatnote, .wikia-gallery, .gallery, .mw-empty-elt"
        ).remove()
        val links = body.select("a[href]").mapNotNull { anchor ->
            if (anchor.hasClass("new") || anchor.attr("data-redlink").equals("1")) return@mapNotNull null
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val target = FandomUrls.parseArticleUrl(href, defaultWikiId) ?: return@mapNotNull null
            val label = inline(anchor.html()).ifBlank { target.second }
            val row = anchor.closest("tr")
            val cell = anchor.closest("td, th")
            val rowHeader = row?.children()?.filter { it.normalName() == "th" }.orEmpty()
            val columnHeader = if (rowHeader.size == 1) {
                rowHeader.first()?.text()
            } else {
                val table = row?.closest("table")
                val headerRow = table?.select("tr")?.firstOrNull { candidate ->
                    candidate.children().any { it.normalName() == "th" }
                }
                val columnIndex = cell?.siblingIndex()
                val headerCells = headerRow?.children()?.filter { it.normalName() in setOf("th", "td") }.orEmpty()
                columnIndex?.let { headerCells.getOrNull(it)?.text() }
            }
            val contextElement = row ?: anchor.closest("li, dd, p") ?: anchor
            val context = inline(contextElement.text())
                .takeIf { it.isNotBlank() && it != label }
                ?.take(360)
            val explicitRole = columnHeader?.let(FandomRelationClassifier::forInfoboxLabel)
                ?.takeUnless { it == FandomRelationType.RELATED }
            val contextualRole = context?.let(FandomRelationClassifier::forInfoboxLabel)
                ?.takeUnless { it == FandomRelationType.RELATED }
            FandomWikiLink(
                wikiId = target.first,
                pageTitle = target.second,
                label = label.take(200),
                role = explicitRole ?: contextualRole ?: role,
                language = columnHeader?.let(FandomRelationClassifier::languageHint)
                    ?: context?.let(FandomRelationClassifier::languageHint)
                    ?: language
                    ?: FandomUrls.languageOf(target.first),
                context = context
            )
        }
        return links.distinctBy {
            "${it.wikiId}|${FandomNameVariants.normalizeForCompare(it.pageTitle)}|${it.role}|${it.language.orEmpty()}"
        }
    }

    private fun inlineOf(element: Element): String {
        val sb = StringBuilder()
        appendInline(element, sb)
        return tidyInline(sb.toString())
    }

    private fun appendInline(node: Node, sb: StringBuilder) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> sb.append(child.text())
                is Element -> {
                    val tag = child.normalName()
                    when {
                        tag == "br" -> appendSeparator(sb)
                        tag in BLOCKISH_INLINE_TAGS -> {
                            appendSeparator(sb)
                            appendInline(child, sb)
                            appendSeparator(sb)
                        }
                        else -> appendInline(child, sb)
                    }
                }
            }
        }
    }

    private fun appendSeparator(sb: StringBuilder) {
        val trimmed = sb.trimEnd()
        if (trimmed.isEmpty() || trimmed.endsWith("·")) return
        sb.setLength(trimmed.length)
        sb.append(INLINE_SEPARATOR)
    }

    private fun tidyInline(raw: String): String {
        var text = REF_MARKERS.replace(raw, "")
        text = text.replace('\n', ' ').replace(HORIZONTAL_SPACE, " ")
        text = text.replace(REPEATED_SEPARATORS, INLINE_SEPARATOR)
        return text.trim().trim('·').trim()
    }

    // ── Bölüm gövdesi (bloklar) ──────────────────────────────────────────────

    /**
     * `action=parse&section=N` çıktısını bloklara ayırır. Çıktının başındaki bölüm
     * başlığı (içerikten önceki ilk başlık) atlanır; alt başlıklar korunur.
     */
    fun blocks(html: String?, maxBlocks: Int = 120): List<FandomTextBlock> {
        if (html.isNullOrBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(SECTION_JUNK_SELECTOR).remove()
        val collector = BlockCollector(maxBlocks)
        collector.walk(body)
        collector.flushPending()
        return collector.result()
    }

    private class BlockCollector(private val maxBlocks: Int) {
        private val out = mutableListOf<FandomTextBlock>()
        private val pending = StringBuilder()
        private var headingsSeen = 0
        private var contentSeen = false

        fun result(): List<FandomTextBlock> = out.toList()

        fun walk(node: Node) {
            for (child in node.childNodes()) {
                if (out.size >= maxBlocks) return
                when (child) {
                    is TextNode -> pending.append(child.text())
                    is Element -> handle(child)
                }
            }
        }

        fun flushPending() {
            val text = pending.toString()
            pending.setLength(0)
            add(FandomBlockKind.PARAGRAPH, text)
        }

        private fun add(kind: FandomBlockKind, raw: String) {
            if (out.size >= maxBlocks) return
            val text = tidyBlock(raw)
            if (text.isEmpty()) return
            if (kind != FandomBlockKind.HEADING) contentSeen = true
            out += FandomTextBlock(kind, text)
        }

        private fun handle(element: Element) {
            when (val tag = element.normalName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flushPending()
                    headingsSeen++
                    if (headingsSeen == 1 && !contentSeen) return
                    add(FandomBlockKind.HEADING, inlineOf(element))
                }
                "p" -> {
                    flushPending()
                    add(FandomBlockKind.PARAGRAPH, inlineOf(element))
                }
                "ul", "ol" -> {
                    flushPending()
                    element.children().forEach { item ->
                        if (item.normalName() == "li") handleListItem(item)
                    }
                }
                "li" -> {
                    flushPending()
                    handleListItem(element)
                }
                "blockquote" -> {
                    flushPending()
                    add(FandomBlockKind.QUOTE, inlineOf(element))
                }
                "dl" -> {
                    flushPending()
                    element.children().forEach { child ->
                        when (child.normalName()) {
                            "dt" -> add(FandomBlockKind.HEADING, inlineOf(child))
                            "dd" -> add(FandomBlockKind.PARAGRAPH, inlineOf(child))
                            else -> walk(child)
                        }
                    }
                }
                "br" -> pending.append(' ')
                else -> {
                    if (tag in INLINE_TAGS) {
                        pending.append(inlineOf(element))
                    } else {
                        flushPending()
                        walk(element)
                        flushPending()
                    }
                }
            }
        }

        private fun handleListItem(item: Element) {
            val own = item.clone()
            own.select("ul, ol").remove()
            add(FandomBlockKind.BULLET, inlineOf(own))
            item.children()
                .filter { it.normalName() == "ul" || it.normalName() == "ol" }
                .forEach { nested ->
                    nested.children().forEach { child ->
                        if (child.normalName() == "li") handleListItem(child)
                    }
                }
        }
    }

    private fun tidyBlock(raw: String): String {
        val text = tidyInline(raw)
        return if (text.any { it.isLetterOrDigit() }) text else ""
    }
}

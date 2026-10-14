package com.kitsugi.animelist.data.fandom

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Fandom HTML parçalarını sadeleştirilmiş bloklara çevirir (saf fonksiyonlar, Jsoup).
 *
 *  - [inline]: bilgi kutusu değerleri gibi kısa parçalar; liste maddeleri ve `<br>` → " · ".
 *  - [blocks]: bölüm gövdesi; başlık / paragraf / madde / alıntı / görsel blokları.
 *
 * Bölüm tablosu gövdesi de içerik taşıdığı için metin satırlarına düzleştirilir (ör. hikâye
 * yaylarındaki manga-bölüm / anime-bölüm listeleri). Navigasyon tabloları ve infobox'lar
 * çıkarılır; resim galerileri sınırlı sayıda görsel+altyazı bloğu olarak korunur.
 */
object FandomHtmlText {

    private const val INLINE_SEPARATOR = " · "
    private const val MAX_SECTION_IMAGES = 24
    private const val MAX_BLOCK_TEXT = 8_000

    private const val JUNK_SELECTOR =
        "sup.reference, .reference, .mw-ref, sup[id^=cite_ref], .mw-editsection, " +
            "style, script, noscript, rt, rp, .noprint, .error, .printfooter, .mw-cite-backlink"

    private const val SECTION_JUNK_SELECTOR = JUNK_SELECTOR +
        ", ol.references, .mw-references-wrap, .references, .navbox, .navigation-box, " +
            "table.navbox, .toc, #toc, .notice, .mbox, .ambox, .portable-infobox, aside, " +
            ".wds-tabs__wrapper, .mw-collapsible-toggle, .dablink, .hatnote, .mw-empty-elt, " +
            ".reference-text"

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
    fun blocks(html: String?, maxBlocks: Int = 400): List<FandomTextBlock> {
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
        private val seenImages = HashSet<String>()
        private var headingsSeen = 0
        private var contentSeen = false
        private var imagesSeen = 0

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
            val text = tidyBlock(raw).take(MAX_BLOCK_TEXT)
            if (text.isEmpty()) return
            if (kind != FandomBlockKind.HEADING) contentSeen = true
            out += FandomTextBlock(kind, text)
        }

        private fun handle(element: Element) {
            val tag = element.normalName()
            if (isImageContainer(element)) {
                flushPending()
                emitImages(element)
                return
            }
            when (tag) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flushPending()
                    headingsSeen++
                    if (headingsSeen == 1 && !contentSeen) return
                    add(FandomBlockKind.HEADING, inlineOf(element))
                }
                "p" -> {
                    flushPending()
                    add(FandomBlockKind.PARAGRAPH, inlineOf(element))
                    // Bazı Fandom makaleleri önemli illüstrasyonları paragraf içine gömer.
                    emitImages(element)
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
                    emitImages(element)
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
                "table" -> {
                    flushPending()
                    handleContentTable(element)
                }
                "img" -> {
                    flushPending()
                    emitImages(element)
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

        /**
         * Bölümün ana metnine ait tabloları sırayla ve satır sınırlarını koruyarak düzleştirir.
         * Navigasyon/infobox tabloları üstte selector ile zaten çıkarılmıştır.
         */
        private fun handleContentTable(table: Element) {
            val rows = table.select("tr").take(150)
            if (rows.isEmpty()) {
                walk(table)
                return
            }
            val firstHeaders = rows.first().children()
                .filter { it.normalName() == "th" }
                .map { inlineOf(it) }
            rows.forEach { row ->
                if (out.size >= maxBlocks) return
                val cells = row.children().filter { it.normalName() == "th" || it.normalName() == "td" }
                if (cells.isEmpty()) return@forEach
                val values = cells.map { inlineOf(it).take(MAX_BLOCK_TEXT) }
                val meaningful = values.withIndex().filter { it.value.isNotBlank() }
                if (meaningful.isEmpty()) return@forEach
                val headers = cells.mapIndexedNotNull { index, cell ->
                    if (cell.normalName() == "th") index to values[index] else null
                }.toMap()
                val isHeaderRow = cells.all { it.normalName() == "th" }
                val rowText = when {
                    isHeaderRow -> meaningful.joinToString(" · ") { it.value }
                    headers.isNotEmpty() -> meaningful.joinToString(" · ") { (index, value) ->
                        val label = headers[index]
                            ?: firstHeaders.getOrNull(index)?.takeIf { it.isNotBlank() }
                        if (label.isNullOrBlank() || label == value) value else "$label: $value"
                    }
                    firstHeaders.size == values.size && firstHeaders.any { it.isNotBlank() } ->
                        meaningful.joinToString(" · ") { (index, value) ->
                            val label = firstHeaders.getOrNull(index).orEmpty()
                            if (label.isBlank()) value else "$label: $value"
                        }
                    else -> meaningful.joinToString(" · ") { it.value }
                }
                add(FandomBlockKind.BULLET, rowText)
                emitImages(row)
            }
        }

        private fun handleListItem(item: Element) {
            val own = item.clone()
            own.select("ul, ol, figure, .thumb, .gallery").remove()
            add(FandomBlockKind.BULLET, inlineOf(own))
            emitImages(item)
            item.children()
                .filter { it.normalName() == "ul" || it.normalName() == "ol" }
                .forEach { nested ->
                    nested.children().forEach { child ->
                        if (child.normalName() == "li") handleListItem(child)
                    }
                }
        }

        private fun isImageContainer(element: Element): Boolean =
            element.normalName() == "figure" ||
                element.hasClass("gallery") ||
                element.hasClass("wikia-gallery") ||
                element.hasClass("thumb") ||
                element.hasClass("gallerybox")

        private fun emitImages(container: Element) {
            if (imagesSeen >= MAX_SECTION_IMAGES || out.size >= maxBlocks) return
            val images = if (container.normalName() == "img") listOf(container) else container.select("img")
            for (img in images) {
                if (imagesSeen >= MAX_SECTION_IMAGES || out.size >= maxBlocks) break
                val url = imageUrl(img) ?: continue
                if (!seenImages.add(url)) continue
                val width = (img.attr("data-width").ifBlank { img.attr("width") }).toIntOrNull()
                val height = (img.attr("data-height").ifBlank { img.attr("height") }).toIntOrNull()
                if (width != null && height != null && width < 32 && height < 32) continue

                val imageContainer = img.parents().firstOrNull { it.hasClass("gallerybox") }
                    ?: img.parents().firstOrNull {
                        it.normalName() == "figure" || it.hasClass("thumb") || it.hasClass("gallery")
                    }
                    ?: container
                val caption = imageContainer.selectFirst(".gallerytext, .thumbcaption, figcaption, .caption")
                    ?.let(::inlineOf)
                    ?.takeIf { it.isNotBlank() }
                    ?: img.attr("alt").trim().takeIf { it.isNotBlank() }
                val fandomImage = FandomImage(url = url, caption = caption)
                out += FandomTextBlock(
                    kind = FandomBlockKind.IMAGE,
                    text = caption.orEmpty(),
                    image = fandomImage
                )
                imagesSeen++
                contentSeen = true
            }
        }

        private fun imageUrl(image: Element): String? {
            val srcSet = image.attr("data-srcset").ifBlank { image.attr("srcset") }
            val fromSrcSet = srcSet.substringBefore(',').trim().substringBefore(' ').trim()
            val candidate = sequenceOf(
                image.attr("data-src"),
                image.attr("data-original"),
                fromSrcSet,
                image.attr("src")
            ).firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
            return FandomUrls.normalizeImageUrl(candidate)
        }
    }

    private fun tidyBlock(raw: String): String {
        val text = tidyInline(raw)
        return if (text.any { it.isLetterOrDigit() }) text else ""
    }

    /**
     * HTML'deki VİKİ İÇİ bağlantıları (/wiki/Title) ayıklar. Özel ad alanları
     * (Kategori:, Dosya:, Şablon: …), bağlantı çapaları (#ref…) ve aynı başlık tekrarları
     * ayıklanır; sonuçlar görüntülenme sırasına göre döner.
     */
    fun internalWikiLinks(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(JUNK_SELECTOR).remove()
        val out = LinkedHashSet<String>()
        body.select("a[href]").forEach { a ->
            val href = a.attr("href")
            val title = parseWikiLink(href) ?: return@forEach
            if (isSpecialNamespace(title)) return@forEach
            val text = inline(a.text()).takeIf { it.isNotBlank() } ?: title
            // Bağlantı metni "[]" veya referans gibi işe yaramazsa atla.
            if (!text.any { it.isLetterOrDigit() }) return@forEach
            out += title
        }
        return out.toList()
    }

    private fun parseWikiLink(href: String): String? {
        var raw = href.trim()
        if (raw.isEmpty()) return null
        val hash = raw.indexOf('#')
        if (hash >= 0) raw = raw.substring(0, hash)
        val query = raw.indexOf('?')
        if (query >= 0) raw = raw.substring(0, query)
        val marker = "/wiki/"
        val idx = raw.indexOf(marker)
        if (idx < 0) return null
        val tail = raw.substring(idx + marker.length)
        if (tail.isBlank()) return null
        // href'ler UTF-8 yüzde-kodlu gelir (`Shinj%C5%8D,_Yamagata` = `Shinjō, Yamagata`).
        // Yalnızca birkaç kodu elle çözmek ō/ş/ç gibi ASCII dışı karakterleri `%C5%8D`
        // olarak çiplere (ve tıklanınca çift kodlanmış bozuk sayfa aramasına) taşıyordu;
        // bu yüzden tamamı çözülür. `+`, MediaWiki yolunda boşluk değil artı işaretidir:
        // URLDecoder onu boşluğa çevirmesin diye önce `%2B` olarak korunur
        // (FandomUrls.parseArticleUrl ile aynı yaklaşım).
        val decoded = runCatching {
            java.net.URLDecoder.decode(tail.replace("+", "%2B"), "UTF-8")
        }.getOrElse {
            // Bozuk yüzde dizisi (ör. `%ZZ`): en azından bilinen kodları elle çöz.
            tail.replace("%20", " ")
                .replace("%2F", "/")
                .replace("%3A", ":")
                .replace("%28", "(")
                .replace("%29", ")")
        }
        return decoded
            .replace('_', ' ')
            .trim()
            .takeIf { it.isNotEmpty() && it.length <= 200 }
    }

    private val SPECIAL_NS = setOf(
        "Kategori:", "Dosya:", "Şablon:", "Modül:", "Özel:", "Tartışma:", "Kullanıcı:",
        "Kullanıcı mesaj:", "Vikipedi:", "MediaWiki:", "TimedText:", "Help:", "Yardım:",
        "Category:", "File:", "Template:", "Module:", "Special:", "Talk:", "User:",
        "User talk:", "Wikipedia:", "MediaWiki talk:", "Help talk:", "File talk:",
        "Template talk:", "Category talk:", "Fandom:", "Community Central:"
    )

    private fun isSpecialNamespace(title: String): Boolean =
        SPECIAL_NS.any { title.startsWith(it, ignoreCase = true) }
}

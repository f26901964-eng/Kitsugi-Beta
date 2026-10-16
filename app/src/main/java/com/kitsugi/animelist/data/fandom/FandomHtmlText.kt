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

    /** Bölüm başına görsel sınırı: ana sayfa gibi yoğun sayfalarda (yüzlerce kart) kesilmez. */
    private const val MAX_SECTION_IMAGES = 300
    private const val MAX_BLOCK_TEXT = 8_000
    private const val MAX_BLOCK_LINKS = 120
    private const val MAX_TABLE_ROWS = 300

    /** Blok sınırı: bölüm HTML'i (ör. ana sayfa) tamamen okunabilsin diye yüksek tutulur. */
    const val SECTION_MAX_BLOCKS = 1_500

    /** Kart hücresinde satır sonu yaratan blok öğeler. */
    private val BLOCK_BREAK_TAGS = setOf(
        "p", "div", "li", "ul", "ol", "dl", "dt", "dd", "tr", "table", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "section", "center"
    )

    private const val JUNK_SELECTOR =
        "sup.reference, .reference, .mw-ref, sup[id^=cite_ref], .mw-editsection, " +
            "style, script, noscript, rt, rp, .noprint, .error, .printfooter, .mw-cite-backlink"

    /**
     * Bölüm gövdesinden çıkarılanlar: kaynakça, içindekiler, bakım/uyarı kutuları ve bilgi
     * kutusu (infobox ayrı işlenir). Navigasyon kutuları (navbox) ARTIK çıkarılmaz; bağlantı
     * grubu olarak korunur.
     */
    private const val SECTION_JUNK_SELECTOR = JUNK_SELECTOR +
        ", ol.references, .mw-references-wrap, .references, .toc, #toc, .notice, .mbox, .ambox, " +
            ".portable-infobox, aside, .wds-tabs__wrapper, .mw-collapsible-toggle, .dablink, " +
            ".hatnote, .mw-empty-elt, .reference-text"

    /**
     * MediaWiki (Vikipedi) sayfalarına özel gövde dışı kutular.
     *
     * Fandom'da bilgi kutusu `.portable-infobox`, gezinme kutusu `.navbox`tır; MediaWiki'de ise
     * bilgi kutusu bir TABLO (`table.infobox`) ve yan kutular `.sidebar`/`.metadata`dır. Bunlar
     * gövde metni değildir. Özellikle `{{Animanga lisans}}` gibi şablonlar bilgi kutusuna
     * ÜLKE/BÖLGE bağlantısı ("Amerika Birleşik Devletleri", "Kuzey Amerika", "Türkiye"…)
     * üretiyordu ve bu bağlantılar "İlgili maddeler" şeridine alakasız kart olarak düşüyordu.
     * Otorite kontrolü kutusu ("Kanada (LAC)", "Japonya (NDL)", "Fransa (BNF)") de aynı
     * şekilde sayfanın en altından sızıp "Kanada" gibi maddeleri öneriyordu.
     */
    private const val MEDIAWIKI_JUNK_SELECTOR =
        ".infobox, table.infobox, .navbox, .navbox-styles, .vertical-navbox, .sidebar, .side-box, " +
            ".metadata, .mw-authority-control, .navbox-odd, .navbox-even, .reflist, .mw-references-wrap, " +
            "ol.references, .mw-cite-backlink, .catlinks, .mw-indicators, .mw-jump-link, .printfooter, " +
            ".mw-collapsible-toggle, .mw-editsection, .mw-empty-elt"

    /** Vikipedi sayfalarında gövde dışı kutu seçicisi; Fandom'da boş dize (davranış değişmez). */
    private fun mediaWikiJunk(wikiId: String?): String =
        if (FandomUrls.isWikipedia(wikiId)) ", $MEDIAWIKI_JUNK_SELECTOR" else ""

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
                ".mw-editsection, .dablink, .hatnote, .wikia-gallery, .gallery, .mw-empty-elt" +
                mediaWikiJunk(defaultWikiId)
        ).remove()
        val links = body.select("a[href]").mapNotNull { anchor ->
            if (anchor.hasClass("new") || anchor.attr("data-redlink").equals("1")) return@mapNotNull null
            // Vikipedi bilgi kutusundaki ülke bayrakları yalnızca görselden oluşur; hedef
            // maddeyi "bağlantı" saymak ("Kanada", "Amerika Birleşik Devletleri" çipleri)
            // sayfayla ilgisiz öneriler üretiyordu.
            if (isImageOnlyLink(anchor, defaultWikiId)) return@mapNotNull null
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
     *
     * [wikiId] verilirse bağlantılar ve bağlantılı görseller mutlak URL'ye çevrilir;
     * böylece okuyucu her öğeyi (kart, grup, satır içi bağlantı) tıklanabilir yapabilir.
     */
    fun blocks(html: String?, maxBlocks: Int = 400, wikiId: String? = null): List<FandomTextBlock> {
        if (html.isNullOrBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(SECTION_JUNK_SELECTOR + mediaWikiJunk(wikiId)).remove()
        val collector = BlockCollector(maxBlocks, wikiId)
        collector.walk(body)
        collector.flushPending()
        return collector.result()
    }

    /**
     * Göreli Fandom bağlantısını (`/wiki/…`, `//…`) mutlak URL'ye çevirir. Sayfa içi çapalar
     * (`#…`) ve `javascript:` bağlantıları tıklanabilir hedef değildir (`null`).
     */
    fun absoluteUrl(href: String?, wikiId: String?): String? {
        val raw = href?.trim().orEmpty()
        if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("javascript:", ignoreCase = true)) return null
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("mailto:", ignoreCase = true) -> raw
            wikiId != null -> FandomUrls.baseUrl(wikiId)?.let { "$it/${raw.removePrefix("/")}" } ?: raw
            else -> raw
        }
    }

    /** Etiketi olan, kırmızı (olmayan) bağlantılar. Yalnızca görselden oluşan bağlantılar atlanır. */
    private fun anchorLinks(root: Element, wikiId: String?): List<FandomBlockLink> {
        val anchors = (if (root.normalName() == "a") listOf(root) else emptyList()) + root.select("a[href]")
        val out = LinkedHashMap<String, FandomBlockLink>()
        anchors.forEach { a ->
            if (a.hasClass("new") || a.attr("data-redlink") == "1") return@forEach
            val label = tidyInline(a.text()).take(200)
            if (label.isBlank() || label.none { it.isLetterOrDigit() }) return@forEach
            val url = absoluteUrl(a.attr("href"), wikiId) ?: return@forEach
            out.putIfAbsent("$label|$url", FandomBlockLink(label, url))
        }
        return out.values.toList()
    }

    private class BlockCollector(private val maxBlocks: Int, private val wikiId: String?) {
        private val out = mutableListOf<FandomTextBlock>()
        private val pending = StringBuilder()
        private val pendingLinks = LinkedHashMap<String, FandomBlockLink>()
        private val seenImages = HashSet<String>()
        private var headingsSeen = 0
        private var contentSeen = false
        private var imagesSeen = 0

        /** Tablo hücresi içindeki bir parça: metin (isteğe bağlı bağlantı), görsel veya boş. */
        private class Seg(
            val text: String = "",
            val link: FandomBlockLink? = null,
            val image: Element? = null,
            val href: String? = null
        )

        /** Görselle başlayan bir kart taslağı; satırlar başlık ve alt metne dönüşür. */
        private class CardDraft(val image: Element, val href: String?) {
            val lines = mutableListOf<String>()
            val links = mutableListOf<FandomBlockLink>()
        }

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
            val links = pendingLinks.values.toList()
            pending.setLength(0)
            pendingLinks.clear()
            add(FandomBlockKind.PARAGRAPH, text, links = links)
        }

        private fun add(
            kind: FandomBlockKind,
            raw: String,
            href: String? = null,
            subtitle: String? = null,
            links: List<FandomBlockLink> = emptyList()
        ) {
            if (out.size >= maxBlocks) return
            val text = tidyBlock(raw).take(MAX_BLOCK_TEXT)
            if (text.isEmpty()) return
            if (kind != FandomBlockKind.HEADING) contentSeen = true
            out += FandomTextBlock(
                kind = kind,
                text = text,
                href = href,
                subtitle = subtitle?.let { tidyBlock(it) }?.takeIf { it.isNotEmpty() },
                links = links.take(MAX_BLOCK_LINKS)
            )
        }

        private fun collectPendingLinks(element: Element) {
            anchorLinks(element, wikiId).forEach { pendingLinks.putIfAbsent("${it.label}|${it.url}", it) }
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
                    add(FandomBlockKind.PARAGRAPH, inlineOf(element), links = anchorLinks(element, wikiId))
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
                    add(FandomBlockKind.QUOTE, inlineOf(element), links = anchorLinks(element, wikiId))
                    emitImages(element)
                }
                "dl" -> {
                    flushPending()
                    element.children().forEach { child ->
                        when (child.normalName()) {
                            "dt" -> add(FandomBlockKind.HEADING, inlineOf(child))
                            "dd" -> add(FandomBlockKind.PARAGRAPH, inlineOf(child), links = anchorLinks(child, wikiId))
                            else -> walk(child)
                        }
                    }
                }
                "table" -> {
                    flushPending()
                    handleTable(element)
                }
                "img" -> {
                    flushPending()
                    emitImages(element)
                }
                "br" -> pending.append(' ')
                "div", "center" -> {
                    flushPending()
                    when {
                        isNavigationBox(element) || isLinkCluster(element) -> {
                            // Kutudaki görseller (logo, ikon) da korunur; bağlantılar grup olur.
                            emitImages(element)
                            emitLinkGroup(element)
                        }
                        else -> {
                            walk(element)
                            flushPending()
                        }
                    }
                }
                else -> {
                    if (tag in INLINE_TAGS) {
                        // Satır içi öğe görsel taşıyorsa (bağlantılı görsel, ikon) görseli kaybetme.
                        if (element.select("img").isNotEmpty()) {
                            flushPending()
                            emitImages(element)
                        }
                        val text = inlineOf(element)
                        if (text.isNotEmpty()) pending.append(text)
                        collectPendingLinks(element)
                    } else {
                        flushPending()
                        walk(element)
                        flushPending()
                    }
                }
            }
        }

        /**
         * Tablo: başlık satırları `HEADING`; görsel içeren hücreler kart (`CARD`) olarak;
         * tek sütunlu metin hücreleri satır satır `BULLET`; çok sütunlu metin satırları
         * "Başlık: değer" biçiminde. Navigasyon kutuları bağlantı grubuna dönüşür.
         */
        private fun handleTable(table: Element) {
            if (isNavigationBox(table)) {
                emitLinkGroup(table)
                return
            }
            val rows = table.select("tr").take(MAX_TABLE_ROWS)
            if (rows.isEmpty()) {
                walk(table)
                flushPending()
                return
            }
            val firstHeaders = rows.first().children()
                .filter { it.normalName() == "th" }
                .map { inlineOf(it) }
            rows.forEach { row ->
                if (out.size >= maxBlocks) return
                val cells = row.children().filter { it.normalName() == "th" || it.normalName() == "td" }
                if (cells.isEmpty()) return@forEach
                if (cells.all { it.normalName() == "th" }) {
                    val header = cells.map { inlineOf(it) }.filter { it.isNotBlank() }.joinToString(" · ")
                    add(FandomBlockKind.HEADING, header)
                    return@forEach
                }
                if (cells.any { it.select("img").isNotEmpty() }) {
                    cells.forEach { handleCardCell(it) }
                    return@forEach
                }
                if (cells.size == 1) {
                    emitCellLines(cells.first())
                    return@forEach
                }
                val values = cells.map { inlineOf(it).take(MAX_BLOCK_TEXT) }
                val meaningful = values.withIndex().filter { it.value.isNotBlank() }
                if (meaningful.isEmpty()) return@forEach
                val headers = cells.mapIndexedNotNull { index, cell ->
                    if (cell.normalName() == "th") index to values[index] else null
                }.toMap()
                val rowText = when {
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
                add(FandomBlockKind.BULLET, rowText, links = anchorLinks(row, wikiId))
            }
        }

        /** Metin-yalnız tablo hücresi: her satır ayrı madde (bağlantılarıyla). */
        private fun emitCellLines(cell: Element) {
            flushPending()
            splitLines(cell).forEach { line ->
                val text = line.joinToString("") { it.text }
                add(FandomBlockKind.BULLET, text, links = line.mapNotNull { it.link })
            }
        }

        /**
         * Görsel içeren hücre: her görsel yeni bir kart başlatır. Görselden sonraki ilk
         * satır başlık, kalanlar alt metin olur; kartın bağlantısı görselin (veya başlık
         * bağlantısının) hedefidir. Görselsiz satırlar kart öncesinde madde olarak kalır.
         */
        private fun handleCardCell(cell: Element) {
            flushPending()
            var card: CardDraft? = null
            fun closeCard() {
                card?.let { emitCard(it) }
                card = null
            }
            splitLines(cell).forEach { line ->
                val buffer = StringBuilder()
                val lineLinks = mutableListOf<FandomBlockLink>()
                fun flushLine() {
                    val text = buffer.toString()
                    val c = card
                    if (text.isNotBlank() || lineLinks.isNotEmpty()) {
                        if (c != null) {
                            if (text.isNotBlank()) c.lines += text
                            c.links += lineLinks
                        } else {
                            add(FandomBlockKind.BULLET, text, links = lineLinks.toList())
                        }
                    }
                    buffer.setLength(0)
                    lineLinks.clear()
                }
                line.forEach { seg ->
                    val image = seg.image
                    if (image != null) {
                        flushLine()
                        closeCard()
                        card = CardDraft(image, seg.href)
                    } else {
                        buffer.append(seg.text)
                        seg.link?.let { lineLinks.add(it) }
                    }
                }
                flushLine()
            }
            closeCard()
        }

        private fun emitCard(card: CardDraft) {
            if (out.size >= maxBlocks) return
            val imageUrl = imageUrl(card.image)
            val lines = card.lines.map { tidyBlock(it) }.filter { it.isNotEmpty() }
            val title = lines.firstOrNull()
                ?: card.image.attr("alt").trim().takeIf { it.isNotBlank() }
                ?: hostLabel(card.href)
            if (title.isNullOrBlank() && imageUrl == null) return
            val subtitle = lines.drop(1).filterNot { it == title }.distinct()
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
            val href = card.href ?: card.links.firstOrNull()?.url
            val image = imageUrl?.let { FandomImage(url = it, caption = title) }
            out += FandomTextBlock(
                kind = FandomBlockKind.CARD,
                text = title.orEmpty().take(MAX_BLOCK_TEXT),
                image = image,
                href = href,
                subtitle = subtitle?.take(MAX_BLOCK_TEXT),
                links = card.links.distinctBy { it.url }.take(MAX_BLOCK_LINKS)
            )
            contentSeen = true
        }

        /** Hücreyi satırlara böler (`<br>`, blok öğeler ve metindeki satır sonları). */
        private fun splitLines(root: Element): List<List<Seg>> {
            val lines = mutableListOf<List<Seg>>()
            var current = mutableListOf<Seg>()
            fun newLine() {
                if (current.isNotEmpty()) lines.add(current)
                current = mutableListOf()
            }
            fun visit(node: Node, href: String?) {
                when (node) {
                    is TextNode -> {
                        node.text().split('\n').forEachIndexed { index, part ->
                            if (index > 0) newLine()
                            if (part.isNotBlank()) {
                                val link = href?.let { FandomBlockLink(tidyInline(part).take(200), it) }
                                    ?.takeIf { it.label.isNotBlank() }
                                current.add(Seg(text = part, link = link, href = href))
                            }
                        }
                    }
                    is Element -> {
                        val tag = node.normalName()
                        val ownHref = if (tag == "a" && !node.hasClass("new")) {
                            absoluteUrl(node.attr("href"), wikiId)
                        } else null
                        val effectiveHref = ownHref ?: href
                        when {
                            tag == "br" -> newLine()
                            tag == "img" -> current.add(Seg(image = node, href = effectiveHref))
                            tag == "a" && ownHref != null && node.select("img").isEmpty() -> {
                                val label = tidyInline(node.text()).take(200)
                                if (label.isNotBlank()) {
                                    current.add(Seg(text = node.text(), link = FandomBlockLink(label, ownHref), href = ownHref))
                                }
                            }
                            tag in BLOCK_BREAK_TAGS -> {
                                newLine()
                                node.childNodes().forEach { visit(it, effectiveHref) }
                                newLine()
                            }
                            else -> node.childNodes().forEach { visit(it, effectiveHref) }
                        }
                    }
                }
            }
            root.childNodes().forEach { visit(it, null) }
            newLine()
            return lines
        }

        /** Navigasyon kutusu (navbox): satır başlığı (th) grup adı, satırdaki bağlantılar çipler. */
        private fun emitLinkGroup(container: Element) {
            flushPending()
            val rows = container.select("tr").take(MAX_TABLE_ROWS)
            val title = (container.selectFirst(".navbox-title, .navigation-box-title, .navbox-title-group")
                ?: rows.firstOrNull()?.selectFirst("th"))
                ?.let { inlineOf(it) }?.takeIf { it.isNotBlank() }
            if (rows.isEmpty()) {
                // Tablosuz navbox (div): tüm bağlantılar tek grup.
                val links = anchorLinks(container, wikiId)
                if (links.isNotEmpty() && out.size < maxBlocks) {
                    out += FandomTextBlock(FandomBlockKind.LINK_GROUP, text = title.orEmpty(), links = links.take(MAX_BLOCK_LINKS))
                    contentSeen = true
                }
                return
            }
            title?.let { add(FandomBlockKind.HEADING, it) }
            rows.forEach { row ->
                if (out.size >= maxBlocks) return
                val label = row.selectFirst("th")?.let { inlineOf(it) }?.takeIf { it.isNotBlank() && it != title }
                val links = anchorLinks(row, wikiId)
                if (links.isEmpty()) return@forEach
                out += FandomTextBlock(FandomBlockKind.LINK_GROUP, text = label.orEmpty(), links = links.take(MAX_BLOCK_LINKS))
                contentSeen = true
            }
        }

        private fun handleListItem(item: Element) {
            val own = item.clone()
            own.select("ul, ol, figure, .thumb, .gallery").remove()
            add(FandomBlockKind.BULLET, inlineOf(own), links = anchorLinks(own, wikiId))
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

        private fun isNavigationBox(element: Element): Boolean =
            element.hasClass("navbox") || element.hasClass("navbox-inner") ||
                element.hasClass("navigation-box") || element.hasClass("navbox-subgroup")

        /**
         * Yalnızca bağlantılardan oluşan kutu (ör. "Capcom Wikis", "Horror Games", dil ve
         * şablon listeleri): en az üç bağlantı ve metnin büyük kısmı bağlantı.
         */
        private fun isLinkCluster(element: Element): Boolean {
            if (element.select("table, p").size > 2) return false
            val anchors = element.select("a[href]").filter { tidyInline(it.text()).isNotBlank() }
            if (anchors.size < 3) return false
            val fullText = tidyInline(element.text())
            if (fullText.isEmpty() || fullText.length > 900) return false
            val anchorText = anchors.sumOf { tidyInline(it.text()).length }
            return anchorText * 10 >= fullText.length * 6
        }

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
                // Bağlantılı görsel: dokununca hedef sayfa açılır (galeri, kart, ana sayfa vitrini).
                val href = img.parents().firstOrNull { it.normalName() == "a" && it.hasAttr("href") }
                    ?.let { absoluteUrl(it.attr("href"), wikiId) }
                val fandomImage = FandomImage(url = url, caption = caption)
                out += FandomTextBlock(
                    kind = FandomBlockKind.IMAGE,
                    text = caption.orEmpty(),
                    image = fandomImage,
                    href = href
                )
                imagesSeen++
                contentSeen = true
            }
        }

        private fun hostLabel(href: String?): String? =
            href?.substringAfter("://", "")?.substringBefore('/')?.takeIf { it.isNotBlank() }

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
    fun internalWikiLinks(html: String?, defaultWikiId: String? = null): List<String> =
        internalWikiLinkCounts(html, defaultWikiId).keys.toList()

    /**
     * [internalWikiLinks] ile aynı sonucu verir, ancak her başlığın gövdede kaç kez
     * geçtiğini de döndürür. "İlgili maddeler" seçimi bu sayımla önem sırasına dizilir:
     * bir maddede bir kez geçen bölge/ülke bağlantısı, gövdede defalarca geçen
     * karakter/stüdyo bağlantısının önüne geçemez.
     *
     * Vikipedi'de bilgi kutusu/navbox/otorite kontrolü gibi tablolar gövde sayılmaz
     * ([MEDIAWIKI_JUNK_SELECTOR]); Fandom davranışı değişmez.
     */
    fun internalWikiLinkCounts(html: String?, defaultWikiId: String? = null): LinkedHashMap<String, Int> {
        if (html.isNullOrBlank()) return LinkedHashMap()
        val body = Jsoup.parseBodyFragment(html).body()
        body.select(JUNK_SELECTOR + mediaWikiJunk(defaultWikiId)).remove()
        val out = LinkedHashMap<String, Int>()
        body.select("a[href]").forEach { a ->
            if (a.hasClass("new") || a.attr("data-redlink") == "1") return@forEach
            val href = a.attr("href")
            val title = parseWikiLink(href) ?: return@forEach
            if (isSpecialNamespace(title)) return@forEach
            // Related-page chips belong to THIS wiki. Cross-wiki links can share a
            // title but lead to a different article (or a dead card) on the current one.
            if (defaultWikiId != null) {
                val target = FandomUrls.parseArticleUrl(href, defaultWikiId) ?: return@forEach
                if (target.first != FandomUrls.normalize(defaultWikiId)) return@forEach
            }
            if (isImageOnlyLink(a, defaultWikiId)) return@forEach
            val text = inline(a.text()).takeIf { it.isNotBlank() } ?: title
            // Bağlantı metni "[]" veya referans gibi işe yaramazsa atla.
            if (!text.any { it.isLetterOrDigit() }) return@forEach
            out[title] = (out[title] ?: 0) + 1
        }
        return out
    }

    /**
     * Vikipedi'de yalnızca görselden oluşan bağlantılar (bilgi kutusundaki ülke bayrakları,
     * logo/ikon bağlantıları) gövde bağlantısı sayılmaz: metni olmadığı için etiketi
     * hedef başlığa düşer ve "Kanada", "Amerika Birleşik Devletleri" gibi sayfayla ilgisiz
     * öneriler doğurur. Fandom'da görselli bağlantılar başlık metnine sahip olduğundan
     * (ör. kapak galerisi) davranış değişmez.
     */
    private fun isImageOnlyLink(anchor: Element, defaultWikiId: String?): Boolean =
        FandomUrls.isWikipedia(defaultWikiId) &&
            anchor.text().isBlank() &&
            anchor.select("img, picture, svg").isNotEmpty()

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

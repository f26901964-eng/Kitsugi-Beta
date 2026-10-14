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

    /**
     * Bölüm başına görsel üst sınırı.
     *
     * Canlı denetim (tools/wiki_audit/results/content_report.md, 27 sayfa): sınır 24 iken
     * 700 görsel düşüyordu; 60'ta da 6 sayfa sınıra takıldı. `FandomApi.pageImages` aynı
     * sayfa için 300 dosya çekebildiği için bölüm sınırı da 300'e eşitlendi. Aşıldığında
     * sessizce düşmek yerine "N görsel daha var" notu üretilir.
     */
    private const val MAX_SECTION_IMAGES = 300

    /**
     * Bölüm başına tablo satırı üst sınırı.
     *
     * Canlı denetim: eski `take(150)` tek bir sayfada ("List of Canon Characters",
     * onepiece) 1477 satırı düşürdü ve metin kapsaması %13,5'e indi. Sınır 2000'e
     * çıkarıldı; aşan kısım için satır sayısı notu üretilir.
     */
    private const val MAX_TABLE_ROWS = 2_000

    /**
     * Bölüm başına blok üst sınırı.
     *
     * Canlı denetim bunun GERÇEK darboğaz olduğunu gösterdi: tablo satırı sınırı 2000'e
     * çıkarılsa da 1500 satırlık bir liste sayfası 400 blokta kesiliyordu. 4000'e çıkarıldı
     * (bölüm başına ~4000 kısa blok; bellek açısından sorun değil).
     */
    private const val MAX_BLOCKS = 4_000
    private const val MAX_BLOCK_TEXT = 8_000

    /**
     * Bölüm metninden SİLİNEN yapılar: atıflar, düzenleme bağlantıları, gezinme kutuları
     * ve bilgi kutuları.
     *
     * Katlanabilir içeriğin YALNIZCA "göster/gizle" düğmesi (`.mw-collapsible-toggle`,
     * `.collapsible-toggle-link`) silinir — düğme etiketi içerik değildir. Sekmeli içerik
     * (`.wds-tabs__wrapper`) ise eskiden BURADAYDI ve sekme içindeki gerçek metin tümüyle
     * kayboluyordu; artık korunuyor.
     */
    private const val JUNK_SELECTOR =
        "sup.reference, .reference, .mw-ref, sup[id^=cite_ref], .mw-editsection, " +
            "style, script, noscript, rt, rp, .noprint, .error, .printfooter, .mw-cite-backlink, " +
            ".mw-collapsible-toggle, .collapsible-toggle-link"

    private const val SECTION_JUNK_SELECTOR = JUNK_SELECTOR +
        ", ol.references, .mw-references-wrap, .references, .navbox, .navigation-box, " +
            "table.navbox, .toc, #toc, .notice, .mbox, .ambox, .portable-infobox, aside, " +
            ".notice, .dablink, .hatnote, .mw-empty-elt, .reference-text"

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

    private fun inlineOf(element: Element): String = tidyInline(collectInline(element).text)

    /** Metin + bağlantı konumları birlikte üretilir (arayüz bağlantıları tıklanabilir yapar). */
    private data class InlineResult(val text: String, val links: List<FandomTextLinkSpan>)

    private fun collectInline(element: Element, defaultWikiId: String? = null): InlineResult {
        val sb = StringBuilder()
        val spans = mutableListOf<Span>()
        appendInline(element, sb, defaultWikiId, spans)
        val tidied = tidyForInline(sb.toString())
        return InlineResult(tidied.text, remapSpans(spans, tidied, defaultWikiId))
    }

    private class Span(var start: Int, var end: Int, val href: String?, val fallbackTitle: String?)

    private fun remapSpans(
        spans: List<Span>,
        tidied: TidyResult,
        defaultWikiId: String?
    ): List<FandomTextLinkSpan> {
        if (spans.isEmpty()) return emptyList()
        val out = ArrayList<FandomTextLinkSpan>(spans.size)
        for (span in spans) {
            val href = span.href ?: continue
            val start = tidied.mapPosition(span.start)
            val end = tidied.mapPosition(span.end)
            if (start < 0 || end <= start || end > tidied.text.length) continue
            val label = tidied.text.substring(start, end)
            // Metin temizliği sırasında etiket tamamen silindiyse bağlantıyı da at.
            if (label.isBlank() || !label.any { it.isLetterOrDigit() }) continue
            val target = if (defaultWikiId != null) {
                FandomUrls.parseArticleUrl(href, defaultWikiId)
            } else {
                internalLinkTitle(href)?.let { null to it }
            } ?: continue
            out += FandomTextLinkSpan(
                start = start,
                end = end,
                wikiId = target.first ?: defaultWikiId.orEmpty(),
                title = target.second
            )
        }
        return dedupeSpans(out)
    }

    /** Aynı hedefin aynı metinde tekrar eden konumları korunur; birebir çakışanlar tekilleştirilir. */
    private fun dedupeSpans(spans: List<FandomTextLinkSpan>): List<FandomTextLinkSpan> {
        val seen = HashSet<String>()
        return spans.filter { span ->
            span.start < span.end && seen.add("${span.start}:${span.end}:${span.title}")
        }
    }

    private fun appendInline(
        node: Node,
        sb: StringBuilder,
        defaultWikiId: String?,
        spans: MutableList<Span>
    ) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> sb.append(child.text())
                is Element -> {
                    val tag = child.normalName()
                    when {
                        tag == "br" -> appendSeparator(sb)
                        tag in BLOCKISH_INLINE_TAGS -> {
                            appendSeparator(sb)
                            appendInline(child, sb, defaultWikiId, spans)
                            appendSeparator(sb)
                        }
                        tag == "a" -> {
                            val href = child.attr("href").takeIf { it.isNotBlank() }
                            val redlink = child.hasClass("new") || child.attr("data-redlink") == "1"
                            val start = sb.length
                            appendInline(child, sb, defaultWikiId, spans)
                            val end = sb.length
                            if (href != null && !redlink && end > start) {
                                spans += Span(start, end, href, null)
                            }
                        }
                        else -> appendInline(child, sb, defaultWikiId, spans)
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

    private fun tidyInline(raw: String): String = tidyForInline(raw).text

    /**
     * Metin temizliğinin KONUM KORUYAN hâli: [TidyResult.map] girdideki her karakterin
     * çıktıda hangi konuma geldiğini tutar, böylece bağlantı aralıkları bozulmaz.
     */
    private class TidyResult(val text: String, private val map: IntArray) {
        /** Girdi konumu → çıktı konumu; çıktıdan düşen konumlar bir sonraki geçerli konuma yuvarlanır. */
        fun mapPosition(input: Int): Int {
            if (map.isEmpty()) return 0
            val clamped = input.coerceIn(0, map.lastIndex)
            val direct = map[clamped]
            if (direct >= 0) return direct
            for (i in clamped..map.lastIndex) {
                if (map[i] >= 0) return map[i]
            }
            for (i in clamped downTo 0) {
                if (map[i] >= 0) return map[i] + 1
            }
            return map.count { it >= 0 }
        }
    }

    private fun tidyForInline(raw: String): TidyResult {
        var text = raw
        var map = IntArray(raw.length) { it }
        REF_MARKERS.findAll(raw).toList().reversed().forEach { match ->
            text = text.removeRange(match.range.first, match.range.last + 1)
            map = dropRange(map, match.range.first, match.range.last + 1)
        }
        val builder = StringBuilder(text.length)
        val remap = IntArray(map.size) { -1 }
        for (i in text.indices) {
            val source = map[i]
            val ch = text[i]
            if (isHorizontalSpace(ch)) {
                if (builder.isNotEmpty() && builder.last() != ' ') {
                    builder.append(' ')
                    if (source >= 0) remap[source] = builder.length - 1
                }
            } else {
                builder.append(ch)
                if (source >= 0) remap[source] = builder.length - 1
            }
        }
        text = builder.toString()
        map = remap
        REPEATED_SEPARATORS.findAll(text).toList().reversed().forEach { match ->
            if (match.value == INLINE_SEPARATOR) return@forEach
            text = text.replaceRange(match.range, INLINE_SEPARATOR)
            map = replaceRange(map, match.range.first, match.range.last + 1)
        }
        val trimmedStart = text.length - text.trimStart().length
        val trimmedEnd = text.trimEnd().length
        text = text.substring(trimmedStart, trimmedEnd)
        map = shiftMap(map, -trimmedStart)
        while (text.startsWith('·')) {
            text = text.substring(1)
            map = dropRange(map, 0, 1)
        }
        while (text.endsWith('·')) {
            text = text.substring(0, text.length - 1)
            map = dropRange(map, text.length, text.length + 1)
        }
        return TidyResult(text.trim(), shiftMap(map, 0))
    }

    private fun isHorizontalSpace(ch: Char): Boolean =
        ch == ' ' || ch == '\t' || ch == '\u00A0' || ch == '\u2009' || ch == '\u200A'

    private fun dropRange(map: IntArray, from: Int, to: Int): IntArray {
        val out = IntArray(map.size) { -1 }
        for (i in map.indices) {
            out[i] = when {
                i < from -> map[i]
                i >= to -> map[i]
                else -> -1
            }
        }
        return out
    }

    /** Silinen aralık yerine tek bir ayraç girdiğinde konum haritasını günceller. */
    private fun replaceRange(map: IntArray, from: Int, to: Int): IntArray {
        val out = IntArray(map.size) { -1 }
        for (i in map.indices) {
            out[i] = when {
                i < from -> map[i]
                i >= to -> map[i]
                else -> if (i == from) map[i] else -1
            }
        }
        return out
    }

    private fun shiftMap(map: IntArray, delta: Int): IntArray {
        if (delta == 0) return map
        return IntArray(map.size) { if (map[it] >= 0) map[it] + delta else -1 }
    }

    // ── Bölüm gövdesi (bloklar) ──────────────────────────────────────────────

    /**
     * `action=parse&section=N` çıktısını bloklara ayırır. Çıktının başındaki bölüm
     * başlığı (içerikten önceki ilk başlık) atlanır; alt başlıklar korunur.
     *
     * [wikiId] verildiğinde blok içindeki wiki bağlantıları kanonik wiki+başlık çiftine
     * çözülür ve [FandomTextBlock.linkSpans] doldurulur (arayüz bunları tıklanabilir yapar).
     */
    fun blocks(html: String?, maxBlocks: Int = MAX_BLOCKS, wikiId: String? = null): List<FandomTextBlock> {
        if (html.isNullOrBlank()) return emptyList()
        val body = Jsoup.parseBodyFragment(html, FandomUrls.baseUrl(wikiId.orEmpty()).orEmpty()).body()
        body.select(SECTION_JUNK_SELECTOR).remove()
        val collector = BlockCollector(maxBlocks)
        collector.wikiId = wikiId
        collector.walk(body)
        collector.flushPending()
        collector.appendOmissionNotes()
        return collector.result()
    }

    private class BlockCollector(private val maxBlocks: Int) {
        private val out = mutableListOf<FandomTextBlock>()
        private val pending = StringBuilder()
        private val seenImages = HashSet<String>()
        private var headingsSeen = 0
        private var contentSeen = false
        private var imagesSeen = 0

        /** Bağlantıların çözüleceği varsayılan wiki (`tr.onepiece` gibi). */
        var wikiId: String? = null

        /** Bir bloğun metni + içindeki bağlantı aralıkları birlikte taşınır. */
        private data class Inline(val text: String, val links: List<FandomTextLinkSpan>)

        /** Son üretilen bloğun bağlantıları; [add] çağrısında bloğa işlenir. */
        private val pendingLinks = mutableListOf<FandomTextLinkSpan>()

        /** Kırpma sayıları: kullanıcıya "N satır daha var" notu olarak gösterilir. */
        var tableRowsOmitted = 0
            private set
        var imagesOmitted = 0
            private set

        /**
         * Fandom video yer tutucuları (`figure[data-video-key]`). Uygulama video
         * oynatmadığı için görsel olarak çizilemez; ama sayfa "videosuz" da sayılmaz —
         * kullanıcıya kaç video olduğu bildirilir.
         */
        var videoPlaceholders = 0
            private set

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

        /**
         * Bölüm sonuna "kırpıldı" notları ekler. Sessiz veri kaybı yerine kullanıcıya
         * eksik kalan satır/görsel sayısı bildirilir.
         */
        fun appendOmissionNotes() {
            if (videoPlaceholders > 0) {
                out += FandomTextBlock(
                    kind = FandomBlockKind.PARAGRAPH,
                    text = "Bu bölümde $videoPlaceholders video var (uygulama içinde oynatılamıyor; " +
                        "tarayıcıda aç düğmesiyle izlenebilir)."
                )
            }
            if (tableRowsOmitted > 0) {
                out += FandomTextBlock(
                    kind = FandomBlockKind.PARAGRAPH,
                    text = "… (+$tableRowsOmitted tablo satırı gösterilmedi)"
                )
            }
            if (imagesOmitted > 0) {
                out += FandomTextBlock(
                    kind = FandomBlockKind.PARAGRAPH,
                    text = "… (+$imagesOmitted görsel bölüm sınırına takıldı)"
                )
            }
        }

        private fun add(kind: FandomBlockKind, raw: String) {
            if (out.size >= maxBlocks) {
                pendingLinks.clear()
                return
            }
            val text = tidyBlock(raw).take(MAX_BLOCK_TEXT)
            if (text.isEmpty()) {
                pendingLinks.clear()
                return
            }
            val links = pendingLinks
                .filter { it.start >= 0 && it.end > it.start && it.end <= text.length }
                .let { if (text.length < raw.length) it.filter { span -> span.end <= text.length } else it }
            pendingLinks.clear()
            if (kind != FandomBlockKind.HEADING) contentSeen = true
            out += FandomTextBlock(kind, text, linkSpans = links)
        }

        /**
         * Metin + bağlantı aralıklarını birlikte üretir. Konumlar zaten temizlenmiş
         * metne göredir; bu yüzden [addInline] ile eklenir (ham metin yeniden
         * temizlenmez, aralıklar bozulmaz).
         */
        private fun inlineWithLinks(element: Element): Inline {
            val collected = collectInline(element, wikiId)
            pendingLinks += collected.links
            return Inline(collected.text, collected.links)
        }

        private fun addInline(kind: FandomBlockKind, inline: Inline) {
            if (out.size >= maxBlocks) {
                pendingLinks.clear()
                return
            }
            val text = inline.text.take(MAX_BLOCK_TEXT)
            if (text.isEmpty() || !text.any { it.isLetterOrDigit() }) {
                pendingLinks.clear()
                return
            }
            val links = inline.links.filter { it.start >= 0 && it.end > it.start && it.end <= text.length }
            pendingLinks.clear()
            if (kind != FandomBlockKind.HEADING) contentSeen = true
            out += FandomTextBlock(kind, text, linkSpans = links)
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
                    addInline(FandomBlockKind.HEADING, inlineWithLinks(element))
                }
                "p" -> {
                    flushPending()
                    addInline(FandomBlockKind.PARAGRAPH, inlineWithLinks(element))
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
                    addInline(FandomBlockKind.QUOTE, inlineWithLinks(element))
                    emitImages(element)
                }
                "dl" -> {
                    flushPending()
                    element.children().forEach { child ->
                        when (child.normalName()) {
                            "dt" -> addInline(FandomBlockKind.HEADING, inlineWithLinks(child))
                            "dd" -> addInline(FandomBlockKind.PARAGRAPH, inlineWithLinks(child))
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
            val allRows = table.select("tr")
            val rows = allRows.take(MAX_TABLE_ROWS)
            if (rows.isEmpty()) {
                walk(table)
                return
            }
            // 150 satır üstü tablolar kırpılır; kullanıcıya kaç satır kaldığı bildirilir.
            val omitted = (allRows.size - rows.size).coerceAtLeast(0)
            tableRowsOmitted += omitted
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
            addInline(FandomBlockKind.BULLET, inlineWithLinks(own))
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

        /** Bir düğümdeki <img> sayısı (sınır aşımında "kaç görsel kaldı" notu için). */
        private fun countImages(container: Element): Int =
            if (container.normalName() == "img") 1 else container.select("img").size

        /**
         * Fandom video yer tutucusu sayısı (`data-video-key` taşıyan öğeler).
         *
         * Jsoup'ta `select` öğenin KENDİNİ de eşleştirir; bu yüzden kapsayıcı ayrıca
         * sayılırsa her video iki kez sayılır (canlı testte 2 video "4" çıkmıştı).
         */
        private fun countVideos(container: Element): Int {
            val self = if (container.attr("data-video-key").isNotBlank()) 1 else 0
            val descendants = container.select("[data-video-key]").count { it !== container }
            return self + descendants
        }

        private fun emitImages(container: Element) {
            videoPlaceholders += countVideos(container)
            if (imagesSeen >= MAX_SECTION_IMAGES || out.size >= maxBlocks) {
                imagesOmitted += countImages(container)
                return
            }
            val images = if (container.normalName() == "img") listOf(container) else container.select("img")
            for (img in images) {
                if (imagesSeen >= MAX_SECTION_IMAGES || out.size >= maxBlocks) {
                    imagesOmitted++
                    continue
                }
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

    /**
     * Wiki kimliği bilinmeyen bağlamlar (infobox değerleri) için `href` → madde başlığı.
     * Özel ad alanları ve çapalar elenir; bağlantı değilse `null`.
     */
    internal fun internalLinkTitle(href: String?): String? {
        val raw = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val title = parseWikiLink(raw) ?: return null
        return if (isSpecialNamespace(title)) null else title
    }

    private fun parseWikiLink(href: String): String? {
        var raw = href.trim()
        if (raw.isEmpty()) return null
        val hash = raw.indexOf('#')
        if (hash >= 0) raw = raw.substring(0, hash)
        val marker = "/wiki/"
        val idx = raw.indexOf(marker)
        if (idx < 0) return null
        val tail = raw.substring(idx + marker.length)
        if (tail.isBlank()) return null
        return tail
            .replace('_', ' ')
            .replace("%20", " ")
            .replace("%2F", "/")
            .replace("%3A", ":")
            .replace("%28", "(")
            .replace("%29", ")")
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

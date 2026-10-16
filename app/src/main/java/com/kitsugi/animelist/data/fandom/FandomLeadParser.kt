package com.kitsugi.animelist.data.fandom

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Sayfanın "giriş" bölümünü (`action=parse&section=0`) çözer.
 *
 * Neden gerekli (canlı Fandom ölçümleri):
 *  - `action=parse&prop=sections` yalnızca başlıklı bölümleri listeler; ilk başlıktan önceki giriş
 *    metni hiçbir bölümde yoktur ve uygulama onu hiç göstermiyordu.
 *  - `prop=extracts` Fandom'da kurulu değildir ("Unrecognized value for parameter prop"); yani
 *    `fandomdescription` yoksa açıklama tamamen boş kalıyordu. `fandomdescription` ise ~500 karakterde
 *    "..." ile kesilir (ör. Gintoki Sakata, Monkey D. Luffy).
 *  - `pageprops.infoboxes` JSON'u bağlantı metinlerini düşürebilir ve liste ayırıcılarını siler
 *    ("Japanese VA: (Child) (Gender Bender Arc)" — seslendirenlerin adı yok). İşlenmiş
 *    `aside.portable-infobox` HTML'i ise eksiksizdir.
 */
internal object FandomLeadParser {

    private const val MAX_INTRO_CHARS = 6_000
    private const val MAX_INTRO_BLOCKS = 60
    private const val MIN_LONE_PARAGRAPH = 40

    data class Lead(val infobox: FandomInfobox? = null, val intro: String? = null) {
        val isEmpty: Boolean get() = infobox == null && intro.isNullOrBlank()
    }

    fun parse(html: String?, wikiId: String): Lead {
        if (html.isNullOrBlank()) return Lead()
        return Lead(infobox = parseInfobox(html, wikiId), intro = parseIntro(html, wikiId))
    }

    // ── Giriş metni ──────────────────────────────────────────────────────────

    /**
     * @param wikiId Vikipedi'de giriş bölümünün içindeki bilgi kutusu/navbox/otorite kontrolü
     *   gövde metni sayılmaz (bkz. `FandomHtmlText.MEDIAWIKI_JUNK_SELECTOR`); açıklama kartına
     *   "Kodansha USA · Funimation" gibi kutu kırıntıları düşmez. Fandom davranışı aynıdır.
     */
    fun parseIntro(html: String?, wikiId: String? = null): String? {
        val blocks = FandomHtmlText.blocks(html, maxBlocks = MAX_INTRO_BLOCKS, wikiId = wikiId)
        val parts = ArrayList<String>()
        var chars = 0
        for (block in blocks) {
            val text = when (block.kind) {
                FandomBlockKind.PARAGRAPH -> block.text.takeIf(::isRealParagraph)
                FandomBlockKind.BULLET -> block.text.takeIf(::isRealParagraph)?.let { "• $it" }
                else -> null
            } ?: continue
            if (chars + text.length > MAX_INTRO_CHARS && parts.isNotEmpty()) break
            parts += text
            chars += text.length
        }
        return parts.joinToString("\n\n")
            .let(FandomNameVariants::cleanDescription)
            .takeIf { it.isNotBlank() }
    }

    /** "Overview", "Story", "Gallery" gibi sekme etiketleri ve tek kelimelik kırıntılar giriş sayılmaz. */
    private fun isRealParagraph(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length >= MIN_LONE_PARAGRAPH) return true
        return trimmed.length >= 12 && trimmed.last() in ".!?…。！？」』\"”"
    }

    // ── Bilgi kutusu (işlenmiş HTML) ─────────────────────────────────────────

    fun parseInfobox(html: String?, wikiId: String): FandomInfobox? {
        if (html.isNullOrBlank()) return null
        val body = Jsoup.parseBodyFragment(html, FandomUrls.baseUrl(wikiId).orEmpty()).body()
        var best: FandomInfobox? = null
        var bestWeight = -1
        // İç içe aside'lar tek kutu sayılır (dıştaki yeterli).
        for (aside in body.select("aside.portable-infobox").filter { it.parents().none { p -> p.hasClass("portable-infobox") } }) {
            val box = parseAside(aside, wikiId)
            val weight = box.allFields().size * 2 + box.images.size + (if (box.title.isNullOrBlank()) 0 else 1)
            if (weight > bestWeight) {
                best = box
                bestWeight = weight
            }
        }
        if (best == null || best.isEmpty()) {
            for (table in body.select("table.infobox, table.vevent")) {
                val box = parseTableInfobox(table, wikiId)
                val weight = box.allFields().size * 2 + box.images.size + (if (box.title.isNullOrBlank()) 0 else 1)
                if (weight > bestWeight) {
                    best = box
                    bestWeight = weight
                }
            }
        }
        return best?.takeUnless { it.isEmpty() }
    }

    private class Builder {
        var title: String? = null
        val images = mutableListOf<FandomImage>()
        val groups = mutableListOf<FandomInfoboxGroup>()
        val loose = mutableListOf<FandomInfoboxField>()

        fun flushLoose() {
            if (loose.isNotEmpty()) {
                groups += FandomInfoboxGroup(null, loose.toList())
                loose.clear()
            }
        }
    }

    private fun parseAside(aside: Element, wikiId: String): FandomInfobox {
        val builder = Builder()
        walk(aside, builder, wikiId, tabLabel = null)
        builder.flushLoose()
        return FandomInfobox(
            title = builder.title,
            images = builder.images.distinctBy { it.url }.take(24),
            groups = builder.groups.toList()
        )
    }

    private fun parseTableInfobox(table: Element, wikiId: String): FandomInfobox {
        val builder = Builder()
        val rows = table.select("tr")
        var currentGroupHeader: String? = null
        val currentGroupFields = ArrayList<FandomInfoboxField>()

        for (row in rows) {
            val above = row.selectFirst("th.infobox-above, th.summary")
            if (above != null && builder.title.isNullOrBlank()) {
                builder.title = FandomHtmlText.inline(above.html()).takeIf { it.isNotBlank() }
                continue
            }
            val imgEl = row.selectFirst("td.infobox-image img, td.image img, a.image img, img")
            if (row.selectFirst("td.infobox-image") != null || (row.select("th").isEmpty() && imgEl != null)) {
                val src = imgEl?.absUrl("src")?.ifBlank { imgEl.attr("src") }
                val cleanUrl = FandomUrls.normalizeImageUrl(src)
                if (cleanUrl != null) {
                    val captionEl = row.selectFirst(".infobox-caption, .caption")
                    val caption = captionEl?.let { FandomHtmlText.inline(it.html()) }?.takeIf { it.isNotBlank() }
                    builder.images += FandomImage(cleanUrl, caption)
                }
                continue
            }
            val headerTh = row.selectFirst("th.infobox-header, th[colspan]")
            if (headerTh != null && row.select("td").isEmpty()) {
                if (currentGroupFields.isNotEmpty()) {
                    builder.groups += FandomInfoboxGroup(currentGroupHeader, currentGroupFields.toList())
                    currentGroupFields.clear()
                }
                currentGroupHeader = FandomHtmlText.inline(headerTh.html()).takeIf { it.isNotBlank() }
                continue
            }
            val th = row.selectFirst("th.infobox-label, th")
            val td = row.selectFirst("td.infobox-data, td")
            if (th != null && td != null) {
                val label = FandomHtmlText.inline(th.html())
                val value = FandomHtmlText.inline(td.html())
                if (label.isNotBlank() && value.isNotBlank()) {
                    val field = FandomInfoboxParser.buildField(th.html(), td.html(), null, wikiId)
                    if (field != null) {
                        currentGroupFields += field
                    }
                }
            }
        }
        if (currentGroupFields.isNotEmpty()) {
            builder.groups += FandomInfoboxGroup(currentGroupHeader, currentGroupFields.toList())
        }
        builder.flushLoose()
        return FandomInfobox(
            title = builder.title,
            images = builder.images.distinctBy { it.url }.take(24),
            groups = builder.groups.toList()
        )
    }

    private fun walk(container: Element, builder: Builder, wikiId: String, tabLabel: String?) {
        for (child in container.children()) {
            when {
                child.hasClass("pi-title") -> if (builder.title.isNullOrBlank()) {
                    builder.title = FandomHtmlText.inline(child.html()).takeIf { it.isNotBlank() }
                }
                child.hasClass("pi-image") || child.normalName() == "figure" -> addImages(child, builder)
                child.hasClass("pi-group") -> {
                    builder.flushLoose()
                    parseGroup(child, builder, wikiId, tabLabel)
                }
                child.hasClass("pi-data") -> dataField(child, wikiId)?.let { builder.loose += it }
                child.hasClass("pi-header") -> {
                    builder.flushLoose()
                    val header = FandomHtmlText.inline(child.html()).takeIf { it.isNotBlank() }
                    // Üstbilgiyi izleyen kardeş alanlar bu başlığa aittir.
                    val fields = ArrayList<FandomInfoboxField>()
                    var next = child.nextElementSibling()
                    while (next != null && next.hasClass("pi-data")) {
                        dataField(next, wikiId)?.let { fields += it }
                        next = next.nextElementSibling()
                    }
                    if (fields.isNotEmpty()) builder.groups += FandomInfoboxGroup(header, fields)
                }
                child.hasClass("pi-panel") || child.hasClass("wds-tabber") -> parseTabs(child, builder, wikiId)
                child.hasClass("pi-smart-group") -> {
                    builder.flushLoose()
                    parseGroup(child, builder, wikiId, tabLabel)
                }
                child.hasClass("pi-navigation") || child.hasClass("pi-media-collection") -> {
                    if (child.hasClass("pi-media-collection")) addImages(child, builder)
                }
                else -> if (child.children().isNotEmpty() &&
                    !child.hasClass("pi-data") && child.normalName() in setOf("div", "section")
                ) {
                    walk(child, builder, wikiId, tabLabel)
                }
            }
        }
    }

    private fun parseTabs(panel: Element, builder: Builder, wikiId: String) {
        val tabLabels = panel.select(".wds-tabs__tab-label").ifEmpty { panel.select(".wds-tabs__tab") }
        val labels = tabLabels.map { FandomHtmlText.inline(it.html()) }
        val contents = panel.select(".wds-tab__content").filter { content ->
            content.parents().firstOrNull { it.hasClass("pi-panel") || it.hasClass("wds-tabber") } == panel
        }
        if (contents.isEmpty()) {
            walk(panel, builder, wikiId, tabLabel = null)
            return
        }
        contents.forEachIndexed { index, content ->
            builder.flushLoose()
            walk(content, builder, wikiId, tabLabel = labels.getOrNull(index)?.takeIf { it.isNotBlank() })
        }
    }

    private fun parseGroup(group: Element, builder: Builder, wikiId: String, tabLabel: String?) {
        val header = group.selectFirst(".pi-header")
            ?.let { FandomHtmlText.inline(it.html()) }
            ?.takeIf { it.isNotBlank() }
        val fields = group.select(".pi-data").mapNotNull { dataField(it, wikiId) }
        if (fields.isEmpty()) {
            group.select("figure.pi-image, .pi-image").forEach { addImages(it, builder) }
            return
        }
        val label = when {
            header != null && tabLabel != null -> "$tabLabel · $header"
            else -> header ?: tabLabel
        }
        builder.groups += FandomInfoboxGroup(label, fields)
    }

    private fun dataField(element: Element, wikiId: String): FandomInfoboxField? {
        val labelElement = element.selectFirst(".pi-data-label")
        val valueElement = element.selectFirst(".pi-data-value") ?: return null
        val source = element.attr("data-source").takeIf { it.isNotBlank() }
        return FandomInfoboxParser.buildField(
            rawLabel = labelElement?.html(),
            rawValue = valueElement.html(),
            source = source,
            wikiId = wikiId
        )
    }

    private fun addImages(container: Element, builder: Builder) {
        val images = if (container.normalName() == "img") listOf(container) else container.select("img")
        for (img in images) {
            if (img.attr("data-video-key").isNotBlank()) continue
            val href = img.parents().firstOrNull { it.normalName() == "a" }?.attr("href").orEmpty()
            val raw = sequenceOf(
                href.takeIf { it.contains("static.wikia.nocookie.net") },
                img.attr("data-src"),
                img.attr("src")
            ).firstOrNull { !it.isNullOrBlank() && !it!!.startsWith("data:") } ?: continue
            val url = FandomUrls.normalizeImageUrl(raw) ?: continue
            val caption = container.selectFirst("figcaption, .pi-caption")
                ?.let { FandomHtmlText.inline(it.html()) }
                ?.takeIf { it.isNotBlank() }
                ?: img.attr("alt").trim().takeIf { it.isNotBlank() }
            builder.images += FandomImage(url, caption)
        }
    }

    // ── Birleştirme ──────────────────────────────────────────────────────────

    /**
     * `pageprops` JSON bilgi kutusu ile işlenmiş HTML kutusunu birleştirir. HTML kutusu eksiksizdir
     * (bağlantı metinleri, liste ayırıcıları); JSON ise HTML yoksa ya da daha az alan taşıyorsa korunur.
     * Görseller JSON'dan gelir (tam boy adres), yoksa HTML'den.
     */
    fun mergeInfobox(json: FandomInfobox?, dom: FandomInfobox?): FandomInfobox? {
        if (dom == null || dom.isEmpty()) return json
        if (json == null || json.isEmpty()) return dom
        val jsonFields = json.allFields()
        val domFields = dom.allFields()
        val groups = if (domFields.size >= jsonFields.size) {
            dom.groups
        } else {
            // HTML kutusu daha az alan çıkardıysa (özel şablon), JSON'u temel al ve yalnızca
            // HTML'de daha eksiksiz olan değerleri yerine koy.
            val byLabel = domFields.associateBy { labelKey(it.label) }
            json.groups.map { group ->
                group.copy(fields = group.fields.map { field ->
                    val better = byLabel[labelKey(field.label)]
                    if (better != null && better.value.length > field.value.length) {
                        field.copy(value = better.value, links = better.links.ifEmpty { field.links })
                    } else {
                        field
                    }
                })
            }
        }
        return FandomInfobox(
            title = json.title ?: dom.title,
            images = json.images.ifEmpty { dom.images },
            groups = groups
        )
    }

    private fun labelKey(label: String): String = FandomNameVariants.normalizeForCompare(label)

    /** Girişin tam metni, kesik (`...`) kısa açıklamadan uzunsa onun yerine kullanılır. */
    fun mergeDescription(short: String?, intro: String?): String? {
        val shortText = short?.takeIf { it.isNotBlank() }
        val introText = intro?.takeIf { it.isNotBlank() }
        if (introText == null) return shortText
        if (shortText == null) return introText
        val shortCore = shortText.trimEnd('.', '…', ' ')
        // Kısa açıklama girişin kesilmiş başı mı? Evetse giriş daha eksiksizdir.
        val prefix = FandomNameVariants.normalizeForCompare(shortCore).take(60)
        val looksTruncated = shortText.endsWith("...") || shortText.endsWith("…")
        return when {
            introText.length > shortText.length &&
                (looksTruncated || FandomNameVariants.normalizeForCompare(introText).contains(prefix)) -> introText
            else -> shortText
        }
    }
}

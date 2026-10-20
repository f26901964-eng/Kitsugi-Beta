package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomBlockLink
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import com.kitsugi.animelist.utils.isTechnicalTranslationValue

/**
 * OTOMATİK ÇEVİRİ KORUMA KATMANI (yapı sözleşmesi)
 * ===============================================
 *
 * Wiki bölümleri ham HTML yerine **yapılandırılmış bloklar** hâlinde saklanır
 * ([FandomSectionContent] → [FandomTextBlock]). Bu sayede:
 *
 *  - liste işaretleri (madde/başlık/alıntı), kartlar ve bağlantı çipleri düzenini bilir,
 *  - görseller kendi konumunda kalır,
 *  - bağlantıların **dokunma hedefi** (URL) metinden bağımsız taşınır.
 *
 * Çeviri bu yapıya dokunmamalıdır. Geçmişte bölüm çevirisi tüm bloğu tek bir düz metne
 * indirip yeniden çiziyordu; bu yüzden çeviri açıldığında:
 *
 *  1. Bağlantılar kopuyordu (URL'ler ve sayfa bağlantıları düz yazıya dönüşüyordu),
 *  2. Sayfa düzeni bozuluyordu (madde işaretleri, kartlar ve başlıklar kaybolup tüm
 *     görseller metnin ALTINA yığılıyordu),
 *  3. Bilgi kutusu değerleri satır satır kayabiliyordu (Google Translate satır
 *     birleştirdiğinde alanlar kayıyor, değerler karışıyordu).
 *
 * Bu dosya o hataların tek merkezden önlenmesini sağlar:
 *
 *  - [translateSectionBlocks]: bölümü **blok blok** çevirir; her blok kendi anahtarına
 *    yazılır. Böylece Google'ın satır birleştirmesi yapıyı bozamaz.
 *  - **Mavi (bağlantı) metinler de çevrilir** — ama asla sağlayıcının insafına bırakılmaz:
 *    her bağlantı etiketi `⟦L2⟧Etiket⟦/2⟧` biçiminde bir **yuva** ile sarılır. Sağlayıcı
 *    cümleyi tek parça hâlinde çevirir (böylece "678 articles" + "ve" gibi boşluksuz
 *    birleşmeler olmaz), etiket tam olarak aynı konumda kalır, URL hiç gönderilmez.
 *  - Jetonlar bozulursa (sağlayıcı bir yuvayı düşürürse) **güvenli yedeğe** düşülür: metin
 *    parça parça çevrilir, etiketler orijinal kalır — bağlantı yine kopmaz.
 *  - Çevrilmiş etiketler blok anahtarının çocuğu olarak saklanır
 *    ([WikiStructuredTranslationKeys.link]) ve [withTranslatedBlocks] bunları
 *    `FandomBlockLink.label` alanına geri yazar; `url` **hiç değişmez**. Metin çevrilmiş
 *    etiketi taşırken bağlantı listesinin orijinal etiketi taşıması, mavi alanların
 *    tıklanabilirliğini kaybetmesi demek olurdu.
 *  - [withTranslatedBlocks]: çevrilmiş metinleri orijinal blok **sırasına ve türüne**
 *    geri oturtur; görsel/kart/bağlantı alanları aynen korunur. Bağlantı çözülemeyecekse
 *    (eski önbellek, kopuk çeviri) o bloğun çevirisi reddedilir — kırık mavi metin yerine
 *    orijinal gösterilir.
 *  - [isTechnicalWikiValue]: URL, dosya adı, `File:` ad alanı gibi teknik değerler
 *    çeviriye hiç gönderilmez.
 *  - [translateRowsPreservingStructure]: çok satırlı (bilgi kutusu) çevirilerde satır
 *    sayısı kanıtlanır; kanıtlanamazsa satır satır yedeğe düşülür — değerler asla kaymaz.
 */
internal object WikiStructuredTranslationKeys {
    /**
     * Bölüm anahtarıyla blok çocuk anahtarını ayıran işaret. Wiki başlıklarında ve
     * sayfa adlarında geçemeyecek bir denetim karakteri kullanılır; böylece çocuk
     * anahtarlar başka bir çeviri anahtarıyla çakışmaz.
     */
    const val SEPARATOR = "\u0000b"

    /** Blok çevirisi tamamlandığında bölüm anahtarına yazılan işaret değeri. */
    const val COMPLETE = "1"

    private const val SUBTITLE_SUFFIX = ":s"

    /** Görsel altı açıklaması (figcaption) için çocuk anahtar son eki. */
    private const val CAPTION_SUFFIX = ":i"

    /** Satır içi (mavi) bağlantı etiketinin çevirisi için çocuk anahtar son eki. */
    private const val LINK_SUFFIX = ":l"

    fun block(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index"

    fun subtitle(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index$SUBTITLE_SUFFIX"

    /** Görselin ALTINDAKİ yazı; görsel URL'si ve dosya adı asla çevrilmez. */
    fun caption(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index$CAPTION_SUFFIX"

    /**
     * Bloğun [FandomTextBlock.links] listesindeki [linkIndex]. sıradaki bağlantının
     * **çevrilmiş etiketi**. Dokunma hedefi (`url`) bu anahtarda tutulmaz: URL zaten
     * blok verisindedir ve çeviriye hiç gönderilmez.
     */
    fun link(baseKey: String, index: Int, linkIndex: Int): String =
        "$baseKey$SEPARATOR$index$LINK_SUFFIX$linkIndex"

    fun isChildKey(key: String): Boolean = key.contains(SEPARATOR)

    fun isChildOf(key: String, baseKey: String): Boolean = key.startsWith(baseKey + SEPARATOR)

    /** Anahtar, verilen bölümün kendisi ya da blok çocuklarından biri mi? */
    fun belongsTo(key: String, baseKey: String): Boolean = key == baseKey || isChildOf(key, baseKey)

    /** Bölüm anahtarına yazılan değerin "çeviri tamam" işareti olup olmadığı. */
    fun isCompleteMarker(value: String?): Boolean = value != null && value == COMPLETE
}

/**
 * Çeviri anahtarından bir bölümün **tüm** izlerini (kendisi + blok çocukları) siler.
 * "Orijinali göster" ve hata temizliği bu yardımcıyı kullanır; yarım kalan blok çevirisi
 * ekranda kalmaz.
 */
internal fun Map<String, String>.withoutTranslation(baseKey: String): Map<String, String> =
    filterKeys { !WikiStructuredTranslationKeys.belongsTo(it, baseKey) }

/**
 * Bir yüzeyde gerçekten uygulama içi çeviri bulunup bulunmadığını söyler.
 *
 * Bölüm çevirileri akış hâlinde geldiği için çocuk blok, bölümün COMPLETE işaretinden önce
 * belleğe düşebilir. Düğmeyi yalnızca ana anahtara bağlamak bu kısa aralıkta (ve eski
 * önbelleklerde) çevrilmiş metni gösterip "Orijinal" düğmesini gizliyordu. Her çeviri
 * yüzeyi bu yardımcıyı kullanarak anahtar + bütün çocukları birlikte denetler.
 */
internal fun Map<String, String>.hasWikiTranslation(baseKey: String): Boolean =
    entries.any { (key, value) ->
        WikiStructuredTranslationKeys.belongsTo(key, baseKey) && value.isNotBlank()
    }

/**
 * Tek bir bloğun çeviri sonucu.
 *
 * [text] çevrilmiş metindir; mavi (bağlantı) etiketleri **tam olarak kendi konumunda**
 * çevrilmiş hâlde gelir. [linkLabels] ise "blok bağlantı listesindeki sıra → çevrilmiş
 * etiket" eşlemesidir. Bu eşleme kalıcılaştırılmazsa metin çevrilmiş etiketi taşırken
 * `links` listesi orijinal etiketi taşır ve mavi alanlar tıklanamaz hâle gelir.
 */
internal data class WikiTranslatedBlockText(
    val text: String,
    val linkLabels: Map<Int, String> = emptyMap()
)

/**
 * Bir bölümün bloklarını **tek tek** çevirir ve blok anahtarına göre sonuçları döner.
 *
 * Kurallar (hepsi "yapı bozulmasın, bağlantı kopmasın" hedefine hizmet eder):
 *  - Görsel URL'leri ve konumları korunur; açıklamaları yerinde çevrilir.
 *  - Bağlantı çipleri ([FandomBlockKind.LINK_GROUP]) yalnızca başlık metniyle çevrilir;
 *    çip etiketleri sayfa adıdır, orijinal kalır (kart başlığıyla aynı kural).
 *  - Satır içi (mavi) bağlantı etiketleri de çevrilir; konumları ve dokunma hedefleri
 *    korunur. Çeviri metni ile `links` etiketleri **birlikte** yazılır.
 *  - Kart başlığı bir sayfaya bağlanıyorsa ([FandomTextBlock.href] dolu) sayfa adı olarak
 *    kalır; yalnızca gerçek cümle görünümlü başlıklar çevrilir (ana sayfa kartları).
 *  - Teknik değerler (URL, dosya adı, `File:` ad alanı) hiç gönderilmez.
 *
 * @param onBlock her çocuk değer tamamlandığında çağrılır (akan UI güncellemesi için):
 *   (çocuk anahtar, çevrilmiş değer). Bağlantı etiketleri metinden ÖNCE yayınlanır ki
 *   metin göründüğü ilk karede mavi alanlar zaten tıklanabilir olsun.
 */
internal suspend fun translateSectionBlocks(
    baseKey: String,
    content: FandomSectionContent,
    onBlock: (childKey: String, value: String) -> Unit = { _, _ -> },
    translate: suspend (String) -> String
): Map<String, String> {
    if (content.blocks.isEmpty()) return emptyMap()
    val entries = LinkedHashMap<String, String>()

    content.blocks.forEachIndexed { index, block ->
        // Blok başına TEK etiket haritası: metin, alt satır ve görsel açıklaması aynı
        // bağlantı için aynı çevrilmiş etiketi taşımalı, yoksa `links` listesi bunlardan
        // birini bulamaz ve mavi alan tıklanabilirliğini kaybeder.
        val blockLabels = LinkedHashMap<Int, String>()

        fun publishLabels(labels: Map<Int, String>) {
            labels.forEach { (linkIndex, label) ->
                if (label.isBlank() || blockLabels[linkIndex] == label) return@forEach
                blockLabels[linkIndex] = label
                val linkKey = WikiStructuredTranslationKeys.link(baseKey, index, linkIndex)
                entries[linkKey] = label
                onBlock(linkKey, label)
            }
        }

        if (block.kind == FandomBlockKind.IMAGE) {
            // Görselin kendisi, URL'si ve dosya adı çeviriye GİTMEZ; yalnızca altındaki
            // açıklama (figcaption) beyaz metin olduğu için çevrilir. Blok sırası ve
            // görselin konumu değişmez. Açıklama mavi bağlantı içeriyorsa o etiket de
            // yerinde çevrilir (yuvalı akış).
            val caption = block.image?.caption
            if (!caption.isNullOrBlank() && !isTechnicalWikiValue(caption)) {
                translateBlockText(caption, block, translate)?.let { translated ->
                    publishLabels(translated.linkLabels)
                    if (translated.text.isNotBlank() && translated.text != caption) {
                        val childKey = WikiStructuredTranslationKeys.caption(baseKey, index)
                        entries[childKey] = translated.text
                        onBlock(childKey, translated.text)
                    }
                }
            }
            return@forEachIndexed
        }

        val sourceText = block.text
        if (shouldTranslateBlockText(block) && !isTechnicalWikiValue(sourceText)) {
            translateBlockText(sourceText, block, translate)?.let { translated ->
                publishLabels(translated.linkLabels)
                if (translated.text.isNotBlank() && translated.text != sourceText) {
                    val childKey = WikiStructuredTranslationKeys.block(baseKey, index)
                    entries[childKey] = translated.text
                    onBlock(childKey, translated.text)
                }
            }
        }

        val subtitle = block.subtitle
        if (block.kind == FandomBlockKind.CARD && !subtitle.isNullOrBlank() && !isTechnicalWikiValue(subtitle)) {
            translateBlockText(
                text = subtitle,
                block = block,
                translate = translate,
                preferredLabels = blockLabels
            )?.let { translated ->
                publishLabels(translated.linkLabels)
                if (translated.text.isNotBlank() && translated.text != subtitle) {
                    val childKey = WikiStructuredTranslationKeys.subtitle(baseKey, index)
                    entries[childKey] = translated.text
                    onBlock(childKey, translated.text)
                }
            }
        }
    }

    return entries
}

/**
 * Kart başlığı bir sayfaya bağlıysa **sayfa adı** olarak kalır: başlık, dokunma hedefinin
 * okunabilir adıdır. Ancak Fandom ana sayfalarındaki kartlarda başlık gerçek bir cümledir
 * ("This wiki is dedicated to everything related to…"); bunlar çevrilmediğinde sayfada
 * yarım kalmış İngilizce paragraf kalıyordu. Cümle görünümlü kart başlığı çevrilir,
 * `href` yine korunur.
 */
private fun shouldTranslateBlockText(block: FandomTextBlock): Boolean {
    if (block.kind != FandomBlockKind.CARD) return true
    if (block.href.isNullOrBlank()) return true
    return block.text.looksLikeSentence()
}

/** Sayfa adı mı, cümle mi? Cümleler uzun ve çok kelimelidir; kısa adlar olduğu gibi kalır. */
private fun String.looksLikeSentence(): Boolean {
    val trimmed = trim()
    if (trimmed.length >= 80) return true
    if (trimmed.count { it == ' ' } >= 8) return true
    return trimmed.endsWith("!") || trimmed.endsWith("?") || trimmed.endsWith("…")
}

// --------------------------------------------------------------- yuvalı (mavi) çeviri

/**
 * Bağlantı etiketlerini çeviriye "yuvalı" göndermek için kullanılan jetonlar:
 * `⟦L2⟧Shido⟦/2⟧`. Sağlayıcı cümleyi bir bütün olarak çevirir, etiket tam olarak aynı
 * konumda kalır, URL hiç gönderilmez. Tek istek yeter: hem cümle hem etiket çevrilir.
 *
 * Jetonlar bilinçli olarak `⟪…⟫` biçiminde DEĞİL: çeviri altyapısı yanıt sonundaki
 * tanınmayan `⟪…⟫` jetonlarını temizler, `⟦…⟧` biçimi o temizlikten etkilenmez.
 */
private const val LINK_MARK_OPEN = "⟦L"
private const val LINK_MARK_END = "⟦/"
private const val LINK_MARK_CLOSE = "⟧"

private fun linkOpenMark(slot: Int): String = "$LINK_MARK_OPEN$slot$LINK_MARK_CLOSE"

private fun linkCloseMark(slot: Int): String = "$LINK_MARK_END$slot$LINK_MARK_CLOSE"

private fun linkOpenRegex(slot: Int): Regex = Regex("""⟦\s*L\s*$slot\s*⟧""")

private fun linkCloseRegex(slot: Int): Regex = Regex("""⟦\s*/\s*$slot\s*⟧""")

/** Yanıtta kalan (bozulmuş) jetonlar: kullanıcı asla `⟦L0⟧` görmemeli. */
private val LEFTOVER_LINK_MARK = Regex("""⟦\s*/?\s*L?\s*\d+\s*⟧""")

/** Yuva yer tutucusu; etiketler çeviri yanıtından ayrıştırıldıktan sonra geri yazılır. */
private const val SLOT_MARK = "\u0001"

private fun slotPlaceholder(slot: Int): String = "$SLOT_MARK$slot$SLOT_MARK"

/** Bir bağlantının metindeki yeri + hangi bağlantıya gittiği. */
internal data class WikiLinkRange(
    val range: IntRange,
    val link: FandomBlockLink,
    /** [FandomTextBlock.links] içindeki sıra; çevrilmiş etiket anahtarı bununla yazılır. */
    val linkIndex: Int = -1
)

internal fun wikiLinkRanges(text: String, links: List<FandomBlockLink>): List<WikiLinkRange> {
    val candidates = ArrayList<WikiLinkRange>()
    links.asSequence()
        .withIndex()
        .filter { it.value.label.isNotBlank() && it.value.url.isNotBlank() }
        .groupBy { it.value.label }
        .entries.sortedByDescending { it.key.length }
        .forEach { (label, indexed) ->
            var occurrence = 0
            var from = 0
            while (from < text.length) {
                val start = text.indexOf(label, from)
                if (start < 0) break
                val end = start + label.length
                val range = start until end
                // "Rick" bağlantısı "Rickshaw" sözcüğünün içini bağlantıya dönüştürmesin.
                val leftBoundary = start == 0 || !label.first().isLatinWordChar() || !text[start - 1].isLatinWordChar()
                val rightBoundary = end == text.length || !label.last().isLatinWordChar() || !text[end].isLatinWordChar()
                if (leftBoundary && rightBoundary && candidates.none {
                        range.first <= it.range.last && it.range.first <= range.last
                    }) {
                    val target = indexed[occurrence.coerceAtMost(indexed.lastIndex)]
                    candidates += WikiLinkRange(range, target.value, target.index)
                    occurrence++
                }
                from = end
            }
        }
    return candidates.sortedBy { it.range.first }
}

// CJK metinlerde sözcükler arası boşluk aranmaz; bitişik Japonca/Çince bağlantıları koru.
private fun Char.isLatinWordChar(): Boolean =
    isDigit() || Character.UnicodeScript.of(code) == Character.UnicodeScript.LATIN

/**
 * Blok metnini çevirir; bağlantı varsa etiketleri **yuva** içinde gönderir.
 *
 * Dönen metin her zaman blok sırasına uyar. Etiketlerin çevrilmiş hâli
 * ([WikiTranslatedBlockText.linkLabels]) çağıran tarafından kalıcılaştırılır.
 *
 * [preferredLabels] bir blokta daha önce (ör. başlık metninde) çevrilmiş etiketleri taşır;
 * aynı `links` listesini paylaşan alt satır da bu etiketleri kullanır ki tek bir bağlantı
 * için iki farklı çeviri oluşmasın.
 */
private suspend fun translateBlockText(
    text: String,
    block: FandomTextBlock,
    translate: suspend (String) -> String,
    preferredLabels: Map<Int, String> = emptyMap()
): WikiTranslatedBlockText? {
    val links = block.inlineTranslationLinks()
    if (links.isEmpty()) {
        return translateOrNull(translate, text)?.let { WikiTranslatedBlockText(it) }
    }

    val ranges = wikiLinkRanges(text, links)
    if (ranges.isEmpty()) {
        return translateOrNull(translate, text)?.let { WikiTranslatedBlockText(it) }
    }

    // TEK istek: cümle + etiketler birlikte. Böylece "678 articles" + "ve" gibi parça
    // parça çeviriden doğan boşluksuz birleşmeler olmaz ve ek etiket isteği gerekmez.
    val marked = markLinkSpans(text, ranges)
    val translatedMarked = translateOrNull(translate, marked) ?: return null

    val parsed = parseLinkSpans(translatedMarked, ranges.size)
        // Jetonlar bozulduysa güvenli yedek: parçaları ayrı ayrı çevir, etiketleri orijinal
        // bırak. Kırık bağlantı göstermektense mavi metin çevrilmeden kalır.
        ?: return translateLinkedSegments(text, ranges, translate)

    var out = parsed.text
    val labels = LinkedHashMap<Int, String>()
    parsed.slotLabels.forEach { (slot, providerLabel) ->
        val range = ranges[slot]
        val linkIndex = if (range.linkIndex >= 0) range.linkIndex else block.links.indexOf(range.link)
        val label = preferredLabels[linkIndex]?.takeIf { it.isNotBlank() }
            ?: providerLabel.takeIf { it.isNotBlank() }
            ?: range.link.label
        if (linkIndex >= 0) labels[linkIndex] = label
        out = out.replace(slotPlaceholder(slot), label)
    }

    if (labels.isEmpty()) {
        return WikiTranslatedBlockText(out)
    }
    // Yalnızca gerçekten değişen etiketler saklanır; orijinaliyle aynı kalan etiket
    // gereksiz anahtar üretmez.
    val changedLabels = labels.filterKeys { linkIndex ->
        block.links.getOrNull(linkIndex)?.label != labels[linkIndex]
    }
    return WikiTranslatedBlockText(out, changedLabels)
}

/** Metindeki bağlantı etiketlerini `⟦L0⟧Etiket⟦/0⟧` yuvalarına çevirir. */
private fun markLinkSpans(text: String, ranges: List<WikiLinkRange>): String {
    val builder = StringBuilder(text.length + ranges.size * 8)
    var cursor = 0
    ranges.forEachIndexed { slot, range ->
        builder.append(text, cursor, range.range.first)
        builder.append(linkOpenMark(slot))
        builder.append(text, range.range.first, range.range.last + 1)
        builder.append(linkCloseMark(slot))
        cursor = range.range.last + 1
    }
    builder.append(text, cursor, text.length)
    return builder.toString()
}

private class ParsedLinkSpans(
    /** Yuvaların yerine [slotPlaceholder] konmuş metin (yuva sırasıyla). */
    val text: String,
    /** Yuva → sağlayıcının döndürdüğü etiket metni. */
    val slotLabels: Map<Int, String>
)

/**
 * Çevrilmiş metindeki yuvaları çözer.
 *
 * Her yuva **tam olarak bir kez** açılıp kapanmalı ve yuvalar üst üste binmemelidir. Aksi
 * hâlde `null` döner: eksik/kaymış bir jetonla devam etmek, bir bağlantının cümlenin
 * tamamını yutmasına (ya da metnin bir bölümünün kaybolmasına) yol açabilir. Bu durumda
 * güvenli yedeğe düşülür.
 */
private fun parseLinkSpans(marked: String, slotCount: Int): ParsedLinkSpans? {
    if (slotCount <= 0) return ParsedLinkSpans(marked.replace(LEFTOVER_LINK_MARK, ""), emptyMap())

    val slots = ArrayList<IntArray>(slotCount) // [openStart, openEnd, closeStart, closeEnd, slot]
    for (slot in 0 until slotCount) {
        val openMatch = linkOpenRegex(slot).findAll(marked).toList()
        val closeMatch = linkCloseRegex(slot).findAll(marked).toList()
        if (openMatch.size != 1 || closeMatch.size != 1) return null
        val open = openMatch.first()
        val close = closeMatch.first()
        if (close.range.first <= open.range.last) return null
        slots += intArrayOf(open.range.first, open.range.last + 1, close.range.first, close.range.last + 1, slot)
    }

    slots.sortBy { it[0] }
    var cursor = 0
    val builder = StringBuilder(marked.length)
    val labels = LinkedHashMap<Int, String>()
    for (slot in slots) {
        val openStart = slot[0]
        val openEnd = slot[1]
        val closeStart = slot[2]
        val closeEnd = slot[3]
        val slotIndex = slot[4]
        if (openStart < cursor || closeStart < openEnd) return null
        builder.append(marked, cursor, openStart)
        val label = marked.substring(openEnd, closeStart).trim()
        builder.append(slotPlaceholder(slotIndex))
        labels[slotIndex] = label
        cursor = closeEnd
    }
    builder.append(marked, cursor, marked.length)
    return ParsedLinkSpans(builder.toString().replace(LEFTOVER_LINK_MARK, ""), labels)
}

/**
 * GÜVENLİ YEDEK yol: bağlantı etiketlerine dokunmadan yalnızca **etiketler arasındaki**
 * metni çevirir. Jetonlar bozulduğunda kullanılır; mavi metin orijinal dilinde kalır ama
 * konumu, dokunma hedefi ve cümle yapısı korunur.
 */
private suspend fun translateLinkedSegments(
    text: String,
    ranges: List<WikiLinkRange>,
    translate: suspend (String) -> String
): WikiTranslatedBlockText? {
    val result = StringBuilder(text.length)
    var cursor = 0
    for (range in ranges) {
        if (cursor < range.range.first) {
            result.append(translateTextSegment(text.substring(cursor, range.range.first), translate))
        }
        result.append(text.substring(range.range))
        cursor = range.range.last + 1
    }
    if (cursor < text.length) {
        result.append(translateTextSegment(text.substring(cursor), translate))
    }
    return result.toString().takeIf { it.isNotBlank() }?.let { WikiTranslatedBlockText(it) }
}

/**
 * Bağlantı etiketleri arasındaki boşluk/noktalama parçalarını gereksiz ağ isteğine çevirmez.
 * Çevrilebilir bir parça başarısız olursa yalnızca o parça orijinal kalır; tüm paragraf değil.
 */
private suspend fun translateTextSegment(
    segment: String,
    translate: suspend (String) -> String
): String {
    if (segment.isBlank() || segment.none(Char::isLetter)) return segment
    val leading = segment.takeWhile(Char::isWhitespace)
    val trailing = segment.takeLastWhile(Char::isWhitespace)
    val coreEnd = segment.length - trailing.length
    val core = segment.substring(leading.length, coreEnd)
    if (core.isBlank() || isTechnicalWikiValue(core)) return segment
    val translated = translateOrNull(translate, core)?.takeIf { it.isNotBlank() } ?: core
    return leading + translated + trailing
}

/**
 * Çeviriyi güvenle çağırır: hata durumunda `null` döner, ancak **coroutine iptali**
 * yeniden fırlatılır (iptal edilen ekranın çevirisi arka planda devam etmesin).
 */
private suspend fun translateOrNull(
    translate: suspend (String) -> String,
    text: String
): String? = try {
    translate(text).trim()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * Çevrilmiş blok metinlerini **orijinal yapıya** geri oturtur.
 *
 * Blok sayısı, tür sırası, görseller, kartlar, `href` ve bağlantı **URL'leri** birebir
 * korunur; yalnızca metin alanları ve bağlantıların görünen etiketleri değişir. Hiçbir
 * blok için çeviri yoksa `null` döner (çağıran orijinal içeriği çizer).
 *
 * Ekran okuyucu/önbellek güvenliği: bir bloğun çevrilmiş metni kendi bağlantılarını
 * çözemiyorsa (eski önbellek, kopuk çeviri) o yüzeyin çevirisi **reddedilir**; mavi alanlar
 * tıklanamaz hâle gelmez.
 */
internal fun FandomSectionContent.withTranslatedBlocks(
    baseKey: String,
    translations: Map<String, String>
): FandomSectionContent? {
    if (blocks.isEmpty() || translations.isEmpty()) return null
    var changed = false
    val next = blocks.mapIndexed { index, block ->
        val rawText = translations[WikiStructuredTranslationKeys.block(baseKey, index)]
        val rawSubtitle = translations[WikiStructuredTranslationKeys.subtitle(baseKey, index)]
        val rawCaption = translations[WikiStructuredTranslationKeys.caption(baseKey, index)]

        // Mavi metinlerin çevrilmiş etiketleri: yalnızca `label` değişir, `url` asla.
        val relinked = block.links.mapIndexed { linkIndex, link ->
            val translatedLabel = translations[WikiStructuredTranslationKeys.link(baseKey, index, linkIndex)]
                ?.takeIf { it.isNotBlank() }
            if (translatedLabel == null || translatedLabel == link.label) link else link.copy(label = translatedLabel)
        }

        val translatedText = rawText?.takeIf { candidate ->
            shouldTranslateBlockText(block) &&
                !isTechnicalWikiValue(candidate) &&
                keepsLinks(block.text, candidate, block.links, relinked)
        }
        val translatedSubtitle = rawSubtitle?.takeIf { candidate ->
            !isTechnicalWikiValue(candidate) && keepsLinks(block.subtitle, candidate, block.links, relinked)
        }
        val translatedCaption = rawCaption?.takeIf { candidate ->
            block.image != null && !isTechnicalWikiValue(candidate) &&
                keepsLinks(block.image?.caption, candidate, block.links, relinked)
        }
        val image = if (translatedCaption != null) {
            block.image?.copy(caption = translatedCaption)
        } else {
            block.image
        }
        // Etiket çevirisi yalnızca gerçekten uygulanan bir yüzey varsa devreye girer;
        // aksi hâlde orijinal (çevrilmemiş) metin kendi etiketini kaybederdi.
        val appliedAny = translatedText != null || translatedSubtitle != null || translatedCaption != null
        val links = if (appliedAny) relinked else block.links

        if (translatedText == null && translatedSubtitle == null &&
            image == block.image && links == block.links
        ) {
            block
        } else {
            changed = true
            FandomTextBlock(
                kind = block.kind,
                text = translatedText ?: block.text,
                image = image,
                href = block.href,
                subtitle = translatedSubtitle ?: block.subtitle,
                links = links
            )
        }
    }
    return if (changed) copy(blocks = next) else null
}

/**
 * Bir yüzeyin (paragraf / alt satır / görsel açıklaması) mavi bağlantıları çeviriden
 * sonra da bulunabiliyor mu?
 *
 * Yalnızca **orijinal metinde gerçekten bağlantı varsa** denetim yapılır: bağlantısı
 * olmayan yüzeyler serbestçe çevrilir. Orijinalde bağlantı varken çevrilmiş metinde hiç
 * bağlantı bulunamıyorsa o çeviri reddedilir — kırık/tıklanamaz mavi metin göstermektense
 * orijinal gösterilir.
 */
private fun keepsLinks(
    original: String?,
    candidate: String,
    originalLinks: List<FandomBlockLink>,
    translatedLinks: List<FandomBlockLink>
): Boolean {
    if (originalLinks.isEmpty() || original.isNullOrBlank()) return true
    if (wikiLinkRanges(original, originalLinks).isEmpty()) return true
    if (candidate.isBlank()) return false
    return wikiLinkRanges(candidate, translatedLinks).isNotEmpty()
}

// LINK_GROUP bağlantıları başlığın içinde değil, ayrı çiplerde çizilir; çip etiketleri
// sayfa adıdır ve kart başlığıyla aynı kurala uyar (orijinal kalır).
private fun FandomTextBlock.inlineTranslationLinks(): List<FandomBlockLink> =
    if (kind == FandomBlockKind.LINK_GROUP) emptyList() else links

/** IMAGE metni eski kayıtlarda boş olabilir; çizicinin gösterdiği açıklamayı kullan. */
private fun FandomTextBlock.translationText(): String =
    if (kind == FandomBlockKind.IMAGE) image?.caption ?: text else text

/** Görselden oluşan bir bölümde de çeviri/orijinal düğmesi görünür olmalı. */
internal fun FandomSectionContent.wikiTranslationSourceText(): String =
    blocks.joinToString("\n\n") { block ->
        listOfNotNull(block.translationText().takeIf { it.isNotBlank() }, block.subtitle)
            .joinToString(" — ")
    }

/**
 * "Kopyala" düğmeleri için metin: yapı korunur (madde işaretleri ve kart sırası aynı kalır),
 * orijinal görünümdeyken hiçbir şey değişmez.
 *
 * [flatFallback] yalnızca yapı bilgisi olmayan eski/önbellek çevirileri için kullanılır.
 */
internal fun FandomSectionContent.copyTextFor(
    baseKey: String,
    translations: Map<String, String>,
    showOriginal: Boolean,
    flatFallback: String? = null
): String {
    if (showOriginal) return plainText()
    withTranslatedBlocks(baseKey, translations)?.let { return it.plainText() }
    // Düz metin yedeği yalnızca gerçek bir çeviri ise kullanılır; bölüm anahtarındaki
    // "tamam" işareti panoya kopyalanmaz.
    return flatFallback?.takeIf { blocks.isEmpty() }
        ?.takeIf { it.isNotBlank() && !WikiStructuredTranslationKeys.isCompleteMarker(it) }
        ?: plainText()
}

/**
 * Çeviriye gönderilmemesi gereken teknik değerler: URL'ler, dosya adları, `File:`/`Category:`
 * ad alanı değerleri ve rakam/işaret ağırlıklı jetonlar. Google Translate bunları
 * bozar (boşluk ekler, yol parçasını çevirir) ve bağlantı erişilemez hâle gelir.
 *
 * Karar mantığı çeviri katmanıyla ortaktır: [isTechnicalTranslationValue]. Böylece wiki
 * arayüzü ile tek metin çevirisi (açıklama, özet, arama) aynı kuralı uygular.
 */
internal fun isTechnicalWikiValue(value: String): Boolean =
    isTechnicalTranslationValue(value)

/**
 * Çok satırlı çeviride **satır sayısını kanıtlar**; kanıtlayamazsa satır satır çevirir.
 *
 * Bilgi kutusu değerleri tek istekte çevrildiğinde Google Translate satırları
 * birleştirebiliyor ya da bölebiliyordu; böylece etiketler ve değerler birbirine kayıyordu.
 * Burada satırlar `⟪§⟫` ayırıcı jetonuyla gönderilir:
 *
 *  - ayırıcı sayısı satır sayısını veriyorsa tek istekle biter (hızlı yol),
 *  - aksi hâlde her satır kendi başına çevrilir (güvenli yol),
 *  - teknik satırlar hiç gönderilmez, yerlerinde kalır.
 *
 * Dönen liste her zaman `rows` ile **aynı uzunluktadır**: hangi yol seçilirse seçilsin
 * alan sırası kaymaz.
 */
internal suspend fun translateRowsPreservingStructure(
    rows: List<String>,
    translate: suspend (String) -> String
): List<String> {
    if (rows.isEmpty()) return emptyList()
    val result = rows.toMutableList()
    val targets = rows.withIndex().filter { !isTechnicalWikiValue(it.value) }
    if (targets.isEmpty()) return result

    if (targets.size == 1) {
        val target = targets.first()
        translateOrNull(translate, target.value)?.takeIf { it.isNotBlank() }?.let { translated ->
            result[target.index] = translated
        }
        return result
    }

    val joined = targets.joinToString("\n$ROW_SENTINEL\n") { it.value }
    val translatedJoined = translateOrNull(translate, joined)
    if (!translatedJoined.isNullOrBlank()) {
        val parts = translatedJoined.split(ROW_SENTINEL_REGEX).map { it.trim() }
        if (parts.size == targets.size && parts.all { it.isNotBlank() }) {
            targets.forEachIndexed { position, target -> result[target.index] = parts[position] }
            return result
        }
    }

    // Güvenli yol: satır satır çeviri. Başarısız satır orijinaliyle kalır.
    targets.forEach { target ->
        translateOrNull(translate, target.value)?.takeIf { it.isNotBlank() }?.let { translated ->
            result[target.index] = translated
        }
    }
    return result
}

/** Satır sınırı jetonu: çeviriye gönderilen tek istekte satır sayısını kanıtlar. */
private const val ROW_SENTINEL = "⟪§⟫"

private val ROW_SENTINEL_REGEX = Regex("""⟪\s*§\s*⟫""")

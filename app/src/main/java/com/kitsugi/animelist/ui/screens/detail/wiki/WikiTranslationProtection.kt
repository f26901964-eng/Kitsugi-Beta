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
 *  - Bağlantı etiketleri çeviriye gönderilmez; yalnızca aralarındaki düz metin
 *    çevrilir. Bağlantı sırası ve etiketleri sağlayıcının yanıtından bağımsız korunur.
 *  - [withTranslatedBlocks]: çevrilmiş metinleri orijinal blok **sırasına ve türüne**
 *    geri oturtur; görsel/kart/bağlantı alanları aynen korunur.
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

    fun block(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index"

    fun subtitle(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index$SUBTITLE_SUFFIX"

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
 * Bir bölümün bloklarını **tek tek** çevirir ve blok anahtarına göre sonuçları döner.
 *
 * Kurallar (hepsi "yapı bozulmasın" hedefine hizmet eder):
 *  - Görsel URL'leri ve konumları korunur; açıklamaları yerinde çevrilir.
 *  - Bağlantı çipleri ([FandomBlockKind.LINK_GROUP]) yalnızca başlık metniyle çevrilir;
 *    çip etiketleri sayfa adıdır, orijinal kalır.
 *  - Kart başlığı bir sayfaya bağlanıyorsa ([FandomTextBlock.href] dolu) çevrilmez;
 *    yalnızca alt satırdaki bağlantı dışı metin (tarih vb.) çevrilir.
 *  - Bağlantı içeren paragraflarda yalnızca bağlantılar arasındaki yazılar çevrilir.
 *  - Teknik değerler (URL, dosya adı, `File:` ad alanı) hiç gönderilmez.
 *
 * @param onBlock her blok tamamlandığında çağrılır (akan UI güncellemesi için):
 *   (çocuk anahtar, çevrilmiş değer).
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
        val sourceText = block.translationText()
        if (shouldTranslateBlockText(block) && !isTechnicalWikiValue(sourceText)) {
            val translated = translateBlockText(sourceText, block, translate)
            if (!translated.isNullOrBlank() && translated != sourceText) {
                val childKey = WikiStructuredTranslationKeys.block(baseKey, index)
                entries[childKey] = translated
                onBlock(childKey, translated)
            }
        }

        val subtitle = block.subtitle
        if (block.kind == FandomBlockKind.CARD && !subtitle.isNullOrBlank() && !isTechnicalWikiValue(subtitle)) {
            val translatedSubtitle = translateBlockText(subtitle, block, translate)
            if (!translatedSubtitle.isNullOrBlank() && translatedSubtitle != subtitle) {
                val childKey = WikiStructuredTranslationKeys.subtitle(baseKey, index)
                entries[childKey] = translatedSubtitle
                onBlock(childKey, translatedSubtitle)
            }
        }
    }

    return entries
}

/**
 * Kart başlığı bir wiki sayfasına bağlıysa orijinal kalır: başlık, dokunma hedefinin
 * (URL) okunabilir adıdır. Kullanıcı çevrilmiş adı görüp başka bir sayfaya gitmesin.
 */
private fun shouldTranslateBlockText(block: FandomTextBlock): Boolean {
    if (block.kind != FandomBlockKind.CARD) return true
    return block.href.isNullOrBlank()
}

/**
 * Bağlantı içeren metinlerde etiketleri koruyarak çevirir.
 * Etiketler ve sıraları asla sağlayıcıya emanet edilmez.
 */
private suspend fun translateBlockText(
    text: String,
    block: FandomTextBlock,
    translate: suspend (String) -> String
): String? {
    val links = block.inlineTranslationLinks()
    if (links.isEmpty()) {
        return translateOrNull(translate, text)
    }

    // Bir cümleyi jetonlarla topluca çevirmek etiketlerin yer değiştirmesine yol açabilir.
    // Yalnızca etiketler ARASINDAKİ metni çevir; bağlantıları kendi yuvalarında tut.
    val protectedRanges = linkLabelRanges(text, links)
    if (protectedRanges.isEmpty()) return translateOrNull(translate, text)

    val result = StringBuilder(text.length)
    var cursor = 0
    for (range in protectedRanges) {
        if (cursor < range.first) {
            result.append(translateTextSegment(text.substring(cursor, range.first), translate))
        }
        result.append(text.substring(range.first, range.last + 1))
        cursor = range.last + 1
    }
    if (cursor < text.length) {
        result.append(translateTextSegment(text.substring(cursor), translate))
    }
    return result.toString().takeIf { it.isNotBlank() }
}

/** Çevirici ve çizici aynı, çakışmayan bağlantı sınırlarını kullanır. */
internal data class WikiLinkRange(val range: IntRange, val link: FandomBlockLink)

internal fun wikiLinkRanges(text: String, links: List<FandomBlockLink>): List<WikiLinkRange> {
    val candidates = ArrayList<WikiLinkRange>()
    links.asSequence()
        .filter { it.label.isNotBlank() && it.url.isNotBlank() }
        .groupBy { it.label }
        .entries.sortedByDescending { it.key.length }
        .forEach { (label, targets) ->
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
                    candidates += WikiLinkRange(range, targets[occurrence.coerceAtMost(targets.lastIndex)])
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

private fun linkLabelRanges(text: String, links: List<FandomBlockLink>): List<IntRange> =
    wikiLinkRanges(text, links).map { it.range }

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
 * Blok sayısı, tür sırası, görseller, kartlar, `href` ve bağlantı listeleri birebir korunur;
 * yalnızca metin alanları değişir. Hiçbir blok için çeviri yoksa `null` döner (çağıran
 * orijinal içeriği çizer).
 */
internal fun FandomSectionContent.withTranslatedBlocks(
    baseKey: String,
    translations: Map<String, String>
): FandomSectionContent? {
    if (blocks.isEmpty() || translations.isEmpty()) return null
    var changed = false
    val next = blocks.mapIndexed { index, block ->
        // Eski/bozuk önbellek de bağlı kart başlığını veya link etiketini değiştiremez.
        val text = translations[WikiStructuredTranslationKeys.block(baseKey, index)]
            ?.takeIf { shouldTranslateBlockText(block) }
            ?.takeIf { preservesWikiLinks(block.translationText(), it, block.inlineTranslationLinks()) }
        val subtitle = translations[WikiStructuredTranslationKeys.subtitle(baseKey, index)]
            ?.takeIf { block.kind == FandomBlockKind.CARD && block.subtitle != null }
            ?.takeIf { preservesWikiLinks(block.subtitle.orEmpty(), it, block.inlineTranslationLinks()) }
        if (text == null && subtitle == null) {
            block
        } else {
            changed = true
            block.copy(
                text = text ?: block.text,
                image = if (block.kind == FandomBlockKind.IMAGE && text != null) {
                    block.image?.copy(caption = text)
                } else block.image,
                subtitle = subtitle ?: block.subtitle
            )
        }
    }
    return if (changed) copy(blocks = next) else null
}

// LINK_GROUP bağlantıları başlığın içinde değil, ayrı çiplerde çizilir.
private fun FandomTextBlock.inlineTranslationLinks(): List<FandomBlockLink> =
    if (kind == FandomBlockKind.LINK_GROUP) emptyList() else links

/** IMAGE metni eski kayıtlarda boş olabilir; çizicinin gösterdiği açıklamayı kullan. */
private fun FandomTextBlock.translationText(): String =
    if (kind == FandomBlockKind.IMAGE) image?.caption ?: text else text

private fun preservesWikiLinks(source: String, translated: String, links: List<FandomBlockLink>): Boolean {
    if (translated.isBlank()) return false
    fun labels(text: String) = linkLabelRanges(text, links).map { text.substring(it) }
    return labels(source) == labels(translated)
}

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

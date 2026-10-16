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
 *  - [WikiLinkLabelMask]: bağlantı etiketleri çeviriye gönderilmez; çeviri döndüğünde
 *    etiketler **birebir** yerine konur. Jeton kaybolursa çeviri güvenilmez sayılır ve
 *    blok orijinal hâliyle kalır (kopuk bağlantı hiçbir zaman gösterilmez).
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

    /** Görsel altı açıklaması (figcaption) için çocuk anahtar son eki. */
    private const val CAPTION_SUFFIX = ":i"

    fun block(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index"

    fun subtitle(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index$SUBTITLE_SUFFIX"

    /** Görselin ALTINDAKİ yazı; görsel URL'si ve dosya adı asla çevrilmez. */
    fun caption(baseKey: String, index: Int): String = "$baseKey$SEPARATOR$index$CAPTION_SUFFIX"

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
 * Metin içindeki bağlantı etiketlerini çeviriye göndermeden önce jetonlarla maskeler.
 *
 * Neden: Etiketler wiki sayfa adlarıdır ("Yume Tōrō", "Your Name"…). Google Translate bunları
 * çevirdiğinde tıklama hedefi ile ekranda görünen ad birbirini tutmaz; dahası tıklanabilir
 * alanın metni kaybolur. Burada etiketler korunur, cümlenin kalanı çevrilir.
 */
internal class WikiLinkLabelMask private constructor(
    /** Çeviriye gönderilecek metin (etiketler jetonla değiştirilmiş). */
    val masked: String,
    private val labels: List<String>,
    /** Etiket sırası → metinde maskelenen tekrar sayısı. */
    private val occurrences: Map<Int, Int>
) {
    /** Maskelenecek etiket yoksa çeviri doğrudan kullanılır. */
    val hasMaskedLabels: Boolean get() = occurrences.isNotEmpty()

    /**
     * Çevrilmiş metni geri kurar.
     *
     * @return etiketler birebir yerine konmuş metin; jetonlar kayboldu/çoğaldı ise `null`
     *   (bu durumda çağıran blok orijinaliyle kalır — bozuk bağlantı gösterilmez).
     */
    fun restore(translated: String): String? {
        if (occurrences.isEmpty()) return translated
        var result = translated
        for ((index, expected) in occurrences) {
            val pattern = tokenRegex(index)
            val found = pattern.findAll(result).count()
            if (found != expected) return null
            result = pattern.replace(result, Regex.escapeReplacement(labels[index]))
        }
        // Google'ın uydurduğu/bozduğu artık jeton varsa metin güvenilmez.
        if (ANY_TOKEN_REGEX.containsMatchIn(result)) return null
        return result
    }

    companion object {
        private fun tokenRegex(index: Int) = Regex("""⟪\s*L\s*$index\s*⟫""")

        private val ANY_TOKEN_REGEX = Regex("""⟪\s*L\s*\d+\s*⟫""")

        private fun token(index: Int) = "⟪L$index⟫"

        /**
         * Etiketleri (uzundan kısaya doğru) metinde maskeler. Kısa etiket uzun etiketin
         * parçası olabileceği için sıralama önemlidir.
         */
        fun of(text: String, links: List<FandomBlockLink>): WikiLinkLabelMask {
            val labels = links
                .map { it.label }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByDescending { it.length }
            if (labels.isEmpty() || text.isEmpty()) {
                return WikiLinkLabelMask(text, labels, emptyMap())
            }
            var masked = text
            val occurrences = LinkedHashMap<Int, Int>()
            labels.forEachIndexed { index, label ->
                var count = 0
                while (true) {
                    val at = masked.indexOf(label)
                    if (at < 0) break
                    // Aynı jeton zaten yerindeyse tekrar maskelemeyiz.
                    masked = masked.substring(0, at) + token(index) + masked.substring(at + label.length)
                    count++
                    if (count > 32) break
                }
                if (count > 0) occurrences[index] = count
            }
            return WikiLinkLabelMask(masked, labels, occurrences)
        }
    }
}

/**
 * Bir bölümün bloklarını **tek tek** çevirir ve blok anahtarına göre sonuçları döner.
 *
 * Kurallar (hepsi "yapı bozulmasın" hedefine hizmet eder):
 *  - Görseller çevrilmez ve yerlerinde kalır.
 *  - Bağlantı çipleri ([FandomBlockKind.LINK_GROUP]) yalnızca başlık metniyle çevrilir;
 *    çip etiketleri sayfa adıdır, orijinal kalır.
 *  - Kart başlığı bir sayfaya bağlanıyorsa ([FandomTextBlock.href] dolu ya da kartta
 *    bağlantı varsa) çevrilmez; yalnızca alt satır (tarih vb.) çevrilir.
 *  - Bağlantı içeren paragraflarda etiketler maskelenir; maske geri kurulamazsa blok
 *    orijinal metniyle kalır.
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
        if (block.kind == FandomBlockKind.IMAGE) {
            // Görselin kendisi, URL'si ve dosya adı çeviriye GİTMEZ; yalnızca altındaki
            // açıklama (figcaption) beyaz metin olduğu için çevrilir. Blok sırası ve
            // görselin konumu değişmez.
            val caption = block.image?.caption
            if (!caption.isNullOrBlank() && !isTechnicalWikiValue(caption)) {
                val translatedCaption = translateOrNull(translate, caption)
                if (!translatedCaption.isNullOrBlank() && translatedCaption != caption) {
                    val childKey = WikiStructuredTranslationKeys.caption(baseKey, index)
                    entries[childKey] = translatedCaption
                    onBlock(childKey, translatedCaption)
                }
            }
            return@forEachIndexed
        }

        val sourceText = block.text
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
            val translatedSubtitle = translateOrNull(translate, subtitle)
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
    return block.href.isNullOrBlank() && block.links.isEmpty()
}

/**
 * Bağlantı içeren metinlerde etiketleri koruyarak çevirir.
 * Maske geri kurulamazsa `null` döner (blok orijinaliyle kalır).
 */
private suspend fun translateBlockText(
    text: String,
    block: FandomTextBlock,
    translate: suspend (String) -> String
): String? {
    if (block.links.isEmpty()) {
        return translateOrNull(translate, text)
    }

    /*
     * Bağlantı etiketlerini jetonla birlikte Google'a göndermiyoruz. Google bazı dillerde
     * ⟪L0⟫ benzeri jetonları düşürüyor/değiştiriyor; eski akış bu durumda güvenlik için
     * PARAGRAFIN TAMAMINI orijinal bırakıyordu. Wiki paragraflarının çoğu en az bir bağlantı
     * içerdiğinden kullanıcı pratikte "otomatik çeviri çalışmıyor" görüyordu.
     *
     * Bunun yerine metni tıklanabilir etiket sınırlarında parçalıyoruz: etiketler sonuçta
     * byte-byte aynı kalıyor, yalnızca aralarındaki normal metin çeviriye gidiyor. Böylece
     * URL/dokunma aralıkları korunurken cümlenin geri kalanı her zaman normal otomatik
     * çeviri yolundan geçiyor; geri kurulacak kırılgan bir jeton da kalmıyor.
     */
    // Önce cümlenin tamamını tek istekte çevir: dilbilgisi ve bağlam kalitesi daha iyidir.
    // Sağlayıcı jetonları korursa etiketleri geri koyup hemen kullanırız. Jeton bozulursa
    // aşağıdaki parça yolu devreye girer; artık paragrafın tümü orijinale düşmez.
    val mask = WikiLinkLabelMask.of(text, block.links)
    if (mask.hasMaskedLabels) {
        val whole = translateOrNull(translate, mask.masked)
        if (whole != null) mask.restore(whole)?.let { return it }
    }

    val protectedRanges = linkLabelRanges(text, block.links)
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

/** Metinde gerçekten bulunan, çakışmayan bağlantı etiketi aralıkları. */
private fun linkLabelRanges(text: String, links: List<FandomBlockLink>): List<IntRange> {
    val candidates = ArrayList<IntRange>()
    links.asSequence()
        .map { it.label }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length }
        .forEach { label ->
            var from = 0
            while (from < text.length) {
                val start = text.indexOf(label, from)
                if (start < 0) break
                val range = start until (start + label.length)
                if (candidates.none { existing -> range.first <= existing.last && existing.first <= range.last }) {
                    candidates += range
                }
                from = start + label.length
            }
        }
    return candidates.sortedBy { it.first }
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
    if (core.isBlank()) return segment
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
        val text = translations[WikiStructuredTranslationKeys.block(baseKey, index)]
        val subtitle = translations[WikiStructuredTranslationKeys.subtitle(baseKey, index)]
        val caption = translations[WikiStructuredTranslationKeys.caption(baseKey, index)]
        val image = if (caption != null && block.image != null) {
            block.image.copy(caption = caption)
        } else {
            block.image
        }
        if (text == null && subtitle == null && image == block.image) {
            block
        } else {
            changed = true
            FandomTextBlock(
                kind = block.kind,
                text = text ?: block.text,
                image = image,
                href = block.href,
                subtitle = subtitle ?: block.subtitle,
                links = block.links
            )
        }
    }
    return if (changed) copy(blocks = next) else null
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
    return flatFallback
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

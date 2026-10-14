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
        if (block.kind == FandomBlockKind.IMAGE) return@forEachIndexed

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
    val mask = WikiLinkLabelMask.of(text, block.links)
    val translated = translateOrNull(translate, mask.masked) ?: return null
    return mask.restore(translated)
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
        if (text == null && subtitle == null) {
            block
        } else {
            changed = true
            FandomTextBlock(
                kind = block.kind,
                text = text ?: block.text,
                image = block.image,
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

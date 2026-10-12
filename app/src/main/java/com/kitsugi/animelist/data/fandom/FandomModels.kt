package com.kitsugi.animelist.data.fandom

import kotlinx.serialization.Serializable

/*
 * Fandom wiki entegrasyonunun ortak veri modelleri.
 *
 * Eşleştirme sırası (bkz. PLAN_TASK_FANDOM_WIKI_ENTEGRASYONU_2026-10-12.md):
 *   eşleme tablosu → önbellek → Wikidata → ilişki yürüyüşü → son çare: isim (kanıtla doğrulanır).
 *
 * `@Serializable` olan sınıflar `persistent_details_cache` tablosunda JSON olarak saklanır;
 * alan eklerken varsayılan değer vermek eski kayıtların okunabilmesi için zorunludur.
 */

/** Wiki'nin hangi yolla bulunduğu. */
@Serializable
enum class FandomMatchSource {
    /** `assets/fandom/fandom_overrides.json` içindeki elle doğrulanmış kayıt. */
    OVERRIDE,

    /** Wikidata: P4073 (Fandom wiki kimliği) / P6262 (Fandom sayfa kimliği). */
    WIKIDATA,

    /** İlişkili yapımın (önceki sezon, kaynak manga…) kimliği üzerinden. */
    RELATION,

    /** Son çare: isimle bulunan aday, karakter/yerel ad kanıtıyla doğrulandı. */
    NAME,

    /** Kullanıcının onayladığı eşleşme. */
    USER
}

/** Eşleşme güveni. `PROBABLE` sonuçlarda arayüz "Otomatik eşleşme" etiketi gösterir. */
@Serializable
enum class FandomConfidence { CERTAIN, PROBABLE }

/**
 * Bir yapımın (anime/manga) Fandom wiki'si.
 *
 * @property wikiId P4073 biçiminde kimlik: `jujutsu-kaisen`, dil sürümleri için `tr.onepiece`.
 */
@Serializable
data class FandomWikiRef(
    val wikiId: String,
    val source: FandomMatchSource,
    val confidence: FandomConfidence,
    val siteName: String? = null,
    val score: Double? = null
)

@Serializable
data class FandomImage(
    val url: String,
    val caption: String? = null
)

@Serializable
data class FandomInfoboxField(
    val label: String,
    val value: String,
    /** Şablon parametresi (`status`, `age`…); spoiler ve yerel ad tespitinde kullanılır. */
    val source: String? = null,
    /** Spoiler içerebilir (ör. Durum/Status) → arayüzde dokunana kadar gizli. */
    val spoiler: Boolean = false
)

@Serializable
data class FandomInfoboxGroup(
    val header: String? = null,
    val fields: List<FandomInfoboxField> = emptyList()
)

@Serializable
data class FandomInfobox(
    val title: String? = null,
    val images: List<FandomImage> = emptyList(),
    val groups: List<FandomInfoboxGroup> = emptyList()
) {
    fun allFields(): List<FandomInfoboxField> = groups.flatMap { it.fields }

    fun isEmpty(): Boolean =
        title.isNullOrBlank() && images.isEmpty() && groups.all { it.fields.isEmpty() }

    /** Kanıt doğrulaması (ör. Kanji/Hangul ad) için başlık + tüm etiket/değerler. */
    fun searchableText(): String = buildString {
        title?.let { append(it).append(' ') }
        allFields().forEach { field ->
            append(field.label).append(' ').append(field.value).append(' ')
        }
    }
}

/** `action=parse&prop=sections` sonucundaki bir bölüm. */
@Serializable
data class FandomSectionRef(
    val index: Int,
    val title: String,
    val level: Int,
    val spoiler: Boolean = false
)

@Serializable
enum class FandomBlockKind { PARAGRAPH, HEADING, BULLET, QUOTE }

@Serializable
data class FandomTextBlock(
    val kind: FandomBlockKind,
    val text: String
)

/** Bir wiki bölümünün sadeleştirilmiş metni (ham HTML saklanmaz). */
@Serializable
data class FandomSectionContent(
    val blocks: List<FandomTextBlock> = emptyList()
) {
    fun isEmpty(): Boolean = blocks.isEmpty()

    /** Çeviri ve kopyalama için paragraf aralıklı düz metin. */
    fun plainText(): String = blocks.joinToString("\n\n") { block ->
        if (block.kind == FandomBlockKind.BULLET) "• ${block.text}" else block.text
    }
}

/** Karakter Wiki sekmesinde gösterilen sayfa özeti. */
@Serializable
data class FandomCharacterPage(
    val wikiId: String,
    val pageTitle: String,
    val siteName: String? = null,
    val description: String? = null,
    val infobox: FandomInfobox? = null,
    val sections: List<FandomSectionRef> = emptyList(),
    val matchSource: FandomMatchSource,
    val confidence: FandomConfidence,
    /** Sayfa Türkçe wiki'den geliyorsa, eşleşmenin yapıldığı İngilizce wiki/sayfa. */
    val englishWikiId: String? = null,
    val englishPageTitle: String? = null
) {
    val pageUrl: String
        get() = FandomUrls.pageUrl(wikiId, pageTitle)

    /** Eşleştirmenin yapıldığı (kullanıcı "yanlış" derse reddedilecek) wiki/sayfa çifti. */
    val matchedWikiId: String
        get() = englishWikiId ?: wikiId

    val matchedPageTitle: String
        get() = englishPageTitle ?: pageTitle
}

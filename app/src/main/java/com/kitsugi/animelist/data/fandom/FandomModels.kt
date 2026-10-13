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
    val score: Double? = null,
    /** Direct P6262 article title when Wikidata links this item to its exact Fandom page. */
    val pageTitle: String? = null
)

@Serializable
data class FandomImage(
    val url: String,
    val caption: String? = null,
    val fileTitle: String? = null,
    val artist: String? = null,
    val license: String? = null
)

/** Semantic roles inferred from infobox labels and article section headings. */
@Serializable
enum class FandomRelationType { CHARACTER, CAST, VOICE_ACTOR, STAFF, CREW, MUSIC, RELATED }

/** A verified or discoverable page linked from another Fandom article. */
@Serializable
data class FandomWikiLink(
    val wikiId: String,
    val pageTitle: String,
    val label: String,
    val role: FandomRelationType = FandomRelationType.RELATED,
    val language: String? = null,
    val context: String? = null,
    val description: String? = null,
    val imageUrl: String? = null
) {
    val pageUrl: String get() = FandomUrls.pageUrl(wikiId, pageTitle)
}

@Serializable
data class FandomInfoboxField(
    val label: String,
    val value: String,
    /** Şablon parametresi (`status`, `age`…); spoiler ve yerel ad tespitinde kullanılır. */
    val source: String? = null,
    /** Spoiler içerebilir (ör. Durum/Status) → arayüzde dokunana kadar gizli. */
    val spoiler: Boolean = false,
    /** Değerdeki gerçek Fandom madde bağlantıları (karakter, ekip, seslendiren vb.). */
    val links: List<FandomWikiLink> = emptyList()
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
    val blocks: List<FandomTextBlock> = emptyList(),
    /** Linked cast/crew/character pages found in tables and lists in this section. */
    val links: List<FandomWikiLink> = emptyList()
) {
    fun isEmpty(): Boolean = blocks.isEmpty() && links.isEmpty()

    /** Çeviri ve kopyalama için paragraf aralıklı düz metin. */
    fun plainText(): String = blocks.joinToString("\n\n") { block ->
        if (block.kind == FandomBlockKind.BULLET) "• ${block.text}" else block.text
    }
}

/** `action=query&list=search` sonucundaki bir sayfa (başlık + kırpılmış özet). */
data class FandomSearchHit(
    val title: String,
    val snippet: String? = null
)

/**
 * Bir sayfanın kimliği + kısa açıklaması (`fandomdescription`).
 * Evren rehberi çiplerinde, arama sonuçlarında ve seri özetinde kullanılır.
 */
@Serializable
data class FandomPageBlurb(
    val title: String,
    /** Yönlendirmeler çözüldükten sonraki gerçek başlık; sayfa yoksa null. */
    val resolvedTitle: String? = null,
    val description: String? = null
) {
    val exists: Boolean get() = resolvedTitle != null
}

/** Evren rehberi grupları (yapım wiki'sindeki kategori kümeleri). */
@Serializable
enum class FandomGuideGroup { ORGANIZATIONS, ABILITIES, PLACES, TERMS }

@Serializable
data class FandomGuideItem(val title: String)

@Serializable
data class FandomGuideGroupContent(
    val group: FandomGuideGroup,
    /** Üyelerin çekildiği kategori (ör. `Organizations`). */
    val category: String,
    val items: List<FandomGuideItem> = emptyList(),
    /**
     * MediaWiki `cmcontinue` jetonu: `null` ise kategoride bu listeden fazlası yok.
     * "Devamını göster" bu jetonla sonraki sayfayı çeker (14'lük sayfalar).
     */
    val nextToken: String? = null
) {
    /** Kategoride henüz gösterilmeyen üyeler var mı? */
    val hasMore: Boolean get() = nextToken != null
}

/** `list=categorymembers` yanıtının tek sayfası: üye başlıkları + sonraki sayfa jetonu. */
data class FandomCategoryMembersPage(
    val items: List<String> = emptyList(),
    val nextToken: String? = null
)

/**
 * Yapım wiki'sinin "evren rehberi": organizasyonlar, yetenekler, mekânlar, terimler.
 * Çiplerdeki isimler orijinal kalır; açıklamalar isteğe bağlı olarak çevrilir.
 */
@Serializable
data class FandomWorldGuide(val groups: List<FandomGuideGroupContent> = emptyList()) {
    fun isEmpty(): Boolean = groups.all { it.items.isEmpty() }
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
    val englishPageTitle: String? = null,
    /** Wiki'nin bulunduğu yapımın önbellek anahtarı (ör. `al:113415`) — wiki düzeyinde onay/ret için. */
    val mediaKey: String? = null,
    /** Wiki'nin (sayfadan bağımsız olarak) nasıl bulunduğu ve güveni. */
    val wikiSource: FandomMatchSource? = null,
    val wikiConfidence: FandomConfidence? = null,
    /** Main-article gallery images in addition to infobox images. */
    val galleryImages: List<FandomImage> = emptyList(),
    /** Linked characters, cast, voice actors, staff and related articles. */
    val relatedEntities: List<FandomWikiLink> = emptyList()
) {
    val pageUrl: String
        get() = FandomUrls.pageUrl(wikiId, pageTitle)

    /** Otomatik (kanıtı kesinleşmemiş) eşleşme: arayüz "Bu eşleşme doğru mu?" kartı gösterir. */
    val needsConfirmation: Boolean
        get() = confidence == FandomConfidence.PROBABLE

    /**
     * Wiki isimle bulundu ve kesinleşmedi. Kullanıcı "yanlış" derse en olası hata wiki'nin
     * kendisidir; bu durumda wiki de o yapım için reddedilir. Kimlikle bulunan wiki asla
     * reddedilmez (yalnızca sayfa eşleşmesi reddedilir).
     */
    val isWikiUnconfirmedNameMatch: Boolean
        get() = wikiSource == FandomMatchSource.NAME && wikiConfidence == FandomConfidence.PROBABLE

    /** Eşleştirmenin yapıldığı (kullanıcı "yanlış" derse reddedilecek) wiki/sayfa çifti. */
    val matchedWikiId: String
        get() = englishWikiId ?: wikiId

    val matchedPageTitle: String
        get() = englishPageTitle ?: pageTitle
}

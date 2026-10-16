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

/** A plain infobox field used by inline related-page profiles (no nested article links). */
@Serializable
data class FandomProfileField(
    val label: String,
    val value: String,
    val source: String? = null,
    val spoiler: Boolean = false
)

@Serializable
data class FandomProfileGroup(
    val header: String? = null,
    val fields: List<FandomProfileField> = emptyList()
)

/**
 * The profile data shown directly on a parent Wiki page for a related character, cast member,
 * voice actor, crew member, or article. It intentionally contains plain fields rather than an
 * infobox model so serialization cannot recurse through an infobox's linked-page graph.
 */
@Serializable
data class FandomPageProfile(
    val title: String? = null,
    val intro: String? = null,
    val images: List<FandomImage> = emptyList(),
    val groups: List<FandomProfileGroup> = emptyList()
) {
    fun hasDetails(): Boolean = !intro.isNullOrBlank() || images.isNotEmpty() ||
        groups.any { it.fields.isNotEmpty() }

    companion object {
        fun from(infobox: FandomInfobox?, intro: String?): FandomPageProfile? {
            val profile = FandomPageProfile(
                title = infobox?.title,
                intro = intro?.takeIf { it.isNotBlank() },
                images = infobox?.images.orEmpty(),
                groups = infobox?.groups.orEmpty().map { group ->
                    FandomProfileGroup(
                        header = group.header,
                        fields = group.fields.map { field ->
                            FandomProfileField(
                                label = field.label,
                                value = field.value,
                                source = field.source,
                                spoiler = field.spoiler
                            )
                        }
                    )
                }
            )
            return profile.takeIf { it.hasDetails() }
        }
    }
}

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
    val imageUrl: String? = null,
    /** Full intro/infobox fields rendered inline; opening the article is optional. */
    val profile: FandomPageProfile? = null
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

/**
 * Okuyucu blok türleri.
 *  - [CARD]: Görsel + başlık + alt metin (ana sayfadaki "Video Games", "Featured Characters"
 *    gibi kart ızgaraları). Dokunulunca bağlantı hedefi açılır.
 *  - [LINK_GROUP]: Başlıklı bağlantı grubu (navbox'lar, "Capcom Wikis", sosyal medya, şablon
 *    listeleri). Her bağlantı ayrı çip olarak gösterilir.
 */
@Serializable
enum class FandomBlockKind { PARAGRAPH, HEADING, BULLET, QUOTE, IMAGE, CARD, LINK_GROUP }

/** Metin / kart içindeki tıklanabilir bağlantı: görünen etiket + mutlak URL. */
@Serializable
data class FandomBlockLink(
    val label: String,
    val url: String
)

@Serializable
data class FandomTextBlock(
    val kind: FandomBlockKind,
    val text: String,
    /** İçerik bölümlerindeki görsel (IMAGE ve CARD bloklarında doludur). */
    val image: FandomImage? = null,
    /** Bloğun tıklanınca açılacağı mutlak URL (bağlantılı görsel, kart, grup). */
    val href: String? = null,
    /** Kartın ikincil satırı (ör. "Release date: 2027"). */
    val subtitle: String? = null,
    /** Metindeki bağlantılar (satır içi tıklanabilir alanlar veya grup çipleri). */
    val links: List<FandomBlockLink> = emptyList()
)

/** Bir wiki bölümünün sadeleştirilmiş metni (ham HTML saklanmaz). */
@Serializable
data class FandomSectionContent(
    val blocks: List<FandomTextBlock> = emptyList(),
    /** Linked cast/crew/character pages found in tables and lists in this section. */
    val linkedEntities: List<FandomWikiLink> = emptyList(),
    /** Bölümdeki viki içi bağlantılar (görünüm sırasıyla, tekrarsız). */
    val links: List<String> = emptyList(),
    /**
     * Aynı bağlantıların gövdede kaç kez geçtiği. "İlgili maddeler" adaylarını
     * önem sırasına dizmek için kullanılır (Vikipedi'de ülke/bölge gibi tek geçen
     * bilgi kutusu bağlantıları, defalarca geçen karakter/stüdyo bağlantılarının
     * önüne geçmesin).
     */
    val linkCounts: Map<String, Int> = emptyMap()
) {
    fun isEmpty(): Boolean = blocks.isEmpty() && links.isEmpty() && linkedEntities.isEmpty()

    /** Çeviri ve kopyalama için paragraf aralıklı düz metin; görsel URL'leri metne karışmaz. */
    fun plainText(): String = blocks
        .filterNot { it.kind == FandomBlockKind.IMAGE }
        .joinToString("\n\n") { block ->
            when (block.kind) {
                FandomBlockKind.BULLET -> "• ${block.text}"
                FandomBlockKind.CARD -> listOfNotNull(block.text.takeIf { it.isNotBlank() }, block.subtitle)
                    .joinToString(" — ")
                else -> block.text
            }
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
enum class FandomGuideGroup {
    CHARACTERS,
    ORGANIZATIONS,
    STORY_ARCS,
    EPISODES,
    CHAPTERS,
    ABILITIES,
    PLACES,
    TERMS
}

@Serializable
data class FandomGuideItem(val title: String)

/**
 * Bir wiki sayfası için önizleme kartı ("İlgili wiki sayfaları" ve arama/bağlantı
 * sonuçlarında kullanılır). Başlık, küçük açıklama ve varsa kapak görseli taşır;
 * görsel yoksa arayüz başlık ilk harfiyle bir yer tutucu gösterir.
 */
@Serializable
data class FandomRelatedPage(
    val title: String,
    val snippet: String? = null,
    val thumbnailUrl: String? = null,
    /**
     * Vikipedi kısa tanımı (`prop=description`, ör. "Kuzey Amerika'da bir ülke").
     * Özet metinden çok daha kısa ve tür belirtici olduğu için "İlgili maddeler"
     * süzgeci önce bunu okur; Fandom wikilerinde boştur.
     */
    val shortDescription: String? = null,
    /** Expanded profile data so important facts are visible without opening another page. */
    val profile: FandomPageProfile? = null
)

/**
 * Uygulama içi wiki okuyucu için bir sayfanın tamamı: başlık, yönlendirme
 * çözülmüş hâli, kısa özet, kapak görseli, bölümler (İçindekiler).
 */
@Serializable
data class FandomFullPage(
    val title: String,
    val resolvedTitle: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val sections: List<FandomSectionRef> = emptyList(),
    /** A failed TOC request is not the same as a page with zero sections. */
    val sectionsFailed: Boolean = false
) {
    val displayTitle: String get() = resolvedTitle ?: title
}

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
    val relatedEntities: List<FandomWikiLink> = emptyList(),
    /**
     * Aynı kişi/karakter için bulunan diğer kaynaklar (ör. Fandom sayfası varken Vikipedi maddesi).
     * Bu liste yalnızca üst düzey sayfada doludur; içindeki sayfaların kendisi boş tutulur.
     */
    val alternatives: List<FandomCharacterPage> = emptyList()
) {
    val pageUrl: String
        get() = FandomUrls.pageUrl(wikiId, pageTitle)

    /** Kullanılabilir kaynak sayısı (bu sayfa + alternatifler). */
    val sourceCount: Int
        get() = 1 + alternatives.size

    /**
     * [index]. kaynağı (0 = bu sayfa, sonrası [alternatives]) etkin yapar. Seçilen kaynağın
     * alternatifleri, diğer kaynakları içerecek şekilde yeniden kurulur. Geçersiz indekste null.
     */
    fun withSourceAt(index: Int): FandomCharacterPage? {
        if (index < 0 || index >= sourceCount) return null
        val all = listOf(copy(alternatives = emptyList())) +
            alternatives.map { it.copy(alternatives = emptyList()) }
        val target = all[index]
        return target.copy(alternatives = all.filterIndexed { i, _ -> i != index })
    }

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
/**
 * Medya wiki'sindeki herhangi bir makale için uygulama içi okuma modeli.
 * Arama sonucu ve evren rehberi çipinden açıldığında bilgi kutusu/içindekiler hemen,
 * içerik bölümleri ise kullanıcı açtıkça yüklenir.
 */
@Serializable
data class FandomArticlePage(
    val wikiId: String,
    val pageTitle: String,
    val siteName: String? = null,
    val description: String? = null,
    val infobox: FandomInfobox? = null,
    val sections: List<FandomSectionRef> = emptyList(),
    /** İçindekiler isteği başarısızsa yeniden deneme eylemi gösterilir. */
    val sectionsFailed: Boolean = false
) {
    val pageUrl: String
        get() = FandomUrls.pageUrl(wikiId, pageTitle)
}

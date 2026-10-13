package com.kitsugi.animelist.data.fandom

import java.util.Locale

/**
 * Yapım detayından türetilen Fandom sorgusu. Bütün sağlayıcılardan gelen sabit kimlikleri
 * taşır; kimlik bulunmuyorsa başlıklar (Latin veya yerel alfabe) güvenli ad yedeği olur.
 */
data class FandomMediaQuery(
    val ids: FandomSourceIds,
    val isManga: Boolean,
    val isMovie: Boolean = false,
    /** Kaynağın verdiği başlıklar: İngilizce, romaji ve varsa yerel yazım. */
    val fallbackTitles: List<String> = emptyList(),
    /** Film ve TV başlıkları ayrı önbelleklenir; aynı ada sahip farklı yapımlar karışmaz. */
    val isTvShow: Boolean = false,
    val releaseYear: Int? = null
) {
    /**
     * Kararlı yapım kimliği: kullanıcı onay/ret tercihleri için kullanılır ve zenginleştirme
     * sırasında değişmez.
     */
    fun cacheKey(): String? = ids.stableKey(isManga, isMovie) ?: titleKey()

    /**
     * Çözümleme önbelleği kimliği ayrıca hesaba katar: detay yüklenince aynı yapım için
     * gelen ek Wikidata/IMDb ID'leri, yerel başlıklar veya yıl eski bir negatif sonucu
     * sonsuza yakın süreyle maskelemesin.
     */
    fun resolutionKey(): String? = cacheKey()?.let { "$it:q${Integer.toHexString(hashCode())}" }

    private fun titleKey(): String? {
        val title = fallbackTitles.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = FandomNameVariants.normalizeForCompare(title)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
            .trim('_')
            .take(100)
        if (normalized.isEmpty()) return null
        val kind = when {
            isManga -> "m"
            isMovie -> "movie"
            isTvShow -> "tv"
            else -> "a"
        }
        return "title:$kind:$normalized" + (releaseYear?.let { ":$it" } ?: "")
    }
}

/**
 * Yapım detay sayfalarından (liste kaydı / arama sonucu) [FandomMediaQuery] üretimi (saf fonksiyon).
 *
 * Kimlikler [FandomMediaIds.fromStableId] sözleşmesiyle çözülür; gerçek provider ID'leri
 * boş alanları tamamlar. Başlık yedeği Latin adlarla sınırlı değildir, böylece eklenti manga
 * ve yerel dilde açılmış kayıtlar da en azından Wiki alanını ve arama yolunu alır.
 */
object FandomMediaQueries {

    private const val MAX_TITLES = 12

    fun fromMedia(
        source: String?,
        mediaId: Int?,
        malId: Int? = null,
        aniListId: Int? = null,
        tmdbId: Int? = null,
        isManga: Boolean,
        isMovie: Boolean,
        titles: List<String>,
        simklId: Int? = null,
        isTvShow: Boolean = false,
        releaseYear: Int? = null,
        imdbId: String? = null,
        wikidataId: String? = null
    ): FandomMediaQuery? {
        val base = FandomMediaIds.fromStableId(source, mediaId)
        val cleanImdbId = (imdbId ?: base.imdbId)
            ?.trim()
            ?.takeIf { it.matches(Regex("(?i)^tt[0-9]{5,12}$")) }
            ?.lowercase(Locale.ROOT)
        val cleanWikidataId = (wikidataId ?: base.wikidataId)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { WikidataParsers.isQid(it) }
        val ids = FandomSourceIds(
            aniListId = base.aniListId ?: aniListId?.takeIf { it > 0 },
            malId = base.malId ?: malId?.takeIf { it in 1..99_999_999 },
            kitsuId = base.kitsuId,
            shikimoriId = base.shikimoriId,
            bangumiId = base.bangumiId,
            tmdbId = base.tmdbId ?: tmdbId?.takeIf { it > 0 },
            wikidataId = cleanWikidataId,
            simklId = base.simklId ?: simklId?.takeIf { it > 0 },
            imdbId = cleanImdbId
        )
        val cleanTitles = titles
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && it.length <= 200 }
            .distinctBy(FandomNameVariants::normalizeForCompare)
            .take(MAX_TITLES)
        if (ids.isEmpty && cleanTitles.isEmpty()) return null
        return FandomMediaQuery(
            ids = ids,
            isManga = isManga,
            isMovie = isMovie,
            fallbackTitles = cleanTitles,
            isTvShow = isTvShow,
            releaseYear = releaseYear?.takeIf { it in 1800..2200 }
        )
    }
}

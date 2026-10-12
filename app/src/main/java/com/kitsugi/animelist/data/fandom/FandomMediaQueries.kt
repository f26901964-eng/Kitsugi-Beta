package com.kitsugi.animelist.data.fandom

/**
 * Yapım detay sayfalarından (liste kaydı / arama sonucu) [FandomMediaQuery] üretimi (saf fonksiyon).
 *
 * Kimlikler [FandomMediaIds.fromStableId] sözleşmesiyle çözülür (kaynak + offset'li id);
 * sayfanın sonradan öğrendiği gerçek MAL/TMDB kimlikleri boş alanları doldurur.
 * Hiç kimlik yoksa bile başlıklar isim yolunu (son çare) besler.
 */
object FandomMediaQueries {

    private const val MAX_TITLES = 4

    fun fromMedia(
        source: String?,
        mediaId: Int?,
        malId: Int? = null,
        aniListId: Int? = null,
        tmdbId: Int? = null,
        isManga: Boolean,
        isMovie: Boolean,
        titles: List<String>
    ): FandomMediaQuery? {
        val base = FandomMediaIds.fromStableId(source, mediaId)
        val ids = FandomSourceIds(
            aniListId = base.aniListId ?: aniListId?.takeIf { it > 0 },
            malId = base.malId ?: malId?.takeIf { it > 0 },
            kitsuId = base.kitsuId,
            shikimoriId = base.shikimoriId,
            bangumiId = base.bangumiId,
            tmdbId = base.tmdbId ?: tmdbId?.takeIf { it > 0 },
            wikidataId = base.wikidataId
        )
        val cleanTitles = titles
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
            .distinct()
            .take(MAX_TITLES)
        if (ids.isEmpty && cleanTitles.isEmpty()) return null
        return FandomMediaQuery(
            ids = ids,
            isManga = isManga,
            isMovie = isMovie,
            fallbackTitles = cleanTitles
        )
    }
}

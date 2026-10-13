package com.kitsugi.animelist.data.fandom

/** Bir yapımın kaynak kimlikleri (offset'siz, gerçek değerler). */
data class FandomSourceIds(
    val aniListId: Int? = null,
    val malId: Int? = null,
    val kitsuId: Int? = null,
    val shikimoriId: Int? = null,
    val bangumiId: Int? = null,
    val tmdbId: Int? = null,
    val wikidataId: String? = null,
    val simklId: Int? = null,
    /** IMDb title ID (P345); especially useful for Simkl/TMDB entries missing catalog IDs. */
    val imdbId: String? = null
) {
    val isEmpty: Boolean
        get() = aniListId == null && malId == null && kitsuId == null && shikimoriId == null &&
            bangumiId == null && tmdbId == null && wikidataId == null && simklId == null && imdbId == null

    /** Önbellek anahtarı: en kararlı kimlikten başlayarak. */
    fun stableKey(isManga: Boolean, isMovie: Boolean): String? {
        val kind = if (isManga) "m" else "a"
        return when {
            aniListId != null -> "al:$aniListId"
            malId != null -> "mal:$kind:$malId"
            kitsuId != null -> "kitsu:$kind:$kitsuId"
            shikimoriId != null -> "shiki:$kind:$shikimoriId"
            bangumiId != null -> "bgm:$bangumiId"
            simklId != null -> "simkl:$simklId"
            tmdbId != null -> "tmdb:${if (isMovie) "movie" else "tv"}:$tmdbId"
            imdbId != null -> "imdb:${imdbId.lowercase(java.util.Locale.ROOT)}"
            wikidataId != null -> "wd:$wikidataId"
            else -> null
        }
    }
}

/**
 * Kitsugi'nin kimlik alanı sözleşmesini (bkz. `BangumiIdNamespace`) Fandom aramaları için
 * gerçek kimliklere çevirir ve Wikidata özelliklerini üretir (saf fonksiyonlar).
 *
 *  - MAL       → gerçek MAL ID        (1..99M)
 *  - AniList   → `id + 100_000_000`   (AniList kayıtları MAL eşleşmesi varsa gerçek MAL ID saklar)
 *  - Kitsu     → `id + 300_000_000`
 *  - Shikimori → `id + 400_000_000`
 *  - Bangumi   → `id + 500_000_000`
 */
object FandomMediaIds {

    private const val ANILIST_OFFSET = 100_000_000
    private const val ANILIST_MAX = 149_999_999
    private const val KITSU_OFFSET = 300_000_000
    private const val SHIKIMORI_OFFSET = 400_000_000
    private const val BANGUMI_OFFSET = 500_000_000
    /** Simkl kararlı kimlik ofseti (`CrossProviderIds.SIMKL_OFFSET` ile aynı sözleşme). */
    private const val SIMKL_OFFSET = 150_000_000
    private const val WINDOW = 100_000_000

    private val MAL_SOURCES = setOf("mal", "jikan", "myanimelist", "jikan (mal)", "mal (jikan)")

    private fun canonicalSource(source: String?): String = when (source?.trim()?.lowercase().orEmpty()) {
        "al" -> "anilist"
        "shiki" -> "shikimori"
        "bgm", "bgm.tv" -> "bangumi"
        "themoviedb" -> "tmdb"
        else -> source?.trim()?.lowercase().orEmpty()
    }

    fun isMalSource(source: String?): Boolean = canonicalSource(source) in MAL_SOURCES

    fun fromStableId(source: String?, id: Int?): FandomSourceIds {
        if (id == null || id <= 0) return FandomSourceIds()
        val key = canonicalSource(source)
        return when {
            key in MAL_SOURCES -> if (id < ANILIST_OFFSET) FandomSourceIds(malId = id) else FandomSourceIds()
            key == "anilist" -> when {
                id < ANILIST_OFFSET -> FandomSourceIds(malId = id)
                id <= ANILIST_MAX -> FandomSourceIds(aniListId = id - ANILIST_OFFSET)
                else -> FandomSourceIds()
            }
            key == "kitsu" -> FandomSourceIds(kitsuId = unwrap(id, KITSU_OFFSET))
            key == "shikimori" -> FandomSourceIds(shikimoriId = unwrap(id, SHIKIMORI_OFFSET))
            key == "bangumi" || key == "bgm" -> FandomSourceIds(bangumiId = unwrap(id, BANGUMI_OFFSET))
            key == "tmdb" -> FandomSourceIds(tmdbId = id)
            key == "simkl" -> FandomSourceIds(simklId = unwrap(id, SIMKL_OFFSET))
            else -> FandomSourceIds()
        }
    }

    /** Offset'li değeri çözer; offset'siz gerçek kimliği aynen bırakır. */
    private fun unwrap(id: Int, offset: Int): Int? = when {
        id in (offset + 1) until (offset + WINDOW) -> id - offset
        id < ANILIST_OFFSET -> id
        else -> null
    }

    private val MANGA_HINTS = listOf(
        "manga", "manhwa", "manhua", "novel", "roman", "one-shot", "one_shot", "oneshot",
        "tek bölüm", "doujin", "comic", "webtoon"
    )

    /** Yerelleştirilmiş tür etiketi ("Manga", "Hafif Roman", "Tek Bölüm"…) manga mı? */
    fun isMangaType(label: String?): Boolean {
        val text = label?.lowercase() ?: return false
        return MANGA_HINTS.any { text.contains(it) }
    }

    fun isMovieType(label: String?): Boolean {
        val text = label?.lowercase() ?: return false
        return text.contains("movie") || text.contains("film")
    }

    /** Yerelleştirilmiş rol ("Ana Karakter", "Main", "MAIN", "主角") ana karakter mi? */
    fun isMainRole(label: String?): Boolean {
        val text = label?.lowercase() ?: return false
        return text.contains("main") || text.contains("ana karakter") || text.contains("主角")
    }

    /** Wikidata kimlik araması için (özellik, değer) çiftleri — öncelik sırasıyla. */
    fun wikidataLookups(ids: FandomSourceIds, isManga: Boolean, isMovie: Boolean): List<Pair<String, String>> =
        buildList {
            if (isManga) {
                ids.aniListId?.let { add("P8731" to it.toString()) }
                ids.malId?.let { add("P4087" to it.toString()) }
                ids.kitsuId?.let { add("P11494" to it.toString()) }
                ids.bangumiId?.let { add("P5732" to it.toString()) }
            } else {
                ids.aniListId?.let { add("P8729" to it.toString()) }
                ids.malId?.let { add("P4086" to it.toString()) }
                ids.kitsuId?.let { add("P11495" to it.toString()) }
                ids.bangumiId?.let { add("P5732" to it.toString()) }
                ids.tmdbId?.let { add((if (isMovie) "P4947" else "P4983") to it.toString()) }
            }
            ids.imdbId
                ?.trim()
                ?.takeIf { it.matches(Regex("(?i)^tt[0-9]{5,12}$")) }
                ?.let { add("P345" to it.lowercase(java.util.Locale.ROOT)) }
        }
}

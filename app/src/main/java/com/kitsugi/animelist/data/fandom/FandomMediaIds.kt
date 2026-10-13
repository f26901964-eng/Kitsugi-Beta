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

    private val MOVIE_HINT = Regex(
        "(?i)(^|[^\\p{L}\\p{N}])(?:movies?|films?|feature\\s+film|theatrical\\s+film|cinema|sinema\\s+filmi|uzun\\s+metraj)(?:$|[^\\p{L}\\p{N}])"
    )

    /** Explicit movie format or title marker, including common Japanese/Korean/Chinese labels. */
    fun isMovieType(label: String?): Boolean {
        val text = label?.trim()?.lowercase(java.util.Locale.ROOT) ?: return false
        if (text.isBlank()) return false
        return MOVIE_HINT.containsMatchIn(text) || listOf("映画", "劇場版", "剧场版", "電影", "电影", "극장판")
            .any { hint -> text.contains(hint) }
    }

    /**
     * Outbound URLs used as exact identity proofs on Fandom pages. These are not guessed titles:
     * every URL is constructed from a validated provider ID already attached to this media item.
     */
    fun externalLinkLookups(
        ids: FandomSourceIds,
        isManga: Boolean,
        isMovie: Boolean,
        isTvShow: Boolean = false
    ): List<String> = buildList {
        val animeOrManga = if (isManga) "manga" else "anime"
        val simklKind = when {
            isManga -> "manga"
            isMovie -> "movies"
            isTvShow -> "tv"
            else -> "anime"
        }
        ids.wikidataId?.takeIf(WikidataParsers::isQid)?.let { add("wikidata.org/wiki/$it") }
        ids.imdbId?.trim()?.takeIf { it.matches(Regex("(?i)^tt[0-9]{5,12}$")) }
            ?.let { add("imdb.com/title/${it.lowercase(java.util.Locale.ROOT)}") }
        ids.aniListId?.takeIf { it > 0 }?.let { add("anilist.co/$animeOrManga/$it") }
        ids.malId?.takeIf { it in 1..99_999_999 }?.let { add("myanimelist.net/$animeOrManga/$it") }
        ids.tmdbId?.takeIf { it > 0 }?.let {
            add("themoviedb.org/${if (isMovie) "movie" else "tv"}/$it")
        }
        ids.kitsuId?.takeIf { it > 0 }?.let {
            add("kitsu.app/$animeOrManga/$it")
            add("kitsu.io/$animeOrManga/$it")
        }
        ids.shikimoriId?.takeIf { it > 0 }?.let { add("shikimori.one/${if (isManga) "mangas" else "animes"}/$it") }
        ids.bangumiId?.takeIf { it > 0 }?.let { add("bgm.tv/subject/$it") }
        ids.simklId?.takeIf { it > 0 }?.let { add("simkl.com/$simklKind/$it") }
    }.distinct()

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

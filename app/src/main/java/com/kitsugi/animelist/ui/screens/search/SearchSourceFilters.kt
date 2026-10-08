package com.kitsugi.animelist.ui.screens.search

/**
 * 7 Ana Katalog Arama Motoru
 * Arama çubuğunun sağ iç tarafındaki Brave Tarzı Seçici bu seçenekleri listeler.
 */
enum class SearchSourceEngine(val id: String, val label: String, val emoji: String) {
    ALL("all", "Tümü", "🌐"),
    ANILIST("anilist", "AniList", "🅰️"),
    MAL("mal", "MyAnimeList", "Ⓜ️"),
    TMDB("tmdb", "TMDB", "🎬"),
    SHIKIMORI("shikimori", "Shikimori", "🌸"),
    KITSU("kitsu", "Kitsu", "🦊"),
    SIMKL("simkl", "Simkl", "📺"),
    BANGUMI("bangumi", "Bangumi", "🎌");

    val displayLabel: String get() = "$emoji $label"
    val shortLabel: String get() = when (this) {
        ALL -> "🌐 Tümü"
        ANILIST -> "🅰️ AL"
        MAL -> "Ⓜ️ MAL"
        TMDB -> "🎬 TMDB"
        SHIKIMORI -> "🌸 SHI"
        KITSU -> "🦊 KTS"
        SIMKL -> "📺 SMK"
        BANGUMI -> "🎌 BGM"
    }
}

/**
 * Her kaynağın altında çıkan alt kapsam sekmeleri (Anime, Manga, Dizi, Karakter vb.)
 */
enum class SearchScope(val id: String, val label: String, val emoji: String = "") {
    ALL_MIXED("all_mixed", "Tümü", "🌟"),
    ANIME("anime", "Anime", "⚡"),
    MANGA("manga", "Manga", "📖"),
    MANHWA("manhwa", "Manhwa", "🇰🇷"),
    MANHUA("manhua", "Manhua", "🇨🇳"),
    LIGHT_NOVEL("light_novel", "Light Novel", "📚"),
    TV("tv", "Diziler", "📺"),
    MOVIE("movie", "Filmler", "🎬"),
    K_DRAMA("k_drama", "K-Drama", "🫰"),
    CHARACTER("character", "Karakterler", "👤"),
    STAFF("staff", "Seslendirmen & Ekip", "🎙️"),
    STUDIO("studio", "Stüdyolar", "🏢");

    val displayLabel: String get() = if (emoji.isNotBlank()) "$emoji $label" else label
}

/**
 * Bir kaynak motoru seçildiğinde arama çubuğunun altında listelenecek alt kapsamlar.
 */
fun SearchSourceEngine.availableScopes(): List<SearchScope> = when (this) {
    SearchSourceEngine.ALL -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA,
        SearchScope.TV, SearchScope.MOVIE, SearchScope.CHARACTER, SearchScope.STAFF
    )
    SearchSourceEngine.ANILIST -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF, SearchScope.STUDIO
    )
    SearchSourceEngine.MAL -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF, SearchScope.STUDIO
    )
    SearchSourceEngine.TMDB -> listOf(
        SearchScope.ALL_MIXED, SearchScope.TV, SearchScope.MOVIE,
        SearchScope.ANIME, SearchScope.K_DRAMA, SearchScope.STAFF, SearchScope.STUDIO
    )
    SearchSourceEngine.SHIKIMORI -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF
    )
    SearchSourceEngine.KITSU -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER
    )
    SearchSourceEngine.SIMKL -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.TV, SearchScope.MOVIE
    )
    SearchSourceEngine.BANGUMI -> listOf(
        SearchScope.ALL_MIXED, SearchScope.ANIME, SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA,
        SearchScope.LIGHT_NOVEL, SearchScope.CHARACTER, SearchScope.STAFF
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// KAYNAĞA ÖZEL FİLTRE MODELLERİ
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 🅰️ AniList Özel Filtreleri
 */
data class AniListSpecificFilters(
    val formats: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val country: String? = null, // JP, KR, CN, TW
    val sources: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val minimumTagRank: Int? = null, // 0..100
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val minEpCh: Int? = null,
    val maxEpCh: Int? = null,
    val minDuration: Int? = null,
    val maxDuration: Int? = null,
    val minVolumes: Int? = null,
    val maxVolumes: Int? = null,
    val licensedBy: List<String> = emptyList(),
    val onMyList: Boolean? = null,
    val isDoujin: Boolean? = null,
    val isAdult: Boolean? = null,
    val isBirthday: Boolean? = null, // Karakter/Staff için doğum günü
    val sort: String = "POPULARITY_DESC"
) {
    val activeCount: Int get() {
        var count = 0
        if (formats.isNotEmpty()) count += formats.size
        if (statuses.isNotEmpty()) count += statuses.size
        if (season != null) count++
        if (seasonYear != null) count++
        if (startYear != null || endYear != null) count++
        if (country != null) count++
        if (sources.isNotEmpty()) count += sources.size
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (tags.isNotEmpty()) count += tags.size
        if (minimumTagRank != null) count++
        if (minScore != null || maxScore != null) count++
        if (minEpCh != null || maxEpCh != null) count++
        if (minDuration != null || maxDuration != null) count++
        if (minVolumes != null || maxVolumes != null) count++
        if (licensedBy.isNotEmpty()) count += licensedBy.size
        if (onMyList != null) count++
        if (isDoujin != null) count++
        if (isAdult != null) count++
        if (isBirthday != null) count++
        return count
    }
}

/**
 * Ⓜ️ MyAnimeList (Jikan v4) Özel Filtreleri
 */
data class MalSpecificFilters(
    val orderBy: String = "popularity",
    val sortDirection: String = "desc",
    val type: String? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val rating: String? = null,
    val genres: List<Int> = emptyList(),
    val excludedGenres: List<Int> = emptyList(),
    val minScore: Double? = null,
    val maxScore: Double? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val producerId: Int? = null,
    val magazineId: Int? = null,
    val letter: String? = null,
    val sfw: Boolean = true
) {
    val activeCount: Int get() {
        var count = 0
        if (type != null) count++
        if (status != null) count++
        if (season != null) count++
        if (seasonYear != null) count++
        if (rating != null) count++
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (minScore != null || maxScore != null) count++
        if (startYear != null || endYear != null) count++
        if (producerId != null) count++
        if (magazineId != null) count++
        if (letter != null) count++
        if (!sfw) count++
        return count
    }
}

/**
 * 🎬 TMDB v3 Özel Filtreleri
 */
data class TmdbSpecificFilters(
    val isMovie: Boolean = true,
    val sortBy: String = "popularity.desc",
    val genres: List<Int> = emptyList(),
    val excludedGenres: List<Int> = emptyList(),
    val originCountry: String? = null,
    val tvStatus: String? = null,
    val tvType: String? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val minScore: Double? = null,
    val maxScore: Double? = null,
    val minVoteCount: Int? = null,
    val minRuntime: Int? = null,
    val maxRuntime: Int? = null,
    val watchProviderId: Int? = null,
    val networkId: Int? = null,
    val keyword: String? = null,
    val includeAdult: Boolean = false
) {
    val activeCount: Int get() {
        var count = 0
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (originCountry != null) count++
        if (tvStatus != null) count++
        if (tvType != null) count++
        if (startYear != null || endYear != null) count++
        if (minScore != null || maxScore != null) count++
        if (minVoteCount != null) count++
        if (minRuntime != null || maxRuntime != null) count++
        if (watchProviderId != null) count++
        if (networkId != null) count++
        if (keyword != null) count++
        if (includeAdult) count++
        return count
    }
}

/**
 * 🌸 Shikimori Özel Filtreleri
 */
data class ShikimoriSpecificFilters(
    val order: String = "popularity",
    val kinds: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val season: String? = null, // winter_2026, 2025, 202x, 199x
    val minScore: Int? = null,
    val duration: String? = null, // S, D, F
    val rating: String? = null, // g, pg, pg_13, r, r_plus, rx
    val genres: List<Int> = emptyList(),
    val excludedGenres: List<Int> = emptyList(),
    val studioId: Int? = null,
    val publisherId: Int? = null,
    val censored: Boolean = true,
    val peopleKind: String? = null // seyu, mangaka, producer
) {
    val activeCount: Int get() {
        var count = 0
        if (kinds.isNotEmpty()) count += kinds.size
        if (statuses.isNotEmpty()) count += statuses.size
        if (season != null) count++
        if (minScore != null) count++
        if (duration != null) count++
        if (rating != null) count++
        if (genres.isNotEmpty()) count += genres.size
        if (excludedGenres.isNotEmpty()) count += excludedGenres.size
        if (studioId != null) count++
        if (publisherId != null) count++
        if (!censored) count++
        if (peopleKind != null) count++
        return count
    }
}

/**
 * 🦊 Kitsu Özel Filtreleri
 */
data class KitsuSpecificFilters(
    val sort: String = "trending",
    val subtypes: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val categories: List<String> = emptyList(),
    val ageRating: String? = null,
    val streamers: List<String> = emptyList(),
    val minRating: Int? = null // 5..100
) {
    val activeCount: Int get() {
        var count = 0
        if (subtypes.isNotEmpty()) count += subtypes.size
        if (statuses.isNotEmpty()) count += statuses.size
        if (season != null) count++
        if (seasonYear != null) count++
        if (categories.isNotEmpty()) count += categories.size
        if (ageRating != null) count++
        if (streamers.isNotEmpty()) count += streamers.size
        if (minRating != null) count++
        return count
    }
}

/**
 * 📺 Simkl Özel Filtreleri
 */
data class SimklSpecificFilters(
    val trendingPeriod: String = "today", // today, week, month, calendar
    val sort: String = "rank", // rank, popular-today, popular-this-week, votes, release-date, a-z
    val subtype: String? = null, // tv, movies, ovas, onas, specials
    val genre: String? = null, // action, comedy, drama, fantasy...
    val country: String? = null, // jp, kr, cn, us
    val year: String? = null, // 2026, 2025, 2020s, 1990s
    val minScore: Int? = null
) {
    val activeCount: Int get() {
        var count = 0
        if (subtype != null) count++
        if (genre != null) count++
        if (country != null) count++
        if (year != null) count++
        if (minScore != null) count++
        return count
    }
}

/**
 * 🎌 Bangumi (bgm.tv) Özel Filtreleri
 * `POST /v0/search/subjects` gövdesindeki `filter` alanıyla birebir hizalıdır.
 */
data class BangumiSpecificFilters(
    /** `match` | `heat` | `rank` | `score` */
    val sort: String = "match",
    val tags: List<String> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minScore: Int? = null,
    val nsfw: Boolean = false,
    val comicsOnly: Boolean = true,
    val career: List<String> = emptyList()
) {
    val activeCount: Int get() {
        var count = 0
        if (sort != "match") count++
        if (tags.isNotEmpty()) count += tags.size
        if (yearFrom != null || yearTo != null) count++
        if (minScore != null) count++
        if (nsfw) count++
        if (!comicsOnly) count++
        if (career.isNotEmpty()) count += career.size
        return count
    }
}

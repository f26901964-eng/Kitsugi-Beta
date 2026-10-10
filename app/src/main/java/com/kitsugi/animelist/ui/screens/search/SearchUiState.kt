package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiCountryOfOrigin
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaFormat
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSeason
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSortSearch
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSource
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaStatus

enum class KitsugiSearchTab(val label: String) {
    All("🌟 Tümü"),
    Anime("⚡ AniList"),
    Manga("📖 Manga"),
    MAL("⭐ MyAnimeList"),
    Shikimori("🌸 Shikimori 🇷🇺"),
    TMDB("🍿 Film & Dizi"),
    Kitsu("🦊 Kitsu"),
    Simkl("📺 Simkl"),
    Bangumi("🎌 Bangumi 🇨🇳"),
    Character("👤 Karakter"),
    Staff("🎙️ Personel")
}

/**
 * Seçenek C: All-in-One Çoklu platform eş zamanlı arama sonuçları.
 */
data class MultiPlatformResults(
    val aniListResults: List<JikanSearchResult> = emptyList(),
    val malResults: List<JikanSearchResult> = emptyList(),
    val tmdbResults: List<JikanSearchResult> = emptyList(),
    val shikimoriResults: List<JikanSearchResult> = emptyList(),
    val kitsuResults: List<JikanSearchResult> = emptyList(),
    val simklResults: List<JikanSearchResult> = emptyList(),
    val bangumiResults: List<JikanSearchResult> = emptyList(),
    val isLoadingAniList: Boolean = false,
    val isLoadingMal: Boolean = false,
    val isLoadingTmdb: Boolean = false,
    val isLoadingShikimori: Boolean = false,
    val isLoadingKitsu: Boolean = false,
    val isLoadingSimkl: Boolean = false,
    val isLoadingBangumi: Boolean = false,
) {
    val isEmpty: Boolean get() =
        aniListResults.isEmpty() &&
        malResults.isEmpty() &&
        tmdbResults.isEmpty() &&
        shikimoriResults.isEmpty() &&
        kitsuResults.isEmpty() &&
        simklResults.isEmpty() &&
        bangumiResults.isEmpty()

    val isAnyLoading: Boolean get() =
        isLoadingAniList || isLoadingMal || isLoadingTmdb ||
        isLoadingShikimori || isLoadingKitsu || isLoadingSimkl || isLoadingBangumi

    companion object {
        /**
         * Yalnızca gerçekten sorgulanacak kaynaklar "yükleniyor" işaretlenir.
         *
         * Arama başlarken tüm raflar boş + yüklenmiyor durumuna düşüyordu; gerçek
         * yükleme bayrakları ancak ağ isteği kurulduktan sonra yazıldığı için araya
         * tüm rafların kaybolduğu bir kare giriyordu (titreme). Ayrıca kapsamın
         * desteklemediği kaynaklar (örn. Manga kapsamında TMDB) hiç sorgulanmadığı
         * hâlde boşuna shimmer çiziyordu.
         */
        fun loading(
            aniList: Boolean,
            mal: Boolean,
            tmdb: Boolean,
            shikimori: Boolean,
            kitsu: Boolean,
            simkl: Boolean,
            bangumi: Boolean
        ): MultiPlatformResults = MultiPlatformResults(
            isLoadingAniList = aniList,
            isLoadingMal = mal,
            isLoadingTmdb = tmdb,
            isLoadingShikimori = shikimori,
            isLoadingKitsu = kitsu,
            isLoadingSimkl = simkl,
            isLoadingBangumi = bangumi
        )
    }
}

/**
 * Search ekranının UI durumu.
 * AniHyou SearchUiState.kt mimarisine ve Seçenek C Çoklu Platform motoruna tam uyarlanmıştır.
 */
data class SearchUiState(
    val query: String = "",
    val currentTab: KitsugiSearchTab = KitsugiSearchTab.All,
    val selectedMediaType: MediaType = MediaType.Anime,
    val selectedPlatform: SearchPlatform = SearchPlatform.All,
    val results: List<JikanSearchResult> = emptyList(),
    val multiResults: MultiPlatformResults = MultiPlatformResults(),
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasNextPage: Boolean = true,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    val isFilterSheetOpen: Boolean = false,
    val showMoreFilters: Boolean = true,

    // ── Brave-style Engine & Scope ─────────────────────────────────────────
    val selectedEngine: SearchSourceEngine = SearchSourceEngine.ALL,
    val selectedScope: SearchScope = SearchScope.ALL_MIXED,

    // ── Source-specific filter bundles ─────────────────────────────────────
    val aniListSpecificFilters: AniListSpecificFilters = AniListSpecificFilters(),
    val malSpecificFilters: MalSpecificFilters = MalSpecificFilters(),
    val tmdbSpecificFilters: TmdbSpecificFilters = TmdbSpecificFilters(),
    val shikimoriSpecificFilters: ShikimoriSpecificFilters = ShikimoriSpecificFilters(),
    val kitsuSpecificFilters: KitsuSpecificFilters = KitsuSpecificFilters(),
    val simklSpecificFilters: SimklSpecificFilters = SimklSpecificFilters(),
    val bangumiSpecificFilters: BangumiSpecificFilters = BangumiSpecificFilters(),
    /** Last typed detail facet; used to avoid querying providers that cannot apply it. */
    val detailSearchFilterRequest: DetailSearchFilterRequest? = null,

    // ── Plugin Explore Mode ────────────────────────────────────────────────
    /** When non-null, the search screen shows this plugin's explore page instead of normal search */
    val selectedPluginApiName: String? = null,

    // ── Genre / Tag filters (used by GenresTagsSheet) ──────────────────────
    val genres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),

    // ── AniHyou-style chip filters (AniList-only) ──────────────────────────
    val selectedFormats: List<KitsugiMediaFormat> = emptyList(),
    val selectedStatuses: List<KitsugiMediaStatus> = emptyList(),
    val country: KitsugiCountryOfOrigin? = null,
    val selectedSources: List<KitsugiMediaSource> = emptyList(),

    // ── Date / Season ──────────────────────────────────────────────────────
    val startYear: Int? = null,
    val endYear: Int? = null,
    val season: KitsugiMediaSeason? = null,

    // ── Score / Episode / Duration ranges ──────────────────────────────────
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val minEpCh: Int? = null,
    val maxEpCh: Int? = null,
    val minDuration: Int? = null,
    val maxDuration: Int? = null,

    // ── AniHyou Tri-Filter states ──────────────────────────────────────────
    val onMyList: Boolean? = null,
    val isDoujin: Boolean? = null,
    val isAdultFilter: Boolean? = null,

    // ── Sort ───────────────────────────────────────────────────────────────
    val sortSearch: KitsugiMediaSortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
    val isSortDescending: Boolean = true,
) {
    /** Effective AniList sort string (e.g. "POPULARITY_DESC") */
    val effectiveSortApiValue: String get() =
        if (isSortDescending) sortSearch.descApiValue else sortSearch.ascApiValue

    /** True if any filter besides the default sort is active */
    val hasFiltersApplied: Boolean get() = activeFilterCount > 0

    val activeFilterCount: Int get() {
        return when (selectedEngine) {
            SearchSourceEngine.ALL -> {
                var c = 0
                if (genres.isNotEmpty()) c += genres.size
                if (tags.isNotEmpty()) c += tags.size
                if (startYear != null || endYear != null) c++
                if (season != null) c++
                if (isAdultFilter != null) c++
                c
            }
            SearchSourceEngine.ANILIST -> {
                var count = 0
                if (genres.isNotEmpty()) count += genres.size
                if (excludedGenres.isNotEmpty()) count += excludedGenres.size
                if (tags.isNotEmpty()) count += tags.size
                if (selectedFormats.isNotEmpty()) count += selectedFormats.size
                if (selectedStatuses.isNotEmpty()) count += selectedStatuses.size
                if (country != null) count++
                if (selectedSources.isNotEmpty()) count += selectedSources.size
                if (startYear != null) count++
                if (endYear != null) count++
                if (season != null) count++
                if (minScore != null || maxScore != null) count++
                if (minEpCh != null || maxEpCh != null) count++
                if (minDuration != null || maxDuration != null) count++
                if (onMyList != null) count++
                if (isDoujin != null) count++
                if (isAdultFilter != null) count++
                count + aniListSpecificFilters.activeCount
            }
            SearchSourceEngine.MAL -> malSpecificFilters.activeCount
            SearchSourceEngine.TMDB -> tmdbSpecificFilters.activeCount
            SearchSourceEngine.SHIKIMORI -> shikimoriSpecificFilters.activeCount
            SearchSourceEngine.KITSU -> kitsuSpecificFilters.activeCount
            SearchSourceEngine.SIMKL -> simklSpecificFilters.activeCount
            SearchSourceEngine.BANGUMI -> bangumiSpecificFilters.activeCount
        }
    }

    /** Returns the legacy SearchFilters object for backward-compatible ViewModel code. */
    fun toLegacyFilters(): SearchFilters = SearchFilters(
        format = selectedFormats.firstOrNull()?.apiValue,
        status = selectedStatuses.firstOrNull()?.apiValue,
        genres = genres,
        excludedGenres = excludedGenres,
        tags = tags,
        minYear = startYear,
        maxYear = endYear,
        season = season?.apiValue,
        minScore = minScore,
        maxScore = maxScore,
        sort = effectiveSortApiValue
    )
}

data class SearchHistoryItem(
    val query: String,
    val platform: SearchPlatform,
    val mediaType: MediaType
)

enum class SearchPlatform(val label: String) {
    All("Tümü"),
    AniList("AniList"),
    MAL("MyAnimeList"),
    TMDB("TMDB"),
    Kitsu("Kitsu"),
    Shikimori("Shikimori"),
    Simkl("Simkl"),
    Bangumi("Bangumi"),
    CS3("Eklentiler")
}

/**
 * Legacy filter bag kept for backward compatibility with JikanApiClient
 * and the existing executeSearchForQuery logic.
 */
data class SearchFilters(
    val format: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val excludedGenres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val season: String? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val sort: String? = "POPULARITY_DESC"
) {
    fun isDefault(): Boolean =
        format == null &&
        status == null &&
        genres.isEmpty() &&
        excludedGenres.isEmpty() &&
        tags.isEmpty() &&
        minYear == null &&
        maxYear == null &&
        season == null &&
        minScore == null &&
        maxScore == null &&
        sort == "POPULARITY_DESC"
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.screens.fullscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.kitsugi.animelist.ui.components.KITSUGI_MEDIA_GENRES
import com.kitsugi.animelist.ui.components.KitsugiGridSortOption
import com.kitsugi.animelist.ui.components.KitsugiMediaFilterBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiRankingMediaCard
import com.kitsugi.animelist.ui.components.DetailedSeasonalMediaCard
import com.kitsugi.animelist.ui.components.KitsugiSeasonalFilterBottomSheet
import com.kitsugi.animelist.ui.screens.explore.ExploreCategoryType
import com.kitsugi.animelist.ui.screens.explore.exploreIdentity
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import kotlinx.coroutines.launch
import java.util.Calendar

/** Simkl uses 20-item API pages; its static trending chart files are sliced locally. */
private const val SIMKL_EXPLORE_PAGE_SIZE = 20
private const val SIMKL_MAX_API_PAGE = 20

@Composable
fun FullScreenMediaGridPage(
    title: String,
    categoryType: ExploreCategoryType,
    platform: ExplorePlatform,
    initialResults: List<JikanSearchResult>,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    onBackClick: () -> Unit,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAdultContent: Boolean = false,
    blurAdultMedia: Boolean = false,
    getMediaEntry: (JikanSearchResult) -> com.kitsugi.animelist.model.MediaEntry? = { null }
) {
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { JikanApiClient() }
    val tmdbApiClient = remember { TmdbApiClient() }
    val simklApiClient = remember { com.kitsugi.animelist.data.remote.SimklApiClient() }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columnCount = remember(isLandscape, screenWidthDp) {
        if (isLandscape) {
            when {
                screenWidthDp >= 1200 -> 3
                screenWidthDp >= 800 -> 2
                else -> 2
            }
        } else {
            when {
                screenWidthDp >= 900 -> 4
                screenWidthDp >= 600 -> 3
                else -> 2
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val gridPrefs = remember(context) { context.getSharedPreferences("kitsugi_ui_prefs", android.content.Context.MODE_PRIVATE) }
    // Grid/Liste görünümü tercihi kalıcı olarak hatırlanır
    var isGridView by rememberSaveable { mutableStateOf(gridPrefs.getBoolean("fullscreen_media_grid_is_grid_view", false)) }
    LaunchedEffect(isGridView) {
        gridPrefs.edit().putBoolean("fullscreen_media_grid_is_grid_view", isGridView).apply()
    }

    val currentCalendar = remember { Calendar.getInstance() }
    val currentYear = remember { currentCalendar.get(Calendar.YEAR) }
    val currentSeasonStr = remember {
        val month = currentCalendar.get(Calendar.MONTH)
        when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "WINTER"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "SPRING"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
    }

    var seasonalYear by remember { mutableIntStateOf(currentYear) }
    var seasonalSeason by remember { mutableStateOf(currentSeasonStr) }
    var seasonalSort by remember { mutableStateOf("POPULARITY_DESC") }
    var showFilterBottomSheet by remember { mutableStateOf(false) }

    var selectedGenreId by rememberSaveable { mutableStateOf("ALL") }
    var selectedSortOption by rememberSaveable { mutableStateOf(KitsugiGridSortOption.DEFAULT) }
    var showGeneralFilterBottomSheet by remember { mutableStateOf(false) }

    val isSeasonalAnime = categoryType == ExploreCategoryType.SEASONAL_ANIME &&
        platform != ExplorePlatform.TMDB && platform != ExplorePlatform.SIMKL
    val dynamicTitle = remember(isSeasonalAnime, title, seasonalSeason, seasonalYear) {
        if (isSeasonalAnime) {
            val sn = when (seasonalSeason.uppercase()) {
                "WINTER" -> "Kış"; "SPRING" -> "İlkbahar"; "SUMMER" -> "Yaz"; else -> "Sonbahar"
            }
            "${platform.label} · $sn $seasonalYear"
        } else title
    }

    var loadedResults by remember(platform, categoryType) { mutableStateOf(initialResults) }
    var currentPage by remember(platform, categoryType) {
        mutableIntStateOf(if (initialResults.isEmpty()) 0 else 1)
    }
    var isLoadingMore by remember { mutableStateOf(false) }
    // Simkl mixes page-based genre/premiere APIs with finite trending charts and an unpaged
    // airing schedule. Its own watchlists are already fully loaded and must not be re-fetched.
    val isSimklPersonalList = platform == ExplorePlatform.SIMKL && listOf(
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_continue_watching_series),
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_continue_watching_movies),
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_plantowatch_series),
        context.getString(com.kitsugi.animelist.R.string.explore_simkl_plantowatch_movies)
    ).any { title.contains(it, ignoreCase = true) }
    val finiteChart = platform == ExplorePlatform.KITSU && categoryType == ExploreCategoryType.TRENDING_ANIME
    val simklCategoryUsesFiniteChart = platform == ExplorePlatform.SIMKL && categoryType in setOf(
        ExploreCategoryType.TOP_ANIME,
        ExploreCategoryType.MOVIE_ANIME,
        ExploreCategoryType.PUBLISHING_MANGA,
        ExploreCategoryType.TRENDING_ANIME
    )
    val simklCategoryHasPagination = !isSimklPersonalList && categoryType !in setOf(
        ExploreCategoryType.AIRING_ANIME,
        ExploreCategoryType.TRENDING_MANGA
    )
    var simklPageInitialized by remember(platform, categoryType, isSimklPersonalList) {
        mutableStateOf(platform != ExplorePlatform.SIMKL || isSimklPersonalList)
    }
    var simklTrendingResults by remember(platform, categoryType) { mutableStateOf<List<JikanSearchResult>?>(null) }
    var hasMorePages by remember(platform, categoryType, isSimklPersonalList) {
        mutableStateOf(!isSimklPersonalList && (platform == ExplorePlatform.SIMKL || !finiteChart || initialResults.isEmpty()))
    }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun fetchSeasonalPage(page: Int): List<JikanSearchResult> = when (platform) {
        ExplorePlatform.AniList -> apiClient.aniListSeasonalAnime(page, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
        ExplorePlatform.MAL -> apiClient.seasonalAnime(page, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
        ExplorePlatform.KITSU -> com.kitsugi.animelist.data.remote.KitsuExploreClient.searchMediaAdvanced(
            mediaType = com.kitsugi.animelist.model.MediaType.Anime, page = page, limit = 20,
            season = seasonalSeason.lowercase(), seasonYear = seasonalYear,
            sort = when (seasonalSort) { "SCORE_DESC" -> "-averageRating"; "START_DATE_DESC" -> "-startDate"; else -> "-userCount" }
        )
        ExplorePlatform.SHIKIMORI -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
            mediaType = com.kitsugi.animelist.model.MediaType.Anime, page = page, limit = 20,
            season = "${seasonalSeason.lowercase()}_$seasonalYear",
            order = when (seasonalSort) { "SCORE_DESC" -> "ranked"; "START_DATE_DESC" -> "aired_on"; else -> "popularity" },
            censored = !showAdultContent
        )
        ExplorePlatform.BANGUMI -> {
            // Bangumi'de sezon "yıl+ay" ile ifade edilir; mevsim adını temsil ayına çevir.
            val bangumiMonth = when (seasonalSeason.uppercase()) {
                "WINTER" -> 1
                "SPRING" -> 4
                "SUMMER" -> 7
                else -> 10
            }
            com.kitsugi.animelist.data.remote.KitsugiBangumiClient.seasonFor(
                year = seasonalYear, month = bangumiMonth, limit = 20,
                offset = (page - 1).coerceAtLeast(0) * 20
            )
        }
        else -> emptyList()
    }

    /**
     * Bangumi "Tümünü Gör" sayfalaması. `limit/offset` tabanlıdır; kategori → Bangumi
     * şeridi eşlemesi ExploreViewModel.loadBangumiData() ile birebir aynıdır ki ilk sayfa
     * ve devam sayfaları aynı sıralamayı kullansın.
     */
    suspend fun bangumiPage(page: Int): List<JikanSearchResult> {
        val bangumi = com.kitsugi.animelist.data.remote.KitsugiBangumiClient
        val limit = 20
        val offset = (page - 1).coerceAtLeast(0) * limit
        val adult = showAdultContent
        return when (categoryType) {
            ExploreCategoryType.TOP_ANIME -> bangumi.topAnime(limit, offset = offset)
            ExploreCategoryType.TOP_RATED_ANIME -> bangumi.topRatedAnime(limit, offset = offset)
            ExploreCategoryType.TRENDING_ANIME -> bangumi.trendingAnime(limit, offset = offset)
            ExploreCategoryType.AIRING_ANIME -> bangumi.airingAnime(limit)
            ExploreCategoryType.UPCOMING_ANIME -> bangumi.upcomingAnime(limit)
            ExploreCategoryType.MOVIE_ANIME -> bangumi.movieAnime(limit, offset = offset)
            ExploreCategoryType.SEASONAL_ANIME -> fetchSeasonalPage(page)
            ExploreCategoryType.NEWLY_ADDED_ANIME -> bangumi.newlyAddedAnime(limit, offset = offset)
            ExploreCategoryType.TOP_MANGA -> bangumi.topManga(limit, offset = offset)
            ExploreCategoryType.TOP_RATED_MANGA -> bangumi.topRatedManga(limit, offset = offset)
            ExploreCategoryType.PUBLISHING_MANGA -> bangumi.publishingManga(limit, offset = offset)
            ExploreCategoryType.TRENDING_MANGA -> bangumi.trendingManga(limit, offset = offset)
            ExploreCategoryType.NEWLY_ADDED_MANGA -> bangumi.publishingManga(limit, offset = offset)
            else -> emptyList()
        }.filter { adult || !it.isAdult }
    }

    suspend fun fetchSimklPage(page: Int): List<JikanSearchResult> = when (categoryType) {
        ExploreCategoryType.TOP_ANIME -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("tv", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.AIRING_ANIME -> if (page == 1) simklApiClient.getAiringMedia("tv") else emptyList()
        ExploreCategoryType.MOVIE_ANIME -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("movies", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.TOP_MANGA -> simklApiClient.getExploreGenrePage("tv", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
        // The Simkl explore tile is the popular-movies list, not an upcoming-release list.
        // `this-week` is not a valid movie year filter; use the documented weekly sort instead.
        ExploreCategoryType.UPCOMING_ANIME -> simklApiClient.getExploreGenrePage(
            "movies", "popular-this-week", page, SIMKL_EXPLORE_PAGE_SIZE
        )
        ExploreCategoryType.PUBLISHING_MANGA -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("movies", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.SEASONAL_ANIME -> simklApiClient.getExploreGenrePage("tv", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
        ExploreCategoryType.TRENDING_ANIME -> {
            val chart = simklTrendingResults ?: simklApiClient.getTrendingPeriod("anime", "week")
                .also { simklTrendingResults = it }
            chart.drop((page - 1) * SIMKL_EXPLORE_PAGE_SIZE).take(SIMKL_EXPLORE_PAGE_SIZE)
        }
        ExploreCategoryType.NEWLY_ADDED_ANIME -> simklApiClient.getExploreGenrePage("anime", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
        ExploreCategoryType.TRENDING_MANGA -> if (page == 1) simklApiClient.getAiringMedia("anime") else emptyList()
        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> simklApiClient.getPremieresPage(
            "anime", "soon", page, SIMKL_EXPLORE_PAGE_SIZE
        )
        else -> simklApiClient.getExploreGenrePage("anime", "rank", page, SIMKL_EXPLORE_PAGE_SIZE)
    }

    fun loadNextPage() {
        if (isLoadingMore || !hasMorePages) return
        isLoadingMore = true; loadError = null
        scope.launch {
            try {
                val isInitialSimklPage = platform == ExplorePlatform.SIMKL && !simklPageInitialized
                val np = if (isInitialSimklPage) 1 else currentPage + 1
                val newItems = when (platform) {
                    ExplorePlatform.MAL -> when (categoryType) {
                        ExploreCategoryType.TOP_RATED_ANIME -> apiClient.topAnime(np, showAdultContent)
                        ExploreCategoryType.TOP_RATED_MANGA -> apiClient.topManga(np, showAdultContent)
                        ExploreCategoryType.TOP_ANIME -> apiClient.topAnime(np)
                        ExploreCategoryType.TRENDING_ANIME -> apiClient.trendingAnime(np)
                        ExploreCategoryType.AIRING_ANIME -> apiClient.airingAnime(np)
                        ExploreCategoryType.UPCOMING_ANIME -> apiClient.upcomingAnime(np)
                        ExploreCategoryType.MOVIE_ANIME -> apiClient.movieAnime(np)
                        ExploreCategoryType.SEASONAL_ANIME -> apiClient.seasonalAnime(np, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
                        ExploreCategoryType.TOP_MANGA -> apiClient.topManga(np)
                        ExploreCategoryType.PUBLISHING_MANGA -> apiClient.publishingManga(np)
                        ExploreCategoryType.TRENDING_MANGA -> apiClient.trendingManga(np)
                        ExploreCategoryType.NEWLY_ADDED_ANIME -> apiClient.newlyAddedAnime(np)
                        ExploreCategoryType.NEWLY_ADDED_MANGA -> apiClient.newlyAddedManga(np)
                        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> emptyList()
                    }
                    ExplorePlatform.AniList -> when (categoryType) {
                        ExploreCategoryType.TOP_RATED_ANIME -> apiClient.aniListTopRated(com.kitsugi.animelist.model.MediaType.Anime, np, showAdultContent)
                        ExploreCategoryType.TOP_RATED_MANGA -> apiClient.aniListTopRated(com.kitsugi.animelist.model.MediaType.Manga, np, showAdultContent)
                        ExploreCategoryType.TOP_ANIME -> apiClient.aniListTopAnime(np)
                        ExploreCategoryType.TRENDING_ANIME -> apiClient.aniListTrendingAnime(np)
                        ExploreCategoryType.AIRING_ANIME -> apiClient.aniListAiringAnime(np)
                        ExploreCategoryType.UPCOMING_ANIME -> apiClient.aniListUpcomingAnime(np)
                        ExploreCategoryType.MOVIE_ANIME -> apiClient.aniListMovieAnime(np)
                        ExploreCategoryType.SEASONAL_ANIME -> apiClient.aniListSeasonalAnime(np, showAdultContent, seasonalYear, seasonalSeason, seasonalSort)
                        ExploreCategoryType.TOP_MANGA -> apiClient.aniListTopManga(np)
                        ExploreCategoryType.PUBLISHING_MANGA -> apiClient.aniListPublishingManga(np)
                        ExploreCategoryType.TRENDING_MANGA -> apiClient.aniListTrendingManga(np)
                        ExploreCategoryType.NEWLY_ADDED_ANIME -> apiClient.aniListNewlyAddedAnime(np)
                        ExploreCategoryType.NEWLY_ADDED_MANGA -> apiClient.aniListNewlyAddedManga(np)
                        ExploreCategoryType.UPCOMING_MEDIA_TMDB -> emptyList()
                    }
                    ExplorePlatform.TMDB -> {
                        if (title.startsWith("İzlemeye Devam") || title.startsWith("Planladıklarım")) emptyList()
                        else when (categoryType) {
                            ExploreCategoryType.TOP_ANIME -> tmdbApiClient.getTrendingAll(np)
                            ExploreCategoryType.TRENDING_ANIME -> tmdbApiClient.getTrendingMedia(np)
                            ExploreCategoryType.AIRING_ANIME -> tmdbApiClient.getTrendingShows(np)
                            ExploreCategoryType.MOVIE_ANIME -> tmdbApiClient.getTrendingMovies(np)
                            ExploreCategoryType.UPCOMING_ANIME -> tmdbApiClient.getPopularMovies(np)
                            ExploreCategoryType.TOP_MANGA -> tmdbApiClient.getPopularShows(np)
                            ExploreCategoryType.PUBLISHING_MANGA -> tmdbApiClient.getTopRatedMovies(np)
                            ExploreCategoryType.SEASONAL_ANIME -> tmdbApiClient.getTopRatedShows(np)
                            ExploreCategoryType.TRENDING_MANGA -> tmdbApiClient.getTopRatedAnime(np)
                            ExploreCategoryType.NEWLY_ADDED_ANIME -> tmdbApiClient.getPopularMedia(np)
                            ExploreCategoryType.UPCOMING_MEDIA_TMDB -> tmdbApiClient.getUpcomingMedia(np)
                            else -> emptyList()
                        }
                    }
                    ExplorePlatform.KITSU -> when (categoryType) {
                        ExploreCategoryType.TOP_RATED_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedAnime(20, offset = (np - 1) * 20)
                        ExploreCategoryType.TOP_RATED_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedManga(20, offset = (np - 1) * 20)
                        ExploreCategoryType.TRENDING_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingAnime(20)
                        ExploreCategoryType.MOVIE_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.movieAnime(20, offset = (np - 1) * 20)
                        ExploreCategoryType.SEASONAL_ANIME -> fetchSeasonalPage(np)
                        ExploreCategoryType.TOP_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topAnime(20, offset = (np - 1) * 20)
                        ExploreCategoryType.AIRING_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.airingAnime(20, offset = (np - 1) * 20)
                        ExploreCategoryType.UPCOMING_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.upcomingAnime(20, offset = (np - 1) * 20)
                        ExploreCategoryType.TOP_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.topManga(20, offset = (np - 1) * 20)
                        ExploreCategoryType.PUBLISHING_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.publishingManga(20, offset = (np - 1) * 20)
                        ExploreCategoryType.TRENDING_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingManga(20, offset = (np - 1) * 20)
                        ExploreCategoryType.NEWLY_ADDED_ANIME -> com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedAnime(20, offset = (np - 1) * 20)
                        ExploreCategoryType.NEWLY_ADDED_MANGA -> com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedManga(20, offset = (np - 1) * 20)
                        else -> emptyList()
                    }
                    ExplorePlatform.SHIKIMORI -> when (categoryType) {
                        ExploreCategoryType.TRENDING_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.trendingAnime(limit = 20, page = np, censored = !showAdultContent)
                        ExploreCategoryType.MOVIE_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.movieAnime(limit = 20, page = np, censored = !showAdultContent)
                        ExploreCategoryType.SEASONAL_ANIME -> fetchSeasonalPage(np)
                        ExploreCategoryType.TRENDING_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, order = "popularity", page = np, limit = 20, censored = !showAdultContent)
                        ExploreCategoryType.TOP_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, order = "ranked", page = np, limit = 20, censored = !showAdultContent)
                        ExploreCategoryType.AIRING_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.airingAnime(limit = 20, page = np, censored = !showAdultContent)
                        ExploreCategoryType.UPCOMING_ANIME -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Anime, statuses = listOf("anons"), order = "popularity", page = np, limit = 20, censored = !showAdultContent)
                        ExploreCategoryType.TOP_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, order = "ranked", page = np, limit = 20, censored = !showAdultContent)
                        ExploreCategoryType.PUBLISHING_MANGA -> com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(com.kitsugi.animelist.model.MediaType.Manga, statuses = listOf("ongoing"), order = "popularity", page = np, limit = 20, censored = !showAdultContent)
                        else -> emptyList()
                    }
                    ExplorePlatform.BANGUMI -> bangumiPage(np)
                    ExplorePlatform.SIMKL -> fetchSimklPage(np)
                    else -> emptyList()
                }
                if (isInitialSimklPage) {
                    if (newItems.isEmpty()) {
                        // Keep the already-visible chart data and allow the page-one request to be retried.
                        loadError = "Simkl listesinin ilk sayfası alınamadı. Tekrar deneyin."
                    } else {
                        loadedResults = newItems
                        currentPage = 1
                        simklPageInitialized = true
                        hasMorePages = simklCategoryHasPagination &&
                            newItems.size >= SIMKL_EXPLORE_PAGE_SIZE &&
                            (simklCategoryUsesFiniteChart || 1 < SIMKL_MAX_API_PAGE)
                    }
                } else {
                    val existingKeys = loadedResults.map { it.exploreIdentity() }.toHashSet()
                    val uniqueItems = newItems.filter { existingKeys.add(it.exploreIdentity()) }
                    if (uniqueItems.isNotEmpty()) {
                        loadedResults = loadedResults + uniqueItems
                        currentPage = np
                    }
                    if (uniqueItems.isEmpty() || finiteChart) hasMorePages = false
                    if (platform == ExplorePlatform.SIMKL && simklCategoryHasPagination &&
                        (newItems.size < SIMKL_EXPLORE_PAGE_SIZE ||
                            (!simklCategoryUsesFiniteChart && np >= SIMKL_MAX_API_PAGE))
                    ) {
                        hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                loadError = e.message ?: "Yükleme hatası"
            } finally { isLoadingMore = false }
        }
    }

    LaunchedEffect(platform, categoryType, isSimklPersonalList) {
        if (!isSimklPersonalList && (platform == ExplorePlatform.SIMKL || loadedResults.isEmpty())) {
            loadNextPage()
        }
    }

    fun applySeasonalFilter(season: String, year: Int, sort: String) {
        seasonalSeason = season; seasonalYear = year; seasonalSort = sort
        currentPage = 1; hasMorePages = true; loadedResults = emptyList()
        isLoadingMore = true; loadError = null
        scope.launch {
            try {
                loadedResults = fetchSeasonalPage(1)
            } catch (e: Exception) {
                loadError = e.message ?: "Hata"
            } finally { isLoadingMore = false }
        }
    }

    val displayedResults = remember(loadedResults, showAdultContent, selectedGenreId, selectedSortOption) {
        val base = loadedResults.filter { showAdultContent || !it.isAdult }
        val filtered = if (selectedGenreId == "ALL") {
            base
        } else {
            val genreObj = KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }
            if (genreObj != null) {
                base.filter { item ->
                    val inGenres = item.genres.any { g ->
                        genreObj.keywords.any { kw -> g.contains(kw, ignoreCase = true) }
                    }
                    val inSubtitle = genreObj.keywords.any { kw ->
                        item.subtitle.contains(kw, ignoreCase = true)
                    }
                    inGenres || inSubtitle
                }
            } else {
                base
            }
        }

        when (selectedSortOption) {
            KitsugiGridSortOption.DEFAULT -> filtered
            KitsugiGridSortOption.SCORE_DESC -> filtered.sortedByDescending {
                it.rawScoreDouble ?: (it.score?.toDouble()?.div(10.0)) ?: 0.0
            }
            KitsugiGridSortOption.SCORE_ASC -> filtered.sortedWith(
                compareBy {
                    val s = it.rawScoreDouble ?: (it.score?.toDouble()?.div(10.0)) ?: 0.0
                    if (s <= 0.0) 999.0 else s
                }
            )
            KitsugiGridSortOption.POPULARITY_DESC -> filtered.sortedByDescending {
                it.members ?: it.favorites ?: 0
            }
            KitsugiGridSortOption.YEAR_DESC -> filtered.sortedByDescending {
                it.year ?: 0
            }
            KitsugiGridSortOption.YEAR_ASC -> filtered.sortedWith(
                compareBy {
                    val y = it.year ?: 0
                    if (y <= 0) 9999 else y
                }
            )
            KitsugiGridSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            KitsugiGridSortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }

    // Liste scroll state + auto-load trigger
    val listState = rememberLazyListState()
    val shouldLoadMoreList by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMoreList, isLoadingMore, hasMorePages, isGridView) {
        if (!isGridView && shouldLoadMoreList && !isLoadingMore && hasMorePages) loadNextPage()
    }

    // Grid scroll state + auto-load trigger
    val gridState = rememberLazyGridState()
    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMoreGrid, isLoadingMore, hasMorePages, isGridView) {
        if (isGridView && shouldLoadMoreGrid && !isLoadingMore && hasMorePages) loadNextPage()
    }

    val showFloatingHeader = if (isGridView) gridState.firstVisibleItemIndex >= 1
    else listState.firstVisibleItemIndex >= 1

    val showScrollToTop by remember {
        derivedStateOf {
            if (isGridView) {
                gridState.firstVisibleItemIndex > 3
            } else {
                listState.firstVisibleItemIndex > 3
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(KitsugiColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {

        if (isGridView) {
            // ── GRID MODU ────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Başlık - tam genişlik span
                item(key = "grid_header", span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                        // Geri butonu
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onBackClick) {
                                Text("Geri", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Başlık + toggle + filtre
                        val hasActiveFilter = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dynamicTitle,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            // Filtre ve Sıralama Butonu (Her kategoride aktif)
                            IconButton(
                                onClick = {
                                    if (isSeasonalAnime) {
                                        showFilterBottomSheet = true
                                    } else {
                                        showGeneralFilterBottomSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else KitsugiColors.Surface)
                            ) {
                                Icon(
                                    Icons.Rounded.FilterList,
                                    contentDescription = "Filtre ve Sıralama",
                                    tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Grid→Liste toggle
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val gridIndex = gridState.firstVisibleItemIndex
                                        val gridOffset = gridState.firstVisibleItemScrollOffset
                                        val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                                        isGridView = false
                                        kotlinx.coroutines.delay(10)
                                        listState.scrollToItem(listIndex, gridOffset)
                                    }
                                },
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitsugiColors.Surface)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ListAlt, contentDescription = "Liste Görünümü", tint = accentColor)
                            }
                        }

                        // Emojili Hızlı Tür Çipleri (Yatay Kaydırılabilir)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            items(KITSUGI_MEDIA_GENRES) { genre ->
                                val isSelected = selectedGenreId == genre.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.22f)
                                            else KitsugiColors.Surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) accentColor else Color.Transparent,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            selectedGenreId = if (isSelected && genre.id != "ALL") "ALL" else genre.id
                                        }
                                        .padding(horizontal = 13.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = genre.displayLabel,
                                        color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        val activeGenre = KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }
                        val activeFilterDesc = buildString {
                            if (activeGenre != null && activeGenre.id != "ALL") append(" • ${activeGenre.displayLabel}")
                            if (selectedSortOption != KitsugiGridSortOption.DEFAULT) append(" • ${selectedSortOption.displayLabel}")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${displayedResults.size} içerik$activeFilterDesc",
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasActiveFilter) {
                                Text(
                                    text = "Temizle",
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        selectedGenreId = "ALL"
                                        selectedSortOption = KitsugiGridSortOption.DEFAULT
                                    }
                                )
                            }
                        }
                    }
                }

                if (displayedResults.isEmpty() && !isLoadingMore) {
                    item(key = "grid_empty", span = { GridItemSpan(maxLineSpan) }) {
                        KitsugiEmptyState(
                            title = "Henüz içerik yok",
                            subtitle = "Bu kategoride gösterilecek içerik bulunamadı.",
                            icon = Icons.Rounded.SearchOff
                        )
                    }
                } else {
                    itemsIndexed(
                        displayedResults,
                        key = { idx, item -> "${item.source}_${item.malId}_g$idx" }
                    ) { index, result ->
                        val showRank = !isSeasonalAnime
                        KitsugiExploreMediaCard(
                            result = result,
                            alreadyInList = alreadyInList(result),
                            mediaEntry = getMediaEntry(result),
                            onClick = { onItemClick(result) },
                            titleLanguage = titleLanguage,
                            scoreFormat = scoreFormat,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia,
                            forceVertical = !isLandscape,
                            rankIndex = if (showRank) index + 1 else null
                        )
                    }
                }

                if (isLoadingMore) {
                    item(key = "grid_loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = accentColor) }
                    }
                }

                if (loadError != null) {
                    item(key = "grid_error", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(loadError ?: "Bir hata oluştu.", color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { loadNextPage() }) {
                                Text("Tekrar Dene", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

        } else {
            // ── LİSTE MODU ───────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "list_header") {
                    Column {
                        Spacer(modifier = Modifier.height(28.dp))
                        // Geri butonu
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onBackClick) {
                                Text("Geri", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Başlık + toggle + filtre
                        val hasActiveFilter = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dynamicTitle,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            // Filtre ve Sıralama Butonu (Her kategoride aktif)
                            IconButton(
                                onClick = {
                                    if (isSeasonalAnime) {
                                        showFilterBottomSheet = true
                                    } else {
                                        showGeneralFilterBottomSheet = true
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else KitsugiColors.Surface)
                            ) {
                                Icon(
                                    Icons.Rounded.FilterList,
                                    contentDescription = "Filtre ve Sıralama",
                                    tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Liste→Grid toggle
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val listIndex = listState.firstVisibleItemIndex
                                        val listOffset = listState.firstVisibleItemScrollOffset
                                        val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                                        isGridView = true
                                        kotlinx.coroutines.delay(10)
                                        gridState.scrollToItem(gridIndex, listOffset)
                                    }
                                },
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KitsugiColors.Surface)
                            ) {
                                Icon(Icons.Rounded.GridView, contentDescription = "Grid Görünümü", tint = accentColor)
                            }
                        }

                        // Emojili Hızlı Tür Çipleri (Yatay Kaydırılabilir)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            items(KITSUGI_MEDIA_GENRES) { genre ->
                                val isSelected = selectedGenreId == genre.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.22f)
                                            else KitsugiColors.Surface
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) accentColor else Color.Transparent,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            selectedGenreId = if (isSelected && genre.id != "ALL") "ALL" else genre.id
                                        }
                                        .padding(horizontal = 13.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = genre.displayLabel,
                                        color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "list_count") {
                    val hasActiveFilter = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                    val activeGenre = KITSUGI_MEDIA_GENRES.firstOrNull { it.id == selectedGenreId }
                    val activeFilterDesc = buildString {
                        if (activeGenre != null && activeGenre.id != "ALL") append(" • ${activeGenre.displayLabel}")
                        if (selectedSortOption != KitsugiGridSortOption.DEFAULT) append(" • ${selectedSortOption.displayLabel}")
                    }

                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${displayedResults.size} içerik$activeFilterDesc",
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasActiveFilter) {
                                Text(
                                    text = "Temizle",
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        selectedGenreId = "ALL"
                                        selectedSortOption = KitsugiGridSortOption.DEFAULT
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (displayedResults.isEmpty() && !isLoadingMore) {
                    item(key = "list_empty") {
                        KitsugiEmptyState(
                            title = "Henüz içerik yok",
                            subtitle = "Bu kategoride gösterilecek içerik bulunamadı.",
                            icon = Icons.Rounded.SearchOff
                        )
                    }
                } else {
                    itemsIndexed(
                        displayedResults,
                        key = { idx, item -> "${item.source}_${item.malId}_l$idx" }
                    ) { index, result ->
                        if (isSeasonalAnime) {
                            DetailedSeasonalMediaCard(
                                result = result,
                                alreadyInList = alreadyInList(result),
                                onClick = { onItemClick(result) },
                                titleLanguage = titleLanguage,
                                blurAdultMedia = blurAdultMedia
                            )
                        } else {
                            KitsugiRankingMediaCard(
                                result = result,
                                rankIndex = index + 1,
                                alreadyInList = alreadyInList(result),
                                mediaEntry = getMediaEntry(result),
                                onClick = { onItemClick(result) },
                                titleLanguage = titleLanguage,
                                hideScores = hideScores,
                                blurAdultMedia = blurAdultMedia
                            )
                        }
                    }
                }

                if (isLoadingMore) {
                    item(key = "list_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = accentColor) }
                    }
                }

                if (loadError != null) {
                    item(key = "list_error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(loadError ?: "Bir hata oluştu.", color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { loadNextPage() }) {
                                Text("Tekrar Dene", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Overlay Header ───────────────────────────────────────────
        AnimatedVisibility(
            visible = showFloatingHeader,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = (if (isTv) Modifier.width(960.dp) else Modifier.fillMaxWidth())
                    .height(64.dp)
                    .background(KitsugiColors.Surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri", tint = KitsugiColors.TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dynamicTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Filtre ve Sıralama Butonu (Her zaman toggle'dan önce gösterilir)
                val hasActiveFilterFloating = selectedGenreId != "ALL" || selectedSortOption != KitsugiGridSortOption.DEFAULT
                IconButton(
                    onClick = {
                        if (isSeasonalAnime) {
                            showFilterBottomSheet = true
                        } else {
                            showGeneralFilterBottomSheet = true
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilterFloating) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Rounded.FilterList,
                        contentDescription = "Filtre ve Sıralama",
                        tint = if (hasActiveFilterFloating) accentColor else KitsugiColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Toggle – floating header'da da göster
                IconButton(onClick = {
                    scope.launch {
                        if (isGridView) {
                            val gridIndex = gridState.firstVisibleItemIndex
                            val gridOffset = gridState.firstVisibleItemScrollOffset
                            val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                            isGridView = false
                            kotlinx.coroutines.delay(10)
                            listState.scrollToItem(listIndex, gridOffset)
                        } else {
                            val listIndex = listState.firstVisibleItemIndex
                            val listOffset = listState.firstVisibleItemScrollOffset
                            val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                            isGridView = true
                            kotlinx.coroutines.delay(10)
                            gridState.scrollToItem(gridIndex, listOffset)
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ListAlt else Icons.Rounded.GridView,
                        contentDescription = if (isGridView) "Liste" else "Grid",
                        tint = accentColor
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor)
                    .tvClickable(shape = RoundedCornerShape(16.dp)) {
                        scope.launch {
                            if (isGridView) {
                                gridState.animateScrollToItem(0)
                            } else {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Yukarı Git",
                    tint = KitsugiColors.Background,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (showFilterBottomSheet) {
            KitsugiSeasonalFilterBottomSheet(
                initialSeason = seasonalSeason,
                initialYear = seasonalYear,
                initialSort = seasonalSort,
                onDismissRequest = { showFilterBottomSheet = false },
                onApply = { s, y, sort -> applySeasonalFilter(s, y, sort) }
            )
        }

        if (showGeneralFilterBottomSheet) {
            KitsugiMediaFilterBottomSheet(
                initialGenreId = selectedGenreId,
                initialSortOption = selectedSortOption,
                onDismissRequest = { showGeneralFilterBottomSheet = false },
                onApply = { genreId, sortOption ->
                    selectedGenreId = genreId
                    selectedSortOption = sortOption
                },
                onReset = {
                    selectedGenreId = "ALL"
                    selectedSortOption = KitsugiGridSortOption.DEFAULT
                }
            )
        }
    }
}
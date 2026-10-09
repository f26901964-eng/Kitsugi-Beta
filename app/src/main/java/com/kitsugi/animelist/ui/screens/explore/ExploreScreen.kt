@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.explore

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiHeroSection
import com.kitsugi.animelist.ui.components.KitsugiShimmerHeroSection
import com.kitsugi.animelist.ui.components.KitsugiErrorState
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiShimmerProvider
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec

@Composable
fun ExploreScreen(
    currentEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>, platform: ExplorePlatform) -> Unit,
    onNavigateToWatchHistory: () -> Unit = {},
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onEditEntry: (MediaEntry) -> Unit,
    onOpenMangaReader: () -> Unit = {},
    onOpenAiringCalendar: () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
    viewModel: ExploreViewModel = viewModel(),
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAnimeLogos: Boolean = false,
    isSimklConnected: Boolean = false,
    blurAdultMedia: Boolean = false,
    onOpenNotifications: () -> Unit = {},
    isNotificationsVisible: Boolean = false,
    /** Alt navigasyon barı görünüyor mu? — "Yukarı Çık" FAB'ı alt barın üstünde hizalanır. */
    isBottomBarVisible: Boolean = true,
    /** Liste tepesine dönüldüğünde çağrılır (diğer ana sayfalarla aynı sözleşme). */
    onScrollReset: (() -> Unit)? = null,
    /** TMDB hatası — Ayarlar/Entegrasyonlar sayfasına yönlendir */
    onRedirectToSettings: (() -> Unit)? = null,
    /** AniList/MAL hatası — Giriş yap sayfasına yönlendir */
    onRedirectToAuth: (() -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val onSeeAllForSelected: (String, ExploreCategoryType, List<JikanSearchResult>) -> Unit = { title, category, results ->
        onSeeAllSection(title, category, results, viewModel.selectedPlatform)
    }

    val filteredTopAnime = remember(viewModel.topAnime, showAdultContent) { viewModel.topAnime.filter { showAdultContent || !it.isAdult } }
    val filteredAiringAnime = remember(viewModel.airingAnime, showAdultContent) { viewModel.airingAnime.filter { showAdultContent || !it.isAdult } }
    val filteredUpcomingAnime = remember(viewModel.upcomingAnime, showAdultContent) { viewModel.upcomingAnime.filter { showAdultContent || !it.isAdult } }
    val filteredTopManga = remember(viewModel.topManga, showAdultContent) { viewModel.topManga.filter { showAdultContent || !it.isAdult } }
    val filteredPublishingManga = remember(viewModel.publishingManga, showAdultContent) { viewModel.publishingManga.filter { showAdultContent || !it.isAdult } }
    val filteredTrendingAnime = remember(viewModel.trendingAnime, showAdultContent) { viewModel.trendingAnime.filter { showAdultContent || !it.isAdult } }
    val filteredMovieAnime = remember(viewModel.movieAnime, showAdultContent) { viewModel.movieAnime.filter { showAdultContent || !it.isAdult } }
    val filteredSeasonalAnime = remember(viewModel.seasonalAnime, showAdultContent) { viewModel.seasonalAnime.filter { showAdultContent || !it.isAdult } }
    val filteredTrendingManga = remember(viewModel.trendingManga, showAdultContent) { viewModel.trendingManga.filter { showAdultContent || !it.isAdult } }
    val filteredNewlyAddedAnime = remember(viewModel.newlyAddedAnime, showAdultContent) { viewModel.newlyAddedAnime.filter { showAdultContent || !it.isAdult } }
    val filteredNewlyAddedManga = remember(viewModel.newlyAddedManga, showAdultContent) { viewModel.newlyAddedManga.filter { showAdultContent || !it.isAdult } }
    val filteredAiringSoonAnime = remember(viewModel.airingSoonAnime, showAdultContent) { viewModel.airingSoonAnime.filter { showAdultContent || !it.isAdult } }
    val filteredUpcomingMediaTmdb = remember(viewModel.upcomingMediaTmdb, showAdultContent) { viewModel.upcomingMediaTmdb.filter { showAdultContent || !it.isAdult } }

    // Vitrin: tüm kaynak/kategori havuzundan sayısal metriklere göre seçim.
    //  - Tümü modu: her kaynaktan en az bir temsil + skor sıralı kontenjan (12'ye kadar).
    //  - Kaynak modu: o kaynağın tüm bölümleri (trend, yeni eklenen, manga, film...)
    //    metrik tabanlı puanlanır; kategori tavanlarıyla çeşitlilik korunur (10'a kadar).
    val heroItems = remember(
        viewModel.selectedPlatform,
        viewModel.allSourceStates,
        showAdultContent,
        filteredTopAnime,
        filteredAiringAnime,
        filteredUpcomingAnime,
        filteredTopManga,
        filteredPublishingManga,
        filteredTrendingAnime,
        filteredMovieAnime,
        filteredSeasonalAnime,
        filteredTrendingManga,
        filteredNewlyAddedAnime,
        filteredNewlyAddedManga,
        filteredUpcomingMediaTmdb
    ) {
        if (viewModel.selectedPlatform == ExplorePlatform.ALL) {
            allSourceHeroes(viewModel.allSourceStates, showAdultContent)
        } else {
            val sections = sourceSections(
                viewModel.selectedPlatform,
                ExplorePayload(
                    topAnime = filteredTopAnime,
                    airingAnime = filteredAiringAnime,
                    upcomingAnime = filteredUpcomingAnime,
                    topManga = filteredTopManga,
                    publishingManga = filteredPublishingManga,
                    trendingAnime = filteredTrendingAnime,
                    movieAnime = filteredMovieAnime,
                    seasonalAnime = filteredSeasonalAnime,
                    trendingManga = filteredTrendingManga,
                    newlyAddedAnime = filteredNewlyAddedAnime,
                    newlyAddedManga = filteredNewlyAddedManga,
                    upcomingMediaTmdb = filteredUpcomingMediaTmdb
                )
            ).filter { it.results.isNotEmpty() }
            selectHeroItems(
                sections = sections,
                limit = HERO_LIMIT_SINGLE,
                guaranteeSourceCoverage = false,
                perCategoryCap = HERO_PER_CATEGORY_CAP
            )
        }
    }

    val entryMap = remember(currentEntries) {
        generateExploreEntryMap(currentEntries)
    }

    // TMDB'den sonradan gelen vitrin arka planlarını (landscape'te kullanılır) birleştir.
    val displayHeroItems = remember(heroItems, viewModel.heroBackdropOverrides) {
        val overrides = viewModel.heroBackdropOverrides
        if (overrides.isEmpty()) {
            heroItems
        } else {
            heroItems.map { item ->
                overrides[item.exploreIdentity()]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { url -> item.copy(backdropUrl = url) }
                    ?: item
            }
        }
    }

    val getMediaEntry = remember(entryMap) {
        { result: JikanSearchResult ->
            getMediaEntryFromMap(result, entryMap)
        }
    }

    val isAlreadyInList = remember(getMediaEntry) {
        { result: JikanSearchResult ->
            getMediaEntry(result) != null
        }
    }

    val onLongClickItem = remember(getMediaEntry) {
        { result: JikanSearchResult ->
            val entry = getMediaEntry(result)
            if (entry != null) {
                onEditEntry(entry)
            } else {
                onAddSelectionToList(ApiSearchSelection(result = result, synopsis = null))
            }
        }
    }

    val hasCatalogContent = heroItems.isNotEmpty() || filteredTopAnime.isNotEmpty() ||
        filteredAiringAnime.isNotEmpty() || filteredTopManga.isNotEmpty() ||
        filteredTrendingAnime.isNotEmpty() || viewModel.simklContinueSeries.isNotEmpty()
    val lazyListState = com.kitsugi.animelist.ui.utils.rememberRetainedLazyListState(
        contentReady = hasCatalogContent,
        initialIndex = initialScrollIndex,
        initialOffset = initialScrollOffset
    )

    val allSourcesScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Kalan yapışkan başlık: kaynak seçici pill satırı (~48dp). Kaynak başlıkları bu
    // yüksekliğin ALTINDA oturmalı ki sticky pill'in arkasına gizlenmesinler.
    val stickyToggleHeight = with(density) { 56.dp.roundToPx() }
    var collapsedSourceNames by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(emptyList<String>()) }

    var activeRankingSheetData by remember { mutableStateOf<Triple<String, MediaType, List<JikanSearchResult>>?>(null) }
    var isCategoriesExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var showSourceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(lazyListState, hasCatalogContent) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (hasCatalogContent) onScrollPositionChange(index, offset)
        }
    }

    val isTvDevice = LocalIsTvDevice.current

    // ── "Yukarı Çık" FAB — arama/liste sayfalarıyla aynı davranış, alt barla uyumlu ──
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Yatay modda kullanılacak arka planlar eksikse seçilen vitrin öğeleri için TMDB'den tamamla.
    LaunchedEffect(displayHeroItems, isLandscape) {
        if (isLandscape && displayHeroItems.isNotEmpty()) {
            viewModel.enrichHeroBackdrops(displayHeroItems)
        }
    }
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isLandscape) {
            16.dp
        } else if (isBottomBarVisible) {
            80.dp + navigationBarsPadding
        } else {
            16.dp + navigationBarsPadding
        },
        animationSpec = tween(durationMillis = 200),
        label = "explore_fab_bottom_padding"
    )
    val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 1 } }

    // Manuel olarak tepeye dönüldüğünde sıfırlama sinyali ver (diğer sayfalarla aynı sözleşme).
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (firstIndex, scrollOffset) ->
                if (firstIndex == 0 && scrollOffset == 0) {
                    onScrollReset?.invoke()
                }
            }
    }

    val isCatalogEmpty = !viewModel.isLoading && viewModel.errorMessage == null &&
        filteredTopAnime.isEmpty() && filteredAiringAnime.isEmpty() &&
        filteredUpcomingAnime.isEmpty() && filteredTopManga.isEmpty() &&
        filteredPublishingManga.isEmpty() && filteredTrendingAnime.isEmpty() &&
        filteredMovieAnime.isEmpty() && filteredSeasonalAnime.isEmpty() &&
        viewModel.simklContinueSeries.isEmpty() && viewModel.simklContinueMovies.isEmpty() &&
        viewModel.simklPlannedSeries.isEmpty() && viewModel.simklPlannedMovies.isEmpty() &&
        filteredNewlyAddedAnime.isEmpty() && filteredNewlyAddedManga.isEmpty() &&
        filteredAiringSoonAnime.isEmpty()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
            val pullRefreshState = rememberPullToRefreshState()
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides if (isTvDevice) tvSpec else LocalBringIntoViewSpec.current
            ) {
                PullToRefreshBox(
                    isRefreshing = viewModel.isLoading,
                    onRefresh = { viewModel.loadData(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize(),
                    state = pullRefreshState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullRefreshState,
                            isRefreshing = viewModel.isLoading,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = KitsugiColors.Surface,
                            color = accentColor
                        )
                    }
                ) {
                    KitsugiShimmerProvider {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            if (heroItems.isNotEmpty()) {
                                item {
                                    KitsugiHeroSection(
                                        items = displayHeroItems,
                                        alreadyInList = isAlreadyInList,
                                        onInfoClick = onOpenApiDetail,
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        showAnimeLogos = showAnimeLogos,
                                        blurAdultMedia = blurAdultMedia,
                                        isVisible = lazyListState.firstVisibleItemIndex == 0
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            } else if (viewModel.isLoading) {
                                item {
                                    KitsugiShimmerHeroSection()
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            } else {
                                item {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }

                            // Header / Title and Toggle as STICKY HEADER
                            stickyHeader(key = "explore_platform_toggle") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = KitsugiColors.Background.copy(alpha = 0.95f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 6.dp)
                                    ) {
                                        if (isTvDevice) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Keşfet",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = KitsugiColors.TextPrimary
                                                )
                                                ExploreSourceEngineSelectorPill(
                                                    selectedPlatform = viewModel.selectedPlatform,
                                                    onClick = { showSourceSheet = true }
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ExploreSourceEngineSelectorPill(
                                                    selectedPlatform = viewModel.selectedPlatform,
                                                    onClick = { showSourceSheet = true },
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (isNotificationsVisible) {
                                                    // 🔔 Bildirim butonu
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(KitsugiColors.SurfaceElevated)
                                                            .border(1.dp, KitsugiColors.SurfaceElevated.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                                            .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                                                onOpenNotifications()
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Notifications,
                                                            contentDescription = "Bildirimler",
                                                            tint = KitsugiColors.TextPrimary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                } else {
                                                    // 🎲 Rastgele keşfet butonu
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(KitsugiColors.SurfaceElevated)
                                                            .border(1.dp, KitsugiColors.SurfaceElevated.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                                            .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                                                val randomPool = mutableListOf<JikanSearchResult>()
                                                                randomPool.addAll(filteredTopAnime)
                                                                randomPool.addAll(filteredAiringAnime)
                                                                randomPool.addAll(filteredUpcomingAnime)
                                                                randomPool.addAll(filteredTopManga)
                                                                randomPool.addAll(filteredPublishingManga)
                                                                randomPool.addAll(filteredTrendingAnime)
                                                                randomPool.addAll(filteredMovieAnime)
                                                                randomPool.addAll(filteredSeasonalAnime)
                                                                randomPool.addAll(viewModel.simklContinueMovies)
                                                                randomPool.addAll(viewModel.simklPlannedMovies)
                                                                randomPool.addAll(viewModel.simklContinueSeries)
                                                                randomPool.addAll(viewModel.simklPlannedSeries)
                                                                if (viewModel.selectedPlatform == ExplorePlatform.ALL) {
                                                                    randomPool.addAll(allSourceSections(viewModel.allSourceStates, showAdultContent)
                                                                        .flatMap { it.results }.distinctBy { it.exploreIdentity() })
                                                                }
                                                                randomPool.filter { showAdultContent || !it.isAdult }
                                                                    .randomOrNull()?.let(onOpenApiDetail)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "🎲",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Önbellek Bildirim Banner'ı
                            if (viewModel.isShowingCachedData) {
                                item(key = "cached_data_banner") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(KitsugiColors.Surface.copy(alpha = 0.5f))
                                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "📡",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Column {
                                                Text(
                                                    text = "Çevrimdışı / Önbellek Modu",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KitsugiColors.TextPrimary
                                                )
                                                Text(
                                                    text = "İnternet bağlantısı kesildi veya sunucu yanıt vermiyor. Son başarılı önbelleğe alınan veriler gösteriliyor.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = KitsugiColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Hata mesajı
                            if (viewModel.errorMessage != null) {
                                item(key = "error_message") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        val errorType = viewModel.exploreErrorType
                                        KitsugiErrorState(
                                            message = viewModel.errorMessage.orEmpty(),
                                            onRetryClick = { viewModel.loadData(forceRefresh = true) },
                                            actionButton = when {
                                                errorType == ExploreErrorType.TmdbError && onRedirectToSettings != null -> ({
                                                    OutlinedButton(
                                                        onClick = { onRedirectToSettings.invoke() },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = LocalKitsugiAccent.current
                                                        ),
                                                        border = ButtonDefaults.outlinedButtonBorder
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Key,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "TMDB API Anahtarı Ayarla",
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                })
                                                (errorType == ExploreErrorType.AniListError || errorType == ExploreErrorType.MalError) && onRedirectToAuth != null -> ({
                                                    OutlinedButton(
                                                        onClick = { onRedirectToAuth.invoke() },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = LocalKitsugiAccent.current
                                                        ),
                                                        border = ButtonDefaults.outlinedButtonBorder
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Login,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = "Giriş Yap",
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                })
                                                else -> null
                                            }
                                        )
                                    }
                                }
                            }


                            if (viewModel.selectedPlatform == ExplorePlatform.ALL) {
                                allSourcesExploreSections(
                                    states = viewModel.allSourceStates,
                                    showAdultContent = showAdultContent,
                                    collapsedSources = ExplorePlatform.sources.filter { it.name in collapsedSourceNames }.toSet(),
                                    onToggleSource = { source ->
                                        collapsedSourceNames = if (source.name in collapsedSourceNames) collapsedSourceNames - source.name
                                            else collapsedSourceNames + source.name
                                    },
                                    startIndex = 2 + (if (viewModel.isShowingCachedData) 1 else 0) + (if (viewModel.errorMessage != null) 1 else 0),
                                    onJumpToIndex = { index -> allSourcesScope.launch {
                                        lazyListState.animateScrollToItem(index, stickyToggleHeight)
                                    } },
                                    onRetrySource = viewModel::retrySource,
                                    alreadyInList = isAlreadyInList,
                                    getMediaEntry = getMediaEntry,
                                    onItemClick = onOpenApiDetail,
                                    onLongClickItem = onLongClickItem,
                                    onSeeAllSection = onSeeAllSection,
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    // Telefon: yapışkan kaynak çubuğu kaldırıldı —
                                    // yukarı çıkma için yüzen FAB kullanılıyor (alt barla uyumlu).
                                    showSourceJumpBar = false
                                )
                            } else if (isCatalogEmpty) {
                                item {
                                    KitsugiEmptyState(
                                        title = "Gösterilecek İçerik Yok",
                                        subtitle = "Seçilen platformda görüntülenebilecek medya bulunamadı."
                                    )
                                }
                            } else if (viewModel.selectedPlatform == ExplorePlatform.TMDB || viewModel.selectedPlatform == ExplorePlatform.SIMKL) {
                                item {
                                    TmdbCategoriesSection(
                                        isExpanded = isCategoriesExpanded,
                                        onExpandedChange = { isCategoriesExpanded = it },
                                        accentColor = accentColor,
                                        filteredTopAnime = filteredTopAnime,
                                        filteredAiringAnime = filteredAiringAnime,
                                        filteredMovieAnime = filteredMovieAnime,
                                        filteredTopManga = filteredTopManga,
                                        filteredUpcomingAnime = filteredUpcomingAnime,
                                        filteredSeasonalAnime = filteredSeasonalAnime,
                                        filteredPublishingManga = filteredPublishingManga,
                                        filteredTrendingAnime = filteredTrendingAnime,
                                        filteredNewlyAddedAnime = filteredNewlyAddedAnime,
                                        filteredTrendingManga = filteredTrendingManga,
                                        filteredUpcomingMediaTmdb = filteredUpcomingMediaTmdb,
                                        onSeeAllSection = onSeeAllForSelected,
                                        onOpenAiringCalendar = onOpenAiringCalendar
                                    )
                                }
                                if (filteredAiringSoonAnime.isNotEmpty() || viewModel.isLoading) {
                                    item {
                                        ExploreAiringSoonSection(
                                            title = stringResource(R.string.explore_airing_soon),
                                            airingSoonAnime = filteredAiringSoonAnime,
                                            isLoading = viewModel.isLoading,
                                            alreadyInList = isAlreadyInList,
                                            onItemClick = onOpenApiDetail,
                                            onLongClickItem = onLongClickItem,
                                            onOpenAiringCalendar = {
                                                onSeeAllForSelected(context.getString(R.string.explore_airing_soon), ExploreCategoryType.UPCOMING_MEDIA_TMDB, filteredUpcomingMediaTmdb)
                                            },
                                            accentColor = accentColor,
                                            titleLanguage = titleLanguage,
                                            blurAdultMedia = blurAdultMedia
                                        )
                                        Spacer(modifier = Modifier.height(26.dp))
                                    }
                                }
                                tmdbExploreSections(
                                    viewModel = viewModel,
                                    filteredTrendingAnime = filteredTrendingAnime,
                                    filteredNewlyAddedAnime = filteredNewlyAddedAnime,
                                    filteredTrendingManga = filteredTrendingManga,
                                    filteredTopAnime = filteredTopAnime,
                                    filteredAiringAnime = filteredAiringAnime,
                                    filteredMovieAnime = filteredMovieAnime,
                                    filteredTopManga = filteredTopManga,
                                    filteredUpcomingAnime = filteredUpcomingAnime,
                                    filteredPublishingManga = filteredPublishingManga,
                                    filteredSeasonalAnime = filteredSeasonalAnime,
                                    isAlreadyInList = isAlreadyInList,
                                    getMediaEntry = getMediaEntry,
                                    onItemClick = onOpenApiDetail,
                                    onLongClickItem = onLongClickItem,
                                    onSeeAllSection = onSeeAllForSelected,
                                    onNavigateToWatchHistory = onNavigateToWatchHistory,
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    context = context
                                )
                            } else {
                                item {
                                    DefaultCategoriesSection(
                                        isExpanded = isCategoriesExpanded,
                                        onExpandedChange = { isCategoriesExpanded = it },
                                        accentColor = accentColor,
                                        filteredSeasonalAnime = filteredSeasonalAnime,
                                        filteredTopAnime = filteredTopAnime,
                                        filteredTrendingAnime = filteredTrendingAnime,
                                        filteredMovieAnime = filteredMovieAnime,
                                        filteredNewlyAddedAnime = filteredNewlyAddedAnime,
                                        filteredTopManga = filteredTopManga,
                                        filteredPublishingManga = filteredPublishingManga,
                                        filteredTrendingManga = filteredTrendingManga,
                                        filteredNewlyAddedManga = filteredNewlyAddedManga,
                                        onSeeAllSection = onSeeAllForSelected,
                                        onOpenAiringCalendar = onOpenAiringCalendar,
                                        onOpenMangaReader = onOpenMangaReader
                                    )
                                    Spacer(modifier = Modifier.height(26.dp))
                                }
                                if (filteredAiringSoonAnime.isNotEmpty()) {
                                    item {
                                        ExploreAiringSoonSection(
                                            title = stringResource(R.string.explore_airing_soon),
                                            airingSoonAnime = filteredAiringSoonAnime,
                                            isLoading = viewModel.isLoading,
                                            alreadyInList = isAlreadyInList,
                                            onItemClick = onOpenApiDetail,
                                            onLongClickItem = onLongClickItem,
                                            onOpenAiringCalendar = onOpenAiringCalendar,
                                            accentColor = accentColor,
                                            titleLanguage = titleLanguage,
                                            blurAdultMedia = blurAdultMedia
                                        )
                                        Spacer(modifier = Modifier.height(26.dp))
                                    }
                                }
                                defaultExploreSections(
                                    viewModel = viewModel,
                                    filteredTopAnime = filteredTopAnime,
                                    filteredAiringAnime = filteredAiringAnime,
                                    filteredUpcomingAnime = filteredUpcomingAnime,
                                    filteredNewlyAddedAnime = filteredNewlyAddedAnime,
                                    filteredTopManga = filteredTopManga,
                                    filteredPublishingManga = filteredPublishingManga,
                                    filteredTrendingManga = filteredTrendingManga,
                                    filteredNewlyAddedManga = filteredNewlyAddedManga,
                                    isAlreadyInList = isAlreadyInList,
                                    getMediaEntry = getMediaEntry,
                                    onItemClick = onOpenApiDetail,
                                    onLongClickItem = onLongClickItem,
                                    onSeeAllSection = onSeeAllForSelected,
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    context = context
                                )
                            }
                        }
                    }
                }
            }

            // ── "Yukarı Çık" FAB — aşağı kaydırınca belirir, alt bar üstünde hizalanır ──
            androidx.compose.animation.AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = fabBottomPadding)
            ) {
                FloatingActionButton(
                    onClick = {
                        onScrollReset?.invoke()
                        scope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                    containerColor = accentColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Yukarı Çık")
                }
            }
        }
    }

    val rankingSheet = activeRankingSheetData
    if (rankingSheet != null) {
        com.kitsugi.animelist.ui.components.KitsugiRankingBottomSheet(
            title = rankingSheet.first,
            mediaType = rankingSheet.second,
            platform = viewModel.selectedPlatform,
            initialResults = rankingSheet.third,
            alreadyInList = isAlreadyInList,
            onItemClick = onOpenApiDetail,
            onDismissRequest = { activeRankingSheetData = null },
            titleLanguage = titleLanguage,
            hideScores = hideScores,
            showAdultContent = showAdultContent,
            blurAdultMedia = blurAdultMedia,
            getMediaEntry = getMediaEntry
        )
    }

    if (showSourceSheet) {
        ExploreSourcePickerSheet(
            selectedPlatform = viewModel.selectedPlatform,
            onSelectPlatform = { platform -> viewModel.selectPlatform(platform) },
            onDismiss = { showSourceSheet = false }
        )
    }
}

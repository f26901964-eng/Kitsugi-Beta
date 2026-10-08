@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kitsugi.animelist.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiShimmerSearchResultList
import com.kitsugi.animelist.ui.components.KitsugiShimmerMediaRow
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.components.KitsugiCosmicSearchBar
import com.kitsugi.animelist.ui.components.CosmicCompanionButton
import com.kitsugi.animelist.ui.screens.search.components.AddonExploreDialog
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchCountryChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchDateChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchEpChDurationChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchFormatChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchSourceChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchSortChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiSearchStatusChip
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiTriFilterChip
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import com.kitsugi.animelist.ui.components.KitsugiShimmerMediaRow
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    currentEntries: List<MediaEntry>,
    showAdultContent: Boolean,
    onOpenApiDetail: (JikanSearchResult) -> Unit,
    onAddSelectionToList: (ApiSearchSelection) -> Unit,
    viewModel: SearchViewModel = viewModel(),
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    onSeeAllAddonSection: ((apiName: String, title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<com.lagradost.cloudstream3.SearchResponse>) -> Unit)? = null,
    // Eklenti Keşfet dialogı state'i — AnimatedContent geçişlerinde
    // yerel remember sıfırlanmaması için dışarıdan yönetilir (navState)
    addonExploreOpen: Boolean = false,
    onAddonExploreOpenChange: (Boolean) -> Unit = {},
    // Eklenti Portalı tam ekran navigasyonu
    onOpenPluginPicker: () -> Unit = {},
    // Eklenti Keşfet tam ekran navigasyonu
    onOpenAddonExplore: (String) -> Unit = {},
    // AniHyou Karakter ve Personel detay yönlendirmeleri
    onOpenCharacterDetail: ((characterId: Int, name: String?, imageUrl: String?) -> Unit)? = null,
    onOpenStaffDetail: ((staffId: Int, name: String?, imageUrl: String?) -> Unit)? = null,
    isBottomBarVisible: Boolean = true,
    onScrollReset: (() -> Unit)? = null,
    // "Tümünü Gör" → kaynağa özel tam arama sayfasını ayrı ekranda açar
    onOpenSourceSearch: (SearchSourceEngine, SearchScope, List<JikanSearchResult>) -> Unit = { _, _, _ -> },
    // Alt sayfa olarak kullanıldığında büyük başlığı gizlemek için null verilebilir
    pageTitle: String? = "Arama"
) {
    val uiState by viewModel.uiState.collectAsState()
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val lazyListState = com.kitsugi.animelist.ui.utils.rememberRetainedLazyListState(
        contentReady = uiState.results.isNotEmpty() || !uiState.multiResults.isEmpty || !uiState.hasSearched
    )
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
        label = "search_fab_bottom_padding"
    )

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (firstIndex, scrollOffset) ->
                if (firstIndex == 0 && scrollOffset == 0) {
                    onScrollReset?.invoke()
                }
            }
    }

    // AniHyou Infinite Scroll (OnBottomReached)
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = lazyListState.layoutInfo.totalItemsCount
            val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3 && !uiState.isLoading && !uiState.isLoadingMore && uiState.hasNextPage
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    // Active Dialog States
    var openPlatformDialog by remember { mutableStateOf(false) }
    var openTmdbFormatDialog by remember { mutableStateOf(false) }
    var openTmdbGenreDialog by remember { mutableStateOf(false) }
    var openAddonSearchDialog by rememberSaveable { mutableStateOf(false) }
    var activeDetailCs3Item by remember { mutableStateOf<JikanSearchResult?>(null) }

    // Seçili eklentinin keşfet dialogunu aç/kapat
    // showPluginExploreDialog navState'ten gelir — AnimatedContent geçişlerinde sıfırlanmaz
    val selectedPluginApiName = uiState.selectedPluginApiName
    val selectedPluginApi = remember(selectedPluginApiName) {
        if (selectedPluginApiName == null) null
        else APIHolder.allProviders.firstOrNull { it.name == selectedPluginApiName }
    }

    val entryMap = remember(currentEntries) {
        val mapping = mutableMapOf<String, MediaEntry>()
        currentEntries.forEach { entry ->
            mapping["${entry.source.lowercase()}_${entry.type.name.lowercase()}_${entry.malId}"] = entry
            mapping["${entry.source.lowercase()}_${entry.malId}"] = entry
            if (entry.tmdbId != null) {
                mapping["tmdb_${entry.tmdbId}"] = entry
            }
            if (entry.simklId != null) {
                mapping["simkl_${entry.simklId}"] = entry
            }
            if (entry.source.equals("anilist", ignoreCase = true) && entry.malId != null && entry.malId >= 100_000_000) {
                mapping["anilist_${entry.malId - 100_000_000}"] = entry
            }
            if (entry.source.equals("jikan", ignoreCase = true) || entry.source.equals("mal", ignoreCase = true)) {
                mapping["mal_${entry.malId}"] = entry
                mapping["jikan_${entry.malId}"] = entry
            }
            val normTitle = entry.title.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.trim()
            if (normTitle.isNotEmpty()) {
                mapping["${entry.type.name.lowercase()}_$normTitle"] = entry
            }
        }
        mapping
    }

    val getMediaEntry = remember(entryMap) {
        { result: JikanSearchResult ->
            val compositeKey = "${result.source.lowercase()}_${result.type.name.lowercase()}_${result.malId}"
            val directKey = "${result.source.lowercase()}_${result.malId}"
            var found = entryMap[compositeKey] ?: entryMap[directKey]

            if (found == null) {
                val tmdbId = result.tmdbId ?: if (result.source.equals("tmdb", ignoreCase = true)) result.malId else null
                if (tmdbId != null) {
                    found = entryMap["tmdb_$tmdbId"]
                }
            }

            if (found == null) {
                val rMal = if (result.source.equals("jikan", ignoreCase = true) || result.source.equals("mal", ignoreCase = true)) {
                    result.malId
                } else {
                    result.realMalId
                }
                if (rMal != null) {
                    found = entryMap["${result.source.lowercase()}_$rMal"]
                        ?: entryMap["mal_$rMal"]
                        ?: entryMap["jikan_$rMal"]
                        ?: entryMap["anilist_$rMal"]
                        ?: entryMap["simkl_$rMal"]
                }
            }

            if (found == null) {
                val normTitle = buildString {
                    for (c in result.title.lowercase()) {
                        if (c in 'a'..'z' || c in '0'..'9') append(c)
                    }
                }.trim()
                if (normTitle.isNotEmpty()) {
                    found = entryMap["${result.type.name.lowercase()}_$normTitle"]
                }
            }

            found
        }
    }

    val isAlreadyInList = remember(getMediaEntry) {
        { result: JikanSearchResult ->
            getMediaEntry(result) != null
        }
    }

    val showIdleContent = !uiState.hasSearched && !uiState.isLoading
    val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 1 } }

    val isTmdbPlatform = uiState.selectedPlatform == SearchPlatform.TMDB
    val currentMediaType = uiState.selectedMediaType

    var showEnginePickerSheet by remember { mutableStateOf(false) }
    var showSourceEngineFilterSheet by remember { mutableStateOf(false) }

    val animeFormats = listOf("TV", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")
    val mangaFormats = listOf("MANGA", "NOVEL", "ONE_SHOT", "DOUJIN", "MANHWA", "MANHUA")
    val formats = if (currentMediaType == MediaType.Manga) mangaFormats else animeFormats

    val animeStatuses = listOf("AIRING" to "Yayında", "FINISHED" to "Tamamlandı", "UPCOMING" to "Yakında")
    val mangaStatuses = listOf("PUBLISHING" to "Yayınlanıyor", "FINISHED" to "Tamamlandı", "HIATUS" to "Ara Verildi", "DISCONTINUED" to "Durduruldu")
    val statuses = if (currentMediaType == MediaType.Manga) mangaStatuses else animeStatuses

    val sortOptions = listOf(
        "POPULARITY_DESC" to "🔥 Popülerlik",
        "SCORE_DESC" to "⭐ Puan",
        "TITLE_ROMAJI_ASC" to "🔤 İsim (A-Z)",
        "TITLE_ROMAJI_DESC" to "🔤 İsim (Z-A)"
    )

    val tmdbGenres = listOf(
        "Tümü", "Aksiyon", "Macera", "Komedi", "Dram", "Fantastik",
        "Korku", "Gizem", "Romantizm", "Sci-Fi", "Gerilim", "Müzik", "Tarihi", "Animasyon"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .then(if (isTv) Modifier.dpadVerticalFastScroll(lazyListState) else Modifier)
        ) {
            // Title & Search Input Section
            item {
                if (pageTitle != null) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = pageTitle,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val placeholder = when (uiState.selectedEngine) {
                    SearchSourceEngine.ALL -> "Tüm platformlarda ara (7 motor eşzamanlı)..."
                    SearchSourceEngine.ANILIST -> "AniList'te ${uiState.selectedScope.label.lowercase()} ara..."
                    SearchSourceEngine.MAL -> "MyAnimeList'te ${uiState.selectedScope.label.lowercase()} ara..."
                    SearchSourceEngine.TMDB -> "TMDB'de ${uiState.selectedScope.label.lowercase()} ara..."
                    SearchSourceEngine.SHIKIMORI -> "Shikimori'de ${uiState.selectedScope.label.lowercase()} ara..."
                    SearchSourceEngine.KITSU -> "Kitsu'da ${uiState.selectedScope.label.lowercase()} ara..."
                    SearchSourceEngine.SIMKL -> "Simkl'de ${uiState.selectedScope.label.lowercase()} ara..."
                    SearchSourceEngine.BANGUMI -> "Bangumi'de ${uiState.selectedScope.label.lowercase()} ara..."
                }

                // Uiverse Lakshay-art Cosmic Search Bar & Companion Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KitsugiCosmicSearchBar(
                        query = uiState.query,
                        onQueryChange = viewModel::setQuery,
                        onSearch = {
                            viewModel.search()
                            keyboardController?.hide()
                        },
                        onClearQuery = {
                            viewModel.clearQuery()
                            keyboardController?.hide()
                        },
                        placeholder = placeholder,
                        leadingContent = {
                            SourceEngineSelectorPill(
                                selectedEngine = uiState.selectedEngine,
                                onClick = { showEnginePickerSheet = true }
                            )
                        },
                        onFilterClick = {
                            showSourceEngineFilterSheet = true
                        },
                        isFilterActive = uiState.hasFiltersApplied,
                        modifier = Modifier.weight(1f)
                    )

                    // Eklenti Portalı butonu (Uiverse cosmic companion button)
                    CosmicCompanionButton(
                        onClick = { onOpenPluginPicker() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "Eklenti Portalı",
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Kompakt Arama Seçenekleri Özeti ─────────────────────────────
            // Kategori (kapsam), sıralama ve filtre kontrollerinin tamamı arama
            // çubuğunun sağındaki butonda (SourceEngineFilterSheet) toplandı;
            // ana sayfa dağınık bırakılmaz. Bu satır yalnızca varsayılandan
            // farklı bir ayar aktifse görünür ve aktif durumu özetler.
            item {
                SearchOptionsSummaryRow(
                    uiState = uiState,
                    onClick = { showSourceEngineFilterSheet = true }
                )
            }


            // Active Filters Inline Dismissible Chips Row
            // (Active filter pills removed – chips themselves show active state)

            // Search History Section
            if (showIdleContent && uiState.searchHistory.isNotEmpty()) {
                item {
                    SearchHistorySection(
                        history = uiState.searchHistory,
                        onHistoryItemClick = viewModel::applyHistoryItem,
                        onRemoveItem = viewModel::removeHistoryItem,
                        onClearAll = viewModel::clearHistory
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Shimmer Loading State
            if (uiState.isLoading) {
                if (uiState.currentTab == KitsugiSearchTab.All) {
                    item {
                        MultiSearchShelfShimmer(
                            title = "AniList",
                            platformId = "anilist"
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "MyAnimeList",
                            platformId = "mal"
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "TMDB (Film & Dizi)",
                            platformId = "tmdb"
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "Shikimori",
                            platformId = "shikimori"
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "Bangumi",
                            platformId = "bangumi"
                        )
                    }
                } else {
                    item {
                        KitsugiShimmerSearchResultList(itemCount = 4)
                    }
                }
            }

            // Empty / Error State
            if (!uiState.isLoading && uiState.hasSearched && uiState.errorMessage != null) {
                item {
                    KitsugiEmptyState(
                        title = "Sonuç bulunamadı",
                        subtitle = uiState.errorMessage,
                        icon = Icons.Rounded.SearchOff
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Search Results List (AniHyou-style filtered by adult and onMyList)
            val filteredResults = uiState.results
                .filter { showAdultContent || !it.isAdult }
                .filter { result ->
                    when (uiState.onMyList) {
                        true -> isAlreadyInList(result)
                        false -> !isAlreadyInList(result)
                        null -> true
                    }
                }

            if (uiState.currentTab == KitsugiSearchTab.All) {
                // Çoklu Platform Rafları (Seçenek C: All-in-One Multi Platform Search)
                item {
                    MultiSearchSection(
                        title = "AniList",
                        platformId = "anilist",
                        results = uiState.multiResults.aniListResults,
                        isLoading = uiState.multiResults.isLoadingAniList,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.ANILIST, uiState.selectedScope, uiState.multiResults.aniListResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "MyAnimeList",
                        platformId = "mal",
                        results = uiState.multiResults.malResults,
                        isLoading = uiState.multiResults.isLoadingMal,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.MAL, uiState.selectedScope, uiState.multiResults.malResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Film & Dizi (TMDB)",
                        platformId = "tmdb",
                        results = uiState.multiResults.tmdbResults,
                        isLoading = uiState.multiResults.isLoadingTmdb,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.TMDB, uiState.selectedScope, uiState.multiResults.tmdbResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Shikimori (Rusça Kaynak / Anime & Manga)",
                        platformId = "shikimori",
                        results = uiState.multiResults.shikimoriResults,
                        isLoading = uiState.multiResults.isLoadingShikimori,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.SHIKIMORI, uiState.selectedScope, uiState.multiResults.shikimoriResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Kitsu",
                        platformId = "kitsu",
                        results = uiState.multiResults.kitsuResults,
                        isLoading = uiState.multiResults.isLoadingKitsu,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.KITSU, uiState.selectedScope, uiState.multiResults.kitsuResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Simkl",
                        platformId = "simkl",
                        results = uiState.multiResults.simklResults,
                        isLoading = uiState.multiResults.isLoadingSimkl,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.SIMKL, uiState.selectedScope, uiState.multiResults.simklResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Bangumi (Çin Kaynağı / Anime & Kitap)",
                        platformId = "bangumi",
                        results = uiState.multiResults.bangumiResults,
                        isLoading = uiState.multiResults.isLoadingBangumi,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { onOpenSourceSearch(SearchSourceEngine.BANGUMI, uiState.selectedScope, uiState.multiResults.bangumiResults) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }
            } else if (uiState.currentTab == KitsugiSearchTab.Character || uiState.currentTab == KitsugiSearchTab.Staff) {
                items(filteredResults, key = { "${it.source}_${it.type}_${it.malId}" }) { result ->
                    val displayName = com.kitsugi.animelist.data.remote.displayPersonName(result.title, result.titleEnglish, result.titleJapanese, titleLanguage)
                    CharacterStaffResultRow(
                        result = result.copy(
                            title = displayName,
                            subtitle = if (titleLanguage in listOf("NATIVE", "JAPANESE_STAFF")) {
                                when {
                                    result.subtitle == displayName -> "Karakter"
                                    result.subtitle.startsWith("$displayName • ") -> result.subtitle.removePrefix("$displayName • ")
                                    else -> result.subtitle
                                }
                            } else result.subtitle
                        ),
                        isStaff = uiState.currentTab == KitsugiSearchTab.Staff,
                        onClick = {
                            if (uiState.currentTab == KitsugiSearchTab.Staff) {
                                onOpenStaffDetail?.invoke(result.malId, displayName, result.imageUrl)
                            } else {
                                onOpenCharacterDetail?.invoke(result.malId, displayName, result.imageUrl)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                items(filteredResults, key = { "${it.source}_${it.type}_${it.malId}" }) { result ->
                    SearchResultRow(
                        result = result,
                        alreadyInList = isAlreadyInList(result),
                        mediaEntry = getMediaEntry(result),
                        onItemClick = {
                            if (result.source == "cs3") {
                                activeDetailCs3Item = result
                            } else {
                                onOpenApiDetail(result)
                            }
                        },
                        onAddClick = {
                            onAddSelectionToList(ApiSearchSelection(result = result, synopsis = null))
                        },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Infinite scroll loading spinner
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KitsugiPlasmaLoader(
                            size = 32.dp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(90.dp)) }
        }

        // Scroll to Top FAB
        AnimatedVisibility(
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

    if (showEnginePickerSheet) {
        SourceEnginePickerSheet(
            selectedEngine = uiState.selectedEngine,
            onSelectEngine = {
                viewModel.setEngine(it)
                showEnginePickerSheet = false
            },
            onDismiss = { showEnginePickerSheet = false }
        )
    }

    if (showSourceEngineFilterSheet) {
        SourceEngineFilterSheet(
            uiState = uiState,
            viewModel = viewModel,
            onOpenGenresTags = {
                showSourceEngineFilterSheet = false
                viewModel.setFilterSheetOpen(true)
            },
            onDismiss = { showSourceEngineFilterSheet = false }
        )
    }

    // Genres Bottom Sheet Dialog
    if (uiState.isFilterSheetOpen) {
        GenresTagsSheet(
            currentFilters = SearchFilters(
                genres = uiState.genres,
                excludedGenres = uiState.excludedGenres,
                tags = uiState.tags
            ),
            onApplyFilters = {
                viewModel.updateFilters(it)
                viewModel.setFilterSheetOpen(false)
            },
            onDismiss = { viewModel.setFilterSheetOpen(false) }
        )
    }

    // Dialog: Platform selection
    if (openPlatformDialog) {
        val platforms = listOf(
            SearchPlatform.All,
            SearchPlatform.AniList,
            SearchPlatform.MAL,
            SearchPlatform.TMDB,
            SearchPlatform.Shikimori,
            SearchPlatform.Kitsu,
            SearchPlatform.Simkl,
            SearchPlatform.Bangumi
        )
        DialogWithRadioSelection(
            title = "Kaynak Platform Seç",
            options = platforms,
            selectedOption = uiState.selectedPlatform,
            onOptionSelected = { plat ->
                if (plat != null) viewModel.setPlatform(plat)
            },
            onDismiss = { openPlatformDialog = false },
            optionLabel = { it.label }
        )
    }



    // Dialog: TMDB Format Selection
    if (openTmdbFormatDialog) {
        val tmdbTypes = listOf(MediaType.Movie, MediaType.TvShow)
        DialogWithRadioSelection(
            title = "Tip Seç",
            options = tmdbTypes,
            selectedOption = currentMediaType,
            onOptionSelected = { mt ->
                if (mt != null) viewModel.setMediaType(mt)
            },
            onDismiss = { openTmdbFormatDialog = false },
            optionLabel = { if (it == MediaType.Movie) "Film" else "Dizi" }
        )
    }

    // Dialog: TMDB Genre Selection
    if (openTmdbGenreDialog) {
        val currentGenre = uiState.genres.firstOrNull() ?: "Tümü"
        DialogWithRadioSelection(
            title = "Tür Seç",
            options = tmdbGenres,
            selectedOption = currentGenre,
            onOptionSelected = { g ->
                val updated = SearchFilters(
                    genres = if (g == null || g == "Tümü") emptyList() else listOf(g)
                )
                viewModel.updateFilters(updated)
            },
            onDismiss = { openTmdbGenreDialog = false }
        )
    }

    if (openAddonSearchDialog) {
        com.kitsugi.animelist.ui.screens.search.components.AddonSearchDialog(
            onDismissRequest = { openAddonSearchDialog = false },
            onSeeAllAddonSection = onSeeAllAddonSection
        )
    }

    // Plugin picker is now handled via full-screen navigation (PluginPickerScreen)

    // ── Eklentiye Özel Keşfet (tam ekran sayfa navigasyonu) ──────────────
    val exploreApi = selectedPluginApi
    LaunchedEffect(addonExploreOpen, exploreApi?.name) {
        if (addonExploreOpen && exploreApi != null) {
            onAddonExploreOpenChange(false)
            onOpenAddonExplore(exploreApi.name)
        }
    }

    // ── KitsugiAddonDetailDialog ──────────────────────────────────────────
    activeDetailCs3Item?.let { result ->
        val api = remember(result.cs3ApiName) {
            APIHolder.allProviders.firstOrNull { it.name.equals(result.cs3ApiName, ignoreCase = true) }
        }
        if (api != null && !result.cs3Url.isNullOrBlank()) {
            com.kitsugi.animelist.ui.screens.search.components.KitsugiAddonDetailDialog(
                api = api,
                url = result.cs3Url,
                onDismissRequest = { activeDetailCs3Item = null }
            )
        }
    }
}

@Composable
fun SearchTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accentColor else KitsugiColors.Surface)
            .border(1.dp, if (selected) Color.Transparent else KitsugiColors.Border, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SearchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) accentColor.copy(alpha = 0.5f) else KitsugiColors.Border, shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Kompakt Arama Seçenekleri Özeti ──────────────────────────────────────────

/**
 * Arama sayfasını dağınık göstermemek için kategori/sıralama/filtre
 * kontrolleri sağ üstteki butona (SourceEngineFilterSheet) taşındı.
 * Bu çip yalnızca varsayılandan farklı bir ayar aktifken görünür;
 * aktif kapsam + sıralama + filtre adedini tek satırda özetler ve
 * dokununca birleşik seçenekler sheet'ini açar.
 */
@Composable
private fun SearchOptionsSummaryRow(
    uiState: SearchUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    val engine = uiState.selectedEngine

    val defaultScope = engine.availableScopes().first()
    val scopeChanged = uiState.selectedScope != defaultScope
    val sortChanged = getCurrentSortKey(uiState) != defaultSortKeyForEngine(engine)
    val activeFilterCount = uiState.activeFilterCount

    if (!scopeChanged && !sortChanged && activeFilterCount == 0) return

    val parts = buildList {
        if (scopeChanged) add(uiState.selectedScope.displayLabel)
        if (sortChanged) add(getSortDisplayLabel(uiState))
        if (activeFilterCount > 0) add("🎛️ $activeFilterCount filtre")
    }

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(accentColor.copy(alpha = 0.10f))
                .border(1.dp, accentColor.copy(alpha = 0.45f), shape)
                .tvClickable(shape = shape, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🎛️ ${engine.shortLabel} • ${parts.joinToString(" • ")}",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "Düzenle",
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}

// ─── Active Filters Chips Row ─────────────────────────────────────────────────

@Composable
fun ActiveFiltersChipsRow(
    filters: SearchFilters,
    onRemoveFilter: (filterType: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filters.format != null) {
            ActiveFilterChip(label = "Format: ${filters.format}", onCloseClick = { onRemoveFilter("format", "") })
        }
        if (filters.status != null) {
            val lbl = when (filters.status) {
                "AIRING" -> "Yayında"; "FINISHED" -> "Tamamlandı"; "UPCOMING" -> "Yakında"
                "PUBLISHING" -> "Yayınlanıyor"; "HIATUS" -> "Ara"; "DISCONTINUED" -> "Durduruldu"
                else -> filters.status
            }
            ActiveFilterChip(label = "Durum: $lbl", onCloseClick = { onRemoveFilter("status", "") })
        }
        if (filters.season != null) {
            val lbl = when (filters.season) {
                "WINTER" -> "Kış"; "SPRING" -> "İlkbahar"; "SUMMER" -> "Yaz"; "FALL" -> "Sonbahar"
                else -> filters.season
            }
            ActiveFilterChip(label = "Sezon: $lbl", onCloseClick = { onRemoveFilter("season", "") })
        }
        filters.genres.forEach { g ->
            val tr = SearchTranslation.translateToTurkishForDisplay(g)
            ActiveFilterChip(label = "+ $tr", onCloseClick = { onRemoveFilter("genre", g) }, borderColor = Color(0xFF10B981))
        }
        filters.excludedGenres.forEach { g ->
            val tr = SearchTranslation.translateToTurkishForDisplay(g)
            ActiveFilterChip(label = "- $tr", onCloseClick = { onRemoveFilter("excludedGenre", g) }, borderColor = Color(0xFFEF4444))
        }
        filters.tags.forEach { t ->
            val tr = SearchTranslation.translateToTurkishForDisplay(t)
            ActiveFilterChip(label = "# $tr", onCloseClick = { onRemoveFilter("tag", t) })
        }
        if (filters.minYear != null || filters.maxYear != null) {
            ActiveFilterChip(label = "Yıl: ${filters.minYear ?: 1970}-${filters.maxYear ?: 2026}", onCloseClick = { onRemoveFilter("year", "") })
        }
        if (filters.minScore != null || filters.maxScore != null) {
            ActiveFilterChip(label = "Puan: %${filters.minScore ?: 0}-%${filters.maxScore ?: 100}", onCloseClick = { onRemoveFilter("score", "") })
        }
        if (filters.sort != null && filters.sort != "POPULARITY_DESC") {
            val lbl = when (filters.sort) {
                "SCORE_DESC" -> "Puan"; "TITLE_ROMAJI_ASC" -> "A-Z"; "TITLE_ROMAJI_DESC" -> "Z-A"; else -> filters.sort
            }
            ActiveFilterChip(label = "Sıra: $lbl", onCloseClick = { onRemoveFilter("sort", "") })
        }
    }
}

@Composable
fun ActiveFilterChip(
    label: String,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = LocalKitsugiAccent.current
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.Surface.copy(alpha = 0.6f))
            .border(1.dp, borderColor.copy(alpha = 0.5f), shape)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = KitsugiColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .tvClickable(shape = RoundedCornerShape(8.dp), onClick = onCloseClick)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Temizle",
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MultiSearchShelfShimmer(
    title: String,
    platformId: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Platform Logosu + Başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KitsugiPlatformLogo(
                platformId = platformId,
                size = 24.dp,
                cornerRadius = 6.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Yatay kartlar için animasyonlu shimmer efekti
        KitsugiShimmerMediaRow(cardCount = 5)
    }
}

/**
 * All-in-One Çoklu Platform Arama Bölümü (Yatay Kart Listesi - Keşfet Sayfası ile Birebir Aynı Mekanik ve Görünüm)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MultiSearchSection(
    title: String,
    platformId: String,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry? = { null },
    onItemClick: (JikanSearchResult) -> Unit,
    onSeeAllClick: () -> Unit,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false
) {
    if (results.isEmpty() && !isLoading) return

    val accentColor = LocalKitsugiAccent.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTvDevice = LocalIsTvDevice.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Platform Logosu + Başlık & "Tümünü Gör" Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KitsugiPlatformLogo(
                platformId = platformId,
                size = 24.dp,
                cornerRadius = 6.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (results.isNotEmpty()) {
                TextButton(
                    onClick = onSeeAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Tümünü Gör",
                        color = accentColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isLoading && results.isEmpty() -> {
                // Keşfet sayfası ile birebir aynı animasyonlu shimmer efekti
                KitsugiShimmerMediaRow(cardCount = 5)
            }

            else -> {
                val lazyListState = androidx.compose.runtime.saveable.rememberSaveable(title, saver = LazyListState.Saver) { LazyListState() }
                val cardWidth = if (isLandscape) 260.dp else 180.dp

                if (isTvDevice) {
                    val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
                    val lastFocusedIndex = remember(results) { mutableStateOf(0) }
                    val focusRequesters = remember(results) { mutableMapOf<Int, FocusRequester>() }
                    val rowFocusRequester = remember { FocusRequester() }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
                        LazyRow(
                            state = lazyListState,
                            modifier = Modifier
                                .focusRequester(rowFocusRequester)
                                .focusRestorer {
                                    focusRequesters[lastFocusedIndex.value] ?: FocusRequester.Default
                                }
                                .focusGroup(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = results,
                                key = { index, result -> "${result.source}_${result.malId ?: result.tmdbId ?: 0}_$index" }
                            ) { index, result ->
                                val requester = focusRequesters.getOrPut(index) { FocusRequester() }
                                KitsugiExploreMediaCard(
                                    result = result,
                                    alreadyInList = isAlreadyInList(result),
                                    mediaEntry = getMediaEntry(result),
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .focusRequester(requester)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                lastFocusedIndex.value = index
                                            }
                                        },
                                    onClick = { onItemClick(result) },
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    forceVertical = false
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
                            lazyListState = lazyListState,
                            snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Start
                        )
                    ) {
                        itemsIndexed(
                            items = results,
                            key = { index, result -> "${result.source}_${result.malId ?: result.tmdbId ?: 0}_$index" }
                        ) { _, result ->
                            KitsugiExploreMediaCard(
                                result = result,
                                alreadyInList = isAlreadyInList(result),
                                mediaEntry = getMediaEntry(result),
                                modifier = Modifier.width(cardWidth),
                                onClick = { onItemClick(result) },
                                titleLanguage = titleLanguage,
                                scoreFormat = scoreFormat,
                                hideScores = hideScores,
                                blurAdultMedia = blurAdultMedia,
                                forceVertical = false
                            )
                        }
                    }
                }
            }
        }
    }
}


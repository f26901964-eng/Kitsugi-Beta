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
import com.kitsugi.animelist.ui.components.KitsugiShimmerSearchResultList
import com.kitsugi.animelist.ui.components.KitsugiShimmerMediaRow
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
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
    onScrollReset: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val lazyListState = rememberLazyListState()
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
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Arama",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(18.dp))

                // Premium Search Bar (inspired by AniHyou)
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .onFocusChanged { isFocused = it.hasFocus }
                            .border(
                                width = if (isFocused) 1.5.dp else 1.dp,
                                color = if (isFocused) accentColor else KitsugiColors.Border,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .background(KitsugiColors.Surface)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SourceEngineSelectorPill(
                                selectedEngine = uiState.selectedEngine,
                                onClick = { showEnginePickerSheet = true }
                            )

                            BasicTextField(
                                value = uiState.query,
                                onValueChange = viewModel::setQuery,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                cursorBrush = SolidColor(accentColor),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = KitsugiColors.TextPrimary,
                                    fontWeight = FontWeight.Normal
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        viewModel.search()
                                        keyboardController?.hide()
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (uiState.query.isEmpty()) {
                                        val placeholder = when (uiState.selectedEngine) {
                                            SearchSourceEngine.ALL -> "Tüm platformlarda ara (6 motor eşzamanlı)..."
                                            SearchSourceEngine.ANILIST -> "AniList'te ${uiState.selectedScope.label.lowercase()} ara..."
                                            SearchSourceEngine.MAL -> "MyAnimeList'te ${uiState.selectedScope.label.lowercase()} ara..."
                                            SearchSourceEngine.TMDB -> "TMDB'de ${uiState.selectedScope.label.lowercase()} ara..."
                                            SearchSourceEngine.SHIKIMORI -> "Shikimori'de ${uiState.selectedScope.label.lowercase()} ara..."
                                            SearchSourceEngine.KITSU -> "Kitsu'da ${uiState.selectedScope.label.lowercase()} ara..."
                                            SearchSourceEngine.SIMKL -> "Simkl'de ${uiState.selectedScope.label.lowercase()} ara..."
                                        }
                                        Text(
                                            text = placeholder,
                                            color = KitsugiColors.TextMuted,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            AnimatedVisibility(
                                visible = uiState.query.isNotEmpty(),
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.clearQuery()
                                        keyboardController?.hide()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Temizle",
                                        tint = KitsugiColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Eklenti Portalı butonu
                    IconButton(
                        onClick = { onOpenPluginPicker() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(KitsugiColors.Surface)
                            .border(
                                width = 1.dp,
                                color = KitsugiColors.Border,
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Eklenti Portalı",
                            tint = KitsugiColors.TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Seçili Kaynak Motoruna Özel Alt Kapsamlar (Scope Chips) ─────
            item {
                SourceScopeChipsRow(
                    selectedEngine = uiState.selectedEngine,
                    selectedScope = uiState.selectedScope,
                    onScopeSelected = { scope -> viewModel.setScope(scope) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Kaynağa ve Kapsama Özel Emojili Sıralama & Filtreleme Çubuğu ──
            item {
                SourceSpecificFilterChipsRow(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenFullFilterSheet = { showSourceEngineFilterSheet = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                            badgeText = "AL",
                            badgeColor = Color(0xFF02A9FF)
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "MyAnimeList",
                            badgeText = "MAL",
                            badgeColor = Color(0xFF2E51A2)
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "TMDB (Film & Dizi)",
                            badgeText = "TMDB",
                            badgeColor = Color(0xFFFFB800)
                        )
                    }
                    item {
                        MultiSearchShelfShimmer(
                            title = "Shikimori",
                            badgeText = "SHIKI",
                            badgeColor = Color(0xFF4C86C8)
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
                        badgeText = "AL",
                        badgeColor = Color(0xFF02A9FF),
                        results = uiState.multiResults.aniListResults,
                        isLoading = uiState.multiResults.isLoadingAniList,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { viewModel.setTab(KitsugiSearchTab.Anime) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "MyAnimeList",
                        badgeText = "MAL",
                        badgeColor = Color(0xFF2E51A2),
                        results = uiState.multiResults.malResults,
                        isLoading = uiState.multiResults.isLoadingMal,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { viewModel.setTab(KitsugiSearchTab.MAL) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Film & Dizi (TMDB)",
                        badgeText = "TMDB",
                        badgeColor = Color(0xFFFFB800),
                        results = uiState.multiResults.tmdbResults,
                        isLoading = uiState.multiResults.isLoadingTmdb,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { viewModel.setTab(KitsugiSearchTab.TMDB) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Shikimori (Rusça Kaynak / Anime & Manga)",
                        badgeText = "SHI",
                        badgeColor = Color(0xFF4C86C8),
                        results = uiState.multiResults.shikimoriResults,
                        isLoading = uiState.multiResults.isLoadingShikimori,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { viewModel.setTab(KitsugiSearchTab.Shikimori) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Kitsu",
                        badgeText = "KT",
                        badgeColor = Color(0xFFE35A02),
                        results = uiState.multiResults.kitsuResults,
                        isLoading = uiState.multiResults.isLoadingKitsu,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { viewModel.setTab(KitsugiSearchTab.Kitsu) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }

                item {
                    MultiSearchSection(
                        title = "Simkl",
                        badgeText = "SK",
                        badgeColor = Color(0xFF1F1F1F),
                        results = uiState.multiResults.simklResults,
                        isLoading = uiState.multiResults.isLoadingSimkl,
                        isAlreadyInList = isAlreadyInList,
                        getMediaEntry = getMediaEntry,
                        onItemClick = onOpenApiDetail,
                        onSeeAllClick = { viewModel.setTab(KitsugiSearchTab.Simkl) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores
                    )
                }
            } else if (uiState.currentTab == KitsugiSearchTab.Character || uiState.currentTab == KitsugiSearchTab.Staff) {
                items(filteredResults, key = { "${it.source}_${it.malId}" }) { result ->
                    CharacterStaffResultRow(
                        result = result,
                        isStaff = uiState.currentTab == KitsugiSearchTab.Staff,
                        onClick = {
                            if (uiState.currentTab == KitsugiSearchTab.Staff) {
                                onOpenStaffDetail?.invoke(result.malId, result.title, result.imageUrl)
                            } else {
                                onOpenCharacterDetail?.invoke(result.malId, result.title, result.imageUrl)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                items(filteredResults, key = { "${it.source}_${it.malId}" }) { result ->
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
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
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
            SearchPlatform.Simkl
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
    badgeText: String,
    badgeColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Platform Rozeti + Başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.9f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = if (badgeText.equals("TMDB", ignoreCase = true) || badgeColor == Color(0xFFFFB800)) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    badgeText: String,
    badgeColor: Color,
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
        // Platform Badge + Title & "Tümünü Gör" Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.9f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = if (badgeText.equals("TMDB", ignoreCase = true) || badgeColor == Color(0xFFFFB800)) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
                val lazyListState = remember(title) { LazyListState() }
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


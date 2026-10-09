@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import com.kitsugi.animelist.ui.components.KitsugiMotion
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.ui.screens.mylist.components.KitsugiListStatusBottomSheet
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.data.remote.matches
import com.kitsugi.animelist.ui.components.KitsugiApiSearchDialog
import com.kitsugi.animelist.ui.components.KitsugiConfirmDialog
import com.kitsugi.animelist.ui.components.KitsugiInfoDialog
import com.kitsugi.animelist.ui.components.KitsugiImagePreviewDialog
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryEditorDialog
import com.kitsugi.animelist.ui.components.KitsugiKitsuLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiBangumiLoginDialog
import com.kitsugi.animelist.ui.components.KitsugiShikimoriLoginDialog
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import kotlinx.coroutines.launch

@Composable
fun MyListScreen(
    selectedListLayoutId: String,
    onListLayoutChange: (String) -> Unit,
    showAdultContent: Boolean,
    appSettings: com.kitsugi.animelist.data.settings.AppSettings,
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    selectedTabIndex: Int,
    onTabIndexChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onFavoriteFilterChange: (String) -> Unit,
    onScoreFilterChange: (String) -> Unit,
    onYearFilterChange: (String) -> Unit,
    onExtraFilterChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit,
    onExternalSyncMessage: (String) -> Unit,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isSimklSessionExpired: Boolean,
    isKitsuConnected: Boolean = false,
    isShikimoriConnected: Boolean = false,
    isBangumiConnected: Boolean = false,
    onLoginAniList: () -> Unit,
    onLoginMal: () -> Unit,
    onLoginSimkl: () -> Unit,
    onLoginKitsu: () -> Unit = {},
    onLoginShikimori: () -> Unit = {},
    onLoginBangumi: () -> Unit = {},
    onSyncAniList: () -> Unit,
    onSyncMal: () -> Unit,
    onSyncSimkl: () -> Unit,
    onSyncKitsu: () -> Unit = {},
    onSyncShikimori: () -> Unit = {},
    onSyncBangumi: () -> Unit = {},
    onKitsuAuthSubmit: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onShikimoriAuthSubmit: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _ -> },
    onBangumiAuthSubmit: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _ -> },
    onEntryClick: (MediaEntry) -> Unit,
    onSettingsClick: () -> Unit,
    isBottomBarVisible: Boolean = true,
    onScrollReset: () -> Unit = {},
    isNotificationsVisible: Boolean = false,
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val KitsugiColors = LocalKitsugiColors.current

    val viewModel: MyListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // ViewModel artık init{} içinde hazır — sadece sync mesajlarını dinle
    LaunchedEffect(Unit) {
        viewModel.syncMessages.collect { message ->
            onExternalSyncMessage(message)
        }
    }

    val entries by viewModel.entriesFlow.collectAsState()

    // Save positions separately from LazyListState: an empty/loading list can clamp its
    // index to zero before the library finishes loading when returning from a detail page.
    var savedTabIndices by rememberSaveable {
        mutableStateOf(List(MY_LIST_TAB_COUNT) { if (it == selectedTabIndex) initialScrollIndex else 0 })
    }
    var savedTabOffsets by rememberSaveable {
        mutableStateOf(List(MY_LIST_TAB_COUNT) { if (it == selectedTabIndex) initialScrollOffset else 0 })
    }
    var restoredTabs by remember { mutableStateOf(emptySet<Int>()) }

    var isFabVisible by rememberSaveable { mutableStateOf(true) }
    // showHeader and showScrollToTop are driven by active tab scroll — updated in LaunchedEffect below
    var showHeader by remember { mutableStateOf(true) }
    var showScrollToTopState by remember { mutableStateOf(false) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }
    var showSearchField by rememberSaveable { mutableStateOf(false) }
    var showFilterPanel by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showStatusBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showApiSearchDialog by rememberSaveable { mutableStateOf(false) }
    var showKitsuLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showShikimoriLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showBangumiLoginDialog by rememberSaveable { mutableStateOf(false) }
    var activeZoomImageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var activeZoomTitle by rememberSaveable { mutableStateOf("") }
    var activeZoomIsAdult by rememberSaveable { mutableStateOf(false) }

    // Five provider tabs plus a combined, deduplicated library tab.
    val tabPagerState = rememberPagerState(
        initialPage = selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1),
        pageCount = { MY_LIST_TAB_COUNT }
    )

    // Pager page change -> notify parent
    LaunchedEffect(tabPagerState.settledPage) {
        if (tabPagerState.settledPage != selectedTabIndex) {
            onTabIndexChange(tabPagerState.settledPage)
        }
    }

    // Parent tab change (chip click) -> animate pager
    LaunchedEffect(selectedTabIndex) {
        if (tabPagerState.currentPage != selectedTabIndex) {
            tabPagerState.animateScrollToPage(selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1))
        }
    }

    // Scroll tracking is handled per-tab inside the HorizontalPager LaunchedEffect below.

    var duplicateMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var editingEntry by remember {
        mutableStateOf<MediaEntry?>(null)
    }

    var deletingEntry by remember {
        mutableStateOf<MediaEntry?>(null)
    }

    var showDetailedStats by rememberSaveable {
        mutableStateOf(false)
    }

    val entriesAfterAdultFilter = remember(entries, showAdultContent) {
        entries.filter { entry ->
            showAdultContent || !entry.isAdult
        }
    }

    val allLibraryItems = remember(entriesAfterAdultFilter) {
        groupMyListEntries(entriesAfterAdultFilter)
    }
    val allLibraryEntries = remember(allLibraryItems) {
        allLibraryItems.map { it.entry }
    }
    val allSourceBadgesByEntryId = remember(allLibraryItems) {
        allLibraryItems.associate { it.entry.id to it.sourceIds }
    }

    val selectedTabEntries = remember(
        entriesAfterAdultFilter,
        allLibraryEntries,
        selectedTabIndex,
        searchQuery
    ) {
        when {
            selectedTabIndex == MY_LIST_ALL_TAB_INDEX -> allLibraryEntries
            searchQuery.isNotBlank() -> entriesAfterAdultFilter
            else -> entriesAfterAdultFilter.filter { entry ->
                myListSourceMatchesTab(selectedTabIndex, entry.source)
            }
        }
    }

    val filteredEntries = remember(
        selectedTabEntries,
        searchQuery,
        selectedStatusFilterId,
        selectedTypeFilterId,
        selectedFavoriteFilterId,
        selectedScoreFilterId,
        selectedYearFilterId,
        selectedExtraFilterId
    ) {
        filterMyListEntries(
            entries = selectedTabEntries,
            searchQuery = searchQuery,
            selectedStatusFilterId = selectedStatusFilterId,
            selectedTypeFilterId = selectedTypeFilterId,
            selectedFavoriteFilterId = selectedFavoriteFilterId,
            selectedScoreFilterId = selectedScoreFilterId,
            selectedYearFilterId = selectedYearFilterId,
            selectedExtraFilterId = selectedExtraFilterId
        )
    }

    val visibleEntries = remember(filteredEntries, selectedSortId) {
        applySort(
            entries = filteredEntries,
            sortId = selectedSortId
        )
    }

    val groupedVisibleEntries = remember(visibleEntries) {
        groupMyListEntriesByStatus(visibleEntries)
    }

    val listStats = remember(selectedTabEntries) {
        ListStats.from(selectedTabEntries)
    }

    fun incrementEntryProgress(entry: MediaEntry) {
        viewModel.incrementEntryProgress(entry)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 12.dp else 20.dp

    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val accentColor = com.kitsugi.animelist.ui.theme.LocalKitsugiAccent.current


    // Per-tab scroll states — declared at top level so FAB can reference them
    val tabScrollStates = (0 until MY_LIST_TAB_COUNT).map { page ->
        androidx.compose.runtime.key(page) {
            rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
                androidx.compose.foundation.lazy.LazyListState(savedTabIndices[page], savedTabOffsets[page])
            }
        }
    }
    val activeTabScrollState = tabScrollStates[selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.background)
    ) {
        val hasActiveFilters = selectedStatusFilterId != "all" ||
                selectedTypeFilterId != "all" ||
                selectedFavoriteFilterId != "all" ||
                selectedScoreFilterId != "all" ||
                selectedYearFilterId != "all" ||
                selectedExtraFilterId != "all" ||
                (selectedSortId.isNotBlank() && selectedSortId != "newest" && selectedSortId != "added")

        // ── Sabit üst bar (başlık + arama + filtreleme) ──
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            color = KitsugiColors.background,
            shadowElevation = 0.dp
        ) {
            MyListHeaderSection(
                selectedListLayoutId = selectedListLayoutId,
                onListLayoutChange = onListLayoutChange,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                showSearchField = showSearchField,
                onSearchFieldToggle = { showSearchField = !showSearchField },
                showFilterPanel = showFilterPanel,
                onFilterPanelToggle = { showFilterPanel = !showFilterPanel },
                onHideFilters = { showFilterPanel = false },
                selectedStatusFilterId = selectedStatusFilterId,
                selectedTypeFilterId = selectedTypeFilterId,
                selectedFavoriteFilterId = selectedFavoriteFilterId,
                selectedScoreFilterId = selectedScoreFilterId,
                selectedYearFilterId = selectedYearFilterId,
                selectedExtraFilterId = selectedExtraFilterId,
                selectedSortId = selectedSortId,
                onStatusFilterChange = onStatusFilterChange,
                onTypeFilterChange = onTypeFilterChange,
                onFavoriteFilterChange = onFavoriteFilterChange,
                onScoreFilterChange = onScoreFilterChange,
                onYearFilterChange = onYearFilterChange,
                onExtraFilterChange = onExtraFilterChange,
                onSortChange = onSortChange,
                activeTabScrollState = activeTabScrollState,
                accentColor = accentColor,
                horizontalPadding = horizontalPadding,
                hasActiveFilters = hasActiveFilters
            )
        }

        // ── Sabit platform tab bar (AniList / MAL / Simkl) ──────────────────────
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 0.dp),
            color = KitsugiColors.background
        ) {
            MyListTabBar(
                selectedTabIndex = selectedTabIndex,
                onTabIndexChange = onTabIndexChange,
                visibleEntries = visibleEntries,
                allEntries = entriesAfterAdultFilter,
                allLibraryEntryCount = allLibraryItems.size,
                isAniListConnected = isAniListConnected,
                isMalConnected = isMalConnected,
                isSimklConnected = isSimklConnected,
                isKitsuConnected = isKitsuConnected,
                isShikimoriConnected = isShikimoriConnected,
                isBangumiConnected = isBangumiConnected,
                anilistUsername = appSettings.anilistUsername,
                malUsername = appSettings.malUsername,
                simklUsername = appSettings.simklUsername,
                kitsuUsername = appSettings.kitsuUsername,
                shikimoriUsername = appSettings.shikimoriUsername,
                bangumiUsername = appSettings.bangumiUsername,
                onEntryClick = onEntryClick,
                onExternalSyncMessage = onExternalSyncMessage,
                accentColor = accentColor,
                horizontalPadding = horizontalPadding,
                isNotificationsVisible = isNotificationsVisible,
                onOpenNotifications = onOpenNotifications
            )
        }

        // ── HorizontalPager: her sekme kendi scroll alanı ──────────────────────
        // tabScrollStates declared at composable top level


        // activeTabScrollState is declared at composable top level (tabScrollStates[selectedTabIndex])

        LaunchedEffect(activeTabScrollState) {
            snapshotFlow {
                activeTabScrollState.firstVisibleItemIndex to activeTabScrollState.firstVisibleItemScrollOffset
            }.collect { (index, offset) ->
                if (selectedTabIndex in restoredTabs && entries.isNotEmpty()) {
                    onScrollPositionChange(index, offset)
                }
                val scrollingDown = index > prevIndex || (index == prevIndex && offset > prevOffset + 15)
                val scrollingUp   = index < prevIndex || (index == prevIndex && offset < prevOffset - 15)
                // Header collapse
                showHeader = (index == 0 && offset < 100)
                // Reset scroll/show bottom bar at top
                if (index == 0 && offset == 0) {
                    onScrollReset()
                }
                // Scroll-to-top FAB
                showScrollToTopState = index > 3
                // FAB (Tümü) görünürlüğü
                if (index == 0 && offset < 40) {
                    isFabVisible = true
                } else if (scrollingDown) {
                    isFabVisible = false
                } else if (scrollingUp) {
                    isFabVisible = true
                }
                // Hızlı aşağı scroll'da panel/arama otomatik kapat
                if (scrollingDown && index >= 1) {
                    if (showFilterPanel) showFilterPanel = false
                    if (showSearchField) showSearchField = false
                }
                prevIndex = index
                prevOffset = offset
            }
        }

        val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides if (isTvDevice) tvSpec else LocalBringIntoViewSpec.current
        ) {
            HorizontalPager(
                state = tabPagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = !isTvDevice
            ) { page ->
                val pageTabIndex = page
                val pageIsConnected = when (pageTabIndex) {
                    MY_LIST_ANILIST_TAB_INDEX -> isAniListConnected
                    MY_LIST_MAL_TAB_INDEX -> isMalConnected
                    MY_LIST_SIMKL_TAB_INDEX -> isSimklConnected
                    MY_LIST_KITSU_TAB_INDEX -> isKitsuConnected
                    MY_LIST_SHIKIMORI_TAB_INDEX -> isShikimoriConnected
                    MY_LIST_BANGUMI_TAB_INDEX -> isBangumiConnected
                    MY_LIST_ALL_TAB_INDEX -> true // A combined library also includes locally cached records.
                    else -> false
                }
                val pageScrollState = tabScrollStates[pageTabIndex]
                val pageEntries = remember(entriesAfterAdultFilter, allLibraryEntries, pageTabIndex) {
                    if (pageTabIndex == MY_LIST_ALL_TAB_INDEX) {
                        allLibraryEntries
                    } else {
                        entriesAfterAdultFilter.filter { entry ->
                            myListSourceMatchesTab(pageTabIndex, entry.source)
                        }
                    }
                }

                // Wait until this page has actual items before restoring a deep position.
                // A loading/empty LazyColumn would otherwise reset the saved index to zero.
                LaunchedEffect(pageTabIndex, pageEntries.isNotEmpty()) {
                    if (pageEntries.isNotEmpty() && pageTabIndex !in restoredTabs) {
                        pageScrollState.scrollToItem(savedTabIndices[pageTabIndex], savedTabOffsets[pageTabIndex])
                        restoredTabs = restoredTabs + pageTabIndex
                    }
                }
                LaunchedEffect(pageScrollState, pageTabIndex, pageEntries.isNotEmpty(), restoredTabs.contains(pageTabIndex)) {
                    snapshotFlow { pageScrollState.firstVisibleItemIndex to pageScrollState.firstVisibleItemScrollOffset }
                        .collect { (index, offset) ->
                            if (pageTabIndex in restoredTabs && pageEntries.isNotEmpty()) {
                                savedTabIndices = savedTabIndices.toMutableList().also { it[pageTabIndex] = index }
                                savedTabOffsets = savedTabOffsets.toMutableList().also { it[pageTabIndex] = offset }
                            }
                        }
                }

                MyListContentPage(
                    pageTabIndex = pageTabIndex,
                    selectedTabIndex = selectedTabIndex,
                    isConnected = pageIsConnected,
                    isSimklSessionExpired = isSimklSessionExpired,
                    pageEntries = pageEntries,
                    sourceBadgesByEntryId = if (pageTabIndex == MY_LIST_ALL_TAB_INDEX) allSourceBadgesByEntryId else emptyMap(),
                    visibleEntries = visibleEntries,
                    groupedVisibleEntries = groupedVisibleEntries,
                    searchQuery = searchQuery,
                    selectedStatusFilterId = selectedStatusFilterId,
                    selectedListLayoutId = selectedListLayoutId,
                    appSettings = appSettings,
                    pageScrollState = pageScrollState,
                    onLogin = {
                        when (pageTabIndex) {
                            MY_LIST_ANILIST_TAB_INDEX -> onLoginAniList()
                            MY_LIST_MAL_TAB_INDEX -> onLoginMal()
                            MY_LIST_SIMKL_TAB_INDEX -> onLoginSimkl()
                            MY_LIST_KITSU_TAB_INDEX -> {
                                if (isKitsuConnected) onLoginKitsu()
                                else showKitsuLoginDialog = true
                            }
                            MY_LIST_SHIKIMORI_TAB_INDEX -> {
                                if (isShikimoriConnected) onLoginShikimori()
                                else showShikimoriLoginDialog = true
                            }
                            MY_LIST_BANGUMI_TAB_INDEX -> {
                                if (isBangumiConnected) onLoginBangumi()
                                else showBangumiLoginDialog = true
                            }
                        }
                    },
                    onRefresh = {
                        when (pageTabIndex) {
                            MY_LIST_ANILIST_TAB_INDEX -> onSyncAniList()
                            MY_LIST_MAL_TAB_INDEX -> onSyncMal()
                            MY_LIST_SIMKL_TAB_INDEX -> onSyncSimkl()
                            MY_LIST_KITSU_TAB_INDEX -> onSyncKitsu()
                            MY_LIST_SHIKIMORI_TAB_INDEX -> onSyncShikimori()
                            MY_LIST_BANGUMI_TAB_INDEX -> onSyncBangumi()
                            MY_LIST_ALL_TAB_INDEX -> {
                                var startedSync = false
                                if (isAniListConnected) { onSyncAniList(); startedSync = true }
                                if (isMalConnected) { onSyncMal(); startedSync = true }
                                if (isSimklConnected && !isSimklSessionExpired) { onSyncSimkl(); startedSync = true }
                                if (isKitsuConnected) { onSyncKitsu(); startedSync = true }
                                if (isShikimoriConnected) { onSyncShikimori(); startedSync = true }
                                if (isBangumiConnected) { onSyncBangumi(); startedSync = true }
                                if (!startedSync) onExternalSyncMessage("Birleşik listeyi yenilemek için en az bir kaynağı bağla")
                            }
                        }
                    },
                    onEntryClick = onEntryClick,
                    onIncrementProgress = { incrementEntryProgress(it) },
                    onPosterLongClick = { imageUrl ->
                        activeZoomImageUrl = imageUrl
                        val zoomEntry = visibleEntries.find { it.imageUrl == imageUrl }
                            ?: pageEntries.find { it.imageUrl == imageUrl }
                        activeZoomTitle = zoomEntry?.title ?: ""
                        activeZoomIsAdult = zoomEntry?.isAdult == true
                    },
                    accentColor = accentColor,
                    horizontalPadding = horizontalPadding
                )
            }
        }

    } // end Column

    // ── Scroll-aware floating category button ──
    val isTv = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    if (!isTv) {
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding by animateDpAsState(
            targetValue = if (isLandscape) {
                16.dp
            } else if (isBottomBarVisible) {
                96.dp + navigationBarsPadding
            } else {
                16.dp + navigationBarsPadding
            },
            animationSpec = tween(durationMillis = 200),
            label = "fab_bottom_padding"
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tümü (Kategori) button on the Bottom-Start (Bottom-Left)
            AnimatedVisibility(
                visible = isFabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        bottom = bottomPadding,
                        start = 20.dp
                    )
                    .zIndex(10f)
            ) {
                val activeStatusLabel = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.title
                    ?: if (selectedStatusFilterId == "adult") "Yetişkin"
                    else if (selectedStatusFilterId == "favorites") "Favoriler"
                    else "Tümü"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(999.dp), onClick = { showStatusBottomSheet = true })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FormatListBulleted,
                            contentDescription = "Kategori",
                            tint = KitsugiColors.background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeStatusLabel,
                            color = KitsugiColors.background,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Scroll to Top button on the Bottom-End (Bottom-Right)
            AnimatedVisibility(
                visible = showScrollToTopState,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = bottomPadding,
                        end = 20.dp
                    )
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            coroutineScope.launch {
                                val activeState = tabScrollStates[selectedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)]
                                activeState.animateScrollToItem(0)
                                onScrollReset()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Yukarı Git",
                        tint = KitsugiColors.background,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showStatusBottomSheet) {
        KitsugiListStatusBottomSheet(
            entries = selectedTabEntries,
            selectedStatusFilterId = selectedStatusFilterId,
            showAdultContent = showAdultContent,
            onStatusSelected = { newStatusId ->
                onStatusFilterChange(newStatusId)
                showStatusBottomSheet = false
            },
            onDismissRequest = {
                showStatusBottomSheet = false
            }
        )
    }


    if (showAddDialog) {
        // Manüel kayıt varsayılan kaynağı: Tümü sekmesinde AniList.
        val manualAddSource = defaultMyListSourceForTab(selectedTabIndex)
        KitsugiMediaEntryEditorDialog(
            source = manualAddSource,
            onDismiss = {
                showAddDialog = false
            },
            onConfirm = { title, subtitle, type, status, isAdult, progress, total, score, isFavorite, startDate, endDate, notes, tags, priority, isRepeating, repeatCount, repeatValue, volumeProgress, isPrivate, isHiddenFromStatusLists, _ ->
                val newEntry = MediaEntry(
                    id = 0,
                    title = title,
                    subtitle = if (subtitle.isBlank()) {
                        "Manuel ${myListSourceDisplayName(manualAddSource)} Kaydı"
                    } else {
                        subtitle
                    },
                    type = type,
                    status = status,
                    score = score,
                    progress = progress,
                    total = total,
                    isFavorite = isFavorite,
                    isAdult = isAdult,
                    source = manualAddSource,
                    malId = null,
                    imageUrl = null,
                    year = null,
                    synopsis = null,
                    startDate = startDate,
                    endDate = endDate,
                    notes = notes,
                    tags = tags,
                    priority = priority,
                    isRepeating = isRepeating,
                    repeatCount = repeatCount,
                    repeatValue = repeatValue,
                    volumeProgress = volumeProgress,
                    isPrivate = isPrivate,
                    isHiddenFromStatusLists = isHiddenFromStatusLists
                )

                viewModel.insertEntry(newEntry)

                showAddDialog = false
            }
        )
    }

    if (showApiSearchDialog) {
        KitsugiApiSearchDialog(
            onDismiss = {
                showApiSearchDialog = false
            },
            onResultSelected = { selection ->
                val result = selection.result
                // Always honour the result's own source instead of guessing from tab index.
                // Normalize "jikan" to "mal" since they refer to the same service.
                val resolvedSource = when (result.source.lowercase()) {
                    "jikan" -> "mal"
                    "anilist", "mal", "simkl" -> result.source.lowercase()
                    else -> result.source
                }

                // Duplicate kontrolü: herhangi bir platform ID'si üzerinden eşleşme yeter
                val alreadyExists = entries.firstOrNull { entry -> entry.matches(result) }

                if (alreadyExists != null) {
                    duplicateMessage = duplicateListMessage(alreadyExists)
                    showApiSearchDialog = false
                    return@KitsugiApiSearchDialog
                }

                val newEntry = MediaEntry(
                    id = 0,
                    title = result.title,
                    subtitle = result.subtitle,
                    type = result.type,
                    status = WatchStatus.Planned,
                    // Yeni kayıtlar puansız başlar; API metadata puanı kullanıcı puanı değil
                    score = null,
                    progress = 0,
                    total = result.total,
                    isFavorite = false,
                    isAdult = result.isAdult,
                    source = resolvedSource,
                    malId = result.malId,
                    imageUrl = result.imageUrl,
                    year = result.year,
                    synopsis = selection.synopsis,
                    startDate = null,
                    endDate = null
                )

                viewModel.insertEntry(newEntry)

                showApiSearchDialog = false
            }
        )
    }

    duplicateMessage?.let { message ->
        KitsugiInfoDialog(
            title = "Zaten listede",
            message = message,
            onDismiss = {
                duplicateMessage = null
            }
        )
    }

    editingEntry?.let { entry ->
        KitsugiMediaEntryEditorDialog(
            initialEntry = entry,
            onDismiss = {
                editingEntry = null
            },
            onDeleteClick = {
                editingEntry = null
                viewModel.deleteEntryById(entry.id)
            },
            onConfirm = { title, subtitle, type, status, isAdult, progress, total, score, isFavorite, startDate, endDate, notes, tags, priority, isRepeating, repeatCount, repeatValue, volumeProgress, isPrivate, isHiddenFromStatusLists, advancedScores ->
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

                // Kullanıcı tarihleri boş bıraktıysa durum değişimine göre otomatik doldur
                val resolvedStartDate = if (startDate.isNullOrBlank() &&
                    (status == WatchStatus.Watching || status == WatchStatus.Completed) &&
                    entry.startDate.isNullOrBlank()
                ) today else startDate

                val resolvedEndDate = if (endDate.isNullOrBlank() &&
                    status == WatchStatus.Completed &&
                    entry.endDate.isNullOrBlank()
                ) today else endDate

                val updatedEntry = entry.copy(
                    title = title,
                    subtitle = if (subtitle.isBlank()) {
                        "Manuel eklenen içerik"
                    } else {
                        subtitle
                    },
                    type = type,
                    status = status,
                    isAdult = isAdult,
                    progress = progress,
                    total = total,
                    score = score,
                    isFavorite = isFavorite,
                    startDate = resolvedStartDate,
                    endDate = resolvedEndDate,
                    notes = notes,
                    tags = tags,
                    priority = priority ?: 0,
                    isRepeating = isRepeating,
                    repeatCount = repeatCount,
                    repeatValue = repeatValue,
                    volumeProgress = volumeProgress,
                    isPrivate = isPrivate,
                    isHiddenFromStatusLists = isHiddenFromStatusLists
                )

                viewModel.updateEntry(updatedEntry, advancedScores = advancedScores)

                editingEntry = null
            }
        )
    }

    deletingEntry?.let { entry ->
        KitsugiConfirmDialog(
            title = "Kaydı sil?",
            message = "\"${entry.title}\" listenizden kalıcı olarak kaldırılacak.",
            confirmText = "Sil",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteEntryById(entry.id)

                deletingEntry = null
            },
            onDismiss = {
                deletingEntry = null
            }
        )
    }

    if (activeZoomImageUrl != null) {
        KitsugiImagePreviewDialog(
            imageUrl = activeZoomImageUrl!!,
            title = activeZoomTitle,
            isAdult = activeZoomIsAdult,
            onDismiss = {
                activeZoomImageUrl = null
                activeZoomTitle = ""
                activeZoomIsAdult = false
            }
        )
    }

    if (showKitsuLoginDialog) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = { username, password, onComplete ->
                onKitsuAuthSubmit(username, password) { success, error ->
                    onComplete(success, error)
                    if (success) {
                        onSyncKitsu()
                    }
                }
            }
        )
    }

    if (showShikimoriLoginDialog) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = { clientId, clientSecret, authCode, onComplete ->
                onShikimoriAuthSubmit(clientId, clientSecret, authCode) { success, error ->
                    onComplete(success, error)
                    if (success) {
                        onSyncShikimori()
                    }
                }
            }
        )
    }

    if (showBangumiLoginDialog) {
        KitsugiBangumiLoginDialog(
            onDismiss = { showBangumiLoginDialog = false },
            onLogin = { clientId, clientSecret, authCode, onComplete ->
                onBangumiAuthSubmit(clientId, clientSecret, authCode) { success, error ->
                    onComplete(success, error)
                    if (success) {
                        onSyncBangumi()
                    }
                }
            }
        )
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kitsugi.animelist.ui.screens.profile

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.StopCircle
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.data.remote.matches
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiSearchField
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.screens.mylist.MyListFlatContent
import com.kitsugi.animelist.ui.screens.mylist.MyListGroupedContent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

@Composable
fun KitsugiUserMediaListScreen(
    userId: Int,
    username: String,
    initialMediaType: MediaType,
    appSettings: AppSettings,
    mediaEntries: List<MediaEntry>,
    onBackClick: () -> Unit,
    onMediaClick: (JikanSearchResult) -> Unit,
    onLocalEntryClick: (MediaEntry) -> Unit,
    accentColor: Color = LocalKitsugiAccent.current,
    customViewModel: KitsugiUserMediaListViewModel? = null
) {
    val viewModel: KitsugiUserMediaListViewModel = customViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel(key = "user_media_list_${userId}_${initialMediaType.name}")

    val pagerState = rememberPagerState(
        initialPage = if (initialMediaType == MediaType.Anime) 0 else 1,
        pageCount = { 2 }
    )
    var selectedType by rememberSaveable { mutableStateOf(initialMediaType) }

    // Pager kaydırınca type güncelle
    LaunchedEffect(pagerState.settledPage) {
        selectedType = if (pagerState.settledPage == 0) MediaType.Anime else MediaType.Manga
    }

    LaunchedEffect(userId, selectedType) {
        viewModel.loadUserMediaList(userId, selectedType)
    }

    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedStatusFilter by rememberSaveable { mutableStateOf<WatchStatus?>(null) }

    var isFabVisible by rememberSaveable { mutableStateOf(true) }
    var isSearchBarVisible by rememberSaveable { mutableStateOf(true) }
    var prevIndex by rememberSaveable { mutableStateOf(0) }
    var prevOffset by rememberSaveable { mutableStateOf(0) }

    val lazyListState = rememberLazyListState()

    val showScrollToTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 3
        }
    }

    LaunchedEffect(Unit) {
        isFabVisible = true
        prevIndex = 0
        prevOffset = 0
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset < 40) {
                    isFabVisible = true
                    isSearchBarVisible = true
                } else if (index > prevIndex || (index == prevIndex && offset > prevOffset + 15)) {
                    isFabVisible = false
                    isSearchBarVisible = false
                } else if (index < prevIndex || (index == prevIndex && offset < prevOffset - 15)) {
                    isFabVisible = true
                    isSearchBarVisible = true
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    // Sort: 0=Varsayılan, 1=A-Z, 2=Puan, 3=İlerleme
    var sortId by rememberSaveable { mutableStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showStatusBottomSheet by remember { mutableStateOf(false) }

    val sortLabels = listOf("Varsayılan", "A-Z", "Puana Göre ↓", "İlerleyeye Göre ↓")

    val filteredItems = remember(state.items, searchQuery, selectedStatusFilter, sortId) {
        state.items
            .filter { item ->
                if (selectedStatusFilter == null) true else item.status == selectedStatusFilter
            }
            .filter { item ->
                if (searchQuery.isBlank()) true
                else item.title.lowercase().contains(searchQuery.trim().lowercase())
            }
            .let { list ->
                when (sortId) {
                    1 -> list.sortedBy { it.title.lowercase() }
                    2 -> list.sortedByDescending { it.score ?: -1.0 }
                    3 -> list.sortedByDescending { it.progress }
                    else -> list
                }
            }
    }

    val statusOrder = listOf(
        WatchStatus.Watching,
        WatchStatus.Paused,
        WatchStatus.Planned,
        WatchStatus.Dropped,
        WatchStatus.Completed
    )

    // Listem (MyListScreen) bileşenleri için MediaEntry görünümü
    val listemEntries = remember(filteredItems) { filteredItems.map { it.toListemMediaEntry() } }
    val listemItemsById = remember(filteredItems) { filteredItems.associateBy { it.mediaId } }
    val listemGrouped = remember(listemEntries) {
        listOf(
            WatchStatus.Watching, WatchStatus.Repeating, WatchStatus.Planned,
            WatchStatus.Paused, WatchStatus.Dropped, WatchStatus.Completed
        ).map { status -> status to listemEntries.filter { it.status == status } }
            .filter { it.second.isNotEmpty() }
    }

    val pullRefreshState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        // Sticky Header / Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = KitsugiColors.Surface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Geri",
                                tint = KitsugiColors.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = username,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedType == MediaType.Anime) "Anime Listesi" else "Manga Listesi",
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sort chip
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KitsugiColors.SurfaceStrong)
                                    .tvClickable(shape = RoundedCornerShape(12.dp)) { showSortMenu = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "↕ ${sortLabels[sortId]}",
                                    color = if (sortId != 0) accentColor else KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                sortLabels.forEachIndexed { idx, label ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(text = label, color = if (sortId == idx) accentColor else KitsugiColors.TextPrimary) },
                                        onClick = { sortId = idx; showSortMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Anime / Manga Pager Tab (kaydırılabilir)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(4.dp)
                ) {
                    listOf("Anime Listesi", "Manga Listesi").forEachIndexed { idx, label ->
                        val isSelected = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .tvClickable(shape = RoundedCornerShape(12.dp)) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Arama Kutusu - kaydırma yönüne göre gizlenir/görünür
                AnimatedVisibility(
                    visible = isSearchBarVisible,
                    enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
                    exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        KitsugiSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Kullanıcının listesinde ara...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Kaydırılabilir Pager İçeriği
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = true
        ) { page ->
        val pageMediaType = if (page == 0) MediaType.Anime else MediaType.Manga
        LaunchedEffect(page) {
            viewModel.loadUserMediaList(userId, pageMediaType)
        }
        PullToRefreshBox(
            isRefreshing = state.isLoading && selectedType == pageMediaType,
            onRefresh = {
                viewModel.loadUserMediaList(userId, pageMediaType, forceRefresh = true)
            },
            modifier = Modifier.fillMaxSize(),
            state = pullRefreshState
        ) {
            if (state.isLoading && state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KitsugiPlasmaLoader(size = 48.dp)
                }
            } else if (state.error != null && state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.error!!, color = KitsugiColors.TextMuted)
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Gösterilecek öğe bulunamadı",
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val handleItemClick: (UserMediaListItem) -> Unit = { item ->
                    val stableId = if (item.malId != null && item.malId > 0) item.malId else (item.mediaId + 100_000_000)
                    val searchResult = JikanSearchResult(
                        malId = stableId,
                        title = item.title,
                        subtitle = item.format ?: "",
                        type = item.mediaType,
                        total = item.total,
                        score = item.score?.toInt(),
                        isAdult = item.isAdult,
                        imageUrl = item.imageUrl,
                        year = item.year,
                        source = "anilist",
                        realMalId = item.malId,
                        rawScoreDouble = item.score
                    )
                    val existing = mediaEntries.firstOrNull { it.matches(searchResult) }
                    if (existing != null) {
                        onLocalEntryClick(existing)
                    } else {
                        onMediaClick(searchResult)
                    }
                }

                val listemGridColumns = if (isLandscape) {
                    if (configuration.screenWidthDp >= 900) 6 else 5
                } else {
                    if (configuration.screenWidthDp >= 600) 4 else 3
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isLandscape) 12.dp else 20.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${filteredItems.size} sonuç",
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                        )
                    }
                    if (selectedStatusFilter == null) {
                        MyListGroupedContent(
                            groupedEntries = listemGrouped,
                            selectedListLayoutId = appSettings.selectedListLayoutId,
                            titleLanguage = appSettings.titleLanguage,
                            scoreFormat = appSettings.scoreFormat,
                            hideScores = appSettings.hideScores,
                            blurAdultMedia = appSettings.blurAdultMedia,
                            gridColumns = listemGridColumns,
                            sourceBadgesByEntryId = emptyMap(),
                            onEntryClick = { entry -> listemItemsById[entry.id]?.let(handleItemClick) },
                            onIncrementProgress = { entry -> listemItemsById[entry.id]?.let(handleItemClick) },
                            onPosterLongClick = { _ -> }
                        )
                    } else {
                        MyListFlatContent(
                            visibleEntries = listemEntries,
                            selectedListLayoutId = appSettings.selectedListLayoutId,
                            titleLanguage = appSettings.titleLanguage,
                            scoreFormat = appSettings.scoreFormat,
                            hideScores = appSettings.hideScores,
                            blurAdultMedia = appSettings.blurAdultMedia,
                            gridColumns = listemGridColumns,
                            sourceBadgesByEntryId = emptyMap(),
                            onEntryClick = { entry -> listemItemsById[entry.id]?.let(handleItemClick) },
                            onIncrementProgress = { entry -> listemItemsById[entry.id]?.let(handleItemClick) },
                            onPosterLongClick = { _ -> }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(90.dp)) }
                }
            }
        }
        } // end HorizontalPager
    }

    // ── Floating Status FAB (sağ alt köşe) ──
    val isTv = LocalIsTvDevice.current
    if (!isTv && !state.isLoading && state.items.isNotEmpty()) {
        Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
        ) {
            // Tümü (Kategori) button on the Bottom-Start (Bottom-Left)
            AnimatedVisibility(
                visible = isFabVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 20.dp, start = 20.dp)
                    .zIndex(10f)
            ) {
                val fabLabel = when (selectedStatusFilter) {
                    WatchStatus.Watching -> if (selectedType == MediaType.Anime) "İzleniyor" else "Okunuyor"
                    WatchStatus.Completed -> "Tamamlandı"
                    WatchStatus.Planned -> "Planlandı"
                    WatchStatus.Paused -> "Durduruldu"
                    WatchStatus.Dropped -> "Bırakıldı"
                    else -> "Tümü"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(999.dp)) {
                            showStatusBottomSheet = true
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.List,
                            contentDescription = "Kategori",
                            tint = KitsugiColors.Background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fabLabel,
                            color = KitsugiColors.Background,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Scroll to Top button on the Bottom-End (Bottom-Right)
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 20.dp)
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            coroutineScope.launch {
                                lazyListState.animateScrollToItem(0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Yukarı Git",
                        tint = KitsugiColors.Background,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showStatusBottomSheet) {
        UserMediaListStatusBottomSheet(
            items = state.items,
            selectedStatus = selectedStatusFilter,
            mediaType = selectedType,
            onStatusSelected = { selectedStatusFilter = it },
            onDismissRequest = { showStatusBottomSheet = false }
        )
    }
}

/** Diğer kullanıcının liste öğesini Listem kartlarının beklediği MediaEntry'e çevirir. */
private fun UserMediaListItem.toListemMediaEntry(): MediaEntry = MediaEntry(
    id = mediaId,
    title = title,
    subtitle = format.orEmpty(),
    type = mediaType,
    status = status,
    score = score?.toInt(),
    progress = progress,
    total = total,
    isAdult = isAdult,
    source = "anilist",
    malId = malId,
    imageUrl = imageUrl,
    year = year
)

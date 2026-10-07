package com.kitsugi.animelist.ui.screens.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.notifications.NotificationDiagnostics
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

// ─── Platform seçimi ──────────────────────────────────────────────────────────

private enum class NotifPlatform(val label: String, val logoId: String) {
    ANILIST("AniList", "anilist"),
    MAL("MAL", "mal"),
    TMDB_SIMKL("TMDB & Simkl", "simkl"),
    KITSU("Kitsu", "kitsu"),
    SHIKIMORI("Shikimori", "shikimori")
}

// ─── Ana Ekran ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KitsugiNotificationsScreen(
    mediaEntries: List<MediaEntry>,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isKitsuConnected: Boolean = false,
    isShikimoriConnected: Boolean = false,
    onBack: () -> Unit,
    onOpenApiDetail: ((mediaId: Int, source: String, mediaType: String?, title: String?, imageUrl: String?) -> Unit)? = null,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    viewModel: KitsugiNotificationsViewModel = viewModel()
) {
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aniListToken = remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) }
    val apiClient = remember(aniListToken) { com.kitsugi.animelist.data.remote.JikanApiClient(aniListToken) }
    var activeActivityIdForDetail by remember { mutableStateOf<Int?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }

    // ── ViewModel state'lerini topla ──
    val malState       by viewModel.mal.collectAsState()
    val simklState     by viewModel.tmdbSimkl.collectAsState()
    val kitsuState     by viewModel.kitsu.collectAsState()
    val shikimoriState by viewModel.shikimori.collectAsState()
    val aniListUnread  by viewModel.aniListUnread.collectAsState()
    val diagState      by viewModel.diagnostics.collectAsState()

    // ── Sayfa + filtre state ──
    val pagerState = rememberPagerState(
        initialPage = when {
            isAniListConnected   -> 0
            isMalConnected       -> 1
            isSimklConnected     -> 2
            isKitsuConnected     -> 3
            isShikimoriConnected -> 4
            else                 -> 0
        },
        pageCount = { 5 }
    )

    val aniListPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { AniListFilter.entries.size }
    )
    val currentAniListFilter = AniListFilter.entries[aniListPagerState.currentPage]

    // ── İlk yükleme — sayfa veya filtre değiştiğinde ──
    LaunchedEffect(pagerState.currentPage, currentAniListFilter) {
        when (pagerState.currentPage) {
            0 -> if (isAniListConnected) viewModel.loadAniList(
                filter = currentAniListFilter,
                resetPage = true,
                mediaEntries = mediaEntries
            )
            1 -> if (isMalConnected) viewModel.loadMal(mediaEntries)
            2 -> if (isSimklConnected) viewModel.loadTmdbSimkl(mediaEntries)
            3 -> if (isKitsuConnected) viewModel.loadKitsu(mediaEntries)
            4 -> if (isShikimoriConnected) viewModel.loadShikimori(mediaEntries)
        }
    }

    val malListState       = rememberLazyListState()
    val tmdbSimklListState = rememberLazyListState()
    val kitsuListState     = rememberLazyListState()
    val shikimoriListState = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen  = isLandscape || configuration.screenWidthDp > 600

    fun refreshCurrentPage() {
        when (pagerState.currentPage) {
            0 -> viewModel.loadAniList(currentAniListFilter, resetPage = true, mediaEntries = mediaEntries)
            1 -> viewModel.loadMal(mediaEntries)
            2 -> viewModel.loadTmdbSimkl(mediaEntries)
            3 -> viewModel.loadKitsu(mediaEntries)
            4 -> viewModel.loadShikimori(mediaEntries)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .statusBarsPadding()
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.notif_action_back),
                    tint = KitsugiColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.notif_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = {
                showDiagnostics = true
                viewModel.runDiagnostics(mediaEntries)
            }) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = stringResource(R.string.notif_diag_action),
                    tint = KitsugiColors.TextPrimary
                )
            }
            IconButton(onClick = { refreshCurrentPage() }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.notif_action_refresh),
                    tint = accentColor
                )
            }
        }

        // ── Platform Sekmeleri ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 18.dp else 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NotifPlatform.entries.forEachIndexed { index, platform ->
                    val active    = pagerState.currentPage == index
                    val isConnected = when (platform) {
                        NotifPlatform.ANILIST    -> isAniListConnected
                        NotifPlatform.MAL        -> isMalConnected
                        NotifPlatform.TMDB_SIMKL -> isSimklConnected
                        NotifPlatform.KITSU      -> isKitsuConnected
                        NotifPlatform.SHIKIMORI  -> isShikimoriConnected
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) accentColor else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(18.dp)) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            KitsugiPlatformLogo(
                                platformId = platform.logoId,
                                size = 16.dp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = platform.label,
                                color = if (active) KitsugiColors.Background
                                        else if (isConnected) KitsugiColors.TextPrimary
                                        else KitsugiColors.TextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Black else FontWeight.Medium
                            )
                            // AniList okunmamış bildirim rozeti
                            val badge = if (platform == NotifPlatform.ANILIST) aniListUnread else null
                            if (badge != null && badge > 0) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (active) KitsugiColors.Background else accentColor)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (badge > 99) "99+" else badge.toString(),
                                        color = if (active) accentColor else KitsugiColors.Background,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (isConnected) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (active) KitsugiColors.Background
                                            else KitsugiColors.AccentGreen
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Pager ──
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                // ──────────────── Page 0: AniList ────────────────
                0 -> {
                    if (!isAniListConnected) {
                        CenteredEmptyState(stringResource(R.string.notif_login_required_anilist))
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AniListFilterHeader(
                                activeFilter = currentAniListFilter,
                                onFilterSelected = { filter ->
                                    scope.launch {
                                        aniListPagerState.animateScrollToPage(filter.ordinal)
                                    }
                                },
                                isWideScreen = isWideScreen,
                                accentColor = accentColor
                            )

                            HorizontalPager(
                                state = aniListPagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) { filterPage ->
                                val filter = AniListFilter.entries[filterPage]
                                val filterState by viewModel.getAniListState(filter).collectAsState()
                                val listState = rememberLazyListState()

                                // Infinite scroll for this specific filter list
                                val shouldLoadMore by remember {
                                    derivedStateOf {
                                        val total = listState.layoutInfo.totalItemsCount
                                        val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        last >= total - 5 && total > 0
                                    }
                                }
                                LaunchedEffect(shouldLoadMore) {
                                    if (shouldLoadMore && !filterState.isLoading && filterState.hasMore) {
                                        viewModel.loadAniList(
                                            filter = filter,
                                            resetPage = false,
                                            mediaEntries = mediaEntries
                                        )
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    when {
                                        filterState.isLoading && filterState.items.isEmpty() -> {
                                            item { LoadingState(accentColor) }
                                        }
                                        filterState.error != null && filterState.items.isEmpty() -> {
                                            item { CenteredEmptyState(filterState.error!!) }
                                        }
                                        filterState.items.isEmpty() -> {
                                            item { CenteredEmptyState(stringResource(R.string.notif_empty_no_notifications)) }
                                        }
                                        else -> {
                                            items(filterState.items, key = { it.id }) { notif ->
                                                NotifItemRow(
                                                    notif = notif,
                                                    accentColor = accentColor,
                                                    onUserProfileClick = onUserProfileClick,
                                                    onClick = {
                                                        if (notif.activityId != null && notif.activityId > 0) {
                                                            activeActivityIdForDetail = notif.activityId
                                                        } else if (notif.mediaId != null) {
                                                            onOpenApiDetail?.invoke(notif.mediaId, "anilist", notif.mediaType, notif.title, notif.imageUrl)
                                                        } else if (notif.userId != null && notif.userName != null) {
                                                            onUserProfileClick?.invoke(notif.userId, notif.userName, notif.userAvatarUrl)
                                                        }
                                                    }
                                                )
                                            }
                                            // Sayfa sonu yükleyici
                                            if (filterState.isLoading) {
                                                item {
                                                    Box(
                                                        Modifier.fillMaxWidth().padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        KitsugiPlasmaLoader(size = 26.dp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ──────────────── Page 1: MAL ────────────────
                1 -> SourceNotifPage(
                    state = malState,
                    isConnected = isMalConnected,
                    loginRequiredMessage = stringResource(R.string.notif_login_required_mal),
                    emptyMessage = stringResource(R.string.notif_empty_list_mal),
                    accentColor = accentColor,
                    listState = malListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id -> onOpenApiDetail?.invoke(id, "jikan", notif.mediaType, notif.title, notif.imageUrl) }
                    }
                )

                // ──────────────── Page 2: TMDB & Simkl ────────────────
                2 -> SourceNotifPage(
                    state = simklState,
                    isConnected = isSimklConnected,
                    loginRequiredMessage = stringResource(R.string.notif_login_required_simkl),
                    emptyMessage = stringResource(R.string.notif_empty_list_simkl),
                    accentColor = accentColor,
                    listState = tmdbSimklListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id -> onOpenApiDetail?.invoke(id, "simkl", notif.mediaType, notif.title, notif.imageUrl) }
                    }
                )

                // ──────────────── Page 3: Kitsu ────────────────
                3 -> SourceNotifPage(
                    state = kitsuState,
                    isConnected = isKitsuConnected,
                    loginRequiredMessage = stringResource(R.string.notif_notice_kitsu_not_connected),
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    accentColor = accentColor,
                    listState = kitsuListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id -> onOpenApiDetail?.invoke(id, "kitsu", notif.mediaType, notif.title, notif.imageUrl) }
                    }
                )

                // ──────────────── Page 4: Shikimori ────────────────
                4 -> SourceNotifPage(
                    state = shikimoriState,
                    isConnected = isShikimoriConnected,
                    loginRequiredMessage = stringResource(R.string.notif_login_required_shikimori),
                    emptyMessage = stringResource(R.string.notif_empty_no_notifications),
                    accentColor = accentColor,
                    listState = shikimoriListState,
                    onUserProfileClick = onUserProfileClick,
                    onItemClick = { notif ->
                        notif.mediaId?.let { id -> onOpenApiDetail?.invoke(id, "shikimori", notif.mediaType, notif.title, notif.imageUrl) }
                    }
                )
            }
        }
    }

    if (activeActivityIdForDetail != null) {
        com.kitsugi.animelist.ui.components.KitsugiActivityDetailBottomSheet(
            activityId = activeActivityIdForDetail!!,
            apiClient = apiClient,
            onUserProfileClick = { uid, uname, uavatar ->
                if (uid != null) {
                    onUserProfileClick?.invoke(uid, uname, uavatar)
                }
            },
            onMediaClick = { mediaId, type, source ->
                onOpenApiDetail?.invoke(mediaId, source, type.name, null, null)
            },
            onDismiss = { activeActivityIdForDetail = null }
        )
    }

    if (showDiagnostics) {
        NotificationDiagnosticsSheet(
            state = diagState,
            onRerun = { viewModel.runDiagnostics(mediaEntries) },
            onDismiss = { showDiagnostics = false }
        )
    }
}

// ─── Kaynak sayfası (MAL / Simkl / Kitsu / Shikimori) ─────────────────────────

@Composable
private fun SourceNotifPage(
    state: NotifUiState,
    isConnected: Boolean,
    loginRequiredMessage: String,
    emptyMessage: String,
    accentColor: Color,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)?,
    onItemClick: (NotifItem) -> Unit
) {
    if (!isConnected && state.items.isEmpty()) {
        CenteredEmptyState(loginRequiredMessage)
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (!state.notice.isNullOrBlank()) {
            item(key = "notice") { SourceNoticeBanner(text = state.notice) }
        }
        when {
            state.isLoading && state.items.isEmpty() -> {
                item { LoadingState(accentColor) }
            }
            state.error != null && state.items.isEmpty() -> {
                item { CenteredEmptyState(state.error!!) }
            }
            state.items.isEmpty() -> {
                item { CenteredEmptyState(emptyMessage) }
            }
            else -> {
                items(state.items, key = { it.id }) { notif ->
                    NotifItemRow(
                        notif = notif,
                        accentColor = accentColor,
                        onUserProfileClick = onUserProfileClick,
                        onClick = { onItemClick(notif) }
                    )
                }
                if (state.isLoading) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiPlasmaLoader(size = 26.dp)
                        }
                    }
                }
            }
        }
    }
}

// ─── Kaynak bilgi notu (neden boş / hangi uç nokta kullanılıyor) ──────────────

@Composable
private fun SourceNoticeBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

// ─── Bildirim Teşhisi Paneli ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDiagnosticsSheet(
    state: NotifDiagnosticsUiState,
    onRerun: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.notif_diag_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.notif_diag_subtitle),
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(14.dp))

            if (state.isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitsugiPlasmaLoader(size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.notif_diag_running),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = state.error, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontSize = 12.sp)
            }

            state.rows.forEach { row ->
                DiagnosticRow(row = row)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KitsugiTonalButtonCompat(
                    text = stringResource(R.string.notif_diag_run),
                    enabled = !state.isRunning,
                    onClick = onRerun
                )
                KitsugiTonalButtonCompat(
                    text = stringResource(R.string.notif_diag_close),
                    enabled = true,
                    onClick = onDismiss,
                    tint = accentColor
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(row: NotificationDiagnostics.Row) {
    val statusColor = when {
        !row.connected -> KitsugiColors.TextMuted
        row.ok -> KitsugiColors.AccentGreen
        else -> Color(0xFFFF6B6B)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            KitsugiPlatformLogo(platformId = row.source, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = row.source,
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    if (row.ok) R.string.notif_diag_ok else R.string.notif_diag_fail
                ),
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.notif_diag_endpoint) + ": " + row.endpoint,
            color = KitsugiColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!row.connected) {
                Text(
                    text = stringResource(R.string.notif_diag_not_connected),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = stringResource(R.string.notif_diag_connected),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            row.httpStatus?.let {
                Text(
                    text = stringResource(R.string.notif_diag_status, it),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            row.itemCount?.let {
                Text(
                    text = stringResource(R.string.notif_diag_items, it),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            Text(
                text = stringResource(R.string.notif_diag_duration, row.durationMs),
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = row.detail,
            color = KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        row.error?.let { error ->
            Spacer(Modifier.height(3.dp))
            Text(text = error, color = Color(0xFFFF6B6B), fontSize = 11.sp, lineHeight = 15.sp)
        }
        row.hint?.let { hint ->
            Spacer(Modifier.height(3.dp))
            Text(text = hint, color = KitsugiColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** Teşhis panelindeki basit buton — Material3 TextButton'ın tema uyumlu sarmalayıcısı. */
@Composable
private fun KitsugiTonalButtonCompat(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = LocalKitsugiAccent.current
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
    ) {
        Text(text = text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── AniList Alt Filtre Header ────────────────────────────────────────────────

@Composable
private fun AniListFilterHeader(
    activeFilter: AniListFilter,
    onFilterSelected: (AniListFilter) -> Unit,
    isWideScreen: Boolean,
    accentColor: Color
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rowMod = if (isWideScreen) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .padding(4.dp)
            } else {
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .horizontalScroll(scrollState)
                    .padding(4.dp)
            }
            Row(
                modifier = rowMod,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isWideScreen) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(4.dp)
            ) {
                AniListFilter.entries.forEach { filter ->
                    val active    = activeFilter == filter
                    val itemMod   = if (isWideScreen) Modifier.weight(1f) else Modifier
                    Box(
                        modifier = itemMod
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (active) accentColor else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(22.dp)) { onFilterSelected(filter) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(filter.labelResId),
                            color = if (active) KitsugiColors.Background else KitsugiColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Bildirim Satır Kartı ─────────────────────────────────────────────────────

@Composable
private fun NotifItemRow(
    notif: NotifItem,
    accentColor: Color,
    onUserProfileClick: ((userId: Int, username: String, avatarUrl: String?) -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(if (notif.userId != null) CircleShape else RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceStrong)
                .then(
                    if (notif.userId != null && notif.userName != null && onUserProfileClick != null) {
                        Modifier.tvClickable(shape = CircleShape) {
                            onUserProfileClick(notif.userId, notif.userName, notif.userAvatarUrl)
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!notif.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notif.imageUrl,
                    contentDescription = notif.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.NotificationsNone,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Okunmamış göstergesi (Shikimori mesajları)
            if (notif.isUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = notif.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    KitsugiPlatformLogo(platformId = notif.source, size = 12.dp)
                    Text(
                        text = notif.source,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = notif.body,
                color = KitsugiColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!notif.dateText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = notif.dateText, color = KitsugiColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
    HorizontalDivider(
        color = KitsugiColors.SurfaceStrong.copy(alpha = 0.35f),
        modifier = Modifier.padding(start = 80.dp, end = 16.dp)
    )
}

// ─── Yardımcı Composable'lar ──────────────────────────────────────────────────

@Composable
private fun LoadingState(accentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KitsugiPlasmaLoader(size = 48.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.loading), color = KitsugiColors.TextMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CenteredEmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = KitsugiColors.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

package com.kitsugi.animelist.ui.screens.manga

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.app.MangaViewModel
import com.kitsugi.animelist.ui.components.KitsugiAddonsSettingsDialog
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiMotion
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.rememberKitsugiGridColumns
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.launch

// ─── Sabitler ─────────────────────────────────────────────────────────────────
// Yapışkan kaynak çubuğu: 56 dp çip satırı + 1 dp ayırıcı. Üstteki (katlanan)
// başlık bu kadarlık alanı terk edince çubuk tam olarak y = 0'a oturur.
private val MangaStickyBarHeight: Dp = 57.dp
private val MangaCompactTopBarHeight: Dp = 54.dp

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * Manga eklenti keşfet ekranı.
 *
 * v2.4.340 — "Video İzle / Keşfet" (StreamScreenContent) ile aynı üst bar dili:
 *  - Aşağı kaydırınca başlık, arama adı, arama kutusu ve sıralama satırı parmakla
 *    birlikte yukarı kayıp soluklaşır; yalnızca kaynak çubuğu ekrana yapışır.
 *  - Kaynak çubuğundaki açılır (dropdown) buton, tüm kaynakları tek bir
 *    ModalBottomSheet içinde toplu + seçilebilir gösterir.
 *  - Kısa listelerde başlık gizlenmez; liste başına dönünce otomatik geri gelir.
 */
@Composable
fun MangaBrowseScreen(
    repository: MangaSourceRepository,
    initialQuery: String? = null,
    vm: MangaBrowseViewModel = viewModel(factory = MangaBrowseViewModel.Factory(repository)),
    onMangaClick: (MangaSource, MangaDetails) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()

    val addonViewModel: AddonViewModel = viewModel()
    val mangaViewModel: MangaViewModel = viewModel()

    val addonsList by addonViewModel.addonsList.collectAsState(initial = emptyList())
    val reposList by addonViewModel.reposList.collectAsState(initial = emptyList())
    val csPluginsList by addonViewModel.csPluginsList.collectAsState(initial = emptyList())

    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    // ── GitHub Vekil Sunucu ayarı (Manga Kaynakları sekmesindeki diyalog için) ──
    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val appSettings by settingsDataStore.settingsFlow.collectAsState(
        initial = com.kitsugi.animelist.data.settings.AppSettings()
    )
    val settingsScope = rememberCoroutineScope()

    LaunchedEffect(reposList) { addonViewModel.syncRepos(reposList) }
    LaunchedEffect(addonViewModel) {
        addonViewModel.onShowMessage = { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(mangaViewModel) {
        mangaViewModel.onShowMessage = { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(initialQuery) {
        if (vm.lastInitialQuery != initialQuery) {
            vm.lastInitialQuery = initialQuery
            if (!initialQuery.isNullOrBlank()) vm.search(initialQuery) else vm.search("")
        }
    }

    val accentColor = LocalKitsugiAccent.current
    val cols = rememberKitsugiGridColumns()

    // Snapshot delegated props → allow smart cast
    val query      = ui.searchQuery
    val states     = ui.sourceStates
    val selFilter  = ui.selectedSourceFilter
    val popular    = ui.popularMangas
    val hasNext    = ui.hasNextPage

    // Keep the actual source with each result. Display names are not identities:
    // Kotatsu and Mihon can expose the same title/name with different URL contracts.
    val mergedMangas = remember(states, selFilter, query, popular) {
        val raw = if (query.isNotBlank()) {
            states.filter { selFilter == null || it.source.stableSourceKey() == selFilter.stableSourceKey() }
                .flatMap { state -> state.mangas.map { state.source to it } }
                .sortedWith(
                    compareByDescending<Pair<MangaSource, MangaDetails>> {
                        com.kitsugi.animelist.data.manga.MangaTitleMatcher.getSimilarityScore(query, it.second.title)
                    }.thenByDescending { repository.getSourcePriority(it.first) }
                )
        } else {
            selFilter?.let { source -> popular.map { source to it } }.orEmpty()
        }
        raw.distinctBy { "${it.first.stableSourceKey()}|${it.second.url}" }
    }

    // Sıralama: "İlgi" (varsayılan, benzerlik puanı) / "A-Z" (başlık).
    var sortAlphabetical by rememberSaveable { mutableStateOf(false) }
    val orderedMangas = remember(mergedMangas, sortAlphabetical) {
        if (sortAlphabetical) mergedMangas.sortedBy { it.second.title.lowercase() } else mergedMangas
    }

    // +18 uyumu: yetişkin başlıklar ayar kapalıyken listelenmez.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleMangas = remember(orderedMangas, showAdultContent) {
        if (showAdultContent) orderedMangas else orderedMangas.filter { !it.second.isAdultContent }
    }

    val backdropUrl = visibleMangas.firstOrNull()?.second?.thumbnailUrl
    val sourcesEmpty = ui.sources.isEmpty() && states.isEmpty() && !ui.isLoadingPopular
    val isSearchMode = query.isNotBlank()
    val resultCount = visibleMangas.size
    val sourceCount = if (isSearchMode) states.size else ui.sources.size
    val loading = if (isSearchMode) states.any { it.isLoading } else ui.isLoadingPopular
    val hasNextPageToShow = if (!isSearchMode) hasNext else
        (selFilter != null && states.firstOrNull {
            it.source.stableSourceKey() == selFilter.stableSourceKey()
        }?.hasNextPage == true)

    // ── Katlanan başlık durumu (Stream ekranındaki HideOnScrollConnection ile aynı) ──
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }
    val headerOffsetPx = remember { mutableFloatStateOf(0f) }
    // İlk ölçüm gelene kadar makul bir tahmin: grid'in üst boşluğu bir kare zıplamasın.
    val initialCollapsiblePx = with(density) { 200.dp.toPx() }
    val collapsibleHeightPx = remember { mutableFloatStateOf(initialCollapsiblePx) }
    val stickyBarPx = with(density) { MangaStickyBarHeight.toPx() }
    val nestedScrollConnection = remember(gridState) {
        MangaHideOnScrollConnection(headerOffsetPx, collapsibleHeightPx, gridState)
    }

    // Liste tam başa dönerse başlığı tekrar aç.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 4 }
            .collect { atTop -> if (atTop) headerOffsetPx.floatValue = 0f }
    }
    // Yalnızca eşik geçişlerinde recomposition — her kaydırma karesinde değil.
    val collapsed by remember {
        derivedStateOf {
            collapsibleHeightPx.floatValue > 0f &&
                headerOffsetPx.floatValue >= collapsibleHeightPx.floatValue * 0.6f
        }
    }

    val revealHeader: () -> Unit = {
        settingsScope.launch {
            gridState.animateScrollToItem(0)
            headerOffsetPx.floatValue = 0f
        }
    }
    val changeSearchName: () -> Unit = {
        settingsScope.launch {
            gridState.animateScrollToItem(0)
            headerOffsetPx.floatValue = 0f
            searchFocusRequester.requestFocus()
        }
    }
    val topContentPadding = with(density) {
        (collapsibleHeightPx.floatValue + stickyBarPx).toDp()
    } + 10.dp

    val bg = KitsugiColors.Background

    Box(Modifier.fillMaxSize().background(bg)) {

        // ── Backdrop ──
        if (!backdropUrl.isNullOrBlank()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                isAdult = visibleMangas.firstOrNull()?.second?.isAdultContent == true,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f }
            )
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(bg.copy(alpha = 0.55f), bg))
        ))

        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            if (sourcesEmpty) {
                MangaBrowseTopBar(
                    resultCount = resultCount, sourceCount = sourceCount, accent = accentColor,
                    onBack = onBack, onManageSources = { showSettingsDialog = true }
                )
                MangaSearchBar(
                    query = query, accent = accentColor, focusRequester = searchFocusRequester,
                    onQueryChange = { vm.search(it) }
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptySourcesHint(onOpenSettings = { showSettingsDialog = true })
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // 1) Izgara — daima tek bir kaydırılabilir liste (kısa liste koruması için).
                    MangaResultsGrid(
                        gridState = gridState,
                        topPadding = topContentPadding,
                        cols = cols,
                        query = query,
                        selFilter = selFilter,
                        states = states,
                        visibleMangas = visibleMangas,
                        orderedCount = orderedMangas.size,
                        loading = loading,
                        hasNextPageToShow = hasNextPageToShow,
                        accent = accentColor,
                        onLoadNextPage = { vm.loadNextPage() },
                        onMangaClick = onMangaClick
                    )

                    // 2) Üst overlay: [katlanan kısım] + [yapışkan kaynak çubuğu].
                    //    Tek bir Column'ı -offset kadar yukarı taşımak, katlanan kısım
                    //    tamamen çıkınca çubuğu kendiliğinden y=0'a yapıştırır.
                    val headerTop = bg.copy(alpha = 0.88f)
                    val headerBottom = bg.copy(alpha = 0.97f)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationY = -headerOffsetPx.floatValue }
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { collapsibleHeightPx.floatValue = it.height.toFloat() }
                                .background(Brush.verticalGradient(listOf(headerTop, headerBottom)))
                                .graphicsLayer {
                                    val max = collapsibleHeightPx.floatValue.coerceAtLeast(1f)
                                    val progress = (headerOffsetPx.floatValue / max).coerceIn(0f, 1f)
                                    alpha = 1f - progress * 0.85f
                                }
                        ) {
                            MangaBrowseTopBar(
                                resultCount = resultCount, sourceCount = sourceCount, accent = accentColor,
                                onBack = onBack, onManageSources = { showSettingsDialog = true }
                            )
                            if (isSearchMode) {
                                MangaSearchNameRow(query = query, accent = accentColor, onChange = changeSearchName)
                            }
                            Spacer(Modifier.height(2.dp))
                            MangaSearchBar(
                                query = query, accent = accentColor, focusRequester = searchFocusRequester,
                                onQueryChange = { vm.search(it) }
                            )
                            Spacer(Modifier.height(6.dp))
                            MangaSortRow(
                                query = query,
                                sortAlphabetical = sortAlphabetical,
                                accent = accentColor,
                                onSortChange = { sortAlphabetical = it }
                            )
                            Spacer(Modifier.height(6.dp))
                        }

                        MangaStickySourceBar(
                            states = states,
                            sources = ui.sources,
                            isSearchMode = isSearchMode,
                            popularSize = popular.size,
                            isLoadingPopular = ui.isLoadingPopular,
                            selFilter = selFilter,
                            accent = accentColor,
                            collapsed = collapsed,
                            onSelect = { vm.selectSourceFilter(it) },
                            onOpenPicker = { showSourcePicker = true },
                            onRevealHeader = revealHeader
                        )
                    }
                }
            }
        }
    }

    // ── Kaynak seçici: tüm kaynaklar tek pencerede, toplu + seçilebilir ──
    if (showSourcePicker) {
        MangaSourcePickerSheet(
            states = states,
            sources = ui.sources,
            isSearchMode = isSearchMode,
            popularSize = popular.size,
            resultCount = resultCount,
            selectedKey = selFilter?.stableSourceKey()
                ?: if (isSearchMode) null else ui.sources.firstOrNull()?.stableSourceKey(),
            accent = accentColor,
            onSelect = { source ->
                vm.selectSourceFilter(source)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showSettingsDialog) {
        KitsugiAddonsSettingsDialog(
            addons = addonsList,
            initialDebridToken = addonViewModel.debridToken,
            repos = reposList,
            repoPlugins = addonViewModel.repoPluginsState,
            repoLoadingState = addonViewModel.repoLoadingState,
            csPlugins = csPluginsList,
            initialTab = 2,
            onAddAddon = { addonViewModel.addAddon(it) },
            onToggleAddon = { addon, enabled -> addonViewModel.toggleAddon(addon, enabled) },
            onDeleteAddon = { addonViewModel.deleteAddon(it) },
            onSaveDebridToken = { addonViewModel.saveDebridToken(it) },
            onAddRepo = { addonViewModel.addRepo(it) },
            onDeleteRepo = { addonViewModel.deleteRepo(it) },
            onFetchRepoPlugins = { addonViewModel.fetchRepoPlugins(it) },
            onInstallPlugin = { plugin, onResult -> addonViewModel.installPlugin(plugin, onResult) },
            onInstallAllPlugins = { repoUrl, repoName, plugins ->
                addonViewModel.installAllPlugins(repoUrl, repoName, plugins, addonsList, csPluginsList)
            },
            onUpdateAllPlugins = { repoUrl, repoName, plugins ->
                addonViewModel.updateAllPlugins(repoUrl, repoName, plugins, csPluginsList)
            },
            bulkInstallRepoUrl = addonViewModel.bulkInstallRepoUrl,
            bulkInstallRepoName = addonViewModel.bulkInstallRepoName,
            bulkInstallDone = addonViewModel.bulkInstallDone,
            bulkInstallTotal = addonViewModel.bulkInstallTotal,
            bulkInstallCurrentName = addonViewModel.bulkInstallCurrentName,
            bulkInstallResultMessage = addonViewModel.bulkInstallResultMessage,
            onClearBulkInstallResult = { addonViewModel.clearBulkInstallResult() },
            onToggleCsPlugin = { plugin, enabled -> addonViewModel.toggleCsPlugin(plugin, enabled) },
            onUninstallCsPlugin = { addonViewModel.uninstallCsPlugin(it) },
            onUninstallAllCsPlugins = { addonViewModel.uninstallAllCsPlugins() },
            // ── GitHub Vekil Sunucu (jsDelivr) — eksik geçilen parametreler ─────
            useGithubProxy = appSettings.useGithubProxy,
            onUseGithubProxyChanged = { enabled ->
                settingsScope.launch { settingsDataStore.setUseGithubProxy(enabled) }
            },
            mangaSources = mangaViewModel.mangaSources,
            onInstallMangaExtension = { mangaViewModel.installMangaExtension(it) },
            onDeleteMangaExtension = { mangaViewModel.deleteMangaExtension(it) },
            mangaRepos = mangaViewModel.mangaReposState,
            mangaRepoExtensions = mangaViewModel.mangaRepoExtensionsState,
            mangaRepoLoadingState = mangaViewModel.mangaRepoLoadingState,
            onAddMangaRepo = { mangaViewModel.addMangaRepo(it) },
            onDeleteMangaRepo = { mangaViewModel.deleteMangaRepo(it) },
            onFetchMangaRepo = { mangaViewModel.fetchMangaRepo(it) },
            onInstallMangaApk = { extension, onResult -> mangaViewModel.installMangaApk(extension, onResult) },
            onInstallAllMangaExtensions = { repoUrl, extensions -> mangaViewModel.installAllMangaExtensions(repoUrl, extensions) },
            onUpdateAllMangaExtensions = { repoUrl, extensions -> mangaViewModel.updateAllMangaExtensions(repoUrl, extensions) },
            mangaBulkInstallRepoUrl = mangaViewModel.mangaBulkInstallRepoUrl,
            mangaBulkInstallDone = mangaViewModel.mangaBulkInstallDone,
            mangaBulkInstallTotal = mangaViewModel.mangaBulkInstallTotal,
            mangaBulkInstallCurrentName = mangaViewModel.mangaBulkInstallCurrentName,
            onGetInstalledMangaVersionCode = { mangaViewModel.getInstalledVersionCode(it) },
            onGetInstalledMangaVersion = { mangaViewModel.getInstalledMangaExtensionVersion(it) },
            mangaSourceStateReport = mangaViewModel.mangaSourceStateReport,
            onGetMangaSourceHealthStatus = { mangaViewModel.getSourceHealthStatus(it) },
            onGetMangaSourceRuntimeStats = { mangaViewModel.getSourceRuntimeStats(it) },
            onGetMangaConfiguredDomain = { mangaViewModel.getConfiguredSourceDomain(it) },
            onGetMangaConfiguredBaseUrl = { mangaViewModel.getConfiguredSourceBaseUrl(it) },
            onGetMangaSourceUserAgent = { mangaViewModel.getSourceUserAgentOverride(it) },
            onGetMangaSourceSlowdownEnabled = { mangaViewModel.getSourceSlowdownEnabled(it) },
            onSetMangaSourceUserAgent = { source, ua -> mangaViewModel.setSourceUserAgentOverride(source, ua) },
            onSetMangaSourceSlowdownEnabled = { source, enabled -> mangaViewModel.setSourceSlowdownEnabled(source, enabled) },
            onSetMangaSourceDomain = { source, domain -> mangaViewModel.setSourceDomainOverride(source, domain) },
            onResetMangaSourceDiagnostics = { mangaViewModel.resetSourceDiagnostics(it) },
            onClearAllMangaSourceDiagnostics = { mangaViewModel.clearAllSourceDiagnostics() },
            onIsMangaSourceBusy = { mangaViewModel.isSourceBusy(it) },
            onQuickCheckMangaSource = { mangaViewModel.quickCheckSource(it) },
            onRefreshMangaSourceMirror = { mangaViewModel.refreshSourceMirror(it) },
            onClearMangaSourceMirror = { mangaViewModel.clearSourceMirror(it) },
            onForceCheckMangaUpdates = { mangaViewModel.forceCheckMangaUpdates(it) },
            untrustedRepoToConfirm = mangaViewModel.untrustedRepoToConfirm,
            untrustedSignatureToConfirm = mangaViewModel.untrustedSignatureToConfirm,
            onConfirmUntrustedRepo = { mangaViewModel.addMangaRepo(it, force = true) },
            onDismissUntrustedRepo = { mangaViewModel.clearUntrustedRepoConfirm() },
            onConfirmUntrustedSignature = { ext, hash -> mangaViewModel.installMangaApk(ext, force = true) },
            onDismissUntrustedSignature = { mangaViewModel.clearUntrustedSignatureConfirm() },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

// ─── Katlanan başlık: nested scroll bağlantısı ────────────────────────────────

/**
 * Kaydırma deltasını başlık ofsetine çeviren bağlantı. 0 = tam görünür,
 * [maxOffset] = katlanan kısım tamamen gizli (yapışkan çubuk y=0'da).
 *
 * StreamScreenContent.HideOnScrollConnection ile birebir aynı mantık: bar
 * yüksekliği bir anda değişmediği için liste zıplaması olmaz, parmakla akar.
 * Kısa listelerde (kaydırılamayan içerik) gizleme tüketilmez.
 */
private class MangaHideOnScrollConnection(
    private val offset: MutableFloatState,
    private val maxOffset: MutableFloatState,
    private val gridState: LazyGridState
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        val max = maxOffset.floatValue
        if (dy == 0f || max <= 0f) return Offset.Zero
        if (dy < 0f && !gridState.canScrollForward) return Offset.Zero
        val target = (offset.floatValue - dy).coerceIn(0f, max)
        val delta = target - offset.floatValue
        if (delta == 0f) return Offset.Zero
        offset.floatValue = target
        return Offset(0f, -delta)
    }
}

// ─── Kompakt üst bar ──────────────────────────────────────────────────────────

@Composable
private fun MangaBrowseTopBar(
    resultCount: Int,
    sourceCount: Int,
    accent: Color,
    onBack: () -> Unit,
    onManageSources: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(MangaCompactTopBarHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.55f),
                contentColor = KitsugiColors.TextPrimary
            ),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(R.string.action_back),
                tint = KitsugiColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.inline_discover_manga_eb24f62),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.ui2_manga_search_summary, resultCount, sourceCount),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onManageSources, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Extension,
                stringResource(R.string.manga_browse_manage_sources),
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Arama adı satırı ─────────────────────────────────────────────────────────

@Composable
private fun MangaSearchNameRow(query: String, accent: Color, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = KitsugiColors.Surface.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, KitsugiColors.Border.copy(alpha = 0.45f))
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Translate, null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.manga_browse_search_name, query),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 190.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onChange, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(
                stringResource(R.string.manga_browse_change_search_name),
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

// ─── Arama kutusu ─────────────────────────────────────────────────────────────

@Composable
private fun MangaSearchBar(
    query: String,
    accent: Color,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.manga_ara), color = KitsugiColors.TextMuted) },
        leadingIcon = {
            Icon(Icons.Rounded.Search, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(R.string.temizle),
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = KitsugiColors.Border,
            cursorColor = accent,
            focusedTextColor = KitsugiColors.TextPrimary,
            unfocusedTextColor = KitsugiColors.TextPrimary,
            focusedContainerColor = KitsugiColors.Surface.copy(alpha = 0.5f),
            unfocusedContainerColor = KitsugiColors.Surface.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .focusRequester(focusRequester)
    )
}

// ─── Sıralama satırı ──────────────────────────────────────────────────────────

@Composable
private fun MangaSortRow(
    query: String,
    sortAlphabetical: Boolean,
    accent: Color,
    onSortChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.manga_browse_sort_label),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(8.dp))
        MangaSortPill(
            label = stringResource(R.string.manga_browse_sort_relevance),
            selected = !sortAlphabetical,
            accent = accent,
            onClick = { onSortChange(false) }
        )
        Spacer(Modifier.width(6.dp))
        MangaSortPill(
            label = stringResource(R.string.manga_browse_sort_az),
            selected = sortAlphabetical,
            accent = accent,
            onClick = { onSortChange(true) }
        )
        Spacer(Modifier.weight(1f))
        if (query.isNotBlank()) {
            Text(
                stringResource(R.string.manga_browse_searched_name, query),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}

@Composable
private fun MangaSortPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent.copy(alpha = 0.20f) else KitsugiColors.Surface.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.5f) else KitsugiColors.Border.copy(alpha = 0.4f)
        ),
        modifier = Modifier.height(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                label,
                color = if (selected) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// ─── Yapışkan kaynak çubuğu (çipler + açılır buton) ──────────────────────────

/**
 * Video İzle keşfet ekranındaki AddonChipsRow'un manga karşılığı: uzun bir
 * yatay çip listesi yerine tek, derli toplu bir satır. Çipler satırı kaydırılır,
 * açılır buton sabit kalır ve tüm kaynakları bottom sheet'te topluca gösterir.
 */
@Composable
private fun MangaStickySourceBar(
    states: List<MangaSourceFetchState>,
    sources: List<MangaSource>,
    isSearchMode: Boolean,
    popularSize: Int,
    isLoadingPopular: Boolean,
    selFilter: MangaSource?,
    accent: Color,
    collapsed: Boolean,
    onSelect: (MangaSource?) -> Unit,
    onOpenPicker: () -> Unit,
    onRevealHeader: () -> Unit
) {
    val bg = KitsugiColors.Background
    val stickyAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0.985f else 0.92f,
        animationSpec = tween(KitsugiMotion.normalMillis),
        label = "manga_sticky_alpha"
    )
    val stickyElevation by animateDpAsState(
        targetValue = if (collapsed) 6.dp else 0.dp,
        animationSpec = tween(KitsugiMotion.normalMillis),
        label = "manga_sticky_elevation"
    )

    Surface(color = Color.Transparent, shadowElevation = stickyElevation) {
        Column(Modifier.fillMaxWidth().background(bg.copy(alpha = stickyAlpha))) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Katlanmışken arama kutusuna geri dönüş kısayolu (animasyonlu).
                AnimatedVisibility(
                    visible = collapsed,
                    enter = fadeIn(tween(KitsugiMotion.stickyHeaderEnterMillis)) +
                        scaleIn(tween(KitsugiMotion.stickyHeaderEnterMillis), initialScale = 0.7f),
                    exit = fadeOut(tween(KitsugiMotion.stickyHeaderExitMillis)) +
                        scaleOut(tween(KitsugiMotion.stickyHeaderExitMillis), targetScale = 0.7f)
                ) {
                    IconButton(onClick = onRevealHeader, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Search,
                            stringResource(R.string.manga_browse_back_to_search),
                            tint = accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchMode) {
                        val totalCount = states.sumOf { it.mangas.size }
                        val anyLoading = states.any { it.isLoading }
                        item {
                            MangaChip(
                                label = stringResource(R.string.tumu),
                                count = totalCount,
                                isLoading = anyLoading,
                                error = null,
                                isSelected = selFilter == null,
                                accent = accent,
                                isAll = true,
                                onClick = { onSelect(null) }
                            )
                        }
                        items(states, key = { it.source.stableSourceKey() }) { st ->
                            MangaChip(
                                label = st.source.name,
                                count = st.mangas.size,
                                isLoading = st.isLoading,
                                error = st.error,
                                errorCode = st.errorCode,
                                isSelected = selFilter?.stableSourceKey() == st.source.stableSourceKey(),
                                accent = accent,
                                isAll = false,
                                dotColor = sourceDotColor(st.source.name),
                                onClick = {
                                    onSelect(
                                        if (selFilter?.stableSourceKey() == st.source.stableSourceKey()) null
                                        else st.source
                                    )
                                }
                            )
                        }
                    } else {
                        items(sources, key = { it.stableSourceKey() }) { src ->
                            val isSelected = selFilter?.stableSourceKey() == src.stableSourceKey()
                            MangaChip(
                                label = src.name,
                                count = if (isSelected) popularSize else 0,
                                isLoading = isSelected && isLoadingPopular,
                                error = null,
                                isSelected = isSelected,
                                accent = accent,
                                isAll = false,
                                dotColor = sourceDotColor(src.name),
                                onClick = { onSelect(src) }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // ── Açılır (dropdown) buton: kaynakların tamamı tek pencerede ──
                Surface(
                    onClick = onOpenPicker,
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            stringResource(R.string.manga_browse_show_all_sources),
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f))
        }
    }
}

// ─── Kaynak seçici bottom sheet ───────────────────────────────────────────────

private data class MangaSourcePickerItem(
    val source: MangaSource,
    val count: Int,
    val isLoading: Boolean,
    val error: String?,
    val errorCode: String?
)

/**
 * StreamScreenContent.AddonChipsRow'daki kaynak picker'ın manga uyarlaması:
 * başlık + özet + kaynak içi arama + "Tümü" + tüm kaynaklar tek listede,
 * durumları ve sonuç sayılarıyla birlikte seçilebilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaSourcePickerSheet(
    states: List<MangaSourceFetchState>,
    sources: List<MangaSource>,
    isSearchMode: Boolean,
    popularSize: Int,
    resultCount: Int,
    selectedKey: String?,
    accent: Color,
    onSelect: (MangaSource?) -> Unit,
    onDismiss: () -> Unit
) {
    var pickerQuery by remember { mutableStateOf("") }
    val entries = remember(states, sources, isSearchMode, popularSize, selectedKey) {
        if (isSearchMode) {
            states.map {
                MangaSourcePickerItem(
                    source = it.source,
                    count = it.mangas.size,
                    isLoading = it.isLoading,
                    error = it.error,
                    errorCode = it.errorCode
                )
            }
        } else {
            sources.map { src ->
                MangaSourcePickerItem(
                    source = src,
                    count = if (selectedKey == src.stableSourceKey()) popularSize else 0,
                    isLoading = false,
                    error = null,
                    errorCode = null
                )
            }
        }
    }
    val visible = remember(entries, pickerQuery) {
        entries.filter { it.source.name.contains(pickerQuery.trim(), ignoreCase = true) }
    }
    val allCount = if (isSearchMode) states.sumOf { it.mangas.size } else popularSize

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Background
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.manga_browse_sources_title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.ui2_manga_search_summary, resultCount, entries.size),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pickerQuery,
                onValueChange = { pickerQuery = it },
                singleLine = true,
                placeholder = {
                    Text(stringResource(R.string.manga_browse_search_sources), color = KitsugiColors.TextMuted)
                },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = accent) },
                trailingIcon = {
                    if (pickerQuery.isNotEmpty()) {
                        IconButton(onClick = { pickerQuery = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.temizle))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = KitsugiColors.Border.copy(alpha = 0.5f)
                )
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (isSearchMode) {
                    item(key = "all") {
                        MangaSourcePickerRow(
                            name = stringResource(R.string.tumu),
                            status = stringResource(R.string.manga_browse_result_count, allCount),
                            count = allCount,
                            selected = selectedKey == null,
                            dotColor = accent,
                            accent = accent,
                            onClick = { onSelect(null) }
                        )
                    }
                }
                items(visible, key = { it.source.stableSourceKey() }) { entry ->
                    val status = when {
                        entry.isLoading -> stringResource(R.string.araniyor)
                        entry.error != null -> stringResource(R.string.manga_browse_source_error) +
                            (entry.errorCode?.let { " · $it" } ?: "")
                        else -> stringResource(R.string.manga_browse_result_count, entry.count)
                    }
                    MangaSourcePickerRow(
                        name = entry.source.name,
                        status = status,
                        count = entry.count,
                        selected = selectedKey == entry.source.stableSourceKey(),
                        dotColor = sourceDotColor(entry.source.name),
                        accent = accent,
                        onClick = { onSelect(entry.source) }
                    )
                }
                if (visible.isEmpty()) {
                    item(key = "no_match") {
                        Text(
                            stringResource(R.string.manga_browse_no_source_match),
                            modifier = Modifier.padding(16.dp),
                            color = KitsugiColors.TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MangaSourcePickerRow(
    name: String,
    status: String,
    count: Int,
    selected: Boolean,
    dotColor: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    status,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$count", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Premium capsule chip (kompakt, video keşfet stili) ───────────────────────

@Composable
fun MangaChip(
    label: String,
    count: Int,
    isLoading: Boolean,
    error: String?,
    isSelected: Boolean,
    accent: Color,
    isAll: Boolean,
    onClick: () -> Unit,
    errorCode: String? = null,
    dotColor: Color = accent
) {
    val inf = rememberInfiniteTransition(label = "p")
    val pulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a"
    )
    val bg = when {
        isSelected -> accent.copy(alpha = 0.30f)
        isLoading -> accent.copy(alpha = pulse * 0.22f)
        count > 0 -> accent.copy(alpha = 0.12f)
        error != null -> Color.Red.copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    val outline = if (isSelected) accent.copy(alpha = 0.55f)
        else KitsugiColors.Border.copy(alpha = 0.35f)

    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(BorderStroke(1.dp, outline), RoundedCornerShape(999.dp))
            .tvClickable(shape = RoundedCornerShape(999.dp)) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            isLoading -> CircularProgressIndicator(Modifier.size(10.dp), color = accent, strokeWidth = 1.5.dp)
            isAll -> Icon(
                Icons.Rounded.Extension, null,
                tint = if (isSelected) accent else KitsugiColors.TextSecondary,
                modifier = Modifier.size(13.dp)
            )
            error != null -> Box(Modifier.size(7.dp).clip(CircleShape).background(Color.Red.copy(0.7f)))
            else -> Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        }
        Text(
            label,
            color = if (isSelected || count > 0) KitsugiColors.TextPrimary else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected || count > 0) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (count > 0 && !isAll) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$count", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (error != null && errorCode != null) {
            Text(
                errorCode,
                color = Color.Red.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Kaynak adına göre kararlı renk: çip ve picker noktaları aynı dili kullanır. */
private fun sourceDotColor(name: String): Color {
    val palette = listOf(
        KitsugiColors.AccentPink,
        KitsugiColors.AccentPurple,
        KitsugiColors.AccentBlue,
        KitsugiColors.AccentGreen,
        KitsugiColors.AccentOrange,
        KitsugiColors.AccentTeal,
        KitsugiColors.AccentYellow,
        KitsugiColors.AccentIndigo
    )
    return palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
}

// ─── Sonuç ızgarası ───────────────────────────────────────────────────────────

@Composable
private fun MangaResultsGrid(
    gridState: LazyGridState,
    topPadding: Dp,
    cols: Int,
    query: String,
    selFilter: MangaSource?,
    states: List<MangaSourceFetchState>,
    visibleMangas: List<Pair<MangaSource, MangaDetails>>,
    orderedCount: Int,
    loading: Boolean,
    hasNextPageToShow: Boolean,
    accent: Color,
    onLoadNextPage: () -> Unit,
    onMangaClick: (MangaSource, MangaDetails) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            loading && visibleMangas.isEmpty() -> {
                items(8) { MangaCardShimmer() }
            }
            !loading && visibleMangas.isEmpty() -> {
                item(span = { GridItemSpan(cols) }) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.SearchOff, null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.sonuc_bulunamadi), color = KitsugiColors.TextSecondary)
                        }
                    }
                }
            }
            else -> {
                items(visibleMangas, key = { "${it.first.stableSourceKey()}|${it.second.url}" }) { (source, manga) ->
                    MangaCard(manga, accent) { onMangaClick(source, manga) }
                }
                if (query.isNotBlank()) {
                    val loadingStates = states.filter {
                        it.isLoading && (selFilter == null || selFilter.stableSourceKey() == it.source.stableSourceKey())
                    }
                    items(loadingStates, key = { "sk_${it.source.stableSourceKey()}" }) {
                        SkeletonCard(it.source.name, accent)
                    }
                }
                if (query.isBlank() || selFilter != null) {
                    item(span = { GridItemSpan(cols) }) {
                        LaunchedEffect(orderedCount) { onLoadNextPage() }
                        AnimatedVisibility(visible = hasNextPageToShow) {
                            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                                KitsugiPlasmaLoader(size = 32.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Skeleton card ────────────────────────────────────────────────────────────

@Composable
private fun SkeletonCard(sourceName: String, accent: Color) {
    val inf = rememberInfiniteTransition(label = "sk")
    val sh by inf.animateFloat(0.25f, 0.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sh")
    Card(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft.copy(0.6f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(sh)))
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))).padding(8.dp)
            ) {
                Column {
                    Text(stringResource(R.string.araniyor), color = KitsugiColors.TextSecondary, fontSize = 11.sp)
                    Text(sourceName, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ─── Manga card ───────────────────────────────────────────────────────────────

@Composable
private fun MangaCard(manga: MangaDetails, accent: Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).tvClickable(shape = RoundedCornerShape(12.dp)) { onClick() },
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            com.kitsugi.animelist.ui.components.KitsugiNsfwImage(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                isAdult = manga.isAdultContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.93f)))).padding(8.dp)
            ) {
                Column {
                    Text(manga.title, color = KitsugiColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!manga.source.isNullOrBlank())
                        Text(manga.source, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Empty sources hint ───────────────────────────────────────────────────────

@Composable
private fun EmptySourcesHint(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Extension, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.inline_no_manga_extensions_have_been_e92d6be), color = KitsugiColors.TextSecondary, fontWeight = FontWeight.Medium)
            KitsugiButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.inline_edit_extensions_134b588), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Manga card shimmer ───────────────────────────────────────────────────────

@Composable
private fun MangaCardShimmer() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.SurfaceSoft),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha * 0.1f))
        ) {
            // Text placeholders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.15f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.15f))
                )
            }
        }
    }
}

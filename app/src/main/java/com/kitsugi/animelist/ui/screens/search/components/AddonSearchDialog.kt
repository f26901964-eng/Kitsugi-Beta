package com.kitsugi.animelist.ui.screens.search.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.ui.components.*
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

/**
 * Eklenti Arama Diyaloğu — Ana Arama (SearchScreen) ile birebir aynı tema:
 * - Aynı KitsugiCosmicSearchBar (56dp, 16dp radius, Surface, focus accent border)
 * - Aynı CosmicCompanionButton
 * - Aynı shimmer (KitsugiShimmerSearchResultList / KitsugiShimmerResponsiveMediaRow)
 * - Aynı kartlar (KitsugiExploreMediaCard) ve aynı başlık/“Tümünü Gör” stili
 * - Aynı hata/empty state bileşenleri
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonSearchDialog(
    onDismissRequest: () -> Unit,
    onSeeAllAddonSection: ((apiName: String, title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var exploreApiName by rememberSaveable { mutableStateOf<String?>(null) }
    val exploreApi = remember(exploreApiName) {
        if (exploreApiName == null) null
        else APIHolder.allProviders.firstOrNull { it.name == exploreApiName }
    }
    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Pair<MainAPI, SearchResponse>>>(emptyList()) }
    var activeApis by remember { mutableStateOf<List<MainAPI>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var activeDetailItem by remember { mutableStateOf<Pair<MainAPI, SearchResponse>?>(null) }

    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleResults = remember(searchResults, showAdultContent) {
        if (showAdultContent) searchResults
        else searchResults.filter { !isAddonAdultResponse(it.second) }
    }

    // Load active addons
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = KitsugiDatabase.getDatabase(context.applicationContext)
                val enabledPlugins = db.csPluginDao().getEnabledPlugins()
                for (plugin in enabledPlugins) {
                    try {
                        CsPluginLoader.loadExtension(context, plugin.id)
                    } catch (e: Exception) {
                        Log.e("AddonSearchDialog", "Failed to load extension ${plugin.name}: ${e.message}")
                    }
                }
                val enabledIds = enabledPlugins.map { it.id }
                activeApis = APIHolder.allProviders.filter { api ->
                    CsStreamRunner.isApiEnabledFor(api, enabledIds)
                }
            } catch (e: Exception) {
                Log.e("AddonSearchDialog", "Error fetching active extensions: ${e.message}")
            }
        }
    }

    val performSearch = {
        if (query.isNotBlank()) {
            CsPluginStatusTracker.clear()
            CsStreamRunner.clearUnsupportedMethodsCache()
            isLoading = true
            hasSearched = true
            keyboardController?.hide()
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val apiList = activeApis
                    val list = mutableListOf<Pair<MainAPI, SearchResponse>>()
                    supervisorScope {
                        val jobs = apiList.map { api ->
                            async {
                                try {
                                    val searchRes = CsStreamRunner.searchWithCfRetry(api, query)
                                    synchronized(list) {
                                        searchRes.forEach { list.add(api to it) }
                                    }
                                } catch (e: Exception) {
                                    Log.e("AddonSearchDialog", "Search failed for provider ${api.name}: ${e.message}")
                                }
                            }
                        }
                        jobs.forEach {
                            try { it.await() } catch (_: Exception) {}
                        }
                    }
                    list
                }
                searchResults = results
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KitsugiColors.Background
        ) {
            // Shimmer provider — ana aramadakiyle aynı tek kaynaklı animasyon
            KitsugiShimmerProvider(active = isLoading) {
                val lazyListState = com.kitsugi.animelist.ui.utils.rememberRetainedLazyListState(
                    contentReady = visibleResults.isNotEmpty() || !hasSearched
                )
                val scopeFab = rememberCoroutineScope()
                val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 1 } }
                val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val groupedResults = remember(visibleResults) {
                    visibleResults.groupBy { it.first }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ── Başlık — Ana Arama'daki headlineLarge ile birebir aynı
                        item(key = "header") {
                            Spacer(modifier = Modifier.height(28.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(onClick = onDismissRequest) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.geri),
                                        tint = KitsugiColors.TextPrimary
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.eklenti_portali),
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // ── Arama çubuğu — KitsugiCosmicSearchBar ile birebir aynı
                        item(key = "search_bar") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                KitsugiCosmicSearchBar(
                                    query = query,
                                    onQueryChange = { query = it },
                                    onSearch = { performSearch() },
                                    onClearQuery = {
                                        query = ""
                                        searchResults = emptyList()
                                        hasSearched = false
                                        keyboardController?.hide()
                                    },
                                    placeholder = stringResource(R.string.inline_search_all_installed_extensions_590b4c9),
                                    modifier = Modifier.weight(1f)
                                )
                                CosmicCompanionButton(
                                    onClick = {
                                        if (query.isNotBlank()) performSearch()
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Rounded.Search,
                                            contentDescription = stringResource(R.string.ara),
                                            tint = KitsugiColors.TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // ── Yükleniyor — Aynı shimmer listesi
                        if (isLoading) {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KitsugiPlasmaLoader(size = 48.dp)
                                }
                                // Shimmer placeholder raflar — ana aramadakiyle aynı
                                Spacer(modifier = Modifier.height(8.dp))
                                KitsugiShimmerSearchResultList(itemCount = 4)
                            }
                        } else {
                            if (hasSearched && visibleResults.isNotEmpty()) {
                                // Her eklenti grubu — MultiSearchSection tarzı yatay raflar, aynı kartlar
                                items(
                                    items = groupedResults.keys.toList(),
                                    key = { "grp_${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" }
                                ) { api ->
                                    val itemsForApi = groupedResults[api] ?: emptyList()
                                    if (itemsForApi.isNotEmpty()) {
                                        val cardWidth = if (isLandscape) 260.dp else 180.dp
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                        ) {
                                            // Başlık — MultiSearchSection ile aynı: logo + başlık + Tümünü Gör
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Eklenti logosu yoksa Extension ikonu kullan (ana uygulamadaki fallback)
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(accentColor.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Extension,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = api.name,
                                                    color = KitsugiColors.TextPrimary,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${itemsForApi.size}",
                                                    color = KitsugiColors.TextMuted,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            // Yatay kart listesi — aynı kart, aynı boşluk, aynı snap
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(
                                                    items = itemsForApi,
                                                    key = { (_, response) -> "${api.name}_${response.url}" }
                                                ) { (_, response) ->
                                                    val result = response.toAddonSearchResult()
                                                    KitsugiExploreMediaCard(
                                                        result = result,
                                                        onClick = { activeDetailItem = api to response },
                                                        modifier = Modifier.width(cardWidth),
                                                        forceVertical = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (hasSearched && visibleResults.isEmpty()) {
                                item(key = "empty_search") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        KitsugiEmptyState(
                                            title = stringResource(R.string.sonuc_bulunamadi),
                                            subtitle = stringResource(R.string.inline_no_results_found_03b621d),
                                            icon = Icons.Rounded.SearchOff
                                        )
                                    }
                                }
                            } else {
                                // ── Idle: yüklü eklentiler — Aynı Surface kart stili, aynı typography
                                item(key = "installed_header") {
                                    Text(
                                        text = stringResource(R.string.inline_installed_extensions_select_one_to_9595e4d),
                                        color = KitsugiColors.TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                                if (activeApis.isEmpty()) {
                                    item(key = "no_plugins") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            KitsugiEmptyState(
                                                title = stringResource(R.string.henuz_icerik_yok),
                                                subtitle = stringResource(R.string.inline_no_installed_extensions_found_bd1713b),
                                                icon = Icons.Filled.Extension
                                            )
                                        }
                                    }
                                } else {
                                    // Yüklü eklenti kartları — aynı neon glow + 16dp radius + Surface
                                    items(
                                        items = activeApis,
                                        key = { "api_${java.io.File(it.sourcePlugin ?: "").nameWithoutExtension}_${it.name}" }
                                    ) { api ->
                                        val shape = RoundedCornerShape(16.dp)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 5.dp)
                                                .kitsugiNeonGlow(shape)
                                                .tvClickable(shape = shape, onClick = { exploreApiName = api.name }),
                                            shape = shape,
                                            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(accentColor.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Extension,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = api.name,
                                                        color = KitsugiColors.TextPrimary,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = api.mainUrl,
                                                        color = KitsugiColors.TextMuted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(R.string.inline_explore_2aeb544),
                                                    color = accentColor,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(90.dp)) }
                    }

                    // ── Scroll to Top FAB — Ana arama ile aynı
                    KitsugiScrollToTopFab(
                        visible = showFab,
                        isLandscape = false,
                        bottomBarScrollState = null,
                        fallbackBottomBarVisible = true,
                        onClick = {
                            scopeFab.launch { lazyListState.animateScrollToItem(0) }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }

    if (exploreApi != null) {
        val capturedApi = exploreApi!!
        AddonExploreDialog(
            api = capturedApi,
            onDismissRequest = { exploreApiName = null },
            onSeeAllClick = if (onSeeAllAddonSection != null) {
                { title, mainPageData, horizontalImages, initialItems ->
                    onSeeAllAddonSection(
                        capturedApi.name, title, mainPageData, horizontalImages, initialItems
                    )
                }
            } else null
        )
    }

    activeDetailItem?.let { (api, response) ->
        KitsugiAddonDetailDialog(
            api = api,
            url = response.url,
            onDismissRequest = { activeDetailItem = null }
        )
    }
}

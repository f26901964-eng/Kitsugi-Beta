@file:Suppress("UNUSED_PARAMETER")
package com.kitsugi.animelist.ui.screens.search.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.errors.PlatformErrorCodeHint
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ─────────────────────────────────────────────────────────────────────────────
// In-memory cache
// ─────────────────────────────────────────────────────────────────────────────

object AddonExploreCache {
    private val cache = BoundedCache<String, List<HomePageList>>("addon.explore", 12)
    fun get(apiName: String): List<HomePageList>? = cache[apiName]
    fun put(apiName: String, data: List<HomePageList>) { cache[apiName] = data }
    fun clear(apiName: String) { cache.remove(apiName) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonExplorePage(
    api: MainAPI,
    onBackClick: () -> Unit,
    onSeeAllClick: ((title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResponse>>(emptyList()) }
    var isHomeLoading by remember { mutableStateOf(false) }
    var homeLists by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var activeDetailUrl by remember { mutableStateOf<String?>(null) }

    // +18 UYUMU (2026-10-14): eklenti portalı da uygulamanın geri kalanıyla aynı
    // göster/gizle sözleşmesine uyar — yetişkin sonuçlar ayar kapalıyken elenir.
    val showAdultContent = com.kitsugi.animelist.ui.theme.LocalShowAdultContent.current
    val visibleSearchResults = remember(searchResults, showAdultContent) {
        if (showAdultContent) searchResults else searchResults.filter { !isAddonAdultResponse(it) }
    }


    BackHandler {
        when {
            activeDetailUrl != null -> activeDetailUrl = null
            hasSearched -> {
                searchQuery = ""
                searchResults = emptyList()
                hasSearched = false
                keyboardController?.hide()
            }
            else -> onBackClick()
        }
    }

    val loadHomeFeed = { forceRefresh: Boolean ->
        isHomeLoading = true
        scope.launch {
            val cached = if (forceRefresh) null else AddonExploreCache.get(api.name)
            if (cached != null) {
                homeLists = cached
                isHomeLoading = false
            } else {
                val dataList = withContext(Dispatchers.IO) {
                    val list = mutableListOf<HomePageList>()
                    try {
                        // v2.4.239: vitrin (Öne Çıkan) yüklemesi de gate'li — CF korumalı
                        // eklentide ana sayfa istekleri clearance'siz başlayıp 403 yemesin.
                        runCatching { CsStreamRunner.ensureCfClearance(api) }
                        for (pageData in api.mainPage) {
                            try {
                                val req = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                                api.getMainPage(1, req)?.let { list.addAll(it.items) }
                            } catch (t: Throwable) {
                                Log.e("AddonExploreDialog", "Page load failed: ${pageData.name} — ${t.message}")
                                CsPluginStatusTracker.recordSkip(
                                    api.name,
                                    t.localizedMessage ?: "${pageData.name}: ${t.javaClass.simpleName}",
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("AddonExploreDialog", "mainPage list failed: ${t.message}")
                        CsPluginStatusTracker.recordSkip(
                            api.name,
                            t.localizedMessage ?: t.javaClass.simpleName,
                        )
                    }
                    list
                }
                homeLists = dataList
                AddonExploreCache.put(api.name, dataList)
                isHomeLoading = false
            }
        }
    }

    LaunchedEffect(api.name) { loadHomeFeed(false) }

    val performSearch = {
        if (searchQuery.isNotBlank()) {
            isSearchLoading = true
            hasSearched = true
            keyboardController?.hide()
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    try {
                        // v2.4.239: eklenti sayfası araması da CF gate'li — çıplak isim
                        // sorgusundan önce clearance çözülür (sağlıklı sitede 0 ms);
                        // boş sonuç + challenge kanıtı halinde sabırlı yeniden deneme yapılır.
                        runCatching { CsStreamRunner.ensureCfClearance(api) }
                        CsStreamRunner.searchWithCfRetry(api, searchQuery)
                    } catch (t: Throwable) {
                        CsPluginStatusTracker.recordSkip(
                            api.name,
                            t.localizedMessage ?: t.javaClass.simpleName,
                        )
                        emptyList()
                    }
                }
                searchResults = results
                isSearchLoading = false
            }
        }
    }

    // Vitrin öğeleri — ana sayfa (Keşfet) vitriniyle aynı model üzerinden beslenir.
    val heroResults = remember(homeLists, showAdultContent) {
        homeLists.asSequence()
            .map { shelf -> shelf.list.filter { showAdultContent || !isAddonAdultResponse(it) } }
            .firstOrNull { it.isNotEmpty() }
            ?.take(10)
            ?.map { it.toAddonSearchResult() } ?: emptyList()
    }
    val isBlocked = CsPluginStatusTracker.isBlocked(api.name)
    val errorCode = CsPluginStatusTracker.getErrorCode(api.name)
    val isCfProtected = remember(api.name) {
        CsStreamRunner.CF_PROTECTED_PLUGINS.contains(api.name) || api.usesWebView
    }

    Surface(
            modifier = Modifier.fillMaxSize(),
            color = KitsugiColors.Background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Üst bar (ana arama sayfasıyla aynı hiza) ───────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = api.name,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (hasSearched) {
                            stringResource(R.string.plugin_search_results, visibleSearchResults.size)
                        } else {
                            stringResource(R.string.plugin_search_results, homeLists.sumOf { it.list.size })
                        },
                        color = KitsugiColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    IconButton(onClick = { loadHomeFeed(true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.yenile),
                            tint = KitsugiColors.TextMuted
                        )
                    }
                }

                // ── Arama çubuğu: ana arama sayfasıyla BİREBİR aynı bileşen ─
                // (KitsugiCosmicSearchBar: 16 dp köşe, aynı yükseklik/hiza,
                //  aynı yazı tipi ve temizle/ara butonları.)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    com.kitsugi.animelist.ui.components.KitsugiCosmicSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { performSearch() },
                        onClearQuery = {
                            searchQuery = ""
                            searchResults = emptyList()
                            hasSearched = false
                            keyboardController?.hide()
                        },
                        placeholder = stringResource(R.string.plugin_search_in_addon, api.name),
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Content area ───────────────────────────────────────────
                when {
                    isHomeLoading && homeLists.isEmpty() -> {
                        // Initial loading spinner
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KitsugiPlasmaLoader(size = 48.dp)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.api_name_yukleniyor, api.name), color = KitsugiColors.TextMuted, fontSize = 14.sp)
                            }
                        }
                    }

                    hasSearched -> {
                        // Arama sonuçları — ana arama sayfasıyla AYNI satır düzeni.
                        // (Eskiden 3'lü poster ızgarasıydı; dikey/yatay geçişte
                        //  kart boyutları bozuluyordu. Artık tek sütun, kompakt,
                        //  56×80 posterli yatay satırlar.)
                        when {
                            isSearchLoading -> {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) { KitsugiPlasmaLoader(size = 48.dp) }
                            }
                            visibleSearchResults.isEmpty() -> {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.inline_no_results_found_for_1_3ed9f56, searchQuery),
                                        color = KitsugiColors.TextMuted,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item(key = "search_summary") {
                                        Text(
                                            stringResource(R.string.inline_1_s_2_s_results_1656e6f, searchQuery, visibleSearchResults.size),
                                            color = KitsugiColors.TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                        )
                                    }
                                    items(visibleSearchResults, key = { it.url }) { item ->
                                        AddonResultRow(
                                            title = item.name,
                                            // Ana aramadaki satırla aynı alt bilgi dili:
                                            // tür etiketi (Film / Dizi / Anime …)
                                            subtitle = item.type.toAddonTypeLabel(),
                                            imageUrl = item.posterUrl,
                                            apiName = api.name,
                                            qualityLabel = item.quality?.name,
                                            year = item.safeYear(),
                                            isAdult = isAddonAdultResponse(item),
                                            onClick = { activeDetailUrl = item.url },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Home feed — ana sayfa (Keşfet) ile birebir aynı bileşenler:
                        // KitsugiHeroSection vitrini + KitsugiHorizontalMediaSection rafları.
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // Vitrin (hero carousel) — ana sayfadakiyle aynı sunum
                            item(key = "hero") {
                                com.kitsugi.animelist.ui.components.KitsugiHeroSection(
                                    items = heroResults,
                                    alreadyInList = { false },
                                    onInfoClick = { item ->
                                        item.cs3Url?.let { activeDetailUrl = it }
                                    },
                                    titleLanguage = titleLanguage,
                                    scoreFormat = scoreFormat,
                                    hideScores = hideScores,
                                    blurAdultMedia = blurAdultMedia,
                                    isVisible = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Warning pill (CF / blocked)
                            if (isBlocked || isCfProtected || errorCode != null) {
                                item(key = "warning") {
                                    AddonStatusPill(
                                        isBlocked = isBlocked,
                                        blockReason = if (isBlocked || errorCode != null)
                                            CsPluginStatusTracker.getErrorMessage(api.name) ?: "Ağ hatası"
                                        else null,
                                        errorCode = errorCode,
                                    )
                                }
                            }

                            // Empty state
                            if (homeLists.isEmpty() && !isHomeLoading) {
                                item(key = "empty") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.kesfet_icerigi_yuklenemedi), color = KitsugiColors.TextMuted)
                                    }
                                }
                            }

                            // Kategori rafları — ana sayfadaki yatay medya raflarıyla aynı:
                            // aynı kutucuk (KitsugiExploreMediaCard), aynı kaydırma/snap
                            // mekaniği, aynı "Tümünü Gör" sözleşmesi.
                            items(homeLists, key = { "cat_${homeLists.indexOf(it)}_${it.name}" }) { homeList ->
                                val rowResults = remember(homeList, showAdultContent) {
                                    homeList.list.map { it.toAddonSearchResult() }
                                        .filter { showAdultContent || !it.isAdult }
                                }
                                if (rowResults.isNotEmpty()) {
                                    val matchingPage = remember(homeList.name) {
                                        api.mainPage.firstOrNull { it.name == homeList.name }
                                    }
                                    com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection(
                                        title = homeList.name,
                                        results = rowResults,
                                        isLoading = isHomeLoading,
                                        alreadyInList = { false },
                                        onItemClick = { item ->
                                            item.cs3Url?.let { activeDetailUrl = it }
                                        },
                                        onSeeAllClick = if (onSeeAllClick != null && matchingPage != null) {
                                            {
                                                onSeeAllClick(
                                                    homeList.name,
                                                    matchingPage.data,
                                                    matchingPage.horizontalImages,
                                                    homeList.list
                                                )
                                            }
                                        } else null,
                                        titleLanguage = titleLanguage,
                                        scoreFormat = scoreFormat,
                                        hideScores = hideScores,
                                        blurAdultMedia = blurAdultMedia
                                    )
                                    Spacer(modifier = Modifier.height(26.dp))
                                }
                            }
                        }
                    }
                }

            }
        }

    // ── Detail Dialog (opens when a content card is tapped) ──────────────────
    activeDetailUrl?.let { detailUrl ->
        KitsugiAddonDetailDialog(
            api = api,
            url = detailUrl,
            onDismissRequest = { activeDetailUrl = null }
        )
    }
}

/**
 * Geriye dönük uyumluluk köprüsü
 */
@Composable
fun AddonExploreDialog(
    api: MainAPI,
    onDismissRequest: () -> Unit,
    onSeeAllClick: ((title: String, mainPageData: String, horizontalImages: Boolean, initialItems: List<SearchResponse>) -> Unit)? = null
) {
    AddonExplorePage(
        api = api,
        onBackClick = onDismissRequest,
        onSeeAllClick = onSeeAllClick
    )
}

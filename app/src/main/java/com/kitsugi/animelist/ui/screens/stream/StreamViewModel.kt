package com.kitsugi.animelist.ui.screens.stream

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import com.kitsugi.animelist.data.cloudstream.CsStreamRunner
import com.kitsugi.animelist.data.cloudstream.CsPluginStatusTracker
import com.kitsugi.animelist.data.cloudstream.CloudflareBlockException
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.remote.AddonStreamClient
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.repository.EpisodeStreamFilter
import com.kitsugi.animelist.data.repository.StreamSorter
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.UnsafeSSL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.cloudstream.diag.CsTrace

/**
 * ViewModel for the stream picker screen.
 *
 * All network fetch work lives here so that it survives:
 *  - the app being sent to the background
 *  - screen rotation / configuration changes
 *  - navigating away and back (within the same Activity back-stack)
 *
 * A "fetch key" tracks which (malId, aniListId, episode, season) combination
 * is currently loaded. [startFetch] is a no-op if the same combination is
 * already fetched or in progress, so the UI can call it safely on every
 * recomposition without triggering a redundant scan.
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val TAG = "StreamViewModel"

    // ── Public state ──────────────────────────────────────────────────────────

    private val _addonStates = MutableStateFlow<List<AddonFetchState>>(emptyList())
    val addonStates: StateFlow<List<AddonFetchState>> = _addonStates.asStateFlow()

    private val _isResolvingId = MutableStateFlow(false)
    val isResolvingId: StateFlow<Boolean> = _isResolvingId.asStateFlow()

    private val _idResolveFailed = MutableStateFlow(false)
    val idResolveFailed: StateFlow<Boolean> = _idResolveFailed.asStateFlow()

    private val _imdbId = MutableStateFlow<String?>(null)
    val imdbId: StateFlow<String?> = _imdbId.asStateFlow()

    /** Non-null when a Cloudflare WebView dialog should be shown. */
    private val _webViewDialogState = MutableStateFlow<WebViewDialogState?>(null)
    val webViewDialogState: StateFlow<WebViewDialogState?> = _webViewDialogState.asStateFlow()

    // ── Internal guards ───────────────────────────────────────────────────────

    /**
     * Identifies the active fetch. Format: "malId:aniListId:season:episode".
     * Null = nothing fetched yet.
     */
    @Volatile private var currentFetchKey: String? = null

    /** Whether a fetch coroutine is currently running for [currentFetchKey]. */
    @Volatile private var isFetchInProgress = false

    private val stateUpdateMutex = Mutex()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts a stream fetch for the given parameters.
     *
     * Safe to call on every recomposition – returns immediately when the same
     * key is already loaded or a fetch is already in progress.
     */
    fun startFetch(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        episode: Int,
        season: Int,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        cs3Url: String? = null,
        cs3ApiName: String? = null,
        isMovie: Boolean = false
    ) {
        val newKey = "$malId:$aniListId:$tmdbId:$isMovie:$season:$episode:$startYear:$cs3ApiName:$cs3Url:${title.hashCode()}:${alternativeTitles.hashCode()}"

        // ── Cache hit: same combination, data already present ─────────────────
        if (newKey == currentFetchKey) {
            Log.d(TAG, "startFetch: cache hit for key=$newKey — skipping")
            return
        }

        // ── New combination: reset everything and start fresh ─────────────────
        Log.d(TAG, "startFetch: new key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        isFetchInProgress = true

        // Clear per-session plugin blocklist so every new anime retries all plugins
        CsPluginStatusTracker.clear()
        // Clear "unsupported method" cache — önceki animeden kalan NotImplementedError işaretleri
        // temizlenmezse o eklentinin search() metodu bir sonraki anime için hiç denenmez (0 sonuç).
        CsStreamRunner.clearUnsupportedMethodsCache()

        _addonStates.value = emptyList()
        _isResolvingId.value = true
        _idResolveFailed.value = false
        _imdbId.value = null

        viewModelScope.launch {
            fetchStreams(
                malId = malId,
                aniListId = aniListId,
                tmdbId = tmdbId,
                episode = episode,
                season = season,
                title = title,
                alternativeTitles = alternativeTitles,
                startYear = startYear,
                cs3Url = cs3Url,
                cs3ApiName = cs3ApiName,
                isMovie = isMovie
            )
            isFetchInProgress = false
        }
    }

    /**
     * Dismisses the WebView captcha dialog and retries the affected plugin.
     */
    fun onWebViewDismissed(
        dismissedPluginId: String,
        dismissedDisplayName: String,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        season: Int,
        episode: Int,
        malId: Int? = null,
        aniListId: Int? = null,
        tmdbId: Int? = null,
        isMovie: Boolean = false
    ) {
        CookieManager.getInstance().flush()
        _webViewDialogState.value = null

        // Clear the plugin's failure state so it can retry
        CsPluginStatusTracker.clearPluginStatus(dismissedPluginId)

        // Reset addon state to "loading" so the user sees a spinner
        updateAddonStateSync(dismissedDisplayName, isLoading = true, streams = emptyList(), error = null)

        viewModelScope.launch {
            val db = KitsugiDatabase.getDatabase(context)
            val csPlugin = db.csPluginDao().getEnabledPlugins()
                .firstOrNull { it.id == dismissedPluginId } ?: return@launch
            try {
                val apis = withContext(Dispatchers.IO) {
                    CsPluginLoader.loadExtension(context, csPlugin.id)
                }
                // Tracker anahtarları api.name'le tutulur — yalnızca plugin.id temizlemek
                // "Doğrula" sonrası tekrar denemede eklentinin hâlâ bloklu sanılmasına
                // yol açıyordu. Bu yüzden id, eklenti adı ve yüklenen tüm api adları temizlenir.
                CsPluginStatusTracker.clearPluginStatus(csPlugin.id)
                CsPluginStatusTracker.clearPluginStatus(csPlugin.name)
                apis.forEach { CsPluginStatusTracker.clearPluginStatus(it.name) }
                val csStreams = mutableListOf<StreamSource>()
                for (api in apis) {
                    val streams = CsStreamRunner.getStreams(
                        api = api, title = title, alternativeTitles = alternativeTitles,
                        year = startYear, season = season, episode = episode,
                        malId = malId, aniListId = aniListId, tmdbId = tmdbId,
                        isMovie = isMovie
                    )
                    csStreams.addAll(streams)
                }
                val diagMsg = if (csStreams.isEmpty()) {
                    CsPluginStatusTracker.getErrorMessage(csPlugin.name)
                        ?: CsPluginStatusTracker.getErrorMessage(apis.firstOrNull()?.name ?: csPlugin.name)
                } else null
                updateAddonStateSync(
                    dismissedDisplayName,
                    isLoading = false,
                    streams = csStreams,
                    error = if (csStreams.isEmpty()) (diagMsg?.let { "⚠️ $it" } ?: "Bu anime için akış bulunamadı") else null
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateAddonStateSync(
                    dismissedDisplayName,
                    isLoading = false,
                    error = "Hata: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * Resolves the Cloudflare-gated plugin URL and signals the UI to open a WebView.
     */
    fun onVerifyPlugin(addonDisplayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = KitsugiDatabase.getDatabase(context)
            val allCsPlugins = db.csPluginDao().getEnabledPlugins()
            val pluginName = addonDisplayName.removePrefix("⚡ ").trim()
            val plugin = allCsPlugins.firstOrNull { it.name.equals(pluginName, ignoreCase = true) }
                ?: return@launch

            val cs3File = java.io.File(context.filesDir, "cs_extensions/${plugin.id}.cs3")
            if (!CsPluginLoader.loadedPlugins.containsKey(cs3File.absolutePath)) {
                CsPluginLoader.loadExtension(context, plugin.id)
            }
            val api = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                it.sourcePlugin == cs3File.absolutePath ||
                    it.name.equals(pluginName, ignoreCase = true)
            }
            val url = api?.mainUrl
            if (url.isNullOrBlank()) {
                Log.w(TAG, "onVerifyPlugin: Provider not found for $pluginName")
                return@launch
            }
            withContext(Dispatchers.Main) {
                _webViewDialogState.value = WebViewDialogState(
                    url = url,
                    displayName = addonDisplayName,
                    pluginId = plugin.id
                )
            }
        }
    }

    // ── Core fetch ────────────────────────────────────────────────────────────

    private suspend fun fetchStreams(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        episode: Int,
        season: Int,
        title: String,
        alternativeTitles: List<String>,
        startYear: Int?,
        cs3Url: String? = null,
        cs3ApiName: String? = null,
        isMovie: Boolean = false
    ) {
        val directUrl = cs3Url?.trim()?.takeIf { it.isNotBlank() }
        val directApiName = cs3ApiName?.trim()?.takeIf { it.isNotBlank() }
        if (directUrl != null && directApiName != null) {
            _addonStates.value = listOf(AddonFetchState(directApiName, isLoading = true))
            var directFailure: String? = null
            try {
                val db = KitsugiDatabase.getDatabase(context)
                val csPlugin = db.csPluginDao().getEnabledPlugins()
                    .firstOrNull {
                        it.id.equals(directApiName, ignoreCase = true) ||
                            it.name.equals(directApiName, ignoreCase = true)
                    }
                if (csPlugin != null) {
                    withContext(Dispatchers.IO) {
                        CsPluginLoader.loadExtension(context, csPlugin.id)
                    }
                }
                val activeApi = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                    it.name.equals(directApiName, ignoreCase = true)
                }
                if (activeApi != null) {
                    val resolved = CsStreamRunner.getStreamsForUrl(
                        api = activeApi,
                        url = directUrl,
                        season = season,
                        episode = episode,
                        isMovie = isMovie
                    )
                    if (resolved.isNotEmpty()) {
                        _isResolvingId.value = false
                        updateAddonStateSync(directApiName, isLoading = false, streams = resolved)
                        return
                    }
                    directFailure = "the selected provider URL returned no streams"
                } else {
                    directFailure = "the selected provider is not loaded"
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                directFailure = e.localizedMessage ?: e.javaClass.simpleName
            }

            // A provider/search-result URL is an optimization, not a reason to stop the
            // shared title-first search. If that one route is stale, mismatched for this
            // episode, or temporarily unavailable, continue through every enabled plugin.
            Log.w(TAG, "[$directApiName] Direct source fetch failed ($directFailure); falling back to all enabled providers")
            CsTrace.warn(directApiName, "direct-url", "$directFailure; falling back to shared title/episode search")
            _addonStates.value = emptyList()
        }

        // ── 1. Resolve IDs ────────────────────────────────────────────────────
        val realAniListId = when {
            aniListId != null && aniListId >= 100_000_000 -> aniListId - 100_000_000
            aniListId != null && aniListId > 0 -> aniListId
            else -> null
        }

        // Kitsu stableId offset: 300_000_000 + kitsuId
        // AniList stableId offset: 100_000_000 + aniListId
        // MAL ID ise offset'siz doğrudan değer
        val kitsuNumericId: Int? = when {
            malId != null && malId >= 300_000_000 -> malId - 300_000_000
            else -> null
        }
        val realMalId: Int? = when {
            malId != null && malId >= 300_000_000 -> null  // Kitsu kaynaklı — MAL ID değil
            malId != null && malId >= 100_000_000 -> null  // AniList offset — MAL ID değil
            malId != null && malId > 0 -> malId
            else -> null
        }

        val resolvedIds = KitsugiIdResolver.resolveIds(realMalId, realAniListId, tmdbId, kitsuId = kitsuNumericId)
        _isResolvingId.value = false

        val resolvedImdbId = resolvedIds.imdbId
        val resolvedKitsuId = resolvedIds.kitsuId ?: kitsuNumericId
        if (resolvedImdbId == null && resolvedKitsuId == null) {
            // ID çözümlenemedi — yine de CS plugin'leri başlık bazlı aramaya devam edecek.
            // idResolveFailed sadece bilgilendirme amaçlı set ediliyor, bloklamıyor.
            Log.w(TAG, "ID çözümleme başarısız — title-based fallback ile devam ediliyor. title='$title'")
            _idResolveFailed.value = true
        }
        _imdbId.value = resolvedImdbId

        val stremioLookup = com.kitsugi.animelist.data.remote.StremioVideoId.forContent(
            isMovie = isMovie,
            imdbId = resolvedImdbId,
            kitsuId = resolvedKitsuId,
            season = season,
            episode = episode
        )
        val imdbVideoId = stremioLookup.imdbVideoId
        val kitsuVideoId = stremioLookup.kitsuVideoId
        val contentType = stremioLookup.contentType

        data class StreamTask(val addonName: String, val manifestUrl: String, val videoId: String)

        val db = KitsugiDatabase.getDatabase(context)
        val addonClient = AddonStreamClient()
        val allAddons = db.managedAddonDao().getEnabledAddons()

        // ── 2. Build Stremio tasks ────────────────────────────────────────────
        val tasks = mutableListOf<StreamTask>()
        for (addon in allAddons) {
            val prefixesRaw = addon.idPrefixes
            val prefixList = if (!prefixesRaw.isNullOrBlank()) {
                try {
                    Gson().fromJson(prefixesRaw, Array<String>::class.java).filter { it.isNotBlank() }
                } catch (_: Exception) { emptyList() }
            } else emptyList()

            if (prefixList.isEmpty()) {
                if (kitsuVideoId != null && addon.supportsStreamResource(contentType, kitsuVideoId))
                    tasks += StreamTask(addon.name, addon.manifestUrl, kitsuVideoId)
                if (imdbVideoId != null && addon.supportsStreamResource(contentType, imdbVideoId))
                    tasks += StreamTask(addon.name, addon.manifestUrl, imdbVideoId)
            } else {
                val supportsKitsu = prefixList.any { it == "kitsu:" }
                val supportsImdb  = prefixList.any { it == "tt" }
                if (supportsKitsu && kitsuVideoId != null && addon.supportsStreamResource(contentType, kitsuVideoId)) {
                    tasks += StreamTask(addon.name, addon.manifestUrl, kitsuVideoId)
                    if (supportsImdb && imdbVideoId != null && addon.supportsStreamResource(contentType, imdbVideoId))
                        tasks += StreamTask(addon.name, addon.manifestUrl, imdbVideoId)
                } else if (supportsImdb && imdbVideoId != null && addon.supportsStreamResource(contentType, imdbVideoId)) {
                    tasks += StreamTask(addon.name, addon.manifestUrl, imdbVideoId)
                }
            }
        }

        if (tasks.isNotEmpty()) {
            val uniqueAddons = tasks.map { it.addonName }.distinct()
            _addonStates.value = uniqueAddons.map { name ->
                AddonFetchState(addonName = name, manifestUrl = "")
            }
        }

        // ── 3. Run Stremio + CS plugin fetches in parallel ────────────────────
        kotlinx.coroutines.supervisorScope {
            // Stremio tasks
            val stremioJobs = tasks.map { task ->
                launch {
                    try {
                        val streams = addonClient.fetchStreams(task.manifestUrl, contentType, task.videoId)
                        val sources = streams.map { item ->
                            val nameText  = item.name  ?: "Bilinmeyen Akış"
                            val titleText = item.title ?: "İsimsiz Dosya"
                            val parsedQuality = StreamSorter.parseQualityFromTitle("$titleText $nameText")
                            StreamSource(
                                addonName    = task.addonName,
                                name         = nameText,
                                title        = titleText,
                                url          = item.url,
                                infoHash     = item.infoHash,
                                fileIndex    = item.fileIdx ?: item.fileIndex,
                                trackers     = item.trackers,
                                requestHeaders = item.behaviorHints?.proxyHeaders?.request,
                                quality      = parsedQuality,
                                qualityValue = StreamSorter.parseQualityValue(parsedQuality)
                            )
                        }
                        val filteredSources = EpisodeStreamFilter.filterForEpisode(sources, season, episode)
                        val addonIdx = tasks.map { it.addonName }.distinct().indexOf(task.addonName)
                        if (addonIdx >= 0) {
                            stateUpdateMutex.withLock {
                                val list = _addonStates.value.toMutableList()
                                val existing = list[addonIdx]
                                val seen = existing.streams
                                    .mapNotNull { it.infoHash?.lowercase() ?: it.url?.lowercase() }
                                    .toHashSet()
                                val fresh = filteredSources.filter { s ->
                                    val key = s.infoHash?.lowercase() ?: s.url?.lowercase() ?: return@filter true
                                    seen.add(key)
                                }
                                list[addonIdx] = existing.copy(
                                    isLoading = false,
                                    streams   = StreamSorter.sort(existing.streams + fresh)
                                )
                                _addonStates.value = list
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        val addonIdx = tasks.map { it.addonName }.distinct().indexOf(task.addonName)
                        if (addonIdx >= 0) {
                            stateUpdateMutex.withLock {
                                val list = _addonStates.value.toMutableList()
                                list[addonIdx] = list[addonIdx].copy(
                                    isLoading = false,
                                    error     = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                                )
                                _addonStates.value = list
                            }
                        }
                    }
                }
            }

            // CS plugin tasks
            val installedCsPlugins = withContext(Dispatchers.IO) { db.csPluginDao().getAllPlugins() }
            val enabledCsPlugins = installedCsPlugins.filter { it.enabled }
            CsTrace.setInventory(installedCsPlugins.map {
                "${it.name} v${it.version} (id=${it.id}) enabled=${it.enabled} ${it.downloadUrl}"
            })
            CsTrace.session(
                "Arama: '$title' S${season}E${episode} movie=$isMovie yil=$startYear mal=$malId anilist=$aniListId tmdb=$tmdbId — " +
                    "CloudStream ${enabledCsPlugins.size}/${installedCsPlugins.size} etkin"
            )
            if (enabledCsPlugins.isEmpty() && installedCsPlugins.isNotEmpty()) {
                val message = context.getString(com.kitsugi.animelist.R.string.cs_all_plugins_disabled_error)
                Log.w(TAG, "${installedCsPlugins.size} kurulu CloudStream eklentisinin tamamı devre dışı")
                CsTrace.warn("CloudStream", "inventory", message)
                stateUpdateMutex.withLock {
                    _addonStates.value = _addonStates.value + AddonFetchState(
                        addonName = "CloudStream video sağlayıcıları",
                        isLoading = false,
                        error = message
                    )
                }
            }
            if (enabledCsPlugins.isNotEmpty()) {
                val csPlaceholders = enabledCsPlugins.map { plugin ->
                    AddonFetchState(
                        addonName   = "⚡ ${plugin.name}",
                        manifestUrl = plugin.downloadUrl,
                        isLoading   = true
                    )
                }
                stateUpdateMutex.withLock {
                    _addonStates.value = _addonStates.value + csPlaceholders
                }
                Log.d(TAG, "CS eklentileri paralel yükleniyor: ${enabledCsPlugins.map { it.name }}")

                for (plugin in enabledCsPlugins) {
                    launch {
                        val csDisplayName = "⚡ ${plugin.name}"
                        try {
                            // Tracker kayıtları api.name ile tutulur; plugin.id tek başına yetersizdir.
                            if (CsPluginStatusTracker.isBlocked(plugin.id) || CsPluginStatusTracker.isBlocked(plugin.name)) {
                                val reason = CsPluginStatusTracker.getErrorMessage(plugin.name)
                                    ?: CsPluginStatusTracker.getErrorMessage(plugin.id)
                                    ?: "Tekrarlı hata"
                                Log.w(TAG, "[$csDisplayName] Engellendi: $reason")
                                CsTrace.warn(plugin.name, "skip", "Engellendi: $reason")
                                updateAddonState(csDisplayName, isLoading = false, error = "Engellendi: $reason")
                                return@launch
                            }

                            Log.d(TAG, "[$csDisplayName] loadExtension başlatılıyor...")
                            CsTrace.info(plugin.name, "load", "loadExtension başlıyor")
                            val apis = withContext(Dispatchers.IO) {
                                CsPluginLoader.loadExtension(context, plugin.id)
                            }
                            if (apis.isEmpty()) {
                                Log.e(TAG, "[$csDisplayName] 0 API döndü — DEX yüklenemedi")
                                CsTrace.error(plugin.name, "load", "0 API döndü — DEX/manifest yüklenemedi")
                                updateAddonState(csDisplayName, isLoading = false, error = "Eklenti yüklenemedi (DEX/manifest hatası)")
                                return@launch
                            }

                            val csStreams = mutableListOf<StreamSource>()
                            for (api in apis) {
                                val streams = CsStreamRunner.getStreams(
                                    api              = api,
                                    title            = title,
                                    alternativeTitles = alternativeTitles,
                                    year             = startYear,
                                    season           = season,
                                    episode          = episode,
                                    malId            = realMalId ?: resolvedIds.malId,
                                    aniListId        = realAniListId ?: resolvedIds.aniListId,
                                    tmdbId           = tmdbId ?: resolvedIds.tmdbId,
                                    isMovie          = isMovie
                                )
                                csStreams.addAll(streams)
                            }
                            Log.d(TAG, "[$csDisplayName] Toplam ${csStreams.size} stream bulundu")
                            CsTrace.info(plugin.name, "result", "Toplam ${csStreams.size} stream bulundu")
                            // Akış boşsa SESSİZ "akış bulunamadı" yerine tracker'daki GERÇEK
                            // hatayı göster (DNS/CF/timeout/parse) — kullanıcı nedenini görsün.
                            val diagMsg = if (csStreams.isEmpty()) {
                                CsPluginStatusTracker.getErrorMessage(plugin.name)
                                    ?: CsPluginStatusTracker.getErrorMessage(apis.firstOrNull()?.name ?: plugin.name)
                            } else null
                            updateAddonState(
                                csDisplayName, isLoading = false, streams = csStreams,
                                error = diagMsg?.let { "⚠️ $it" }
                            )

                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Log.e(TAG, "[$csDisplayName] KRİTİK HATA: ${e.javaClass.simpleName}: ${e.message}", e)
                            CsTrace.error(plugin.name, "pipeline", "KRİTİK HATA: ${e.javaClass.simpleName}: ${e.message}", e)

                            val isWebViewUiFailure = e is android.view.WindowManager.BadTokenException ||
                                e.cause is android.view.WindowManager.BadTokenException ||
                                e.message?.contains("token null", ignoreCase = true) == true ||
                                e.message?.contains("BadTokenException", ignoreCase = true) == true ||
                                e.message?.contains("activity running", ignoreCase = true) == true

                            val isCfBlock = e is CloudflareBlockException
                            val isTimeout = e is java.util.concurrent.TimeoutException ||
                                e.message?.contains("zaman aşımı", ignoreCase = true) == true ||
                                e.message?.contains("timeout", ignoreCase = true) == true

                            when {
                                isCfBlock -> {
                                    Log.w(TAG, "[$csDisplayName] Açık bot/WAF challenge tespit edildi")
                                    updateAddonState(
                                        csDisplayName,
                                        isLoading = false,
                                        error = "🔐 Bot/WAF doğrulaması gerekiyor (Doğrula butonuna bas)",
                                        requiresVerification = true
                                    )
                                }
                                isTimeout -> {
                                    Log.w(TAG, "[$csDisplayName] Ağ/site zaman aşımı; CF/WAF olarak sınıflandırılmadı")
                                    updateAddonState(
                                        csDisplayName,
                                        isLoading = false,
                                        error = "⏱ İstek zaman aşımına uğradı; ağ veya sağlayıcı yanıt vermedi."
                                    )
                                }
                                isWebViewUiFailure -> {
                                    Log.w(TAG, "[$csDisplayName] WebView doğrulama penceresi açılamadı")
                                    updateAddonState(
                                        csDisplayName,
                                        isLoading = false,
                                        error = "Doğrulama penceresi açılamadı; ekrandan yeniden deneyin."
                                    )
                                }
                                else -> updateAddonState(
                                    csDisplayName,
                                    isLoading = false,
                                    error = "Hata: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun updateAddonState(
        addonName: String,
        isLoading: Boolean,
        streams: List<StreamSource> = emptyList(),
        error: String? = null,
        requiresVerification: Boolean = false
    ) {
        stateUpdateMutex.withLock {
            withContext(Dispatchers.Main) {
                _addonStates.value = _addonStates.value.toMutableList().also { list ->
                    val i = list.indexOfFirst { it.addonName == addonName }
                    if (i >= 0) {
                        list[i] = list[i].copy(
                            isLoading = isLoading,
                            streams   = streams,
                            error     = if (error == null && streams.isEmpty()) "Bu anime için akış bulunamadı" else error,
                            requiresVerification = requiresVerification
                        )
                    }
                }
            }
        }
    }

    private fun updateAddonStateSync(
        addonName: String,
        isLoading: Boolean,
        streams: List<StreamSource> = emptyList(),
        error: String? = null,
        requiresVerification: Boolean = false
    ) {
        val current = _addonStates.value.toMutableList()
        val i = current.indexOfFirst { it.addonName == addonName }
        if (i >= 0) {
            current[i] = current[i].copy(
                isLoading = isLoading,
                streams   = streams,
                error     = error,
                requiresVerification = requiresVerification
            )
            _addonStates.value = current
        }
    }

    @OptIn(Prerelease::class, UnsafeSSL::class)
    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "onCleared: Watch screen closed. Cancelling all ongoing network calls for stream resolution.")
        try {
            com.lagradost.cloudstream3.app.baseClient.dispatcher.cancelAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel app.baseClient calls: ${e.message}")
        }
        try {
            com.lagradost.cloudstream3.insecureApp.baseClient.dispatcher.cancelAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel insecureApp.baseClient calls: ${e.message}")
        }
        try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.dispatcher.cancelAll()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to cancel KitsugiHttpClient calls: ${e.message}")
        }
    }
}

/** State for the Cloudflare WebView verification dialog. */
data class WebViewDialogState(
    val url: String,
    val displayName: String,
    val pluginId: String
)

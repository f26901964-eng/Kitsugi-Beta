package com.kitsugi.animelist.ui.screens.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.SimklSyncManager
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
// SimklApiClient: discovery için artık kullanılmıyor; sadece SimklSyncManager üzerinden watchlist sync'te kullanılır
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.kitsugi.animelist.model.MediaType

/**
 * Keşfet hatalarının türünü belirler — UI'da platforma özgü aksiyon butonları göstermek için.
 * - [TmdbError]: TMDB API anahtarı geçersiz veya eksik → Ayarlara yönlendir
 * - [AniListError]: AniList servis hatası veya token sorunları → Giriş yap
 * - [MalError]: MAL/Jikan servis hatası → Giriş yap
 * - [None]: Hata yok
 */
enum class ExploreErrorType { None, TmdbError, AniListError, MalError }

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = JikanApiClient(
        aniListToken = ExternalAuthManager.getAniListToken(application)
    )
    // simklApiClient: discovery/trending için KALDIRILDI — sadece SimklSyncManager üzerinden watchlist sync
    // tmdbApiClient: settings'ten tmdbUserApiKey yüklenince rebuild edilir
    private var tmdbApiClient = TmdbApiClient()
    private val settingsDataStore = SettingsDataStore(application)
    private var tmdbUserApiKeyState = ""

    // Her platform için ayrı cache — platform geçişinde yeniden fetch yapılmaz
    private val platformCache = Companion.platformCache
    // Başarıyla yüklenen platformların seti — boş veri dönsün, takılmaması için
    private val loadedPlatforms = Companion.loadedPlatforms

    private var isFallbackInProgress = false
    private var isFirstLoad = true
    private var showAdultContentState = false
    private var loadJob: Job? = null
    private val sourceJobs = mutableMapOf<ExplorePlatform, Job>()
    private var requestGeneration = 0

    var allSourceStates by mutableStateOf<Map<ExplorePlatform, ExploreSourceState>>(emptyMap())
        private set

    private fun cancelLoads() {
        requestGeneration++
        loadJob?.cancel()
        loadJob = null
        sourceJobs.values.forEach { it.cancel() }
        sourceJobs.clear()
        isLoading = false
    }

    /**
     * Kalıcı (persisted) kaynak seçimi. Uygulama kapatılıp açıldığında Keşfet ekranı
     * kullanıcının son seçtiği kaynakla açılır — varsayılan: Tümü (6 kaynak birlikte).
     */
    var selectedPlatform by mutableStateOf(ExplorePlatform.ALL)
        private set

    private var platformRestored = false

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Hatanın kaynağını belirtir — UI'da platforma özgü yönlendirme butonu göstermek için */
    var exploreErrorType by mutableStateOf(ExploreErrorType.None)
        private set

    var isShowingCachedData by mutableStateOf(false)
        private set

    // TMDB entegrasyon durumu — settingsFlow'dan reaktif olarak güncellenir
    private var tmdbEnabledState = true
    private var tmdbModernHomeEnabledState = false
    private var tmdbEnrichContinueWatchingState = true

    private val initialPayload: ExplorePayload?
        get() = platformCache[selectedPlatform]

    var topAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.topAnime ?: emptyList())
        private set

    var airingAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.airingAnime ?: emptyList())
        private set

    var upcomingAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.upcomingAnime ?: emptyList())
        private set

    var topManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.topManga ?: emptyList())
        private set

    var publishingManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.publishingManga ?: emptyList())
        private set

    var trendingAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.trendingAnime ?: emptyList())
        private set

    var movieAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.movieAnime ?: emptyList())
        private set

    var seasonalAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.seasonalAnime ?: emptyList())
        private set

    var airingSoonAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.airingSoonAnime ?: emptyList())
        private set

    var trendingManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.trendingManga ?: emptyList())
        private set

    var newlyAddedAnime by mutableStateOf<List<JikanSearchResult>>(initialPayload?.newlyAddedAnime ?: emptyList())
        private set

    var newlyAddedManga by mutableStateOf<List<JikanSearchResult>>(initialPayload?.newlyAddedManga ?: emptyList())
        private set

    /** TMDB'ye özgü "Yakında Yayında" içerikleri — ExploreCategoryType.UPCOMING_MEDIA_TMDB ile sayfalanır */
    var upcomingMediaTmdb by mutableStateOf<List<JikanSearchResult>>(initialPayload?.upcomingMediaTmdb ?: emptyList())
        private set

    var heroIndex by mutableIntStateOf(0)

    // ── Simkl Kullanıcı Listeleri (NyanTV HomeSections.kt referans) ──────────────
    /** İzlemeye devam et — filmler (Simkl status=watching, isMovie=true) */
    var simklContinueMovies by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklContinueMovies ?: emptyList())
        private set

    /** Planladıklarım — filmler (Simkl status=plantowatch, isMovie=true) */
    var simklPlannedMovies by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklPlannedMovies ?: emptyList())
        private set

    /** İzlemeye devam et — diziler/anime (Simkl status=watching, isMovie=false) */
    var simklContinueSeries by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklContinueSeries ?: emptyList())
        private set

    /** Planladıklarım — diziler/anime (Simkl status=plantowatch, isMovie=false) */
    var simklPlannedSeries by mutableStateOf<List<JikanSearchResult>>(initialPayload?.simklPlannedSeries ?: emptyList())
        private set

    val isDataLoaded: Boolean
        get() = (selectedPlatform == ExplorePlatform.ALL && allSourceStates.values.any { it.payload != null }) ||
                selectedPlatform in loadedPlatforms ||
                topAnime.isNotEmpty() || airingAnime.isNotEmpty() || trendingAnime.isNotEmpty()

    init {
        viewModelScope.launch {
            // Kaynak seçimi kalıcılığı: açılışta kullanıcının son seçtiği kaynağa dön.
            // Ayar akışı collection'ından ÖNCE okunmalı ki ilk yükleme yanlış platformla yapılmasın.
            if (!platformRestored) {
                platformRestored = true
                val restored = runCatching {
                    settingsDataStore.lastExplorePlatformFlow.firstOrNull()?.let { name ->
                        ExplorePlatform.entries.firstOrNull { it.name == name }
                    }
                }.getOrNull() ?: ExplorePlatform.ALL
                if (restored != selectedPlatform) {
                    selectedPlatform = restored
                    if (restored == ExplorePlatform.ALL) {
                        loadAllSources(forceRefresh = false)
                    } else {
                        platformCache[restored]?.let { cached ->
                            applyPayload(cached)
                            loadedPlatforms.add(restored)
                        }
                    }
                }
            }

            settingsDataStore.settingsFlow.collect { settings ->
                val adultChanged = showAdultContentState != settings.showAdultContent
                showAdultContentState = settings.showAdultContent

                // TMDB toggle değişiklikleri
                val userKey = settings.tmdbUserApiKey
                val tmdbChanged = tmdbEnabledState != settings.tmdbEnabled ||
                    tmdbModernHomeEnabledState != settings.tmdbModernHomeEnabled ||
                    tmdbEnrichContinueWatchingState != settings.tmdbEnrichContinueWatching ||
                    userKey != tmdbUserApiKeyState
                tmdbEnabledState = settings.tmdbEnabled
                tmdbModernHomeEnabledState = settings.tmdbModernHomeEnabled
                tmdbEnrichContinueWatchingState = settings.tmdbEnrichContinueWatching

                // tmdbUserApiKey değişince TmdbApiClient'i yeniden oluştur
                if (userKey != tmdbUserApiKeyState) {
                    tmdbUserApiKeyState = userKey
                    tmdbApiClient = TmdbApiClient(userApiKey = userKey)
                }

                if (adultChanged || tmdbChanged || isFirstLoad) {
                    val wasFirstLoad = isFirstLoad
                    isFirstLoad = false
                    
                    if (wasFirstLoad) {
                        // Prefetch işlemi arka planda devam ediyorsa bitmesini bekle
                        Companion.prefetchJob?.join()

                        val cached = platformCache[selectedPlatform]
                        if (cached != null) {
                            // Cache hit — hemen uygula, loading state'i kısa tut
                            isLoading = true
                            applyPayload(cached)
                            loadedPlatforms.add(selectedPlatform)
                            isLoading = false
                        } else {
                            // Cache miss — loadData() kendi finally bloğuyla isLoading'i yönetir
                            errorMessage = null
                            loadData(forceRefresh = true)
                        }
                    } else {
                        platformCache.clear()
                        loadedPlatforms.clear()
                        allSourceStates = emptyMap()
                        loadData(forceRefresh = true)
                    }
                }
            }
        }
    }

    fun selectPlatform(platform: ExplorePlatform, isFallback: Boolean = false) {
        if (selectedPlatform == platform) return
        if (!isFallback) {
            isFallbackInProgress = false
            // Yalnızca kullanıcının kendi seçimi kalıcı olur; otomatik fallback
            // (örn. TMDB anahtarı bozuksa AniList'e düşme) seçim olarak kaydedilmez.
            persistPlatform(platform)
        }
        cancelLoads()
        isShowingCachedData = false
        selectedPlatform = platform

        // Stale veriyi hemen temizle — eski platformun verisi yeni platformda gözükmesin
        clearPayload()

        if (platform == ExplorePlatform.ALL) {
            loadAllSources(forceRefresh = false)
            return
        }

        // Cache'de varsa anında yükle, yoksa fetch et
        val cached = platformCache[platform]
        if (cached != null) {
            applyPayload(cached)
            // Cache'den yüklenen platformu da "loaded" say
            loadedPlatforms.add(platform)
        } else {
            loadData(forceRefresh = true)
        }
    }

    /** Kullanıcının Keşfet kaynak seçimini disk'e yazar (uygulama yeniden açıldığında korunur). */
    private fun persistPlatform(platform: ExplorePlatform) {
        viewModelScope.launch {
            runCatching { settingsDataStore.setLastExplorePlatform(platform.name) }
        }
    }

    private fun applyPayload(payload: ExplorePayload) {
        topAnime = payload.topAnime
        airingAnime = payload.airingAnime
        upcomingAnime = payload.upcomingAnime
        topManga = payload.topManga
        publishingManga = payload.publishingManga
        trendingAnime = payload.trendingAnime
        movieAnime = payload.movieAnime
        seasonalAnime = payload.seasonalAnime
        airingSoonAnime = payload.airingSoonAnime
        trendingManga = payload.trendingManga
        newlyAddedAnime = payload.newlyAddedAnime
        newlyAddedManga = payload.newlyAddedManga
        upcomingMediaTmdb = payload.upcomingMediaTmdb
        heroIndex = 0

        simklContinueMovies = payload.simklContinueMovies
        simklPlannedMovies = payload.simklPlannedMovies
        simklContinueSeries = payload.simklContinueSeries
        simklPlannedSeries = payload.simklPlannedSeries
    }

    /** Platform değişiminde UI'daki eski veriyi siler, loading skeleton gösterilir. */
    private fun clearPayload() {
        topAnime = emptyList()
        airingAnime = emptyList()
        upcomingAnime = emptyList()
        topManga = emptyList()
        publishingManga = emptyList()
        trendingAnime = emptyList()
        movieAnime = emptyList()
        seasonalAnime = emptyList()
        airingSoonAnime = emptyList()
        trendingManga = emptyList()
        newlyAddedAnime = emptyList()
        newlyAddedManga = emptyList()
        upcomingMediaTmdb = emptyList()
        // Simkl kullanıcı şeritlerini temizleme — platform değişiminde de gözüksün
        heroIndex = 0
        errorMessage = null
        exploreErrorType = ExploreErrorType.None
    }

    fun loadData(forceRefresh: Boolean = false) {
        // Eğer forceRefresh değilse ve zaten data yüklüyse veya yükleniyorsa bir şey yapma
        if (!forceRefresh && (isDataLoaded || isLoading)) return

        cancelLoads()
        if (selectedPlatform == ExplorePlatform.ALL) {
            loadAllSources(forceRefresh)
            return
        }
        val generation = requestGeneration
        loadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            exploreErrorType = ExploreErrorType.None

            val platformSnapshot = selectedPlatform

            try {
                val payload = fetchPlatform(platformSnapshot)
                currentCoroutineContext().ensureActive()

                if (selectedPlatform == platformSnapshot && generation == requestGeneration) {
                    platformCache[platformSnapshot] = payload
                    loadedPlatforms.add(platformSnapshot)
                    applyPayload(payload)
                    isFallbackInProgress = false
                    isShowingCachedData = false
                    exploreErrorType = ExploreErrorType.None

                    // Cache successfully loaded payload in database
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                            val gson = com.google.gson.Gson()
                            val json = gson.toJson(payload)
                            db.exploreCacheDao().insertCategory(
                                com.kitsugi.animelist.data.local.ExploreCacheEntity(
                                    categoryKey = exploreCacheKey(platformSnapshot),
                                    payloadJson = json,
                                    cachedAtMs = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedPlatform == platformSnapshot && generation == requestGeneration) {
                    // Try to fall back to local offline database cache
                    val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                    val cached = runCatching { db.exploreCacheDao().getCategory(exploreCacheKey(platformSnapshot)) }.getOrNull()
                    currentCoroutineContext().ensureActive()
                    if (generation != requestGeneration || selectedPlatform != platformSnapshot) return@launch
                    if (cached != null) {
                        val gson = com.google.gson.Gson()
                        val payload = runCatching { gson.fromJson(cached.payloadJson, ExplorePayload::class.java) }.getOrNull()
                        if (payload != null) {
                            android.util.Log.d("ExploreViewModel", "Serving offline explore cache for ${platformSnapshot.name}")
                            isShowingCachedData = true
                            platformCache[platformSnapshot] = payload
                            loadedPlatforms.add(platformSnapshot)
                            applyPayload(payload)
                            isFallbackInProgress = false
                            isLoading = false
                            return@launch
                        }
                    }

                    errorMessage = e.message ?: "Keşfet verileri alınamadı."
                    if (!isFallbackInProgress) {
                        isFallbackInProgress = true
                        val nextPlatform = if (platformSnapshot == ExplorePlatform.AniList) {
                            ExplorePlatform.MAL
                        } else if (platformSnapshot == ExplorePlatform.TMDB) {
                            ExplorePlatform.AniList
                        } else {
                            ExplorePlatform.AniList
                        }
                        selectPlatform(nextPlatform, isFallback = true)
                    } else {
                        // Tüm fallback'ler tükendi — platforma özgü hata tipini belirt
                        isFallbackInProgress = false
                        exploreErrorType = when (platformSnapshot) {
                            ExplorePlatform.ALL       -> ExploreErrorType.None
                            ExplorePlatform.TMDB      -> ExploreErrorType.TmdbError
                            ExplorePlatform.AniList   -> ExploreErrorType.AniListError
                            ExplorePlatform.MAL       -> ExploreErrorType.MalError
                            ExplorePlatform.SIMKL     -> ExploreErrorType.None
                            ExplorePlatform.KITSU     -> ExploreErrorType.None
                            ExplorePlatform.SHIKIMORI -> ExploreErrorType.None
                        }
                    }
                }
            } finally {
                if (coroutineContext[Job] == loadJob) {
                    isLoading = false
                }
            }
        }
    }

    private suspend fun fetchPlatform(platform: ExplorePlatform, allowFallback: Boolean = true): ExplorePayload = when (platform) {
        ExplorePlatform.ALL -> error("Tümü bir API kaynağı değildir")
        ExplorePlatform.AniList -> loadAniListData(allowFallback)
        ExplorePlatform.MAL -> loadMalData()
        ExplorePlatform.TMDB -> loadTmdbData()
        ExplorePlatform.SIMKL -> loadSimklData()
        ExplorePlatform.KITSU -> loadKitsuData()
        ExplorePlatform.SHIKIMORI -> loadShikimoriData()
    }

    private fun loadAllSources(forceRefresh: Boolean) {
        allSourceStates = ExplorePlatform.sources.associateWith { platform ->
            ExploreSourceState(
                payload = (platformCache[platform] ?: allSourceStates[platform]?.payload)?.forSource(platform),
                isLoading = true,
                isCached = platformCache[platform] == null && allSourceStates[platform]?.isCached == true
            )
        }
        isLoading = true
        ExplorePlatform.sources.forEach { launchSource(it, forceRefresh) }
    }

    /** Only the failed source is retried; successful sources stay visible. */
    fun retrySource(platform: ExplorePlatform) {
        if (selectedPlatform != ExplorePlatform.ALL || platform == ExplorePlatform.ALL ||
            allSourceStates[platform]?.isLoading == true) return
        allSourceStates = allSourceStates + (platform to
            (allSourceStates[platform] ?: ExploreSourceState()).copy(isLoading = true, error = null))
        isLoading = true
        launchSource(platform, forceRefresh = true)
    }

    /**
     * Keşfet disk önbelleği anahtarı.
     *
     * Aktif TMDB dilini ve [MediaTitleResolver.VERSION] sürümünü içerir; böylece
     * dil değişiminde ya da başlık çözümleme mantığı güncellendiğinde (ör. CJK
     * başlıkların düzeltildiği sürümde) eski hatalı kayıtlar servis edilmez.
     */
    private fun exploreCacheKey(platform: ExplorePlatform): String {
        val language = com.kitsugi.animelist.data.remote.TmdbApiClient.getActiveLanguage().lowercase()
        return "explore_platform_${platform.name}_${language}_v${com.kitsugi.animelist.utils.MediaTitleResolver.VERSION}"
    }

    private fun launchSource(platform: ExplorePlatform, forceRefresh: Boolean) {
        val generation = requestGeneration
        sourceJobs[platform]?.cancel()
        sourceJobs[platform] = viewModelScope.launch {
            val cached = allSourceStates[platform]?.payload
            val needsFetch = forceRefresh || allSourceStates[platform]?.isCached == true || cached?.hasCatalogContent() != true
            val state = loadExploreSource(
                cached = cached,
                forceRefresh = needsFetch,
                fetch = { fetchPlatform(platform, allowFallback = false).forSource(platform) },
                readOffline = {
                    val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                    db.exploreCacheDao().getCategory(exploreCacheKey(platform))?.let {
                        com.google.gson.Gson().fromJson(it.payloadJson, ExplorePayload::class.java)?.forSource(platform)
                    }
                }
            )
            currentCoroutineContext().ensureActive()
            if (generation != requestGeneration || selectedPlatform != ExplorePlatform.ALL) return@launch
            allSourceStates = allSourceStates + (platform to state)
            isLoading = allSourceStates.values.any { it.isLoading }
            // Never mark a failed/empty/offline response as a successful in-memory load.
            if (state.error == null && state.payload != null) {
                platformCache[platform] = state.payload
                loadedPlatforms.add(platform)
                if (needsFetch) {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(getApplication())
                            db.exploreCacheDao().insertCategory(
                                com.kitsugi.animelist.data.local.ExploreCacheEntity(
                                    categoryKey = exploreCacheKey(platform),
                                    payloadJson = com.google.gson.Gson().toJson(state.payload),
                                    cachedAtMs = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("ExploreViewModel", "Explore cache write failed", e)
                    }
                }
            }
        }
    }

    /**
     * enrichWithTmdb: Sadece source="simkl" olan öğeler için (kullanıcı watchlist'i)
     * TMDB'den zenginleştirilmiş poster/puan çeker.
     * TMDB trending öğeleri zaten doğru veriye sahip olduğu için burada işleme alınmaz.
     */
    private suspend fun enrichWithTmdb(item: JikanSearchResult): JikanSearchResult {
        // TMDB kaynaklı öğeler zaten tam veriye sahip — tekrar TMDB'ye gitme
        if (item.source == "tmdb") return item
        val tmdbId = item.tmdbId ?: return item
        val isMovie = item.type == com.kitsugi.animelist.model.MediaType.Movie
        val details = tmdbApiClient.fetchMediaDetail(tmdbId, isMovie) ?: return item

        val typeStr = when (item.type) {
            com.kitsugi.animelist.model.MediaType.Movie -> "Film"
            com.kitsugi.animelist.model.MediaType.TvShow -> "Dizi"
            com.kitsugi.animelist.model.MediaType.Anime -> "Anime"
            else -> "Anime"
        }

        val subtitleParts = buildList {
            add(typeStr)
            val yearVal = item.year ?: details.year
            if (yearVal != null && yearVal > 0) add(yearVal.toString())
            addAll(details.genres.take(3))
        }
        val richSubtitle = subtitleParts.joinToString(", ")

        val ratingInt = (details.score ?: 0) / 10
        val finalScore = if (ratingInt > 0) ratingInt else null

        val backdropUrl = details.pictures.firstOrNull { it.contains("/w1280/") } ?: item.backdropUrl

        return item.copy(
            title = details.title?.takeIf { it.isNotBlank() } ?: item.title,
            subtitle = richSubtitle,
            score = finalScore ?: item.score,
            year = details.year ?: item.year,
            imageUrl = details.imageUrl ?: item.imageUrl,
            backdropUrl = backdropUrl
        )
    }

    /**
     * TMDB tab için veri kaynağı — TMDB trending/popular içerikleri çeker.
     * Simkl API'si yalnızca giriş yapmış kullanıcının watchlist'ini çekmek için
     * (SimklSyncManager) çağrılır. Unauthenticated discovery tamamen TMDB'ye taşındı.
     */
    private suspend fun loadTmdbData(): ExplorePayload = supervisorScope {
        val context = getApplication<Application>().applicationContext
        val simklToken = ExternalAuthManager.getSimklToken(context)

        // ── TMDB Discovery: her zaman aktif ──────────────────────────────────────
        val trendingMoviesDeferred = async { runCatching { tmdbApiClient.getTrendingMovies() }.getOrDefault(emptyList()) }
        val trendingShowsDeferred  = async { runCatching { tmdbApiClient.getTrendingShows() }.getOrDefault(emptyList()) }
        val popularMoviesDeferred  = async { runCatching { tmdbApiClient.getPopularMovies() }.getOrDefault(emptyList()) }
        val trendingAllDeferred    = async { runCatching { tmdbApiClient.getTrendingAll() }.getOrDefault(emptyList()) }
        val popularShowsDeferred   = async { runCatching { tmdbApiClient.getPopularShows() }.getOrDefault(emptyList()) }
        val topRatedMoviesDeferred = async { runCatching { tmdbApiClient.getTopRatedMovies() }.getOrDefault(emptyList()) }
        val topRatedShowsDeferred  = async { runCatching { tmdbApiClient.getTopRatedShows() }.getOrDefault(emptyList()) }

        val trendingMediaDeferred  = async { runCatching { tmdbApiClient.getTrendingMedia() }.getOrDefault(emptyList()) }
        val popularMediaDeferred   = async { runCatching { tmdbApiClient.getPopularMedia() }.getOrDefault(emptyList()) }
        val upcomingMediaDeferred  = async { runCatching { tmdbApiClient.getUpcomingMedia() }.getOrDefault(emptyList()) }
        val topRatedAnimeDeferred  = async { runCatching { tmdbApiClient.getTopRatedAnime() }.getOrDefault(emptyList()) }
        val airingSoonDeferred = async {
            val calendarClient = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
            val upcoming = runCatching { calendarClient.fetchUpcomingSchedule(limit = 40, preferredSource = "tmdb") }.getOrNull() ?: emptyList()
            val nowSeconds = System.currentTimeMillis() / 1000L
            upcoming
                .filter { it.airingAt > nowSeconds }
                .sortedBy { it.airingAt }
                .take(15)
                .map { entry ->
                    val finalType = if (entry.episode == 0) MediaType.Movie else MediaType.TvShow
                    JikanSearchResult(
                        malId = entry.aniListId,
                        title = entry.title,
                        subtitle = if (entry.episode == 0) "Film" else "${entry.episode}. Bölüm",
                        type = finalType,
                        total = null,
                        score = entry.averageScore,
                        isAdult = entry.isAdult,
                        imageUrl = entry.coverUrl,
                        year = null,
                        source = "tmdb",
                        realMalId = null,
                        titleEnglish = entry.titleEnglish,
                        titleJapanese = entry.titleNative,
                        nextAiringEpisode = "${entry.episode}|${entry.airingAt}",
                        tmdbId = entry.aniListId
                    )
                }
        }

        val moviesList    = trendingMoviesDeferred.await()
        val showsList     = trendingShowsDeferred.await()
        val popularMovies = popularMoviesDeferred.await()
        val allTrending   = trendingAllDeferred.await()
        val popularShows  = popularShowsDeferred.await()
        val topRatedMovies = topRatedMoviesDeferred.await()
        val topRatedShows  = topRatedShowsDeferred.await()

        val trendingMediaList = trendingMediaDeferred.await()
        val popularMediaList  = popularMediaDeferred.await()
        val upcomingMediaList = upcomingMediaDeferred.await()
        val topRatedAnimeList = topRatedAnimeDeferred.await()
        val airingSoonList    = airingSoonDeferred.await()

        // ── Authenticated: Simkl watchlist (sadece giriş yapılmışsa) ────────────────
        val userMoviesDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "movies") }
        } else null
        val userShowsDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "shows") }
        } else null

        // Kullanıcı listelerini filtrele
        val userMovies = userMoviesDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()
        val userShows  = userShowsDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()

        var continueMovies = userMovies.filter { it.subtitle.contains("İzleniyor") }
        var plannedMovies  = userMovies.filter { it.subtitle.contains("Planlandı") }
        var continueSeries = userShows.filter { it.subtitle.contains("İzleniyor") }
        var plannedSeries  = userShows.filter { it.subtitle.contains("Planlandı") }

        if (tmdbEnabledState && tmdbEnrichContinueWatchingState) {
            // Sadece "İzlemeye Devam Et" listelerini paralel olarak zenginleştir (maksimum ilk 8 öğe)
            val moviesToEnrich = continueMovies.take(8)
            val seriesToEnrich = continueSeries.take(8)
            val movieJobs = moviesToEnrich.map { item ->
                async { enrichWithTmdb(item) }
            }
            val seriesJobs = seriesToEnrich.map { item ->
                async { enrichWithTmdb(item) }
            }
            val enrichedMovies = movieJobs.mapIndexed { idx, job ->
                runCatching { job.await() }.getOrDefault(moviesToEnrich[idx])
            }
            val enrichedSeries = seriesJobs.mapIndexed { idx, job ->
                runCatching { job.await() }.getOrDefault(seriesToEnrich[idx])
            }
            continueMovies = enrichedMovies + continueMovies.drop(8)
            continueSeries = enrichedSeries + continueSeries.drop(8)
        }

        ExplorePayload(
            topAnime      = allTrending,       // Trend Her Şey (film + dizi karışık)
            airingAnime   = showsList,          // Trend Diziler
            upcomingAnime = popularMovies,      // Popüler Filmler
            topManga      = popularShows,       // Popüler Diziler
            publishingManga = topRatedMovies,   // En Yüksek Puanlı Filmler
            trendingAnime = trendingMediaList,  // Trend Animeler
            movieAnime    = moviesList,         // Trend Filmler
            seasonalAnime = topRatedShows,      // En Yüksek Puanlı Diziler
            newlyAddedAnime = popularMediaList, // Popüler Animeler
            trendingManga = topRatedAnimeList,  // En Yüksek Puanlı Animeler
            upcomingMediaTmdb = upcomingMediaList, // Yakında Yayında (Medya) — ayrı field
            simklContinueMovies = continueMovies,
            simklPlannedMovies = plannedMovies,
            simklContinueSeries = continueSeries,
            simklPlannedSeries = plannedSeries,
            airingSoonAnime = airingSoonList
        )
    }

    private suspend fun loadKitsuData(): ExplorePayload = supervisorScope {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH)
        val year = cal.get(java.util.Calendar.YEAR)
        val currentSeason = when (month) {
            0, 1, 11 -> "winter"
            2, 3, 4 -> "spring"
            5, 6, 7 -> "summer"
            else -> "fall"
        }

        val topAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topAnime(20) }.getOrDefault(emptyList()) }
        val topRatedAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedAnime(20) }.getOrDefault(emptyList()) }
        val topRatedMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topRatedManga(20) }.getOrDefault(emptyList()) }
        val trendingAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingAnime(20) }.getOrDefault(emptyList()) }
        val seasonalAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.seasonalAnime(currentSeason, year, 20) }.getOrDefault(emptyList()) }
        val airingAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.airingAnime(20) }.getOrDefault(emptyList()) }
        val upcomingAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.upcomingAnime(20) }.getOrDefault(emptyList()) }
        val newlyAddedAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedAnime(20) }.getOrDefault(emptyList()) }
        val movieAnimeDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.movieAnime(20) }.getOrDefault(emptyList()) }
        val topMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.topManga(20) }.getOrDefault(emptyList()) }
        val publishingMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.publishingManga(20) }.getOrDefault(emptyList()) }
        val trendingMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.trendingManga(20) }.getOrDefault(emptyList()) }
        val newlyAddedMangaDeferred = async { runCatching { com.kitsugi.animelist.data.remote.KitsuExploreClient.newlyAddedManga(20) }.getOrDefault(emptyList()) }

        val rawTopAnime = runCatching { topAnimeDeferred.await() }.getOrDefault(emptyList())
        val enrichedTopAnime = if (rawTopAnime.isNotEmpty() && tmdbEnabledState) {
            val heroCount = minOf(rawTopAnime.size, 5)
            val backdropJobs = (0 until heroCount).map { index ->
                val item = rawTopAnime[index]
                async {
                    val backdrop = tmdbApiClient.fetchBackdropByTitle(item.title)
                    if (backdrop != null) item.copy(backdropUrl = backdrop) else item
                }
            }
            val enrichedHeroes = backdropJobs.mapIndexed { index, job ->
                runCatching { job.await() }.getOrDefault(rawTopAnime[index])
            }
            enrichedHeroes + rawTopAnime.drop(heroCount)
        } else {
            rawTopAnime
        }

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = airingAnimeDeferred.await(),
            upcomingAnime = upcomingAnimeDeferred.await(),
            topManga = topMangaDeferred.await(),
            publishingManga = publishingMangaDeferred.await(),
            trendingManga = trendingMangaDeferred.await(),
            newlyAddedAnime = newlyAddedAnimeDeferred.await(),
            newlyAddedManga = newlyAddedMangaDeferred.await(),
            trendingAnime = trendingAnimeDeferred.await(),
            movieAnime = movieAnimeDeferred.await(),
            seasonalAnime = seasonalAnimeDeferred.await(),
            topRatedAnime = topRatedAnimeDeferred.await(),
            topRatedManga = topRatedMangaDeferred.await()
        )
    }

    private suspend fun loadShikimoriData(): ExplorePayload = supervisorScope {
        // Shikimori excludes Rx titles by default. Ask for uncensored results only when
        // the user enabled adult content; each returned item is still rated individually.
        val censored = !showAdultContentState
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH)
        val year = cal.get(java.util.Calendar.YEAR)
        val seasonPrefix = when (month) {
            0, 1, 11 -> "winter"
            2, 3, 4 -> "spring"
            5, 6, 7 -> "summer"
            else -> "fall"
        }
        val currentShikiSeason = "${seasonPrefix}_${year}"

        val topAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.topAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val trendingAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.trendingAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val seasonalAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.seasonalAnime(season = currentShikiSeason, limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val movieAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.movieAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val airingAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.airingAnime(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val upcomingAnimeDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                    com.kitsugi.animelist.model.MediaType.Anime,
                    statuses = listOf("anons"),
                    order = "popularity",
                    limit = 20,
                    censored = censored
                )
            }.getOrDefault(emptyList())
        }
        val topMangaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.topManga(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val publishingMangaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.publishingManga(limit = 20, censored = censored)
            }.getOrDefault(emptyList())
        }
        val trendingMangaDeferred = async {
            runCatching {
                com.kitsugi.animelist.data.remote.KitsugiShikimoriClient.searchMediaAdvanced(
                    com.kitsugi.animelist.model.MediaType.Manga,
                    order = "popularity",
                    limit = 20,
                    censored = censored
                )
            }.getOrDefault(emptyList())
        }

        val rawTopAnime = runCatching { topAnimeDeferred.await() }.getOrDefault(emptyList())
        val enrichedTopAnime = if (rawTopAnime.isNotEmpty() && tmdbEnabledState) {
            val heroCount = minOf(rawTopAnime.size, 5)
            val backdropJobs = (0 until heroCount).map { index ->
                val item = rawTopAnime[index]
                async {
                    val backdrop = tmdbApiClient.fetchBackdropByTitle(item.title)
                    if (backdrop != null) item.copy(backdropUrl = backdrop) else item
                }
            }
            val enrichedHeroes = backdropJobs.mapIndexed { index, job ->
                runCatching { job.await() }.getOrDefault(rawTopAnime[index])
            }
            enrichedHeroes + rawTopAnime.drop(heroCount)
        } else {
            rawTopAnime
        }

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = airingAnimeDeferred.await(),
            upcomingAnime = upcomingAnimeDeferred.await(),
            topManga = topMangaDeferred.await(),
            publishingManga = publishingMangaDeferred.await(),
            trendingManga = trendingMangaDeferred.await(),
            trendingAnime = trendingAnimeDeferred.await(),
            movieAnime = movieAnimeDeferred.await(),
            seasonalAnime = seasonalAnimeDeferred.await()
        )
    }

    private suspend fun loadSimklData(): ExplorePayload = supervisorScope {
        val context = getApplication<Application>().applicationContext
        val simkl = com.kitsugi.animelist.data.remote.SimklApiClient()

        val allTrendingDeferred = async { runCatching { simkl.getBestMedia("tv/trending", com.kitsugi.animelist.model.MediaType.TvShow, 20) }.getOrDefault(emptyList()) }
        val airingTvDeferred = async { runCatching { simkl.getBestMedia("tv/best/airing", com.kitsugi.animelist.model.MediaType.TvShow, 20) }.getOrDefault(emptyList()) }
        val trendingMoviesDeferred = async { runCatching { simkl.getBestMedia("movies/trending", com.kitsugi.animelist.model.MediaType.Movie, 20) }.getOrDefault(emptyList()) }
        val topTvDeferred = async { runCatching { simkl.getBestMedia("tv/best/all-time", com.kitsugi.animelist.model.MediaType.TvShow, 20) }.getOrDefault(emptyList()) }
        val popularMoviesDeferred = async { runCatching { simkl.getBestMedia("movies/recent", com.kitsugi.animelist.model.MediaType.Movie, 20) }.getOrDefault(emptyList()) }
        val trendingAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/trending", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        val topAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/best/all-time", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        val airingAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/best/airing", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }
        val upcomingAnimeDeferred = async { runCatching { simkl.getBestMedia("anime/best/upcoming", com.kitsugi.animelist.model.MediaType.Anime, 20) }.getOrDefault(emptyList()) }

        val simklToken = ExternalAuthManager.getSimklToken(context)
        val userMoviesDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "movies") }
        } else null
        val userShowsDeferred = if (!simklToken.isNullOrBlank()) {
            async { SimklSyncManager.fetchSimklWatchlist(context, "shows") }
        } else null

        val userMovies = userMoviesDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()
        val userShows  = userShowsDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()

        val continueMovies = userMovies.filter { it.subtitle.contains("İzleniyor") }
        val plannedMovies  = userMovies.filter { it.subtitle.contains("Planlandı") }
        val continueSeries = userShows.filter { it.subtitle.contains("İzleniyor") }
        val plannedSeries  = userShows.filter { it.subtitle.contains("Planlandı") }

        val topAnimeList = topAnimeDeferred.await()
        val airingAnimeList = airingAnimeDeferred.await()
        val upcomingAnimeList = upcomingAnimeDeferred.await()
        val trendingAnimeList = trendingAnimeDeferred.await()
        val allTrendingList = allTrendingDeferred.await()
        val airingTvList = airingTvDeferred.await()
        val trendingMoviesList = trendingMoviesDeferred.await()
        val topTvList = topTvDeferred.await()
        val popularMoviesList = popularMoviesDeferred.await()

        ExplorePayload(
            topAnime = allTrendingList,            // Trend Her Şey
            airingAnime = airingTvList,            // Trend Diziler
            upcomingAnime = popularMoviesList,     // Popüler Filmler
            topManga = topTvList,                  // Popüler Diziler
            publishingManga = trendingMoviesList,  // En Yüksek Puanlı / Trend Filmler
            trendingAnime = trendingAnimeList,     // Trend Animeler
            movieAnime = trendingMoviesList,       // Trend Filmler
            seasonalAnime = topTvList,             // En Yüksek Puanlı Diziler
            newlyAddedAnime = topAnimeList,        // Popüler Animeler
            trendingManga = airingAnimeList,       // En Yüksek Puanlı Animeler
            upcomingMediaTmdb = upcomingAnimeList, // Yakında Yayında
            simklContinueMovies = continueMovies,
            simklPlannedMovies = plannedMovies,
            simklContinueSeries = continueSeries,
            simklPlannedSeries = plannedSeries,
            airingSoonAnime = upcomingAnimeList
        )
    }

    private suspend fun loadMalData(): ExplorePayload = supervisorScope {
        val showAdult = showAdultContentState
        val topAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.topAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val airingAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.airingAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val upcomingAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.upcomingAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val trendingAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.trendingAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val movieAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.movieAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val seasonalAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.seasonalAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val topMangaDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.topManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val publishingMangaDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.publishingManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val trendingMangaDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.trendingManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val newlyAddedAnimeDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.newlyAddedAnime(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }
        val newlyAddedMangaDeferred = async { withTimeoutOrNull(5000L) { runCatching { apiClient.newlyAddedManga(showAdultContent = showAdult) }.getOrDefault(emptyList()) } ?: emptyList() }

        val rawTopAnime = runCatching { topAnimeDeferred.await() }.getOrDefault(emptyList())

        // İlk 5 vitrin öğesini paralel olarak TMDB'den yatay backdrop resmi ile zenrichleştir
        val enrichedTopAnime = if (rawTopAnime.isNotEmpty() && tmdbEnabledState) {
            val heroCount = minOf(rawTopAnime.size, 5)
            val backdropJobs = (0 until heroCount).map { index ->
                val item = rawTopAnime[index]
                async {
                    val backdrop = tmdbApiClient.fetchBackdropByTitle(item.title)
                    if (backdrop != null) item.copy(backdropUrl = backdrop) else item
                }
            }
            val enrichedHeroes = backdropJobs.mapIndexed { index, job ->
                runCatching { job.await() }.getOrDefault(rawTopAnime[index])
            }
            enrichedHeroes + rawTopAnime.drop(heroCount)
        } else {
            rawTopAnime
        }

        val airingSoonDeferred = async {
            val calendarClient = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
            val upcoming = runCatching { calendarClient.fetchUpcomingSchedule(limit = 40) }.getOrNull() ?: emptyList()
            val nowSeconds = System.currentTimeMillis() / 1000L
            upcoming
                .filter { it.airingAt > nowSeconds && it.malId != null }
                .sortedBy { it.airingAt }
                .take(15)
                .map { entry ->
                    JikanSearchResult(
                        malId = entry.malId!!,
                        title = entry.getDisplayTitle(),
                        subtitle = "${entry.episode}. Bölüm",
                        type = MediaType.Anime,
                        total = null,
                        score = entry.averageScore,
                        isAdult = entry.isAdult,
                        imageUrl = entry.coverUrl,
                        year = null,
                        source = "mal",
                        realMalId = entry.malId,
                        titleEnglish = entry.titleEnglish,
                        titleJapanese = entry.titleNative,
                        nextAiringEpisode = "${entry.episode}|${entry.airingAt}"
                    )
                }
        }

        ExplorePayload(
            topAnime = enrichedTopAnime,
            airingAnime = runCatching { airingAnimeDeferred.await() }.getOrDefault(emptyList()),
            upcomingAnime = runCatching { upcomingAnimeDeferred.await() }.getOrDefault(emptyList()),
            topManga = runCatching { topMangaDeferred.await() }.getOrDefault(emptyList()),
            publishingManga = runCatching { publishingMangaDeferred.await() }.getOrDefault(emptyList()),
            trendingManga = runCatching { trendingMangaDeferred.await() }.getOrDefault(emptyList()),
            newlyAddedAnime = runCatching { newlyAddedAnimeDeferred.await() }.getOrDefault(emptyList()),
            newlyAddedManga = runCatching { newlyAddedMangaDeferred.await() }.getOrDefault(emptyList()),
            trendingAnime = runCatching { trendingAnimeDeferred.await() }.getOrDefault(emptyList()),
            movieAnime = runCatching { movieAnimeDeferred.await() }.getOrDefault(emptyList()),
            seasonalAnime = runCatching { seasonalAnimeDeferred.await() }.getOrDefault(emptyList()),
            airingSoonAnime = runCatching { airingSoonDeferred.await() }.getOrDefault(emptyList())
        )
    }

    private suspend fun loadAniListData(allowFallback: Boolean = true): ExplorePayload = supervisorScope {
        val showAdult = showAdultContentState
        val serviceError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        fun <T> Result<T>.orDefaultTracking(d: T): T {
            exceptionOrNull()?.let { ex ->
                if (ex is com.kitsugi.animelist.data.remote.AniListServiceDownException)
                    serviceError.compareAndSet(null, ex)
            }
            return getOrDefault(d) ?: d
        }

        val topAnimeDeferred = async { apiClient.aniListTopAnime(showAdultContent = showAdult) }
        val topRatedAnimeDeferred = async { apiClient.aniListTopRated(MediaType.Anime, showAdultContent = showAdult) }
        val topRatedMangaDeferred = async { apiClient.aniListTopRated(MediaType.Manga, showAdultContent = showAdult) }
        val trendingAnimeDeferred = async { apiClient.aniListTrendingAnime(showAdultContent = showAdult) }
        val seasonalAnimeDeferred = async { apiClient.aniListSeasonalAnime(showAdultContent = showAdult) }
        val movieAnimeDeferred = async { apiClient.aniListMovieAnime(showAdultContent = showAdult) }
        val airingAnimeDeferred = async { apiClient.aniListAiringAnime(showAdultContent = showAdult) }
        val upcomingAnimeDeferred = async { apiClient.aniListUpcomingAnime(showAdultContent = showAdult) }
        val topMangaDeferred = async { apiClient.aniListTopManga(showAdultContent = showAdult) }
        val publishingMangaDeferred = async { apiClient.aniListPublishingManga(showAdultContent = showAdult) }
        val trendingMangaDeferred = async { apiClient.aniListTrendingManga(showAdultContent = showAdult) }
        val newlyAddedAnimeDeferred = async { apiClient.aniListNewlyAddedAnime(showAdultContent = showAdult) }
        val newlyAddedMangaDeferred = async { apiClient.aniListNewlyAddedManga(showAdultContent = showAdult) }

        val airingSoonDeferred = async {
            val cal = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
            val upcoming = runCatching { cal.fetchUpcomingSchedule(limit = 15) }.getOrNull() ?: emptyList()
            val nowSec = System.currentTimeMillis() / 1000L
            upcoming.filter { it.airingAt > nowSec }.sortedBy { it.airingAt }.take(15)
                .map { e ->
                    JikanSearchResult(
                        malId = e.malId ?: e.aniListId, title = e.getDisplayTitle(),
                        subtitle = "${e.episode}. Bölüm", type = MediaType.Anime,
                        total = null, score = e.averageScore, isAdult = e.isAdult,
                        imageUrl = e.coverUrl, year = null, source = "anilist",
                        realMalId = e.malId, titleEnglish = e.titleEnglish,
                        titleJapanese = e.titleNative,
                        nextAiringEpisode = "${e.episode}|${e.airingAt}"
                    )
                }
        }

        val topAnime        = runCatching { topAnimeDeferred.await() }.orDefaultTracking(emptyList())
        val airingAnime     = runCatching { airingAnimeDeferred.await() }.orDefaultTracking(emptyList())
        val upcomingAnime   = runCatching { upcomingAnimeDeferred.await() }.orDefaultTracking(emptyList())
        val topManga        = runCatching { topMangaDeferred.await() }.orDefaultTracking(emptyList())
        val publishingManga = runCatching { publishingMangaDeferred.await() }.orDefaultTracking(emptyList())
        val trendingManga   = runCatching { trendingMangaDeferred.await() }.orDefaultTracking(emptyList())
        val newlyAddedAnime = runCatching { newlyAddedAnimeDeferred.await() }.orDefaultTracking(emptyList())
        val newlyAddedManga = runCatching { newlyAddedMangaDeferred.await() }.orDefaultTracking(emptyList())
        val airingSoon      = runCatching { airingSoonDeferred.await() }.getOrDefault(emptyList())

        // AniList veri vermezse veya servis hatası tespit edildiyse Kitsu tam fallback
        val isAniListEmpty = topAnime.isEmpty() && airingAnime.isEmpty() && upcomingAnime.isEmpty()
        if (allowFallback && (serviceError.get() != null || isAniListEmpty)) {
            android.util.Log.w("ExploreViewModel", "AniList veri vermedi veya servis hatası (boş=$isAniListEmpty) → Kitsu fallback")
            return@supervisorScope loadKitsuData()
        }

        // Kısmi boşlukları Kitsu ile tamamla
        val needsKitsuFill = topAnime.isEmpty() || airingAnime.isEmpty() || upcomingAnime.isEmpty() ||
            topManga.isEmpty() || publishingManga.isEmpty() || trendingManga.isEmpty()
        val kitsuFill = if (allowFallback && needsKitsuFill) runCatching { loadKitsuData() }.getOrNull() else null

        ExplorePayload(
            topAnime = topAnime.ifEmpty { kitsuFill?.topAnime ?: emptyList() },
            airingAnime = airingAnime.ifEmpty { kitsuFill?.airingAnime ?: emptyList() },
            upcomingAnime = upcomingAnime.ifEmpty { kitsuFill?.upcomingAnime ?: emptyList() },
            topManga = topManga.ifEmpty { kitsuFill?.topManga ?: emptyList() },
            publishingManga = publishingManga.ifEmpty { kitsuFill?.publishingManga ?: emptyList() },
            trendingManga = trendingManga.ifEmpty { kitsuFill?.trendingManga ?: emptyList() },
            newlyAddedAnime = newlyAddedAnime.ifEmpty { kitsuFill?.newlyAddedAnime ?: emptyList() },
            newlyAddedManga = newlyAddedManga.ifEmpty { kitsuFill?.newlyAddedManga ?: emptyList() },
            trendingAnime = runCatching { trendingAnimeDeferred.await() }.orDefaultTracking(emptyList()),
            movieAnime = runCatching { movieAnimeDeferred.await() }.orDefaultTracking(emptyList()),
            seasonalAnime = runCatching { seasonalAnimeDeferred.await() }.orDefaultTracking(emptyList()),
            topRatedAnime = runCatching { topRatedAnimeDeferred.await() }.orDefaultTracking(emptyList()),
            topRatedManga = runCatching { topRatedMangaDeferred.await() }.orDefaultTracking(emptyList()),
            airingSoonAnime = airingSoon
        )
    }



    fun nextHero(heroCount: Int) {
        if (heroCount == 0) return
        heroIndex = if (heroIndex >= heroCount - 1) 0 else heroIndex + 1
    }

    fun previousHero(heroCount: Int) {
        if (heroCount == 0) return
        heroIndex = if (heroIndex <= 0) heroCount - 1 else heroIndex - 1
    }

    companion object {
        val platformCache = java.util.concurrent.ConcurrentHashMap<ExplorePlatform, ExplorePayload>()
        val loadedPlatforms = java.util.Collections.synchronizedSet(mutableSetOf<ExplorePlatform>())
        
        private val isPrefetchStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile
        var prefetchJob: kotlinx.coroutines.Job? = null

        fun prefetch(context: android.content.Context) {
            if (!isPrefetchStarted.compareAndSet(false, true)) return
            
            val app = context.applicationContext as android.app.Application
            
            prefetchJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    val settingsDataStore = SettingsDataStore(app)
                    val settings = settingsDataStore.settingsFlow.first()
                    
                    val showAdult = settings.showAdultContent
                    val tmdbEnabled = settings.tmdbEnabled
                    val tmdbEnrich = settings.tmdbEnrichContinueWatching
                    val simklToken = ExternalAuthManager.getSimklToken(app)
                    
                    val tmdbApiClient = TmdbApiClient(userApiKey = settings.tmdbUserApiKey)

                    // Kullanıcının kalıcı Keşfet kaynağı TMDB değilse bu pahalı ön-yükleme
                    // boşa bant genişliği tüketir; kendi platformunun verisini açılışta
                    // kendisi çeker. Sadece TMDB / Tümü seçiliyse önbelleği dolduruyoruz.
                    val lastPlatformName = settingsDataStore.lastExplorePlatformFlow.first()
                    val lastPlatform = ExplorePlatform.entries.firstOrNull { it.name == lastPlatformName }
                        ?: ExplorePlatform.ALL
                    if (lastPlatform != ExplorePlatform.TMDB && lastPlatform != ExplorePlatform.ALL) {
                        android.util.Log.d("ExplorePrefetch", "Son seçili kaynak $lastPlatform — TMDB önbelleği atlanıyor")
                        return@launch
                    }

                    supervisorScope {
                        // Startup prefetch only fetches TMDB (default platform) to minimize latency and bandwidth
                        val tmdbPayload = runCatching {
                            val trendingMoviesDeferred = async { runCatching { tmdbApiClient.getTrendingMovies() }.getOrDefault(emptyList()) }
                            val trendingShowsDeferred  = async { runCatching { tmdbApiClient.getTrendingShows() }.getOrDefault(emptyList()) }
                            val popularMoviesDeferred  = async { runCatching { tmdbApiClient.getPopularMovies() }.getOrDefault(emptyList()) }
                            val trendingAllDeferred    = async { runCatching { tmdbApiClient.getTrendingAll() }.getOrDefault(emptyList()) }
                            val popularShowsDeferred   = async { runCatching { tmdbApiClient.getPopularShows() }.getOrDefault(emptyList()) }
                            val topRatedMoviesDeferred = async { runCatching { tmdbApiClient.getTopRatedMovies() }.getOrDefault(emptyList()) }
                            val topRatedShowsDeferred  = async { runCatching { tmdbApiClient.getTopRatedShows() }.getOrDefault(emptyList()) }
                            val trendingMediaDeferred  = async { runCatching { tmdbApiClient.getTrendingMedia() }.getOrDefault(emptyList()) }
                            val popularMediaDeferred   = async { runCatching { tmdbApiClient.getPopularMedia() }.getOrDefault(emptyList()) }
                            val upcomingMediaDeferred  = async { runCatching { tmdbApiClient.getUpcomingMedia() }.getOrDefault(emptyList()) }
                            val topRatedAnimeDeferred  = async { runCatching { tmdbApiClient.getTopRatedAnime() }.getOrDefault(emptyList()) }
                            val airingSoonDeferred = async {
                                val calendarClient = com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient()
                                val upcoming = runCatching { calendarClient.fetchUpcomingSchedule(limit = 40, preferredSource = "tmdb") }.getOrNull() ?: emptyList()
                                val nowSeconds = System.currentTimeMillis() / 1000L
                                upcoming
                                    .filter { it.airingAt > nowSeconds }
                                    .sortedBy { it.airingAt }
                                    .take(15)
                                    .map { entry ->
                                        val finalType = if (entry.episode == 0) MediaType.Movie else MediaType.TvShow
                                        JikanSearchResult(
                                            malId = entry.aniListId,
                                            title = entry.title,
                                            subtitle = if (entry.episode == 0) "Film" else "${entry.episode}. Bölüm",
                                            type = finalType,
                                            total = null,
                                            score = entry.averageScore,
                                            isAdult = entry.isAdult,
                                            imageUrl = entry.coverUrl,
                                            year = null,
                                            source = "tmdb",
                                            realMalId = null,
                                            titleEnglish = entry.titleEnglish,
                                            titleJapanese = entry.titleNative,
                                            nextAiringEpisode = "${entry.episode}|${entry.airingAt}",
                                            tmdbId = entry.aniListId
                                        )
                                    }
                            }

                            val moviesList    = trendingMoviesDeferred.await()
                            val showsList     = trendingShowsDeferred.await()
                            val popularMovies = popularMoviesDeferred.await()
                            val allTrending   = trendingAllDeferred.await()
                            val popularShows  = popularShowsDeferred.await()
                            val topRatedMovies = topRatedMoviesDeferred.await()
                            val topRatedShows  = topRatedShowsDeferred.await()
                            val trendingMediaList = trendingMediaDeferred.await()
                            val popularMediaList  = popularMediaDeferred.await()
                            val upcomingMediaList = upcomingMediaDeferred.await()
                            val topRatedAnimeList = topRatedAnimeDeferred.await()
                            val airingSoonList    = airingSoonDeferred.await()

                            val userMoviesDeferred = if (!simklToken.isNullOrBlank()) {
                                async { SimklSyncManager.fetchSimklWatchlist(app, "movies") }
                            } else null
                            val userShowsDeferred = if (!simklToken.isNullOrBlank()) {
                                async { SimklSyncManager.fetchSimklWatchlist(app, "shows") }
                            } else null

                            val userMovies = userMoviesDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()
                            val userShows  = userShowsDeferred?.let { runCatching { it.await() }.getOrDefault(emptyList()) } ?: emptyList()

                            var continueMovies = userMovies.filter { it.subtitle.contains("İzleniyor") }
                            val plannedMovies  = userMovies.filter { it.subtitle.contains("Planlandı") }
                            var continueSeries = userShows.filter { it.subtitle.contains("İzleniyor") }
                            val plannedSeries  = userShows.filter { it.subtitle.contains("Planlandı") }

                            if (tmdbEnabled && tmdbEnrich) {
                                val moviesToEnrich = continueMovies.take(8)
                                val seriesToEnrich = continueSeries.take(8)
                                val movieJobs = moviesToEnrich.map { item ->
                                    async {
                                        if (item.source != "tmdb") {
                                            val tmdbId = item.tmdbId
                                            if (tmdbId != null) {
                                                val isMovie = item.type == MediaType.Movie
                                                val details = tmdbApiClient.fetchMediaDetail(tmdbId, isMovie)
                                                if (details != null) {
                                                    val typeStr = when (item.type) {
                                                        MediaType.Movie -> "Film"
                                                        MediaType.TvShow -> "Dizi"
                                                        MediaType.Anime -> "Anime"
                                                        else -> "Anime"
                                                    }
                                                    val subtitleParts = buildList {
                                                        add(typeStr)
                                                        val yearVal = item.year ?: details.year
                                                        if (yearVal != null && yearVal > 0) add(yearVal.toString())
                                                        addAll(details.genres.take(3))
                                                    }
                                                    val ratingInt = (details.score ?: 0) / 10
                                                    val finalScore = if (ratingInt > 0) ratingInt else null
                                                    val backdropUrl = details.pictures.firstOrNull { it.contains("/w1280/") } ?: item.backdropUrl
                                                    item.copy(
                                                        title = details.title?.takeIf { it.isNotBlank() } ?: item.title,
                                                        subtitle = subtitleParts.joinToString(", "),
                                                        score = finalScore ?: item.score,
                                                        year = details.year ?: item.year,
                                                        imageUrl = details.imageUrl ?: item.imageUrl,
                                                        backdropUrl = backdropUrl
                                                    )
                                                } else item
                                            } else item
                                        } else item
                                    }
                                }
                                val seriesJobs = seriesToEnrich.map { item ->
                                    async {
                                        if (item.source != "tmdb") {
                                            val tmdbId = item.tmdbId
                                            if (tmdbId != null) {
                                                val isMovie = item.type == MediaType.Movie
                                                val details = tmdbApiClient.fetchMediaDetail(tmdbId, isMovie)
                                                if (details != null) {
                                                    val typeStr = when (item.type) {
                                                        MediaType.Movie -> "Film"
                                                        MediaType.TvShow -> "Dizi"
                                                        MediaType.Anime -> "Anime"
                                                        else -> "Anime"
                                                    }
                                                    val subtitleParts = buildList {
                                                        add(typeStr)
                                                        val yearVal = item.year ?: details.year
                                                        if (yearVal != null && yearVal > 0) add(yearVal.toString())
                                                        addAll(details.genres.take(3))
                                                    }
                                                    val ratingInt = (details.score ?: 0) / 10
                                                    val finalScore = if (ratingInt > 0) ratingInt else null
                                                    val backdropUrl = details.pictures.firstOrNull { it.contains("/w1280/") } ?: item.backdropUrl
                                                    item.copy(
                                                        title = details.title?.takeIf { it.isNotBlank() } ?: item.title,
                                                        subtitle = subtitleParts.joinToString(", "),
                                                        score = finalScore ?: item.score,
                                                        year = details.year ?: item.year,
                                                        imageUrl = details.imageUrl ?: item.imageUrl,
                                                        backdropUrl = backdropUrl
                                                    )
                                                } else item
                                            } else item
                                        } else item
                                    }
                                }
                                continueMovies = movieJobs.mapIndexed { idx, job ->
                                    runCatching { job.await() }.getOrDefault(moviesToEnrich[idx])
                                }
                                continueSeries = seriesJobs.mapIndexed { idx, job ->
                                    runCatching { job.await() }.getOrDefault(seriesToEnrich[idx])
                                }
                                continueMovies = continueMovies + userMovies.filter { it.subtitle.contains("İzleniyor") }.drop(8)
                                continueSeries = continueSeries + userShows.filter { it.subtitle.contains("İzleniyor") }.drop(8)
                            }

                            ExplorePayload(
                                topAnime      = allTrending,
                                airingAnime   = showsList,
                                upcomingAnime = popularMovies,
                                topManga      = popularShows,
                                publishingManga = topRatedMovies,
                                trendingAnime = trendingMediaList,
                                movieAnime    = moviesList,
                                seasonalAnime = topRatedShows,
                                newlyAddedAnime = popularMediaList,
                                trendingManga = topRatedAnimeList,
                                upcomingMediaTmdb = upcomingMediaList, // Yakında Yayında (Medya)
                                simklContinueMovies = continueMovies,
                                simklPlannedMovies = plannedMovies,
                                simklContinueSeries = continueSeries,
                                simklPlannedSeries = plannedSeries,
                                airingSoonAnime = airingSoonList
                            )
                        }.getOrNull()

                        if (tmdbPayload != null) {
                            platformCache[ExplorePlatform.TMDB] = tmdbPayload
                            loadedPlatforms.add(ExplorePlatform.TMDB)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ExplorePrefetch", "Prefetch failed", e)
                }
            }
        }
    }
}


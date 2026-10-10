package com.kitsugi.animelist.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiCountryOfOrigin
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaFormat
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSeason
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSortSearch
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaSource
import com.kitsugi.animelist.ui.screens.search.composables.KitsugiMediaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.repository.SearchHistoryRepository
import com.kitsugi.animelist.data.remote.KitsugiBangumiClient
import com.kitsugi.animelist.data.remote.KitsugiShikimoriClient
import com.kitsugi.animelist.data.remote.KitsuExploreClient
import com.kitsugi.animelist.data.remote.SimklApiClient

/**
 * Search ViewModel.
 * MoeList SearchViewModel.kt ve AniHyou SearchViewModel.kt'den ilham alınarak
 * Kitsugi'nun JikanApiClient (Jikan + AniList fallback) altyapısına uyarlanmıştır.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = JikanApiClient()
    private val settingsDataStore = SettingsDataStore(application)
    private val database = KitsugiDatabase.getDatabase(application)
    private val searchHistoryRepository = SearchHistoryRepository(database.searchHistoryDao())

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var showAdultContentState = false
    private var searchHistoryEnabledState = true
    private var searchJob: Job? = null

    private val mixedMediaEngines = setOf(
        SearchSourceEngine.ANILIST, SearchSourceEngine.MAL,
        SearchSourceEngine.SHIKIMORI, SearchSourceEngine.KITSU,
        SearchSourceEngine.BANGUMI
    )

    /**
     * Arama nesli (generation) sayacı.
     *
     * Her yeni arama bu sayacı artırır. Arka planda kalan iptal edilmiş bir çalışma
     * yalnızca kendi nesli güncel nesle eşitse UI durumuna yazabilir; aksi halde eski
     * sorgunun (çoğu zaman boş/başarısız) sonuçları yeni sorgunun üzerine yazıyor ve
     * "Tümü" ekranında kaynaklara göre karışık/eksik sonuç görünüyordu.
     */
    private val searchGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * "Tümü" aramasında tek bir kaynağın yanıtı için tanınan süre. Eski değer 7 sn idi;
     * yavaş mobil bağlantılarda (ör. 125 KB/s) AniList/MAL yanıtı bu süreyi aşınca kaynak
     * tamamen kayboluyor ve kullanıcı "aradığım şey çıkmıyor" durumuyla karşılaşıyordu.
     * Şeffaf yükleme durumu (shimmer) korunduğu için süre artışı UX'i bozmaz.
     */
    private val allSourceTimeoutMs = 20_000L

    /** "Tümü" rafında gösterilecek kart sayısı. */
    private val allShelfLimit = 10

    /**
     * "Tümü" rafında kaynaklardan çekilecek aday havuzu.
     *
     * Gösterilenden geniş tutulur: alakalılık sıralamasının yeniden dizmeye alanı
     * olur, böylece kaynağın popülerlik/gevşek eşleşme sıralaması rafın başını
     * işgal edemez.
     */
    private val allShelfFetchLimit = 20

    /**
     * Arama ekranında kaynak seçimi kalıcılığı: kullanıcı hangi kaynağı/kapsamı seçtiyse
     * uygulama yeniden açıldığında oradan devam eder.
     */
    private var searchSelectionRestored = false
    private var restoringSearchSelection = false

    private data class TabSearchState(
        val query: String = "",
        val results: List<JikanSearchResult> = emptyList(),
        val hasSearched: Boolean = false,
        val errorMessage: String? = null
    )

    private val stateCache = mutableMapOf<String, TabSearchState>()

    private fun getTabKey(tab: KitsugiSearchTab, platform: SearchPlatform, mediaType: MediaType): String {
        return "${tab.name}_${platform.name}_${mediaType.name}"
    }

    private fun saveCurrentStateToCache() {
        val currentState = _uiState.value
        val key = getTabKey(currentState.currentTab, currentState.selectedPlatform, currentState.selectedMediaType)
        stateCache[key] = TabSearchState(
            query = currentState.query,
            results = currentState.results,
            hasSearched = currentState.hasSearched,
            errorMessage = currentState.errorMessage
        )
    }

    private fun loadStateFromCache(tab: KitsugiSearchTab = _uiState.value.currentTab, platform: SearchPlatform, mediaType: MediaType): TabSearchState {
        val key = getTabKey(tab, platform, mediaType)
        return stateCache[key] ?: TabSearchState()
    }

    /** Sekme + motor + kapsam + platform + tür seçimini disk'e yazar. */
    private fun persistSearchSelection() {
        if (restoringSearchSelection) return
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                settingsDataStore.setLastSearchEngineAndScope(state.selectedEngine.name, state.selectedScope.name)
                settingsDataStore.setLastSearchSelection(state.currentTab.name, state.selectedPlatform.name, state.selectedMediaType.name)
            }
        }
    }

    /**
     * Kalıcı seçim geri yüklenirken kaynak motoruna göre UI durumunu senkron tutar.
     * Arama başlatmaz — açılışta yalnızca seçili görünen kaynak düzeltilir.
     */
    private fun applySearchSelection(engine: SearchSourceEngine, scope: SearchScope, tab: KitsugiSearchTab) {
        val platform = when (engine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
            SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
        }
        val mediaType = when (scope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.MOVIE -> MediaType.Movie
            SearchScope.TV -> MediaType.TvShow
            else -> MediaType.Anime
        }
        _uiState.update {
            it.copy(
                selectedEngine = engine,
                selectedScope = scope,
                currentTab = tab,
                selectedPlatform = platform,
                selectedMediaType = mediaType
            )
        }
    }

    init {
        viewModelScope.launch {
            // Kalıcı kaynak seçimini uygula (kullanıcı henüz arama yapmadıysa)
            if (!searchSelectionRestored) {
                searchSelectionRestored = true
                val engineName = runCatching { settingsDataStore.lastSearchEngineFlow.firstOrNull() }.getOrNull().orEmpty()
                val scopeName = runCatching { settingsDataStore.lastSearchScopeFlow.firstOrNull() }.getOrNull().orEmpty()
                val engine = SearchSourceEngine.entries.firstOrNull { it.name == engineName }
                val scope = SearchScope.entries.firstOrNull { it.name == scopeName }
                if (engine != null && engine != _uiState.value.selectedEngine &&
                    _uiState.value.query.isBlank() && !_uiState.value.hasSearched
                ) {
                    val scopes = engine.availableScopes()
                    val effectiveScope = scope?.takeIf { it in scopes } ?: scopes.first()
                    restoringSearchSelection = true
                    applySearchSelection(
                        engine = engine,
                        scope = effectiveScope,
                        tab = when (effectiveScope) {
                            SearchScope.CHARACTER -> KitsugiSearchTab.Character
                            SearchScope.STAFF -> KitsugiSearchTab.Staff
                            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> KitsugiSearchTab.Manga
                            else -> when (engine) {
                                SearchSourceEngine.ALL -> KitsugiSearchTab.All
                                SearchSourceEngine.ANILIST -> KitsugiSearchTab.Anime
                                SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
                                SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
                                SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
                                SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
                                SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
                                SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
                            }
                        }
                    )
                    restoringSearchSelection = false
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                showAdultContentState = settings.showAdultContent
                searchHistoryEnabledState = settings.searchHistoryEnabled
            }
        }
        viewModelScope.launch {
            searchHistoryRepository.getRecentSearchHistory().collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    fun setQuery(value: String) {
        if (value != _uiState.value.query) {
            // Metin değiştiği anda eski sorgunun sonuç/isteklerini geçersiz kıl.
            // Yazarken eski sorgunun sonuçları yeni metnin altında görünmemeli.
            searchGeneration.incrementAndGet()
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    query = value,
                    results = emptyList(),
                    multiResults = MultiPlatformResults(),
                    isLoading = false,
                    hasSearched = false,
                    errorMessage = null,
                    hasNextPage = false
                )
            }
        }
        saveCurrentStateToCache()

        if (value.isBlank() && !_uiState.value.hasFiltersApplied) {
            clearResults()
        }
        // NOT: Burada bilinçli olarak arama BAŞLATILMIYOR.
        //
        // Eski davranış her tuş vuruşundan 400 ms sonra `search()` çağırıyordu
        // (debounced live search). Kullanıcı daha "naruto" yazarken "n", "na",
        // "nar" ... sorguları yedi motora birden gidiyor, yarıda kesilen istekler
        // raflarda alakasız/eksik sonuç bırakıyordu. Arama artık yalnızca açık bir
        // onayla tetiklenir: klavyedeki "Ara" tuşu veya arama butonu → [onSearchAction].
    }

    /** Klavyedeki "Ara" onay tuşu veya arama butonu: aramayı başlatan tek yol. */
    fun onSearchAction() {
        search(resetPage = true)
    }

    /** Rafın sorgusunu, kapsamını ve gerçekten görünen kayıtlarını tek seferde devral. */
    fun openSourceSearch(engine: SearchSourceEngine, query: String, scope: SearchScope, shelf: List<JikanSearchResult>) {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        val selectedScope = scope.takeIf { it in engine.availableScopes() } ?: engine.availableScopes().first()
        val mediaType = when (selectedScope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.TV -> MediaType.TvShow
            SearchScope.MOVIE -> MediaType.Movie
            else -> MediaType.Anime
        }
        val tab = when (engine) {
            SearchSourceEngine.ANILIST -> KitsugiSearchTab.Anime
            SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
            SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
            SearchSourceEngine.ALL -> KitsugiSearchTab.All
        }
        _uiState.update {
            it.copy(
                query = query,
                selectedEngine = engine,
                selectedScope = selectedScope,
                currentTab = tab,
                selectedPlatform = when (engine) {
                    SearchSourceEngine.ANILIST -> SearchPlatform.AniList
                    SearchSourceEngine.MAL -> SearchPlatform.MAL
                    SearchSourceEngine.TMDB -> SearchPlatform.TMDB
                    SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
                    SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
                    SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
                    SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
                    SearchSourceEngine.ALL -> SearchPlatform.All
                },
                selectedMediaType = mediaType,
                results = shelf,
                hasSearched = shelf.isNotEmpty(),
                errorMessage = null,
                page = 1,
                hasNextPage = false
            )
        }
        if (query.isNotBlank()) search(seedResults = shelf)
    }

    fun setTab(tab: KitsugiSearchTab) {
        val currentQuery = _uiState.value.query.trim()
        val currentMultiResults = _uiState.value.multiResults
        saveCurrentStateToCache()

        val mappedPlatform = when (tab) {
            KitsugiSearchTab.TMDB -> SearchPlatform.TMDB
            KitsugiSearchTab.MAL -> SearchPlatform.MAL
            KitsugiSearchTab.Kitsu -> SearchPlatform.Kitsu
            KitsugiSearchTab.Shikimori -> SearchPlatform.Shikimori
            KitsugiSearchTab.Simkl -> SearchPlatform.Simkl
            KitsugiSearchTab.Bangumi -> SearchPlatform.Bangumi
            KitsugiSearchTab.Anime, KitsugiSearchTab.Manga -> SearchPlatform.AniList
            else -> SearchPlatform.All
        }
        val mappedMediaType = when (tab) {
            KitsugiSearchTab.Manga -> MediaType.Manga
            KitsugiSearchTab.TMDB -> MediaType.Movie
            else -> MediaType.Anime
        }

        val cached = loadStateFromCache(tab, mappedPlatform, mappedMediaType)

        // Keep active query if user was actively searching!
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query

        // Seed immediate results from multiResults if switching from All or with active query
        val seedResults = when {
            cached.results.isNotEmpty() -> cached.results
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.TMDB && currentMultiResults.tmdbResults.isNotEmpty() -> currentMultiResults.tmdbResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Anime && currentMultiResults.aniListResults.isNotEmpty() -> currentMultiResults.aniListResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.MAL && currentMultiResults.malResults.isNotEmpty() -> currentMultiResults.malResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Shikimori && currentMultiResults.shikimoriResults.isNotEmpty() -> currentMultiResults.shikimoriResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Kitsu && currentMultiResults.kitsuResults.isNotEmpty() -> currentMultiResults.kitsuResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Simkl && currentMultiResults.simklResults.isNotEmpty() -> currentMultiResults.simklResults
            currentQuery.isNotBlank() && tab == KitsugiSearchTab.Bangumi && currentMultiResults.bangumiResults.isNotEmpty() -> currentMultiResults.bangumiResults
            else -> emptyList()
        }

        val hasSearched = cached.hasSearched || seedResults.isNotEmpty()

        _uiState.update {
            it.copy(
                currentTab = tab,
                selectedPlatform = mappedPlatform,
                selectedMediaType = mappedMediaType,
                query = effectiveQuery,
                results = seedResults,
                page = 1,
                hasNextPage = true,
                hasSearched = hasSearched,
                errorMessage = cached.errorMessage
            )
        }

        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setMediaType(value: MediaType) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val currentPlatform = _uiState.value.selectedPlatform
        val targetPlatform = if (value == MediaType.Manga && currentPlatform == SearchPlatform.TMDB) {
            SearchPlatform.All
        } else {
            currentPlatform
        }
        val cached = loadStateFromCache(_uiState.value.currentTab, targetPlatform, value)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update { 
            it.copy(
                selectedMediaType = value,
                selectedPlatform = targetPlatform,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setPlatform(value: SearchPlatform) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val cached = loadStateFromCache(_uiState.value.currentTab, value, _uiState.value.selectedMediaType)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update {
            it.copy(
                selectedPlatform = value,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setPlatformAndMediaType(platform: SearchPlatform, mediaType: MediaType) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val cached = loadStateFromCache(_uiState.value.currentTab, platform, mediaType)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update {
            it.copy(
                selectedPlatform = platform,
                selectedMediaType = mediaType,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        persistSearchSelection()
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }

    fun setFilterSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isFilterSheetOpen = isOpen) }
    }

    // ── Legacy updateFilters (used by GenresTagsSheet & SearchFilterSheet) ──
    fun updateFilters(filters: SearchFilters) {
        _uiState.update {
            it.copy(
                genres = filters.genres,
                excludedGenres = filters.excludedGenres,
                tags = filters.tags,
                startYear = filters.minYear,
                endYear = filters.maxYear,
                season = filters.season?.let { s -> KitsugiMediaSeason.entries.find { e -> e.apiValue == s } },
                minScore = filters.minScore,
                maxScore = filters.maxScore
            )
        }
        search(resetPage = true)
    }

    fun resetFilters() {
        _uiState.update { current ->
            val cleared = current.copy(
                genres = emptyList(),
                excludedGenres = emptyList(),
                tags = emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = null,
                endYear = null,
                season = null,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                onMyList = null,
                isDoujin = null,
                isAdultFilter = null,
                detailSearchFilterRequest = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                page = 1,
                hasNextPage = true
            )
            when (current.selectedEngine) {
                SearchSourceEngine.ANILIST -> cleared.copy(aniListSpecificFilters = AniListSpecificFilters())
                SearchSourceEngine.MAL -> cleared.copy(malSpecificFilters = MalSpecificFilters())
                SearchSourceEngine.TMDB -> cleared.copy(tmdbSpecificFilters = TmdbSpecificFilters())
                SearchSourceEngine.SHIKIMORI -> cleared.copy(shikimoriSpecificFilters = ShikimoriSpecificFilters())
                SearchSourceEngine.KITSU -> cleared.copy(kitsuSpecificFilters = KitsuSpecificFilters())
                SearchSourceEngine.SIMKL -> cleared.copy(simklSpecificFilters = SimklSpecificFilters())
                SearchSourceEngine.BANGUMI -> cleared.copy(bangumiSpecificFilters = BangumiSpecificFilters())
                SearchSourceEngine.ALL -> cleared.copy(
                    aniListSpecificFilters = AniListSpecificFilters(),
                    malSpecificFilters = MalSpecificFilters(),
                    tmdbSpecificFilters = TmdbSpecificFilters(),
                    shikimoriSpecificFilters = ShikimoriSpecificFilters(),
                    kitsuSpecificFilters = KitsuSpecificFilters(),
                    simklSpecificFilters = SimklSpecificFilters(),
                    bangumiSpecificFilters = BangumiSpecificFilters()
                )
            }
        }
        search(resetPage = true)
    }

    // ── AniHyou-style reactive filter events ──────────────────────────────

    fun setFormats(values: List<KitsugiMediaFormat>) {
        _uiState.update { it.copy(selectedFormats = values) }
        search(resetPage = true)
    }

    fun setStatuses(values: List<KitsugiMediaStatus>) {
        _uiState.update { it.copy(selectedStatuses = values) }
        search(resetPage = true)
    }

    fun setCountry(value: KitsugiCountryOfOrigin?) {
        _uiState.update { it.copy(country = value) }
        search(resetPage = true)
    }

    fun setSources(values: List<KitsugiMediaSource>) {
        _uiState.update { it.copy(selectedSources = values) }
        search(resetPage = true)
    }

    fun setStartYear(value: Int?) {
        _uiState.update { it.copy(startYear = value) }
        search(resetPage = true)
    }

    fun setEndYear(value: Int?) {
        _uiState.update { it.copy(endYear = value) }
        search(resetPage = true)
    }

    fun setSeason(value: KitsugiMediaSeason?) {
        _uiState.update { it.copy(season = value) }
        search(resetPage = true)
    }

    fun setMinScore(value: Int?) {
        _uiState.update { it.copy(minScore = value) }
        search(resetPage = true)
    }

    fun setMaxScore(value: Int?) {
        _uiState.update { it.copy(maxScore = value) }
        search(resetPage = true)
    }

    fun setEpCh(range: IntRange?) {
        _uiState.update { it.copy(minEpCh = range?.first, maxEpCh = range?.last) }
        search(resetPage = true)
    }

    fun setDuration(range: IntRange?) {
        _uiState.update { it.copy(minDuration = range?.first, maxDuration = range?.last) }
        search(resetPage = true)
    }

    fun setSort(sortSearch: KitsugiMediaSortSearch, isDescending: Boolean) {
        _uiState.update { it.copy(sortSearch = sortSearch, isSortDescending = isDescending) }
        search(resetPage = true)
    }

    fun setOnMyList(value: Boolean?) {
        _uiState.update { it.copy(onMyList = value) }
        search(resetPage = true)
    }

    fun setIsDoujin(value: Boolean?) {
        _uiState.update { it.copy(isDoujin = value) }
        search(resetPage = true)
    }

    fun setIsAdultFilter(value: Boolean?) {
        _uiState.update { it.copy(isAdultFilter = value) }
        search(resetPage = true)
    }

    fun setShowMoreFilters(show: Boolean) {
        _uiState.update { it.copy(showMoreFilters = show) }
    }

    fun setEngine(engine: SearchSourceEngine) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val scopes = engine.availableScopes()
        val targetScope = if (_uiState.value.selectedScope in scopes) _uiState.value.selectedScope else scopes.first()
        
        val mappedTab = when (engine) {
            SearchSourceEngine.ALL -> KitsugiSearchTab.All
            SearchSourceEngine.ANILIST -> if (targetScope == SearchScope.MANGA) KitsugiSearchTab.Manga else KitsugiSearchTab.Anime
            SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
            SearchSourceEngine.BANGUMI -> KitsugiSearchTab.Bangumi
        }
        val mappedPlatform = when (engine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
            SearchSourceEngine.BANGUMI -> SearchPlatform.Bangumi
        }

        _uiState.update {
            it.copy(
                selectedEngine = engine,
                selectedScope = targetScope,
                currentTab = mappedTab,
                selectedPlatform = mappedPlatform,
                page = 1,
                hasNextPage = true
            )
        }
        persistSearchSelection()
        search(resetPage = true)
    }

    fun setScope(scope: SearchScope) {
        saveCurrentStateToCache()
        val engine = _uiState.value.selectedEngine
        val mappedMediaType = when (scope) {
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> MediaType.Manga
            SearchScope.MOVIE -> MediaType.Movie
            SearchScope.TV -> MediaType.TvShow
            else -> MediaType.Anime
        }
        val mappedTab = when (scope) {
            SearchScope.CHARACTER -> KitsugiSearchTab.Character
            SearchScope.STAFF -> KitsugiSearchTab.Staff
            SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL -> KitsugiSearchTab.Manga
            else -> if (engine == SearchSourceEngine.ALL) KitsugiSearchTab.All else _uiState.value.currentTab
        }

        _uiState.update {
            it.copy(
                selectedScope = scope,
                selectedMediaType = mappedMediaType,
                currentTab = mappedTab,
                page = 1,
                hasNextPage = true
            )
        }
        persistSearchSelection()
        search(resetPage = true)
    }

    fun updateAniListFilters(filters: AniListSpecificFilters) {
        _uiState.update { it.copy(aniListSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateMalFilters(filters: MalSpecificFilters) {
        _uiState.update { it.copy(malSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateTmdbFilters(filters: TmdbSpecificFilters) {
        _uiState.update { it.copy(tmdbSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateShikimoriFilters(filters: ShikimoriSpecificFilters) {
        _uiState.update { it.copy(shikimoriSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateKitsuFilters(filters: KitsuSpecificFilters) {
        _uiState.update { it.copy(kitsuSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateSimklFilters(filters: SimklSpecificFilters) {
        _uiState.update { it.copy(simklSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun updateBangumiFilters(filters: BangumiSpecificFilters) {
        _uiState.update { it.copy(bangumiSpecificFilters = filters) }
        search(resetPage = true)
    }

    fun resetEngineFilters(engine: SearchSourceEngine) {
        _uiState.update {
            when (engine) {
                SearchSourceEngine.ANILIST -> it.copy(aniListSpecificFilters = AniListSpecificFilters())
                SearchSourceEngine.MAL -> it.copy(malSpecificFilters = MalSpecificFilters())
                SearchSourceEngine.TMDB -> it.copy(tmdbSpecificFilters = TmdbSpecificFilters())
                SearchSourceEngine.SHIKIMORI -> it.copy(shikimoriSpecificFilters = ShikimoriSpecificFilters())
                SearchSourceEngine.KITSU -> it.copy(kitsuSpecificFilters = KitsuSpecificFilters())
                SearchSourceEngine.SIMKL -> it.copy(simklSpecificFilters = SimklSpecificFilters())
                SearchSourceEngine.BANGUMI -> it.copy(bangumiSpecificFilters = BangumiSpecificFilters())
                SearchSourceEngine.ALL -> it.copy(
                    aniListSpecificFilters = AniListSpecificFilters(),
                    malSpecificFilters = MalSpecificFilters(),
                    tmdbSpecificFilters = TmdbSpecificFilters(),
                    shikimoriSpecificFilters = ShikimoriSpecificFilters(),
                    kitsuSpecificFilters = KitsuSpecificFilters(),
                    simklSpecificFilters = SimklSpecificFilters(),
                    bangumiSpecificFilters = BangumiSpecificFilters()
                )
            }
        }
        resetFilters()
    }

    fun getActiveFilterCountForEngine(engine: SearchSourceEngine): Int {
        val state = _uiState.value
        return when (engine) {
            SearchSourceEngine.ANILIST -> state.aniListSpecificFilters.activeCount
            SearchSourceEngine.MAL -> state.malSpecificFilters.activeCount
            SearchSourceEngine.TMDB -> state.tmdbSpecificFilters.activeCount
            SearchSourceEngine.SHIKIMORI -> state.shikimoriSpecificFilters.activeCount
            SearchSourceEngine.KITSU -> state.kitsuSpecificFilters.activeCount
            SearchSourceEngine.SIMKL -> state.simklSpecificFilters.activeCount
            SearchSourceEngine.BANGUMI -> state.bangumiSpecificFilters.activeCount
            SearchSourceEngine.ALL -> state.activeFilterCount
        }
    }

    private fun planAllSourcesForState(state: SearchUiState, scope: SearchScope): AllSourcePlan {
        val base = planAllSources(scope)
        val request = state.detailSearchFilterRequest ?: return base
        return base.copy(
            aniList = base.aniList && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.ANILIST, request),
            mal = base.mal && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.MAL, request),
            tmdb = base.tmdb && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.TMDB, request),
            shikimori = base.shikimori && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SHIKIMORI, request),
            kitsu = base.kitsu && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.KITSU, request),
            simkl = base.simkl && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.SIMKL, request),
            bangumi = base.bangumi && CanonicalFilterBridge.supportsDetailFilter(SearchSourceEngine.BANGUMI, request)
        )
    }

    /**
     * Profil / detay sayfasından bir tür (genre) adına tıklandığında çağrılır.
     * [genreEnglish] her zaman İngilizce API adı olmalı (Kitsugi translation map zaten dönüştürür).
     */
    fun setGenreFilter(genreEnglish: String) {
        val cleanGenre = translateToEnglishForSearch(genreEnglish)
        val isAniListGenre = isOfficialAniListGenre(cleanGenre)
        _uiState.update {
            it.copy(
                query = "",
                genres = if (isAniListGenre) listOf(cleanGenre) else emptyList(),
                excludedGenres = emptyList(),
                tags = if (!isAniListGenre) listOf(cleanGenre) else emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = null,
                endYear = null,
                season = null,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                detailSearchFilterRequest = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                hasSearched = false
            )
        }
        search()
    }

    /**
     * Profil / detay sayfasından bir etikete (tag) tıklandığında çağrılır.
     */
    fun setTagFilter(tag: String) {
        val cleanTag = translateToEnglishForSearch(tag)
        _uiState.update {
            it.copy(
                query = "",
                genres = emptyList(),
                excludedGenres = emptyList(),
                tags = listOf(cleanTag),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = null,
                endYear = null,
                season = null,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                detailSearchFilterRequest = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                hasSearched = false
            )
        }
        search()
    }

    /**
     * Ayrıntı sayfalarındaki (Detail Pages) tıklanabilir tüm metadata öğelerini
     * (Tür, Tema, Demografi, Etiket/Keyword, Sezon+Yıl, Stüdyo, Dergi, Kanal, Format vb.)
     * tek bir kanonik model üzerinden filtre olarak ayarlar ve aramayı anında tetikler.
     */
    fun applyDetailFilterRequest(request: DetailSearchFilterRequest) {
        // Detail facets are intentionally always searched in ALL mode. `source` remains
        // available for provenance, while `tagSource` carries the tag's native taxonomy.
        val cleanGenre = request.genre?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val cleanTheme = request.theme?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val cleanDemographic = request.demographic?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val cleanTag = request.tag?.let(::translateToEnglishForSearch)?.trim()?.takeIf { it.isNotBlank() }
        val season = CanonicalFilterBridge.normalizeSeason(request.season)
        val year = request.year?.takeIf { it > 0 }
        val normalizedRequest = request.copy(
            source = SearchSourceEngine.ALL.id,
            genre = cleanGenre,
            theme = cleanTheme,
            demographic = cleanDemographic,
            tag = cleanTag,
            season = season,
            year = year
        )
        val targetScope = CanonicalFilterBridge.autoScopeGuard(SearchSourceEngine.ALL, request.mediaType)
        val facetTerms = listOfNotNull(cleanGenre, cleanTheme, cleanDemographic)
        val anyFacetTerm = (facetTerms + listOfNotNull(cleanTag)).firstOrNull()
        val isManga = request.mediaType == MediaType.Manga
        val isTv = request.mediaType == MediaType.TvShow
        val tmdbTagId = request.keywordId?.takeIf {
            it > 0 && CanonicalFilterBridge.isTmdbTagSource(request.tagSource)
        }
        val supports = { engine: SearchSourceEngine ->
            CanonicalFilterBridge.supportsDetailFilter(engine, normalizedRequest)
        }
        val aniListOfficialGenre = facetTerms.firstOrNull()?.takeIf {
            cleanTag == null && CanonicalFilterBridge.isOfficialAniListGenre(it)
        }
        val aniListTagTerms = buildList {
            if (cleanTag != null) add(cleanTag)
            facetTerms.filterNot { it == aniListOfficialGenre }.forEach { add(it) }
        }.distinct()
        val bangumiTag = request.tag?.takeIf {
            request.tagSource.equals("bangumi", ignoreCase = true) || request.tagSource.equals("bgm", ignoreCase = true)
        }?.trim()?.takeIf { it.isNotBlank() } ?: cleanTag
        val bangumiTags = listOfNotNull(cleanGenre, cleanTheme, cleanDemographic, bangumiTag).distinct()
        val kitsuCategories = listOfNotNull(cleanGenre, cleanTheme, cleanDemographic, cleanTag).distinct()

        val aniListFilters = if (supports(SearchSourceEngine.ANILIST)) {
            AniListSpecificFilters(
                season = season,
                seasonYear = year,
                startYear = if (season == null) year else null,
                endYear = if (season == null) year else null,
                genres = listOfNotNull(aniListOfficialGenre),
                tags = aniListTagTerms
            )
        } else AniListSpecificFilters()

        val malGenreId = anyFacetTerm?.let {
            CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
        }
        val malFilters = if (supports(SearchSourceEngine.MAL)) {
            MalSpecificFilters(
                season = season?.lowercase(java.util.Locale.ROOT),
                seasonYear = year,
                genres = listOfNotNull(malGenreId),
                startYear = if (season == null) year else null,
                endYear = if (season == null) year else null
            )
        } else MalSpecificFilters()

        val tmdbGenreIds = if (supports(SearchSourceEngine.TMDB) && cleanTag == null) {
            facetTerms.mapNotNull { CanonicalFilterBridge.mapTmdbGenreId(it, isTv) }.distinct()
        } else emptyList()
        val tmdbFilters = if (supports(SearchSourceEngine.TMDB)) {
            TmdbSpecificFilters(
                isMovie = request.mediaType != MediaType.TvShow,
                genres = tmdbGenreIds,
                startYear = if (season == null) year else null,
                endYear = if (season == null) year else null,
                keyword = tmdbTagId?.toString()
            )
        } else TmdbSpecificFilters()

        val shikimoriGenreId = anyFacetTerm?.let {
            CanonicalFilterBridge.mapAnimeGenreId(it)
                ?: CanonicalFilterBridge.mapMalGenreId(it, isManga)?.toIntOrNull()
        }
        val shikimoriSeason = when {
            season != null && year != null -> "${season.lowercase(java.util.Locale.ROOT)}_$year"
            year != null -> year.toString()
            else -> null
        }
        val shikimoriFilters = if (supports(SearchSourceEngine.SHIKIMORI)) {
            ShikimoriSpecificFilters(
                season = shikimoriSeason,
                genres = listOfNotNull(shikimoriGenreId)
            )
        } else ShikimoriSpecificFilters()

        val kitsuFilters = if (supports(SearchSourceEngine.KITSU)) {
            KitsuSpecificFilters(
                season = season?.lowercase(java.util.Locale.ROOT),
                seasonYear = year,
                categories = kitsuCategories
            )
        } else KitsuSpecificFilters()

        val simklGenre = anyFacetTerm?.let(CanonicalFilterBridge::mapSimklGenre)
        val simklFilters = if (supports(SearchSourceEngine.SIMKL)) {
            SimklSpecificFilters(
                genre = simklGenre,
                year = year?.toString()
            )
        } else SimklSpecificFilters()

        val bangumiFilters = if (supports(SearchSourceEngine.BANGUMI)) {
            BangumiSpecificFilters(
                tags = bangumiTags,
                yearFrom = if (season == null) year else null,
                yearTo = if (season == null) year else null
            )
        } else BangumiSpecificFilters()

        val seasonEnum = season?.let { value ->
            KitsugiMediaSeason.entries.firstOrNull { it.apiValue == value }
        }
        _uiState.update { current ->
            current.copy(
                query = "",
                selectedEngine = SearchSourceEngine.ALL,
                selectedScope = targetScope,
                currentTab = KitsugiSearchTab.All,
                selectedPlatform = SearchPlatform.All,
                selectedMediaType = request.mediaType,
                detailSearchFilterRequest = normalizedRequest,
                genres = listOfNotNull(cleanGenre ?: cleanTheme ?: cleanDemographic),
                tags = listOfNotNull(cleanTag),
                excludedGenres = emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                selectedSources = emptyList(),
                startYear = year,
                endYear = year,
                season = seasonEnum,
                minScore = null,
                maxScore = null,
                minEpCh = null,
                maxEpCh = null,
                minDuration = null,
                maxDuration = null,
                onMyList = null,
                isDoujin = null,
                isAdultFilter = null,
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                aniListSpecificFilters = aniListFilters,
                malSpecificFilters = malFilters,
                tmdbSpecificFilters = tmdbFilters,
                shikimoriSpecificFilters = shikimoriFilters,
                kitsuSpecificFilters = kitsuFilters,
                simklSpecificFilters = simklFilters,
                bangumiSpecificFilters = bangumiFilters,
                page = 1,
                hasNextPage = true,
                hasSearched = false
            )
        }
        search(resetPage = true)
    }

    private fun isOfficialAniListGenre(genre: String): Boolean {
        val officialGenres = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
            "Hentai", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
            "Supernatural", "Thriller"
        )
        return officialGenres.any { it.equals(genre.trim(), ignoreCase = true) }
    }


    private fun translateToEnglishForSearch(label: String): String =
        SearchTranslation.translateToEnglishForSearch(label)


    private fun getJikanGenreId(genreOrTag: String?): Int? {
        // Önce Türkçe ise İngilizce'ye çevir, sonra ID'yi bul
        val eng = if (genreOrTag != null) SearchTranslation.translateToEnglishForSearch(genreOrTag) else return null
        return when (eng.lowercase().trim()) {
            "action"           -> 1
            "adventure"        -> 2
            "racing"           -> 3
            "comedy"           -> 4
            "avant garde"      -> 5
            "mythology"        -> 6
            "mystery"          -> 7
            "drama"            -> 8
            "ecchi"            -> 9
            "fantasy"          -> 10
            "magic"            -> 10
            "strategy game"    -> 11
            "hentai"           -> 12
            "historical"       -> 13
            "horror"           -> 14
            "kids"             -> 15
            "martial arts"     -> 17
            "mecha"            -> 18
            "music"            -> 19
            "parody"           -> 20
            "samurai"          -> 21
            "romance"          -> 22
            "school"           -> 23
            "sci-fi"           -> 24
            "cyberpunk"        -> 24
            "shoujo ai"        -> 25
            "shounen ai"       -> 26
            "space"            -> 27
            "space opera"      -> 27
            "sports"           -> 30
            "super power"      -> 31
            "superhero"        -> 31
            "vampire"          -> 32
            "harem"            -> 33
            "slice of life"    -> 36
            "iyashikei"        -> 36
            "supernatural"     -> 37
            "youkai"           -> 37
            "military"         -> 38
            "detective"        -> 39
            "psychological"    -> 40
            "suspense","thriller" -> 41
            "seinen"           -> 42
            "josei"            -> 43
            "gourmet"          -> 47
            "workplace", "work" -> 48
            "adult cast"       -> 50
            "cgdct"            -> 52
            "cute girls doing cute things" -> 52
            "childcare"        -> 53
            "combat sports"    -> 54
            "delinquents"      -> 56
            "educational"      -> 57
            "gag humor"        -> 58
            "surreal comedy"   -> 58
            "gore"             -> 59
            "body horror"      -> 59
            "high stakes game", "death game" -> 60
            "idols"            -> 61
            "isekai"           -> 62
            "love polygon", "love triangle" -> 64
            "medicine", "medical" -> 66
            "organized crime", "mafia", "yakuza", "criminal organization" -> 67
            "otaku culture"    -> 68
            "performing arts", "showbiz" -> 69
            "pets", "animals"  -> 70
            "reincarnation"    -> 71
            "reverse harem"    -> 72
            "survival"         -> 75
            "post-apocalyptic" -> 75
            "time travel", "time loop", "time manipulation" -> 77
            "video games", "video game", "e-sports" -> 79
            "visual arts", "photography", "drawing" -> 80
            "boys' love", "boys love" -> 26
            "yuri"             -> 25
            "shounen"          -> 27
            "shoujo"           -> 25
            "seinen"           -> 42
            "josei"            -> 43
            else               -> null
        }
    }

    private fun getTmdbGenreId(genreOrTag: String?, isMovie: Boolean): Int? {
        val eng = if (genreOrTag != null) SearchTranslation.translateToEnglishForSearch(genreOrTag) else return null
        return when (eng.lowercase().trim()) {
            "action", "battle royale", "martial arts", "superhero" -> if (isMovie) 28 else 10759
            "adventure", "isekai", "survival", "post-apocalyptic" -> if (isMovie) 12 else 10759
            "comedy", "parody", "gag humor", "surreal comedy", "slapstick" -> 35
            "drama", "tragedy", "coming of age", "romance" -> 18
            "fantasy", "magic", "supernatural", "alchemy", "youkai", "mythology" -> if (isMovie) 14 else 10765
            "horror", "gore", "body horror", "cosmic horror" -> 27
            "mystery", "detective", "conspiracy", "noir" -> 9648
            "sci-fi", "cyberpunk", "space opera", "time travel", "time loop" -> if (isMovie) 878 else 10765
            "thriller", "suspense", "psychological", "espionage", "terrorism" -> 53
            "music", "band", "dancing", "musical theater" -> 10402
            "historical", "medieval", "ancient china", "samurai", "vikings" -> 36
            "crime", "mafia", "yakuza", "organized crime", "gangs" -> 80
            "family", "childcare", "parenthood" -> 10751
            "military", "war", "guns" -> if (isMovie) 10752 else 10768
            "animation", "anime" -> 16
            "documentary", "educational", "biographical" -> 99
            "western" -> if (isMovie) 37 else null
            "kids", "cgdct" -> 10762
            else -> null
        }
    }

    // AniList'e gönderilecek tür adını normalize eder (Türkçe → İngilizce)
    private fun getAniListGenreName(genre: String?): String? {
        if (genre == null) return null
        return SearchTranslation.translateToEnglishForSearch(genre)
    }

    private fun getAniListGenreNames(genres: List<String>): List<String> =
        genres.mapNotNull { getAniListGenreName(it) }

    private fun cleanSearchQuery(query: String): String {
        return query
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([a-zA-Z])([0-9])"), "$1 $2")
            .replace(Regex("([0-9])([a-zA-Z])"), "$1 $2")
            .replace("-", " ")
            .replace("_", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun generateFallbackQueries(query: String): List<String> {
        val list = mutableListOf<String>()
        val raw = query.trim()
        if (raw.isBlank()) return list

        val cleaned = cleanSearchQuery(raw)
        if (cleaned.isNotBlank() && cleaned != raw) {
            list.add(cleaned)
        }

        // Vowel typo correction (e.g. shorlock -> sherlock)
        val lower = raw.lowercase()
        if (lower.contains("shorl")) list.add(raw.replace(Regex("shorl", RegexOption.IGNORE_CASE), "sherl"))
        if (lower.contains("attak")) list.add(raw.replace(Regex("attak", RegexOption.IGNORE_CASE), "attack"))
        if (lower.contains("demn")) list.add(raw.replace(Regex("demn", RegexOption.IGNORE_CASE), "demon"))

        return list.distinct()
    }

    fun search(resetPage: Boolean = true, seedResults: List<JikanSearchResult> = emptyList()) {
        val state = _uiState.value
        if (state.query.isBlank() && !state.hasFiltersApplied) {
            clearResults()
            return
        }

        searchJob?.cancel()

        // Bu çalışmanın nesli. Sonraki aramalar sayacı ilerletir; o anda hâlâ çalışan
        // (iptali yutan) eski çalışmalar UI durumuna yazamaz.
        val generation = searchGeneration.incrementAndGet()

        val queryNotBlank = state.query.isNotBlank()
        val newHistoryItem = if (queryNotBlank) {
            SearchHistoryItem(
                query = state.query.trim(),
                platform = state.selectedPlatform,
                mediaType = state.selectedMediaType
            )
        } else null

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                page = if (resetPage) 1 else it.page,
                hasNextPage = true,
                results = seedResults,
                hasSearched = seedResults.isNotEmpty(),
                // "Tümü" aramasında yükleme bayrakları hemen (kapsama göre) yazılır:
                // aksi halde raflar bir kare boyunca boş + yüklenmiyor görünüp
                // kayboluyor, sonra shimmer ile geri geliyordu (titreme).
                multiResults = if (state.selectedEngine == SearchSourceEngine.ALL) {
                    planAllSourcesForState(state, state.selectedScope).toLoadingState()
                } else {
                    it.multiResults
                }
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val rawQuery = state.query.trim()
                val (fetched, hasNext) = executeSearchForPage(rawQuery, page = 1, generation = generation)
                // Kaynağın gevşek eşleşme/popülerlik kuyruğunu alta it; sorguyla
                // gerçekten örtüşenleri öne al.
                val results = SearchRelevance.rank(rawQuery, fetched)

                ensureActive()
                if (searchGeneration.get() != generation) return@launch

                if (newHistoryItem != null && searchHistoryEnabledState && results.isNotEmpty()) {
                    searchHistoryRepository.insertSearchQuery(newHistoryItem)
                }

                ensureActive()

                _uiState.update {
                    if (searchGeneration.get() != generation) return@update it
                    // Yenileme başarısız olsa bile kullanıcıyı getiren raf kaydı kaybolmaz.
                    val visible = mergeSourceSearchResults(seedResults, results)
                    it.copy(
                        results = visible,
                        isLoading = false,
                        hasSearched = true,
                        page = 1,
                        hasNextPage = hasNext,
                        errorMessage = if (visible.isEmpty()) "Sonuç bulunamadı." else null
                    )
                }
                if (searchGeneration.get() == generation) saveCurrentStateToCache()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    if (searchGeneration.get() != generation) return@update it
                    it.copy(
                        isLoading = false,
                        errorMessage = if (it.results.isEmpty()) e.message ?: "Arama sırasında bir hata oluştu." else null,
                        hasNextPage = false
                    )
                }
                if (searchGeneration.get() == generation) saveCurrentStateToCache()
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasNextPage) return
        if (state.query.isBlank() && !state.hasFiltersApplied) return

        val nextPage = state.page + 1
        val generation = searchGeneration.get()
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val (moreResults, hasNext) = executeSearchForPage(state.query.trim(), page = nextPage, generation = generation)
                _uiState.update { current ->
                    // Nesil değiştiyse (araya yeni bir arama girdiyse) sayfalamayı uygulama.
                    if (searchGeneration.get() != generation) return@update current
                    val currentIds = current.results.map { SearchRelevance.keyOf(it) }.toSet()
                    val uniqueNew = moreResults.filter { SearchRelevance.keyOf(it) !in currentIds }
                    current.copy(
                        results = current.results + uniqueNew,
                        page = nextPage,
                        hasNextPage = hasNext,
                        isLoadingMore = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun executeSearchForPage(
        queryText: String,
        page: Int,
        generation: Int = searchGeneration.get(),
        scopeOverride: SearchScope? = null
    ): Pair<List<JikanSearchResult>, Boolean> {
        val state = _uiState.value
        val showAdult = showAdultContentState

        val engine = state.selectedEngine
        val scope = scopeOverride ?: state.selectedScope

        // Kaynağın "Tümü" rafı anime + manga sorgularını birlikte kullanır.
        // Tam sayfa da aynı iki uç noktayı sayfalayarak sorgulamalı.
        if (scope == SearchScope.ALL_MIXED && engine in mixedMediaEngines) {
            return supervisorScope {
                suspend fun fetch(mediaScope: SearchScope): Result<Pair<List<JikanSearchResult>, Boolean>> =
                    try {
                        Result.success(executeSearchForPage(queryText, page, generation, mediaScope))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                val anime = async { fetch(SearchScope.ANIME) }
                val manga = async { fetch(SearchScope.MANGA) }
                val animeOutcome = anime.await()
                val mangaOutcome = manga.await()
                // İki istek de HATA verdiyse bunu "Sonuç bulunamadı" diye yutma: gerçek mesajı göster.
                if (animeOutcome.isFailure && mangaOutcome.isFailure) {
                    throw animeOutcome.exceptionOrNull() ?: mangaOutcome.exceptionOrNull()
                        ?: IllegalStateException("Arama başarısız")
                }
                val (animeResults, animeNext) = animeOutcome.getOrNull() ?: Pair(emptyList(), false)
                val (mangaResults, mangaNext) = mangaOutcome.getOrNull() ?: Pair(emptyList(), false)
                Pair(mergeSourceSearchResults(animeResults, mangaResults), animeNext || mangaNext)
            }
        }

        // If a specific engine is chosen (not ALL):
        if (engine != SearchSourceEngine.ALL) {
            when (engine) {
                SearchSourceEngine.MAL -> {
                    val f = state.malSpecificFilters
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = apiClient.searchMalCharacters(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STAFF -> {
                            val res = apiClient.searchMalPeople(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STUDIO -> {
                            val res = apiClient.searchMalProducers(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val res = apiClient.searchMalAdvanced(
                                query = queryText,
                                mediaType = mediaType,
                                showAdultContent = showAdult,
                                status = f.status,
                                format = f.type,
                                genres = f.genres,
                                excludedGenres = f.excludedGenres,
                                rating = f.rating,
                                minScore = f.minScore,
                                maxScore = f.maxScore,
                                producerId = f.producerId,
                                magazineId = f.magazineId,
                                letter = f.letter,
                                sort = f.sortDirection,
                                orderBy = f.orderBy,
                                page = page,
                                season = f.season,
                                seasonYear = f.seasonYear,
                                startYear = f.startYear,
                                endYear = f.endYear
                            )
                            return Pair(res, res.size >= 24)
                        }
                    }
                }
                SearchSourceEngine.TMDB -> {
                    val f = state.tmdbSpecificFilters
                    when (scope) {
                        SearchScope.STAFF -> {
                            val res = TmdbApiClient().searchPerson(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STUDIO -> {
                            val res = TmdbApiClient().searchCompany(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        else -> {
                            // TMDB discover/with_keywords sayısal keyword ID bekler; serbest metin
                            // başlığını buraya göndermek rafın bulduğu filmi/diziyi gizliyordu.
                            if (queryText.isNotBlank() && f.activeCount == 0) {
                                val res = TmdbApiClient().search(queryText, page = page).filter {
                                    when (scope) {
                                        SearchScope.MOVIE -> it.type == MediaType.Movie
                                        SearchScope.TV -> it.type == MediaType.TvShow
                                        else -> true
                                    }
                                }
                                return Pair(res, res.size >= 20)
                            }
                            val isMovie = scope == SearchScope.MOVIE || (scope != SearchScope.TV && state.selectedMediaType == MediaType.Movie)
                            val country = if (scope == SearchScope.K_DRAMA) "KR" else f.originCountry
                            val res = TmdbApiClient().discoverAdvanced(
                                isMovie = isMovie,
                                page = page,
                                sortBy = f.sortBy,
                                genres = f.genres,
                                excludedGenres = f.excludedGenres,
                                originCountry = country,
                                startYear = f.startYear,
                                endYear = f.endYear,
                                minScore = f.minScore,
                                maxScore = f.maxScore,
                                minVoteCount = f.minVoteCount,
                                minRuntime = f.minRuntime,
                                maxRuntime = f.maxRuntime,
                                watchProviderId = f.watchProviderId,
                                networkId = f.networkId,
                                keyword = if (queryText.isNotBlank()) queryText else f.keyword,
                                tvStatus = f.tvStatus,
                                tvType = f.tvType,
                                includeAdult = f.includeAdult
                            )
                            return Pair(res, res.size >= 24)
                        }
                    }
                }
                SearchSourceEngine.SHIKIMORI -> {
                    val f = state.shikimoriSpecificFilters
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = KitsugiShikimoriClient.searchCharacters(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        SearchScope.STAFF -> {
                            val res = KitsugiShikimoriClient.searchPeople(queryText, page = page)
                            return Pair(res, res.size >= 24)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val kinds = if (scope == SearchScope.MANHWA) listOf("manhwa")
                                else if (scope == SearchScope.MANHUA) listOf("manhua")
                                else if (scope == SearchScope.LIGHT_NOVEL) listOf("light_novel")
                                else f.kinds.ifEmpty { null }
                            val res = KitsugiShikimoriClient.searchMediaAdvanced(
                                mediaType = mediaType,
                                query = queryText,
                                page = page,
                                order = f.order,
                                kinds = kinds,
                                statuses = f.statuses.ifEmpty { null },
                                season = f.season,
                                score = f.minScore,
                                duration = f.duration,
                                rating = f.rating,
                                genres = f.genres,
                                excludedGenres = f.excludedGenres,
                                studioId = f.studioId,
                                publisherId = f.publisherId,
                                censored = f.censored
                            )
                            return Pair(res, res.size >= 24)
                        }
                    }
                }
                SearchSourceEngine.BANGUMI -> {
                    val f = state.bangumiSpecificFilters
                    // Bangumi arama uçları sayfa başına EN FAZLA 20 kayıt döndürür (sunucu limiti
                    // sessizce 20'ye kısar). 24 istenirse offset her sayfada 4 kayıt atlar ve
                    // `size >= 24` kontrolü "sonraki sayfa yok" sonucu verir.
                    val bangumiPageSize = BangumiApiClient.SEARCH_PAGE_SIZE
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = KitsugiBangumiClient.searchCharacters(
                                queryText, page = page, limit = bangumiPageSize, includeAdult = showAdult
                            )
                            return Pair(res, res.size >= bangumiPageSize)
                        }
                        SearchScope.STAFF -> {
                            val res = KitsugiBangumiClient.searchPeople(
                                queryText, page = page, limit = bangumiPageSize, career = f.career
                            )
                            return Pair(res, res.size >= bangumiPageSize)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val res = KitsugiBangumiClient.searchMediaAdvanced(
                                mediaType = mediaType,
                                query = queryText,
                                page = page,
                                limit = bangumiPageSize,
                                sort = f.sort,
                                tags = f.tags,
                                yearFrom = f.yearFrom,
                                yearTo = f.yearTo,
                                minScore = f.minScore,
                                includeAdult = f.nsfw || showAdult
                            )
                            return Pair(res, res.size >= bangumiPageSize)
                        }
                    }
                }
                SearchSourceEngine.KITSU -> {
                    val f = state.kitsuSpecificFilters
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val res = KitsuExploreClient.searchCharacters(queryText, page = page)
                            return Pair(res, res.size >= 20)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val subtypes = if (scope == SearchScope.MANGA) listOf("manga")
                                else if (scope == SearchScope.MANHWA) listOf("manhwa")
                                else if (scope == SearchScope.MANHUA) listOf("manhua")
                                else if (scope == SearchScope.LIGHT_NOVEL) listOf("novel")
                                else f.subtypes.ifEmpty { null }
                            val res = KitsuExploreClient.searchMediaAdvanced(
                                mediaType = mediaType,
                                query = queryText,
                                page = page,
                                sort = f.sort,
                                subtypes = subtypes,
                                statuses = f.statuses.ifEmpty { null },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                categories = f.categories,
                                ageRating = f.ageRating,
                                streamers = f.streamers,
                                minRating = f.minRating
                            )
                            return Pair(res, res.size >= 20)
                        }
                    }
                }
                SearchSourceEngine.SIMKL -> {
                    val f = state.simklSpecificFilters
                    val simklType = when (scope) {
                        SearchScope.MOVIE -> "movies"
                        SearchScope.TV -> "tv"
                        SearchScope.ANIME -> "anime"
                        else -> "anime"
                    }
                    val hasCustomFilters = f.subtype != null || f.genre != null || f.country != null || f.year != null
                    val res = if (queryText.isNotBlank() && scope == SearchScope.ALL_MIXED) {
                        // Raf da üç Simkl kataloğunu (anime, dizi, film) birlikte arıyor.
                        SimklApiClient().search(queryText, limit = 20, page = page)
                    } else if (queryText.isBlank() && !hasCustomFilters && f.trendingPeriod.isNotBlank()) {
                        SimklApiClient().getTrendingPeriodPage(simklType, f.trendingPeriod, page, pageSize = 20)
                    } else {
                        SimklApiClient().searchAdvanced(
                            type = simklType,
                            query = queryText,
                            subtype = f.subtype,
                            genre = f.genre,
                            country = f.country,
                            year = f.year,
                            sort = f.sort,
                            page = page
                        )
                    }
                    return Pair(res, res.size >= 20)
                }
                SearchSourceEngine.ANILIST -> {
                    val f = state.aniListSpecificFilters
                    when (scope) {
                        SearchScope.CHARACTER -> {
                            val paged = apiClient.searchCharacters(queryText, page = page, perPage = 24)
                            return Pair(paged.results, paged.hasNextPage)
                        }
                        SearchScope.STAFF -> {
                            val paged = apiClient.searchStaff(queryText, page = page, perPage = 24)
                            return Pair(paged.results, paged.hasNextPage)
                        }
                        SearchScope.STUDIO -> {
                            val paged = apiClient.searchStudios(queryText, page = page, perPage = 24)
                            return Pair(paged.results, paged.hasNextPage)
                        }
                        else -> {
                            val mediaType = if (scope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL)) MediaType.Manga else MediaType.Anime
                            val country = if (scope == SearchScope.MANHWA) "KR" else if (scope == SearchScope.MANHUA) "CN" else f.country
                            val format = if (scope == SearchScope.LIGHT_NOVEL) "NOVEL" else null
                            val formats = if (f.formats.isNotEmpty()) f.formats else if (format != null) listOf(format) else null

                            val paged = apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = mediaType,
                                showAdultContent = showAdult,
                                formats = formats,
                                statuses = f.statuses.ifEmpty { null },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                genres = f.genres.ifEmpty { null },
                                excludedGenres = f.excludedGenres.ifEmpty { null },
                                tags = f.tags.ifEmpty { null },
                                minYear = f.startYear,
                                maxYear = f.endYear,
                                minScore = f.minScore,
                                maxScore = f.maxScore,
                                minEpCh = f.minEpCh,
                                maxEpCh = f.maxEpCh,
                                minDuration = f.minDuration,
                                maxDuration = f.maxDuration,
                                sort = listOf(f.sort),
                                country = country,
                                sources = f.sources.ifEmpty { null },
                                isAdult = f.isAdult,
                                isLicensed = null,
                                page = page,
                                perPage = 24
                            )
                            return Pair(paged.results, paged.hasNextPage)
                        }
                    }
                }
                SearchSourceEngine.ALL -> Unit
            }
        }

        // 0. Seçenek C: "Tümü (All-in-One Çoklu Platform Arama)"
        //
        // Karakter/Personel kapsamları bu dala GİRMEZ: "Tümü" motoru seçiliyken
        // karakter araması da yedi medya rafını tetikliyordu ve Karakter sekmesi bu
        // medya kayıtlarını kişi satırı gibi listeliyordu (alakasız sonuç).
        val isShelfSearch = (state.currentTab == KitsugiSearchTab.All || engine == SearchSourceEngine.ALL) &&
            scope != SearchScope.CHARACTER && scope != SearchScope.STAFF
        if (isShelfSearch) {
            val plan = planAllSourcesForState(state, scope)
            _uiState.update { it.copy(multiResults = plan.toLoadingState()) }
            return supervisorScope {
                // Kapsam duyarlı çoklu platform araması: her kaynak yalnızca kapsamın
                // gerçekten istediği içerik ailesini sorgular.
                //   ALL_MIXED                → anime + manga (dönüşümlü birleştirilir)
                //   ANIME                    → yalnızca anime
                //   MANGA/MANHWA/MANHUA/LN   → yalnızca manga
                //   TV/MOVIE/K_DRAMA         → yalnızca TMDB + Simkl (film/dizi)
                val wantAnime = scope.wantsAnimeSources && !scope.isLiveActionScope
                val wantManga = scope.wantsMangaSources && !scope.isLiveActionScope
                val kitsuSubtypes = when (scope) {
                    SearchScope.MANHWA -> listOf("manhwa")
                    SearchScope.MANHUA -> listOf("manhua")
                    SearchScope.LIGHT_NOVEL -> listOf("novel")
                    else -> null
                }
                val aniListCountry = when (scope) {
                    SearchScope.MANHWA -> "KR"
                    SearchScope.MANHUA -> "CN"
                    else -> null
                }
                // Film/dizi kapsamlarında TMDB'nin karma (movie+tv) yanıtı istenen
                // türe süzülür; "Diziler" rafında film, "Filmler" rafında dizi çıkmaz.
                val tmdbAllowedTypes: Set<MediaType>? = when (scope) {
                    SearchScope.TV, SearchScope.K_DRAMA -> setOf(MediaType.TvShow)
                    SearchScope.MOVIE -> setOf(MediaType.Movie)
                    else -> null
                }
                // AniList'e sorgu varken POPULARITY_DESC gönderilmez: boş sort listesi
                // değişkeni hiç yazmaz ve AniList kendi alakalılık (SEARCH_MATCH)
                // sıralamasını kullanır. Popülerlik sıralaması, sorguyla gevşek
                // eşleşen popüler kayıtları rafın başına taşıyordu.
                val aniListShelfSort = if (queryText.isNotBlank()) emptyList() else listOf("POPULARITY_DESC")

                val resultsLock = Any()
                val combinedResults = mutableListOf<JikanSearchResult>()

                fun rethrowIfCancellation(error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                }

                fun onPlatformCompleted(
                    platformResults: List<JikanSearchResult>,
                    updater: (MultiPlatformResults) -> MultiPlatformResults
                ) {
                    // Bayat çalışma koruması: iptal edilen (artık geçersiz) bir arama,
                    // kaynak sonuçlarını yeni aramanın üzerine YAZAMAZ — aksi halde yeni
                    // sorgunun AniList/MAL satırları boşalırken Shikimori/Kitsu satırları
                    // eski sorgunun (alakasız) sonuçlarıyla doluyordu.
                    if (searchGeneration.get() != generation) {
                        android.util.Log.d(
                            "SearchViewModel",
                            "Bayat arama nesli (gen=$generation, güncel=${searchGeneration.get()}) — kaynak sonucu yazılmadı"
                        )
                        return
                    }
                    _uiState.update { current ->
                        val newMulti = updater(current.multiResults)
                        val merged = synchronized(resultsLock) {
                            combinedResults.addAll(platformResults)
                            // Anahtara medya türü de girer: Kitsu, Shikimori, MAL ve
                            // TMDB'de anime ile manga (film ile dizi) AYRI kimlik uzayı
                            // kullanır, aynı numara iki farklı kayda aittir. Türsüz
                            // anahtar karma aramada meşru kayıtları sessizce siliyordu.
                            combinedResults.distinctBy { SearchRelevance.keyOf(it) }
                        }
                        current.copy(
                            multiResults = newMulti,
                            results = merged,
                            hasSearched = true
                        )
                    }
                }

                suspend fun fetchSource(block: suspend () -> List<JikanSearchResult>): List<JikanSearchResult> =
                    runCatching { block() }.getOrElse { error ->
                        rethrowIfCancellation(error)
                        emptyList()
                    }

                /**
                 * Bir kaynağın anime + manga uçlarını paralel çalıştırır, sonuçları
                 * dönüşümlü birleştirir (manga, anime altında ezilmesin), sorguyla
                 * örtüşenleri öne alır ve raf sınırına indirir.
                 *
                 * [active] false ise ağ isteği hiç yapılmaz: raf boş + yüklenmiyor
                 * kalır ve çizilmez.
                 */
                suspend fun fetchMediaShelf(
                    active: Boolean,
                    anime: suspend () -> List<JikanSearchResult>,
                    manga: suspend () -> List<JikanSearchResult>
                ): List<JikanSearchResult> {
                    if (!active || (!wantAnime && !wantManga)) return emptyList()
                    val raw = withTimeoutOrNull(allSourceTimeoutMs) {
                        fetchSource {
                            coroutineScope {
                                val animePart = if (wantAnime) {
                                    async(Dispatchers.IO) { fetchSource(anime) }
                                } else {
                                    null
                                }
                                val mangaPart = if (wantManga) {
                                    async(Dispatchers.IO) { fetchSource(manga) }
                                } else {
                                    null
                                }
                                SearchRelevance.interleave(
                                    animePart?.await() ?: emptyList(),
                                    mangaPart?.await() ?: emptyList()
                                )
                            }
                        }
                    } ?: emptyList()
                    return SearchRelevance.refine(queryText, raw).take(allShelfLimit)
                }

                /** Tek içerik ailesi olan kaynaklar (TMDB, Simkl) için. */
                suspend fun fetchSingleShelf(
                    active: Boolean,
                    block: suspend () -> List<JikanSearchResult>
                ): List<JikanSearchResult> {
                    if (!active) return emptyList()
                    val raw = withTimeoutOrNull(allSourceTimeoutMs) { fetchSource(block) } ?: emptyList()
                    return SearchRelevance.refine(queryText, raw).take(allShelfLimit)
                }

                val aniListDef = async(Dispatchers.IO) {
                    val f = state.aniListSpecificFilters
                    val globalAniListGenres = state.genres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }
                        .map(::translateToEnglishForSearch)
                    val globalAniListTags = (state.tags + state.genres.filterNot { CanonicalFilterBridge.isOfficialAniListGenre(it) })
                        .map(::translateToEnglishForSearch).distinct()
                    val res = fetchMediaShelf(
                        active = plan.aniList,
                        anime = {
                            apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = MediaType.Anime,
                                showAdultContent = showAdult,
                                formats = f.formats.ifEmpty { state.selectedFormats.map { it.apiValue } }.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.ifEmpty { state.selectedStatuses.map { it.apiValue } }.takeIf { it.isNotEmpty() },
                                season = f.season ?: state.season?.apiValue,
                                seasonYear = f.seasonYear ?: state.startYear,
                                genres = f.genres.ifEmpty { globalAniListGenres }.takeIf { it.isNotEmpty() },
                                excludedGenres = f.excludedGenres.ifEmpty { state.excludedGenres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }.map(::translateToEnglishForSearch) }.takeIf { it.isNotEmpty() },
                                tags = f.tags.ifEmpty { globalAniListTags }.takeIf { it.isNotEmpty() },
                                minYear = f.startYear ?: state.startYear,
                                maxYear = f.endYear ?: state.endYear,
                                sort = if (f.sort == "POPULARITY_DESC") aniListShelfSort else listOf(f.sort),
                                country = aniListCountry ?: f.country,
                                sources = f.sources.takeIf { it.isNotEmpty() },
                                isAdult = f.isAdult,
                                page = 1,
                                perPage = allShelfFetchLimit
                            ).results
                        },
                        manga = {
                            apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = MediaType.Manga,
                                showAdultContent = showAdult,
                                formats = f.formats.ifEmpty { state.selectedFormats.map { it.apiValue } }.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.ifEmpty { state.selectedStatuses.map { it.apiValue } }.takeIf { it.isNotEmpty() },
                                season = f.season ?: state.season?.apiValue,
                                seasonYear = f.seasonYear ?: state.startYear,
                                genres = f.genres.ifEmpty { globalAniListGenres }.takeIf { it.isNotEmpty() },
                                excludedGenres = f.excludedGenres.ifEmpty { state.excludedGenres.filter { CanonicalFilterBridge.isOfficialAniListGenre(it) }.map(::translateToEnglishForSearch) }.takeIf { it.isNotEmpty() },
                                tags = f.tags.ifEmpty { globalAniListTags }.takeIf { it.isNotEmpty() },
                                minYear = f.startYear ?: state.startYear,
                                maxYear = f.endYear ?: state.endYear,
                                sort = if (f.sort == "POPULARITY_DESC") aniListShelfSort else listOf(f.sort),
                                country = aniListCountry ?: f.country,
                                sources = f.sources.takeIf { it.isNotEmpty() },
                                isAdult = f.isAdult,
                                page = 1,
                                perPage = allShelfFetchLimit
                            ).results
                        }
                    )
                    onPlatformCompleted(res) { it.copy(aniListResults = res, isLoadingAniList = false) }
                    res
                }

                val malDef = async(Dispatchers.IO) {
                    val f = state.malSpecificFilters
                    suspend fun searchMal(type: MediaType) = apiClient.searchMalAdvanced(
                        query = queryText,
                        mediaType = type,
                        showAdultContent = showAdult,
                        status = f.status,
                        format = f.type,
                        genres = f.genres.takeIf { it.isNotEmpty() },
                        excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                        rating = f.rating,
                        minScore = f.minScore,
                        maxScore = f.maxScore,
                        producerId = f.producerId,
                        magazineId = f.magazineId,
                        letter = f.letter,
                        sort = f.sortDirection,
                        orderBy = f.orderBy,
                        page = 1,
                        season = f.season,
                        seasonYear = f.seasonYear,
                        startYear = f.startYear,
                        endYear = f.endYear
                    )
                    val res = fetchMediaShelf(
                        active = plan.mal,
                        anime = { searchMal(MediaType.Anime) },
                        manga = { searchMal(MediaType.Manga) }
                    )
                    onPlatformCompleted(res) { it.copy(malResults = res, isLoadingMal = false) }
                    res
                }

                val tmdbDef = async(Dispatchers.IO) {
                    val f = state.tmdbSpecificFilters
                    val res = fetchSingleShelf(active = plan.tmdb) {
                        val client = TmdbApiClient()
                        val allowed = tmdbAllowedTypes
                        if (queryText.isNotBlank() && f.activeCount == 0) {
                            val list = client.search(queryText)
                            if (allowed == null) list else list.filter { it.type in allowed }
                        } else {
                            val mediaKinds = allowed ?: setOf(MediaType.Movie, MediaType.TvShow)
                            mediaKinds.flatMap { type ->
                                val isMovie = type == MediaType.Movie
                                client.discoverAdvanced(
                                    isMovie = isMovie,
                                    page = 1,
                                    sortBy = f.sortBy,
                                    genres = f.genres.takeIf { it.isNotEmpty() },
                                    excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                                    originCountry = f.originCountry,
                                    startYear = f.startYear ?: state.startYear,
                                    endYear = f.endYear ?: state.endYear,
                                    minScore = f.minScore,
                                    maxScore = f.maxScore,
                                    minVoteCount = f.minVoteCount,
                                    minRuntime = f.minRuntime,
                                    maxRuntime = f.maxRuntime,
                                    watchProviderId = f.watchProviderId,
                                    networkId = f.networkId,
                                    keyword = f.keyword,
                                    tvStatus = f.tvStatus,
                                    tvType = f.tvType,
                                    includeAdult = f.includeAdult
                                )
                            }.distinctBy { SearchRelevance.keyOf(it) }
                        }
                    }
                    onPlatformCompleted(res) { it.copy(tmdbResults = res, isLoadingTmdb = false) }
                    res
                }

                val shikimoriDef = async(Dispatchers.IO) {
                    val f = state.shikimoriSpecificFilters
                    suspend fun searchShikimori(type: MediaType) = KitsugiShikimoriClient.searchMediaAdvanced(
                        mediaType = type,
                        query = queryText,
                        page = 1,
                        limit = allShelfFetchLimit,
                        order = f.order,
                        kinds = f.kinds.takeIf { it.isNotEmpty() },
                        statuses = f.statuses.takeIf { it.isNotEmpty() },
                        season = f.season,
                        score = f.minScore,
                        duration = f.duration,
                        rating = f.rating,
                        genres = f.genres.takeIf { it.isNotEmpty() },
                        excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                        studioId = f.studioId,
                        publisherId = f.publisherId,
                        censored = f.censored
                    )
                    val res = fetchMediaShelf(
                        active = plan.shikimori,
                        anime = { searchShikimori(MediaType.Anime) },
                        manga = { searchShikimori(MediaType.Manga) }
                    )
                    onPlatformCompleted(res) { it.copy(shikimoriResults = res, isLoadingShikimori = false) }
                    res
                }

                val bangumiDef = async(Dispatchers.IO) {
                    val f = state.bangumiSpecificFilters
                    suspend fun searchBangumi(type: MediaType) = KitsugiBangumiClient.searchMediaAdvanced(
                        mediaType = type,
                        query = queryText,
                        page = 1,
                        limit = BangumiApiClient.SEARCH_PAGE_SIZE,
                        sort = f.sort,
                        tags = f.tags,
                        yearFrom = f.yearFrom,
                        yearTo = f.yearTo,
                        minScore = f.minScore,
                        includeAdult = f.nsfw || showAdult
                    )
                    val res = fetchMediaShelf(
                        active = plan.bangumi,
                        anime = { searchBangumi(MediaType.Anime) },
                        manga = { searchBangumi(MediaType.Manga) }
                    )
                    onPlatformCompleted(res) { it.copy(bangumiResults = res, isLoadingBangumi = false) }
                    res
                }

                val kitsuDef = async(Dispatchers.IO) {
                    val f = state.kitsuSpecificFilters
                    val res = fetchMediaShelf(
                        active = plan.kitsu,
                        anime = {
                            KitsuExploreClient.searchMediaAdvanced(
                                mediaType = MediaType.Anime,
                                query = queryText,
                                limit = allShelfFetchLimit,
                                sort = f.sort,
                                subtypes = f.subtypes.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.takeIf { it.isNotEmpty() },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                categories = f.categories.takeIf { it.isNotEmpty() },
                                ageRating = f.ageRating,
                                streamers = f.streamers.takeIf { it.isNotEmpty() },
                                minRating = f.minRating
                            )
                        },
                        manga = {
                            KitsuExploreClient.searchMediaAdvanced(
                                mediaType = MediaType.Manga,
                                query = queryText,
                                limit = allShelfFetchLimit,
                                sort = f.sort,
                                subtypes = kitsuSubtypes ?: f.subtypes.takeIf { it.isNotEmpty() },
                                statuses = f.statuses.takeIf { it.isNotEmpty() },
                                season = f.season,
                                seasonYear = f.seasonYear,
                                categories = f.categories.takeIf { it.isNotEmpty() },
                                ageRating = f.ageRating,
                                streamers = f.streamers.takeIf { it.isNotEmpty() },
                                minRating = f.minRating
                            )
                        }
                    )
                    onPlatformCompleted(res) { it.copy(kitsuResults = res, isLoadingKitsu = false) }
                    res
                }

                val simklDef = async(Dispatchers.IO) {
                    val f = state.simklSpecificFilters
                    val res = fetchSingleShelf(active = plan.simkl) {
                        val client = SimklApiClient()
                        if (queryText.isNotBlank() && f.activeCount == 0) {
                            client.search(
                                queryText,
                                type = scope.simklTypeFilter,
                                limit = allShelfFetchLimit
                            )
                        } else {
                            client.searchAdvanced(
                                type = scope.simklTypeFilter ?: "anime",
                                query = queryText,
                                subtype = f.subtype,
                                genre = f.genre,
                                country = f.country,
                                year = f.year,
                                sort = f.sort,
                                limit = allShelfFetchLimit,
                                page = 1
                            )
                        }
                    }
                    onPlatformCompleted(res) { it.copy(simklResults = res, isLoadingSimkl = false) }
                    res
                }

                val aniListRes = runCatching { aniListDef.await() }.getOrDefault(emptyList())
                val malRes = runCatching { malDef.await() }.getOrDefault(emptyList())
                val tmdbRes = runCatching { tmdbDef.await() }.getOrDefault(emptyList())
                val shikimoriRes = runCatching { shikimoriDef.await() }.getOrDefault(emptyList())
                val kitsuRes = runCatching { kitsuDef.await() }.getOrDefault(emptyList())
                val simklRes = runCatching { simklDef.await() }.getOrDefault(emptyList())
                val bangumiRes = runCatching { bangumiDef.await() }.getOrDefault(emptyList())

                val finalCombined = SearchRelevance.dedupe(
                    aniListRes + malRes + tmdbRes + shikimoriRes + kitsuRes + simklRes + bangumiRes
                )

                if (searchGeneration.get() != generation) {
                    // Bu çalışma arada başlayan yeni bir arama tarafından geçersiz kılındı;
                    // hiçbir şey yazmıyoruz (çağıran tarafın ensureActive() kontrolü de var).
                    return@supervisorScope Pair(_uiState.value.results, false)
                }

                _uiState.update {
                    it.copy(
                        multiResults = MultiPlatformResults(
                            aniListResults = aniListRes,
                            malResults = malRes,
                            tmdbResults = tmdbRes,
                            shikimoriResults = shikimoriRes,
                            kitsuResults = kitsuRes,
                            simklResults = simklRes,
                            bangumiResults = bangumiRes,
                            isLoadingAniList = false,
                            isLoadingMal = false,
                            isLoadingTmdb = false,
                            isLoadingShikimori = false,
                            isLoadingKitsu = false,
                            isLoadingSimkl = false,
                            isLoadingBangumi = false
                        ),
                        results = finalCombined
                    )
                }

                Pair(finalCombined, false)
            }
        }

        // 1. MyAnimeList (MAL)
        if (state.currentTab == KitsugiSearchTab.MAL || state.selectedPlatform == SearchPlatform.MAL) {
            val results = apiClient.searchMALOnly(
                query = queryText,
                mediaType = state.selectedMediaType,
                showAdultContent = showAdult
            )
            return Pair(results, false)
        }

        // 2. Shikimori (Rusya / Shikimori.one)
        if (state.currentTab == KitsugiSearchTab.Shikimori || state.selectedPlatform == SearchPlatform.Shikimori) {
            val f = state.shikimoriSpecificFilters
            val results = when (state.selectedScope) {
                SearchScope.CHARACTER -> KitsugiShikimoriClient.searchCharacters(queryText, page = page, limit = 24)
                SearchScope.STAFF -> KitsugiShikimoriClient.searchPeople(queryText, page = page, limit = 24, kind = f.peopleKind)
                else -> {
                    val targetType = if (state.selectedScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) || state.selectedMediaType == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                    KitsugiShikimoriClient.searchMediaAdvanced(
                        mediaType = targetType,
                        query = queryText,
                        page = page,
                        limit = 24,
                        order = f.order,
                        kinds = f.kinds.takeIf { it.isNotEmpty() },
                        statuses = f.statuses.takeIf { it.isNotEmpty() },
                        season = f.season,
                        score = f.minScore,
                        duration = f.duration,
                        rating = f.rating,
                        genres = f.genres.takeIf { it.isNotEmpty() },
                        excludedGenres = f.excludedGenres.takeIf { it.isNotEmpty() },
                        studioId = f.studioId,
                        publisherId = f.publisherId,
                        censored = f.censored
                    )
                }
            }
            return Pair(results, results.size >= 20)
        }

        // 3. Kitsu
        if (state.currentTab == KitsugiSearchTab.Kitsu || state.selectedPlatform == SearchPlatform.Kitsu) {
            val f = state.kitsuSpecificFilters
            val results = when (state.selectedScope) {
                SearchScope.CHARACTER -> KitsuExploreClient.searchCharacters(queryText, page = page, limit = 20)
                else -> {
                    val targetType = if (state.selectedScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) || state.selectedMediaType == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                    KitsuExploreClient.searchMediaAdvanced(
                        mediaType = targetType,
                        query = queryText,
                        page = page,
                        limit = 20,
                        sort = f.sort,
                        subtypes = f.subtypes.takeIf { it.isNotEmpty() },
                        statuses = f.statuses.takeIf { it.isNotEmpty() },
                        season = f.season,
                        seasonYear = f.seasonYear,
                        categories = f.categories.takeIf { it.isNotEmpty() },
                        ageRating = f.ageRating,
                        streamers = f.streamers.takeIf { it.isNotEmpty() },
                        minRating = f.minRating
                    )
                }
            }
            return Pair(results, results.size >= 20)
        }

        // 4. Simkl
        if (state.currentTab == KitsugiSearchTab.Simkl || state.selectedPlatform == SearchPlatform.Simkl) {
            val simklType = when (state.selectedMediaType) {
                MediaType.Movie -> "movie"
                MediaType.TvShow -> "tv"
                MediaType.Anime -> "anime"
                else -> null
            }
            val results = SimklApiClient().search(queryText, type = simklType, limit = 20, page = page)
            return Pair(results, results.size >= 20)
        }

        // 4b. Bangumi (Çin / bgm.tv)
        if (state.currentTab == KitsugiSearchTab.Bangumi || state.selectedPlatform == SearchPlatform.Bangumi) {
            val f = state.bangumiSpecificFilters
            val bangumiPageSize = BangumiApiClient.SEARCH_PAGE_SIZE
            val results = when (state.selectedScope) {
                SearchScope.CHARACTER -> KitsugiBangumiClient.searchCharacters(
                    queryText, page = page, limit = bangumiPageSize, includeAdult = showAdult
                )
                SearchScope.STAFF -> KitsugiBangumiClient.searchPeople(
                    queryText, page = page, limit = bangumiPageSize, career = f.career
                )
                else -> {
                    val targetType = if (state.selectedScope in listOf(SearchScope.MANGA, SearchScope.MANHWA, SearchScope.MANHUA, SearchScope.LIGHT_NOVEL) || state.selectedMediaType == MediaType.Manga) {
                        MediaType.Manga
                    } else {
                        MediaType.Anime
                    }
                    KitsugiBangumiClient.searchMediaAdvanced(
                        mediaType = targetType,
                        query = queryText,
                        page = page,
                        limit = bangumiPageSize,
                        sort = f.sort,
                        tags = f.tags,
                        yearFrom = f.yearFrom,
                        yearTo = f.yearTo,
                        minScore = f.minScore,
                        includeAdult = f.nsfw || showAdult
                    )
                }
            }
            return Pair(results, results.size >= bangumiPageSize)
        }

        // 5. Karakter Arama (AniHyou)
        if (state.currentTab == KitsugiSearchTab.Character) {
            val paged = apiClient.searchCharacters(queryText, page = page, perPage = 24)
            return Pair(paged.results, paged.hasNextPage)
        }

        // 6. Personel / Seslendirmen Arama (AniHyou)
        if (state.currentTab == KitsugiSearchTab.Staff) {
            val paged = apiClient.searchStaff(queryText, page = page, perPage = 24)
            return Pair(paged.results, paged.hasNextPage)
        }

        // 7. TMDB Platformu (Film & Dizi)
        if (state.currentTab == KitsugiSearchTab.TMDB || state.selectedPlatform == SearchPlatform.TMDB) {
            val tmdbGenreId = getTmdbGenreId(state.genres.firstOrNull() ?: state.tags.firstOrNull(), state.selectedMediaType == MediaType.Movie)
            val results = if (queryText.isBlank() && tmdbGenreId != null) {
                TmdbApiClient().discoverByGenre(tmdbGenreId, state.selectedMediaType == MediaType.Movie)
            } else if (queryText.isNotBlank()) {
                TmdbApiClient().search(queryText, page = page)
            } else {
                emptyList()
            }
            return Pair(results, results.size >= 20)
        }

        // 5. Anime / Manga Arama (AniHyou Standardında AniList GraphQL Motoru)
        val targetMediaType = if (state.currentTab == KitsugiSearchTab.Manga || state.selectedMediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
        val formatStrings = state.selectedFormats.map { it.apiValue }
        val statusStrings = state.selectedStatuses.map { it.apiValue }
        val sourceStrings = state.selectedSources.map { it.apiValue }
        val aniListCountry = state.country?.code
        val aniListGenres = getAniListGenreNames(state.genres.filter { isOfficialAniListGenre(it) })
        val aniListExcludedGenres = getAniListGenreNames(state.excludedGenres.filter { isOfficialAniListGenre(it) })
        val aniListTags = state.tags.toMutableList().apply {
            addAll(state.genres.filter { !isOfficialAniListGenre(it) })
        }.distinct()
        val effectiveSort = state.effectiveSortApiValue
        val aniListSort = when {
            effectiveSort == "SEARCH_MATCH" && queryText.isNotBlank() -> emptyList()
            effectiveSort == "SEARCH_MATCH" -> listOf("POPULARITY_DESC")
            else -> listOf(effectiveSort)
        }
        val isDoujinFlag = when (state.isDoujin) {
            true -> false
            false -> true
            null -> null
        }
        val fallbacks = generateFallbackQueries(queryText)

        return runCatching {
            var paged = apiClient.searchAniListPaged(
                query = queryText,
                mediaType = targetMediaType,
                showAdultContent = showAdult,
                formats = formatStrings.takeIf { it.isNotEmpty() },
                statuses = statusStrings.takeIf { it.isNotEmpty() },
                season = state.season?.apiValue,
                genres = aniListGenres.takeIf { it.isNotEmpty() },
                excludedGenres = aniListExcludedGenres.takeIf { it.isNotEmpty() },
                tags = aniListTags.takeIf { it.isNotEmpty() },
                minYear = state.startYear,
                maxYear = state.endYear,
                minScore = state.minScore,
                maxScore = state.maxScore,
                minEpCh = state.minEpCh,
                maxEpCh = state.maxEpCh,
                minDuration = state.minDuration,
                maxDuration = state.maxDuration,
                sort = aniListSort,
                country = aniListCountry,
                sources = sourceStrings.takeIf { it.isNotEmpty() },
                isAdult = state.isAdultFilter,
                isLicensed = isDoujinFlag,
                page = page,
                perPage = 24
            )

            // İlk sayfada sonuç boşsa fallback sorgularını dene
            if (paged.results.isEmpty() && page == 1 && queryText.isNotBlank()) {
                for (fb in fallbacks) {
                    if (fb != queryText) {
                        val fbPaged = apiClient.searchAniListPaged(
                            query = fb,
                            mediaType = targetMediaType,
                            showAdultContent = showAdult,
                            formats = formatStrings.takeIf { it.isNotEmpty() },
                            statuses = statusStrings.takeIf { it.isNotEmpty() },
                            season = state.season?.apiValue,
                            genres = aniListGenres.takeIf { it.isNotEmpty() },
                            excludedGenres = aniListExcludedGenres.takeIf { it.isNotEmpty() },
                            tags = aniListTags.takeIf { it.isNotEmpty() },
                            minYear = state.startYear,
                            maxYear = state.endYear,
                            minScore = state.minScore,
                            maxScore = state.maxScore,
                            minEpCh = state.minEpCh,
                            maxEpCh = state.maxEpCh,
                            minDuration = state.minDuration,
                            maxDuration = state.maxDuration,
                            sort = aniListSort,
                            country = aniListCountry,
                            sources = sourceStrings.takeIf { it.isNotEmpty() },
                            isAdult = state.isAdultFilter,
                            isLicensed = isDoujinFlag,
                            page = 1,
                            perPage = 24
                        )
                        if (fbPaged.results.isNotEmpty()) {
                            paged = fbPaged
                            break
                        }
                    }
                }
            }

            // Kitsu fallback — AniList boşsa ve ilk sayfadaysa
            if (paged.results.isEmpty() && page == 1 && queryText.isNotBlank()) {
                val kitRes = runCatching<List<JikanSearchResult>> {
                    com.kitsugi.animelist.data.remote.KitsuExploreClient.searchAnime(
                        query = queryText,
                        mediaType = targetMediaType
                    )
                }.getOrElse { emptyList() }
                if (kitRes.isNotEmpty()) {
                    return@runCatching Pair(kitRes, false)
                }
            }

            Pair(paged.results, paged.hasNextPage)
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Pair(emptyList(), false)
        }
    }

    private suspend fun executeSearchForQuery(queryText: String): List<JikanSearchResult> =
        executeSearchForPage(queryText, 1).first

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearSearchHistory()
        }
    }

    fun removeHistoryItem(item: SearchHistoryItem) {
        viewModelScope.launch {
            searchHistoryRepository.deleteSearchQuery(item.query)
        }
    }

    fun applyHistoryItem(item: SearchHistoryItem) {
        _uiState.update {
            it.copy(
                query = item.query
            )
        }
        search()
    }

    fun clearResults() {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _uiState.update { 
            it.copy(
                results = emptyList(),
                multiResults = MultiPlatformResults(),
                hasSearched = false,
                errorMessage = null
            )
        }
        saveCurrentStateToCache()
    }

    /**
     * Arama çubuğundaki X butonuna basıldığında çağrılır.
     * Hem sorgu metnini hem arama sonuçlarını sıfırlar → geçmiş görünümüne dönüş.
     */
    fun clearQuery() {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                multiResults = MultiPlatformResults(),
                hasSearched = false,
                errorMessage = null
            )
        }
        saveCurrentStateToCache()
    }

    /**
     * Bir eklenti seçildiğinde çağrılır.
     * [apiName] null ise ve [keepPlatformCs3] true ise, platform CS3'te (Eklentiler) kalır ama tüm eklentiler aranır.
     * [apiName] null ise ve [keepPlatformCs3] false ise, platform All (Tümü) olur.
     */
    fun setSelectedPlugin(apiName: String?, keepPlatformCs3: Boolean = false) {
        val currentQuery = _uiState.value.query.trim()
        saveCurrentStateToCache()
        val targetPlatform = if (keepPlatformCs3 || apiName != null) SearchPlatform.CS3 else SearchPlatform.All
        val cached = loadStateFromCache(_uiState.value.currentTab, targetPlatform, _uiState.value.selectedMediaType)
        val effectiveQuery = if (currentQuery.isNotBlank()) currentQuery else cached.query
        _uiState.update {
            it.copy(
                selectedPluginApiName = apiName,
                selectedPlatform = targetPlatform,
                query = effectiveQuery,
                results = if (currentQuery.isNotBlank() && cached.results.isEmpty()) it.results else cached.results,
                hasSearched = cached.hasSearched || (currentQuery.isNotBlank() && it.hasSearched),
                errorMessage = cached.errorMessage
            )
        }
        if (effectiveQuery.isNotBlank() || _uiState.value.hasFiltersApplied) {
            search(resetPage = true)
        }
    }
}

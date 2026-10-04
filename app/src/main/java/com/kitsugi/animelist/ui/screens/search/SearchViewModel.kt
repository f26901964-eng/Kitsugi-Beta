package com.kitsugi.animelist.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.repository.SearchHistoryRepository
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

    init {
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

    private var debounceJob: Job? = null

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        saveCurrentStateToCache()

        debounceJob?.cancel()
        if (value.isBlank() && !_uiState.value.hasFiltersApplied) {
            clearResults()
            return
        }
        // AniHyou debounced live search
        debounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            search(resetPage = true)
        }
    }

    fun onSearchAction() {
        debounceJob?.cancel()
        search(resetPage = true)
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
        _uiState.update {
            it.copy(
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
                sortSearch = KitsugiMediaSortSearch.SEARCH_MATCH,
                isSortDescending = true,
                page = 1,
                hasNextPage = true
            )
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
        }
        val mappedPlatform = when (engine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
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

    fun resetEngineFilters(engine: SearchSourceEngine) {
        _uiState.update {
            when (engine) {
                SearchSourceEngine.ANILIST -> it.copy(aniListSpecificFilters = AniListSpecificFilters())
                SearchSourceEngine.MAL -> it.copy(malSpecificFilters = MalSpecificFilters())
                SearchSourceEngine.TMDB -> it.copy(tmdbSpecificFilters = TmdbSpecificFilters())
                SearchSourceEngine.SHIKIMORI -> it.copy(shikimoriSpecificFilters = ShikimoriSpecificFilters())
                SearchSourceEngine.KITSU -> it.copy(kitsuSpecificFilters = KitsuSpecificFilters())
                SearchSourceEngine.SIMKL -> it.copy(simklSpecificFilters = SimklSpecificFilters())
                SearchSourceEngine.ALL -> it.copy(
                    aniListSpecificFilters = AniListSpecificFilters(),
                    malSpecificFilters = MalSpecificFilters(),
                    tmdbSpecificFilters = TmdbSpecificFilters(),
                    shikimoriSpecificFilters = ShikimoriSpecificFilters(),
                    kitsuSpecificFilters = KitsuSpecificFilters(),
                    simklSpecificFilters = SimklSpecificFilters()
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
            SearchSourceEngine.ALL -> 0
        }
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
        val targetEngine = CanonicalFilterBridge.sourceToEngine(request.source)
        val targetScope = CanonicalFilterBridge.autoScopeGuard(targetEngine, request.mediaType)
        val cleanGenre = request.genre?.let { translateToEnglishForSearch(it) }
        val cleanTheme = request.theme?.let { translateToEnglishForSearch(it) }
        val cleanDemographic = request.demographic?.let { translateToEnglishForSearch(it) }
        val cleanTag = request.tag?.let { translateToEnglishForSearch(it) }

        val mappedTab = when (targetEngine) {
            SearchSourceEngine.ALL -> KitsugiSearchTab.All
            SearchSourceEngine.ANILIST -> if (targetScope == SearchScope.MANGA) KitsugiSearchTab.Manga else KitsugiSearchTab.Anime
            SearchSourceEngine.MAL -> KitsugiSearchTab.MAL
            SearchSourceEngine.TMDB -> KitsugiSearchTab.TMDB
            SearchSourceEngine.SHIKIMORI -> KitsugiSearchTab.Shikimori
            SearchSourceEngine.KITSU -> KitsugiSearchTab.Kitsu
            SearchSourceEngine.SIMKL -> KitsugiSearchTab.Simkl
        }
        val mappedPlatform = when (targetEngine) {
            SearchSourceEngine.ALL -> SearchPlatform.All
            SearchSourceEngine.ANILIST -> SearchPlatform.AniList
            SearchSourceEngine.MAL -> SearchPlatform.MAL
            SearchSourceEngine.TMDB -> SearchPlatform.TMDB
            SearchSourceEngine.SHIKIMORI -> SearchPlatform.Shikimori
            SearchSourceEngine.KITSU -> SearchPlatform.Kitsu
            SearchSourceEngine.SIMKL -> SearchPlatform.Simkl
        }

        _uiState.update { current ->
            var newState = current.copy(
                query = "",
                selectedEngine = targetEngine,
                selectedScope = targetScope,
                currentTab = mappedTab,
                selectedPlatform = mappedPlatform,
                selectedMediaType = request.mediaType,
                genres = if (cleanGenre != null) listOf(cleanGenre) else emptyList(),
                tags = if (cleanTag != null) listOf(cleanTag) else emptyList(),
                excludedGenres = emptyList(),
                selectedFormats = emptyList(),
                selectedStatuses = emptyList(),
                country = null,
                startYear = request.year,
                endYear = request.year,
                page = 1,
                hasNextPage = true,
                hasSearched = false
            )

            when (targetEngine) {
                SearchSourceEngine.ANILIST -> {
                    val currentF = newState.aniListSpecificFilters
                    val newGenres = if (cleanGenre != null) listOf(cleanGenre) else emptyList()
                    val newTags = if (cleanTag != null) listOf(cleanTag) else emptyList()
                    val newFormats = if (!request.format.isNullOrBlank()) listOf(request.format.uppercase()) else emptyList()
                    val newStatuses = if (!request.status.isNullOrBlank()) listOf(request.status.uppercase()) else emptyList()
                    newState = newState.copy(
                        aniListSpecificFilters = currentF.copy(
                            genres = newGenres,
                            tags = newTags,
                            formats = newFormats,
                            statuses = newStatuses,
                            season = request.season?.uppercase(),
                            seasonYear = request.year,
                            startYear = request.year,
                            country = request.countryCode
                        )
                    )
                }
                SearchSourceEngine.MAL -> {
                    val currentF = newState.malSpecificFilters
                    val malGenreIdStr = cleanGenre?.let { CanonicalFilterBridge.mapMalGenreId(it, request.mediaType == MediaType.Manga) }
                        ?: cleanTheme?.let { CanonicalFilterBridge.mapMalGenreId(it, request.mediaType == MediaType.Manga) }
                        ?: cleanDemographic?.let { CanonicalFilterBridge.mapMalGenreId(it, request.mediaType == MediaType.Manga) }
                    val malGenreId = malGenreIdStr?.toIntOrNull()
                    val newGenres = if (malGenreId != null) listOf(malGenreId) else emptyList()
                    newState = newState.copy(
                        malSpecificFilters = currentF.copy(
                            genres = newGenres,
                            type = request.format?.lowercase(),
                            status = request.status?.lowercase(),
                            season = request.season?.lowercase(),
                            seasonYear = request.year,
                            producerId = request.producerId ?: request.studioId,
                            magazineId = request.magazineId,
                            rating = request.ageRating,
                            orderBy = request.sortBy ?: currentF.orderBy
                        )
                    )
                }
                SearchSourceEngine.TMDB -> {
                    val currentF = newState.tmdbSpecificFilters
                    val isTv = targetScope == SearchScope.TV || request.mediaType == MediaType.TvShow
                    val tmdbGenreId = cleanGenre?.let { CanonicalFilterBridge.mapTmdbGenreId(it, isTv) }
                    val newGenres = if (tmdbGenreId != null) listOf(tmdbGenreId) else emptyList()
                    newState = newState.copy(
                        tmdbSpecificFilters = currentF.copy(
                            genres = newGenres,
                            originCountry = request.countryCode,
                            startYear = request.year,
                            endYear = request.year,
                            networkId = request.networkId,
                            keyword = request.keywordId?.toString() ?: cleanTag,
                            tvStatus = request.status,
                            sortBy = request.sortBy ?: currentF.sortBy
                        )
                    )
                }
                SearchSourceEngine.SHIKIMORI -> {
                    val currentF = newState.shikimoriSpecificFilters
                    val shikiGenreId = cleanGenre?.let { getJikanGenreId(it) } ?: cleanGenre?.toIntOrNull()
                    val newGenres = if (shikiGenreId != null) listOf(shikiGenreId) else emptyList()
                    val newKinds = if (!request.format.isNullOrBlank()) listOf(request.format.lowercase()) else emptyList()
                    val newStatuses = if (!request.status.isNullOrBlank()) listOf(request.status.lowercase()) else emptyList()
                    newState = newState.copy(
                        shikimoriSpecificFilters = currentF.copy(
                            genres = newGenres,
                            kinds = newKinds,
                            statuses = newStatuses,
                            season = request.year?.toString() ?: request.season?.lowercase(),
                            studioId = request.studioId,
                            publisherId = request.producerId ?: request.magazineId,
                            rating = request.ageRating,
                            order = request.sortBy ?: currentF.order
                        )
                    )
                }
                SearchSourceEngine.KITSU -> {
                    val currentF = newState.kitsuSpecificFilters
                    val cat = cleanGenre ?: cleanTag
                    val newCategories = if (cat != null) listOf(cat) else emptyList()
                    val newSubtypes = if (!request.format.isNullOrBlank()) listOf(request.format.lowercase()) else emptyList()
                    val newStatuses = if (!request.status.isNullOrBlank()) listOf(request.status.lowercase()) else emptyList()
                    newState = newState.copy(
                        kitsuSpecificFilters = currentF.copy(
                            categories = newCategories,
                            subtypes = newSubtypes,
                            statuses = newStatuses,
                            season = request.season?.lowercase(),
                            seasonYear = request.year,
                            ageRating = request.ageRating,
                            streamers = if (!request.streamerName.isNullOrBlank()) listOf(request.streamerName) else emptyList(),
                            sort = request.sortBy ?: currentF.sort
                        )
                    )
                }
                SearchSourceEngine.SIMKL -> {
                    val currentF = newState.simklSpecificFilters
                    newState = newState.copy(
                        simklSpecificFilters = currentF.copy(
                            genre = cleanGenre,
                            year = request.year?.toString(),
                            country = request.countryCode,
                            subtype = request.format?.lowercase(),
                            sort = request.sortBy ?: currentF.sort
                        )
                    )
                }
                SearchSourceEngine.ALL -> {
                    newState = newState.copy(
                        genres = if (cleanGenre != null) listOf(cleanGenre) else emptyList(),
                        tags = if (cleanTag != null) listOf(cleanTag) else emptyList(),
                        startYear = request.year,
                        endYear = request.year
                    )
                }
            }

            newState
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

    fun search(resetPage: Boolean = true) {
        val state = _uiState.value
        if (state.query.isBlank() && !state.hasFiltersApplied) {
            clearResults()
            return
        }

        searchJob?.cancel()
        debounceJob?.cancel()

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
                hasNextPage = true
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val rawQuery = state.query.trim()
                val (results, hasNext) = executeSearchForPage(rawQuery, page = 1)

                ensureActive()

                if (newHistoryItem != null && searchHistoryEnabledState && results.isNotEmpty()) {
                    searchHistoryRepository.insertSearchQuery(newHistoryItem)
                }

                ensureActive()

                _uiState.update {
                    it.copy(
                        results = results,
                        isLoading = false,
                        hasSearched = true,
                        page = 1,
                        hasNextPage = hasNext,
                        errorMessage = if (results.isEmpty()) "Sonuç bulunamadı." else null
                    )
                }
                saveCurrentStateToCache()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Arama sırasında bir hata oluştu."
                    )
                }
                saveCurrentStateToCache()
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasNextPage) return
        if (state.query.isBlank() && !state.hasFiltersApplied) return

        val nextPage = state.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val (moreResults, hasNext) = executeSearchForPage(state.query.trim(), page = nextPage)
                _uiState.update { current ->
                    val currentIds = current.results.map { "${it.source}_${it.malId}" }.toSet()
                    val uniqueNew = moreResults.filter { !currentIds.contains("${it.source}_${it.malId}") }
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

    private suspend fun executeSearchForPage(queryText: String, page: Int): Pair<List<JikanSearchResult>, Boolean> {
        val state = _uiState.value
        val showAdult = showAdultContentState

        val engine = state.selectedEngine
        val scope = state.selectedScope

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
                                seasonYear = f.seasonYear
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
                    val res = if (queryText.isBlank() && f.trendingPeriod.isNotBlank()) {
                        SimklApiClient().getTrendingPeriod(simklType, f.trendingPeriod)
                    } else {
                        SimklApiClient().searchAdvanced(
                            type = simklType,
                            query = queryText,
                            subtype = f.subtype,
                            genre = f.genre,
                            country = f.country,
                            year = f.year,
                            sort = f.sort
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
        if (state.currentTab == KitsugiSearchTab.All || engine == SearchSourceEngine.ALL) {
            _uiState.update {
                it.copy(
                    multiResults = MultiPlatformResults(
                        isLoadingAniList = true,
                        isLoadingMal = true,
                        isLoadingTmdb = true,
                        isLoadingShikimori = true,
                        isLoadingKitsu = true,
                        isLoadingSimkl = true
                    )
                )
            }
            return supervisorScope {
                val resultsLock = Any()
                val combinedResults = mutableListOf<JikanSearchResult>()

                fun onPlatformCompleted(platformResults: List<JikanSearchResult>, updater: (MultiPlatformResults) -> MultiPlatformResults) {
                    _uiState.update { current ->
                        val newMulti = updater(current.multiResults)
                        val merged = synchronized(resultsLock) {
                            combinedResults.addAll(platformResults)
                            combinedResults.distinctBy { "${it.source}_${it.malId}" }
                        }
                        current.copy(
                            multiResults = newMulti,
                            results = merged,
                            hasSearched = true
                        )
                    }
                }

                val aniListDef = async(Dispatchers.IO) {
                    val res = withTimeoutOrNull(7000L) {
                        runCatching {
                            apiClient.searchAniListPaged(
                                query = queryText,
                                mediaType = MediaType.Anime,
                                showAdultContent = showAdult,
                                page = 1,
                                perPage = 10
                            ).results
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    onPlatformCompleted(res) { it.copy(aniListResults = res, isLoadingAniList = false) }
                    res
                }

                val malDef = async(Dispatchers.IO) {
                    val res = withTimeoutOrNull(7000L) {
                        runCatching {
                            apiClient.searchMALOnly(
                                query = queryText,
                                mediaType = MediaType.Anime,
                                showAdultContent = showAdult
                            ).take(10)
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    onPlatformCompleted(res) { it.copy(malResults = res, isLoadingMal = false) }
                    res
                }

                val tmdbDef = async(Dispatchers.IO) {
                    val res = withTimeoutOrNull(7000L) {
                        runCatching {
                            TmdbApiClient().search(queryText).take(10)
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    onPlatformCompleted(res) { it.copy(tmdbResults = res, isLoadingTmdb = false) }
                    res
                }

                val shikimoriDef = async(Dispatchers.IO) {
                    val res = withTimeoutOrNull(7000L) {
                        runCatching {
                            KitsugiShikimoriClient.searchAnime(queryText, limit = 10)
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    onPlatformCompleted(res) { it.copy(shikimoriResults = res, isLoadingShikimori = false) }
                    res
                }

                val kitsuDef = async(Dispatchers.IO) {
                    val res = withTimeoutOrNull(7000L) {
                        runCatching {
                            KitsuExploreClient.searchAnime(queryText, MediaType.Anime, limit = 10)
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    onPlatformCompleted(res) { it.copy(kitsuResults = res, isLoadingKitsu = false) }
                    res
                }

                val simklDef = async(Dispatchers.IO) {
                    val res = withTimeoutOrNull(7000L) {
                        runCatching {
                            SimklApiClient().search(queryText, limit = 10)
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    onPlatformCompleted(res) { it.copy(simklResults = res, isLoadingSimkl = false) }
                    res
                }

                val aniListRes = runCatching { aniListDef.await() }.getOrDefault(emptyList())
                val malRes = runCatching { malDef.await() }.getOrDefault(emptyList())
                val tmdbRes = runCatching { tmdbDef.await() }.getOrDefault(emptyList())
                val shikimoriRes = runCatching { shikimoriDef.await() }.getOrDefault(emptyList())
                val kitsuRes = runCatching { kitsuDef.await() }.getOrDefault(emptyList())
                val simklRes = runCatching { simklDef.await() }.getOrDefault(emptyList())

                val finalCombined = (aniListRes + malRes + tmdbRes + shikimoriRes + kitsuRes + simklRes)
                    .distinctBy { "${it.source}_${it.malId}" }

                _uiState.update {
                    it.copy(
                        multiResults = MultiPlatformResults(
                            aniListResults = aniListRes,
                            malResults = malRes,
                            tmdbResults = tmdbRes,
                            shikimoriResults = shikimoriRes,
                            kitsuResults = kitsuRes,
                            simklResults = simklRes,
                            isLoadingAniList = false,
                            isLoadingMal = false,
                            isLoadingTmdb = false,
                            isLoadingShikimori = false,
                            isLoadingKitsu = false,
                            isLoadingSimkl = false
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
            val results = if (state.selectedMediaType == MediaType.Manga) {
                KitsugiShikimoriClient.searchManga(queryText, limit = 24)
            } else {
                KitsugiShikimoriClient.searchAnime(queryText, limit = 24)
            }
            return Pair(results, results.size >= 20)
        }

        // 3. Kitsu
        if (state.currentTab == KitsugiSearchTab.Kitsu || state.selectedPlatform == SearchPlatform.Kitsu) {
            val results = KitsuExploreClient.searchAnime(queryText, state.selectedMediaType, limit = 20)
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
            val results = SimklApiClient().search(queryText, type = simklType, limit = 20)
            return Pair(results, results.size >= 20)
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

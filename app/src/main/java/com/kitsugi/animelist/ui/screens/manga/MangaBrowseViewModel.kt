package com.kitsugi.animelist.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.manga.CanonicalMangaResolver
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.manga.MangaSourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// ─── Per-source fetch state ───────────────────────────────────────────────────

data class MangaSourceFetchState(
    val source: MangaSource,
    val isLoading: Boolean = false,
    val mangas: List<MangaDetails> = emptyList(),
    val error: String? = null,
    val currentPage: Int = 1,
    val hasNextPage: Boolean = true
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MangaBrowseViewModel(private val repository: MangaSourceRepository) : ViewModel() {

    data class UiState(
        val sources: List<MangaSource> = emptyList(),
        val sourceStates: List<MangaSourceFetchState> = emptyList(),
        val selectedSourceFilter: MangaSource? = null,
        val popularMangas: List<MangaDetails> = emptyList(),
        val isLoadingPopular: Boolean = false,
        val hasNextPage: Boolean = false,
        val currentPage: Int = 1,
        val searchQuery: String = ""
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    var lastInitialQuery: String? = null

    fun reset() {
        lastInitialQuery = null
        searchJob?.cancel()
        loadJob?.cancel()
        // Kaynakları sıfırla ama listede tut — refreshSources tekrar dolduracak.
        val available = repository.getAvailableSources()
        _ui.update {
            UiState(
                sources = available,
                selectedSourceFilter = available.firstOrNull()
            )
        }
        if (available.isNotEmpty()) fetchPopularMangas(1)
    }

    init { refreshSources() }

    fun refreshSources() {
        val available = repository.getAvailableSources()
        _ui.update { s ->
            s.copy(sources = available, selectedSourceFilter = s.selectedSourceFilter ?: available.firstOrNull())
        }
        if (_ui.value.searchQuery.isBlank()) fetchPopularMangas(1)
    }

    fun selectSourceFilter(source: MangaSource?) {
        _ui.update { it.copy(selectedSourceFilter = source) }
        if (_ui.value.searchQuery.isBlank() && source != null) {
            _ui.update { it.copy(popularMangas = emptyList(), currentPage = 1) }
            fetchPopularMangas(1)
        }
    }

    fun fetchPopularMangas(page: Int = 1) {
        val src = _ui.value.selectedSourceFilter ?: _ui.value.sources.firstOrNull() ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(isLoadingPopular = true) }
            try {
                val result = repository.fetchPopular(src, page)
                _ui.update { s ->
                    s.copy(
                        popularMangas  = if (page == 1) result.mangas else s.popularMangas + result.mangas,
                        isLoadingPopular = false,
                        hasNextPage    = result.hasNextPage,
                        currentPage    = page
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoadingPopular = false) }
            }
        }
    }

    fun search(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(300)
                parallelSearch(query)
            } else {
                _ui.update { it.copy(sourceStates = emptyList()) }
                fetchPopularMangas(1)
            }
        }
    }

    private val searchGate = Semaphore(6)

    private suspend fun searchSourceWithFallback(source: MangaSource, query: String, page: Int = 1): MangaSourceResult {
        val initial = source.fetchSearchManga(page, query)
        if (initial.mangas.isNotEmpty()) return initial

        // Fallback: If 0 results on page 1, try canonical core query if different from raw
        if (page == 1) {
            val canonical = CanonicalMangaResolver.resolve(query)
            val fallbackQuery = when {
                canonical.core.isNotBlank() && !canonical.core.equals(query, ignoreCase = true) -> canonical.core
                canonical.ascii.isNotBlank() && !canonical.ascii.equals(query, ignoreCase = true) -> canonical.ascii
                else -> null
            }
            if (!fallbackQuery.isNullOrBlank() && fallbackQuery.length >= 2) {
                return runCatching { source.fetchSearchManga(1, fallbackQuery) }.getOrDefault(initial)
            }
        }
        return initial
    }

    private fun filterResultsTwoPass(source: MangaSource, query: String, mangas: List<MangaDetails>): List<MangaDetails> {
        val strict = repository.postProcessSearchResults(source, query, mangas, relaxScoring = false)
        return if (strict.isNotEmpty()) {
            strict
        } else {
            repository.postProcessSearchResults(source, query, mangas, relaxScoring = true)
                .filterNot { manga ->
                    val lower = manga.title.lowercase()
                    !query.contains("dj", ignoreCase = true) &&
                    !query.contains("doujin", ignoreCase = true) &&
                    (lower.contains("dj") || lower.contains("doujinshi") || lower.contains("fan comic"))
                }
        }
    }

    private suspend fun parallelSearch(query: String) {
        val sources = repository.getSearchCandidateSources(includeTrustedFallbacks = true)
        _ui.update { it.copy(sourceStates = sources.map { s -> MangaSourceFetchState(s, isLoading = true) }, selectedSourceFilter = null) }
        supervisorScope {
            sources.forEach { src ->
                launch {
                    searchGate.withPermit {
                        try {
                            val result = withContext(Dispatchers.IO) { searchSourceWithFallback(src, query, 1) }
                            repository.recordSearchSuccess(src)
                            val matched = filterResultsTwoPass(src, query, result.mangas)
                            patchState(src.name, false, matched, null, page = 1, hasNext = result.hasNextPage)
                        } catch (e: Exception) {
                            repository.recordSearchFailure(src, e)
                            patchState(src.name, false, emptyList(), e.message ?: "Hata", page = 1, hasNext = false)
                        }
                    }
                }
            }
        }
    }

    private fun patchState(name: String, loading: Boolean, mangas: List<MangaDetails>, error: String?, page: Int = 1, hasNext: Boolean = true) {
        _ui.update { s ->
            s.copy(sourceStates = s.sourceStates.map { if (it.source.name == name) it.copy(isLoading = loading, mangas = mangas, error = error, currentPage = page, hasNextPage = hasNext) else it })
        }
    }

    fun loadNextPage() {
        val state = _ui.value
        if (state.searchQuery.isBlank()) {
            if (state.hasNextPage && !state.isLoadingPopular) {
                fetchPopularMangas(state.currentPage + 1)
            }
        } else {
            val src = state.selectedSourceFilter
            if (src != null) {
                val fetchState = state.sourceStates.firstOrNull { it.source.name == src.name }
                if (fetchState != null && fetchState.hasNextPage && !fetchState.isLoading) {
                    loadMoreSearchForSource(src, fetchState.currentPage + 1)
                }
            }
        }
    }

    private fun loadMoreSearchForSource(source: MangaSource, page: Int) {
        val query = _ui.value.searchQuery
        if (query.isBlank()) return

        viewModelScope.launch {
            _ui.update { s ->
                s.copy(sourceStates = s.sourceStates.map {
                    if (it.source.name == source.name) it.copy(isLoading = true) else it
                })
            }
            try {
                val result = withContext(Dispatchers.IO) { searchSourceWithFallback(source, query, page) }
                repository.recordSearchSuccess(source)
                val matched = filterResultsTwoPass(source, query, result.mangas)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.name == source.name) {
                            it.copy(
                                isLoading = false,
                                mangas = it.mangas + matched,
                                currentPage = page,
                                hasNextPage = result.hasNextPage
                            )
                        } else it
                    })
                }
            } catch (e: Exception) {
                repository.recordSearchFailure(source, e)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.name == source.name) {
                            it.copy(isLoading = false, error = e.message ?: "Hata", hasNextPage = false)
                        } else it
                    })
                }
            }
        }
    }

    class Factory(private val repository: MangaSourceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MangaBrowseViewModel(repository) as T
    }
}

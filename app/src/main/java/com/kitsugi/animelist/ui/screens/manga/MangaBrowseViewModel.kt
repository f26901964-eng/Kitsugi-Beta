package com.kitsugi.animelist.ui.screens.manga

import androidx.lifecycle.ViewModel
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.manga.stableSourceKey
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
    /** Kaynağın manga hata kodu (KTS-SRC-...); hata yoksa null. */
    val errorCode: String? = null,
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
    private val paginationJobs = mutableListOf<Job>()

    var lastInitialQuery: String? = null

    /**
     * Son "Oku" aramasının yedek ad listesi.
     * Aynı sorgu ama farklı dil sırası (ör. İngilizce → Orijinal ad) yeni bir
     * arama sayılır; ekran bu sayede tekrar arar.
     */
    var lastSearchFingerprint: List<String> = emptyList()

    /**
     * "Oku" butonundan gelen, dil sırasına göre aday başlıklar.
     * Arama bu sırayla denenir: ilk adla sonuç yoksa sıradaki ad kullanılır.
     */
    private var titleVariants: List<String> = emptyList()

    fun reset() {
        lastInitialQuery = null
        lastSearchFingerprint = emptyList()
        titleVariants = emptyList()
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(isLoadingPopular = false) }
            }
        }
    }

    /**
     * Kullanıcı araması. [variants] verilirse (ör. "Oku" butonundan gelen dil
     * sıralı adlar) boş sonuç dönen kaynaklar için sıradaki ad denenir.
     */
    fun search(query: String, variants: List<String> = emptyList()) {
        loadJob?.cancel()
        titleVariants = variants.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        _ui.update { it.copy(searchQuery = query) }
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
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
                return try { source.fetchSearchManga(1, fallbackQuery) } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    initial
                }
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
                            val result = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                                withContext(Dispatchers.IO) { searchSourceWithFallback(src, query, 1) }
                            } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")
                            repository.recordSearchSuccess(src)
                            var matched = filterResultsTwoPass(src, query, result.mangas)
                            // Dil sıralı yedekler: seçili adla sonuç yoksa sıradaki ad denenir
                            // (ör. İngilizce adla bulunamazsa Romaji/orijinal ad).
                            if (matched.isEmpty()) {
                                matched = searchWithVariantFallback(src, query)
                            }
                            patchState(src.stableSourceKey(), false, matched, null, page = 1, hasNext = result.hasNextPage)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            repository.recordSearchFailure(src, e)
                            patchState(src.stableSourceKey(), false, emptyList(), e.message ?: "Hata", page = 1, hasNext = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * Seçili adla sonuç gelmezse dil sıralı diğer adları dener.
     * En fazla 3 yedek sorgu — kaynakları boşa yormamak için.
     */
    private suspend fun searchWithVariantFallback(
        source: MangaSource,
        primaryQuery: String
    ): List<MangaDetails> {
        val candidates = titleVariants
            .filterNot { it.equals(primaryQuery, ignoreCase = true) }
            .take(3)
        for (candidate in candidates) {
            val attempt = try {
                kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(source, candidate, 1) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            } ?: continue
            val matched = filterResultsTwoPass(source, candidate, attempt.mangas)
            if (matched.isNotEmpty()) return matched
        }
        return emptyList()
    }

    private fun patchState(key: String, loading: Boolean, mangas: List<MangaDetails>, error: String?, page: Int = 1, hasNext: Boolean = true) {
        _ui.update { s ->
            s.copy(sourceStates = s.sourceStates.map { if (it.source.stableSourceKey() == key) it.copy(isLoading = loading, mangas = mangas, error = error, errorCode = if (error == null) null else it.errorCode, currentPage = page, hasNextPage = hasNext) else it })
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
                val fetchState = state.sourceStates.firstOrNull { it.source.stableSourceKey() == src.stableSourceKey() }
                if (fetchState != null && fetchState.hasNextPage && !fetchState.isLoading) {
                    loadMoreSearchForSource(src, fetchState.currentPage + 1)
                }
            }
        }
    }

    private fun loadMoreSearchForSource(source: MangaSource, page: Int) {
        val query = _ui.value.searchQuery
        if (query.isBlank()) return

        paginationJobs.removeAll { it.isCompleted }
        paginationJobs += viewModelScope.launch {
            _ui.update { s ->
                s.copy(sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == source.stableSourceKey()) it.copy(isLoading = true) else it
                })
            }
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(source, query, page) }
                } ?: throw java.io.IOException("Kaynak araması zaman aşımına uğradı")
                repository.recordSearchSuccess(source)
                val matched = filterResultsTwoPass(source, query, result.mangas)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                repository.recordSearchFailure(source, e)
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
                            it.copy(isLoading = false, error = e.message ?: "Hata", errorCode = SourceFailureClassifier.classifyCode(e), hasNextPage = false)
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

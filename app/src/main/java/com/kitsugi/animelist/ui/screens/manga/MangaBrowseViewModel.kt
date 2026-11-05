package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.R
import androidx.lifecycle.ViewModel
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.manga.stableSourceKey
import com.kitsugi.animelist.LocaleCache
import com.kitsugi.animelist.data.manga.CanonicalMangaResolver
import com.kitsugi.animelist.data.manga.MangaSearchAlternates
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
    val hasNextPage: Boolean = true,
    /** Bu kaynakta sonuç getiren sorgu (alternatif başlık olabilir) — sayfalama bunu kullanır. */
    val resolvedQuery: String? = null,
    /** Kaç farklı başlık denendi (çok dilli arama göstergesi). */
    val titleAttempts: Int = 0
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MangaBrowseViewModel(
    private val repository: MangaSourceRepository,
    /** Hata metinleri için uygulama bağlamı (dil değişince doğru dilde gelir). */
    private val appContext: android.content.Context? = null,
) : ViewModel() {

    private fun errorText(resId: Int): String =
        appContext?.getString(resId) ?: ""


    data class UiState(
        val sources: List<MangaSource> = emptyList(),
        val sourceStates: List<MangaSourceFetchState> = emptyList(),
        val selectedSourceFilter: MangaSource? = null,
        val popularMangas: List<MangaDetails> = emptyList(),
        val isLoadingPopular: Boolean = false,
        val hasNextPage: Boolean = false,
        val currentPage: Int = 1,
        val searchQuery: String = "",
        /** Aramada denenen tüm başlıklar (çok dilli arama). */
        val searchTitles: List<String> = emptyList()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val paginationJobs = mutableListOf<Job>()

    var lastInitialQuery: String? = null

    fun reset() {
        lastInitialQuery = null
        alternatesUsed = emptyList()
        paginationJobs.forEach { it.cancel() }
        paginationJobs.clear()
        searchJob?.cancel()
        loadJob?.cancel()
        // Kaynakları sıfırla ama listede tut — refreshSources tekrar dolduracak.
        val available = orderSourcesForLanguage(repository.getAvailableSources())
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
        val available = orderSourcesForLanguage(repository.getAvailableSources())
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

    fun search(query: String, alternates: List<String> = emptyList()) {
        loadJob?.cancel()
        alternatesUsed = alternates
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

    /** Animeden gelen ek başlıklar (romaji / İngilizce / Japonca). */
    private var alternatesUsed: List<String> = emptyList()

    // ─── Dil tercihi ─────────────────────────────────────────────────────────
    /** Uygulama içinde seçilen dil (yoksa sistem dili). */
    private val preferredLanguage: String
        get() {
            val tag = LocaleCache.localeTag
            val code = tag.takeIf { it.isNotBlank() && it != LocaleCache.UNSET }
            return (code ?: java.util.Locale.getDefault().language)
                .substringBefore('-')
                .lowercase()
        }

    /**
     * Kaynakları uygulama diline göre öne alır.
     *
     * Kararlı sıralama kullanıldığı için aynı gruptaki kaynakların mevcut
     * öncelik/sağlık sırası korunur — sadece "benim dilim" olanlar başa geçer.
     */
    private fun orderSourcesForLanguage(sources: List<MangaSource>): List<MangaSource> {
        val lang = preferredLanguage
        if (lang.isBlank()) return sources
        return sources.sortedByDescending { if (sourceMatchesLanguage(it, lang)) 1 else 0 }
    }

    private fun sourceMatchesLanguage(source: MangaSource, lang: String): Boolean =
        source.lang.trim().lowercase()
            .split(',', '/', ';', '|')
            .map { it.trim().substringBefore('-') }
            .any { it == lang }

    /**
     * Bir kaynakta aramayı **çok dilli** dener.
     *
     * 1. Kullanıcının yazdığı başlık,
     * 2. kanonik çekirdek/ASCII varyant,
     * 3. animeden gelen diğer başlıklar (romaji / İngilizce / Japonca / yerel).
     *
     * Kaynak hangi dilde kayıtlı olursa olsun eser bulunur. Sonuç bulunduğunda
     * hangi sorgunun işe yaradığı da döner — sayfalama ve puanlama bunu kullanır.
     */
    private suspend fun searchSourceWithFallback(
        source: MangaSource,
        query: String,
        page: Int = 1,
        alternates: List<String> = emptyList(),
    ): SourceSearchOutcome {
        val initial = source.fetchSearchManga(page, query)
        if (initial.mangas.isNotEmpty() || page != 1) {
            return SourceSearchOutcome(initial, query, usedAlternateTitle = false, attempts = 1)
        }

        val canonical = CanonicalMangaResolver.resolve(query)
        val candidates = buildList {
            if (canonical.core.isNotBlank() && !canonical.core.equals(query, ignoreCase = true)) {
                add(canonical.core)
            }
            if (canonical.ascii.isNotBlank() && !canonical.ascii.equals(query, ignoreCase = true)) {
                add(canonical.ascii)
            }
            addAll(MangaSearchAlternates.build(query, alternates, preferredLanguage))
        }
            .map { it.trim() }
            .filter { it.length >= 2 && !it.equals(query, ignoreCase = true) }
            .distinctBy { CanonicalMangaResolver.resolve(it).compact.ifBlank { it.lowercase() } }
            .take(MangaSearchAlternates.MAX_TITLE_ATTEMPTS)

        var attempts = 1
        candidates.forEach { candidate ->
            attempts++
            val result = try {
                source.fetchSearchManga(1, candidate)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            if (result != null && result.mangas.isNotEmpty()) {
                return SourceSearchOutcome(result, candidate, usedAlternateTitle = true, attempts = attempts)
            }
        }
        return SourceSearchOutcome(initial, query, usedAlternateTitle = false, attempts = attempts)
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
        val alternates = alternatesUsed
        val titles = MangaSearchAlternates.build(query, alternates, preferredLanguage)
        val sources = orderSourcesForLanguage(
            repository.getSearchCandidateSources(includeTrustedFallbacks = true)
        )
        _ui.update {
            it.copy(
                sourceStates = sources.map { s -> MangaSourceFetchState(s, isLoading = true) },
                selectedSourceFilter = null,
                searchTitles = titles,
            )
        }
        supervisorScope {
            sources.forEach { src ->
                launch {
                    searchGate.withPermit {
                        try {
                            val outcome = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                                withContext(Dispatchers.IO) {
                                    searchSourceWithFallback(src, query, 1, alternates)
                                }
                            } ?: throw java.io.IOException(errorText(R.string.manga_error_search_timeout).ifBlank { "search_timeout" })
                            repository.recordSearchSuccess(src)

                            // Sonuç, farklı bir dildeki başlıkla geldiyse başlık
                            // benzerliği düşük çıkar; bu durumda kaynağa güvenilir.
                            val strict = filterResultsTwoPass(src, outcome.usedQuery, outcome.result.mangas)
                            val accepted = when {
                                strict.isNotEmpty() -> strict
                                outcome.usedAlternateTitle -> outcome.result.mangas.distinctBy { it.url }
                                else -> emptyList()
                            }
                            patchState(
                                key = src.stableSourceKey(),
                                loading = false,
                                mangas = accepted,
                                error = null,
                                page = 1,
                                hasNext = outcome.result.hasNextPage,
                                resolvedQuery = outcome.usedQuery,
                                titleAttempts = outcome.attempts,
                            )
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            repository.recordSearchFailure(src, e)
                            patchState(
                                key = src.stableSourceKey(),
                                loading = false,
                                mangas = emptyList(),
                                error = e.message ?: errorText(R.string.manga_error_generic).ifBlank { "search_error" },
                                page = 1,
                                hasNext = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun patchState(
        key: String,
        loading: Boolean,
        mangas: List<MangaDetails>,
        error: String?,
        page: Int = 1,
        hasNext: Boolean = true,
        resolvedQuery: String? = null,
        titleAttempts: Int = 0,
    ) {
        _ui.update { s ->
            s.copy(
                sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == key) {
                        it.copy(
                            isLoading = loading,
                            mangas = mangas,
                            error = error,
                            errorCode = if (error == null) null else it.errorCode,
                            currentPage = page,
                            hasNextPage = hasNext,
                            resolvedQuery = resolvedQuery ?: it.resolvedQuery,
                            titleAttempts = if (titleAttempts > 0) titleAttempts else it.titleAttempts,
                        )
                    } else it
                }
            )
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
        val state = _ui.value
        val fetchState = state.sourceStates.firstOrNull { it.source.stableSourceKey() == source.stableSourceKey() }
        // Sayfalama, sonuç getiren sorguyla devam etmeli: alternatif başlıkla
        // bulunan sonuçların 2. sayfası ham sorguyla boş dönerdi.
        val query = fetchState?.resolvedQuery?.takeIf { it.isNotBlank() } ?: state.searchQuery
        if (query.isBlank()) return
        val usedAlternateTitle = fetchState?.resolvedQuery != null

        paginationJobs.removeAll { it.isCompleted }
        paginationJobs += viewModelScope.launch {
            _ui.update { s ->
                s.copy(sourceStates = s.sourceStates.map {
                    if (it.source.stableSourceKey() == source.stableSourceKey()) it.copy(isLoading = true) else it
                })
            }
            try {
                val outcome = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) { searchSourceWithFallback(source, query, page) }
                } ?: throw java.io.IOException(errorText(R.string.manga_error_search_timeout).ifBlank { "search_timeout" })
                repository.recordSearchSuccess(source)
                val strict = filterResultsTwoPass(source, outcome.usedQuery, outcome.result.mangas)
                val matched = when {
                    strict.isNotEmpty() -> strict
                    usedAlternateTitle || outcome.usedAlternateTitle ->
                        outcome.result.mangas.distinctBy { it.url }
                    else -> emptyList()
                }
                _ui.update { s ->
                    s.copy(sourceStates = s.sourceStates.map {
                        if (it.source.stableSourceKey() == source.stableSourceKey()) {
                            it.copy(
                                isLoading = false,
                                mangas = (it.mangas + matched).distinctBy { m -> m.url },
                                currentPage = page,
                                hasNextPage = outcome.result.hasNextPage
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
                            it.copy(isLoading = false, error = e.message ?: errorText(R.string.manga_error_generic).ifBlank { "error" }, errorCode = SourceFailureClassifier.classifyCode(e), hasNextPage = false)
                        } else it
                    })
                }
            }
        }
    }

    class Factory(
        private val repository: MangaSourceRepository,
        private val appContext: android.content.Context? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaBrowseViewModel(repository, appContext) as T
    }
}

/** Bir kaynakta yapılan çok dilli aramanın sonucu. */
private data class SourceSearchOutcome(
    val result: MangaSourceResult,
    val usedQuery: String,
    val usedAlternateTitle: Boolean,
    val attempts: Int,
)

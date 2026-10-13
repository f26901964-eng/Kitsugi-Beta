package com.kitsugi.animelist.ui.screens.detail.wiki

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomArticlePage
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomGuideGroup
import com.kitsugi.animelist.data.fandom.FandomGuideItem
import com.kitsugi.animelist.data.fandom.FandomMediaQuery
import com.kitsugi.animelist.data.fandom.FandomPageBlurb
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomSearchHit
import com.kitsugi.animelist.data.fandom.FandomWikiPrefs
import com.kitsugi.animelist.data.fandom.FandomWikiRef
import com.kitsugi.animelist.data.fandom.FandomWorldGuide
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Yapım (anime/manga/dizi/film) detayındaki "Wiki" sekmesinin durumu.
 *
 * Karakter sekmesiyle aynı ilkeler: sekme sayfayla birlikte doğar (iskelet gösterir),
 * wiki eşleşmesi arka planda çözülür; seri özeti, evren rehberi ve arama sonuçları
 * sekme göründükten sonra aşamalı çekilir. Rehber ve açıklamalar kalıcı önbellektedir.
 */
class FandomMediaWikiViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Ready(
            val ref: FandomWikiRef,
            val mediaKey: String,
            val resolutionKey: String = mediaKey,
            val generation: Long = 0L
        ) : UiState
        data object Unavailable : UiState
    }

    sealed interface ArticleState {
        data object Idle : ArticleState
        data object Loading : ArticleState
        data class Ready(val page: FandomCharacterPage) : ArticleState
        data object Unavailable : ArticleState
    }

    sealed interface GuideState {
        data object Idle : GuideState
        data object Loading : GuideState
        data class Ready(val guide: FandomWorldGuide) : GuideState
        data object Failed : GuideState
    }

    /**
     * "Devamını göster" akışının durumu (aynı anda tek grup yüklenir).
     * [Failed] yalnızca ilgili grubun düğmesinde "tekrar dene" ipucu gösterir; çipler kalır.
     */
    sealed interface GuideMore {
        data object Idle : GuideMore
        data class Loading(val group: FandomGuideGroup) : GuideMore
        data class Failed(val group: FandomGuideGroup) : GuideMore
    }

    sealed interface BlurbState {
        data object Idle : BlurbState
        data object Loading : BlurbState
        data class Ready(val blurb: FandomPageBlurb?) : BlurbState
        data object Failed : BlurbState
    }

    sealed interface SearchState {
        data object Idle : SearchState
        data object Loading : SearchState
        data class Ready(val hits: List<FandomSearchHit>) : SearchState
        data object Failed : SearchState
    }

    sealed interface SelectedState {
        data object Closed : SelectedState
        data object Loading : SelectedState
        data class Ready(val title: String, val article: FandomArticlePage) : SelectedState
        data class Failed(val title: String) : SelectedState
    }

    sealed interface ArticleSectionState {
        data object Loading : ArticleSectionState
        data class Loaded(val content: FandomSectionContent) : ArticleSectionState
        data object Failed : ArticleSectionState
    }

    companion object {
        private const val TAG = "FandomMediaWikiVM"
        const val KEY_SUMMARY = "media:desc"
        fun itemKey(title: String): String = "item:$title"
        fun articleSectionKey(title: String, index: Int): String = "item-sec:$index:$title"
        fun articleInfoboxKey(title: String): String = "item-info:$title"
        fun articleInfoboxFieldKey(title: String, index: Int): String = "item-if:$index:$title"
        private const val SEARCH_DEBOUNCE_MS = 350L
    }

    private val appContext = application.applicationContext
    private val translationManager by lazy { TranslationManager(appContext) }
    private val settingsDataStore by lazy { SettingsDataStore(appContext) }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _article = MutableStateFlow<ArticleState>(ArticleState.Idle)
    val article: StateFlow<ArticleState> = _article.asStateFlow()

    private val _articleSections = MutableStateFlow<Map<Int, FandomCharacterWikiViewModel.SectionState>>(emptyMap())
    val articleSections: StateFlow<Map<Int, FandomCharacterWikiViewModel.SectionState>> = _articleSections.asStateFlow()

    private val _guide = MutableStateFlow<GuideState>(GuideState.Idle)
    val guide: StateFlow<GuideState> = _guide.asStateFlow()

    private val _guideMore = MutableStateFlow<GuideMore>(GuideMore.Idle)
    val guideMore: StateFlow<GuideMore> = _guideMore.asStateFlow()

    private val _summary = MutableStateFlow<BlurbState>(BlurbState.Idle)
    val summary: StateFlow<BlurbState> = _summary.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _selected = MutableStateFlow<SelectedState>(SelectedState.Closed)
    val selected: StateFlow<SelectedState> = _selected.asStateFlow()

    private val _selectedSections = MutableStateFlow<Map<Int, ArticleSectionState>>(emptyMap())
    val selectedSections: StateFlow<Map<Int, ArticleSectionState>> = _selectedSections.asStateFlow()

    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    /**
     * Orijinal metne dönülmüş anahtarlar ([KEY_SUMMARY], [itemKey], [FandomCharacterWikiViewModel.KEY_DESCRIPTION], vb.).
     * Çeviri bellekte kalır; geçiş düğmesiyle tekrar gösterilebilir. Bu anahtarlar için
     * otomatik çeviri yeniden çalışıp üstüne yazmaz.
     */
    private val _originalKeys = MutableStateFlow<Set<String>>(emptySet())
    val originalKeys: StateFlow<Set<String>> = _originalKeys.asStateFlow()

    /**
     * Ayarlardaki çeviri uygulaması tercihi ("GOOGLE", "DEEPL", "TRANSLATE_YOU", "DEFAULT").
     * Wiki çeviri düğmeleri detay sayfasındaki açıklama düğmesiyle aynı davrandığı için
     * (dokununca harici çeviri uygulaması açılır) bu tercih UI'ya akıtılır.
     */
    private val _preferredTranslator = MutableStateFlow("DEFAULT")
    val preferredTranslator: StateFlow<String> = _preferredTranslator.asStateFlow()

    val prefs: StateFlow<FandomWikiPrefs.State> = FandomWikiPrefs.stateFlow

    private var boundKey: String? = null
    private var bindingGeneration = 0L
    private var lastQuery: FandomMediaQuery? = null
    private var resolveJob: Job? = null
    private var articleJob: Job? = null
    private var articleGeneration = 0L
    private var guideJob: Job? = null
    private var guideMoreJob: Job? = null
    private var summaryJob: Job? = null
    private var searchJob: Job? = null
    private var selectedJob: Job? = null
    private val selectedSectionJobs = HashMap<Int, Job>()
    private val translationJobs = HashMap<String, Job>()

    init {
        FandomWikiPrefs.ensureLoaded(appContext)
        viewModelScope.launch {
            FandomWikiPrefs.stateFlow
                .map { Triple(it.enabled, it.nameFallback, it.preferTurkish) }
                .distinctUntilChanged()
                .drop(1)
                .collect { rebind() }
        }
        // Detay sayfalarındaki açıklama çevirisiyle aynı tepkisellik: sekme açıkken
        // otomatik çeviri açılırsa yüklü metinler çevrilir, kapanırsa orijinale dönülür,
        // hedef dil değişince çeviriler tazelenir. Harici çeviri tercihi de buradan akar.
        viewModelScope.launch {
            var previous: Triple<Boolean, String, String>? = null
            settingsDataStore.settingsFlow
                .map { Triple(it.autoTranslateEnabled, it.translateTargetLanguage, it.preferredTranslator) }
                .distinctUntilChanged()
                .collect { current ->
                    _preferredTranslator.value = current.third
                    val previousState = previous
                    previous = current
                    if (previousState == null) return@collect
                    val autoChanged = previousState.first != current.first
                    val targetChanged = previousState.second != current.second
                    when {
                        autoChanged && !current.first -> {
                            cancelTranslations()
                            _translations.value = emptyMap()
                        }
                        (autoChanged && current.first) || (targetChanged && current.first) -> {
                            retranslateLoadedTexts(force = targetChanged)
                        }
                        else -> Unit
                    }
                }
        }
    }

    /** Sayfa açıldığında / kimlikler netleştiğinde çağrılır; aynı sorgu için tekrar çözmez. */
    fun bind(query: FandomMediaQuery?) {
        lastQuery = query
        val prefsState = FandomWikiPrefs.state(appContext)
        val key = if (query == null || !prefsState.enabled) {
            "none"
        } else {
            "${query.resolutionKey()}|${prefsState.enabled}|${prefsState.nameFallback}|${prefsState.preferTurkish}"
        }
        if (key == boundKey) return
        boundKey = key
        val generation = ++bindingGeneration
        resolveJob?.cancel()
        articleJob?.cancel()
        articleGeneration += 1
        guideJob?.cancel()
        guideMoreJob?.cancel()
        _guideMore.value = GuideMore.Idle
        summaryJob?.cancel()
        searchJob?.cancel()
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelTranslations()
        _article.value = ArticleState.Idle
        _articleSections.value = emptyMap()
        _selectedSections.value = emptyMap()
        _translations.value = emptyMap()
        _originalKeys.value = emptySet()
        _guide.value = GuideState.Idle
        _summary.value = BlurbState.Idle
        _searchQuery.value = ""
        _searchState.value = SearchState.Idle
        _selected.value = SelectedState.Closed

        if (query == null || !prefsState.enabled) {
            _state.value = UiState.Unavailable
            return
        }
        _state.value = UiState.Loading
        resolveJob = viewModelScope.launch {
            val ref = try {
                FandomRepository.resolveMediaWiki(appContext, query, allowNameFallback = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki çözümlenemedi: ${e.message}")
                null
            }
            if (generation != bindingGeneration) return@launch
            val mediaKey = query.cacheKey()
            _state.value = if (ref != null && mediaKey != null) {
                UiState.Ready(ref, mediaKey, query.resolutionKey() ?: mediaKey, generation)
            } else {
                UiState.Unavailable
            }
        }
    }

    /** Orijinal / çeviri geçişi: bu anahtar için orijinal metnin gösterilip gösterilmeyeceğini değiştirir. */
    fun toggleOriginal(key: String) {
        _originalKeys.update { if (key in it) it - key else it + key }
    }

    private fun rebind() {
        boundKey = null
        bind(lastQuery)
    }

    /** "Tekrar dene": eski (olumsuz dahil) wiki önbelleği silinir, çözümleme baştan yapılır. */
    fun retry() {
        val query = lastQuery
        val wikiId = (_state.value as? UiState.Ready)?.ref?.wikiId
        viewModelScope.launch {
            if (query != null) {
                try {
                    FandomRepository.forgetMediaWiki(appContext, query)
                    if (wikiId != null) FandomRepository.forgetMediaArticle(appContext, query, wikiId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Önbellek temizlenemedi: ${e.message}")
                }
            }
            rebind()
        }
    }

    /** Sekme görünür olduğunda çağrılır: seri özeti + evren rehberi aşamalı çekilir. */
    fun ensureContentLoaded() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        if (_article.value == ArticleState.Idle) {
            val articleRequestGeneration = articleGeneration
            _article.value = ArticleState.Loading
            articleJob = viewModelScope.launch {
                val page = try {
                    FandomRepository.resolveMediaArticle(appContext, query, ready.ref)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Yapım wiki maddesi yüklenemedi: ${e.message}")
                    null
                }
                if (articleRequestGeneration != articleGeneration || !isCurrent(ready)) return@launch
                _article.value = if (page != null) ArticleState.Ready(page) else ArticleState.Unavailable
                if (!page?.description.isNullOrBlank() && isAutoTranslateEnabled()) {
                    translate(FandomCharacterWikiViewModel.KEY_DESCRIPTION)
                }
            }
        }
        if (_summary.value == BlurbState.Idle) {
            _summary.value = BlurbState.Loading
            summaryJob = viewModelScope.launch {
                val blurb = try {
                    FandomRepository.loadMainPageBlurb(appContext, ready.ref.wikiId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Seri özeti yüklenemedi: ${e.message}")
                    null
                }
                if (!isCurrent(ready)) return@launch
                _summary.value = BlurbState.Ready(blurb)
                if (!blurb?.description.isNullOrBlank()) maybeAutoTranslate(KEY_SUMMARY)
            }
        }
        if (_guide.value == GuideState.Idle) {
            _guide.value = GuideState.Loading
            guideJob = viewModelScope.launch {
                val guide = try {
                    FandomRepository.loadWorldGuide(appContext, ready.ref.wikiId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Evren rehberi yüklenemedi: ${e.message}")
                    null
                }
                if (!isCurrent(ready)) return@launch
                _guide.value = if (guide != null) GuideState.Ready(guide) else GuideState.Failed
            }
        }
    }

    /** Retry fetching the complete article without discarding the already-resolved wiki. */
    fun retryArticle() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        articleJob?.cancel()
        articleGeneration += 1
        val oldArticleTranslationKeys = translationJobs.keys.filter(::isArticleTranslationKey).toSet()
        oldArticleTranslationKeys.forEach { key -> translationJobs.remove(key)?.cancel() }
        _translating.update { it - oldArticleTranslationKeys }
        _article.value = ArticleState.Loading
        _articleSections.value = emptyMap()
        _translations.update { map -> map.filterKeys { key -> !isArticleTranslationKey(key) } }
        viewModelScope.launch {
            try {
                FandomRepository.forgetMediaArticle(appContext, query, ready.ref.wikiId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Madde önbelleği temizlenemedi: ${e.message}")
            }
            if (!isCurrent(ready)) return@launch
            _article.value = ArticleState.Idle
            ensureContentLoaded()
        }
    }

    fun loadArticleSection(index: Int) {
        val page = (_article.value as? ArticleState.Ready)?.page ?: return
        when (_articleSections.value[index]) {
            is FandomCharacterWikiViewModel.SectionState.Loading,
            is FandomCharacterWikiViewModel.SectionState.Loaded -> return
            else -> Unit
        }
        val generation = articleGeneration
        _articleSections.update { it + (index to FandomCharacterWikiViewModel.SectionState.Loading) }
        viewModelScope.launch {
            val content = try {
                FandomRepository.loadSection(
                    appContext,
                    page.wikiId,
                    page.pageTitle,
                    index,
                    page.sections.firstOrNull { it.index == index }?.title
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki bölümü yüklenemedi ($index): ${e.message}")
                null
            }
            if (generation != articleGeneration || !isCurrentArticle(page)) return@launch
            val loaded = if (content != null) {
                FandomCharacterWikiViewModel.SectionState.Loaded(content)
            } else {
                FandomCharacterWikiViewModel.SectionState.Failed
            }
            _articleSections.update { it + (index to loaded) }
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) {
                translate(FandomCharacterWikiViewModel.sectionKey(index))
            }
        }
    }

    /** User-confirmed wiki matches are remembered across visits. */
    fun confirmMatch() {
        val ready = _state.value as? UiState.Ready ?: return
        com.kitsugi.animelist.data.fandom.FandomUserChoices.acceptWiki(
            appContext,
            ready.mediaKey,
            ready.ref.wikiId
        )
        val acceptedRef = ready.ref.copy(
            source = com.kitsugi.animelist.data.fandom.FandomMatchSource.USER,
            confidence = com.kitsugi.animelist.data.fandom.FandomConfidence.CERTAIN
        )
        _state.value = ready.copy(ref = acceptedRef)
        val page = (_article.value as? ArticleState.Ready)?.page
        if (page != null) {
            _article.value = ArticleState.Ready(
                page.copy(
                    matchSource = com.kitsugi.animelist.data.fandom.FandomMatchSource.USER,
                    confidence = com.kitsugi.animelist.data.fandom.FandomConfidence.CERTAIN,
                    wikiSource = com.kitsugi.animelist.data.fandom.FandomMatchSource.USER,
                    wikiConfidence = com.kitsugi.animelist.data.fandom.FandomConfidence.CERTAIN
                )
            )
        } else if (_article.value is ArticleState.Loading) {
            articleJob?.cancel()
            _article.value = ArticleState.Idle
            ensureContentLoaded()
        }
    }

    fun retryGuide() {
        _guide.value = GuideState.Idle
        _guideMore.value = GuideMore.Idle
        ensureContentLoaded()
    }

    /**
     * "Devamını göster": ilgili grubun kategorisinde sonraki sayfayı çeker ve çiplere ekler.
     *
     * Jeton `null` ise (kategori tükendi) hiç istek atılmaz. Aynı anda tek grup yüklenir;
     * yükleme sürerken gelen dokunuşlar yok sayılır. Hata olursa mevcut çipler korunur,
     * düğme "tekrar dene" durumuna geçer.
     */
    fun loadMoreGuide(group: FandomGuideGroup) {
        val ready = _state.value as? UiState.Ready ?: return
        val content = (_guide.value as? GuideState.Ready)
            ?.guide?.groups?.firstOrNull { it.group == group } ?: return
        val token = content.nextToken ?: return
        if (_guideMore.value is GuideMore.Loading) return
        _guideMore.value = GuideMore.Loading(group)
        guideMoreJob = viewModelScope.launch {
            val page = try {
                FandomRepository.loadMoreGuideItems(
                    context = appContext,
                    wikiId = ready.ref.wikiId,
                    category = content.category,
                    continueToken = token
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Rehber devamı yüklenemedi (${content.category}): ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            val current = (_guide.value as? GuideState.Ready)?.guide
            if (page == null || current == null) {
                _guideMore.value = GuideMore.Failed(group)
                return@launch
            }
            val merged = current.groups.map { item ->
                if (item.group != group) {
                    item
                } else {
                    item.copy(
                        items = (item.items + page.items.map(::FandomGuideItem))
                            .distinctBy { it.title },
                        nextToken = page.nextToken
                    )
                }
            }
            _guide.value = GuideState.Ready(current.copy(groups = merged))
            _guideMore.value = GuideMore.Idle
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _searchState.value = SearchState.Idle
            return
        }
        val ready = _state.value as? UiState.Ready ?: return
        _searchState.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val hits = try {
                FandomRepository.searchWiki(appContext, ready.ref.wikiId, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Arama başarısız: ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            _searchState.value = if (hits != null) SearchState.Ready(hits) else SearchState.Failed
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchState.value = SearchState.Idle
    }

    fun openItem(title: String) {
        val ready = _state.value as? UiState.Ready ?: return
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelArticleItemTranslations()
        _selectedSections.value = emptyMap()
        _selected.value = SelectedState.Loading
        selectedJob = viewModelScope.launch {
            val article = try {
                FandomRepository.loadArticle(appContext, ready.ref.wikiId, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki makalesi yüklenemedi ($title): ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            _selected.value = if (article != null) {
                SelectedState.Ready(title, article)
            } else {
                SelectedState.Failed(title)
            }
            val key = itemKey(title)
            if (!article?.description.isNullOrBlank() && _translations.value[key] == null) {
                maybeAutoTranslate(key)
            }
            if (!article?.infobox?.allFields().isNullOrEmpty() && isAutoTranslateEnabled()) {
                translateArticleInfobox(title)
            }
        }
    }

    fun loadSelectedSection(index: Int) {
        val selected = _selected.value as? SelectedState.Ready ?: return
        when (_selectedSections.value[index]) {
            is ArticleSectionState.Loading, is ArticleSectionState.Loaded -> return
            else -> Unit
        }
        _selectedSections.update { it + (index to ArticleSectionState.Loading) }
        selectedSectionJobs[index]?.cancel()
        selectedSectionJobs[index] = viewModelScope.launch {
            val content = try {
                FandomRepository.loadSection(
                    appContext,
                    selected.article.wikiId,
                    selected.article.pageTitle,
                    index
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki makale bölümü yüklenemedi (${selected.title}/$index): ${e.message}")
                null
            }
            if (!isCurrentArticleItem(selected.title, selected.article.pageTitle)) return@launch
            val next: ArticleSectionState = if (content != null) ArticleSectionState.Loaded(content) else ArticleSectionState.Failed
            _selectedSections.update { it + (index to next) }
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) {
                translate(articleSectionKey(selected.title, index))
            }
        }
    }

    fun closeItem() {
        selectedJob?.cancel()
        cancelSelectedSectionJobs()
        cancelArticleItemTranslations()
        _selectedSections.value = emptyMap()
        _selected.value = SelectedState.Closed
    }

    fun retryItem(title: String) {
        openItem(title)
    }

    /**
     * Metni uygulama içi çeviriye verir. [force] = true iken eldeki çeviri yok sayılır
     * (hedef dil değiştiğinde ya da otomatik çeviri yeni açıldığında).
     */
    fun translate(key: String, force: Boolean = false) {
        if (key in _translating.value) return
        if (!force && key in _translations.value) return
        if (!force && key in _originalKeys.value) return
        val ready = _state.value as? UiState.Ready ?: return
        val articleTarget = isArticleTranslationKey(key)
        val articlePage = (_article.value as? ArticleState.Ready)?.page
        val text = when {
            key == KEY_SUMMARY -> (_summary.value as? BlurbState.Ready)?.blurb?.description
            key == FandomCharacterWikiViewModel.KEY_DESCRIPTION -> articlePage?.description
            key.startsWith("ib:") -> {
                val index = key.removePrefix("ib:").toIntOrNull()
                index?.let { articlePage?.infobox?.allFields()?.getOrNull(it)?.value }
            }
            key.startsWith("sec:") -> {
                val index = key.removePrefix("sec:").toIntOrNull()
                (index?.let { _articleSections.value[it] } as? FandomCharacterWikiViewModel.SectionState.Loaded)
                    ?.content?.plainText()
            }
            key.startsWith("item:") -> {
                val title = key.removePrefix("item:")
                (_selected.value as? SelectedState.Ready)?.takeIf { it.title == title }?.article?.description
            }
            key.startsWith("item-sec:") -> {
                val parts = key.removePrefix("item-sec:")
                val index = parts.substringBefore(':').toIntOrNull()
                val title = parts.substringAfter(':', missingDelimiterValue = "")
                if (index == null || title.isBlank()) null
                else {
                    val current = _selected.value as? SelectedState.Ready
                    current?.takeIf { it.title == title }
                        ?.let { (_selectedSections.value[index] as? ArticleSectionState.Loaded)?.content?.plainText() }
                }
            }
            else -> null
        }
        if (text.isNullOrBlank()) return
        if (force) _translations.update { it - key }
        _translating.update { it + key }
        translationJobs[key] = viewModelScope.launch {
            try {
                // Parça parça akan çeviri: detay sayfasındaki açıklama çevirisiyle aynı davranış.
                val result = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(text) { partial ->
                        if (partial.isNotBlank() && isCurrent(ready) &&
                            (!articleTarget || articlePage?.let(::isCurrentArticle) == true)
                        ) {
                            _translations.update { it + (key to partial) }
                        }
                    }
                }
                if (!isCurrent(ready) || (articleTarget && articlePage?.let(::isCurrentArticle) != true)) return@launch
                if (result.isNotBlank() && result != text) {
                    _translations.update { it + (key to result) }
                } else {
                    _translations.update { it - key }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Çeviri başarısız ($key): ${e.message}")
                if (isCurrent(ready)) _translations.update { it - key }
            } finally {
                _translating.update { it - key }
                translationJobs.remove(key)
            }
        }
    }

    fun translateInfobox() {
        val page = (_article.value as? ArticleState.Ready)?.page ?: return
        val boxKey = FandomCharacterWikiViewModel.KEY_INFOBOX
        if (boxKey in _translating.value || boxKey in _translations.value || boxKey in _originalKeys.value) return
        val fields = page.infobox?.allFields().orEmpty()
        val targets = fields.withIndex()
            .filter { (_, field) -> field.value.isNotBlank() && FandomInfoboxLabels.valueResId(field.value) == null }
            .take(60)
        if (targets.isEmpty()) {
            _translations.update { it + (boxKey to "1") }
            return
        }
        val ready = _state.value as? UiState.Ready ?: return
        _translating.update { it + boxKey }
        translationJobs[boxKey] = viewModelScope.launch {
            try {
                val translated = withContext(Dispatchers.IO) {
                    val lines = targets.map { it.value.value.replace(Regex("\\s*\\n\\s*"), " · ").trim() }
                    val joined = runCatching {
                        translationManager.translateToTarget(lines.joinToString("\n"))
                    }.getOrDefault("")
                    val resultLines = joined.split('\n').map { it.trim() }
                    if (resultLines.size == lines.size) resultLines else lines.map { line ->
                        runCatching { translationManager.translateToTarget(line) }.getOrDefault(line)
                    }
                }
                if (!isCurrent(ready) || !isCurrentArticle(page)) return@launch
                val additions = HashMap<String, String>()
                targets.forEachIndexed { position, (fieldIndex, field) ->
                    translated.getOrNull(position)?.takeIf { it.isNotBlank() && it != field.value }?.let {
                        additions[FandomCharacterWikiViewModel.infoboxFieldKey(fieldIndex)] = it
                    }
                }
                additions[boxKey] = "1"
                _translations.update { it + additions }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bilgi kutusu çevirisi başarısız: ${e.message}")
            } finally {
                _translating.update { it - boxKey }
                translationJobs.remove(boxKey)
            }
        }
    }

    fun showOriginal(key: String) {
        if (key == FandomCharacterWikiViewModel.KEY_INFOBOX) {
            _translations.update { map -> map.filterKeys { it != key && !it.startsWith("ib:") } }
        } else {
            _translations.update { it - key }
        }
    }

    /**
     * Şu an bellekte olan tüm wiki metinlerini çevirir (seri özeti + açık kart açıklaması + madde).
     * Otomatik çeviri ayarı açıldığında ya da hedef dil değiştiğinde çağrılır.
     */
    private fun translateArticleInfobox(title: String, force: Boolean = false) {
        val infoKey = articleInfoboxKey(title)
        if (force) {
            translationJobs.remove(infoKey)?.cancel()
            _translating.update { it - infoKey }
            _translations.update { map -> map.filterKeys { it != infoKey && !it.startsWith("item-if:") } }
        }
        if (infoKey in _translating.value) return
        if (!force && infoKey in _translations.value) return
        val article = (_selected.value as? SelectedState.Ready)
            ?.takeIf { it.title == title }?.article ?: return
        val targets = article.infobox?.allFields().orEmpty().withIndex()
            .filter { (_, field) ->
                field.value.isNotBlank() && FandomInfoboxLabels.valueResId(field.value) == null
            }
            .take(40)
        if (targets.isEmpty()) {
            _translations.update { it + (infoKey to "1") }
            return
        }
        _translating.update { it + infoKey }
        translationJobs[infoKey] = viewModelScope.launch {
            try {
                val translatedValues = withContext(Dispatchers.IO) {
                    val sourceLines = targets.map { (_, field) -> field.value.replace(Regex("\\s*\\n\\s*"), " · ").trim() }
                    val translatedJoined = try {
                        translationManager.translateToTarget(sourceLines.joinToString("\n"))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ""
                    }
                    val lines = translatedJoined.split('\n').map { it.trim() }
                    if (lines.size == sourceLines.size && lines.all { it.isNotBlank() }) {
                        lines
                    } else {
                        sourceLines.map { value ->
                            try {
                                translationManager.translateToTarget(value)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                value
                            }
                        }
                    }
                }
                if (!isCurrentArticleItem(title, article.pageTitle)) return@launch
                val additions = HashMap<String, String>()
                targets.forEachIndexed { position, (fieldIndex, field) ->
                    val translated = translatedValues.getOrNull(position).orEmpty()
                    if (translated.isNotBlank() && translated != field.value) {
                        additions[articleInfoboxFieldKey(title, fieldIndex)] = translated
                    }
                }
                additions[infoKey] = "1"
                _translations.update { it + additions }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki bilgi kutusu çevirisi başarısız ($title): ${e.message}")
            } finally {
                _translating.update { it - infoKey }
                translationJobs.remove(infoKey)
            }
        }
    }

    private fun cancelSelectedSectionJobs() {
        selectedSectionJobs.values.forEach { it.cancel() }
        selectedSectionJobs.clear()
    }

    private fun cancelArticleItemTranslations() {
        val keys = translationJobs.keys.filter { it.startsWith("item") }
        keys.forEach { key ->
            translationJobs.remove(key)?.cancel()
            _translating.update { it - key }
        }
        _translations.update { map -> map.filterKeys { !it.startsWith("item") } }
    }

    private fun isCurrentArticleItem(title: String, pageTitle: String): Boolean {
        val current = _selected.value as? SelectedState.Ready ?: return false
        return current.title == title && current.article.pageTitle == pageTitle
    }

    private fun retranslateLoadedTexts(force: Boolean) {
        if (force) {
            _translations.value = emptyMap()
            _originalKeys.value = emptySet()
        }
        if (!(_summary.value as? BlurbState.Ready)?.blurb?.description.isNullOrBlank()) {
            translate(KEY_SUMMARY, force = force)
        }
        val selected = _selected.value as? SelectedState.Ready
        if (selected != null) {
            if (!selected.article.description.isNullOrBlank()) {
                translate(itemKey(selected.title), force = force)
            }
            if (!selected.article.infobox?.allFields().isNullOrEmpty()) {
                translateArticleInfobox(selected.title, force = force)
            }
            _selectedSections.value.forEach { (index, state) ->
                if (state is ArticleSectionState.Loaded) {
                    translate(articleSectionKey(selected.title, index), force = force)
                }
            }
        }
        val articlePage = (_article.value as? ArticleState.Ready)?.page
        if (articlePage != null) {
            if (!articlePage.description.isNullOrBlank()) {
                translate(FandomCharacterWikiViewModel.KEY_DESCRIPTION, force = force)
            }
            if (!articlePage.infobox?.allFields().isNullOrEmpty()) {
                translateInfobox()
            }
            _articleSections.value.keys.sorted().forEach { index ->
                translate(FandomCharacterWikiViewModel.sectionKey(index), force = force)
            }
        }
    }

    fun reportWrongMatch() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.rejectMediaWiki(appContext, query, ready.ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki engellenemedi: ${e.message}")
            }
            rebind()
        }
    }

    private fun isCurrent(ready: UiState.Ready): Boolean {
        val current = _state.value as? UiState.Ready ?: return false
        return current.generation == ready.generation && current.mediaKey == ready.mediaKey &&
            current.resolutionKey == ready.resolutionKey && current.ref.wikiId == ready.ref.wikiId &&
            current.ref.source == ready.ref.source && current.ref.confidence == ready.ref.confidence
    }

    private fun isCurrentArticle(page: FandomCharacterPage): Boolean {
        val current = (_article.value as? ArticleState.Ready)?.page ?: return false
        return current.wikiId == page.wikiId && current.pageTitle == page.pageTitle
    }

    private fun isArticleTranslationKey(key: String): Boolean =
        key == FandomCharacterWikiViewModel.KEY_DESCRIPTION ||
            key == FandomCharacterWikiViewModel.KEY_INFOBOX ||
            key.startsWith("ib:") || key.startsWith("sec:")

    private suspend fun isAutoTranslateEnabled(): Boolean = try {
        SettingsDataStore(appContext).settingsFlow.first().autoTranslateEnabled
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun maybeAutoTranslate(key: String) {
        // Otomatik çeviri yalnızca kullanıcı ayarı açıksa; karakter sekmesiyle aynı davranış.
        if (isAutoTranslateEnabled()) translate(key)
    }

    private fun cancelTranslations() {
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        _translating.value = emptySet()
        // Yeni wiki / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _originalKeys.value = emptySet()
    }

    override fun onCleared() {
        super.onCleared()
        cancelTranslations()
    }
}

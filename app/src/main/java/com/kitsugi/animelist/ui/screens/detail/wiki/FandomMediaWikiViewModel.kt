package com.kitsugi.animelist.ui.screens.detail.wiki

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
        data class Ready(val ref: FandomWikiRef, val mediaKey: String) : UiState
        data object Unavailable : UiState
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
        data class Ready(val title: String, val blurb: FandomPageBlurb?) : SelectedState
        data class Failed(val title: String) : SelectedState
    }

    companion object {
        private const val TAG = "FandomMediaWikiVM"
        const val KEY_SUMMARY = "media:desc"
        fun itemKey(title: String): String = "item:$title"
        private const val SEARCH_DEBOUNCE_MS = 350L
    }

    private val appContext = application.applicationContext
    private val translationManager by lazy { TranslationManager(appContext) }
    private val settingsDataStore by lazy { SettingsDataStore(appContext) }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

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

    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    /**
     * Ayarlardaki çeviri uygulaması tercihi ("GOOGLE", "DEEPL", "TRANSLATE_YOU", "DEFAULT").
     * Wiki çeviri düğmeleri detay sayfasındaki açıklama düğmesiyle aynı davrandığı için
     * (dokununca harici çeviri uygulaması açılır) bu tercih UI'ya akıtılır.
     */
    private val _preferredTranslator = MutableStateFlow("DEFAULT")
    val preferredTranslator: StateFlow<String> = _preferredTranslator.asStateFlow()

    val prefs: StateFlow<FandomWikiPrefs.State> = FandomWikiPrefs.stateFlow

    private var boundKey: String? = null
    private var lastQuery: FandomMediaQuery? = null
    private var resolveJob: Job? = null
    private var guideJob: Job? = null
    private var guideMoreJob: Job? = null
    private var summaryJob: Job? = null
    private var searchJob: Job? = null
    private var selectedJob: Job? = null
    private val translationJobs = HashMap<String, Job>()

    init {
        FandomWikiPrefs.ensureLoaded(appContext)
        viewModelScope.launch {
            FandomWikiPrefs.stateFlow
                .map { Pair(it.enabled, it.nameFallback) }
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
            "${query.cacheKey()}|${prefsState.enabled}|${prefsState.nameFallback}"
        }
        if (key == boundKey) return
        boundKey = key
        resolveJob?.cancel()
        guideJob?.cancel()
        guideMoreJob?.cancel()
        _guideMore.value = GuideMore.Idle
        summaryJob?.cancel()
        searchJob?.cancel()
        selectedJob?.cancel()
        cancelTranslations()
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
            val mediaKey = query.cacheKey()
            _state.value = if (ref != null && mediaKey != null) {
                UiState.Ready(ref, mediaKey)
            } else {
                UiState.Unavailable
            }
        }
    }

    private fun rebind() {
        boundKey = null
        bind(lastQuery)
    }

    /** "Tekrar dene": eski (olumsuz dahil) wiki önbelleği silinir, çözümleme baştan yapılır. */
    fun retry() {
        val query = lastQuery
        viewModelScope.launch {
            if (query != null) {
                try {
                    FandomRepository.forgetMediaWiki(appContext, query)
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

    /** Arama kutusu: kısa bir duraklamayla (debounce) wiki içi arama. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchState.value = SearchState.Idle
            return
        }
        val ready = _state.value as? UiState.Ready ?: return
        _searchState.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val hits = try {
                FandomRepository.searchWiki(appContext, ready.ref.wikiId, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki araması başarısız: ${e.message}")
                null
            }
            if (!isCurrent(ready) || _searchQuery.value != query) return@launch
            _searchState.value = if (hits != null) SearchState.Ready(hits) else SearchState.Failed
        }
    }

    fun clearSearch() = onSearchQueryChange("")

    /** Çipe / arama sonucuna dokunma: alttan kartda kısa açıklama. */
    fun openItem(title: String) {
        val ready = _state.value as? UiState.Ready ?: return
        selectedJob?.cancel()
        _selected.value = SelectedState.Loading
        selectedJob = viewModelScope.launch {
            val blurb = try {
                FandomRepository.loadPageBlurb(appContext, ready.ref.wikiId, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sayfa açıklaması yüklenemedi ($title): ${e.message}")
                null
            }
            if (!isCurrent(ready)) return@launch
            _selected.value = if (blurb != null) {
                SelectedState.Ready(title, blurb)
            } else {
                SelectedState.Failed(title)
            }
            val key = itemKey(title)
            if (!blurb?.description.isNullOrBlank() && _translations.value[key] == null) {
                maybeAutoTranslate(key)
            }
        }
    }

    fun closeItem() {
        selectedJob?.cancel()
        _selected.value = SelectedState.Closed
    }

    fun retryItem(title: String) = openItem(title)

    /**
     * Metni uygulama içi çeviriye verir. [force] = true iken eldeki çeviri yok sayılır
     * (hedef dil değiştiğinde ya da otomatik çeviri yeni açıldığında).
     */
    fun translate(key: String, force: Boolean = false) {
        if (key in _translating.value) return
        if (!force && key in _translations.value) return
        val ready = _state.value as? UiState.Ready ?: return
        val text = when {
            key == KEY_SUMMARY -> (_summary.value as? BlurbState.Ready)?.blurb?.description
            key.startsWith("item:") -> {
                val title = key.removePrefix("item:")
                (_selected.value as? SelectedState.Ready)?.takeIf { it.title == title }?.blurb?.description
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
                        if (partial.isNotBlank() && isCurrent(ready)) {
                            _translations.update { it + (key to partial) }
                        }
                    }
                }
                if (!isCurrent(ready)) return@launch
                if (result.isNotBlank() && result != text) {
                    _translations.update { it + (key to result) }
                } else {
                    // Metin zaten hedef dilde (ya da çeviri boş döndü): akan taslağı geri al.
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

    /**
     * Şu an bellekte olan tüm wiki metinlerini çevirir (seri özeti + açık kart açıklaması).
     * Otomatik çeviri ayarı açıldığında ya da hedef dil değiştiğinde çağrılır.
     */
    private fun retranslateLoadedTexts(force: Boolean) {
        if (force) _translations.value = emptyMap()
        if (!(_summary.value as? BlurbState.Ready)?.blurb?.description.isNullOrBlank()) {
            translate(KEY_SUMMARY, force = force)
        }
        val selected = _selected.value as? SelectedState.Ready
        if (selected != null && !selected.blurb?.description.isNullOrBlank()) {
            translate(itemKey(selected.title), force = force)
        }
    }

    /** "Yanlışsa bildir": wiki bu yapım için reddedilir ve yeniden çözülür. */
    fun reportWrongMatch() {
        val ready = _state.value as? UiState.Ready ?: return
        val query = lastQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.rejectMediaWiki(appContext, query, ready.ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bildirim kaydedilemedi: ${e.message}")
            }
            rebind()
        }
    }

    private fun isCurrent(ready: UiState.Ready): Boolean {
        val current = _state.value as? UiState.Ready ?: return false
        return current.mediaKey == ready.mediaKey && current.ref.wikiId == ready.ref.wikiId
    }

    private suspend fun maybeAutoTranslate(key: String) {
        // Otomatik çeviri yalnızca kullanıcı ayarı açıksa; karakter sekmesiyle aynı davranış.
        val auto = try {
            settingsDataStore.settingsFlow.first().autoTranslateEnabled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (auto) translate(key)
    }

    private fun cancelTranslations() {
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        _translating.value = emptySet()
    }
}

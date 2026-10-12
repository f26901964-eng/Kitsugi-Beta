package com.kitsugi.animelist.ui.screens.detail.wiki

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomCharacterQueries
import com.kitsugi.animelist.data.fandom.FandomCharacterQuery
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomWikiPrefs
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Karakter detayındaki "Wiki" sekmesinin durumu.
 *
 * Sayfa açılınca yalnızca "bu karakterin wiki sayfası var mı?" çözülür (kimlik önce,
 * isim son çare). Sekme ancak [UiState.Ready] olduğunda görünür; bölüm metinleri
 * kullanıcı açtıkça yüklenir.
 */
class FandomCharacterWikiViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Ready(val page: FandomCharacterPage) : UiState
        data object Unavailable : UiState
    }

    sealed interface SectionState {
        data object Loading : SectionState
        data class Loaded(val content: FandomSectionContent) : SectionState
        data object Failed : SectionState
    }

    companion object {
        private const val TAG = "FandomWikiVM"
        const val KEY_DESCRIPTION = "desc"
        fun sectionKey(index: Int): String = "sec:$index"
    }

    private val appContext = application.applicationContext
    private val translationManager by lazy { TranslationManager(appContext) }
    private val settingsDataStore by lazy { SettingsDataStore(appContext) }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _sections = MutableStateFlow<Map<Int, SectionState>>(emptyMap())
    val sections: StateFlow<Map<Int, SectionState>> = _sections.asStateFlow()

    /** Çevrilmiş metinler: [KEY_DESCRIPTION] veya [sectionKey]. */
    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    val prefs: StateFlow<FandomWikiPrefs.State> = FandomWikiPrefs.stateFlow

    private var boundKey: String? = null
    private var lastDetail: KitsugiCharacterDetail? = null
    private var lastSource: String = ""
    private var lastCharacterId: Int = 0
    private var currentQuery: FandomCharacterQuery? = null
    private var resolveJob: Job? = null
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
    }

    /** Karakter detayı yüklendiğinde (ve yenilendiğinde) çağrılır; aynı karakter için tekrar çözmez. */
    fun bind(detail: KitsugiCharacterDetail, source: String, characterId: Int) {
        lastDetail = detail
        lastSource = source
        lastCharacterId = characterId
        val prefsState = FandomWikiPrefs.state(appContext)
        val query = FandomCharacterQueries.from(detail, source, characterId)
        val key = if (query == null) {
            "none:${source.lowercase()}:$characterId"
        } else {
            "${query.cacheKey()}|${prefsState.enabled}|${prefsState.nameFallback}|${prefsState.preferTurkish}"
        }
        if (key == boundKey) return
        boundKey = key
        currentQuery = query
        resolveJob?.cancel()
        cancelTranslations()
        _sections.value = emptyMap()
        _translations.value = emptyMap()

        if (query == null || !prefsState.enabled) {
            _state.value = UiState.Unavailable
            return
        }
        _state.value = UiState.Loading
        resolveJob = viewModelScope.launch {
            val page = try {
                FandomRepository.resolveCharacterPage(appContext, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki sayfası çözümlenemedi: ${e.message}")
                null
            }
            _state.value = if (page != null) UiState.Ready(page) else UiState.Unavailable
            if (page?.description != null && isAutoTranslateEnabled()) translate(KEY_DESCRIPTION)
        }
    }

    private fun rebind() {
        val detail = lastDetail ?: return
        boundKey = null
        bind(detail, lastSource, lastCharacterId)
    }

    fun loadSection(index: Int) {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        when (_sections.value[index]) {
            is SectionState.Loading, is SectionState.Loaded -> return
            else -> Unit
        }
        _sections.update { it + (index to SectionState.Loading) }
        viewModelScope.launch {
            val content = try {
                FandomRepository.loadSection(appContext, page.wikiId, page.pageTitle, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bölüm yüklenemedi ($index): ${e.message}")
                null
            }
            if (!isCurrent(page)) return@launch
            val newState: SectionState = if (content != null) SectionState.Loaded(content) else SectionState.Failed
            _sections.update { it + (index to newState) }
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) translate(sectionKey(index))
        }
    }

    fun translate(key: String) {
        if (key in _translating.value || key in _translations.value) return
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val text = when {
            key == KEY_DESCRIPTION -> page.description
            key.startsWith("sec:") -> {
                val index = key.removePrefix("sec:").toIntOrNull()
                (index?.let { _sections.value[it] } as? SectionState.Loaded)?.content?.plainText()
            }
            else -> null
        }
        if (text.isNullOrBlank()) return
        _translating.update { it + key }
        translationJobs[key] = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { translationManager.translateToTarget(text) }
                if (result.isNotBlank() && result != text && isCurrent(page)) {
                    _translations.update { it + (key to result) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Çeviri başarısız ($key): ${e.message}")
            } finally {
                _translating.update { it - key }
                translationJobs.remove(key)
            }
        }
    }

    fun showOriginal(key: String) {
        _translations.update { it - key }
    }

    /** "Bu eşleşme doğru": otomatik eşleşme kesinleşir (wiki onayı aynı yapımın diğer karakterlerine de geçer). */
    fun confirmMatch() {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val query = currentQuery ?: return
        val confirmed = try {
            FandomRepository.confirmCharacterPage(appContext, query, page)
        } catch (e: Exception) {
            Log.w(TAG, "Onay kaydedilemedi: ${e.message}")
            return
        }
        if (isCurrent(page)) {
            _state.value = UiState.Ready(confirmed)
        }
    }

    /** "Bu eşleşme yanlış": sayfa bu karakter için bir daha önerilmez, yeniden çözülür. */
    fun reportWrongMatch() {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val query = currentQuery ?: return
        viewModelScope.launch {
            try {
                FandomRepository.rejectCharacterPage(appContext, query, page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bildirim kaydedilemedi: ${e.message}")
            }
            rebind()
        }
    }

    /** Aynı wiki sayfası hâlâ gösteriliyor mu? (Onay sonrası sayfa nesnesi değişir, kimliği değişmez.) */
    private fun isCurrent(page: FandomCharacterPage): Boolean {
        val current = (_state.value as? UiState.Ready)?.page ?: return false
        return current.wikiId == page.wikiId && current.pageTitle == page.pageTitle
    }

    private fun cancelTranslations() {
        translationJobs.values.forEach { it.cancel() }
        translationJobs.clear()
        _translating.value = emptySet()
    }

    private suspend fun isAutoTranslateEnabled(): Boolean = try {
        settingsDataStore.settingsFlow.first().autoTranslateEnabled
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}

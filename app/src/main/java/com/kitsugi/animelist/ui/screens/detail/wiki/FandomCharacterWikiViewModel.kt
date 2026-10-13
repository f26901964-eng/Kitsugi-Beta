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
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail
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
        /** Bilgi kutusu toplu çeviri durumu; alan bazlı sonuçlar [infoboxFieldKey] ile saklanır. */
        const val KEY_INFOBOX = "infobox"
        private const val INFOBOX_FIELD_PREFIX = "ib:"
        private const val MAX_INFOBOX_FIELDS = 60
        fun sectionKey(index: Int): String = "sec:$index"
        /** Düzleştirilmiş alan sırasına (grup → alan) göre anahtar. */
        fun infoboxFieldKey(index: Int): String = "$INFOBOX_FIELD_PREFIX$index"
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
    /** Son bağlanan sorgu üreticisi; tercih değişince aynı girdiyle yeniden çözmek için. */
    private var lastBinding: (() -> Pair<FandomCharacterQuery?, String>)? = null
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
        bindQuery(
            fallbackKey = "none:${source.lowercase()}:$characterId",
            queryProvider = { FandomCharacterQueries.from(detail, source, characterId) }
        )
    }

    /** Ekip/seslendirme sanatçısı detayı: kişi, çalıştığı yapımların wiki'sinde adıyla aranır. */
    fun bindStaff(detail: KitsugiStaffDetail, source: String, staffId: Int) {
        bindQuery(
            fallbackKey = "none:staff:${source.lowercase()}:$staffId",
            queryProvider = { FandomCharacterQueries.fromStaff(detail, source, staffId) }
        )
    }

    /**
     * Genel bağlama: [queryProvider] null dönerse sekme "bulunamadı" durumuna düşer.
     * Aynı sorgu + aynı tercihler için yeniden çözümleme yapılmaz.
     */
    fun bindQuery(fallbackKey: String, queryProvider: () -> FandomCharacterQuery?) {
        lastBinding = { queryProvider() to fallbackKey }
        val prefsState = FandomWikiPrefs.state(appContext)
        val query = queryProvider()
        val key = if (query == null) {
            fallbackKey
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
        val binding = lastBinding ?: return
        boundKey = null
        bindQuery(fallbackKey = binding().second, queryProvider = { binding().first })
    }

    /** "Tekrar dene": son karakter için çözümlemeyi sıfırdan başlatır. */
    fun retry() = rebind()

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
        if (key == KEY_INFOBOX) {
            _translations.update { map -> map.filterKeys { it != KEY_INFOBOX && !it.startsWith(INFOBOX_FIELD_PREFIX) } }
            return
        }
        _translations.update { it - key }
    }

    /**
     * Bilgi kutusundaki serbest metin değerleri topluca çevirir. Arayüz dilinde zaten karşılığı
     * olan kısa değerler ("Male", "Deceased"…) atlanır; sonuçlar alan sırasına göre saklanır.
     * Satır sayısı korunursa tek istekle, bozulursa alan alan çevrilir.
     */
    fun translateInfobox() {
        if (KEY_INFOBOX in _translating.value || KEY_INFOBOX in _translations.value) return
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val fields = page.infobox?.allFields().orEmpty()
        val targets = fields.withIndex()
            .filter { (_, field) ->
                field.value.isNotBlank() && FandomInfoboxLabels.valueResId(field.value) == null
            }
            .take(MAX_INFOBOX_FIELDS)
        if (targets.isEmpty()) {
            _translations.update { it + (KEY_INFOBOX to "1") }
            return
        }
        _translating.update { it + KEY_INFOBOX }
        translationJobs[KEY_INFOBOX] = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val singleLine = targets.map { it.value.value.replace(Regex("\\s*\\n\\s*"), " · ").trim() }
                    val joined = singleLine.joinToString("\n")
                    val translatedJoined = runCatching { translationManager.translateToTarget(joined) }.getOrDefault("")
                    val lines = translatedJoined.split('\n').map { it.trim() }
                    if (lines.size == singleLine.size) {
                        lines
                    } else {
                        singleLine.map { value ->
                            runCatching { translationManager.translateToTarget(value) }.getOrDefault(value)
                        }
                    }
                }
                if (!isCurrent(page)) return@launch
                val additions = HashMap<String, String>()
                targets.forEachIndexed { position, (fieldIndex, field) ->
                    val translated = results.getOrNull(position).orEmpty()
                    if (translated.isNotBlank() && translated != field.value) {
                        additions[infoboxFieldKey(fieldIndex)] = translated
                    }
                }
                additions[KEY_INFOBOX] = "1"
                _translations.update { it + additions }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bilgi kutusu çevirisi başarısız: ${e.message}")
            } finally {
                _translating.update { it - KEY_INFOBOX }
                translationJobs.remove(KEY_INFOBOX)
            }
        }
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

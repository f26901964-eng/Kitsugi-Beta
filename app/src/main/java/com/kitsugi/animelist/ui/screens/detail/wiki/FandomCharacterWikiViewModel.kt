package com.kitsugi.animelist.ui.screens.detail.wiki

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.fandom.FandomCharacterPage
import com.kitsugi.animelist.data.fandom.FandomFullPage
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import kotlin.collections.ArrayDeque
import java.util.LinkedHashSet
import com.kitsugi.animelist.data.fandom.FandomCharacterQueries
import com.kitsugi.animelist.data.fandom.FandomCharacterQuery
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomWikiResolver
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

    /** Uygulama içi wiki okuyucu durumu (medya sekmesiyle aynı sözleşme). */
    sealed interface ReaderState {
        data object Closed : ReaderState
        data object Loading : ReaderState
        data class Ready(
            val wikiId: String,
            val page: FandomFullPage,
            val sections: Map<Int, FandomSectionContent>,
            val related: List<FandomRelatedPage>,
            val loadingSection: Int? = null,
            val failedSection: Int? = null
        ) : ReaderState
        data class Failed(val title: String) : ReaderState
    }


    companion object {
        private const val TAG = "FandomWikiVM"
        const val KEY_DESCRIPTION = "desc"
        /** Bilgi kutusu toplu çeviri durumu; alan bazlı sonuçlar [infoboxFieldKey] ile saklanır. */
        const val KEY_INFOBOX = "infobox"
        const val INFOBOX_FIELD_PREFIX = "ib:"
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

    private val _reader = MutableStateFlow<ReaderState>(ReaderState.Closed)
    val reader: StateFlow<ReaderState> = _reader.asStateFlow()

    private val _relatedPages = MutableStateFlow<List<FandomRelatedPage>>(emptyList())
    val relatedPages: StateFlow<List<FandomRelatedPage>> = _relatedPages.asStateFlow()
    private var relatedJob: Job? = null

    private var readerJob: Job? = null
    private var readerSectionJob: Job? = null
    private var readerRelatedJob: Job? = null
    private val readerBackStack = ArrayDeque<Pair<String, String>>() // wikiId to title


    /** Çevrilmiş metinler: [KEY_DESCRIPTION] veya [sectionKey]. */
    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    /**
     * Orijinal metne dönülmüş anahtarlar ([KEY_DESCRIPTION], [sectionKey], [KEY_INFOBOX]).
     * Çeviri bellekte kalır; geçiş düğmesiyle tekrar gösterilebilir. Bu anahtarlar için
     * otomatik çeviri yeniden çalışıp üstüne yazmaz.
     */
    private val _originalKeys = MutableStateFlow<Set<String>>(emptySet())
    val originalKeys: StateFlow<Set<String>> = _originalKeys.asStateFlow()

    /**
     * Ayarlardaki çeviri uygulaması tercihi ("GOOGLE", "DEEPL", "TRANSLATE_YOU", "DEFAULT").
     * Wiki çeviri düğmeleri de detay sayfasındaki açıklama düğmesiyle aynı davrandığı için
     * (dokununca harici çeviri uygulaması açılır) bu tercih UI'ya akıtılır.
     */
    private val _preferredTranslator = MutableStateFlow("DEFAULT")
    val preferredTranslator: StateFlow<String> = _preferredTranslator.asStateFlow()

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
        // Detay sayfalarındaki açıklama çevirisiyle aynı tepkisellik: sayfa açıkken
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
    fun bindQuery(fallbackKey: String, queryProvider: () -> FandomCharacterQuery?, forceRefresh: Boolean = false) {
        lastBinding = { queryProvider() to fallbackKey }
        val prefsState = FandomWikiPrefs.state(appContext)
        val query = queryProvider()
        val key = if (query == null) {
            fallbackKey
        } else {
            "${query.cacheKey()}|${prefsState.enabled}|${prefsState.nameFallback}|${prefsState.preferTurkish}"
        }
        if (key == boundKey && !forceRefresh) return
        boundKey = key
        currentQuery = query
        resolveJob?.cancel()
        cancelTranslations()
        readerJob?.cancel()
        readerSectionJob?.cancel()
        readerRelatedJob?.cancel()
        relatedJob?.cancel()
        readerBackStack.clear()
        _relatedPages.value = emptyList()
        _reader.value = ReaderState.Closed
        _sections.value = emptyMap()
        _translations.value = emptyMap()

        if (query == null || !prefsState.enabled) {
            _state.value = UiState.Unavailable
            return
        }
        _state.value = UiState.Loading
        resolveJob = viewModelScope.launch {
            val page = try {
                FandomRepository.resolveCharacterPage(appContext, query, forceRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wiki sayfası çözümlenemedi: ${e.message}")
                null
            }
            _state.value = if (page != null) UiState.Ready(page) else UiState.Unavailable
            // Otomatik çeviri açıkken tüm metinler kendiliğinden çevrilir: özet + bilgi kutusu
            // (bölümler kullanıcı açtıkça, [loadSection] içinde çevrilir).
            if (page != null) {
                if (isAutoTranslateEnabled()) {
                    if (!page.description.isNullOrBlank()) translate(KEY_DESCRIPTION)
                    if (!page.infobox?.allFields().isNullOrEmpty()) translateInfobox()
                }
                // İlgili wiki sayfalarını arka planda topla (ilk 1-2 bölümden viki içi bağlantılar).
                val wikiId = page.wikiId
                val pageTitle = page.pageTitle
                relatedJob?.cancel()
                _relatedPages.value = emptyList()
                relatedJob = viewModelScope.launch {
                    val list = try {
                        val titles = LinkedHashSet<String>()
                        for (idx in listOf(0, 1)) {
                            val sec = FandomRepository.loadSection(appContext, wikiId, pageTitle, idx)
                            if (sec != null) titles += sec.links
                        }
                        if (titles.isNotEmpty()) {
                            FandomRepository.loadRelatedPages(appContext, wikiId, titles.take(30))
                        } else emptyList()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "İlgili wiki sayfaları yüklenemedi ($pageTitle): ${e.message}")
                        emptyList()
                    }
                    val current = (_state.value as? UiState.Ready)?.page ?: return@launch
                    if (current.wikiId == wikiId && current.pageTitle == pageTitle) {
                        _relatedPages.value = list.take(20)
                    }
                }
            }
        }
    }

    private fun rebind(forceRefresh: Boolean = false) {
        val binding = lastBinding ?: return
        boundKey = null
        bindQuery(
            fallbackKey = binding().second,
            queryProvider = { binding().first },
            forceRefresh = forceRefresh
        )
    }

    /**
     * "Tekrar dene": çözümleme gerçekten baştan yapılır.
     *
     * Önceki davranış yalnızca yeniden bağlanıyordu; karakter ve yapım wiki katmanlarındaki
     * "bulunamadı" önbelleği (3 güne kadar) ve süreç içi "wiki yok" listesi aynı sonucu
     * anında geri verdiği için düğme ölü gibi görünüyordu. Artık zehir kayıtlar temizlenir
     * ve [bindQuery] çözümlemeyi `forceRefresh` ile (önbellek okumadan) çalıştırır.
     */
    fun retry() {
        viewModelScope.launch {
            try {
                FandomWikiResolver.forgetMissingWikis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Eksik wiki listesi temizlenemedi: ${e.message}")
            }
            rebind(forceRefresh = true)
        }
    }

    /** Orijinal / çeviri geçişi: bu anahtar için orijinal metnin gösterilip gösterilmeyeceğini değiştirir. */
    fun toggleOriginal(key: String) {
        _originalKeys.update { if (key in it) it - key else it + key }
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
                Log.w(TAG, "Bölüm yüklenemedi ($index): ${e.message}")
                null
            }
            if (!isCurrent(page)) return@launch
            val newState: SectionState = if (content != null) SectionState.Loaded(content) else SectionState.Failed
            _sections.update { it + (index to newState) }
            if (content != null && !content.isEmpty() && isAutoTranslateEnabled()) translate(sectionKey(index))
        }
    }

    /**
     * Metni uygulama içi çeviriye verir. [force] = true iken eldeki çeviri yok sayılır
     * (hedef dil değiştiğinde ya da otomatik çeviri yeni açıldığında).
     */
    fun translate(key: String, force: Boolean = false) {
        if (key in _translating.value) return
        if (!force && key in _translations.value) return
        if (!force && key in _originalKeys.value) return
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
        if (force) clearTranslation(key)
        _translating.update { it + key }
        translationJobs[key] = viewModelScope.launch {
            try {
                // Parça parça akan çeviri: detay sayfasındaki açıklama çevirisiyle aynı davranış.
                val result = withContext(Dispatchers.IO) {
                    translationManager.translateToTarget(text) { partial ->
                        if (partial.isNotBlank() && isCurrent(page)) {
                            _translations.update { it + (key to partial) }
                        }
                    }
                }
                if (!isCurrent(page)) return@launch
                if (result.isNotBlank() && result != text) {
                    _translations.update { it + (key to result) }
                } else {
                    // Metin zaten hedef dilde (ya da çeviri boş döndü): akan taslağı geri al.
                    clearTranslation(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Çeviri başarısız ($key): ${e.message}")
                if (isCurrent(page)) clearTranslation(key)
            } finally {
                _translating.update { it - key }
                translationJobs.remove(key)
            }
        }
    }

    /** Çeviriyi bırakıp orijinal metne döner. */
    private fun clearTranslation(key: String) {
        if (key == KEY_INFOBOX) {
            _translations.update { map -> map.filterKeys { it != KEY_INFOBOX && !it.startsWith(INFOBOX_FIELD_PREFIX) } }
        } else {
            _translations.update { it - key }
        }
    }

    /**
     * Şu an bellekte olan tüm wiki metinlerini çevirir (açıklama + yüklenmiş bölümler +
     * bilgi kutusu). Otomatik çeviri ayarı açıldığında ya da hedef dil değiştiğinde çağrılır.
     */
    private fun retranslateLoadedTexts(force: Boolean) {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        if (force) {
            _translations.value = emptyMap()
            _originalKeys.value = emptySet()
        }
        if (!page.description.isNullOrBlank()) translate(KEY_DESCRIPTION, force = force)
        if (!page.infobox?.allFields().isNullOrEmpty()) translateInfobox(force = force)
        _sections.value.keys.sorted().forEach { index -> translate(sectionKey(index), force = force) }
    }

    /**
     * Bilgi kutusundaki serbest metin değerleri topluca çevirir. Arayüz dilinde zaten karşılığı
     * olan kısa değerler ("Male", "Deceased"…) atlanır; sonuçlar alan sırasına göre saklanır.
     * Satır sayısı korunursa tek istekle, bozulursa alan alan çevrilir.
     */
    fun translateInfobox(force: Boolean = false) {
        if (KEY_INFOBOX in _translating.value) return
        if (!force && KEY_INFOBOX in _translations.value) return
        if (!force && KEY_INFOBOX in _originalKeys.value) return
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
        if (force) clearTranslation(KEY_INFOBOX)
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
        // Yeni sayfa / otomatik çeviri kapandı: eski orijinal-geçiş durumu geçerli değil.
        _originalKeys.value = emptySet()
    }

    private suspend fun isAutoTranslateEnabled(): Boolean = try {
        settingsDataStore.settingsFlow.first().autoTranslateEnabled
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
    // ── Uygulama içi wiki okuyucu ────────────────────────────────────────────

    /** Mevcut karakterin wiki sayfasını veya başka bir wiki sayfasını okuyucuda açar. */
    fun openReader(title: String? = null, wikiIdOverride: String? = null, pushToBackStack: Boolean = true) {
        val page = (_state.value as? UiState.Ready)?.page ?: return
        val targetWiki = wikiIdOverride ?: page.wikiId
        val targetTitle = (title ?: page.pageTitle).trim()
        if (targetTitle.isEmpty()) return
        val current = (_reader.value as? ReaderState.Ready)
        if (pushToBackStack && current != null
            && !(current.page.displayTitle.equals(targetTitle, ignoreCase = true) && current.wikiId == targetWiki)) {
            readerBackStack.addLast(current.wikiId to current.page.displayTitle)
        }
        readerJob?.cancel()
        readerSectionJob?.cancel()
        readerRelatedJob?.cancel()
        _reader.value = ReaderState.Loading
        readerJob = viewModelScope.launch {
            val meta = try {
                FandomRepository.loadFullPageMeta(appContext, targetWiki, targetTitle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Okuyucu sayfa yüklenemedi ($targetWiki/$targetTitle): ${e.message}")
                null
            }
            if (meta == null) {
                _reader.value = ReaderState.Failed(targetTitle)
                return@launch
            }
            _reader.value = ReaderState.Ready(
                wikiId = targetWiki,
                page = meta,
                sections = emptyMap(),
                related = emptyList()
            )
            launchRelatedLoad(targetWiki, meta)
        }
    }

    private fun launchRelatedLoad(wikiId: String, page: FandomFullPage) {
        readerRelatedJob?.cancel()
        readerRelatedJob = viewModelScope.launch {
            val candidates = LinkedHashSet<String>()
            for (idx in listOf(0, 1)) {
                val sec = FandomRepository.loadSection(appContext, wikiId, page.displayTitle, idx)
                if (sec != null) candidates += sec.links
            }
            val related = try {
                val titles = candidates.take(30)
                if (titles.isEmpty()) emptyList()
                else FandomRepository.loadRelatedPages(appContext, wikiId, titles)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Okuyucu ilgili sayfalar yüklenemedi (${page.displayTitle}): ${e.message}")
                emptyList()
            }
            val cur = _reader.value as? ReaderState.Ready ?: return@launch
            if (cur.page.displayTitle != page.displayTitle || cur.wikiId != wikiId) return@launch
            _reader.value = cur.copy(related = related.take(20))
        }
    }

    fun loadReaderSection(index: Int) {
        val cur = _reader.value as? ReaderState.Ready ?: return
        if (cur.sections.containsKey(index)) return
        readerSectionJob?.cancel()
        _reader.value = cur.copy(loadingSection = index, failedSection = null)
        readerSectionJob = viewModelScope.launch {
            val content = try {
                FandomRepository.loadSection(appContext, cur.wikiId, cur.page.displayTitle, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Okuyucu bölüm yüklenemedi (${cur.page.displayTitle}#$index): ${e.message}")
                null
            }
            val now = _reader.value as? ReaderState.Ready ?: return@launch
            if (now.page.displayTitle != cur.page.displayTitle || now.wikiId != cur.wikiId) return@launch
            if (content == null) {
                _reader.value = now.copy(loadingSection = null, failedSection = index)
                return@launch
            }
            val sections = HashMap(now.sections)
            sections[index] = content
            _reader.value = now.copy(sections = sections, loadingSection = null, failedSection = null)
        }
    }

    fun retryReaderSection(index: Int) = loadReaderSection(index)

    fun openReaderLink(title: String) = openReader(title = title, pushToBackStack = true)

    fun readerBack(): Boolean {
        val prev = readerBackStack.removeLastOrNull()
        return if (prev != null) {
            openReader(title = prev.second, wikiIdOverride = prev.first, pushToBackStack = false)
            true
        } else {
            closeReader()
            false
        }
    }

    fun closeReader() {
        readerJob?.cancel()
        readerSectionJob?.cancel()
        readerRelatedJob?.cancel()
        readerBackStack.clear()
        _reader.value = ReaderState.Closed
    }
}

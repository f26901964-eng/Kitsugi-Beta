package com.kitsugi.animelist.ui.screens.manga

import kotlinx.coroutines.ensureActive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MangaChapterProgressEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterNumberResolver
import com.kitsugi.animelist.data.manga.MangaChapterSequence
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaTapAction
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaTapActionStateStore
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kitsugi.animelist.data.manga.loader.MangaCache
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.data.manga.SourceFailureClassifier

import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.DualPageMode
import com.kitsugi.animelist.data.manga.MangaFitMode

/**
 * MangaReaderScreen için ViewModel.
 *
 * Sorumluluklar:
 * - Aktif bölümü ve sayfa listesini yönetmek
 * - Okuma modunu (LTR/RTL/Webtoon) kalıcı yapmak
 * - Her sayfa değişiminde Room'a ilerlemeyi kaydetmek
 * - Bir sonraki/önceki bölüme geçişi yönetmek
 */
class MangaReaderViewModel(
    private val context: Context,
    val source: MangaSource,
    val mangaDetails: MangaDetails,
    initialChapter: MangaChapter,
    /**
     * Seri türüne göre ÖNERİLEN okuma modu (manhwa → webtoon gibi).
     * Yalnızca kullanıcı bu seri için mod seçmediyse uygulanır.
     */
    private val suggestedMode: ReadingMode? = null
) : ViewModel() {

    /**
     * Seriye özel okuma modu deposu. Kotatsu/Mihon'daki "bu seri için görüntüleyici"
     * davranışının karşılığı: kullanıcı mod değiştirdiğinde yalnızca bu seri etkilenir.
     */
    private val perSeriesModeStore = context.getSharedPreferences("manga_reader_modes", Context.MODE_PRIVATE)
    private val perSeriesKey = "mode_${source.name}_${mangaDetails.url}".hashCode().toString()
    /** Çift sayfa tercihi de seri bazında hatırlanır. */
    private val perSeriesDualKey = "dual_${source.name}_${mangaDetails.url}".hashCode().toString()

    /** Seriye özel kayıtlı mod (yoksa null). */
    fun perSeriesMode(): ReadingMode? =
        perSeriesModeStore.getString(perSeriesKey, null)?.let { id ->
            ReadingMode.entries.firstOrNull { it.name == id }
        }

    /** Seriye özel kayıtlı çift sayfa tercihi (yoksa null). */
    fun perSeriesDualMode(): DualPageMode? =
        perSeriesModeStore.getString(perSeriesDualKey, null)?.let { id ->
            DualPageMode.entries.firstOrNull { it.name == id }
        }

    private val db  = KitsugiDatabase.getDatabase(context)
    private val dao = db.mangaChapterProgressDao()
    private val cache = MangaCache(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val sourceStateStore = MangaSourceStateStore(context)
    val loader = MangaPageLoaderV2(context, source, cache)
    val pages: StateFlow<List<MangaPage>> = loader.pages

    /** Sayfa en-boy oranları — geniş sayfaları çiftlemeden muaf tutmak için. */
    val pageAspects: StateFlow<Map<Int, Float>> = loader.pageAspectsFlow

    // F5: Debounce için Job referansı — hızlı swipe'ta Room IO flood önler
    private var progressSaveJob: kotlinx.coroutines.Job? = null

    // ─── UI Durumu ────────────────────────────────────────────────────────────

    data class UiState(
        val currentChapter: MangaChapter,
        val chapterList: List<MangaChapter> = emptyList(),
        val currentPageIndex: Int = 0,
        val readingMode: ReadingMode = ReadingMode.RightToLeft,
        val colorFilterType: ColorFilterType = ColorFilterType.Normal,
        val fitMode: MangaFitMode = MangaFitMode.FitScreen,
        val customBrightness: Float = 1.0f,
        // ─── Jest / dokunma alanı / ekran ayarları (Mihon paritesi) ──────────
        val tapLeftAction: MangaTapAction = MangaTapAction.Previous,
        val tapCenterAction: MangaTapAction = MangaTapAction.Menu,
        val tapRightAction: MangaTapAction = MangaTapAction.Next,
        val brightnessGestureEnabled: Boolean = true,
        val webtoonGapDp: Int = 0,
        val volumeKeysEnabled: Boolean = false,
        val volumeKeysInvert: Boolean = false,
        val keepScreenOn: Boolean = true,
        val fullscreenEnabled: Boolean = false,
        val autoNextChapter: Boolean = true,
        // ─── Görüntü işleme / okuma deneyimi (Mihon "Reading" paritesi) ──────
        val cropBorders: Boolean = false,
        val splitWidePages: Boolean = false,
        val rotateWidePages: Boolean = false,
        val sidePaddingDp: Int = 0,
        val animatePageTransition: Boolean = true,
        val einkFlash: Boolean = false,
        val showPageNumber: Boolean = true,
        // ─── Çift sayfa (2-up / spread) — TachiyomiSY "dual page" paritesi ────
        /** Kapalı / her zaman / yalnızca geniş ekran. */
        val dualPageMode: DualPageMode = DualPageMode.Off,
        val skipReadChapters: Boolean = false,
        val isLoadingChapters: Boolean = false,
        val error: String? = null,
        val isLoadingPages: Boolean = false,
        val pagesError: String? = null,
        /** Sayfa yükleme hatasının manga kaynak kodu (KTS-SRC-...). */
        val pagesErrorCode: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(currentChapter = initialChapter))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadChapterList()
        restoreProgress(initialChapter)
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                val mode = when (settings.mangaReadingMode) {
                    "LeftToRight" -> ReadingMode.LeftToRight
                    "RightToLeft" -> ReadingMode.RightToLeft
                    "Vertical" -> ReadingMode.Vertical
                    "Webtoon" -> ReadingMode.Webtoon
                    else -> ReadingMode.RightToLeft
                }
                val filter = when (settings.mangaColorFilter) {
                    "Normal" -> ColorFilterType.Normal
                    "Grayscale" -> ColorFilterType.Grayscale
                    "Sepia" -> ColorFilterType.Sepia
                    "Invert" -> ColorFilterType.Invert
                    else -> ColorFilterType.Normal
                }
                val fit = when (settings.mangaFitMode) {
                    "FitScreen" -> MangaFitMode.FitScreen
                    "FitWidth" -> MangaFitMode.FitWidth
                    "FitHeight" -> MangaFitMode.FitHeight
                    else -> MangaFitMode.FitScreen
                }
                // Seriye özel mod varsa global varsayılanı ezer; o da yoksa tür
                // önerisi (manhwa → webtoon) devreye girer.
                val effectiveMode = perSeriesMode() ?: suggestedMode ?: mode
                loader.preloadAhead = if (effectiveMode == ReadingMode.Webtoon) 3 else 2
                loader.keepBehind   = 1
                _uiState.update {
                    it.copy(
                        readingMode = effectiveMode,
                        colorFilterType = filter,
                        fitMode = fit,
                        customBrightness = settings.mangaBrightness,
                        tapLeftAction = MangaTapAction.fromId(settings.mangaTapLeftAction),
                        tapCenterAction = normalizeTapAction(settings.mangaTapCenterAction, MangaTapAction.Menu),
                        tapRightAction = normalizeTapAction(settings.mangaTapRightAction, MangaTapAction.Next),
                        brightnessGestureEnabled = settings.mangaBrightnessGesture,
                        webtoonGapDp = settings.mangaWebtoonGap,
                        volumeKeysEnabled = settings.mangaVolumeKeys,
                        volumeKeysInvert = settings.mangaVolumeKeysInvert,
                        keepScreenOn = settings.mangaKeepScreenOn,
                        fullscreenEnabled = settings.mangaFullscreen,
                        autoNextChapter = settings.mangaAutoNextChapter,
                        cropBorders = settings.mangaCropBorders,
                        splitWidePages = settings.mangaSplitWidePages,
                        rotateWidePages = settings.mangaRotateWidePages,
                        sidePaddingDp = settings.mangaSidePadding,
                        animatePageTransition = settings.mangaAnimatePageTransition,
                        einkFlash = settings.mangaEinkFlash,
                        showPageNumber = settings.mangaShowPageNumber,
                        skipReadChapters = settings.mangaSkipReadChapters,
                        dualPageMode = (perSeriesDualMode() ?: DualPageMode.fromId(settings.mangaDualPageMode))
                            .let { if (effectiveMode == ReadingMode.Webtoon) DualPageMode.Off else it }
                    )
                }
            }
        }
    }

    // ─── Bölüm listesini yükle ────────────────────────────────────────────────

    private fun loadChapterList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true, error = null) }
            val startedAt = System.currentTimeMillis()
            try {
                val chapters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    source.fetchChapterList(mangaDetails.url)
                }
                sourceStateStore.recordOperationSuccess(
                    source = source,
                    operation = "chapters",
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
                _uiState.update { it.copy(chapterList = chapters, isLoadingChapters = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                sourceStateStore.recordOperationFailure(
                    source = source,
                    operation = "chapters",
                    reason = e.message,
                    statusOverride = classifySourceStatus(e),
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
                _uiState.update { it.copy(isLoadingChapters = false, error = e.message) }
            }
        }
    }

    // ─── İlerleme kaydetme ───────────────────────────────────────────────────

    /**
     * Sayfa değiştikçe Room'a anlık olarak kaydeder.
     * Bölüm tamamlandıysa isCompleted = true olarak işaretler.
     */
    // F5: Progress debounce — 500ms bekle, hızlı swipe'ta tekrar tetiklenirse iptal et
    fun onPageChanged(chapterUrl: String, pageIndex: Int, totalPages: Int) {
        _uiState.update { it.copy(currentPageIndex = pageIndex) }
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500L)
            val isCompleted = totalPages > 0 && pageIndex >= totalPages - 1
            dao.upsert(
                MangaChapterProgressEntity(
                    chapterUrl    = chapterUrl,
                    mangaUrl      = mangaDetails.url,
                    chapterName   = _uiState.value.currentChapter.name,
                    lastPageIndex = pageIndex,
                    totalPages    = totalPages,
                    isCompleted   = isCompleted,
                    updatedAt     = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── Okuma modu değiştirme ────────────────────────────────────────────────

    fun setReadingMode(mode: ReadingMode) {
        // Bu seri için kalıcı: okuyucuyu kapatıp açınca aynı mod korunur.
        perSeriesModeStore.edit().putString(perSeriesKey, mode.name).apply()
        syncDualPageWithMode(mode)
        _uiState.update { it.copy(readingMode = mode) }
        viewModelScope.launch {
            settingsDataStore.setMangaReadingMode(mode.name)
        }
    }

    /**
     * Çift sayfa modunu değiştirir.
     * Seriye özel tercih SharedPreferences'te, genel varsayılan DataStore'da tutulur
     * (okuma moduyla aynı desen).
     */
    fun setDualPageMode(mode: DualPageMode) {
        perSeriesModeStore.edit().putString(perSeriesDualKey, mode.name).apply()
        _uiState.update { it.copy(dualPageMode = mode) }
        viewModelScope.launch {
            settingsDataStore.setMangaDualPageMode(DualPageMode.idOf(mode))
        }
    }

    /** Tek dokunuşla çift sayfa modunu aç/kapat (geniş ekran moduna geri döner). */
    fun toggleDualPageMode() {
        val next = when (_uiState.value.dualPageMode) {
            DualPageMode.Off -> DualPageMode.Always
            DualPageMode.Always -> DualPageMode.Off
            DualPageMode.WideOnly -> DualPageMode.Off
        }
        setDualPageMode(next)
    }

    /**
     * Çift sayfa modu webtoon akışında anlamsızdır; mod değişince otomatik kapanır.
     * (Kotatsu/Mihon da sürekli dikey akışta spread göstermez.)
     */
    private fun syncDualPageWithMode(mode: ReadingMode) {
        if (mode == ReadingMode.Webtoon && _uiState.value.dualPageMode != DualPageMode.Off) {
            _uiState.update { it.copy(dualPageMode = DualPageMode.Off) }
        }
    }

    fun setColorFilter(filter: ColorFilterType) {
        viewModelScope.launch {
            settingsDataStore.setMangaColorFilter(filter.name)
        }
    }

    fun setFitMode(fitMode: MangaFitMode) {
        viewModelScope.launch {
            settingsDataStore.setMangaFitMode(fitMode.name)
        }
    }

    fun setCustomBrightness(brightness: Float) {
        viewModelScope.launch {
            settingsDataStore.setMangaBrightness(brightness)
        }
    }

    // ─── Kenar dokunma alanları ──────────────────────────────────────────────

    /**
     * Okuma yönüne göre geçerli dokunma davranışlarını çözer.
     *
     * Kullanıcı "varsayılan" bıraktığında (PREV/NEXT) yön otomatik uyarlanır:
     *  - RTL manga: sol kenar = sonraki sayfa (sayfalar sağdan sola akar)
     *  - LTR/webtoon: sol kenar = önceki sayfa
     * Kullanıcı menü/hiçbir şey seçtiyse aynen korunur.
     */
    fun resolveTapActions(): Triple<MangaTapAction, MangaTapAction, MangaTapAction> {
        val state = _uiState.value
        val rtl = state.readingMode == ReadingMode.RightToLeft
        fun side(action: MangaTapAction, isLeft: Boolean): MangaTapAction = when (action) {
            MangaTapAction.Previous -> if (rtl && isLeft) MangaTapAction.Next else MangaTapAction.Previous
            MangaTapAction.Next -> if (rtl && isLeft) MangaTapAction.Previous else MangaTapAction.Next
            else -> action
        }
        return Triple(
            side(state.tapLeftAction, isLeft = true),
            state.tapCenterAction,
            side(state.tapRightAction, isLeft = false)
        )
    }

    /** Jest: iki parmakla kaydırma ile parlaklık (0.1 – 1.0). */
    fun adjustBrightness(delta: Float) {
        val next = (_uiState.value.customBrightness + delta).coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(customBrightness = next) }
        viewModelScope.launch { settingsDataStore.setMangaBrightness(next) }
    }

    fun setBrightnessGestureEnabled(enabled: Boolean) {
        _uiState.update { it.copy(brightnessGestureEnabled = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaBrightnessGesture(enabled) }
    }

    fun setWebtoonGap(gapDp: Int) {
        _uiState.update { it.copy(webtoonGapDp = gapDp.coerceIn(0, 48)) }
        viewModelScope.launch { settingsDataStore.setMangaWebtoonGap(gapDp) }
    }

    fun setVolumeKeysEnabled(enabled: Boolean) {
        _uiState.update { it.copy(volumeKeysEnabled = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaVolumeKeys(enabled) }
    }

    fun setVolumeKeysInvert(invert: Boolean) {
        _uiState.update { it.copy(volumeKeysInvert = invert) }
        viewModelScope.launch { settingsDataStore.setMangaVolumeKeysInvert(invert) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaKeepScreenOn(enabled) }
    }

    fun setFullscreenEnabled(enabled: Boolean) {
        _uiState.update { it.copy(fullscreenEnabled = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaFullscreen(enabled) }
    }

    fun setCropBorders(enabled: Boolean) {
        _uiState.update { it.copy(cropBorders = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaCropBorders(enabled) }
    }

    fun setSplitWidePages(enabled: Boolean) {
        _uiState.update { it.copy(splitWidePages = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaSplitWidePages(enabled) }
    }

    fun setRotateWidePages(enabled: Boolean) {
        _uiState.update { it.copy(rotateWidePages = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaRotateWidePages(enabled) }
    }

    fun setSidePadding(dpValue: Int) {
        _uiState.update { it.copy(sidePaddingDp = dpValue.coerceIn(0, 120)) }
        viewModelScope.launch { settingsDataStore.setMangaSidePadding(dpValue) }
    }

    fun setAnimatePageTransition(enabled: Boolean) {
        _uiState.update { it.copy(animatePageTransition = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaAnimatePageTransition(enabled) }
    }

    fun setEinkFlash(enabled: Boolean) {
        _uiState.update { it.copy(einkFlash = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaEinkFlash(enabled) }
    }

    fun setShowPageNumber(enabled: Boolean) {
        _uiState.update { it.copy(showPageNumber = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaShowPageNumber(enabled) }
    }

    fun setSkipReadChapters(enabled: Boolean) {
        _uiState.update { it.copy(skipReadChapters = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaSkipReadChapters(enabled) }
    }

    fun setAutoNextChapter(enabled: Boolean) {
        _uiState.update { it.copy(autoNextChapter = enabled) }
        viewModelScope.launch { settingsDataStore.setMangaAutoNextChapter(enabled) }
    }

    fun setTapActions(left: MangaTapAction, center: MangaTapAction, right: MangaTapAction) {
        _uiState.update {
            it.copy(tapLeftAction = left, tapCenterAction = center, tapRightAction = right)
        }
        viewModelScope.launch {
            settingsDataStore.setMangaTapActions(
                MangaTapAction.idOf(left),
                MangaTapAction.idOf(center),
                MangaTapAction.idOf(right)
            )
        }
    }

    private fun normalizeTapAction(raw: String?, fallback: MangaTapAction): MangaTapAction =
        if (raw.isNullOrBlank()) fallback else MangaTapAction.fromId(raw)

    // ─── Bölüm gezinme ───────────────────────────────────────────────────────

    // F6: Bölüm değişiminde eski queue'yu temizle — yanlış sayfa yükleme önler
    fun goToChapter(chapter: MangaChapter) {
        loader.resetQueue()
        _uiState.update { it.copy(currentChapter = chapter, currentPageIndex = 0) }
        restoreProgress(chapter)
    }

    /**
     * Bölümleri **okuma sırasına** (1 → N) göre dizer.
     *
     * Neden: kaynaklar bölüm listesini kimi zaman yeniden eskiye, kimi zaman eskiden
     * yeniye döndürür. Eskiden "sonraki bölüm" listeyi hep ters kabul edip `idx - 1`
     * kullanıyordu; kaynak artan sırada verdiyse ileri/geri düğmeleri ters çalışıyordu.
     * Artık numara varsa numaraya göre, yoksa kaynak sırasının tersi (yaygın durum)
     * kullanılır — hangi kaynak olursa olsun yön doğru kalır.
     */
    private fun chaptersInReadingOrder(): List<MangaChapter> {
        val list = _uiState.value.chapterList
        if (list.isEmpty()) return emptyList()
        // Numara bilinmiyorsa addan çözülür (Türkçe/İngilizce kalıpları destekler).
        val withNumbers = list.map { chapter ->
            val number = if (chapter.chapterNumber >= 0f) chapter.chapterNumber
            else MangaChapterNumberResolver.resolve(
                mangaTitle = mangaDetails.title,
                chapterName = chapter.name,
                declaredNumber = chapter.chapterNumber
            )
            chapter to number
        }
        val order = MangaChapterSequence.readingOrder(withNumbers.map { it.first.url to it.second })
        val byUrl = withNumbers.associate { it.first.url to it.first }
        return order.mapNotNull { byUrl[it] }
    }

    /** Okuma sırasına göre geçerli bölümün konumu (-1 = bulunamadı). */
    private fun currentReadingIndex(ordered: List<MangaChapter> = chaptersInReadingOrder()): Int =
        ordered.indexOfFirst { it.url == _uiState.value.currentChapter.url }

    fun goToNextChapter() {
        val ordered = chaptersInReadingOrder()
        val idx = currentReadingIndex(ordered)
        if (idx >= 0 && idx < ordered.size - 1) goToChapter(ordered[idx + 1])
    }

    fun goToPreviousChapter() {
        val ordered = chaptersInReadingOrder()
        val idx = currentReadingIndex(ordered)
        if (idx > 0) goToChapter(ordered[idx - 1])
    }

    val hasNextChapter: Boolean
        get() {
            val ordered = chaptersInReadingOrder()
            val idx = currentReadingIndex(ordered)
            return idx >= 0 && idx < ordered.size - 1
        }

    /** Sonraki bölümün adı (bölüm sonu geçiş kartında göstermek için); yoksa null. */
    val nextChapterName: String?
        get() {
            val ordered = chaptersInReadingOrder()
            val idx = currentReadingIndex(ordered)
            return if (idx >= 0 && idx < ordered.size - 1) ordered[idx + 1].name else null
        }

    val hasPreviousChapter: Boolean
        get() {
            val ordered = chaptersInReadingOrder()
            val idx = currentReadingIndex(ordered)
            return idx > 0
        }

    private var pageListJob: kotlinx.coroutines.Job? = null

    private fun restoreProgress(chapter: MangaChapter) {
        pageListJob?.cancel()
        pageListJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPages = true, pagesError = null, pagesErrorCode = null) }
            val saved = dao.getProgress(chapter.url)
            val pageIndex = if (saved != null && !saved.isCompleted) saved.lastPageIndex else 0
            _uiState.update { it.copy(currentPageIndex = pageIndex) }
            try {
                loader.loadPageList(chapter)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val restoredIndex = pageIndex.coerceIn(0, (loader.pages.value.size - 1).coerceAtLeast(0))
                _uiState.update { it.copy(currentPageIndex = restoredIndex) }
                loader.onPageChanged(restoredIndex)
                if (loader.pages.value.isEmpty()) {
                    _uiState.update { it.copy(isLoadingPages = false, pagesError = "Sayfalar yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.") }
                } else {
                    _uiState.update { it.copy(isLoadingPages = false) }
                }
            } catch (e: com.kitsugi.animelist.data.manga.CaptchaRequiredException) {
                // F9: Captcha tespiti — kullanıcıya anlamlı mesaj göster
                _uiState.update {
                    it.copy(
                        isLoadingPages = false,
                        pagesError = "⚠️ ${e.sourceName}: Bu bölümü açmak için captcha doğrulaması gerekiyor. Tarayıcıda açmayı deneyin."
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) kotlinx.coroutines.currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(isLoadingPages = false, pagesError = e.localizedMessage ?: e.message ?: "Sayfalar yüklenirken bir hata oluştu.", pagesErrorCode = SourceFailureClassifier.classifyCode(e)) }
            }
        }
    }

    /**
     * Geçerli sayfayı JPG olarak Galeri'ye kaydeder (Mihon "save page" paritesi).
     *
     * Akış: çözümlenmiş sayfa URL'si → MediaStore görsel kaydı (Pictures/Kitsugi).
     * İndirme için Coil yerine doğrudan HTTP kullanılır; böylece bellek/kiremit
     * önbelleğinden bağımsız, tam çözünürlüklü dosya yazılır.
     *
     * @return Başarılıysa true.
     */
    fun savePageToGallery(page: MangaPage, chapterName: String): Boolean {
        val imageUrl = page.imageUrl ?: page.url
        if (imageUrl.isBlank()) return false
        return try {
            val safeName = buildString {
                append(chapterName.take(40).replace(Regex("[^\p{L}\p{N} _-]"), "_").trim())
                append("_s").append(page.index + 1)
            }.ifBlank { "kitsugi_page_${page.index + 1}" }

            // 1) Yerel önbellek varsa doğrudan kopyala (en hızlı yol).
            val cached = loader.cache.getImageFile(imageUrl)
            val bytes: ByteArray = if (cached.exists()) {
                cached.readBytes()
            } else {
                val conn = java.net.URL(imageUrl).openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Kitsugi)")
                }
                conn.getInputStream().use { it.readBytes() }
            }
            if (bytes.isEmpty()) return false

            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$safeName.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/Kitsugi")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun retryLoadPages() {
        restoreProgress(_uiState.value.currentChapter)
    }

    private fun classifySourceStatus(error: Throwable): SourceHealthStatus {
        return SourceFailureClassifier.classifyStatus(error)
    }

    override fun onCleared() {
        super.onCleared()
        loader.recycle()
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    class Factory(
        private val context: Context,
        private val source: MangaSource,
        private val mangaDetails: MangaDetails,
        private val initialChapter: MangaChapter,
        private val suggestedMode: ReadingMode? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaReaderViewModel(context, source, mangaDetails, initialChapter, suggestedMode) as T
    }
}

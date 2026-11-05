package com.kitsugi.animelist.ui.screens.manga

import kotlinx.coroutines.ensureActive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MangaChapterProgressEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceStateStore
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
    initialChapter: MangaChapter
) : ViewModel() {

    private val db  = KitsugiDatabase.getDatabase(context)
    private val dao = db.mangaChapterProgressDao()
    private val cache = MangaCache(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val sourceStateStore = MangaSourceStateStore(context)
    private val downloader = com.kitsugi.animelist.data.manga.download.MangaChapterDownloader(context)

    // İndirilmiş bölüm varsa ağ yerine yerel CBZ arşivi okunur (çevrimdışı destek).
    val loader = MangaPageLoaderV2(
        context,
        source,
        cache,
        archiveResolver = { chapter -> archiveFor(chapter) }
    )

    /**
     * Bölümün indirilmiş arşivini bulur.
     *
     * Dosya adı numara/sıra tabanlı olduğu için o an bilinen bölüm listesindeki sıra
     * kullanılır; liste henüz yüklenmemişse 0 ile denenir. Bulunamazsa null döner ve
     * okuyucu normal akışa (ağ) düşer.
     */
    private fun archiveFor(chapter: MangaChapter): java.io.File? = runCatching {
        val index = _uiState.value.chapterList
            .sortedWith(compareBy({ it.chapterNumber < 0f }, { it.chapterNumber }))
            .indexOfFirst { it.url == chapter.url }
        downloader.existingArchive(
            source = source,
            mangaTitle = mangaDetails.title,
            mangaUrl = mangaDetails.url,
            chapter = chapter,
            index = if (index >= 0) index else 0
        )
    }.getOrNull()
    val pages: StateFlow<List<MangaPage>> = loader.pages

    // F5: Debounce için Job referansı — hızlı swipe'ta Room IO flood önler
    private var progressSaveJob: kotlinx.coroutines.Job? = null

    /** Debounce bekleyen son ilerleme; ekran kapanırken/arkaya geçerken anında yazılır. */
    private var pendingProgress: PendingProgress? = null

    private data class PendingProgress(
        val chapter: MangaChapter,
        val pageIndex: Int,
        val totalPages: Int
    )

    /**
     * Okuyucudan çıkarken / uygulama arkaya geçerken çağrılır.
     *
     * Debounce (500 ms) bekleyen ilerleme varsa **hemen** yazılır; aksi hâlde kullanıcı son
     * sayfadan çıkıp geri döndüğünde "son okunan sayfa" bir önceki sayfada kalıyordu.
     */
    fun flushProgress() {
        val pending = pendingProgress ?: return
        pendingProgress = null
        progressSaveJob?.cancel()
        progressSaveJob = null
        viewModelScope.launch { saveProgressNow(pending) }
    }

    /**
     * İlerlemeyi tek noktadan yazar: bölüm ilerlemesi (Room) + okuma geçmişi (geçmiş sayfası).
     * Hata olsa bile okuma akışı kesilmez.
     */
    private suspend fun saveProgressNow(pending: PendingProgress) {
        val isCompleted = pending.totalPages > 0 && pending.pageIndex >= pending.totalPages - 1
        if (isCompleted) {
            // Sonraki bölüme geçerken bu bölümün "okundu" sayılması için anında ekle.
            _uiState.update {
                it.copy(readChapterUrls = it.readChapterUrls + pending.chapter.url)
            }
        }
        runCatching {
            dao.upsert(
                MangaChapterProgressEntity(
                    chapterUrl    = pending.chapter.url,
                    mangaUrl      = mangaDetails.url,
                    chapterName   = pending.chapter.name,
                    lastPageIndex = pending.pageIndex,
                    totalPages    = pending.totalPages,
                    isCompleted   = isCompleted,
                    updatedAt     = System.currentTimeMillis()
                )
            )
            com.kitsugi.animelist.data.local.MangaReadHistoryManager.record(
                sourceName = source.name,
                mangaUrl = mangaDetails.url,
                mangaTitle = mangaDetails.title,
                thumbnailUrl = mangaDetails.thumbnailUrl,
                chapterUrl = pending.chapter.url,
                chapterName = pending.chapter.name,
                lastPageIndex = pending.pageIndex,
                totalPages = pending.totalPages,
                isCompleted = isCompleted
            )
        }
    }

    // ─── UI Durumu ────────────────────────────────────────────────────────────

    data class UiState(
        val currentChapter: MangaChapter,
        val chapterList: List<MangaChapter> = emptyList(),
        val currentPageIndex: Int = 0,
        val readingMode: ReadingMode = ReadingMode.RightToLeft,
        val colorFilterType: ColorFilterType = ColorFilterType.Normal,
        val fitMode: MangaFitMode = MangaFitMode.FitScreen,
        val customBrightness: Float = 1.0f,
        val isLoadingChapters: Boolean = false,
        val error: String? = null,
        val isLoadingPages: Boolean = false,
        val pagesError: String? = null,
        /** Sayfa yükleme hatasının manga kaynak kodu (KTS-SRC-...). */
        val pagesErrorCode: String? = null,
        /** Okurken ekran kapanmasın (Ayarlar > Okuyucu). */
        val keepScreenOn: Boolean = true,
        /** Bölüm geçişinde okunmuş bölümleri atla (Ayarlar > Okuyucu). */
        val skipReadChapters: Boolean = false,
        /** Bölüm bitince otomatik sonraki bölüme geç (Ayarlar > Okuyucu). */
        val autoAdvanceChapter: Boolean = false,
        /** Okundu olarak işaretlenmiş bölüm URL'leri — atlama için. */
        val readChapterUrls: Set<String> = emptySet(),
        /** Sayfa kenarlarını kırp (Mihon "crop borders"). */
        val cropBorders: Boolean = false
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
                // R2: Webtoon modunda 3+1 keep, pager modunda 2+1 keep preload
                loader.preloadAhead = if (mode == ReadingMode.Webtoon) 3 else 2
                loader.keepBehind   = 1
                _uiState.update {
                    it.copy(
                        readingMode = mode,
                        colorFilterType = filter,
                        fitMode = fit,
                        customBrightness = settings.mangaBrightness,
                        keepScreenOn = settings.mangaKeepScreenOn,
                        skipReadChapters = settings.mangaSkipReadChapters,
                        autoAdvanceChapter = settings.mangaAutoAdvanceChapter,
                        cropBorders = settings.mangaCropBorders
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
                // Okunan bölümler "skip read" için tek seferde çekilir (Room).
                val readUrls = runCatching {
                    dao.getAllForManga(mangaDetails.url)
                        .filter { it.isCompleted }
                        .map { it.chapterUrl }
                        .toSet()
                }.getOrDefault(emptySet())
                _uiState.update {
                    it.copy(
                        chapterList = chapters,
                        isLoadingChapters = false,
                        readChapterUrls = readUrls
                    )
                }
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
        val chapter = _uiState.value.currentChapter
        pendingProgress = PendingProgress(chapter, pageIndex, totalPages)
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500L)
            val pending = pendingProgress ?: return@launch
            pendingProgress = null
            saveProgressNow(pending)
        }
    }

    // ─── Okuma modu değiştirme ────────────────────────────────────────────────

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            settingsDataStore.setMangaReadingMode(mode.name)
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

    /** Okunmuş bölümleri atla tercihi — kalıcı (Ayarlar > Okuyucu ile aynı anahtar). */
    fun setSkipReadChapters(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaSkipReadChapters(enabled) }
    }

    /** Kenarlık kırpma tercihi — kalıcı. */
    fun setCropBorders(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaCropBorders(enabled) }
    }

    /** Bölüm bitince otomatik geçiş tercihi — kalıcı. */
    fun setAutoAdvanceChapter(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMangaAutoAdvanceChapter(enabled) }
    }

    // ─── Bölüm gezinme ───────────────────────────────────────────────────────

    // F6: Bölüm değişiminde eski queue'yu temizle — yanlış sayfa yükleme önler
    fun goToChapter(chapter: MangaChapter) {
        loader.resetQueue()
        _uiState.update { it.copy(currentChapter = chapter, currentPageIndex = 0) }
        restoreProgress(chapter)
    }

    /**
     * Bölüm listesi **okuma sırasına göre artan** (1 → N) tutulur.
     *
     * Önceden liste eklentiden geldiği gibi (yeniden eskiye) kullanılıyor ve "sonraki bölüm"
     * `idx - 1` ile bulunuyordu. Bu, hem sıralama tercihini yok sayıyordu hem de kullanıcı
     * "1 → N" istediğinde sonraki/önceki bölüm düğmelerini ters çeviriyordu. Artık liste
     * her zaman [ChapterSortMode.OldestFirst] sırasına normalize edilir ve geçişler +1 yönünde
     * yapılır; sıralama tercihi yalnızca **görüntülemeyi** etkiler.
     *
     * Ayrıca istenirse okunmuş bölümler atlanır ([skipReadChapters]).
     */
    fun goToNextChapter() {
        val target = nextChapter() ?: return
        goToChapter(target)
    }

    fun goToPreviousChapter() {
        val target = previousChapter() ?: return
        goToChapter(target)
    }

    /** Sıradaki bölüm — okunanları atlama tercihi uygulanmış hâlde. */
    private fun nextChapter(): MangaChapter? {
        val list = orderedChapters()
        val idx = list.indexOfFirst { it.url == _uiState.value.currentChapter.url }
        if (idx < 0) return null
        val candidates = list.drop(idx + 1)
        if (!_uiState.value.skipReadChapters) return candidates.firstOrNull()
        val read = _uiState.value.readChapterUrls
        return candidates.firstOrNull { it.url !in read } ?: candidates.firstOrNull()
    }

    private fun previousChapter(): MangaChapter? {
        val list = orderedChapters()
        val idx = list.indexOfFirst { it.url == _uiState.value.currentChapter.url }
        if (idx <= 0) return null
        // Geriye doğru da okunanları atla (Mihon "skip read chapters" davranışı).
        val candidates = list.take(idx).reversed()
        if (!_uiState.value.skipReadChapters) return candidates.firstOrNull()
        val read = _uiState.value.readChapterUrls
        return candidates.firstOrNull { it.url !in read } ?: candidates.firstOrNull()
    }

    /**
     * Bölüm listesini okuma sırasına (1 → N) normalize eder.
     *
     * Numara bilinmeyen (-1) kayıtlar kaynak sırasını korur ve sona düşer; böylece
     * numarasız "özel bölüm" kayıtları yüzünden sıra bozulmaz.
     */
    private fun orderedChapters(): List<MangaChapter> {
        val list = _uiState.value.chapterList
        val numbered = list.filter { it.chapterNumber >= 0f }.sortedBy { it.chapterNumber }
        val unnumbered = list.filter { it.chapterNumber < 0f }
        return numbered + unnumbered
    }

    val hasNextChapter: Boolean
        get() = nextChapter() != null

    /** Sonraki bölümün adı (bölüm sonu geçiş kartında göstermek için); yoksa null. */
    val nextChapterName: String?
        get() = nextChapter()?.name

    val hasPreviousChapter: Boolean
        get() = previousChapter() != null

    val previousChapterName: String?
        get() = previousChapter()?.name

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
        private val initialChapter: MangaChapter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaReaderViewModel(context, source, mangaDetails, initialChapter) as T
    }
}

package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.ChapterFilter
import com.kitsugi.animelist.data.manga.ChapterSort
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaChapterNumberResolver
import com.kitsugi.animelist.data.manga.MangaChapterGrouping
import com.kitsugi.animelist.data.manga.MangaChapterOrdering
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaKindDetector
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MangaDetailScreen için ViewModel.
 *
 * Sorumluluklar:
 * - Bölüm listesini eklentiden çekmek
 * - Room'dan okuma ilerlemesini okuyup her bölümün okundu/devam durumunu hesaplamak
 * - "Oku / Devam Et" butonunun hedef bölümünü belirlemek
 *
 * NOT: Mevcut okuyucu ve navigasyon akışına dokunmaz; yalnızca detay ekranını besler.
 */
class MangaDetailViewModel(
    private val context: Context,
    val source: MangaSource,
    initialDetails: MangaDetails
) : ViewModel() {

    private val db = KitsugiDatabase.getDatabase(context)
    private val progressDao = db.mangaChapterProgressDao()

    /** Bir bölümün okuma durumunu UI'a taşıyan sarmalayıcı. */
    data class ChapterRow(
        val chapter: MangaChapter,
        val isCompleted: Boolean = false,
        val lastPageIndex: Int = 0,
        val totalPages: Int = 0,
        val isInProgress: Boolean = false,
        /** Ad/eklenti verisinden çözülen bölüm numarası; bilinmiyorsa -1f. */
        val resolvedNumber: Float = -1f,
        /** "Cilt 3" bilgisi (addan çıkarılır); yoksa null. */
        val volume: Int? = null
    ) {
        /** "137", "12.5" gibi gösterim etiketi; numara yoksa null. */
        val numberLabel: String? get() = MangaChapterNumberResolver.label(resolvedNumber)
    }

    data class UiState(
        val details: MangaDetails,
        val isLoadingDetails: Boolean = false,
        val isLoadingChapters: Boolean = true,
        /** Filtre + sıralama uygulanmış, ekranda çizilen liste. */
        val chapters: List<ChapterRow> = emptyList(),
        /** Kaynaktan gelen tüm bölümler (filtresiz) — "Tümü" sayacı ve çipler için. */
        val allChapters: List<ChapterRow> = emptyList(),
        /** Seçilebilir tarama grupları (scanlator) — çipler. */
        val scanlators: List<String> = emptyList(),
        val chapterSort: ChapterSort = ChapterSort.NewestFirst,
        val chapterFilter: ChapterFilter = ChapterFilter.All,
        /** Seçili tarama grubu; null = tüm gruplar. */
        val scanlatorFilter: String? = null,
        /** Bölümleri cilt (volume) gruplarına katla. */
        val groupByVolume: Boolean = false,
        /** Seri türü tahmini (manga/manhwa/manhua/webtoon/novel). */
        val kind: MangaKindDetector.Kind = MangaKindDetector.Kind.Unknown,
        val error: String? = null,
        /** Hatanın manga kaynak kodu (KTS-SRC-...). */
        val errorCode: String? = null,
        /** "Devam Et" için hedef bölüm; yoksa null (baştan okunur). */
        val resumeChapter: MangaChapter? = null
    )

    private val _uiState = MutableStateFlow(UiState(details = initialDetails))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshDetails()
        // Kullanıcının varsayılan sıralama/filtre tercihini uygula (ayarlardan).
        viewModelScope.launch {
            runCatching {
                val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(context)
                    .settingsFlow.first()
                _uiState.update {
                    it.copy(
                        chapterSort = ChapterSort.fromId(settings.mangaChapterSort),
                        chapterFilter = ChapterFilter.fromId(settings.mangaChapterFilter)
                    )
                }
            }
        }
        loadChapters()
    }

    /** Kapak/açıklama gibi alanları tazelemek için detayları yeniden çeker (best-effort). */
    private fun refreshDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true) }
            val initial = _uiState.value.details
            try {
                val fresh = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(15_000L) {
                        source.fetchMangaDetails(initial.url)
                    }
                }

                // Bazı kaynaklar fetchMangaDetails başarısız olunca title = url döndürür.
                // URL'e benzeyen (/ ile başlayan veya :// içeren) bir title anlamlı değil
                // demektir — bu durumda mevcut (browse sayfasından gelen) title'ı koru.
                fun String?.isUrlLike() = this != null && (startsWith("/") || contains("://"))
                fun String?.isMeaningful() = !isNullOrBlank() && !isUrlLike()

                val merged = initial.copy(
                    title        = fresh.title.takeIf { it.isMeaningful() } ?: initial.title,
                    thumbnailUrl = fresh.thumbnailUrl.takeIf { it.isMeaningful() }
                                       ?: initial.thumbnailUrl,
                    description  = fresh.description?.takeIf { it.isNotBlank() }
                                       ?: initial.description,
                    author       = fresh.author?.takeIf { it.isNotBlank() } ?: initial.author,
                    artist       = fresh.artist?.takeIf { it.isNotBlank() } ?: initial.artist,
                    genre        = fresh.genre.takeIf { it.isNotEmpty() } ?: initial.genre,
                    status       = if (fresh.status != MangaStatus.Unknown) fresh.status else initial.status,
                    source       = initial.source.takeIf { it.isNotBlank() } ?: fresh.source
                )
                _uiState.update { it.copy(details = merged, isLoadingDetails = false) }
            } catch (_: Exception) {
                // Detay tazeleme başarısız olsa bile elimizdeki initialDetails ile devam ederiz.
                _uiState.update { it.copy(isLoadingDetails = false) }
            }
        }
    }

    fun loadChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChapters = true, error = null, errorCode = null) }
            var lastError: Exception? = null
            // Ağ gecikmelerine karşı 2 deneme yap
            repeat(2) attempt@{ attempt ->
                try {
                    val mangaUrl = _uiState.value.details.url
                    val chapters = withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeout(45_000L) {
                            source.fetchChapterList(mangaUrl)
                        }
                    }
                    val progressList = withContext(Dispatchers.IO) {
                        progressDao.getAllForManga(mangaUrl)
                    }
                    val progressByUrl = progressList.associateBy { it.chapterUrl }

                    val rows = chapters.map { ch ->
                        val p = progressByUrl[ch.url]
                        ChapterRow(
                            chapter = ch,
                            isCompleted = p?.isCompleted == true,
                            lastPageIndex = p?.lastPageIndex ?: 0,
                            totalPages = p?.totalPages ?: 0,
                            isInProgress = p != null && p.isCompleted.not() && p.lastPageIndex > 0,
                            resolvedNumber = MangaChapterNumberResolver.resolve(
                                mangaTitle = _uiState.value.details.title,
                                chapterName = ch.name,
                                declaredNumber = ch.chapterNumber
                            ),
                            volume = MangaChapterNumberResolver.volumeOf(ch.name)
                        )
                    }
                    val scanlators = MangaChapterOrdering.scanlatorsOf(rows.map { it.chapter.scanlator })
                    val detailsState = _uiState.value.details
                    // Tür/okuma modu önerisi: kaynak adı + etiketler + seri adı.
                    val kind = MangaKindDetector.detect(
                        sourceName = detailsState.source.ifBlank { source.name },
                        title = detailsState.title,
                        genres = detailsState.genre
                    )

                    // "Devam Et" hedefi: en son güncellenen, tamamlanmamış bölüm.
                    val resume = progressList
                        .filter { !it.isCompleted }
                        .maxByOrNull { it.updatedAt }
                        ?.let { prog -> chapters.firstOrNull { it.url == prog.chapterUrl } }

                    _uiState.update { state ->
                        state.copy(
                            isLoadingChapters = false,
                            allChapters = rows,
                            scanlators = scanlators,
                            // Seçili tarama grubu kaybolduysa (kaynak değişti) filtreyi sıfırla.
                            scanlatorFilter = state.scanlatorFilter
                                ?.takeIf { selected -> scanlators.any { it.equals(selected, true) } },
                            kind = kind,
                            resumeChapter = resume,
                            error = if (rows.isEmpty()) "Bu manga için bölüm bulunamadı." else null,
                            errorCode = null
                        )
                    }
                    applyListTransform()
                    return@launch  // Başarılı — döngüden çık
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 0) {
                        kotlinx.coroutines.delay(1_500L)  // Kısa bekleme sonrası tekrar dene
                    }
                }
            }
            // Her iki deneme de başarısız
            _uiState.update {
                it.copy(
                    isLoadingChapters = false,
                    error = lastError?.localizedMessage ?: lastError?.message ?: "Bölümler yüklenemedi. Tekrar deneyin.",
                    errorCode = SourceFailureClassifier.classifyCode(lastError)
                )
            }
        }
    }

    /**
     * "Oku / Devam Et" butonuna basıldığında açılacak bölümü döndürür.
     * - Devam edilecek bölüm varsa onu,
     * - Yoksa listedeki en eski (baştan) bölümü döndürür.
     */
    fun chapterToOpen(): MangaChapter? {
        val state = _uiState.value
        state.resumeChapter?.let { return it }
        // ÖNEMLİ: "Baştan oku" her zaman EN ESKİ bölümü açmalı. Ekrandaki liste
        // kullanıcı sıralamasına göre ters çevrilebilir; bu yüzden kaynak sırasını
        // koruyan [UiState.allChapters] (en yeni → en eski) kullanılır.
        return (state.allChapters.lastOrNull() ?: state.chapters.lastOrNull())?.chapter
    }

    // ─── Sıralama & Filtre ────────────────────────────────────────────────────

    /** Bölüm listesi sıralamasını değiştirir (yeni üstte / numara artan-azalan / A-Z). */
    fun setChapterSort(sort: ChapterSort) {
        _uiState.update { it.copy(chapterSort = sort) }
        applyListTransform()
        viewModelScope.launch {
            runCatching {
                com.kitsugi.animelist.data.settings.SettingsDataStore(context)
                    .setMangaChapterSort(sort.name)
            }
        }
    }

    /** Aynı sıralamaya tekrar basıldığında yönü çevirir (Mihon'daki ok ikonunun karşılığı). */
    fun toggleChapterSortDirection() {
        val next = when (_uiState.value.chapterSort) {
            ChapterSort.NewestFirst -> ChapterSort.NumberAsc
            ChapterSort.NumberAsc -> ChapterSort.NumberDesc
            ChapterSort.NumberDesc -> ChapterSort.NumberAsc
            ChapterSort.TitleAsc -> ChapterSort.NumberDesc
        }
        setChapterSort(next)
    }

    /** Okuma durumuna göre filtre (Tümü / Okunmamış / Okunmuş / Ekstraları gizle). */
    fun setChapterFilter(filter: ChapterFilter) {
        _uiState.update { it.copy(chapterFilter = filter) }
        applyListTransform()
        viewModelScope.launch {
            runCatching {
                com.kitsugi.animelist.data.settings.SettingsDataStore(context)
                    .setMangaChapterFilter(filter.name)
            }
        }
    }

    /** Tarama grubu çipi; aynı gruba tekrar basınca filtre kalkar. */
    fun setScanlatorFilter(scanlator: String?) {
        _uiState.update { state ->
            val next = when {
                scanlator == null -> null
                state.scanlatorFilter.equals(scanlator, ignoreCase = true) -> null
                else -> scanlator
            }
            state.copy(scanlatorFilter = next)
        }
        applyListTransform()
    }

    /** Cilt gruplamasını açar/kapatır (uzun serilerde 1000 satır yerine 80 grup). */
    fun toggleVolumeGrouping() {
        _uiState.update { it.copy(groupByVolume = !it.groupByVolume) }
    }

    /** Ekrandaki listeyi cilt gruplarına ayırır (UI katlanabilir başlıklar çizer). */
    fun volumeGroups(): List<MangaChapterGrouping.Group<ChapterRow>> =
        MangaChapterGrouping.groupByVolume(
            rows = _uiState.value.chapters,
            volumeOf = { it.volume },
            number = { it.resolvedNumber }
        )

    /** Seçili filtre nedeniyle hiç bölüm kalmadıysa filtreyi gevşetir. */
    fun clearChapterFilters() {
        _uiState.update {
            it.copy(chapterFilter = ChapterFilter.All, scanlatorFilter = null)
        }
        applyListTransform()
    }

    /**
     * Bölümü "okundu" olarak işaretler. İlerleme Room'a yazılır; liste anında
     * güncellenir (okunmamış filtresinde satır listeden düşer).
     */
    fun markChapterRead(row: ChapterRow) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                progressDao.upsert(
                    com.kitsugi.animelist.data.local.MangaChapterProgressEntity(
                        chapterUrl = row.chapter.url,
                        mangaUrl = _uiState.value.details.url,
                        chapterName = row.chapter.name,
                        lastPageIndex = (row.totalPages - 1).coerceAtLeast(0),
                        totalPages = row.totalPages,
                        isCompleted = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.update { state ->
                state.copy(
                    allChapters = state.allChapters.map {
                        if (it.chapter.url == row.chapter.url) {
                            it.copy(
                                isCompleted = true,
                                isInProgress = false,
                                lastPageIndex = (it.totalPages - 1).coerceAtLeast(0)
                            )
                        } else it
                    },
                    resumeChapter = state.resumeChapter?.takeIf { it.url != row.chapter.url }
                )
            }
            applyListTransform()
        }
    }

    /**
     * Tüm bölümleri tek dokunuşla "okundu" yapar (Mihon'da "Mark all as read").
     * Room'a tek tek yazmak yerine tek transaction benzeri toplu upsert kullanılır.
     */
    fun markAllRead() {
        val rows = _uiState.value.allChapters.filter { !it.isCompleted }
        if (rows.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                rows.forEachIndexed { index, row ->
                    progressDao.upsert(
                        com.kitsugi.animelist.data.local.MangaChapterProgressEntity(
                            chapterUrl = row.chapter.url,
                            mangaUrl = _uiState.value.details.url,
                            chapterName = row.chapter.name,
                            lastPageIndex = (row.totalPages - 1).coerceAtLeast(0),
                            totalPages = row.totalPages,
                            isCompleted = true,
                            updatedAt = now - index
                        )
                    )
                }
            }
            _uiState.update { state ->
                state.copy(
                    allChapters = state.allChapters.map {
                        if (it.isCompleted) it else it.copy(isCompleted = true, isInProgress = false)
                    },
                    resumeChapter = null
                )
            }
            applyListTransform()
        }
    }

    /** "Okundu" işaretini geri alır. */
    fun markChapterUnread(row: ChapterRow) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                progressDao.upsert(
                    com.kitsugi.animelist.data.local.MangaChapterProgressEntity(
                        chapterUrl = row.chapter.url,
                        mangaUrl = _uiState.value.details.url,
                        chapterName = row.chapter.name,
                        lastPageIndex = 0,
                        totalPages = row.totalPages,
                        isCompleted = false,
                        updatedAt = 0L
                    )
                )
            }
            _uiState.update { state ->
                state.copy(
                    allChapters = state.allChapters.map {
                        if (it.chapter.url == row.chapter.url) {
                            it.copy(isCompleted = false, isInProgress = false, lastPageIndex = 0)
                        } else it
                    }
                )
            }
            applyListTransform()
        }
    }

    /** Okunmamış bölüm sayısı — başlıktaki sayaç ve "Devam Et" ipucu için. */
    val unreadCount: Int
        get() = _uiState.value.allChapters.count { !it.isCompleted }

    /**
     * [allChapters] üzerinden filtre + sıralama uygular ve [UiState.chapters]'ı tazeler.
     * Tüm liste dönüşümleri tek noktadan geçer; böylece sıra/filtre tutarlı kalır.
     */
    private fun applyListTransform() {
        _uiState.update { state ->
            val visible = MangaChapterOrdering.apply(
                rows = state.allChapters,
                filter = state.chapterFilter,
                sort = state.chapterSort,
                scanlatorFilter = state.scanlatorFilter,
                number = { it.resolvedNumber },
                isCompleted = { it.isCompleted },
                scanlator = { it.chapter.scanlator },
                title = { it.chapter.name }
            )
            state.copy(chapters = visible)
        }
    }

    class Factory(
        private val context: Context,
        private val source: MangaSource,
        private val details: MangaDetails
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MangaDetailViewModel(context, source, details) as T
    }
}

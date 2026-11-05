package com.kitsugi.animelist.ui.screens.manga

import android.content.Context
import com.kitsugi.animelist.data.manga.SourceFailureClassifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Okuyucudan dönüldüğünde çağrılır: yalnızca Room'daki okuma ilerlemesini tazeler.
     *
     * ViewModel, okuyucu açıkken de yaşadığı için (Activity'nin ViewModelStore'unda kalır)
     * bölüm listesi çekilmeden sadece ilerleme satırları yeniden okunur — ağ trafiği yok,
     * bölüm listesi yeniden sıralanıp kaydırma konumu bozulmaz.
     */
    fun refreshProgress() {
        val state = _uiState.value
        if (state.chapters.isEmpty()) return
        viewModelScope.launch {
            val mangaUrl = state.details.url
            val progressList = withContext(Dispatchers.IO) { progressDao.getAllForManga(mangaUrl) }
            val progressByUrl = progressList.associateBy { it.chapterUrl }
            val rows = state.chapters.map { row ->
                val p = progressByUrl[row.chapter.url] ?: return@map row
                row.copy(
                    isCompleted = p.isCompleted,
                    lastPageIndex = p.lastPageIndex,
                    totalPages = p.totalPages,
                    isInProgress = !p.isCompleted && p.lastPageIndex > 0
                )
            }
            val resume = progressList
                .filter { !it.isCompleted }
                .maxByOrNull { it.updatedAt }
                ?.let { prog -> rows.firstOrNull { it.chapter.url == prog.chapterUrl }?.chapter }
            _uiState.update { it.copy(chapters = rows, resumeChapter = resume) }
        }
    }

    /** Bir bölümün okuma durumunu UI'a taşıyan sarmalayıcı. */
    data class ChapterRow(
        val chapter: MangaChapter,
        val isCompleted: Boolean = false,
        val lastPageIndex: Int = 0,
        val totalPages: Int = 0,
        val isInProgress: Boolean = false
    )

    data class UiState(
        val details: MangaDetails,
        val isLoadingDetails: Boolean = false,
        val isLoadingChapters: Boolean = true,
        val chapters: List<ChapterRow> = emptyList(),
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
                            isInProgress = p != null && p.isCompleted.not() && p.lastPageIndex > 0
                        )
                    }

                    // "Devam Et" hedefi: en son güncellenen, tamamlanmamış bölüm.
                    val resume = progressList
                        .filter { !it.isCompleted }
                        .maxByOrNull { it.updatedAt }
                        ?.let { prog -> chapters.firstOrNull { it.url == prog.chapterUrl } }

                    _uiState.update {
                        it.copy(
                            isLoadingChapters = false,
                            chapters = rows,
                            resumeChapter = resume,
                            error = if (rows.isEmpty()) "Bu manga için bölüm bulunamadı." else null,
                            errorCode = null
                        )
                    }
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
     * Bir bölümü okundu / okunmadı olarak işaretler.
     *
     * Bölüm listesindeki tik'e dokunulduğunda çağrılır. Room'daki ilerleme satırı yoksa
     * oluşturulur, varsa `isCompleted` alanı güncellenir; okuma geçmişi de buna göre
     * temizlenir/yazılır (geçmiş sayfasındaki "Okunan" sekmesi ile tutarlı kalması için).
     */
    fun setChapterRead(chapter: MangaChapter, read: Boolean, totalPages: Int = 0) {
        viewModelScope.launch {
            val mangaUrl = _uiState.value.details.url
            val details = _uiState.value.details
            val existing = withContext(Dispatchers.IO) { progressDao.getProgress(chapter.url) }
            val pages = if (totalPages > 0) totalPages else existing?.totalPages ?: 0
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                progressDao.upsert(
                    com.kitsugi.animelist.data.local.MangaChapterProgressEntity(
                        chapterUrl = chapter.url,
                        mangaUrl = mangaUrl,
                        chapterName = chapter.name,
                        // Okundu işaretlenirse son sayfa = toplam sayfa (kaldığı yer kalmaz).
                        lastPageIndex = if (read) (pages - 1).coerceAtLeast(0) else 0,
                        totalPages = pages,
                        isCompleted = read,
                        updatedAt = now
                    )
                )
            }
            if (read) {
                com.kitsugi.animelist.data.local.MangaReadHistoryManager.record(
                    sourceName = source.name,
                    mangaUrl = mangaUrl,
                    mangaTitle = details.title,
                    thumbnailUrl = details.thumbnailUrl,
                    chapterUrl = chapter.url,
                    chapterName = chapter.name,
                    lastPageIndex = (pages - 1).coerceAtLeast(0),
                    totalPages = pages,
                    isCompleted = true
                )
            }
            _uiState.update { state ->
                state.copy(
                    chapters = state.chapters.map { row ->
                        if (row.chapter.url != chapter.url) {
                            row
                        } else {
                            row.copy(
                                isCompleted = read,
                                isInProgress = !read && row.lastPageIndex > 0,
                                lastPageIndex = if (read) (pages - 1).coerceAtLeast(0) else 0,
                                totalPages = pages
                            )
                        }
                    },
                    resumeChapter = if (read) {
                        // Tamamlanan bölüm "devam et" hedefi olamaz.
                        if (state.resumeChapter?.url == chapter.url) null else state.resumeChapter
                    } else {
                        state.resumeChapter
                    }
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
        // Bölüm listesi en yeniden eskiye sıralı geldiği için "baştan oku" = son eleman.
        return state.chapters.lastOrNull()?.chapter
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

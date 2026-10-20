package com.kitsugi.animelist.ui.screens.mylist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyListViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // ─── Sync mesajları için Channel ─────────────────────────────────────────
    // Screen, bu channel'dan mesajları toplayarak kullanıcıya gösterir.
    private val _syncMessages = Channel<String>(Channel.BUFFERED)
    val syncMessages = _syncMessages.receiveAsFlow()

    // ─── Repository & Flow ───────────────────────────────────────────────────
    // Repository hemen oluşturuluyor; entriesFlow her zaman non-null ve hazır.
    private val repository: MediaEntryRepository = run {
        val db = KitsugiDatabase.getDatabase(context)
        MediaEntryRepository(
            dao = db.mediaEntryDao(),
            pendingSyncDao = db.pendingSyncDao(),
            context = context,
            onExternalSyncMessage = { msg ->
                viewModelScope.launch { _syncMessages.send(msg) }
            }
        )
    }

    val entriesFlow: StateFlow<List<MediaEntry>> = repository.entriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Birleşik kütüphane ("Tümü" sekmesi) hesabının SON sonucu (UI önbelleği).
     *
     * PERF: gruplama artık arka planda hesaplanıp yayınlanıyor; detay sayfasına gidilip
     * dönüldüğünde ekran yeniden kompoze olurken sonuç hazır olsun ki liste bir-iki kare
     * boşalıp dolmasın. Girdi değişince MyListScreen hesabı yeniden koşturup bunu günceller;
     * eski veri en fazla bir kare görünür (kullanıcının son gördüğü listedir).
     */
    // internal: [CombinedMyListLibrary] internal bir tip; public üye internal tipi gösteremez.
    internal var cachedCombinedLibrary: CombinedMyListLibrary = CombinedMyListLibrary.EMPTY
        private set

    internal fun rememberCombinedLibrary(value: CombinedMyListLibrary) {
        cachedCombinedLibrary = value
    }

    init {
        // Bozuk Simkl subtitle'larını ve kaynak etiketlerini tek seferlik onar
        viewModelScope.launch {
            val dao = KitsugiDatabase.getDatabase(context).mediaEntryDao()
            repairBrokenSimklSubtitles(dao)
            repairMisclassifiedSources(dao)
            // Kitsu kimlik alanını kanonik stableId uzayına taşı (eski sürüm import'ları
            // bu alana gerçek MAL ID yazıyordu → ayrıntı sayfası alakasız veri açıyordu)
            KitsuIdentityMigration.runIfNeeded(context, dao)
            // +18 blur'unun TÜM kaynaklarda uygulanması için eksik yetişkin işaretlerini
            // kimlik üzerinden tamamla: Simkl/Shikimori/Bangumi liste API'leri `adult`
            // alanını taşımadığından satırlar isAdult=false yazılıyordu → Listem (Simkl)
            // ve "Tümü" sekmesinde afişler bulanıklanmıyordu.
            AdultFlagBackfillMigration.runIfNeeded(context, dao)
        }

        // Bangumi koleksiyonundan içe aktarılan satırlar YALNIZCA özgün (Japonca) adla gelir;
        // kullanıcı "Başlık Dili" olarak Romaji/İngilizce seçtiğinde liste CJK adla çiziliyordu.
        // Latin adları arka planda tamamla (önce önbellek, sonra subject infobox'ı / AniList).
        // Sonuç veritabanına yazıldığı için bir sonraki açılışta hiçbir iş kalmaz.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BangumiLibraryTitleHydrator.hydrate(KitsugiDatabase.getDatabase(context))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MyListViewModel", "Bangumi başlık tamamlama atlandı: ${error.message}")
            }
        }
    }

    /**
     * Eski Simkl importlarından kalan bozuk subtitle'ları düzeltir.
     * Örn: "movies.uppercase() â€¢ 2011" → "Film • 2011"
     */
    private suspend fun repairBrokenSimklSubtitles(
        dao: com.kitsugi.animelist.data.local.MediaEntryDao
    ) {
        val brokenPatterns = listOf("uppercase()", "â€¢", "â€")
        val all = dao.getAll()
        val broken = all.filter { entity ->
            entity.source == "simkl" &&
                brokenPatterns.any { entity.subtitle.contains(it) }
        }
        broken.forEach { entity ->
            val typeLabel = when (entity.type) {
                "Movie" -> "Film"
                "TvShow" -> "Dizi"
                "Anime" -> "Anime"
                else -> "Manga"
            }
            val fixedSubtitle = if (entity.year != null && entity.year > 0) {
                "$typeLabel • ${entity.year}"
            } else {
                typeLabel
            }
            dao.update(entity.copy(subtitle = fixedSubtitle))
        }
    }

    private suspend fun repairMisclassifiedSources(
        dao: com.kitsugi.animelist.data.local.MediaEntryDao
    ) {
        val isAniListConnected = ExternalAuthManager.getAniListToken(context) != null
        val isMalConnected = ExternalAuthManager.getMalToken(context) != null

        if (isAniListConnected && !isMalConnected) {
            val all = dao.getAll()
            val misclassified = all.filter { it.source == "jikan" || it.source == "mal" }
            misclassified.forEach { entity ->
                dao.update(entity.copy(source = "anilist"))
            }
        }
    }

    fun incrementEntryProgress(entry: MediaEntry) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        val nextProgress = if (entry.total != null) {
            (entry.progress + 1).coerceAtMost(entry.total)
        } else {
            entry.progress + 1
        }

        val nextStatus = if (entry.total != null && nextProgress >= entry.total) {
            WatchStatus.Completed
        } else if (entry.status == WatchStatus.Planned) {
            WatchStatus.Watching
        } else {
            entry.status
        }

        val autoStartDate = if (entry.status == WatchStatus.Planned && entry.startDate.isNullOrBlank()) {
            today
        } else {
            entry.startDate
        }

        val autoEndDate = if (nextStatus == WatchStatus.Completed && entry.endDate.isNullOrBlank()) {
            today
        } else {
            entry.endDate
        }

        val updatedEntry = entry.copy(
            progress = nextProgress,
            status = nextStatus,
            startDate = autoStartDate,
            endDate = autoEndDate
        )

        viewModelScope.launch {
            repository.update(updatedEntry)
        }
    }

    fun insertEntry(entry: MediaEntry) {
        viewModelScope.launch {
            repository.insert(entry)
        }
    }

    /**
     * Bangumi liste ekranından ekleme/düzenleme: diğer kaynaklarla aynı hat kullanılır —
     * kayıt önce yerele yazılır, sonra canlı eşitleme Bangumi dahil altı kaynağa birden
     * yayılır (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi).
     *
     * Eski akış önce Bangumi'ye yazıyor, başarısız olursa kaydı yerele bile kaydetmiyordu:
     * kullanıcının seçtiği durum/puan/tarih/bölüm kayboluyor ve hiçbir kaynağa gidilmiyordu.
     * Artık senkron sonucu repository geri çağrısıyla (_syncMessages) bildirilir; başarısız
     * yazımlar bekleyen senkron kuyruğunda yeniden denenir.
     */
    fun saveBangumiEntry(entry: MediaEntry, isNew: Boolean) {
        viewModelScope.launch {
            val persisted = entry.copy(source = BangumiIdNamespace.SOURCE)
            if (isNew) {
                repository.insert(persisted.copy(id = 0), syncExternal = true)
            } else {
                repository.update(persisted, syncExternal = true)
            }
        }
    }

    fun updateEntry(entry: MediaEntry, advancedScores: List<Double>? = null) {
        viewModelScope.launch {
            repository.update(entry, advancedScores = advancedScores)
        }
    }

    fun deleteEntryById(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}

package com.kitsugi.animelist.ui.screens.mylist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator
import com.kitsugi.animelist.data.remote.MalAxisLibraryTitleHydrator
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
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
     * Birleşik kütüphane ("Tümü") ve sekme bazlı kütüphane bölümlendirmesinin SON sonucu (UI önbelleği).
     *
     * PERF: gruplama ve kardeş başlık tamamlama arka planda hesaplanıp yayınlanıyor; detay sayfasına
     * gidilip dönüldüğünde ekran yeniden kompoze olurken sonuç hazır olsun ki liste bir-iki kare
     * boşalıp dolmasın.
     */
    internal var cachedLibrarySnapshot: MyListLibrarySnapshot = MyListLibrarySnapshot.EMPTY
        private set

    internal fun rememberLibrarySnapshot(value: MyListLibrarySnapshot) {
        cachedLibrarySnapshot = value
    }

    // Plain caches, not Compose state: retain the last complete search and prepared
    // documents across detail navigation without invalidating the screen per keystroke.
    internal var cachedPreparedSearch: PreparedMyListSearch? = null
    internal var cachedSearchResult: MyListSearchUiCache? = null

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
            // Eski/boş `addedAt` satırlarını eldeki tek zaman damgasıyla onar: aksi halde
            // MAL/Bangumi gibi kaynaklarda eklenme zamanı vermeyen satırlar Listem'in
            // "Eklenme Tarihi Bilinmiyor" kovasında birikip sıralamayı çorbaya çeviriyordu.
            repairMissingAddedAt(dao)
        }

        // Bangumi koleksiyonundan içe aktarılan satırlar YALNIZCA özgün (Japonca/Çince)
        // adla gelir; kullanıcı "Başlık Dili" olarak İng/Romaji seçtiğinde liste CJK adla
        // çiziliyordu. Latin adları arka planda tamamla (önce önbellek, sonra AniList /
        // subject infobox'ı). Sonuç veritabanına yazıldığı için bir sonraki açılışta iş kalmaz.
        // NOT: Bu tek başına yetmez — içe aktarım init'ten SONRA biterse yeni satırlar bu
        // taramayı kaçırır. Bu yüzden aktarım sonrası AuthViewModel ve başlık dili
        // değişiminde ekran da aynı taramayı tetikler (bkz. refreshBangumiTitles).
        refreshBangumiTitles()
    }

    /**
     * Bangumi satırlarının Latin/İngilizce ad tamamlama taramasını (yeniden) başlatır.
     *
     * @param titleLanguage güncel başlık dili; `null` ise ayardan okunur. Dil yalnızca
     *        İngilizce zenginleştirme kademesini etkiler — CJK gösterimi düzelten LATIN
     *        kademesi her dilde çalışır, böylece dil sonradan değişse bile veri hazırdır.
     *        Taramalar tamamlayıcıdaki kilitle sıraya girer; üst üste çağrı güvenlidir.
     */
    fun refreshBangumiTitles(titleLanguage: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = KitsugiDatabase.getDatabase(context)
                val language = titleLanguage
                    ?: runCatching { SettingsDataStore(context).settingsFlow.first().titleLanguage }
                        .getOrDefault("ENGLISH")
                BangumiLibraryTitleHydrator.hydrate(db, language)
                MalAxisLibraryTitleHydrator.hydrate(db)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MyListViewModel", "Kütüphane başlık tamamlama atlandı: ${error.message}")
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

    /**
     * `addedAt` alanı boş kalmış satırları onarır (tek yerden, tek yazımla).
     *
     * Neden gerekli: "Son eklenen" sıralaması ve dönem başlıkları `addedAt`e bakar.
     * Alan v30 göçünde dolduruldu, ama o göçten sonra farklı yazma yollarından
     * (yedek geri yükleme, çapraz eşitlemenin kardeş satır aynası, çok eski
     * sürümlerden kalan 0 damgalı satırlar) geçen kayıtlar 0 kalabiliyor. Kaynak
     * eklenme zamanı vermiyorsa (MAL, Bangumi) elimizdeki tek zaman damgası
     * `updatedAt`'tir; bu, gerçek eklenme anının bir yaklaşığıdır ama satırın
     * "bilinmiyor" kovasına düşüp sıralamayı bozmasından iyidir.
     *
     * Yalnızca `addedAt = 0` + `updatedAt > 0` satırlara dokunur, topluca tek
     * `updateAll` çağrısı yapar ve düzeltilecek satır yoksa HİÇ yazmaz (Room
     * invalidation / gereksiz yeniden hesaplama olmasın).
     */
    private suspend fun repairMissingAddedAt(
        dao: com.kitsugi.animelist.data.local.MediaEntryDao
    ) {
        val rows = runCatching { dao.getAll() }.getOrNull() ?: return
        if (rows.isEmpty()) return
        val repaired = rows.mapNotNull { row ->
            if (row.addedAt > 0L) return@mapNotNull null
            // Bangumi milisaniye yazar; normalize edilmeden `addedAt`e kopyalanırsa
            // satır ×1000 ile her zaman en üste çıkardı.
            val fallback = com.kitsugi.animelist.model.normalizeToEpochSeconds(row.updatedAt)
            if (fallback <= 0L) null else row.copy(addedAt = fallback)
        }
        if (repaired.isEmpty()) return
        runCatching { dao.updateAll(repaired) }
            .onFailure { android.util.Log.w("MyListViewModel", "addedAt onarımı atlandı: ${it.message}") }
    }

    private suspend fun repairMisclassifiedSources(
        dao: com.kitsugi.animelist.data.local.MediaEntryDao
    ) {
        val isAniListConnected = ExternalAuthManager.getAniListToken(context) != null
        val isMalConnected = ExternalAuthManager.getMalToken(context) != null

        if (isAniListConnected && !isMalConnected) {
            val all = dao.getAll()
            val ownedAniListIds = all.asSequence()
                .filter { it.source.equals("anilist", ignoreCase = true) }
                .mapNotNull { it.aniListEntryId?.takeIf { id -> id > 0 } }
                .toHashSet()
            val misclassified = all.filter { entity ->
                (entity.source == "jikan" || entity.source == "mal") &&
                    entity.malListId == null &&
                    entity.aniListEntryId != null &&
                    entity.aniListEntryId > 0 &&
                    entity.aniListEntryId !in ownedAniListIds
            }
            misclassified.forEach { entity ->
                dao.update(entity.copy(source = "anilist"))
            }
        }
    }

    fun incrementEntryProgress(entry: MediaEntry) {
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

        // Ortak kural motoru (EntryAutoRules): "İzlemeye başla" anında başlangıç tarihi
        // bugüne dolar, son bölüme gelindiğinde bitiş tarihi yazılır. Listem ekranındaki
        // +1 kısayolu da artık düzenleyiciyle birebir aynı davranıyor.
        val auto = EntryAutoRules.resolveOnStatusChange(
            status = nextStatus,
            startDate = entry.startDate,
            endDate = entry.endDate,
            progress = nextProgress,
            total = entry.total,
            statusChanged = nextStatus != entry.status
        )

        val updatedEntry = entry.copy(
            progress = auto.progress,
            status = nextStatus,
            startDate = auto.startDate,
            endDate = auto.endDate
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

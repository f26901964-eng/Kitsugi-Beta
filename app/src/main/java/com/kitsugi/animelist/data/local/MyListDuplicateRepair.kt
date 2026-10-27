package com.kitsugi.animelist.data.local

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * LİSTEM ÇİFT KAYIT ONARIMI — aynı kaynağın aynı yapımı iki kez tutan satırlarını birleştirir.
 *
 * ## Neden var
 * `MediaEntryRepository.smartImport` içe aktarımı yazarken mevcut satırı bulamazsa YENİ satır
 * ekler. Aşağıdaki durumlarda eşleştirme başarısız oluyor ve kitaplıkta kopya birikiyordu:
 *
 *  1. **Simkl kategori çakışması** — aynı anime filmi `movies` ve `anime` uçlarının ikisinde
 *     döndüğü için satır `Movie` + `Anime` olarak iki kez yazılıyordu; `sameMedia` tür eşitliği
 *     istediğinden ikinci satır "yeni kayıt" sayılıyordu. Sonuç: Listem → Simkl sekmesinde ve
 *     "Tümü"nde aynı film iki kart (ekran görüntüleri: Demon Slayer · Kaiju No. 8 · Attack on Titan).
 *  2. **Eşzamanlı yenileme** — açılışta arka plan liste yenilemesi (`replaceSource...`), içe
 *     aktarım (`smartImport`) ve eşitleme yazımları aynı kaynağa paralel dokunabiliyor.
 *  3. **Eski sürümler** — kimlik sözleşmesi düzeltmelerinden önce yazılmış kopyalar.
 *
 * ## Kural
 * Yalnızca **kanıtlanmış** kopyalar birleştirilir: aynı kaynak + (aynı türde kesişen kimlik ya
 * da tür etiketi farklıysa `simklId`/`subject_id` kanıtı). **Başlık benzerliği asla yetmez** —
 * bu onarım veri SİLDİĞİ için yanlış birleşme kabul edilemez.
 *
 * Kullanıcı verisi kaybolmaz: birleşen satırların ilerlemesi (maks.), durumu (en ileri),
 * puanı, favorisi, +18 işareti, notları ve kimlik alanları tek satırda toplanır.
 */
internal object MyListDuplicateRepair {

    private const val TAG = "MyListDuplicateRepair"
    private const val PREFS = "Kitsugi_list_filters"
    private const val KEY_DONE_VERSION = "mylist_duplicate_repair_version"
    internal const val MIGRATION_VERSION = 1

    /** Birleştirme sonucu: [keeper] korunacak satır, [removed] silinecek kopyalar. */
    data class MergePlan(
        val keeper: MediaEntry,
        val removed: List<MediaEntry>
    )

    /**
     * Hiçbir şey değişmediyse boş liste. Saf fonksiyon — birim testleriyle doğrulanır.
     *
     * PERF: kimlik anahtarları satır başına BİR kez hesaplanır ve karşılaştırma yalnızca
     * **aynı kaynağın** satırları arasında yapılır (kaynak başına ayrı kova). Aksi halde
     * 800 satırlık kitaplıkta ~320.000 gereksiz kimlik hesabı yapılıyordu.
     */
    internal fun plan(entries: List<MediaEntry>): List<MergePlan> {
        if (entries.size < 2) return emptyList()
        val groupsBySource = mutableMapOf<String, MutableList<MutableList<Pair<MediaEntry, Set<String>>>>>()
        entries.forEach { entry ->
            val source = MediaIdentity.canonicalSource(entry.source)
            val keys = MediaIdentity.keys(entry)
            val groups = groupsBySource.getOrPut(source) { mutableListOf() }
            val existingGroup = groups.firstOrNull { members ->
                members.any { (member, memberKeys) ->
                    MediaIdentity.sameProduction(
                        a = member,
                        b = entry,
                        keysA = memberKeys,
                        keysB = keys,
                        allowTitle = false
                    )
                }
            }
            if (existingGroup != null) existingGroup.add(entry to keys) else groups.add(mutableListOf(entry to keys))
        }
        return groupsBySource.values.flatten().mapNotNull { members ->
            if (members.size < 2) return@mapNotNull null
            val rows = members.map { it.first }
            val keeper = rows.maxWithOrNull(keeperComparator) ?: rows.first()
            val merged = rows.filter { it.id != keeper.id }.fold(keeper) { acc, copy -> merge(acc, copy) }
            MergePlan(keeper = merged, removed = rows.filter { it.id != keeper.id })
        }
    }

    /** En çok kullanıcı verisi taşıyan + en yeni satır korunur. */
    private val keeperComparator: Comparator<MediaEntry> =
        compareBy<MediaEntry> { it.updatedAt }.thenBy { richness(it) }.thenBy { it.id }

    private fun richness(entry: MediaEntry): Int {
        var score = 0
        if (entry.progress > 0) score += 2
        if (entry.status != WatchStatus.Planned) score += 2
        if ((entry.score ?: 0) > 0) score += 1
        if (!entry.notes.isNullOrBlank()) score += 1
        if (!entry.imageUrl.isNullOrBlank()) score += 1
        if (entry.startDate != null) score += 1
        return score
    }

    /**
     * Aynı kaynağın, kimlikle kanıtlanmış aynı yapımı mı?
     * `allowTitle = false` — onarım başlık benzerliğine dayanmaz.
     */
    internal fun isSameStoredRecord(a: MediaEntry, b: MediaEntry): Boolean {
        if (MediaIdentity.canonicalSource(a.source) != MediaIdentity.canonicalSource(b.source)) return false
        return MediaIdentity.sameProduction(a, b, allowTitle = false)
    }

    /** İki kopyanın kullanıcı verisini tek satırda birleştirir (hiçbir alan gerilemez). */
    internal fun merge(keeper: MediaEntry, copy: MediaEntry): MediaEntry = keeper.copy(
        // Simkl movie ↔ anime kopyasında anime etiketi tercih edilir.
        type = if (MediaIdentity.preferredTypeRank(copy.type) <
            MediaIdentity.preferredTypeRank(keeper.type)
        ) copy.type else keeper.type,
        title = keeper.title.ifBlank { copy.title },
        titleEnglish = keeper.titleEnglish?.takeIf { it.isNotBlank() } ?: copy.titleEnglish,
        titleJapanese = keeper.titleJapanese?.takeIf { it.isNotBlank() } ?: copy.titleJapanese,
        subtitle = keeper.subtitle.ifBlank { copy.subtitle },
        status = moreAdvancedStatus(keeper.status, copy.status),
        progress = maxOf(keeper.progress, copy.progress),
        volumeProgress = maxOf(keeper.volumeProgress, copy.volumeProgress),
        total = keeper.total ?: copy.total,
        score = keeper.score ?: copy.score,
        isFavorite = keeper.isFavorite || copy.isFavorite,
        isAdult = keeper.isAdult || copy.isAdult,
        isPrivate = keeper.isPrivate || copy.isPrivate,
        startDate = keeper.startDate ?: copy.startDate,
        endDate = keeper.endDate ?: copy.endDate,
        notes = keeper.notes?.takeIf { it.isNotBlank() } ?: copy.notes,
        tags = keeper.tags?.takeIf { it.isNotBlank() } ?: copy.tags,
        priority = keeper.priority ?: copy.priority,
        isRepeating = keeper.isRepeating || copy.isRepeating,
        repeatCount = maxOf(keeper.repeatCount, copy.repeatCount),
        repeatValue = maxOf(keeper.repeatValue, copy.repeatValue),
        imageUrl = keeper.imageUrl?.takeIf { it.isNotBlank() } ?: copy.imageUrl,
        synopsis = keeper.synopsis?.takeIf { it.isNotBlank() } ?: copy.synopsis,
        year = keeper.year ?: copy.year,
        malId = keeper.malId ?: copy.malId,
        aniListEntryId = keeper.aniListEntryId ?: copy.aniListEntryId,
        malListId = keeper.malListId ?: copy.malListId,
        tmdbId = keeper.tmdbId ?: copy.tmdbId,
        simklId = keeper.simklId ?: copy.simklId,
        updatedAt = maxOf(keeper.updatedAt, copy.updatedAt)
    )

    private fun moreAdvancedStatus(a: WatchStatus, b: WatchStatus): WatchStatus =
        if (statusRank(b) > statusRank(a)) b else a

    private fun statusRank(status: WatchStatus): Int = when (status) {
        WatchStatus.Completed -> 5
        WatchStatus.Repeating -> 4
        WatchStatus.Watching -> 3
        WatchStatus.Paused -> 2
        WatchStatus.Dropped -> 1
        WatchStatus.Planned -> 0
    }

    /**
     * Onarımı bir kez çalıştırır. Sürüm yükseltildiğinde yeniden koşar; hata durumunda sürüm
     * yazılmaz (bir sonraki açılışta tekrar denenir).
     *
     * @return birleştirilen/silinen satır sayısı
     */
    suspend fun runIfNeeded(
        context: Context,
        dao: MediaEntryDao,
        pendingSyncDao: PendingSyncDao? = null
    ): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DONE_VERSION, 0) >= MIGRATION_VERSION) return 0
        return try {
            val removed = run(context, dao, pendingSyncDao)
            prefs.edit().putInt(KEY_DONE_VERSION, MIGRATION_VERSION).apply()
            removed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Çift kayıt onarımı atlandı: ${error.message}")
            0
        }
    }

    /**
     * Onarımı koşulsuz çalıştırır (içe aktarım sonrası elle tetikleme için).
     *
     * Planlama CPU işidir; çağıran `viewModelScope` (Main) olsa bile UI kilitlenmesin diye
     * [kotlinx.coroutines.Dispatchers.Default] üzerinde hesaplanır.
     */
    suspend fun run(
        context: Context,
        dao: MediaEntryDao,
        pendingSyncDao: PendingSyncDao? = null
    ): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val plans = plan(dao.getAll().map { it.toDomain() })
        if (plans.isEmpty()) return 0
        var removedCount = 0
        plans.forEach { plan ->
            val removedIds = plan.removed.map { it.id }
            // Aynı kaynağa eşzamanlı içe aktarım/yenileme yazmasın: onarım sırasında gelen
            // bir liste, az önce sildiğimiz kopyayı yeniden ekleyebilirdi.
            LibraryWriteLocks.forSource(plan.keeper.source).withLock {
                // Silinen satırların bekleyen kuyruk işleri düşürülür: aksi halde drain'de
                // bayat bir UPDATE, az önce birleştirdiğimiz kopyayı geri diriltirdi.
                clearPendingJobs(pendingSyncDao, removedIds)
                dao.update(plan.keeper.toEntity())
                dao.deleteByIds(removedIds)
            }
            removedCount += removedIds.size
            Log.i(TAG, "Çift kayıt birleştirildi: '${plan.keeper.title}' (${removedIds.size} kopya)")
        }
        return removedCount
    }

    private suspend fun clearPendingJobs(pendingSyncDao: PendingSyncDao?, entryIds: List<Int>) {
        if (pendingSyncDao == null || entryIds.isEmpty()) return
        runCatching {
            pendingSyncDao.getAll().forEach { job ->
                val matches = runCatching {
                    val json = org.json.JSONObject(job.entryJson)
                    json.optInt("id", -1) in entryIds
                }.getOrDefault(false)
                if (matches) pendingSyncDao.deleteById(job.id)
            }
        }
    }
}

package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.data.auth.SyncField
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Bekleyen (başarısız) sync işlemlerini kuyruğa yazar ve boşaltır.
 *
 * Akış:
 *  1. Sync başarısız → [enqueue] ile kuyruğa yaz
 *  2. Uygulama açılışı / her başarılı işlem sonrası → [drain] çağır
 *  3. [drain]: kuyruktaki her işlemi dene, başarılıysa sil, değilse retry++ yap
 *  4. retryCount >= MAX_RETRIES olanları silmeden beklet
 */
object PendingSyncDrainer {

    private const val MAX_RETRIES = 5
    private val drainMutex = Mutex()

    // ────────────────────────────────────────────────
    // Kuyruğa Ekleme
    // ────────────────────────────────────────────────

    /**
     * @param fields kuyruğa alınan işin taşıdığı ALANLAR. Yeniden denemede yalnızca bu alanlar
     * yazılır; aksi hâlde çevrimdışıyken kuyruğa giren bir "ilerleme" işi, bağlantı gelince
     * satırın TAMAMINI yazıp hedeflerdeki puan/not/tarihi ezebilirdi.
     */
    suspend fun enqueue(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry,
        fields: Set<SyncField> = SyncField.ALL
    ) = withContext(Dispatchers.IO) {
        drainMutex.withLock {
            // Aynı satır için bekleyen işler bulunur. Yeni iş eskileri EZER (satır en güncel
            // hâliyle yazılır) — ama ALAN KÜMELERİ BİRLEŞTİRİLİR: aksi hâlde çevrimdışıyken
            // önce "ilerleme", sonra "not" değiştirilirse ikinci iş birincisini siler ve
            // ilerleme güncellemesi sessizce kaybolurdu.
            var mergedFields = fields
            val older = dao.getAll().filter { pending ->
                runSyncCatching {
                    val previous = JSONObject(pending.entryJson)
                    val sameRow = entry.id > 0 && previous.optInt("id", 0) == entry.id &&
                        previous.optString("source") == entry.source &&
                        previous.optString("type") == entry.type.name
                    if (sameRow) mergedFields = mergedFields + previous.readFields()
                    sameRow
                }.getOrDefault(false)
            }
            // Insert first: a failed insert must not destroy the previous recoverable job.
            dao.insert(PendingSyncEntity(operation = operation, entryJson = entry.toJson(mergedFields)))
            older.forEach { dao.deleteById(it.id) }
        }
    }

    // ────────────────────────────────────────────────
    // Kuyruğu Boşalt
    // ────────────────────────────────────────────────

    /**
     * Kuyruktaki tüm bekleyen işlemleri dener.
     * Başarılı olanları siler, başarısız olanların retryCount'unu artırır.
     * MAX_RETRIES'a ulaşan kayıtlar oturum içinde silmeden bekletilir;
     * [freshBudget] verildiğinde (bağlantı geri geldiğinde) sayaçlar sıfırlanır ve
     * işler yeniden tam deneme hakkına kavuşur — kullanıcı niyeti asla donmaz.
     *
     * @return Başarıyla gönderilen işlem sayısı
     */
    suspend fun drain(
        context: Context,
        dao: PendingSyncDao,
        freshBudget: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        // Exhausted jobs remain inspectable/recoverable; never silently delete user intent.
        if (!drainMutex.tryLock()) return@withContext 0
        try {
        if (freshBudget) dao.resetRetries()
        val pending = dao.getAll().filter { it.retryCount < MAX_RETRIES }
        if (pending.isEmpty()) return@withContext 0

        var successCount = 0

        for (item in pending) {
            val decoded = runSyncCatching { item.entryJson.decodeJob() }.getOrNull()
            val entry = decoded?.first
            if (entry == null) {
                // Preserve malformed jobs for recovery rather than destroying them.
                dao.incrementRetry(item.id)
                continue
            }

            val result = runSyncCatching {
                val fields = decoded?.second ?: SyncField.ALL
                when (item.operation) {
                    "UPDATE" -> ExternalListSyncManager.syncEntry(context, entry, fields = fields)
                    "DELETE" -> ExternalListSyncManager.deleteEntry(context, entry)
                    else -> null
                }
            }

            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val syncResult = result.getOrNull()
            // Yalnızca GERÇEK hata (ağ/HTTP) yeniden denemeyi haklı çıkarır. "Kimlik çözülemedi"
            // ya da "değişiklik yok" gibi sonuçlar hatasız olduğu için iş kuyruktan düşer;
            // aksi hâlde bu işler 5 kez denenip sonsuza kadar kuyrukta kalıyordu.
            val success = result.isSuccess && syncResult != null && !syncResult.needsRetry

            if (success) {
                dao.deleteById(item.id)
                successCount++
            } else {
                val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
                val isAuthError = allErrors.contains("401") || allErrors.contains("token geçersiz") || allErrors.contains("Unauthorized")
                if (!isAuthError) {
                    dao.incrementRetry(item.id)
                }
            }
        }

        successCount
        } finally {
            drainMutex.unlock()
        }
    }

    // ────────────────────────────────────────────────
    // JSON Serializasyon / Deserializasyon
    // ────────────────────────────────────────────────

    /** İşin kendi alan kümesi JSON'a `_syncFields` olarak gömülür (şema değişikliği gerekmez). */
    private fun MediaEntry.toJson(fields: Set<SyncField>): String {
        val names = org.json.JSONArray()
        fields.forEach { names.put(it.name) }
        return JSONObject(MediaEntryBackup.exportToJson(listOf(this)))
            .getJSONArray("entries").getJSONObject(0)
            .put("id", id)
            .put(KEY_FIELDS, names)
            .toString()
    }

    /**
     * Kuyruk kaydını (satır, alanlar) çiftine çevirir. `_syncFields` yoksa (eski kayıtlar)
     * tam yazım kabul edilir.
     */
    private fun String.decodeJob(): Pair<MediaEntry, Set<SyncField>> {
        val item = JSONObject(this)
        val fields = item.optJSONArray(KEY_FIELDS)?.let { names ->
            (0 until names.length()).mapNotNull { index ->
                runSyncCatching { SyncField.valueOf(names.getString(index)) }.getOrNull()
            }.toSet()
        }?.takeIf { it.isNotEmpty() } ?: SyncField.ALL
        item.remove(KEY_FIELDS)
        val backup = JSONObject().put("schemaVersion", 2)
            .put("entries", org.json.JSONArray().put(item))
        val entry = MediaEntryBackup.importFromJson(backup.toString()).single()
            .copy(id = item.optInt("id", 0))
        return entry to fields
    }

    /** Kayıttaki alan kümesini okur (yoksa tam yazım). */
    private fun JSONObject.readFields(): Set<SyncField> = optJSONArray(KEY_FIELDS)?.let { names ->
        (0 until names.length()).mapNotNull { index ->
            runSyncCatching { SyncField.valueOf(names.getString(index)) }.getOrNull()
        }.toSet()
    }?.takeIf { it.isNotEmpty() } ?: SyncField.ALL

    private const val KEY_FIELDS = "_syncFields"
}

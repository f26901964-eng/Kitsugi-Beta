package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
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

    suspend fun enqueue(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry
    ) = withContext(Dispatchers.IO) {
        drainMutex.withLock {
            val all = dao.getAll()
            val older = all.filter { pending ->
                runSyncCatching {
                    val previous = JSONObject(pending.entryJson)
                    entry.id > 0 && previous.optInt("id", 0) == entry.id &&
                        previous.optString("source") == entry.source && previous.optString("type") == entry.type.name
                }.getOrDefault(false)
            }
            // Insert first: a failed insert must not destroy the previous recoverable job.
            dao.insert(PendingSyncEntity(operation = operation, entryJson = entry.toJson()))
            older.forEach { dao.deleteById(it.id) }

            // ── "Sildim, sonra tekrar ekledim" koruması ────────────────────────────
            // Yerel silme satırı kaldırdığı için yeniden eklenen kayıt YENİ bir yerel id
            // alır; kuyrukta kalan eski DELETE işi yukarıdaki id eşleşmesine takılmaz ve
            // bir sonraki boşaltmada yeni kaydı uzaktan SİLERDİ (kullanıcı: "tekrar
            // ekledim ama yine silindi"). UPDATE kuyruğa girerken aynı yapımı hedefleyen
            // bekleyen DELETE işleri düşürülür.
            if (operation.equals("UPDATE", ignoreCase = true)) {
                all.filter { it.operation.equals("DELETE", ignoreCase = true) }
                    .filter { pending ->
                        runSyncCatching { sameProduction(JSONObject(pending.entryJson), entry) }
                            .getOrDefault(false)
                    }
                    .forEach { dao.deleteById(it.id) }
            }
        }
    }

    /**
     * İki kuyruk kaydı **aynı yapımı** mı gösteriyor?
     *
     * Yalnızca kanıt sayılan alanlara bakılır: tür + ortak dolu sağlayıcı kimliği; kimlik
     * yoksa (nadiren) aynı normalize başlık + aynı yıl. Başlık benzerliği tek başına
     * yetmez — aynı adı taşıyan remake'ler birbirinin işini silmemeli.
     */
    private fun sameProduction(previous: JSONObject, entry: MediaEntry): Boolean {
        if (previous.optString("type") != entry.type.name) return false
        val prevMalId = previous.optInt("malId", 0).takeIf { it > 0 }
        val prevSimklId = previous.optInt("simklId", 0).takeIf { it > 0 }
        val prevTmdbId = previous.optInt("tmdbId", 0).takeIf { it > 0 }
        if (prevMalId != null && prevMalId == entry.malId) return true
        if (prevSimklId != null && prevSimklId == entry.simklId) return true
        if (prevTmdbId != null && prevTmdbId == entry.tmdbId) return true
        val prevYear = previous.optInt("year", 0).takeIf { it > 0 }
        if (prevYear != null && entry.year != null && prevYear != entry.year) return false
        val prevTitle = com.kitsugi.animelist.model.MediaIdentity
            .normalizedTitle(previous.optString("title"))
        val entryTitle = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(entry.title)
        return prevTitle.isNotBlank() && prevTitle == entryTitle
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
            val entry = runSyncCatching { item.entryJson.toMediaEntry() }.getOrNull()
            if (entry == null) {
                // Preserve malformed jobs for recovery rather than destroying them.
                dao.incrementRetry(item.id)
                continue
            }

            val result = runSyncCatching {
                when (item.operation) {
                    "UPDATE" -> ExternalListSyncManager.syncEntry(context, entry)
                    "DELETE" -> ExternalListSyncManager.deleteEntry(context, entry)
                    else -> null
                }
            }

            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val syncResult = result.getOrNull()
            // Yalnızca gerçek hata yeniden denemeyi haklı çıkarır; "kimlik çözülemedi"
            // (atlandı) durumları kuyrukta sonsuza kadar dönmez.
            val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty() &&
                (syncResult.messages.isNotEmpty() || syncResult.skipped.isNotEmpty())

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

    private fun MediaEntry.toJson(): String = JSONObject(MediaEntryBackup.exportToJson(listOf(this)))
        .getJSONArray("entries").getJSONObject(0).put("id", id).toString()

    private fun String.toMediaEntry(): MediaEntry {
        val item = JSONObject(this)
        val backup = JSONObject().put("schemaVersion", 2)
            .put("entries", org.json.JSONArray().put(item))
        return MediaEntryBackup.importFromJson(backup.toString()).single().copy(id = item.optInt("id", 0))
    }
}

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
    private const val TMDB_FIELDS_KEY = "_tmdbWriteFields"
    private const val TMDB_ACCOUNT_ID_KEY = "_tmdbAccountId"
    private val drainMutex = Mutex()

    // ────────────────────────────────────────────────
    // Kuyruğa Ekleme
    // ────────────────────────────────────────────────

    suspend fun enqueue(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry,
        tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields = com.kitsugi.animelist.data.auth.TmdbWriteFields.All,
        expectedTmdbAccountId: Int? = null
    ) = withContext(Dispatchers.IO) {
        drainMutex.withLock {
            val older = dao.getAll().filter { pending ->
                runSyncCatching {
                    val previous = JSONObject(pending.entryJson)
                    entry.id > 0 && previous.optInt("id", 0) == entry.id &&
                        previous.optString("source") == entry.source && previous.optString("type") == entry.type.name
                }.getOrDefault(false)
            }
            // Insert first: a failed insert must not destroy the previous recoverable job.
            dao.insert(PendingSyncEntity(operation = operation, entryJson = entry.toJson(tmdbFields, expectedTmdbAccountId)))
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
            val entry = runSyncCatching { item.entryJson.toMediaEntry() }.getOrNull()
            val tmdbFields = if (item.operation == "UPDATE") {
                runSyncCatching {
                    val json = JSONObject(item.entryJson)
                    com.kitsugi.animelist.data.auth.TmdbWriteFields.fromToken(
                        if (json.has(TMDB_FIELDS_KEY)) json.optString(TMDB_FIELDS_KEY) else null
                    )
                }.getOrNull()
            } else {
                com.kitsugi.animelist.data.auth.TmdbWriteFields.All
            }
            val expectedTmdbAccountId = runSyncCatching {
                val json = JSONObject(item.entryJson)
                if (json.has(TMDB_ACCOUNT_ID_KEY)) json.optInt(TMDB_ACCOUNT_ID_KEY, 0) else null
            }.getOrNull()
            if (entry == null || tmdbFields == null) {
                // Preserve malformed jobs for recovery rather than destroying them.
                dao.incrementRetry(item.id)
                continue
            }

            val result = runSyncCatching {
                when (item.operation) {
                    "UPDATE" -> ExternalListSyncManager.syncEntry(
                        context, entry, tmdbFields = tmdbFields, expectedTmdbAccountId = expectedTmdbAccountId
                    )
                    "DELETE" -> ExternalListSyncManager.deleteEntry(
                        context, entry, expectedTmdbAccountId = expectedTmdbAccountId
                    )
                    else -> null
                }
            }

            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val syncResult = result.getOrNull()
            // Yalnızca gerçek hata yeniden denemeyi haklı çıkarır; "kimlik çözülemedi"
            // (atlandı) durumları kuyrukta sonsuza kadar dönmez.
            val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty() &&
                (syncResult.messages.isNotEmpty() || syncResult.skipped.isNotEmpty() ||
                    syncResult.outcomes.containsValue(ExternalListSyncManager.OUTCOME_NO_NATIVE_CHANGES))

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

    private fun MediaEntry.toJson(
        tmdbFields: com.kitsugi.animelist.data.auth.TmdbWriteFields,
        expectedTmdbAccountId: Int?
    ): String = JSONObject(MediaEntryBackup.exportToJson(listOf(this)))
        .getJSONArray("entries").getJSONObject(0)
        .put("id", id)
        .put(TMDB_FIELDS_KEY, tmdbFields.toToken())
        .apply {
            if (expectedTmdbAccountId != null) put(TMDB_ACCOUNT_ID_KEY, expectedTmdbAccountId)
        }
        .toString()

    private fun String.toMediaEntry(): MediaEntry {
        val item = JSONObject(this)
        val backup = JSONObject().put("schemaVersion", 2)
            .put("entries", org.json.JSONArray().put(item))
        return MediaEntryBackup.importFromJson(backup.toString()).single().copy(id = item.optInt("id", 0))
    }
}

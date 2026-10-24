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
 *
 * Karar kuralları (hangi iş düşer, hangi sayaç artar, hangi alanlar birleşir/düşülür)
 * SAF olarak [PendingSyncPolicy] içindedir ve birim testleriyle sabitlenmiştir (T02);
 * bu nesne yalnızca JSON/Room/ağ kablolamasını yapar.
 */
object PendingSyncDrainer {

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
            val all = dao.getAll()
            val older = mutableListOf<PendingSyncEntity>()
            val olderFieldSets = mutableListOf<Set<SyncField>>()
            all.forEach { pending ->
                val sameRow = runSyncCatching {
                    val previous = JSONObject(pending.entryJson)
                    PendingSyncPolicy.sameRow(
                        entryId = entry.id,
                        entrySource = entry.source,
                        entryTypeName = entry.type.name,
                        queuedId = previous.optInt("id", 0),
                        queuedSource = previous.optString("source"),
                        queuedTypeName = previous.optString("type")
                    ).also { matched ->
                        if (matched) olderFieldSets += previous.readFields()
                    }
                }.getOrDefault(false)
                if (sameRow) older += pending
            }
            val mergedFields = PendingSyncPolicy.mergedFields(fields, olderFieldSets)
            // Insert first: a failed insert must not destroy the previous recoverable job.
            dao.insert(PendingSyncEntity(operation = operation, entryJson = entry.toJson(mergedFields)))
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
     * Doğrudan (çevrimiçi) yazım başarılı olduktan sonra aynı satırın bekleyen işlerinden
     * **yalnızca az önce gönderildiği bilinen alanları** düşer (E73).
     *
     * Eski davranış işi bütün olarak siliyordu: çevrimdışıyken kuyruğa giren bir not
     * değişikliği (`{NOTES}`), çevrimiçiyken yalnız ilerleme (`{PROGRESS}`) içeren
     * başarılı bir düzenlemeden sonra siliniyor ve not hedeflere hiç gitmiyordu.
     * Artık alan kümesi küçültülür; küme boşalırsa iş silinir, kalırsa işin JSON'ındaki
     * `_syncFields` geri kalan alanlarla güncellenir. Anlık görüntü olduğu gibi kalır:
     * geri kalan alanların değerleri arada değişseydi o değişiklik ya doğrudan
     * gönderilmiş (ve buradan düşülmüş) ya da yeni bir [enqueue] ile görüntüyü
     * tazelemiş olurdu.
     *
     * `DELETE` için çağıran [SyncField.ALL] geçirir: başarılı bir doğrudan silmenin
     * ardından bekleyen `DELETE` işi bütünüyle gereksizdir.
     */
    suspend fun removeSyncedFields(
        dao: PendingSyncDao,
        operation: String,
        entry: MediaEntry,
        syncedFields: Set<SyncField>
    ) = withContext(Dispatchers.IO) {
        if (syncedFields.isEmpty()) return@withContext
        drainMutex.withLock {
            dao.getAll().filter { it.operation == operation }.forEach { pending ->
                runSyncCatching {
                    val json = JSONObject(pending.entryJson)
                    val sameRow = PendingSyncPolicy.sameRow(
                        entryId = entry.id,
                        entrySource = entry.source,
                        entryTypeName = entry.type.name,
                        queuedId = json.optInt("id", 0),
                        queuedSource = json.optString("source"),
                        queuedTypeName = json.optString("type")
                    )
                    if (sameRow) {
                        val queuedFields = json.readFields()
                        val remaining = PendingSyncPolicy.remainingFields(queuedFields, syncedFields)
                        when {
                            remaining.isEmpty() -> dao.deleteById(pending.id)
                            remaining != queuedFields -> {
                                val names = org.json.JSONArray()
                                remaining.forEach { field -> names.put(field.name) }
                                // REPLACE ile aynı id'ye yazılır: createdAt / retryCount korunur.
                                dao.insert(
                                    pending.copy(entryJson = json.put(KEY_FIELDS, names).toString())
                                )
                            }
                        }
                    }
                }
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
    ): Int = drain(dao, freshBudget) { operation, entry, fields ->
        when (operation) {
            "UPDATE" -> ExternalListSyncManager.syncEntry(context, entry, fields = fields)
            "DELETE" -> ExternalListSyncManager.deleteEntry(context, entry)
            else -> null
        }
    }

    /**
     * Boşaltma döngüsünün çalıştırıcıdan ayrılmış çekirdeği (T02): `Context` ve ağ
     * yazıcıları olmadan, sahte DAO + sahte çalıştırıcıyla JVM birim testinde koşulur.
     *
     * [execute] tanınmayan işlemler için ÇAĞRILMAZ (sonuç `null` sayılır ve iş bütçe
     * yakarak korunur — bilinmeyen iş sessizce silinmez). `CancellationException`
     * asla bir "başarısız deneme" sayılmaz: `runSyncCatching` onu yeniden fırlatır ve
     * boşaltma bütçe yakmadan yarıda kalır (kilit `finally` ile bırakılır).
     */
    internal suspend fun drain(
        dao: PendingSyncDao,
        freshBudget: Boolean = false,
        execute: suspend (
            operation: String,
            entry: MediaEntry,
            fields: Set<SyncField>
        ) -> ExternalListSyncManager.SyncResult?
    ): Int = withContext(Dispatchers.IO) {
        // Exhausted jobs remain inspectable/recoverable; never silently delete user intent.
        if (!drainMutex.tryLock()) return@withContext 0
        try {
        if (freshBudget) dao.resetRetries()
        val pending = dao.getAll().filter { PendingSyncPolicy.isEligible(it.retryCount) }
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
                    "UPDATE", "DELETE" -> execute(item.operation, entry, fields)
                    else -> null
                }
            }

            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val syncResult = result.getOrNull()
            // Yalnızca GERÇEK hata (ağ/HTTP) yeniden denemeyi haklı çıkarır. "Kimlik çözülemedi"
            // ya da "değişiklik yok" gibi sonuçlar hatasız olduğu için iş kuyruktan düşer;
            // aksi hâlde bu işler 5 kez denenip sonsuza kadar kuyrukta kalıyordu.
            // Yetki hatası — sonuçtaki hata metninde de fırlatılan istisnada da (E74) —
            // deneme sayacını artırmaz; kararın tamamı PendingSyncPolicy'dedir.
            when (
                PendingSyncPolicy.decision(
                    hasResult = result.isSuccess && syncResult != null,
                    needsRetry = syncResult?.needsRetry ?: true,
                    errorTexts = syncResult?.errors.orEmpty(),
                    thrownMessage = result.exceptionOrNull()?.message
                )
            ) {
                PendingSyncPolicy.RetryDecision.DROP -> {
                    dao.deleteById(item.id)
                    successCount++
                }
                PendingSyncPolicy.RetryDecision.KEEP_INCREMENT -> dao.incrementRetry(item.id)
                PendingSyncPolicy.RetryDecision.KEEP_WITHOUT_INCREMENT -> Unit
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

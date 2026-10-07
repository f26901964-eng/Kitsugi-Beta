package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.content.Context
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaEntryRepository(
    private val dao: MediaEntryDao,
    private val pendingSyncDao: PendingSyncDao? = null,
    val context: Context? = null,
    private val onExternalSyncMessage: ((String) -> Unit)? = null
) {
    val entriesFlow: Flow<List<MediaEntry>> = kotlinx.coroutines.flow.flow {
        dao.observeAll().collect { entities ->
            emit(entities.map { it.toDomain() })
        }
    }

    suspend fun insert(entry: MediaEntry) {
        val entryWithTime = if (entry.updatedAt == 0L) {
            entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
        } else {
            entry
        }
        val newId = dao.insert(entryWithTime.copy(id = 0).toEntity())
        // DB'de oluşan gerçek id ile sync yap (AniList entryId geri yazımı doğru entity'yi yakalar)
        val persistedEntry = dao.getById(newId.toInt())?.toDomain() ?: entryWithTime.copy(id = newId.toInt())
        syncEntryIfPossible(persistedEntry)
    }

    suspend fun insertAll(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        val entriesWithTime = entries.map { entry ->
            if (entry.updatedAt == 0L) {
                entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
            } else {
                entry
            }
        }
        dao.insertAll(entriesWithTime.map { it.copy(id = 0).toEntity() })
    }

    /**
     * Granüler import: mevcut kayıtlarla karşılaştırır, sadece
     * gerçekten değişen kayıtları günceller. Tüm işlem tek bir
     * @Transaction içinde çalışır → Room Flow yalnızca 1 kez
     * tetiklenir ve Compose yalnızca değişen kartları yeniden çizer.
     *
     * @param source  "anilist", "mal", "simkl" vb.
     * @param importedEntries  Uzak API'dan gelen güncel liste
     */
    suspend fun smartImport(
        source: String,
        importedEntries: List<MediaEntry>,
        allowDelete: Boolean = true
    ) {
        if (importedEntries.isEmpty()) {
            if (allowDelete) {
                dao.deleteBySource(source)
            }
            return
        }

        val existing = dao.getAll().filter { it.source.equals(source, ignoreCase = true) }
        val updates = linkedMapOf<Int, MediaEntryEntity>()
        val inserts = mutableListOf<MediaEntry>()
        val matchedIds = mutableSetOf<Int>()
        for (entry in importedEntries) {
            val imported = entry.copy(source = source)
            val matches = existing.filter {
                com.kitsugi.animelist.model.MediaIdentity.sameMedia(it.toDomain(), imported)
            }
            val match = when {
                matches.isEmpty() -> null
                matches.size == 1 -> matches.single()
                else -> {
                    // Birden fazla eşleşme durumunda en doğru kaydı tespit et (çökmeyi engelle)
                    val importedKeys = com.kitsugi.animelist.model.MediaIdentity.keys(imported)
                    val keyMatch = matches.firstOrNull { m ->
                        val mKeys = com.kitsugi.animelist.model.MediaIdentity.keys(m.toDomain())
                        importedKeys.intersect(mKeys).isNotEmpty()
                    }
                    val exactTitleMatch = matches.firstOrNull { m ->
                        val normLocal = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(m.title)
                        val normImp = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(imported.title)
                        normLocal.isNotBlank() && normLocal == normImp
                    }
                    val exactEngMatch = matches.firstOrNull { m ->
                        val engLocal = m.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        val engImp = imported.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        !engLocal.isNullOrBlank() && engLocal == engImp
                    }
                    keyMatch ?: exactTitleMatch ?: exactEngMatch ?: matches.maxByOrNull { it.updatedAt } ?: matches.first()
                }
            }
            if (match != null) {
                matchedIds.add(match.id)
                updates[match.id] = imported.copy(id = match.id).toEntity()
            } else {
                val prior = inserts.indexOfFirst {
                    com.kitsugi.animelist.model.MediaIdentity.sameMedia(it, imported, allowTitle = false)
                }
                if (prior >= 0) inserts[prior] = imported else inserts.add(imported)
            }
        }
        // allowDelete=false must really mean NO deletion, including alleged title duplicates.
        val deletions = if (allowDelete) existing.filter { it.id !in matchedIds }.map { it.id } else emptyList()
        dao.smartImportTransaction(inserts.map { it.copy(id = 0).toEntity() }, updates.values.toList(), deletions)
    }

    suspend fun updateAllDirect(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        dao.updateAll(entries.map { it.toEntity() })
    }

    suspend fun update(entry: MediaEntry, syncExternal: Boolean = true, advancedScores: List<Double>? = null) {
        val entryWithTime = entry.copy(updatedAt = System.currentTimeMillis() / 1000L)
        dao.update(
            entryWithTime.toEntity()
        )

        if (syncExternal) {
            syncEntryIfPossible(entryWithTime, advancedScores)
        }
    }

    suspend fun deleteById(id: Int) {
        val entry = dao.getById(id)?.toDomain()

        dao.deleteById(id)

        if (entry != null) {
            syncDeleteIfPossible(entry)
        }
    }

    suspend fun deleteBySource(source: String) {
        dao.deleteBySource(source)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun replaceAll(entries: List<MediaEntry>) {
        val now = System.currentTimeMillis() / 1000L
        val entities = entries.map { it.copy(id = 0, updatedAt = it.updatedAt.takeIf { value -> value > 0 } ?: now).toEntity() }
        dao.replaceAllTransaction(entities)
    }

    /**
     * Bekleyen kuyruğu boşaltır. AppViewModel.init'ten çağrılır.
     * @return Başarıyla gönderilen işlem sayısı
     */
    suspend fun drainPendingQueue(): Int {
        val ctx = context ?: return 0
        val queueDao = pendingSyncDao ?: return 0
        return PendingSyncDrainer.drain(ctx, queueDao)
    }

    // ────────────────────────────────────────────────
    // Private Sync Helpers
    // ────────────────────────────────────────────────

    private suspend fun syncEntryIfPossible(entry: MediaEntry, advancedScores: List<Double>? = null) {
        val appContext = context ?: return
        // Senkronizasyon için geçerli koşullar:
        // 1) malId var (MAL veya gerçek bir MAL ID'si olan AniList kaydı)
        // 2) aniListEntryId var (kaynak ne olursa olsun AniList liste kaydı)
        // 3) kaynak anilist (mediaId henüz bilinmese bile AniList'e push edilebilir)
        // 4) simklId var (Simkl kaydı)
        val hasAniListLink = entry.aniListEntryId != null || entry.source == "anilist"
        val hasMalLink = entry.malId != null
        val hasSimklLink = entry.simklId != null && entry.simklId > 0
        val hasTmdbLink = entry.tmdbId != null && entry.tmdbId > 0
        val hasTitle = entry.title.isNotBlank() || !entry.titleEnglish.isNullOrBlank()
        if (!hasAniListLink && !hasMalLink && !hasSimklLink && !hasTmdbLink && !hasTitle) return

        val result = runSyncCatching {
            ExternalListSyncManager.syncEntry(
                context = appContext,
                entry = entry,
                advancedScores = advancedScores
            )
        }

        val syncResult = result.getOrNull()
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            if (messages.isNotEmpty()) removeFromQueueIfPresent(entry, "UPDATE")
            notifySyncResult(messages)

            // Dual-write: AniList'ten dönen list entry ID'yi yerel DB'ye kaydet
            val newAniListEntryId = syncResult?.aniListEntryId
            if (newAniListEntryId != null && newAniListEntryId != entry.aniListEntryId) {
                val updatedEntry = (dao.getById(entry.id)?.toDomain() ?: entry).copy(aniListEntryId = newAniListEntryId)
                dao.update(updatedEntry.toEntity())
            }

            if (messages.isNotEmpty()) drainPendingQueue()
        } else {
            enqueuePending("UPDATE", entry)
            val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
            val isAuthError = allErrors.contains("401") || allErrors.contains("token geçersiz") || allErrors.contains("Unauthorized")
            val displayMessage = if (isAuthError) {
                "🔑 Oturum süresi doldu, lütfen ayarlardan tekrar bağlanın"
            } else {
                "📵 Çevrimdışı kaydedildi, bağlantı gelince gönderilecek"
            }
            notifySyncResult(syncResult?.messages.orEmpty() + listOf(displayMessage))
        }
    }

    private suspend fun syncDeleteIfPossible(entry: MediaEntry) {
        val appContext = context ?: return
        // Silme için geçerli koşullar (syncEntryIfPossible ile aynı mantık)
        val hasAniListLink = entry.aniListEntryId != null || entry.source == "anilist"
        val hasMalLink = entry.malId != null
        val hasSimklLink = entry.simklId != null && entry.simklId > 0
        val hasTmdbLink = entry.tmdbId != null && entry.tmdbId > 0
        val hasTitle = entry.title.isNotBlank() || !entry.titleEnglish.isNullOrBlank()
        if (!hasAniListLink && !hasMalLink && !hasSimklLink && !hasTmdbLink && !hasTitle) return

        val result = runSyncCatching {
            ExternalListSyncManager.deleteEntry(
                context = appContext,
                entry = entry
            )
        }

        val syncResult = result.getOrNull()
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            if (messages.isNotEmpty()) removeFromQueueIfPresent(entry, "DELETE")
            notifySyncResult(messages)
            if (messages.isNotEmpty()) drainPendingQueue()
        } else {
            enqueuePending("DELETE", entry)
            val allErrors = syncResult?.errors.orEmpty().joinToString(" | ")
            val isAuthError = allErrors.contains("401") || allErrors.contains("token geçersiz") || allErrors.contains("Unauthorized")
            val displayMessage = if (isAuthError) {
                "🔑 Oturum süresi doldu, lütfen ayarlardan tekrar bağlanın"
            } else {
                "📵 Çevrimdışı kaydedildi, bağlantı gelince gönderilecek"
            }
            notifySyncResult(syncResult?.messages.orEmpty() + listOf(displayMessage))
        }
    }

    private suspend fun enqueuePending(operation: String, entry: MediaEntry) {
        val queueDao = pendingSyncDao ?: return
        PendingSyncDrainer.enqueue(queueDao, operation, entry)
    }

    /**
     * Aynı yerel kayıt + kaynak + tür + işlem için bekleyen kaydı temizle
     * (örn. çevrimiçiyken yeniden güncelleme yapınca eski UPDATE'i temizle)
     */
    private suspend fun removeFromQueueIfPresent(entry: MediaEntry, operation: String) {
        val queueDao = pendingSyncDao ?: return
        val all = queueDao.getAll()
        all.filter { it.operation == operation }.forEach { pending ->
            // Bare MAL IDs are not unique across anime, manga, or platform libraries.
            runSyncCatching {
                val json = org.json.JSONObject(pending.entryJson)
                val sameLocalRecord = entry.id > 0 && json.optInt("id", 0) == entry.id &&
                    json.optString("type") == entry.type.name && json.optString("source") == entry.source
                if (sameLocalRecord) queueDao.deleteById(pending.id)
            }
        }
    }

    private fun notifySyncResult(messages: List<String>) {
        val message = messages
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        if (message.isNotBlank()) {
            onExternalSyncMessage?.invoke(message)
        }
    }
}
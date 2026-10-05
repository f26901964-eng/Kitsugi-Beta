package com.kitsugi.animelist.data.local

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
    private fun normalizeTitleKey(title: String, type: MediaType): String {
        val clean = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${clean}_${type.name}"
    }

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

        // Yerel DB'deki mevcut kayıtları al (sadece ilgili source)
        val allExistingEntities = dao.getAll()
            .filter { it.source.equals(source, ignoreCase = true) }

        // Veritabanındaki yinelenen (duplicate) kayıtları tespit et ve temizle (örn. Kitsu 1400 kayıt sorunu)
        val duplicateDeleteIds = mutableListOf<Int>()
        val uniqueExistingEntities = mutableListOf<MediaEntryEntity>()
        val seenTitleKeys = mutableSetOf<String>()

        for (entity in allExistingEntities.sortedByDescending { it.updatedAt }) {
            val domain = entity.toDomain()
            val titleKey = normalizeTitleKey(domain.title, domain.type)
            if (titleKey.isNotBlank() && !seenTitleKeys.add(titleKey)) {
                // Bu başlık ve türde zaten daha güncel bir kayıt listeye alındı -> fazlalığı sil
                duplicateDeleteIds.add(entity.id)
            } else {
                uniqueExistingEntities.add(entity)
            }
        }

        val existingByKey = uniqueExistingEntities.associateBy { it.toDomain().importKey() }
        val existingByTitle = uniqueExistingEntities.associateBy { normalizeTitleKey(it.title, it.toDomain().type) }

        val importedByKey = importedEntries.associateBy { it.importKey() }

        val toInsert = mutableListOf<MediaEntryEntity>()
        val toUpdate = mutableListOf<MediaEntryEntity>()
        val matchedExistingIds = mutableSetOf<Int>()

        for (imported in importedEntries) {
            val key = imported.importKey()
            val titleKey = normalizeTitleKey(imported.title, imported.type)
            val engTitleKey = imported.titleEnglish?.takeIf { it.isNotBlank() }?.let { normalizeTitleKey(it, imported.type) }

            // Öncelik: 1. Doğrudan ID anahtarı -> 2. Orijinal Başlık -> 3. İngilizce Başlık
            val existingEntity = existingByKey[key]
                ?: existingByTitle[titleKey]
                ?: (if (engTitleKey != null) existingByTitle[engTitleKey] else null)

            val importTime = if (imported.updatedAt > 0L) imported.updatedAt else (System.currentTimeMillis() / 1000L)
            val finalImported = imported.copy(updatedAt = importTime)

            if (existingEntity == null) {
                // Yeni kayıt → insert
                toInsert.add(finalImported.copy(id = 0).toEntity())
            } else {
                matchedExistingIds.add(existingEntity.id)
                if (hasChanged(existingEntity.toDomain(), finalImported)) {
                    // Değişmiş kayıt → id'yi koruyarak güncelle
                    toUpdate.add(finalImported.copy(id = existingEntity.id).toEntity())
                }
            }
        }

        // Uzak listede artık olmayan kayıtları sil (ve önceden tespit edilen dublikatları daima sil)
        val toDeleteIds = mutableListOf<Int>()
        toDeleteIds.addAll(duplicateDeleteIds)

        if (allowDelete) {
            val importedKeys = importedByKey.keys
            for (entity in uniqueExistingEntities) {
                val domain = entity.toDomain()
                val k = domain.importKey()
                val tk = normalizeTitleKey(domain.title, domain.type)
                val isMatched = entity.id in matchedExistingIds || k in importedKeys || existingByTitle[tk]?.id in matchedExistingIds
                if (!isMatched) {
                    toDeleteIds.add(entity.id)
                }
            }
        }

        // Tek atomik transaction → Flow 1 kez tetiklenir
        dao.smartImportTransaction(toInsert, toUpdate, toDeleteIds.distinct())
    }

    private fun MediaEntry.importKey(): String {
        return when {
            // Kitsu kayd\u0131 - gerçek MAL ID biliniyor (mappings'ten)
            source.equals("kitsu", ignoreCase = true) && malId != null && malId in 1..99_999_999 -> "kitsu_mal_$malId"
            // Kitsu kayd\u0131 - offset'li fake ID
            source.equals("kitsu", ignoreCase = true) && malId != null && malId >= 300_000_000 -> "kitsu_${malId - 300_000_000}"
            source.equals("shikimori", ignoreCase = true) && malId != null && malId >= 400_000_000 -> "shiki_${malId - 400_000_000}"
            simklId != null && simklId > 0 -> "simkl_$simklId"
            malId != null && malId in 1..99_999_999 -> "mal_$malId"
            aniListEntryId != null && aniListEntryId > 0 -> "al_$aniListEntryId"
            tmdbId != null && tmdbId > 0 -> "tmdb_$tmdbId"
            else -> "title_${normalizeTitleKey(title, type)}"
        }
    }

    /**
     * İki entry arasında anlamlı bir fark var mı kontrol eder.
     * Aynıysa import sırasında DB'ye dokunmayız.
     */
    private fun hasChanged(local: MediaEntry, remote: MediaEntry): Boolean {
        return local.status != remote.status ||
            local.progress != remote.progress ||
            local.score != remote.score ||
            local.total != remote.total ||
            local.isFavorite != remote.isFavorite ||
            local.repeatCount != remote.repeatCount ||
            local.notes != remote.notes ||
            local.startDate != remote.startDate ||
            local.endDate != remote.endDate
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
        dao.deleteAll()
        insertAll(entries)
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

        val result = runCatching {
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
            removeFromQueueIfPresent(entry, "UPDATE")
            notifySyncResult(messages)

            // Dual-write: AniList'ten dönen list entry ID'yi yerel DB'ye kaydet
            val newAniListEntryId = syncResult?.aniListEntryId
            if (newAniListEntryId != null && newAniListEntryId != entry.aniListEntryId) {
                val updatedEntry = entry.copy(aniListEntryId = newAniListEntryId)
                dao.update(updatedEntry.toEntity())
            }

            drainPendingQueue()
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

        val result = runCatching {
            ExternalListSyncManager.deleteEntry(
                context = appContext,
                entry = entry
            )
        }

        val syncResult = result.getOrNull()
        val success = result.isSuccess && syncResult != null && syncResult.errors.isEmpty()

        if (success) {
            val messages = syncResult?.messages.orEmpty()
            removeFromQueueIfPresent(entry, "DELETE")
            notifySyncResult(messages)
            drainPendingQueue()
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
     * Aynı malId + operation için eski bekleyen kayıt varsa sil
     * (örn. çevrimiçiyken yeniden güncelleme yapınca eski UPDATE'i temizle)
     */
    private suspend fun removeFromQueueIfPresent(entry: MediaEntry, operation: String) {
        val queueDao = pendingSyncDao ?: return
        val all = queueDao.getAll()
        all.filter { it.operation == operation }.forEach { pending ->
            // JSON'dan malId'yi okuyup karşılaştır
            runCatching {
                val json = org.json.JSONObject(pending.entryJson)
                val pendingMalId = json.opt("malId")
                if (pendingMalId != null && pendingMalId != org.json.JSONObject.NULL &&
                    (pendingMalId as? Int) == entry.malId
                ) {
                    queueDao.deleteById(pending.id)
                }
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
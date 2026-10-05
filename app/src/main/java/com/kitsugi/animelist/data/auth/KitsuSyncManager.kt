package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kitsu kütüphane senkronizasyon yöneticisi.
 */
object KitsuSyncManager {
    private const val TAG = "KitsuSyncManager"
    private const val KITSU_OFFSET = 300_000_000

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList()
    )

    fun watchStatusToKitsu(status: WatchStatus?): String = when (status) {
        WatchStatus.Watching  -> "current"
        WatchStatus.Completed -> "completed"
        WatchStatus.Paused    -> "on_hold"
        WatchStatus.Dropped   -> "dropped"
        WatchStatus.Planned   -> "planned"
        else                  -> "planned"
    }

    fun kitsuStatusToWatchStatus(status: String?): WatchStatus = when (status?.lowercase()) {
        "current"   -> WatchStatus.Watching
        "completed" -> WatchStatus.Completed
        "on_hold"   -> WatchStatus.Paused
        "dropped"   -> WatchStatus.Dropped
        "planned"   -> WatchStatus.Planned
        else        -> WatchStatus.Planned
    }

    /**
     * Bir MediaEntry'yi kullanıcının Kitsu kütüphanesine senkronize eder.
     */
    suspend fun syncEntryToKitsu(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getKitsuToken(context)
        val userId = ExternalAuthManager.getKitsuUserId(context)

        if (token.isNullOrBlank() || userId.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Kitsu hesabı bağlı değil"))
        }

        // Kitsu sadece Anime ve Manga destekler
        val isAnime = entry.type != MediaType.Manga

        // 1. Kitsu Media Numeric ID'sini bul
        var kitsuMediaId: Int? = knownKitsuMediaId
        if (kitsuMediaId == null && entry.malId != null && entry.malId >= KITSU_OFFSET && entry.malId < 400_000_000) {
            kitsuMediaId = entry.malId - KITSU_OFFSET
        }

        if (kitsuMediaId == null) {
            val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < KITSU_OFFSET) {
                entry.malId - 100_000_000
            } else null
            val realMalId = entry.malId?.takeIf { it in 1..99_999_999 }

            val armKitsu = runCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                    malId = realMalId,
                    aniListId = rawAniListId,
                    tmdbId = entry.tmdbId,
                    mediaType = entry.type
                ).kitsuId
            }.getOrNull()

            if (armKitsu != null && armKitsu > 0) {
                kitsuMediaId = armKitsu
            }
        }

        if (kitsuMediaId == null) {
            val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title
            kitsuMediaId = KitsuApiClient.lookupKitsuId(searchTitle, isAnime = isAnime)
            if (kitsuMediaId == null && !entry.titleEnglish.isNullOrBlank() && entry.title.isNotBlank()) {
                kitsuMediaId = KitsuApiClient.lookupKitsuId(entry.title, isAnime = isAnime)
            }
        }

        if (kitsuMediaId == null || kitsuMediaId <= 0) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Kitsu ID bulunamadı: ${entry.title}"))
        }

        val status = watchStatusToKitsu(entry.status)
        val progress = entry.progress
        val ratingTwenty = entry.score?.let { s ->
            if (s > 10) kotlin.math.round(s / 5.0).toInt().coerceIn(2, 20)
            else (s * 2).coerceIn(2, 20)
        }

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 2. Kütüphanedeki mevcut kaydı kontrol et (önce yerel önbellek, sonra uzaktan sorgu)
        var existingEntryId = ExternalAuthManager.getKitsuLibraryEntryId(context, kitsuMediaId, isAnime)
        if (existingEntryId == null) {
            existingEntryId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
            if (existingEntryId != null) {
                ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, existingEntryId)
            }
        }

        if (existingEntryId != null) {
            val success = KitsuApiClient.updateLibraryEntry(
                token = token,
                entryId = existingEntryId,
                status = status,
                progress = progress,
                ratingTwenty = ratingTwenty
            )
            if (success) messages.add("Kitsu güncellendi (${entry.title})")
            else errors.add("Kitsu güncellenemedi (${entry.title})")
        } else {
            val newEntryId = KitsuApiClient.createLibraryEntry(
                token = token,
                userId = userId,
                kitsuMediaId = kitsuMediaId,
                isAnime = isAnime,
                status = status,
                progress = progress,
                ratingTwenty = ratingTwenty
            )
            if (newEntryId != null) {
                ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, newEntryId)
                messages.add("Kitsu kütüphanesine eklendi (${entry.title})")
            } else {
                // Kayıt zaten Kitsu'da var olabilir -> ID'yi bulup güncellemeyi dene
                val fallbackId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
                if (fallbackId != null) {
                    ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, fallbackId)
                    val updateSuccess = KitsuApiClient.updateLibraryEntry(
                        token = token,
                        entryId = fallbackId,
                        status = status,
                        progress = progress,
                        ratingTwenty = ratingTwenty
                    )
                    if (updateSuccess) messages.add("Kitsu güncellendi (${entry.title})")
                    else errors.add("Kitsu güncellenemedi (${entry.title})")
                } else {
                    errors.add("Kitsu kütüphanesine eklenemedi (${entry.title})")
                }
            }
        }

        SyncResult(messages = messages, errors = errors)
    }

    /**
     * Kitsu kütüphanesinden kaydı siler.
     */
    suspend fun deleteEntryFromKitsu(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getKitsuToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Kitsu hesabı bağlı değil"))
        }

        val isAnime = entry.type != MediaType.Manga
        var kitsuMediaId: Int? = null
        if (entry.malId != null && entry.malId >= KITSU_OFFSET && entry.malId < 400_000_000) {
            kitsuMediaId = entry.malId - KITSU_OFFSET
        }

        val existingEntryId = if (kitsuMediaId != null) {
            ExternalAuthManager.getKitsuLibraryEntryId(context, kitsuMediaId, isAnime)
        } else null

        if (existingEntryId != null) {
            val success = KitsuApiClient.deleteLibraryEntry(token, existingEntryId)
            ExternalAuthManager.removeKitsuLibraryEntryId(context, kitsuMediaId!!, isAnime)
            if (success) SyncResult(messages = listOf("Kitsu kütüphanesinden silindi (${entry.title})"))
            else SyncResult(messages = emptyList(), errors = listOf("Kitsu'dan silinemedi"))
        } else {
            SyncResult(messages = emptyList(), errors = listOf("Kitsu kaydı bulunamadı"))
        }
    }
}

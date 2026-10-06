package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shikimori.one kütüphane senkronizasyon yöneticisi.
 */
object ShikimoriSyncManager {
    private const val TAG = "ShikimoriSyncManager"

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList()
    )

    fun watchStatusToShikimori(status: WatchStatus?): String = when (status) {
        WatchStatus.Watching  -> "watching"
        WatchStatus.Repeating -> "rewatching"
        WatchStatus.Completed -> "completed"
        WatchStatus.Paused    -> "on_hold"
        WatchStatus.Dropped   -> "dropped"
        WatchStatus.Planned   -> "planned"
        else                  -> "planned"
    }

    fun shikimoriStatusToWatchStatus(status: String?): WatchStatus = when (status?.lowercase()) {
        "watching"   -> WatchStatus.Watching
        "rewatching" -> WatchStatus.Repeating
        "completed"  -> WatchStatus.Completed
        "on_hold"    -> WatchStatus.Paused
        "dropped"    -> WatchStatus.Dropped
        "planned"    -> WatchStatus.Planned
        else         -> WatchStatus.Planned
    }

    /**
     * Bir MediaEntry'yi kullanıcının Shikimori kütüphanesine senkronize eder.
     */
    suspend fun syncEntryToShikimori(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        val userId = ExternalAuthManager.getShikimoriUserId(context)

        if (token.isNullOrBlank() || userId == null || userId <= 0) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Shikimori hesabı bağlı değil"))
        }

        // Shikimori MAL ID'lerini doğrudan hedef ID (target_id) olarak kullanır!
        val targetId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
            ?: if (entry.source == "shikimori" && entry.malId != null && entry.malId > 0) entry.malId else null

        if (targetId == null) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Shikimori (MAL) ID bulunamadı: ${entry.title}"))
        }

        val targetType = if (entry.type == MediaType.Manga) "Manga" else "Anime"
        val status = watchStatusToShikimori(entry.status)
        val score = entry.score?.let { s ->
            if (s > 10) kotlin.math.round(s / 10.0).toInt().coerceIn(0, 10)
            else s.coerceIn(0, 10)
        } ?: 0
        val progress = entry.progress ?: 0

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val existingRateId = ExternalAuthManager.getShikimoriRateId(context, targetId, targetType)

        if (existingRateId != null) {
            val success = ShikimoriApiClient.updateUserRate(
                token = token,
                rateId = existingRateId,
                targetType = targetType,
                status = status,
                score = score,
                progress = progress
            )
            if (success) messages.add("Shikimori güncellendi (${entry.title})")
            else errors.add("Shikimori güncellenemedi (${entry.title})")
        } else {
            val newRateId = ShikimoriApiClient.createUserRate(
                token = token,
                userId = userId,
                targetId = targetId,
                targetType = targetType,
                status = status,
                score = score,
                progress = progress
            )
            if (newRateId != null) {
                ExternalAuthManager.saveShikimoriRateId(context, targetId, targetType, newRateId)
                messages.add("Shikimori kütüphanesine eklendi (${entry.title})")
            } else {
                errors.add("Shikimori kütüphanesine eklenemedi (${entry.title})")
            }
        }

        SyncResult(messages = messages, errors = errors)
    }

    /**
     * Shikimori kütüphanesinden kaydı siler.
     */
    suspend fun deleteEntryFromShikimori(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Shikimori hesabı bağlı değil"))
        }

        val targetId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
            ?: if (entry.source == "shikimori" && entry.malId != null && entry.malId > 0) entry.malId else null

        val targetType = if (entry.type == MediaType.Manga) "Manga" else "Anime"
        val existingRateId = if (targetId != null) {
            ExternalAuthManager.getShikimoriRateId(context, targetId, targetType)
        } else null

        if (existingRateId != null) {
            val success = ShikimoriApiClient.deleteUserRate(token, existingRateId)
            ExternalAuthManager.removeShikimoriRateId(context, targetId!!, targetType)
            if (success) SyncResult(messages = listOf("Shikimori kütüphanesinden silindi (${entry.title})"))
            else SyncResult(messages = emptyList(), errors = listOf("Shikimori'den silinemedi"))
        } else {
            SyncResult(messages = emptyList(), errors = listOf("Shikimori kaydı bulunamadı"))
        }
    }
}

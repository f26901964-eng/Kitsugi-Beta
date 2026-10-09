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
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf("Bu servis yalnızca anime/manga destekler"))
        }

        val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        var userId = ExternalAuthManager.getShikimoriUserId(context)
        if (!token.isNullOrBlank() && userId == null) {
            // Kimlik eksikse (girişte whoami geçici yanıt vermediyse) bir kez daha dene.
            ExternalAuthManager.ensureShikimoriUserResolved(context)
            userId = ExternalAuthManager.getShikimoriUserId(context)
        }

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

        // Official v2 create is an upsert by user_id + target_type + target_id.
        // It recovers safely from stale cached rate IDs without assuming a failed PATCH means absence.
        // Not / tekrar sayısı / cilt: boş ya da sıfır değerler uzaktaki kaydı silmesin diye gönderilmez.
        val rateId = ShikimoriApiClient.createUserRate(
            token = token, userId = userId, targetId = targetId, targetType = targetType,
            status = status, score = score, progress = progress,
            text = entry.notes?.takeIf { it.isNotBlank() },
            rewatches = entry.repeatCount.takeIf { it > 0 },
            volumes = if (targetType == "Manga") entry.volumeProgress.takeIf { it > 0 } else null
        )
        if (rateId != null) {
            ExternalAuthManager.saveShikimoriRateId(context, targetId, targetType, rateId)
            messages.add("Shikimori kaydı onaylandı (${entry.title})")
        } else {
            errors.add("Shikimori kaydı onaylanmadı (${entry.title})")
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
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf("Bu servis yalnızca anime/manga destekler"))
        }

        val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Shikimori hesabı bağlı değil"))
        }
        // getShikimoriRateId, kullanıcı kimliği kayıtlı değilse istisna fırlatır; önce çözmeye çalış.
        if (ExternalAuthManager.getShikimoriUserId(context) == null) {
            ExternalAuthManager.ensureShikimoriUserResolved(context)
        }

        val targetId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
            ?: if (entry.source == "shikimori" && entry.malId != null && entry.malId > 0) entry.malId else null

        val targetType = if (entry.type == MediaType.Manga) "Manga" else "Anime"
        val existingRateId = if (targetId != null) {
            ExternalAuthManager.getShikimoriRateId(context, targetId, targetType)
        } else null

        if (existingRateId != null) {
            val success = ShikimoriApiClient.deleteUserRate(token, existingRateId)
            if (success) ExternalAuthManager.removeShikimoriRateId(context, targetId!!, targetType)
            if (success) SyncResult(messages = listOf("Shikimori kütüphanesinden silindi (${entry.title})"))
            else SyncResult(messages = emptyList(), errors = listOf("Shikimori'den silinemedi"))
        } else {
            SyncResult(messages = emptyList(), errors = listOf("Shikimori kaydı bulunamadı"))
        }
    }
}

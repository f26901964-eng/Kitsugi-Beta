package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.SimklApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.local.toEntity

/**
 * Simkl ↔ Kitsugi uygulaması arasında çift yönlü senkronizasyon yöneticisi.
 *
 * Referans mimariler:
 * - NyanTV/SimklService.kt → updateEntry, fetchUserMovies/Shows, status mapping
 * - scrob → history/sync geri yazma (POST /sync/history)
 * - SyncMeta → dry-run, diagnostics, concurrency kontrolü
 * - Showly → Trakt = Simkl katmanı, TMDB = metadata katmanı ayrımı
 */
object SimklSyncManager {

    private const val TAG = "SimklSyncManager"
    private val simklApiClient = SimklApiClient()

    // ── Durum Dönüşüm Yardımcıları (NyanTV simklStatusToAL referans) ────────────

    /** Simkl status string'ini uygulama WatchStatus'una çevirir */
    fun simklStatusToWatchStatus(simklStatus: String?): WatchStatus = when (simklStatus?.lowercase()) {
        "watching", "reading", "rewatching" -> WatchStatus.Watching
        "completed"                         -> WatchStatus.Completed
        "hold", "on_hold", "paused"         -> WatchStatus.Paused
        "dropped"                           -> WatchStatus.Dropped
        "plantowatch", "plan_to_watch", "planning" -> WatchStatus.Planned
        else                                -> WatchStatus.Planned
    }

    /** Uygulama WatchStatus'unu Simkl status string'ine çevirir (NyanTV alStatusToSimkl referans) */
    fun watchStatusToSimkl(status: WatchStatus?): String = when (status) {
        WatchStatus.Watching   -> "watching"
        WatchStatus.Completed  -> "completed"
        WatchStatus.Paused     -> "hold"
        WatchStatus.Dropped    -> "dropped"
        WatchStatus.Planned    -> "plantowatch"
        else                   -> "plantowatch"
    }

    /** MediaType'a göre Simkl API tipini döner */
    fun mediaTypeToSimklType(mediaType: MediaType): String = when (mediaType) {
        MediaType.Movie  -> "movies"
        MediaType.TvShow -> "shows"
        MediaType.Anime  -> "anime" // Simkl API'sinde animeler 'anime' anahtarı altındadır!
        else             -> "shows"
    }

    // ── Toplu Senkronizasyon (Batch Sync) ──────────────────────────────────────────

    /**
     * Çoklu MediaEntry listesini Simkl API'sine tek veya az sayıda toplu istek ile gönderir.
     * Simkl'in 1 istek / saniye rate limit'ine ve 50 öğe / istek limitine tam uyumludur.
     */
    suspend fun syncBatchToSimkl(
        context: Context,
        entries: List<MediaEntry>
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl hesabı bağlı değil"))
        }
        if (entries.isEmpty()) {
            return@withContext SyncResult(messages = emptyList(), errors = emptyList())
        }

        val batchItems = entries.mapNotNull { entry ->
            val realMalId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
            val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
                entry.malId - 100_000_000
            } else null
            val rawKitsuId = if (entry.source == "kitsu" || (entry.malId != null && entry.malId >= 300_000_000)) {
                if (entry.malId != null && entry.malId >= 300_000_000) entry.malId - 300_000_000 else entry.malId
            } else null
            val simklId = entry.simklId?.takeIf { it > 0 } ?: 0

            // En az bir geçerli ID olmalı (simklId, malId, anilist, kitsu veya tmdb)
            if (simklId == 0 && realMalId == null && (entry.tmdbId == null || entry.tmdbId <= 0) && aniListId == null) {
                null
            } else {
                SimklApiClient.SimklBatchEntry(
                    type = mediaTypeToSimklType(entry.type),
                    status = watchStatusToSimkl(entry.status),
                    simklId = simklId,
                    malId = realMalId,
                    tmdbId = entry.tmdbId,
                    aniListId = aniListId,
                    kitsuId = rawKitsuId
                )
            }
        }

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 35'lik parçalar halinde gönder (Simkl limiti 50)
        val chunks = batchItems.chunked(35)
        for ((idx, chunk) in chunks.withIndex()) {
            try {
                val ok = simklApiClient.addToListBatch(token, chunk)
                if (ok) {
                    messages.add("Simkl grubu ${idx + 1}/${chunks.size} başarıyla senkronize edildi (${chunk.size} öğe).")
                } else {
                    errors.add("Simkl grubu ${idx + 1} gönderilemedi.")
                }
            } catch (e: Exception) {
                errors.add("Simkl grup ${idx + 1} hatası: ${e.message}")
            }
            if (idx < chunks.size - 1) {
                // Rate limit (1 req/sn) aşmamak için bekle
                kotlinx.coroutines.delay(1100L)
            }
        }

        SyncResult(messages = messages, errors = errors)
    }

    // ── Tek Entry Senkronizasyonu ─────────────────────────────────────────────────

    /**
     * Uygulama listesindeki bir MediaEntry'yi Simkl'e yazar.
     * Simkl bağlı değilse işlem yapılmaz.
     * Referans: NyanTV/SimklService.kt updateEntry()
     */
    suspend fun syncEntryToSimkl(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl hesabı bağlı değil"))
        }

        var simklId = entry.simklId
        val realMalId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
        val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null
        val rawKitsuId = if (entry.source == "kitsu" || (entry.malId != null && entry.malId >= 300_000_000)) {
            if (entry.malId != null && entry.malId >= 300_000_000) entry.malId - 300_000_000 else entry.malId
        } else null

        // Simkl ID yoksa ve malId/tmdbId de yoksa o zaman başlığa göre Simkl ID çözümlenir
        if ((simklId == null || simklId <= 0) && realMalId == null && entry.tmdbId == null) {
            val resolvedId = simklApiClient.lookupSimklId(
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                title = entry.titleEnglish ?: entry.title,
                year = entry.year,
                mediaType = entry.type
            )
            if (resolvedId != null && resolvedId > 0) {
                simklId = resolvedId
                runCatching {
                    val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                    dao.update(entry.copy(simklId = resolvedId).toEntity())
                }
            }
        }

        // Eğer simklId, malId ve tmdbId'nin hiçbiri yoksa senkronize edilemez
        if ((simklId == null || simklId <= 0) && realMalId == null && (entry.tmdbId == null || entry.tmdbId <= 0)) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl/MAL/TMDB ID bulunamadı: ${entry.title}"))
        }

        val effectiveSimklId = simklId ?: 0

        val type = mediaTypeToSimklType(entry.type)
        val simklStatus = watchStatusToSimkl(entry.status)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. Listeye ekle / durumu güncelle (POST /sync/add-to-list)
        runCatching {
            simklApiClient.addToList(
                token = token,
                simklId = effectiveSimklId,
                type = type,
                status = simklStatus,
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                kitsuId = rawKitsuId
            )
        }.onSuccess { success ->
            if (success) messages.add("Simkl listesi güncellendi (${entry.title})")
            else errors.add("Simkl listesi güncellenemedi (${entry.title})")
        }.onFailure { e ->
            errors.add("Simkl addToList hatası: ${e.message}")
            Log.e(TAG, "syncEntryToSimkl addToList hatası", e)
        }

        // 2. Bölüm ilerlemesini güncelle — sadece dizi/anime için ve geçerli simklId varsa
        val progress = entry.progress
        if (progress > 0 && entry.type != MediaType.Movie && effectiveSimklId > 0) {
            runCatching {
                simklApiClient.updateEpisodeProgress(token, effectiveSimklId, season = 1, episode = progress)
            }.onSuccess { success ->
                if (success) messages.add("Simkl bölüm ilerlemesi güncellendi: Bölüm $progress")
                else errors.add("Bölüm ilerlemesi güncellenemedi")
            }.onFailure { e ->
                errors.add("Simkl bölüm ilerlemesi hatası: ${e.message}")
                Log.e(TAG, "syncEntryToSimkl updateEpisodeProgress hatası", e)
            }
        }

        // 3. Puanı Simkl'e yaz (POST /sync/ratings)
        val score = entry.score
        if (score != null && score > 0 && effectiveSimklId > 0) {
            val normalizedRating = if (score > 10) {
                kotlin.math.round(score / 10.0).toInt().coerceIn(1, 10)
            } else {
                score.coerceIn(1, 10)
            }
            runCatching {
                simklApiClient.setRating(token, effectiveSimklId, entry.type, normalizedRating)
            }.onSuccess { success ->
                if (success) messages.add("Simkl puanı güncellendi: $normalizedRating/10")
                else errors.add("Simkl puanı güncellenemedi")
            }.onFailure { e ->
                errors.add("Simkl puan hatası: ${e.message}")
                Log.e(TAG, "syncEntryToSimkl setRating hatası", e)
            }
        } else if ((score == null || score == 0) && effectiveSimklId > 0) {
            // Puan silinmişse kaldır
            runCatching {
                simklApiClient.removeRating(token, effectiveSimklId, entry.type)
            }.onFailure { e ->
                Log.w(TAG, "Simkl removeRating uyarısı: ${e.message}")
            }
        }

        SyncResult(messages = messages, errors = errors)
    }

    /**
     * Entry'yi Simkl'den siler.
     * Referans: NyanTV/SimklService.kt deleteEntry() → POST /sync/history/remove
     */
    suspend fun deleteEntryFromSimkl(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl hesabı bağlı değil"))
        }

        var simklId = entry.simklId
        val realMalId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
        val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null

        if (simklId == null || simklId <= 0) {
            val resolvedId = simklApiClient.lookupSimklId(
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                title = entry.titleEnglish ?: entry.title,
                year = entry.year,
                mediaType = entry.type
            )
            if (resolvedId != null && resolvedId > 0) {
                simklId = resolvedId
            }
        }

        if (simklId == null || simklId <= 0) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl ID bulunamadı"))
        }

        val type = mediaTypeToSimklType(entry.type)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val rawKitsuId = if (entry.source == "kitsu" || (entry.malId != null && entry.malId >= 300_000_000)) {
            if (entry.malId != null && entry.malId >= 300_000_000) entry.malId - 300_000_000 else entry.malId
        } else null

        runCatching {
            simklApiClient.removeFromList(
                token = token,
                simklId = simklId,
                type = type,
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                kitsuId = rawKitsuId
            )
        }.onSuccess { success ->
            if (success) messages.add("Simkl listesinden silindi (${entry.title})")
            else errors.add("Simkl listesinden silinemedi")
        }.onFailure { e ->
            errors.add("Simkl removeFromList hatası: ${e.message}")
            Log.e(TAG, "deleteEntryFromSimkl hatası", e)
        }

        SyncResult(messages = messages, errors = errors)
    }

    // ── Simkl → Uygulama İçe Aktarma ─────────────────────────────────────────────

    /**
     * Simkl kullanıcı listesini çekip JikanSearchResult listesi olarak döner.
     * type: "shows", "movies", "anime"
     * Referans: NyanTV/SimklService.kt fetchUserMovies() + fetchUserShows()
     */
    suspend fun fetchSimklWatchlist(
        context: Context,
        type: String = "shows"
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            Log.w(TAG, "fetchSimklWatchlist: Simkl token bulunamadı")
            return@withContext emptyList()
        }

        runCatching {
            simklApiClient.getUserWatchlist(token, type)
        }.getOrElse { e ->
            Log.e(TAG, "fetchSimklWatchlist $type hatası: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Simkl kullanıcı profil bilgilerini çeker (kullanıcı adı, avatar vb.).
     * Referans: NyanTV/SimklService.kt fetchUserProfile()
     */
    suspend fun fetchSimklUserProfile(context: Context): SimklUserProfile? = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) return@withContext null

        runCatching {
            val json = simklApiClient.getUserProfile(token) ?: return@withContext null
            val account = json.optJSONObject("account")
            val user = json.optJSONObject("user")
            SimklUserProfile(
                id = account?.optString("id"),
                name = user?.optString("name"),
                avatarUrl = user?.optString("avatar")
            )
        }.getOrElse { e ->
            Log.e(TAG, "fetchSimklUserProfile hatası: ${e.message}", e)
            null
        }
    }

    // ── Veri Modelleri ────────────────────────────────────────────────────────────

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList()
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val hasWarnings: Boolean get() = messages.isNotEmpty() && errors.isNotEmpty()
    }

    data class SimklUserProfile(
        val id: String?,
        val name: String?,
        val avatarUrl: String?
    )
}

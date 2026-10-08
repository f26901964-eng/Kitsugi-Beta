package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shikimori.one kütüphanesini içe aktarma yöneticisi.
 */
object ShikimoriImportManager {
    private const val TAG = "ShikimoriImportManager"

    data class ShikimoriUserProfile(
        val id: Int,
        val nickname: String,
        val avatarUrl: String?
    )

    suspend fun fetchUserProfile(token: String): ShikimoriUserProfile {
        return withContext(Dispatchers.IO) {
            val user = ShikimoriApiClient.getCurrentUser(token)
            ShikimoriUserProfile(id = user.id, nickname = user.nickname, avatarUrl = user.avatarUrl)
        }
    }

    suspend fun fetchAllLists(context: Context, token: String, userId: Int): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val rates = fetchRatesWithRetry(context, token, userId)
            val unresolvedAnimeIds = rates.asSequence()
                .filter { it.targetType == "Anime" && it.ageRating.isNullOrBlank() && !it.isAdult }
                .map { it.targetId }
                .distinct()
                .toList()
            val unresolvedMangaIds = rates.asSequence()
                .filter { it.targetType == "Manga" && it.ageRating.isNullOrBlank() && !it.isAdult }
                .map { it.targetId }
                .distinct()
                .toList()
            // List responses usually omit `rating`; fetch it in batches, without making import
            // fail if Shikimori temporarily refuses this optional metadata request.
            val adultAnimeIds = runCatching {
                ShikimoriApiClient.fetchAdultMediaIds("Anime", unresolvedAnimeIds)
            }.getOrElse { error ->
                Log.w(TAG, "Shikimori anime adult metadata lookup failed: ${error.message}")
                emptySet()
            }
            val adultMangaIds = runCatching {
                ShikimoriApiClient.fetchAdultMediaIds("Manga", unresolvedMangaIds)
            }.getOrElse { error ->
                Log.w(TAG, "Shikimori manga adult metadata lookup failed: ${error.message}")
                emptySet()
            }
            val result = mutableListOf<MediaEntry>()

            for (rate in rates) {
                // Shikimori targetId doğrudan MAL ID'dir
                val malId = rate.targetId
                val isManga = rate.targetType == "Manga"

                // Cache Shikimori user_rate ID for future updates/deletes
                ExternalAuthManager.saveShikimoriRateId(context, malId, rate.targetType, rate.id)

                val status = ShikimoriSyncManager.shikimoriStatusToWatchStatus(rate.status)
                val progress = if (isManga) rate.chapters else rate.episodes

                result.add(
                    MediaEntry(
                        id = 0,
                        title = rate.title,
                        subtitle = "",
                        titleEnglish = rate.titleEnglish,
                        titleJapanese = null,
                        imageUrl = rate.imageUrl ?: "",
                        type = if (isManga) MediaType.Manga else MediaType.Anime,
                        status = status,
                        progress = progress,
                        total = rate.total,
                        score = rate.score.takeIf { it > 0 },
                        isAdult = rate.isAdult || when (rate.targetType) {
                            "Anime" -> rate.targetId in adultAnimeIds
                            "Manga" -> rate.targetId in adultMangaIds
                            else -> false
                        },
                        malId = malId,
                        aniListEntryId = null,
                        source = "shikimori",
                        updatedAt = rate.updatedAt,
                        year = rate.startYear
                    )
                )
            }
            result
        }
    }

    /**
     * Shikimori liste çekimini dener; HTTP 401 (token sunucu tarafında iptal
     * edilmiş ya da süresi dolmuş ama yerel `expiresAt` güncellenmemiş) alırsa
     * refresh token ile zorla yeni bir access token alır ve bir kez daha dener.
     */
    private suspend fun fetchRatesWithRetry(
        context: Context,
        token: String,
        userId: Int
    ): List<ShikimoriApiClient.ShikimoriRate> {
        return try {
            ShikimoriApiClient.fetchAllUserRates(token, userId)
        } catch (e: Exception) {
            // Sadece HTTP 401 hatasında retry yap; diğer hatalar olduğu gibi fırlatılır.
            if (!e.message.orEmpty().contains("HTTP 401")) throw e

            Log.w(TAG, "fetchAllUserRates 401 – token yenileme deneniyor", e)
            val newToken = ExternalAuthManager.forceRefreshShikimoriToken(context)
            if (newToken.isNullOrBlank()) {
                Log.e(TAG, "Token yenilenemedi, Shikimori oturumu sona ermiş olabilir")
                throw Exception("Shikimori oturumu sona erdi (HTTP 401). Lütfen Shikimori hesabıyla tekrar giriş yapın.")
            }
            Log.i(TAG, "Shikimori token yenilendi, liste yeniden çekiliyor...")
            ShikimoriApiClient.fetchAllUserRates(newToken, userId)
        }
    }
}

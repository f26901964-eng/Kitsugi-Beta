package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shikimori.one kütüphanesini içe aktarma yöneticisi.
 */
object ShikimoriImportManager {

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
            val rates = ShikimoriApiClient.fetchAllUserRates(token, userId)
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
                        titleEnglish = null,
                        titleJapanese = null,
                        imageUrl = rate.imageUrl ?: "",
                        type = if (isManga) MediaType.Manga else MediaType.Anime,
                        status = status,
                        progress = progress,
                        total = rate.total,
                        score = rate.score.takeIf { it > 0 },
                        isAdult = false,
                        malId = malId,
                        aniListEntryId = null,
                        source = "shikimori",
                        updatedAt = rate.updatedAt
                    )
                )
            }
            result
        }
    }
}

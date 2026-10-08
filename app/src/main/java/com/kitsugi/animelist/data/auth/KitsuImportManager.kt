package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kitsu kütüphanesini içe aktarma yöneticisi.
 */
object KitsuImportManager {
    private const val KITSU_OFFSET = 300_000_000

    data class KitsuUserProfile(
        val name: String,
        val avatarUrl: String?
    )

    suspend fun fetchUserProfile(token: String): KitsuUserProfile {
        return withContext(Dispatchers.IO) {
            val user = KitsuApiClient.getCurrentUser(token)
            KitsuUserProfile(name = user.name, avatarUrl = user.avatarUrl)
        }
    }

    suspend fun fetchAllLists(context: Context, token: String, userId: String): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val entries = KitsuApiClient.fetchAllLibraryEntries(token, userId)
            val result = mutableListOf<MediaEntry>()

            for (entry in entries) {
                val isManga = entry.mangaId != null
                val mediaId = (if (isManga) entry.mangaId else entry.animeId) ?: continue
                val isAnime = !isManga

                // Cache entry ID for later updates/deletes
                ExternalAuthManager.saveKitsuLibraryEntryId(context, mediaId, isAnime, entry.id)

                val kitsuStableId = KITSU_OFFSET + mediaId
                // Gerçek MAL ID biliniyorsa eşleme tablosuna yazıyoruz (çapraz eşitleme ve
                // Kitsu'ya geri yazım bunu kullanıyor) — ama kütüphane kaydının kimlik alanına
                // ASLA gerçek MAL ID yazılmaz. `malId` alanı kaynak-bazlı offset'li stableId
                // taşımak zorunda (MediaIdentity + 300M aralığı); aksi halde Kitsu kaydının
                // malId'si bir MAL ID'si gibi yorumlanıp alakasız bir yapımın ayrıntısı açılıyor.
                entry.realMalId?.takeIf { it in 1..99_999_999 }?.let {
                    ExternalAuthManager.saveKitsuMediaIdForMal(context, it, isAnime, mediaId)
                }
                val malIdToStore = kitsuStableId
                val status = KitsuSyncManager.kitsuStatusToWatchStatus(entry.status)
                val score = entry.ratingTwenty?.let { kotlin.math.round(it / 2.0).toInt().coerceIn(1, 10) }

                result.add(
                    MediaEntry(
                        id = 0,
                        title = entry.title,
                        subtitle = "",
                        titleEnglish = entry.titleEnglish,
                        titleJapanese = entry.titleJapanese,
                        imageUrl = entry.imageUrl ?: "",
                        type = if (isManga) MediaType.Manga else MediaType.Anime,
                        status = status,
                        progress = entry.progress,
                        total = entry.total,
                        score = score,
                        isAdult = false,
                        malId = malIdToStore, // her zaman Kitsu stableId (300M aralığı)
                        aniListEntryId = null,
                        source = "kitsu",
                        updatedAt = entry.updatedAt,
                        // Yıl olmadan kimlik doğrulaması yapılamıyor; aynı isimli yapımlar
                        // çapraz eşitlemede "inceleme gerekli" diye atlanıyordu.
                        year = entry.startYear
                    )
                )
            }
            result
        }
    }
}

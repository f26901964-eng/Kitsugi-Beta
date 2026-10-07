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
        val errors: List<String> = emptyList(),
        /**
         * True when the record was skipped because no trustworthy Kitsu media ID could be
         * resolved (not an API failure). Callers should report this as a skip, not an error.
         */
        val unresolvedMedia: Boolean = false,
        /** Which resolver produced the Kitsu media ID (diagnostics for the report). */
        val resolvedVia: String? = null
    )

    fun watchStatusToKitsu(status: WatchStatus?): String = when (status) {
        WatchStatus.Watching, WatchStatus.Repeating -> "current"
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

    private data class ResolvedKitsuMedia(val id: Int, val via: String)

    /**
     * Kitsu medya ID'sini güvenilirlikten düşüğe doğru sırayla çözer:
     * bilinen ID → yerel MAL eşleme önbelleği → Kitsu `mappings` (MAL, AniList) → ARM → sıkı başlık araması.
     * Hiçbiri sonuç vermezse null döner; bu bir API hatası değil, "eşleştirilemedi" durumudur.
     */
    private suspend fun resolveKitsuMediaId(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int?,
        isAnime: Boolean
    ): ResolvedKitsuMedia? {
        knownKitsuMediaId?.takeIf { it > 0 }?.let { return ResolvedKitsuMedia(it, "bilinen Kitsu ID") }

        val realMalId = entry.malId?.takeIf { it in 1..99_999_999 }
        realMalId?.let { ExternalAuthManager.getKitsuMediaIdForMal(context, it, isAnime) }
            ?.takeIf { it > 0 }
            ?.let { return ResolvedKitsuMedia(it, "önbellek (MAL→Kitsu)") }

        if (entry.malId != null && entry.malId >= KITSU_OFFSET && entry.malId < 400_000_000) {
            return ResolvedKitsuMedia(entry.malId - KITSU_OFFSET, "Kitsu kaynaklı kayıt")
        }

        val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < KITSU_OFFSET) {
            entry.malId - 100_000_000
        } else null
        val typeSegment = if (isAnime) "anime" else "manga"

        // Resmi Kitsu mappings tablosu: MAL ID (önce), sonra AniList ID
        if (realMalId != null) {
            val mapped = runSyncCatching {
                KitsuApiClient.lookupKitsuIdByExternalMapping("myanimelist/$typeSegment", realMalId)
            }.getOrNull()
            if (mapped != null && mapped > 0) {
                ExternalAuthManager.saveKitsuMediaIdForMal(context, realMalId, isAnime, mapped)
                return ResolvedKitsuMedia(mapped, "Kitsu mappings (MAL)")
            }
        }
        if (rawAniListId != null) {
            val mapped = runSyncCatching {
                KitsuApiClient.lookupKitsuIdByExternalMapping("anilist/$typeSegment", rawAniListId)
            }.getOrNull()
            if (mapped != null && mapped > 0) {
                return ResolvedKitsuMedia(mapped, "Kitsu mappings (AniList)")
            }
        }

        // ARM (yalnızca anime için veri içerir). GÜVENLİK: TMDB kimliği verilmez — film/dizi kimlik uzayı
        // çakışması ve sezon gruplaması yüzünden yanlış yapımın Kitsu kimliği dönüyordu; yalnızca birebir
        // kimlikler (gerçek MAL / AniList) kullanılır.
        if (isAnime && (realMalId != null || rawAniListId != null)) {
            val armKitsu = runSyncCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                    malId = realMalId,
                    aniListId = rawAniListId,
                    tmdbId = null,
                    mediaType = entry.type
                ).kitsuId
            }.getOrNull()
            if (armKitsu != null && armKitsu > 0) {
                if (realMalId != null) ExternalAuthManager.saveKitsuMediaIdForMal(context, realMalId, isAnime, armKitsu)
                return ResolvedKitsuMedia(armKitsu, "ARM")
            }
        }

        // Son çare: sıkı başlık araması (belirsiz eşleşmelerde null döner)
        val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title
        var byTitle = KitsuApiClient.lookupKitsuId(searchTitle, isAnime = isAnime, expectedYear = entry.year)
        if (byTitle == null && !entry.titleEnglish.isNullOrBlank() && entry.title.isNotBlank() && entry.title != searchTitle) {
            byTitle = KitsuApiClient.lookupKitsuId(entry.title, isAnime = isAnime, expectedYear = entry.year)
        }
        if (byTitle != null && byTitle > 0) {
            if (realMalId != null) ExternalAuthManager.saveKitsuMediaIdForMal(context, realMalId, isAnime, byTitle)
            return ResolvedKitsuMedia(byTitle, "başlık araması")
        }
        return null
    }

    /**
     * Bir MediaEntry'yi kullanıcının Kitsu kütüphanesine senkronize eder.
     */
    suspend fun syncEntryToKitsu(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf("Bu servis yalnızca anime/manga destekler"))
        }

        val token = ExternalAuthManager.getKitsuToken(context)
        val userId = ExternalAuthManager.getKitsuUserId(context)

        if (token.isNullOrBlank() || userId.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Kitsu hesabı bağlı değil"))
        }

        // Kitsu sadece Anime ve Manga destekler
        val isAnime = entry.type != MediaType.Manga

        // 1. Kitsu Media Numeric ID'sini bul
        val resolved = resolveKitsuMediaId(context, entry, knownKitsuMediaId, isAnime)
        if (resolved == null) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf("Kitsu'da eşleşen kayıt bulunamadı: ${entry.title}"),
                unresolvedMedia = true
            )
        }
        val kitsuMediaId = resolved.id

        val status = watchStatusToKitsu(entry.status)
        val progress = entry.progress
        val ratingTwenty = SyncScores.kitsuTwenty(entry.score)

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // A stale cached library-entry ID is not evidence that a remote record exists.
        val existingEntryId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
        if (existingEntryId != null) {
            ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, existingEntryId)
        }

        if (existingEntryId != null) {
            val result = KitsuApiClient.updateLibraryEntryDetailed(
                token = token,
                entryId = existingEntryId,
                status = status,
                progress = progress,
                ratingTwenty = ratingTwenty
            )
            if (result.success) messages.add("Kitsu güncellendi (${entry.title})")
            else errors.add("Kitsu güncellenemedi (${entry.title}): ${result.errorMessage ?: "bilinmeyen hata"}")
        } else {
            val created = KitsuApiClient.createLibraryEntryDetailed(
                token = token,
                userId = userId,
                kitsuMediaId = kitsuMediaId,
                isAnime = isAnime,
                status = status,
                progress = progress,
                ratingTwenty = ratingTwenty
            )
            if (created.success && created.entryId != null) {
                ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, created.entryId)
                messages.add("Kitsu kütüphanesine eklendi (${entry.title})")
            } else {
                // Kayıt zaten Kitsu'da var olabilir (HTTP 422 "already exists") -> ID'yi bulup güncellemeyi dene
                val fallbackId = runSyncCatching {
                    KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
                }.getOrNull()
                if (fallbackId != null) {
                    ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, fallbackId)
                    val updated = KitsuApiClient.updateLibraryEntryDetailed(
                        token = token,
                        entryId = fallbackId,
                        status = status,
                        progress = progress,
                        ratingTwenty = ratingTwenty
                    )
                    if (updated.success) messages.add("Kitsu güncellendi (${entry.title})")
                    else errors.add("Kitsu güncellenemedi (${entry.title}): ${updated.errorMessage ?: "bilinmeyen hata"}")
                } else {
                    errors.add("Kitsu kütüphanesine eklenemedi (${entry.title}): ${created.errorMessage ?: "bilinmeyen hata"} [kitsu=$kitsuMediaId, ${resolved.via}]")
                }
            }
        }

        SyncResult(messages = messages, errors = errors, resolvedVia = resolved.via)
    }

    /**
     * Kitsu kütüphanesinden kaydı siler.
     */
    suspend fun deleteEntryFromKitsu(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf("Bu servis yalnızca anime/manga destekler"))
        }

        val token = ExternalAuthManager.getKitsuToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Kitsu hesabı bağlı değil"))
        }

        val isAnime = entry.type != MediaType.Manga
        var kitsuMediaId: Int? = entry.malId?.takeIf { it in 1..99_999_999 }?.let {
            ExternalAuthManager.getKitsuMediaIdForMal(context, it, isAnime)
        }
        if (entry.malId != null && entry.malId >= KITSU_OFFSET && entry.malId < 400_000_000) {
            kitsuMediaId = entry.malId - KITSU_OFFSET
        }

        val existingEntryId = if (kitsuMediaId != null) {
            val userId = ExternalAuthManager.getKitsuUserId(context) ?: error("Kitsu kullanıcı kimliği yok")
            KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
        } else null

        if (existingEntryId != null) {
            val success = KitsuApiClient.deleteLibraryEntry(token, existingEntryId)
            if (success) ExternalAuthManager.removeKitsuLibraryEntryId(context, kitsuMediaId!!, isAnime)
            if (success) SyncResult(messages = listOf("Kitsu kütüphanesinden silindi (${entry.title})"))
            else SyncResult(messages = emptyList(), errors = listOf("Kitsu'dan silinemedi"))
        } else {
            if (kitsuMediaId != null) SyncResult(messages = listOf("Kitsu kaydı zaten yok (${entry.title})"))
            else SyncResult(messages = emptyList(), errors = listOf("Kitsu medya kimliği bulunamadı"))
        }
    }
}

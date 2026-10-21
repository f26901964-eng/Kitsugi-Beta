package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.KitsuIdNamespace
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
        knownKitsuMediaId?.takeIf { it > 0 }?.let { return ResolvedKitsuMedia(it, context.getString(R.string.kitsu_sync_via_known_id)) }

        val realMalId = entry.malId?.takeIf { it in 1..99_999_999 }
        realMalId?.let { ExternalAuthManager.getKitsuMediaIdForMal(context, it, isAnime) }
            ?.takeIf { it > 0 }
            ?.let { return ResolvedKitsuMedia(it, context.getString(R.string.kitsu_sync_via_mal_cache)) }

        KitsuIdNamespace.rawIdFromStable(entry.malId)?.let {
            return ResolvedKitsuMedia(it, context.getString(R.string.kitsu_sync_via_kitsu_entry))
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
                return ResolvedKitsuMedia(mapped, context.getString(R.string.kitsu_sync_via_mal_mapping))
            }
        }
        if (rawAniListId != null) {
            val mapped = runSyncCatching {
                KitsuApiClient.lookupKitsuIdByExternalMapping("anilist/$typeSegment", rawAniListId)
            }.getOrNull()
            if (mapped != null && mapped > 0) {
                return ResolvedKitsuMedia(mapped, context.getString(R.string.kitsu_sync_via_anilist_mapping))
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
                return ResolvedKitsuMedia(armKitsu, context.getString(R.string.kitsu_sync_via_arm))
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
            return ResolvedKitsuMedia(byTitle, context.getString(R.string.kitsu_sync_via_title_search))
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
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_anime_manga_only)))
        }

        val token = ExternalAuthManager.getKitsuToken(context)
        val userId = ExternalAuthManager.getKitsuUserId(context)

        if (token.isNullOrBlank() || userId.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Kitsu")))
        }

        // Kitsu sadece Anime ve Manga destekler
        val isAnime = entry.type != MediaType.Manga

        // 1. Kitsu Media Numeric ID'sini bul
        val resolved = resolveKitsuMediaId(context, entry, knownKitsuMediaId, isAnime)
        if (resolved == null) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_kitsu_match_not_found, entry.title)),
                unresolvedMedia = true
            )
        }
        val kitsuMediaId = resolved.id

        val status = watchStatusToKitsu(entry.status)
        val ratingTwenty = SyncScores.kitsuTwenty(entry.score)
        // Tarih / not / gizlilik: yalnızca dolu değerler gönderilir (boş alan uzaktaki değeri silmez).
        val kitsuExtras = KitsuApiClient.KitsuEntryExtras(
            startedAt = KitsuApiClient.toKitsuDateTime(entry.startDate),
            finishedAt = KitsuApiClient.toKitsuDateTime(entry.endDate),
            notes = entry.notes?.takeIf { it.isNotBlank() },
            isPrivate = entry.isPrivate
        )

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Birleştirilmiş ilerleme (ör. AniList 13/13) Kitsu'daki bölüm sayısından (12) büyük olabilir;
        // Kitsu bunu 422 "cannot exceed length of media" ile reddeder. Böyle bir hatada medya uzunluğunu
        // alıp ilerlemeyi o sınıra indirerek bir kez daha yazarız. Kayıt ve durum değişmez.
        var cappedProgressTo: Int? = null
        suspend fun writeWithLengthGuard(
            write: suspend (Int) -> KitsuApiClient.KitsuWriteResult
        ): KitsuApiClient.KitsuWriteResult {
            val first = write(entry.progress)
            if (first.success || !KitsuApiClient.isMediaLengthError(first.errorMessage)) return first
            val cap = KitsuApiClient.fetchMediaLength(kitsuMediaId, isAnime) ?: return first
            if (cap >= entry.progress) return first
            val capped = write(cap)
            if (capped.success) cappedProgressTo = cap
            return capped
        }

        // A stale cached library-entry ID is not evidence that a remote record exists.
        val existingEntryId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
        if (existingEntryId != null) {
            ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, existingEntryId)
        }

        val cappedNote = {
            cappedProgressTo?.let { context.getString(R.string.kitsu_sync_progress_capped, entry.progress, it) }.orEmpty()
        }

        if (existingEntryId != null) {
            val result = writeWithLengthGuard { p ->
                KitsuApiClient.updateLibraryEntryDetailed(
                    token = token,
                    entryId = existingEntryId,
                    status = status,
                    progress = p,
                    ratingTwenty = ratingTwenty,
                    extras = kitsuExtras
                )
            }
            if (result.success) messages.add(context.getString(R.string.account_sync_entry_updated, "Kitsu", entry.title) + cappedNote())
            else errors.add(context.getString(R.string.account_sync_entry_update_failed, "Kitsu", entry.title, result.errorMessage ?: context.getString(R.string.account_sync_unknown_error)))
        } else {
            val created = writeWithLengthGuard { p ->
                KitsuApiClient.createLibraryEntryDetailed(
                    token = token,
                    userId = userId,
                    kitsuMediaId = kitsuMediaId,
                    isAnime = isAnime,
                    status = status,
                    progress = p,
                    ratingTwenty = ratingTwenty,
                    extras = kitsuExtras
                )
            }
            if (created.success && created.entryId != null) {
                ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, created.entryId)
                messages.add(context.getString(R.string.account_sync_entry_added, "Kitsu", entry.title) + cappedNote())
            } else {
                // Kayıt zaten Kitsu'da var olabilir (HTTP 422 "already exists") -> ID'yi bulup güncellemeyi dene
                val fallbackId = runSyncCatching {
                    KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)
                }.getOrNull()
                if (fallbackId != null) {
                    ExternalAuthManager.saveKitsuLibraryEntryId(context, kitsuMediaId, isAnime, fallbackId)
                    val updated = writeWithLengthGuard { p ->
                        KitsuApiClient.updateLibraryEntryDetailed(
                            token = token,
                            entryId = fallbackId,
                            status = status,
                            progress = p,
                            ratingTwenty = ratingTwenty,
                            extras = kitsuExtras
                        )
                    }
                    if (updated.success) messages.add(context.getString(R.string.account_sync_entry_updated, "Kitsu", entry.title) + cappedNote())
                    else errors.add(context.getString(R.string.account_sync_entry_update_failed, "Kitsu", entry.title, updated.errorMessage ?: context.getString(R.string.account_sync_unknown_error)))
                } else {
                    errors.add(context.getString(R.string.account_sync_entry_add_failed, "Kitsu", entry.title, created.errorMessage ?: context.getString(R.string.account_sync_unknown_error), kitsuMediaId, resolved.via))
                }
            }
        }

        SyncResult(messages = messages, errors = errors, resolvedVia = resolved.via)
    }

    /**
     * Kitsu kütüphanesinden kaydı siler.
     *
     * Medya kimliği [syncEntryToKitsu] ile AYNI güvenilirlik zincirinden çözülür (bilinen
     * kimlik → yerel eşleme önbelleği → Kitsu mappings → ARM → sıkı başlık araması). Eski
     * davranış yalnızca satırın kendi namespace'ini ve MAL önbelleğini kontrol ediyordu;
     * çapraz eşitlemede [CrossProviderIdentityResolver]'ın çözdüğü Kitsu kimliği yok sayılıyor
     * ve "medya kimliği bulunamadı" bir **hata** olarak raporlanıyordu — silme işlemi haksız
     * yere "Çevrimdışı kaydedildi" kuyruğuna düşüyordu.
     *
     * @param knownKitsuMediaId Çapraz kimlik katmanının önceden çözdüğü Kitsu medya kimliği.
     */
    suspend fun deleteEntryFromKitsu(
        context: Context,
        entry: MediaEntry,
        knownKitsuMediaId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_anime_manga_only)))
        }

        val token = ExternalAuthManager.getKitsuToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_account_not_connected, "Kitsu")))
        }

        val isAnime = entry.type != MediaType.Manga

        val resolved = resolveKitsuMediaId(context, entry, knownKitsuMediaId, isAnime)
        if (resolved == null) {
            // Eşleştirilemedi: katalogda karşılığı olmayan ya da hiçbir güvenilir kimlikle
            // bulunamayan kayıt. Hata değil atlandı — kuyrukta yeniden denenmeyecek.
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf(context.getString(R.string.account_sync_media_id_not_found, "Kitsu")),
                unresolvedMedia = true
            )
        }
        val kitsuMediaId = resolved.id

        val userId = ExternalAuthManager.getKitsuUserId(context)
            ?: error(context.getString(R.string.account_sync_kitsu_user_id_missing))
        val existingEntryId = KitsuApiClient.findLibraryEntryId(token, userId, kitsuMediaId, isAnime)

        if (existingEntryId != null) {
            val success = KitsuApiClient.deleteLibraryEntry(token, existingEntryId)
            if (success) ExternalAuthManager.removeKitsuLibraryEntryId(context, kitsuMediaId, isAnime)
            if (success) SyncResult(messages = listOf(context.getString(R.string.account_sync_entry_deleted, "Kitsu", entry.title)))
            else SyncResult(messages = emptyList(), errors = listOf(context.getString(R.string.account_sync_entry_delete_failed, "Kitsu")))
        } else {
            // Medya bulunuyor ama kullanıcının kütüphanesinde kayıt yok: silme zaten amaca
            // ulaştı (idempotent başarı sayılır).
            SyncResult(messages = listOf(context.getString(R.string.account_sync_entry_already_missing, "Kitsu", entry.title)))
        }
    }
}

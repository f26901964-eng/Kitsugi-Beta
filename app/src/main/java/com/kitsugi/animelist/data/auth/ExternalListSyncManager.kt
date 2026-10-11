package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.local.toEntity

object ExternalListSyncManager {
    private const val ANILIST_SYNTHETIC_ID_OFFSET = 100_000_000

    private fun Context.sourceFailure(platform: String, error: Throwable): String =
        getString(
            R.string.account_sync_source_failure,
            platform,
            error.message ?: getString(R.string.account_sync_unknown_error)
        )

    suspend fun syncEntry(
        context: Context,
        entry: MediaEntry,
        advancedScores: List<Double>? = null
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            // A Bangumi-origin row represents membership in that account's own collection.
            // Editing it must not fan out to unrelated providers using its subject ID.
            if (canonicalMediaSourceId(entry.source) == BangumiIdNamespace.SOURCE) {
                val settings = runSyncCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
                if (settings?.syncEnabledBangumi == false) return@withContext SyncResult(emptyList())
                val result = BangumiSyncManager.syncEntryToBangumi(context, entry)
                return@withContext SyncResult(result.messages, result.errors)
            }

            val messages = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val syncedSources = mutableListOf<String>()
            var resolvedAniListEntryId: Int? = null

            val settings = runSyncCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
            val syncEnabledAniList = settings?.syncEnabledAnilist ?: true
            val syncEnabledMal = settings?.syncEnabledMal ?: true
            val syncEnabledSimkl = settings?.syncEnabledSimkl ?: true
            val syncEnabledKitsu = settings?.syncEnabledKitsu ?: true
            val syncEnabledShikimori = settings?.syncEnabledShikimori ?: true
            val syncEnabledBangumi = settings?.syncEnabledBangumi ?: true

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            val bangumiToken = if (syncEnabledBangumi) BangumiAuthStore.getValidToken(context) else null

            var realMalId = entry.malId?.takeIf { it.isRealMalId() }

            // 1. Gerçek MAL ID eksikse ancak anime/manga ise çözmeyi dene (AniList -> MAL / Simkl -> MAL eşitlemesi için)
            val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga
            if (realMalId == null && isAnimeOrManga) {
                val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= ANILIST_SYNTHETIC_ID_OFFSET) {
                    entry.malId - ANILIST_SYNTHETIC_ID_OFFSET
                } else null

                val rawKitsuId = entry.malId?.takeIf { it in 300_000_001..399_999_999 }?.minus(300_000_000)

                if (rawAniListId != null) {
                    val resolvedMal = runSyncCatching {
                        AniListSyncManager.resolveMalIdFromAniList(token = aniListToken, aniListId = rawAniListId)
                    }.getOrNull()
                    if (resolvedMal != null && resolvedMal.isRealMalId()) {
                        realMalId = resolvedMal
                    }
                }

                // GÜVENLİK: ARM'a TMDB kimliği verilmez (film/dizi kimlik uzayı çakışması + sezon gruplaması
                // yanlış MAL ID döndürüyor ve bu kimlik aşağıda yerel veritabanına da kalıcı yazılıyordu).
                // Yalnızca birebir kimlikler (AniList / Kitsu) kullanılır.
                if (realMalId == null && (rawAniListId != null || rawKitsuId != null)) {
                    val armMal = runSyncCatching {
                        com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                            malId = null,
                            aniListId = rawAniListId,
                            tmdbId = null,
                            mediaType = entry.type,
                            kitsuId = rawKitsuId
                        ).malId
                    }.getOrNull()
                    if (armMal != null && armMal.isRealMalId()) {
                        realMalId = armMal
                    }
                }

                if (realMalId == null) {
                    val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title.takeIf { it.isNotBlank() }
                    if (!searchTitle.isNullOrBlank()) {
                        val jikanResults = runSyncCatching {
                            com.kitsugi.animelist.data.remote.JikanSearchClient().searchMALOnly(
                                query = searchTitle,
                                mediaType = entry.type
                            )
                        }.getOrNull()
                        val jikanMal = jikanResults?.filter { res ->
                res.type == entry.type && (entry.year == null || res.year == entry.year) &&
                    listOfNotNull(res.title, res.titleEnglish, res.titleJapanese).any {
                        com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) ==
                            com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(searchTitle)
                    }
            }?.singleOrNull()?.let { res -> (res.realMalId ?: res.malId).takeIf { it.isRealMalId() } }
                        if (jikanMal != null) {
                            realMalId = jikanMal
                        }
                    }
                }
            }

            // Eğer gerçek MAL ID yeni çözüldüyse yerel veritabanına da kaydet
            val effectiveEntry = if (realMalId != null && realMalId != entry.malId) {
                val updated = entry.copy(malId = realMalId)
                runSyncCatching {
                    val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                    dao.update(updated.toEntity())
                }
                updated
            } else {
                entry
            }

            // ── AniList Senkronizasyonu (Anime & Manga) ──────────────────────────
            val shouldSyncAniList = syncEnabledAniList && !aniListToken.isNullOrBlank() && isAnimeOrManga

            if (shouldSyncAniList) {
                runSyncCatching {
                    AniListSyncManager.updateAniListEntry(
                        token = aniListToken!!,
                        entry = effectiveEntry,
                        advancedScores = advancedScores,
                        context = context
                    )
                }.onSuccess { remoteId ->
                    if (remoteId != null && remoteId > 0) {
                        resolvedAniListEntryId = remoteId
                        syncedSources.add("AniList")
                        // Yeni dönen aniListEntryId'yi yerel DB'ye yaz
                        if (effectiveEntry.aniListEntryId != remoteId) {
                            runSyncCatching {
                                val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                                dao.update(effectiveEntry.copy(aniListEntryId = remoteId).toEntity())
                            }
                        }
                    } else {
                        errors.add(context.getString(R.string.account_sync_anilist_update_unconfirmed))
                    }
                }.onFailure { error ->
                    errors.add(context.sourceFailure("AniList", error))
                }
            }

            // AniList favori durumu senkronizasyonu
            if (syncEnabledAniList && !aniListToken.isNullOrBlank() && isAnimeOrManga) {
                runSyncCatching {
                    val remoteFav = AniListSyncManager.getAniListMediaFavoriteStatus(aniListToken, effectiveEntry, context)
                    if (remoteFav != null && remoteFav != effectiveEntry.isFavorite) {
                        check(AniListSyncManager.toggleAniListFavourite(aniListToken, effectiveEntry, context)) { context.getString(R.string.account_sync_anilist_favorite_unconfirmed) }
                        if (!syncedSources.contains("AniList")) {
                            syncedSources.add("AniList")
                        }
                    }
                }.onFailure { error -> errors.add(context.getString(R.string.account_sync_favorite_error, error.message ?: context.getString(R.string.account_sync_unknown_error))) }
            }

            // ── MyAnimeList (MAL) Senkronizasyonu (Anime & Manga) ────────────────
            val shouldSyncMal = syncEnabledMal && !malToken.isNullOrBlank() && realMalId != null && isAnimeOrManga

            if (shouldSyncMal && realMalId != null) {
                runSyncCatching {
                    MalSyncManager.updateMalEntry(
                        token = malToken!!,
                        entry = effectiveEntry,
                        context = context
                    )
                }.onSuccess {
                    syncedSources.add("MyAnimeList")
                }.onFailure { error ->
                    errors.add(context.sourceFailure("MAL", error))
                }
            }

            // ── Simkl Senkronizasyonu (Anime, Film, Dizi — Tümü) ──────────────────
            val shouldSyncSimkl = syncEnabledSimkl && !simklToken.isNullOrBlank() && entry.type != MediaType.Manga

            if (shouldSyncSimkl) {
                runSyncCatching {
                    SimklSyncManager.syncEntryToSimkl(context, effectiveEntry)
                }.onSuccess { simklResult ->
                    if (simklResult.isSuccess) {
                        syncedSources.add("Simkl")
                    } else {
                        errors.addAll(simklResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.sourceFailure("Simkl", error))
                }
            }

            // ── Kitsu Senkronizasyonu (Anime & Manga) ───────────────────────────
            val shouldSyncKitsu = syncEnabledKitsu && !kitsuToken.isNullOrBlank() && isAnimeOrManga

            if (shouldSyncKitsu) {
                runSyncCatching {
                    KitsuSyncManager.syncEntryToKitsu(context, effectiveEntry)
                }.onSuccess { kitsuResult ->
                    if (kitsuResult.errors.isEmpty()) {
                        syncedSources.add("Kitsu")
                    } else {
                        errors.addAll(kitsuResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.sourceFailure("Kitsu", error))
                }
            }

            // ── Shikimori Senkronizasyonu (Anime & Manga) ────────────────────────
            val shouldSyncShikimori = syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && isAnimeOrManga

            if (shouldSyncShikimori) {
                runSyncCatching {
                    ShikimoriSyncManager.syncEntryToShikimori(context, effectiveEntry)
                }.onSuccess { shikiResult ->
                    if (shikiResult.errors.isEmpty()) {
                        syncedSources.add("Shikimori")
                    } else {
                        errors.addAll(shikiResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.sourceFailure("Shikimori", error))
                }
            }

            // ── Bangumi Senkronizasyonu (Anime & Kitap/Manga) ────────────────────
            val shouldSyncBangumi = syncEnabledBangumi && !bangumiToken.isNullOrBlank() && BangumiSyncManager.supportsMediaType(entry.type)

            if (shouldSyncBangumi) {
                runSyncCatching {
                    BangumiSyncManager.syncEntryToBangumi(context, effectiveEntry)
                }.onSuccess { bangumiResult ->
                    if (bangumiResult.errors.isEmpty()) {
                        syncedSources.add("Bangumi")
                    } else {
                        errors.addAll(bangumiResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.sourceFailure("Bangumi", error))
                }
            }

            if (syncEnabledMal && !malToken.isNullOrBlank() && isAnimeOrManga && realMalId == null) {
                errors.add(context.getString(R.string.account_sync_no_valid_mal_id))
            }
            val formatted = formatSyncSources(context, syncedSources, isDelete = false)
            if (formatted.isNotEmpty()) {
                messages.add(formatted)
            }

            SyncResult(
                messages = messages,
                errors = errors,
                aniListEntryId = resolvedAniListEntryId
            )
        }
    }

    suspend fun deleteEntry(
        context: Context,
        entry: MediaEntry
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            if (canonicalMediaSourceId(entry.source) == BangumiIdNamespace.SOURCE) {
                val settings = runSyncCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
                if (settings?.syncEnabledBangumi == false) return@withContext SyncResult(emptyList())
                val result = BangumiSyncManager.deleteEntryFromBangumi(context, entry)
                return@withContext SyncResult(result.messages, result.errors)
            }

            val messages = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val deletedSources = mutableListOf<String>()

            val settings = runSyncCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
            val syncEnabledAniList = settings?.syncEnabledAnilist ?: true
            val syncEnabledMal = settings?.syncEnabledMal ?: true
            val syncEnabledSimkl = settings?.syncEnabledSimkl ?: true
            val syncEnabledKitsu = settings?.syncEnabledKitsu ?: true
            val syncEnabledShikimori = settings?.syncEnabledShikimori ?: true
            val syncEnabledBangumi = settings?.syncEnabledBangumi ?: true

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            val bangumiToken = if (syncEnabledBangumi) BangumiAuthStore.getValidToken(context) else null

            val realMalId = entry.malId?.takeIf { it.isRealMalId() }
            val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga

            val shouldDeleteAniList = syncEnabledAniList && !aniListToken.isNullOrBlank() && isAnimeOrManga
            val shouldDeleteMal = syncEnabledMal && !malToken.isNullOrBlank() && realMalId != null && isAnimeOrManga
            val shouldDeleteSimkl = syncEnabledSimkl && !simklToken.isNullOrBlank() && entry.type != MediaType.Manga
            val shouldDeleteKitsu = syncEnabledKitsu && !kitsuToken.isNullOrBlank() && isAnimeOrManga
            val shouldDeleteShikimori = syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && isAnimeOrManga
            val shouldDeleteBangumi = syncEnabledBangumi && !bangumiToken.isNullOrBlank() && BangumiSyncManager.supportsMediaType(entry.type)

            if (shouldDeleteAniList) {
                runSyncCatching {
                    AniListSyncManager.deleteAniListEntry(
                        token = aniListToken!!,
                        entry = entry,
                        context = context
                    )
                }.onSuccess {
                    deletedSources.add("AniList")
                }.onFailure { error ->
                    errors.add(context.getString(R.string.account_sync_delete_error, "AniList", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }

            if (shouldDeleteMal && realMalId != null) {
                runSyncCatching {
                    MalSyncManager.deleteMalEntry(
                        token = malToken!!,
                        entry = entry,
                        context = context
                    )
                }.onSuccess {
                    deletedSources.add("MyAnimeList")
                }.onFailure { error ->
                    errors.add(context.getString(R.string.account_sync_delete_error, "MAL", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }

            if (shouldDeleteSimkl) {
                runSyncCatching {
                    SimklSyncManager.deleteEntryFromSimkl(context, entry)
                }.onSuccess { simklResult ->
                    if (simklResult.isSuccess) {
                        deletedSources.add("Simkl")
                    } else {
                        errors.addAll(simklResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.getString(R.string.account_sync_delete_error, "Simkl", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }

            if (shouldDeleteKitsu) {
                runSyncCatching {
                    KitsuSyncManager.deleteEntryFromKitsu(context, entry)
                }.onSuccess { kitsuResult ->
                    if (kitsuResult.errors.isEmpty()) {
                        deletedSources.add("Kitsu")
                    } else {
                        errors.addAll(kitsuResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.getString(R.string.account_sync_delete_error, "Kitsu", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }

            if (shouldDeleteShikimori) {
                runSyncCatching {
                    ShikimoriSyncManager.deleteEntryFromShikimori(context, entry)
                }.onSuccess { shikiResult ->
                    if (shikiResult.errors.isEmpty()) {
                        deletedSources.add("Shikimori")
                    } else {
                        errors.addAll(shikiResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.getString(R.string.account_sync_delete_error, "Shikimori", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }

            if (shouldDeleteBangumi) {
                runSyncCatching {
                    BangumiSyncManager.deleteEntryFromBangumi(context, entry)
                }.onSuccess { bangumiResult ->
                    if (bangumiResult.errors.isEmpty()) {
                        deletedSources.add("Bangumi")
                    } else {
                        errors.addAll(bangumiResult.errors)
                    }
                }.onFailure { error ->
                    errors.add(context.getString(R.string.account_sync_delete_error, "Bangumi", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            }

            val formatted = formatSyncSources(context, deletedSources, isDelete = true)
            if (formatted.isNotEmpty()) {
                messages.add(formatted)
            }

            SyncResult(
                messages = messages,
                errors = errors
            )
        }
    }

    private fun formatSyncSources(context: Context, sources: List<String>, isDelete: Boolean): String {
        if (sources.isEmpty()) return ""
        val joined = when (sources.size) {
            1 -> sources[0]
            2 -> context.getString(R.string.account_sync_sources_join_two, sources[0], sources[1])
            else -> {
                val last = sources.last()
                val remaining = sources.dropLast(1).joinToString(", ")
                context.getString(R.string.account_sync_sources_join_many, remaining, last)
            }
        }
        return if (isDelete) {
            context.getString(R.string.account_sync_sources_deleted, joined)
        } else {
            context.getString(R.string.account_sync_sources_synced, joined)
        }
    }

    private fun Int.isRealMalId(): Boolean {
        return this > 0 && this < ANILIST_SYNTHETIC_ID_OFFSET
    }

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        val aniListEntryId: Int? = null
    )
}
package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.local.toEntity

object ExternalListSyncManager {
    private const val ANILIST_SYNTHETIC_ID_OFFSET = 100_000_000

    suspend fun syncEntry(
        context: Context,
        entry: MediaEntry,
        advancedScores: List<Double>? = null
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            val messages = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val syncedSources = mutableListOf<String>()
            var resolvedAniListEntryId: Int? = null

            val settings = runCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
            val syncEnabledAniList = settings?.syncEnabledAnilist ?: true
            val syncEnabledMal = settings?.syncEnabledMal ?: true
            val syncEnabledSimkl = settings?.syncEnabledSimkl ?: true
            val syncEnabledKitsu = settings?.syncEnabledKitsu ?: true
            val syncEnabledShikimori = settings?.syncEnabledShikimori ?: true

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)

            var realMalId = entry.malId?.takeIf { it.isRealMalId() }

            // 1. Gerçek MAL ID eksikse ancak anime/manga ise çözmeyi dene (AniList -> MAL / Simkl -> MAL eşitlemesi için)
            val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga
            if (realMalId == null && isAnimeOrManga) {
                val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= ANILIST_SYNTHETIC_ID_OFFSET) {
                    entry.malId - ANILIST_SYNTHETIC_ID_OFFSET
                } else null

                val rawKitsuId = if (entry.source == "kitsu" || (entry.malId != null && entry.malId >= 300_000_000)) {
                    if (entry.malId != null && entry.malId >= 300_000_000) entry.malId - 300_000_000 else entry.malId
                } else null

                if (rawAniListId != null) {
                    val resolvedMal = runCatching {
                        AniListSyncManager.resolveMalIdFromAniList(token = aniListToken, aniListId = rawAniListId)
                    }.getOrNull()
                    if (resolvedMal != null && resolvedMal.isRealMalId()) {
                        realMalId = resolvedMal
                    }
                }

                if (realMalId == null) {
                    val armMal = runCatching {
                        com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                            malId = null,
                            aniListId = rawAniListId,
                            tmdbId = entry.tmdbId,
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
                        val jikanResults = runCatching {
                            com.kitsugi.animelist.data.remote.JikanSearchClient().searchMALOnly(
                                query = searchTitle,
                                mediaType = entry.type
                            )
                        }.getOrNull()
                        val jikanMal = jikanResults?.firstOrNull()?.let { res -> (res.realMalId ?: res.malId).takeIf { it.isRealMalId() } }
                        if (jikanMal != null) {
                            realMalId = jikanMal
                        }
                    }
                }
            }

            // Eğer gerçek MAL ID yeni çözüldüyse yerel veritabanına da kaydet
            val effectiveEntry = if (realMalId != null && realMalId != entry.malId) {
                val updated = entry.copy(malId = realMalId)
                runCatching {
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
                runCatching {
                    AniListSyncManager.updateAniListEntry(
                        token = aniListToken!!,
                        entry = effectiveEntry,
                        advancedScores = advancedScores
                    )
                }.onSuccess { remoteId ->
                    if (remoteId != null && remoteId > 0) {
                        resolvedAniListEntryId = remoteId
                        syncedSources.add("AniList")
                        // Yeni dönen aniListEntryId'yi yerel DB'ye yaz
                        if (effectiveEntry.aniListEntryId != remoteId) {
                            runCatching {
                                val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                                dao.update(effectiveEntry.copy(aniListEntryId = remoteId).toEntity())
                            }
                        }
                    }
                }.onFailure { error ->
                    errors.add("AniList: ${error.message}")
                }
            }

            // AniList favori durumu senkronizasyonu
            if (syncEnabledAniList && !aniListToken.isNullOrBlank() && isAnimeOrManga) {
                runCatching {
                    val remoteFav = AniListSyncManager.getAniListMediaFavoriteStatus(aniListToken, effectiveEntry)
                    if (remoteFav != null && remoteFav != effectiveEntry.isFavorite) {
                        AniListSyncManager.toggleAniListFavourite(aniListToken, effectiveEntry)
                        if (!syncedSources.contains("AniList")) {
                            syncedSources.add("AniList")
                        }
                    }
                }
            }

            // ── MyAnimeList (MAL) Senkronizasyonu (Anime & Manga) ────────────────
            val shouldSyncMal = syncEnabledMal && !malToken.isNullOrBlank() && realMalId != null && isAnimeOrManga

            if (shouldSyncMal && realMalId != null) {
                runCatching {
                    MalSyncManager.updateMalEntry(
                        token = malToken!!,
                        entry = effectiveEntry
                    )
                }.onSuccess {
                    syncedSources.add("MyAnimeList")
                }.onFailure { error ->
                    errors.add("MAL: ${error.message}")
                }
            }

            // ── Simkl Senkronizasyonu (Anime, Film, Dizi — Tümü) ──────────────────
            val shouldSyncSimkl = syncEnabledSimkl && !simklToken.isNullOrBlank()

            if (shouldSyncSimkl) {
                runCatching {
                    SimklSyncManager.syncEntryToSimkl(context, effectiveEntry)
                }.onSuccess { simklResult ->
                    if (simklResult.isSuccess) {
                        syncedSources.add("Simkl")
                    } else {
                        errors.addAll(simklResult.errors)
                    }
                }.onFailure { error ->
                    errors.add("Simkl: ${error.message}")
                }
            }

            // ── Kitsu Senkronizasyonu (Anime & Manga) ───────────────────────────
            val shouldSyncKitsu = syncEnabledKitsu && !kitsuToken.isNullOrBlank() && isAnimeOrManga

            if (shouldSyncKitsu) {
                runCatching {
                    KitsuSyncManager.syncEntryToKitsu(context, effectiveEntry)
                }.onSuccess { kitsuResult ->
                    if (kitsuResult.errors.isEmpty()) {
                        syncedSources.add("Kitsu")
                    } else {
                        errors.addAll(kitsuResult.errors)
                    }
                }.onFailure { error ->
                    errors.add("Kitsu: ${error.message}")
                }
            }

            // ── Shikimori Senkronizasyonu (Anime & Manga) ────────────────────────
            val shouldSyncShikimori = syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && isAnimeOrManga

            if (shouldSyncShikimori) {
                runCatching {
                    ShikimoriSyncManager.syncEntryToShikimori(context, effectiveEntry)
                }.onSuccess { shikiResult ->
                    if (shikiResult.errors.isEmpty()) {
                        syncedSources.add("Shikimori")
                    } else {
                        errors.addAll(shikiResult.errors)
                    }
                }.onFailure { error ->
                    errors.add("Shikimori: ${error.message}")
                }
            }

            val formatted = formatSyncSources(syncedSources, isDelete = false)
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
            val messages = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val deletedSources = mutableListOf<String>()

            val settings = runCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
            val syncEnabledAniList = settings?.syncEnabledAnilist ?: true
            val syncEnabledMal = settings?.syncEnabledMal ?: true
            val syncEnabledSimkl = settings?.syncEnabledSimkl ?: true
            val syncEnabledKitsu = settings?.syncEnabledKitsu ?: true
            val syncEnabledShikimori = settings?.syncEnabledShikimori ?: true

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)

            val realMalId = entry.malId?.takeIf { it.isRealMalId() }
            val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga

            val shouldDeleteAniList = syncEnabledAniList && !aniListToken.isNullOrBlank() && isAnimeOrManga
            val shouldDeleteMal = syncEnabledMal && !malToken.isNullOrBlank() && realMalId != null && isAnimeOrManga
            val shouldDeleteSimkl = syncEnabledSimkl && !simklToken.isNullOrBlank()
            val shouldDeleteKitsu = syncEnabledKitsu && !kitsuToken.isNullOrBlank() && isAnimeOrManga
            val shouldDeleteShikimori = syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && isAnimeOrManga

            if (shouldDeleteAniList) {
                runCatching {
                    AniListSyncManager.deleteAniListEntry(
                        token = aniListToken!!,
                        entry = entry
                    )
                }.onSuccess {
                    deletedSources.add("AniList")
                }.onFailure { error ->
                    errors.add("AniList silme: ${error.message}")
                }
            }

            if (shouldDeleteMal && realMalId != null) {
                runCatching {
                    MalSyncManager.deleteMalEntry(
                        token = malToken!!,
                        entry = entry
                    )
                }.onSuccess {
                    deletedSources.add("MyAnimeList")
                }.onFailure { error ->
                    errors.add("MAL silme: ${error.message}")
                }
            }

            if (shouldDeleteSimkl) {
                runCatching {
                    SimklSyncManager.deleteEntryFromSimkl(context, entry)
                }.onSuccess { simklResult ->
                    if (simklResult.isSuccess) {
                        deletedSources.add("Simkl")
                    } else {
                        errors.addAll(simklResult.errors)
                    }
                }.onFailure { error ->
                    errors.add("Simkl silme: ${error.message}")
                }
            }

            if (shouldDeleteKitsu) {
                runCatching {
                    KitsuSyncManager.deleteEntryFromKitsu(context, entry)
                }.onSuccess { kitsuResult ->
                    if (kitsuResult.errors.isEmpty()) {
                        deletedSources.add("Kitsu")
                    } else {
                        errors.addAll(kitsuResult.errors)
                    }
                }.onFailure { error ->
                    errors.add("Kitsu silme: ${error.message}")
                }
            }

            if (shouldDeleteShikimori) {
                runCatching {
                    ShikimoriSyncManager.deleteEntryFromShikimori(context, entry)
                }.onSuccess { shikiResult ->
                    if (shikiResult.errors.isEmpty()) {
                        deletedSources.add("Shikimori")
                    } else {
                        errors.addAll(shikiResult.errors)
                    }
                }.onFailure { error ->
                    errors.add("Shikimori silme: ${error.message}")
                }
            }

            val formatted = formatSyncSources(deletedSources, isDelete = true)
            if (formatted.isNotEmpty()) {
                messages.add(formatted)
            }

            SyncResult(
                messages = messages,
                errors = errors
            )
        }
    }

    private fun formatSyncSources(sources: List<String>, isDelete: Boolean): String {
        if (sources.isEmpty()) return ""
        val joined = when (sources.size) {
            1 -> sources[0]
            2 -> "${sources[0]} ve ${sources[1]}"
            else -> {
                val last = sources.last()
                val remaining = sources.dropLast(1).joinToString(", ")
                "$remaining ve $last"
            }
        }
        return if (isDelete) {
            "$joined kütüphanesinden silindi"
        } else {
            "$joined başarıyla eşitlendi"
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
package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLocalMappingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.local.toEntity

/**
 * Bir listedeki değişikliği **altı kaynağın tamamına** yayan canlı eşitleme hattı.
 *
 * Eskiden bu hat yalnızca AniList/MAL/Simkl için güvenilir kimlik çözebiliyor,
 * Kitsu · Shikimori · Bangumi satırları ise ya hiç yayılamıyor (Bangumi satırları
 * kısa devre yapıyordu) ya da "kimlik çözülemedi" şeklinde **hata** sayıldığı için
 * bekleme kuyruğunu sonsuza kadar şişiriyordu. Artık kimlik tek yerden gelir
 * ([CrossProviderIdentityResolver]) ve çözülemeyen kaynak hata değil **atlandı** sayılır.
 */
object ExternalListSyncManager {
    /** Kaynak başına sonuç etiketleri (raporda ve özet mesajda gösterilir). */
    internal const val OUTCOME_SYNCED = "eşitlendi"
    internal const val OUTCOME_SKIPPED_IDENTITY = "kimlik çözülemedi"
    internal const val OUTCOME_SKIPPED_TYPE = "tür desteklenmiyor"
    internal const val OUTCOME_DISABLED = "kapalı"
    internal const val OUTCOME_NOT_CONNECTED = "bağlı değil"
    internal const val OUTCOME_FAILED = "hata"

    /** Bangumi yazması çağrıda zaten yapıldı (`skipBangumiWrite`). */
    internal const val OUTCOME_WRITTEN_BY_CALLER = "çağıran yazdı"

    private fun Context.sourceFailure(platform: String, error: Throwable): String =
        getString(
            R.string.account_sync_source_failure,
            platform,
            error.message ?: getString(R.string.account_sync_unknown_error)
        )

    /**
     * @param skipBangumiWrite Bangumi yazması çağrının kendisi tarafından zaten yapıldıysa
     * (Bangumi liste ekranlarından ekleme/düzenleme) `true` verilir; böylece koleksiyona
     * iki kez yazılmaz, diğer beş kaynağa yayılım yine çalışır.
     */
    suspend fun syncEntry(
        context: Context,
        entry: MediaEntry,
        advancedScores: List<Double>? = null,
        skipBangumiWrite: Boolean = false
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            val messages = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val outcomes = mutableMapOf<String, String>()
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

            // Kimlik araması yalnızca BAĞLI kaynaklar için yapılır: bağlı olmayan servis
            // adına katalog araması yapmak eşitlemeyi geciktirir, sonuç vermez.
            val connectedSources = ConnectedSources(
                aniList = syncEnabledAniList && !aniListToken.isNullOrBlank(),
                mal = syncEnabledMal && !malToken.isNullOrBlank(),
                kitsu = syncEnabledKitsu && !kitsuToken.isNullOrBlank(),
                shikimori = syncEnabledShikimori && !shikimoriToken.isNullOrBlank(),
                simkl = syncEnabledSimkl && !simklToken.isNullOrBlank(),
                bangumi = !bangumiToken.isNullOrBlank()
            )

            val isBangumiRow = canonicalMediaSourceId(entry.source) == BangumiIdNamespace.SOURCE

            // ── Çapraz kimlik: satır hangi kaynaktan gelirse gelsin altı ID birden çözülür ──
            val fallbackIdentity = CrossProviderIds.decode(
                storedId = entry.malId,
                source = entry.source,
                type = entry.type,
                entrySimklId = entry.simklId,
                entryTmdbId = entry.tmdbId
            )
            val identity = runSyncCatching {
                CrossProviderIdentityResolver.resolve(context, entry, connected = connectedSources)
            }.getOrNull()?.mergedWith(fallbackIdentity) ?: fallbackIdentity

            // Bangumi'nin kendi koleksiyon yazması her zaman serbest; diğer beş kaynağa
            // yayılmak için doğrulanmış MAL/AniList kimliği gerekir.
            val allowFanOut = CrossSyncPolicy.allowFanOut(isBangumiRow, identity)
            if (isBangumiRow && !allowFanOut) {
                skipped.add(context.getString(R.string.account_sync_fanout_blocked_bangumi))
            }

            // Uzak çağrılar çözülen gerçek MAL kimliğiyle yapılır; YEREL satır kendi kaynak
            // alanını (stableId) korur — aksi halde satırın kendi listesindeki kimliği bozulur.
            val remoteEntry = withResolvedIds(entry, identity)
            persistNewlyResolvedIds(context, entry, identity)

            val realMalId = identity.malId

            // ── AniList Senkronizasyonu (Anime & Manga) ──────────────────────────
            if (!syncEnabledAniList) {
                outcomes["AniList"] = OUTCOME_DISABLED
            } else if (aniListToken.isNullOrBlank()) {
                outcomes["AniList"] = OUTCOME_NOT_CONNECTED
            } else if (!CrossSyncPolicy.supportsAniList(entry.type)) {
                outcomes["AniList"] = OUTCOME_SKIPPED_TYPE
            } else if (!allowFanOut) {
                outcomes["AniList"] = OUTCOME_SKIPPED_IDENTITY
            } else {
                runSyncCatching {
                    PlatformRateLimiter.acquire("anilist")
                    AniListSyncManager.updateAniListEntry(
                        token = aniListToken,
                        entry = remoteEntry,
                        advancedScores = advancedScores,
                        context = context,
                        knownAniListMediaId = identity.aniListId
                    )
                }.onSuccess { remoteId ->
                    if (remoteId != null && remoteId > 0) {
                        resolvedAniListEntryId = remoteId
                        syncedSources.add("AniList")
                        outcomes["AniList"] = OUTCOME_SYNCED
                        // Yeni dönen aniListEntryId'yi yerel DB'ye yaz (orijinal satırın kimliği korunur).
                        if (entry.aniListEntryId != remoteId) {
                            runSyncCatching {
                                val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                                val current = dao.getById(entry.id)?.toDomain() ?: entry
                                dao.update(current.copy(aniListEntryId = remoteId).toEntity())
                            }
                        }
                    } else {
                        // null = "AniList'te hangi medya olduğu güvenle çözülemedi" — API hatası değil.
                        outcomes["AniList"] = OUTCOME_SKIPPED_IDENTITY
                        skipped.add(context.getString(R.string.cross_sync_reason_anilist_media_id_unresolved))
                    }
                }.onFailure { error ->
                    outcomes["AniList"] = OUTCOME_FAILED
                    errors.add(context.sourceFailure("AniList", error))
                }

                // AniList favori durumu senkronizasyonu
                runSyncCatching {
                    val remoteFav = AniListSyncManager.getAniListMediaFavoriteStatus(
                        aniListToken, remoteEntry, context, knownAniListMediaId = identity.aniListId
                    )
                    if (remoteFav != null && remoteFav != entry.isFavorite) {
                        check(
                            AniListSyncManager.toggleAniListFavourite(
                                aniListToken, remoteEntry, context, knownAniListMediaId = identity.aniListId
                            )
                        ) {
                            context.getString(R.string.account_sync_anilist_update_unconfirmed)
                        }
                        true
                    } else {
                        false
                    }
                }.onSuccess { changed ->
                    if (changed && !syncedSources.contains("AniList")) {
                        syncedSources.add("AniList")
                        outcomes["AniList"] = OUTCOME_SYNCED
                    }
                }.onFailure { error ->
                    errors.add(
                        context.getString(
                            R.string.account_sync_favorite_error,
                            error.message ?: context.getString(R.string.account_sync_unknown_error)
                        )
                    )
                }
            }

            // ── MyAnimeList Senkronizasyonu (Anime & Manga) ────────────────────
            if (!syncEnabledMal) {
                outcomes["MyAnimeList"] = OUTCOME_DISABLED
            } else if (malToken.isNullOrBlank()) {
                outcomes["MyAnimeList"] = OUTCOME_NOT_CONNECTED
            } else if (!CrossSyncPolicy.supportsMal(entry.type) || !allowFanOut) {
                outcomes["MyAnimeList"] = if (allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY
            } else if (realMalId == null) {
                outcomes["MyAnimeList"] = OUTCOME_SKIPPED_IDENTITY
                skipped.add(context.getString(R.string.account_sync_no_valid_mal_id))
            } else {
                runSyncCatching {
                    PlatformRateLimiter.acquire("mal")
                    MalSyncManager.updateMalEntry(
                        token = malToken,
                        entry = remoteEntry,
                        context = context
                    )
                }.onSuccess {
                    syncedSources.add("MyAnimeList")
                    outcomes["MyAnimeList"] = OUTCOME_SYNCED
                }.onFailure { error ->
                    outcomes["MyAnimeList"] = OUTCOME_FAILED
                    errors.add(context.sourceFailure("MAL", error))
                }
            }

            // ── Simkl Senkronizasyonu (Anime, Film, Dizi — Manga hariç) ──────────
            if (!syncEnabledSimkl) {
                outcomes["Simkl"] = OUTCOME_DISABLED
            } else if (simklToken.isNullOrBlank()) {
                outcomes["Simkl"] = OUTCOME_NOT_CONNECTED
            } else if (!CrossSyncPolicy.supportsSimkl(entry.type) || !allowFanOut) {
                outcomes["Simkl"] = if (allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY
            } else {
                runSyncCatching {
                    PlatformRateLimiter.acquire("simkl")
                    SimklSyncManager.syncEntryToSimkl(context, remoteEntry)
                }.onSuccess { simklResult ->
                    if (simklResult.isSuccess) {
                        syncedSources.add("Simkl")
                        outcomes["Simkl"] = OUTCOME_SYNCED
                    } else {
                        outcomes["Simkl"] = OUTCOME_FAILED
                        errors.addAll(simklResult.errors)
                    }
                }.onFailure { error ->
                    outcomes["Simkl"] = OUTCOME_FAILED
                    errors.add(context.sourceFailure("Simkl", error))
                }
            }

            // ── Kitsu Senkronizasyonu (Anime & Manga) ────────────────────────────
            if (!syncEnabledKitsu) {
                outcomes["Kitsu"] = OUTCOME_DISABLED
            } else if (kitsuToken.isNullOrBlank()) {
                outcomes["Kitsu"] = OUTCOME_NOT_CONNECTED
            } else if (!CrossSyncPolicy.supportsKitsu(entry.type) || !allowFanOut) {
                outcomes["Kitsu"] = if (allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY
            } else {
                runSyncCatching {
                    PlatformRateLimiter.acquire("kitsu")
                    KitsuSyncManager.syncEntryToKitsu(
                        context = context,
                        entry = remoteEntry,
                        knownKitsuMediaId = identity.kitsuId
                    )
                }.onSuccess { kitsuResult ->
                    when {
                        kitsuResult.errors.isEmpty() -> {
                            syncedSources.add("Kitsu")
                            outcomes["Kitsu"] = OUTCOME_SYNCED
                        }
                        // Kimlik bulunamadı: güvenli atlama, yeniden denenecek hata değil.
                        kitsuResult.unresolvedMedia -> {
                            outcomes["Kitsu"] = OUTCOME_SKIPPED_IDENTITY
                            skipped.addAll(kitsuResult.errors)
                        }
                        else -> {
                            outcomes["Kitsu"] = OUTCOME_FAILED
                            errors.addAll(kitsuResult.errors)
                        }
                    }
                }.onFailure { error ->
                    outcomes["Kitsu"] = OUTCOME_FAILED
                    errors.add(context.sourceFailure("Kitsu", error))
                }
            }

            // ── Shikimori Senkronizasyonu (Anime & Manga; hedef kimlik = MAL ID) ───
            if (!syncEnabledShikimori) {
                outcomes["Shikimori"] = OUTCOME_DISABLED
            } else if (shikimoriToken.isNullOrBlank()) {
                outcomes["Shikimori"] = OUTCOME_NOT_CONNECTED
            } else if (!CrossSyncPolicy.supportsShikimori(entry.type) || !allowFanOut) {
                outcomes["Shikimori"] = if (allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY
            } else if (realMalId == null && identity.shikimoriId == null) {
                outcomes["Shikimori"] = OUTCOME_SKIPPED_IDENTITY
                skipped.add(context.getString(R.string.account_sync_shikimori_id_not_found, entry.title))
            } else {
                // Shikimori'nin kendi satırı: MAL kimliği bu tur çözülemese bile satırın kendi
                // target_id'si Shikimori yazması için yeterlidir (kendi kaynağına güncelleme
                // MAL çözümüne bağlı olmamalı). Çapraz satırlarda hedef yine MAL kimliğidir.
                val shikimoriEntry = if (realMalId == null) {
                    remoteEntry.copy(malId = identity.shikimoriId)
                } else {
                    remoteEntry
                }
                runSyncCatching {
                    PlatformRateLimiter.acquire("shikimori")
                    ShikimoriSyncManager.syncEntryToShikimori(context, shikimoriEntry)
                }.onSuccess { shikiResult ->
                    when {
                        shikiResult.errors.isEmpty() -> {
                            syncedSources.add("Shikimori")
                            outcomes["Shikimori"] = OUTCOME_SYNCED
                        }
                        shikiResult.unresolvedMedia -> {
                            outcomes["Shikimori"] = OUTCOME_SKIPPED_IDENTITY
                            skipped.addAll(shikiResult.errors)
                        }
                        else -> {
                            outcomes["Shikimori"] = OUTCOME_FAILED
                            errors.addAll(shikiResult.errors)
                        }
                    }
                }.onFailure { error ->
                    outcomes["Shikimori"] = OUTCOME_FAILED
                    errors.add(context.sourceFailure("Shikimori", error))
                }
            }

            // ── Bangumi Senkronizasyonu (Anime, manga, film, dizi) ────────────────
            if (skipBangumiWrite) {
                outcomes["Bangumi"] = OUTCOME_WRITTEN_BY_CALLER
            } else if (!syncEnabledBangumi) {
                outcomes["Bangumi"] = OUTCOME_DISABLED
            } else if (bangumiToken.isNullOrBlank()) {
                outcomes["Bangumi"] = OUTCOME_NOT_CONNECTED
            } else if (!CrossSyncPolicy.supportsBangumi(entry.type)) {
                outcomes["Bangumi"] = OUTCOME_SKIPPED_TYPE
            } else {
                runSyncCatching {
                    PlatformRateLimiter.acquire("bangumi")
                    BangumiSyncManager.syncEntryToBangumi(
                        context = context,
                        // Bangumi satırları kendi stableId'siyle yazılır; çözülen subject kimliği
                        // çağrıya ayrı olarak verilir.
                        entry = entry,
                        knownSubjectId = identity.bangumiSubjectId
                    )
                }.onSuccess { bangumiResult ->
                    when {
                        bangumiResult.errors.isEmpty() -> {
                            syncedSources.add("Bangumi")
                            outcomes["Bangumi"] = OUTCOME_SYNCED
                        }
                        bangumiResult.unresolvedSubject -> {
                            outcomes["Bangumi"] = OUTCOME_SKIPPED_IDENTITY
                            skipped.addAll(bangumiResult.errors)
                        }
                        else -> {
                            outcomes["Bangumi"] = OUTCOME_FAILED
                            errors.addAll(bangumiResult.errors)
                        }
                    }
                    // Kaynak satırı Bangumi dışında ise MAL↔Bangumi köprüsü öğrenilir:
                    // sonraki eşitlemede Bangumi araması yapılmaz.
                    val learnedSubjectId = bangumiResult.subjectId
                    if (!isBangumiRow && realMalId != null && learnedSubjectId != null) {
                        runSyncCatching {
                            BangumiLocalMappingCache.put(
                                context,
                                realMalId,
                                BangumiIdNamespace.subjectTypeFor(entry.type),
                                learnedSubjectId
                            )
                        }
                    }
                }.onFailure { error ->
                    outcomes["Bangumi"] = OUTCOME_FAILED
                    errors.add(context.sourceFailure("Bangumi", error))
                }
            }

            val formatted = formatSyncSources(context, syncedSources, isDelete = false)
            if (formatted.isNotEmpty()) {
                messages.add(formatted)
            }
            if (skipped.isNotEmpty()) {
                messages.add(
                    context.getString(
                        R.string.account_sync_sources_skipped,
                        skipped.distinct().joinToString(" • ")
                    )
                )
            }

            SyncResult(
                messages = messages,
                errors = errors,
                aniListEntryId = resolvedAniListEntryId,
                skipped = skipped,
                outcomes = outcomes
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
            val skipped = mutableListOf<String>()
            val outcomes = mutableMapOf<String, String>()
            val deletedSources = mutableListOf<String>()

            val settings = runSyncCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
            val syncEnabledAniList = settings?.syncEnabledAnilist ?: true
            val syncEnabledMal = settings?.syncEnabledMal ?: true
            val syncEnabledSimkl = settings?.syncEnabledSimkl ?: true
            val syncEnabledKitsu = settings?.syncEnabledKitsu ?: true
            val syncEnabledShikimori = settings?.syncEnabledShikimori ?: true
            val syncEnabledBangumi = settings?.syncEnabledBangumi ?: true

            val isBangumiRow = canonicalMediaSourceId(entry.source) == BangumiIdNamespace.SOURCE
            val fallbackIdentity = CrossProviderIds.decode(
                storedId = entry.malId,
                source = entry.source,
                type = entry.type,
                entrySimklId = entry.simklId,
                entryTmdbId = entry.tmdbId
            )
            // Silmede yanlış yapıma dokunmamak için ağ araması YAPILMAZ: yalnızca satırın
            // kendi kimliği ve daha önce çözülmüş kalıcı eşlemeler kullanılır.
            val identity = runSyncCatching {
                CrossProviderIdentityResolver.resolve(context, entry, allowNetwork = false)
            }.getOrNull()?.mergedWith(fallbackIdentity) ?: fallbackIdentity
            val allowFanOut = CrossSyncPolicy.allowFanOut(isBangumiRow, identity)
            if (isBangumiRow && !allowFanOut) {
                skipped.add(context.getString(R.string.account_sync_fanout_blocked_bangumi))
            }
            val remoteEntry = withResolvedIds(entry, identity)

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            val bangumiToken = if (syncEnabledBangumi) BangumiAuthStore.getValidToken(context) else null

            val realMalId = identity.malId
            val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga

            if (syncEnabledAniList && !aniListToken.isNullOrBlank() && isAnimeOrManga && allowFanOut) {
                runSyncCatching {
                    PlatformRateLimiter.acquire("anilist")
                    AniListSyncManager.deleteAniListEntry(
                        token = aniListToken,
                        entry = remoteEntry,
                        context = context
                    )
                }.onSuccess {
                    deletedSources.add("AniList")
                    outcomes["AniList"] = OUTCOME_SYNCED
                }.onFailure { error ->
                    outcomes["AniList"] = OUTCOME_FAILED
                    errors.add(context.getString(R.string.account_sync_delete_error, "AniList", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            } else {
                outcomes["AniList"] = deleteGateOutcome(syncEnabledAniList, !aniListToken.isNullOrBlank(), isAnimeOrManga && allowFanOut)
            }

            if (syncEnabledMal && !malToken.isNullOrBlank() && isAnimeOrManga && realMalId != null && allowFanOut) {
                runSyncCatching {
                    PlatformRateLimiter.acquire("mal")
                    MalSyncManager.deleteMalEntry(
                        token = malToken,
                        entry = remoteEntry,
                        context = context
                    )
                }.onSuccess {
                    deletedSources.add("MyAnimeList")
                    outcomes["MyAnimeList"] = OUTCOME_SYNCED
                }.onFailure { error ->
                    outcomes["MyAnimeList"] = OUTCOME_FAILED
                    errors.add(context.getString(R.string.account_sync_delete_error, "MAL", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            } else {
                outcomes["MyAnimeList"] = deleteGateOutcome(syncEnabledMal, !malToken.isNullOrBlank(), isAnimeOrManga && allowFanOut && realMalId != null)
                if (realMalId == null && syncEnabledMal && !malToken.isNullOrBlank() && isAnimeOrManga) {
                    skipped.add(context.getString(R.string.account_sync_no_valid_mal_id))
                }
            }

            if (syncEnabledSimkl && !simklToken.isNullOrBlank() && entry.type != MediaType.Manga && allowFanOut) {
                runSyncCatching {
                    PlatformRateLimiter.acquire("simkl")
                    SimklSyncManager.deleteEntryFromSimkl(context, remoteEntry)
                }.onSuccess { simklResult ->
                    if (simklResult.isSuccess) {
                        deletedSources.add("Simkl")
                        outcomes["Simkl"] = OUTCOME_SYNCED
                    } else {
                        outcomes["Simkl"] = OUTCOME_FAILED
                        errors.addAll(simklResult.errors)
                    }
                }.onFailure { error ->
                    outcomes["Simkl"] = OUTCOME_FAILED
                    errors.add(context.getString(R.string.account_sync_delete_error, "Simkl", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            } else {
                outcomes["Simkl"] = deleteGateOutcome(syncEnabledSimkl, !simklToken.isNullOrBlank(), entry.type != MediaType.Manga && allowFanOut)
            }

            if (syncEnabledKitsu && !kitsuToken.isNullOrBlank() && isAnimeOrManga && allowFanOut) {
                runSyncCatching {
                    PlatformRateLimiter.acquire("kitsu")
                    KitsuSyncManager.deleteEntryFromKitsu(context, remoteEntry)
                }.onSuccess { kitsuResult ->
                    if (kitsuResult.errors.isEmpty()) {
                        deletedSources.add("Kitsu")
                        outcomes["Kitsu"] = OUTCOME_SYNCED
                    } else {
                        outcomes["Kitsu"] = OUTCOME_FAILED
                        errors.addAll(kitsuResult.errors)
                    }
                }.onFailure { error ->
                    outcomes["Kitsu"] = OUTCOME_FAILED
                    errors.add(context.getString(R.string.account_sync_delete_error, "Kitsu", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            } else {
                outcomes["Kitsu"] = deleteGateOutcome(syncEnabledKitsu, !kitsuToken.isNullOrBlank(), isAnimeOrManga && allowFanOut)
            }

            if (syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && isAnimeOrManga && allowFanOut) {
                runSyncCatching {
                    PlatformRateLimiter.acquire("shikimori")
                    ShikimoriSyncManager.deleteEntryFromShikimori(context, remoteEntry)
                }.onSuccess { shikiResult ->
                    if (shikiResult.errors.isEmpty()) {
                        deletedSources.add("Shikimori")
                        outcomes["Shikimori"] = OUTCOME_SYNCED
                    } else {
                        outcomes["Shikimori"] = OUTCOME_SKIPPED_IDENTITY
                        skipped.addAll(shikiResult.errors)
                    }
                }.onFailure { error ->
                    outcomes["Shikimori"] = OUTCOME_FAILED
                    errors.add(context.getString(R.string.account_sync_delete_error, "Shikimori", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            } else {
                outcomes["Shikimori"] = deleteGateOutcome(syncEnabledShikimori, !shikimoriToken.isNullOrBlank(), isAnimeOrManga && allowFanOut)
            }

            if (syncEnabledBangumi && !bangumiToken.isNullOrBlank() && BangumiSyncManager.supportsMediaType(entry.type)) {
                runSyncCatching {
                    PlatformRateLimiter.acquire("bangumi")
                    BangumiSyncManager.deleteEntryFromBangumi(
                        context = context,
                        entry = entry,
                        knownSubjectId = identity.bangumiSubjectId
                    )
                }.onSuccess { bangumiResult ->
                    if (bangumiResult.errors.isEmpty()) {
                        deletedSources.add("Bangumi")
                        outcomes["Bangumi"] = OUTCOME_SYNCED
                    } else {
                        outcomes["Bangumi"] = OUTCOME_SKIPPED_IDENTITY
                        skipped.addAll(bangumiResult.errors)
                    }
                }.onFailure { error ->
                    outcomes["Bangumi"] = OUTCOME_FAILED
                    errors.add(context.getString(R.string.account_sync_delete_error, "Bangumi", error.message ?: context.getString(R.string.account_sync_unknown_error)))
                }
            } else {
                outcomes["Bangumi"] = deleteGateOutcome(
                    syncEnabledBangumi,
                    !bangumiToken.isNullOrBlank(),
                    BangumiSyncManager.supportsMediaType(entry.type)
                )
            }

            val formatted = formatSyncSources(context, deletedSources, isDelete = true)
            if (formatted.isNotEmpty()) {
                messages.add(formatted)
            }
            if (skipped.isNotEmpty()) {
                messages.add(
                    context.getString(
                        R.string.account_sync_sources_skipped,
                        skipped.distinct().joinToString(" • ")
                    )
                )
            }

            SyncResult(
                messages = messages,
                errors = errors,
                skipped = skipped,
                outcomes = outcomes
            )
        }
    }

    /**
     * Uzak çağrıların kullandığı geçici kayıt: çözülen gerçek MAL / TMDB / Simkl kimlikleri
     * taşır. YEREL satıra yazılmaz (satır kendi kaynak alanını korur).
     */
    private fun withResolvedIds(entry: MediaEntry, ids: CrossProviderIds): MediaEntry {
        val malId = CrossProviderIds.realMalIdOf(ids.malId) ?: entry.malId
        if (malId == entry.malId && ids.simklId == null && ids.tmdbId == null) return entry
        return entry.copy(
            malId = malId,
            simklId = entry.simklId ?: ids.simklId,
            tmdbId = entry.tmdbId ?: ids.tmdbId
        )
    }

    /**
     * Çözülen kimlikleri kalıcı eşitleme önbelleklerine yazar.
     *
     * Yalnızca satırda hiç kimlik yoksa (ör. elle eklenen kayıt) `malId` alanına gerçek MAL
     * ID yazılır; aksi halde satırın kendi stableId'si bozulur ve provider'a özel listelerde
     * kaybolur. MAL↔Bangumi ve MAL↔Kitsu eşlemeleri her durumda öğrenilir.
     */
    private suspend fun persistNewlyResolvedIds(
        context: Context,
        entry: MediaEntry,
        ids: CrossProviderIds
    ) = withContext(Dispatchers.IO) {
        val malId = CrossProviderIds.realMalIdOf(ids.malId) ?: return@withContext
        if (entry.malId == null && entry.id > 0) {
            runSyncCatching {
                val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                val current = dao.getById(entry.id)?.toDomain() ?: entry
                dao.update(current.copy(malId = malId).toEntity())
            }
        }
        if (ids.bangumiSubjectId != null && entry.type != MediaType.Movie && entry.type != MediaType.TvShow) {
            runSyncCatching {
                BangumiLocalMappingCache.put(
                    context,
                    malId,
                    BangumiIdNamespace.subjectTypeFor(entry.type),
                    ids.bangumiSubjectId
                )
            }
        }
    }

    private fun deleteGateOutcome(enabled: Boolean, connected: Boolean, applicable: Boolean): String = when {
        !enabled -> OUTCOME_DISABLED
        !connected -> OUTCOME_NOT_CONNECTED
        !applicable -> OUTCOME_SKIPPED_IDENTITY
        else -> OUTCOME_SKIPPED_TYPE
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

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        val aniListEntryId: Int? = null,
        /**
         * Kimlik güvenle çözülemediği için YAZILMAYAN kaynakların gerekçeleri.
         * Bunlar API hatası değildir: kullanıcıya bildirilir, bekleme kuyruğuna yazılmaz.
         */
        val skipped: List<String> = emptyList(),
        /** Kaynak adı → [OUTCOME_SYNCED] / [OUTCOME_SKIPPED_IDENTITY] / … (raporlama ve arayüz). */
        val outcomes: Map<String, String> = emptyMap()
    ) {
        /** Yalnızca gerçek hatalar yeniden denemeyi haklı çıkarır. */
        val needsRetry: Boolean get() = CrossSyncPolicy.shouldRetry(errors, skipped)
    }
}

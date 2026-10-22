package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiLocalMappingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.local.NetworkMonitor
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.local.toEntity

/**
 * Bir listedeki değişikliği **altı kaynağın tamamına** yayan CANLI ve EŞZAMANLI eşitleme hattı.
 *
 * Kaynaklar (AniList · MyAnimeList · Simkl · Kitsu · Shikimori · Bangumi) artık **sırayla
 * değil, aynı anda** (her biri kendi coroutine'i içinde, kendi zaman aşımıyla) yazılır:
 *
 *  - Biri yavaş/kilitliyse diğerleri beklemez; toplam süre "en yavaş kaynak" kadar olur,
 *    "altı kaynağın toplamı" değil.
 *  - Bir kaynağın hatası diğerlerinin yazmasını engellemez; sonuçlar kaynak kaynak toplanır.
 *
 * Kimlik kararı tek yerden, [CrossProviderIdentityResolver] + [CrossSyncPolicy] üzerinden gelir.
 * **Yayılım kapısı** (bkz. [CrossSyncPolicy.allowsWrite]) artık "MAL kataloğu yıl alanını
 * doğrulayamadı" diye tüm kaynakları kesmiyor: satır MAL'ın kendisiyse kimlik zaten kaynağından
 * gelir; diğer hâllerde hedefin KENDİ resmî kimliği (AniList `idMal`, Kitsu `mappings`,
 * Simkl `ids.mal`) belirleyicidir. Yalnızca katalog **çelişkisi** yazımı durdurur.
 *
 * "Atlandı" (kimlik güvenle çözülemedi) hata değildir: kullanıcıya gerekçe olarak gösterilir,
 * bekleyen-senkron kuyruğunu şişirmez.
 */
object ExternalListSyncManager {
    /** Kaynak başına sonuç etiketleri (raporda ve özet mesajda gösterilir). */
    internal const val OUTCOME_SYNCED = "eşitlendi"
    internal const val OUTCOME_SKIPPED_IDENTITY = "kimlik çözülemedi"
    internal const val OUTCOME_SKIPPED_TYPE = "tür desteklenmiyor"
    internal const val OUTCOME_DISABLED = "kapalı"
    internal const val OUTCOME_NOT_CONNECTED = "bağlı değil"
    internal const val OUTCOME_FAILED = "hata"
    internal const val OUTCOME_TIMEOUT = "zaman aşımı"
    /** Değişen alan yok: hedefe istek atılmadı (veri kaybını önleyen "dokunma" kuralı). */
    internal const val OUTCOME_UP_TO_DATE = "değişiklik yok"

    /** Bangumi yazması çağrıda zaten yapıldı (`skipBangumiWrite`). */
    internal const val OUTCOME_WRITTEN_BY_CALLER = "çağıran yazdı"

    /** Tek kaynağın yanıtını bundan fazla beklemeyiz; kilitlenen kaynak diğerlerini durdurmaz. */
    private const val SOURCE_TIMEOUT_MS = 30_000L

    private const val ANILIST = "AniList"
    private const val MAL = "MyAnimeList"
    private const val SIMKL = "Simkl"
    private const val KITSU = "Kitsu"
    private const val SHIKIMORI = "Shikimori"
    private const val BANGUMI = "Bangumi"

    private fun Context.sourceFailure(platform: String, error: Throwable): String =
        getString(
            R.string.account_sync_source_failure,
            platform,
            error.message ?: getString(R.string.account_sync_unknown_error)
        )

    /**
     * Bir eşitleme turunun DEĞİŞMEZ bağlamı. Altı kaynak bloğu bunu paylaşır; hiçbiri
     * diğerinin alanlarını yazmaz (eşzamanlı çalışmanın ön koşulu).
     */
    private class Run(
        val context: Context,
        val entry: MediaEntry,
        val remoteEntry: MediaEntry,
        val identity: CrossProviderIds,
        val advancedScores: List<Double>?,
        val skipBangumiWrite: Boolean,
        val allowFanOut: Boolean,
        val rowSource: String,
        val isBangumiRow: Boolean,
        val enabled: Map<String, Boolean>,
        val tokens: Map<String, String?>,
        val verdictBlocked: Boolean,
        /**
         * Bu turda DEĞİŞEN alanlar. Sağlayıcı yalnızca kendi desteklediği alanları yazar;
         * böylece hedefteki (uygulama dışında girilmiş) puan/not/tarih EZİLMEZ.
         * bkz. [SyncField], [SyncFieldDiff], [ProviderCapabilitiesCatalog].
         */
        val fields: Set<SyncField>
    ) {
        /** Bu sağlayıcıya yazılacak alanlar (desteklenmeyenler süzülür). */
        fun fieldsFor(provider: String): Set<SyncField> = fields.narrowTo(provider)

        /**
         * İlk/tam yazım mı? (yeni kayıt ya da eski kuyruk işi)
         *
         * ÖNEMLİ: bu bayrak süzme SONRASI kümeyle hesaplanamaz — süzme her sağlayıcıya
         * farklı bir alt küme verdiği için "tam yazım" bilgisi kaybolurdu ve boş puan/not
         * hedefte silinirdi. Yazıcılara AÇIKÇA geçirilir.
         */
        val isFullWrite: Boolean get() = fields == SyncField.ALL

        fun isOn(key: String): Boolean = enabled[key] == true
        fun token(key: String): String? = tokens[key]

        /** Bu turda [target] kaynağına yazılabilir mi? (kimlik kanıtı + çelişki kapısı) */
        fun allows(target: String): Boolean =
            CrossSyncPolicy.allowsWrite(target, rowSource, identity, verdictBlocked)

        val isAnimeOrManga: Boolean
            get() = entry.type == MediaType.Anime || entry.type == MediaType.Manga
    }

    /** Tek kaynağın sonucu — toplama işlemi ana akışta, sıralı ve deterministik yapılır. */
    private data class SourceResult(
        val source: String,
        val outcome: String,
        val errors: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val synced: Boolean = false,
        val aniListEntryId: Int? = null,
        val learnedSubjectId: Int? = null
    )

    private class Prepared(
        val run: Run,
        val skipped: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    )

    // ────────────────────────────────────────────────────────────────────────────────
    // GÜNCELLEME
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * @param skipBangumiWrite Bangumi yazması çağrının kendisi tarafından zaten yapıldıysa
     * (Bangumi liste ekranlarından ekleme/düzenleme) `true` verilir; böylece koleksiyona
     * iki kez yazılmaz, diğer beş kaynağa yayılım yine çalışır.
     */
    suspend fun syncEntry(
        context: Context,
        entry: MediaEntry,
        advancedScores: List<Double>? = null,
        skipBangumiWrite: Boolean = false,
        /**
         * Yerel satırda DEĞİŞEN alanlar ([SyncFieldDiff.between]). Varsayılan [SyncField.ALL]
         * "tam yazım"dır ve yalnızca yeni kayıt/kuyruk yeniden denemesi için doğrudur.
         */
        fields: Set<SyncField> = SyncField.ALL
    ): SyncResult = withContext(Dispatchers.IO) {
        val prepared = prepare(context, entry, advancedScores, skipBangumiWrite, fields)
        val run = prepared.run

        // ── EŞZAMANLI YAYILIM: altı kaynak aynı anda, birbirini beklemeden ──────────
        val results = coroutineScope {
            listOf(
                async { timed(ANILIST) { syncAniList(run) } },
                async { timed(MAL) { syncMal(run) } },
                async { timed(SIMKL) { syncSimkl(run) } },
                async { timed(KITSU) { syncKitsu(run) } },
                async { timed(SHIKIMORI) { syncShikimori(run) } },
                async { timed(BANGUMI) { syncBangumi(run) } }
            ).awaitAll()
        }

        // Bangumi ↔ MAL köprüsü öğrenilir: sonraki eşitlemede Bangumi araması yapılmaz.
        val realMalId = run.identity.malId
        val learnedSubjectId = results.firstOrNull { it.source == BANGUMI }?.learnedSubjectId
        if (!run.isBangumiRow && realMalId != null && learnedSubjectId != null) {
            runSyncCatching {
                BangumiLocalMappingCache.put(
                    context,
                    realMalId,
                    BangumiIdNamespace.subjectTypeFor(entry.type),
                    learnedSubjectId
                )
            }
        }

        aggregate(context, prepared, results, isDelete = false)
    }

    /**
     * Kimlik + ayar + oturum bilgisini bir kez hazırlar. Ağa çıkan adım budur; altı kaynak
     * bloğu bu tek sonucu paylaşır.
     */
    private suspend fun prepare(
        context: Context,
        entry: MediaEntry,
        advancedScores: List<Double>?,
        skipBangumiWrite: Boolean,
        fields: Set<SyncField> = SyncField.ALL
    ): Prepared {
        val skipped = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val settings = runSyncCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
        val enabled = mapOf(
            "anilist" to (settings?.syncEnabledAnilist ?: true),
            "mal" to (settings?.syncEnabledMal ?: true),
            "simkl" to (settings?.syncEnabledSimkl ?: true),
            "kitsu" to (settings?.syncEnabledKitsu ?: true),
            "shikimori" to (settings?.syncEnabledShikimori ?: true),
            "bangumi" to (settings?.syncEnabledBangumi ?: true)
        )

        val aniListToken = ExternalAuthManager.getAniListToken(context)
        val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
        val simklToken = ExternalAuthManager.getSimklToken(context)
        val kitsuToken = ExternalAuthManager.getKitsuToken(context)
        val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
        val bangumiToken = if (enabled["bangumi"] == true) BangumiAuthStore.getValidToken(context) else null

        // Kimlik araması yalnızca BAĞLI kaynaklar için yapılır: bağlı olmayan servis
        // adına katalog araması yapmak eşitlemeyi geciktirir, sonuç vermez.
        val connected = ConnectedSources(
            aniList = enabled["anilist"] == true && !aniListToken.isNullOrBlank(),
            mal = enabled["mal"] == true && !malToken.isNullOrBlank(),
            kitsu = enabled["kitsu"] == true && !kitsuToken.isNullOrBlank(),
            shikimori = enabled["shikimori"] == true && !shikimoriToken.isNullOrBlank(),
            simkl = enabled["simkl"] == true && !simklToken.isNullOrBlank(),
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
            CrossProviderIdentityResolver.resolve(context, entry, connected = connected)
        }.getOrNull()?.mergedWith(fallbackIdentity) ?: fallbackIdentity

        // Bangumi'nin kendi koleksiyon yazması her zaman serbest; diğer beş kaynağa
        // yayılmak için doğrulanmış MAL/AniList kimliği gerekir.
        val allowFanOut = CrossSyncPolicy.allowFanOut(isBangumiRow, identity)
        if (isBangumiRow && !allowFanOut) {
            skipped.add(context.getString(R.string.account_sync_fanout_blocked_bangumi))
            // Çevrimdışı kimlik eksiği KALICI değildir: hataya yazılıp bekleyen-senkron
            // kuyruğuna girmezse Bangumi satırı bir daha hiç yayılmaz (kuyruk yalnızca
            // `errors` üzerinden çalışır). Ağ varken çözülemiyorsa ise gerçek atlamadır.
            if (!runCatching { NetworkMonitor(context).isCurrentlyOnline() }.getOrDefault(true)) {
                errors.add(context.getString(R.string.account_sync_offline_identity_pending))
            }
        }

        // Uzak çağrılar çözülen gerçek MAL kimliğiyle yapılır; YEREL satır kendi kaynak
        // alanını (stableId) korur — aksi halde satırın kendi listesindeki kimliği bozulur.
        val remoteEntry = withResolvedIds(entry, identity)
        persistNewlyResolvedIds(context, entry, identity)

        // ── Çelişki kapısı: YALNIZCA katalog çelişkisi (Rejected) yazımı durdurur ────
        // Satır MAL'ın kendisiyse kimlik kaynağından gelir → doğrulama gerekmez.
        // "Kanıt yok" (Unverifiable: yıl boş, katalog/kota/ağ sorunu) artık yazımı
        // durdurmaz; hedefin kendi resmî kimliği (AniList idMal, Kitsu mappings, …)
        // belirleyicidir. Eskiden burada Unverifiable da engelliyordu ve yıl alanı boş
        // olan her satır altı kaynağın beşine sessizce yazılmıyordu.
        val rowSource = canonicalMediaSourceId(entry.source)
        val realMalId = identity.malId
        val origin = CrossSyncPolicy.malIdOrigin(entry.source, identity)
        val verdict = if (realMalId != null && origin != CrossSyncPolicy.MalIdOrigin.NATIVE) {
            runSyncCatching {
                CrossSyncIdentityGuard.verifyMalId(
                    malId = realMalId,
                    // Movie/TvShow MAL'de anime-namespace'inde yaşar ("movie"/"tv");
                    // guard'ın Anime kümesi bu tipleri kapsar. Manga hariç Anime'ye sabit.
                    mediaType = if (entry.type == MediaType.Manga) MediaType.Manga else MediaType.Anime,
                    localTitles = CrossSyncIdentityGuard.aliasesOf(entry),
                    localYear = entry.year
                )
            }.getOrNull()
        } else null
        val verdictBlocked = verdict != null && CrossSyncIdentityGuard.blocksCrossWrite(verdict)
        if (verdictBlocked) {
            skipped.add(
                context.getString(
                    R.string.cross_sync_reason_identity_unverified,
                    realMalId.toString()
                )
            )
        }

        return Prepared(
            run = Run(
                context = context,
                entry = entry,
                remoteEntry = remoteEntry,
                identity = identity,
                advancedScores = advancedScores,
                skipBangumiWrite = skipBangumiWrite,
                allowFanOut = allowFanOut,
                rowSource = rowSource,
                isBangumiRow = isBangumiRow,
                enabled = enabled,
                tokens = mapOf(
                    "anilist" to aniListToken,
                    "mal" to malToken,
                    "simkl" to simklToken,
                    "kitsu" to kitsuToken,
                    "shikimori" to shikimoriToken,
                    "bangumi" to bangumiToken
                ),
                verdictBlocked = verdictBlocked,
                fields = fields
            ),
            skipped = skipped,
            errors = errors
        )
    }

    /**
     * Bir kaynağı zaman aşımına karşı korur: kilitlenen kaynak turu kilitlemez.
     *
     * Zaman aşımı `aggregate` içinde **hata** olarak da yazılır (bkz. `OUTCOME_TIMEOUT`
     * işleme) — böylece `SyncResult.needsRetry` `true` olur ve bekleyen-senkron kuyruğu
     * işi "başarılı" sayıp silmez.
     */
    private suspend fun timed(source: String, block: suspend () -> SourceResult): SourceResult =
        withTimeoutOrNull(SOURCE_TIMEOUT_MS) { block() }
            ?: SourceResult(source = source, outcome = OUTCOME_TIMEOUT)

    /** Görünen kaynak adı → kanonik sağlayıcı anahtarı. */
    private fun providerKeyOf(source: String): String = when (source) {
        ANILIST -> "anilist"
        MAL -> "mal"
        KITSU -> "kitsu"
        SHIKIMORI -> "shikimori"
        BANGUMI -> "bangumi"
        SIMKL -> "simkl"
        else -> source.lowercase()
    }

    private fun identitySkip(run: Run, source: String): SourceResult {
        val reason = runSyncCatching {
            run.context.getString(
                R.string.cross_sync_reason_identity_unverified,
                run.identity.malId?.toString().orEmpty()
            )
        }.getOrDefault("")
        return SourceResult(source, OUTCOME_SKIPPED_IDENTITY, skipped = listOfNotNull(reason.ifBlank { null }))
    }

    // ── AniList ─────────────────────────────────────────────────────────────────────
    private suspend fun syncAniList(run: Run): SourceResult {
        val context = run.context
        val token = run.token("anilist")
        when {
            !run.isOn("anilist") -> return SourceResult(ANILIST, OUTCOME_DISABLED)
            token.isNullOrBlank() -> return SourceResult(ANILIST, OUTCOME_NOT_CONNECTED)
            !CrossSyncPolicy.supportsAniList(run.entry.type) -> return SourceResult(ANILIST, OUTCOME_SKIPPED_TYPE)
            !run.allowFanOut -> return SourceResult(ANILIST, OUTCOME_SKIPPED_IDENTITY)
            !run.allows("anilist") -> return identitySkip(run, ANILIST)
        }

        val fields = run.fieldsFor("anilist")
        // Değişen alan yoksa istek atılmaz: hedefte uygulama dışında girilmiş puan/not ezilmesin.
        if (fields.isEmpty()) return SourceResult(ANILIST, OUTCOME_UP_TO_DATE)

        val update = runSyncCatching {
            PlatformRateLimiter.acquire("anilist")
            AniListSyncManager.updateAniListEntry(
                token = token,
                entry = run.remoteEntry,
                advancedScores = run.advancedScores,
                context = context,
                knownAniListMediaId = run.identity.aniListId,
                knownMalId = run.identity.malId,
                fields = fields,
                fullWrite = run.isFullWrite
            )
        }

        var entryId: Int? = null
        var synced = false
        val errors = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        update.onSuccess { remoteId ->
            if (remoteId != null && remoteId > 0) {
                entryId = remoteId
                synced = true
                // Yeni dönen aniListEntryId'yi yerel DB'ye yaz (orijinal satırın kimliği korunur).
                if (run.entry.aniListEntryId != remoteId) {
                    runSyncCatching {
                        val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                        val current = dao.getById(run.entry.id)?.toDomain() ?: run.entry
                        dao.update(current.copy(aniListEntryId = remoteId).toEntity())
                    }
                }
            } else {
                // null = "AniList'te hangi medya olduğu güvenle çözülemedi" — API hatası değil.
                skipped.add(context.getString(R.string.cross_sync_reason_anilist_media_id_unresolved))
            }
        }.onFailure { error ->
            errors.add(context.sourceFailure(ANILIST, error))
        }

        if (errors.isNotEmpty()) {
            return SourceResult(ANILIST, OUTCOME_FAILED, errors = errors, skipped = skipped)
        }
        // Liste kaydı yazılamadıysa favori sorgusu da anlamsızdır (iki fazladan istek atmayalım).
        if (!synced) {
            return SourceResult(ANILIST, OUTCOME_SKIPPED_IDENTITY, skipped = skipped)
        }

        // AniList favori durumu: diff'te iki yönlü, tam yazımda YALNIZ EKLEME yönünde.
        // (Tam yazımda yerel `isFavorite=false` iken uzaktaki favoriyi silmek veri kaybıydı.)
        val touchFavorite = if (run.isFullWrite) run.entry.isFavorite else SyncField.FAVORITE in fields
        if (!touchFavorite) {
            return SourceResult(ANILIST, OUTCOME_SYNCED, synced = true, aniListEntryId = entryId, skipped = skipped)
        }
        runSyncCatching {
            val remoteFav = AniListSyncManager.getAniListMediaFavoriteStatus(
                token, run.remoteEntry, context,
                knownAniListMediaId = run.identity.aniListId,
                knownMalId = run.identity.malId
            )
            if (remoteFav != null && remoteFav != run.entry.isFavorite) {
                check(
                    AniListSyncManager.toggleAniListFavourite(
                        token, run.remoteEntry, context,
                        knownAniListMediaId = run.identity.aniListId,
                        knownMalId = run.identity.malId
                    )
                ) {
                    context.getString(R.string.account_sync_anilist_update_unconfirmed)
                }
                true
            } else {
                false
            }
        }.onSuccess { changed ->
            if (changed) synced = true
        }.onFailure { error ->
            errors.add(
                context.getString(
                    R.string.account_sync_favorite_error,
                    error.message ?: context.getString(R.string.account_sync_unknown_error)
                )
            )
        }

        return SourceResult(
            source = ANILIST,
            outcome = when {
                errors.isNotEmpty() -> OUTCOME_FAILED
                synced -> OUTCOME_SYNCED
                else -> OUTCOME_SKIPPED_IDENTITY
            },
            errors = errors,
            skipped = skipped,
            synced = synced,
            aniListEntryId = entryId
        )
    }

    // ── MyAnimeList ─────────────────────────────────────────────────────────────────
    private suspend fun syncMal(run: Run): SourceResult {
        val context = run.context
        val token = run.token("mal")
        val realMalId = run.identity.malId
        when {
            !run.isOn("mal") -> return SourceResult(MAL, OUTCOME_DISABLED)
            token.isNullOrBlank() -> return SourceResult(MAL, OUTCOME_NOT_CONNECTED)
            !CrossSyncPolicy.supportsMal(run.entry.type) || !run.allowFanOut ->
                return SourceResult(MAL, if (run.allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY)
            !run.allows("mal") -> return identitySkip(run, MAL)
            realMalId == null -> return SourceResult(
                MAL,
                OUTCOME_SKIPPED_IDENTITY,
                skipped = listOf(context.getString(R.string.account_sync_no_valid_mal_id))
            )
        }

        val fields = run.fieldsFor("mal")
        if (fields.isEmpty()) return SourceResult(MAL, OUTCOME_UP_TO_DATE)
        return runSyncCatching {
            PlatformRateLimiter.acquire("mal")
            MalSyncManager.updateMalEntry(
                token = token, entry = run.remoteEntry, context = context,
                fields = fields, fullWrite = run.isFullWrite
            )
        }.fold(
            // `false` = gönderilecek geçerli alan yok (ör. yalnız parsiyel tarih değişti) →
            // "eşitlendi" demek yalan olurdu.
            onSuccess = { wrote ->
                if (wrote) SourceResult(MAL, OUTCOME_SYNCED, synced = true)
                else SourceResult(MAL, OUTCOME_UP_TO_DATE)
            },
            onFailure = { error ->
                SourceResult(MAL, OUTCOME_FAILED, errors = listOf(context.sourceFailure("MAL", error)))
            }
        )
    }

    // ── Simkl ───────────────────────────────────────────────────────────────────────
    private suspend fun syncSimkl(run: Run): SourceResult {
        val context = run.context
        val token = run.token("simkl")
        when {
            !run.isOn("simkl") -> return SourceResult(SIMKL, OUTCOME_DISABLED)
            token.isNullOrBlank() -> return SourceResult(SIMKL, OUTCOME_NOT_CONNECTED)
            !CrossSyncPolicy.supportsSimkl(run.entry.type) || !run.allowFanOut ->
                return SourceResult(SIMKL, if (run.allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY)
            !run.allows("simkl") -> return identitySkip(run, SIMKL)
        }

        val fields = run.fieldsFor("simkl")
        if (fields.isEmpty()) return SourceResult(SIMKL, OUTCOME_UP_TO_DATE)
        return runSyncCatching {
            PlatformRateLimiter.acquire("simkl")
            SimklSyncManager.syncEntryToSimkl(
                context, run.remoteEntry,
                knownMalId = run.identity.malId,
                fields = fields,
                fullWrite = run.isFullWrite
            )
        }.fold(
            onSuccess = { result ->
                when {
                    // Simkl'de desteklenmeyen alanlar (ör. yalnız cilt) süzüldüğünde hiçbir istek
                    // atılmamış olabilir: mesaj yoksa "eşitlendi" demeyiz.
                    result.isSuccess && result.messages.isEmpty() -> SourceResult(SIMKL, OUTCOME_UP_TO_DATE)
                    result.isSuccess -> SourceResult(SIMKL, OUTCOME_SYNCED, synced = true)
                    // Katalog eşleşmesi üretilemedi (medya Simkl'de yok / kimlik yok):
                    // hata değil atlandı — "Çevrimdışı kaydedildi" kuyruğuna düşmemeli.
                    result.unresolvedMedia -> SourceResult(SIMKL, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(SIMKL, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(SIMKL, OUTCOME_FAILED, errors = listOf(context.sourceFailure("Simkl", error)))
            }
        )
    }

    // ── Kitsu ───────────────────────────────────────────────────────────────────────
    private suspend fun syncKitsu(run: Run): SourceResult {
        val context = run.context
        val token = run.token("kitsu")
        when {
            !run.isOn("kitsu") -> return SourceResult(KITSU, OUTCOME_DISABLED)
            token.isNullOrBlank() -> return SourceResult(KITSU, OUTCOME_NOT_CONNECTED)
            !CrossSyncPolicy.supportsKitsu(run.entry.type) || !run.allowFanOut ->
                return SourceResult(KITSU, if (run.allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY)
            !run.allows("kitsu") -> return identitySkip(run, KITSU)
        }

        val fields = run.fieldsFor("kitsu")
        if (fields.isEmpty()) return SourceResult(KITSU, OUTCOME_UP_TO_DATE)
        return runSyncCatching {
            PlatformRateLimiter.acquire("kitsu")
            KitsuSyncManager.syncEntryToKitsu(
                context = context,
                entry = run.remoteEntry,
                knownKitsuMediaId = run.identity.kitsuId,
                fields = fields,
                fullWrite = run.isFullWrite
            )
        }.fold(
            onSuccess = { result ->
                when {
                    // Hiç istek atılmadı (yazılacak attribute kalmadı) → "eşitlendi" değil
                    // "değişiklik yok". Aksi hâlde arayüz yazılmamış bir alan için başarı gösterir.
                    result.errors.isEmpty() && result.messages.isEmpty() ->
                        SourceResult(KITSU, OUTCOME_UP_TO_DATE)
                    result.errors.isEmpty() -> SourceResult(KITSU, OUTCOME_SYNCED, synced = true)
                    // Kimlik bulunamadı: güvenli atlama, yeniden denenecek hata değil.
                    result.unresolvedMedia -> SourceResult(KITSU, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(KITSU, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(KITSU, OUTCOME_FAILED, errors = listOf(context.sourceFailure("Kitsu", error)))
            }
        )
    }

    // ── Shikimori ───────────────────────────────────────────────────────────────────
    private suspend fun syncShikimori(run: Run): SourceResult {
        val context = run.context
        val token = run.token("shikimori")
        val realMalId = run.identity.malId
        when {
            !run.isOn("shikimori") -> return SourceResult(SHIKIMORI, OUTCOME_DISABLED)
            token.isNullOrBlank() -> return SourceResult(SHIKIMORI, OUTCOME_NOT_CONNECTED)
            !CrossSyncPolicy.supportsShikimori(run.entry.type) || !run.allowFanOut ->
                return SourceResult(SHIKIMORI, if (run.allowFanOut) OUTCOME_SKIPPED_TYPE else OUTCOME_SKIPPED_IDENTITY)
            !run.allows("shikimori") -> return identitySkip(run, SHIKIMORI)
            realMalId == null && run.identity.shikimoriId == null -> return SourceResult(
                SHIKIMORI,
                OUTCOME_SKIPPED_IDENTITY,
                skipped = listOf(context.getString(R.string.account_sync_shikimori_id_not_found, run.entry.title))
            )
        }

        // Shikimori'nin kendi satırı: MAL kimliği bu tur çözülemese bile satırın kendi
        // target_id'si Shikimori yazması için yeterlidir (kendi kaynağına güncelleme
        // MAL çözümüne bağlı olmamalı). Çapraz satırlarda hedef yine MAL kimliğidir.
        val shikimoriEntry = if (realMalId == null) {
            run.remoteEntry.copy(malId = run.identity.shikimoriId)
        } else {
            run.remoteEntry
        }

        val fields = run.fieldsFor("shikimori")
        if (fields.isEmpty()) return SourceResult(SHIKIMORI, OUTCOME_UP_TO_DATE)
        return runSyncCatching {
            PlatformRateLimiter.acquire("shikimori")
            ShikimoriSyncManager.syncEntryToShikimori(
                context, shikimoriEntry,
                knownMalId = run.identity.malId,
                fields = fields,
                fullWrite = run.isFullWrite
            )
        }.fold(
            onSuccess = { result ->
                when {
                    result.errors.isEmpty() -> SourceResult(SHIKIMORI, OUTCOME_SYNCED, synced = true)
                    result.unresolvedMedia -> SourceResult(SHIKIMORI, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(SHIKIMORI, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(SHIKIMORI, OUTCOME_FAILED, errors = listOf(context.sourceFailure("Shikimori", error)))
            }
        )
    }

    // ── Bangumi ─────────────────────────────────────────────────────────────────────
    private suspend fun syncBangumi(run: Run): SourceResult {
        val context = run.context
        val token = run.token("bangumi")
        when {
            run.skipBangumiWrite -> return SourceResult(BANGUMI, OUTCOME_WRITTEN_BY_CALLER)
            !run.isOn("bangumi") -> return SourceResult(BANGUMI, OUTCOME_DISABLED)
            token.isNullOrBlank() -> return SourceResult(BANGUMI, OUTCOME_NOT_CONNECTED)
            !CrossSyncPolicy.supportsBangumi(run.entry.type) -> return SourceResult(BANGUMI, OUTCOME_SKIPPED_TYPE)
            !run.allows("bangumi") -> return identitySkip(run, BANGUMI)
        }

        val fields = run.fieldsFor("bangumi")
        if (fields.isEmpty()) return SourceResult(BANGUMI, OUTCOME_UP_TO_DATE)
        return runSyncCatching {
            PlatformRateLimiter.acquire("bangumi")
            BangumiSyncManager.syncEntryToBangumi(
                context = context,
                // Bangumi satırları kendi stableId'siyle yazılır; çözülen subject kimliği
                // çağrıya ayrı olarak verilir.
                entry = run.entry,
                knownSubjectId = run.identity.bangumiSubjectId,
                fields = fields,
                fullWrite = run.isFullWrite
            )
        }.fold(
            onSuccess = { result ->
                when {
                    result.errors.isEmpty() -> SourceResult(
                        BANGUMI,
                        OUTCOME_SYNCED,
                        synced = true,
                        learnedSubjectId = result.subjectId
                    )
                    result.unresolvedSubject -> SourceResult(BANGUMI, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(BANGUMI, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(BANGUMI, OUTCOME_FAILED, errors = listOf(context.sourceFailure("Bangumi", error)))
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // SİLME
    // ────────────────────────────────────────────────────────────────────────────────

    suspend fun deleteEntry(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        // Silme de tıpkı güncelleme gibi DOĞRULANMIŞ kimlikle çalışır: satır ilk kez
        // siliniyorsa (daha önce hiç yayılmamış içe aktarım satırı gibi) kimlik daha önce
        // çözülmemiş olabilir ve eski "ağ araması yok" kuralı yalnızca satırın kendi
        // kaynağından silinmesine izin veriyor, diğer kaynaklarda hayalet kayıt kalıyordu.
        val prepared = prepare(context, entry, advancedScores = null, skipBangumiWrite = false, fields = SyncField.ALL)
        val run = prepared.run

        val results = coroutineScope {
            listOf(
                async { timed(ANILIST) { deleteAniList(run) } },
                async { timed(MAL) { deleteMal(run) } },
                async { timed(SIMKL) { deleteSimkl(run) } },
                async { timed(KITSU) { deleteKitsu(run) } },
                async { timed(SHIKIMORI) { deleteShikimori(run) } },
                async { timed(BANGUMI) { deleteBangumi(run) } }
            ).awaitAll()
        }

        aggregate(context, prepared, results, isDelete = true)
    }

    private suspend fun deleteAniList(run: Run): SourceResult {
        val context = run.context
        val token = run.token("anilist")
        val applicable = run.isAnimeOrManga && run.allowFanOut && run.allows("anilist")
        if (token.isNullOrBlank() || !run.isOn("anilist") || !applicable) {
            return SourceResult(ANILIST, deleteGateOutcome(run.isOn("anilist"), !token.isNullOrBlank(), applicable))
        }
        return runSyncCatching {
            PlatformRateLimiter.acquire("anilist")
            AniListSyncManager.deleteAniListEntry(token = token, entry = run.remoteEntry, context = context)
        }.fold(
            onSuccess = { SourceResult(ANILIST, OUTCOME_SYNCED, synced = true) },
            onFailure = { error ->
                val detail = error.message ?: context.getString(R.string.account_sync_unknown_error)
                // Medya kimliği çözülemedi → atlandı (yeniden deneme faydasız).
                if (detail == context.getString(R.string.account_sync_anilist_media_id_unresolved)) {
                    SourceResult(ANILIST, OUTCOME_SKIPPED_IDENTITY, skipped = listOf(detail))
                } else {
                    SourceResult(
                        ANILIST,
                        OUTCOME_FAILED,
                        errors = listOf(context.getString(R.string.account_sync_delete_error, "AniList", detail))
                    )
                }
            }
        )
    }

    private suspend fun deleteMal(run: Run): SourceResult {
        val context = run.context
        val token = run.token("mal")
        val applicable = run.isAnimeOrManga && run.identity.malId != null &&
            run.allowFanOut && run.allows("mal")
        if (token.isNullOrBlank() || !run.isOn("mal") || !applicable) {
            val skipped = if (run.identity.malId == null && run.isOn("mal") && !token.isNullOrBlank() && run.isAnimeOrManga) {
                listOf(context.getString(R.string.account_sync_no_valid_mal_id))
            } else {
                emptyList()
            }
            return SourceResult(
                MAL,
                deleteGateOutcome(run.isOn("mal"), !token.isNullOrBlank(), applicable),
                skipped = skipped
            )
        }
        return runSyncCatching {
            PlatformRateLimiter.acquire("mal")
            MalSyncManager.deleteMalEntry(token = token, entry = run.remoteEntry, context = context)
        }.fold(
            onSuccess = { SourceResult(MAL, OUTCOME_SYNCED, synced = true) },
            onFailure = { error ->
                SourceResult(
                    MAL,
                    OUTCOME_FAILED,
                    errors = listOf(
                        context.getString(
                            R.string.account_sync_delete_error,
                            "MAL",
                            error.message ?: context.getString(R.string.account_sync_unknown_error)
                        )
                    )
                )
            }
        )
    }

    private suspend fun deleteSimkl(run: Run): SourceResult {
        val context = run.context
        val token = run.token("simkl")
        val applicable = run.entry.type != MediaType.Manga && run.allowFanOut && run.allows("simkl")
        if (token.isNullOrBlank() || !run.isOn("simkl") || !applicable) {
            return SourceResult(SIMKL, deleteGateOutcome(run.isOn("simkl"), !token.isNullOrBlank(), applicable))
        }
        return runSyncCatching {
            PlatformRateLimiter.acquire("simkl")
            SimklSyncManager.deleteEntryFromSimkl(context, run.remoteEntry, knownMalId = run.identity.malId)
        }.fold(
            onSuccess = { result ->
                when {
                    result.isSuccess -> SourceResult(SIMKL, OUTCOME_SYNCED, synced = true)
                    // Kimlik/katalog eşleşmesi üretilemedi: hata değil, güvenli atlama.
                    result.unresolvedMedia -> SourceResult(SIMKL, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(SIMKL, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(
                    SIMKL,
                    OUTCOME_FAILED,
                    errors = listOf(
                        context.getString(
                            R.string.account_sync_delete_error,
                            "Simkl",
                            error.message ?: context.getString(R.string.account_sync_unknown_error)
                        )
                    )
                )
            }
        )
    }

    private suspend fun deleteKitsu(run: Run): SourceResult {
        val context = run.context
        val token = run.token("kitsu")
        val applicable = run.isAnimeOrManga && run.allowFanOut && run.allows("kitsu")
        if (token.isNullOrBlank() || !run.isOn("kitsu") || !applicable) {
            return SourceResult(KITSU, deleteGateOutcome(run.isOn("kitsu"), !token.isNullOrBlank(), applicable))
        }
        return runSyncCatching {
            PlatformRateLimiter.acquire("kitsu")
            KitsuSyncManager.deleteEntryFromKitsu(
                context = context,
                entry = run.remoteEntry,
                // Çapraz kimlik katmanının çözdüğü Kitsu medya kimliği silmeye de taşınır;
                // eski davranış yalnızca satırın namespace'ine bakıyordu ve bu kimlik
                // yok sayılıyordu ("Kitsu: hata").
                knownKitsuMediaId = run.identity.kitsuId
            )
        }.fold(
            onSuccess = { result ->
                when {
                    result.errors.isEmpty() -> SourceResult(KITSU, OUTCOME_SYNCED, synced = true)
                    result.unresolvedMedia -> SourceResult(KITSU, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(KITSU, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(
                    KITSU,
                    OUTCOME_FAILED,
                    errors = listOf(
                        context.getString(
                            R.string.account_sync_delete_error,
                            "Kitsu",
                            error.message ?: context.getString(R.string.account_sync_unknown_error)
                        )
                    )
                )
            }
        )
    }

    private suspend fun deleteShikimori(run: Run): SourceResult {
        val context = run.context
        val token = run.token("shikimori")
        val hasTarget = run.identity.malId != null || run.identity.shikimoriId != null
        val applicable = run.isAnimeOrManga && run.allowFanOut && hasTarget && run.allows("shikimori")
        if (token.isNullOrBlank() || !run.isOn("shikimori") || !applicable) {
            val skipped = if (!hasTarget && run.isOn("shikimori") && !token.isNullOrBlank() && run.isAnimeOrManga) {
                listOf(context.getString(R.string.account_sync_shikimori_id_not_found, run.entry.title))
            } else {
                emptyList()
            }
            return SourceResult(
                SHIKIMORI,
                deleteGateOutcome(run.isOn("shikimori"), !token.isNullOrBlank(), applicable),
                skipped = skipped
            )
        }
        return runSyncCatching {
            PlatformRateLimiter.acquire("shikimori")
            ShikimoriSyncManager.deleteEntryFromShikimori(context, run.remoteEntry, knownMalId = run.identity.malId)
        }.fold(
            onSuccess = { result ->
                when {
                    result.errors.isEmpty() -> SourceResult(SHIKIMORI, OUTCOME_SYNCED, synced = true)
                    // Doğrulanmış hedef çözülemedi: atlandı. Diğer hatalar (ör. silme
                    // reddedildi) gerçek hatadır ve yeniden denenmelidir.
                    result.unresolvedMedia -> SourceResult(SHIKIMORI, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(SHIKIMORI, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(
                    SHIKIMORI,
                    OUTCOME_FAILED,
                    errors = listOf(
                        context.getString(
                            R.string.account_sync_delete_error,
                            "Shikimori",
                            error.message ?: context.getString(R.string.account_sync_unknown_error)
                        )
                    )
                )
            }
        )
    }

    private suspend fun deleteBangumi(run: Run): SourceResult {
        val context = run.context
        val token = run.token("bangumi")
        val applicable = BangumiSyncManager.supportsMediaType(run.entry.type) && run.allows("bangumi")
        if (token.isNullOrBlank() || !run.isOn("bangumi") || !applicable) {
            return SourceResult(BANGUMI, deleteGateOutcome(run.isOn("bangumi"), !token.isNullOrBlank(), applicable))
        }
        return runSyncCatching {
            PlatformRateLimiter.acquire("bangumi")
            BangumiSyncManager.deleteEntryFromBangumi(
                context = context,
                entry = run.entry,
                knownSubjectId = run.identity.bangumiSubjectId
            )
        }.fold(
            onSuccess = { result ->
                when {
                    result.errors.isEmpty() -> SourceResult(BANGUMI, OUTCOME_SYNCED, synced = true)
                    // subject eşleşmesi üretilemedi: atlandı. Gerçek yazım hataları
                    // (ör. ağ/HTTP) hâlâ yeniden denenmelidir — eşitleme yoluyla aynı.
                    result.unresolvedSubject -> SourceResult(BANGUMI, OUTCOME_SKIPPED_IDENTITY, skipped = result.errors)
                    else -> SourceResult(BANGUMI, OUTCOME_FAILED, errors = result.errors)
                }
            },
            onFailure = { error ->
                SourceResult(
                    BANGUMI,
                    OUTCOME_FAILED,
                    errors = listOf(
                        context.getString(
                            R.string.account_sync_delete_error,
                            "Bangumi",
                            error.message ?: context.getString(R.string.account_sync_unknown_error)
                        )
                    )
                )
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // TOPLAMA / YARDIMCILAR
    // ────────────────────────────────────────────────────────────────────────────────

    private fun aggregate(
        context: Context,
        prepared: Prepared,
        results: List<SourceResult>,
        isDelete: Boolean
    ): SyncResult {
        val errors = prepared.errors.toMutableList()
        val skipped = prepared.skipped.toMutableList()
        val outcomes = LinkedHashMap<String, String>()
        val syncedSources = mutableListOf<String>()
        var aniListEntryId: Int? = null

        results.forEach { result ->
            outcomes[result.source] = result.outcome
            if (result.outcome == OUTCOME_TIMEOUT) {
                errors.add(context.getString(R.string.account_sync_source_timeout, result.source))
            }
            errors.addAll(result.errors)
            skipped.addAll(result.skipped)
            if (result.synced) syncedSources.add(result.source)
            if (result.source == ANILIST && result.aniListEntryId != null) aniListEntryId = result.aniListEntryId
        }

        // "Bağlı değil" sonuçları arasından YALNIZ gerçekten kullanılanları ayır.
        val rowSourceKey = providerKeyOf(prepared.run.rowSource)
        val connectionGapSources = results
            .filter { it.outcome == OUTCOME_NOT_CONNECTED }
            .map { it.source }
            .filter { source ->
                val key = providerKeyOf(source)
                key == rowSourceKey ||
                    (key == "anilist" && prepared.run.entry.aniListEntryId != null) ||
                    (key == "simkl" && (prepared.run.entry.simklId ?: 0) > 0)
            }

        val messages = mutableListOf<String>()
        val formatted = formatSyncSources(context, syncedSources, isDelete)
        if (formatted.isNotEmpty()) messages.add(formatted)
        if (skipped.isNotEmpty()) {
            messages.add(
                context.getString(
                    R.string.account_sync_sources_skipped,
                    skipped.distinct().joinToString(" • ")
                )
            )
        }

        return SyncResult(
            messages = messages,
            errors = errors,
            aniListEntryId = aniListEntryId,
            connectionGapSources = connectionGapSources,
            skipped = skipped,
            outcomes = outcomes
        )
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
        // YEREL SATIR = kullanıcının kalıcı kaydı; uzak bulut kayıtlarının TUTAMAZLARI
        // (kimlikler) buraya, yalnız alan BOŞKEN, ezmeden yazılır. Her kaynak kendi
        // tutamaç alanını kullanır: malId (gerçek), aniListEntryId (senkron dönüşü ayrıca
        // yazar), simklId, tmdbId + kaynak-özel önbellekler (Kitsu kütüphane girdisi,
        // Shikimori rateId, Bangumi eşleme).
        val malId = CrossProviderIds.realMalIdOf(ids.malId)
        val newSimklId = ids.simklId?.takeIf { it > 0 && entry.simklId == null }
        val newTmdbId = ids.tmdbId?.takeIf { it > 0 && entry.tmdbId == null }
        val needsRowWrite = (malId != null && entry.malId == null) || newSimklId != null || newTmdbId != null
        if (needsRowWrite && entry.id > 0) {
            runSyncCatching {
                val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                val current = dao.getById(entry.id)?.toDomain() ?: entry
                dao.update(
                    current.copy(
                        malId = if (current.malId == null) malId else current.malId,
                        simklId = current.simklId ?: newSimklId,
                        tmdbId = current.tmdbId ?: newTmdbId
                    ).toEntity()
                )
            }
        }
        if (malId != null && ids.bangumiSubjectId != null && entry.type != MediaType.Movie && entry.type != MediaType.TvShow) {
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
        val outcomes: Map<String, String> = emptyMap(),
        /**
         * "Bağlı değil" olduğu için yazılamayan **ve gerçekten kullanılan** kaynaklar.
         *
         * Kullanıcının hiç bağlamadığı bir servis (ör. Bangumi hesabı yok) buraya GİRMEZ;
         * aksi hâlde her düzenlemede "hesap bağlı değil" uyarısı çıkardı. Yalnız satırın
         * kendi kaynağı ya da satırda o sağlayıcıya ait bir kayıt kimliği varsa (AniList
         * `aniListEntryId`, Simkl `simklId`) bu bir boşluk sayılır.
         */
        val connectionGapSources: List<String> = emptyList()
    ) {
        /** En az bir kaynak gerçekten yazıldı mı? (kısmi başarı bildirimi için) */
        val anySynced: Boolean
            get() = outcomes.values.any { it == OUTCOME_SYNCED || it == OUTCOME_WRITTEN_BY_CALLER }

        /**
         * En az bir kaynak "bağlı değil" dedi (oturum yok / token süresi dolmuş).
         *
         * Bu bir API hatası değildir ama **kaybedilecek bir değişikliktir**: kullanıcı
         * hesabı yeniden bağladığında yazılması gerekir. Eskiden `needsRetry` yalnız
         * `errors`e baktığı için bu iş "başarılı" sayılıp kuyruktan SİLİNİYORDU →
         * token'ı süresi dolmuşken yapılan düzenleme o sağlayıcıya bir daha hiç gitmiyordu.
         * "Kapalı" (kullanıcı o kaynağın eşitlemesini kapatmış) bu kapsama GİRMEZ.
         */
        val hasConnectionGap: Boolean
            get() = connectionGapSources.isNotEmpty()

        /** Yalnızca gerçek hatalar ve bağlantı boşlukları yeniden denemeyi haklı çıkarır. */
        val needsRetry: Boolean
            get() = CrossSyncPolicy.shouldRetry(errors, skipped) || hasConnectionGap
    }
}

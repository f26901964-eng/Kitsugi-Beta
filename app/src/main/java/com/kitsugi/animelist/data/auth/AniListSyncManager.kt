package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

object AniListSyncManager {
    @Volatile private var cachedAniListUserId: Int? = null
    @Volatile private var cachedAniListToken: String? = null

    /**
     * Oturum değiştiğinde (çıkış / farklı hesap) kimlik ve kullanıcı önbelleklerini
     * boşaltır: eski hesabın doğrulanmış kimlikleri yeni hesaba yazım yapmasın.
     */
    fun clearSessionCaches() {
        cachedAniListUserId = null
        cachedAniListToken = null
        identityVerifyCache.clear()
        malIdToAniListCache.clear()
    }

    fun updateAniListEntry(
        token: String,
        entry: MediaEntry,
        advancedScores: List<Double>? = null,
        context: Context,
        /**
         * Çapraz kimlik katmanının ([CrossProviderIdentityResolver]) önceden çözdüğü AniList
         * medya kimliği. Verilmezse eski zincir (yerel kimlik → MAL `idMal` → ARM) çalışır.
         * Verilse bile yazmadan önce [verifyAniListMediaIdentity] ile doğrulanır.
         */
        knownAniListMediaId: Int? = null
    ): Int? {
        require(entry.type == MediaType.Anime || entry.type == MediaType.Manga) {
            context.getString(R.string.account_sync_anilist_type_unsupported)
        }
        val existingEntryId: Int? = entry.aniListEntryId?.takeIf { it > 0 }

        val mediaId: Int? = if (existingEntryId == null) {
            // Önceden çözülmüş kimlik önce denenir: Bangumi köprüsü bu kimliği AniList'in kendi
            // kataloğunda başlık+yıl puanlamasıyla bulmuş olabilir; buna rağmen yerel zincir
            // (MAL doğrulaması, Jikan başlıkları) aynı kaydı Çince görünen başlık yüzünden
            // reddedebiliyordu ve çözülmüş kimlik çöpe gidiyordu. Doğrulama yine yapılır.
            val verifiedKnownId = knownAniListMediaId?.takeIf { it > 0 }?.takeIf { candidate ->
                val mediaTypeGql = if (entry.type == MediaType.Manga) "MANGA" else "ANIME"
                verifyAniListMediaIdentityCached(
                    token, candidate, mediaTypeGql, entry,
                    CrossSyncIdentityGuard.aliasesOf(entry), context
                )
            }
            verifiedKnownId ?: resolveAniListMediaId(token = token, entry = entry, context = context)
        } else {
            val verifiedMediaId = resolveAniListMediaIdForEntry(
                token = token,
                entryId = existingEntryId,
                entry = entry,
                context = context,
                knownAniListMediaId = knownAniListMediaId
            ) ?: error(context.getString(R.string.account_sync_anilist_entry_identity_mismatch, entry.title))
            // Existing list-entry IDs are only mutation handles. Verify the attached media before
            // updating status, progress, score, or other fields.
            verifiedMediaId
        }

        // Null yalnızca "AniList'te hangi medya olduğu güvenle çözülemedi" anlamına gelir
        // (atlanacak kayıt). Mutasyonun başarısız olması aşağıda istisna ile bildirilir.
        if (existingEntryId == null && mediaId == null) return null

        val scoreRaw = SyncScores.tenPoint(entry.score)?.times(10)
        val scoreVariableDefinition = if (scoreRaw != null) "${'$'}scoreRaw: Int," else ""
        val scoreArgument = if (scoreRaw != null) "scoreRaw: ${'$'}scoreRaw," else ""
        val query = """
            mutation (
                ${'$'}id: Int,
                ${'$'}mediaId: Int,
                ${'$'}status: MediaListStatus,
                $scoreVariableDefinition
                ${'$'}progress: Int,
                ${'$'}progressVolumes: Int,
                ${'$'}startedAt: FuzzyDateInput,
                ${'$'}completedAt: FuzzyDateInput,
                ${'$'}private: Boolean,
                ${'$'}notes: String,
                ${'$'}repeat: Int,
                ${'$'}hiddenFromStatusLists: Boolean,
                ${'$'}advancedScores: [Float]
            ) {
                SaveMediaListEntry(
                    id: ${'$'}id,
                    mediaId: ${'$'}mediaId,
                    status: ${'$'}status,
                    $scoreArgument
                    progress: ${'$'}progress,
                    progressVolumes: ${'$'}progressVolumes,
                    startedAt: ${'$'}startedAt,
                    completedAt: ${'$'}completedAt,
                    private: ${'$'}private,
                    notes: ${'$'}notes,
                    repeat: ${'$'}repeat,
                    hiddenFromStatusLists: ${'$'}hiddenFromStatusLists,
                    advancedScores: ${'$'}advancedScores
                ) {
                    id
                    status
                    score
                    progress
                }
            }
        """.trimIndent()

        val variables = JSONObject()
        if (existingEntryId != null) {
            variables.put("id", existingEntryId)
        } else {
            variables.put("mediaId", mediaId)
        }
        variables
            .put("status", entry.status.toAniListStatus())
            .put("progress", entry.progress)
            .put("progressVolumes", entry.volumeProgress)
            .put("private", entry.isPrivate)
            .put("notes", entry.notes ?: "")
            .put("repeat", entry.repeatCount)
            .put("hiddenFromStatusLists", entry.isHiddenFromStatusLists)

        if (scoreRaw != null) {
            variables.put("scoreRaw", scoreRaw)
        }

        val startedAt = entry.startDate.toAniListFuzzyDate()
        if (startedAt != null) {
            variables.put("startedAt", startedAt)
        }

        val completedAt = entry.endDate.toAniListFuzzyDate()
        if (completedAt != null) {
            variables.put("completedAt", completedAt)
        }

        if (advancedScores != null) {
            val jsonArray = org.json.JSONArray()
            advancedScores.forEach { jsonArray.put(it) }
            variables.put("advancedScores", jsonArray)
        }

        val response = postAniList(
            token = token,
            query = query,
            variables = variables,
            context = context
        )

        return JSONObject(response)
            .optJSONObject("data")
            ?.optJSONObject("SaveMediaListEntry")
            ?.optInt("id", 0)
            ?.takeIf { it > 0 }
            ?: throw IllegalStateException(
                context.getString(R.string.account_sync_anilist_entry_id_missing, entry.title)
            )
    }

    /**
     * AniList'teki güncel favori durumunu sorgular.
     */
    fun getAniListMediaFavoriteStatus(
        token: String,
        entry: MediaEntry,
        context: Context,
        knownAniListMediaId: Int? = null
    ): Boolean? {
        val aniListMediaId: Int = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token = token, entry = entry, context = context)
            ?: return null

        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    isFavourite
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("id", aniListMediaId)

        return runSyncCatching {
            val response = postAniList(token = token, query = query, variables = variables, context = context)
            JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optBoolean("isFavourite", false)
        }.getOrNull()
    }

    /**
     * AniList'te favori toggle yapar.
     * ToggleFavourite mutasyonu ile anime/manga ID'sine göre toggle eder.
     * Bu fonksiyon sadece isFavorite değeri değiştiğinde çağrılmalı.
     * @return true = başarılı, false = API hatası veya mediaId bulunamadı
     */
    fun toggleAniListFavourite(
        token: String,
        entry: MediaEntry,
        context: Context,
        knownAniListMediaId: Int? = null
    ): Boolean {
        val aniListMediaId: Int? = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token = token, entry = entry, context = context)

        if (aniListMediaId == null) return false

        val isAnime = entry.type != MediaType.Manga
        val mutationArg = if (isAnime) "\$animeId: Int" else "\$mangaId: Int"
        val mutationParam = if (isAnime) "animeId: \$animeId" else "mangaId: \$mangaId"
        val varKey = if (isAnime) "animeId" else "mangaId"

        val query = """
            mutation ($mutationArg) {
                ToggleFavourite($mutationParam) {
                    anime { nodes { id } }
                    manga { nodes { id } }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put(varKey, aniListMediaId)

        return runSyncCatching {
            postAniList(token = token, query = query, variables = variables, context = context)
            true
        }.getOrDefault(false)
    }

    fun deleteAniListEntry(
        token: String,
        entry: MediaEntry,
        context: Context
    ) {
        val existingEntryId = entry.aniListEntryId?.takeIf { it > 0 }
        val entryId: Int? = if (existingEntryId != null) {
            resolveAniListMediaIdForEntry(token, existingEntryId, entry, context)
                ?: error(context.getString(R.string.account_sync_anilist_entry_identity_mismatch, entry.title))
            existingEntryId
        } else {
            val mediaId = resolveAniListMediaId(token = token, entry = entry, context = context)
                ?: error(context.getString(R.string.account_sync_anilist_media_id_unresolved))
            val userId = fetchAniListUserId(token, context)

            val findQuery = """
                query (
                    ${'$'}mediaId: Int,
                    ${'$'}userId: Int
                ) {
                    MediaList(
                        mediaId: ${'$'}mediaId,
                        userId: ${'$'}userId
                    ) {
                        id
                    }
                }
            """.trimIndent()

            val findVariables = JSONObject()
                .put("mediaId", mediaId)
                .put("userId", userId)

            val findResponse = postAniList(
                token = token,
                query = findQuery,
                variables = findVariables,
                context = context
            )

            JSONObject(findResponse)
                .optJSONObject("data")
                ?.optJSONObject("MediaList")
                ?.optInt("id", 0)
                ?.takeIf { it > 0 }
        }

        entryId ?: return

        val deleteQuery = """
            mutation (
                ${'$'}entryId: Int
            ) {
                DeleteMediaListEntry(
                    id: ${'$'}entryId
                ) {
                    deleted
                }
            }
        """.trimIndent()

        val deleteVariables = JSONObject()
            .put("entryId", entryId)

        postAniList(
            token = token,
            query = deleteQuery,
            variables = deleteVariables,
            context = context
        )
    }

    /** Resolves a list-entry handle only after proving which AniList media it belongs to. */
    private fun resolveAniListMediaIdForEntry(
        token: String,
        entryId: Int,
        entry: MediaEntry,
        context: Context,
        knownAniListMediaId: Int? = null
    ): Int? = runSyncCatching {
        if (entryId <= 0) return@runSyncCatching null
        val query = """
            query (${'$'}id: Int) {
                MediaList(id: ${'$'}id) {
                    id
                    userId
                    mediaId
                    media { id }
                }
            }
        """.trimIndent()
        val response = postAniList(
            token = token,
            query = query,
            variables = JSONObject().put("id", entryId),
            context = context
        )
        val listEntry = JSONObject(response)
            .optJSONObject("data")
            ?.optJSONObject("MediaList") ?: return@runSyncCatching null
        if (listEntry.optInt("id", 0) != entryId) return@runSyncCatching null
        if (listEntry.optInt("userId", 0) != fetchAniListUserId(token, context)) return@runSyncCatching null
        val mediaId = listEntry.optJSONObject("media")?.optInt("id", 0)?.takeIf { it > 0 }
            ?: return@runSyncCatching null
        if (listEntry.optInt("mediaId", 0) != mediaId) return@runSyncCatching null
        // Liste kaydının bağlı olduğu medya, çapraz katmanın çözdüğü kimlikle de doğrulanabilir;
        // yerel zincir başlık/yıl katılıkları yüzünden aynı medyayı bulamayabiliyordu.
        val expectedMediaId = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token, entry, context)
            ?: return@runSyncCatching null
        mediaId.takeIf { it == expectedMediaId }
    }.getOrNull()

    fun resolveAniListMediaId(
        token: String,
        entry: MediaEntry,
        context: Context
    ): Int? {
        val mediaTypeGql = when (entry.type) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "ANIME"
            MediaType.Manga -> "MANGA"
        }
        val realMalId = MediaIdentity.realMalId(entry)
        val nativeAniListId = MediaIdentity.aniListId(entry)
        val verifiedTitleAliases = if (nativeAniListId != null) {
            CrossSyncIdentityGuard.aliasesOf(entry)
        } else {
            if (realMalId == null) return null
            val malVerdict = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                CrossSyncIdentityGuard.verifyMalId(
                    malId = realMalId,
                    mediaType = entry.type,
                    localTitles = CrossSyncIdentityGuard.aliasesOf(entry),
                    localYear = entry.year
                )
            } as? CrossSyncIdentityGuard.Verdict.Verified ?: return null
            malVerdict.matchedLocalTitles
        }
        val rawKitsuId = MediaIdentity.kitsuId(entry)

        // A stored AniList media ID is source-native but still checked against type/title/year before add/favorite writes.
        if (nativeAniListId != null) {
            return nativeAniListId.takeIf {
                verifyAniListMediaIdentityCached(token, it, mediaTypeGql, entry, verifiedTitleAliases, context)
            }
        }

        // Resolve by real MAL ID, then verify AniList's returned type/title/year before writing.
        // NOT: `idMal → AniList id` eşlemesi katalogda sabittir; her düzenlemede yeniden
        // sorgulamak kotayı boşa harcıyordu. Sonuç 30 dk önbellekte tutulur.
        if (realMalId != null) {
            val malKey = "$mediaTypeGql:$realMalId"
            val cached = malIdToAniListCache.get(malKey)
            val resolved = if (cached != null && cached > 0) {
                cached
            } else {
                val query = """
                    query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                        Media(idMal: ${'$'}idMal, type: ${'$'}type) { id }
                    }
                """.trimIndent()
                val variables = JSONObject().put("idMal", realMalId).put("type", mediaTypeGql)
                val fetched = runSyncCatching {
                    val response = postAniList(token = token, query = query, variables = variables, context = context)
                    JSONObject(response).optJSONObject("data")?.optJSONObject("Media")?.optInt("id", 0)?.takeIf { it > 0 }
                }.getOrNull()
                if (fetched != null) malIdToAniListCache.put(malKey, fetched, IDENTITY_CACHE_TTL_MS)
                fetched
            }
            if (resolved != null) {
                return resolved.takeIf {
                    verifyAniListMediaIdentityCached(token, it, mediaTypeGql, entry, verifiedTitleAliases, context)
                }
            }
        }

        // ARM is another cross-reference, not identity proof. Verify its candidate in AniList as well.
        val armId = if (realMalId != null || rawKitsuId != null) {
            runSyncCatching {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                        malId = realMalId,
                        aniListId = null,
                        tmdbId = null,
                        mediaType = entry.type,
                        kitsuId = rawKitsuId
                    ).aniListId
                }
            }.getOrNull()
        } else null
        if (
            armId != null && armId > 0 &&
            verifyAniListMediaIdentityCached(token, armId, mediaTypeGql, entry, verifiedTitleAliases, context)
        ) {
            return armId
        }

        // A search rank is not an identity mapping. Never mutate the first fuzzy result.
        return null
    }

    // ─── Kimlik doğrulama önbelleği ────────────────────────────────────────────
    // Her düzenleme (bölüm +1, puan, durum) eskiden AniList'e 4-6 istek atıyordu:
    // MediaList sorgusu + Viewer + idMal çözümü + kimlik doğrulama + mutasyon
    // (+ favori durumu sorgusu). 30 istek/dk kotasında bu, dakikada ~6 düzenlemede
    // hız sınırına takılmak demekti — kullanıcının gördüğü "sürekli AniList sorunu".
    // Kimlik doğrulama sonuçları (medya kimliği sabit bir gerçek) kısa ömürlü
    // önbelleğe alınır: aynı kayıt üst üste düzenlendiğinde AĞA ÇIKILMAZ.
    private class TtlCache<T>(private val maxSize: Int = 500) {
        private val map = ConcurrentHashMap<String, Pair<Long, T>>()

        fun get(key: String): T? {
            val hit = map[key] ?: return null
            return if (System.currentTimeMillis() > hit.first) {
                map.remove(key)
                null
            } else {
                hit.second
            }
        }

        fun put(key: String, value: T, ttlMs: Long) {
            if (map.size >= maxSize) map.clear()
            map[key] = (System.currentTimeMillis() + ttlMs) to value
        }

        fun clear() = map.clear()
    }

    private val identityVerifyCache = TtlCache<Boolean>()
    private val malIdToAniListCache = TtlCache<Int>()

    private const val IDENTITY_CACHE_TTL_MS = 30 * 60 * 1000L   // doğrulanan kimlik 30 dk
    private const val IDENTITY_NEGATIVE_TTL_MS = 3 * 60 * 1000L // doğrulanamayan 3 dk (anlık tekrar denemeyi önler)

    /** Kimlik doğrulamasını önbellekli çalıştırır (aynı medya için tekrar ağa çıkmaz). */
    private fun verifyAniListMediaIdentityCached(
        token: String,
        mediaId: Int,
        expectedType: String,
        entry: MediaEntry,
        expectedTitles: Collection<String>,
        context: Context
    ): Boolean {
        val key = listOf(
            mediaId,
            expectedType,
            MediaIdentity.realMalId(entry) ?: 0,
            entry.year ?: 0,
            expectedTitles.map { it.lowercase() }.sorted().joinToString("|")
        ).joinToString("#")
        identityVerifyCache.get(key)?.let { return it }
        val verified = verifyAniListMediaIdentity(token, mediaId, expectedType, entry, expectedTitles, context)
        identityVerifyCache.put(
            key,
            verified,
            if (verified) IDENTITY_CACHE_TTL_MS else IDENTITY_NEGATIVE_TTL_MS
        )
        return verified
    }

    private fun verifyAniListMediaIdentity(
        token: String,
        mediaId: Int,
        expectedType: String,
        entry: MediaEntry,
        expectedTitles: Collection<String>,
        context: Context
    ): Boolean = runSyncCatching {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    id
                    idMal
                    type
                    title { romaji english native }
                    startDate { year }
                }
            }
        """.trimIndent()
        val response = postAniList(
            token = token,
            query = query,
            variables = JSONObject().put("id", mediaId),
            context = context
        )
        val media = JSONObject(response).optJSONObject("data")?.optJSONObject("Media") ?: return@runSyncCatching false
        if (media.optInt("id", 0) != mediaId || media.optString("type") != expectedType) return@runSyncCatching false
        val expectedMalId = MediaIdentity.realMalId(entry)
        if (expectedMalId != null && media.optInt("idMal", 0) != expectedMalId) return@runSyncCatching false
        val expectedAniListId = MediaIdentity.aniListId(entry)
        if (expectedAniListId != null && expectedAniListId != mediaId) return@runSyncCatching false
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        CrossSyncIdentityGuard.titlesAreIdentityEquivalent(expectedTitles, remoteTitles) &&
            CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear)
    }.getOrDefault(false)

    /**
     * Resolves an AniList source row's official `idMal` only after verifying AniList's own record
     * agrees with the source-native ID, media type, strict title aliases, and exact known year.
     */
    internal fun resolveVerifiedMalIdFromAniList(
        token: String?,
        entry: MediaEntry,
        aniListId: Int
    ): Int? = runSyncCatching {
        if (aniListId <= 0 || MediaIdentity.aniListId(entry) != aniListId) return@runSyncCatching null
        val expectedType = when (entry.type) {
            MediaType.Anime -> "ANIME"
            MediaType.Manga -> "MANGA"
            else -> return@runSyncCatching null
        }
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    id
                    idMal
                    type
                    title { romaji english native }
                    startDate { year }
                }
            }
        """.trimIndent()
        val response = postAniList(token = token, query = query, variables = JSONObject().put("id", aniListId))
        val media = JSONObject(response).optJSONObject("data")?.optJSONObject("Media")
            ?: return@runSyncCatching null
        if (media.optInt("id", 0) != aniListId || media.optString("type") != expectedType) return@runSyncCatching null
        val malId = media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
            ?: return@runSyncCatching null
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        if (!CrossSyncIdentityGuard.titlesAreIdentityEquivalent(CrossSyncIdentityGuard.aliasesOf(entry), remoteTitles)) return@runSyncCatching null
        if (!CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear)) return@runSyncCatching null
        val storedMalId = MediaIdentity.realMalId(entry)
        malId.takeIf { storedMalId == null || storedMalId == it }
    }.getOrNull()

    /**
     * AniList-sourced list rows must prove their native list handle belongs to the current viewer,
     * even when the stored identity is an AniList synthetic ID. This resolves the attached media,
     * checks account ownership, and confirms its official `idMal`, type, title aliases, and exact
     * year before returning the MAL ID.
     */
    internal fun resolveVerifiedMalIdFromAniListEntry(
        token: String?,
        entry: MediaEntry,
        expectedMalId: Int?
    ): Int? = runSyncCatching {
        val listEntryId = entry.aniListEntryId?.takeIf { it > 0 } ?: return@runSyncCatching null
        if (token.isNullOrBlank()) return@runSyncCatching null
        if (expectedMalId != null && (expectedMalId !in 1..99_999_999 || MediaIdentity.realMalId(entry) != expectedMalId)) return@runSyncCatching null
        val expectedType = when (entry.type) {
            MediaType.Anime -> "ANIME"
            MediaType.Manga -> "MANGA"
            else -> return@runSyncCatching null
        }
        val query = """
            query (${'$'}id: Int) {
                Viewer { id }
                MediaList(id: ${'$'}id) {
                    id
                    userId
                    media {
                        id
                        idMal
                        type
                        title { romaji english native }
                        startDate { year }
                    }
                }
            }
        """.trimIndent()
        val response = JSONObject(postAniList(token, query, JSONObject().put("id", listEntryId)))
        val data = response.optJSONObject("data") ?: return@runSyncCatching null
        val viewerId = data.optJSONObject("Viewer")?.optInt("id", 0)?.takeIf { it > 0 }
            ?: return@runSyncCatching null
        val listEntry = data.optJSONObject("MediaList") ?: return@runSyncCatching null
        if (listEntry.optInt("id", 0) != listEntryId || listEntry.optInt("userId", 0) != viewerId) return@runSyncCatching null
        val media = listEntry.optJSONObject("media") ?: return@runSyncCatching null
        val resolvedMalId = media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
            ?: return@runSyncCatching null
        if (media.optInt("id", 0) <= 0 || media.optString("type") != expectedType) return@runSyncCatching null
        if (expectedMalId != null && resolvedMalId != expectedMalId) return@runSyncCatching null
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        if (!CrossSyncIdentityGuard.titlesAreIdentityEquivalent(CrossSyncIdentityGuard.aliasesOf(entry), remoteTitles)) return@runSyncCatching null
        if (!CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear)) return@runSyncCatching null
        resolvedMalId
    }.getOrNull()

    /**
     * AniList Media ID'sinden gerçek MAL ID'yi sorgular.
     */
    fun resolveMalIdFromAniList(
        token: String?,
        aniListId: Int
    ): Int? {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    id
                    idMal
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("id", aniListId)
        return runSyncCatching {
            val response = postAniList(token = token, query = query, variables = variables)
            val media = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Media") ?: return@runSyncCatching null
            if (media.optInt("id", 0) != aniListId) return@runSyncCatching null
            media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
        }.getOrNull()
    }

    fun fetchAniListUserId(
        token: String,
        context: Context
    ): Int {
        if (token == cachedAniListToken && cachedAniListUserId != null) {
            return cachedAniListUserId!!
        }

        val query = """
            query {
                Viewer {
                    id
                }
            }
        """.trimIndent()

        val response = postAniList(
            token = token,
            query = query,
            variables = JSONObject(),
            context = context
        )

        val userId = JSONObject(response)
            .optJSONObject("data")
            ?.optJSONObject("Viewer")
            ?.optInt("id", 0)
            ?.takeIf { it > 0 }
            ?: throw IllegalStateException(
                context.getString(R.string.account_sync_anilist_user_id_unavailable)
            )

        cachedAniListToken = token
        cachedAniListUserId = userId
        return userId
    }

    data class ViewerOptions(
        val scoreFormat: String,
        val animeCategories: List<String>,
        val animeEnabled: Boolean,
        val mangaCategories: List<String>,
        val mangaEnabled: Boolean
    )

    fun getViewerOptions(token: String): ViewerOptions? {
        val query = """
            query {
                Viewer {
                    mediaListOptions {
                        scoreFormat
                        animeList {
                            advancedScoring
                            advancedScoringEnabled
                        }
                        mangaList {
                            advancedScoring
                            advancedScoringEnabled
                        }
                    }
                }
            }
        """.trimIndent()

        return try {
            val response = postAniList(token = token, query = query, variables = JSONObject())
            val viewer = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Viewer")
            val mediaListOptions = viewer?.optJSONObject("mediaListOptions")
            val scoreFormat = mediaListOptions?.optString("scoreFormat", "POINT_10") ?: "POINT_10"
            
            val animeList = mediaListOptions?.optJSONObject("animeList")
            val animeEnabled = animeList?.optBoolean("advancedScoringEnabled", false) ?: false
            val animeCatsArray = animeList?.optJSONArray("advancedScoring")
            val animeCategories = mutableListOf<String>()
            if (animeCatsArray != null) {
                for (i in 0 until animeCatsArray.length()) {
                    animeCategories.add(animeCatsArray.optString(i))
                }
            }

            val mangaList = mediaListOptions?.optJSONObject("mangaList")
            val mangaEnabled = mangaList?.optBoolean("advancedScoringEnabled", false) ?: false
            val mangaCatsArray = mangaList?.optJSONArray("advancedScoring")
            val mangaCategories = mutableListOf<String>()
            if (mangaCatsArray != null) {
                for (i in 0 until mangaCatsArray.length()) {
                    mangaCategories.add(mangaCatsArray.optString(i))
                }
            }

            ViewerOptions(
                scoreFormat = scoreFormat,
                animeCategories = animeCategories,
                animeEnabled = animeEnabled,
                mangaCategories = mangaCategories,
                mangaEnabled = mangaEnabled
            )
        } catch (e: java.lang.Exception) {
            null
        }
    }

    data class MediaListMetadata(
        val customLists: Map<String, Boolean>?,
        val advancedScores: Map<String, Double>?
    )

    fun getMediaListMetadata(
        token: String,
        entryId: Int,
        userId: Int
    ): MediaListMetadata? {
        val query = """
            query (${'$'}id: Int, ${'$'}userId: Int) {
                MediaList(id: ${'$'}id, userId: ${'$'}userId) {
                    customLists
                    advancedScores
                }
            }
        """.trimIndent()

        val variables = JSONObject()
            .put("id", entryId)
            .put("userId", userId)

        return try {
            val response = postAniList(token = token, query = query, variables = variables)
            val mediaList = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("MediaList") ?: return null

            val jsonCustomLists = mediaList.optJSONObject("customLists")
            val customListsMap = mutableMapOf<String, Boolean>()
            if (jsonCustomLists != null) {
                val keys = jsonCustomLists.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    customListsMap[key] = jsonCustomLists.optBoolean(key, false)
                }
            }

            val jsonAdvancedScores = mediaList.optJSONObject("advancedScores")
            val advancedScoresMap = mutableMapOf<String, Double>()
            if (jsonAdvancedScores != null) {
                val keys = jsonAdvancedScores.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    advancedScoresMap[key] = jsonAdvancedScores.optDouble(key, 0.0)
                }
            }

            MediaListMetadata(customLists = customListsMap, advancedScores = advancedScoresMap)
        } catch (e: java.lang.Exception) {
            null
        }
    }

    fun getViewerCustomLists(
        token: String,
        isManga: Boolean
    ): List<String>? {
        val query = """
            query {
                Viewer {
                    mediaListOptions {
                        animeList {
                            customLists
                        }
                        mangaList {
                            customLists
                        }
                    }
                }
            }
        """.trimIndent()

        return try {
            val response = postAniList(token = token, query = query, variables = JSONObject())
            val viewer = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Viewer")
            val listOptions = viewer?.optJSONObject("mediaListOptions")
            val list = if (isManga) {
                listOptions?.optJSONObject("mangaList")
            } else {
                listOptions?.optJSONObject("animeList")
            }
            val customListsArray = list?.optJSONArray("customLists")
            val result = mutableListOf<String>()
            if (customListsArray != null) {
                for (i in 0 until customListsArray.length()) {
                    val item = customListsArray.optString(i)
                    if (!item.isNullOrBlank()) {
                        result.add(item)
                    }
                }
            }
            result
        } catch (e: java.lang.Exception) {
            null
        }
    }

    /**
     * AniHyou "Özel Listeler" paritesi: hesap genelindeki özel liste adlarını günceller.
     * UpdateUser mutasyonu anime+manga liste seçeneklerine aynı ad listesini yazar.
     */
    fun updateCustomLists(token: String, customLists: List<String>): Boolean {
        val query = """
            mutation (${'$'}animeLists: [String], ${'$'}mangaLists: [String]) {
                UpdateUser(
                    animeListOptions: { customLists: ${'$'}animeLists },
                    mangaListOptions: { customLists: ${'$'}mangaLists }
                ) {
                    id
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("animeLists", org.json.JSONArray(customLists))
            .put("mangaLists", org.json.JSONArray(customLists))
        return try {
            val response = postAniList(token = token, query = query, variables = variables)
            JSONObject(response).optJSONObject("data")?.optJSONObject("UpdateUser") != null
        } catch (e: java.lang.Exception) {
            false
        }
    }

    fun updateEntryCustomLists(
        token: String,
        mediaId: Int?,
        entryId: Int?,
        customLists: List<String>
    ): Boolean {
        val query = """
            mutation (${'$'}id: Int, ${'$'}mediaId: Int, ${'$'}customLists: [String]) {
                SaveMediaListEntry(id: ${'$'}id, mediaId: ${'$'}mediaId, customLists: ${'$'}customLists) {
                    id
                }
            }
        """.trimIndent()

        val jsonArray = org.json.JSONArray()
        customLists.forEach { jsonArray.put(it) }

        val variables = JSONObject()
        if (entryId != null && entryId > 0) {
            variables.put("id", entryId)
        } else if (mediaId != null) {
            variables.put("mediaId", mediaId)
        } else {
            return false
        }
        variables.put("customLists", jsonArray)

        return try {
            postAniList(token = token, query = query, variables = variables)
            true
        } catch (e: java.lang.Exception) {
            false
        }
    }

    private fun postAniList(
        token: String?,
        query: String,
        variables: JSONObject,
        context: Context? = null
    ): String {
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()

        val requestBuilder = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        // ── Ortak hız bütçesi (AniListGovernor) ─────────────────────────────────
        // Eski kod 429'da yalnızca `PlatformRateLimiter` içinde bekliyordu; ancak
        // Keşfet/detay sorguları ve liste içe aktarımı AYRI bir sayaçtan harcıyordu.
        // Yani bu döngü beklerken diğer yollar AniList'i dövmeye devam ediyor, soğuma
        // her yeni 429'da baştan başlıyordu → kullanıcı için "sürekli ve çözülmeyen"
        // hız sınırı. Artık üç yol da tek bütçeden geçiyor; 429 soğuması gerçekten
        // bekleniyor ve GraphQL katmanındaki 429 (HTTP 200 + errors[status=429]) de
        // yeniden denenebilir sayılıyor.
        var attempt = 0
        var lastFailure: Throwable? = null

        while (attempt < MAX_ANILIST_ATTEMPTS) {
            attempt++

            val gate = AniListGovernor.acquireBlocking()
            if (gate is AniListGovernor.Gate.Blocked) {
                val retrySec = (gate.retryAfterMs / 1000L).coerceAtLeast(1L)
                throw when (gate.reason) {
                    AniListGovernor.BlockedReason.RATE_LIMITED -> IllegalStateException(
                        context?.getString(R.string.account_sync_anilist_rate_limit, attempt, retrySec)
                            ?: "AniList istek sınırı doldu — $retrySec sn sonra kendiliğinden tekrar denenecek"
                    )
                    AniListGovernor.BlockedReason.SERVICE_DOWN -> IllegalStateException(
                        "AniList geçici olarak devre dışı: ${AniListGovernor.serviceDownMessage()}"
                    )
                    AniListGovernor.BlockedReason.GATEWAY_DOWN -> IllegalStateException(
                        "AniList şu anda ulaşılamıyor (ağ/geçit hatası)"
                    )
                }
            }

            val call = try {
                com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request)
                    .execute()
                    .use { response ->
                        val body = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                        response to body
                    }
            } catch (e: Exception) {
                AniListGovernor.observeTransportFailure(e)
                lastFailure = e
                if (attempt >= MAX_ANILIST_ATTEMPTS) break
                Thread.sleep(1_000L * attempt)
                continue
            }

            val (response, responseText) = call

            when (val outcome = AniListGovernor.observeResponse(response, responseText)) {
                is AniListGovernor.Outcome.Success -> {
                    val errors = runCatching { JSONObject(responseText).optJSONArray("errors") }.getOrDefault(null)
                    when (val classified = AniListGovernor.classifyErrors(errors)) {
                        is AniListGovernor.Outcome.Success -> return responseText
                        is AniListGovernor.Outcome.RateLimited -> {
                            lastFailure = IllegalStateException(
                                context?.getString(
                                    R.string.account_sync_anilist_rate_limit,
                                    attempt,
                                    classified.retryAfterMs / 1000L
                                ) ?: "AniList istek sınırı doldu — ${classified.retryAfterMs / 1000L} sn sonra kendiliğinden tekrar denenecek"
                            )
                            if (attempt >= MAX_ANILIST_ATTEMPTS) break
                            continue // acquireBlocking() soğumayı bekleyecek
                        }
                        is AniListGovernor.Outcome.AuthFailure -> throw IllegalStateException(
                            context?.getString(R.string.account_sync_token_missing, "AniList")
                                ?: "AniList oturum süresi dolmuş; lütfen hesabı yeniden bağlayın."
                        )
                        is AniListGovernor.Outcome.ServiceDown -> throw IllegalStateException(
                            "AniList geçici olarak devre dışı: ${classified.message}"
                        )
                        is AniListGovernor.Outcome.Retryable -> {
                            lastFailure = IllegalStateException("AniList geçici sunucu hatası")
                            if (attempt >= MAX_ANILIST_ATTEMPTS) break
                            Thread.sleep(classified.retryAfterMs.coerceIn(500L, 4_000L))
                        }
                        is AniListGovernor.Outcome.Permanent -> throw IllegalStateException(
                            context?.getString(
                                R.string.account_sync_api_error,
                                "AniList",
                                response.code,
                                (errors?.toString() ?: responseText).take(300)
                            ) ?: "AniList API hatası: ${response.code} ${(errors?.toString() ?: responseText).take(300)}"
                        )
                    }
                }
                is AniListGovernor.Outcome.RateLimited -> {
                    val retrySec = (outcome.retryAfterMs / 1000L).coerceAtLeast(1L)
                    lastFailure = IllegalStateException(
                        context?.getString(R.string.account_sync_anilist_rate_limit, attempt, retrySec)
                            ?: "AniList istek sınırı doldu — $retrySec sn sonra kendiliğinden tekrar denenecek"
                    )
                    if (attempt >= MAX_ANILIST_ATTEMPTS) break
                    continue
                }
                is AniListGovernor.Outcome.Retryable -> {
                    lastFailure = IllegalStateException("AniList geçici sunucu hatası")
                    if (attempt >= MAX_ANILIST_ATTEMPTS) break
                    Thread.sleep(outcome.retryAfterMs.coerceIn(500L, 4_000L))
                }
                is AniListGovernor.Outcome.AuthFailure -> throw IllegalStateException(
                    context?.getString(R.string.account_sync_token_missing, "AniList")
                        ?: "AniList oturum süresi dolmuş; lütfen hesabı yeniden bağlayın."
                )
                is AniListGovernor.Outcome.ServiceDown -> throw IllegalStateException(
                    "AniList geçici olarak devre dışı: ${outcome.message}"
                )
                is AniListGovernor.Outcome.Permanent -> throw IllegalStateException(
                    context?.getString(R.string.account_sync_api_error, "AniList", response.code, responseText.take(300))
                        ?: "AniList API hatası: ${response.code} ${responseText.take(300)}"
                )
            }
        }

        throw lastFailure ?: IllegalStateException(
            context?.getString(R.string.account_sync_unknown_error) ?: "AniList isteği tamamlanamadı"
        )
    }

    private const val MAX_ANILIST_ATTEMPTS = 3

    private fun WatchStatus.toAniListStatus(): String {
        return when (this) {
            WatchStatus.Watching -> "CURRENT"
            WatchStatus.Completed -> "COMPLETED"
            WatchStatus.Planned -> "PLANNING"
            WatchStatus.Dropped -> "DROPPED"
            WatchStatus.Paused -> "PAUSED"
            WatchStatus.Repeating -> "REPEATING"
        }
    }

    private fun String?.toAniListFuzzyDate(): JSONObject? {
        // Boş tarih = değişken hiç gönderilmez. Eskiden {0,0,0} gönderiliyordu; AniList bunu
        // "tarihi temizle" olarak uygular — yerel satırda tarih yokken yapılan her eşitleme
        // (ör. Bangumi satırından yayılım) AniList'teki başlangıç/bitiş tarihlerini sessizce
        // siliyordu. MAL/Kitsu hatlarıyla aynı kural: boş alan uzaktaki değeri ezmez.
        if (this.isNullOrBlank()) return null

        val parts = split("-")
        if (parts.size != 3) return null

        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null

        return JSONObject()
            .put("year", year)
            .put("month", month)
            .put("day", day)
    }

}

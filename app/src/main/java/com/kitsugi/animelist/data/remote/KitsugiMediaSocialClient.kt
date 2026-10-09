package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * İstatistik, inceleme, forum konuları ve aktivite verilerini çeken istemci.
 * [KitsugiMediaTabsClient]'dan bölünmüştür.
 */
class KitsugiMediaSocialClient {

    private val relationsClient = KitsugiMediaRelationsClient()

    private fun supportsAnimeSocial(mediaType: MediaType): Boolean =
        mediaType == MediaType.Anime || mediaType == MediaType.Manga

    private fun validRealMalId(id: Int?): Int? = id?.takeIf { it in 1 until 100_000_000 }

    private fun cachedSocialDetail(source: String, externalId: Int, mediaType: MediaType): KitsugiMediaDetail? {
        if (externalId <= 0) return null
        return DetailCache.getMediaDetail(source, externalId, mediaType.name)
            ?.takeIf { it.type == mediaType }
    }

    /**
     * Kaynağın kendi kimlik alanını kullanarak eşleşen AniList öğesini çözer.
     * TMDB/Simkl/Kitsu/Shikimori/Bangumi ID'leri asla MAL veya AniList ID'si
     * olarak doğrudan kullanılmaz; yalnızca açık çapraz eşleme kabul edilir.
     */
    private fun aniListIdFromStableId(externalId: Int): Int? =
        externalId.takeIf { it in 100_000_001..199_999_999 }?.minus(100_000_000)

    private suspend fun resolveAniListMediaId(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): Int? {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return null
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val detail = cachedSocialDetail(source, externalId, mediaType)
        val safeMalId = validRealMalId(realMalId) ?: validRealMalId(detail?.realMalId)
        val safeTmdbId = tmdbId?.takeIf { it > 0 }
            ?: detail?.tmdbId?.takeIf { it > 0 }
            ?: externalId.takeIf { canonical == "tmdb" && it > 0 }

        val resolved = when (canonical) {
            "anilist" -> aniListIdFromStableId(externalId)
                ?: (safeMalId ?: validRealMalId(externalId))?.let {
                    runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull()
                }
            "jikan", "mal" -> {
                val malId = MalJikanMediaSupport.resolveMalId(source, externalId, safeMalId)
                malId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "kitsu" -> {
                val kitsuId = KitsuIdNamespace.rawIdFromStable(externalId)
                val mapped = runCatching {
                    KitsugiIdResolver.resolveIds(
                        malId = safeMalId,
                        aniListId = null,
                        mediaType = mediaType,
                        kitsuId = kitsuId
                    ).aniListId
                }.getOrNull()
                mapped ?: safeMalId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "shikimori" -> {
                val malId = resolveMalIdForShikimori(externalId, safeMalId, mediaType)
                malId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "bangumi" -> {
                val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                cross?.aniListId?.takeIf { it > 0 }
                    ?: validRealMalId(cross?.malId)?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            "tmdb", "simkl" -> {
                val mapping = if (safeMalId != null || safeTmdbId != null) {
                    runCatching {
                        KitsugiIdResolver.resolveIds(
                            malId = safeMalId,
                            aniListId = null,
                            tmdbId = safeTmdbId,
                            mediaType = mediaType
                        )
                    }.getOrNull()
                } else null
                mapping?.aniListId?.takeIf { it > 0 }
                    ?: safeMalId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
            }
            else -> safeMalId?.let { runCatching { relationsClient.resolveAniListId(it, mediaType) }.getOrNull() }
        }
        return resolved?.takeIf { it > 0 }
    }

    /** Resolves a verified MAL counterpart without ever interpreting another provider's ID as MAL. */
    private suspend fun resolveRealMalIdForSocial(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): Int? {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return null
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val detail = cachedSocialDetail(source, externalId, mediaType)
        val safeMalId = validRealMalId(realMalId) ?: validRealMalId(detail?.realMalId)
        val safeTmdbId = tmdbId?.takeIf { it > 0 }
            ?: detail?.tmdbId?.takeIf { it > 0 }
            ?: externalId.takeIf { canonical == "tmdb" && it > 0 }

        return when (canonical) {
            "jikan", "mal" -> MalJikanMediaSupport.resolveMalId(source, externalId, safeMalId)
            "anilist" -> safeMalId
                ?: aniListIdFromStableId(externalId)?.let { aniListId ->
                    runCatching {
                        KitsugiIdResolver.resolveIds(aniListId = aniListId, malId = null, mediaType = mediaType).malId
                    }.getOrNull()?.let(::validRealMalId)
                }
                ?: validRealMalId(externalId)
            "kitsu" -> safeMalId ?: runCatching {
                KitsugiIdResolver.resolveIds(
                    malId = null,
                    aniListId = null,
                    mediaType = mediaType,
                    kitsuId = KitsuIdNamespace.rawIdFromStable(externalId)
                ).malId
            }.getOrNull()?.let(::validRealMalId)
            "shikimori" -> resolveMalIdForShikimori(externalId, safeMalId, mediaType)
            "bangumi" -> {
                val crossMalId = runCatching {
                    KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType).malId
                }.getOrNull()
                validRealMalId(crossMalId) ?: safeMalId
            }
            "tmdb", "simkl" -> safeMalId ?: safeTmdbId?.let { verifiedTmdbId ->
                runCatching {
                    KitsugiIdResolver.resolveIds(
                        malId = null,
                        aniListId = null,
                        tmdbId = verifiedTmdbId,
                        mediaType = mediaType
                    ).malId
                }.getOrNull()?.let(::validRealMalId)
            }
            else -> safeMalId
        }
    }

    /**
     * Shikimori kayıtları için MAL ID'sini çözer (verilen değer → detay önbelleği → ARM →
     * Shikimori API). Shikimori ID'si ASLA MAL ID yerine kullanılmaz; eşleme yoksa null döner.
     */
    private fun resolveMalIdForShikimori(
        shikimoriId: Int,
        realMalId: Int?,
        mediaType: MediaType = MediaType.Anime
    ): Int? {
        validRealMalId(realMalId)?.let { return it }
        validRealMalId(cachedSocialDetail("shikimori", shikimoriId, mediaType)?.realMalId)?.let { return it }
        // The legacy Shikimori lookup probes anime before manga and caches by numeric ID
        // only. Do not use that ambiguous fallback for manga social content.
        if (mediaType == MediaType.Manga) return null
        return KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId)?.let(::validRealMalId)
    }

    /** Shikimori kaydı için AniList "stableId"si (100_000_000 + aniListId); yoksa null. */
    private suspend fun resolveAniListStableIdForShikimori(malId: Int?): Int? {
        if (malId == null || malId <= 0) return null
        val resolved = runCatching {
            KitsugiIdResolver.resolveIds(malId = malId, aniListId = null, tmdbId = null)
        }.getOrNull() ?: return null
        val aniListId = resolved.aniListId ?: return null
        return if (aniListId > 0) 100_000_000 + aniListId else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchStats(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        realMalId: Int? = null
    ): KitsugiStats? {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) return@withContext null
            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi" -> {
                    // 1) Bangumi'nin kendi puan dağılımı + koleksiyon durumu + sıralaması (ek istek yok).
                    val native = runCatching { KitsugiBangumiDetailClient.fetchStats(externalId, mediaType) }.getOrNull()
                    val nativeUseful = native != null &&
                        (native.scoreDistribution.isNotEmpty() || native.rankings.isNotEmpty() ||
                            (native.completed ?: 0) + (native.watching ?: 0) + (native.planned ?: 0) > 0)
                    if (nativeUseful) return@withContext native

                    // 2) Yedek: Çözülen AniList / MAL kimliği (Bangumi stableId'si MAL ID DEĞİLDİR).
                    if (mediaType != MediaType.Anime && mediaType != MediaType.Manga) return@withContext native
                    val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    val aniListStable = cross?.aniListStableId ?: resolveAniListStableIdForShikimori(malId)
                    if (aniListStable != null) {
                        val aniStats = fetchStatsFromAniList(aniListStable, mediaType)
                        if (aniStats != null && (aniStats.rankings.isNotEmpty() || aniStats.scoreDistribution.isNotEmpty())) {
                            return@withContext aniStats
                        }
                    }
                    if (malId != null && malId > 0) fetchStatsFromJikan(malId, mediaType) ?: native else native
                }
                "tmdb" -> {
                    val effectiveTmdbId = externalId
                    val isMovie = mediaType == MediaType.Movie
                    if (mediaType == MediaType.Anime) {
                        // Anime için önce MAL ID'si üzerinden Jikan'a git
                        val malId = realMalId ?: DetailCache.getMediaDetail("tmdb", externalId)?.realMalId
                        if (malId != null && malId > 0) {
                            fetchStats("jikan", malId, MediaType.Anime, null)
                        } else {
                            val resolved = KitsugiIdResolver.resolveIds(malId = null, aniListId = null, tmdbId = effectiveTmdbId)
                            val resolvedMalId = resolved.malId
                            if (resolvedMalId != null && resolvedMalId > 0) {
                                fetchStats("jikan", resolvedMalId, MediaType.Anime, null)
                            } else {
                                // Fallback: TMDB kendi istatistiğini döndür
                                TmdbApiClient().fetchStats(effectiveTmdbId, isMovie)
                            }
                        }
                    } else {
                        // Film / Dizi → doğrudan TMDB istatistiği
                        TmdbApiClient().fetchStats(effectiveTmdbId, isMovie)
                    }
                }
                "simkl" -> {
                    if (mediaType == MediaType.Anime) {
                        val malId = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                        if (malId != null && malId > 0) {
                            fetchStats("jikan", malId, mediaType, null)
                        } else {
                            val resolved = KitsugiIdResolver.resolveIds(malId = null, aniListId = null, tmdbId = null)
                            val resolvedMalId = resolved.malId
                            if (resolvedMalId != null && resolvedMalId > 0) {
                                fetchStats("jikan", resolvedMalId, mediaType, null)
                            } else null
                        }
                    } else {
                        // Simkl Film/Dizi → TMDB ID'sini bul ve TMDB istatistiğini çek
                        val detail = DetailCache.getMediaDetail("simkl", externalId)
                        val resolvedTmdb = detail?.tmdbId ?: run {
                            val malIdForResolve = realMalId ?: detail?.realMalId
                            KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, tmdbId = null).tmdbId
                        }
                        if (resolvedTmdb != null && resolvedTmdb > 0) {
                            val isMovie = mediaType == MediaType.Movie
                            TmdbApiClient().fetchStats(resolvedTmdb, isMovie)
                        } else null
                    }
                }
                "kitsu" -> {
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    if (kitsuNumericId <= 0) return@withContext null

                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        val encoded = 100_000_000 + aniListId
                        val stats = fetchStatsFromAniList(encoded, mediaType)
                        if (stats != null) return@withContext stats
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        return@withContext fetchStatsFromJikan(jikanId, mediaType)
                    }

                    null
                }
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext null
                    // MAL details use Jikan as their primary source. AniList is a fallback,
                    // not a prerequisite that can keep valid MAL statistics waiting.
                    val jikanStats = fetchStatsFromJikan(malId, mediaType)
                    if (jikanStats != null && (
                            jikanStats.watching != null ||
                                jikanStats.completed != null ||
                                jikanStats.planned != null ||
                                jikanStats.dropped != null ||
                                jikanStats.paused != null ||
                                jikanStats.scoreDistribution.isNotEmpty()
                            )
                    ) {
                        jikanStats
                    } else {
                        fetchStatsFromAniList(malId, mediaType)
                    }
                }
                "shikimori" -> {
                    // Shikimori ID'si MAL ID'si değildir. Gerçek MAL ID'si çözülür; istatistik
                    // AniList'ten (varsa) yoksa MAL/Jikan'dan çekilir.
                    val malId = resolveMalIdForShikimori(externalId, realMalId, mediaType)
                    val jikanType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                    if (malId != null && malId > 0) {
                        val aniListStableId = resolveAniListStableIdForShikimori(malId)
                        if (aniListStableId != null) {
                            val aniStats = fetchStatsFromAniList(aniListStableId, jikanType)
                            if (aniStats != null && (aniStats.rankings.isNotEmpty() || aniStats.scoreDistribution.isNotEmpty())) {
                                return@withContext aniStats
                            }
                        }
                        fetchStatsFromJikan(malId, jikanType)
                    } else null
                }
                "anilist"      -> fetchStatsFromAniList(externalId, mediaType)
                else           -> null
            }
        }
    }

    private suspend fun fetchStatsFromJikan(externalId: Int, mediaType: MediaType): KitsugiStats? {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/statistics")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit null
                val root = JSONObject(response)
                val data = root.optJSONObject("data") ?: return@runWithRateLimit null
                val watching   = data.optionalPositiveInt("watching")
                val completed  = data.optionalPositiveInt("completed")
                val onHold     = data.optionalPositiveInt("on_hold")
                val dropped    = data.optionalPositiveInt("dropped")
                val planToWatch = data.optionalPositiveInt("plan_to_watch")
                val scores = data.optJSONArray("scores")
                val scoreList = mutableListOf<KitsugiScoreStat>()
                if (scores != null) {
                    for (i in 0 until scores.length()) {
                        val s = scores.optJSONObject(i) ?: continue
                        scoreList.add(KitsugiScoreStat(s.optInt("score"), s.optInt("votes")))
                    }
                }
                KitsugiStats(
                    watching = watching,
                    completed = completed,
                    planned = planToWatch,
                    dropped = dropped,
                    paused = onHold,
                    scoreDistribution = scoreList
                )
            }
        }.getOrNull()
    }

    private suspend fun fetchStatsFromAniList(externalId: Int, mediaType: MediaType): KitsugiStats? {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    rankings {
                        id rank type format year season allTime context
                    }
                    stats {
                        statusDistribution { status amount }
                        scoreDistribution  { score amount }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val mediaObj = root.optJSONObject("data")?.optJSONObject("Media") ?: return@runCatching null
            
            val rankingsArr = mediaObj.optJSONArray("rankings")
            val rankingsList = mutableListOf<KitsugiRanking>()
            if (rankingsArr != null) {
                for (i in 0 until rankingsArr.length()) {
                    val r = rankingsArr.optJSONObject(i) ?: continue
                    val rank = r.optInt("rank", 0)
                    if (rank <= 0) continue
                    val type = r.optNullableString("type").orEmpty()
                    val context = r.optNullableString("context").orEmpty()
                    val allTime = r.optBoolean("allTime", false)
                    val year = r.optionalPositiveInt("year")
                    val season = r.optNullableString("season")
                    rankingsList.add(KitsugiRanking(rank = rank, type = type, context = context, allTime = allTime, year = year, season = season))
                }
            }

            val statsObj = mediaObj.optJSONObject("stats") ?: return@runCatching null
            val statusDist = statsObj.optJSONArray("statusDistribution")
            val scoreDist  = statsObj.optJSONArray("scoreDistribution")
            var watching: Int? = null; var completed: Int? = null
            var planned: Int? = null;  var dropped: Int? = null; var paused: Int? = null
            if (statusDist != null) {
                for (i in 0 until statusDist.length()) {
                    val item = statusDist.optJSONObject(i) ?: continue
                    when (item.optNullableString("status").orEmpty()) {
                        "CURRENT"   -> watching  = item.optInt("amount")
                        "COMPLETED" -> completed = item.optInt("amount")
                        "PLANNING"  -> planned   = item.optInt("amount")
                        "DROPPED"   -> dropped   = item.optInt("amount")
                        "PAUSED"    -> paused    = item.optInt("amount")
                    }
                }
            }
            val scoreList = mutableListOf<KitsugiScoreStat>()
            if (scoreDist != null) {
                for (i in 0 until scoreDist.length()) {
                    val item = scoreDist.optJSONObject(i) ?: continue
                    scoreList.add(KitsugiScoreStat(item.optInt("score"), item.optInt("amount")))
                }
            }
            KitsugiStats(
                watching = watching,
                completed = completed,
                planned = planned,
                dropped = dropped,
                paused = paused,
                scoreDistribution = scoreList,
                rankings = rankingsList
            )
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reviews
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchReviews(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        page: Int = 1,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiReview> = withContext(Dispatchers.IO) {
        if (externalId == null || externalId <= 0) return@withContext emptyList()
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val animeSocialSupported = supportsAnimeSocial(mediaType)
        if (!animeSocialSupported && canonical !in setOf("bangumi", "simkl", "tmdb")) {
            // MAL/Jikan/AniList reviews must never be requested for a live-action movie/show.
            return@withContext emptyList()
        }

        when (canonical) {
            "bangumi" -> {
                val native = runCatching { KitsugiBangumiDetailClient.fetchReviews(externalId, page) }
                    .getOrNull().orEmpty()
                if (!animeSocialSupported) return@withContext native

                val crossReviews = fetchAnimeSocialReviews(source, externalId, mediaType, page, tmdbId, realMalId)
                (native + crossReviews).distinctBy(::reviewFingerprint)
            }
            "simkl" -> {
                val detail = cachedSocialDetail(source, externalId, mediaType)
                val reviews = mutableListOf<KitsugiReview>()
                if (animeSocialSupported) {
                    reviews += fetchAnimeSocialReviews(source, externalId, mediaType, page, tmdbId, realMalId)
                }

                // Simkl movie/TV results have an unambiguous TMDB media type. Anime and
                // manga are not sent to a guessed TMDB movie-vs-TV endpoint: TMDB IDs can
                // overlap across those namespaces, so use the exact anime cross-ID instead.
                if (mediaType == MediaType.Movie || mediaType == MediaType.TvShow) {
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 }
                        ?: detail?.tmdbId?.takeIf { it > 0 }
                    if (resolvedTmdb != null) {
                        reviews += TmdbApiClient().fetchReviews(resolvedTmdb, mediaType == MediaType.Movie, page)
                    }
                }
                reviews.distinctBy(::reviewFingerprint)
            }
            "tmdb" -> {
                val reviews = mutableListOf<KitsugiReview>()
                if (mediaType == MediaType.Movie || mediaType == MediaType.TvShow) {
                    val effectiveTmdbId = tmdbId?.takeIf { it > 0 } ?: externalId
                    reviews += TmdbApiClient().fetchReviews(
                        effectiveTmdbId,
                        mediaType == MediaType.Movie,
                        page
                    )
                } else if (mediaType == MediaType.Anime) {
                    // TMDB can be the identity source for an anime result, but do not guess its
                    // movie-vs-TV endpoint. Only exact, typed MAL/AniList mappings are used here.
                    reviews += fetchAnimeSocialReviews(source, externalId, mediaType, page, tmdbId, realMalId)
                }
                reviews.distinctBy(::reviewFingerprint)
            }
            else -> {
                if (!animeSocialSupported) return@withContext emptyList()
                fetchAnimeSocialReviews(source, externalId, mediaType, page, tmdbId, realMalId)
            }
        }
    }

    private suspend fun fetchAnimeSocialReviews(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        page: Int,
        tmdbId: Int?,
        realMalId: Int?
    ): List<KitsugiReview> {
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return emptyList()
        val malId = resolveRealMalIdForSocial(source, externalId, mediaType, tmdbId, realMalId)
        val aniListId = resolveAniListMediaId(source, externalId, mediaType, tmdbId, realMalId)
        val reviews = mutableListOf<KitsugiReview>()

        if (aniListId != null) {
            reviews += runCatching {
                fetchReviewsFromAniList(100_000_000 + aniListId, mediaType, page)
            }.getOrNull().orEmpty()
        } else if (malId != null) {
            // Jikan-ID fallback still queries AniList by idMal and keeps the requested media type.
            reviews += runCatching { fetchReviewsFromAniList(malId, mediaType, page) }.getOrNull().orEmpty()
        }
        if (malId != null) {
            reviews += runCatching { fetchReviewsFromJikan(malId, mediaType, page) }.getOrNull().orEmpty()
        }
        return reviews.distinctBy(::reviewFingerprint)
    }

    private fun reviewFingerprint(review: KitsugiReview): String =
        review.username.lowercase().trim() + "_" + review.summary.take(20).lowercase().trim()

    private suspend fun fetchReviewsFromJikan(externalId: Int, mediaType: MediaType, page: Int): List<KitsugiReview> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/reviews?page=$page")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                val list = mutableListOf<KitsugiReview>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val score = item.optionalPositiveInt("score")
                    val reviewText = item.optNullableString("review")?.cleanApiText().orEmpty()
                    val summary = if (reviewText.length > 280) reviewText.take(280) + "..." else reviewText
                    val rawDate = item.optNullableString("date")
                    val dateText = rawDate?.let {
                        try {
                            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            val sdfOut = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                            sdfOut.format(sdfIn.parse(it)!!)
                        } catch (_: Exception) { it.take(10) }
                    }
                    val reactions = item.optJSONObject("reactions")
                    val helpfulCount = reactions?.optInt("overall") ?: item.optInt("votes")
                    val userObj  = item.optJSONObject("user")
                    val username = userObj?.optNullableString("username") ?: "Kullanıcı"
                    val avatarUrl = userObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                    list.add(KitsugiReview(
                        id = null, username = username, avatarUrl = avatarUrl,
                        score = score, summary = summary, fullText = reviewText,
                        dateText = dateText, helpfulCount = if (helpfulCount > 0) helpfulCount else null,
                        ratingAmount = null, userRating = null, source = "jikan"
                    ))
                }
                list
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchReviewsFromAniList(externalId: Int, mediaType: MediaType, page: Int): List<KitsugiReview> {
        val aniListId = if (externalId >= 100_000_000) {
            externalId - 100_000_000
        } else {
            relationsClient.resolveAniListId(externalId, mediaType)
        }
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam, ${'$'}page: Int) {
                Media($idFilter, type: ${'$'}type) {
                    reviews(page: ${'$'}page, perPage: 10, sort: RATING_DESC) {
                        nodes {
                            id summary body score rating ratingAmount userRating createdAt
                            user { id name avatar { medium } }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
            .put("page", page)
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
            val nodes = JSONObject(response)
                .optJSONObject("data")?.optJSONObject("Media")
                ?.optJSONObject("reviews")?.optJSONArray("nodes") ?: return@runCatching emptyList()
            val list = mutableListOf<KitsugiReview>()
            for (i in 0 until nodes.length()) {
                val node = nodes.optJSONObject(i) ?: continue
                val createdAt = node.optInt("createdAt")
                val dateText = if (createdAt > 0) {
                    try {
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(createdAt * 1000L))
                    } catch (_: Exception) { null }
                } else null
                val summary  = node.optNullableString("summary")?.cleanApiText().orEmpty()
                val body     = node.optNullableString("body")?.cleanApiText().orEmpty()
                val userObj  = node.optJSONObject("user")
                list.add(KitsugiReview(
                    id = node.optInt("id"),
                    userId = userObj?.optInt("id"),
                    username = userObj?.optNullableString("name") ?: "Kullanıcı",
                    avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                    score = node.optionalPositiveInt("score"),
                    summary = summary,
                    fullText = if (body.isNotBlank()) body else summary,
                    dateText = dateText,
                    helpfulCount = node.optionalPositiveInt("rating"),
                    ratingAmount = node.optionalPositiveInt("ratingAmount"),
                    userRating   = node.optNullableString("userRating"),
                    source = "anilist"
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forum Topics
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchForumTopics(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        page: Int = 1,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiForumTopic> = withContext(Dispatchers.IO) {
        // Jikan/AniList forums are anime/manga-specific. Never ask them to interpret
        // a TMDB/Simkl movie or live-action series ID as a MAL ID.
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return@withContext emptyList()

        val aniListId = resolveAniListMediaId(source, externalId, mediaType, tmdbId, realMalId)
        val malId = resolveRealMalIdForSocial(source, externalId, mediaType, tmdbId, realMalId)
        val aniTopics = aniListId?.let {
            runCatching { fetchForumTopicsFromAniList(it, page.coerceAtLeast(1)) }.getOrNull().orEmpty()
        }.orEmpty()
        val jikanTopics = if (malId != null && page <= 1) {
            runCatching { fetchForumTopicsFromJikan(malId, mediaType) }.getOrNull().orEmpty()
        } else emptyList()
        (aniTopics + jikanTopics).distinctBy { it.title.lowercase().trim() }.take(30)
    }

    private suspend fun fetchForumTopicsFromJikan(externalId: Int, mediaType: MediaType): List<KitsugiForumTopic> {
        val pathType = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$pathType/$externalId/forum")
        return KitsugiApiBase.runWithRateLimit {
            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
            runCatching {
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList()
                val list = mutableListOf<KitsugiForumTopic>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    list.add(KitsugiForumTopic(
                        id = item.optInt("mal_id"),
                        title = item.optString("title"),
                        commentCount = item.optInt("comments"),
                        viewCount = 0,
                        username = item.optString("author_username"),
                        avatarUrl = null,
                        dateText = item.optNullableString("date")?.take(10),
                        source = "jikan"
                    ))
                }
                list
            }.getOrElse { emptyList() }
        }
    }

    private suspend fun fetchForumTopicsFromAniList(externalId: Int, page: Int): List<KitsugiForumTopic> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else externalId
        val query = """
            query (${'$'}mediaId: Int, ${'$'}page: Int) {
                Page(page: ${'$'}page, perPage: 15) {
                    threads(mediaCategoryId: ${'$'}mediaId) {
                        id title replyCount viewCount likeCount isLiked createdAt
                        user { id name avatar { medium } }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("mediaId", aniListId).put("page", page)
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
            val threads = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("threads") ?: return@runCatching emptyList()
            val list = mutableListOf<KitsugiForumTopic>()
            for (i in 0 until threads.length()) {
                val item = threads.optJSONObject(i) ?: continue
                val createdAt = item.optInt("createdAt")
                val dateText = if (createdAt > 0) {
                    try { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
                } else null
                val userObj = item.optJSONObject("user")
                list.add(KitsugiForumTopic(
                    id = item.optInt("id"), title = item.optString("title"),
                    commentCount = item.optInt("replyCount"), viewCount = item.optInt("viewCount"),
                    username = userObj?.optNullableString("name") ?: "Kullanıcı",
                    avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                    dateText = dateText, likeCount = item.optInt("likeCount", 0),
                    isLiked = item.optBoolean("isLiked", false),
                    userId = userObj?.optInt("id"), source = "anilist"
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Activities
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchActivities(
        source: String,
        externalId: Int,
        page: Int = 1,
        mediaType: MediaType = MediaType.Anime,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiActivity> = withContext(Dispatchers.IO) {
        // AniList activity feed'i yalnızca Anime/Manga için anlamlıdır. Film/dizi
        // TMDB veya Simkl ID'lerini MAL/AniList namespace'ine düşürmek yasaktır.
        if (!supportsAnimeSocial(mediaType) || externalId <= 0) return@withContext emptyList()
        val aniListId = resolveAniListMediaId(source, externalId, mediaType, tmdbId, realMalId)
            ?: return@withContext emptyList()

        val query = """
            query (${'$'}mediaId: Int, ${'$'}page: Int) {
                Page(page: ${'$'}page, perPage: 15) {
                    activities(mediaId: ${'$'}mediaId, sort: [ID_DESC]) {
                        ... on ListActivity {
                            id status progress createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            media { id type isAdult title { romaji english native } coverImage { large } }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("mediaId", aniListId).put("page", page.coerceAtLeast(1))
        runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
            val activities = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("activities")
                ?: return@runCatching emptyList()
            val expectedAniListType = if (mediaType == MediaType.Manga) "MANGA" else "ANIME"
            val list = mutableListOf<KitsugiActivity>()
            for (i in 0 until activities.length()) {
                val item = activities.optJSONObject(i) ?: continue
                val activity = parseActivity(item)
                // API/ID eşlemesi hatalı olsa bile farklı formatın aktivitesi ekrana sızmasın.
                // Only ListActivity records carry a media ID. Free-text updates without
                // a verifiable media target are intentionally excluded from detail pages.
                if (activity.mediaId != aniListId || activity.mediaType?.equals(expectedAniListType, ignoreCase = true) != true) continue
                list.add(activity)
            }
            list
        }.getOrElse { emptyList() }
    }

    suspend fun fetchActivityReplies(activityId: Int): KitsugiActivity? {
        return withContext(Dispatchers.IO) {
            val query = """
                query (${'$'}activityId: Int) {
                    Activity(id: ${'$'}activityId) {
                        ... on ListActivity {
                            id status progress createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            media { id type isAdult title { romaji english native } coverImage { large } }
                            replies { id text createdAt likeCount isLiked user { id name avatar { medium } } }
                        }
                        ... on TextActivity {
                            id text createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            replies { id text createdAt likeCount isLiked user { id name avatar { medium } } }
                        }
                    }
                }
            """.trimIndent()
            val variables = JSONObject().put("activityId", activityId)
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                val actObj = JSONObject(response).optJSONObject("data")?.optJSONObject("Activity") ?: return@runCatching null
                val base = parseActivity(actObj)
                val repliesArr = actObj.optJSONArray("replies")
                val repliesList = mutableListOf<KitsugiActivityReply>()
                if (repliesArr != null) {
                    for (i in 0 until repliesArr.length()) {
                        val rep = repliesArr.optJSONObject(i) ?: continue
                        val rCreatedAt = rep.optInt("createdAt")
                        val rDateText = if (rCreatedAt > 0) {
                            try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rCreatedAt * 1000L)) } catch (_: Exception) { null }
                        } else null
                        val rUser = rep.optJSONObject("user")
                        repliesList.add(KitsugiActivityReply(
                            id = rep.optInt("id"), text = rep.optString("text").cleanApiText(),
                            dateText = rDateText,
                            username = rUser?.optNullableString("name") ?: "Kullanıcı",
                            avatarUrl = rUser?.optJSONObject("avatar")?.optNullableString("medium"),
                            likeCount = rep.optInt("likeCount", 0), isLiked = rep.optBoolean("isLiked", false),
                            userId = rUser?.optInt("id")
                        ))
                    }
                }
                base.copy(replies = repliesList)
            }.getOrNull()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forum Replies
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchForumTopicReplies(topicId: Int, page: Int = 1): List<KitsugiForumReply> {
        return withContext(Dispatchers.IO) {
            val query = """
                query (${'$'}threadId: Int, ${'$'}page: Int) {
                    Page(page: ${'$'}page, perPage: 30) {
                        threadComments(threadId: ${'$'}threadId) {
                            id comment createdAt likeCount isLiked
                            user { id name avatar { medium } }
                            childComments
                        }
                    }
                }
            """.trimIndent()
            val variables = JSONObject().put("threadId", topicId).put("page", page)
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
                val comments = JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("threadComments") ?: return@runCatching emptyList()
                val list = mutableListOf<KitsugiForumReply>()
                for (i in 0 until comments.length()) {
                    val item = comments.optJSONObject(i) ?: continue
                    val createdAt = item.optInt("createdAt")
                    val dateText = if (createdAt > 0) {
                        try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
                    } else null
                    val userObj = item.optJSONObject("user")
                    list.add(KitsugiForumReply(
                        id = item.optInt("id"), comment = item.optString("comment").cleanApiText(),
                        dateText = dateText,
                        username = userObj?.optNullableString("name") ?: "Kullanıcı",
                        avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                        likeCount = item.optInt("likeCount", 0), isLiked = item.optBoolean("isLiked", false),
                        userId = userObj?.optInt("id"),
                        createdAt = createdAt,
                        childComments = parseChildComments(item.optJSONArray("childComments"))
                    ))
                }
                list
            }.getOrElse { emptyList() }
        }
    }

    private fun parseChildComments(arr: org.json.JSONArray?): List<KitsugiForumReply> {
        if (arr == null) return emptyList()
        val list = mutableListOf<KitsugiForumReply>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val createdAt = item.optInt("createdAt")
            val dateText = if (createdAt > 0) {
                try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
            } else null
            val userObj = item.optJSONObject("user")
            list.add(KitsugiForumReply(
                id = item.optInt("id"), comment = item.optString("comment").cleanApiText(),
                dateText = dateText,
                username = userObj?.optNullableString("name") ?: "Kullanıcı",
                avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
                likeCount = item.optInt("likeCount", 0), isLiked = item.optBoolean("isLiked", false),
                userId = userObj?.optInt("id"),
                createdAt = createdAt,
                childComments = parseChildComments(item.optJSONArray("childComments"))
            ))
        }
        return list
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseActivity(item: JSONObject): KitsugiActivity {
        val createdAt = item.optInt("createdAt")
        val dateText = if (createdAt > 0) {
            try { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAt * 1000L)) } catch (_: Exception) { null }
        } else null
        val userObj = item.optJSONObject("user")
        var mediaTitleRomaji: String? = null; var mediaTitleEnglish: String? = null
        var mediaTitleNative: String? = null;  var mediaCoverUrl: String? = null
        var mediaId: Int? = null; var mediaType: String? = null; var isAdult = false
        if (item.has("media")) {
            val mediaObj = item.optJSONObject("media")
            if (mediaObj != null) {
                mediaId           = mediaObj.optInt("id")
                mediaType         = mediaObj.optNullableString("type")
                isAdult           = mediaObj.optBoolean("isAdult", false)
                val titleObj      = mediaObj.optJSONObject("title")
                mediaTitleRomaji  = titleObj?.optNullableString("romaji")
                mediaTitleEnglish = titleObj?.optNullableString("english")
                mediaTitleNative  = titleObj?.optNullableString("native")
                mediaCoverUrl     = mediaObj.optJSONObject("coverImage")?.optNullableString("large")
            }
        }
        val mediaTitle = mediaTitleRomaji ?: mediaTitleEnglish ?: mediaTitleNative
        val text = if (item.has("text")) item.optString("text")
                   else formatActivityText(item.optNullableString("status"), item.optNullableString("progress"), mediaTitle)
        return KitsugiActivity(
            id = item.optInt("id"), text = text.cleanApiText(), dateText = dateText,
            username = userObj?.optNullableString("name") ?: "Kullanıcı",
            avatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium"),
            mediaTitle = mediaTitle, mediaTitleRomaji = mediaTitleRomaji,
            mediaTitleEnglish = mediaTitleEnglish, mediaTitleNative = mediaTitleNative,
            mediaCoverUrl = mediaCoverUrl,
            likeCount = item.optInt("likeCount", 0), isLiked = item.optBoolean("isLiked", false),
            mediaId = mediaId, mediaType = mediaType, isAdult = isAdult,
            userId = userObj?.optInt("id"), source = "anilist"
        )
    }

    private fun formatActivityText(status: String?, progress: String?, mediaTitle: String? = null): String {
        val cleanStatus   = status?.lowercase()?.trim().orEmpty()
        val cleanProgress = progress?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val prefix = if (!mediaTitle.isNullOrBlank()) "**$mediaTitle** " else ""
        return when {
            cleanStatus.contains("completed")    -> if (cleanProgress != null) "${prefix}serisini tamamladı ($cleanProgress)" else "${prefix}serisini tamamladı"
            cleanStatus.contains("watched")      -> if (cleanProgress != null) "${prefix}$cleanProgress. bölümü izledi" else "${prefix}izledi"
            cleanStatus.contains("watching")     -> if (cleanProgress != null) "${prefix}$cleanProgress. bölümü izliyor" else "${prefix}izliyor"
            cleanStatus.contains("plan")         -> if (cleanProgress != null) "${prefix}izlemeyi planlıyor ($cleanProgress)" else "${prefix}izlemeyi planlıyor"
            cleanStatus.contains("dropped")      -> if (cleanProgress != null) "${prefix}bıraktı ($cleanProgress)" else "${prefix}bıraktı"
            cleanStatus.contains("paused")       -> if (cleanProgress != null) "${prefix}ara verdi ($cleanProgress)" else "${prefix}ara verdi"
            cleanStatus.contains("rewatc")       -> if (cleanProgress != null) "${prefix}tekrar izledi ($cleanProgress)" else "${prefix}tekrar izledi"
            else -> if (cleanProgress != null) "${prefix}${status.orEmpty()} ($cleanProgress)" else "${prefix}${status.orEmpty()}"
        }
    }
}

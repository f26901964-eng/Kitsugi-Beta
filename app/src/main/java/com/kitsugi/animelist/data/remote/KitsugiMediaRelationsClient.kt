package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kitsugi.animelist.utils.toTurkishRelationType

/**
 * İki medya kimliği çözümleme ve ilişki çekme işlemlerini yürüten istemci.
 * [KitsugiMediaTabsClient]'dan bölünmüştür.
 */
class KitsugiMediaRelationsClient {

    /**
     * Verilen MAL id'sini AniList id'sine çevirir.
     * Önce ARM API, ardından GraphQL fallback kullanır.
     */
    internal suspend fun resolveAniListId(malId: Int, mediaType: MediaType): Int? {
        if (malId <= 0) return null
        return withContext(Dispatchers.IO) {
            val armResolved = runCatching {
                KitsugiIdResolver.resolveIds(malId, null).aniListId
            }.getOrNull()
            if (armResolved != null && armResolved > 0) return@withContext armResolved

            val query = """
                query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                    Media(idMal: ${'$'}idMal, type: ${'$'}type) {
                        id
                    }
                }
            """.trimIndent()
            val variables = JSONObject()
                .put("idMal", malId)
                .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                val root = JSONObject(response)
                root.optJSONObject("data")?.optJSONObject("Media")?.optInt("id")
            }.getOrNull()
        }
    }

    suspend fun fetchRelations(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): List<KitsugiRelation> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) return@withContext emptyList()

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi" -> {
                    // 1) Bangumi'nin KENDİ ilişki ucu (p1 /relations): ön/devam, yan hikaye, uyarlama ...
                    val native = runCatching { KitsugiBangumiDetailClient.fetchRelations(externalId, mediaType) }
                        .getOrNull().orEmpty()
                    if (native.isNotEmpty()) return@withContext native

                    // 2) Yedek: Çözülen MAL / AniList kimliği (Bangumi stableId'si MAL ID DEĞİLDİR).
                    val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    if (mediaType == MediaType.Anime || mediaType == MediaType.Manga) {
                        if (malId != null && malId > 0) {
                            val list = fetchRelationsFromJikan(malId, mediaType)
                            if (list.isNotEmpty()) return@withContext list
                        }
                        val aniListStable = cross?.aniListStableId ?: resolveAniListStableIdForShikimori(malId)
                        if (aniListStable != null) fetchRelationsFromAniList(aniListStable, mediaType) else emptyList()
                    } else {
                        val resolvedTmdb = tmdbId ?: cross?.tmdbId
                        if (resolvedTmdb != null && resolvedTmdb > 0) {
                            TmdbApiClient().fetchRelations(resolvedTmdb, mediaType == MediaType.Movie)
                                .map { it.copy(source = "tmdb") }
                        } else emptyList()
                    }
                }
                "simkl" -> {
                    if (mediaType == MediaType.Anime) {
                        val malId = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                        if (malId != null && malId > 0) {
                            val malList = fetchRelations("jikan", malId, mediaType, null, null, title)
                            if (malList.isNotEmpty()) return@withContext malList
                        }
                    }
                    val resolvedTmdb = tmdbId ?: run {
                        val malIdForResolve = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                        KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, tmdbId = tmdbId).tmdbId
                    }
                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val tmdbRelations = TmdbApiClient().fetchRelations(resolvedTmdb, isMovie)
                        if (tmdbRelations.isNotEmpty()) return@withContext tmdbRelations.map { it.copy(source = "simkl") }
                    }
                    emptyList()
                }
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val tmdbRels = TmdbApiClient().fetchRelations(effectiveTmdbId, isMovie).map { it.copy(source = "tmdb") }
                        if (tmdbRels.isNotEmpty()) return@withContext tmdbRels

                        val mediaDetail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        val malId = realMalId ?: mediaDetail?.realMalId
                        if (malId != null && malId > 0) {
                            val malList = fetchRelations("jikan", malId, mediaType, null, null, title)
                            if (malList.isNotEmpty()) return@withContext malList
                        }

                        val cleanTitle = (title ?: mediaDetail?.title ?: mediaDetail?.titleEnglish)
                            ?.replace(Regex("\\s*\\(.*?\\)"), "")?.trim()
                        if (!cleanTitle.isNullOrBlank()) {
                            val aniRelations = fetchRelationsFromAniListBySearch(cleanTitle, mediaType)
                            if (aniRelations.isNotEmpty()) return@withContext aniRelations
                        }

                        emptyList()
                    } else emptyList()
                }
                "kitsu" -> {
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        val encoded = 100_000_000 + aniListId
                        val list = fetchRelationsFromAniList(encoded, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        val list = fetchRelationsFromJikan(jikanId, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    emptyList()
                }
                "shikimori" -> {
                    // 1) Shikimori'nin KENDİ `related` ucu: MAL/ARM eşlemesi gerektirmez,
                    //    tek istek sürer ve sayfa açılır açılmaz dolar.
                    val native = runCatching {
                        KitsugiShikimoriClient.fetchRelatedRelations(externalId, mediaType)
                    }.getOrNull().orEmpty()
                    if (native.isNotEmpty()) return@withContext native

                    // 2) Yedek: gerçek MAL ID'si çözülüp Jikan (MAL) ilişkileri kullanılır.
                    //    MAL eşlemesi yoksa AniList (ARM ile çözülen AniList ID) denenir.
                    val malId = resolveMalIdForShikimori(externalId, realMalId)
                    if (malId != null && malId > 0) {
                        val list = fetchRelationsFromJikan(malId, if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime)
                        if (list.isNotEmpty()) return@withContext list
                    }
                    val aniListStableId = resolveAniListStableIdForShikimori(malId)
                    if (aniListStableId != null) fetchRelationsFromAniList(aniListStableId, mediaType) else emptyList()
                }
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val jikanRelations = fetchRelationsFromJikan(malId, mediaType)
                    if (jikanRelations.isNotEmpty()) return@withContext jikanRelations

                    // Jikan is the canonical source for MAL records; use AniList by the exact
                    // MAL ID only if Jikan has no relation data (never fuzzy-title match here).
                    val aniListRelations = fetchRelationsFromAniList(malId, mediaType)
                    if (aniListRelations.isNotEmpty()) return@withContext aniListRelations

                    if (mediaType != MediaType.Manga && tmdbId != null && tmdbId > 0) {
                        TmdbApiClient().fetchRelations(tmdbId, mediaType == MediaType.Movie)
                            .map { it.copy(source = "tmdb") }
                    } else {
                        emptyList()
                    }
                }
                "anilist"      -> fetchRelationsFromAniList(externalId, mediaType)
                else           -> emptyList()
            }
        }
    }

    /**
     * Shikimori kayıtları için MAL ID'sini çözer (detay → ARM → Shikimori API zinciri).
     * Çözülemezse null döner — Shikimori ID'si ASLA MAL ID yerine kullanılmaz.
     */
    private fun resolveMalIdForShikimori(shikimoriId: Int, realMalId: Int?): Int? {
        realMalId?.takeIf { it > 0 }?.let { return it }
        DetailCache.getMediaDetail("shikimori", shikimoriId)?.realMalId?.takeIf { it > 0 }?.let { return it }
        return KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId)
    }

    /** Shikimori kaydı için AniList "stableId"si (100_000_000 + aniListId); yoksa null. */
    private suspend fun resolveAniListStableIdForShikimori(malId: Int?): Int? {
        val resolved = runCatching {
            KitsugiIdResolver.resolveIds(malId = malId, aniListId = null, tmdbId = null)
        }.getOrNull() ?: return null
        val aniListId = resolved.aniListId ?: return null
        return if (aniListId > 0) 100_000_000 + aniListId else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchRelationsFromJikan(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/relations")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                val list = mutableListOf<KitsugiRelation>()

                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val relType = item.optNullableString("relation") ?: "Relation"
                    val entries = item.optJSONArray("entry") ?: continue
                    for (j in 0 until entries.length()) {
                        val entry = entries.optJSONObject(j) ?: continue
                        val relMalId = entry.optInt("mal_id")
                        val title = entry.optNullableString("name") ?: "Bilinmeyen"
                        val mTypeStr = entry.optNullableString("type").orEmpty()
                        val mType = if (mTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                        list.add(KitsugiRelation(
                            malId = relMalId,
                            title = title,
                            relationType = relType.toTurkishRelationType(),
                            imageUrl = null,
                            mediaType = mType,
                            source = "jikan"
                        ))
                    }
                }

                // Jikan'ın ilişki listesi zaten kullanılabilir temel veridir. Kapak/başlık
                // zenginleştirmesini burada bekletmiyoruz; AniList'in yavaşlığı MAL listesinin
                // ekrana ulaşmasını engellememeli.
                list
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRelationsFromAniList(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    relations {
                        edges {
                            relationType(version: 2)
                            node {
                                id
                                idMal
                                title { romaji english native }
                                type
                                coverImage { large }
                                isAdult
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root  = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("relations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val relType  = edge.optNullableString("relationType") ?: "Relation"
                val node     = edge.optJSONObject("node") ?: continue
                val nodeId   = node.optInt("id")
                val nodeIdMal = node.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = node.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji
                    ?: titleEnglish
                    ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = node.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = node.optBoolean("isAdult", false)
                list.add(KitsugiRelation(
                    malId = stableId,
                    title = title,
                    relationType = relType.toTurkishRelationType(),
                    imageUrl = imageUrl,
                    mediaType = nodeType,
                    source = "anilist",
                    titleEnglish = titleEnglish,
                    titleJapanese = titleNative,
                    titleRomaji = titleRomaji,
                    isAdult = isAdult
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    suspend fun fetchRecommendations(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): List<KitsugiRelation> {
        return withContext(Dispatchers.IO) {
            if (source.equals("anilist", ignoreCase = true) && (externalId == null || externalId <= 0)) {
                // Kimliği bilinmeyen AniList kaydı: başlıkla ara (yanlış kimlikle veri getirme).
                val cleanTitle = title?.replace(Regex("\\s*\\(.*?\\)"), "")?.trim()
                if (cleanTitle.isNullOrBlank()) return@withContext emptyList()
                return@withContext fetchRecommendationsFromAniListBySearch(cleanTitle, mediaType)
            }
            if (externalId == null || externalId <= 0) return@withContext emptyList()

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi" -> {
                    // 1) Bangumi'nin kendi önerileri (p1 /recs; sunucu en fazla 10 kayıt verir).
                    val native = runCatching { KitsugiBangumiDetailClient.fetchRecommendations(externalId, mediaType) }
                        .getOrNull().orEmpty()
                    if (native.size >= 4) return@withContext native

                    // 2) Az/boşsa MAL / AniList / TMDB önerileriyle tamamla (çözülen kimliklerle).
                    val cross = runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    val extra: List<KitsugiRelation> = if (mediaType == MediaType.Anime || mediaType == MediaType.Manga) {
                        val fromMal = if (malId != null && malId > 0) fetchRecommendationsFromJikan(malId, mediaType) else emptyList()
                        if (fromMal.isNotEmpty()) {
                            fromMal
                        } else {
                            val aniListStable = cross?.aniListStableId ?: resolveAniListStableIdForShikimori(malId)
                            if (aniListStable != null) fetchRecommendationsFromAniList(aniListStable, mediaType) else emptyList()
                        }
                    } else {
                        val resolvedTmdb = tmdbId ?: cross?.tmdbId
                        if (resolvedTmdb != null && resolvedTmdb > 0) {
                            TmdbApiClient().fetchRecommendations(resolvedTmdb, mediaType == MediaType.Movie)
                                .map { it.copy(source = "tmdb") }
                        } else emptyList()
                    }
                    (native + extra).distinctBy { "${it.source}_${it.malId}" }
                }
                "simkl" -> {
                    if (mediaType == MediaType.Anime) {
                        val malId = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                        if (malId != null && malId > 0) {
                            val malList = fetchRecommendations("jikan", malId, mediaType, null, null, title)
                            if (malList.isNotEmpty()) return@withContext malList
                        }
                    }
                    val resolvedTmdb = tmdbId ?: run {
                        val malIdForResolve = realMalId ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                        KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, tmdbId = tmdbId).tmdbId
                    }
                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val tmdbRecommendations = TmdbApiClient().fetchRecommendations(resolvedTmdb, isMovie)
                        if (tmdbRecommendations.isNotEmpty()) return@withContext tmdbRecommendations.map { it.copy(source = "simkl") }
                    }
                    emptyList()
                }
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val mediaDetail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        val malId = realMalId ?: mediaDetail?.realMalId
                        if (malId != null && malId > 0) {
                            val malList = fetchRecommendations("jikan", malId, mediaType, null, null, title)
                            if (malList.isNotEmpty()) return@withContext malList
                        }

                        val cleanTitle = (title ?: mediaDetail?.title ?: mediaDetail?.titleEnglish)
                            ?.replace(Regex("\\s*\\(.*?\\)"), "")?.trim()
                        if (!cleanTitle.isNullOrBlank()) {
                            val aniRecs = fetchRecommendationsFromAniListBySearch(cleanTitle, mediaType)
                            if (aniRecs.isNotEmpty()) return@withContext aniRecs
                        }

                        TmdbApiClient().fetchRecommendations(effectiveTmdbId, isMovie).map { it.copy(source = "tmdb") }
                    } else emptyList()
                }
                "jikan", "mal" -> {
                    val malId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val jikanRecommendations = fetchRecommendationsFromJikan(malId, mediaType)
                    if (jikanRecommendations.isNotEmpty()) return@withContext jikanRecommendations

                    // Exact MAL-ID fallback keeps both MAL/Jikan result types on the same path.
                    val aniListRecommendations = fetchRecommendationsFromAniList(malId, mediaType)
                    if (aniListRecommendations.isNotEmpty()) return@withContext aniListRecommendations

                    // TMDB is a last-resort source. Do not resolve IDs through ARM or wait for
                    // TMDB when Jikan already has recommendations to show.
                    if (mediaType != MediaType.Manga && tmdbId != null && tmdbId > 0) {
                        TmdbApiClient().fetchRecommendations(tmdbId, mediaType == MediaType.Movie)
                            .map { it.copy(source = "tmdb") }
                    } else {
                        emptyList()
                    }
                }
                "kitsu" -> {
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        val encoded = 100_000_000 + aniListId
                        val list = fetchRecommendationsFromAniList(encoded, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        val list = fetchRecommendationsFromJikan(jikanId, mediaType)
                        if (list.isNotEmpty()) return@withContext list
                    }

                    emptyList()
                }
                "shikimori" -> {
                    // 1) Shikimori'nin KENDİ `similar` ucu: MAL/ARM eşlemesi gerektirmez.
                    val native = runCatching {
                        KitsugiShikimoriClient.fetchSimilarRecommendations(externalId, mediaType)
                    }.getOrNull().orEmpty()
                    if (native.isNotEmpty()) return@withContext native

                    // 2) Yedek: gerçek MAL ID'si çözülür (Shikimori ID'si MAL ID değildir).
                    val malId = resolveMalIdForShikimori(externalId, realMalId)
                    if (malId != null && malId > 0) {
                        val list = fetchRecommendationsFromJikan(
                            malId,
                            if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                        )
                        if (list.isNotEmpty()) return@withContext list
                    }
                    val aniListStableId = resolveAniListStableIdForShikimori(malId)
                    if (aniListStableId != null) fetchRecommendationsFromAniList(aniListStableId, mediaType) else emptyList()
                }
                "anilist"      -> fetchRecommendationsFromAniList(externalId, mediaType)
                else           -> emptyList()
            }
        }
    }

    private suspend fun fetchRecommendationsFromJikan(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = java.net.URL("https://api.jikan.moe/v4/$endpoint/$externalId/recommendations")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                val root = JSONObject(response)
                val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                val list = mutableListOf<KitsugiRelation>()

                for (i in 0 until minOf(data.length(), 20)) {
                    val item = data.optJSONObject(i) ?: continue
                    val entry = item.optJSONObject("entry") ?: continue
                    val relMalId = entry.optInt("mal_id")
                    val title = entry.optNullableString("title") ?: "Bilinmeyen"
                    val imagesObj = entry.optJSONObject("images")
                    val imageUrl = imagesObj?.optJSONObject("jpg")?.optNullableString("image_url")
                        ?: imagesObj?.optJSONObject("webp")?.optNullableString("image_url")
                    
                    list.add(KitsugiRelation(
                        malId = relMalId,
                        title = title,
                        relationType = "Öneri",
                        imageUrl = imageUrl,
                        mediaType = mediaType,
                        source = "jikan"
                    ))
                }

                // Öneri kartlarının Jikan'dan gelen başlık ve kapakları hemen gösterilir;
                // ek bir AniList isteği temel sonucun önüne geçmez.
                list
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRecommendationsFromAniList(
        externalId: Int,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
        val idParam  = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
        val idFilter = if (aniListId != null) "id: \$id"  else "idMal: \$idMal"
        val query = """
            query (${'$'}type: MediaType, $idParam) {
                Media($idFilter, type: ${'$'}type) {
                    recommendations(page: 1, perPage: 20, sort: [RATING_DESC, ID]) {
                        edges {
                            node {
                                mediaRecommendation {
                                    id
                                    idMal
                                    title { romaji english native }
                                    type
                                    coverImage { large }
                                    isAdult
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root  = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("recommendations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val mediaRec = node.optJSONObject("mediaRecommendation") ?: continue
                val nodeId   = mediaRec.optInt("id")
                val nodeIdMal = mediaRec.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = mediaRec.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji
                    ?: titleEnglish
                    ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = mediaRec.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = mediaRec.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = mediaRec.optBoolean("isAdult", false)
                list.add(KitsugiRelation(
                    malId = stableId,
                    title = title,
                    relationType = "Öneri",
                    imageUrl = imageUrl,
                    mediaType = nodeType,
                    source = "anilist",
                    titleEnglish = titleEnglish,
                    titleJapanese = titleNative,
                    titleRomaji = titleRomaji,
                    isAdult = isAdult
                ))
            }
            list
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRelationsFromAniListBySearch(
        searchTitle: String,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
                Media(search: ${'$'}search, type: ${'$'}type) {
                    relations {
                        edges {
                            relationType(version: 2)
                            node {
                                id
                                idMal
                                title { romaji english native }
                                type
                                coverImage { large }
                                isAdult
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", searchTitle)
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("relations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val relType = edge.optNullableString("relationType") ?: "Relation"
                val node = edge.optJSONObject("node") ?: continue
                val nodeId = node.optInt("id")
                val nodeIdMal = node.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = node.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji ?: titleEnglish ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = node.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = node.optBoolean("isAdult", false)
                list.add(
                    KitsugiRelation(
                        malId = stableId,
                        title = title,
                        relationType = relType.toTurkishRelationType(),
                        imageUrl = imageUrl,
                        mediaType = nodeType,
                        source = "anilist",
                        titleEnglish = titleEnglish,
                        titleJapanese = titleNative,
                        titleRomaji = titleRomaji,
                        isAdult = isAdult
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchRecommendationsFromAniListBySearch(
        searchTitle: String,
        mediaType: MediaType
    ): List<KitsugiRelation> {
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
                Media(search: ${'$'}search, type: ${'$'}type) {
                    recommendations(page: 1, perPage: 20, sort: [RATING_DESC, ID]) {
                        edges {
                            node {
                                mediaRecommendation {
                                    id
                                    idMal
                                    title { romaji english native }
                                    type
                                    coverImage { large }
                                    isAdult
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", searchTitle)
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
            val root = JSONObject(response)
            val edges = root.optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optJSONObject("recommendations")
                ?.optJSONArray("edges") ?: return emptyList()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                val mediaRec = node.optJSONObject("mediaRecommendation") ?: continue
                val nodeId = mediaRec.optInt("id")
                val nodeIdMal = mediaRec.optionalPositiveInt("idMal")
                val stableId = nodeIdMal ?: (100_000_000 + nodeId)
                val titleObj = mediaRec.optJSONObject("title")
                val titleRomaji = titleObj?.optNullableString("romaji")
                val titleEnglish = titleObj?.optNullableString("english")
                val titleNative = titleObj?.optNullableString("native")
                val title = titleRomaji ?: titleEnglish ?: titleNative ?: "Bilinmeyen"
                val nodeTypeStr = mediaRec.optNullableString("type").orEmpty()
                val nodeType = if (nodeTypeStr.equals("manga", ignoreCase = true)) MediaType.Manga else MediaType.Anime
                val imageUrl = mediaRec.optJSONObject("coverImage")?.optNullableString("large")
                val isAdult = mediaRec.optBoolean("isAdult", false)
                list.add(
                    KitsugiRelation(
                        malId = stableId,
                        title = title,
                        relationType = "Öneri",
                        imageUrl = imageUrl,
                        mediaType = nodeType,
                        source = "anilist",
                        titleEnglish = titleEnglish,
                        titleJapanese = titleNative,
                        titleRomaji = titleRomaji,
                        isAdult = isAdult
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }
}

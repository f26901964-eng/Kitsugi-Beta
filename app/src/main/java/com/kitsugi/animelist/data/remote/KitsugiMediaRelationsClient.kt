package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
                .put("type", if (mediaType == MediaType.Anime) "ANIME" else "MANGA")
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

            when (source.lowercase()) {
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
                "jikan", "mal" -> fetchRelationsFromJikan(externalId, mediaType)
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
        val endpoint = if (mediaType == MediaType.Anime) "anime" else "manga"
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

                // AniList bulk sorgusuyla kapak + İngilizce/Japonca başlıkları ekle
                // (başlık dili ayarı İngilizce/Japonca seçildiğinde Romaji'ye düşmesin).
                if (list.isNotEmpty()) {
                    val infoMap = fetchAniListBulkInfoByMalIds(list.map { it.malId }.distinct())
                    list.replaceAll { rel ->
                        val info = infoMap[rel.malId] ?: return@replaceAll rel
                        rel.copy(
                            imageUrl = info.coverUrl ?: rel.imageUrl,
                            titleRomaji = info.romaji ?: rel.title,
                            titleEnglish = info.english,
                            titleJapanese = info.native
                        )
                    }
                }
                list
            }
        }.getOrElse { emptyList() }
    }

    /** AniList'ten toplu çekilen kapak + başlık bilgisi (MAL kimliğiyle eşlenir). */
    private data class AniListBulkInfo(
        val coverUrl: String?,
        val romaji: String?,
        val english: String?,
        val native: String?
    )

    /**
     * Verilen MAL kimliklerini TEK AniList sorgusuyla çözer (kapak + romaji/İngilizce/Japonca).
     * Hata durumunda boş harita döner; çağıran liste yine de gösterilir.
     */
    private suspend fun fetchAniListBulkInfoByMalIds(malIds: List<Int>): Map<Int, AniListBulkInfo> {
        val ids = malIds.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val query = """
            query (${'$'}ids: [Int]) {
                Page(perPage: 50) {
                    media(idMal_in: ${'$'}ids) {
                        idMal
                        coverImage { large }
                        title { romaji english native }
                    }
                }
            }
        """.trimIndent()
        val out = HashMap<Int, AniListBulkInfo>()
        runCatching {
            val resp = KitsugiApiBase.executeAniListQuery(query, JSONObject().put("ids", JSONArray(ids)))
                ?: return@runCatching
            val mediaArr = JSONObject(resp).optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media") ?: return@runCatching
            for (k in 0 until mediaArr.length()) {
                val mi = mediaArr.optJSONObject(k) ?: continue
                val malId = mi.optInt("idMal")
                if (malId <= 0 || out.containsKey(malId)) continue
                val titleObj = mi.optJSONObject("title")
                out[malId] = AniListBulkInfo(
                    coverUrl = mi.optJSONObject("coverImage")?.optNullableString("large"),
                    romaji = titleObj?.optNullableString("romaji"),
                    english = titleObj?.optNullableString("english"),
                    native = titleObj?.optNullableString("native")
                )
            }
        }
        return out
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
        val variables = JSONObject().put("type", if (mediaType == MediaType.Anime) "ANIME" else "MANGA")
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
            if (externalId == null || externalId <= 0) return@withContext emptyList()

            when (source.lowercase()) {
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
                    val malList = fetchRecommendationsFromJikan(externalId, mediaType)
                    // For anime/TV, try to enrich with TMDB "More Like This" when a tmdbId is known
                    if (mediaType != MediaType.Manga) {
                        val effectiveTmdbId = tmdbId ?: runCatching {
                            KitsugiIdResolver.resolveIds(malId = realMalId ?: externalId, aniListId = null).tmdbId
                        }.getOrNull()
                        if (effectiveTmdbId != null && effectiveTmdbId > 0) {
                            val isMovie = mediaType == MediaType.Movie
                            val tmdbList = TmdbApiClient().fetchRecommendations(effectiveTmdbId, isMovie)
                            if (tmdbList.isNotEmpty()) {
                                // Keep MAL items (canonical IDs) first; append TMDB-only items after
                                val malIds = malList.map { it.malId }.toHashSet()
                                val tmdbOnly = tmdbList.filter { it.malId !in malIds }
                                return@withContext malList + tmdbOnly
                            }
                        }
                    }
                    malList
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
        val endpoint = if (mediaType == MediaType.Anime) "anime" else "manga"
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

                // Başlık dili İngilizce/Japonca iken Romaji'ye düşmemek için AniList'ten tamamla.
                if (list.isNotEmpty()) {
                    val infoMap = fetchAniListBulkInfoByMalIds(list.map { it.malId }.distinct())
                    list.replaceAll { rec ->
                        val info = infoMap[rec.malId] ?: return@replaceAll rec
                        rec.copy(
                            titleRomaji = info.romaji ?: rec.title,
                            titleEnglish = info.english,
                            titleJapanese = info.native
                        )
                    }
                }
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
        val variables = JSONObject().put("type", if (mediaType == MediaType.Anime) "ANIME" else "MANGA")
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
            .put("type", if (mediaType == MediaType.Anime) "ANIME" else "MANGA")

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
            .put("type", if (mediaType == MediaType.Anime) "ANIME" else "MANGA")

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

package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.*

class KitsugiDetailClient {

    private suspend fun getTurkishMetadataFromTmdb(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        providedTmdbId: Int? = null,
        providedRealMalId: Int? = null
    ): KitsugiMediaDetail? {
        val tmdbId = providedTmdbId ?: run {
            // Source'a göre doğru ID tipini belirle
            val malIdForResolve: Int? = when (source.lowercase()) {
                "simkl" -> providedRealMalId
                "jikan", "mal" -> externalId
                "anilist" -> {
                    // stableId < 100_000_000 → AniList arama sonucu MAL ID ile döndü
                    // Bu durumda MAL ID olarak çözümle, AniList ID olarak değil
                    if (externalId < 100_000_000) externalId else providedRealMalId
                }
                else -> null
            }
            val aniListIdForResolve: Int? = if (source.lowercase() == "anilist" && externalId >= 100_000_000) {
                // 100_000_000+ offset'li stableId → gerçek AniList ID'yi çıkar
                externalId - 100_000_000
            } else null
            val kitsuIdForResolve: Int? = if (source.lowercase() == "kitsu") {
                externalId - 300_000_000
            } else null
            
            KitsugiIdResolver.resolveIds(
                malId = malIdForResolve,
                aniListId = aniListIdForResolve,
                tmdbId = providedTmdbId,
                mediaType = mediaType,
                kitsuId = kitsuIdForResolve
            ).tmdbId
        }

        if (tmdbId != null && tmdbId > 0) {
            val isMovie = mediaType == MediaType.Movie
            val firstTry = TmdbApiClient().fetchMediaDetail(tmdbId, isMovie)
            if (firstTry != null) return firstTry

            // Anime veya TMDB kaynaklı içeriklerde film/dizi ayrımı yanlış yapılmış olabilir.
            // İlk deneme null dönerse, diğer formatta tekrar çekmeyi dene (TV -> Movie veya Movie -> TV).
            if (mediaType != MediaType.Manga) {
                return TmdbApiClient().fetchMediaDetail(tmdbId, !isMovie)
            }
        }
        return null
    }

    /**
     * Simkl kayıtları için TMDB tabanlı ayrıntı çözümü.
     *
     * Simkl API'si yavaş ve gecikmeli veri döndürdüğü için Simkl kütüphanesindeki
     * kayıtların ayrıntı sayfası önce TMDB üzerinden açılır:
     *  1. Bilinen/çözülen TMDB ID ile doğrudan TMDB,
     *  2. Olmazsa ters medya türüyle (film ↔ dizi) TMDB,
     *  3. TMDB ID hiç yoksa başlıkla TMDB araması.
     * Hiçbiri tutmazsa null döner; çağıran taraf MAL (Jikan) → Simkl zincirini dener.
     */
    private suspend fun fetchSimklDetailViaTmdb(
        tmdbId: Int?,
        mediaType: MediaType,
        title: String?
    ): KitsugiMediaDetail? {
        if (mediaType == MediaType.Manga) return null

        if (tmdbId != null && tmdbId > 0) {
            val isMovie = mediaType == MediaType.Movie
            TmdbApiClient().fetchMediaDetail(tmdbId, isMovie)?.let { return it }
            if (mediaType != MediaType.Manga) {
                TmdbApiClient().fetchMediaDetail(tmdbId, !isMovie)?.let { return it }
            }
        }

        // TMDB ID çözülemedi → başlıkla ara (dizi/film başlıkları TMDB'de en güvenilir sonucu verir)
        val searchTitle = title?.trim().orEmpty()
        if (searchTitle.isBlank()) return null

        val results = runCatching { TmdbApiClient().search(searchTitle) }.getOrNull().orEmpty()
        if (results.isEmpty()) return null

        // Beklenen tür önce denenir; anime kayıtları TMDB'de dizi ya da film olarak görünür.
        val preferredTypes = when (mediaType) {
            MediaType.Movie -> listOf(MediaType.Movie, MediaType.TvShow)
            MediaType.TvShow -> listOf(MediaType.TvShow, MediaType.Movie)
            else -> listOf(MediaType.TvShow, MediaType.Movie, MediaType.Anime)
        }

        for (type in preferredTypes) {
            val candidate = results.firstOrNull { it.type == type && (it.tmdbId ?: 0) > 0 } ?: continue
            TmdbApiClient().fetchMediaDetail(candidate.tmdbId!!, type == MediaType.Movie)?.let { return it }
        }
        return null
    }

    suspend fun fetchSynopsis(
        source: String,
        externalId: Int?,
        mediaType: MediaType
    ): String? {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                return@withContext null
            }

            if (mediaType != MediaType.Manga) {
                // Simkl kayıtlarında TMDB çözümü için önbellekteki gerçek ID'ler kullanılır,
                // böylece özet de Simkl API'si yerine TMDB'den gelebilir.
                val cachedSimklDetail =
                    if (source.equals("simkl", ignoreCase = true)) DetailCache.getMediaDetail("simkl", externalId) else null
                val trMeta = getTurkishMetadataFromTmdb(
                    source = source,
                    externalId = externalId,
                    mediaType = mediaType,
                    providedTmdbId = cachedSimklDetail?.tmdbId,
                    providedRealMalId = cachedSimklDetail?.realMalId
                )
                if (trMeta != null && !trMeta.synopsis.isNullOrBlank()) {
                    return@withContext trMeta.synopsis
                }
            }

            when (source.lowercase()) {
                "jikan", "mal" -> KitsugiMalDetailClient.fetchSynopsis(
                    malId = externalId,
                    mediaType = mediaType
                )

                "anilist" -> KitsugiAniListDetailClient.fetchSynopsis(
                    stableId = externalId,
                    mediaType = mediaType
                )

                "simkl" -> KitsugiSimklDetailClient.fetchSimklDetailDirect(externalId, mediaType)?.synopsis

                else -> null
            }
        }
    }

    suspend fun fetchDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) return@withContext null

            val mediaTypeStr = mediaType.name.lowercase()
            val cacheKey = "${source.lowercase()}_${mediaTypeStr}_$externalId"
            val legacyKey = if (source.lowercase() == "tmdb") {
                val typeStr = if (mediaType == MediaType.Movie) "movie" else "tv"
                "tmdb_${typeStr}_$externalId"
            } else {
                "${source.lowercase()}_$externalId"
            }
            val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            val db = context?.let { com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(it) }
            val gson = com.google.gson.Gson()

            // 1. Fresh Cache Check (Room - 24 hours threshold)
            if (db != null) {
                try {
                    val cached = db.persistentDetailCacheDao().getDetail(cacheKey) 
                        ?: db.persistentDetailCacheDao().getDetail(legacyKey)
                    if (cached != null) {
                        val isFresh = (System.currentTimeMillis() - cached.cachedAtMs) < 24 * 60 * 60 * 1000L
                        if (isFresh) {
                            val detail = gson.fromJson(cached.detailJson, KitsugiMediaDetail::class.java)
                            if (detail != null) {
                                // Eski nsfw!="white" hatasından dolayı yanlışlıkla isAdult=true kalan içerikleri düzelt
                                val isActuallyAdult = detail.isAdult && (
                                    detail.rating?.contains("rx", ignoreCase = true) == true ||
                                    detail.rating?.contains("hentai", ignoreCase = true) == true ||
                                    detail.genres.any { it.contains("hentai", ignoreCase = true) }
                                )
                                val cleanDetail = if (detail.isAdult && !isActuallyAdult) {
                                    detail.copy(isAdult = false)
                                } else {
                                    detail
                                }

                                if (!title.isNullOrBlank() && !detail.title.isNullOrBlank() && source.lowercase() == "tmdb") {
                                    val orig = title.trim().lowercase()
                                    val dTitle = detail.title?.trim()?.lowercase().orEmpty()
                                    val dEnTitle = detail.titleEnglish?.trim()?.lowercase().orEmpty()
                                    val matches = dTitle.contains(orig) || orig.contains(dTitle) || dEnTitle.contains(orig) || orig.contains(dEnTitle)
                                    if (!matches) {
                                        android.util.Log.w("KitsugiDetailClient", "Cached TMDB detail title mismatch: expected '$title', got '${detail.title}'. Invalidating cache.")
                                        db.persistentDetailCacheDao().deleteDetail(cacheKey)
                                        db.persistentDetailCacheDao().deleteDetail("tmdb_$externalId")
                                    } else {
                                        android.util.Log.d("KitsugiDetailClient", "Serving fresh detail from Room cache for $cacheKey")
                                        return@withContext cleanDetail
                                    }
                                } else {
                                    android.util.Log.d("KitsugiDetailClient", "Serving fresh detail from Room cache for $cacheKey")
                                    return@withContext cleanDetail
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error reading detail cache: ${e.message}")
                }
            }

            // 2. Primary source fetch
            val detail = when (source.lowercase()) {
                "jikan", "mal" -> KitsugiMalDetailClient.fetchDetail(externalId, mediaType)
                "shikimori" -> KitsugiShikimoriClient.fetchDetail(externalId, mediaType) ?: KitsugiMalDetailClient.fetchDetail(externalId, mediaType)
                "anilist" -> KitsugiAniListDetailClient.fetchDetail(externalId, mediaType)
                // Kitsu keşfet fallback öğeleri: stableId = kitsuId + 300_000_000
                "kitsu" -> {
                    android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail for stableId=$externalId")
                    val kitsuDetail = KitsuExploreClient.fetchDetailByStableId(externalId, mediaType)
                    // AnimeThemes entegrasyonu: Kitsu ID'si ile tema müziklerini çek
                    if (kitsuDetail != null && mediaType != MediaType.Manga) {
                        val kitsuNumericId = if (externalId >= KitsuExploreClient.ID_OFFSET) {
                            externalId - KitsuExploreClient.ID_OFFSET
                        } else {
                            externalId
                        }
                        if (kitsuNumericId > 0) {
                            try {
                                val themes = KitsugiAnimeThemesClient.fetchAnimeThemes(kitsuNumericId, "Kitsu")
                                if (themes.first.isNotEmpty() || themes.second.isNotEmpty()) {
                                    android.util.Log.d("KitsugiDetailClient", "AnimeThemes Kitsu: ${themes.first.size} OP, ${themes.second.size} ED")
                                    kitsuDetail.copy(openings = themes.first, endings = themes.second)
                                } else kitsuDetail
                            } catch (e: Exception) {
                                android.util.Log.w("KitsugiDetailClient", "AnimeThemes Kitsu fetch failed: ${e.message}")
                                kitsuDetail
                            }
                        } else kitsuDetail
                    } else kitsuDetail
                }
                // TMDB discovery öğeleri: malId aslında tmdbId, doğrudan TMDB'den çek
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val firstTry = TmdbApiClient().fetchMediaDetail(effectiveTmdbId, isMovie)
                        if (firstTry != null && !title.isNullOrBlank()) {
                            val originalTitle = title.trim().lowercase()
                            val fetchedTitle = firstTry.title?.trim()?.lowercase().orEmpty()
                            val fetchedEnTitle = firstTry.titleEnglish?.trim()?.lowercase().orEmpty()
                            val matches = fetchedTitle.contains(originalTitle) || originalTitle.contains(fetchedTitle) ||
                                          fetchedEnTitle.contains(originalTitle) || originalTitle.contains(fetchedEnTitle)
                            if (matches) {
                                firstTry
                            } else {
                                android.util.Log.w("KitsugiDetailClient", "TMDB detail title mismatch: expected '$title', got '$fetchedTitle'. Trying alternative type (!isMovie)...")
                                val secondTry = TmdbApiClient().fetchMediaDetail(effectiveTmdbId, !isMovie)
                                secondTry ?: firstTry
                            }
                        } else {
                            firstTry ?: TmdbApiClient().fetchMediaDetail(effectiveTmdbId, !isMovie)
                        }
                    } else null
                }
                "simkl" -> {
                    // ── Öncelik zinciri (TMDB ilk sıradadır) ────────────────────────────
                    // Simkl API'si verileri gecikmeli döndürdüğü için Simkl kayıtlarının
                    // ayrıntı sayfası önce TMDB üzerinden açılır. TMDB çözülemezse
                    // anime kayıtlarında Jikan (MAL), son çare olarak Simkl kullanılır.
                    val malIdForResolve = realMalId?.takeIf { it > 0 }
                        ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 } ?: run {
                        KitsugiIdResolver.resolveIds(
                            malId = malIdForResolve,
                            aniListId = null,
                            tmdbId = null,
                            mediaType = mediaType
                        ).tmdbId
                    }

                    val tmdbDetail = fetchSimklDetailViaTmdb(
                        tmdbId = resolvedTmdb,
                        mediaType = mediaType,
                        title = title
                    )
                    if (tmdbDetail != null) {
                        tmdbDetail
                    } else if (mediaType == MediaType.Anime && malIdForResolve != null && malIdForResolve > 0) {
                        KitsugiMalDetailClient.fetchDetail(malIdForResolve, mediaType)
                            ?: KitsugiSimklDetailClient.fetchSimklDetailDirect(externalId, mediaType)
                    } else {
                        KitsugiSimklDetailClient.fetchSimklDetailDirect(externalId, mediaType)
                    }
                }
                else -> null
            }

            var finalDetail = detail
            
            // 3. Fallback Client Chains (Live Backups)
            if (finalDetail == null) {
                if (mediaType == MediaType.Movie || mediaType == MediaType.TvShow) {
                    // TMDB Fallback: TVmaze for TV Shows
                    if (mediaType == MediaType.TvShow && !title.isNullOrBlank()) {
                        android.util.Log.d("KitsugiDetailClient", "TMDB returned null. Trying TVmaze fallback for TV show: $title")
                        finalDetail = TvMazeClient.fetchShowDetailByTitle(title)
                    }
                    // TMDB Fallback: Search fallback by title
                    if (finalDetail == null && !title.isNullOrBlank()) {
                        android.util.Log.d("KitsugiDetailClient", "Trying direct TMDB search fallback for: $title")
                        val searchResults = TmdbApiClient().search(title)
                        val matchedResult = searchResults.firstOrNull { it.type == mediaType }
                        if (matchedResult != null && matchedResult.tmdbId != null && matchedResult.tmdbId > 0) {
                            finalDetail = TmdbApiClient().fetchMediaDetail(matchedResult.tmdbId, mediaType == MediaType.Movie)
                        }
                    }
                } else if (mediaType == MediaType.Anime) {
                    // Anime Fallback: Kitsu
                    android.util.Log.d("KitsugiDetailClient", "AniList/MAL detail returned null. Trying Kitsu fallback.")
                    val kitsuId = if (db != null) {
                        val resolvedEntity = if (source.lowercase() == "anilist") {
                            db.mediaMetaCacheDao().getByAniListId(externalId)
                        } else {
                            db.mediaMetaCacheDao().getByMalId(externalId)
                        }
                        resolvedEntity?.kitsuId
                    } else null

                    if (!kitsuId.isNullOrBlank()) {
                        android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail via resolved kitsuId: $kitsuId")
                        finalDetail = KitsuClient.fetchAnimeDetail(kitsuId)
                    }
                    if (finalDetail == null && !title.isNullOrBlank()) {
                        android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail via title search: $title")
                        finalDetail = KitsuClient.fetchAnimeDetailByTitle(title)
                    }
                    if (finalDetail == null && !title.isNullOrBlank()) {
                        runCatching {
                            val simklResults = SimklApiClient().search(title, type = "anime", limit = 1)
                            val matched = simklResults.firstOrNull()
                            if (matched != null && matched.malId > 0) {
                                finalDetail = KitsugiSimklDetailClient.fetchSimklDetailDirect(matched.malId, mediaType)
                            }
                        }
                    }
                    if (finalDetail == null && !title.isNullOrBlank()) {
                        runCatching {
                            val jikanResults = JikanApiClient().search(title, MediaType.Anime)
                            val matched = jikanResults.firstOrNull()
                            if (matched != null && matched.malId > 0) {
                                finalDetail = KitsugiMalDetailClient.fetchDetail(matched.malId, mediaType)
                            }
                        }
                    }
                }
            }

            val currentDetail = finalDetail
            if (currentDetail != null && mediaType != MediaType.Manga) {
                // TMDB zenginleştirmesi için en iyi MAL ID'yi bul
                val effectiveRealMalId = realMalId
                    ?: currentDetail.realMalId
                    ?: if (source.lowercase() == "anilist" && externalId < 100_000_000) externalId else null
                
                var resolvedTmdbId = tmdbId ?: currentDetail.tmdbId
                
                // Fallback scenario 2: Primary detail is not null, but tmdbId is missing -> Try direct TMDB search fallback by title!
                if ((resolvedTmdbId == null || resolvedTmdbId <= 0) && (mediaType == MediaType.Movie || mediaType == MediaType.TvShow)) {
                    val searchTitle = title ?: currentDetail.title ?: currentDetail.titleEnglish
                    if (!searchTitle.isNullOrBlank()) {
                        android.util.Log.d("KitsugiDetailClient", "Primary resolution has no tmdbId. Triggering TMDB search for title: $searchTitle")
                        val searchResults = TmdbApiClient().search(searchTitle)
                        val matchedResult = searchResults.firstOrNull { it.type == mediaType }
                        if (matchedResult != null && matchedResult.tmdbId != null && matchedResult.tmdbId > 0) {
                            resolvedTmdbId = matchedResult.tmdbId
                            android.util.Log.d("KitsugiDetailClient", "Resolved tmdbId = $resolvedTmdbId via search for title: $searchTitle")
                        }
                    }
                }
                
                val trMeta = getTurkishMetadataFromTmdb(source, externalId, mediaType, resolvedTmdbId, effectiveRealMalId)
                var mergedDetail = if (trMeta != null) {
                    val updatedSynopsis = if (!trMeta.synopsis.isNullOrBlank()) trMeta.synopsis else currentDetail.synopsis
                    // Kaynak Otoritesi Kuralı (Source Authority Preservation):
                    // Birincil kaynağın başlıkları, türleri, stüdyoları ve kapak görseli her zaman önceliklidir!
                    val updatedTitle = if (!currentDetail.title.isNullOrBlank()) currentDetail.title else trMeta.title
                    val updatedTitleEnglish = if (!currentDetail.titleEnglish.isNullOrBlank()) currentDetail.titleEnglish else trMeta.titleEnglish
                    val updatedGenres = if (currentDetail.genres.isNotEmpty()) currentDetail.genres else trMeta.genres
                    val combinedPictures = (currentDetail.pictures.orEmpty() + trMeta.pictures.orEmpty()).distinct()
                    val mergedStudios = if (currentDetail.studios.isNotEmpty()) currentDetail.studios else trMeta.studios
                    val mergedProducers = if (currentDetail.producers.isNotEmpty()) currentDetail.producers else trMeta.producers
                    val mergedRating = if (!currentDetail.rating.isNullOrBlank()) currentDetail.rating else trMeta.rating
                    val updatedImageUrl = if (!currentDetail.imageUrl.isNullOrBlank()) currentDetail.imageUrl else trMeta.imageUrl
                    currentDetail.copy(
                        synopsis = updatedSynopsis,
                        title = updatedTitle,
                        titleEnglish = updatedTitleEnglish,
                        genres = updatedGenres,
                        tmdbId = resolvedTmdbId ?: currentDetail.tmdbId,
                        imageUrl = updatedImageUrl,
                        pictures = combinedPictures,
                        studios = mergedStudios,
                        producers = mergedProducers,
                        rating = mergedRating,
                        totalSeasons = if (source.lowercase() == "tmdb" || source.lowercase() == "simkl") {
                            trMeta.totalSeasons ?: currentDetail.totalSeasons
                        } else {
                            currentDetail.totalSeasons ?: 1
                        },
                        meanScore = currentDetail.meanScore ?: trMeta.meanScore,
                        averageScore = currentDetail.averageScore ?: trMeta.averageScore,
                        popularity = currentDetail.popularity ?: trMeta.popularity,
                        favorites = currentDetail.favorites ?: trMeta.favorites,
                        rank = currentDetail.rank ?: trMeta.rank,
                        popularityRank = currentDetail.popularityRank ?: trMeta.popularityRank,
                        scoredBy = currentDetail.scoredBy ?: trMeta.scoredBy,
                        members = currentDetail.members ?: trMeta.members,
                        nextAiringEpisode = currentDetail.nextAiringEpisode ?: trMeta.nextAiringEpisode
                    )
                } else {
                    currentDetail
                }

                // Eğer nextAiringEpisode hâlâ null ise AniList üzerinden çöz ve çek
                if (mergedDetail.nextAiringEpisode == null) {
                    val malIdForResolve = when (source.lowercase()) {
                        "simkl" -> realMalId ?: mergedDetail.realMalId
                        "jikan", "mal" -> externalId
                        "anilist" -> if (externalId < 100_000_000) externalId else realMalId ?: mergedDetail.realMalId
                        else -> null
                    }
                    val resolvedAniListId = runCatching {
                        KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = null, mediaType = mediaType).aniListId
                    }.getOrNull()
                    if (resolvedAniListId != null && resolvedAniListId > 0) {
                        val nextAiring = KitsugiAniListDetailClient.fetchNextAiringEpisodeOnly(resolvedAniListId)
                        if (nextAiring != null) {
                            mergedDetail = mergedDetail.copy(nextAiringEpisode = nextAiring)
                        }
                    }
                }
                finalDetail = mergedDetail
            }

            // 4. Stale Cache Fallback (If all network attempts returned null, check cache again even if expired)
            if (finalDetail == null && db != null) {
                try {
                    val cached = db.persistentDetailCacheDao().getDetail(cacheKey)
                    if (cached != null) {
                        finalDetail = gson.fromJson(cached.detailJson, KitsugiMediaDetail::class.java)
                        if (finalDetail != null) {
                            android.util.Log.d("KitsugiDetailClient", "Serving stale detail from Room cache for $cacheKey")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error reading stale cache: ${e.message}")
                }
            }

            // 5. Cache update on success
            if (finalDetail != null && db != null) {
                try {
                    val entity = com.kitsugi.animelist.data.local.PersistentDetailCacheEntity(
                        cacheKey = cacheKey,
                        detailJson = gson.toJson(finalDetail),
                        cachedAtMs = System.currentTimeMillis()
                    )
                    db.persistentDetailCacheDao().insertDetail(entity)
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error writing detail cache: ${e.message}")
                }
            }

            finalDetail
        }
    }
}

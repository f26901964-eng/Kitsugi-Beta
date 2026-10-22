package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.trailer.DetailTrailerFallback
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.isTurkish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Detay sayfasındaki **Videolar** sekmesi için tüm kaynakları ortak çalıştıran toplayıcı.
 *
 * Tek bir kaynağın sınırlı video alanı yerine 8 farklı kaynaktan paralel olarak video toplar,
 * aynı videoyu farklı kaynaklar sunduğunda kopya satır üretmek yerine tek satırda birleştirip
 * tüm kaynak rozetlerini (`source` + `extraSources`) korur:
 *  - Birincil detay (`KitsugiMediaDetail`)
 *  - MyAnimeList / Jikan (`/v4/anime/{malId}/videos` → tüm PV'ler, karakter tanıtımları, resmi MV'ler)
 *  - AnimeThemes (`api.animethemes.moe` → doğrudan oynatılabilir HD OP / ED videoları)
 *  - TMDB (`/3/{tv|movie}/{tmdbId}/videos?include_video_language=tr,en,ja,ko,zh,null` → Fragman, Teaser, Kamera Arkası, Klip)
 *  - AniList (`graphql.anilist.co` → resmi YouTube / Dailymotion fragmanı)
 *  - Shikimori (`/api/animes/{id}/videos` → PV, OP, ED, CM, karakter tanıtımı, klip)
 *  - Simkl (`/{type}/{simklId}?extended=full` → `trailers[]`)
 *  - Kitsu (`/api/edge/anime/{kitsuId}` → `youtubeVideoId`)
 *  - Eklenti yedeği (`DetailTrailerFallback` → hiçbir kaynakta fragman yoksa Cloudstream eklentileri)
 */
object KitsugiCrossSourceVideoAggregator {

    private const val TAG = "CrossSourceVideos"
    private const val PER_SOURCE_TIMEOUT_MS = 6_500L
    private const val SIMKL_LOOKUP_TIMEOUT_MS = 3_500L
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private val YOUTUBE_ID_REGEXES = listOf(
        Regex("""(?:youtube(?:-nocookie)?\.com/(?:watch\?.*?v=|embed/|shorts/|v/)|youtu\.be/)([A-Za-z0-9_-]{11})"""),
        Regex("""^([A-Za-z0-9_-]{11})$""")
    )

    private data class CachedVideos(
        val items: List<KitsugiVideoItem>,
        val cachedAtMs: Long
    )

    private data class CrossVideoIds(
        val malId: Int? = null,
        val aniListId: Int? = null,
        val tmdbId: Int? = null,
        val kitsuId: Int? = null,
        val simklId: Int? = null,
        val bangumiId: Int? = null,
        val shikimoriId: Int? = null
    )

    private val memoryCache = BoundedCache<String, CachedVideos>("videos.crossSource", 256)
    private val aggregatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<List<KitsugiVideoItem>>>()

    internal fun extractCanonicalYouTubeId(url: String?): String? {
        val clean = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (regex in YOUTUBE_ID_REGEXES) {
            val match = regex.find(clean)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) return match
        }
        return null
    }

    internal fun buildSeedVideosFromDetail(
        detail: KitsugiMediaDetail?,
        primarySource: String
    ): List<KitsugiVideoItem> {
        if (detail == null) return emptyList()
        val canonicalSrc = canonicalVideoSource(primarySource)
        val raw = mutableListOf<KitsugiVideoItem>()
        raw += detail.unifiedVideos

        detail.trailerUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { url ->
            val ytId = extractCanonicalYouTubeId(url)
            raw += KitsugiVideoItem(
                id = "seed-trailer-${ytId ?: url.hashCode()}",
                title = if (isTurkish()) "Resmi Fragman" else "Official Trailer",
                videoUrl = url,
                category = KitsugiVideoCategory.TRAILER,
                source = canonicalSrc,
                thumbnailUrl = ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            )
        }

        detail.openings.forEachIndexed { idx, theme ->
            val src = inferThemeSource(theme.videoUrl, canonicalSrc)
            val ytId = extractCanonicalYouTubeId(theme.videoUrl)
            raw += KitsugiVideoItem(
                id = "seed-op-$idx",
                title = theme.label,
                videoUrl = theme.videoUrl,
                category = classifyThemeOrExtraLabel(theme.label, defaultCategory = KitsugiVideoCategory.OPENING),
                source = src,
                thumbnailUrl = ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            )
        }

        detail.endings.forEachIndexed { idx, theme ->
            val src = inferThemeSource(theme.videoUrl, canonicalSrc)
            val ytId = extractCanonicalYouTubeId(theme.videoUrl)
            raw += KitsugiVideoItem(
                id = "seed-ed-$idx",
                title = theme.label,
                videoUrl = theme.videoUrl,
                category = KitsugiVideoCategory.ENDING,
                source = src,
                thumbnailUrl = ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            )
        }

        return mergeCrossSourceVideos(raw)
    }

    /**
     * Bir `KitsugiMediaDetail` kaydını tüm platformlardaki videolarla zenginleştirir.
     * Hem `unifiedVideos` alanını doldurur hem de eksik `trailerUrl` / `openings` / `endings`
     * alanlarını tamamlar; böylece hem mobil/tablet hem TV detay ekranı ortak veriden yararlanır.
     */
    suspend fun enrichDetailWithCrossSourceVideos(
        context: Context?,
        source: String,
        externalId: Int,
        mediaType: MediaType,
        detail: KitsugiMediaDetail,
        hintTmdbId: Int? = null,
        hintMalId: Int? = null,
        hintSimklId: Int? = null,
        title: String? = null,
        year: Int? = null
    ): KitsugiMediaDetail = withContext(Dispatchers.IO) {
        if (mediaType == MediaType.Manga) return@withContext detail

        val cacheKey = buildCacheKey(source, externalId, mediaType)
        val now = System.currentTimeMillis()
        val cached = memoryCache[cacheKey]?.takeIf { now - it.cachedAtMs < CACHE_TTL_MS }?.items

        val mergedVideos = cached ?: run {
            val job = inFlight.computeIfAbsent(cacheKey) {
                aggregatorScope.async {
                    try {
                        collectAllSourceVideos(
                            context = context,
                            source = source,
                            externalId = externalId,
                            mediaType = mediaType,
                            detail = detail,
                            hintTmdbId = hintTmdbId,
                            hintMalId = hintMalId,
                            hintSimklId = hintSimklId,
                            title = title,
                            year = year
                        )
                    } finally {
                        inFlight.remove(cacheKey)
                    }
                }
            }
            val result = try {
                job.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Cross-source video aggregation failed ($cacheKey): ${e.message}")
                buildSeedVideosFromDetail(detail, source)
            }
            if (result.isNotEmpty()) {
                memoryCache[cacheKey] = CachedVideos(result, System.currentTimeMillis())
            }
            result
        }

        applyUnifiedVideosToDetail(detail, mergedVideos)
    }

    internal fun applyUnifiedVideosToDetail(
        detail: KitsugiMediaDetail,
        videos: List<KitsugiVideoItem>
    ): KitsugiMediaDetail {
        if (videos.isEmpty()) return detail
        val bestTrailer = detail.trailerUrl?.takeIf { it.isNotBlank() }
            ?: videos.firstOrNull {
                it.category == KitsugiVideoCategory.TRAILER && !it.videoUrl.isNullOrBlank()
            }?.videoUrl
            ?: videos.firstOrNull { !it.videoUrl.isNullOrBlank() }?.videoUrl

        val enrichedOpenings = videos
            .filter { it.category == KitsugiVideoCategory.OPENING }
            .map { KitsugiTheme(label = it.title, videoUrl = it.videoUrl) }
            .ifEmpty { detail.openings }

        val enrichedEndings = videos
            .filter { it.category == KitsugiVideoCategory.ENDING }
            .map { KitsugiTheme(label = it.title, videoUrl = it.videoUrl) }
            .ifEmpty { detail.endings }

        return detail.copy(
            trailerUrl = bestTrailer,
            openings = enrichedOpenings,
            endings = enrichedEndings,
            unifiedVideos = videos
        )
    }

    private suspend fun collectAllSourceVideos(
        context: Context?,
        source: String,
        externalId: Int,
        mediaType: MediaType,
        detail: KitsugiMediaDetail,
        hintTmdbId: Int?,
        hintMalId: Int?,
        hintSimklId: Int?,
        title: String?,
        year: Int?
    ): List<KitsugiVideoItem> = supervisorScope {
        val ids = resolveCrossVideoIds(
            source = source,
            externalId = externalId,
            mediaType = mediaType,
            detail = detail,
            hintTmdbId = hintTmdbId,
            hintMalId = hintMalId,
            hintSimklId = hintSimklId
        )

        val expectedTitles = listOfNotNull(
            title,
            detail.title,
            detail.titleEnglish,
            detail.titleRomaji,
            detail.titleNative,
            detail.titleJapanese,
            detail.titleTurkish
        ) + detail.synonyms

        val seedVideos = buildSeedVideosFromDetail(detail, source)

        val jikanDef = async(Dispatchers.IO) {
            val malId = ids.malId ?: return@async emptyList()
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching { fetchJikanVideos(malId) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val animeThemesDef = async(Dispatchers.IO) {
            if (mediaType != MediaType.Anime && ids.malId == null && ids.aniListId == null && ids.kitsuId == null) {
                return@async emptyList()
            }
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching { fetchAnimeThemesVideos(ids) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val tmdbDef = async(Dispatchers.IO) {
            val tmdbId = ids.tmdbId ?: return@async emptyList()
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching {
                    fetchTmdbAllLanguageVideos(
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        detail = detail,
                        isPrimaryTmdb = source.equals("tmdb", ignoreCase = true),
                        expectedTitles = expectedTitles
                    )
                }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val aniListDef = async(Dispatchers.IO) {
            if (ids.aniListId == null && ids.malId == null) return@async emptyList()
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching { fetchAniListTrailerVideo(ids.aniListId, ids.malId) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val shikimoriDef = async(Dispatchers.IO) {
            val shikiId = ids.shikimoriId ?: ids.malId ?: return@async emptyList()
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching { fetchShikimoriVideoItems(shikiId) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val simklDef = async(Dispatchers.IO) {
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching {
                    val effectiveSimklId = ids.simklId ?: withTimeoutOrNull(SIMKL_LOOKUP_TIMEOUT_MS) {
                        SimklApiClient().lookupSimklId(
                            malId = ids.malId,
                            tmdbId = ids.tmdbId,
                            aniListId = ids.aniListId,
                            mediaType = mediaType
                        )
                    }
                    if (effectiveSimklId != null && effectiveSimklId > 0) {
                        KitsugiSimklDetailClient.fetchSimklRawJson(effectiveSimklId, mediaType)
                            ?.let { KitsugiSimklDetailClient.extractSimklTrailers(it) }
                            .orEmpty()
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val kitsuDef = async(Dispatchers.IO) {
            val kitsuId = ids.kitsuId ?: return@async emptyList()
            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                runCatching { fetchKitsuTrailerVideo(kitsuId) }.getOrDefault(emptyList())
            }.orEmpty()
        }

        val allRaw = buildList {
            addAll(jikanDef.await())
            addAll(tmdbDef.await())
            addAll(aniListDef.await())
            addAll(shikimoriDef.await())
            addAll(simklDef.await())
            addAll(kitsuDef.await())
            addAll(animeThemesDef.await())
            addAll(seedVideos)
        }

        var merged = mergeCrossSourceVideos(allRaw)

        val hasPlayableTrailer = merged.any {
            it.category == KitsugiVideoCategory.TRAILER && !it.videoUrl.isNullOrBlank()
        }
        if (!hasPlayableTrailer && context != null) {
            val fallbackTrailer = runCatching {
                DetailTrailerFallback.resolve(
                    context = context,
                    title = detail.title ?: title,
                    year = detail.year ?: year,
                    type = mediaType,
                    tmdbId = ids.tmdbId,
                    malId = ids.malId,
                    aniListId = ids.aniListId,
                    sourceName = source
                )
            }.getOrNull()
            if (!fallbackTrailer.isNullOrBlank()) {
                val ytId = extractCanonicalYouTubeId(fallbackTrailer)
                merged = mergeCrossSourceVideos(
                    listOf(
                        KitsugiVideoItem(
                            id = "fallback-trailer-${ytId ?: fallbackTrailer.hashCode()}",
                            title = if (isTurkish()) "Fragman" else "Trailer",
                            videoUrl = fallbackTrailer,
                            category = KitsugiVideoCategory.TRAILER,
                            source = canonicalVideoSource(source),
                            thumbnailUrl = ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                        )
                    ) + merged
                )
            }
        }

        merged
    }

    private suspend fun resolveCrossVideoIds(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        detail: KitsugiMediaDetail,
        hintTmdbId: Int?,
        hintMalId: Int?,
        hintSimklId: Int?
    ): CrossVideoIds {
        fun realMal(v: Int?): Int? = v?.takeIf { it in 1..99_999_999 }
        fun pos(v: Int?): Int? = v?.takeIf { it > 0 }

        val src = source.trim().lowercase(Locale.ROOT)
        var malId = realMal(hintMalId) ?: realMal(detail.realMalId)
        var aniListId: Int? = null
        var tmdbId = pos(hintTmdbId) ?: pos(detail.tmdbId)
        var kitsuId: Int? = null
        var simklId = pos(hintSimklId)
        var bangumiId: Int? = null
        var shikimoriId: Int? = null

        when (src) {
            "mal", "jikan", "myanimelist" -> {
                if (malId == null) malId = realMal(externalId)
            }
            "anilist" -> {
                aniListId = if (externalId >= 100_000_000) pos(externalId - 100_000_000) else pos(externalId)
            }
            "tmdb" -> {
                if (tmdbId == null) tmdbId = pos(externalId)
            }
            "kitsu" -> {
                kitsuId = KitsuIdNamespace.rawIdFromStable(externalId) ?: pos(externalId)
                if (malId == null) malId = realMal(KitsuIdNamespace.realMalIdOf(externalId))
            }
            "simkl" -> {
                if (simklId == null) simklId = pos(externalId)
            }
            "bangumi", "bgm" -> {
                bangumiId = KitsugiBangumiDetailClient.rawIdOf(externalId)
            }
            "shikimori" -> {
                shikimoriId = if (externalId >= 400_000_000) pos(externalId - 400_000_000) else pos(externalId)
                if (malId == null) malId = realMal(shikimoriId)
            }
        }

        if (bangumiId != null) {
            val cross = runCatching {
                withTimeoutOrNull(5_500L) {
                    KitsugiBangumiDetailClient.resolveCrossIds(bangumiId, mediaType)
                }
            }.getOrNull()
            if (cross != null) {
                if (aniListId == null) aniListId = pos(cross.aniListId)
                if (malId == null) malId = realMal(cross.malId)
                if (tmdbId == null) tmdbId = pos(cross.tmdbId)
                if (kitsuId == null) kitsuId = pos(cross.kitsuId)
            }
        }

        if (simklId != null && (malId == null || tmdbId == null || aniListId == null)) {
            val simklCross = runCatching {
                withTimeoutOrNull(4_500L) {
                    KitsugiSimklDetailClient.resolveSimklCrossIds(simklId, mediaType, tmdbId, malId)
                }
            }.getOrNull()
            if (simklCross != null) {
                if (malId == null) malId = realMal(simklCross.malId)
                if (tmdbId == null) tmdbId = pos(simklCross.tmdbId)
                if (aniListId == null) aniListId = pos(simklCross.aniListId)
                if (kitsuId == null) kitsuId = pos(simklCross.kitsuId)
            }
        }

        if (shikimoriId != null && malId == null) {
            malId = runCatching { KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId) }.getOrNull()?.let(::realMal)
        }

        if ((malId != null || aniListId != null || tmdbId != null || kitsuId != null) &&
            (malId == null || aniListId == null || tmdbId == null || kitsuId == null)
        ) {
            val resolved = runCatching {
                withTimeoutOrNull(4_500L) {
                    KitsugiIdResolver.resolveIds(
                        malId = malId,
                        aniListId = aniListId,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        kitsuId = kitsuId
                    )
                }
            }.getOrNull()
            if (resolved != null) {
                if (malId == null) malId = realMal(resolved.malId)
                if (aniListId == null) aniListId = pos(resolved.aniListId)
                if (tmdbId == null) tmdbId = pos(resolved.tmdbId)
                if (kitsuId == null) kitsuId = pos(resolved.kitsuId)
            }
        }

        return CrossVideoIds(
            malId = malId,
            aniListId = aniListId,
            tmdbId = tmdbId,
            kitsuId = kitsuId,
            simklId = simklId,
            bangumiId = bangumiId,
            shikimoriId = shikimoriId
        )
    }

    // ── Sağlayıcı toplayıcıları ──────────────────────────────────────────────

    private suspend fun fetchJikanVideos(malId: Int): List<KitsugiVideoItem> {
        val url = runCatching { URL("https://api.jikan.moe/v4/anime/$malId/videos") }.getOrNull()
            ?: return emptyList()
        val body = KitsugiApiBase.executeGetRequestResilient(url, maxRetries = 2) ?: return emptyList()
        return parseJikanVideosJson(body)
    }

    internal fun parseJikanVideosJson(body: String): List<KitsugiVideoItem> {
        val data = runCatching { JSONObject(body).optJSONObject("data") }.getOrNull() ?: return emptyList()
        val out = mutableListOf<KitsugiVideoItem>()

        val promos = data.optJSONArray("promo")
        if (promos != null) {
            for (i in 0 until promos.length()) {
                val p = promos.optJSONObject(i) ?: continue
                val title = p.optString("title", "").trim().takeIf { it.isNotEmpty() } ?: continue
                val trailer = p.optJSONObject("trailer") ?: continue
                val ytId = trailer.optNullableString("youtube_id")
                    ?: extractCanonicalYouTubeId(trailer.optNullableString("url"))
                    ?: extractCanonicalYouTubeId(trailer.optNullableString("embed_url"))
                val videoUrl = ytId?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: trailer.optNullableString("url")
                    ?: continue
                val images = trailer.optJSONObject("images")
                val thumb = images?.optNullableString("maximum_image_url")
                    ?: images?.optNullableString("large_image_url")
                    ?: images?.optNullableString("medium_image_url")
                    ?: ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                out += KitsugiVideoItem(
                    id = "mal-promo-$i-${ytId ?: i}",
                    title = title,
                    videoUrl = videoUrl,
                    category = KitsugiVideoCategory.TRAILER,
                    source = "mal",
                    thumbnailUrl = thumb
                )
            }
        }

        val mvs = data.optJSONArray("music_videos")
        if (mvs != null) {
            for (i in 0 until mvs.length()) {
                val mv = mvs.optJSONObject(i) ?: continue
                val rawTitle = mv.optString("title", "").trim()
                val meta = mv.optJSONObject("meta")
                val songTitle = meta?.optNullableString("title")
                val author = meta?.optNullableString("author")
                val video = mv.optJSONObject("video") ?: continue
                val ytId = video.optNullableString("youtube_id")
                    ?: extractCanonicalYouTubeId(video.optNullableString("url"))
                    ?: extractCanonicalYouTubeId(video.optNullableString("embed_url"))
                val videoUrl = ytId?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: video.optNullableString("url")
                    ?: continue
                val images = video.optJSONObject("images")
                val thumb = images?.optNullableString("maximum_image_url")
                    ?: images?.optNullableString("large_image_url")
                    ?: ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                val label = when {
                    !songTitle.isNullOrBlank() && !rawTitle.equals(songTitle, ignoreCase = true) && rawTitle.isNotBlank() ->
                        "\"$songTitle\" ($rawTitle)"
                    !songTitle.isNullOrBlank() -> "\"$songTitle\""
                    rawTitle.isNotBlank() -> rawTitle
                    else -> if (isTurkish()) "Müzik Videosu ${i + 1}" else "Music Video ${i + 1}"
                }
                val category = when {
                    rawTitle.startsWith("OP", ignoreCase = true) || rawTitle.contains("Opening", ignoreCase = true) ->
                        KitsugiVideoCategory.OPENING
                    rawTitle.startsWith("ED", ignoreCase = true) || rawTitle.contains("Ending", ignoreCase = true) ->
                        KitsugiVideoCategory.ENDING
                    else -> KitsugiVideoCategory.MUSIC_VIDEO
                }
                out += KitsugiVideoItem(
                    id = "mal-mv-$i-${ytId ?: i}",
                    title = label,
                    videoUrl = videoUrl,
                    category = category,
                    source = "mal",
                    thumbnailUrl = thumb,
                    subtitle = author
                )
            }
        }
        return out
    }

    private suspend fun fetchAnimeThemesVideos(ids: CrossVideoIds): List<KitsugiVideoItem> {
        val candidates = buildList {
            ids.malId?.let { add(it to "MyAnimeList") }
            ids.aniListId?.let { add(it to "AniList") }
            ids.kitsuId?.let { add(it to "Kitsu") }
        }
        for ((extId, site) in candidates) {
            val (ops, eds) = KitsugiAnimeThemesClient.fetchAnimeThemes(extId, site)
            if (ops.isNotEmpty() || eds.isNotEmpty()) {
                val out = mutableListOf<KitsugiVideoItem>()
                ops.forEachIndexed { idx, theme ->
                    out += KitsugiVideoItem(
                        id = "animethemes-op-$idx",
                        title = theme.label,
                        videoUrl = theme.videoUrl,
                        category = KitsugiVideoCategory.OPENING,
                        source = "animethemes"
                    )
                }
                eds.forEachIndexed { idx, theme ->
                    out += KitsugiVideoItem(
                        id = "animethemes-ed-$idx",
                        title = theme.label,
                        videoUrl = theme.videoUrl,
                        category = KitsugiVideoCategory.ENDING,
                        source = "animethemes"
                    )
                }
                return out
            }
        }
        return emptyList()
    }

    private fun fetchTmdbAllLanguageVideos(
        tmdbId: Int,
        mediaType: MediaType,
        detail: KitsugiMediaDetail,
        isPrimaryTmdb: Boolean,
        expectedTitles: List<String>
    ): List<KitsugiVideoItem> {
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return emptyList()
        val preferMovie = mediaType == MediaType.Movie ||
            detail.format.equals("MOVIE", ignoreCase = true) ||
            detail.rawFormat.equals("movie", ignoreCase = true)
        val typeOrder = if (preferMovie) listOf("movie", "tv") else listOf("tv", "movie")
        val activeLang = TmdbApiClient.getActiveLanguage()
        val preferTurkish = isTurkish()

        for (typePath in typeOrder) {
            val url = "https://api.themoviedb.org/3/$typePath/$tmdbId" +
                "?api_key=$apiKey&language=$activeLang" +
                "&append_to_response=videos,alternative_titles" +
                "&include_video_language=tr,en,ja,ko,zh,null"
            val body = runCatching {
                val req = Request.Builder().url(url).header("Accept", "application/json").build()
                com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull() ?: continue

            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            if (!isPrimaryTmdb) {
                val tmdbTitle = if (typePath == "movie") json.optNullableString("title") else json.optNullableString("name")
                val origTitle = if (typePath == "movie") {
                    json.optNullableString("original_title")
                } else {
                    json.optNullableString("original_name")
                }
                val alts = TmdbArtworkIdentity.extractAlternativeTitles(json)
                if (!TmdbArtworkIdentity.matches(expectedTitles, tmdbTitle, origTitle, alts)) {
                    continue
                }
            }

            val parsed = parseTmdbVideosArray(json.optJSONObject("videos")?.optJSONArray("results"), preferTurkish)
            if (parsed.isNotEmpty() || isPrimaryTmdb) return parsed
        }
        return emptyList()
    }

    internal fun parseTmdbVideosArray(
        results: JSONArray?,
        preferTurkish: Boolean = true
    ): List<KitsugiVideoItem> {
        if (results == null || results.length() == 0) return emptyList()
        data class RawTmdbVid(
            val index: Int,
            val item: KitsugiVideoItem,
            val official: Boolean,
            val langPriority: Int
        )
        val list = mutableListOf<RawTmdbVid>()
        for (i in 0 until results.length()) {
            val v = results.optJSONObject(i) ?: continue
            val site = v.optString("site", "")
            val key = v.optString("key", "").trim()
            if (key.isEmpty()) continue
            val videoUrl = when {
                site.equals("YouTube", ignoreCase = true) -> "https://www.youtube.com/watch?v=$key"
                site.equals("Vimeo", ignoreCase = true) -> "https://vimeo.com/$key"
                else -> continue
            }
            val typeStr = v.optString("type", "Trailer").trim()
            val name = v.optString("name", "").trim().ifBlank { typeStr }
            val isoLang = v.optString("iso_639_1", "").trim().lowercase(Locale.ROOT)
            val official = v.optBoolean("official", false)
            val category = when {
                typeStr.equals("Opening Credits", ignoreCase = true) -> KitsugiVideoCategory.OPENING
                typeStr.equals("Trailer", ignoreCase = true) ||
                    typeStr.equals("Teaser", ignoreCase = true) -> KitsugiVideoCategory.TRAILER
                else -> KitsugiVideoCategory.FEATURETTE
            }
            val langPriority = when {
                preferTurkish && isoLang == "tr" -> 0
                isoLang == "en" -> 1
                isoLang == "ja" -> 2
                isoLang.isEmpty() -> 3
                else -> 4
            }
            val langBadge = isoLang.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
            val typeBadge = localizeTmdbVideoType(typeStr, preferTurkish)
            val subtitle = listOfNotNull(typeBadge, langBadge).joinToString(" · ").takeIf { it.isNotEmpty() }
            list += RawTmdbVid(
                index = i,
                item = KitsugiVideoItem(
                    id = "tmdb-$i-$key",
                    title = name,
                    videoUrl = videoUrl,
                    category = category,
                    source = "tmdb",
                    thumbnailUrl = if (site.equals("YouTube", ignoreCase = true)) {
                        "https://img.youtube.com/vi/$key/hqdefault.jpg"
                    } else null,
                    subtitle = subtitle,
                    language = langBadge
                ),
                official = official,
                langPriority = langPriority
            )
        }
        return list.sortedWith(
            compareBy<RawTmdbVid> { it.langPriority }
                .thenByDescending { it.official }
                .thenBy { it.index }
        ).map { it.item }
    }

    private fun localizeTmdbVideoType(typeStr: String, preferTurkish: Boolean): String? {
        if (!preferTurkish) return typeStr.takeIf { it.isNotBlank() }
        return when (typeStr.lowercase(Locale.ROOT)) {
            "trailer" -> "Fragman"
            "teaser" -> "Tanıtım (Teaser)"
            "featurette" -> "Özel Yapım"
            "behind the scenes" -> "Kamera Arkası"
            "bloopers" -> "Çekim Hataları"
            "clip" -> "Klip"
            "opening credits" -> "Açılış Jeneriği"
            "recap" -> "Özet"
            else -> typeStr.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun fetchAniListTrailerVideo(aniListId: Int?, malId: Int?): List<KitsugiVideoItem> {
        val (query, vars) = if (aniListId != null && aniListId > 0) {
            "query(\$id: Int) { Media(id: \$id, type: ANIME) { trailer { id site thumbnail } } }" to
                JSONObject().put("id", aniListId)
        } else if (malId != null && malId > 0) {
            "query(\$idMal: Int) { Media(idMal: \$idMal, type: ANIME) { trailer { id site thumbnail } } }" to
                JSONObject().put("idMal", malId)
        } else {
            return emptyList()
        }
        val body = KitsugiApiBase.executeAniListQuery(query, vars) ?: return emptyList()
        val trailer = runCatching {
            JSONObject(body).optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("trailer")
        }.getOrNull() ?: return emptyList()
        val id = trailer.optNullableString("id") ?: return emptyList()
        val site = trailer.optString("site", "").lowercase(Locale.ROOT)
        val url = when {
            site.contains("youtube") -> "https://www.youtube.com/watch?v=$id"
            site.contains("dailymotion") -> "https://www.dailymotion.com/video/$id"
            else -> return emptyList()
        }
        val thumb = trailer.optNullableString("thumbnail")
            ?: if (site.contains("youtube")) "https://img.youtube.com/vi/$id/hqdefault.jpg" else null
        return listOf(
            KitsugiVideoItem(
                id = "anilist-trailer-$id",
                title = if (isTurkish()) "Resmi Fragman" else "Official Trailer",
                videoUrl = url,
                category = KitsugiVideoCategory.TRAILER,
                source = "anilist",
                thumbnailUrl = thumb
            )
        )
    }

    private suspend fun fetchShikimoriVideoItems(animeId: Int): List<KitsugiVideoItem> {
        val shikiList = KitsugiShikimoriClient.fetchShikimoriVideos(animeId)
        return shikiList.mapIndexed { idx, (kind, theme) ->
            val category = when (kind.lowercase(Locale.ROOT)) {
                "op" -> KitsugiVideoCategory.OPENING
                "ed" -> KitsugiVideoCategory.ENDING
                "op_ed_clip" -> KitsugiVideoCategory.MUSIC_VIDEO
                "clip", "other" -> KitsugiVideoCategory.FEATURETTE
                else -> KitsugiVideoCategory.TRAILER
            }
            val ytId = extractCanonicalYouTubeId(theme.videoUrl)
            KitsugiVideoItem(
                id = "shikimori-$idx-${ytId ?: idx}",
                title = theme.label,
                videoUrl = theme.videoUrl,
                category = category,
                source = "shikimori",
                thumbnailUrl = ytId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
            )
        }
    }

    private fun fetchKitsuTrailerVideo(kitsuId: Int): List<KitsugiVideoItem> {
        val url = runCatching { URL("${KitsuApiHost.API_BASE}/anime/$kitsuId") }.getOrNull() ?: return emptyList()
        val body = KitsugiApiBase.executeGetRequest(url) ?: return emptyList()
        val ytId = runCatching {
            JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")?.optNullableString("youtubeVideoId")
        }.getOrNull()?.let { extractCanonicalYouTubeId(it) ?: it.trim().takeIf(String::isNotEmpty) }
            ?: return emptyList()
        return listOf(
            KitsugiVideoItem(
                id = "kitsu-trailer-$ytId",
                title = if (isTurkish()) "Resmi Fragman" else "Official Trailer",
                videoUrl = "https://www.youtube.com/watch?v=$ytId",
                category = KitsugiVideoCategory.TRAILER,
                source = "kitsu",
                thumbnailUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg"
            )
        )
    }

    // ── Akıllı Tekilleştirme & Çapraz Kaynak Birleştirme ──────────────────────

    internal fun mergeCrossSourceVideos(items: List<KitsugiVideoItem>): List<KitsugiVideoItem> {
        if (items.isEmpty()) return emptyList()

        val byCanonicalUrl = LinkedHashMap<String, KitsugiVideoItem>()
        val unplayableThemes = mutableListOf<KitsugiVideoItem>()

        for (rawItem in items) {
            val cleanTitle = rawItem.title.trim()
            if (cleanTitle.isEmpty()) continue
            val cleanUrl = rawItem.videoUrl?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedItem = rawItem.copy(
                title = cleanTitle,
                videoUrl = cleanUrl,
                source = canonicalVideoSource(rawItem.source)
            )
            if (cleanUrl == null) {
                unplayableThemes += normalizedItem
                continue
            }
            val key = canonicalVideoKey(cleanUrl)
            val existing = byCanonicalUrl[key]
            byCanonicalUrl[key] = if (existing == null) {
                normalizedItem
            } else {
                mergeTwoVideoItems(existing, normalizedItem)
            }
        }

        val finalPlayable = byCanonicalUrl.values.toMutableList()
        val remainingUnplayable = mutableListOf<KitsugiVideoItem>()
        val seenUnplayableKeys = HashSet<String>()

        for (themeItem in unplayableThemes) {
            val songNorm = extractNormalizedSongKey(themeItem.title)
            val matchIndex = if (songNorm.length >= 3) {
                finalPlayable.indexOfFirst { playable ->
                    playable.category == themeItem.category &&
                        extractNormalizedSongKey(playable.title).let { pNorm ->
                            pNorm.length >= 3 && (pNorm == songNorm || pNorm.contains(songNorm) || songNorm.contains(pNorm))
                        }
                }
            } else -1

            if (matchIndex >= 0) {
                finalPlayable[matchIndex] = mergeTwoVideoItems(finalPlayable[matchIndex], themeItem)
            } else {
                val dedupKey = "${themeItem.category}:${songNorm.ifEmpty { themeItem.title.lowercase(Locale.ROOT) }}"
                if (seenUnplayableKeys.add(dedupKey)) {
                    remainingUnplayable += themeItem
                }
            }
        }

        return finalPlayable + remainingUnplayable
    }

    private fun mergeTwoVideoItems(
        primary: KitsugiVideoItem,
        secondary: KitsugiVideoItem
    ): KitsugiVideoItem {
        val combinedSources = (primary.allSources + secondary.allSources)
            .map { canonicalVideoSource(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        val mainSource = combinedSources.firstOrNull().orEmpty()
        val extra = combinedSources.drop(1)

        val chosenTitle = pickBetterVideoTitle(primary.title, secondary.title)
        val chosenCategory = when {
            primary.category == KitsugiVideoCategory.OPENING || secondary.category == KitsugiVideoCategory.OPENING ->
                if (primary.source == "animethemes" || secondary.source == "animethemes") KitsugiVideoCategory.OPENING
                else primary.category
            primary.category == KitsugiVideoCategory.ENDING || secondary.category == KitsugiVideoCategory.ENDING ->
                if (primary.source == "animethemes" || secondary.source == "animethemes") KitsugiVideoCategory.ENDING
                else primary.category
            primary.category == KitsugiVideoCategory.MUSIC_VIDEO || secondary.category == KitsugiVideoCategory.MUSIC_VIDEO ->
                KitsugiVideoCategory.MUSIC_VIDEO
            else -> primary.category
        }

        return primary.copy(
            title = chosenTitle,
            videoUrl = primary.videoUrl ?: secondary.videoUrl,
            category = chosenCategory,
            source = mainSource,
            extraSources = extra,
            thumbnailUrl = primary.thumbnailUrl ?: secondary.thumbnailUrl,
            subtitle = primary.subtitle ?: secondary.subtitle,
            language = primary.language ?: secondary.language
        )
    }

    private fun pickBetterVideoTitle(first: String, second: String): String {
        fun isGeneric(text: String): Boolean {
            val lower = text.trim().lowercase(Locale.ROOT)
            return lower == "fragman" ||
                lower == "resmi fragman" ||
                lower == "fragmanı izle" ||
                lower == "trailer" ||
                lower == "official trailer" ||
                lower == "watch trailer" ||
                lower == "video"
        }
        val firstGeneric = isGeneric(first)
        val secondGeneric = isGeneric(second)
        return when {
            firstGeneric && !secondGeneric -> second
            !firstGeneric && secondGeneric -> first
            else -> first
        }
    }

    private fun canonicalVideoKey(url: String): String {
        extractCanonicalYouTubeId(url)?.let { return "yt:$it" }
        val normalized = url.trim().substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)
        return "url:$normalized"
    }

    internal fun extractNormalizedSongKey(label: String): String {
        val quoted = Regex(""""([^"]{2,})"""").find(label)?.groupValues?.getOrNull(1)
        val raw = quoted ?: label
            .replace(Regex("""^\s*#?\d+\s*[:.\-–]\s*"""), "")
            .substringBefore(" by ")
            .substringBefore(" (")
        return raw.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "").trim()
    }

    internal fun canonicalVideoSource(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "jikan", "mal", "myanimelist" -> "mal"
        "anilist" -> "anilist"
        "tmdb" -> "tmdb"
        "animethemes", "animethemes.moe" -> "animethemes"
        "shikimori" -> "shikimori"
        "simkl" -> "simkl"
        "kitsu" -> "kitsu"
        "bangumi", "bgm" -> "bangumi"
        else -> raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    }

    private fun inferThemeSource(url: String?, fallbackSource: String): String {
        val clean = url?.lowercase(Locale.ROOT).orEmpty()
        return when {
            clean.contains("animethemes.moe") -> "animethemes"
            else -> fallbackSource
        }
    }

    private fun classifyThemeOrExtraLabel(
        label: String,
        defaultCategory: KitsugiVideoCategory
    ): KitsugiVideoCategory {
        val lower = label.lowercase(Locale.ROOT)
        return when {
            lower.contains("pv / tanıtım") ||
                lower.contains("karakter tanıtımı") ||
                lower.contains("ticari reklam") ||
                lower.endsWith("[fragman]") ||
                lower.endsWith("[teaser]") ||
                lower.contains("official trailer") ||
                lower.contains("main trailer") -> KitsugiVideoCategory.TRAILER
            lower.endsWith("[kamera arkası]") ||
                lower.endsWith("[özel yapım]") ||
                lower.endsWith("[klip]") -> KitsugiVideoCategory.FEATURETTE
            else -> defaultCategory
        }
    }

    private fun buildCacheKey(source: String, externalId: Int, mediaType: MediaType): String =
        "${canonicalVideoSource(source)}:$externalId:${mediaType.name.lowercase(Locale.ROOT)}"
}

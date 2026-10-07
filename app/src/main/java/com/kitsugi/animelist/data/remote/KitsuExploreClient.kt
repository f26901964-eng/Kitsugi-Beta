package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Kitsu.io REST API — AniList keşfet sayfası için ücretsiz fallback.
 *
 * Kullanım senaryosu:
 *  - AniList hesabı bağlı DEĞİL ve AniList tab seçili → Kitsu ile doldur
 *  - AniList API geçici olarak kapalı (AniListServiceDownException) → Kitsu'ya düş
 *
 * Kitsu API özellikleri:
 *  - Base URL: https://kitsu.io/api/edge
 *  - Format: JSON:API (application/vnd.api+json)
 *  - Auth: GET istekleri için gerekmiyor (public keşfet)
 *  - Rate Limit: Belirtilmemiş; yavaş-sabırlı istek yapılması önerilir
 *
 * ID Stratejisi:
 *  - Kitsu anime numeric ID'si kullanılır (örn. 7936)
 *  - stableId = kitsuId + 300_000_000 (MAL/AniList offset'leriyle çakışmaz)
 *  - source = "kitsu"
 *  - Detail sayfası: KitsugiDetailClient "kitsu" source'u KitsuClient üzerinden handle eder
 */
object KitsuExploreClient {
    private const val TAG = "KitsuExploreClient"
    private const val BASE = "https://kitsu.io/api/edge"
    private const val KITSU_ID_OFFSET = 300_000_000

    // ── Public API ───────────────────────────────────────────────────────────
    
    /** Kitsu API üzerinden anime/manga araması (AniList için fallback) */
    suspend fun searchAnime(query: String, mediaType: MediaType, limit: Int = 20): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "anime"
                MediaType.Manga -> "manga"
            }
            val safeLimit = limit.coerceIn(1, 20)
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$BASE/$endpoint?filter[text]=$encoded&page[limit]=$safeLimit"
            fetchList(url, mediaType)
        }

    suspend fun searchMediaAdvanced(
        mediaType: MediaType,
        query: String = "",
        page: Int = 1,
        limit: Int = 20,
        sort: String = "trending",
        subtypes: List<String>? = null,
        statuses: List<String>? = null,
        season: String? = null,
        seasonYear: Int? = null,
        categories: List<String>? = null,
        ageRating: String? = null,
        streamers: List<String>? = null,
        minRating: Int? = null
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val endpoint = when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "anime"
            MediaType.Manga -> "manga"
        }
        val safeLimit = limit.coerceIn(1, 20)
        val offset = (page - 1) * safeLimit
        val params = mutableListOf<String>()
        params.add("page[limit]=$safeLimit")
        params.add("page[offset]=$offset")

        if (query.isNotBlank()) params.add("filter[text]=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}")
        if (sort != "trending") params.add("sort=$sort")
        if (!subtypes.isNullOrEmpty()) params.add("filter[subtype]=${subtypes.joinToString(",")}")
        if (!statuses.isNullOrEmpty()) params.add("filter[status]=${statuses.joinToString(",")}")
        if (!season.isNullOrBlank()) params.add("filter[season]=${season.lowercase()}")
        if (seasonYear != null) params.add("filter[seasonYear]=$seasonYear")
        if (!categories.isNullOrEmpty()) params.add("filter[categories]=${categories.joinToString(",")}")
        if (!ageRating.isNullOrBlank()) params.add("filter[ageRating]=$ageRating")
        if (!streamers.isNullOrEmpty()) params.add("filter[streamers]=${streamers.joinToString(",")}")
        if (minRating != null && minRating > 0) params.add("filter[averageRating]=$minRating..100")

        val url = "$BASE/$endpoint?${params.joinToString("&")}"
        fetchList(url, mediaType)
    }

    suspend fun searchCharacters(query: String, page: Int = 1, limit: Int = 20): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 20)
        val offset = (page - 1) * safeLimit
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE/characters?filter[name]=$encoded&page[limit]=$safeLimit&page[offset]=$offset"
        val req = Request.Builder().url(url).header("Accept", "application/vnd.api+json").build()
        try {
            KitsugiHttpClient.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext emptyList()
                val root = JSONObject(res.body?.string().orEmpty())
                val data = root.optJSONArray("data") ?: return@withContext emptyList()
                val list = mutableListOf<JikanSearchResult>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optInt("id", 0)
                    if (id <= 0) continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val name = attrs.optString("canonicalName", attrs.optString("name", "Karakter"))
                    val imageObj = attrs.optJSONObject("image")
                    val imgUrl = imageObj?.optNullableString("original") ?: imageObj?.optNullableString("medium")
                    list.add(
                        JikanSearchResult(
                            malId = id,
                            title = name,
                            subtitle = "Karakter (Kitsu)",
                            type = MediaType.Anime,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = imgUrl,
                            year = null,
                            source = "kitsu"
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Trend animeler (Kitsu trending endpoint) */
    suspend fun trendingAnime(limit: Int = 20): List<JikanSearchResult> =
        fetchAnimeList("$BASE/trending/anime?limit=$limit")

    /** Sezonluk animeler (belirtilen mevsim ve yıl) */
    suspend fun seasonalAnime(season: String, year: Int, limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?filter[season]=${season.lowercase()}&filter[seasonYear]=$year&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** En yüksek puanlı animeler */
    suspend fun topRatedAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?sort=-averageRating&page[limit]=$limit&page[offset]=$offset")

    /** En popüler animeler (userCount'a göre sıralı) */
    suspend fun topAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yayında olan animeler */
    suspend fun airingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?filter[status]=current&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yakında yayınlanacak animeler */
    suspend fun upcomingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?filter[status]=upcoming&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yakın zamanda eklenen animeler */
    suspend fun newlyAddedAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?sort=-createdAt&page[limit]=$limit&page[offset]=$offset")

    /** Film formatındaki animeler */
    suspend fun movieAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("$BASE/anime?filter[subtype]=movie&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** En yüksek puanlı mangalar */
    suspend fun topRatedManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("$BASE/manga?sort=-averageRating&page[limit]=$limit&page[offset]=$offset")

    /** En popüler mangalar */
    suspend fun topManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("$BASE/manga?sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yayında olan mangalar */
    suspend fun publishingManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("$BASE/manga?filter[status]=current&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Trend mangalar (favoritesCount sırası) */
    suspend fun trendingManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("$BASE/manga?sort=-favoritesCount&page[limit]=$limit&page[offset]=$offset")

    /** Yakın zamanda eklenen mangalar */
    suspend fun newlyAddedManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("$BASE/manga?sort=-createdAt&page[limit]=$limit&page[offset]=$offset")

    // ── Internal HTTP ─────────────────────────────────────────────────────────

    private suspend fun fetchAnimeList(url: String): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            fetchList(url, MediaType.Anime)
        }

    private suspend fun fetchMangaList(url: String): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            fetchList(url, MediaType.Manga)
        }

    private fun fetchList(url: String, mediaType: MediaType): List<JikanSearchResult> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.api+json")
                .header("Content-Type", "application/vnd.api+json")
                .header("User-Agent", "Kitsugi/1.0 (Android)")
                .build()

            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val dataArr = root.optJSONArray("data") ?: return emptyList()

                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i) ?: continue
                    parseItem(item, mediaType)?.let { results.add(it) }
                }
                results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Kitsu list from $url: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseItem(data: JSONObject, mediaType: MediaType): JikanSearchResult? {
        return try {
            val kitsuNumericId = data.optString("id", "0").toIntOrNull() ?: return null
            val stableId = KITSU_ID_OFFSET + kitsuNumericId

            val attrs = data.optJSONObject("attributes") ?: return null

            val canonicalTitle = attrs.optString("canonicalTitle", "").takeIf { it.isNotBlank() }
            val titlesObj = attrs.optJSONObject("titles")
            val titleEn = titlesObj?.optString("en", "")?.takeIf { it.isNotBlank() }
            val titleEnJp = titlesObj?.optString("en_jp", "")?.takeIf { it.isNotBlank() }
            val titleJa = titlesObj?.optString("ja_jp", "")?.takeIf { it.isNotBlank() }
            val title = canonicalTitle ?: titleEn ?: titleEnJp ?: titleJa ?: return null

            val synopsis = attrs.optString("synopsis", "").takeIf { it.isNotBlank() }

            val startDate = attrs.optString("startDate", "")
            val year = startDate.take(4).toIntOrNull()?.takeIf { it > 1900 }

            val statusRaw = attrs.optString("status", "")
            val statusTr = when (statusRaw.lowercase()) {
                "current"    -> "Yayında"
                "finished"   -> "Tamamlandı"
                "upcoming"   -> "Yakında"
                "unreleased" -> "Yayınlanmadı"
                "tba"        -> "Bilinmiyor"
                else         -> statusRaw
            }

            val subtypeRaw = attrs.optString("subtype", "")
            val subtypeTr = when (subtypeRaw.lowercase()) {
                "tv"      -> "TV"
                "movie"   -> "Film"
                "ova"     -> "OVA"
                "ona"     -> "ONA"
                "special" -> "Özel"
                "music"   -> "Müzik"
                "manga"   -> "Manga"
                "manhwa"  -> "Manhwa"
                "manhua"  -> "Manhua"
                "novel"   -> "Novel"
                "oneshot" -> "One-Shot"
                else      -> subtypeRaw
            }

            val avgRating = attrs.optString("averageRating", "0").toDoubleOrNull() ?: 0.0
            val score = if (avgRating > 0) (avgRating / 10.0).toInt().coerceIn(1, 10) else null

            val userCount = attrs.optInt("userCount", 0).takeIf { it > 0 }
            val favCount  = attrs.optInt("favoritesCount", 0).takeIf { it > 0 }

            val posterObj = attrs.optJSONObject("posterImage")
            val imageUrl  = posterObj?.optString("medium")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("large")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("original")?.takeIf { it.isNotBlank() }

            val coverObj    = attrs.optJSONObject("coverImage")
            val backdropUrl = coverObj?.optString("large")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("original")?.takeIf { it.isNotBlank() }

            val total = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow ->
                    attrs.optInt("episodeCount", 0).takeIf { it > 0 }
                MediaType.Manga ->
                    attrs.optInt("chapterCount", 0).takeIf { it > 0 }
            }

            val ageRating = attrs.optString("ageRating", "")
            val isAdult   = ageRating.equals("R18", ignoreCase = true)

            // Altyazı: tür + yıl + durum
            val subtitleParts = buildList {
                if (subtypeTr.isNotBlank()) add(subtypeTr)
                if (year != null) add(year.toString())
                if (statusTr.isNotBlank()) add(statusTr)
            }
            val subtitle = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ")
                           else "Kitsu ile eklenen anime"

            JikanSearchResult(
                malId         = stableId,
                title         = title,
                subtitle      = subtitle,
                type          = mediaType,
                total         = total,
                score         = score,
                isAdult       = isAdult,
                imageUrl      = imageUrl,
                year          = year,
                source        = "kitsu",
                realMalId     = null,       // Kitsu ek API çağrısı olmadan MAL ID'sini bilmiyor
                titleEnglish  = titleEn,
                titleJapanese = titleJa,
                backdropUrl   = backdropUrl,
                members       = userCount,
                favorites     = favCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Kitsu item: ${e.message}", e)
            null
        }
    }

    // ── Detail yardımcısı (KitsugiDetailClient tarafından çağrılır) ───────────

    /**
     * stableId veya doğrudan Kitsu numeric ID'den detay çeker.
     * stableId = kitsuId + 300_000_000 veya doğrudan kitsuId
     */
    suspend fun fetchDetailByStableId(stableId: Int, mediaType: MediaType): KitsugiMediaDetail? {
        val kitsuNumericId = if (stableId >= KITSU_ID_OFFSET) {
            stableId - KITSU_ID_OFFSET
        } else {
            stableId
        }
        if (kitsuNumericId <= 0) return null
        return when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow ->
                KitsuClient.fetchAnimeDetail(kitsuNumericId.toString())
            MediaType.Manga ->
                fetchMangaDetail(kitsuNumericId.toString())
        }
    }

    private suspend fun fetchMangaDetail(kitsuId: String): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE/manga/$kitsuId"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.api+json")
                    .header("Content-Type", "application/vnd.api+json")
                    .header("User-Agent", "Kitsugi/1.0 (Android)")
                    .build()

                KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val root = JSONObject(body)
                    val dataObj = root.optJSONObject("data") ?: return@withContext null
                    parseMangaDetail(dataObj)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Kitsu manga detail: ${e.message}", e)
                null
            }
        }

    private fun parseMangaDetail(data: JSONObject): KitsugiMediaDetail? {
        return try {
            val attrs = data.optJSONObject("attributes") ?: return null
            val canonicalTitle = attrs.optString("canonicalTitle", "")
            val titlesObj = attrs.optJSONObject("titles")
            val titleEn = titlesObj?.optString("en", "")?.takeIf { it.isNotBlank() }
            val titleJa = titlesObj?.optString("ja_jp", "")?.takeIf { it.isNotBlank() }
            val title = canonicalTitle.takeIf { it.isNotBlank() } ?: titleEn ?: titleJa ?: "Başlıksız"

            val synopsis = attrs.optString("synopsis", "").takeIf { it.isNotBlank() }
            val startDate = attrs.optString("startDate", "")
            val endDate = attrs.optString("endDate", "")
            val year = startDate.take(4).toIntOrNull()

            val chapterCount = attrs.optInt("chapterCount", 0).takeIf { it > 0 }
            val avgRating = attrs.optString("averageRating", "0").toDoubleOrNull() ?: 0.0
            val score = if (avgRating > 0) (avgRating / 10.0).toInt().coerceIn(1, 10) else null

            val posterObj = attrs.optJSONObject("posterImage")
            val imageUrl = posterObj?.optString("medium")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("original")?.takeIf { it.isNotBlank() }

            val kitsuId = data.optString("id", "")
            val links = mutableListOf<KitsugiExternalLink>()
            if (kitsuId.isNotBlank()) links.add(KitsugiExternalLink("Kitsu", "https://kitsu.io/manga/$kitsuId", "EN"))

            val statusRaw = attrs.optString("status", "")
            val statusTr = when (statusRaw.lowercase()) {
                "current"    -> "Devam Ediyor"
                "finished"   -> "Tamamlandı"
                "upcoming"   -> "Yakında"
                else         -> statusRaw
            }

            KitsugiMediaDetail(
                synopsis      = synopsis,
                title         = title,
                titleEnglish  = titleEn,
                titleJapanese = titleJa,
                imageUrl      = imageUrl,
                score         = score,
                year          = year,
                total         = chapterCount,
                startDate     = startDate.takeIf { it.isNotBlank() },
                endDate       = endDate.takeIf { it.isNotBlank() },
                status        = statusTr,
                externalLinks = links
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Kitsu manga detail: ${e.message}", e)
            null
        }
    }

    /** Kitsu ID offset sabiti — dışarıdan erişilebilir */
    const val ID_OFFSET = KITSU_ID_OFFSET
}

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
 *  - Base URL: [KitsuApiHost] üzerinden çözülür (kitsu.app kanonik, kitsu.io yedek)
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
    private const val KITSU_ID_OFFSET = 300_000_000

    /**
     * Sayfa başına kayıt tavanı. Kitsu `page[limit]` için 20'den büyük değeri
     * `400 Invalid page value → Limit exceeds maximum page size of 20` ile reddeder.
     */
    private const val KITSU_MAX_PAGE_LIMIT = 20

    /**
     * `page[offset]` desteklemeyen host'ta (kitsu.io uyumluluk katmanı) 2. sayfanın 1.
     * sayfayla birebir aynı olduğunu anlayabilmek için sorgu başına ilk kaydın kimliği.
     */
    private val pageAnchors = com.kitsugi.animelist.core.memory.BoundedCache<String, String>(
        name = "kitsu_page_anchor", maxEntries = 64
    )

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
            fetchPaged("/$endpoint?filter[text]=$encoded&page[limit]=$safeLimit", mediaType)
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

        fetchPaged("/$endpoint?${params.joinToString("&")}", mediaType)
    }

    suspend fun searchCharacters(query: String, page: Int = 1, limit: Int = 20): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 20)
        val offset = (page - 1) * safeLimit
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = KitsuApiHost.url("/characters?filter[name]=$encoded&page[limit]=$safeLimit&page[offset]=$offset")
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

    /**
     * Trend animeler (Kitsu trending endpoint).
     *
     * `page[limit]`/`page[offset]` JSON:API sayfalamasını kullanır; böylece "Tümünü Gör"
     * sayfası 20 kayıtta kilitli kalmaz.
     */
    suspend fun trendingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList(
            "/trending/anime?page[limit]=${limit.coerceIn(1, KITSU_MAX_PAGE_LIMIT)}" +
                "&page[offset]=${offset.coerceAtLeast(0)}"
        )

    /** Sezonluk animeler (belirtilen mevsim ve yıl) */
    suspend fun seasonalAnime(season: String, year: Int, limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?filter[season]=${season.lowercase()}&filter[seasonYear]=$year&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** En yüksek puanlı animeler */
    suspend fun topRatedAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?sort=-averageRating&page[limit]=$limit&page[offset]=$offset")

    /** En popüler animeler (userCount'a göre sıralı) */
    suspend fun topAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yayında olan animeler */
    suspend fun airingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?filter[status]=current&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yakında yayınlanacak animeler */
    suspend fun upcomingAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?filter[status]=upcoming&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yakın zamanda eklenen animeler */
    suspend fun newlyAddedAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?sort=-createdAt&page[limit]=$limit&page[offset]=$offset")

    /** Film formatındaki animeler */
    suspend fun movieAnime(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchAnimeList("/anime?filter[subtype]=movie&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** En yüksek puanlı mangalar */
    suspend fun topRatedManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("/manga?sort=-averageRating&page[limit]=$limit&page[offset]=$offset")

    /** En popüler mangalar */
    suspend fun topManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("/manga?sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Yayında olan mangalar */
    suspend fun publishingManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("/manga?filter[status]=current&sort=-userCount&page[limit]=$limit&page[offset]=$offset")

    /** Trend mangalar (favoritesCount sırası) */
    suspend fun trendingManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("/manga?sort=-favoritesCount&page[limit]=$limit&page[offset]=$offset")

    /** Yakın zamanda eklenen mangalar */
    suspend fun newlyAddedManga(limit: Int = 20, offset: Int = 0): List<JikanSearchResult> =
        fetchMangaList("/manga?sort=-createdAt&page[limit]=$limit&page[offset]=$offset")

    // ── Internal HTTP ─────────────────────────────────────────────────────────

    private suspend fun fetchAnimeList(pathAndQuery: String): List<JikanSearchResult> =
        fetchPaged(pathAndQuery, MediaType.Anime)

    private suspend fun fetchMangaList(pathAndQuery: String): List<JikanSearchResult> =
        fetchPaged(pathAndQuery, MediaType.Manga)

    /**
     * Kitsu liste ucu çağrısı: host seçimi, yedek host denemesi ve sayfa bekçisi burada.
     *
     * Sayfa bekçisi: `page[offset]` bazı adreslerde yok sayılıyor ve 2. sayfa 1. sayfanın
     * aynısını dönüyordu. Arayüz kopya kayıtları elediği için kullanıcı "devamı hiç
     * gelmiyor" diye görüyordu. Artık ilk sayfanın baş kaydıyla aynı kayıt dönerse önce
     * diğer adres denenir; o da aynısını verirse listenin bittiği kabul edilir.
     */
    private suspend fun fetchPaged(
        pathAndQuery: String,
        mediaType: MediaType
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val offset = pagingOffset(pathAndQuery)
        val anchorKey = pagingAnchorKey(pathAndQuery)
        var primaryFailed = false
        for (host in KitsuApiHost.candidates()) {
            val items = runCatching { fetchFromHost(KitsuApiHost.url(pathAndQuery, host), mediaType) }
                .getOrElse { err ->
                    Log.w(TAG, "Kitsu isteği başarısız ($host): ${err.message}")
                    null
                }
            if (items == null) {
                primaryFailed = true
                continue
            }
            val head = items.firstOrNull()?.let { "${it.source}:${it.malId}:${it.title}" }
            if (offset > 0) {
                val anchor = pageAnchors[anchorKey]
                if (anchor != null && head == anchor) {
                    Log.w(TAG, "Kitsu sayfa bekçisi: offset=$offset aynı kaydı döndürdü ($host atlandı)")
                    continue
                }
            } else if (head != null) {
                pageAnchors[anchorKey] = head
            }
            if (host != KitsuApiHost.base && primaryFailed) KitsuApiHost.degrade()
            return@withContext items
        }
        if (primaryFailed) KitsuApiHost.degrade()
        emptyList()
    }

    /** Sorgudaki `page[offset]` değeri (yoksa 0). */
    private fun pagingOffset(pathAndQuery: String): Int =
        Regex("page\\[offset\\]=(\\d+)").find(pathAndQuery)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** `page[offset]` hariç sorgunun tamamı — aynı listenin sayfaları aynı anahtarı paylaşır. */
    private fun pagingAnchorKey(pathAndQuery: String): String =
        pathAndQuery.replace(Regex("&?page\\[offset\\]=\\d+"), "")

    /** Tek bir adresten liste çeker; HTTP hatası/bozuk gövde için `null` döner. */
    private fun fetchFromHost(url: String, mediaType: MediaType): List<JikanSearchResult>? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json")
            .header("User-Agent", "Kitsugi/1.0 (Android)")
            .build()

        return try {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    return null
                }
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                if (root.has("errors")) {
                    Log.w(TAG, "Kitsu hata yanıtı for $url: ${root.optJSONArray("errors")?.optJSONObject(0)?.optString("detail")}")
                    return null
                }
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
            null
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

            val isAdult   = KitsuAdultFlags.isAdult(attrs)

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
        // stableId her zaman offset'li Kitsu aralığında (300M..399M) olmalı. Aralığın altındaki
        // değerler MAL/AniList kimlik alanlarıyla çakışır; "ham Kitsu ID" diye yorumlanırsa
        // alakasız bir yapımın verisi döner (Listem → Kitsu ayrıntı sayfası hatasının kökü).
        val kitsuNumericId = KitsuIdNamespace.rawIdFromStable(stableId)
        if (kitsuNumericId == null || kitsuNumericId <= 0) {
            Log.w(TAG, "fetchDetailByStableId: $stableId Kitsu kimlik aralığında değil, reddedildi")
            return null
        }
        return when (mediaType) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow ->
                KitsuClient.fetchAnimeDetail(kitsuNumericId.toString())
            MediaType.Manga ->
                fetchMangaDetail(kitsuNumericId.toString())
        }
    }

    private suspend fun fetchMangaDetail(kitsuId: String): KitsugiMediaDetail? {
        // R18/+18 Kitsu kayıtları anonim isteklere gizlenir (resmî API kuralı). Kayıt
        // kullanıcının listesinden geldiği için oturum varsa jetonla çekiyoruz; jeton
        // yoksa/geçersizse eskisi gibi anonim istek yapılır (bkz. KitsuClient.executeGet).
        val authToken = runCatching { KitsuClient.authTokenOrNull() }.getOrNull()
        return fetchMangaDetailOnce(kitsuId, authToken)
            ?: if (authToken != null) fetchMangaDetailOnce(kitsuId, null) else null
    }

    private suspend fun fetchMangaDetailOnce(kitsuId: String, authToken: String?): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            try {
                val url = KitsuApiHost.url("/manga/$kitsuId")
                val builder = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.api+json")
                    .header("Content-Type", "application/vnd.api+json")
                    .header("User-Agent", "Kitsugi/1.0 (Android)")
                if (authToken != null) builder.header("Authorization", "Bearer $authToken")
                val request = builder.build()

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
            val posterOriginal = posterObj?.optString("original")?.takeIf { it.isNotBlank() }

            val coverObj = attrs.optJSONObject("coverImage")
            val coverOriginal = coverObj?.optString("original")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("large")?.takeIf { it.isNotBlank() }
            val kitsuPictures = listOfNotNull(posterOriginal, coverOriginal).distinct()

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
                bannerImage   = coverOriginal,
                type          = MediaType.Manga,
                score         = score,
                year          = year,
                total         = chapterCount,
                startDate     = startDate.takeIf { it.isNotBlank() },
                endDate       = endDate.takeIf { it.isNotBlank() },
                status        = statusTr,
                isAdult       = KitsuAdultFlags.isAdult(attrs),
                externalLinks = links,
                pictures      = kitsuPictures
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Kitsu manga detail: ${e.message}", e)
            null
        }
    }

    /** Kitsu ID offset sabiti — dışarıdan erişilebilir */
    const val ID_OFFSET = KITSU_ID_OFFSET
}

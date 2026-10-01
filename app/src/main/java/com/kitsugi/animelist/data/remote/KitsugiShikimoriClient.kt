package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.*

object KitsugiShikimoriClient {
    private const val TAG = "KitsugiShikimoriClient"
    private const val BASE_URL = "https://shikimori.one/api"

    // ─── Arama fonksiyonları ───────────────────────────────────────────────

    /**
     * Shikimori REST API üzerinden anime araması.
     * MAL/Jikan sunucusu hata verdiğinde fallback olarak kullanılır.
     */
    suspend fun searchAnime(query: String, limit: Int = 20): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL("$BASE_URL/animes?search=$encoded&limit=$limit&order=popularity")
                Log.d(TAG, "Shikimori searchAnime: $url")
                val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
                val array = JSONArray(response)
                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id")
                    if (id <= 0) continue
                    val romajiTitle = item.optString("name", "").trim()
                    val russianTitle = item.optString("russian", "").trim()
                    val title = romajiTitle.ifBlank { russianTitle }
                    val relativeImg = item.optJSONObject("image")?.optString("original")
                    val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                    val kind = item.optString("kind", "tv")
                    val score = item.optString("score", "0").toDoubleOrNull()?.toInt()?.coerceIn(0, 10)
                    val year = item.optString("aired_on", "").take(4).toIntOrNull()
                    val episodes = item.optInt("episodes").takeIf { it > 0 }
                    val mediaType = when (kind) {
                        "movie" -> com.kitsugi.animelist.model.MediaType.Movie
                        "manga", "novel", "manhwa", "manhua", "one_shot", "doujin" -> com.kitsugi.animelist.model.MediaType.Manga
                        else -> com.kitsugi.animelist.model.MediaType.Anime
                    }
                    results.add(
                        JikanSearchResult(
                            malId = id,
                            title = title,
                            subtitle = kind.uppercase(),
                            type = mediaType,
                            total = episodes,
                            score = score,
                            isAdult = false,
                            imageUrl = imageUrl,
                            year = year,
                            source = "shikimori",
                            titleEnglish = romajiTitle.ifBlank { null }
                        )
                    )
                }
                results
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori searchAnime exception: ${err.message}", err)
                emptyList()
            }
        }

    /**
     * Shikimori REST API üzerinden manga araması.
     */
    suspend fun searchManga(query: String, limit: Int = 20): List<JikanSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL("$BASE_URL/mangas?search=$encoded&limit=$limit&order=popularity")
                Log.d(TAG, "Shikimori searchManga: $url")
                val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
                val array = JSONArray(response)
                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id")
                    if (id <= 0) continue
                    val romajiTitle = item.optString("name", "").trim()
                    val russianTitle = item.optString("russian", "").trim()
                    val title = romajiTitle.ifBlank { russianTitle }
                    val relativeImg = item.optJSONObject("image")?.optString("original")
                    val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                    val kind = item.optString("kind", "manga")
                    val score = item.optString("score", "0").toDoubleOrNull()?.toInt()?.coerceIn(0, 10)
                    val year = item.optString("aired_on", "").take(4).toIntOrNull()
                    val chapters = item.optInt("chapters").takeIf { it > 0 }
                    results.add(
                        JikanSearchResult(
                            malId = id,
                            title = title,
                            subtitle = kind.uppercase(),
                            type = com.kitsugi.animelist.model.MediaType.Manga,
                            total = chapters,
                            score = score,
                            isAdult = false,
                            imageUrl = imageUrl,
                            year = year,
                            source = "shikimori",
                            titleEnglish = romajiTitle.ifBlank { null }
                        )
                    )
                }
                results
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori searchManga exception: ${err.message}", err)
                emptyList()
            }
        }

    suspend fun searchMediaAdvanced(
        mediaType: com.kitsugi.animelist.model.MediaType,
        query: String = "",
        page: Int = 1,
        limit: Int = 24,
        order: String = "popularity",
        kinds: List<String>? = null,
        statuses: List<String>? = null,
        season: String? = null,
        score: Int? = null,
        duration: String? = null,
        rating: String? = null,
        genres: List<Int>? = null,
        excludedGenres: List<Int>? = null,
        studioId: Int? = null,
        publisherId: Int? = null,
        censored: Boolean = true
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = when (mediaType) {
                com.kitsugi.animelist.model.MediaType.Manga -> "mangas"
                else -> "animes"
            }
            val params = mutableListOf<String>()
            params.add("limit=$limit")
            params.add("page=$page")
            params.add("order=$order")
            params.add("censored=$censored")

            if (query.isNotBlank()) params.add("search=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}")
            if (!kinds.isNullOrEmpty()) params.add("kind=${kinds.joinToString(",")}")
            if (!statuses.isNullOrEmpty()) params.add("status=${statuses.joinToString(",")}")
            if (!season.isNullOrBlank()) params.add("season=$season")
            if (score != null && score > 0) params.add("score=$score")
            if (!duration.isNullOrBlank()) params.add("duration=$duration")
            if (!rating.isNullOrBlank()) params.add("rating=$rating")
            if (studioId != null && studioId > 0) params.add("studio=$studioId")
            if (publisherId != null && publisherId > 0) params.add("publisher=$publisherId")

            val genreParts = mutableListOf<String>()
            genres?.forEach { genreParts.add("$it") }
            excludedGenres?.forEach { genreParts.add("!$it") }
            if (genreParts.isNotEmpty()) {
                params.add("genre=${genreParts.joinToString(",")}")
            }

            val url = URL("$BASE_URL/$endpoint?${params.joinToString("&")}")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val results = mutableListOf<JikanSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                val romajiTitle = item.optString("name", "").trim()
                val russianTitle = item.optString("russian", "").trim()
                val title = romajiTitle.ifBlank { russianTitle }
                val relativeImg = item.optJSONObject("image")?.optString("original")
                val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                val kind = item.optString("kind", "tv")
                val sc = item.optString("score", "0").toDoubleOrNull()?.toInt()?.coerceIn(0, 10)
                val yr = item.optString("aired_on", "").take(4).toIntOrNull()
                val episodes = item.optInt("episodes").takeIf { it > 0 }
                val epOrCh = if (mediaType == com.kitsugi.animelist.model.MediaType.Manga) item.optInt("chapters").takeIf { it > 0 } else episodes
                results.add(
                    JikanSearchResult(
                        malId = id,
                        title = title,
                        subtitle = kind.uppercase(),
                        type = mediaType,
                        total = epOrCh,
                        score = sc,
                        isAdult = !censored,
                        imageUrl = imageUrl,
                        year = yr,
                        source = "shikimori",
                        titleEnglish = romajiTitle.ifBlank { null }
                    )
                )
            }
            results
        }.getOrElse { emptyList() }
    }

    suspend fun searchCharacters(query: String, page: Int = 1, limit: Int = 24): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("$BASE_URL/characters/search?search=$encoded&page=$page&limit=$limit")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val results = mutableListOf<JikanSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                val name = item.optString("name", "").ifBlank { item.optString("russian", "Karakter") }
                val relativeImg = item.optJSONObject("image")?.optString("original")
                val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                results.add(
                    JikanSearchResult(
                        malId = id,
                        title = name,
                        subtitle = "Karakter (Shikimori)",
                        type = com.kitsugi.animelist.model.MediaType.Anime,
                        total = null,
                        score = null,
                        isAdult = false,
                        imageUrl = imageUrl,
                        year = null,
                        source = "shikimori"
                    )
                )
            }
            results
        }.getOrElse { emptyList() }
    }

    suspend fun searchPeople(query: String, page: Int = 1, limit: Int = 24, kind: String? = null): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val kindParam = if (!kind.isNullOrBlank()) "&kind=$kind" else ""
            val url = URL("$BASE_URL/people/search?search=$encoded&page=$page&limit=$limit$kindParam")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val results = mutableListOf<JikanSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                val name = item.optString("name", "").ifBlank { item.optString("russian", "Kişi") }
                val relativeImg = item.optJSONObject("image")?.optString("original")
                val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                val role = when (kind) {
                    "seyu" -> "Seiyuu / Seslendirmen"
                    "mangaka" -> "Mangaka / Yazar"
                    "producer" -> "Yapımcı / Yönetmen"
                    else -> "Kişi / Ekip"
                }
                results.add(
                    JikanSearchResult(
                        malId = id,
                        title = name,
                        subtitle = "$role (Shikimori)",
                        type = com.kitsugi.animelist.model.MediaType.Anime,
                        total = null,
                        score = null,
                        isAdult = false,
                        imageUrl = imageUrl,
                        year = null,
                        source = "shikimori"
                    )
                )
            }
            results
        }.getOrElse { emptyList() }
    }

    /**
     * Shikimori REST API üzerinden bir animenin ekran görüntülerini (screenshots / backdrops) çeker.
     * Her görsel orijinal tam çözünürlüktedir.
     */
    suspend fun fetchScreenshots(animeId: Int): List<GalleryItem> = withContext(Dispatchers.IO) {
        if (animeId <= 0) return@withContext emptyList()
        val cached = DetailCache.getShikimoriGallery(animeId)
        if (cached != null) return@withContext cached

        val url = URL("$BASE_URL/animes/$animeId/screenshots")
        val response = KitsugiApiBase.executeGetRequest(url) ?: return@withContext emptyList()
        runCatching {
            val array = JSONArray(response)
            val list = mutableListOf<GalleryItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val relOriginal = obj.optString("original", "").trim()
                if (relOriginal.isBlank()) continue
                val fullUrl = if (relOriginal.startsWith("http")) relOriginal else "https://shikimori.one$relOriginal"
                list.add(
                    GalleryItem(
                        url = fullUrl,
                        source = "Shikimori",
                        category = GalleryCategory.BACKDROP,
                        description = "Shikimori Ekran Görüntüsü",
                        language = "ru"
                    )
                )
            }
            if (list.isNotEmpty()) {
                DetailCache.putShikimoriGallery(animeId, list)
            }
            list
        }.getOrElse {
            Log.e(TAG, "Shikimori fetchScreenshots exception: ${it.message}", it)
            emptyList()
        }
    }

    /**
     * Shikimori REST API üzerinden anime veya manga detaylarını çeker.
     * Yaş sınırını ("rating") doğru şekilde ayrıştırır ve yalnızca "rx" (Hentai)
     * olanları +18 (isAdult) olarak işaretler; "r" (17+ şiddet/aksiyon) içerikleri
     * asla yetişkin olarak blurlamaz.
     */
    suspend fun fetchDetail(externalId: Int, mediaType: MediaType): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            if (externalId <= 0) return@withContext null
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "animes"
                MediaType.Manga -> "mangas"
            }
            val url = URL("$BASE_URL/$endpoint/$externalId")
            Log.d(TAG, "Shikimori fetchDetail: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequest(url) ?: return@runWithRateLimit null
                    val data = JSONObject(response)

                    val russianTitle = data.optString("russian").takeIf { it.isNotBlank() }
                    val romajiTitle = data.optString("name").takeIf { it.isNotBlank() }
                    val engTitle = data.optJSONArray("english")?.optString(0)?.takeIf { it.isNotBlank() }
                    val japTitle = data.optJSONArray("japanese")?.optString(0)?.takeIf { it.isNotBlank() }
                    val mainTitle = romajiTitle ?: engTitle ?: russianTitle ?: "Bilinmeyen"

                    val relativeImg = data.optJSONObject("image")?.optString("original")
                    val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                    val rawScore = data.optString("score", "0").toDoubleOrNull()
                    val score = rawScore?.toInt()?.coerceIn(0, 10)
                    val meanScore = rawScore?.let { (it * 10).toInt() }

                    val year = data.optString("aired_on", "").take(4).toIntOrNull()
                    val total = if (mediaType == MediaType.Manga) {
                        data.optInt("chapters").takeIf { it > 0 }
                    } else {
                        data.optInt("episodes").takeIf { it > 0 }
                    }

                    val ratingRaw = data.optString("rating", "").lowercase()
                    val statusRaw = data.optString("status", "")
                    val statusStr = when (statusRaw) {
                        "released" -> "Finished Airing"
                        "ongoing"  -> "Currently Airing"
                        "anons"    -> "Not yet aired"
                        else       -> statusRaw
                    }.toTurkishStatus()

                    val genresList = mutableListOf<String>()
                    val genresArr = data.optJSONArray("genres")
                    if (genresArr != null) {
                        for (i in 0 until genresArr.length()) {
                            val gObj = genresArr.optJSONObject(i) ?: continue
                            val gName = gObj.optString("name")
                            if (gName.isNotBlank()) genresList.add(gName)
                        }
                    }

                    // SADECE "rx" (Hentai) veya hentai türü yetişkin (+18) olarak kabul edilir!
                    // "r" (17+ şiddet/aksiyon, PG-13 vb.) kesinlikle +18 DEĞİLDİR!
                    val isAdult = ratingRaw == "rx" ||
                        ratingRaw.contains("hentai") ||
                        genresList.any { it.contains("hentai", ignoreCase = true) }

                    val rating = when (ratingRaw) {
                        "g"      -> "G - All Ages"
                        "pg"     -> "PG - Children"
                        "pg_13"  -> "PG-13 - Teens 13 or older"
                        "r"      -> "R - 17+ (violence & profanity)"
                        "r_plus" -> "R+ - Mild Nudity"
                        "rx"     -> "Rx - Hentai"
                        else     -> null
                    }?.toTurkishRating()

                    val studiosList = mutableListOf<KitsugiStudio>()
                    val studiosArr = data.optJSONArray("studios")
                    if (studiosArr != null) {
                        for (i in 0 until studiosArr.length()) {
                            val sObj = studiosArr.optJSONObject(i) ?: continue
                            val sId = sObj.optInt("id")
                            val sName = sObj.optString("name")
                            if (sId > 0 && sName.isNotBlank()) {
                                studiosList.add(KitsugiStudio(id = sId, name = sName, isMain = true))
                            }
                        }
                    }

                    val realMalId = data.optInt("myanimelist_id").takeIf { it > 0 } ?: externalId

                    // Shikimori ekstra videolar (PV, Teaser, Karakter tanıtımları, OP, ED)
                    val shikiVideos = if (mediaType != MediaType.Manga) {
                        fetchShikimoriVideos(externalId)
                    } else emptyList()

                    // Shikimori harici ve yayın linkleri
                    val shikiLinks = if (mediaType != MediaType.Manga) {
                        fetchShikimoriExternalLinks(externalId)
                    } else emptyList()

                    // AnimeThemes entegrasyonu (MyAnimeList ID üzerinden)
                    val (themeOps, themeEds) = if (mediaType != MediaType.Manga && realMalId > 0) {
                        try {
                            KitsugiAnimeThemesClient.fetchAnimeThemes(realMalId, "MyAnimeList")
                        } catch (e: Exception) {
                            Pair(emptyList(), emptyList())
                        }
                    } else Pair(emptyList(), emptyList())

                    // Fragman (PV)
                    var trailerUrl: String? = null
                    val videosArr = data.optJSONArray("videos")
                    if (videosArr != null) {
                        for (i in 0 until videosArr.length()) {
                            val vObj = videosArr.optJSONObject(i) ?: continue
                            val vUrl = vObj.optString("url")
                            if (vUrl.isNotBlank() && (vUrl.contains("youtube.com") || vUrl.contains("youtu.be"))) {
                                trailerUrl = vUrl
                                break
                            }
                        }
                    }
                    if (trailerUrl.isNullOrBlank()) {
                        trailerUrl = shikiVideos.firstOrNull { it.first == "pv" }?.second?.videoUrl
                            ?: shikiVideos.firstOrNull()?.second?.videoUrl
                    }

                    // Açılış müzikleri & ekstra tanıtım videoları
                    val extraVideosAsThemes = shikiVideos.filter { it.first != "ed" }.map { it.second }
                    val finalOpenings = (themeOps + extraVideosAsThemes).distinctBy { it.videoUrl ?: it.label }

                    // Kapanış müzikleri & ED videoları
                    val extraEdsAsThemes = shikiVideos.filter { it.first == "ed" }.map { it.second }
                    val finalEndings = (themeEds + extraEdsAsThemes).distinctBy { it.videoUrl ?: it.label }

                    val streamingSites = setOf("Crunchyroll", "Netflix", "HIDIVE", "Disney+", "Amazon Prime")
                    val streamingLinks = shikiLinks.filter { link -> streamingSites.any { link.site.contains(it, ignoreCase = true) } }

                    val rawDesc = data.optNullableString("description")?.cleanApiText()
                    val synopsis = translateIfRussian(rawDesc)

                    KitsugiMediaDetail(
                        synopsis = synopsis,
                        genres = genresList.toTurkishGenres(),
                        status = statusStr,
                        studios = studiosList,
                        rating = rating,
                        episodeDuration = data.optInt("duration").takeIf { it > 0 }?.let { "$it dk" },
                        startDate = data.optNullableString("aired_on"),
                        endDate = data.optNullableString("released_on"),
                        titleEnglish = engTitle ?: romajiTitle,
                        titleJapanese = japTitle,
                        titleRomaji = romajiTitle,
                        titleNative = japTitle,
                        trailerUrl = trailerUrl,
                        title = mainTitle,
                        imageUrl = imageUrl,
                        score = score,
                        meanScore = meanScore,
                        year = year,
                        total = total,
                        isAdult = isAdult,
                        realMalId = realMalId,
                        openings = finalOpenings,
                        endings = finalEndings,
                        externalLinks = shikiLinks,
                        streamingLinks = streamingLinks
                    )
                }
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori fetchDetail exception: ${err.message}", err)
                null
            }
        }

    suspend fun fetchShikimoriVideos(animeId: Int): List<Pair<String, KitsugiTheme>> = withContext(Dispatchers.IO) {
        if (animeId <= 0) return@withContext emptyList()
        val url = runCatching { URL("$BASE_URL/animes/$animeId/videos") }.getOrNull() ?: return@withContext emptyList()
        runCatching {
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val list = mutableListOf<Pair<String, KitsugiTheme>>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val vUrl = item.optString("url")
                if (vUrl.isBlank() || (!vUrl.contains("youtube.com") && !vUrl.contains("youtu.be"))) continue
                val rawName = item.optString("name", "").trim()
                val kind = item.optString("kind", "pv").lowercase()
                val kindTr = when (kind) {
                    "pv" -> "PV / Tanıtım"
                    "character_trailer" -> "Karakter Tanıtımı"
                    "op" -> "Açılış (OP)"
                    "ed" -> "Kapanış (ED)"
                    "cm" -> "Ticari Reklam (CM)"
                    "clip" -> "Klip"
                    else -> "Video"
                }
                val label = if (rawName.isNotBlank()) "$kindTr - $rawName" else kindTr
                list.add(Pair(kind, KitsugiTheme(label = label, videoUrl = vUrl)))
            }
            list
        }.getOrElse { emptyList() }
    }

    suspend fun fetchShikimoriExternalLinks(animeId: Int): List<KitsugiExternalLink> = withContext(Dispatchers.IO) {
        if (animeId <= 0) return@withContext emptyList()
        val url = runCatching { URL("$BASE_URL/animes/$animeId/external_links") }.getOrNull() ?: return@withContext emptyList()
        runCatching {
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val array = JSONArray(response)
            val list = mutableListOf<KitsugiExternalLink>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lUrl = item.optString("url")
                if (lUrl.isBlank()) continue
                val kind = item.optString("kind", "official_site")
                val siteName = when (kind.lowercase()) {
                    "official_site" -> "Resmi Web Sitesi"
                    "wikipedia" -> "Vikipedi"
                    "crunchyroll" -> "Crunchyroll"
                    "twitter" -> "X (Twitter)"
                    "youtube" -> "YouTube"
                    "netflix" -> "Netflix"
                    "hidive" -> "HIDIVE"
                    "animenewsnetwork" -> "Anime News Network"
                    "myanimelist" -> "MyAnimeList"
                    else -> kind.replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                list.add(KitsugiExternalLink(site = siteName, url = lUrl))
            }
            list
        }.getOrElse { emptyList() }
    }

    // ─── Karakter / Ekip fonksiyonları ────────────────────────────────────

    suspend fun fetchCharacters(
        mediaType: MediaType,
        externalId: Int
    ): List<KitsugiCharacter> {
        return withContext(Dispatchers.IO) {
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "animes"
                MediaType.Manga -> "mangas"
            }
            val url = URL("$BASE_URL/$endpoint/$externalId/roles")
            Log.d(TAG, "Shikimori fetchCharacters: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequest(url) ?: return@runWithRateLimit emptyList()
                    val array = JSONArray(response)
                    val charactersMap = mutableMapOf<Int, KitsugiCharacter>()

                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val charObj = item.optJSONObject("character") ?: continue
                        val charId = charObj.optInt("id")
                        if (charId <= 0) continue

                        val rolesArr = item.optJSONArray("roles")
                        val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                            rolesArr.optString(0)
                        } else "Supporting"

                        val charName = translateIfRussian(charObj.optString("name", "Bilinmeyen"))
                        val relativeImg = charObj.optJSONObject("image")?.optString("original")
                        val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                        val vaList = mutableListOf<KitsugiVoiceActor>()
                        val personObj = item.optJSONObject("person")
                        if (personObj != null) {
                            val vaId = personObj.optInt("id")
                            if (vaId > 0) {
                                val vaName = translateIfRussian(personObj.optString("name", "Bilinmeyen"))
                                val vaRelativeImg = personObj.optJSONObject("image")?.optString("original")
                                val vaImageUrl = vaRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                                vaList.add(
                                    KitsugiVoiceActor(
                                        id = vaId,
                                        name = vaName,
                                        language = "Japonca",
                                        imageUrl = vaImageUrl,
                                        source = "shikimori"
                                    )
                                )
                            }
                        }

                        val existing = charactersMap[charId]
                        if (existing != null) {
                            val updatedVas = (existing.voiceActors + vaList).distinctBy { it.id }
                            charactersMap[charId] = existing.copy(voiceActors = updatedVas)
                        } else {
                            charactersMap[charId] = KitsugiCharacter(
                                id = charId,
                                name = charName,
                                role = roleStr.toTurkishCharacterRole(),
                                imageUrl = imageUrl,
                                voiceActors = vaList,
                                source = "shikimori"
                            )
                        }
                    }
                    charactersMap.values.toList()
                }
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori fetchCharacters exception: ${err.message}", err)
                emptyList()
            }
        }
    }

    suspend fun fetchStaff(
        mediaType: MediaType,
        externalId: Int
    ): List<KitsugiStaff> {
        return withContext(Dispatchers.IO) {
            val endpoint = when (mediaType) {
                MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "animes"
                MediaType.Manga -> "mangas"
            }
            val url = URL("$BASE_URL/$endpoint/$externalId/roles")
            Log.d(TAG, "Shikimori fetchStaff: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequest(url) ?: return@runWithRateLimit emptyList()
                    val array = JSONArray(response)
                    val staffList = mutableListOf<KitsugiStaff>()

                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val charObj = item.optJSONObject("character")
                        if (charObj == null) {
                            val personObj = item.optJSONObject("person") ?: continue
                            val staffId = personObj.optInt("id")
                            if (staffId <= 0) continue

                            val rolesArr = item.optJSONArray("roles")
                            val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                                rolesArr.optString(0)
                            } else "Staff"

                            val staffName = translateIfRussian(personObj.optString("name", "Bilinmeyen"))
                            val relativeImg = personObj.optJSONObject("image")?.optString("original")
                            val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                            staffList.add(
                                KitsugiStaff(
                                    id = staffId,
                                    name = staffName,
                                    role = roleStr.toTurkishStaffRole(),
                                    imageUrl = imageUrl,
                                    source = "shikimori"
                                )
                            )
                        }
                    }
                    staffList
                }
            }.getOrElse { err ->
                Log.e(TAG, "Shikimori fetchStaff exception: ${err.message}", err)
                emptyList()
            }
        }
    }

    suspend fun fetchCharacterDetail(characterId: Int): KitsugiCharacterDetail? {
        return withContext(Dispatchers.IO) {
            val url = URL("$BASE_URL/characters/$characterId")
            Log.d(TAG, "Shikimori fetchCharacterDetail: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequest(url) ?: return@runWithRateLimit null
                    val data = JSONObject(response)

                    val charName = translateIfRussian(data.optString("name", "Bilinmeyen"))
                    val nativeName = data.optNullableString("japanese")
                    val alternativeNames = mutableListOf<String>()
                    val altname = data.optNullableString("altname")
                    if (!altname.isNullOrBlank()) {
                        alternativeNames.add(altname)
                    }

                    val relativeImg = data.optJSONObject("image")?.optString("original")
                    val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                    val biography = translateIfRussian(data.optNullableString("description")?.cleanApiText())

                    val gender = null
                    val age = null
                    val birthday = null
                    val bloodType = null

                    val voiceActors = mutableListOf<KitsugiVoiceActor>()
                    val seyuArray = data.optJSONArray("seyu")
                    if (seyuArray != null) {
                        for (i in 0 until seyuArray.length()) {
                            val seyuItem = seyuArray.optJSONObject(i) ?: continue
                            val seyuId = seyuItem.optInt("id")
                            if (seyuId <= 0) continue

                            val seyuName = translateIfRussian(seyuItem.optString("name", "Bilinmeyen"))
                            val seyuRelativeImg = seyuItem.optJSONObject("image")?.optString("original")
                            val seyuImageUrl = seyuRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                            voiceActors.add(
                                KitsugiVoiceActor(
                                    id = seyuId,
                                    name = seyuName,
                                    language = "Japonca",
                                    imageUrl = seyuImageUrl,
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                    val animesArray = data.optJSONArray("animes")
                    if (animesArray != null) {
                        for (i in 0 until animesArray.length()) {
                            val animeItem = animesArray.optJSONObject(i) ?: continue
                            val animeId = animeItem.optInt("id")
                            if (animeId <= 0) continue

                            val animeTitle = translateIfRussian(animeItem.optString("name", "Bilinmeyen"))
                            val animeRelativeImg = animeItem.optJSONObject("image")?.optString("original")
                            val animeImageUrl = animeRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                            val kind = animeItem.optString("kind", "tv")

                            val rolesArr = animeItem.optJSONArray("roles")
                            val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                                rolesArr.optString(0)
                            } else "Supporting"

                            mediaAppearances.add(
                                KitsugiCharacterMediaAppearance(
                                    mediaId = animeId,
                                    title = animeTitle,
                                    imageUrl = animeImageUrl,
                                    mediaType = kind.toTurkishMediaTypeString(),
                                    characterRole = roleStr.toTurkishCharacterRole(),
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    val mangasArray = data.optJSONArray("mangas")
                    if (mangasArray != null) {
                        for (i in 0 until mangasArray.length()) {
                            val mangaItem = mangasArray.optJSONObject(i) ?: continue
                            val mangaId = mangaItem.optInt("id")
                            if (mangaId <= 0) continue

                            val mangaTitle = translateIfRussian(mangaItem.optString("name", "Bilinmeyen"))
                            val mangaRelativeImg = mangaItem.optJSONObject("image")?.optString("original")
                            val mangaImageUrl = mangaRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                            val rolesArr = mangaItem.optJSONArray("roles")
                            val roleStr = if (rolesArr != null && rolesArr.length() > 0) {
                                rolesArr.optString(0)
                            } else "Supporting"

                            mediaAppearances.add(
                                KitsugiCharacterMediaAppearance(
                                    mediaId = mangaId,
                                    title = mangaTitle,
                                    imageUrl = mangaImageUrl,
                                    mediaType = "manga".toTurkishMediaTypeString(),
                                    characterRole = roleStr.toTurkishCharacterRole(),
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    KitsugiCharacterDetail(
                        id = characterId,
                        name = charName,
                        nativeName = nativeName,
                        alternativeNames = alternativeNames,
                        imageUrl = imageUrl,
                        gender = gender,
                        age = age,
                        birthday = birthday,
                        bloodType = bloodType,
                        biography = biography,
                        voiceActors = voiceActors,
                        mediaAppearances = mediaAppearances,
                        isFavourite = false
                    )
                }
            }.getOrNull()
        }
    }

    suspend fun fetchStaffDetail(staffId: Int): KitsugiStaffDetail? {
        return withContext(Dispatchers.IO) {
            val url = URL("$BASE_URL/people/$staffId")
            Log.d(TAG, "Shikimori fetchStaffDetail: $url")
            runCatching {
                KitsugiApiBase.runWithRateLimit {
                    val response = KitsugiApiBase.executeGetRequest(url) ?: return@runWithRateLimit null
                    val data = JSONObject(response)

                    val staffName = translateIfRussian(data.optString("name", "Bilinmeyen"))
                    val nativeName = data.optNullableString("japanese")
                    val alternativeNames = mutableListOf<String>()
                    val biography = translateIfRussian(data.optNullableString("biography")?.cleanApiText())

                    val rawBirthdayObj = data.opt("birth_on")
                    val rawBirthday = when (rawBirthdayObj) {
                        is JSONObject -> {
                            val d = rawBirthdayObj.optInt("day", 0).takeIf { it > 0 }
                            val m = rawBirthdayObj.optInt("month", 0).takeIf { it in 1..12 }
                            val y = rawBirthdayObj.optInt("year", 0).takeIf { it > 0 }
                            if (d != null && m != null && y != null) "$d.$m.$y"
                            else if (m != null && y != null) "$m.$y"
                            else if (y != null) "$y"
                            else null
                        }
                        is String -> rawBirthdayObj.takeIf { it.isNotBlank() && it != "null" }
                        else -> null
                    }
                    val (birthday, age) = com.kitsugi.animelist.utils.KitsugiDateUtils.formatBirthdayAndCalculateAge(rawBirthday, null)
                    val homeTown = translateIfRussian(data.optNullableString("birth_place"))
                    val gender = null
                    val rawOccupation = data.optNullableString("job_title")
                    val occupation = rawOccupation?.let { translateIfRussian(it).toTurkishStaffRole() }

                    val relativeImg = data.optJSONObject("image")?.optString("original")
                    val imageUrl = relativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                    val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
                    val rolesArray = data.optJSONArray("roles")
                    if (rolesArray != null) {
                        for (i in 0 until rolesArray.length()) {
                            val roleObj = rolesArray.optJSONObject(i) ?: continue
                            val charObj = roleObj.optJSONObject("character") ?: continue
                            val animeObj = roleObj.optJSONObject("anime") ?: roleObj.optJSONObject("manga")

                            val charId = charObj.optInt("id")
                            val charName = translateIfRussian(charObj.optString("name", "Bilinmeyen"))
                            val charRelativeImg = charObj.optJSONObject("image")?.optString("original")
                            val charImg = charRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }

                            val mediaId = animeObj?.optInt("id") ?: 0
                            val mediaTitle = translateIfRussian(animeObj?.optString("name", "Bilinmeyen") ?: "Bilinmeyen")
                            val mediaRelativeImg = animeObj?.optJSONObject("image")?.optString("original")
                            val mediaImg = mediaRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                            val mediaTypeStr = animeObj?.optString("kind", "tv") ?: "manga"

                            val roleStr = roleObj.optString("role", "Seyu") ?: "Seyu"

                            characterRoles.add(
                                KitsugiStaffCharacterRole(
                                    characterId = charId,
                                    characterName = charName,
                                    characterImageUrl = charImg,
                                    characterSource = "shikimori",
                                    mediaId = mediaId,
                                    mediaTitle = mediaTitle,
                                    mediaImageUrl = mediaImg,
                                    mediaType = mediaTypeStr.toTurkishMediaTypeString(),
                                    characterRole = roleStr.toTurkishCharacterRole(),
                                    mediaSource = "shikimori"
                                )
                            )
                        }
                    }

                    val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
                    val worksArray = data.optJSONArray("works")
                    if (worksArray != null) {
                        for (i in 0 until worksArray.length()) {
                            val workObj = worksArray.optJSONObject(i) ?: continue
                            val animeObj = workObj.optJSONObject("anime") ?: workObj.optJSONObject("manga") ?: continue
                            val mediaId = animeObj.optInt("id")
                            if (mediaId <= 0) continue

                            val mediaTitle = translateIfRussian(animeObj.optString("name", "Bilinmeyen") ?: "Bilinmeyen")
                            val mediaRelativeImg = animeObj.optJSONObject("image")?.optString("original")
                            val mediaImg = mediaRelativeImg?.let { if (it.startsWith("/")) "https://shikimori.one$it" else it }
                            val mediaTypeStr = animeObj.optString("kind", "tv") ?: "tv"

                            val roleStr = workObj.optString("role", "Staff") ?: "Staff"

                            mediaWorks.add(
                                KitsugiStaffMediaWork(
                                    mediaId = mediaId,
                                    mediaTitle = mediaTitle,
                                    mediaImageUrl = mediaImg,
                                    mediaType = mediaTypeStr.toTurkishMediaTypeString(),
                                    staffRole = roleStr.toTurkishStaffRole(),
                                    source = "shikimori"
                                )
                            )
                        }
                    }

                    KitsugiStaffDetail(
                        id = staffId,
                        name = staffName,
                        nativeName = nativeName,
                        alternativeNames = alternativeNames,
                        imageUrl = imageUrl,
                        biography = biography,
                        occupation = occupation,
                        birthday = birthday,
                        age = age,
                        gender = gender,
                        homeTown = homeTown,
                        characterRoles = characterRoles,
                        mediaWorks = mediaWorks,
                        isFavourite = false
                    )
                }
            }.getOrNull()
        }
    }

    private suspend fun translateIfRussian(text: String?): String {
        if (text.isNullOrBlank()) return ""
        if (!text.any { it in '\u0400'..'\u04FF' }) return text

        val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
        return if (context != null) {
            val translationManager = com.kitsugi.animelist.data.local.TranslationManager(context)
            val settings = runCatching {
                com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
            }.getOrNull()
            val targetLang = settings?.translateTargetLanguage?.ifBlank { "tr" } ?: "tr"
            translationManager.translateTo(text, "auto", targetLang)
        } else {
            directTranslate(text, "tr")
        }
    }

    private fun directTranslate(text: String, targetLang: String = "tr"): String {
        val cleaned = text.cleanShikimoriBbCode()
        return try {
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=" + 
                    java.net.URLEncoder.encode(cleaned, "UTF-8")
            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: return cleaned
                    val jsonArray = org.json.JSONArray(responseText)
                    val sentences = jsonArray.optJSONArray(0) ?: return cleaned
                    val result = java.lang.StringBuilder()
                    for (i in 0 until sentences.length()) {
                        val sentence = sentences.optJSONArray(i)
                        val translatedPart = sentence?.optString(0)
                        if (translatedPart != null) {
                            result.append(translatedPart)
                        }
                    }
                    result.toString().cleanShikimoriBbCode()
                } else {
                    cleaned
                }
            }
        } catch (e: java.lang.Exception) {
            cleaned
        }
    }
}

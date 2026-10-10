package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.kitsugi.animelist.utils.*

class KitsugiStudioClient {

    suspend fun fetchStudioDetail(
        source: String,
        studioId: Int,
        name: String? = null
    ): KitsugiStudioDetail? {
        return withContext(Dispatchers.IO) {
            if (studioId <= 0) return@withContext null
            when (StudioSourceSupport.canonicalSource(source)) {
                "jikan", "mal" -> {
                    val byId = fetchJikanStudioDetail(studioId)
                    // A producer ID from one namespace must never silently resolve to a different
                    // company. If the clicked chip's label disagrees, recover by exact-name search
                    // in Jikan rather than falling through to an unrelated AniList ID.
                    val detail = when {
                        byId != null && StudioSourceSupport.namesMatch(name, byId.name) -> byId
                        !name.isNullOrBlank() -> {
                            val matchedId = searchJikanProducerId(name)
                            matchedId?.let { fetchJikanStudioDetail(it) }
                                ?.takeIf { StudioSourceSupport.namesMatch(name, it.name) }
                        }
                        else -> byId
                    }
                    if (detail != null && !detail.name.isNullOrBlank() &&
                        com.kitsugi.animelist.KitsugiApplication.getInstance()?.let { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(it) } != null) {
                        val aniListDetail = fetchAniListStudioByName(detail.name)
                        if (aniListDetail != null) {
                            detail.copy(isFavourite = aniListDetail.isFavourite, aniListId = aniListDetail.id)
                        } else detail
                    } else detail
                }
                "anilist" -> fetchAniListStudioDetail(studioId)
                "bangumi" -> fetchBangumiStudioDetail(studioId)
                "shikimori" -> fetchShikimoriStudioDetail(studioId, name)
                "tmdb" -> {
                    val tmdbRes = fetchTmdbStudioDetail(studioId)
                    if (tmdbRes != null && !tmdbRes.name.isNullOrBlank() &&
                        com.kitsugi.animelist.KitsugiApplication.getInstance()?.let { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(it) } != null) {
                        val aniListDetail = fetchAniListStudioByName(tmdbRes.name)
                        if (aniListDetail != null) {
                            tmdbRes.copy(isFavourite = aniListDetail.isFavourite, aniListId = aniListDetail.id)
                        } else tmdbRes
                    } else tmdbRes
                }
                else -> null
            }
        }
    }

    /**
     * Stüdyo yapımlarının [page]. sayfası (2'den başlar; 1. sayfa [fetchStudioDetail] ile gelir).
     * [studioId] çözümlenmiş kimliktir (detail.id): Jikan'da chip kimliğinden farklı olabilir.
     */
    suspend fun fetchStudioWorksPage(source: String, studioId: Int, page: Int): KitsugiStudioWorksPage? =
        withContext(Dispatchers.IO) {
            if (studioId <= 0 || page < 2) return@withContext null
            when (StudioSourceSupport.canonicalSource(source)) {
                "jikan", "mal" -> fetchJikanStudioWorksPage(studioId, page)
                "anilist" -> fetchAniListStudioWorksPage(studioId, page)
                "shikimori" -> fetchShikimoriStudioWorksPage(studioId, page)
                "tmdb" -> fetchTmdbStudioWorksPage(studioId, page)
                // Bangumi: yapım listesi sayfalanmıyor (sayfalama ucu doğrulanamadı) → devamı yok.
                else -> null
            }
        }

    /**
     * Bangumi'de stüdyo/şirketler ayrı bir kayıt türü değil, 人物 (kişi)条目'larının
     * `type = 2` (公司) alt kümesidir. Bu yüzden kurum detayı, kişi ucu üzerinden okunur ve
     * Kitsugi'nin stüdyo modeline çevrilir: açıklamalar infobox'dan, "Yapımlar" listesi
     * `/persons/{id}/works` ucundan gelir.
     */
    private suspend fun fetchBangumiStudioDetail(personId: Int): KitsugiStudioDetail? {
        val person = runCatching {
            KitsugiBangumiDetailClient.fetchStaffDetail(personId)
        }.getOrNull() ?: return null

        val about = buildString {
            if (!person.occupation.isNullOrBlank()) appendLine(person.occupation)
            if (!person.homeTown.isNullOrBlank()) appendLine(person.homeTown)
            if (!person.biography.isNullOrBlank()) append(person.biography)
        }.trim().takeIf { it.isNotBlank() }

        return KitsugiStudioDetail(
            id = person.id,
            name = person.englishName ?: person.name,
            isMain = true,
            imageUrl = person.imageUrl,
            about = about,
            mediaWorks = person.mediaWorks
        )
    }

    private suspend fun searchJikanProducerId(expectedName: String): Int? = runCatching {
        val query = URLEncoder.encode(expectedName.trim(), "UTF-8")
        val url = URL("https://api.jikan.moe/v4/producers?q=$query&limit=25")
        val response = KitsugiApiBase.runWithRateLimit {
            KitsugiApiBase.executeGetRequest(url)
        } ?: return@runCatching null
        val data = JSONObject(response).optJSONArray("data") ?: return@runCatching null
        var bestId: Int? = null
        var bestScore = 0
        val expectedKey = StudioSourceSupport.normalizeName(expectedName)

        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val id = item.optInt("mal_id").takeIf { it > 0 } ?: continue
            val names = buildList {
                item.optNullableString("name")?.let(::add)
                item.optJSONArray("titles")?.let { titles ->
                    for (titleIndex in 0 until titles.length()) {
                        titles.optJSONObject(titleIndex)?.optNullableString("title")?.let(::add)
                    }
                }
            }
            if (names.none { StudioSourceSupport.namesMatch(expectedName, it) }) continue
            val exact = names.any { StudioSourceSupport.normalizeName(it) == expectedKey }
            val score = if (exact) 2 else 1
            if (score > bestScore) {
                bestId = id
                bestScore = score
                if (exact) break
            }
        }
        bestId
    }.getOrNull()

    /**
     * Shikimori'de tekil `/api/studios/{id}` ucu yoktur (404 döner); stüdyo kayıtları yalnızca
     * `/api/studios` listesinde bulunur. Bu yüzden ad, tıklanan chip'ten alınır ve yapımlar
     * doğrudan `/api/animes?studio={id}` ucundan çekilir.
     */
    private suspend fun fetchShikimoriStudioDetail(studioId: Int, name: String?): KitsugiStudioDetail? = runCatching {
        val studioName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return@runCatching null
        val worksUrl = shikimoriStudioWorksUrl(studioId, 1)
        val worksResponse = KitsugiApiBase.executeGetRequestResilient(worksUrl)
        val works = worksResponse?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }

        KitsugiStudioDetail(
            id = studioId,
            name = studioName,
            isMain = true,
            imageUrl = null,
            about = null,
            mediaWorks = parseShikimoriStudioWorks(works).distinctBy { it.mediaId },
            hasMoreWorks = (works?.length() ?: 0) >= STUDIO_WORKS_PAGE_SIZE
        )
    }.getOrNull()

    private fun shikimoriStudioWorksUrl(studioId: Int, page: Int): URL =
        URL("https://shikimori.io/api/animes?studio=$studioId&limit=$STUDIO_WORKS_PAGE_SIZE&order=aired_on&page=$page")

    private fun parseShikimoriStudioWorks(works: org.json.JSONArray?): List<KitsugiStaffMediaWork> {
        if (works == null) return emptyList()
        val out = ArrayList<KitsugiStaffMediaWork>(works.length())
        for (index in 0 until works.length()) {
            val item = works.optJSONObject(index) ?: continue
            val mediaId = item.optInt("id").takeIf { it > 0 } ?: continue
            val romajiTitle = item.optNullableString("name")
            val englishTitle = item.optJSONArray("english")?.optString(0)?.takeIf { it.isNotBlank() && it != "null" }
            val russianTitle = item.optNullableString("russian")
            val title = russianTitle ?: englishTitle ?: romajiTitle ?: "Başlıksız"
            val kind = item.optNullableString("kind").orEmpty().lowercase()
            val posterUrl = item.optJSONObject("image")
                ?.let { image -> (image.optNullableString("original") ?: image.optNullableString("preview")) }
                ?.let(::absoluteShikimoriImageUrl)
            out.add(
                KitsugiStaffMediaWork(
                    mediaId = mediaId,
                    mediaTitle = title,
                    mediaImageUrl = posterUrl,
                    mediaType = (if (kind == "movie") "movie" else "anime").toTurkishMediaTypeString(),
                    staffRole = "Stüdyo",
                    source = "shikimori",
                    titleEnglish = englishTitle,
                    titleRomaji = romajiTitle
                )
            )
        }
        return out
    }

    private suspend fun fetchShikimoriStudioWorksPage(studioId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val body = KitsugiApiBase.executeGetRequestResilient(shikimoriStudioWorksUrl(studioId, page))
            ?: return@runCatching null
        val works = org.json.JSONArray(body)
        KitsugiStudioWorksPage(
            works = parseShikimoriStudioWorks(works).distinctBy { it.mediaId },
            hasMore = works.length() >= STUDIO_WORKS_PAGE_SIZE
        )
    }.getOrNull()

    private fun absoluteShikimoriImageUrl(value: String): String? = when {
        value.isBlank() || value.contains("/assets/globals/missing_") -> null
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://shikimori.io$value"
        else -> null
    }

    private suspend fun fetchTmdbStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val infoUrl = URL("https://api.themoviedb.org/3/company/$studioId?api_key=$apiKey")

        return runCatching {
            val detailResponse = KitsugiApiBase.executeGetRequest(infoUrl) ?: return@runCatching null
            val detailData = JSONObject(detailResponse)

            val name = detailData.optString("name")
            val hq = detailData.optNullableString("headquarters")
            val country = detailData.optNullableString("origin_country")
            val desc = detailData.optNullableString("description")

            val about = buildString {
                if (!hq.isNullOrBlank()) append("Merkez: $hq\n")
                if (!country.isNullOrBlank()) append("Ülke: $country\n")
                if (!desc.isNullOrBlank()) append(desc)
            }.trim().cleanApiText().takeIf { it.isNotBlank() }

            val logoPath = detailData.optNullableString("logo_path")
            val imageUrl = if (!logoPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$logoPath" else null

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()

            val (movieWorks, movieTotalPages) = fetchTmdbDiscover(true, studioId, apiKey, lang, 1)
            val (tvWorks, tvTotalPages) = fetchTmdbDiscover(false, studioId, apiKey, lang, 1)
            mediaWorks.addAll(movieWorks)
            mediaWorks.addAll(tvWorks)
            val tmdbHasMore = movieTotalPages > 1 || tvTotalPages > 1

            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = true,
                imageUrl = imageUrl,
                favorites = null,
                established = null,
                about = about,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                hasMoreWorks = tmdbHasMore
            )
        }.getOrNull()
    }

    /** TMDB discover (film veya dizi) tek sayfası: yapımlar + toplam sayfa sayısı. */
    private fun fetchTmdbDiscover(
        isMovie: Boolean,
        studioId: Int,
        apiKey: String,
        lang: String,
        page: Int
    ): Pair<List<KitsugiStaffMediaWork>, Int> {
        val kind = if (isMovie) "movie" else "tv"
        val urlStr = "https://api.themoviedb.org/3/discover/$kind?api_key=$apiKey&with_companies=$studioId&language=$lang&sort_by=popularity.desc&page=$page"
        val response = KitsugiApiBase.executeGetRequest(URL(urlStr)) ?: return Pair(emptyList(), 0)
        val root = JSONObject(response)
        val results = root.optJSONArray("results") ?: return Pair(emptyList(), 0)
        val totalPages = root.optInt("total_pages", 0)
        // Türkçe başlık yoksa İngilizce'ye düşmek için en-US haritası
        val enTitles = if (TmdbTitleFallback.needsEnglishFallback(results, urlStr, { isMovie })) {
            TmdbTitleFallback.parseEnglishTitles(
                runCatching { KitsugiApiBase.executeGetRequest(URL(TmdbUrlUtils.englishVariant(urlStr))) }.getOrNull()
            )
        } else emptyMap()
        val works = ArrayList<KitsugiStaffMediaWork>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optInt("id")
            val title = TmdbTitleFallback.resolve(
                item = item,
                isMovie = isMovie,
                url = urlStr,
                englishTitle = enTitles[id]
            ).display.ifBlank { "Başlıksız" }
            val posterPath = item.optNullableString("poster_path")
            val imgUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$posterPath" else null
            works.add(
                KitsugiStaffMediaWork(
                    mediaId = id,
                    mediaTitle = title,
                    mediaImageUrl = imgUrl,
                    mediaType = kind.toTurkishMediaTypeString(),
                    staffRole = "Yapım Şirketi",
                    source = "tmdb"
                )
            )
        }
        return Pair(works, totalPages)
    }

    private suspend fun fetchTmdbStudioWorksPage(studioId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val (movies, movieTotal) = fetchTmdbDiscover(true, studioId, apiKey, lang, page)
        val (tv, tvTotal) = fetchTmdbDiscover(false, studioId, apiKey, lang, page)
        KitsugiStudioWorksPage(
            works = (movies + tv).distinctBy { it.mediaId },
            hasMore = page < movieTotal || page < tvTotal
        )
    }.getOrNull()

    private fun jikanStudioWorksUrl(producerId: Int, page: Int): URL =
        // limit=50: Tenrai'nin üst sınırı (daha büyük değer 400 döndürür).
        URL("https://api.jikan.moe/v4/anime?producers=$producerId&order_by=start_date&sort=desc&limit=$STUDIO_WORKS_PAGE_SIZE&sfw=false&page=$page")

    private fun parseJikanStudioWorks(data: org.json.JSONArray?): List<KitsugiStaffMediaWork> {
        if (data == null) return emptyList()
        val out = ArrayList<KitsugiStaffMediaWork>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optInt("mal_id")
            val title = item.optNullableString("title") ?: "Bilinmeyen"
            val imageUrl = item.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
            val type = item.optNullableString("type").orEmpty().lowercase()
            out.add(
                KitsugiStaffMediaWork(
                    mediaId = id,
                    mediaTitle = title,
                    mediaImageUrl = imageUrl,
                    mediaType = if (type.contains("manga")) "manga".toTurkishMediaTypeString() else "anime".toTurkishMediaTypeString(),
                    staffRole = "Ana Stüdyo",
                    source = "jikan"
                )
            )
        }
        return out
    }

    private suspend fun fetchJikanStudioWorksPage(producerId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val body = KitsugiApiBase.executeGetRequestResilient(jikanStudioWorksUrl(producerId, page))
            ?: return@runCatching null
        val root = JSONObject(body)
        KitsugiStudioWorksPage(
            works = parseJikanStudioWorks(root.optJSONArray("data")).distinctBy { it.mediaId },
            hasMore = root.optJSONObject("pagination")?.optBoolean("has_next_page", false) == true
        )
    }.getOrNull()

    private suspend fun fetchJikanStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val infoUrl = URL("https://api.jikan.moe/v4/producers/$studioId")
        // limit=50: Tenrai'nin üst sınırı; daha büyük değerler 400 döndürüp yapım listesini boşaltıyordu.
        val mediaUrl = jikanStudioWorksUrl(studioId, 1)

        return runCatching {
            // Jikan rate limit: maks 3 istek/saniye — her çağrı runWithRateLimit ile korunuyor
            // Resilient: tek seferlik 429/5xx yüzünden tüm stüdyo sayfası düşmesin.
            val infoResponse = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequestResilient(infoUrl)
            } ?: return@runCatching null
            val infoRoot = JSONObject(infoResponse)
            val infoData = infoRoot.optJSONObject("data") ?: return@runCatching null

            // Jikan /producers/{id}: isim data.titles[].title (type=="Default") altında,
            // üst seviye data.name alanı bu endpoint'te mevcut değil.
            val titlesArr = infoData.optJSONArray("titles")
            val name: String = if (titlesArr != null) {
                var defaultTitle: String? = null
                var anyTitle: String? = null
                for (i in 0 until titlesArr.length()) {
                    val t = titlesArr.optJSONObject(i) ?: continue
                    val type = t.optNullableString("type")
                    val title = t.optNullableString("title")
                    if (!title.isNullOrBlank()) {
                        if (anyTitle == null) anyTitle = title
                        if (type.equals("Default", ignoreCase = true) ||
                            type.equals("English", ignoreCase = true)
                        ) {
                            defaultTitle = title
                        }
                    }
                }
                defaultTitle ?: anyTitle ?: infoData.optNullableString("name") ?: "Bilinmeyen"
            } else {
                infoData.optNullableString("name") ?: "Bilinmeyen"
            }

            val favorites = infoData.optionalPositiveInt("favorites")
            val established = infoData.optNullableString("established")
            val about = infoData.optNullableString("about")?.cleanApiText()

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val mediaResponse = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequestResilient(mediaUrl)
            }
            var worksHasMore = false
            if (mediaResponse != null) {
                val mediaRoot = JSONObject(mediaResponse)
                mediaWorks.addAll(parseJikanStudioWorks(mediaRoot.optJSONArray("data")))
                worksHasMore = mediaRoot.optJSONObject("pagination")?.optBoolean("has_next_page", false) == true
            }

            val imagesObj = infoData.optJSONObject("images")
            val imageUrl = imagesObj?.optJSONObject("jpg")?.optNullableString("image_url")

            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = true,
                imageUrl = imageUrl,
                favorites = favorites,
                established = established,
                about = about,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                hasMoreWorks = worksHasMore
            )
        }.getOrNull()
    }

    private suspend fun fetchAniListStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val query = """
            query (${'$'}id: Int) {
                Studio(id: ${'$'}id) {
                    id
                    name
                    isAnimationStudio
                    isFavourite
                    favourites
                    media(page: 1, perPage: 50, sort: [START_DATE_DESC]) {
                        pageInfo { hasNextPage }
                        nodes {
                            id
                            idMal
                            title { userPreferred english romaji native }
                            coverImage { large }
                            type
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("id", studioId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val data = root.optJSONObject("data")?.optJSONObject("Studio") ?: return@runCatching null

            val name = data.optString("name")
            val favorites = data.optionalPositiveInt("favourites")
            val isAnimationStudio = data.optBoolean("isAnimationStudio", true)

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val mediaObj = data.optJSONObject("media")
            val nodes = mediaObj?.optJSONArray("nodes")
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val node = nodes.optJSONObject(i) ?: continue
                    val id = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val titleObj = node.optJSONObject("title")
                    val title = titleObj?.optNullableString("userPreferred") ?: "Başlıksız"
                    val titleEnglish = titleObj?.optNullableString("english")
                    val titleNative = titleObj?.optNullableString("native")
                    val titleRomaji = titleObj?.optNullableString("romaji")
                    val imgUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableId = idMal ?: (100_000_000 + id)

                    mediaWorks.add(
                        KitsugiStaffMediaWork(
                            mediaId = stableId,
                            mediaTitle = title,
                            mediaImageUrl = imgUrl,
                            mediaType = type.toTurkishMediaTypeString(),
                            staffRole = "Ana Stüdyo",
                            source = if (idMal != null) "jikan" else "anilist",
                            titleEnglish = titleEnglish,
                            titleJapanese = titleNative,
                            titleRomaji = titleRomaji
                        )
                    )
                }
            }

            val worksHasMore = mediaObj?.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
            val isFavourite = data.optBoolean("isFavourite", false)
            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = isAnimationStudio,
                imageUrl = null,
                favorites = favorites,
                established = null,
                about = null,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                isFavourite = isFavourite,
                aniListId = studioId,
                hasMoreWorks = worksHasMore
            )
        }.getOrNull()
    }

    private suspend fun fetchAniListStudioWorksPage(studioId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val query = """
            query (${'$'}id: Int, ${'$'}page: Int) {
                Studio(id: ${'$'}id) {
                    media(page: ${'$'}page, perPage: 50, sort: [START_DATE_DESC]) {
                        pageInfo { hasNextPage }
                        nodes {
                            id
                            idMal
                            title { userPreferred english romaji native }
                            coverImage { large }
                            type
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("id", studioId).put("page", page)
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
        val mediaObj = JSONObject(response).optJSONObject("data")
            ?.optJSONObject("Studio")
            ?.optJSONObject("media") ?: return@runCatching null
        KitsugiStudioWorksPage(
            works = parseAniListStudioNodes(mediaObj.optJSONArray("nodes")).distinctBy { it.mediaId },
            hasMore = mediaObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
        )
    }.getOrNull()

    private fun parseAniListStudioNodes(nodes: org.json.JSONArray?): List<KitsugiStaffMediaWork> {
        if (nodes == null) return emptyList()
        val out = ArrayList<KitsugiStaffMediaWork>(nodes.length())
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val id = node.optInt("id")
            val idMal = node.optionalPositiveInt("idMal")
            val titleObj = node.optJSONObject("title")
            val title = titleObj?.optNullableString("userPreferred") ?: "Başlıksız"
            val imgUrl = node.optJSONObject("coverImage")?.optNullableString("large")
            val type = node.optNullableString("type").orEmpty().lowercase()
            out.add(
                KitsugiStaffMediaWork(
                    mediaId = idMal ?: (100_000_000 + id),
                    mediaTitle = title,
                    mediaImageUrl = imgUrl,
                    mediaType = type.toTurkishMediaTypeString(),
                    staffRole = "Ana Stüdyo",
                    source = if (idMal != null) "jikan" else "anilist",
                    titleEnglish = titleObj?.optNullableString("english"),
                    titleJapanese = titleObj?.optNullableString("native"),
                    titleRomaji = titleObj?.optNullableString("romaji")
                )
            )
        }
        return out
    }

    private suspend fun fetchAniListStudioByName(name: String): KitsugiStudioDetail? {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 10) {
                    studios(search: ${'$'}search) {
                        id
                        name
                        isAnimationStudio
                        isFavourite
                        favourites
                        media(page: 1, perPage: 50, sort: [START_DATE_DESC]) {
                            pageInfo { hasNextPage }
                            nodes {
                                id
                                idMal
                                title { userPreferred english romaji native }
                                coverImage { large }
                                type
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("search", name)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val studiosArr = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("studios") ?: return@runCatching null
            val candidates = (0 until studiosArr.length()).mapNotNull { studiosArr.optJSONObject(it) }
            val expectedName = StudioSourceSupport.normalizeName(name)
            val studioObj = candidates.firstOrNull {
                StudioSourceSupport.normalizeName(it.optString("name")) == expectedName
            } ?: candidates.firstOrNull {
                StudioSourceSupport.namesMatch(name, it.optString("name"))
            } ?: return@runCatching null

            val studioId = studioObj.optInt("id")
            val studioName = studioObj.optString("name")
            val favorites = studioObj.optionalPositiveInt("favourites")
            val isAnimationStudio = studioObj.optBoolean("isAnimationStudio", true)

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val mediaObj = studioObj.optJSONObject("media")
            val nodes = mediaObj?.optJSONArray("nodes")
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val node = nodes.optJSONObject(i) ?: continue
                    val id = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val titleObj = node.optJSONObject("title")
                    val title = titleObj?.optNullableString("userPreferred") ?: "Başlıksız"
                    val titleEnglish = titleObj?.optNullableString("english")
                    val titleNative = titleObj?.optNullableString("native")
                    val titleRomaji = titleObj?.optNullableString("romaji")
                    val imgUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableId = idMal ?: (100_000_000 + id)

                    mediaWorks.add(
                        KitsugiStaffMediaWork(
                            mediaId = stableId,
                            mediaTitle = title,
                            mediaImageUrl = imgUrl,
                            mediaType = type.toTurkishMediaTypeString(),
                            staffRole = "Ana Stüdyo",
                            source = if (idMal != null) "jikan" else "anilist",
                            titleEnglish = titleEnglish,
                            titleJapanese = titleNative,
                            titleRomaji = titleRomaji
                        )
                    )
                }
            }

            val worksHasMore = mediaObj?.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
            val isFavourite = studioObj.optBoolean("isFavourite", false)
            KitsugiStudioDetail(
                id = studioId,
                name = studioName,
                isMain = isAnimationStudio,
                imageUrl = null,
                favorites = favorites,
                established = null,
                about = null,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                isFavourite = isFavourite,
                aniListId = studioId,
                hasMoreWorks = worksHasMore
            )
        }.getOrNull()
    }
}

/** Stüdyo yapım listesinin sayfa boyutu: Jikan/Tenrai, Shikimori ve AniList için ortak üst sınır. */
private const val STUDIO_WORKS_PAGE_SIZE = 50

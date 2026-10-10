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
            if (studioId <= 0 && name.isNullOrBlank()) return@withContext null
            val canonicalSource = StudioSourceSupport.canonicalSource(source)
            val primary = when (canonicalSource) {
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
                "shikimori" -> fetchShikimoriStudioDetail(studioId)
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

            // ── Sağlayıcılar arası isim kurtarması ───────────────────────────
            // Stüdyo/şirket kimlikleri sağlayıcıya özgüdür: kimlik uzayı çözülen
            // kaynakla uyuşmayan çipler (Simkl/Kitsu gibi stüdyo ucu olmayan
            // kaynakların çipleri, eski önbellek satırları, çapraz zenginleştirme
            // kalıntıları) ya hiç açılmaz ya da YANLIŞ şirketi açardı. Birincil
            // arama boş dönerse ya da dönen ad tıklanan çiple uyuşmazsa, şirket
            // adıyla doğru kimliği ararız (Jikan → AniList → TMDB). Birincil sonuç
            // ad eşleşmesiyle sağlamsa hiçbir ek istek atılmaz.
            if (name.isNullOrBlank()) {
                primary
            } else if (primary == null || !StudioSourceSupport.namesMatch(name, primary.name)) {
                recoverStudioByName(name, excludeSource = canonicalSource) ?: primary
            } else {
                primary
            }
        }
    }

    /**
     * Şirket adıyla sağlayıcılar arasında doğru kimliği arar. Kimlik uzayı
     * uyuşmazlığındaki tüm stüdyo/yapımcı çipleri için ortak kurtarma yoludur;
     * birincil kaynağı tekrar sorgulamamak için [excludeSource] atlanır.
     */
    private suspend fun recoverStudioByName(name: String, excludeSource: String? = null): KitsugiStudioDetail? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null

        if (excludeSource != "jikan" && excludeSource != "mal") {
            val jikanId = searchJikanProducerId(trimmed)
            if (jikanId != null) {
                fetchJikanStudioDetail(jikanId)
                    ?.takeIf { StudioSourceSupport.namesMatch(trimmed, it.name) }
                    ?.let { return it }
            }
        }
        if (excludeSource != "anilist") {
            fetchAniListStudioByName(trimmed)
                ?.takeIf { StudioSourceSupport.namesMatch(trimmed, it.name) }
                ?.let { return it }
        }
        if (excludeSource != "tmdb") {
            val tmdbId = searchTmdbCompanyId(trimmed)
            if (tmdbId != null) {
                fetchTmdbStudioDetail(tmdbId)
                    ?.takeIf { StudioSourceSupport.namesMatch(trimmed, it.name) }
                    ?.let { return it }
            }
        }
        return null
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

    /** TMDB şirket adıyla kimlik arar — kimlik uzayı uyuşmazlığında kurtarma yoludur. */
    private suspend fun searchTmdbCompanyId(expectedName: String): Int? = runCatching {
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return@runCatching null
        val query = URLEncoder.encode(expectedName.trim(), "UTF-8")
        val url = URL("https://api.themoviedb.org/3/search/company?api_key=$apiKey&query=$query")
        val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
        val results = JSONObject(response).optJSONArray("results") ?: return@runCatching null
        var bestId: Int? = null
        var bestScore = 0
        val expectedKey = StudioSourceSupport.normalizeName(expectedName)

        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val id = item.optInt("id").takeIf { it > 0 } ?: continue
            val companyName = item.optNullableString("name") ?: continue
            if (!StudioSourceSupport.namesMatch(expectedName, companyName)) continue
            val exact = StudioSourceSupport.normalizeName(companyName) == expectedKey
            val score = if (exact) 2 else 1
            if (score > bestScore) {
                bestId = id
                bestScore = score
                if (exact) break
            }
        }
        bestId
    }.getOrNull()

    private suspend fun fetchShikimoriStudioDetail(studioId: Int): KitsugiStudioDetail? = runCatching {
        val infoUrl = URL("https://shikimori.io/api/studios/$studioId")
        val infoResponse = KitsugiApiBase.executeGetRequestResilient(infoUrl) ?: return@runCatching null
        val info = JSONObject(infoResponse)
        val name = info.optNullableString("name")
            ?: info.optNullableString("filtered_name")
            ?: return@runCatching null
        val imageUrl = info.optJSONObject("image")?.let { image ->
            (image.optNullableString("original") ?: image.optNullableString("preview"))
                ?.let(::absoluteShikimoriImageUrl)
        }
        val about = info.optNullableString("description")?.cleanApiText()?.takeIf { it.isNotBlank() }
        val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
        val worksUrl = URL("https://shikimori.io/api/animes?studio=$studioId&limit=50&order=aired_on")
        val worksResponse = KitsugiApiBase.executeGetRequestResilient(worksUrl)
        val works = worksResponse?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }
        if (works != null) {
            for (index in 0 until works.length()) {
                val item = works.optJSONObject(index) ?: continue
                val mediaId = item.optInt("id").takeIf { it > 0 } ?: continue
                val romajiTitle = item.optNullableString("name")
                val englishTitle = item.optJSONArray("english")?.optString(0)?.takeIf { it.isNotBlank() && it != "null" }
                val russianTitle = item.optNullableString("russian")
                val title = russianTitle ?: englishTitle ?: romajiTitle ?: "Başlıksız"
                val kind = item.optNullableString("kind").orEmpty().lowercase()
                val mediaType = if (kind == "movie") MediaType.Movie else MediaType.Anime
                val posterUrl = item.optJSONObject("image")
                    ?.let { image -> (image.optNullableString("original") ?: image.optNullableString("preview")) }
                    ?.let(::absoluteShikimoriImageUrl)
                mediaWorks.add(
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
        }

        KitsugiStudioDetail(
            id = studioId,
            name = name,
            isMain = true,
            imageUrl = imageUrl,
            about = about,
            mediaWorks = mediaWorks.distinctBy { it.mediaId }
        )
    }.getOrNull()

    private fun absoluteShikimoriImageUrl(value: String): String? = when {
        value.isBlank() -> null
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://shikimori.io$value"
        else -> null
    }

    private suspend fun fetchTmdbStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val infoUrl = URL("https://api.themoviedb.org/3/company/$studioId?api_key=$apiKey")
        val moviesUrl = URL("https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&with_companies=$studioId&language=$lang&sort_by=popularity.desc")
        val tvUrl = URL("https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_companies=$studioId&language=$lang&sort_by=popularity.desc")

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

            // Movie discover — Türkçe başlık yoksa İngilizce'ye düşmek için en-US haritası
            val movieResponse = KitsugiApiBase.executeGetRequest(moviesUrl)
            if (movieResponse != null) {
                val root = JSONObject(movieResponse)
                val results = root.optJSONArray("results")
                if (results != null) {
                    val movieUrlStr = moviesUrl.toString()
                    val movieEnTitles = if (TmdbTitleFallback.needsEnglishFallback(results, movieUrlStr, { true })) {
                        TmdbTitleFallback.parseEnglishTitles(
                            runCatching {
                                KitsugiApiBase.executeGetRequest(URL(TmdbUrlUtils.englishVariant(movieUrlStr)))
                            }.getOrNull()
                        )
                    } else emptyMap()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val id = item.optInt("id")
                        val title = TmdbTitleFallback.resolve(
                            item = item,
                            isMovie = true,
                            url = movieUrlStr,
                            englishTitle = movieEnTitles[id]
                        ).display.ifBlank { "Başlıksız" }
                        val posterPath = item.optNullableString("poster_path")
                        val imgUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$posterPath" else null
                        mediaWorks.add(
                            KitsugiStaffMediaWork(
                                mediaId = id,
                                mediaTitle = title,
                                mediaImageUrl = imgUrl,
                                mediaType = "movie".toTurkishMediaTypeString(),
                                staffRole = "Yapım Şirketi",
                                source = "tmdb"
                            )
                        )
                    }
                }
            }

            // TV discover — Türkçe başlık yoksa İngilizce'ye düşmek için en-US haritası
            val tvResponse = KitsugiApiBase.executeGetRequest(tvUrl)
            if (tvResponse != null) {
                val root = JSONObject(tvResponse)
                val results = root.optJSONArray("results")
                if (results != null) {
                    val tvUrlStr = tvUrl.toString()
                    val tvEnTitles = if (TmdbTitleFallback.needsEnglishFallback(results, tvUrlStr, { false })) {
                        TmdbTitleFallback.parseEnglishTitles(
                            runCatching {
                                KitsugiApiBase.executeGetRequest(URL(TmdbUrlUtils.englishVariant(tvUrlStr)))
                            }.getOrNull()
                        )
                    } else emptyMap()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val id = item.optInt("id")
                        val title = TmdbTitleFallback.resolve(
                            item = item,
                            isMovie = false,
                            url = tvUrlStr,
                            englishTitle = tvEnTitles[id]
                        ).display.ifBlank { "Başlıksız" }
                        val posterPath = item.optNullableString("poster_path")
                        val imgUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$posterPath" else null
                        mediaWorks.add(
                            KitsugiStaffMediaWork(
                                mediaId = id,
                                mediaTitle = title,
                                mediaImageUrl = imgUrl,
                                mediaType = "tv".toTurkishMediaTypeString(),
                                staffRole = "Yapım Şirketi",
                                source = "tmdb"
                            )
                        )
                    }
                }
            }

            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = true,
                imageUrl = imageUrl,
                favorites = null,
                established = null,
                about = about,
                mediaWorks = mediaWorks.distinctBy { it.mediaId }
            )
        }.getOrNull()
    }

    private suspend fun fetchJikanStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val infoUrl = URL("https://api.jikan.moe/v4/producers/$studioId")
        val mediaUrl = URL("https://api.jikan.moe/v4/anime?producers=$studioId&order_by=start_date&sort=desc&limit=80&sfw=false")

        return runCatching {
            // Jikan rate limit: maks 3 istek/saniye — her çağrı runWithRateLimit ile korunuyor
            val infoResponse = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequest(infoUrl)
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
                KitsugiApiBase.executeGetRequest(mediaUrl)
            }
            if (mediaResponse != null) {
                val mediaRoot = JSONObject(mediaResponse)
                val mediaData = mediaRoot.optJSONArray("data")
                if (mediaData != null) {
                    for (i in 0 until mediaData.length()) {
                        val item = mediaData.optJSONObject(i) ?: continue
                        val id = item.optInt("mal_id")
                        val title = item.optNullableString("title") ?: "Bilinmeyen"
                        val imageUrl = item.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                        val type = item.optNullableString("type").orEmpty().lowercase()

                        mediaWorks.add(
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
                }
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
                mediaWorks = mediaWorks.distinctBy { it.mediaId }
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
                    media(page: 1, perPage: 80, sort: [START_DATE_DESC]) {
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
                aniListId = studioId
            )
        }.getOrNull()
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
                        media(page: 1, perPage: 80, sort: [START_DATE_DESC]) {
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
                aniListId = studioId
            )
        }.getOrNull()
    }
}

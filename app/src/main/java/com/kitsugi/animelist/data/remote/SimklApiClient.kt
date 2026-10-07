package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.model.MediaEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class SimklApiClient(
    private val clientMap: Map<String, String>? = null
) {
    private val client = com.kitsugi.animelist.core.network.KitsugiHttpClient.client
    // T3-01: BuildConfig'den alınır
    private val clientId get() = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID

    private fun checkResponseAndThrow(response: okhttp3.Response) {
        if (response.code == 401) {
            val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
            if (context != null) {
                com.kitsugi.animelist.data.auth.ExternalAuthManager.handleSimkl401(context)
            }
            throw com.kitsugi.animelist.data.repository.SimklAuthException("Token revoked (401)")
        }
    }


    suspend fun search(query: String, type: String? = null, limit: Int = 20): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val rawQuery = query.trim()
        if (rawQuery.isBlank()) return@withContext emptyList()
        if (type == null || type == "all") {
            coroutineScope {
                val animeDef = async { searchType(rawQuery, "anime", limit) }
                val tvDef = async { searchType(rawQuery, "tv", limit) }
                val movieDef = async { searchType(rawQuery, "movie", limit) }
                (animeDef.await() + tvDef.await() + movieDef.await()).distinctBy { it.malId }
            }
        } else {
            searchType(rawQuery, type, limit)
        }
    }

    private fun searchType(query: String, type: String, limit: Int): List<JikanSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = when (type.lowercase()) {
            "anime" -> "anime"
            "movie", "movies" -> "movie"
            "tv", "shows", "series" -> "tv"
            else -> "anime"
        }
        val url = "https://api.simkl.com/search/$endpoint?q=$encoded&client_id=$clientId&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("SimklApiClient", "search $endpoint HTTP ${response.code}")
                    return emptyList()
                }
                val responseText = response.body?.string().orEmpty()
                if (responseText.isBlank() || responseText.trim() == "null") return emptyList()
                val jsonArray = JSONArray(responseText)
                val results = mutableListOf<JikanSearchResult>()

                for (i in 0 until minOf(jsonArray.length(), limit)) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", "")
                    val ids = obj.optJSONObject("ids") ?: continue

                    val simklId = ids.optInt("simkl_id", 0).takeIf { it > 0 }
                        ?: ids.optInt("simkl", 0)
                    if (simklId <= 0) continue

                    val tmdbId = ids.optInt("tmdb_id", 0).takeIf { it > 0 } ?: ids.optInt("tmdb", 0)
                    val malId = ids.optInt("mal", 0)
                    val poster = obj.optString("poster", "")
                    val year = obj.optInt("year", 0)
                    val itemType = obj.optString("type", type)

                    val ratingsObj = obj.optJSONObject("ratings")
                    val simklRating = ratingsObj?.optJSONObject("simkl")?.optDouble("rating", 0.0) ?: 0.0
                    val imdbRating = ratingsObj?.optJSONObject("imdb")?.optDouble("rating", 0.0) ?: 0.0
                    val score = if (simklRating > 0.0) (simklRating * 10).toInt()
                    else if (imdbRating > 0.0) (imdbRating * 10).toInt()
                    else null

                    val mediaType = when (itemType.lowercase()) {
                        "movie", "movies" -> MediaType.Movie
                        "tv", "series", "shows" -> MediaType.TvShow
                        else -> MediaType.Anime
                    }

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = if (year > 0) year.toString() else "",
                            type = mediaType,
                            total = null,
                            score = score,
                            isAdult = false,
                            imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                            year = if (year > 0) year else null,
                            source = "simkl",
                            realMalId = if (malId > 0) malId else null,
                            titleEnglish = title,
                            titleJapanese = null,
                            tmdbId = if (tmdbId > 0) tmdbId else null
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            android.util.Log.e("SimklApiClient", "search $endpoint failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getTrendingPeriod(typePath: String, period: String = "today"): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val cdnType = when (typePath.lowercase()) {
            "movies", "movie" -> "movies"
            "tv", "shows", "series" -> "tv"
            "anime" -> "anime"
            else -> "anime"
        }
        val periodFile = when (period.lowercase()) {
            "week" -> "week_100.json"
            "month" -> "month_100.json"
            else -> "today_100.json"
        }
        val cdnUrl = "https://data.simkl.in/discover/trending/$cdnType/$periodFile"
        val request = Request.Builder().url(cdnUrl).header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseText = response.body?.string().orEmpty()
                parseSimklArray(responseText, cdnType, 30)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchAdvanced(
        type: String,
        query: String = "",
        subtype: String? = null,
        genre: String? = null,
        country: String? = null,
        year: String? = null,
        sort: String? = null,
        limit: Int = 24
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            return@withContext search(query, type, limit)
        }
        val safeType = when (type.lowercase()) {
            "movies", "movie" -> "movies"
            "tv", "shows", "series" -> "tv"
            else -> "anime"
        }
        val g = genre ?: "all"
        val st = subtype ?: "all"
        val c = country ?: "all"
        val y = year ?: "all"
        val s = sort ?: "rank"
        val url = "https://api.simkl.com/$safeType/genres/$g/$st/$c/$y/$s?client_id=$clientId&limit=$limit"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseText = response.body?.string().orEmpty()
                parseSimklArray(responseText, safeType, limit)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSimklArray(responseText: String, defaultType: String, limit: Int): List<JikanSearchResult> {
        if (responseText.isBlank() || responseText.trim() == "null") return emptyList()
        val jsonArray = JSONArray(responseText)
        val results = mutableListOf<JikanSearchResult>()
        for (i in 0 until minOf(jsonArray.length(), limit)) {
            val obj = jsonArray.getJSONObject(i)
            val title = obj.optString("title", "")
            val ids = obj.optJSONObject("ids") ?: continue
            val simklId = ids.optInt("simkl_id", 0).takeIf { it > 0 } ?: ids.optInt("simkl", 0)
            if (simklId <= 0) continue
            val tmdbId = ids.optInt("tmdb_id", 0).takeIf { it > 0 } ?: ids.optInt("tmdb", 0)
            val malId = ids.optInt("mal", 0)
            val poster = obj.optString("poster", "")
            val year = obj.optInt("year", 0)
            val itemType = obj.optString("type", defaultType)
            val ratingsObj = obj.optJSONObject("ratings")
            val simklRating = ratingsObj?.optJSONObject("simkl")?.optDouble("rating", 0.0) ?: 0.0
            val imdbRating = ratingsObj?.optJSONObject("imdb")?.optDouble("rating", 0.0) ?: 0.0
            val score = if (simklRating > 0.0) (simklRating * 10).toInt()
            else if (imdbRating > 0.0) (imdbRating * 10).toInt()
            else null
            val mediaType = when (itemType.lowercase()) {
                "movie", "movies" -> MediaType.Movie
                "tv", "series", "shows" -> MediaType.TvShow
                else -> MediaType.Anime
            }
            results.add(
                JikanSearchResult(
                    malId = simklId,
                    title = title,
                    subtitle = if (year > 0) year.toString() else "",
                    type = mediaType,
                    total = null,
                    score = score,
                    isAdult = false,
                    imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                    year = if (year > 0) year else null,
                    source = "simkl",
                    realMalId = if (malId > 0) malId else null,
                    titleEnglish = title,
                    titleJapanese = null,
                    tmdbId = if (tmdbId > 0) tmdbId else null
                )
            )
        }
        return results
    }

    suspend fun getTrending(typePath: String): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val cdnType = when (typePath) {
            "movies" -> "movies"
            "tv", "shows" -> "tv"
            "anime" -> "anime"
            else -> "tv"
        }
        val cdnUrl = "https://data.simkl.in/discover/trending/$cdnType/today_100.json"
        val request = Request.Builder()
            .url(cdnUrl)
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("SimklApiClient", "getTrending $cdnType HTTP ${response.code}")
                    return@withContext emptyList()
                }
                val responseText = response.body?.string().orEmpty()
                val jsonArray = JSONArray(responseText)
                val results = mutableListOf<JikanSearchResult>()

                for (i in 0 until minOf(jsonArray.length(), 30)) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", "")
                    val ids = obj.optJSONObject("ids") ?: continue

                    // CDN response'unda ids iÃ§inde "simkl_id" veya "simkl" olabilir (NyanTV referans)
                    val simklId = ids.optInt("simkl_id", 0)
                        .takeIf { it > 0 }
                        ?: ids.optInt("simkl", 0)
                    if (simklId <= 0) continue

                    val tmdbId = ids.optInt("tmdb_id", 0)
                        .takeIf { it > 0 }
                        ?: ids.optInt("tmdb", 0)
                    val malId = ids.optInt("mal", 0)
                    val poster = obj.optString("poster", "")
                    val year = obj.optInt("year", 0)

                    // CDN ratingsObj: ratings â†’ simkl â†’ {rating, votes}
                    val ratingsObj = obj.optJSONObject("ratings")
                    val simklRating = ratingsObj?.optJSONObject("simkl")?.optDouble("rating", 0.0) ?: 0.0
                    val imdbRating = ratingsObj?.optJSONObject("imdb")?.optDouble("rating", 0.0) ?: 0.0
                    val score = if (simklRating > 0.0) (simklRating * 10).toInt()
                                else if (imdbRating > 0.0) (imdbRating * 10).toInt()
                                else null

                    val mediaType = when (cdnType) {
                        "movies" -> MediaType.Movie
                        "tv" -> MediaType.TvShow
                        "anime" -> MediaType.Anime
                        else -> MediaType.TvShow
                    }

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = if (year > 0) year.toString() else "",
                            type = mediaType,
                            total = null,
                            score = score,
                            isAdult = false,
                            imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                            year = if (year > 0) year else null,
                            source = "simkl",
                            realMalId = if (malId > 0) malId else null,
                            titleEnglish = title,
                            titleJapanese = null,
                            tmdbId = if (tmdbId > 0) tmdbId else null
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            android.util.Log.e("SimklApiClient", "getTrending $cdnType failed: ${e.message}", e)
            emptyList()
        }
    }


    suspend fun getDvdMovies(): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.simkl.com/discover/dvd/week.json?client_id=$clientId")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseText = response.body?.string().orEmpty()
                val jsonArray = JSONArray(responseText)
                val results = mutableListOf<JikanSearchResult>()

                for (i in 0 until minOf(jsonArray.length(), 20)) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", "")
                    val ids = obj.optJSONObject("ids") ?: continue
                    val simklId = ids.optInt("simkl", 0)
                    val tmdbId = ids.optInt("tmdb", 0)
                    val poster = obj.optString("poster", "")
                    val year = obj.optInt("year", 0)

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = if (year > 0) year.toString() else "",
                            type = MediaType.Movie,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                            year = if (year > 0) year else null,
                            source = "simkl",
                            realMalId = null,
                            titleEnglish = title,
                            titleJapanese = null,
                            tmdbId = if (tmdbId > 0) tmdbId else null
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            // T3-05: printStackTrace → Log.e
            android.util.Log.e("SimklApiClient", "getDvdMovies failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun search(query: String): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://api.simkl.com/search/mixed?q=$encodedQuery&client_id=$clientId")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseText = response.body?.string().orEmpty()
                if (responseText.trim().startsWith("{")) {
                    android.util.Log.w("SimklApiClient", "Search returned JSON object instead of array: $responseText")
                    return@withContext emptyList()
                }
                val jsonArray = try {
                    JSONArray(responseText)
                } catch (e: Exception) {
                    android.util.Log.e("SimklApiClient", "Failed to parse Simkl search response: ${e.message}")
                    return@withContext emptyList()
                }
                val results = mutableListOf<JikanSearchResult>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", "")
                    val typeStr = obj.optString("type", "")
                    val ids = obj.optJSONObject("ids") ?: continue
                    val simklId = ids.optInt("simkl", 0)
                    val tmdbId = ids.optInt("tmdb", 0)
                    val poster = obj.optString("poster", "")
                    val year = obj.optInt("year", 0)

                    val mediaType = when (typeStr) {
                        "movie" -> MediaType.Movie
                        "show", "tv" -> MediaType.TvShow
                        "anime" -> MediaType.Anime
                        else -> MediaType.Anime
                    }

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = if (year > 0) year.toString() else "",
                            type = mediaType,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                            year = if (year > 0) year else null,
                            source = "simkl",
                            realMalId = ids.optInt("mal", 0).takeIf { it > 0 },
                            titleEnglish = title,
                            titleJapanese = null,
                            tmdbId = if (tmdbId > 0) tmdbId else null
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            // T3-05: printStackTrace → Log.e
            android.util.Log.e("SimklApiClient", "search failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updateWatchlistStatus(
        token: String,
        simklId: Int,
        mediaType: MediaType,
        status: WatchStatus
    ): Boolean = withContext(Dispatchers.IO) {
        val simklStatus = when (status) {
            WatchStatus.Watching -> "watching"
            WatchStatus.Completed -> "completed"
            WatchStatus.Planned -> "plantowatch"
            WatchStatus.Dropped -> "dropped"
            WatchStatus.Paused -> "hold"
            WatchStatus.Repeating -> "watching" // Simkl has no rewatching; treat as watching
        }

        addToList(token, simklId, if (mediaType == MediaType.Movie) "movies" else "shows", simklStatus)
    }

    suspend fun updateEpisodeProgress(
        token: String,
        simklId: Int,
        season: Int,
        episode: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val jsonPayload = JSONObject().apply {
            put("shows", JSONArray().apply {
                put(JSONObject().apply {
                    put("ids", JSONObject().apply {
                        put("simkl", simklId)
                    })
                    put("seasons", JSONArray().apply {
                        put(JSONObject().apply {
                            put("number", season)
                            put("episodes", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("number", episode)
                                })
                            })
                        })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url("https://api.simkl.com/sync/history")
            .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                response.isSuccessful
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
            throw e
        } catch (e: Exception) {
            // T3-05: printStackTrace → Log.e
            android.util.Log.e("SimklApiClient", "updateEpisodeProgress failed: ${e.message}", e)
            false
        }
    }

    // â”€â”€ KullanÄ±cÄ± Listesi YÃ¶netimi (NyanTV SimklService.kt referans) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * KullanÄ±cÄ±nÄ±n tÃ¼m izleme listesini Ã§eker.
     * type: "shows", "movies", "anime"
     * NyanTV referans: GET /sync/all-items/{type}
     */
    suspend fun getUserWatchlist(token: String, type: String): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.simkl.com/sync/all-items/$type?extended=full")
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                if (!response.isSuccessful) return@withContext emptyList()
                val root = JSONObject(response.body?.string().orEmpty())
                val itemsArray = root.optJSONArray(type) ?: return@withContext emptyList()
                val results = mutableListOf<JikanSearchResult>()

                for (i in 0 until itemsArray.length()) {
                    val entry = itemsArray.getJSONObject(i)
                    // Both shows and anime contain a nested "show" object.
                    val mediaKey = SimklSyncContract.readMediaKey(type)
                    val mediaObj = entry.optJSONObject(mediaKey) ?: continue
                    val ids = mediaObj.optJSONObject("ids") ?: continue
                    val simklId = ids.optInt("simkl", 0).takeIf { it > 0 } ?: continue
                    val tmdbId = ids.optInt("tmdb", 0)
                    val malId = ids.optInt("mal", 0)
                    val title = mediaObj.optString("title", "")
                    val poster = mediaObj.optString("poster", "")
                    val year = mediaObj.optInt("year", 0)
                    val status = entry.optString("status", "")
                    val watchedEps = entry.optInt("watched_episodes_count", 0)
                    val totalEps = entry.optInt("total_episodes_count", 0)

                    val mediaType = when (type) {
                        "movies" -> MediaType.Movie
                        "shows" -> MediaType.TvShow
                        "anime" -> MediaType.Anime
                        else -> MediaType.TvShow
                    }

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = simklStatusToDisplayString(status),
                            type = mediaType,
                            total = if (totalEps > 0) totalEps else null,
                            score = null,
                            isAdult = false,
                            imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                            year = if (year > 0) year else null,
                            source = "simkl",
                            realMalId = if (malId > 0) malId else null,
                            titleEnglish = title,
                            titleJapanese = null,
                            tmdbId = if (tmdbId > 0) tmdbId else null
                        )
                    )
                }
                results
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SimklApiClient", "getUserWatchlist $type failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Simkl Keşfet / En İyiler listesini çeker (Örn. anime/best/all-time, tv/best/all-time).
     */
    suspend fun getBestMedia(endpoint: String, defaultType: MediaType, limit: Int = 20): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val url = "https://api.simkl.com/$endpoint?client_id=$clientId&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseText = response.body?.string().orEmpty()
                if (responseText.isBlank()) return@withContext emptyList()
                val array = JSONArray(responseText)
                val results = mutableListOf<JikanSearchResult>()
                for (i in 0 until minOf(array.length(), limit)) {
                    val obj = array.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "")
                    if (title.isBlank()) continue
                    val ids = obj.optJSONObject("ids")
                    val simklId = ids?.optInt("simkl_id", 0)?.takeIf { it > 0 } ?: (i + 1)
                    val poster = obj.optString("poster", "")
                    val imageUrl = if (poster.isNotBlank()) "https://simkl.in/posters/${poster}_m.webp" else null
                    val fanart = obj.optString("fanart", "")
                    val backdropUrl = if (fanart.isNotBlank()) "https://simkl.in/fanart/${fanart}_medium.webp" else null
                    val year = obj.optInt("year", 0).takeIf { it > 0 }
                    val ratings = obj.optJSONObject("ratings")
                    val ratingDouble = ratings?.optJSONObject("simkl")?.optDouble("rating", 0.0) ?: ratings?.optJSONObject("mal")?.optDouble("rating", 0.0) ?: 0.0
                    val score = if (ratingDouble > 0.0) (ratingDouble * 10).toInt() else null

                    val subtitleParts = buildList {
                        when (defaultType) {
                            MediaType.Anime -> {
                                add("Anime")
                                val animeType = obj.optString("anime_type", "")
                                if (animeType.equals("movie", ignoreCase = true)) add("Film") else add("Dizi")
                            }
                            MediaType.TvShow -> add("Dizi")
                            MediaType.Movie -> add("Film")
                            else -> {}
                        }
                        if (year != null && year > 0) add(year.toString())
                    }
                    val subtitle = subtitleParts.joinToString(", ").ifBlank { if (year != null) "$year" else "Simkl" }

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = subtitle,
                            type = defaultType,
                            total = null,
                            score = score,
                            isAdult = false,
                            imageUrl = imageUrl,
                            year = year,
                            source = "simkl",
                            realMalId = ids?.optInt("mal", 0)?.takeIf { it > 0 },
                            backdropUrl = backdropUrl
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            android.util.Log.e("SimklApiClient", "getBestMedia $endpoint failed", e)
            emptyList()
        }
    }

    /**
     * Listeye iÃ§erik ekler veya durumunu gÃ¼nceller.
     * type: "shows" veya "movies" veya "anime"
     * status: "watching", "plantowatch", "completed", "hold", "dropped"
     * NyanTV referans: POST /sync/add-to-list
     */
    suspend fun addToList(
        token: String,
        simklId: Int,
        type: String,
        status: String,
        malId: Int? = null,
        tmdbId: Int? = null,
        aniListId: Int? = null,
        kitsuId: Int? = null,
        title: String? = null,
        year: Int? = null
    ): Boolean = addToListBatchDetailed(token, listOf(SimklBatchEntry(
        type = type, status = status, simklId = simklId, malId = malId,
        tmdbId = tmdbId, aniListId = aniListId, kitsuId = kitsuId, title = title, year = year
    ))).isSuccess

    data class SimklBatchEntry(
        val type: String, // "anime", "movies", "shows"
        val status: String,
        val simklId: Int = 0,
        val malId: Int? = null,
        val tmdbId: Int? = null,
        val aniListId: Int? = null,
        val kitsuId: Int? = null,
        val title: String? = null,
        val year: Int? = null,
        val progress: Int = 0,
        val score: Int? = null
    )

    /**
     * [isSuccess] is only true when every requested item was confirmed. A partially matched batch
     * still carries [addedCount] and the echoed [notFoundItems], so callers must not treat it as a
     * whole-batch failure. [transportFailed] marks HTTP/network level failures where nothing was
     * written at all.
     */
    data class SimklBatchResponse(
        val isSuccess: Boolean,
        val addedCount: Int = 0,
        val notFoundCount: Int = 0,
        val errorMessage: String? = null,
        val notFoundItems: List<SimklSyncContract.UnmatchedItem> = emptyList(),
        val transportFailed: Boolean = false
    ) {
        val isPartial: Boolean get() = !transportFailed && addedCount > 0 && notFoundCount > 0
    }

    private fun buildSimklIds(entry: SimklBatchEntry): JSONObject {
        return JSONObject().apply {
            if (entry.simklId > 0) put("simkl", entry.simklId)
            if (entry.malId != null && entry.malId > 0 && entry.malId < 100_000_000) {
                put("mal", entry.malId)
            }
            if (entry.tmdbId != null && entry.tmdbId > 0) {
                put("tmdb", entry.tmdbId)
            }
            if (entry.aniListId != null && entry.aniListId > 0) {
                put("anilist", entry.aniListId)
            }
            if (entry.kitsuId != null && entry.kitsuId > 0) {
                put("kitsu", entry.kitsuId)
            }
        }
    }

    /**
     * Simkl toplu senkronizasyon (POST /sync/add-to-list).
     * Anime is a read category, not a write envelope: all series are sent under shows.
     */
    suspend fun addToListBatchDetailed(
        token: String,
        entries: List<SimklBatchEntry>
    ): SimklBatchResponse = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext SimklBatchResponse(isSuccess = true)
        val showsArray = JSONArray()
        val moviesArray = JSONArray()

        entries.forEach { entry ->
            val idObj = buildSimklIds(entry)
            val itemObj = JSONObject().put("to", entry.status)
            if (idObj.length() > 0) {
                itemObj.put("ids", idObj)
            }
            if (!entry.title.isNullOrBlank()) {
                itemObj.put("title", entry.title)
            }
            if (entry.year != null && entry.year > 1900) {
                itemObj.put("year", entry.year)
            }
            if (idObj.length() > 0 || !entry.title.isNullOrBlank()) {
                when (SimklSyncContract.writeKey(entry.type)) {
                    "movies" -> moviesArray.put(itemObj)
                    else -> showsArray.put(itemObj)
                }
            }
        }

        val payloadObj = JSONObject()
        if (showsArray.length() > 0) payloadObj.put("shows", showsArray)
        if (moviesArray.length() > 0) payloadObj.put("movies", moviesArray)

        if (payloadObj.length() == 0) {
            return@withContext SimklBatchResponse(
                isSuccess = false,
                errorMessage = "Gönderilebilir Simkl kimliği/başlığı yok",
                transportFailed = true
            )
        }

        postSyncEnvelope(
            endpoint = "add-to-list",
            token = token,
            payload = payloadObj,
            expectedCount = entries.size,
            logTag = "addToListBatchDetailed"
        )
    }

    suspend fun addToListBatch(
        token: String,
        entries: List<SimklBatchEntry>
    ): Boolean = addToListBatchDetailed(token, entries).isSuccess

    /**
     * Shared POST + receipt handling for /sync/add-to-list, /sync/history and /sync/ratings.
     * Retries 429 and transient transport errors; never reports a partial receipt as a transport failure.
     */
    private suspend fun postSyncEnvelope(
        endpoint: String,
        token: String,
        payload: JSONObject,
        expectedCount: Int,
        logTag: String
    ): SimklBatchResponse {
        var attempt = 0
        var lastError: String? = null
        while (attempt < 3) {
            attempt++
            val request = Request.Builder()
                .url("https://api.simkl.com/sync/$endpoint?client_id=$clientId&app-name=Kitsugi&app-version=2.4")
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer $token")
                .header("simkl-api-key", clientId)
                .header("Content-Type", "application/json")
                .header("User-Agent", "KitsugiApp/2.4")
                .build()

            try {
                val callResult = client.newCall(request).execute().use { response ->
                    checkResponseAndThrow(response)
                    if (response.code == 429) {
                        val waitMs = response.header("Retry-After")?.toLongOrNull()
                            ?.coerceIn(1L, 3600L)?.times(1000L) ?: (2500L * attempt)
                        return@use Pair(
                            waitMs,
                            SimklBatchResponse(isSuccess = false, errorMessage = "HTTP 429 Rate Limit", transportFailed = true)
                        )
                    }
                    if (!response.isSuccessful) {
                        val bodySnippet = response.body?.string().orEmpty().take(200).replace('\n', ' ')
                        return@use Pair(
                            0L,
                            SimklBatchResponse(
                                isSuccess = false,
                                errorMessage = "HTTP ${response.code}${if (bodySnippet.isNotBlank()) " · $bodySnippet" else ""}",
                                transportFailed = true
                            )
                        )
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    val receipt = SimklSyncContract.receipt(bodyStr)
                    val complete = receipt.notFound == 0 && receipt.added >= expectedCount
                    Pair(
                        0L,
                        SimklBatchResponse(
                            isSuccess = complete,
                            addedCount = receipt.added,
                            notFoundCount = receipt.notFound,
                            notFoundItems = receipt.unmatched,
                            errorMessage = if (complete) null else
                                "Simkl $expectedCount öğeden ${receipt.added} tanesini onayladı; ${receipt.notFound} eşleşmedi"
                        )
                    )
                }

                if (callResult.first == 0L) {
                    return callResult.second
                }
                lastError = callResult.second.errorMessage
                if (attempt < 3) kotlinx.coroutines.delay(callResult.first)
            } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                // Receipt contract rejected the body (empty/no-op receipt). Retrying the same payload
                // cannot change the answer; surface it once as a failed write.
                android.util.Log.w("SimklApiClient", "$logTag receipt rejected: ${e.message}")
                return SimklBatchResponse(isSuccess = false, errorMessage = e.message, transportFailed = true)
            } catch (e: org.json.JSONException) {
                android.util.Log.w("SimklApiClient", "$logTag malformed receipt: ${e.message}")
                return SimklBatchResponse(isSuccess = false, errorMessage = "Simkl yanıtı çözümlenemedi: ${e.message}", transportFailed = true)
            } catch (e: Exception) {
                android.util.Log.e("SimklApiClient", "$logTag exception", e)
                lastError = e.message ?: e.javaClass.simpleName
                if (attempt >= 3) {
                    return SimklBatchResponse(isSuccess = false, errorMessage = lastError, transportFailed = true)
                }
                kotlinx.coroutines.delay(1500L)
            }
        }
        return SimklBatchResponse(
            isSuccess = false,
            errorMessage = "Simkl isteği başarısız oldu (deneme sınırı aşıldı${if (lastError != null) ": $lastError" else ""})",
            transportFailed = true
        )
    }

    /**
     * Simkl toplu izleme geçmişi / bölüm ilerlemesi (POST /sync/history).
     * Returns a per-item receipt; unmatched items are echoed in [SimklBatchResponse.notFoundItems].
     */
    suspend fun historyBatchReceipt(
        token: String,
        entries: List<SimklBatchEntry>
    ): SimklBatchResponse = withContext(Dispatchers.IO) {
        val filtered = entries.filter { it.progress > 0 }
        if (filtered.isEmpty()) return@withContext SimklBatchResponse(isSuccess = true)
        // A TV aggregate count cannot be mapped to seasons without an episode catalogue.
        // Never invent S01E<total>. Report unsupported progress instead of corrupting history.
        if (filtered.any { it.type == "shows" || it.type == "tv" }) {
            return@withContext SimklBatchResponse(
                isSuccess = false,
                errorMessage = "Dizi bölüm ilerlemesi sezon/bölüm eşlemesi olmadan gönderilemez",
                transportFailed = true
            )
        }
        val shows = JSONArray()
        val movies = JSONArray()
        filtered.forEach { entry ->
            val item = JSONObject().put("ids", buildSimklIds(entry))
            if (!entry.title.isNullOrBlank()) item.put("title", entry.title)
            if (entry.year != null && entry.year > 0) item.put("year", entry.year)
            if (SimklSyncContract.writeKey(entry.type) == "movies") {
                movies.put(item)
            } else {
                item.put("episodes", SimklSyncContract.animeEpisodes(entry.progress))
                shows.put(item)
            }
        }
        val payload = JSONObject()
        if (shows.length() > 0) payload.put("shows", shows)
        if (movies.length() > 0) payload.put("movies", movies)

        // The history receipt counts accepted shows/movies/episodes together, so "complete" means
        // no unmatched items rather than an exact item count.
        val result = postSyncEnvelope(
            endpoint = "history",
            token = token,
            payload = payload,
            expectedCount = 1,
            logTag = "historyBatchDetailed"
        )
        if (result.transportFailed) result else result.copy(
            isSuccess = result.notFoundCount == 0 && result.addedCount > 0,
            errorMessage = if (result.notFoundCount == 0 && result.addedCount > 0) null else
                "Simkl izleme geçmişinde ${result.notFoundCount} kayıt eşleşmedi"
        )
    }

    suspend fun historyBatchDetailed(
        token: String,
        entries: List<SimklBatchEntry>
    ): Boolean = historyBatchReceipt(token, entries).isSuccess

    /**
     * Simkl toplu puanlama (POST /sync/ratings).
     * Puanlar 1–10 arasına normalize edilir.
     */
    suspend fun ratingsBatchReceipt(
        token: String,
        entries: List<SimklBatchEntry>
    ): SimklBatchResponse = withContext(Dispatchers.IO) {
        val filtered = entries.filter { it.score != null && it.score > 0 }
        if (filtered.isEmpty()) return@withContext SimklBatchResponse(isSuccess = true)

        val showsArray = JSONArray()
        val moviesArray = JSONArray()
        var sentCount = 0

        filtered.forEach { entry ->
            val scoreVal = entry.score ?: return@forEach
            val normalizedRating = if (scoreVal > 10) {
                kotlin.math.round(scoreVal / 10.0).toInt().coerceIn(1, 10)
            } else {
                scoreVal.coerceIn(1, 10)
            }

            val idObj = buildSimklIds(entry)
            if (idObj.length() == 0 && entry.title.isNullOrBlank()) return@forEach

            val item = JSONObject().apply {
                put("ids", idObj)
                put("rating", normalizedRating)
                if (!entry.title.isNullOrBlank()) put("title", entry.title)
                if (entry.year != null && entry.year > 1900) put("year", entry.year)
            }
            sentCount++

            when (SimklSyncContract.writeKey(entry.type)) {
                "movies" -> moviesArray.put(item)
                else -> showsArray.put(item)
            }
        }

        val payload = JSONObject()
        if (showsArray.length() > 0) payload.put("shows", showsArray)
        if (moviesArray.length() > 0) payload.put("movies", moviesArray)

        if (payload.length() == 0) return@withContext SimklBatchResponse(isSuccess = true)

        postSyncEnvelope(
            endpoint = "ratings",
            token = token,
            payload = payload,
            expectedCount = sentCount,
            logTag = "ratingsBatchDetailed"
        )
    }

    suspend fun ratingsBatchDetailed(
        token: String,
        entries: List<SimklBatchEntry>
    ): Boolean = ratingsBatchReceipt(token, entries).isSuccess

    /**
     * Listeden içeriği siler.
     * NyanTV referans: POST /sync/history/remove
     */
    suspend fun removeFromList(
        token: String,
        simklId: Int,
        type: String,
        malId: Int? = null,
        tmdbId: Int? = null,
        aniListId: Int? = null,
        kitsuId: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val mediaKey = when (type.lowercase()) {
            "movies", "movie" -> "movies"
            "anime" -> "shows"
            else -> "shows"
        }
        val idObj = JSONObject().apply {
            if (simklId > 0) put("simkl", simklId)
            if (malId != null && malId > 0 && malId < 100_000_000) put("mal", malId)
            if (aniListId != null && aniListId > 0) put("anilist", aniListId)
            if (kitsuId != null && kitsuId > 0) put("kitsu", kitsuId)
            if (tmdbId != null && tmdbId > 0) put("tmdb", tmdbId)
        }
        val itemObj = JSONObject().put("ids", idObj)
        val payload = JSONObject().put(mediaKey, JSONArray().put(itemObj)).toString()

        val request = Request.Builder()
            .url("https://api.simkl.com/sync/history/remove")
            .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .header("Content-Type", "application/json")
            .header("User-Agent", "KitsugiApp/2.4")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                response.isSuccessful
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * KullanÄ±cÄ± profilini Ã§eker (ad, avatar, id).
     * NyanTV referans: POST /users/settings
     */
    suspend fun getUserProfile(token: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.simkl.com/users/settings")
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                if (!response.isSuccessful) null
                else JSONObject(response.body?.string().orEmpty())
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // â”€â”€ Status yardÄ±mcÄ±larÄ± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Simkl status string'ini WatchStatus'a Ã§evirir (NyanTV simklStatusToAL referans) */
    fun simklStatusToWatchStatusString(simklStatus: String?): String = when (simklStatus) {
        "watching"    -> "CURRENT"
        "completed"   -> "COMPLETED"
        "hold"        -> "PAUSED"
        "dropped"     -> "DROPPED"
        "plantowatch" -> "PLANNING"
        else          -> "PLANNING"
    }

    /** WatchStatus string'ini Simkl status'una Ã§evirir (NyanTV alStatusToSimkl referans) */
    fun watchStatusStringToSimkl(status: String?): String = when (status) {
        "CURRENT"   -> "watching"
        "COMPLETED" -> "completed"
        "PAUSED"    -> "hold"
        "DROPPED"   -> "dropped"
        "PLANNING"  -> "plantowatch"
        else        -> "plantowatch"
    }

    private fun simklStatusToDisplayString(status: String?) = when (status) {
        "watching"    -> "İzleniyor"
        "completed"   -> "Tamamlandı"
        "hold"        -> "Beklemede"
        "dropped"     -> "Bırakıldı"
        "plantowatch" -> "Planlandı"
        else          -> status.orEmpty()
    }

    // ── Delta Sync: /sync/activities ─────────────────────────────────────────

    /**
     * SIMKL sync/activities endpoint'ini çeker.
     * Delta sync gate: timestamp değişmemişse library yeniden çekilmez.
     * Bkz: https://api.simkl.org/guides/sync
     */
    suspend fun getActivities(token: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.simkl.com/sync/activities")
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                when {
                    response.code == 429 -> throw com.kitsugi.animelist.data.repository.SimklRateLimitException("Rate limited (429)")
                    !response.isSuccessful -> null
                    else -> JSONObject(response.body?.string().orEmpty())
                }
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) { throw e }
        catch (e: com.kitsugi.animelist.data.repository.SimklRateLimitException) { throw e }
        catch (e: Exception) {
            android.util.Log.e("SimklApiClient", "getActivities failed: ${e.message}")
            null
        }
    }

    // ── Rating ───────────────────────────────────────────────────────────────

    /**
     * 1–10 arası puan gönderir.
     * NyanTV referans: POST /sync/ratings
     */
    suspend fun setRating(
        token: String,
        simklId: Int,
        mediaType: MediaType,
        rating: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val typeKey = when (mediaType) {
            MediaType.Movie -> "movies"
            MediaType.Anime -> "shows"
            else            -> "shows"
        }
        val idObj   = JSONObject().put("simkl", simklId)
        val itemObj = JSONObject().put("ids", idObj).put("rating", rating)
        val payload = JSONObject().put(typeKey, JSONArray().put(itemObj)).toString()

        val request = Request.Builder()
            .url("https://api.simkl.com/sync/ratings")
            .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                response.isSuccessful
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Puanı kaldırır.
     * NyanTV referans: POST /sync/ratings/remove
     */
    suspend fun removeRating(
        token: String,
        simklId: Int,
        mediaType: MediaType
    ): Boolean = withContext(Dispatchers.IO) {
        val typeKey = when (mediaType) {
            MediaType.Movie -> "movies"
            MediaType.Anime -> "shows"
            else            -> "shows"
        }
        val idObj   = JSONObject().put("simkl", simklId)
        val itemObj = JSONObject().put("ids", idObj)
        val payload = JSONObject().put(typeKey, JSONArray().put(itemObj)).toString()

        val request = Request.Builder()
            .url("https://api.simkl.com/sync/ratings/remove")
            .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", clientId)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                checkResponseAndThrow(response)
                response.isSuccessful
            }
        } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // ── Calendar (CDN) ───────────────────────────────────────────────────────

    /**
     * SIMKL CDN takvim JSON'ını çeker (auth gerekmez).
     * type: "tv", "anime", "movie_release"
     */
    @Deprecated(
        "Eski (v2 olmayan) data.simkl.in takvim dosyalarını okur ve sonucu ilk 50 öğeyle " +
            "sınırlar; bu dosyalar 1 Şubat 2027'de güncellenmeyi bırakacak. " +
            "Bildirim akışları için SimklCalendarClient kullanın."
    )
    suspend fun getCalendar(type: String): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val cdnType = when (type) {
            "movie", "movies", "movie_release" -> "movie_release"
            "anime"                            -> "anime"
            else                               -> "tv"
        }
        val url = "https://data.simkl.in/calendar/${cdnType}.json"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val jsonArray = JSONArray(response.body?.string().orEmpty())
                val results = mutableListOf<JikanSearchResult>()

                for (i in 0 until minOf(jsonArray.length(), 50)) {
                    val obj   = jsonArray.getJSONObject(i)
                    val ids   = obj.optJSONObject("ids") ?: continue
                    val simklId = ids.optInt("simkl_id", 0).takeIf { it > 0 }
                        ?: ids.optInt("simkl", 0).takeIf { it > 0 } ?: continue
                    val title = obj.optString("title", "")
                    val poster = obj.optString("poster", "")
                    val year  = obj.optInt("year", 0)
                    val tmdbId = ids.optInt("tmdb", 0)

                    val mediaType = when (cdnType) {
                        "movie_release" -> MediaType.Movie
                        "anime"         -> MediaType.Anime
                        else            -> MediaType.TvShow
                    }

                    results.add(
                        JikanSearchResult(
                            malId = simklId,
                            title = title,
                            subtitle = if (year > 0) year.toString() else "",
                            type = mediaType,
                            total = null,
                            score = null,
                            isAdult = false,
                            imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                            year = if (year > 0) year else null,
                            source = "simkl",
                            realMalId = null,
                            titleEnglish = title,
                            titleJapanese = null,
                            tmdbId = if (tmdbId > 0) tmdbId else null
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            android.util.Log.e("SimklApiClient", "getCalendar $cdnType failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Simkl /search/id ve /search API'lerini kullanarak MAL ID, AniList ID, TMDB ID veya başlık üzerinden Simkl ID'sini çözer.
     * Unified senkronizasyon ve ekleme akışında, doğru simklId'yi bulmak için kullanılır.
     *
     * @param malId MyAnimeList media ID (anime için)
     * @param tmdbId TMDB media ID (dizi/film için)
     * @param aniListId AniList media ID
     * @param title Başlık (fuzzy arama için fallback)
     * @param year Yapım yılı (eşleştirme doğrulaması için)
     * @param mediaType Medya tipi (Anime, TvShow, Movie)
     * @return Çözümlenen Simkl ID veya null
     */
    suspend fun lookupSimklId(
        malId: Int? = null,
        tmdbId: Int? = null,
        aniListId: Int? = null,
        title: String? = null,
        year: Int? = null,
        mediaType: MediaType? = null
    ): Int? = withContext(Dispatchers.IO) {
        try {
            // 1. MAL ID ile dene (anime için doğrudan eşleşme)
            if (malId != null && malId > 0 && malId < 100_000_000) {
                val result = lookupByIdParam("mal", malId)
                if (result != null && result > 0) return@withContext result
            }
            // 2. AniList ID ile dene
            if (aniListId != null && aniListId > 0) {
                val result = lookupByIdParam("anilist", aniListId)
                if (result != null && result > 0) return@withContext result
            }
            // 3. TMDB ID ile dene (film / dizi / anime)
            if (tmdbId != null && tmdbId > 0) {
                val result = lookupByIdParam("tmdb", tmdbId)
                if (result != null && result > 0) return@withContext result
            }
            // 4. Başlık ile Simkl'de fuzzy ara
            if (!title.isNullOrBlank()) {
                val searchType = when (mediaType) {
                    MediaType.Movie -> "movie"
                    MediaType.TvShow -> "tv"
                    else -> "anime"
                }
                val results = search(query = title, type = searchType, limit = 5)
                fun clean(s: String?) = s?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: ""
                val cTarget = clean(title)

                val matched = results.firstOrNull { res ->
                    if (res.isAdult) return@firstOrNull false
                    val cRes = clean(res.title)
                    val cResEng = clean(res.titleEnglish)
                    val isTitleMatch = (cTarget.isNotEmpty() && (cTarget == cRes || (cResEng.isNotEmpty() && cTarget == cResEng)))
                    val isYearMatch = year == null || res.year == null || kotlin.math.abs(year - res.year) <= 1
                    isTitleMatch && isYearMatch
                }
                val foundSimklId = matched?.malId?.takeIf { it > 0 }
                if (foundSimklId != null) return@withContext foundSimklId
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("SimklApiClient", "lookupSimklId failed malId=$malId tmdbId=$tmdbId aniListId=$aniListId: ${e.message}")
            null
        }
    }

    /** Simkl /search/id endpoint'i ile belirtilen parametre ve ID'den Simkl ID'sini çözer. */
    private fun lookupByIdParam(param: String, id: Int): Int? {
        val url = "https://api.simkl.com/search/id?$param=$id&client_id=$clientId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("simkl-api-key", clientId)
            .header("User-Agent", "KitsugiApp/2.4")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                if (text.isBlank() || text.trim() == "null") return null
                val arr = JSONArray(text)
                if (arr.length() == 0) return null
                val obj = arr.optJSONObject(0) ?: return null
                val ids = obj.optJSONObject("ids") ?: return null
                val simklId = ids.optInt("simkl_id", 0).takeIf { it > 0 }
                    ?: ids.optInt("simkl", 0).takeIf { it > 0 }
                simklId
            }
        } catch (e: Exception) {
            android.util.Log.w("SimklApiClient", "lookupByIdParam $param=$id failed: ${e.message}")
            null
        }
    }
}

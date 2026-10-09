package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.json.JSONObject
import okhttp3.Request
import com.kitsugi.animelist.utils.*

/**
 * Simkl API'sinden medya detaylarını çeker.
 */
internal object KitsugiSimklDetailClient {

    suspend fun fetchSimklDetailDirect(
        simklId: Int,
        mediaType: MediaType
    ): KitsugiMediaDetail? {
        // T3-01: BuildConfig'den alınır
        val clientId = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID
        val typePath = when (mediaType) {
            MediaType.Movie -> "movies"
            MediaType.TvShow -> "tv"
            MediaType.Anime -> "anime"
            else -> "anime"
        }
        val urlString = "https://api.simkl.com/$typePath/$simklId?client_id=$clientId&extended=full"
        val request = Request.Builder()
            .url(urlString)
            .header("Accept", "application/json")
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                val obj = JSONObject(text)

                val overview = obj.optString("overview", "")
                val poster = obj.optString("poster", "")
                val year = obj.optInt("year", 0)
                val runtime = obj.optInt("runtime", 0)
                val genresArray = obj.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until genresArray.length()) {
                        genresList.add(genresArray.getString(i))
                    }
                }

                val ids = obj.optJSONObject("ids")
                val tmdbId = ids?.optInt("tmdb", 0) ?: 0
                val realMalId = ids?.optInt("mal", 0) ?: 0

                // ── Başlık zinciri: yerelleştirilmiş (Türkçe) → İngilizce → Romaji ──────
                // Simkl `title` alanı bazı kayıtlarda Japonca/Çince gelebilir; bu yüzden
                // önce TMDB'den aktif dildeki (Türkçe) başlık denenir, ardından Simkl'in
                // İngilizce/romaji alanlarına inilir.
                val simklTitle = obj.optString("title", "")
                val simklEnglishTitle = MediaTitleResolver.nonBlank(
                    obj.optString("en_title"),
                    obj.optString("title_en"),
                    obj.optString("title_english")
                )
                val simklRomaji = MediaTitleResolver.nonBlank(
                    obj.optString("title_romaji"),
                    obj.optString("romaji")
                )
                val simklNative = MediaTitleResolver.nonBlank(
                    obj.optString("ja_title"),
                    obj.optString("title_native"),
                    obj.optString("title_japanese")
                )

                val isTmdbMovie = mediaType == MediaType.Movie ||
                    obj.optString("anime_type", "").equals("movie", ignoreCase = true)
                val localizedTitle = if (tmdbId > 0) fetchTmdbLocalizedTitle(tmdbId, isTmdbMovie) else null

                val englishCandidate = MediaTitleResolver.latin(simklEnglishTitle)
                    ?: MediaTitleResolver.latin(simklTitle)
                val resolvedTitle = MediaTitleResolver.resolve(
                    localized = localizedTitle,
                    english = englishCandidate,
                    romaji = simklRomaji,
                    original = simklNative ?: simklTitle
                )
                val resolvedTitleEnglish = MediaTitleResolver.resolveEnglish(
                    localized = localizedTitle,
                    english = englishCandidate,
                    romaji = simklRomaji,
                    original = simklNative
                ) ?: resolvedTitle
                val resolvedTitleJapanese = listOfNotNull(
                    simklNative,
                    simklTitle.takeIf { MediaTitleResolver.hasCjk(it) }
                ).firstOrNull { it.isNotBlank() && MediaTitleResolver.hasCjk(it) }

                val ratingObj = obj.optJSONObject("ratings")?.optJSONObject("simkl")
                val ratingScore = ratingObj?.optDouble("rating", 0.0) ?: 0.0

                // Simkl pictures: büyük poster (_w.jpg) + orta poster (_m.jpg)
                val simklPictures = if (poster.isNotEmpty()) {
                    listOfNotNull(
                        "https://simkl.in/posters/${poster}_w.jpg",
                        "https://simkl.in/posters/${poster}_m.jpg"
                    )
                } else emptyList()

                KitsugiMediaDetail(
                    synopsis = overview,
                    genres = genresList.toTurkishGenres(),
                    status = obj.optString("status", "").toTurkishStatus(),
                    season = null,
                    sourceMaterial = null,
                    studios = emptyList(),
                    producers = emptyList(),
                    rating = if (ratingScore > 0.0) ratingScore.toString() else null,
                    broadcast = null,
                    episodeDuration = if (runtime > 0) "$runtime min".toTurkishDuration() else null,
                    // Simkl tarihleri: film → released (YYYY-MM-DD); dizi/anime → first_aired / last_aired
                    // (ISO UTC). Anime tarihleri JST referanslı yorumlanır (MAL/AniList ile tutarlı).
                    startDate = com.kitsugi.animelist.utils.KitsugiReleaseDates.isoToLocalDateString(
                        raw = if (isTmdbMovie) obj.optString("released", "") else obj.optString("first_aired", ""),
                        zone = if (mediaType == MediaType.Anime) com.kitsugi.animelist.utils.KitsugiReleaseDates.JST_ZONE
                               else java.time.ZoneOffset.UTC
                    ),
                    endDate = if (isTmdbMovie) null else com.kitsugi.animelist.utils.KitsugiReleaseDates.isoToLocalDateString(
                        raw = obj.optString("last_aired", ""),
                        zone = if (mediaType == MediaType.Anime) com.kitsugi.animelist.utils.KitsugiReleaseDates.JST_ZONE
                               else java.time.ZoneOffset.UTC
                    ),
                    titleEnglish = resolvedTitleEnglish,
                    titleJapanese = resolvedTitleJapanese,
                    // TMDB/Simkl tarafında ayrı bir romaji alanı yoktur; detay ekranları
                    // birincil başlık olarak yerelleştirilmiş (Türkçe) başlığı korusun diye
                    // titleRomaji null bırakılır, alternatifler synonyms'e taşınır.
                    titleRomaji = null,
                    titleNative = simklNative,
                    // "Diğer Adlar" bölümünde ekrana düşeceği için yalnızca Latin alternatifler
                    synonyms = listOfNotNull(simklRomaji, simklTitle.takeIf { it != resolvedTitle })
                        .mapNotNull { MediaTitleResolver.latin(it) }
                        .distinct(),
                    openings = emptyList(),
                    endings = emptyList(),
                    trailerUrl = null,
                    title = resolvedTitle,
                    imageUrl = if (poster.isNotEmpty()) "https://simkl.in/posters/${poster}_m.jpg" else null,
                    score = if (ratingScore > 0.0) (ratingScore * 10).toInt() else null,
                    year = if (year > 0) year else null,
                    total = null,
                    isAdult = com.kitsugi.animelist.data.remote.SimklAdultFlags.isAdult(obj),
                    realMalId = if (realMalId > 0) realMalId else null,
                    tags = emptyList(),
                    externalLinks = emptyList(),
                    streamingLinks = emptyList(),
                    streamingEpisodes = emptyList(),
                    tmdbId = if (tmdbId > 0) tmdbId else null,
                    tmdbSeason = 1,
                    pictures = simklPictures
                )
            }
        } catch (e: Exception) {
            // T3-05: printStackTrace → Log.e
            android.util.Log.e("KitsugiSimklDetailClient", "fetchSimklDetailDirect simklId=$simklId failed: ${e.message}", e)
            null
        }
    }

    /**
     * Simkl kaydının TMDB karşılığından aktif dildeki (varsayılan: Türkçe) başlığı çeker.
     *
     * Simkl API'si Türkçe başlık sunmadığı için "Türkçe → İngilizce → Romaji" zincirinin
     * ilk adımı TMDB üzerinden tamamlanır. Tek bir hafif istek yapılır; aktif dil zaten
     * İngilizce ise ya da TMDB anahtarı yoksa istek atlanır.
     */
    private fun fetchTmdbLocalizedTitle(tmdbId: Int, isMovie: Boolean): String? {
        val language = TmdbApiClient.getActiveLanguage()
        if (language.startsWith("en", ignoreCase = true)) return null
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return null
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId?api_key=$apiKey&language=$language"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                val obj = JSONObject(text)
                val title = if (isMovie) obj.optString("title", "") else obj.optString("name", "")
                MediaTitleResolver.latin(title)
            }
        } catch (e: Exception) {
            android.util.Log.w("KitsugiSimklDetailClient", "TMDB yerelleştirilmiş başlık alınamadı (tmdb=$tmdbId): ${e.message}")
            null
        }
    }
}

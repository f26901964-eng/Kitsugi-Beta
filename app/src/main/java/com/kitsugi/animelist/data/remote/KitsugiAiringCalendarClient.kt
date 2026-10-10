package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.utils.MediaTitleResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

/**
 * AniList `airingSchedules` GraphQL endpoint'inden haftalık yayın takvimini çeker.
 * Sonuç Map<Int, List<AiringEntry>>: key = Calendar.DAY_OF_WEEK
 */
class KitsugiAiringCalendarClient {

    suspend fun fetchWeeklySchedule(accessToken: String? = null, preferredSource: String? = null): Map<Int, List<AiringEntry>> {
        return withContext(Dispatchers.IO) {
            when (preferredSource) {
                "tmdb" -> fetchTmdbWeeklySchedule()
                // "Tümü" keşfet modu: haftalık takvim tüm kaynakların BİRLEŞİMİ —
                // AniList bölüm takvimi + TMDB vizyon/bölüm tarihleri tek takvimde.
                "all" -> mergeWeeklySchedules(
                    fetchAniListWeeklySchedule(accessToken),
                    fetchTmdbWeeklySchedule()
                )
                else -> fetchAniListWeeklySchedule(accessToken)
            }
        }
    }

    /** AniList `airingSchedules` haftalık takvimi (eski varsayılan davranış). */
    private suspend fun fetchAniListWeeklySchedule(accessToken: String?): Map<Int, List<AiringEntry>> {
        val (weekStart, weekEnd) = currentWeekRange()
        val rawEntries = fetchAiringSchedule(weekStart, weekEnd, accessToken)
        return rawEntries
            .filter { it.airingAt in weekStart..weekEnd }
            .groupBy { it.dayOfWeek }
            .mapValues { (_, list) -> list.sortedBy { it.airingAt } }
    }

    /**
     * Birden fazla takvimin gün bazlı birleşimi. Aynı yapım hem AniList hem TMDB
     * takviminde olabilir → aynı gün içinde kimlik ve normalize başlıkla tekilleştirilir
     * (öncelik ilk takvimde, yani AniList'te kalır: kesin bölüm numarası ondadır).
     */
    private fun mergeWeeklySchedules(
        vararg schedules: Map<Int, List<AiringEntry>>
    ): Map<Int, List<AiringEntry>> {
        val merged = mutableMapOf<Int, MutableList<AiringEntry>>()
        for (schedule in schedules) {
            for ((day, entries) in schedule) {
                val bucket = merged.getOrPut(day) { mutableListOf() }
                for (entry in entries) {
                    val sameId = bucket.any { it.aniListId == entry.aniListId && it.source == entry.source }
                    val titleKey = normalizeAiringTitle(entry.title)
                        ?: normalizeAiringTitle(entry.titleEnglish)
                    val sameTitle = titleKey != null && bucket.any { existing ->
                        normalizeAiringTitle(existing.title) == titleKey ||
                            normalizeAiringTitle(existing.titleEnglish) == titleKey
                    }
                    if (!sameId && !sameTitle) bucket.add(entry)
                }
            }
        }
        return merged.mapValues { (_, list) -> list.sortedBy { it.airingAt } }
    }

    private suspend fun fetchTmdbWeeklySchedule(): Map<Int, List<AiringEntry>> {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val language = TmdbApiClient.getActiveLanguage()
        val scheduleMap = mutableMapOf<Int, MutableList<AiringEntry>>()
        for (day in 1..7) {
            scheduleMap[day] = mutableListOf()
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val weekStartCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val weekEndCal = (weekStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        val weekStartStr = sdf.format(weekStartCal.time)
        val weekEndStr = sdf.format(weekEndCal.time)

        // DÜZELTME (eski davranış): tv/on_the_air + first_air_date kullanılıyordu; uzun soluklu
        // diziler ilk yayın tarihlerinin (ör. yıllar önceki) hafta gününe düşüyor, geçmiş epoch
        // taşıyordu → takvimde rastgele/yanlış günlere dağılmış eski kayıtlar görünüyordu.
        //
        // Yeni davranış: discover/tv'nin `air_date` filtresi "o gün bölümü yayınlanan dizileri"
        // verir → haftanın her günü için ayrı sorgu yapılıp kayıt gerçek yayı gününe yerleştirilir.
        // TMDB bölüm numarası vermediği için episode = -1 (bilinmiyor) işaretlenir.
        for (offset in 0..6) {
            val dayCal = (weekStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dateStr = sdf.format(dayCal.time)
            val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language" +
                "&air_date.gte=$dateStr&air_date.lte=$dateStr&sort_by=popularity.desc" +
                "&include_null_first_air_dates=false&page=1"
            parseTmdbList(tvUrl, isMovie = false, scheduleMap = scheduleMap, overrideDate = dateStr, overrideEpisode = -1)
        }

        // Bu hafta vizyona giren filmler — kendi vizyon günlerine yerleştirilir.
        val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language" +
            "&primary_release_date.gte=$weekStartStr&primary_release_date.lte=$weekEndStr" +
            "&sort_by=primary_release_date.asc&page=1"
        parseTmdbList(movieUrl, isMovie = true, scheduleMap = scheduleMap)

        return scheduleMap.mapValues { (_, list) -> list.toList() }
    }

    /**
     * @param overrideDate Verilirse kaydın gün/epoch bilgisi öğenin kendi tarihi yerine bu
     *                     tarihten (yyyy-MM-dd) hesaplanır — per-gün `discover/tv?air_date=`
     *                     sorgularının sonuçlarını gerçek yayın gününe yerleştirmek için.
     * @param overrideEpisode Verilirse varsayılan bölüm numarası yerine kullanılır
     *                        (-1 = TMDB bölüm numarası bilinmiyor).
     */
    private fun parseTmdbList(
        urlStr: String,
        isMovie: Boolean,
        scheduleMap: MutableMap<Int, MutableList<AiringEntry>>? = null,
        outList: MutableList<AiringEntry>? = null,
        overrideDate: String? = null,
        overrideEpisode: Int? = null
    ) {
        try {
            val responseText = KitsugiApiBase.executeGetRequest(java.net.URL(urlStr)) ?: return
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return
            // Türkçe başlık yoksa İngilizce'ye düşmek için aynı sayfanın en-US başlık haritası
            val enTitlesMap = fetchEnglishTitleMapIfNeeded(results, isMovie, urlStr)
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val tmdbId = item.optInt("id", 0).takeIf { it > 0 } ?: continue
                val localizedTitle = if (isMovie) item.optString("title", "") else item.optString("name", "")
                if (localizedTitle.isBlank()) continue
                val originalTitle = if (isMovie) item.optString("original_title", "") else item.optString("original_name", "")
                val originalLang = item.optString("original_language", "")
                val localizedFallback = MediaTitleResolver.isLocalizedFallback(
                    localized = localizedTitle,
                    original = originalTitle,
                    requestedLanguage = TmdbUrlUtils.languageOf(urlStr),
                    originalLanguage = originalLang
                )
                val titleInput = if (localizedFallback) null else localizedTitle
                val englishTitle = enTitlesMap[tmdbId]
                // Türkçe → İngilizce → Romaji zinciri (CJK başlık ekrana düşmez)
                val title = MediaTitleResolver.resolve(
                    localized = titleInput,
                    english = englishTitle,
                    romaji = MediaTitleResolver.latin(originalTitle),
                    original = originalTitle.ifBlank { localizedTitle }
                )
                val resolvedTitleEnglish = MediaTitleResolver.resolveEnglish(
                    localized = titleInput,
                    english = englishTitle,
                    romaji = MediaTitleResolver.latin(originalTitle),
                    original = originalTitle
                ) ?: title
                val resolvedTitleNative = originalTitle
                    .ifBlank { localizedTitle }
                    .takeIf { MediaTitleResolver.hasCjk(it) }
                val posterPath = item.optNullableString("poster_path") ?: ""
                val releaseDate = overrideDate
                    ?: if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")

                val (airingAt, dayOfWeek) = parseDateToAiringAtAndDayOfWeek(releaseDate)
                val coverUrl = if (posterPath.isNotEmpty()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val rating = item.optDouble("vote_average", 0.0)
                val score = if (rating > 0.0) (rating * 10).toInt().coerceIn(0, 100) else null

                val entry = AiringEntry(
                    aniListId = tmdbId,
                    malId = null,
                    title = title,
                    titleEnglish = resolvedTitleEnglish,
                    titleNative = resolvedTitleNative,
                    coverUrl = coverUrl,
                    episode = overrideEpisode ?: if (isMovie) 0 else 1,
                    airingAt = airingAt,
                    dayOfWeek = dayOfWeek,
                    averageScore = score,
                    isAdult = item.optBoolean("adult", false),
                    source = "tmdb"
                )

                // TMDB film ve dizi kimlik alanları çakışabilir → dedupe anahtarı türle birlikte.
                if (scheduleMap != null) {
                    val existingList = scheduleMap[dayOfWeek] ?: continue
                    if (existingList.none { it.aniListId == tmdbId && it.isMovieEntry() == isMovie }) {
                        existingList.add(entry)
                    }
                }
                if (outList != null) {
                    if (outList.none { it.aniListId == tmdbId && it.isMovieEntry() == isMovie }) {
                        outList.add(entry)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AiringCalendarClient", "parseTmdbList error: ${e.message}", e)
        }
    }

    /** TMDB kayıtlarında film işareti: episode == 0 (diziler 1 veya -1 taşır). */
    private fun AiringEntry.isMovieEntry(): Boolean = episode == 0

    /**
     * Aynı sayfanın İngilizce (en-US) başlıklarını id → başlık haritası olarak çeker.
     *
     * TMDB, istenen dilde (ör. Türkçe) başlık bulamazsa orijinal Japonca başlığı döndürür.
     * Bu durumda yalnızca bir ek istek yapılır; `with_original_language` parametresi
     * bozulmadan korunur (bkz. [TmdbUrlUtils.withLanguage]).
     */
    private fun fetchEnglishTitleMapIfNeeded(
        results: org.json.JSONArray,
        isMovie: Boolean,
        urlStr: String
    ): Map<Int, String> {
        // Gereksinim kontrolü ortak yardımcıya devredilir (tüm TMDB çağrıları aynı kuralı kullanır).
        if (!TmdbTitleFallback.needsEnglishFallback(results, urlStr, { isMovie })) return emptyMap()
        repeat(2) { attempt ->
            try {
                val enUrl = java.net.URL(TmdbUrlUtils.englishVariant(urlStr))
                val map = TmdbTitleFallback.parseEnglishTitles(KitsugiApiBase.executeGetRequest(enUrl))
                if (map.isNotEmpty()) return map
            } catch (e: Exception) {
                android.util.Log.e("AiringCalendarClient", "İngilizce başlık haritası alınamadı (deneme ${attempt + 1}): ${e.message}")
            }
        }
        return emptyMap()
    }

    private fun parseDateToAiringAtAndDayOfWeek(dateStr: String, shiftToCurrentWeek: Boolean = false): Pair<Long, Int> {
        if (dateStr.isBlank()) {
            // Tarihi bilinmeyen içerikler: çok uzak gelecek olarak işaretle ki filtrelensin
            val now = System.currentTimeMillis()
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return Pair((now / 1000L) + 365 * 24 * 3600L, day)
        }
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1
                val day = parts[2].toInt()

                // Kesin saat bilinmediğinden gün başlangıcını (00:00) epoch olarak kullan
                // Bu sayede aynı gün içindeki tüm içerikler birbirinden farklı epoch'a sahip olmaz
                val cal = Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

                if (shiftToCurrentWeek) {
                    val thisWeekCal = Calendar.getInstance()
                    val today = thisWeekCal.get(Calendar.DAY_OF_WEEK)
                    val diff = dayOfWeek - today
                    thisWeekCal.add(Calendar.DAY_OF_YEAR, diff)
                    thisWeekCal.set(Calendar.HOUR_OF_DAY, 0)
                    thisWeekCal.set(Calendar.MINUTE, 0)
                    thisWeekCal.set(Calendar.SECOND, 0)
                    thisWeekCal.set(Calendar.MILLISECOND, 0)
                    Pair(thisWeekCal.timeInMillis / 1000L, dayOfWeek)
                } else {
                    Pair(cal.timeInMillis / 1000L, dayOfWeek)
                }
            } else {
                val now = System.currentTimeMillis()
                val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                // Bilinmeyen format — filtrelenebilmesi için şimdiki zaman döndür
                Pair(now / 1000L, day)
            }
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            Pair(now / 1000L, day)
        }
    }

    /**
     * Belirli bir zaman aralığında yayınlanan bölümleri (geçmiş + gelecek) döndürür.
     *
     * Bildirim akışları için kullanılır: örn. "son 7 günde yayınlananlar".
     * AniList `airingSchedules` ucu herkese açıktır; token opsiyoneldir.
     *
     * @param fromEpochSec aralık başlangıcı (dahil), Unix epoch saniye
     * @param toEpochSec   aralık bitişi (dahil), Unix epoch saniye
     */
    suspend fun fetchAiringWindow(
        fromEpochSec: Long,
        toEpochSec: Long,
        accessToken: String? = null
    ): List<AiringEntry> = withContext(Dispatchers.IO) {
        val entries = fetchAiringSchedule(fromEpochSec, toEpochSec, accessToken)
        entries
            .filter { it.airingAt in fromEpochSec..toEpochSec }
            .sortedBy { it.airingAt }
    }

    suspend fun fetchUpcomingSchedule(limit: Int = 30, accessToken: String? = null, preferredSource: String? = null): List<AiringEntry> {
        return withContext(Dispatchers.IO) {
            if (preferredSource == "tmdb") {
                fetchTmdbUpcomingSchedule(limit)
            } else {
                val nowSeconds = System.currentTimeMillis() / 1000L
                val fourteenDaysLater = nowSeconds + 14 * 24 * 3600L
                val variables = JSONObject()
                    .put("page", 1)
                    .put("perPage", 50)
                    .put("airingAt_greater", nowSeconds)
                    .put("airingAt_lesser", fourteenDaysLater)
                val responseText = KitsugiApiBase.executeAniListQuery(
                    query = QUERY,
                    variables = variables,
                    accessToken = accessToken
                ) ?: return@withContext emptyList<AiringEntry>()
                val (entries, _) = parseResponse(responseText)
                entries
                    .filter { it.airingAt > nowSeconds }
                    .sortedBy { it.airingAt }
                    .take(limit)
            }
        }
    }

    private suspend fun fetchTmdbUpcomingSchedule(limit: Int): List<AiringEntry> {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val language = TmdbApiClient.getActiveLanguage()
        val list = mutableListOf<AiringEntry>()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = sdf.format(java.util.Date())
        val cal90 = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 90) }
        val maxDate = sdf.format(cal90.time)

        // Fetch upcoming TV shows — 2 pages for variety
        val tvUrl1 = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&first_air_date.gte=$today&first_air_date.lte=$maxDate&sort_by=first_air_date.asc&page=1"
        val tvUrl2 = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&language=$language&first_air_date.gte=$today&first_air_date.lte=$maxDate&sort_by=first_air_date.asc&page=2"
        // Fetch upcoming movies — 2 pages for variety
        val movieUrl1 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&primary_release_date.gte=$today&primary_release_date.lte=$maxDate&sort_by=primary_release_date.asc&page=1"
        val movieUrl2 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=$language&primary_release_date.gte=$today&primary_release_date.lte=$maxDate&sort_by=primary_release_date.asc&page=2"

        parseTmdbList(tvUrl1, isMovie = false, outList = list)
        parseTmdbList(tvUrl2, isMovie = false, outList = list)
        parseTmdbList(movieUrl1, isMovie = true, outList = list)
        parseTmdbList(movieUrl2, isMovie = true, outList = list)

        val nowSeconds = System.currentTimeMillis() / 1000L
        return list
            .filter { it.airingAt > nowSeconds }
            .sortedBy { it.airingAt }
            .take(limit)
    }

    private suspend fun fetchAiringSchedule(
        airingAtGreater: Long,
        airingAtLesser: Long,
        accessToken: String?
    ): List<AiringEntry> {
        val allEntries = mutableListOf<AiringEntry>()
        var page = 1
        var hasNextPage = true
        while (hasNextPage && page <= 10) {
            val variables = JSONObject()
                .put("page", page)
                .put("perPage", 50)
                .put("airingAt_greater", airingAtGreater)
                .put("airingAt_lesser", airingAtLesser)
            val responseText = KitsugiApiBase.executeAniListQuery(
                query = QUERY,
                variables = variables,
                accessToken = accessToken
            ) ?: break
            val (entries, nextPage) = parseResponse(responseText)
            allEntries.addAll(entries)
            hasNextPage = nextPage && entries.isNotEmpty()
            page++
        }
        return allEntries
    }

    private fun parseResponse(jsonText: String): Pair<List<AiringEntry>, Boolean> {
        return try {
            val root = JSONObject(jsonText)
            val pageObj = root.optJSONObject("data")?.optJSONObject("Page")
                ?: return Pair(emptyList(), false)
            val hasNextPage = pageObj.optJSONObject("pageInfo")
                ?.optBoolean("hasNextPage", false) ?: false
            val schedules = pageObj.optJSONArray("airingSchedules")
                ?: return Pair(emptyList(), false)

            val entries = mutableListOf<AiringEntry>()
            for (i in 0 until schedules.length()) {
                val item = schedules.optJSONObject(i) ?: continue
                val media = item.optJSONObject("media") ?: continue
                val airingAt = item.optLong("airingAt", 0L)
                if (airingAt <= 0L) continue
                val episode = item.optInt("episode", 0)
                val aniListId = media.optInt("id", 0)
                if (aniListId <= 0) continue
                val malId = if (media.has("idMal") && !media.isNull("idMal"))
                    media.optInt("idMal", 0).takeIf { it > 0 } else null
                val averageScore = if (media.has("averageScore") && !media.isNull("averageScore"))
                    media.optInt("averageScore", 0).takeIf { it > 0 } else null
                val countryOfOrigin = media.optNullableString("countryOfOrigin")
                val isNonJapanese = countryOfOrigin != null && !countryOfOrigin.equals("JP", ignoreCase = true)
                val titleObj = media.optJSONObject("title")
                val romaji = titleObj?.optNullableString("romaji")
                val english = titleObj?.optNullableString("english")
                val native = titleObj?.optNullableString("native")
                // Kore ("KR") ve Çin ("CN") yapımı anime/donghua/awe için İngilizce başlık ("Tomb Raider King")
                // raw romanizasyon ("Dogul Wang") yerine tercih edilir.
                val title = if (isNonJapanese && !english.isNullOrBlank()) {
                    english
                } else {
                    romaji ?: english ?: native ?: continue
                }
                val coverUrl = media.optJSONObject("coverImage")?.optNullableString("large")
                val dayOfWeek = Calendar.getInstance().apply {
                    timeInMillis = airingAt * 1000L
                }.get(Calendar.DAY_OF_WEEK)
                entries.add(
                    AiringEntry(
                        aniListId = aniListId,
                        malId = malId,
                        title = title,
                        titleEnglish = english,
                        titleNative = native,
                        coverUrl = coverUrl,
                        episode = episode,
                        airingAt = airingAt,
                        dayOfWeek = dayOfWeek,
                        averageScore = averageScore,
                        countryOfOrigin = countryOfOrigin,
                        isAdult = media.optBoolean("isAdult", false)
                    )
                )
            }
            Pair(entries, hasNextPage)
        } catch (e: Exception) {
            android.util.Log.e("AiringCalendarClient", "Parse hatası: ${e.message}", e)
            Pair(emptyList(), false)
        }
    }

    private fun currentWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val weekStart = cal.timeInMillis / 1000L
        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val weekEnd = cal.timeInMillis / 1000L
        return Pair(weekStart, weekEnd)
    }

    companion object {
        private val QUERY = """
            query(${'$'}page:Int,${'$'}perPage:Int,${'$'}airingAt_greater:Int,${'$'}airingAt_lesser:Int){
              Page(page:${'$'}page,perPage:${'$'}perPage){
                pageInfo{hasNextPage}
                airingSchedules(airingAt_greater:${'$'}airingAt_greater,airingAt_lesser:${'$'}airingAt_lesser,sort:TIME){
                  airingAt episode
                  media{
                    id idMal
                    title{romaji english native}
                    countryOfOrigin
                    coverImage{large}
                    averageScore
                    isAdult
                  }
                }
              }
            }
        """.trimIndent()
    }
}

package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fanart.tv REST API istemcisi.
 *
 * Fanart.tv, TVDB ID'ye göre (TV/Anime) ve TMDB ID'ye göre (Film) yüksek kaliteli
 * logo, backdrop, poster ve daha fazlasını sunar.
 *
 * Proje API Anahtarı: https://fanart.tv/get-an-api-key/
 *
 * TV  : https://webservice.fanart.tv/v3/tv/{tvdb_id}?api_key=KEY
 * Film: https://webservice.fanart.tv/v3/movies/{tmdb_id}?api_key=KEY
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │                    TV / ANİME KATEGORİLERİ                          │
 * ├──────────────────────┬───────────────────────┬──────────────────────┤
 * │ Fanart.tv Kategorisi │ API Alanı              │ GalleryCategory      │
 * ├──────────────────────┼───────────────────────┼──────────────────────┤
 * │ HD ClearLOGO         │ hdtvlogo / clearlogo   │ LOGO                 │
 * │ HD ClearART          │ hdclearart / clearart  │ CLEARART             │
 * │ Background           │ showbackground         │ BACKDROP             │
 * │ 4K Background        │ showbackground (4k)    │ BACKDROP             │
 * │ Poster               │ tvposter / seasonposter│ POSTER               │
 * │ Banner               │ tvbanner / seasonbanner│ BANNER               │
 * │ TV Thumbs            │ tvthumb / seasonthumb  │ THUMBNAIL            │
 * │ CharacterART         │ characterart           │ CHARACTER            │
 * │ Square               │ squareposter           │ SQUARE               │
 * ├──────────────────────┴───────────────────────┴──────────────────────┤
 * │                    FİLM KATEGORİLERİ                                │
 * ├──────────────────────┬───────────────────────┬──────────────────────┤
 * │ HD ClearLOGO         │ hdmovielogo / movielogo│ LOGO                 │
 * │ HD ClearART          │ hdmovieclearart        │ CLEARART             │
 * │ Background           │ moviebackground        │ BACKDROP             │
 * │ Poster               │ movieposter            │ POSTER               │
 * │ Banner               │ moviebanner            │ BANNER               │
 * │ Thumbnail            │ moviethumb             │ THUMBNAIL            │
 * │ Disc Art             │ moviedisc              │ OTHER                │
 * └──────────────────────┴───────────────────────┴──────────────────────┘
 *
 * NOT: Tüm kategoriler LIMIT OLMADAN çekilir — Fanart.tv API'si tek sorguda
 * tüm kategori verilerini döndürür (tek HTTP isteği), dolayısıyla limit
 * kaldırmak ağ maliyetini artırmaz, sadece UI'a daha fazla resim iletir.
 */
object FanartApiClient {

    private const val TAG = "FanartApiClient"
    private const val BASE_URL = "https://webservice.fanart.tv/v3"

    /**
     * Dahili (yedek) Fanart.tv proje API anahtarı.
     * Kullanıcı kendi anahtarını girmezse bu anahtar kullanılır.
     *
     * Öncelik sırası:
     *  1. Kullanıcının ayarlardan girdiği kişisel API anahtarı
     *  2. Bu built-in proje anahtarı (rate-limit paylaşımlı)
     */
    private const val BUILT_IN_API_KEY = "d5a4d282038f8b55b707b93e462911c8"

    /**
     * Etkin Fanart.tv API anahtarını döner.
     */
    fun getActiveApiKey(userKey: String = ""): String =
        userKey.trim().ifBlank { BUILT_IN_API_KEY }

    /**
     * Görsel dil tercihi: çağıran dil vermediyse UYGULAMA dilini kullanır (örn. "tr").
     * Eskiden sabit "en" idi; Türkçe kullanıcıya İngilizce logo dayatılıyordu.
     * Öncelik zinciri (extractBestUrl/appendImages): tercih edilen dil → nötr → en.
     */
    fun resolveLanguage(language: String): String {
        val explicit = language.trim()
        if (explicit.isNotBlank()) return explicit.lowercase()
        val active = runCatching { TmdbApiClient.getActiveLanguage() }.getOrDefault("tr")
        return active.substringBefore('-').trim().lowercase().ifBlank { "en" }
    }

    /**
     * Fanart.tv anahtarını gerçek bir, yaygın olarak bulunan film kaydıyla doğrular.
     * Boş anahtarda uygulamanın paylaşılan anahtarı sınanır.
     */
    suspend fun validateApiKey(userKey: String): Boolean = withContext(Dispatchers.IO) {
        val keyToCheck = getActiveApiKey(userKey)
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("webservice.fanart.tv")
            .addPathSegments("v3/movies/550")
            .addQueryParameter("api_key", keyToCheck)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()
        val validationClient = com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient
            .newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        try {
            validationClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fanart.tv key validation failed: HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body?.string() ?: return@withContext false
                runCatching { JSONObject(body) }.isSuccess
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fanart.tv key validation failed (${e.javaClass.simpleName})")
            false
        }
    }

    private fun buildUrl(endpoint: String, id: Int, apiKey: String): java.net.URL {
        val trimmedKey = apiKey.trim().ifBlank { BUILT_IN_API_KEY }
        val urlString = "$BASE_URL/$endpoint/$id?api_key=$trimmedKey"
        Log.d(TAG, "Fanart request: $endpoint/$id (API key redacted)")
        return java.net.URL(urlString)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TV / Anime: TVDB ID bazlı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TVDB ID ile TV/Anime görsellerini çeker.
     * Tek HTTP isteği ile TÜM kategoriler alınır — limit yok.
     */
    fun fetchTvImages(tvdbId: Int, apiKey: String, language: String = ""): List<GalleryItem> {
        if (tvdbId <= 0 || apiKey.isBlank()) return emptyList()
        return try {
            val url = buildUrl("tv", tvdbId, apiKey)
            val response = KitsugiApiBase.executeGetRequest(url)
            if (response == null) {
                Log.w(TAG, "fetchTvImages: HTTP request failed or returned empty for tvdbId=$tvdbId")
                return emptyList()
            }
            Log.d(TAG, "fetchTvImages: Response length=${response.length} for tvdbId=$tvdbId")
            parseTvImages(JSONObject(response), resolveLanguage(language))
        } catch (e: Exception) {
            Log.w(TAG, "fetchTvImages failed for tvdbId=$tvdbId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * TVDB ID ile yalnızca en iyi logo URL'sini çeker (hero alanı için).
     */
    fun fetchBestLogo(
        tvdbId: Int,
        apiKey: String,
        language: String = "",
        seasonNumber: Int? = null
    ): String? {
        if (tvdbId <= 0 || apiKey.isBlank()) return null
        return try {
            val url = buildUrl("tv", tvdbId, apiKey)
            val response = KitsugiApiBase.executeGetRequest(url)
            if (response == null) {
                Log.w(TAG, "fetchBestLogo: HTTP request failed or returned empty for tvdbId=$tvdbId")
                return null
            }
            val root = JSONObject(response)
            // ClearART logo fallback'i DEĞİLDİR; ayrı bir kategoridir. Yalnızca gerçek logolar.
            extractBestUrl(root, listOf("hdtvlogo", "clearlogo"), resolveLanguage(language), seasonNumber = seasonNumber)
        } catch (e: Exception) {
            Log.w(TAG, "fetchBestLogo (TV) failed for tvdbId=$tvdbId: ${e.message}", e)
            null
        }
    }

    /**
     * Sezona özel logo çeker (TVDB). Yalnızca o sezona etiketli logolara bakar.
     */
    fun fetchBestSeasonLogo(
        tvdbId: Int,
        seasonNumber: Int,
        apiKey: String,
        language: String = "",
        strictLanguage: Boolean = false
    ): String? {
        if (tvdbId <= 0 || seasonNumber <= 0 || apiKey.isBlank()) return null
        return try {
            val url = buildUrl("tv", tvdbId, apiKey)
            val response = KitsugiApiBase.executeGetRequest(url) ?: return null
            val root = JSONObject(response)
            extractBestUrl(
                root = root,
                keys = listOf("hdtvlogo", "clearlogo"),
                language = resolveLanguage(language),
                seasonNumber = seasonNumber,
                seasonOnly = true,
                strictLanguage = strictLanguage
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchBestSeasonLogo failed for tvdbId=$tvdbId season=$seasonNumber: ${e.message}", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Film: TMDB ID bazlı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TMDB ID ile film görsellerini çeker.
     * Tek HTTP isteği ile TÜM kategoriler alınır — limit yok.
     */
    fun fetchMovieImages(tmdbId: Int, apiKey: String, language: String = ""): List<GalleryItem> {
        if (tmdbId <= 0 || apiKey.isBlank()) return emptyList()
        return try {
            val url = buildUrl("movies", tmdbId, apiKey)
            val response = KitsugiApiBase.executeGetRequest(url)
            if (response == null) {
                Log.w(TAG, "fetchMovieImages: HTTP request failed or returned empty for tmdbId=$tmdbId")
                return emptyList()
            }
            Log.d(TAG, "fetchMovieImages: Response length=${response.length} for tmdbId=$tmdbId")
            parseMovieImages(JSONObject(response), resolveLanguage(language))
        } catch (e: Exception) {
            Log.w(TAG, "fetchMovieImages failed for tmdbId=$tmdbId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * TMDB ID ile yalnızca en iyi film logo URL'sini çeker (hero alanı için).
     */
    fun fetchBestMovieLogo(tmdbId: Int, apiKey: String, language: String = ""): String? {
        if (tmdbId <= 0 || apiKey.isBlank()) return null
        return try {
            val url = buildUrl("movies", tmdbId, apiKey)
            val response = KitsugiApiBase.executeGetRequest(url)
            if (response == null) {
                Log.w(TAG, "fetchBestMovieLogo: HTTP request failed or returned empty for tmdbId=$tmdbId")
                return null
            }
            val root = JSONObject(response)
            // ClearART logo fallback'i DEĞİLDİR.
            extractBestUrl(root, listOf("hdmovielogo", "movielogo"), resolveLanguage(language))
        } catch (e: Exception) {
            Log.w(TAG, "fetchBestMovieLogo failed for tmdbId=$tmdbId: ${e.message}", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON parse yardımcıları — LİMİTSİZ, tüm kategoriler
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TV / Anime: Fanart.tv sayfasındaki TÜM kategorileri çeker.
     *
     * Fanart.tv'de görebileceğin kategoriler (resimdeki gibi):
     *   HD ClearLOGO, Poster, HD ClearART, CharacterART,
     *   TV Thumbs, Background, Banner, 4K Background, Square
     */
    private fun parseTvImages(root: JSONObject, language: String): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()

        // ── POSTER ────────────────────────────────────────────────────────────
        appendImages(items, root, "tvposter",      GalleryCategory.POSTER,    language)
        appendImages(items, root, "seasonposter",  GalleryCategory.POSTER,    language)

        // ── BACKDROP (Background + 4K Background) ─────────────────────────────
        appendImages(items, root, "showbackground", GalleryCategory.BACKDROP,  language)

        // ── LOGO ──────────────────────────────────────────────────────────────
        appendImages(items, root, "hdtvlogo",      GalleryCategory.LOGO,      language)
        appendImages(items, root, "clearlogo",     GalleryCategory.LOGO,      language)

        // ── CLEARART (HD ClearART) ────────────────────────────────────────────
        appendImages(items, root, "hdclearart",    GalleryCategory.CLEARART,  language)
        appendImages(items, root, "clearart",      GalleryCategory.CLEARART,  language)

        // ── BANNER ────────────────────────────────────────────────────────────
        appendImages(items, root, "tvbanner",      GalleryCategory.BANNER,    language)
        appendImages(items, root, "seasonbanner",  GalleryCategory.BANNER,    language)

        // ── THUMBNAIL (TV Thumbs) ─────────────────────────────────────────────
        appendImages(items, root, "tvthumb",       GalleryCategory.THUMBNAIL, language)
        appendImages(items, root, "seasonthumb",   GalleryCategory.THUMBNAIL, language)

        // ── CHARACTER (CharacterART) ───────────────────────────────────────────
        appendImages(items, root, "characterart",  GalleryCategory.CHARACTER, language)

        // ── SQUARE ────────────────────────────────────────────────────────────
        appendImages(items, root, "squareposter",  GalleryCategory.SQUARE,    language)

        Log.d(TAG, "parseTvImages: ${items.size} total items parsed")
        return items
    }

    /**
     * Film: Fanart.tv'deki tüm film kategorileri.
     */
    private fun parseMovieImages(root: JSONObject, language: String): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()

        // ── POSTER ────────────────────────────────────────────────────────────
        appendImages(items, root, "movieposter",     GalleryCategory.POSTER,    language)

        // ── BACKDROP ──────────────────────────────────────────────────────────
        appendImages(items, root, "moviebackground", GalleryCategory.BACKDROP,  language)

        // ── LOGO ──────────────────────────────────────────────────────────────
        appendImages(items, root, "hdmovielogo",     GalleryCategory.LOGO,      language)
        appendImages(items, root, "movielogo",       GalleryCategory.LOGO,      language)

        // ── CLEARART ──────────────────────────────────────────────────────────
        appendImages(items, root, "hdmovieclearart", GalleryCategory.CLEARART,  language)
        appendImages(items, root, "movieart",        GalleryCategory.CLEARART,  language)

        // ── BANNER ────────────────────────────────────────────────────────────
        appendImages(items, root, "moviebanner",     GalleryCategory.BANNER,    language)

        // ── THUMBNAIL ─────────────────────────────────────────────────────────
        appendImages(items, root, "moviethumb",      GalleryCategory.THUMBNAIL, language)

        // ── DISC ART ──────────────────────────────────────────────────────────
        appendImages(items, root, "moviedisc",       GalleryCategory.OTHER,     language)

        Log.d(TAG, "parseMovieImages: ${items.size} total items parsed")
        return items
    }

    /**
     * Belirtilen API alanındaki TÜM resimleri listeye ekler (limit yok).
     *
     * Sıralama: tercih edilen dil → dil bağımsız ("00") → İngilizce → diğerleri
     *
     * NOT: Fanart.tv API tek bir HTTP isteğiyle tüm kategori verilerini döndürür.
     * Limit kaldırmak ek ağ isteği gerektirmez — sadece JSON'dan daha fazla
     * öğe okunur ve UI'a iletilir.
     */
    private fun appendImages(
        target: MutableList<GalleryItem>,
        root: JSONObject,
        key: String,
        category: GalleryCategory,
        preferredLanguage: String
    ) {
        val array = root.optJSONArray(key) ?: return
        val preferred = mutableListOf<Pair<Int, GalleryItem>>()
        val neutral   = mutableListOf<Pair<Int, GalleryItem>>()
        val english   = mutableListOf<Pair<Int, GalleryItem>>()
        val other     = mutableListOf<Pair<Int, GalleryItem>>()
        val wanted = preferredLanguage.trim().lowercase().substringBefore('-')

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val urlStr = obj.optString("url", "").trim()
            if (urlStr.isBlank()) continue
            val lang = normalizeFanartLang(obj.optString("lang", ""))
            val season = normalizeFanartSeason(obj.optString("season", ""))
            val likes = obj.optString("likes", "").toIntOrNull() ?: obj.optInt("likes", 0)
            val extractedName = extractNameFromUrl(urlStr)

            // ── API'nin görsel başına sağladığı alanlar (fanart.tv v3: name, iMDb) ──
            // Web sayfasındaki "Uploader / Copyright / Downloads" gibi alanlar v3 API
            // yanıtında YOKTUR; bu yüzden yalnızca gerçekten gelenler detay olarak taşınır.
            val apiName = obj.optString("name", "").trim()
            val imdbId = obj.optString("iMDb", "").trim()
            val details = linkedMapOf<String, String>()
            if (apiName.isNotBlank()) details["Ad"] = apiName
            if (imdbId.isNotBlank()) details["IMDb ID"] = imdbId

            // API'nin `name` alanı (örn. CharacterART'te karakter adı) URL türetimi
            // kadar güvenilir olduğundan Açıklama satırında önceliklidir.
            val item = GalleryItem(
                url = urlStr,
                source = "Fanart.tv",
                category = category,
                description = apiName.ifBlank { extractedName },
                language = lang,
                details = details,
                season = season
            )
            val scored = likes to item
            when {
                lang == wanted                 -> preferred.add(scored)
                lang == null                   -> neutral.add(scored)
                lang == "en"                   -> english.add(scored)
                else                           -> other.add(scored)
            }
        }

        fun List<Pair<Int, GalleryItem>>.byLikes() = sortedByDescending { it.first }.map { it.second }
        // Tüm öğeleri ekle — limit yok. Tercih edilen dil ve en çok beğenilen önde.
        target.addAll(preferred.byLikes() + neutral.byLikes() + english.byLikes() + other.byLikes())
    }

    private fun normalizeFanartLang(raw: String): String? {
        val lang = raw.trim().lowercase().substringBefore('-').substringBefore('_')
        return if (lang.isBlank() || lang == "00" || lang == "null") null else lang
    }

    /** "all" / "0" sezon değil, eserin genel görselidir. */
    private fun normalizeFanartSeason(raw: String): String? {
        val season = raw.trim()
        if (season.isBlank() || season == "0" || season.equals("all", true) || season.equals("null", true)) return null
        return season
    }

    /**
     * URL'den temiz, baş harfleri büyük karakter adını veya görsel adını ayıklar.
     * Örn: .../monkey-d-luffy-5231c69c6f2df.png -> Monkey D Luffy
     */
    fun extractNameFromUrl(url: String): String? {
        return try {
            val uri = java.net.URI.create(url)
            val path = uri.path ?: return null
            val filename = path.substringAfterLast('/').substringBeforeLast('.')
            if (filename.isBlank()) return null

            // Trailing hash ayıklama (örn: -5231c69c6f2df)
            val nameWithoutHash = if (filename.contains('-')) {
                val lastHyphenIndex = filename.lastIndexOf('-')
                val suffix = filename.substring(lastHyphenIndex + 1)
                val isLikelyHash = suffix.length in 10..20 && suffix.all { it.isLetterOrDigit() }
                if (isLikelyHash) {
                    filename.substring(0, lastHyphenIndex)
                } else {
                    filename
                }
            } else {
                filename
            }

            // Tire ve alt çizgileri boşlukla değiştir, kelimelerin baş harflerini büyüt
            nameWithoutHash
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Logo alanlarından en iyi URL.
     *
     * Dil sırası: tercih edilen dil → metinsiz ("00") → İngilizce → diğer.
     * Aynı dilde en çok beğenilen (likes) kazanır.
     *
     * [seasonNumber] verilirse o sezona etiketli logo, dil önceliğini bozmadan
     * genel logodan önce gelir. [seasonOnly] ise genel logoya düşülmez.
     */
    private fun extractBestUrl(
        root: JSONObject,
        keys: List<String>,
        language: String,
        seasonNumber: Int? = null,
        seasonOnly: Boolean = false,
        strictLanguage: Boolean = false
    ): String? {
        data class Candidate(val url: String, val lang: String?, val likes: Int, val season: String?)

        val candidates = mutableListOf<Candidate>()
        for (key in keys) {
            val array = root.optJSONArray(key) ?: continue
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val urlStr = obj.optString("url", "").trim()
                if (urlStr.isBlank()) continue
                candidates.add(
                    Candidate(
                        url = urlStr,
                        lang = normalizeFanartLang(obj.optString("lang", "")),
                        likes = obj.optString("likes", "").toIntOrNull() ?: obj.optInt("likes", 0),
                        season = normalizeFanartSeason(obj.optString("season", ""))
                    )
                )
            }
        }
        if (candidates.isEmpty()) return null

        val wantedSeason = seasonNumber?.takeIf { it > 0 }?.toString()
        val seasonal = if (wantedSeason == null) emptyList() else candidates.filter { it.season == wantedSeason }
        val main = candidates.filter { it.season == null }
        val pools = when {
            seasonOnly -> listOf(seasonal)
            wantedSeason != null -> listOf(seasonal, main)
            main.isNotEmpty() -> listOf(main)
            else -> listOf(candidates)
        }
        val wanted = language.trim().lowercase().substringBefore('-').ifBlank { "en" }

        fun best(pool: List<Candidate>, predicate: (Candidate) -> Boolean): String? =
            pool.filter(predicate).maxByOrNull { it.likes }?.url

        for (pool in pools) {
            if (pool.isEmpty()) continue
            best(pool) { it.lang == wanted }?.let { return it }
            if (strictLanguage) continue
            best(pool) { it.lang == null }?.let { return it }
            best(pool) { it.lang == "en" }?.let { return it }
            best(pool) { true }?.let { return it }
        }
        return null
    }
}

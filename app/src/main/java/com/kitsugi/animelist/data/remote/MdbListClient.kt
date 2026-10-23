package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * MDBList API istemcisi.
 *
 * IMDb, Rotten Tomatoes, Metacritic, Letterboxd, TMDB ve Trakt puanlarını tek istekte alır.
 * IMDb kimliği tercih edilir; IMDb kimliği çözülemeyen içeriklerde TMDB ID yedeği kullanılabilir.
 */
data class MdbListRatings(
    val imdb: Double? = null,
    val tomatoes: Int? = null,
    val tomatoesAudience: Int? = null,
    val metacritic: Int? = null,
    val letterboxd: Double? = null,
    val tmdb: Double? = null,
    val trakt: Double? = null,
    val imdbId: String? = null
) {
    val isEmpty: Boolean get() = imdb == null && tomatoes == null && tomatoesAudience == null &&
        metacritic == null && letterboxd == null && tmdb == null && trakt == null
}

/**
 * MDBList, bir kayıt için puan bulunamadığında alanı **0** olarak döndürür. 0 gerçek bir
 * puan değildir (IMDb 0,0 / Metacritic 0 / Trakt %0 diye bir şey yoktur); ekranda
 * "0 Metacritic" gibi anlamsız kutular çizilmemesi için bu değerler "puan yok" kabul edilir.
 */
private fun MdbListRatings.withoutEmptyScores(): MdbListRatings = copy(
    imdb = imdb?.takeIf { it > 0.0 },
    tomatoes = tomatoes?.takeIf { it > 0 },
    tomatoesAudience = tomatoesAudience?.takeIf { it > 0 },
    metacritic = metacritic?.takeIf { it > 0 },
    letterboxd = letterboxd?.takeIf { it > 0.0 },
    tmdb = tmdb?.takeIf { it > 0.0 },
    trakt = trakt?.takeIf { it > 0.0 }
)

object MdbListClient {

    private const val TAG = "MdbListClient"
    private const val BASE_URL = "https://mdblist.com/api/"
    private const val CACHE_TTL_MS = 30L * 60L * 1_000L
    private const val MAX_CACHE_ENTRIES = 500
    private val imdbIdPattern = Regex("^tt\\d{5,12}$", RegexOption.IGNORE_CASE)

    /** MDBList isteklerinde kullanılan tanımlayıcı (bazı uç noktalar UA ister). */
    private const val USER_AGENT = "KitsugiAnimeList/1.0"

    /** Ayarlar sekmesi açıldığında kota bu yaştan eskiyse sunucudan yeniden okunur. */
    private const val QUOTA_STALE_AFTER_MS = 15L * 60L * 1_000L

    /** Yenile düğmesine art arda basıldığında boşa istek harcanmasını engeller. */
    private const val QUOTA_MIN_REFRESH_INTERVAL_MS = 5L * 1_000L

    /** Sunucudan değer alınamadıysa otomatik yeniden deneme aralığı. */
    private const val QUOTA_RETRY_INTERVAL_MS = 60L * 1_000L

    private data class CacheEntry(val ratings: MdbListRatings, val expiresAt: Long)
    private val cache = BoundedCache<String, CacheEntry>("mdbList", 300)

    /** Son okunan MDBList kotası (ayarlar ve detay sayfası bu akışı dinler). */
    private val _quota = MutableStateFlow<MdbListQuota?>(null)
    val quotaFlow: StateFlow<MdbListQuota?> = _quota.asStateFlow()

    @Volatile
    private var quotaFingerprint: String? = null

    private val lastQuotaRefreshAt = ConcurrentHashMap<String, Long>()

    /** Puan isteğini bekletmeden kotayı tazelemek için (yalnızca gerektiğinde 1 istek). */
    private val quotaScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Uygulama açılışında kalıcı kota deposunu bağlar (bkz. KitsugiApplication). */
    fun initialize(context: Context) = MdbListQuotaStore.initialize(context)

    /**
     * Kayıtlı API anahtarı için son bilinen kotayı akışa yükler.
     * Ağ isteği yapmaz; ayarlar ekranı ilk karede gerçek (son okunan) değeri gösterir.
     */
    fun loadCachedQuota(apiKey: String): MdbListQuota? {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            quotaFingerprint = null
            _quota.value = null
            return null
        }
        val fingerprint = MdbListQuotaStore.fingerprint(trimmed)
        if (fingerprint != quotaFingerprint) {
            quotaFingerprint = fingerprint
            _quota.value = MdbListQuotaStore.load(trimmed, System.currentTimeMillis())
        }
        return _quota.value
    }

    /**
     * Kotayı `GET https://api.mdblist.com/user` üzerinden okur.
     *
     * Yanıtın `X-RateLimit-*` başlıkları tek başına yeterlidir; gövde yalnızca başlık
     * gelmediğinde ve HTTP 2xx ise ayrıştırılır. Not: bu istek de günlük kotadan 1 düşer,
     * bu yüzden otomatik çağrılar [QUOTA_STALE_AFTER_MS] ile sınırlandırılır.
     */
    suspend fun refreshQuota(apiKey: String, force: Boolean = true): MdbListQuota? =
        withContext(Dispatchers.IO) {
            val trimmed = apiKey.trim()
            if (trimmed.isEmpty()) return@withContext null
            val fingerprint = MdbListQuotaStore.fingerprint(trimmed)
            val now = System.currentTimeMillis()
            // Her kota okuması da günlük kotadan 1 düşer. Bu yüzden:
            //  - kullanıcı düğmeye bassa bile 5 sn'lik taban uygulanır,
            //  - otomatik yenileme kesin bir değer varken 15 dk'da bir,
            //    değer yoksa/başarısız olduysa 60 sn'de bir denenir.
            val last = lastQuotaRefreshAt[fingerprint] ?: 0L
            val minInterval = when {
                force -> QUOTA_MIN_REFRESH_INTERVAL_MS
                _quota.value?.authoritative == true -> QUOTA_STALE_AFTER_MS
                else -> QUOTA_RETRY_INTERVAL_MS
            }
            if (now - last < minInterval) {
                return@withContext loadCachedQuota(trimmed)
            }
            lastQuotaRefreshAt[fingerprint] = now

            val url = try {
                buildUserUrl(trimmed)
            } catch (e: Exception) {
                Log.w(TAG, "Kota URL'i oluşturulamadı (${e.javaClass.simpleName})")
                null
            } ?: return@withContext null
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()

            try {
                KitsugiHttpClient.mdbListClient.newCall(request).execute().use { response ->
                    val observedAt = System.currentTimeMillis()
                    // 401/403'te başlıklar olsa bile anahtar geçersizdir; kota yayınlanmaz.
                    var quota = if (response.isSuccessful || response.code == 429) {
                        MdbListQuotaParser.fromHeaders(response.headers, observedAt)
                    } else {
                        null
                    }
                    if (quota == null && response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            quota = MdbListQuotaParser.fromUserJson(body, observedAt)
                        }
                    }
                    val resolved = quota
                    when {
                        resolved != null -> publishQuota(trimmed, resolved)
                        response.code == 429 -> publishQuota(
                            trimmed,
                            MdbListQuotaStore.base(trimmed, observedAt).onRateLimited(
                                observedAt,
                                response.header("Retry-After")?.toLongOrNull()
                            )
                        )
                        else -> Log.w(TAG, "MDBList kota okunamadı: HTTP ${response.code}")
                    }
                    quota
                }
            } catch (e: Exception) {
                Log.w(TAG, "MDBList kota isteği başarısız (${e.javaClass.simpleName})")
                null
            }
        }

    /** Kotanın sunucudan yenilenmesi gerekip gerekmediğini söyler (ağ isteği yapmaz). */
    fun isQuotaStale(quota: MdbListQuota?, nowMillis: Long = System.currentTimeMillis()): Boolean =
        quota == null || nowMillis - quota.observedAtMillis > QUOTA_STALE_AFTER_MS

    /**
     * Akıştaki değerin verilen anahtara ait olup olmadığını söyler.
     * Ayarlarda anahtar değiştirildiği anda eski anahtarın kotası gösterilmesin diye.
     */
    fun quotaBelongsTo(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return false
        return quotaFingerprint == MdbListQuotaStore.fingerprint(trimmed)
    }

    private fun publishQuota(apiKey: String, quota: MdbListQuota) {
        MdbListQuotaStore.save(apiKey, quota)
        quotaFingerprint = MdbListQuotaStore.fingerprint(apiKey)
        _quota.value = quota
    }

    /**
     * Eski (`mdblist.com/api`) uç noktası `X-RateLimit-*` başlığı döndürmezse kota yalnızca
     * yerel sayaçla tahmin edilir. Bu durumda puan isteğini bekletmeden, arka planda tek bir
     * `/user` çağrısıyla gerçek değer alınır (15 dakikada en fazla bir kez).
     */
    private fun maybeRefreshQuotaInBackground(apiKey: String) {
        val current = _quota.value
        if (current?.authoritative == true && !isQuotaStale(current)) return
        quotaScope.launch { refreshQuota(apiKey, force = false) }
    }

    /**
     * Her MDBList yanıtından kotayı günceller: başlık varsa değer kesinleşir,
     * yoksa yerel sayaç bir azalır (arayüz bunu "tahmini" olarak etiketler).
     */
    private fun recordQuotaResponse(apiKey: String, response: Response) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        // Kimlik doğrulama hatasında istek işlenmediği için kotadan düşülmez.
        if (response.code == 401 || response.code == 403) return
        val fromHeaders = MdbListQuotaParser.fromHeaders(response.headers, now)
        val next: MdbListQuota = when {
            fromHeaders != null -> fromHeaders
            response.code == 429 -> MdbListQuotaStore.base(trimmed, now)
                .onRateLimited(now, response.header("Retry-After")?.toLongOrNull())
            else -> MdbListQuotaStore.base(trimmed, now).afterRequest(now)
        }
        publishQuota(trimmed, next)
    }

    /**
     * API anahtarını Matrix'in bilinen IMDb ID'si ile sınar.
     * Başarılı HTTP cevabına ek olarak MDBList'in response alanı da doğrulanır.
     */
    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext false
        val url = buildLookupUrl(apiKey = apiKey, imdbId = "tt0133093", tmdbId = null)
            ?: return@withContext false
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        try {
            val valid = KitsugiHttpClient.mdbListClient.newCall(request).execute().use { response ->
                // Doğrulama isteği de kotadan düşer; başlıklar geldiyse kalan hak hemen görünür.
                recordQuotaResponse(apiKey, response)
                if (!response.isSuccessful) {
                    Log.w(TAG, "MDBList key validation failed: HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body?.string() ?: return@withContext false
                val root = JSONObject(body)
                if (root.has("response") && !root.isNull("response")) {
                    isResponseAccepted(root)
                } else {
                    // Some response versions omit the response flag. Accept only a real
                    // Matrix rating payload, not an empty/error JSON object.
                    parseRatings(body, "tt0133093")?.isEmpty == false
                }
            }
            if (valid) maybeRefreshQuotaInBackground(apiKey)
            valid
        } catch (e: Exception) {
            Log.w(TAG, "MDBList key validation failed (${e.javaClass.simpleName})")
            false
        }
    }

    /** Existing IMDb-ID API retained for callers. */
    suspend fun fetchRatings(imdbId: String, apiKey: String): MdbListRatings? =
        fetchRatings(imdbId = imdbId, tmdbId = null, apiKey = apiKey)

    /**
     * Gets scores by IMDb ID, with TMDB ID as a fallback for catalogs without an IMDb mapping.
     * IMDb is preferred if both IDs are available.
     */
    suspend fun fetchRatings(
        imdbId: String?,
        tmdbId: Int?,
        apiKey: String
    ): MdbListRatings? = withContext(Dispatchers.IO) {
        val normalizedImdbId = imdbId?.trim()?.lowercase()?.takeIf(imdbIdPattern::matches)
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (apiKey.isBlank() || (normalizedImdbId == null && normalizedTmdbId == null)) {
            return@withContext null
        }

        // Detay sayfasındaki kota rozeti, puan isteğiyle aynı anahtarın kotasını göstersin.
        loadCachedQuota(apiKey)

        val identity = normalizedImdbId?.let { "imdb:$it" } ?: "tmdb:$normalizedTmdbId"
        val cacheKey = "${fingerprint(apiKey.trim())}:$identity"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { entry ->
            if (entry.expiresAt > now) return@withContext entry.ratings
            cache.remove(cacheKey, entry)
        }

        val url = buildLookupUrl(apiKey, normalizedImdbId, normalizedTmdbId)
            ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        val parsed = try {
            KitsugiHttpClient.mdbListClient.newCall(request).execute().use { response ->
                // Gerçek kota: X-RateLimit-* başlıkları varsa bu yanıttan okunur.
                recordQuotaResponse(apiKey, response)
                if (!response.isSuccessful) {
                    Log.w(TAG, "MDBList request failed: HTTP ${response.code} for $identity")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                parseRatings(body, normalizedImdbId)
            }
        } catch (e: Exception) {
            // Do not cache transient network failures; a later screen visit should retry.
            Log.w(TAG, "MDBList request failed (${e.javaClass.simpleName}) for $identity")
            null
        } ?: return@withContext null

        if (cache.size >= MAX_CACHE_ENTRIES) {
            val expiredKeys = cache.entries
                .filter { it.value.expiresAt <= now }
                .map { it.key }
            expiredKeys.forEach(cache::remove)
            if (cache.size >= MAX_CACHE_ENTRIES) cache.keys.firstOrNull()?.let(cache::remove)
        }
        cache[cacheKey] = CacheEntry(parsed, now + CACHE_TTL_MS)
        maybeRefreshQuotaInBackground(apiKey)
        parsed
    }

    /** `/user` kota uç noktası; birim testlerinde ağ olmadan doğrulanır. */
    internal fun buildUserUrl(apiKey: String): HttpUrl? {
        if (apiKey.isBlank()) return null
        return HttpUrl.Builder()
            .scheme("https")
            .host("api.mdblist.com")
            .addPathSegment("user")
            .addQueryParameter("apikey", apiKey.trim())
            .build()
    }

    internal fun buildLookupUrl(apiKey: String, imdbId: String?, tmdbId: Int?): HttpUrl? {
        if (apiKey.isBlank()) return null
        val normalizedImdbId = imdbId?.trim()?.lowercase()?.takeIf(imdbIdPattern::matches)
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return null

        return HttpUrl.Builder()
            .scheme("https")
            .host("mdblist.com")
            .addPathSegments("api/")
            .addQueryParameter("apikey", apiKey.trim())
            .apply {
                if (normalizedImdbId != null) addQueryParameter("i", normalizedImdbId)
                else addQueryParameter("tmdb", normalizedTmdbId.toString())
            }
            .build()
    }

    /** Kept internal so the API response contract can be tested without network access. */
    internal fun parseRatings(json: String, imdbId: String?): MdbListRatings? {
        return try {
            val root = JSONObject(json)
            if (!isResponseAccepted(root)) return null

            var imdb: Double? = null
            var tomatoes: Int? = null
            var tomatoesAudience: Int? = null
            var metacritic: Int? = null
            var letterboxd: Double? = null
            var tmdb: Double? = null
            var trakt: Double? = null

            val ratings = root.optJSONArray("ratings") ?: root.optJSONArray("scores")
            if (ratings != null) {
                for (index in 0 until ratings.length()) {
                    val item = ratings.optJSONObject(index) ?: continue
                    val category = scoreCategory(
                        source = item.optString("source"),
                        name = item.optString("name"),
                        type = item.optString("type")
                    ) ?: continue
                    val raw = firstNumber(item.opt("value"), item.opt("rating"))
                        ?: scoreFallback(item.opt("score"), category)
                        ?: continue

                    when (category) {
                        ScoreCategory.IMDB -> if (imdb == null) imdb = raw
                        ScoreCategory.TOMATOES -> if (tomatoes == null) tomatoes = raw.toInt().coerceIn(0, 100)
                        ScoreCategory.TOMATOES_AUDIENCE -> if (tomatoesAudience == null) tomatoesAudience = raw.toInt().coerceIn(0, 100)
                        ScoreCategory.METACRITIC -> if (metacritic == null) metacritic = raw.toInt().coerceIn(0, 100)
                        ScoreCategory.LETTERBOXD -> if (letterboxd == null) letterboxd = raw
                        ScoreCategory.TMDB -> if (tmdb == null) tmdb = raw
                        ScoreCategory.TRAKT -> if (trakt == null) trakt = raw
                    }
                }
                return MdbListRatings(
                    imdb = imdb,
                    tomatoes = tomatoes,
                    tomatoesAudience = tomatoesAudience,
                    metacritic = metacritic,
                    letterboxd = letterboxd,
                    tmdb = tmdb,
                    trakt = trakt,
                    imdbId = imdbId ?: root.optString("imdbid").takeIf(imdbIdPattern::matches)
                ).withoutEmptyScores()
            }

            // Older MDBList responses expose scores as top-level fields.
            val legacy = MdbListRatings(
                imdb = firstNumber(root.opt("imdbrating")),
                tomatoes = firstNumber(root.opt("tomatoesrating"))?.toInt()?.coerceIn(0, 100),
                tomatoesAudience = firstNumber(root.opt("tomatoesaudiencerating"))?.toInt()?.coerceIn(0, 100),
                metacritic = firstNumber(root.opt("metacriticrating"))?.toInt()?.coerceIn(0, 100),
                letterboxd = firstNumber(root.opt("letterboxdrating")),
                tmdb = firstNumber(root.opt("tmdbrating")),
                trakt = firstNumber(root.opt("traktrating")),
                imdbId = imdbId ?: root.optString("imdbid").takeIf(imdbIdPattern::matches)
            ).withoutEmptyScores()

            when {
                !legacy.isEmpty -> legacy
                // A valid response with an empty ratings array is a real negative lookup and may be cached.
                root.has("response") || root.has("ratings") || root.has("scores") -> legacy
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "MDBList JSON parse failed (${e.javaClass.simpleName})")
            null
        }
    }

    private fun isResponseAccepted(root: JSONObject): Boolean {
        if (!root.has("response") || root.isNull("response")) return true
        return when (val response = root.opt("response")) {
            is Boolean -> response
            is Number -> response.toInt() == 1
            is String -> response.trim().equals("true", ignoreCase = true) || response.trim() == "1"
            else -> false
        }
    }

    private enum class ScoreCategory {
        IMDB, TOMATOES, TOMATOES_AUDIENCE, METACRITIC, LETTERBOXD, TMDB, TRAKT
    }

    private fun scoreCategory(source: String, name: String, type: String): ScoreCategory? {
        val normalized = "$source $name $type".lowercase().filter(Char::isLetterOrDigit)
        return when {
            normalized.contains("tomato") &&
                (normalized.contains("audience") || normalized.contains("user")) -> ScoreCategory.TOMATOES_AUDIENCE
            normalized.contains("tomato") || normalized.contains("tomatometer") -> ScoreCategory.TOMATOES
            normalized.contains("metacritic") || normalized.contains("metascore") -> ScoreCategory.METACRITIC
            normalized.contains("letterboxd") -> ScoreCategory.LETTERBOXD
            normalized.contains("imdb") -> ScoreCategory.IMDB
            normalized.contains("trakt") -> ScoreCategory.TRAKT
            normalized.contains("tmdb") -> ScoreCategory.TMDB
            else -> null
        }
    }

    /** Prefer MDBList's human-readable value (e.g. "8.2/10" or "91%") over its normalized score. */
    private fun firstNumber(vararg values: Any?): Double? = values.firstNotNullOfOrNull(::parseNumber)

    private fun parseNumber(value: Any?): Double? {
        val text = when (value) {
            null, JSONObject.NULL -> return null
            is Number -> value.toString()
            is String -> value.trim()
            else -> return null
        }
        val match = Regex("[-+]?\\d+(?:\\.\\d+)?").find(text) ?: return null
        return match.value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun scoreFallback(value: Any?, category: ScoreCategory): Double? {
        val score = parseNumber(value) ?: return null
        return when (category) {
            ScoreCategory.IMDB, ScoreCategory.TMDB, ScoreCategory.TRAKT ->
                if (score > 10.0 && score <= 100.0) score / 10.0 else score
            ScoreCategory.LETTERBOXD ->
                if (score > 5.0 && score <= 100.0) score / 20.0 else score
            ScoreCategory.TOMATOES, ScoreCategory.TOMATOES_AUDIENCE, ScoreCategory.METACRITIC -> score
        }
    }

    private fun fingerprint(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /** Tests may clear the process-local cache between scenarios. */
    internal fun clearCache() = cache.clear()
}

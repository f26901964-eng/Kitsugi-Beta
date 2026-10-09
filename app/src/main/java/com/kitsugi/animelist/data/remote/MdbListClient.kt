package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
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

object MdbListClient {

    private const val TAG = "MdbListClient"
    private const val BASE_URL = "https://mdblist.com/api/"
    private const val CACHE_TTL_MS = 30L * 60L * 1_000L
    private const val MAX_CACHE_ENTRIES = 500
    private val imdbIdPattern = Regex("^tt\\d{5,12}$", RegexOption.IGNORE_CASE)

    private data class CacheEntry(val ratings: MdbListRatings, val expiresAt: Long)
    private val cache = BoundedCache<String, CacheEntry>("mdbList", 300)

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
            KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
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
            KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
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
        parsed
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
                )
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
            )

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

package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.kitsugi.animelist.utils.cleanShikimoriBbCode

class RateLimitException(message: String) : java.io.IOException(message)
class ResourceNotFoundException(message: String) : java.io.IOException(message)

/**
 * AniList API sunucu tarafında geçici olarak devre dışı bırakıldığında fırlatılır.
 * (HTTP 403 + "temporarily disabled due to severe stability issues" mesajı)
 * Bu exception ExploreViewModel fallback mekanizmasını tetikler.
 */
class AniListServiceDownException(message: String) : Exception(message)

object KitsugiApiBase {
    /**
     * Host bazlı hız bütçesi. Jikan ve Shikimori'nin RESMİ limitleri ayrı ayrı uygulanır:
     *  - Jikan (docs.api.jikan.moe): 3 istek/sn VE 60 istek/dk. Eski 450 ms aralık tek başına
     *    dakikada ~133 istek üretiyordu → 60/dk aşılınca 429 geliyor, karakter/ekip/ilişki
     *    listeleri boş ("bulunamadı") ya da skeleton'da kalıyordu. Artık hem aralık hem de
     *    kayan 60 sn penceresi uygulanır (güvenlik payı ile 55/dk).
     *  - Shikimori: 5 istek/sn sınırı için 450 ms aralık; dakika için güvenli üst sınır (80/dk).
     */
    private class HostBudget(
        val minIntervalMs: Long,
        val perMinute: Int
    ) {
        /** Bir sonraki isteğin başlayabileceği en erken an (ms). */
        var nextSlotAt: Long = 0L
        /** Son 60 sn içinde başlatılan isteklerin zamanları (kayan pencere). */
        val recentStarts = ArrayDeque<Long>()
    }

    private val hostBudgets: Map<String, HostBudget> = mapOf(
        // api.jikan.moe bilerek burada YOK: Jikan kotası JikanGateway tarafından yönetilir
        // (tek kaynak, çift sayım ve çift bekleme olmasın diye).
        // Aralık eskisi gibi (450 ms): Shikimori GraphQL (PlatformRateLimiter) aynı 5 istek/sn
        // havuzunu paylaşıyor; REST aralığını kısaltmak toplamda 429 riskini artırır.
        "shikimori.io" to HostBudget(minIntervalMs = 450L, perMinute = 80),
        "shikimori.one" to HostBudget(minIntervalMs = 450L, perMinute = 80),
        "shikimori.me" to HostBudget(minIntervalMs = 450L, perMinute = 80)
    )

    /** Hosttaki (bütçesi olan) bütçeyi döndürür; yoksa null. */
    private fun budgetFor(host: String?): HostBudget? {
        if (host == null) return null
        return hostBudgets[host.lowercase()]
    }

    /**
     * Bu istek için gereken bekleme süresini (ms) hesaplayıp slotu REZERVE eder.
     * Kilit yalnızca hesap için tutulur; bekleme çağıran tarafta yapılır.
     */
    private fun reserveSlotMs(budget: HostBudget): Long = synchronized(budget) {
        val now = System.currentTimeMillis()
        while (budget.recentStarts.isNotEmpty() && now - budget.recentStarts.first() >= 60_000L) {
            budget.recentStarts.removeFirst()
        }
        var slotAt = maxOf(now, budget.nextSlotAt)
        if (budget.recentStarts.size >= budget.perMinute) {
            // Dakika penceresi dolu: en eski isteğin 60 sn'si dolana kadar bekle.
            slotAt = maxOf(slotAt, budget.recentStarts.first() + 60_000L)
        }
        budget.nextSlotAt = slotAt + budget.minIntervalMs
        budget.recentStarts.addLast(slotAt)
        slotAt - now
    }

    /**
     * Jikan/Shikimori isteğini hız bütçesine göre bekletir. Her HTTP denemesi (yeniden
     * denemeler dâhil) tek slot tüketir; bu yüzden tüm GET yolları [performGet] içinde
     * sınırlanır. Blok kendi kendine ek bekleme yapmaz (çift sayım olmasın diye pass-through).
     *
     * ÖNEMLİ: Yalnızca IO thread'lerinde çağrılmalıdır (bekleme `Thread.sleep` ile yapılır).
     */
    suspend fun <T> runWithRateLimit(block: suspend () -> T): T = block()

    /** 429 yanıtında host bütçesinin [delayMs] kadar durmasını sağlar (ortak geri çekilme). */
    private fun penalizeBudget(host: String?, delayMs: Long) {
        val budget = budgetFor(host) ?: return
        synchronized(budget) {
            budget.nextSlotAt = maxOf(budget.nextSlotAt, System.currentTimeMillis() + delayMs)
        }
    }

    /** Hız sınırlı host ise slotu bekler; değilse hemen döner. */
    private fun awaitBudgetSync(host: String?) {
        val budget = budgetFor(host) ?: return
        val waitMs = reserveSlotMs(budget)
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    // ─── AniList'e özel hız sınırlama (Jikan'dan bağımsız) ──────────────────────
    // AniList resmi limiti dakikada ~90 istek; güvenli tarafta kalmak için
    // istekler arası minimum 700ms ve aynı anda tek istek (Mutex) uygulanır.
    private const val ANILIST_MIN_INTERVAL_MS = 700L
    private const val ANILIST_MAX_RETRIES = 3
    private val aniListMutex = Mutex()
    private var lastAniListRequestTime: Long = 0L

    private suspend fun waitForAniListWindow() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastAniListRequestTime
        val waitMs = ANILIST_MIN_INTERVAL_MS - elapsed

        if (waitMs > 0) {
            delay(waitMs)
        }

        lastAniListRequestTime = System.currentTimeMillis()
    }

    private data class RawGetResult(
        val code: Int,
        val body: String?,
        val retryAfterMs: Long?
    )

    private fun performGet(url: URL): RawGetResult {
        if (JikanGateway.isJikanUrl(url)) return jikanRawGet(url)
        awaitBudgetSync(url.host)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                val body = if (response.isSuccessful) response.body?.string() else null
                val retryAfterMs = if (response.code == 429 || response.code in 500..599) {
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    retryAfterSec?.let { (it * 1_000L).coerceIn(0L, 10_000L) }
                } else null
                if (response.code == 429) {
                    // Sunucu limiti aştı: aynı host'taki sonraki istekler de kısa süre beklesin.
                    penalizeBudget(url.host, retryAfterMs ?: 2_000L)
                }
                if (body == null) {
                    if (response.code == 429) {
                        android.util.Log.w("KitsugiApiBase", "HTTP 429 Too Many Requests: Rate limit hit for URL: $url")
                    } else {
                        android.util.Log.w("KitsugiApiBase", "HTTP Error: ${response.code} ${response.message} for URL: $url")
                    }
                }
                RawGetResult(response.code, body, retryAfterMs)
            }
        } catch (e: Exception) {
            android.util.Log.e("KitsugiApiBase", "executeGetRequest Exception: ${e.message} for URL: $url", e)
            RawGetResult(0, null, null)
        }
    }

    /**
     * Jikan isteklerini [JikanGateway] üzerinden yapar. Yeniden deneme (429/5xx) çağıran
     * tarafın `executeGetRequestResilient` döngüsünde kalır; kapı kendi içinde ayrıca
     * denemediği için kota çarpanla tüketilmez.
     */
    private fun jikanRawGet(url: URL): RawGetResult =
        when (val r = JikanGateway.fetchBlocking(url.toString(), maxRetries = 0)) {
            is JikanResult.Ok -> RawGetResult(200, r.body, null)
            is JikanResult.NotFound -> RawGetResult(404, null, null)
            is JikanResult.RateLimited -> RawGetResult(429, null, r.retryAfterMs)
            is JikanResult.Failed -> RawGetResult(r.code, null, null)
        }

    fun executeGetRequest(url: URL): String? {
        val result = performGet(url)
        return result.body
    }

    /**
     * 429 (rate-limit) ve 5xx (geçici sunucu hatası) durumlarında kısa bir beklemeyle
     * sınırlı sayıda tekrar deneyen GET.
     *
     * Jikan/Shikimori gibi rate-limit'li kaynaklarda tek seferlik 429 gelirse veri
     * "eksik/boş" dönmek yerine (karakter listesi, ekip, ilişkiler vb.) Retry-After
     * başlığına uyarak (en fazla 5 sn) üstel beklemeyle yeniden çekilir. Kalıcı 4xx
     * hataları (404 vb.) yeniden denenmez.
     *
     * NOT: `Dispatchers.IO` üzerinde çağrılmalıdır (suspend bekleme kullanır).
     */
    suspend fun executeGetRequestResilient(url: URL, maxRetries: Int = 3): String? {
        var attempt = 0
        while (true) {
            val result = performGet(url)
            if (result.body != null) return result.body
            val retryable = result.code == 429 || result.code in 500..599
            if (!retryable || attempt >= maxRetries) return null
            val backoffMs = (result.retryAfterMs ?: (1_000L * (1 shl attempt))).coerceAtMost(5_000L)
            android.util.Log.w("KitsugiApiBase", "HTTP ${result.code} → ${backoffMs}ms bekleme ile yeniden deneniyor (deneme ${attempt + 1}/$maxRetries): $url")
            kotlinx.coroutines.delay(backoffMs)
            attempt++
        }
    }

    fun executeGetRequestOrThrow(url: URL): String {
        if (JikanGateway.isJikanUrl(url)) {
            return when (val r = JikanGateway.fetchBlocking(url.toString(), maxRetries = 0)) {
                is JikanResult.Ok -> r.body
                is JikanResult.NotFound -> throw ResourceNotFoundException("HTTP 404 Not Found for URL: $url")
                is JikanResult.RateLimited -> throw RateLimitException("HTTP 429 Too Many Requests: Rate limit hit for URL: $url")
                is JikanResult.Failed -> throw java.io.IOException("HTTP Error ${r.code}: ${r.message} for URL: $url")
            }
        }
        awaitBudgetSync(url.host)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.string().orEmpty()
            } else {
                if (response.code == 429) {
                    penalizeBudget(
                        url.host,
                        response.header("Retry-After")?.toLongOrNull()?.times(1_000L)?.coerceIn(0L, 10_000L) ?: 2_000L
                    )
                    throw RateLimitException("HTTP 429 Too Many Requests: Rate limit hit for URL: $url")
                } else if (response.code == 404) {
                    throw ResourceNotFoundException("HTTP 404 Not Found for URL: $url")
                } else {
                    throw java.io.IOException("HTTP Error ${response.code}: ${response.message} for URL: $url")
                }
            }
        }
    }

    /**
     * AniList GraphQL sorgusu çalıştırır.
     *
     * Hız sınırlama (rate limiting) ve 429 dayanıklılığı BURADA yönetilir:
     *  - [aniListMutex] ile aynı anda tek istek gider (paralel patlamayı önler).
     *  - Her istekten önce [waitForAniListWindow] ile ~700ms bekleme uygulanır.
     *  - 429 (Too Many Requests) veya 5xx gelirse, "Retry-After" başlığına saygı
     *    göstererek üstel bekleme ile [ANILIST_MAX_RETRIES] kez tekrar denenir.
     *
     * NOT: Fonksiyon artık `suspend`. Tüm çağıranlar zaten suspend/withContext(IO)
     * bağlamında olduğu için imza değişikliği çağrı yerlerini bozmaz.
     */
    suspend fun executeAniListQuery(query: String, variables: JSONObject, accessToken: String? = null): String? {
        val requestBody = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()

        val token = if (!accessToken.isNullOrBlank()) {
            accessToken
        } else {
            KitsugiApplication.getInstance()?.let { ctx ->
                ExternalAuthManager.getAniListToken(ctx)
            }
        }

        var attempt = 0
        var tokenFailed = false
        while (true) {
            val currentToken = if (tokenFailed) null else token
            // Sırayla + throttle uygulayarak tek istek gönder
            val result = aniListMutex.withLock {
                waitForAniListWindow()
                performAniListRequest(requestBody, currentToken)
            }

            when (result) {
                is AniListResult.ServiceDown -> {
                    // AniList sunucu tarafında tamamen kapalı; retry veya token dönüşümü anlamsız.
                    android.util.Log.e("KitsugiApiBase", "AniList servis kesintisi: ${result.message}")
                    throw AniListServiceDownException(
                        "AniList API geçici olarak devre dışı: ${result.message}"
                    )
                }
                is AniListResult.Success -> {
                    // JSON içindeki authorization veya invalid token hatalarını kontrol et
                    val hasAuthError = try {
                        val root = JSONObject(result.body)
                        val errors = root.optJSONArray("errors")
                        if (errors != null && errors.length() > 0) {
                            val msg = errors.optJSONObject(0)?.optString("message").orEmpty().lowercase()
                            msg.contains("invalid token") || msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("invalid credentials")
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    if (hasAuthError && !currentToken.isNullOrBlank()) {
                        tokenFailed = true
                        continue
                    }
                    return result.body
                }
                is AniListResult.Failure -> {
                    if (!currentToken.isNullOrBlank()) {
                        // Token geçersiz veya yetkisiz, tokensız tekrar dene
                        tokenFailed = true
                        continue
                    }
                    return null   // Kalıcı hata (4xx, retry anlamsız)
                }
                is AniListResult.Retryable -> {
                    if (attempt >= ANILIST_MAX_RETRIES) return null
                    // Retry-After (saniye) verilmişse ona uy; yoksa üstel bekleme
                    val backoffMs = result.retryAfterMs
                        ?: (1_000L * (1 shl attempt))   // 1s, 2s, 4s...
                    delay(backoffMs)
                    attempt++
                }
            }
        }
    }

    private sealed class AniListResult {
        data class Success(val body: String) : AniListResult()
        data object Failure : AniListResult()
        data class Retryable(val retryAfterMs: Long?) : AniListResult()
        /** AniList sunucu tarafında tamamen kapalı — retry anlamsız, fallback gerekli */
        data class ServiceDown(val message: String) : AniListResult()
    }

    private fun performAniListRequest(requestBody: String, token: String?): AniListResult {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = requestBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    AniListResult.Success(responseBody)
                } else if (response.code == 429 || response.code in 500..599) {
                    android.util.Log.w("KitsugiApiBase", "AniList query retryable failure with code: ${response.code} ${response.message}")
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    val resetEpoch = response.header("X-RateLimit-Reset")?.toLongOrNull()
                    val retryAfterMs = when {
                        retryAfterSec != null -> retryAfterSec * 1_000L
                        resetEpoch != null -> (resetEpoch * 1_000L - System.currentTimeMillis()).coerceAtLeast(0L)
                        else -> null
                    }
                    AniListResult.Retryable(retryAfterMs)
                } else {
                    // 403 için özel kontrol: AniList servis kesintisi mi?
                    val errBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                    val serviceDownMsg = runCatching {
                        val root = JSONObject(errBody)
                        val errors = root.optJSONArray("errors")
                        if (errors != null && errors.length() > 0) {
                            errors.optJSONObject(0)?.optString("message")
                        } else null
                    }.getOrNull()
                    if (serviceDownMsg != null && (
                            serviceDownMsg.contains("temporarily disabled", ignoreCase = true) ||
                            serviceDownMsg.contains("stability issues", ignoreCase = true)
                        )
                    ) {
                        android.util.Log.w("KitsugiApiBase", "AniList servis kesintisi tespit edildi: $serviceDownMsg")
                        AniListResult.ServiceDown(serviceDownMsg)
                    } else {
                        android.util.Log.e("KitsugiApiBase", "AniList query permanent failure with code: ${response.code} ${response.message}")
                        AniListResult.Failure
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("KitsugiApiBase", "AniList performRequest Exception: ${e.message}", e)
            AniListResult.Retryable(null)
        }
    }

    fun classifyNetworkError(error: Throwable): NetworkErrorCategory {
        val message = error.message.orEmpty()

        val isRateLimited = message.contains("429") ||
                message.contains("rate", ignoreCase = true) ||
                message.contains("limit", ignoreCase = true)

        if (isRateLimited) {
            return NetworkErrorCategory.RateLimited
        }

        val isGatewayProblem = message.contains("504") ||
                message.contains("502") ||
                message.contains("503") ||
                message.contains("gateway", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true)

        if (isGatewayProblem) {
            return NetworkErrorCategory.GatewayProblem
        }

        return NetworkErrorCategory.Other
    }

    enum class NetworkErrorCategory {
        RateLimited,
        GatewayProblem,
        Other
    }
}

// JSON and String helper extensions
fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return if (value == "null" || value.isBlank()) null else value
}

fun JSONObject.optionalPositiveInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = optInt(key, 0)
    return if (value > 0) value else null
}

fun JSONObject.namesFromObjectArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    val names = mutableListOf<String>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val name = item.optString("name")
        if (name.isNotBlank() && name != "null") {
            names.add(name)
        }
    }

    return names
}

fun JSONObject.namesFromStringArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    val names = mutableListOf<String>()

    for (index in 0 until array.length()) {
        val name = array.optString(index)
        if (name.isNotBlank() && name != "null") {
            names.add(name)
        }
    }

    return names
}

fun String.cleanApiText(): String {
    return this
        .cleanShikimoriBbCode()
        .replace("<br>", "\n")
        .replace("<br />", "\n")
        .replace("<br/>", "\n")
        .replace("<i>", "")
        .replace("</i>", "")
        .replace("<b>", "")
        .replace("</b>", "")
        .replace(Regex("<.*?>"), "")
        // HTML entity decode
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&apos;", "'")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&ldquo;", "\u201C")
        .replace("&rdquo;", "\u201D")
        .replace("&lsquo;", "\u2018")
        .replace("&rsquo;", "\u2019")
        .replace("&mdash;", "\u2014")
        .replace("&ndash;", "\u2013")
        .replace("&hellip;", "\u2026")
        // Remove excessive blank lines (3+ newlines → 2 newlines)
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import kotlinx.coroutines.delay
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
        "shikimori.me" to HostBudget(minIntervalMs = 450L, perMinute = 80),
        "shikimori.tv" to HostBudget(minIntervalMs = 450L, perMinute = 80)
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

    // ─── AniList'e özel uyarlanabilir hız sınırlama ve devre kesici ────────────
    // Eskiden tüm uygulama tek bir global Mutex içinde performAniListRequest bitene
    // kadar kilitleniyordu (maxConcurrency = 1 + 700ms + RetryInterceptor çarpanı).
    // ─── AniList hız bütçesi artık TEK yerde: AniListGovernor ────────────────────
    // Eski kod burada 350 ms (≈171 istek/dk) / "degraded" modda 1500 ms (≈40 istek/dk)
    // aralıkla çalışıyordu. AniList fiilî limiti 30 istek/dk olduğu için her iki değer
    // de limitin ÜSTÜNDEYDI: uygulama AniList'i sürekli 429'a düşürüyordu. Aralık
    // artık X-RateLimit-Limit başlığından hesaplanıyor (limit/60 sn) ve import +
    // mutasyon yolları da aynı bütçeden harcıyor. Ayrıntılar: AniListGovernor.
    private const val ANILIST_MAX_RETRIES = 1

    /** AniList şu anda kapalı, 429 limitinde ya da gateway kesintisinde mi? */
    fun isAniListUnavailableNow(): Boolean =
        com.kitsugi.animelist.data.auth.AniListGovernor.isRateLimitedNow() ||
            com.kitsugi.animelist.data.auth.AniListGovernor.isServiceDownNow()

    /**
     * Ham JSON veya hata metni içinde AniList'in sunucu şalteri mesajını arar.
     * AniList bu mesajı bazen HTTP 403, bazen HTTP 503/500, bazen de HTTP 200
     * yanıtının `errors` dizisi içinde döndürür.
     */
    fun extractAniListServiceDownMessage(bodyOrMessage: String?): String? =
        com.kitsugi.animelist.data.auth.AniListGovernor.detectServiceDown(bodyOrMessage)

    /**
     * AniList servis kesintisini global devre kesiciye ve [com.kitsugi.animelist.data.platform.PlatformFailureRegistry]'ye
     * kaydedip standart [AniListServiceDownException] üretir.
     */
    fun recordAniListServiceDown(
        rawReason: String,
        httpCode: Int? = 403
    ): AniListServiceDownException {
        val cleanReason = com.kitsugi.animelist.data.auth.AniListGovernor.markServiceDown(
            reason = rawReason,
            code = httpCode ?: 403
        )
        val fullMessage = "AniList API geçici olarak devre dışı: $cleanReason"
        return AniListServiceDownException(fullMessage)
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
                    val safeUrl = SensitiveUrlRedactor.redact(url.toString())
                    if (response.code == 429) {
                        android.util.Log.w("KitsugiApiBase", "HTTP 429 Too Many Requests: Rate limit hit for URL: $safeUrl")
                    } else {
                        android.util.Log.w("KitsugiApiBase", "HTTP Error: ${response.code} ${response.message} for URL: $safeUrl")
                    }
                }
                RawGetResult(response.code, body, retryAfterMs)
            }
        } catch (e: Exception) {
            val safeUrl = SensitiveUrlRedactor.redact(url.toString())
            android.util.Log.e("KitsugiApiBase", "executeGetRequest failed (${e.javaClass.simpleName}) for URL: $safeUrl")
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

    fun executeGetRequestWithHeaders(url: URL, headers: Map<String, String>): String? {
        awaitBudgetSync(url.host)
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val request = reqBuilder.build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            val safeUrl = SensitiveUrlRedactor.redact(url.toString())
            android.util.Log.e("KitsugiApiBase", "executeGetRequestWithHeaders failed (${e.javaClass.simpleName}) for URL: $safeUrl")
            null
        }
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
            android.util.Log.w(
                "KitsugiApiBase",
                "HTTP ${result.code} → ${backoffMs}ms bekleme ile yeniden deneniyor (deneme ${attempt + 1}/$maxRetries): ${SensitiveUrlRedactor.redact(url.toString())}"
            )
            kotlinx.coroutines.delay(backoffMs)
            attempt++
        }
    }

    fun executeGetRequestOrThrow(url: URL): String {
        if (JikanGateway.isJikanUrl(url)) {
            val safeUrl = SensitiveUrlRedactor.redact(url.toString())
            return when (val r = JikanGateway.fetchBlocking(url.toString(), maxRetries = 0)) {
                is JikanResult.Ok -> r.body
                is JikanResult.NotFound -> throw ResourceNotFoundException("HTTP 404 Not Found for URL: $safeUrl")
                is JikanResult.RateLimited -> throw RateLimitException("HTTP 429 Too Many Requests: Rate limit hit for URL: $safeUrl")
                is JikanResult.Failed -> throw java.io.IOException("HTTP Error ${r.code}: ${r.message} for URL: $safeUrl")
            }
        }
        awaitBudgetSync(url.host)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")
            .build()

        val safeUrl = SensitiveUrlRedactor.redact(url.toString())
        com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.string().orEmpty()
            } else {
                if (response.code == 429) {
                    penalizeBudget(
                        url.host,
                        response.header("Retry-After")?.toLongOrNull()?.times(1_000L)?.coerceIn(0L, 10_000L) ?: 2_000L
                    )
                    throw RateLimitException("HTTP 429 Too Many Requests: Rate limit hit for URL: $safeUrl")
                } else if (response.code == 404) {
                    throw ResourceNotFoundException("HTTP 404 Not Found for URL: $safeUrl")
                } else {
                    throw java.io.IOException("HTTP Error ${response.code}: ${response.message} for URL: $safeUrl")
                }
            }
        }
    }

    /**
     * AniList GraphQL sorgusu çalıştırır (Keşfet / detay / bildirim / takvim / profil).
     *
     * Hız sınırlama, 429 dayanıklılığı ve kesinti (devre kesici) yönetimi TEK merkezde:
     * [com.kitsugi.animelist.data.auth.AniListGovernor]. Liste içe aktarımı
     * ([com.kitsugi.animelist.data.auth.AniListImportManager]) ve yazma/mutasyon yolu
     * ([com.kitsugi.animelist.data.auth.AniListSyncManager]) de aynı bütçeden harcar;
     * böylece AniList'in 30 istek/dk sınırı üç yol tarafından ayrı ayrı aşılmaz.
     *
     * @param maxCooldownWaitMs 429 soğuması için en fazla bu kadar beklenir. Sorgular
     *        (UI yolları) kısa tutar ve hızlıca `null` dönerek yedek zincire düşer;
     *        liste içe aktarımı gibi arka plan işleri daha uzun bekleyebilir.
     * @return Başarılı yanıtın gövdesi; kullanılamıyorsa `null`.
     */
    suspend fun executeAniListQuery(
        query: String,
        variables: JSONObject,
        accessToken: String? = null,
        throwOnServiceDown: Boolean = false,
        maxCooldownWaitMs: Long = ANILIST_QUERY_COOLDOWN_WAIT_MS
    ): String? {
        val governor = com.kitsugi.animelist.data.auth.AniListGovernor

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
            // 1) Tek bütçeden slot al: soğuma / kesinti varsa bloke et, gerekirse bekle.
            when (val gate = governor.acquire(maxWaitMs = maxCooldownWaitMs)) {
                is com.kitsugi.animelist.data.auth.AniListGovernor.Gate.Proceed -> Unit
                is com.kitsugi.animelist.data.auth.AniListGovernor.Gate.Blocked -> {
                    val isServiceDown =
                        gate.reason == com.kitsugi.animelist.data.auth.AniListGovernor.BlockedReason.SERVICE_DOWN
                    com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
                        platformLabel = "anilist",
                        httpCode = when (gate.reason) {
                            com.kitsugi.animelist.data.auth.AniListGovernor.BlockedReason.RATE_LIMITED -> 429
                            com.kitsugi.animelist.data.auth.AniListGovernor.BlockedReason.SERVICE_DOWN -> 403
                            com.kitsugi.animelist.data.auth.AniListGovernor.BlockedReason.GATEWAY_DOWN -> 503
                        },
                        rawMessage = if (isServiceDown) {
                            "AniList API geçici olarak devre dışı: ${governor.serviceDownMessage()}"
                        } else if (gate.reason == com.kitsugi.animelist.data.auth.AniListGovernor.BlockedReason.GATEWAY_DOWN) {
                            "HTTP 503 · graphql.anilist.co gateway unavailable"
                        } else {
                            "HTTP 429 Too Many Requests · graphql.anilist.co (${gate.retryAfterMs / 1000}s soğuma)"
                        }
                    )
                    if (isServiceDown && throwOnServiceDown) {
                        throw AniListServiceDownException(
                            "AniList API geçici olarak devre dışı: ${governor.serviceDownMessage()}"
                        )
                    }
                    return null
                }
            }

            val currentToken = if (tokenFailed) null else token
            val call = performAniListRequest(requestBody, currentToken)

            // 2) Taşıma hatası (timeout / DNS / socket)
            if (call.error != null) {
                governor.observeTransportFailure(call.error)
                com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report("anilist", error = call.error)
                if (attempt >= ANILIST_MAX_RETRIES) return null
                delay(1_000L * (1 shl attempt))
                attempt++
                continue
            }

            val response = call.response
            val body = call.body.orEmpty()
            if (response == null) {
                if (attempt >= ANILIST_MAX_RETRIES) return null
                delay(1_000L * (1 shl attempt))
                attempt++
                continue
            }

            // 3) HTTP katmanı: başlıklardan kotayı öğren, 429/5xx/kesintiyi sınıflandır.
            when (val outcome = governor.observeResponse(response, body)) {
                is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.Success -> {
                    // 4) GraphQL katmanı: AniList 429'u bazen HTTP 200 + errors[status=429]
                    //    olarak döner — "boş sonuç" sanılmamalı.
                    val errors = runCatching { org.json.JSONObject(body).optJSONArray("errors") }.getOrNull()
                    when (val classified = governor.classifyErrors(errors)) {
                        is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.Success -> return body
                        is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.AuthFailure -> {
                            // Token geçersiz: tokensız bir kez daha dene (genel katalog sorgusu olabilir).
                            if (!currentToken.isNullOrBlank()) {
                                tokenFailed = true
                                continue
                            }
                            return null
                        }
                        is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.RateLimited -> {
                            if (attempt >= ANILIST_MAX_RETRIES) return null
                            // Soğuma zaten kuruldu; acquire() süre bitene kadar bekler.
                            attempt++
                            continue
                        }
                        else -> return null
                    }
                }
                is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.RateLimited -> {
                    com.kitsugi.animelist.data.platform.PlatformFailureRegistry.report(
                        platformLabel = "anilist",
                        httpCode = 429,
                        rawMessage = "HTTP 429 Too Many Requests · graphql.anilist.co (${outcome.retryAfterMs / 1000}s)"
                    )
                    if (attempt >= ANILIST_MAX_RETRIES) return null
                    attempt++
                    continue
                }
                is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.Retryable -> {
                    if (attempt >= ANILIST_MAX_RETRIES) return null
                    delay(outcome.retryAfterMs.coerceIn(500L, 4_000L))
                    attempt++
                    continue
                }
                is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.AuthFailure -> {
                    if (!currentToken.isNullOrBlank()) {
                        tokenFailed = true
                        continue
                    }
                    return null
                }
                is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.ServiceDown -> {
                    if (throwOnServiceDown) {
                        throw AniListServiceDownException(
                            "AniList API geçici olarak devre dışı: ${outcome.message}"
                        )
                    }
                    return null
                }
                is com.kitsugi.animelist.data.auth.AniListGovernor.Outcome.Permanent -> return null
            }
        }
    }

    /** Sorgu (UI) yollarında 429 soğuması için beklenen üst sınır. */
    private const val ANILIST_QUERY_COOLDOWN_WAIT_MS = 8_000L

    private data class AniListCall(
        val response: okhttp3.Response?,
        val body: String?,
        val error: Throwable?
    )

    private fun performAniListRequest(requestBody: String, token: String?): AniListCall {
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

        // Not: gövde OkHttp'de tek seferliktir; burada okunup hem çağırana hem de
        // hız yöneticisine (başlıklar hâlâ erişilebilir) aynı metin verilir.
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request).execute()
                .use { response ->
                    val responseBody = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                    AniListCall(response = response, body = responseBody, error = null)
                }
        } catch (e: Exception) {
            AniListCall(response = null, body = null, error = e)
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

package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URL

/** Jikan'ın (api.jikan.moe/v4) resmi kotaları ve sunucu tarafı önbelleği için sonuç tipi. */
sealed class JikanResult {
    class Ok(val body: String) : JikanResult()
    object NotFound : JikanResult()
    class RateLimited(val retryAfterMs: Long) : JikanResult()
    class Failed(val code: Int, val message: String) : JikanResult()
}

/**
 * Jikan için TEK merkezli istemci. Tüm MAL/Jikan çağrıları (detay, karakter, ekip, ilişki,
 * öneri, keşfet, arama, profil, yedek eşleştirme) bu kapıdan geçer; böylece:
 *
 *  1. **Hız limiti (resmi):** docs.api.jikan.moe → 3 istek/sn ve 60 istek/dk. Burada
 *     aralık 350 ms (≈2.85/sn) ve kayan 60 sn penceresinde en fazla [UI_PER_MINUTE]=55
 *     istek uygulanır. Arka plan işleri ([Priority.BACKGROUND]) pencerenin son 15 hakkına
 *     dokunamaz; kullanıcı ekranları (UI) her zaman yeterli kotaya sahip olur.
 *  2. **429 soğuması:** 429 gelirse TÜM Jikan istekleri `Retry-After` kadar (yoksa 2 sn,
 *     en fazla 10 sn) beklemeye alınır. Eski yapıda her çağrı kendi yeniden denemesini
 *     yapıp kotayı tekrar tüketiyordu.
 *  3. **Önbellek:** Jikan sunucuda 24 saat önbellekler. İstemci tarafında ID'li detay
 *     uçları (anime/manga/karakter/kişi `/full`, `/characters`, `/staff`, `/relations`,
 *     …) 6 saat, liste/arama uçları 30 dk tutulur. Aynı sayfayı birden çok sekme açarsa
 *     tekrar ağa çıkılmaz.
 *  4. **Tekilleştirme (singleflight):** Aynı URL için eşzamanlı istekler tek ağ çağrısını
 *     paylaşır. Bu, MAL detay açıldığında 5-6 sekmenin aynı `/full` uç noktasına aynı anda
 *     vurup kotayı yakmasını engeller.
 *
 * Not: Jikan kimlik doğrulamalı yazma yapmaz; yalnızca GET kullanılır.
 */
object JikanGateway {
    private const val TAG = "JikanGateway"
    private const val HOST = "api.jikan.moe"
    private const val USER_AGENT = "KitsugiAnimeList/1.0"

    /** Jikan 3 istek/sn sınırının altında kalmak için istekler arası minimum aralık. */
    const val MIN_INTERVAL_MS = 350L
    private const val WINDOW_MS = 60_000L
    private const val UI_PER_MINUTE = 55
    private const val BACKGROUND_PER_MINUTE = 40
    private const val DEFAULT_COOLDOWN_MS = 2_000L
    private const val MIN_COOLDOWN_MS = 1_000L
    private const val MAX_COOLDOWN_MS = 10_000L
    private const val DETAIL_TTL_MS = 6L * 60L * 60L * 1000L
    private const val LIST_TTL_MS = 30L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 300

    private val ID_DETAIL_REGEX = Regex("^(anime|manga|characters|people|producers)/\\d+(/.*)?$")
    private val VOLATILE_SUFFIXES = listOf("/reviews", "/forum", "/news", "/statistics")

    enum class Priority {
        /** Kullanıcının açık ekranı: tüm kota kullanılabilir. */
        UI,

        /** Arka plan (eşitleme, kimlik doğrulama, profil): pencerenin son 15 hakkına dokunmaz. */
        BACKGROUND
    }

    private class CacheEntry(val body: String, val expiresAt: Long)

    private val lock = Any()
    private var nextSlotAt = 0L
    private var cooldownUntil = 0L
    private val recentStarts = ArrayDeque<Long>()
    private val inFlight = HashMap<String, CompletableDeferred<JikanResult>>()
    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    fun isJikanUrl(url: URL): Boolean = url.host.equals(HOST, ignoreCase = true)

    /** Uç noktanın önbellek süresi: ID'li ve kararlı detay uçları uzun, liste/arama kısa tutulur. */
    internal fun ttlFor(url: String): Long {
        val path = url.substringAfter("/v4/", "").substringBefore("?")
        if (VOLATILE_SUFFIXES.any { path.endsWith(it) }) {
            return LIST_TTL_MS
        }
        return if (ID_DETAIL_REGEX.matches(path)) DETAIL_TTL_MS else LIST_TTL_MS
    }

    /**
     * Kilit altında bir gönderim slotu rezerve eder ve beklenecek süreyi (ms) döndürür.
     * Slot zamanları monoton arttığı için `recentStarts` her zaman sıralıdır.
     */
    private fun reserveLocked(priority: Priority): Long {
        val now = System.currentTimeMillis()
        while (recentStarts.isNotEmpty() && now - recentStarts.first() >= WINDOW_MS) {
            recentStarts.removeFirst()
        }
        var slot = maxOf(now, nextSlotAt, cooldownUntil)
        val ceiling = if (priority == Priority.BACKGROUND) BACKGROUND_PER_MINUTE else UI_PER_MINUTE
        if (recentStarts.size >= ceiling) {
            // Pencerede yer açılması için en az (size - ceiling + 1)'inci eski isteğin 60 sn'si dolmalı.
            val blocker = recentStarts.elementAt(recentStarts.size - ceiling)
            slot = maxOf(slot, blocker + WINDOW_MS)
        }
        nextSlotAt = slot + MIN_INTERVAL_MS
        recentStarts.addLast(slot)
        return slot - now
    }

    /** Askıya alınabilen (coroutine) kota kapısı. */
    suspend fun admit(priority: Priority = Priority.UI) {
        val wait = synchronized(lock) { reserveLocked(priority) }
        if (wait > 0) delay(wait)
    }

    /** Thread bloklayan kota kapısı; yalnızca IO thread'lerinden çağrılmalıdır. */
    fun admitBlocking(priority: Priority = Priority.UI) {
        val wait = synchronized(lock) { reserveLocked(priority) }
        if (wait > 0) {
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Jikan 429 döndüğünde çağrılır. Soğuma süresi TÜM Jikan istekleri için geçerlidir,
     * çünkü limit IP/istemci bazlıdır ve tek bir uç noktaya özgü değildir.
     */
    fun reportRateLimited(retryAfterMs: Long? = null) {
        val cooldown = (retryAfterMs ?: DEFAULT_COOLDOWN_MS).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
        synchronized(lock) {
            cooldownUntil = maxOf(cooldownUntil, System.currentTimeMillis() + cooldown)
        }
        Log.w(TAG, "Jikan 429: ${cooldown}ms soğuma uygulandı")
    }

    private fun cacheGet(key: String): String? = synchronized(lock) {
        val entry = cache[key]
        when {
            entry == null -> null
            entry.expiresAt < System.currentTimeMillis() -> {
                cache.remove(key)
                null
            }
            else -> entry.body
        }
    }

    private fun cachePut(key: String, body: String, ttlMs: Long) {
        synchronized(lock) {
            cache[key] = CacheEntry(body, System.currentTimeMillis() + ttlMs)
        }
    }

    /** Tek bir HTTP denemesi (bloklayan). Kota kapısı çağıran tarafta alınmış olmalıdır. */
    private fun executeOnce(url: String): JikanResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> JikanResult.Ok(response.body?.string().orEmpty())
                    response.code == 404 -> JikanResult.NotFound
                    response.code == 429 -> JikanResult.RateLimited(
                        response.header("Retry-After")?.toLongOrNull()?.times(1_000L) ?: DEFAULT_COOLDOWN_MS
                    )
                    else -> JikanResult.Failed(response.code, response.message)
                }
            }
        } catch (e: Exception) {
            JikanResult.Failed(0, e.message ?: "network")
        }
    }

    private suspend fun fetchWithRetry(url: String, priority: Priority, maxRetries: Int): JikanResult {
        val ttl = ttlFor(url)
        var attempt = 0
        while (true) {
            admit(priority)
            val result = withContext(Dispatchers.IO) { executeOnce(url) }
            when (result) {
                is JikanResult.Ok -> {
                    cachePut(url, result.body, ttl)
                    return result
                }
                is JikanResult.NotFound -> return result
                is JikanResult.RateLimited -> {
                    reportRateLimited(result.retryAfterMs)
                    if (attempt >= maxRetries) return result
                }
                is JikanResult.Failed -> {
                    val retryable = result.code == 0 || result.code in 500..599
                    if (!retryable || attempt >= maxRetries) return result
                    Log.w(TAG, "HTTP ${result.code} → yeniden deneme ${attempt + 1}/$maxRetries: $url")
                    delay(1_000L * (1 shl attempt))
                }
            }
            attempt++
        }
    }

    /**
     * Jikan GET isteği: önbellek → tekilleştirme → kota kapısı → yeniden deneme.
     * 404 kalıcıdır ve yeniden denenmez. 429/5xx/ağ hatası sınırlı sayıda denenir.
     */
    suspend fun fetch(url: String, priority: Priority = Priority.UI, maxRetries: Int = 2): JikanResult {
        cacheGet(url)?.let { return JikanResult.Ok(it) }

        val owned = CompletableDeferred<JikanResult>()
        val existing = synchronized(lock) {
            inFlight[url] ?: run {
                inFlight[url] = owned
                null
            }
        }
        if (existing != null) {
            val shared = existing.await()
            // Sahip iptal edildiyse (paylaşılan sonuç "cancelled") kendimiz yeniden deneriz.
            return if (shared is JikanResult.Failed && shared.code == 0 && shared.message == CANCELLED) {
                fetch(url, priority, maxRetries)
            } else {
                shared
            }
        }

        var result: JikanResult = JikanResult.Failed(0, CANCELLED)
        try {
            result = fetchWithRetry(url, priority, maxRetries)
        } finally {
            synchronized(lock) { inFlight.remove(url) }
            owned.complete(result)
        }
        return result
    }

    /** Bloklayan sürüm (eski `KitsugiApiBase` çağıranları için). IO thread'lerinde kullanın. */
    fun fetchBlocking(url: String, priority: Priority = Priority.UI, maxRetries: Int = 2): JikanResult =
        runBlocking { fetch(url, priority, maxRetries) }

    private const val CANCELLED = "cancelled"
}

package com.kitsugi.animelist.data.remote.malweb

import android.util.Log
import com.kitsugi.animelist.data.remote.JikanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Jikan-uyumlu `users/...` uçlarını CİHAZ ÜZERİNDE, doğrudan myanimelist.net HTML'inden üretir.
 *
 * JikanGateway zincirinde ilk sıradadır: cihaz → miribyou (Vercel) → Tenrai.
 * Her kullanıcı kendi IP'sinden, normal bir ziyaretçi temposunda istek attığı için kullanıcı sayısı
 * arttıkça merkezi sunucuya (Vercel) yük binmez ve toplu ban riski oluşmaz.
 *
 * Koruma kuralları (MAL'e nazik davranmak için):
 *  - Tüm MAL sayfa istekleri tek sıradan geçer, iki istek arası en az [MIN_INTERVAL_MS].
 *  - Aynı sayfa [HTML_TTL_MS] boyunca tekrar indirilmez (profil, istatistik, favori ve "full"
 *    uçları aynı tek profil sayfasından üretilir → 1 istek).
 *  - 429/403/405 veya beklenmeyen sayfa (captcha vb.) gelirse cihaz kaynağı [BLOCK_COOLDOWN_MS]
 *    boyunca devre dışı kalır; zincir otomatik olarak miribyou/Tenrai'ye düşer.
 */
object MalWebSource {
    const val HOST_ID = "device"
    private const val TAG = "MalWebSource"
    private const val MAL_BASE = "https://myanimelist.net"

    /** Acil durum anahtarı: false yapılırsa zincir doğrudan miribyou/Tenrai ile başlar. */
    @Volatile
    var enabled: Boolean = true

    private const val MIN_INTERVAL_MS = 1_500L
    private const val HTML_TTL_MS = 10L * 60L * 1000L
    private const val BLOCK_COOLDOWN_MS = 10L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 40

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    private val RESERVED = setOf("recentlyonline", "userbyid")
    private val USER_PATH = Regex(
        """^users/([A-Za-z0-9_\-]{2,32})(?:/(full|statistics|favorites|about|userupdates|friends|history))?/?$"""
    )

    private class Target(val username: String, val endpoint: String, val query: Map<String, String>)

    private class CachedHtml(val html: String, val expiresAt: Long)

    private val fetchLock = Mutex()
    private val cacheLock = Any()
    private var lastRequestAt = 0L
    @Volatile
    private var blockedUntil = 0L
    private val htmlCache = object : LinkedHashMap<String, CachedHtml>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedHtml>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private fun parseTarget(canonical: String): Target? {
        val rest = canonical.substringAfter("/v4/", "")
        if (rest.isEmpty()) return null
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', "").split('&').mapNotNull { p ->
            val k = p.substringBefore('=', "")
            if (k.isEmpty()) null else k to p.substringAfter('=', "")
        }.toMap()
        val m = USER_PATH.find(path) ?: return null
        val username = m.groupValues[1]
        if (username.lowercase() in RESERVED) return null
        val endpoint = m.groupValues[2].ifEmpty { "base" }
        return Target(username, endpoint, query)
    }

    /** Bu kanonik Jikan adresi cihaz üzerinde üretilebilir mi? */
    fun supports(canonical: String): Boolean = enabled && parseTarget(canonical) != null

    fun isCoolingDown(): Boolean = System.currentTimeMillis() < blockedUntil

    /**
     * Kanonik Jikan adresine karşılık gelen JSON gövdesini üretir.
     * Başarısızlıkta [JikanResult.Failed]/[JikanResult.RateLimited] döner; çağıran bir sonraki
     * kaynağa geçer. Kullanıcı gerçekten yoksa [JikanResult.NotFound].
     */
    suspend fun fetch(canonical: String): JikanResult {
        val target = parseTarget(canonical) ?: return JikanResult.Failed(0, "unsupported")
        if (!enabled) return JikanResult.Failed(0, "disabled")
        if (isCoolingDown()) return JikanResult.Failed(0, "device cooldown")

        val u = target.username
        return when (target.endpoint) {
            "base", "full", "statistics", "favorites", "about", "userupdates" -> {
                val html = when (val r = page("/profile/$u")) {
                    is PageResult.Html -> r.html
                    is PageResult.Error -> return r.result
                }
                val profile = MalWebParser.parseProfile(html)
                    ?: return unexpectedPage("/profile/$u")
                val body = when (target.endpoint) {
                    "base" -> MalWebJson.wrap(MalWebJson.user(profile, full = false))
                    "full" -> MalWebJson.wrap(MalWebJson.user(profile, full = true))
                    "statistics" -> MalWebJson.wrap(MalWebJson.statistics(profile))
                    "favorites" -> MalWebJson.wrap(MalWebJson.favorites(profile))
                    "userupdates" -> MalWebJson.wrap(MalWebJson.updates(profile))
                    else -> MalWebJson.wrap(org.json.JSONObject().put("about", profile.aboutHtml ?: org.json.JSONObject.NULL))
                }
                JikanResult.Ok(body)
            }
            "friends" -> {
                val pageNo = target.query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val path = if (pageNo > 1) "/profile/$u/friends?p=$pageNo" else "/profile/$u/friends"
                val html = when (val r = page(path)) {
                    is PageResult.Html -> r.html
                    is PageResult.Error -> return r.result
                }
                val parsed = MalWebParser.parseFriends(html) ?: return unexpectedPage(path)
                JikanResult.Ok(MalWebJson.friends(parsed, pageNo).toString())
            }
            "history" -> {
                val type = target.query["type"]?.lowercase()?.takeIf { it == "anime" || it == "manga" }
                val path = if (type != null) "/history/$u/$type" else "/history/$u"
                val html = when (val r = page(path)) {
                    is PageResult.Html -> r.html
                    is PageResult.Error -> return r.result
                }
                val items = MalWebParser.parseHistory(html) ?: return unexpectedPage(path)
                JikanResult.Ok(MalWebJson.history(items).toString())
            }
            else -> JikanResult.Failed(0, "unsupported")
        }
    }

    private fun unexpectedPage(path: String): JikanResult {
        // Beklenmeyen içerik (captcha / bakım / tasarım değişikliği): bir süre cihaz kaynağını kullanma.
        blockedUntil = System.currentTimeMillis() + BLOCK_COOLDOWN_MS
        synchronized(cacheLock) { htmlCache.remove(path) }
        Log.w(TAG, "Beklenmeyen MAL sayfası ($path) → cihaz kaynağı ${BLOCK_COOLDOWN_MS / 60000} dk devre dışı")
        return JikanResult.Failed(200, "unexpected page")
    }

    private sealed class PageResult {
        class Html(val html: String) : PageResult()
        class Error(val result: JikanResult) : PageResult()
    }

    private fun cached(path: String): String? = synchronized(cacheLock) {
        val e = htmlCache[path] ?: return@synchronized null
        if (e.expiresAt < System.currentTimeMillis()) {
            htmlCache.remove(path)
            null
        } else {
            e.html
        }
    }

    private suspend fun page(path: String): PageResult {
        cached(path)?.let { return PageResult.Html(it) }
        return fetchLock.withLock {
            // Kilit beklenirken başka bir istek aynı sayfayı indirmiş olabilir.
            cached(path)?.let { return@withLock PageResult.Html(it) }
            if (isCoolingDown()) return@withLock PageResult.Error(JikanResult.Failed(0, "device cooldown"))

            val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) { download(path) }
            if (result is PageResult.Html) {
                synchronized(cacheLock) {
                    htmlCache[path] = CachedHtml(result.html, System.currentTimeMillis() + HTML_TTL_MS)
                }
            }
            result
        }
    }

    private fun download(path: String): PageResult {
        val request = Request.Builder()
            .url(MAL_BASE + path)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.jikanClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> PageResult.Html(response.body?.string().orEmpty())
                    response.code == 404 -> PageResult.Error(JikanResult.NotFound)
                    response.code == 429 || response.code == 403 || response.code == 405 -> {
                        blockedUntil = System.currentTimeMillis() + BLOCK_COOLDOWN_MS
                        Log.w(TAG, "MAL HTTP ${response.code} ($path) → cihaz kaynağı geçici olarak devre dışı")
                        PageResult.Error(JikanResult.Failed(response.code, "device blocked"))
                    }
                    else -> PageResult.Error(JikanResult.Failed(response.code, response.message))
                }
            }
        } catch (e: Exception) {
            PageResult.Error(JikanResult.Failed(0, e.message ?: "network"))
        }
    }
}

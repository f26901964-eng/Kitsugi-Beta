package com.kitsugi.animelist.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import okhttp3.Request

object CloudstreamUrlHelper {

    private const val TAG = "CloudstreamUrlHelper"

    /** Kısa kod regex: sadece harf, rakam, tire, alt çizgi, ünlem — nokta/slash yok */
    private val SHORT_CODE_REGEX = Regex("^[a-zA-Z0-9!_-]+$")

    /**
     * Normalizes repository and plugin URLs.
     * Handles cloudstreamrepo:// and cs.repo/? URI schemes.
     * Both keyiflerolsun and feroxx repos are treated as independent, valid sources.
     */
    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.contains("http-protocol-redirector?r=")) {
            val queryParam = url.substringAfter("http-protocol-redirector?r=")
            url = try {
                URLDecoder.decode(queryParam, "UTF-8")
            } catch (e: Exception) {
                queryParam
            }
        }
        if (url.startsWith("cloudstreamrepo://")) {
            url = "https://" + url.removePrefix("cloudstreamrepo://")
        } else if (url.startsWith("cloudstreamrepo:")) {
            val remaining = url.removePrefix("cloudstreamrepo:")
            url = if (remaining.startsWith("http://") || remaining.startsWith("https://")) {
                remaining
            } else {
                "https://$remaining"
            }
        } else if (url.startsWith("https://cs.repo/?") || url.startsWith("https://cs.repo?")) {
            // cs.repo kısa URL şeması: https://cs.repo/?https://raw.githubusercontent.com/...
            val realUrl = url.substringAfter("?")
            url = if (realUrl.startsWith("http")) realUrl else "https://$realUrl"
        }

        // Remove trailing slashes and common wrappers
        val cleanUrl = url.trim().removeSuffix("/")

        // Check for our KitsugiPlugins repo or legacy repositories that migrated to KitsugiPlugins
        val isOurRepoOrLegacy = cleanUrl.contains("KitsugiPlugins", ignoreCase = true) ||
            cleanUrl.contains("Kitsugi-Plugins", ignoreCase = true) ||
            cleanUrl.contains("f26901964-eng", ignoreCase = true) ||
            cleanUrl.contains("KitsugiBeta-dev", ignoreCase = true) ||
            cleanUrl.contains("gameras1010-afk", ignoreCase = true) ||
            cleanUrl.contains("keyiflerolsun/Kekik-cloudstream", ignoreCase = true) ||
            cleanUrl.contains("maarrem/cs-Kekik", ignoreCase = true)

        if (isOurRepoOrLegacy) {
            val cleanPath = cleanUrl.substringBefore("?").substringBefore("#")
            return when {
                cleanPath.endsWith(".cs3", ignoreCase = true) -> {
                    val fileName = cleanPath.substringAfterLast("/")
                    "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/$fileName"
                }
                cleanPath.endsWith("plugins.json", ignoreCase = true) -> {
                    "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/plugins.json"
                }
                else -> {
                    // Any other URL: repo root, .git, repo.json, main branch, raw builds, etc.
                    // All resolve to the canonical repo manifest!
                    "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/repo.json"
                }
            }
        }

        // Handle general GitHub raw/web URLs
        if (url.startsWith("https://github.com/", ignoreCase = true)) {
            val withoutPrefix = url.removePrefix("https://github.com/")
            if (withoutPrefix.contains("/blob/") || withoutPrefix.contains("/raw/")) {
                val converted = withoutPrefix.replaceFirst("/blob/", "/").replaceFirst("/raw/", "/")
                return "https://raw.githubusercontent.com/$converted"
            }
            if (withoutPrefix.split("/").size == 2 && !withoutPrefix.endsWith(".json")) {
                return "https://raw.githubusercontent.com/$withoutPrefix/master/repo.json"
            }
        }

        return url
    }

    /**
     * Appends a timestamp query parameter to bypass CDN/Fastly caches for instant updates.
     */
    fun withCacheBuster(url: String): String {
        val trimmed = url.trim()
        val sep = if (trimmed.contains("?")) "&" else "?"
        return "$trimmed${sep}_ts=${System.currentTimeMillis()}"
    }

    /**
     * GitHub Vekil Sunucu — CS3'ün "GitHub proxy" özelliğinin muadili.
     *
     * ISS'ler raw.githubusercontent.com'u engellediyse, URL'yi jsDelivr CDN üzerinden
     * yönlendirir. jsDelivr birkaç günlük önbellek gecikmesine sahip olabilir.
     *
     * Dönüşüm örneği:
     *   https://raw.githubusercontent.com/maarrem/cs-Kekik/master/repo.json
     *   → https://cdn.jsdelivr.net/gh/maarrem/cs-Kekik@master/repo.json
     *
     * @param url Herhangi bir URL; raw.githubusercontent.com değilse değiştirilmez.
     */
    fun applyGithubProxy(url: String): String {
        val prefix = "https://raw.githubusercontent.com/"
        if (!url.startsWith(prefix)) return url
        // Format: /user/repo/branch/path → cdn.jsdelivr.net/gh/user/repo@branch/path
        val rest = url.removePrefix(prefix)       // "user/repo/branch/path"
        val parts = rest.split("/", limit = 3)    // ["user", "repo", "branch/path"]
        if (parts.size < 3) return url
        val (user, repo, branchAndPath) = parts
        val branchSep = branchAndPath.indexOf('/')
        return if (branchSep == -1) {
            // Sadece branch, path yok — olası değil ama güvenli işle
            "https://cdn.jsdelivr.net/gh/$user/$repo@$branchAndPath"
        } else {
            val branch = branchAndPath.substring(0, branchSep)
            val path   = branchAndPath.substring(branchSep + 1)
            "https://cdn.jsdelivr.net/gh/$user/$repo@$branch/$path"
        }
    }

    /**
     * normalizeUrl + isteğe bağlı GitHub proxy dönüşümü.
     * [useProxy] true ise ve URL raw.githubusercontent.com ise jsDelivr'a yönlendirir.
     */
    fun normalizeAndProxy(rawUrl: String, useProxy: Boolean): String {
        val normalized = normalizeUrl(rawUrl)
        return if (useProxy) applyGithubProxy(normalized) else normalized
    }

    /**
     * Verilen girdinin bir Cloudstream kısa kodu olup olmadığını kontrol eder.
     * Kısa kod: sadece harf/rakam/tire/alt çizgi/ünlem içeren ve nokta veya slash içermeyen string.
     * Örnek: "kekikdevam" → true, "https://..." → false
     */
    fun isShortCode(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.isNotBlank() &&
               !trimmed.contains('.') &&
               !trimmed.contains('/') &&
               !trimmed.startsWith("http") &&
               trimmed.matches(SHORT_CODE_REGEX)
    }

    /**
     * cutt.ly URL kısaltma servisi üzerinden kısa kodu gerçek repo URL'sine çözer.
     * Cloudstream topluluğu repo URL'lerini cutt.ly kısa kodlarıyla paylaşır.
     *
     * Örnek: "kekikdevam" → "https://raw.githubusercontent.com/Kekik.../repo.json"
     *
     * @return Gerçek URL veya null (geçersiz / bulunamayan kısa kod)
     */
    suspend fun resolveShortCode(shortCode: String): String? {
        return try {
            Log.d(TAG, "Kısa kod çözülüyor: cutt.ly/$shortCode")
            val request = Request.Builder()
                .url("https://cutt.ly/$shortCode")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val noRedirectClient = com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (location == null) {
                    Log.w(TAG, "cutt.ly/$shortCode → Location header yok")
                    return null
                }
                if (location.startsWith("https://cutt.ly/404") || location.removeSuffix("/") == "https://cutt.ly") {
                    Log.w(TAG, "cutt.ly/$shortCode → Geçersiz kısa kod (404)")
                    return null
                }

                Log.d(TAG, "Kısa kod çözüldü: $shortCode → $location")
                location
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kısa kod çözme hatası: $shortCode", e)
            null
        }
    }
}

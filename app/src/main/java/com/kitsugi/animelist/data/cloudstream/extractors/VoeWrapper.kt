package com.kitsugi.animelist.data.cloudstream.extractors

import android.util.Base64
import android.util.Log

/**
 * FP-34 – Voe (voe.sx) extractor wrapper.
 * Stream URL'sini JS değişkenlerinde (hls/mp4) veya Base64 ile saklar.
 */
object VoeWrapper {
    private const val TAG = "VoeWrapper"

    data class ExtractedVideo(
        val url: String,
        val label: String,
        val isM3u8: Boolean,
        val headers: Map<String, String> = emptyMap()
    )

    fun extractFromHtml(pageContent: String, embedUrl: String): List<ExtractedVideo> {
        val videos = mutableListOf<ExtractedVideo>()
        try {
            // Voe's CDN may bind the signed URL to the exact embed page, not
            // merely the host root. Preserve that page as Referer and provide
            // the matching origin for the player and HLS segment requests.
            val pageReferer = embedUrl.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: "https://voe.sx/"
            val pageOrigin = runCatching {
                val uri = java.net.URI(pageReferer)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: "voe.sx"
                "$scheme://$host"
            }.getOrDefault("https://voe.sx")
            val headers = mapOf(
                "Referer" to pageReferer,
                "Origin" to pageOrigin
            )

            // Yöntem 1: 'hls': '...' veya 'mp4': '...'
            Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(pageContent)?.groupValues?.get(1)?.let { url ->
                val decoded = tryBase64Decode(url) ?: url
                if (decoded.startsWith("http") && decoded.contains(".m3u8")) {
                    videos.add(ExtractedVideo(decoded, "HLS", true, headers))
                }
            }
            Regex("""['"]mp4['"]\s*:\s*['"]([^'"]+)['"]""").find(pageContent)?.groupValues?.get(1)?.let { url ->
                val decoded = tryBase64Decode(url) ?: url
                if (decoded.startsWith("http") && decoded.contains(".mp4")) {
                    videos.add(ExtractedVideo(decoded, "MP4", false, headers))
                }
            }

            // Yöntem 2: atob('...') decode
            if (videos.isEmpty()) {
                Regex("""atob\(['"]([A-Za-z0-9+/=]+)['"]\)""").findAll(pageContent).forEach { m ->
                    val decoded = tryBase64Decode(m.groupValues[1])
                    if (!decoded.isNullOrBlank() && decoded.startsWith("http")) {
                        val isM3u8 = decoded.contains(".m3u8")
                        videos.add(ExtractedVideo(decoded, if (isM3u8) "HLS" else "MP4", isM3u8, headers))
                    }
                }
            }

            Log.d(TAG, "Voe extraction: ${videos.size} video")
        } catch (e: Exception) {
            Log.e(TAG, "Voe extraction hatası: ${e.message}")
        }
        return videos
    }

    private fun tryBase64Decode(s: String): String? = try {
        val d = String(Base64.decode(s, Base64.DEFAULT)).trim()
        if (d.startsWith("http")) d else null
    } catch (_: Exception) { null }

    fun isVoeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("voe.sx") || lower.contains("voeload.com")
    }
}

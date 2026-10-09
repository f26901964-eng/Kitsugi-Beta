package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Akışın GERÇEK bilgilerini kaynağın kendisinden ölçer.
 *
 * - HLS (`.m3u8`): master playlist indirilir; `#EXT-X-STREAM-INF` satırlarından gerçek
 *   `RESOLUTION` değerleri, `#EXT-X-MEDIA` satırlarından gerçek ses/altyazı dilleri okunur.
 * - Progressive (`.mp4`, `.mkv`, `.webm`): HTTP HEAD ile `Content-Length` (gerçek dosya boyutu).
 *
 * Böylece arayüzdeki "1080p", "Altyazılı", "Dublaj" rozetleri tahmin değil ölçüm olur.
 * Ölçülemeyen bilgi için rozet basılmaz.
 *
 * Sonuçlar URL bazlı önbelleğe alınır; eşzamanlı ölçüm sayısı sınırlıdır.
 */
object StreamProbe {

    private const val TAG = "StreamProbe"
    private const val MAX_PLAYLIST_BYTES = 128 * 1024L
    private const val PROBE_TIMEOUT_MS = 8_000L

    private val cache = BoundedCache<String, MeasuredStreamInfo>("streamProbe", 200)
    private val gate = Semaphore(3)

    /** Daha önce ölçülmüş sonuç (anında arayüze verilebilir). */
    fun cached(url: String?): MeasuredStreamInfo? = url?.let { cache[it] }

    fun clearCache() = cache.clear()

    /** Bu akış için ölçüm yapmak anlamlı mı? (torrent / embed sayfaları ölçülmez) */
    fun isProbeable(stream: StreamSource): Boolean {
        if (stream.isTorrent) return false
        val url = stream.url?.trim().orEmpty()
        if (!url.startsWith("http", ignoreCase = true)) return false
        return mediaKind(url) != MediaKind.UNSUPPORTED
    }

    private enum class MediaKind { HLS, PROGRESSIVE, UNSUPPORTED }

    private fun mediaKind(url: String): MediaKind {
        val lower = url.lowercase(Locale.ROOT).substringBefore('#')
        return when {
            lower.contains(".m3u8") -> MediaKind.HLS
            lower.contains(".mp4") || lower.contains(".mkv") ||
                lower.contains(".webm") || lower.contains(".m4v") -> MediaKind.PROGRESSIVE
            else -> MediaKind.UNSUPPORTED
        }
    }

    /**
     * Akışı ölçer (önbellekte varsa onu döndürür). Ölçüm mümkün değilse `null` döner —
     * bu durumda arayüz yalnızca kaynağın bildirdiği bilgileri gösterir.
     */
    suspend fun probe(stream: StreamSource): MeasuredStreamInfo? {
        val url = stream.url?.trim().orEmpty()
        cache[url]?.let { return it }
        if (!isProbeable(stream)) return null

        val result = gate.withPermit {
            cache[url] ?: withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        when (mediaKind(url)) {
                            MediaKind.HLS -> probeHls(url, stream.requestHeaders)
                            MediaKind.PROGRESSIVE -> probeProgressive(url, stream.requestHeaders)
                            MediaKind.UNSUPPORTED -> null
                        }
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        Log.w(TAG, "Ölçüm hatası (${error.javaClass.simpleName}): ${url.take(80)}")
                        MeasuredStreamInfo(failed = true)
                    }
                } ?: MeasuredStreamInfo(failed = true)
            }
        }

        cache[url] = result
        return result
    }

    // ── HLS ──────────────────────────────────────────────────────────────────

    private val RESOLUTION_ATTR = Regex("""RESOLUTION=(\d{2,5})x(\d{2,5})""", RegexOption.IGNORE_CASE)
    private val LANGUAGE_ATTR = Regex("LANGUAGE=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val TYPE_ATTR = Regex("""TYPE=([A-Z\-]+)""", RegexOption.IGNORE_CASE)

    private fun probeHls(url: String, headers: Map<String, String>?): MeasuredStreamInfo {
        val body = fetchText(url, headers) ?: return MeasuredStreamInfo(failed = true)
        if (!body.contains("#EXTM3U")) return MeasuredStreamInfo(failed = true)

        val variantHeights = mutableListOf<Int>()
        val audioLanguages = mutableListOf<String>()
        val subtitleLanguages = mutableListOf<String>()

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    RESOLUTION_ATTR.find(line)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let {
                        variantHeights.add(it)
                    }
                }
                line.startsWith("#EXT-X-MEDIA", ignoreCase = true) -> {
                    val type = TYPE_ATTR.find(line)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
                    val lang = LANGUAGE_ATTR.find(line)?.groupValues?.getOrNull(1)
                    if (!lang.isNullOrBlank()) {
                        when (type) {
                            "AUDIO" -> audioLanguages.add(lang)
                            "SUBTITLES", "CLOSED-CAPTIONS" -> subtitleLanguages.add(lang)
                        }
                    }
                }
            }
        }

        val isMaster = body.contains("#EXT-X-STREAM-INF", ignoreCase = true)
        val distinctHeights = variantHeights.distinct().sorted()

        return MeasuredStreamInfo(
            height = distinctHeights.maxOrNull().takeIf { distinctHeights.size == 1 },
            isAdaptive = isMaster,
            variantHeights = distinctHeights,
            audioLanguages = audioLanguages.distinct(),
            subtitleLanguages = subtitleLanguages.distinct(),
            sizeBytes = null,
            failed = false
        )
    }

    // ── Progressive dosyalar ─────────────────────────────────────────────────

    private fun probeProgressive(url: String, headers: Map<String, String>?): MeasuredStreamInfo {
        val request = buildRequest(url, headers).head().build()
        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return MeasuredStreamInfo(failed = true)
            val length = response.header("Content-Length")?.toLongOrNull()
            return MeasuredStreamInfo(
                sizeBytes = length?.takeIf { it > 0 },
                failed = length == null
            )
        }
    }

    // ── Ortak yardımcılar ────────────────────────────────────────────────────

    private fun buildRequest(url: String, headers: Map<String, String>?): Request.Builder {
        val builder = Request.Builder().url(url)
        headers?.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                runCatching { builder.header(key, value) }
            }
        }
        return builder
    }

    private fun fetchText(url: String, headers: Map<String, String>?): String? {
        val request = buildRequest(url, headers)
            .header("Range", "bytes=0-${MAX_PLAYLIST_BYTES - 1}")
            .get()
            .build()
        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.peekBody(MAX_PLAYLIST_BYTES).string()
        }
    }
}

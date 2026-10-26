package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume

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

    /**
     * Eşzamanlı ölçüm sayısı.
     *
     * 3 → 2: Listede aynı anda onlarca kart var; ölçümler OkHttp bağlantı havuzunu ve
     * mobil radyoyu doldurduğunda resim yükleme/kaydırma da yavaşlıyordu.
     */
    private val gate = Semaphore(2)

    private val cache = BoundedCache<String, MeasuredStreamInfo>("streamProbe", 200)

    /** Daha önce aynı URL, transport ipucu ve request header'larıyla ölçülmüş sonuç. */
    fun cached(stream: StreamSource): MeasuredStreamInfo? = cache[cacheKeyFor(stream)]

    fun clearCache() = cache.clear()

    internal fun cacheKeyFor(stream: StreamSource): String {
        val material = buildString {
            append(stream.url?.trim().orEmpty())
            append('\n').append(stream.streamTypeHint?.uppercase(Locale.ROOT).orEmpty())
            stream.requestHeaders.orEmpty().entries
                .sortedBy { it.key.lowercase(Locale.ROOT) }
                .forEach { (name, value) ->
                    append('\n').append(name.lowercase(Locale.ROOT)).append(':').append(value)
                }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    /** Bu akış için ölçüm yapmak anlamlı mı? (torrent / embed sayfaları ölçülmez) */
    fun isProbeable(stream: StreamSource): Boolean {
        if (stream.isTorrent) return false
        val url = stream.url?.trim().orEmpty()
        if (!url.startsWith("http", ignoreCase = true)) return false
        return mediaKind(stream) != MediaKind.UNSUPPORTED
    }

    /**
     * Ölçüm bu akışa YENİ bilgi katıyor mu?
     *
     * NEDEN GEREKLİ: Kitsugi rozetleri kanıta dayalı gösterir. Kaynak (CloudStream eklentisi /
     * Stremio) kalite, ses türü ve altyazı listesini ZATEN bildirdiyse ağ üzerinden ölçüm yapmak
     * hiçbir yeni rozet üretmez — yalnızca veri harcar ve kaydırmayı kasar. Bilinen eksik bilgi
     * varsa (ör. yalnızca kalite verilmiş, altyazı verilmemiş) ölçüm yine yapılır.
     */
    fun isProbeNeeded(stream: StreamSource): Boolean {
        if (!isProbeable(stream)) return false
        val qualityKnown = StreamInfoResolver.csQualityHeightOrNull(stream.qualityValue) != null
        val audioKnown = !stream.providerAudioKind.isNullOrBlank() ||
            stream.providerLanguages.isNotEmpty()
        val subtitlesKnown = stream.subtitles.isNotEmpty()
        return !(qualityKnown && audioKnown && subtitlesKnown)
    }

    private enum class MediaKind { HLS, PROGRESSIVE, UNSUPPORTED }

    private fun mediaKind(stream: StreamSource): MediaKind {
        when (stream.streamTypeHint?.uppercase(Locale.ROOT)) {
            "M3U8", "HLS" -> return MediaKind.HLS
            "DASH", "TORRENT", "MAGNET" -> return MediaKind.UNSUPPORTED
        }
        val lower = stream.url.orEmpty().lowercase(Locale.ROOT).substringBefore('#')
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
        if (!isProbeable(stream)) return null
        val key = cacheKeyFor(stream)
        cache[key]?.let { return it }

        val result = gate.withPermit {
            cache[key] ?: withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        when (mediaKind(stream)) {
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

        cache[key] = result
        return result
    }

    // ── HLS ──────────────────────────────────────────────────────────────────

    private val RESOLUTION_ATTR = Regex("""RESOLUTION=(\d{2,5})x(\d{2,5})""", RegexOption.IGNORE_CASE)
    private val LANGUAGE_ATTR = Regex("LANGUAGE=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val TYPE_ATTR = Regex("""TYPE=([A-Z\-]+)""", RegexOption.IGNORE_CASE)

    private suspend fun probeHls(url: String, headers: Map<String, String>?): MeasuredStreamInfo {
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

    private suspend fun probeProgressive(url: String, headers: Map<String, String>?): MeasuredStreamInfo {
        val request = buildRequest(url, headers).head().build()
        // İptal edilebilir async çağrı: kart görüş alanından çıkınca istek GERÇEKTEN iptal edilir
        // ve eşzamanlılık kapısı (semaphore) hemen serbest kalır.
        val response = awaitResponse(KitsugiHttpClient.client.newCall(request))
            ?: return MeasuredStreamInfo(failed = true)
        return response.use { resp ->
            if (!resp.isSuccessful) {
                MeasuredStreamInfo(failed = true)
            } else {
                val length = resp.header("Content-Length")?.toLongOrNull()
                MeasuredStreamInfo(
                    sizeBytes = length?.takeIf { it > 0 },
                    failed = length == null
                )
            }
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

    private suspend fun fetchText(url: String, headers: Map<String, String>?): String? {
        val request = buildRequest(url, headers)
            .header("Range", "bytes=0-${MAX_PLAYLIST_BYTES - 1}")
            .get()
            .build()
        val response = awaitResponse(KitsugiHttpClient.client.newCall(request)) ?: return null
        return response.use { resp ->
            if (!resp.isSuccessful) null else resp.peekBody(MAX_PLAYLIST_BYTES).string()
        }
    }

    /**
     * OkHttp çağrısını askıya alınabilir (cancellable) biçimde çalıştırır.
     *
     * NEDEN: Eskiden `execute()` (bloklayıcı) kullanılıyordu. Coroutine iptal edilse bile
     * bloklayıcı çağrı arka planda devam ediyor, `gate` semaforundaki izni 8 sn boyunca
     * TUTUYORDU. Listeyi hızlı kaydırınca onlarca terk edilmiş ölçüm izinleri tüketiyor ve
     * yeni görünen kartların rozetleri hiç gelmiyordu (kilitlenme hissi).
     */
    private suspend fun awaitResponse(call: Call): Response? = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else runCatching { response.close() }
            }
        })
    }
}

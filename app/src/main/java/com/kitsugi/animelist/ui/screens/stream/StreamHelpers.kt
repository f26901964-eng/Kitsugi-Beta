package com.kitsugi.animelist.ui.screens.stream

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.repository.MeasuredStreamInfo
import com.kitsugi.animelist.data.repository.StreamAudioKind
import com.kitsugi.animelist.data.repository.StreamInfoResolver
import com.kitsugi.animelist.data.repository.StreamLangInfo
import com.kitsugi.animelist.data.repository.StreamQualityInfo
import com.kitsugi.animelist.data.repository.StreamSource
import com.google.gson.Gson
import java.util.Locale

/** Applies a semi-transparent graphicsLayer alpha value. */
fun Modifier.graphicsLayerAlpha(alphaValue: Float): Modifier =
    this.then(Modifier.graphicsLayer { alpha = alphaValue })

/**
 * Determines whether an addon supports streaming for the given content type and video ID,
 * based on its declared stream types and ID prefix list.
 */
fun ManagedAddonEntity.supportsStreamResource(type: String, videoId: String): Boolean {
    if (streamTypes == null && subtitleTypes != null) return false
    val types = streamTypes?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
    if (!types.isNullOrEmpty()) {
        val matches = types.any {
            it.equals(type, ignoreCase = true) ||
            (type.equals("series", ignoreCase = true) && it.equals("anime", ignoreCase = true)) ||
            (type.equals("movie", ignoreCase = true) && (it.equals("anime", ignoreCase = true) || it.equals("animemovie", ignoreCase = true)))
        }
        if (!matches) return false
    }
    val prefixesJson = idPrefixes
    if (!prefixesJson.isNullOrBlank()) {
        try {
            val prefixList = Gson().fromJson(prefixesJson, Array<String>::class.java).filter { it.isNotBlank() }
            if (prefixList.isNotEmpty() && prefixList.none { videoId.startsWith(it) }) return false
        } catch (_: Exception) { /* fall through */ }
    }
    return true
}

/**
 * Bir [StreamSource] için Debrid önbellek durumunu, yalnızca isim/başlıkta AÇIKÇA yazan
 * işaretçilere göre belirler. Kanıt yoksa torrentler için P2P kabul edilir —
 * eskiden olduğu gibi "varsayılan olarak Önbellekte" denmez (bu yanlış bilgiydi).
 */
fun getCacheState(stream: StreamSource): DebridCacheState {
    val nameLower = stream.name.lowercase(Locale.ROOT)
    val titleLower = stream.title.lowercase(Locale.ROOT)
    val text = "$nameLower $titleLower"
    return when {
        text.contains("[rd+]") || text.contains("[tb+]") || text.contains("[pm+]") ||
        text.contains("[ad+]") || text.contains("cached") || text.contains("önbellek") -> DebridCacheState.CACHED

        text.contains("[rd~]") || text.contains("[tb~]") || text.contains("download") -> DebridCacheState.NOT_CACHED

        else -> DebridCacheState.P2P
    }
}

enum class StreamLangType(val label: String, val isDub: Boolean, val isSub: Boolean) {
    DUB("🎙️ Dublaj", true, false),
    SUB("💬 Altyazılı", false, true),
    DUAL("🌐 Dual", true, true),
    UNKNOWN("Bilinmiyor", false, false)
}

internal fun StreamAudioKind.toLangType(): StreamLangType = when (this) {
    StreamAudioKind.DUB -> StreamLangType.DUB
    StreamAudioKind.SUB -> StreamLangType.SUB
    StreamAudioKind.DUAL -> StreamLangType.DUAL
    StreamAudioKind.UNKNOWN -> StreamLangType.UNKNOWN
}

/**
 * Akışın dil bilgisini **kanıta dayalı** olarak çözer.
 *
 * Kaynak sırası: ölçülen HLS ses parçaları → eklenti meta verisi (CloudStream DubStatus,
 * gerçek altyazı dosyaları) → dosya adındaki açık etiketler. Kanıt yoksa
 * [StreamLangInfo.kind] = UNKNOWN olur ve arayüzde rozet gösterilmez.
 *
 * Kaldırılan yanlış varsayımlar: "Türkçe eklenti ⇒ altyazılı", "isimde 'sub' geçiyor ⇒ altyazılı"
 * (Subaru, Submarine gibi kelimelerde yanlış eşleşiyordu).
 */
fun resolveStreamLangInfo(stream: StreamSource, measured: MeasuredStreamInfo? = null): StreamLangInfo =
    StreamInfoResolver.resolveLang(stream, measured)

/** Geriye dönük uyumluluk: yalnızca dil türünü döndürür. */
fun detectStreamLang(stream: StreamSource): StreamLangType =
    StreamInfoResolver.resolveLang(stream).kind.toLangType()

/**
 * Akışın çözünürlük bilgisini çözer. Kanıt yoksa [StreamQualityInfo.label] null döner;
 * arayüz bu durumda "Kalite ?" gösterir, uydurma bir "1080p (HD)" basmaz.
 */
fun resolveStreamQuality(stream: StreamSource, measured: MeasuredStreamInfo? = null): StreamQualityInfo =
    StreamInfoResolver.resolveQuality(stream, measured)

/** Dosya boyutu etiketi (ölçülen veya kaynağın bildirdiği); bilinmiyorsa null. */
fun resolveStreamSizeLabel(stream: StreamSource, measured: MeasuredStreamInfo? = null): String? =
    StreamInfoResolver.resolveSizeLabel(stream, measured)

/**
 * Geriye dönük uyumluluk: (kalite, boyut) çifti. Kalite bilinmiyorsa boş string döner.
 */
fun parseStreamQuality(stream: StreamSource): Pair<String, String> {
    val quality = StreamInfoResolver.resolveQuality(stream)
    val size = StreamInfoResolver.resolveSizeLabel(stream)
    return (quality.label ?: "") to (size ?: "")
}

/** Yalnızca başlıktan kalite/boyut okur (TV arayüzü). Kanıt yoksa boş string. */
fun parseStreamTitle(title: String): Pair<String, String> {
    val quality = StreamInfoResolver.parseHeightFromText(title)
        ?.let { StreamInfoResolver.heightToLabel(it) }
        ?: ""
    val sizeRegex = Regex("""(\d+(?:\.\d+)?\s*(?:gb|mb|gib|mib))""", RegexOption.IGNORE_CASE)
    val size = sizeRegex.find(title)?.value?.uppercase(Locale.ROOT) ?: ""
    return quality to size
}

/**
 * Extracts a clean and descriptive video name or domain name from the source stream url or title.
 */
fun getCleanVideoSourceLabel(stream: StreamSource): String {
    // 1. Torrent / Magnet
    val isTorrent = stream.isTorrent
    if (isTorrent) {
        if (!stream.title.isNullOrBlank()) {
            return stream.title.trim()
        }
        return "Torrent: ${stream.p2pHash?.take(8) ?: "Magnet"}"
    }

    // 2. Direct or Embed HTTP Link
    val url = stream.url
    if (!url.isNullOrBlank()) {
        try {
            // Unescape href.li if present
            val cleanUrl = if (url.contains("href.li/?", ignoreCase = true)) {
                val idx = url.indexOf("href.li/?")
                url.substring(idx + "href.li/?".length)
            } else {
                url
            }

            val uri = java.net.URI(cleanUrl)
            val host = uri.host
            val path = uri.path

            val hostLabel = if (!host.isNullOrBlank()) {
                var h = host.lowercase(Locale.ROOT)
                if (h.startsWith("www.")) h = h.substring(4)
                val parts = h.split(".")
                if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
            } else {
                ""
            }

            val pathLabel = if (!path.isNullOrBlank()) {
                val segments = path.split("/").filter { it.isNotBlank() }
                if (segments.isNotEmpty()) {
                    val lastSegment = segments.last()
                    // If the last segment is just generic like "master.m3u8", "index.m3u8", "playlist.m3u8", "video.mp4", "play.html", etc.
                    val isGeneric = lastSegment.equals("master.m3u8", ignoreCase = true) ||
                            lastSegment.equals("index.m3u8", ignoreCase = true) ||
                            lastSegment.equals("playlist.m3u8", ignoreCase = true) ||
                            lastSegment.equals("video.mp4", ignoreCase = true) ||
                            lastSegment.equals("play", ignoreCase = true) ||
                            lastSegment.equals("play.html", ignoreCase = true) ||
                            lastSegment.matches(Regex("""\d+""")) // only digits
                    
                    if (isGeneric && segments.size >= 2) {
                        segments[segments.size - 2] + "/" + lastSegment
                    } else {
                        lastSegment
                    }
                } else {
                    ""
                }
            } else {
                ""
            }

            return when {
                !hostLabel.isNullOrBlank() && !pathLabel.isNullOrBlank() -> "$hostLabel • $pathLabel"
                !hostLabel.isNullOrBlank() -> hostLabel
                !pathLabel.isNullOrBlank() -> pathLabel
                else -> stream.title.ifBlank { "Doğrudan Bağlantı" }
            }
        } catch (e: Exception) {
            // Fallback
        }
    }

    return stream.title.ifBlank { "Doğrudan Bağlantı" }
}


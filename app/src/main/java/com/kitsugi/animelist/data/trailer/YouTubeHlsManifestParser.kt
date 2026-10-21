package com.kitsugi.animelist.data.trailer

import java.net.URI

internal data class YouTubeHlsManifestInfo(
    /** Highest-quality child playlist for diagnostics; null for a media playlist. */
    val selectedVariantUrl: String?,
    val width: Int,
    val height: Int,
    val bandwidth: Long
)

/** Pure parser for YouTube's HLS master and media playlists. */
internal object YouTubeHlsManifestParser {
    fun parse(manifestUrl: String, body: String): YouTubeHlsManifestInfo? {
        val lines = body
            .lineSequence()
            .map { it.trim().removePrefix("\uFEFF").trim() }
            .filter(String::isNotBlank)
            .toList()
        if (lines.firstOrNull() != "#EXTM3U") return null

        var best: YouTubeHlsManifestInfo? = null
        for (index in lines.indices) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) continue

            val attributes = parseAttributeList(line)
            val childLine = lines.drop(index + 1).firstOrNull { !it.startsWith('#') } ?: continue
            val (width, height) = parseResolution(attributes["RESOLUTION"].orEmpty())
            val bandwidth = attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull()
                ?: attributes["BANDWIDTH"]?.toLongOrNull()
                ?: 0L
            val candidate = YouTubeHlsManifestInfo(
                selectedVariantUrl = resolveUrl(manifestUrl, childLine),
                width = width,
                height = height,
                bandwidth = bandwidth
            )
            val previous = best
            if (previous == null || candidate.height > previous.height ||
                (candidate.height == previous.height && candidate.bandwidth > previous.bandwidth) ||
                (candidate.height == previous.height && candidate.bandwidth == previous.bandwidth && candidate.width > previous.width)
            ) {
                best = candidate
            }
        }
        if (best != null) return best

        // YouTube may return an already-selected media playlist instead of a master. The
        // old extractor accepted only #EXT-X-STREAM-INF, rejected this valid form, then
        // fell back to a signed adaptive URL that can expire before playback starts.
        val isMediaPlaylist = lines.any { line ->
            line.startsWith("#EXTINF:", ignoreCase = true) ||
                line.startsWith("#EXT-X-TARGETDURATION:", ignoreCase = true) ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) ||
                line.startsWith("#EXT-X-PART:", ignoreCase = true) ||
                line.startsWith("#EXT-X-MAP:", ignoreCase = true)
        }
        return if (isMediaPlaylist) {
            YouTubeHlsManifestInfo(selectedVariantUrl = null, width = 0, height = 0, bandwidth = 0L)
        } else {
            null
        }
    }

    private fun parseAttributeList(line: String): Map<String, String> {
        val colon = line.indexOf(':')
        if (colon < 0) return emptyMap()

        val values = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var readingKey = true
        var inQuotes = false
        for (char in line.substring(colon + 1)) {
            if (readingKey) {
                if (char == '=') readingKey = false else key.append(char)
                continue
            }
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    if (key.isNotEmpty()) values[key.toString().trim()] = value.toString().trim()
                    key.clear()
                    value.clear()
                    readingKey = true
                }
                else -> value.append(char)
            }
        }
        if (key.isNotEmpty()) values[key.toString().trim()] = value.toString().trim()
        return values
    }

    private fun parseResolution(raw: String): Pair<Int, Int> {
        val parts = raw.split('x', limit = 2)
        if (parts.size != 2) return 0 to 0
        return (parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 0)
    }

    private fun resolveUrl(baseUrl: String, childUrl: String): String = runCatching {
        URI(baseUrl).resolve(childUrl).toString()
    }.getOrElse { childUrl }
}

package com.kitsugi.animelist.data.trailer

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "InAppYouTubeExtractor"
private const val EXTRACTOR_TIMEOUT_MS = 18_000L
private const val CLIENT_TIMEOUT_MS = 8_000L

private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")

private val CLIENTS = YouTubePlayerClients.ALL

private data class WatchConfig(
    val apiKey: String?,
    val visitorData: String?
)

private data class ResolvedClient(
    val client: YouTubeClient,
    val videos: List<YouTubeStreamCandidate> = emptyList(),
    val audios: List<YouTubeStreamCandidate> = emptyList(),
    val progressive: List<YouTubeStreamCandidate> = emptyList(),
    val manifests: List<YouTubeManifestCandidate> = emptyList(),
    val loginRequired: Boolean = false
)

private val DEFAULT_HEADERS = mapOf(
    "accept-language" to "en-US,en;q=0.9",
    "user-agent" to YouTubeRequestPolicy.STREAM_USER_AGENT
)

class InAppYouTubeExtractor {
    private val gson = Gson()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // --- Cached watch config (api key + visitor data) ---
    private data class CachedConfig(
        val apiKey: String,
        val visitorData: String?,
        val fetchedAt: Long = System.currentTimeMillis()
    )

    private val cachedConfig = AtomicReference<CachedConfig?>(null)
    private val configMutex = Mutex()
    private val sourceCache = YouTubePlaybackSourceCache()

    companion object {
        /** A process-wide extractor reuses config and HTTP resources across detail-video switches. */
        val shared: InAppYouTubeExtractor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            InAppYouTubeExtractor()
        }

        /** How long cached visitor_data stays valid before a proactive refresh. */
        private const val CONFIG_TTL_MS = 3 * 60 * 60 * 1000L // 3 hours
    }

    /**
     * Returns cached watch config, fetching from watch page only if:
     *  - No cached config exists yet (first call)
     *  - Cache is older than CONFIG_TTL_MS
     *  - [forceRefresh] is true (e.g. after LOGIN_REQUIRED)
     */
    private suspend fun ensureWatchConfig(forceRefresh: Boolean = false): CachedConfig {
        // Fast path: return valid cache without locking
        if (!forceRefresh) {
            val current = cachedConfig.get()
            if (current != null && !isConfigStale(current)) {
                return current
            }
        }

        // Slow path: fetch new config under mutex (only one fetch at a time)
        return configMutex.withLock {
            // Double-check after acquiring lock
            if (!forceRefresh) {
                val current = cachedConfig.get()
                if (current != null && !isConfigStale(current)) {
                    return@withLock current
                }
            }

            Log.d(TAG, "Fetching watch page for visitor_data (forceRefresh=$forceRefresh)")
            val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en"
            val watchResponse = performRequest(
                url = watchUrl,
                method = "GET",
                headers = DEFAULT_HEADERS
            )
            if (!watchResponse.ok) {
                // If we have a stale config, prefer it over failing
                val stale = cachedConfig.get()
                if (stale != null) {
                    Log.w(TAG, "Watch page failed (${watchResponse.status}), using stale config")
                    return@withLock stale
                }
                throw IllegalStateException("Failed to fetch watch page (${watchResponse.status})")
            }

            val parsed = getWatchConfig(watchResponse.body)
            val apiKey = parsed.apiKey ?: "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" // fallback key
            val newConfig = CachedConfig(
                apiKey = apiKey,
                visitorData = parsed.visitorData
            )
            cachedConfig.set(newConfig)
            Log.d(TAG, "Watch config cached (visitor=${!parsed.visitorData.isNullOrBlank()})")
            newConfig
        }
    }

    private fun isConfigStale(config: CachedConfig): Boolean {
        return System.currentTimeMillis() - config.fetchedAt > CONFIG_TTL_MS
    }

    /** Invalidate cached config so next extraction re-fetches watch page. */
    fun invalidateConfig() {
        cachedConfig.set(null)
        sourceCache.clear()
        Log.d(TAG, "Watch config and resolved-source cache invalidated")
    }

    /** Called after every candidate failed, so manual retry obtains fresh signed URLs. */
    fun invalidatePlaybackSource(videoId: String) {
        sourceCache.remove(videoId)
    }

    fun clearPlaybackSourceCache() {
        sourceCache.clear()
    }

    suspend fun extractPlaybackSource(youtubeUrl: String): TrailerPlaybackSource? =
        withContext(Dispatchers.IO) {
            val videoId = extractVideoId(youtubeUrl) ?: return@withContext null
            sourceCache.get(videoId)?.let { cached ->
                Log.d(TAG, "Using cached playback source for $videoId")
                return@withContext cached
            }

            Log.d(TAG, "Starting Kotlin extraction for ${summarizeUrl(youtubeUrl)}")
            val source = try {
                withTimeoutOrNull(EXTRACTOR_TIMEOUT_MS) {
                    extractPlaybackSourceInternal(videoId)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Kotlin extractor failed for $videoId: ${error.message}")
                null
            }

            if (source == null) {
                sourceCache.remove(videoId)
                Log.w(TAG, "Kotlin extraction returned no playable source for $videoId")
            } else {
                sourceCache.put(videoId, source)
                Log.d(
                    TAG,
                    "Kotlin extraction success for $videoId " +
                        "(candidates=${source.playbackCandidates().size}, " +
                        "video=${summarizeUrl(source.videoUrl)}, " +
                        "audioPresent=${!source.audioUrl.isNullOrBlank()})"
                )
            }
            source
        }

    private data class ExtractionResult(
        val source: TrailerPlaybackSource?,
        val shouldRefreshConfig: Boolean
    )

    private suspend fun extractPlaybackSourceInternal(videoId: String): TrailerPlaybackSource? {
        var config = ensureWatchConfig()
        var result = resolveWithConfig(videoId, config)
        if (result.source == null && result.shouldRefreshConfig) {
            config = ensureWatchConfig(forceRefresh = true)
            result = resolveWithConfig(videoId, config)
        }
        return result.source
    }

    private suspend fun resolveWithConfig(
        videoId: String,
        config: CachedConfig
    ): ExtractionResult = coroutineScope {
        val resolvedClients = CLIENTS.map { client ->
            async {
                try {
                    withTimeoutOrNull(CLIENT_TIMEOUT_MS) {
                        resolveClient(videoId, config, client)
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Client ${client.key} failed: ${error.message}")
                    }
                    null
                }
            }
        }.awaitAll().filterNotNull()

        val loginRequiredCount = resolvedClients.count { it.loginRequired }
        val respondedLoginSensitiveClients = resolvedClients.count { it.client.needsVisitorData }
        val shouldRefreshConfig = respondedLoginSensitiveClients > 0 &&
            loginRequiredCount == respondedLoginSensitiveClients

        val manifests = resolvedClients.flatMap { it.manifests }
        val checkedManifests = manifests.map { manifest ->
            async {
                try {
                    val variant = parseHlsManifest(
                        manifestUrl = manifest.manifestUrl,
                        requestHeaders = headersForClient(manifest.client)
                    ) ?: return@async null
                    manifest.copy(
                        width = variant.width,
                        height = variant.height,
                        bandwidth = variant.bandwidth
                    )
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Manifest parse failed for ${manifest.client}: ${error.message}")
                    }
                    null
                }
            }
        }.awaitAll().filterNotNull()

        val orderedSources = YouTubePlaybackCandidates.buildOrderedSources(
            manifests = checkedManifests,
            adaptiveVideos = resolvedClients.flatMap { it.videos },
            adaptiveAudios = resolvedClients.flatMap { it.audios },
            progressive = resolvedClients.flatMap { it.progressive },
            headersForClient = ::headersForClient
        )
        val source = orderedSources.asPrimaryWithAlternatives()
        if (source == null) {
            Log.w(
                TAG,
                "No playable formats for $videoId " +
                    "(clients=${resolvedClients.size}, loginRequired=$loginRequiredCount)"
            )
        }
        ExtractionResult(source = source, shouldRefreshConfig = shouldRefreshConfig)
    }

    private suspend fun resolveClient(
        videoId: String,
        config: CachedConfig,
        client: YouTubeClient
    ): ResolvedClient? {
        val playerResponse = fetchPlayerResponse(
            apiKey = config.apiKey,
            videoId = videoId,
            client = client,
            visitorData = config.visitorData,
            cookieHeader = null
        )
        val playabilityStatus = playerResponse.mapValue("playabilityStatus")
        val status = playabilityStatus?.stringValue("status")
        if (status == "LOGIN_REQUIRED") {
            Log.w(TAG, "Client ${client.key}: LOGIN_REQUIRED")
            return ResolvedClient(client = client, loginRequired = true)
        }
        if (status != null && status != "OK") {
            Log.d(TAG, "Client ${client.key}: status=$status")
            return null
        }
        val streamingData = playerResponse.mapValue("streamingData") ?: return null
        val videos = mutableListOf<YouTubeStreamCandidate>()
        val audios = mutableListOf<YouTubeStreamCandidate>()
        val progressive = mutableListOf<YouTubeStreamCandidate>()
        val manifests = mutableListOf<YouTubeManifestCandidate>()

        streamingData.stringValue("hlsManifestUrl")
            ?.takeIf { it.isNotBlank() }
            ?.let { manifestUrl ->
                manifests += YouTubeManifestCandidate(
                    client = client.key,
                    priority = client.priority,
                    manifestUrl = manifestUrl,
                    width = 0,
                    height = 0,
                    bandwidth = 0
                )
            }

        streamingData.listMapValue("formats").forEach { format ->
            streamCandidate(format, client, expectAudio = null)?.let(progressive::add)
        }
        streamingData.listMapValue("adaptiveFormats").forEach { format ->
            val mimeType = format.stringValue("mimeType").orEmpty()
            when {
                mimeType.contains("video/") ->
                    streamCandidate(format, client, expectAudio = false)?.let(videos::add)
                mimeType.contains("audio/") ->
                    streamCandidate(format, client, expectAudio = true)?.let(audios::add)
            }
        }
        return ResolvedClient(
            client = client,
            videos = videos,
            audios = audios,
            progressive = progressive,
            manifests = manifests
        )
    }

    private fun streamCandidate(
        format: Map<*, *>,
        client: YouTubeClient,
        expectAudio: Boolean?
    ): YouTubeStreamCandidate? {
        // cipher/signatureCipher formats need the current player JS. Never treat those as direct URLs.
        val url = format.stringValue("url")?.takeIf { it.isNotBlank() } ?: return null
        val mimeType = format.stringValue("mimeType").orEmpty()
        val isVideo = mimeType.contains("video/")
        val isAudio = mimeType.contains("audio/")
        if (expectAudio == true && !isAudio) return null
        if (expectAudio == false && !isVideo) return null
        if (expectAudio == null && !isVideo) return null

        val height = if (isVideo) {
            (format.numberValue("height")
                ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                ?: 0.0).toInt()
        } else 0
        val fps = (format.numberValue("fps") ?: 0.0).toInt()
        val bitrate = format.numberValue("bitrate")
            ?: format.numberValue("averageBitrate")
            ?: 0.0
        val score = if (isAudio) {
            audioScore(bitrate, format.numberValue("audioSampleRate") ?: 0.0)
        } else {
            videoScore(height, fps, bitrate)
        }
        return YouTubeStreamCandidate(
            client = client.key,
            priority = client.priority,
            url = url,
            score = score,
            hasN = hasNParam(url),
            itag = format.stringValue("itag").orEmpty(),
            height = height,
            fps = fps,
            ext = when {
                mimeType.contains("webm") -> "webm"
                isAudio -> "m4a"
                else -> "mp4"
            }
        )
    }

    private fun headersForClient(clientKey: String): Map<String, String> {
        val client = CLIENTS.firstOrNull { it.key == clientKey } ?: return emptyMap()
        return mapOf(
            "User-Agent" to client.userAgent,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/"
        )
    }

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return runCatching {
            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("youtu.be")) {
                val id = uri.pathSegments.firstOrNull()
                if (!id.isNullOrBlank() && VIDEO_ID_REGEX.matches(id)) {
                    return id
                }
            }

            val queryId = uri.getQueryParameter("v")
            if (!queryId.isNullOrBlank() && VIDEO_ID_REGEX.matches(queryId)) {
                return queryId
            }

            val segments = uri.pathSegments
            if (segments.size >= 2) {
                val first = segments[0]
                val second = segments[1]
                if ((first == "embed" || first == "shorts" || first == "live") && VIDEO_ID_REGEX.matches(second)) {
                    return second
                }
            }

            null
        }.getOrNull()
    }

    private fun getWatchConfig(html: String): WatchConfig {
        val apiKey = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
        val visitorData = VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
        return WatchConfig(apiKey = apiKey, visitorData = visitorData)
    }

    private suspend fun fetchPlayerResponse(
        apiKey: String,
        videoId: String,
        client: YouTubeClient,
        visitorData: String?,
        cookieHeader: String?
    ): Map<*, *> {
        val endpoint = "https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}"

        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (client.needsVisitorData && !visitorData.isNullOrBlank()) put("x-goog-visitor-id", visitorData)
            if (!cookieHeader.isNullOrBlank()) put("cookie", cookieHeader)
            // Embedded clients require a referer for embed-accessible formats
            if (client.embedUrl != null) put("referer", client.embedUrl)
        }

        val payload = buildMap<String, Any> {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            // Embedded clients inject thirdParty.embedUrl into context.
            put("context", buildMap {
                put("client", client.context)
                if (client.embedUrl != null) {
                    put("thirdParty", mapOf("embedUrl" to client.embedUrl))
                }
            })
            put("playbackContext", mapOf(
                "contentPlaybackContext" to mapOf("html5Preference" to "HTML5_PREF_WANTS")
            ))
        }

        val response = performRequest(
            url = endpoint,
            method = "POST",
            headers = headers,
            body = gson.toJson(payload)
        )
        if (!response.ok) {
            throw IllegalStateException("player API ${client.key} failed (${response.status})")
        }

        val parsed = gson.fromJson(response.body, Map::class.java)
        return parsed ?: emptyMap<String, Any>()
    }

    private suspend fun parseHlsManifest(
        manifestUrl: String,
        requestHeaders: Map<String, String>
    ): YouTubeHlsManifestInfo? {
        val response = performRequest(
            url = manifestUrl,
            method = "GET",
            headers = YouTubeRequestPolicy.youtubePlaybackHeaders(requestHeaders)
        )
        if (!response.ok) {
            throw IllegalStateException("Failed to fetch HLS manifest (${response.status})")
        }
        return YouTubeHlsManifestParser.parse(manifestUrl, response.body)
    }

    private fun parseQualityLabel(label: String?): Int? {
        if (label.isNullOrBlank()) return null
        val match = QUALITY_LABEL_REGEX.find(label) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun hasNParam(url: String): Boolean {
        return runCatching {
            !Uri.parse(url).getQueryParameter("n").isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun videoScore(height: Int, fps: Int, bitrate: Double): Double {
        return height * 1_000_000_000.0 + fps * 1_000_000.0 + bitrate
    }

    private fun audioScore(bitrate: Double, audioSampleRate: Double): Double {
        return bitrate * 1_000_000.0 + audioSampleRate
    }

    private fun summarizeUrl(url: String): String {
        return runCatching {
            val parsed = URL(url)
            val host = parsed.host ?: "unknown-host"
            val path = parsed.path ?: "/"
            "$host$path"
        }.getOrDefault(url.take(80))
    }

    private suspend fun performRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null
    ): RequestResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))

        when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toRequestBody())
            "PUT" -> requestBuilder.put((body ?: "").toRequestBody())
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        return executeCallCancellable(httpClient.newCall(requestBuilder.build())) { response ->
            RequestResponse(
                ok = response.isSuccessful,
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
        }
    }

    /**
     * Executes an OkHttp request as a cancellable suspend call. Removing a player from the
     * Compose tree now cancels its in-flight YouTube extraction instead of leaving blocking
     * requests alive until their socket timeout.
     */
    private suspend fun <T> executeCallCancellable(
        call: Call,
        readResponse: (Response) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine

        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        runCatching { continuation.resumeWith(Result.failure(e)) }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use(readResponse) }
                    if (continuation.isActive) {
                        runCatching { continuation.resumeWith(result) }
                    }
                }
            })
        } catch (e: Exception) {
            if (continuation.isActive) {
                runCatching { continuation.resumeWith(Result.failure(e)) }
            }
        }
    }

    private fun buildHeaders(source: Map<String, String>): Headers {
        val headers = Headers.Builder()
        source.forEach { (name, value) ->
            if (!name.equals("Accept-Encoding", ignoreCase = true)) {
                headers.add(name, value)
            }
        }
        if (source.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers.add("User-Agent", YouTubeRequestPolicy.STREAM_USER_AGENT)
        }
        return headers.build()
    }
}

private data class RequestResponse(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String
)

private fun Map<*, *>.mapValue(key: String): Map<*, *>? {
    return this[key] as? Map<*, *>
}

private fun Map<*, *>.listMapValue(key: String): List<Map<*, *>> {
    val raw = this[key] as? List<*> ?: return emptyList()
    return raw.mapNotNull { it as? Map<*, *> }
}

private fun Map<*, *>.stringValue(key: String): String? {
    val value = this[key] ?: return null
    return value.toString()
}

private fun Map<*, *>.numberValue(key: String): Double? {
    val value = this[key] ?: return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

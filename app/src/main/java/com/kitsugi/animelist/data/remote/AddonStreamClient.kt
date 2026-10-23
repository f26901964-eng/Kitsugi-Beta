package com.kitsugi.animelist.data.remote

import android.util.Log
import com.google.gson.Gson
import com.kitsugi.animelist.core.network.IPv4FirstDns
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class AddonStreamClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_ANI_SUB_ARCHIVE_BYTES = 80L * 1024 * 1024
        private const val MAX_ANI_SUB_EXTRACTED_BYTES = 100L * 1024 * 1024
        private const val MAX_ANI_SUB_ENTRY_BYTES = 20L * 1024 * 1024
        private const val MAX_ANI_SUB_ARCHIVE_FILES = 300
        private val SUPPORTED_ANI_SUB_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt")
    }

    /**
     * Normalizes a user-input manifest URL, converting stremio:// to https:// and appending
     * /manifest.json if missing, while preserving query parameters.
     */
    fun normalizeManifestUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""

        val normalizedScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("stremio://") -> "https://${trimmed.removePrefix("stremio://")}"
            else -> "https://$trimmed"
        }

        val withoutFragment = normalizedScheme.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val manifestPath = if (path.endsWith("/manifest.json")) {
            path
        } else {
            "$path/manifest.json"
        }

        return if (query.isEmpty()) manifestPath else "$manifestPath?$query"
    }

    suspend fun fetchManifest(manifestUrl: String): ManagedAddonEntity? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeManifestUrl(manifestUrl)
        if (normalizedUrl.isBlank()) return@withContext null

        if (normalizedUrl.contains("anisub.co")) {
            return@withContext ManagedAddonEntity(
                manifestUrl = normalizedUrl,
                name = "AniSub.co",
                description = KitsugiStrings.get(R.string.anisub_co_turkce_altyazi_eklentisi),
                icon = "https://anisub.co/favicon.ico",
                isEnabled = true,
                orderIndex = 99,
                idPrefixes = null,
                streamTypes = null,
                subtitleTypes = "movie,series"
            )
        }

        val request = Request.Builder()
            .url(normalizedUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AddonStreamClient", "HTTP error ${response.code} fetching manifest from $normalizedUrl")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val manifestMap = gson.fromJson(bodyString, Map::class.java) ?: return@withContext null

                val name = manifestMap["name"] as? String ?: "Bilinmeyen Eklenti"
                val description = manifestMap["description"] as? String
                val icon = manifestMap["icon"] as? String

                // Parse top-level idPrefixes (e.g. ["tt", "kitsu:"])
                @Suppress("UNCHECKED_CAST")
                val idPrefixList = (manifestMap["idPrefixes"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

                // Stream resource: Stremio spesifikasyonuna göre ayrıştırılır (bkz. StremioStreamRouting).
                // Kısa biçim ("stream") manifest düzeyindeki types/idPrefixes'i miras alır; tam biçim
                // ({name, types, idPrefixes}) kendi değerlerini kullanır. Eskiden tam biçimdeki
                // idPrefixes yok sayılıyordu ve addon'un ilan ettiği tür istek yoluna yansımıyordu.
                @Suppress("UNCHECKED_CAST")
                val resourcesList = manifestMap["resources"] as? List<*>
                val manifestTypes = (manifestMap["types"] as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                val streamSpec = StremioStreamRouting.parseStreamResource(
                    resources = resourcesList,
                    manifestTypes = manifestTypes,
                    manifestIdPrefixes = idPrefixList
                )
                // null → stream resource yok (eski davranış: tür filtresi yok)
                // "*"  → stream var ama tür beyan edilmemiş
                // csv  → addon'un ilan ettiği stream türleri
                val streamTypes: String? = when {
                    streamSpec == null -> null
                    streamSpec.types == null -> StremioStreamRouting.ANY_TYPE
                    else -> streamSpec.types.joinToString(",")
                }
                // Stream için geçerli prefix'ler: stream resource'a özgü değer varsa o, yoksa top-level.
                val streamIdPrefixList = streamSpec?.idPrefixes ?: idPrefixList

                val subtitleTypes = resourcesList?.mapNotNull { res ->
                    when (res) {
                        is String -> if (res == "subtitles") "movie,series" else null
                        is Map<*, *> -> {
                            val resName = res["name"] as? String ?: return@mapNotNull null
                            if (resName != "subtitles") return@mapNotNull null
                            @Suppress("UNCHECKED_CAST")
                            val types = (res["types"] as? List<*>)?.filterIsInstance<String>()
                            val typesStr = types?.joinToString(",")
                            if (typesStr.isNullOrBlank()) "movie,series" else typesStr
                        }
                        else -> null
                    }
                }?.firstOrNull() ?: run {
                    if (resourcesList?.contains("subtitles") == true) "movie,series" else null
                }

                return@withContext ManagedAddonEntity(
                    manifestUrl = normalizedUrl,
                    name = name,
                    description = description,
                    icon = icon,
                    isEnabled = true,
                    orderIndex = 0,
                    // Stream prefix'leri (stream resource'a özgü ya da top-level). Altyazı kodu bu alanı kullanmaz.
                    idPrefixes = if (streamIdPrefixList.isEmpty()) null else gson.toJson(streamIdPrefixList),
                    streamTypes = streamTypes,
                    subtitleTypes = if (subtitleTypes == null) null else subtitleTypes.ifBlank { null }
                )
            }
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error fetching manifest from $normalizedUrl", e)
            null
        }
    }

    suspend fun fetchStreams(
        manifestUrl: String,
        type: String, // "series" or "movie"
        id: String // tt1234567:1:5
    ): List<StreamResponseItem> = withContext(Dispatchers.IO) {
        val normalizedManifest = normalizeManifestUrl(manifestUrl)
        if (normalizedManifest.isBlank()) return@withContext emptyList()

        val withoutFragment = normalizedManifest.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val baseUrl = path.removeSuffix("/manifest.json").trimEnd('/')

        // URL encode path segments exactly like KitsugiTV-dev's encodePathSegment:
        // URLEncoder replaces spaces with '+', but RFC 3986 path segments need '%20'
        val encodedType = encodePathSegment(type)
        val encodedId = id.split(':').map { encodePathSegment(it) }.joinToString(":")

        val resourceUrl = "$baseUrl/stream/$encodedType/$encodedId.json"
        val finalUrl = if (query.isEmpty()) resourceUrl else "$resourceUrl?$query"

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AddonStreamClient", "HTTP error ${response.code} fetching streams from $finalUrl")
                    return@withContext emptyList()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val streamResult = gson.fromJson(bodyString, StreamResponseWrapper::class.java)
                return@withContext streamResult?.streams ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error fetching streams from $finalUrl", e)
            emptyList()
        }
    }

    suspend fun fetchSubtitles(
        context: android.content.Context,
        manifestUrl: String,
        type: String, // "series" or "movie"
        id: String, // tt1234567:1:5
        extraParams: Map<String, String>? = null,
        aniListId: Int? = null,
        animeTitle: String? = null,
        episode: Int? = null
    ): List<SubtitleResponseItem> = withContext(Dispatchers.IO) {
        if (manifestUrl.contains("anisub.co")) {
            return@withContext fetchAniSubSubtitles(context, id, aniListId, animeTitle, episode)
        }

        val normalizedManifest = normalizeManifestUrl(manifestUrl)
        if (normalizedManifest.isBlank()) return@withContext emptyList()

        val withoutFragment = normalizedManifest.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val baseUrl = path.removeSuffix("/manifest.json").trimEnd('/')

        // Stremio subtitle URL formatı (NuvioTV SubtitleRepositoryImpl referansı):
        // {baseUrl}/subtitles/{type}/{id}/{videoHash=x&videoSize=y&filename=z}.json
        // NOT: id segmenti URL-encode edilmelidir çünkü ':' gibi karakterler yönlendirmeyi bozabilir.
        // NOT: extraParams path segment içine eklenir, query string'e değil
        val encodedType = encodePathSegment(type)
        val encodedId = id.split(':').map { encodePathSegment(it) }.joinToString(":")
        val extraParamStr = buildSubtitleExtraParams(extraParams)

        val resourceUrl = if (extraParamStr.isNotBlank()) {
            "$baseUrl/subtitles/$encodedType/$encodedId/$extraParamStr.json"
        } else {
            "$baseUrl/subtitles/$encodedType/$encodedId.json"
        }
        val finalUrl = if (query.isEmpty()) resourceUrl else "$resourceUrl?$query"
        Log.d("AddonStreamClient", "fetchSubtitles URL: $finalUrl")

        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AddonStreamClient", "HTTP error ${response.code} fetching subtitles from $finalUrl")
                    return@withContext emptyList()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val subResult = gson.fromJson(bodyString, SubtitleResponseWrapper::class.java)
                return@withContext subResult?.subtitles ?: emptyList()
            }
        } catch (e: java.lang.Exception) {
            Log.e("AddonStreamClient", "Error fetching subtitles from $finalUrl", e)
            emptyList()
        }
    }

    /**
     * Encodes a URL path segment, replacing '+' with '%20' to be RFC 3986-compliant.
     * This matches KitsugiTV-dev's StreamRepositoryImpl.encodePathSegment exactly.
     */
    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    /**
     * Stremio subtitle extra params path segment oluşturur.
     * NuvioTV SubtitleRepositoryImpl.buildExtraParams() referans alınarak port edildi.
     *
     * Format: "videoHash=abc123&videoSize=1234567890&filename=episode.mkv"
     * Bu string daha sonra: /subtitles/{type}/{id}/{extraParams}.json şeklinde kullanılır.
     */
    private fun buildSubtitleExtraParams(params: Map<String, String>?): String {
        if (params.isNullOrEmpty()) return ""
        val parts = mutableListOf<String>()
        params["videoHash"]?.let { parts.add("videoHash=$it") }
        params["videoSize"]?.let { parts.add("videoSize=$it") }
        params["filename"]?.let {
            // filename değeri URL encode edilmeli (boşluk, Türkçe karakter vb. için)
            parts.add("filename=${encodePathSegment(it)}")
        }
        return parts.joinToString("&")
    }

    private suspend fun fetchAniSubSubtitles(
        context: android.content.Context,
        id: String,
        aniListId: Int?,
        animeTitle: String?,
        episode: Int?
    ): List<SubtitleResponseItem> = withContext(Dispatchers.IO) {
        try {
            val effectiveAniListId = aniListId ?: id.takeIf { it.startsWith("anilist:", ignoreCase = true) }
                ?.substringAfter(':')?.substringBefore(':')?.toIntOrNull()
            Log.d("AddonStreamClient", "fetchAniSubSubtitles: id=$id, aniListId=$effectiveAniListId, title=$animeTitle, ep=$episode")

            // AniSub's public search is an Inertia endpoint. Obtain the current asset version;
            // hard-coding it caused search requests to break whenever the website deployed.
            val initHtml = client.newCall(
                Request.Builder().url("https://anisub.co")
                    .header("User-Agent", DEFAULT_USER_AGENT).get().build()
            ).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
            if (initHtml.isBlank()) return@withContext emptyList()
            val version = """"version"\s*:\s*"([a-f0-9]{32})"""".toRegex(RegexOption.IGNORE_CASE)
                .find(initHtml)?.groupValues?.getOrNull(1)
                ?: "13462527c7e340ff00f088b7f91a548a"

            val title = animeTitle?.trim()?.takeIf(String::isNotBlank)
                ?: effectiveAniListId?.let { resolveAniListTitle(it) }
                ?: id.substringBefore(':').takeIf { it.startsWith("tt", ignoreCase = true) }?.let(::resolveImdbTitle)
            // Stremio IDs may be IMDb, Kitsu, or plain catalog IDs. Never turn `kitsu:123:1`
            // into the useless query "kitsu", and don't let an empty title suppress the ID fallback.
            val idQuery = when {
                id.startsWith("kitsu:", ignoreCase = true) || id.startsWith("anilist:", ignoreCase = true) ->
                    id.substringAfter(':').substringBefore(':')
                else -> id.substringBefore(':').trim()
            }
            val queries = listOfNotNull(
                title,
                if (title == null) effectiveAniListId?.toString() else null,
                if (title == null) idQuery.takeIf(String::isNotBlank) else null
            ).map(String::trim).filter(String::isNotBlank).distinct().take(3)
            if (queries.isEmpty()) {
                Log.w("AddonStreamClient", "AniSub search skipped: no usable title or catalog ID")
                return@withContext emptyList()
            }

            // Inertia paginates search results. Search a bounded number of pages for title hits
            // instead of silently missing items beyond page one.
            val itemsById = linkedMapOf<Long, AnisubSubtitleItem>()
            for (query in queries) {
                var page = 1
                var lastPage = 1
                while (page <= lastPage && page <= 4) {
                    val searchUrl = "https://anisub.co/tum-altyazilar?q=${URLEncoder.encode(query, "UTF-8")}&page=$page"
                    val json = client.newCall(
                        Request.Builder().url(searchUrl)
                            .header("User-Agent", DEFAULT_USER_AGENT)
                            .header("Accept", "application/json")
                            .header("x-inertia", "true")
                            .header("x-inertia-version", version)
                            .header("x-requested-with", "XMLHttpRequest")
                            .get().build()
                    ).execute().use { response ->
                        if (response.isSuccessful) response.body?.string().orEmpty() else ""
                    }
                    if (json.isBlank()) break
                    val result = runCatching { gson.fromJson(json, AnisubSearchResponse::class.java) }.getOrNull()
                    val data = result?.props?.subtitles ?: break
                    data.data.orEmpty().forEach { item -> item.subtitle_id?.let { itemsById.putIfAbsent(it, item) } }
                    lastPage = data.last_page?.coerceAtLeast(1) ?: 1
                    page++
                }
            }

            val normalizedQuery = title?.let(::normalizeAniSubTitle).orEmpty()
            val candidates = itemsById.values.filter { item ->
                val itemAniListId = item.mediaInfo?.media_anilist_id
                if (effectiveAniListId != null && itemAniListId != null && itemAniListId != effectiveAniListId) return@filter false
                if (effectiveAniListId != null && itemAniListId == effectiveAniListId) {
                    // Exact AniList identity is authoritative; title spelling may differ.
                } else if (normalizedQuery.isNotBlank()) {
                    val itemTitles = listOfNotNull(item.mediaInfo?.title_romaji, item.mediaInfo?.title_english)
                        .map(::normalizeAniSubTitle).filter(String::isNotBlank)
                    if (itemTitles.none { it.contains(normalizedQuery) || normalizedQuery.contains(it) }) return@filter false
                } else if (itemAniListId == null || itemAniListId != effectiveAniListId) {
                    // If all we had was an IMDb/Kitsu ID, only retain results whose indexed title
                    // contains that ID (the site may index external IDs); never return unrelated rows.
                    val external = normalizeAniSubTitle(idQuery)
                    val itemTitles = listOfNotNull(item.mediaInfo?.title_romaji, item.mediaInfo?.title_english)
                        .map(::normalizeAniSubTitle).filter(String::isNotBlank)
                    if (external.isBlank() || itemTitles.none { it.contains(external) }) return@filter false
                }
                episode == null || AniSubSubtitleMatcher.releaseMatchesEpisode(item.subtitle_release_name.orEmpty(), episode)
            }
            Log.d("AddonStreamClient", "AniSub candidates: ${candidates.size} for title=$title id=$effectiveAniListId episode=$episode")

            val subtitleItems = mutableListOf<SubtitleResponseItem>()
            // Keep the player request bounded even for very popular shows with many historical uploads.
            for (item in candidates.take(8)) {
                val subId = item.subtitle_id ?: continue
                val downloadInfoJson = client.newCall(
                    Request.Builder().url("https://anisub.co/api/subtitles/$subId/download")
                        .header("User-Agent", DEFAULT_USER_AGENT).get().build()
                ).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                } ?: continue
                val downloadInfo = runCatching { gson.fromJson(downloadInfoJson, AnisubDownloadResponse::class.java) }.getOrNull()
                val downloadUrl = downloadInfo?.data?.download_url?.takeIf { downloadInfo.success && it.isNotBlank() } ?: continue
                val files = downloadAndUnzipSubtitle(
                    context = context,
                    downloadUrl = downloadUrl,
                    subId = subId,
                    downloadFilename = downloadInfo?.data?.filename,
                    episode = episode,
                    mediaTitle = title,
                    releaseName = item.subtitle_release_name.orEmpty()
                )
                // AniSub.co is a Turkish-only source; normalize its display language for all rows.
                val lang = "tr"
                files.forEach { file ->
                    subtitleItems.add(
                        SubtitleResponseItem(
                            id = "anisub-$subId-${file.name.hashCode().toLong().and(0xffffffffL).toString(16)}",
                            url = "file://${file.absolutePath}",
                            lang = lang
                        )
                    )
                }
            }
            subtitleItems.distinctBy { it.url }
        } catch (e: Exception) {
            Log.e("AddonStreamClient", "Error in fetchAniSubSubtitles", e)
            emptyList()
        }
    }

    private fun normalizeAniSubTitle(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    /** Resolve the display title when a caller provides only an AniList numeric ID. */
    private fun resolveAniListTitle(aniListId: Int): String? {
        return runCatching {
            val payload = gson.toJson(
                mapOf("query" to "query { Media(id: $aniListId, type: ANIME) { title { romaji english } } }")
            )
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val media = gson.fromJson(response.body?.string(), Map::class.java)
                    ?.get("data").let { it as? Map<*, *> }
                    ?.get("Media").let { it as? Map<*, *> }
                    ?.get("title").let { it as? Map<*, *> }
                (media?.get("romaji") as? String)?.takeIf(String::isNotBlank)
                    ?: (media?.get("english") as? String)?.takeIf(String::isNotBlank)
            }
        }.getOrNull()
    }

    /** Resolve an IMDb-only Stremio request through Cinemeta when the caller has no title metadata. */
    private fun resolveImdbTitle(imdbId: String): String? {
        return runCatching {
            for (type in listOf("series", "movie")) {
                val request = Request.Builder()
                    .url("https://v3-cinemeta.strem.io/meta/$type/${encodePathSegment(imdbId)}.json")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "application/json")
                    .get().build()
                val title = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val meta = gson.fromJson(response.body?.string(), Map::class.java)
                        ?.get("meta") as? Map<*, *>
                    (meta?.get("name") as? String)?.takeIf(String::isNotBlank)
                }
                if (title != null) return title
            }
            null
        }.getOrNull()
    }

    private fun readLimitedBody(body: okhttp3.ResponseBody, limitBytes: Long): ByteArray? {
        if (body.contentLength() > limitBytes) return null
        val output = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > limitBytes) return null
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun downloadAndUnzipSubtitle(
        context: android.content.Context,
        downloadUrl: String,
        subId: Long,
        downloadFilename: String?,
        episode: Int?,
        mediaTitle: String?,
        releaseName: String
    ): List<java.io.File> {
        val cacheDir = java.io.File(context.cacheDir, "anisub_subs/$subId")
        return try {
            if (!cacheDir.exists() && !cacheDir.mkdirs()) return emptyList()
            val cached = cacheDir.listFiles()?.filter { it.isFile }.orEmpty()
            if (cached.isNotEmpty()) {
                return AniSubSubtitleMatcher.selectFiles(cached, episode, mediaTitle, releaseName)
            }

            val archiveBytes = client.newCall(
                Request.Builder().url(downloadUrl).header("User-Agent", DEFAULT_USER_AGENT).get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body ?: return emptyList()
                readLimitedBody(body, MAX_ANI_SUB_ARCHIVE_BYTES)
            } ?: return emptyList()

            val isZip = archiveBytes.size >= 4 && archiveBytes[0] == 0x50.toByte() && archiveBytes[1] == 0x4b.toByte()
            if (!isZip) {
                val text = archiveBytes.toString(Charsets.UTF_8)
                val inferredExtension = downloadFilename?.substringAfterLast('.', "")?.lowercase()
                    ?.takeIf { it in SUPPORTED_ANI_SUB_EXTENSIONS }
                    ?: when {
                        text.trimStart().startsWith("[Script Info]", ignoreCase = true) -> "ass"
                        text.trimStart().startsWith("WEBVTT", ignoreCase = true) -> "vtt"
                        text.contains("-->") -> "srt"
                        else -> null
                    }
                if (inferredExtension == null) return emptyList()
                val directFile = java.io.File(cacheDir, "subtitle.$inferredExtension")
                directFile.writeBytes(archiveBytes)
                return AniSubSubtitleMatcher.selectFiles(listOf(directFile), episode, mediaTitle, releaseName)
            }

            val extractedFiles = mutableListOf<java.io.File>()
            var totalExtractedBytes = 0L
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(archiveBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && extractedFiles.size < MAX_ANI_SUB_ARCHIVE_FILES) {
                    if (!entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in SUPPORTED_ANI_SUB_EXTENSIONS) {
                        // Use only the basename, so ZIP paths cannot escape the app cache directory.
                        val baseName = java.io.File(entry.name).name
                        if (baseName.isNotBlank()) {
                            val destination = java.io.File(cacheDir, "${extractedFiles.size}_$baseName")
                            var entryBytes = 0L
                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count <= 0) break
                                    entryBytes += count
                                    totalExtractedBytes += count
                                    if (entryBytes > MAX_ANI_SUB_ENTRY_BYTES || totalExtractedBytes > MAX_ANI_SUB_EXTRACTED_BYTES) {
                                        throw IOException("AniSub archive exceeds extraction size limit")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                            extractedFiles.add(destination)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            AniSubSubtitleMatcher.selectFiles(extractedFiles, episode, mediaTitle, releaseName)
        } catch (e: Exception) {
            // Do not let a partially extracted/bomb archive masquerade as a valid cached subtitle.
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.e("AddonStreamClient", "Error downloading/unzipping AniSub subtitle $subId", e)
            emptyList()
        }
    }

}

private data class AnisubSearchResponse(val props: AnisubProps?)
private data class AnisubProps(val subtitles: AnisubSubtitlesData?)
private data class AnisubSubtitlesData(val data: List<AnisubSubtitleItem>?, val current_page: Int? = null, val last_page: Int? = null)
private data class AnisubSubtitleItem(
    val subtitle_id: Long?,
    val subtitle_release_name: String?,
    val subtitle_language: String?,
    val format: String?,
    val mediaInfo: AnisubMediaInfo?
)
private data class AnisubMediaInfo(
    val media_anilist_id: Int?,
    val title_romaji: String?,
    val title_english: String?
)
private data class AnisubDownloadResponse(
    val success: Boolean,
    val data: AnisubDownloadData?,
    val message: String?
)
private data class AnisubDownloadData(
    val download_url: String?,
    val filename: String?,
    val file_size: Long?
)


data class SubtitleResponseWrapper(
    val subtitles: List<SubtitleResponseItem>?
)

data class SubtitleResponseItem(
    val id: String?,
    val url: String?,
    val lang: String?
)

data class StreamResponseWrapper(
    val streams: List<StreamResponseItem>?
)

/**
 * Raw Stremio stream object returned by an addon.
 * `behaviorHints.proxyHeaders.request` carries the HTTP headers needed to play the stream.
 */
data class StreamResponseItem(
    val name: String?,
    val title: String?,
    val url: String?,
    val infoHash: String?,
    /**
     * Stremio addon protocol uses "fileIdx".
     * SerializedName ensures Gson correctly parses fileIdx into this field.
     */
    @com.google.gson.annotations.SerializedName("fileIdx")
    val fileIdx: Int? = null,
    /**
     * Stremio "sources" array containing tracker and peer addresses (e.g. tracker:udp://...).
     */
    val sources: List<String>? = null,
    val behaviorHints: StreamBehaviorHintsDto?,
    /** Episode/stream thumbnail URL returned by some addons (e.g. Cloudstream, Torrentio) */
    val thumbnail: String? = null
) {
    /** Backward compatibility property for existing references */
    val fileIndex: Int? get() = fileIdx

    /** Clean tracker URLs extracted from "tracker:" prefixed entries in sources */
    val trackers: List<String>
        get() = sources
            ?.asSequence()
            ?.filter { it.startsWith("tracker:", ignoreCase = true) }
            ?.map { it.removePrefix("tracker:").removePrefix("tracker:").trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.toList()
            ?: emptyList()
}

/**
 * Top-level `behaviorHints` object from a Stremio stream response.
 */
data class StreamBehaviorHintsDto(
    val notWebReady: Boolean? = null,
    val bingeGroup: String? = null,
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val proxyHeaders: ProxyHeadersDto? = null
)

/** Mirrors `behaviorHints.proxyHeaders` in the Stremio spec. */
data class ProxyHeadersDto(
    /** Headers to attach to every segment/manifest request (e.g. Authorization, Referer). */
    val request: Map<String, String>? = null,
    /** Headers returned by the upstream server — typically not needed by the player. */
    val response: Map<String, String>? = null
)

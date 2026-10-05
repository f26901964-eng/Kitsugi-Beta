package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.kitsugi.animelist.core.network.IPv4FirstDns
import java.io.IOException
import java.util.concurrent.TimeUnit

class DebridResolver(private val context: Context) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val RD_BASE = "https://api.real-debrid.com/rest/1.0"
    }

    private val sharedPrefs by lazy {
        context.applicationContext.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE)
    }

    fun getApiKey(): String? {
        return sharedPrefs.getString("debrid_api_key", null)
    }

    fun setApiKey(key: String?) {
        sharedPrefs.edit().putString("debrid_api_key", key).apply()
    }

    /**
     * Resolves a torrent infoHash into a direct streaming link via RealDebrid.
     */
    suspend fun resolveHash(
        infoHash: String,
        fileIndex: Int?,
        title: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()?.trim()
        if (apiKey.isNullOrBlank()) {
            Log.w("DebridResolver", "RealDebrid API key is not set.")
            return@withContext null
        }

        val canonicalHash = infoHash.trim().lowercase()
        var addedTorrentId: String? = null
        var shouldDeleteOnFail = false

        try {
            // Step 1: Add Magnet to RealDebrid
            val magnetUrl = "magnet:?xt=urn:btih:$canonicalHash"
            val addRequest = Request.Builder()
                .url("$RD_BASE/torrents/addMagnet")
                .post(FormBody.Builder().add("magnet", magnetUrl).build())
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val torrentId = client.newCall(addRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("DebridResolver", "addMagnet failed: ${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val result = gson.fromJson(bodyStr, Map::class.java)
                result["id"]?.toString()
            } ?: return@withContext null

            addedTorrentId = torrentId
            shouldDeleteOnFail = true

            // Step 2: Poll torrents/info until files are parsed
            var files: List<*>? = null
            for (attempt in 0..4) {
                val infoRequest = Request.Builder()
                    .url("$RD_BASE/torrents/info/$torrentId")
                    .get()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val infoMap = client.newCall(infoRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use null
                        gson.fromJson(bodyStr, Map::class.java)
                    } else null
                }

                val currentFiles = infoMap?.get("files") as? List<*>
                if (!currentFiles.isNullOrEmpty()) {
                    files = currentFiles
                    break
                }
                kotlinx.coroutines.delay(800L)
            }

            if (files.isNullOrEmpty()) {
                Log.w("DebridResolver", "Torrent files list is empty after polling")
                return@withContext null
            }

            // Step 3: Select the appropriate video file
            val selectedFileId = pickVideoFile(files, fileIndex, title)
            val fileSelectStr = selectedFileId ?: "all"

            val selectRequest = Request.Builder()
                .url("$RD_BASE/torrents/selectFiles/$torrentId")
                .post(FormBody.Builder().add("files", fileSelectStr).build())
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(selectRequest).execute().use { response ->
                if (!response.isSuccessful && response.code != 204) {
                    Log.w("DebridResolver", "selectFiles response code: ${response.code}")
                }
            }

            // Step 4: Poll torrents/info until status is 'downloaded' and links are populated
            var rdLink: String? = null
            for (attempt in 0..5) {
                kotlinx.coroutines.delay(1200L)

                val infoRequest = Request.Builder()
                    .url("$RD_BASE/torrents/info/$torrentId")
                    .get()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val infoMap = client.newCall(infoRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use null
                        gson.fromJson(bodyStr, Map::class.java)
                    } else null
                } ?: continue

                val status = infoMap["status"]?.toString()
                val links = infoMap["links"] as? List<*>

                if (status == "downloaded" && !links.isNullOrEmpty()) {
                    // If a specific file was selected, RealDebrid usually provides 1 link corresponding to it
                    rdLink = links.firstOrNull()?.toString()
                    break
                } else if (status in listOf("magnet_error", "error", "dead")) {
                    Log.w("DebridResolver", "Torrent status is error: $status")
                    break
                }
            }

            if (rdLink.isNullOrBlank()) {
                Log.w("DebridResolver", "RealDebrid did not provide a download link within timeout (not cached).")
                return@withContext null
            }

            // Step 5: Unrestrict the link to get direct download/streaming URL
            val unrestrictRequest = Request.Builder()
                .url("$RD_BASE/unrestrict/link")
                .post(FormBody.Builder().add("link", rdLink).build())
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val downloadUrl = client.newCall(unrestrictRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("DebridResolver", "unrestrict/link failed: ${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val unrestrictMap = gson.fromJson(bodyStr, Map::class.java) ?: return@withContext null
                unrestrictMap["download"]?.toString()
            }

            if (!downloadUrl.isNullOrBlank()) {
                shouldDeleteOnFail = false // Success!
                return@withContext downloadUrl
            }
            null
        } catch (e: Exception) {
            Log.e("DebridResolver", "Exception during debrid resolution", e)
            null
        } finally {
            if (shouldDeleteOnFail && addedTorrentId != null) {
                // Delete failed/uncached torrent from RealDebrid account to avoid cluttering
                try {
                    val deleteRequest = Request.Builder()
                        .url("$RD_BASE/torrents/delete/$addedTorrentId")
                        .delete()
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()
                    client.newCall(deleteRequest).execute().close()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun resolveHash(infoHash: String, fileIndex: Int?): String? =
        resolveHash(infoHash, fileIndex, null)

    /**
     * Picks the best playable video file from RealDebrid's file list.
     */
    private fun pickVideoFile(files: List<*>, fileIndex: Int?, searchTitle: String? = null): String? {
        val videoExtensions = setOf("mkv", "mp4", "avi", "m2ts", "ts", "mov", "webm", "ogm", "mpg", "mpeg")
        val skipPatterns = listOf("sample", "subs", "subtitle", ".nfo", ".jpg", ".png", ".srt")

        data class Candidate(val id: String, val path: String, val bytes: Long, val originalIndex: Int)
        val candidates = files.mapIndexedNotNull { idx, raw ->
            val file = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            val id = file["id"]?.toString() ?: return@mapIndexedNotNull null
            val path = (file["path"] as? String).orEmpty()
            val bytes = (file["bytes"] as? Number)?.toLong() ?: 0L
            Candidate(id, path, bytes, idx)
        }

        if (candidates.isEmpty()) return null

        // 1. If fileIndex provided, try to match by 0-based index or 1-based id
        if (fileIndex != null) {
            val byOriginalIndex = candidates.getOrNull(fileIndex)
            if (byOriginalIndex != null) {
                val ext = byOriginalIndex.path.substringAfterLast('.', "").lowercase()
                if (ext in videoExtensions) return byOriginalIndex.id
            }
            val byId = candidates.firstOrNull { it.id == fileIndex.toString() || it.id == (fileIndex + 1).toString() }
            if (byId != null) {
                val ext = byId.path.substringAfterLast('.', "").lowercase()
                if (ext in videoExtensions) return byId.id
            }
        }

        // 2. Filter video candidates without skip patterns
        val videoCandidates = candidates
            .filter { candidate ->
                val ext = candidate.path.substringAfterLast('.', "").lowercase()
                ext in videoExtensions
            }
            .filter { candidate ->
                val lower = candidate.path.lowercase()
                skipPatterns.none { lower.contains(it) }
            }

        if (videoCandidates.isEmpty()) {
            return candidates.firstOrNull {
                val ext = it.path.substringAfterLast('.', "").lowercase()
                ext in videoExtensions
            }?.id
        }

        // 3. If multiple and title has episode markers like "S01E01" or "01", try to match
        if (searchTitle != null) {
            val epRegex = Regex("""(?<!\d)(?:s\d{1,2}e(\d{1,3})|ep?\.?\s*(\d{1,3})|(\d{1,3}))(?!\d)""", RegexOption.IGNORE_CASE)
            val titleMatch = epRegex.find(searchTitle)
            if (titleMatch != null) {
                val epNum = titleMatch.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                if (epNum != null) {
                    val padded = epNum.padStart(2, '0')
                    val match = videoCandidates.firstOrNull { c ->
                        val filename = c.path.substringAfterLast('/')
                        filename.contains("e$padded", ignoreCase = true) ||
                        filename.contains("e$epNum", ignoreCase = true) ||
                        filename.contains(" $padded ", ignoreCase = true) ||
                        filename.contains("-$padded", ignoreCase = true) ||
                        filename.contains("[$padded", ignoreCase = true) ||
                        filename.contains(" $epNum ", ignoreCase = true)
                    }
                    if (match != null) return match.id
                }
            }
        }

        // 4. Return largest video candidate (usually the main movie or episode)
        return videoCandidates.maxByOrNull { it.bytes }?.id
    }
}

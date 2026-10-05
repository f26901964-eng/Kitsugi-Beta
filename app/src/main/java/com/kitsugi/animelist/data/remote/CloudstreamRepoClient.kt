package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Data classes mirroring the Cloudstream repository JSON format.
 */
data class CsRepository(
    val name: String,
    val description: String?,
    val pluginLists: List<String>
)

data class CsPlugin(
    val name: String,
    val internalName: String,
    /** Direct manifest URL or plugin download URL. For Stremio addons this IS the manifest URL. */
    val url: String,
    val description: String?,
    val version: Int,
    val language: String?,
    val tvTypes: List<String>?,
    val iconUrl: String?,
    val authors: List<String>,
    /** Plugin availability status from repo JSON: 0=Down/Disabled, 1=OK, 2=Beta/Slow, 3=Broken (filtered out before reaching UI) */
    val status: Int = 1,
    /** SHA-256 hash of the .cs3 file for integrity verification (optional, provided by repo). */
    val fileHash: String? = null,
    /** File size in bytes, for display purposes (optional, provided by repo). */
    val fileSize: Long? = null,
    val repositoryUrl: String? = null
)

/**
 * Network client that fetches Cloudstream-compatible repository manifests
 * and plugin lists. The repo JSON format is:
 *
 * ```json
 * {
 *   "name": "My Repo",
 *   "description": "...",
 *   "manifestVersion": 1,
 *   "pluginLists": ["https://raw.githubusercontent.com/.../plugins.json"]
 * }
 * ```
 *
 * Each plugin list URL returns an array of plugin objects:
 *
 * ```json
 * [
 *   {
 *     "name": "Addon Name",
 *     "internalName": "AddonProvider",
 *     "url": "https://example.com/manifest.json",
 *     "version": 1,
 *     "status": 1,
 *     "apiVersion": 1,
 *     "description": "...",
 *     "language": "tr",
 *     "tvTypes": ["Anime", "AnimeMovie"],
 *     "authors": ["author"],
 *     "iconUrl": null
 *   }
 * ]
 * ```
 *
 * Note: For KitsugiAnimeList, the "url" field should be a Stremio manifest URL.
 * Cloudstream .cs3 binary plugins are NOT supported — only Stremio HTTP addons.
 */
class CloudstreamRepoClient(private val context: android.content.Context? = null) {

    private val client: OkHttpClient
        get() = com.kitsugi.animelist.core.network.KitsugiHttpClient.client

    companion object {
        private const val TAG = "CloudstreamRepoClient"
        private const val CODEBERG_API_REPO_FALLBACK = "https://codeberg.org/api/v1/repos/BlackDamage/KitsugiPlugins/raw/builds/repo.json"
        private const val CODEBERG_API_PLUGINS_FALLBACK = "https://codeberg.org/api/v1/repos/BlackDamage/KitsugiPlugins/raw/builds/plugins.json"
    }

    fun fetchRepoFromAssets(assetPath: String = "plugins/repo.json"): CsRepository? {
        val ctx = context ?: return null
        return runCatching {
            val jsonStr = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val name = json.optString("name", "⚡ Kitsugi Eklentileri (Önerilen)")
            val description = json.optString("description").takeIf { it.isNotBlank() }
            val pluginLists = mutableListOf<String>()
            val arr = json.optJSONArray("pluginLists")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val url = arr.optString(i)
                    if (url.isNotBlank()) pluginLists.add(url)
                }
            }
            CsRepository(name = name, description = description, pluginLists = pluginLists)
        }.getOrNull()
    }

    fun fetchPluginsFromAssets(assetPath: String = "plugins/plugins.json"): List<CsPlugin> {
        val ctx = context ?: return emptyList()
        return runCatching {
            val jsonStr = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            parsePluginsJson(jsonStr, false)
        }.getOrDefault(emptyList())
    }

    suspend fun fetchRepo(repoUrl: String, useGithubProxy: Boolean = false, forceRefresh: Boolean = false): CsRepository? = withContext(Dispatchers.IO) {
        val normalizedUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(repoUrl, useGithubProxy)
        val finalUrl = if (forceRefresh) com.kitsugi.animelist.utils.CloudstreamUrlHelper.withCacheBuster(normalizedUrl) else normalizedUrl
        try {
            val requestBuilder = Request.Builder()
                .url(finalUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Kitsugi/2.4")
                .addHeader("Accept", "application/json, text/plain, */*")

            if (forceRefresh) {
                requestBuilder.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                requestBuilder.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                requestBuilder.addHeader("Pragma", "no-cache")
            }

            val request = requestBuilder.build()

            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchRepo failed (${response.code}): $finalUrl")
                    null
                } else {
                    response.body?.string()
                }
            }

            if (responseBody == null) {
                if (!useGithubProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                    Log.i(TAG, "Direct GitHub fetch failed for $repoUrl, automatically retrying with jsDelivr GitHub proxy...")
                    return@withContext fetchRepo(repoUrl, useGithubProxy = true, forceRefresh = forceRefresh)
                }
                if (repoUrl.contains("codeberg.org", ignoreCase = true) || repoUrl.contains("KitsugiPlugins", ignoreCase = true) || repoUrl.contains("Kitsugi-Plugins", ignoreCase = true)) {
                    Log.i(TAG, "Primary Codeberg fetch failed, trying Codeberg API fallback endpoint...")
                    val apiResult = runCatching {
                        val apiReq = Request.Builder().url(CODEBERG_API_REPO_FALLBACK).build()
                        client.newCall(apiReq).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
                    }.getOrNull()
                    if (apiResult != null) {
                        return@withContext parseRepoJson(apiResult, finalUrl, useGithubProxy)
                    }
                    Log.i(TAG, "Codeberg remote endpoints failed, falling back to bundled assets repo...")
                    return@withContext fetchRepoFromAssets()
                }
                return@withContext null
            }

            parseRepoJson(responseBody, finalUrl, useGithubProxy)
        } catch (e: Exception) {
            Log.e(TAG, "fetchRepo exception for $finalUrl", e)
            if (!useGithubProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                Log.i(TAG, "Direct GitHub fetch exception for $repoUrl, automatically retrying with jsDelivr proxy...")
                return@withContext fetchRepo(repoUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            if (repoUrl.contains("codeberg.org", ignoreCase = true) || repoUrl.contains("KitsugiPlugins", ignoreCase = true) || repoUrl.contains("Kitsugi-Plugins", ignoreCase = true)) {
                return@withContext fetchRepoFromAssets()
            }
            null
        }
    }

    private fun parseRepoJson(responseBody: String, requestUrl: String, useGithubProxy: Boolean): CsRepository {
        val trimmed = responseBody.trim()
        if (trimmed.startsWith("[")) {
            // Direct plugins.json URL (e.g. Kraptor, Hexated, or direct plugins array)
            val repoName = deriveRepoNameFromUrl(requestUrl)
            return CsRepository(
                name = repoName,
                description = "Eklenti Deposu ($repoName)",
                pluginLists = listOf(requestUrl)
            )
        }
        val json = org.json.JSONObject(responseBody)
        val name = json.optString("name", deriveRepoNameFromUrl(requestUrl))
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val pluginLists = mutableListOf<String>()
        val arr = json.optJSONArray("pluginLists")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val url = arr.optString(i)
                if (url.isNotBlank()) {
                    pluginLists.add(com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(url, useGithubProxy))
                }
            }
        }
        return CsRepository(name = name, description = description, pluginLists = pluginLists)
    }

    private fun deriveRepoNameFromUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#").removeSuffix("/")
        val parts = clean.split("/")
        val nameCandidate = parts.findLast { it.isNotBlank() && !it.endsWith(".json") && !it.equals("builds", true) && !it.equals("master", true) && !it.equals("main", true) }
        return nameCandidate?.replace("-", " ")?.replace("_", " ")?.capitalize(java.util.Locale.ROOT) ?: "Eklenti Havuzu"
    }

    suspend fun fetchPlugins(pluginListUrl: String, useGithubProxy: Boolean = false, forceRefresh: Boolean = false): List<CsPlugin> = withContext(Dispatchers.IO) {
        val normalizedUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(pluginListUrl, useGithubProxy)
        val finalUrl = if (forceRefresh) com.kitsugi.animelist.utils.CloudstreamUrlHelper.withCacheBuster(normalizedUrl) else normalizedUrl
        try {
            val requestBuilder = Request.Builder()
                .url(finalUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Kitsugi/2.4")
                .addHeader("Accept", "application/json, text/plain, */*")

            if (forceRefresh) {
                requestBuilder.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                requestBuilder.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                requestBuilder.addHeader("Pragma", "no-cache")
            }

            val request = requestBuilder.build()

            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchPlugins failed (${response.code}): $finalUrl")
                    null
                } else {
                    response.body?.string()
                }
            }

            if (responseBody == null) {
                if (!useGithubProxy && pluginListUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                    Log.i(TAG, "Direct GitHub pluginList fetch failed for $pluginListUrl, retrying with jsDelivr proxy...")
                    return@withContext fetchPlugins(pluginListUrl, useGithubProxy = true, forceRefresh = forceRefresh)
                }
                if (pluginListUrl.contains("codeberg.org", ignoreCase = true) || pluginListUrl.contains("KitsugiPlugins", ignoreCase = true)) {
                    Log.i(TAG, "Primary Codeberg pluginList fetch failed, trying Codeberg API fallback endpoint...")
                    val apiResult = runCatching {
                        val apiReq = Request.Builder().url(CODEBERG_API_PLUGINS_FALLBACK).build()
                        client.newCall(apiReq).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
                    }.getOrNull()
                    if (apiResult != null) {
                        return@withContext parsePluginsJson(apiResult, useGithubProxy)
                    }
                    Log.i(TAG, "Codeberg remote plugins endpoints failed, falling back to bundled assets plugins...")
                    return@withContext fetchPluginsFromAssets()
                }
                return@withContext emptyList()
            }

            parsePluginsJson(responseBody, useGithubProxy)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPlugins exception for $finalUrl", e)
            if (!useGithubProxy && pluginListUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                Log.i(TAG, "Direct GitHub pluginList exception for $pluginListUrl, retrying with jsDelivr proxy...")
                return@withContext fetchPlugins(pluginListUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            if (pluginListUrl.contains("codeberg.org", ignoreCase = true) || pluginListUrl.contains("KitsugiPlugins", ignoreCase = true)) {
                return@withContext fetchPluginsFromAssets()
            }
            emptyList()
        }
    }

    private fun parsePluginsJson(responseBody: String, useGithubProxy: Boolean): List<CsPlugin> {
        val arr = org.json.JSONArray(responseBody)
        val plugins = mutableListOf<CsPlugin>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawUrl = obj.optString("url", "")
            val name = obj.optString("name", "")
            if (rawUrl.isBlank() || name.isBlank()) continue

            val url = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(rawUrl, useGithubProxy)

            val status = obj.optInt("status", 1)
            if (status == 3) {
                Log.d(TAG, "Skipping broken/deprecated plugin: $name (status=$status)")
                continue
            }

            val tvTypesArr = obj.optJSONArray("tvTypes")
            val tvTypes = if (tvTypesArr != null) {
                (0 until tvTypesArr.length()).mapNotNull { tvTypesArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else null

            // +18 / Adult / NSFW eklentileri genel eklenti havuzuna asla ekleme
            val isAdult = com.kitsugi.animelist.data.cloudstream.CsStreamRunner.ADULT_PLUGINS_SET.any { it.equals(name, ignoreCase = true) } ||
                tvTypes?.any { it.equals("NSFW", ignoreCase = true) || it.equals("Adult", ignoreCase = true) } == true
            if (isAdult) {
                Log.d(TAG, "Skipping adult/NSFW plugin from catalog: $name")
                continue
            }

            val authorsArr = obj.optJSONArray("authors")
            val authors = if (authorsArr != null) {
                (0 until authorsArr.length()).mapNotNull { authorsArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else emptyList()

            val fileHash = obj.optString("fileHash").takeIf { it.isNotBlank() }
            val fileSize = if (obj.has("fileSize")) obj.optLong("fileSize") else null

            plugins.add(
                CsPlugin(
                    name = name,
                    internalName = obj.optString("internalName", name),
                    url = url,
                    description = obj.optString("description").takeIf { it.isNotBlank() },
                    version = obj.optInt("version", 1),
                    language = obj.optString("language").takeIf { it.isNotBlank() },
                    tvTypes = tvTypes,
                    iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
                    authors = authors,
                    status = status,
                    fileHash = fileHash,
                    fileSize = fileSize,
                    repositoryUrl = obj.optString("repositoryUrl").takeIf { it.isNotBlank() }
                )
            )
        }
        return plugins
    }

    /**
     * Fetches the repo manifest and then all plugin lists it references.
     * Returns all plugins in a flat list.
     */
    suspend fun fetchAllPlugins(repoUrl: String, useGithubProxy: Boolean = false, forceRefresh: Boolean = false): List<CsPlugin>? {
        val normalizedRepoUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(repoUrl, useGithubProxy)
        
        // Doğrudan plugins.json URL'si girilmişse manifest aramadan direkt eklentileri çek
        if (normalizedRepoUrl.endsWith("plugins.json", ignoreCase = true)) {
            var directPlugins = fetchPlugins(normalizedRepoUrl, useGithubProxy, forceRefresh)
            if (directPlugins.isEmpty() && !useGithubProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                directPlugins = fetchPlugins(repoUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            if (directPlugins.isNotEmpty()) {
                return directPlugins.map { it.copy(repositoryUrl = it.repositoryUrl ?: normalizedRepoUrl) }
            }
        }

        var repo = fetchRepo(normalizedRepoUrl, useGithubProxy, forceRefresh)
        if (repo == null && !useGithubProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
            repo = fetchRepo(repoUrl, useGithubProxy = true, forceRefresh = forceRefresh)
        }
        if (repo == null && (normalizedRepoUrl.contains("KitsugiPlugins", ignoreCase = true) || normalizedRepoUrl.contains("Kitsugi-Plugins", ignoreCase = true) || normalizedRepoUrl.contains("codeberg.org", ignoreCase = true))) {
            repo = fetchRepoFromAssets()
        }
        if (repo == null) return null
        val all = mutableListOf<CsPlugin>()
        for (listUrl in repo.pluginLists) {
            var plugins = fetchPlugins(listUrl, useGithubProxy, forceRefresh)
            if (plugins.isEmpty() && !useGithubProxy && listUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                plugins = fetchPlugins(listUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            if (plugins.isEmpty() && (listUrl.contains("KitsugiPlugins", ignoreCase = true) || listUrl.contains("Kitsugi-Plugins", ignoreCase = true) || listUrl.contains("codeberg.org", ignoreCase = true))) {
                plugins = fetchPluginsFromAssets()
            }
            all.addAll(plugins.map { it.copy(repositoryUrl = it.repositoryUrl ?: normalizedRepoUrl) })
        }
        return all
    }
}

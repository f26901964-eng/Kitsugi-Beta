package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bir Manga Eklenti Reposundan cekilen eklentiyi temsil eder.
 * Mihon/Keiyoushi repo JSON formatiyla uyumludur (v1 ve v2).
 */
data class MangaExtensionInfo(
    val name: String,
    val pkg: String,
    val apkUrl: String,
    val lang: String,
    val version: String,
    val versionCode: Int = 0,
    val iconUrl: String? = null,
    val description: String? = null,
    val isNsfw: Boolean = false,
    val categories: List<String> = emptyList()
)

/**
 * MangaRepoClient
 *
 * Keiyoushi/Mihon uyumlu repo JSON'larindan eklenti listesi cekmek icin
 * kullanilan network istemcisi.
 *
 * Desteklenen formatlar:
 *
 * 1) Keiyoushi V2 formati (index.json, 2026+):
 *    { "extensionList": { "extensions": [ { "packageName": "...", "versionName": "...",
 *      "resources": { "apkUrl": "...", "iconUrl": "..." }, "sources": [...] } ] } }
 *
 * 2) Keiyoushi V1 formati (eski, artik decoy/sahte -- sadece fallback):
 *    [ { "name": "...", "pkg": "...", "apk": "...", "lang": "tr", "code": 14, ... } ]
 *
 * 3) Basit duz APK liste formati (plugins.json - Kitsugi native):
 *    [{ "name": "...", "lang": "tr", "url": "https://.../plugin.apk", "version": "1.0" }]
 */
class MangaRepoClient(context: android.content.Context) {

    companion object {
        private const val TAG = "MangaRepoClient"

        // Keiyoushi V2 Repo URL'leri (2026+)
        const val KEIYOUSHI_REPO_JSON_URL =
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/repo.json"

        /** Keiyoushi V2 ana index (JSON, 1396 eklenti, 1.45 MB). index.min.json DECOY'dur -- bunu kullanin. */
        const val KEIYOUSHI_INDEX_URL =
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json"

        const val KEIYOUSHI_INDEX_PB_URL =
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"

        @Deprecated("V2'de index.json kullanin -- index.min.json artik decoy")
        const val KEIYOUSHI_INDEX_MIN_URL =
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"

        const val KEIYOUSHI_APK_BASE_URL =
            "https://github.com/keiyoushi/extensions/releases/download/"

        const val KEIYOUSHI_RELEASE_BASE =
            "https://github.com/keiyoushi/extensions/releases/latest/download/"

        const val KOTATSU_COMMIT_API =
            "https://api.github.com/repos/Kotatsu-Redo/kotatsu-parsers-redo/commits/master"

        const val KOTATSU_RELEASES_URL =
            "https://github.com/Kotatsu-Redo/kotatsu-parsers-redo/releases"

        // Decoy tespiti icin minimum icerik boyutu (765 bayt < THRESHOLD < gercek index)
        private const val DECOY_THRESHOLD_BYTES = 5_000

        private val DECOY_MARKERS = listOf(
            "Outdated App",
            "Update to Mihon",
            "Update to Tachiyomi",
        )

        /**
         * raw.githubusercontent.com adresini raw.github.com ile degistirir.
         * Turkiye'deki ISP engellerini (Turk Telekom vb.) asmak icin kullanilir.
         */
        fun applyMirror(url: String): String {
            if (url.startsWith("https://raw.githubusercontent.com/")) {
                return url.replace("raw.githubusercontent.com", "raw.github.com")
            }
            return url
        }
    }

    private val client = uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java).client

    /**
     * Keiyoushi V2 index.json formatini ayristirir.
     * V2 sema (2026+): { "extensionList": { "extensions": [...] } }
     */
    private fun parseKeiyoushiV2Index(json: String): List<MangaExtensionInfo> {
        val out = mutableListOf<MangaExtensionInfo>()
        try {
            val root = JSONObject(json)
            val extensionList = root.optJSONObject("extensionList") ?: run {
                Log.w(TAG, "parseKeiyoushiV2Index: 'extensionList' alani bulunamadi")
                return emptyList()
            }
            val arr = extensionList.optJSONArray("extensions") ?: run {
                Log.w(TAG, "parseKeiyoushiV2Index: 'extensions' array bulunamadi")
                return emptyList()
            }

            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val pkg = e.optString("packageName", "").trim()
                if (pkg.isBlank()) continue

                val res = e.optJSONObject("resources")
                val apkUrl = res?.optString("apkUrl", "")?.trim() ?: ""
                if (apkUrl.isBlank()) continue

                val iconUrl = res?.optString("iconUrl", "")?.trim()?.ifBlank { null }

                val sourcesArr = e.optJSONArray("sources")
                val primarySource = sourcesArr?.optJSONObject(0)
                val lang = (primarySource?.optString("language", "")?.trim() ?: "").let { l ->
                    if (l.isBlank()) {
                        if (".tr." in pkg || pkg.endsWith(".tr")) "tr" else "all"
                    } else l
                }

                // versionCode V2'de string olarak geliyor
                val versionCode = e.optString("versionCode", "0").toIntOrNull()
                    ?: e.optInt("versionCode", 0)

                val isNsfw = e.optString("contentWarning", "") == "CONTENT_WARNING_NSFW"

                val categories = mutableListOf<String>()
                if (sourcesArr != null) {
                    val seen = mutableSetOf<String>()
                    for (j in 0 until sourcesArr.length()) {
                        val src = sourcesArr.optJSONObject(j) ?: continue
                        val type = src.optString("type", "").trim()
                        if (type.isNotBlank() && seen.add(type)) categories.add(type)
                    }
                }

                out.add(
                    MangaExtensionInfo(
                        name        = e.optString("name", pkg).trim(),
                        pkg         = pkg,
                        apkUrl      = apkUrl,
                        lang        = lang,
                        version     = e.optString("versionName", "1.0").trim(),
                        versionCode = versionCode,
                        iconUrl     = iconUrl,
                        isNsfw      = isNsfw,
                        categories  = categories,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseKeiyoushiV2Index ayristirma hatasi: ${e.message}", e)
        }
        Log.i(TAG, "V2 index: ${out.size} eklenti ayristirildi")
        return out
    }

    /**
     * Keiyoushi V1 (eski) index formatini ayristirir.
     * UYARI: index.min.json artik decoy -- bu fonksiyon ucuncu parti repo destegi icin korunuyor.
     */
    private fun parseKeiyoushiV1Index(json: String, apkBaseUrl: String): List<MangaExtensionInfo> {
        val result = mutableListOf<MangaExtensionInfo>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val pkg = obj.optString("pkg", "").trim()
                val lang = obj.optString("lang", "all").trim()
                val version = obj.optString("version", "1.0").trim()
                val versionCode = obj.optInt("code", 0)
                val isNsfw = obj.optInt("nsfw", 0) == 1

                if (name.isBlank() || pkg.isBlank()) continue

                val apkFileName = obj.optString("apk", "").trim().ifBlank { "$pkg.apk" }
                val apkUrl = if (apkFileName.startsWith("http")) {
                    apkFileName
                } else {
                    "${apkBaseUrl.trimEnd('/')}/$apkFileName"
                }

                val iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() }

                val categories = mutableListOf<String>()
                val sourcesArr = obj.optJSONArray("sources")
                if (sourcesArr != null) {
                    val seen = mutableSetOf<String>()
                    for (j in 0 until sourcesArr.length()) {
                        val src = sourcesArr.optJSONObject(j) ?: continue
                        val type = src.optString("type", "").trim()
                        if (type.isNotBlank() && seen.add(type)) categories.add(type)
                    }
                }

                result.add(
                    MangaExtensionInfo(
                        name = name, pkg = pkg, apkUrl = apkUrl, lang = lang,
                        version = version, versionCode = versionCode, iconUrl = iconUrl,
                        isNsfw = isNsfw, categories = categories
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseKeiyoushiV1Index ayristirma hatasi: ${e.message}", e)
        }
        Log.i(TAG, "V1 index: ${result.size} eklenti ayristirildi")
        return result
    }

    /** Basit Kitsugi native JSON listesini ayristirir. */
    private fun parseNativePluginList(json: String): List<MangaExtensionInfo> {
        val result = mutableListOf<MangaExtensionInfo>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val url = obj.optString("url", "").trim()
                if (name.isBlank() || url.isBlank()) continue
                result.add(
                    MangaExtensionInfo(
                        name = name,
                        pkg = obj.optString("pkg", name).trim(),
                        apkUrl = url,
                        lang = obj.optString("lang", "all").trim(),
                        version = obj.optString("version", "1.0").trim(),
                        iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseNativePluginList ayristirma hatasi: ${e.message}", e)
        }
        return result
    }

    /**
     * Verilen response body'nin Keiyoushi decoy (sahte) olup olmadigini kontrol eder.
     * index.min.json artik 765 baytlik decoy -- gercek veri index.json'da.
     */
    private fun isDecoyResponse(body: String): Boolean {
        if (body.length < DECOY_THRESHOLD_BYTES) return true
        return DECOY_MARKERS.any { marker -> body.contains(marker, ignoreCase = true) }
    }

    /**
     * Belirli bir URL'den akillica format tespiti yaparak eklenti listesi ceker.
     *
     * Oncelik sirasi:
     *   1) Keiyoushi V2 (index.json) -- kok nesne format  { "extensionList": ... }
     *   2) Decoy tespiti: kucukse / DECOY_MARKERS iceriyorsa -> V2 index.json'a otomatik gec
     *   3) Keiyoushi V1 (eski array format)
     *   4) Kitsugi native (plugins.json)
     */
    suspend fun fetchExtensionsAutoDetect(repoUrl: String): List<MangaExtensionInfo>? =
        withContext(Dispatchers.IO) {
            try {
                val mirroredUrl = applyMirror(repoUrl)
                Log.d(TAG, "fetchExtensionsAutoDetect cekiliyor: $mirroredUrl (original: $repoUrl)")
                val request = Request.Builder().url(mirroredUrl).build()
                val responseBody = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "fetchExtensionsAutoDetect basarisiz (${response.code}): $mirroredUrl")
                        return@withContext null
                    }
                    response.body?.string()
                } ?: return@withContext null

                val trimmed = responseBody.trim()

                // Keiyoushi V2: kok nesne ile baslar
                if (trimmed.startsWith("{")) {
                    Log.d(TAG, "V2 JSON formati tespit edildi")
                    return@withContext parseKeiyoushiV2Index(trimmed)
                }

                if (!trimmed.startsWith("[")) {
                    Log.e(TAG, "Desteklenmeyen format: $mirroredUrl")
                    return@withContext null
                }

                // Decoy tespiti: kucuk/sahte yanit varsa V2 index.json'u cek
                if (isDecoyResponse(trimmed)) {
                    Log.w(TAG, "DECOY TESPIT EDILDI: $mirroredUrl -- Keiyoushi V2 index.json'a geciliyor")
                    val v2Url = applyMirror(KEIYOUSHI_INDEX_URL)
                    val v2Request = Request.Builder().url(v2Url).build()
                    val v2Body = client.newCall(v2Request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e(TAG, "V2 index.json cekilemedi (${response.code}): $v2Url")
                            return@withContext null
                        }
                        response.body?.string()
                    } ?: return@withContext null

                    val v2Trimmed = v2Body.trim()
                    return@withContext if (v2Trimmed.startsWith("{")) {
                        parseKeiyoushiV2Index(v2Trimmed)
                    } else {
                        Log.e(TAG, "V2 fallback yaniti da beklenmeyen formatta")
                        null
                    }
                }

                // V1 array -- ilk elemana bakarak format belirle
                val arr = JSONArray(trimmed)
                if (arr.length() == 0) return@withContext emptyList()

                val firstObj = arr.optJSONObject(0)
                val isKeiyoushiV1 = firstObj?.has("pkg") == true

                if (isKeiyoushiV1) {
                    val baseUrl = deriveApkBaseUrl(mirroredUrl)
                    parseKeiyoushiV1Index(trimmed, baseUrl)
                } else {
                    parseNativePluginList(trimmed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchExtensionsAutoDetect hata: ${e.message}", e)
                null
            }
        }

    /**
     * Keiyoushi repo.json'dan meta bilgileri (signing key fingerprint, index URL) ceker.
     * Bu bilgiler imza guven yonetimi icin kullanilir.
     */
    suspend fun fetchRepoMeta(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = applyMirror(KEIYOUSHI_REPO_JSON_URL)
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "repo.json cekilemedi (${response.code})")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRepoMeta hata: ${e.message}")
            null
        }
    }

    /**
     * Verilen URL'den eklenti listesini ceker (basit metod, geriye donuk uyum).
     */
    suspend fun fetchExtensions(
        indexUrl: String,
        apkBaseUrl: String = KEIYOUSHI_RELEASE_BASE
    ): List<MangaExtensionInfo>? = withContext(Dispatchers.IO) {
        try {
            val mirroredUrl = applyMirror(indexUrl)
            val mirroredApkBase = applyMirror(apkBaseUrl)
            Log.d(TAG, "Manga repo cekiliyor: $mirroredUrl (original: $indexUrl)")
            val request = Request.Builder().url(mirroredUrl).build()
            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchExtensions basarisiz (${response.code}): $mirroredUrl")
                    return@withContext null
                }
                response.body?.string()
            } ?: return@withContext null

            val trimmed = responseBody.trim()
            when {
                trimmed.startsWith("{") -> parseKeiyoushiV2Index(trimmed)
                isDecoyResponse(trimmed) -> {
                    Log.w(TAG, "fetchExtensions: decoy tespit edildi, V2 index.json'a geciliyor")
                    fetchExtensionsAutoDetect(KEIYOUSHI_INDEX_URL)
                }
                else -> parseKeiyoushiV1Index(trimmed, mirroredApkBase)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchExtensions hata: ${e.message}", e)
            null
        }
    }

    /** Keiyoushi tarzi bir repo URL'sinden APK base URL'sini turetir (V1 icin). */
    private fun deriveApkBaseUrl(repoUrl: String): String {
        val baseDir = repoUrl.substringBeforeLast("/")
        return "$baseDir/apk/"
    }
}

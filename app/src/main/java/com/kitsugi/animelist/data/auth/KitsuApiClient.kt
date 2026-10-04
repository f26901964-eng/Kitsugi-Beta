package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kitsu.io JSON-API İstemcisi.
 * Kitsu OAuth token alma, kullanıcı profili çekme, kütüphane listesini okuma
 * ve kütüphane kayıtlarını ekleme/güncelleme/silme işlemlerini yönetir.
 */
object KitsuApiClient {
    private const val TAG = "KitsuApiClient"

    // Açık kaynak standart Kitsu OAuth kimlik bilgileri (Tachiyomi / Aniyomi referansı)
    const val DEFAULT_CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
    const val DEFAULT_CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

    private const val OAUTH_URL = "https://kitsu.app/api/oauth/token"
    private const val BASE_URL = "https://kitsu.app/api/edge"
    private val JSON_MEDIA_TYPE = "application/vnd.api+json".toMediaTypeOrNull()

    data class KitsuTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    data class KitsuUser(
        val id: String,
        val name: String,
        val avatarUrl: String?
    )

    data class KitsuLibraryEntry(
        val id: String,
        val status: String,
        val progress: Int,
        val ratingTwenty: Int?,
        val animeId: Int?,
        val mangaId: Int?,
        val updatedAt: Long,
        val title: String = "",
        val titleEnglish: String? = null,
        val imageUrl: String? = null,
        val total: Int? = null
    )

    /**
     * Kullanıcı adı / e-posta ve şifre ile Kitsu OAuth Bearer token alır.
     */
    suspend fun loginWithPassword(
        username: String,
        password: String,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET
    ): KitsuTokenResponse = withContext(Dispatchers.IO) {
        val formBody = okhttp3.FormBody.Builder()
            .add("grant_type", "password")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("username", username.trim())
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(OAUTH_URL)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .post(formBody)
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorType = runCatching { JSONObject(body).optString("error", "") }.getOrDefault("")
                val errorDesc = runCatching { JSONObject(body).optString("error_description", "") }.getOrDefault("")
                val localizedMsg = when {
                    errorType == "invalid_grant" || errorDesc.contains("authorization grant is invalid", ignoreCase = true) ->
                        "E-posta veya şifre hatalı. Lütfen bilgilerinizi kontrol edip tekrar deneyin."
                    errorType == "invalid_client" ->
                        "Kitsu sunucu bağlantı hatası. Lütfen daha sonra tekrar deneyin."
                    errorDesc.isNotBlank() -> errorDesc
                    else -> "Kitsu girişi başarısız oldu (${response.code})"
                }
                throw Exception(localizedMsg)
            }
            val json = JSONObject(body)
            KitsuTokenResponse(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", ""),
                expiresIn = json.optLong("expires_in", 2592000L)
            )
        }
    }

    /**
     * Mevcut oturum açmış kullanıcının kimlik ve profil bilgilerini alır.
     */
    suspend fun getCurrentUser(token: String): KitsuUser = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/users?filter[self]=true")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception("Kullanıcı bilgisi alınamadı (${response.code})")
            val data = JSONObject(body).getJSONArray("data")
            if (data.length() == 0) throw Exception("Kullanıcı bulunamadı")
            val userObj = data.getJSONObject(0)
            val userId = userObj.getString("id")
            val attrs = userObj.optJSONObject("attributes")
            val name = attrs?.optString("name", "Kitsu User") ?: "Kitsu User"
            val avatarObj = attrs?.optJSONObject("avatar")
            val avatarUrl = avatarObj?.optString("medium") ?: avatarObj?.optString("original")
            KitsuUser(id = userId, name = name, avatarUrl = avatarUrl)
        }
    }

    /**
     * Kitsu kullanıcısının tüm kütüphanesini sayfalayarak çeker.
     */
    suspend fun fetchAllLibraryEntries(token: String, userId: String): List<KitsuLibraryEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<KitsuLibraryEntry>()
        var offset = 0
        val limit = 500

        while (true) {
            val url = "$BASE_URL/library-entries?filter[userId]=$userId&page[limit]=$limit&page[offset]=$offset&include=anime,manga"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", "KitsugiApp/2.4")
                .get()
                .build()

            val fetchedCount = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use 0
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: JSONArray()

                val included = json.optJSONArray("included") ?: JSONArray()
                val mediaInfoMap = mutableMapOf<String, Triple<String, String?, String?>>() // key -> (title, titleEn, imageUrl)
                val totalMap = mutableMapOf<String, Int?>()

                for (j in 0 until included.length()) {
                    val inc = included.getJSONObject(j)
                    val incType = inc.optString("type")
                    val incId = inc.optString("id")
                    val incAttrs = inc.optJSONObject("attributes") ?: JSONObject()
                    val title = incAttrs.optString("canonicalTitle", "")
                    val titlesObj = incAttrs.optJSONObject("titles")
                    val titleEn = titlesObj?.optString("en")?.takeIf { it.isNotBlank() }
                        ?: titlesObj?.optString("en_jp")?.takeIf { it.isNotBlank() }
                    val poster = incAttrs.optJSONObject("posterImage")
                    val img = poster?.optString("medium") ?: poster?.optString("original")
                    val total = if (incType == "anime") incAttrs.optInt("episodeCount", 0).takeIf { it > 0 }
                    else incAttrs.optInt("chapterCount", 0).takeIf { it > 0 }

                    val key = "${incType}_$incId"
                    mediaInfoMap[key] = Triple(title, titleEn, img)
                    totalMap[key] = total
                }

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val entryId = item.getString("id")
                    val attrs = item.optJSONObject("attributes") ?: JSONObject()
                    val status = attrs.optString("status", "planned")
                    val progress = attrs.optInt("progress", 0)
                    val ratingTwenty = if (attrs.has("ratingTwenty") && !attrs.isNull("ratingTwenty")) attrs.optInt("ratingTwenty") else null
                    val updatedAtStr = attrs.optString("updatedAt", "")
                    val updatedAt = runCatching {
                        java.time.Instant.parse(updatedAtStr).epochSecond
                    }.getOrDefault(System.currentTimeMillis() / 1000L)

                    val rels = item.optJSONObject("relationships")
                    val animeData = rels?.optJSONObject("anime")?.optJSONObject("data")
                    val mangaData = rels?.optJSONObject("manga")?.optJSONObject("data")

                    val animeId = animeData?.optString("id")?.toIntOrNull()
                    val mangaId = mangaData?.optString("id")?.toIntOrNull()

                    val mediaKey = if (animeId != null) "anime_$animeId" else if (mangaId != null) "manga_$mangaId" else ""
                    val info = mediaInfoMap[mediaKey]

                    result.add(
                        KitsuLibraryEntry(
                            id = entryId,
                            status = status,
                            progress = progress,
                            ratingTwenty = ratingTwenty,
                            animeId = animeId,
                            mangaId = mangaId,
                            updatedAt = updatedAt,
                            title = info?.first ?: if (animeId != null) "Kitsu Anime #$animeId" else "Kitsu Manga #$mangaId",
                            titleEnglish = info?.second,
                            imageUrl = info?.third,
                            total = totalMap[mediaKey]
                        )
                    )
                }
                data.length()
            }

            if (fetchedCount < limit) break
            offset += limit
        }

        result
    }

    /**
     * Kitsu'da kütüphane kaydı oluşturur (POST).
     */
    suspend fun createLibraryEntry(
        token: String,
        userId: String,
        kitsuMediaId: Int,
        isAnime: Boolean,
        status: String,
        progress: Int,
        ratingTwenty: Int?
    ): String? = withContext(Dispatchers.IO) {
        val relType = if (isAnime) "anime" else "manga"
        val payload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("type", "libraryEntries")
                put("attributes", JSONObject().apply {
                    put("status", status)
                    put("progress", progress)
                    if (ratingTwenty != null && ratingTwenty > 0) {
                        put("ratingTwenty", ratingTwenty)
                    }
                })
                put("relationships", JSONObject().apply {
                    put("user", JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("type", "users")
                            put("id", userId)
                        })
                    })
                    put(relType, JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("type", relType)
                            put("id", kitsuMediaId.toString())
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/library-entries")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Content-Type", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                JSONObject(body).optJSONObject("data")?.optString("id")
            } else {
                Log.e(TAG, "createLibraryEntry failed: ${response.code} $body")
                null
            }
        }
    }

    /**
     * Mevcut bir Kitsu kütüphane kaydını günceller (PATCH).
     */
    suspend fun updateLibraryEntry(
        token: String,
        entryId: String,
        status: String,
        progress: Int,
        ratingTwenty: Int?
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("data", JSONObject().apply {
                put("id", entryId)
                put("type", "libraryEntries")
                put("attributes", JSONObject().apply {
                    put("status", status)
                    put("progress", progress)
                    if (ratingTwenty != null && ratingTwenty > 0) {
                        put("ratingTwenty", ratingTwenty)
                    } else {
                        put("ratingTwenty", JSONObject.NULL)
                    }
                })
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/library-entries/$entryId")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Content-Type", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    /**
     * Kitsu kütüphanesinden kaydı siler (DELETE).
     */
    suspend fun deleteLibraryEntry(token: String, entryId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/library-entries/$entryId")
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .delete()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    /**
     * Başlık veya MAL ID ile Kitsu medya numeric ID'sini arar.
     */
    suspend fun lookupKitsuId(title: String, isAnime: Boolean): Int? = withContext(Dispatchers.IO) {
        val endpoint = if (isAnime) "anime" else "manga"
        val encoded = java.net.URLEncoder.encode(title.trim(), "UTF-8")
        val url = "$BASE_URL/$endpoint?filter[text]=$encoded&page[limit]=1"

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.api+json")
            .addHeader("User-Agent", "KitsugiApp/2.4")
            .get()
            .build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val data = JSONObject(body).optJSONArray("data") ?: return@use null
                if (data.length() > 0) {
                    data.getJSONObject(0).optString("id").toIntOrNull()
                } else null
            }
        }.getOrNull()
    }
}

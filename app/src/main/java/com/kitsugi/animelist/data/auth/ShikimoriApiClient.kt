package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shikimori.one REST API istemcisi.
 * OAuth2 token yönetimi, kullanıcı profili ve kullanıcı izleme listesi (user_rates)
 * okuma, ekleme, güncelleme ve silme işlemlerini yürütür.
 */
object ShikimoriApiClient {
    private const val TAG = "ShikimoriApiClient"
    private const val BASE_URL = "https://shikimori.one"
    private const val USER_AGENT = "KitsugiApp/2.4 (Android)"
    private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()

    data class ShikimoriTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    data class ShikimoriUser(
        val id: Int,
        val nickname: String,
        val avatarUrl: String?
    )

    data class ShikimoriRate(
        val id: Int,
        val targetId: Int, // MAL ID ile aynı
        val targetType: String, // "Anime" veya "Manga"
        val status: String,
        val score: Int,
        val episodes: Int,
        val chapters: Int,
        val updatedAt: Long,
        val title: String = "",
        val imageUrl: String? = null,
        val total: Int? = null
    )

    const val DEFAULT_CLIENT_ID = "aOAYRqOLwxpA8skpcQIXetNy4cw2rn2fRzScawlcQ5U"
    const val DEFAULT_CLIENT_SECRET = "jqjmORn6bh2046ulkm4lHEwJ3OA1RmO3FD2sR9f6Clw"
    const val DEFAULT_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"

    /**
     * OAuth2 yetkilendirme URL'sini üretir.
     */
    fun buildAuthorizeUrl(clientId: String = DEFAULT_CLIENT_ID, redirectUri: String = DEFAULT_REDIRECT_URI): String {
        return "$BASE_URL/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=user_rates"
    }

    /**
     * OAuth yetki kodunu (authorization code) access token ile takas eder.
     */
    suspend fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String = "urn:ietf:wg:oauth:2.0:oob"
    ): ShikimoriTokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code.trim())
            .add("redirect_uri", redirectUri)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/oauth/token")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("Shikimori token alınamadı (${response.code}): $body")
            }
            val json = JSONObject(body)
            ShikimoriTokenResponse(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", ""),
                expiresIn = json.optLong("expires_in", 2592000L)
            )
        }
    }

    /**
     * Refresh token ile token yeniler.
     */
    suspend fun refreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): ShikimoriTokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/oauth/token")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("Shikimori token yenilenemedi (${response.code}): $body")
            }
            val json = JSONObject(body)
            ShikimoriTokenResponse(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", refreshToken),
                expiresIn = json.optLong("expires_in", 2592000L)
            )
        }
    }

    /**
     * Giriş yapmış kullanıcının profilini alır (/api/users/whoami).
     */
    suspend fun getCurrentUser(token: String): ShikimoriUser = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/users/whoami")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("Shikimori profil bilgisi alınamadı (${response.code})")
            }
            val json = JSONObject(body)
            ShikimoriUser(
                id = json.getInt("id"),
                nickname = json.optString("nickname", "Shikimori User"),
                avatarUrl = json.optString("avatar").takeIf { it.isNotBlank() }
            )
        }
    }

    /**
     * Kullanıcının tüm Anime ve Manga liste kayıtlarını çeker.
     */
    suspend fun fetchAllUserRates(token: String, userId: Int): List<ShikimoriRate> = withContext(Dispatchers.IO) {
        val rates = mutableListOf<ShikimoriRate>()
        val endpoints = listOf("anime_rates" to "Anime", "manga_rates" to "Manga")

        for ((ep, targetType) in endpoints) {
            var page = 1
            val limit = 500
            while (true) {
                val url = "$BASE_URL/api/users/$userId/$ep?page=$page&limit=$limit"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val count = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use 0
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val rateId = item.getInt("id")
                        val status = item.optString("status", "planned")
                        val score = item.optInt("score", 0)
                        val episodes = item.optInt("episodes", 0)
                        val chapters = item.optInt("chapters", 0)
                        val updatedAtStr = item.optString("updated_at", "")
                        val updatedAt = runCatching {
                            java.time.Instant.parse(updatedAtStr).epochSecond
                        }.getOrDefault(System.currentTimeMillis() / 1000L)

                        val mediaObj = if (targetType == "Anime") item.optJSONObject("anime") else item.optJSONObject("manga")
                        val targetId = mediaObj?.optInt("id", 0)?.takeIf { it > 0 } ?: item.optInt("target_id", 0)
                        if (targetId <= 0) continue

                        val title = mediaObj?.optString("name") ?: "Shikimori #$targetId"
                        val imageObj = mediaObj?.optJSONObject("image")
                        val rawImg = imageObj?.optString("original") ?: imageObj?.optString("preview")
                        val imgUrl = if (!rawImg.isNullOrBlank()) {
                            if (rawImg.startsWith("http")) rawImg else "$BASE_URL$rawImg"
                        } else null
                        val total = if (targetType == "Anime") mediaObj?.optInt("episodes", 0)?.takeIf { it > 0 }
                        else mediaObj?.optInt("chapters", 0)?.takeIf { it > 0 }

                        rates.add(
                            ShikimoriRate(
                                id = rateId,
                                targetId = targetId,
                                targetType = targetType,
                                status = status,
                                score = score,
                                episodes = episodes,
                                chapters = chapters,
                                updatedAt = updatedAt,
                                title = title,
                                imageUrl = imgUrl,
                                total = total
                            )
                        )
                    }
                    array.length()
                }

                if (count < limit) break
                page++
            }
        }
        rates
    }

    /**
     * Shikimori'de yeni bir liste kaydı oluşturur (POST).
     */
    suspend fun createUserRate(
        token: String,
        userId: Int,
        targetId: Int, // MAL ID
        targetType: String, // "Anime" veya "Manga"
        status: String,
        score: Int,
        progress: Int
    ): Int? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("user_rate", JSONObject().apply {
                put("user_id", userId)
                put("target_id", targetId)
                put("target_type", targetType)
                put("status", status)
                put("score", score.coerceIn(0, 10))
                if (targetType == "Anime") put("episodes", progress)
                else put("chapters", progress)
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/v2/user_rates")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                JSONObject(body).optInt("id", 0).takeIf { it > 0 }
            } else {
                Log.e(TAG, "createUserRate failed: ${response.code} $body")
                null
            }
        }
    }

    /**
     * Mevcut bir Shikimori liste kaydını günceller (PATCH).
     */
    suspend fun updateUserRate(
        token: String,
        rateId: Int,
        targetType: String,
        status: String,
        score: Int,
        progress: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("user_rate", JSONObject().apply {
                put("status", status)
                put("score", score.coerceIn(0, 10))
                if (targetType == "Anime") put("episodes", progress)
                else put("chapters", progress)
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/v2/user_rates/$rateId")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .patch(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    /**
     * Shikimori liste kaydını siler (DELETE).
     */
    suspend fun deleteUserRate(token: String, rateId: Int): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v2/user_rates/$rateId")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }
}

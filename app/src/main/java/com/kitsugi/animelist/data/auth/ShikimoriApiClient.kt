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

    data class ShikimoriFullProfile(
        val id: Int,
        val nickname: String,
        val avatarUrl: String?,
        val location: String?,
        val lastOnline: String?,
        val bio: String?,
        val website: String?,
        val sex: String?,
        val fullYears: Int?,
        val watchingAnime: Int = 0,
        val completedAnime: Int = 0,
        val onHoldAnime: Int = 0,
        val droppedAnime: Int = 0,
        val plannedAnime: Int = 0,
        val readingManga: Int = 0,
        val completedManga: Int = 0,
        val onHoldManga: Int = 0,
        val droppedManga: Int = 0,
        val plannedManga: Int = 0,
        val animeScoreDist: Map<Int, Int> = emptyMap(),
        val mangaScoreDist: Map<Int, Int> = emptyMap()
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

    const val DEFAULT_CLIENT_ID = "poB5DHHfiPP-DiphGJoelAnUeQ3PNkhPXwuUgXusl20"
    const val DEFAULT_CLIENT_SECRET = "ZkmIi8ysb-lDe1RRewUTeEN46Ef6iziPcpJPAnbsAEs"
    const val DEFAULT_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    const val DEEP_LINK_REDIRECT_URI = "kitsugi://shikimori-auth"
    const val FALLBACK_DEEP_LINK_REDIRECT_URI = "aniyomi://shikimori-auth"
    private const val OAUTH_TOKEN_URL = "https://shikimori.io/oauth/token"

    /**
     * Kullanıcının yapıştırdığı metinden (örn. URL veya query parametresi) auth code'u ayıklar.
     */
    fun sanitizeAuthCode(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.contains("code=")) {
            val extracted = trimmed.substringAfter("code=").substringBefore("&").substringBefore("#").trim()
            if (extracted.isNotBlank()) return extracted
        }
        return trimmed
    }

    /**
     * Shikimori üzerinde önceden doldurulmuş yeni OAuth uygulama oluşturma URL'si (gelişmiş kullanıcılar için).
     */
    fun buildNewApplicationUrl(): String {
        val encodedUri = java.net.URLEncoder.encode(DEEP_LINK_REDIRECT_URI, "UTF-8")
        return "$BASE_URL/oauth/applications/new?application%5Bname%5D=Kitsugi&application%5Bredirect_uri%5D=$encodedUri&application%5Bscopes%5D=user_rates+comments+topics"
    }

    /**
     * OAuth2 yetkilendirme URL'sini üretir.
     */
    fun buildAuthorizeUrl(
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: String = DEEP_LINK_REDIRECT_URI,
        scopes: String = "user_rates+comments+topics"
    ): String {
        val effectiveClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val encodedUri = java.net.URLEncoder.encode(redirectUri, "UTF-8")
        val effectiveScope = if (effectiveClientId == DEFAULT_CLIENT_ID) scopes else "user_rates"
        return "$BASE_URL/oauth/authorize?client_id=$effectiveClientId&redirect_uri=$encodedUri&response_type=code&scope=$effectiveScope"
    }

    /**
     * OAuth yetki kodunu (authorization code) access token ile takas eder.
     * OAuth 2.1 (RFC 9700) standardı gereği token istek gövdesinde redirect_uri parametresi gönderilmez.
     * Geriye dönük uyumluluk için gerekirse eski redirect_uri fallback'leri de desteklenir.
     */
    suspend fun exchangeCodeForToken(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET,
        code: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): ShikimoriTokenResponse = withContext(Dispatchers.IO) {
        val cleanCode = sanitizeAuthCode(code)
        val targetClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val targetSecret = clientSecret.trim().ifBlank { DEFAULT_CLIENT_SECRET }

        try {
            // OAuth 2.1: redirect_uri artık gönderilmiyor
            return@withContext executeTokenRequest(
                clientId = targetClientId,
                clientSecret = targetSecret,
                code = cleanCode
            )
        } catch (e: Exception) {
            // Geriye dönük uyumluluk fallback'i: eski OAuth 2.0 sunucuları için redirect_uri ile deneme
            val legacyUrls = listOf(OAUTH_TOKEN_URL, "$BASE_URL/oauth/token")
            val urisToTry = when (redirectUri) {
                DEEP_LINK_REDIRECT_URI -> listOf(DEEP_LINK_REDIRECT_URI, FALLBACK_DEEP_LINK_REDIRECT_URI, DEFAULT_REDIRECT_URI)
                FALLBACK_DEEP_LINK_REDIRECT_URI -> listOf(FALLBACK_DEEP_LINK_REDIRECT_URI, DEEP_LINK_REDIRECT_URI, DEFAULT_REDIRECT_URI)
                else -> listOf(DEFAULT_REDIRECT_URI, DEEP_LINK_REDIRECT_URI, FALLBACK_DEEP_LINK_REDIRECT_URI)
            }
            for (uri in urisToTry) {
                for (tokenUrl in legacyUrls) {
                    try {
                        val legacyFormBody = FormBody.Builder()
                            .add("grant_type", "authorization_code")
                            .add("client_id", targetClientId)
                            .add("client_secret", targetSecret)
                            .add("code", cleanCode)
                            .add("redirect_uri", uri)
                            .build()
                        val req = Request.Builder()
                            .url(tokenUrl)
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Accept", "application/json")
                            .post(legacyFormBody)
                            .build()
                        KitsugiHttpClient.client.newCall(req).execute().use { resp ->
                            val respBody = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) {
                                val json = JSONObject(respBody)
                                return@withContext ShikimoriTokenResponse(
                                    accessToken = json.getString("access_token"),
                                    refreshToken = json.optString("refresh_token", ""),
                                    expiresIn = json.optLong("expires_in", 2592000L)
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            throw e
        }
    }

    private fun executeTokenRequest(
        clientId: String,
        clientSecret: String,
        code: String
    ): ShikimoriTokenResponse {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            // ✅ redirect_uri artık gönderilmiyor (OAuth 2.1)
            .build()

        val urlsToTry = listOf(OAUTH_TOKEN_URL, "$BASE_URL/oauth/token")
        var lastErr: Exception? = null

        for (tokenUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(tokenUrl)
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
                    return ShikimoriTokenResponse(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token", ""),
                        expiresIn = json.optLong("expires_in", 2592000L)
                    )
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: Exception("Shikimori token isteği başarısız oldu.")
    }

    /**
     * Refresh token ile token yeniler.
     */
    suspend fun refreshToken(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET,
        refreshToken: String
    ): ShikimoriTokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId.trim().ifBlank { DEFAULT_CLIENT_ID })
            .add("client_secret", clientSecret.trim().ifBlank { DEFAULT_CLIENT_SECRET })
            .add("refresh_token", refreshToken)
            .build()

        val urlsToTry = listOf(OAUTH_TOKEN_URL, "$BASE_URL/oauth/token")
        var lastErr: Exception? = null

        for (tokenUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(tokenUrl)
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
                    return@withContext ShikimoriTokenResponse(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token", refreshToken),
                        expiresIn = json.optLong("expires_in", 2592000L)
                    )
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: Exception("Shikimori token yenileme isteği başarısız oldu.")
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
     * Kullanıcının ayrıntılı profilini ve anime/manga durum istatistiklerini alır (/api/users/{userId}).
     */
    suspend fun fetchFullUserProfile(token: String, userId: Int): ShikimoriFullProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/users/$userId")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception("Shikimori profil bilgisi alınamadı (${response.code})")
            val json = JSONObject(body)
            val id = json.getInt("id")
            val nickname = json.optString("nickname", "Shikimori Kullanıcısı")
            val avatar = json.optString("avatar").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("image")?.optString("x160")
                ?: json.optJSONObject("image")?.optString("original")
            val location = json.optString("location").takeIf { it.isNotBlank() }
            val lastOnline = json.optString("last_online_at").takeIf { it.isNotBlank() }
            val bio = json.optString("about").takeIf { it.isNotBlank() }
            val website = json.optString("website").takeIf { it.isNotBlank() }
            val sex = json.optString("sex").takeIf { it.isNotBlank() }
            val fullYears = json.optInt("full_years", 0).takeIf { it > 0 }

            var watchingAnime = 0
            var completedAnime = 0
            var onHoldAnime = 0
            var droppedAnime = 0
            var plannedAnime = 0

            var readingManga = 0
            var completedManga = 0
            var onHoldManga = 0
            var droppedManga = 0
            var plannedManga = 0

            val animeScoreDist = mutableMapOf<Int, Int>()
            val mangaScoreDist = mutableMapOf<Int, Int>()

            val stats = json.optJSONObject("stats")
            val statuses = stats?.optJSONObject("statuses")

            statuses?.optJSONArray("anime")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val name = item.optString("name")
                    val size = item.optInt("size", 0)
                    when (name) {
                        "watching" -> watchingAnime = size
                        "completed" -> completedAnime = size
                        "on_hold" -> onHoldAnime = size
                        "dropped" -> droppedAnime = size
                        "planned" -> plannedAnime = size
                    }
                }
            }

            statuses?.optJSONArray("manga")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val name = item.optString("name")
                    val size = item.optInt("size", 0)
                    when (name) {
                        "watching", "reading" -> readingManga = size
                        "completed" -> completedManga = size
                        "on_hold" -> onHoldManga = size
                        "dropped" -> droppedManga = size
                        "planned" -> plannedManga = size
                    }
                }
            }

            val scores = stats?.optJSONObject("scores")
            scores?.optJSONArray("anime")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val score = item.optString("name").toIntOrNull() ?: continue
                    val value = item.optInt("value", 0)
                    animeScoreDist[score] = value
                }
            }
            scores?.optJSONArray("manga")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val score = item.optString("name").toIntOrNull() ?: continue
                    val value = item.optInt("value", 0)
                    mangaScoreDist[score] = value
                }
            }

            ShikimoriFullProfile(
                id = id,
                nickname = nickname,
                avatarUrl = avatar,
                location = location,
                lastOnline = lastOnline,
                bio = bio,
                website = website,
                sex = sex,
                fullYears = fullYears,
                watchingAnime = watchingAnime,
                completedAnime = completedAnime,
                onHoldAnime = onHoldAnime,
                droppedAnime = droppedAnime,
                plannedAnime = plannedAnime,
                readingManga = readingManga,
                completedManga = completedManga,
                onHoldManga = onHoldManga,
                droppedManga = droppedManga,
                plannedManga = plannedManga,
                animeScoreDist = animeScoreDist,
                mangaScoreDist = mangaScoreDist
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

                val count = try {
                    KitsugiHttpClient.client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "fetchAllUserRates HTTP ${response.code} for $ep page $page")
                            error("Shikimori liste okuma hatası: HTTP ${response.code} ($ep, sayfa $page)")
                        }
                        val body = response.body?.string().orEmpty()
                        if (!body.trim().startsWith("[")) {
                            Log.w(TAG, "fetchAllUserRates non-array response: $body")
                            error("Shikimori liste yanıtı JSON dizisi değil ($ep, sayfa $page)")
                        }
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
                            }.getOrDefault(0L)

                            val mediaObj = if (targetType == "Anime") item.optJSONObject("anime") else item.optJSONObject("manga")
                            val targetId = mediaObj?.optInt("id", 0)?.takeIf { it > 0 } ?: item.optInt("target_id", 0)
                            if (targetId <= 0) continue

                            val title = mediaObj?.optString("name")?.ifBlank { mediaObj.optString("russian") }
                                ?: "Shikimori #$targetId"
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
                } catch (e: Exception) {
                    Log.e(TAG, "fetchAllUserRates error on $ep page $page", e)
                    throw e
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
            response.isSuccessful || response.code == 404
        }
    }

    data class ShikimoriHistoryItem(
        val id: Long,
        val createdAt: String,
        val description: String,
        val targetTitle: String,
        val targetImageUrl: String?,
        val targetScore: String?,
        val targetId: Long? = null,
        val targetType: String? = null
    ) {
        val targetImage: String? get() = targetImageUrl
    }

    /**
     * Shikimori kullanıcısının geçmiş hareketlerini (izlenen/okunan/puanlanan kayıtlar) çeker.
     */
    suspend fun fetchUserHistory(token: String, userId: Int, limit: Int = 30): List<ShikimoriHistoryItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/users/$userId/history?limit=$limit"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                val list = mutableListOf<ShikimoriHistoryItem>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optLong("id", 0L)
                    val createdAt = item.optString("created_at", "")
                    val description = item.optString("description", "")
                    val target = item.optJSONObject("target")
                    val targetId = target?.optLong("id", 0L)?.takeIf { it > 0 }
                    val targetType = target?.optString("target_type")?.ifBlank { null }
                        ?: target?.optString("kind")?.ifBlank { null }
                    val targetTitle = target?.optString("name", "")?.ifBlank { target.optString("russian", "") } ?: "Kayıt"
                    val imgObj = target?.optJSONObject("image")
                    val rawImg = imgObj?.optString("original") ?: imgObj?.optString("preview")
                    val imgUrl = if (!rawImg.isNullOrBlank()) {
                        if (rawImg.startsWith("http")) rawImg else "$BASE_URL$rawImg"
                    } else null
                    val score = target?.optString("score")
                    list.add(
                        ShikimoriHistoryItem(
                            id = id,
                            createdAt = createdAt,
                            description = description,
                            targetTitle = targetTitle,
                            targetImageUrl = imgUrl,
                            targetScore = score,
                            targetId = targetId,
                            targetType = targetType
                        )
                    )
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Shikimori kullanıcısının favorilerini (anime, manga, karakterler) çeker.
     */
    suspend fun fetchUserFavorites(token: String, userId: Int): List<com.kitsugi.animelist.ui.app.ProfileFavoriteItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/users/$userId/favourites"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val list = mutableListOf<com.kitsugi.animelist.ui.app.ProfileFavoriteItem>()

                val arrayKeys = listOf("animes", "mangas", "characters")
                for (key in arrayKeys) {
                    val arr = json.optJSONArray(key) ?: continue
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val id = item.optString("id", "")
                        val title = item.optString("name", "").ifBlank { item.optString("russian", "Favori") }
                        val imgObj = item.optJSONObject("image")
                        val rawImg = imgObj?.optString("original") ?: imgObj?.optString("preview") ?: item.optString("image", "")
                        val imgUrl = if (rawImg.isNotBlank()) {
                            if (rawImg.startsWith("http")) rawImg else "$BASE_URL$rawImg"
                        } else null
                        if (id.isNotBlank() && title.isNotBlank()) {
                            list.add(
                                com.kitsugi.animelist.ui.app.ProfileFavoriteItem(
                                    id = id,
                                    title = title,
                                    imageUrl = imgUrl ?: ""
                                )
                            )
                        }
                    }
                }
                list
            }
        }.getOrDefault(emptyList())
    }
}

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
 * Shikimori OAuth token uç noktasından dönen hata (Doorkeeper JSON hatası veya beklenmeyen yanıt).
 */
class ShikimoriOAuthException(
    val httpCode: Int,
    val error: String?,
    val description: String?,
    message: String
) : Exception(message) {
    /** Kodun başka bir redirect_uri ile üretilmiş olma ihtimali varsa diğer adresler denenebilir. */
    val isRedirectMismatchCandidate: Boolean
        get() = error == "invalid_grant" || error == "invalid_redirect_uri"
}

/**
 * Shikimori REST API istemcisi.
 * OAuth2 token yönetimi, kullanıcı profili ve kullanıcı izleme listesi (user_rates)
 * okuma, ekleme, güncelleme ve silme işlemlerini yürütür.
 */
object ShikimoriApiClient {
    private const val TAG = "ShikimoriApiClient"

    /**
     * Shikimori'nin birincil alan adı artık `shikimori.io`.
     * `shikimori.one` → `shikimori.io` geçişi HTTP 301 ile yapılıyor. OkHttp 301 yönlendirmesinde
     * POST'u GET'e çevirip gövdeyi düşürdüğü için token isteği HTML/404'e dönüşüyor; ayrıca alan adı
     * değişen yönlendirmelerde `Authorization` başlığını sildiği için API istekleri 401 alıyor.
     * Bu yüzden tüm istekler doğrudan `.io` adresine gider, `.one` hiçbir yerde kullanılmaz.
     */
    const val WEB_URL = "https://shikimori.io"
    private const val BASE_URL = WEB_URL
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

    /** Doorkeeper "native" (out-of-band) yönlendirmesi: kod tarayıcıda gösterilir, kullanıcı kopyalayıp yapıştırır. */
    const val DEFAULT_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"

    /**
     * 1-Tık otomatik giriş için uygulamanın kendi deep link'i.
     * DİKKAT: Bu adres Shikimori OAuth uygulamasının (client_id) "Redirect URI" listesinde kayıtlı olmak
     * zorundadır; aksi halde Shikimori "The requested redirect uri is malformed or doesn't match client
     * redirect URI." hatası verir. Birden fazla adres her satıra bir tane yazılarak kaydedilebilir.
     */
    const val DEEP_LINK_REDIRECT_URI = "kitsugi://shikimori-auth"

    /** Eski sürümlerde kullanılan deep link; geriye dönük uyumluluk için manifest'te hâlâ dinleniyor. */
    const val FALLBACK_DEEP_LINK_REDIRECT_URI = "aniyomi://shikimori-auth"

    /** Token takasında denenebilecek bilinen tüm yönlendirme adresleri. */
    val KNOWN_REDIRECT_URIS: List<String> = listOf(
        DEEP_LINK_REDIRECT_URI,
        FALLBACK_DEEP_LINK_REDIRECT_URI,
        DEFAULT_REDIRECT_URI
    )

    /** Shikimori OAuth uygulamasına kaydedilmesi gereken yönlendirme adresleri (her satıra bir tane). */
    val REQUIRED_REGISTERED_REDIRECT_URIS: List<String> = listOf(DEEP_LINK_REDIRECT_URI, DEFAULT_REDIRECT_URI)

    private const val OAUTH_AUTHORIZE_URL = "$BASE_URL/oauth/authorize"
    private const val OAUTH_TOKEN_URL = "$BASE_URL/oauth/token"
    const val OAUTH_APPLICATIONS_URL = "$BASE_URL/oauth/applications"

    /** Doorkeeper varsayılanı: yetkilendirme kodları 10 dakika geçerli ve tek kullanımlıktır. */
    private const val DEFAULT_TOKEN_TTL_SECONDS = 2592000L

    /**
     * Kullanıcının yapıştırdığı metinden (örn. URL veya query parametresi) auth code'u ayıklar.
     * Desteklenen girdiler: salt kod, `code=XYZ`, `https://shikimori.io/oauth/authorize/native?code=XYZ`,
     * `kitsugi://shikimori-auth?code=XYZ`.
     */
    fun sanitizeAuthCode(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.contains("code=")) {
            val extracted = trimmed.substringAfter("code=").substringBefore("&").substringBefore("#").trim()
            if (extracted.isNotBlank()) return extracted
        }
        return trimmed.substringBefore("&").substringBefore("#").trim()
    }

    /**
     * Yapıştırılan metin bir deep link geri dönüş adresiyse (`kitsugi://shikimori-auth?code=...`),
     * kodun hangi redirect_uri ile üretildiğini tespit eder; aksi halde null döner.
     */
    fun detectRedirectUri(rawInput: String): String? {
        val lower = rawInput.trim().lowercase()
        return KNOWN_REDIRECT_URIS.firstOrNull { it != DEFAULT_REDIRECT_URI && lower.startsWith(it) }
    }

    /**
     * Shikimori üzerinde önceden doldurulmuş yeni OAuth uygulama oluşturma URL'si (gelişmiş kullanıcılar için).
     * Redirect URI alanına hem deep link hem de oob adresi (satır satır) önceden yazılır.
     */
    fun buildNewApplicationUrl(): String {
        val redirectUris = java.net.URLEncoder.encode(REQUIRED_REGISTERED_REDIRECT_URIS.joinToString("\n"), "UTF-8")
        return "$OAUTH_APPLICATIONS_URL/new?application%5Bname%5D=Kitsugi&application%5Bredirect_uri%5D=$redirectUris&application%5Bscopes%5D=user_rates+comments+topics"
    }

    /**
     * OAuth2 yetkilendirme URL'sini üretir.
     * redirect_uri değeri token takasında birebir aynı şekilde tekrar gönderilmelidir.
     */
    fun buildAuthorizeUrl(
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: String = DEEP_LINK_REDIRECT_URI,
        scopes: String = "user_rates+comments+topics"
    ): String {
        val effectiveClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val encodedUri = java.net.URLEncoder.encode(redirectUri, "UTF-8")
        val effectiveScope = if (effectiveClientId == DEFAULT_CLIENT_ID) scopes else "user_rates"
        return "$OAUTH_AUTHORIZE_URL?client_id=$effectiveClientId&redirect_uri=$encodedUri&response_type=code&scope=$effectiveScope"
    }

    /**
     * OAuth yetki kodunu (authorization code) access token ile takas eder.
     *
     * Shikimori (Doorkeeper) token isteğinde `redirect_uri` parametresini ZORUNLU tutar
     * ("Missing required parameter: redirect_uri.") ve değer authorize adımındakiyle birebir aynı olmalıdır.
     * Önce beklenen adres denenir; kod başka bir adresle üretildiyse (invalid_grant) bilinen diğer adresler
     * sırayla denenir. Başarısız denemeler kodu tüketmez; kod yalnızca başarılı takasta geçersiz olur.
     */
    suspend fun exchangeCodeForToken(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET,
        code: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): ShikimoriTokenResponse = withContext(Dispatchers.IO) {
        val cleanCode = sanitizeAuthCode(code)
        if (cleanCode.isBlank()) {
            throw ShikimoriOAuthException(0, "invalid_request", null, "Yetkilendirme kodu boş. Lütfen tarayıcıdan aldığınız kodu yapıştırın.")
        }
        val targetClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val targetSecret = clientSecret.trim().ifBlank { DEFAULT_CLIENT_SECRET }
        val preferredUri = detectRedirectUri(code) ?: redirectUri.trim().ifBlank { DEFAULT_REDIRECT_URI }
        val candidates = (listOf(preferredUri) + KNOWN_REDIRECT_URIS).distinct()

        var firstError: ShikimoriOAuthException? = null
        for (uri in candidates) {
            try {
                return@withContext executeTokenRequest(targetClientId, targetSecret, cleanCode, uri)
            } catch (e: ShikimoriOAuthException) {
                Log.w(TAG, "Token takası başarısız (redirect_uri=$uri, http=${e.httpCode}, error=${e.error}): ${e.description}")
                if (firstError == null) firstError = e
                // invalid_client, sunucu hatası vb. durumlarda diğer adresleri denemenin anlamı yok.
                if (!e.isRedirectMismatchCandidate) throw e
            }
        }
        throw firstError ?: ShikimoriOAuthException(0, null, null, "Shikimori token isteği başarısız oldu.")
    }

    private fun executeTokenRequest(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String
    ): ShikimoriTokenResponse {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .build()

        val request = Request.Builder()
            .url(OAUTH_TOKEN_URL)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return parseTokenResponse(response.code, body, fallbackRefreshToken = "")
        }
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

        val request = Request.Builder()
            .url(OAUTH_TOKEN_URL)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            try {
                parseTokenResponse(response.code, body, fallbackRefreshToken = refreshToken)
            } catch (e: ShikimoriOAuthException) {
                throw ShikimoriOAuthException(e.httpCode, e.error, e.description, "Shikimori token yenilenemedi: ${e.message}")
            }
        }
    }

    /**
     * Token uç noktası yanıtını çözümler. Doorkeeper hataları JSON (`error`, `error_description`) döner;
     * HTML/boş gövde ise yönlendirme, Cloudflare ya da bakım sayfasıdır.
     */
    private fun parseTokenResponse(httpCode: Int, body: String, fallbackRefreshToken: String): ShikimoriTokenResponse {
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (json == null) {
            Log.w(TAG, "Token yanıtı JSON değil (HTTP $httpCode): ${body.take(200)}")
            throw ShikimoriOAuthException(
                httpCode, null, null,
                "Shikimori token sunucusu beklenmeyen bir yanıt döndürdü (HTTP $httpCode). Ağ bağlantınızı kontrol edip biraz sonra tekrar deneyin."
            )
        }
        val error = json.optString("error").takeIf { it.isNotBlank() }
        if (httpCode !in 200..299 || error != null) {
            val description = json.optString("error_description").takeIf { it.isNotBlank() }
            throw ShikimoriOAuthException(httpCode, error, description, friendlyOAuthMessage(httpCode, error, description))
        }
        val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw ShikimoriOAuthException(httpCode, null, null, "Shikimori yanıtında access_token bulunamadı.")
        return ShikimoriTokenResponse(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token").ifBlank { fallbackRefreshToken },
            expiresIn = json.optLong("expires_in", DEFAULT_TOKEN_TTL_SECONDS)
        )
    }

    private fun friendlyOAuthMessage(httpCode: Int, error: String?, description: String?): String {
        val base = when (error) {
            "invalid_grant" ->
                "Yetkilendirme kodu geçersiz, süresi dolmuş ya da daha önce kullanılmış. Kodlar tek kullanımlıktır ve birkaç dakika içinde geçersiz olur; lütfen tarayıcıdan yeni bir kod alıp tekrar deneyin."
            "invalid_client" ->
                "Shikimori Client ID / Client Secret doğrulanamadı. Özel API anahtarı kullanıyorsanız değerleri kontrol edin."
            "invalid_redirect_uri" ->
                "Yönlendirme adresi (redirect_uri) Shikimori OAuth uygulamasında kayıtlı değil. Uygulama ayarlarına ${REQUIRED_REGISTERED_REDIRECT_URIS.joinToString(" ve ")} adreslerini ekleyin."
            "invalid_request" ->
                "Shikimori isteği reddetti: ${description ?: "eksik veya hatalı parametre"}"
            else ->
                "Shikimori token alınamadı (HTTP $httpCode)${description?.let { ": $it" } ?: ""}"
        }
        return if (error != null) "$base [$error]" else base
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

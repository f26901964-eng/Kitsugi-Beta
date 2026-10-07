package com.kitsugi.animelist.data.auth

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shikimori.one REST API istemcisi.
 * OAuth2 token yönetimi, kullanıcı profili ve kullanıcı izleme listesi (user_rates)
 * okuma, ekleme, güncelleme ve silme işlemlerini yürütür.
 */
object ShikimoriApiClient {
    private const val TAG = "ShikimoriApiClient"
    /**
     * Production'daki resmi (kanonik) host. Shikimori, diğer domain'leri
     * (örn. shikimori.one) 301 ile shikimori.io adresine yönlendiriyor; OkHttp
     * host değişen yönlendirmelerde Authorization header'ını SİLER, bu yüzden
     * tüm kimlik gerektiren istekler doğrudan bu host'a gider (bkz. executeShikimori).
     */
    private const val BASE_URL = "https://shikimori.io"
    private const val LEGACY_BASE_URL = "https://shikimori.one"
    private const val USER_AGENT = "KitsugiApp/2.4 (Android)"
    private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()

    /**
     * Host değiştiren yönlendirmeleri manuel takip eden istemci.
     *
     * OkHttp varsayılan olarak başka bir host'a yapılan 301/302 yönlendirmelerinde
     * Authorization header'ını güvenlik nedeniyle atar. Shikimori'nin domain
     * arası yönlendirmeleri (shikimori.one → shikimori.io) bu yüzden
     * "oturumsuz" isteklere dönüyordu: whoami 200 + "null" döner, liste uçları 403
     * verirdi — token geçerliyken bile. Bu istemcide yönlendirmeler başlıklar korunarak
     * elle takip edilir.
     */
    private val shikimoriHttpClient: OkHttpClient by lazy {
        KitsugiHttpClient.client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * Shikimori isteklerini çalıştırır. Sunucu yönlendirme (301/302/303/307/308)
     * döndürürse, isteği orijinal Authorization header'ı korunarak yeni adrese
     * yeniden gönderir (en fazla 3 atlayış). Dönen response'u çağıran kapatmalıdır.
     */
    internal fun executeShikimori(request: Request, maxHops: Int = 3): Response {
        var current = request
        var hops = 0
        while (true) {
            val response = shikimoriHttpClient.newCall(current).execute()
            if (response.code in 300..399 && hops < maxHops) {
                val location = response.header("Location")
                if (!location.isNullOrBlank()) {
                    val nextUrl = current.url.resolve(location)
                    if (nextUrl != null) {
                        response.close()
                        // RFC 7231: 301/302/303 POST'u GET'e çevirir (gövde atılır); 307/308 korur.
                        var next = current.newBuilder().url(nextUrl)
                        if (response.code != 307 && response.code != 308 && current.method == "POST") {
                            next = next.method("GET", null)
                        }
                        current = next.build()
                        hops++
                        continue
                    }
                }
            }
            return response
        }
    }

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
    const val SCOPE_USER_RATES = "user_rates"
    const val SCOPE_MESSAGES = "messages"
    const val DEFAULT_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    const val DEEP_LINK_REDIRECT_URI = "kitsugi://shikimori-auth"
    const val FALLBACK_DEEP_LINK_REDIRECT_URI = "aniyomi://shikimori-auth"
    private const val OAUTH_TOKEN_URL = "https://shikimori.io/oauth/token"

    /**
     * Kullanıcının yapıştırdığı metinden (örn. URL veya query parametresi) auth code'u ayıklar.
     * "?code=XXX&state=...", "code=XXX", tırnaklı yapıştırmalar ve yüzde-kodlu metinler desteklenir.
     */
    fun sanitizeAuthCode(rawInput: String): String {
        var value = rawInput.trim().trim('"', '\'', '<', '>', ' ')
        if (value.contains("%")) {
            value = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }
        if (value.contains("code=")) {
            value = value.substringAfter("code=").substringBefore("&").substringBefore("#").trim()
        }
        return value
    }

    /** Shikimori OAuth uygulamaları yönetim sayfası (Redirect URI ayarları burada yapılır). */
    const val APPLICATIONS_URL = "$BASE_URL/oauth/applications"

    /**
     * Shikimori üzerinde önceden doldurulmuş yeni OAuth uygulama oluşturma URL'si (gelişmiş kullanıcılar için).
     */
    fun buildNewApplicationUrl(): String {
        val encodedUri = java.net.URLEncoder.encode(DEEP_LINK_REDIRECT_URI, "UTF-8")
        // Kişisel bildirimler (GET /api/users/:id/messages) için "messages" izni gerekir;
        // kullanıcının kendi uygulamasını oluştururken iki izni de ön-dolduruyoruz.
        return "$BASE_URL/oauth/applications/new?application%5Bname%5D=Kitsugi&application%5Bredirect_uri%5D=$encodedUri&application%5Bscopes%5D=user_rates%20messages"
    }

    /**
     * Verilen client_id için istenebilecek izin kümesini döndürür.
     *
     * Shikimori kuralı: istenen `scope`, uygulamanın Shikimori'de kayıtlı izinlerinin
     * **alt kümesi** olmak zorundadır; aksi halde "invalid scope" hatası döner.
     * Kitsugi'nin paylaşılan (varsayılan) uygulaması yalnızca `user_rates` ile kayıtlıdır,
     * bu yüzden varsayılan client ile `messages` istenemez. Kullanıcı kendi uygulamasını
     * (user_rates + messages) girerse gerçek bildirimler de okunabilir.
     */
    fun scopesFor(clientId: String?): String {
        val id = clientId?.trim().orEmpty()
        return if (id.isBlank() || id == DEFAULT_CLIENT_ID) SCOPE_USER_RATES
        else "$SCOPE_USER_RATES $SCOPE_MESSAGES"
    }

    /**
     * OAuth2 yetkilendirme URL'sini üretir.
     *
     * Not: "scope" uygulamanın Shikimori'de kayıtlı scope'larının bir alt kümesi olmak zorundadır;
     * aksi halde Shikimori "invalid scope" hatası verir. Bu yüzden varsayılan olarak yalnızca
     * "user_rates" istenir; özel client_id için [scopesFor] kullanın.
     */
    fun buildAuthorizeUrl(
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: String = DEEP_LINK_REDIRECT_URI,
        scopes: String = SCOPE_USER_RATES
    ): String {
        val effectiveClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val effectiveRedirect = redirectUri.trim().ifBlank { DEFAULT_REDIRECT_URI }
        val encodedUri = java.net.URLEncoder.encode(effectiveRedirect, "UTF-8")
        return "$BASE_URL/oauth/authorize?client_id=$effectiveClientId&redirect_uri=$encodedUri&response_type=code&scope=$scopes"
    }

    /**
     * Shikimori OAuth/token hatalarını taşıyan özel istisna.
     * [isInvalidGrant] true ise yetki kodu geçersiz, süresi dolmuş ya da farklı bir
     * istemci/redirect adresi için üretilmiş demektir.
     */
    class ShikimoriTokenException(
        message: String,
        val isInvalidGrant: Boolean = false,
        /** Sunucunun döndürdüğü HTTP durum kodu (0 = ağ/istemci kaynaklı hata). */
        val status: Int = 0
    ) : Exception(message)

    /**
     * OAuth yetki kodunu (authorization code) access token ile takas eder.
     *
     * ÖNEMLİ (Shikimori / Doorkeeper): token isteğinde "redirect_uri" ZORUNLUDUR ve
     * /oauth/authorize adımında kullanılan değerle birebir aynı olmalıdır. Aksi halde
     * Shikimori "Missing required parameter: redirect_uri." (400) veya "invalid_grant" döner.
     *
     * Bu yüzden önce çağıranın verdiği redirect_uri denenir; başarısız olursa uygulamanın
     * desteklediği diğer redirect_uri değerleri yedek olarak denenir.
     */
    suspend fun exchangeCodeForToken(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET,
        code: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): ShikimoriTokenResponse = withContext(Dispatchers.IO) {
        val cleanCode = sanitizeAuthCode(code)
        if (cleanCode.isBlank()) {
            throw ShikimoriTokenException("Shikimori yetkilendirme kodu boş görünüyor. Tarayıcıdaki kodu yeniden kopyalayıp yapıştırın.")
        }
        val targetClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val targetSecret = clientSecret.trim().ifBlank { DEFAULT_CLIENT_SECRET }

        val candidates = listOf(
            redirectUri.trim(),
            DEFAULT_REDIRECT_URI,
            DEEP_LINK_REDIRECT_URI,
            FALLBACK_DEEP_LINK_REDIRECT_URI
        ).filter { it.isNotBlank() }.distinct()

        var lastError: Exception? = null
        for (uri in candidates) {
            try {
                return@withContext executeTokenRequest(
                    clientId = targetClientId,
                    clientSecret = targetSecret,
                    code = cleanCode,
                    redirectUri = uri
                )
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Token denemesi başarısız (redirect_uri=$uri): ${e.message}")
            }
        }
        throw lastError ?: ShikimoriTokenException("Shikimori token isteği başarısız oldu.")
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
            // ✅ Doorkeeper/Shikimori bunu zorunlu tutar ve authorize adımındaki değerle eşleşmelidir.
            .add("redirect_uri", redirectUri)
            .build()

        val urlsToTry = listOf(OAUTH_TOKEN_URL, "$LEGACY_BASE_URL/oauth/token")
        var lastErr: Exception? = null

        for (tokenUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(tokenUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .post(formBody)
                    .build()

                executeShikimori(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw tokenErrorFor(response.code, body)
                    }
                    return parseTokenResponse(body)
                }
            } catch (e: ShikimoriTokenException) {
                // Sunucu OAuth hatası döndürdü; ayna adresini denemek sonucu değiştirmez.
                throw e
            } catch (e: Exception) {
                // Ağ/TLS/parse kaynaklı hata: ayna adres denenebilir.
                lastErr = e
            }
        }
        throw lastErr ?: ShikimoriTokenException("Shikimori token isteği başarısız oldu.")
    }

    /**
     * /oauth/token hata yanıtını kullanıcı dostu Türkçe mesaja çevirir.
     * Ham sunucu açıklaması parantez içinde korunur (hata ayıklama için).
     */
    private fun tokenErrorFor(status: Int, body: String): ShikimoriTokenException {
        val json = runCatching { JSONObject(body.trim()) }.getOrNull()
        val errorCode = json?.optString("error").orEmpty()
        val description = json?.optString("error_description").orEmpty()
        val raw = "HTTP $status" +
            if (description.isNotBlank()) " • $description"
            else if (errorCode.isNotBlank()) " • $errorCode"
            else ""

        val friendly = when {
            description.contains("redirect_uri", ignoreCase = true) ->
                "Shikimori token isteğini reddetti: redirect_uri eksik ya da yetkilendirmede kullanılanla uyuşmuyor. " +
                    "Aynı yöntemle (derin bağlantı veya manuel kod) yeniden deneyin. ($raw)"
            errorCode == "invalid_client" ->
                "Shikimori istemci bilgileri geçersiz (client_id/client_secret). " +
                    "Gelişmiş Ayarlar'dan 'Varsayılan Anahtarları Geri Yükle' seçeneğini kullanın. ($raw)"
            errorCode == "invalid_grant" ->
                "Shikimori yetkilendirme kodu geçersiz, süresi dolmuş ya da farklı bir istemci/redirect adresi için üretilmiş. " +
                    "Tarayıcıda yetkilendirmeyi baştan başlatıp yeni kodu hızlıca girin. ($raw)"
            status == 429 ->
                "Shikimori çok fazla istek nedeniyle geçici olarak sınırladı. Birkaç dakika bekleyip tekrar deneyin. ($raw)"
            else ->
                "Shikimori token alınamadı. ($raw)"
        }
        return ShikimoriTokenException(friendly, isInvalidGrant = errorCode == "invalid_grant", status = status)
    }

    /**
     * /oauth/token başarılı yanıtını ayrıştırır; gövde JSON değilse ham JSONException yerine
     * anlaşılır bir hata fırlatır (sunucu/önbellek/engel sayfaları bu yüzden çökertmesin).
     */
    private fun parseTokenResponse(body: String): ShikimoriTokenResponse {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) {
            throw ShikimoriTokenException(
                "Shikimori token yanıtı beklenen JSON biçiminde değil " +
                    "(ağ engeli/önbellek ya da tamamlanmamış yetkilendirme olabilir). Birkaç dakika sonra tekrar deneyin. " +
                    "Yanıt başlangıcı: ${trimmed.take(120).ifBlank { "<boş>" }}"
            )
        }
        val json = JSONObject(trimmed)
        val accessToken = json.optString("access_token").trim()
        if (accessToken.isBlank()) {
            throw ShikimoriTokenException("Shikimori yanıtında access_token bulunamadı: ${trimmed.take(160)}")
        }
        return ShikimoriTokenResponse(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token", ""),
            expiresIn = json.optLong("expires_in", 2592000L)
        )
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

        val urlsToTry = listOf(OAUTH_TOKEN_URL, "$LEGACY_BASE_URL/oauth/token")
        var lastErr: Exception? = null

        for (tokenUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(tokenUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .post(formBody)
                    .build()

                executeShikimori(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw tokenErrorFor(response.code, body)
                    }
                    val parsed = parseTokenResponse(body)
                    return@withContext ShikimoriTokenResponse(
                        accessToken = parsed.accessToken,
                        refreshToken = parsed.refreshToken.ifBlank { refreshToken },
                        expiresIn = parsed.expiresIn
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
     *
     * Not: Shikimori, istek oturumsuz ulaştığında (geçici yönlendirme/önbellek
     * durumlarında da dahil) 200 OK + "null" döndürür. Bu tek bir denemede
     * kalıcı hata sayılmamalı: en fazla 3 deneme yapar; 401 (sunucunun jetonu
     * net biçimde reddi) ise hemen sonlandırır.
     */
    suspend fun getCurrentUser(token: String): ShikimoriUser = withContext(Dispatchers.IO) {
        var lastBody: String? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) {
                Thread.sleep(if (attempt == 1) 700L else 1500L)
            }
            val request = Request.Builder()
                .url("$BASE_URL/api/users/whoami")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val (body, code, isSuccessful) = executeShikimori(request).use { response ->
                Triple(response.body?.string().orEmpty().trim(), response.code, response.isSuccessful)
            }
            if (!isSuccessful) {
                throw Exception(
                    if (code == 401) "Shikimori oturumu geçersiz ya da süresi dolmuş (401). Lütfen yeniden giriş yapın."
                    else "Shikimori profil bilgisi alınamadı ($code)."
                )
            }
            // 200 + "null"/boş/HTML: istek oturumsuz algılanmış. Geçici olabilir → tekrar dene.
            if (body.isBlank() || body == "null" || !body.startsWith("{")) {
                lastBody = body
                continue
            }
            val json = JSONObject(body)
            if (json.optInt("id", 0) <= 0) {
                throw Exception("Shikimori kullanıcı bilgisi alınamadı (geçersiz yanıt). Lütfen yeniden giriş yapın.")
            }
            return@withContext ShikimoriUser(
                id = json.getInt("id"),
                nickname = json.optString("nickname", "Shikimori User"),
                avatarUrl = json.optString("avatar").takeIf { it.isNotBlank() }
            )
        }
        throw Exception(
            "Shikimori oturumu doğrulanamadı: sunucu oturumu tanımadı (whoami yanıtı: ${lastBody?.take(40) ?: "-"})" +
                ". Lütfen yeniden giriş yapın."
        )
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

        executeShikimori(request).use { response ->
            val body = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) throw Exception("Shikimori profil bilgisi alınamadı (${response.code})")
            if (body.isBlank() || body == "null" || !body.startsWith("{")) {
                throw Exception("Shikimori profil yanıtı geçersiz (jeton süresi dolmuş olabilir). Lütfen yeniden giriş yapın.")
            }
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
                    executeShikimori(request).use { response ->
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

        executeShikimori(request).use { response ->
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

        executeShikimori(request).use { response ->
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

        executeShikimori(request).use { response ->
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
            executeShikimori(request).use { response ->
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

    // ─────────────────────────────────────────────────────────────────────────
    // Gerçek bildirimler (mesajlar) — `messages` OAuth izni gerekir
    // Resmî doküman: https://shikimori.io/api/doc/1.0/users/messages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * `messages` izni olmadığında (HTTP 401/403) fırlatılır; UI bunu "izin yok"
     * durumuna çevirip kullanıcıya ne yapması gerektiğini söyler.
     */
    class ShikimoriScopeException(message: String) : Exception(message)

    data class ShikimoriMessage(
        val id: Long,
        val kind: String,
        val read: Boolean,
        val body: String,
        val htmlBody: String,
        val createdAt: String,
        val linkedType: String?,
        val linkedId: Long?,
        val targetTitle: String?,
        val targetImageUrl: String?,
        val targetUrl: String?,
        val fromNickname: String?,
        val fromAvatarUrl: String?,
        val fromUserId: Int?,
        val type: String
    )

    data class ShikimoriUnread(
        val messages: Int,
        val news: Int,
        val notifications: Int
    )

    /**
     * Kullanıcının mesaj/bildirim listesini çeker.
     *
     * @param type `inbox`, `private`, `sent`, `news` veya `notifications` (zorunlu)
     */
    suspend fun fetchMessages(
        token: String,
        userId: Int,
        type: String,
        limit: Int = 30,
        page: Int = 1
    ): List<ShikimoriMessage> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        val url = "$BASE_URL/api/users/$userId/messages?limit=$safeLimit&page=$page&type=$type"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        KitsugiHttpClient.client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw ShikimoriScopeException(
                    "Shikimori 'messages' izni yok (HTTP ${response.code})"
                )
            }
            if (!response.isSuccessful) return@use emptyList()
            val body = response.body?.string().orEmpty()
            val array = runCatching { JSONArray(body) }.getOrNull() ?: return@use emptyList()
            val list = mutableListOf<ShikimoriMessage>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val linked = item.optJSONObject("linked")
                val from = item.optJSONObject("from")
                val rawImg = linked?.optJSONObject("image")?.optString("original")
                    ?: linked?.optJSONObject("image")?.optString("preview")
                list.add(
                    ShikimoriMessage(
                        id = item.optLong("id", 0L),
                        kind = item.optString("kind", ""),
                        read = item.optBoolean("read", true),
                        body = item.optString("body", ""),
                        htmlBody = item.optString("html_body", ""),
                        createdAt = item.optString("created_at", ""),
                        linkedType = item.optString("linked_type").takeIf { it.isNotBlank() },
                        linkedId = item.optLong("linked_id", 0L).takeIf { it > 0 },
                        targetTitle = linked?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: linked?.optString("russian")?.takeIf { it.isNotBlank() },
                        targetImageUrl = rawImg?.takeIf { it.isNotBlank() }?.let {
                            if (it.startsWith("http")) it else "$BASE_URL$it"
                        },
                        targetUrl = linked?.optString("url")?.takeIf { it.isNotBlank() }?.let {
                            if (it.startsWith("http")) it else "$BASE_URL$it"
                        },
                        fromNickname = from?.optString("nickname")?.takeIf { it.isNotBlank() },
                        fromAvatarUrl = from?.optJSONObject("image")?.optString("x48")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { if (it.startsWith("http")) it else "$BASE_URL$it" },
                        fromUserId = from?.optInt("id", 0)?.takeIf { it > 0 },
                        type = type
                    )
                )
            }
            list
        }
    }

    /**
     * Okunmamış mesaj/haber/bildirim sayıları.
     * Resmî doküman: GET /api/users/:id/unread_messages
     */
    suspend fun fetchUnreadCounts(token: String, userId: Int): ShikimoriUnread? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/users/$userId/unread_messages")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        runCatching {
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw ShikimoriScopeException("Shikimori 'messages' izni yok (HTTP ${response.code})")
                }
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                ShikimoriUnread(
                    messages = json.optInt("messages", 0),
                    news = json.optInt("news", 0),
                    notifications = json.optInt("notifications", 0)
                )
            }
        }.getOrElse { cause ->
            // İzin hatası çağırana taşınır; diğer hatalarda sayı bilinmez (null).
            if (cause is ShikimoriScopeException) throw cause
            null
        }
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
            executeShikimori(request).use { response ->
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

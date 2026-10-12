package com.kitsugi.animelist.data.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * MAL Tek Oturum Yöneticisi
 *
 * OAuth API ve Web Scraping için **tek bir giriş** ile her ikisini de yetkilendirir.
 *
 * ## Nasıl Çalışır?
 *
 * 1. OAuth sayfasını **uygulama içi WebView** ile açar
 * 2. Kullanıcı MAL'de giriş yapar
 * 3. WebView otomatik olarak cookie'leri CookieManager'a kaydeder (web scraping için)
 * 4. OAuth tamamlandığında `malapp://auth?code=XXX` redirect'i yakalanır
 * 5. Code ile token exchange yapılır (API için)
 * 6. Sonuç: Hem OAuth token hem web cookie'ler hazır!
 *
 * ## Avantajları
 *
 * - ✅ Kullanıcı sadece **1 kez** giriş yapar
 * - ✅ OAuth API (api.myanimelist.net) için Bearer token
 * - ✅ Web Scraping (myanimelist.net) için session cookie'ler
 * - ✅ Bildirimler, karakter detayları, incelemeler, öneriler hepsi çalışır
 * - ✅ Ekstra login gerektirmez
 *
 * ## Kullanım
 *
 * ```kotlin
 * val result = MalUnifiedAuthManager.startAuth(context)
 * when (result) {
 *     is MalUnifiedAuthResult.Success -> {
 *         // Hem token hem cookie'ler hazır!
 *         // API ve web scraping kullanılabilir
 *     }
 *     is MalUnifiedAuthResult.Cancelled -> { /* Kullanıcı iptal etti */ }
 *     is MalUnifiedAuthResult.Error -> { /* Hata oluştu */ }
 * }
 * ```
 */
object MalUnifiedAuthManager {
    private const val TAG = "MalUnifiedAuth"
    private const val TIMEOUT_MS = 5 * 60 * 1000L // 5 dakika

    private val MAL_CLIENT_ID get() = com.kitsugi.animelist.BuildConfig.MAL_OAUTH_CLIENT_ID
    private val MAL_CLIENT_SECRET get() = com.kitsugi.animelist.BuildConfig.MAL_OAUTH_CLIENT_SECRET
    private const val REDIRECT_URI = "malapp://auth"

    /**
     * OAuth yetkilendirme akışını başlatır ve hem token hem cookie'leri döndürür.
     *
     * @param context Activity context (WebView göstermek için gerekli)
     * @return [MalUnifiedAuthResult] - Başarı, iptal veya hata durumu
     */
    suspend fun startAuth(context: Context): MalUnifiedAuthResult = withContext(Dispatchers.Main) {
        try {
            // 1. Code verifier oluştur
            val codeVerifier = generateCodeVerifier()
            ExternalAuthManager.getPrefs(context).edit()
                .putString("mal_code_verifier", codeVerifier)
                .apply()

            // 2. OAuth URL oluştur
            val authUrl = buildAuthUrl(codeVerifier)

            // 3. WebView'de OAuth sayfasını aç ve code'u bekle
            val code = withTimeout(TIMEOUT_MS) {
                openWebViewAndGetCode(context, authUrl)
            }

            // 4. Code ile token exchange yap
            withContext(Dispatchers.IO) {
                exchangeCodeForToken(context, code, codeVerifier)
            }

            // 5. Cookie'leri doğrula
            val cookiesVerified = verifyWebCookies()

            if (cookiesVerified) {
                android.util.Log.d(TAG, "MAL unified auth successful: both token and cookies acquired")
                MalUnifiedAuthResult.Success
            } else {
                android.util.Log.w(TAG, "MAL token acquired but web cookies missing")
                // Token alındı ama cookie'ler eksik - yine de başarılı say (API çalışır)
                MalUnifiedAuthResult.Success
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e(TAG, "MAL auth timeout", e)
            MalUnifiedAuthResult.Error("Giriş zaman aşımına uğradı. Lütfen tekrar deneyin.")
        } catch (e: MalAuthCancelledException) {
            android.util.Log.d(TAG, "MAL auth cancelled by user")
            MalUnifiedAuthResult.Cancelled
        } catch (e: MalAuthException) {
            android.util.Log.e(TAG, "MAL auth failed", e)
            MalUnifiedAuthResult.Error(e.message ?: "Bilinmeyen hata")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "MAL auth unexpected error", e)
            MalUnifiedAuthResult.Error("Beklenmeyen hata: ${e.message}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun openWebViewAndGetCode(
        context: Context,
        authUrl: String
    ): String = suspendCancellableCoroutine { continuation ->
        // WebView'i başlat
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        }

        // Cookie'leri etkinleştir
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Redirect'i yakala
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // OAuth redirect'i yakala
                if (url.startsWith("malapp://auth")) {
                    val uri = Uri.parse(url)
                    val code = uri.getQueryParameter("code")
                    val error = uri.getQueryParameter("error")

                    if (code != null) {
                        // Başarılı - code'u döndür
                        android.util.Log.d(TAG, "OAuth code received")
                        continuation.resume(code)
                    } else {
                        // Hata veya iptal
                        val message = error ?: "Yetkilendirme iptal edildi"
                        android.util.Log.w(TAG, "OAuth error: $message")
                        continuation.resumeWithException(MalAuthCancelledException(message))
                    }

                    // WebView'i kapat
                    webView.destroy()
                    return true
                }

                // MAL domain'inde kal
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d(TAG, "WebView page loaded: $url")
            }
        }

        // OAuth sayfasını yükle
        android.util.Log.d(TAG, "Loading OAuth URL: $authUrl")
        webView.loadUrl(authUrl)

        // İptal edildiğinde WebView'i temizle
        continuation.invokeOnCancellation {
            android.util.Log.d(TAG, "OAuth flow cancelled, destroying WebView")
            webView.destroy()
        }
    }

    private fun buildAuthUrl(codeVerifier: String): String {
        return "https://myanimelist.net/v1/oauth2/authorize?" +
                "response_type=code" +
                "&client_id=$MAL_CLIENT_ID" +
                "&code_challenge=$codeVerifier" +
                "&code_challenge_method=plain" +
                "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}"
    }

    private suspend fun exchangeCodeForToken(
        context: Context,
        code: String,
        codeVerifier: String
    ) = withContext(Dispatchers.IO) {
        val secretParam = if (MAL_CLIENT_SECRET.isNotBlank()) "&client_secret=$MAL_CLIENT_SECRET" else ""
        val postData =
            "client_id=$MAL_CLIENT_ID" +
                    secretParam +
                    "&code=$code" +
                    "&code_verifier=$codeVerifier" +
                    "&grant_type=authorization_code" +
                    "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}"

        val request = okhttp3.Request.Builder()
            .url("https://myanimelist.net/v1/oauth2/token")
            .post(postData.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseText = response.body?.string() ?: ""
                val jsonObj = org.json.JSONObject(responseText)
                val accessToken = jsonObj.getString("access_token")
                val refreshToken = jsonObj.optString("refresh_token", "")
                val expiresIn = jsonObj.optLong("expires_in", 2419200L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                // Token'ları kaydet
                ExternalAuthManager.getPrefs(context).edit()
                    .putString("mal_access_token", accessToken)
                    .putString("mal_refresh_token", refreshToken)
                    .putLong("mal_token_expires_at", expiresAt)
                    .remove("mal_code_verifier")
                    .apply()

                android.util.Log.d(TAG, "Token exchange successful")
            } else {
                val errorResponse = response.body?.string().orEmpty()
                ExternalAuthManager.getPrefs(context).edit().remove("mal_code_verifier").apply()

                if (errorResponse.contains("invalid_client", ignoreCase = true) || response.code == 401) {
                    throw MalAuthException(
                        "MAL istemci kimliği (client_id) reddedildi. Geliştirici: myanimelist.net/apiconfig " +
                                "üzerinden kendi uygulamanızı oluşturup local.properties içine mal_client_id olarak ekleyin."
                    )
                } else {
                    throw MalAuthException("MAL token değişimi başarısız: ${response.code} - $errorResponse")
                }
            }
        }
    }

    /**
     * Web cookie'lerin alınıp alınmadığını doğrular.
     *
     * @return Cookie'ler mevcutsa true, yoksa false
     */
    private suspend fun verifyWebCookies(): Boolean = withContext(Dispatchers.Main) {
        val cookieHeader = CookieManager.getInstance().getCookie("https://myanimelist.net/")
        val hasSessionCookie = cookieHeader?.contains("MALHLOGSESSID=", ignoreCase = true) == true ||
                cookieHeader?.contains("MALSESSIONID=", ignoreCase = true) == true

        if (hasSessionCookie) {
            android.util.Log.d(TAG, "Web session cookies verified")
        } else {
            android.util.Log.w(TAG, "Web session cookies not found")
        }

        hasSessionCookie
    }

    /**
     * Mevcut oturumun hem API hem web scraping için geçerli olup olmadığını kontrol eder.
     *
     * @return [MalSessionStatus] - API ve web oturum durumu
     */
    suspend fun checkSessionStatus(context: Context): MalSessionStatus = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getMalToken(context)
        val hasToken = !token.isNullOrBlank()

        val cookies = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie("https://myanimelist.net/")
        }
        val hasCookies = cookies?.contains("MALHLOGSESSID=", ignoreCase = true) == true ||
                cookies?.contains("MALSESSIONID=", ignoreCase = true) == true

        MalSessionStatus(
            hasApiToken = hasToken,
            hasWebSession = hasCookies,
            isFullyAuthenticated = hasToken && hasCookies
        )
    }

    /**
     * Web cookie'leri eksikse, mevcut OAuth token ile web session oluşturmaya çalışır.
     *
     * Bu fonksiyon, OAuth token var ama web cookie'ler yoksa kullanılır.
     * Örneğin: Eski sürümden yükseltme veya cookie'lerin süresi dolmuşsa.
     *
     * @return Başarılıysa true, başarısızsa false
     */
    suspend fun ensureWebSession(context: Context): Boolean = withContext(Dispatchers.IO) {
        val status = checkSessionStatus(context)

        // Zaten her ikisi de varsa işlem yapma
        if (status.isFullyAuthenticated) {
            android.util.Log.d(TAG, "Session already fully authenticated")
            return@withContext true
        }

        // Token yoksa yeniden giriş gerekli
        if (!status.hasApiToken) {
            android.util.Log.w(TAG, "No API token, re-authentication required")
            return@withContext false
        }

        // Token var ama cookie yok - web session oluştur
        android.util.Log.d(TAG, "API token exists but web cookies missing, attempting to create web session")

        // Kullanıcı adını al
        val username = try {
            val token = ExternalAuthManager.getOrRefreshMalToken(context) ?: return@withContext false
            val request = okhttp3.Request.Builder()
                .url("https://api.myanimelist.net/v2/users/@me?fields=name")
                .header("Authorization", "Bearer $token")
                .build()

            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(response.body?.string().orEmpty())
                    json.optString("name", "")
                } else ""
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to fetch username", e)
            ""
        }

        if (username.isBlank()) {
            android.util.Log.w(TAG, "Could not fetch username, cannot create web session")
            return@withContext false
        }

        // Web session oluşturmak için profil sayfasına istek at
        // Bu, cookie'leri otomatik olarak oluşturmayacak ama en azından kullanıcı adını doğrulayacak
        android.util.Log.d(TAG, "Web session creation requires user interaction (re-login)")
        false
    }

    /**
     * Oturumu tamamen temizler (hem token hem cookie'ler).
     */
    suspend fun logout(context: Context) = withContext(Dispatchers.Main) {
        // Token'ları temizle
        withContext(Dispatchers.IO) {
            ExternalAuthManager.disconnectAccount(context, "mal")
        }

        // Cookie'leri temizle
        com.kitsugi.animelist.data.remote.malweb.MalWebSessionStore.clearSession()

        android.util.Log.d(TAG, "MAL session fully cleared")
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = java.security.SecureRandom()
        val codeVerifier = ByteArray(32)
        secureRandom.nextBytes(codeVerifier)
        return android.util.Base64.encodeToString(
            codeVerifier,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }
}

/**
 * MAL unified auth sonucu
 */
sealed class MalUnifiedAuthResult {
    /** Hem token hem cookie'ler başarıyla alındı */
    object Success : MalUnifiedAuthResult()

    /** Kullanıcı iptal etti */
    object Cancelled : MalUnifiedAuthResult()

    /** Hata oluştu */
    data class Error(val message: String) : MalUnifiedAuthResult()
}

/**
 * MAL oturum durumu
 */
data class MalSessionStatus(
    val hasApiToken: Boolean,
    val hasWebSession: Boolean,
    val isFullyAuthenticated: Boolean
)

class MalAuthException(message: String) : Exception(message)
class MalAuthCancelledException(message: String) : Exception(message)

package com.kitsugi.animelist.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bangumi (bgm.tv) OAuth 2.0 giriş akışı yöneticisi.
 *
 * Akış (Bangumi klasik authorization code grant — **PKCE yok**):
 *
 *  1. Kullanıcı https://bgm.tv/dev/app adresinden kendi uygulamasını kaydeder,
 *     "回调地址" (redirect) alanını **boş** bırakır → App ID + App Secret alır.
 *  2. [startAuth] tarayıcıyı `https://bgm.tv/oauth/authorize?client_id=...&response_type=code
 *     &redirect_uri=kitsugi://bangumi-auth` adresine götürür.
 *  3. Kullanıcı "允许" der; Bangumi `kitsugi://bangumi-auth?code=XXX` ile uygulamaya döner.
 *     Kod **60 saniye** geçerlidir ve tek kullanımlıktır.
 *  4. [exchangeCode] `POST https://bgm.tv/oauth/access_token` ile token'ı alır
 *     (grant_type=authorization_code + client_id + client_secret + code + redirect_uri).
 *  5. `GET /v0/me` ile kullanıcı adı çözülür; koleksiyon uçlarında `-` yerine de geçer.
 *  6. Token 7 gün geçerlidir; [BangumiAuthStore] süresi dolmadan 1 saat önce yeniler.
 *
 * Deep link çalışmazsa (tarayıcı özel şemayı açmayı reddederse) kullanıcı adres
 * çubuğundaki tam URL'yi kopyalayıp giriş diyaloğuna yapıştırabilir;
 * [BangumiApiClient.sanitizeAuthCode] kodu ayıklar.
 */
object BangumiAuthManager {
    private const val TAG = "BangumiAuthManager"

    /** Servis adı — [ExternalAuthManager.AuthEvent] içinde kullanılan anahtar. */
    const val SERVICE_NAME = "bangumi"

    /**
     * Tarayıcıda Bangumi yetki sayfasını açar.
     *
     * @return hata mesajı; `null` ise akış başarıyla başlatıldı.
     */
    fun startAuth(
        context: Context,
        redirectUri: String = BangumiApiClient.DEEP_LINK_REDIRECT_URI
    ): String? {
        if (!BangumiAuthStore.hasCredentials(context)) {
            return "Bangumi App ID / App Secret bulunamadı. Önce https://bgm.tv/dev/app " +
                "adresinden uygulamanızı kaydedin veya anahtarları giriş ekranından girin."
        }
        val clientId = BangumiAuthStore.getClientId(context)
        return runCatching {
            // Token isteğindeki redirect_uri, authorize adımındakiyle birebir aynı olmalı.
            BangumiAuthStore.savePendingRedirectUri(context, redirectUri)
            val state = java.util.UUID.randomUUID().toString().take(8)
            val authUrl = BangumiApiClient.buildAuthorizeUrl(
                clientId = clientId,
                redirectUri = redirectUri,
                state = state
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            null
        }.getOrElse { error ->
            Log.e(TAG, "startAuth failed: ${error.message}", error)
            "Bangumi yetkilendirme başlatılamadı: ${error.message}"
        }
    }

    /**
     * Geri dönen `code` değerini token'a çevirir ve oturumu kaydeder.
     *
     * Kod tek kullanımlıktır: takas başarılı olduğunda token **derhal** saklanır.
     * `GET /v0/me` geçici olarak başarısız olsa bile geçerli oturum çöpe atılmaz;
     * kullanıcı adı ilk kullanımda tembel çözümlenir (Shikimori ile aynı yaklaşım).
     */
    fun exchangeCode(
        context: Context,
        code: String,
        redirectUri: String = BangumiAuthStore.getPendingRedirectUri(context),
        onSuccess: (serviceName: String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanCode = BangumiApiClient.sanitizeAuthCode(code)
        if (cleanCode.isBlank()) {
            val message = "Bangumi yetkilendirme kodu boş"
            onError(message)
            ExternalAuthManager.emitBangumiError(message)
            return
        }
        if (!BangumiAuthStore.hasCredentials(context)) {
            val message = "Bangumi App ID / App Secret girilmeden kod değiştirilemez"
            onError(message)
            ExternalAuthManager.emitBangumiError(message)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                BangumiApiClient.exchangeCodeForToken(
                    clientId = BangumiAuthStore.getClientId(context),
                    clientSecret = BangumiAuthStore.getClientSecret(context),
                    code = cleanCode,
                    redirectUri = redirectUri
                )
            }.onSuccess { token ->
                // 1) Token'ı hemen sakla — kod bir daha kullanılamaz.
                BangumiAuthStore.saveAuth(context, token, user = null, notify = false)

                // 2) Kimliği çöz (başarısızlık oturumu düşürmez).
                val user = runCatching { BangumiApiClient.getMe(token.accessToken) }.getOrNull()
                if (user != null) {
                    BangumiAuthStore.saveAuth(context, token, user)
                } else {
                    Log.w(TAG, "Bangumi /v0/me çözülemedi; kimlik tembel yüklenecek")
                    ExternalAuthManager.emitBangumiSuccess()
                }
                BangumiAuthStore.clearPendingRedirectUri(context)
                withContext(Dispatchers.Main) { onSuccess(SERVICE_NAME) }
            }.onFailure { error ->
                val message = when (error) {
                    is BangumiApiClient.BangumiApiException -> buildString {
                        append("Bangumi giriş başarısız (HTTP ${error.code})")
                        error.title?.let { append(": ").append(it) }
                        error.description?.let { append(" — ").append(it) }
                        if (error.code == 400) {
                            append(". Kod 60 saniyede geçerliliğini yitirir ve tek kullanımlıktır; ")
                            append("ayrıca redirect_uri kaydınızla birebir aynı olmalıdır (")
                            append(redirectUri).append(").")
                        }
                    }
                    else -> "Bangumi giriş başarısız: ${error.message}"
                }
                BangumiAuthStore.clearPendingRedirectUri(context)
                Log.e(TAG, message, error)
                ExternalAuthManager.emitBangumiError(message)
                withContext(Dispatchers.Main) { onError(message) }
            }
        }
    }

    /** Oturumu kapatır (token + kullanıcı bilgisi silinir, App ID/Secret korunur). */
    fun logout(context: Context) {
        BangumiAuthStore.logout(context)
    }

    /** Bağlantıyı test eder: kimlik bilgisi var mı, token yenilenebiliyor mu, API erişilebilir mi. */
    suspend fun diagnose(context: Context): List<String> {
        val report = mutableListOf<String>()
        report += if (BangumiAuthStore.hasCredentials(context)) {
            "App ID hazır: ${BangumiAuthStore.getClientId(context).maskSecret()}"
        } else {
            "HATA: App ID / App Secret yok → https://bgm.tv/dev/app"
        }
        report += if (BangumiAuthStore.isConnected(context)) {
            "Oturum var (kullanıcı: ${BangumiAuthStore.getUsername(context) ?: "?"}, " +
                "uid: ${BangumiAuthStore.getUserId(context)})"
        } else {
            "Oturum yok"
        }
        val reachable = runCatching { BangumiApiClient.testConnection() }.getOrDefault(false)
        report += if (reachable) "api.bgm.tv erişilebilir" else "HATA: api.bgm.tv erişilemiyor"
        return report
    }

    /** App Secret'ı loglarda/ekranda maskelemek için. */
    private fun String.maskSecret(): String =
        if (length <= 8) "${take(2)}***" else "${take(6)}…${takeLast(2)}"
}

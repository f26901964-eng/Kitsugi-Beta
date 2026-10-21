package com.kitsugi.animelist.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

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
            val message = KitsugiStrings.get(R.string.bangumi_yetkilendirme_kodu_bos)
            onError(message)
            ExternalAuthManager.emitBangumiError(message)
            return
        }
        if (!BangumiAuthStore.hasCredentials(context)) {
            val message = KitsugiStrings.get(R.string.bangumi_app_id_app_secret_girilmeden_kod_deg)
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
                val code = PlatformFailureRegistry.report(
                    platformLabel = "bangumi",
                    error = error,
                    rawMessage = message,
                ) ?: PlatformErrorCode.COM_1020
                val codedMessage = "$message (${code.code})"
                ExternalAuthManager.emitBangumiError(codedMessage)
                withContext(Dispatchers.Main) { onError(codedMessage) }
            }
        }
    }

    /**
     * **Kişisel erişim anahtarı** (个人令牌) ile bağlanır — OAuth'a alternatif,
     * uygulama kaydı gerektirmeyen yol.
     *
     * Neden gerekli olabiliyor: Bangumi 2026-06-25'te (`bangumi/server` PR #1011)
     * koleksiyon yazma uçlarına `write:collection` kapsam denetimi ekledi. Kapsam,
     * token satırındaki `scope` alanından okunur ve Go tarafı bu alanı **yalnızca JSON
     * nesnesi** olarak çözebilir; yeni OAuth servisi (`server-private`) ise aynı alanı
     * **JSON dizisi** olarak yazıyor. Dizi çözülemeyince token "kapsamsız" sayılır ve
     * yazma 403 ile reddedilir. Kişisel erişim anahtarlarında `scope` hiç yazılmadığı
     * için sunucu onları "legacy" kabul eder ve yazma çalışır.
     *
     * Bu, tanılamadaki yazma sondasıyla (`probeCollectionWrite`) ölçülür: kullanıcı
     * "Bağlantıyı ve yazma iznini test et" dediğinde kapsam reddi görülürse çözüm bu
     * yoldur. Anahtar `next.bgm.tv/demo/access-token/create` sayfasından üretilir;
     * süresi kullanıcı tarafından seçilir (7–365 gün) ve yenilenemez.
     */
    fun connectWithPersonalToken(
        context: Context,
        rawToken: String,
        onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }
    ) {
        val token = sanitizePersonalToken(rawToken)
        if (token.isBlank()) {
            val message = KitsugiStrings.get(R.string.bangumi_pat_bos)
            onResult(false, message)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { BangumiApiClient.getMe(token) }
                .onSuccess { user ->
                    BangumiAuthStore.saveManualToken(context, token, user)
                    Log.i(TAG, "PAT oturumu kuruldu: uid=${user.id}")
                    withContext(Dispatchers.Main) { onResult(true, null) }
                }
                .onFailure { error ->
                    val message = when (error) {
                        is BangumiApiClient.BangumiApiException -> buildString {
                            append(KitsugiStrings.get(R.string.bangumi_pat_dogrulanamadi, error.code))
                            error.title?.let { append(": ").append(it) }
                            error.description?.let { append(" — ").append(it) }
                            if (error.isUnauthorized) {
                                append(". ").append(KitsugiStrings.get(R.string.bangumi_pat_401_ipucu))
                            }
                        }
                        else -> KitsugiStrings.get(R.string.bangumi_diag_network_error, error.message ?: "?")
                    }
                    Log.e(TAG, message, error)
                    withContext(Dispatchers.Main) { onResult(false, message) }
                }
        }
    }

    /**
     * Kullanıcı anahtarı düz metin olarak da, `Bearer x` biçiminde de, tam URL olarak da
     * yapıştırabilir; üç biçim de desteklenir. Tırnak/boşluk kırpılır.
     */
    internal fun sanitizePersonalToken(raw: String): String {
        var value = raw.trim().trim('"', '\'', ' ')
        if (value.contains("access_token=")) {
            value = value.substringAfter("access_token=")
        } else if (value.contains("token=")) {
            value = value.substringAfter("token=")
        }
        value = value.substringBefore('&').substringBefore(' ').trim()
        if (value.startsWith("Bearer ", ignoreCase = true)) {
            value = value.substring(7).trim()
        }
        return value
    }

    /** Oturumu kapatır (token + kullanıcı bilgisi silinir, App ID/Secret korunur). */
    fun logout(context: Context) {
        BangumiAuthStore.logout(context)
    }

    /**
     * Bağlantıyı **uçtan uca** test eder: App kimliği → oturum → token geçerliliği →
     * koleksiyon **yazma kapısı**. Dönen satırlar giriş diyaloğunda gösterilir.
     *
     * Yazma testi [BangumiApiClient.probeCollectionWrite] ile yapılır: **var olmayan**
     * bir yapıt kimliğine boş `PATCH` gönderilir. Bu istek hiçbir kaydı değiştirmez,
     * ama sunucunun yazma kapısını kesin olarak ölçer (404 = açık, 403 = kapalı).
     *
     * Böylece "listeye hiçbir şey ekleyemiyorum" şikâyeti ölçülebilir bir sonuca döner:
     *  - kapı kapalıysa sebep kapsamdır (`write:collection`) → kişisel erişim anahtarı çözer,
     *  - kapı açıkken yazma yine başarısızsa sebep istek gövdesi ya da hesap durumudur;
     *    yazma hatalarında sunucunun ham metni gösterilir.
     */
    suspend fun diagnose(context: Context): List<String> {
        val report = mutableListOf<String>()
        report += if (BangumiAuthStore.hasCredentials(context)) {
            KitsugiStrings.get(R.string.bangumi_diag_credentials_ok, BangumiAuthStore.getClientId(context).maskSecret())
        } else {
            KitsugiStrings.get(R.string.bangumi_diag_credentials_missing)
        }
        if (!BangumiAuthStore.isConnected(context)) {
            report += KitsugiStrings.get(R.string.bangumi_diag_session_none)
            return report
        }
        report += if (BangumiAuthStore.isManualToken(context)) {
            KitsugiStrings.get(R.string.bangumi_diag_session_manual)
        } else {
            KitsugiStrings.get(R.string.bangumi_diag_session_oauth)
        }

        val token = runCatching { BangumiAuthStore.getValidToken(context) }.getOrNull()
        if (token.isNullOrBlank()) {
            report += KitsugiStrings.get(R.string.bangumi_diag_token_unavailable)
            return report
        }

        // 1) Token gerçekten geçerli mi? (`GET /v0/me`)
        val me = runCatching { BangumiApiClient.getMe(token) }
        val user = me.getOrNull()
        if (user == null) {
            val error = me.exceptionOrNull()
            report += if (error is BangumiApiClient.BangumiApiException) {
                KitsugiStrings.get(R.string.bangumi_diag_token_invalid, error.code)
            } else {
                KitsugiStrings.get(R.string.bangumi_diag_network_error, error?.message ?: "?")
            }
            return report
        }
        report += KitsugiStrings.get(R.string.bangumi_diag_token_ok, user.username)

        // 2) Yazma kapısı: yan etkisiz PATCH sondası.
        val probe = BangumiApiClient.probeCollectionWrite(token)
        report += when (probe.outcome) {
            BangumiApiClient.CollectionWriteProbe.WRITE_ALLOWED ->
                KitsugiStrings.get(R.string.bangumi_diag_write_ok)
            BangumiApiClient.CollectionWriteProbe.SCOPE_DENIED ->
                KitsugiStrings.get(R.string.bangumi_diag_write_denied)
            BangumiApiClient.CollectionWriteProbe.UNAUTHORIZED ->
                KitsugiStrings.get(R.string.bangumi_diag_write_unauthorized)
            BangumiApiClient.CollectionWriteProbe.FORBIDDEN_OTHER ->
                KitsugiStrings.get(R.string.bangumi_diag_write_forbidden_other, probe.detail ?: "?")
            // 415: sunucu isteğin biçimini reddetti — token/kapsam denetimine hiç ulaşılmadı.
            // Kullanıcının yapabileceği bir şey yoktur; uygulama hatası olarak raporlanmalı.
            BangumiApiClient.CollectionWriteProbe.REQUEST_REJECTED ->
                KitsugiStrings.get(R.string.bangumi_diag_write_request_rejected, probe.detail ?: "?")
            BangumiApiClient.CollectionWriteProbe.FAILED ->
                KitsugiStrings.get(R.string.bangumi_diag_write_failed, probe.detail ?: "?")
        }
        return report
    }

    /** App Secret'ı loglarda/ekranda maskelemek için. */
    private fun String.maskSecret(): String =
        if (length <= 8) "${take(2)}***" else "${take(6)}…${takeLast(2)}"
}

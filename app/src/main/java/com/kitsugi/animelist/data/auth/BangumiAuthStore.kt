package com.kitsugi.animelist.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kitsugi.animelist.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bangumi (bgm.tv) oturum ve uygulama kimlik bilgisi deposu.
 *
 * Depolama, [ExternalAuthManager] ile aynı SharedPreferences dosyasını kullanır; böylece
 * tüm harici hesaplar tek yerde yönetilir. Alan adları Shikimori/Kitsu düzenini izler.
 *
 * Kimlik bilgisi önceliği:
 *  1. Kullanıcının uygulama içinden girdiği App ID / App Secret (kendi bgm.tv uygulaması)
 *  2. `local.properties` → BuildConfig.BANGUMI_CLIENT_ID / BANGUMI_CLIENT_SECRET
 *
 * 2. seçenek boşsa giriş akışı "kendi uygulamanızı kaydedin" yönergesiyle kullanıcıyı
 * https://bgm.tv/dev/app adresine gönderir. Başka uygulamaların (Aniyomi, Mihon, Komikku)
 * App ID/Secret değerleri **kullanılmaz**: her uygulama Bangumi'de kendi kaydını yapmalıdır.
 */
object BangumiAuthStore {
    private const val TAG = "BangumiAuthStore"

    /** [ExternalAuthManager.PREFS_NAME] ile aynı depo. */
    private const val PREFS_NAME = "MyWebViewPrefs"

    private const val KEY_TOKEN = "bangumi_access_token"
    private const val KEY_REFRESH_TOKEN = "bangumi_refresh_token"
    private const val KEY_EXPIRES_AT = "bangumi_token_expires_at"
    private const val KEY_TOKEN_TYPE = "bangumi_token_type"
    private const val KEY_SCOPE = "bangumi_token_scope"
    private const val KEY_USER_ID = "bangumi_user_id"
    private const val KEY_USERNAME = "bangumi_username"
    private const val KEY_NICKNAME = "bangumi_nickname"
    private const val KEY_AVATAR = "bangumi_avatar_url"
    private const val KEY_CLIENT_ID = "bangumi_client_id"
    private const val KEY_CLIENT_SECRET = "bangumi_client_secret"

    /**
     * Son yetkilendirme akışında tarayıcıya verilen `redirect_uri`.
     * Bangumi token isteğinde bu değerin authorize adımıyla **birebir** aynı olmasını ister,
     * bu yüzden akış başlarken kaydedilir (Shikimori ile aynı desen).
     */
    private const val KEY_PENDING_REDIRECT = "bangumi_pending_redirect_uri"

    /** Süresi dolmadan 1 saat önce yenile (Aniyomi/Mihon `isExpired()` ile aynı pay). */
    private const val REFRESH_SAFETY_MARGIN_MS = 60L * 60L * 1000L

    /** Eş zamanlı yenileme isteklerini teke indirir. */
    private val refreshMutex = Mutex()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Kimlik bilgileri ─────────────────────────────────────────────────────

    /** BuildConfig'den gelen geliştirici varsayılanı (local.properties). */
    private val buildConfigClientId: String get() = BuildConfig.BANGUMI_CLIENT_ID.trim()
    private val buildConfigClientSecret: String get() = BuildConfig.BANGUMI_CLIENT_SECRET.trim()

    fun getClientId(context: Context): String {
        val saved = prefs(context).getString(KEY_CLIENT_ID, null)?.trim()
        if (isValidCredential(saved)) return saved!!
        return buildConfigClientId.takeIf { isValidCredential(it) }.orEmpty()
    }

    fun getClientSecret(context: Context): String {
        val saved = prefs(context).getString(KEY_CLIENT_SECRET, null)?.trim()
        if (isValidCredential(saved)) return saved!!
        return buildConfigClientSecret.takeIf { isValidCredential(it) }.orEmpty()
    }

    /**
     * `local.properties` doldurulmadığında BuildConfig'e yazılan yer tutucu değerler
     * (`YOUR_BANGUMI_CLIENT_ID` / `YOUR_BANGUMI_CLIENT_SECRET`) geçerli sayılmaz.
     * Bangumi App ID'leri `bgm` öneki + 17 hex karakter biçimindedir (örn. `bgm2916...`),
     * ancak biçim denetimi yapılmaz: kullanıcı kendi kaydından farklı bir şema alabilir.
     */
    private fun isValidCredential(value: String?): Boolean {
        val trimmed = value?.trim()
        if (trimmed.isNullOrBlank()) return false
        return !trimmed.startsWith("YOUR_") && !trimmed.startsWith("\${")
    }

    /**
     * OAuth için gerekli iki değer de hazır mı?
     * Hazır değilse arayüz kullanıcıyı uygulama kaydına yönlendirmelidir.
     */
    fun hasCredentials(context: Context): Boolean =
        getClientId(context).isNotBlank() && getClientSecret(context).isNotBlank()

    fun saveCredentials(context: Context, clientId: String, clientSecret: String) {
        prefs(context).edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun clearCredentials(context: Context) {
        prefs(context).edit()
            .remove(KEY_CLIENT_ID)
            .remove(KEY_CLIENT_SECRET)
            .apply()
    }

    // ── Oturum durumu ────────────────────────────────────────────────────────

    fun isConnected(context: Context): Boolean =
        !prefs(context).getString(KEY_TOKEN, null).isNullOrBlank()

    /** Ham token (süre denetimi YAPMAZ). Yalnızca tanılama/çıktı için. */
    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getUserId(context: Context): Long = prefs(context).getLong(KEY_USER_ID, 0L)

    /** Koleksiyon uçlarında `-` yerine kullanılabilen benzersiz kullanıcı adı. */
    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }

    fun getNickname(context: Context): String? =
        prefs(context).getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() }

    fun getAvatarUrl(context: Context): String? =
        prefs(context).getString(KEY_AVATAR, null)?.takeIf { it.isNotBlank() }

    fun saveAuth(
        context: Context,
        token: BangumiApiClient.BangumiToken,
        user: BangumiApiClient.BangumiUser? = null,
        notify: Boolean = true
    ) {
        val editor = prefs(context).edit()
            .putString(KEY_TOKEN, token.accessToken)
            .putString(KEY_TOKEN_TYPE, token.tokenType)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + token.expiresIn * 1000L)
        token.refreshToken?.let { editor.putString(KEY_REFRESH_TOKEN, it) }
        token.scope?.let { editor.putString(KEY_SCOPE, it) }

        val resolvedUserId = user?.id ?: token.userId ?: 0L
        if (resolvedUserId > 0) editor.putLong(KEY_USER_ID, resolvedUserId)
        user?.let {
            if (it.username.isNotBlank()) editor.putString(KEY_USERNAME, it.username)
            if (it.nickname.isNotBlank()) editor.putString(KEY_NICKNAME, it.nickname)
            it.avatarUrl?.let { url -> editor.putString(KEY_AVATAR, url) }
        }
        editor.apply()

        if (notify) {
            ExternalAuthManager.emitBangumiSuccess()
        }
    }

    /** Giriş sonrası `GET /v0/me` ile kimliği tamamlar (kullanıcı adı koleksiyon uçlarında gerekir). */
    suspend fun ensureUserResolved(context: Context): Boolean {
        val token = getToken(context) ?: return false
        if (!getUsername(context).isNullOrBlank() && getUserId(context) > 0) return true
        return runCatching {
            val user = BangumiApiClient.getMe(token)
            val editor = prefs(context).edit()
                .putLong(KEY_USER_ID, user.id)
                .putString(KEY_USERNAME, user.username)
                .putString(KEY_NICKNAME, user.nickname)
            user.avatarUrl?.let { editor.putString(KEY_AVATAR, it) }
            editor.apply()
            true
        }.getOrElse {
            Log.w(TAG, "ensureUserResolved failed: ${it.message}")
            false
        }
    }

    /**
     * Koleksiyon **okuma** uçları (`GET /v0/users/{username}/collections[/{id}]`) için
     * GERÇEK kullanıcı adını döndürür.
     *
     * Bangumi sunucusu bu uçlarda `-` takma adını kabul etmez (`user.GetByName("-")` →
     * 404 "user doesn't exist or has been removed"). `-` yalnızca yazma uçlarında
     * (`POST/PATCH /v0/users/-/collections/...`) geçerlidir. Sıra:
     *  1. Kayıtlı kullanıcı adı,
     *  2. `GET /v0/me` ile çözülen kullanıcı adı (kaydedilir),
     *  3. Kayıtlı sayısal kullanıcı ID'si (özel kullanıcı adı yoksa `username` zaten ID'dir).
     */
    suspend fun resolveUsername(context: Context): String? {
        getUsername(context)?.takeIf { it.isNotBlank() && it != "-" }?.let { return it }
        ensureUserResolved(context)
        getUsername(context)?.takeIf { it.isNotBlank() && it != "-" }?.let { return it }
        return getUserId(context).takeIf { it > 0L }?.toString()
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_TOKEN_TYPE)
            .remove(KEY_SCOPE)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR)
            .remove(KEY_PENDING_REDIRECT)
            .apply()
        // Uygulama kimlik bilgileri bilinçli olarak SAKLANIR: kullanıcı tekrar giriş
        // yaptığında App ID/Secret'i yeniden yazmak zorunda kalmasın.
    }

    // ── Token yaşam döngüsü ──────────────────────────────────────────────────

    private fun isExpired(context: Context): Boolean {
        val expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= 0L) return false // Süre bilgisi yoksa süresi dolmuş sayma
        return System.currentTimeMillis() > expiresAt - REFRESH_SAFETY_MARGIN_MS
    }

    /**
     * Geçerli bir access token döndürür; gerekiyorsa önce yeniler.
     * Yenileme başarısız olursa oturumu düşürür ve `null` verir.
     */
    suspend fun getValidToken(context: Context): String? {
        val token = getToken(context) ?: return null
        if (!isExpired(context)) return token
        return forceRefresh(context)
    }

    /** Refresh token ile zorla yeni access token alır. */
    suspend fun forceRefresh(context: Context): String? = refreshMutex.withLock {
        val refreshToken = prefs(context).getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrBlank()) {
            Log.w(TAG, "forceRefresh: refresh_token yok, oturum kapatılıyor")
            dropSession(context)
            return@withLock null
        }
        if (!hasCredentials(context)) {
            Log.w(TAG, "forceRefresh: App ID/Secret yok, token yenilenemiyor")
            return@withLock null
        }
        runCatching {
            val pendingRedirect = getPendingRedirectUri(context)
            val newToken = BangumiApiClient.refreshToken(
                clientId = getClientId(context),
                clientSecret = getClientSecret(context),
                refreshToken = refreshToken,
                redirectUri = pendingRedirect
            )
            saveAuth(context, newToken, notify = false)
            newToken.accessToken
        }.getOrElse { error ->
            Log.e(TAG, "Bangumi token yenileme başarısız: ${error.message}")
            if (error is BangumiApiClient.BangumiApiException && (error.isUnauthorized || error.isForbidden)) {
                dropSession(context)
            }
            null
        }
    }

    /**
     * 401 alan bir çağrıdan sonra bir kez denenir: yenile ve yeni token'ı döndür.
     * [BangumiImportManager] / [BangumiSyncManager] bu deseni Shikimori ile aynı şekilde kullanır.
     */
    suspend fun retryAfterUnauthorized(context: Context, block: suspend (String) -> Unit) {
        val refreshed = forceRefresh(context)
        if (refreshed.isNullOrBlank()) {
            throw BangumiApiClient.BangumiApiException(
                code = 401,
                title = "Unauthorized",
                description = null,
                message = "Bangumi oturumu sona erdi. Lütfen Bangumi hesabıyla tekrar giriş yapın."
            )
        }
        block(refreshed)
    }

    private fun dropSession(context: Context) {
        logout(context)
        ExternalAuthManager.emitBangumiSessionExpired()
    }

    // ── Redirect URI takibi ──────────────────────────────────────────────────

    fun savePendingRedirectUri(context: Context, redirectUri: String) {
        prefs(context).edit().putString(KEY_PENDING_REDIRECT, redirectUri.trim()).apply()
    }

    /** Kayıtlı yoksa varsayılan deep link şeması döner. */
    fun getPendingRedirectUri(context: Context): String =
        prefs(context).getString(KEY_PENDING_REDIRECT, null)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: BangumiApiClient.DEEP_LINK_REDIRECT_URI

    fun clearPendingRedirectUri(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_REDIRECT).apply()
    }

    /**
     * Derin bağlantıdan gelen şemaya göre doğru `redirect_uri`'yi seçer.
     * `aniyomi://bangumi-auth` ve `kitsugi://bangumi-auth` manifest'te kayıtlıdır.
     */
    fun redirectUriForScheme(scheme: String?): String = when (scheme?.lowercase()) {
        "aniyomi" -> BangumiApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI
        else -> BangumiApiClient.DEEP_LINK_REDIRECT_URI
    }
}

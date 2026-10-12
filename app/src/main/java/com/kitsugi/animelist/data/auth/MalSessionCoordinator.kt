package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.malweb.MalNotificationsClient
import com.kitsugi.animelist.data.remote.malweb.MalWebSessionRequiredException
import com.kitsugi.animelist.data.remote.malweb.MalWebSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MyAnimeList **tek oturum** koordinatörü — kullanıcı bir kez giriş yapar, iki ayrı kimlik
 * aynı hesaba bağlanır.
 *
 * MAL iki farklı kimlik kullanır ve ikisi de farklı özellikler açar:
 *
 *  - **Uygulama API'si (OAuth 2):** `ExternalAuthManager` içindeki erişim jetonu.
 *    Liste yazma/okuma, profil, senkronizasyon bu jetonla çalışır.
 *  - **Web oturumu:** WebView çerez deposundaki MAL oturum çerezleri.
 *    MAL'ın kişisel bildirim akışı (`myanimelist.net/notification`) yalnızca bununla okunur;
 *    API v2'de bildirim ucu yoktur.
 *
 * Kullanıcı şikâyeti bu iki kimliğin AYRI girişler gibi davranmasıydı: normal (OAuth) girişten
 * sonra bildirimler gelmiyor, bildirim penceresindeki "yeniden bağlan" ile ikinci bir giriş
 * yapmak gerekiyordu. Kök neden: OAuth girişi jetonu bağlıyor ama web oturumu ya hiç
 * kurulmuyor ya da **başka bir MAL hesabına** ait kalıyor; bildirim akışı da hesaplar
 * uyuşmadığı için bilinçli olarak gizleniyor.
 *
 * Bu koordinatör tek bir girişte iki kimliği de KURAR ve DOĞRULAR:
 *  1. Girişten hemen sonra [verifyAndPersist] çağrılır: jeton + çerez aynı hesabı gösteriyor mu?
 *  2. Eşleşmiyorsa yalnızca MAL web çerezleri temizlenir ([clearWebSessionOnly]) ve giriş
 *     akışı bir kez daha açılır — bu kez MAL giriş formu görünür ve kullanıcı OAuth hesabıyla
 *     AYNI hesaba web oturumu da açar. Bağlı hesap (jeton) kesilmez.
 *  3. Sonuç kalıcı olarak saklanır ([savedWebUsername]) ki ekranlar her açılışta yeniden
 *     doğrulama yapmasın.
 */
object MalSessionCoordinator {

    private const val TAG = "MalSessionCoordinator"
    private const val PREF_WEB_USERNAME = "mal_web_username"
    private const val PREF_WEB_VERIFIED_AT = "mal_web_verified_at"

    /** İki kimliğin toplam durumu. */
    enum class State {
        /** İkisi de yok. */
        NOT_LINKED,

        /** Yalnızca OAuth jetonu var (liste/senkron çalışır, bildirimler çalışmaz). */
        API_ONLY,

        /** Yalnızca web oturumu var (bildirimler çalışır, liste yazma çalışmaz). */
        WEB_ONLY,

        /** İkisi de var ve AYNI hesabı gösteriyor. */
        LINKED,

        /** İkisi de var ama FARKLI hesaplara ait — bildirimler gösterilmez. */
        MISMATCHED,

        /** Kimliklerden biri okunamadı (sayfa yapısı / geçici ağ hatası). */
        UNVERIFIED
    }

    data class Status(
        val hasApiToken: Boolean,
        val oauthUsername: String?,
        val hasWebCookie: Boolean,
        val webUsername: String?,
        val state: State
    ) {
        /** Bildirim akışı ve web özellikleri kullanılabilir mi? */
        val webReady: Boolean
            get() = state == State.LINKED || state == State.WEB_ONLY

        /** Liste/senkronizasyon (API) özellikleri kullanılabilir mi? */
        val apiReady: Boolean
            get() = state == State.LINKED || state == State.API_ONLY
    }

    /**
     * Mevcut durumu okur.
     *
     * @param verifyWeb `true` ise web oturumunun sahibi MAL'a gerçekten sorularak okunur
     *   (1 istek). `false` ise yalnızca çerez varlığına ve kaydedilmiş kullanıcı adına bakılır.
     */
    suspend fun probe(context: Context, verifyWeb: Boolean = true): Status = withContext(Dispatchers.IO) {
        val token = runCatching { ExternalAuthManager.getOrRefreshMalToken(context) }.getOrNull()
        val hasApiToken = !token.isNullOrBlank()
        val oauthUsername = if (hasApiToken) {
            runCatching { MalImportManager.fetchUserName(token!!) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } else null

        val cookie = MalWebSessionStore.browserCookieHeader()
        val hasWebCookie = !cookie.isNullOrBlank()

        val webUsername = when {
            !hasWebCookie -> null
            verifyWeb -> verifyWebUsername(cookie!!)
            else -> savedWebUsername(context)
        }

        val state = when {
            !hasApiToken && !hasWebCookie -> State.NOT_LINKED
            hasApiToken && !hasWebCookie -> State.API_ONLY
            !hasApiToken && hasWebCookie -> State.WEB_ONLY
            oauthUsername != null && webUsername != null ->
                if (oauthUsername.equals(webUsername, ignoreCase = true)) State.LINKED else State.MISMATCHED
            else -> State.UNVERIFIED
        }

        Log.d(
            TAG,
            "probe → state=$state api=$hasApiToken($oauthUsername) web=$hasWebCookie($webUsername)"
        )
        Status(
            hasApiToken = hasApiToken,
            oauthUsername = oauthUsername,
            hasWebCookie = hasWebCookie,
            webUsername = webUsername,
            state = state
        )
    }

    /**
     * Web oturumunun sahibi olan MAL kullanıcı adını MAL'a sorarak okur.
     *
     * Çerez ADINA bakılarak karar verilmez: MAL'ın hangi çerezle "giriş yapılmış" saydığını
     * yalnızca MAL bilir. Bildirim sayfası istenir; giriş sayfasına düşerse oturum yok demektir.
     *
     * @return oturum sahibinin kullanıcı adı; oturum yoksa/okunamazsa `null`.
     */
    suspend fun verifyWebUsername(cookieHeader: String): String? = withContext(Dispatchers.IO) {
        try {
            MalNotificationsClient.fetch(cookieHeader).sessionUsername
        } catch (e: MalWebSessionRequiredException) {
            Log.w(TAG, "MAL web oturumu doğrulanamadı: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "MAL web kimliği okunamadı: ${e.message}")
            null
        }
    }

    /**
     * Girişten hemen sonra çağrılır: iki kimliği doğrular, sonucu saklar ve durumu döndürür.
     *
     * Bağlı hesap (OAuth jetonu) ASLA silinmez — yalnızca durum raporlanır. Web oturumu
     * eksikse ya da başka bir hesaba aitse çağıran taraf [clearWebSessionOnly] + tek bir
     * ek giriş adımıyla akışı tamamlar ([needsWebRelogin]).
     */
    suspend fun verifyAndPersist(context: Context): Status {
        val status = probe(context, verifyWeb = true)
        val username = status.webUsername?.takeIf { it.isNotBlank() }
        val prefs = runCatching { ExternalAuthManager.getPrefs(context) }.getOrNull() ?: return status
        prefs.edit().apply {
            if (username != null) {
                putString(PREF_WEB_USERNAME, username)
                putLong(PREF_WEB_VERIFIED_AT, System.currentTimeMillis())
            } else {
                remove(PREF_WEB_USERNAME)
                remove(PREF_WEB_VERIFIED_AT)
            }
            apply()
        }
        return status
    }

    /** Web oturumunun yeniden kurulması gerekiyor mu? (eksik ya da farklı hesap) */
    fun needsWebRelogin(status: Status): Boolean =
        status.state == State.MISMATCHED || status.state == State.API_ONLY

    /** Kaydedilmiş (doğrulanmış) web oturumu kullanıcı adı. */
    fun savedWebUsername(context: Context): String? =
        runCatching { ExternalAuthManager.getPrefs(context).getString(PREF_WEB_USERNAME, null) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Web oturumu bilgisini sıfırlar (çıkışta çağrılır). */
    fun forgetWebIdentity(context: Context) {
        runCatching {
            ExternalAuthManager.getPrefs(context).edit()
                .remove(PREF_WEB_USERNAME)
                .remove(PREF_WEB_VERIFIED_AT)
                .apply()
        }
    }

    /**
     * Yalnızca MAL web çerezlerini temizler; OAuth jetonu ve diğer platformların oturumları
     * olduğu gibi kalır. Amaç: bir sonraki WebView girişinde MAL'ın login formunu göstermek ve
     * web oturumunu OAuth hesabıyla AYNI hesaba bağlamak.
     */
    suspend fun clearWebSessionOnly(): Boolean = MalWebSessionStore.clearSession()
}

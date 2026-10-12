package com.kitsugi.animelist.data.remote.malweb

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the already-consented MyAnimeList website session held by Android WebView. */
internal object MalWebSessionStore {
    private const val MAL_ROOT = "https://myanimelist.net/"
    private const val MAL_DOMAIN = "myanimelist.net"
    private val MAL_HOSTS = listOf("https://$MAL_DOMAIN/", "https://www.$MAL_DOMAIN/")

    /** CookieManager is accessed on the main thread; raw cookies are never copied to preferences. */
    suspend fun authenticatedCookieHeader(): String? = withContext(Dispatchers.Main.immediate) {
        readAuthenticatedCookieHeader()
    }

    /** Must be called on the main thread (for example, from a WebView callback). */
    fun readAuthenticatedCookieHeader(): String? {
        val cookieHeader = runCatching { CookieManager.getInstance().getCookie(MAL_ROOT) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return if (isAuthenticatedCookie(cookieHeader)) cookieHeader else null
    }

    internal fun isAuthenticatedCookie(cookieHeader: String?): Boolean =
        !cookieHeader.isNullOrBlank() && cookieHeader
            .split(';')
            .asSequence()
            .map { it.trim() }
            .any { it.equals("is_signed_in=1", ignoreCase = true) }

    // ─── Oturumu temizleme ────────────────────────────────────────────────────

    /**
     * Drops only the MyAnimeList website cookies from the shared WebView store so the user can
     * sign in with the account that matches the linked OAuth profile.
     *
     * Cookies are expired individually instead of calling `removeAllCookies`: that API wipes
     * every site's session, including other platforms the user signed into.
     *
     * @return oturum çerezi gerçekten silindiyse `true`.
     */
    suspend fun clearSession(): Boolean = withContext(Dispatchers.Main.immediate) {
        clearSessionBlocking()
    }

    /** Must be called on the main thread (for example, from a WebView callback). */
    fun clearSessionBlocking(): Boolean {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return false
        val names = runCatching { manager.getCookie(MAL_ROOT) }
            .getOrNull()
            .orEmpty()
            .split(';')
            .map { it.trim().substringBefore('=') }
            .filter { it.isNotBlank() }
            .distinct()
        if (names.isEmpty()) return false

        for (host in MAL_HOSTS) {
            for (name in names) {
                // Süresi geçmiş çerez yazmak, WebView'in çerez deposundan silmenin tek
                // güvenilir yolu; alan/domain çerezleri için iki varyant da yazılır.
                runCatching {
                    manager.setCookie(host, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/")
                }
                runCatching {
                    manager.setCookie(
                        host,
                        "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Domain=$MAL_DOMAIN"
                    )
                }
            }
        }
        runCatching { manager.flush() }
        return readAuthenticatedCookieHeader() == null
    }
}

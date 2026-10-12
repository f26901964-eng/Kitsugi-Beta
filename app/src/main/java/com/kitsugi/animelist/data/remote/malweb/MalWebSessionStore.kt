package com.kitsugi.animelist.data.remote.malweb

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the already-consented MyAnimeList website session held by Android WebView. */
internal object MalWebSessionStore {
    private const val MAL_ROOT = "https://myanimelist.net/"

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
}

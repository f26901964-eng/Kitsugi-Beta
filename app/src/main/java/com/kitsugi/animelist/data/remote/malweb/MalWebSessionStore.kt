package com.kitsugi.animelist.data.remote.malweb

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kullanıcının Android WebView'de (uygulama içi giriş) açtığı MyAnimeList web oturumunu okur.
 *
 * MAL'ın web sitesi `is_signed_in` adlı bir çerez KULLANMAZ; eski Kitsugi sürümü bu adı
 * aradığı için OAuth ile giriş yapılmış olsa bile oturum asla "bağlı" görünmüyordu ve
 * gerçek MAL bildirimleri hiç yüklenmiyordu. MAL'ın gerçek çerezleri:
 *
 *  - `MALHLOGSESSID`  → giriş yapılmış kullanıcı oturumu (login.php / OAuth authorize sayfası)
 *  - `MALSESSIONID`   → oturum kimliği; giriş yapılmışken de bulunur
 *  - `is_logged_in=1` → MAL arayüzünün yazdığı yardımcı bayrak; tek başına yeterli değildir
 *  - `is_signed_in=1` → eski Kitsugi varsayımı; daha önce bağlanmış kurulumlar bozulmasın diye korunur
 *
 * Bayrak çerezleri boş bir `MALHLOGSESSID=` ile birlikte kalabilir (çıkış sonrası kalıntı).
 * Bu yüzden bayrak tek başına oturum sayılmaz; en az bir oturum çerezi bulunmalıdır.
 */
internal object MalWebSessionStore {
    private const val MAL_ROOT = "https://myanimelist.net/"

    /** Giriş yapılmış oturumu taşıyan çerezler (öncelik sırasıyla). */
    private val SESSION_COOKIES = listOf("malhlogsessid", "malsessionid")

    /** MAL arayüzünün giriş yapılmışken yazdığı yardımcı bayraklar. */
    private val FLAG_COOKIES = listOf("is_logged_in", "is_signed_in")

    /** CookieManager ana iş parçacığında okunur; ham çerez değerleri hiçbir zaman tercihlere yazılmaz. */
    suspend fun authenticatedCookieHeader(): String? = withContext(Dispatchers.Main.immediate) {
        readAuthenticatedCookieHeader()
    }

    /**
     * Oturum doğrulanmış sayılmasa bile MAL için duran TÜM çerezler.
     *
     * Bunun amacı: "oturum yok" kararını tahminle değil, doğrudan MAL'a yapılan isteğin
     * cevabıyla vermek. Çerez kavanozunda MAL çerezi varsa besleme bir kez denenir;
     * MAL giriş sayfasına yönlendirirse oturum gerçekten yoktur.
     */
    suspend fun browserCookieHeader(): String? = withContext(Dispatchers.Main.immediate) {
        readBrowserCookieHeader()
    }

    /** Yalnızca ana iş parçacığından çağrılmalıdır (örneğin WebView geri çağrısı). */
    fun readAuthenticatedCookieHeader(): String? {
        val cookieHeader = readBrowserCookieHeader() ?: return null
        return if (isAuthenticatedCookie(cookieHeader)) cookieHeader else null
    }

    fun readBrowserCookieHeader(): String? = runCatching {
        CookieManager.getInstance().getCookie(MAL_ROOT)
    }.getOrNull()
        ?.takeIf { it.isNotBlank() && it.contains('=') }

    /**
     * Teşhis için: kavanozda bulunan MAL çerezlerinin ADLARI (değerler asla dışarı verilmez).
     * "session" girdisi gerçek oturum çerezini, "flag" girdisi yardımcı bayrağı gösterir.
     */
    fun presentCookieNames(cookieHeader: String? = null): List<String> {
        val header = cookieHeader ?: runCatching {
            CookieManager.getInstance().getCookie(MAL_ROOT)
        }.getOrNull() ?: return emptyList()
        val cookies = parseCookies(header)
        return SESSION_COOKIES.mapNotNull { name ->
            cookies[name]?.takeIf { it.isNotBlank() }?.let { name }
        } + FLAG_COOKIES.mapNotNull { name ->
            cookies[name]?.takeIf { it.isNotBlank() }?.let { "$name=${cookies[name]}" }
        }
    }

    /**
     * Kavanozdaki çerez başlığı gerçek bir MAL oturumuna işaret ediyor mu?
     *
     * Karar yalnızca gerçek oturum çerezine (boş olmayan `MALHLOGSESSID` / `MALSESSIONID`)
     * dayanır. Bayrak çerezleri tek başına yeterli değildir: eski sürümün `is_signed_in=1`
     * araması tam olarak bu yüzden oturumu hiç göremiyordu ve çıkış sonrası kalıntı bayraklar
     * yanlış "bağlı" sonucu üretirdi. Bayraklar yalnızca teşhis satırında raporlanır.
     */
    internal fun isAuthenticatedCookie(cookieHeader: String?): Boolean {
        val cookies = parseCookies(cookieHeader)
        if (cookies.isEmpty()) return false
        return SESSION_COOKIES.any { !cookies[it].isNullOrBlank() }
    }

    private fun parseCookies(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        return cookieHeader
            .split(';')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    part.substring(0, separator).trim().lowercase() to
                        part.substring(separator + 1).trim()
                }
            }
            .toMap()
    }
}

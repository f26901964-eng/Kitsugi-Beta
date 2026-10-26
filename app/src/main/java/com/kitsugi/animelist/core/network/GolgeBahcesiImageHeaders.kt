package com.kitsugi.animelist.core.network

/**
 * Gölge Bahçesi (`golgebahcesi.com` ve `*.skycdn.online`) görsel CDN'i için
 * tarayıcı uyumlu istek başlıkları (saf, birim testlenebilir).
 *
 * Kapak görselleri `/image-proxy/series-thumbnail/...` veya doğrudan
 * `c2.skycdn.online/series-thumbnail/...` üzerinden, eski (legacy) bölüm sayfaları ise
 * `c2.skycdn.online/series/...` üzerinden servis edilir. Coil ([KitsugiHttpClient.client])
 * ve OkHttp isteklerinde Referer/Accept eksikliği hotlink 403 hatasına veya eski Android
 * sürümlerinde çözülemeyen AVIF içerik pazarlığına yol açabildiğinden burada eklenir.
 */
object GolgeBahcesiImageHeaders {

    const val REFERER = "https://golgebahcesi.com/"
    const val ORIGIN = "https://golgebahcesi.com"

    /** AVIF dışarıda bırakılır: WebP/PNG/JPEG tüm hedef Android API seviyelerinde desteklenir. */
    const val ACCEPT = "image/webp,image/png,image/jpeg,image/gif,image/*;q=0.8,*/*;q=0.5"

    private val IMAGE_EXTENSIONS = listOf(".webp", ".jpg", ".jpeg", ".png", ".gif")

    fun appliesReferer(host: String?): Boolean {
        val h = host?.trim()?.lowercase() ?: return false
        if (h.isEmpty()) return false
        return h == "golgebahcesi.com" ||
            h.endsWith(".golgebahcesi.com") ||
            h == "skycdn.online" ||
            h.endsWith(".skycdn.online")
    }

    fun appliesImageAccept(host: String?, encodedPath: String?): Boolean {
        val h = host?.trim()?.lowercase() ?: return false
        if (h.isEmpty()) return false
        if (h == "api.golgebahcesi.com") return false

        if (h == "skycdn.online" || h.endsWith(".skycdn.online")) {
            return true
        }

        if (h == "golgebahcesi.com" || h.endsWith(".golgebahcesi.com")) {
            val p = encodedPath?.trim()?.lowercase().orEmpty()
            if (p.startsWith("/image-proxy/") || p.startsWith("/series/") || p.startsWith("/uploads/")) {
                return true
            }
            val cleanPath = p.substringBefore('?')
            return IMAGE_EXTENSIONS.any { cleanPath.endsWith(it) }
        }
        return false
    }
}

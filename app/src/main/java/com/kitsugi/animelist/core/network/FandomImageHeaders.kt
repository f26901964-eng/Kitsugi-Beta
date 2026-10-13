package com.kitsugi.animelist.core.network

/**
 * Fandom görsel CDN'i için istek başlıkları (saf, testlenebilir).
 *
 * Wiki bilgi kutusu görselleri `static.wikia.nocookie.net` (ve eski `vignette.wikia.nocookie.net`)
 * üzerinden gelir. Bu uç, tarayıcı dışı isteklerde Referer/Accept eksikse 403 dönebiliyor;
 * ayrıca içerik pazarlığında AVIF seçebiliyor. Coil, [KitsugiHttpClient.client] üzerinden
 * indirdiği için başlıklar o istemcinin interceptor'ında eklenir.
 */
object FandomImageHeaders {

    const val REFERER = "https://www.fandom.com/"

    /** AVIF bilinçli olarak dışarıda: Android 12 öncesi BitmapFactory AVIF çözemez. */
    const val ACCEPT = "image/webp,image/png,image/jpeg,image/gif,image/*;q=0.8,*/*;q=0.5"

    /** Yalnızca görsel uçları; `*.fandom.com` API istekleri etkilenmez. */
    private val HOST_SUFFIXES = listOf(
        ".wikia.nocookie.net",
        ".wikia.com"
    )

    fun applies(host: String?): Boolean {
        val h = host?.trim()?.lowercase() ?: return false
        return HOST_SUFFIXES.any { h.endsWith(it) }
    }
}

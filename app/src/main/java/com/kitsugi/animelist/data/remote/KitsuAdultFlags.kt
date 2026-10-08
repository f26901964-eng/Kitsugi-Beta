package com.kitsugi.animelist.data.remote

import org.json.JSONObject

/**
 * Kitsu API yaş sınırı bayrakları için ortak +18 tespiti.
 *
 * Kitsu'da +18 içerik üç şekilde işaretlenebilir:
 *  - `ageRating == "R18"`
 *  - `nsfw == true` (açık kapak / müstehcen görsel)
 *  - `subtype == "hentai"`
 *
 * Tüm Kitsu okuma yolları (liste, keşfet, detay, kütüphane içe aktarma)
 * bu tek noktadan kontrolden geçer; böylece bulanıklık mantığı her yerde
 * aynı şekilde çalışır.
 */
object KitsuAdultFlags {

    /** Verilen Kitsu `attributes` nesnesinin +18 olup olmadığını belirler. */
    fun isAdult(attrs: JSONObject?): Boolean {
        if (attrs == null) return false
        val ageRating = attrs.optString("ageRating", "")
        if (ageRating.equals("R18", ignoreCase = true)) return true
        if (attrs.optBoolean("nsfw", false)) return true
        val subtype = attrs.optString("subtype", "")
        if (subtype.equals("hentai", ignoreCase = true)) return true
        val guide = attrs.optString("ageRatingGuide", "")
        return guide.contains("hentai", ignoreCase = true)
    }
}

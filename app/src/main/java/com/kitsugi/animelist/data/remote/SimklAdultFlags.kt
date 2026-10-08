package com.kitsugi.animelist.data.remote

import org.json.JSONObject

/**
 * Simkl API yaş sınırı bayrakları için ortak +18 tespiti.
 *
 * Simkl içeriklerinde +18 iki şekilde işaretlenebilir:
 *  - `adult == true`
 *  - `certification` alanı açık yaş sertifikası ("NC-17", "X", "XXX", "18+" ...)
 *
 * "R" (17+) ve "TV-MA" gibi sertifikalar +18 DEĞİLDİR; blur yalnızca
 * kesin +18 sertifikalarında uygulanır.
 */
object SimklAdultFlags {

    private val adultCertifications = setOf(
        "NC-17", "X", "XXX", "18+", "18", "R18", "R18+", "TV-18", "AO", "M18", "VM18"
    )

    /** Verilen Simkl medya nesnesinin +18 olup olmadığını belirler. */
    fun isAdult(obj: JSONObject?): Boolean {
        if (obj == null) return false
        if (obj.optBoolean("adult", false)) return true
        val cert = obj.optString("certification", "").trim()
        if (cert.isEmpty()) return false
        val upper = cert.uppercase()
        return upper in adultCertifications ||
            upper.contains("XXX") ||
            upper.contains("18+") ||
            upper.contains("R18")
    }
}

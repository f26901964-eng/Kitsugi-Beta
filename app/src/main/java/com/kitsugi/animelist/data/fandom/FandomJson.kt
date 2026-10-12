package com.kitsugi.animelist.data.fandom

import org.json.JSONArray
import org.json.JSONObject

/*
 * org.json yardımcıları.
 *
 * Android'in org.json uygulamasında `optString()` JSON `null` değeri için "null" METNİNİ
 * döndürür (JVM'deki org.json ise boş metin döndürür). Fandom/Wikidata/AniList yanıtlarında
 * `"english": null` gibi alanlar sık görüldüğü için tüm ayrıştırıcılar bu yardımcıları kullanır.
 */

/** Alan yoksa, JSON `null` ise ya da boşsa `null`; aksi halde kırpılmış metin. */
internal fun JSONObject.str(key: String): String? {
    if (isNull(key)) return null
    val value = opt(key) as? String ?: return null
    val trimmed = value.trim()
    return trimmed.takeIf { it.isNotEmpty() }
}

/** Pozitif tam sayı; yoksa/`null`/≤0 ise `null`. */
internal fun JSONObject.positiveInt(key: String): Int? {
    if (isNull(key)) return null
    val value = optInt(key, 0)
    return value.takeIf { it > 0 }
}

/** Dizideki metin öğeleri (null/boş olanlar atlanır). */
internal fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        if (isNull(i)) continue
        val value = opt(i) as? String ?: continue
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) out += trimmed
    }
    return out
}

/** Dizideki pozitif tam sayılar. */
internal fun JSONArray?.positiveInts(): List<Int> {
    if (this == null) return emptyList()
    val out = ArrayList<Int>(length())
    for (i in 0 until length()) {
        val value = when (val raw = opt(i)) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: continue
            else -> continue
        }
        if (value > 0) out += value
    }
    return out
}

/** Dizideki JSON nesneleri. */
internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out += it }
    }
    return out
}

/** Ayrıştırma hatasında `null` döner. */
internal fun parseJsonObject(json: String?): JSONObject? {
    if (json.isNullOrBlank()) return null
    return try {
        JSONObject(json)
    } catch (e: Exception) {
        null
    }
}

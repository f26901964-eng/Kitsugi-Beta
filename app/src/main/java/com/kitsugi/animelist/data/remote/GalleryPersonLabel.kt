package com.kitsugi.animelist.data.remote

/**
 * Kişi / karakter galerisindeki görsel etiketi için dile duyarlı ad.
 *
 * Öncelik (seçilen başlık diline göre):
 *  - NATIVE / JAPANESE_STAFF → Japonca özgün ad, yoksa mevcut ad
 *  - ENGLISH                 → İngilizce → Romaji → Latin alternatif ad → mevcut ad
 *  - ROMAJI (varsayılan)     → Romaji → İngilizce → Latin alternatif ad → mevcut ad
 *
 * "Latin alternatif ad" = Japonca/Kana/Kanji içermeyen ilk alternatif isim. Bu sayede
 * ad yalnızca Japonca geldiğinde bile mümkünse romaji bir isim gösterilir.
 */
fun galleryPersonLabel(
    titleLanguage: String,
    name: String,
    romanized: String?,
    native: String?,
    english: String?,
    alternatives: List<String> = emptyList()
): String {
    val romajiName = romanized?.takeIf { it.isNotBlank() }
    val englishName = english?.takeIf { it.isNotBlank() }
    val latinAlias = alternatives.firstOrNull { isLatinPersonName(it) }
    return when (titleLanguage) {
        "NATIVE", "JAPANESE_STAFF" -> native?.takeIf { it.isNotBlank() } ?: name
        "ENGLISH" -> englishName ?: romajiName ?: latinAlias ?: name
        else -> romajiName ?: englishName ?: latinAlias ?: name
    }
}

/** Harf içerir ve Japonca (kana/kanji) veya Korece (hangul) karakter içermez. */
internal fun isLatinPersonName(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    return text.any { it.isLetter() } && text.none { ch ->
        ch in '\u3040'..'\u30ff' || ch in '\u3400'..'\u9fff' || ch in '\uac00'..'\ud7af'
    }
}

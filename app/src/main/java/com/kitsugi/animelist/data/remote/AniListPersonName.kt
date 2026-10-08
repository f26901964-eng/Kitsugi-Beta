package com.kitsugi.animelist.data.remote

import org.json.JSONObject

/** AniList's userPreferred follows the API account, not Kitsugi's title-language setting. */
internal data class AniListPersonName(val preferred: String, val romanized: String?, val native: String?) {
    fun display(language: String): String = when (language) {
        "NATIVE", "JAPANESE_STAFF" -> native ?: preferred
        else -> romanized ?: preferred // ENGLISH and ROMAJI both use Latin person names
    }
}

internal fun JSONObject?.aniListPersonName(): AniListPersonName {
    val json = this ?: return AniListPersonName("Bilinmeyen", null, null)
    fun value(key: String) = json.optString(key).takeIf { it.isNotBlank() && it != "null" }
    fun latin(text: String?) = text?.takeIf { candidate ->
        candidate.any { it.isLetter() } && candidate.none { ch ->
            ch in '\u3040'..'\u30ff' || ch in '\u3400'..'\u9fff' || ch in '\uac00'..'\ud7af'
        }
    }
    val preferred = value("userPreferred") ?: value("full") ?: "Bilinmeyen"
    val parts = listOfNotNull(value("first"), value("middle"), value("last"))
    val composed = parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    val alternatives = json.optJSONArray("alternative")
    val alias = (0 until (alternatives?.length() ?: 0)).firstNotNullOfOrNull { i ->
        latin(alternatives?.optString(i))
    }
    return AniListPersonName(
        preferred = preferred,
        romanized = latin(value("full")) ?: latin(composed) ?: latin(preferred) ?: alias,
        native = value("native")
    )
}

/** Cached API models stay language-neutral; switching languages does not require a refetch. */
fun displayPersonName(name: String, romanized: String?, native: String?, language: String): String =
    if (language == "NATIVE" || language == "JAPANESE_STAFF") native?.takeIf { it.isNotBlank() } ?: name
    else romanized?.takeIf { it.isNotBlank() } ?: name

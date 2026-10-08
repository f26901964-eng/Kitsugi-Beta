package com.kitsugi.animelist.utils

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiCharacterMediaAppearance
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.R

object PreferenceHelpers {
    fun hasCjkCharacters(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.any {
            it.code in 0x3040..0x30ff || // Hiragana & Katakana
            it.code in 0x4e00..0x9fff || // CJK Unified Ideographs (Kanji)
            it.code in 0xac00..0xd7af || // Hangul (Korean)
            it.code in 0x31f0..0x31ff || // Katakana phonetic extensions
            it.code in 0xff00..0xffef    // Halfwidth and Fullwidth Forms
        }
    }

    fun getDisplayTitle(
        title: String,
        titleEnglish: String?,
        titleJapanese: String?,
        titleLanguage: String
    ): String {
        return when (titleLanguage) {
            "ENGLISH" -> {
                titleEnglish?.takeIf { it.isNotBlank() } ?: title
            }
            "NATIVE", "JAPANESE_STAFF" -> {
                titleJapanese?.takeIf { it.isNotBlank() } ?: title
            }
            else -> {
                // ROMAJI / varsayılan (Türkçe / Romaji): Eğer başlık Japonca/CJK karakter içeriyorsa ve İngilizce/Latin başlık varsa, Latin olanı önceliklendir
                if (hasCjkCharacters(title) && !titleEnglish.isNullOrBlank() && !hasCjkCharacters(titleEnglish)) {
                    titleEnglish
                } else {
                    title
                }
            }
        }
    }

    fun MediaEntry.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun JikanSearchResult.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: MediaTitleResolver.latin(titleJapanese)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiRelation.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(titleRomaji) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleRomaji)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiCharacterMediaAppearance.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiStaffMediaWork.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(mediaTitle, titleEnglish, titleJapanese, titleLanguage)
    }

    fun formatScore(score: Int?, scoreFormat: String, hideScores: Boolean): String {
        val context = KitsugiApplication.getInstance()
        if (hideScores) {
            return context?.getString(R.string.label_unknown) ?: "-"
        }
        if (score == null || score == 0) {
            return context?.getString(R.string.score_unrated) ?: "unrated"
        }
        return when (scoreFormat) {
            "POINT_100" -> "${score * 10}"
            "POINT_10_DECIMAL" -> "${score}.0/10"
            "POINT_5" -> {
                val fullStars = score / 2
                val hasHalf = (score % 2) != 0
                val emptyStars = 5 - fullStars - (if (hasHalf) 1 else 0)
                buildString {
                    repeat(fullStars) { append("★") }
                    if (hasHalf) append("½")
                    repeat(emptyStars) { append("☆") }
                }
            }
            "POINT_3" -> {
                when {
                    score >= 8 -> "😊"
                    score >= 5 -> "😐"
                    else -> "🙁"
                }
            }
            "STARS" -> {
                buildString {
                    repeat(score) { append("★") }
                    repeat(10 - score) { append("☆") }
                }
            }
            else -> "$score/10"
        }
    }

    fun MediaEntry.getDisplayScore(scoreFormat: String, hideScores: Boolean): String {
        return formatScore(score, scoreFormat, hideScores)
    }

    fun JikanSearchResult.getDisplayScore(scoreFormat: String, hideScores: Boolean): String {
        return formatScore(score, scoreFormat, hideScores)
    }
}

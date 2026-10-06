package com.kitsugi.animelist.utils

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiCharacterMediaAppearance
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.R

object PreferenceHelpers {
    /**
     * Metnin CJK (Çince/Japonca/Korece) karakter içerip içermediğini kontrol eder.
     * Kod noktaları (code point) üzerinden çalışır; böylece temel çokdilli düzlem
     * dışındaki CJK uzantıları (Ext B–F, tamamlayıcı ideograflar) da yakalanır.
     */
    fun hasCjkCharacters(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val isCjk = when (codePoint) {
                in 0x1100..0x11FF -> true   // Hangul Jamo
                in 0x2E80..0x2FDF -> true   // CJK Radicals Supplement / Kangxi Radicals
                in 0x3000..0x303F -> true   // CJK Symbols & Punctuation (、。〈〉「」『』・ー vb.)
                in 0x3040..0x30FF -> true   // Hiragana & Katakana
                in 0x3100..0x312F -> true   // Bopomofo
                in 0x3130..0x318F -> true   // Hangul Compatibility Jamo
                in 0x3190..0x319F -> true   // Kanbun
                in 0x31A0..0x31BF -> true   // Bopomofo Extended
                in 0x31F0..0x31FF -> true   // Katakana Phonetic Extensions
                in 0x3400..0x4DBF -> true   // CJK Unified Ideographs Ext A
                in 0x4E00..0x9FFF -> true   // CJK Unified Ideographs (Kanji/Hanzi)
                in 0xA960..0xA97F -> true   // Hangul Jamo Extended-A
                in 0xAC00..0xD7AF -> true   // Hangul Syllables (Korean)
                in 0xF900..0xFAFF -> true   // CJK Compatibility Ideographs
                in 0xFF00..0xFFEF -> true   // Halfwidth and Fullwidth Forms (＋ Japonca noktalama)
                in 0x20000..0x2EBEF -> true // CJK Ext B–F + Compatibility Supplement
                in 0x30000..0x323AF -> true // CJK Ext G–J
                else -> false
            }
            if (isCjk) return true
            i += Character.charCount(codePoint)
        }
        return false
    }

    /**
     * Başlık olarak doğrudan gösterilmeye uygun "Latin okunabilir" metin mi?
     * Boş olmayan ve CJK karakter içermeyen metinler (Türkçe, İngilizce, romaji,
     * Kiril, Arapça vb.) true döner. Japonca/Çince/Korece içeren metinler false döner.
     */
    fun isLatinReadable(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return !hasCjkCharacters(text)
    }

    /** Verilen adaylar arasından ilk Latin-okunabilir olanı döndürür (yoksa null). */
    fun firstLatinReadable(vararg candidates: String?): String? {
        return candidates.firstOrNull { isLatinReadable(it) }
    }

    /**
     * Kullanıcı açıkça CJK/yerel başlık dili seçtiyse true döner
     * (NATIVE / JAPANESE / CHINESE / KOREAN + varyantları).
     */
    fun isNativeTitleLanguage(titleLanguage: String): Boolean {
        return when (titleLanguage.trim().uppercase()) {
            "NATIVE", "JAPANESE", "JAPANESE_STAFF", "JA",
            "CHINESE", "CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL", "ZH",
            "KOREAN", "KO" -> true
            else -> false
        }
    }

    /**
     * Kullanıcının başlık dili tercihine göre görüntülenecek başlığı çözer.
     *
     * Düşüş zinciri (Japonca/Çince açıkça seçilmedikçe CJK gösterilmez):
     * - ENGLISH  → İngilizce (Latin) → yerelleştirilmiş başlık (Latin) → İngilizce → başlık
     * - NATIVE / JAPANESE / CHINESE (+ varyantları) → Japonca/Yerel → başlık
     * - ROMAJI / TURKISH / varsayılan → Türkçe/yerel (Latin) → İngilizce (Latin)
     *   → romaji (Latin) → İngilizce → Japonca → başlık
     *
     * Böylece Türkçe seçiliyken Türkçe çeviri yoksa İngilizce, o da yoksa romaji
     * gösterilir; Japonca/Çince ancak bu diller seçildiğinde (veya Latin alternatif
     * hiçbir yerde bulunamadığında son çare olarak) görünür.
     */
    fun getDisplayTitle(
        title: String,
        titleEnglish: String?,
        titleJapanese: String?,
        titleLanguage: String
    ): String {
        val lang = titleLanguage.trim().uppercase()
        val notBlankEnglish = titleEnglish?.takeIf { it.isNotBlank() }
        val notBlankJapanese = titleJapanese?.takeIf { it.isNotBlank() }
        return when (lang) {
            "ENGLISH" -> {
                firstLatinReadable(titleEnglish, title, titleJapanese)
                    ?: notBlankEnglish
                    ?: title.takeIf { it.isNotBlank() }
                    ?: notBlankJapanese
                    ?: title
            }
            "NATIVE", "JAPANESE", "JAPANESE_STAFF", "JA",
            "CHINESE", "CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL", "ZH",
            "KOREAN", "KO" -> {
                notBlankJapanese ?: title
            }
            else -> {
                // ROMAJI / TURKISH / TR / varsayılan:
                // yerelleştirilmiş (TR) → İngilizce → romaji; CJK yalnızca son çare.
                firstLatinReadable(title, titleEnglish, titleJapanese)
                    ?: notBlankEnglish
                    ?: notBlankJapanese
                    ?: title
            }
        }
    }

    /**
     * Aktivite/akış kartlarındaki medya başlığını kullanıcı tercihine göre çözer.
     * [getDisplayTitle] ile aynı düşüş zincirini kullanır; tek fark romaji alanının
     * açıkça ayrı tutulmasıdır (AniList aktivite modeli).
     */
    fun resolveActivityTitle(
        mediaTitleRomaji: String?,
        mediaTitleEnglish: String?,
        mediaTitleNative: String?,
        mediaTitle: String?,
        titleLanguage: String
    ): String? {
        val lang = titleLanguage.trim().uppercase()
        val notBlankRomaji = mediaTitleRomaji?.takeIf { it.isNotBlank() }
        val notBlankEnglish = mediaTitleEnglish?.takeIf { it.isNotBlank() }
        val notBlankNative = mediaTitleNative?.takeIf { it.isNotBlank() }
        val notBlankFallback = mediaTitle?.takeIf { it.isNotBlank() }
        return when (lang) {
            "ENGLISH" -> {
                firstLatinReadable(mediaTitleEnglish, mediaTitleRomaji, mediaTitle, mediaTitleNative)
                    ?: notBlankEnglish
                    ?: notBlankRomaji
                    ?: notBlankFallback
                    ?: notBlankNative
            }
            "NATIVE", "JAPANESE", "JAPANESE_STAFF", "JA",
            "CHINESE", "CHINESE_SIMPLIFIED", "CHINESE_TRADITIONAL", "ZH",
            "KOREAN", "KO" -> {
                notBlankNative ?: notBlankRomaji ?: notBlankEnglish ?: notBlankFallback
            }
            else -> {
                firstLatinReadable(mediaTitleRomaji, mediaTitleEnglish, mediaTitle, mediaTitleNative)
                    ?: notBlankRomaji
                    ?: notBlankEnglish
                    ?: notBlankFallback
                    ?: notBlankNative
            }
        }
    }

    fun MediaEntry.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun JikanSearchResult.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiRelation.getDisplayTitle(titleLanguage: String): String {
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

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
            it.code in 0x4e00..0x9fff || // CJK Unified Ideographs (Kanji / Çince)
            it.code in 0x3400..0x4dbf || // CJK Extension A
            it.code in 0xac00..0xd7af || // Hangul (Korean)
            it.code in 0x31f0..0x31ff || // Katakana phonetic extensions
            it.code in 0xff00..0xffef    // Halfwidth and Fullwidth Forms
        }
    }

    /**
     * Kullanıcı açıkça Japonca/Çince başlık istedi mi?
     * Anime başlık dili NATIVE/JAPANESE_STAFF ise CJK gösterimine izin verilir.
     */
    fun isNativeTitleRequested(titleLanguage: String): Boolean {
        return titleLanguage == "NATIVE" || titleLanguage == "JAPANESE_STAFF"
    }

    /** TMDB dizi/film dili Japonca veya Çince mi? (CJK gösterimine izin verilir) */
    fun isCjkTmdbLanguage(tmdbLanguage: String?): Boolean {
        if (tmdbLanguage.isNullOrBlank()) return false
        val code = tmdbLanguage.trim().lowercase()
        return code.startsWith("ja") || code.startsWith("zh") || code.startsWith("cn")
    }

    private fun String?.cleanTitle(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    /**
     * Merkezi başlık çözümleme zinciri:
     *  - NATIVE / JAPANESE_STAFF seçiliyse → native (Japonca/Çince) öncelikli.
     *  - ENGLISH seçiliyse → İngilizce (Latin) → Romaji (Latin) → eldeki ilk başlık.
     *  - ROMAJI / TURKISH / varsayılan → Türkçe→İngilizce→Romaji zinciri:
     *    Latin başlık ( Türkçe / Romaji ) → Latin İngilizce → CJK son çare.
     *
     * KURAL: Japonca veya Çince açıkça seçilmedikçe, Latin alternatifi varken
     * ASLA CJK (Japonca/Çince/Korece) başlık döndürülmez.
     */
    fun getDisplayTitle(
        title: String,
        titleEnglish: String?,
        titleJapanese: String?,
        titleLanguage: String
    ): String {
        val romaji = title.cleanTitle().orEmpty()
        val romajiLatin = romaji.takeIf { it.isNotBlank() && !hasCjkCharacters(it) }
        val english = titleEnglish.cleanTitle()
        val englishLatin = english?.takeIf { !hasCjkCharacters(it) }
        val native = titleJapanese.cleanTitle()

        return when {
            isNativeTitleRequested(titleLanguage) -> {
                native ?: romajiLatin ?: englishLatin ?: romaji.ifBlank { english ?: native.orEmpty() }
            }
            titleLanguage == "ENGLISH" -> {
                englishLatin ?: romajiLatin ?: english ?: romaji.ifBlank { native.orEmpty() }
            }
            else -> {
                // ROMAJI / TURKISH / varsayılan
                romajiLatin ?: englishLatin ?: romaji.ifBlank { english ?: native.orEmpty() }
            }
        }
    }

    fun MediaEntry.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    /**
     * TMDB kaynaklı öğelerde `title` alanı, TMDB istek anında kullanıcının dizi/film
     * diline (tmdbLanguage) göre zaten çözümlenmiştir (TR → EN → orijinal zinciri).
     * Bu yüzden ROMAJI/varsayılan modda fetch çözümüne dokunulmaz; böylece dizi/film
     * dili Japonca/Çince seçilmişse CJK korunur, Türkçe seçilmişse Türkçe/İngilizce
     * zinciri aynen gösterilir. ENGLISH/NATIVE istekleri ise varyantlara uygulanır.
     */
    fun JikanSearchResult.getDisplayTitle(titleLanguage: String): String {
        if (source.equals("tmdb", ignoreCase = true) &&
            !isNativeTitleRequested(titleLanguage) && titleLanguage != "ENGLISH"
        ) {
            return title
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiRelation.getDisplayTitle(titleLanguage: String): String {
        if (source.equals("tmdb", ignoreCase = true) &&
            !isNativeTitleRequested(titleLanguage) && titleLanguage != "ENGLISH"
        ) {
            return title
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiCharacterMediaAppearance.getDisplayTitle(titleLanguage: String): String {
        if (source.equals("tmdb", ignoreCase = true) &&
            !isNativeTitleRequested(titleLanguage) && titleLanguage != "ENGLISH"
        ) {
            return title
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
    }

    fun KitsugiStaffMediaWork.getDisplayTitle(titleLanguage: String): String {
        if (source.equals("tmdb", ignoreCase = true) &&
            !isNativeTitleRequested(titleLanguage) && titleLanguage != "ENGLISH"
        ) {
            return mediaTitle
        }
        return PreferenceHelpers.getDisplayTitle(mediaTitle, titleEnglish, titleJapanese, titleLanguage)
    }

    // ─── TMDB başlık çözümleme (fetch anında) ────────────────────────────────

    /**
     * TMDB'den gelen başlık adaylarını tek zincirde çözer:
     *  1. İstenen dildeki başlık (örn. Türkçe) — Latin ise aynen.
     *  2. İngilizce başlık (en-US) — Latin ise.
     *  3. Orijinal başlık — Latin ise (örn. orijinali İngilizce içerikler).
     *  4. Son çare: eldeki ilk boş-olmayan başlık (CJK dahil).
     *
     * İstisna: istenen dil Japonca/Çince ise (kullanıcı açıkça seçmiş),
     * yerelleştirilmiş/CJK başlık korunur.
     */
    data class TmdbResolvedTitles(
        val displayTitle: String,
        val titleEnglish: String?,
        val titleJapanese: String?
    )

    fun resolveTmdbTitles(
        localizedTitle: String?,
        englishTitle: String?,
        originalTitle: String?,
        originalLanguage: String?,
        requestedLanguage: String
    ): TmdbResolvedTitles {
        val loc = localizedTitle.cleanTitle()
        val locLatin = loc?.takeIf { !hasCjkCharacters(it) }
        val en = englishTitle.cleanTitle()
        val enLatin = en?.takeIf { !hasCjkCharacters(it) }
        val orig = originalTitle.cleanTitle()
        val origLatin = orig?.takeIf { !hasCjkCharacters(it) }
        val origLang = originalLanguage?.trim()?.lowercase().orEmpty()

        // İngilizce varyant: en-US başlığı Latin ise o; orijinal dil İngilizce ise orijinal.
        val resolvedEnglish: String? = enLatin
            ?: if (origLang == "en" || origLang.startsWith("en-")) origLatin ?: orig else null
            ?: if (requestedLanguage.trim().lowercase().startsWith("en")) locLatin else null

        // Native/CJK varyantı: orijinalde CJK varsa orijinal, yoksa CJK içeren yerelleştirilmiş başlık.
        val resolvedJapanese: String? = when {
            orig != null && hasCjkCharacters(orig) -> orig
            loc != null && hasCjkCharacters(loc) -> loc
            origLang == "ja" || origLang == "zh" || origLang == "cn" || origLang == "ko" -> orig
            else -> null
        }

        val display: String = if (isCjkTmdbLanguage(requestedLanguage)) {
            // Kullanıcı açıkça Japonca/Çince istedi → TMDB'nin döndürdüğünü koru.
            loc ?: orig ?: en ?: ""
        } else {
            // Türkçe (veya diğer Latin diller): TR → EN → orijinal(Latin) → son çare.
            locLatin ?: enLatin ?: origLatin ?: loc ?: orig ?: en ?: ""
        }

        return TmdbResolvedTitles(
            displayTitle = display,
            titleEnglish = resolvedEnglish,
            titleJapanese = resolvedJapanese
        )
    }

    // ─── Başlık eşleştirme (bildirim / takvim için fuzzy fallback) ───────────

    /** Başlığı karşılaştırılabilir forma indirger: küçük harf, noktalamasız, tek boşluklu. */
    fun normalizeTitleForMatch(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * İki başlığın aynı içeriğe ait olup olmadığını kabaca belirler.
     * ID eşleşmesi yokken (örn. AniList idMal boşken) son çare olarak kullanılır.
     */
    fun titlesRoughlyMatch(a: String?, b: String?): Boolean {
        return normalizedTitlesMatch(normalizeTitleForMatch(a), normalizeTitleForMatch(b))
    }

    /**
     * Önceden [normalizeTitleForMatch] ile normalize edilmiş iki başlığı karşılaştırır.
     * Toplu eşleştirmede (bildirim işçisi) normalizasyon maliyetini teke indirir.
     */
    fun normalizedTitlesMatch(na: String, nb: String): Boolean {
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        // Biri diğerini içeriyorsa (sezon/part ekleri vb.) — kısa başlıklarda
        // yanlış pozitifleri önlemek için minimum uzunluk şartı aranır.
        val (short, long) = if (na.length <= nb.length) na to nb else nb to na
        if (short.length < 10) return false
        return long.contains(short)
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

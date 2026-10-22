package com.kitsugi.animelist.utils

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiCharacterMediaAppearance
import com.kitsugi.animelist.data.remote.KitsugiStaffMediaWork
import com.kitsugi.animelist.data.remote.KitsugiStaffCharacterRole
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

    /**
     * Bir karakterin Latin alfabesine ait olup olmadığını kontrol eder (Temel Latin,
     * Latin-1 Supplement, Latin Extended A/B/Additional — Türkçe "İ/ı/ğ/ş/ü/ö/ç" ve diğer
     * Avrupa dillerindeki aksanlı harfler dahil).
     */
    private fun isLatinLetter(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0041..0x005A || // A-Z
            code in 0x0061..0x007A ||    // a-z
            code in 0x00C0..0x024F ||    // Latin-1 Supplement + Latin Extended A/B
            code in 0x1E00..0x1EFF ||    // Latin Extended Additional (Vietnamese vb.)
            code in 0x2C60..0x2C7F       // Latin Extended C
    }

    /**
     * Bir metnin GERÇEKTEN Latin alfabesiyle yazılmış olup olmadığını kontrol eder.
     *
     * Eskiden "Latin ad" seçimi `!hasCjkCharacters(text)` şartıyla yapılıyordu; bu, CJK
     * (Japonca/Çince/Korece) OLMAYAN her şeyi (Arapça, Tayca, Kiril, İbranice, Devanagari…)
     * yanlışlıkla "Latin" sayıyordu. Örnek: TMDB `also_known_as` listesinde bir kişinin
     * Arapça ("اری کیتامورا") veya Tayca ("ไอ โนะนะกะ") çevirisi varsa, bu fonksiyon
     * olmadan romaji/İngilizce ad yerine o seçiliyordu. Artık yalnızca harflerinin TAMAMI
     * Latin alfabesinde olan adlar "Latin" kabul edilir.
     */
    fun isLatinText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.all { isLatinLetter(it) }
    }

    /**
     * Başlık dili seçimini tüm kaynaklar için tek yerde uygular.
     *
     * `titleRomaji`, Bangumi gibi English ve Latin özgün başlığın farklı gelebildiği
     * kaynaklarda zorunludur. Alan mevcut değilse eski model davranışı korunur.
     */
    fun getDisplayTitle(
        title: String,
        titleEnglish: String?,
        titleJapanese: String?,
        titleLanguage: String,
        titleRomaji: String? = null
    ): String {
        fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
        val english = nonBlank(titleEnglish)
        val native = nonBlank(titleJapanese)
        val romaji = nonBlank(titleRomaji)
        val raw = nonBlank(title)
        return when (titleLanguage) {
            "ENGLISH" -> english ?: romaji ?: raw ?: native ?: ""
            "NATIVE", "JAPANESE_STAFF" -> native ?: raw ?: romaji ?: english ?: ""
            else -> {
                // ROMAJI: Latin özgün ad varsa onu kullan. Kaynak romaji sağlamıyorsa
                // İngilizce Latin ad güvenli geri dönüş olur; uydurma transliterasyon yapılmaz.
                romaji
                    ?: raw?.takeIf { !hasCjkCharacters(it) }
                    ?: english?.takeIf { !hasCjkCharacters(it) }
                    ?: raw
                    ?: native
                    ?: ""
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
        val resolved = PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage)
        return bangumiCachedTitleOrNull(titleLanguage, resolved) ?: resolved
    }

    /**
     * Bangumi kaynaklı kayıtlar için önbellekten dile uygun ad.
     *
     * Bangumi'nin koleksiyon/liste uçları YALNIZCA özgün adı (`name`, çoğu zaman Japonca)
     * döndürür: içe aktarılan satırlarda `titleEnglish` boş kalır. Bu yüzden "Listem" ve
     * benzeri listeler, kullanıcı Romaji/İngilizce başlık dilini seçse bile CJK adla
     * çiziliyordu. Latin adlar (romaji/İngilizce) detay sayfası, keşfet veya
     * [com.kitsugi.animelist.data.remote.BangumiLibraryTitleHydrator] tarafından öğrenilir
     * ve [BangumiTitleCache]'e yazılır; burada yalnızca bellek içi önbellek okunur —
     * ekran bekletilmez, ağ isteği yapılmaz.
     *
     * @param resolved mevcut alanlardan çözülen ad. Seçili dili DB alanları zaten
     *        karşılıyorsa önbelleğe hiç bakılmaz (önbellek yalnızca eksiği tamamlar):
     *        `ENGLISH` için karşılama ölçütü, gösterimin gerçek (Latin) `titleEnglish`
     *        alanından gelmesidir — romaji yedeği gösteriliyorsa önbellekteki ayrı
     *        İngilizce ad kazanır. Diğer Latin dillerde (ROMAJI) Latin gösterim yeterlidir.
     */
    private fun MediaEntry.bangumiCachedTitleOrNull(titleLanguage: String, resolved: String): String? {
        val wantsLatin = !titleLanguage.equals("NATIVE", ignoreCase = true) &&
            !titleLanguage.equals("JAPANESE_STAFF", ignoreCase = true)
        if (!wantsLatin) return null
        val satisfiedByFields = if (titleLanguage.equals("ENGLISH", ignoreCase = true)) {
            !titleEnglish.isNullOrBlank() && isLatinText(titleEnglish)
        } else {
            resolved.isNotBlank() && !hasCjkCharacters(resolved)
        }
        if (satisfiedByFields) return null
        if (!BangumiIdNamespace.SOURCE.equals(source.trim(), ignoreCase = true)) return null
        val rawId = BangumiIdNamespace.rawIdOrNull(malId) ?: return null
        val cached = BangumiTitleCache.localizedTitleFor(rawId, title, titleLanguage) ?: return null
        // Önbellek adayı hâlihazırda gösterilen adın aynısıysa (örn. romaji) dokunma.
        return cached.takeIf { !it.equals(resolved, ignoreCase = true) }
    }

    fun JikanSearchResult.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(titleRomaji) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(titleRomaji)
                    ?: MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: MediaTitleResolver.latin(titleJapanese)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiRelation.getDisplayTitle(titleLanguage: String): String {
        if (MediaTitleResolver.isLatinPreferredSource(source)) {
            return when (titleLanguage) {
                "NATIVE", "JAPANESE_STAFF" -> titleJapanese?.takeIf { it.isNotBlank() } ?: title
                "ENGLISH" -> MediaTitleResolver.latin(titleEnglish) ?: MediaTitleResolver.latin(titleRomaji) ?: MediaTitleResolver.latin(title) ?: title
                else -> MediaTitleResolver.latin(titleRomaji)
                    ?: MediaTitleResolver.latin(title)
                    ?: MediaTitleResolver.latin(titleEnglish)
                    ?: title
            }
        }
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiCharacterMediaAppearance.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(title, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiStaffMediaWork.getDisplayTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(mediaTitle, titleEnglish, titleJapanese, titleLanguage, titleRomaji)
    }

    fun KitsugiStaffCharacterRole.getDisplayMediaTitle(titleLanguage: String): String {
        return PreferenceHelpers.getDisplayTitle(
            mediaTitle,
            mediaTitleEnglish,
            mediaTitleJapanese,
            titleLanguage,
            mediaTitleRomaji
        )
    }

    fun formatScore(
        score: Int?,
        scoreFormat: String,
        hideScores: Boolean,
        rawScoreDouble: Double? = null
    ): String {
        val context = KitsugiApplication.getInstance()
        if (hideScores) {
            return context?.getString(R.string.label_unknown) ?: "-"
        }
        val decimalScore = rawScoreDouble?.takeIf { !it.isNaN() && it > 0.0 }?.let {
            if (it > 10.0) (it / 10.0).coerceIn(0.1, 10.0) else it.coerceIn(0.1, 10.0)
        }
        val effectiveScore10 = when {
            score != null && score > 10 -> kotlin.math.round(score / 10.0).toInt().coerceIn(1, 10)
            score != null && score > 0 -> score.coerceIn(1, 10)
            decimalScore != null -> kotlin.math.round(decimalScore).toInt().coerceIn(1, 10)
            else -> null
        }
        if (effectiveScore10 == null || effectiveScore10 == 0) {
            return context?.getString(R.string.score_unrated) ?: "unrated"
        }
        val effectiveScore100 = when {
            decimalScore != null -> kotlin.math.round(decimalScore * 10.0).toInt().coerceIn(1, 100)
            score != null && score > 10 -> score.coerceIn(1, 100)
            else -> (effectiveScore10 * 10).coerceIn(1, 100)
        }
        return when (scoreFormat) {
            "POINT_100" -> "$effectiveScore100"
            "POINT_10_DECIMAL" -> {
                val displayDecimal = decimalScore
                    ?: if (score != null && score > 10) (score / 10.0) else effectiveScore10.toDouble()
                String.format(java.util.Locale.US, "%.1f/10", displayDecimal)
            }
            "POINT_5" -> {
                val fullStars = (effectiveScore10 / 2).coerceIn(0, 5)
                val hasHalf = (effectiveScore10 % 2) != 0 && fullStars < 5
                val emptyStars = (5 - fullStars - (if (hasHalf) 1 else 0)).coerceAtLeast(0)
                buildString {
                    repeat(fullStars) { append("★") }
                    if (hasHalf) append("½")
                    repeat(emptyStars) { append("☆") }
                }
            }
            "POINT_3" -> {
                when {
                    effectiveScore10 >= 8 -> "😊"
                    effectiveScore10 >= 5 -> "😐"
                    else -> "🙁"
                }
            }
            "STARS" -> {
                buildString {
                    repeat(effectiveScore10) { append("★") }
                    repeat((10 - effectiveScore10).coerceAtLeast(0)) { append("☆") }
                }
            }
            else -> "$effectiveScore10/10"
        }
    }

    fun MediaEntry.getDisplayScore(scoreFormat: String, hideScores: Boolean): String {
        return formatScore(score, scoreFormat, hideScores)
    }

    fun JikanSearchResult.getDisplayScore(scoreFormat: String, hideScores: Boolean): String {
        return formatScore(score, scoreFormat, hideScores, rawScoreDouble)
    }
}

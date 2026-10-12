package com.kitsugi.animelist.data.fandom

import java.text.Normalizer
import java.util.Locale

/**
 * Başlık ve karakter adı normalizasyonu / varyant üretimi (saf fonksiyonlar).
 *
 * İsim yolu yalnızca son çaredir ve her zaman kanıtla doğrulanır; buradaki varyantlar
 * birebir başlık sorgusu (MediaWiki `titles=` + yönlendirmeler) için kullanılır,
 * bulanık bir "benzerlik" kabulü yapılmaz.
 */
object FandomNameVariants {

    private val LATIN_COMBINING = Regex("[\\u0300-\\u036F]+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
    private val POSSESSIVE = Regex("(?i)['’]s\\b")
    private val APOSTROPHES = Regex("['’`´]")

    /** Karşılaştırmada anlam taşımayan kelimeler (wiki adlarındaki "Wiki" vb.). */
    private val STOP_TOKENS = setOf(
        "the", "wiki", "wikia", "fandom", "encyclopedia", "database", "db",
        "official", "unofficial", "community"
    )

    // ── Latin metin normalizasyonu ───────────────────────────────────────────

    /**
     * Latin aksanlarını kaldırır (`Kenzō` → `Kenzo`, `é` → `e`). Japonca dakuten gibi
     * Latin dışı birleşik işaretlere dokunmaz.
     */
    fun foldDiacritics(input: String): String {
        val replaced = input
            .replace('ı', 'i').replace('İ', 'I')
            .replace("ß", "ss")
            .replace('ø', 'o').replace('Ø', 'O')
            .replace('ł', 'l').replace('Ł', 'L')
            .replace('đ', 'd').replace('Đ', 'D')
            .replace("æ", "ae").replace("Æ", "AE")
        val decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD).replace(LATIN_COMBINING, "")
        return Normalizer.normalize(decomposed, Normalizer.Form.NFC)
    }

    /** Küçük harf, aksansız, noktalamasız, tek boşluklu karşılaştırma biçimi. */
    fun normalizeForCompare(input: String): String {
        val noPossessive = input.replace(POSSESSIVE, "")
        val folded = foldDiacritics(noPossessive)
            .replace("×", " x ")
            .replace("✕", " x ")
            .replace("&", " and ")
            .replace(APOSTROPHES, "")
        return folded.lowercase(Locale.ROOT)
            .replace(NON_ALNUM, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }

    fun tokens(input: String, dropStopWords: Boolean = true): List<String> =
        normalizeForCompare(input)
            .split(' ')
            .filter { it.isNotEmpty() && (!dropStopWords || it !in STOP_TOKENS) }

    /** Harflerin en az yarısı Latin alfabesindeyse true (arama sorgusu için uygun başlık). */
    fun isMostlyLatin(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        val latin = letters.count { it.code < 0x0250 }
        return latin * 2 >= letters.length
    }

    // ── Başlık varyantları ───────────────────────────────────────────────────

    private val SEASON_PATTERNS = listOf(
        Regex("(?i)\\b(season|saison|temporada|staffel)\\s*\\d+\\b"),
        Regex("(?i)\\b\\d+(st|nd|rd|th)\\s+(season|cour|part)\\b"),
        Regex("(?i)\\b(part|cour)\\s*(\\d+|i{1,3}|iv|v)\\b"),
        Regex("(?i)\\b(the\\s+)?(final|last)\\s+(season|chapters?)\\b"),
        Regex("(?i)\\b(the\\s+)?movie\\b"),
        Regex("(?i)\\b(ova|ona|oad|specials?|tv)\\b"),
        Regex("\\(\\s*\\d{4}\\s*\\)"),
        Regex("(?i)\\s+(ii|iii|iv|v|vi|vii|viii|ix|x)$")
    )

    /** "Shingeki no Kyojin Season 3 Part 2" → "Shingeki no Kyojin". */
    fun stripSeasonMarkers(title: String): String {
        var text = title
        SEASON_PATTERNS.forEach { pattern -> text = pattern.replace(text, " ") }
        return text.replace(WHITESPACE, " ").trim().trim(':', '-', '–', '—', ',', '.', ' ', '(', ')')
    }

    /**
     * Arama için başlık varyantları (yalnızca Latin başlıklar; sıra korunur):
     * sezon eki ayıklanmış hâl, `:` öncesi kök başlık, `:` sonrası alt başlık, özgün hâl.
     */
    fun baseTitleVariants(titles: List<String>, max: Int = 4): List<String> {
        val out = LinkedHashMap<String, String>()
        fun add(candidate: String?) {
            val text = candidate?.replace(WHITESPACE, " ")?.trim()?.trim(':', '-', ' ') ?: return
            if (text.length < 2 || text.none { it.isLetterOrDigit() }) return
            val key = normalizeForCompare(text)
            if (key.isNotEmpty() && key !in out) out[key] = text
        }
        titles.forEach { raw ->
            if (!isMostlyLatin(raw)) return@forEach
            val stripped = stripSeasonMarkers(raw)
            add(stripped)
            val colon = stripped.indexOf(": ")
            if (colon > 0) {
                add(stripped.substring(0, colon))
                val after = stripped.substring(colon + 2)
                if (after.trim().split(' ').size >= 2) add(after)
            }
            add(raw)
        }
        return out.values.take(max)
    }

    private val SLUG_OK = Regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$")

    /** Başlıktan olası Fandom alt alan adları: `spy-x-family`, `spyxfamily`… */
    fun slugCandidates(titles: List<String>, max: Int = 6): List<String> {
        val out = LinkedHashSet<String>()
        baseTitleVariants(titles, max = 4).forEach { title ->
            val parts = normalizeForCompare(title).split(' ').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return@forEach
            listOf(parts.joinToString("-"), parts.joinToString("")).forEach { slug ->
                if (slug.length in 3..60 && SLUG_OK.matches(slug)) out += slug
            }
        }
        return out.take(max)
    }

    // ── Karakter adı varyantları ─────────────────────────────────────────────

    /**
     * MediaWiki `titles=` sorgusu için karakter adı varyantları (birebir aranır), öncelik
     * sırasıyla: özgün ad + ters ad sırası (Japon/Kore ↔ Batı), tire birleştirme
     * (`Jin-Woo` → `Jinwoo`), aksansız yazım (`Gojō` → `Gojo`), uzun ünlü sadeleştirme
     * (`Kenzou` → `Kenzo`) ve alternatif adlar.
     */
    fun characterTitleVariants(fullName: String?, alternatives: List<String> = emptyList(), max: Int = 6): List<String> {
        val base = fullName?.replace(WHITESPACE, " ")?.trim().orEmpty()
        val out = LinkedHashSet<String>()
        fun add(candidate: String?) {
            val text = candidate?.replace(WHITESPACE, " ")?.trim() ?: return
            if (text.length in 2..80) out += text
        }
        fun addWithReversed(name: String) {
            add(name)
            val parts = name.split(' ')
            if (parts.size == 2) add("${parts[1]} ${parts[0]}")
        }
        if (base.isNotEmpty()) {
            addWithReversed(base)
            if (base.contains('-')) addWithReversed(collapseHyphens(base))
            val folded = foldDiacritics(base)
            if (folded != base) addWithReversed(folded)
            val simplified = simplifyLongVowels(base)
            if (simplified != base) addWithReversed(simplified)
        }
        alternatives.take(2).forEach { add(it) }
        return out.take(max)
    }

    /** "Sung Jin-Woo" → "Sung Jinwoo". */
    fun collapseHyphens(name: String): String =
        name.split(' ').joinToString(" ") { part ->
            val pieces = part.split('-').filter { it.isNotEmpty() }
            if (pieces.size <= 1) part
            else pieces.first() + pieces.drop(1).joinToString("") { it.lowercase(Locale.ROOT) }
        }

    /**
     * Hepburn uzun ünlülerini sadeleştirir: `Kenzou` → `Kenzo`, `Ryuuko` → `Ryuko`.
     * `oo` bilinçli olarak dokunulmaz (Korece `Woo`, İngilizce `Moon` bozulmasın).
     */
    fun simplifyLongVowels(name: String): String =
        name.replace("ou", "o").replace("Ou", "O")
            .replace("uu", "u").replace("Uu", "U")

    // ── Yerel (Japonca/Korece/Çince) ad doğrulaması ──────────────────────────

    /** "五 (ご) 条 (じょう)" gibi okunuş (furigana) parantezleri. */
    private val KANA_RUBY = Regex("[（(]\\s*[\\u3040-\\u30FF\\s]+\\s*[）)]")
    private val NATIVE_SEPARATORS = Regex("[\\s\\u3000・･·‧•=＝\"'「」『』]+")

    fun squashNative(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKC).replace(NATIVE_SEPARATORS, "")

    fun hasCjkOrHangul(text: String): Boolean = text.any { ch ->
        val code = ch.code
        code in 0x3040..0x30FF || code in 0x3400..0x9FFF || code in 0xF900..0xFAFF ||
            code in 0xAC00..0xD7AF || code in 0x1100..0x11FF || code in 0x3130..0x318F
    }

    /**
     * Kaynaktaki yerel ad (ör. AniList `native`: `五条悟`, `성진우`) wiki metninde geçiyor mu?
     * Boşluk/orta nokta farkları ve okunuş parantezleri yok sayılır. Tek karakterli veya
     * CJK/Hangul içermeyen adlar kanıt sayılmaz.
     */
    fun nativeAppearsIn(native: String?, haystack: String?): Boolean {
        if (native.isNullOrBlank() || haystack.isNullOrBlank()) return false
        val needle = squashNative(KANA_RUBY.replace(native, ""))
        if (needle.length < 2 || !hasCjkOrHangul(needle)) return false
        if (squashNative(haystack).contains(needle)) return true
        return squashNative(KANA_RUBY.replace(haystack, "")).contains(needle)
    }

    private val SPACE_BETWEEN_CJK = Regex("(?<=[\\u3040-\\u30FF\\u3400-\\u9FFF])[ \\t]+(?=[\\u3040-\\u30FF\\u3400-\\u9FFF])")

    /** Wiki açıklamasındaki okunuş parantezlerini ve `?)` kalıntısını temizler. */
    fun cleanDescription(text: String): String =
        KANA_RUBY.replace(text, "")
            .replace(Regex("\\s*\\?\\s*\\)"), ")")
            .replace(Regex("\\s+,"), ",")
            .replace(Regex("\\(\\s+"), "(")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(SPACE_BETWEEN_CJK, "")
            .trim()
}

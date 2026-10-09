package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.data.remote.JikanSearchResult
import java.util.Locale

/**
 * Sorgu ↔ sonuç alakalılığı ve kimlik tekilleştirme yardımcıları.
 *
 * İki ayrı sorunu çözer:
 *
 * 1. **Alakasız sonuçların üste çıkması.** Bazı kaynaklar (özellikle yedek/legacy
 *    uçlar) sorguyla eşleşmeyen popüler kayıtlar döndürebiliyor. Kaynağın kendi
 *    eşleşme semantiğini (eş anlamlılar, takma adlar, romanaji ↔ İngilizce)
 *    bozmamak için sonuç SİLİNMEZ; sorguyla gerçekten örtüşenler rafın başına
 *    taşınır, hiç örtüşmeyenler sona itilir. Yatay raflarda ilk kartlar göründüğü
 *    için kullanıcı artık önce alakalı kayıtları görür.
 *
 * 2. **Anime/manga (ve film/dizi) kimlik çakışması.** Kitsu, Shikimori, MAL ve
 *    TMDB'de anime ile manga (ya da film ile dizi) AYRI kimlik uzayları kullanır:
 *    `kitsu anime#1` ile `kitsu manga#1` farklı kayıtlardır. Yalnızca
 *    `kaynak_kimlik` ile tekilleştirme, karma (anime + manga) aramada aynı numaraya
 *    denk gelen meşru kayıtlardan birini sessizce siliyordu. Bu yüzden anahtar
 *    medya türünü de içerir.
 */
internal object SearchRelevance {

    // ── Sabitler (nesne başlatma sırası için fonksiyonlardan ÖNCE tanımlı) ────

    private val WHITESPACE = Regex("\\s+")

    /** Birleşik aksan aralığı (Combining Diacritical Marks). */
    private val COMBINING_MARKS = '\u0300'..'\u036F'

    private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "la", "le", "de")

    private val FOLD_MAP: Map<Char, String> = mapOf(
        // Türkçe
        'ı' to "i", 'ş' to "s", 'ğ' to "g", 'ü' to "u", 'ö' to "o", 'ç' to "c",
        // Latin aksanları
        'â' to "a", 'î' to "i", 'û' to "u", 'é' to "e", 'è' to "e", 'ê' to "e",
        'á' to "a", 'à' to "a", 'ã' to "a", 'å' to "a", 'ä' to "a", 'æ' to "ae",
        'ó' to "o", 'ò' to "o", 'õ' to "o", 'ø' to "o", 'ú' to "u", 'ù' to "u",
        'ñ' to "n", 'ć' to "c", 'ł' to "l", 'ř' to "r", 'ž' to "z", 'š' to "s",
        'đ' to "d", 'ß' to "ss", 'ō' to "o", 'ū' to "u", 'ā' to "a", 'ē' to "e",
        // Japonca uzun ünlü / ayraç işaretleri boşluğa iner
        'ī' to "i", '・' to " ", 'ー' to " ", '〜' to " ", '～' to " "
    )

    // ── Kimlik ───────────────────────────────────────────────────────────────

    /** Sonucun benzersiz anahtarı: kaynak + medya türü + kimlik. */
    fun keyOf(result: JikanSearchResult): String =
        "${result.source.lowercase(Locale.ROOT)}_${result.type.name.lowercase(Locale.ROOT)}_${result.malId}"

    /** Aynı kaynağın aynı türdeki aynı kimliğini tek kayda indirir (sıra korunur). */
    fun dedupe(results: List<JikanSearchResult>): List<JikanSearchResult> =
        results.distinctBy { keyOf(it) }

    /** Alakalılık sıralaması + tekilleştirme (tek çağrıda). */
    fun refine(query: String, results: List<JikanSearchResult>): List<JikanSearchResult> =
        dedupe(rank(query, results))

    // ── Karma kapsam birleştirme ─────────────────────────────────────────────

    /**
     * İki listeyi dönüşümlü (a1, b1, a2, b2, ...) birleştirir.
     *
     * Karma (anime + manga) aramada iki uç art arda eklenip sonra `take(10)`
     * uygulanıyordu; anime listesi dolu geldiğinde manga kayıtları kesilip
     * atılıyordu (örn. "The Greatest Estate Developer" manhwa'sı Tümü rafında hiç
     * görünmüyordu). Dönüşümlü birleştirme iki aileye de eşit yer ayırır.
     */
    fun interleave(
        first: List<JikanSearchResult>,
        second: List<JikanSearchResult>
    ): List<JikanSearchResult> {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        val merged = ArrayList<JikanSearchResult>(first.size + second.size)
        var i = 0
        var j = 0
        while (i < first.size || j < second.size) {
            if (i < first.size) merged.add(first[i++])
            if (j < second.size) merged.add(second[j++])
        }
        return merged
    }

    // ── Alakalılık ───────────────────────────────────────────────────────────

    /**
     * Sonuçları sorguyla örtüşme derecesine göre öne alır.
     *
     * Kotlin'in `sortedByDescending`'i kararlı (stable) olduğu için aynı kademede
     * kalan kayıtlar kaynağın kendi sıralamasını korur — yani kaynağın alakalılık
     * sıralaması bozulmaz, yalnızca hiç örtüşmeyen kuyruk aşağı iner.
     *
     * Sorgu boşsa (yalnızca filtreyle yapılan keşif aramaları) liste aynen döner.
     */
    fun rank(query: String, results: List<JikanSearchResult>): List<JikanSearchResult> {
        if (results.size < 2) return results
        val probe = Probe.of(query) ?: return results
        return results.sortedByDescending { probe.tierOf(it) }
    }

    /**
     * Karşılaştırmayı engelleyen aksan/çeviri yazısı farklarını indirger.
     *
     * `Locale.ROOT` ile küçültülür: Türkçe yerel ayarında `"I".lowercase()` `"ı"`
     * üretir ve İngilizce başlıklarla eşleşmeyi bozardı.
     */
    fun fold(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text.lowercase(Locale.ROOT)) {
            val mapped = FOLD_MAP[ch]
            when {
                mapped != null -> builder.append(mapped)
                // Birleşik aksanlar yok sayılır. 'İ'.lowercase() "i" + U+0307 üretir;
                // bu işaret boşluğa dönseydi "İstanbul" → "i stanbul" olur ve
                // "istanbul" sorgusuyla eşleşmezdi.
                ch in COMBINING_MARKS -> Unit
                ch.isLetterOrDigit() -> builder.append(ch)
                else -> builder.append(' ')
            }
        }
        return builder.toString().trim().replace(WHITESPACE, " ")
    }

    /**
     * Anlamlı sorgu kelimeleri. Artikel/bağlaçlar yalnızca sorgu birden fazla
     * kelime içeriyorsa elenir; tek kelimelik sorguda hiçbir şey silinmez
     * (örn. "The" araması meşrudur).
     */
    private fun meaningfulTokens(folded: String): List<String> {
        val all = folded.split(' ').filter { it.length >= 2 }
        if (all.size <= 1) return all
        val trimmed = all.filterNot { it in STOP_WORDS }
        return trimmed.ifEmpty { all }
    }

    /**
     * Bir sorgunun karşılaştırma biçimleri. `of` null dönerse sorgu karşılaştırılabilir
     * bir metin içermiyordur (örn. yalnızca emoji/sembol) ve sıralama yapılmaz.
     *
     * İç nesne, dıştaki `object` üyelerini nitelenmiş adla çağırır; böylece iç içe
     * sınıfın dış kapsamı görüp görmemesine dair bir belirsizlik kalmaz.
     */
    internal class Probe private constructor(
        private val folded: String,
        private val tokens: List<String>,
        private val raw: String
    ) {
        /**
         * 4 = birebir başlık, 3 = başlık sorguyu içeriyor, 2 = tüm anlamlı kelimeler
         * mevcut, 1 = en az bir anlamlı kelime mevcut, 0 = hiç örtüşme yok.
         */
        fun tierOf(result: JikanSearchResult): Int {
            var best = 0
            for (title in titlesOf(result)) {
                val candidate = SearchRelevance.fold(title)
                if (candidate.isEmpty()) continue
                val tier = when {
                    candidate == folded -> 4
                    candidate.contains(folded) -> 3
                    folded.contains(candidate) && candidate.length >= 3 -> 3
                    tokens.isNotEmpty() && candidateTokens(candidate).containsAll(tokens) -> 2
                    tokens.any { it.length >= 3 && candidate.contains(it) } -> 1
                    else -> 0
                }
                if (tier > best) best = tier
                if (best == 4) break
            }
            // CJK sorgularda katlama anlamlı olmayabilir; ham metin üzerinden de dene.
            if (best == 0 && raw.length >= 2) {
                val needle = raw.trim()
                for (title in titlesOf(result)) {
                    if (title.contains(needle, ignoreCase = true)) return 3
                }
            }
            return best
        }

        private fun candidateTokens(candidate: String): Set<String> =
            candidate.split(' ').filter { it.isNotEmpty() }.toSet()

        private fun titlesOf(result: JikanSearchResult): List<String> =
            listOfNotNull(result.title, result.titleEnglish, result.titleJapanese)
                .filter { it.isNotBlank() }

        companion object {
            fun of(query: String): Probe? {
                val raw = query.trim()
                if (raw.isEmpty()) return null
                val folded = SearchRelevance.fold(raw)
                val tokens = SearchRelevance.meaningfulTokens(folded)
                if (folded.isEmpty() && tokens.isEmpty()) return null
                val needle = if (folded.isNotEmpty()) folded else raw.lowercase(Locale.ROOT)
                return Probe(needle, tokens, raw.lowercase(Locale.ROOT))
            }
        }
    }
}

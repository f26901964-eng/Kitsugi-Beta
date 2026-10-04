package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.SearchResponse
import java.util.Locale

/**
 * Başlık benzerliği ve en iyi eşleşme algoritmaları.
 * [CsStreamRunner] içindeki title matching mantığı buraya taşındı.
 */
internal object CsTitleMatcher {

    private const val TAG = "CsTitleMatcher"

    private val seasonRegex = Regex("\\b(\\d+)(?:\\s*\\.|\\s*(?:st|nd|rd|th))?\\s*(?:sezon|season|s\\b)|\\b(?:sezon|season|s(?=\\d)|s\\b)\\s*\\.?\\s*(\\d+)\\b", RegexOption.IGNORE_CASE)

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Verilen başlık dizesinden sezon numarasını çıkarmaya çalışır.
     */
    fun parseSeasonFromTitle(title: String): Int? {
        val lower = title.lowercase(Locale.ROOT).trim()
        val seasonMatch = seasonRegex.find(lower)
        if (seasonMatch != null) {
            val found = seasonMatch.groupValues.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                ?: seasonMatch.groupValues.getOrNull(1)?.toIntOrNull()
                ?: seasonMatch.groupValues.getOrNull(2)?.toIntOrNull()
            if (found != null && found in 1..25) return found
        }

        // Uzundan kısaya doğru Roma rakamları (kelime sınırı ve ayraçlarla: "Mushoku Tensei III: ...", "Mob Psycho 100 III")
        val romanNumerals = listOf(
            "x" to 10, "ix" to 9, "viii" to 8, "vii" to 7, "vi" to 6,
            "v" to 5, "iv" to 4, "iii" to 3, "ii" to 2
        )
        for ((roman, num) in romanNumerals) {
            val regex = Regex("\\b$roman\\b(?:\\s*[:\\-\\(\\[]|\\s*$)", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(lower)) {
                return num
            }
        }

        // Trailing number fallback (e.g. "Anime Name 2", "KonoSuba 3", excluding years)
        val trailingNumMatch = Regex("\\b([2-9]|1[0-9])\\s*(?:[:\\-\\(\\[]|\\s*$)").find(lower)
        if (trailingNumMatch != null) {
            val num = trailingNumMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 2..25) {
                return num
            }
        }
        return null
    }

    /**
     * Başlıktan yıl, sezon anahtar kelimeleri, Roma rakamları ve alt başlıkları temizleyerek
     * saf anime adını çıkarır (ör. "Mushoku Tensei III: Isekai..." -> "Mushoku Tensei").
     */
    fun extractCleanBaseTitle(rawTitle: String): String {
        var clean = rawTitle.trim()

        // 1. İki nokta üst üste ve tire sonrası alt başlıkları temizle (Franchise adı iki noktadan öncedir)
        // ör. "Mushoku Tensei III: Isekai Ittara Honki Dasu" -> "Mushoku Tensei III"
        // ör. "Mushoku Tensei: Jobless Reincarnation" -> "Mushoku Tensei"
        if (clean.contains(":")) {
            val prefix = clean.substringBefore(":").trim()
            if (prefix.length >= 3) {
                clean = prefix
            }
        }
        if (clean.contains(" - ")) {
            val prefix = clean.substringBefore(" - ").trim()
            if (prefix.length >= 3) {
                clean = prefix
            }
        }

        // 2. Yıl bilgisini temizle: (2021), 2024
        clean = clean.replace(Regex("""\s*\(?(19|20)\d{2}\)?"""), "")

        // 3. Sezon anahtar kelimelerini temizle: Season 3, 3. Sezon, 3rd Season, S3, Part 2, Cour 2
        clean = clean.replace(Regex("""\s*\b(?:season|sezon|s)\s*[:.-]?\s*\d+\b""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s*\b\d+\s*(?:st|nd|rd|th)?\s*(?:season|sezon)\b""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s*\b\d+\.\s*sezon\b""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s*\b(?:part|cour)\s*\d+\b""", RegexOption.IGNORE_CASE), "")

        // 4. Roma rakamlarını temizle (II, III, IV, V, VI, VII, VIII, IX, X)
        clean = clean.replace(Regex("""\s*\b(x|ix|viii|vii|vi|v|iv|iii|ii)\b""", RegexOption.IGNORE_CASE), "")

        // 5. Sondaki tek basamaklı veya sezon sayılarını temizle (örn. "Mushoku Tensei 2" -> "Mushoku Tensei")
        clean = clean.replace(Regex("""\s*\b(?:[2-9]|1[0-9])\b\s*$"""), "")

        return clean.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Verilen ana başlık ve alternatif başlıklardan arama varyantları listesi oluşturur.
     * Türkçe anime siteleri için romaji, ASCII, kısmi isim gibi varyantlar üretilir.
     */
    fun buildTitleVariants(main: String, alts: List<String>, season: Int? = null): List<String> {
        val variants = linkedSetOf<String>()
        val romanMap = mapOf(2 to "II", 3 to "III", 4 to "IV", 5 to "V", 6 to "VI", 7 to "VII", 8 to "VIII", 9 to "IX", 10 to "X")

        val cleanMain = extractCleanBaseTitle(main)

        // 1. Temiz ana başlığı ve orijinal ana başlığı önceliklendir
        if (cleanMain.isNotBlank() && cleanMain.length >= 3 && cleanMain != main) {
            variants.add(cleanMain)
            variants.add(simplifyTitle(cleanMain))
            variants.add(toAsciiTitle(cleanMain))
        }

        variants.add(main)
        variants.add(simplifyTitle(main))
        variants.add(toAsciiTitle(main))

        // 2. Alternatif başlıkların temiz franchise adlarını ve tam hallerini ekle
        for (alt in alts) {
            val cleanAlt = extractCleanBaseTitle(alt)
            if (cleanAlt.isNotBlank() && cleanAlt.length >= 3 && cleanAlt != alt) {
                variants.add(cleanAlt)
                variants.add(toAsciiTitle(cleanAlt))
            }
            variants.add(alt)
            variants.add(simplifyTitle(alt))
            variants.add(toAsciiTitle(alt))
        }

        // 3. İleri sezon aramasında sezon eki eklenmiş varyantları üret
        if (season != null && season > 1) {
            val allBases = (listOf(main, cleanMain) + alts.flatMap { listOf(it, extractCleanBaseTitle(it)) })
                .filter { it.isNotBlank() && it.length >= 3 }
                .distinct()

            val seasonSuffixes = mutableListOf(
                "$season. Sezon",
                "Season $season",
                "S$season",
                "$season"
            )
            romanMap[season]?.let { seasonSuffixes.add(it) }

            for (base in allBases) {
                for (suffix in seasonSuffixes) {
                    val q = "$base $suffix"
                    variants.add(q)
                    variants.add(simplifyTitle(q))
                    variants.add(toAsciiTitle(q))
                }
            }
        }

        // Strip season/year suffix — e.g. "Naruto: Shippuden (2007)" → "Naruto: Shippuden"
        val withoutYear = main.replace(Regex("\\s*\\(?(19|20)\\d{2}\\)?"), "").trim()
        if (withoutYear != main) { variants.add(withoutYear); variants.add(toAsciiTitle(withoutYear)) }

        // Strip season numbers — e.g. "Boku no Hero Academia Season 4" → "Boku no Hero Academia"
        val withoutSeason = extractCleanBaseTitle(main)
        if (withoutSeason != main && withoutSeason.length >= 3) {
            variants.add(withoutSeason)
            variants.add(toAsciiTitle(withoutSeason))
        }

        // Add first 3 words as a short variant (anime sites often search by partial name)
        val words = main.split(" ").filter { it.isNotBlank() }
        if (words.size > 2) variants.add(words.take(3).joinToString(" "))
        if (words.size > 1) variants.add(words.take(2).joinToString(" "))
        val GENERIC_WORDS = setOf(
            "attack", "titan", "season", "final", "the", "and", "from", "into", "with",
            "sezon", "bölüm", "film", "dizi", "izle", "part", "new", "world", "slayer",
            "shippuden", "naruto", "boruto", "piece", "clover", "academy", "academia",
            "kaisen", "hunter", "online", "game", "free", "live", "movie", "series",
            "turkce", "dublaj", "altyazi", "hd", "full", "tek", "parca", "anime"
        )
        val isNotGeneric = { w: String ->
            val cleaned = w.replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ]"), "").lowercase(Locale.ROOT)
            cleaned.length >= 4 && cleaned !in GENERIC_WORDS
        }

        // Always add every word with 4+ chars as standalone fallback (e.g. "Frieren", "Chainsaw", "Demon")
        words.filter { isNotGeneric(it) }.forEach { variants.add(it) }
        words.firstOrNull()?.let { firstWord ->
            val cleaned = firstWord.replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ]"), "").lowercase(Locale.ROOT)
            if (cleaned.length >= 2 && cleaned !in GENERIC_WORDS) {
                variants.add(firstWord)
            }
        }

        // Alternative titles (romaji, english, native) — with the same cleaning
        for (alt in alts) {
            variants.add(alt)
            variants.add(simplifyTitle(alt))
            variants.add(toAsciiTitle(alt))
            val altWords = alt.split(" ").filter { it.isNotBlank() }
            if (altWords.size > 2) variants.add(altWords.take(3).joinToString(" "))
            if (altWords.size > 1) variants.add(altWords.take(2).joinToString(" "))
            // Also add every significant alt-word standalone (e.g. "Frieren" from english title)
            altWords.filter { isNotGeneric(it) }.forEach { variants.add(it) }
            altWords.firstOrNull()?.let { firstWord ->
                val cleaned = firstWord.replace(Regex("[^a-zA-Z0-9çğıöşüÇĞİÖŞÜ]"), "").lowercase(Locale.ROOT)
                if (cleaned.length >= 2 && cleaned !in GENERIC_WORDS) {
                    variants.add(firstWord)
                }
            }
            val altNoYear = alt.replace(Regex("\\s*\\(?(19|20)\\d{2}\\)?"), "").trim()
            if (altNoYear != alt) { variants.add(altNoYear); variants.add(toAsciiTitle(altNoYear)) }
        }

        // Remove blanks and single-character strings
        return variants.filter { it.length >= 2 }
    }

    /**
     * Arama sonuçları arasından başlık benzerliği, kelime örtüşmesi ve yıl bilgisine göre
     * en iyi eşleşmeyi döndürür.
     */
    fun findBestMatch(
        results: List<SearchResponse>,
        mainTitle: String,
        altTitles: List<String>,
        targetYear: Int?,
        targetSeason: Int? = null,
        targetEpisode: Int? = null
    ): SearchResponse? {
        var bestScore = -1.0
        var bestMatch: SearchResponse? = null

        // Flatten all title variants into comparable lowercase strings
        val allQueryTitles = (listOf(mainTitle) + altTitles)
        val titlesToCompare = allQueryTitles
            .flatMap { t -> listOf(t, simplifyTitle(t), toAsciiTitle(t)) }
            .map { it.lowercase(Locale.ROOT).trim() }
            .filter { it.length >= 2 }
            .distinct()

        // Extract seasons explicitly mentioned in search query titles
        val querySeasons = allQueryTitles
            .mapNotNull { parseSeasonFromTitle(it) }
            .toSet()

        // Build word sets for overlap scoring
        val queryWordSets = allQueryTitles.map { t ->
            toAsciiTitle(t).lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length >= 3 }.toSet()
        }.filter { it.isNotEmpty() }

        for (result in results) {
            val resultName = result.name.lowercase(Locale.ROOT).trim()
            val resultNameSimple = toAsciiTitle(result.name).lowercase(Locale.ROOT).trim()
            val resultNameNoYear = resultName.replace(Regex("\\s*\\(?(19|20)\\d{2}\\)?"), "").trim()

            var foundSeason: Int? = null
            if (targetSeason != null) {
                foundSeason = parseSeasonFromTitle(resultName) ?: parseSeasonFromSlug(result.url)
            }

            val resultNameNoSeason = resultName
                .replace(seasonRegex, "")
                .replace(Regex("""\b(x|ix|viii|vii|vi|v|iv|iii|ii)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(\d+)\s*$"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            var maxSimilarity = 0.0
            for (t in titlesToCompare) {
                val sim1 = getSimilarity(resultName, t)
                val sim2 = getSimilarity(resultNameSimple, t)
                val sim3 = getSimilarity(resultNameNoYear, t)
                val sim4 = getSimilarity(resultNameNoSeason, t)
                val sim = maxOf(sim1, sim2, sim3, sim4)
                if (sim > maxSimilarity) maxSimilarity = sim
            }

            // Word-overlap bonus: reward results that share significant words with any query
            val resultWords = resultNameSimple.split(Regex("\\s+")).filter { it.length >= 3 }.toSet()
            var wordOverlapBonus = 0.0
            for (queryWords in queryWordSets) {
                val shared = queryWords.intersect(resultWords).size
                val unionSize = (queryWords + resultWords).size
                if (unionSize > 0) {
                    val jaccard = shared.toDouble() / unionSize
                    wordOverlapBonus = maxOf(wordOverlapBonus, jaccard * 0.25)
                }
            }

            // Containment bonus: if result name contains the query (or vice-versa)
            var containmentBonus = 0.0
            for (t in titlesToCompare) {
                if (resultNameSimple.contains(t) || t.contains(resultNameSimple)) {
                    containmentBonus = 0.10
                    break
                }
            }

            var score = maxSimilarity + wordOverlapBonus + containmentBonus
            val resultYear = getResultYear(result) ?: parseYearFromTitle(result.name)
            if (targetYear != null && resultYear != null) {
                when {
                    resultYear == targetYear -> score += 0.15
                    Math.abs(resultYear - targetYear) <= 1 -> score += 0.05
                    else -> score -= 0.05
                }
            }

            // ── Akıllı Sezon ve Bölüm Filtrelemesi ──
            // Eğer başlıkta "bölüm" veya "bolum" veya "episode" veya "ep" bilgisi geçiyorsa,
            // bölüm numarasını yakalayıp arananla karşılaştıralım.
            if (targetEpisode != null) {
                val epRegex = Regex("(\\d+)\\s*(?:\\.?\\s*(?:bölüm|bolum|ep|episode|b\\b))|\\b(?:bölüm|bolum|ep|episode|b\\b)\\s*\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                val epMatch = epRegex.find(resultName)
                val foundEp = epMatch?.groupValues?.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                    ?: epMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: epMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

                if (foundEp != null) {
                    if (foundEp == targetEpisode) {
                        score += 0.30 // Bölüm numarası birebir eşleşirse büyük ödül puanı
                        Log.d(TAG, "  -> Bölüm Eşleşti: '${result.name}' (Bölüm $foundEp) +0.30")
                    } else {
                        score -= 0.80 // Farklı bir bölüm başlığıysa elensin (-0.80 ceza puanı)
                        Log.d(TAG, "  -> Farklı Bölüm Uyuşmazlığı: '${result.name}' (Bulunan: $foundEp, Aranan: $targetEpisode) -0.80")
                    }
                }
            }

            if (targetSeason != null) {
                if (foundSeason != null) {
                    if (foundSeason == targetSeason || querySeasons.contains(foundSeason)) {
                        score += 0.35 // Sezon eşleşti ödülü
                        Log.d(TAG, "  -> Sezon Eşleşti: '${result.name}' (Sezon $foundSeason) +0.35")
                    } else {
                        score -= 0.60 // Farklı sezon uyuşmazlığı cezası
                        Log.d(TAG, "  -> Farklı Sezon Uyuşmazlığı: '${result.name}' (Bulunan: $foundSeason, Aranan: $targetSeason) -0.60")
                    }
                } else {
                    // Implicit season (no season mentioned in result)
                    // If targetSeason is 1, and no other season is specified in query, we match Season 1.
                    val expectsHigherSeason = targetSeason > 1 || querySeasons.any { it > 1 }
                    if (!expectsHigherSeason) {
                        score += 0.10
                    } else {
                        // Birçok Türkçe anime sağlayıcısı (Anizium, TrAnimeİzle, TürkAnime vb.) tüm sezonları
                        // tek bir ana dizi başlığı altında (konteyner) toplar (örn. "Mushoku Tensei: Jobless Reincarnation").
                        // Eğer açıkça "3. Sezon" adayı varsa o kazansın (+0.35 ödülü var), ancak konteyner adayı da
                        // elenmesin diye ceza sadece -0.15 olarak verilir. Böylece 0.20 eşiğini rahatça aşar ve
                        // safeLoad() çağrıldığında findEpisodeData hedef sezonu ve bölümü bulur.
                        score -= 0.15
                        Log.d(TAG, "  -> Sezonsuz Dizi Başlığı (Konteyner adayı, Aranan: $targetSeason): '${result.name}' -0.15")
                    }
                }
            }

            Log.d(TAG, "  Aday: '${result.name}' sim=${"%.2f".format(maxSimilarity)} overlap=${"%.2f".format(wordOverlapBonus)} total=${"%.2f".format(score)}")

            if (score > bestScore) {
                bestScore = score
                bestMatch = result
            }
        }

        Log.d(TAG, "En iyi aday puanı: ${"%.2f".format(bestScore)} (eşik: 0.20)")
        return if (bestScore >= 0.20) bestMatch else null
    }

    /**
     * Bir aday adının, ana başlık ve alternatifleri arasında en yüksek benzerlik skorunu döndürür.
     */
    fun getBestTitleSimilarity(candidateName: String, mainTitle: String, altTitles: List<String>): Double {
        val cand = toAsciiTitle(candidateName).lowercase(Locale.ROOT).trim()
        var maxSim = 0.0
        for (t in (listOf(mainTitle) + altTitles)) {
            val query = toAsciiTitle(t).lowercase(Locale.ROOT).trim()
            val sim = getSimilarity(cand, query)
            if (sim > maxSim) maxSim = sim
        }
        return maxSim
    }

    // ─── Internal Helpers ────────────────────────────────────────────────────

    /** Türkçe özel karakterleri kaldırır, gereksiz boşlukları temizler. */
    fun simplifyTitle(title: String): String {
        return title
            .replace(Regex("[^a-zA-Z0-9\\sçÇğĞıİöÖşŞüÜ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Türkçe karakterleri ASCII karşılıklarına dönüştürür (romaji eşleştirme için). */
    fun toAsciiTitle(title: String): String {
        return title
            .replace("ç", "c").replace("Ç", "C")
            .replace("ğ", "g").replace("Ğ", "G")
            .replace("ı", "i").replace("İ", "I")
            .replace("ö", "o").replace("Ö", "O")
            .replace("ş", "s").replace("Ş", "S")
            .replace("ü", "u").replace("Ü", "U")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Levenshtein mesafesi tabanlı string benzerliği (0.0 – 1.0).
     */
    fun getSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        val maxLen = maxOf(len1, len2)
        return (maxLen - dp[len1][len2]).toDouble() / maxLen
    }

    private val yearRegex = Regex("\\b(19|20)\\d{2}\\b")

    private fun getResultYear(result: SearchResponse): Int? {
        return try {
            val field = result.javaClass.getDeclaredField("year")
            field.isAccessible = true
            (field.get(result) as? Number)?.toInt()
        } catch (_: Exception) {
            try {
                val method = result.javaClass.getMethod("getYear")
                (method.invoke(result) as? Number)?.toInt()
            } catch (_: Exception) { null }
        }
    }

    private fun parseYearFromTitle(title: String): Int? =
        yearRegex.find(title)?.value?.toIntOrNull()

    /**
     * Verilen URL slug'ından veya path'inden sezon numarasını çıkarmaya çalışır.
     * Örneğin: "naruto-shippuden-2-sezon", "sezon-3", "naruto-2"
     */
    fun parseSeasonFromSlug(url: String): Int? {
        val lower = url.lowercase(Locale.ROOT)
        // Match "-2-sezon", "/sezon-2", "/2-sezon", "-season-2", "/season-2", "s2"
        val regex = Regex("(?:-|/|\\b)(\\d+)(?:-sezon|-season|sezon|season)\\b|\\b(?:sezon|season|s)(?:-|/|\\b)?(\\d+)\\b", RegexOption.IGNORE_CASE)
        val match = regex.find(lower)
        if (match != null) {
            val found = match.groupValues.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                ?: match.groupValues.getOrNull(1)?.toIntOrNull()
                ?: match.groupValues.getOrNull(2)?.toIntOrNull()
            if (found != null) return found
        }

        // Check trailing digit in slug: e.g. "naruto-shippuden-2/" or "naruto-shippuden-2"
        val cleanUrl = lower.trimEnd('/')
        val trailingNum = Regex("-(\\d+)$").find(cleanUrl)
        if (trailingNum != null) {
            val num = trailingNum.groupValues[1].toIntOrNull()
            if (num != null && num in 2..10) {
                return num
            }
        }
        return null
    }
}

package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType

/**
 * Kitsu kayıtlarındaki bozuk/alakasız özet (synopsis) tespiti.
 *
 * Kitsu'de bazı kayıtlar vandalizasyon veya hatalı birleştirme (merge) nedeniyle bambaşka
 * bir yapımın özetini taşır. Görülen gerçek örnek: "ROAR" (2004, anime) kaydının özeti
 * 1997 Fox TV dizisi "Roar"ı anlatır — "Roar is an American television show that
 * originally aired on the Fox network in July 1997. In the year AD 400, a young Irish
 * man, Conor, sets out to rid his land of the invading Romans..." — özet ile kayıt
 * (yıl/tür/poster) birbiriyle çelişir.
 *
 * Bu sınıf, bir Kitsu detayının özetinin kaydın kimliğiyle (medya türü + yayın yılı)
 * çelişip çelişmediğini düşük yanlış-pozitif oranıyla tespit eder. Tespit edilen özetler
 * asla olduğu gibi gösterilmez; doğrulanmış bir kaynakla değiştirilir ya da boşaltılır.
 */
object KitsuSynopsisValidator {

    /**
     * Özetin, anime/film kataloğundaki bir kayıt için "batı live-action TV dizisi"
     * tarif eden ifadeleri. (K-dizileri hariç tutulur — Kitsu anime uç noktasında
     * diziler makul ölçüde bulunabilir; Amerikan/İngiliz live-action ise neredeyse
     * her zaman bozuk kayıttır.)
     */
    private val wrongMediumPatterns = listOf(
        Regex(
            """\b(?:is|was)\s+an?\s+(?:american|british|canadian|australian)\s+(?:live[- ]action\s+)?television\s+(?:show|series)\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """\b(?:is|was)\s+an?\s+(?:american|british|canadian|australian)\s+tv\s+(?:show|series)\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """\b(?:american|british|canadian|australian)\s+live[- ]action\s+(?:television|tv)\s+(?:series|show)\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """\baired\s+on\s+(?:the\s+)?(?:fox\s+network|fox|nbc|abc|cbs|bbc|itv|channel\s*4|netflix|hbo|showtime|the\s+cw)\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""\boriginally\s+aired\s+on\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfox\s+network\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * "Yayın tarihi" bağlamındaki yıl: "aired ... in 1997", "premiered in 1997",
     * "debuted on ... in 1997". Hikâye içinde geçen yıllar ("In July 1997, X happened")
     * eşleşmez — fiil zorunludur, bu da yanlış-pozitif oranını düşürür.
     */
    private val airYearPattern = Regex(
        """\b(?:aired|premiered|debuted|released|broadcast|first\s+aired|originally\s+aired)\b[^.]{0,60}?\b((?:19|20)\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Özet şüpheli mi? Şüpheliyse nedenini (log için) döner; değilse null.
     *
     * Kurallar:
     *  1. Anime/film türündeki bir kayıt için özet açıkça amerikan/ingiliz/kanada/avustralya
     *     yapımı live-action TV dizisi tarif ediyorsa → şüpheli. (TvShow türünde bu ifadeler
     *     makuldür, bu yüzden orada uygulanmaz.)
     *  2. Özet, yayın-tarihi bağlamında bir yıl içeriyor ve bu yıl kaydın yayın yılından
     *     2+ yıl farklıysa → şüpheli (iki veri birbirini tutmuyor; biri yanlıştır).
     *
     * Not: Boş özet "şüpheli" değildir — bu fonksiyon null/boş özet için null döner.
     */
    fun suspiciousReason(synopsis: String?, entryYear: Int?, mediaType: MediaType): String? {
        if (synopsis.isNullOrBlank()) return null

        // 1) Batı live-action TV tarif eden ifadeler (yalnızca anime/film türü için çelişki)
        if (mediaType == MediaType.Anime || mediaType == MediaType.Movie) {
            for (pattern in wrongMediumPatterns) {
                val match = pattern.find(synopsis)
                if (match != null) {
                    return "özet \"${match.value}\" ifadesiyle Amerikan/İngiliz live-action TV dizisi tarif ediyor (kayıt türü: ${mediaType.name})"
                }
            }
        }

        // 2) Yayın-yılı çelişkisi
        val mentionedYear = airYearPattern.find(synopsis)?.groupValues?.get(1)?.toIntOrNull()
        if (entryYear != null && mentionedYear != null && kotlin.math.abs(entryYear - mentionedYear) > 2) {
            return "özet yayın yılını $mentionedYear olarak belirtiyor; kayıt yılı $entryYear ile çelişiyor"
        }

        return null
    }
}

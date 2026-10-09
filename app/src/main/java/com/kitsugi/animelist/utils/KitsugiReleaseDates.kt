package com.kitsugi.animelist.utils

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Tüm kaynaklar için ORTAK yayın tarihi yardımcıları (saf Kotlin, Android bağımlılığı yok).
 *
 * Kaynaklar tarihi farklı biçimlerde verir:
 *  - Jikan/MAL, Kitsu, Shikimori, Bangumi, TMDB, AniList → `YYYY-MM-DD`, `YYYY-MM`, `YYYY`
 *    veya saat dahil ISO (`2026-10-15T00:00:00+00:00`)
 *  - Simkl → film `released` (YYYY-MM-DD), dizi/anime `first_aired` (ISO UTC `...Z`)
 *  - TMDB bölüm/gün yayınları → yalnız tarih (saat yok) — UTC gece yarısı epoch'u olarak taşınır
 *
 * Bu sınıf ham değeri her kaynak için aynı Türkçe biçime çevirir; böylece
 * "Yakında yayında" detay sayfalarında tarih/saat/geri sayım tüm kaynaklarda aynı mantıkla görünür.
 */
object KitsugiReleaseDates {

    /** Kullanıcıya gösterilen saat dilimi (Türkiye). */
    val TR_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")

    /** Anime yayın günleri Japonya (JST) referanslıdır; ISO-UTC tarihleri bu bölgede yorumlanır. */
    val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    private val MONTHS_TR = arrayOf(
        "", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    private val DAYS_TR = mapOf(
        DayOfWeek.MONDAY to "Pazartesi",
        DayOfWeek.TUESDAY to "Salı",
        DayOfWeek.WEDNESDAY to "Çarşamba",
        DayOfWeek.THURSDAY to "Perşembe",
        DayOfWeek.FRIDAY to "Cuma",
        DayOfWeek.SATURDAY to "Cumartesi",
        DayOfWeek.SUNDAY to "Pazar"
    )

    private val PARTIAL_DATE = Regex("""^(\d{4})(?:-(\d{1,2}))?(?:-(\d{1,2}))?""")

    /** Ayrıştırılmış kısmi tarih. [hasMonth]/[hasDay] false ise tarihin o kısmı bilinmiyordur. */
    data class ParsedDate(val date: LocalDate, val hasMonth: Boolean, val hasDay: Boolean)

    /**
     * `YYYY-MM-DD`, `YYYY-MM-DDTHH:MM...`, `YYYY-MM`, `YYYY` biçimlerini çözer.
     * Tanınmayan / boş değerlerde null döner.
     */
    fun parse(raw: String?): ParsedDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val match = PARTIAL_DATE.find(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val monthRaw = match.groupValues[2].toIntOrNull()
        val dayRaw = match.groupValues[3].toIntOrNull()
        val month = monthRaw?.takeIf { it in 1..12 } ?: 1
        val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
        val day = dayRaw?.takeIf { it in 1..maxDay } ?: 1
        return ParsedDate(
            date = LocalDate.of(year, month, day),
            hasMonth = monthRaw != null && monthRaw in 1..12,
            hasDay = dayRaw != null && dayRaw in 1..maxDay
        )
    }

    /**
     * Tarihi Türkçe, okunabilir biçimde döndürür: "15 Ekim 2026" / "Ekim 2026" / "2026".
     * Çözülemeyen değer olduğu gibi döner (veri kaybı olmasın diye).
     */
    fun formatTr(raw: String?): String? {
        val parsed = parse(raw) ?: return raw?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            parsed.hasMonth && parsed.hasDay -> "${parsed.date.dayOfMonth} ${MONTHS_TR[parsed.date.monthValue]} ${parsed.date.year}"
            parsed.hasMonth -> "${MONTHS_TR[parsed.date.monthValue]} ${parsed.date.year}"
            else -> parsed.date.year.toString()
        }
    }

    /** Yalnız tam tarih (gün dahil) için "15 Ekim 2026 Perşembe" biçimi; eksikse null. */
    fun formatTrWithWeekday(raw: String?): String? {
        val parsed = parse(raw)?.takeIf { it.hasMonth && it.hasDay } ?: return null
        return formatDateWithWeekday(parsed.date)
    }

    fun formatDateWithWeekday(date: LocalDate): String =
        "${date.dayOfMonth} ${MONTHS_TR[date.monthValue]} ${date.year} ${DAYS_TR[date.dayOfWeek].orEmpty()}".trim()

    /**
     * Tam tarih → UTC gece yarısı epoch (saniye). TMDB "air_date" gibi yalnız tarih içeren
     * kaynaklar için kullanılır; [isDateOnlyEpoch] bu değeri "saat bilinmiyor" olarak tanır.
     */
    fun utcMidnightEpoch(raw: String?): Long? {
        val parsed = parse(raw)?.takeIf { it.hasMonth && it.hasDay } ?: return null
        return parsed.date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    }

    /** UTC gece yarısına denk gelen epoch = saat bilgisi yok (yalnız tarih). */
    fun isDateOnlyEpoch(epochSec: Long): Boolean = epochSec % 86_400L == 0L

    /**
     * Epoch'u Türkiye saatine göre biçimlendirir:
     *  - dateOnly → "17 Ekim 2026 Cumartesi"
     *  - aksi halde → "17 Ekim 2026 Cumartesi, 21:30"
     */
    fun formatEpochTr(epochSec: Long, dateOnly: Boolean = isDateOnlyEpoch(epochSec)): String {
        val zdt = Instant.ofEpochSecond(epochSec).atZone(TR_ZONE)
        val dateText = formatDateWithWeekday(zdt.toLocalDate())
        return if (dateOnly) dateText else "$dateText, ${"%02d:%02d".format(zdt.hour, zdt.minute)}"
    }

    /** Türkiye saatine göre saat metni "HH:mm" (dateOnly ise null). */
    fun formatTimeTr(epochSec: Long, dateOnly: Boolean = isDateOnlyEpoch(epochSec)): String? {
        if (dateOnly) return null
        val zdt: ZonedDateTime = Instant.ofEpochSecond(epochSec).atZone(TR_ZONE)
        return "%02d:%02d".format(zdt.hour, zdt.minute)
    }

    /**
     * Simkl ISO-UTC (`2026-10-04T15:00:00Z`) veya düz tarih (`2026-10-15`) değerini
     * YYYY-MM-DD'ye çevirir. [zone] verilirse o saat diliminde tarih alınır
     * (anime için JST — MAL/AniList tarihleriyle tutarlı).
     */
    fun isoToLocalDateString(raw: String?, zone: ZoneId = ZoneOffset.UTC): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(text)) return text
        return runCatching {
            Instant.parse(text).atZone(zone).toLocalDate().toString()
        }.getOrNull() ?: PARTIAL_DATE.find(text)?.value
    }

    /**
     * Kalan süreyi insan okunur Türkçe metne çevirir.
     * [episode] verilirse "Bölüm N, ..." öneki eklenir (TMDB film/dizi için null).
     */
    fun countdownText(targetEpochSec: Long, nowEpochSec: Long, episode: Int? = null): String {
        val remaining = targetEpochSec - nowEpochSec
        val prefix = if (episode != null && episode > 0) "Bölüm $episode, " else ""
        if (remaining <= 0L) {
            return if (episode != null && episode > 0) "Bölüm $episode yayınlandı!" else "Yayınlandı!"
        }
        val days = remaining / 86_400L
        return when {
            remaining < 3_600L -> "${prefix}${remaining / 60} dk sonra yayında"
            remaining < 86_400L -> "${prefix}${remaining / 3_600} saat sonra yayında"
            days < 7L -> "${prefix}$days gün sonra yayında"
            days < 30L -> "${prefix}${days / 7} hafta sonra yayında"
            else -> "${prefix}${days / 30} ay sonra yayında"
        }
    }
}

package com.kitsugi.animelist.utils

import org.json.JSONObject
import java.time.LocalDate
import java.time.Period

object KitsugiDateUtils {

    private val TURKISH_MONTHS = arrayOf(
        "", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    private val ENGLISH_MONTHS = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    /**
     * Parses birthday input from any source (raw JSON like {"month":5,"year":1992,"day":21},
     * ISO-8601 like "1992-05-21T00:00:00+00:00", "1992-05-21", "May 21, 1992", "21.05.1992", etc.)
     * and returns a clean, localized Turkish date string (e.g. "21 Mayıs 1992") along with
     * an automatically computed age if not already present.
     */
    fun formatBirthdayAndCalculateAge(
        rawBirthday: String?,
        currentAge: String?
    ): Pair<String?, String?> {
        if (rawBirthday.isNullOrBlank()) {
            return Pair(null, currentAge?.trim()?.takeIf { it.isNotBlank() && it != "null" })
        }

        var raw = rawBirthday.trim()

        var day: Int? = null
        var month: Int? = null
        var year: Int? = null

        // 1. Raw JSON string: e.g. {"month":5,"year":1992,"day":21} or with spaces
        if (raw.startsWith("{") && raw.endsWith("}")) {
            runCatching {
                val json = JSONObject(raw)
                val d = json.optInt("day", 0)
                val m = json.optInt("month", 0)
                val y = json.optInt("year", 0)
                if (d > 0) day = d
                if (m in 1..12) month = m
                if (y > 0) year = y
            }
        }

        // 2. ISO-8601 or YYYY-MM-DD: e.g. "1992-05-21" or "1992-05-21T00:00:00+00:00"
        if (year == null && raw.matches(Regex("""^\d{4}-\d{2}-\d{2}.*"""))) {
            val parts = raw.substring(0, 10).split("-")
            year = parts.getOrNull(0)?.toIntOrNull()
            month = parts.getOrNull(1)?.toIntOrNull()
            day = parts.getOrNull(2)?.toIntOrNull()
        }

        // 3. DD.MM.YYYY, DD/MM/YYYY or YYYY.MM.DD
        if (year == null && raw.contains(Regex("""[./-]"""))) {
            val parts = raw.split(Regex("""[./-]""")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size == 3) {
                if (parts[0].length == 4) { // YYYY.MM.DD
                    year = parts[0].toIntOrNull()
                    month = parts[1].toIntOrNull()
                    day = parts[2].toIntOrNull()
                } else if (parts[2].length == 4) { // DD.MM.YYYY
                    day = parts[0].toIntOrNull()
                    month = parts[1].toIntOrNull()
                    year = parts[2].toIntOrNull()
                }
            } else if (parts.size == 2) { // DD.MM
                day = parts[0].toIntOrNull()
                month = parts[1].toIntOrNull()
            }
        }

        // 4. English formatted dates: "May 21, 1992" or "21 May 1992" or "May 21"
        if (month == null) {
            val words = raw.replace(",", " ").split(Regex("""\s+""")).filter { it.isNotBlank() }
            for (w in words) {
                val mVal = ENGLISH_MONTHS[w.lowercase()]
                if (mVal != null) {
                    month = mVal
                    break
                }
            }
            if (month != null) {
                for (w in words) {
                    val n = w.toIntOrNull() ?: continue
                    if (n > 31 && year == null) {
                        year = n
                    } else if (n in 1..31 && day == null) {
                        day = n
                    }
                }
            }
        }

        // 5. Year only: e.g. "1992"
        if (year == null && raw.matches(Regex("""^\d{4}$"""))) {
            year = raw.toIntOrNull()
        }

        // Format date string in Turkish
        val formattedDate = when {
            day != null && month != null && month in 1..12 && year != null -> {
                "$day ${TURKISH_MONTHS[month]} $year"
            }
            day != null && month != null && month in 1..12 -> {
                "$day ${TURKISH_MONTHS[month]}"
            }
            month != null && month in 1..12 && year != null -> {
                "${TURKISH_MONTHS[month]} $year"
            }
            year != null -> {
                "$year"
            }
            else -> {
                // If it was raw JSON that failed to parse, hide the curly braces; otherwise keep text
                if (raw.startsWith("{")) null else raw
            }
        }

        // Calculate age if not provided
        var finalAge = currentAge?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (finalAge == null && year != null && year in 1900..2030) {
            runCatching {
                val now = LocalDate.now()
                val m = (month ?: 1).coerceIn(1, 12)
                val maxDay = when (m) {
                    2 -> 28
                    4, 6, 9, 11 -> 30
                    else -> 31
                }
                val d = (day ?: 1).coerceIn(1, maxDay)
                val birthDate = LocalDate.of(year, m, d)
                val calculated = Period.between(birthDate, now).years
                if (calculated in 1..130) {
                    finalAge = calculated.toString()
                }
            }
        }

        return Pair(formattedDate, finalAge)
    }
}

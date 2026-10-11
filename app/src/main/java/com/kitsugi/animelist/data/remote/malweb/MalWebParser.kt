package com.kitsugi.animelist.data.remote.malweb

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * MyAnimeList'in herkese açık HTML sayfalarını CİHAZ ÜZERİNDE ayrıştırır.
 *
 * Neden: Resmi MAL API'si başka kullanıcıların profilini/istatistiğini/favorilerini/arkadaşlarını/
 * geçmişini vermez. Jikan ve miribyou bunu sunucuda HTML okuyarak yapar; ancak tek bir sunucu IP'si
 * çok kullanıcıda MAL tarafından kısıtlanır. Aynı ayrıştırmayı uygulama içinde yapınca her kullanıcı
 * kendi IP'sinden normal bir ziyaretçi gibi istek atar → merkezi ban riski yoktur.
 *
 * Seçiciler miribyou (nattadasu/miribyou, MIT) ve Jikan parser'larından uyarlanmıştır; gerçek MAL
 * sayfalarıyla (jikan-fixtures) test edilmiştir. Saf Kotlin + jsoup'tur, Android bağımlılığı yoktur.
 */
object MalWebParser {

    private const val MAL_BASE = "https://myanimelist.net"
    private val MEDIA_ID_REGEX = Regex("""/(anime|manga|character|people|person)(?:\.php\?id=|/)(\d+)""")
    private val RESIZE_REGEX = Regex("""/r/\d+x\d+/""")
    private val SIZE_SUFFIX_REGEX = Regex("""(\d+)[tlv](\.(?:jpg|png|webp|gif))$""")
    private val USER_ID_REGEX = Regex("""var\s+user_id\s*=\s*(\d+);""")
    private val REPORT_ID_REGEX = Regex("""/modules\.php\?go=report&amp;type=profile&amp;id=(\d+)|go=report&type=profile&id=(\d+)""")
    private val NUMBER_REGEX = Regex("""-?\d+(?:\.\d+)?""")
    private val TOTAL_REGEX = Regex("""^\s*/\s*(\d+|-)""")

    // ── Modeller ────────────────────────────────────────────────────────────

    data class Images(val imageUrl: String) {
        val webpUrl: String get() = imageUrl.replace(".jpg", ".webp")
        val smallUrl: String get() = imageUrl.replace(".jpg", "t.jpg")
        val largeUrl: String get() = imageUrl.replace(".jpg", "l.jpg")
    }

    data class AnimeStats(
        val daysWatched: Double = 0.0,
        val meanScore: Double = 0.0,
        val watching: Int = 0,
        val completed: Int = 0,
        val onHold: Int = 0,
        val dropped: Int = 0,
        val planToWatch: Int = 0,
        val totalEntries: Int = 0,
        val rewatched: Int = 0,
        val episodesWatched: Int = 0
    )

    data class MangaStats(
        val daysRead: Double = 0.0,
        val meanScore: Double = 0.0,
        val reading: Int = 0,
        val completed: Int = 0,
        val onHold: Int = 0,
        val dropped: Int = 0,
        val planToRead: Int = 0,
        val totalEntries: Int = 0,
        val reread: Int = 0,
        val chaptersRead: Int = 0,
        val volumesRead: Int = 0
    )

    /** kind: anime | manga | character | person */
    data class Favorite(
        val malId: Int,
        val kind: String,
        val title: String,
        val url: String,
        val image: Images,
        val type: String? = null,
        val startYear: Int? = null
    )

    data class Update(
        val malId: Int,
        val kind: String,
        val title: String,
        val url: String,
        val image: Images,
        val status: String,
        val score: Int,
        /** Bölüm (anime) veya bölüm/chapter (manga) ilerlemesi. */
        val progress: Int?,
        val total: Int?,
        val dateIso: String?
    )

    data class External(val name: String, val url: String)

    data class Profile(
        val malId: Int?,
        val username: String,
        val url: String,
        val image: Images,
        val lastOnlineIso: String?,
        val gender: String?,
        val birthdayIso: String?,
        val location: String?,
        val joinedIso: String?,
        val animeStats: AnimeStats,
        val mangaStats: MangaStats,
        val favoriteAnime: List<Favorite>,
        val favoriteManga: List<Favorite>,
        val favoriteCharacters: List<Favorite>,
        val favoritePeople: List<Favorite>,
        val animeUpdates: List<Update>,
        val mangaUpdates: List<Update>,
        val aboutHtml: String?,
        val external: List<External>
    )

    data class Friend(
        val username: String,
        val url: String,
        val image: Images,
        val lastOnlineIso: String?,
        val friendsSinceIso: String?
    )

    data class FriendsPage(val friends: List<Friend>, val hasNextPage: Boolean)

    data class HistoryItem(
        val malId: Int,
        val kind: String,
        val title: String,
        val url: String,
        val increment: Int,
        val dateIso: String?
    )

    // ── Profil ──────────────────────────────────────────────────────────────

    /**
     * `/profile/{username}` sayfasını ayrıştırır. Sayfa bir MAL profili değilse (captcha, hata,
     * bakım sayfası vb.) null döner — çağıran taraf bir sonraki kaynağa (miribyou/Jikan) düşer.
     */
    fun parseProfile(html: String, nowMs: Long = System.currentTimeMillis()): Profile? {
        val doc = Jsoup.parse(html, MAL_BASE)
        val titleText = doc.title()
        val statusBox = doc.selectFirst("ul.user-status")
        if (statusBox == null && !titleText.contains("'s Profile")) return null

        val username = titleText.substringBefore("'s Profile").trim().takeIf { it.isNotEmpty() && it != titleText.trim() }
            ?: doc.selectFirst(".header-profile-name, h1.h1 span")?.text()?.substringBefore("'s Profile")?.trim()
            ?: return null

        val malId = USER_ID_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: REPORT_ID_REGEX.find(html)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()

        val avatarEl = doc.selectFirst(".user-profile .user-image img, .user-image img")
        val avatar = cleanImageUrl(avatarEl?.attr("data-src")?.ifBlank { null } ?: avatarEl?.attr("src"))

        fun info(label: String): String? {
            val li = doc.select("ul.user-status li").firstOrNull { li ->
                li.selectFirst(".user-status-title")?.text()?.trim()?.equals(label, ignoreCase = true)
                    ?: li.text().startsWith(label)
            } ?: return null
            val data = li.selectFirst(".user-status-data")?.text()?.trim()
                ?: li.text().removePrefix(label).trim()
            return data.takeIf { it.isNotEmpty() }
        }

        val animeBox = doc.selectFirst(".stats.anime")
        val mangaBox = doc.selectFirst(".stats.manga")

        val animeStats = AnimeStats(
            daysWatched = statNumber(animeBox, "Days:")?.toDouble() ?: 0.0,
            meanScore = statNumber(animeBox, "Mean Score:")?.toDouble() ?: 0.0,
            watching = statNumber(animeBox, "Watching")?.toInt() ?: 0,
            completed = statNumber(animeBox, "Completed")?.toInt() ?: 0,
            onHold = statNumber(animeBox, "On-Hold")?.toInt() ?: 0,
            dropped = statNumber(animeBox, "Dropped")?.toInt() ?: 0,
            planToWatch = statNumber(animeBox, "Plan to Watch")?.toInt() ?: 0,
            totalEntries = statNumber(animeBox, "Total Entries")?.toInt() ?: 0,
            rewatched = statNumber(animeBox, "Rewatched")?.toInt() ?: 0,
            episodesWatched = statNumber(animeBox, "Episodes")?.toInt() ?: 0
        )
        val mangaStats = MangaStats(
            daysRead = statNumber(mangaBox, "Days:")?.toDouble() ?: 0.0,
            meanScore = statNumber(mangaBox, "Mean Score:")?.toDouble() ?: 0.0,
            reading = statNumber(mangaBox, "Reading")?.toInt() ?: 0,
            completed = statNumber(mangaBox, "Completed")?.toInt() ?: 0,
            onHold = statNumber(mangaBox, "On-Hold")?.toInt() ?: 0,
            dropped = statNumber(mangaBox, "Dropped")?.toInt() ?: 0,
            planToRead = statNumber(mangaBox, "Plan to Read")?.toInt() ?: 0,
            totalEntries = statNumber(mangaBox, "Total Entries")?.toInt() ?: 0,
            reread = statNumber(mangaBox, "Reread")?.toInt() ?: 0,
            chaptersRead = statNumber(mangaBox, "Chapters")?.toInt() ?: 0,
            volumesRead = statNumber(mangaBox, "Volumes")?.toInt() ?: 0
        )

        val about = doc.selectFirst(".user-profile-about .word-break")?.html()?.trim()?.takeIf { it.isNotEmpty() }

        val external = ArrayList<External>()
        doc.select(".user-profile-sns a[href]").forEach { a ->
            val url = a.attr("href").trim()
            if (url.isEmpty() || url.contains("rss.php")) return@forEach
            val name = a.text().trim().ifEmpty { url.substringAfter("//").substringBefore("/") }
            if (external.none { it.url == url }) external += External(name, url)
        }

        return Profile(
            malId = malId,
            username = username,
            url = "$MAL_BASE/profile/$username",
            image = Images(avatar),
            lastOnlineIso = MalWebDates.toIso(info("Last Online"), nowMs),
            gender = info("Gender"),
            birthdayIso = MalWebDates.toIso(info("Birthday"), nowMs),
            location = info("Location"),
            joinedIso = MalWebDates.toIso(info("Joined"), nowMs),
            animeStats = animeStats,
            mangaStats = mangaStats,
            favoriteAnime = parseFavorites(doc, "#anime_favorites", "anime"),
            favoriteManga = parseFavorites(doc, "#manga_favorites", "manga"),
            favoriteCharacters = parseFavorites(doc, "#character_favorites", "character"),
            favoritePeople = parseFavorites(doc, "#person_favorites", "person"),
            animeUpdates = parseUpdates(doc, ".updates.anime", "anime", nowMs),
            mangaUpdates = parseUpdates(doc, ".updates.manga", "manga", nowMs),
            aboutHtml = about,
            external = external
        )
    }

    /**
     * İstatistik kutusundan etiketle eşleşen sayıyı okur.
     *  1. `.di-tc` (Days / Mean Score) — etiket `.fn-grey2` içinde, değer kardeş metinde.
     *  2. `.stats-status li` / `.stats-data li` — etiket ilk span/a'da, değer son span'da.
     */
    private fun statNumber(box: Element?, label: String): Double? {
        if (box == null) return null
        box.select(".di-tc").firstOrNull { it.text().contains(label) }?.let { cell ->
            val valueText = cell.selectFirst(".score-label")?.text()
                ?: cell.ownText().ifBlank { cell.text().replace(label, "") }
            return parseNumber(valueText)
        }
        val li = box.select(".stats-status li, .stats-data li").firstOrNull { item ->
            val first = item.children().firstOrNull()?.text()?.trim().orEmpty()
            first.equals(label, ignoreCase = true) || first.startsWith(label, ignoreCase = true)
        } ?: return null
        return parseNumber(li.select("span").lastOrNull()?.text())
    }

    private fun parseNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "")
        return NUMBER_REGEX.find(cleaned)?.value?.toDoubleOrNull()
    }

    private fun parseFavorites(doc: Document, selector: String, kind: String): List<Favorite> {
        val box = doc.selectFirst(selector) ?: return emptyList()
        return box.select("ul li").mapNotNull { li ->
            val a = li.selectFirst("a[href]") ?: return@mapNotNull null
            val href = absUrl(a.attr("href"))
            val malId = mediaIdOf(href) ?: return@mapNotNull null
            val title = a.selectFirst(".title")?.text()?.trim()?.ifEmpty { null }
                ?: li.attr("title").trim().ifEmpty { null }
                ?: a.text().trim()
            val img = a.selectFirst("img")
            val image = cleanImageUrl(img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src"))
            val info = li.selectFirst(".users")?.text()?.trim().orEmpty()
            val parts = info.split("·").map { it.trim() }.filter { it.isNotEmpty() }
            val (type, year) = when {
                parts.size > 1 -> parts[0] to parts[1].toIntOrNull()
                parts.size == 1 && parts[0].matches(Regex("""\d{4}""")) -> null to parts[0].toInt()
                parts.size == 1 && (kind == "anime" || kind == "manga") -> parts[0] to null
                else -> null to null
            }
            Favorite(malId, kind, title, href, Images(image), type, year)
        }
    }

    private fun parseUpdates(doc: Document, selector: String, kind: String, nowMs: Long): List<Update> {
        val box = doc.selectFirst(selector) ?: return emptyList()
        return box.select(".statistics-updates").mapNotNull { row ->
            val data = row.selectFirst(".data") ?: return@mapNotNull null
            val link = data.selectFirst("a[href]") ?: return@mapNotNull null
            val href = absUrl(link.attr("href"))
            val malId = mediaIdOf(href) ?: return@mapNotNull null
            val img = row.selectFirst("img")
            val image = cleanImageUrl(img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src"))
            val dateText = data.selectFirst(".graph-content span.fn-grey2, .graph-content .fn-grey2")?.text()?.trim()

            // Durum satırı: "Completed 13/13 · Scored 6" / "Watching 5/12 · Scored -"
            val statusRow = data.children().lastOrNull { it.hasClass("fn-grey2") && !it.hasClass("graph-content") }
            val statusText = statusRow?.text()?.replace('\u00A0', ' ')?.trim().orEmpty()
            val status = normalizeStatus(statusText, kind)

            val scoreText = statusRow?.selectFirst(".score-label")?.text()?.trim()
            val score = scoreText?.toIntOrNull() ?: 0

            val progressSpan = statusRow?.select("span.text")?.firstOrNull { !it.hasClass("score-label") }
            val progress = progressSpan?.text()?.trim()?.toIntOrNull()
            val nextText = progressSpan?.nextSibling()?.toString().orEmpty()
            val total = TOTAL_REGEX.find(nextText)?.groupValues?.get(1)?.toIntOrNull()

            Update(
                malId = malId,
                kind = kind,
                title = link.text().trim(),
                url = href,
                image = Images(image),
                status = status,
                score = score,
                progress = progress,
                total = total,
                dateIso = MalWebDates.toIso(dateText, nowMs)
            )
        }
    }

    private fun normalizeStatus(text: String, kind: String): String {
        val t = text.lowercase(Locale.US)
        return when {
            t.contains("plan to") -> if (kind == "anime") "Plan to Watch" else "Plan to Read"
            t.contains("on-hold") || t.contains("on hold") -> "On-Hold"
            t.contains("dropped") -> "Dropped"
            t.contains("completed") -> "Completed"
            t.contains("rewatching") -> "Rewatching"
            t.contains("rereading") -> "Rereading"
            t.contains("reading") -> "Reading"
            t.contains("watching") -> "Watching"
            else -> if (kind == "anime") "Watching" else "Reading"
        }
    }

    // ── Arkadaşlar ──────────────────────────────────────────────────────────

    /** `/profile/{username}/friends?p=N` sayfası. */
    fun parseFriends(html: String, nowMs: Long = System.currentTimeMillis()): FriendsPage? {
        val doc = Jsoup.parse(html, MAL_BASE)
        val container = doc.selectFirst(".boxlist-container")
        if (container == null) {
            // Arkadaşı olmayan kullanıcı: sayfa var ama liste yok. Profil sayfası değilse null.
            return if (doc.title().contains("Friends", ignoreCase = true) || doc.selectFirst("ul.user-status") != null) {
                FriendsPage(emptyList(), false)
            } else {
                null
            }
        }
        val friends = container.select(".boxlist").mapNotNull { box ->
            val data = box.selectFirst(".data")
            val nameLink = data?.selectFirst(".title a[href]")
                ?: box.select("a[href*='/profile/']").firstOrNull { it.text().isNotBlank() }
                ?: return@mapNotNull null
            val username = nameLink.text().trim().ifEmpty { return@mapNotNull null }
            val img = box.selectFirst("img")
            val image = cleanImageUrl(img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src"))
                .replace("thumbs/", "")
                .replace("_thumb", "")
            val greys = data?.select(".fn-grey2")?.map { it.text().trim() }.orEmpty()
            val lastOnline = greys.firstOrNull { !it.startsWith("Friends since") }
            val since = greys.firstOrNull { it.startsWith("Friends since") }?.removePrefix("Friends since")?.trim()
            Friend(
                username = username,
                url = absUrl(nameLink.attr("href")),
                image = Images(image),
                lastOnlineIso = MalWebDates.toIso(lastOnline, nowMs),
                friendsSinceIso = MalWebDates.toIso(since, nowMs)
            )
        }
        val hasNext = doc.select("a[href*='friends?p=']").any { it.text().trim().equals("Next", ignoreCase = true) }
        return FriendsPage(friends, hasNext)
    }

    // ── Geçmiş ──────────────────────────────────────────────────────────────

    /** `/history/{username}` veya `/history/{username}/anime|manga` sayfası. */
    fun parseHistory(html: String, nowMs: Long = System.currentTimeMillis()): List<HistoryItem>? {
        val doc = Jsoup.parse(html, MAL_BASE)
        val content = doc.selectFirst("#content") ?: return null
        val items = ArrayList<HistoryItem>()
        content.select("tr").forEach { tr ->
            val cells = tr.children().filter { it.tagName() == "td" }
            if (cells.size < 2) return@forEach
            val link = cells[0].selectFirst("a[href]") ?: return@forEach
            val href = link.attr("href")
            val match = MEDIA_ID_REGEX.find(href) ?: return@forEach
            val kind = if (match.groupValues[1] == "manga") "manga" else "anime"
            val malId = match.groupValues[2].toIntOrNull() ?: return@forEach
            val increment = cells[0].selectFirst("strong")?.text()?.trim()?.toIntOrNull() ?: 1
            val dateText = cells[1].ownText().replace('\u00A0', ' ').trim().ifEmpty {
                cells[1].text().replace('\u00A0', ' ').trim()
            }
            items += HistoryItem(
                malId = malId,
                kind = kind,
                title = link.text().trim(),
                url = "$MAL_BASE/$kind/$malId",
                increment = increment,
                dateIso = MalWebDates.toIso(dateText, nowMs)
            )
        }
        return items
    }

    // ── Yardımcılar ─────────────────────────────────────────────────────────

    internal fun cleanImageUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        if (raw.contains("spacer.gif")) return ""
        return raw.substringBefore("?")
            .replace(RESIZE_REGEX, "/")
            .replace(SIZE_SUFFIX_REGEX, "$1$2")
    }

    private fun absUrl(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("/") -> MAL_BASE + href
        else -> "$MAL_BASE/$href"
    }

    internal fun mediaIdOf(href: String): Int? = MEDIA_ID_REGEX.find(href)?.groupValues?.get(2)?.toIntOrNull()
}

/**
 * MAL'in tarih metinlerini ISO-8601'e çevirir. Oturum açmamış ziyaretçiye MAL saatleri
 * Pasifik saatiyle (America/Los_Angeles) gösterir.
 *
 * Desteklenen biçimler: "Now", "22 seconds ago", "10 minutes ago", "3 hours ago", "2 days ago",
 * "Today, 3:12 PM", "Yesterday, 8:55 PM", "Jul 15, 2:22 AM", "Apr 14, 2025 9:21 PM",
 * "May 17, 2016", "Jul 14" (yıl yok), "2000" gibi.
 */
object MalWebDates {
    private val MAL_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
    private val RELATIVE = Regex("""(\d+)\s+(second|minute|hour|day|week)s?\s+ago""", RegexOption.IGNORE_CASE)
    private val ISO_OUT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private fun fmt(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.US)

    private val TIME = fmt("h:mm a")
    private val DATE_TIME_YEAR = listOf(fmt("MMM d, yyyy h:mm a"), fmt("MMM d, yyyy, h:mm a"))
    private val DATE_TIME_NO_YEAR = fmt("MMM d, h:mm a")
    private val DATE_YEAR = listOf(fmt("MMM d, yyyy"), fmt("MMM  d, yyyy"))
    private val MONTH_YEAR = fmt("MMM yyyy")

    fun toIso(raw: String?, nowMs: Long = System.currentTimeMillis()): String? {
        val text = raw?.replace('\u00A0', ' ')?.replace(Regex("""\s+"""), " ")?.trim()
        if (text.isNullOrEmpty() || text.equals("Unknown", true) || text == "-") return null
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), MAL_ZONE)

        if (text.equals("Now", true) || text.equals("Online", true)) return now.withNano(0).format(ISO_OUT)

        RELATIVE.find(text)?.let { m ->
            val n = m.groupValues[1].toLong()
            val t = when (m.groupValues[2].lowercase(Locale.US)) {
                "second" -> now.minusSeconds(n)
                "minute" -> now.minusMinutes(n)
                "hour" -> now.minusHours(n)
                "day" -> now.minusDays(n)
                else -> now.minusWeeks(n)
            }
            return t.withNano(0).format(ISO_OUT)
        }

        if (text.startsWith("Today", true) || text.startsWith("Yesterday", true)) {
            val base = if (text.startsWith("Today", true)) now.toLocalDate() else now.toLocalDate().minusDays(1)
            val timePart = text.substringAfter(",", "").trim()
            val time = runCatching { LocalTime.parse(timePart.uppercase(Locale.US), TIME) }.getOrNull() ?: LocalTime.MIDNIGHT
            return ZonedDateTime.of(base, time, MAL_ZONE).format(ISO_OUT)
        }

        for (f in DATE_TIME_YEAR) {
            runCatching { LocalDateTime.parse(text.uppercase(Locale.US), f) }.getOrNull()?.let {
                return it.atZone(MAL_ZONE).format(ISO_OUT)
            }
        }
        runCatching {
            // Yıl yoksa içinde bulunulan yıl; gelecekte kalıyorsa bir önceki yıl.
            val parsed = DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern("MMM d, h:mm a")
                .parseDefaulting(java.time.temporal.ChronoField.YEAR, now.year.toLong())
                .toFormatter(Locale.US)
            var dt = LocalDateTime.parse(text, parsed).atZone(MAL_ZONE)
            if (dt.isAfter(now.plusDays(1))) dt = dt.minusYears(1)
            return dt.format(ISO_OUT)
        }
        for (f in DATE_YEAR) {
            runCatching { LocalDate.parse(text, f) }.getOrNull()?.let {
                return it.atStartOfDay(MAL_ZONE).format(ISO_OUT)
            }
        }
        runCatching {
            // "Jul 14" (yıl gizli doğum günü) → yıl bilinmediği için 0001 yerine içinde bulunulan yıl kullanılmaz;
            // Jikan gibi null döndürmek yerine ay/gün korunur.
            val parsed = DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern("MMM d")
                .parseDefaulting(java.time.temporal.ChronoField.YEAR, now.year.toLong())
                .toFormatter(Locale.US)
            return LocalDate.parse(text, parsed).atStartOfDay(MAL_ZONE).format(ISO_OUT)
        }
        runCatching { java.time.YearMonth.parse(text, MONTH_YEAR) }.getOrNull()?.let {
            return it.atDay(1).atStartOfDay(MAL_ZONE).format(ISO_OUT)
        }
        if (text.matches(Regex("""\d{4}"""))) {
            return LocalDate.of(text.toInt(), 1, 1).atStartOfDay(MAL_ZONE).format(ISO_OUT)
        }
        return null
    }
}

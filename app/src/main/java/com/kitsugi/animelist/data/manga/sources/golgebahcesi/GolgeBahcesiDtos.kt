package com.kitsugi.animelist.data.manga.sources.golgebahcesi

import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class SeriesListResponseDto(
    val data: List<SeriesDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

@Serializable
data class PaginationDto(
    val currentPage: Int = 1,
    val itemsPerPage: Int = 20,
    val totalItems: Int = 0,
    val totalPages: Int = 1,
)

@Serializable
data class SeriesDto(
    val id: String = "",
    val title: String = "",
    val slug: String = "",
    val alternativeTitles: List<String> = emptyList(),
    val description: String? = null,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val status: String? = null,
    val type: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: String = "",
    val seriesSlug: String = "",
    val number: Double = -1.0,
    val title: String? = null,
    val slug: String = "",
    val publishedAt: String? = null,
    val createdAt: String? = null,
    val isLocked: Boolean = false,
    val deliverySystem: String? = null,
    val imageEnc: Boolean = false,
    val pages: List<PageDto> = emptyList(),
    val lock: ChapterLockDto? = null,
)

@Serializable
data class ChapterLockDto(
    val type: String? = null,
    val unlockAt: String? = null,
)

@Serializable
data class PageDto(
    val index: Int = 0,
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

data class ParsedChapterRef(
    val seriesSlug: String,
    val chapterSlug: String,
    val chapterId: String?,
)

data class SecurePageRef(
    val chapterId: String,
    val seriesSlug: String,
    val chapterSlug: String,
    val pageIndex: Int,
)

object GolgeBahcesiParser {

    const val SECURE_PAGE_HOST = "golgebahcesi-pages.local"
    private const val SECURE_PAGE_PREFIX = "https://$SECURE_PAGE_HOST/v1/"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val PLACEHOLDER_CREATORS = setOf(
        "0", "-", "--", "null", "undefined", "n/a", "na", "bilinmiyor", "unknown", "yok"
    )

    private val PLACEHOLDER_CHAPTER_TITLES = setOf(
        "manga oku", "bolum oku", "bölüm oku", "oku", "read manga"
    )

    fun resolveSiteUrl(baseUrl: String, rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val cleanBase = baseUrl.trim().trimEnd('/')
        val resolved = when {
            trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$cleanBase$trimmed"
            else -> "$cleanBase/$trimmed"
        }
        return resolved.replace(" ", "%20")
    }

    fun sanitizeCreator(raw: String?): String? {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (cleaned.lowercase(Locale.ROOT) in PLACEHOLDER_CREATORS) return null
        return cleaned
    }

    fun parseStatus(raw: String?): MangaStatus {
        return when (raw?.trim()?.lowercase(Locale.ROOT)) {
            "ongoing", "devam ediyor", "publishing", "releasing" -> MangaStatus.Ongoing
            "completed", "tamamlandı", "tamamlandi", "finished", "ended" -> MangaStatus.Completed
            "hiatus", "on_hiatus", "on hiatus", "ara verildi", "paused" -> MangaStatus.OnHiatus
            "cancelled", "canceled", "dropped", "iptal", "bırakıldı", "birakildi" -> MangaStatus.Cancelled
            else -> MangaStatus.Unknown
        }
    }

    fun formatChapterNumber(number: Double): String {
        if (number < 0.0 || number.isNaN() || number.isInfinite()) return ""
        val asLong = number.toLong()
        return if (number == asLong.toDouble()) {
            asLong.toString()
        } else {
            number.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    fun normalizeChapterName(title: String?, number: Double, fallbackSlug: String = ""): String {
        val numLabel = formatChapterNumber(number)
        val rawTitle = title?.trim().orEmpty()
        val lowerTitle = rawTitle.lowercase(Locale("tr"))

        if (rawTitle.isEmpty() || lowerTitle in PLACEHOLDER_CHAPTER_TITLES) {
            if (numLabel.isNotEmpty()) return "Bölüm $numLabel"
            val slugClean = fallbackSlug.trim().removePrefix("chapter-").removePrefix("bolum-")
            return if (slugClean.isNotEmpty()) "Bölüm $slugClean" else "Bölüm"
        }

        if (numLabel.isNotEmpty() && rawTitle == numLabel) {
            return "Bölüm $numLabel"
        }

        return rawTitle
    }

    fun parseIsoTimestamp(raw: String?): Long {
        val input = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        return try {
            // Normalize fractional seconds (.123456Z -> .123Z) & 'Z' timezone for SimpleDateFormat
            var normalized = input.replace(' ', 'T')
            normalized = Regex("""\.(\d{1,9})(?=[Z+-])""").replace(normalized) { m ->
                val ms = m.groupValues[1].take(3).padEnd(3, '0')
                ".$ms"
            }
            val hasMillis = Regex("""\.\d{3}(?:Z|[+-]\d{2}:?\d{2})$""").containsMatchIn(normalized)
            if (normalized.endsWith("Z")) {
                val pattern = if (hasMillis) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(normalized)?.time ?: 0L
            } else {
                // Convert +03:00 -> +0300 for 'Z' pattern compatibility on all Android levels
                val tzFixed = Regex("""([+-]\d{2}):(\d{2})$""").replace(normalized, "$1$2")
                val pattern = if (hasMillis) "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.parse(tzFixed)?.time ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun extractSeriesSlug(mangaUrlOrSlug: String): String {
        val clean = mangaUrlOrSlug.trim()
            .removePrefix("slug:")
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        if (clean.contains("/manga/")) {
            return clean.substringAfter("/manga/").substringBefore('/').trim()
        }
        return clean.substringAfterLast('/').trim()
    }

    fun buildChapterUrl(seriesSlug: String, chapterSlug: String, chapterId: String): String {
        val cleanSeries = seriesSlug.trim().trim('/')
        val cleanChapter = chapterSlug.trim().trim('/')
        val cleanId = chapterId.trim()
        return if (cleanId.isNotEmpty()) {
            "/manga/$cleanSeries/bolum/$cleanChapter#$cleanId"
        } else {
            "/manga/$cleanSeries/bolum/$cleanChapter"
        }
    }

    fun parseChapterUrl(chapterUrl: String): ParsedChapterRef? {
        val trimmed = chapterUrl.trim()
        if (trimmed.isEmpty()) return null
        val chapterId = trimmed.substringAfter('#', "").trim().takeIf { it.isNotEmpty() }
        val withoutFragment = trimmed.substringBefore('#').substringBefore('?').trimEnd('/')
        val afterManga = if (withoutFragment.contains("/manga/")) {
            withoutFragment.substringAfter("/manga/")
        } else {
            withoutFragment.trimStart('/')
        }
        val parts = afterManga.split('/').filter { it.isNotBlank() }
        // Expected: [seriesSlug, "bolum", chapterSlug] or [seriesSlug, chapterSlug]
        val seriesSlug: String
        val chapterSlug: String
        when {
            parts.size >= 3 && parts[1].equals("bolum", ignoreCase = true) -> {
                seriesSlug = parts[0]
                chapterSlug = parts[2]
            }
            parts.size >= 2 -> {
                seriesSlug = parts[0]
                chapterSlug = parts.last()
            }
            else -> return null
        }
        return ParsedChapterRef(
            seriesSlug = seriesSlug,
            chapterSlug = chapterSlug,
            chapterId = chapterId,
        )
    }

    fun buildSecurePageUrl(
        chapterId: String,
        seriesSlug: String,
        chapterSlug: String,
        pageIndex: Int,
    ): String {
        return "$SECURE_PAGE_PREFIX${chapterId.trim()}/${seriesSlug.trim()}/${chapterSlug.trim()}/$pageIndex.jpg"
    }

    fun parseSecurePageUrl(url: String?): SecurePageRef? {
        val clean = url?.trim()?.substringBefore('?')?.substringBefore('#') ?: return null
        if (!clean.startsWith(SECURE_PAGE_PREFIX)) return null
        val rest = clean.removePrefix(SECURE_PAGE_PREFIX)
        val parts = rest.split('/')
        if (parts.size < 4) return null
        val chapterId = parts[0].trim()
        val seriesSlug = parts[1].trim()
        val chapterSlug = parts[2].trim()
        val indexStr = parts[3].substringBefore('.').trim()
        val pageIndex = indexStr.toIntOrNull() ?: return null
        if (chapterId.isEmpty() || seriesSlug.isEmpty() || chapterSlug.isEmpty() || pageIndex < 0) {
            return null
        }
        return SecurePageRef(
            chapterId = chapterId,
            seriesSlug = seriesSlug,
            chapterSlug = chapterSlug,
            pageIndex = pageIndex,
        )
    }

    fun SeriesDto.toMangaDetails(baseUrl: String, sourceName: String): MangaDetails {
        val cover = resolveSiteUrl(baseUrl, coverImage).ifEmpty {
            resolveSiteUrl(baseUrl, bannerImage)
        }.takeIf { it.isNotEmpty() }

        val genreNames = buildList {
            type?.trim()?.takeIf { it.isNotEmpty() }?.let { rawType ->
                val pretty = rawType.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                }
                add(pretty)
            }
            genres.forEach { genre ->
                val n = genre.trim()
                if (n.isNotEmpty() && none { it.equals(n, ignoreCase = true) }) {
                    add(n)
                }
            }
        }

        val altTitles = alternativeTitles
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(title.trim(), ignoreCase = true) }
            .distinct()

        return MangaDetails(
            url = "/manga/${slug.trim()}",
            title = title.trim(),
            author = sanitizeCreator(author),
            artist = sanitizeCreator(artist),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            genre = genreNames,
            thumbnailUrl = cover,
            status = parseStatus(status),
            source = sourceName,
            alternativeTitles = altTitles,
        )
    }

    fun ChapterDto.toMangaChapter(seriesSlugFallback: String): MangaChapter {
        val effectiveSeriesSlug = seriesSlug.trim().ifEmpty { seriesSlugFallback.trim() }
        val mangaUrl = "/manga/$effectiveSeriesSlug"
        return MangaChapter(
            url = buildChapterUrl(
                seriesSlug = effectiveSeriesSlug,
                chapterSlug = slug,
                chapterId = id,
            ),
            name = normalizeChapterName(title = title, number = number, fallbackSlug = slug),
            chapterNumber = if (number >= 0.0 && !number.isNaN()) number.toFloat() else -1f,
            scanlator = "Gölge Bahçesi",
            uploadDate = parseIsoTimestamp(publishedAt ?: createdAt),
            mangaUrl = mangaUrl,
        )
    }

    fun isSecureChapter(chapter: ChapterDto): Boolean {
        if (chapter.deliverySystem.equals("secure", ignoreCase = true)) return true
        if (chapter.imageEnc) return true
        return chapter.pages.any { it.url.substringBefore('?').endsWith(".enc", ignoreCase = true) }
    }
}

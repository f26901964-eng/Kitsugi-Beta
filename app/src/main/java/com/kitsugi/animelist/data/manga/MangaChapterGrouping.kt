package com.kitsugi.animelist.data.manga

import java.util.Locale
import kotlin.math.abs

/** UI-independent grouping and navigation rules shared by manga detail and reader. */
object MangaChapterGrouping {

    data class Group(
        val key: String,
        val number: Double?,
        val displayName: String,
        val chapters: List<MangaChapter>,
        val firstSourceIndex: Int,
    ) {
        val uploadDate: Long
            get() = chapters.maxOfOrNull { it.uploadDate } ?: 0L

        val scanlators: List<String>
            get() = chapters
                .map { it.scanlator.orEmpty().trim() }
                .distinctBy { it.lowercase(Locale.ROOT) }
    }

    fun numberOf(mangaTitle: String, chapter: MangaChapter): Double? {
        val supplied = chapter.chapterNumber.toDouble()
        if (supplied >= 0.0 && supplied.isFinite()) return supplied.normalizedZero()

        val parsed = ChapterRecognition.parseChapterNumber(
            mangaTitle = mangaTitle,
            chapterName = chapter.name,
            chapterNumber = null,
        )
        return parsed.takeIf { it >= 0.0 && it.isFinite() }?.normalizedZero()
    }

    /**
     * Groups every translation of a chapter under one row. URL remains the variant identity;
     * chapter number (or a normalized name when no number exists) is the group identity.
     */
    fun group(mangaTitle: String, chapters: List<MangaChapter>): List<Group> {
        data class MutableGroup(
            val key: String,
            val number: Double?,
            val firstSourceIndex: Int,
            val variants: MutableList<Pair<Int, MangaChapter>>,
        )

        val groups = linkedMapOf<String, MutableGroup>()
        chapters.forEachIndexed { index, chapter ->
            val number = numberOf(mangaTitle, chapter)
            val key = number?.let { "number:${formatNumber(it)}" }
                ?: "name:${normalizeName(chapter.name).ifBlank { "chapter-$index" }}"
            val target = groups.getOrPut(key) {
                MutableGroup(key, number, index, mutableListOf())
            }
            // Duplicate source entries must not create duplicate read/download actions.
            if (target.variants.none { it.second.url == chapter.url }) {
                target.variants += index to chapter
            }
        }

        return groups.values.map { mutable ->
            val variants = mutable.variants
                .sortedWith(
                    compareByDescending<Pair<Int, MangaChapter>> { it.second.uploadDate }
                        .thenBy { it.first },
                )
                .map { it.second }
            val displayName = variants
                .map { it.name.trim() }
                .filter { it.isNotBlank() }
                .minByOrNull { it.length }
                ?.takeIf { it.isNotBlank() }
                ?: mutable.number?.let { "Bölüm ${formatNumber(it)}" }
                ?: "Bölüm"
            Group(
                key = mutable.key,
                number = mutable.number,
                displayName = displayName,
                chapters = variants,
                firstSourceIndex = mutable.firstSourceIndex,
            )
        }
    }

    /** Canonical newest-to-oldest order used for previous/next chapter navigation. */
    fun newestFirst(groups: List<Group>): List<Group> = groups.sortedWith(
        compareByDescending<Group> { it.number ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.firstSourceIndex },
    )

    fun oldestFirst(groups: List<Group>): List<Group> = newestFirst(groups).asReversed()

    fun recentlyUpdated(groups: List<Group>): List<Group> = groups.sortedWith(
        compareByDescending<Group> { it.uploadDate }
            .thenByDescending { it.number ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.firstSourceIndex },
    )

    /**
     * Returns the adjacent *chapter group*, never merely the next raw list item. Consequently
     * moving from Chapter 2 can never silently open another translator's Chapter 2.
     */
    fun adjacentGroup(
        mangaTitle: String,
        chapters: List<MangaChapter>,
        currentChapter: MangaChapter,
        next: Boolean,
    ): Group? {
        val groups = newestFirst(group(mangaTitle, chapters))
        val currentIndex = groups.indexOfFirst { group ->
            group.chapters.any { it.url == currentChapter.url }
        }
        if (currentIndex < 0) return null
        val targetIndex = if (next) currentIndex - 1 else currentIndex + 1
        return groups.getOrNull(targetIndex)
    }

    fun formatNumber(number: Double): String {
        val normalized = number.normalizedZero()
        if (abs(normalized - normalized.toLong().toDouble()) < 0.000_001) {
            return normalized.toLong().toString()
        }
        return String.format(Locale.ROOT, "%.3f", normalized).trimEnd('0').trimEnd('.')
    }

    private fun normalizeName(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}.]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun Double.normalizedZero(): Double = if (abs(this) < 0.000_001) 0.0 else this
}

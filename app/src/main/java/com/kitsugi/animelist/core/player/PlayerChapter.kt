package com.kitsugi.animelist.core.player

/** Positions are milliseconds in the engine contract; Compose converts to seconds. */
data class PlayerChapter(val title: String, val positionMs: Long)

object PlayerChapterPolicy {
    fun normalize(chapters: List<PlayerChapter>, durationMs: Long = 0L): List<PlayerChapter> =
        chapters.filter { it.positionMs >= 0 && (durationMs <= 0 || it.positionMs < durationMs) }
            .sortedBy { it.positionMs }
            .distinctBy { it.positionMs }

    fun currentIndex(chapters: List<PlayerChapter>, positionMs: Long): Int =
        chapters.indexOfLast { it.positionMs <= positionMs }
}

/** Source/AniSkip intervals can be merged without depending on Compose or an engine. */
data class PlayerChapterRange(val title: String, val startMs: Long, val endMs: Long)

fun mergePlayerChapters(native: List<PlayerChapter>, ranges: List<PlayerChapterRange>, durationMs: Long): List<PlayerChapter> {
    val valid = ranges.filter { it.startMs >= 0 && it.endMs > it.startMs && (durationMs <= 0 || it.startMs < durationMs) }
        .sortedBy { it.startMs }
    val starts = valid.map { PlayerChapter(it.title, it.startMs) }
    val ends = valid.mapNotNull { range ->
        val end = if (durationMs > 0) range.endMs.coerceAtMost(durationMs) else range.endMs
        if ((durationMs > 0 && end >= durationMs) || valid.any { kotlin.math.abs(it.startMs - end) < 1000 }) null
        else PlayerChapter("", end)
    }
    val kept = native.filter { chapter -> starts.none { kotlin.math.abs(it.positionMs - chapter.positionMs) < 1000 } }
    val combined = PlayerChapterPolicy.normalize(starts + kept + ends, durationMs)
    val withStart = if (combined.isNotEmpty() && combined.first().positionMs > 0) listOf(PlayerChapter("", 0)) + combined else combined
    return withStart.filterIndexed { index, chapter -> index == 0 || chapter.title.isNotBlank() || withStart[index - 1].title.isNotBlank() }
}

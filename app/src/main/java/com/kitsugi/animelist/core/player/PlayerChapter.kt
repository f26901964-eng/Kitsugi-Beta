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

    /**
     * Parses Vorbis / Matroska chapter timestamp ("HH:MM:SS.mmm" or "MM:SS.mmm") to milliseconds.
     */
    fun parseTimestampMs(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        if (parts.size !in 2..3) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val secParts = parts.last().split(".")
        val seconds = secParts[0].toLongOrNull() ?: return null
        val millis = if (secParts.size > 1) {
            secParts[1].take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        } else 0L
        if (hours < 0 || minutes !in 0..59 || seconds !in 0..59 || millis < 0) return null
        return ((hours * 3600L + minutes * 60L + seconds) * 1000L) + millis
    }

    /**
     * Parses Vorbis comment key-value pairs (CHAPTER001=00:01:30.000, CHAPTER001NAME=Opening)
     * into normalized [PlayerChapter] entries.
     */
    fun parseVorbisComments(comments: List<Pair<String, String>>): List<PlayerChapter> {
        if (comments.isEmpty()) return emptyList()
        val timeByPrefix = linkedMapOf<String, Long>()
        val nameByPrefix = linkedMapOf<String, String>()
        val chapterKeyRegex = Regex("""^CHAPTER(\d+)$""", RegexOption.IGNORE_CASE)
        val chapterNameRegex = Regex("""^CHAPTER(\d+)NAME$""", RegexOption.IGNORE_CASE)
        for ((rawKey, rawVal) in comments) {
            val key = rawKey.trim()
            val timeMatch = chapterKeyRegex.matchEntire(key)
            if (timeMatch != null) {
                val id = timeMatch.groupValues[1]
                val ms = parseTimestampMs(rawVal)
                if (ms != null) timeByPrefix[id] = ms
                continue
            }
            val nameMatch = chapterNameRegex.matchEntire(key)
            if (nameMatch != null) {
                val id = nameMatch.groupValues[1]
                val title = rawVal.trim()
                if (title.isNotEmpty()) nameByPrefix[id] = title
            }
        }
        return timeByPrefix.entries.mapIndexed { idx, (id, ms) ->
            PlayerChapter(
                title = nameByPrefix[id] ?: "Chapter ${idx + 1}",
                positionMs = ms
            )
        }
    }
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

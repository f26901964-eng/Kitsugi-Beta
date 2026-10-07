package com.kitsugi.animelist.model

import java.text.Normalizer
import java.util.Locale

/** Provider IDs are namespaced by media type. A title must never override conflicting IDs. */
object MediaIdentity {
    fun canonicalSource(source: String): String = when (val value = source.lowercase(Locale.ROOT)) {
        "jikan" -> "mal"
        else -> value
    }

    fun keys(entry: MediaEntry): Set<String> = buildSet {
        val prefix = entry.type.name
        entry.malId?.let { id ->
            when {
                id in 1..99_999_999 && entry.type in listOf(MediaType.Anime, MediaType.Manga) -> add("$prefix:mal:$id")
                id in 100_000_001..299_999_999 && entry.source == "anilist" -> add("$prefix:anilist:${id - 100_000_000}")
                id in 300_000_001..399_999_999 -> add("$prefix:kitsu:${id - 300_000_000}")
            }
        }
        entry.simklId?.takeIf { it > 0 }?.let { add("$prefix:simkl:$it") }
        entry.tmdbId?.takeIf { it > 0 }?.let { add("$prefix:tmdb:$it") }
    }

    fun normalizedTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun sameMedia(a: MediaEntry, b: MediaEntry, allowTitle: Boolean = true): Boolean {
        if (a.type != b.type) return false
        val aKeys = keys(a)
        val bKeys = keys(b)
        val aIds = aKeys.associateBy { it.substringBeforeLast(':') }
        val bIds = bKeys.associateBy { it.substringBeforeLast(':') }
        if (aIds.keys.intersect(bIds.keys).any { aIds[it] != bIds[it] }) return false
        if (aKeys.intersect(bKeys).isNotEmpty()) return true
        if (!allowTitle || (a.year != null && b.year != null && a.year != b.year)) return false
        fun titles(entry: MediaEntry) = listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
            .map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        return titles(a).intersect(titles(b)).isNotEmpty()
    }

    fun sameLibraryRecord(a: MediaEntry, b: MediaEntry): Boolean =
        canonicalSource(a.source) == canonicalSource(b.source) && sameMedia(a, b)
}

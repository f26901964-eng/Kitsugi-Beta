package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

/** Source-scoped index used to mark catalog/search results as present in the matching list. */
internal typealias ExploreEntryMap = Map<String, List<MediaEntry>>

internal fun generateExploreEntryMap(currentEntries: List<MediaEntry>): ExploreEntryMap {
    val mapping = mutableMapOf<String, MutableList<MediaEntry>>()

    fun add(key: String, entry: MediaEntry) {
        mapping.getOrPut(key) { mutableListOf() }.add(entry)
    }

    currentEntries.forEach { entry ->
        val source = canonicalMediaSourceId(entry.source)
        val type = entry.type.name.lowercase()
        add("$source|all", entry)
        entry.malId?.let { id ->
            add("$source|type|$type|id|$id", entry)
            add("$source|id|$id", entry)
            if (source == "anilist" && id >= 100_000_000) {
                add("$source|id|${id - 100_000_000}", entry)
            }
        }
        if (source == "simkl") {
            entry.simklId?.let { add("$source|id|$it", entry) }
        }
        entry.tmdbId?.let { add("$source|tmdb|$it", entry) }

        listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
            .map { MediaIdentity.normalizedTitle(it) }
            .filter { it.length >= 2 }
            .distinct()
            .forEach { normalized -> add("$source|title|$type|$normalized", entry) }
    }

    return mapping.mapValues { (_, entries) -> entries.distinctBy { "${it.source}:${it.id}" } }
}

internal fun getMediaEntryFromMap(
    result: JikanSearchResult,
    entryMap: ExploreEntryMap
): MediaEntry? {
    val source = canonicalMediaSourceId(result.source)
    val type = result.type.name.lowercase()
    val candidates = linkedSetOf<MediaEntry>()

    fun add(key: String) {
        entryMap[key].orEmpty().forEach { candidates.add(it) }
    }

    add("$source|type|$type|id|${result.malId}")
    add("$source|id|${result.malId}")
    if (source == "anilist") {
        val alternateId = if (result.malId >= 100_000_000) {
            result.malId - 100_000_000
        } else {
            result.malId + 100_000_000
        }
        add("$source|id|$alternateId")
    }
    result.tmdbId?.let { add("$source|tmdb|$it") }
    if (source == "tmdb") {
        add("$source|tmdb|${result.malId}")
    }

    listOfNotNull(result.title, result.titleEnglish, result.titleJapanese, result.titleRomaji)
        .map { MediaIdentity.normalizedTitle(it) }
        .filter { it.length >= 2 }
        .distinct()
        .forEach { normalized -> add("$source|title|$type|$normalized") }

    // Keep a source-only fallback for IDs whose provider-specific namespace changed. The
    // matcher still enforces exact provider identity before accepting a title/ID match.
    if (candidates.isEmpty()) add("$source|all")

    return candidates.firstOrNull { it.matchesInSource(result) }
}

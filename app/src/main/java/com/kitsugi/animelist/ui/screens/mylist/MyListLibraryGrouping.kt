package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

internal const val MY_LIST_ALL_TAB_INDEX = 5
internal const val MY_LIST_TAB_COUNT = MY_LIST_ALL_TAB_INDEX + 1

/** One visible item in the combined library, with the provider logos that contain it. */
internal data class MyListLibraryItem(
    val entry: MediaEntry,
    val sourceIds: List<String>
)

private data class MutableMyListLibraryGroup(
    var representative: MediaEntry,
    val entries: MutableList<MediaEntry> = mutableListOf(),
    val sourceIds: LinkedHashSet<String> = linkedSetOf()
)

/**
 * Collapses copies of the same title across the connected list providers without modifying
 * the underlying records. The representative is the newest inserted record; progress and
 * status changes remain scoped to that provider, while [sourceIds] exposes every provider
 * that has a matching record.
 */
internal fun groupMyListEntries(entries: List<MediaEntry>): List<MyListLibraryItem> {
    if (entries.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableMyListLibraryGroup>()
    val groupIndexesByIdentity = mutableMapOf<String, MutableList<Int>>()

    entries.forEach { entry ->
        val candidateKeys = identityCandidateKeys(entry)
        val candidateGroups = candidateKeys
            .asSequence()
            .flatMap { groupIndexesByIdentity[it].orEmpty().asSequence() }
            .distinct()
            .sorted()

        val matchingGroupIndex = candidateGroups.firstOrNull { groupIndex ->
            val groupEntries = groups[groupIndex].entries
            groupEntries.none { existing ->
                MediaIdentity.conflictingIdentityKeys(existing, entry).isNotEmpty()
            } && groupEntries.any { existing ->
                MediaIdentity.sameMedia(existing, entry)
            }
        }

        val groupIndex = matchingGroupIndex ?: groups.size.also {
            groups += MutableMyListLibraryGroup(representative = entry)
        }
        val group = groups[groupIndex]

        if (matchingGroupIndex != null) {
            group.entries += entry
            if (entry.id > group.representative.id) {
                group.representative = entry
            }
        } else {
            group.entries += entry
        }

        sourceBadgeId(entry.source)?.let(group.sourceIds::add)
        candidateKeys.forEach { key ->
            val indexedGroups = groupIndexesByIdentity.getOrPut(key) { mutableListOf() }
            if (groupIndex !in indexedGroups) indexedGroups += groupIndex
        }
    }

    return groups.map { group ->
        MyListLibraryItem(
            entry = group.representative,
            sourceIds = group.sourceIds.toList()
        )
    }
}

/** Canonical IDs are shared by source logos, source tabs, and search-result aliases. */
internal fun sourceBadgeId(source: String): String? = when (source.trim().lowercase()) {
    "anilist", "al" -> "anilist"
    "mal", "myanimelist", "jikan", "jikan (mal)", "mal (jikan)" -> "mal"
    "simkl" -> "simkl"
    "kitsu" -> "kitsu"
    "shikimori", "shiki" -> "shikimori"
    "tmdb", "themoviedb" -> "tmdb"
    else -> null
}

private fun identityCandidateKeys(entry: MediaEntry): Set<String> = buildSet {
    MediaIdentity.keys(entry).forEach { add("id:$it") }
    listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese).forEach { title ->
        val normalized = MediaIdentity.normalizedTitle(title)
        if (normalized.isNotBlank()) add("title:${entry.type.name}:$normalized")
    }
}

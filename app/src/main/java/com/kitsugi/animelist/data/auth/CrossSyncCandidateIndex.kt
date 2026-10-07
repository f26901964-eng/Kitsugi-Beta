package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

/**
 * Narrows cross-sync matching to groups that could possibly share a provider ID or title.
 * The caller still applies MediaIdentity.sameMedia() to each candidate, so the index never
 * makes a match decision by itself. This avoids scanning every previous group for every entry.
 */
internal class CrossSyncCandidateIndex<Group : Any> {
    private val groupsByIdentityKey = mutableMapOf<String, LinkedHashSet<Group>>()
    private val groupsByTitleAlias = mutableMapOf<String, LinkedHashSet<Group>>()
    private val groupsByJapaneseTitle = mutableMapOf<String, LinkedHashSet<Group>>()

    fun add(group: Group, entry: MediaEntry) {
        MediaIdentity.keys(entry).forEach { key ->
            groupsByIdentityKey.getOrPut(key) { linkedSetOf() }.add(group)
        }
        titleAliases(entry).forEach { alias ->
            groupsByTitleAlias.getOrPut(titleKey(entry, alias)) { linkedSetOf() }.add(group)
        }
        japaneseAlias(entry)?.let { alias ->
            groupsByJapaneseTitle.getOrPut(titleKey(entry, alias)) { linkedSetOf() }.add(group)
        }
    }

    /** Returns a small superset of groups that may satisfy MediaIdentity.sameMedia(). */
    fun possibleMatches(entry: MediaEntry): Set<Group> = LinkedHashSet<Group>().apply {
        MediaIdentity.keys(entry).forEach { key -> addAll(groupsByIdentityKey[key].orEmpty()) }
        titleAliases(entry).forEach { alias -> addAll(groupsByTitleAlias[titleKey(entry, alias)].orEmpty()) }
        japaneseAlias(entry)?.let { alias -> addAll(groupsByJapaneseTitle[titleKey(entry, alias)].orEmpty()) }
    }

    /** Only primary/English aliases participate in the conflicting-ID safety check. */
    fun sharingTitleAlias(entry: MediaEntry): Set<Group> = LinkedHashSet<Group>().apply {
        titleAliases(entry).forEach { alias -> addAll(groupsByTitleAlias[titleKey(entry, alias)].orEmpty()) }
    }

    private fun titleAliases(entry: MediaEntry): Set<String> = listOfNotNull(
        entry.title,
        entry.titleEnglish
    ).map { MediaIdentity.normalizedTitle(it) }
        .filter { it.isNotBlank() }
        .toSet()

    private fun japaneseAlias(entry: MediaEntry): String? = entry.titleJapanese
        ?.let { MediaIdentity.normalizedTitle(it) }
        ?.takeIf { it.length >= 2 }

    private fun titleKey(entry: MediaEntry, alias: String): String = "${entry.type.name}:$alias"
}

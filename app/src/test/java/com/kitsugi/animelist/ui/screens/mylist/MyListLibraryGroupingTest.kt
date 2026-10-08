package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListLibraryGroupingTest {

    private fun entry(
        id: Int,
        title: String,
        source: String,
        malId: Int? = null,
        type: MediaType = MediaType.Anime,
        year: Int? = 2024
    ) = MediaEntry(
        id = id,
        title = title,
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = malId,
        year = year
    )

    @Test
    fun groupsSameTitleAcrossProvidersAndCollectsTheirBadges() {
        val mal = entry(id = 12, title = "Frieren: Beyond Journey's End", source = "mal", malId = 52991)
        val anilist = entry(id = 10, title = "Sousou no Frieren", source = "anilist", malId = 100_000_870)
            .copy(titleEnglish = "Frieren: Beyond Journey's End")
        val kitsu = entry(id = 8, title = "Frieren: Beyond Journey's End", source = "kitsu", malId = 300_123_456)

        val grouped = groupMyListEntries(listOf(mal, anilist, kitsu))

        assertEquals(1, grouped.size)
        // Yeni kural: Temsilci önceliği AniList > MAL > Kitsu > Shikimori > Simkl > TMDB
        assertEquals(anilist.id, grouped.single().entry.id)
        assertEquals(listOf("mal", "anilist", "kitsu"), grouped.single().sourceIds)
    }


    @Test
    fun combinedRepresentativeKeepsAdultFlagFromAnyProvider() {
        val shikimori = entry(id = 12, title = "Same title", source = "shikimori", malId = 52991)
            .copy(isAdult = true)
        val anilist = entry(id = 10, title = "Same title", source = "anilist", malId = 52991)

        val grouped = groupMyListEntries(listOf(shikimori, anilist))

        assertEquals(1, grouped.size)
        assertEquals(anilist.id, grouped.single().entry.id)
        assertTrue("Combined card should stay blurred if any provider marks it adult", grouped.single().entry.isAdult)
        assertEquals(listOf("shikimori", "anilist"), grouped.single().sourceIds)
    }

    @Test
    fun doesNotGroupDifferentMediaTypesOrConflictingProviderIds() {
        val first = entry(id = 4, title = "Same Title", source = "mal", malId = 1234)
        val otherMalRecord = entry(id = 3, title = "Same Title", source = "mal", malId = 5678)
        val manga = entry(id = 2, title = "Same Title", source = "kitsu", malId = 300_000_004, type = MediaType.Manga)

        val grouped = groupMyListEntries(listOf(first, otherMalRecord, manga))

        assertEquals(3, grouped.size)
        assertFalse(grouped.any { it.sourceIds.size > 1 })
    }

    @Test
    fun normalizesProviderAliasesForBadges() {
        val grouped = groupMyListEntries(
            listOf(
                entry(id = 2, title = "Alias title", source = "jikan", malId = 999),
                entry(id = 1, title = "Alias title", source = "myanimelist", malId = 1000)
            )
        )

        assertEquals(2, grouped.size) // Conflicting MAL IDs must remain separate.
        assertTrue(grouped.all { it.sourceIds == listOf("mal") })
        assertEquals("mal", sourceBadgeId("Jikan"))
        assertEquals("shikimori", sourceBadgeId("Shiki"))
        assertEquals(null, sourceBadgeId("manual"))
    }
}

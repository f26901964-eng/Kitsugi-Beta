package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSyncCandidateIndexTest {
    private class Group(val name: String)

    @Test
    fun indexesProviderIdsPrimaryAndEnglishTitlesAndJapaneseAliases() {
        val index = CrossSyncCandidateIndex<Group>()
        val byId = Group("id")
        val byEnglishTitle = Group("english")
        val byJapaneseTitle = Group("japanese")

        index.add(byId, anime("Id title", malId = 55))
        index.add(byEnglishTitle, anime("Japanese title", titleEnglish = "English title"))
        index.add(
            byJapaneseTitle,
            anime("Kimi no Na wa", titleJapanese = "君の名は", year = 2016)
        )

        assertEquals(setOf(byId), index.possibleMatches(anime("Renamed title", malId = 55)))
        assertEquals(setOf(byEnglishTitle), index.possibleMatches(anime("English title")))
        assertEquals(
            setOf(byJapaneseTitle),
            index.possibleMatches(anime("Kimi no Na wa Movie", titleJapanese = "君の名は", year = 2016))
        )
        assertEquals(setOf(byEnglishTitle), index.sharingTitleAlias(anime("English title")))
    }

    @Test
    fun indexPartitionsTitlesByMediaTypeAndDoesNotReturnUnrelatedGroups() {
        val index = CrossSyncCandidateIndex<Group>()
        val groups = (0 until 2_000).map { position ->
            Group("group-$position").also { group ->
                index.add(group, anime("Anime $position", malId = position + 1))
            }
        }
        val movieGroup = Group("movie")
        index.add(movieGroup, anime("Anime 42", type = MediaType.Movie, malId = 42))

        val candidates = index.possibleMatches(anime("Anime 42", malId = 43))
        assertEquals(setOf(groups[42]), candidates)
        assertTrue(movieGroup !in candidates)
    }

    private fun anime(
        title: String,
        type: MediaType = MediaType.Anime,
        malId: Int? = null,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        year: Int? = null
    ) = MediaEntry(
        id = 1,
        title = title,
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = if (malId == null) "manual" else "mal",
        malId = malId,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        year = year
    )
}

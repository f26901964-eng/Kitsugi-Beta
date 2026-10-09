package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListFilterHelpersTest {

    private val entries = listOf(
        entry(
            id = 1,
            title = "Eski Anime",
            english = "Old Anime",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            year = 1998,
            favorite = true
        ),
        entry(
            id = 2,
            title = "Yeni Manga",
            type = MediaType.Manga,
            status = WatchStatus.Planned,
            score = null,
            year = 2025
        ),
        entry(
            id = 3,
            title = "Tekrar",
            type = MediaType.Anime,
            status = WatchStatus.Repeating,
            score = 4,
            year = 2016,
            repeating = true
        )
    )

    @Test
    fun filters_mediaTypeScoreAndYear_withSharedContract() {
        val result = filter(
            type = "anime",
            score = "high",
            year = "classic"
        )

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun favoritesPseudoStatus_andLocalizedTitleSearch_areSupported() {
        val favorites = filter(status = "favorites")
        val englishSearch = filter(query = "old anime")

        assertEquals(listOf(1), favorites.map { it.id })
        assertEquals(listOf(1), englishSearch.map { it.id })
    }

    @Test
    fun repeatingExtra_acceptsRepeatingStatus() {
        val result = filter(extra = "repeating")

        assertEquals(listOf(3), result.map { it.id })
    }

    @Test
    fun statusGrouping_usesTheSameStableOrderAsListem() {
        val groups = groupMyListEntriesByStatus(entries)

        assertEquals(
            listOf(WatchStatus.Repeating, WatchStatus.Planned, WatchStatus.Completed),
            groups.map { it.first }
        )
        assertTrue(groups.all { it.second.isNotEmpty() })
    }

    @Test
    fun sortByScore_keepsHighestScoreFirst() {
        val result = applySort(entries, "score")

        assertEquals(listOf(1, 3, 2), result.map { it.id })
    }

    private fun filter(
        query: String = "",
        status: String = "all",
        type: String = "all",
        favorite: String = "all",
        score: String = "all",
        year: String = "all",
        extra: String = "all"
    ): List<MediaEntry> = filterMyListEntries(
        entries = entries,
        searchQuery = query,
        selectedStatusFilterId = status,
        selectedTypeFilterId = type,
        selectedFavoriteFilterId = favorite,
        selectedScoreFilterId = score,
        selectedYearFilterId = year,
        selectedExtraFilterId = extra
    )

    private fun entry(
        id: Int,
        title: String,
        english: String? = null,
        type: MediaType,
        status: WatchStatus,
        score: Int?,
        year: Int,
        favorite: Boolean = false,
        repeating: Boolean = false
    ) = MediaEntry(
        id = id,
        title = title,
        titleEnglish = english,
        type = type,
        status = status,
        score = score,
        progress = 0,
        total = null,
        isFavorite = favorite,
        isRepeating = repeating,
        year = year
    )
}

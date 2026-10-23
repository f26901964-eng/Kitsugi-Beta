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

    @Test
    fun searchDoesNotMatchSourceSubtitleTypeStatusOrSyntheticIds() {
        val sourceHeavyEntries = listOf(
            entry(
                id = 10,
                title = "Bleach",
                type = MediaType.Manga,
                status = WatchStatus.Completed,
                score = 8,
                year = 2001
            ).copy(source = "shikimori", subtitle = "Manga • 2001", malId = 400_086_123),
            entry(
                id = 11,
                title = "86 - Eighty Six",
                type = MediaType.Anime,
                status = WatchStatus.Watching,
                score = 9,
                year = 2021
            ).copy(source = "bangumi", subtitle = "bangumi", malId = 500_111_222)
        )

        fun runQuery(query: String) = filterMyListEntries(
            entries = sourceHeavyEntries,
            searchQuery = query,
            selectedStatusFilterId = "all",
            selectedTypeFilterId = "all",
            selectedFavoriteFilterId = "all",
            selectedScoreFilterId = "all",
            selectedYearFilterId = "all",
            selectedExtraFilterId = "all"
        ).map { it.id }

        assertTrue(runQuery("shikimori").isEmpty())
        assertTrue(runQuery("bangumi").isEmpty())
        assertTrue(runQuery("manga").isEmpty())
        assertTrue(runQuery("completed").isEmpty())
        // "86" yalnızca başlığı gerçekten 86 olan yapımı bulmalı; malId=400086123 eşleşmemeli.
        assertEquals(listOf(11), runQuery("86"))
    }

    @Test
    fun searchSupportsPunctuationMultiTokenTurkishCjkAndAliasesWithoutFalseMatches() {
        val catalog = listOf(
            entry(
                id = 20,
                title = "Kaijuu 8-gou",
                english = "Kaiju No. 8",
                type = MediaType.Anime,
                status = WatchStatus.Watching,
                score = 8,
                year = 2024
            ).copy(titleJapanese = "怪獣8号"),
            entry(
                id = 21,
                title = "Kaijuu 8-gou",
                english = null,
                type = MediaType.Anime,
                status = WatchStatus.Watching,
                score = 8,
                year = 2024
            ),
            entry(
                id = 22,
                title = "Isekai Ojisan",
                english = "Uncle from Another World",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 7,
                year = 2022
            )
        )
        val aliases = mapOf(21 to setOf("Kaiju No. 8", "怪兽8号"))

        fun runQuery(query: String) = filterMyListEntries(
            entries = catalog,
            searchQuery = query,
            selectedStatusFilterId = "all",
            selectedTypeFilterId = "all",
            selectedFavoriteFilterId = "all",
            selectedScoreFilterId = "all",
            selectedYearFilterId = "all",
            selectedExtraFilterId = "all",
            searchAliasesByEntryId = aliases
        ).map { it.id }

        assertEquals(listOf(20, 21), runQuery("kaiju no 8"))
        assertEquals(listOf(20, 21), runQuery("kaiju 8"))
        assertTrue("Numeric token 2 must not match 8", runQuery("kaiju 2").isEmpty())
        assertEquals(listOf(22), runQuery("İsekai"))
        assertEquals(listOf(20, 21), runQuery("kaıju no 8"))
        // CJK sorgusu yalnızca ilgili CJK başlıkları bulmalı; boş needle hatasıyla tüm listeyi döndürmemeli.
        assertEquals(listOf(20), runQuery("怪獣8号"))
        assertEquals(listOf(21), runQuery("怪兽8号"))
        // Tam 4 haneli yıl eşleşir, 2 haneli parça eşleşmez.
        assertEquals(listOf(20, 21), runQuery("2024"))
        assertTrue(runQuery("24").isEmpty())
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

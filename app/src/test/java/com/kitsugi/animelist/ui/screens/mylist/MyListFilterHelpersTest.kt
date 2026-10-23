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

    // ── Eklenme tarihi sıralaması (2026-10-23 düzeltmesi) ────────────────────

    @Test
    fun newestSort_ordersByAddedAtNotLocalRowId() {
        // Yerel id, toplu içe aktarımda API sayfa sırasıdır; ekleme sırası DEĞİL.
        // Yüksek id'li ama erken eklenmiş kayıt "Son eklenen"de üste çıkmamalı.
        val list = listOf(
            entry(id = 90, title = "İlk eklenen", addedAt = 1_000L),
            entry(id = 10, title = "Son eklenen", addedAt = 3_000L),
            entry(id = 50, title = "Ortada eklenen", addedAt = 2_000L)
        )

        assertEquals(listOf(10, 50, 90), applySort(list, "newest").map { it.id })
        assertEquals(listOf(90, 50, 10), applySort(list, "oldest").map { it.id })
    }

    @Test
    fun unknownAddedAt_sinksToEndInBothDirections() {
        val list = listOf(
            entry(id = 1, title = "Bilinmiyor", addedAt = 0L),
            entry(id = 2, title = "Eski", addedAt = 100L),
            entry(id = 3, title = "Yeni", addedAt = 200L)
        )

        assertEquals(listOf(3, 2, 1), applySort(list, "newest").map { it.id })
        assertEquals(listOf(2, 3, 1), applySort(list, "oldest").map { it.id })
    }

    @Test
    fun scoreAsc_keepsUnratedLast() {
        val list = listOf(
            entry(id = 1, title = "Puansız", score = null),
            entry(id = 2, title = "Düşük", score = 3),
            entry(id = 3, title = "Yüksek", score = 9)
        )

        assertEquals(listOf(2, 3, 1), applySort(list, "score_asc").map { it.id })
        assertEquals(listOf(3, 2, 1), applySort(list, "score").map { it.id })
    }

    @Test
    fun progressSort_usesCompletionRatioNotRawEpisodes() {
        val list = listOf(
            entry(id = 1, title = "Bitti", progress = 12, total = 12),
            entry(id = 2, title = "Uzun yol", progress = 100, total = 1000),
            entry(id = 3, title = "Yarıda", progress = 6, total = 12),
            entry(id = 4, title = "Toplamı bilinmiyor", progress = 500, total = null)
        )

        // Oran: %100, %50, %10; toplamı bilinmeyen sonda.
        assertEquals(listOf(1, 3, 2, 4), applySort(list, "progress").map { it.id })
    }

    @Test
    fun updatedSort_normalizesMillisecondTimestamps() {
        // Bangumi updatedAt'yi milisaniye yazar; normalize edilmezse eski Bangumi
        // kaydı (×1000 büyük değer) daha yeni saniye-damgalı kaydı ezerdi.
        val list = listOf(
            entry(id = 1, title = "Yeni (saniye)", updatedAt = 1_700_000_000L),
            entry(id = 2, title = "Eski (milisaniye)", updatedAt = 1_600_000_000_000L)
        )

        assertEquals(listOf(1, 2), applySort(list, "updated_desc").map { it.id })
    }

    @Test
    fun titleSort_usesDisplayedTitleForSelectedLanguage() {
        val list = listOf(
            entry(id = 1, title = "Zzz Romaji", english = "Apple Title"),
            entry(id = 2, title = "Aaa Romaji", english = "Zebra Title")
        )

        // Başlık dili verilmezse depolanan başlık, verilirse EKRANDAKİ başlık sıralanır.
        assertEquals(listOf(2, 1), applySort(list, "title").map { it.id })
        assertEquals(
            listOf(1, 2),
            applySort(list, "title", titleLanguage = "ENGLISH").map { it.id }
        )
    }

    // ── Düz akış / durum bölümü kararı ───────────────────────────────────────

    @Test
    fun explicitSort_disablesStatusSections() {
        // Varsayılan gezinme: bölümler açık.
        assertEquals(
            false,
            myListUsesFlatSortedLayout(MY_LIST_SORT_DEFAULT, "all", separatedListStyle = true)
        )
        // Kullanıcı sıralama seçti: global sıra kazanır, bölümler kapanır.
        assertEquals(
            true,
            myListUsesFlatSortedLayout("newest", "all", separatedListStyle = true)
        )
        assertEquals(
            true,
            myListUsesFlatSortedLayout("score", "all", separatedListStyle = true)
        )
        // Tek durum filtresi: varsayılan sıralamada bile düz (tek bölüm başlığı gürültü).
        assertEquals(
            true,
            myListUsesFlatSortedLayout(MY_LIST_SORT_DEFAULT, "completed", separatedListStyle = true)
        )
        // Ayrılmış tarz kapalı: her zaman düz.
        assertEquals(
            true,
            myListUsesFlatSortedLayout(MY_LIST_SORT_DEFAULT, "all", separatedListStyle = false)
        )
    }

    // ── Eklenme dönemi bölümleri ─────────────────────────────────────────────

    @Test
    fun addedPeriodGrouping_bucketsByAgeInSeenOrder() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.of(2026, 10, 23)
            .atTime(12, 0)
            .atZone(zone)
            .toEpochSecond()
        val day = 86_400L
        val list = listOf(
            entry(id = 1, title = "Bugün", addedAt = now),
            entry(id = 2, title = "Dün", addedAt = now - day),
            entry(id = 3, title = "Bu hafta", addedAt = now - 3 * day),
            entry(id = 4, title = "Geçen ay", addedAt = now - 40 * day),
            entry(id = 5, title = "Bilinmiyor", addedAt = 0L)
        )

        val groups = groupMyListEntriesByAddedPeriod(list, nowEpochSeconds = now)

        assertEquals(
            listOf(
                MyListAddedPeriod.TODAY,
                MyListAddedPeriod.YESTERDAY,
                MyListAddedPeriod.THIS_WEEK,
                MyListAddedPeriod.LAST_MONTH,
                MyListAddedPeriod.UNKNOWN
            ),
            groups.map { it.first }
        )
        assertEquals(listOf(1, 2, 3, 4, 5), groups.flatMap { it.second }.map { it.id })
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
        type: MediaType = MediaType.Anime,
        status: WatchStatus = WatchStatus.Watching,
        score: Int? = null,
        year: Int = 2020,
        favorite: Boolean = false,
        repeating: Boolean = false,
        progress: Int = 0,
        total: Int? = null,
        addedAt: Long = 0L,
        updatedAt: Long = 0L
    ) = MediaEntry(
        id = id,
        title = title,
        titleEnglish = english,
        type = type,
        status = status,
        score = score,
        progress = progress,
        total = total,
        isFavorite = favorite,
        isRepeating = repeating,
        year = year,
        addedAt = addedAt,
        updatedAt = updatedAt
    )
}

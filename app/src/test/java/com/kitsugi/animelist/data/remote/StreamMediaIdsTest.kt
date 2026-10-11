package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamMediaIdsTest {
    @Test
    fun tmdbSearchResultNeverTreatsTmdbIdAsMalId() {
        val result = JikanSearchResult(
            malId = 1622,
            title = "Supernatural",
            subtitle = "Dizi",
            type = MediaType.TvShow,
            total = null,
            score = null,
            isAdult = false,
            imageUrl = null,
            year = 2005,
            source = "tmdb",
            tmdbId = 1622
        )

        val ids = result.toStreamMediaIds()

        assertNull(ids.malId)
        assertNull(ids.aniListId)
        assertEquals(1622, ids.tmdbId)
    }

    @Test
    fun aniListStableIdIsDecodedAndRealMalIdIsPreserved() {
        val result = result(source = "anilist", id = 100_000_123, realMalId = 46)

        val ids = result.toStreamMediaIds()

        assertEquals(46, ids.malId)
        assertEquals(123, ids.aniListId)
    }

    @Test
    fun simklIdDoesNotLeakIntoMalSlot() {
        val result = result(source = "simkl", id = 98_765, realMalId = 46)

        val ids = result.toStreamMediaIds()

        assertEquals(46, ids.malId)
        assertNull(ids.aniListId)
    }

    @Test
    fun malResultKeepsItsActualMalId() {
        val ids = result(source = "mal", id = 46).toStreamMediaIds()

        assertEquals(46, ids.malId)
    }

    @Test
    fun bangumiStableIdIsDecodedIntoItsOwnNamespace() {
        val ids = result(source = "bangumi", id = 500_035_658).toStreamMediaIds()

        assertNull(ids.malId)
        assertNull(ids.tmdbId)
        assertEquals(35_658, ids.bangumiId)
    }

    @Test
    fun libraryBangumiStableIdIsDecodedIntoItsOwnNamespace() {
        val entry = mediaEntry(id = 37, source = "bangumi", malId = 500_035_658)

        val ids = entry.toStreamMediaIds()

        assertNull(ids.malId)
        assertEquals(35_658, ids.bangumiId)
    }

    @Test
    fun libraryRowIdIsNeverUsedAsExternalMediaId() {
        val entry = mediaEntry(
            id = 37,
            source = "tmdb",
            type = MediaType.TvShow,
            tmdbId = 1622
        )

        val ids = entry.toStreamMediaIds()

        assertNull(ids.malId)
        assertNull(ids.aniListId)
        assertEquals(1622, ids.tmdbId)
    }

    @Test
    fun libraryMalEntryUsesStoredMalIdInsteadOfRoomRowId() {
        val entry = mediaEntry(id = 37, source = "mal", malId = 46)

        val ids = entry.toStreamMediaIds()

        assertEquals(46, ids.malId)
    }

    @Test
    fun libraryAniListEntryUsesStableMediaIdInsteadOfRoomRowId() {
        val entry = mediaEntry(id = 37, source = "anilist", malId = 100_000_123)

        val ids = entry.toStreamMediaIds()

        assertNull(ids.malId)
        assertEquals(123, ids.aniListId)
    }

    @Test
    fun legacyStreamTypeInferencePrefersTmdbSeriesWhenNoAnimeIdsExist() {
        assertEquals(
            MediaType.TvShow,
            inferStreamMediaType(malId = null, aniListId = null, tmdbId = 1622)
        )
        assertEquals(
            MediaType.Movie,
            inferStreamMediaType(isMovie = true, tmdbId = 27205)
        )
        assertEquals(
            MediaType.Anime,
            inferStreamMediaType(malId = 46)
        )
    }

    private fun result(
        source: String,
        id: Int,
        realMalId: Int? = null
    ) = JikanSearchResult(
        malId = id,
        title = "Title",
        subtitle = "",
        type = MediaType.TvShow,
        total = null,
        score = null,
        isAdult = false,
        imageUrl = null,
        year = null,
        source = source,
        realMalId = realMalId
    )

    private fun mediaEntry(
        id: Int,
        source: String,
        malId: Int? = null,
        tmdbId: Int? = null,
        type: MediaType = MediaType.Anime
    ) = MediaEntry(
        id = id,
        title = "Title",
        type = type,
        subtitle = "",
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = malId,
        tmdbId = tmdbId
    )
}

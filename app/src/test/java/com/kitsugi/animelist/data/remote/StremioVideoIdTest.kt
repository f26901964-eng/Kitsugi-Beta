package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class StremioVideoIdTest {
    @Test
    fun movieLookupUsesMovieResourceAndBareIds() {
        val lookup = StremioVideoId.forContent(
            isMovie = true,
            imdbId = "tt1234567",
            kitsuId = 42,
            season = 3,
            episode = 8
        )

        assertEquals("movie", lookup.contentType)
        assertEquals("tt1234567", lookup.imdbVideoId)
        assertEquals("kitsu:42", lookup.kitsuVideoId)
    }

    @Test
    fun seriesLookupIncludesSeasonAndEpisodeForImdb() {
        val lookup = StremioVideoId.forContent(
            isMovie = false,
            imdbId = "tt1234567",
            kitsuId = 42,
            season = 3,
            episode = 8
        )

        assertEquals("series", lookup.contentType)
        assertEquals("tt1234567:3:8", lookup.imdbVideoId)
        assertEquals("kitsu:42:8", lookup.kitsuVideoId)
    }

    @Test
    fun invalidIdsAreOmittedAndSeriesNumbersAreOneBased() {
        val lookup = StremioVideoId.forContent(
            isMovie = false,
            imdbId = "tt1234567",
            kitsuId = 0,
            season = 0,
            episode = 0
        )

        assertEquals("series", lookup.contentType)
        assertEquals("tt1234567:1:1", lookup.imdbVideoId)
        assertEquals(null, lookup.kitsuVideoId)
    }
}

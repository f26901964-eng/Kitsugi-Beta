package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DetailCacheReviewIsolationTest {

    @Before
    fun clearCache() {
        DetailCache.clear()
    }

    @Test
    fun `typed review lookup does not fall back to a different media type`() {
        val movieReview = KitsugiReview(
            username = "movie-reviewer",
            avatarUrl = null,
            score = 8,
            summary = "A movie review"
        )
        DetailCache.putMediaReviews("tmdb", 42, listOf(movieReview), MediaType.Movie.name)

        assertEquals(listOf(movieReview), DetailCache.getMediaReviews("tmdb", 42, MediaType.Movie.name))
        assertNull(DetailCache.getMediaReviews("tmdb", 42, MediaType.TvShow.name))
    }
}

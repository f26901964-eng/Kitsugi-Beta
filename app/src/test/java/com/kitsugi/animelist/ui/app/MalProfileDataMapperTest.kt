package com.kitsugi.animelist.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalProfileDataMapperTest {

    @Test
    fun parsesDetailedStatisticsIncludingRatingsAndRankedItems() {
        val body = """
            {
              "data": {
                "scores": [{"score": 10, "count": 40}, {"score": 8, "count": 15}],
                "types": [{"type": "TV", "count": 31}],
                "statuses": [{"status": "watching", "count": 4}],
                "ratings": [{"rating": "PG-13", "count": 18}],
                "genres": [{"mal_id": 1, "name": "Action", "count": 21, "mean_score": 8.4}],
                "studios": [{"mal_id": 17, "name": "Sunrise", "count": 12, "mean_score": 8.1}],
                "voice_actors": [{"mal_id": 23, "name": "Voice Actor", "count": 9, "mean_score": 8.0}]
              }
            }
        """.trimIndent()

        val result = MalProfileDataMapper.parseDetailedStatistics(body)

        assertEquals(listOf(8, 10), result.scores.map { it.score })
        assertEquals(listOf("TV"), result.types.map { it.format })
        assertEquals("watching", result.statuses.single().status)
        assertEquals("PG-13", result.ratings.single().format)
        assertEquals("Action", result.genres.single().name)
        assertEquals(17, result.studios.single().id)
        assertEquals("Voice Actor", result.voiceActors.single().name)
    }

    @Test
    fun parsesClubsAndPaginationMetadata() {
        val body = """
            {
              "data": [{
                "mal_id": 71,
                "name": "Example Club",
                "url": "https://myanimelist.net/clubs.php?cid=71",
                "members": 123,
                "category": "Anime"
              }],
              "pagination": {"current_page": 1, "has_next_page": true}
            }
        """.trimIndent()

        val result = MalProfileDataMapper.parseClubsPage(body)

        assertTrue(result.hasNextPage)
        assertEquals(71, result.items.single().clubId)
        assertEquals("Example Club", result.items.single().name)
        assertEquals(123, result.items.single().memberCount)
    }

    @Test
    fun parsesReviewsRecommendationsAndPagination() {
        val reviewsBody = """
            {
              "data": [{
                "mal_id": 90,
                "score": 9,
                "helpful_count": 14,
                "date": "2025-02-03T12:00:00+00:00",
                "review": "A thoughtful review.",
                "entry": {
                  "mal_id": 12,
                  "type": "manga",
                  "title": "Example Manga",
                  "url": "https://myanimelist.net/manga/12/example",
                  "images": {"jpg": {"image_url": "https://cdn.example/manga.jpg"}}
                }
              }],
              "pagination": {"has_next_page": true}
            }
        """.trimIndent()
        val recommendationsBody = """
            {
              "data": [{
                "mal_id": 91,
                "date": "2025-02-04T12:00:00+00:00",
                "recommendation": "Similar tone and characters.",
                "entry": {"mal_id": 24, "type": "anime", "title": "Example Anime"}
              }],
              "pagination": {"has_next_page": false}
            }
        """.trimIndent()

        val reviews = MalProfileDataMapper.parseReviews(reviewsBody)
        val recommendations = MalProfileDataMapper.parseRecommendations(recommendationsBody)

        assertTrue(reviews.hasNextPage)
        assertEquals(90, reviews.items.single().reviewId)
        assertEquals("manga", reviews.items.single().kind)
        assertEquals(14, reviews.items.single().helpfulCount)
        assertEquals("Example Manga", reviews.items.single().title)
        assertFalse(recommendations.hasNextPage)
        assertEquals("Similar tone and characters.", recommendations.items.single().recommendationText)
        assertEquals("https://myanimelist.net/anime/24", recommendations.items.single().url)
    }
}

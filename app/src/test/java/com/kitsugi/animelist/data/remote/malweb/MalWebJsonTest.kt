package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalWebJsonTest {

    @Test
    fun paginationComesFromObservedNextLinkRatherThanItemCount() {
        val club = MalWebParser.Club(
            clubId = 71,
            name = "Example Club",
            url = "https://myanimelist.net/clubs.php?cid=71",
            image = MalWebParser.Images(""),
            memberCount = 123,
            category = "Anime"
        )

        val oneItemWithoutNext = MalWebJson.clubs(listOf(club), page = 1, hasNextPage = false)
        val oneItemWithNext = MalWebJson.clubs(listOf(club), page = 1, hasNextPage = true)
        val reviewsWithoutNext = MalWebJson.reviews(emptyList(), page = 1, hasNextPage = false)

        assertFalse(oneItemWithoutNext.getJSONObject("pagination").getBoolean("has_next_page"))
        assertTrue(oneItemWithNext.getJSONObject("pagination").getBoolean("has_next_page"))
        assertFalse(reviewsWithoutNext.getJSONObject("pagination").getBoolean("has_next_page"))
    }
}

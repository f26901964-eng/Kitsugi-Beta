package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SimklScoreAndFormattingTest {

    @Test
    fun extractSimklScoreInfo_fallsBackToMalWhenSimklRatingIsZero() {
        val obj = JSONObject(
            """
            {
              "title": "Kaijuu 8 Gou",
              "en_title": "Kaiju No. 8",
              "year": 2024,
              "ep_count": 12,
              "rank": 295,
              "ids": { "simkl_id": 196710, "mal": 52588 },
              "ratings": {
                "simkl": { "rating": 0, "votes": 0 },
                "mal": { "rating": 8.29, "votes": 390000, "rank": 295 }
              }
            }
            """.trimIndent()
        )
        val parsed = extractSimklScoreInfo(obj)
        assertEquals(83, parsed.score100)
        assertEquals(8.29, parsed.rawScoreDouble ?: 0.0, 0.001)
        assertEquals(390000, parsed.votes)
        assertEquals(295, parsed.rank)
        assertEquals(12, parsed.totalEpisodes)
    }

    @Test
    fun parseSimklArray_readsMalImdbAndSimklRatingsAcrossSearchAndExplore() {
        val json = """
        [
          {
            "title": "Kaijuu 8 Gou",
            "en_title": "Kaiju No. 8",
            "year": 2024,
            "ep_count": 12,
            "ids": { "simkl_id": 196710, "mal": "52588" },
            "ratings": { "mal": { "rating": 8.3, "votes": 120000 } }
          },
          {
            "title": "Severance",
            "type": "tv",
            "year": 2022,
            "ids": { "simkl": 141234, "tmdb": 95396 },
            "ratings": { "imdb": { "rating": 8.7, "votes": 250000 } }
          }
        ]
        """.trimIndent()

        val client = SimklApiClient()
        val parsed = client.parseSimklArray(json, "anime", 20)
        assertEquals(2, parsed.size)
        assertEquals(83, parsed[0].score)
        assertEquals(8.3, parsed[0].rawScoreDouble ?: 0.0, 0.001)
        assertEquals(52588, parsed[0].realMalId)
        assertEquals(12, parsed[0].total)

        assertEquals(87, parsed[1].score)
        assertEquals(8.7, parsed[1].rawScoreDouble ?: 0.0, 0.001)
        assertEquals(MediaType.TvShow, parsed[1].type)
    }

    @Test
    fun formatScore_handlesBothTenAndHundredScalesWithoutCrashing() {
        assertEquals("8/10", PreferenceHelpers.formatScore(83, "POINT_10", false, 8.29))
        assertEquals("8.3/10", PreferenceHelpers.formatScore(83, "POINT_10_DECIMAL", false, 8.29))
        assertEquals("83", PreferenceHelpers.formatScore(83, "POINT_100", false, 8.29))
        assertEquals("★★★★☆", PreferenceHelpers.formatScore(83, "POINT_5", false, 8.29))
        assertEquals("★★★★★★★★☆☆", PreferenceHelpers.formatScore(83, "STARS", false, 8.29))
        assertNotNull(PreferenceHelpers.formatScore(95, "STARS", false, 9.5))
    }
}

package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimklSyncContractTest {
    @Test fun animeWritesUseShowsNotReadCategory() {
        assertEquals("shows", SimklSyncContract.writeKey("anime"))
        assertEquals("shows", SimklSyncContract.writeKey("shows"))
        assertEquals("movies", SimklSyncContract.writeKey("movies"))
    }

    @Test fun animeReadUsesNestedShow() {
        // Shape from SIMKL/API apiary.apib Get All Items, NOT an invented anime object.
        val fixture = JSONObject("""{"anime":[{"status":"watching","show":{"title":"Fate/Stay Night","ids":{"simkl":46116,"mal":"22297"}}}]}""")
        val media = fixture.getJSONArray("anime").getJSONObject(0)
            .getJSONObject(SimklSyncContract.readMediaKey("anime"))
        assertEquals(46116, media.getJSONObject("ids").getInt("simkl"))
        assertEquals("movie", SimklSyncContract.readMediaKey("movies"))
    }

    @Test fun listReceiptCountsArraysAndReportsUnmatched() {
        val result = SimklSyncContract.receipt("""{"added":{"movies":[],"shows":[{"ids":{"mal":16498}}]},"not_found":{"shows":[{"ids":{"mal":999999}}]}}""")
        assertEquals(1, result.added)
        assertEquals(1, result.notFound)
    }

    @Test fun historyReceiptCountsNumbersIncludingEpisodes() {
        val result = SimklSyncContract.receipt("""{"added":{"movies":1,"shows":0,"episodes":12},"not_found":{"movies":[],"shows":[],"episodes":[]}}""")
        assertEquals(13, result.added)
        assertEquals(0, result.notFound)
    }

    @Test fun emptyMalformedOrIgnoredPayloadIsNotSuccess() {
        listOf("", "null", "{}", "not json", """{"added":{"shows":0,"movies":0},"not_found":{"shows":[]}}""")
            .forEach { body ->
                assertTrue("Must reject: $body", runCatching { SimklSyncContract.receipt(body) }.isFailure)
            }
    }

    @Test fun allUnmatchedIsVisible() {
        val result = SimklSyncContract.receipt("""{"added":{"shows":[]},"not_found":{"shows":[{}]}}""")
        assertEquals(0, result.added)
        assertEquals(1, result.notFound)
    }

    @Test fun twelveWatchedEpisodesMeansOneThroughTwelveNotOnlyTwelve() {
        val episodes = SimklSyncContract.animeEpisodes(12)
        assertEquals(12, episodes.length())
        (1..12).forEach { assertEquals(it, episodes.getJSONObject(it - 1).getInt("number")) }
    }

    @Test fun overlappingCategoriesCollapseToOneLibraryRow() {
        // Anime filmi hem `movies` hem `anime` ucunda döner → Movie + Anime iki satır yazılıyordu.
        fun row(id: Int, title: String, type: MediaType, simklId: Int) = MediaEntry(
            id = id,
            title = title,
            type = type,
            status = WatchStatus.Completed,
            score = 8,
            progress = 1,
            total = 1,
            source = "simkl",
            year = 2025,
            simklId = simklId
        )
        val movies = listOf(row(1, "Demon Slayer: Infinity Castle", MediaType.Movie, 2098300))
        val shows = emptyList<MediaEntry>()
        val anime = listOf(row(2, "Demon Slayer: Infinity Castle", MediaType.Anime, 2098300))

        val merged = SimklSyncContract.mergeCategories(movies, shows, anime)

        assertEquals(1, merged.size)
        assertEquals(MediaType.Anime, merged.single().type)
        assertEquals(2098300, merged.single().simklId)
    }

    @Test fun distinctSimklItemsSurviveCategoryMerge() {
        fun row(id: Int, title: String, simklId: Int) = MediaEntry(
            id = id,
            title = title,
            type = MediaType.Anime,
            status = WatchStatus.Planned,
            score = null,
            progress = 0,
            total = null,
            source = "simkl",
            simklId = simklId
        )
        val merged = SimklSyncContract.mergeCategories(
            listOf(row(1, "Frieren", 111)),
            emptyList(),
            listOf(row(2, "Frieren", 222))
        )
        assertEquals(2, merged.size)
    }

    @Test fun unsupportedMangaIsNotSilentlyATvShow() {
        assertTrue(runCatching { SimklSyncContract.writeKey("manga") }.isFailure)
    }
}

package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ExploreMappingSourceScopeTest {
    private fun entry(
        source: String,
        id: Int,
        title: String,
        tmdbId: Int? = null,
        simklId: Int? = null
    ) = MediaEntry(
        id = id,
        title = title,
        type = MediaType.Anime,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = id,
        tmdbId = tmdbId,
        simklId = simklId
    )

    private fun result(source: String, id: Int, title: String, tmdbId: Int? = null) = JikanSearchResult(
        malId = id,
        title = title,
        subtitle = "",
        type = MediaType.Anime,
        total = null,
        score = null,
        isAdult = false,
        imageUrl = null,
        year = 2011,
        source = source,
        tmdbId = tmdbId
    )

    @Test
    fun `Bangumi discover result is not marked as listed by a Simkl record`() {
        val simkl = entry(source = "simkl", id = 1001, title = "Steins Gate", tmdbId = 42)
        val bangumiResult = result(source = "bangumi", id = 500_000_876, title = "Steins;Gate", tmdbId = 42)

        val map = generateExploreEntryMap(listOf(simkl))

        assertNull(getMediaEntryFromMap(bangumiResult, map))
    }

    @Test
    fun `Bangumi and Simkl rows retain their own list membership`() {
        val simkl = entry(source = "simkl", id = 1001, title = "Steins Gate", tmdbId = 42)
        val bangumi = entry(source = "bangumi", id = 500_000_876, title = "Old Bangumi title", tmdbId = 42)
        val map = generateExploreEntryMap(listOf(simkl, bangumi))

        assertSame(bangumi, getMediaEntryFromMap(result("bangumi", 500_000_876, "Steins;Gate", 42), map))
        assertSame(simkl, getMediaEntryFromMap(result("simkl", 1001, "Steins Gate", 42), map))
    }

    @Test
    fun `Simkl source ids still match their own Simkl record`() {
        val simkl = entry(source = "simkl", id = 987654, title = "Old title", simklId = 1001)
        val map = generateExploreEntryMap(listOf(simkl))

        assertSame(simkl, getMediaEntryFromMap(result("simkl", 1001, "Different display title"), map))
    }

    @Test
    fun `provider aliases are canonicalized without merging providers`() {
        assertEquals("bangumi", canonicalMediaSourceId("bgm.tv"))
        assertEquals("mal", canonicalMediaSourceId("jikan"))
        assertEquals("simkl", canonicalMediaSourceId("simkl"))
        assertEquals("bangumi", canonicalMediaSourceId("bangumi"))
    }
}

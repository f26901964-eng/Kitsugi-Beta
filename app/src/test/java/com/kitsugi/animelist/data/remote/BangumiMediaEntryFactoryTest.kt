package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiMediaEntryFactoryTest {
    private fun result(
        source: String = "bangumi",
        id: Int = 500_000_876,
        type: MediaType = MediaType.Anime,
        subjectType: Int = BangumiApiClient.SubjectType.ANIME
    ) = JikanSearchResult(
        malId = id,
        title = "Bangumi title",
        subtitle = "FİLM",
        type = type,
        total = 24,
        score = 9,
        isAdult = false,
        imageUrl = null,
        year = 2024,
        source = source,
        realMalId = 5681,
        titleEnglish = "English title",
        titleJapanese = "日本語",
        bangumiSubjectType = subjectType
    )

    @Test
    fun `factory preserves Bangumi stable id instead of resolved MAL id`() {
        val entry = BangumiMediaEntryFactory.fromSearchResult(result(type = MediaType.Movie), "Synopsis")!!

        assertEquals("bangumi", entry.source)
        assertEquals(500_000_876, entry.malId)
        assertEquals(MediaType.Movie, entry.type)
        assertEquals("Synopsis", entry.synopsis)
    }

    @Test
    fun `factory accepts Bangumi REAL movies and TV shows without relabeling them as anime`() {
        val movie = BangumiMediaEntryFactory.fromSearchResult(
            result(type = MediaType.Movie, subjectType = BangumiApiClient.SubjectType.REAL)
        )!!
        val tvShow = BangumiMediaEntryFactory.fromSearchResult(
            result(type = MediaType.TvShow, subjectType = BangumiApiClient.SubjectType.REAL)
        )!!

        assertEquals(MediaType.Movie, movie.type)
        assertEquals(MediaType.TvShow, tvShow.type)
    }

    @Test
    fun `factory converts raw Bangumi subject id to stable id`() {
        val entry = BangumiMediaEntryFactory.fromSearchResult(result(id = 876))!!

        assertEquals(500_000_876, entry.malId)
    }

    @Test
    fun `factory rejects other providers and invalid Bangumi ids`() {
        assertNull(BangumiMediaEntryFactory.fromSearchResult(result(source = "simkl")))
        assertNull(BangumiMediaEntryFactory.fromSearchResult(result(id = 120_000_000)))
        assertNull(
            BangumiMediaEntryFactory.fromSearchResult(
                result(subjectType = BangumiApiClient.SubjectType.GAME)
            )
        )
        assertNull(BangumiMediaEntryFactory.fromSearchResult(result(subjectType = 0)))
        assertNull(
            BangumiMediaEntryFactory.fromSearchResult(
                result(type = MediaType.Anime, subjectType = BangumiApiClient.SubjectType.REAL)
            )
        )
    }
}

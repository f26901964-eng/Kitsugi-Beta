package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomMediaIdsTest {

    @Test
    fun `stable ids are unwrapped per namespace`() {
        assertEquals(FandomSourceIds(malId = 44511), FandomMediaIds.fromStableId("jikan", 44511))
        assertEquals(FandomSourceIds(malId = 44511), FandomMediaIds.fromStableId("MAL", 44511))
        assertEquals(FandomSourceIds(malId = 44511), FandomMediaIds.fromStableId("anilist", 44511))
        assertEquals(FandomSourceIds(aniListId = 127230), FandomMediaIds.fromStableId("anilist", 100_127_230))
        assertEquals(FandomSourceIds(kitsuId = 1376), FandomMediaIds.fromStableId("kitsu", 300_001_376))
        assertEquals(FandomSourceIds(kitsuId = 1376), FandomMediaIds.fromStableId("kitsu", 1376))
        assertEquals(FandomSourceIds(shikimoriId = 5114), FandomMediaIds.fromStableId("shikimori", 400_005_114))
        assertEquals(FandomSourceIds(bangumiId = 35658), FandomMediaIds.fromStableId("bangumi", 500_035_658))
        assertEquals(FandomSourceIds(tmdbId = 1429), FandomMediaIds.fromStableId("tmdb", 1429))
        assertTrue(FandomMediaIds.fromStableId("simkl", 150_000_001).isEmpty)
        assertTrue(FandomMediaIds.fromStableId("anilist", 0).isEmpty)
        assertTrue(FandomMediaIds.fromStableId(null, 5).isEmpty)
    }

    @Test
    fun `localized type and role labels`() {
        assertTrue(FandomMediaIds.isMangaType("Manga"))
        assertTrue(FandomMediaIds.isMangaType("Hafif Roman"))
        assertTrue(FandomMediaIds.isMangaType("Tek Bölüm"))
        assertTrue(FandomMediaIds.isMangaType("LIGHT_NOVEL"))
        assertFalse(FandomMediaIds.isMangaType("TV Dizisi"))
        assertFalse(FandomMediaIds.isMangaType("Anime"))
        assertFalse(FandomMediaIds.isMangaType(null))
        assertTrue(FandomMediaIds.isMovieType("Film"))
        assertTrue(FandomMediaIds.isMovieType("MOVIE"))
        assertTrue(FandomMediaIds.isMainRole("Ana Karakter"))
        assertTrue(FandomMediaIds.isMainRole("MAIN"))
        assertTrue(FandomMediaIds.isMainRole("主角"))
        assertFalse(FandomMediaIds.isMainRole("Yardımcı Karakter"))
        assertFalse(FandomMediaIds.isMainRole("Supporting"))
    }

    @Test
    fun `wikidata lookups follow priority per media kind`() {
        val ids = FandomSourceIds(aniListId = 11757, malId = 11757, kitsuId = 6589, bangumiId = 12345, tmdbId = 45782)
        assertEquals(
            listOf("P8729" to "11757", "P4086" to "11757", "P11495" to "6589", "P5732" to "12345", "P4983" to "45782"),
            FandomMediaIds.wikidataLookups(ids, isManga = false, isMovie = false)
        )
        assertEquals(
            "P4947" to "45782",
            FandomMediaIds.wikidataLookups(FandomSourceIds(tmdbId = 45782), isManga = false, isMovie = true).single()
        )
        assertEquals(
            listOf("P8731" to "11757", "P4087" to "11757", "P11494" to "6589", "P5732" to "12345"),
            FandomMediaIds.wikidataLookups(ids, isManga = true, isMovie = false)
        )
    }

    @Test
    fun `stable cache keys prefer anilist`() {
        assertEquals("al:5", FandomSourceIds(aniListId = 5, malId = 6).stableKey(isManga = false, isMovie = false))
        assertEquals("mal:m:6", FandomSourceIds(malId = 6).stableKey(isManga = true, isMovie = false))
        assertEquals("tmdb:movie:7", FandomSourceIds(tmdbId = 7).stableKey(isManga = false, isMovie = true))
        assertNull(FandomSourceIds().stableKey(isManga = false, isMovie = false))
    }
}

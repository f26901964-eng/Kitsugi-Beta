package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomGuideCatalogTest {

    @Test
    fun `english wiki gets english candidates first`() {
        val candidates = FandomGuideCatalog.candidates("en", FandomGuideGroup.ORGANIZATIONS)
        assertEquals("Organizations", candidates.first())
        assertTrue(candidates.contains("Factions"))
    }

    @Test
    fun `turkish wiki gets turkish candidates first and english fallback`() {
        val candidates = FandomGuideCatalog.candidates("tr", FandomGuideGroup.ABILITIES)
        assertEquals("Yetenekler", candidates.first())
        assertTrue(candidates.contains("Abilities"))
    }

    @Test
    fun `all groups present and deduped`() {
        val all = FandomGuideCatalog.allCandidates(null)
        assertEquals(FandomGuideGroup.values().toList(), all.keys.toList())
        all.values.forEach { names ->
            assertEquals(names.size, names.distinct().size)
            assertTrue(names.isNotEmpty())
        }
    }
}

class FandomMediaQueriesTest {

    @Test
    fun `anilist offset ids unwrap to anilist id`() {
        val query = FandomMediaQueries.fromMedia(
            source = "anilist",
            mediaId = 121_415_155, // 21_415_155 + AniList offseti
            isManga = false,
            isMovie = false,
            titles = listOf("Kimi no Na wa.")
        )
        assertEquals(null, query?.ids?.malId)
        assertEquals(21_415_155, query?.ids?.aniListId)
    }

    @Test
    fun `mal source below offset is mal id and real mal fills gaps`() {
        val query = FandomMediaQueries.fromMedia(
            source = "mal",
            mediaId = 32281,
            tmdbId = 63072,
            isManga = false,
            isMovie = true,
            titles = listOf("Kimi no Na wa.", "Your Name.")
        )
        assertEquals(32281, query?.ids?.malId)
        assertEquals(63072, query?.ids?.tmdbId)
        assertTrue(query?.isMovie == true)
        assertEquals(listOf("Kimi no Na wa.", "Your Name."), query?.fallbackTitles)
    }

    @Test
    fun `no ids but titles still yields query for name fallback`() {
        val query = FandomMediaQueries.fromMedia(
            source = "extension",
            mediaId = 555,
            isManga = false,
            isMovie = false,
            titles = listOf("Monster")
        )
        assertEquals(null, query?.ids?.stableKey(false, false))
        assertEquals(listOf("Monster"), query?.fallbackTitles)
        // Kimliksiz sorgu da önbelleklenebilir olmalı (eklenti mangası / TV): başlık anahtarı.
        assertEquals("title:a:monster", query?.cacheKey())
    }

    @Test
    fun `simkl source resolves simkl stable key`() {
        val query = FandomMediaQueries.fromMedia(
            source = "simkl",
            mediaId = 555,
            isManga = false,
            isMovie = false,
            titles = listOf("Monster")
        )
        assertEquals("simkl:555", query?.ids?.stableKey(false, false))
    }

    @Test
    fun `title cache key is normalized and manga aware`() {
        val manga = FandomMediaQueries.fromMedia(
            source = null,
            mediaId = null,
            isManga = true,
            isMovie = false,
            titles = listOf("  Solo   Leveling: Ragnarok! ")
        )
        assertEquals("title:m:solo_leveling_ragnarok", manga?.cacheKey())
        val withIds = FandomMediaQueries.fromMedia(
            source = "mal",
            mediaId = 1,
            isManga = false,
            isMovie = false,
            titles = listOf("Cowboy Bebop")
        )
        assertEquals(withIds?.ids?.stableKey(false, false), withIds?.cacheKey())
    }

    @Test
    fun `nothing usable yields null`() {
        assertNull(
            FandomMediaQueries.fromMedia(
                source = "simkl",
                mediaId = 0,
                isManga = false,
                isMovie = false,
                titles = emptyList()
            )
        )
    }

    @Test
    fun `non-latin titles are dropped`() {
        val query = FandomMediaQueries.fromMedia(
            source = "mal",
            mediaId = 1,
            isManga = false,
            isMovie = false,
            titles = listOf("君の名は。", "Kimi no Na wa.")
        )
        assertEquals(listOf("Kimi no Na wa."), query?.fallbackTitles)
    }
}

class FandomApiParsersGuideTest {

    @Test
    fun `category info maps existing categories with sizes`() {
        val json = """
            {"query":{"pages":[
              {"pageid":1,"title":"Category:Organizations","categoryinfo":{"size":42,"pages":40,"subcats":2}},
              {"pageid":2,"title":"Category:Missing","missing":true},
              {"pageid":3,"title":"Category:Empty","categoryinfo":{"size":0}}
            ]}}
        """.trimIndent()
        val info = FandomApiParsers.parseCategoryInfo(json)
        assertEquals(42, info["Category:Organizations"])
        assertEquals(0, info["Category:Empty"])
        assertNull(info["Category:Missing"])
        assertTrue(FandomApiParsers.parseCategoryInfo("bozuk").isEmpty())
    }

    @Test
    fun `category members returns main namespace titles`() {
        val json = """
            {"query":{"categorymembers":[
              {"pageid":10,"title":"Tokyo Jujutsu High"},
              {"pageid":11,"title":"Kyoto Jujutsu High"},
              {"pageid":10,"title":"Tokyo Jujutsu High"}
            ]}}
        """.trimIndent()
        assertEquals(
            listOf("Tokyo Jujutsu High", "Kyoto Jujutsu High"),
            FandomApiParsers.parseCategoryMembers(json)
        )
        assertTrue(FandomApiParsers.parseCategoryMembers(null).isEmpty())
    }

    @Test
    fun `category members page exposes the continue token`() {
        val json = """
            {"continue":{"cmcontinue":"page|123|456","continue":"-||"},"query":{"categorymembers":[
              {"pageid":10,"title":"Emilia Camp"}
            ]}}
        """.trimIndent()
        assertEquals("page|123|456", FandomApiParsers.parseContinueToken(json))
        assertNull(FandomApiParsers.parseContinueToken("""{"query":{"categorymembers":[]}}"""))
        assertNull(FandomApiParsers.parseContinueToken(null))
    }

    @Test
    fun `guide group content reports more pages only with a token`() {
        assertTrue(
            FandomGuideGroupContent(
                group = FandomGuideGroup.PLACES,
                category = "Locations",
                items = listOf(FandomGuideItem("Karakura Town")),
                nextToken = "page|0|1"
            ).hasMore
        )
        assertTrue(
            !FandomGuideGroupContent(group = FandomGuideGroup.PLACES, category = "Locations").hasMore
        )
    }

    @Test
    fun `search hits strip markup from snippets`() {
        val json = """
            {"query":{"search":[
              {"pageid":1,"title":"Satoru Gojo","snippet":"<span class=\"searchmatch\">Gojo</span> is a teacher."},
              {"pageid":2,"title":"Empty","snippet":"   "}
            ]}}
        """.trimIndent()
        val hits = FandomApiParsers.parseSearchHits(json)
        assertEquals(2, hits.size)
        assertEquals("Satoru Gojo", hits[0].title)
        assertEquals("Gojo is a teacher.", hits[0].snippet)
        assertNull(hits[1].snippet)
    }
}

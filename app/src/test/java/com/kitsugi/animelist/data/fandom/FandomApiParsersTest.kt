package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yanıt örnekleri 2026-10-12 tarihli canlı Fandom API çıktılarından sadeleştirilmiştir. */
class FandomApiParsersTest {

    @Test
    fun `siteinfo uses the real server after redirects`() {
        val json = """{"batchcomplete":"","query":{"general":{"mainpage":"Chainsaw Man Wiki","sitename":"Chainsaw Man Wiki",""" +
            """"lang":"en","server":"https://chainsaw-man.fandom.com","scriptpath":"","wikiid":"chainsawman"},""" +
            """"statistics":{"pages":5327,"articles":589}}}"""
        val info = FandomApiParsers.parseSiteInfo("chainsawman", json)
        assertNotNull(info)
        assertEquals("chainsaw-man", info!!.wikiId)
        assertEquals("Chainsaw Man Wiki", info.siteName)
        assertEquals(589, info.articles)

        val turkish = """{"query":{"general":{"sitename":"One Piece Vikisi","lang":"tr",""" +
            """"server":"https://onepiece.fandom.com","scriptpath":"/tr"},"statistics":{"articles":10}}}"""
        assertEquals("tr.onepiece", FandomApiParsers.parseSiteInfo("tr.onepiece", turkish)!!.wikiId)

        assertNull(FandomApiParsers.parseSiteInfo("x", "<html>not json</html>"))
        assertNull(FandomApiParsers.parseSiteInfo("x", """{"error":{"code":"x"}}"""))
    }

    @Test
    fun `title lookups follow normalization and redirects`() {
        val json = """
            {"batchcomplete":true,"query":{
              "normalized":[{"fromencoded":false,"from":"kenzo Tenma","to":"Kenzo Tenma"}],
              "redirects":[{"from":"Kenzo Tenma","to":"Kenzō Tenma"},{"from":"Heinrich Lunge","to":"Heinrich Runge"}],
              "pages":[
                {"pageid":2065,"ns":0,"title":"Kenzō Tenma",
                 "pageprops":{"fandomdescription":"Dr. Kenzō Tenma is a Japanese neurosurgeon.","infoboxes":"[]"},
                 "langlinks":[{"lang":"tr","url":"https://monster.fandom.com/tr/wiki/Kenzo_Tenma","title":"Kenzo Tenma"}]},
                {"pageid":2087,"ns":0,"title":"Heinrich Runge","pageprops":{"disambiguation":""}},
                {"ns":0,"title":"Nina Fortner","missing":true}
              ]}}
        """.trimIndent()
        val result = FandomApiParsers.parseTitleLookups(
            listOf("kenzo Tenma", "Heinrich Lunge", "Nina Fortner", "Unrequested"),
            json
        )
        val tenma = result.getValue("kenzo Tenma")
        assertEquals("Kenzō Tenma", tenma.resolvedTitle)
        assertEquals(2065L, tenma.pageId)
        assertEquals("Dr. Kenzō Tenma is a Japanese neurosurgeon.", tenma.description)
        assertEquals("tr.monster" to "Kenzo Tenma", tenma.turkishLink)
        assertTrue(result.getValue("Heinrich Lunge").isDisambiguation)
        assertFalse(result.getValue("Nina Fortner").exists)
        assertFalse(result.getValue("Unrequested").exists)
        assertTrue(FandomApiParsers.parseTitleLookups(listOf("a"), "oops").isEmpty())
    }

    @Test
    fun `sections skip template and hidden sections and flag spoilers`() {
        val json = """
            {"parse":{"title":"Satoru Gojo","pageid":162,"sections":[
              {"toclevel":1,"level":"2","line":"Appearance","index":"1"},
              {"toclevel":1,"level":"2","line":"Battles &amp; Events","index":"9"},
              {"toclevel":2,"level":"3","line":"<i>Overview</i>","index":"4"},
              {"toclevel":1,"level":"2","line":"References","index":"11"},
              {"toclevel":1,"level":"2","line":"Navigation","index":"T-1"}
            ]}}
        """.trimIndent()
        val sections = FandomApiParsers.parseSections(json)
        assertNotNull(sections)
        assertEquals(listOf("Appearance", "Battles & Events", "Overview"), sections!!.map { it.title })
        assertEquals(listOf(1, 9, 4), sections.map { it.index })
        assertEquals(listOf(2, 2, 3), sections.map { it.level })
        assertEquals(listOf(false, true, false), sections.map { it.spoiler })
        assertNull(FandomApiParsers.parseSections("""{"error":{"code":"missingtitle"}}"""))
    }

    @Test
    fun `parsed text supports both format versions`() {
        assertEquals("<div>x</div>", FandomApiParsers.parseParsedText("""{"parse":{"title":"A","text":"<div>x</div>"}}"""))
        assertEquals("<p>y</p>", FandomApiParsers.parseParsedText("""{"parse":{"text":{"*":"<p>y</p>"}}}"""))
        assertNull(FandomApiParsers.parseParsedText("""{"error":{"code":"nosuchsection"}}"""))
    }

    @Test
    fun `community search results map urls to wiki ids`() {
        val json = """
            {"results":[
              {"id":"633396","name":"Naoki Urasawa's Monster Wiki","language":"en",
               "description":"Renowned neurosurgeon Kenzō Tenma saves the life of a young boy named Johan Liebert.",
               "url":"https://obluda.fandom.com/","hub":"anime","pageCount":200,"pageViews":20000},
              {"id":"16748","name":"Monster Wiki","language":"en","url":"https://monster.fandom.com/","hub":"tv","pageCount":4000},
              {"id":"1","name":"Not Fandom","url":"https://example.com/"}
            ]}
        """.trimIndent()
        val hits = FandomApiParsers.parseCommunitySearch(json)
        assertEquals(listOf("obluda", "monster"), hits.map { it.wikiId })
        assertEquals("anime", hits[0].hub)
        assertEquals(200, hits[0].pageCount)
        assertNull(hits[1].description)
        assertNull(hits[1].pageViews)
    }

    @Test
    fun `external link usage`() {
        assertTrue(FandomApiParsers.parseExtUrlUsageFound("""{"query":{"exturlusage":[{"pageid":1}]}}"""))
        assertFalse(FandomApiParsers.parseExtUrlUsageFound("""{"query":{"exturlusage":[]}}"""))
        assertFalse(FandomApiParsers.parseExtUrlUsageFound(null))
    }

    @Test
    fun `search titles`() {
        val json = """{"query":{"search":[{"ns":0,"title":"Satoru Gojo"},{"ns":0,"title":"Gojo Clan"}]}}"""
        assertEquals(listOf("Satoru Gojo", "Gojo Clan"), FandomApiParsers.parseSearchTitles(json))
    }
}

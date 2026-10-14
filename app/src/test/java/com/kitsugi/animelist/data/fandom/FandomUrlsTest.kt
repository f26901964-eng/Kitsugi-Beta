package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomUrlsTest {

    @Test
    fun `normalize accepts wikidata P4073 forms and rejects invalid ids`() {
        assertEquals("jujutsu-kaisen", FandomUrls.normalize("jujutsu-kaisen"))
        assertEquals("tr.onepiece", FandomUrls.normalize("TR.OnePiece"))
        assertEquals("naruto", FandomUrls.normalize("en.naruto"))
        assertEquals("pt-br.pokemon", FandomUrls.normalize("pt-br.pokemon"))
        assertNull(FandomUrls.normalize("bad id"))
        assertNull(FandomUrls.normalize("a.b.c"))
        assertNull(FandomUrls.normalize("community"))
        assertNull(FandomUrls.normalize(""))
        assertNull(FandomUrls.normalize("../etc"))
        assertNull(FandomUrls.normalize(null))
    }

    @Test
    fun `root and language detection`() {
        assertTrue(FandomUrls.isRoot("onepiece"))
        assertFalse(FandomUrls.isRoot("tr.onepiece"))
        assertFalse(FandomUrls.isRoot("bad id"))
        assertEquals("tr", FandomUrls.languageOf("tr.onepiece"))
        assertNull(FandomUrls.languageOf("onepiece"))
    }

    @Test
    fun `base and api urls follow fandom language paths`() {
        assertEquals("https://jujutsu-kaisen.fandom.com", FandomUrls.baseUrl("jujutsu-kaisen"))
        assertEquals("https://onepiece.fandom.com/tr", FandomUrls.baseUrl("tr.onepiece"))
        assertEquals("https://onepiece.fandom.com/tr/api.php", FandomUrls.apiUrl("tr.onepiece"))
        assertNull(FandomUrls.baseUrl("bad id"))
    }

    @Test
    fun `wiki id is extracted from community urls`() {
        assertEquals("obluda", FandomUrls.wikiIdFromUrl("https://obluda.fandom.com/"))
        assertEquals("onepiece", FandomUrls.wikiIdFromUrl("https://onepiece.wikia.com/wiki/Monkey_D_Luffy"))
        assertEquals("tr.onepiece", FandomUrls.wikiIdFromUrl("https://onepiece.fandom.com/tr/"))
        assertEquals("solo-leveling", FandomUrls.wikiIdFromUrl("https://solo-leveling.fandom.com/wiki/Sung_Jinwoo"))
        assertEquals("zh-tw.example", FandomUrls.wikiIdFromUrl("https://example.fandom.com/zh-tw/wiki/X"))
        assertNull(FandomUrls.wikiIdFromUrl("https://example.com/"))
        assertNull(FandomUrls.wikiIdFromUrl("https://community.fandom.com/wiki/Special:NotAValidWiki"))
        assertNull(FandomUrls.wikiIdFromUrl("not a url"))
    }

    @Test
    fun `api query encodes parameters safely`() {
        val url = FandomUrls.apiQuery(
            "jujutsu-kaisen",
            listOf("action" to "query", "titles" to "Satoru Gojo|Yuji Itadori")
        )
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://jujutsu-kaisen.fandom.com/api.php?"))
        assertTrue(url.contains("titles=Satoru%20Gojo%7CYuji%20Itadori"))
        assertNull(FandomUrls.apiQuery("bad id", emptyList()))
    }

    @Test
    fun `page urls use underscores and percent encoding`() {
        assertEquals(
            "https://jujutsu-kaisen.fandom.com/wiki/Satoru_Gojo",
            FandomUrls.pageUrl("jujutsu-kaisen", "Satoru Gojo")
        )
        assertEquals(
            "https://obluda.fandom.com/wiki/Kenz%C5%8D_Tenma",
            FandomUrls.pageUrl("obluda", "Kenzō Tenma")
        )
        assertEquals(
            "https://onepiece.fandom.com/tr/wiki/Monkey_D._Luffy",
            FandomUrls.pageUrl("tr.onepiece", "Monkey D. Luffy")
        )
    }

    @Test
    fun `article hrefs resolve wiki identity and reject non-article namespaces`() {
        assertEquals(
            "solo-leveling" to "Sung Jinwoo",
            FandomUrls.parseArticleUrl("/wiki/Sung_Jinwoo", "solo-leveling")
        )
        assertEquals(
            "tr.onepiece" to "Monkey D. Luffy",
            FandomUrls.parseArticleUrl("https://onepiece.fandom.com/wiki/Monkey_D._Luffy", "tr.onepiece")
        )
        assertEquals(
            "jujutsu-kaisen" to "Satoru Gojo",
            FandomUrls.parseArticleUrl("https://jujutsu-kaisen.fandom.com/wiki/Satoru_Gojo?so=search", "other-wiki")
        )
        assertNull(FandomUrls.parseArticleUrl("/wiki/File:Gojo.png", "jujutsu-kaisen"))
        assertNull(FandomUrls.parseArticleUrl("https://example.com/wiki/Gojo", "jujutsu-kaisen"))
    }

    @Test
    fun `article ids split into wiki and title`() {
        assertEquals("jujutsu-kaisen" to "Satoru Gojo", FandomUrls.parseArticleId("jujutsu-kaisen:Satoru_Gojo"))
        assertEquals("ru.onepiece" to "Аниме One Piece", FandomUrls.parseArticleId("ru.onepiece:Аниме_One_Piece"))
        assertNull(FandomUrls.parseArticleId("nocolon"))
        assertNull(FandomUrls.parseArticleId(":Title"))
        assertNull(FandomUrls.parseArticleId("wiki:"))
    }

    @Test
    fun `images are scaled only for plain fandom cdn urls`() {
        val raw = "https://static.wikia.nocookie.net/solo-leveling/images/8/8b/Jinwoo4.jpg/revision/latest?cb=20250411080707"
        assertEquals(
            "https://static.wikia.nocookie.net/solo-leveling/images/8/8b/Jinwoo4.jpg/revision/latest/scale-to-width-down/360?cb=20250411080707",
            FandomUrls.scaledImage(raw, 360)
        )
        val alreadyScaled = "https://static.wikia.nocookie.net/x/images/a.png/revision/latest/scale-to-width-down/200?cb=1"
        assertEquals(alreadyScaled, FandomUrls.scaledImage(alreadyScaled, 360))
        val smart = "https://static.wikia.nocookie.net/x/images/a.png/revision/latest/smart/width/40/height/40?cb=1"
        assertEquals(smart, FandomUrls.scaledImage(smart, 360))
        assertEquals("https://example.com/a.png", FandomUrls.scaledImage("https://example.com/a.png", 360))
    }

    @Test
    fun `image urls are normalized to https`() {
        assertEquals(
            "https://static.wikia.nocookie.net/x/images/a.png",
            FandomUrls.normalizeImageUrl("//static.wikia.nocookie.net/x/images/a.png")
        )
        assertEquals("https://a.b/c.png", FandomUrls.normalizeImageUrl("http://a.b/c.png"))
        assertNull(FandomUrls.normalizeImageUrl("data:image/png;base64,xyz"))
        assertNull(FandomUrls.normalizeImageUrl(null))
    }

    @Test
    fun `wikipedia urls and ids are normalized and resolved`() {
        assertTrue(FandomUrls.isWikipedia("wikipedia.en"))
        assertTrue(FandomUrls.isWikipedia("wikipedia.tr"))
        assertTrue(FandomUrls.isWikipedia("wikipedia:ko"))
        assertTrue(FandomUrls.isWikipedia("wp.ja"))
        assertEquals("wikipedia.en", FandomUrls.normalize("wikipedia.en"))
        assertEquals("wikipedia.tr", FandomUrls.normalize("WIKIPEDIA:TR"))
        assertEquals("wikipedia.ko", FandomUrls.normalize("wp.ko"))
        assertTrue(FandomUrls.isRoot("wikipedia.en"))
        assertFalse(FandomUrls.isRoot("wikipedia.tr"))
        assertEquals("tr", FandomUrls.languageOf("wikipedia.tr"))
        assertNull(FandomUrls.languageOf("wikipedia.en"))

        assertEquals("https://en.wikipedia.org", FandomUrls.baseUrl("wikipedia.en"))
        assertEquals("https://tr.wikipedia.org", FandomUrls.baseUrl("wikipedia.tr"))
        assertEquals("https://en.wikipedia.org/w/api.php", FandomUrls.apiUrl("wikipedia.en"))
        assertEquals("https://tr.wikipedia.org/w/api.php", FandomUrls.apiUrl("wikipedia.tr"))
        assertEquals(
            "https://en.wikipedia.org/wiki/Crash_Landing_on_You",
            FandomUrls.pageUrl("wikipedia.en", "Crash Landing on You")
        )
        assertEquals(
            "https://tr.wikipedia.org/wiki/Squid_Game",
            FandomUrls.pageUrl("wikipedia.tr", "Squid Game")
        )

        assertEquals("wikipedia.en", FandomUrls.wikiIdFromUrl("https://en.wikipedia.org/wiki/Crash_Landing_on_You"))
        assertEquals("wikipedia.tr", FandomUrls.wikiIdFromUrl("https://tr.wikipedia.org/wiki/Crash_Landing_on_You_(dizi)"))
        assertEquals(
            "wikipedia.en" to "Crash Landing on You",
            FandomUrls.parseArticleUrl("https://en.wikipedia.org/wiki/Crash_Landing_on_You", "wikipedia.en")
        )
        assertEquals(
            "wikipedia.tr" to "Squid Game",
            FandomUrls.parseArticleUrl("/wiki/Squid_Game", "wikipedia.tr")
        )
    }

    @Test
    fun `withOriginalFormat only touches fandom cdn urls once`() {
        val url = "https://static.wikia.nocookie.net/w/images/a/ab/X.webp/revision/latest?cb=1"
        assertEquals("$url&format=original", FandomUrls.withOriginalFormat(url))
        val noQuery = "https://static.wikia.nocookie.net/w/images/a/ab/X.webp/revision/latest"
        assertEquals("$noQuery?format=original", FandomUrls.withOriginalFormat(noQuery))
        val already = "$url&format=webp"
        assertEquals(already, FandomUrls.withOriginalFormat(already))
        assertEquals("https://example.com/x.png", FandomUrls.withOriginalFormat("https://example.com/x.png"))
    }
}

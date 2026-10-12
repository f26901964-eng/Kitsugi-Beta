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
}

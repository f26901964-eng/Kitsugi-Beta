package com.kitsugi.animelist.data.fandom

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `character page survives a json round trip`() {
        val page = FandomCharacterPage(
            wikiId = "jujutsu-kaisen",
            pageTitle = "Satoru Gojo",
            siteName = "Jujutsu Kaisen Wiki",
            description = "Satoru Gojo is one of the main protagonists.",
            infobox = FandomInfobox(
                title = "Satoru Gojo",
                images = listOf(FandomImage("https://static.wikia.nocookie.net/x/a.png", "Anime")),
                groups = listOf(
                    FandomInfoboxGroup(
                        header = "Professional Status",
                        fields = listOf(FandomInfoboxField("Status", "Hidden", "status", spoiler = true))
                    )
                )
            ),
            sections = listOf(FandomSectionRef("1", "Appearance", 2)),
            matchSource = FandomMatchSource.WIKIDATA,
            confidence = FandomConfidence.CERTAIN
        )
        val encoded = json.encodeToString(FandomCharacterPage.serializer(), page)
        assertEquals(page, json.decodeFromString(FandomCharacterPage.serializer(), encoded))
        assertEquals("https://jujutsu-kaisen.fandom.com/wiki/Satoru_Gojo", page.pageUrl)
        assertEquals("jujutsu-kaisen", page.matchedWikiId)
    }

    @Test
    fun `older cache entries decode with defaults`() {
        val legacy = """{"wikiId":"naruto","pageTitle":"Naruto Uzumaki","matchSource":"NAME","confidence":"PROBABLE","futureField":1}"""
        val decoded = json.decodeFromString(FandomCharacterPage.serializer(), legacy)
        assertEquals("naruto", decoded.matchedWikiId)
        assertEquals("Naruto Uzumaki", decoded.matchedPageTitle)
        assertTrue(decoded.sections.isEmpty())
    }

    @Test
    fun `turkish page keeps the english match for reporting`() {
        val page = FandomCharacterPage(
            wikiId = "tr.onepiece",
            pageTitle = "Monkey D. Luffy",
            matchSource = FandomMatchSource.WIKIDATA,
            confidence = FandomConfidence.CERTAIN,
            englishWikiId = "onepiece",
            englishPageTitle = "Monkey D. Luffy"
        )
        assertEquals("onepiece", page.matchedWikiId)
        assertEquals("https://onepiece.fandom.com/tr/wiki/Monkey_D._Luffy", page.pageUrl)
    }

    @Test
    fun `article page round trips with image infobox and builds its source url`() {
        val article = FandomArticlePage(
            wikiId = "tr.onepiece",
            pageTitle = "Monkey D. Luffy",
            siteName = "One Piece Vikisi",
            description = "Hasır Şapka Korsanları'nın kaptanı.",
            infobox = FandomInfobox(
                title = "Monkey D. Luffy",
                images = listOf(FandomImage("https://static.wikia.nocookie.net/x/luffy.png", "Timeskip")),
                groups = listOf(FandomInfoboxGroup(fields = listOf(FandomInfoboxField("Age", "19"))))
            ),
            sections = listOf(FandomSectionRef("1", "Appearance", 2)),
            sectionsFailed = true
        )
        val encoded = json.encodeToString(FandomArticlePage.serializer(), article)
        assertEquals(article, json.decodeFromString(FandomArticlePage.serializer(), encoded))
        val legacy = json.decodeFromString(
            FandomArticlePage.serializer(),
            """{"wikiId":"onepiece","pageTitle":"Monkey D. Luffy"}"""
        )
        assertFalse(legacy.sectionsFailed)
        assertEquals("https://onepiece.fandom.com/tr/wiki/Monkey_D._Luffy", article.pageUrl)
    }

    @Test
    fun `section plain text omits image urls but keeps text and legacy blocks decode`() {
        val image = FandomImage("https://static.wikia.nocookie.net/x/hero.png", "The hero")
        val content = FandomSectionContent(
            listOf(
                FandomTextBlock(FandomBlockKind.HEADING, "Outfit"),
                FandomTextBlock(FandomBlockKind.BULLET, "Blindfold"),
                FandomTextBlock(FandomBlockKind.IMAGE, "The hero", image)
            )
        )
        assertEquals("Outfit\n\n• Blindfold", content.plainText())
        val legacy = json.decodeFromString(FandomTextBlock.serializer(), """{"kind":"PARAGRAPH","text":"old cache"}""")
        assertEquals(null, legacy.image)
    }

    @Test
    fun `confirmation flags follow confidence and wiki source`() {
        val nameMatched = FandomCharacterPage(
            wikiId = "obluda",
            pageTitle = "Kenzō Tenma",
            matchSource = FandomMatchSource.NAME,
            confidence = FandomConfidence.PROBABLE,
            mediaKey = "mal:a:19",
            wikiSource = FandomMatchSource.NAME,
            wikiConfidence = FandomConfidence.PROBABLE
        )
        assertTrue(nameMatched.needsConfirmation)
        assertTrue(nameMatched.isWikiUnconfirmedNameMatch)

        val idBasedWiki = nameMatched.copy(wikiSource = FandomMatchSource.WIKIDATA, wikiConfidence = FandomConfidence.CERTAIN)
        assertFalse(idBasedWiki.isWikiUnconfirmedNameMatch)
        assertFalse(nameMatched.copy(confidence = FandomConfidence.CERTAIN).needsConfirmation)
    }
}

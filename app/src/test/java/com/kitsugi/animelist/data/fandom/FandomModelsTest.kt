package com.kitsugi.animelist.data.fandom

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
            sections = listOf(FandomSectionRef(1, "Appearance", 2)),
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
    fun `section content plain text marks bullets`() {
        val content = FandomSectionContent(
            listOf(
                FandomTextBlock(FandomBlockKind.HEADING, "Outfit"),
                FandomTextBlock(FandomBlockKind.BULLET, "Blindfold")
            )
        )
        assertEquals("Outfit\n\n• Blindfold", content.plainText())
    }
}

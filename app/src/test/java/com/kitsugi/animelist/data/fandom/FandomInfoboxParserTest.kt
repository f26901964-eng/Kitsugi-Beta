package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `pageprops.infoboxes` örneği, Solo Leveling Wiki'deki Sung Jinwoo sayfasının
 * (2026-10-12) yapısından sadeleştirilmiştir.
 */
class FandomInfoboxParserTest {

    private val sample = """
        [{"parser_tag_version":5,"data":[
          {"type":"image","data":[
            {"url":"https://static.wikia.nocookie.net/solo-leveling/images/8/8b/Jinwoo4.jpg/revision/latest?cb=1","caption":"Webtoon","isVideo":false},
            {"url":"//static.wikia.nocookie.net/solo-leveling/images/8/84/Jinwoo_Anime.png/revision/latest?cb=2","caption":"<i>Anime</i>","isVideo":false},
            {"url":"https://static.wikia.nocookie.net/x/video.mp4","isVideo":true}
          ]},
          {"type":"title","data":{"value":"Sung Jinwoo","source":"Name"}},
          {"type":"group","data":{"value":[
            {"type":"data","data":{"label":"<b>Hangul</b>","value":"성진우","source":"Hangul"}},
            {"type":"data","data":{"label":"<b>Status</b>","value":"<i>Alive</i>","source":"Status"}}
          ]}},
          {"type":"group","data":{"value":[
            {"type":"header","data":{"value":"<b>Personal Information</b>"}},
            {"type":"data","data":{"label":"Age","value":"<ul><li>20-25</li><li>40-45</li></ul>","source":"Age"}},
            {"type":"data","data":{"label":"","value":"Male","source":"gender"}},
            {"type":"data","data":{"label":"Empty","value":"","source":"x"}}
          ]}},
          {"type":"navigation","data":{"value":"<a>nav</a>"}}
        ]}]
    """.trimIndent()

    @Test
    fun `parses title images groups and spoiler flags`() {
        val box = FandomInfoboxParser.parse(sample)
        assertNotNull(box)
        box!!
        assertEquals("Sung Jinwoo", box.title)
        assertEquals(2, box.images.size)
        assertEquals(
            "https://static.wikia.nocookie.net/solo-leveling/images/8/84/Jinwoo_Anime.png/revision/latest?cb=2",
            box.images[1].url
        )
        assertEquals("Anime", box.images[1].caption)

        assertEquals(2, box.groups.size)
        val first = box.groups[0]
        assertNull(first.header)
        assertEquals(listOf("Hangul", "Status"), first.fields.map { it.label })
        assertFalse(first.fields[0].spoiler)
        assertTrue(first.fields[1].spoiler)

        val second = box.groups[1]
        assertEquals("Personal Information", second.header)
        assertEquals(2, second.fields.size)
        assertEquals("20-25 · 40-45", second.fields[0].value)
        assertEquals("Gender", second.fields[1].label)

        assertTrue(box.searchableText().contains("성진우"))
    }

    @Test
    fun `infobox values preserve linked cast and voice actor pages`() {
        val json = """[{"data":[
            {"type":"data","data":{"label":"Voice Actor (Japanese)","value":"<a href=\"/wiki/Aya_Hirano\">Aya Hirano</a>","source":"Japanese VA"}},
            {"type":"data","data":{"label":"Character","value":"<a href=\"/wiki/Haruhi_Suzumiya\">Haruhi</a>","source":"Character"}}
        ]}]"""

        val fields = FandomInfoboxParser.parse(json, wikiId = "haruhi-suzumiya")!!.allFields()

        assertEquals("Aya Hirano", fields[0].links.single().label)
        assertEquals("ja", fields[0].links.single().language)
        assertEquals(FandomRelationType.VOICE_ACTOR, fields[0].links.single().role)
        assertEquals("Haruhi Suzumiya", fields[1].links.single().pageTitle)
        assertEquals(FandomRelationType.CHARACTER, fields[1].links.single().role)
    }

    @Test
    fun `richest infobox wins and invalid input returns null`() {
        val two = """[{"data":[{"type":"title","data":{"value":"Small"}}]},""" +
            """{"data":[{"type":"title","data":{"value":"Rich"}},{"type":"data","data":{"label":"Age","value":"17"}}]}]"""
        assertEquals("Rich", FandomInfoboxParser.parse(two)!!.title)
        assertNull(FandomInfoboxParser.parse("not json"))
        assertNull(FandomInfoboxParser.parse("[]"))
        assertNull(FandomInfoboxParser.parse(null))
    }

    @Test
    fun `unknown container types are walked`() {
        val panel = """[{"data":[{"type":"panel","data":{"value":[
            {"type":"section","data":{"value":[{"type":"data","data":{"label":"Grade","value":"Special Grade"}}]}}
        ]}}]}]"""
        val box = FandomInfoboxParser.parse(panel)
        assertEquals("Special Grade", box!!.allFields().single().value)
    }
}

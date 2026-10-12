package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomAniListParserTest {

    private val sample = """
        {"data":{"Media":{
          "id":151807,"idMal":52299,"type":"ANIME",
          "title":{"romaji":"Ore dake Level Up na Ken","english":"Solo Leveling","native":"俺だけレベルアップな件"},
          "synonyms":["Only I Level Up","나 혼자만 레벨업",null],
          "relations":{"edges":[
            {"relationType":"CHARACTER","node":{"id":1,"idMal":null,"type":"ANIME"}},
            {"relationType":"SEQUEL","node":{"id":176496,"idMal":58567,"type":"ANIME"}},
            {"relationType":"ADAPTATION","node":{"id":105398,"idMal":121496,"type":"MANGA"}},
            {"relationType":"SOURCE","node":{"id":105398,"idMal":121496,"type":"MANGA"}}
          ]},
          "characters":{"edges":[
            {"role":"MAIN","node":{"id":127212,"name":{"full":"Jinwoo Sung","native":"성진우","alternative":["Sung Jin-Woo","水篠旬"]}}},
            {"role":"SUPPORTING","node":{"id":127213,"name":{"full":"Hae-In Cha","native":null,"alternative":[]}}}
          ]},
          "staff":{"edges":[
            {"role":"Original Creator","node":{"name":{"full":"Chugong"}}},
            {"role":"Director","node":{"name":{"full":"Shunsuke Nakashige"}}},
            {"role":"Original Character Design","node":{"name":{"full":"DUBU"}}}
          ]}
        }}}
    """.trimIndent()

    @Test
    fun `parses titles creators characters and relations`() {
        val evidence = FandomAniListParser.parse(sample, fallbackIsManga = false)
        assertNotNull(evidence)
        evidence!!
        assertEquals(151807, evidence.aniListId)
        assertEquals(52299, evidence.malId)
        assertFalse(evidence.isManga)
        assertEquals(listOf("Solo Leveling", "Ore dake Level Up na Ken", "Only I Level Up"), evidence.titles)
        assertEquals("俺だけレベルアップな件", evidence.nativeTitle)
        assertEquals(listOf("Chugong"), evidence.creators)

        assertEquals(2, evidence.characters.size)
        val jinwoo = evidence.characters[0]
        assertTrue(jinwoo.isMain)
        assertEquals("성진우", jinwoo.nativeName)
        assertEquals(listOf("Sung Jin-Woo"), jinwoo.alternatives)
        assertNull(evidence.characters[1].nativeName)
        assertFalse(evidence.characters[1].isMain)

        assertEquals(listOf("SOURCE", "SEQUEL"), evidence.relations.map { it.relationType })
        assertTrue(evidence.relations[0].isManga)
        assertEquals(121496, evidence.relations[0].malId)
    }

    @Test
    fun `missing media yields null`() {
        assertNull(FandomAniListParser.parse("""{"data":{"Media":null},"errors":[{"message":"Not Found."}]}""", false))
        assertNull(FandomAniListParser.parse(null, false))
        assertNull(FandomAniListParser.parse("not json", true))
    }

    @Test
    fun `manga creators use story and art roles`() {
        val json = """{"data":{"Media":{"id":30013,"idMal":13,"type":"MANGA","title":{"romaji":"ONE PIECE","english":null},
            "staff":{"edges":[{"role":"Story & Art","node":{"name":{"full":"Eiichiro Oda"}}}]}}}}"""
        val evidence = FandomAniListParser.parse(json, fallbackIsManga = true)!!
        assertTrue(evidence.isManga)
        assertEquals(listOf("ONE PIECE"), evidence.titles)
        assertEquals(listOf("Eiichiro Oda"), evidence.creators)
        assertTrue(evidence.characters.isEmpty())
        assertTrue(evidence.relations.isEmpty())
    }
}

package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataParsersTest {

    @Test
    fun `haswbstatement search yields qids`() {
        val json = """{"batchcomplete":"","query":{"searchinfo":{"totalhits":1},""" +
            """"search":[{"ns":0,"title":"Q132174429","pageid":126053943},{"ns":0,"title":"Property:P1"}]}}"""
        assertEquals(listOf("Q132174429"), WikidataParsers.parseSearchQids(json))
        assertTrue(WikidataParsers.parseSearchQids("not json").isEmpty())
    }

    @Test
    fun `entities expose fandom ids and ordered parents and sitelinks`() {
        val json = """
            {"entities":{
              "Q1":{"id":"Q1","claims":{
                "P4073":[
                  {"mainsnak":{"snaktype":"value","datavalue":{"value":"old-wiki","type":"string"}},"rank":"deprecated"},
                  {"mainsnak":{"snaktype":"value","datavalue":{"value":"tr.onepiece","type":"string"}},"rank":"normal"},
                  {"mainsnak":{"snaktype":"value","datavalue":{"value":"onepiece","type":"string"}},"rank":"preferred"}
                ],
                "P6262":[{"mainsnak":{"snaktype":"value","datavalue":{"value":"onepiece:One_Piece","type":"string"}},"rank":"normal"}],
                "P144":[{"mainsnak":{"snaktype":"value","datavalue":{"value":{"entity-type":"item","numeric-id":673,"id":"Q673"},"type":"wikibase-entityid"}},"rank":"normal"}],
                "P179":[
                  {"mainsnak":{"snaktype":"somevalue"},"rank":"normal"},
                  {"mainsnak":{"snaktype":"value","datavalue":{"value":{"numeric-id":99}}},"rank":"normal"}
                ]
              },
              "sitelinks":{
                "enwiki":{"site":"enwiki","title":"Crash Landing on You"},
                "trwiki":{"site":"trwiki","title":"Crash Landing on You (dizi)"}
              }},
              "Q404":{"id":"Q404","missing":""}
            }}
        """.trimIndent()
        val entities = WikidataParsers.parseEntities(json)
        val q1 = entities.getValue("Q1")
        assertEquals(listOf("onepiece", "tr.onepiece"), q1.fandomWikiIds)
        assertEquals(listOf("onepiece:One_Piece"), q1.fandomArticleIds)
        assertEquals(listOf("Q99", "Q673"), q1.parents)
        assertEquals("Crash Landing on You", q1.wikipediaTitles["enwiki"])
        assertEquals("Crash Landing on You (dizi)", q1.wikipediaTitles["trwiki"])
        assertFalse(entities.containsKey("Q404"))
    }

    @Test
    fun `media sparql query validates input and contains the traversal`() {
        val query = WikidataParsers.buildMediaQuery(
            lookups = listOf("P8729" to "11757", "P4086" to "bad value\" } DROP"),
            startQids = listOf("Q104211858", "not-a-qid")
        )
        assertNotNull(query)
        query!!
        assertTrue(query.contains("BIND(wd:Q104211858 AS ?start)"))
        assertTrue(query.contains("?start wdt:P8729 \"11757\""))
        assertTrue(query.contains("wdt:P4073"))
        assertTrue(query.contains("wdt:P6262"))
        assertFalse(query.contains("DROP"))
        assertFalse(query.contains("not-a-qid"))
        assertNull(WikidataParsers.buildMediaQuery(emptyList(), emptyList()))
        assertNull(WikidataParsers.buildMediaQuery(listOf("X1" to "1"), emptyList()))
    }

    @Test
    fun `media sparql results group wikis by depth`() {
        val json = """
            {"head":{"vars":["start","depth","wiki","article","node"]},"results":{"bindings":[
              {"start":{"type":"uri","value":"http://www.wikidata.org/entity/Q132174429"},"depth":{"type":"literal","value":"1"},
               "wiki":{"type":"literal","value":"swordartonline"},"node":{"type":"uri","value":"http://www.wikidata.org/entity/Q7"}},
              {"start":{"type":"uri","value":"http://www.wikidata.org/entity/Q132174429"},"depth":{"type":"literal","value":"1"},
               "wiki":{"type":"literal","value":"swordartonline"},"node":{"type":"uri","value":"http://www.wikidata.org/entity/Q7"}},
              {"start":{"type":"uri","value":"http://www.wikidata.org/entity/Q132174429"},"depth":{"type":"literal","value":"3"},
               "article":{"type":"literal","value":"dengeki:Sword_Art_Online"}},
              {"start":{"type":"uri","value":"http://www.wikidata.org/entity/Q132174429"},"depth":{"type":"literal","value":"4"},
               "node":{"type":"uri","value":"http://www.wikidata.org/entity/Q8"}}
            ]}}
        """.trimIndent()
        val resolution = WikidataParsers.parseMediaResults(json)
        assertNotNull(resolution)
        resolution!!
        assertEquals(setOf("Q132174429"), resolution.starts)
        assertEquals(listOf("swordartonline"), resolution.wikisByDepth[1])
        assertNull(resolution.wikisByDepth[0])
        assertEquals(listOf("dengeki:Sword_Art_Online"), resolution.articleIds)
        assertEquals(setOf("Q7", "Q8"), resolution.visitedNodes)
        assertFalse(resolution.isEmpty)
        assertNull(WikidataParsers.parseMediaResults("<html>502 Bad Gateway</html>"))
        assertTrue(WikidataParsers.parseMediaResults("""{"results":{"bindings":[]}}""")!!.isEmpty)
    }

    @Test
    fun `character sparql query and results`() {
        val query = WikidataParsers.buildCharacterQuery(listOf("P11736" to "127691", "P4085" to "164471"))
        assertNotNull(query)
        assertTrue(query!!.contains("?item wdt:P11736 \"127691\""))
        assertTrue(query.contains("?item wdt:P4085 \"164471\""))
        val json = """{"results":{"bindings":[{"article":{"type":"literal","value":"jujutsu-kaisen:Satoru_Gojo"}}]}}"""
        assertEquals(listOf("jujutsu-kaisen:Satoru_Gojo"), WikidataParsers.parseCharacterResults(json))
        assertNull(WikidataParsers.parseCharacterResults("oops"))
        assertNull(WikidataParsers.buildCharacterQuery(emptyList()))
    }
}

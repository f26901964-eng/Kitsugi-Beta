package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wikidata P345 (IMDb kimliği) ayrıştırması. Akış tarafındaki IMDb köprüsü
 * ([com.kitsugi.animelist.data.remote.KitsugiIdResolver]) bu alana bağlıdır.
 */
class WikidataImdbParseTest {

    private fun entityJson(qid: String, p345Value: String?): String {
        val claims = if (p345Value == null) {
            "{}"
        } else {
            """{"P345":[{"mainsnak":{"snaktype":"value","property":"P345","datavalue":{"value":"$p345Value","type":"string"}}}]}"""
        }
        return """{"entities":{"$qid":{"id":"$qid","claims":$claims}}}"""
    }

    @Test
    fun parsesImdbIdFromP345() {
        val parsed = WikidataParsers.parseEntities(entityJson("Q1", "tt0137523"))
        assertEquals("tt0137523", parsed["Q1"]?.imdbId)
    }

    @Test
    fun lowercasesImdbIdAndRejectsMalformedValues() {
        assertEquals("tt0137523", WikidataParsers.parseEntities(entityJson("Q1", "TT0137523"))["Q1"]?.imdbId)
        assertNull(WikidataParsers.parseEntities(entityJson("Q1", "12345"))["Q1"]?.imdbId)
        assertNull(WikidataParsers.parseEntities(entityJson("Q1", "nm0000206"))["Q1"]?.imdbId)
    }

    @Test
    fun missingP345YieldsNull() {
        assertNull(WikidataParsers.parseEntities(entityJson("Q1", null))["Q1"]?.imdbId)
    }
}

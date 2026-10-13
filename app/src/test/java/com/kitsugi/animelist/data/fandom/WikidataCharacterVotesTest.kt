package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikidataCharacterVotesTest {

    @Test
    fun `tally counts one vote per character per root wiki`() {
        val votes = WikidataCharacterVotes.tally(
            listOf(
                listOf("gintama:Sakata_Gintoki", "gintama:Sakata_Gintoki_(alt)"), // tek oy
                listOf("gintama:Kagura", "es.doblaje:Gintoki_Sakata"),           // dil wiki'si yok sayılır
                listOf("datealive:Tohka_Yatogami")
            )
        )
        assertEquals(mapOf("gintama" to 2, "datealive" to 1), votes)
    }

    @Test
    fun `tally ignores malformed article ids`() {
        assertEquals(emptyMap<String, Int>(), WikidataCharacterVotes.tally(listOf(listOf("", "nocolon", ":x"))))
    }

    @Test
    fun `winner requires minimum votes and a strict lead`() {
        assertEquals("gintama", WikidataCharacterVotes.winner(mapOf("gintama" to 7, "other" to 1)))
        assertNull(WikidataCharacterVotes.winner(mapOf("gintama" to 1)))
        assertNull(WikidataCharacterVotes.winner(mapOf("a" to 3, "b" to 3)))
        assertNull(WikidataCharacterVotes.winner(emptyMap()))
    }
}

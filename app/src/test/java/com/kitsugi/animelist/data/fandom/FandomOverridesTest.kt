package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FandomOverridesTest {

    @Test
    fun `parse builds lookups and skips invalid or blocked wikis`() {
        val json = """
            {"version":1,"entries":[
              {"wiki":"chainsaw-man","anilist":[127230],"mal":[44511],"qid":["Q104211858","bad"]},
              {"wiki":"villains","anilist":[1]},
              {"wiki":"bad id","anilist":[2]},
              {"wiki":"berserk","mal_manga":[2]}
            ]}
        """.trimIndent()
        val table = FandomOverrides.parse(json)
        assertEquals("chainsaw-man", table.lookup(FandomSourceIds(aniListId = 127230), isManga = false))
        assertEquals("chainsaw-man", table.lookup(FandomSourceIds(malId = 44511), isManga = false))
        assertNull(table.lookup(FandomSourceIds(malId = 44511), isManga = true))
        assertEquals("berserk", table.lookup(FandomSourceIds(malId = 2), isManga = true))
        assertEquals("chainsaw-man", table.lookupQid("Q104211858"))
        assertEquals("chainsaw-man", table.lookup(FandomSourceIds(wikidataId = "Q104211858"), isManga = false))
        assertNull(table.lookupQid("bad"))
        assertNull(table.lookup(FandomSourceIds(aniListId = 1), isManga = false))
        assertNull(table.lookup(FandomSourceIds(aniListId = 2), isManga = false))
        assertEquals(FandomOverrideTable.EMPTY, FandomOverrides.parse("not json"))
    }

    @Test
    fun `shipped asset parses and contains verified entries`() {
        val file = listOf(
            File("src/main/assets/${FandomOverrides.ASSET_PATH}"),
            File("app/src/main/assets/${FandomOverrides.ASSET_PATH}")
        ).first { it.exists() }
        val table = FandomOverrides.parse(file.readText())
        assertTrue(table.size >= 3)
        assertEquals("chainsaw-man", table.lookup(FandomSourceIds(aniListId = 127230), isManga = false))
        assertEquals("spy-x-family", table.lookup(FandomSourceIds(malId = 50602), isManga = false))
        assertEquals("solo-leveling", table.lookupQid("Q125221706"))
    }
}

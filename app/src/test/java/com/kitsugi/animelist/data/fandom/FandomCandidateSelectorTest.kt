package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomCandidateSelectorTest {

    @Test
    fun `crossover dubbing and fan wikis are blocked`() {
        listOf(
            "villains", "hero", "isekai", "vsbattles", "es.doblaje", "no.norske-dubber", "dubdb",
            "naruto-fanon", "anime", "powerlisting", "netflix", "dengeki", "cosplay", "bad id"
        ).forEach { assertTrue(it, FandomCandidateSelector.isBlocked(it)) }
        listOf("jujutsu-kaisen", "onepiece", "tr.onepiece", "gundam", "ghibli", "obluda", "attackontitan")
            .forEach { assertFalse(it, FandomCandidateSelector.isBlocked(it)) }
    }

    @Test
    fun `root wiki wins over language variants and turkish pair is kept`() {
        val choice = FandomCandidateSelector.choose(listOf("ar.onepiece", "tr.onepiece", "onepiece", "ru.onepiece"))
        assertNotNull(choice)
        assertEquals("onepiece", choice!!.primary)
        assertEquals("tr.onepiece", choice.turkish)
    }

    @Test
    fun `bigger wiki wins when several roots exist`() {
        val candidates = listOf("shingekipedia", "attackontitan")
        assertTrue(FandomCandidateSelector.needsArticleCounts(candidates))
        val choice = FandomCandidateSelector.choose(candidates, mapOf("shingekipedia" to 120, "attackontitan" to 3500))
        assertEquals("attackontitan", choice!!.primary)
        assertNull(choice.turkish)
    }

    @Test
    fun `first root wins when counts are unknown`() {
        assertEquals(
            "bokunoheroacademia",
            FandomCandidateSelector.choose(listOf("bokunoheroacademia", "myheroacademia"))!!.primary
        )
    }

    @Test
    fun `localized wikis are accepted when no root wiki exists`() {
        assertEquals("tr.pokemon", FandomCandidateSelector.choose(listOf("de.pokemon", "tr.pokemon"))!!.primary)
        assertEquals("ja.pokemon", FandomCandidateSelector.choose(listOf("ja.pokemon"))!!.primary)
        assertEquals(
            "fr.pokemon",
            FandomCandidateSelector.choose(
                listOf("de.pokemon", "fr.pokemon"),
                mapOf("de.pokemon" to 50, "fr.pokemon" to 120)
            )!!.primary
        )
        assertNull(FandomCandidateSelector.choose(listOf("villains", "hero")))
        assertNull(FandomCandidateSelector.choose(emptyList()))
    }

    @Test
    fun `article count ranks multiple wikis in the preferred localized language`() {
        assertEquals(
            "tr.second",
            FandomCandidateSelector.choose(
                listOf("tr.first", "tr.second"),
                mapOf("tr.first" to 10, "tr.second" to 900)
            )!!.primary
        )
    }

    @Test
    fun `usable list is canonical and duplicate free`() {
        assertEquals(
            listOf("onepiece", "tr.onepiece"),
            FandomCandidateSelector.usable(listOf("OnePiece", "en.onepiece", "tr.onepiece", "villains"))
        )
    }
}

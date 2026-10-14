package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomPersonMatcherTest {

    private val nameKeys = FandomPersonMatcher.nameKeys(listOf("Kenji Nojima"))
    private val works = FandomPersonMatcher.workKeys(listOf("Attack on Titan"))

    @Test
    fun `mentionsWork matches whole words only`() {
        assertTrue(FandomPersonMatcher.mentionsWork("He voiced Eren in Attack on Titan.", works))
        // "Titans" bir başka yapım adıdır; "Attack on Titan" ile eşleşmemeli.
        assertFalse(FandomPersonMatcher.mentionsWork("He appeared in Attack on Titans.", works))
    }

    @Test
    fun `mentionsWork ignores diacritics and punctuation`() {
        val keys = FandomPersonMatcher.workKeys(listOf("Pokémon"))
        assertTrue(FandomPersonMatcher.mentionsWork("A role in Pokemon: The Series.", keys))
    }

    @Test
    fun `short work titles are not used as evidence`() {
        val keys = FandomPersonMatcher.workKeys(listOf("Ai", "It"))
        assertTrue(keys.isEmpty())
        assertFalse(FandomPersonMatcher.mentionsWork("Ai it", keys))
    }

    @Test
    fun `title match strips parenthetical qualifiers`() {
        assertTrue(FandomPersonMatcher.titleMatchesName("Kenji Nojima (voice actor)", nameKeys))
        assertTrue(FandomPersonMatcher.titleMatchesName("Nojima Kenji", nameKeys))
        assertFalse(FandomPersonMatcher.titleMatchesName("Kenji Nojimaa", nameKeys))
    }

    @Test
    fun `empty name set never matches`() {
        assertFalse(FandomPersonMatcher.titleMatchesName("Anything", emptySet()))
    }

    @Test
    fun `proof prefers native name over work mention`() {
        val proof = FandomPersonMatcher.proofOf(
            pageTitle = "Kenji Nojima",
            evidence = "野島健児 is a Japanese voice actor. He voiced Eren in Attack on Titan.",
            nameKeys = nameKeys,
            nativeName = "野島健児",
            workKeys = works
        )
        assertEquals(FandomPersonMatcher.Proof.NATIVE, proof)
    }

    @Test
    fun `work mention alone is weaker proof`() {
        val proof = FandomPersonMatcher.proofOf(
            pageTitle = "Kenji Nojima",
            evidence = "Japanese voice actor known for Attack on Titan.",
            nameKeys = nameKeys,
            nativeName = "野島健児",
            workKeys = works
        )
        assertEquals(FandomPersonMatcher.Proof.WORK, proof)
    }

    @Test
    fun `namesake without any shared evidence is rejected`() {
        val proof = FandomPersonMatcher.proofOf(
            pageTitle = "Kenji Nojima",
            evidence = "A football manager from Osaka.",
            nameKeys = nameKeys,
            nativeName = "野島健児",
            workKeys = works
        )
        assertEquals(FandomPersonMatcher.Proof.NONE, proof)
    }

    @Test
    fun `pickUnique accepts a single native proof`() {
        val picked = FandomPersonMatcher.pickUnique(
            listOf("a" to FandomPersonMatcher.Proof.NONE, "b" to FandomPersonMatcher.Proof.NATIVE)
        )
        assertEquals("b", picked?.first)
    }

    @Test
    fun `pickUnique refuses ambiguous native proofs`() {
        val picked = FandomPersonMatcher.pickUnique(
            listOf("a" to FandomPersonMatcher.Proof.NATIVE, "b" to FandomPersonMatcher.Proof.NATIVE)
        )
        assertNull(picked)
    }

    @Test
    fun `pickUnique accepts a single work proof and refuses several`() {
        val single = FandomPersonMatcher.pickUnique(listOf("a" to FandomPersonMatcher.Proof.WORK))
        assertEquals("a", single?.first)
        val several = FandomPersonMatcher.pickUnique(
            listOf("a" to FandomPersonMatcher.Proof.WORK, "b" to FandomPersonMatcher.Proof.WORK)
        )
        assertNull(several)
    }

    @Test
    fun `pickUnique returns null without proof`() {
        assertNull(FandomPersonMatcher.pickUnique(listOf("a" to FandomPersonMatcher.Proof.NONE)))
    }

    @Test
    fun `person search queries are localized and bounded`() {
        val en = FandomPersonMatcher.personSearchQueries("Kenji Nojima", listOf("Attack on Titan", "Naruto"), turkish = false)
        assertEquals("Kenji Nojima voice actor", en.first())
        assertTrue(en.size <= 4)
        val tr = FandomPersonMatcher.personSearchQueries("Kenji Nojima", listOf("Attack on Titan"), turkish = true)
        assertEquals("Kenji Nojima seslendirme sanatçısı", tr.first())
    }

    @Test
    fun `character search pairs name with work first`() {
        val queries = FandomPersonMatcher.characterSearchQueries("Eren Yeager", listOf("Attack on Titan"))
        assertEquals("Eren Yeager Attack on Titan", queries.first())
        assertEquals("Eren Yeager", queries.last())
    }
}

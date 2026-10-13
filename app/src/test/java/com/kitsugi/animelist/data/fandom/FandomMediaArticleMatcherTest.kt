package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FandomMediaArticleMatcherTest {

    @Test
    fun `exact non-disambiguation article is selected`() {
        val selected = FandomMediaArticleMatcher.choose(
            sourceTitles = listOf("君の名は。"),
            candidates = listOf(
                lookup("君の名は。", "君の名は。"),
                lookup("Your Name (disambiguation)", "Your Name (disambiguation)", disambiguation = true)
            )
        )

        assertEquals("君の名は。", selected?.lookup?.resolvedTitle)
        assertEquals(FandomConfidence.CERTAIN, selected?.confidence)
    }

    @Test
    fun `disambiguation-only result is rejected`() {
        val selected = FandomMediaArticleMatcher.choose(
            sourceTitles = listOf("Dune"),
            candidates = listOf(lookup("Dune", "Dune (disambiguation)", disambiguation = true))
        )

        assertNull(selected)
    }

    @Test
    fun `close exact title collisions are rejected instead of guessed`() {
        val selected = FandomMediaArticleMatcher.choose(
            sourceTitles = listOf("The Office"),
            candidates = listOf(
                lookup("The Office", "The Office (US)"),
                lookup("The Office", "The Office (UK)")
            )
        )

        assertNull(selected)
    }

    private fun lookup(
        requested: String,
        resolved: String,
        disambiguation: Boolean = false
    ) = FandomTitleLookup(
        requested = requested,
        resolvedTitle = resolved,
        pageId = 1L,
        infoboxJson = null,
        description = null,
        isDisambiguation = disambiguation
    )
}

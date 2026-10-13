package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomMediaIdentityProofTest {

    @Test
    fun `multiword work title plus its exact fandom article identifies its franchise wiki`() {
        val title = "Chainsaw Man – The Movie: Reze Arc"
        val hit = FandomCommunityHit(
            wikiId = "chainsaw-man",
            name = "Chainsaw Man Wiki",
            language = "en",
            hub = "anime",
            pageCount = 589
        )
        val info = FandomSiteInfo("chainsaw-man", "Chainsaw Man Wiki", "en", 589, "Main Page")
        val article = FandomMediaArticleMatcher.choose(
            listOf(title),
            listOf(lookup(title, title, "Reze Arc is the Chainsaw Man theatrical film adaptation."))
        )!!

        assertTrue(
            FandomMediaIdentityProof.hasStrongArticleProof(
                hit = hit,
                siteInfo = info,
                sourceTitles = listOf(title),
                creators = emptyList(),
                isManga = false,
                releaseYear = 2025,
                articleMatch = article
            )
        )
    }

    @Test
    fun `single generic title does not accept a similarly named wiki without year proof`() {
        val title = "Monster (2004)"
        val hit = FandomCommunityHit(
            wikiId = "monster",
            name = "Monster Wiki",
            language = "en",
            hub = "tv",
            pageCount = 4000,
            description = "A generic encyclopedia about monsters."
        )
        val info = FandomSiteInfo("monster", "Monster Wiki", "en", 4000, "Monster")
        val article = FandomMediaArticleMatcher.choose(
            listOf(title),
            listOf(lookup(title, "Monster", "A generic encyclopedia about monsters."))
        )!!

        assertFalse(
            FandomMediaIdentityProof.hasStrongArticleProof(
                hit = hit,
                siteInfo = info,
                sourceTitles = listOf(title),
                creators = emptyList(),
                isManga = false,
                releaseYear = null,
                articleMatch = article
            )
        )
    }

    @Test
    fun `year embedded in a source title can identify a one-word media article`() {
        val title = "Monster (2004)"
        val hit = FandomCommunityHit(
            wikiId = "monster",
            name = "Monster Wiki",
            language = "en",
            hub = "tv",
            pageCount = 4000
        )
        val info = FandomSiteInfo("monster", "Monster Wiki", "en", 4000, "Monster")
        val article = FandomMediaArticleMatcher.choose(
            listOf(title),
            listOf(lookup(title, "Monster", "The 2004 psychological thriller series."))
        )!!

        assertTrue(
            FandomMediaIdentityProof.hasStrongArticleProof(
                hit = hit,
                siteInfo = info,
                sourceTitles = listOf(title),
                creators = emptyList(),
                isManga = false,
                releaseYear = null,
                articleMatch = article
            )
        )
    }

    private fun lookup(requested: String, resolved: String, description: String) = FandomTitleLookup(
        requested = requested,
        resolvedTitle = resolved,
        pageId = 1L,
        infoboxJson = null,
        description = description,
        isDisambiguation = false
    )
}

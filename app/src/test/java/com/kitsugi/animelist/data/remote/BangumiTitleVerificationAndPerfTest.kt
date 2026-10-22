package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiTitleVerificationAndPerfTest {

    @Test
    fun verifiedSameRomajiAndEnglishDoesNotLoopForeverAndKeepsEnglishDisplay() {
        val rawItem = JikanSearchResult(
            malId = BangumiIdNamespace.stableIdFromRaw(294993)!!,
            title = "呪術廻戦",
            subtitle = "TV",
            type = MediaType.Anime,
            total = 24,
            score = 8,
            isAdult = false,
            imageUrl = null,
            year = 2020,
            source = BangumiIdNamespace.SOURCE,
            titleJapanese = "呪術廻戦",
            bangumiSubjectType = BangumiApiClient.SubjectType.ANIME
        )

        val verifiedSame = BangumiTitleCache.LatinTitles(
            romaji = "Jujutsu Kaisen",
            english = "Jujutsu Kaisen",
            native = "呪術廻戦",
            englishVerified = true
        )

        assertTrue(BangumiSubjectTitleResolver.hasReliableEnglish(verifiedSame))

        val applied = KitsugiBangumiClient.applyLocalizedBangumiTitles(rawItem, verifiedSame)
        assertEquals("Jujutsu Kaisen", applied.titleEnglish)
        assertEquals("Jujutsu Kaisen", applied.getDisplayTitle("ENGLISH"))
        assertEquals("Jujutsu Kaisen", applied.getDisplayTitle("ROMAJI"))
        assertEquals("呪術廻戦", applied.getDisplayTitle("NATIVE"))
    }

    @Test
    fun mergeTitles_preservesEnglishVerifiedFlagAcrossPartialUpdates() {
        val unverified = BangumiTitleCache.LatinTitles(
            romaji = "Sousou no Frieren",
            english = null,
            native = "葬送のフリーレン"
        )
        assertFalse(BangumiSubjectTitleResolver.hasReliableEnglish(unverified))

        val upgraded = BangumiTitleCache.mergeTitles(
            previous = unverified,
            romaji = "Sousou no Frieren",
            english = "Frieren: Beyond Journey's End",
            native = null,
            englishVerified = true
        )
        assertTrue(upgraded.englishVerified)
        assertEquals("Frieren: Beyond Journey's End", upgraded.english)
        assertEquals("Sousou no Frieren", upgraded.romaji)
        assertEquals("葬送のフリーレン", upgraded.native)
    }
}

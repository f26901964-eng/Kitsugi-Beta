package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoSelectionPolicyTest {
    @Test
    fun preferredLanguageBeatsProviderPreferenceAndScore() {
        val selected = LogoSelectionPolicy.select(
            candidates = listOf(
                LogoCandidate("fanart-en", "en", score = 10_000.0, sourcePriority = LogoSelectionPolicy.FANART_SOURCE_PRIORITY),
                LogoCandidate("tmdb-tr", "tr", score = 1.0, sourcePriority = LogoSelectionPolicy.TMDB_SOURCE_PRIORITY),
            ),
            preferredLanguage = "tr-TR",
            seasonNumber = null,
        )

        assertEquals("tmdb-tr", selected?.url)
    }

    @Test
    fun fallbackOrderIsEnglishThenNeutralRomanizedThenCjk() {
        val english = listOf(
            LogoCandidate("neutral", null),
            LogoCandidate("japanese", "ja"),
            LogoCandidate("english", "en"),
        )
        assertEquals("english", LogoSelectionPolicy.select(english, "tr")?.url)

        val romanized = listOf(
            LogoCandidate("japanese", "ja"),
            LogoCandidate("romaji", "ja-Latn"),
        )
        assertEquals("romaji", LogoSelectionPolicy.select(romanized, "tr")?.url)

        val cjk = listOf(
            LogoCandidate("chinese", "zh"),
            LogoCandidate("japanese", "ja"),
        )
        val cjkSelection = LogoSelectionPolicy.select(cjk, "tr")
        assertEquals(3, LogoSelectionPolicy.languageRank("ja", "tr"))
        assertEquals(3, LogoSelectionPolicy.languageRank("zh", "tr"))
        assertTrue(cjkSelection != null)
    }

    @Test
    fun selectedSeasonLogoWinsOnlyWithinItsLanguageTier() {
        val candidates = listOf(
            LogoCandidate("tr-main", "tr", sourcePriority = LogoSelectionPolicy.TMDB_SOURCE_PRIORITY),
            LogoCandidate("en-season-2", "en", season = "2"),
            LogoCandidate("tr-season-2", "tr", season = "2"),
            LogoCandidate("en-season-3", "en", season = "3"),
        )

        assertEquals("tr-season-2", LogoSelectionPolicy.select(candidates, "tr", 2)?.url)
        assertEquals("tr-main", LogoSelectionPolicy.select(candidates - candidates[2], "tr", 2)?.url)
        assertEquals("tr-main", LogoSelectionPolicy.select(candidates, "tr", 3)?.url)
        assertNull(LogoSelectionPolicy.select(candidates.filter { it.season != null }, "tr", 4))
    }

    @Test
    fun selectedEnglishDoesNotGetDemotedBehindNeutralOrJapanese() {
        val selected = LogoSelectionPolicy.select(
            listOf(
                LogoCandidate("neutral", null),
                LogoCandidate("japanese", "ja"),
                LogoCandidate("english", "en"),
            ),
            preferredLanguage = "en-US",
        )

        assertEquals("english", selected?.url)
    }

    @Test
    fun firstSeasonLogosAreConsideredAndGalleryOrderingUsesSeasonMetadata() {
        val candidates = listOf(
            LogoCandidate("series", "tr"),
            LogoCandidate("season-1", "tr", season = "1"),
        )

        assertEquals("season-1", LogoSelectionPolicy.select(candidates, "tr", 1)?.url)
        assertEquals(0, LogoSelectionPolicy.gallerySeasonRank("1", 1))
        assertEquals(1, LogoSelectionPolicy.gallerySeasonRank(null, 1))
        assertEquals(2, LogoSelectionPolicy.gallerySeasonRank("2", 1))
    }

    @Test
    fun tmdbImageLanguageListIncludesFallbacksWithoutDuplicates() {
        assertEquals(
            listOf("tr", "en", "null", "ja", "zh", "ko"),
            LogoSelectionPolicy.tmdbImageLanguages("tr-TR"),
        )
        assertEquals(
            listOf("en", "null", "ja", "zh", "ko"),
            LogoSelectionPolicy.tmdbImageLanguages("en"),
        )
    }
}

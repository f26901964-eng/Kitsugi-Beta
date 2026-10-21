package com.kitsugi.animelist.ui.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBadgeSourcesTest {
    @Test fun removesPrimaryAliasesAndDuplicateMemberships() {
        assertEquals(
            listOf("mal", "shikimori", "bangumi", "tmdb"),
            additionalLibraryBadgeSources(
                " AL ",
                listOf("anilist", "al", "MAL", "jikan", "myanimelist", "shiki", "shikimori",
                    "bgm", "bangumi", "themoviedb", "tmdb_network", "tmdb", " ")
            )
        )
    }

    @Test fun malAliasesNeverRepeatThePrimaryPosterBadge() {
        assertEquals(
            listOf("kitsu"),
            additionalLibraryBadgeSources("Jikan (MAL)", listOf("mal", "myanimelist", "mal (jikan)", "kitsu"))
        )
    }

    @Test fun noMembershipDataDoesNotInventBadges() {
        assertEquals(emptyList<String>(), additionalLibraryBadgeSources("anilist", null))
        assertEquals(emptyList<String>(), additionalLibraryBadgeSources("anilist", emptyList()))
    }

    @Test fun normalizationIsIndependentOfTurkishDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("simkl", "shikimori"), additionalLibraryBadgeSources("ANILIST", listOf("SIMKL", "SHIKI", "AL")))
        } finally {
            Locale.setDefault(previous)
        }
    }
}

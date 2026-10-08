package com.kitsugi.animelist.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSyncIdentityGuardTest {

    @Test
    fun romanizationAndPunctuationVariantsAreRelated() {
        // Shikimori romaji vs AniList romaji (aynı MAL ID, aynı yıl): 2026-10-08 raporundaki vaka.
        assertTrue(
            CrossSyncIdentityGuard.titlesVariantRelated(
                "Mayoiga no Oneesan The Animation",
                "Mayohiga no Onee-san THE ANIMATION"
            )
        )
    }

    @Test
    fun numericTitleMatchesItsEnglishExpansion() {
        // Simkl "86" ↔ AniList "86: Eighty Six" (aynı MAL ID, aynı yıl): raporda yanlış biçimde izole edilmişti.
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("86", "86: Eighty Six"))
        assertTrue(CrossSyncIdentityGuard.titlesVariantRelated("86 Part 2", "86: Eighty Six Part 2"))
    }

    @Test
    fun differentSeasonNumbersAreNeverRelated() {
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("Re:Zero Part 2", "Re:Zero Part 3"))
        assertFalse(CrossSyncIdentityGuard.titlesVariantRelated("86 Part 2", "86: Eighty Six"))
    }

    @Test
    fun genuinelyDifferentTitlesStillRejected() {
        // Kitsu'daki yanlış MAL eşlemesi (Koi wo Shita no wa ↔ Koe no Katachi Specials): yazma durmalı.
        assertFalse(
            CrossSyncIdentityGuard.titlesVariantRelated(
                "Koi wo Shita no wa",
                "Koe no Katachi Specials"
            )
        )
    }
}

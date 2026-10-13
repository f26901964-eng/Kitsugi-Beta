package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KitsugiPlatformLogosTest {
    @Test
    fun fandomGallerySourceUsesOriginalLogo() {
        assertEquals(R.drawable.ic_logo_fandom, KitsugiPlatformLogos.resFor("Fandom"))
    }

    @Test
    fun fandomLogoLookupIgnoresCaseAndSurroundingWhitespace() {
        listOf("fandom", "FANDOM", "  Fandom  ").forEach { source ->
            assertEquals(R.drawable.ic_logo_fandom, KitsugiPlatformLogos.resFor(source))
        }
    }

    @Test
    fun existingGallerySourcesKeepTheirLogos() {
        assertEquals(R.drawable.ic_logo_anilist, KitsugiPlatformLogos.resFor("AniList"))
        assertEquals(R.drawable.ic_logo_mal, KitsugiPlatformLogos.resFor("Jikan (MAL)"))
        assertEquals(R.drawable.ic_logo_fanart, KitsugiPlatformLogos.resFor("Fanart.tv"))
        assertEquals(R.drawable.ic_logo_tmdb, KitsugiPlatformLogos.resFor("TMDB"))
    }

    @Test
    fun unknownSourcesKeepFallbackBehavior() {
        assertNull(KitsugiPlatformLogos.resFor("Unknown plugin"))
        assertNull(KitsugiPlatformLogos.resFor(null))
    }
}

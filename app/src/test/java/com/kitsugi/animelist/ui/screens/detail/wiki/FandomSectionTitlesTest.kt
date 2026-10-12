package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ekran görüntülerinde Türkçe olmadan kalan başlıklar ("Role in the Movie",
 * "Appearances in Other Media") dahil bilinen tüm kalıplar eşlenmeli.
 */
class FandomSectionTitlesTest {

    @Test
    fun `screenshot headings map to turkish resources`() {
        assertEquals(R.string.fandom_section_role_movie, FandomSectionTitles.resId("Role in the Movie"))
        assertEquals(
            R.string.fandom_section_other_media,
            FandomSectionTitles.resId("Appearances in Other Media")
        )
        assertEquals(R.string.fandom_section_appearance, FandomSectionTitles.resId("Appearance"))
        assertEquals(R.string.fandom_section_personality, FandomSectionTitles.resId("Personality"))
        assertEquals(R.string.fandom_section_abilities, FandomSectionTitles.resId("Abilities"))
        assertEquals(R.string.fandom_section_etymology, FandomSectionTitles.resId("Etymology"))
        assertEquals(R.string.fandom_section_trivia, FandomSectionTitles.resId("Trivia"))
    }

    @Test
    fun `matching ignores case and extra whitespace`() {
        assertEquals(
            R.string.fandom_section_role_series,
            FandomSectionTitles.resId("  Role   in the Series ")
        )
        assertEquals(R.string.fandom_section_abilities, FandomSectionTitles.resId("POWERS & ABILITIES"))
    }

    @Test
    fun `unknown headings stay untouched`() {
        assertNull(FandomSectionTitles.resId("The World of Jujutsu"))
        assertNull(FandomSectionTitles.resId(""))
    }
}

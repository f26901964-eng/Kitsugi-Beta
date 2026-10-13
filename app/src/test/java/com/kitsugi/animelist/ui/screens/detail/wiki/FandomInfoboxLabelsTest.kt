package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ekran görüntüsündeki (Eren Yeager / Attack on Titan Wiki) bilgi kutusu etiketleri
 * ve kısa değerler yerel kaynaklara eşlenmeli; serbest metin çevrilmeden kalmalı.
 */
class FandomInfoboxLabelsTest {

    @Test
    fun `screenshot labels map to resources`() {
        assertEquals(R.string.fandom_ib_name, FandomInfoboxLabels.labelResId("Name"))
        assertEquals(R.string.fandom_ib_alias, FandomInfoboxLabels.labelResId("Alias"))
        assertEquals(R.string.fandom_ib_species, FandomInfoboxLabels.labelResId("Species"))
        assertEquals(R.string.fandom_ib_height, FandomInfoboxLabels.labelResId("Height"))
        assertEquals(R.string.fandom_ib_birthday, FandomInfoboxLabels.labelResId("Birthday"))
        assertEquals(R.string.fandom_ib_affiliation, FandomInfoboxLabels.labelResId("Affiliation"))
        assertEquals(R.string.fandom_ib_occupation, FandomInfoboxLabels.labelResId("Occupation"))
    }

    @Test
    fun `label matching ignores case, whitespace and trailing colon`() {
        assertEquals(R.string.fandom_ib_alias, FandomInfoboxLabels.labelResId("  ALIAS(ES): "))
        assertEquals(R.string.fandom_ib_birthday, FandomInfoboxLabels.labelResId("Date of   Birth"))
    }

    @Test
    fun `unknown label falls back to portable infobox source key`() {
        assertEquals(R.string.fandom_ib_occupation, FandomInfoboxLabels.labelResId("Trabajo", source = "occupation"))
        assertEquals(R.string.fandom_ib_height, FandomInfoboxLabels.labelResId("??", source = "height"))
        assertEquals(R.string.fandom_ib_form, FandomInfoboxLabels.labelResId("Titan form", source = "titan_form"))
        assertNull(FandomInfoboxLabels.labelResId("Paths", source = "paths_connection"))
    }

    @Test
    fun `group headers map to resources`() {
        assertEquals(R.string.fandom_ib_group_personal, FandomInfoboxLabels.headerResId("Biological Information"))
        assertEquals(R.string.fandom_ib_group_professional, FandomInfoboxLabels.headerResId("Professional Information"))
        assertEquals(R.string.fandom_ib_group_debut, FandomInfoboxLabels.headerResId("Debut"))
        assertEquals(R.string.fandom_ib_group_voice, FandomInfoboxLabels.headerResId("Voice actors"))
        assertNull(FandomInfoboxLabels.headerResId("Titan Information"))
    }

    @Test
    fun `only short enumerable values are localized`() {
        assertEquals(R.string.fandom_ib_value_male, FandomInfoboxLabels.valueResId("Male"))
        assertEquals(R.string.fandom_ib_value_deceased, FandomInfoboxLabels.valueResId("Deceased"))
        assertEquals(R.string.fandom_ib_value_unknown, FandomInfoboxLabels.valueResId("Unknown"))
        assertNull(FandomInfoboxLabels.valueResId("Survey Corps (formerly)"))
        assertNull(FandomInfoboxLabels.valueResId(""))
    }
}

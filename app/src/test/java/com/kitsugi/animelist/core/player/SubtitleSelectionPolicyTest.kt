package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSelectionPolicyTest {

    private fun c(name: String, lang: String?, label: String?, ext: Boolean) =
        SubtitleSelectionPolicy.Candidate(name, lang, label, ext)

    @Test
    fun embeddedTurkishBeatsExternalTurkish_evenIfExternalListedFirst() {
        val picked = SubtitleSelectionPolicy.pick(
            listOf(
                c("ext-tr", "tr", "Türkçe (Addon)", true),
                c("emb-tr", "tur", "Turkish", false),
            ),
            listOf("tr"),
        )
        assertEquals("emb-tr", picked)
    }

    @Test
    fun embeddedTurkishWithSameNameAsExternal_stillEmbeddedWins() {
        val picked = SubtitleSelectionPolicy.pick(
            listOf(
                c("ext", null, "Türkçe (Addon)", true),
                c("emb", null, "Türkçe", false),
            ),
            listOf("tr"),
        )
        assertEquals("emb", picked)
    }

    @Test
    fun externalTurkishBeatsEmbeddedEnglish() {
        val picked = SubtitleSelectionPolicy.pick(
            listOf(
                c("emb-en", "en", "English", false),
                c("ext-tr", "tr", "Türkçe", true),
            ),
            listOf("tr", "en"),
        )
        assertEquals("ext-tr", picked)
    }

    @Test
    fun regionTaggedTurkishIsRecognized() {
        val picked = SubtitleSelectionPolicy.pick(
            listOf(
                c("emb-en", "en", null, false),
                c("emb-trtr", "tr-TR", null, false),
            ),
            listOf("tr"),
        )
        assertEquals("emb-trtr", picked)
    }

    @Test
    fun otherPreferredLanguage_embeddedBeforeExternal() {
        val picked = SubtitleSelectionPolicy.pick(
            listOf(
                c("ext-en", "en", "English", true),
                c("emb-en", "en", "English", false),
            ),
            listOf("en"),
        )
        assertEquals("emb-en", picked)
    }

    @Test
    fun noMatch_returnsNull_foreignLanguageNeverChosen() {
        val picked = SubtitleSelectionPolicy.pick(
            listOf(
                c("it", "it", "Italiano", false),
                c("fr", "fr", "Français", true),
            ),
            listOf("tr"),
        )
        assertNull(picked)
    }

    @Test
    fun emptyCandidates_returnsNull() {
        assertNull(SubtitleSelectionPolicy.pick(emptyList<SubtitleSelectionPolicy.Candidate<String>>(), listOf("tr")))
    }

    @Test
    fun effectiveLanguages_addsTurkishFirstWhenMissing() {
        assertEquals(listOf("tr", "en"), SubtitleSelectionPolicy.effectiveLanguages(listOf("en")))
        assertEquals(listOf("en", "tr"), SubtitleSelectionPolicy.effectiveLanguages(listOf("en", "tr")))
    }
}

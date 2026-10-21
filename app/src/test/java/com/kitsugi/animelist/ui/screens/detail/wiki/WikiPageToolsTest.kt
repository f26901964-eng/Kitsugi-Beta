package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.*
import org.junit.Test

class WikiPageToolsTest {
    @Test fun automaticPreferenceAndManualOriginalWorkIndependently() {
        val prefs = WikiPagePreferences(true, "auto", "tr", emptySet())
        assertTrue(prefs.configFor("you-zitsu.fandom.com", null, null, 0).translate)
        assertFalse(prefs.configFor("you-zitsu.fandom.com", false, prefs.translationKey, 0).translate)
        assertTrue(prefs.configFor("you-zitsu.fandom.com", false, "old-preferences", 0).translate)
        val off = prefs.copy(autoTranslate = false)
        assertFalse(off.configFor("you-zitsu.fandom.com", null, null, 0).translate)
        assertTrue(off.configFor("you-zitsu.fandom.com", true, off.translationKey, 0).translate)
    }

    @Test fun adExceptionOnlyAffectsExactWikiHost() {
        val prefs = WikiPagePreferences(false, "auto", "tr", setOf("you-zitsu.fandom.com"))
        assertFalse(prefs.configFor("YOU-ZITSU.FANDOM.COM", null, null, 0).blockAds)
        assertTrue(prefs.configFor("naruto.fandom.com", null, null, 0).blockAds)
    }

    @Test fun resourceMatcherUsesHostBoundariesAndProtectsWikiContent() {
        val rules = WikiAdBlockRules.fromHosts("0.0.0.0 adserver.test\n0.0.0.0 wikia.nocookie.net")
        assertTrue(rules.shouldBlock("https://cdn.adserver.test/request", false))
        assertFalse(rules.shouldBlock("https://adserver.test/", true))
        assertFalse(rules.shouldBlock("https://adserver.test.safe.test/image", false))
        assertFalse(rules.shouldBlock("https://static.wikia.nocookie.net/image.png", false))
        assertTrue(rules.shouldBlock("https://services.fandom.com/ads/config", false))
        assertFalse(rules.shouldBlock("https://services.fandom.com/content/config", false))
    }

    @Test fun slotTokensCannotBeReorderedLostOrDuplicated() {
        val rows = listOf(WikiDomText(1, 0, "Hello"), WikiDomText(2, 0, "world"))
        assertEquals(listOf("Hello", "world"), WikiDomTranslationBatch.unpack(WikiDomTranslationBatch.pack(rows), 2))
        assertNull(WikiDomTranslationBatch.unpack("⟪K1⟫world⟪/K1⟫⟪K0⟫Hello⟪/K0⟫", 2))
        assertNull(WikiDomTranslationBatch.unpack("⟪K0⟫Hello⟪/K0⟫", 2))
        assertNull(WikiDomTranslationBatch.unpack("⟪K0⟫Hello⟪/K0⟫⟪K0⟫world⟪/K0⟫", 2))
    }

    @Test fun invalidLanguageValuesNeverEnterTranslationUrl() {
        val prefs = WikiPagePreferences(true, "en&key=oops", "auto", emptySet())
        val config = prefs.configFor("you-zitsu.fandom.com", null, null, 0)
        assertEquals("auto", config.source)
        assertEquals("tr", config.target)
    }
}

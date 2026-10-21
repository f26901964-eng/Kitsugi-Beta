package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.*
import org.junit.Test

class WikiReadingAppearanceTest {
    @Test fun sourceThemeIsDefaultAndAppThemeIsExplicit() {
        assertEquals("SITE", WikiReadingAppearance.resolve("unknown", true))
        assertEquals("DARK", WikiReadingAppearance.resolve("APP", true))
        assertEquals("LIGHT", WikiReadingAppearance.resolve("APP", false))
        assertEquals("LIGHT", WikiReadingAppearance.resolve("LIGHT", true))
    }

    @Test fun zoomIsBoundedAndRespectsAccessibilityScale() {
        assertEquals(80, WikiReadingAppearance.textPercent(Int.MIN_VALUE))
        assertEquals(180, WikiReadingAppearance.textPercent(Int.MAX_VALUE))
        assertEquals(130, WikiReadingAppearance.textPercent(125))
        assertEquals(180, WikiReadingAppearance.engineTextZoom(120, 1.5f))
        assertEquals(300, WikiReadingAppearance.engineTextZoom(180, 3f))
        assertEquals(100, WikiReadingAppearance.engineTextZoom(100, Float.NaN))
    }

    @Test fun appearanceDoesNotInvalidateManualTranslationOrReloadAds() {
        val prefs = WikiPagePreferences(true, "auto", "tr", emptySet())
        val changed = prefs.copy(pageTheme = "DARK", textZoom = 140)
        assertEquals(prefs.translationKey, changed.translationKey)
        assertEquals(prefs.configFor("you-zitsu.fandom.com", false, prefs.translationKey, 0),
            changed.configFor("you-zitsu.fandom.com", false, prefs.translationKey, 0))
    }
}

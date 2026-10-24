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

    @Test fun paletteUsesAppColoursOnlyWhenTheSchemeMatches() {
        val dark = WikiReadingAppearance.palette("DARK", true, "#101418", "#161b21", "#eef1f5")
        assertEquals("#101418", dark.background)
        assertEquals("#eef1f5", dark.text)
        assertEquals("#161b21", dark.surface)
        // Uygulama açıkken zorlanan koyu tema beyaz uygulama yüzeyini miras almaz.
        val forcedDark = WikiReadingAppearance.palette("DARK", false, "#ffffff", "#ffffff", "#101010")
        assertEquals("#0e1116", forcedDark.background)
        assertEquals("#e9edf3", forcedDark.text)
        val forcedLight = WikiReadingAppearance.palette("LIGHT", true, "#101418", "#161b21", "#eef1f5")
        assertEquals("#ffffff", forcedLight.background)
        assertEquals("#171a1f", forcedLight.text)
    }

    @Test fun paletteRejectsMalformedColoursAndKeepsJsonValid() {
        for (value in listOf("", "red", "#12345", "#GGGGGG", "url(javascript:alert(1))")) {
            val palette = WikiReadingAppearance.palette("DARK", false, value, value, value)
            assertEquals("#0e1116", palette.background)
            assertEquals("#e9edf3", palette.text)
        }
        val json = WikiReadingAppearance.palette("DARK", false, "", "", "").toJson()
        assertTrue(json.startsWith("{\"background\":\"#"))
        assertTrue(json.endsWith("\"}"))
        assertEquals(28, json.count { it == '"' })
        assertFalse(json.contains(" "))
    }

    @Test fun pureBlackCanvasStillGetsDistinctCards() {
        val palette = WikiReadingAppearance.palette("DARK", true, "#000000", "#000000", "#ffffff")
        assertEquals("#000000", palette.background)
        assertNotEquals(palette.background, palette.surface)
        assertNotEquals(palette.surface, palette.raised)
        assertNotEquals(palette.surface, palette.border)
        assertEquals("#808080", WikiReadingAppearance.mix("#000000", "#ffffff", 0.5f))
    }

    @Test fun desktopLayoutIsFittedAndTextKeepsItsSize() {
        assertEquals(1200, WikiReaderFit.layoutWidth(true, 360f))
        assertEquals(360, WikiReaderFit.layoutWidth(false, 360f))
        assertEquals(30, WikiReaderFit.initialScalePercent(360f, 1200))
        assertEquals(100, WikiReaderFit.initialScalePercent(2000f, 1200))
        assertEquals(1f, WikiReaderFit.scale(Float.NaN, 1200), 0.0001f)
        assertEquals(0.2f, WikiReaderFit.scale(1f, 1200), 0.0001f)
        val fit = WikiReaderFit.scale(360f, 1200)
        assertTrue(WikiReaderFit.readingTextZoom(100, fit) in 220..300)
        assertEquals(300, WikiReaderFit.readingTextZoom(180, fit))
        assertEquals(100, WikiReaderFit.readingTextZoom(100, 1f))
        assertEquals(100, WikiReaderFit.effectivePercent(100, 1f))
        assertTrue(WikiReaderFit.effectivePercent(100, fit) in 220..300)
    }

    @Test fun appearanceDoesNotInvalidateManualTranslationOrReloadAds() {
        val prefs = WikiPagePreferences(true, "auto", "tr", emptySet())
        val changed = prefs.copy(pageTheme = "DARK", textZoom = 140)
        assertEquals(prefs.translationKey, changed.translationKey)
        assertEquals(prefs.configFor("you-zitsu.fandom.com", false, prefs.translationKey, 0),
            changed.configFor("you-zitsu.fandom.com", false, prefs.translationKey, 0))
    }
}

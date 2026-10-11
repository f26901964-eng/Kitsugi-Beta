package com.kitsugi.animelist.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yatay mod vitrin yerleşimi:
 *  - vitrin yüksekliği sabit bir dp bandına sıkışmaz, ekran genişliği + 16:9
 *    arka plan oranından hesaplanır (yalnızca ekran yüksekliği sınırlar),
 *  - geniş bantta dikey görsel (manga kapağı) kırpılmaz, ortalanmış çizilir.
 */
class HeroLandscapeSizingTest {

    // ── Yatay mod yüksekliği: ekranla birlikte büyür ──────────────────────────

    @Test
    fun `landscape phone hero grows beyond the old hard cap`() {
        // 914dp x 411dp (1080x2400 @420dpi yatay) → eski sınır 265dp idi.
        val height = heroLandscapeHeightDp(
            screenWidthDp = 914f,
            screenHeightDp = 411f,
            minDp = 240f,
            maxFraction = 0.90f
        )
        // 16:9 için gereken 514dp, ekran yüksekliğinin %90'ı ile sınırlanır (370dp).
        assertEquals(369.9f, height.value, 0.6f)
        assertTrue("Eski 210–265 dp bandının üstüne çıkmalı", height.value > 265f)
    }

    @Test
    fun `landscape height is capped by screen height not by a fixed dp band`() {
        val phone = heroLandscapeHeightDp(800f, 360f, 240f, 0.90f)
        val tablet = heroLandscapeHeightDp(1280f, 800f, 340f, 0.80f)
        assertEquals(324f, phone.value, 0.5f)
        assertEquals(640f, tablet.value, 0.5f)
        assertTrue(phone.value <= 360f * 0.90f + 0.5f)
        assertTrue(tablet.value <= 800f * 0.80f + 0.5f)
    }

    @Test
    fun `image aspect drives height when the screen is tall enough`() {
        // Çok yüksek/geniş olmayan bir pencere: 16:9 oranı doğrudan uygulanır.
        val height = heroLandscapeHeightDp(
            screenWidthDp = 960f,
            screenHeightDp = 1200f,
            minDp = 240f,
            maxFraction = 0.90f
        )
        assertEquals(540f, height.value, 0.5f)
    }

    @Test
    fun `tiny landscape window never throws and keeps the minimum`() {
        // minDp, ekran yüksekliğinden büyük olsa bile coerceIn patlamamalı.
        val height = heroLandscapeHeightDp(
            screenWidthDp = 320f,
            screenHeightDp = 200f,
            minDp = 240f,
            maxFraction = 0.90f
        )
        assertEquals(240f, height.value, 0.5f)
    }

    // ── Geniş bantta dikey görsel: kırpma değil, ortalanmış gösterim ───────────

    @Test
    fun `manga cover in wide landscape band is shown centered instead of cropped`() {
        // Yatay telefon vitrini ~2.4:1 → manga kapağı (poster) kırpılırsa tanınmaz.
        assertTrue(heroUsesFitPresentation(chosenIsPoster = true, heroAspect = 2.47f))
        assertTrue(heroUsesFitPresentation(chosenIsPoster = true, heroAspect = 1.78f))
        assertTrue(heroUsesFitPresentation(chosenIsPoster = true, heroAspect = 1.15f))
    }

    @Test
    fun `wide backdrop keeps the full bleed crop`() {
        // Yatay fanart/backdrop vitrini baştan sona kaplamaya devam eder.
        assertFalse(heroUsesFitPresentation(chosenIsPoster = false, heroAspect = 2.47f))
        assertFalse(heroUsesFitPresentation(chosenIsPoster = false, heroAspect = 1.78f))
    }

    @Test
    fun `portrait hero keeps cropping the poster`() {
        // Dikey telefon vitrini (~0.5) dar olduğu için eski davranış korunur.
        assertFalse(heroUsesFitPresentation(chosenIsPoster = true, heroAspect = 0.46f))
        assertFalse(heroUsesFitPresentation(chosenIsPoster = true, heroAspect = 1.0f))
    }
}

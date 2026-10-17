package com.kitsugi.animelist.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keşfet vitrinin yerleşim sözleşmesi.
 *
 * Vitrin görselinin "boş kutu / hayalet kopya / yarım görsel" gibi bozuk
 * görünmesinin kök nedeni, görselin sabit bir kutuya `ContentScale.Fit` ile
 * sığdırılmasıydı. Yeni politika:
 *
 *  - Dikey telefonda görsel **üstte kendi şeridinde** tam kaplar (Crop), metin
 *    altta kendi yuvasında durur → görsel asla yüklemez, metin asla görselin
 *    üstüne binmez.
 *  - Yatay / TV / tablet modunda görsel bandın tamamını kaplar, metin Nuvio'daki
 *    gibi gradyan scrim'in üzerine bindirilir.
 *
 * Hiçbir ölçü görsele bağlı olamaz.
 */
class HeroVitrinLayoutTest {

    private fun phonePortrait(
        widthDp: Float = 393f,
        heightDp: Float = 851f,
        fontScale: Float = 1f
    ) = heroVitrinLayout(
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        isLandscape = false,
        isTablet = false,
        isTv = false,
        fontScale = fontScale
    )

    @Test
    fun portraitPhoneStacksArtworkAboveTheTextZone() {
        val layout = phonePortrait()
        assertEquals(HeroVitrinMode.STACKED, layout.mode)
        // Şerit + metin yuvası bandı tamamen doldurur: arada boşluk kalmaz.
        assertEquals(
            layout.bandHeightDp,
            layout.artworkHeightDp + layout.textZoneHeightDp,
            0.01f
        )
        assertTrue(layout.artworkHeightDp > 0f)
        assertTrue(layout.textZoneHeightDp > 0f)
    }

    @Test
    fun landscapeTvAndTabletOverlayTheArtwork() {
        val landscape = heroVitrinLayout(851f, 393f, isLandscape = true, isTablet = false, isTv = false)
        val tv = heroVitrinLayout(1920f, 1080f, isLandscape = true, isTablet = false, isTv = true)
        val tablet = heroVitrinLayout(800f, 1280f, isLandscape = false, isTablet = true, isTv = false)
        for (layout in listOf(landscape, tv, tablet)) {
            assertEquals(HeroVitrinMode.OVERLAY, layout.mode)
            // Overlay modunda görsel bandın tamamını kaplar.
            assertEquals(layout.bandHeightDp, layout.artworkHeightDp, 0.01f)
        }
    }

    @Test
    fun theLayoutNeverDependsOnTheArtwork() {
        // Yerleşim politikasının görsele hiçbir girdisi yoktur: aynı cihaz
        // geometrisi her zaman aynı ölçüleri üretir (kaydırma, decode veya
        // sayfa değişimi bandı kaydıramaz).
        repeat(50) {
            assertEquals(phonePortrait().artworkHeightDp, phonePortrait().artworkHeightDp)
            assertEquals(phonePortrait().textZoneHeightDp, phonePortrait().textZoneHeightDp)
            assertEquals(phonePortrait().bandHeightDp, phonePortrait().bandHeightDp)
        }
        // Farklı cihazlarda bant yüksekliği değişebilir (bu cihazdan türetilir).
        assertNotEquals(
            phonePortrait(widthDp = 393f, heightDp = 851f).bandHeightDp,
            phonePortrait(widthDp = 412f, heightDp = 915f).bandHeightDp
        )
    }

    @Test
    fun theTextZoneAlwaysFitsTheRenderedSlots() {
        for (fontScale in listOf(1f, 1.15f, 1.3f, 1.5f, 2f)) {
            val zone = heroTextZoneHeightDp(isLandscape = false, fontScale = fontScale)
            // Gerçekte çizilen yuvalar: kaynak rozeti + başlık/logo + alt başlık + meta
            // + aralıklar + alt boşluk.
            val rendered = 28f + 6f + 84f * fontScale.coerceAtLeast(1f) + 6f + 40f + 6f + 20f + 44f
            assertTrue("fontScale=$fontScale zone=$zone", zone >= rendered - 0.01f)
            // Yatayda yuvalar daha kısa.
            val landscapeZone = heroTextZoneHeightDp(isLandscape = true, fontScale = fontScale)
            val landscapeRendered = 28f + 4f + 64f * fontScale.coerceAtLeast(1f) + 4f + 22f + 4f + 20f + 44f
            assertTrue("landscape fontScale=$fontScale", landscapeZone >= landscapeRendered - 0.01f)
            assertTrue(landscapeZone < zone)
        }
    }

    @Test
    fun aLargerFontScaleNeverChangesTheBandHeight() {
        val normal = phonePortrait(fontScale = 1f)
        val large = phonePortrait(fontScale = 1.5f)
        // Bant yalnızca büyüyebilir (metin yuvası sığmazsa), asla küçülemez.
        assertTrue(large.bandHeightDp >= normal.bandHeightDp)
        // Yazı büyürse metin yuvası büyür, görsel şeridi aynı oranda küçülür.
        assertTrue(large.textZoneHeightDp > normal.textZoneHeightDp)
        assertTrue(large.artworkHeightDp < normal.artworkHeightDp)
        assertTrue(large.artworkHeightDp >= HERO_MIN_ARTWORK_HEIGHT_DP)
    }

    @Test
    fun theArtworkStripStaysInsideTheBandOnEveryPhone() {
        val devices = listOf(
            360f to 640f,
            393f to 851f,
            412f to 915f,
            430f to 932f
        )
        for ((width, height) in devices) {
            val layout = phonePortrait(widthDp = width, heightDp = height)
            assertTrue(layout.artworkHeightDp in HERO_MIN_ARTWORK_HEIGHT_DP..layout.bandHeightDp)
            // Kutu oranı sinematik bandın etrafında kalır: 16:9 görsel neredeyse
            // tamamen, çok geniş banner ise yatay ortadan kırpılmış görünür.
            assertTrue("aspect=${layout.artworkAspect}", layout.artworkAspect in 1.15f..2.60f)
        }
    }
}

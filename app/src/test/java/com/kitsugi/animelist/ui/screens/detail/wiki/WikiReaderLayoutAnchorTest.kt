package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Okuma konumu çapasının sözleşmesi: çeviri geçişinde konum kaymamalı.
 *
 * Kullanıcı kuralı: "bu wiki sayfaları özel olarak çeviriye gidip gelmeli ve konumları da
 * aynen korunmalı". Bu testler ölçüm → çapa → geri kurma zincirinin saf matematiğini
 * doğrular; Compose'a bağımlılık yoktur.
 */
class WikiReaderLayoutAnchorTest {

    private fun probe(scrollValue: Int, containerTop: Float = 100f): WikiReaderLayoutProbe =
        WikiReaderLayoutProbe { scrollValue }.also { it.onContainerPosition(containerTop) }

    @Test
    fun blockPositionsAreMeasuredInContentCoordinates() {
        val probe = probe(scrollValue = 0, containerTop = 100f)
        probe.record(id = "sec#b0", topInRoot = 300f, height = 120)
        assertEquals(200, probe.topOf("sec#b0"))
        assertEquals(120, probe.heightOf("sec#b0"))
    }

    @Test
    fun scrollingDoesNotChangeTheMeasuredContentPosition() {
        // Aynı blok, sayfa 500px kaydırılmışken ölçülüyor: kök koordinatı küçülür ama
        // içerik koordinatı değişmemeli.
        val probe = probe(scrollValue = 500, containerTop = 100f)
        probe.record(id = "sec#b0", topInRoot = -200f, height = 120)
        assertEquals(200, probe.topOf("sec#b0"))
    }

    @Test
    fun measurementsBeforeContainerPositionAreIgnored() {
        val probe = WikiReaderLayoutProbe { 0 }
        probe.record(id = "sec#b0", topInRoot = 300f, height = 120)
        assertNull(probe.topOf("sec#b0"))
    }

    @Test
    fun anchorIsTheBlockThatContainsTheViewportTop() {
        // Sayfa 350px kaydırılmışken blokların İÇERİK konumları:
        //   b0: 0..300   (tamamen yukarıda kaldı)
        //   b1: 300..500 (görünümün üstünü içeriyor → okunan paragraf)
        //   b2: 600..    (görünümün altında)
        val scrollValue = 350
        val probe = probe(scrollValue = scrollValue, containerTop = 0f)
        probe.record("sec#b0", topInRoot = 0f - scrollValue, height = 300)
        probe.record("sec#b1", topInRoot = 300f - scrollValue, height = 200)
        probe.record("sec#b2", topInRoot = 600f - scrollValue, height = 100)

        val anchor = probe.anchorFor(scrollValue = scrollValue)
        assertNotNull(anchor)
        assertEquals("sec#b1", anchor!!.id)
        assertEquals(300, anchor.top)
        assertEquals(200, anchor.height)
    }

    @Test
    fun anchorFallsBackToTheNextBlockWhenTheViewportTopIsInAGap() {
        // Görünümün üstü hiçbir bloğun içine düşmüyorsa (kart/boşluk) altındaki ilk blok
        // çapa olur; geri kurma bu durumda yalnızca mesafeyi korur, içerik zıplamaz.
        val scrollValue = 300
        val probe = probe(scrollValue = scrollValue, containerTop = 0f)
        probe.record("sec#b0", topInRoot = 0f - scrollValue, height = 100)
        probe.record("sec#b1", topInRoot = 400f - scrollValue, height = 200)

        val anchor = probe.anchorFor(scrollValue = scrollValue)
        assertNotNull(anchor)
        assertEquals("sec#b1", anchor!!.id)
        assertEquals(400, anchor.top)
    }

    @Test
    fun upcomingBlocksAreNeverUsedWhenAnOnScreenBlockExists() {
        // Görünümün üstünü içeren blok varken altındaki bloklar çapa seçilmemeli.
        val scrollValue = 500
        val probe = probe(scrollValue = scrollValue, containerTop = 0f)
        probe.record("sec#b0", topInRoot = 400f - scrollValue, height = 400)
        probe.record("sec#b1", topInRoot = 900f - scrollValue, height = 100)

        assertEquals("sec#b0", probe.anchorFor(scrollValue = scrollValue)?.id)
    }

    @Test
    fun anchorIsNullWhenNothingIsMeasured() {
        assertNull(probe(scrollValue = 100, containerTop = 0f).anchorFor(scrollValue = 100))
    }

    @Test
    fun restoreKeepsReadingRatioWhenTheTranslatedBlockChangesHeight() {
        // Kullanıcı 200px'lik paragrafın 100px içinde: yarısında. Çeviri paragrafı 400px
        // yaparsa yine yarısında kalmalı (aynı cümle ekranda kalır).
        val anchor = WikiReaderAnchor(id = "sec#b1", top = 1000, height = 200)
        assertEquals(
            1200,
            anchor.restoreScrollValue(newTop = 1000, newHeight = 400, scrollValue = 1100, maxScrollValue = 9000)
        )
    }

    @Test
    fun restoreIsNoOpWhenLayoutDidNotChange() {
        val anchor = WikiReaderAnchor(id = "sec#b1", top = 1000, height = 200)
        assertEquals(
            1100,
            anchor.restoreScrollValue(newTop = 1000, newHeight = 200, scrollValue = 1100, maxScrollValue = 9000)
        )
    }

    @Test
    fun restoreKeepsTheGapWhenTheAnchorStartsBelowTheViewport() {
        // Görünümün üstünde ölçülmüş bir blok yoksa (araya kart/boşluk girmişse) içerik
        // zıplamamalı: blok ile görünüm arasındaki mesafe korunur.
        val anchor = WikiReaderAnchor(id = "sec#b4", top = 1500, height = 200)
        assertEquals(
            1100,
            anchor.restoreScrollValue(newTop = 1600, newHeight = 300, scrollValue = 1000, maxScrollValue = 9000)
        )
    }

    @Test
    fun restoreClampsIntoScrollableRange() {
        val below = WikiReaderAnchor(id = "sec#b0", top = 5000, height = 100)
        assertEquals(
            0,
            below.restoreScrollValue(newTop = 4900, newHeight = 100, scrollValue = 0, maxScrollValue = 4000)
        )
        val above = WikiReaderAnchor(id = "sec#b0", top = 0, height = 100)
        assertEquals(
            4000,
            above.restoreScrollValue(newTop = 9000, newHeight = 100, scrollValue = 0, maxScrollValue = 4000)
        )
    }

    @Test
    fun clearingDropsThePreviousPageMeasurements() {
        val probe = probe(scrollValue = 0, containerTop = 0f)
        probe.record("sec#b0", 10f, 20)
        probe.clear()
        assertNull(probe.topOf("sec#b0"))
        assertNull(probe.anchorFor(0))
    }

    @Test
    fun idsStayStableBetweenOriginalAndTranslatedRendering() {
        // Blok sırası çeviride korunur; çapa kimliği bu yüzden aynı kalır.
        assertEquals("reader-section:w:1:Page#b3", WikiReaderLayoutProbe.blockId("reader-section:w:1:Page", 3))
        assertEquals(
            "reader-description:w:Page#desc",
            WikiReaderLayoutProbe.surfaceId("reader-description:w:Page", WikiReaderLayoutProbe.SURFACE_DESCRIPTION)
        )
    }
}

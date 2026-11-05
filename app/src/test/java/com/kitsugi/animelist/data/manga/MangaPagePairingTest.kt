package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Çift sayfa (2-up / spread) eşleştirme mantığı testleri.
 *
 * Kapsam:
 *  - Dikine sayfalar ikişer, geniş sayfalar tek
 *  - Tek kalan sayfa (kapak/son sayfa) tek başına
 *  - RTL/LTR gösterim sırası
 *  - Sayfa → çift indeksi dönüşümü (ilerleme kaydı için)
 *  - "Geniş ekranda" eşiği
 */
class MangaPagePairingTest {

    private val portrait = 0.7f   // 2:3 poster
    private val wide = 1.6f       // çift sayfa tarama

    // ─── Temel çiftleme ───────────────────────────────────────────────────────

    @Test
    fun `spreads pairs pages two by two`() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4, 5)),
            MangaPagePairing.spreads(6)
        )
    }

    @Test
    fun `spreads keeps last odd page alone`() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
            MangaPagePairing.spreads(5)
        )
    }

    @Test
    fun `spreads of empty and single page`() {
        assertTrue(MangaPagePairing.spreads(0).isEmpty())
        assertEquals(listOf(listOf(0)), MangaPagePairing.spreads(1))
    }

    // ─── Akıllı çiftleme (geniş sayfalar tek kalır) ───────────────────────────

    @Test
    fun `smartSpreads pairs only portrait pages`() {
        val aspects = mapOf(0 to portrait, 1 to portrait, 2 to portrait, 3 to portrait)
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3)),
            MangaPagePairing.smartSpreads(4, aspects)
        )
    }

    @Test
    fun `smartSpreads leaves wide page alone without breaking order`() {
        val aspects = mapOf(
            0 to portrait, 1 to portrait,
            2 to wide,
            3 to portrait, 4 to portrait
        )
        val spreads = MangaPagePairing.smartSpreads(5, aspects)
        // Geniş sayfa tek başına; sonrasında eşleşme yeniden kurulur.
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4)), spreads)
        // Sıra korunur: tüm sayfalar tam bir kez, artan sırada geçer.
        assertEquals(listOf(0, 1, 2, 3, 4), spreads.flatten())
    }

    @Test
    fun `smartSpreads handles unknown aspect as portrait`() {
        val spreads = MangaPagePairing.smartSpreads(4, emptyMap())
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), spreads)
    }

    @Test
    fun `smartSpreads never loses or duplicates a page`() {
        val aspects = mapOf(1 to wide, 4 to wide, 5 to wide)
        val spreads = MangaPagePairing.smartSpreads(8, aspects)
        assertEquals((0 until 8).toList(), spreads.flatten())
        assertTrue(spreads.all { it.size in 1..2 })
    }

    @Test
    fun `isPortrait rejects square and landscape`() {
        assertTrue(MangaPagePairing.isPortrait(0.66f))
        assertFalse(MangaPagePairing.isPortrait(1.0f))
        assertFalse(MangaPagePairing.isPortrait(1.4f))
        assertFalse(MangaPagePairing.isPortrait(0f))
    }

    // ─── Gösterim sırası (okuma yönü) ─────────────────────────────────────────

    @Test
    fun `displayOrder is reversed for right to left`() {
        val spread = listOf(2, 3)
        assertEquals(listOf(3, 2), MangaPagePairing.displayOrder(spread, rightToLeft = true))
        assertEquals(listOf(2, 3), MangaPagePairing.displayOrder(spread, rightToLeft = false))
    }

    @Test
    fun `displayOrder keeps single page untouched`() {
        assertEquals(listOf(4), MangaPagePairing.displayOrder(listOf(4), rightToLeft = true))
    }

    // ─── Sayfa → çift indeksi ─────────────────────────────────────────────────

    @Test
    fun `spreadIndexForPage maps both members of a pair`() {
        val spreads = listOf(listOf(0, 1), listOf(2), listOf(3, 4))
        assertEquals(0, MangaPagePairing.spreadIndexForPage(spreads, 0))
        assertEquals(0, MangaPagePairing.spreadIndexForPage(spreads, 1))
        assertEquals(1, MangaPagePairing.spreadIndexForPage(spreads, 2))
        assertEquals(2, MangaPagePairing.spreadIndexForPage(spreads, 3))
        assertEquals(2, MangaPagePairing.spreadIndexForPage(spreads, 4))
    }

    @Test
    fun `spreadIndexForPage falls back to zero`() {
        assertEquals(0, MangaPagePairing.spreadIndexForPage(emptyList(), 7))
        assertEquals(0, MangaPagePairing.spreadIndexOf(0))
        assertEquals(2, MangaPagePairing.spreadIndexOf(5))
    }

    // ─── Ekran genişliği eşiği ────────────────────────────────────────────────

    @Test
    fun `shouldShowTwoPages honours mode and width`() {
        assertFalse(MangaPagePairing.shouldShowTwoPages(DualPageMode.Off, 1200))
        assertTrue(MangaPagePairing.shouldShowTwoPages(DualPageMode.Always, 360))
        assertTrue(MangaPagePairing.shouldShowTwoPages(DualPageMode.WideOnly, 800))
        assertFalse(MangaPagePairing.shouldShowTwoPages(DualPageMode.WideOnly, 411))
        assertTrue(
            MangaPagePairing.shouldShowTwoPages(
                DualPageMode.WideOnly,
                MangaPagePairing.WIDE_SCREEN_DP
            )
        )
    }

    // ─── Ayar kimlikleri ──────────────────────────────────────────────────────

    @Test
    fun `dual page mode ids round trip`() {
        DualPageMode.entries.forEach { mode ->
            assertEquals(mode, DualPageMode.fromId(DualPageMode.idOf(mode)))
        }
        assertEquals(DualPageMode.Off, DualPageMode.fromId(null))
        assertEquals(DualPageMode.Off, DualPageMode.fromId("bilinmeyen"))
        assertEquals(DualPageMode.Always, DualPageMode.fromId("on"))
        assertEquals(DualPageMode.WideOnly, DualPageMode.fromId("auto"))
    }
}

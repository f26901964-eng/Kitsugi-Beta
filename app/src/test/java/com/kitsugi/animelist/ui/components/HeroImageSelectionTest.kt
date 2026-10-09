package com.kitsugi.animelist.ui.components

import androidx.compose.ui.Alignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vitrin görsel seçimi: ekran boyutu (vitirin kutusunun en-boy oranı) ve
 * dikey/yatay moda göre kaynak önceliği + kaplayan (Crop) kırpma odağı.
 */
class HeroImageSelectionTest {

    private val poster = "https://img.test/poster.jpg"
    private val backdrop = "https://img.test/backdrop.jpg"

    // ── Geniş vitrin bandı → yatay fanart/backdrop önce ────────────────────────

    @Test
    fun `wide landscape band prefers backdrop then poster`() {
        // Yatay tablet: 1200dp x 320dp → en-boy ~3.75
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 1200f,
            heroHeightDp = 320f,
            isLandscape = true
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    @Test
    fun `portrait tablet band still prefers backdrop when band is wide`() {
        // Dikey tablet: 800dp x 450dp → en-boy ~1.78 (yatay dikdörtgen hem dikey
        // hem yatay moda uyumlu olduğundan geniş bantta yine o tercih edilir)
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 800f,
            heroHeightDp = 450f,
            isLandscape = false
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    // ── Dikey telefon vitrini → poster önce ────────────────────────────────────

    @Test
    fun `portrait phone hero prefers poster then backdrop`() {
        // Dikey telefon: 400dp x 430dp → en-boy ~0.93
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 400f,
            heroHeightDp = 430f,
            isLandscape = false
        )
        assertEquals(listOf(poster, backdrop), candidates)
    }

    @Test
    fun `square-ish portrait hero prefers poster`() {
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 411f,
            heroHeightDp = 411f,
            isLandscape = false
        )
        assertEquals(listOf(poster, backdrop), candidates)
    }

    @Test
    fun `landscape flag breaks near-square tie toward backdrop`() {
        // En-boy ~0.96 ölçülmüş ama yön bilgisi yatay → backdrop yine de önde
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 640f,
            heroHeightDp = 666f,
            isLandscape = true
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    // ── Eksik kaynaklar ve yedek zinciri ───────────────────────────────────────

    @Test
    fun `missing backdrop falls back to poster only`() {
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = null,
            heroWidthDp = 1200f,
            heroHeightDp = 320f,
            isLandscape = true
        )
        assertEquals(listOf(poster), candidates)
    }

    @Test
    fun `missing poster falls back to backdrop only`() {
        val candidates = heroImageCandidates(
            posterUrl = "   ",
            backdropUrl = backdrop,
            heroWidthDp = 400f,
            heroHeightDp = 430f,
            isLandscape = false
        )
        assertEquals(listOf(backdrop), candidates)
    }

    @Test
    fun `both missing yields empty candidates`() {
        val candidates = heroImageCandidates(
            posterUrl = null,
            backdropUrl = "  ",
            heroWidthDp = 1200f,
            heroHeightDp = 320f,
            isLandscape = true
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `blank urls are trimmed and normalized`() {
        val candidates = heroImageCandidates(
            posterUrl = "  $poster  ",
            backdropUrl = "  $backdrop  ",
            heroWidthDp = 400f,
            heroHeightDp = 430f,
            isLandscape = false
        )
        assertEquals(listOf(poster, backdrop), candidates)
    }

    // ── Ölçüm yoksa yön bilgisinden oran türetilir ─────────────────────────────

    @Test
    fun `zero dimensions use orientation to derive aspect`() {
        val landscape = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 0f,
            heroHeightDp = 0f,
            isLandscape = true
        )
        assertEquals(listOf(backdrop, poster), landscape)

        val portrait = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 0f,
            heroHeightDp = 0f,
            isLandscape = false
        )
        assertEquals(listOf(poster, backdrop), portrait)
    }

    // ── Kaplayan kırpma odağı ──────────────────────────────────────────────────

    @Test
    fun `backdrop crop stays centered in every band`() {
        assertEquals(Alignment.Center, heroImageAlignment(chosenIsPoster = false, heroAspect = 3.75f))
        assertEquals(Alignment.Center, heroImageAlignment(chosenIsPoster = false, heroAspect = 0.93f))
    }

    @Test
    fun `poster crop in wide band biases slightly to top`() {
        // Geniş bantta poster kırpılırken yüz/başlık bandı korunmalı
        assertEquals(Alignment(0f, -0.2f), heroImageAlignment(chosenIsPoster = true, heroAspect = 1.2f))
        assertNotEquals(Alignment.Center, heroImageAlignment(chosenIsPoster = true, heroAspect = 4f))
    }

    @Test
    fun `poster crop in portrait hero stays centered`() {
        assertEquals(Alignment.Center, heroImageAlignment(chosenIsPoster = true, heroAspect = 0.93f))
    }
}

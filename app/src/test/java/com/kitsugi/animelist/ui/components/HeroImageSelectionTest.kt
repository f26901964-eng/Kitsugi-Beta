package com.kitsugi.animelist.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vitrin görsel seçimi: dizi/film/anime için yatay backdrop önceliği,
 * manga için ekran oranına uygun kapak önceliği ve kaplayan kırpma odağı.
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

    // ── Anime / film / dizi: backdrop her telefon oranında önce ────────────────

    @Test
    fun `portrait phone hero prefers backdrop then poster`() {
        // Dikey telefon: poster mevcut olsa da geniş hero'da yatay artwork önceliklidir.
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 400f,
            heroHeightDp = 430f,
            isLandscape = false,
            mediaType = MediaType.TvShow
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    @Test
    fun `square-ish portrait anime hero prefers backdrop`() {
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 411f,
            heroHeightDp = 411f,
            isLandscape = false,
            mediaType = MediaType.Anime
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    @Test
    fun `portrait manga keeps its poster first`() {
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 400f,
            heroHeightDp = 430f,
            isLandscape = false,
            mediaType = MediaType.Manga
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
        assertEquals(listOf(backdrop, poster), candidates)
    }

    // ── Ölçüm yoksa yön bilgisinden oran türetilir ─────────────────────────────

    @Test
    fun `zero dimensions keep landscape artwork first for anime in both orientations`() {
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
        assertEquals(listOf(backdrop, poster), portrait)
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
        assertEquals(BiasAlignment(0f, -0.2f), heroImageAlignment(chosenIsPoster = true, heroAspect = 1.2f))
        assertNotEquals(Alignment.Center, heroImageAlignment(chosenIsPoster = true, heroAspect = 4f))
    }

    @Test
    fun `poster crop in portrait hero stays centered`() {
        assertEquals(Alignment.Center, heroImageAlignment(chosenIsPoster = true, heroAspect = 0.93f))
    }
}

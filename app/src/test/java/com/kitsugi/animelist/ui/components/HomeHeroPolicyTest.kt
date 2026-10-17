package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class HomeHeroPolicyTest {
    private fun item(id: Int) = JikanSearchResult(
        malId = id, title = "Title $id", subtitle = "", type = MediaType.Anime,
        total = null, score = null, isAdult = false, imageUrl = "https://example.test/poster_$id.jpg",
        year = 2026, source = "anilist", backdropUrl = "https://example.test/wide_$id.jpg"
    )

    @Test fun dimensionsMustReallyBeHorizontal() {
        assertTrue(isHorizontalHomeHeroArtwork(1920, 1080))
        assertTrue(isHorizontalHomeHeroArtwork(1200, 500))
        assertFalse(isHorizontalHomeHeroArtwork(1000, 1500))
        assertFalse(isHorizontalHomeHeroArtwork(1000, 1000))
        assertFalse(isHorizontalHomeHeroArtwork(0, 0))
        assertFalse(isHorizontalHomeHeroArtwork(-1, 10))
    }

    @Test fun aResolvedBangumiBackdropIsNotRejectedJustBecauseItsSourceIsBangumi() {
        val wide = "https://image.tmdb.org/t/p/w1280/wide.jpg"
        val bangumi = item(1).copy(
            source = "bangumi", imageUrl = "https://lain.bgm.tv/pic/cover/l/poster.jpg", backdropUrl = wide
        )
        assertEquals("https://image.tmdb.org/t/p/original/wide.jpg", homeHeroBackdropUrl(bangumi))
        assertNull(homeHeroBackdropUrl(bangumi.copy(backdropUrl = bangumi.imageUrl)))
        assertEquals("https://image.tmdb.org/t/p/original/wide.jpg",
            KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(wide, bangumi.imageUrl, "bangumi"))
    }

    @Test fun posterUrlCannotBeRelabeledAsBackdropByChangingItsResolution() {
        val same = item(1).copy(
            imageUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/poster.jpg"
        )
        assertNull(homeHeroBackdropUrl(same))
        assertNull(homeHeroBackdropUrl(item(1).copy(backdropUrl = "file:///poster.jpg")))
    }

    @Test fun imageLoadingCannotChangeTheDeviceBasedHeroBand() {
        // No current page, image aspect, poster/backdrop mode, or loading state is an input.
        val portrait = heroBandHeightDp(800f, isLandscape = false, isTablet = false, isTv = false)
        val landscape = heroBandHeightDp(390f, isLandscape = true, isTablet = false, isTv = false)
        repeat(50) {
            assertEquals(portrait, heroBandHeightDp(800f, false, false, false))
            assertEquals(landscape, heroBandHeightDp(390f, true, false, false))
        }
        assertTrue(portrait > 0f && landscape > 0f)
    }

    @Test fun slowSourceResultsDoNotReorderExistingPages() {
        val a = item(1)
        val b = item(2)
        val c = item(3)
        val d = item(4)
        assertEquals(listOf(a, b, d, c), stableHomeHeroOrder(listOf(a, b), listOf(d, b, c, a)))
        // Apply current metadata, not the stale object, while retaining its page position.
        val updated = b.copy(title = "New title")
        assertEquals(listOf(updated, c), stableHomeHeroOrder(listOf(a, b), listOf(c, updated)))
    }

    @Test fun pageKeysKeepMediaAndSourceNamespacesButIgnoreArtworkChanges() {
        val a = item(1)
        assertEquals(homeHeroItemKey(a), homeHeroItemKey(a.copy(backdropUrl = "https://example.test/better.jpg")))
        assertNotEquals(homeHeroItemKey(a), homeHeroItemKey(a.copy(type = MediaType.Movie)))
        assertNotEquals(homeHeroItemKey(a), homeHeroItemKey(a.copy(source = "kitsu")))
        assertNotEquals(homeHeroItemKey(a.copy(cs3Url = "https://site/a")), homeHeroItemKey(a.copy(cs3Url = "https://site/b")))
    }

    @Test fun indicatorsAreBoundedAndAlwaysIncludeTheActivePage() {
        for (count in listOf(1, 2, 7, 18, 70)) {
            for (page in 0 until count) {
                val window = homeHeroIndicatorIndices(count, page)
                assertEquals(minOf(count, 7), window.size)
                assertTrue(page in window)
                assertTrue(window.all { it in 0 until count })
                assertEquals(window.sorted().distinct(), window)
            }
        }
        assertTrue(homeHeroIndicatorIndices(0, 0).isEmpty())
        assertTrue(homeHeroIndicatorIndices(4, 0, windowSize = 0).isEmpty())
    }

    @Test fun autoplayNeverRewindsAcrossTheWholeCarouselAtAnEdge() {
        assertEquals(0, homeHeroAutoDirection(0, 1, 1))
        assertEquals(1, homeHeroAutoDirection(0, 70, -1))
        assertEquals(-1, homeHeroAutoDirection(69, 70, 1))
        assertEquals(1, homeHeroAutoDirection(35, 70, 1))
        assertEquals(-1, homeHeroAutoDirection(35, 70, -1))
    }
}

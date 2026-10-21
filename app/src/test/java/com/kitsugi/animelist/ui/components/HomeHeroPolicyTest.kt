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

    @Test fun indicatorsAlwaysMatchTheRealItemCount() {
        // Vitrin barı içerik sayısını birebir göstermeli: kayan pencere/kısaltma yok.
        for (count in listOf(0, 1, 2, 7, 18, 70)) {
            val indices = homeHeroIndicatorIndices(count)
            assertEquals(count, indices.size)
            assertEquals((0 until count).toList(), indices)
        }
        assertTrue(homeHeroIndicatorIndices(0).isEmpty())
        assertTrue(homeHeroIndicatorIndices(-3).isEmpty())
    }

    @Test fun indicatorRowFitsTheAvailableWidthAndKeepsNaturalWidthWhenFew() {
        // Az içerikte doğal (yuva hedefi) genişlik; çok içerikte satır kullanılabilir
        // genişliği aşmaz — barlar incelir ama bar sayısı gerçek sayıya eşit kalır.
        val available = 360f - 32f
        assertEquals(4 * HERO_INDICATOR_SLOT_DP + 3 * 6f, heroIndicatorRowWidthDp(4, available), 0.01f)
        assertEquals(available, heroIndicatorRowWidthDp(18, available), 0.01f)
        assertEquals(available, heroIndicatorRowWidthDp(70, available), 0.01f)
        assertEquals(0f, heroIndicatorRowWidthDp(0, available), 0.01f)
        assertTrue(heroIndicatorSpacingDp(4) > heroIndicatorSpacingDp(18))
        assertTrue(heroIndicatorSpacingDp(18) >= heroIndicatorSpacingDp(70))
    }

    @Test fun portraitPosterIsUpgradedToItsHighestResolutionVariant() {
        val tmdb = item(1).copy(
            source = "tmdb",
            imageUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/wide.jpg"
        )
        assertEquals("https://image.tmdb.org/t/p/original/poster.jpg", homeHeroPosterUrl(tmdb))
        val bangumi = item(2).copy(
            source = "bangumi",
            imageUrl = "https://lain.bgm.tv/pic/cover/c/example.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/wide.jpg"
        )
        assertEquals("https://lain.bgm.tv/pic/cover/l/example.jpg", homeHeroPosterUrl(bangumi))
        val aniList = item(3).copy(
            source = "anilist",
            imageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/bx123.jpg",
            backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/123.jpg"
        )
        assertEquals(
            "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx123.jpg",
            homeHeroPosterUrl(aniList)
        )
    }

    @Test fun posterSharingTheBackdropIdentityIsNotAPoster() {
        val same = item(1).copy(
            imageUrl = "https://image.tmdb.org/t/p/w500/art.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/art.jpg"
        )
        assertNull(homeHeroPosterUrl(same))
        assertNull(homeHeroPosterUrl(item(1).copy(imageUrl = null)))
        assertNull(homeHeroPosterUrl(item(1).copy(imageUrl = "file:///poster.jpg")))
    }

    @Test fun verticalPosterDimensionsAreCheckedAsStrictlyAsBackdrops() {
        assertTrue(isVerticalHomeHeroPoster(1000, 1500))
        assertFalse(isVerticalHomeHeroPoster(1920, 1080))
        assertFalse(isVerticalHomeHeroPoster(1000, 1000))
        assertFalse(isVerticalHomeHeroPoster(0, 0))
        assertTrue(isVerticalHomeHeroPosterAspect(2f / 3f))
        assertFalse(isVerticalHomeHeroPosterAspect(16f / 9f))
        assertFalse(isVerticalHomeHeroPosterAspect(1f))
        assertFalse(isVerticalHomeHeroPosterAspect(Float.NaN))
        assertFalse(isVerticalHomeHeroPosterAspect(0f))
    }

    @Test fun autoplayWaitsFiveSecondsBetweenPages() {
        assertEquals(5_000L, HERO_AUTOPLAY_DELAY_MS)
        assertTrue(HERO_AUTOPLAY_TICK_MS in 1..HERO_AUTOPLAY_DELAY_MS)
    }

    @Test fun autoplayNeverRewindsAcrossTheWholeCarouselAtAnEdge() {
        assertEquals(0, homeHeroAutoDirection(0, 1, 1))
        assertEquals(1, homeHeroAutoDirection(0, 70, -1))
        assertEquals(-1, homeHeroAutoDirection(69, 70, 1))
        assertEquals(1, homeHeroAutoDirection(35, 70, 1))
        assertEquals(-1, homeHeroAutoDirection(35, 70, -1))
    }
}

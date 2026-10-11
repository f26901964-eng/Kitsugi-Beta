package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.shouldShowHeroLogo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsuPaginationAndSimklLogoTest {

    private fun simklItem(type: MediaType) = JikanSearchResult(
        malId = 179_062,
        title = "Sousou no Frieren",
        subtitle = "Anime · 2023",
        type = type,
        total = 28,
        score = 92,
        isAdult = false,
        imageUrl = null,
        year = 2023,
        source = "simkl"
    )

    @Test
    fun `kitsu filtered anime and manga queries compensate typesense 1-based page offset`() {
        val page1 = "/anime?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=0"
        val page2 = "/anime?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=20"
        val page3 = "/anime?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=40"

        assertEquals(page1, KitsuExploreClient.adjustKitsuTypesenseQuery(page1))
        assertEquals(
            "/anime?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=40",
            KitsuExploreClient.adjustKitsuTypesenseQuery(page2)
        )
        assertEquals(
            "/anime?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=60",
            KitsuExploreClient.adjustKitsuTypesenseQuery(page3)
        )

        val mangaPage2 = "/manga?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=20"
        assertEquals(
            "/manga?filter[status]=current&sort=-userCount&page[limit]=20&page[offset]=40",
            KitsuExploreClient.adjustKitsuTypesenseQuery(mangaPage2)
        )

        val seasonalPage2 = "/anime?filter[season]=summer&filter[seasonYear]=2026&sort=-userCount&page[limit]=20&page[offset]=20"
        assertEquals(
            "/anime?filter[season]=summer&filter[seasonYear]=2026&sort=-userCount&page[limit]=20&page[offset]=40",
            KitsuExploreClient.adjustKitsuTypesenseQuery(seasonalPage2)
        )
    }

    @Test
    fun `kitsu unfiltered queries keep direct sql offset unchanged`() {
        val topAnimePage2 = "/anime?sort=-userCount&page[limit]=20&page[offset]=20"
        val topRatedMangaPage2 = "/manga?sort=-averageRating&page[limit]=20&page[offset]=20"

        assertFalse(KitsuExploreClient.usesTypesensePaging(topAnimePage2))
        assertEquals(topAnimePage2, KitsuExploreClient.adjustKitsuTypesenseQuery(topAnimePage2))
        assertEquals(topRatedMangaPage2, KitsuExploreClient.adjustKitsuTypesenseQuery(topRatedMangaPage2))
    }

    @Test
    fun `kitsu pagingAnchorKey normalizes parameter order and strips offset`() {
        val q1 = "/anime?page[limit]=20&page[offset]=0&filter[season]=summer&filter[seasonYear]=2026&sort=-userCount"
        val q2 = "/anime?filter[season]=summer&filter[seasonYear]=2026&sort=-userCount&page[limit]=20&page[offset]=20"

        assertEquals(
            KitsuExploreClient.pagingAnchorKey(q1),
            KitsuExploreClient.pagingAnchorKey(q2)
        )
    }

    @Test
    fun `kitsu api host keeps primary host first even after degrade`() {
        KitsuApiHost.degrade()
        assertEquals(KitsuApiHost.PRIMARY, KitsuApiHost.base)
        assertEquals(listOf(KitsuApiHost.PRIMARY, KitsuApiHost.LEGACY), KitsuApiHost.candidates())
    }

    @Test
    fun `simkl hero logo policy enables logo lookup for anime movie and tv`() {
        assertTrue(shouldShowHeroLogo(simklItem(MediaType.Anime), showAnimeLogos = true))
        assertTrue(shouldShowHeroLogo(simklItem(MediaType.Movie), showAnimeLogos = true))
        assertTrue(shouldShowHeroLogo(simklItem(MediaType.TvShow), showAnimeLogos = true))
        assertTrue(shouldShowHeroLogo(simklItem(MediaType.Anime), showAnimeLogos = false))
        assertFalse(shouldShowHeroLogo(simklItem(MediaType.Manga), showAnimeLogos = true))
    }
}

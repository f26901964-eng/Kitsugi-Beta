package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchHandoffTest {
    private fun result(id: Int, source: String, type: MediaType = MediaType.Manga) =
        JikanSearchResult(id, "The Greatest Estate Developer", "", type, null, null, false, null, 2021, source)

    @Test fun sourceShelvesSupportTheSameMixedScopeAsAllSearch() {
        for (engine in listOf(SearchSourceEngine.ANILIST, SearchSourceEngine.MAL,
            SearchSourceEngine.SHIKIMORI, SearchSourceEngine.KITSU)) {
            assertTrue("$engine must search anime AND manga", SearchScope.ALL_MIXED in engine.availableScopes())
        }
    }

    @Test fun shelfMangaSurvivesEmptyRefresh() {
        val shelf = result(147272, "mal")
        assertEquals(listOf(shelf), mergeSourceSearchResults(listOf(shelf), emptyList()))
    }

    @Test fun fullResultsAppendWithoutRepeatingShelfItems() {
        val shelf = result(147272, "anilist")
        val next = result(147273, "anilist")
        assertEquals(listOf(shelf, next), mergeSourceSearchResults(listOf(shelf), listOf(shelf, next)))
    }

    @Test fun sameIdFromAnimeAndMangaIsNotDropped() {
        val manga = result(42, "kitsu")
        val anime = result(42, "kitsu", MediaType.Anime)
        assertEquals(listOf(anime, manga), mergeSourceSearchResults(listOf(anime), listOf(manga)))
    }
}

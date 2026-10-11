package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsPluginTypeCompatibilityTest {
    @Test
    fun animeOnlyPluginIsNotUsedForLiveActionTvSeries() {
        assertFalse(
            CsPluginTypeCompatibility.supports(
                MediaType.TvShow,
                listOf("Anime", "OVA", "AnimeMovie")
            )
        )
    }

    @Test
    fun liveActionTvPluginSupportsTvShowButNotAnime() {
        val types = listOf("TvSeries", "AsianDrama")

        assertTrue(CsPluginTypeCompatibility.supports(MediaType.TvShow, types))
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.TvShow, listOf("Tv Series")))
        assertFalse(CsPluginTypeCompatibility.supports(MediaType.Anime, types))
    }

    @Test
    fun movieAndAnimeMovieTypesStayDistinctButAnimeMovieSupportsBoth() {
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.Movie, listOf("Movie")))
        assertFalse(CsPluginTypeCompatibility.supports(MediaType.TvShow, listOf("Movie")))
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.Movie, listOf("AnimeMovie")))
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.Anime, listOf("AnimeMovie")))
    }

    @Test
    fun directUrlTypeCanBeInferredForSingleCategoryProviders() {
        assertTrue(
            CsPluginTypeCompatibility.infer(null, isMovie = false, supportedTypes = listOf("TvSeries"))
                == MediaType.TvShow
        )
        assertTrue(
            CsPluginTypeCompatibility.infer(null, isMovie = false, supportedTypes = listOf("Anime", "OVA"))
                == MediaType.Anime
        )
        assertTrue(
            CsPluginTypeCompatibility.infer(null, isMovie = true, supportedTypes = listOf("TvSeries"))
                == MediaType.Movie
        )
    }

    @Test
    fun missingTypeDeclarationsRemainEligible() {
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.TvShow, emptyList()))
        assertTrue(CsPluginTypeCompatibility.infer(null, isMovie = false, supportedTypes = emptyList()) == null)
    }
}

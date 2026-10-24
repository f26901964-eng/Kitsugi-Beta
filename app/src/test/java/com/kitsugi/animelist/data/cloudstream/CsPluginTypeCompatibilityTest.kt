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
    fun liveActionTvPluginSupportsTvShowAndAnimeRelaxation() {
        val types = listOf("TvSeries", "AsianDrama")

        assertTrue(CsPluginTypeCompatibility.supports(MediaType.TvShow, types))
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.TvShow, listOf("Tv Series")))
        // v2.4.250+: TR dizi siteleri dublajlı anime barındırdığı için Anime türü TvSeries eklentilerini de destekler
        assertTrue(CsPluginTypeCompatibility.supports(MediaType.Anime, types))
        // Ancak Manga/Çizgi Roman eklentileri Anime veya Dizi için desteklenmez
        assertFalse(CsPluginTypeCompatibility.supports(MediaType.Anime, listOf("Manga", "Comic")))
        assertFalse(CsPluginTypeCompatibility.supports(MediaType.TvShow, listOf("Manga", "Comic")))
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

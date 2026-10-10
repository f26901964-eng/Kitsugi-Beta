package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioSourceSupportTest {
    @Test
    fun explicitStudioNamespaceWinsOverParentMediaSource() {
        assertEquals(
            "jikan",
            StudioSourceSupport.resolveClickSource("jikan", "anilist", MediaType.Anime)
        )
        assertEquals(
            "jikan",
            StudioSourceSupport.resolveClickSource("mal", "anilist", MediaType.Anime)
        )
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource("tmdb", "anilist", MediaType.Movie)
        )
        assertEquals(
            "shikimori",
            StudioSourceSupport.resolveClickSource("shikimori", "jikan", MediaType.Anime)
        )
        assertEquals(
            "bangumi",
            StudioSourceSupport.resolveClickSource("bangumi", "jikan", MediaType.Anime)
        )
    }

    @Test
    fun tmdbNetworksUseTheirOwnNamespaceInsteadOfCompanyIds() {
        assertEquals(StudioSourceSupport.TMDB_NETWORK_SOURCE, StudioSourceSupport.canonicalSource("TMDB-Network"))
        assertEquals(
            StudioSourceSupport.TMDB_NETWORK_SOURCE,
            StudioSourceSupport.resolveClickSource("tmdb", "simkl", MediaType.TvShow, StudioRole.NETWORK)
        )
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource("tmdb", "simkl", MediaType.TvShow, StudioRole.PRODUCER)
        )
        assertEquals(
            StudioSourceSupport.TMDB_NETWORK_SOURCE,
            StudioSourceSupport.resolveClickSource(StudioSourceSupport.TMDB_NETWORK_SOURCE, "tmdb", MediaType.TvShow)
        )
        assertEquals(
            "tmdb_network",
            StudioSourceSupport.resolveClickSource(null, "anilist", MediaType.TvShow, StudioRole.NETWORK)
        )
        assertEquals(
            "bangumi",
            StudioSourceSupport.resolveClickSource(null, "bangumi", MediaType.Anime, StudioRole.NETWORK)
        )
    }

    @Test
    fun providerFallbackIsUsedOnlyWhenStudioHasNoNamespace() {
        assertEquals(
            "jikan",
            StudioSourceSupport.resolveClickSource(null, "MyAnimeList", MediaType.Anime)
        )
        assertEquals(
            "anilist",
            StudioSourceSupport.resolveClickSource("", "AniList", MediaType.Manga)
        )
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource(null, "unknown", MediaType.Movie)
        )
        // Simkl's direct details contain no company IDs; its studio chips are enriched from TMDB.
        assertEquals(
            "tmdb",
            StudioSourceSupport.resolveClickSource(null, "simkl", MediaType.TvShow, StudioRole.PRODUCER)
        )
    }

    @Test
    fun identityGuardAcceptsLegalSuffixesButRejectsDifferentCompanies() {
        assertTrue(StudioSourceSupport.namesMatch("BONES", "BONES, Inc."))
        assertTrue(StudioSourceSupport.namesMatch("Kyoto Animation Co., Ltd.", "Kyoto Animation"))
        assertFalse(StudioSourceSupport.namesMatch("Aniplex", "BONES"))
    }
}

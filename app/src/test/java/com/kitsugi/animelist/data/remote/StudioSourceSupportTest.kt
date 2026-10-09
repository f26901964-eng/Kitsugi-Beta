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
    }

    @Test
    fun identityGuardAcceptsLegalSuffixesButRejectsDifferentCompanies() {
        assertTrue(StudioSourceSupport.namesMatch("BONES", "BONES, Inc."))
        assertTrue(StudioSourceSupport.namesMatch("Kyoto Animation Co., Ltd.", "Kyoto Animation"))
        assertFalse(StudioSourceSupport.namesMatch("Aniplex", "BONES"))
    }
}

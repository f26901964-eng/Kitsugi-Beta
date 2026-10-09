package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaGalleryIdentityTest {
    private fun result(
        id: Int,
        source: String,
        realMalId: Int? = null,
        tmdbId: Int? = null,
        type: MediaType = MediaType.Anime
    ) = JikanSearchResult(
        malId = id,
        title = "Title",
        subtitle = "",
        type = type,
        total = null,
        score = null,
        isAdult = false,
        imageUrl = null,
        year = null,
        source = source,
        realMalId = realMalId,
        tmdbId = tmdbId
    )

    @Test
    fun simklNeverTreatsItsOwnIdAsMalOrTmdb() {
        val identity = resolveMediaGalleryIdentity(result(id = 82_001, source = "simkl"), detail = null)

        assertNull(identity.malId)
        assertNull(identity.tmdbId)
    }

    @Test
    fun simklCarriesItsResolvedMalAndTmdbIdsIntoGallery() {
        val identity = resolveMediaGalleryIdentity(
            result(id = 82_001, source = "simkl", realMalId = 1_234, tmdbId = 5_678),
            detail = null
        )

        assertEquals(1_234, identity.malId)
        assertEquals(5_678, identity.tmdbId)
    }

    @Test
    fun bangumiUsesOnlyResolvedCrossIdsNotItsStableId() {
        val cross = KitsugiBangumiDetailClient.CrossIds(
            malId = 123,
            aniListId = 456,
            tmdbId = 789,
            kitsuId = 987
        )
        val identity = resolveMediaGalleryIdentity(
            result(id = 500_000_321, source = "bangumi"),
            detail = null,
            bangumiCross = cross
        )

        assertEquals(123, identity.malId)
        assertEquals(456, identity.aniListId)
        assertEquals(789, identity.tmdbId)
        assertEquals(987, identity.kitsuId)
    }

    @Test
    fun anilistAndKitsuOffsetsDecodeOnlyIntoTheirOwnNamespaces() {
        val aniList = resolveMediaGalleryIdentity(
            result(id = 100_000_456, source = "anilist"),
            detail = null
        )
        val kitsu = resolveMediaGalleryIdentity(
            result(id = 300_000_789, source = "kitsu"),
            detail = null
        )

        assertEquals(456, aniList.aniListId)
        assertNull(aniList.malId)
        assertEquals(789, kitsu.kitsuId)
        assertNull(kitsu.malId)
    }

    @Test
    fun tmdbSourceUsesItsPrimaryIdAsTmdbOnly() {
        val identity = resolveMediaGalleryIdentity(result(id = 999, source = "tmdb"), detail = null)

        assertEquals(999, identity.tmdbId)
        assertNull(identity.malId)
    }
}

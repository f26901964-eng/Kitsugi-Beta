package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrossSyncEntryFactoryTest {
    private fun entry(
        source: String,
        malId: Int?,
        type: MediaType = MediaType.Anime,
        aniListEntryId: Int? = null,
        malListId: Long? = null,
        simklId: Int? = null,
        tmdbId: Int? = null
    ) = MediaEntry(
        id = 1,
        title = "Example",
        type = type,
        status = WatchStatus.Watching,
        score = 8,
        progress = 3,
        total = 12,
        source = source,
        malId = malId,
        aniListEntryId = aniListEntryId,
        malListId = malListId,
        simklId = simklId,
        tmdbId = tmdbId
    )

    @Test
    fun newCrossTargetKeepsOnlyVerifiedMalIdentity() {
        val source = entry(
            source = "shikimori",
            malId = 42_000,
            aniListEntryId = 11,
            malListId = 12L,
            simklId = 13,
            tmdbId = 14
        )

        val target = CrossSyncEntryFactory.forNewTarget(source, verifiedMalId = 99_001)

        assertEquals("mal", target.source)
        assertEquals(99_001, MediaIdentity.realMalId(target))
        assertNull(target.aniListEntryId)
        assertNull(target.malListId)
        assertNull(target.simklId)
        assertNull(target.tmdbId)
        assertNull(MediaIdentity.shikimoriId(target))
    }

    @Test
    fun unresolvedCrossTargetDoesNotLeakAnyProviderIdentity() {
        val source = entry(
            source = "simkl",
            malId = null,
            aniListEntryId = 11,
            malListId = 12L,
            simklId = 13,
            tmdbId = 14
        )

        val target = CrossSyncEntryFactory.forNewTarget(source, verifiedMalId = null)

        assertEquals("manual", target.source)
        assertNull(target.malId)
        assertNull(target.aniListEntryId)
        assertNull(target.malListId)
        assertNull(target.simklId)
        assertNull(target.tmdbId)
    }

    @Test
    fun updatesRetainDestinationNamespaceAndItsOwnIds() {
        val destination = entry(
            source = "bangumi",
            malId = 500_035_658,
            type = MediaType.Movie,
            aniListEntryId = 21,
            malListId = 22L,
            simklId = 23,
            tmdbId = 24
        )
        val merged = entry(source = "mal", malId = 99_001, type = MediaType.Movie)

        val update = CrossSyncEntryFactory.forExistingTarget(merged, destination)

        assertEquals("bangumi", update.source)
        assertEquals(destination.malId, update.malId)
        assertEquals(destination.type, update.type)
        assertEquals(destination.aniListEntryId, update.aniListEntryId)
        assertEquals(destination.malListId, update.malListId)
        assertEquals(destination.simklId, update.simklId)
        assertEquals(destination.tmdbId, update.tmdbId)
    }
}

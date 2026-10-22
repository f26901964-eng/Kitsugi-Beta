package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossSyncMergePolicyTest {

    @Test
    fun newerDroppedDecisionBeatsStaleShikimoriWatchingRow() {
        val staleShikimori = entry(
            source = "shikimori",
            status = WatchStatus.Watching,
            progress = 0,
            updatedAt = 100
        )
        val newestMal = entry(
            source = "mal",
            status = WatchStatus.Dropped,
            progress = 4,
            updatedAt = 200
        )
        val candidates = listOf(newestMal, staleShikimori)

        val merged = CrossSyncMergePolicy.selectStatus(
            candidates = candidates,
            newest = candidates.maxByOrNull { it.updatedAt }!!,
            totalCount = 12,
            maxProgress = 4
        )

        assertEquals(WatchStatus.Dropped, merged)
    }

    @Test
    fun newerWatchingDecisionAlsoBeatsStaleDroppedRow() {
        val staleDropped = entry(source = "mal", status = WatchStatus.Dropped, updatedAt = 100)
        val newestAniList = entry(source = "anilist", status = WatchStatus.Watching, updatedAt = 200)
        val candidates = listOf(newestAniList, staleDropped)

        val merged = CrossSyncMergePolicy.selectStatus(
            candidates = candidates,
            newest = candidates.maxByOrNull { it.updatedAt }!!,
            totalCount = 12,
            maxProgress = 4
        )

        assertEquals(WatchStatus.Watching, merged)
    }

    @Test
    fun completedProgressSafeguardIsPreserved() {
        val completed = entry(
            source = "mal",
            status = WatchStatus.Completed,
            progress = 12,
            updatedAt = 100
        )
        val newest = entry(
            source = "shikimori",
            status = WatchStatus.Watching,
            progress = 12,
            updatedAt = 200
        )
        val candidates = listOf(completed, newest)

        val merged = CrossSyncMergePolicy.selectStatus(
            candidates = candidates,
            newest = newest,
            totalCount = 12,
            maxProgress = 12
        )

        assertEquals(WatchStatus.Completed, merged)
    }

    private fun entry(
        source: String,
        status: WatchStatus,
        progress: Int = 4,
        updatedAt: Long
    ) = MediaEntry(
        id = 1,
        title = "Zom 100",
        type = MediaType.Anime,
        status = status,
        score = null,
        progress = progress,
        total = 12,
        source = source,
        malId = 54_112,
        updatedAt = updatedAt
    )
}

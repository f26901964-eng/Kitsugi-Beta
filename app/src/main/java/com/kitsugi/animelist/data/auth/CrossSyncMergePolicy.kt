package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus

/** Pure field-merge decisions shared by the full cross-account synchronization pass. */
internal object CrossSyncMergePolicy {

    /**
     * Keeps the existing completion safeguards, but otherwise respects the newest user's
     * decision. The old status ranking made any stale `Watching` row permanently override a
     * newer `Dropped`/`Paused` row on every other provider; Shikimori could therefore never
     * converge even after its identity had been matched correctly.
     */
    fun selectStatus(
        candidates: List<MediaEntry>,
        newest: MediaEntry,
        totalCount: Int?,
        maxProgress: Int
    ): WatchStatus = when {
        totalCount != null && maxProgress >= totalCount -> WatchStatus.Completed
        candidates.any { it.status == WatchStatus.Completed } &&
            (totalCount == null || maxProgress >= totalCount) -> WatchStatus.Completed
        else -> newest.status
    }
}

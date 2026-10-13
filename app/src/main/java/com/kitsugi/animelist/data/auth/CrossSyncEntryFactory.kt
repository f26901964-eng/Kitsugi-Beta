package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry

/** Builds provider-scoped entries for cross-account writes without leaking source/destination IDs. */
internal object CrossSyncEntryFactory {
    /**
     * A new target row may carry only the MAL ID that passed the catalog identity gate.
     * Provider-native IDs are either looked up and verified by the target manager or the write is skipped.
     */
    fun forNewTarget(entry: MediaEntry, verifiedMalId: Int?): MediaEntry = entry.copy(
        source = if (verifiedMalId != null) "mal" else "manual",
        malId = verifiedMalId,
        aniListEntryId = null,
        malListId = null,
        simklId = null,
        tmdbId = null
    )

    /** Existing rows keep the destination's own provider namespace and IDs for safe updates. */
    fun forExistingTarget(merged: MediaEntry, target: MediaEntry): MediaEntry = merged.copy(
        source = target.source,
        malId = target.malId,
        type = target.type,
        aniListEntryId = target.aniListEntryId,
        malListId = target.malListId,
        simklId = target.simklId,
        tmdbId = target.tmdbId
    )
}

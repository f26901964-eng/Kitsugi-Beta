package com.kitsugi.animelist.core.player

import com.kitsugi.animelist.data.remote.SkipInterval

/**
 * Pure policy helpers for AniSkip integration.
 *
 * - Whether AniSkip should be disabled when the video already contains chapters
 *   (Aniyomi: "Disable AniSkip if video already contains chapters").
 * - Merging of AniSkip and AnimeSkip intervals (AnimeSkip wins per type).
 */
object AniSkipPolicy {
    /**
     * AniSkip should be skipped entirely when the user opted to disable it on
     * chaptered videos and the video actually has chapters.
     */
    fun shouldDisableOnChapters(hasChapters: Boolean, disableOnChapters: Boolean): Boolean =
        disableOnChapters && hasChapters

    /**
     * Merge AniSkip intervals with AnimeSkip intervals. AnimeSkip takes priority
     * for a given segment type; AniSkip fills the gaps.
     */
    fun mergeIntervals(aniList: List<SkipInterval>, animeList: List<SkipInterval>): List<SkipInterval> {
        val merged = mutableListOf<SkipInterval>()
        merged.addAll(animeList)
        val covered = animeList.map { it.type }.toSet()
        aniList.filterNot { it.type in covered }.forEach { merged.add(it) }
        return merged
    }
}

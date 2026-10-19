package com.kitsugi.animelist.core.player

/**
 * Pure policy helpers for AniSkip integration (SDK-independent).
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
    fun shouldDisableOnChapters(hasChapters: Boolean, disableOnChaptersSetting: Boolean): Boolean =
        disableOnChaptersSetting && hasChapters

    /**
     * Merge AniSkip intervals with AnimeSkip intervals. AnimeSkip takes priority
     * for a given segment type; AniSkip fills the gaps.
     */
    fun <T> mergeIntervals(
        aniList: List<T>,
        animeList: List<T>,
        typeOf: (T) -> String
    ): List<T> {
        val merged = mutableListOf<T>()
        merged.addAll(animeList)
        val covered = animeList.map(typeOf).toSet()
        aniList.filterNot { typeOf(it) in covered }.forEach { merged.add(it) }
        return merged
    }
}

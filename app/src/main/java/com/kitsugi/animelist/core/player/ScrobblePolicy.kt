package com.kitsugi.animelist.core.player

/**
 * Pure policy helpers for watch-progress scrobbling.
 *
 * Aniyomi uses a configurable threshold (default 85%); Kitsugi historically
 * hardcoded 80%. Centralising the decision keeps the controller and any future
 * tests on the same rule.
 */
object ScrobblePolicy {
    const val DEFAULT_THRESHOLD: Float = 0.85f

    /**
     * Whether the current progress justifies marking the episode as watched.
     *
     * @param progress 0..1 fraction of the episode consumed.
     * @param threshold fraction at which scrobbling triggers (clamped to 0..1).
     */
    fun shouldScrobble(progress: Float, threshold: Float = DEFAULT_THRESHOLD): Boolean {
        if (progress.isNaN() || progress.isInfinite()) return false
        val t = threshold.coerceIn(0f, 1f)
        return progress >= t
    }
}

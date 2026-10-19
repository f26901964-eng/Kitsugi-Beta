package com.kitsugi.animelist.core.player

/**
 * Pure policy for customizable player double-tap gestures, OSD hide timeout,
 * panel opacity, and Netflix-style intro skip countdown (Aniyomi parity).
 */
enum class DoubleTapGestureAction {
    SEEK,
    PLAY_PAUSE,
    CUSTOM,
    SWITCH_SUB,
    NONE;

    companion object {
        fun fromString(raw: String?, default: DoubleTapGestureAction): DoubleTapGestureAction {
            if (raw.isNullOrBlank()) return default
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: default
        }
    }
}

object PlayerUiPolicy {
    const val DEFAULT_OSD_HIDE_DELAY_MS = 5_000L
    const val MIN_OSD_HIDE_DELAY_MS = 1_000L
    const val MAX_OSD_HIDE_DELAY_MS = 30_000L

    const val DEFAULT_PANEL_OPACITY = 95
    const val MIN_PANEL_OPACITY = 40
    const val MAX_PANEL_OPACITY = 100

    fun clampOsdHideDelayMs(ms: Int): Long =
        ms.toLong().coerceIn(MIN_OSD_HIDE_DELAY_MS, MAX_OSD_HIDE_DELAY_MS)

    fun panelAlpha(opacityPercent: Int): Float =
        (opacityPercent.coerceIn(MIN_PANEL_OPACITY, MAX_PANEL_OPACITY) / 100f)

    /**
     * Computes remaining seconds for Netflix-style intro skip countdown.
     * Returns null when countdown is not active, or 0 when immediate skip should fire.
     */
    fun computeSkipCountdownRemainingSec(
        autoSkipEnabled: Boolean,
        netflixStyleEnabled: Boolean,
        waitingTimeSec: Int,
        elapsedInIntervalSec: Int
    ): Int? {
        if (!autoSkipEnabled) return null
        if (!netflixStyleEnabled) return 0
        val wait = waitingTimeSec.coerceIn(1, 30)
        return (wait - elapsedInIntervalSec.coerceAtLeast(0)).coerceAtLeast(0)
    }
}

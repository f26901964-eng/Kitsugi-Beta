package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType

/** A network/format stall must not leave the compact player black at 00:00 forever. */
internal const val INLINE_PLAYER_START_TIMEOUT_MS = 18_000L

internal fun PlayerEngineType.alternateInlineEngine(): PlayerEngineType = when (this) {
    PlayerEngineType.MEDIA3 -> PlayerEngineType.MPV
    PlayerEngineType.MPV, PlayerEngineType.EXTERNAL -> PlayerEngineType.MEDIA3
}

/**
 * YouTube's extracted, signed Googlevideo streams use the Media3-specific request/data-source
 * policy. Keep Media3 as the inline first choice for these URLs (matching fullscreen playback),
 * while preserving the user's engine preference for ordinary direct OP/ED files.
 */
internal fun initialInlineEngineType(
    configuredPreference: String?,
    isYouTubeSource: Boolean
): PlayerEngineType = when {
    isYouTubeSource -> PlayerEngineType.MEDIA3
    configuredPreference.equals("MPV", ignoreCase = true) -> PlayerEngineType.MPV
    else -> PlayerEngineType.MEDIA3
}

/** READY only means prepared; it does not prove that inline playback actually advanced. */
internal fun hasInlinePlaybackProgress(state: PlayerEngine.State, positionMs: Long): Boolean =
    state == PlayerEngine.State.ENDED || positionMs > 0L

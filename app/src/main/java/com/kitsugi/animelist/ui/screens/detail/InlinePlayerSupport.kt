package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType

/** A network/format stall must not leave the compact player black at 00:00 forever. */
internal const val INLINE_PLAYER_START_TIMEOUT_MS = 18_000L

internal fun PlayerEngineType.alternateInlineEngine(): PlayerEngineType = when (this) {
    PlayerEngineType.MEDIA3 -> PlayerEngineType.MPV
    PlayerEngineType.MPV, PlayerEngineType.EXTERNAL -> PlayerEngineType.MEDIA3
}

internal fun PlayerEngine.State.isInlinePlaybackReady(): Boolean =
    this == PlayerEngine.State.READY || this == PlayerEngine.State.ENDED

package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlinePlayerSupportTest {
    @Test
    fun playerEnginesAlternateInBothDirections() {
        assertEquals(PlayerEngineType.MPV, PlayerEngineType.MEDIA3.alternateInlineEngine())
        assertEquals(PlayerEngineType.MEDIA3, PlayerEngineType.MPV.alternateInlineEngine())
    }

    @Test
    fun readyAndEndedStatesTerminateInlineStartupWait() {
        assertTrue(PlayerEngine.State.READY.isInlinePlaybackReady())
        assertTrue(PlayerEngine.State.ENDED.isInlinePlaybackReady())
        assertFalse(PlayerEngine.State.IDLE.isInlinePlaybackReady())
        assertFalse(PlayerEngine.State.BUFFERING.isInlinePlaybackReady())
    }
}

package com.kitsugi.animelist.core.player.engine

import org.junit.Assert.*
import org.junit.Test

class MpvPlaybackPolicyTest {
    @Test fun normalVolumeAndMuteArePreserved() {
        assertEquals(0f, MpvPlaybackPolicy.volume(0f, 200), 0f)
        assertEquals(0.35f, MpvPlaybackPolicy.volume(0.35f, 200), 0f)
        assertEquals(1f, MpvPlaybackPolicy.volume(1f, 200), 0f)
    }

    @Test fun boostIsLimitedToConfiguredCap() {
        assertEquals(1.5f, MpvPlaybackPolicy.volume(2f, 150), 0f)
        assertEquals(2f, MpvPlaybackPolicy.volume(4f, 999), 0f)
        assertEquals(1f, MpvPlaybackPolicy.volume(2f, 0), 0f)
        assertEquals(0f, MpvPlaybackPolicy.volume(-1f, 200), 0f)
    }

    @Test fun invalidVolumeDoesNotReachJni() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(1f, MpvPlaybackPolicy.volume(it, 200), 0f)
        }
    }

    @Test fun cpuDebandingUsesGradfun() {
        assertEquals("gradfun=radius=12", MpvPlaybackPolicy.videoFilters("cpu", false))
    }

    @Test fun yuvConversionDoesNotReplaceCpuDebanding() {
        assertEquals("gradfun=radius=12,format=yuv420p", MpvPlaybackPolicy.videoFilters("cpu", true))
    }

    @Test fun disablingOptionsClearsOldFilterChain() {
        assertEquals("", MpvPlaybackPolicy.videoFilters("none", false))
        assertEquals("", MpvPlaybackPolicy.videoFilters("gpu", false))
        assertEquals("format=yuv420p", MpvPlaybackPolicy.videoFilters("gpu", true))
    }

    @Test fun readyRequiresAFileAndNoPendingSeekOrBuffering() {
        for (loaded in listOf(false, true))
            for (cached in listOf(false, true))
                for (idle in listOf(false, true))
                    for (playing in listOf(false, true))
                        for (seeking in listOf(false, true)) {
                            val expected = loaded && !cached && !seeking && (!idle || !playing)
                            assertEquals(expected, MpvPlaybackPolicy.isReady(loaded, cached, idle, playing, seeking))
                        }
    }

    @Test fun scriptObserversCoverLeafCommandsOnly() {
        val properties = MpvPlaybackPolicy.scriptProperties
        assertEquals(14, properties.size)
        assertEquals(properties.size, properties.distinct().size)
        assertFalse(properties.contains("user-data/aniyomi"))
        listOf("pause", "show_text", "seek_by", "seek_to", "seek_by_with_text", "seek_to_with_text",
            "launch_int_picker").forEach {
            assertTrue(properties.contains("user-data/aniyomi/$it"))
        }
    }
}

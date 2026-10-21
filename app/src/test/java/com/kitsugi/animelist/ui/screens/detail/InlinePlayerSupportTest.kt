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
    fun youtubeInlinePlaybackUsesMedia3BeforeMpvRegardlessOfGeneralPreference() {
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType("MPV", isYouTubeSource = true))
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType("MEDIA3", isYouTubeSource = true))
    }

    @Test
    fun directInlineVideosKeepConfiguredEnginePreference() {
        assertEquals(PlayerEngineType.MPV, initialInlineEngineType("MPV", isYouTubeSource = false))
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType("MEDIA3", isYouTubeSource = false))
        assertEquals(PlayerEngineType.MEDIA3, initialInlineEngineType(null, isYouTubeSource = false))
    }

    @Test
    fun compactThemePlaybackStartsWithMedia3ForReliableComposeRendering() {
        // Fragman (Media3 + TextureView) aynı kartta çalışırken OP/ED (MPV + SurfaceView)
        // siyah kalıyordu; kompakt tema oynatıcısı artık her zaman Media3 ile başlar.
        assertEquals(PlayerEngineType.MEDIA3, initialCompactThemeEngineType())
    }

    @Test
    fun nonWebmCompactThemeStartsOnMedia3WithoutTouchingDeviceCodecs() {
        // `.mp4`/`.m4v` kaynaklarda VP9 ön kontrolü yapılmaz: her cihazda Media3 ile
        // başlanır, gerekiyorsa bekçi MPV'ye geçer.
        assertEquals(
            PlayerEngineType.MEDIA3,
            initialCompactThemeEngineType("https://cdn.example/opening-1.mp4")
        )
        assertEquals(PlayerEngineType.MEDIA3, initialCompactThemeEngineType(""))
    }

    @Test
    fun selectedButUnrenderedVideoIsStillAudioOnlyAfterGrace() {
        // hasVisibleVideo = hasVideoTrack && hasRenderedVideoFrame olduğu için, video
        // parçası seçili olsa da hiç kare çizilmediyse bekçi ses-only hükmü verir.
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS - 1L,
                state = PlayerEngine.State.READY,
                positionMs = 4_000L,
                hasVisibleVideo = false
            )
        )
        assertTrue(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS,
                state = PlayerEngine.State.READY,
                positionMs = 4_000L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun preparedButStalledPlayerDoesNotCountAsStarted() {
        assertFalse(hasInlinePlaybackProgress(PlayerEngine.State.READY, 0L))
        assertFalse(hasInlinePlaybackProgress(PlayerEngine.State.BUFFERING, 0L))
        assertTrue(hasInlinePlaybackProgress(PlayerEngine.State.READY, 1L))
        assertTrue(hasInlinePlaybackProgress(PlayerEngine.State.ENDED, 0L))
    }

    @Test
    fun audioOnlyPlaybackIsNotDetectedDuringTheGracePeriod() {
        // Video parçası henüz seçilmemiş olabilir: ses başladı diye hemen motor değişmemeli.
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS - 1_000L,
                state = PlayerEngine.State.READY,
                positionMs = 3_500L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun audioOnlyPlaybackIsDetectedWhenTimelineAdvancesWithoutVideo() {
        assertTrue(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS,
                state = PlayerEngine.State.READY,
                positionMs = 6_200L,
                hasVisibleVideo = false
            )
        )
    }

    @Test
    fun healthyVideoPlaybackNeverTriggersTheAudioOnlyGuard() {
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS * 3,
                state = PlayerEngine.State.READY,
                positionMs = 20_000L,
                hasVisibleVideo = true
            )
        )
        // Ses ilerlemiyorsa (hâlâ 0 ms) tanı konmaz; bu ayrı bir bekleme durumudur.
        assertFalse(
            audioOnlyPlaybackDetected(
                elapsedMs = INLINE_AUDIO_ONLY_GRACE_MS * 3,
                state = PlayerEngine.State.BUFFERING,
                positionMs = 0L,
                hasVisibleVideo = false
            )
        )
    }
}

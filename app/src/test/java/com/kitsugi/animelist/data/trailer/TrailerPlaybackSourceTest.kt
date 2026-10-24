package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPlaybackSourceTest {
    @Test
    fun candidatesAreFlatDistinctAndNeverBlank() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://cdn/primary",
            alternatives = listOf(
                TrailerPlaybackSource(videoUrl = "https://cdn/alternate"),
                TrailerPlaybackSource(videoUrl = "https://cdn/primary"),
                TrailerPlaybackSource(videoUrl = " ")
            )
        )

        val candidates = source.playbackCandidates()

        assertEquals(listOf("https://cdn/primary", "https://cdn/alternate"), candidates.map { it.videoUrl })
        assertTrue(candidates.all { it.alternatives.isEmpty() })
    }

    @Test
    fun expiryIncludesAlternativeAudioAndVideoUrls() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://cdn/video?expire=1900000300",
            audioUrl = "https://cdn/audio?expire=1900000400",
            alternatives = listOf(
                TrailerPlaybackSource(
                    videoUrl = "https://cdn/alternate?expire=1900000500",
                    audioUrl = "https://cdn/alternate-audio?expire=1900000200"
                )
            )
        )

        assertEquals(1_900_000_200L, source.earliestYouTubeExpiryEpochSeconds())
    }

    @Test
    fun orderedListBecomesOnePrimaryWithAllOtherCandidatesAsAlternatives() {
        val sources = listOf(
            TrailerPlaybackSource(videoUrl = "https://cdn/1"),
            TrailerPlaybackSource(videoUrl = "https://cdn/2"),
            TrailerPlaybackSource(videoUrl = "https://cdn/3")
        )

        val primary = sources.asPrimaryWithAlternatives()!!

        assertEquals("https://cdn/1", primary.videoUrl)
        assertEquals(listOf("https://cdn/2", "https://cdn/3"), primary.alternatives.map { it.videoUrl })
        assertEquals(3, primary.playbackCandidates().size)
    }
}

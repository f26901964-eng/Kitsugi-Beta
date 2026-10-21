package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRequestPolicyTest {
    @Test
    fun recognizesYouTubeAndGoogleVideoHostsWithoutMatchingLookalikes() {
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://www.youtube.com/watch?v=abcdefghijk"))
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://manifest.googlevideo.com/api/manifest/hls_playlist/x"))
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback"))
        assertTrue(YouTubeRequestPolicy.isYouTubeMediaUrl("https://youtu.be/abcdefghijk"))
        assertFalse(YouTubeRequestPolicy.isYouTubeMediaUrl("https://googlevideo.com.attacker.example/video"))
        assertFalse(YouTubeRequestPolicy.isYouTubeMediaUrl("https://v.animethemes.moe/AttackOnTitan-OP1.webm"))
    }

    @Test
    fun hlsManifestNeverGetsQueryRangeChunkingButProgressiveGoogleVideoDoes() {
        val manifest = "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/id/abc"
        val progressive = "https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback?expire=1"

        assertFalse(
            YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = manifest,
                audioUrl = null,
                isCloudstream = false,
                isHls = true
            )
        )
        assertTrue(
            YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = progressive,
                audioUrl = null,
                isCloudstream = false,
                isHls = false
            )
        )
        assertFalse(
            YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = progressive,
                audioUrl = null,
                isCloudstream = true,
                isHls = false
            )
        )
    }

    @Test
    fun youtubeHeadersAreConsistentAndThemeFilesGetTheirOwnOrigin() {
        val youtube = YouTubeRequestPolicy.headersForInlineUrl(
            "https://rr2---sn-a5mekn7e.googlevideo.com/videoplayback"
        )
        assertEquals(YouTubeRequestPolicy.STREAM_USER_AGENT, youtube["User-Agent"])
        assertEquals(YouTubeRequestPolicy.YOUTUBE_REFERER, youtube["Referer"])
        assertEquals("https://www.youtube.com", youtube["Origin"])

        val theme = YouTubeRequestPolicy.headersForInlineUrl(
            "https://v.animethemes.moe/AttackOnTitan-OP1.webm"
        )
        assertEquals("https://v.animethemes.moe/", theme["Referer"])
        assertEquals("https://v.animethemes.moe", theme["Origin"])
        assertEquals(YouTubeRequestPolicy.STREAM_USER_AGENT, theme["User-Agent"])
    }
}

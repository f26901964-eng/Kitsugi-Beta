package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiAnimeThemesClientTest {

    @Test
    fun noCandidateYieldsNoLink() {
        assertNull(KitsugiAnimeThemesClient.pickBestThemeVideoLink(emptyList()))
        assertNull(
            KitsugiAnimeThemesClient.pickBestThemeVideoLink(
                listOf(candidate(link = "  "))
            )
        )
    }

    @Test
    fun cleanVideoWinsOverBurnedInSubtitles() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-subbed.webm", isSubbed = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1.webm", isSubbed = false, resolution = 480)
            )
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun mp4IsPreferredBecauseMedia3DecodesH264Everywhere() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1.mp4", isNc = true, resolution = 720)
            )
        )
        assertEquals("https://a.animethemes.moe/op1.mp4", link)
    }

    @Test
    fun creditlessWebVersionBeatsHeavierBluRayRemux() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-bd.webm", isNc = false, resolution = 1080, source = "BD"),
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 720, source = "WEB")
            )
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun resolutionAbove1080IsRankedBehindPlayableSizes() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-4k.webm", isNc = true, resolution = 2160, source = "WEB"),
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 720, source = "WEB")
            )
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun compactPreviewPrefers720pOverHeavier1080pWithinTheSameSource() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-480.webm", isNc = true, resolution = 480),
                candidate(link = "https://a.animethemes.moe/op1-1080.webm", isNc = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1-720.webm", isNc = true, resolution = 720)
            )
        )
        assertEquals("https://a.animethemes.moe/op1-720.webm", link)
    }

    @Test
    fun anySupportedResolutionBeatsAnUnsupported4kRendition() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-4k.webm", resolution = 2160),
                candidate(link = "https://a.animethemes.moe/op1-360.webm", resolution = 360)
            )
        )
        assertEquals("https://a.animethemes.moe/op1-360.webm", link)
    }

    @Test
    fun smallerFileWinsWhenResolutionAndOtherPropertiesMatch() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-large.webm", sizeBytes = 60_000_000),
                candidate(link = "https://a.animethemes.moe/op1-small.webm", sizeBytes = 20_000_000)
            )
        )
        assertEquals("https://a.animethemes.moe/op1-small.webm", link)
    }

    @Test
    fun single1080pThemeRemainsPlayableWhenNoLighterRenditionExists() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(candidate(link = "https://a.animethemes.moe/op1.webm", resolution = 1080, sizeBytes = 62_128_452))
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun mp4DetectionIgnoresQueryParameters() {
        assertTrue("https://a.animethemes.moe/op1.mp4?token=1".isMp4Link())
        assertTrue("https://a.animethemes.moe/op1.MP4".isMp4Link())
        assertTrue(!"https://a.animethemes.moe/op1.webm".isMp4Link())
    }

    private fun candidate(
        link: String,
        resolution: Int = 720,
        isSubbed: Boolean = false,
        isNc: Boolean = true,
        source: String = "WEB",
        sizeBytes: Long = 0L
    ) = ThemeVideoCandidate(
        link = link,
        resolution = resolution,
        isSubbed = isSubbed,
        isNc = isNc,
        source = source,
        sizeBytes = sizeBytes
    )
}

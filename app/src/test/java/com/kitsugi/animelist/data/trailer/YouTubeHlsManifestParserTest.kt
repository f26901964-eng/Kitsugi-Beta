package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeHlsManifestParserTest {
    @Test
    fun masterPlaylistSelectsHighestResolutionAndResolvesRelativeUrl() {
        val parsed = YouTubeHlsManifestParser.parse(
            manifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_playlist/master.m3u8?sig=abc",
            body = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
                /api/manifest/hls_playlist/variant/360.m3u8?itag=18
                #EXT-X-STREAM-INF:BANDWIDTH=3000000,AVERAGE-BANDWIDTH=2500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
                /api/manifest/hls_playlist/variant/1080.m3u8?itag=37
            """.trimIndent()
        )

        val result = requireNotNull(parsed)
        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
        assertEquals(2_500_000L, result.bandwidth)
        assertEquals(
            "https://manifest.googlevideo.com/api/manifest/hls_playlist/variant/1080.m3u8?itag=37",
            result.selectedVariantUrl
        )
    }

    @Test
    fun equalHeightAndBandwidthVariantsUseStoredWidthAsTieBreaker() {
        val parsed = YouTubeHlsManifestParser.parse(
            manifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_playlist/master",
            body = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
                variant-1280.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1920x720
                variant-1920.m3u8
            """.trimIndent()
        )

        val result = requireNotNull(parsed)
        assertEquals(1920, result.width)
        assertEquals(720, result.height)
        assertEquals(
            "https://manifest.googlevideo.com/api/manifest/hls_playlist/variant-1920.m3u8",
            result.selectedVariantUrl
        )
    }

    @Test
    fun mediaPlaylistWithoutMasterVariantsIsStillAccepted() {
        val parsed = YouTubeHlsManifestParser.parse(
            manifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_playlist/media?id=abc",
            body = "\uFEFF" + """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXT-X-MEDIA-SEQUENCE:0
                #EXTINF:6.0,
                https://rr1---sn-a5mekn7e.googlevideo.com/segment/0.ts
                #EXT-X-ENDLIST
            """.trimIndent()
        )

        val result = requireNotNull(parsed)
        assertNull(result.selectedVariantUrl)
        assertEquals(0, result.height)
    }

    @Test
    fun nonPlaylistResponseIsRejected() {
        assertNull(
            YouTubeHlsManifestParser.parse(
                "https://manifest.googlevideo.com/api/manifest/hls_playlist/x",
                "<html>Access denied</html>"
            )
        )
    }
}

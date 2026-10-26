package com.kitsugi.animelist.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GolgeBahcesiImageHeadersTest {

    @Test
    fun `applies referer to golgebahcesi and skycdn hosts only`() {
        assertTrue(GolgeBahcesiImageHeaders.appliesReferer("golgebahcesi.com"))
        assertTrue(GolgeBahcesiImageHeaders.appliesReferer("www.golgebahcesi.com"))
        assertTrue(GolgeBahcesiImageHeaders.appliesReferer("api.golgebahcesi.com"))
        assertTrue(GolgeBahcesiImageHeaders.appliesReferer("c2.skycdn.online"))
        assertTrue(GolgeBahcesiImageHeaders.appliesReferer("skycdn.online"))

        assertFalse(GolgeBahcesiImageHeaders.appliesReferer("cdn.myanimelist.net"))
        assertFalse(GolgeBahcesiImageHeaders.appliesReferer("evil-golgebahcesi.com"))
        assertFalse(GolgeBahcesiImageHeaders.appliesReferer(null))
    }

    @Test
    fun `applies image accept to image proxy and skycdn but never api json host`() {
        assertTrue(
            GolgeBahcesiImageHeaders.appliesImageAccept(
                "golgebahcesi.com",
                "/image-proxy/series-thumbnail/1793012622_0f5bc861039b75449d899403a0fb1c0c.webp"
            )
        )
        assertTrue(
            GolgeBahcesiImageHeaders.appliesImageAccept(
                "c2.skycdn.online",
                "/series/player-s7gyl3nz/68a60eb85bc1f28064351da3/1.jpg"
            )
        )
        assertFalse(
            GolgeBahcesiImageHeaders.appliesImageAccept(
                "api.golgebahcesi.com",
                "/api/series"
            )
        )
        assertFalse(GolgeBahcesiImageHeaders.ACCEPT.contains("avif"))
        assertEquals("https://golgebahcesi.com/", GolgeBahcesiImageHeaders.REFERER)
    }
}

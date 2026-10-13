package com.kitsugi.animelist.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomImageHeadersTest {

    @Test
    fun `applies only to fandom image hosts`() {
        assertTrue(FandomImageHeaders.applies("static.wikia.nocookie.net"))
        assertTrue(FandomImageHeaders.applies("vignette.wikia.nocookie.net"))
        assertTrue(FandomImageHeaders.applies("images.wikia.com"))
        assertFalse(FandomImageHeaders.applies("cdn.myanimelist.net"))
        assertFalse(FandomImageHeaders.applies("image.tmdb.org"))
        assertFalse(FandomImageHeaders.applies(null))
    }

    @Test
    fun `referer is fandom and accept does not request avif`() {
        assertEquals("https://www.fandom.com/", FandomImageHeaders.REFERER)
        assertFalse(FandomImageHeaders.ACCEPT.contains("avif"))
        assertTrue(FandomImageHeaders.ACCEPT.contains("image/webp"))
    }
}

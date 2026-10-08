package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShikimoriAdultContentTest {
    @Test
    fun onlyRxAndHentaiAreMarkedAdult() {
        assertTrue(isShikimoriAdultContent("rx"))
        assertTrue(isShikimoriAdultContent("Rx - Hentai"))
        assertTrue(isShikimoriAdultContent(null, listOf("Comedy", "Hentai")))

        assertFalse(isShikimoriAdultContent("r"))
        assertFalse(isShikimoriAdultContent("r_plus"))
        assertFalse(isShikimoriAdultContent("pg_13"))
        assertFalse(isShikimoriAdultContent(null, listOf("Romance", "Comedy")))
    }
}

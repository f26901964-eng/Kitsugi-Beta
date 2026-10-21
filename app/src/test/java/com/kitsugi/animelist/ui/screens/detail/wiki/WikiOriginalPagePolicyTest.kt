package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiOriginalPagePolicyTest {
    @Test
    fun wikiDomainsIncludeLanguageAndLegacyHostsButNotLookalikes() {
        listOf("https://you-zitsu.fandom.com/wiki/Home", "https://onepiece.fandom.com/tr/wiki/A",
            "https://naruto.wikia.com/wiki/A", "https://tr.m.wikipedia.org/wiki/Anime")
            .forEach { assertEquals(it, WikiOriginalPagePolicy.Destination.WIKI, WikiOriginalPagePolicy.destination(it)) }
        listOf("https://fandom.com.evil.test/", "https://evilfandom.com/", "https://example.com/")
            .forEach { assertEquals(it, WikiOriginalPagePolicy.Destination.EXTERNAL, WikiOriginalPagePolicy.destination(it)) }
    }

    @Test
    fun nonHttpsAndAmbiguousDestinationsAreBlocked() {
        listOf("javascript:alert(1)", "file:///sdcard/test", "intent://wiki/", "content://contacts/",
            "data:text/html,hi", "http://you-zitsu.fandom.com/", "https://user@fandom.com/",
            "https://fandom.com:8080/", "https:///wiki/Home", "//fandom.com/", "")
            .forEach { assertEquals(it, WikiOriginalPagePolicy.Destination.BLOCKED, WikiOriginalPagePolicy.destination(it)) }
    }

    @Test
    fun desktopUserAgentPreservesEngineVersionAndIsIdempotent() {
        val original = "Mozilla/5.0 (Linux; Android 15; Pixel 8; wv) AppleWebKit/537.36 Version/4.0 Chrome/140.0.7339.123 Mobile Safari/537.36"
        val desktop = WikiOriginalPagePolicy.desktopUserAgent(original)
        assertTrue(desktop.contains("Chrome/140.0.7339.123"))
        assertTrue(desktop.contains("(X11; Linux x86_64)"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("Version/4.0"))
        assertFalse(desktop.contains("; wv"))
        assertEquals(desktop, WikiOriginalPagePolicy.desktopUserAgent(desktop))
    }
}

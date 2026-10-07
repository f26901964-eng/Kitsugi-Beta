package com.kitsugi.animelist.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class MalImportManagerUrlTest {
    @Test
    fun animeListUrlEncodesNestedFieldsAndSupportsAdultContentSetting() {
        assertValidListUrl(
            url = MalImportManager.buildAnimeListUrl(showAdultContent = false),
            path = "/v2/users/@me/animelist",
            adultContent = false,
            progressField = "num_episodes_watched"
        )
    }

    @Test
    fun mangaListUrlEncodesNestedFieldsAndSupportsAdultContentSetting() {
        assertValidListUrl(
            url = MalImportManager.buildMangaListUrl(showAdultContent = true),
            path = "/v2/users/@me/mangalist",
            adultContent = true,
            progressField = "num_chapters_read"
        )
    }

    private fun assertValidListUrl(
        url: String,
        path: String,
        adultContent: Boolean,
        progressField: String
    ) {
        // This was the failing point during cross-account sync: raw `{` / `}` in the query
        // caused java.net.URI to throw "Illegal character in query" before the HTTP request.
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("api.myanimelist.net", uri.host)
        assertEquals(path, uri.rawPath)
        assertFalse(url.contains('{'))
        assertFalse(url.contains('}'))

        val httpUrl = url.toHttpUrl()
        val fields = requireNotNull(httpUrl.queryParameter("fields"))
        assertTrue(fields.contains("list_status{"))
        assertTrue(fields.contains(progressField))
        assertTrue(fields.endsWith("}"))
        assertEquals("100", httpUrl.queryParameter("limit"))
        assertEquals(adultContent.toString(), httpUrl.queryParameter("nsfw"))
    }
}

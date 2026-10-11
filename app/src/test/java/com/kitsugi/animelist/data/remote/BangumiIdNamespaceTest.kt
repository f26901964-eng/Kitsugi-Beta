package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiIdNamespaceTest {
    @Test
    fun `Bangumi subject types and platform map to supported Kitsugi types`() {
        assertEquals(MediaType.Anime, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.ANIME, "TV"))
        assertEquals(MediaType.Movie, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.ANIME, "剧场版"))
        assertEquals(MediaType.Manga, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.BOOK, "漫画"))
        assertEquals(MediaType.TvShow, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.REAL, "电视剧"))
        assertEquals(MediaType.Movie, BangumiIdNamespace.mediaTypeFor(BangumiApiClient.SubjectType.REAL, "电影"))
    }

    @Test
    fun `unsupported subject types are not treated as anime`() {
        assertFalse(BangumiIdNamespace.supportsSubjectType(BangumiApiClient.SubjectType.GAME))
        assertFalse(BangumiIdNamespace.supportsSubjectType(BangumiApiClient.SubjectType.MUSIC))
        assertFalse(BangumiIdNamespace.supportsSubjectType(0))
    }

    @Test
    fun `media type maps back to the correct Bangumi subject type`() {
        assertEquals(BangumiApiClient.SubjectType.ANIME, BangumiIdNamespace.subjectTypeFor(MediaType.Anime))
        assertEquals(BangumiApiClient.SubjectType.BOOK, BangumiIdNamespace.subjectTypeFor(MediaType.Manga))
        assertEquals(BangumiApiClient.SubjectType.REAL, BangumiIdNamespace.subjectTypeFor(MediaType.Movie))
        assertEquals(BangumiApiClient.SubjectType.REAL, BangumiIdNamespace.subjectTypeFor(MediaType.TvShow))
    }

    @Test
    fun `movie platform mapping is explicit`() {
        assertTrue(BangumiIdNamespace.isMoviePlatform("电影"))
        assertTrue(BangumiIdNamespace.isMoviePlatform("劇場版"))
        assertFalse(BangumiIdNamespace.isMoviePlatform("电视剧"))
    }
}

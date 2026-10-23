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

    @Test
    fun `selectUniqueSubjectMatch normalizes NFKC fullwidth digits and matches infobox aliases`() {
        val kaijuSubject = BangumiApiClient.BangumiSubject(
            id = 400_602,
            type = BangumiApiClient.SubjectType.ANIME,
            name = "怪獣8号",
            nameCn = "怪兽8号",
            summary = "",
            date = "2024-04-13",
            platform = "TV",
            eps = 12,
            totalEpisodes = 12,
            volumes = 0,
            score = 7.4,
            rank = 0,
            ratingCount = 1000,
            ratingBreakdown = emptyMap(),
            collectionCounts = emptyMap(),
            imageCommon = null,
            imageMedium = null,
            imageLarge = null,
            tags = emptyList(),
            infobox = listOf("别名" to "Kaiju No. 8", "罗马字" to "Kaijuu 8-gou"),
            nsfw = false
        )

        // Fullwidth `８` from AniList `title.native = "怪獣８号"`
        assertEquals(
            400_602,
            BangumiIdNamespace.selectUniqueSubjectMatch(listOf(kaijuSubject), "怪獣８号", expectedYear = 2024)
        )
        // Infobox English alias
        assertEquals(
            400_602,
            BangumiIdNamespace.selectUniqueSubjectMatch(listOf(kaijuSubject), "Kaiju No. 8", expectedYear = 2024)
        )
    }

    @Test
    fun `selectUniqueSubjectMatch disambiguates homonyms using expectedYear`() {
        val hxh1999 = BangumiApiClient.BangumiSubject(
            id = 1704,
            type = BangumiApiClient.SubjectType.ANIME,
            name = "HUNTER×HUNTER",
            nameCn = "全职猎人",
            summary = "",
            date = "1999-10-16",
            platform = "TV",
            eps = 62,
            totalEpisodes = 62,
            volumes = 0,
            score = 8.6,
            rank = 0,
            ratingCount = 1000,
            ratingBreakdown = emptyMap(),
            collectionCounts = emptyMap(),
            imageCommon = null,
            imageMedium = null,
            imageLarge = null,
            tags = emptyList(),
            infobox = emptyList(),
            nsfw = false
        )
        val hxh2011 = hxh1999.copy(id = 10357, date = "2011-10-02", eps = 148, totalEpisodes = 148)

        assertEquals(
            10357,
            BangumiIdNamespace.selectUniqueSubjectMatch(listOf(hxh1999, hxh2011), "HUNTER×HUNTER", expectedYear = 2011)
        )
        assertEquals(
            1704,
            BangumiIdNamespace.selectUniqueSubjectMatch(listOf(hxh1999, hxh2011), "HUNTER×HUNTER", expectedYear = 1999)
        )
        // Without expectedYear, two distinct homonyms remain safely ambiguous (null)
        assertEquals(
            null,
            BangumiIdNamespace.selectUniqueSubjectMatch(listOf(hxh1999, hxh2011), "HUNTER×HUNTER", expectedYear = null)
        )
    }
}

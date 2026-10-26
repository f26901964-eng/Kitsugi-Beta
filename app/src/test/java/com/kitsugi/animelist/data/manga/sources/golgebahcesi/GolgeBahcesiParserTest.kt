package com.kitsugi.animelist.data.manga.sources.golgebahcesi

import com.kitsugi.animelist.data.manga.MangaStatus
import com.kitsugi.animelist.data.manga.sources.golgebahcesi.GolgeBahcesiParser.toMangaChapter
import com.kitsugi.animelist.data.manga.sources.golgebahcesi.GolgeBahcesiParser.toMangaDetails
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GolgeBahcesiParserTest {

    @Test
    fun `parses series list json with relative cover image alternative titles and zero artist`() {
        val rawJson = """
            {
              "data": [
                {
                  "id": "69aef05a9a7d5c6272852256",
                  "title": "Gölge Köle",
                  "slug": "golge-kole",
                  "alternativeTitles": ["Shadow Slave", "Gölge Köle"],
                  "description": "Kabus büyüsünün seçtiği genç.",
                  "coverImage": "/image-proxy/series-thumbnail/1793012622_0f5bc861039b75449d899403a0fb1c0c.webp",
                  "status": "ongoing",
                  "type": "webtoon",
                  "author": "Guiltythree",
                  "artist": "0",
                  "genres": ["Aksiyon", "Fantastik"]
                }
              ],
              "pagination": {
                "currentPage": 1,
                "itemsPerPage": 20,
                "totalItems": 45,
                "totalPages": 3
              }
            }
        """.trimIndent()

        val parsed = GolgeBahcesiParser.json.decodeFromString(SeriesListResponseDto.serializer(), rawJson)
        assertEquals(1, parsed.data.size)
        assertTrue(parsed.pagination.currentPage < parsed.pagination.totalPages)

        val details = parsed.data.first().toMangaDetails("https://golgebahcesi.com", "Gölge Bahçesi")
        assertEquals("/manga/golge-kole", details.url)
        assertEquals("Gölge Köle", details.title)
        assertEquals(listOf("Shadow Slave"), details.alternativeTitles)
        assertEquals(
            "https://golgebahcesi.com/image-proxy/series-thumbnail/1793012622_0f5bc861039b75449d899403a0fb1c0c.webp",
            details.thumbnailUrl
        )
        assertEquals("Guiltythree", details.author)
        assertNull(details.artist)
        assertEquals(MangaStatus.Ongoing, details.status)
        assertEquals(listOf("Webtoon", "Aksiyon", "Fantastik"), details.genre)
    }

    @Test
    fun `normalizes placeholder chapter titles and filters locked chapters`() {
        val rawChapters = """
            [
              {
                "id": "lock1",
                "seriesSlug": "oyuncu",
                "number": 265,
                "title": "Bölüm 265",
                "slug": "chapter-265",
                "publishedAt": "2026-10-20T18:32:11.456Z",
                "isLocked": true,
                "deliverySystem": "secure"
              },
              {
                "id": "6a57707b580054d5fe0db9da",
                "seriesSlug": "oyuncu",
                "number": 264,
                "title": "Manga Oku",
                "slug": "bolum-264",
                "publishedAt": "2026-10-18T12:00:00Z",
                "isLocked": false,
                "deliverySystem": "legacy"
              },
              {
                "id": "half1",
                "seriesSlug": "oyuncu",
                "number": 14.5,
                "title": "",
                "slug": "chapter-14-5",
                "publishedAt": "2026-10-10T09:15:00+03:00",
                "isLocked": false,
                "deliverySystem": "secure"
              }
            ]
        """.trimIndent()

        val dtos = GolgeBahcesiParser.json.decodeFromString(
            ListSerializer(ChapterDto.serializer()),
            rawChapters
        )
        val unlocked = dtos.filterNot { it.isLocked }.map { it.toMangaChapter("oyuncu") }
        assertEquals(2, unlocked.size)

        val ch264 = unlocked[0]
        assertEquals("Bölüm 264", ch264.name)
        assertEquals(264f, ch264.chapterNumber, 0.001f)
        assertEquals("/manga/oyuncu/bolum/bolum-264#6a57707b580054d5fe0db9da", ch264.url)
        assertTrue(ch264.uploadDate > 1_700_000_000_000L)

        val chHalf = unlocked[1]
        assertEquals("Bölüm 14.5", chHalf.name)
        assertEquals(14.5f, chHalf.chapterNumber, 0.001f)
        assertTrue(chHalf.uploadDate > 1_700_000_000_000L)
    }

    @Test
    fun `parses chapter url with or without fragment and full domain`() {
        val withId = GolgeBahcesiParser.parseChapterUrl(
            "/manga/golge-kole/bolum/chapter-14#6adbfb66754ddd5445d518ce"
        )
        assertNotNull(withId)
        assertEquals("golge-kole", withId!!.seriesSlug)
        assertEquals("chapter-14", withId.chapterSlug)
        assertEquals("6adbfb66754ddd5445d518ce", withId.chapterId)

        val fullWithoutId = GolgeBahcesiParser.parseChapterUrl(
            "https://golgebahcesi.com/manga/mutlak-buyucu/bolum/bolum-88/"
        )
        assertNotNull(fullWithoutId)
        assertEquals("mutlak-buyucu", fullWithoutId!!.seriesSlug)
        assertEquals("bolum-88", fullWithoutId.chapterSlug)
        assertNull(fullWithoutId.chapterId)
    }

    @Test
    fun `roundtrips secure page urls and detects secure vs legacy chapters`() {
        val secureUrl = GolgeBahcesiParser.buildSecurePageUrl(
            chapterId = "6adbfb66754ddd5445d518ce",
            seriesSlug = "golge-kole",
            chapterSlug = "chapter-14",
            pageIndex = 42,
        )
        val parsed = GolgeBahcesiParser.parseSecurePageUrl(secureUrl)
        assertNotNull(parsed)
        assertEquals("6adbfb66754ddd5445d518ce", parsed!!.chapterId)
        assertEquals("golge-kole", parsed.seriesSlug)
        assertEquals("chapter-14", parsed.chapterSlug)
        assertEquals(42, parsed.pageIndex)

        assertNull(GolgeBahcesiParser.parseSecurePageUrl("https://c2.skycdn.online/series/player/1.jpg"))

        val legacyChapter = ChapterDto(
            id = "1",
            deliverySystem = "legacy",
            pages = listOf(PageDto(0, "https://c2.skycdn.online/series/player/1.jpg")),
        )
        val secureChapter = ChapterDto(
            id = "2",
            deliverySystem = "secure",
            imageEnc = true,
            pages = listOf(PageDto(0, "/series/golge-kole/tok/hash.png.enc")),
        )
        assertFalse(GolgeBahcesiParser.isSecureChapter(legacyChapter))
        assertTrue(GolgeBahcesiParser.isSecureChapter(secureChapter))
    }

    @Test
    fun `builds document start script with worker hook priority queue and turnstile detector`() {
        val bridge = GolgeBahcesiReaderScripts.randomJsIdentifier()
        val channel = GolgeBahcesiReaderScripts.randomJsIdentifier()
        val script = GolgeBahcesiReaderScripts.buildDocumentStartScript(bridge, channel)

        assertTrue(script.contains(bridge))
        assertTrue(script.contains(channel))
        assertTrue(script.contains("BroadcastChannel"))
        assertTrue(script.contains("createImageBitmap"))
        assertTrue(script.contains("IntersectionObserver"))
        assertTrue(script.contains("need_interaction"))
        assertTrue(script.contains("data-reader-page"))
    }
}

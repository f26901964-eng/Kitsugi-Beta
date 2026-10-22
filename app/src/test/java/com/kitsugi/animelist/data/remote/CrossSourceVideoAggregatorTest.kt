package com.kitsugi.animelist.data.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSourceVideoAggregatorTest {

    @Test
    fun parseJikanVideosJson_extractsPromosAndMusicVideos() {
        val sample = """
        {
          "data": {
            "promo": [
              {
                "title": "PV 1",
                "trailer": {
                  "youtube_id": "Iwr1aLEDpe4",
                  "url": "https://www.youtube.com/watch?v=Iwr1aLEDpe4",
                  "images": { "maximum_image_url": "https://img.youtube.com/vi/Iwr1aLEDpe4/maxresdefault.jpg" }
                }
              },
              {
                "title": "PV 2",
                "trailer": {
                  "youtube_id": "ZEkwCGJ3o7M",
                  "url": "https://www.youtube.com/watch?v=ZEkwCGJ3o7M"
                }
              }
            ],
            "music_videos": [
              {
                "title": "Opening 1",
                "video": {
                  "youtube_id": "OIBODIPC_8Y",
                  "url": "https://www.youtube.com/watch?v=OIBODIPC_8Y"
                },
                "meta": {
                  "title": "Yuusha",
                  "author": "YOASOBI"
                }
              }
            ]
          }
        }
        """.trimIndent()

        val videos = KitsugiCrossSourceVideoAggregator.parseJikanVideosJson(sample)
        assertEquals(3, videos.size)
        assertEquals("PV 1", videos[0].title)
        assertEquals(KitsugiVideoCategory.TRAILER, videos[0].category)
        assertEquals("mal", videos[0].source)
        assertEquals("\"Yuusha\" (Opening 1)", videos[2].title)
        assertEquals("YOASOBI", videos[2].subtitle)
    }

    @Test
    fun parseTmdbVideosArray_prioritizesTurkishAndCategorizesTypes() {
        val arr = JSONArray(
            """
            [
              { "key": "engKey12345", "site": "YouTube", "type": "Trailer", "name": "Official English Trailer", "iso_639_1": "en", "official": true },
              { "key": "turKey12345", "site": "YouTube", "type": "Trailer", "name": "Resmi Türkçe Fragman", "iso_639_1": "tr", "official": true },
              { "key": "btsKey12345", "site": "YouTube", "type": "Behind the Scenes", "name": "Studio Featurette", "iso_639_1": "en", "official": false }
            ]
            """.trimIndent()
        )
        val parsed = KitsugiCrossSourceVideoAggregator.parseTmdbVideosArray(arr, preferTurkish = true)
        assertEquals(3, parsed.size)
        assertEquals("Resmi Türkçe Fragman", parsed[0].title)
        assertEquals(KitsugiVideoCategory.TRAILER, parsed[0].category)
        assertEquals(KitsugiVideoCategory.FEATURETTE, parsed[2].category)
    }

    @Test
    fun mergeCrossSourceVideos_deduplicatesSameYouTubeIdAndPreservesAllSources() {
        val items = listOf(
            KitsugiVideoItem(
                id = "1",
                title = "Official Trailer",
                videoUrl = "https://www.youtube.com/watch?v=Iwr1aLEDpe4",
                category = KitsugiVideoCategory.TRAILER,
                source = "anilist"
            ),
            KitsugiVideoItem(
                id = "2",
                title = "PV 1 — Frieren: Beyond Journey's End",
                videoUrl = "https://youtu.be/Iwr1aLEDpe4",
                category = KitsugiVideoCategory.TRAILER,
                source = "mal"
            ),
            KitsugiVideoItem(
                id = "3",
                title = "Official Trailer",
                videoUrl = "https://www.youtube.com/embed/Iwr1aLEDpe4",
                category = KitsugiVideoCategory.TRAILER,
                source = "simkl"
            ),
            KitsugiVideoItem(
                id = "4",
                title = "\"Yuusha\" by YOASOBI (OP1)",
                videoUrl = "https://v.animethemes.moe/SousouNoFrieren-OP1.mp4",
                category = KitsugiVideoCategory.OPENING,
                source = "animethemes"
            ),
            KitsugiVideoItem(
                id = "5",
                title = "\"Yuusha\" by YOASOBI",
                videoUrl = null,
                category = KitsugiVideoCategory.OPENING,
                source = "mal"
            )
        )

        val merged = KitsugiCrossSourceVideoAggregator.mergeCrossSourceVideos(items)
        assertEquals(2, merged.size)

        val trailer = merged.first { it.category == KitsugiVideoCategory.TRAILER }
        assertEquals("PV 1 — Frieren: Beyond Journey's End", trailer.title)
        assertEquals(listOf("anilist", "mal", "simkl"), trailer.allSources)

        val op = merged.first { it.category == KitsugiVideoCategory.OPENING }
        assertEquals("https://v.animethemes.moe/SousouNoFrieren-OP1.mp4", op.videoUrl)
        assertTrue(op.allSources.containsAll(listOf("animethemes", "mal")))
    }

    @Test
    fun extractSimklTrailers_parsesYoutubeIdsAndBuildsThumbnailUrls() {
        val raw = JSONObject(
            """
            {
              "trailers": [
                { "name": "Official Trailer 1", "youtube": "Iwr1aLEDpe4", "size": 1080 }
              ]
            }
            """.trimIndent()
        )
        val extracted = KitsugiSimklDetailClient.extractSimklTrailers(raw)
        assertEquals(1, extracted.size)
        assertEquals("https://www.youtube.com/watch?v=Iwr1aLEDpe4", extracted[0].videoUrl)
        assertEquals("simkl", extracted[0].source)
        assertEquals("1080p", extracted[0].subtitle)
    }
}

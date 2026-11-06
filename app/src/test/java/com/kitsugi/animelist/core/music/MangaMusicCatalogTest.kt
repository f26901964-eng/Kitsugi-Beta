package com.kitsugi.animelist.core.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Okuma müziği çekirdeğinin ağ gerektirmeyen kuralları.
 *
 * Bu testler "hiç ses çalmıyor" sınıfındaki hataların en sık kaynağını kilitler:
 * oynatıcıya HTML/`.m3u8`/`http://` adresi verilmesi. Böyle bir adres ExoPlayer'ı
 * süresiz "buffering" durumunda bırakıyor ve kullanıcı hiçbir ses duymuyordu.
 */
class MangaMusicCatalogTest {

    @Test
    fun onlyHttpsDirectStreamsArePlayable() {
        assertTrue(MangaMusicCatalog.isPlayableStreamUrl("https://ice1.somafm.com/groovesalad-128-mp3"))
        assertTrue(MangaMusicCatalog.isPlayableStreamUrl("https://stream.example.com/live?token=1"))

        assertFalse("cleartext akış engellenir", MangaMusicCatalog.isPlayableStreamUrl("http://stream.example.com/live.mp3"))
        assertFalse("HLS canlı radyo panelde takılıyordu", MangaMusicCatalog.isPlayableStreamUrl("https://x.example.com/live.m3u8"))
        assertFalse(MangaMusicCatalog.isPlayableStreamUrl("https://x.example.com/listen.pls"))
        assertFalse(MangaMusicCatalog.isPlayableStreamUrl("https://x.example.com/page.html"))
        assertFalse(MangaMusicCatalog.isPlayableStreamUrl(""))
    }

    @Test
    fun parserDropsBrokenEntriesAndDuplicates() {
        val json = """
            [
              {"stationuuid":"a","name":"Groove Salad","url_resolved":"https://ice1.somafm.com/groovesalad-128-mp3","codec":"MP3","bitrate":128,"countrycode":"US","hls":0},
              {"stationuuid":"a","name":"Groove Salad","url_resolved":"https://ice1.somafm.com/groovesalad-128-mp3","codec":"MP3","bitrate":128,"countrycode":"US","hls":0},
              {"stationuuid":"b","name":"HLS Station","url_resolved":"https://x.example.com/live","codec":"AAC","hls":1},
              {"stationuuid":"c","name":"","url_resolved":"https://x.example.com/live.mp3","codec":"MP3","hls":0},
              {"stationuuid":"d","name":"Insecure","url_resolved":"http://x.example.com/live.mp3","codec":"MP3","hls":0},
              {"stationuuid":"e","name":"Video Codec","url_resolved":"https://x.example.com/live.ts","codec":"MP2T","hls":0},
              {"stationuuid":"f","name":"Lofi Girl Radio","url_resolved":"https://x.example.com/lofi.mp3","codec":"MP3","bitrate":192,"countrycode":"TR","hls":0}
            ]
        """.trimIndent()

        val stations = MangaMusicCatalog.parseStations(json, MangaMusicMood.LO_FI.tag)

        assertEquals("yalnızca iki geçerli istasyon kalmalı", 2, stations.size)
        assertEquals(listOf("Groove Salad", "Lofi Girl Radio"), stations.map { it.name })
        assertTrue(stations.all { it.streamUrl.startsWith("https://") })
        assertTrue(stations.all { it.mood == MangaMusicMood.LO_FI.tag })
        assertEquals("Radio Browser", stations.first().providerLabel)
    }

    @Test
    fun parserSurvivesGarbagePayload() {
        assertTrue(MangaMusicCatalog.parseStations("not-json", "lofi").isEmpty())
        assertTrue(MangaMusicCatalog.parseStations("{}", "lofi").isEmpty())
        assertTrue(MangaMusicCatalog.parseStations("[]", "lofi").isEmpty())
    }

    @Test
    fun everyMoodHasPlayableBuiltInFallback() {
        MangaMusicMood.entries.forEach { mood ->
            val stations = MangaMusicCatalog.builtInFor(mood.tag)
            assertTrue("${mood.tag} için yedek istasyon yok", stations.isNotEmpty())
            assertTrue(
                "${mood.tag} yedeğinde oynatılamaz adres var",
                stations.all { MangaMusicCatalog.isPlayableStreamUrl(it.streamUrl) }
            )
        }
    }

    @Test
    fun unknownMoodFallsBackInsteadOfReturningEmpty() {
        assertTrue(MangaMusicCatalog.builtInFor("bilinmeyen-etiket").isNotEmpty())
        assertEquals(MangaMusicMood.LO_FI, MangaMusicMood.fromTag("bilinmeyen-etiket"))
        assertEquals(MangaMusicSource.BUILT_IN, MangaMusicSource.fromStorage("built_in"))
        assertEquals(MangaMusicSource.RADIO_BROWSER, MangaMusicSource.fromStorage(null))
    }

    @Test
    fun lastStationIsRebuiltFromPersistedSettings() {
        val settings = MangaMusicSettings(
            enabled = true,
            stationId = "somafm-groove-salad",
            stationName = "Groove Salad",
            stationUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
            mood = MangaMusicMood.LO_FI.tag
        )
        val station = settings.lastStationOrNull()
        assertNotNull(station)
        assertEquals("Groove Salad", station?.name)
        assertEquals("https://ice1.somafm.com/groovesalad-128-mp3", station?.streamUrl)

        // Kayıtlı adres yoksa istasyon uydurulmaz.
        assertEquals(null, MangaMusicSettings(stationUrl = " ").lastStationOrNull())
    }
}

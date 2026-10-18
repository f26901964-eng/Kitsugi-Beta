package com.kitsugi.animelist.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.4.258 regresyon testi: FilmMakinesi / HDFilmCehennemi (CloseLoad, RapidVid, rapidrame vb.)
 * üzerindeki DÜZ MP4 dosyaları HLS olarak işaretlenmemeli; aksi halde ExoPlayer
 * application/x-mpegURL MIME ile MP4'ü çözemez ve oynatma başarısız olur.
 */
class PlayerHlsDetectionTest {

    @Test
    fun progressiveMp4_onKnownHosts_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://srv.closeload.top/hash/video.mp4?t=123"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/abc/film.mp4"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://cdn.hdfilmcehennemi.nl/x/movie.mkv"))
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://filmmakinesi.to/files/film.webm"))
    }

    @Test
    fun hlsManifests_onKnownHosts_stayHls() {
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://closeload.top/hls/abc123/video.mp4/master.txt"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/x/index.m3u8?token=1"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://cdn.example.com/stream/master.m3u8"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://vidmixi.com/list/abc"))
        assertTrue(PlayerMediaSourceFactory.isHlsUrl("https://rapidrame.com/x/playlist.txt"))
    }

    @Test
    fun progressiveWithQuery_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl("https://cdn.example.com/video.mp4?sig=abc"))
    }

    @Test
    fun blankUrl_isNotHls() {
        assertFalse(PlayerMediaSourceFactory.isHlsUrl(""))
    }
}

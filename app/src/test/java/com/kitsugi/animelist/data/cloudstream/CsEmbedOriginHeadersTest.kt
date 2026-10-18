package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-18 kök neden regresyon testleri — "HDFilmCehennemi / FilmMakinesi oynatmıyor".
 *
 * İki ayrı kök neden vardı:
 *
 *  KN-A (eklenti tarafı): eklentilerin `linkTypeFor()` fonksiyonu uzantısız adreste
 *      `INFER_TYPE!!` kullanıyordu. CloudStream kütüphanesinde
 *      `val INFER_TYPE: ExtractorLinkType? = null` olduğu için bu dal HER ZAMAN
 *      NullPointerException fırlatıyor, NPE eklentinin try/catch'inde yutuluyor ve
 *      `callback` hiç çağrılmıyordu → uygulama 0 kaynak görüyordu.
 *      Düzeltme eklenti deposunda (HDFilmCehennemi 54 / FilmMakinesi 64).
 *
 *  KN-B (uygulama tarafı): eklenti çökünce devreye geren derin tarama / son-çare
 *      yolları aday adresi PROVIDER origin'i ile doğruluyordu. Türkçe CDN'ler
 *      (playmix.uno, closeload, rapidrame) isteği EMBED sayfasının origin'ine göre
 *      doğruladığı için çalışan aday "ölü" sayılıyordu (eklentinin kendi kaydı:
 *      "eksik kalınca uygulama onu playmix.uno yapıyor ve yayın 5004 ile düşüyordu").
 *      Düzeltme: [CsStreamRunner.originOfUrl] + [CsStreamRunner.applyEmbedOriginHeaders].
 *
 * Bu dosya KN-B'yi kilitler.
 */
class CsEmbedOriginHeadersTest {

    // ─── originOfUrl ─────────────────────────────────────────────────────────

    @Test
    fun originOfUrl_stripsPathQueryAndFragment() {
        assertEquals(
            "https://closeload.filmmakinesi.to",
            CsStreamRunner.originOfUrl("https://closeload.filmmakinesi.to/film/1234?x=1#frag")
        )
        assertEquals(
            "https://www.hdfilmcehennemi.nl",
            CsStreamRunner.originOfUrl("https://www.hdfilmcehennemi.nl/video/98/")
        )
    }

    @Test
    fun originOfUrl_keepsNonStandardPort() {
        assertEquals(
            "https://cdn.example.com:8443",
            CsStreamRunner.originOfUrl("https://cdn.example.com:8443/hls/a/master.m3u8")
        )
    }

    @Test
    fun originOfUrl_dropsDefaultPorts() {
        assertEquals("https://a.example", CsStreamRunner.originOfUrl("https://a.example:443/x"))
        assertEquals("http://a.example", CsStreamRunner.originOfUrl("http://a.example:80/x"))
    }

    @Test
    fun originOfUrl_returnsNull_forUnusableInput() {
        assertNull(CsStreamRunner.originOfUrl(null))
        assertNull(CsStreamRunner.originOfUrl(""))
        assertNull(CsStreamRunner.originOfUrl("   "))
        assertNull(CsStreamRunner.originOfUrl("not a url"))
        assertNull(CsStreamRunner.originOfUrl("https://"))
    }

    // ─── applyEmbedOriginHeaders ─────────────────────────────────────────────

    @Test
    fun applyEmbedOriginHeaders_fillsMissingOriginAndReferer() {
        val headers = CsStreamRunner.applyEmbedOriginHeaders(
            mutableMapOf(),
            "https://closeload.filmmakinesi.to/film/1234"
        )
        assertEquals("https://closeload.filmmakinesi.to", headers["Origin"])
        assertEquals("https://closeload.filmmakinesi.to/", headers["Referer"])
    }

    @Test
    fun applyEmbedOriginHeaders_neverOverridesCallerValues() {
        // Eklentinin kendi verdiği Referer/Origin korunmalı: CDN token'ı buna bağlı.
        val headers = CsStreamRunner.applyEmbedOriginHeaders(
            mutableMapOf(
                "Referer" to "https://filmmakinesi.to/",
                "Origin" to "https://filmmakinesi.to"
            ),
            "https://closeload.filmmakinesi.to/film/1234"
        )
        assertEquals("https://filmmakinesi.to/", headers["Referer"])
        assertEquals("https://filmmakinesi.to", headers["Origin"])
    }

    @Test
    fun applyEmbedOriginHeaders_headerNamesAreCaseInsensitive() {
        val headers = CsStreamRunner.applyEmbedOriginHeaders(
            mutableMapOf("referer" to "https://provider.example/"),
            "https://playmix.uno/embed/abc"
        )
        // Küçük harfli referer zaten var → ikinci bir Referer eklenmemeli.
        assertEquals(1, headers.keys.count { it.equals("referer", ignoreCase = true) })
        assertEquals("https://provider.example/", headers["referer"])
        // Origin eksikti → embed origin'i ile doldurulmalı.
        assertEquals("https://playmix.uno", headers["Origin"])
    }

    @Test
    fun applyEmbedOriginHeaders_noOpWhenEmbedUrlUnusable() {
        val input = mutableMapOf("User-Agent" to "kitsugi")
        val headers = CsStreamRunner.applyEmbedOriginHeaders(input, null)
        assertFalse(headers.keys.any { it.equals("origin", ignoreCase = true) })
        assertFalse(headers.keys.any { it.equals("referer", ignoreCase = true) })
        assertEquals("kitsugi", headers["User-Agent"])
    }

    @Test
    fun applyEmbedOriginHeaders_worksForRealTurkishCdnEmbeds() {
        // HDFilmCehennemi (rapidrame) ve FilmMakinesi (playmix/closeload) gerçek şekilleri.
        val hdfc = CsStreamRunner.applyEmbedOriginHeaders(
            mutableMapOf(), "https://rapidrame.com/e/lozcj87hjrh6fuof"
        )
        assertEquals("https://rapidrame.com", hdfc["Origin"])
        assertEquals("https://rapidrame.com/", hdfc["Referer"])

        val fm = CsStreamRunner.applyEmbedOriginHeaders(
            mutableMapOf(), "https://closeload.filmmakinesi.to/film/62f1ab?sub=tr"
        )
        assertEquals("https://closeload.filmmakinesi.to", fm["Origin"])
        assertEquals("https://closeload.filmmakinesi.to/", fm["Referer"])
    }

    @Test
    fun originAndRefererAreConsistentForSameEmbedPage() {
        val embed = "https://playmix.uno/embed/abc123?token=x"
        val headers = CsStreamRunner.applyEmbedOriginHeaders(mutableMapOf(), embed)
        val origin = headers["Origin"]
        assertTrue("Referer, Origin + '/' olmalı", headers["Referer"] == "$origin/")
        assertTrue("Origin kendi hostunu içermeli", embed.startsWith(origin!!))
    }
}

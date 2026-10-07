package com.kitsugi.animelist.data.cloudstream.embed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * EmbedMediaScanner birim testleri — FP-41 derin embed çözümleme.
 *
 * Amaç: "eklenti kaynak döndürdü ama medya URL'si çıkarılamıyor" sınıfındaki hataların
 * (Türkçe eklentilerin çoğunda görülen "boş sonuç" şikâyeti) tekrar etmemesi.
 *
 * Çalıştırma:
 *   ./gradlew testDebugUnitTest --tests "*EmbedMediaScannerTest*"
 *
 * Not: Aynı senaryoların Python ikizi `scripts/verify_embed_scanner.py` içinde bulunur ve
 * derleme gerektirmeden `python3 scripts/verify_embed_scanner.py` ile çalıştırılabilir.
 * İki test seti BİRLİKTE güncellenmelidir.
 */
class EmbedMediaScannerTest {

    // ── 1) JWPlayer + token'lı HLS + reklam gürültüsü ─────────────────────────

    @Test
    fun `jwplayer setup icindeki tokenli m3u8 bulunur`() {
        val html = """
            <html><head><script src="/jwplayer.js"></script></head><body>
            <div id="player"></div><iframe src="//videoseyred.in/embed/abc123"></iframe>
            <script>
            jwplayer("player").setup({
              file: "https://cdn1.videoseyred.in/hls/abc123/1080/master.m3u8?token=eyJhbGciOi&expires=1730000000",
              image: "https://cdn1.videoseyred.in/img/poster.jpg",
              sources: [{ file: "https://cdn1.videoseyred.in/hls/abc123/720/index.m3u8" }]
            });
            </script></body></html>
        """.trimIndent()

        val result = EmbedMediaScanner.scan(html, "https://videoseyred.in/embed/abc123")
        val urls = result.media.map { it.url }

        assertTrue("m3u8 bulunmalı: $urls", urls.isNotEmpty())
        assertTrue(
            "token'lı master.m3u8 (1080) ilk sırada olmalı: $urls",
            urls.first().contains("1080/master.m3u8")
        )
        assertTrue(
            "poster görseli medya olarak eklenmemeli: $urls",
            urls.none { it.contains("poster.jpg") }
        )
        assertTrue("oynatıcı sayfası olarak tanınmalı", result.looksLikePlayer)
    }

    // ── 2) Dean Edwards "p.a.c.k.e.r" (gerçek çıktı) ───────────────────────────

    /**
     * Gövde GERÇEKTEN paketlenmiştir: `0("v").1({2:"3://4.5.6/7/8/9/a.b?c=d"});`
     * Sözlük kelimeleri yerine konunca URL ortaya çıkar. Eskiden çözücü "kelime → kelime"
     * değişimi yaptığı için bu sayfalardan hiçbir URL çıkarılamıyordu.
     */
    private val packedJs: String = """
        eval(function(p,a,c,k,e,d){e=function(c){return(c<a?'':e(parseInt(c/a)))+((c=c%a)>35?String.fromCharCode(c+29):c.toString(36))};while(c--){if(k[c]){p=p.replace(new RegExp('\b'+e(c)+'\b','g'),k[c])}}return p}('0(\'v\').1({2:\'3://4.5.6/7/8/9/a.b?c=d\'});',36,14,'jwplayer|setup|file|https|s1|molystream|org|hls|x9|720|index|m3u8|h|abc'.split('|')))
    """.trimIndent()

    @Test
    fun `packer token-kelime degisimi ile m3u8 cozulur`() {
        val unpacked = EmbedMediaScanner.unpackPackedJs(packedJs)
        assertNotNull("packer çözülmeli", unpacked)
        assertEquals(
            "token → kelime değişimi doğru olmalı",
            "jwplayer('v').setup({file:'https://s1.molystream.org/hls/x9/720/index.m3u8?h=abc'});",
            unpacked
        )

        val html = "<html><body><script>$packedJs</script></body></html>"
        val result = EmbedMediaScanner.scan(html, "https://player.molystream.org/e/1")
        assertEquals(
            "packer içindeki m3u8 bulunmalı",
            "https://s1.molystream.org/hls/x9/720/index.m3u8?h=abc",
            result.media.firstOrNull()?.url
        )
    }

    // ── 3) atob / base64 payload ──────────────────────────────────────────────

    @Test
    fun `atob icindeki base64 payload taranir`() {
        val inner = """{"sources":[{"file":"https://cdn3.alions.pro/stream/sd/abc/master.m3u8","label":"720p"}]}"""
        val b64 = Base64.getEncoder().encodeToString(inner.toByteArray(Charsets.UTF_8))
        val html = """<html><body><script>var data = atob("$b64");</script></body></html>"""

        val result = EmbedMediaScanner.scan(html, "https://alions.pro/embed/xyz")
        assertEquals(
            "base64 içindeki m3u8 bulunmalı",
            "https://cdn3.alions.pro/stream/sd/abc/master.m3u8",
            result.media.firstOrNull()?.url
        )
    }

    // ── 4) Kaçışlı (escaped) ve protokolsüz URL'ler ───────────────────────────

    @Test
    fun `kacisli ve protokolsuz urller mutlaklastirilir`() {
        val html = """
            <html><body><script>
            var src = "https:\/\/trstx.org\/hls\/live\/ch1\/index.m3u8?e=1&a=2";
            var other = '\/\/closeload.top\/vod\/hd\/film.mp4';
            </script></body></html>
        """.trimIndent()

        val result = EmbedMediaScanner.scan(html, "https://trstx.org/player.php?id=5")
        val urls = result.media.map { it.url }

        assertTrue(
            "kaçışlı (\\/) m3u8 çözülmeli: $urls",
            urls.contains("https://trstx.org/hls/live/ch1/index.m3u8?e=1&a=2")
        )
        assertTrue(
            "protokolsüz // URL https'e çevrilmeli: $urls",
            urls.contains("https://closeload.top/vod/hd/film.mp4")
        )
    }

    // ── 5) <video><source> + reklam filtresi ──────────────────────────────────

    @Test
    fun `video source bulunur reklam ve analitik elenir`() {
        val html = """
            <html><body>
            <video controls poster="/img/p.jpg"><source src="/videos/film-1080.mp4" type="video/mp4"></video>
            <script src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
            <img src="/banner/ads-300x250.png"><a href="/click?ad=1">reklam</a>
            <script>var _gaq='https://www.google-analytics.com/analytics.js';</script>
            </body></html>
        """.trimIndent()

        val result = EmbedMediaScanner.scan(html, "https://gstore.one/izle/film")
        assertEquals("yalnızca gerçek video kalmalı: ${result.media.map { it.url }}", 1, result.media.size)
        assertEquals("https://gstore.one/videos/film-1080.mp4", result.media.first().url)
    }

    // ── 6) iframe zinciri ─────────────────────────────────────────────────────

    @Test
    fun `iframe medya degil zincir icin ayri listeye girer`() {
        val html = """<html><body><iframe src="https://vidmoly.to/embed-x1y2z3.html" allowfullscreen></iframe></body></html>"""

        val result = EmbedMediaScanner.scan(html, "https://hdfilmcehennemi.nl/film/x")
        assertTrue("iframe medya olarak dönmemeli", result.media.isEmpty())
        assertEquals(listOf("https://vidmoly.to/embed-x1y2z3.html"), result.iframes)
    }

    // ── 7) data-* nitelikleri ─────────────────────────────────────────────────

    @Test
    fun `data-hls niteligi medya sayilir oynatici url sayilmaz`() {
        val html = """<html><body><div id="p" data-video="//playmogo.com/e/abc123" data-hls="/hls/abc/master.m3u8"></div></body></html>"""

        val result = EmbedMediaScanner.scan(html, "https://doodstream.com/e/abc123")
        assertEquals(
            listOf("https://doodstream.com/hls/abc/master.m3u8"),
            result.media.map { it.url }
        )
    }

    // ── 8) Reklam preroll vs. gerçek akış (skorlama) ──────────────────────────

    @Test
    fun `reklam preroll akisi gercek akistan sonra siralanir`() {
        val html = """
            <html><script>
            var pre = "https://ads.adserver.com/preroll/trailer.m3u8";
            jwplayer("v").setup({file:"https://cdn2.pichive.cc/vod/999/1080p.m3u8"});
            </script></html>
        """.trimIndent()

        val result = EmbedMediaScanner.scan(html, "https://pichive.cc/embed/999")
        val urls = result.media.map { it.url }

        assertTrue("gerçek akış bulunmalı: $urls", urls.contains("https://cdn2.pichive.cc/vod/999/1080p.m3u8"))
        assertEquals("reklam akışı ilk sıraya geçmemeli: $urls", "https://cdn2.pichive.cc/vod/999/1080p.m3u8", urls.first())
    }

    // ── 9) Yardımcı fonksiyonlar ──────────────────────────────────────────────

    @Test
    fun `medya olmayan urls reddedilir`() {
        assertFalse(EmbedMediaScanner.looksLikeMediaUrl("https://site.com/style.css"))
        assertFalse(EmbedMediaScanner.looksLikeMediaUrl("https://site.com/app.js?v=3"))
        assertFalse(EmbedMediaScanner.looksLikeMediaUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertFalse(EmbedMediaScanner.looksLikeMediaUrl("not-a-url"))
        assertTrue(EmbedMediaScanner.looksLikeMediaUrl("https://cdn/x/master.m3u8?a=1"))
        assertTrue(EmbedMediaScanner.looksLikeMediaUrl("https://cdn/x/film.mp4"))
        assertTrue(EmbedMediaScanner.looksLikeMediaUrl("https://cdn/x/hls/1080/index.m3u8"))
    }

    @Test
    fun `kacis cozumleme dogru calisir`() {
        assertEquals("https://a.com/x.m3u8", EmbedMediaScanner.decodeEscapes("https:\\/\\/a.com\\/x.m3u8"))
        assertEquals("https://a.com/x.m3u8?b=1&c=2", EmbedMediaScanner.decodeEscapes("https://a.com/x.m3u8?b\\u003d1&c=2"))
        assertEquals("https://a.com/x.m3u8", EmbedMediaScanner.decodeEscapes("https://a.com/x.m3u8"))
    }

    @Test
    fun `bos ve anlamsiz icerik guvenli sekilde doner`() {
        val empty = EmbedMediaScanner.scan("", "https://example.com")
        assertTrue(empty.media.isEmpty())
        assertTrue(empty.iframes.isEmpty())
        assertFalse(empty.looksLikePlayer)

        val noMedia = EmbedMediaScanner.scan("<html><body><h1>Film bulunamadı</h1></body></html>", "https://example.com")
        assertTrue(noMedia.media.isEmpty())
    }
}

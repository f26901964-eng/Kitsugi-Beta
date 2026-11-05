package com.kitsugi.animelist.data.manga.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Light novel HTML → blok ayrıştırma testleri (ağ erişimi gerektirmez).
 */
class NovelChapterParserTest {

    private val parser = NovelChapterParser()

    @Test
    fun paragraphsAreExtractedAndNoiseRemoved() {
        val html = """
            <html><body>
              <nav>Ana Sayfa | İletişim</nav>
              <div class="chapter-content">
                <h1>Bölüm 12: Uyanış</h1>
                <p>Ahmet gözlerini açtığında güneş çoktan doğmuştu ve odanın içi sıcaktı.</p>
                <p>Uzun bir süre hiçbir şey söylemeden tavana baktı, sonra yavaşça doğruldu.</p>
              </div>
              <div class="comments">Yorum yap</div>
              <script>alert(1)</script>
            </body></html>
        """.trimIndent()

        val content = parser.parse(html, "https://example.com/bolum-12")
        assertEquals("Bölüm 12: Uyanış", content.title)
        assertEquals(2, content.blocks.count { it.style == NovelBlockStyle.Paragraph })
        assertTrue(content.blocks.none { it.text.contains("Yorum") })
        assertTrue(content.blocks.none { it.text.contains("alert") })
        assertTrue(content.blocks.none { it.text.contains("İletişim") })
    }

    @Test
    fun noReadableTextProducesError() {
        val content = parser.parse("<html><body><div class='comments'>yorum</div></body></html>")
        assertTrue(content.blocks.isEmpty())
        assertTrue(content.error != null)
    }

    @Test
    fun imagesAreKeptAsBlocks() {
        val html = """
            <div class="chapter-content">
              <p>İllüstrasyon öncesi uzun bir açıklama paragrafı burada yer alıyor ve yeterince uzun.</p>
              <img src="https://cdn.example.com/illus1.jpg" />
            </div>
        """.trimIndent()
        val content = parser.parse(html)
        assertTrue(content.blocks.any { it.style == NovelBlockStyle.Image && it.text.contains("illus1") })
    }

    @Test
    fun htmlDetectionRecognisesChapterUrls() {
        assertTrue(parser.looksLikeHtmlPage("https://site.com/bolum-12"))
        assertTrue(parser.looksLikeHtmlPage("https://site.com/chapter/5"))
        assertTrue(parser.looksLikeHtmlPage("https://site.com/x/1.html"))
        assertTrue(!parser.looksLikeHtmlPage("https://site.com/img/1.jpg"))
    }

    @Test
    fun readingTimeEstimate() {
        assertEquals(1, NovelChapterParser.estimateReadingMinutes(30))
        assertEquals(2, NovelChapterParser.estimateReadingMinutes(440))
    }
}

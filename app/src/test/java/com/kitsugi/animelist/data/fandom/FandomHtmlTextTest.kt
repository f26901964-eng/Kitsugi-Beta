package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.fandom.FandomBlockKind.BULLET
import com.kitsugi.animelist.data.fandom.FandomBlockKind.HEADING
import com.kitsugi.animelist.data.fandom.FandomBlockKind.PARAGRAPH
import com.kitsugi.animelist.data.fandom.FandomBlockKind.QUOTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomHtmlTextTest {

    @Test
    fun `inline joins list items and drops references`() {
        val html = "<ul><li>King of the Dead</li><li>Monarch of Shadows<sup class=\"reference\">[1]</sup></li></ul>"
        assertEquals("King of the Dead · Monarch of Shadows", FandomHtmlText.inline(html))
    }

    @Test
    fun `inline removes ruby readings line breaks and plain reference markers`() {
        assertEquals(
            "五条悟",
            FandomHtmlText.inline("<ruby>五<rp>(</rp><rt>ご</rt><rp>)</rp></ruby><ruby>条<rt>じょう</rt></ruby>悟")
        )
        assertEquals("Line one · Line two", FandomHtmlText.inline("Line one<br>Line two"))
        assertEquals("Over 190 cm", FandomHtmlText.inline("Over 190 cm[3][5]"))
        assertEquals("Tom & Jerry", FandomHtmlText.inline("Tom &amp; Jerry"))
        assertEquals("", FandomHtmlText.inline(null))
        assertEquals("Age", FandomHtmlText.inline("<b>Age</b>"))
    }

    @Test
    fun `section blocks skip the leading section title and keep structure`() {
        val html = """
            <div class="mw-parser-output">
              <h2><span class="mw-headline">Appearance</span><span class="mw-editsection">[edit]</span></h2>
              <p>Satoru is a tall man<sup class="reference">[1]</sup> with white hair.</p>
              <table class="navbox"><tr><td>Navigation</td></tr></table>
              <div class="notice">This article may require a cleanup.</div>
              <h3>Outfit</h3>
              <ul><li>Blindfold<ul><li>Black</li></ul></li><li>Uniform</li></ul>
              <blockquote>Throughout heaven and earth.</blockquote>
              Loose text node
            </div>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html)
        assertEquals(
            listOf(
                FandomTextBlock(PARAGRAPH, "Satoru is a tall man with white hair."),
                FandomTextBlock(HEADING, "Outfit"),
                FandomTextBlock(BULLET, "Blindfold"),
                FandomTextBlock(BULLET, "Black"),
                FandomTextBlock(BULLET, "Uniform"),
                FandomTextBlock(QUOTE, "Throughout heaven and earth."),
                FandomTextBlock(PARAGRAPH, "Loose text node")
            ),
            blocks
        )
    }

    @Test
    fun `wiki links preserve article targets roles language and row context`() {
        val html = """
            <table class="wikitable">
              <tr><th>Japanese voice actor</th><td><a href="/wiki/Kana_Hanazawa">Kana Hanazawa</a></td></tr>
              <tr><th>Character</th><td><a href="/wiki/Sung_Jinwoo">Sung Jinwoo</a></td></tr>
              <tr><td><a class="new" href="/wiki/Red_Link">Red link</a></td></tr>
              <tr><td><a href="/wiki/File:Portrait.png">Portrait</a></td></tr>
              <tr><td><a href="https://example.com/person">External</a></td></tr>
            </table>
            <table>
              <tr><th>Character</th><th>Voice Actor</th></tr>
              <tr><td><a href="/wiki/Megumin">Megumin</a></td><td><a href="/wiki/Rie_Takahashi">Rie Takahashi</a></td></tr>
            </table>
        """.trimIndent()

        val links = FandomHtmlText.wikiLinks(
            html,
            defaultWikiId = "frieren",
            role = FandomRelationType.RELATED
        )

        assertEquals(4, links.size)
        val kana = links[0]
        assertEquals("frieren" to "Kana Hanazawa", kana.wikiId to kana.pageTitle)
        assertEquals(FandomRelationType.VOICE_ACTOR, kana.role)
        assertEquals("ja", kana.language)
        assertEquals(FandomRelationType.CHARACTER, links[1].role)
        assertEquals(FandomRelationType.CHARACTER, links[2].role)
        assertEquals(FandomRelationType.VOICE_ACTOR, links[3].role)
    }

    @Test
    fun `definition lists become headings and paragraphs`() {
        val html = "<dl><dt>Limitless</dt><dd>Brings the concept of infinity into reality.</dd></dl>"
        assertEquals(
            listOf(
                FandomTextBlock(HEADING, "Limitless"),
                FandomTextBlock(PARAGRAPH, "Brings the concept of infinity into reality.")
            ),
            FandomHtmlText.blocks(html)
        )
    }

    @Test
    fun `content tables keep chapter and episode information but navigation tables are dropped`() {
        val html = """
            <table class="wikitable">
              <tr><th>Number of Chapters</th><th>Number of Episodes</th></tr>
              <tr><td>18</td><td>8</td></tr>
              <tr><td>1. Ryomen Sukuna<br>2. Secret Execution</td><td>1. Ryomen Sukuna</td></tr>
            </table>
            <table class="navbox"><tr><td>Related navigation</td></tr></table>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html)
        assertEquals(3, blocks.size)
        assertEquals("Number of Chapters · Number of Episodes", blocks[0].text)
        assertTrue(blocks[1].text.contains("Number of Chapters: 18"))
        assertTrue(blocks[1].text.contains("Number of Episodes: 8"))
        assertTrue(blocks[2].text.contains("Ryomen Sukuna"))
        assertTrue(blocks.none { it.text.contains("Related navigation") })
    }

    @Test
    fun `article figures and gallery items keep secure image urls and captions`() {
        val html = """
            <figure>
              <img data-src="//static.wikia.nocookie.net/test/images/a/ab/Gojo.webp/revision/latest/scale-to-width-down/320?cb=1"
                   src="data:image/gif;base64,AA" alt="Satoru Gojo">
              <figcaption>Gojo at Jujutsu High</figcaption>
            </figure>
            <ul class="gallery mw-gallery-traditional">
              <li class="gallerybox"><div class="thumb"><img src="https://static.wikia.nocookie.net/test/images/Battle.jpg/revision/latest?cb=2"></div>
                <div class="gallerytext"><p>Shibuya Incident</p></div></li>
            </ul>
        """.trimIndent()
        val images = FandomHtmlText.blocks(html).filter { it.kind == FandomBlockKind.IMAGE }
        assertEquals(2, images.size)
        assertEquals("https://static.wikia.nocookie.net/test/images/a/ab/Gojo.webp/revision/latest/scale-to-width-down/320?cb=1", images[0].image?.url)
        assertEquals("Gojo at Jujutsu High", images[0].image?.caption)
        assertEquals("Shibuya Incident", images[1].image?.caption)
    }

    @Test
    fun `block count is bounded and empty input yields nothing`() {
        val html = (1..200).joinToString("") { "<p>Paragraph $it</p>" }
        assertEquals(10, FandomHtmlText.blocks(html, maxBlocks = 10).size)
        assertTrue(FandomHtmlText.blocks("").isEmpty())
        assertTrue(FandomHtmlText.blocks("<p> </p><p>…</p>").isEmpty())
    }

    @Test
    fun `internalWikiLinks extracts article titles while ignoring special namespaces`() {
        val html = """
            <p>
              <a href="/wiki/Yuji_Itadori">Yuji</a> and <a href="/wiki/Megumi_Fushiguro">Megumi</a>
              met with <a href="/wiki/File:Jujutsu.png">a photo</a>,
              checked <a href="/wiki/Special:Search">search</a> and
              <a href="https://example.com/external">external site</a>.
              Also referenced <a href="/wiki/Yuji_Itadori#Abilities">Yuji's abilities</a>.
            </p>
        """.trimIndent()
        val links = FandomHtmlText.internalWikiLinks(html)
        assertEquals(listOf("Yuji Itadori", "Megumi Fushiguro"), links)
    }
}

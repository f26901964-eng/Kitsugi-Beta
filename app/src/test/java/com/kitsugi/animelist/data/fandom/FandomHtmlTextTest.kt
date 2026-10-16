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

    @Test
    fun `internalWikiLinks decodes utf8 percent encoded titles`() {
        // Gerçek vaka (Yoshihiro Togashi sayfası): ō, UTF-8'de %C5%8D olarak kodlanır.
        // Çiplerde `Shinj%C5%8D, Yamagata` yerine çözülmüş başlık görünmelidir.
        val html = """
            <p>
              <a href="/wiki/Shinj%C5%8D,_Yamagata">Shinjō, Yamagata</a>
              <a href="/wiki/Weekly_Sh%C5%8Dnen_Jump">Weekly Shōnen Jump</a>
              <a href="/wiki/Tank%C5%8Dbon">Tankōbon</a>
              <a href="/wiki/Ten_de_Sh%C5%8Dwaru_Cupid">Ten de Shōwaru Cupid</a>
              <a href="/wiki/Category:Sh%C5%8Dnen_manga">category</a>
            </p>
        """.trimIndent()
        val links = FandomHtmlText.internalWikiLinks(html)
        assertEquals(
            listOf("Shinjō, Yamagata", "Weekly Shōnen Jump", "Tankōbon", "Ten de Shōwaru Cupid"),
            links
        )
    }

    @Test
    fun `internalWikiLinks decodes encoded punctuation keeps plus and survives malformed escapes`() {
        val html = """
            <p>
              <a href="/wiki/Shonen_Jump_%28magazine%29">Shonen Jump (magazine)</a>
              <a href="/wiki/A%2FB_Testing">A/B Testing</a>
              <a href="/wiki/C%2B%2B">C++</a>
              <a href="/wiki/Plus+Sign">Plus+Sign</a>
              <a href="/wiki/Bad%ZZEscape">Bad escape</a>
            </p>
        """.trimIndent()
        val links = FandomHtmlText.internalWikiLinks(html)
        assertEquals(
            listOf("Shonen Jump (magazine)", "A/B Testing", "C++", "Plus+Sign", "Bad%ZZEscape"),
            links
        )
    }

    @Test
    fun `paragraph keeps its inline wiki links with absolute urls`() {
        val html = "<p>Visit <a href=\"/wiki/Jill_Valentine\">Jill</a> and <a href=\"#Notes\">notes</a>.</p>"
        val block = FandomHtmlText.blocks(html, wikiId = "residentevil").single()
        assertEquals(PARAGRAPH, block.kind)
        // Sayfa içi çapa (#Notes) tıklanabilir hedef değildir.
        assertEquals(1, block.links.size)
        assertEquals("Jill", block.links.single().label)
        assertEquals("https://residentevil.fandom.com/wiki/Jill_Valentine", block.links.single().url)
    }

    @Test
    fun `image cell becomes a card with title subtitle image and target`() {
        val html = """
            <table><tr><td>
              <a href="/wiki/Resident_Evil_2_(remake)"><img src="https://static.wikia.nocookie.net/residentevil/images/a/ab/RE2.jpg/revision/latest?cb=1" alt="RE2"></a>
              <br>Resident Evil 2<br>2019
            </td></tr></table>
        """.trimIndent()
        val card = FandomHtmlText.blocks(html, wikiId = "residentevil").single { it.kind == FandomBlockKind.CARD }
        assertEquals("Resident Evil 2", card.text)
        assertEquals("2019", card.subtitle)
        assertEquals("https://residentevil.fandom.com/wiki/Resident_Evil_2_(remake)", card.href)
        assertTrue(card.image?.url?.startsWith("https://") == true)
    }

    @Test
    fun `navigation box keeps every link as a link group`() {
        val html = """
            <table class="navbox"><tr><th>Franchise</th></tr>
            <tr><td><a href="/wiki/Resident_Evil">Resident Evil</a> · <a href="/wiki/Umbrella">Umbrella</a></td></tr></table>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html, wikiId = "residentevil")
        val group = blocks.single { it.kind == FandomBlockKind.LINK_GROUP }
        assertEquals(listOf("Resident Evil", "Umbrella"), group.links.map { it.label })
        assertTrue(group.links.all { it.url.startsWith("https://residentevil.fandom.com/wiki/") })
    }

    @Test
    fun `linked image keeps its target page`() {
        val html = "<p><a href=\"/wiki/Video_Games\"><img src=\"https://static.wikia.nocookie.net/residentevil/images/a/ab/Games.jpg/revision/latest?cb=3\"></a></p>"
        val image = FandomHtmlText.blocks(html, wikiId = "residentevil").single { it.kind == FandomBlockKind.IMAGE }
        assertEquals("https://residentevil.fandom.com/wiki/Video_Games", image.href)
    }

    @Test
    fun `absolute url resolution handles relative protocol and anchors`() {
        assertEquals("https://residentevil.fandom.com/wiki/Virus", FandomHtmlText.absoluteUrl("/wiki/Virus", "residentevil"))
        assertEquals("https://static.example.com/a.png", FandomHtmlText.absoluteUrl("//static.example.com/a.png", null))
        assertEquals(null, FandomHtmlText.absoluteUrl("#Top", "residentevil"))
        assertEquals(null, FandomHtmlText.absoluteUrl("javascript:void(0)", "residentevil"))
    }
    @Test
    fun `related wiki links exclude red links and other fandom communities`() {
        val html = """
            <p><a class="new" href="/wiki/Tetsur%C5%8D_Araki">Tetsurō Araki</a>
               <a data-redlink="1" href="/wiki/Yuichiro_Hayashi">Yuichiro Hayashi</a>
               <a href="https://some-other-wiki.fandom.com/wiki/Staff">Staff</a>
               <a href="https://attackontitan.fandom.com/wiki/Yui_Ishikawa">Yui</a>
               <a href="/wiki/Eren_Jaeger">Eren</a></p>
        """.trimIndent()
        assertEquals(
            listOf("Yui Ishikawa", "Eren Jaeger"),
            FandomHtmlText.internalWikiLinks(html, "attackontitan")
        )
    }

    /**
     * Canlı tr.wikipedia hatası: "Titana Saldırı" maddesinin bilgi kutusundaki
     * `{{Animanga lisans}}` şablonu ülke/bölge bağlantısı üretiyor ve bunlar
     * "İlgili maddeler" şeridine "Amerika Birleşik Devletleri", "Kanada" olarak düşüyordu.
     */
    @Test
    fun `wikipedia body links never include infobox navbox or reference boxes`() {
        val html = """
            <div class="mw-parser-output">
              <table class="infobox"><tbody><tr><th>Diğer yayımcı</th><td>
                <a href="/wiki/Amerika_Birle%C5%9Fik_Devletleri">ABD</a>
                <a href="/wiki/Kanada"><img src="//upload.wikimedia.org/flag.svg" alt=""></a>
              </td></tr></tbody></table>
              <table class="navbox"><tbody><tr><td><a href="/wiki/Anime">Anime</a></td></tr></tbody></table>
              <div class="reflist"><ol class="references"><li><a href="/wiki/Tokyo">Tokyo</a></li></ol></div>
              <p>Başkahraman <a href="/wiki/Eren_Yeager">Eren Yeager</a>'dır.</p>
            </div>
        """.trimIndent()
        assertEquals(
            listOf("Eren Yeager"),
            FandomHtmlText.internalWikiLinkCounts(html, "wikipedia.tr").keys.toList()
        )
        // Fandom kurulumunda bu kutular gövde kabul edilir; davranış değişmemeli.
        val fandom = FandomHtmlText.internalWikiLinkCounts(html, "attackontitan")
        assertTrue(fandom.containsKey("Amerika Birleşik Devletleri"))
        assertTrue(fandom.containsKey("Anime"))
        assertTrue(fandom.containsKey("Eren Yeager"))
    }

    @Test
    fun `image only flag links are skipped on wikipedia but kept on fandom`() {
        val html = """
            <p><a href="/wiki/Kanada"><img src="//upload.wikimedia.org/flag.svg" alt=""></a>
               <a href="/wiki/Amerika_Birle%C5%9Fik_Devletleri">Amerika Birleşik Devletleri</a></p>
        """.trimIndent()
        assertEquals(
            listOf("Amerika Birleşik Devletleri"),
            FandomHtmlText.internalWikiLinkCounts(html, "wikipedia.tr").keys.toList()
        )
        assertEquals(
            listOf("Kanada", "Amerika Birleşik Devletleri"),
            FandomHtmlText.internalWikiLinkCounts(html, "attackontitan").keys.toList()
        )
    }

    @Test
    fun `wikipedia blocks drop infobox and navbox text`() {
        val html = """
            <table class="infobox"><tbody><tr><th>Yayımcı</th><td>Kodansha USA</td></tr></tbody></table>
            <div class="navbox"><a href="/wiki/Anime">Anime</a></div>
            <p>Titana Saldırı, Hajime Isayama'nın manga serisidir.</p>
        """.trimIndent()
        val wikipedia = FandomHtmlText.blocks(html, wikiId = "wikipedia.tr").map { it.text }
        assertTrue(wikipedia.none { it.contains("Kodansha USA") })
        assertTrue(wikipedia.none { it.contains("Anime") })
        assertTrue(wikipedia.any { it.contains("manga serisidir") })
    }

}

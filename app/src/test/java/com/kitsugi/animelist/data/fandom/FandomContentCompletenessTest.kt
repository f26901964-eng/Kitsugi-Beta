package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.fandom.FandomBlockKind.BULLET
import com.kitsugi.animelist.data.fandom.FandomBlockKind.IMAGE
import com.kitsugi.animelist.data.fandom.FandomBlockKind.PARAGRAPH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Wiki içeriği eksiksiz alınıyor mu?" denetiminin kilit testleri.
 *
 * Her test, canlı denetimde (tools/wiki_audit/content_audit.py) ölçülen bir veri kaybını
 * kapatır: şablon bölümleri, katlanmış/sekmeli içerik, tıklanabilir bağlantı konumları ve
 * sessiz kırpma (tablo satırı / görsel) bildirimleri.
 */
class FandomContentCompletenessTest {

    // ── 1. Şablon bölümleri ─────────────────────────────────────────────────

    /**
     * MediaWiki, sayfa gövdesine şablonla eklenen bölümlere `T-1` gibi SAYISAL OLMAYAN
     * kimlik verir. Eskiden `toIntOrNull() ?: continue` bu bölümleri tamamen atıyordu:
     * Seslendirenler / Görünümler gibi bölümler ne içindekilerde görünüyordu ne de
     * içerikleri çekiliyordu.
     */
    @Test
    fun `template sections keep their non numeric index instead of being dropped`() {
        val json = """
            {"parse":{"sections":[
              {"toclevel":1,"level":"1","line":"Kişilik","number":"1","index":"1"},
              {"toclevel":1,"level":"2","line":"Seslendirenler","number":"2","index":"T-1"},
              {"toclevel":1,"level":"2","line":"Görünümler","number":"3","index":"T-2"},
              {"toclevel":1,"level":"1","line":"Trivia","number":"4","index":"4"}
            ]}}
        """.trimIndent()
        val sections = FandomApiParsers.parseSections(json)
        assertNotNull(sections)
        val indexes = sections!!.map { it.index }
        assertEquals(listOf("1", "T-1", "T-2"), indexes)
        assertTrue("Trivia gizli bölüm olarak kalmalı", indexes.none { it == "4" })

        val template = sections.first { it.index == "T-1" }
        assertEquals("Seslendirenler", template.title)
        assertTrue(template.isTemplateSection)
        assertFalse(sections.first { it.index == "1" }.isTemplateSection)
    }

    @Test
    fun `section refs survive json round trip with string index`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val original = FandomSectionRef(index = "T-3", title = "Güçler", level = 2, spoiler = true)
        val restored = json.decodeFromString(
            FandomSectionRef.serializer(),
            json.encodeToString(FandomSectionRef.serializer(), original)
        )
        assertEquals(original, restored)
        assertTrue(restored.isTemplateSection)
    }

    // ── 2. Katlanmış ve sekmeli içerik ──────────────────────────────────────

    /**
     * `.mw-collapsible-toggle` ve `.wds-tabs__wrapper` eskiden çöp sayılıp SİLİNİYORDU.
     * Oysa ikisi de gerçek içerik taşır; tarayıcıda yalnızca katlanmış görünür.
     */
    @Test
    fun `collapsible and tabbed content is kept as text`() {
        val html = """
            <div class="mw-parser-output">
              <div class="mw-collapsible mw-collapsed">
                <div class="mw-collapsible-toggle">göster</div>
                <p>Katlanmış kutunun içindeki gerçek içerik burada duruyor ve oldukça uzun.</p>
              </div>
              <div class="wds-tabs__wrapper">
                <div class="wds-tab__content"><p>Sekmeli içerik: çocukluk dönemi bilgileri burada.</p></div>
              </div>
            </div>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html)
        val text = blocks.joinToString("\n") { it.text }
        assertTrue("katlanmış içerik kayboldu: $text", text.contains("Katlanmış kutunun içindeki gerçek içerik"))
        assertTrue("sekmeli içerik kayboldu: $text", text.contains("çocukluk dönemi bilgileri"))
        // "göster" düğmesinin etiketi içerik değildir; yalnızca düğme metni kalmamalı.
        assertFalse(blocks.any { it.text == "göster" })
    }

    // ── 3. Metin içi bağlantılar ────────────────────────────────────────────

    /**
     * Gövde metni düzleştirilirken `<a>` hedefleri kayboluyordu; kullanıcı yalnızca bölüm
     * sonundaki en fazla 12 çipten gezinebiliyordu. Artık her blok bağlantı konumlarını taşır.
     */
    @Test
    fun `paragraph blocks carry clickable link spans with exact offsets`() {
        val html = """
            <div class="mw-parser-output">
              <p>Naruto is a ninja from <a href="/wiki/Konohagakure" title="Konohagakure">Konohagakure</a>
                 and a member of <a href="https://naruto.fandom.com/wiki/Team_7" title="Team 7">Team 7</a>.</p>
            </div>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html, wikiId = "naruto")
        val paragraph = blocks.first { it.kind == PARAGRAPH }
        assertEquals(2, paragraph.linkSpans.size)

        val first = paragraph.linkSpans[0]
        assertEquals("Konohagakure", first.title)
        assertEquals("naruto", first.wikiId)
        assertEquals("Konohagakure", paragraph.text.substring(first.start, first.end))

        val second = paragraph.linkSpans[1]
        assertEquals("Team 7", second.title)
        assertEquals("Team 7", paragraph.text.substring(second.start, second.end))
    }

    @Test
    fun `link spans survive whitespace tidying and reference removal`() {
        val html = """
            <p>Konu  <a href="/wiki/Gintoki_Sakata">Gintoki Sakata</a>  etrafında
               döner<sup class="reference">[2]</sup>.</p>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html, wikiId = "gintama")
        val block = blocks.first()
        assertEquals("Konu Gintoki Sakata etrafında döner.", block.text)
        assertEquals(1, block.linkSpans.size)
        val span = block.linkSpans.single()
        assertEquals("Gintoki Sakata", block.text.substring(span.start, span.end))
    }

    @Test
    fun `red links and special namespaces never become link spans`() {
        val html = """
            <p>Gerçek <a href="/wiki/Uzumaki_Naruto">Naruto</a>, kırmızı
               <a class="new" href="/wiki/Olmayan_Sayfa">olmayan</a> ve
               <a href="/wiki/Kategori:Karakterler">kategori</a> bağlantısı.</p>
        """.trimIndent()
        val blocks = FandomHtmlText.blocks(html, wikiId = "naruto")
        val titles = blocks.first().linkSpans.map { it.title }
        assertEquals(listOf("Uzumaki Naruto"), titles)
    }

    // ── 4. Sessiz kırpma artık bildiriliyor ─────────────────────────────────

    @Test
    fun `oversized tables report how many rows were omitted`() {
        val rows = (1..170).joinToString("") { "<tr><td>$it</td><td>Bölüm $it</td></tr>" }
        val html = "<table class=\"wikitable\"><tr><th>No</th><th>Ad</th></tr>$rows</table>"
        val blocks = FandomHtmlText.blocks(html)
        val bullets = blocks.filter { it.kind == BULLET }
        assertTrue("satırlar kırpıldı", bullets.size < 171)
        val note = blocks.last().text
        assertTrue("kırpma notu yok: $note", note.contains("tablo satırı gösterilmedi"))
        assertTrue("not doğru sayıyı vermeli: $note", note.contains("+21"))
    }

    @Test
    fun `gallery over the image cap reports the omitted count`() {
        val cells = (1..70).joinToString("") {
            "<div class=\"gallerybox\"><img src=\"https://static.wikia.nocookie.net/naruto/images/$it.png\" " +
                "data-width=\"600\" data-height=\"800\"/><div class=\"gallerytext\">Görsel $it</div></div>"
        }
        val blocks = FandomHtmlText.blocks("<div class=\"gallery\">$cells</div>")
        val images = blocks.count { it.kind == IMAGE }
        assertEquals("görsel üst sınırı 60 olmalı", 60, images)
        val note = blocks.last().text
        assertTrue("görsel kırpma notu yok: $note", note.contains("görsel bölüm sınırına takıldı"))
    }

    // ── 5. Regresyon: bilinen iyi davranışlar korunuyor ─────────────────────

    @Test
    fun `navbox infobox and references are still removed`() {
        val html = """
            <div class="mw-parser-output">
              <p>Gerçek içerik cümlesi burada.</p>
              <table class="navbox"><tr><td>Navigasyon kutusu</td></tr></table>
              <aside class="portable-infobox"><div class="pi-data">Bilgi kutusu</div></aside>
              <ol class="references"><li>Kaynak metni</li></ol>
            </div>
        """.trimIndent()
        val text = FandomHtmlText.blocks(html).joinToString("\n") { it.text }
        assertTrue(text.contains("Gerçek içerik cümlesi"))
        assertFalse(text.contains("Navigasyon kutusu"))
        assertFalse(text.contains("Bilgi kutusu"))
        assertFalse(text.contains("Kaynak metni"))
    }

    @Test
    fun `infobox fields still expose their wiki links`() {
        val infoboxJson = """
            [{"data":[
              {"type":"title","data":{"value":"Sakata Gintoki"}},
              {"type":"data","data":{"label":"Relatives","source":"relatives",
                "value":"<a href=\"/wiki/Sakata_Kintoki\">Sakata Kintoki</a>"}}
            ]}]
        """.trimIndent()
        val infobox = FandomInfoboxParser.parse(infoboxJson, wikiId = "gintama")
        assertNotNull(infobox)
        val field = infobox!!.allFields().single()
        assertEquals("Relatives", field.label)
        val link = field.links.single()
        assertEquals("Sakata Kintoki", link.pageTitle)
        assertEquals("gintama", link.wikiId)
    }
}

package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomLeadParserTest {

    private val leadHtml = """
        <div class="mw-parser-output">
          <div class="page-tabs"><span>Overview</span><span>Story</span><span>Gallery</span></div>
          <aside class="portable-infobox pi-background pi-theme-wikia">
            <h2 class="pi-item pi-title" data-source="title2">Gintoki Sakata</h2>
            <figure class="pi-item pi-image" data-source="image">
              <a href="https://static.wikia.nocookie.net/gintama/images/b/b9/GinFinal.png/revision/latest?cb=1" class="image">
                <img src="https://static.wikia.nocookie.net/gintama/images/b/b9/GinFinal.png/revision/latest/scale-to-width-down/350?cb=1" alt="Present"/>
              </a>
              <figcaption class="pi-item-spacing pi-caption">Present</figcaption>
            </figure>
            <section class="pi-item pi-group">
              <h2 class="pi-item pi-header">Voice Actors</h2>
              <div class="pi-item pi-data" data-source="jva">
                <h3 class="pi-data-label">Japanese VA:</h3>
                <div class="pi-data-value"><a href="/wiki/Tomokazu_Sugita" title="Tomokazu Sugita">Tomokazu Sugita</a><br><a href="/wiki/Yaguchi_Asami">Yaguchi Asami</a> (Child)</div>
              </div>
              <div class="pi-item pi-data" data-source="status">
                <h3 class="pi-data-label">Status:</h3>
                <div class="pi-data-value">Alive</div>
              </div>
            </section>
          </aside>
          <p>Gintoki Sakata (坂田 銀時, Sakata Gintoki?) is the main protagonist of the Gintama series. He is the founder and president of the Yorozuya.</p>
          <p>He is a highly skilled samurai who fought in the Joui War.<sup class="reference">[1]</sup></p>
        </div>
    """.trimIndent()

    @Test
    fun `infobox keeps link text and list separators that the JSON pageprop drops`() {
        val box = FandomLeadParser.parseInfobox(leadHtml, "gintama")
        assertNotNull(box)
        assertEquals("Gintoki Sakata", box!!.title)
        val va = box.allFields().first { it.label == "Japanese VA" }
        assertEquals("Tomokazu Sugita · Yaguchi Asami (Child)", va.value)
        assertEquals(listOf("Tomokazu Sugita", "Yaguchi Asami"), va.links.map { it.label })
        assertTrue(box.allFields().first { it.label == "Status" }.spoiler)
        assertEquals("Voice Actors", box.groups.single().header)
        assertEquals(
            "https://static.wikia.nocookie.net/gintama/images/b/b9/GinFinal.png/revision/latest?cb=1",
            box.images.single().url
        )
    }

    @Test
    fun `intro keeps every lead paragraph but drops tab labels and the infobox`() {
        val intro = FandomLeadParser.parseIntro(leadHtml)
        assertNotNull(intro)
        assertTrue(intro!!.startsWith("Gintoki Sakata ("))
        assertTrue(intro.contains("Yorozuya."))
        assertTrue(intro.contains("fought in the Joui War."))
        assertFalse(intro.contains("Overview"))
        assertFalse(intro.contains("Tomokazu"))
        assertFalse(intro.contains("[1]"))
        assertEquals(2, intro.split("\n\n").size)
    }

    @Test
    fun `tabbed infobox panels become labelled groups`() {
        val html = """
            <aside class="portable-infobox">
              <h2 class="pi-title">Luffy</h2>
              <div class="pi-item pi-panel wds-tabber">
                <div class="wds-tabs__wrapper"><ul class="wds-tabs">
                  <li class="wds-tabs__tab"><div class="wds-tabs__tab-label">Before</div></li>
                  <li class="wds-tabs__tab"><div class="wds-tabs__tab-label">After</div></li>
                </ul></div>
                <div class="wds-tab__content wds-is-current">
                  <section class="pi-group"><h2 class="pi-header">Bounty</h2>
                    <div class="pi-data" data-source="b1"><h3 class="pi-data-label">Amount</h3><div class="pi-data-value">30,000,000</div></div></section>
                </div>
                <div class="wds-tab__content">
                  <section class="pi-group"><h2 class="pi-header">Bounty</h2>
                    <div class="pi-data" data-source="b2"><h3 class="pi-data-label">Amount</h3><div class="pi-data-value">3,000,000,000</div></div></section>
                </div>
              </div>
            </aside>
        """.trimIndent()
        val box = FandomLeadParser.parseInfobox(html, "onepiece")!!
        assertEquals(listOf("Before · Bounty", "After · Bounty"), box.groups.map { it.header })
        assertEquals(listOf("30,000,000", "3,000,000,000"), box.allFields().map { it.value })
    }

    @Test
    fun `merge prefers the complete HTML infobox but keeps JSON images`() {
        val json = FandomInfobox(
            title = "Gintoki Sakata",
            images = listOf(FandomImage("https://static.wikia.nocookie.net/a.png")),
            groups = listOf(FandomInfoboxGroup("Voice Actors", listOf(FandomInfoboxField("Japanese VA", "(Child)"))))
        )
        val dom = FandomLeadParser.parseInfobox(leadHtml, "gintama")
        val merged = FandomLeadParser.mergeInfobox(json, dom)!!
        assertEquals("Tomokazu Sugita · Yaguchi Asami (Child)", merged.allFields().first { it.label == "Japanese VA" }.value)
        assertEquals("https://static.wikia.nocookie.net/a.png", merged.images.single().url)
        assertEquals(json, FandomLeadParser.mergeInfobox(json, null))
        assertEquals(dom, FandomLeadParser.mergeInfobox(null, dom))
    }

    @Test
    fun `merge keeps JSON fields but upgrades truncated values when HTML has fewer fields`() {
        val json = FandomInfobox(
            groups = listOf(
                FandomInfoboxGroup(
                    null,
                    listOf(FandomInfoboxField("Japanese VA", "(Child)"), FandomInfoboxField("Hair", "Silver"), FandomInfoboxField("Eyes", "Red"))
                )
            )
        )
        val dom = FandomInfobox(groups = listOf(FandomInfoboxGroup(null, listOf(FandomInfoboxField("Japanese VA", "Tomokazu Sugita (Child)")))))
        val merged = FandomLeadParser.mergeInfobox(json, dom)!!
        assertEquals(listOf("Tomokazu Sugita (Child)", "Silver", "Red"), merged.allFields().map { it.value })
    }

    @Test
    fun `description uses the full intro when the short description is truncated`() {
        val short = "Monkey D. Luffy is the captain of the Straw Hat Pirates. He pursues the legendary treasure..."
        val intro = "Monkey D. Luffy is the captain of the Straw Hat Pirates. He pursues the legendary treasure of Gol D. Roger.\n\nBorn on Dawn Island, Luffy is the son of Dragon."
        assertEquals(intro, FandomLeadParser.mergeDescription(short, intro))
        assertEquals(short, FandomLeadParser.mergeDescription(short, null))
        assertEquals(intro, FandomLeadParser.mergeDescription(null, intro))
        assertNull(FandomLeadParser.mergeDescription(null, " "))
        assertEquals("Complete short text.", FandomLeadParser.mergeDescription("Complete short text.", "Something unrelated and rather longer than the short one."))
    }

    @Test
    fun `page without infobox or intro is empty`() {
        assertTrue(FandomLeadParser.parse("<div class=\"mw-parser-output\"></div>", "gintama").isEmpty)
        assertTrue(FandomLeadParser.parse(null, "gintama").isEmpty)
    }
}

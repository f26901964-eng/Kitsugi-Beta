package com.kitsugi.animelist.ui.screens.detail.wiki

import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomBlockLink
import com.kitsugi.animelist.data.fandom.FandomImage
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Otomatik çeviri koruma katmanının sözleşme testleri.
 *
 * Buradaki kurallar doğrudan kullanıcı şikâyetlerinden türetildi: çeviri açıldığında
 * bağlantılar kopmamalı, sayfa düzeni (blok sırası, görsel konumu, kart/grup yapısı)
 * değişmemeli ve bilgi kutusu değerleri birbirine kaymamalı.
 */
class WikiTranslationProtectionTest {

    private fun paragraph(text: String, links: List<FandomBlockLink> = emptyList()) =
        FandomTextBlock(kind = FandomBlockKind.PARAGRAPH, text = text, links = links)

    private fun image(url: String = "https://static.example/yume.png") =
        FandomTextBlock(
            kind = FandomBlockKind.IMAGE,
            text = "Yume Tōrō",
            image = FandomImage(url = url, caption = "Yume Tōrō")
        )

    // ---------------------------------------------------------------- yapı koruması

    @Test
    fun blockTranslationKeepsOrderKindsImagesAndLinks() = runBlocking {
        val content = FandomSectionContent(
            blocks = listOf(
                FandomTextBlock(kind = FandomBlockKind.HEADING, text = "Name"),
                paragraph(
                    text = "Yume Tōrō is a song from the Your Name film.",
                    links = listOf(
                        FandomBlockLink(label = "Yume Tōrō", url = "https://kiminonawa.fandom.com/wiki/Yume_Tourou"),
                        FandomBlockLink(label = "Your Name", url = "https://kiminonawa.fandom.com/wiki/Kimi_no_Na_wa")
                    )
                ),
                image(),
                FandomTextBlock(kind = FandomBlockKind.BULLET, text = "Release Date: August 24, 2016"),
                FandomTextBlock(
                    kind = FandomBlockKind.CARD,
                    text = "Kimi no Na wa. Soundtracks",
                    href = "https://kiminonawa.fandom.com/wiki/Kimi_no_Na_wa._Soundtracks",
                    subtitle = "Release date: 2016"
                ),
                FandomTextBlock(kind = FandomBlockKind.QUOTE, text = "It plays at the end of the 2nd minute.")
            )
        )

        val entries = translateSectionBlocks("sec:0", content) { text -> "[tr] $text" }

        val translated = content.withTranslatedBlocks("sec:0", entries)
        assertNotNull(translated)
        translated!!

        // Blok sayısı, türü ve SIRASI birebir korunur: görseller yerinden kaymaz.
        assertEquals(content.blocks.size, translated.blocks.size)
        assertEquals(
            content.blocks.map { it.kind },
            translated.blocks.map { it.kind }
        )

        // Görsel bloğu yerinde kalır: tür, sıra, URL ve dosya adı birebir aynıdır.
        // Yalnızca altındaki beyaz metin (figcaption) çevrilir — "bu wiki sayfalarında
        // mavi bağlantılar hariç her şey seçili dile gelir" kuralı.
        val imageBlock = translated.blocks[2]
        assertEquals(content.blocks[2].text, imageBlock.text)
        assertEquals(content.blocks[2].image?.url, imageBlock.image?.url)
        assertEquals(content.blocks[2].image?.fileTitle, imageBlock.image?.fileTitle)
        assertEquals("[tr] Yume Tōrō", imageBlock.image?.caption)

        // Bağlantılar (dokunma hedefi) ve etiketleri korunur.
        val translatedParagraph = translated.blocks[1]
        assertEquals(content.blocks[1].links, translatedParagraph.links)
        assertTrue(translatedParagraph.text.startsWith("Yume Tōrō [tr] "))
        assertTrue(translatedParagraph.text.contains("Yume Tōrō"))
        assertTrue(translatedParagraph.text.contains("Your Name"))

        // Sayfaya bağlı kart başlığı çevrilmez; yalnızca alt satır çevrilir.
        val card = translated.blocks[4]
        assertEquals("Kimi no Na wa. Soundtracks", card.text)
        assertEquals("[tr] Release date: 2016", card.subtitle)
        assertEquals("https://kiminonawa.fandom.com/wiki/Kimi_no_Na_wa._Soundtracks", card.href)
    }

    @Test
    fun translatedTextIsNotProducedWhenTranslationIsIdentical() = runBlocking {
        val content = FandomSectionContent(blocks = listOf(paragraph("Yume Tōrō")))
        val entries = translateSectionBlocks("sec:0", content) { it }
        assertTrue(entries.isEmpty())
        assertNull(content.withTranslatedBlocks("sec:0", entries))
    }

    @Test
    fun withTranslatedBlocksReturnsNullWhenNoChildTranslationExists() {
        val content = FandomSectionContent(blocks = listOf(paragraph("Yume Tōrō")))
        assertNull(content.withTranslatedBlocks("sec:0", emptyMap()))
        assertNull(content.withTranslatedBlocks("sec:0", mapOf("sec:0" to WikiStructuredTranslationKeys.COMPLETE)))
    }

    // ---------------------------------------------------------------- bağlantı koruması

    @Test
    fun blockWithLinksTranslatesNormalSegmentsWithoutSendingLinkLabel() = runBlocking {
        val content = FandomSectionContent(
            blocks = listOf(
                paragraph(
                    text = "Yume Tōrō is a song from Your Name.",
                    links = listOf(
                        FandomBlockLink("Yume Tōrō", "https://wiki/Yume_Tourou"),
                        FandomBlockLink("Your Name", "https://wiki/Kimi_no_Na_wa")
                    )
                )
            )
        )
        val requests = mutableListOf<String>()
        val entries = translateSectionBlocks("sec:0", content) { segment ->
            requests += segment
            when {
                segment == "is a song from" -> "bir şarkıdır"
                else -> "[tr] $segment"
            }
        }
        val translated = content.withTranslatedBlocks("sec:0", entries)

        assertNotNull(translated)
        assertEquals("Yume Tōrō bir şarkıdır Your Name.", translated!!.blocks.single().text)
        // Tıklanabilir adlar çeviri servisine hiç gönderilmez; normal metin ise gönderilir.
        assertTrue(requests.none { it.contains("Yume Tōrō") || it.contains("Your Name") })
        assertEquals(listOf("is a song from"), requests)
        assertEquals(content.blocks.single().links, translated.blocks.single().links)
    }

    @Test
    fun failedSegmentDoesNotCancelTheRestOfLinkedParagraph() = runBlocking {
        val content = FandomSectionContent(
            blocks = listOf(
                paragraph(
                    text = "Before Link after text.",
                    links = listOf(FandomBlockLink("Link", "https://wiki/Link"))
                )
            )
        )
        val entries = translateSectionBlocks("sec:0", content) { segment ->
            if (segment.startsWith("Before")) throw IllegalStateException("ilk parça başarısız")
            "sonraki metin."
        }
        val translated = content.withTranslatedBlocks("sec:0", entries)

        assertNotNull(translated)
        assertEquals("Before Link sonraki metin.", translated!!.blocks.single().text)
    }

    // ---------------------------------------------------------------- teknik değerler

    @Test
    fun technicalValuesAreNeverSentToTranslator() = runBlocking {
        val rows = listOf(
            "https://kiminonawa.fandom.com/wiki/File:01_-_Yume_Tourou.ogg",
            "File:01_-_Yume_Tourou.ogg",
            "01_-_Yume_Tourou.ogg",
            "August 24, 2016",
            "2:09"
        )
        val seen = mutableListOf<String>()
        val translator: suspend (String) -> String = { value ->
            seen.add(value)
            "[tr] $value"
        }
        val translated = translateRowsPreservingStructure(rows, translator)

        assertEquals(rows.size, translated.size)
        // URL, ad alanı ve dosya adı satırları çeviriye hiç gönderilmez.
        assertEquals(rows[0], translated[0])
        assertEquals(rows[1], translated[1])
        assertEquals(rows[2], translated[2])
        assertTrue(seen.none { it.contains("https://") || it.startsWith("File:") || it.endsWith(".ogg") })
        // Çevrilebilir satır (tarih) çeviriden geçer; "2:09" gibi teknik satır yerinde kalır.
        assertEquals("[tr] August 24, 2016", translated[3])
        assertEquals("2:09", translated[4])
    }

    @Test
    fun isTechnicalWikiValueRecognisesUrlsFilesAndNamespaces() {
        assertTrue(isTechnicalWikiValue("https://kiminonawa.fandom.com/wiki/File:01_-_Yume_Tourou.ogg"))
        assertTrue(isTechnicalWikiValue("File:01_-_Yume_Tourou.ogg"))
        assertTrue(isTechnicalWikiValue("Category:Songs"))
        assertTrue(isTechnicalWikiValue("01_-_Yume_Tourou.ogg"))
        assertTrue(isTechnicalWikiValue("2016"))
        assertTrue(isTechnicalWikiValue("v2.4.289"))

        assertFalse(isTechnicalWikiValue("Kimi no Na wa."))
        assertFalse(isTechnicalWikiValue("Yume Tōrō is a song from the Your Name film."))
        assertFalse(isTechnicalWikiValue("Release Date: August 24, 2016"))
    }

    // ---------------------------------------------------------------- satır yapısı

    @Test
    fun rowTranslationUsesSentinelProofWhenLineCountIsPreserved() = runBlocking {
        val rows = listOf("Kanji", "Rōmaji: Yume Tōrō", "English: Dream Lantern")
        var calls = 0
        val translator: suspend (String) -> String = { joined ->
            calls++
            // Ayırıcıyı koruyan "gerçekçi" Google yanıtı.
            joined.split("⟪§⟫").joinToString("⟪§⟫") { it.trim().let { line -> "[tr] $line" } }
        }
        val translated = translateRowsPreservingStructure(rows, translator)

        assertEquals(1, calls)
        assertEquals(listOf("[tr] Kanji", "[tr] Rōmaji: Yume Tōrō", "[tr] English: Dream Lantern"), translated)
    }

    @Test
    fun rowTranslationFallsBackToPerRowWhenSentinelIsLost() = runBlocking {
        val rows = listOf("Kanji", "Rōmaji: Yume Tōrō", "English: Dream Lantern")
        val calls = mutableListOf<String>()
        val translator: suspend (String) -> String = { value ->
            calls.add(value)
            if (value.contains("⟪§⟫")) "tek satıra indirgenmiş cevap" else "[tr] $value"
        }
        val translated = translateRowsPreservingStructure(rows, translator)

        // İlk çağrı tek istek, sonrası satır satır yedek.
        assertEquals(4, calls.size)
        assertEquals(listOf("[tr] Kanji", "[tr] Rōmaji: Yume Tōrō", "[tr] English: Dream Lantern"), translated)
    }

    @Test
    fun rowTranslationKeepsRowCountWhenTranslatorFails() = runBlocking {
        val rows = listOf("Kanji", "Rōmaji: Yume Tōrō")
        val translated = translateRowsPreservingStructure(rows) { throw IllegalStateException("ağ yok") }
        assertEquals(rows, translated)
    }

    // ---------------------------------------------------------------- kopyalama ve temizlik

    @Test
    fun copyTextKeepsBulletsAndSkipsImages() = runBlocking {
        val content = FandomSectionContent(
            blocks = listOf(
                FandomTextBlock(kind = FandomBlockKind.BULLET, text = "Track Number: 1"),
                image()
            )
        )
        val entries = translateSectionBlocks("sec:0", content) { text -> "[tr] $text" }

        assertEquals("• [tr] Track Number: 1", content.copyTextFor("sec:0", entries, showOriginal = false))
        assertEquals("• Track Number: 1", content.copyTextFor("sec:0", entries, showOriginal = true))
    }

    @Test
    fun copyTextRejectsFlatTranslationWhenStructureExists() {
        val content = FandomSectionContent(blocks = listOf(paragraph("Yume Tōrō")))
        val flat = "Eski önbellekten gelen düz metin çevirisi"
        assertEquals("Yume Tōrō", content.copyTextFor("sec:0", emptyMap(), showOriginal = false, flatFallback = flat))
        assertEquals(flat, FandomSectionContent().copyTextFor("sec:0", emptyMap(), showOriginal = false, flatFallback = flat))
        assertEquals("Yume Tōrō", content.copyTextFor("sec:0", emptyMap(), showOriginal = true, flatFallback = flat))
    }

    @Test
    fun copyTextNeverCopiesTheCompleteMarker() {
        val content = FandomSectionContent(blocks = listOf(paragraph("Yume Tōrō")))
        val marker = WikiStructuredTranslationKeys.COMPLETE
        // Yapısal çeviri yoksa (örn. bölümde yalnızca görsel var) düz metin yedeği işareti
        // panoya taşımamalı; orijinal metin kopyalanır.
        assertEquals("Yume Tōrō", content.copyTextFor("sec:0", mapOf("sec:0" to marker), showOriginal = false, flatFallback = marker))
    }

    @Test
    fun withoutTranslationDropsBaseKeyAndAllBlockChildren() {
        val child = WikiStructuredTranslationKeys.block("sec:0", 3)
        val subtitleChild = WikiStructuredTranslationKeys.subtitle("sec:0", 3)
        val other = WikiStructuredTranslationKeys.block("sec:1", 3)
        val map = mapOf(
            "sec:0" to WikiStructuredTranslationKeys.COMPLETE,
            child to "çevrilmiş blok",
            subtitleChild to "çevrilmiş alt satır",
            other to "başka bölüm",
            "desc" to "açıklama"
        )

        val cleaned = map.withoutTranslation("sec:0")
        assertEquals(setOf(other, "desc"), cleaned.keys)
        assertTrue(WikiStructuredTranslationKeys.belongsTo("sec:0", "sec:0"))
        assertTrue(WikiStructuredTranslationKeys.isChildOf(child, "sec:0"))
        assertFalse(WikiStructuredTranslationKeys.isChildOf(other, "sec:0"))
    }

    @Test
    fun toggleAvailabilitySeesStreamingChildBeforeCompleteMarker() {
        val childOnly = mapOf(WikiStructuredTranslationKeys.block("sec:0", 2) to "çevrilmiş")
        assertTrue(childOnly.hasWikiTranslation("sec:0"))
        assertFalse(childOnly.hasWikiTranslation("sec:1"))
        assertTrue(mapOf("desc" to "çeviri").hasWikiTranslation("desc"))
    }

    // ------------------------------------------------- görsel altı yazısı (figcaption)

    @Test
    fun imageCaptionIsTranslatedWhileTheImageKeepsItsPlaceAndUrl() = runBlocking {
        val baseKey = "reader-section:solo-leveling.fandom.com:1:Sung Jinwoo"
        val content = FandomSectionContent(
            blocks = listOf(
                paragraph("Sung Jinwoo is the protagonist of Solo Leveling."),
                FandomTextBlock(
                    kind = FandomBlockKind.IMAGE,
                    text = "Sung Jinwoo",
                    image = FandomImage(url = "https://static.example/jinwoo.png", caption = "Sung Jinwoo in the anime")
                ),
                paragraph("He becomes the Shadow Monarch.")
            )
        )

        val childKeys = ArrayList<String>()
        val entries = translateSectionBlocks(
            baseKey = baseKey,
            content = content,
            onBlock = { childKey, _ -> childKeys += childKey },
            translate = { text -> "çeviri($text)" }
        )

        assertTrue(childKeys.any { it == WikiStructuredTranslationKeys.caption(baseKey, 1) })
        assertTrue(entries.hasWikiTranslation(baseKey))

        val restored = content.withTranslatedBlocks(baseKey, entries)
        assertNotNull(restored)
        // Blok sayısı, sırası ve görselin URL'si değişmez; yalnızca beyaz metinler çevrilir.
        assertEquals(3, restored!!.blocks.size)
        assertEquals(FandomBlockKind.IMAGE, restored.blocks[1].kind)
        assertEquals("https://static.example/jinwoo.png", restored.blocks[1].image?.url)
        assertEquals("çeviri(Sung Jinwoo in the anime)", restored.blocks[1].image?.caption)
        assertEquals("çeviri(Sung Jinwoo is the protagonist of Solo Leveling.)", restored.blocks[0].text)
    }

    @Test
    fun technicalImageCaptionsAreNotTranslated() = runBlocking {
        val baseKey = "sec:0"
        val content = FandomSectionContent(
            blocks = listOf(
                FandomTextBlock(
                    kind = FandomBlockKind.IMAGE,
                    text = "",
                    image = FandomImage(url = "https://static.example/a.png", caption = "File:Jinwoo.png")
                )
            )
        )
        val entries = translateSectionBlocks(baseKey, content) { text -> "çeviri($text)" }
        assertFalse(entries.hasWikiTranslation(baseKey))
        assertTrue(entries.isEmpty())
    }

    // ------------------------------------------------- okuyucu (reader) anahtarları

    @Test
    fun readerKeysSeparateDescriptionSectionsAndContents() {
        val location = FandomReaderLocation("solo-leveling.fandom.com", "Sung Jinwoo")
        val descriptionKey = FandomReaderTranslationKeys.description(location)
        val contentsKey = FandomReaderTranslationKeys.contents(location)
        val sectionKey = FandomReaderTranslationKeys.section(location, 2)

        assertTrue(
            listOf(descriptionKey, contentsKey, sectionKey).all { FandomReaderTranslationKeys.isReaderKey(it) }
        )
        assertFalse(FandomReaderTranslationKeys.isReaderKey("sec:0"))

        // İçindekiler çevirisi (satır başına çocuk anahtar) yalnızca kendi yüzeyini açar.
        val translations = mapOf(
            WikiStructuredTranslationKeys.block(contentsKey, 0) to "Hikâye",
            WikiStructuredTranslationKeys.block(contentsKey, 1) to "Karakterler"
        )
        assertTrue(translations.hasWikiTranslation(contentsKey))
        assertFalse(translations.hasWikiTranslation(sectionKey))
        assertFalse(translations.hasWikiTranslation(descriptionKey))

        // Geçiş "Orijinal"e alındığında içindekiler çevirisi de bırakılır.
        assertTrue(translations.withoutTranslation(contentsKey).isEmpty())
    }

    @Test
    fun completeMarkerIsDistinguishedFromRealTranslations() {
        assertTrue(WikiStructuredTranslationKeys.isCompleteMarker(WikiStructuredTranslationKeys.COMPLETE))
        assertTrue(WikiStructuredTranslationKeys.isCompleteMarker("1"))
        assertFalse(WikiStructuredTranslationKeys.isCompleteMarker("Yume Tōrō çevirisi"))
        assertFalse(WikiStructuredTranslationKeys.isCompleteMarker(null))
        assertFalse(WikiStructuredTranslationKeys.isCompleteMarker("11"))
    }

    @Test
    fun linkOnlyParagraphAndLinkedCardNeverCallTranslator() = runBlocking {
        val links = listOf(FandomBlockLink("Rick", "https://wiki/Rick"), FandomBlockLink("Morty", "https://wiki/Morty"))
        val content = FandomSectionContent(blocks = listOf(
            paragraph("Rick · Morty", links),
            FandomTextBlock(FandomBlockKind.CARD, "Read more", href = "https://wiki/Page")
        ))
        val requests = mutableListOf<String>()
        assertTrue(translateSectionBlocks("page", content) { requests += it; "translated" }.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun captionsAndSubtitlesTranslateInPlaceWithoutTranslatingTheirLinks() = runBlocking {
        val link = FandomBlockLink("Rick", "https://wiki/Rick")
        val content = FandomSectionContent(blocks = listOf(
            FandomTextBlock(FandomBlockKind.IMAGE, "", image = FandomImage("https://img/1.png", "Follow Rick today"),
                href = "https://wiki/Characters", links = listOf(link)),
            FandomTextBlock(FandomBlockKind.CARD, "Characters", subtitle = "Follow Rick today",
                href = "https://wiki/Characters", links = listOf(link))
        ))
        val requests = mutableListOf<String>()
        val entries = translateSectionBlocks("page", content) { requests += it; "TR($it)" }
        val result = content.withTranslatedBlocks("page", entries)!!
        assertEquals("TR(Follow) Rick TR(today)", result.blocks[0].image?.caption)
        assertEquals("TR(Follow) Rick TR(today)", result.blocks[1].subtitle)
        assertEquals("Characters", result.blocks[1].text)
        assertEquals(content.blocks.map { it.href }, result.blocks.map { it.href })
        assertEquals(content.blocks.map { it.links }, result.blocks.map { it.links })
        assertEquals(content.blocks.map { it.image?.url }, result.blocks.map { it.image?.url })
        assertTrue(requests.none { it.contains("Rick") || it.contains("Characters") || it.contains("https:") })
        assertTrue(content.wikiTranslationSourceText().contains("Follow Rick today"))
    }

    @Test
    fun cachedTranslationsCannotOverwriteLinkedTitlesOrDropLabels() {
        val content = FandomSectionContent(blocks = listOf(
            paragraph("Follow Rick today", listOf(FandomBlockLink("Rick", "https://wiki/Rick"))),
            FandomTextBlock(FandomBlockKind.CARD, "Read more", href = "https://wiki/Page")
        ))
        assertNull(content.withTranslatedBlocks("page", mapOf(
            WikiStructuredTranslationKeys.block("page", 0) to "Bugün takip et",
            WikiStructuredTranslationKeys.block("page", 1) to "Devamını oku"
        )))
    }

    @Test
    fun repeatedAndOverlappingLabelsKeepTheirOrderAndClickTargets() {
        val rick = FandomBlockLink("Rick", "https://wiki/Rick")
        val show = FandomBlockLink("Rick and Morty", "https://wiki/Show")
        val another = FandomBlockLink("Rick", "https://wiki/Another_Rick")
        val text = "Rick and Morty · Rick · Rick"
        val ranges = wikiLinkRanges(text, listOf(rick, show, another))
        assertEquals(listOf("Rick and Morty", "Rick", "Rick"), ranges.map { text.substring(it.range) })
        assertEquals(listOf(show.url, rick.url, another.url), ranges.map { it.link.url })
    }

    @Test
    fun labelsDoNotProtectUnrelatedLatinWordsButSupportAdjacentCjk() {
        val ranges = wikiLinkRanges("Rickshaw Rick", listOf(FandomBlockLink("Rick", "https://wiki/Rick")))
        assertEquals(listOf(9..12), ranges.map { it.range })
        val cjk = wikiLinkRanges("これは東京です", listOf(FandomBlockLink("東京", "https://wiki/Tokyo")))
        assertEquals(listOf(3..4), cjk.map { it.range })
    }

    @Test
    fun failedAndBlankSegmentsKeepWhitespaceAndOtherSegmentsContinue() = runBlocking {
        val content = FandomSectionContent(blocks = listOf(paragraph(
            "Before  Rick\n after", listOf(FandomBlockLink("Rick", "https://wiki/Rick"))
        )))
        val entries = translateSectionBlocks("page", content) { if (it == "Before") "" else "sonra" }
        assertEquals("Before  Rick\n sonra", content.withTranslatedBlocks("page", entries)!!.blocks.single().text)
    }

    @Test
    fun cancellationStopsTranslationRatherThanBecomingOriginalText() = runBlocking {
        var cancelled = false
        try {
            translateSectionBlocks("page", FandomSectionContent(blocks = listOf(paragraph("Some text")))) {
                throw kotlinx.coroutines.CancellationException("page closed")
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun manyRepeatedLinksRemainProtectedWithoutTokenLimits() = runBlocking {
        val link = FandomBlockLink("Rick", "https://wiki/Rick")
        val original = List(40) { "Rick is here." }.joinToString(" ")
        val content = FandomSectionContent(blocks = listOf(paragraph(original, listOf(link))))
        val entries = translateSectionBlocks("page", content) { segment ->
            assertFalse(segment.contains("Rick"))
            "burada."
        }
        val translated = content.withTranslatedBlocks("page", entries)!!.blocks.single().text
        assertEquals(40, wikiLinkRanges(translated, listOf(link)).size)
    }

    @Test
    fun plainGroupAndUnlinkedCardTitlesTranslateEvenWhenChipsHaveSameLabel() = runBlocking {
        val link = FandomBlockLink("Characters", "https://wiki/Characters")
        val content = FandomSectionContent(blocks = listOf(
            FandomTextBlock(FandomBlockKind.LINK_GROUP, "Characters", links = listOf(link)),
            FandomTextBlock(FandomBlockKind.CARD, "Explore", subtitle = "See Characters", links = listOf(link))
        ))
        val entries = translateSectionBlocks("page", content) { "TR($it)" }
        val result = content.withTranslatedBlocks("page", entries)!!
        assertEquals("TR(Characters)", result.blocks[0].text)
        assertEquals(listOf(link), result.blocks[0].links)
        assertEquals("TR(Explore)", result.blocks[1].text)
        assertEquals("TR(See) Characters", result.blocks[1].subtitle)
    }
}

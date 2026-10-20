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
 *
 * "MAVİ METİN" SÖZLEŞMESİ (v2.4.318): satır içi bağlantı etiketleri de çevrilir, ancak
 *  1. etiket tam olarak eski konumunda kalır (`⟦L0⟧…⟦/0⟧` yuvaları),
 *  2. dokunma hedefi (URL) hiç değişmez ve çeviriye hiç gönderilmez,
 *  3. cümle tek parça çevrilir (parça parça çevirinin ürettiği "678 articlesve" gibi
 *     boşluksuz birleşmeler olmaz),
 *  4. jetonlar bozulursa güvenli yedek devreye girer: etiket orijinal kalır, bağlantı yine
 *     tıklanabilir olur — kırık mavi metin asla gösterilmez.
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

        // Bağlantılar (dokunma hedefi) ve etiketleri korunur. Metin tek istekte çevrildiği
        // için markdown jetonları çıkarılmış, etiketler TAM olarak eski yerinde kalmıştır.
        val translatedParagraph = translated.blocks[1]
        assertEquals(content.blocks[1].links, translatedParagraph.links)
        assertEquals("[tr] Yume Tōrō is a song from the Your Name film.", translatedParagraph.text)
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
    fun blockWithLinksIsTranslatedAsOneSentenceAndLabelsKeepTheirSlot() = runBlocking {
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
        val entries = translateSectionBlocks("sec:0", content) { marked ->
            requests += marked
            // Google gibi davran: cümleyi çevir, yuvaları ve içlerindeki adları koru.
            marked.replace("is a song from", "bir şarkıdır")
        }
        val translated = content.withTranslatedBlocks("sec:0", entries)

        assertNotNull(translated)
        assertEquals("Yume Tōrō bir şarkıdır Your Name.", translated!!.blocks.single().text)
        // TEK istek: parça parça çeviri yok (eski davranışta her bağlantı arası ayrı istekti,
        // bu yüzden "678 articles" + "ve" gibi boşluksuz birleşmeler oluşuyordu).
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("⟦L0⟧Yume Tōrō⟦/0⟧"))
        // URL'ler çeviri servisine hiç gönderilmez.
        assertTrue(requests.none { it.contains("https://") })
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
            when {
                // Yuvalı istek bozulmuş gelirse güvenli yedek devreye girer...
                segment.contains("⟦") -> "bozuk yanıt"
                // ...ve yedek yolda bir parçanın hatası diğerlerini iptal etmez.
                segment.startsWith("Before") -> throw IllegalStateException("ilk parça başarısız")
                else -> "sonraki metin."
            }
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
        val linkChild = WikiStructuredTranslationKeys.link("sec:0", 3, 1)
        val other = WikiStructuredTranslationKeys.block("sec:1", 3)
        val map = mapOf(
            "sec:0" to WikiStructuredTranslationKeys.COMPLETE,
            child to "çevrilmiş blok",
            subtitleChild to "çevrilmiş alt satır",
            linkChild to "çevrilmiş etiket",
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
    fun linkOnlyParagraphCostsAtMostOneRequestAndKeepsEverything() = runBlocking {
        val links = listOf(FandomBlockLink("Rick", "https://wiki/Rick"), FandomBlockLink("Morty", "https://wiki/Morty"))
        val content = FandomSectionContent(blocks = listOf(
            paragraph("Rick · Morty", links),
            FandomTextBlock(FandomBlockKind.CARD, "Read more", href = "https://wiki/Page")
        ))
        val requests = mutableListOf<String>()
        // Sağlayıcı adları olduğu gibi döndürür (Google'ın özel ad davranışı).
        val entries = translateSectionBlocks("page", content) { requests += it; it }
        // Değişen bir şey yok: ne metin ne etiket anahtarı yazılır, mavi alanlar dokunulmaz.
        assertTrue(entries.isEmpty())
        // Yalnızca bağlantı içeren paragraf TEK istek harcar; kart başlığı (sayfa adı) hiç gitmez.
        assertEquals(1, requests.size)
        assertTrue(requests.none { it.contains("https://") || it.contains("Read more") })
    }

    @Test
    fun captionsAndSubtitlesTranslateInPlaceWithoutBreakingTheirLinks() = runBlocking {
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
        // Tek istekte çevrildiği için boşluklar da korunur; etiket yerinde kalır.
        assertEquals("TR(Follow Rick today)", result.blocks[0].image?.caption)
        assertEquals("TR(Follow Rick today)", result.blocks[1].subtitle)
        assertEquals("Characters", result.blocks[1].text)
        assertEquals(content.blocks.map { it.href }, result.blocks.map { it.href })
        assertEquals(content.blocks.map { it.links }, result.blocks.map { it.links })
        assertEquals(content.blocks.map { it.image?.url }, result.blocks.map { it.image?.url })
        // Kart başlığı sayfa adıdır ve hiç gönderilmez; URL'ler de hiç gönderilmez.
        assertTrue(requests.none { it.contains("Characters") || it.contains("https:") })
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
        val requests = mutableListOf<String>()
        val entries = translateSectionBlocks("page", content) { marked ->
            requests += marked
            assertEquals(40, Regex("""⟦L\d+⟧""").findAll(marked).count())
            marked.replace("is here.", "buradadır.")
        }
        // 40 tekrar da tek istekte gider; hepsi kendi yuvasında döner.
        assertEquals(1, requests.size)
        val translated = content.withTranslatedBlocks("page", entries)!!.blocks.single().text
        assertEquals(40, wikiLinkRanges(translated, listOf(link)).size)
    }

    // ------------------------------------------------- mavi metin (bağlantı etiketi) çevirisi

    @Test
    fun inlineLinkLabelsAreTranslatedInPlaceAndUrlsNeverChange() = runBlocking {
        val links = listOf(
            FandomBlockLink("Characters", "https://date-a-live.fandom.com/wiki/Characters"),
            FandomBlockLink("Terminology", "https://date-a-live.fandom.com/wiki/Terminology")
        )
        val content = FandomSectionContent(
            blocks = listOf(paragraph("Read the Characters and Terminology pages.", links))
        )
        val requests = mutableListOf<String>()
        val entries = translateSectionBlocks("reader-section:date-a-live:1:Home", content) { marked ->
            requests += marked
            // Google'ın gerçekçi yanıtı: yuvalar ve etiketler yerinde, cümle çevrilmiş.
            "⟦L0⟧Karakterler⟦/0⟧ ve ⟦L1⟧Terminoloji⟦/1⟧ sayfalarını okuyun."
        }

        // URL'ler ve HTML/jetonlar sağlayıcıya gitmez.
        assertTrue(requests.all { !it.contains("https://") && !it.contains("fandom.com") })

        val result = content.withTranslatedBlocks("reader-section:date-a-live:1:Home", entries)
        assertNotNull(result)
        val block = result!!.blocks.single()
        assertEquals("Karakterler ve Terminoloji sayfalarını okuyun.", block.text)

        // Dokunma hedefleri birebir aynı; yalnızca görünen etiket değişti.
        assertEquals(links.map { it.url }, block.links.map { it.url })
        assertEquals(listOf("Karakterler", "Terminoloji"), block.links.map { it.label })

        // Çizici, çevrilmiş etiketleri metinde bulup TIKLANABİLİR hâle getirir.
        val ranges = wikiLinkRanges(block.text, block.links)
        assertEquals(listOf("Karakterler", "Terminoloji"), ranges.map { block.text.substring(it.range) })
        assertEquals(links.map { it.url }, ranges.map { it.link.url })
    }

    @Test
    fun translatedLabelsArePersistedAsChildKeysSoTheCacheRoundTripKeepsLinks() = runBlocking {
        val baseKey = "sec:7"
        val link = FandomBlockLink("Characters", "https://wiki/Characters")
        val content = FandomSectionContent(blocks = listOf(paragraph("Characters of the story", listOf(link))))
        val entries = translateSectionBlocks(baseKey, content) { "⟦L0⟧Karakterler⟦/0⟧ hikâyesi" }

        // Etiket anahtarı metinle AYNI çeviri haritasında yaşar.
        assertEquals("Karakterler", entries[WikiStructuredTranslationKeys.link(baseKey, 0, 0)])
        assertTrue(entries.hasWikiTranslation(baseKey))

        // "Orijinali göster" hem metni hem etiketi birlikte bırakır.
        assertTrue(entries.withoutTranslation(baseKey).isEmpty())

        // Eski önbellekten yalnızca metin gelirse (etiket anahtarı yok) çeviri reddedilir:
        // mavi alan kaybolmaz, orijinal gösterilir.
        val legacy = mapOf(WikiStructuredTranslationKeys.block(baseKey, 0) to "Karakterler hikâyesi")
        assertNull(content.withTranslatedBlocks(baseKey, legacy))
    }

    @Test
    fun brokenSlotsFallBackToUntranslatedLabelsWithoutLosingTargets() = runBlocking {
        val links = listOf(
            FandomBlockLink("Characters", "https://wiki/Characters"),
            FandomBlockLink("Terminology", "https://wiki/Terminology")
        )
        val content = FandomSectionContent(
            blocks = listOf(paragraph("Read the Characters and Terminology pages.", links))
        )
        val requests = mutableListOf<String>()
        val entries = translateSectionBlocks("page", content) { value ->
            requests += value
            when {
                // Sağlayıcı bir yuvayı düşürdü: eksik jetonla devam etmek riskli.
                value.contains("⟦") -> "Karakterler ve ⟦L1⟧Terminoloji⟦/1⟧ sayfaları."
                else -> "TR($value)"
            }
        }

        val result = content.withTranslatedBlocks("page", entries)
        assertNotNull(result)
        val block = result!!.blocks.single()
        // Etiketler orijinal kaldı (çevrilmedi) ama KONUM ve DOKUNMA HEDEFİ korunur.
        assertEquals(links, block.links)
        assertEquals(2, wikiLinkRanges(block.text, block.links).size)
        assertTrue(block.text.contains("Characters"))
        // Bozuk jetonlar kullanıcıya asla sızmaz.
        assertFalse(block.text.contains("⟦"))
        assertFalse(block.text.contains("⟧"))
    }

    @Test
    fun translatedLabelKeysAreStreamedBeforeTheTranslatedText() = runBlocking {
        val link = FandomBlockLink("Characters", "https://wiki/Characters")
        val content = FandomSectionContent(blocks = listOf(paragraph("Characters are here", listOf(link))))
        val order = mutableListOf<String>()
        translateSectionBlocks(
            baseKey = "page",
            content = content,
            onBlock = { key, _ -> order += key }
        ) { "⟦L0⟧Karakterler⟦/0⟧ burada" }

        // Metin göründüğü ilk karede etiket anahtarı çoktan yazılmış olmalı; yoksa mavi alan
        // bir an tıklanamaz görünürdü.
        assertEquals(
            listOf(
                WikiStructuredTranslationKeys.link("page", 0, 0),
                WikiStructuredTranslationKeys.block("page", 0)
            ),
            order
        )
    }

    @Test
    fun linkedCardTitleThatIsARealSentenceIsTranslatedButKeepsItsHref() = runBlocking {
        val card = FandomTextBlock(
            kind = FandomBlockKind.CARD,
            text = "This wiki is dedicated to everything related to the series Date A Live, that anyone can edit!",
            href = "https://date-a-live.fandom.com/wiki/Date_A_Live_Wiki",
            subtitle = "Steam page"
        )
        val content = FandomSectionContent(blocks = listOf(card))
        val entries = translateSectionBlocks("page", content) { "TR($it)" }

        val result = content.withTranslatedBlocks("page", entries)!!.blocks.single()
        assertTrue(result.text.startsWith("TR("))
        assertEquals(card.href, result.href)
        assertEquals("TR(Steam page)", result.subtitle)
    }

    @Test
    fun shortLinkedCardTitleStaysAsAPageName() = runBlocking {
        val card = FandomTextBlock(
            kind = FandomBlockKind.CARD,
            text = "Kimi no Na wa. Soundtracks",
            href = "https://wiki/Soundtracks"
        )
        val content = FandomSectionContent(blocks = listOf(card))
        val requests = mutableListOf<String>()
        val entries = translateSectionBlocks("page", content) { requests += it; "TR($it)" }
        assertTrue(entries.isEmpty())
        assertTrue(requests.isEmpty())
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
        assertEquals("TR(See Characters)", result.blocks[1].subtitle)
    }
}

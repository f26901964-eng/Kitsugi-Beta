package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canlı tr.wikipedia ölçümleri (madde "Titana Saldırı") bu kuralların gerekçesidir:
 * bilgi kutusu şablonları ülke/bölge bağlantısı üretiyor ve API yanıtı pageid sırasıyla
 * geldiği için "en eski" maddeler şeridin başına geçiyordu.
 */
class WikipediaRelatedSelectionTest {

    @Test
    fun `country language and year titles are never offered as related pages`() {
        listOf(
            "Amerika Birleşik Devletleri", "Kanada", "Türkiye", "Kuzey Amerika",
            "Güneydoğu Asya", "Avustralasya", "Birleşik Krallık", "Japonya",
            "Japonca", "Japoncanın romanizasyonu", "İngilizce", "Anime", "Manga",
            "Tankōbon", "Light Novel", "2013", "2010'lar", "IMDb", "Otorite kontrolü"
        ).forEach { title ->
            assertTrue("genel başlık elenmeliydi: $title", WikipediaRelatedSelection.isGenericTitle(title))
        }
    }

    @Test
    fun `specific works characters and studios stay`() {
        listOf(
            "Eren Yeager", "Mikasa Ackerman", "Hajime Isayama", "Kodansha",
            "Bessatsu Shōnen Magazine", "Wit Studio", "MAPPA",
            "Attack on Titan: Lost Girls", "Titana Saldırı: Çöküşten Önce",
            "Attack on Titan cilt listesi"
        ).forEach { title ->
            assertFalse("özgül başlık korunmalıydı: $title", WikipediaRelatedSelection.isGenericTitle(title))
        }
    }

    @Test
    fun `lead frequency and body frequency decide the order and self titles drop out`() {
        val lead = linkedMapOf(
            "Kodansha" to 1,
            "Hajime Isayama" to 1,
            "Amerika Birleşik Devletleri" to 2,
            "Eren Yeager" to 1
        )
        val body = linkedMapOf(
            "Eren Yeager" to 5,
            "Mikasa Ackerman" to 3,
            "Nördlingen" to 1
        )
        val ranked = WikipediaRelatedSelection.rank(
            leadCounts = lead,
            bodyCounts = body,
            selfTitles = setOf("Titana Saldırı", "Attack on Titan"),
            workTitles = listOf("Attack on Titan"),
            limit = 10
        )
        assertEquals("Eren Yeager", ranked.first())
        assertTrue(ranked.contains("Hajime Isayama"))
        assertTrue(ranked.contains("Kodansha"))
        assertFalse(ranked.contains("Amerika Birleşik Devletleri"))
        assertFalse(ranked.contains("Titana Saldırı"))
    }

    @Test
    fun `articles carrying the work name outrank equally frequent generic prose links`() {
        val ranked = WikipediaRelatedSelection.rank(
            leadCounts = linkedMapOf("Attack on Titan: Lost Girls" to 1, "Some Filler Article" to 3),
            bodyCounts = emptyMap(),
            selfTitles = setOf("Titana Saldırı"),
            workTitles = listOf("Attack on Titan"),
            limit = 10
        )
        assertEquals("Attack on Titan: Lost Girls", ranked.first())
    }

    /**
     * Vikipedi kısa tanımları 2026-10-16 tarihinde tr.wikipedia'dan canlı okundu
     * (`prop=description`): karar bu tanımlara dayanır, özet metne değil.
     */
    @Test
    fun `short descriptions of places languages and dates are generic`() {
        listOf(
            "Kuzey Amerika'da bir ülke", "Japonya'nın başkenti", "Doğu Asya dili",
            "Bavyera'da bağımsız şehir (Almanya)", "Almanya'da şehir",
            "country in North America", "island country in East Asia", "calendar year"
        ).forEach { description ->
            assertTrue(
                "genel konu elenmeliydi: $description",
                WikipediaRelatedSelection.looksGenericTopic(description, null)
            )
        }
    }

    @Test
    fun `short descriptions of studios publishers and characters are kept`() {
        listOf(
            "Japon yayınevi", "Japon animasyon stüdyosu", "Japon manga sanatçısı",
            "Titana Saldırı anime ve manga serisindeki kurgusal karakter",
            "Japanese animation studio", "manga series by Hajime Isayama"
        ).forEach { description ->
            assertFalse(
                "özgül konu korunmalıydı: $description",
                WikipediaRelatedSelection.looksGenericTopic(description, null)
            )
        }
    }

    @Test
    fun `missing short description falls back to the extract text`() {
        assertTrue(
            WikipediaRelatedSelection.looksGenericTopic(
                null,
                "Kanada, Kuzey Amerika'da bir ülkedir. On eyalet ve üç bölgeden oluşur."
            )
        )
        assertFalse(
            WikipediaRelatedSelection.looksGenericTopic(null, "Eren Yeager, Titana Saldırı'nın başkahramanıdır.")
        )
    }

    @Test
    fun `descriptions declaring a country or city are filtered out`() {
        assertTrue(
            WikipediaRelatedSelection.looksGenericDescription(
                "Kanada (İngilizce: ; Fransızca: ), Kuzey Amerika'da bir ülkedir. On eyalet ve üç bölgeden oluşur."
            )
        )
        assertTrue(
            WikipediaRelatedSelection.looksGenericDescription(
                "Nördlingen, Almanya'nın Bavyera eyaletinde bulunan bir şehirdir."
            )
        )
        assertTrue(
            WikipediaRelatedSelection.looksGenericDescription(
                "The United States of America is a country in North America."
            )
        )
    }

    @Test
    fun `descriptions of characters studios and works are kept`() {
        assertFalse(
            WikipediaRelatedSelection.looksGenericDescription(
                "Eren Yeager ya da Eren Jeager, Hajime Isayama'nın Titana Saldırı animesinin başkahramanıdır."
            )
        )
        assertFalse(
            WikipediaRelatedSelection.looksGenericDescription(
                "MAPPA Co., Ltd., Japonya merkezli bir animasyon stüdyosudur. Merkezi Tokyo'dadır."
            )
        )
        assertFalse(
            WikipediaRelatedSelection.looksGenericDescription(
                "Hajime İsayama, Japon manga sanatçısıdır. İlk serisi Titana Saldırı'dır."
            )
        )
        assertFalse(WikipediaRelatedSelection.looksGenericDescription(null))
    }
}

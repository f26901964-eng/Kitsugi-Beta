package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bölüm numarası çözümü — kullanıcının bildirdiği "bölüm sayısı sıralaması
 * bozuk / 1'den başlamıyor" hatasının regresyon testleri.
 */
class MangaChapterNumberResolverTest {

    @Test
    fun declaredNumberWins() {
        assertEquals(
            12f,
            MangaChapterNumberResolver.resolve("Seri", "Bir şeyler", 12f),
            0.001f
        )
    }

    @Test
    fun turkishChapterPrefixIsParsed() {
        // "Bölüm 137 - Chu Chu Sevgi..." → 137 (screenshot'taki gerçek metin)
        assertEquals(
            137f,
            MangaChapterNumberResolver.resolve("Seri", "Bölüm 137 - Chu Chu Sevgi Muni Muni", -1f),
            0.001f
        )
    }

    @Test
    fun turkishAbbreviationsAreParsed() {
        assertEquals(134f, MangaChapterNumberResolver.resolve("X", "Bölüm 134 - Sıradan Mutluluk", -1f), 0.001f)
        assertEquals(107f, MangaChapterNumberResolver.resolve("X", "Bolum 107 Okula Saldırı", -1f), 0.001f)
        assertEquals(105f, MangaChapterNumberResolver.resolve("X", "Böl.105 Sır", -1f), 0.001f)
    }

    @Test
    fun englishChapterNamesAreParsed() {
        assertEquals(4f, MangaChapterNumberResolver.resolve("Mokushiroku Alice", "Ch. 4: Misrepresentation", -1f), 0.001f)
        assertEquals(12.5f, MangaChapterNumberResolver.resolve("X", "Chapter 12.5", -1f), 0.001f)
    }

    @Test
    fun leadingNumberIsUsedAsLastResort() {
        assertEquals(42f, MangaChapterNumberResolver.resolve("X", "42 - Başlangıç", -1f), 0.001f)
    }

    @Test
    fun unknownChapterReturnsNegative() {
        val resolved = MangaChapterNumberResolver.resolve("X", "Duyuru", -1f)
        assertTrue("Numara yoksa negatif kalmalı", resolved < 0f)
        assertNull("Etiket üretilmemeli", MangaChapterNumberResolver.label(resolved))
    }

    @Test
    fun labelsAreHumanReadable() {
        assertEquals("137", MangaChapterNumberResolver.label(137f))
        assertEquals("12.5", MangaChapterNumberResolver.label(12.5f))
        assertEquals("1", MangaChapterNumberResolver.label(1f))
        assertNull(MangaChapterNumberResolver.label(-1f))
    }

    @Test
    fun volumeIsExtractedFromName() {
        assertEquals(3, MangaChapterNumberResolver.volumeOf("Cilt 3 - Bölüm 21"))
        assertEquals(1, MangaChapterNumberResolver.volumeOf("Vol.1 Ch. 4"))
        assertNull(MangaChapterNumberResolver.volumeOf("Bölüm 12"))
    }
}

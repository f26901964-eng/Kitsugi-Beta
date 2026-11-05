package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tür algılama + önerilen okuma modu (manhwa → webtoon vb.) testleri.
 */
class MangaKindDetectorTest {

    @Test
    fun manhwaGenreSelectsWebtoonMode() {
        val kind = MangaKindDetector.detect("Kaynak", "Solo Leveling", listOf("Manhwa", "Aksiyon"))
        assertEquals(MangaKindDetector.Kind.Manhwa, kind)
        assertEquals(ReadingMode.Webtoon, MangaKindDetector.recommendedMode(kind))
    }

    @Test
    fun manhuaGenreSelectsLeftToRight() {
        val kind = MangaKindDetector.detect("Kaynak", "X", listOf("Manhua"))
        assertEquals(MangaKindDetector.Kind.Manhua, kind)
        assertEquals(ReadingMode.LeftToRight, MangaKindDetector.recommendedMode(kind))
    }

    @Test
    fun novelSourceDetectedAsLightNovel() {
        val kind = MangaKindDetector.detect("ARAZNOVEL", "Re:Zero", listOf("Fantastik"))
        assertEquals(MangaKindDetector.Kind.LightNovel, kind)
    }

    @Test
    fun novelTagDetected() {
        val kind = MangaKindDetector.detect("Bilinmeyen", "X", listOf("Light Novel"))
        assertEquals(MangaKindDetector.Kind.LightNovel, kind)
    }

    @Test
    fun singleHtmlChapterIsNovel() {
        val kind = MangaKindDetector.detect(
            "Kaynak", "X", emptyList(), chapterPageCount = 1, singleHtmlChapter = true
        )
        assertEquals(MangaKindDetector.Kind.LightNovel, kind)
    }

    @Test
    fun plainMangaStaysManga() {
        val kind = MangaKindDetector.detect("MangaDex", "Berserk", listOf("Action"))
        assertEquals(MangaKindDetector.Kind.Unknown, kind)
        assertEquals(ReadingMode.RightToLeft, MangaKindDetector.recommendedMode(kind))
    }

    @Test
    fun describeIsHumanReadable() {
        assertEquals("Manhwa · dikey kaydırma", MangaKindDetector.describe(MangaKindDetector.Kind.Manhwa))
    }
}

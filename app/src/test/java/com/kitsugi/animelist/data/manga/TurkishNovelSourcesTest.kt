package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishNovelSourcesTest {

    @Test
    fun novelSourcesAreDetected() {
        assertTrue(TurkishNovelSources.isNovelSource("ArazNovel"))
        assertTrue(TurkishNovelSources.isNovelSource("NovelTR"))
        assertTrue(TurkishNovelSources.isNovelSource("Light Novel Türkiye"))
    }

    @Test
    fun mangaSourcesAreNotNovels() {
        assertFalse(TurkishNovelSources.isNovelSource("MangaTR"))
        assertFalse(TurkishNovelSources.isNovelSource("Garcia Fansub"))
        assertFalse(TurkishNovelSources.isNovelSource(""))
    }

    @Test
    fun metaChaptersAreFlagged() {
        assertTrue(TurkishNovelSources.isMetaChapter("Ön Söz"))
        assertTrue(TurkishNovelSources.isMetaChapter("Yazarın Notu"))
        assertTrue(TurkishNovelSources.isMetaChapter("SSS"))
        assertFalse(TurkishNovelSources.isMetaChapter("Bölüm 12"))
    }
}

package com.kitsugi.animelist.data.manga

import org.junit.Assert.*
import org.junit.Test

class MangaTrPolicyTest {
    @Test fun loginRequiresTheRightSourceAndMessage() {
        assertTrue(isMangaTrLoginRequired("eu.kanade.tachiyomi.extension.tr.mangatr", "WebView üzerinden üye girişi yapmanız gerekmektedir"))
        assertFalse(isMangaTrLoginRequired("other.source", "üye girişi"))
        assertFalse(isMangaTrLoginRequired("eu.kanade.tachiyomi.extension.tr.mangatr", "captcha"))
    }

    @Test fun automaticGuessedMirrorsAreDisabledOnlyForMangaTr() {
        assertFalse(allowsAutomaticMangaMirror("eu.kanade.tachiyomi.extension.tr.mangatr"))
        assertFalse(allowsAutomaticMangaMirror("EU.KANADE.TACHIYOMI.EXTENSION.TR.MANGATR"))
        assertTrue(allowsAutomaticMangaMirror("eu.kanade.tachiyomi.extension.tr.other"))
    }

    @Test fun hostMatchingIsBoundedToRealDomains() {
        assertTrue(isMangaTrHost("forum.manga-tr.com"))
        assertTrue(isMangaTrHost("image.mngtr.site"))
        assertFalse(isMangaTrHost("manga-tr.com.example.org"))
        assertFalse(isMangaTrHost("notmngtr.site"))
    }

    @Test fun transformedCachePreservesOrderFragmentAndInvalidatesLegacy() {
        val a = "https://image.mngtr.site/a.webp#100,200"
        val b = "https://image.mngtr.site/a.webp#200,100"
        val pkg = "eu.kanade.tachiyomi.extension.tr.mangatr"
        assertNotEquals(a, mangaImageCacheKey(pkg, a))
        assertNotEquals(mangaImageCacheKey(pkg, a), mangaImageCacheKey(pkg, b))
        assertEquals(a, mangaImageCacheKey("other", a))
    }
    @Test fun rejectsApkWithMislabelledVersionOrPackage() {
        val pkg = "eu.kanade.tachiyomi.extension.tr.mangatr"
        assertTrue(matchesMangaApkMetadata(pkg, 106025, pkg, 106025))
        assertFalse(matchesMangaApkMetadata(pkg, 106026, pkg, 106024))
        assertFalse(matchesMangaApkMetadata(pkg, 106025, "other", 106025))
        assertTrue(matchesMangaApkMetadata(pkg, 0, pkg, 106024))
    }

    @Test fun chapterSearchKeepsDistinctTranslations() {
        val chapters = listOf(
            MangaChapter("/id-1", "1. Bölüm", 1f, "Bağımsız"),
            MangaChapter("/id-2", "1. Bölüm", 1f, "Başka Grup"),
        )
        assertEquals(2, chapters.filter { it.matchesChapterQuery("1") }.size)
        assertEquals("/id-2", chapters.single { it.matchesChapterQuery("Başka") }.url)
    }
}

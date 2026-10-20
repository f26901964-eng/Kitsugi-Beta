package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.data.remote.BangumiNameLocalizer
import com.kitsugi.animelist.data.remote.BangumiTitleCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * İçe aktarımda başlık üçlüsünün kurulması ([BangumiImportManager.resolveImportTitles]).
 *
 * Bangumi koleksiyon ucu kısaltılmış subject döndürür (infobox yok) → yerelleştirici çoğu
 * zaman yalnızca özgün (Japonca/Çince) adı verebilir. Daha önce başka bir ekranda çözülmüş
 * Latin/İngilizce ad kalıcı önbellekteyse satır, seçili başlık diline ilk andan uyacak
 * şekilde yazılmalıdır (v2.4.315).
 */
class BangumiImportTitlesTest {

    @Test
    fun slimSubjectWithoutCacheKeepsNativeBehavior() {
        val localized = BangumiNameLocalizer.entity("デュラララ!!", "无头骑士异闻录")

        val titles = BangumiImportManager.resolveImportTitles(
            localized = localized,
            cached = null,
            fallbackTitle = "Bangumi #222"
        )

        assertEquals("デュラララ!!", titles.title)
        assertNull(titles.titleEnglish)
        assertEquals("デュラララ!!", titles.titleJapanese)
    }

    @Test
    fun cachedLatinTitlesWinAtImportTime() {
        val localized = BangumiNameLocalizer.entity("デュラララ!!", "无头骑士异闻录")
        val cached = BangumiTitleCache.LatinTitles(
            romaji = "Durarara!!",
            english = "Durarara!!",
            native = "デュラララ!!"
        )

        val titles = BangumiImportManager.resolveImportTitles(
            localized = localized,
            cached = cached,
            fallbackTitle = "Bangumi #222"
        )

        // Ana başlık romaji, İngilizce ayrı alanda, özgün ad kaybolmaz → ekran, başlık
        // dili ENGLISH/ROMAJI/NATIVE ne olursa olsun doğru varyantı seçebilir.
        assertEquals("Durarara!!", titles.title)
        assertEquals("Durarara!!", titles.titleEnglish)
        assertEquals("デュラララ!!", titles.titleJapanese)
    }

    @Test
    fun cachedEnglishIsUsedWhenRomajiIsMissing() {
        val localized = BangumiNameLocalizer.entity("進撃の巨人", "进击的巨人")
        val cached = BangumiTitleCache.LatinTitles(
            romaji = null,
            english = "Attack on Titan",
            native = "進撃の巨人"
        )

        val titles = BangumiImportManager.resolveImportTitles(
            localized = localized,
            cached = cached,
            fallbackTitle = "Bangumi #111"
        )

        assertEquals("Attack on Titan", titles.title)
        assertEquals("Attack on Titan", titles.titleEnglish)
        assertEquals("進撃の巨人", titles.titleJapanese)
    }

    @Test
    fun missingSubjectFallsBackToPlaceholder() {
        val titles = BangumiImportManager.resolveImportTitles(
            localized = null,
            cached = null,
            fallbackTitle = "Bangumi #999"
        )

        assertEquals("Bangumi #999", titles.title)
        assertNull(titles.titleEnglish)
        assertNull(titles.titleJapanese)
    }
}

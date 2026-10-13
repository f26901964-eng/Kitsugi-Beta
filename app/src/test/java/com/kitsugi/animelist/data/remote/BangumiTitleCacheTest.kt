package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiTitleCacheTest {

    @Test
    fun partialUpdateKeepsPreviouslyResolvedEnglishAndNativeTitles() {
        val previous = BangumiTitleCache.LatinTitles(
            romaji = "Shingeki no Kyojin",
            english = "Attack on Titan",
            native = "進撃の巨人"
        )

        val merged = BangumiTitleCache.mergeTitles(
            previous = previous,
            romaji = "Shingeki no Kyojin",
            english = null,
            native = null
        )

        assertEquals(previous, merged)
    }

    @Test
    fun newEnglishCanCompleteRomajiOnlyRecord() {
        val previous = BangumiTitleCache.LatinTitles(
            romaji = "Shingeki no Kyojin",
            english = null,
            native = "進撃の巨人"
        )

        val merged = BangumiTitleCache.mergeTitles(
            previous = previous,
            romaji = null,
            english = "Attack on Titan",
            native = null
        )

        assertEquals("Shingeki no Kyojin", merged.romaji)
        assertEquals("Attack on Titan", merged.english)
        assertEquals("進撃の巨人", merged.native)
    }
}

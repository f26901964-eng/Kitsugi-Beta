package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KitsugiBangumiClientTitleTest {

    private fun cjkResult() = JikanSearchResult(
        malId = BangumiIdNamespace.stableIdFromRaw(1234)!!,
        title = "進撃の巨人",
        subtitle = "TV",
        type = MediaType.Anime,
        total = 25,
        score = null,
        isAdult = false,
        imageUrl = null,
        year = 2013,
        source = BangumiIdNamespace.SOURCE,
        titleJapanese = "進撃の巨人",
        bangumiSubjectType = BangumiApiClient.SubjectType.ANIME
    )

    @Test
    fun appliesEnglishRomajiAndNativeToTheirOwnFields() {
        val resolved = KitsugiBangumiClient.applyLocalizedBangumiTitles(
            cjkResult(),
            BangumiTitleCache.LatinTitles(
                romaji = "Shingeki no Kyojin",
                english = "Attack on Titan",
                native = "進撃の巨人"
            )
        )

        assertEquals("Attack on Titan", resolved.getDisplayTitle("ENGLISH"))
        assertEquals("Shingeki no Kyojin", resolved.getDisplayTitle("ROMAJI"))
        assertEquals("進撃の巨人", resolved.getDisplayTitle("NATIVE"))
    }

    @Test
    fun romajiOnlyFallsBackForEnglishDisplayWithoutClaimingAnEnglishField() {
        val resolved = KitsugiBangumiClient.applyLocalizedBangumiTitles(
            cjkResult(),
            BangumiTitleCache.LatinTitles(
                romaji = "Shingeki no Kyojin",
                english = null,
                native = "進撃の巨人"
            )
        )

        assertEquals("Shingeki no Kyojin", resolved.getDisplayTitle("ENGLISH"))
        assertNull(resolved.titleEnglish)
        assertEquals("Shingeki no Kyojin", resolved.titleRomaji)
    }

    @Test
    fun neverCopiesRomajiIntoEnglishWhenASeparateEnglishTitleIsCached() {
        val item = cjkResult().copy(
            title = "Shingeki no Kyojin",
            titleEnglish = "Shingeki no Kyojin",
            titleRomaji = "Shingeki no Kyojin"
        )
        val resolved = KitsugiBangumiClient.applyLocalizedBangumiTitles(
            item,
            BangumiTitleCache.LatinTitles(
                romaji = "Shingeki no Kyojin",
                english = "Attack on Titan",
                native = "進撃の巨人"
            )
        )

        assertEquals("Attack on Titan", resolved.getDisplayTitle("ENGLISH"))
        assertEquals("Shingeki no Kyojin", resolved.getDisplayTitle("ROMAJI"))
    }
}

package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Listeye Ekle" akışındaki tekrar kontrolü (`MediaEntry.matches`) senaryoları.
 *
 * Hata raporu: Bangumi (CLANNAD 〜AFTER STORY〜, subject 876) detay sayfasında
 * "Listeye Ekle" → "zaten listende var" deniyor ama kullanıcı kaydı Listem'de
 * bulamıyordu. Kök nedenlerden biri, eşlemenin yalnızca ekranda GÖRÜNEN başlığa
 * (`result.title` — başlık diline göre değişir) bakmasıydı; diğeri de çapraz
 * MAL-ID eşleşmesinin aday kaydı isimlendirmeden reddetmesi.
 */
class MediaEntryMatchesTest {

    private fun entry(
        title: String,
        malId: Int? = null,
        year: Int? = null,
        source: String = "manual",
        type: MediaType = MediaType.Anime,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        tmdbId: Int? = null
    ) = MediaEntry(
        id = 1,
        title = title,
        subtitle = "",
        type = type,
        status = WatchStatus.Planned,
        score = null,
        progress = 0,
        total = null,
        source = source,
        malId = malId,
        year = year,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        tmdbId = tmdbId
    )

    private fun result(
        title: String,
        malId: Int,
        year: Int? = null,
        source: String = "bangumi",
        type: MediaType = MediaType.Anime,
        realMalId: Int? = null,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        titleRomaji: String? = null,
        tmdbId: Int? = null
    ) = JikanSearchResult(
        malId = malId,
        title = title,
        subtitle = "TV",
        type = type,
        total = 24,
        score = 9,
        isAdult = false,
        imageUrl = null,
        year = year,
        source = source,
        realMalId = realMalId,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        titleRomaji = titleRomaji,
        tmdbId = tmdbId
    )

    @Test
    fun `bangumi result with resolved MAL id matches the MAL copy in the list`() {
        // Rapor senaryosu: Bangumi AS kaydı (stableId 500M+876), detay zenginleştirmesiyle
        // çözülen gerçek MAL ID (5681) üzerinden listedeki MAL kopyasıyla eşleşmeli.
        val asBangumi = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681
        )
        val malCopy = entry(
            title = "Clannad: After Story",
            malId = 5681,
            year = 2008,
            source = "mal"
        )
        assertTrue(malCopy.matches(asBangumi))
    }

    @Test
    fun `title fallback matches across all title variants not just display title`() {
        // Sonuç başlığı olarak görünen (seçili dile göre değişen) alan Japonca olsa bile,
        // romaji/English varyantı listedeki kayıtla eşleşmeli.
        val asResult = result(
            title = "クラナド ～アフターストーリー～",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            titleJapanese = "クラナド ～アフターストーリー～",
            titleRomaji = "Clannad: After Story"
        )
        val storedLatin = entry(
            title = "CLANNAD 〜AFTER STORY〜",
            year = 2008,
            source = "bangumi"
        )
        assertTrue(storedLatin.matches(asResult))
    }

    @Test
    fun `season one and after story never match each other`() {
        val s1 = entry(
            title = "Clannad",
            malId = 2167,
            year = 2007,
            source = "mal",
            titleEnglish = "Clannad",
            titleJapanese = "クラナド"
        )
        val asResult = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681,
            titleRomaji = "Clannad: After Story",
            titleJapanese = "クラナド ～アフターストーリー～"
        )
        assertFalse(s1.matches(asResult))
    }

    @Test
    fun `tmdb id collision is an identity match`() {
        val withTmdb = entry(title = "Farklı Başlık", source = "simkl", tmdbId = 2167)
        val asResult = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            source = "bangumi",
            tmdbId = 2167
        )
        assertTrue(withTmdb.matches(asResult))
    }

    @Test
    fun `same bangumi stableId is an identity match regardless of title`() {
        val stored = entry(title = "Eski Kayıtlı Ad", malId = 500_000_876, source = "bangumi")
        val asResult = result(title = "Clannad: After Story", malId = 500_000_876, source = "bangumi")
        assertTrue(stored.matches(asResult))
    }

    @Test
    fun `unrelated entries do not match`() {
        val unrelated = entry(title = "Steins;Gate", malId = 9253, year = 2011, source = "mal")
        val asResult = result(
            title = "Clannad: After Story",
            malId = 500_000_876,
            year = 2008,
            source = "bangumi",
            realMalId = 5681
        )
        assertFalse(unrelated.matches(asResult))
    }
}

package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Yeniden içe aktarımda çözülmüş başlıkların korunması ([preserveResolvedTitles]).
 *
 * Kök neden (v2.4.315): Bangumi koleksiyon ucu yalnızca özgün (Japonca/Çince) adı
 * döndürür. `smartImport` güncelleme kolu gelen satırı aynen yazdığı için, daha önce
 * `BangumiLibraryTitleHydrator` ile tamamlanmış Latin/İngilizce adlar her senkronda
 * CJK ada geri dönüyordu — başlık dili İngilizce seçili olsa bile "Listem → Bangumi"
 * yeniden Japonca/Çince görünüyordu, eski/yeni kayıt fark etmeksizin.
 */
class MediaEntryRepositoryTitlePreservationTest {

    private fun entity(
        title: String,
        titleEnglish: String? = null,
        titleJapanese: String? = null
    ) = MediaEntryEntity(
        id = 7,
        title = title,
        subtitle = "bangumi",
        type = "Anime",
        status = "Watching",
        score = null,
        progress = 1,
        total = 26,
        isFavorite = false,
        isAdult = false,
        source = "bangumi",
        malId = 500_000_222,
        imageUrl = null,
        year = 2010,
        synopsis = null,
        startDate = null,
        endDate = null,
        notes = null,
        tags = null,
        priority = null,
        isRepeating = false,
        repeatCount = 0,
        repeatValue = 0,
        volumeProgress = 0,
        isPrivate = false,
        isHiddenFromStatusLists = false,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )

    private fun imported(
        title: String,
        titleEnglish: String? = null,
        titleJapanese: String? = null
    ) = MediaEntry(
        id = 0,
        title = title,
        subtitle = "bangumi",
        type = MediaType.Anime,
        status = WatchStatus.Watching,
        score = null,
        progress = 1,
        total = 26,
        source = "bangumi",
        malId = 500_000_222,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )

    @Test
    fun cjkReimportDoesNotClobberResolvedLatinTitles() {
        // Tamamlayıcı daha önce: title=romaji, titleEnglish=İngilizce, titleJapanese=özgün yazmış.
        val existing = entity(
            title = "Durarara!!",
            titleEnglish = "Durarara!!",
            titleJapanese = "デュラララ!!"
        )
        // Yeni aktarım yalnızca özgün adı getirir (Bangumi koleksiyon ucu).
        val reimported = imported(
            title = "デュラララ!!",
            titleEnglish = null,
            titleJapanese = "デュラララ!!"
        )

        val result = preserveResolvedTitles(existing, reimported)

        assertEquals("Durarara!!", result.title)
        assertEquals("Durarara!!", result.titleEnglish)
        assertEquals("デュラララ!!", result.titleJapanese)
    }

    @Test
    fun cjkReimportKeepsEnglishAndRestoresNativeSearchTerm() {
        val existing = entity(
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            titleJapanese = null
        )
        val reimported = imported(
            title = "進撃の巨人",
            titleEnglish = null,
            titleJapanese = null
        )

        val result = preserveResolvedTitles(existing, reimported)

        assertEquals("Shingeki no Kyojin", result.title)
        assertEquals("Attack on Titan", result.titleEnglish)
        // Özgün ad hem NATIVE gösterimi hem sonraki çözümleme arama terimi için korunur.
        assertEquals("進撃の巨人", result.titleJapanese)
    }

    @Test
    fun latinImportStillWinsOverExisting() {
        val existing = entity(
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            titleJapanese = "進撃の巨人"
        )
        val reimported = imported(
            title = "Shingeki no Kyojin Season 2",
            titleEnglish = "Attack on Titan Season 2",
            titleJapanese = "進撃の巨人 Season2"
        )

        val result = preserveResolvedTitles(existing, reimported)

        assertEquals("Shingeki no Kyojin Season 2", result.title)
        assertEquals("Attack on Titan Season 2", result.titleEnglish)
        assertEquals("進撃の巨人 Season2", result.titleJapanese)
    }

    @Test
    fun blankImportedTitleKeepsExisting() {
        val existing = entity(title = "Angel Beats!")
        val reimported = imported(title = "")

        val result = preserveResolvedTitles(existing, reimported)

        assertEquals("Angel Beats!", result.title)
        assertNull(result.titleJapanese)
    }

    @Test
    fun unresolvedRowsStayAsImported() {
        // Hiçbir Latin ad çözülmediyse kural tabloyu değiştirmez; tamamlama işi
        // BangumiLibraryTitleHydrator'a kalır.
        val existing = entity(title = "東京ミュウミュウ", titleJapanese = "東京ミュウミュウ")
        val reimported = imported(title = "東京ミュウミュウ", titleJapanese = "東京ミュウミュウ")

        val result = preserveResolvedTitles(existing, reimported)

        assertEquals("東京ミュウミュウ", result.title)
        assertNull(result.titleEnglish)
        assertEquals("東京ミュウミュウ", result.titleJapanese)
    }
}

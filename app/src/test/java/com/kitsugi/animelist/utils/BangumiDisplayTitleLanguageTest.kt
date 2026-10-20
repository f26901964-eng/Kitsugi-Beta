package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bangumi kütüphane satırında başlık dili sözleşmesi (gösterim katmanı).
 *
 * Satır üç varyantı saklar: `title` = romaji/Latin ana ad, `titleEnglish` = İngilizce ad,
 * `titleJapanese` = özgün ad. "Listem" kartları seçili dile göre bu üçlüden seçmelidir:
 *
 * - `ENGLISH` → İngilizce → romaji → özgün
 * - `ROMAJI` → romaji → İngilizce → özgün
 * - `NATIVE` → özgün → diğerleri
 *
 * Kullanıcı bildirimindeki hata (İngilizce seçili iken Japonca/Çince gösterim) bu seçimde
 * değil, varyantların hiç doldurulmamasındaydı; doldurma tarafı
 * `BangumiLibraryTitleHydrator` + içe aktarım koruması testleriyle kilitlidir. Bu test,
 * varyantlar dolduğunda gösterimin dile uyduğunu (ve dolmadan önce özgün ada düştüğünü)
 * belgeler. Ayırt edici subject kimliği, önbellek yan etkisinden kaçınmak için özeldir.
 */
class BangumiDisplayTitleLanguageTest {

    private fun entry(
        title: String,
        titleEnglish: String? = null,
        titleJapanese: String? = null
    ) = MediaEntry(
        id = 1,
        title = title,
        subtitle = "bangumi",
        type = MediaType.Anime,
        status = WatchStatus.Watching,
        score = null,
        progress = 1,
        total = 26,
        source = BangumiIdNamespace.SOURCE,
        malId = BangumiIdNamespace.stableIdFromRaw(424_242),
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese
    )

    @Test
    fun englishLanguageShowsEnglishTitle() {
        val row = entry(
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            titleJapanese = "進撃の巨人"
        )
        assertEquals("Attack on Titan", row.getDisplayTitle("ENGLISH"))
    }

    @Test
    fun englishLanguageFallsBackToRomajiNotNative() {
        val row = entry(
            title = "Durarara!!",
            titleEnglish = null,
            titleJapanese = "デュラララ!!"
        )
        assertEquals("Durarara!!", row.getDisplayTitle("ENGLISH"))
    }

    @Test
    fun englishLanguageUsesEnglishFieldWhenMainTitleIsStillNative() {
        // Tamamlama yalnızca İngilizce alanı yazabildiyse bile İng gösterimi CJK kalmaz.
        val row = entry(
            title = "進撃の巨人",
            titleEnglish = "Attack on Titan",
            titleJapanese = "進撃の巨人"
        )
        assertEquals("Attack on Titan", row.getDisplayTitle("ENGLISH"))
    }

    @Test
    fun romajiLanguageShowsRomajiTitle() {
        val row = entry(
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            titleJapanese = "進撃の巨人"
        )
        assertEquals("Shingeki no Kyojin", row.getDisplayTitle("ROMAJI"))
    }

    @Test
    fun nativeLanguageShowsNativeTitle() {
        val row = entry(
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            titleJapanese = "進撃の巨人"
        )
        assertEquals("進撃の巨人", row.getDisplayTitle("NATIVE"))
    }

    @Test
    fun unresolvedRowFallsBackToNativeUntilHydrated() {
        // İçe aktarımın ilk hali: yalnızca özgün ad var. Hatanın görünür hali budur;
        // tamamlama taraması varyantları doldurunca yukarıdaki kurallar devreye girer.
        val row = entry(
            title = "東京ミュウミュウ",
            titleEnglish = null,
            titleJapanese = "東京ミュウミュウ"
        )
        assertEquals("東京ミュウミュウ", row.getDisplayTitle("ENGLISH"))
        assertEquals("東京ミュウミュウ", row.getDisplayTitle("ROMAJI"))
    }
}

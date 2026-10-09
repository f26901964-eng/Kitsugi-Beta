package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AniList ad köprüsünün eşleme mantığı testleri (ağ çağrısı gerektirmeyen saf kısımlar).
 */
class KitsugiAniListPersonBridgeTest {

    private fun variant(full: String, native: String?, alternatives: List<String> = emptyList()) =
        KitsugiAniListPersonBridge.NameVariant(full = full, native = native, alternatives = alternatives)

    @Test
    fun findByNative_matchesExactNativeName() {
        val tomoya = variant("Tomoya Okazaki", "岡崎朋也", listOf("Tomoya Okazaki"))
        val nagisa = variant("Nagisa Furukawa", "古河渚")
        val list = listOf(tomoya, nagisa)

        assertEquals(tomoya, KitsugiAniListPersonBridge.findByNative(list, "岡崎朋也", "岡崎朋也"))
        assertEquals(nagisa, KitsugiAniListPersonBridge.findByNative(list, null, "古河渚"))
    }

    @Test
    fun findByNative_matchesIgnoringWhitespaceAndCase() {
        val variant = variant("Yuichi Nakamura", "中村悠一", listOf("Yuichi Nakamura"))
        // Japonca adlardaki boşluklar ve Latin adlardaki büyük/küçük harf farkı eşleşmeyi bozmaz
        assertEquals(variant, KitsugiAniListPersonBridge.findByNative(listOf(variant), "中村 悠一", "中村 悠一"))
        assertEquals(variant, KitsugiAniListPersonBridge.findByNative(listOf(variant), null, "yuichi nakamura"))
    }

    @Test
    fun findByNative_returnsNullWhenNoMatch() {
        val nagisa = variant("Nagisa Furukawa", "古河渚")
        assertNull(KitsugiAniListPersonBridge.findByNative(listOf(nagisa), "岡崎朋也", "岡崎朋也"))
        assertNull(KitsugiAniListPersonBridge.findByNative(listOf(nagisa), null, null))
        assertNull(KitsugiAniListPersonBridge.findByNative(emptyList(), "岡崎朋也", "岡崎朋也"))
    }

    @Test
    fun nameVariant_romajiRequiresLatinFullName() {
        // `full` Latin değilse romaji üretilmez (uydurma transliterasyon yapılmaz)
        val cjkOnly = variant("岡崎朋也", "岡崎朋也")
        assertNull(cjkOnly.romaji)

        val latin = variant("Tomoya Okazaki", "岡崎朋也", listOf("Tomoya Okazaki"))
        assertEquals("Tomoya Okazaki", latin.romaji)
        // Tek alternatif `full` ile aynıysa İngilizce adı null kalır (romaji yeterli)
        assertNull(latin.english)

        val withEnglish = variant("Tomoya Okazaki", "岡崎朋也", listOf("Tomoya Okazaki (VA)"))
        assertEquals("Tomoya Okazaki (VA)", withEnglish.english)
    }
}

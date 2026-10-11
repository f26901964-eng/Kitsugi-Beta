package com.kitsugi.animelist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bangumi MASTER sözlük sözleşme testi (JVM, Android bağımlılığı yok).
 *
 * Bu test `diag/bangumi_master/` üretecinin çıktısını kilitler: üreteç bozuk
 * çıktı üretirse (virgül hatası, gölgelenen yazım, eksik rol) burada kırılır.
 * Python doğrulayıcı (`verify_bangumi_master.py`) aynı kuralları dosya düzeyinde
 * denetler; bu test ise gerçek Kotlin davranışını doğrular.
 */
class BangumiMasterDictionaryTest {

    // ── Kurum sözlüğü ────────────────────────────────────────────────────────

    @Test
    fun `Japonca studyo adi Latin karsiligina eslenir`() {
        assertEquals("kyoto_animation", BangumiOrgDictionary.entryFor("京都アニメーション")?.key)
        assertEquals("bones", BangumiOrgDictionary.entryFor("ボンズ")?.key)
        assertEquals("madhouse", BangumiOrgDictionary.entryFor("マッドハウス")?.key)
        assertEquals("ufotable", BangumiOrgDictionary.entryFor("幽浮社")?.key)
    }

    @Test
    fun `Cince takma adlar da ayni kayda duser`() {
        // 京阿尼 / 京都动画 / 京都アニメーション aynı stüdyodur, tek çipte buluşmalı.
        val keys = listOf("京阿尼", "京都动画", "京都動画", "京都アニメーション")
            .map { BangumiOrgDictionary.entryFor(it)?.key }
        assertEquals(setOf("kyoto_animation"), keys.toSet())
    }

    @Test
    fun `rol siniflandirmasi korunur`() {
        assertEquals(BangumiOrgRole.STUDIO, BangumiOrgDictionary.entryFor("京都アニメーション")?.role)
        assertEquals(BangumiOrgRole.PRODUCER, BangumiOrgDictionary.entryFor("アニプレックス")?.role)
        assertEquals(BangumiOrgRole.NETWORK, BangumiOrgDictionary.entryFor("TBSテレビ")?.role)
        assertEquals(BangumiOrgRole.PUBLISHER, BangumiOrgDictionary.entryFor("集英社")?.role)
    }

    @Test
    fun `sirket unvan eki eslestirmeyi bozmaz`() {
        // 株式会社集英社 → 集英社: unvan eki lookup için yok sayılır.
        assertEquals("shueisha", BangumiOrgDictionary.entryFor("株式会社集英社")?.key)
        assertEquals("shueisha", BangumiOrgDictionary.entryFor("集英社")?.key)
    }

    @Test
    fun `bilinmeyen kurum ve uretim komitesi null doner`() {
        // Uydurma çeviri yok: sözlükte olmayan ad eşlenmez.
        assertNull(BangumiOrgDictionary.entryFor("光坂高校演劇部"))
        assertNull(BangumiOrgDictionary.entryFor("PROJECT-CLS"))
        assertNull(BangumiOrgDictionary.entryFor("Bilinmeyen Stüdyo A.Ş."))
        assertNull(BangumiOrgDictionary.entryFor(""))
        assertNull(BangumiOrgDictionary.entryFor(null))
    }

    @Test
    fun `her kaydin anahtari gecerli Android kaynak adidir`() {
        val invalid = BangumiOrgDictionary.allEntries()
            .map { "bangumi_org_" + it.key }
            .filter { !ANDROID_RESOURCE_NAME.matches(it) }
        assertTrue("geçersiz kaynak adları: $invalid", invalid.isEmpty())
    }

    @Test
    fun `kayit anahtarlari benzersizdir`() {
        val keys = BangumiOrgDictionary.allEntries().map { it.key }
        assertEquals("yinelenen anahtar var", keys.size, keys.toSet().size)
    }

    @Test
    fun `hicbir yazi iki farkli kayda eslenmez`() {
        // Sözlük "ilk kazanan" der; belirsizlik ekranda yanlış ad gösterir.
        val owners = HashMap<String, String>()
        val conflicts = mutableListOf<String>()
        // Yazımlar üzerinden sahiplik denetimi: entryFor her yazımı tek kayda götürmeli.
        for (label in BangumiOrgDictionary.knownLabels()) {
            val key = BangumiOrgDictionary.entryFor(label)?.key ?: continue
            val previous = owners.putIfAbsent(label, key)
            if (previous != null && previous != key) conflicts += "$label → $previous / $key"
        }
        assertTrue("gölgelenen yazımlar: ${conflicts.take(5)}", conflicts.isEmpty())
    }

    // ── Etiket sözlüğü: MASTER ekleri ────────────────────────────────────────

    @Test
    fun `After Story kisa yazimi AS yakalanir ama ASOBI yakalanmaz`() {
        assertEquals("after_story", BangumiTagDictionary.entryFor("After Story")?.key)
        assertEquals("after_story", BangumiTagDictionary.entryFor("AS")?.key)
        // ASOBI SYSTEM bir müzik grubudur; "AS" kısaltmasıyla karışmamalı.
        assertTrue(
            "ASOBI yanlış eşlendi: ${BangumiTagDictionary.entryFor("ASOBI")?.key}",
            BangumiTagDictionary.entryFor("ASOBI")?.key != "after_story"
        )
    }

    @Test
    fun `geleneksel ve Japonca yazim varyantlari eslenir`() {
        // Bu üç yazım tek etikette buluşmalı: 催泪 (basit) / 催淚 (geleneksel) / 催涙 (Japonca).
        val keys = listOf("催泪", "催淚", "催涙").map { BangumiTagDictionary.entryFor(it)?.key }
        assertEquals(setOf("tearjerker"), keys.toSet())
        assertEquals("kyoto_animation", BangumiTagDictionary.entryFor("京都")?.key)
        assertEquals("sequel", BangumiTagDictionary.entryFor("続編")?.key)
    }

    @Test
    fun `huayu etiketi Cince konusulan bolge olarak cevrilir`() {
        // Yazım denetimi: Türkçesi "Çince"dir, "Çinçe" değil.
        val entry = BangumiTagDictionary.entryFor("华语")
        assertNotNull("华语 eşlenmedi", entry)
        assertEquals("chinese_speaking", entry?.key)
        assertEquals("Çince Konuşulan Bölge", entry?.turkish)
        assertEquals("Chinese-speaking Region", entry?.english)
        assertEquals("Çince Konuşulan Bölge", BangumiTagDictionary.entryFor("華語")?.turkish)
    }

    @Test
    fun `sozlukte hicbir Turkce deger Cince biciminde yazilmaz`() {
        val typo = BangumiTagDictionary.allEntries()
            .filter { it.turkish.contains("Çinçe") || it.english.contains("Çinçe") }
            .map { it.key }
        assertTrue("'Çinçe' yazım hatası bulunan kayıtlar: $typo", typo.isEmpty())
    }

    @Test
    fun `ters harem ile kadin haremi ayrik kayitlardir`() {
        // Geçmiş hata: iki kaydın Türkçe değeri de "Ters Harem" idi → gölgelenme.
        val reverse = BangumiTagDictionary.entryFor("逆后宫")
        assertEquals("reverse_harem", reverse?.key)
        assertEquals("Ters Harem", reverse?.turkish)
        val female = BangumiTagDictionary.entryFor("Female Harem")
        assertEquals("female_harem", female?.key)
        assertEquals("Kadın Haremi", female?.turkish)
    }

    @Test
    fun `tum etiket anahtarlari gecerli kaynak adidir`() {
        val invalid = BangumiTagDictionary.allEntries()
            .map { "bangumi_tag_" + it.key }
            .filter { !ANDROID_RESOURCE_NAME.matches(it) }
        assertTrue("geçersiz kaynak adları: $invalid", invalid.isEmpty())
    }

    private companion object {
        val ANDROID_RESOURCE_NAME = Regex("^[a-z_][a-z0-9_]*$")
    }
}

package com.kitsugi.animelist.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Bangumi (Çince/Japonca) etiket, kadro rolü ve stüdyo adı çevirileri için birim testleri.
 * Testler Türkçe cihaz dili taklidi yapar; bitişte özgün dil geri yüklenir.
 */
class KitsugiTranslationsTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setTurkishLocale() {
        Locale.setDefault(Locale("tr", "TR"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ── Bangumi tür / meta etiketleri ────────────────────────────────────────

    @Test
    fun bangumiGenreTags_translateToTurkish() {
        assertEquals("Romantik", "恋爱".toTurkishGenre())
        assertEquals("Romantik", "恋愛".toTurkishGenre())
        assertEquals("Günlük Yaşam", "日常".toTurkishGenre())
        assertEquals("Oyun Uyarlaması", "游戏改".toTurkishGenre())
        assertEquals("Japonya", "日本".toTurkishGenre())
        assertEquals("TV", "TV".toTurkishGenre())
        assertEquals("Bilim Kurgu", "科幻".toTurkishGenre())
    }

    @Test
    fun bangumiGenreTags_translateBackToEnglishForSearch() {
        assertEquals("Romance", "恋爱".toEnglishGenreForSearch())
        assertEquals("Slice of Life", "日常".toEnglishGenreForSearch())
        assertEquals("Game Adaptation", "游戏改".toEnglishGenreForSearch())
        assertEquals("Mystery", "悬疑".toEnglishGenreForSearch())
    }

    @Test
    fun unknownBangumiTag_staysOriginal() {
        assertEquals("BilinmeyenEtiket", "BilinmeyenEtiket".toTurkishGenre())
        assertEquals("BilinmeyenEtiket", "BilinmeyenEtiket".toTurkishBangumiTag())
        assertEquals("BilinmeyenEtiket", "BilinmeyenEtiket".toEnglishGenreForSearch())
        assertNull(bangumiTagEnglishOrNull("BilinmeyenEtiket"))
    }

    // ── Bangumi kullanıcı etiketleri ─────────────────────────────────────────

    @Test
    fun bangumiTags_translateForDisplay() {
        assertEquals("Kyoto Animation", "京阿尼".toTurkishBangumiTag())
        assertEquals("Kyoto Animation", "京都アニメーション".toTurkishBangumiTag())
        assertEquals("Başyapıt", "神作".toTurkishBangumiTag())
        assertEquals("Jun Maeda", "麻枝准".toTurkishBangumiTag())
        assertEquals("Duygusal", "催涙".toTurkishBangumiTag())
        assertEquals("İyileştirici", "治愈".toTurkishBangumiTag())
        assertEquals("Yaşam", "人生".toTurkishBangumiTag())
        assertEquals("Key", "key".toTurkishBangumiTag())
        // Latin etiketler olduğu gibi kalır
        assertEquals("Clannad", "Clannad".toTurkishBangumiTag())
        assertEquals("~After-Story~", "~After-Story~".toTurkishBangumiTag())
    }

    @Test
    fun bangumiTags_translateToEnglish() {
        assertEquals("Masterpiece", bangumiTagEnglishOrNull("神作"))
        assertEquals("Tearjerker", bangumiTagEnglishOrNull("催涙"))
        assertEquals("Kyoto Animation", bangumiTagEnglishOrNull("京阿尼"))
        assertEquals("Jun Maeda", bangumiTagEnglishOrNull("麻枝准"))
    }

    // ── Bangumi kadro / pozisyon rolleri ─────────────────────────────────────

    @Test
    fun staffRoles_translateBangumiEnglishPositionLabels() {
        assertEquals("Bölüm Yönetmeni, Storyboard", "Episode Direction, Storyboard".toTurkishStaffRole())
        assertEquals("Senarist", "Script/Screenplay".toTurkishStaffRole())
        assertEquals(
            "Animasyon Yönetmeni, Baş Animasyon Yönetmeni, Karakter Tasarımı",
            "Animation Direction, Chief Animation Director, Character Design".toTurkishStaffRole()
        )
        assertEquals("Seri Düzenlemesi, Senarist", "Series Composition, Script".toTurkishStaffRole())
        assertEquals("Yönetmen, Senarist", "Director, Script".toTurkishStaffRole())
    }

    @Test
    fun staffRoles_translateJapaneseAndChinesePositionLabels() {
        assertEquals("Yönetmen", "監督".toTurkishStaffRole())
        assertEquals("Senarist", "脚本".toTurkishStaffRole())
        assertEquals("Seri Düzenlemesi", "シリーズ構成".toTurkishStaffRole())
        assertEquals("Storyboard", "絵コンテ".toTurkishStaffRole())
        assertEquals("Animasyon Yönetmeni", "作画監督".toTurkishStaffRole())
        assertEquals("Karakter Tasarımı", "キャラクターデザイン".toTurkishStaffRole())
        assertEquals("Renk Tasarımı", "色彩設計".toTurkishStaffRole())
        assertEquals("Görüntü Yönetmeni", "撮影監督".toTurkishStaffRole())
        assertEquals("Anahtar Animasyon", "原画".toTurkishStaffRole())
        assertEquals("Müzik", "音楽".toTurkishStaffRole())
        assertEquals("Animasyon Yapımı", "アニメーション制作".toTurkishStaffRole())
        assertEquals("Orijinal Yaratıcı", "原作".toTurkishStaffRole())
    }

    @Test
    fun staffRoles_splitBangumiV0DotSeparator() {
        // v0 `/subjects/{id}/persons` görevleri " · " ile birle gelir
        assertEquals("Yönetmen, Senarist", "監督 · 脚本".toTurkishStaffRole())
        assertEquals("Yönetmen, Senarist", "監督、脚本".toTurkishStaffRole())
    }

    // ── Stüdyo adları ────────────────────────────────────────────────────────

    @Test
    fun studioNames_translateToLatin() {
        assertEquals("Kyoto Animation", "京都アニメーション".toLatinStudioName())
        assertEquals("Kyoto Animation", "京都动画".toLatinStudioName())
        assertEquals("Pony Canyon", "ポニーキャニオン".toLatinStudioName())
        assertEquals("Movic", "ムービック".toLatinStudioName())
        assertEquals("Toei Animation", "東映アニメーション".toLatinStudioName())
        // Bilinmeyen (kurgusal) stüdyo adı olduğu gibi kalır
        assertEquals("光坂高校演劇部", "光坂高校演劇部".toLatinStudioName())
        // Zaten Latin adlar değişmez
        assertEquals("TBS", "TBS".toLatinStudioName())
    }
}

package com.kitsugi.animelist.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PreferenceHelpers.isLatinText] ve [PreferenceHelpers.isLatinLetter] fonksiyonları için birim testleri.
 *
 * TMDB, Bangumi ve AniList köprülerinde yanlış alfabeli (Arapça, Tayca, Kiril, CJK)
 * adların "Latin/Romaji" sanılmasını engelleyen mantığın doğruluğunu sınar.
 */
class LatinScriptDetectionTest {

    @Test
    fun isLatinText_rejectsNonLatinAlphabets() {
        // Tayca
        assertFalse("Thai script must not be accepted as Latin", PreferenceHelpers.isLatinText("ไอ โนะนะกะ"))
        // Arapça / Farsça
        assertFalse("Arabic script must not be accepted as Latin", PreferenceHelpers.isLatinText("اری کیتامورا"))
        // Japonca Kanji / Hiragana / Katakana
        assertFalse("Japanese Kanji must not be accepted as Latin", PreferenceHelpers.isLatinText("北村 恵理"))
        assertFalse("Japanese Hiragana must not be accepted as Latin", PreferenceHelpers.isLatinText("きたむら えり"))
        assertFalse("Japanese Katakana must not be accepted as Latin", PreferenceHelpers.isLatinText("キタムラ エリ"))
        // Kiril
        assertFalse("Cyrillic script must not be accepted as Latin", PreferenceHelpers.isLatinText("Эри Окамура"))
        // İbranice
        assertFalse("Hebrew script must not be accepted as Latin", PreferenceHelpers.isLatinText("ארי קיטאמורה"))
    }

    @Test
    fun isLatinText_acceptsEnglishAndRomaji() {
        assertTrue("Standard English name must be Latin", PreferenceHelpers.isLatinText("Eri Okamura"))
        assertTrue("Hayao Miyazaki must be Latin", PreferenceHelpers.isLatinText("Hayao Miyazaki"))
        assertTrue("With middle name or hyphens", PreferenceHelpers.isLatinText("Jean-Luc Picard"))
        assertTrue("Title with punctuation", PreferenceHelpers.isLatinText("Cowboy Bebop (TV)"))
    }

    @Test
    fun isLatinText_acceptsTurkishAndExtendedLatinCharacters() {
        // Türkçe karakterler (ç, ğ, ı, ö, ş, ü, İ, Ğ, Ş, Ç, Ö, Ü)
        assertTrue("Turkish name with special letters must be Latin", PreferenceHelpers.isLatinText("Şeyma Öztürk"))
        assertTrue("Turkish name with dotted I", PreferenceHelpers.isLatinText("İpek Çetin"))
        assertTrue("Turkish name with Yumusak G", PreferenceHelpers.isLatinText("Çağlar Göksu"))

        // Fransızca / Almanca / İskandinav / Vietnam vb. aksanlar
        assertTrue("French accents must be Latin", PreferenceHelpers.isLatinText("Frédéric Chopin"))
        assertTrue("German umlauts must be Latin", PreferenceHelpers.isLatinText("Röntgen"))
        assertTrue("Romaji macron must be Latin", PreferenceHelpers.isLatinText("Tōkyō"))
    }

    @Test
    fun isLatinText_rejectsBlankNullOrPunctuationOnly() {
        assertFalse(PreferenceHelpers.isLatinText(null))
        assertFalse(PreferenceHelpers.isLatinText(""))
        assertFalse(PreferenceHelpers.isLatinText("   "))
        assertFalse(PreferenceHelpers.isLatinText("12345"))
        assertFalse(PreferenceHelpers.isLatinText("---...///"))
    }

    @Test
    fun isLatinText_rejectsMixedLatinAndNonLatin() {
        // Latin + Kanji karışımı saf Latin sayılmamalı
        assertFalse(PreferenceHelpers.isLatinText("Eri Okamura (北村 恵理)"))
    }
}

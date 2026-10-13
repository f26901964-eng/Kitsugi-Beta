package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomNameVariantsTest {

    @Test
    fun `titles normalize punctuation diacritics possessives and multiplication sign`() {
        assertEquals("spy x family", FandomNameVariants.normalizeForCompare("SPY×FAMILY"))
        assertEquals("oshi no ko", FandomNameVariants.normalizeForCompare("[Oshi no Ko]"))
        assertEquals("naoki urasawa monster wiki", FandomNameVariants.normalizeForCompare("Naoki Urasawa's Monster Wiki"))
        assertEquals("kenzo tenma", FandomNameVariants.normalizeForCompare("Kenzō Tenma"))
        assertEquals("re zero kara hajimeru isekai seikatsu", FandomNameVariants.normalizeForCompare("Re:Zero kara Hajimeru Isekai Seikatsu"))
        assertEquals(listOf("attack", "on", "titan"), FandomNameVariants.tokens("Attack on Titan Wiki"))
    }

    @Test
    fun `latin detection`() {
        assertTrue(FandomNameVariants.isMostlyLatin("Sousou no Frieren"))
        assertFalse(FandomNameVariants.isMostlyLatin("葬送のフリーレン"))
        assertFalse(FandomNameVariants.isMostlyLatin("나 혼자만 레벨업"))
        assertFalse(FandomNameVariants.isMostlyLatin("12345"))
    }

    @Test
    fun `season markers are stripped`() {
        assertEquals("Shingeki no Kyojin", FandomNameVariants.stripSeasonMarkers("Shingeki no Kyojin Season 3 Part 2"))
        assertEquals("Jujutsu Kaisen", FandomNameVariants.stripSeasonMarkers("Jujutsu Kaisen 2nd Season"))
        assertEquals("Mob Psycho 100", FandomNameVariants.stripSeasonMarkers("Mob Psycho 100 II"))
        assertEquals("Hunter x Hunter", FandomNameVariants.stripSeasonMarkers("Hunter x Hunter (2011)"))
        assertEquals("Jujutsu Kaisen 0", FandomNameVariants.stripSeasonMarkers("Jujutsu Kaisen 0: The Movie"))
        assertEquals("Kaiju No. 8", FandomNameVariants.stripSeasonMarkers("Kaiju No. 8"))
    }

    @Test
    fun `media title variants preserve native script and romanized forms`() {
        assertEquals(
            listOf("君の名は。", "Kimi no Na wa"),
            FandomNameVariants.mediaTitleVariants(listOf("君の名は。", "Kimi no Na wa."))
        )
        assertEquals(
            listOf("呪術廻戦 0: 東京都立呪術高等専門学校", "呪術廻戦 0"),
            FandomNameVariants.mediaTitleVariants(listOf("呪術廻戦 0: 東京都立呪術高等専門学校"))
        )
    }

    @Test
    fun `base title variants keep order and split subtitles`() {
        val variants = FandomNameVariants.baseTitleVariants(
            listOf("Demon Slayer: Kimetsu no Yaiba", "Kimetsu no Yaiba", "鬼滅の刃")
        )
        assertEquals(listOf("Demon Slayer: Kimetsu no Yaiba", "Demon Slayer", "Kimetsu no Yaiba"), variants)
    }

    @Test
    fun `slug candidates use hyphenated and joined forms`() {
        assertEquals(listOf("spy-x-family", "spyxfamily"), FandomNameVariants.slugCandidates(listOf("Spy x Family")))
        assertEquals(listOf("chainsaw-man", "chainsawman"), FandomNameVariants.slugCandidates(listOf("Chainsaw Man")))
    }

    @Test
    fun `character variants cover name order long vowels diacritics and hyphens`() {
        val tenma = FandomNameVariants.characterTitleVariants("Kenzou Tenma", max = 4)
        assertEquals(listOf("Kenzou Tenma", "Tenma Kenzou", "Kenzo Tenma", "Tenma Kenzo"), tenma)

        val jinwoo = FandomNameVariants.characterTitleVariants("Jinwoo Sung")
        assertTrue("Sung Jinwoo" in jinwoo)

        val hyphen = FandomNameVariants.characterTitleVariants("Sung Jin-Woo", max = 4)
        assertEquals(listOf("Sung Jin-Woo", "Jin-Woo Sung", "Sung Jinwoo", "Jinwoo Sung"), hyphen)

        val gojo = FandomNameVariants.characterTitleVariants("Satoru Gojō")
        assertTrue("Satoru Gojo" in gojo)
        assertTrue("Gojo Satoru" in gojo)

        assertEquals(listOf("Luffy"), FandomNameVariants.characterTitleVariants(null, listOf("Luffy")))
    }

    @Test
    fun `native names are matched despite readings spaces and dots`() {
        val description = "Satoru Gojo (五 (ご) 条 (じょう) 悟 (さとる) , Gojō Satoru?) is one of the main protagonists"
        assertTrue(FandomNameVariants.nativeAppearsIn("五条悟", description))
        assertTrue(FandomNameVariants.nativeAppearsIn("五条 悟", "Kanji 五条悟"))
        assertTrue(FandomNameVariants.nativeAppearsIn("성진우", "Hangul 성진우 Debut Chapter 1"))
        assertTrue(FandomNameVariants.nativeAppearsIn("モンキー・D・ルフィ", "Japanese: モンキー・D・ルフィ"))
        assertFalse(FandomNameVariants.nativeAppearsIn("五条悟", "Kanji 夏油傑"))
        assertFalse(FandomNameVariants.nativeAppearsIn("Gojo", "Gojo"))
        assertFalse(FandomNameVariants.nativeAppearsIn("茜", "茜"))
        assertFalse(FandomNameVariants.nativeAppearsIn(null, "五条悟"))
    }

    @Test
    fun `descriptions are cleaned from ruby readings`() {
        assertEquals(
            "Satoru Gojo (五条悟, Gojō Satoru) is",
            FandomNameVariants.cleanDescription("Satoru Gojo (五 (ご) 条 (じょう) 悟 (さとる) , Gojō Satoru?) is")
        )
    }
}

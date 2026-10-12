package com.kitsugi.animelist.data.fandom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * İsim yolunun puanlaması — senaryolar 2026-10-12 tarihli canlı testlerden alınmıştır:
 *  - "Monster" araması genel "canavarlar" wiki'sini (monster.fandom.com) ilk sıraya koyar;
 *    karakter parmak izi 0/5 → veto. "Monster Naoki Urasawa" ile bulunan obluda.fandom.com 5/5.
 *  - Solo Leveling: anime wiki'si ile oyun (ARISE) wiki'si aynı karakter sayfalarına sahip.
 */
class FandomNameMatcherTest {

    private val monsterEvidence = FandomNameEvidence(
        titles = listOf("Monster"),
        creators = listOf("Naoki Urasawa"),
        characterNames = listOf("Kenzou Tenma", "Johan Liebert", "Nina Fortner"),
        isManga = false
    )

    private val genericMonster = FandomCommunityHit(
        wikiId = "monster",
        name = "Monster Wiki",
        language = "en",
        hub = "tv",
        pageCount = 4000,
        pageViews = 50000,
        description = "Monster is a generic term for malevolent beings of many horrible species."
    )

    private val urasawaMonster = FandomCommunityHit(
        wikiId = "obluda",
        name = "Naoki Urasawa's Monster Wiki",
        language = "en",
        hub = "anime",
        pageCount = 200,
        pageViews = 20000,
        description = "Renowned neurosurgeon Kenzō Tenma's life is turned upside-down when he saves the life of a young boy named Johan Liebert."
    )

    @Test
    fun `generic wiki with the same name is vetoed by the character fingerprint`() {
        val fingerprint = FandomFingerprint(mainTotal = 3, mainFound = 0, anyTotal = 5, anyFound = 0, nativeConfirmed = 0)
        val score = FandomNameMatcher.score(genericMonster, monsterEvidence, fingerprint)
        assertEquals(FandomDecision.REJECT, score.decision)
        assertEquals("no-main-characters", score.vetoReason)
    }

    @Test
    fun `title plus author wiki is accepted with native confirmation`() {
        val fingerprint = FandomFingerprint(mainTotal = 3, mainFound = 3, anyTotal = 5, anyFound = 5, nativeConfirmed = 2)
        val score = FandomNameMatcher.score(urasawaMonster, monsterEvidence, fingerprint)
        assertEquals(FandomDecision.CERTAIN, score.decision)
        assertTrue(score.score >= FandomNameMatcher.CERTAIN_THRESHOLD)
    }

    @Test
    fun `without native names a strong wiki with few characters stays probable`() {
        val fingerprint = FandomFingerprint(mainTotal = 3, mainFound = 3, anyTotal = 3, anyFound = 3, nativeConfirmed = 0)
        val score = FandomNameMatcher.score(urasawaMonster, monsterEvidence, fingerprint)
        assertEquals(FandomDecision.PROBABLE, score.decision)
    }

    @Test
    fun `anime wiki beats the franchise game wiki`() {
        val evidence = FandomNameEvidence(
            titles = listOf("Solo Leveling"),
            creators = listOf("Chugong"),
            characterNames = listOf("Jinwoo Sung", "Hae-In Cha"),
            isManga = false
        )
        val anime = FandomCommunityHit(
            "solo-leveling", "Solo Leveling Wiki", "en", "anime", 600, 100000,
            "Solo Leveling Wiki is a community site that anyone can contribute to."
        )
        val game = FandomCommunityHit(
            "solo-leveling-arise", "Solo Leveling: ARISE Wiki", "en", "games", 300, 10000,
            "Solo Leveling: ARISE players, speedrunners and Solo Leveling fans"
        )
        val animePrint = FandomFingerprint(mainTotal = 2, mainFound = 2, anyTotal = 6, anyFound = 5, nativeConfirmed = 1, creatorPageFound = true)
        val gamePrint = animePrint.copy(nativeConfirmed = 0, creatorPageFound = false)

        val animeScore = FandomNameMatcher.score(anime, evidence, animePrint)
        val gameScore = FandomNameMatcher.score(game, evidence, gamePrint)
        assertTrue(animeScore.score > gameScore.score)
        assertEquals(FandomDecision.REJECT, gameScore.decision)

        val best = FandomNameMatcher.pickBest(listOf(gameScore, animeScore))
        assertEquals("solo-leveling", best!!.wikiId)
        assertEquals(FandomDecision.CERTAIN, best.decision)
    }

    @Test
    fun `prefilter vetoes blocked tiny foreign and fan wikis`() {
        assertEquals("blocked", FandomNameMatcher.preFilterVeto(FandomCommunityHit("villains", "Villains Wiki")))
        assertEquals(
            "tiny",
            FandomNameMatcher.preFilterVeto(FandomCommunityHit("solo-leveling-simulator", "Solo Leveling Simulator Wiki", "en", "games", 10))
        )
        assertEquals(
            "language",
            FandomNameMatcher.preFilterVeto(FandomCommunityHit("re-monster-ita", "Re:Monster Ita Wikia", "it", "books", 100))
        )
        assertEquals("language", FandomNameMatcher.preFilterVeto(FandomCommunityHit("es.onepiece", "One Piece Wiki")))
        assertEquals(
            "fan-content",
            FandomNameMatcher.preFilterVeto(FandomCommunityHit("anime-battle", "Anime Battle Simulator Wiki", "en", "games", 500))
        )
        assertNull(FandomNameMatcher.preFilterVeto(FandomCommunityHit("tr.onepiece", "One Piece Vikisi", "tr", "anime", 300)))
    }

    @Test
    fun `title similarity ignores creator names but penalizes extra words`() {
        assertEquals(1.0, FandomNameMatcher.titleSimilarity("Naoki Urasawa's Monster Wiki", listOf("Monster"), listOf("Naoki Urasawa")), 1e-9)
        assertEquals(2.0 / 3.0, FandomNameMatcher.titleSimilarity("Solo Leveling: ARISE Wiki", listOf("Solo Leveling")), 1e-9)
        assertEquals(1.0, FandomNameMatcher.titleSimilarity("Attack on Titan Wiki", listOf("Shingeki no Kyojin", "Attack on Titan")), 1e-9)
        assertEquals(0.0, FandomNameMatcher.titleSimilarity("Naruto Wiki", listOf("Bleach")), 1e-9)
    }

    @Test
    fun `close runner up demotes a certain result`() {
        val leader = FandomNameScore("a", 0.90, FandomDecision.CERTAIN)
        val runnerUp = FandomNameScore("b", 0.88, FandomDecision.PROBABLE)
        assertEquals(FandomDecision.PROBABLE, FandomNameMatcher.pickBest(listOf(runnerUp, leader))!!.decision)
        assertNull(FandomNameMatcher.pickBest(listOf(FandomNameScore("c", 0.1, FandomDecision.REJECT))))
    }

    @Test
    fun `without character evidence the name path never accepts`() {
        val hit = FandomCommunityHit("frieren", "Frieren Wiki", "en", "anime", 900)
        val score = FandomNameMatcher.score(hit, FandomNameEvidence(titles = listOf("Frieren")), fingerprint = null)
        assertNotEquals(FandomDecision.CERTAIN, score.decision)
        assertEquals(FandomDecision.REJECT, score.decision)
    }

    @Test
    fun `exact anilist link is decisive`() {
        val score = FandomNameMatcher.score(urasawaMonster, monsterEvidence, fingerprint = null, exactLink = true)
        assertEquals(FandomDecision.CERTAIN, score.decision)
        assertEquals(1.0, score.score, 1e-9)
    }
}

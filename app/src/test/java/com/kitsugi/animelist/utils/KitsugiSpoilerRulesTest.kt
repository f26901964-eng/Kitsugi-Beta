package com.kitsugi.animelist.utils

import com.kitsugi.animelist.data.remote.KitsugiTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class KitsugiSpoilerRulesTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun isIntrinsicSpoiler_detectsEnglishTurkishAndCjkSpoilerTags() {
        // English
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Death"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Suicide"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Plot Twist"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Betrayal"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Time Loop"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Amnesia"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Tragedy"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Netorare"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Bad Ending"))

        // Turkish
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Ölüm"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("İntihar"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Sürpriz Gelişme"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("İhanet"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Zaman Döngüsü"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Hafıza Kaybı"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Trajedi"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("Kötü Son"))

        // CJK (Bangumi)
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("剧透"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("悲剧"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("致郁"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("胃痛"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("便当"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("轮回"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("失忆"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("背叛"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("寝取られ"))
        assertTrue(KitsugiSpoilerRules.isIntrinsicSpoiler("神展开"))
    }

    @Test
    fun isIntrinsicSpoiler_keepsStandardGenresAndNormalTropesVisible() {
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Action"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Comedy"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Romance"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Fantasy"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Sci-Fi"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Slice of Life"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Aksiyon"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Komedi"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("搞笑"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("奇幻"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("校园"))
        assertFalse(KitsugiSpoilerRules.isIntrinsicSpoiler("Kyoto Animation"))
    }

    @Test
    fun mergeTagsPreservingSpoilers_transfersAniListSpoilerAndRankToBangumiTags() {
        val bangumiTags = listOf(
            KitsugiTag(name = "搞笑", rank = null, isSpoiler = false, source = "bangumi"),
            KitsugiTag(name = "丧尸", rank = null, isSpoiler = false, source = "bangumi"),
            KitsugiTag(name = "悲剧", rank = null, isSpoiler = false, source = "bangumi")
        )
        val aniListTags = listOf(
            KitsugiTag(name = "Comedy", rank = 92, isSpoiler = false, source = "anilist"),
            // Yapıma özel spoiler (örn. Gakkougurashi! için Zombie/丧尸 AniList'te media spoiler'dır)
            KitsugiTag(name = "Zombie", rank = 88, isSpoiler = true, source = "anilist"),
            KitsugiTag(name = "Tragedy", rank = 80, isSpoiler = true, source = "anilist"),
            KitsugiTag(name = "Memory Manipulation", rank = 65, isSpoiler = true, source = "anilist")
        )

        val merged = KitsugiSpoilerRules.mergeTagsPreservingSpoilers(bangumiTags, aniListTags, limit = 48)

        // Bangumi'nin özgün etiket adı ve source alanı korunur; AniList'ten rank ve isSpoiler aktarılır
        val comedy = merged.first { it.name == "搞笑" }
        assertEquals("bangumi", comedy.source)
        assertEquals(92, comedy.rank)
        assertFalse(comedy.isSpoiler)

        val zombie = merged.first { it.name == "丧尸" }
        assertEquals("bangumi", zombie.source)
        assertEquals(88, zombie.rank)
        assertTrue(zombie.isSpoiler)

        val tragedy = merged.first { it.name == "悲剧" }
        assertEquals("bangumi", tragedy.source)
        assertEquals(80, tragedy.rank)
        assertTrue(tragedy.isSpoiler)

        // AniList'ten gelen ek spoiler etiketi de listeye dahil edilir
        val memory = merged.first { it.name == "Memory Manipulation" }
        assertTrue(memory.isSpoiler)
        assertEquals(65, memory.rank)
    }

    @Test
    fun isGenreSpoiler_detectsIntrinsicAndMediaSpecificSpoilerGenresWithoutHidingCoreGenres() {
        val mediaTags = listOf(
            KitsugiTag(name = "Time Travel", rank = 85, isSpoiler = true, source = "anilist"),
            KitsugiTag(name = "Survival", rank = 78, isSpoiler = true, source = "anilist"),
            KitsugiTag(name = "School", rank = 95, isSpoiler = false, source = "anilist")
        )

        // Ana türler asla gizlenmez
        assertFalse(KitsugiSpoilerRules.isGenreSpoiler("Action", mediaTags))
        assertFalse(KitsugiSpoilerRules.isGenreSpoiler("Aksiyon", mediaTags))
        assertFalse(KitsugiSpoilerRules.isGenreSpoiler("Drama", mediaTags))
        assertFalse(KitsugiSpoilerRules.isGenreSpoiler("Komedi", mediaTags))

        // Doğal spoiler olan alt tür/tema her zaman spoiler sayılır
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Tragedy", emptyList()))
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Trajedi", emptyList()))
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("悲剧", emptyList()))
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Zaman Döngüsü", emptyList()))

        // Yapıma özel spoiler olan alt temalar (MAL/Bangumi/Shikimori/Kitsu genres içinde geldiğinde)
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Time Travel", mediaTags))
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Zaman Yolculuğu", mediaTags))
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Survival", mediaTags))
        assertTrue(KitsugiSpoilerRules.isGenreSpoiler("Hayatta Kalma", mediaTags))
        assertFalse(KitsugiSpoilerRules.isGenreSpoiler("School", mediaTags))
        assertFalse(KitsugiSpoilerRules.isGenreSpoiler("Okul", mediaTags))
    }
}

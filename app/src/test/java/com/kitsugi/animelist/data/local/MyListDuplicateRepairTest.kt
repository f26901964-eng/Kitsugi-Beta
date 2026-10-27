package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Var olan çift kayıtların onarımı. Onarım VERİ SİLDİĞİ için yalnızca kanıtlanmış kimlik
 * eşleşmelerini birleştirmelidir; başlık benzerliği asla yetmez.
 */
class MyListDuplicateRepairTest {

    private fun entry(
        id: Int,
        title: String,
        type: MediaType = MediaType.Anime,
        source: String = "simkl",
        simklId: Int? = null,
        malId: Int? = null,
        progress: Int = 0,
        status: WatchStatus = WatchStatus.Planned,
        updatedAt: Long = 0L,
        imageUrl: String? = null,
        notes: String? = null
    ) = MediaEntry(
        id = id,
        title = title,
        type = type,
        status = status,
        score = null,
        progress = progress,
        total = 1,
        source = source,
        malId = malId,
        imageUrl = imageUrl,
        notes = notes,
        year = 2025,
        updatedAt = updatedAt,
        simklId = simklId
    )

    @Test
    fun movieAndAnimeCopiesOfOneSimklItemAreMergedIntoASingleRow() {
        val movie = entry(
            id = 1,
            title = "Demon Slayer: Infinity Castle",
            type = MediaType.Movie,
            simklId = 2098300,
            progress = 1,
            status = WatchStatus.Completed,
            updatedAt = 100
        )
        val anime = entry(
            id = 2,
            title = "Demon Slayer: Infinity Castle",
            type = MediaType.Anime,
            simklId = 2098300,
            progress = 0,
            status = WatchStatus.Planned,
            updatedAt = 50
        )

        val plans = MyListDuplicateRepair.plan(listOf(movie, anime))

        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals(1, plan.removed.size)
        assertEquals(MediaType.Anime, plan.keeper.type)
        assertEquals("ilerleme ve durum gerilemez", 1, plan.keeper.progress)
        assertEquals(WatchStatus.Completed, plan.keeper.status)
    }

    @Test
    fun sameTitleWithDifferentProviderIdsIsNeverMerged() {
        val first = entry(id = 1, title = "Frieren", simklId = 111)
        val second = entry(id = 2, title = "Frieren", simklId = 222)

        assertTrue(MyListDuplicateRepair.plan(listOf(first, second)).isEmpty())
    }

    @Test
    fun duplicatesOfDifferentSourcesAreLeftAlone() {
        val simkl = entry(id = 1, title = "Same title", source = "simkl", simklId = 42)
        val kitsu = entry(id = 2, title = "Same title", source = "kitsu", malId = 300_000_042)

        assertTrue(MyListDuplicateRepair.plan(listOf(simkl, kitsu)).isEmpty())
    }

    @Test
    fun userDataFromBothCopiesSurvivesTheMerge() {
        val older = entry(
            id = 1,
            title = "Kaiju No. 8 Season 2",
            simklId = 77,
            progress = 6,
            status = WatchStatus.Watching,
            notes = "not",
            updatedAt = 10
        )
        val newer = entry(
            id = 2,
            title = "Kaiju No. 8 Season 2",
            type = MediaType.Movie,
            simklId = 77,
            imageUrl = "https://example.invalid/p.jpg",
            updatedAt = 20
        )

        val keeper = MyListDuplicateRepair.plan(listOf(older, newer)).single().keeper

        assertEquals(6, keeper.progress)
        assertEquals(WatchStatus.Watching, keeper.status)
        assertEquals("not", keeper.notes)
        assertEquals("https://example.invalid/p.jpg", keeper.imageUrl)
        assertEquals(MediaType.Anime, keeper.type)
    }

    @Test
    fun bangumiSubjectDuplicatesAreRecognizedThroughTheStableIdNamespace() {
        val first = entry(id = 1, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)
        val second = entry(id = 2, title = "葬送のフリーレン", source = "bangumi", malId = 500_057_162)

        val plan = MyListDuplicateRepair.plan(listOf(first, second)).single()

        assertEquals(1, plan.removed.size)
    }
}

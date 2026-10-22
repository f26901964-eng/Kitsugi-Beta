package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Listem "Yenile" akışının sözleşmesi: **liste sıfırdan çekilmez, eksikler tamamlanır**.
 *
 * Arka plan otomatik yenilemesi ve kullanıcı yenilemesi [planSourceMerge] üzerinden
 * yalnızca gerçek farkı üretir. Bu testler üç regresyonu kilitler:
 * 1. Değişmeyen kayıtlar UPDATE edilmez (aksi hâlde Room tüm tabloyu geçersiz kılıp
 *    Listem'i baştan çiziyordu → "yeniledi ama liste baştan geldi" hissi).
 * 2. Hiçbir satır SİLİNMEZ (`allowDelete = false`): uzakta olmayan yerel kayıt korunur.
 * 3. Kullanıcı/yerel zenginleştirme geri alınmaz: +18 işareti, çözülmüş başlıklar ve
 *    uzak kaynağın göndermediği `updatedAt` damgası.
 */
class SourceMergePlanTest {

    private fun existing(
        id: Int,
        title: String = "Frieren",
        progress: Int = 3,
        total: Int = 28,
        isAdult: Boolean = false,
        updatedAt: Long = 1_700_000_000L,
        malId: Int? = 100
    ) = MediaEntryEntity(
        id = id,
        title = title,
        subtitle = "Anime • 2023",
        type = "Anime",
        status = "Watching",
        score = null,
        progress = progress,
        total = total,
        isFavorite = false,
        isAdult = isAdult,
        source = "anilist",
        malId = malId,
        imageUrl = null,
        year = 2023,
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
        updatedAt = updatedAt
    )

    private fun imported(
        title: String = "Frieren",
        progress: Int = 3,
        total: Int = 28,
        isAdult: Boolean = false,
        updatedAt: Long = 1_700_000_000L,
        malId: Int? = 100
    ) = MediaEntry(
        id = 0,
        title = title,
        subtitle = "Anime • 2023",
        type = MediaType.Anime,
        status = WatchStatus.Watching,
        score = null,
        progress = progress,
        total = total,
        isAdult = isAdult,
        source = "anilist",
        malId = malId,
        year = 2023,
        updatedAt = updatedAt
    )

    @Test
    fun identicalRemoteListWritesNothing() {
        val plan = planSourceMerge(
            source = "anilist",
            importedEntries = listOf(imported()),
            existing = listOf(existing(id = 1))
        )

        assertTrue("değişmeyen satır INSERT edilmemeli", plan.inserts.isEmpty())
        assertTrue("değişmeyen satır UPDATE edilmemeli", plan.updates.isEmpty())
        assertTrue("yenileme silmemeli", plan.deletions.isEmpty())
        assertEquals(0, plan.stats.added)
        assertEquals(0, plan.stats.updated)
        assertEquals(1, plan.stats.unchanged)
        assertFalse(plan.stats.hasChanges)
    }

    @Test
    fun missingEntryIsAddedAndExistingRowIsKept() {
        val plan = planSourceMerge(
            source = "anilist",
            importedEntries = listOf(
                imported(),
                imported(title = "Apothecary Diaries", malId = 200)
            ),
            existing = listOf(existing(id = 1))
        )

        assertEquals("yalnızca eksik kayıt eklenir", 1, plan.inserts.size)
        assertEquals("Apothecary Diaries", plan.inserts.single().title)
        assertTrue(plan.updates.isEmpty())
        assertTrue("uzak listede olmayan yerel satır korunur", plan.deletions.isEmpty())
        assertEquals(1, plan.stats.added)
    }

    @Test
    fun changedProgressUpdatesOnlyThatRow() {
        val plan = planSourceMerge(
            source = "anilist",
            importedEntries = listOf(
                imported(progress = 5),
                imported(title = "Apothecary Diaries", progress = 3, malId = 200)
            ),
            existing = listOf(
                existing(id = 1, progress = 3),
                existing(id = 2, title = "Apothecary Diaries", progress = 3, malId = 200)
            )
        )

        assertEquals(1, plan.updates.size)
        assertEquals(1, plan.updates.single().id)
        assertEquals(5, plan.updates.single().progress)
        assertEquals(0, plan.stats.added)
        assertEquals(1, plan.stats.updated)
        assertEquals(1, plan.stats.unchanged)
    }

    @Test
    fun allowDeleteTrueStillReportsRemovals() {
        val plan = planSourceMerge(
            source = "anilist",
            importedEntries = listOf(imported()),
            existing = listOf(
                existing(id = 1),
                existing(id = 2, title = "Silinen Kayıt", malId = 300)
            ),
            allowDelete = true
        )

        assertEquals(listOf(2), plan.deletions)
    }

    @Test
    fun adultFlagAndResolvedTitlesAreNeverRolledBack() {
        val plan = planSourceMerge(
            source = "anilist",
            importedEntries = listOf(
                // Bangumi koleksiyon ucu gibi: uzak taraf yalnızca özgün (CJK) adı getirir.
                // İlerleme değişmiş olmalı ki satır gerçekten UPDATE listesine girsin
                // (hiçbir alan değişmezse tasarım gereği yazılmaz).
                imported(title = "葬送のフリーレン", progress = 4, isAdult = false)
            ),
            existing = listOf(
                existing(id = 1, title = "Frieren: Beyond Journey's End", isAdult = true)
                    .copy(titleEnglish = "Frieren: Beyond Journey's End", titleJapanese = "葬送のフリーレン")
            )
        )

        val updated = plan.updates.single()
        assertTrue("+18 işareti geri alınmamalı", updated.isAdult)
        assertEquals(
            "çözülmüş Latin başlık CJK ada geri dönmemeli",
            "Frieren: Beyond Journey's End",
            updated.title
        )
    }

    @Test
    fun zeroRemoteTimestampKeepsLocalUpdatedAt() {
        val plan = planSourceMerge(
            source = "simkl",
            importedEntries = listOf(
                imported(progress = 4, updatedAt = 0L).copy(source = "simkl")
            ),
            existing = listOf(existing(id = 1, progress = 3).copy(source = "simkl"))
        )

        assertEquals(1, plan.updates.size)
        assertEquals(
            "uzak kaynak zaman damgası göndermiyorsa yerel damga korunur",
            1_700_000_000L,
            plan.updates.single().updatedAt
        )
    }

    @Test
    fun emptyRemoteListNeverDeletesLocalRows() {
        val plan = planSourceMerge(
            source = "anilist",
            importedEntries = emptyList(),
            existing = listOf(existing(id = 1)),
            allowDelete = false
        )

        assertTrue(plan.deletions.isEmpty())
        assertFalse(plan.stats.hasChanges)
    }
}

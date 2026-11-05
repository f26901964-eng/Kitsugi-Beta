package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaChapterGroupingTest {

    private data class Row(val name: String, val number: Float, val volume: Int?)

    @Test
    fun groupsByVolumeNewestVolumeFirstAndUnknownLast() {
        val rows = listOf(
            Row("Cilt 2 Bölüm 10", 10f, 2),
            Row("Cilt 2 Bölüm 9", 9f, 2),
            Row("Cilt 1 Bölüm 5", 5f, 1),
            Row("Duyuru", -1f, null)
        )
        val groups = MangaChapterGrouping.groupByVolume(rows, { it.volume }, { it.number })
        assertEquals(3, groups.size)
        assertEquals(2, groups[0].volume)
        assertEquals(1, groups[1].volume)
        assertNull("Cilt bilgisi olmayan grup en sonda", groups[2].volume)
        assertEquals(2, groups[0].chapters.size)
    }

    @Test
    fun rangeLabelShowsSpan() {
        val rows = listOf(Row("a", 3f, 1), Row("b", 1f, 1))
        val groups = MangaChapterGrouping.groupByVolume(rows, { it.volume }, { it.number })
        val label = MangaChapterGrouping.rangeLabel<Row> { it.number }(groups.first())
        assertEquals("Cilt 1 · 1–3", label)
    }
}

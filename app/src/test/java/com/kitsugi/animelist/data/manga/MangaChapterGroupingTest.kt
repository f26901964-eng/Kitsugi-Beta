package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaChapterGroupingTest {

    @Test
    fun `same chapter number keeps translators in one group`() {
        val chapters = listOf(
            chapter("two-b", "Bölüm 2", 2f, "MangaciX", 20),
            chapter("three", "Bölüm 3", 3f, "Şimarik Fansub", 30),
            chapter("two-a", "Bölüm 2", 2f, "Şimarik Fansub", 10),
            chapter("one", "Bölüm 1", 1f, "Şimarik Fansub", 5),
        )

        val groups = MangaChapterGrouping.group("Chainsaw Man", chapters)

        assertEquals(3, groups.size)
        val chapterTwo = groups.single { it.number == 2.0 }
        assertEquals(listOf("two-b", "two-a"), chapterTwo.chapters.map { it.url })
        assertEquals(2, chapterTwo.scanlators.size)
    }

    @Test
    fun `number is recovered from localized chapter name`() {
        val groups = MangaChapterGrouping.group(
            "Chainsaw Man",
            listOf(
                chapter("one-a", "Bölüm 12.5", -1f, "A"),
                chapter("one-b", "Bölüm 12.5", -1f, "B"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(12.5, groups.single().number)
        assertEquals(2, groups.single().chapters.size)
    }

    @Test
    fun `next navigation skips alternate translations of current chapter`() {
        val chapters = listOf(
            chapter("three", "Bölüm 3", 3f, "A"),
            chapter("two-b", "Bölüm 2", 2f, "B"),
            chapter("two-a", "Bölüm 2", 2f, "A"),
            chapter("one", "Bölüm 1", 1f, "A"),
        )

        val next = MangaChapterGrouping.adjacentGroup(
            "Chainsaw Man", chapters, chapters[1], next = true,
        )
        val previous = MangaChapterGrouping.adjacentGroup(
            "Chainsaw Man", chapters, chapters[1], next = false,
        )

        assertEquals(listOf("three"), next?.chapters?.map { it.url })
        assertEquals(listOf("one"), previous?.chapters?.map { it.url })
    }

    @Test
    fun `next navigation returns every translator for explicit selection`() {
        val chapters = listOf(
            chapter("three", "Bölüm 3", 3f, "A"),
            chapter("two-b", "Bölüm 2", 2f, "B"),
            chapter("two-a", "Bölüm 2", 2f, "A"),
            chapter("one", "Bölüm 1", 1f, "A"),
        )

        val target = MangaChapterGrouping.adjacentGroup(
            "Chainsaw Man", chapters, chapters[3], next = true,
        )

        assertEquals(listOf("two-b", "two-a"), target?.chapters?.map { it.url })
    }

    @Test
    fun `last chapter has no next target`() {
        val chapters = listOf(
            chapter("two", "Bölüm 2", 2f),
            chapter("one", "Bölüm 1", 1f),
        )

        assertNull(MangaChapterGrouping.adjacentGroup("Manga", chapters, chapters[0], next = true))
    }

    @Test
    fun `identical urls are deduplicated without dropping real variants`() {
        val duplicate = chapter("same", "Bölüm 1", 1f, "A")
        val other = chapter("other", "Bölüm 1", 1f, "A")

        val group = MangaChapterGrouping.group("Manga", listOf(duplicate, duplicate, other)).single()

        assertEquals(2, group.chapters.size)
    }

    private fun chapter(
        url: String,
        name: String,
        number: Float = -1f,
        scanlator: String? = null,
        uploadDate: Long = 0,
    ) = MangaChapter(
        url = url,
        name = name,
        chapterNumber = number,
        scanlator = scanlator,
        uploadDate = uploadDate,
    )
}

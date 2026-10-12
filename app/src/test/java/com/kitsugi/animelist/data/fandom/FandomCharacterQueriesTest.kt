package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.remote.KitsugiCharacterMediaAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomCharacterQueriesTest {

    private fun detail(
        source: String,
        id: Int = 1,
        aniListId: Int? = null,
        appearances: List<KitsugiCharacterMediaAppearance> = emptyList()
    ) = KitsugiCharacterDetail(
        id = id,
        name = "Satoru Gojo",
        nativeName = "五条悟",
        alternativeNames = listOf("Gojo-sensei", "五条先生"),
        imageUrl = null,
        gender = null,
        age = null,
        birthday = null,
        bloodType = null,
        biography = null,
        mediaAppearances = appearances,
        aniListId = aniListId,
        source = source,
        romanizedName = "Gojou Satoru",
        englishName = "Satoru Gojo"
    )

    @Test
    fun `tmdb characters are skipped`() {
        assertNull(FandomCharacterQueries.from(detail("tmdb"), "tmdb", 5))
    }

    @Test
    fun `anilist character builds ids names and appearances`() {
        val appearances = listOf(
            KitsugiCharacterMediaAppearance(
                mediaId = 40748, title = "Jujutsu Kaisen", imageUrl = null, mediaType = "TV Dizisi",
                characterRole = "Ana Karakter", source = "anilist",
                titleEnglish = "JUJUTSU KAISEN", titleRomaji = "Jujutsu Kaisen"
            ),
            KitsugiCharacterMediaAppearance(
                mediaId = 100_101_517, title = "Jujutsu Kaisen", imageUrl = null, mediaType = "Manga",
                characterRole = "Yardımcı Karakter", source = "anilist"
            ),
            KitsugiCharacterMediaAppearance(
                mediaId = 0, title = "Bilinmeyen", imageUrl = null, mediaType = "Anime",
                characterRole = "MAIN", source = "anilist"
            )
        )
        val query = FandomCharacterQueries.from(detail("anilist", id = 127691, appearances = appearances), "anilist", 127691)!!
        assertEquals(127691, query.aniListCharacterId)
        assertNull(query.malCharacterId)
        assertEquals("al:127691", query.cacheKey())
        assertEquals(listOf("Satoru Gojo", "Gojou Satoru"), query.names)
        assertEquals("五条悟", query.nativeName)
        assertEquals(listOf("Gojo-sensei"), query.alternatives)

        assertEquals(2, query.appearances.size)
        assertEquals(FandomSourceIds(malId = 40748), query.appearances[0].ids)
        assertTrue(query.appearances[0].isMainRole)
        assertFalse(query.appearances[0].isManga)
        assertEquals(listOf("JUJUTSU KAISEN", "Jujutsu Kaisen"), query.appearances[0].titles)
        assertEquals(FandomSourceIds(aniListId = 101517), query.appearances[1].ids)
        assertTrue(query.appearances[1].isManga)
    }

    @Test
    fun `mal and shikimori character ids map to mal`() {
        val mal = FandomCharacterQueries.from(detail("jikan"), "jikan", 164471)!!
        assertEquals(164471, mal.malCharacterId)
        assertEquals("mal:164471", mal.cacheKey())

        val shikimori = FandomCharacterQueries.from(detail("shikimori", id = 164471), "shikimori", 164471)!!
        assertEquals(164471, shikimori.malCharacterId)
    }

    @Test
    fun `characters without ids or verifiable names are skipped`() {
        val bare = detail("bangumi").copy(name = "五条悟", englishName = null, romanizedName = null)
        assertNull(FandomCharacterQueries.from(bare, "bangumi", 77))
    }
}

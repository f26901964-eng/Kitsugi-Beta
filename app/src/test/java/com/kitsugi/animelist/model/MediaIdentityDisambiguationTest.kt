package com.kitsugi.animelist.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIdentityDisambiguationTest {

    private fun testEntry(
        id: Int = 1,
        title: String,
        titleEnglish: String? = null,
        titleJapanese: String? = null,
        type: MediaType = MediaType.Anime,
        status: WatchStatus = WatchStatus.Completed,
        source: String = "kitsu",
        malId: Int? = null,
        year: Int? = null
    ): MediaEntry = MediaEntry(
        id = id,
        title = title,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        type = type,
        status = status,
        score = 8,
        progress = 1,
        total = 1,
        source = source,
        malId = malId,
        year = year
    )

    @Test
    fun franchiseMoviesWithDifferentEnglishTitlesDoNotMatchEvenIfJapaneseTitleIsIdentical() {
        // Date A Bullet: Dead or Bullet vs Date A Bullet: Nightmare or Queen
        // Both share the franchise Japanese title "デート・ア・バレット"
        val movie1 = testEntry(
            id = 1,
            title = "Date A Bullet: Dead or Bullet",
            titleEnglish = "Date A Bullet: Dead or Bullet",
            titleJapanese = "デート・ア・バレット",
            source = "kitsu",
            malId = 300_016_086
        )

        val movie2 = testEntry(
            id = 2,
            title = "Date A Bullet: Nightmare or Queen",
            titleEnglish = "Date A Bullet: Nightmare or Queen",
            titleJapanese = "デート・ア・バレット",
            source = "kitsu",
            malId = 300_016_140
        )

        // They must NOT be identified as the same media!
        val same = MediaIdentity.sameMedia(movie1, movie2, allowTitle = true)
        assertFalse("Franchise movies with distinct titles must not match merely by shared franchise Japanese title", same)
    }

    @Test
    fun identicalPrimaryTitlesMatchCorrectly() {
        val entry1 = testEntry(
            id = 1,
            title = "Date A Bullet: Nightmare or Queen",
            titleJapanese = "デート・ア・バレット",
            source = "kitsu",
            malId = 300_016_140
        )

        val entry2 = testEntry(
            id = 2,
            title = "Date A Bullet: Nightmare or Queen",
            titleJapanese = "デート・ア・バレット",
            source = "mal",
            malId = 42000
        )

        val same = MediaIdentity.sameMedia(entry1, entry2, allowTitle = true)
        assertTrue("Entries with identical primary title must match", same)
    }

    @Test
    fun primaryTitleMatchingEnglishTitleMatchesCorrectly() {
        val entryRomaji = testEntry(
            id = 1,
            title = "Shingeki no Kyojin",
            titleEnglish = "Attack on Titan",
            source = "mal",
            malId = 16498
        )

        val entryEnglish = testEntry(
            id = 2,
            title = "Attack on Titan",
            titleEnglish = "Attack on Titan",
            source = "kitsu",
            malId = 300_007_442
        )

        val same = MediaIdentity.sameMedia(entryRomaji, entryEnglish, allowTitle = true)
        assertTrue("Entry with English title matching other entry's primary title must match", same)
    }

    @Test
    fun shikimoriIdsAreNamespacedCorrectly() {
        val shikiEntry = testEntry(
            id = 1,
            title = "Some Anime",
            source = "shikimori",
            malId = 400_050_000 // Shikimori offset
        )

        val keys = MediaIdentity.keys(shikiEntry)
        assertTrue("Keys should contain Shikimori namespaced key", keys.contains("Anime:shikimori:50000"))
    }
}

package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * İçe aktarım tekilleştirme — "aynı yapım bir kaynakta iki satır olmasın".
 *
 * Simkl üç kategori döndürür (`movies`, `shows`, `anime`) ve aynı öğe birden fazla kategoride
 * yer alabilir; üç liste birleştirilirken aynı `simklId` iki kez geliyordu.
 */
class ProviderImportDedupeTest {

    private fun entry(
        id: Int = 0,
        title: String,
        type: MediaType,
        source: String = "simkl",
        simklId: Int? = null,
        progress: Int = 0,
        imageUrl: String? = null,
        malId: Int? = null
    ) = MediaEntry(
        id = id,
        title = title,
        type = type,
        status = WatchStatus.Completed,
        score = 8,
        progress = progress,
        total = 1,
        source = source,
        malId = malId,
        imageUrl = imageUrl,
        year = 2025,
        simklId = simklId
    )

    @Test
    fun animeMovieAppearingInBothMoviesAndAnimeCategoriesBecomesOneRow() {
        val fromMovies = entry(title = "Demon Slayer: Infinity Castle", type = MediaType.Movie, simklId = 2098300)
        val fromAnime = entry(title = "Demon Slayer: Infinity Castle", type = MediaType.Anime, simklId = 2098300)

        val collapsed = ProviderImportDedupe.collapse(listOf(fromMovies, fromAnime))

        assertEquals(1, collapsed.size)
        assertEquals("anime kategorisi tercih edilir", MediaType.Anime, collapsed.single().type)
    }

    @Test
    fun animeSeriesAppearingInShowsAndAnimeCategoriesBecomesOneRow() {
        val fromShows = entry(title = "Attack on Titan", type = MediaType.TvShow, simklId = 1234)
        val fromAnime = entry(title = "Attack on Titan", type = MediaType.Anime, simklId = 1234)

        val collapsed = ProviderImportDedupe.collapse(listOf(fromShows, fromAnime))

        assertEquals(1, collapsed.size)
        assertEquals(MediaType.Anime, collapsed.single().type)
    }

    @Test
    fun differentIdsAreDifferentProductionsEvenWithTheSameTitle() {
        val first = entry(title = "Frieren", type = MediaType.Anime, simklId = 111)
        val second = entry(title = "Frieren", type = MediaType.Anime, simklId = 222)

        assertEquals(2, ProviderImportDedupe.collapse(listOf(first, second)).size)
    }

    @Test
    fun entriesOfDifferentSourcesAreNeverCollapsed() {
        val simkl = entry(title = "Same title", type = MediaType.Anime, source = "simkl", simklId = 42)
        val kitsu = entry(title = "Same title", type = MediaType.Anime, source = "kitsu", malId = 300_000_042)

        assertEquals(2, ProviderImportDedupe.collapse(listOf(simkl, kitsu)).size)
    }

    @Test
    fun userProgressAndArtworkAreNotLostWhenCollapsing() {
        val withoutArt = entry(title = "Kaiju No. 8 Season 2", type = MediaType.Anime, simklId = 77, progress = 6)
        val withArt = entry(
            title = "Kaiju No. 8 Season 2",
            type = MediaType.Movie,
            simklId = 77,
            progress = 0,
            imageUrl = "https://example.invalid/poster.jpg"
        )

        val merged = ProviderImportDedupe.collapse(listOf(withoutArt, withArt)).single()

        assertEquals(6, merged.progress)
        assertEquals("https://example.invalid/poster.jpg", merged.imageUrl)
        assertEquals(MediaType.Anime, merged.type)
    }

    @Test
    fun identicalProviderIdEntriesAreRecognized() {
        val first = entry(id = 1, title = "Sousou no Frieren", type = MediaType.Anime, source = "mal", malId = 52991)
        val second = entry(id = 2, title = "Frieren: Beyond Journey's End", type = MediaType.Anime, source = "jikan", malId = 52991)

        assertTrue(ProviderImportDedupe.isSameProviderRecord(first, second))
    }
}

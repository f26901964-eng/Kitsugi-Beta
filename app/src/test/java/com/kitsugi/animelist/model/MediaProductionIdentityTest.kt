package com.kitsugi.animelist.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MediaIdentity.sameProduction` — aynı yapımın **farklı tür etiketiyle** gelen kopyaları.
 *
 * Kök neden: Simkl aynı anime filmini `movies` ve `anime` uçlarında birlikte döndürüyor,
 * kitaplığa `Movie` + `Anime` iki satır yazılıyordu. `sameMedia` tür eşitliği istediği için
 * bu iki satır hiçbir zaman birleşmiyordu (Listem'de çift kart).
 */
class MediaProductionIdentityTest {

    private fun entry(
        id: Int,
        title: String,
        type: MediaType,
        source: String = "simkl",
        simklId: Int? = null,
        tmdbId: Int? = null,
        malId: Int? = null,
        year: Int? = 2025
    ) = MediaEntry(
        id = id,
        title = title,
        type = type,
        status = WatchStatus.Completed,
        score = 8,
        progress = 1,
        total = 1,
        source = source,
        malId = malId,
        year = year,
        simklId = simklId,
        tmdbId = tmdbId
    )

    @Test
    fun simklMovieAndAnimeCopiesOfTheSameFilmAreOneProduction() {
        val asMovie = entry(id = 1, title = "Demon Slayer: Infinity Castle", type = MediaType.Movie, simklId = 2098300)
        val asAnime = entry(id = 2, title = "Demon Slayer: Infinity Castle", type = MediaType.Anime, simklId = 2098300)

        assertFalse("sameMedia tür eşitliği ister", MediaIdentity.sameMedia(asMovie, asAnime))
        assertTrue(
            "aynı simklId kanıt sayılır",
            MediaIdentity.sameProduction(asMovie, asAnime)
        )
    }

    @Test
    fun simklShowAndAnimeCopiesOfTheSameSeriesAreOneProduction() {
        val asShow = entry(id = 1, title = "Kaiju No. 8 Season 2", type = MediaType.TvShow, simklId = 4188123)
        val asAnime = entry(id = 2, title = "Kaiju No. 8 Season 2", type = MediaType.Anime, simklId = 4188123)

        assertTrue(MediaIdentity.sameProduction(asShow, asAnime))
    }

    @Test
    fun differentProductionsOfDifferentTypeLabelsStaySeparate() {
        val movie = entry(id = 1, title = "Frieren", type = MediaType.Movie, simklId = 111)
        val otherAnime = entry(id = 2, title = "Frieren", type = MediaType.Anime, simklId = 222)

        assertFalse(MediaIdentity.sameProduction(movie, otherAnime))
    }

    @Test
    fun titleSimilarityNeverMergesDifferentTypeLabels() {
        val movie = entry(id = 1, title = "Attack on Titan", type = MediaType.Movie)
        val anime = entry(id = 2, title = "Attack on Titan", type = MediaType.Anime)

        assertFalse(
            "tür etiketi farklıysa yalnızca kanıtlanmış kimlik birleştirir",
            MediaIdentity.sameProduction(movie, anime)
        )
    }

    @Test
    fun animeAndMangaNeverShareAProductionEvenWithTheSameNumericId() {
        // MAL anime 52991 ≠ MAL manga 52991; türler asla bu yolla birleşmez.
        val anime = entry(id = 1, title = "Sousou no Frieren", type = MediaType.Anime, source = "mal", malId = 52991)
        val manga = entry(id = 2, title = "Sousou no Frieren", type = MediaType.Manga, source = "mal", malId = 52991)

        assertFalse(MediaIdentity.sameProduction(anime, manga))
    }

    @Test
    fun tmdbIdIsNotCrossTypeEvidenceBecauseMovieAndTvIdsCollide() {
        // TMDB'de film 123 ile dizi 123 birbirinden alakasız yapımlardır.
        val movie = entry(id = 1, title = "Some Movie", type = MediaType.Movie, tmdbId = 123, simklId = null)
        val show = entry(id = 2, title = "Some Show", type = MediaType.TvShow, tmdbId = 123, simklId = null)

        assertFalse(MediaIdentity.sameProduction(movie, show))
    }

    @Test
    fun oneYearSkewStillMatchesButARemakeDoesNot() {
        // AniList sezon yılını, Kitsu/MAL prömiyer yılını saklar: Aralık/Ocak sınırında aynı
        // yapım iki kaynakta 1 yıl farklı görünür ve eskiden hiç birleşmiyordu.
        val anilistRow = entry(id = 1, title = "Sousou no Frieren", type = MediaType.Anime, source = "anilist", malId = 100_000_870, year = 2023)
        val kitsuRow = entry(id = 2, title = "Sousou no Frieren", type = MediaType.Anime, source = "kitsu", malId = 300_464_578, year = 2024)

        assertTrue(
            "±1 yıl farkı aynı yapımı ayırmamalı",
            MediaIdentity.sameMedia(anilistRow, kitsuRow)
        )

        val original = entry(id = 3, title = "Hunter x Hunter", type = MediaType.Anime, source = "mal", malId = 11061, year = 1999)
        val remake = entry(id = 4, title = "Hunter x Hunter", type = MediaType.Anime, source = "mal", malId = 136, year = 2011)
        assertFalse(
            "gerçek remake ayrı kalmalı",
            MediaIdentity.sameMedia(original, remake)
        )
    }

    @Test
    fun animeIsThePreferredTypeLabelForADuplicatedProduction() {
        assertEquals(0, MediaIdentity.preferredTypeRank(MediaType.Anime))
        assertTrue(
            MediaIdentity.preferredTypeRank(MediaType.Anime) <
                MediaIdentity.preferredTypeRank(MediaType.TvShow)
        )
        assertTrue(
            MediaIdentity.preferredTypeRank(MediaType.TvShow) <
                MediaIdentity.preferredTypeRank(MediaType.Movie)
        )
    }
}

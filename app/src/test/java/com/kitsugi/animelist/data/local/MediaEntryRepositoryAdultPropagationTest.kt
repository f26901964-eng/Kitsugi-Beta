package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * +18 işaretinin kaynaklar arası yayılımı.
 *
 * Blur kararı `isAdult` alanına dayanır ve bu alan satır bazlı tutulur. Simkl/Shikimori/
 * Bangumi liste API'leri yetişkin bayrağını taşımadığından aynı yapımın Simkl satırı
 * `false`, AniList satırı `true` olabiliyor → kullanıcı bulanıklığı bir sekmede görüp
 * diğerinde görmüyordu. Kök düzeltme: kanonik kimlik eşleşmesiyle yayılım.
 */
class MediaEntryRepositoryAdultPropagationTest {

    private fun entity(
        id: Int,
        source: String,
        title: String = "Kayıt $id",
        type: MediaType = MediaType.Anime,
        malId: Int? = null,
        tmdbId: Int? = null,
        simklId: Int? = null,
        isAdult: Boolean = false
    ) = MediaEntryEntity(
        id = id,
        title = title,
        subtitle = "Anime",
        type = type.name,
        status = "Watching",
        score = null,
        progress = 0,
        total = null,
        isFavorite = false,
        isAdult = isAdult,
        source = source,
        malId = malId,
        imageUrl = null,
        year = 2024,
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
        tmdbId = tmdbId,
        simklId = simklId
    )

    @Test
    fun adultFlagSpreadsToTheSameMediaOnOtherSources() {
        val anilist = entity(id = 1, source = "anilist", title = "Euphoria", malId = 1234, isAdult = true)
        val simkl = entity(id = 2, source = "simkl", title = "Euphoria", malId = 1234, isAdult = false)

        val mapped = listOf(anilist, simkl).withCrossSourceAdultFlags()

        assertTrue(mapped[0].isAdult)
        assertTrue("Aynı MAL kimliğindeki Simkl satırı da +18 sayılmalı", mapped[1].isAdult)
    }

    @Test
    fun differentIdentitiesAreNotBlurred() {
        val adult = entity(id = 1, source = "anilist", malId = 1234, isAdult = true)
        val other = entity(id = 2, source = "simkl", title = "Yuki Yuna", malId = 9999, isAdult = false)

        val mapped = listOf(adult, other).withCrossSourceAdultFlags()

        assertTrue(mapped[0].isAdult)
        assertFalse(mapped[1].isAdult)
    }

    @Test
    fun typeDifferenceKeepsEntriesSeparate() {
        // Aynı sayı farklı türde farklı bir kimliktir (Anime:mal:1 ≠ Manga:mal:1).
        val adultAnime = entity(id = 1, source = "anilist", type = MediaType.Anime, malId = 42, isAdult = true)
        val manga = entity(id = 2, source = "simkl", type = MediaType.Manga, malId = 42, isAdult = false)

        val mapped = listOf(adultAnime, manga).withCrossSourceAdultFlags()

        assertTrue(mapped[0].isAdult)
        assertFalse(mapped[1].isAdult)
    }

    @Test
    fun adultFlagIsNeverRemoved() {
        val flagged = entity(id = 1, source = "simkl", malId = 77, isAdult = true)

        val mapped = listOf(flagged).withCrossSourceAdultFlags()

        assertTrue(mapped.single().isAdult)
    }

    @Test
    fun emptyListMapsToEmptyList() {
        assertTrue(emptyList<MediaEntryEntity>().withCrossSourceAdultFlags().isEmpty())
    }

    @Test
    fun flaglessSourcesRequestAnAdultRescanAfterImport() {
        assertTrue(sourceNeedsAdultRescan("simkl"))
        assertTrue(sourceNeedsAdultRescan("Shikimori"))
        assertTrue(sourceNeedsAdultRescan("bangumi"))
        // Bu kaynaklar +18 bilgisini zaten listeyle birlikte veriyor → ekstra tura gerek yok.
        assertFalse(sourceNeedsAdultRescan("anilist"))
        assertFalse(sourceNeedsAdultRescan("mal"))
    }
}

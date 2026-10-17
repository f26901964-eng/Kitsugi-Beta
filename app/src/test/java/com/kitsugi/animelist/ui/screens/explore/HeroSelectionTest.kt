package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.components.homeHeroItemKey
import com.kitsugi.animelist.ui.components.shouldShowHeroLogo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class HeroSelectionTest {
    private fun item(
        id: Int,
        source: String = "anilist",
        type: MediaType = MediaType.Anime,
        backdrop: String? = "https://example.test/backdrop_$id.jpg"
    ) = JikanSearchResult(
        malId = id, title = "Title $id", subtitle = "", type = type,
        total = null, score = 8, isAdult = false,
        imageUrl = "https://example.test/poster_$id.jpg", year = 2026,
        source = source, backdropUrl = backdrop
    )

    private fun section(
        category: ExploreCategoryType,
        items: List<JikanSearchResult>,
        platform: ExplorePlatform = ExplorePlatform.AniList
    ) = ExploreSourceSection(platform, category, category.name, items)

    private val videoCategories = listOf(
        ExploreCategoryType.TOP_ANIME, ExploreCategoryType.TRENDING_ANIME,
        ExploreCategoryType.TOP_RATED_ANIME, ExploreCategoryType.AIRING_ANIME,
        ExploreCategoryType.UPCOMING_ANIME, ExploreCategoryType.MOVIE_ANIME,
        ExploreCategoryType.SEASONAL_ANIME, ExploreCategoryType.NEWLY_ADDED_ANIME
    )

    @Test fun allModeSelectsOneFromEverySourceShelfWithoutTheOldTwelveItemCap() {
        val sections = ExplorePlatform.sources.flatMap { platform ->
            videoCategories.mapIndexed { categoryIndex, category ->
                val base = platform.ordinal * 1000 + categoryIndex * 10
                section(category, (1..5).map { item(base + it, platform.name.lowercase()) }, platform)
            }
        }
        val heroes = allSourceHeroes(sections, random = Random(42))
        assertEquals(sections.size, heroes.size)
        sections.forEach { shelf -> assertEquals(1, heroes.count { hero -> hero in shelf.results }) }
    }

    @Test fun selectedSourceGetsTwoFromEachAvailableShelfAndTwoFromAiringSoon() {
        val sections = videoCategories.mapIndexed { i, category ->
            section(category, (1..5).map { item(i * 10 + it) })
        }
        val soon = (101..105).map { item(it) }
        val heroes = selectHeroItems(sections, airingSoon = soon, random = Random(7))
        assertEquals((videoCategories.size + 1) * 2, heroes.size)
        sections.forEach { shelf -> assertEquals(2, heroes.count { it in shelf.results }) }
        assertEquals(2, heroes.count { it in soon })
    }

    @Test fun missingOrSmallShelvesDoNotBorrowSlotsFromAnUnrelatedShelf() {
        val few = listOf(item(1))
        val many = (11..30).map { item(it) }
        val heroes = selectHeroItems(listOf(
            section(ExploreCategoryType.TOP_ANIME, few),
            section(ExploreCategoryType.TRENDING_ANIME, many),
            section(ExploreCategoryType.UPCOMING_ANIME, emptyList())
        ), random = Random(1))
        assertEquals(3, heroes.size)
        assertEquals(1, heroes.count { it in few })
        assertEquals(2, heroes.count { it in many })
    }

    @Test fun mangaManhwaAndNovelsNeverEnterEvenWithExcellentWideArtwork() {
        val books = (1..4).map { item(it, type = MediaType.Manga).copy(score = 10) } +
            item(5, source = "bangumi").copy(bangumiSubjectType = 1)
        val heroes = selectHeroItems(listOf(section(ExploreCategoryType.TOP_MANGA, books)))
        assertTrue(heroes.isEmpty())
    }

    @Test fun tmdbAndSimklVideoShelvesAreNotRejectedBecauseLegacyCategorySaysManga() {
        val tmdbTv = item(1, "tmdb", MediaType.TvShow)
        val tmdbMovie = item(2, "tmdb", MediaType.Movie)
        val simklAnime = item(3, "simkl")
        val sections = listOf(
            section(ExploreCategoryType.TOP_MANGA, listOf(tmdbTv), ExplorePlatform.TMDB),
            section(ExploreCategoryType.PUBLISHING_MANGA, listOf(tmdbMovie), ExplorePlatform.TMDB),
            section(ExploreCategoryType.TRENDING_MANGA, listOf(simklAnime), ExplorePlatform.SIMKL)
        )
        assertEquals(setOf(tmdbTv, tmdbMovie, simklAnime), allSourceHeroes(sections).toSet())
    }

    @Test fun noPosterFallbackAndNoBlankTitleEvenIfScoresAreHigher() {
        val valid = item(1)
        val absent = item(2, backdrop = null).copy(score = 10)
        val blank = item(3, backdrop = "   ")
        val posterAsBackdrop = item(4).let { it.copy(backdropUrl = it.imageUrl) }
        val blankTitle = item(5).copy(title = " ")
        val heroes = selectHeroItems(listOf(section(
            ExploreCategoryType.TOP_ANIME, listOf(valid, absent, blank, posterAsBackdrop, blankTitle)
        )))
        assertEquals(listOf(valid), heroes)
    }

    @Test fun knownPortraitUrlsAreNotTreatedAsBackdrops() {
        val urls = listOf(
            "https://lain.bgm.tv/pic/cover/l/example.jpg",
            "https://media.kitsu.app/anime/poster_images/1/large.jpg",
            "https://simkl.in/posters/example.jpg",
            "https://cdn.myanimelist.net/images/anime/1/2.jpg",
            "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx123.jpg"
        )
        val heroes = selectHeroItems(listOf(section(ExploreCategoryType.TOP_ANIME,
            urls.mapIndexed { i, url -> item(i, backdrop = url) }
        )))
        assertTrue(heroes.isEmpty())
    }

    @Test fun stableSeedIsReproducibleButDifferentSeedsReallyRandomize() {
        val sections = listOf(section(ExploreCategoryType.TOP_ANIME, (1..50).map { item(it) }))
        val first = selectHeroItems(sections, random = Random(123))
        assertEquals(first, selectHeroItems(sections, random = Random(123)))
        val choices = (1..20).map { seed -> selectHeroItems(sections, random = Random(seed)).map { it.malId } }.toSet()
        assertTrue(choices.size > 5)
    }

    @Test fun sourceQuotaIsStrictEvenWithAHugeSingleShelf() {
        val sections = listOf(section(ExploreCategoryType.TRENDING_ANIME, (1..50).map { item(it) }))
        assertEquals(2, selectHeroItems(sections).size)
        assertEquals(1, allSourceHeroes(sections).size)
        assertTrue(selectHeroItems(sections, itemsPerSection = 0).isEmpty())
    }

    @Test fun overlappingShelvesUseAlternativesRatherThanRepeatOrStarveTheSmallShelf() {
        val shared = item(1)
        val alternate = item(2)
        val sections = listOf(
            HomeHeroSection("popular", listOf(shared, alternate)),
            HomeHeroSection("trending", listOf(shared))
        )
        val picks = matchHomeHeroSections(sections, 1)
        assertEquals(2, picks.size)
        assertEquals(shared, picks.single { it.sectionKey == "trending" }.item)
        assertEquals(alternate, picks.single { it.sectionKey == "popular" }.item)
    }

    @Test fun duplicateAcrossAllShelvesAppearsOnlyOnce() {
        val shared = item(1)
        val sections = videoCategories.map { section(it, listOf(shared, shared)) }
        assertEquals(listOf(shared), selectHeroItems(sections))
    }

    @Test fun movieTvAndSourceIdentityCannotCollide() {
        val videos = listOf(item(10, "tmdb", MediaType.Movie), item(10, "tmdb", MediaType.TvShow), item(10, "anilist"))
        val heroes = selectHeroItems(listOf(section(ExploreCategoryType.TOP_ANIME, videos)), itemsPerSection = 3)
        assertEquals(videos.toSet(), heroes.toSet())
        assertEquals(3, heroes.distinctBy(::homeHeroItemKey).size)
    }

    @Test fun airingSoonIsOneSharedPoolInAllModeAndDeduplicatesAgainstOtherShelves() {
        val shared = item(1)
        val upcoming = item(2)
        val sections = listOf(section(ExploreCategoryType.TOP_ANIME, listOf(shared)))
        val heroes = allSourceHeroes(sections, airingSoon = listOf(shared, upcoming), random = Random(5))
        assertEquals(setOf(shared, upcoming), heroes.toSet())
        assertEquals(2, heroes.size)
    }

    @Test fun asyncPreparationFindsArtworkBeforeAdmissionAndTriesAnotherOnFailure() = runBlocking {
        val raw = (1..8).map { item(it, backdrop = null) }
        val prepared = resolveHomeHeroItems(listOf(HomeHeroSection("popular", raw)), 2, seed = 42) {
            if (it.malId <= 6) null else it.copy(backdropUrl = "https://example.test/wide_${it.malId}.jpg")
        }
        assertEquals(setOf(7, 8), prepared.map { it.malId }.toSet())
        assertTrue(prepared.all { !it.backdropUrl.isNullOrBlank() })
        assertTrue(prepared.all { it.tmdbId == null && it.source == "anilist" })
    }

    @Test fun asyncPreparationSkipsBooksAndSharesFailedLookupsAcrossShelves() = runBlocking {
        val missing = item(1, backdrop = null)
        val manga = item(2, type = MediaType.Manga)
        val calls = mutableListOf<Int>()
        val prepared = resolveHomeHeroItems(listOf(
            HomeHeroSection("popular", listOf(missing, manga)),
            HomeHeroSection("trending", listOf(missing, manga))
        ), 2, seed = 8) { calls += it.malId; null }
        assertTrue(prepared.isEmpty())
        assertEquals(listOf(1), calls)
    }

    @Test fun asyncPreparationBoundsConcurrentRequests() = runBlocking {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val sections = (1..10).map { HomeHeroSection("shelf_$it", listOf(item(it))) }
        val prepared = resolveHomeHeroItems(sections, 1, seed = 3, maxConcurrent = 3) {
            val n = active.incrementAndGet()
            maximum.updateAndGet { old -> maxOf(old, n) }
            delay(5)
            active.decrementAndGet()
            it
        }
        assertEquals(10, prepared.size)
        assertEquals(3, maximum.get())
        assertEquals(0, active.get())
    }

    @Test fun cancellationIsNeverSwallowedAsAnArtworkMiss() = runBlocking {
        var cancelled = false
        try {
            withTimeout(50) {
                resolveHomeHeroItems(listOf(HomeHeroSection("popular", listOf(item(1)))), 1, seed = 1) {
                    delay(1_000)
                    it
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun resolverErrorsDoNotAdmitAPosterOrBreakTheRemainingShelves() = runBlocking {
        val good = item(2)
        val prepared = resolveHomeHeroItems(listOf(
            HomeHeroSection("bad", listOf(item(1))), HomeHeroSection("good", listOf(good))
        ), 1, seed = 4) {
            if (it.malId == 1) error("unavailable source") else it
        }
        assertEquals(listOf(good), prepared)
    }

    @Test fun resolverCannotSilentlyReplaceProviderIdentity() = runBlocking {
        val prepared = resolveHomeHeroItems(listOf(HomeHeroSection("popular", listOf(item(1)))), 1, seed = 1) {
            it.copy(tmdbId = 123)
        }
        assertTrue(prepared.isEmpty())
    }

    @Test fun artworkLogoSourcesKeepTheirExistingPolicyAndBooksNeverRequestLogos() {
        assertTrue(shouldShowHeroLogo(item(1, "bangumi"), showAnimeLogos = false))
        assertTrue(shouldShowHeroLogo(item(2, "simkl"), showAnimeLogos = false))
        assertTrue(shouldShowHeroLogo(item(3, "shikimori"), showAnimeLogos = false))
        assertFalse(shouldShowHeroLogo(item(4), showAnimeLogos = false))
        assertTrue(shouldShowHeroLogo(item(4), showAnimeLogos = true))
        assertFalse(shouldShowHeroLogo(item(5, type = MediaType.Manga), showAnimeLogos = true))
    }
}

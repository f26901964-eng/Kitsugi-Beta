package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AllSourcesExploreTest {
    private fun media(source: String = "anilist", id: Int = 1, type: MediaType = MediaType.Anime, adult: Boolean = false) =
        JikanSearchResult(id, "Title $id", "", type, null, 8, adult, null, 2026, source)

    private fun payload(items: List<JikanSearchResult> = listOf(media())) = ExplorePayload(
        topAnime = items, airingAnime = emptyList(), upcomingAnime = emptyList(), topManga = emptyList(),
        publishingManga = emptyList(), trendingAnime = emptyList(), movieAnime = emptyList(), seasonalAnime = emptyList()
    )

    @Test fun allIsAModeNotASeventhApi() {
        assertEquals(6, ExplorePlatform.sources.size)
        assertFalse(ExplorePlatform.ALL in ExplorePlatform.sources)
    }

    @Test fun categoriesStayGroupedUnderTheirSource() {
        val states = ExplorePlatform.sources.reversed().associateWith {
            val item = media(it.name.lowercase())
            ExploreSourceState(payload(listOf(item)).copy(trendingAnime = listOf(item)))
        }
        val sections = allSourceSections(states, false)
        assertEquals(ExplorePlatform.sources.flatMap { listOf(it, it) }, sections.map { it.platform })
        assertEquals(sections.size, sections.map { it.key }.distinct().size)
        assertEquals(6, allSourceHeroes(states, false).size)
    }

    @Test fun movieAndTvIdsDoNotCollide() {
        val movie = media("tmdb", 10, MediaType.Movie)
        val show = media("tmdb", 10, MediaType.TvShow)
        val states = mapOf(ExplorePlatform.TMDB to ExploreSourceState(payload(listOf(movie, movie, show))))
        assertEquals(listOf(movie, show), allSourceSections(states, false).single().results)
    }

    @Test fun sourceIdentityAndMediaTypeArePreserved() {
        val anime = media("anilist", 1)
        val manga = media("anilist", 1, MediaType.Manga)
        val other = media("kitsu", 1)
        assertEquals(3, listOf(anime, manga, other).distinctBy { it.exploreIdentity() }.size)
    }

    @Test fun adultFilteringAppliesToEveryCategoryAndHero() {
        val adult = media(adult = true)
        val states = mapOf(ExplorePlatform.AniList to ExploreSourceState(payload(listOf(adult)).copy(topRatedAnime = listOf(adult))))
        assertTrue(allSourceSections(states, false).isEmpty())
        assertTrue(allSourceHeroes(states, false).isEmpty())
        assertEquals(2, allSourceSections(states, true).size)
        assertEquals(listOf(adult), allSourceHeroes(states, true))
    }

    @Test fun legacyKitsuFallbackIsNotShownUnderAniList() {
        val kitsu = media("kitsu")
        val cached = payload(listOf(kitsu))
        val states = mapOf(ExplorePlatform.AniList to ExploreSourceState(cached), ExplorePlatform.KITSU to ExploreSourceState(cached))
        assertEquals(listOf(ExplorePlatform.KITSU), allSourceSections(states, false).map { it.platform })
        assertFalse(cached.forSource(ExplorePlatform.AniList).hasCatalogContent())
    }

    @Test fun malAndJikanAliasesBelongToMal() {
        val cached = payload(listOf(media("jikan"), media("MAL", 2)))
        assertEquals(2, cached.forSource(ExplorePlatform.MAL).topAnime.size)
    }

    @Test fun tmdbShowsAreNotLabelledAsManga() {
        val item = media("tmdb", type = MediaType.TvShow)
        val p = payload(emptyList()).copy(topManga = listOf(item))
        val section = allSourceSections(mapOf(ExplorePlatform.TMDB to ExploreSourceState(p)), false).single()
        assertEquals("Popüler Diziler", section.title)
        assertEquals(ExplorePlatform.TMDB, section.platform)
        assertEquals(ExploreCategoryType.TOP_MANGA, section.category)
    }

    @Test fun scoreChartsAreDistinctFromPopularityCharts() {
        val popular = media()
        val rated = media(id = 2)
        val states = mapOf(ExplorePlatform.AniList to ExploreSourceState(payload(listOf(popular)).copy(topRatedAnime = listOf(rated))))
        val sections = allSourceSections(states, false)
        assertEquals(listOf(ExploreCategoryType.TOP_ANIME, ExploreCategoryType.TOP_RATED_ANIME), sections.map { it.category })
        assertEquals(listOf(rated), sections.last().results)
    }

    @Test fun sourceJumpIndicesAccountForCollapsedAndEmptyGroups() {
        val states = ExplorePlatform.sources.associateWith { ExploreSourceState(payload(listOf(media(it.name.lowercase())))) }
        val sections = allSourceSections(states, false).groupBy { it.platform }
        val expanded = allSourceHeaderIndices(sections, emptySet(), startIndex = 2)
        assertEquals(listOf(4, 7, 10, 13, 16, 19), ExplorePlatform.sources.map { expanded[it] })
        val collapsed = allSourceHeaderIndices(sections, setOf(ExplorePlatform.AniList), startIndex = 2)
        assertEquals(5, collapsed[ExplorePlatform.MAL])
        val empty = allSourceHeaderIndices(emptyMap(), emptySet(), startIndex = 1)
        assertEquals(listOf(3, 5, 7, 9, 11, 13), ExplorePlatform.sources.map { empty[it] })
    }

    @Test fun successfulCacheAvoidsNetwork() = runBlocking {
        val cached = payload()
        val state = loadExploreSource(cached, false, { error("Network must not be called") }, { error("Disk must not be called") })
        assertSame(cached, state.payload)
        assertNull(state.error)
    }

    @Test fun forcedRefreshReplacesCache() = runBlocking {
        val fresh = payload(listOf(media(id = 2)))
        val state = loadExploreSource(payload(), true, { fresh }, { null })
        assertSame(fresh, state.payload)
        assertNull(state.error)
    }

    @Test fun failedRefreshKeepsContentAndMarksItCached() = runBlocking {
        val cached = payload()
        val state = loadExploreSource(cached, true, { error("Offline") }, { null })
        assertSame(cached, state.payload)
        assertNotNull(state.error)
        assertTrue(state.isCached)
    }

    @Test fun emptyResponseFallsBackToDiskRatherThanCachingSuccess() = runBlocking {
        val offline = payload()
        val state = loadExploreSource(null, false, { payload(emptyList()) }, { offline })
        assertSame(offline, state.payload)
        assertNotNull(state.error)
        assertTrue(state.isCached)
    }

    @Test fun corruptDiskCacheDoesNotCrashTheFeed() = runBlocking {
        val state = loadExploreSource(null, false, { error("Offline") }, { error("Invalid JSON") })
        assertNull(state.payload)
        assertNotNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test fun slowSourceTimesOutIndependently() = runBlocking {
        val state = loadExploreSource(null, false, { awaitCancellation() }, { null }, timeoutMillis = 20)
        assertNull(state.payload)
        assertNotNull(state.error)
    }

    @Test fun cancellationDoesNotReadOfflineOrPublishSuccess() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var returned = false
        var diskRead = false
        val job = launch {
            loadExploreSource(null, false, { entered.complete(Unit); awaitCancellation() }, { diskRead = true; payload() })
            returned = true
        }
        entered.await()
        job.cancelAndJoin()
        assertFalse(returned)
        assertFalse(diskRead)
    }

    @Test fun fastAndFailedSourcesDoNotWaitForSlowSource() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val slow = async { loadExploreSource(null, false, { gate.await(); payload() }, { null }) }
        val fast = async { loadExploreSource(null, false, { payload() }, { null }) }
        val failed = async { loadExploreSource(null, false, { error("Unavailable") }, { null }) }
        assertNotNull(fast.await().payload)
        assertNotNull(failed.await().error)
        assertFalse(slow.isCompleted)
        gate.complete(Unit)
        assertNotNull(slow.await().payload)
    }
}

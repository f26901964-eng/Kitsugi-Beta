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

    @Test fun allIsAModeNotAnotherApi() {
        // Tümü bir görünüm modudur; Bangumi eklendikten sonra 7 gerçek API kaynağı vardır.
        assertEquals(7, ExplorePlatform.sources.size)
        assertFalse(ExplorePlatform.ALL in ExplorePlatform.sources)
        assertTrue(ExplorePlatform.BANGUMI in ExplorePlatform.sources)
    }

    @Test fun categoriesStayGroupedUnderTheirSource() {
        val states = ExplorePlatform.sources.reversed().associateWith {
            val item = media(it.name.lowercase())
            ExploreSourceState(payload(listOf(item)).copy(trendingAnime = listOf(item)))
        }
        val sections = allSourceSections(states, false)
        assertEquals(ExplorePlatform.sources.flatMap { listOf(it, it) }, sections.map { it.platform })
        assertEquals(sections.size, sections.map { it.key }.distinct().size)
        assertEquals(ExplorePlatform.sources.size, allSourceHeroes(states, false).size)
    }

    @Test fun allSourceHeroIncludesOneAttributedHighlightFromEachProvider() {
        val states = ExplorePlatform.sources.associateWith { platform ->
            ExploreSourceState(payload(listOf(media(platform.name.lowercase(), id = platform.ordinal + 1))))
        }

        val heroes = allSourceHeroes(states, showAdultContent = false)

        assertEquals(ExplorePlatform.sources.map { it.name.lowercase() }, heroes.map { it.source })
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

    @Test fun bangumiRealShelvesAreSeparateAndStayUnderBangumi() {
        val tv = media("bangumi", id = 41, type = MediaType.TvShow)
        val movie = media("bangumi", id = 42, type = MediaType.Movie)
        val p = payload(emptyList()).copy(bangumiTvShows = listOf(tv), bangumiMovies = listOf(movie))

        val sections = allSourceSections(mapOf(ExplorePlatform.BANGUMI to ExploreSourceState(p)), false)

        assertEquals(
            listOf(ExploreCategoryType.BANGUMI_TV, ExploreCategoryType.BANGUMI_MOVIES),
            sections.map { it.category }
        )
        assertEquals(listOf("Bangumi Dizileri", "Bangumi Filmleri"), sections.map { it.title })
        assertEquals(listOf(tv, movie), sections.map { it.results.single() })
        assertTrue(p.hasCatalogContent())
        assertTrue(p.forSource(ExplorePlatform.MAL).bangumiTvShows.orEmpty().isEmpty())
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
        assertEquals(listOf(4, 7, 10, 13, 16, 19, 22), ExplorePlatform.sources.map { expanded[it] })
        val collapsed = allSourceHeaderIndices(sections, setOf(ExplorePlatform.AniList), startIndex = 2)
        assertEquals(5, collapsed[ExplorePlatform.MAL])
        val empty = allSourceHeaderIndices(emptyMap(), emptySet(), startIndex = 1)
        assertEquals(listOf(3, 5, 7, 9, 11, 13, 15), ExplorePlatform.sources.map { empty[it] })
    }

    @Test fun airingSoonShelfDataSurvivesSourceFiltering() {
        // Ortak "Yakında Yayında" verisi gerçek kimliklerini taşır (anilist/mal/tmdb) ve hiçbir
        // kaynağa ait etiketlenmediğinden owned() filtresi bu alanı boşaltmamalı — aksi hâlde
        // Tümü modundan önbelleğe yazılan payload tek-kaynak görünümünde şeridi kaybederdi.
        val shared = media("anilist", id = 77)
        val kitsu = media("kitsu", id = 3)
        val payload = payload(listOf(kitsu)).copy(airingSoonAnime = listOf(shared))
        val filtered = payload.forSource(ExplorePlatform.KITSU)
        assertEquals(listOf(shared), filtered.airingSoonAnime)
        assertEquals(listOf(kitsu), filtered.topAnime)
    }

    @Test fun airingSoonShelfItemShiftsSourceJumpIndices() {
        val states = ExplorePlatform.sources.associateWith { ExploreSourceState(payload(listOf(media(it.name.lowercase())))) }
        val sections = allSourceSections(states, false).groupBy { it.platform }
        val withoutShelf = allSourceHeaderIndices(sections, emptySet(), startIndex = 2, extraItemsAfterIntro = 0)
        val withShelf = allSourceHeaderIndices(sections, emptySet(), startIndex = 2, extraItemsAfterIntro = 1)
        ExplorePlatform.sources.forEach { platform ->
            assertEquals(withoutShelf.getValue(platform) + 1, withShelf.getValue(platform))
        }
        // Şerit gösterilmiyorsa indeksler eski davranışla bire bir aynı kalır.
        assertEquals(withoutShelf, allSourceHeaderIndices(sections, emptySet(), startIndex = 2))
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

    @Test fun nativeKindShelvesAppearOnlyWhenSourceSupportsThem() {
        // Desteklemeyen kaynak (Kitsu): raf null/boş → bölüm YOK.
        val kitsuState = ExploreSourceState(payload(listOf(media("kitsu"))))
        assertTrue(
            allSourceSections(mapOf(ExplorePlatform.KITSU to kitsuState), false)
                .none { it.category == ExploreCategoryType.MANHWA_MANHUA || it.category == ExploreCategoryType.NOVELS }
        )

        // Destekleyen kaynak (Shikimori): dolu raflar kendi kaynağı altında görünür.
        val manhwa = media("shikimori", 7, MediaType.Manga)
        val novel = media("shikimori", 8, MediaType.Manga)
        val shikiState = ExploreSourceState(
            payload(listOf(media("shikimori")))
                .copy(manhwaManhua = listOf(manhwa), novels = listOf(novel))
        )
        val sections = allSourceSections(mapOf(ExplorePlatform.SHIKIMORI to shikiState), false)
        assertEquals(
            listOf(ExploreCategoryType.MANHWA_MANHUA, ExploreCategoryType.NOVELS),
            sections.filter { it.category == ExploreCategoryType.MANHWA_MANHUA || it.category == ExploreCategoryType.NOVELS }
                .map { it.category }
        )
        assertTrue(sections.all { it.platform == ExplorePlatform.SHIKIMORI })
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

package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSelectionTest {

    private fun item(
        id: Int,
        score: Int? = null,
        rawScoreDouble: Double? = null,
        members: Int? = null,
        favorites: Int? = null,
        rank: Int? = null,
        source: String = "anilist",
        type: MediaType = MediaType.Anime
    ) = JikanSearchResult(
        malId = id,
        title = "Title $id",
        subtitle = "",
        type = type,
        total = null,
        score = score,
        isAdult = false,
        imageUrl = "https://example.test/poster_$id.jpg",
        year = 2026,
        source = source,
        members = members,
        favorites = favorites,
        rank = rank,
        rawScoreDouble = rawScoreDouble
    )

    private fun section(
        category: ExploreCategoryType,
        items: List<JikanSearchResult>,
        platform: ExplorePlatform = ExplorePlatform.AniList
    ) = ExploreSourceSection(platform, category, category.name, items)

    // ── Normalizasyon ──────────────────────────────────────────────────────

    @Test fun qualityScoreKeepsTenScaleSources() {
        assertEquals(0.8, qualityScore(item(1, score = 8))!!, 0.0001)
        assertEquals(0.95, qualityScore(item(2, rawScoreDouble = 9.5))!!, 0.0001)
        assertNull(qualityScore(item(3, score = null)))
        assertNull(qualityScore(item(4, score = 0)))
    }

    @Test fun qualityScoreNormalizesHundredScaleSources() {
        // Simkl, simklRating*10 ile 0–100 arası değer üretir (ör. 8.5 → 85).
        assertEquals(0.85, qualityScore(item(1, source = "simkl", score = 85))!!, 0.0001)
        assertEquals(1.0, qualityScore(item(2, source = "simkl", score = 100))!!, 0.0001)
    }

    // ── Metrik ağırlıkları ─────────────────────────────────────────────────

    @Test fun missingMetricsAreRedistributedNotPenalized() {
        val onlyScore = item(1, score = 8)
        val fullMetrics = item(
            2, score = 8,
            members = 5_000_000, favorites = 1_000_000, rank = 1
        )
        // Aynı puan; tüm metrikleri güçlü olan aday öne çıkar...
        assertTrue(heroMetricScore(fullMetrics) > heroMetricScore(onlyScore))
        // ...eksik metrikli aday "sıfırla cezalandırılmaz": puanı 0.8'in hemen altındadır,
        // 0.5 gibi bir tabana çekilmez.
        assertEquals(0.8, heroMetricScore(onlyScore), 0.0001)
    }

    @Test fun strongQualityOutranksWeakPopularityBundle() {
        val greatShow = item(1, score = 10)
        val weakButPopular = item(
            2, score = 6, members = 50_000, favorites = 1_000, rank = 5_000
        )
        assertTrue(heroMetricScore(greatShow) > heroMetricScore(weakButPopular))
    }

    // ── Kategori bonusları ─────────────────────────────────────────────────

    @Test fun trendAndNewReleasesOutrankUpcomingUnderEqualMetrics() {
        assertTrue(
            heroCategoryBoost(ExploreCategoryType.TRENDING_ANIME) >
                heroCategoryBoost(ExploreCategoryType.TOP_ANIME)
        )
        assertTrue(
            heroCategoryBoost(ExploreCategoryType.NEWLY_ADDED_MANGA) >
                heroCategoryBoost(ExploreCategoryType.UPCOMING_ANIME)
        )
        assertTrue(
            heroCategoryBoost(ExploreCategoryType.TOP_RATED_MANGA) >
                heroCategoryBoost(ExploreCategoryType.PUBLISHING_MANGA)
        )
        assertEquals(1.12, heroCategoryBoost(ExploreCategoryType.BANGUMI_TV), 0.0001)
        assertEquals(1.12, heroCategoryBoost(ExploreCategoryType.BANGUMI_MOVIES), 0.0001)
        assertTrue(
            heroCategoryBoost(ExploreCategoryType.UPCOMING_MEDIA_TMDB) <
                heroCategoryBoost(ExploreCategoryType.TRENDING_ANIME)
        )
    }

    // ── Seçim davranışı ────────────────────────────────────────────────────

    @Test fun selectionRespectsLimitAndOpensWithStrongestCandidate() {
        val sections = listOf(
            section(ExploreCategoryType.TRENDING_ANIME, listOf(item(1, score = 6), item(2, score = 9))),
            section(ExploreCategoryType.TOP_MANGA, listOf(item(3, score = 7, type = MediaType.Manga)))
        )
        val heroes = selectHeroItems(sections, limit = 2)
        assertEquals(2, heroes.size)
        assertEquals(2, heroes.first().malId) // en yüksek puanlı aday vitrinin ilk sayfasında
    }

    @Test fun selectionDeduplicatesItemThatAppearsInMultipleSections() {
        val shared = item(1, score = 9)
        val sections = listOf(
            section(ExploreCategoryType.TOP_ANIME, listOf(shared)),
            section(ExploreCategoryType.TRENDING_ANIME, listOf(shared))
        )
        val heroes = selectHeroItems(sections, limit = 10)
        assertEquals(1, heroes.size)
    }

    @Test fun categoryCapsKeepSectionsMixedWhenPoolAllows() {
        val trending = (1..10).map { item(it, score = 9) }
        val manga = (11..20).map {
            item(it, score = 8, type = MediaType.Manga, source = "mal")
        }
        val heroes = selectHeroItems(
            sections = listOf(
                section(ExploreCategoryType.TRENDING_ANIME, trending),
                section(ExploreCategoryType.TOP_MANGA, manga, platform = ExplorePlatform.MAL)
            ),
            limit = 6,
            perCategoryCap = 3
        )
        assertEquals(6, heroes.size)
        val trendingCount = heroes.count { it.malId <= 10 }
        val mangaCount = heroes.count { it.malId >= 11 }
        assertEquals(3, trendingCount)
        assertEquals(3, mangaCount)
    }

    @Test fun strictCapsNeverLeaveTheHeroShort() {
        // Tek kategoride 10 aday var; tavan 3 ama vitrin 6 istiyor → kalan yerler dolmalı.
        val onlyTrending = (1..10).map { item(it, score = 8) }
        val heroes = selectHeroItems(
            sections = listOf(section(ExploreCategoryType.TRENDING_ANIME, onlyTrending)),
            limit = 6,
            perCategoryCap = 3
        )
        assertEquals(6, heroes.size)
    }

    @Test fun allModeGuaranteesEverySourceEvenWithUnbalancedScores() {
        // Bir kaynak çok güçlü, diğerleri zayıf olsa bile her kaynak temsil edilir.
        val states = ExplorePlatform.sources.associateWith { platform ->
            val score = if (platform == ExplorePlatform.AniList) 10 else 4
            val id = platform.ordinal + 1
            ExploreSourceState(
                ExplorePayload(
                    topAnime = listOf(
                        item(id, score = score, source = platform.name.lowercase())
                    ),
                    airingAnime = emptyList(), upcomingAnime = emptyList(), topManga = emptyList(),
                    publishingManga = emptyList(), trendingAnime = emptyList(),
                    movieAnime = emptyList(), seasonalAnime = emptyList()
                )
            )
        }
        val heroes = allSourceHeroes(states, showAdultContent = false)
        assertEquals(ExplorePlatform.sources.map { it.name.lowercase() }.toSet(), heroes.map { it.source }.toSet())
        // AniList en yüksek puanlı olduğu için listede yer alır ve güçlü adaylar öne çıkar.
        assertEquals("anilist", heroes.first().source)
    }

    @Test fun singleSourceModeDrawsFromAllSectionsNotJustTopAnime() {
        val sections = listOf(
            section(ExploreCategoryType.TOP_ANIME, (1..4).map { item(it, score = 7) }),
            section(ExploreCategoryType.TRENDING_ANIME, (5..8).map { item(it, score = 9) }),
            section(
                ExploreCategoryType.NEWLY_ADDED_MANGA,
                (9..12).map { item(it, score = 6, type = MediaType.Manga) }
            )
        )
        val heroes = selectHeroItems(sections, limit = HERO_LIMIT_SINGLE)
        assertEquals(HERO_LIMIT_SINGLE, heroes.size)
        // En yüksek puanlı trend öğeleri vitrinin başında.
        assertTrue(heroes.take(3).all { it.malId in 5..8 })
        // Manga, uygun şartlar altında (kategori tavanı sayesinde) listeye girer.
        assertTrue(heroes.any { it.type == MediaType.Manga })
    }
}

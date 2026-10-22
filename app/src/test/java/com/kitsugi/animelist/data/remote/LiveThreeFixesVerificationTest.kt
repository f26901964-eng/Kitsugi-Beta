package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canlı uçtan uca doğrulama:
 *  1) Simkl arama (`Kaiju`, #196710) + Keşfet (`anime/best/all-time`) + Tümünü Gör (`getExploreGenrePage`)
 *     gerçekten dolu puan (`score != null`, `rawScoreDouble != null`) döndürüyor mu?
 *  2) Bangumi Keşfet şeridi (`topAnime`) saniyeler içinde geliyor ve `hydrateSearchResultTitles`
 *     seçilen İngilizce başlık diline (`ENGLISH`) tam uyuyor mu?
 *  3) Bangumi `Frieren` (`subject_id = 400602` → `stableId = 500400602`) detayında ortak
 *     `Videolar` toplayıcısı birden çok kaynaktan çok sayıda fragman + OP + ED videosu topluyor mu?
 */
class LiveThreeFixesVerificationTest {

    @Test
    fun liveVerify_simklScores_bangumiEnglishSpeed_andUnifiedCrossSourceVideos() = runBlocking {
        // 1. SIMKL CANLI PUAN DOĞRULAMASI (Arama + Keşfet + Tümünü Gör)
        val simkl = SimklApiClient()
        val searchResults = simkl.search("Kaiju", type = "anime", limit = 10, page = 1)
        assertTrue(
            "Simkl search should return results, got empty",
            searchResults.isNotEmpty()
        )
        val ratedSearchCount = searchResults.count { (it.score ?: 0) > 0 && (it.rawScoreDouble ?: 0.0) > 0.0 }
        assertTrue(
            "Simkl search results should carry scores, got rated=$ratedSearchCount/${searchResults.size}: " +
                searchResults.joinToString { "${it.title}#${it.malId}=${it.score}/${it.rawScoreDouble}" },
            ratedSearchCount > 0
        )

        val bestAnime = simkl.getBestMedia("anime/best/all-time", MediaType.Anime, limit = 10)
        assertTrue("Simkl best anime shelf should not be empty", bestAnime.isNotEmpty())
        val ratedBestCount = bestAnime.count { (it.score ?: 0) > 0 }
        assertTrue(
            "Simkl Explore best shelf should carry scores, got rated=$ratedBestCount/${bestAnime.size}",
            ratedBestCount >= bestAnime.size / 2
        )

        val seeAllGenrePage = simkl.getExploreGenrePage("anime", "rank", page = 1, limit = 10)
        assertTrue("Simkl See All genre page should not be empty", seeAllGenrePage.isNotEmpty())
        val ratedSeeAllCount = seeAllGenrePage.count { (it.score ?: 0) > 0 }
        assertTrue(
            "Simkl See All page should carry scores, got rated=$ratedSeeAllCount/${seeAllGenrePage.size}",
            ratedSeeAllCount >= seeAllGenrePage.size / 2
        )

        // 2. BANGUMI HIZ + İNGİLİZCE DİL UYUMU DOĞRULAMASI
        val t0 = System.currentTimeMillis()
        val fastShelf = KitsugiBangumiClient.topAnime(limit = 15, cacheOnlyTitles = true)
        val firstPaintMs = System.currentTimeMillis() - t0
        assertTrue("Bangumi fast shelf should not be empty", fastShelf.isNotEmpty())
        assertTrue(
            "Bangumi fast first paint took too long: ${firstPaintMs}ms",
            firstPaintMs < 12_000L
        )

        val hydratedShelf = KitsugiBangumiClient.hydrateSearchResultTitles(fastShelf, thorough = true)
        val latinDisplayCount = hydratedShelf.count { item ->
            val displayed = item.getDisplayTitle("ENGLISH")
            displayed.isNotBlank() && !com.kitsugi.animelist.utils.PreferenceHelpers.hasCjkCharacters(displayed)
        }
        assertTrue(
            "Bangumi ENGLISH display titles must resolve to Latin/English, got $latinDisplayCount/${hydratedShelf.size}: " +
                hydratedShelf.take(5).joinToString { it.getDisplayTitle("ENGLISH") },
            latinDisplayCount >= (hydratedShelf.size * 2) / 3
        )

        // 3. ORTAK ÇAPRAZ KAYNAK VİDEOLAR SEKMESİ (Frieren — Bangumi #400602)
        val frierenStableId = 500_400_602
        val baseDetail = KitsugiBangumiDetailClient.fetchDetail(frierenStableId, MediaType.Anime)
        assertTrue("Bangumi Frieren native detail should load", baseDetail != null)

        val enrichedVideosDetail = KitsugiCrossSourceVideoAggregator.enrichDetailWithCrossSourceVideos(
            context = null,
            source = "bangumi",
            externalId = frierenStableId,
            mediaType = MediaType.Anime,
            detail = baseDetail!!,
            title = "Frieren: Beyond Journey's End",
            year = 2023
        )
        val videos = enrichedVideosDetail.unifiedVideos
        val distinctSources = videos.flatMap { it.allSources }.distinct()
        assertTrue(
            "Unified videos for Frieren should contain multiple videos across multiple sources, " +
                "got count=${videos.size}, sources=$distinctSources",
            videos.size >= 4 && distinctSources.size >= 2
        )
        assertTrue(
            "Unified videos for Frieren should include openings and endings, " +
                "ops=${enrichedVideosDetail.openings.size}, eds=${enrichedVideosDetail.endings.size}",
            enrichedVideosDetail.openings.isNotEmpty() && enrichedVideosDetail.endings.isNotEmpty()
        )
    }
}

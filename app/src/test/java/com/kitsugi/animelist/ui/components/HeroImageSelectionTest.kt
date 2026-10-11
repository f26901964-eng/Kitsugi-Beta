package com.kitsugi.animelist.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vitrin (Hero) görsel seçimi ve TMDB + Fanart.tv kalite sıralaması testleri:
 * - Dikey telefon vitrininde (`aspect < 1.10`, dikey mod) en kaliteli dikey poster (`posterUrl`) birincildir.
 * - Geniş / yatay vitrin alanlarında (yatay telefon, dikey tablet, yatay tablet, TV) en kaliteli
 *   yatay arka plan (`backdropUrl`) birincildir.
 * - Bangumi dikey kapakları (`/pic/cover/`) asla yatay arka plan olarak kabul edilmez.
 * - TMDB ve Fanart.tv adayları arasından en yüksek çözünürlüklü ve en yüksek puanlı
 *   dikey poster ile yatay arka plan otomatik seçilir.
 */
class HeroImageSelectionTest {

    private val poster = "https://image.tmdb.org/t/p/original/poster.jpg"
    private val backdrop = "https://image.tmdb.org/t/p/original/backdrop.jpg"

    @Test
    fun `wide landscape band prefers backdrop then poster`() {
        // Yatay tablet: 1200dp x 420dp → en-boy ~2.85
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 1200f,
            heroHeightDp = 420f,
            isLandscape = true
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    @Test
    fun `portrait tablet band prefers backdrop when band is wide`() {
        // Dikey tablet: 800dp x 500dp → en-boy 1.60 (>= 1.10)
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 800f,
            heroHeightDp = 500f,
            isLandscape = false
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    @Test
    fun `portrait phone hero prefers vertical poster then backdrop`() {
        // Dikey telefon: 400dp x 480dp → en-boy ~0.83 (< 1.10) → dikey poster birincil!
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 400f,
            heroHeightDp = 480f,
            isLandscape = false,
            mediaType = MediaType.Anime
        )
        assertEquals(listOf(poster, backdrop), candidates)
    }

    @Test
    fun `landscape phone hero prefers horizontal backdrop then poster`() {
        // Yatay telefon: 840dp x 270dp → yatay arka plan birincil!
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 840f,
            heroHeightDp = 270f,
            isLandscape = true,
            mediaType = MediaType.Anime
        )
        assertEquals(listOf(backdrop, poster), candidates)
    }

    @Test
    fun `manga always prefers vertical poster first`() {
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = backdrop,
            heroWidthDp = 1200f,
            heroHeightDp = 440f,
            isLandscape = true,
            mediaType = MediaType.Manga
        )
        assertEquals(listOf(poster, backdrop), candidates)
    }

    @Test
    fun `identical poster and backdrop deduplicate to single entry`() {
        val candidates = heroImageCandidates(
            posterUrl = poster,
            backdropUrl = poster,
            heroWidthDp = 1200f,
            heroHeightDp = 420f,
            isLandscape = true
        )
        assertEquals(listOf(poster), candidates)
    }

    @Test
    fun `bangumi cover in backdropUrl is rejected so it is never treated as horizontal backdrop`() {
        val bgmCommon = "https://lain.bgm.tv/r/400/pic/cover/l/c1/34/12345_abc.jpg"
        val bgmLarge = "https://lain.bgm.tv/pic/cover/l/c1/34/12345_abc.jpg"

        assertNull(KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(bgmLarge, bgmCommon, "bangumi"))
        assertEquals(bgmLarge, KitsugiHeroArtworkResolver.upgradeNativePosterUrl(bgmCommon, "bangumi"))

        val candidates = heroImageCandidates(
            posterUrl = bgmCommon,
            backdropUrl = bgmLarge,
            heroWidthDp = 1200f,
            heroHeightDp = 420f,
            isLandscape = true
        )
        assertEquals(listOf(bgmLarge), candidates)
    }

    @Test
    fun `tmdb w500 poster and w1280 backdrop are automatically upgraded to original`() {
        val w500Poster = "https://image.tmdb.org/t/p/w500/p1.jpg"
        val w1280Backdrop = "https://image.tmdb.org/t/p/w1280/b1.jpg"

        assertEquals(
            "https://image.tmdb.org/t/p/original/p1.jpg",
            KitsugiHeroArtworkResolver.upgradeNativePosterUrl(w500Poster, "tmdb")
        )
        assertEquals(
            "https://image.tmdb.org/t/p/original/b1.jpg",
            KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(w1280Backdrop, w500Poster, "tmdb")
        )
    }

    @Test
    fun `poster fallback in wide band uses upper bias and wide backdrop uses upper bias to protect character heads`() {
        assertEquals(
            BiasAlignment(0f, -0.25f),
            heroImageAlignment(chosenIsPoster = true, heroAspect = 1.78f)
        )
        assertEquals(
            BiasAlignment(0f, -0.20f),
            heroImageAlignment(chosenIsPoster = true, heroAspect = 0.80f)
        )
        assertEquals(
            BiasAlignment(0f, -0.32f),
            heroImageAlignment(chosenIsPoster = false, heroAspect = 2.40f)
        )
        assertEquals(
            Alignment.Center,
            heroImageAlignment(chosenIsPoster = false, heroAspect = 1.55f)
        )
    }

    @Test
    fun `artwork resolver selects highest quality vertical poster and horizontal backdrop from TMDB and Fanart`() {
        val lowResTmdbPoster = KitsugiHeroArtworkResolver.ArtworkCandidate(
            url = "https://image.tmdb.org/t/p/original/low_poster.jpg",
            provider = "TMDB",
            isBackdrop = false,
            width = 680,
            height = 1000,
            language = "ja",
            voteAverage = 5.0,
            voteCount = 1
        )
        val highResTmdbPoster = KitsugiHeroArtworkResolver.ArtworkCandidate(
            url = "https://image.tmdb.org/t/p/original/uhd_poster.jpg",
            provider = "TMDB",
            isBackdrop = false,
            width = 2000,
            height = 3000,
            language = "en",
            voteAverage = 8.8,
            voteCount = 24
        )
        val fanartPoster = KitsugiHeroArtworkResolver.ArtworkCandidate(
            url = "https://assets.fanart.tv/fanart/tv/1/tvposter/fanart_poster.jpg",
            provider = "Fanart.tv",
            isBackdrop = false,
            width = 1000,
            height = 1426,
            language = "en",
            likes = 12
        )

        val rankedPosters = KitsugiHeroArtworkResolver.selectBestPosterCandidates(
            candidates = listOf(lowResTmdbPoster, fanartPoster, highResTmdbPoster),
            preferredLanguage = "tr",
            fallbackPosterUrl = "https://cdn.myanimelist.net/images/anime/1/1.jpg"
        )
        assertEquals("https://image.tmdb.org/t/p/original/uhd_poster.jpg", rankedPosters.first())
        assertTrue(rankedPosters.contains("https://assets.fanart.tv/fanart/tv/1/tvposter/fanart_poster.jpg"))

        val hd720Backdrop = KitsugiHeroArtworkResolver.ArtworkCandidate(
            url = "https://image.tmdb.org/t/p/original/720p_bg.jpg",
            provider = "TMDB",
            isBackdrop = true,
            width = 1280,
            height = 720,
            language = "ja",
            voteAverage = 5.2,
            voteCount = 2
        )
        val uhd4kBackdrop = KitsugiHeroArtworkResolver.ArtworkCandidate(
            url = "https://image.tmdb.org/t/p/original/4k_textless_bg.jpg",
            provider = "TMDB",
            isBackdrop = true,
            width = 3840,
            height = 2160,
            language = null,
            voteAverage = 9.1,
            voteCount = 30
        )
        val fanartBackdrop = KitsugiHeroArtworkResolver.ArtworkCandidate(
            url = "https://assets.fanart.tv/fanart/tv/1/showbackground/fanart_bg.jpg",
            provider = "Fanart.tv",
            isBackdrop = true,
            width = 1920,
            height = 1080,
            language = null,
            likes = 18
        )

        val rankedBackdrops = KitsugiHeroArtworkResolver.selectBestBackdropCandidates(
            candidates = listOf(hd720Backdrop, fanartBackdrop, uhd4kBackdrop),
            preferredLanguage = "tr",
            fallbackBackdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/1.jpg"
        )
        assertEquals("https://image.tmdb.org/t/p/original/4k_textless_bg.jpg", rankedBackdrops.first())
        assertEquals("https://assets.fanart.tv/fanart/tv/1/showbackground/fanart_bg.jpg", rankedBackdrops[1])
    }
}

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
    fun `dynamic crop alignment protects the top of the artwork and centres it horizontally`() {
        // 16:9 arka plan + 2.47:1 geniş bant → görselin üstünden yalnızca ~%5 kesilir.
        val wide = heroImageAlignment(
            chosenIsPoster = false,
            heroAspect = 2.47f,
            imageAspect = 16f / 9f
        ) as BiasAlignment
        assertEquals(0f, wide.horizontalBias, 0.001f)
        assertEquals(-0.09f, wide.verticalBias, 0.02f)

        // Dikey poster dikey telefonda: %80'i görünür → neredeyse ortada, hafif üst odak.
        val posterPortrait = heroImageAlignment(
            chosenIsPoster = true,
            heroAspect = 0.83f,
            imageAspect = 0.67f
        ) as BiasAlignment
        assertEquals(-0.08f, posterPortrait.verticalBias, 0.03f)

        // Dikey poster çok geniş bir şeritte: yalnızca en üst kısım korunur (baş/yüz kesilmez).
        val posterWide = heroImageAlignment(
            chosenIsPoster = true,
            heroAspect = 2.5f,
            imageAspect = 0.67f
        ) as BiasAlignment
        assertTrue("poster üst sınırda yukarı odaklanmalı", posterWide.verticalBias <= -0.30f)
    }

    @Test
    fun `aspect mismatch falls back to contain with fill so wide art is never sliced`() {
        // Tam eşleşme ve küçük uyumsuzluk → kaplama (COVER)
        assertEquals(HeroArtworkFit.COVER, heroArtworkFit(boxAspect = 16f / 9f, imageAspect = 16f / 9f))
        assertEquals(HeroArtworkFit.COVER, heroArtworkFit(boxAspect = 2.07f, imageAspect = 16f / 9f))
        // Yatay görsel dikey panelde / dikey poster geniş bantta → tam görünür + dolgu
        assertEquals(HeroArtworkFit.CONTAIN_WITH_FILL, heroArtworkFit(boxAspect = 0.72f, imageAspect = 16f / 9f))
        assertEquals(HeroArtworkFit.CONTAIN_WITH_FILL, heroArtworkFit(boxAspect = 2.40f, imageAspect = 0.67f))
        // Poster + dikey panel: neredeyse tam oturur → kaplama
        assertEquals(HeroArtworkFit.COVER, heroArtworkFit(boxAspect = 0.72f, imageAspect = 0.67f))
        // Oran bilinmiyorsa (görsel henüz yüklenmedi) güvenli varsayılan
        assertEquals(HeroArtworkFit.COVER, heroArtworkFit(boxAspect = 2.4f, imageAspect = null))
    }

    @Test
    fun `landscape band is tall enough to show wide artwork almost uncropped`() {
        val landscapePhone = heroBandHeightDp(
            screenHeightDp = 476f,
            isLandscape = true,
            isTablet = false,
            isTv = false
        )
        // 730dp genişlikte 352dp band → ~2.07:1; 16:9 görselin ~%86'sı görünür.
        assertEquals(352f, landscapePhone, 1f)

        val landscapeTablet = heroBandHeightDp(
            screenHeightDp = 800f,
            isLandscape = true,
            isTablet = true,
            isTv = false
        )
        assertTrue("tablet bandı telefon bandından büyük olmalı", landscapeTablet >= 480f)

        val portraitPhone = heroBandHeightDp(
            screenHeightDp = 800f,
            isLandscape = false,
            isTablet = false,
            isTv = false
        )
        assertEquals(440f, portraitPhone, 1f)
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Vitrin görsel KALİTE politikası (HeroArtworkImagePolicy)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `hero target size scales with screen width and never decodes 4K into memory`() {
        // 1080px genişlikli telefon → 1.5x pay, 16:9 hedef
        val w = clampHeroTargetWidth(1080)
        assertEquals(1620, w)
        assertEquals(911, clampHeroTargetHeight(w))

        // Çok küçük ekranda bile kullanılabilir taban korunur
        assertTrue(clampHeroTargetWidth(320) >= 720)
        assertTrue(clampHeroTargetHeight(clampHeroTargetWidth(320)) >= 405)

        // 4K tablet/TV: hedef asla MAX tavanı geçmez (4K decode = ~33 MB yerine sınırlı bitmap)
        val wide = clampHeroTargetWidth(3840)
        assertEquals(HeroArtworkImagePolicy.MAX_TARGET_WIDTH_PX, wide)
        assertTrue(clampHeroTargetHeight(wide) <= HeroArtworkImagePolicy.MAX_TARGET_HEIGHT_PX)
        // RGB_565 yerine ARGB_8888 kullanıldığında bile bitmap tavanı makul kalmalı
        val bytes = wide.toLong() * clampHeroTargetHeight(wide) * 4L
        assertTrue("hero bitmap tavanı aşıldı: $bytes bayt", bytes <= 16L * 1024 * 1024)
    }

    @Test
    fun `hero target height keeps the cinematic 16-9 band ratio`() {
        val w = clampHeroTargetWidth(1080)
        val h = clampHeroTargetHeight(w)
        val ratio = w.toDouble() / h.toDouble()
        // 16:9 ≈ 1.778 — band oranı korunuyorsa kırpma gerekmez
        assertTrue("beklenen 16:9 bandı, bulunan $ratio", ratio in 1.70..1.85)
    }

    @Test
    fun `hero fit never slices a vertical poster inside the cinematic band`() {
        // Dikey poster (2:3 ≈ 0.667) + 16:9 bant (1.778) → fark eşiği aşılır → KIRPMA yok
        val fit = heroArtworkFit(
            boxAspect = 16f / 9f,
            imageAspect = 2f / 3f,
            tolerance = HERO_BANNER_FIT_TOLERANCE
        )
        assertEquals(HeroArtworkFit.CONTAIN_WITH_FILL, fit)

        // 16:9 görsel + 16:9 bant → hiç kırpma/dolgu gerekmez
        assertEquals(
            HeroArtworkFit.COVER,
            heroArtworkFit(
                boxAspect = 16f / 9f,
                imageAspect = 16f / 9f,
                tolerance = HERO_BANNER_FIT_TOLERANCE
            )
        )
    }
}

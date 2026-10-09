package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.model.MediaType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.utils.copyOnDoubleTap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.matches
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.0f
private const val HERO_CONTENT_PARALLAX = 0.18f

private fun heroItemIdentity(item: JikanSearchResult): String =
    "${item.source.trim().lowercase()}:${item.type.name}:${item.malId}"

private suspend fun fetchHeroLogo(item: JikanSearchResult): String? = try {
    val stableId = item.malId
    val isMovie = item.type == MediaType.Movie
    when {
        item.source.equals("tmdb", ignoreCase = true) -> {
            val tmdbId = item.tmdbId ?: stableId.takeIf { it > 0 }
            tmdbId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, isMovie = isMovie) }
        }
        item.source.equals("anilist", ignoreCase = true) -> {
            if (stableId in 100_000_001..199_999_999) {
                KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(
                    aniListId = stableId - 100_000_000,
                    fallbackMalId = item.realMalId,
                    isMovie = isMovie
                )
            } else {
                val malId = item.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: stableId.takeIf { it in 1..99_999_999 }
                malId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, isMovie = isMovie) }
            }
        }
        item.source.equals("kitsu", ignoreCase = true) -> {
            val kitsuLogo = if (stableId in 300_000_001..399_999_999) {
                KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(stableId - 300_000_000)
            } else null
            kitsuLogo?.takeIf { it.isNotBlank() }
                ?: item.realMalId?.takeIf { it in 1..99_999_999 }
                    ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, isMovie = isMovie) }
        }
        item.source.equals("simkl", ignoreCase = true) -> {
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 }
            val tmdbId = item.tmdbId?.takeIf { it > 0 }
            tmdbId?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, isMovie = isMovie) }
                ?.takeIf { it.isNotBlank() }
                ?: realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, isMovie = isMovie) }
        }
        item.source.equals("bangumi", ignoreCase = true) || item.source.equals("bgm", ignoreCase = true) -> {
            // Bangumi kimliği 500M+ stableId'dir; MAL olarak kesinlikle kullanma.
            val cross = KitsugiBangumiDetailClient.resolveCrossIds(stableId, item.type)
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 } ?: cross.malId
            val malLogo = realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, isMovie = isMovie) }
                ?.takeIf { it.isNotBlank() }
            val aniListLogo = if (malLogo.isNullOrBlank()) {
                cross.aniListId?.takeIf { it > 0 }
                    ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(it, isMovie = isMovie) }
                    ?.takeIf { it.isNotBlank() }
            } else null
            malLogo ?: aniListLogo ?: cross.tmdbId?.takeIf { it > 0 }
                ?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, isMovie = isMovie) }
        }
        item.source.equals("jikan", ignoreCase = true) ||
            item.source.equals("mal", ignoreCase = true) ||
            item.source.equals("myanimelist", ignoreCase = true) -> {
            stableId.takeIf { it in 1..99_999_999 }
                ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, isMovie = isMovie) }
        }
        item.source.equals("shikimori", ignoreCase = true) -> {
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 }
                ?: withContext(kotlinx.coroutines.Dispatchers.IO) {
                    KitsugiIdResolver.resolveMalIdFromShikimori(stableId)
                }
            realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, isMovie = isMovie) }
        }
        else -> null
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

// Kaynak adı etiketleri artık ortak `toFriendlySourceLabel()` üzerinden üretilir;
// vitrin çipi `KitsugiSourceNamePill` ile tüm kaynaklarda logo+isim gösterir.

/**
 * Vitrin görsel adayları — ekran boyutu (vitirin kutusunun en-boy oranı) ve
 * dikey/yatay moda göre ÖNCELİK SIRASIyla döndürülür:
 *
 * - Geniş vitrin bandı (yatay mod, dikey tablet bandı, TV vb. — en/boy ≥ 1.1):
 *   yatay dikdörtgen fanart/backdrop önce gelir; hem dikey hem yatay modda
 *   kutuyu kusursuz kaplar.
 * - Dikey-telefon vitrini (kareye yakın / dar kutu): dikey poster önce gelir.
 * - Her ikisi de mevcutsa diğeri yedek adaydır (yükleme başarısız olursa
 *   [KitsugiNsfwImage.onLoadingFailed] ile zincir devreye girer).
 * - İkisi de boşsa boş liste döner (başlık harfleriyle fallback çizilir).
 */
internal fun heroImageCandidates(
    posterUrl: String?,
    backdropUrl: String?,
    heroWidthDp: Float,
    heroHeightDp: Float,
    isLandscape: Boolean
): List<String> {
    val poster = posterUrl?.trim()?.takeIf { it.isNotEmpty() }
    val backdrop = backdropUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (poster == null) return listOfNotNull(backdrop)
    if (backdrop == null) return listOf(poster)
    // Ekran boyutuna göre gerçek vitrin kutusunun en-boy oranı; ölçüm yoksa
    // yön bilgisinden makul bir oran türetilir.
    val aspect = if (heroWidthDp > 0f && heroHeightDp > 0f) {
        heroWidthDp / heroHeightDp
    } else if (isLandscape) {
        16f / 9f
    } else {
        9f / 16f
    }
    val preferBackdrop = aspect >= 1.1f || (isLandscape && aspect >= 0.95f)
    return if (preferBackdrop) listOf(backdrop, poster) else listOf(poster, backdrop)
}

/**
 * Kaplayan (Crop) kırpmanın odak noktası:
 * - Yatay fanart/backdrop → merkez (özne bandı görselin ortasındadır).
 * - Poster geniş bantta kullanılmak zorunda kalırsa hafif yukarı bias
 *   (yüz/başlık bandı korunur, alt boşluklar kırpılır).
 * - Poster dikey/kare vitrinde → merkez.
 */
internal fun heroImageAlignment(chosenIsPoster: Boolean, heroAspect: Float): Alignment = when {
    !chosenIsPoster -> Alignment.Center
    heroAspect >= 1.15f -> BiasAlignment(0f, -0.2f)
    else -> Alignment.Center
}

@Composable
private fun HeroSourcePill(source: String) {
    // Tüm kaynaklarda logo + dostça isim gösteren ortak çip.
    KitsugiSourceNamePill(source = source)
}

private data class HeroPageLayer(
    val page: Int,
    val visibility: Float,
    val offset: Float,
)

internal data class HomeHeroLayout(
    val isTablet: Boolean,
    val isLandscape: Boolean,
    val heroHeight: Dp,
    val contentMaxWidth: Dp,
    val contentWidthFraction: Float,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val bottomFadeHeight: Dp,
    val logoWidthFraction: Float,
)

@Composable
fun KitsugiHeroSection(
    items: List<JikanSearchResult>,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onInfoClick: (JikanSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAnimeLogos: Boolean = false,
    isVisible: Boolean = true,
    blurAdultMedia: Boolean = false
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    val accentColor = LocalKitsugiAccent.current

    var logos by remember(items) { mutableStateOf<Map<String, String?>>(emptyMap()) }

    LaunchedEffect(items, showAnimeLogos) {
        if (items.none { shouldShowHeroLogo(it, showAnimeLogos) }) {
            logos = emptyMap()
            return@LaunchedEffect
        }
        val logoMap = logos.toMutableMap()
        val maxConcurrentLogoLookups = Semaphore(3)

        // Önce vitrinde açık olan öğeyi çöz; diğer kaynakların logoları, Bangumi'nin
        // çapraz-kimlik araması sürerken paralel yüklenip tamamlandıkça gösterilebilir.
        val priorityItems = buildList {
            val cur = pagerState.currentPage.coerceIn(items.indices)
            add(items[cur])
            items.indices.filter { it != cur }.forEach { add(items[it]) }
        }.distinctBy { heroItemIdentity(it) }
            .filter { shouldShowHeroLogo(it, showAnimeLogos) }
            .filterNot { logoMap.containsKey(heroItemIdentity(it)) }

        val logoCompletions = Channel<Pair<String, String?>>(Channel.UNLIMITED)
        val pending = priorityItems.map { item ->
            async {
                maxConcurrentLogoLookups.withPermit {
                    logoCompletions.send(heroItemIdentity(item) to fetchHeroLogo(item))
                }
            }
        }

        repeat(pending.size) {
            val (identity, logoUrl) = logoCompletions.receive()
            logoMap[identity] = logoUrl
            logos = logoMap.toMap()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || screenWidthDp > screenHeightDp
    val isTablet = if (isLandscape) screenHeightDp >= 600f else screenWidthDp >= 600f

    val isTvDevice = LocalIsTvDevice.current
    val isTv = isTvDevice
    val layout = remember(screenWidthDp, screenHeightDp, isTv, isLandscape, isTablet) {
        if (isTv) {
            HomeHeroLayout(
                isTablet = true,
                isLandscape = true,
                heroHeight = (screenHeightDp * 0.68f).coerceIn(380f, 480f).dp,
                contentMaxWidth = 720.dp,
                contentWidthFraction = 0.58f,
                contentHorizontalPadding = 48.dp,
                contentVerticalPadding = 28.dp,
                bottomFadeHeight = 220.dp,
                logoWidthFraction = 0.5f,
            )
        } else if (isLandscape) {
            if (isTablet) {
                HomeHeroLayout(
                    isTablet = true,
                    isLandscape = true,
                    heroHeight = (screenHeightDp * 0.48f).coerceIn(300f, 390f).dp,
                    contentMaxWidth = 640.dp,
                    contentWidthFraction = 0.56f,
                    contentHorizontalPadding = if (screenWidthDp >= 1000f) 52.dp else 36.dp,
                    contentVerticalPadding = 18.dp,
                    bottomFadeHeight = 180.dp,
                    logoWidthFraction = 0.52f,
                )
            } else {
                // Yatay modda telefon: yukarı aşağı kısa (kompakt), sağa sola uzun, alttaki blokları açıkta bırakır
                HomeHeroLayout(
                    isTablet = false,
                    isLandscape = true,
                    heroHeight = (screenHeightDp * 0.60f).coerceIn(210f, 265f).dp,
                    contentMaxWidth = 520.dp,
                    contentWidthFraction = 0.56f,
                    contentHorizontalPadding = 24.dp,
                    contentVerticalPadding = 12.dp,
                    bottomFadeHeight = 140.dp,
                    logoWidthFraction = 0.48f,
                )
            }
        } else {
            if (isTablet) {
                // Dikey tablet
                HomeHeroLayout(
                    isTablet = true,
                    isLandscape = false,
                    heroHeight = (screenHeightDp * 0.42f).coerceIn(400f, 500f).dp,
                    contentMaxWidth = 560.dp,
                    contentWidthFraction = 0.85f,
                    contentHorizontalPadding = 32.dp,
                    contentVerticalPadding = 20.dp,
                    bottomFadeHeight = 200.dp,
                    logoWidthFraction = 0.56f,
                )
            } else {
                // Dikey modda telefon: yukarı aşağı uzun, sağa sola dar, görseli kesmeyen ferah oran
                HomeHeroLayout(
                    isTablet = false,
                    isLandscape = false,
                    heroHeight = (screenHeightDp * 0.50f).coerceIn(390f, 460f).dp,
                    contentMaxWidth = 480.dp,
                    contentWidthFraction = 1f,
                    contentHorizontalPadding = 20.dp,
                    contentVerticalPadding = 18.dp,
                    bottomFadeHeight = 220.dp,
                    logoWidthFraction = 0.62f,
                )
            }
        }
    }

    val currentPage = pagerState.currentPage.coerceIn(items.indices)
    val visiblePages = listOf(
        currentPage,
        (currentPage - 1).coerceIn(items.indices),
        (currentPage + 1).coerceIn(items.indices)
    ).distinct()
        .mapNotNull { index ->
            val pageOffset = heroPageOffset(pagerState, index)
            val visibility = (1f - abs(pageOffset)).coerceIn(0f, 1f)
            if (visibility <= 0f) {
                null
            } else {
                HeroPageLayer(
                    page = index,
                    visibility = visibility,
                    offset = pageOffset
                )
            }
        }
        .sortedBy { it.visibility }

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible, isFocused, pagerState.currentPage, pagerState.isScrollInProgress, items.size) {
        if (items.size > 1 && isVisible && !isFocused && !pagerState.isScrollInProgress) {
            delay(5000L)
            val nextPage = (pagerState.currentPage + 1) % items.size
            coroutineScope.launch {
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(layout.heroHeight)
            .then(
                if (isTv) {
                    Modifier
                        .focusable()
                        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                            true
                                        } else false
                                    }
                                    Key.DirectionRight -> {
                                        if (pagerState.currentPage < items.size - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                            true
                                        } else false
                                    }
                                    Key.DirectionCenter,
                                    Key.Enter,
                                    Key.NumPadEnter -> {
                                        onInfoClick(items[pagerState.currentPage])
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                } else {
                    Modifier.homeHeroPagerGesture(
                        pagerState = pagerState,
                        itemCount = items.size,
                        coroutineScope = coroutineScope
                    )
                }
            )
            .then(
                if (isTv) {
                    Modifier
                        .border(
                            width = 3.dp,
                            color = if (isFocused) accentColor else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                        )
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                } else Modifier
            )
            .background(KitsugiColors.Background)
    ) {
        val heroWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // Ekran boyutuna göre gerçek vitrin kutusu ölçüsü — görsel seçimi ve
        // kaplayan kırpma bu en-boy oranına göre uyarlanır.
        val heroBoxWidthDp = maxWidth.value
        val heroBoxHeightDp = maxHeight.value
        val heroScrollScale = 1f
        val heroScrollTranslationY = 0f

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.01f }
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }

        visiblePages.forEach { layer ->
            val item = items[layer.page]
            val displayTitle = item.getDisplayTitle(titleLanguage)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isTv) {
                            Modifier
                        } else {
                            Modifier.tvClickable(
                                scaleOnFocus = false
                            ) { onInfoClick(item) }
                        }
                    )
            ) {
                // Vitrin görseli: ekran boyutu (kutu en-boy oranı) ve dikey/yatay moda göre
                // seçilen kaynak — geniş bantta yatay fanart/backdrop önce, dikey vitrinde
                // poster önce; kalan aday yükleme hatasında yedek olarak kullanılır.
                val heroImagePlan = remember(item, heroBoxWidthDp, heroBoxHeightDp, layout.isLandscape) {
                    heroImageCandidates(
                        posterUrl = item.imageUrl,
                        backdropUrl = item.backdropUrl,
                        heroWidthDp = heroBoxWidthDp,
                        heroHeightDp = heroBoxHeightDp,
                        isLandscape = layout.isLandscape
                    )
                }
                var heroImageIndex by remember(heroImagePlan) { mutableStateOf(0) }
                val heroImageModel = heroImagePlan.getOrNull(heroImageIndex)
                if (!heroImageModel.isNullOrBlank()) {
                    val heroBoxAspect = if (heroBoxHeightDp > 0f) heroBoxWidthDp / heroBoxHeightDp else 1f
                    val chosenIsPoster =
                        heroImageModel == item.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    // Kaplayan sunum (Cover): seçilen görsel vitrini BAŞTAN SONA KAPLAR.
                    // Fit/bulanık dolgu yok — yön ve ekran boyutu artık kırpma oranıyla uyumludur.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = layer.visibility
                                translationX = -layer.offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
                                translationY = heroScrollTranslationY
                                scaleX = HERO_BACKGROUND_SCALE * heroScrollScale
                                scaleY = HERO_BACKGROUND_SCALE * heroScrollScale
                            }
                    ) {
                        KitsugiNsfwImage(
                            model = heroImageModel,
                            contentDescription = displayTitle,
                            isAdult = item.isAdult,
                            blurAdultMedia = blurAdultMedia,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = heroImageAlignment(chosenIsPoster, heroBoxAspect),
                            onLoadingFailed = {
                                // Birincil görsel yüklenemezse sıradaki adaya düş
                                // (ör. backdrop başarısız → poster, poster başarısız → backdrop).
                                if (heroImageIndex < heroImagePlan.lastIndex) {
                                    heroImageIndex += 1
                                }
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = layer.visibility
                                translationX = -layer.offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
                                translationY = heroScrollTranslationY
                                scaleX = HERO_BACKGROUND_SCALE * heroScrollScale
                                scaleY = HERO_BACKGROUND_SCALE * heroScrollScale
                            }
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayTitle.take(2).uppercase(),
                            color = accentColor,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // 1. Yatay modda sol taraf gölgelendirmesi (metinlerin okunurluğu ve sağda görselin parlaması için - NuvioTV tarzı)
        if (layout.isLandscape || layout.isTablet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to KitsugiColors.Background.copy(alpha = 0.92f),
                                0.24f to KitsugiColors.Background.copy(alpha = 0.78f),
                                0.48f to KitsugiColors.Background.copy(alpha = 0.46f),
                                0.70f to KitsugiColors.Background.copy(alpha = 0.14f),
                                0.88f to androidx.compose.ui.graphics.Color.Transparent,
                                1.0f to androidx.compose.ui.graphics.Color.Transparent
                            ),
                            startX = 0f,
                            endX = heroWidthPx * if (layout.isLandscape) 0.65f else 0.50f
                        )
                    )
            )
        }

        // 2. Üst durum çubuğu ve genel kart ton geçişi — resmin büyük kısmı net kalsın diye
        //    (yukarıda durum çubuğu için hafif gölge, ortada transparan, aşağıda metin için geçiş)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to KitsugiColors.Background.copy(alpha = 0.36f),
                            0.16f to KitsugiColors.Background.copy(alpha = 0.08f),
                            0.38f to androidx.compose.ui.graphics.Color.Transparent,
                            0.62f to KitsugiColors.Background.copy(alpha = 0.10f),
                            0.78f to KitsugiColors.Background.copy(alpha = 0.42f),
                            0.91f to KitsugiColors.Background.copy(alpha = 0.78f),
                            1.0f to KitsugiColors.Background
                        )
                    )
                )
        )

        // 3. Alt blokların arkasına doğru uzanan pürüzsüz dip geçiş katmanı (NuvioTV tarzı %100 kusursuz birleşme)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.bottomFadeHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.40f to KitsugiColors.Background.copy(alpha = 0.22f),
                            0.72f to KitsugiColors.Background.copy(alpha = 0.58f),
                            1.0f to KitsugiColors.Background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = layout.contentHorizontalPadding,
                    vertical = layout.contentVerticalPadding
                ),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(layout.contentWidthFraction)
                    .widthIn(max = layout.contentMaxWidth),
                contentAlignment = Alignment.CenterStart
            ) {
                visiblePages.forEach { layer ->
                    val item = items[layer.page]
                    val displayTitle = item.getDisplayTitle(titleLanguage)
                    val alreadyInList = alreadyInList(item)

                    val context = LocalContext.current
                    val copyTitleGesture = Modifier.copyOnDoubleTap(context, displayTitle)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = layer.visibility
                                translationX = -layer.offset * heroWidthPx * HERO_CONTENT_PARALLAX
                            }
                            .then(
                                if (isTv) {
                                    Modifier
                                } else {
                                    Modifier.tvClickable(
                                        shape = RoundedCornerShape(12.dp),
                                        scaleOnFocus = false
                                    ) { onInfoClick(item) }
                                }
                            ),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
                        ) {
                            Text(
                                text = "ÖNE ÇIKAN",
                                color = accentColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black
                            )

                            HeroSourcePill(item.source)

                            if (alreadyInList) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(KitsugiColors.AccentGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "LİSTEDE",
                                        color = KitsugiColors.AccentGreen,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(if (layout.isLandscape) 4.dp else 8.dp))

                        val logoUrl = if (shouldShowHeroLogo(item, showAnimeLogos)) logos[heroItemIdentity(item)] else null
                        if (!logoUrl.isNullOrBlank()) {
                            var logoFailed by remember(logoUrl) { mutableStateOf(false) }
                            if (!logoFailed) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = displayTitle,
                                    modifier = Modifier
                                        .align(Alignment.Start)
                                        .height(if (layout.isLandscape) 56.dp else if (layout.isTablet) 86.dp else 68.dp)
                                        .fillMaxWidth(0.85f)
                                        .then(copyTitleGesture),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                    onError = {
                                        logoFailed = true
                                    }
                                )
                            } else {
                                Text(
                                    text = displayTitle,
                                    color = KitsugiColors.TextPrimary,
                                    style = if (layout.isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(copyTitleGesture)
                                )
                            }
                        } else {
                            Text(
                                text = displayTitle,
                                color = KitsugiColors.TextPrimary,
                                style = if (layout.isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(copyTitleGesture)
                            )
                        }

                        if (item.subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(if (layout.isLandscape) 3.dp else 6.dp))
                            Text(
                                text = item.subtitle,
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (layout.isLandscape) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val heroMeta = buildHeroMeta(item, scoreFormat, hideScores)
                        if (heroMeta.isNotBlank()) {
                            Spacer(modifier = Modifier.height(if (layout.isLandscape) 4.dp else 8.dp))
                            Text(
                                text = heroMeta,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (items.size > 1) {
                Spacer(modifier = Modifier.height(if (layout.isLandscape) 8.dp else 12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, _ ->
                        val activeFraction = heroPageVisibility(pagerState, index)
                        Box(
                            modifier = Modifier
                                .then(
                                    if (isTv) {
                                        Modifier
                                    } else {
                                        Modifier.tvClickable(
                                            shape = CircleShape
                                        ) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                    }
                                )
                                .clip(CircleShape)
                                .background(if (isTv && isFocused && activeFraction > 0.5f) accentColor else KitsugiColors.TextPrimary)
                                .graphicsLayer {
                                    alpha = 0.35f + (0.57f * activeFraction)
                                }
                                .width(8.dp + (20.dp * activeFraction))
                                .height(6.dp)
                        )
                    }
                }
            }
        }
    }
}
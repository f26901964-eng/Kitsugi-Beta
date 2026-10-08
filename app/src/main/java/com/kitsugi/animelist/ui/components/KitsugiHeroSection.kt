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
import com.kitsugi.animelist.utils.copyOnDoubleTap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.0f
private const val HERO_CONTENT_PARALLAX = 0.18f

private fun heroItemIdentity(item: JikanSearchResult): String =
    "${item.source.trim().lowercase()}:${item.type.name}:${item.malId}"

private fun heroSourceLabel(source: String): String? = when (source.trim().lowercase()) {
    "anilist", "al" -> "AniList"
    "mal", "jikan", "jikan (mal)", "mal (jikan)" -> "MAL"
    "tmdb", "themoviedb" -> "TMDB"
    "simkl" -> "Simkl"
    "kitsu" -> "Kitsu"
    "shikimori", "shiki" -> "Shikimori"
    "bangumi", "bgm" -> "Bangumi"
    else -> null
}

@Composable
private fun HeroSourcePill(source: String) {
    val label = heroSourceLabel(source) ?: return
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(KitsugiColors.Background.copy(alpha = 0.72f))
            .border(1.dp, KitsugiColors.TextPrimary.copy(alpha = 0.16f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KitsugiPlatformLogo(platformId = source, size = 17.dp)
        Text(
            text = label,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
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

    var logos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    LaunchedEffect(items, showAnimeLogos) {
        if (!showAnimeLogos) {
            logos = emptyMap()
            return@LaunchedEffect
        }
        val logoMap = mutableMapOf<String, String?>()

        val priorityIndices = buildList {
            val cur = pagerState.currentPage.coerceIn(items.indices)
            add(cur)
            items.indices.filter { it != cur }.forEach { add(it) }
        }

        for (idx in priorityIndices) {
            val item = items[idx]
            if (item.type == MediaType.Manga) continue
            val stableId = item.malId
            val logoUrl = when {
                item.source.equals("tmdb", ignoreCase = true) -> {
                    val tmdbId = item.tmdbId ?: if (stableId > 0) stableId else null
                    if (tmdbId != null && tmdbId > 0) KitsugiEpisodeRatingsRepository.getLogoUrl(tmdbId) else null
                }
                item.source.equals("anilist", ignoreCase = true) -> {
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(
                            aniListId = aniListId,
                            fallbackMalId = item.realMalId
                        )
                    } else {
                        KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId)
                    }
                }
                item.source.equals("kitsu", ignoreCase = true) -> {
                    if (stableId >= 300_000_000) {
                        val kitsuId = stableId - 300_000_000
                        KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(kitsuId)
                    } else if (stableId > 0) {
                        // malId gerçek MAL ID ise (Kitsu import onu saklamış olabilir)
                        KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId)
                    } else null
                }
                item.source.equals("simkl", ignoreCase = true) -> {
                    val realMalId = item.realMalId
                    val tmdbId = item.tmdbId
                    when {
                        realMalId != null && realMalId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(realMalId)
                        tmdbId != null && tmdbId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrl(tmdbId)
                        else -> null
                    }
                }
                item.source.equals("jikan", ignoreCase = true) ||
                item.source.equals("mal", ignoreCase = true) ||
                item.source.equals("shikimori", ignoreCase = true) -> {
                    val aniListFallback = if (stableId >= 100_000_000) stableId - 100_000_000 else null
                    KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(
                        malId = stableId,
                        fallbackAniListId = aniListFallback
                    )
                }
                stableId > 0 && !item.source.equals("simkl", ignoreCase = true) -> {
                    val aniListFallback = if (stableId >= 100_000_000) stableId - 100_000_000 else null
                    KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(
                        malId = stableId,
                        fallbackAniListId = aniListFallback
                    )
                }
                else -> null
            }
            logoMap[heroItemIdentity(item)] = logoUrl
        }
        logos = logoMap.toMap()
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
                // Dikey moddayken dikey poster görseli (imageUrl), yatay moddayken yatay arka plan (backdropUrl) önceliklidir
                val heroImageModel = if (layout.isLandscape) {
                    item.backdropUrl?.takeIf { it.isNotBlank() } ?: item.imageUrl
                } else {
                    item.imageUrl?.takeIf { it.isNotBlank() } ?: item.backdropUrl
                }
                if (!heroImageModel.isNullOrBlank()) {
                    // 1. Ana net görsel — dikeyde üstten hizalı poster, yatayda ortalanmış sinematik görsel
                    KitsugiNsfwImage(
                        model = heroImageModel,
                        contentDescription = displayTitle,
                        isAdult = item.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = layer.visibility
                                translationX = -layer.offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
                                translationY = heroScrollTranslationY
                                scaleX = HERO_BACKGROUND_SCALE * heroScrollScale
                                scaleY = HERO_BACKGROUND_SCALE * heroScrollScale
                            },
                        contentScale = ContentScale.Crop,
                        alignment = if (layout.isLandscape) Alignment.Center else Alignment.TopCenter
                    )
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
                                0.0f to KitsugiColors.Background.copy(alpha = 0.95f),
                                0.22f to KitsugiColors.Background.copy(alpha = 0.88f),
                                0.44f to KitsugiColors.Background.copy(alpha = 0.52f),
                                0.68f to KitsugiColors.Background.copy(alpha = 0.16f),
                                0.88f to androidx.compose.ui.graphics.Color.Transparent,
                                1.0f to androidx.compose.ui.graphics.Color.Transparent
                            ),
                            startX = 0f,
                            endX = heroWidthPx * if (layout.isLandscape) 0.65f else 0.50f
                        )
                    )
            )
        }

        // 2. Üst durum çubuğu ve genel kart ton geçişi (yukarıda hafif gölge, ortada transparan, aşağıda soluk geçiş)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to KitsugiColors.Background.copy(alpha = 0.50f),
                            0.18f to KitsugiColors.Background.copy(alpha = 0.15f),
                            0.35f to androidx.compose.ui.graphics.Color.Transparent,
                            0.55f to KitsugiColors.Background.copy(alpha = 0.30f),
                            0.72f to KitsugiColors.Background.copy(alpha = 0.65f),
                            0.88f to KitsugiColors.Background.copy(alpha = 0.92f),
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
                            0.35f to KitsugiColors.Background.copy(alpha = 0.40f),
                            0.70f to KitsugiColors.Background.copy(alpha = 0.85f),
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

                        val logoUrl = if (showAnimeLogos) logos[heroItemIdentity(item)] else null
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
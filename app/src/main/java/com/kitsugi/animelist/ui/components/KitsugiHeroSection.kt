package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.alpha
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.utils.copyOnDoubleTap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import kotlin.math.round
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.0f
private const val HERO_CONTENT_PARALLAX = 0.18f

private fun heroItemIdentity(item: JikanSearchResult): String =
    "${item.source.trim().lowercase()}:${item.type.name}:${item.malId}"

private suspend fun fetchHeroLogo(item: JikanSearchResult): String? = try {
    val stableId = item.malId
    val logoTitles = listOfNotNull(item.title, item.titleEnglish, item.titleJapanese, item.titleRomaji)
        .filter { it.isNotBlank() }.distinct()
    val isMovie = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
        type = item.type,
        subtitle = item.subtitle,
        title = listOfNotNull(item.title, item.titleEnglish).joinToString(" | ")
    )
    val season = KitsugiEpisodeRatingsRepository.determineTargetSeason(
        tmdbSeason = null,
        title = item.title,
        titleEnglish = item.titleEnglish,
        synonyms = emptyList()
    ).takeIf { it > 1 }
    when {
        item.source.equals("tmdb", ignoreCase = true) -> {
            val tmdbId = item.tmdbId ?: stableId.takeIf { it > 0 }
            tmdbId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("anilist", ignoreCase = true) -> {
            if (stableId in 100_000_001..199_999_999) {
                KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(
                    aniListId = stableId - 100_000_000,
                    fallbackMalId = item.realMalId,
                    seasonNumber = season,
                    isMovie = isMovie,
                    expectedTitles = logoTitles
                )
            } else {
                val malId = item.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: stableId.takeIf { it in 1..99_999_999 }
                malId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
            }
        }
        item.source.equals("kitsu", ignoreCase = true) -> {
            val kitsuLogo = if (stableId in 300_000_001..399_999_999) {
                KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(stableId - 300_000_000, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
            } else null
            kitsuLogo?.takeIf { it.isNotBlank() }
                ?: item.realMalId?.takeIf { it in 1..99_999_999 }
                    ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("simkl", ignoreCase = true) -> {
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 }
            val tmdbId = item.tmdbId?.takeIf { it > 0 }
            tmdbId?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
                ?.takeIf { it.isNotBlank() }
                ?: realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("bangumi", ignoreCase = true) || item.source.equals("bgm", ignoreCase = true) -> {
            // Bangumi kimliği 500M+ stableId'dir; MAL olarak kesinlikle kullanma.
            val cross = KitsugiBangumiDetailClient.resolveCrossIds(stableId, item.type)
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 } ?: cross.malId
            val malLogo = realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
                ?.takeIf { it.isNotBlank() }
            val aniListLogo = if (malLogo.isNullOrBlank()) {
                cross.aniListId?.takeIf { it > 0 }
                    ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
                    ?.takeIf { it.isNotBlank() }
            } else null
            malLogo ?: aniListLogo ?: cross.tmdbId?.takeIf { it > 0 }
                ?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("jikan", ignoreCase = true) ||
            item.source.equals("mal", ignoreCase = true) ||
            item.source.equals("myanimelist", ignoreCase = true) -> {
            stableId.takeIf { it in 1..99_999_999 }
                ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("shikimori", ignoreCase = true) -> {
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 }
                ?: withContext(kotlinx.coroutines.Dispatchers.IO) {
                    KitsugiIdResolver.resolveMalIdFromShikimori(stableId)
                }
            realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
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
 * Vitrin görseli için birincil ve yedek URL sırasını belirler.
 *
 * - **Dikey ekran / dar vitrin (`aspect < 1.10` ve dikey mod — ör. dikey telefon)**:
 *   en kaliteli **dikey poster** (`posterUrl`) birincildir, `backdropUrl` yedektir.
 * - **Yatay ekran / geniş vitrin bandı (`aspect >= 1.10` veya yatay mod — ör. yatay telefon,
 *   dikey tablet geniş vitrin, yatay tablet, TV)**:
 *   anime, dizi ve filmlerde en kaliteli **yatay arka plan** (`backdropUrl`) birincildir,
 *   `posterUrl` yedektir.
 * - **Manga**: her zaman dikey kapak (`posterUrl`) birincildir.
 */
internal fun heroImageCandidates(
    posterUrl: String?,
    backdropUrl: String?,
    heroWidthDp: Float,
    heroHeightDp: Float,
    isLandscape: Boolean,
    mediaType: MediaType = MediaType.Anime
): List<String> {
    val poster = KitsugiHeroArtworkResolver.upgradeNativePosterUrl(posterUrl)
    val backdrop = KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(backdropUrl, poster)
    if (poster == null) return listOfNotNull(backdrop)
    if (backdrop == null) return listOf(poster)
    if (poster == backdrop) return listOf(backdrop)

    val aspect = if (heroWidthDp > 0f && heroHeightDp > 0f) {
        heroWidthDp / heroHeightDp
    } else if (isLandscape) {
        16f / 9f
    } else {
        9f / 16f
    }
    val preferBackdrop = mediaType != MediaType.Manga &&
        (aspect >= 1.10f || (isLandscape && aspect >= 0.95f))
    return if (preferBackdrop) listOf(backdrop, poster) else listOf(poster, backdrop)
}

/**
 * Vitrin görselinin kırpma hizalaması:
 * - Dikey poster geniş bir kutuda gösterildiğinde üst-orta (`-0.25f`) odaklanır.
 * - Dikey poster dikey telefonda gösterildiğinde hafif üst-orta (`-0.20f`) odaklanır ki
 *   karakterlerin başı/yüzü üstten kesilmesin, kırpma alt metin scrim'inin arkasında kalsın.
 * - Yatay (16:9) arka plan çok geniş bir şeritte (`heroAspect >= 1.85f`, ör. yatay tablet/telefon)
 *   gösterildiğinde üst-orta (`-0.32f`) odaklanır ki karakterlerin kafası üstten kesilmesin.
 */
internal fun heroImageAlignment(
    chosenIsPoster: Boolean,
    heroAspect: Float
): Alignment = when {
    chosenIsPoster && heroAspect >= 1.15f -> BiasAlignment(0f, -0.25f)
    chosenIsPoster -> BiasAlignment(0f, -0.20f)
    heroAspect >= 1.85f -> BiasAlignment(0f, -0.32f)
    else -> Alignment.Center
}

@Composable
private fun HeroSourcePill(source: String) {
    // Tüm kaynaklarda logo + dostça isim gösteren ortak çip.
    KitsugiSourceNamePill(source = source)
}

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

    val pagerKey = remember(items) {
        items.joinToString("|") { "${it.source}:${it.type}:${it.malId}:${it.title}" }
    }
    // TMDB + Fanart.tv üzerinden her öğe için en kaliteli dikey poster + yatay arka plan çözümü.
    // Önbellekte varsa ilk karede (0ms) senkron olarak okunur; ekran döndürmede anında hazırdır.
    var resolvedArtworkMap by remember(pagerKey) {
        mutableStateOf(KitsugiHeroArtworkResolver.snapshotFor(items))
    }

    LaunchedEffect(pagerKey, pagerState.currentPage) {
        val currentIdx = pagerState.currentPage.coerceIn(0, items.lastIndex)
        val ordered = buildList {
            add(items[currentIdx])
            items.forEachIndexed { idx, item ->
                if (idx != currentIdx) add(item)
            }
        }
        for (item in ordered) {
            val key = KitsugiHeroArtworkResolver.cacheKey(item)
            if (resolvedArtworkMap[key] == null) {
                launch {
                    val resolved = runCatching {
                        KitsugiHeroArtworkResolver.resolveArtwork(item)
                    }.getOrNull()
                    if (resolved != null) {
                        resolvedArtworkMap = resolvedArtworkMap + (key to resolved)
                    }
                }
            }
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
                heroHeight = (screenHeightDp * 0.68f).coerceIn(380f, 500f).dp,
                contentMaxWidth = 640.dp,
                contentWidthFraction = 0.54f,
                contentHorizontalPadding = 48.dp,
                contentVerticalPadding = 28.dp,
                bottomFadeHeight = 150.dp,
                logoWidthFraction = 0.5f,
            )
        } else if (isLandscape) {
            if (isTablet) {
                HomeHeroLayout(
                    isTablet = true,
                    isLandscape = true,
                    // Yatay tablette 16:9 arka plan görselinin sıkıp kırpılmaması için daha dengeli yükseklik
                    heroHeight = (screenHeightDp * 0.56f).coerceIn(350f, 460f).dp,
                    contentMaxWidth = 560.dp,
                    contentWidthFraction = 0.54f,
                    contentHorizontalPadding = 32.dp,
                    contentVerticalPadding = 18.dp,
                    bottomFadeHeight = 120.dp,
                    logoWidthFraction = 0.52f,
                )
            } else {
                // Yatay modda telefon: yukarı aşağı kısa (kompakt), sağa sola uzun, alttaki blokları açıkta bırakır
                HomeHeroLayout(
                    isTablet = false,
                    isLandscape = true,
                    heroHeight = (screenHeightDp * 0.65f).coerceIn(230f, 295f).dp,
                    contentMaxWidth = 440.dp,
                    contentWidthFraction = 0.56f,
                    contentHorizontalPadding = 24.dp,
                    contentVerticalPadding = 12.dp,
                    bottomFadeHeight = 96.dp,
                    logoWidthFraction = 0.48f,
                )
            }
        } else {
            if (isTablet) {
                // Dikey tablette ~16:9 / 3:2 vitrin alanı: görsel çok daha ferah ve net görünür
                HomeHeroLayout(
                    isTablet = true,
                    isLandscape = false,
                    heroHeight = (screenHeightDp * 0.46f).coerceIn(440f, 560f).dp,
                    contentMaxWidth = 600.dp,
                    contentWidthFraction = 0.88f,
                    contentHorizontalPadding = 28.dp,
                    contentVerticalPadding = 20.dp,
                    bottomFadeHeight = 140.dp,
                    logoWidthFraction = 0.56f,
                )
            } else {
                // Dikey modda telefon: dikey posterin tam ve belirgin sergilenmesi için optimize edilmiş yükseklik
                HomeHeroLayout(
                    isTablet = false,
                    isLandscape = false,
                    heroHeight = (screenHeightDp * 0.55f).coerceIn(430f, 530f).dp,
                    contentMaxWidth = 520.dp,
                    contentWidthFraction = 1f,
                    contentHorizontalPadding = 16.dp,
                    contentVerticalPadding = 18.dp,
                    bottomFadeHeight = 130.dp,
                    logoWidthFraction = 0.62f,
                )
            }
        }
    }

    val currentPage = pagerState.currentPage.coerceIn(items.indices)
    // Offset her animasyon karesinde değişir. Burada offset/visibility okuyup List +
    // HeroPageLayer üretmek tüm vitrini 60 Hz recompose ediyor ve GC baskısı yaratıyordu.
    // Yalnız sayfa değiştiğinde üç sabit indeks oluştur; karelik değerler aşağıdaki
    // graphicsLayer bloklarında draw/layer aşamasında okunur.
    val visiblePageIndices = remember(currentPage, items.size) {
        listOf(currentPage, currentPage - 1, currentPage + 1)
            .filter { it in items.indices }
            .distinct()
    }

    // Vitrin katmanları üst üste biner ve en üstteki katman dokunmayı ilk yakalar;
    // graphicsLayer alfası yalnızca çizimi etkiler, hit-test'i etkilemez. Bu yüzden
    // katmanın kendi öğesini açması yerine, tıklama anında GÖRSEL OLARAK ekranda olan
    // (baskın) öğeyi çözüp açarız — "tıkladığım yerine sıradaki öğe açılıyor" hatası
    // bu sayede ortadan kalkar.
    val openVisibleHeroItem: () -> Unit = {
        if (items.isNotEmpty()) {
            val dominant = round(pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .toInt()
                .coerceIn(items.indices)
            onInfoClick(items[dominant])
        }
    }

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
                                        openVisibleHeroItem()
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

        // Hit-test düzeltmesi: görünür sayfalar (current-1, current, current+1) üst üste
        // çizilir. alpha=0 olan komşu katmanlar görsel olarak görünmese de dokunma
        // olaylarını yiyor ve "yanlış anime" detay sayfası açılmasına sebep oluyordu
        // (One Piece'e tıklayınca Frieren açılması vb.). Sadece o an ÖNDE olan sayfa
        // (offset mutlak değeri en küçük olan) tıklama alsın; diğer katmanlar
        // pointerInput'ı kendi üstlerine kapatmasın.
        val frontPageIndex by remember(pagerState) {
            derivedStateOf {
                var best = visiblePageIndices.firstOrNull() ?: 0
                var bestOffset = Float.MAX_VALUE
                visiblePageIndices.forEach { idx ->
                    val o = abs(heroPageOffset(pagerState, idx))
                    if (o < bestOffset) {
                        bestOffset = o
                        best = idx
                    }
                }
                best
            }
        }

        visiblePageIndices.forEach { page ->
            val rawItem = items[page]
            val artwork = resolvedArtworkMap[KitsugiHeroArtworkResolver.cacheKey(rawItem)]
                ?: KitsugiHeroArtworkResolver.getCached(rawItem)
            val item = remember(rawItem, artwork) {
                KitsugiHeroArtworkResolver.applyCachedOrInstant(
                    rawItem,
                    if (artwork != null) mapOf(KitsugiHeroArtworkResolver.cacheKey(rawItem) to artwork) else emptyMap()
                )
            }
            val displayTitle = item.getDisplayTitle(titleLanguage)
            val isFrontPage = (page == frontPageIndex)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isTv || !isFrontPage) {
                            // Komşu sayfalar (alpha=0) ve TV odak modu: katman dokunmaları
                            // yutmasın diye clickable ekleme. TV'de Enter tuşu zaten üstteki
                            // ana Box'ta pagerState.currentPage ile doğru öğeyi tetikliyor.
                            Modifier
                        } else {
                            Modifier.tvClickable(
                                scaleOnFocus = false
                            ) { openVisibleHeroItem() }
                        }
                    )
            ) {
                // Ekranın dikey/yatay boyutuna göre birincil ve yedek görsel zincirini oluştur.
                val heroBoxAspect = if (heroBoxHeightDp > 0f) heroBoxWidthDp / heroBoxHeightDp else 1f
                val heroImagePlan = remember(
                    item.imageUrl,
                    item.backdropUrl,
                    artwork,
                    heroBoxWidthDp,
                    heroBoxHeightDp,
                    layout.isLandscape,
                    item.type
                ) {
                    val primaryPair = heroImageCandidates(
                        posterUrl = item.imageUrl,
                        backdropUrl = item.backdropUrl,
                        heroWidthDp = heroBoxWidthDp,
                        heroHeightDp = heroBoxHeightDp,
                        isLandscape = layout.isLandscape,
                        mediaType = item.type
                    )
                    if (artwork == null) {
                        primaryPair
                    } else {
                        val preferBackdrop = item.type != MediaType.Manga &&
                            (heroBoxAspect >= 1.10f || (layout.isLandscape && heroBoxAspect >= 0.95f))
                        val orderedExtras = if (preferBackdrop) {
                            artwork.backdropCandidates + artwork.posterCandidates
                        } else {
                            artwork.posterCandidates + artwork.backdropCandidates
                        }
                        (primaryPair + orderedExtras).distinct()
                    }
                }
                var heroImageIndex by remember(heroImagePlan) { mutableIntStateOf(0) }
                val heroImageModel = heroImagePlan.getOrNull(heroImageIndex)

                val chosenIsPoster = remember(heroImageModel, item.imageUrl, item.backdropUrl, artwork) {
                    when {
                        heroImageModel == null -> false
                        artwork != null && heroImageModel in artwork.posterCandidates && heroImageModel !in artwork.backdropCandidates -> true
                        artwork != null && heroImageModel in artwork.backdropCandidates -> false
                        else -> KitsugiHeroArtworkResolver.isLikelyVerticalUrl(
                            url = heroImageModel,
                            posterUrl = item.imageUrl,
                            backdropUrl = item.backdropUrl
                        )
                    }
                }

                // Yüklü görselin gerçek en-boy oranı (width / height).
                var loadedImageAspect by remember(heroImageModel) { mutableStateOf<Float?>(null) }
                val isVerticalArtwork = loadedImageAspect?.let { it < 1.05f } ?: chosenIsPoster
                val isWideShowcaseBox = heroBoxAspect >= 1.18f

                // Geniş/yatay vitrin alanında (tablet yatay, tablet dikey geniş bant, telefon yatay)
                // dikey bir görsel gösteriliyorsa:
                // arkada yumuşak atmosferik katman + ön planda tam boy kesilmemiş dikey görsel gösterilir.
                val useDualLayerContain = isVerticalArtwork && isWideShowcaseBox
                val heroAlignment = remember(isVerticalArtwork, heroBoxAspect) {
                    heroImageAlignment(
                        chosenIsPoster = isVerticalArtwork,
                        heroAspect = heroBoxAspect
                    )
                }

                if (!heroImageModel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = heroPageOffset(pagerState, page)
                                alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                                translationX = -offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
                                translationY = heroScrollTranslationY
                                scaleX = HERO_BACKGROUND_SCALE * heroScrollScale
                                scaleY = HERO_BACKGROUND_SCALE * heroScrollScale
                            }
                    ) {
                        if (useDualLayerContain) {
                            // 1. Atmosferik arka plan dolgusu (Modifier.blur kullanmadan MIUI/HyperOS güvenli)
                            KitsugiNsfwImage(
                                model = heroImageModel,
                                contentDescription = null,
                                isAdult = item.isAdult,
                                blurAdultMedia = blurAdultMedia,
                                contentScale = ContentScale.Crop,
                                alignment = BiasAlignment(0f, -0.25f),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.30f)
                            )
                            // 2. Kesilmemiş, net dikey görsel katmanı
                            KitsugiNsfwImage(
                                model = heroImageModel,
                                contentDescription = displayTitle,
                                isAdult = item.isAdult,
                                blurAdultMedia = blurAdultMedia,
                                contentScale = ContentScale.Fit,
                                alignment = if (layout.isLandscape) BiasAlignment(0.42f, -0.04f) else Alignment.TopCenter,
                                initials = displayTitle,
                                onLoadingFailed = {
                                    if (heroImageIndex < heroImagePlan.lastIndex) {
                                        heroImageIndex += 1
                                    }
                                },
                                onImageAspectResolved = { ratio ->
                                    if (loadedImageAspect == null || abs((loadedImageAspect ?: ratio) - ratio) > 0.05f) {
                                        loadedImageAspect = ratio
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = if (layout.isLandscape) 8.dp else 6.dp,
                                        bottom = if (layout.isLandscape) 18.dp else 32.dp,
                                        end = if (layout.isLandscape) layout.contentHorizontalPadding else 0.dp
                                    )
                            )
                        } else {
                            // Tam ekran vitrin görseli (dikey ekranda en kaliteli dikey poster, yatay/geniş ekranda en kaliteli 16:9 backdrop)
                            KitsugiNsfwImage(
                                model = heroImageModel,
                                contentDescription = displayTitle,
                                isAdult = item.isAdult,
                                blurAdultMedia = blurAdultMedia,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alignment = heroAlignment,
                                onLoadingFailed = {
                                    if (heroImageIndex < heroImagePlan.lastIndex) {
                                        heroImageIndex += 1
                                    }
                                },
                                onImageAspectResolved = { ratio ->
                                    if (loadedImageAspect == null || abs((loadedImageAspect ?: ratio) - ratio) > 0.05f) {
                                        loadedImageAspect = ratio
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = heroPageOffset(pagerState, page)
                                alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                                translationX = -offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
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

        // 1. Yalnızca yatay (landscape) modda sol taraf gölgelendirmesi
        // (Dikey tablette sol tarafı karartmaz).
        if (layout.isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            0.00f to KitsugiColors.Background.copy(alpha = 0.54f),
                            0.42f to KitsugiColors.Background.copy(alpha = 0.22f),
                            1.00f to Color.Transparent
                        )
                    )
            )
        }

        // 2. Berrak dikey scrim: görselin üst %60'lık kısmını tamamen açık ve net bırakır,
        // yalnızca alt kısımda başlık ve meta verilerin okunurluğu için yumuşak geçiş sağlar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to KitsugiColors.Background.copy(alpha = 0.14f),
                        0.12f to Color.Transparent,
                        0.58f to Color.Transparent,
                        0.76f to KitsugiColors.Background.copy(alpha = 0.38f),
                        0.90f to KitsugiColors.Background.copy(alpha = 0.78f),
                        1.00f to KitsugiColors.Background
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
                visiblePageIndices.forEach { page ->
                    val item = items[page]
                    val displayTitle = item.getDisplayTitle(titleLanguage)
                    val alreadyInList = alreadyInList(item)
                    val isFrontPageCol = (page == frontPageIndex)

                    val context = LocalContext.current
                    val copyTitleGesture = Modifier.copyOnDoubleTap(context, displayTitle)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val offset = heroPageOffset(pagerState, page)
                                alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                                translationX = -offset * heroWidthPx * HERO_CONTENT_PARALLAX
                            }
                            .then(
                                if (isTv || !isFrontPageCol) {
                                    // Komşu sayfaların metin/başlık katmanları (alpha=0)
                                    // tıklamaları yutmasın; sadece öndeki sayfa detay açar.
                                    Modifier
                                } else {
                                    Modifier.tvClickable(
                                        shape = RoundedCornerShape(12.dp),
                                        scaleOnFocus = false
                                    ) { openVisibleHeroItem() }
                                }
                            ),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
                        ) {
                            Text(
                                text = stringResource(R.string.inline_featured_20f57e4),
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
                                        text = stringResource(R.string.inline_in_list_85c8d82),
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
                                color = KitsugiColors.TextSecondary,
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
                // Vitrin gösterge çubuğu: sayısal sayaç kaldırıldı; bunun yerine etkin
                // sayfanın çizgisi çerçeve ile kapsanır ve kaçıncı vitrin sayfasında
                // olunduğunu doğrudan gösterir. Çizgiler weight(1f) ile kullanılabilir
                // genişliği paylaşır; telefon/tablet, yatay/dikey her ekran ölçüsünde
                // taşma olmadan ekrana sığar. Geniş ekranlarda ise üst sınır ile
                // çizgilerin aşırı uzaması engellenir.
                // KitsugiColors.TextPrimary @Composable getter olduğundan drawBehind
                // içinde değil, composition kapsamında okunup yakalanır.
                val indicatorFrameColor = KitsugiColors.TextPrimary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = (items.size * 34 + (items.size - 1) * 6).dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                                .height(13.dp)
                                .drawBehind {
                                    // Etkin sayfanın çizgisini çerçeve ile kapsa;
                                    // çerçeve opaklığı sayfa geçişiyle yumuşak değişir.
                                    val frameFraction = heroPageVisibility(pagerState, index)
                                    if (frameFraction > 0.02f) {
                                        val strokeW = 1.5.dp.toPx()
                                        val inset = strokeW / 2f
                                        val w = size.width
                                        val h = size.height
                                        drawRoundRect(
                                            color = indicatorFrameColor.copy(alpha = frameFraction),
                                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                            size = androidx.compose.ui.geometry.Size(w - strokeW, h - strokeW),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW)
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 3.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                                    .graphicsLayer {
                                        val activeFraction = heroPageVisibility(pagerState, index)
                                        // Daha belirgin gösterge: etkin sayfa TAM opak ve kalın,
                                        // pasif sayfalar da net seçilebilir koyulukta.
                                        alpha = 0.30f + (0.70f * activeFraction)
                                        // Sabit ölçü: her karede width değiştirip tüm Row'u yeniden
                                        // ölçmek yerine GPU katmanını yatay ölçekle.
                                        scaleX = 0.40f + (0.60f * activeFraction)
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                    }
                                    .height(7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
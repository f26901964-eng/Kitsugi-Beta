package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import kotlinx.coroutines.launch

/**
 * Manga Okuyucu Ana Ekranı
 *
 * — Pager modu (LTR / RTL / Dikey) → [MangaPagerReader]
 * — Webtoon modu (sürekli dikey) → [MangaWebtoonReader]
 * — Üst ve alt barlar merkeze tıklanınca gizlenir/gösterilir (immersive)
 * — Alt çekme menüsünden okuma modu anında değiştirilebilir
 * — Sayfa ilerlemesi ve bölüm bilgisi alt çubukta gösterilir
 *
 * ## v2.4.341 — sayfa galerisi + hızlı yukarı kaydırma
 *  - Barlar açıkken alt barın üstünde **sayfa galerisi şeridi** görünür; sayfalar oradan
 *    seçilerek (tek dokunuşla) atlanır. Şeritteki görsele dokunmak ilgili sayfaya gider.
 *  - Aynı şeritte "galeri" düğmesi tam ekran **sayfa galerisi**ni açar (ızgara + sayfa
 *    numarası); bu galeride bir sayfaya dokunmak o sayfaya atlar.
 *  - Webtoon modunda aşağı kaydırınca Keşfet sayfasındaki [KitsugiScrollToTopFab] birebir
 *    aynı düğme belirir ve en üstteki sayfaya yumuşakça döner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    chapter: MangaChapter,
    mangaDetails: com.kitsugi.animelist.data.manga.MangaDetails,
    source: MangaSource,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: MangaReaderViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}_${chapter.url}",
        factory = MangaReaderViewModel.Factory(
            context = context,
            source = source,
            mangaDetails = mangaDetails,
            initialChapter = chapter
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val pages by viewModel.pages.collectAsState()

    val currentChapter = uiState.currentChapter
    val readingMode = uiState.readingMode
    val currentPage = uiState.currentPageIndex
    val loader = viewModel.loader

    val scope = rememberCoroutineScope()
    var menuVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var galleryInitialIndex by remember { mutableStateOf(currentPage) }

    val pagerState = rememberPagerState(initialPage = currentPage) { pages.size }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    var hasRestoredProgress by remember(currentChapter.url) { mutableStateOf(false) }

    LaunchedEffect(currentChapter.url) {
        hasRestoredProgress = false
    }

    LaunchedEffect(pages, currentPage) {
        if (pages.isNotEmpty() && !hasRestoredProgress) {
            hasRestoredProgress = true
            val targetPage = currentPage.coerceIn(0, pages.size - 1)
            if (readingMode == ReadingMode.Webtoon) {
                listState.scrollToItem(targetPage)
            } else {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // Pager'daki sayfa değişimini takip et — V2 prefetch tetikle
    LaunchedEffect(pagerState.currentPage) {
        if (hasRestoredProgress && pages.isNotEmpty()) {
            viewModel.onPageChanged(currentChapter.url, pagerState.currentPage, pages.size)
            // R2: Sayfa değiştikçe V2 loader'da 3 ileri prefetch'i başlat
            loader.onPageChanged(pagerState.currentPage)
        }
    }

    /** Herhangi bir sayfaya (moddan bağımsız) animasyonlu geçiş. */
    fun goToPage(index: Int) {
        val target = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scope.launch {
            if (readingMode == ReadingMode.Webtoon) {
                listState.animateScrollToItem(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
        viewModel.onPageChanged(currentChapter.url, target, pages.size)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ─── Okuyucu İçeriği ─────────────────────────────────────────────────
        if (uiState.isLoadingPages) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF7C4DFF),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.inline_loading_page_list_31ccd61),
                        color = Color.White.copy(0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        } else if (uiState.pagesError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.pagesError ?: stringResource(R.string.ui_error_generic),
                        color = Color.White.copy(0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    uiState.pagesErrorCode?.let { code ->
                        Spacer(Modifier.height(10.dp))
                        com.kitsugi.animelist.ui.components.errors.PlatformErrorExplanationBlock(code = code)
                    }
                    Spacer(Modifier.height(24.dp))
                    KitsugiButton(
                        onClick = { viewModel.retryLoadPages() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) {
                        Text(stringResource(R.string.manga_retry), color = Color.White)
                    }
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.inline_no_pages_found_d05c840),
                    color = Color.White.copy(0.5f)
                )
            }
        } else {
            when (readingMode) {
                ReadingMode.Webtoon -> MangaWebtoonReader(
                    pages = pages,
                    listState = listState,
                    loader = loader,
                    colorFilterType = uiState.colorFilterType,
                    fitMode = uiState.fitMode,
                    hasNextChapter = viewModel.hasNextChapter,
                    nextChapterName = viewModel.nextChapterName,
                    onGoToNextChapter = { viewModel.goToNextChapter() },
                    onCenterTap = { menuVisible = !menuVisible },
                    onPageChanged = { index ->
                        if (hasRestoredProgress) {
                            viewModel.onPageChanged(currentChapter.url, index, pages.size)
                        }
                    },
                    onNextPage = {
                        val current = listState.firstVisibleItemIndex
                        if (current < pages.size - 1) {
                            scope.launch { listState.animateScrollToItem(current + 1) }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage = {
                        val current = listState.firstVisibleItemIndex
                        if (current > 0) {
                            scope.launch { listState.animateScrollToItem(current - 1) }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )

                else -> MangaPagerReader(
                    pages = pages,
                    pagerState = pagerState,
                    loader = loader,
                    readingMode = readingMode,
                    colorFilterType = uiState.colorFilterType,
                    fitMode = uiState.fitMode,
                    onCenterTap = { menuVisible = !menuVisible },
                    onNextPage = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )
            }
        }

        // ─── Özel Parlaklık Katmanı ───────────────────────────────────────────
        if (uiState.customBrightness < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1.0f - uiState.customBrightness))
            )
        }

        // ─── Hızlı Yukarı Kaydırma (Keşfet ile birebir aynı bileşen) ──────────
        // Yalnızca dikey (webtoon) okuma modunda anlamlı: kullanıcı yüzlerce sayfalık
        // bir bölümün ortasından tek dokunuşla başa dönebilir.
        if (readingMode == ReadingMode.Webtoon && pages.isNotEmpty()) {
            val showScrollToTop by remember {
                androidx.compose.runtime.derivedStateOf {
                    listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 400
                }
            }
            KitsugiScrollToTopFab(
                visible = showScrollToTop,
                isLandscape = LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                bottomBarScrollState = null,
                fallbackBottomBarVisible = menuVisible,
                // Alt barda sayfa şeridi + gezinme satırı + araç satırı var; düğme
                // bunların üstünde durmalı (varsayılan 88dp yetersiz kalıyordu).
                bottomBarHeight = 168.dp,
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                    viewModel.onPageChanged(currentChapter.url, 0, pages.size)
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // ─── Üst Bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            mangaDetails.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            currentChapter.name,
                            color = Color.White.copy(0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (pages.isNotEmpty()) {
                        // Sayfa galerisi kısayolu — üst bardan da erişilebilir.
                        IconButton(onClick = { showGallery = true }) {
                            Icon(
                                Icons.Rounded.GridView,
                                contentDescription = stringResource(R.string.manga_reader_open_gallery),
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.okuyucu_ayarlari),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // ─── Alt Bar (Aniyomi Parity) + Sayfa Galerisi Şeridi ────────────────
        AnimatedVisibility(
            visible = menuVisible && pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val barBackgroundColor = Color(0xFF161622).copy(alpha = 0.95f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBackgroundColor)
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 0. Sayfa Galerisi Şeridi — dokununca o sayfaya atlar.
                MangaPageStrip(
                    pages = pages,
                    currentIndex = currentPage,
                    loader = loader,
                    accentColor = Color(0xFF7C4DFF),
                    onPageClick = { index ->
                        galleryInitialIndex = index
                        showGallery = true
                    },
                    onOpenGallery = { showGallery = true }
                )

                // 1. ChapterNavigator (Aniyomi Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Chapter Button
                    IconButton(
                        onClick = { viewModel.goToPreviousChapter() },
                        enabled = viewModel.hasPreviousChapter
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.onceki_bolum),
                            tint = if (viewModel.hasPreviousChapter) Color.White else Color.White.copy(0.3f)
                        )
                    }

                    // Slider Container
                    if (pages.size > 1) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF26263B))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentPage + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            var sliderValue by remember(currentPage) {
                                mutableStateOf((currentPage + 1).toFloat())
                            }
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderValue = it
                                    val targetPage = it.toInt() - 1
                                    scope.launch {
                                        if (readingMode == ReadingMode.Webtoon) {
                                            listState.scrollToItem(targetPage.coerceIn(0, pages.size - 1))
                                        } else {
                                            pagerState.scrollToPage(targetPage.coerceIn(0, pages.size - 1))
                                        }
                                    }
                                },
                                valueRange = 1f..pages.size.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFF7C4DFF),
                                    inactiveTrackColor = Color(0xFF3D3D5C),
                                    thumbColor = Color(0xFF7C4DFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                text = "${pages.size}",
                                color = Color.White.copy(0.6f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Next Chapter Button
                    IconButton(
                        onClick = { viewModel.goToNextChapter() },
                        enabled = viewModel.hasNextChapter
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.sonraki_bolum),
                            tint = if (viewModel.hasNextChapter) Color.White else Color.White.copy(0.3f)
                        )
                    }
                }

                // 2. BottomReaderBar (Aniyomi Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Reading Mode cycle
                    val readingModeIcon = when (readingMode) {
                        ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
                        ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
                        ReadingMode.Vertical -> Icons.Default.ViewStream
                        ReadingMode.Webtoon -> Icons.Default.ViewStream
                    }
                    IconButton(onClick = {
                        val nextMode = when (readingMode) {
                            ReadingMode.RightToLeft -> ReadingMode.LeftToRight
                            ReadingMode.LeftToRight -> ReadingMode.Vertical
                            ReadingMode.Vertical -> ReadingMode.Webtoon
                            ReadingMode.Webtoon -> ReadingMode.RightToLeft
                        }
                        viewModel.setReadingMode(nextMode)
                    }) {
                        Icon(
                            imageVector = readingModeIcon,
                            contentDescription = stringResource(R.string.okuma_yonu),
                            tint = Color.White
                        )
                    }

                    // Quick Orientation cycle
                    IconButton(onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            val currentOrientation = activity.requestedOrientation
                            if (currentOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = stringResource(R.string.ekran_dondurme),
                            tint = Color.White
                        )
                    }

                    // Quick Fit Mode cycle
                    IconButton(onClick = {
                        val nextFit = when (uiState.fitMode) {
                            MangaFitMode.FitScreen -> MangaFitMode.FitWidth
                            MangaFitMode.FitWidth -> MangaFitMode.FitHeight
                            MangaFitMode.FitHeight -> MangaFitMode.FitScreen
                        }
                        viewModel.setFitMode(nextFit)
                    }) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = stringResource(R.string.sigdirma_modu),
                            tint = Color.White
                        )
                    }

                    // Settings Dialog Trigger
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.ayarlar),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // ─── Tam Ekran Sayfa Galerisi ────────────────────────────────────────────
    if (showGallery && pages.isNotEmpty()) {
        MangaPageGallerySheet(
            pages = pages,
            currentIndex = currentPage,
            initialFocusIndex = galleryInitialIndex,
            accentColor = Color(0xFF7C4DFF),
            loader = loader,
            onSelect = { index ->
                showGallery = false
                goToPage(index)
            },
            onDismiss = { showGallery = false }
        )
    }

    // ─── Okuma Modu Ayarlar Paneli ────────────────────────────────────────────
    if (showSettings) {
        KitsugiSheetOrDialog(
            onDismiss = { showSettings = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.inline_reader_settings_82b7841),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Okuma Yönü
                Text(
                    stringResource(R.string.manga_reading_direction),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        ReadingMode.RightToLeft to "RTL (Manga)",
                        ReadingMode.LeftToRight to "LTR",
                        ReadingMode.Vertical to "Dikey",
                        ReadingMode.Webtoon to "Webtoon"
                    )
                    modes.forEach { (mode, label) ->
                        FilterChip(
                            selected = readingMode == mode,
                            onClick = { viewModel.setReadingMode(mode) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor = Color(0xFF7C4DFF),
                                containerColor = Color(0xFF2A2A40),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Görsel Sığdırma Modu
                Text(
                    stringResource(R.string.inline_image_fit_mode_3eed46c),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fits = listOf(
                        MangaFitMode.FitScreen to stringResource(R.string.manga_fit_screen),
                        MangaFitMode.FitWidth to "Genişlik",
                        MangaFitMode.FitHeight to "Yükseklik"
                    )
                    fits.forEach { (fit, label) ->
                        FilterChip(
                            selected = uiState.fitMode == fit,
                            onClick = { viewModel.setFitMode(fit) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor = Color(0xFF7C4DFF),
                                containerColor = Color(0xFF2A2A40),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Renk Filtresi
                Text(
                    "Renk Filtresi",
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        ColorFilterType.Normal to "Normal",
                        ColorFilterType.Grayscale to "Siyah-Beyaz",
                        ColorFilterType.Sepia to "Sepya",
                        ColorFilterType.Invert to "Ters Negatif"
                    )
                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = uiState.colorFilterType == filter,
                            onClick = { viewModel.setColorFilter(filter) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor = Color(0xFF7C4DFF),
                                containerColor = Color(0xFF2A2A40),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Özel Parlaklık Sürgüsü
                Text(
                    stringResource(R.string.inline_reader_brightness_e5e9f7a),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessLow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = uiState.customBrightness,
                        onValueChange = { viewModel.setCustomBrightness(it) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF7C4DFF),
                            inactiveTrackColor = Color(0xFF2A2A40),
                            thumbColor = Color(0xFF7C4DFF)
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { viewModel.setCustomBrightness(1.0f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.slider_reset_to_default),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sayfa şeridi (alt barın üstünde)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Alt barın üstündeki yatay sayfa şeridi.
 *
 * Küçük resimler `MangaPageLoaderV2`'nin disk önbelleğini kullandığı için ek indirme
 * yapmaz; henüz hazır olmayan sayfalar yer tutucu kutu olarak çizilir. Geçerli sayfa
 * vurgulanır ve şerit açıldığında o sayfaya kaydırılır.
 */
@Composable
private fun MangaPageStrip(
    pages: List<com.kitsugi.animelist.data.manga.MangaPage>,
    currentIndex: Int,
    loader: com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2,
    accentColor: Color,
    onPageClick: (Int) -> Unit,
    onOpenGallery: () -> Unit
) {
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    LaunchedEffect(currentIndex) {
        if (pages.isNotEmpty()) {
            stripState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = stripState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pages, key = { _, page -> page.index }) { index, page ->
                val selected = index == currentIndex
                val url = page.imageUrl ?: page.url
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) accentColor.copy(alpha = 0.25f) else Color(0xFF26263B))
                        .androidxClickable { onPageClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (page.status == MangaPageStatus.Ready) {
                        // Önbellek yolu sayfa başına bir kez hesaplanır (getImageFile LRU için
                        // dosya zaman damgasına dokunur; her karede çağrılmamalı).
                        val cachedModel = remember(page.index, page.status, url) {
                            runCatching {
                                val file = loader.cache.getImageFile(url)
                                if (file.exists()) file else url
                            }.getOrDefault(url)
                        }
                        AsyncImage(
                            model = cachedModel,
                            contentDescription = stringResource(R.string.manga_reader_page_number, index + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = Color.White.copy(0.45f),
                            fontSize = 10.sp
                        )
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(accentColor.copy(alpha = 0.18f))
                        )
                    }
                    Text(
                        text = "${index + 1}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onOpenGallery, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Rounded.GridView,
                contentDescription = stringResource(R.string.manga_reader_open_gallery),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tam ekran sayfa galerisi
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bölümdeki tüm sayfaların ızgara galerisi.
 *
 * Amaç: 100+ sayfalık bir bölümde slider'ı milimetrik sürüklemek yerine hedef sayfayı
 * görsel olarak bulup tek dokunuşla atlamak. Seçilen sayfa çağırana bildirilir.
 */
@Composable
private fun MangaPageGallerySheet(
    pages: List<com.kitsugi.animelist.data.manga.MangaPage>,
    currentIndex: Int,
    /** Galeri açıldığında odaklanacak sayfa (şeritten gelindiğinde o sayfa). */
    initialFocusIndex: Int,
    accentColor: Color,
    loader: com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.manga_reader_gallery_title),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.manga_reader_gallery_hint),
                            color = Color.White.copy(0.6f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${currentIndex + 1}/${pages.size}",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.kapat), tint = Color.White)
                    }
                }

                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
                    initialFirstVisibleItemIndex = (initialFocusIndex - 2).coerceAtLeast(0)
                )
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    state = gridState,
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pages.size,
                        key = { index -> pages[index].index }
                    ) { index ->
                        val page = pages[index]
                        // Galeri küçük resimleri de aynı önbellekten beslenir.
                        LaunchedEffect(page.index) { loader.loadPage(page) }
                        val selected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.22f) else Color(0xFF1B1B26))
                                .androidxClickable { onSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (page.status == MangaPageStatus.Ready) {
                                // Galeri küçük resimleri de okuyucunun disk önbelleğini kullanır:
                                // telephoto yüklenen dosyayı (varsa) doğrudan gösterir, aksi hâlde URL.
                                val source = page.imageUrl ?: page.url
                                val cachedModel = remember(page.index, page.status, source) {
                                    runCatching {
                                        val file = loader.cache.getImageFile(source)
                                        if (file.exists()) file else source
                                    }.getOrDefault(source)
                                }
                                AsyncImage(
                                    model = cachedModel,
                                    contentDescription = stringResource(
                                        R.string.manga_reader_page_number,
                                        index + 1
                                    ),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = Color.White.copy(0.25f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(accentColor.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ripple'sız dokunma.
 *
 * Galeri küçük resimleri ve sayfa şeridi sürekli yeniden çizildiği için varsayılan
 * ripple göstergesi burada görsel gürültü üretiyordu; dokunma geri bildirimi seçili
 * sayfanın vurgusuyla veriliyor.
 */
@Composable
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )

private fun ReadingMode.label() = when (this) {
    ReadingMode.RightToLeft -> "Sağdan Sola"
    ReadingMode.LeftToRight -> "Soldan Sağa"
    ReadingMode.Vertical -> "Dikey"
    ReadingMode.Webtoon -> "Webtoon"
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

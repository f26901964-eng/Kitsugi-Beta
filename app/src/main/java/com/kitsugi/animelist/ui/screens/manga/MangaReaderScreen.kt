package com.kitsugi.animelist.ui.screens.manga
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.Slider
import androidx.compose.material3.SliderDefaults
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedStateCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaTapAction
import com.kitsugi.animelist.ui.screens.manga.components.MangaPagerReader
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Manga Okuyucu Ana Ekranı
 *
 * — Pager modu (LTR / RTL / Dikey) → MangaPagerReader
 * — Webtoon modu (sürekli dikey) → MangaWebtoonReader
 * — Üst ve alt barlar merkeze tıklanınca gizlenir/gösterilir (immersive)
 * — Alt çekme menüsünden okuma modu anında değiştirilebilir
 * — Sayfa ilerlemesi ve bölüm bilgisi alt çubukta gösterilir
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
    // Tür algılama → doğru varsayılan mod (manhwa = webtoon, manhua = LTR).
    // Kotatsu-Redo'nun "her başlık için en iyi modu otomatik seç" davranışı.
    val kind = remember(source.name, mangaDetails) {
        com.kitsugi.animelist.data.manga.MangaKindDetector.detect(
            sourceName = source.name,
            title = mangaDetails.title,
            genres = mangaDetails.genre
        )
    }
    val viewModel: MangaReaderViewModel = viewModel(
        key = "${source.name}_${mangaDetails.url}_${chapter.url}",
        factory = MangaReaderViewModel.Factory(
            context = context,
            source = source,
            mangaDetails = mangaDetails,
            initialChapter = chapter,
            suggestedMode = if (kind == com.kitsugi.animelist.data.manga.MangaKindDetector.Kind.Unknown) null
                            else com.kitsugi.animelist.data.manga.MangaKindDetector.recommendedMode(kind)
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val pages by viewModel.pages.collectAsState()

    val currentChapter = uiState.currentChapter
    val readingMode = uiState.readingMode
    val currentPage = uiState.currentPageIndex
    val loader = viewModel.loader

    val scope      = rememberCoroutineScope()
    var menuVisible     by remember { mutableStateOf(true) }
    var showSettings    by remember { mutableStateOf(false) }
    var showJumpToPage  by remember { mutableStateOf(false) }
    var showPageTools   by remember { mutableStateOf(false) }

    // Kenar/orta dokunma davranışları — okuma yönüne göre otomatik çözülür.
    val tapActions = viewModel.resolveTapActions()

    // Pencere bayrakları (ekranı açık tut / tam ekran) için Activity referansı.
    val activity = remember(context) { context.findActivity() }

    // ─── Geçerli sayfa (başlık rozeti, sayfa atlama, kaydetme için) ───────────
    // Sayfa sayacı: webtoon modunda LazyColumn, diğerlerinde pager konumu.
    var visiblePage by remember { mutableIntStateOf(currentPage) }

    // ─── Ekranı açık tut + tam ekran (immersive) yan etkileri ────────────────
    MangaReaderSystemEffects(
        keepScreenOn = uiState.keepScreenOn,
        fullscreen = uiState.fullscreenEnabled,
        activity = activity
    )

    val pagerState    = rememberPagerState(initialPage = currentPage) { pages.size }
    val listState     = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    // ─── Ses tuşları ile sayfa çevirme ───────────────────────────────────────
    // Compose odak yakalama yerine Activity seviyesinde tuş dinleyicisi kurulur:
    // böylece zoom/pager jestleriyle çakışmaz ve okuyucudan çıkınca temizlenir.
    val volumeTurnPage by rememberUpdatedState<(Boolean) -> Unit> { isVolumeUp ->
        val forward = if (uiState.volumeKeysInvert) !isVolumeUp else isVolumeUp
        val isWebtoon = readingMode == ReadingMode.Webtoon
        if (forward) {
            if (isWebtoon) {
                val current = listState.firstVisibleItemIndex
                if (current < pages.size - 1) scope.launch { listState.animateScrollToItem(current + 1) }
                else viewModel.goToNextChapter()
            } else {
                if (pagerState.currentPage < pages.size - 1) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                else viewModel.goToNextChapter()
            }
        } else {
            if (isWebtoon) {
                val current = listState.firstVisibleItemIndex
                if (current > 0) scope.launch { listState.animateScrollToItem(current - 1) }
                else viewModel.goToPreviousChapter()
            } else {
                if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                else viewModel.goToPreviousChapter()
            }
        }
    }

    // Activity seviyesinde ses tuşu dinleyicisi (yalnızca ayar açıkken devrede).
    // Kazara etki bırakmamak için orijinal Window.Callback saklanır ve çıkışta geri konur.
    DisposableEffect(uiState.volumeKeysEnabled, activity) {
        val window = activity?.window
        val original = window?.callback
        if (window == null || original == null || !uiState.volumeKeysEnabled) {
            onDispose { }
        } else {
            window.callback = object : android.view.Window.Callback by original {
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    val isVolume = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                        event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                    if (isVolume && event.action == android.view.KeyEvent.ACTION_DOWN) {
                        volumeTurnPage(event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
                        return true
                    }
                    return original.dispatchKeyEvent(event)
                }
            }
            onDispose { window.callback = original }
        }
    }

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

    // Geçerli sayfayı sayaç/atlama için izle (pager + webtoon).
    LaunchedEffect(pagerState.currentPage) { visiblePage = pagerState.currentPage }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (readingMode == ReadingMode.Webtoon) visiblePage = listState.firstVisibleItemIndex
    }

    // Pager'daki sayfa değişimini takip et — V2 prefetch tetikle
    LaunchedEffect(pagerState.currentPage) {
        if (hasRestoredProgress && pages.isNotEmpty()) {
            viewModel.onPageChanged(currentChapter.url, pagerState.currentPage, pages.size)
            // R2: Sayfa değiştikçe V2 loader'da 3 ileri prefetch'i başlat
            loader.onPageChanged(pagerState.currentPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ─── Okuyucu İçeriği ─────────────────────────────────────────────────
        if (uiState.isLoadingPages) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF7C4DFF),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.inline_loading_page_list_31ccd61), color = Color.White.copy(0.6f), fontSize = 13.sp)
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        Text("Tekrar Dene", color = Color.White)
                    }
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.inline_no_pages_found_d05c840), color = Color.White.copy(0.5f))
            }
        } else {
            when (readingMode) {
                ReadingMode.Webtoon -> MangaWebtoonReader(
                    pages        = pages,
                    listState    = listState,
                    loader       = loader,
                    colorFilterType = uiState.colorFilterType,
                    fitMode      = uiState.fitMode,
                    hasNextChapter = viewModel.hasNextChapter,
                    nextChapterName = viewModel.nextChapterName,
                    onGoToNextChapter = { viewModel.goToNextChapter() },
                    onCenterTap  = { menuVisible = !menuVisible },
                    pageGapDp     = uiState.webtoonGapDp,
                    cropBorders   = uiState.cropBorders,
                    sidePaddingDp = uiState.sidePaddingDp,
                    showPageNumber = uiState.showPageNumber,
                    tapActions    = tapActions,
                    brightnessGesture = uiState.brightnessGestureEnabled,
                    onBrightnessDrag = { delta -> viewModel.adjustBrightness(delta) },
                    onLongPress   = { showPageTools = true },
                    onPageChanged = { idx ->
                        if (hasRestoredProgress) {
                            viewModel.onPageChanged(currentChapter.url, idx, pages.size)
                            // R2: Webtoon scroll'da V2 prefetch tetikle
                            loader.onPageChanged(idx)
                        }
                    },
                    onNextPage   = {
                        val current = listState.firstVisibleItemIndex
                        if (current < pages.size - 1) {
                            scope.launch {
                                listState.animateScrollToItem(current + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage   = {
                        val current = listState.firstVisibleItemIndex
                        if (current > 0) {
                            scope.launch {
                                listState.animateScrollToItem(current - 1)
                            }
                        } else {
                            viewModel.goToPreviousChapter()
                        }
                    }
                )
                else -> MangaPagerReader(
                    pages        = pages,
                    pagerState   = pagerState,
                    loader       = loader,
                    readingMode  = readingMode,
                    colorFilterType = uiState.colorFilterType,
                    fitMode      = uiState.fitMode,
                    tapActions   = tapActions,
                    brightnessGesture = uiState.brightnessGestureEnabled,
                    onBrightnessDrag = { delta -> viewModel.adjustBrightness(delta) },
                    onLongPress  = { showPageTools = true },
                    cropBorders  = uiState.cropBorders,
                    splitWidePages = uiState.splitWidePages,
                    rotateWidePages = uiState.rotateWidePages,
                    sidePaddingDp = uiState.sidePaddingDp,
                    showPageNumber = uiState.showPageNumber,
                    onCenterTap  = { menuVisible = !menuVisible },
                    onNextPage   = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            viewModel.goToNextChapter()
                        }
                    },
                    onPrevPage   = {
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

        // ─── E-ink: sayfa değişiminde kısa beyaz flaş (hayalet görüntüyü siler) ─
        // Kısa beyaz katman 90 ms görünür; e-mürekkep ekranlarda sayfa artığını
        // temizler (Mihon "Flash on page change" paritesi).
        if (uiState.einkFlash) {
            var flashOn by remember { mutableStateOf(false) }
            LaunchedEffect(visiblePage) {
                flashOn = true
                kotlinx.coroutines.delay(90)
                flashOn = false
            }
            if (flashOn) {
                Box(Modifier.fillMaxSize().background(Color.White))
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

        // ─── Üst Bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible,
            enter   = fadeIn() + slideInVertically { -it },
            exit    = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.85f), Color.Transparent)
                        )
                    )
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
                            color     = Color.White,
                            fontSize  = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines  = 1,
                            overflow  = TextOverflow.Ellipsis
                        )
                        Text(
                            currentChapter.name,
                            color    = Color.White.copy(0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Bölüm içinde sayfa durumu (Mihon tarzı kompakt rozet).
                    if (pages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { showJumpToPage = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${visiblePage + 1}/${pages.size}",
                                color = Color.White.copy(0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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

        // ─── Alt Bar (Aniyomi Parity) ──────────────────────────────────────────
        AnimatedVisibility(
            visible = menuVisible && pages.isNotEmpty(),
            enter   = fadeIn() + slideInVertically { it },
            exit    = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val barBackgroundColor = Color(0xFF161622).copy(alpha = 0.95f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBackgroundColor)
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. ChapterNavigator (Aniyomi Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            // Sayfa numarasına dokun → "Sayfaya git" paneli (Mihon paritesi).
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showJumpToPage = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${visiblePage + 1}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            var sliderValue by remember(currentPage) { mutableStateOf((currentPage + 1).toFloat()) }
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Reading Mode cycle
                    val readingModeIcon = when (readingMode) {
                        ReadingMode.RightToLeft -> Icons.AutoMirrored.Filled.MenuBook
                        ReadingMode.LeftToRight -> Icons.Default.SwapHoriz
                        ReadingMode.Vertical    -> Icons.Default.ViewStream
                        ReadingMode.Webtoon     -> Icons.Default.ViewStream
                    }
                    IconButton(onClick = {
                        val nextMode = when (readingMode) {
                            ReadingMode.RightToLeft -> ReadingMode.LeftToRight
                            ReadingMode.LeftToRight -> ReadingMode.Vertical
                            ReadingMode.Vertical    -> ReadingMode.Webtoon
                            ReadingMode.Webtoon     -> ReadingMode.RightToLeft
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
                            MangaFitMode.FitWidth  -> MangaFitMode.FitHeight
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

                    // Sayfa araçları: kaydet / paylaş / sayfaya git
                    IconButton(onClick = { showPageTools = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.reader_tools),
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

    // ─── Sayfaya Git ─────────────────────────────────────────────────────────
    if (showJumpToPage && pages.isNotEmpty()) {
        MangaJumpToPageDialog(
            totalPages = pages.size,
            currentPage = visiblePage,
            onDismiss = { showJumpToPage = false },
            onJump = { target ->
                showJumpToPage = false
                scope.launch {
                    if (readingMode == ReadingMode.Webtoon) {
                        listState.scrollToItem(target.coerceIn(0, pages.size - 1))
                    } else {
                        pagerState.scrollToPage(target.coerceIn(0, pages.size - 1))
                    }
                }
            }
        )
    }

    // ─── Sayfa Araçları (kaydet / paylaş) ────────────────────────────────────
    if (showPageTools && pages.isNotEmpty()) {
        MangaPageToolsSheet(
            onDismiss = { showPageTools = false },
            onSave = {
                showPageTools = false
                val page = pages.getOrNull(visiblePage)
                if (page != null) {
                    val ok = viewModel.savePageToGallery(page, currentChapter.name)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.reader_page_saved else R.string.reader_page_save_failed),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onShare = { shareCurrentPage(context, pages.getOrNull(visiblePage), currentChapter.name) },
            onJump = {
                showPageTools = false
                showJumpToPage = true
            },
            currentPageLabel = "${visiblePage + 1}/${pages.size}"
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
                    color     = Color.White,
                    fontSize  = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(bottom = 16.dp)
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
                        ReadingMode.Vertical    to "Dikey",
                        ReadingMode.Webtoon     to "Webtoon"
                    )
                    modes.forEach { (mode, label) ->
                        FilterChip(
                            selected = readingMode == mode,
                            onClick  = { viewModel.setReadingMode(mode) },
                            label    = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor       = Color(0xFF7C4DFF),
                                containerColor           = Color(0xFF2A2A40),
                                labelColor               = Color.White
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
                        MangaFitMode.FitWidth  to "Genişlik",
                        MangaFitMode.FitHeight to "Yükseklik"
                    )
                    fits.forEach { (fit, label) ->
                        FilterChip(
                            selected = uiState.fitMode == fit,
                            onClick  = { viewModel.setFitMode(fit) },
                            label    = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor       = Color(0xFF7C4DFF),
                                containerColor           = Color(0xFF2A2A40),
                                labelColor               = Color.White
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
                        ColorFilterType.Normal    to "Normal",
                        ColorFilterType.Grayscale to "Siyah-Beyaz",
                        ColorFilterType.Sepia     to "Sepya",
                        ColorFilterType.Invert    to "Ters Negatif"
                    )
                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = uiState.colorFilterType == filter,
                            onClick  = { viewModel.setColorFilter(filter) },
                            label    = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = Color(0xFF7C4DFF).copy(0.2f),
                                selectedLabelColor       = Color(0xFF7C4DFF),
                                containerColor           = Color(0xFF2A2A40),
                                labelColor               = Color.White
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
                Spacer(Modifier.height(16.dp))

                // ─── Dokunma Alanları (Mihon "tap zones" paritesi) ───────────
                Text(
                    stringResource(R.string.reader_tap_zones_title),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                listOf(
                    stringResource(R.string.reader_tap_zone_left) to uiState.tapLeftAction,
                    stringResource(R.string.reader_tap_zone_center) to uiState.tapCenterAction,
                    stringResource(R.string.reader_tap_zone_right) to uiState.tapRightAction
                ).forEachIndexed { index, (zoneLabel, action) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            zoneLabel,
                            color = Color.White.copy(0.75f),
                            fontSize = 12.sp,
                            modifier = Modifier.width(84.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                MangaTapAction.Previous to stringResource(R.string.reader_tap_action_prev),
                                MangaTapAction.Next to stringResource(R.string.reader_tap_action_next),
                                MangaTapAction.Menu to stringResource(R.string.reader_tap_action_menu),
                                MangaTapAction.None to stringResource(R.string.reader_tap_action_none)
                            ).forEach { (candidate, label) ->
                                FilterChip(
                                    selected = action == candidate,
                                    onClick = {
                                        val next = when (index) {
                                            0 -> Triple(candidate, uiState.tapCenterAction, uiState.tapRightAction)
                                            1 -> Triple(uiState.tapLeftAction, candidate, uiState.tapRightAction)
                                            else -> Triple(uiState.tapLeftAction, uiState.tapCenterAction, candidate)
                                        }
                                        viewModel.setTapActions(next.first, next.second, next.third)
                                    },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF7C4DFF).copy(0.25f),
                                        selectedLabelColor = Color(0xFFB39DFF),
                                        containerColor = Color(0xFF2A2A40),
                                        labelColor = Color.White.copy(0.8f)
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ─── Açma / kapama anahtarları ──────────────────────────────
                MangaToggleRow(
                    title = stringResource(R.string.reader_brightness_swipe),
                    checked = uiState.brightnessGestureEnabled,
                    onCheckedChange = { viewModel.setBrightnessGestureEnabled(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_volume_keys),
                    checked = uiState.volumeKeysEnabled,
                    onCheckedChange = { viewModel.setVolumeKeysEnabled(it) }
                )
                if (uiState.volumeKeysEnabled) {
                    MangaToggleRow(
                        title = stringResource(R.string.reader_volume_keys_invert),
                        checked = uiState.volumeKeysInvert,
                        onCheckedChange = { viewModel.setVolumeKeysInvert(it) }
                    )
                }
                MangaToggleRow(
                    title = stringResource(R.string.reader_keep_screen_on),
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_fullscreen),
                    checked = uiState.fullscreenEnabled,
                    onCheckedChange = { viewModel.setFullscreenEnabled(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_next_chapter_on_last_page),
                    checked = uiState.autoNextChapter,
                    onCheckedChange = { viewModel.setAutoNextChapter(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_skip_read_chapters),
                    checked = uiState.skipReadChapters,
                    onCheckedChange = { viewModel.setSkipReadChapters(it) }
                )

                // ─── Görüntü işleme (Mihon "Reading" paritesi) ──────────────
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.reader_image_processing),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_crop_borders),
                    checked = uiState.cropBorders,
                    onCheckedChange = { viewModel.setCropBorders(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_split_wide_pages),
                    checked = uiState.splitWidePages,
                    onCheckedChange = { viewModel.setSplitWidePages(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_rotate_wide_pages),
                    checked = uiState.rotateWidePages,
                    onCheckedChange = { viewModel.setRotateWidePages(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_show_page_number),
                    checked = uiState.showPageNumber,
                    onCheckedChange = { viewModel.setShowPageNumber(it) }
                )
                MangaToggleRow(
                    title = stringResource(R.string.reader_eink_flash),
                    checked = uiState.einkFlash,
                    onCheckedChange = { viewModel.setEinkFlash(it) }
                )

                // ─── Yan boşluk (dp) ───────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                Text(
                    "${stringResource(R.string.reader_side_padding)}: ${uiState.sidePaddingDp} dp",
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Slider(
                    value = uiState.sidePaddingDp.toFloat(),
                    onValueChange = { viewModel.setSidePadding(it.toInt()) },
                    valueRange = 0f..60f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF7C4DFF),
                        inactiveTrackColor = Color(0xFF2A2A40),
                        thumbColor = Color(0xFF7C4DFF)
                    )
                )

                // ─── Webtoon sayfa aralığı ──────────────────────────────────
                if (readingMode == ReadingMode.Webtoon) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${stringResource(R.string.reader_webtoon_gap)}: ${uiState.webtoonGapDp} dp",
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Slider(
                        value = uiState.webtoonGapDp.toFloat(),
                        onValueChange = { viewModel.setWebtoonGap(it.toInt()) },
                        valueRange = 0f..48f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF7C4DFF),
                            inactiveTrackColor = Color(0xFF2A2A40),
                            thumbColor = Color(0xFF7C4DFF)
                        )
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Okuyucu ayar panelindeki sade anahtar satırı. */
@Composable
private fun MangaToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(0.85f),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF7C4DFF),
                uncheckedThumbColor = Color.White.copy(0.7f),
                uncheckedTrackColor = Color(0xFF2A2A40)
            )
        )
    }
}

private fun ReadingMode.label() = when (this) {
    ReadingMode.RightToLeft -> "Sağdan Sola"
    ReadingMode.LeftToRight -> "Soldan Sağa"
    ReadingMode.Vertical    -> "Dikey"
    ReadingMode.Webtoon     -> "Webtoon"
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Okuyucu sistem yan etkileri:
 *  - [keepScreenOn] → okurken ekran kararmasın (Mihon "keep screen on" paritesi)
 *  - [fullscreen]   → sistem çubukları gizlenir, kaydırınca geçici olarak görünür
 *
 * Her ikisi de DisposableEffect ile temizlenir; okuyucudan çıkınca pencere
 * bayrakları ve sistem çubukları eski hâline döner.
 */
@Composable
private fun MangaReaderSystemEffects(
    keepScreenOn: Boolean,
    fullscreen: Boolean,
    activity: android.app.Activity?
) {
    DisposableEffect(keepScreenOn, activity) {
        val window = activity?.window
        if (keepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(fullscreen, activity) {
        val window = activity?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        if (fullscreen) {
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * "Sayfaya git" paneli — büyük sayfa numarası girişi + hızlı git düğmesi.
 * Mihon'da sayfa göstergesine dokununca açılan akışın sade karşılığı.
 */
@Composable
private fun MangaJumpToPageDialog(
    totalPages: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var input by remember { mutableStateOf((currentPage + 1).toString()) }
    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_page_jump),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    // Yalnızca rakam; toplam sayfa sınırına kırpılır.
                    input = value.filter { it.isDigit() }.take(6)
                },
                placeholder = { Text(stringResource(R.string.reader_page_jump_hint), color = Color.White.copy(0.5f)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7C4DFF),
                    unfocusedBorderColor = Color.White.copy(0.25f),
                    cursorColor = Color(0xFF7C4DFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "1 – $totalPages",
                color = Color.White.copy(0.5f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
            KitsugiButton(
                onClick = {
                    val target = input.toIntOrNull()?.minus(1)
                    if (target != null) onJump(target.coerceIn(0, totalPages - 1))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text(stringResource(R.string.reader_jump), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Okuyucudan çıkarken kullanılan sayfa araçları paneli. */
@Composable
private fun MangaPageToolsSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onJump: () -> Unit,
    currentPageLabel: String
) {
    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_tools),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currentPageLabel,
                color = Color.White.copy(0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            listOf(
                Triple(Icons.Rounded.FindInPage, R.string.reader_page_jump, onJump),
                Triple(Icons.Rounded.SaveAlt, R.string.reader_save_page_jpg, onSave),
                Triple(Icons.Rounded.Share, R.string.reader_share_page, onShare)
            ).forEach { (icon, labelRes, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { action() }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(labelRes), color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Geçerli sayfayı paylaşır.
 *
 * — Sayfa yerel önbellekteyse FileProvider ile dosya olarak paylaşılır (en iyi yol).
 * — Değilse sayfanın doğrudan bağlantısı metin olarak paylaşılır; böylece
 *   kullanıcı hiçbir durumda "paylaş" düğmesinden boş dönmez.
 */
private fun shareCurrentPage(
    context: android.content.Context,
    page: com.kitsugi.animelist.data.manga.MangaPage?,
    chapterName: String
) {
    if (page == null) return
    val imageUrl = page.imageUrl ?: page.url
    val title = "$chapterName · Sayfa ${page.index + 1}"
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, "$title" + System.lineSeparator() + imageUrl)
        }
        context.startActivity(android.content.Intent.createChooser(intent, title))
    } catch (_: Exception) {
        // Paylaşım hedefi yoksa sessizce yut — okuyucu akışını bozmaz.
    }
}

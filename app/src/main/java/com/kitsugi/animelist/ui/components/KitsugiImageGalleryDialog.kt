package com.kitsugi.animelist.ui.components

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.KitsugiImageDownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitsugiImageGalleryDialog(
    galleryItems: List<GalleryItem>,
    initialIndex: Int = 0,
    initialCategory: GalleryCategory? = galleryItems.getOrNull(initialIndex)?.category,
    title: String,
    isAdult: Boolean = false,
    allowDownload: Boolean = true,
    onDismiss: () -> Unit
) {
    if (galleryItems.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // İndirme index'i: "zaten indirilmiş" rozetini göstermek ve tekrar
    // indirmeyi önlemek için dinlenir. Açılışta arka planda tazelenir.
    val downloadedUrls by KitsugiImageDownloadHelper.downloadedUrls.collectAsState()
    LaunchedEffect(Unit) {
        KitsugiImageDownloadHelper.refreshDownloadedUrls(context)
        // Çökme raporu için iz: hangi galeri, kaç görsel
        try {
            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                .noteScreen("Galeri: $title (${galleryItems.size} görsel)")
        } catch (_: Throwable) {}
    }

    // We only show category tabs if there are multiple categories.
    val availableCategories = remember(galleryItems) {
        val cats = galleryItems.map { it.category }.distinct()
        if (cats.size > 1) {
            listOf(null) + cats.sortedBy { it.ordinal } // null representing "All" (Tümü)
        } else {
            emptyList()
        }
    }

    var selectedCategoryFilter by remember(initialCategory, initialIndex) {
        mutableStateOf<GalleryCategory?>(initialCategory)
    }

    // Filtered items based on selected category
    val filteredItems = remember(galleryItems, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) {
            galleryItems
        } else {
            galleryItems.filter { it.category == selectedCategoryFilter }
        }
    }

    val resolvedInitialPage = remember(galleryItems, initialIndex, filteredItems) {
        val initialItem = galleryItems.getOrNull(initialIndex)
        if (initialItem != null) {
            val idx = filteredItems.indexOf(initialItem)
            if (idx >= 0) idx else 0
        } else {
            0
        }
    }

    val pagerState = rememberPagerState(initialPage = resolvedInitialPage.coerceIn(0, maxOf(0, filteredItems.size - 1)), pageCount = { filteredItems.size })

    var isFirstLaunch by remember { mutableStateOf(true) }

    // Reset to page 0 if selectedCategoryFilter changes to avoid index bounds error, ignoring first launch
    LaunchedEffect(selectedCategoryFilter) {
        if (isFirstLaunch) {
            isFirstLaunch = false
        } else {
            if (filteredItems.isNotEmpty()) {
                pagerState.scrollToPage(0)
            }
        }
    }

    val accentColor = LocalKitsugiAccent.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Galeride gezinme izi (çökme raporunda görünür)
    LaunchedEffect(pagerState.currentPage, filteredItems.size) {
        try {
            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                .noteAction("galeri gezinme: sayfa ${pagerState.currentPage + 1}/${filteredItems.size}")
        } catch (_: Throwable) {}
    }

    // Slide-up and fade transitions state
    var isAnimatedVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isAnimatedVisible = true
    }

    val dismissWithAnimation = {
        scope.launch {
            isAnimatedVisible = false
            delay(280)
            onDismiss()
        }
    }

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            val currentItem = filteredItems.getOrNull(pagerState.currentPage)
            if (currentItem != null) {
                KitsugiImageDownloadHelper.downloadImage(context, currentItem.url, title)
            }
        } else {
            android.widget.Toast.makeText(context, "Depolama izni verilmedi.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Download button pulse animation
    val downloadPulse = rememberInfiniteTransition(label = "download_pulse")
    val downloadGlow by downloadPulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "download_glow"
    )

    // Mevcut sayfa için indirme butonu durumu:
    //  • Yerel dosya (file:// / content://) ise buton hiç gösterilmez — içerik zaten cihazda.
    //  • allowDownload=false ise buton hiç gösterilmez (örn. İndirilenler ekranı).
    //  • Daha önce indirilmişse buton "indirildi" (tik) durumunda gösterilir.
    val currentActionItem = filteredItems.getOrNull(pagerState.currentPage)
    val isLocalImage = currentActionItem?.url?.let {
        it.startsWith("file://") || it.startsWith("content://")
    } == true
    val showDownloadButton = allowDownload && !isLocalImage
    val isAlreadyDownloaded = !isLocalImage &&
        currentActionItem != null &&
        downloadedUrls.contains(currentActionItem.url)

    val onDownload = {
        val currentItem = filteredItems.getOrNull(pagerState.currentPage)
        if (currentItem != null) {
            val url = currentItem.url
            when {
                url.startsWith("file://") || url.startsWith("content://") -> {
                    android.widget.Toast.makeText(context, "Bu resim zaten cihazda kayıtlı.", android.widget.Toast.LENGTH_SHORT).show()
                }
                downloadedUrls.contains(url) -> {
                    android.widget.Toast.makeText(context, "Bu resim zaten indirilmiş.", android.widget.Toast.LENGTH_SHORT).show()
                }
                KitsugiImageDownloadHelper.hasWritePermission(context) -> {
                    KitsugiImageDownloadHelper.downloadImage(context, url, title)
                }
                else -> {
                    launcher.launch(KitsugiImageDownloadHelper.getRequiredPermissions())
                }
            }
        }
    }

    val onShare = {
        val currentItem = filteredItems.getOrNull(pagerState.currentPage)
        if (currentItem != null) {
            KitsugiImageDownloadHelper.shareImage(context, currentItem.url, title)
        }
    }

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isAnimatedVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300, easing = EaseOutCubic)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 280, easing = EaseInCubic)
                ) + fadeOut(animationSpec = tween(280)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    KitsugiColors.Surface.copy(alpha = 0.97f),
                                    KitsugiColors.Background.copy(alpha = 0.99f)
                                ),
                                radius = 1800f
                            )
                        )
                ) {
                    // Ambient glow
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.04f),
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.03f)
                                    )
                                )
                            )
                    )

                    if (isLandscape) {
                        // ── LANDSCAPE LAYOUT (fanart.tv style) ─────────────────────────
                        GalleryLandscapeLayout(
                            filteredItems = filteredItems,
                            availableCategories = availableCategories,
                            selectedCategoryFilter = selectedCategoryFilter,
                            onCategorySelected = { selectedCategoryFilter = it },
                            pagerState = pagerState,
                            title = title,
                            accentColor = accentColor,
                            downloadGlow = downloadGlow,
                            onDownload = onDownload,
                            onShare = onShare,
                            onDismiss = { dismissWithAnimation() },
                            density = density,
                            galleryItems = galleryItems,
                            isAdult = isAdult,
                            showDownloadButton = showDownloadButton,
                            isAlreadyDownloaded = isAlreadyDownloaded
                        )
                    } else {
                        // ── PORTRAIT LAYOUT (existing behaviour) ───────────────────────
                        GalleryPortraitLayout(
                            filteredItems = filteredItems,
                            availableCategories = availableCategories,
                            selectedCategoryFilter = selectedCategoryFilter,
                            onCategorySelected = { selectedCategoryFilter = it },
                            pagerState = pagerState,
                            title = title,
                            accentColor = accentColor,
                            downloadGlow = downloadGlow,
                            onDownload = onDownload,
                            onShare = onShare,
                            onDismiss = { dismissWithAnimation() },
                            density = density,
                            galleryItems = galleryItems,
                            isAdult = isAdult,
                            showDownloadButton = showDownloadButton,
                            isAlreadyDownloaded = isAlreadyDownloaded
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LANDSCAPE LAYOUT  –  fanart.tv style
//   Left: vertical thumbnail rail
//   Center: full image + left/right nav arrows
//   Right: detail panel (source, category, language, resolution, etc.)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryLandscapeLayout(
    filteredItems: List<GalleryItem>,
    availableCategories: List<GalleryCategory?>,
    selectedCategoryFilter: GalleryCategory?,
    onCategorySelected: (GalleryCategory?) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    title: String,
    accentColor: Color,
    downloadGlow: Float,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    density: androidx.compose.ui.unit.Density,
    galleryItems: List<GalleryItem>,
    isAdult: Boolean = false,
    showDownloadButton: Boolean = true,
    isAlreadyDownloaded: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val currentItem = filteredItems.getOrNull(pagerState.currentPage)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 18.dp)
    ) {

        // ── LEFT: Vertical thumbnail rail ─────────────────────────────────────
        val thumbRailState = rememberLazyListState()

        LaunchedEffect(pagerState.currentPage) {
            val viewportHeight = thumbRailState.layoutInfo.viewportEndOffset - thumbRailState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val itemHeightPx = with(density) { 72.dp.roundToPx() }
                val targetOffset = (viewportHeight - itemHeightPx) / 2
                thumbRailState.animateScrollToItem(pagerState.currentPage, -targetOffset)
            } else {
                thumbRailState.animateScrollToItem(pagerState.currentPage)
            }
        }

        Box(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(KitsugiColors.Background.copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = KitsugiColors.Border.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(0.dp)
                )
        ) {
            LazyColumn(
                state = thumbRailState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(filteredItems) { index, item ->
                    val isSelected = pagerState.currentPage == index
                    val thumbScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 0.92f,
                        animationSpec = spring(dampingRatio = 0.65f),
                        label = "ls_thumb_scale_$index"
                    )
                    val thumbAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.45f,
                        label = "ls_thumb_alpha_$index"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer(scaleX = thumbScale, scaleY = thumbScale, alpha = thumbAlpha)
                    ) {
                        KitsugiNsfwImage(
                            model = item.url,
                            contentDescription = "Küçük resim $index",
                            isAdult = isAdult,
                            modifier = Modifier
                                .size(width = 62.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            contentScale = ContentScale.Crop
                        )
                        // Active indicator bar at left edge
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                                    .background(accentColor)
                                    .align(Alignment.CenterStart)
                                    .offset(x = (-8).dp)
                            )
                        }
                    }
                }
            }
        }

        // ── CENTER: Main image with nav arrows ────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Category filter bar at top (compact)
            Column(modifier = Modifier.fillMaxSize()) {
                if (availableCategories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(availableCategories) { _, cat ->
                            val isSelected = selectedCategoryFilter == cat
                            val count = if (cat == null) galleryItems.size else galleryItems.count { it.category == cat }
                            val label = if (cat == null) "Tümü ($count)" else "${cat.label} ($count)"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) accentColor else KitsugiColors.SurfaceSoft)
                                    .tvClickable(shape = RoundedCornerShape(999.dp)) { onCategorySelected(cat) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Pager image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 8.dp,
                        verticalAlignment = Alignment.CenterVertically
                    ) { page ->
                        val item = filteredItems.getOrNull(page)
                        if (item != null) {
                            GalleryImagePage(
                                imageUrl = item.url,
                                title = title,
                                page = page,
                                pagerState = pagerState,
                                isAdult = isAdult
                            )
                        }
                    }

                    // Left navigation arrow
                    if (pagerState.currentPage > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), CircleShape)
                                .tvClickable(shape = CircleShape) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = "Önceki",
                                tint = KitsugiColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Right navigation arrow
                    if (pagerState.currentPage < filteredItems.size - 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), CircleShape)
                                .tvClickable(shape = CircleShape) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Sonraki",
                                tint = KitsugiColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Bottom: page counter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (filteredItems.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1}",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "/ ${filteredItems.size}",
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── RIGHT: Detail panel (fanart.tv right side) ────────────────────────
        Box(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(KitsugiColors.Surface.copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = KitsugiColors.Border.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(0.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Top action row (Download | Share | Close)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download — yerel dosyalarda gizli; zaten indirilmişse tik işareti
                    if (showDownloadButton) {
                        val downloadBtnColor =
                            if (isAlreadyDownloaded) KitsugiColors.AccentGreen else accentColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = downloadBtnColor.copy(alpha = 0.18f * downloadGlow),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(1.dp, downloadBtnColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .tvClickable(shape = RoundedCornerShape(10.dp), onClick = onDownload),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAlreadyDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                contentDescription = if (isAlreadyDownloaded) "İndirildi" else "İndir",
                                tint = downloadBtnColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Share / Send (Animated Fly)
                    KitsugiFlySendIconButton(
                        onClick = onShare,
                        size = 36.dp,
                        contentDescription = "Paylaş"
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Close
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = KitsugiColors.SurfaceStrong.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(1.dp, KitsugiColors.Border, RoundedCornerShape(10.dp))
                            .tvClickable(shape = RoundedCornerShape(10.dp), onClick = { onDismiss() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Kapat",
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Title
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = accentColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail tabs: Details / Fans / Comments — mimic fanart.tv
                var detailTab by remember { mutableStateOf(0) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Detaylar", "Diğer Resimler").forEachIndexed { idx, tabLabel ->
                        val sel = detailTab == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (sel) accentColor.copy(alpha = 0.5f) else KitsugiColors.Border.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(8.dp)) { detailTab = idx }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (sel) accentColor else KitsugiColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KitsugiColors.Border.copy(alpha = 0.4f))
                )

                // Panel content
                if (detailTab == 0) {
                    // ── Details tab ──
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (currentItem != null) {
                            // Source
                            val badgeBg = when (currentItem.source.lowercase()) {
                                "tmdb"      -> Color(0xFFFFB800)
                                "fanart.tv" -> Color(0xFF9C27B0)
                                "anilist"   -> Color(0xFF02A9FF)
                                "simkl"     -> Color(0xFFE50914)
                                "kitsu"     -> Color(0xFFE35A02)
                                "shikimori" -> Color(0xFF4C86C8)
                                "bangumi"   -> Color(0xFFF09199)
                                "jikan", "jikan (mal)", "mal" -> Color(0xFF2E51A2)
                                else        -> accentColor
                            }

                            item {
                                DetailRow(
                                    label = "Kaynak",
                                    value = null,
                                    badge = {
                                        val logoRes = KitsugiPlatformLogos.resFor(currentItem.source)
                                        if (logoRes != null) {
                                            KitsugiPlatformLogo(
                                                platformId = currentItem.source,
                                                size = 28.dp,
                                                cornerRadius = 6.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(badgeBg)
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = currentItem.source,
                                                    color = if (currentItem.source.equals("tmdb", ignoreCase = true)) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            // Category
                            val catEmoji = when (currentItem.category) {
                                GalleryCategory.POSTER    -> "📋"
                                GalleryCategory.BACKDROP  -> "🖼️"
                                GalleryCategory.LOGO      -> "🎨"
                                GalleryCategory.CLEARART  -> "✨"
                                GalleryCategory.CHARACTER -> "🎭"
                                GalleryCategory.PERSON    -> "👤"
                                GalleryCategory.THUMBNAIL -> "🌐"
                                GalleryCategory.BANNER    -> "🎫"
                                GalleryCategory.SQUARE    -> "🟩"
                                GalleryCategory.OTHER     -> "📁"
                            }
                            item {
                                DetailRow(label = "Tür", value = "$catEmoji ${currentItem.category.label}")
                            }

                            // Language
                            val langInfo = formatLanguage(currentItem.language)
                            if (langInfo != null) {
                                item {
                                    DetailRow(label = "Dil", value = "${langInfo.first} ${langInfo.second}")
                                }
                            } else if (currentItem.language == null &&
                                (currentItem.category == GalleryCategory.BACKDROP ||
                                 currentItem.category == GalleryCategory.LOGO ||
                                 currentItem.category == GalleryCategory.CLEARART)
                            ) {
                                item {
                                    DetailRow(label = "Dil", value = "✨ Metinsiz")
                                }
                            }

                            // Resolution
                            val resStr = formatResolution(currentItem.width, currentItem.height)
                            if (resStr != null) {
                                item {
                                    DetailRow(label = "Boyut", value = "📐 $resStr")
                                }
                            }

                            // Description
                            if (!currentItem.description.isNullOrBlank() &&
                                currentItem.description != "TMDB Poster" &&
                                currentItem.description != "TMDB Arka Plan" &&
                                currentItem.description != "TMDB Logo"
                            ) {
                                item {
                                    DetailRow(label = "Açıklama", value = currentItem.description)
                                }
                            }

                            // Separator
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            // Page indicator
                            item {
                                DetailRow(
                                    label = "Sayfa",
                                    value = "${pagerState.currentPage + 1} / ${filteredItems.size}"
                                )
                            }
                        }
                    }
                } else {
                    // ── Other images tab: small grid of same-category items ──
                    val sameCategory = remember(currentItem, galleryItems) {
                        if (currentItem == null) emptyList()
                        else galleryItems.filter { it.category == currentItem.category && it.url != currentItem.url }
                    }
                    if (sameCategory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bu kategoride başka resim yok",
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(sameCategory) { _, relatedItem ->
                                val relIdx = filteredItems.indexOf(relatedItem)
                                KitsugiNsfwImage(
                                    model = relatedItem.url,
                                    contentDescription = "İlgili resim",
                                    isAdult = isAdult,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, KitsugiColors.Border.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                            if (relIdx >= 0) {
                                                scope.launch { pagerState.animateScrollToPage(relIdx) }
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PORTRAIT LAYOUT  –  original behaviour
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryPortraitLayout(
    filteredItems: List<GalleryItem>,
    availableCategories: List<GalleryCategory?>,
    selectedCategoryFilter: GalleryCategory?,
    onCategorySelected: (GalleryCategory?) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    title: String,
    accentColor: Color,
    downloadGlow: Float,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    density: androidx.compose.ui.unit.Density,
    galleryItems: List<GalleryItem>,
    isAdult: Boolean = false,
    showDownloadButton: Boolean = true,
    isAlreadyDownloaded: Boolean = false
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header (üst)
        KitsugiGalleryHeader(
            title = title,
            currentPage = pagerState.currentPage,
            totalPages = filteredItems.size,
            accentColor = accentColor,
            downloadGlow = downloadGlow,
            onDownload = onDownload,
            onShare = onShare,
            onDismiss = onDismiss,
            isLandscape = false,
            showDownloadButton = showDownloadButton,
            isAlreadyDownloaded = isAlreadyDownloaded
        )

        // Categories filter (if multiple categories available)
        if (availableCategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(availableCategories) { _, cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val count = if (cat == null) galleryItems.size else galleryItems.count { it.category == cat }
                    val label = if (cat == null) "Tümü ($count)" else "${cat.label} ($count)"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) accentColor else KitsugiColors.SurfaceSoft)
                            .tvClickable(shape = RoundedCornerShape(999.dp)) {
                                onCategorySelected(cat)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Main Pager & Image area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val item = filteredItems.getOrNull(page)
                if (item != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GalleryImagePage(
                            imageUrl = item.url,
                            title = title,
                            page = page,
                            pagerState = pagerState,
                            isAdult = isAdult
                        )

                        // Source, Category & Metadata badge overlay on the page
                        FlowRow(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.88f))
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val badgeBg = when (item.source.lowercase()) {
                                "tmdb"      -> Color(0xFFFFB800)
                                "fanart.tv" -> Color(0xFF9C27B0)
                                "anilist"   -> Color(0xFF02A9FF)
                                "simkl"     -> Color(0xFFE50914)
                                "kitsu"     -> Color(0xFFE35A02)
                                "shikimori" -> Color(0xFF4C86C8)
                                "bangumi"   -> Color(0xFFF09199)
                                "jikan", "jikan (mal)", "mal" -> Color(0xFF2E51A2)
                                else        -> accentColor
                            }

                            // 1. Kaynak Rozeti
                            val logoRes = KitsugiPlatformLogos.resFor(item.source)
                            if (logoRes != null) {
                                KitsugiPlatformLogo(
                                    platformId = item.source,
                                    size = 22.dp,
                                    cornerRadius = 5.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = item.source,
                                        color = if (item.source.equals("tmdb", ignoreCase = true)) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Kategori Rozeti
                            val catEmoji = when (item.category) {
                                GalleryCategory.POSTER    -> "📋"
                                GalleryCategory.BACKDROP  -> "🖼️"
                                GalleryCategory.LOGO      -> "🎨"
                                GalleryCategory.CLEARART  -> "✨"
                                GalleryCategory.CHARACTER -> "🎭"
                                GalleryCategory.PERSON    -> "👤"
                                GalleryCategory.THUMBNAIL -> "🌐"
                                GalleryCategory.BANNER    -> "🎫"
                                GalleryCategory.SQUARE    -> "🟩"
                                GalleryCategory.OTHER     -> "📁"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KitsugiColors.SurfaceSoft)
                                    .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$catEmoji ${item.category.label}",
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // 3. Dil Bilgisi
                            val langInfo = formatLanguage(item.language)
                            if (langInfo != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft)
                                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${langInfo.first} ${langInfo.second}",
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (item.language == null && (item.category == GalleryCategory.BACKDROP || item.category == GalleryCategory.LOGO || item.category == GalleryCategory.CLEARART)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "✨ Metinsiz",
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }

                            // 4. Çözünürlük Bilgisi
                            val resStr = formatResolution(item.width, item.height)
                            if (resStr != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft)
                                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "📐 $resStr",
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 5. Özel Açıklama (varsa)
                            if (!item.description.isNullOrBlank() &&
                                item.description != "TMDB Poster" &&
                                item.description != "TMDB Arka Plan" &&
                                item.description != "TMDB Logo") {
                                Text(
                                    text = item.description,
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Thumbnail Strip (alt)
        if (filteredItems.size > 1) {
            val listState = rememberLazyListState()

            LaunchedEffect(pagerState.currentPage) {
                val listInfo = listState.layoutInfo
                val viewportWidth = listInfo.viewportEndOffset - listInfo.viewportStartOffset
                if (viewportWidth > 0) {
                    val itemWidthPx = with(density) { 52.dp.roundToPx() }
                    val targetOffset = (viewportWidth - itemWidthPx) / 2
                    listState.animateScrollToItem(pagerState.currentPage, -targetOffset)
                } else {
                    listState.animateScrollToItem(pagerState.currentPage)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                KitsugiColors.Background.copy(alpha = 0.9f),
                                KitsugiColors.Surface.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, top = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(filteredItems) { index, item ->
                        val isSelected = pagerState.currentPage == index
                        val thumbScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 0.88f,
                            animationSpec = spring(dampingRatio = 0.6f),
                            label = "thumb_scale_$index"
                        )
                        val thumbAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.38f,
                            label = "thumb_alpha_$index"
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer(scaleX = thumbScale, scaleY = thumbScale, alpha = thumbAlpha)
                        ) {
                            KitsugiNsfwImage(
                                model = item.url,
                                contentDescription = "Thumbnail $index",
                                isAdult = isAdult,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                contentScale = ContentScale.Crop
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accentColor)
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail row helper for the right panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    badge: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        if (badge != null) {
            badge()
        } else if (value != null) {
            Text(
                text = value,
                color = KitsugiColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KitsugiColors.Border.copy(alpha = 0.25f))
        )
    }
}

@JvmName("KitsugiImageGalleryDialogFromUrls")
@Composable
fun KitsugiImageGalleryDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    title: String,
    isAdult: Boolean = false,
    allowDownload: Boolean = true,
    onDismiss: () -> Unit
) {
    val items = remember(imageUrls) {
        imageUrls.map { GalleryItem(url = it, source = "Kitsugi", category = GalleryCategory.OTHER) }
    }
    KitsugiImageGalleryDialog(
        galleryItems = items,
        initialIndex = initialIndex,
        title = title,
        isAdult = isAdult,
        allowDownload = allowDownload,
        onDismiss = onDismiss
    )
}

// Custom gesture detector helper to allow page swipes when not zoomed in
suspend fun PointerInputScope.detectTransformGesturesCustom(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    shouldConsume: () -> Boolean
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown()
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    onGesture(panChange, zoomChange)
                    if (shouldConsume()) {
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryImagePage(
    imageUrl: String,
    title: String,
    page: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isAdult: Boolean = false
) {
    var zoomScale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        zoomScale = 1f
        offset = Offset.Zero
    }

    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
    val isZoomed = zoomScale > 1f

    val dominoScale = if (isZoomed) 1f else (1f - (abs(pageOffset) * 0.15f)).coerceIn(0.75f, 1f)
    val dominoAlpha = if (isZoomed) 1f else (1f - (abs(pageOffset) * 0.45f)).coerceIn(0.35f, 1f)
    val dominoTranslationX = if (isZoomed) 0f else (pageOffset * 60.dp.value)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1f) {
                                zoomScale = 1f
                                offset = Offset.Zero
                            } else {
                                zoomScale = 2.5f
                                offset = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGesturesCustom(
                        onGesture = { pan, zoom ->
                            val newZoom = (zoomScale * zoom).coerceIn(1f, 5f)
                            zoomScale = newZoom
                            if (newZoom > 1f) {
                                val maxX = (widthPx * (newZoom - 1f)) / 2f
                                val maxY = (heightPx * (newZoom - 1f)) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        },
                        shouldConsume = { zoomScale > 1f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // +18 içeriklerde her koşulda bulanıklık (cihaz desteklemiyorsa bitmap blur)
            KitsugiNsfwImage(
                model = imageUrl,
                contentDescription = "$title - Resim $page",
                isAdult = isAdult,
                modifier = Modifier
                    .fillMaxSize(0.88f)
                    .graphicsLayer(
                        scaleX = zoomScale * dominoScale,
                        scaleY = zoomScale * dominoScale,
                        translationX = offset.x + dominoTranslationX,
                        translationY = offset.y,
                        alpha = dominoAlpha
                    )
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun KitsugiGalleryHeader(
    title: String,
    currentPage: Int,
    totalPages: Int,
    accentColor: Color,
    downloadGlow: Float,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    isLandscape: Boolean,
    showDownloadButton: Boolean = true,
    isAlreadyDownloaded: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // Glassmorphism header background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            KitsugiColors.Surface.copy(alpha = if (isLandscape) 0.82f else 0.90f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol: Başlık + Sayfa göstergesi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // İkon kutusu
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (totalPages > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${currentPage + 1}",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "/ $totalPages",
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Sağ: Butonlar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // İndirme Butonu — accent renkli, animasyonlu
                    // Yerel dosyalarda gizli; zaten indirilmişse tik işareti
                    if (showDownloadButton) {
                        val downloadBtnColor =
                            if (isAlreadyDownloaded) KitsugiColors.AccentGreen else accentColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = downloadBtnColor.copy(alpha = 0.18f * downloadGlow),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = downloadBtnColor.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onDownload),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAlreadyDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                contentDescription = if (isAlreadyDownloaded) "İndirildi" else "İndir",
                                tint = downloadBtnColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Paylaşım Butonu — accent dokunuşlu glassmorphism
                    // Share / Send (Animated Fly)
                    KitsugiFlySendIconButton(
                        onClick = onShare,
                        size = 40.dp,
                        contentDescription = "Paylaş"
                    )

                    // Kapatma Butonu — nötr, SurfaceSoft tabanlı
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = KitsugiColors.SurfaceStrong.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = KitsugiColors.Border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Kapat",
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatLanguage(code: String?): Pair<String, String>? {
    if (code.isNullOrBlank() || code == "00") return null
    return when (code.lowercase()) {
        "tr" -> Pair("🇹🇷", "Türkçe")
        "en" -> Pair("🇬🇧", "İngilizce")
        "ja" -> Pair("🇯🇵", "Japonca")
        "de" -> Pair("🇩🇪", "Almanca")
        "fr" -> Pair("🇫🇷", "Fransızca")
        "es" -> Pair("🇪🇸", "İspanyolca")
        "it" -> Pair("🇮🇹", "İtalyanca")
        "ko" -> Pair("🇰🇷", "Korece")
        "zh" -> Pair("🇨🇳", "Çince")
        "ru" -> Pair("🇷🇺", "Rusça")
        "pt" -> Pair("🇵🇹", "Portekizce")
        else -> Pair("🌐", code.uppercase())
    }
}

private fun formatResolution(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val tag = when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "2K"
        width >= 1920 || height >= 1080 -> "FHD"
        width >= 1280 || height >= 720  -> "HD"
        else -> null
    }
    return if (tag != null) "$width×$height ($tag)" else "$width×$height"
}

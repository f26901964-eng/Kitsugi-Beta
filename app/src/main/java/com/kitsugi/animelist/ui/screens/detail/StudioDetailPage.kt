package com.kitsugi.animelist.ui.screens.detail

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.KitsugiCinematicLoadingScreen
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiExploreMediaCard
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiPageEnter
import com.kitsugi.animelist.ui.components.KitsugiRankingMediaCard
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.parseToMediaType
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import kotlinx.coroutines.launch

sealed interface StudioDetailState {
    object Loading : StudioDetailState
    data class Error(val message: String) : StudioDetailState
    data class Success(val detail: com.kitsugi.animelist.data.remote.KitsugiStudioDetail) : StudioDetailState
}

@Composable
fun StudioDetailPage(
    studioId: Int,
    source: String,
    onBackClick: () -> Unit,
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit,
    name: String? = null,
    imageUrl: String? = null,
    titleLanguage: String = "ROMAJI",
    preferredTranslator: String = "DEFAULT"
) {
    val accentColor = LocalKitsugiAccent.current

    // Obtain ViewModel
    val viewModel: StudioDetailViewModel = viewModel(key = "studio_${source}_${studioId}")

    // Load studio in ViewModel
    LaunchedEffect(studioId, source, name) {
        viewModel.loadStudio(studioId, source, name)
    }

    // Collect state from ViewModel
    val state by viewModel.state.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val isAniListSource = source.lowercase() == "anilist"
    val context = LocalContext.current
    val isAniListConnected = remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) != null }
    val showFavouriteButton = isAniListSource || isAniListConnected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        when (val currentState = state) {
            is StudioDetailState.Loading -> {
                KitsugiCinematicLoadingScreen(
                    title = name?.takeIf { it.isNotBlank() } ?: "Stüdyo / Yapımcı Yükleniyor...",
                    imageUrl = imageUrl,
                    onBackClick = onBackClick,
                    source = source
                )
            }
            is StudioDetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentState.message,
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                viewModel.retry()
                            }
                        ) {
                            Text("Yeniden Dene", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is StudioDetailState.Success -> {
                val detail = currentState.detail
                val galleryItems by viewModel.galleryItems.collectAsState()
                val translatedAbout by viewModel.translatedAbout.collectAsState()
                var activeGalleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
                var activeGalleryIndex by remember { mutableStateOf(0) }

                StudioDetailSuccessContent(
                    detail = detail,
                    source = source,
                    accentColor = accentColor,
                    isFavourite = isFavourite,
                    showFavouriteButton = showFavouriteButton,
                    galleryItems = galleryItems,
                    titleLanguage = titleLanguage,
                    translatedAbout = translatedAbout,
                    preferredTranslator = preferredTranslator,
                    onTranslateAbout = { viewModel.translateAbout() },
                    onBackClick = onBackClick,
                    onToggleFavourite = { viewModel.toggleFavourite() },
                    onMediaClick = onMediaClick,
                    onGalleryClick = { items, idx ->
                        activeGalleryItems = items
                        activeGalleryIndex = idx
                    }
                )

                if (activeGalleryItems.isNotEmpty()) {
                    KitsugiImageGalleryDialog(
                        galleryItems = activeGalleryItems,
                        initialIndex = activeGalleryIndex,
                        title = detail.name,
                        onDismiss = { activeGalleryItems = emptyList() }
                    )
                }
            }
        }
    }
}

/**
 * Stüdyo / yapımcı detay içeriği — keşfet "Tümünü Gör" (FullScreenMediaGridPage)
 * sayfalarıyla BİREBİR aynı davranış: ekran boyutuna ve dikey/yatay moda göre
 * uyarlanan kolon sayısı, grid/liste görünümleri, emojili tür çipleri, filtre
 * bottom sheet'i, kaydırınca beliren üst şerit (floating header) ve aşağı
 * kaydırınca çıkan yukarı kaydırma FAB'ı.
 */
@Composable
private fun StudioDetailSuccessContent(
    detail: com.kitsugi.animelist.data.remote.KitsugiStudioDetail,
    source: String,
    accentColor: Color,
    isFavourite: Boolean,
    showFavouriteButton: Boolean,
    galleryItems: List<GalleryItem>,
    titleLanguage: String,
    translatedAbout: String? = null,
    preferredTranslator: String = "DEFAULT",
    onTranslateAbout: () -> Unit = {},
    onBackClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit,
    onGalleryClick: (List<GalleryItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = LocalIsTv.current

    // "Hakkında" kartı eylemleri — detay sayfasındaki Açıklama kartıyla aynı sözleşme:
    // metin hâlâ ham ise uygulama içi çeviri, çevrilmişse 3. parti çevirmen açılır.
    val onAboutTranslateClick: (String) -> Unit = { text ->
        val currentText = translatedAbout ?: detail.about
        if (currentText == detail.about) {
            onTranslateAbout()
        } else {
            context.openTranslator(text, preferredTranslator)
        }
    }
    val onAboutCopyClick: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("studio_about", text))
        android.widget.Toast.makeText(context, "Panoya kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
    }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Keşfet sayfalarıyla aynı duyarlı kolon hesabı ─────────────────────────
    val columnCount = remember(isLandscape, screenWidthDp) {
        if (isLandscape) {
            when {
                screenWidthDp >= 1200 -> 5
                screenWidthDp >= 800 -> 4
                else -> 3
            }
        } else {
            when {
                screenWidthDp >= 900 -> 5
                screenWidthDp >= 600 -> 4
                else -> 3
            }
        }
    }

    // Grid/Liste görünümü tercihi kalıcı olarak hatırlanır
    val gridPrefs = remember(context) { context.getSharedPreferences("kitsugi_ui_prefs", android.content.Context.MODE_PRIVATE) }
    var isGridView by remember { mutableStateOf(gridPrefs.getBoolean("studio_detail_is_grid_view", true)) }
    LaunchedEffect(isGridView) {
        gridPrefs.edit().putBoolean("studio_detail_is_grid_view", isGridView).apply()
    }

    // ── Filtre / sıralama durumu ──────────────────────────────────────────────
    var selectedTypeId by rememberSaveable { mutableStateOf("ALL") }
    var selectedSortOption by rememberSaveable { mutableStateOf(StudioSortOption.DEFAULT) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val hasActiveFilter = selectedTypeId != "ALL" || selectedSortOption != StudioSortOption.DEFAULT

    val displayedWorks = remember(detail, selectedTypeId, selectedSortOption) {
        val filtered = detail.mediaWorks.filter {
            studioTypeMatches(selectedTypeId, it.mediaType.parseToMediaType())
        }
        when (selectedSortOption) {
            StudioSortOption.DEFAULT -> filtered
            StudioSortOption.TITLE_ASC -> filtered.sortedBy { it.mediaTitle.lowercase() }
            StudioSortOption.TITLE_DESC -> filtered.sortedByDescending { it.mediaTitle.lowercase() }
        }
    }

    // ── Scroll state'leri + üst şerit / yukarı FAB tetikleyicileri ────────────
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val showFloatingHeader = if (isGridView) gridState.firstVisibleItemIndex >= 1
    else listState.firstVisibleItemIndex >= 1

    val showScrollToTop by remember {
        derivedStateOf {
            if (isGridView) gridState.firstVisibleItemIndex > 3
            else listState.firstVisibleItemIndex > 3
        }
    }

    val onOpenGallery: (() -> Unit)? = if (galleryItems.isNotEmpty()) {
        { onGalleryClick(galleryItems, 0) }
    } else null

    // Kontroller başlığı (başlık + filtre + görünüm toggle + çipler + sayaç)
    val controlsHeader: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yapımlar (${detail.mediaWorks.size})",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                // Filtre ve Sıralama butonu
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else KitsugiColors.Surface)
                ) {
                    Icon(
                        Icons.Rounded.FilterList,
                        contentDescription = "Filtre ve Sıralama",
                        tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Grid ↔ Liste toggle
                IconButton(
                    onClick = {
                        scope.launch {
                            if (isGridView) {
                                val gridIndex = gridState.firstVisibleItemIndex
                                val gridOffset = gridState.firstVisibleItemScrollOffset
                                val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                                isGridView = false
                                kotlinx.coroutines.delay(10)
                                listState.scrollToItem(listIndex, gridOffset)
                            } else {
                                val listIndex = listState.firstVisibleItemIndex
                                val listOffset = listState.firstVisibleItemScrollOffset
                                val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                                isGridView = true
                                kotlinx.coroutines.delay(10)
                                gridState.scrollToItem(gridIndex, listOffset)
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.Surface)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ListAlt else Icons.Rounded.GridView,
                        contentDescription = if (isGridView) "Liste Görünümü" else "Grid Görünümü",
                        tint = accentColor
                    )
                }
            }

            // Emojili hızlı tür çipleri (keşfet sayfalarındaki şeridin aynısı)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
            ) {
                items(STUDIO_TYPE_FILTERS) { typeFilter ->
                    val isSelected = selectedTypeId == typeFilter.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.22f)
                                else KitsugiColors.Surface
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedTypeId = if (isSelected && typeFilter.id != "ALL") "ALL" else typeFilter.id
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = typeFilter.displayLabel,
                            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Sayaç + aktif filtre açıklaması + Temizle
            val activeType = STUDIO_TYPE_FILTERS.firstOrNull { it.id == selectedTypeId }
            val activeFilterDesc = buildString {
                if (activeType != null && activeType.id != "ALL") append(" • ${activeType.displayLabel}")
                if (selectedSortOption != StudioSortOption.DEFAULT) append(" • ${selectedSortOption.displayLabel}")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${displayedWorks.size} içerik$activeFilterDesc",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasActiveFilter) {
                    Text(
                        text = "Temizle",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            selectedTypeId = "ALL"
                            selectedSortOption = StudioSortOption.DEFAULT
                        }
                    )
                }
            }
        }
    }

    KitsugiPageEnter {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isGridView) {
                // ── GRID MODU ─────────────────────────────────────────────────
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    state = gridState,
                    modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "studio_hero", span = { GridItemSpan(maxLineSpan) }) {
                        StudioHeroHeader(
                            detail = detail,
                            source = source,
                            accentColor = accentColor,
                            onBackClick = onBackClick,
                            isFavourite = isFavourite,
                            showFavouriteButton = showFavouriteButton,
                            onToggleFavourite = onToggleFavourite,
                            onGalleryClick = onOpenGallery,
                            height = if (isLandscape) 260.dp else 340.dp
                        )
                    }

                    if (!detail.about.isNullOrBlank()) {
                        item(key = "studio_about", span = { GridItemSpan(maxLineSpan) }) {
                            StudioAboutSection(
                                about = detail.about,
                                onGalleryClick = onGalleryClick,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                translatedAbout = translatedAbout,
                                onTranslateClick = onAboutTranslateClick,
                                onCopyClick = onAboutCopyClick
                            )
                        }
                    }

                    item(key = "studio_controls", span = { GridItemSpan(maxLineSpan) }) {
                        controlsHeader()
                    }

                    if (displayedWorks.isEmpty()) {
                        item(key = "studio_empty", span = { GridItemSpan(maxLineSpan) }) {
                            KitsugiEmptyState(
                                title = "Henüz içerik yok",
                                subtitle = "Bu stüdyo için gösterilecek yapım bulunamadı.",
                                icon = Icons.Rounded.SearchOff
                            )
                        }
                    } else {
                        gridItems(
                            displayedWorks,
                            key = { work -> "${work.source}_${work.mediaId}_g" }
                        ) { work ->
                            KitsugiExploreMediaCard(
                                result = work.toSearchResult(),
                                onClick = { onMediaClick(work.mediaId, work.mediaType, work.source) },
                                titleLanguage = titleLanguage,
                                forceVertical = !isLandscape
                            )
                        }
                    }
                }
            } else {
                // ── LİSTE MODU ────────────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    modifier = if (isTv) Modifier.width(960.dp) else Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "studio_hero_list") {
                        StudioHeroHeader(
                            detail = detail,
                            source = source,
                            accentColor = accentColor,
                            onBackClick = onBackClick,
                            isFavourite = isFavourite,
                            showFavouriteButton = showFavouriteButton,
                            onToggleFavourite = onToggleFavourite,
                            onGalleryClick = onOpenGallery,
                            height = if (isLandscape) 260.dp else 340.dp
                        )
                    }

                    if (!detail.about.isNullOrBlank()) {
                        item(key = "studio_about_list") {
                            StudioAboutSection(
                                about = detail.about,
                                onGalleryClick = onGalleryClick,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                translatedAbout = translatedAbout,
                                onTranslateClick = onAboutTranslateClick,
                                onCopyClick = onAboutCopyClick
                            )
                        }
                    }

                    item(key = "studio_controls_list") {
                        controlsHeader()
                    }

                    if (displayedWorks.isEmpty()) {
                        item(key = "studio_empty_list") {
                            KitsugiEmptyState(
                                title = "Henüz içerik yok",
                                subtitle = "Bu stüdyo için gösterilecek yapım bulunamadı.",
                                icon = Icons.Rounded.SearchOff
                            )
                        }
                    } else {
                        itemsIndexed(
                            displayedWorks,
                            key = { idx, work -> "${work.source}_${work.mediaId}_l$idx" }
                        ) { index, work ->
                            KitsugiRankingMediaCard(
                                result = work.toSearchResult(),
                                rankIndex = index + 1,
                                onClick = { onMediaClick(work.mediaId, work.mediaType, work.source) },
                                titleLanguage = titleLanguage
                            )
                        }
                    }
                }
            }

            // ── Kaydırınca beliren üst şerit (floating header) ────────────────
            AnimatedVisibility(
                visible = showFloatingHeader,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = (if (isTv) Modifier.width(960.dp) else Modifier.fillMaxWidth())
                        .height(64.dp)
                        .background(KitsugiColors.Surface.copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = detail.name,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onOpenGallery != null) {
                        IconButton(onClick = onOpenGallery) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = "Galeri",
                                tint = accentColor
                            )
                        }
                    }
                    if (showFavouriteButton) {
                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFavourite) "Favoriden Çıkar" else "Favori Yap",
                                tint = if (isFavourite) accentColor else KitsugiColors.TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (hasActiveFilter) accentColor.copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "Filtre ve Sıralama",
                            tint = if (hasActiveFilter) accentColor else KitsugiColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = {
                        scope.launch {
                            if (isGridView) {
                                val gridIndex = gridState.firstVisibleItemIndex
                                val gridOffset = gridState.firstVisibleItemScrollOffset
                                val listIndex = if (gridIndex == 0) 0 else gridIndex + 1
                                isGridView = false
                                kotlinx.coroutines.delay(10)
                                listState.scrollToItem(listIndex, gridOffset)
                            } else {
                                val listIndex = listState.firstVisibleItemIndex
                                val listOffset = listState.firstVisibleItemScrollOffset
                                val gridIndex = if (listIndex <= 1) 0 else listIndex - 1
                                isGridView = true
                                kotlinx.coroutines.delay(10)
                                gridState.scrollToItem(gridIndex, listOffset)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ListAlt else Icons.Rounded.GridView,
                            contentDescription = if (isGridView) "Liste" else "Grid",
                            tint = accentColor
                        )
                    }
                }
            }

            // ── Aşağı kaydırınca çıkan yukarı kaydırma FAB'ı ──────────────────
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor)
                        .tvClickable(shape = RoundedCornerShape(16.dp)) {
                            scope.launch {
                                if (isGridView) gridState.animateScrollToItem(0)
                                else listState.animateScrollToItem(0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Yukarı Git",
                        tint = KitsugiColors.Background,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        KitsugiStudioFilterBottomSheet(
            initialTypeId = selectedTypeId,
            initialSortOption = selectedSortOption,
            onDismissRequest = { showFilterSheet = false },
            onApply = { typeId, sortOption ->
                selectedTypeId = typeId
                selectedSortOption = sortOption
            },
            onReset = {
                selectedTypeId = "ALL"
                selectedSortOption = StudioSortOption.DEFAULT
            }
        )
    }
}

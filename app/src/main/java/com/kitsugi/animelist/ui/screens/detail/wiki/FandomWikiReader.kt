package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomFullPage
import com.kitsugi.animelist.data.fandom.FandomImage
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomSectionRef
import com.kitsugi.animelist.data.fandom.FandomSpoilerRules
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.DisableDialogWindowForceDark
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlinx.coroutines.flow.collect

/**
 * Uygulama içi wiki okuyucusunun arayüzle sözleşmesi. İki farklı VM (medya & karakter)
 * aynı arayüzü besler: bu sınıf o paydasızdır.
 */
data class FandomReaderRenderState(
    val wikiId: String,
    val page: FandomFullPage,
    val sections: Map<Int, FandomSectionContent>,
    val related: List<FandomRelatedPage>,
    val loadingSections: Set<Int> = emptySet(),
    val failedSections: Set<Int> = emptySet(),
    val translations: Map<String, String> = emptyMap(),
    val translatingKeys: Set<String> = emptySet(),
    val originalKeys: Set<String> = emptySet(),
    val preferredTranslator: String = "DEFAULT"
)

/**
 * Tam ekran uygulama içi wiki okuyucu. Tarayıcıya ATLAMAZ; aynı wiki sekmesindeki
 * kartlarla aynı görsel dili kullanır. İçindekiler satırları içeriği kendi altında açar;
 * çeviri/orijinal geçişleri, galeri, sekmeler ve ileri-geri sayfa geçmişi desteklenir.
 *
 * KRİTİK (çökme düzeltmesi): Okuyucu KENDİ penceresinde (tam ekran Dialog) çizilir.
 *
 *  1) Detay sayfası sekme içeriği portre düzende `DetailPageScaffold` içindeki
 *     `HorizontalPager` tarafından `constraints.copy(maxHeight = Constraints.Infinity)`
 *     ile ölçülür (sekme yüksekliğini doğal hâliyle ölçebilmek için). Okuyucu bu
 *     öğenin İÇİNE gömülü çizildiğinde `verticalScroll` sonsuz max height alıyor ve
 *     `IllegalStateException: Vertically scrollable component was measured with an
 *     infinity maximum height constraints` ile uygulama kapanıyordu.
 *  2) Yatay/TV düzeninde sayfa içeriği zaten bir `Box.verticalScroll` içindedir;
 *     okuyucunun kendi kaydırması onun içine iç içe girdiğinde AYNI istisna oluşuyordu.
 *
 * Dialog penceresi her koşulda sınırlı (bounded) kısıt verir; bu yüzden okuyucu hangi
 * ekrandan açılırsa açılsın (yapım / karakter / ekip / gömülü wiki kartı) güvenlidir.
 * Geri tuşu: etkin sekmenin geçmişinde geri gider; geçmiş boşsa okuyucuyu kapatır.
 */
@Composable
internal fun FandomWikiReaderSheet(
    state: FandomReaderRenderState?,
    navigation: FandomReaderBrowserState = FandomReaderBrowserState.Empty,
    accentColor: Color,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onSwitchTab: (Long) -> Unit = {},
    onCloseTab: (Long) -> Unit = {},
    onCloseOtherTabs: (Long) -> Unit = {},
    onLoadSection: (Int) -> Unit,
    onRetrySection: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
    onToggleOriginal: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Yüklenemeyen sayfanın başlığı; doluysa sonsuz yükleme yerine hata kartı gösterilir. */
    failedTitle: String? = null,
    onRetryFailed: (() -> Unit)? = null
) {
    val hasBackStack = navigation.canGoBack
    var localGalleryItems by remember { mutableStateOf(emptyList<GalleryItem>()) }
    var localGalleryIndex by remember { mutableStateOf(0) }
    val scrollPositions = remember { mutableStateMapOf<String, Int>() }
    val activeScrollKey = state?.let { page ->
        navigation.activeTabId?.let { tabId -> "$tabId:${page.wikiId}:${page.page.displayTitle}" }
    }
    val displayTitle = state?.page?.displayTitle
        ?: failedTitle
        ?: navigation.activeTab?.location?.title

    Dialog(
        onDismissRequest = { if (hasBackStack) onBack() else onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        DisableDialogWindowForceDark()
        Surface(
            modifier = modifier.fillMaxSize(),
            color = KitsugiColors.Background,
            contentColor = KitsugiColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                ReaderTopBar(
                    title = displayTitle,
                    accentColor = accentColor,
                    navigation = navigation,
                    onBack = onBack,
                    onForward = onForward,
                    onNewTab = onNewTab,
                    onClose = onClose,
                    onOpenInBrowser = onOpenInBrowser
                )
                ReaderTabStrip(
                    navigation = navigation,
                    accentColor = accentColor,
                    onSwitchTab = onSwitchTab,
                    onCloseTab = onCloseTab,
                    onCloseOtherTabs = onCloseOtherTabs
                )
                when {
                    state == null && failedTitle != null -> ReaderFailed(
                        title = failedTitle,
                        accentColor = accentColor,
                        onRetry = onRetryFailed
                    )
                    state == null -> ReaderLoading(accentColor)
                    else -> ReaderContent(
                        state = state,
                        accentColor = accentColor,
                        onLoadSection = onLoadSection,
                        onRetrySection = onRetrySection,
                        onOpenLink = onOpenLink,
                        onToggleOriginal = onToggleOriginal,
                        scrollKey = activeScrollKey.orEmpty(),
                        initialScrollPosition = activeScrollKey?.let { scrollPositions[it] } ?: 0,
                        onScrollPositionChanged = { key, position ->
                            scrollPositions[key] = position
                            if (scrollPositions.size > 120) {
                                scrollPositions.keys.firstOrNull { it != key }?.let(scrollPositions::remove)
                            }
                        },
                        onOpenGallery = { items, index ->
                            if (items.isNotEmpty()) {
                                localGalleryItems = items
                                localGalleryIndex = index.coerceIn(0, items.lastIndex)
                            }
                        }
                    )
                }
            }
        }
    }

    // Wiki sekmelerinin hepsinde galeri açma callback'i olmayabilir (ör. TV / eklenti
    // detayları). Böyle durumlarda okuyucunun kendi galeri penceresi devreye girer.
    if (localGalleryItems.isNotEmpty()) {
        KitsugiImageGalleryDialog(
            galleryItems = localGalleryItems,
            initialIndex = localGalleryIndex,
            title = displayTitle ?: "Wiki",
            onDismiss = { localGalleryItems = emptyList() }
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String?,
    accentColor: Color,
    navigation: FandomReaderBrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Surface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = navigation.canGoBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.fandom_reader_back),
                tint = if (navigation.canGoBack) accentColor else KitsugiColors.TextMuted.copy(alpha = 0.45f)
            )
        }
        IconButton(onClick = onForward, enabled = navigation.canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.fandom_reader_forward),
                tint = if (navigation.canGoForward) accentColor else KitsugiColors.TextMuted.copy(alpha = 0.45f)
            )
        }
        Text(
            text = title ?: stringResource(R.string.fandom_reader_title),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        IconButton(onClick = onOpenInBrowser, enabled = title != null) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = stringResource(R.string.fandom_reader_open_browser),
                tint = accentColor
            )
        }
        IconButton(onClick = onNewTab, enabled = navigation.canOpenNewTab) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.fandom_reader_new_tab),
                tint = accentColor
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.fandom_reader_close),
                tint = KitsugiColors.TextMuted
            )
        }
    }
}

/** Sekmelerin en dar hâli; bunun altına sığılmazsa şerit yatay kaydırılır (Chrome davranışı). */
private val READER_TAB_MIN_WIDTH = 88.dp

/** Az sekme varken bir sekmenin genişleyebileceği üst sınır; artan alan boş kalır. */
private val READER_TAB_MAX_WIDTH = 220.dp
private val READER_TAB_SPACING = 6.dp
private val READER_TAB_HEIGHT = 38.dp

/**
 * Chrome/Brave tarzı sekme şeridi:
 *  - Sekmeler yan yana dizilir ve kullanılabilir genişliği EŞİT paylaşır; sekme sayısı
 *    arttıkça hepsi birlikte daralır, azaldıkça yeniden genişler.
 *  - Eşit pay [READER_TAB_MIN_WIDTH] altına düşerse genişlik orada sabitlenir ve şerit
 *    yatay kaydırılabilir olur; yeni/açılan sekme otomatik görünür alana kaydırılır.
 *  - Aktif sekme sayfa arkaplanıyla aynı renkte ve üst köşeleri yuvarlıdır (içerikle
 *    bütünleşir), pasif sekmeler şerit üzerinde düz durur. Dar sekmelerde kapat (×)
 *    yalnızca aktif sekmede gösterilir — Chrome'un dar sekmede yaptığı gibi.
 */
@Composable
private fun ReaderTabStrip(
    navigation: FandomReaderBrowserState,
    accentColor: Color,
    onSwitchTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseOtherTabs: (Long) -> Unit = {}
) {
    if (navigation.tabs.isEmpty()) return
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Surface.copy(alpha = 0.72f))
            .padding(start = 10.dp, end = 10.dp, top = 6.dp)
    ) {
        val count = navigation.tabs.size
        val equalWidth = (maxWidth - READER_TAB_SPACING * (count - 1)) / count
        if (equalWidth >= READER_TAB_MIN_WIDTH) {
            // Hepsi ekrana sığıyor: eşit paylaş (gerekirse üst sınırda bırak).
            Row(horizontalArrangement = Arrangement.spacedBy(READER_TAB_SPACING)) {
                navigation.tabs.forEach { tab ->
                    ReaderTabChip(
                        title = tab.location.title,
                        selected = tab.id == navigation.activeTabId,
                        accentColor = accentColor,
                        showClose = tab.id == navigation.activeTabId || equalWidth >= 132.dp,
                        onClick = { onSwitchTab(tab.id) },
                        onClose = { onCloseTab(tab.id) },
                        onLongClick = { onCloseOtherTabs(tab.id) },
                        modifier = if (equalWidth > READER_TAB_MAX_WIDTH) {
                            Modifier.width(READER_TAB_MAX_WIDTH)
                        } else {
                            Modifier.weight(1f)
                        }
                    )
                }
            }
        } else {
            // Sığmıyor: sabit dar genişlik + yatay kaydırma; aktif sekme görünür tutulur.
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val activeIndex = navigation.tabs.indexOfFirst { it.id == navigation.activeTabId }
            LaunchedEffect(navigation.activeTabId, navigation.tabs.size) {
                if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
            }
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(READER_TAB_SPACING)
            ) {
                items(navigation.tabs, key = { it.id }) { tab ->
                    ReaderTabChip(
                        title = tab.location.title,
                        selected = tab.id == navigation.activeTabId,
                        accentColor = accentColor,
                        showClose = tab.id == navigation.activeTabId,
                        onClick = { onSwitchTab(tab.id) },
                        onClose = { onCloseTab(tab.id) },
                        onLongClick = { onCloseOtherTabs(tab.id) },
                        modifier = Modifier.width(READER_TAB_MIN_WIDTH)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTabChip(
    title: String,
    selected: Boolean,
    accentColor: Color,
    showClose: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    Row(
        modifier = modifier
            .height(READER_TAB_HEIGHT)
            .clip(shape)
            .background(if (selected) KitsugiColors.Background else KitsugiColors.SurfaceStrong.copy(alpha = 0.55f))
            .then(
                if (selected) {
                    Modifier.border(1.dp, accentColor.copy(alpha = 0.45f), shape)
                } else {
                    Modifier
                }
            )
            .tvClickable(shape = shape, onLongClick = onLongClick) { onClick() }
            .padding(start = 10.dp, end = if (showClose) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showClose) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.fandom_reader_close_tab, title),
                    tint = if (selected) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** Okuyucu sayfası yüklenemedi: ölü sonsuz spinner yerine başlık + tekrar dene. */
@Composable
private fun ReaderFailed(title: String, accentColor: Color, onRetry: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.fandom_reader_failed),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.action_retry_label),
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderLoading(accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.fandom_guide_more_loading), color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReaderContent(
    state: FandomReaderRenderState,
    accentColor: Color,
    onLoadSection: (Int) -> Unit,
    onRetrySection: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    scrollKey: String,
    initialScrollPosition: Int,
    onScrollPositionChanged: (String, Int) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val scrollState = remember(scrollKey) { ScrollState(initialScrollPosition) }
    LaunchedEffect(scrollKey, scrollState) {
        snapshotFlow { scrollState.value }.collect { position ->
            onScrollPositionChanged(scrollKey, position)
        }
    }
    val expandedSections = remember(scrollKey) { mutableStateListOf<Int>() }
    val galleryItems = remember(state.wikiId, state.page, state.sections, state.related) {
        buildReaderGalleryItems(state)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReaderHeaderCard(
            state = state,
            galleryItems = galleryItems,
            accentColor = accentColor,
            onToggleOriginal = onToggleOriginal,
            onOpenGallery = onOpenGallery
        )

        // Her İçindekiler satırı kendi içeriğini hemen altında açar; sayfanın en
        // altına ayrı kartlar yığılmaz. Birden fazla bölüm aynı anda açık kalabilir.
        if (state.page.sections.isNotEmpty()) {
            ReaderContentsCard(
                state = state,
                accentColor = accentColor,
                expandedSections = expandedSections,
                onToggle = { index ->
                    if (index in expandedSections) {
                        expandedSections.remove(index)
                    } else {
                        expandedSections.add(index)
                        when {
                            index in state.failedSections -> onRetrySection(index)
                            index !in state.sections -> onLoadSection(index)
                        }
                    }
                },
                onRetrySection = onRetrySection,
                onOpenLink = onOpenLink,
                onToggleOriginal = onToggleOriginal,
                galleryItems = galleryItems,
                onOpenGallery = onOpenGallery
            )
        }

        if (state.related.isNotEmpty()) {
            ReaderRelatedCard(
                wikiId = state.wikiId,
                related = state.related,
                accentColor = accentColor,
                galleryItems = galleryItems,
                onOpenGallery = onOpenGallery,
                onOpenPage = onOpenLink
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReaderHeaderCard(
    state: FandomReaderRenderState,
    galleryItems: List<GalleryItem>,
    accentColor: Color,
    onToggleOriginal: (String) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    val location = FandomReaderLocation(state.wikiId, state.page.displayTitle)
    val descriptionKey = FandomReaderTranslationKeys.description(location)
    val translatedDescription = state.translations[descriptionKey]
    val showingOriginal = descriptionKey in state.originalKeys
    val description = state.page.description.orEmpty()
    val displayDescription = translatedDescription?.takeUnless { showingOriginal } ?: description

    ReaderCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            state.page.thumbnailUrl?.let { url ->
                val imageIndex = galleryItems.indexOfFirst { it.url == url }.coerceAtLeast(0)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(false).build(),
                    contentDescription = state.page.displayTitle,
                    modifier = Modifier
                        .size(width = 96.dp, height = 136.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .tvClickable(shape = RoundedCornerShape(14.dp)) {
                            if (galleryItems.isNotEmpty()) onOpenGallery(galleryItems, imageIndex)
                        },
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize().background(KitsugiColors.SurfaceStrong), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.BrokenImage, null, tint = KitsugiColors.TextMuted)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.page.displayTitle,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayDescription,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        WikiTranslateButton(
                            text = description,
                            preferredTranslator = state.preferredTranslator,
                            accentColor = accentColor,
                            isLoading = descriptionKey in state.translatingKeys,
                            hasTranslation = !translatedDescription.isNullOrBlank(),
                            showingOriginal = showingOriginal,
                            onToggleOriginal = { onToggleOriginal(descriptionKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderContentsCard(
    state: FandomReaderRenderState,
    accentColor: Color,
    expandedSections: List<Int>,
    onToggle: (Int) -> Unit,
    onRetrySection: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    ReaderCard {
        Text(
            text = stringResource(R.string.fandom_reader_contents),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.page.sections.forEach { section ->
                val isExpanded = section.index in expandedSections
                val isLoaded = section.index in state.sections
                val isLoading = section.index in state.loadingSections
                val isFailed = section.index in state.failedSections
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isExpanded) accentColor.copy(alpha = 0.08f) else Color.Transparent)
                            .tvClickable(shape = RoundedCornerShape(10.dp)) { onToggle(section.index) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = localizedSectionTitle(section.title),
                            color = if (isExpanded || isLoaded) accentColor else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isExpanded || isLoaded) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        when {
                            isLoading -> CircularProgressIndicator(Modifier.size(15.dp), color = accentColor, strokeWidth = 2.dp)
                            isFailed -> Text("!", color = KitsugiColors.AccentOrange, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (isExpanded) accentColor else KitsugiColors.TextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    AnimatedVisibility(visible = isExpanded) {
                        ReaderSectionContent(
                            section = section,
                            content = state.sections[section.index],
                            isLoading = isLoading,
                            isFailed = isFailed,
                            translationKey = FandomReaderTranslationKeys.section(
                                FandomReaderLocation(state.wikiId, state.page.displayTitle), section.index
                            ),
                            translatedText = state.translations[FandomReaderTranslationKeys.section(
                                FandomReaderLocation(state.wikiId, state.page.displayTitle), section.index
                            )],
                            isTranslating = FandomReaderTranslationKeys.section(
                                FandomReaderLocation(state.wikiId, state.page.displayTitle), section.index
                            ) in state.translatingKeys,
                            showingOriginal = FandomReaderTranslationKeys.section(
                                FandomReaderLocation(state.wikiId, state.page.displayTitle), section.index
                            ) in state.originalKeys,
                            preferredTranslator = state.preferredTranslator,
                            accentColor = accentColor,
                            onRetry = { onRetrySection(section.index) },
                            onOpenLink = onOpenLink,
                            onToggleOriginal = onToggleOriginal,
                            galleryItems = galleryItems,
                            onOpenGallery = onOpenGallery
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSectionContent(
    section: FandomSectionRef,
    content: FandomSectionContent?,
    isLoading: Boolean,
    isFailed: Boolean,
    translationKey: String,
    translatedText: String?,
    isTranslating: Boolean,
    showingOriginal: Boolean,
    preferredTranslator: String,
    accentColor: Color,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    onToggleOriginal: (String) -> Unit,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isLoading || content == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(15.dp), color = accentColor, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.fandom_guide_more_loading),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            isFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.fandom_section_error),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry_label), color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                val sourceText = content.plainText()
                val showTranslated = !translatedText.isNullOrBlank() && !showingOriginal
                if (sourceText.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        WikiTranslateButton(
                            text = sourceText,
                            preferredTranslator = preferredTranslator,
                            accentColor = accentColor,
                            isLoading = isTranslating,
                            hasTranslation = !translatedText.isNullOrBlank(),
                            showingOriginal = showingOriginal,
                            onToggleOriginal = { onToggleOriginal(translationKey) }
                        )
                    }
                }
                if (showTranslated) {
                    Text(
                        text = translatedText.orEmpty(),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Çeviri düz metindir; görseller ayrı blok olarak kalır ve galeriye
                    // erişim çevrilmiş görünümde de aynı şekilde çalışır.
                    content.blocks.filter { it.kind == FandomBlockKind.IMAGE }
                        .forEach { block -> ReaderBlock(block, accentColor, onOpenLink, galleryItems, onOpenGallery) }
                } else {
                    content.blocks.forEach { block ->
                        ReaderBlock(block, accentColor, onOpenLink, galleryItems, onOpenGallery)
                    }
                }
                if (content.links.isNotEmpty() && !FandomSpoilerRules.isHiddenSection(section.title)) {
                    ReaderLinkChips(links = content.links.take(12), accentColor = accentColor, onOpenLink = onOpenLink)
                }
            }
        }
    }
}

@Composable
private fun ReaderBlock(
    block: com.kitsugi.animelist.data.fandom.FandomTextBlock,
    accentColor: Color,
    onOpenLink: (String) -> Unit,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit
) {
    when (block.kind) {
        FandomBlockKind.HEADING -> Text(
            text = localizedSectionTitle(block.text),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        FandomBlockKind.PARAGRAPH -> Text(
            text = block.text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        FandomBlockKind.BULLET -> Row(Modifier.fillMaxWidth()) {
            Text("•", color = KitsugiColors.TextSecondary, modifier = Modifier.padding(end = 8.dp))
            Text(block.text, color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        FandomBlockKind.QUOTE -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(block.text, color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
        FandomBlockKind.IMAGE -> {
            val image = block.image
            if (image != null && image.url.isNotBlank()) {
                val imageIndex = galleryItems.indexOfFirst { it.url == image.url }.coerceAtLeast(0)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = image.url,
                        contentDescription = image.caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                if (galleryItems.isNotEmpty()) onOpenGallery(galleryItems, imageIndex)
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                        Text(
                            text = caption,
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun buildReaderGalleryItems(state: FandomReaderRenderState): List<GalleryItem> = buildList {
    state.page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
        add(GalleryItem(url, READER_GALLERY_SOURCE, GalleryCategory.OTHER, state.page.displayTitle))
    }
    state.sections.toSortedMap().values.forEach { content ->
        content.blocks.asSequence()
            .filter { it.kind == FandomBlockKind.IMAGE }
            .mapNotNull { it.image }
            .filter { it.url.isNotBlank() }
            .forEach { image -> add(image.toReaderGalleryItem()) }
    }
    state.related.forEach { page ->
        page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            add(GalleryItem(url, READER_GALLERY_SOURCE, GalleryCategory.OTHER, page.title))
        }
    }
}.distinctBy { it.url }

private fun FandomImage.toReaderGalleryItem(): GalleryItem = GalleryItem(
    url = url,
    source = READER_GALLERY_SOURCE,
    category = GalleryCategory.OTHER,
    description = caption,
    details = buildMap {
        fileTitle?.takeIf { it.isNotBlank() }?.let { put("File", it) }
        artist?.takeIf { it.isNotBlank() }?.let { put("Artist", it) }
        license?.takeIf { it.isNotBlank() }?.let { put("License", it) }
    }
)

private const val READER_GALLERY_SOURCE = "Fandom"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderLinkChips(links: List<String>, accentColor: Color, onOpenLink: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        links.forEach { title ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.10f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .tvClickable(shape = RoundedCornerShape(10.dp)) { onOpenLink(title) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(title, color = accentColor, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReaderRelatedCard(
    wikiId: String,
    related: List<FandomRelatedPage>,
    accentColor: Color,
    galleryItems: List<GalleryItem>,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    onOpenPage: (String) -> Unit
) {
    ReaderCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.fandom_related_pages_title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.fandom_related_pages_count, related.size),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(related, key = { it.title }) { page ->
                WikiRelatedPageCard(
                    page = page,
                    accentColor = accentColor,
                    onClick = { onOpenPage(page.title) },
                    onImageClick = { url ->
                        val index = galleryItems.indexOfFirst { it.url == url }
                        if (index >= 0) onOpenGallery(galleryItems, index)
                    }
                )
            }
        }
    }
}

/** Sekmelerde ve okuyucuda yeniden kullanılan "İlgili wiki sayfası" kartı. */
@Composable
internal fun WikiRelatedPageCard(
    page: FandomRelatedPage,
    accentColor: Color,
    onClick: () -> Unit,
    onImageClick: ((String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceStrong)
            .tvClickable(shape = RoundedCornerShape(14.dp)) { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            val url = page.thumbnailUrl
            if (url != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(false).build(),
                    contentDescription = page.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (onImageClick != null) {
                                Modifier.tvClickable(shape = RoundedCornerShape(10.dp)) { onImageClick(url) }
                            } else {
                                Modifier
                            }
                        ),
                    loading = {
                        CircularProgressIndicator(Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                    },
                    error = { PagePlaceholder(page.title, accentColor) }
                )
            } else {
                PagePlaceholder(page.title, accentColor)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = page.title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        page.snippet?.let { snip ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = snip,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .tvClickable(shape = RoundedCornerShape(10.dp)) { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.fandom_open_page),
                color = accentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PagePlaceholder(title: String, accentColor: Color) {
    val letter = title.trim().firstOrNull()?.uppercase() ?: "?"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, color = accentColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReaderCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun localizedSectionTitle(raw: String): String {
    val resource = FandomSectionTitles.resId(raw)
    return if (resource != null) androidx.compose.ui.res.stringResource(resource) else raw
}

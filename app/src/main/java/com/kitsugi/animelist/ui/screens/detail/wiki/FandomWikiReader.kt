package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomSectionRef
import com.kitsugi.animelist.data.fandom.FandomSpoilerRules
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.ui.components.DisableDialogWindowForceDark
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Uygulama içi wiki okuyucusunun arayüzle sözleşmesi. İki farklı VM (medya & karakter)
 * aynı arayüzü besler: bu sınıf o paydasızdır.
 */
data class FandomReaderRenderState(
    val wikiId: String,
    val page: FandomFullPage,
    val sections: Map<String, FandomSectionContent>,
    val related: List<FandomRelatedPage>,
    val loadingSection: String? = null,
    val failedSection: String? = null,
    val hasBackStack: Boolean = false
)

/**
 * Tam ekran uygulama içi wiki okuyucu. Tarayıcıya ATLAMAZ, aynı wiki sekmesindeki
 * kartlarla aynı renkler/şekiller kullanılır; üst çubuk + kaydırılabilir içerik +
 * İçindekiler + İlgili sayfalar + bağlantı içinden başka sayfa açma + geri yığını.
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
 * Geri tuşu: yığın varsa önceki wiki sayfasına döner, yığın boşsa okuyucuyu kapatır.
 */
@Composable
internal fun FandomWikiReaderSheet(
    state: FandomReaderRenderState?,
    accentColor: Color,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onLoadSection: (String) -> Unit,
    onRetrySection: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
    onTranslateSummary: (() -> Unit)? = null,
    summaryIsTranslating: Boolean = false,
    modifier: Modifier = Modifier,
    /** Yüklenemeyen sayfanın başlığı; doluysa sonsuz yükleme yerine hata kartı gösterilir. */
    failedTitle: String? = null,
    onRetryFailed: (() -> Unit)? = null
) {
    val hasBackStack = state?.hasBackStack == true

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
                    title = state?.page?.displayTitle ?: failedTitle,
                    accentColor = accentColor,
                    hasBackStack = hasBackStack,
                    onBack = onBack,
                    onClose = onClose,
                    onOpenInBrowser = onOpenInBrowser
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
                        onTranslateSummary = onTranslateSummary,
                        summaryIsTranslating = summaryIsTranslating
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String?,
    accentColor: Color,
    hasBackStack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = if (hasBackStack) onBack else onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(if (hasBackStack) R.string.fandom_reader_back else R.string.fandom_reader_close),
                tint = accentColor
            )
        }
        Text(
            text = title ?: "Wiki",
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        IconButton(onClick = onOpenInBrowser) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = stringResource(R.string.fandom_reader_open_browser),
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
    onLoadSection: (String) -> Unit,
    onRetrySection: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onTranslateSummary: (() -> Unit)?,
    summaryIsTranslating: Boolean
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Kapak + başlık/özet
        ReaderHeaderCard(state = state, accentColor = accentColor,
            onTranslateSummary = onTranslateSummary, summaryIsTranslating = summaryIsTranslating)

        // İçindekiler
        if (state.page.sections.isNotEmpty()) {
            ReaderContentsCard(
                sections = state.page.sections,
                loaded = state.sections.keys,
                loading = state.loadingSection,
                failed = state.failedSection,
                accentColor = accentColor,
                onToggle = { idx ->
                    if (!state.sections.containsKey(idx)) onLoadSection(idx)
                }
            )
        }

        // Yüklü bölümler
        state.page.sections
            .filter { it.index in state.sections.keys || state.failedSection == it.index || state.loadingSection == it.index }
            .sortedWith(compareBy({ it.index.toIntOrNull() == null }, { it.index.toIntOrNull() ?: 0 }, { it.index }))
            .forEach { sec ->
                ReaderSectionCard(
                    section = sec,
                    content = state.sections[sec.index],
                    isLoading = state.loadingSection == sec.index,
                    isFailed = state.failedSection == sec.index,
                    accentColor = accentColor,
                    onRetry = { onRetrySection(sec.index) },
                    onOpenLink = onOpenLink
                )
            }

        // İlgili wiki sayfaları
        if (state.related.isNotEmpty()) {
            ReaderRelatedCard(
                wikiId = state.wikiId,
                related = state.related,
                accentColor = accentColor,
                onOpenPage = onOpenLink
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReaderHeaderCard(
    state: FandomReaderRenderState,
    accentColor: Color,
    onTranslateSummary: (() -> Unit)?,
    summaryIsTranslating: Boolean
) {
    ReaderCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            state.page.thumbnailUrl?.let { url ->
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(false).build(),
                    contentDescription = state.page.displayTitle,
                    modifier = Modifier
                        .size(width = 96.dp, height = 136.dp)
                        .clip(RoundedCornerShape(14.dp)),
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
                state.page.description?.let { desc ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = desc,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (onTranslateSummary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onTranslateSummary) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        val label = if (summaryIsTranslating) "Çevriliyor…" else "Özeti çevir"
                        Text(label, color = accentColor, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderContentsCard(
    sections: List<FandomSectionRef>,
    loaded: Set<String>,
    loading: String?,
    failed: String?,
    accentColor: Color,
    onToggle: (String) -> Unit
) {
    ReaderCard {
        Text(
            text = stringResource(R.string.fandom_reader_contents),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            sections.forEach { sec ->
                val isLoaded = sec.index in loaded
                val isLoading = loading == sec.index
                val isFailed = failed == sec.index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggle(sec.index) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = sec.title,
                        color = if (isLoaded) accentColor else KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLoaded) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    when {
                        isLoading -> CircularProgressIndicator(Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
                        isFailed -> Text("!", color = KitsugiColors.AccentOrange, fontWeight = FontWeight.Bold)
                        isLoaded -> Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSectionCard(
    section: FandomSectionRef,
    content: FandomSectionContent?,
    isLoading: Boolean,
    isFailed: Boolean,
    accentColor: Color,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    ReaderCard {
        Text(
            text = localizedSectionTitle(section.title),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        when {
            isLoading || content == null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = accentColor, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.fandom_guide_more_loading), color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            isFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.fandom_section_error),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry_label), color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                content.blocks.forEach { block -> ReaderBlock(block, accentColor, onOpenLink) }
                // Bölümdeki ilgili bağlantılar (metin içinde atıf geçen ama öne çıkarmak
                // istediğimiz sayfalar): yatay çipler olarak.
                if (content.links.isNotEmpty() && !FandomSpoilerRules.isHiddenSection(section.title)) {
                    Spacer(Modifier.height(8.dp))
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
    onOpenLink: (String) -> Unit
) {
    when (block.kind) {
        FandomBlockKind.HEADING -> Text(
            text = localizedSectionTitle(block.text),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        FandomBlockKind.PARAGRAPH -> WikiLinkedText(
            block = block,
            accentColor = accentColor,
            onOpenLink = onOpenLink,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        FandomBlockKind.BULLET -> Row(Modifier.fillMaxWidth()) {
            Text("•", color = KitsugiColors.TextSecondary, modifier = Modifier.padding(end = 8.dp))
            WikiLinkedText(
                block = block,
                accentColor = accentColor,
                onOpenLink = onOpenLink,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        FandomBlockKind.QUOTE -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KitsugiColors.SurfaceSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            WikiLinkedText(
                block = block,
                accentColor = accentColor,
                onOpenLink = onOpenLink,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
        FandomBlockKind.IMAGE -> {
            val img = block.image
            if (img != null && img.url.isNotBlank()) {
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
                        model = img.url,
                        contentDescription = img.caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    img.caption?.takeIf { it.isNotBlank() }?.let { caption ->
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
                WikiRelatedPageCard(page = page, accentColor = accentColor, onClick = { onOpenPage(page.title) })
            }
        }
    }
}

/** Sekmelerde ve okuyucuda yeniden kullanılan "İlgili wiki sayfası" kartı. */
@Composable
internal fun WikiRelatedPageCard(
    page: FandomRelatedPage,
    accentColor: Color,
    onClick: () -> Unit
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
                    modifier = Modifier.fillMaxSize(),
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

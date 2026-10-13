package com.kitsugi.animelist.ui.screens.detail.wiki

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import com.kitsugi.animelist.data.fandom.FandomRelatedPage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import com.kitsugi.animelist.data.fandom.FandomArticlePage
import com.kitsugi.animelist.data.fandom.FandomBlockKind
import com.kitsugi.animelist.data.fandom.FandomInfoboxField
import com.kitsugi.animelist.data.fandom.FandomSectionContent
import com.kitsugi.animelist.data.fandom.FandomSectionRef
import com.kitsugi.animelist.data.fandom.FandomTextBlock
import com.kitsugi.animelist.data.fandom.FandomGuideGroup
import com.kitsugi.animelist.data.fandom.FandomGuideItem
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.data.fandom.FandomConfidence
import com.kitsugi.animelist.data.fandom.FandomWikiRef
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable

/** Evren rehberi grubunda katlanmışken gösterilen çip sayısı. */
private const val GUIDE_COLLAPSED_CHIPS = 8

/**
 * Yapım detayındaki "Wiki" sekmesi (Faz 2).
 *
 * İçerik: wiki başlığı + tarayıcıda aç, kısa seri özeti (çevrilebilir), wiki içi arama
 * ve "evren rehberi" çipleri (organizasyonlar / yetenekler / mekânlar / terimler).
 * Çiplerdeki isimler orijinal kalır; çipe dokununca alttan açılan kartta Türkçe kısa
 * açıklama ve "Tamamını oku" bağlantısı gösterilir.
 */
@Composable
internal fun MediaWikiTabSlot(
    viewModel: FandomMediaWikiViewModel,
    accentColor: Color,
    onOpenGallery: ((List<GalleryItem>, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    val articleState by viewModel.article.collectAsState()
    val articleSections by viewModel.articleSections.collectAsState()
    val guideState by viewModel.guide.collectAsState()
    val guideMore by viewModel.guideMore.collectAsState()
    val summaryState by viewModel.summary.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedState by viewModel.selected.collectAsState()
    val selectedSections by viewModel.selectedSections.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val translatingKeys by viewModel.translating.collectAsState()
    val preferredTranslator by viewModel.preferredTranslator.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    val originalKeys by viewModel.originalKeys.collectAsState()
    val readerState by viewModel.reader.collectAsState()
    val relatedPages by viewModel.relatedPages.collectAsState()
    val lastRejected by viewModel.lastRejected.collectAsState()
    val readerRender = (readerState as? FandomMediaWikiViewModel.ReaderState.Ready)?.let { r ->
        FandomReaderRenderState(
            wikiId = (uiState as? FandomMediaWikiViewModel.UiState.Ready)?.ref?.wikiId ?: "",
            page = r.page,
            sections = r.sections,
            related = r.related,
            loadingSection = r.loadingSection,
            failedSection = r.failedSection,
            hasBackStack = true
        )
    }
    val ready = uiState as? FandomMediaWikiViewModel.UiState.Ready

    BackHandler(enabled = readerState != FandomMediaWikiViewModel.ReaderState.Closed) {
        viewModel.readerBack()
    }

    LaunchedEffect(uiState) {
        if (uiState is FandomMediaWikiViewModel.UiState.Ready) viewModel.ensureContentLoaded()
    }

    MediaWikiTabContent(
        uiState = uiState,
        articleState = articleState,
        articleSections = articleSections,
        guideState = guideState,
        guideMore = guideMore,
        summaryState = summaryState,
        searchState = searchState,
        searchQuery = searchQuery,
        selectedState = selectedState,
        selectedSections = selectedSections,
        translations = translations,
        translatingKeys = translatingKeys,
        spoilerGuard = prefs.spoilerGuard,
        accentColor = accentColor,
        onRetry = viewModel::retry,
        onRetryArticle = viewModel::retryArticle,
        onLoadArticleSection = viewModel::loadArticleSection,
        onConfirm = viewModel::confirmMatch,
        onRetryGuide = viewModel::retryGuide,
        onLoadMoreGuide = viewModel::loadMoreGuide,
        onSearchChange = viewModel::onSearchQueryChange,
        onClearSearch = viewModel::clearSearch,
        onOpenItem = { viewModel.openReader(it) },
        onLoadSelectedSection = viewModel::loadSelectedSection,
        onCloseItem = viewModel::closeItem,
        onRetryItem = viewModel::retryItem,
        onReportWrong = viewModel::reportWrongMatch,
        rejectedName = lastRejected?.let { it.siteName ?: it.wikiId },
        onUndoReject = {
            viewModel.undoLastReject()
            Toast.makeText(context, context.getString(R.string.fandom_reject_undo_done), Toast.LENGTH_SHORT).show()
        },
        onDismissRejectedUndo = viewModel::dismissLastRejected,
        onOpenGallery = onOpenGallery,
        preferredTranslator = preferredTranslator,
        originalKeys = originalKeys,
        onToggleOriginal = viewModel::toggleOriginal,
        relatedPages = relatedPages,
        onOpenReaderHome = { viewModel.openReaderMainPage() },
        modifier = modifier
    )

    if (ready != null && readerState != FandomMediaWikiViewModel.ReaderState.Closed) {
        val wikiId = ready.ref.wikiId
        val context = LocalContext.current
        FandomWikiReaderSheet(
            state = readerRender,
            accentColor = accentColor,
            onClose = viewModel::closeReader,
            onBack = { viewModel.readerBack() },
            onLoadSection = viewModel::loadReaderSection,
            onRetrySection = viewModel::retryReaderSection,
            onOpenLink = viewModel::openReaderLink,
            onOpenInBrowser = {
                val title = (readerState as? FandomMediaWikiViewModel.ReaderState.Ready)?.page?.displayTitle
                    ?: return@FandomWikiReaderSheet
                openExternal(context, FandomUrls.pageUrl(wikiId, title))
            },
            onTranslateSummary = null
        )
    }
}

@Composable
internal fun MediaWikiTabContent(
    uiState: FandomMediaWikiViewModel.UiState,
    articleState: FandomMediaWikiViewModel.ArticleState,
    articleSections: Map<Int, FandomCharacterWikiViewModel.SectionState>,
    guideState: FandomMediaWikiViewModel.GuideState,
    guideMore: FandomMediaWikiViewModel.GuideMore,
    summaryState: FandomMediaWikiViewModel.BlurbState,
    searchState: FandomMediaWikiViewModel.SearchState,
    searchQuery: String,
    selectedState: FandomMediaWikiViewModel.SelectedState,
    selectedSections: Map<Int, FandomMediaWikiViewModel.ArticleSectionState>,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    spoilerGuard: Boolean = false,
    accentColor: Color,
    onRetry: () -> Unit,
    onRetryArticle: () -> Unit = {},
    onLoadArticleSection: (Int) -> Unit = {},
    onConfirm: () -> Unit = {},
    onRetryGuide: () -> Unit,
    onLoadMoreGuide: (FandomGuideGroup) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    onLoadSelectedSection: (Int) -> Unit,
    onCloseItem: () -> Unit,
    onRetryItem: (String) -> Unit,
    onReportWrong: () -> Unit,
    rejectedName: String? = null,
    onUndoReject: () -> Unit = {},
    onDismissRejectedUndo: () -> Unit = {},
    onOpenGallery: ((List<GalleryItem>, Int) -> Unit)? = null,
    preferredTranslator: String = "DEFAULT",
    originalKeys: Set<String> = emptySet(),
    onToggleOriginal: (String) -> Unit = {},
    relatedPages: List<FandomRelatedPage> = emptyList(),
    onOpenReaderHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ready = uiState as? FandomMediaWikiViewModel.UiState.Ready
    val articlePage = (articleState as? FandomMediaWikiViewModel.ArticleState.Ready)?.page
    val context = LocalContext.current
    val openArticleGallery: (List<GalleryItem>, Int) -> Unit = onOpenGallery ?: { items, index ->
        items.getOrNull(index)?.let { openExternal(context, it.url) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (rejectedName != null) {
            WikiRejectedUndoCard(
                accentColor = accentColor,
                rejectedName = rejectedName,
                onUndo = onUndoReject,
                onDismiss = onDismissRejectedUndo
            )
        }
        when {
            ready == null && (uiState is FandomMediaWikiViewModel.UiState.Idle ||
                uiState is FandomMediaWikiViewModel.UiState.Loading) -> WikiTabSkeleton(accentColor)
            ready == null -> WikiUnavailableCard(
                accentColor = accentColor,
                messageRes = R.string.fandom_unavailable_media,
                onRetry = onRetry
            )
            else -> {
                MediaWikiHeaderCard(ref = ready.ref, accentColor = accentColor, onOpenWiki = onOpenReaderHome)
                if (ready.ref.confidence == FandomConfidence.PROBABLE && articlePage?.needsConfirmation != true) {
                    WikiConfirmCard(
                        accentColor = accentColor,
                        onConfirm = onConfirm,
                        onReject = onReportWrong
                    )
                }
                when (articleState) {
                    is FandomMediaWikiViewModel.ArticleState.Ready -> CharacterWikiTabContent(
                        page = articleState.page,
                        sections = articleSections,
                        translations = translations,
                        translatingKeys = translatingKeys,
                        spoilerGuard = spoilerGuard,
                        accentColor = accentColor,
                        onLoadSection = onLoadArticleSection,
                        onOpenGallery = openArticleGallery,
                        onConfirm = onConfirm,
                        onReportWrong = onReportWrong,
                        showNestedSections = true,
                        initiallyShowAllFields = true,
                        galleryCategory = GalleryCategory.OTHER,
                        preferredTranslator = preferredTranslator,
                        originalKeys = originalKeys,
                        onToggleOriginal = onToggleOriginal
                    )
                    FandomMediaWikiViewModel.ArticleState.Unavailable -> WikiUnavailableCard(
                        accentColor = accentColor,
                        messageRes = R.string.fandom_media_article_unavailable,
                        onRetry = onRetryArticle,
                        titleRes = R.string.fandom_media_article_unavailable_title
                    )
                    FandomMediaWikiViewModel.ArticleState.Idle,
                    FandomMediaWikiViewModel.ArticleState.Loading -> WikiCard {
                        WikiLoadingLine(accentColor)
                    }
                }
                if (articlePage?.description.isNullOrBlank()) {
                    MediaWikiSummaryCard(
                        state = summaryState,
                        translated = translations[FandomMediaWikiViewModel.KEY_SUMMARY],
                        translating = FandomMediaWikiViewModel.KEY_SUMMARY in translatingKeys,
                        accentColor = accentColor,
                        preferredTranslator = preferredTranslator,
                        showingOriginal = FandomMediaWikiViewModel.KEY_SUMMARY in originalKeys,
                        onToggleOriginal = { onToggleOriginal(FandomMediaWikiViewModel.KEY_SUMMARY) }
                    )
                }
                if (relatedPages.isNotEmpty()) {
                    RelatedPagesStrip(
                        title = stringResource(R.string.fandom_related_pages_title),
                        pages = relatedPages,
                        accentColor = accentColor,
                        onOpenItem = onOpenItem
                    )
                }
                MediaWikiSearchCard(
                    query = searchQuery,
                    state = searchState,
                    accentColor = accentColor,
                    onQueryChange = onSearchChange,
                    onClear = onClearSearch,
                    onOpenItem = onOpenItem
                )
                MediaWikiGuideCard(
                    state = guideState,
                    guideMore = guideMore,
                    accentColor = accentColor,
                    onOpenItem = onOpenItem,
                    onRetryGuide = onRetryGuide,
                    onLoadMoreGuide = onLoadMoreGuide
                )
                if (articlePage == null) {
                    MediaWikiFooter(
                        ref = ready.ref,
                        accentColor = accentColor,
                        onReportWrong = onReportWrong
                    )
                }
            }
        }
    }

    if (ready != null) {
        WikiItemSheet(
            selectedState = selectedState,
            selectedSections = selectedSections,
            wikiId = ready.ref.wikiId,
            spoilerGuard = spoilerGuard,
            translations = translations,
            translatingKeys = translatingKeys,
            accentColor = accentColor,
            onClose = onCloseItem,
            onRetry = onRetryItem,
            onLoadSection = onLoadSelectedSection,
            preferredTranslator = preferredTranslator,
            originalKeys = originalKeys,
            onToggleOriginal = onToggleOriginal
        )
    }
}

@Composable
private fun MediaWikiHeaderCard(
    ref: FandomWikiRef,
    accentColor: Color,
    onOpenWiki: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val openAction = onOpenWiki ?: { openExternal(context, FandomUrls.baseUrl(ref.wikiId)) }
    WikiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ref.siteName ?: ref.wikiId,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.fandom_media_header_subtitle),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = openAction) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fandom_open_page),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * "İlgili wiki sayfaları" şeridi: yatay kaydırılabilir kartlar; her kart
 * FandomWikiReader'daki [WikiRelatedPageCard] ile aynı görsel dile sahiptir.
 */
@Composable
private fun RelatedPagesStrip(
    title: String,
    pages: List<FandomRelatedPage>,
    accentColor: Color,
    onOpenItem: (String) -> Unit
) {
    if (pages.isEmpty()) return
    WikiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.fandom_related_pages_count, pages.size),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pages, key = { it.title }) { page ->
                WikiRelatedPageCard(
                    page = page,
                    accentColor = accentColor,
                    onClick = { onOpenItem(page.title) }
                )
            }
        }
    }
}

@Composable
private fun MediaWikiSummaryCard(
    state: FandomMediaWikiViewModel.BlurbState,
    translated: String?,
    translating: Boolean,
    accentColor: Color,
    preferredTranslator: String = "DEFAULT",
    showingOriginal: Boolean = false,
    onToggleOriginal: (() -> Unit)? = null
) {
    val description = (state as? FandomMediaWikiViewModel.BlurbState.Ready)?.blurb?.description
    if (state is FandomMediaWikiViewModel.BlurbState.Idle || state is FandomMediaWikiViewModel.BlurbState.Loading) {
        WikiCard {
            WikiLoadingLine(accentColor)
        }
        return
    }
    if (description.isNullOrBlank()) return
    val shownText = if (showingOriginal) description else (translated ?: description)
    WikiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.fandom_media_summary_title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Detay sayfasındaki Açıklama kartının çeviri düğmesiyle birebir aynı: her zaman
            // görünür, dokununca orijinal özeti harici çeviri uygulamasında açar, uygulama içi
            // çeviri sürerken nabız + dönen halka animasyonu gösterir.
            WikiTranslateButton(
                text = description,
                preferredTranslator = preferredTranslator,
                accentColor = accentColor,
                isLoading = translating,
                hasTranslation = translated != null,
                showingOriginal = showingOriginal,
                onToggleOriginal = onToggleOriginal
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SelectionContainer {
            Text(
                text = shownText,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MediaWikiSearchCard(
    query: String,
    state: FandomMediaWikiViewModel.SearchState,
    accentColor: Color,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    WikiCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.fandom_media_search_hint),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KitsugiColors.Surface,
                unfocusedContainerColor = KitsugiColors.Surface,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = KitsugiColors.SurfaceStrong,
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary,
                cursorColor = accentColor
            )
        )
        when (state) {
            is FandomMediaWikiViewModel.SearchState.Loading -> {
                Spacer(modifier = Modifier.height(10.dp))
                WikiLoadingLine(accentColor)
            }
            is FandomMediaWikiViewModel.SearchState.Ready -> {
                if (state.hits.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.fandom_media_search_empty),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    state.hits.forEach { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .tvClickable(shape = RoundedCornerShape(12.dp)) { onOpenItem(hit.title) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hit.title,
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                hit.snippet?.let { snippet ->
                                    Text(
                                        text = snippet,
                                        color = KitsugiColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is FandomMediaWikiViewModel.SearchState.Failed -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fandom_section_error),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaWikiGuideCard(
    state: FandomMediaWikiViewModel.GuideState,
    guideMore: FandomMediaWikiViewModel.GuideMore,
    accentColor: Color,
    onOpenItem: (String) -> Unit,
    onRetryGuide: () -> Unit,
    onLoadMoreGuide: (FandomGuideGroup) -> Unit
) {
    when (state) {
        is FandomMediaWikiViewModel.GuideState.Idle, is FandomMediaWikiViewModel.GuideState.Loading -> {
            WikiCard {
                Text(
                    text = stringResource(R.string.fandom_guide_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                WikiLoadingLine(accentColor)
            }
        }
        is FandomMediaWikiViewModel.GuideState.Failed -> {
            WikiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.fandom_section_error),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetryGuide) {
                        Text(
                            text = stringResource(R.string.action_retry_label),
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        is FandomMediaWikiViewModel.GuideState.Ready -> {
            val groups = state.guide.groups.filter { it.items.isNotEmpty() }
            if (groups.isEmpty()) return
            WikiCard {
                Text(
                    text = stringResource(R.string.fandom_guide_title),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                groups.forEach { section ->
                    // Grup kimliği sabit: katla/aç durumu liste yenilenince kaymaz.
                    key(section.group) {
                        val loadingGroup = (guideMore as? FandomMediaWikiViewModel.GuideMore.Loading)?.group
                        val failedGroup = (guideMore as? FandomMediaWikiViewModel.GuideMore.Failed)?.group
                        Spacer(modifier = Modifier.height(12.dp))
                        GuideGroupSection(
                            heading = stringResource(groupLabelRes(section.group)),
                            items = section.items,
                            hasMoreOnWiki = section.hasMore,
                            isLoadingMore = loadingGroup == section.group,
                            loadFailed = failedGroup == section.group,
                            accentColor = accentColor,
                            onOpenItem = onOpenItem,
                            onLoadMore = { onLoadMoreGuide(section.group) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Evren rehberi grubu: başlık + çipler + "Devamını göster" / "Daha az göster".
 *
 * Katlanmış durumda ilk [GUIDE_COLLAPSED_CHIPS] çip görünür (çipler zaten sayfalı
 * geldiği için liste başlangıçta kısa kalır). "Devamını göster":
 *  1) yüklü ama gizli çipler varsa onları açar,
 *  2) wiki kategorisinde başka sayfa varsa (`cmcontinue`) sonraki 14'lük sayfayı çeker.
 * Böylece düğme, eldeki veri bittiğinde de aynı yerde durup devamını getirir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideGroupSection(
    heading: String,
    items: List<FandomGuideItem>,
    hasMoreOnWiki: Boolean,
    isLoadingMore: Boolean,
    loadFailed: Boolean,
    accentColor: Color,
    onOpenItem: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (items.isEmpty()) return
    // Grup başlığına bağlı: veri yenilenince (ya da sayfa yeniden kurulunca) sıfırlanır.
    var expanded by rememberSaveable(heading) { mutableStateOf(false) }
    val hiddenCount = (items.size - GUIDE_COLLAPSED_CHIPS).coerceAtLeast(0)
    val visibleItems = if (expanded || hiddenCount == 0) items else items.take(GUIDE_COLLAPSED_CHIPS)
    val canCollapse = expanded && hiddenCount > 0
    val canShowMore = (!expanded && hiddenCount > 0) || hasMoreOnWiki

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Text(
            text = heading,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleItems.forEach { item ->
                GuideChip(title = item.title, accentColor = accentColor, onClick = { onOpenItem(item.title) })
            }
        }
        if (canShowMore || canCollapse) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canShowMore) {
                    GuideActionChip(
                        label = when {
                            isLoadingMore -> stringResource(R.string.fandom_guide_more_loading)
                            !expanded && hiddenCount > 0 ->
                                stringResource(R.string.fandom_guide_show_more_count, hiddenCount)
                            else -> stringResource(R.string.fandom_guide_show_more)
                        },
                        icon = if (isLoadingMore) null else Icons.Rounded.ExpandMore,
                        loading = isLoadingMore,
                        enabled = !isLoadingMore,
                        accentColor = accentColor,
                        onClick = {
                            if (!expanded) expanded = true
                            if (hasMoreOnWiki) onLoadMore()
                        }
                    )
                }
                if (canCollapse) {
                    GuideActionChip(
                        label = stringResource(R.string.fandom_guide_show_less),
                        icon = Icons.Rounded.ExpandLess,
                        loading = false,
                        accentColor = accentColor,
                        onClick = { expanded = false }
                    )
                }
            }
        }
        if (loadFailed) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.studio_page_load_more_failed),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Rehber grubu altındaki vurgu renkli eylem düğmesi (TV'de odak halkası alır). */
@Composable
private fun GuideActionChip(
    label: String,
    icon: ImageVector?,
    loading: Boolean,
    accentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.32f), shape)
            .tvClickable(enabled = enabled, shape = shape) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = accentColor,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = accentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GuideChip(title: String, accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceStrong)
            .border(1.dp, accentColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .tvClickable(shape = RoundedCornerShape(10.dp)) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaWikiFooter(
    ref: FandomWikiRef,
    accentColor: Color,
    onReportWrong: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.fandom_source_label, ref.siteName ?: ref.wikiId),
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(R.string.fandom_license_note),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ref.confidence == FandomConfidence.PROBABLE) {
                Text(
                    text = stringResource(R.string.fandom_auto_match),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onReportWrong) {
                Text(
                    text = stringResource(R.string.fandom_report_wrong),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/** Çip / arama sonucu kartı: tam makale okuyucusu (açıklama, bilgi kutusu, açılır bölümler, görseller). */
@Composable
private fun WikiItemSheet(
    selectedState: FandomMediaWikiViewModel.SelectedState,
    selectedSections: Map<Int, FandomMediaWikiViewModel.ArticleSectionState>,
    wikiId: String,
    spoilerGuard: Boolean,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    accentColor: Color,
    onClose: () -> Unit,
    onRetry: (String) -> Unit,
    onLoadSection: (Int) -> Unit,
    preferredTranslator: String = "DEFAULT",
    originalKeys: Set<String> = emptySet(),
    onToggleOriginal: (String) -> Unit = {}
) {
    if (selectedState is FandomMediaWikiViewModel.SelectedState.Closed) return
    val scrollState = rememberScrollState()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.84f
    KitsugiSheetOrDialog(onDismiss = onClose, innerColumnScrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedState) {
                is FandomMediaWikiViewModel.SelectedState.Loading -> WikiLoadingLine(accentColor)
                is FandomMediaWikiViewModel.SelectedState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.fandom_section_error),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onRetry(selectedState.title) }) {
                        Text(
                            text = stringResource(R.string.action_retry_label),
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is FandomMediaWikiViewModel.SelectedState.Ready -> {
                    val context = LocalContext.current
                    val article = selectedState.article
                    val articleTitle = article.pageTitle
                    val description = article.description
                    val key = FandomMediaWikiViewModel.itemKey(selectedState.title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = articleTitle,
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = article.siteName ?: wikiId,
                                color = accentColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        WikiTranslateButton(
                            text = description,
                            preferredTranslator = preferredTranslator,
                            accentColor = accentColor,
                            isLoading = key in translatingKeys
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = KitsugiColors.TextSecondary
                            )
                        }
                    }

                    ArticleInfoboxCard(
                        article = article,
                        keyTitle = selectedState.title,
                        translations = translations,
                        translatingKeys = translatingKeys,
                        spoilerGuard = spoilerGuard,
                        accentColor = accentColor,
                        preferredTranslator = preferredTranslator
                    )

                    if (!description.isNullOrBlank()) {
                        WikiCard {
                            Text(
                                text = stringResource(R.string.fandom_summary_title),
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    text = translations[key] ?: description,
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    val topLevel = article.sections.minOfOrNull { it.level } ?: 0
                    val visibleSections = article.sections
                    if (article.sectionsFailed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.fandom_contents_load_failed),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onRetry(selectedState.title) }) {
                                Text(stringResource(R.string.action_retry_label), color = accentColor)
                            }
                        }
                    } else if (visibleSections.isNotEmpty()) {
                        WikiCard {
                            Text(
                                text = stringResource(R.string.fandom_contents_title),
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            visibleSections.forEach { section ->
                                ArticleSectionRow(
                                    articleTitle = selectedState.title,
                                    section = section,
                                    depth = (section.level - topLevel).coerceIn(0, 4),
                                    state = selectedSections[section.index],
                                    translated = translations[
                                        FandomMediaWikiViewModel.articleSectionKey(selectedState.title, section.index)
                                    ],
                                    translating = FandomMediaWikiViewModel.articleSectionKey(
                                        selectedState.title,
                                        section.index
                                    ) in translatingKeys,
                                    spoilerGuard = spoilerGuard,
                                    accentColor = accentColor,
                                    preferredTranslator = preferredTranslator,
                                    onLoad = { onLoadSection(section.index) }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.fandom_article_no_sections),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.fandom_source_label, article.siteName ?: wikiId),
                                color = KitsugiColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.fandom_license_note),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { openExternal(context, article.pageUrl) }) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.fandom_open_article_external),
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleInfoboxCard(
    article: FandomArticlePage,
    keyTitle: String,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    spoilerGuard: Boolean,
    accentColor: Color,
    preferredTranslator: String
) {
    val infobox = article.infobox ?: return
    val fields = infobox.groups.flatMap { group -> group.fields.map { group.header to it } }
    if (fields.isEmpty() && infobox.images.isEmpty()) return
    val revealed = remember(article.wikiId, article.pageTitle) { mutableStateMapOf<Int, Boolean>() }
    var showAll by rememberSaveable(article.wikiId, article.pageTitle) { mutableStateOf(false) }
    val visibleCount = if (showAll) fields.size else minOf(fields.size, 10)

    WikiCard {
        if (infobox.images.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                infobox.images.take(8).forEach { image ->
                    Column(
                        modifier = Modifier.width(72.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FandomWikiImage(
                            url = image.url,
                            width = 180,
                            contentDescription = image.caption ?: article.pageTitle,
                            accentColor = accentColor,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 72.dp, height = 96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KitsugiColors.SurfaceStrong)
                        )
                        image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                            Text(
                                text = caption,
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = infobox.title ?: article.pageTitle,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (fields.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.fandom_infobox_title),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                val sourceText = fields.mapIndexedNotNull { index, (_, field) ->
                    val hidden = spoilerGuard && field.spoiler && revealed[index] != true
                    if (hidden) null else "${field.label}: ${field.value}"
                }.joinToString("\n")
                WikiTranslateButton(
                    text = sourceText,
                    preferredTranslator = preferredTranslator,
                    accentColor = accentColor,
                    isLoading = FandomMediaWikiViewModel.articleInfoboxKey(keyTitle) in translatingKeys
                )
            }
            var lastHeader: String? = null
            fields.take(visibleCount).forEachIndexed { index, (header, field) ->
                if (header != null && header != lastHeader) {
                    Text(
                        text = localizedInfoboxHeader(header),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = if (index == 0) 4.dp else 8.dp, bottom = 3.dp)
                    )
                }
                lastHeader = header
                val hidden = spoilerGuard && field.spoiler && revealed[index] != true
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = localizedInfoboxLabel(field),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.4f).padding(end = 8.dp)
                    )
                    if (hidden) {
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .tvClickable(shape = RoundedCornerShape(8.dp)) { revealed[index] = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.fandom_spoiler_hidden),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text(
                            text = translations[FandomMediaWikiViewModel.articleInfoboxFieldKey(keyTitle, index)]
                                ?: localizedInfoboxValue(field.value),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                }
            }
            if (fields.size > 10) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        text = stringResource(if (showAll) R.string.fandom_show_less else R.string.fandom_show_all_fields),
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleSectionRow(
    articleTitle: String,
    section: FandomSectionRef,
    depth: Int,
    state: FandomMediaWikiViewModel.ArticleSectionState?,
    translated: String?,
    translating: Boolean,
    spoilerGuard: Boolean,
    accentColor: Color,
    preferredTranslator: String,
    onLoad: () -> Unit
) {
    var expanded by rememberSaveable(articleTitle, section.index) { mutableStateOf(false) }
    var revealed by rememberSaveable(articleTitle, section.index) { mutableStateOf(false) }
    val gated = spoilerGuard && section.spoiler && !revealed
    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .tvClickable(shape = RoundedCornerShape(10.dp)) { expanded = !expanded }
                .padding(start = (depth * 14).dp, top = 9.dp, bottom = 9.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = localizedSectionTitle(section.title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (section.spoiler) {
                Text(
                    text = stringResource(R.string.fandom_spoiler_badge),
                    color = KitsugiColors.AccentOrange,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = (27 + depth * 14).dp, end = 3.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (gated) {
                    Text(
                        text = stringResource(R.string.fandom_spoiler_section_warning),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { revealed = true }) {
                        Text(
                            text = stringResource(R.string.fandom_spoiler_reveal),
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    LaunchedEffect(articleTitle, section.index) {
                        if (state == null) onLoad()
                    }
                    when (state) {
                        null, is FandomMediaWikiViewModel.ArticleSectionState.Loading -> WikiLoadingLine(accentColor)
                        is FandomMediaWikiViewModel.ArticleSectionState.Failed -> Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.fandom_section_error),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onLoad) {
                                Text(stringResource(R.string.action_retry_label), color = accentColor)
                            }
                        }
                        is FandomMediaWikiViewModel.ArticleSectionState.Loaded -> {
                            if (state.content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.fandom_section_empty),
                                    color = KitsugiColors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    WikiTranslateButton(
                                        text = state.content.plainText(),
                                        preferredTranslator = preferredTranslator,
                                        accentColor = accentColor,
                                        isLoading = translating
                                    )
                                }
                                ArticleSectionBody(state.content, translated)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleSectionBody(content: FandomSectionContent, translated: String?) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (translated != null) {
                translated.split(Regex("\\n\\s*\\n"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { paragraph ->
                        Text(
                            text = paragraph,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                content.blocks
                    .filter { it.kind == FandomBlockKind.IMAGE }
                    .forEach { block -> ArticleImageBlock(block) }
            } else {
                content.blocks.forEach { block ->
                    when (block.kind) {
                        FandomBlockKind.HEADING -> Text(
                            text = localizedSectionTitle(block.text),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FandomBlockKind.PARAGRAPH -> Text(
                            text = block.text,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FandomBlockKind.BULLET -> Row(modifier = Modifier.fillMaxWidth()) {
                            Text("•", color = KitsugiColors.TextSecondary, modifier = Modifier.padding(end = 8.dp))
                            Text(block.text, color = KitsugiColors.TextPrimary, modifier = Modifier.weight(1f))
                        }
                        FandomBlockKind.QUOTE -> Text(
                            text = "“${block.text}”",
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FandomBlockKind.IMAGE -> ArticleImageBlock(block)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleImageBlock(block: FandomTextBlock) {
    val image = block.image ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FandomWikiImage(
            url = image.url,
            width = 720,
            contentDescription = image.caption,
            accentColor = KitsugiColors.TextMuted,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.SurfaceStrong)
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

@Composable
private fun localizedInfoboxLabel(field: FandomInfoboxField): String {
    val resource = FandomInfoboxLabels.labelResId(field.label, field.source)
    return if (resource != null) stringResource(resource) else field.label
}

@Composable
private fun localizedInfoboxHeader(header: String): String {
    val resource = FandomInfoboxLabels.headerResId(header)
    return if (resource != null) stringResource(resource) else header
}

@Composable
private fun localizedInfoboxValue(value: String): String {
    val resource = FandomInfoboxLabels.valueResId(value)
    return if (resource != null) stringResource(resource) else value
}

@Composable
private fun localizedSectionTitle(raw: String): String {
    val resource = FandomSectionTitles.resId(raw)
    return if (resource != null) stringResource(resource) else raw
}

@Composable
internal fun WikiLoadingLine(accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = accentColor,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.fandom_section_loading),
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WikiCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun groupLabelRes(group: FandomGuideGroup): Int = when (group) {
    FandomGuideGroup.CHARACTERS -> R.string.fandom_guide_characters
    FandomGuideGroup.ORGANIZATIONS -> R.string.fandom_guide_organizations
    FandomGuideGroup.STORY_ARCS -> R.string.fandom_guide_story_arcs
    FandomGuideGroup.EPISODES -> R.string.fandom_guide_episodes
    FandomGuideGroup.CHAPTERS -> R.string.fandom_guide_chapters
    FandomGuideGroup.ABILITIES -> R.string.fandom_guide_abilities
    FandomGuideGroup.PLACES -> R.string.fandom_guide_places
    FandomGuideGroup.TERMS -> R.string.fandom_guide_terms
}

private fun openExternal(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.fandom_open_failed), Toast.LENGTH_SHORT).show()
    }
}

package com.kitsugi.animelist.ui.screens.detail.wiki

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsState()
    val guideState by viewModel.guide.collectAsState()
    val guideMore by viewModel.guideMore.collectAsState()
    val summaryState by viewModel.summary.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedState by viewModel.selected.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val translatingKeys by viewModel.translating.collectAsState()
    val preferredTranslator by viewModel.preferredTranslator.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is FandomMediaWikiViewModel.UiState.Ready) viewModel.ensureContentLoaded()
    }

    MediaWikiTabContent(
        uiState = uiState,
        guideState = guideState,
        guideMore = guideMore,
        summaryState = summaryState,
        searchState = searchState,
        searchQuery = searchQuery,
        selectedState = selectedState,
        translations = translations,
        translatingKeys = translatingKeys,
        preferredTranslator = preferredTranslator,
        accentColor = accentColor,
        onRetry = viewModel::retry,
        onRetryGuide = viewModel::retryGuide,
        onLoadMoreGuide = viewModel::loadMoreGuide,
        onSearchChange = viewModel::onSearchQueryChange,
        onClearSearch = viewModel::clearSearch,
        onOpenItem = viewModel::openItem,
        onCloseItem = viewModel::closeItem,
        onRetryItem = viewModel::retryItem,
        onReportWrong = viewModel::reportWrongMatch,
        modifier = modifier
    )
}

@Composable
internal fun MediaWikiTabContent(
    uiState: FandomMediaWikiViewModel.UiState,
    guideState: FandomMediaWikiViewModel.GuideState,
    guideMore: FandomMediaWikiViewModel.GuideMore,
    summaryState: FandomMediaWikiViewModel.BlurbState,
    searchState: FandomMediaWikiViewModel.SearchState,
    searchQuery: String,
    selectedState: FandomMediaWikiViewModel.SelectedState,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    accentColor: Color,
    onRetry: () -> Unit,
    onRetryGuide: () -> Unit,
    onLoadMoreGuide: (FandomGuideGroup) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    onCloseItem: () -> Unit,
    onRetryItem: (String) -> Unit,
    onReportWrong: () -> Unit,
    modifier: Modifier = Modifier,
    /** Harici çeviri uygulaması tercihi (detay açıklama düğmesiyle aynı davranış için). */
    preferredTranslator: String = "DEFAULT"
) {
    val ready = uiState as? FandomMediaWikiViewModel.UiState.Ready
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            ready == null && (uiState is FandomMediaWikiViewModel.UiState.Idle ||
                uiState is FandomMediaWikiViewModel.UiState.Loading) -> WikiTabSkeleton(accentColor)
            ready == null -> WikiUnavailableCard(
                accentColor = accentColor,
                messageRes = R.string.fandom_unavailable_media,
                onRetry = onRetry
            )
            else -> {
                MediaWikiHeaderCard(ref = ready.ref, accentColor = accentColor)
                MediaWikiSummaryCard(
                    state = summaryState,
                    translated = translations[FandomMediaWikiViewModel.KEY_SUMMARY],
                    translating = FandomMediaWikiViewModel.KEY_SUMMARY in translatingKeys,
                    accentColor = accentColor,
                    preferredTranslator = preferredTranslator
                )
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
                MediaWikiFooter(
                    ref = ready.ref,
                    accentColor = accentColor,
                    onReportWrong = onReportWrong
                )
            }
        }
    }

    if (ready != null) {
        WikiItemSheet(
            selectedState = selectedState,
            wikiId = ready.ref.wikiId,
            translations = translations,
            translatingKeys = translatingKeys,
            accentColor = accentColor,
            onClose = onCloseItem,
            onRetry = onRetryItem,
            preferredTranslator = preferredTranslator
        )
    }
}

@Composable
private fun MediaWikiHeaderCard(
    ref: FandomWikiRef,
    accentColor: Color
) {
    val context = LocalContext.current
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
            TextButton(onClick = { openExternal(context, FandomUrls.baseUrl(ref.wikiId)) }) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fandom_open_in_browser),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
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
    preferredTranslator: String = "DEFAULT"
) {
    val description = (state as? FandomMediaWikiViewModel.BlurbState.Ready)?.blurb?.description
    if (state is FandomMediaWikiViewModel.BlurbState.Idle || state is FandomMediaWikiViewModel.BlurbState.Loading) {
        WikiCard {
            WikiLoadingLine(accentColor)
        }
        return
    }
    if (description.isNullOrBlank()) return
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
                isLoading = translating
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SelectionContainer {
            Text(
                text = translated ?: description,
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

/** Çip / arama sonucu kartı: kısa açıklama + çeviri + "Tamamını oku". */
@Composable
private fun WikiItemSheet(
    selectedState: FandomMediaWikiViewModel.SelectedState,
    wikiId: String,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    accentColor: Color,
    onClose: () -> Unit,
    onRetry: (String) -> Unit,
    preferredTranslator: String = "DEFAULT"
) {
    val title = when (selectedState) {
        is FandomMediaWikiViewModel.SelectedState.Ready -> selectedState.title
        is FandomMediaWikiViewModel.SelectedState.Failed -> selectedState.title
        is FandomMediaWikiViewModel.SelectedState.Loading -> null
        else -> null
    }
    if (selectedState is FandomMediaWikiViewModel.SelectedState.Closed) return
    val key = title?.let(FandomMediaWikiViewModel::itemKey)
    KitsugiSheetOrDialog(onDismiss = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    val blurb = selectedState.blurb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = blurb?.resolvedTitle ?: selectedState.title,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        // Detay sayfasındaki açıklama düğmesiyle aynı: her zaman ikon,
                        // dokununca kartın orijinal metni harici uygulamada açılır.
                        WikiTranslateButton(
                            text = selectedState.blurb?.description,
                            preferredTranslator = preferredTranslator,
                            accentColor = accentColor,
                            isLoading = key != null && key in translatingKeys
                        )
                    }
                    val description = blurb?.description
                    val translated = key?.let { translations[it] }
                    if (!description.isNullOrBlank() || translated != null) {
                        SelectionContainer {
                            Text(
                                text = translated ?: description.orEmpty(),
                                color = KitsugiColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.fandom_blurb_empty),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            openExternal(context, FandomUrls.pageUrl(wikiId, blurb?.resolvedTitle ?: selectedState.title))
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.fandom_read_all),
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
    FandomGuideGroup.ORGANIZATIONS -> R.string.fandom_guide_organizations
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

package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiShimmerMediaRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable

/** One source = one distinct destination, with all its own categories underneath it. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.allSourcesExploreSections(
    states: Map<ExplorePlatform, ExploreSourceState>,
    showAdultContent: Boolean,
    collapsedSources: Set<ExplorePlatform>,
    onToggleSource: (ExplorePlatform) -> Unit,
    startIndex: Int,
    onJumpToIndex: (Int) -> Unit,
    onRetrySource: (ExplorePlatform) -> Unit,
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: ((JikanSearchResult) -> Unit)?,
    onSeeAllSection: (String, ExploreCategoryType, List<JikanSearchResult>, ExplorePlatform) -> Unit,
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    blurAdultMedia: Boolean = false,
    showSourceJumpBar: Boolean = true
) {
    val sections = allSourceSections(states, showAdultContent).groupBy { it.platform }
    val headerIndices = allSourceHeaderIndices(sections, collapsedSources, startIndex, showSourceJumpBar)
    item(key = "all_sources_intro") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("Altı kaynak. Tek keşif.", style = MaterialTheme.typography.headlineSmall,
                color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Her platformun trendleri, en iyileri ve yeni keşifleri kendi alanında. Bir kaynağa atla veya aşağı kaydırarak hepsini gez.",
                style = MaterialTheme.typography.bodyMedium, color = KitsugiColors.TextSecondary)
        }
    }
    if (showSourceJumpBar) {
        stickyHeader(key = "all_sources_navigation") {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .border(1.dp, KitsugiColors.SurfaceElevated.copy(alpha = 0.78f), RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = KitsugiColors.Surface.copy(alpha = 0.96f),
            tonalElevation = 2.dp
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "back_to_explore_top") {
                    BackToExploreChip { onJumpToIndex(0) }
                }
                items(ExplorePlatform.sources, key = { it.name }) { platform ->
                    SourceJumpChip(platform, states[platform]?.isLoading == true) {
                        onJumpToIndex(headerIndices.getValue(platform))
                    }
                }
            }
        }
    }
    }
    ExplorePlatform.sources.forEach { platform ->
        val state = states[platform] ?: ExploreSourceState(isLoading = true)
        val sourceSections = sections[platform].orEmpty()
        val expanded = platform !in collapsedSources
        item(key = "all_source_${platform.name}") {
            SourceHeader(
                platform = platform,
                state = state,
                sections = sourceSections,
                expanded = expanded,
                onToggle = { onToggleSource(platform) },
                onRetry = { onRetrySource(platform) },
                onCategory = { index ->
                    sourceSections.getOrNull(index)?.let { section ->
                        onSeeAllSection(
                            "${platform.label} • ${section.title}",
                            section.category,
                            section.results,
                            platform
                        )
                    }
                }
            )
        }
        if (expanded) {
            items(sourceSections, key = { "all_${it.key}" }) { section ->
                Column(Modifier.padding(top = 6.dp, bottom = 20.dp)) {
                    // The source heading owns the rows; don't repeat its name on every title.
                    CompositionLocalProviderForSource(platform) {
                        KitsugiHorizontalMediaSection(
                            title = section.title,
                            results = section.results,
                            isLoading = false,
                            alreadyInList = alreadyInList,
                            getMediaEntry = getMediaEntry,
                            onItemClick = onItemClick,
                            onLongClickItem = onLongClickItem,
                            onSeeAllClick = {
                                onSeeAllSection("${platform.label} · ${section.title}",
                                    section.category, section.results, platform)
                            },
                            titleLanguage = titleLanguage,
                            scoreFormat = scoreFormat,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia
                        )
                    }
                }
            }
            item(key = "all_source_footer_${platform.name}") {
                if (sourceSections.isEmpty() && state.isLoading) {
                    Column(Modifier.padding(vertical = 16.dp)) {
                        KitsugiShimmerMediaRow(cardCount = 5)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onToggleSource(platform) }) { Text("${platform.shortName} alanını daralt") }
                    val next = ExplorePlatform.sources.getOrNull(ExplorePlatform.sources.indexOf(platform) + 1)
                    if (next != null) {
                        TextButton(onClick = { onJumpToIndex(headerIndices.getValue(next)) }) {
                            Text("Sırada: ${next.shortName} →")
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = sourceColor(platform).copy(alpha = 0.25f))
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderForSource(platform: ExplorePlatform, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.kitsugi.animelist.ui.theme.LocalKitsugiAccent provides sourceColor(platform), content = content
    )
}

private fun sourceColor(platform: ExplorePlatform): Color = when (platform) {
    ExplorePlatform.AniList -> Color(0xFF65BFFF)
    ExplorePlatform.MAL -> Color(0xFF8EAFFF)
    ExplorePlatform.TMDB -> Color(0xFF54D9B4)
    ExplorePlatform.SIMKL -> Color(0xFFC2ADFF)
    ExplorePlatform.KITSU -> Color(0xFFFFA080)
    ExplorePlatform.SHIKIMORI -> Color(0xFFD1BEEC)
    ExplorePlatform.ALL -> Color(0xFFE9AD65)
}

@Composable
private fun BackToExploreChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceElevated.copy(alpha = 0.82f))
            .border(1.dp, KitsugiColors.TextMuted.copy(alpha = 0.20f), shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = KitsugiColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text("Keşfet", color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SourceJumpChip(platform: ExplorePlatform, loading: Boolean, onClick: () -> Unit) {
    val color = sourceColor(platform)
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.32f), shape)
            .tvClickable(shape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        KitsugiPlatformLogo(platformId = platform.name, size = 18.dp)
        Text(
            text = platform.shortName,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = color,
                strokeWidth = 1.5.dp
            )
        }
    }
}

@Composable
private fun SourceHeader(
    platform: ExplorePlatform,
    state: ExploreSourceState,
    sections: List<ExploreSourceSection>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onCategory: (Int) -> Unit
) {
    val color = sourceColor(platform)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.17f), KitsugiColors.Surface)), RoundedCornerShape(24.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitsugiPlatformLogo(platformId = platform.name, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(platform.label, color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("${sections.size} kategori · ${sections.flatMap { it.results }.distinctBy { it.exploreIdentity() }.size} içerik",
                    color = color, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onToggle) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "${platform.label} alanını ${if (expanded) "daralt" else "genişlet"}", tint = color)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(platform.description, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (state.isLoading) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = color)
            Text(if (state.payload != null) "İçerikler güncelleniyor…" else "Bu kaynağın keşif alanı hazırlanıyor…",
                modifier = Modifier.padding(top = 6.dp), color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else if (state.error != null) {
            Text(state.error, Modifier.padding(top = 10.dp), color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("${platform.shortName} için yeniden dene", color = color) }
        } else if (sections.isEmpty()) {
            Text("İçerik filtrelerine uygun sonuç yok.", Modifier.padding(top = 10.dp),
                color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (expanded && sections.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections.size) { index ->
                    val section = sections[index]
                    SuggestionChip(onClick = { onCategory(index) }, label = {
                        Text(section.title, color = color)
                    })
                }
            }
        }
    }
}

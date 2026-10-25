package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background

@Composable
private fun ExploreCategoryGroupKind.localizedTitle(): String = when (this) {
    ExploreCategoryGroupKind.TV_MOVIES -> stringResource(R.string.explore_category_tv_movies)
    ExploreCategoryGroupKind.ANIME -> stringResource(R.string.media_type_anime)
    ExploreCategoryGroupKind.MANGA -> stringResource(R.string.media_type_manga)
    ExploreCategoryGroupKind.PERSONAL_LISTS -> stringResource(R.string.tab_mylist)
}

@Composable
fun ExploreCategoriesSection(
    platform: ExplorePlatform,
    sections: List<ExploreSourceSection>,
    isLoading: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(platform, sections) {
        buildExploreCategoryGroups(platform, sections)
    }
    if (groups.isEmpty() && !isLoading) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.Surface.copy(alpha = 0.4f))
                .clickable { onExpandedChange(!isExpanded) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.GridView,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.explore_categories_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.genislet_daralt),
                tint = KitsugiColors.TextSecondary
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp)
            ) {
                if (groups.isEmpty() && isLoading) {
                    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
                    val placeholderWidths = listOf(116.dp, 108.dp, 168.dp, 124.dp, 132.dp)
                    repeat(2) { rowIdx ->
                        if (rowIdx > 0) Spacer(modifier = Modifier.height(20.dp))
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(64.dp).height(14.dp),
                            cornerRadius = 4.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(placeholderWidths.size) { idx ->
                                KitsugiShimmerBlock(
                                    brush = brush,
                                    modifier = Modifier
                                        .width(placeholderWidths[idx])
                                        .height(38.dp),
                                    cornerRadius = 12.dp
                                )
                            }
                        }
                    }
                } else {
                    groups.forEachIndexed { groupIndex, group ->
                        if (groupIndex > 0) {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        Text(
                            text = group.kind.localizedTitle(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = group.sections,
                                key = { section -> "category_chip_${section.key}" }
                            ) { section ->
                                val resolvedTitle = localizedExploreSectionTitle(section.title)
                                ExploreCategoryChip(
                                    label = resolvedTitle,
                                    onClick = {
                                        openExploreSectionSeeAll(section, resolvedTitle, onSeeAllSection)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun TmdbCategoriesSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    filteredTopAnime: List<JikanSearchResult>,
    filteredAiringAnime: List<JikanSearchResult>,
    filteredMovieAnime: List<JikanSearchResult>,
    filteredTopManga: List<JikanSearchResult>,
    filteredUpcomingAnime: List<JikanSearchResult>,
    filteredSeasonalAnime: List<JikanSearchResult>,
    filteredPublishingManga: List<JikanSearchResult>,
    filteredTrendingAnime: List<JikanSearchResult>,
    filteredNewlyAddedAnime: List<JikanSearchResult>,
    filteredTrendingManga: List<JikanSearchResult>,
    filteredUpcomingMediaTmdb: List<JikanSearchResult>,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    onOpenAiringCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "Kategoriler",
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
                Text(
                    text = stringResource(R.string.dizi_ve_film),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.trend_her_sey),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.explore_tmdb_trending_all), ExploreCategoryType.TOP_ANIME, filteredTopAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = "Trend Diziler",
                            onClick = { onSeeAllSection("Trend Diziler", ExploreCategoryType.AIRING_ANIME, filteredAiringAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = "Trend Filmler",
                            onClick = { onSeeAllSection("Trend Filmler", ExploreCategoryType.MOVIE_ANIME, filteredMovieAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.populer_diziler),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.explore_tmdb_popular_shows), ExploreCategoryType.TOP_MANGA, filteredTopManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.populer_filmler),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.explore_tmdb_popular_movies), ExploreCategoryType.UPCOMING_ANIME, filteredUpcomingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.yakinda_yayinda),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.explore_airing_soon), ExploreCategoryType.UPCOMING_MEDIA_TMDB, filteredUpcomingMediaTmdb) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.en_yuksek_puanli_diziler),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.en_yuksek_puanli_diziler), ExploreCategoryType.SEASONAL_ANIME, filteredSeasonalAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.en_yuksek_puanli_filmler),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.en_yuksek_puanli_filmler), ExploreCategoryType.PUBLISHING_MANGA, filteredPublishingManga) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Anime",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = "Trend Animeler",
                            onClick = { onSeeAllSection("Trend Animeler", ExploreCategoryType.TRENDING_ANIME, filteredTrendingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.populer_animeler),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.populer_animeler), ExploreCategoryType.NEWLY_ADDED_ANIME, filteredNewlyAddedAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.en_yuksek_puanli_animeler),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.explore_top_rated_anime), ExploreCategoryType.TRENDING_MANGA, filteredTrendingManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.yakinda_yayinda),
                            onClick = { onSeeAllSection(KitsugiStrings.get(R.string.explore_airing_soon), ExploreCategoryType.UPCOMING_MEDIA_TMDB, filteredUpcomingMediaTmdb) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.yayin_takvimi),
                            onClick = onOpenAiringCalendar
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultCategoriesSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    filteredSeasonalAnime: List<JikanSearchResult>,
    filteredTopAnime: List<JikanSearchResult>,
    filteredTrendingAnime: List<JikanSearchResult>,
    filteredTopRatedAnime: List<JikanSearchResult> = emptyList(),
    filteredAiringAnime: List<JikanSearchResult> = emptyList(),
    filteredUpcomingAnime: List<JikanSearchResult> = emptyList(),
    filteredMovieAnime: List<JikanSearchResult>,
    filteredNewlyAddedAnime: List<JikanSearchResult>,
    filteredTopManga: List<JikanSearchResult>,
    filteredTrendingManga: List<JikanSearchResult>,
    filteredTopRatedManga: List<JikanSearchResult> = emptyList(),
    filteredPublishingManga: List<JikanSearchResult>,
    filteredNewlyAddedManga: List<JikanSearchResult>,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    onOpenAiringCalendar: () -> Unit,
    onOpenMangaReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    text = "Kategoriler",
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
                Text(
                    text = "Anime",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_anime), ExploreCategoryType.TOP_ANIME, filteredTopAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_trending_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_trending_anime), ExploreCategoryType.TRENDING_ANIME, filteredTrendingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_rated_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_rated_anime), ExploreCategoryType.TOP_RATED_ANIME, filteredTopRatedAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_airing_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_airing_anime), ExploreCategoryType.AIRING_ANIME, filteredAiringAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_upcoming_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_upcoming_anime), ExploreCategoryType.UPCOMING_ANIME, filteredUpcomingAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_movie_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_movie_anime), ExploreCategoryType.MOVIE_ANIME, filteredMovieAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_seasonal_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_seasonal_anime), ExploreCategoryType.SEASONAL_ANIME, filteredSeasonalAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_newly_added_anime),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_newly_added_anime), ExploreCategoryType.NEWLY_ADDED_ANIME, filteredNewlyAddedAnime) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_airing_soon),
                            onClick = onOpenAiringCalendar
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Manga",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_manga), ExploreCategoryType.TOP_MANGA, filteredTopManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_trending_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_trending_manga), ExploreCategoryType.TRENDING_MANGA, filteredTrendingManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_top_rated_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_top_rated_manga), ExploreCategoryType.TOP_RATED_MANGA, filteredTopRatedManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_publishing_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_publishing_manga), ExploreCategoryType.PUBLISHING_MANGA, filteredPublishingManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_newly_added_manga),
                            onClick = { onSeeAllSection(context.getString(R.string.explore_newly_added_manga), ExploreCategoryType.NEWLY_ADDED_MANGA, filteredNewlyAddedManga) }
                        )
                    }
                    item {
                        ExploreCategoryChip(
                            label = stringResource(R.string.explore_read_manga),
                            onClick = onOpenMangaReader
                        )
                    }
                }
            }
        }
    }
}

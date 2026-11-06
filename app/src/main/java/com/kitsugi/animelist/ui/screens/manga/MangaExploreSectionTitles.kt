package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.MangaExploreSection

@Composable
fun mangaExploreSectionTitle(sectionId: String?): String = stringResource(
    when (sectionId) {
        MangaExploreSection.POPULAR -> R.string.manga_explore_popular
        MangaExploreSection.LATEST -> R.string.manga_explore_latest
        MangaExploreSection.NEWEST -> R.string.manga_explore_newest
        MangaExploreSection.RATING -> R.string.manga_explore_rating
        MangaExploreSection.ALPHABETICAL -> R.string.manga_explore_alphabetical
        MangaExploreSection.ADDED -> R.string.manga_explore_added
        MangaExploreSection.TRENDING_HOUR -> R.string.manga_explore_trending_hour
        MangaExploreSection.TRENDING_TODAY -> R.string.manga_explore_trending_today
        MangaExploreSection.TRENDING_WEEK -> R.string.manga_explore_trending_week
        MangaExploreSection.TRENDING_MONTH -> R.string.manga_explore_trending_month
        MangaExploreSection.TRENDING_YEAR -> R.string.manga_explore_trending_year
        else -> R.string.manga_explore_title
    },
)

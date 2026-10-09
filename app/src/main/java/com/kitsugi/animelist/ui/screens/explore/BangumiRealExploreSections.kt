package com.kitsugi.animelist.ui.screens.explore

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiHorizontalMediaSection

/** Bangumi's native REAL (live-action) shelves; kept separate from anime and anime films. */
fun LazyListScope.bangumiRealExploreSections(
    viewModel: ExploreViewModel,
    tvShows: List<JikanSearchResult>,
    movies: List<JikanSearchResult>,
    isAlreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onSeeAllSection: (title: String, categoryType: ExploreCategoryType, results: List<JikanSearchResult>) -> Unit,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    context: android.content.Context
) {
    item {
        val title = stringResource(R.string.explore_bangumi_tv)
        KitsugiHorizontalMediaSection(
            title = title,
            results = tvShows,
            isLoading = viewModel.isLoading,
            alreadyInList = isAlreadyInList,
            getMediaEntry = getMediaEntry,
            onItemClick = onItemClick,
            onLongClickItem = onLongClickItem,
            onSeeAllClick = {
                onSeeAllSection(context.getString(R.string.explore_bangumi_tv), ExploreCategoryType.BANGUMI_TV, tvShows)
            },
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            blurAdultMedia = blurAdultMedia
        )
        Spacer(modifier = Modifier.height(26.dp))
    }

    item {
        val title = stringResource(R.string.explore_bangumi_movies)
        KitsugiHorizontalMediaSection(
            title = title,
            results = movies,
            isLoading = viewModel.isLoading,
            alreadyInList = isAlreadyInList,
            getMediaEntry = getMediaEntry,
            onItemClick = onItemClick,
            onLongClickItem = onLongClickItem,
            onSeeAllClick = {
                onSeeAllSection(context.getString(R.string.explore_bangumi_movies), ExploreCategoryType.BANGUMI_MOVIES, movies)
            },
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            blurAdultMedia = blurAdultMedia
        )
    }
}

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

/**
 * Kaynağın yerel "ek tür" rafları (Bangumi REAL raflarıyla aynı fikir):
 * Manhwa & Manhua ile Noveller & Light Novel. Yalnızca dolu gelen raflar
 * gösterilir; kaynak bu türleri desteklemiyorsa (ör. Kitsu) bölüm gizlenir.
 */
fun LazyListScope.nativeKindExploreSections(
    viewModel: ExploreViewModel,
    manhwaManhua: List<JikanSearchResult>,
    novels: List<JikanSearchResult>,
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
    if (manhwaManhua.isNotEmpty()) {
        item {
            val title = stringResource(R.string.explore_manhwa_manhua)
            KitsugiHorizontalMediaSection(
                title = title,
                results = manhwaManhua,
                isLoading = viewModel.isLoading,
                alreadyInList = isAlreadyInList,
                getMediaEntry = getMediaEntry,
                onItemClick = onItemClick,
                onLongClickItem = onLongClickItem,
                onSeeAllClick = {
                    onSeeAllSection(context.getString(R.string.explore_manhwa_manhua), ExploreCategoryType.MANHWA_MANHUA, manhwaManhua)
                },
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia
            )
            Spacer(modifier = Modifier.height(26.dp))
        }
    }

    if (novels.isNotEmpty()) {
        item {
            val title = stringResource(R.string.explore_novels)
            KitsugiHorizontalMediaSection(
                title = title,
                results = novels,
                isLoading = viewModel.isLoading,
                alreadyInList = isAlreadyInList,
                getMediaEntry = getMediaEntry,
                onItemClick = onItemClick,
                onLongClickItem = onLongClickItem,
                onSeeAllClick = {
                    onSeeAllSection(context.getString(R.string.explore_novels), ExploreCategoryType.NOVELS, novels)
                },
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia
            )
            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}

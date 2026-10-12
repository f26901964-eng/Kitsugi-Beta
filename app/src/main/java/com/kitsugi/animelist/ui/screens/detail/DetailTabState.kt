package com.kitsugi.animelist.ui.screens.detail

/** UI pager index reserved for the media-video library tab. It does not fetch detail-tab data. */
internal const val DETAIL_VIDEO_PAGE_INDEX = 2

/**
 * Maps the visible pager index to the legacy lazy-data index used by detail ViewModels.
 * The Videos page sits after Images but has no remote tab-state request, so pages after it
 * shift back by one when asking the ViewModel to load data.
 */
internal fun detailDataTabIndexForPage(pageIndex: Int): Int? = when {
    pageIndex == DETAIL_VIDEO_PAGE_INDEX -> null
    pageIndex > DETAIL_VIDEO_PAGE_INDEX -> pageIndex - 1
    else -> pageIndex
}

sealed class DetailTabState<out T> {
    data object Loading : DetailTabState<Nothing>()
    data object Error : DetailTabState<Nothing>()
    data class Success<out T>(val data: T) : DetailTabState<T>()
}

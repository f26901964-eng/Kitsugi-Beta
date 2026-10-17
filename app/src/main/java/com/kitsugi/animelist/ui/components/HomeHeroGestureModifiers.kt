package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

// HorizontalPager owns drag, velocity, cancellation and snapping. Only read its offset
// during drawing (indicators); never dispatch raw deltas or wrap a drag at the boundaries.
internal fun heroPageVisibility(pagerState: PagerState, page: Int): Float =
    heroPageVisibility(pagerState.currentPage, pagerState.currentPageOffsetFraction, page)

internal fun heroPageVisibility(currentPage: Int, currentPageOffsetFraction: Float, page: Int): Float =
    (1f - abs((currentPage - page) + currentPageOffsetFraction)).coerceIn(0f, 1f)

package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.data.remote.JikanSearchResult

/** Keep the visible shelf items when the full-page refresh fails or ranks them differently. */
internal fun mergeSourceSearchResults(
    visible: List<JikanSearchResult>,
    fetched: List<JikanSearchResult>
): List<JikanSearchResult> = (visible + fetched).distinctBy { "${it.source.lowercase()}_${it.type}_${it.malId}" }

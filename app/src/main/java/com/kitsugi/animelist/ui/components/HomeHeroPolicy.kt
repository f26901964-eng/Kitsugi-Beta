package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.model.MediaType

/** Books (including manga, manhwa/manhua and novels) never enter a video showcase. */
internal fun isHomeHeroMedia(item: JikanSearchResult): Boolean =
    item.type != MediaType.Manga && item.bangumiSubjectType != 1 && item.title.isNotBlank()

/** Stable across artwork loads; provider, media namespace and extension URL are all significant. */
internal fun homeHeroItemKey(item: JikanSearchResult): String =
    "${item.source.trim().lowercase()}:${item.type.name}:${item.malId}:${item.tmdbId ?: 0}:${item.cs3Url.orEmpty()}"

/** A poster is NOT a fallback for a home hero, even on a portrait phone. */
internal fun homeHeroBackdropUrl(item: JikanSearchResult): String? {
    if (!isHomeHeroMedia(item)) return null
    return KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(item.backdropUrl, item.imageUrl, item.source)
        ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
}

/** Check decoded dimensions as well as URL hints: a mislabeled portrait must not slip through. */
internal fun isHorizontalHomeHeroArtwork(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width > height

/** Keep existing pages in place when a slower source finishes loading. New pages append. */
internal fun stableHomeHeroOrder(
    previous: List<JikanSearchResult>,
    next: List<JikanSearchResult>
): List<JikanSearchResult> {
    val remaining = next.associateByTo(LinkedHashMap()) { homeHeroItemKey(it) }
    return buildList {
        previous.forEach { old -> remaining.remove(homeHeroItemKey(old))?.let { add(it) } }
        addAll(remaining.values)
    }
}

/** At most seven usable indicators, even when ALL supplies many source/section highlights. */
internal fun homeHeroIndicatorIndices(itemCount: Int, currentPage: Int, windowSize: Int = 7): List<Int> {
    if (itemCount <= 0 || windowSize <= 0) return emptyList()
    val count = minOf(itemCount, windowSize)
    val start = (currentPage.coerceIn(0, itemCount - 1) - count / 2).coerceIn(0, itemCount - count)
    return (start until start + count).toList()
}

/** Autoplay reverses at the edges, rather than animating through every page from last to first. */
internal fun homeHeroAutoDirection(currentPage: Int, itemCount: Int, previousDirection: Int): Int = when {
    itemCount <= 1 -> 0
    currentPage <= 0 -> 1
    currentPage >= itemCount - 1 -> -1
    else -> if (previousDirection < 0) -1 else 1
}

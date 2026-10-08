package com.kitsugi.animelist.data.remote

/**
 * Shikimori's `censored=false` means that adult-rated entries may be returned; it does not
 * mean every returned entry is adult. Only Rx/Hentai records are treated as +18, matching
 * the app's blur policy (R and R+ titles remain visible without an adult warning).
 */
internal fun isShikimoriAdultContent(
    rating: String?,
    genreNames: Iterable<String> = emptyList()
): Boolean {
    val normalizedRating = rating.orEmpty().trim().lowercase()
    return normalizedRating == "rx" ||
        normalizedRating.contains("hentai") ||
        genreNames.any { it.contains("hentai", ignoreCase = true) }
}

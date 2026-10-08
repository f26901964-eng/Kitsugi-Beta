package com.kitsugi.animelist.data.remote

/** Kitsu `R18` ratings (including variants such as `R18+`) are explicit/adult content. */
internal fun isKitsuAdultContent(ageRating: String?, ageRatingGuide: String? = null): Boolean =
    ageRating.orEmpty().trim().startsWith("R18", ignoreCase = true) ||
        ageRatingGuide.orEmpty().contains("hentai", ignoreCase = true)

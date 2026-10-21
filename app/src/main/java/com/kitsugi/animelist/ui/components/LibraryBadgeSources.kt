package com.kitsugi.animelist.ui.components

import java.util.Locale

/** Canonical identities prevent a primary provider or its aliases appearing twice. */
internal fun canonicalLibraryBadgeSource(source: String): String =
    when (val normalized = source.trim().lowercase(Locale.ROOT)) {
        "al" -> "anilist"
        "jikan", "myanimelist", "jikan (mal)", "mal (jikan)" -> "mal"
        "shiki" -> "shikimori"
        "bgm" -> "bangumi"
        "themoviedb", "tmdb_network", "tmdb-network", "tmdbnetwork" -> "tmdb"
        else -> normalized
    }

internal fun additionalLibraryBadgeSources(primary: String, sources: List<String>?): List<String> {
    val canonicalPrimary = canonicalLibraryBadgeSource(primary)
    return sources.orEmpty()
        .map(::canonicalLibraryBadgeSource)
        .filter { it.isNotBlank() && it != canonicalPrimary }
        .distinct()
}

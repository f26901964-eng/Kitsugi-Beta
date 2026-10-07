package com.kitsugi.animelist.data.remote

/**
 * Builds Stremio stream-resource identifiers from a canonical IMDb/Kitsu ID.
 * Movie IDs must not be suffixed with season/episode; series IDs must include both.
 */
data class StremioVideoLookup(
    val contentType: String,
    val imdbVideoId: String?,
    val kitsuVideoId: String?
)

object StremioVideoId {
    fun forContent(
        isMovie: Boolean,
        imdbId: String?,
        kitsuId: Int?,
        season: Int,
        episode: Int
    ): StremioVideoLookup {
        val type = if (isMovie) "movie" else "series"
        val safeImdbId = imdbId?.trim()?.takeIf { it.isNotEmpty() }
        val safeKitsuId = kitsuId?.takeIf { it > 0 }

        return if (isMovie) {
            StremioVideoLookup(
                contentType = type,
                imdbVideoId = safeImdbId,
                kitsuVideoId = safeKitsuId?.let { "kitsu:$it" }
            )
        } else {
            val safeSeason = season.coerceAtLeast(1)
            val safeEpisode = episode.coerceAtLeast(1)
            StremioVideoLookup(
                contentType = type,
                imdbVideoId = safeImdbId?.let { "$it:$safeSeason:$safeEpisode" },
                kitsuVideoId = safeKitsuId?.let { "kitsu:$it:$safeEpisode" }
            )
        }
    }
}

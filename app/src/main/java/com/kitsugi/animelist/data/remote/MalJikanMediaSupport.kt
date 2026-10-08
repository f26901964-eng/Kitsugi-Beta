package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType

/** Shared identity and media-type rules for MyAnimeList and its Jikan API mirror. */
internal object MalJikanMediaSupport {
    private const val MAX_REAL_MAL_ID = 100_000_000

    fun isMalSource(source: String): Boolean =
        source.equals("mal", ignoreCase = true) ||
            source.equals("jikan", ignoreCase = true) ||
            source.equals("myanimelist", ignoreCase = true)

    fun canonicalSource(source: String): String =
        if (isMalSource(source)) "jikan" else source.lowercase()

    /**
     * MAL/Jikan result IDs are already MAL IDs and therefore take precedence over any
     * optional cross-source mapping. For all other providers, only an explicitly resolved
     * real MAL ID is safe to use; their external IDs live in different namespaces.
     */
    fun resolveMalId(source: String, externalId: Int?, realMalId: Int?): Int? {
        val candidates = if (isMalSource(source)) {
            listOf(externalId, realMalId)
        } else {
            listOf(realMalId)
        }
        return candidates.firstNotNullOfOrNull { id ->
            id?.takeIf { it in 1 until MAX_REAL_MAL_ID }
        }
    }

    /** Jikan has only anime and manga endpoints; anime films/TV still use /anime/. */
    fun jikanEndpoint(mediaType: MediaType): String =
        if (mediaType == MediaType.Manga) "manga" else "anime"

    /** AniList represents films and TV anime as ANIME, not MANGA. */
    fun aniListMediaType(mediaType: MediaType): String =
        if (mediaType == MediaType.Manga) "MANGA" else "ANIME"
}

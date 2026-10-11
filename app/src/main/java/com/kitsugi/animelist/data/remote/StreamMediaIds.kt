package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/** The source IDs needed by the stream resolver, kept in their own namespaces. */
internal data class StreamMediaIds(
    val malId: Int? = null,
    val aniListId: Int? = null,
    val tmdbId: Int? = null,
    val bangumiId: Int? = null
)

private const val MAL_ID_LIMIT = 100_000_000
private const val ANILIST_OFFSET = 100_000_000

/**
 * Convert a search/detail result into stream IDs without treating its generic `malId`
 * field as a MAL ID when it actually contains a TMDB, Simkl, Bangumi, or Shikimori ID.
 */
internal fun JikanSearchResult.toStreamMediaIds(
    resolvedRealMalId: Int? = realMalId,
    resolvedTmdbId: Int? = tmdbId
): StreamMediaIds {
    val normalizedSource = source.trim().lowercase(Locale.ROOT)
    val safeRealMalId = resolvedRealMalId?.takeIf { it in 1 until MAL_ID_LIMIT }
    val safeTmdbId = resolvedTmdbId?.takeIf { it > 0 }
    val storedAniListId = malId
        .takeIf { normalizedSource == "anilist" && it in (ANILIST_OFFSET + 1) until (ANILIST_OFFSET * 2) }
        ?.minus(ANILIST_OFFSET)

    return when (normalizedSource) {
        // TMDB-backed results store the TMDB ID in JikanSearchResult.malId as a legacy
        // stable card key. Never send that value to ARM as a MyAnimeList ID.
        "tmdb" -> StreamMediaIds(
            malId = safeRealMalId,
            tmdbId = safeTmdbId ?: tmdbId?.takeIf { it > 0 } ?: malId.takeIf { it > 0 }
        )

        // Simkl's `malId` slot is the Simkl ID; only `realMalId` is safe for MAL lookup.
        "simkl" -> StreamMediaIds(malId = safeRealMalId, tmdbId = safeTmdbId)

        // Kitsu results use an offset stable ID so StreamViewModel can decode the Kitsu ID.
        "kitsu" -> StreamMediaIds(
            malId = KitsuIdNamespace.stableIdOrNull(malId) ?: safeRealMalId,
            tmdbId = safeTmdbId
        )

        "anilist" -> StreamMediaIds(
            malId = safeRealMalId ?: malId.takeIf { it in 1 until MAL_ID_LIMIT },
            aniListId = storedAniListId,
            tmdbId = safeTmdbId
        )

        // Bangumi uses a 500M stable-ID namespace. Keep the raw subject ID in its own
        // field so the resolver can perform Bangumi's title/year-aware cross-reference;
        // never reinterpret that value as MAL/TMDB.
        "bangumi" -> StreamMediaIds(
            malId = safeRealMalId,
            tmdbId = safeTmdbId,
            bangumiId = BangumiIdNamespace.rawIdOrNull(malId)
        )

        // Shikimori has its own ID namespace; only an explicitly resolved MAL ID is safe.
        "shikimori" -> StreamMediaIds(malId = safeRealMalId, tmdbId = safeTmdbId)

        // Jikan/MAL results store a real MAL ID directly.
        "mal", "jikan", "myanimelist" -> StreamMediaIds(
            malId = safeRealMalId ?: malId.takeIf { it in 1 until MAL_ID_LIMIT },
            tmdbId = safeTmdbId
        )

        // Be conservative for any future source: accept only an explicitly resolved MAL
        // ID or a value in the real MAL range, never a local database row ID/offset ID.
        else -> StreamMediaIds(
            malId = safeRealMalId ?: malId.takeIf { it in 1 until MAL_ID_LIMIT },
            aniListId = storedAniListId,
            tmdbId = safeTmdbId
        )
    }
}

/**
 * MediaEntry.id is a Room row ID, not a media-provider ID. Build stream IDs only from
 * the source-specific external-ID field(s) and resolved detail metadata.
 */
internal fun MediaEntry.toStreamMediaIds(
    resolvedRealMalId: Int? = null,
    resolvedTmdbId: Int? = tmdbId
): StreamMediaIds {
    val normalizedSource = source.trim().lowercase(Locale.ROOT)
    val storedId = malId
    val safeRealMalId = resolvedRealMalId?.takeIf { it in 1 until MAL_ID_LIMIT }
    val safeTmdbId = resolvedTmdbId?.takeIf { it > 0 }
    val storedAniListId = storedId
        ?.takeIf { normalizedSource == "anilist" && it in (ANILIST_OFFSET + 1) until (ANILIST_OFFSET * 2) }
        ?.minus(ANILIST_OFFSET)

    return when (normalizedSource) {
        "tmdb" -> StreamMediaIds(
            malId = safeRealMalId,
            tmdbId = safeTmdbId
        )

        "bangumi" -> StreamMediaIds(
            malId = safeRealMalId,
            tmdbId = safeTmdbId,
            bangumiId = BangumiIdNamespace.rawIdOrNull(storedId)
        )

        "shikimori" -> StreamMediaIds(malId = safeRealMalId, tmdbId = safeTmdbId)

        "anilist" -> StreamMediaIds(
            malId = safeRealMalId ?: storedId?.takeIf { it in 1 until MAL_ID_LIMIT },
            aniListId = storedAniListId,
            tmdbId = safeTmdbId
        )

        "kitsu" -> StreamMediaIds(
            malId = KitsuIdNamespace.stableIdOrNull(storedId)
                ?: safeRealMalId
                ?: KitsuIdNamespace.realMalIdOf(storedId),
            tmdbId = safeTmdbId
        )

        "simkl" -> StreamMediaIds(
            malId = safeRealMalId ?: storedId?.takeIf {
                it in 1 until MAL_ID_LIMIT && it != simklId
            },
            tmdbId = safeTmdbId
        )

        else -> StreamMediaIds(
            malId = safeRealMalId ?: storedId?.takeIf { it in 1 until MAL_ID_LIMIT },
            aniListId = storedAniListId,
            tmdbId = safeTmdbId
        )
    }
}

/** Infer the media category for legacy callers that have not started passing it yet. */
internal fun inferStreamMediaType(
    mediaType: MediaType? = null,
    isMovie: Boolean = false,
    malId: Int? = null,
    aniListId: Int? = null,
    tmdbId: Int? = null
): MediaType? = mediaType ?: when {
    isMovie -> MediaType.Movie
    tmdbId != null && tmdbId > 0 && malId == null && aniListId == null -> MediaType.TvShow
    malId != null && malId > 0 -> MediaType.Anime
    aniListId != null && aniListId > 0 -> MediaType.Anime
    else -> null
}

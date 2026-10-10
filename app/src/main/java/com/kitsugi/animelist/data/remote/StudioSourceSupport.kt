package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/** Identity helpers for studio/company IDs shared by detail chips and detail loading. */
internal object StudioSourceSupport {
    /** TMDB network IDs live in a different namespace from production-company IDs. */
    const val TMDB_NETWORK_SOURCE = "tmdb_network"

    private val detailSources = setOf("jikan", "anilist", "bangumi", "tmdb", TMDB_NETWORK_SOURCE, "shikimori")
    private val legalCompanySuffixes = setOf(
        "inc", "incorporated", "co", "company", "ltd", "limited", "corp", "corporation", "llc", "gmbh"
    )

    fun canonicalSource(source: String?): String? {
        val value = source?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when (value) {
            "shiki" -> "shikimori"
            "bgm" -> "bangumi"
            "themoviedb" -> "tmdb"
            "tmdb-network", "tmdbnetwork" -> TMDB_NETWORK_SOURCE
            TMDB_NETWORK_SOURCE -> TMDB_NETWORK_SOURCE
            else -> MalJikanMediaSupport.canonicalSource(value)
        }
    }

    /**
     * Prefer the source attached to the studio itself. Studio IDs are provider-specific,
     * and cross-source detail enrichment can attach a TMDB studio to an AniList title (or
     * vice versa). Only fall back to the parent media source for older records that have no
     * studio source metadata.
     */
    fun resolveClickSource(
        studioSource: String?,
        mediaSource: String,
        mediaType: MediaType,
        role: StudioRole? = null
    ): String {
        val studioProvider = canonicalSource(studioSource)
        val mediaProvider = canonicalSource(mediaSource)
        val resolvedProvider = when {
            studioProvider != null && studioProvider in detailSources -> studioProvider
            mediaProvider != null && mediaProvider in detailSources -> mediaProvider
            else -> {
                // Legacy sources that don't expose a studio endpoint can still carry
                // cross-provider records; use the conventional media fallback only when
                // both records omit a supported provider namespace.
                when (mediaType) {
                    MediaType.Movie, MediaType.TvShow -> "tmdb"
                    MediaType.Anime, MediaType.Manga -> "jikan"
                }
            }
        }

        // TMDB TV networks and production companies have independent IDs and endpoints.
        // Passing a network's ID to /company/{id} can 404 or show a different organization.
        // Older persisted Simkl/AniList/etc. details may omit the chip source; their network
        // IDs came from TMDB enrichment, except Bangumi's own person-linked companies.
        return when {
            role == StudioRole.NETWORK && resolvedProvider == "tmdb" -> TMDB_NETWORK_SOURCE
            role == StudioRole.NETWORK && studioProvider == null && mediaProvider != "bangumi" -> TMDB_NETWORK_SOURCE
            else -> resolvedProvider
        }
    }

    /**
     * Compare names before showing a fetched/cached Jikan detail for a clicked chip. Legal
     * company suffixes such as "Inc." are normalized away, while distinct companies such as
     * Aniplex and BONES remain different.
     */
    fun namesMatch(expected: String?, actual: String?): Boolean {
        val expectedName = normalizeName(expected)
        if (expectedName.isEmpty()) return true
        val actualName = normalizeName(actual)
        if (actualName.isEmpty()) return false
        return expectedName == actualName
    }

    fun normalizeName(value: String?): String = value.orEmpty()
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() && it !in legalCompanySuffixes }
        .joinToString(separator = "")
}

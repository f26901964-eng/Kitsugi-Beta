package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/** Identity helpers for studio/company IDs shared by detail chips and detail loading. */
internal object StudioSourceSupport {
    private val detailSources = setOf("jikan", "anilist", "bangumi", "tmdb", "shikimori")
    private val legalCompanySuffixes = setOf(
        "inc", "incorporated", "co", "company", "ltd", "limited", "corp", "corporation", "llc", "gmbh"
    )

    fun canonicalSource(source: String?): String? {
        val value = source?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return when (value) {
            "shiki" -> "shikimori"
            "bgm" -> "bangumi"
            "themoviedb" -> "tmdb"
            else -> MalJikanMediaSupport.canonicalSource(value)
        }
    }

    /**
     * Prefer the source attached to the studio itself. Studio IDs are provider-specific,
     * and cross-source detail enrichment can attach a TMDB studio to an AniList title (or
     * vice versa). Only fall back to the parent media source for older records that have no
     * studio source metadata.
     */
    fun resolveClickSource(studioSource: String?, mediaSource: String, mediaType: MediaType): String {
        val studioProvider = canonicalSource(studioSource)
        if (studioProvider != null && studioProvider in detailSources) return studioProvider

        val mediaProvider = canonicalSource(mediaSource)
        if (mediaProvider != null && mediaProvider in detailSources) return mediaProvider

        // Legacy sources that don't expose a studio endpoint can still carry cross-provider
        // studio records; those should already have an explicit source. Keep a conservative
        // media-type fallback for old/untyped chips so they do not become dead ends.
        return when (mediaType) {
            MediaType.Movie, MediaType.TvShow -> "tmdb"
            MediaType.Anime, MediaType.Manga -> "jikan"
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

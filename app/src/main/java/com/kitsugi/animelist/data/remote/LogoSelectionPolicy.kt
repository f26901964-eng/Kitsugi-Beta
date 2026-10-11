package com.kitsugi.animelist.data.remote

/** A logo image together with the metadata needed to choose it across providers. */
internal data class LogoCandidate(
    val url: String,
    val language: String?,
    /** Null means a title-wide image; otherwise the source's season number. */
    val season: String? = null,
    /** Provider-specific quality signal (Fanart likes or TMDB votes). */
    val score: Double = 0.0,
    /** Lower values preserve the existing provider preference when language/scope tie. */
    val sourcePriority: Int = 0,
)

/**
 * One policy for hero logos and gallery ordering, irrespective of whether the image came
 * from TMDB or Fanart.tv. Provider order must never outweigh the user's language choice.
 */
internal object LogoSelectionPolicy {
    const val FANART_SOURCE_PRIORITY = 0
    const val TMDB_SOURCE_PRIORITY = 1

    private val neutralCodes = setOf("", "00", "null", "und", "none", "undefined")
    private val romanizedAliases = setOf("romaji", "romanji", "romanized", "romanised", "latin")
    private val cjkLanguages = setOf("ja", "zh")

    /**
     * Ranking for poster/logo metadata:
     * selected language → English → unlabelled/romanized → Japanese/Chinese → other.
     * APIs expose only an ISO language tag, not the script used inside the artwork. An
     * explicit `Latn` script or a neutral/unknown tag is therefore the best romanized tier
     * we can identify without OCR; `ja`/`zh` remain the final language fallback.
     */
    fun languageRank(language: String?, preferredLanguage: String?): Int {
        val imageLanguage = normalizeLanguage(language)
        val preferred = normalizeLanguage(preferredLanguage)
        val imagePrimary = imageLanguage.substringBefore('-')
        val preferredPrimary = preferred.substringBefore('-')

        return when {
            preferredPrimary.isNotBlank() &&
                (imageLanguage == preferred || imagePrimary == preferredPrimary) -> 0
            imagePrimary == "en" -> 1
            imageLanguage in neutralCodes || imagePrimary in neutralCodes -> 2
            imageLanguage in romanizedAliases ||
                imageLanguage.split('-').drop(1).any { it == "latn" } -> 2
            imagePrimary in cjkLanguages -> 3
            else -> 4
        }
    }

    /** Select a logo by language first, then target-season specificity, provider, quality. */
    fun select(
        candidates: Iterable<LogoCandidate>,
        preferredLanguage: String?,
        seasonNumber: Int? = null,
    ): LogoCandidate? {
        val wantedSeason = seasonNumber?.takeIf { it > 0 }?.toString()
        val eligible = candidates.asSequence()
            .filter { it.url.isNotBlank() }
            .filter { candidate ->
                val candidateSeason = candidate.season?.trim()?.takeIf { it.isNotBlank() }
                when {
                    wantedSeason == null -> candidateSeason == null
                    candidateSeason == null -> true
                    candidateSeason == wantedSeason -> true
                    else -> false
                }
            }
            .toList()

        return eligible.minWithOrNull(
            compareBy<LogoCandidate> { languageRank(it.language, preferredLanguage) }
                .thenBy { seasonRank(it.season, wantedSeason) }
                .thenBy { it.sourcePriority }
                .thenByDescending { it.score }
                .thenBy { it.url }
        )
    }

    /** Gallery order: selected-season image → title-wide image → another season. */
    fun gallerySeasonRank(season: String?, seasonNumber: Int?): Int =
        seasonRank(season, seasonNumber?.takeIf { it > 0 }?.toString())

    /** Only candidates explicitly tagged for this season; used by the season-only API. */
    fun selectSeasonOnly(
        candidates: Iterable<LogoCandidate>,
        preferredLanguage: String?,
        seasonNumber: Int,
        preferredLanguageOnly: Boolean = false,
    ): LogoCandidate? {
        val season = seasonNumber.takeIf { it > 0 }?.toString() ?: return null
        val matching = candidates.filter { it.season?.trim() == season }
        val allowed = if (preferredLanguageOnly) {
            matching.filter { languageRank(it.language, preferredLanguage) == 0 }
        } else matching
        return select(allowed, preferredLanguage, seasonNumber)
    }

    /** TMDB defaults image results to English and textless artwork; explicitly request our fallback tiers. */
    fun tmdbImageLanguages(preferredLanguage: String?): List<String> = buildList {
        val preferred = normalizeLanguage(preferredLanguage).substringBefore('-')
        if (preferred.length in 2..3 && preferred !in neutralCodes && preferred !in romanizedAliases) {
            add(preferred)
        }
        add("en")
        add("null")
        add("ja")
        add("zh")
        add("ko")
    }.distinct()

    private fun seasonRank(season: String?, wantedSeason: String?): Int {
        val normalizedSeason = season?.trim()?.takeIf { it.isNotBlank() }
        return when {
            wantedSeason == null -> if (normalizedSeason == null) 0 else 2
            normalizedSeason == wantedSeason -> 0
            normalizedSeason == null -> 1
            else -> 2
        }
    }

    private fun normalizeLanguage(language: String?): String {
        val normalized = language
            ?.trim()
            ?.lowercase()
            ?.replace('_', '-')
            .orEmpty()
        return when {
            normalized.isBlank() -> ""
            normalized == "jp" -> "ja"
            else -> normalized
        }
    }
}

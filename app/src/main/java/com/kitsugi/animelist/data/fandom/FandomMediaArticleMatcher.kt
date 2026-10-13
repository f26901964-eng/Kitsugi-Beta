package com.kitsugi.animelist.data.fandom

/**
 * Selects the article representing a provider's media item from exact and wiki-search results.
 * All decisions are deterministic/pure so title collisions and disambiguation pages are testable.
 */
internal object FandomMediaArticleMatcher {

    data class Match(
        val lookup: FandomTitleLookup,
        val confidence: FandomConfidence,
        val score: Double
    )

    fun choose(sourceTitles: List<String>, candidates: Collection<FandomTitleLookup>): Match? {
        val titles = sourceTitles
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy(FandomNameVariants::normalizeForCompare)
        if (titles.isEmpty()) return null

        val viable = candidates
            .filter { it.exists && !it.isDisambiguation && !it.resolvedTitle.isNullOrBlank() }
            .distinctBy { it.resolvedTitle?.let(FandomNameVariants::normalizeForCompare) }
            .mapNotNull { lookup ->
                val requestedKey = FandomNameVariants.normalizeForCompare(lookup.requested)
                val exactRequested = titles.any { FandomNameVariants.normalizeForCompare(it) == requestedKey }
                val titleScore = if (exactRequested) {
                    1.0
                } else {
                    FandomNameMatcher.titleSimilarity(lookup.resolvedTitle.orEmpty(), titles)
                }
                if (titleScore < MIN_SCORE) return@mapNotNull null
                Match(
                    lookup = lookup,
                    confidence = if (exactRequested || titleScore >= CERTAIN_SCORE) {
                        FandomConfidence.CERTAIN
                    } else {
                        FandomConfidence.PROBABLE
                    },
                    score = titleScore
                )
            }
            .sortedByDescending(Match::score)

        val best = viable.firstOrNull() ?: return null
        val runnerUp = viable.getOrNull(1) ?: return best
        // Do not silently pick one of two similarly named adaptations. Direct title equality
        // wins only when it resolved to a single non-disambiguation article.
        if (best.score - runnerUp.score < AMBIGUITY_MARGIN) return null
        return best
    }

    private const val MIN_SCORE = 0.55
    private const val CERTAIN_SCORE = 0.88
    private const val AMBIGUITY_MARGIN = 0.06
}

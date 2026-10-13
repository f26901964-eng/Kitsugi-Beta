package com.kitsugi.animelist.data.fandom

/** Conservative evidence gate for discovering a work's wiki from its own Fandom article. */
internal object FandomMediaIdentityProof {

    private const val MIN_ARTICLES = 20
    private const val MIN_WIKI_TITLE_SCORE = 0.65
    private val YEAR_IN_TITLE = Regex("(?<!\\d)(18\\d{2}|19\\d{2}|20\\d{2}|21\\d{2}|2200)(?!\\d)")

    fun hasStrongArticleProof(
        hit: FandomCommunityHit,
        siteInfo: FandomSiteInfo,
        sourceTitles: List<String>,
        creators: List<String>,
        isManga: Boolean,
        releaseYear: Int?,
        articleMatch: FandomMediaArticleMatcher.Match
    ): Boolean {
        if (FandomCandidateSelector.isBlocked(hit.wikiId) || FandomCandidateSelector.isBlocked(siteInfo.wikiId)) return false
        if (articleMatch.confidence != FandomConfidence.CERTAIN) return false
        if ((siteInfo.articles ?: hit.pageCount ?: 0) < MIN_ARTICLES) return false
        if (FandomNameMatcher.hubScore(hit.hub, isManga) < 0.5) return false

        val rootTitles = FandomNameVariants.baseTitleVariants(sourceTitles)
        val identityTitles = (rootTitles + FandomNameVariants.mediaTitleVariants(sourceTitles) + sourceTitles)
            .distinctBy(FandomNameVariants::normalizeForCompare)
        val wikiName = siteInfo.siteName?.takeIf { it.isNotBlank() } ?: hit.name
        val titleScore = FandomNameMatcher.titleSimilarity(wikiName, identityTitles, creators)
        if (titleScore < MIN_WIKI_TITLE_SCORE) return false

        // A one-word title (e.g. Monster) is too collision-prone to identify a general-purpose
        // wiki by name alone. Require the source year in the matched Fandom article in that case.
        val bestRoot = rootTitles.maxByOrNull { title ->
            FandomNameMatcher.titleSimilarity(wikiName, listOf(title), creators)
        } ?: sourceTitles.firstOrNull().orEmpty()
        val distinctiveWords = FandomNameVariants.tokens(bestRoot).distinct().size
        val year = releaseYear?.takeIf { it in 1800..2200 }
            ?: sourceTitles.asSequence().mapNotNull { YEAR_IN_TITLE.find(it)?.value?.toIntOrNull() }.firstOrNull()
            ?: return distinctiveWords >= 2
        val articleEvidence = buildString {
            append(articleMatch.lookup.resolvedTitle.orEmpty()).append(' ')
            append(articleMatch.lookup.evidenceText()).append(' ')
            append(hit.description.orEmpty())
        }
        val hasYear = Regex("(?<!\\d)$year(?!\\d)").containsMatchIn(articleEvidence)
        return distinctiveWords >= 2 || hasYear
    }
}

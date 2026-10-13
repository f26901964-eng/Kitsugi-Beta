package com.kitsugi.animelist.data.fandom

/**
 * Character-vote fallback (pure logic, no network).
 *
 * A work's characters carry Wikidata P6262 Fandom article IDs. If enough characters point to
 * the same community, it is likely the work's wiki. Each character votes once for its preferred
 * page language (root/English, then Turkish, then another localized community).
 */
internal object WikidataCharacterVotes {

    /** Maximum characters queried for one work (from reverse P1441 links). */
    const val MAX_CHARACTERS = 50

    /** Minimum votes required for a community to win. */
    const val MIN_VOTES = 2

    /** Minimum article count for the winning community (reject empty/tiny wikis). */
    const val MIN_WIKI_ARTICLES = 20

    /**
     * P6262 values per character → wiki ID → vote count. A character votes at most once.
     */
    fun tally(perCharacter: List<List<String>>): Map<String, Int> {
        val votes = LinkedHashMap<String, Int>()
        for (articles in perCharacter) {
            val wikis = articles
                .mapNotNull { FandomUrls.parseArticleId(it)?.first }
                .filterNot(FandomCandidateSelector::isBlocked)
                .distinct()
            val wiki = FandomCandidateSelector.choose(wikis)?.primary ?: continue
            votes[wiki] = (votes[wiki] ?: 0) + 1
        }
        return votes
    }

    /**
     * Winner: at least [MIN_VOTES] and a strict lead over second place. Ties and low vote counts
     * are ambiguous and intentionally return null.
     */
    fun winner(votes: Map<String, Int>): String? {
        val ranked = votes.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        )
        val top = ranked.firstOrNull() ?: return null
        if (top.value < MIN_VOTES) return null
        val second = ranked.getOrNull(1)?.value ?: 0
        return if (top.value > second) top.key else null
    }
}

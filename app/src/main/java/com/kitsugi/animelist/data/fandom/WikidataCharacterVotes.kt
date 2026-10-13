package com.kitsugi.animelist.data.fandom

/**
 * Karakter oylaması (saf mantık, ağ yok).
 *
 * Bir yapımın karakterleri Wikidata'da P6262 (Fandom sayfa kimliği) taşır; karakterlerin
 * çoğu aynı wiki'ye işaret ediyorsa o wiki yapımın wiki'sidir. Her karakter en fazla bir
 * oy verir (aynı wiki'ye birden çok sayfa = tek oy), dil wikileri ve engelli wikiler yok sayılır.
 */
internal object WikidataCharacterVotes {

    /** Bir yapım için sorgulanan en fazla karakter (ters P1441 sonucundan). */
    const val MAX_CHARACTERS = 50

    /** Bir wiki'nin kazanabilmesi için gereken en az oy. */
    const val MIN_VOTES = 2

    /** Kazanan wiki'nin siteinfo makale sayısı alt sınırı (küçük/boş wiki'leri eler). */
    const val MIN_WIKI_ARTICLES = 20

    /**
     * Karakter başına P6262 değerleri → wiki kimliği → oy sayısı.
     * Her karakter bir wiki için en fazla bir oy verir.
     */
    fun tally(perCharacter: List<List<String>>): Map<String, Int> {
        val votes = LinkedHashMap<String, Int>()
        for (articles in perCharacter) {
            val wikis = articles
                .mapNotNull { FandomUrls.parseArticleId(it)?.first }
                .filter { FandomUrls.isRoot(it) && !FandomCandidateSelector.isBlocked(it) }
                .distinct()
            for (wiki in wikis) votes[wiki] = (votes[wiki] ?: 0) + 1
        }
        return votes
    }

    /**
     * Kazanan wiki: en az [MIN_VOTES] oy almış ve ikinciden **kesin** fazla oyu olan.
     * Eşitlik veya yetersiz oy durumunda null (belirsizlik kabul edilmez).
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

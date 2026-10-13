package com.kitsugi.animelist.data.fandom

/**
 * Wiki adayları arasından seçim kuralları (saf fonksiyonlar).
 *
 *  1. Çapraz/genel wikiler (Heroes, Villains, Isekai, dublaj, fanon, VS Battles…) elenir.
 *  2. Dil öneki olmayan ana (İngilizce) wiki önceliklidir; yoksa Türkçe, sonra diğer
 *     yerelleştirilmiş sürümler kabul edilir.
 *  3. Seçilen dil grubunda birden fazla aday varsa makale sayısı büyük olan kazanır.
 */
object FandomCandidateSelector {

    /** Bir yapıma ait olmayan, çok seriyi kapsayan veya hayran içerikli wikiler. */
    private val BLOCKED_NAMES = setOf(
        "hero", "heroes", "villains", "villain", "isekai", "cosplay", "dubbing", "dubdb",
        "netflix", "tropedia", "allthetropes", "animanga", "anime", "manga", "vsbattles",
        "vsdebating", "powerlisting", "superpower", "listofdeaths", "deathbattle",
        "deathbattlefanon", "characterprofile", "aesthetics", "dengeki", "video-games",
        "videogames", "nintendo", "crossover", "fanon", "fanfiction", "lgbtqia-characters",
        "disney", "dreamworks", "cartoons", "jokebattles", "joke-battles", "fictionalcrossover",
        "the-anime-database", "anime-characters-database", "myanimelist", "anilist"
    )

    private val BLOCKED_PATTERNS = listOf(
        Regex("fanon"),
        Regex("fanfic"),
        Regex("dub(b)?(ing|er|ers|db)"),
        Regex("doblaje"),
        Regex("dublagem"),
        Regex("doppiaggio"),
        Regex("roleplay"),
        Regex("(^|-)rp($|-)"),
        Regex("crossover"),
        Regex("deathbattle"),
        Regex("powerlisting"),
        Regex("vs-?battles")
    )

    data class Choice(val primary: String, val turkish: String?)

    fun isBlocked(wikiId: String): Boolean {
        val parts = FandomUrls.parse(wikiId) ?: return true
        val name = parts.name
        if (name in BLOCKED_NAMES) return true
        return BLOCKED_PATTERNS.any { it.containsMatchIn(name) }
    }

    /** Geçerli ve engellenmemiş adaylar (kanonik, tekrarsız, sıra korunur). */
    fun usable(candidates: Iterable<String>): List<String> =
        candidates.mapNotNull { FandomUrls.normalize(it) }
            .distinct()
            .filterNot { isBlocked(it) }

    fun rootCandidates(candidates: Iterable<String>): List<String> =
        usable(candidates).filter { FandomUrls.isRoot(it) }

    fun needsArticleCounts(candidates: Iterable<String>): Boolean = preferredCandidates(usable(candidates)).size > 1

    private fun preferredCandidates(all: List<String>): List<String> {
        val roots = all.filter { FandomUrls.isRoot(it) }
        if (roots.isNotEmpty()) return roots
        val turkish = all.filter { FandomUrls.languageOf(it) == "tr" }
        return turkish.ifEmpty { all }
    }

    /**
     * @param articleCounts canonical wiki ID → article count (used to rank multiple candidates
     *        within the preferred language group).
     */
    fun choose(candidates: Iterable<String>, articleCounts: Map<String, Int> = emptyMap()): Choice? {
        val all = usable(candidates)
        if (all.isEmpty()) return null
        val roots = all.filter { FandomUrls.isRoot(it) }
        val turkish = all.filter { FandomUrls.languageOf(it) == "tr" }
        val preferred = preferredCandidates(all)
        val primary = when {
            preferred.isEmpty() -> return null
            roots.isNotEmpty() -> preferred.maxByOrNull { articleCounts[it] ?: -1 } ?: preferred.first()
            turkish.isNotEmpty() -> preferred.maxByOrNull { articleCounts[it] ?: -1 } ?: preferred.first()
            else -> preferred.maxByOrNull { articleCounts[it] ?: -1 } ?: preferred.first()
        }
        val primaryParts = FandomUrls.parse(primary)
        val turkishPair = if (primaryParts?.lang == "tr") {
            null
        } else {
            turkish.firstOrNull { FandomUrls.parse(it)?.name == primaryParts?.name }
        }
        return Choice(primary, turkishPair)
    }
}

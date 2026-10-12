package com.kitsugi.animelist.data.fandom

/**
 * Wiki adayları arasından seçim kuralları (saf fonksiyonlar).
 *
 *  1. Çapraz/genel wikiler (Heroes, Villains, Isekai, dublaj, fanon, VS Battles…) elenir.
 *  2. Dil öneki olmayan ana (İngilizce) wiki önceliklidir; yoksa Türkçe sürüm kabul edilir.
 *     Diğer dillerdeki wikiler tek başına seçilmez.
 *  3. Birden fazla ana aday varsa makale sayısı büyük olan kazanır; eşitlikte ilk sıradaki.
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

    fun needsArticleCounts(candidates: Iterable<String>): Boolean = rootCandidates(candidates).size > 1

    /**
     * @param articleCounts kanonik wiki kimliği → makale sayısı (yalnızca birden fazla
     *        ana aday varken gerekir).
     */
    fun choose(candidates: Iterable<String>, articleCounts: Map<String, Int> = emptyMap()): Choice? {
        val all = usable(candidates)
        if (all.isEmpty()) return null
        val roots = all.filter { FandomUrls.isRoot(it) }
        val turkish = all.filter { FandomUrls.languageOf(it) == "tr" }
        val primary = when {
            roots.isNotEmpty() -> roots.maxByOrNull { articleCounts[it] ?: -1 } ?: roots.first()
            turkish.isNotEmpty() -> turkish.first()
            else -> return null
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

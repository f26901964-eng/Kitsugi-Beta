package com.kitsugi.animelist.data.fandom

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Wikidata erişimi.
 *
 * Birincil yol tek bir küçük SPARQL sorgusudur (kimlik araması + 2 adımlı üst kayıt
 * yürüyüşü). Sorgu servisi (WDQS) yanıt vermezse Action API yedeğine düşülür:
 * `haswbstatement` araması + `wbgetentities` (daha ağır ama çok kararlı).
 */
internal object WikidataClient {

    private const val API = "https://www.wikidata.org/w/api.php"
    private const val SPARQL = "https://query.wikidata.org/sparql"
    private const val MAX_PARENTS_PER_LEVEL = 8
    /** Medya kimliği başına en fazla sorgulanan özellik (AniList, MAL, Kitsu, Bangumi, TMDB…). */
    private const val MAX_LOOKUPS = 6
    /** Bulunacak en fazla medya QID'i (aynı adı taşıyan farklı öğeler için sınır). */
    private const val MAX_MEDIA_QIDS = 3

    private val PROPERTY = Regex("^P[1-9]\\d{0,6}$")
    private val VALUE = Regex("^[A-Za-z0-9._:/-]{1,64}$")

    // ── Yapım → wiki ─────────────────────────────────────────────────────────

    suspend fun resolveMedia(lookups: List<Pair<String, String>>, startQids: List<String>): WikidataResolution? {
        val query = WikidataParsers.buildMediaQuery(lookups, startQids) ?: return null
        val viaSparql = WikidataParsers.parseMediaResults(sparql(query))
        if (viaSparql != null) return viaSparql
        return resolveMediaViaApi(lookups, startQids)
    }

    private suspend fun resolveMediaViaApi(
        lookups: List<Pair<String, String>>,
        startQids: List<String>
    ): WikidataResolution? {
        val starts = LinkedHashSet<String>(startQids.filter { WikidataParsers.isQid(it) })
        if (starts.isEmpty()) {
            var lookupFailed = false
            // One provider's Wikidata/API failure must not suppress every other identity
            // (for example MAL can still resolve when the AniList property lookup times out).
            for ((property, value) in lookups.take(MAX_LOOKUPS)) {
                val found = findQids(property, value, limit = 2)
                if (found == null) {
                    lookupFailed = true
                    continue
                }
                found.forEach { qid ->
                    if (starts.size < MAX_MEDIA_QIDS) starts += qid
                }
                if (starts.size >= MAX_MEDIA_QIDS) break
            }
            if (starts.isEmpty() && lookupFailed) return null
        }
        if (starts.isEmpty()) return WikidataResolution.NONE

        val wikis = sortedMapOf<Int, MutableList<String>>()
        val articles = LinkedHashSet<String>()
        val visited = LinkedHashSet<String>(starts)
        val nodes = LinkedHashSet<String>()

        val level0 = getEntities(starts) ?: return null
        level0.values.forEach { entity ->
            addAll(wikis, 0, entity.fandomWikiIds)
            articles += entity.fandomArticleIds
        }
        var frontier = level0.values.flatMap { it.parents }
        for (depth in 1..2) {
            if (wikis.isNotEmpty()) break
            val next = frontier.filter { visited.add(it) }.take(MAX_PARENTS_PER_LEVEL)
            if (next.isEmpty()) break
            nodes += next
            val level = getEntities(next) ?: break
            level.values.forEach { entity -> addAll(wikis, depth, entity.fandomWikiIds) }
            frontier = level.values.flatMap { it.parents }
        }
        return WikidataResolution(starts, wikis, articles.toList(), nodes)
    }

    private fun addAll(target: MutableMap<Int, MutableList<String>>, depth: Int, values: List<String>) {
        if (values.isEmpty()) return
        val list = target.getOrPut(depth) { mutableListOf() }
        values.forEach { value -> if (value !in list) list.add(value) }
    }

    // ── Karakter → P6262 ─────────────────────────────────────────────────────

    /** AniList (P11736) / MAL (P4085) karakter kimliği → Fandom sayfa kimlikleri. */
    suspend fun characterArticles(lookups: List<Pair<String, String>>): List<String> {
        val query = WikidataParsers.buildCharacterQuery(lookups) ?: return emptyList()
        WikidataParsers.parseCharacterResults(sparql(query))?.let { return it }
        // Yedek: Action API
        for ((property, value) in lookups) {
            val qids = findQids(property, value, limit = 2) ?: return emptyList()
            if (qids.isEmpty()) continue
            val entities = getEntities(qids) ?: return emptyList()
            val articles = entities.values.flatMap { it.fandomArticleIds }.distinct()
            if (articles.isNotEmpty()) return articles
        }
        return emptyList()
    }

    // ── Düşük seviye çağrılar ────────────────────────────────────────────────

    /** `haswbstatement:P8729=11757` → QID'ler. Ağ hatasında null. */
    suspend fun findQids(property: String, value: String, limit: Int = 3): List<String>? {
        if (!PROPERTY.matches(property) || !VALUE.matches(value)) return emptyList()
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", "haswbstatement:$property=$value")
            .addQueryParameter("srnamespace", "0")
            .addQueryParameter("srlimit", limit.coerceIn(1, 10).toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
            .toString()
        val body = FandomHttp.get(url) ?: return null
        return WikidataParsers.parseSearchQids(body)
    }

    /** `wbgetentities&props=claims` (50'lik parçalar). Ağ hatasında null. */
    suspend fun getEntities(qids: Collection<String>): Map<String, WikidataEntityClaims>? {
        val ids = qids.filter { WikidataParsers.isQid(it) }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, WikidataEntityClaims>()
        for (chunk in ids.chunked(50)) {
            val url = API.toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbgetentities")
                .addQueryParameter("ids", chunk.joinToString("|"))
                .addQueryParameter("props", "claims")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .build()
                .toString()
            val body = FandomHttp.get(url) ?: return null
            result.putAll(WikidataParsers.parseEntities(body))
        }
        return result
    }

    // ── Karakter oylaması (medya → karakterler → P6262) ──────────────────────

    /**
     * Kimlik çiftlerinden (AniList/MAL/Kitsu/Bangumi/TMDB…) medya QID'lerini bulur.
     * En fazla [MAX_LOOKUPS] özellik sorgulanır; kimlik sırası öncelik sırasıdır.
     * Ağ hatasında null (çağıran sonucu kalıcı yazmaz).
     */
    suspend fun findMediaQids(lookups: List<Pair<String, String>>): List<String>? {
        val found = LinkedHashSet<String>()
        var anyLookupFailed = false
        for ((property, value) in lookups.take(MAX_LOOKUPS)) {
            val qids = findQids(property, value, limit = 2)
            if (qids == null) {
                anyLookupFailed = true
                continue
            }
            found += qids
            if (found.size >= MAX_MEDIA_QIDS) break
        }
        return when {
            found.isNotEmpty() -> found.take(MAX_MEDIA_QIDS)
            anyLookupFailed -> null
            else -> emptyList()
        }
    }

    /**
     * Bir medya öğesinin "eserde yer alan" (P1441 ters bağlantı) öğeleri → her birinin P6262 değerleri.
     * Karakter olmayan öğeler (şarkı, mekân…) P6262 taşımadığı için sonuca girmez.
     * Önce tek SPARQL sorgusu; WDQS yanıt vermezse `wbgetentities` yedeği. Ağ hatasında null.
     */
    suspend fun characterArticlesOf(mediaQid: String): List<List<String>>? {
        if (!WikidataParsers.isQid(mediaQid)) return emptyList()
        val members = searchStatementQids("P1441", mediaQid, limit = WikidataCharacterVotes.MAX_CHARACTERS)
            ?: return null
        if (members.isEmpty()) return emptyList()
        val byItem: Map<String, List<String>> =
            WikidataParsers.buildItemArticlesQuery(members)
                ?.let { query -> WikidataParsers.parseItemArticles(sparql(query)) }
                ?: run {
                    val entities = getEntities(members) ?: return null
                    entities.mapValues { (_, claims) -> claims.fandomArticleIds }
                }
        return members.mapNotNull { qid -> byItem[qid]?.takeIf { it.isNotEmpty() } }
    }

    /** `haswbstatement:<property>=<value>` — sonuçlar [limit] ile sınırlı (MediaWiki srlimit ≤ 50). */
    private suspend fun searchStatementQids(property: String, value: String, limit: Int): List<String>? {
        if (!PROPERTY.matches(property) || !VALUE.matches(value)) return emptyList()
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", "haswbstatement:$property=$value")
            .addQueryParameter("srnamespace", "0")
            .addQueryParameter("srlimit", limit.coerceIn(1, 50).toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
            .toString()
        val body = FandomHttp.get(url) ?: return null
        return WikidataParsers.parseSearchQids(body)
    }

    private suspend fun sparql(query: String): String? {
        val url = SPARQL.toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("query", query)
            .build()
            .toString()
        return FandomHttp.get(url, accept = "application/sparql-results+json")
    }
}

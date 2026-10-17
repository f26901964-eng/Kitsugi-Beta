package com.kitsugi.animelist.data.fandom

import org.json.JSONObject

/** Bir Wikidata kaydının Fandom ve Vikipedi ile ilgili beyanları. */
data class WikidataEntityClaims(
    val qid: String,
    /** P4073 — Fandom wiki kimliği (`jujutsu-kaisen`, `tr.onepiece`). */
    val fandomWikiIds: List<String>,
    /** P6262 — Fandom sayfa kimliği (`jujutsu-kaisen:Satoru_Gojo`). */
    val fandomArticleIds: List<String>,
    /** Üst kayıtlar (dizi, franchise, uyarlandığı eser, önceki/sonraki sezon). */
    val parents: List<String>,
    /** Sitelinks: `enwiki` → "Crash Landing on You", `trwiki` → "Crash Landing on You (dizi)". */
    val wikipediaTitles: Map<String, String> = emptyMap(),
    /** P345 — IMDb kimliği (`tt1234567`), küçük harf. Yoksa null. */
    val imdbId: String? = null
)

/**
 * Wikidata zincirinin sonucu.
 *
 * @property wikisByDepth 0 = kaydın kendisi, 1–2 = üst kayıtlar üzerinden bulunan P4073 değerleri.
 * @property articleIds   kaydın kendisindeki P6262 değerleri (yalnızca son çare olarak kullanılır).
 * @property visitedNodes gezilen üst kayıtlar (QID anahtarlı eşleme tablosu için).
 * @property wikipediaTitles kaydın doğrudan Vikipedi dillerindeki madde başlıkları (`enwiki`, `trwiki`).
 */
data class WikidataResolution(
    val starts: Set<String>,
    val wikisByDepth: Map<Int, List<String>>,
    val articleIds: List<String>,
    val visitedNodes: Set<String>,
    val wikipediaTitles: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean
        get() = wikisByDepth.values.all { it.isEmpty() } && articleIds.isEmpty() && wikipediaTitles.isEmpty()

    companion object {
        val NONE = WikidataResolution(emptySet(), emptyMap(), emptyList(), emptySet(), emptyMap())
    }
}

/** Wikidata Action API ve SPARQL yanıtlarının ayrıştırılması + SPARQL sorgu üretimi. */
object WikidataParsers {

    /** Öncelik sırasıyla üst kayıt özellikleri. */
    val PARENT_PROPERTIES = listOf("P179", "P8345", "P144", "P361", "P155", "P156")

    private val QID = Regex("^Q[1-9]\\d{0,11}$")
    private val IMDB_TITLE_ID = Regex("(?i)^tt[0-9]{5,12}$")
    private val PROPERTY = Regex("^P[1-9]\\d{0,6}$")
    private val VALUE = Regex("^[A-Za-z0-9._:/-]{1,64}$")
    private const val ENTITY_PREFIX = "http://www.wikidata.org/entity/"

    fun isQid(value: String?): Boolean = value != null && QID.matches(value)

    // ── Action API ───────────────────────────────────────────────────────────

    /** `list=search&srsearch=haswbstatement:P8729=11757` → QID listesi. */
    fun parseSearchQids(json: String?): List<String> {
        val search = parseJsonObject(json)?.optJSONObject("query")?.optJSONArray("search")
        return search.objects().mapNotNull { obj -> obj.str("title")?.takeIf { isQid(it) } }.distinct()
    }

    /** `action=wbgetentities&props=claims|sitelinks` → QID → beyanlar. */
    fun parseEntities(json: String?): Map<String, WikidataEntityClaims> {
        val entities = parseJsonObject(json)?.optJSONObject("entities") ?: return emptyMap()
        val result = LinkedHashMap<String, WikidataEntityClaims>()
        val keys = entities.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entity = entities.optJSONObject(key) ?: continue
            if (entity.has("missing")) continue
            val qid = entity.str("id") ?: key
            if (!isQid(qid)) continue
            val claims = entity.optJSONObject("claims") ?: JSONObject()
            val sitelinks = entity.optJSONObject("sitelinks")
            result[qid] = WikidataEntityClaims(
                qid = qid,
                fandomWikiIds = stringValues(claims, "P4073"),
                fandomArticleIds = stringValues(claims, "P6262"),
                parents = PARENT_PROPERTIES.flatMap { itemValues(claims, it) }.distinct().filter { it != qid },
                wikipediaTitles = parseSitelinks(sitelinks),
                imdbId = stringValues(claims, "P345")
                    .firstOrNull { IMDB_TITLE_ID.matches(it.trim()) }
                    ?.trim()
                    ?.lowercase(java.util.Locale.ROOT)
            )
        }
        return result
    }

    private fun parseSitelinks(sitelinks: JSONObject?): Map<String, String> {
        if (sitelinks == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        val keys = sitelinks.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val siteObj = sitelinks.optJSONObject(key) ?: continue
            val title = siteObj.str("title")?.trim() ?: continue
            if (title.isNotEmpty()) {
                result[key] = title
            }
        }
        return result
    }

    /** Kullanımdan kaldırılmış (deprecated) beyanlar atılır; tercih edilenler öne alınır. */
    private fun statements(claims: JSONObject, property: String): List<JSONObject> {
        val all = claims.optJSONArray(property).objects().filter { it.str("rank") != "deprecated" }
        val preferred = all.filter { it.str("rank") == "preferred" }
        return preferred + all.filter { it.str("rank") != "preferred" }
    }

    private fun stringValues(claims: JSONObject, property: String): List<String> =
        statements(claims, property).mapNotNull { statement ->
            val snak = statement.optJSONObject("mainsnak") ?: return@mapNotNull null
            if (snak.str("snaktype") != "value") return@mapNotNull null
            snak.optJSONObject("datavalue")?.str("value")
        }.distinct()

    private fun itemValues(claims: JSONObject, property: String): List<String> =
        statements(claims, property).mapNotNull { statement ->
            val snak = statement.optJSONObject("mainsnak") ?: return@mapNotNull null
            if (snak.str("snaktype") != "value") return@mapNotNull null
            val value = snak.optJSONObject("datavalue")?.optJSONObject("value") ?: return@mapNotNull null
            value.str("id")?.takeIf { isQid(it) }
                ?: value.optLong("numeric-id", 0L).takeIf { it > 0L }?.let { "Q$it" }
        }

    // ── SPARQL ───────────────────────────────────────────────────────────────

    private const val PARENT_PATH = "(wdt:P179|wdt:P8345|wdt:P144|wdt:P361|wdt:P155|wdt:P156)"

    /**
     * Kimlik araması + 2 adımlı üst kayıt yürüyüşünü tek sorguda yapan SPARQL.
     *
     * @param lookups (özellik, değer) çiftleri: `("P8729", "11757")`
     * @param startQids doğrudan bilinen Wikidata kayıtları (ör. TMDB'den gelen)
     * @return geçerli girdi yoksa null. Değerler sıkı desenle doğrulanır (sorgu enjeksiyonu yok).
     */
    fun buildMediaQuery(lookups: List<Pair<String, String>>, startQids: List<String>): String? {
        val starts = mutableListOf<String>()
        startQids.filter { isQid(it) }.distinct().forEach { starts += "{ BIND(wd:$it AS ?start) }" }
        lookups.filter { (property, value) -> PROPERTY.matches(property) && VALUE.matches(value) }
            .distinct()
            .forEach { (property, value) -> starts += "{ ?start wdt:$property \"$value\" . }" }
        if (starts.isEmpty()) return null
        return """
            SELECT DISTINCT ?start ?depth ?wiki ?article ?node WHERE {
              ${starts.joinToString(" UNION ")}
              {
                BIND(0 AS ?depth) ?start wdt:P4073 ?wiki .
              } UNION {
                BIND(1 AS ?depth) ?start $PARENT_PATH ?node . ?node wdt:P4073 ?wiki .
              } UNION {
                BIND(2 AS ?depth) ?start $PARENT_PATH ?mid . ?mid $PARENT_PATH ?node . ?node wdt:P4073 ?wiki .
              } UNION {
                BIND(3 AS ?depth) ?start wdt:P6262 ?article .
              } UNION {
                BIND(4 AS ?depth) ?start $PARENT_PATH ?node .
              } UNION {
                BIND(5 AS ?depth) ?start $PARENT_PATH ?mid . ?mid $PARENT_PATH ?node .
              }
            } LIMIT 300
        """.trimIndent()
    }

    /** Karakter kimliği → P6262 sayfa kimlikleri. */
    fun buildCharacterQuery(lookups: List<Pair<String, String>>): String? {
        val starts = lookups.filter { (property, value) -> PROPERTY.matches(property) && VALUE.matches(value) }
            .distinct()
            .map { (property, value) -> "{ ?item wdt:$property \"$value\" . }" }
        if (starts.isEmpty()) return null
        return """
            SELECT DISTINCT ?article WHERE {
              ${starts.joinToString(" UNION ")}
              ?item wdt:P6262 ?article .
            } LIMIT 50
        """.trimIndent()
    }

    /** [buildMediaQuery] yanıtı. Yanıt ayrıştırılamazsa null (→ Action API yedeği). */
    fun parseMediaResults(json: String?): WikidataResolution? {
        val root = parseJsonObject(json) ?: return null
        val bindings = root.optJSONObject("results")?.optJSONArray("bindings") ?: return null
        val starts = LinkedHashSet<String>()
        val wikis = sortedMapOf<Int, MutableList<String>>()
        val articles = LinkedHashSet<String>()
        val nodes = LinkedHashSet<String>()
        for (binding in bindings.objects()) {
            entityValue(binding, "start")?.let { starts += it }
            val depth = bindingValue(binding, "depth")?.toIntOrNull() ?: continue
            entityValue(binding, "node")?.let { nodes += it }
            when (depth) {
                0, 1, 2 -> {
                    val wiki = bindingValue(binding, "wiki")
                    if (wiki != null) {
                        val list = wikis.getOrPut(depth) { mutableListOf() }
                        if (wiki !in list) list.add(wiki)
                    }
                }
                3 -> {
                    val article = bindingValue(binding, "article")
                    if (article != null) articles.add(article)
                }
                else -> Unit
            }
        }
        return WikidataResolution(starts, wikis, articles.toList(), nodes)
    }

    /** [buildCharacterQuery] yanıtı. Ayrıştırılamazsa null. */
    fun parseCharacterResults(json: String?): List<String>? {
        val root = parseJsonObject(json) ?: return null
        val bindings = root.optJSONObject("results")?.optJSONArray("bindings") ?: return null
        return bindings.objects().mapNotNull { bindingValue(it, "article") }.distinct()
    }

    /**
     * Verilen öğelerin P6262 (Fandom sayfa kimliği) değerleri — tek hafif sorgu (`VALUES`).
     * QID'ler doğrulanmadan sorguya girmez.
     */
    fun buildItemArticlesQuery(qids: List<String>): String? {
        val valid = qids.filter { isQid(it) }.distinct()
        if (valid.isEmpty()) return null
        val values = valid.joinToString(" ") { "wd:$it" }
        return """
            SELECT ?item ?article WHERE {
              VALUES ?item { $values }
              ?item wdt:P6262 ?article .
            }
        """.trimIndent()
    }

    /** [buildItemArticlesQuery] yanıtı: öğe QID'i → P6262 değerleri. Ayrıştırılamazsa null. */
    fun parseItemArticles(json: String?): Map<String, List<String>>? {
        val root = parseJsonObject(json) ?: return null
        val bindings = root.optJSONObject("results")?.optJSONArray("bindings") ?: return null
        val result = LinkedHashMap<String, MutableList<String>>()
        for (binding in bindings.objects()) {
            val item = entityValue(binding, "item") ?: continue
            val article = bindingValue(binding, "article") ?: continue
            val list = result.getOrPut(item) { mutableListOf() }
            if (article !in list) list.add(article)
        }
        return result
    }

    private fun bindingValue(binding: JSONObject, name: String): String? =
        binding.optJSONObject(name)?.str("value")

    private fun entityValue(binding: JSONObject, name: String): String? =
        bindingValue(binding, name)?.removePrefix(ENTITY_PREFIX)?.takeIf { isQid(it) }
}

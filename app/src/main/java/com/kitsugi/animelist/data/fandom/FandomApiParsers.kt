package com.kitsugi.animelist.data.fandom

import org.json.JSONArray
import org.json.JSONObject

/** `meta=siteinfo` özeti. [wikiId] yönlendirme sonrası gerçek wiki'dir. */
data class FandomSiteInfo(
    val wikiId: String,
    val siteName: String?,
    val lang: String?,
    val articles: Int?,
    val mainPage: String?
)

data class FandomPageImageBatch(
    val leadImage: FandomImage? = null,
    val fileTitles: List<String> = emptyList(),
    val continueToken: String? = null,
    val generalContinueToken: String? = null
)

/** `action=query&titles=…&redirects=1&prop=pageprops` sonucunda istenen bir başlık. */
data class FandomTitleLookup(
    val requested: String,
    /** Normalizasyon + yönlendirme sonrası sayfa başlığı; sayfa yoksa null. */
    val resolvedTitle: String?,
    val pageId: Long?,
    val infoboxJson: String?,
    val description: String?,
    val isDisambiguation: Boolean,
    /** Türkçe dil bağlantısı (wiki kimliği, sayfa başlığı) — `prop=langlinks&lllang=tr`. */
    val turkishLink: Pair<String, String>? = null,
    /** `prop=pageimages` thumbnail for related-article cards. */
    val thumbnailUrl: String? = null
) {
    val exists: Boolean
        get() = resolvedTitle != null

    /** Yerel ad doğrulaması için bilgi kutusu + özet metni. */
    fun evidenceText(): String {
        val infobox = FandomInfoboxParser.parse(infoboxJson)?.searchableText().orEmpty()
        return "$infobox ${description.orEmpty()}"
    }
}

/** MediaWiki (Fandom) ve Fandom topluluk araması yanıtlarının ayrıştırılması. */
object FandomApiParsers {

    fun parseSiteInfo(requestedWikiId: String, json: String?): FandomSiteInfo? {
        val root = parseJsonObject(json) ?: return null
        if (root.has("error")) return null
        val query = root.optJSONObject("query") ?: return null
        val general = query.optJSONObject("general") ?: return null
        val statistics = query.optJSONObject("statistics")
        val server = general.str("server")
        val scriptPath = general.str("scriptpath").orEmpty()
        val realWikiId = server?.let { FandomUrls.wikiIdFromUrl("$it$scriptPath/") }
            ?: FandomUrls.normalize(requestedWikiId)
            ?: return null
        return FandomSiteInfo(
            wikiId = realWikiId,
            siteName = general.str("sitename"),
            lang = general.str("lang"),
            articles = statistics?.let { stats ->
                if (stats.isNull("articles")) null else stats.optInt("articles", -1).takeIf { it >= 0 }
            },
            mainPage = general.str("mainpage")
        )
    }

    fun parseTitleLookups(requested: List<String>, json: String?): Map<String, FandomTitleLookup> {
        val query = parseJsonObject(json)?.optJSONObject("query") ?: return emptyMap()
        val normalized = fromToMap(query.optJSONArray("normalized"))
        val redirects = fromToMap(query.optJSONArray("redirects"))
        val pagesByTitle = HashMap<String, JSONObject>()
        query.optJSONArray("pages").objects().forEach { page ->
            page.str("title")?.let { pagesByTitle[it] = page }
        }
        // formatversion=1 yedeği: sayfalar kimlik anahtarlı nesne olarak gelebilir.
        query.optJSONObject("pages")?.let { pages ->
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                page.str("title")?.let { pagesByTitle[it] = page }
            }
        }
        val result = LinkedHashMap<String, FandomTitleLookup>()
        for (title in requested) {
            val normalizedTitle = normalized[title] ?: title
            val finalTitle = redirects[normalizedTitle] ?: normalizedTitle
            val page = pagesByTitle[finalTitle]
            val exists = page != null && !page.has("missing") && !page.has("invalid") && page.optInt("ns", 0) == 0
            val props = if (exists) page?.optJSONObject("pageprops") else null
            result[title] = FandomTitleLookup(
                requested = title,
                resolvedTitle = if (exists) finalTitle else null,
                pageId = if (exists) page?.optLong("pageid", 0L)?.takeIf { it > 0L } else null,
                infoboxJson = props?.str("infoboxes"),
                // Fandom short descriptions are ideal when present; MediaWiki extracts keep
                // otherwise-valid articles useful on wikis that do not publish fandomdescription.
                description = props?.str("fandomdescription")
                    ?.takeIf { it.isNotBlank() }
                    ?: page?.str("extract")?.takeIf { it.isNotBlank() },
                isDisambiguation = props?.has("disambiguation") == true,
                turkishLink = if (exists) parseTurkishLink(page?.optJSONArray("langlinks")) else null,
                thumbnailUrl = if (exists) FandomUrls.normalizeImageUrl(page?.optJSONObject("thumbnail")?.str("source")) else null
            )
        }
        return result
    }

    /** `prop=images|pageimages` output: lead image, file-page titles and pagination tokens. */
    fun parsePageImageBatch(json: String?, pageTitle: String): FandomPageImageBatch {
        val root = parseJsonObject(json) ?: return FandomPageImageBatch()
        if (root.has("error")) return FandomPageImageBatch()
        val page = root.optJSONObject("query")?.optJSONArray("pages")?.objects()?.firstOrNull()
            ?: root.optJSONObject("query")?.optJSONObject("pages")?.let { pages ->
                val keys = pages.keys()
                if (keys.hasNext()) pages.optJSONObject(keys.next()) else null
            }
            ?: return FandomPageImageBatch()
        val lead = page.optJSONObject("thumbnail")?.str("source")
            ?.let(FandomUrls::normalizeImageUrl)
            ?.let { FandomImage(url = it, caption = pageTitle) }
        val files = page.optJSONArray("images").objects()
            .mapNotNull { it.str("title") }
            .distinct()
        val continuation = root.optJSONObject("continue")
        return FandomPageImageBatch(
            leadImage = lead,
            fileTitles = files,
            continueToken = continuation?.str("imcontinue"),
            generalContinueToken = continuation?.str("continue")
        )
    }

    /** `prop=imageinfo` results with normalized URLs and any attribution exposed by the wiki. */
    fun parseImageInfo(json: String?): List<FandomImage> {
        val root = parseJsonObject(json) ?: return emptyList()
        if (root.has("error")) return emptyList()
        val pages = root.optJSONObject("query")?.optJSONArray("pages")?.objects().orEmpty()
        val legacyPages = root.optJSONObject("query")?.optJSONObject("pages")
        val allPages = pages.ifEmpty {
            legacyPages?.let { obj ->
                buildList<JSONObject> {
                    val keys = obj.keys()
                    while (keys.hasNext()) obj.optJSONObject(keys.next())?.let(::add)
                }
            }.orEmpty()
        }
        return allPages.mapNotNull { page ->
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val url = FandomUrls.normalizeImageUrl(info.str("thumburl") ?: info.str("url")) ?: return@mapNotNull null
            val metadata = info.optJSONObject("extmetadata")
            fun metadataText(key: String): String? = metadata?.optJSONObject(key)?.str("value")
                ?.let(FandomHtmlText::inline)
                ?.takeIf { it.isNotBlank() }
            FandomImage(
                url = url,
                caption = metadataText("ImageDescription") ?: metadataText("ObjectName"),
                fileTitle = page.str("title"),
                artist = metadataText("Artist") ?: metadataText("Credit"),
                license = metadataText("LicenseShortName")
            )
        }.distinctBy { it.fileTitle ?: it.url }
    }

    private fun parseTurkishLink(links: JSONArray?): Pair<String, String>? {
        for (link in links.objects()) {
            if (!link.str("lang").equals("tr", ignoreCase = true)) continue
            val title = (link.str("title") ?: link.str("*")) ?: continue
            val wiki = FandomUrls.wikiIdFromUrl(link.str("url")) ?: continue
            return wiki to title
        }
        return null
    }

    private fun fromToMap(array: JSONArray?): Map<String, String> {
        val map = HashMap<String, String>()
        array.objects().forEach { entry ->
            val from = entry.optString("from")
            val to = entry.str("to")
            if (from.isNotEmpty() && to != null) map[from] = to
        }
        return map
    }

    /** `action=parse&prop=sections`. Şablon bölümleri (`T-1`) ve gizli bölümler atlanır. */
    fun parseSections(json: String?): List<FandomSectionRef>? {
        val root = parseJsonObject(json) ?: return null
        if (root.has("error")) return null
        val sections = root.optJSONObject("parse")?.optJSONArray("sections") ?: return emptyList()
        val result = mutableListOf<FandomSectionRef>()
        for (section in sections.objects()) {
            val index = section.optString("index").toIntOrNull() ?: continue
            val level = section.optString("level").toIntOrNull()
                ?: (section.optInt("toclevel", 1) + 1)
            val title = FandomHtmlText.inline(section.optString("line"))
            if (title.isBlank() || FandomSpoilerRules.isHiddenSection(title)) continue
            result += FandomSectionRef(index, title, level, FandomSpoilerRules.isSpoilerSection(title))
        }
        return result
    }

    /** `action=parse&prop=text` (formatversion=2 → metin; formatversion=1 → `{"*": …}`). */
    fun parseParsedText(json: String?): String? {
        val root = parseJsonObject(json) ?: return null
        if (root.has("error")) return null
        val parse = root.optJSONObject("parse") ?: return null
        return when (val text = parse.opt("text")) {
            is String -> text.takeIf { it.isNotBlank() }
            is JSONObject -> text.str("*")
            else -> null
        }
    }

    fun parseSearchTitles(json: String?): List<String> {
        val search = parseJsonObject(json)?.optJSONObject("query")?.optJSONArray("search")
        return search.objects().mapNotNull { it.str("title") }.distinct()
    }

    /** `services.fandom.com/unified-search/community-search` sonuçları. */
    fun parseCommunitySearch(json: String?): List<FandomCommunityHit> {
        val results = parseJsonObject(json)?.optJSONArray("results")
        val hits = mutableListOf<FandomCommunityHit>()
        for (item in results.objects()) {
            val wikiId = FandomUrls.wikiIdFromUrl(item.str("url")) ?: continue
            hits += FandomCommunityHit(
                wikiId = wikiId,
                name = item.str("name") ?: wikiId,
                language = item.str("language"),
                hub = item.str("hub"),
                pageCount = if (item.isNull("pageCount")) null else item.optInt("pageCount", -1).takeIf { it >= 0 },
                pageViews = if (item.isNull("pageViews")) null else item.optInt("pageViews", -1).takeIf { it >= 0 },
                description = item.str("description")
            )
        }
        return hits.distinctBy { it.wikiId }
    }

    fun parseExtUrlUsageFound(json: String?): Boolean {
        val usage = parseJsonObject(json)?.optJSONObject("query")?.optJSONArray("exturlusage")
        return usage != null && usage.length() > 0
    }

    /**
     * `prop=categoryinfo` sonucu: çözülmüş kategori başlığı → üye sayısı.
     * Olmayan (missing) kategoriler listede yer almaz; yönlendirmeler hedefe çözülür.
     */
    fun parseCategoryInfo(json: String?): Map<String, Int> {
        val pages = parseJsonObject(json)?.optJSONObject("query")?.optJSONArray("pages") ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (page in pages.objects()) {
            val title = page.str("title") ?: continue
            if (page.optBoolean("missing", false)) continue
            val info = page.optJSONObject("categoryinfo") ?: continue
            out[title] = info.optInt("size", 0).coerceAtLeast(0)
        }
        return out
    }

    /** `list=categorymembers` sonucu: ana ad alanındaki sayfa başlıkları. */
    fun parseCategoryMembers(json: String?): List<String> {
        val members = parseJsonObject(json)?.optJSONObject("query")?.optJSONArray("categorymembers")
            ?: return emptyList()
        return members.objects().mapNotNull { it.str("title") }.distinct()
    }

    /**
     * `continue` bloğundaki sonraki sayfa jetonu. Kategoride başka üye yoksa `null`
     * döner — "Devamını göster" düğmesinin görünürlüğü buna bağlıdır.
     */
    fun parseContinueToken(json: String?): String? =
        parseJsonObject(json)?.optJSONObject("continue")
            ?.str("cmcontinue")
            ?.takeIf { it.isNotBlank() }

    /** `list=search` sonucu: başlık + etiketlerden arındırılmış snippet. */
    fun parseSearchHits(json: String?): List<FandomSearchHit> {
        val search = parseJsonObject(json)?.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return search.objects().mapNotNull { item ->
            val title = item.str("title") ?: return@mapNotNull null
            val snippet = FandomHtmlText.inline(item.str("snippet")).takeIf { it.isNotBlank() }
            FandomSearchHit(title = title, snippet = snippet)
        }.distinctBy { it.title }
    }
}

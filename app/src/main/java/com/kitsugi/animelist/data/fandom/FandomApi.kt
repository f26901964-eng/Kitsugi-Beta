package com.kitsugi.animelist.data.fandom

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Fandom MediaWiki API ve topluluk araması çağrıları. */
internal object FandomApi {

    private const val COMMUNITY_SEARCH = "https://services.fandom.com/unified-search/community-search"
    private const val MAX_TITLES_PER_REQUEST = 50

    suspend fun siteInfo(wikiId: String): FandomSiteInfo? {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "meta" to "siteinfo",
                "siprop" to "general|statistics",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return null
        return FandomApiParsers.parseSiteInfo(wikiId, FandomHttp.get(url))
    }

    /**
     * Başlıkları birebir sorgular (wiki'nin kendi yönlendirmeleriyle); bilgi kutusu JSON'u
     * ve kısa özet de aynı istekte gelir. 50'den fazla başlık parçalara bölünür.
     */
    suspend fun lookupTitles(
        wikiId: String,
        titles: List<String>,
        withTurkishLink: Boolean = false
    ): Map<String, FandomTitleLookup> {
        val clean = titles.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 200 && !it.contains('|') }
            .distinct()
        if (clean.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, FandomTitleLookup>()
        for (chunk in clean.chunked(MAX_TITLES_PER_REQUEST)) {
            val params = mutableListOf(
                "action" to "query",
                "titles" to chunk.joinToString("|"),
                "redirects" to "1",
                "prop" to (if (withTurkishLink) "pageprops|langlinks" else "pageprops"),
                "ppprop" to "infoboxes|fandomdescription|disambiguation",
                "format" to "json",
                "formatversion" to "2"
            )
            if (withTurkishLink) {
                params += "lllang" to "tr"
                params += "llprop" to "url"
            }
            val url = FandomUrls.apiQuery(wikiId, params) ?: continue
            result.putAll(FandomApiParsers.parseTitleLookups(chunk, FandomHttp.get(url)))
        }
        return result
    }

    suspend fun sections(wikiId: String, title: String): List<FandomSectionRef>? {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "parse",
                "page" to title,
                "prop" to "sections",
                "redirects" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return null
        return FandomApiParsers.parseSections(FandomHttp.get(url))
    }

    suspend fun sectionHtml(wikiId: String, title: String, index: Int): String? {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "parse",
                "page" to title,
                "section" to index.toString(),
                "prop" to "text",
                "redirects" to "1",
                "disableeditsection" to "1",
                "disablelimitreport" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return null
        return FandomApiParsers.parseParsedText(FandomHttp.get(url))
    }

    /**
     * Kategori adaylarının varlığı ve sayfa sayısı — tek istekte toplu (`prop=categoryinfo`).
     * Anahtarlar yönlendirmeler çözüldükten sonraki başlıklardır (`Category:…`).
     */
    suspend fun categoryInfo(wikiId: String, categoryTitles: List<String>): Map<String, Int> {
        val clean = categoryTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_TITLES_PER_REQUEST)
        if (clean.isEmpty()) return emptyMap()
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "titles" to clean.joinToString("|"),
                "prop" to "categoryinfo",
                "redirects" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return emptyMap()
        return FandomApiParsers.parseCategoryInfo(FandomHttp.get(url))
    }

    /** Bir kategorinin ana ad alanındaki sayfaları (çip listesi). */
    suspend fun categoryMembers(wikiId: String, categoryTitle: String, limit: Int = 14): List<String> {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "categorymembers",
                "cmtitle" to categoryTitle,
                "cmnamespace" to "0",
                "cmlimit" to limit.coerceIn(1, 50).toString(),
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return emptyList()
        return FandomApiParsers.parseCategoryMembers(FandomHttp.get(url))
    }

    /** Wiki içi arama: başlık + kırpılmış özet (arama kartı sonuç listesi). */
    suspend fun searchHits(wikiId: String, query: String, limit: Int = 8): List<FandomSearchHit> {
        if (query.isBlank()) return emptyList()
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "search",
                "srsearch" to query,
                "srnamespace" to "0",
                "srlimit" to limit.coerceIn(1, 10).toString(),
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return emptyList()
        return FandomApiParsers.parseSearchHits(FandomHttp.get(url))
    }

    suspend fun searchPages(wikiId: String, query: String, limit: Int = 5): List<String> {
        if (query.isBlank()) return emptyList()
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "search",
                "srsearch" to query,
                "srnamespace" to "0",
                "srlimit" to limit.coerceIn(1, 10).toString(),
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return emptyList()
        return FandomApiParsers.parseSearchTitles(FandomHttp.get(url))
    }

    suspend fun communitySearch(query: String, lang: String = "en", limit: Int = 8): List<FandomCommunityHit> {
        if (query.isBlank()) return emptyList()
        val url = COMMUNITY_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("lang", lang)
            .addQueryParameter("limit", limit.coerceIn(1, 20).toString())
            .build()
            .toString()
        return FandomApiParsers.parseCommunitySearch(FandomHttp.get(url))
    }

    /** Wiki'de birebir dış bağlantı (ör. `anilist.co/anime/11757`) var mı? */
    suspend fun hasExternalLink(wikiId: String, urlWithoutProtocol: String): Boolean {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "exturlusage",
                "euquery" to urlWithoutProtocol,
                "euprotocol" to "https",
                "eunamespace" to "0",
                "eulimit" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return false
        return FandomApiParsers.parseExtUrlUsageFound(FandomHttp.get(url))
    }
}

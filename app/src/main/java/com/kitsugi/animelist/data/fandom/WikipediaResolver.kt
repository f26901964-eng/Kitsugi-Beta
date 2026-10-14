package com.kitsugi.animelist.data.fandom

import android.util.Log

/**
 * Vikipedi (Wikipedia) üzerinden yapım makalesi arama ve çözümleme motoru.
 *
 * Wikidata sitelinks kaydı bulunamayan Asya dizileri (K-Drama, C-Drama, J-Drama),
 * TV programları, filmler ve animeler için başlık ve tür varyantlarıyla arama yapar.
 */
internal object WikipediaResolver {

    private const val TAG = "WikipediaResolver"
    private const val MIN_SIMILARITY = 0.55
    private const val CERTAIN_SIMILARITY = 0.88

    /**
     * Verilen yapım sorgusu için Vikipedi üzerinde en uygun maddeyi arar ve doğrular.
     */
    suspend fun resolve(
        query: FandomMediaQuery,
        preferTurkish: Boolean = false,
        /** Bilinen Wikidata varlıkları (yapım ve üst kayıtları): başlık eşleşmesini doğrular. */
        expectedQids: Set<String> = emptySet()
    ): FandomWikiRef? {
        val titles = query.fallbackTitles
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy(FandomNameVariants::normalizeForCompare)
        if (titles.isEmpty()) return null

        val languages = if (preferTurkish) listOf("wikipedia.tr", "wikipedia.en") else listOf("wikipedia.en", "wikipedia.tr")

        for (wikiId in languages) {
            val ref = searchInWiki(wikiId, titles, query, expectedQids)
            if (ref != null) return ref
        }
        return null
    }

    private suspend fun searchInWiki(
        wikiId: String,
        titles: List<String>,
        query: FandomMediaQuery,
        expectedQids: Set<String>
    ): FandomWikiRef? {
        val searchQueries = buildSearchQueries(titles, query)
        val candidateTitles = LinkedHashSet<String>()

        for (searchQuery in searchQueries.take(8)) {
            val hits = try {
                FandomApi.searchPages(wikiId, searchQuery, limit = 4)
            } catch (e: Exception) {
                Log.w(TAG, "Vikipedi arama hatası ($wikiId/$searchQuery): ${e.message}")
                emptyList()
            }
            candidateTitles.addAll(hits)
            if (candidateTitles.size >= 12) break
        }

        if (candidateTitles.isEmpty()) return null

        val lookups = try {
            FandomApi.lookupTitles(wikiId, candidateTitles.toList(), withTurkishLink = false)
        } catch (e: Exception) {
            Log.w(TAG, "Vikipedi başlık doğrulama hatası ($wikiId): ${e.message}")
            emptyMap()
        }

        val match = FandomMediaArticleMatcher.choose(titles, lookups.values) ?: return null
        val matchedTitle = match.lookup.resolvedTitle ?: match.lookup.requested
        val siteName = if (wikiId == "wikipedia.tr") "Vikipedi" else "Wikipedia"

        // Kimlik denetimi: madde başka bir varlığa (aynı adlı başka yapım) aitse eşleşme
        // "kesin" sayılmaz; kullanıcıya onay kartı gösterilir. Kanıt yoksa davranış eskisi.
        val mismatch = identityMismatch(wikiId, matchedTitle, expectedQids)
        if (mismatch) {
            Log.d(TAG, "Vikipedi kimlik uyuşmazlığı: $wikiId/$matchedTitle (beklenen: $expectedQids)")
        }
        val confidence = when {
            mismatch -> FandomConfidence.PROBABLE
            match.score >= CERTAIN_SIMILARITY -> FandomConfidence.CERTAIN
            else -> FandomConfidence.PROBABLE
        }
        return FandomWikiRef(
            wikiId = wikiId,
            source = FandomMatchSource.NAME,
            confidence = confidence,
            siteName = siteName,
            score = match.score,
            pageTitle = matchedTitle
        )
    }

    /**
     * Maddenin Wikidata kimliği bilinen bir varlığa ait DEĞİL mi?
     *
     * Yalnızca kanıt varsa (maddenin kimliği okunabildiyse) `true` döner; ağ hatası ya da
     * kimliksiz madde durumunda mevcut puan tabanlı kabul korunur (yanlış "bulunamadı"
     * üretmemek için).
     */
    private suspend fun identityMismatch(
        wikiId: String,
        title: String,
        expectedQids: Set<String>
    ): Boolean {
        if (expectedQids.isEmpty() || !FandomUrls.isWikipedia(wikiId)) return false
        val item = try {
            FandomApi.pageWikidataItems(wikiId, listOf(title))[title]
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Vikipedi kimliği okunamadı ($wikiId/$title): ${e.message}")
            null
        }
        return item != null && item !in expectedQids
    }

    private fun buildSearchQueries(titles: List<String>, query: FandomMediaQuery): List<String> {
        val queries = LinkedHashSet<String>()
        val primary = titles.firstOrNull() ?: return emptyList()

        if (query.isTvShow) {
            queries += "$primary (TV series)"
            queries += "$primary (South Korean TV series)"
            queries += "$primary (series)"
            queries += "$primary (drama)"
            query.releaseYear?.let { queries += "$primary ($it TV series)" }
        } else if (query.isMovie) {
            queries += "$primary (film)"
            query.releaseYear?.let { queries += "$primary ($it film)" }
        } else if (query.isManga) {
            queries += "$primary (manga)"
            queries += "$primary (novel)"
        } else {
            queries += "$primary (anime)"
            queries += "$primary (TV series)"
        }

        titles.forEach { queries += it }
        return queries.toList()
    }
}

package com.kitsugi.animelist.data.fandom

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Yapım → Fandom wiki çözümleyici.
 *
 * Sıra: 1) eşleme tablosu, 2) Wikidata (kimlik + üst kayıtlar), 3) ilişki yürüyüşü
 * (önceki sezon, kaynak eser… yine kimlikle), 4) son çare: isimle aday + kanıtla doğrulama.
 * Önbellek ve kullanıcı tercihleri [FandomRepository] katmanındadır.
 *
 * @param isWikiRejected kullanıcının bu yapım için "yanlış" dediği wikiler hiçbir adımda seçilmez;
 *        isim yolunda sıradaki aday değerlendirilir.
 */
internal class FandomWikiResolver(
    private val context: Context,
    private val isWikiRejected: (String) -> Boolean = { false }
) {

    private data class ResolutionPick(
        val info: FandomSiteInfo,
        val viaOverride: Boolean,
        val articleTitle: String? = null
    )

    companion object {
        private const val TAG = "FandomWikiResolver"
        private const val MAX_RELATIONS = 4
        private const val MAX_ROOT_CANDIDATES = 8
        private const val MAX_LOCALIZED_CANDIDATES = 8
        private const val MAX_FINGERPRINT_CANDIDATES = 8
        private const val MAX_NATIVE_TITLE_CANDIDATES = 8
        private const val MAX_SLUG_PROBES = 8
        private const val MAX_SEARCH_QUERIES = 8
        /** Last-resort exact-ID checks remain deliberately bounded to protect Fandom's API. */
        private const val MAX_IDENTITY_LINK_CANDIDATES = 3
        private const val MAX_IDENTITY_LINKS = 10
        private const val MAX_ARTICLE_PROOF_CANDIDATES = 6
        /** Karakter oylamasında taranan en fazla medya QID'i (aynı yapımın farklı kayıtları). */
        private const val MAX_VOTE_MEDIA = 3

        /** siteinfo sonuçları (null = wiki yok/erişilemez) — süreç boyunca paylaşılır. */
        private val siteInfoCache = BoundedCache<String, FandomSiteInfo>("fandom_siteinfo", 96)
        private val missingWikis = BoundedCache<String, Long>("fandom_missing_wikis", 96)
        private const val MISSING_WIKI_TTL_MS = 30L * 60L * 1000L

        suspend fun siteInfoCached(wikiId: String): FandomSiteInfo? {
            val key = FandomUrls.normalize(wikiId) ?: return null
            siteInfoCache[key]?.let { return it }
            val missingAt = missingWikis[key]
            if (missingAt != null && System.currentTimeMillis() - missingAt < MISSING_WIKI_TTL_MS) return null
            val info = FandomApi.siteInfo(key)
            if (info == null) {
                missingWikis[key] = System.currentTimeMillis()
                return null
            }
            siteInfoCache[key] = info
            siteInfoCache[info.wikiId] = info
            return info
        }
    }

    suspend fun resolve(query: FandomMediaQuery, allowNameFallback: Boolean): FandomWikiRef? {
        val ids = bridgeIdsByTitle(query, completeIds(query))
        val overrides = FandomOverrides.table(context)

        overrides.lookup(ids, query.isManga)?.takeUnless { isWikiRejected(it) }?.let { wiki ->
            siteInfoCached(wiki)?.takeUnless { isWikiRejected(it.wikiId) }?.let { info ->
                return FandomWikiRef(info.wikiId, FandomMatchSource.OVERRIDE, FandomConfidence.CERTAIN, info.siteName)
            }
        }

        var foreignWikidataRef: FandomWikiRef? = null
        val lookups = FandomMediaIds.wikidataLookups(ids, query.isManga, query.isMovie)
        val starts = listOfNotNull(ids.wikidataId)
        if (lookups.isNotEmpty() || starts.isNotEmpty()) {
            val resolution = WikidataClient.resolveMedia(lookups, starts)
            if (resolution != null) {
                pickFromResolution(resolution, overrides)?.let { pick ->
                    val source = if (pick.viaOverride) FandomMatchSource.OVERRIDE else FandomMatchSource.WIKIDATA
                    val candidateTitle = pick.articleTitle
                    val pageTitle = candidateTitle?.takeIf {
                        query.ids.wikidataId != null ||
                            FandomNameMatcher.titleSimilarity(it, query.fallbackTitles) >= 0.55
                    }
                    val ref = FandomWikiRef(
                        wikiId = pick.info.wikiId,
                        source = source,
                        confidence = FandomConfidence.CERTAIN,
                        siteName = pick.info.siteName,
                        pageTitle = pageTitle
                    )
                    // Dil öneki olmayan wiki İngilizce olmak zorunda değildir (Polonya'nın `wiedzmin`,
                    // Rusya'nın `ru.domb`). Yabancı dilli sonuç, daha iyi bir İngilizce/Türkçe wiki
                    // bulunamazsa son çare olarak tutulur.
                    if (pick.viaOverride || isPreferredLanguage(pick.info)) return ref
                    foreignWikidataRef = ref
                }
            }
        }

        // Doğrudan P4073 yoksa (çoğu yapımda eksik) karakter oylaması: medya → karakterler → P6262.
        resolveByCharacterVotes(lookups, starts)?.let { return it }

        val evidence = FandomAniListSource.load(ids.aniListId, ids.malId, query.isManga)

        resolveViaRelations(evidence, overrides)?.let { return it }

        if (allowNameFallback) {
            searchByName(evidence, query, ids)?.let { return it }
        }
        return foreignWikidataRef
    }

    /** İngilizce ya da Türkçe (uygulama dili) wiki: yabancı dilli kök wiki'den önce gelir. */
    private fun isPreferredLanguage(info: FandomSiteInfo): Boolean = languageRank(info) <= 1

    private fun languageRank(info: FandomSiteInfo): Int =
        when (info.lang?.lowercase(java.util.Locale.ROOT)?.substringBefore('-')) {
            "en" -> 0
            "tr" -> 1
            else -> 2
        }

    // ── Karakter oylaması ────────────────────────────────────────────────────

    /**
     * Medya QID'lerinin karakterlerini (P1441 ters bağlantı) tarar, her karakterin P6262 wiki'lerini
     * oylar. Kazanan wiki siteinfo ile doğrulanır (canlı, yeterli makale). Sonuç PROBABLE:
     * karakter oylaması kesin kimlik değil, güçlü bir kanıttır.
     */
    private suspend fun resolveByCharacterVotes(
        lookups: List<Pair<String, String>>,
        starts: List<String>
    ): FandomWikiRef? {
        val qids = LinkedHashSet<String>()
        starts.filter { WikidataParsers.isQid(it) }.forEach { qids += it }
        if (lookups.isNotEmpty()) {
            val found = WikidataClient.findMediaQids(lookups) ?: return null
            qids += found
        }
        if (qids.isEmpty()) return null

        val votes = LinkedHashMap<String, Int>()
        for (qid in qids.take(MAX_VOTE_MEDIA)) {
            val perCharacter = WikidataClient.characterArticlesOf(qid) ?: continue
            WikidataCharacterVotes.tally(perCharacter).forEach { (wiki, n) ->
                votes[wiki] = (votes[wiki] ?: 0) + n
            }
        }
        val winner = WikidataCharacterVotes.winner(votes) ?: return null
        if (isWikiRejected(winner)) return null
        val info = siteInfoCached(winner)
            ?.takeUnless { isWikiRejected(it.wikiId) || FandomCandidateSelector.isBlocked(it.wikiId) }
            ?: return null
        if ((info.articles ?: 0) < WikidataCharacterVotes.MIN_WIKI_ARTICLES) return null
        Log.d(TAG, "Karakter oylaması: ${info.wikiId} oylar=${votes[winner]} toplam=${votes.size} aday")
        return FandomWikiRef(info.wikiId, FandomMatchSource.WIKIDATA, FandomConfidence.PROBABLE, info.siteName)
    }

    // ── Kimlikleri tamamlama ─────────────────────────────────────────────────

    /**
     * Eklenti kaynaklı kayıtlarda (ör. MangaDetailScreen) hiçbir katalog kimliği yoktur; isim yolu
     * karakter kanıtı olmadan çalışıp çoğu zaman reddeder (franchise wiki'lerinde başlık sayfası
     * ayrım sayfası olabilir). Başlık AniList'te birebir bulunursa kimlikler tamamlanır ve
     * Wikidata / karakter oylaması / ilişki / kimlikli isim doğrulaması açılır.
     */
    private suspend fun bridgeIdsByTitle(query: FandomMediaQuery, ids: FandomSourceIds): FandomSourceIds {
        val hasCatalogId = ids.aniListId != null || ids.malId != null || ids.wikidataId != null ||
            ids.tmdbId != null || ids.imdbId != null || ids.kitsuId != null || ids.bangumiId != null ||
            ids.shikimoriId != null || ids.simklId != null
        if (hasCatalogId || query.isMovie || query.isTvShow || query.fallbackTitles.isEmpty()) return ids
        val match = FandomAniListSource.findByTitle(query.fallbackTitles, query.isManga) ?: return ids
        Log.d(TAG, "Başlık → AniList#${match.aniListId} (MAL#${match.malId})")
        val bridged = ids.copy(aniListId = match.aniListId, malId = match.malId?.takeIf { it in 1..99_999_999 })
        return completeIds(query.copy(ids = bridged))
    }

    private suspend fun completeIds(query: FandomMediaQuery): FandomSourceIds {
        var ids = query.ids
        val mediaType = when {
            query.isManga -> MediaType.Manga
            query.isMovie -> MediaType.Movie
            query.isTvShow -> MediaType.TvShow
            else -> MediaType.Anime
        }

        // Shikimori IDs are not MAL IDs; resolve them through the established mapping first.
        val shikimoriId = ids.shikimoriId
        if (ids.malId == null && shikimoriId != null) {
            val mal = safely { withContext(Dispatchers.IO) { KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId) } }
            if (mal != null && mal in 1..99_999_999) ids = ids.copy(malId = mal)
        }

        val currentSimklId = ids.simklId
        if (currentSimklId != null && mediaType != MediaType.Manga &&
            (ids.aniListId == null || ids.malId == null || ids.tmdbId == null || ids.imdbId == null)
        ) {
            val currentTmdbId = ids.tmdbId
            val currentMalId = ids.malId
            val simkl = safely {
                com.kitsugi.animelist.data.remote.KitsugiSimklDetailClient.resolveSimklCrossIds(
                    simklId = currentSimklId,
                    mediaType = mediaType,
                    hintTmdbId = currentTmdbId,
                    hintMalId = currentMalId
                )
            }
            if (simkl != null) {
                ids = ids.copy(
                    aniListId = ids.aniListId ?: simkl.aniListId?.takeIf { it > 0 },
                    malId = ids.malId ?: simkl.malId?.takeIf { it in 1..99_999_999 },
                    kitsuId = ids.kitsuId ?: simkl.kitsuId?.takeIf { it > 0 },
                    tmdbId = ids.tmdbId ?: simkl.tmdbId?.takeIf { it > 0 },
                    imdbId = ids.imdbId ?: simkl.imdbId?.trim()?.takeIf { it.matches(Regex("(?i)^tt[0-9]{5,12}$")) }
                )
            }
        }

        // Kitsu / Bangumi / TMDB identities can be bridged to MAL/AniList. The result is also
        // useful for IMDb (P345) lookups, even when this item already had a primary catalog ID.
        val hasCatalogId = ids.aniListId != null || ids.malId != null || ids.kitsuId != null ||
            ids.bangumiId != null || ids.tmdbId != null
        if (hasCatalogId && (ids.imdbId == null || (ids.aniListId == null && ids.malId == null))) {
            val resolved = safely {
                KitsugiIdResolver.resolveIds(
                    malId = ids.malId,
                    aniListId = ids.aniListId,
                    tmdbId = ids.tmdbId,
                    mediaType = mediaType,
                    kitsuId = ids.kitsuId,
                    bangumiId = ids.bangumiId
                )
            }
            if (resolved != null) {
                ids = ids.copy(
                    aniListId = ids.aniListId ?: resolved.aniListId?.takeIf { it > 0 },
                    malId = ids.malId ?: resolved.malId?.takeIf { it in 1..99_999_999 },
                    tmdbId = ids.tmdbId ?: resolved.tmdbId?.takeIf { it > 0 },
                    wikidataId = ids.wikidataId ?: resolved.wikidataId?.takeIf { WikidataParsers.isQid(it) },
                    imdbId = ids.imdbId ?: resolved.imdbId?.trim()?.takeIf { it.matches(Regex("(?i)^tt[0-9]{5,12}$")) }
                )
            }
        }
        return ids
    }

    // ── Wikidata sonucundan seçim ────────────────────────────────────────────

    /** Resolve the live wiki and retain an item-level P6262 title when it targets that wiki. */
    private suspend fun pickFromResolution(
        resolution: WikidataResolution,
        overrides: FandomOverrideTable
    ): ResolutionPick? {
        val qids = resolution.starts + resolution.visitedNodes
        for (qid in qids) {
            val wiki = overrides.lookupQid(qid) ?: continue
            if (isWikiRejected(wiki)) continue
            siteInfoCached(wiki)?.takeUnless { isWikiRejected(it.wikiId) }?.let { info ->
                return ResolutionPick(info, viaOverride = true, articleTitle = directArticleTitle(resolution, info.wikiId))
            }
        }
        for (depth in 0..2) {
            val candidates = resolution.wikisByDepth[depth].orEmpty()
            if (candidates.isEmpty()) continue
            pickAlive(candidates)?.let { info ->
                return ResolutionPick(info, viaOverride = false, articleTitle = directArticleTitle(resolution, info.wikiId))
            }
        }
        val articleWikis = resolution.articleIds.mapNotNull { FandomUrls.parseArticleId(it)?.first }
        if (articleWikis.isNotEmpty()) {
            pickAlive(articleWikis)?.let { info ->
                return ResolutionPick(info, viaOverride = false, articleTitle = directArticleTitle(resolution, info.wikiId))
            }
        }
        return null
    }

    private fun directArticleTitle(resolution: WikidataResolution, wikiId: String): String? =
        resolution.articleIds.asSequence()
            .mapNotNull(FandomUrls::parseArticleId)
            .firstOrNull { it.first == wikiId }
            ?.second

    /**
     * Canlı bir wiki seçer. Dil sırası siteinfo'daki GERÇEK dile göre belirlenir (önek yok = İngilizce
     * varsayımı yanlıştı): önce İngilizce, sonra Türkçe, sonra diğerleri; aynı dil grubunda makale
     * sayısı büyük olan kazanır. İngilizce/Türkçe kök wiki yoksa yerelleştirilmiş adaylar da yüklenir.
     */
    private suspend fun pickAlive(candidates: List<String>): FandomSiteInfo? {
        val usable = FandomCandidateSelector.usable(candidates).filterNot { isWikiRejected(it) }
        if (usable.isEmpty()) return null
        val roots = usable.filter { FandomUrls.isRoot(it) }.take(MAX_ROOT_CANDIDATES)
        val localized = usable.filterNot { FandomUrls.isRoot(it) }
            .sortedBy { if (FandomUrls.languageOf(it) == "tr") 0 else 1 }
            .take(MAX_LOCALIZED_CANDIDATES)
        suspend fun loadInfos(pool: List<String>): List<FandomSiteInfo> = coroutineScope {
            pool.map { candidate ->
                async { siteInfoCached(candidate) }
            }.awaitAll()
                .filterNotNull()
                .filterNot { FandomCandidateSelector.isBlocked(it.wikiId) || isWikiRejected(it.wikiId) }
                .distinctBy { it.wikiId }
        }
        var infos = loadInfos(roots)
        if (infos.none(::isPreferredLanguage)) {
            infos = (infos + loadInfos(localized)).distinctBy { it.wikiId }
        }
        if (infos.isEmpty()) return null
        val bestRank = infos.minOf(::languageRank)
        return infos.filter { languageRank(it) == bestRank }.maxByOrNull { it.articles ?: 0 }
    }

    // ── İlişki yürüyüşü ──────────────────────────────────────────────────────

    private suspend fun resolveViaRelations(
        evidence: FandomMediaEvidence?,
        overrides: FandomOverrideTable
    ): FandomWikiRef? {
        val relations = evidence?.relations?.take(MAX_RELATIONS).orEmpty()
        for (relation in relations) {
            val relationIds = FandomSourceIds(aniListId = relation.aniListId, malId = relation.malId)
            overrides.lookup(relationIds, relation.isManga)?.takeUnless { isWikiRejected(it) }?.let { wiki ->
                siteInfoCached(wiki)?.takeUnless { isWikiRejected(it.wikiId) }?.let { info ->
                    return FandomWikiRef(info.wikiId, FandomMatchSource.RELATION, FandomConfidence.CERTAIN, info.siteName)
                }
            }
            val lookups = FandomMediaIds.wikidataLookups(relationIds, relation.isManga, isMovie = false)
            val resolution = WikidataClient.resolveMedia(lookups, emptyList()) ?: continue
            pickFromResolution(resolution, overrides)?.let { (info, _) ->
                return FandomWikiRef(info.wikiId, FandomMatchSource.RELATION, FandomConfidence.CERTAIN, info.siteName)
            }
        }
        return null
    }

    // ── Son çare: isimle bul, kimlikle doğrula ───────────────────────────────

    private suspend fun searchByName(
        evidence: FandomMediaEvidence?,
        query: FandomMediaQuery,
        ids: FandomSourceIds
    ): FandomWikiRef? {
        val allTitles = (evidence?.titles.orEmpty() + listOfNotNull(evidence?.nativeTitle) + query.fallbackTitles)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 200 }
            .distinctBy(FandomNameVariants::normalizeForCompare)
        val baseTitles = FandomNameVariants.mediaTitleVariants(allTitles)
        if (baseTitles.isEmpty()) return null
        val creators = evidence?.creators.orEmpty()
        val nameEvidence = FandomNameEvidence(
            titles = (baseTitles + allTitles).distinct(),
            creators = creators,
            characterNames = evidence?.characters?.map { it.fullName }.orEmpty(),
            isManga = query.isManga
        )
        val nativeTitles = FandomNameVariants.mediaTitleVariants(allTitles)
            .filterNot(FandomNameVariants::isMostlyLatin)

        // A) Aday üretme: ana başlık, varsa yerel yazım, yazar ipucu ve ikinci başlık.
        val latinTitles = baseTitles.filter(FandomNameVariants::isMostlyLatin)
        val latinQueryLimit = if (nativeTitles.isEmpty()) MAX_SEARCH_QUERIES - 1 else 3
        val queries = buildList {
            latinTitles.take(latinQueryLimit).forEach(::add)
            nativeTitles.take(3).forEach(::add)
            creators.firstOrNull()?.let { add("${baseTitles.first()} $it") }
            if (nativeTitles.isNotEmpty()) {
                latinTitles.drop(3).firstOrNull()?.let(::add)
                nativeTitles.drop(3).firstOrNull()?.let(::add)
            }
        }.distinctBy(FandomNameVariants::normalizeForCompare).take(MAX_SEARCH_QUERIES)
        val hits = LinkedHashMap<String, FandomCommunityHit>()
        val nativeQueryKeys = nativeTitles.map(FandomNameVariants::normalizeForCompare).toSet()
        val nativeSearchHitIds = LinkedHashSet<String>()
        val searchResults = coroutineScope {
            queries.map { text -> async { text to FandomApi.communitySearch(text) } }.awaitAll()
        }
        searchResults.forEach { (text, results) ->
            val cameFromNativeQuery = FandomNameVariants.normalizeForCompare(text) in nativeQueryKeys
            results.forEach { hit ->
                hits.putIfAbsent(hit.wikiId, hit)
                if (cameFromNativeQuery) nativeSearchHitIds += hit.wikiId
            }
        }
        val hasExactTitle = hits.values.any { FandomNameMatcher.titleSimilarity(it.name, nameEvidence.titles, creators) >= 0.99 }
        if (!hasExactTitle) {
            val slugs = FandomNameVariants.slugCandidates(baseTitles)
                .filter { it !in hits }
                .take(MAX_SLUG_PROBES)
            val slugInfos = coroutineScope {
                slugs.map { slug -> async { siteInfoCached(slug) } }.awaitAll()
            }
            slugInfos.filterNotNull().forEach { info ->
                if (info.wikiId in hits) return@forEach
                hits[info.wikiId] = FandomCommunityHit(
                    wikiId = info.wikiId,
                    name = info.siteName ?: info.wikiId,
                    language = info.lang,
                    pageCount = info.articles
                )
            }
        }

        // B) Ön eleme + ucuz sıralama.
        val shortlisted = hits.values
            .filter { FandomNameMatcher.preFilterVeto(it) == null && !isWikiRejected(it.wikiId) }
            .sortedByDescending { FandomNameMatcher.quickScore(it, nameEvidence) }
            .take(MAX_FINGERPRINT_CANDIDATES)
        if (shortlisted.isEmpty()) return null

        // Yerel başlıkla açılmış eklenti/sağlayıcı kayıtlarında romaji olmayabilir. Topluluk
        // araması yerel adı kabul ettiyse, aday wiki içinde aynı adlı gerçek (ambiguous olmayan)
        // maddeyi doğrula. Tek bir doğrulanmış wiki yoksa güvenli biçimde isim puanlamasına dön.
        if (nativeTitles.isNotEmpty()) {
            // A native-only title can have zero token overlap with an English wiki name. Probe a
            // wider (still bounded) search set before falling back to the three fingerprint hits.
            val nativePriorityHits = buildList {
                nativeSearchHitIds.forEach { wikiId -> hits[wikiId]?.let(::add) }
                hits.values.filterNot { it.wikiId in nativeSearchHitIds }.forEach(::add)
            }
            val nativeCandidates = FandomNameMatcher.nativeTitleVerificationCandidates(
                hits = nativePriorityHits.filterNot { isWikiRejected(it.wikiId) },
                limit = MAX_NATIVE_TITLE_CANDIDATES
            )
            val nativeKeys = nativeQueryKeys
            val verified = coroutineScope {
                nativeCandidates.map { hit ->
                    async {
                        val lookups = safely { FandomApi.lookupTitles(hit.wikiId, nativeTitles) }.orEmpty()
                        val exact = lookups.values.firstOrNull { lookup ->
                            lookup.exists && !lookup.isDisambiguation &&
                                FandomNameVariants.normalizeForCompare(lookup.requested) in nativeKeys
                        } ?: return@async null
                        val info = siteInfoCached(hit.wikiId) ?: return@async null
                        hit to (exact to info)
                    }
                }.awaitAll().filterNotNull().distinctBy { it.first.wikiId }
            }
            if (verified.size == 1) {
                val (hit, pair) = verified.single()
                val info = pair.second
                Log.d(TAG, "Yerel başlık doğrulaması: ${info.wikiId} ← ${pair.first.resolvedTitle}")
                return FandomWikiRef(
                    wikiId = info.wikiId,
                    source = FandomMatchSource.NAME,
                    confidence = FandomConfidence.PROBABLE,
                    siteName = info.siteName ?: hit.name,
                    score = 0.75
                )
            }
        }

        // Build exact provider references now; the article proof below is cheaper and often
        // sufficient, so external-link scans are delayed until they are actually needed.
        val identityLinks = FandomMediaIds.externalLinkLookups(
            ids = ids,
            isManga = query.isManga,
            isMovie = query.isMovie,
            isTvShow = query.isTvShow
        ).take(MAX_IDENTITY_LINKS)

        // A specific, non-disambiguation article is a second independent proof. Requiring the
        // candidate wiki itself to match the work title, genre hub and (for one-word titles) year
        // prevents a generic "Monster" wiki from winning just because the word occurs there.
        val articleTitles = FandomNameVariants.mediaTitleVariants(allTitles)
        if (articleTitles.isNotEmpty()) {
            val articleProofs = coroutineScope {
                shortlisted.take(MAX_ARTICLE_PROOF_CANDIDATES).map { hit ->
                    async {
                        val lookups = safely { FandomApi.lookupTitles(hit.wikiId, articleTitles) }.orEmpty()
                        val match = FandomMediaArticleMatcher.choose(allTitles, lookups.values)
                            ?: return@async null
                        val info = siteInfoCached(hit.wikiId) ?: return@async null
                        if (!FandomMediaIdentityProof.hasStrongArticleProof(
                                hit = hit,
                                siteInfo = info,
                                sourceTitles = allTitles,
                                creators = creators,
                                isManga = query.isManga,
                                releaseYear = query.releaseYear,
                                articleMatch = match
                            )
                        ) return@async null
                        hit to (match to info)
                    }
                }.awaitAll().filterNotNull().distinctBy { it.first.wikiId }
            }
            if (articleProofs.size == 1) {
                val (hit, pair) = articleProofs.single()
                val (match, info) = pair
                Log.d(TAG, "Yapım maddesi doğruladı: ${info.wikiId} ← ${match.lookup.resolvedTitle}")
                return FandomWikiRef(
                    wikiId = info.wikiId,
                    source = FandomMatchSource.NAME,
                    confidence = FandomConfidence.PROBABLE,
                    siteName = info.siteName ?: hit.name,
                    score = match.score,
                    pageTitle = match.lookup.resolvedTitle
                )
            }
        }

        // C) Known provider IDs are an exact proof, independent of AniList availability. Keep
        // the scan bounded and run it only after article-title proof did not identify one wiki.
        val identityCandidateWikiIds = shortlisted
            .take(MAX_IDENTITY_LINK_CANDIDATES)
            .map { it.wikiId }
            .toSet()
        var identityMatches = emptySet<String>()
        if (identityLinks.isNotEmpty()) {
            val identityCandidates = shortlisted.filter { it.wikiId in identityCandidateWikiIds }
            val linked = coroutineScope {
                identityCandidates.map { hit ->
                    async {
                        var linkedBy: String? = null
                        for (url in identityLinks) {
                            if (safely { FandomApi.hasExternalLink(hit.wikiId, url) } == true) {
                                linkedBy = url
                                break
                            }
                        }
                        linkedBy?.let { hit to it }
                    }
                }.awaitAll().filterNotNull().distinctBy { it.first.wikiId }
            }
            identityMatches = linked.map { it.first.wikiId }.toSet()
            if (linked.size == 1) {
                val (hit, url) = linked.single()
                val info = siteInfoCached(hit.wikiId)
                if (info != null && !isWikiRejected(info.wikiId) && !FandomCandidateSelector.isBlocked(info.wikiId)) {
                    Log.d(TAG, "Kimlik bağlantısı doğruladı: ${info.wikiId} ← $url")
                    return FandomWikiRef(
                        wikiId = info.wikiId,
                        source = FandomMatchSource.NAME,
                        confidence = FandomConfidence.CERTAIN,
                        siteName = info.siteName ?: hit.name,
                        score = 1.0
                    )
                }
            }
        }

        // D) Doğrulama: karakter parmak izi (aday başına tek istek).
        val scores = coroutineScope {
            shortlisted.map { hit ->
                async {
                    val characterPrint = fingerprint(hit.wikiId, evidence)
                    FandomNameMatcher.score(hit, nameEvidence, characterPrint)
                }
            }.awaitAll()
        }

        // E) Karar (+ muhtemel adayda yeniden tüm kaynakların birebir kimlik bağlantısı).
        var best = FandomNameMatcher.pickBest(scores) ?: return null
        if (best.decision == FandomDecision.PROBABLE &&
            best.wikiId !in identityCandidateWikiIds && identityMatches.isEmpty()
        ) {
            for (link in identityLinks) {
                if (safely { FandomApi.hasExternalLink(best.wikiId, link) } == true) {
                    best = best.copy(score = 1.0, decision = FandomDecision.CERTAIN)
                    break
                }
            }
        }
        if (best.decision == FandomDecision.REJECT) return null
        val info = siteInfoCached(best.wikiId)
        Log.d(TAG, "İsim yolu: ${best.wikiId} skor=${"%.2f".format(best.score)} karar=${best.decision}")
        return FandomWikiRef(
            wikiId = info?.wikiId ?: best.wikiId,
            source = FandomMatchSource.NAME,
            confidence = if (best.decision == FandomDecision.CERTAIN) FandomConfidence.CERTAIN else FandomConfidence.PROBABLE,
            siteName = info?.siteName ?: hits[best.wikiId]?.name,
            score = best.score
        )
    }

    /** Ana ve yan karakterlerin (varyantlarıyla) + yaratıcıların sayfaları tek istekte aranır. */
    private suspend fun fingerprint(wikiId: String, evidence: FandomMediaEvidence?): FandomFingerprint? {
        val characters = evidence?.characters.orEmpty()
        if (characters.isEmpty()) return null
        val main = characters.filter { it.isMain }.take(6)
        val others = characters.filterNot { it.isMain }.take(4)
        val probes = main + others
        val variantsByProbe = probes.associateWith {
            FandomNameVariants.characterTitleVariants(it.fullName, it.alternatives, max = 4)
        }
        val creatorTitles = evidence?.creators.orEmpty().take(2)
        // En fazla 10 karakter × 4 varyant + 2 yaratıcı = 42 başlık → tek istek.
        val titles = (creatorTitles + variantsByProbe.values.flatten()).distinct().take(50)
        val lookups = FandomApi.lookupTitles(wikiId, titles)
        if (lookups.isEmpty()) return null

        var mainFound = 0
        var anyFound = 0
        var nativeConfirmed = 0
        for (probe in probes) {
            val hit = variantsByProbe[probe].orEmpty()
                .mapNotNull { lookups[it] }
                .firstOrNull { it.exists && !it.isDisambiguation }
                ?: continue
            anyFound++
            if (probe.isMain) mainFound++
            if (FandomNameVariants.nativeAppearsIn(probe.nativeName, hit.evidenceText())) nativeConfirmed++
        }
        val creatorFound = creatorTitles.any { lookups[it]?.exists == true }
        return FandomFingerprint(
            mainTotal = main.size,
            mainFound = mainFound,
            anyTotal = probes.size,
            anyFound = anyFound,
            nativeConfirmed = nativeConfirmed,
            creatorPageFound = creatorFound
        )
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Kimlik çözümlemesi başarısız: ${e.message}")
        null
    }
}

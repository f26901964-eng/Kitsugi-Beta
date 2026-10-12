package com.kitsugi.animelist.data.fandom

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bir yapımın wiki'sini bulmak için girdi. */
data class FandomMediaQuery(
    val ids: FandomSourceIds,
    val isManga: Boolean,
    val isMovie: Boolean = false,
    /** AniList verisi alınamazsa isim yolunda kullanılacak başlıklar. */
    val fallbackTitles: List<String> = emptyList()
) {
    fun cacheKey(): String? = ids.stableKey(isManga, isMovie)
}

/**
 * Yapım → Fandom wiki çözümleyici.
 *
 * Sıra: 1) eşleme tablosu, 2) Wikidata (kimlik + üst kayıtlar), 3) ilişki yürüyüşü
 * (önceki sezon, kaynak eser… yine kimlikle), 4) son çare: isimle aday + kanıtla doğrulama.
 * Önbellek ve kullanıcı tercihleri [FandomRepository] katmanındadır.
 */
internal class FandomWikiResolver(private val context: Context) {

    companion object {
        private const val TAG = "FandomWikiResolver"
        private const val MAX_RELATIONS = 4
        private const val MAX_ROOT_CANDIDATES = 4
        private const val MAX_FINGERPRINT_CANDIDATES = 3
        private const val MAX_SLUG_PROBES = 3
        private const val MAX_SEARCH_QUERIES = 3

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
        val ids = completeIds(query)
        val overrides = FandomOverrides.table(context)

        overrides.lookup(ids, query.isManga)?.let { wiki ->
            siteInfoCached(wiki)?.let { info ->
                return FandomWikiRef(info.wikiId, FandomMatchSource.OVERRIDE, FandomConfidence.CERTAIN, info.siteName)
            }
        }

        val lookups = FandomMediaIds.wikidataLookups(ids, query.isManga, query.isMovie)
        val starts = listOfNotNull(ids.wikidataId)
        if (lookups.isNotEmpty() || starts.isNotEmpty()) {
            val resolution = WikidataClient.resolveMedia(lookups, starts)
            if (resolution != null) {
                pickFromResolution(resolution, overrides)?.let { (info, viaOverride) ->
                    val source = if (viaOverride) FandomMatchSource.OVERRIDE else FandomMatchSource.WIKIDATA
                    return FandomWikiRef(info.wikiId, source, FandomConfidence.CERTAIN, info.siteName)
                }
            }
        }

        val evidence = FandomAniListSource.load(ids.aniListId, ids.malId, query.isManga)

        resolveViaRelations(evidence, overrides)?.let { return it }

        if (allowNameFallback) {
            searchByName(evidence, query, ids)?.let { return it }
        }
        return null
    }

    // ── Kimlikleri tamamlama ─────────────────────────────────────────────────

    private suspend fun completeIds(query: FandomMediaQuery): FandomSourceIds {
        var ids = query.ids
        val shikimoriId = ids.shikimoriId
        if (ids.malId == null && shikimoriId != null) {
            val mal = safely { withContext(Dispatchers.IO) { KitsugiIdResolver.resolveMalIdFromShikimori(shikimoriId) } }
            if (mal != null && mal > 0) ids = ids.copy(malId = mal)
        }
        // Kitsu / Bangumi / TMDB kaynaklarında AniList–MAL kimliği yoksa çapraz kimlik çözülür
        // (AniList doğrulama verisi ve ilişki yürüyüşü bu kimliklere ihtiyaç duyar).
        if (ids.aniListId == null && ids.malId == null &&
            (ids.kitsuId != null || ids.bangumiId != null || ids.tmdbId != null)
        ) {
            val mediaType = when {
                query.isManga -> MediaType.Manga
                ids.tmdbId != null && query.isMovie -> MediaType.Movie
                else -> MediaType.Anime
            }
            val resolved = safely {
                KitsugiIdResolver.resolveIds(
                    malId = null,
                    aniListId = null,
                    tmdbId = ids.tmdbId,
                    mediaType = mediaType,
                    kitsuId = ids.kitsuId,
                    bangumiId = ids.bangumiId
                )
            }
            if (resolved != null) {
                ids = ids.copy(
                    aniListId = resolved.aniListId?.takeIf { it > 0 },
                    malId = resolved.malId?.takeIf { it in 1..99_999_999 },
                    tmdbId = ids.tmdbId ?: resolved.tmdbId,
                    wikidataId = ids.wikidataId ?: resolved.wikidataId?.takeIf { WikidataParsers.isQid(it) }
                )
            }
        }
        return ids
    }

    // ── Wikidata sonucundan seçim ────────────────────────────────────────────

    /** @return (wiki bilgisi, QID eşleme tablosundan mı geldi) */
    private suspend fun pickFromResolution(
        resolution: WikidataResolution,
        overrides: FandomOverrideTable
    ): Pair<FandomSiteInfo, Boolean>? {
        val qids = resolution.starts + resolution.visitedNodes
        for (qid in qids) {
            val wiki = overrides.lookupQid(qid) ?: continue
            siteInfoCached(wiki)?.let { return it to true }
        }
        for (depth in 0..2) {
            val candidates = resolution.wikisByDepth[depth].orEmpty()
            if (candidates.isEmpty()) continue
            pickAlive(candidates)?.let { return it to false }
        }
        val articleWikis = resolution.articleIds.mapNotNull { FandomUrls.parseArticleId(it)?.first }
        if (articleWikis.isNotEmpty()) {
            pickAlive(articleWikis)?.let { return it to false }
        }
        return null
    }

    /** Adaylardan erişilebilir olanı seçer (birden fazla ana aday → makale sayısı büyük olan). */
    private suspend fun pickAlive(candidates: List<String>): FandomSiteInfo? {
        val usable = FandomCandidateSelector.usable(candidates)
        if (usable.isEmpty()) return null
        val roots = usable.filter { FandomUrls.isRoot(it) }.take(MAX_ROOT_CANDIDATES)
        val pool = roots.ifEmpty { usable.filter { FandomUrls.languageOf(it) == "tr" }.take(2) }
        val infos = LinkedHashMap<String, FandomSiteInfo>()
        for (candidate in pool) {
            val info = siteInfoCached(candidate) ?: continue
            if (!FandomCandidateSelector.isBlocked(info.wikiId)) infos.putIfAbsent(info.wikiId, info)
        }
        if (infos.isEmpty()) return null
        val counts = infos.mapValues { (_, info) -> info.articles ?: 0 }
        val choice = FandomCandidateSelector.choose(infos.keys, counts) ?: return null
        return infos[choice.primary]
    }

    // ── İlişki yürüyüşü ──────────────────────────────────────────────────────

    private suspend fun resolveViaRelations(
        evidence: FandomMediaEvidence?,
        overrides: FandomOverrideTable
    ): FandomWikiRef? {
        val relations = evidence?.relations?.take(MAX_RELATIONS).orEmpty()
        for (relation in relations) {
            val relationIds = FandomSourceIds(aniListId = relation.aniListId, malId = relation.malId)
            overrides.lookup(relationIds, relation.isManga)?.let { wiki ->
                siteInfoCached(wiki)?.let { info ->
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
        val allTitles = (evidence?.titles.orEmpty() + query.fallbackTitles)
            .map { it.trim() }
            .filter { it.isNotEmpty() && FandomNameVariants.isMostlyLatin(it) }
            .distinct()
        val baseTitles = FandomNameVariants.baseTitleVariants(allTitles)
        if (baseTitles.isEmpty()) return null
        val creators = evidence?.creators.orEmpty()
        val nameEvidence = FandomNameEvidence(
            titles = (baseTitles + allTitles).distinct(),
            creators = creators,
            characterNames = evidence?.characters?.map { it.fullName }.orEmpty(),
            isManga = query.isManga
        )

        // A) Aday üretme: başlık, başlık + yazar, ikinci başlık.
        val queries = buildList {
            add(baseTitles.first())
            creators.firstOrNull()?.let { add("${baseTitles.first()} $it") }
            baseTitles.getOrNull(1)?.let { add(it) }
        }.distinct().take(MAX_SEARCH_QUERIES)
        val hits = LinkedHashMap<String, FandomCommunityHit>()
        for (text in queries) {
            FandomApi.communitySearch(text).forEach { hit -> hits.putIfAbsent(hit.wikiId, hit) }
        }
        val hasExactTitle = hits.values.any { FandomNameMatcher.titleSimilarity(it.name, nameEvidence.titles, creators) >= 0.99 }
        if (!hasExactTitle) {
            val slugs = FandomNameVariants.slugCandidates(baseTitles)
                .filter { it !in hits }
                .take(MAX_SLUG_PROBES)
            for (slug in slugs) {
                val info = siteInfoCached(slug) ?: continue
                if (info.wikiId in hits) continue
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
            .filter { FandomNameMatcher.preFilterVeto(it) == null }
            .sortedByDescending { FandomNameMatcher.quickScore(it, nameEvidence) }
            .take(MAX_FINGERPRINT_CANDIDATES)
        if (shortlisted.isEmpty()) return null

        // C) Doğrulama: karakter parmak izi (aday başına tek istek).
        val scores = shortlisted.map { hit ->
            val characterPrint = fingerprint(hit.wikiId, evidence)
            FandomNameMatcher.score(hit, nameEvidence, characterPrint)
        }

        // D) Karar (+ muhtemel sonuçta birebir AniList/MAL bağlantısı ile yükseltme denemesi).
        var best = FandomNameMatcher.pickBest(scores) ?: return null
        if (best.decision == FandomDecision.PROBABLE) {
            val linkQueries = buildList {
                val kind = if (query.isManga) "manga" else "anime"
                ids.aniListId?.let { add("anilist.co/$kind/$it") }
                ids.malId?.let { add("myanimelist.net/$kind/$it") }
            }
            for (link in linkQueries) {
                if (FandomApi.hasExternalLink(best.wikiId, link)) {
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

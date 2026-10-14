package com.kitsugi.animelist.data.fandom

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException

/** Karakter / ekip / seslendirme için bulunan wiki sayfası (henüz sayfa özeti oluşturulmadı). */
internal data class FandomResolvedCharacter(
    val wikiId: String,
    val lookup: FandomTitleLookup,
    val source: FandomMatchSource,
    val confidence: FandomConfidence,
    val siteName: String?,
    /** Wiki'nin bulunduğu yapımın önbellek anahtarı (wiki düzeyinde onay/ret için). */
    val mediaKey: String? = null,
    val wikiSource: FandomMatchSource? = null,
    val wikiConfidence: FandomConfidence? = null
)

/**
 * Karakter / ekip / seslendirme sanatçısı → wiki sayfaları.
 *
 * [resolveAll] birden çok kaynak döndürebilir (Fandom sayfası + Vikipedi maddesi):
 *  1. **Fandom kimlik yolu** — karakterde Wikidata P11736/P4085 → P6262; ekipte P11227/P4084 → P6262.
 *  2. **Yapım wiki'si** — çalıştığı/yer aldığı yapımların wiki'sinde (kimlik önce, isim son çare).
 *     Kişi, bu wiki'de adıyla aranır; yerel ad veya yapım adı kanıtı gerekir.
 *  3. **Vikipedi kimlik yolu** — aynı Wikidata kaydının `enwiki`/`trwiki` sitelinks'i (kesin).
 *  4. **Vikipedi isim yolu** — isim yolu açıksa Vikipedi'de aranır; yerel ad veya yapım adı kanıtı gerekir.
 *
 * Kesin (CERTAIN) kaynaklar önce gelir; eşit güvendeyse Fandom önce gelir.
 */
internal class FandomCharacterResolver(
    private val context: Context,
    private val allowNameFallback: Boolean,
    private val isRejected: (wikiId: String, title: String) -> Boolean
) {

    private companion object {
        const val TAG = "FandomCharResolver"
        const val MAX_APPEARANCES_TO_TRY = 2
        /** Ekipte kişinin yer aldığı yapımlar daha çeşitlidir; bir fazla deneme. */
        const val MAX_STAFF_APPEARANCES_TO_TRY = 3
        const val MAX_TITLE_VARIANTS = 40
        const val MAX_WIKIPEDIA_QUERIES = 4
        const val MAX_WIKIPEDIA_CANDIDATES = 12
        const val MAX_WORK_TITLES = 3
        val NATIVE_LABEL = Regex("(?i)(kanji|japanese|hangul|korean|chinese|native|日本語|漢字|한글)")
    }

    /** Kimlik sorguları bu çözümleme boyunca bir kez yapılır (aynı kimlik birden çok yolda kullanılır). */
    private val identityCache = HashMap<String, WikidataEntityClaims?>()

    /**
     * Tüm bulunabilir kaynakları döner. Liste boşsa sayfa yok demektir.
     * Kaynak sırası: kesin (CERTAIN) önce; eşitlikte Fandom önce.
     */
    suspend fun resolveAll(query: FandomCharacterQuery, forceRefresh: Boolean = false): List<FandomResolvedCharacter> {
        val primary = resolvePrimary(query, forceRefresh)
        val wikipedia = resolveWikipedia(query, excludeWikiId = primary?.wikiId)
        return listOfNotNull(primary, wikipedia)
            .distinctBy { it.wikiId }
            .sortedBy { if (it.confidence == FandomConfidence.CERTAIN) 0 else 1 }
    }

    // ── Fandom (birincil) yolu ───────────────────────────────────────────────

    private suspend fun resolvePrimary(query: FandomCharacterQuery, forceRefresh: Boolean): FandomResolvedCharacter? {
        val articles = fandomArticles(query)
        val media = resolveMediaWiki(query, forceRefresh)
        val mediaWiki = media?.first
        val mediaKey = media?.second

        if (articles.isNotEmpty()) {
            val onMediaWiki = mediaWiki?.let { wiki -> articles.firstOrNull { it.first == wiki.wikiId } }
            val wikidataChoice = if (mediaWiki == null) {
                FandomCandidateSelector.choose(articles.map { it.first })?.primary
                    ?.let { selectedWiki -> articles.firstOrNull { it.first == selectedWiki } }
            } else {
                null
            }
            val chosen = onMediaWiki ?: wikidataChoice
            if (chosen != null && !isRejected(chosen.first, chosen.second)) {
                val lookup = FandomApi.lookupTitles(chosen.first, listOf(chosen.second))[chosen.second]
                if (lookup != null && lookup.exists && !lookup.isDisambiguation) {
                    val siteName = mediaWiki?.takeIf { it.wikiId == chosen.first }?.siteName
                        ?: FandomWikiResolver.siteInfoCached(chosen.first)?.siteName
                    val onResolvedWiki = mediaWiki != null && mediaWiki.wikiId == chosen.first
                    return FandomResolvedCharacter(
                        wikiId = chosen.first,
                        lookup = lookup,
                        source = FandomMatchSource.WIKIDATA,
                        confidence = FandomConfidence.CERTAIN,
                        siteName = siteName,
                        mediaKey = if (onResolvedWiki) mediaKey else null,
                        // Sayfa kimlikle (P6262) doğrulandığı için wiki de kesin kabul edilir.
                        wikiSource = if (onResolvedWiki) mediaWiki?.source else FandomMatchSource.WIKIDATA,
                        wikiConfidence = FandomConfidence.CERTAIN
                    )
                }
            }
        }

        val wiki = mediaWiki ?: return null
        val (lookup, matchConfidence) = matchInWiki(wiki.wikiId, query) ?: return null
        val confidence = if (wiki.confidence == FandomConfidence.PROBABLE) FandomConfidence.PROBABLE else matchConfidence
        return FandomResolvedCharacter(
            wikiId = wiki.wikiId,
            lookup = lookup,
            source = wiki.source,
            confidence = confidence,
            siteName = wiki.siteName,
            mediaKey = mediaKey,
            wikiSource = wiki.source,
            wikiConfidence = wiki.confidence
        )
    }

    /** Kimlikten Fandom sayfaları: karakter için SPARQL yolu, ekip için kişi kaydının P6262 değerleri. */
    private suspend fun fandomArticles(query: FandomCharacterQuery): List<Pair<String, String>> {
        if (!query.isStaff) return wikidataArticles(query)
        return identityLookups(query)
            .flatMap { (property, value) -> safely { identityClaims(property, value) }?.fandomArticleIds.orEmpty() }
            .mapNotNull { FandomUrls.parseArticleId(it) }
            .filterNot { (wiki, _) -> FandomCandidateSelector.isBlocked(wiki) }
            .distinct()
    }

    private suspend fun wikidataArticles(query: FandomCharacterQuery): List<Pair<String, String>> {
        val lookups = buildList {
            query.aniListCharacterId?.let { add("P11736" to it.toString()) }
            query.malCharacterId?.let { add("P4085" to it.toString()) }
        }
        if (lookups.isEmpty()) return emptyList()
        return WikidataClient.characterArticles(lookups)
            .mapNotNull { FandomUrls.parseArticleId(it) }
            .filterNot { (wiki, _) -> FandomCandidateSelector.isBlocked(wiki) }
            .distinct()
    }

    /** Ana rol + anime önce; isim yolu yalnızca ilk yapım için denenir (istek bütçesi). */
    private suspend fun resolveMediaWiki(
        query: FandomCharacterQuery,
        forceRefresh: Boolean = false
    ): Pair<FandomWikiRef, String?>? {
        val ordered = query.appearances.sortedWith(
            compareByDescending<FandomAppearanceRef> { it.isMainRole }.thenBy { it.isManga }
        )
        val maxTries = if (query.isStaff) MAX_STAFF_APPEARANCES_TO_TRY else MAX_APPEARANCES_TO_TRY
        ordered.take(maxTries).forEachIndexed { index, appearance ->
            val mediaQuery = FandomMediaQuery(appearance.ids, appearance.isManga, appearance.isMovie, appearance.titles)
            val ref = FandomRepository.resolveMediaWiki(
                context = context,
                query = mediaQuery,
                allowNameFallback = allowNameFallback && index == 0,
                forceRefresh = forceRefresh
            )
            if (ref != null) return ref to mediaQuery.cacheKey()
        }
        return null
    }

    private suspend fun matchInWiki(
        wikiId: String,
        query: FandomCharacterQuery
    ): Pair<FandomTitleLookup, FandomConfidence>? {
        val variants = LinkedHashSet<String>()
        query.names.forEach { variants += FandomNameVariants.characterTitleVariants(it, max = 6) }
        query.nativeName?.let { variants += FandomNameVariants.characterTitleVariants(it, max = 6) }
        variants += query.alternatives
        val titles = variants.filter { it.isNotBlank() }.take(MAX_TITLE_VARIANTS)
        if (titles.isEmpty()) return null

        val found = FandomApi.lookupTitles(wikiId, titles).values
            .filter { it.exists && !it.isDisambiguation }
            .filterNot { isRejected(wikiId, it.resolvedTitle.orEmpty()) }
            .distinctBy { it.resolvedTitle }
        val native = query.nativeName
        if (query.isStaff) return matchPersonInWiki(wikiId, query, found)

        if (!native.isNullOrBlank()) {
            found.firstOrNull { FandomNameVariants.nativeAppearsIn(native, it.evidenceText()) }
                ?.let { return it to FandomConfidence.CERTAIN }
        }
        if (found.size == 1) {
            val only = found.first()
            if (only.infoboxJson != null && !contradictsNative(only, native)) {
                return only to FandomConfidence.PROBABLE
            }
        }

        // Wiki içi arama — yalnızca yerel ad teyidiyle kabul edilir (karakterler).
        if (!native.isNullOrBlank()) {
            val term = query.names.firstOrNull() ?: native
            val checked = found.mapNotNull { it.resolvedTitle }.toSet()
            val candidates = safely { FandomApi.searchPages(wikiId, term, limit = 5) }.orEmpty()
                .filter { it !in checked }
            if (candidates.isNotEmpty()) {
                safely { FandomApi.lookupTitles(wikiId, candidates) }.orEmpty().values.firstOrNull { lookup ->
                    lookup.exists && !lookup.isDisambiguation &&
                        !isRejected(wikiId, lookup.resolvedTitle.orEmpty()) &&
                        FandomNameVariants.nativeAppearsIn(native, lookup.evidenceText())
                }?.let { return it to FandomConfidence.CERTAIN }
            }
        }
        return null
    }

    /**
     * Ekip / seslendirme sanatçısı için wiki içi eşleşme.
     *  - Yerel ad (Kanji/Hangul) sayfada geçiyorsa CERTAIN.
     *  - Yerel ad yoksa ya da teyit edilemediyse: sayfa adı kişiyle uyuşmalı ve özet/bilgi
     *    kutusu çalıştığı yapımlardan birini (tam kelime) anmalı → PROBABLE (tek aday şartı).
     */
    private suspend fun matchPersonInWiki(
        wikiId: String,
        query: FandomCharacterQuery,
        found: List<FandomTitleLookup>
    ): Pair<FandomTitleLookup, FandomConfidence>? {
        val native = query.nativeName
        val keys = workKeys(query)
        val nameKeys = FandomPersonMatcher.nameKeys(query.names)

        native?.takeIf { it.isNotBlank() }?.let { nativeName ->
            found.firstOrNull { FandomNameVariants.nativeAppearsIn(nativeName, it.evidenceText()) }
                ?.let { return it to FandomConfidence.CERTAIN }
        }
        found.filter { FandomPersonMatcher.mentionsWork(it.evidenceText(), keys) }
            .singleOrNull()
            ?.let { return it to FandomConfidence.PROBABLE }

        val term = query.names.firstOrNull() ?: return null
        val checked = found.mapNotNull { it.resolvedTitle }.toSet()
        val candidates = safely { FandomApi.searchPages(wikiId, term, limit = 5) }.orEmpty()
            .filter { it !in checked }
        if (candidates.isEmpty()) return null
        val lookups = safely { FandomApi.lookupTitles(wikiId, candidates) }.orEmpty().values
            .filter { it.exists && !it.isDisambiguation && !isRejected(wikiId, it.resolvedTitle.orEmpty()) }
        val proofs = lookups.map { lookup ->
            lookup to FandomPersonMatcher.proofOf(
                pageTitle = lookup.resolvedTitle.orEmpty(),
                evidence = lookup.evidenceText(),
                nameKeys = nameKeys,
                nativeName = native,
                workKeys = keys
            )
        }
        val (lookup, proof) = FandomPersonMatcher.pickUnique(proofs) ?: return null
        val confidence = if (proof == FandomPersonMatcher.Proof.NATIVE) FandomConfidence.CERTAIN else FandomConfidence.PROBABLE
        return lookup to confidence
    }

    // ── Vikipedi (ikinci kaynak) yolu ────────────────────────────────────────

    /**
     * Vikipedi kaynağı: önce Wikidata sitelinks (kimlik, kesin), sonra isim araması (kanıtla).
     * [excludeWikiId] birincil kaynakla aynı wiki ise atlanır (aynı sayfa iki kez gösterilmez).
     */
    private suspend fun resolveWikipedia(
        query: FandomCharacterQuery,
        excludeWikiId: String?
    ): FandomResolvedCharacter? {
        val prefs = FandomWikiPrefs.state(context)
        val wikiOrder = if (prefs.preferTurkish) listOf("wikipedia.tr", "wikipedia.en") else listOf("wikipedia.en", "wikipedia.tr")

        // 1) Kimlik: aynı Wikidata kaydının Vikipedi maddesi.
        val sitelinks = identitySitelinks(query)
        for (wikiId in wikiOrder) {
            if (wikiId == excludeWikiId) continue
            val title = sitelinks[sitelinkKey(wikiId)] ?: continue
            if (isRejected(wikiId, title)) continue
            val lookup = safely { FandomApi.lookupTitles(wikiId, listOf(title))[title] } ?: continue
            if (!lookup.exists || lookup.isDisambiguation) continue
            if (isRejected(wikiId, lookup.resolvedTitle.orEmpty())) continue
            return wikipediaCharacter(wikiId, lookup, FandomConfidence.CERTAIN, mediaKey = null)
        }

        // 2) İsim: yalnızca isim yolu açıksa; kişi/karakter kanıtla (yerel ad veya yapım adı) doğrulanır.
        if (!allowNameFallback) return null
        val name = query.names.firstOrNull() ?: return null
        val works = workTitles(query)
        val keys = workKeys(query)
        val nameKeys = FandomPersonMatcher.nameKeys(query.names)
        for (wikiId in wikiOrder) {
            if (wikiId == excludeWikiId) continue
            val turkish = wikiId == "wikipedia.tr"
            val queries = if (query.isStaff) {
                FandomPersonMatcher.personSearchQueries(name, works, turkish, limit = MAX_WIKIPEDIA_QUERIES)
            } else {
                FandomPersonMatcher.characterSearchQueries(name, works, limit = MAX_WIKIPEDIA_QUERIES)
            }
            val candidates = LinkedHashSet<String>()
            for (searchQuery in queries) {
                candidates += safely { FandomApi.searchPages(wikiId, searchQuery, limit = 4) }.orEmpty()
                if (candidates.size >= MAX_WIKIPEDIA_CANDIDATES) break
            }
            if (candidates.isEmpty()) continue
            val lookups = safely { FandomApi.lookupTitles(wikiId, candidates.take(MAX_WIKIPEDIA_CANDIDATES).toList()) }
                .orEmpty().values
                .filter { it.exists && !it.isDisambiguation && !isRejected(wikiId, it.resolvedTitle.orEmpty()) }
            val proofs = lookups.map { lookup ->
                lookup to FandomPersonMatcher.proofOf(
                    pageTitle = lookup.resolvedTitle.orEmpty(),
                    evidence = lookup.evidenceText(),
                    nameKeys = nameKeys,
                    nativeName = query.nativeName,
                    workKeys = keys
                )
            }
            val (lookup, proof) = FandomPersonMatcher.pickUnique(proofs) ?: continue
            val confidence = if (proof == FandomPersonMatcher.Proof.NATIVE) FandomConfidence.CERTAIN else FandomConfidence.PROBABLE
            Log.d(TAG, "Vikipedi isim yolu: $wikiId ← ${lookup.resolvedTitle} ($proof)")
            return wikipediaCharacter(wikiId, lookup, confidence, mediaKey = null)
        }
        return null
    }

    private fun wikipediaCharacter(
        wikiId: String,
        lookup: FandomTitleLookup,
        confidence: FandomConfidence,
        mediaKey: String?
    ): FandomResolvedCharacter {
        val source = if (confidence == FandomConfidence.CERTAIN) FandomMatchSource.WIKIDATA else FandomMatchSource.NAME
        return FandomResolvedCharacter(
            wikiId = wikiId,
            lookup = lookup,
            source = source,
            confidence = confidence,
            siteName = if (wikiId == "wikipedia.tr") "Vikipedi" else "Wikipedia",
            mediaKey = mediaKey,
            wikiSource = source,
            wikiConfidence = confidence
        )
    }

    private fun sitelinkKey(wikiId: String): String = if (wikiId == "wikipedia.tr") "trwiki" else "enwiki"

    /** Kimlik özelliklerinden Vikipedi sitelinks'i (birden çok kimlikte ilk değer kazanır). */
    private suspend fun identitySitelinks(query: FandomCharacterQuery): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        for ((property, value) in identityLookups(query)) {
            safely { identityClaims(property, value) }?.wikipediaTitles?.forEach { (site, title) ->
                merged.putIfAbsent(site, title)
            }
        }
        return merged
    }

    // ── Kimlik yardımcıları ──────────────────────────────────────────────────

    /** Karakter: AniList P11736 / MAL P4085. Ekip: AniList kişi P11227 / MAL kişi P4084. */
    private fun identityLookups(query: FandomCharacterQuery): List<Pair<String, String>> = buildList {
        if (query.isStaff) {
            query.aniListStaffId?.let { add("P11227" to it.toString()) }
            query.malPeopleId?.let { add("P4084" to it.toString()) }
        } else {
            query.aniListCharacterId?.let { add("P11736" to it.toString()) }
            query.malCharacterId?.let { add("P4085" to it.toString()) }
        }
    }

    private suspend fun identityClaims(property: String, value: String): WikidataEntityClaims? {
        val key = "$property=$value"
        if (identityCache.containsKey(key)) return identityCache[key]
        val claims = WikidataClient.identityClaims(property, value)
        identityCache[key] = claims
        return claims
    }

    private fun workTitles(query: FandomCharacterQuery): List<String> =
        query.appearances.flatMap { it.titles }.distinct().take(MAX_WORK_TITLES)

    private fun workKeys(query: FandomCharacterQuery): List<String> =
        FandomPersonMatcher.workKeys(query.appearances.flatMap { it.titles })

    /** Bilgi kutusunda CJK/Hangul bir yerel ad var ve kaynaktakiyle uyuşmuyorsa true. */
    private fun contradictsNative(lookup: FandomTitleLookup, native: String?): Boolean {
        if (native.isNullOrBlank()) return false
        val infobox = FandomInfoboxParser.parse(lookup.infoboxJson) ?: return false
        val nativeFields = infobox.allFields().filter { field ->
            (NATIVE_LABEL.containsMatchIn(field.label) || NATIVE_LABEL.containsMatchIn(field.source.orEmpty())) &&
                FandomNameVariants.hasCjkOrHangul(field.value)
        }
        if (nativeFields.isEmpty()) return false
        return nativeFields.none { FandomNameVariants.nativeAppearsIn(native, it.value) }
    }

    /**
     * Dış isteklerdeki beklenmeyen hata, bir kaynağın diğerini (ör. Fandom sonucunu) düşürmesin.
     * İptal ([CancellationException]) yayılır; diğer hatalar ağ hatası sayılır ve null döner.
     */
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Karakter kaynağı sorgusu başarısız: ${e.message}")
        FandomHttp.markFailure()
        null
    }
}

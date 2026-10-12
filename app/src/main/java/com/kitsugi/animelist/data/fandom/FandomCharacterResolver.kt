package com.kitsugi.animelist.data.fandom

import android.content.Context

/** Karakter için bulunan wiki sayfası (henüz sayfa özeti oluşturulmadı). */
internal data class FandomResolvedCharacter(
    val wikiId: String,
    val lookup: FandomTitleLookup,
    val source: FandomMatchSource,
    val confidence: FandomConfidence,
    val siteName: String?
)

/**
 * Karakter → Fandom sayfası.
 *
 *  1. Wikidata: AniList (P11736) / MAL (P4085) karakter kimliği → P6262 sayfa kimliği.
 *     Sayfa, karakterin yapımının wiki'sinde olmalıdır (çapraz wikiler elenir).
 *  2. Yapımın wiki'si ([FandomRepository.resolveMediaWiki] — kimlik önce, isim son çare).
 *  3. Wiki içinde ad varyantlarıyla birebir başlık sorgusu (wiki'nin yönlendirmeleri dahil);
 *     kabul için bilgi kutusundaki yerel adın (Kanji/Hangul) kaynaktakiyle aynı olması
 *     ya da tek bir birebir eşleşme + bilgi kutusu (çelişen yerel ad olmadan) gerekir.
 *  4. Wiki içi arama: yalnızca yerel ad teyidiyle kabul edilir.
 */
internal class FandomCharacterResolver(
    private val context: Context,
    private val allowNameFallback: Boolean,
    private val isRejected: (wikiId: String, title: String) -> Boolean
) {

    private companion object {
        const val MAX_APPEARANCES_TO_TRY = 2
        const val MAX_TITLE_VARIANTS = 40
        val NATIVE_LABEL = Regex("(?i)(kanji|japanese|hangul|korean|chinese|native|日本語|漢字|한글)")
    }

    suspend fun resolve(query: FandomCharacterQuery): FandomResolvedCharacter? {
        val articles = wikidataArticles(query)
        val mediaWiki = resolveMediaWiki(query)

        if (articles.isNotEmpty()) {
            val onMediaWiki = mediaWiki?.let { wiki -> articles.firstOrNull { it.first == wiki.wikiId } }
            val chosen = onMediaWiki
                ?: if (mediaWiki == null) articles.firstOrNull { FandomUrls.isRoot(it.first) } else null
            if (chosen != null && !isRejected(chosen.first, chosen.second)) {
                val lookup = FandomApi.lookupTitles(chosen.first, listOf(chosen.second))[chosen.second]
                if (lookup != null && lookup.exists && !lookup.isDisambiguation) {
                    val siteName = mediaWiki?.takeIf { it.wikiId == chosen.first }?.siteName
                        ?: FandomWikiResolver.siteInfoCached(chosen.first)?.siteName
                    return FandomResolvedCharacter(
                        wikiId = chosen.first,
                        lookup = lookup,
                        source = FandomMatchSource.WIKIDATA,
                        confidence = FandomConfidence.CERTAIN,
                        siteName = siteName
                    )
                }
            }
        }

        val wiki = mediaWiki ?: return null
        val (lookup, matchConfidence) = matchInWiki(wiki.wikiId, query) ?: return null
        val confidence = if (wiki.confidence == FandomConfidence.PROBABLE) FandomConfidence.PROBABLE else matchConfidence
        return FandomResolvedCharacter(wiki.wikiId, lookup, wiki.source, confidence, wiki.siteName)
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
    private suspend fun resolveMediaWiki(query: FandomCharacterQuery): FandomWikiRef? {
        val ordered = query.appearances.sortedWith(
            compareByDescending<FandomAppearanceRef> { it.isMainRole }.thenBy { it.isManga }
        )
        ordered.take(MAX_APPEARANCES_TO_TRY).forEachIndexed { index, appearance ->
            val ref = FandomRepository.resolveMediaWiki(
                context = context,
                query = FandomMediaQuery(appearance.ids, appearance.isManga, appearance.isMovie, appearance.titles),
                allowNameFallback = allowNameFallback && index == 0
            )
            if (ref != null) return ref
        }
        return null
    }

    private suspend fun matchInWiki(
        wikiId: String,
        query: FandomCharacterQuery
    ): Pair<FandomTitleLookup, FandomConfidence>? {
        val variants = LinkedHashSet<String>()
        query.names.forEach { variants += FandomNameVariants.characterTitleVariants(it, max = 6) }
        variants += query.alternatives
        val titles = variants.filter { it.isNotBlank() }.take(MAX_TITLE_VARIANTS)
        if (titles.isEmpty()) return null

        val found = FandomApi.lookupTitles(wikiId, titles).values
            .filter { it.exists && !it.isDisambiguation }
            .filterNot { isRejected(wikiId, it.resolvedTitle.orEmpty()) }
            .distinctBy { it.resolvedTitle }
        val native = query.nativeName

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

        // Wiki içi arama — yalnızca yerel ad teyidiyle kabul edilir.
        if (!native.isNullOrBlank()) {
            val term = query.names.firstOrNull() ?: return null
            val checked = found.mapNotNull { it.resolvedTitle }.toSet()
            val candidates = FandomApi.searchPages(wikiId, term, limit = 5).filter { it !in checked }
            if (candidates.isNotEmpty()) {
                FandomApi.lookupTitles(wikiId, candidates).values.firstOrNull { lookup ->
                    lookup.exists && !lookup.isDisambiguation &&
                        !isRejected(wikiId, lookup.resolvedTitle.orEmpty()) &&
                        FandomNameVariants.nativeAppearsIn(native, lookup.evidenceText())
                }?.let { return it to FandomConfidence.CERTAIN }
            }
        }
        return null
    }

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
}

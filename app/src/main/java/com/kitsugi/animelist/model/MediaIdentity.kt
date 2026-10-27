package com.kitsugi.animelist.model

import java.text.Normalizer
import java.util.Locale

/** Provider IDs are namespaced by media type. A title must never override conflicting IDs. */
object MediaIdentity {
    private val nonIdentityCharacters = Regex("[^\\p{L}\\p{N}]")

    fun canonicalSource(source: String): String = when (val value = source.trim().lowercase(Locale.ROOT)) {
        "jikan", "myanimelist" -> "mal"
        else -> value
    }

    /**
     * `malId` is a legacy field name: for several providers it stores that provider's own ID.
     * The source is part of the ID namespace. In particular, Shikimori `target_id` is a
     * Shikimori ID, not a MAL ID, even when its numeric value happens to be below 100 million.
     */
    // PERF: keys() her tablo emisyonunda satır başına çağrılır; aşağıdaki sabit koleksiyonlar
    // eskiden çağrı başına yeniden tahsis ediliyordu (2000 satır × emisyon = binlerce
    // gereksiz küme/liste tahsisi → GC baskısı).
    private val identityCapableTypes: List<MediaType> = listOf(MediaType.Anime, MediaType.Manga)

    fun keys(entry: MediaEntry): Set<String> = buildSet {
        val prefix = entry.type.name
        val source = canonicalSource(entry.source)
        entry.malId?.let { id ->
            when {
                source == "shikimori" && id in 1..99_999_999 -> add("$prefix:shikimori:$id")
                source == "bangumi" && id in 1..99_999_999 -> add("$prefix:bangumi:$id")
                id in 1..99_999_999 && isRealMalIdSource(source) && entry.type in identityCapableTypes ->
                    add("$prefix:mal:$id")
                id in 100_000_001..299_999_999 && source == "anilist" -> add("$prefix:anilist:${id - 100_000_000}")
                id in 300_000_001..399_999_999 && source == "kitsu" -> add("$prefix:kitsu:${id - 300_000_000}")
                id in 400_000_001..499_999_999 && source == "shikimori" -> add("$prefix:shikimori:${id - 400_000_000}")
                id in 500_000_001..599_999_999 && source == "bangumi" -> add("$prefix:bangumi:${id - 500_000_000}")
                // Legacy Kitsu rows sometimes stored the raw Kitsu ID without its 300M offset.
                // Keep those in the Kitsu namespace; never reinterpret them as MAL IDs.
                source == "kitsu" && id in 1..99_999_999 -> add("$prefix:kitsu:$id")
            }
        }
        entry.simklId?.takeIf { it > 0 }?.let { add("$prefix:simkl:$it") }
        entry.tmdbId?.takeIf { it > 0 }?.let { add("$prefix:tmdb:$it") }
    }

    /**
     * Canonical keys of [entry] plus [extras] — the cross-provider crosswalk keys learned for
     * this row elsewhere (see `MyListIdentityCrosswalk`).
     *
     * A row only ever carries **its own** provider ID in `malId`, so an imported Bangumi/Kitsu/
     * Shikimori record has no key in common with the AniList or MAL record of the same title.
     * Extra keys close that gap without touching the stored row.
     */
    fun keys(entry: MediaEntry, extras: Set<String>): Set<String> =
        if (extras.isEmpty()) keys(entry) else keys(entry) + extras

    /** Returns the value only when this record's source contract says it is a real MAL ID. */
    fun realMalId(entry: MediaEntry): Int? {
        val source = canonicalSource(entry.source)
        if (!isRealMalIdSource(source)) return null
        return entry.malId?.takeIf { it in 1..99_999_999 }
    }

    /** Provider-native Shikimori ID (supports old raw IDs and the 400M stable-ID form). */
    fun shikimoriId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "shikimori") return null
        val id = entry.malId ?: return null
        return when {
            id in 1..99_999_999 -> id
            id in 400_000_001..499_999_999 -> id - 400_000_000
            else -> null
        }
    }

    /** Provider-native Bangumi subject ID (supports old raw IDs and the 500M stable-ID form). */
    fun bangumiId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "bangumi") return null
        val id = entry.malId ?: return null
        return when {
            id in 1..99_999_999 -> id
            id in 500_000_001..599_999_999 -> id - 500_000_000
            else -> null
        }
    }

    /** Provider-native Kitsu ID (supports old raw IDs and the 300M stable-ID form). */
    fun kitsuId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "kitsu") return null
        val id = entry.malId ?: return null
        return when {
            id in 1..99_999_999 -> id
            id in 300_000_001..399_999_999 -> id - 300_000_000
            else -> null
        }
    }

    /** Provider-native AniList media ID when `malId` contains its synthetic stable ID. */
    fun aniListId(entry: MediaEntry): Int? {
        if (canonicalSource(entry.source) != "anilist") return null
        return entry.malId?.takeIf { it in 100_000_001..299_999_999 }?.minus(100_000_000)
    }

    private val realMalIdSources: Set<String> = setOf("mal", "simkl", "anilist", "manual")

    private fun isRealMalIdSource(source: String): Boolean =
        source in realMalIdSources

    fun normalizedTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(nonIdentityCharacters, "")

    /** Provider-ID namespaces present on both entries but pointing at different IDs. */
    fun conflictingIdentityKeys(a: MediaEntry, b: MediaEntry): Set<String> =
        conflictingIdentityKeys(a.type, b.type, keys(a), keys(b))

    /**
     * [conflictingIdentityKeys] for precomputed key sets, so callers that merge crosswalk keys
     * into a row's identity can reuse the exact same rules.
     *
     * A namespace counts as conflicting only when the two sides share **no** value in it. That
     * matters because an enriched row can legitimately hold several IDs in one namespace (e.g. a
     * legacy Kitsu row plus a crosswalk hit); as soon as one value agrees the identity is proven.
     */
    fun conflictingIdentityKeys(
        typeA: MediaType?,
        typeB: MediaType?,
        keysA: Set<String>,
        keysB: Set<String>
    ): Set<String> {
        if (typeA != typeB) return emptySet()
        if (keysA.isEmpty() || keysB.isEmpty()) return emptySet()
        val byNamespaceA = keysA.groupByNamespace()
        val byNamespaceB = keysB.groupByNamespace()
        return byNamespaceA.keys.intersect(byNamespaceB.keys).filterTo(mutableSetOf()) { namespace ->
            byNamespaceA.getValue(namespace).intersect(byNamespaceB.getValue(namespace)).isEmpty()
        }
    }

    /** `"Anime:mal:123"` → `("Anime:mal" to "123")`; namespaces may hold several values. */
    private fun Set<String>.groupByNamespace(): Map<String, Set<String>> =
        groupBy({ it.substringBeforeLast(':') }, { it.substringAfterLast(':') })
            .mapValues { (_, values) -> values.toSet() }

    fun sameMedia(a: MediaEntry, b: MediaEntry, allowTitle: Boolean = true): Boolean =
        sameMedia(a, b, keys(a), keys(b), allowTitle)

    /**
     * [sameMedia] against precomputed identity keys. [keysA]/[keysB] may carry crosswalk keys
     * (`MediaIdentity.keys(entry, extras)`) — everything else about the decision is identical.
     */
    fun sameMedia(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String>,
        keysB: Set<String>,
        allowTitle: Boolean = true
    ): Boolean {
        if (a.type != b.type) return false
        if (conflictingIdentityKeys(a.type, b.type, keysA, keysB).isNotEmpty()) return false
        if (keysA.intersect(keysB).isNotEmpty()) return true
        if (!allowTitle) return false
        return titleMatch(a, b)
    }

    /**
     * The two rows look like the same production but share no verified ID, so the catalog title
     * decides. A known release year on both sides must agree **within one year** (a re-make with
     * the identical title is still a different production, but providers disagree by ±1 year all
     * the time: AniList stores the *season* year, Kitsu/MAL store the *premiere* year, so a
     * December/January boundary flips the number and the same title never merged).
     *
     * The ±1 tolerance matches the long-standing add-to-list rule
     * (`KitsugiModels.matchesTitleVariant`), so the two paths can no longer disagree about
     * whether a title is already in the library.
     */
    private fun titleMatch(a: MediaEntry, b: MediaEntry): Boolean {
        if (a.year != null && b.year != null && kotlin.math.abs(a.year - b.year) > 1) return false

        val normA = normalizedTitle(a.title)
        val normB = normalizedTitle(b.title)
        if (normA.isNotBlank() && normA == normB) return true

        val aTitles = listOfNotNull(a.title, a.titleEnglish).map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        val bTitles = listOfNotNull(b.title, b.titleEnglish).map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        if (aTitles.intersect(bTitles).isNotEmpty()) return true

        // Yalnızca Japonca başlık eşleştiğinde, iki kaydın ana başlıkları tamamen farklı ise
        // (örneğin Date A Bullet: Dead or Bullet vs Nightmare or Queen), farklı yapımlar say
        val aJp = a.titleJapanese?.let(::normalizedTitle)?.takeIf { it.length >= 2 }
        val bJp = b.titleJapanese?.let(::normalizedTitle)?.takeIf { it.length >= 2 }
        if (aJp != null && aJp == bJp) {
            if (normA.isBlank() || normB.isBlank() || normA.contains(normB) || normB.contains(normA)) {
                return true
            }
        }
        return false
    }

    fun sameLibraryRecord(a: MediaEntry, b: MediaEntry): Boolean =
        canonicalSource(a.source) == canonicalSource(b.source) && sameMedia(a, b)

    // ─────────────────────────────────────────────────────────────────────────────
    // Tür etiketi farklı ama AYNI yapım: Simkl'in anime/film/dizi kopyaları
    // ─────────────────────────────────────────────────────────────────────────────

    /** Film/dizi/anime türleri — aynı yapımı farklı etiketle taşıyabilen türler. */
    private val videoTypes: Set<MediaType> =
        setOf(MediaType.Anime, MediaType.Movie, MediaType.TvShow)

    /**
     * Tür etiketinden BAĞIMSIZ kanıt sayılabilen kimlik uzayları.
     *
     * Simkl tek bir `simklId` uzayı kullanır: aynı film kullanıcı kitaplığında hem
     * `movies` hem `anime` kategorisinde dönebilir ve iki satır `Movie` / `Anime` olarak
     * yazılır. Bangumi `subject_id` uzayı da türden bağımsızdır.
     *
     * **TMDB bilinçli olarak dışarıda:** TMDB'de film kimliği 123 ile dizi kimliği 123
     * birbirinden alakasız yapımlardır; `tmdbId` alanı ikisini ayırt etmez.
     */
    private val crossTypeEvidenceNamespaces: Set<String> = setOf("simkl", "bangumi")

    /**
     * `"Anime:simkl:42"` → `"simkl:42"`: tür etiketi atılmış kimlik jetonları.
     * Yalnızca [crossTypeEvidenceNamespaces] içindeki uzaylar döner.
     */
    private fun crossTypeTokens(keys: Set<String>): Set<String> = buildSet {
        keys.forEach { key ->
            val token = key.substringAfter(':') // "simkl:42"
            val namespace = token.substringBefore(':')
            if (namespace in crossTypeEvidenceNamespaces) add(token)
        }
    }

    /** İki kaydın tür etiketi aynı yapımı gösterebilecek şekilde uyumlu mu? */
    fun compatibleTypes(typeA: MediaType, typeB: MediaType): Boolean =
        typeA == typeB || (typeA in videoTypes && typeB in videoTypes)

    /**
     * Aynı yapımın farklı tür etiketli kopyalarından hangisi tercih edilir.
     *
     * Simkl aynı anime filmini `movies` ve `anime` uçlarında birlikte döndürdüğünde iki satır
     * oluşuyor; bölüm ilerlemesi, detay sayfası ve çapraz eşitleme anime ekseninde çalıştığı
     * için **Anime** etiketi kazanır.
     */
    fun preferredTypeRank(type: MediaType): Int = when (type) {
        MediaType.Anime -> 0
        MediaType.TvShow -> 1
        MediaType.Movie -> 2
        MediaType.Manga -> 3
    }

    /**
     * Tür etiketleri farklı iki satırın kanıtlanmış ortak kimlikleri.
     * Aynı türde çağrıldığında boş küme döner — o durumda [sameMedia] zaten tam kararı verir.
     */
    fun crossTypeProof(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String> = keys(a),
        keysB: Set<String> = keys(b)
    ): Set<String> {
        if (a.type == b.type) return emptySet()
        if (!compatibleTypes(a.type, b.type)) return emptySet()
        return crossTypeTokens(keysA) intersect crossTypeTokens(keysB)
    }

    /**
     * Aynı **yapım** mı? [sameMedia] tür eşitliği ister; oysa bir yapımın kopyaları
     * kaynaklarda farklı tür etiketiyle durabilir (Simkl aynı anime filmini hem
     * `movies` hem `anime` listesinde döndürür → `Movie` + `Anime` iki satır).
     *
     * Kural: türler aynıysa mevcut [sameMedia] sözleşmesi aynen işler (kimlik kesişimi
     * ya da başlık + yıl yedeği). Türler farklıysa **yalnızca kanıtlanmış ortak kimlik**
     * birleştirir; başlık benzerliği asla. Anime ↔ Manga bu yolla asla birleşmez.
     */
    fun sameProduction(
        a: MediaEntry,
        b: MediaEntry,
        keysA: Set<String> = keys(a),
        keysB: Set<String> = keys(b),
        allowTitle: Boolean = true
    ): Boolean = if (a.type == b.type) {
        sameMedia(a, b, keysA, keysB, allowTitle)
    } else {
        crossTypeProof(a, b, keysA, keysB).isNotEmpty()
    }
}

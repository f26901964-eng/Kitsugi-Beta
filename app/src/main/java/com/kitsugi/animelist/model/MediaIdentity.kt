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
     * The source is part of the ID namespace. Shikimori is the exception that must share the
     * MAL axis: its anime/manga `target_id` is the MAL ID (the official API exposes the same
     * value as `myanimelist_id`). Keeping it in an isolated Shikimori-only namespace splits
     * localized copies of the same production into different cross-sync groups.
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
                source == "shikimori" && id in 1..99_999_999 && entry.type in identityCapableTypes -> {
                    // Shikimori anime/manga IDs and MAL IDs are the same catalog identity.
                    // Retain the provider key as an alias for legacy crosswalk data, but also
                    // expose the shared MAL key so Romaji and English library rows can meet.
                    add("$prefix:mal:$id")
                    add("$prefix:shikimori:$id")
                }
                source == "bangumi" && id in 1..99_999_999 -> add("$prefix:bangumi:$id")
                id in 1..99_999_999 && isRealMalIdSource(source) && entry.type in identityCapableTypes ->
                    add("$prefix:mal:$id")
                id in 100_000_001..299_999_999 && source == "anilist" -> add("$prefix:anilist:${id - 100_000_000}")
                id in 300_000_001..399_999_999 && source == "kitsu" -> add("$prefix:kitsu:${id - 300_000_000}")
                id in 400_000_001..499_999_999 && source == "shikimori" && entry.type in identityCapableTypes -> {
                    val targetId = id - 400_000_000
                    add("$prefix:mal:$targetId")
                    add("$prefix:shikimori:$targetId")
                }
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
     * Bangumi/Kitsu rows have no native key in common with AniList or MAL, so learned extra
     * keys close that gap without touching the stored row. Shikimori already shares the MAL
     * axis; its provider key remains only as a source-specific alias.
     */
    fun keys(entry: MediaEntry, extras: Set<String>): Set<String> =
        if (extras.isEmpty()) keys(entry) else keys(entry) + extras

    /** Returns the value only when this record's source contract says it is a real MAL ID. */
    fun realMalId(entry: MediaEntry): Int? {
        val source = canonicalSource(entry.source)
        if (source == "shikimori") {
            if (entry.type !in identityCapableTypes) return null
            return shikimoriId(entry)
        }
        if (!isRealMalIdSource(source)) return null
        return entry.malId?.takeIf { it in 1..99_999_999 }
    }

    /** Shikimori target ID, which is also the MAL ID (raw or 400M stable-ID form). */
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
     * decides. A known release year on both sides must agree (a re-make with the identical title
     * is still a different production).
     */
    private fun titleMatch(a: MediaEntry, b: MediaEntry): Boolean {
        if (a.year != null && b.year != null && a.year != b.year) return false

        val normA = normalizedTitle(a.title)
        val normB = normalizedTitle(b.title)
        if (normA.isNotBlank() && normA == normB) return true

        val aTitles = listOfNotNull(a.title, a.titleEnglish).map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        val bTitles = listOfNotNull(b.title, b.titleEnglish).map(::normalizedTitle).filter { it.length >= 2 }.toSet()
        if (aTitles.intersect(bTitles).isNotEmpty()) return true

        // Yalnızca Japonca başlık eşleştiğinde, iki kaydın ana başlıkları tamamen farklı Latin
        // adlar ise (örneğin Date A Bullet: Dead or Bullet vs Nightmare or Queen), farklı yapımlar say.
        // Ancak kaynaklardan biri CJK/Latin-dışı birincil ad taşıyorsa (ör. Bangumi "怪獣8号" / "命运石之门"
        // ↔ AniList/MAL "Kaijuu 8-gou" / "Steins;Gate"), ortak normalize özgün Japonca başlık +
        // uyumlu yıl + çelişmeyen bölüm sayısı aynı yapımı güvenle kanıtlar.
        val aJp = a.titleJapanese?.let(::normalizedTitle)?.takeIf { it.length >= 2 }
        val bJp = b.titleJapanese?.let(::normalizedTitle)?.takeIf { it.length >= 2 }
        if (aJp != null && aJp == bJp) {
            if (normA.isBlank() || normB.isBlank() || normA.contains(normB) || normB.contains(normA)) {
                return true
            }
            val bothLatin = hasLatinLetters(a.title) && hasLatinLetters(b.title)
            val totalsConflict = a.total != null && b.total != null &&
                a.total > 0 && b.total > 0 && a.total != b.total
            if (!bothLatin && !totalsConflict) {
                return true
            }
        }
        return false
    }

    private fun hasLatinLetters(text: String?): Boolean =
        text?.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN } == true

    fun sameLibraryRecord(a: MediaEntry, b: MediaEntry): Boolean =
        canonicalSource(a.source) == canonicalSource(b.source) && sameMedia(a, b)
}

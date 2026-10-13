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
    fun keys(entry: MediaEntry): Set<String> = buildSet {
        val prefix = entry.type.name
        val source = canonicalSource(entry.source)
        entry.malId?.let { id ->
            when {
                source == "shikimori" && id in 1..99_999_999 -> add("$prefix:shikimori:$id")
                source == "bangumi" && id in 1..99_999_999 -> add("$prefix:bangumi:$id")
                id in 1..99_999_999 && isRealMalIdSource(source) && entry.type in listOf(MediaType.Anime, MediaType.Manga) ->
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

    private fun isRealMalIdSource(source: String): Boolean =
        source in setOf("mal", "simkl", "anilist", "manual")

    fun normalizedTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(nonIdentityCharacters, "")

    /** Provider-ID namespaces present on both entries but pointing at different IDs. */
    fun conflictingIdentityKeys(a: MediaEntry, b: MediaEntry): Set<String> {
        if (a.type != b.type) return emptySet()
        val aIds = keys(a).associateBy { it.substringBeforeLast(':') }
        val bIds = keys(b).associateBy { it.substringBeforeLast(':') }
        return aIds.keys.intersect(bIds.keys).filterTo(mutableSetOf()) { aIds[it] != bIds[it] }
    }

    fun sameMedia(a: MediaEntry, b: MediaEntry, allowTitle: Boolean = true): Boolean {
        if (a.type != b.type) return false
        val aKeys = keys(a)
        val bKeys = keys(b)
        if (conflictingIdentityKeys(a, b).isNotEmpty()) return false
        if (aKeys.intersect(bKeys).isNotEmpty()) return true
        if (!allowTitle || (a.year != null && b.year != null && a.year != b.year)) return false

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
}

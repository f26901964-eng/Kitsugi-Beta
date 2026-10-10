package com.kitsugi.animelist.data.remote

import java.util.Locale

/**
 * Identity helpers for character rows originating from TMDB credits.
 *
 * TMDB credits identify the actor/person, not a separate character entity. The same person ID
 * can therefore occur with several different role names; include both pieces in request/cache
 * identities and only accept exact external character-name matches.
 */
internal object CharacterDetailIdentity {

    fun fetchKey(source: String, characterId: Int, name: String?, isRealMediaRole: Boolean): String {
        val normalizedName = normalizeIdentityName(name)
        return "${source.trim().lowercase(Locale.ROOT)}:$characterId:$normalizedName:$isRealMediaRole"
    }

    /** A TMDB cast-member ID may represent multiple fictional roles. */
    fun cacheDiscriminator(source: String, name: String?, isRealMediaRole: Boolean): String? {
        if (!source.equals("tmdb", ignoreCase = true) || name.isNullOrBlank()) return null
        val kind = if (isRealMediaRole) "person" else "role"
        return "$kind:${normalizeIdentityName(name)}"
    }

    fun matchesExpectedDetail(
        source: String,
        expectedName: String?,
        isRealMediaRole: Boolean,
        detail: KitsugiCharacterDetail
    ): Boolean {
        if (!source.equals("tmdb", ignoreCase = true) || isRealMediaRole || expectedName.isNullOrBlank()) {
            return true
        }
        return roleNameMatches(
            expectedName,
            buildList {
                add(detail.name)
                detail.nativeName?.let(::add)
                detail.romanizedName?.let(::add)
                detail.englishName?.let(::add)
                addAll(detail.alternativeNames)
            }
        )
    }

    /** Match slash-separated TMDB role aliases only as complete names, never as substrings. */
    fun roleNameMatches(expectedName: String, actualNames: List<String>): Boolean {
        val expectedCandidates = nameCandidates(expectedName)
        val actualCandidates = actualNames
            .filter { it.isNotBlank() }
            .flatMap(::nameCandidates)
            .distinct()
        return expectedCandidates.any { expected ->
            actualCandidates.any { actual -> KitsugiPersonImageAggregator.nameMatches(expected, actual) }
        }
    }

    fun nameCandidates(value: String): List<String> = value
        .split(Regex("\\s*(?:/|\\||;)\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf(value.trim()).filter { it.isNotBlank() } }

    private fun normalizeIdentityName(value: String?): String = value.orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}

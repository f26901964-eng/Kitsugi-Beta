package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity

/**
 * Sekme sırası: 0 = Tümü (birleşik kütüphane), 1..5 = platform kütüphaneleri.
 * "Tümü" artık en başta durur; pager ve hap butonlar aynı sırayı paylaşır.
 */
internal const val MY_LIST_ALL_TAB_INDEX = 0
internal const val MY_LIST_ANILIST_TAB_INDEX = 1
internal const val MY_LIST_MAL_TAB_INDEX = 2
internal const val MY_LIST_SIMKL_TAB_INDEX = 3
internal const val MY_LIST_KITSU_TAB_INDEX = 4
internal const val MY_LIST_SHIKIMORI_TAB_INDEX = 5
internal const val MY_LIST_BANGUMI_TAB_INDEX = 6
internal const val MY_LIST_TAB_COUNT = 7

/** Eski sürümlerde "Tümü" sekmesinin index'i (0..4 = platformlar, 5 = Tümü). */
private const val LEGACY_MY_LIST_ALL_TAB_INDEX = 5

/**
 * Eski sekme sırasını yeni sıraya çevirir: 5 (Tümü) → 0, 0..4 (platformlar) → +1.
 */
internal fun migrateLegacyMyListTabIndex(legacyIndex: Int): Int = when (legacyIndex) {
    LEGACY_MY_LIST_ALL_TAB_INDEX -> MY_LIST_ALL_TAB_INDEX
    in 0 until LEGACY_MY_LIST_ALL_TAB_INDEX -> legacyIndex + 1
    else -> MY_LIST_ALL_TAB_INDEX
}

/** Verilen kaynağın hangi platform sekmesine ait olduğunu döner (Tümü için null). */
internal fun myListTabIndexForSource(source: String): Int? = when (sourceBadgeId(source)) {
    "anilist" -> MY_LIST_ANILIST_TAB_INDEX
    "mal" -> MY_LIST_MAL_TAB_INDEX
    "simkl" -> MY_LIST_SIMKL_TAB_INDEX
    "kitsu" -> MY_LIST_KITSU_TAB_INDEX
    "shikimori" -> MY_LIST_SHIKIMORI_TAB_INDEX
    "bangumi" -> MY_LIST_BANGUMI_TAB_INDEX
    else -> null
}

/** Bir kaydın verilen platform sekmesinde görünüp görünmeyeceğini söyler. */
internal fun myListSourceMatchesTab(tabIndex: Int, source: String): Boolean =
    tabIndex != MY_LIST_ALL_TAB_INDEX && myListTabIndexForSource(source) == tabIndex

/**
 * Bir sekmede yeni/manüel kayıt oluşturulurken kullanılacak varsayılan kaynak.
 * Tümü sekmesinde varsayılan AniList'tir; Simkl yalnızca açıkça seçildiğinde kullanılır.
 */
internal fun defaultMyListSourceForTab(tabIndex: Int): String = when (tabIndex) {
    MY_LIST_MAL_TAB_INDEX -> "mal"
    MY_LIST_SIMKL_TAB_INDEX -> "simkl"
    MY_LIST_KITSU_TAB_INDEX -> "kitsu"
    MY_LIST_SHIKIMORI_TAB_INDEX -> "shikimori"
    MY_LIST_BANGUMI_TAB_INDEX -> "bangumi"
    else -> "anilist"
}

/** Kullanıcıya gösterilecek kısa kaynak adı. */
internal fun myListSourceDisplayName(source: String): String = when (sourceBadgeId(source)) {
    "anilist" -> "AniList"
    "mal" -> "MAL"
    "simkl" -> "Simkl"
    "kitsu" -> "Kitsu"
    "shikimori" -> "Shikimori"
    "bangumi" -> "Bangumi"
    "tmdb" -> "TMDB"
    else -> source
}

/**
 * "Zaten listende var" uyarısı tek yerde üretilir.
 *
 * Mesaj GELEN başlığı değil, listede BULUNAN kaydı ve hangi sekmede olduğunu söyler —
 * kullanıcı uyarıyı görünce Listem'de tam olarak neyi, nerede arayacağını bilsin diye.
 */
internal fun duplicateListMessage(entry: MediaEntry): String =
    "\"${entry.title}\" zaten listende var (${myListSourceDisplayName(entry.source)} sekmesi)."

/**
 * Birleşik kütüphanede bir başlığın temsilci kaydının önceliği.
 * AniList en hızlı ve en zengin kaynak olduğu için ilk sırada, Simkl ise
 * (API'si gecikmeli yanıt verdiğinden) son sırada yer alır.
 * Dizi/film kayıtları yalnızca Simkl/TMDB'de bulunduğu için bu türlerde
 * Simkl doğal olarak temsilci olur.
 */
internal fun myListRepresentativeRank(entry: MediaEntry): Int = when (sourceBadgeId(entry.source)) {
    "anilist" -> 0
    "mal" -> 1
    "kitsu" -> 2
    "shikimori" -> 3
    "bangumi" -> 4
    "simkl" -> 5
    "tmdb" -> 6
    else -> 7
}

/** Daha düşük öncelik numarası kazanır; eşitlikte en yeni kayıt temsilci olur. */
private fun isBetterRepresentative(candidate: MediaEntry, current: MediaEntry): Boolean {
    val candidateRank = myListRepresentativeRank(candidate)
    val currentRank = myListRepresentativeRank(current)
    return if (candidateRank != currentRank) candidateRank < currentRank else candidate.id > current.id
}

/** One visible item in the combined library, with the provider logos that contain it. */
internal data class MyListLibraryItem(
    val entry: MediaEntry,
    val sourceIds: List<String>
)

private data class MutableMyListLibraryGroup(
    var representative: MediaEntry,
    val entries: MutableList<MediaEntry> = mutableListOf(),
    val sourceIds: LinkedHashSet<String> = linkedSetOf()
)

/**
 * Collapses copies of the same title across the connected list providers without modifying
 * the underlying records. The representative is the newest inserted record; progress and
 * status changes remain scoped to that provider, while [sourceIds] exposes every provider
 * that has a matching record.
 */
internal fun groupMyListEntries(entries: List<MediaEntry>): List<MyListLibraryItem> {
    if (entries.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableMyListLibraryGroup>()
    val groupIndexesByIdentity = mutableMapOf<String, MutableList<Int>>()

    entries.forEach { entry ->
        val candidateKeys = identityCandidateKeys(entry)
        val candidateGroups = candidateKeys
            .asSequence()
            .flatMap { groupIndexesByIdentity[it].orEmpty().asSequence() }
            .distinct()
            .sorted()

        val matchingGroupIndex = candidateGroups.firstOrNull { groupIndex ->
            val groupEntries = groups[groupIndex].entries
            groupEntries.none { existing ->
                MediaIdentity.conflictingIdentityKeys(existing, entry).isNotEmpty()
            } && groupEntries.any { existing ->
                MediaIdentity.sameMedia(existing, entry)
            }
        }

        val groupIndex = matchingGroupIndex ?: groups.size.also {
            groups += MutableMyListLibraryGroup(representative = entry)
        }
        val group = groups[groupIndex]

        if (matchingGroupIndex != null) {
            group.entries += entry
            if (isBetterRepresentative(entry, group.representative)) {
                group.representative = entry
            }
        } else {
            group.entries += entry
        }

        sourceBadgeId(entry.source)?.let(group.sourceIds::add)
        candidateKeys.forEach { key ->
            val indexedGroups = groupIndexesByIdentity.getOrPut(key) { mutableListOf() }
            if (groupIndex !in indexedGroups) indexedGroups += groupIndex
        }
    }

    return groups.map { group ->
        MyListLibraryItem(
            // Adult metadata from any linked provider is authoritative for the combined
            // tile: don't let a non-adult representative unblur a copy flagged +18 elsewhere.
            entry = group.representative.copy(isAdult = group.entries.any { it.isAdult }),
            sourceIds = group.sourceIds.toList()
        )
    }
}

/** Canonical IDs are shared by source logos, source tabs, and search-result aliases. */
internal fun sourceBadgeId(source: String): String? = when (source.trim().lowercase()) {
    "anilist", "al" -> "anilist"
    "mal", "myanimelist", "jikan", "jikan (mal)", "mal (jikan)" -> "mal"
    "simkl" -> "simkl"
    "kitsu" -> "kitsu"
    "shikimori", "shiki" -> "shikimori"
    "bangumi", "bgm", "bgm.tv" -> "bangumi"
    "tmdb", "themoviedb" -> "tmdb"
    else -> null
}

private fun identityCandidateKeys(entry: MediaEntry): Set<String> = buildSet {
    MediaIdentity.keys(entry).forEach { add("id:$it") }
    listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese).forEach { title ->
        val normalized = MediaIdentity.normalizedTitle(title)
        if (normalized.isNotBlank()) add("title:${entry.type.name}:$normalized")
    }
}

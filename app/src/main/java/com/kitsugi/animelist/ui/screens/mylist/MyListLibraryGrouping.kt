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

/**
 * Bangumi sekmesi yalnızca bir Bangumi subject ID'si taşıyan (Bangumi koleksiyonundan
 * içe aktarılmış veya Bangumi'ye başarıyla eklenmiş) kayıtları gösterir. Eski manuel
 * `source = bangumi` kayıtları hesap koleksiyon üyeliği gibi gösterilmemelidir.
 */
internal fun isBangumiListMember(source: String, malId: Int?): Boolean =
    sourceBadgeId(source) == "bangumi" && malId != null && malId in 500_000_001..599_999_999

/** Bir kaydın verilen platform sekmesinde görünüp görünmeyeceğini söyler. */
internal fun myListSourceMatchesTab(tabIndex: Int, source: String, malId: Int? = null): Boolean = when {
    tabIndex == MY_LIST_ALL_TAB_INDEX -> false
    tabIndex == MY_LIST_BANGUMI_TAB_INDEX -> isBangumiListMember(source, malId)
    else -> myListTabIndexForSource(source) == tabIndex
}

/**
 * Bir sekmede yeni/manüel kayıt oluşturulurken kullanılacak varsayılan kaynak.
 * Tümü sekmesinde varsayılan AniList'tir; Simkl yalnızca açıkça seçildiğinde kullanılır.
 */
internal fun defaultMyListSourceForTab(tabIndex: Int): String = when (tabIndex) {
    MY_LIST_MAL_TAB_INDEX -> "mal"
    MY_LIST_SIMKL_TAB_INDEX -> "simkl"
    MY_LIST_KITSU_TAB_INDEX -> "kitsu"
    MY_LIST_SHIKIMORI_TAB_INDEX -> "shikimori"
    // Manual entries have no verified Bangumi collection membership.
    MY_LIST_BANGUMI_TAB_INDEX -> "manual"
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
    val members: MutableList<IdentityKeysOf> = mutableListOf(),
    val sourceIds: LinkedHashSet<String> = linkedSetOf()
)

/**
 * Collapses copies of the same title across the connected list providers without modifying
 * the underlying records. The representative is the newest inserted record; progress and
 * status changes remain scoped to that provider, while [sourceIds] exposes every provider
 * that has a matching record.
 *
 * [identityExtras] maps a row's `MediaEntry.id` to cross-provider identity keys learned
 * elsewhere (`MyListIdentityCrosswalk`: the cross-provider ID cache and the MAL ↔ Bangumi
 * mapping). A row only carries **its own** provider ID, so without these keys an imported
 * Bangumi/Kitsu/Shikimori copy has nothing in common with the AniList/MAL copy and can only
 * merge by title — which fails for non-Latin catalog titles (Bangumi stores `name_cn`), and
 * every copy then becomes its own tile in "Tümü". See the report
 * `KOK_NEDEN_RAPORU_LISTEM_TUMU_SAYI_SISMESI_BANGUMI_2026-10-13.md`.
 */
internal fun groupMyListEntries(
    entries: List<MediaEntry>,
    identityExtras: Map<Int, Set<String>> = emptyMap()
): List<MyListLibraryItem> {
    if (entries.isEmpty()) return emptyList()

    // Identity keys are resolved once per row: the matcher below compares every entry against
    // the groups it has already opened, so recomputing `MediaIdentity.keys` in that loop was
    // the dominant cost of building a multi-thousand row combined library.
    val groupIndexesByIdentity = mutableMapOf<String, MutableList<Int>>()
    val groups = mutableListOf<MutableMyListLibraryGroup>()

    entries.forEach { entry ->
        val identity = IdentityKeysOf(
            entry = entry,
            keys = if (identityExtras.isEmpty()) MediaIdentity.keys(entry)
            else MediaIdentity.keys(entry, identityExtras[entry.id].orEmpty())
        )

        val candidateKeys = identityCandidateKeys(identity)
        val candidateGroups = candidateKeys
            .asSequence()
            .flatMap { groupIndexesByIdentity[it].orEmpty().asSequence() }
            .distinct()
            .sorted()

        val matchingGroupIndex = candidateGroups.firstOrNull { groupIndex ->
            val groupEntries = groups[groupIndex].members
            groupEntries.none { existing ->
                MediaIdentity.conflictingIdentityKeys(
                    typeA = existing.entry.type,
                    typeB = entry.type,
                    keysA = existing.keys,
                    keysB = identity.keys
                ).isNotEmpty()
            } && groupEntries.any { existing ->
                MediaIdentity.sameMedia(
                    a = existing.entry,
                    b = entry,
                    keysA = existing.keys,
                    keysB = identity.keys
                )
            }
        }

        val groupIndex = matchingGroupIndex ?: groups.size.also {
            groups += MutableMyListLibraryGroup(representative = entry)
        }
        val group = groups[groupIndex]

        if (matchingGroupIndex != null) {
            group.members += identity
            if (isBetterRepresentative(entry, group.representative)) {
                group.representative = entry
            }
        } else {
            group.members += identity
        }

        sourceBadgeId(entry.source)?.let(group.sourceIds::add)
        candidateKeys.forEach { key ->
            val indexedGroups = groupIndexesByIdentity.getOrPut(key) { mutableListOf() }
            if (groupIndex !in indexedGroups) indexedGroups += groupIndex
        }
    }

    // ── 2. tur: aynı yapımın farklı gruplarını birleştir ───────────────────────
    // İlk geçiş açgözlüdür: satır, aday gruplardan *ilk* uyanın içine konur. Kayıtlar hangi
    // sırada geldiği (içe aktarma sırası!) iki ayrı grubun açılmasını belirleyebiliyor ve
    // gruplar birbirine hiç eklenmediği için aynı içerik "Tümü" sekmesinde iki kart olarak
    // kalıyordu. Bu tur, aday anahtarı çakışan grupları üye üye yeniden doğrulayıp birleştirir;
    // sonuç artık sıra bağımsızdır ve tek bir tarama maliyeti üretir.
    val parent = MutableList(groups.size) { it }

    fun root(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]] // path halving
            current = parent[current]
        }
        return current
    }

    if (groups.size > 1) {
        // Birleşme, tek bir satırın gruba katılmasından daha gevşek olamaz: tüm üye çiftleri
        // çakışmasız olmalı ve en az bir çift gerçekten aynı yapım olmalı.
        fun compatible(left: Int, right: Int): Boolean {
            val leftMembers = groups[left].members
            val rightMembers = groups[right].members
            for (existing in leftMembers) {
                for (candidate in rightMembers) {
                    val conflict = MediaIdentity.conflictingIdentityKeys(
                        typeA = existing.entry.type,
                        typeB = candidate.entry.type,
                        keysA = existing.keys,
                        keysB = candidate.keys
                    )
                    if (conflict.isNotEmpty()) return false
                }
            }
            return leftMembers.any { existing ->
                rightMembers.any { candidate ->
                    MediaIdentity.sameMedia(
                        a = existing.entry,
                        b = candidate.entry,
                        keysA = existing.keys,
                        keysB = candidate.keys
                    )
                }
            }
        }

        for (groupIndexes in groupIndexesByIdentity.values) {
            if (groupIndexes.size < 2) continue
            var survivor = root(groupIndexes.first())
            for (candidateIndex in groupIndexes.drop(1)) {
                val other = root(candidateIndex)
                if (other == survivor) continue
                if (!compatible(survivor, other)) {
                    survivor = other
                    continue
                }
                val target = groups[survivor]
                val source = groups[other]
                target.members += source.members
                source.sourceIds.forEach { badge -> if (badge !in target.sourceIds) target.sourceIds += badge }
                if (isBetterRepresentative(source.representative, target.representative)) {
                    target.representative = source.representative
                }
                parent[other] = survivor
            }
        }
    }

    return groups.mapIndexedNotNull { groupIndex, group ->
        if (root(groupIndex) != groupIndex) return@mapIndexedNotNull null
        MyListLibraryItem(
            // Adult metadata from any linked provider is authoritative for the combined
            // tile: don't let a non-adult representative unblur a copy flagged +18 elsewhere.
            //
            // EKLENME ZAMANI: birleşik kartta "bu yapım listeme ne zaman girdi" sorusunun
            // cevabı, kopyalardan EN YENİ eklenmiş olanıdır. Kullanıcı bir yapımı bugün
            // Shikimori'ye eklediyse (AniList kopyası eski olsa bile) Tümü sekmesinde
            // "Son eklenen" sıralamasının en üstünde görmelidir. Sekme bazlı listeler kendi
            // satırlarının (sağlayıcının verdiği) tarihini korur.
            entry = group.representative.copy(
                isAdult = group.members.any { it.entry.isAdult },
                addedAt = group.members
                    .mapNotNull { it.entry.addedAt.takeIf { value -> value > 0L } }
                    .maxOrNull()
                    ?: group.representative.addedAt
            ),
            sourceIds = group.sourceIds.toList()
        )
    }
}

/** A row together with its resolved identity keys (own provider IDs + crosswalk extras). */
private data class IdentityKeysOf(val entry: MediaEntry, val keys: Set<String>)

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

/**
 * Keys used to *find* candidate groups. They are a superset of the identity keys (normalized
 * titles included) — the identity check below still has to confirm the match, so a loose title
 * key can only ever produce a candidate, never a merge.
 */
private fun identityCandidateKeys(identity: IdentityKeysOf): Set<String> = buildSet {
    identity.keys.forEach { add("id:$it") }
    val entry = identity.entry
    listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese).forEach { title ->
        val normalized = MediaIdentity.normalizedTitle(title)
        if (normalized.isNotBlank()) add("title:${entry.type.name}:$normalized")
    }
}

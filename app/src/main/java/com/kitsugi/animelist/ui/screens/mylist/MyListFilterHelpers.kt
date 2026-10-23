package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.ui.utils.localizedLabel
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kitsugi.animelist.R
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.settings.KitsugiContentPrefs
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.WatchStatus
import java.util.Locale

private val SEARCH_TOKEN_SPLIT_REGEX = Regex("[^\\p{L}\\p{N}]+")

/**
 * Türkçe İ/I/ı/i dönüşümünü ve birleşen nokta imini (`\u0307`) bozmadan arama metnini katlar.
 */
internal fun foldMyListSearchText(value: String): String {
    if (value.isEmpty()) return ""
    val sb = StringBuilder(value.length)
    val lowered = value.lowercase(Locale.ROOT)
    for (ch in lowered) {
        when (ch) {
            '\u0307' -> Unit
            'ı' -> sb.append('i')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/**
 * Listem ekranındaki bütün istemci tarafı filtrelerini tek bir sözleşmede uygular.
 *
 * Kendi listemiz ve salt-okunur kullanıcı listeleri aynı yardımcıyı kullanır. Böylece
 * arama/filtre davranışı ekranlar arasında zamanla birbirinden kopmaz.
 *
 * [searchAliasesByEntryId] verildiğinde, bir kaydın kendi sekmesinden çıkmadan doğrulanmış
 * kardeş başlık varyantlarıyla (ör. Simkl/Shikimori/Bangumi kaydının İngilizce veya Romaji adı)
 * eşleşmesi sağlanır; kaynak/tür/durum/sentetik ID alanları ise yanlış pozitif üretmemesi için
 * başlık aramasına katılmaz.
 */
internal fun filterMyListEntries(
    entries: List<MediaEntry>,
    searchQuery: String,
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedFavoriteFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    searchAliasesByEntryId: Map<Int, Set<String>> = emptyMap()
): List<MediaEntry> {
    val selectedStatus = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.status
    val selectedType = typeFilters.firstOrNull { it.id == selectedTypeFilterId }?.type
    val favoritesOnly = selectedFavoriteFilterId == "favorites" || selectedStatusFilterId == "favorites"
    val adultOnly = selectedStatusFilterId == "adult"
    val trimmedQuery = searchQuery.trim()
    val foldedQuery = foldMyListSearchText(trimmedQuery)
    val normQuery = if (foldedQuery.isBlank()) "" else foldMyListSearchText(MediaIdentity.normalizedTitle(trimmedQuery))
    val queryTokens = if (foldedQuery.isBlank()) {
        emptyList()
    } else {
        foldedQuery.split(SEARCH_TOKEN_SPLIT_REGEX).filter { it.isNotEmpty() }
    }
    val exactYearQuery = foldedQuery.takeIf { it.length == 4 && it.all(Char::isDigit) }
    val fuzzyEnabled = KitsugiContentPrefs.fuzzySearchEnabled

    return entries.asSequence()
        .filter { entry -> selectedStatus == null || entry.status == selectedStatus }
        .filter { entry -> selectedType == null || entry.type == selectedType }
        .filter { entry -> !favoritesOnly || entry.isFavorite }
        .filter { entry -> !adultOnly || entry.isAdult }
        .filter { entry ->
            when (selectedScoreFilterId) {
                "high" -> (entry.score ?: 0) >= 8
                "mid" -> (entry.score ?: 0) in 5..7
                "low" -> (entry.score ?: 0) in 1..4
                "unrated" -> entry.score == null || entry.score == 0
                else -> true
            }
        }
        .filter { entry ->
            when (selectedYearFilterId) {
                "new" -> (entry.year ?: 0) >= 2025
                "2020s" -> (entry.year ?: 0) in 2020..2024
                "2010s" -> (entry.year ?: 0) in 2010..2019
                "2000s" -> (entry.year ?: 0) in 2000..2009
                "classic" -> (entry.year ?: 0) in 1..1999
                else -> true
            }
        }
        .filter { entry ->
            when (selectedExtraFilterId) {
                "repeating" -> entry.isRepeating || entry.status == WatchStatus.Repeating
                "private" -> entry.isPrivate
                "ongoing" -> entry.status != WatchStatus.Completed && entry.status != WatchStatus.Dropped
                else -> true
            }
        }
        .filter { entry ->
            if (foldedQuery.isBlank()) {
                true
            } else {
                entryMatchesMyListSearchQuery(
                    entry = entry,
                    foldedQuery = foldedQuery,
                    normQuery = normQuery,
                    queryTokens = queryTokens,
                    exactYearQuery = exactYearQuery,
                    fuzzyEnabled = fuzzyEnabled,
                    extraAliases = searchAliasesByEntryId[entry.id].orEmpty()
                )
            }
        }
        .toList()
}

private fun entryMatchesMyListSearchQuery(
    entry: MediaEntry,
    foldedQuery: String,
    normQuery: String,
    queryTokens: List<String>,
    exactYearQuery: String?,
    fuzzyEnabled: Boolean,
    extraAliases: Set<String>
): Boolean {
    if (exactYearQuery != null && entry.year?.toString() == exactYearQuery) {
        return true
    }

    if (candidateTitleMatches(entry.title, foldedQuery, normQuery, queryTokens, fuzzyEnabled)) {
        return true
    }
    val english = entry.titleEnglish
    if (!english.isNullOrBlank() &&
        candidateTitleMatches(english, foldedQuery, normQuery, queryTokens, fuzzyEnabled)
    ) {
        return true
    }
    val japanese = entry.titleJapanese
    if (!japanese.isNullOrBlank() &&
        candidateTitleMatches(japanese, foldedQuery, normQuery, queryTokens, fuzzyEnabled)
    ) {
        return true
    }
    if (extraAliases.isNotEmpty()) {
        for (alias in extraAliases) {
            if (alias == entry.title || alias == entry.titleEnglish || alias == entry.titleJapanese) continue
            if (candidateTitleMatches(alias, foldedQuery, normQuery, queryTokens, fuzzyEnabled)) {
                return true
            }
        }
    }
    return false
}

private fun candidateTitleMatches(
    candidate: String,
    foldedQuery: String,
    normQuery: String,
    queryTokens: List<String>,
    fuzzyEnabled: Boolean
): Boolean {
    if (candidate.isBlank()) return false

    val foldedCandidate = foldMyListSearchText(candidate)
    if (foldedCandidate.contains(foldedQuery)) return true

    if (normQuery.length >= 2) {
        val normCandidate = foldMyListSearchText(MediaIdentity.normalizedTitle(candidate))
        if (normCandidate.isNotEmpty() && normCandidate.contains(normQuery)) return true
    }

    val candidateTokens by lazy(LazyThreadSafetyMode.NONE) {
        foldedCandidate.split(SEARCH_TOKEN_SPLIT_REGEX).filter { it.isNotEmpty() }
    }

    if (queryTokens.size >= 2) {
        if (candidateTokens.isNotEmpty() && multiTokenMatches(candidateTokens, queryTokens, fuzzyEnabled)) {
            return true
        }
    }

    if (fuzzyEnabled && queryTokens.size == 1) {
        val singleToken = queryTokens[0]
        if (singleToken.length >= 3 && singleToken.none(Char::isDigit) && candidateTokens.isNotEmpty()) {
            // Baş harf kısaltması (ör. "aot" -> "Attack on Titan", "fmab" -> "Fullmetal Alchemist Brotherhood")
            if (candidateTokens.size >= 3) {
                val initials = buildString(candidateTokens.size) {
                    candidateTokens.forEach { append(it[0]) }
                }
                if (initials.contains(singleToken)) return true
            }
            // Kelime bazlı yazım hatası toleransı (rakam içeren sorgularda sezon/numara karışmaması için kapalı)
            if (singleToken.length >= 4 && singleWordTypoMatches(candidateTokens, singleToken)) {
                return true
            }
        }
    }

    return false
}

private fun multiTokenMatches(
    candidateTokens: List<String>,
    queryTokens: List<String>,
    fuzzyEnabled: Boolean
): Boolean = queryTokens.all { qToken ->
    when {
        qToken.all(Char::isDigit) -> candidateTokens.any { it == qToken }
        qToken.length == 1 -> candidateTokens.any { it.startsWith(qToken) }
        else -> candidateTokens.any { it.contains(qToken) } ||
            (fuzzyEnabled && qToken.length >= 4 && qToken.none(Char::isDigit) && singleWordTypoMatches(candidateTokens, qToken))
    }
}

private fun singleWordTypoMatches(candidateTokens: List<String>, token: String): Boolean {
    val tolerance = if (token.length <= 6) 1 else 2
    return candidateTokens.any { word ->
        word.none(Char::isDigit) &&
            kotlin.math.abs(word.length - token.length) <= tolerance &&
            boundedLevenshtein(word, token, tolerance) <= tolerance
    }
}

private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
    if (a == b) return 0
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        var rowMin = curr[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            val value = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            curr[j] = value
            if (value < rowMin) rowMin = value
        }
        if (rowMin > maxDistance) return maxDistance + 1
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[b.length]
}

internal fun groupMyListEntriesByStatus(
    entries: List<MediaEntry>
): List<Pair<WatchStatus, List<MediaEntry>>> {
    val statusOrder = listOf(
        WatchStatus.Watching,
        WatchStatus.Repeating,
        WatchStatus.Planned,
        WatchStatus.Paused,
        WatchStatus.Dropped,
        WatchStatus.Completed
    )
    return statusOrder.map { status -> status to entries.filter { it.status == status } }
        .filter { (_, statusEntries) -> statusEntries.isNotEmpty() }
}

internal fun applySort(
    entries: List<MediaEntry>,
    sortId: String
): List<MediaEntry> {
    if (entries.size < 2) return entries

    // PERF: Kotlin `sortedBy(selector)` seçiciyi HER karşılaştırmada çağırır — başlık
    // sıralarında bu, O(n log n) adet `lowercase()` String tahsisi demektir (1000+ kayıtta
    // her veri değişiminde on binlerce gereksiz tahsis). Birincil anahtar başlık olan iki
    // dal için anahtarları satır başına bir kez hesapla (Schwartzian transform).
    fun sortedByTitleKey(descending: Boolean): List<MediaEntry> {
        val decorated = entries.map { entry -> entry to entry.title.lowercase() }
        val sorted = if (descending) decorated.sortedByDescending { it.second } else decorated.sortedBy { it.second }
        return sorted.asSequence().map { it.first }.toList()
    }

    return when (sortId) {
        "newest" -> entries.sortedByDescending { it.id }
        "oldest" -> entries.sortedBy { it.id }
        "title" -> sortedByTitleKey(descending = false)
        "title_desc" -> sortedByTitleKey(descending = true)
        "score" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.score ?: -1 }.thenBy { it.title.lowercase() }
        )
        "score_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { it.score ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }
        )
        "progress" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.progress }.thenBy { it.title.lowercase() }
        )
        "progress_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { it.progress }.thenBy { it.title.lowercase() }
        )
        "favorites" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.isFavorite }.thenBy { it.title.lowercase() }
        )
        "start_date_desc" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.startDate ?: "" }.thenBy { it.title.lowercase() }
        )
        "start_date_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { if (it.startDate.isNullOrBlank()) "9999" else it.startDate }
                .thenBy { it.title.lowercase() }
        )
        "end_date_desc" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.endDate ?: "" }.thenBy { it.title.lowercase() }
        )
        "end_date_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { if (it.endDate.isNullOrBlank()) "9999" else it.endDate }
                .thenBy { it.title.lowercase() }
        )
        "year_desc" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.year ?: 0 }.thenBy { it.title.lowercase() }
        )
        "year_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { it.year ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }
        )
        "updated_desc" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.updatedAt }.thenBy { it.title.lowercase() }
        )
        "updated_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { it.updatedAt }.thenBy { it.title.lowercase() }
        )
        "repeat_desc" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.repeatCount }.thenBy { it.title.lowercase() }
        )
        "repeat_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { it.repeatCount }.thenBy { it.title.lowercase() }
        )
        "priority_desc" -> entries.sortedWith(
            compareByDescending<MediaEntry> { it.priority ?: 0 }.thenBy { it.title.lowercase() }
        )
        "priority_asc" -> entries.sortedWith(
            compareBy<MediaEntry> { it.priority ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }
        )
        else -> entries.sortedByDescending { it.id }
    }
}

@Composable
internal fun getSortTitle(sortId: String): String = when (sortId) {
    "score" -> "Puan (En yüksek)"
    "score_asc" -> "Puan (En düşük)"
    else -> stringResource(
        when (sortId) {
            "newest" -> R.string.mylist_sort_newest
            "oldest" -> R.string.mylist_sort_oldest
            "title" -> R.string.mylist_sort_title_asc
            "title_desc" -> R.string.mylist_sort_title_desc
            "progress" -> R.string.mylist_sort_progress_high
            "progress_asc" -> R.string.mylist_sort_progress_low
            "favorites" -> R.string.mylist_sort_favorites
            "start_date_desc" -> R.string.mylist_sort_start_new
            "start_date_asc" -> R.string.mylist_sort_start_old
            "end_date_desc" -> R.string.mylist_sort_end_new
            "end_date_asc" -> R.string.mylist_sort_end_old
            "year_desc" -> R.string.mylist_sort_year_new
            "year_asc" -> R.string.mylist_sort_year_old
            "updated_desc" -> R.string.mylist_sort_updated
            "updated_asc" -> R.string.mylist_sort_updated_old
            "repeat_desc" -> R.string.mylist_sort_repeat_high
            "repeat_asc" -> R.string.mylist_sort_repeat_low
            "priority_desc" -> R.string.mylist_sort_priority_high
            "priority_asc" -> R.string.mylist_sort_priority_low
            else -> R.string.mylist_sort_newest
        }
    )
}

internal fun cardSpacingForLayout(layoutId: String): Dp {
    return when (layoutId) {
        "compact"    -> 8.dp
        "large"      -> 18.dp
        "grid_2col"  -> 8.dp
        else         -> 12.dp  // comfortable (varsayılan)
    }
}

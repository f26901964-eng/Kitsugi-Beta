package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.kitsugi.animelist.R
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus

/**
 * Listem ekranındaki bütün istemci tarafı filtrelerini tek bir sözleşmede uygular.
 *
 * Kendi listemiz ve salt-okunur kullanıcı listeleri aynı yardımcıyı kullanır. Böylece
 * arama/filtre davranışı ekranlar arasında zamanla birbirinden kopmaz.
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
    searchIndex: MyListSearchIndex? = null,
    fuzzySearchEnabled: Boolean = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzySearchEnabled,
    checkActive: () -> Unit = {}
): List<MediaEntry> {
    val selectedStatus = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.status
    val selectedType = typeFilters.firstOrNull { it.id == selectedTypeFilterId }?.type
    val favoritesOnly = selectedFavoriteFilterId == "favorites" || selectedStatusFilterId == "favorites"
    val adultOnly = selectedStatusFilterId == "adult"
    val query = MyListSearchQuery(searchQuery, fuzzySearchEnabled)
    // The index must refer to this exact snapshot/order; never reuse a document for a
    // changed row or a coincidentally equal provider ID.
    val index = searchIndex?.takeIf { it.entries === entries }

    return entries.filterIndexed { position, entry ->
        if (position % 32 == 0) checkActive()
        (selectedStatus == null || entry.status == selectedStatus) &&
            (selectedType == null || entry.type == selectedType) &&
            (!favoritesOnly || entry.isFavorite) &&
            (!adultOnly || entry.isAdult) &&
            when (selectedScoreFilterId) {
                "high" -> (entry.score ?: 0) >= 8
                "mid" -> (entry.score ?: 0) in 5..7
                "low" -> (entry.score ?: 0) in 1..4
                "unrated" -> entry.score == null || entry.score == 0
                else -> true
            } &&
            when (selectedYearFilterId) {
                "new" -> (entry.year ?: 0) >= 2025
                "2020s" -> (entry.year ?: 0) in 2020..2024
                "2010s" -> (entry.year ?: 0) in 2010..2019
                "2000s" -> (entry.year ?: 0) in 2000..2009
                "classic" -> (entry.year ?: 0) in 1..1999
                else -> true
            } &&
            when (selectedExtraFilterId) {
                "repeating" -> entry.isRepeating || entry.status == WatchStatus.Repeating
                "private" -> entry.isPrivate
                "ongoing" -> entry.status != WatchStatus.Completed && entry.status != WatchStatus.Dropped
                else -> true
            } &&
            (query.isBlank || (index?.matchesAt(position, query)
                ?: MyListSearchDocument(entry).matches(query)))
    }.also { checkActive() }
}

private val myListStatusOrder = listOf(
    WatchStatus.Watching,
    WatchStatus.Repeating,
    WatchStatus.Planned,
    WatchStatus.Paused,
    WatchStatus.Dropped,
    WatchStatus.Completed
)

internal fun groupMyListEntriesByStatus(
    entries: List<MediaEntry>,
    checkActive: () -> Unit = {}
): List<Pair<WatchStatus, List<MediaEntry>>> {
    // One pass, retaining the current sort order within each section.
    val groups = mutableMapOf<WatchStatus, MutableList<MediaEntry>>()
    entries.forEachIndexed { index, entry ->
        if (index % 32 == 0) checkActive()
        groups.getOrPut(entry.status) { mutableListOf() }.add(entry)
    }
    checkActive()
    return myListStatusOrder.mapNotNull { status -> groups[status]?.let { status to it } }
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

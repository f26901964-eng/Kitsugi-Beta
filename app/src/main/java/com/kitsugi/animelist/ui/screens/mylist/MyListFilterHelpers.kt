package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.ui.unit.Dp
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
    selectedExtraFilterId: String
): List<MediaEntry> {
    val selectedStatus = statusFilters.firstOrNull { it.id == selectedStatusFilterId }?.status
    val selectedType = typeFilters.firstOrNull { it.id == selectedTypeFilterId }?.type
    val favoritesOnly = selectedFavoriteFilterId == "favorites" || selectedStatusFilterId == "favorites"
    val adultOnly = selectedStatusFilterId == "adult"
    val normalizedQuery = searchQuery.trim().lowercase()
    val normQuery = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(normalizedQuery)

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
            if (normalizedQuery.isBlank()) {
                true
            } else {
                val normQueryMatch = normQuery.length >= 2 && (
                    com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(entry.title).contains(normQuery) ||
                        entry.titleEnglish?.let {
                            com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it).contains(normQuery)
                        } == true ||
                        entry.titleJapanese?.let {
                            com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it).contains(normQuery)
                        } == true
                )
                entry.title.lowercase().contains(normalizedQuery) ||
                    entry.titleEnglish?.lowercase()?.contains(normalizedQuery) == true ||
                    entry.titleJapanese?.lowercase()?.contains(normalizedQuery) == true ||
                    entry.subtitle.lowercase().contains(normalizedQuery) ||
                    entry.type.name.lowercase().contains(normalizedQuery) ||
                    entry.status.label.lowercase().contains(normalizedQuery) ||
                    entry.source.lowercase().contains(normalizedQuery) ||
                    entry.year?.toString()?.contains(normalizedQuery) == true ||
                    entry.malId?.toString()?.contains(normalizedQuery) == true ||
                    normQueryMatch ||
                    // AniHyou paritesi: "Fuzzy arama kullan" — yazım hatası/kısaltma toleransı
                    (
                        com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzySearchEnabled && (
                            com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzyMatches(entry.title, normalizedQuery) ||
                                com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzyMatches(entry.titleEnglish.orEmpty(), normalizedQuery) ||
                                com.kitsugi.animelist.data.settings.KitsugiContentPrefs.fuzzyMatches(entry.titleJapanese.orEmpty(), normalizedQuery)
                            )
                        )
            }
        }
        .toList()
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
    return when (sortId) {
        "newest" -> entries.sortedByDescending { it.id }
        "oldest" -> entries.sortedBy { it.id }
        "title" -> entries.sortedBy { it.title.lowercase() }
        "title_desc" -> entries.sortedByDescending { it.title.lowercase() }
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

internal fun getSortTitle(sortId: String): String {
    return when (sortId) {
        "newest" -> "Son eklenen"
        "oldest" -> "İlk eklenen"
        "title" -> "Başlık (A-Z)"
        "title_desc" -> "Başlık (Z-A)"
        "score" -> "Puan (En yüksek)"
        "score_asc" -> "Puan (En düşük)"
        "progress" -> "İlerleme (En çok)"
        "progress_asc" -> "İlerleme (En az)"
        "favorites" -> "Favoriler"
        "start_date_desc" -> "Başlangıç (Yeni)"
        "start_date_asc" -> "Başlangıç (Eski)"
        "end_date_desc" -> "Bitiş (Yeni)"
        "end_date_asc" -> "Bitiş (Eski)"
        "year_desc" -> "Yayın Yılı (Yeni)"
        "year_asc" -> "Yayın Yılı (Eski)"
        "updated_desc" -> "Son Güncelleme"
        "updated_asc" -> "Güncelleme (Eski)"
        "repeat_desc" -> "Tekrar Sayısı (Çok)"
        "repeat_asc" -> "Tekrar Sayısı (Az)"
        "priority_desc" -> "Öncelik (Yüksek)"
        "priority_asc" -> "Öncelik (Düşük)"
        else -> "Son eklenen"
    }
}

internal fun cardSpacingForLayout(layoutId: String): Dp {
    return when (layoutId) {
        "compact"    -> 8.dp
        "large"      -> 18.dp
        "grid_2col"  -> 8.dp
        else         -> 12.dp  // comfortable (varsayılan)
    }
}

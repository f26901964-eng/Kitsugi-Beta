package com.kitsugi.animelist.model

import androidx.annotation.StringRes
import com.kitsugi.animelist.R

enum class MediaType {
    Anime,
    Manga,
    Movie,
    TvShow
}

/**
 * İzleme durumları — etiketler strings.xml üzerinden gelir (values / values-en);
 * böylece rozetler ve liste kartları uygulama dilini takip eder.
 * Gösterim için [com.kitsugi.animelist.ui.utils.localizedLabel] kullanılır.
 */
enum class WatchStatus(
    @StringRes val labelRes: Int
) {
    Watching(R.string.watch_watching),
    Completed(R.string.watch_completed),
    Planned(R.string.watch_planned),
    Dropped(R.string.watch_dropped),
    Paused(R.string.watch_paused),
    Repeating(R.string.watch_repeating)
}

data class MediaEntry(
    val id: Int,
    val title: String,
    val subtitle: String = "",
    val type: MediaType,
    val status: WatchStatus,
    val score: Int?,
    val progress: Int,
    val total: Int?,
    val isFavorite: Boolean = false,
    val isAdult: Boolean = false,
    val source: String = "manual",
    val malId: Int? = null,
    val imageUrl: String? = null,
    val year: Int? = null,
    val synopsis: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val priority: Int? = null,
    val isRepeating: Boolean = false,
    val repeatCount: Int = 0,
    val repeatValue: Int = 0,
    val volumeProgress: Int = 0,
    val isPrivate: Boolean = false,
    val isHiddenFromStatusLists: Boolean = false,
    val updatedAt: Long = 0L,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val aniListEntryId: Int? = null,
    val malListId: Long? = null,
    val tmdbId: Int? = null,
    val simklId: Int? = null
)
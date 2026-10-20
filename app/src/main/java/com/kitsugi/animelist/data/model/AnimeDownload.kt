package com.kitsugi.animelist.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class AnimeDownload(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episode: Int,
    val season: Int,
    val url: String,
    val quality: String,
    /** HTTP request headers to use when downloading (e.g. Cookie, Referer, User-Agent from CS3 addons). */
    val requestHeaders: Map<String, String> = emptyMap(),
    val subtitles: List<com.kitsugi.animelist.core.player.SubtitleInput> = emptyList(),
    val status: Status = Status.QUEUE,
    val progress: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val downloadedSegments: Int = 0,
    val malId: Int? = null,
    val aniListId: Int? = null,
    val tmdbId: Int? = null,
    val source: String? = null,
    val streamTitle: String? = null,
    val streamName: String? = null
) {
    enum class Status {
        QUEUE,
        DOWNLOADING,
        COMPLETED,
        ERROR,
        PAUSED
    }
}


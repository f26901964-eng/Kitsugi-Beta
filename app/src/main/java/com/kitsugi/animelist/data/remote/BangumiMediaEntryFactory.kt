package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus

/** Builds a local Bangumi-membership row without borrowing another provider's identity. */
object BangumiMediaEntryFactory {
    /**
     * Convert a Bangumi catalog selection into the stable identity used by imported Bangumi
     * collections. A real MAL ID is deliberately never stored in `malId` for this row.
     */
    fun fromSearchResult(
        result: JikanSearchResult,
        synopsis: String? = null
    ): MediaEntry? {
        if (canonicalMediaSourceId(result.source) != BangumiIdNamespace.SOURCE) return null
        val subjectType = result.bangumiSubjectType ?: return null
        if (!BangumiIdNamespace.supportsSubjectType(subjectType)) return null
        val typeMatchesSubject = when (subjectType) {
            BangumiApiClient.SubjectType.ANIME ->
                result.type == MediaType.Anime || result.type == MediaType.Movie
            BangumiApiClient.SubjectType.BOOK -> result.type == MediaType.Manga
            BangumiApiClient.SubjectType.REAL ->
                result.type == MediaType.Movie || result.type == MediaType.TvShow
            else -> false
        }
        if (!typeMatchesSubject) return null
        val stableId = BangumiIdNamespace.stableIdOrNull(result.malId)
            ?: BangumiIdNamespace.stableIdFromRaw(result.malId)
            ?: return null
        val title = listOfNotNull(result.title, result.titleEnglish, result.titleJapanese)
            .firstOrNull { it.isNotBlank() }
            ?: return null

        return MediaEntry(
            id = 0,
            title = title,
            subtitle = result.subtitle,
            type = result.type,
            status = WatchStatus.Planned,
            score = null,
            progress = 0,
            total = result.total,
            isAdult = result.isAdult,
            source = BangumiIdNamespace.SOURCE,
            malId = stableId,
            imageUrl = result.imageUrl,
            year = result.year,
            synopsis = synopsis,
            titleEnglish = result.titleEnglish,
            titleJapanese = result.titleJapanese,
            tmdbId = result.tmdbId
        )
    }
}

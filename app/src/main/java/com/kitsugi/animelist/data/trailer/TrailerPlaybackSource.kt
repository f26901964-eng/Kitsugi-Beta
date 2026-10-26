package com.kitsugi.animelist.data.trailer

/**
 * A playable representation of a trailer.
 *
 * [requestHeaders] contains source-specific values (most importantly the YouTube client
 * user-agent which issued the signed URLs). [alternatives] keeps genuinely different
 * playback sources so a CDN/HTTP failure does not pointlessly retry the same broken URL
 * in another player engine.
 */
data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val alternatives: List<TrailerPlaybackSource> = emptyList(),
    val externalAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput> = emptyList(),
    val streamTypeHint: String? = null
)

/**
 * Flattens the primary source and its alternatives into independent, cycle-free entries.
 * The returned entries intentionally have empty [TrailerPlaybackSource.alternatives], so
 * they can safely be used as Compose keys and persisted in small in-memory caches.
 */
fun TrailerPlaybackSource.playbackCandidates(): List<TrailerPlaybackSource> {
    val flattened = buildList {
        add(this@playbackCandidates)
        addAll(alternatives)
    }
    return flattened
        .map { it.copy(alternatives = emptyList()) }
        .distinctBy { it.videoUrl to it.audioUrl }
        .filter { it.videoUrl.isNotBlank() }
}

/** Earliest signed expiry across every primary/alternative video and audio URL. */
internal fun TrailerPlaybackSource.earliestYouTubeExpiryEpochSeconds(): Long? =
    playbackCandidates()
        .flatMap { candidate -> listOfNotNull(candidate.videoUrl, candidate.audioUrl) }
        .mapNotNull(YouTubeRequestPolicy::expiryEpochSeconds)
        .minOrNull()

/** Nests an ordered candidate list once, without recursively duplicating alternatives. */
internal fun List<TrailerPlaybackSource>.asPrimaryWithAlternatives(): TrailerPlaybackSource? {
    val candidates = flatMap { it.playbackCandidates() }
    val primary = candidates.firstOrNull() ?: return null
    return primary.copy(alternatives = candidates.drop(1))
}


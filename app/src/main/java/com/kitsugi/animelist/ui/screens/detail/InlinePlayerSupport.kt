package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType

/** A network/format stall must not leave the compact player black at 00:00 forever. */
internal const val INLINE_PLAYER_START_TIMEOUT_MS = 18_000L

internal fun PlayerEngineType.alternateInlineEngine(): PlayerEngineType = when (this) {
    PlayerEngineType.MEDIA3 -> PlayerEngineType.MPV
    PlayerEngineType.MPV, PlayerEngineType.EXTERNAL -> PlayerEngineType.MEDIA3
}

/**
 * YouTube's extracted, signed Googlevideo streams use the Media3-specific request/data-source
 * policy. Keep Media3 as the inline first choice for these URLs (matching fullscreen playback),
 * while preserving the user's engine preference for ordinary direct OP/ED files.
 */
internal fun initialInlineEngineType(
    configuredPreference: String?,
    isYouTubeSource: Boolean
): PlayerEngineType = when {
    isYouTubeSource -> PlayerEngineType.MEDIA3
    configuredPreference.equals("MPV", ignoreCase = true) -> PlayerEngineType.MPV
    else -> PlayerEngineType.MEDIA3
}

/** READY only means prepared; it does not prove that inline playback actually advanced. */
internal fun hasInlinePlaybackProgress(state: PlayerEngine.State, positionMs: Long): Boolean =
    state == PlayerEngine.State.ENDED || positionMs > 0L

/**
 * Sessiz "sesi var, görüntüsü yok" durumunu yakalamak için tanınan süre.
 *
 * Akış başladıktan sonra video parçasının seçilmesi birkaç yüz ms gecikebilir; bu yüzden
 * ilk [INLINE_AUDIO_ONLY_GRACE_MS] boyunca hüküm verilmez.
 */
internal const val INLINE_AUDIO_ONLY_GRACE_MS = 6_000L

/**
 * Oynatım ilerlediği hâlde hiç video parçası seçilmediyse motor değiştirilmelidir.
 *
 * Neden gerekli: hem Media3 hem libmpv, video parçası çözülemediğinde hata vermez.
 * Ses çalar, süre ilerler, ekran siyah kalır — [hasInlinePlaybackProgress] "başladı"
 * der ve yedek motor bekçisi hiç tetiklenmez. Kaynak iki farklı yerde bozulabiliyor:
 * cihazın çözemediği VP9/AV1 `.webm` (AnimeThemes açılış/kapanış dosyaları) ve imzalı
 * YouTube adaptif video akışının 403 dönmesi. İkisi de aynı bekçiyle yakalanır.
 */
internal fun audioOnlyPlaybackDetected(
    elapsedMs: Long,
    state: PlayerEngine.State,
    positionMs: Long,
    hasVideoTrack: Boolean
): Boolean = elapsedMs >= INLINE_AUDIO_ONLY_GRACE_MS &&
    hasVideoTrack.not() &&
    hasInlinePlaybackProgress(state, positionMs)


/**
 * Oynatım başladıktan sonra video parçasının gerçekten seçilip seçilmediğini izler.
 *
 * `true` dönerse çağıran taraf diğer motora geçmelidir: ses çalıyor ama görüntü hiç
 * gelmiyor. Video parçası seçildiği anda (ya da tanınan süre + pay dolduğunda) izleme
 * biter; uygulama arka plana geçerse yanlış yedek geçişi tetiklenmez.
 */
internal suspend fun waitForInlineVideoTrack(
    engine: PlayerEngine,
    isResumed: () -> Boolean,
    graceMs: Long = INLINE_AUDIO_ONLY_GRACE_MS,
    pollMs: Long = 250L
): Boolean {
    val startedAt = android.os.SystemClock.elapsedRealtime()
    while (true) {
        if (!isResumed()) return false
        if (engine.hasVideoTrack) return false
        if (!engine.isPlaying) {
            // Kullanıcı duraklattı: hüküm vermeden bekle.
            kotlinx.coroutines.delay(pollMs)
            continue
        }
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
        if (audioOnlyPlaybackDetected(
                elapsedMs = elapsedMs,
                state = engine.currentState,
                positionMs = engine.currentPosition,
                hasVideoTrack = engine.hasVideoTrack
            )) {
            return true
        }
        // Tanınan süre + küçük bir pay da geçtiyse video sağlıklı: izlemeyi bırak.
        if (elapsedMs > graceMs + 4_000L) return false
        kotlinx.coroutines.delay(pollMs)
    }
}

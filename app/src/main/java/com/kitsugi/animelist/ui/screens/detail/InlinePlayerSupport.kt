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
 * YouTube'un çıkarılmış imzalı Googlevideo akışları Media3'ün istek/veri kaynağı politikasını
 * gerektirir. Bu adreslerde küçük oynatıcı Media3 ile başlar (tam ekranla aynı yol).
 *
 * NOT: Doğrudan OP/ED dosyaları bu fonksiyonu artık kullanmıyor; onlar
 * [initialCompactThemeEngineType] üzerinden Media3 ile başlar (cihazda VP9 çözücüsü yoksa
 * doğrudan MPV). Bu fonksiyon yalnızca YouTube inline akışı ve mevcut çağrı yerleriyle
 * geriye dönük uyumluluk için genel tercih eşlemesini korur.
 */
internal fun initialInlineEngineType(
    configuredPreference: String?,
    isYouTubeSource: Boolean
): PlayerEngineType = when {
    isYouTubeSource -> PlayerEngineType.MEDIA3
    configuredPreference.equals("MPV", ignoreCase = true) -> PlayerEngineType.MPV
    else -> PlayerEngineType.MEDIA3
}

/**
 * Kompakt (detay sayfası içindeki) tema oynatıcısı her zaman Media3 ile başlar.
 *
 * NEDEN (2026-10-21 saha raporu): aynı sayfada fragman (Media3 + TextureView) sorunsuz
 * oynarken OP/ED videoları sesli ama siyah kalıyordu. Fark motordu: varsayılan ayar
 * "MPV" olduğu için OP/ED satırları MPV'nin SurfaceView'ını kullanıyordu. SurfaceView,
 * Compose'un kırpılmış/ölçüsü animasyonla değişen kabında (kart `clip()` +
 * `animateDetailContentSize()`) görünür bir yüzeye bağlanamıyor; mpv sesi çalıyor,
 * kare üretmiyor. Media3'ün PlayerView'ı TextureView kullandığı için aynı kabın içinde
 * doğru çiziliyor ve tam ekran oynatıcıyla aynı motoru kullanıyor.
 *
 * Kullanıcının genel motor tercihi tam ekranda korunur; kompakt oynatıcıda Media3
 * başarısız olursa MPV yalnızca bir kez yedek olarak denenir.
 */
internal fun initialCompactThemeEngineType(url: String? = null): PlayerEngineType {
    // AnimeThemes açılış/kapanış dosyaları çoğunlukla VP9 `.webm`. Cihazda VP9 çözücüsü
    // yoksa Media3 sesi çalar ama hiç kare üretmez; bekçi 6. saniyede MPV'ye geçene kadar
    // kullanıcı siyah ekran görür. Çözücü yokluğu ÖNCEDEN bilinebildiği için doğrudan
    // MPV (yazılım çözücü) ile başlanır — altı saniyelik siyah ekran tamamen atlanır.
    val looksWebm = url?.contains(".webm", ignoreCase = true) == true
    if (looksWebm && !deviceHasVideoDecoder(VP9_MIME)) return PlayerEngineType.MPV
    return PlayerEngineType.MEDIA3
}

private const val VP9_MIME = "video/x-vnd.on2.vp9"

/**
 * Cihazda verilen MIME türü için bir çözücü kayıtlı mı?
 *
 * Bilinmeyen/okunamayan durumda `true` döner: yanlışlıkla MPV'ye düşmek yerine Media3
 * ile başlanır (Media3 render edemezse bekçi zaten motoru değiştirir).
 */
private fun deviceHasVideoDecoder(mime: String): Boolean = runCatching {
    android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }
}.getOrDefault(true)

/** READY only means prepared; it does not prove that inline playback actually advanced. */
internal fun hasInlinePlaybackProgress(state: PlayerEngine.State, positionMs: Long): Boolean =
    state == PlayerEngine.State.ENDED || positionMs > 0L

/**
 * Kare gerçekten ekranda mı? Seçili video parçası TEK BAŞINA yetmez:
 * Media3'te çözücü kare üretmese de `currentTracks` parçayı seçili gösterir; MPV'de
 * `vo-configured` yüzeyin görünür olduğunu garanti etmez. Bu yüzden hem parça hem
 * "kare çizildi" bilgisi birlikte okunur.
 */
internal fun inlineVideoVisible(engine: PlayerEngine): Boolean =
    engine.hasVideoTrack && engine.hasRenderedVideoFrame

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
    hasVisibleVideo: Boolean
): Boolean = elapsedMs >= INLINE_AUDIO_ONLY_GRACE_MS &&
    hasVisibleVideo.not() &&
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
        if (inlineVideoVisible(engine)) return false
        if (!engine.isPlaying) {
            // Kullanıcı duraklattı: hüküm vermeden bekle. Bekçi yine de sonsuza kadar
            // yaşamamalı (ekran kapanıp cihaz uykuya geçebilir); 30 sn sonra sessizce çık.
            val pausedFor = android.os.SystemClock.elapsedRealtime() - startedAt
            if (pausedFor > graceMs + 24_000L) return false
            kotlinx.coroutines.delay(pollMs)
            continue
        }
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
        if (audioOnlyPlaybackDetected(
                elapsedMs = elapsedMs,
                state = engine.currentState,
                positionMs = engine.currentPosition,
                hasVisibleVideo = inlineVideoVisible(engine)
            )) {
            return true
        }
        // Tanınan süre + küçük bir pay da geçtiyse video sağlıklı: izlemeyi bırak.
        if (elapsedMs > graceMs + 4_000L) return false
        kotlinx.coroutines.delay(pollMs)
    }
}

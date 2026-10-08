package com.kitsugi.animelist.core.player.engine

import android.content.Context
import android.util.Log
import android.view.View
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption
import com.kitsugi.animelist.ui.screens.fullscreen.components.StreamInfoData
import com.kitsugi.animelist.data.settings.AppSettings
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.kitsugi.animelist.data.local.KitsugiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Aniyomi PlayerActivity yaklaşımından ilham alınarak yeniden yazılmış MPV motor.
 *
 * Özellikler:
 * - Güçlü property observation (Aniyomi observeProperties modeli)
 * - İkincil altyazı (secondary-sid) desteği
 * - GPU renderer, debanding, demuxer cache, hwdec, YUV420P init seçenekleri
 * - eof-reached, seeking, hwdec-current gözlemi
 * - Volume boost cap (volume-max ayarı)
 */
class MpvPlayerEngine(
    private val context: Context,
    private val settings: AppSettings
) : PlayerEngine, MPV.EventObserver {

    private val TAG = "MpvPlayerEngine"
    private val listeners = mutableListOf<PlayerEngine.Listener>()
    internal var mpvView: KitsugiMpvSurfaceView? = null

    override val engineType: PlayerEngineType = PlayerEngineType.MPV
    override var currentState: PlayerEngine.State = PlayerEngine.State.IDLE
        private set

    override var currentPosition: Long = 0L
        private set

    override var duration: Long = 0L
        private set

    override var isPlaying: Boolean = false
        private set

    private var _currentSpeed: Float = settings.playerSpeed
    override val currentSpeed: Float
        get() = _currentSpeed

    private var _currentVolume: Float = 1.0f
    override val currentVolume: Float
        get() = _currentVolume

    override var subtitleDelayMs: Long = 0L
        private set

    override var audioDelayMs: Long = 0L
        private set

    private var _isSubtitleDisabled: Boolean = false
    override val isSubtitleDisabled: Boolean
        get() = _isSubtitleDisabled

    /** Aktif ikincil altyazı parça ID'si; -1 = kapalı */
    var secondarySubtitleTrackId: Int = settings.secondarySubtitleTrackId
        private set

    /** İkincil altyazı gecikme değeri (saniye cinsinden MPV'ye iletilir) */
    var secondarySubtitleDelayMs: Long = settings.secondarySubtitleDelayMs
        private set

    /** MPV donanım çözücü türü - hwdec-current gözleminden okunur */
    var activeHwdecMode: String = "none"
        private set

    /** true = buffer dolmayı bekliyor */
    private var pausedForCache: Boolean = false

    /** true = video verisi okunmadan önce idle konumunda */
    private var coreIdle: Boolean = false

    /** true = kullanıcı seek işlemi yapıyor */
    private var isSeeking: Boolean = false

    private var currentAddonName: String? = null
    private var streamTitle: String? = null
    private var videoUrl: String? = null
    private var pendingHeaders: Map<String, String> = emptyMap()
    private var pendingSubtitles: List<SubtitleInput> = emptyList()
    private var pendingStartPositionMs: Long = 0L
    private var pendingAudioUrl: String? = null

    /** Hazırlık sırasında motorun aldığı SubtitleInput listesi (dahili/harici bayrağını taşır). */
    private var preparedSubtitles: List<SubtitleInput> = emptyList()
    /**
     * İlk otomatik parça seçimi tamamlandı mı?
     * true  = tamamlandı (kullanıcı manuel değişiklik yapabilir, tekrar tetiklenmez)
     * false = henüz yapılmadı (bir sonraki updateTracks'de çalışacak)
     */
    private var initialSelectionDone: Boolean = false

    /** Mevcut medya dosyası açıldı mı (MPV_EVENT_FILE_LOADED alındı mı)? */
    private var fileLoaded: Boolean = false
    /** Bu medya yükleme döngüsü için oynatıcı hatası zaten bildirildi mi? */
    private var errorReportedForLoad: Boolean = false
    /**
     * loadfile(replace) ile değiştirilen ÖNCEKİ dosyanın END_FILE olaylarını atlamak için sayaç.
     * Bunlar yeni medyanın hatası değildir; atlanmazsa sahte hata → sahte motor değişimi olur.
     */
    private var staleEndFileEvents: Int = 0
    /** Son yüklenen medyanın anahtarı (url + headers + başlangıç konumu); setMedia ile aynı koşul. */
    private var lastLoadKey: String? = null
    /** Ayrı ses akışı (audioUrl) eklendi mi? true ise MPV'nin ses otomatik seçimi ezilmez. */
    private var hasExternalAudio: Boolean = false
    /** Harici altyazılar beklenmeden de ilk seçim yapılsın diye zorlanmış değerlendirme. */
    private var selectionForced: Boolean = false

    // ──── Observed properties (Aniyomi modeli) ───────────────────────────────
    // Bu liste, MPV başlatılırken tek seferlik kayıt yapılır.
    private val observedProps = mapOf(
        "time-pos"          to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "duration"          to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "pause"             to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache"  to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "core-idle"         to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "seeking"           to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached"       to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "track-list"        to MPV.mpvFormat.MPV_FORMAT_NONE,
        "chapter"           to MPV.mpvFormat.MPV_FORMAT_INT64,
        "chapter-list"      to MPV.mpvFormat.MPV_FORMAT_NONE,
        "speed"             to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "volume"            to MPV.mpvFormat.MPV_FORMAT_DOUBLE,
        "sub-visibility"    to MPV.mpvFormat.MPV_FORMAT_FLAG,
        "sid"               to MPV.mpvFormat.MPV_FORMAT_INT64,
        "secondary-sid"     to MPV.mpvFormat.MPV_FORMAT_INT64,
        "aid"               to MPV.mpvFormat.MPV_FORMAT_INT64,
        "hwdec-current"     to MPV.mpvFormat.MPV_FORMAT_STRING,
        "video-params/w"    to MPV.mpvFormat.MPV_FORMAT_INT64,
        "video-params/h"    to MPV.mpvFormat.MPV_FORMAT_INT64,
        "user-data/aniyomi" to MPV.mpvFormat.MPV_FORMAT_STRING,
        "user-data/current-anime/intro-length" to MPV.mpvFormat.MPV_FORMAT_INT64
    )

    override val activeStreamInfo: StreamInfoData
        get() {
            val vCodec = runCatching { mpvView?.mpv?.getPropertyString("video-codec") }.getOrNull()
            val aCodec = runCatching { mpvView?.mpv?.getPropertyString("audio-codec") }.getOrNull()
            val vw = runCatching { mpvView?.mpv?.getPropertyInt("video-params/w") }.getOrNull()
            val vh = runCatching { mpvView?.mpv?.getPropertyInt("video-params/h") }.getOrNull()
            val fps = runCatching { mpvView?.mpv?.getPropertyDouble("estimated-vf-fps") }.getOrNull()
            val bitrate = runCatching { mpvView?.mpv?.getPropertyInt("file-size") }.getOrNull()
            return StreamInfoData(
                addonName = currentAddonName ?: "Dahili",
                streamName = "MPV",
                streamDescription = streamTitle,
                filename = videoUrl?.substringAfterLast('/'),
                playerEngine = "MPV ($activeHwdecMode)",
                videoWidth = vw ?: mpvView?.width,
                videoHeight = vh ?: mpvView?.height,
                videoCodec = vCodec,
                audioCodec = aCodec,
                videoFrameRate = fps?.toFloat(),
                videoBitrate = bitrate
            )
        }

    override fun addListener(listener: PlayerEngine.Listener) { listeners.add(listener) }
    override fun removeListener(listener: PlayerEngine.Listener) { listeners.remove(listener) }

    override suspend fun prepare(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        subtitles: List<SubtitleInput>,
        startPositionMs: Long,
        addonName: String?,
        isCS: Boolean,
        streamTitle: String?,
        qualityValue: Int?
    ) {
        this.videoUrl = videoUrl
        this.currentAddonName = addonName
        this.streamTitle = streamTitle
        // Yeni ortam için otomatik parça seçimini sıfırla
        this.preparedSubtitles = subtitles
        this.initialSelectionDone = false
        this._isSubtitleDisabled = false

        updateState(PlayerEngine.State.BUFFERING)

        this.pendingHeaders = headers
        this.pendingSubtitles = subtitles
        this.pendingStartPositionMs = startPositionMs

        this.pendingAudioUrl = audioUrl
        val view = mpvView
        if (view != null) {
            applyInitOptions(view)
            beginLoad(videoUrl, audioUrl, headers, startPositionMs)
            view.setMedia(videoUrl, headers, startPositionMs)
            applyExternalAudio(view, audioUrl)
            view.applySubtitleLanguagePreferences(settings.preferredSubtitleLanguages, null)
            subtitles.forEach { sub ->
                view.addAndSelectExternalSubtitle(sub.url, sub.name, sub.lang)
            }
            applySecondarySubtitle(view)
            setupCustomButtons()
            isPlaying = true
        }
    }

    override fun play() {
        mpvView?.setPaused(false)
        isPlaying = true
        updateState(PlayerEngine.State.READY)
    }

    override fun pause() {
        mpvView?.setPaused(true)
        isPlaying = false
        updateState(PlayerEngine.State.READY)
    }

    override fun seekTo(positionMs: Long) {
        if (settings.preciseSeeking) {
            // Hassas mod — tam kare araması (daha yavaş ama doğru)
            runCatching {
                mpvView?.mpv?.command(
                    "seek",
                    (positionMs / 1000.0).toString(),
                    "absolute",
                    "exact"
                )
            }
        } else {
            mpvView?.seekToMs(positionMs)
        }
        currentPosition = positionMs
        notifyPositionChanged()
    }

    override fun setPlaybackSpeed(speed: Float) {
        _currentSpeed = speed
        mpvView?.setPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        _currentVolume = volume
        if (volume > 1.0f) {
            // Boost modu — MPV volume-max sınırına göre ölçekle
            val cap = settings.volumeBoostCap.coerceIn(100, 200)
            val boostedVol = (volume * 100.0).coerceIn(100.0, cap.toDouble())
            runCatching {
                mpvView?.mpv?.setPropertyInt("volume-max", cap)
                mpvView?.mpv?.setPropertyDouble("volume", boostedVol)
            }
        } else {
            runCatching {
                mpvView?.mpv?.setPropertyDouble("volume", (volume * 100.0).coerceIn(0.0, 100.0))
            }
            mpvView?.applyAudioAmplificationDb(0)
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        subtitleDelayMs = delayMs
        runCatching {
            mpvView?.mpv?.setPropertyDouble("sub-delay", delayMs / 1000.0)
        }
    }

    /** İkincil altyazı gecikmesini ayarla */
    fun setSecondarySubtitleDelay(delayMs: Long) {
        secondarySubtitleDelayMs = delayMs
        runCatching {
            mpvView?.mpv?.setPropertyDouble("secondary-sub-delay", delayMs / 1000.0)
        }
    }

    /** İkincil altyazı parça ID'sini seç; -1 = kapat */
    fun selectSecondarySubtitleTrack(trackId: Int) {
        secondarySubtitleTrackId = trackId
        runCatching {
            if (trackId == -1) {
                mpvView?.mpv?.setPropertyString("secondary-sid", "no")
            } else {
                mpvView?.mpv?.setPropertyInt("secondary-sid", trackId)
            }
        }
    }

    override fun setAudioDelay(delayMs: Long) {
        audioDelayMs = delayMs
        runCatching {
            mpvView?.mpv?.setPropertyDouble("audio-delay", delayMs / 1000.0)
        }
    }

    override fun setSubtitleStyle(style: SubtitleStyleSettings) {
        mpvView?.applySubtitleStyle(style)
        // Gelişmiş stil özellikleri (Aniyomi'den uyarlama)
        runCatching {
            val mpv = mpvView?.mpv ?: return@runCatching
            // İtalik
            if (style.bold || settings.subtitleItalic) {
                mpv.setPropertyString("sub-ass-override", "force")
            }
            // Hizalama
            val alignCode = when (settings.subtitleJustification) {
                "left"  -> "7"
                "right" -> "9"
                else    -> "8" // center (üst orta)
            }
            mpv.setPropertyString("sub-align-x", when (settings.subtitleJustification) {
                "left"  -> "left"
                "right" -> "right"
                else    -> "center"
            })
            // Gölge
            if (settings.subtitleShadowOffset > 0f) {
                mpv.setPropertyDouble("sub-shadow-offset", settings.subtitleShadowOffset.toDouble())
            }
            // Kenarlık kalınlığı
            mpv.setPropertyDouble("sub-border-size", settings.subtitleBorderSize.toDouble())
        }
    }

    override fun setResizeMode(resizeMode: Int) {
        val aspectMode = when (resizeMode) {
            0 -> AspectMode.ORIGINAL
            4 -> AspectMode.FULL_SCREEN
            3 -> AspectMode.STRETCH
            else -> AspectMode.ORIGINAL
        }
        mpvView?.applyAspectMode(aspectMode)
    }

    override fun setAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode) {
        val aspectProp = com.kitsugi.animelist.core.player.PlayerAspectScaleUtils.getMpvAspectProperty(mode)
        runCatching {
            mpvView?.mpv?.setPropertyString("video-aspect-override", aspectProp)
        }
        val mpvAspectMode = when (mode) {
            com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL -> AspectMode.ORIGINAL
            com.kitsugi.animelist.core.player.PlayerAspectMode.FIT -> AspectMode.ORIGINAL
            com.kitsugi.animelist.core.player.PlayerAspectMode.FILL -> AspectMode.STRETCH
            com.kitsugi.animelist.core.player.PlayerAspectMode.ZOOM -> AspectMode.FULL_SCREEN
            com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_16_9 -> AspectMode.FULL_SCREEN
            com.kitsugi.animelist.core.player.PlayerAspectMode.CROP_4_3 -> AspectMode.FULL_SCREEN
        }
        mpvView?.applyAspectMode(mpvAspectMode)
    }

    override fun selectTrack(trackOption: TrackOption) {
        if (trackOption.groupIndex == 0) {
            mpvView?.selectAudioTrackById(trackOption.trackIndex)
        } else if (trackOption.groupIndex == 1) {
            mpvView?.selectSubtitleTrackById(trackOption.trackIndex)
            _isSubtitleDisabled = false
        }
        updateTracks()
    }

    override fun disableSubtitles() {
        mpvView?.disableSubtitles()
        _isSubtitleDisabled = true
        updateTracks()
    }

    override fun createVideoView(context: Context): View {
        if (mpvView == null) {
            mpvView = KitsugiMpvSurfaceView(context).apply {
                ensureInitialized()

                // Tüm gözlenen özellikleri tek seferinde kaydet (Aniyomi modeli)
                mpv.addObserver(this@MpvPlayerEngine)
                observedProps.forEach { (prop, format) ->
                    mpv.observeProperty(prop, format)
                }

                // Gelişmiş init seçenekleri uygula
                applyInitOptions(this)

                val pendingUrl = videoUrl
                if (!pendingUrl.isNullOrBlank()) {
                    beginLoad(pendingUrl, pendingAudioUrl, pendingHeaders, pendingStartPositionMs)
                    setMedia(pendingUrl, pendingHeaders, pendingStartPositionMs)
                    applyExternalAudio(this, pendingAudioUrl)
                    applySubtitleLanguagePreferences(settings.preferredSubtitleLanguages, null)
                    pendingSubtitles.forEach { sub ->
                        addAndSelectExternalSubtitle(sub.url, sub.name, sub.lang)
                    }
                    applySecondarySubtitle(this)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        setupCustomButtons()
                    }
                    isPlaying = true
                }
            }
        }
        return mpvView!!
    }

    override fun release() {
        runCatching { mpvView?.mpv?.removeObserver(this) }
        runCatching { mpvView?.releasePlayer() }
        mpvView = null
        listeners.clear()
    }

    // ──── MPV.EventObserver callbacks ────────────────────────────────────────

    override fun eventProperty(property: String) {
        when (property) {
            "track-list", "chapter-list" -> updateTracks()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> {
                isPlaying = !value
                if (!isSeeking) {
                    updateState(PlayerEngine.State.READY)
                }
            }
            "paused-for-cache" -> {
                pausedForCache = value
                checkBufferingState()
            }
            "core-idle" -> {
                coreIdle = value
                checkBufferingState()
            }
            "seeking" -> {
                isSeeking = value
                if (value) {
                    updateState(PlayerEngine.State.BUFFERING)
                } else {
                    checkBufferingState()
                }
            }
            "eof-reached" -> {
                if (value) {
                    Log.d(TAG, "eof-reached=true → State.ENDED")
                    updateState(PlayerEngine.State.ENDED)
                }
            }
            "sub-visibility" -> {
                _isSubtitleDisabled = !value
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> {
                val posMs = (value * 1000).toLong().coerceAtLeast(0L)
                if (posMs != currentPosition) {
                    currentPosition = posMs
                    notifyPositionChanged()
                }
            }
            "duration" -> {
                val durMs = (value * 1000).toLong().coerceAtLeast(0L)
                if (durMs != duration) {
                    duration = durMs
                    notifyPositionChanged()
                }
            }
            "speed" -> {
                _currentSpeed = value.toFloat()
            }
            "volume" -> {
                val mpvVol = (value / 100.0).toFloat()
                if (_currentVolume <= 1.0f || mpvVol < 1.0f) {
                    _currentVolume = mpvVol
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "sid" -> {
                // Primary subtitle track ID güncellendi — track listesini yenile
                updateTracks()
            }
            "secondary-sid" -> {
                secondarySubtitleTrackId = value.toInt()
            }
            "aid" -> {
                // Audio track ID güncellendi
                updateTracks()
            }
            "video-params/w", "video-params/h" -> {
                // Video boyutları değişti — streamInfo güncellenecek
            }
            "chapter" -> {
                // Bölüm değişimi bildirimi
            }
            "user-data/current-anime/intro-length" -> {
                listeners.forEach { it.onEngineEvent(property, value.toString()) }
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "hwdec-current" -> {
                activeHwdecMode = value
                Log.d(TAG, "hwdec-current=$value")
            }
            "user-data/aniyomi" -> {
                listeners.forEach { it.onEngineEvent(property, value) }
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, data: MPVNode) {
        when (eventId) {
            MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                Log.d(TAG, "MPV_EVENT_FILE_LOADED")
                fileLoaded = true
                updateState(PlayerEngine.State.READY)
                updateTracks()
                // Harici altyazılar bazen geç eklenir; kısa bir gecikmeden sonra seçimi zorla.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!initialSelectionDone) {
                        selectionForced = true
                        updateTracks()
                    }
                }, 1500L)
            }
            MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                Log.d(TAG, "MPV_EVENT_END_FILE")
                handleEndFile()
            }
        }
    }

    // ──── Internal helpers ────────────────────────────────────────────────────

    /**
     * Aniyomi'den uyarlama: Tüm MPV başlatma seçeneklerini ayarla.
     * GPU renderer, hwdec, debanding, demuxer cache, volume-max gibi gelişmiş ayarlar.
     */
    private fun applyInitOptions(view: KitsugiMpvSurfaceView) {
        runCatching {
            val mpv = view.mpv

            // GPU renderer backend
            mpv.setPropertyString("gpu-api", "opengl")
            mpv.setPropertyString("vo", settings.mpvGpuRenderer.ifBlank { "gpu" })
            mpv.setPropertyDouble("speed", _currentSpeed.toDouble())

            // Donanım kod çözme
            mpv.setPropertyString("hwdec", settings.mpvHwdecMode.ifBlank { "auto-safe" })

            // Debanding
            when (settings.mpvDebandMode) {
                "cpu" -> {
                    mpv.setPropertyString("deband", "yes")
                    mpv.setPropertyString("deband-iterations", "1")
                }
                "gpu" -> {
                    mpv.setPropertyString("deband", "yes")
                }
                else -> {
                    mpv.setPropertyString("deband", "no")
                }
            }

            // YUV420P format zorlama (eski donanım uyumu)
            if (settings.mpvForceYuv420p) {
                mpv.setPropertyString("vf", "format=yuv420p")
            }

            // Demuxer önbellek limiti
            if (settings.mpvDemuxerCacheMb > 0) {
                mpv.setPropertyString(
                    "demuxer-max-bytes",
                    "${settings.mpvDemuxerCacheMb}MiB"
                )
                mpv.setPropertyString(
                    "demuxer-max-back-bytes",
                    "${(settings.mpvDemuxerCacheMb / 4).coerceAtLeast(8)}MiB"
                )
            }

            // Volume-max sınırı (ses güçlendirme için)
            mpv.setPropertyInt("volume-max", settings.volumeBoostCap.coerceIn(100, 200))

            // Ağ önbelleği — akış sürtünmesini azaltır
            mpv.setPropertyString("cache", "yes")
            mpv.setPropertyString("network-timeout", "30")
        }.onFailure {
            Log.w(TAG, "applyInitOptions hata: ${it.message}")
        }
    }

    /** İkincil altyazı seçimini MPV'ye uygula */
    private fun applySecondarySubtitle(view: KitsugiMpvSurfaceView) {
        val trackId = settings.secondarySubtitleTrackId
        if (trackId != -1) {
            runCatching {
                view.mpv.setPropertyInt("secondary-sid", trackId)
            }
        }
        val delayMs = settings.secondarySubtitleDelayMs
        if (delayMs != 0L) {
            runCatching {
                view.mpv.setPropertyDouble("secondary-sub-delay", delayMs / 1000.0)
            }
        }
    }

    /** Yeni bir medya yüklenmeden önce yükleme durumunu hazırlar. */
    private fun beginLoad(url: String, audioUrl: String?, headers: Map<String, String>, startPositionMs: Long) {
        // setMedia yalnızca bu anahtar değiştiğinde loadfile çağırır; aynı mantığı burada kullanıyoruz.
        val key = "$url|${headers.hashCode()}|${startPositionMs.coerceAtLeast(0L)}"
        val isNewLoad = key != lastLoadKey
        if (isNewLoad) {
            // Önceki dosya hâlâ açıksa, replace nedeniyle gelecek END_FILE'ı atla.
            if (fileLoaded) staleEndFileEvents++
            fileLoaded = false
            errorReportedForLoad = false
            selectionForced = false
            initialSelectionDone = false
        }
        lastLoadKey = key
        hasExternalAudio = !audioUrl.isNullOrBlank()
    }

    /** Ayrı ses akışını (audioUrl) MPV'ye ses parçası olarak ekler ve seçer. */
    private fun applyExternalAudio(view: KitsugiMpvSurfaceView, audioUrl: String?) {
        if (audioUrl.isNullOrBlank()) return
        runCatching {
            view.mpv.command("audio-add", audioUrl, "select")
        }.onFailure {
            Log.w(TAG, "audio-add başarısız: ${it.message}")
        }
    }

    /**
     * MPV_EVENT_END_FILE işleyici. Dosya açılamazsa veya oynatma EOF'a ulaşmadan
     * beklenmeyen şekilde kesilirse hata bildirir; PlayerErrorRecoveryController
     * böylece yedek dahili oynatıcıya (MEDIA3) geçebilir.
     */
    private fun handleEndFile() {
        if (staleEndFileEvents > 0) {
            staleEndFileEvents--
            Log.d(TAG, "Önceki dosyanın END_FILE olayı atlandı (kalan=$staleEndFileEvents)")
            return
        }
        val wasLoaded = fileLoaded
        fileLoaded = false
        val reachedEof = runCatching { mpvView?.mpv?.getPropertyBoolean("eof-reached") == true }
            .getOrDefault(false)
        if (reachedEof || errorReportedForLoad) return

        errorReportedForLoad = true
        val code = if (wasLoaded) 9002 else 9001
        val msg = if (wasLoaded) {
            "MPV oynatma sırasında beklenmeyen şekilde durdu"
        } else {
            "MPV dosyayı açamadı (kaynak erişilemiyor veya format desteklenmiyor)"
        }
        Log.e(TAG, "$msg (code=$code)")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            listeners.forEach { it.onPlaybackError(code, msg, null) }
        }
    }

    private fun updateState(newState: PlayerEngine.State) {
        if (currentState != newState) {
            currentState = newState
            listeners.forEach { it.onStateChanged(newState) }
        }
    }

    private fun checkBufferingState() {
        val view = mpvView ?: return
        val isBuffering = pausedForCache || (coreIdle && isPlaying)
        if (isBuffering) {
            updateState(PlayerEngine.State.BUFFERING)
        } else if (currentState == PlayerEngine.State.BUFFERING && !isSeeking) {
            updateState(PlayerEngine.State.READY)
        }
    }

    private fun notifyPositionChanged() {
        listeners.forEach { it.onPositionChanged(currentPosition, duration) }
    }

    private fun updateTracks() {
        val view = mpvView ?: return
        val snapshot = view.readTrackSnapshot()
        val dummyGroup = createDummyGroup()

        val audioOptions = snapshot.audioTracks.map { track ->
            TrackOption(
                group = dummyGroup,
                groupIndex = 0,
                trackIndex = track.id,
                label = track.name,
                isSelected = track.isSelected
            )
        }

        val subtitleOptions = snapshot.subtitleTracks.map { track ->
            TrackOption(
                group = dummyGroup,
                groupIndex = 1,
                trackIndex = track.id,
                label = track.name,
                isSelected = track.isSelected
            )
        }

        // Harici altyazılar MPV track-list'inde kendi "external" bayrağıyla gelir: isim tahminine gerek yok.
        val expectedExternalSubs = preparedSubtitles.count { it.isExternal }
        val loadedExternalSubs = snapshot.subtitleTracks.count { it.isExternal }
        val externalSubsReady = loadedExternalSubs >= expectedExternalSubs || selectionForced

        // ── İlk otomatik parça seçimi — yalnızca yeni medya yüklendiğinde ve harici altyazılar hazırken ──
        if (!initialSelectionDone && fileLoaded && externalSubsReady &&
            (snapshot.audioTracks.isNotEmpty() || snapshot.subtitleTracks.isNotEmpty())
        ) {
            initialSelectionDone = true
            val preferredLangs = settings.preferredSubtitleLanguages
                .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

            // ── Ses: Türkçe önce, sonra tercih listesi (ayrı ses akışı varsa dokunulmaz) ──
            val currentlySelectedAudio = snapshot.audioTracks.firstOrNull { it.isSelected }
            val isSelectedAudioTurkish = currentlySelectedAudio != null &&
                com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(currentlySelectedAudio.language, currentlySelectedAudio.name)
            if (!isSelectedAudioTurkish && !hasExternalAudio) {
                val bestAudio = com.kitsugi.animelist.core.player.PlayerSubtitleUtils
                    .findBestMpvAudioTrack(snapshot.audioTracks, preferredLangs)
                if (bestAudio != null && currentlySelectedAudio?.id != bestAudio.id) {
                    Log.i(TAG, "Auto-selecting audio track: ${bestAudio.name} (id=${bestAudio.id})")
                    view.selectAudioTrackById(bestAudio.id)
                }
            }

            // ── Altyazı: Tek ortak politika (SubtitleSelectionPolicy) — Media3 ile birebir aynı ──
            // 1. Videonun içindeki Türkçe  2. Harici Türkçe  3. Diğer tercih dilleri  4. Yoksa kapat
            if (!_isSubtitleDisabled && snapshot.subtitleTracks.isNotEmpty()) {
                val currentlySelectedSub = snapshot.subtitleTracks.firstOrNull { it.isSelected }
                val isSelectedTurkishSource = currentlySelectedSub != null &&
                    !currentlySelectedSub.isExternal &&
                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(currentlySelectedSub.language, currentlySelectedSub.name)
                if (!isSelectedTurkishSource) {
                    val candidates = snapshot.subtitleTracks.map { track ->
                        com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.Candidate(
                            item = track,
                            lang = track.language,
                            label = track.name,
                            isExternal = track.isExternal,
                        )
                    }
                    val bestSub = com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.pick(candidates, preferredLangs)
                    if (bestSub != null) {
                        if (currentlySelectedSub?.id != bestSub.id) {
                            Log.i(TAG, "Auto-selecting subtitle track: ${bestSub.name} (id=${bestSub.id})")
                            view.selectSubtitleTrackById(bestSub.id)
                        }
                    } else {
                        Log.i(TAG, "No Turkish/preferred subtitle found. Disabling non-preferred track: ${currentlySelectedSub?.name}")
                        view.disableSubtitles()
                    }
                }
            }
        }

        listeners.forEach { it.onTracksChanged(audioOptions, subtitleOptions) }
    }

    private fun createDummyGroup(): androidx.media3.common.Tracks.Group {
        val format = androidx.media3.common.Format.Builder().build()
        val trackGroup = androidx.media3.common.TrackGroup(format)
        return androidx.media3.common.Tracks.Group(
            trackGroup,
            false,
            intArrayOf(androidx.media3.common.C.FORMAT_HANDLED),
            booleanArrayOf(false)
        )
    }

    override fun takeScreenshot(cachePath: String, showSubtitles: Boolean): java.io.InputStream? {
        val view = mpvView ?: return null
        val filename = cachePath + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        runCatching {
            view.mpv.command("screenshot-to-file", filename, subtitleFlag)
        }.onFailure {
            Log.e(TAG, "screenshot-to-file failed: ${it.message}")
            return null
        }

        val tempFile = java.io.File(filename).takeIf { it.exists() } ?: return null
        val newFile = java.io.File("$cachePath/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    private suspend fun setupCustomButtons() {
        withContext(Dispatchers.IO) {
            runCatching {
                val db = KitsugiDatabase.getDatabase(context)
                val buttons = db.customButtonDao().getAll()
                if (buttons.isEmpty()) return@runCatching
                val primaryButtonId = buttons.firstOrNull { it.isFavorite }?.id ?: 0L
                val scriptsDir = java.io.File(context.filesDir, "scripts")
                if (!scriptsDir.exists()) {
                    scriptsDir.mkdirs()
                }
                val customButtonsContent = buildString {
                    append(
                        """
                            local lua_modules = mp.find_config_file('scripts')
                            if lua_modules then
                                package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua;' .. '${scriptsDir.absolutePath.replace("\\", "/")}' .. '/?.lua'
                            end
                        """.trimIndent()
                    )
                    append("\n")
                    buttons.forEach { button ->
                        append(
                            """
                                -- ${button.name}
                                ${button.getButtonOnStartup(primaryButtonId)}
                                function button${button.id}()
                                    ${button.getButtonContent(primaryButtonId)}
                                end
                                mp.register_script_message('call_button_${button.id}', button${button.id})
                                function button${button.id}long()
                                    ${button.getButtonLongPressContent(primaryButtonId)}
                                end
                                mp.register_script_message('call_button_${button.id}_long', button${button.id}long)
                            """.trimIndent()
                        )
                        append("\n")
                    }
                }
                val file = java.io.File(scriptsDir, "custombuttons.lua")
                file.writeText(customButtonsContent)
                withContext(Dispatchers.Main) {
                    mpvView?.mpv?.command("load-script", file.absolutePath)
                }
            }.onFailure {
                Log.w(TAG, "setupCustomButtons error: ${it.message}")
            }
        }
    }

    override fun executeCommand(command: Array<String>) {
        runCatching {
            mpvView?.mpv?.command(*command)
        }
    }
}


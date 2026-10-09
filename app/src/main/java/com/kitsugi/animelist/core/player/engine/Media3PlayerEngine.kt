package com.kitsugi.animelist.core.player.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.player.BitrateAwareLoadControl
import com.kitsugi.animelist.core.player.ParallelRangeDataSource
import com.kitsugi.animelist.core.player.DolbyVisionBaseLayerPolicy
import com.kitsugi.animelist.core.player.DolbyVisionCodecFallback
import com.kitsugi.animelist.core.player.DolbyVisionConversionConfig
import com.kitsugi.animelist.core.player.DolbyVisionConversionStats
import com.kitsugi.animelist.core.player.DolbyVisionExtractorsFactory
import com.kitsugi.animelist.core.player.DoviBridge
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.core.player.dvmkv.DolbyVisionCompatibility
import com.kitsugi.animelist.core.player.PlayerMediaSourceFactory
import com.kitsugi.animelist.core.player.PlayerLoadingDiagnostics
import com.kitsugi.animelist.core.player.PlayerPlaybackAnalytics
import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.Dv7HandlingMode
import com.kitsugi.animelist.data.trailer.YoutubeChunkedDataSourceFactory
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings
import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption
import com.kitsugi.animelist.ui.screens.fullscreen.components.StreamInfoData
import java.util.Locale

import com.kitsugi.animelist.core.player.buildWithAssSupportCompat
import com.kitsugi.animelist.core.player.getAssHandlerCompat

@OptIn(androidx.media3.common.util.UnstableApi::class)
class Media3PlayerEngine(
    private val context: Context,
    private val settings: AppSettings
) : PlayerEngine {

    private companion object {
        /**
         * Otomatik altyazı seçimi için izin verilen en fazla deneme sayısı.
         * Harici altyazıların akışa geç bağlanması durumunda politikanın birkaç
         * `onTracksChanged` turunda yeniden denemesine imkân verir; sonsuz döngüyü engeller.
         */
        const val MAX_AUTO_SUBTITLE_ATTEMPTS = 8
    }

    private val TAG = "Media3PlayerEngine"
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun runOnMainThread(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null
    private var playerView: PlayerView? = null
    private var assSubtitleView: View? = null
    private val listeners = mutableListOf<PlayerEngine.Listener>()
    private val gainAudioProcessor = com.kitsugi.animelist.core.player.GainAudioProcessor().apply {
        setGainDb(settings.defaultAudioBoost * 6f)
    }
    private var currentAspectMode: com.kitsugi.animelist.core.player.PlayerAspectMode = com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL

    // ── T1.13: Diagnostics & Analytics ───────────────────────────────────────
    private val mediaSourceFactory = PlayerMediaSourceFactory(context, settings)
    val diagnostics = PlayerLoadingDiagnostics()
    val analytics = PlayerPlaybackAnalytics()
    
    private var currentResizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var currentSubtitleStyle = SubtitleStyleSettings()

    override val engineType: PlayerEngineType = PlayerEngineType.MEDIA3
    
    override var currentState: PlayerEngine.State = PlayerEngine.State.IDLE
        private set
        
    override val currentPosition: Long
        get() = exoPlayer?.currentPosition ?: 0L
        
    override val duration: Long
        get() = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
        
    override val isPlaying: Boolean
        get() = exoPlayer?.isPlaying ?: false
        
    override val currentSpeed: Float
        get() = exoPlayer?.playbackParameters?.speed ?: settings.playerSpeed
        
    override val currentVolume: Float
        get() = exoPlayer?.volume ?: 1.0f

    override var subtitleDelayMs: Long = 0L
        private set
        
    override var audioDelayMs: Long = settings.defaultAudioDelayMs
        private set
        
    override val isSubtitleDisabled: Boolean
        get() = exoPlayer?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: true

    private var currentAddonName: String? = null
    private var isCsSource: Boolean = false
    private var streamName: String? = null
    private var streamTitle: String? = null
    private var videoUrl: String? = null

    private var videoWidth: Int? = null
    private var videoHeight: Int? = null
    private var videoFrameRate: Float? = null
    private var videoBitrate: Int? = null
    private var videoCodec: String? = null
    private var audioCodec: String? = null
    private var audioChannels: String? = null
    private var audioSampleRate: Int? = null
    private var audioLanguage: String? = null

    /** Hazırlık sırasında motorun aldığı SubtitleInput listesi (dahili/harici bayrağını taşır). */
    private var preparedSubtitles: List<SubtitleInput> = emptyList()
    /**
     * İlk otomatik parça seçimi tamamlandı mı?
     * true = tamamlandı (kullanıcı manuel değişiklik yapabilir, tekrar tetiklenmez)
     * false = henüz yapılmadı (bir sonraki onTracksChanged'de çalışacak)
     */
    private var initialSelectionDone: Boolean = false
    private var initialAudioSelectionDone: Boolean = false
    private var userSelectedAudioTrack: Boolean = false

    /**
     * Kullanıcı altyazı parçasını ELLE seçti mi? true ise otomatik politika bir daha devreye
     * girmez (kullanıcının seçimi ezilmez).
     */
    private var userSelectedTextTrack: Boolean = false

    /**
     * Otomatik Türkçe/tercih edilen altyazı seçimi sonuçlandı mı?
     *
     * Neden tek atımlık `initialSelectionDone` yetmiyordu: ExoPlayer ilk `onTracksChanged`
     * olayını çoğu akışta **harici (sideloaded) altyazılar henüz bağlanmadan** verir. Politika
     * o anda bir kez çalışıp `initialSelectionDone=true` yaptığı için, videonun kendi Türkçe
     * parçası listeye bir tık geç düşünce seçilmiyor ve kullanıcı altyazısız kalıyordu.
     * Artık seçim, hedef dil gerçekten seçilene (veya gerçekten hiç yok sayılana) kadar
     * sınırlı sayıda yeniden denenir.
     */
    private var autoSubtitleSelectionSettled: Boolean = false
    private var autoSubtitleAttemptCount: Int = 0

    override val activeStreamInfo: StreamInfoData
        get() = StreamInfoData(
            addonName = currentAddonName ?: "Dahili",
            streamName = streamName,
            streamDescription = streamTitle,
            filename = videoUrl?.substringAfterLast('/'),
            playerEngine = "ExoPlayer (Dahili)",
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            videoFrameRate = videoFrameRate,
            videoBitrate = videoBitrate,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            audioSampleRate = audioSampleRate,
            audioLanguage = audioLanguage
        )

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // CRITICAL: This is the correct callback for play/pause transitions in ExoPlayer/Media3.
            // onPlaybackStateChanged does NOT fire when the user pauses — only this callback does.
            // Without this, playerEngine.pause() silently succeeds but isPlayingState in the
            // Compose UI never updates, making the pause/stop button appear broken.
            Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying state=$currentState")
            analytics.onPlayingChanged(isPlaying)
            listeners.forEach { it.onStateChanged(currentState) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val mappedState = when (playbackState) {
                Player.STATE_IDLE -> PlayerEngine.State.IDLE
                Player.STATE_BUFFERING -> PlayerEngine.State.BUFFERING
                Player.STATE_READY -> PlayerEngine.State.READY
                Player.STATE_ENDED -> PlayerEngine.State.ENDED
                else -> PlayerEngine.State.IDLE
            }
            currentState = mappedState
            diagnostics.onStateChanged(mappedState.name)
            when (playbackState) {
                Player.STATE_BUFFERING -> diagnostics.onBufferingStarted("rebuffer")
                Player.STATE_READY     -> diagnostics.onBufferingEnded()
                Player.STATE_ENDED     -> {
                    diagnostics.onPlaybackEnded()
                    analytics.endSession()
                }
                else -> { /* no-op */ }
            }
            listeners.forEach { it.onStateChanged(mappedState) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error code: ${error.errorCode}", error)
            diagnostics.onPlaybackError(error.errorCode, error.localizedMessage)
            diagnostics.dump()
            listeners.forEach {
                it.onPlaybackError(error.errorCode, error.localizedMessage ?: "Oynatma hatası", error)
            }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            videoWidth = videoSize.width.takeIf { it > 0 }
            videoHeight = videoSize.height.takeIf { it > 0 }
            applyAspectMode()
            listeners.forEach {
                it.onVideoSizeChanged(
                    videoSize.width,
                    videoSize.height,
                    videoSize.unappliedRotationDegrees,
                    videoSize.pixelWidthHeightRatio
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Subtitle track detection
            val textOptions = mutableListOf<TrackOption>()
            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            textGroups.forEachIndexed { groupIndex, group ->
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val rawLang = format.language
                    val mappedLang = when (rawLang?.lowercase(Locale.ROOT)) {
                        "tur", "tr" -> "Türkçe"
                        "eng", "en" -> "İngilizce"
                        "jpn", "ja" -> "Japonca"
                        "ger", "de" -> "Almanca"
                        "fre", "fra", "fr" -> "Fransızca"
                        "spa", "es" -> "İspanyolca"
                        "ita", "it" -> "İtalyanca"
                        "rus", "ru" -> "Rusça"
                        "chi", "zho", "zh" -> "Çince"
                        "kor", "ko" -> "Korece"
                        else -> rawLang?.uppercase(Locale.ROOT)
                    }
                    val trackLabel = format.label ?: mappedLang ?: "Altyazı #${textOptions.size + 1}"
                    textOptions.add(
                        TrackOption(
                            group = group,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = trackLabel,
                            isSelected = group.isTrackSelected(trackIndex)
                        )
                    )
                }
            }

            // Audio track detection
            val audioOptions = mutableListOf<TrackOption>()
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            audioGroups.forEachIndexed { groupIndex, group ->
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val rawLang = format.language
                    val mappedLang = when (rawLang?.lowercase(Locale.ROOT)) {
                        "tur", "tr" -> "Türkçe"
                        "eng", "en" -> "İngilizce"
                        "jpn", "ja" -> "Japonca"
                        "ger", "de" -> "Almanca"
                        "fre", "fra", "fr" -> "Fransızca"
                        "spa", "es" -> "İspanyolca"
                        "ita", "it" -> "İtalyanca"
                        "rus", "ru" -> "Rusça"
                        "chi", "zho", "zh" -> "Çince"
                        "kor", "ko" -> "Korece"
                        else -> rawLang?.uppercase(Locale.ROOT)
                    }
                    val trackLabel = format.label ?: mappedLang ?: "Ses #${audioOptions.size + 1}"
                    audioOptions.add(
                        TrackOption(
                            group = group,
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = trackLabel,
                            isSelected = group.isTrackSelected(trackIndex)
                        )
                    )
                }
            }

            // ── Ses parçası seçimi (Türkçe önce, sonra tercih listesi, boşsa ilk ses) ──────────────
            // HLS veya çok dilli akışlarda audio parçaları DEFAULT=NO ile gelebilir (örn. Vidmixi/Dosyaload).
            // Hiçbir parça seçili değilse player sessiz oynar! Bu durumda her zaman geçerli bir ses seçilmelidir.
            if (audioOptions.isNotEmpty()) {
                val currentlySelectedAudio = audioOptions.firstOrNull { it.isSelected }
                val isSelectedAudioTurkish = currentlySelectedAudio != null && run {
                    val format = currentlySelectedAudio.group.getTrackFormat(currentlySelectedAudio.trackIndex)
                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(format.language, format.label)
                }

                // Hiçbir ses seçili değilse VEYA (kullanıcı henüz elle seçmediyse ve Türkçe ses bulunabilir durumdaysa):
                if (currentlySelectedAudio == null || (!userSelectedAudioTrack && !initialAudioSelectionDone && !isSelectedAudioTurkish)) {
                    val preferredLangs = settings.preferredSubtitleLanguages
                        .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

                    val preferredAudio = audioOptions.find { opt ->
                        val format = opt.group.getTrackFormat(opt.trackIndex)
                        com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(format.language, format.label)
                    } ?: run {
                        var found: TrackOption? = null
                        for (lang in preferredLangs) {
                            found = audioOptions.find { opt ->
                                val format = opt.group.getTrackFormat(opt.trackIndex)
                                com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesTrackLanguage(format.language, format.label, lang)
                            }
                            if (found != null) break
                        }
                        found ?: audioOptions.firstOrNull()
                    }

                    preferredAudio?.let {
                        if (currentlySelectedAudio != it) {
                            Log.i(TAG, "Auto-selecting audio track (current=${currentlySelectedAudio?.label}): ${it.label}")
                            selectTrack(it)
                        }
                    }
                    initialAudioSelectionDone = true
                }
            }

            // ── Otomatik altyazı seçimi ────────────────────────────────────────────────
            // Kullanıcının ELLE yaptığı seçim asla ezilmez; onun dışında hedef dil gerçekten
            // seçilene kadar (sınırlı denemeyle) yeniden denenir. Tek atımlık eski davranış,
            // harici altyazılar ilk onTracksChanged'e yetişmediğinde videonun kendi Türkçe
            // parçasının hiç seçilmemesine yol açıyordu.
            if (!userSelectedTextTrack && !autoSubtitleSelectionSettled) {
                val preferredLangs = settings.preferredSubtitleLanguages
                    .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

                // ── Altyazı: Kesin Öncelik Hiyerarşisi ──────────────────────────────────
                // 1. Dahili / site kaynaklı Türkçe altyazı (isExternal = false)
                // 2. Harici (OpenSubtitles vb.) Türkçe altyazı (isExternal = true)
                // 3. Kullanıcı diğer tercih dilleri
                // 4. Eşleşme yoksa: Devre dışı bırak (İtalyanca vb. yabancı diller ASLA seçilmez!)
                if (!isSubtitleDisabled && textOptions.isNotEmpty()) {
                    // Harici altyazı tespiti: ÖNCE track kimliği (setId ile verilen sub.url),
                    // sonra birebir (büyük/küçük harf duyarsız) isim eşleşmesi.
                    // Eskiden "contains" tabanlı eşleşme yüzünden dahili "Türkçe" parçası,
                    // "Türkçe (addon)" adlı harici altyazıyla karıştırılıp harici sayılıyordu.
                    val isExternalTrack: (TrackOption) -> Boolean = { opt ->
                        val format = opt.group.getTrackFormat(opt.trackIndex)
                        val label = format.label ?: ""
                        val id = format.id ?: ""
                        val externals = preparedSubtitles.filter { it.isExternal }
                        externals.any { sub ->
                            (id.isNotBlank() && (id == sub.url || id == "file://${sub.url}")) ||
                                (label.isNotBlank() && sub.name.isNotBlank() && label.equals(sub.name, ignoreCase = true))
                        }
                    }

                    val currentlySelected = textOptions.firstOrNull { it.isSelected }
                    // Politikanın kabul ettiği bir parça seçili mi? (dahili/harici Türkçe ya da
                    // kullanıcının tercih listesindeki bir dil)
                    val currentSatisfiesPolicy = currentlySelected != null && run {
                        val format = currentlySelected.group.getTrackFormat(currentlySelected.trackIndex)
                        com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(format.language, format.label) ||
                            com.kitsugi.animelist.core.player.SubtitleSelectionPolicy
                                .effectiveLanguages(preferredLangs)
                                .any { lang ->
                                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils
                                        .matchesTrackLanguage(format.language, format.label, lang)
                                }
                    }

                    if (currentSatisfiesPolicy) {
                        // Hedefe ulaşıldı — artık otomatik seçim devreye girmez.
                        autoSubtitleSelectionSettled = true
                    } else {
                        // Tek ortak politika (SubtitleSelectionPolicy) — MPV ile birebir aynı sonuç.
                        val candidates = textOptions.map { opt ->
                            val format = opt.group.getTrackFormat(opt.trackIndex)
                            com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.Candidate(
                                item = opt,
                                lang = format.language,
                                label = format.label,
                                isExternal = isExternalTrack(opt),
                            )
                        }
                        val bestSub = com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.pick(candidates, preferredLangs)
                        if (bestSub != null) {
                            // Sınırlı yeniden deneme: override'ın bir sonraki onTracksChanged'e
                            // yansıması gerekir; yansımazsa sonsuz döngü yerine pes edilir.
                            if (currentlySelected != bestSub && autoSubtitleAttemptCount < MAX_AUTO_SUBTITLE_ATTEMPTS) {
                                autoSubtitleAttemptCount++
                                Log.i(
                                    TAG,
                                    "Auto-selecting best subtitle track (attempt $autoSubtitleAttemptCount): ${bestSub.label}"
                                )
                                selectTrack(bestSub)
                            } else if (currentlySelected == bestSub || autoSubtitleAttemptCount >= MAX_AUTO_SUBTITLE_ATTEMPTS) {
                                autoSubtitleSelectionSettled = true
                            }
                        } else {
                            // Türkçe veya tercih edilen dil yoksa yabancı dili (İtalyanca vb.) kapat
                            Log.i(TAG, "No Turkish/preferred subtitle found. Disabling non-preferred track: ${currentlySelected?.label}")
                            disableSubtitles()
                            autoSubtitleSelectionSettled = true
                        }
                    }
                }
            }

            // Detailed format diagnostics
            videoWidth = null
            videoHeight = null
            videoFrameRate = null
            videoBitrate = null
            videoCodec = null
            audioCodec = null
            audioChannels = null
            audioSampleRate = null
            audioLanguage = null

            tracks.groups.forEach { group ->
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        if (group.type == C.TRACK_TYPE_VIDEO) {
                            videoWidth = format.width.takeIf { it > 0 }
                            videoHeight = format.height.takeIf { it > 0 }
                            videoFrameRate = format.frameRate.takeIf { it > 0f }
                            videoBitrate = format.bitrate.takeIf { it > 0 }
                            videoCodec = format.codecs ?: format.sampleMimeType
                        } else if (group.type == C.TRACK_TYPE_AUDIO) {
                            audioCodec = format.codecs ?: format.sampleMimeType
                            audioChannels = format.channelCount.takeIf { it > 0 }?.toString()
                            audioSampleRate = format.sampleRate.takeIf { it > 0 }
                            audioLanguage = format.language
                        }
                    }
                }
            }

            listeners.forEach { it.onTracksChanged(audioOptions, textOptions) }
        }
    }

    // ── Dolby Vision runtime state ──────────────────────────────────────────
    /** Policy result computed once per playback session in [prepare]. Null = not yet resolved. */
    @Volatile private var dvPolicy: DolbyVisionBaseLayerPolicy.Result? = null
    /** Effective DV7 handling mode for the current playback. Derived from settings + policy. */
    @Volatile private var effectiveDv7Mode: Dv7HandlingMode = Dv7HandlingMode.AUTO

    private fun initializePlayer() {
        runOnMainThread {
            if (exoPlayer != null) return@runOnMainThread
            val loadControl = BitrateAwareLoadControl(
                minBufferMs = settings.minBufferMs,
                maxBufferMs = settings.maxBufferMs,
                bufferForPlaybackMs = settings.bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs = settings.bufferForPlaybackAfterRebufferMs,
                prioritizeTimeOverSizeThresholds = true,
                backBufferDurationMs = settings.backBufferDurationMs,
                retainBackBufferFromKeyframe = false
            )
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink? {
                    val caps = androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context)
                    return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setAudioCapabilities(caps)
                        .setAudioProcessors(arrayOf(gainAudioProcessor))
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
                }
            }.apply {
                val mode = if (settings.decoderPriority == 0) {
                    androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                } else {
                    settings.decoderPriority
                }
                setExtensionRendererMode(mode)
                setEnableDecoderFallback(true)
            }

            val preferredLangs = settings.preferredSubtitleLanguages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val expandedTextLangs = mutableListOf<String>()
            preferredLangs.forEach { lang ->
                if (lang == "tr" || lang == "tur") {
                    expandedTextLangs.add("tr")
                    expandedTextLangs.add("tur")
                    expandedTextLangs.add("turkish")
                } else {
                    expandedTextLangs.add(lang)
                }
            }
            val preferredAudioLangs = listOf("tr", "tur", "en", "eng", "ja", "jpn", "zxx", "und")
            val localSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setPreferredAudioLanguages(*preferredAudioLangs.toTypedArray())
                    .setPreferredTextLanguages(*expandedTextLangs.toTypedArray())
                    .setSelectUndeterminedTextLanguage(false)
                    .build()
            }
            this.trackSelector = localSelector

            val baseBuilder = ExoPlayer.Builder(context)
                .setTrackSelector(localSelector)
                .setLoadControl(loadControl)

            exoPlayer = if (settings.enableAssExtractor) {
                val dsFactoryForAss = mediaSourceFactory.buildDataSourceFactory(
                    addonName = null,
                    headers = emptyMap(),
                    videoUrl = "",
                    streamTitle = null,
                    isCS = false,
                    qualityValue = null
                )
                baseBuilder.buildWithAssSupportCompat(
                    context = context,
                    renderType = io.github.peerless2012.ass.media.type.AssRenderType.CUES,
                    playerMediaSourceFactory = mediaSourceFactory,
                    dataSourceFactory = dsFactoryForAss,
                    extractorsFactory = DefaultExtractorsFactory()
                        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                        .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE),
                    renderersFactory = renderersFactory
                )
            } else {
                baseBuilder
                    .setRenderersFactory(renderersFactory)
                    .build()
            }.apply {
                playWhenReady = true
                addListener(playerListener)
                // Set proper AudioAttributes so Android audio focus is handled correctly.
                // Without this, MIUI/Xiaomi may silently duck or mute media audio.
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
            }
            playerView?.player = exoPlayer
        }
    }

    init {
        initializePlayer()
    }

    override fun addListener(listener: PlayerEngine.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayerEngine.Listener) {
        listeners.remove(listener)
    }

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
        withContext(Dispatchers.Main) {
            initializePlayer()
            val player = exoPlayer
            if (player != null) {
                // Ensure subtitle/text tracks are enabled by default
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
            trackSelector?.let { selector ->
                val preferredLangs = settings.preferredSubtitleLanguages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val expandedTextLangs = mutableListOf<String>()
                preferredLangs.forEach { lang ->
                    if (lang == "tr" || lang == "tur") {
                        expandedTextLangs.add("tr")
                        expandedTextLangs.add("tur")
                        expandedTextLangs.add("turkish")
                    } else {
                        expandedTextLangs.add(lang)
                    }
                }
                selector.parameters = selector.buildUponParameters()
                    .setPreferredTextLanguages(*expandedTextLangs.toTypedArray())
                    .setSelectUndeterminedTextLanguage(false)
                    .build()
            }
        }
        this.videoUrl = videoUrl
        this.currentAddonName = addonName
        this.isCsSource = isCS
        this.streamTitle = streamTitle
        // Yeni ortam için otomatik parça seçimini sıfırla
        this.preparedSubtitles = subtitles
        this.initialSelectionDone = false
        this.initialAudioSelectionDone = false
        this.userSelectedAudioTrack = false
        this.userSelectedTextTrack = false
        this.autoSubtitleSelectionSettled = false
        this.autoSubtitleAttemptCount = 0

        // ── T1.13: Start diagnostics & analytics session ──────────────────────
        val sessionId = java.util.UUID.randomUUID().toString().take(8)
        diagnostics.startSession(sessionId, videoUrl)
        analytics.startSession(sessionId, videoUrl)

        // ── Ağır Dolby Vision hesaplamalarını IO thread'de yap ───────────────
        // ExoPlayer'a dokunmadan önce IO'da DV policy resolve et — ana thread bloke olmaz
        var resolvedPolicy: DolbyVisionBaseLayerPolicy.Result? = null
        var finalMode: Dv7HandlingMode = Dv7HandlingMode.OFF
        var isConvertToDv81: Boolean = false
        var stripDvRpuEnabled: Boolean = false
        var effectiveExtractorsFactory: ExtractorsFactory = DefaultExtractorsFactory()
        var mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        var dataSourceFactoryForMainThread: androidx.media3.datasource.DataSource.Factory? = null
        var mergingSourceForMainThread: MergingMediaSource? = null


        withContext(Dispatchers.IO) {
            val player = exoPlayer ?: return@withContext

            // ── Dolby Vision Policy Resolution ──────────────────────────────────
            DoviBridge.resetRuntimeCounters()
            DolbyVisionConversionStats.reset()

            val rPolicy: DolbyVisionBaseLayerPolicy.Result?
            val rMode: Dv7HandlingMode
            when (settings.dv7HandlingMode) {
                Dv7HandlingMode.AUTO -> {
                    val result = DolbyVisionBaseLayerPolicy.resolve(
                        context = context,
                        bridgeReady = DoviBridge.isLibraryLoaded
                    )
                    rPolicy = result
                    rMode = when (result.decision) {
                        DolbyVisionBaseLayerPolicy.Decision.NATIVE_DV7      -> Dv7HandlingMode.OFF
                        DolbyVisionBaseLayerPolicy.Decision.CONVERT_TO_DV81 -> Dv7HandlingMode.DV81_LIBDOVI
                        else                                                 -> Dv7HandlingMode.HDR10_BASE_LAYER
                    }
                    Log.i(TAG, "DV7_AUTO: decision=${result.decision} effectiveMode=$rMode " +
                            "displayDv=${result.displayDv} displayHdr10=${result.displayHdr10} " +
                            "bridgeReady=${result.bridgeReady} url=$videoUrl")
                }
                else -> {
                    rPolicy = null
                    rMode = settings.dv7HandlingMode
                }
            }
            dvPolicy = rPolicy
            effectiveDv7Mode = rMode
            resolvedPolicy = rPolicy

            // Inform the vendored Matroska extractor whether HDR10 base-layer mode is active.
            val isHdr10BaseLayerMode = rMode == Dv7HandlingMode.HDR10_BASE_LAYER ||
                    rMode == Dv7HandlingMode.STRIP_DV
            DolbyVisionCompatibility.setHdr10BaseLayerModeActive(isHdr10BaseLayerMode)

            // ── DV7 Probe (only runs when conversion is requested) ───────────────
            val dv7ToDv81Active = rMode == Dv7HandlingMode.DV81_LIBDOVI
            val dv81Probe = if (dv7ToDv81Active) {
                DoviBridge.probeRealtimeConversionSupport(videoUrl)
            } else {
                null
            }

            val fm = if (settings.dv7HandlingMode == Dv7HandlingMode.AUTO &&
                rMode == Dv7HandlingMode.DV81_LIBDOVI &&
                dv81Probe?.supported != true
            ) {
                Log.i(TAG, "DV7_AUTO_FALLBACK: dv81-probe-failed reason=${dv81Probe?.reason} → HDR10_BASE_LAYER")
                Dv7HandlingMode.HDR10_BASE_LAYER
            } else {
                rMode
            }
            effectiveDv7Mode = fm
            finalMode = fm

            isConvertToDv81 = fm == Dv7HandlingMode.DV81_LIBDOVI && dv81Probe?.supported == true
            stripDvRpuEnabled = fm == Dv7HandlingMode.HDR10_BASE_LAYER || fm == Dv7HandlingMode.STRIP_DV

            Log.i(TAG, "DV7_FINAL: mode=$fm convert=$isConvertToDv81 stripRpu=$stripDvRpuEnabled " +
                    "stripHdr10Plus=${settings.stripHdr10PlusSei} bridgeLoaded=${DoviBridge.isLibraryLoaded}")

            // ── Extractors Factory ───────────────────────────────────────────────
            val baseExtractorsFactory = DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

            effectiveExtractorsFactory =
                if (isConvertToDv81 || stripDvRpuEnabled || settings.stripHdr10PlusSei) {
                    DolbyVisionExtractorsFactory(
                        delegate = baseExtractorsFactory,
                        config = DolbyVisionConversionConfig(
                            active = isConvertToDv81,
                            forcedMode = -1,
                            preserveMapping = false,
                            dv5Enabled = false,
                            manualDv81 = fm == Dv7HandlingMode.DV81_LIBDOVI
                        ),
                        stripDvRpu = stripDvRpuEnabled,
                        stripHdr10PlusSei = settings.stripHdr10PlusSei
                    )
                } else {
                    baseExtractorsFactory
                }

            // ── MediaItem ────────────────────────────────────────────────────────
            val videoUri = Uri.parse(videoUrl)
            val isM3u8 = PlayerMediaSourceFactory.isHlsUrl(videoUrl)
            val isDash = videoUrl.contains(".mpd", ignoreCase = true) || videoUrl.contains("mpd", ignoreCase = true)
            val isSs = videoUrl.contains(".ism", ignoreCase = true)

            val mediaItemBuilder = MediaItem.Builder().setUri(videoUri)
            if (isM3u8) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            else if (isDash) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            else if (isSs) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)

            if (subtitles.isNotEmpty()) {
                val subConfigs = subtitles.map { sub ->
                    val mime = when {
                        sub.url.startsWith("/") || sub.url.startsWith("file://") -> {
                            val path = sub.url.removePrefix("file://")
                            guessLocalMimeType(path)
                        }
                        sub.url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                        sub.url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                        sub.url.contains(".ass", ignoreCase = true) || sub.url.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.TEXT_VTT
                    }
                    // NOT: "file://$sub.url" yanlış interpolasyondu (sub.toString() + ".url").
                    // Yerel önbellek yolu için doğru şema "file://${sub.url}" olmalı.
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(if (sub.url.startsWith("/")) "file://${sub.url}" else sub.url))
                        // Harici altyazıyı track kimliğiyle eşleştirebilmek için benzersiz id:
                        // isim benzerliğine (ör. "Türkçe" dahili / "Türkçe (addon)" harici) güvenmiyoruz.
                        .setId(sub.url)
                        .setMimeType(mime)
                        .setLanguage(sub.lang)
                        .setLabel(sub.name)
                        .build()
                }
                mediaItemBuilder.setSubtitleConfigurations(subConfigs)
            }
            mediaItem = mediaItemBuilder.build()

            // ── T1.13: Delegate media source creation to PlayerMediaSourceFactory ──
            // All DataSource / ExtractorFactory / MergingMediaSource logic lives there.
            val builtSource = mediaSourceFactory.create(
                videoUrl         = videoUrl,
                audioUrl         = audioUrl,
                headers          = headers,
                subtitles        = subtitles,
                isCS             = isCS,
                addonName        = addonName,
                streamTitle      = streamTitle,
                qualityValue     = qualityValue,
                extractorsFactory = effectiveExtractorsFactory,
            )
            // Wrap into the existing two-path variable so the Main-thread block below works unchanged
            mergingSourceForMainThread = builtSource as? MergingMediaSource
            dataSourceFactoryForMainThread = null
        }

        // ── ExoPlayer çağrıları MUTLAKA Main thread'de yapılmalı ────────────────
        withContext(Dispatchers.Main) {
            val player = exoPlayer ?: return@withContext
            player.stop()
            player.clearMediaItems()

            // T1.13: builtSource is always non-null after factory.create()
            // Re-build from factory if the IO block produced a plain MediaSource (not MergingMediaSource)
            val sourceToSet = mergingSourceForMainThread
                ?: mediaSourceFactory.create(
                    videoUrl          = videoUrl,
                    audioUrl          = audioUrl,
                    headers           = headers,
                    subtitles         = subtitles,
                    isCS              = isCS,
                    addonName         = addonName,
                    streamTitle       = streamTitle,
                    qualityValue      = qualityValue,
                    extractorsFactory = effectiveExtractorsFactory,
                )
            player.setMediaSource(sourceToSet)

            player.playbackParameters = androidx.media3.common.PlaybackParameters(settings.playerSpeed)
            player.playWhenReady = true
            player.prepare()
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs)
            }
            diagnostics.onBufferingStarted("initial-load")
        }
    }

    override fun play() {
        runOnMainThread {
            exoPlayer?.play()
        }
    }

    override fun pause() {
        runOnMainThread {
            exoPlayer?.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        runOnMainThread {
            exoPlayer?.seekTo(positionMs)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        runOnMainThread {
            exoPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        }
    }

    override fun setVolume(volume: Float) {
        runOnMainThread {
            val player = exoPlayer ?: return@runOnMainThread
            if (volume > 1.0f) {
                player.volume = 1.0f
                val boost = volume - 1.0f
                gainAudioProcessor.setGainDb(boost * 6f)
            } else {
                player.volume = volume
                gainAudioProcessor.setGainDb(0f)
            }
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        subtitleDelayMs = delayMs
        // Media3 standard Player interface does not support generic dynamic subtitle offset shift natively.
        Log.w(TAG, "Subtitle delay of $delayMs ms requested but not supported natively in Media3 wrapper.")
    }

    override fun setAudioDelay(delayMs: Long) {
        audioDelayMs = delayMs
        // Best effort / no-op warning
        Log.w(TAG, "Audio delay of $delayMs ms requested but not supported natively in Media3 wrapper.")
    }

    override fun setSubtitleStyle(style: SubtitleStyleSettings) {
        currentSubtitleStyle = style
        runOnMainThread { applySubtitleStylesToView() }
    }

    override fun setResizeMode(resizeMode: Int) {
        currentResizeMode = resizeMode
        runOnMainThread { applyAspectMode() }
    }

    override fun setAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode) {
        currentAspectMode = mode
        currentResizeMode = com.kitsugi.animelist.core.player.PlayerAspectScaleUtils.getMedia3ResizeMode(mode)
        runOnMainThread { applyAspectMode() }
    }

    private fun applyAspectMode() {
        runOnMainThread {
            val pv = playerView ?: return@runOnMainThread
            val contentFrame = pv.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return@runOnMainThread
            val overrideRatio = com.kitsugi.animelist.core.player.PlayerAspectScaleUtils.getMedia3AspectRatioOverride(currentAspectMode)
            if (overrideRatio > 0f) {
                contentFrame.setAspectRatio(overrideRatio)
            } else {
                val w = videoWidth ?: 0
                val h = videoHeight ?: 0
                if (w > 0 && h > 0) {
                    contentFrame.setAspectRatio(w.toFloat() / h)
                }
            }
            pv.resizeMode = currentResizeMode
        }
    }

    override fun selectTrack(trackOption: TrackOption) {
        runOnMainThread {
            val player = exoPlayer ?: return@runOnMainThread
            val isSubtitle = trackOption.group.type == C.TRACK_TYPE_TEXT
            val type = if (isSubtitle) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
            if (type == C.TRACK_TYPE_AUDIO) {
                userSelectedAudioTrack = true
            } else {
                // Kullanıcı altyazıyı elle seçti → otomatik politika bir daha karışmasın.
                userSelectedTextTrack = true
                autoSubtitleSelectionSettled = true
            }
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(type, false)
                .setOverrideForType(
                    TrackSelectionOverride(
                        trackOption.group.mediaTrackGroup,
                        trackOption.trackIndex
                    )
                )
                .build()
        }
    }

    override fun disableSubtitles() {
        runOnMainThread {
            val player = exoPlayer ?: return@runOnMainThread
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    override fun createVideoView(context: Context): View {
        if (playerView == null) {
            val root = LayoutInflater.from(context).inflate(R.layout.trailer_player_view, null)
            val pv = root.findViewById<PlayerView>(R.id.trailer_player_view)
            pv.player = exoPlayer
            playerView = pv
            applySubtitleStylesToView()
            applyAspectMode()

            if (settings.enableAssExtractor) {
                val assHandler = exoPlayer?.getAssHandlerCompat()
                if (assHandler != null) {
                    val assView = io.github.peerless2012.ass.media.widget.AssSubtitleView(context, assHandler)
                    assSubtitleView = assView
                    pv.addView(
                        assView,
                        android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }
        return playerView!!
    }

    private fun applySubtitleStylesToView() {
        runOnMainThread {
            val pv = playerView ?: return@runOnMainThread
            pv.subtitleView?.let { subtitleView ->
                subtitleView.setApplyEmbeddedStyles(false)
                subtitleView.setApplyEmbeddedFontSizes(false)
                
                val edgeType = if (currentSubtitleStyle.outlineEnabled) {
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE
                } else {
                    CaptionStyleCompat.EDGE_TYPE_NONE
                }
                val typeface = if (currentSubtitleStyle.bold) {
                    android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    android.graphics.Typeface.DEFAULT
                }
                
                val captionStyle = CaptionStyleCompat(
                    currentSubtitleStyle.textColor,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    edgeType,
                    android.graphics.Color.BLACK,
                    typeface
                )
                subtitleView.setStyle(captionStyle)
                subtitleView.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    currentSubtitleStyle.size.toFloat()
                )
                
                val density = context.resources.displayMetrics.density
                val requested = -currentSubtitleStyle.verticalOffset.toFloat() * density
                // Kullanıcı "Dikey Konum" kaydırıcısını eksiye çektiğinde altyazı görünür
                // alanın dışına (alt barın arkasına / ekran dışına) itilebiliyordu. Çeviri
                // miktarını altyazı alanının içine sabitliyoruz: alt kenarda 4dp pay kalır,
                // üstte ise tüm alanı kaplayacak kadar ötelenemez.
                val subHeight = subtitleView.height.takeIf { it > 0 }
                    ?: (pv.height.takeIf { it > 0 } ?: 0)
                val marginPx = 4f * density
                val translation = if (subHeight > 0) {
                    requested.coerceIn(-(subHeight - marginPx), subHeight - marginPx)
                } else {
                    requested
                }
                subtitleView.translationY = translation
                assSubtitleView?.translationY = translation
            }
        }
    }

    private fun guessLocalMimeType(urlPath: String): String {
        val file = java.io.File(urlPath)
        if (file.exists() && file.isFile) {
            try {
                file.bufferedReader().use { reader ->
                    val lines = mutableListOf<String>()
                    for (i in 0 until 10) {
                        val line = reader.readLine() ?: break
                        lines.add(line.trim())
                    }
                    val content = lines.joinToString("\n")
                    if (content.contains("[Script Info]") || content.contains("[V4+ Styles]")) {
                        return MimeTypes.TEXT_SSA
                    }
                    if (content.contains("WEBVTT")) {
                        return MimeTypes.TEXT_VTT
                    }
                    if (content.contains("-->")) {
                        return MimeTypes.APPLICATION_SUBRIP
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guessing mime type from file content: $urlPath", e)
            }
        }
        // Fallback to extension or default
        return when {
            urlPath.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
            urlPath.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
            urlPath.endsWith(".ass", ignoreCase = true) || urlPath.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_VTT
        }
    }

    override fun release() {
        runOnMainThread {
            exoPlayer?.let { player ->
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                player.release()
            }
            exoPlayer = null
            playerView?.player = null
            playerView = null
            listeners.clear()
        }
    }
}

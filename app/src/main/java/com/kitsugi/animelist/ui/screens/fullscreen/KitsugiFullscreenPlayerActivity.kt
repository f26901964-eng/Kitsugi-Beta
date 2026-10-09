package com.kitsugi.animelist.ui.screens.fullscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kitsugi.animelist.core.player.ExternalPlayerLauncher
import com.kitsugi.animelist.core.player.PlayerMediaSessionHelper
import com.kitsugi.animelist.core.player.PlayerPipHelper
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KitsugiFullscreenPlayerActivity : ComponentActivity() {

    private val viewModel: KitsugiPlayerViewModel by viewModels()
    private var isPipEnabled = true

    // ── T2.3: MediaSession + PiP BroadcastReceiver ────────────────────────────
    private var mediaSessionHelper: PlayerMediaSessionHelper? = null

    /**
     * Oynatıcı ekranının (Compose) köprüsü. PiP penceresindeki / bildirimdeki tuşlar ve
     * Activity yaşam döngüsü olayları buraya iletilir. Ekran, DisposableEffect ile kaydolur
     * ve çıkarken [setPipPlayerCallback] ile `null` yapar.
     */
    private var pipPlayerCallback: PipPlayerCallback? = null

    /** PiP penceresinde miyiz? (onPictureInPictureModeChanged ile güncellenir) */
    private var isInPipNow = false

    /**
     * Harici (3. parti) oynatıcıya devredildi mi? true ise Activity'nin görünmez olması
     * "mini pencere kapatıldı" anlamına gelmez; bu yüzden onStop'te kapanış tetiklenmez.
     */
    private var handedOffToExternalPlayer = false

    /** Son bilinen oynatma durumu — PiP'e geçerken RemoteAction ikonlarını doğru kurmak için. */
    private var lastKnownIsPlaying = false
    private var lastKnownHasNext = false

    interface PipPlayerCallback {
        fun onPipPlay()
        fun onPipPause()
        fun onPipSkipNext()
    }

    fun setPipPlayerCallback(callback: PipPlayerCallback?) {
        pipPlayerCallback = callback
    }

    private val pipBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PlayerPipHelper.ACTION_PLAY -> invokePipAction { it.onPipPlay() }
                PlayerPipHelper.ACTION_PAUSE -> invokePipAction { it.onPipPause() }
                PlayerPipHelper.ACTION_SKIP_NEXT -> invokePipAction { it.onPipSkipNext() }
            }
        }
    }

    /**
     * PiP/bildirim tuşlarını oynatıcıya iletir.
     *
     * Compose köprüsü (`pipPlayerCallback`) yalnızca oynatıcı yüzeyi ağaçtayken kayıtlıdır;
     * kaynak çözümleme/hata durumlarında `null` olabiliyordu ve bu yüzden PiP penceresindeki
     * Oynat/Duraklat/Sonraki tuşları hiçbir şey yapmıyordu. Köprü yoksa doğrudan
     * ViewModel'e düşüyoruz — ViewModel Activity ömrüne bağlı olduğu için her zaman canlıdır.
     */
    private fun invokePipAction(action: (PipPlayerCallback) -> Unit) {
        val callback = pipPlayerCallback
        if (callback != null) {
            runCatching { action(callback) }
            return
        }
        runCatching {
            val fallback = object : PipPlayerCallback {
                override fun onPipPlay() = viewModel.play()
                override fun onPipPause() = viewModel.pause()
                // "Sonraki bölüm" akışı ekran tarafındaki çözümleyiciye bağlıdır; köprü
                // yokken güvenli bir karşılığı olmadığı için bilinçli olarak no-op.
                override fun onPipSkipNext() = Unit
            }
            action(fallback)
        }
    }

    /** Oynatmayı her koşulda durdurur (köprü olmasa bile ses arka planda devam etmesin). */
    private fun stopPlaybackEverywhere() {
        invokePipAction { it.onPipPause() }
    }

    /**
     * Sistem çubuklarını (durum + alt gezinme çubuğu) gizler ve yalnızca kaydırmayla geçici
     * olarak görünmelerine izin verir.
     *
     * Neden tekrar tekrar çağrılıyor: `onCreate` içinde tek sefer gizlemek yetmiyor. MIUI/HyperOS
     * başta olmak üzere bazı kabuklarda PiP'e geçiş/çıkış, bölünmüş ekran ve odak değişimi
     * sonrası alt gezinme çubuğu "geçici" olmaktan çıkıp kalıcı hâle geliyor ve video oynarken
     * ekranın altında beliriyordu. Bu yüzden her odaklanma ve her onResume'da yeniden uygulanır.
     */
    private fun applyImmersiveMode() {
        runCatching {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
    }

    companion object {
        const val EXTRA_VIDEO_ID  = "extra_video_id"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_TITLE     = "extra_title"
        const val EXTRA_HEADERS   = "extra_headers"

        const val EXTRA_SUBTITLES_JSON = "extra_subtitles_json"
        const val EXTRA_STREAM_LIST_JSON = "extra_stream_list_json"
        const val EXTRA_CURRENT_INDEX = "extra_current_index"
        const val EXTRA_MAL_ID = "extra_mal_id"
        const val EXTRA_ANILIST_ID = "extra_anilist_id"
        const val EXTRA_TMDB_ID = "extra_tmdb_id"
        const val EXTRA_EPISODE = "extra_episode"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_ANIME_TITLE = "extra_anime_title"
        const val EXTRA_POSTER_URL = "extra_poster_url"
        const val EXTRA_TITLE_ENGLISH = "extra_title_english"
        const val EXTRA_TITLE_ROMAJI = "extra_title_romaji"
        const val EXTRA_TITLE_NATIVE = "extra_title_native"
        const val EXTRA_SYNONYMS = "extra_synonyms"
        const val EXTRA_START_YEAR = "extra_start_year"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_CAST_JSON   = "extra_cast_json"
        const val EXTRA_IS_MOVIE    = "extra_is_movie"
        const val EXTRA_CS3_URL     = "extra_cs3_url"
        const val EXTRA_CS3_API_NAME = "extra_cs3_api_name"
        const val EXTRA_RESUME_POSITION = "extra_resume_position"

        @Volatile
        var tempStreamSources: List<StreamSource>? = null

        @Volatile
        var tempCast: List<MetaCastMember>? = null

        @Volatile
        var tempSubtitles: List<SubtitleInput>? = null

        fun startWithYouTubeId(context: Context, videoId: String, title: String = "") {
            context.startActivity(
                Intent(context, KitsugiFullscreenPlayerActivity::class.java).apply {
                    putExtra(EXTRA_VIDEO_ID, videoId)
                    putExtra(EXTRA_TITLE, title)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        fun startWithStreamUrls(
            context: Context,
            videoUrl: String,
            audioUrl: String? = null,
            title: String = "",
            headers: Map<String, String>? = null,
            subtitles: List<SubtitleInput> = emptyList(),
            allSources: List<StreamSource> = emptyList(),
            currentSourceIndex: Int = -1,
            malId: Int? = null,
            aniListId: Int? = null,
            tmdbId: Int? = null,
            season: Int = 1,
            episode: Int = 1,
            animeTitle: String = "",
            posterUrl: String? = null,
            titleEnglish: String? = null,
            titleRomaji: String? = null,
            titleNative: String? = null,
            synonyms: List<String> = emptyList(),
            startYear: Int? = null,
            description: String? = null,
            cast: List<MetaCastMember> = emptyList(),
            isMovie: Boolean = false,
            cs3Url: String? = null,
            cs3ApiName: String? = null,
            resumePositionMs: Long = 0L
        ) {
            tempSubtitles = subtitles
            tempStreamSources = allSources
            tempCast = cast

            try {
                context.startActivity(
                    Intent(context, KitsugiFullscreenPlayerActivity::class.java).apply {
                        putExtra(EXTRA_VIDEO_URL, videoUrl)
                        putExtra(EXTRA_AUDIO_URL, audioUrl)
                        putExtra(EXTRA_TITLE, title)
                        cs3Url?.let { putExtra(EXTRA_CS3_URL, it) }
                        cs3ApiName?.let { putExtra(EXTRA_CS3_API_NAME, it) }
                        if (!headers.isNullOrEmpty()) {
                            val bundle = android.os.Bundle()
                            headers.forEach { (k, v) -> bundle.putString(k, v) }
                            putExtra(EXTRA_HEADERS, bundle)
                        }
                        putExtra(EXTRA_CURRENT_INDEX, currentSourceIndex)
                        malId?.let { putExtra(EXTRA_MAL_ID, it) }
                        aniListId?.let { putExtra(EXTRA_ANILIST_ID, it) }
                        tmdbId?.let { putExtra(EXTRA_TMDB_ID, it) }
                        putExtra(EXTRA_SEASON, season)
                        putExtra(EXTRA_EPISODE, episode)
                        putExtra(EXTRA_ANIME_TITLE, animeTitle)
                        putExtra(EXTRA_POSTER_URL, posterUrl)
                        putExtra(EXTRA_TITLE_ENGLISH, titleEnglish)
                        putExtra(EXTRA_TITLE_ROMAJI, titleRomaji)
                        putExtra(EXTRA_TITLE_NATIVE, titleNative)
                        if (synonyms.isNotEmpty()) putStringArrayListExtra(EXTRA_SYNONYMS, ArrayList(synonyms))
                        startYear?.let { putExtra(EXTRA_START_YEAR, it) }
                        description?.let { putExtra(EXTRA_DESCRIPTION, it) }
                        putExtra(EXTRA_IS_MOVIE, isMovie)
                        putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiPlayer", "Oynatıcı başlatılırken hata oluştu: ${e.message}", e)
                android.widget.Toast.makeText(context, "Oynatıcı başlatılamadı: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        fun launchExternalPlayer(
            context: Context,
            videoUrl: String,
            title: String,
            positionMs: Long,
            headers: Map<String, String>? = null,
            subtitles: List<SubtitleInput>? = null
        ) {
            val launched = ExternalPlayerLauncher.launch(
                context          = context,
                url              = videoUrl,
                title            = title,
                headers          = headers,
                resumePositionMs = positionMs,
                subtitles        = subtitles
            )
            if (!launched) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "Harici oynatıcı başlatılamadı.", Toast.LENGTH_SHORT).show()
                }
            }
            // Harici oynatıcıya devredildi: bu Activity artık "mini pencere kapatıldı" diye
            // sonlandırılmamalı (kullanıcı geri döndüğünde yerinde bulsun).
            (context as? KitsugiFullscreenPlayerActivity)?.handedOffToExternalPlayer = true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // T1.8: KeepAliveService start
        com.kitsugi.animelist.core.player.KeepAliveService.start(this)

        // T2.3: PiP BroadcastReceiver kaydı
        val filter = IntentFilter().apply {
            addAction(PlayerPipHelper.ACTION_PLAY)
            addAction(PlayerPipHelper.ACTION_PAUSE)
            addAction(PlayerPipHelper.ACTION_SKIP_NEXT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipBroadcastReceiver, filter)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        val videoId  = intent.getStringExtra(EXTRA_VIDEO_ID)
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        val title    = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val headersBundle = intent.getBundleExtra(EXTRA_HEADERS)
        val requestHeaders: Map<String, String> = if (headersBundle != null) {
            buildMap { headersBundle.keySet().forEach { k -> headersBundle.getString(k)?.let { v -> put(k, v) } } }
        } else emptyMap()

        val initialSubtitles: List<SubtitleInput> = tempSubtitles ?: run {
            val subtitlesJson = intent.getStringExtra(EXTRA_SUBTITLES_JSON)
            if (!subtitlesJson.isNullOrEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<SubtitleInput>>() {}.type
                    com.google.gson.Gson().fromJson(subtitlesJson, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
        }
        tempSubtitles = null

        val rawStreamSources: List<StreamSource> = tempStreamSources ?: run {
            val streamsJson = intent.getStringExtra(EXTRA_STREAM_LIST_JSON)
            if (!streamsJson.isNullOrEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<StreamSource>>() {}.type
                    com.google.gson.Gson().fromJson(streamsJson, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
        }
        tempStreamSources = null
        val streamSources = rawStreamSources.map {
            it.copy(
                subtitles = it.subtitles ?: emptyList(),
                addonName = it.addonName ?: "Bilinmeyen Eklenti",
                name = it.name ?: "",
                title = it.title ?: ""
            )
        }

        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, -1)
        val malId = intent.getIntExtra(EXTRA_MAL_ID, -1).takeIf { it != -1 }
        val aniListId = intent.getIntExtra(EXTRA_ANILIST_ID, -1).takeIf { it != -1 }
        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it != -1 }
        val season = intent.getIntExtra(EXTRA_SEASON, 1)
        val episode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""
        val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL)
        val titleEnglish = intent.getStringExtra(EXTRA_TITLE_ENGLISH)
        val titleRomaji = intent.getStringExtra(EXTRA_TITLE_ROMAJI)
        val titleNative = intent.getStringExtra(EXTRA_TITLE_NATIVE)
        val synonyms = intent.getStringArrayListExtra(EXTRA_SYNONYMS)?.filter { it.isNotBlank() }.orEmpty()
        val startYear = intent.getIntExtra(EXTRA_START_YEAR, -1).takeIf { it != -1 }
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        val cs3Url = intent.getStringExtra(EXTRA_CS3_URL)
        val cs3ApiName = intent.getStringExtra(EXTRA_CS3_API_NAME)
        val resumePosition = intent.getLongExtra(EXTRA_RESUME_POSITION, 0L)

        val castList: List<MetaCastMember> = tempCast ?: run {
            val castJson = intent.getStringExtra(EXTRA_CAST_JSON)
            if (!castJson.isNullOrEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<MetaCastMember>>() {}.type
                    com.google.gson.Gson().fromJson(castJson, type)
                } catch (_: Exception) { emptyList() }
            } else emptyList()
        }
        tempCast = null

        lifecycleScope.launch {
            SettingsDataStore(applicationContext).settingsFlow.collectLatest { settings ->
                isPipEnabled = settings.pipEnabled
            }
        }

        lifecycleScope.launch {
            viewModel.subtitleEvents.collectLatest { event ->
                if (event is KitsugiPlayerViewModel.SubtitleEvent.LoadFailed) {
                    Toast.makeText(
                        this@KitsugiFullscreenPlayerActivity,
                        "Eklentilerden altyaz\u0131 bulunamad\u0131. L\u00fctfen dahili altyaz\u0131lar\u0131 veya dosya se\u00e7iciyi kontrol edin.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // T2.3: MediaSession başlat
        mediaSessionHelper = PlayerMediaSessionHelper(
            context = this,
            title = animeTitle.ifBlank { title },
            onPlay = { invokePipAction { it.onPipPlay() } },
            onPause = { invokePipAction { it.onPipPause() } },
            onSkipNext = { invokePipAction { it.onPipSkipNext() } }
        ).also { helper ->
            helper.setMetadata(
                title = animeTitle.ifBlank { title },
                subtitle = if (episode > 0) "Bölüm $episode" else "",
                durationMs = 0L
            )
            helper.updatePlaybackState(isPlaying = true, positionMs = 0L, hasNext = false)
        }

        setContent {
            // Oynatıcı, ana uygulamayla AYNI tema ayarlarını kullanmalı (seçili tema rengi, AMOLED, tema modu).
            // Aksi halde KitsugiAnimeListTheme varsayılan (mint) vurgu rengiyle açılıyordu.
            val playerSettingsStore = remember { SettingsDataStore(applicationContext) }
            val playerSettings by playerSettingsStore.settingsFlow.collectAsState(
                initial = com.kitsugi.animelist.data.settings.AppSettings()
            )
            val playerDarkTheme = when (playerSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            KitsugiAnimeListTheme(
                darkTheme = playerDarkTheme,
                amoledBlack = playerSettings.amoledBlack,
                selectedThemeId = playerSettings.selectedThemeId,
                customAccentColor = playerSettings.customAccentColor,
            ) {
              PlayerAccentTheme {
                KitsugiFullscreenPlayerScreen(
                    videoId          = videoId,
                    videoUrl         = videoUrl,
                    audioUrl         = audioUrl,
                    title            = title,
                    requestHeaders   = requestHeaders,
                    initialSubtitles = initialSubtitles,
                    streamSources    = streamSources,
                    initialIndex     = currentIndex,
                    malId            = malId,
                    aniListId        = aniListId,
                    tmdbId           = tmdbId,
                    season           = season,
                    episode          = episode,
                    animeTitle       = animeTitle,
                    posterUrl        = posterUrl,
                    titleEnglish     = titleEnglish,
                    titleRomaji      = titleRomaji,
                    titleNative      = titleNative,
                    synonyms         = synonyms,
                    startYear        = startYear,
                    description      = description,
                    castList         = castList,
                    isMovie          = isMovie,
                    cs3Url           = cs3Url,
                    cs3ApiName       = cs3ApiName,
                    resumePosition   = resumePosition,
                    onBack           = { finish() }
                )
              }
            }
        }
    }

    /**
     * Hızlı ardışık bölüm açma/kapama senaryolarında Android bazen henüz RESUME
     * olmamış bir Activity'ye PAUSE transaction gönderir. onNewIntent override'ı
     * intent'i günceller ve bu geçiş hatalarını engeller.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipEnabled) {
            PlayerPipHelper.enterPipSafe(
                this,
                null,
                isPlaying = lastKnownIsPlaying,
                hasNext = lastKnownHasNext
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isInPipNow
        isInPipNow = isInPictureInPictureMode
        PlayerPipHelper.onPipModeChanged(isInPictureInPictureMode) { /* Screen observes via ViewModel */ }

        // Mini (PiP) penceresi çarpıya sürüklenip kapatıldığında veya "tam ekrana dön"
        // sonrası sistem Activity'yi bitirmeye karar verdiğinde: oynatma derhal durdurulur.
        // Eski davranışta Activity arka planda yaşamaya devam ediyor, ses ise çalmayı
        // sürdürüyordu (kullanıcının "arka plandan sesi gelmeye devam ediyor" şikâyeti).
        if (wasInPip && !isInPictureInPictureMode && isFinishing) {
            stopPlaybackEverywhere()
        }
    }

    /**
     * Activity görünmez olduğunda (PiP penceresi kapatıldı, uygulama arka plana alındı,
     * ekran kilitlendi vb.) oynatmayı durdurur. Aksi halde video yüzeyi kaybolsa bile ses
     * arka planda çalmaya devam ediyordu.
     */
    override fun onStop() {
        super.onStop()
        if (!isInPipNow && !isInPictureInPictureMode && !isChangingConfigurations) {
            stopPlaybackEverywhere()
            // PiP'te değilken görünmez olduysak (mini pencere kapatıldı) uygulama "arka plan
            // oynatıcısı" gibi davranmamalı: keep-alive servisi de kapatılır, böylece medya
            // bildirimi üzerinden sesin devam etmesi mümkün olmaz.
            runCatching {
                com.kitsugi.animelist.core.player.KeepAliveService.stop(this)
            }
            // Mini pencere kapatıldıktan sonra Activity arka planda hayalet olarak kalmasın.
            // Harici oynatıcıya devredildiysek veya üzerimizde başka bir görev varsa dokunmayız.
            if (!handedOffToExternalPlayer && !isFinishing && isTaskRoot) {
                finish()
            }
        }
    }

    /**
     * T2.3: MediaSession metadata/state güncellemesi — ViewModel veya Screen tarafından çağrılır.
     */
    fun updateMediaSession(
        title: String,
        episode: Int,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        hasNext: Boolean = false
    ) {
        lastKnownIsPlaying = isPlaying
        lastKnownHasNext = hasNext
        val helper = mediaSessionHelper ?: return
        helper.setMetadata(title = title, subtitle = "Bölüm $episode", durationMs = durationMs)
        helper.updatePlaybackState(isPlaying = isPlaying, positionMs = positionMs, hasNext = hasNext)
        if (isPipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PlayerPipHelper.updatePipActions(this, null, isPlaying, hasNext)
        }
    }

    override fun onDestroy() {
        // Activity tamamen kapanıyorsa (ekran döndürme hariç) ses kesin olarak durmalı.
        // Kaynakların serbest bırakılması Compose tarafındaki DisposableEffect'te yapılır;
        // burada yalnızca durdurma yapıyoruz ki kayıt (progress) işlemi bozulmasın.
        if (!isChangingConfigurations) {
            // Köprü çoktan sökülmüş olsa bile ses kesin olarak durdurulur.
            runCatching { stopPlaybackEverywhere() }
        }
        pipPlayerCallback = null
        super.onDestroy()
        tempStreamSources = null
        tempCast = null
        tempSubtitles = null
        // T2.3: MediaSession temizliği
        mediaSessionHelper?.release()
        mediaSessionHelper = null
        unregisterReceiver(pipBroadcastReceiver)
        // T1.8: KeepAliveService stop
        com.kitsugi.animelist.core.player.KeepAliveService.stop(this)
    }
}

package com.kitsugi.animelist.ui.screens.detail
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.util.Log
import com.kitsugi.animelist.ui.components.KitsugiDetailReveal
import com.kitsugi.animelist.ui.components.animateDetailContentSize
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.kitsugi.animelist.ui.screens.detail.components.SharedTrailerOverlay
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.core.player.engine.PlayerEngine
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import com.kitsugi.animelist.core.player.engine.Media3PlayerEngine
import com.kitsugi.animelist.core.player.engine.MpvPlayerEngine
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings


// ---------------------------------------------------------------------------
//  Fragman Kartı (tıklanınca inline player açılır; tam ekran butonu ile overlay)
// ---------------------------------------------------------------------------
@Composable
fun KitsugiTrailerCard(trailerUrl: String, mediaTitle: String = "") {
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current
    val videoId = remember(trailerUrl) { extractYouTubeId(trailerUrl) }
    var showPlayer by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(false) }

    // Full-screen overlay
    if (showOverlay) {
        SharedTrailerOverlay(
            title = if (mediaTitle.isNotBlank()) "$mediaTitle — Fragman" else "Fragman",
            trailerUrl = trailerUrl,
            onDismiss = { showOverlay = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_preview_c9d9dd2),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        val isPlayerOpen = showPlayer && videoId != null

        // Fragman satırı — oynatıcı açıkken de üstte görünür kalır
        // (Açılış/Kapanış müziklerindeki aktif satır kartıyla aynı davranış ve görünüm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isPlayerOpen) accentColor.copy(alpha = 0.15f)
                    else accentColor.copy(alpha = 0.12f)
                )
                .tvClickable(shape = RoundedCornerShape(16.dp)) {
                    if (videoId != null) {
                        showPlayer = !showPlayer
                    } else {
                        runCatching { uriHandler.openUri(trailerUrl) }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayerOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlayerOpen) stringResource(R.string.settings_close) else "Oynat",
                    tint = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.inline_watch_trailer_8b06108),
                    color = if (isPlayerOpen) accentColor else KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (videoId != null) stringResource(R.string.ui_play_in_app) else stringResource(R.string.youtube_da_ac),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        KitsugiDetailReveal(visible = isPlayerOpen) {
            Column(modifier = Modifier.fillMaxWidth()) {
                videoId?.let { selectedVideoId ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        KitsugiYouTubePlayer(
                            videoId = selectedVideoId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Kapat butonu da aynı geçişin parçası olsun; satır ayrı ayrı pat diye belirmez.
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .tvClickable(shape = RoundedCornerShape(12.dp)) { showPlayer = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.kapat),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Anime Temaları & TMDB Müzik Videoları / Fragmanlar Listesi
// ---------------------------------------------------------------------------
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ThemePlayerContainer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current

    var isPlayerError by remember(url) { mutableStateOf(false) }
    var isPlayerLoading by remember(url) { mutableStateOf(true) }
    var retryTrigger by remember(url) { mutableStateOf(0) }

    val videoId = remember(url) { extractYouTubeId(url) }

    if (videoId != null) {
        KitsugiYouTubePlayer(videoId = videoId, modifier = modifier)
    } else {
        val dataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext) }
        val appSettingsState = dataStore.settingsFlow.collectAsState(initial = null)
        val appSettings = appSettingsState.value
        val safeSettings = remember(appSettings) { appSettings ?: com.kitsugi.animelist.data.settings.AppSettings() }

        // Honor the normal player preference first, then try the other built-in engine once
        // on an explicit error or a startup that never reaches READY.
        val preferredEngineType = if (appSettings?.playerPreference.equals("MPV", ignoreCase = true)) {
            PlayerEngineType.MPV
        } else {
            PlayerEngineType.MEDIA3
        }
        var activeEngineType by remember(url, appSettings?.playerPreference) {
            mutableStateOf(preferredEngineType)
        }
        var didTryAlternateEngine by remember(url, appSettings?.playerPreference) { mutableStateOf(false) }
        val playerEngine = remember(context, activeEngineType, url) {
            when (activeEngineType) {
                PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
                else -> Media3PlayerEngine(context, safeSettings)
            }
        }

        var isPlayingState by remember(url) { mutableStateOf(false) }
        var currentPosition by remember(url) { mutableStateOf(0L) }
        var duration by remember(url) { mutableStateOf(0L) }
        var playerState by remember(playerEngine) { mutableStateOf(PlayerEngine.State.IDLE) }
        val engineTypeForPlayer = activeEngineType
        val resumedState = rememberInlinePlayerResumed()

        fun switchToAlternateEngine(failedEngine: PlayerEngineType, reason: String) {
            if (failedEngine != activeEngineType) return
            if (!didTryAlternateEngine) {
                didTryAlternateEngine = true
                val alternate = failedEngine.alternateInlineEngine()
                Log.w("ThemePlayerContainer", "$failedEngine did not start ($reason); trying $alternate")
                activeEngineType = alternate
            } else {
                Log.e("ThemePlayerContainer", "Both inline engines failed for $url ($reason)")
                isPlayerLoading = false
                isPlayerError = true
            }
        }

        DisposableEffect(playerEngine) {
            var released = false
            val listener = object : PlayerEngine.Listener {
                override fun onStateChanged(state: PlayerEngine.State) {
                    if (released) return
                    playerState = state
                    isPlayingState = playerEngine.isPlaying
                    isPlayerLoading = state == PlayerEngine.State.BUFFERING
                }

                override fun onPlaybackError(errorCode: Int, errorMsg: String, cause: Throwable?) {
                    if (released) return
                    isPlayerLoading = false
                    Log.e("ThemePlayerContainer", "Oynatma hatası: $errorMsg (kod: $errorCode)")
                    switchToAlternateEngine(engineTypeForPlayer, errorMsg)
                }

                override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                    if (released) return
                    currentPosition = positionMs
                    duration = durationMs
                }

                override fun onTracksChanged(
                    audioTracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>,
                    subtitleTracks: List<com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption>
                ) {}
            }
            playerEngine.addListener(listener)
            onDispose {
                released = true
                playerEngine.removeListener(listener)
                runCatching { playerEngine.pause() }
                playerEngine.release()
            }
        }

        LaunchedEffect(resumedState.value, playerEngine) {
            if (!resumedState.value) {
                runCatching { playerEngine.pause() }
                isPlayingState = false
            }
        }

        LaunchedEffect(url, retryTrigger, activeEngineType) {
            val attemptedEngine = engineTypeForPlayer
            isPlayerError = false
            isPlayerLoading = true
            playerState = PlayerEngine.State.BUFFERING
            try {
                val reachedReady = withTimeoutOrNull(INLINE_PLAYER_START_TIMEOUT_MS) {
                    playerEngine.prepare(
                        videoUrl = url,
                        headers = YouTubeRequestPolicy.headersForInlineUrl(url),
                        startPositionMs = currentPosition
                    )
                    if (!resumedState.value) {
                        runCatching { playerEngine.pause() }
                        isPlayingState = false
                        false
                    } else {
                        playerEngine.play()
                        // READY is only a preparation signal. Fall back if playback never
                        // advances instead of leaving the compact surface black at 00:00.
                        while (!hasInlinePlaybackProgress(playerEngine.currentState, playerEngine.currentPosition)) {
                            if (!resumedState.value) return@withTimeoutOrNull false
                            delay(250L)
                        }
                        true
                    }
                }
                if (reachedReady == null) {
                    switchToAlternateEngine(attemptedEngine, "no playback progress within ${INLINE_PLAYER_START_TIMEOUT_MS}ms")
                }
            } catch (e: CancellationException) {
                // Closing or replacing this row cancels prepare; do not trigger a false fallback.
                throw e
            } catch (e: Exception) {
                if (attemptedEngine != activeEngineType) return@LaunchedEffect
                Log.e("ThemePlayerContainer", "Prepare error", e)
                switchToAlternateEngine(attemptedEngine, e.message ?: e.javaClass.simpleName)
            }
        }

        LaunchedEffect(playerEngine, isPlayingState) {
            if (isPlayingState) {
                while (true) {
                    currentPosition = playerEngine.currentPosition
                    duration = playerEngine.duration.coerceAtLeast(0L)
                    delay(1000)
                }
            }
        }

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isPlayerError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = KitsugiStrings.get(R.string.inline_video_couldn_t_be_played_8d8f91f),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = KitsugiStrings.get(R.string.inline_press_to_try_again_45075c9),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KitsugiButton(
                            onClick = {
                                didTryAlternateEngine = false
                                activeEngineType = preferredEngineType
                                retryTrigger++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Tekrar Dene", color = com.kitsugi.animelist.ui.theme.onAccentColor(accentColor))
                        }
                        KitsugiButton(
                            onClick = {
                                val target = if (videoId != null) {
                                    "https://www.youtube.com/watch?v=$videoId"
                                } else {
                                    url
                                }
                                runCatching { uriHandler.openUri(target) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Text(if (videoId != null) "YouTube" else "Kaynağı Aç", color = Color.White)
                        }
                    }
                }
            } else {
                KitsugiPlayerGestureWrapper(
                    isPlaying = isPlayingState,
                    currentPosition = currentPosition,
                    duration = duration,
                    onPlayPause = {
                        if (isPlayingState) playerEngine.pause() else playerEngine.play()
                    },
                    onSeek = { target ->
                        playerEngine.seekTo(target)
                    },
                    modifier = Modifier.fillMaxSize(),
                    onFullscreen = {
                        playerEngine.pause()
                        KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                            context = context,
                            videoUrl = url,
                            headers = YouTubeRequestPolicy.headersForInlineUrl(url)
                        )
                    }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            playerEngine.createVideoView(ctx)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                if (isPlayerLoading) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
        }
    }
}

private data class DetailVideoItem(
    val key: String,
    val title: String,
    val videoUrl: String?
)

/**
 * Video bağlantısını paylaşır. Oynatılabilir bağlantı yoksa (YouTube araması gerekiyorsa)
 * arama bağlantısı paylaşılır; alıcı her zaman açılabilir bir adres görür.
 */
private fun shareDetailVideo(context: android.content.Context, mediaTitle: String, item: DetailVideoItem) {
    val url = item.videoUrl?.takeIf { it.isNotBlank() }
        ?: "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(item.title, "UTF-8")}"
    val title = if (mediaTitle.isBlank()) item.title else "$mediaTitle · ${item.title}"
    com.kitsugi.animelist.utils.ShareUtils.shareText(context, title, url)
}

/**
 * Dedicated Videos tab shared by API-backed and list-backed media detail pages.
 * Exactly one selected item owns the inline player; changing or closing a row removes
 * the previous player immediately instead of keeping it alive for an exit animation.
 */
@Composable
fun KitsugiMediaVideosTab(
    detail: com.kitsugi.animelist.data.remote.KitsugiMediaDetail?,
    /** Paylaşımda video başlığının önüne eklenen yapım adı (boşsa yalnızca video adı). */
    mediaTitle: String = ""
) {
    val trailerUrl = detail?.trailerUrl?.takeIf { it.isNotBlank() }
    val shareContext = LocalContext.current
    val onShareVideo: (DetailVideoItem) -> Unit = { item ->
        shareDetailVideo(shareContext, mediaTitle, item)
    }
    val openingItems = remember(detail?.openings) {
        detail?.openings.orEmpty().mapIndexed { index, theme ->
            DetailVideoItem("opening-$index", theme.label, theme.videoUrl)
        }
    }
    val endingItems = remember(detail?.endings) {
        detail?.endings.orEmpty().mapIndexed { index, theme ->
            DetailVideoItem("ending-$index", theme.label, theme.videoUrl)
        }
    }
    val uriHandler = LocalUriHandler.current
    val accentColor = LocalKitsugiAccent.current
    var activeVideoKey by remember(trailerUrl, openingItems, endingItems) { mutableStateOf<String?>(null) }

    val hasVideos = trailerUrl != null || openingItems.isNotEmpty() || endingItems.isNotEmpty()
    Column(
        modifier = Modifier.padding(horizontal = 0.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasVideos) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(KitsugiColors.Surface)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.detail_video_empty),
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            if (trailerUrl != null) {
                DetailVideoGroup(
                    title = stringResource(R.string.inline_preview_c9d9dd2),
                    items = listOf(
                        DetailVideoItem(
                            key = "trailer",
                            title = stringResource(R.string.inline_watch_trailer_8b06108),
                            videoUrl = trailerUrl
                        )
                    ),
                    activeVideoKey = activeVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = { key ->
                        activeVideoKey = if (activeVideoKey == key) null else key
                    },
                    onSearch = { item ->
                        val query = java.net.URLEncoder.encode(item.title, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    },
                    onShare = onShareVideo
                )
            }

            if (openingItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.inline_opening_ending_music_videos_6b5701d),
                    items = openingItems,
                    activeVideoKey = activeVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = { key ->
                        activeVideoKey = if (activeVideoKey == key) null else key
                    },
                    onSearch = { item ->
                        val query = java.net.URLEncoder.encode(item.title, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    },
                    onCollapseHiddenItems = { hiddenKeys ->
                        if (activeVideoKey?.let { it in hiddenKeys } == true) activeVideoKey = null
                    },
                    onShare = onShareVideo
                )
            }

            if (endingItems.isNotEmpty()) {
                DetailVideoGroup(
                    title = stringResource(R.string.kapanis_muzikleri),
                    items = endingItems,
                    activeVideoKey = activeVideoKey,
                    accentColor = accentColor,
                    onVideoSelected = { key ->
                        activeVideoKey = if (activeVideoKey == key) null else key
                    },
                    onSearch = { item ->
                        val query = java.net.URLEncoder.encode(item.title, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    },
                    onCollapseHiddenItems = { hiddenKeys ->
                        if (activeVideoKey?.let { it in hiddenKeys } == true) activeVideoKey = null
                    },
                    onShare = onShareVideo
                )
            }
        }
    }
}

@Composable
private fun DetailVideoGroup(
    title: String,
    items: List<DetailVideoItem>,
    activeVideoKey: String?,
    accentColor: Color,
    onVideoSelected: (String) -> Unit,
    onSearch: (DetailVideoItem) -> Unit,
    onCollapseHiddenItems: (Set<String>) -> Unit = {},
    onShare: (DetailVideoItem) -> Unit = {}
) {
    val initialCount = 3
    var expanded by remember(items) { mutableStateOf(false) }
    val hasMore = items.size > initialCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (hasMore) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) {
                            if (expanded) {
                                onCollapseHiddenItems(items.drop(initialCount).map { it.key }.toSet())
                            }
                            expanded = !expanded
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.daha_az)
                        else stringResource(R.string.x_daha, items.size - initialCount),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        items.forEachIndexed { index, item ->
            key(item.key) {
                KitsugiDetailReveal(visible = expanded || index < initialCount) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Keep the current 10dp rhythm while allowing hidden items to occupy no space.
                        Spacer(modifier = Modifier.height(10.dp))
                        val isSelected = activeVideoKey == item.key && !item.videoUrl.isNullOrBlank()

                        // Animate the row's measured height, but dispose the player immediately on
                        // close/switch so two heavyweight video engines are never kept alive together.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                                .animateDetailContentSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceSoft)
                                    .padding(end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Oynat / kapat alanı: satırın asıl dokunma bölgesi.
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                            if (item.videoUrl.isNullOrBlank()) onSearch(item)
                                            else onVideoSelected(item.key)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) accentColor else accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isSelected) KitsugiColors.Background else accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = if (item.videoUrl.isNullOrBlank()) stringResource(R.string.ui_open_youtube_search)
                                            else stringResource(R.string.ui_play_in_app),
                                            color = KitsugiColors.TextMuted,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                // Video bağlantısını paylaş (resim ve detay sayfası paylaşımıyla aynı akış).
                                IconButton(onClick = { onShare(item) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = stringResource(R.string.action_share),
                                        tint = KitsugiColors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Keep one player instance in the composition and remove it immediately on close/switch.
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                key(item.key) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.Black)
                                    ) {
                                        ThemePlayerContainer(
                                            url = item.videoUrl!!,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .tvClickable(shape = RoundedCornerShape(12.dp)) { onVideoSelected(item.key) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = KitsugiColors.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.kapat),
                                        color = KitsugiColors.TextSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KitsugiThemesList(
    openings: List<com.kitsugi.animelist.data.remote.KitsugiTheme>,
    endings: List<com.kitsugi.animelist.data.remote.KitsugiTheme>
) {
    if (openings.isEmpty() && endings.isEmpty()) return

    val accentColor = LocalKitsugiAccent.current
    val uriHandler = LocalUriHandler.current
    var activeVideoUrl by remember { mutableStateOf<String?>(null) }
    var openingsExpanded by remember { mutableStateOf(false) }
    var endingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(16.dp)
    ) {
        if (openings.isNotEmpty()) {
            ThemeVideoSection(
                title = stringResource(R.string.inline_opening_ending_music_videos_6b5701d),
                themes = openings,
                expanded = openingsExpanded,
                activeVideoUrl = activeVideoUrl,
                accentColor = accentColor,
                onExpandedChange = { openingsExpanded = it },
                onThemeClick = { theme ->
                    if (theme.videoUrl != null) {
                        activeVideoUrl = if (activeVideoUrl == theme.videoUrl) null else theme.videoUrl
                    } else {
                        val query = java.net.URLEncoder.encode(theme.label, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    }
                },
                onClosePlayer = { activeVideoUrl = null }
            )
        }

        if (endings.isNotEmpty()) {
            if (openings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = KitsugiColors.Background.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            ThemeVideoSection(
                title = stringResource(R.string.kapanis_muzikleri),
                themes = endings,
                expanded = endingsExpanded,
                activeVideoUrl = activeVideoUrl,
                accentColor = accentColor,
                onExpandedChange = { endingsExpanded = it },
                onThemeClick = { theme ->
                    if (theme.videoUrl != null) {
                        activeVideoUrl = if (activeVideoUrl == theme.videoUrl) null else theme.videoUrl
                    } else {
                        val query = java.net.URLEncoder.encode(theme.label, "UTF-8")
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    }
                },
                onClosePlayer = { activeVideoUrl = null }
            )
        }
    }
}

@Composable
private fun ThemeVideoSection(
    title: String,
    themes: List<com.kitsugi.animelist.data.remote.KitsugiTheme>,
    expanded: Boolean,
    activeVideoUrl: String?,
    accentColor: Color,
    onExpandedChange: (Boolean) -> Unit,
    onThemeClick: (com.kitsugi.animelist.data.remote.KitsugiTheme) -> Unit,
    onClosePlayer: () -> Unit
) {
    val initialCount = 3
    val hasMore = themes.size > initialCount

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (hasMore) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .tvClickable(shape = RoundedCornerShape(8.dp)) { onExpandedChange(!expanded) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.daha_az)
                        else stringResource(R.string.x_daha, themes.size - initialCount),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        themes.forEachIndexed { index, theme ->
            key("${title}_${index}_${theme.label}") {
                KitsugiDetailReveal(visible = expanded || index < initialCount) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ThemeRow(
                            theme = theme,
                            isActive = activeVideoUrl == theme.videoUrl && activeVideoUrl != null,
                            accentColor = accentColor,
                            onClick = { onThemeClick(theme) }
                        )
                        KitsugiDetailReveal(
                            visible = activeVideoUrl == theme.videoUrl && activeVideoUrl != null
                        ) {
                            theme.videoUrl?.let { url ->
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    ThemePlayerContainer(
                                        url = url,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onClosePlayer)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = KitsugiColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.kapat),
                                            color = KitsugiColors.TextSecondary,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: com.kitsugi.animelist.data.remote.KitsugiTheme,
    isActive: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.15f) else KitsugiColors.SurfaceSoft)
            .tvClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isActive) accentColor else accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (isActive) KitsugiColors.Background else accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.label,
                color = if (isActive) accentColor else KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = if (theme.videoUrl != null) stringResource(R.string.ui_play_in_app) else stringResource(R.string.ui_open_youtube_search),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

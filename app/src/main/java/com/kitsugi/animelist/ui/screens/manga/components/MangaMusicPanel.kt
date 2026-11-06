package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.music.MangaMusicCatalog
import com.kitsugi.animelist.core.music.MangaMusicMood
import com.kitsugi.animelist.core.music.MangaMusicPlayer
import com.kitsugi.animelist.core.music.MangaMusicSettings
import com.kitsugi.animelist.core.music.MangaMusicSettingsStore
import com.kitsugi.animelist.core.music.MangaMusicSource
import com.kitsugi.animelist.core.music.MusicStation
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.launch

/**
 * Manga okuyucusunun "Okuma Müziği" paneli.
 *
 * Tek bir yerde toplanır: açma/kapama, kaynak (Radyo API / dahili liste), ruh hâli
 * etiketi, istasyon seçimi, oynat/duraklat, sonraki/önceki ve ses seviyesi.
 * Tüm durum [MangaMusicPlayer] (çalma) ve [MangaMusicSettingsStore] (kalıcı ayar)
 * üzerinden yürür; panel kapansa da müzik çalmaya devam eder ve bildirim panelinden
 * kontrol edilebilir.
 */
@Composable
fun MangaMusicPanel(
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    /** Panel ilk açıldığında etkin ayar varsa çalmayı kendiliğinden başlatır. */
    autoStartWhenEnabled: Boolean = true,
    onOpenIntegrations: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { MangaMusicSettingsStore(context.applicationContext) }
    val settings by store.settingsFlow.collectAsState(initial = MangaMusicSettings())
    val playback by MangaMusicPlayer.state.collectAsState()

    var stations by remember { mutableStateOf<List<MusicStation>>(emptyList()) }
    var loadingStations by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var usedFallback by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var autoStartDone by remember { mutableStateOf(false) }

    // İstasyon listesi: ruh hâli / kaynak değişince (veya "Yenile") yeniden yüklenir.
    LaunchedEffect(settings.mood, settings.source, refreshToken) {
        if (settings.source == MangaMusicSource.OFF) {
            stations = emptyList()
            return@LaunchedEffect
        }
        loadingStations = true
        val result = MangaMusicCatalog.loadStations(
            mood = settings.mood,
            source = settings.source,
            forceRefresh = refreshToken > 0
        )
        stations = result.stations
        usedFallback = result.usedFallback
        loadError = result.error
        loadingStations = false

        // Kullanıcı açıkça bir istasyon seçmediyse ilk uygun istasyonu hazırla.
        if (autoStartWhenEnabled && settings.enabled && !autoStartDone &&
            !playback.isPlaying && result.stations.isNotEmpty()
        ) {
            autoStartDone = true
            MangaMusicPlayer.setVolume(settings.volume)
            MangaMusicPlayer.play(context, result.stations.first(), result.stations)
        }
    }

    val moodLabels = listOf(
        MangaMusicMood.LO_FI to stringResource(R.string.music_mood_lofi),
        MangaMusicMood.CLASSICAL to stringResource(R.string.music_mood_classical),
        MangaMusicMood.JAZZ to stringResource(R.string.music_mood_jazz),
        MangaMusicMood.AMBIENT to stringResource(R.string.music_mood_ambient)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Başlık + ana anahtar ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.music_reading_title),
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = playback.station?.name ?: stringResource(R.string.music_station_none),
                    color = contentColor.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        store.setEnabled(enabled)
                        if (enabled) {
                            val station = stations.firstOrNull()
                                ?: MangaMusicCatalog.builtInFor(settings.mood).firstOrNull()
                            if (station != null) {
                                MangaMusicPlayer.setVolume(settings.volume)
                                MangaMusicPlayer.play(
                                    context = context,
                                    station = station,
                                    stations = stations.ifEmpty { MangaMusicCatalog.builtInFor(settings.mood) }
                                )
                            }
                        } else {
                            MangaMusicPlayer.stop()
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accentColor,
                    checkedTrackColor = accentColor.copy(alpha = 0.35f)
                )
            )
        }

        if (!settings.enabled) {
            Text(
                text = stringResource(R.string.music_reading_desc),
                color = contentColor.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            return@Column
        }

        Spacer(Modifier.height(12.dp))

        // ── Ruh hâli ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            moodLabels.forEach { (mood, label) ->
                FilterChip(
                    selected = MangaMusicMood.fromTag(settings.mood) == mood,
                    onClick = { scope.launch { store.setMood(mood) } },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.24f),
                        selectedLabelColor = accentColor,
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = contentColor.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Oynatma denetimleri ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KitsugiColors.SurfaceSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = { MangaMusicPlayer.previous() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.music_prev),
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = {
                    if (stations.isNotEmpty()) {
                        MangaMusicPlayer.toggle(context, stations.first())
                    } else {
                        MangaMusicPlayer.toggle(
                            context,
                            MangaMusicCatalog.builtInFor(settings.mood).firstOrNull()
                        )
                    }
                }) {
                    when {
                        playback.isLoading -> CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        playback.isPlaying -> Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = stringResource(R.string.music_pause),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.music_play),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            IconButton(onClick = { MangaMusicPlayer.next() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.music_next),
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playback.station?.name ?: stringResource(R.string.music_station_none),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val providerText = playback.station?.let { station ->
                    station.providerLabel.ifBlank { context.getString(R.string.music_source_radio) }
                } ?: context.getString(R.string.music_hint_select)
                Text(
                    text = providerText,
                    color = contentColor.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            IconButton(onClick = { refreshToken++ }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.music_refresh),
                    tint = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Ses seviyesi ─────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.music_volume),
                color = contentColor.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Slider(
                value = settings.volume,
                onValueChange = { value ->
                    MangaMusicPlayer.setVolume(value)
                    scope.launch { store.setVolume(value) }
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = KitsugiColors.SurfaceSoft
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = "${(settings.volume * 100).toInt()}%",
                color = contentColor.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }

        // ── Kaynak seçimi ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                MangaMusicSource.RADIO_BROWSER to stringResource(R.string.music_source_radio),
                MangaMusicSource.BUILT_IN to stringResource(R.string.music_source_builtin)
            ).forEach { (source, label) ->
                FilterChip(
                    selected = settings.source == source,
                    onClick = { scope.launch { store.setSource(source) } },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.24f),
                        selectedLabelColor = accentColor,
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = contentColor.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── İstasyon listesi ─────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.music_stations),
            color = contentColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))

        when {
            loadingStations -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.music_loading),
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            stations.isEmpty() -> Text(
                text = stringResource(R.string.music_station_empty),
                color = contentColor.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stations.take(MAX_VISIBLE_STATIONS).forEach { station ->
                    val isActive = playback.station?.id == station.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) accentColor.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActive && playback.isPlaying) Icons.Rounded.MusicNote
                            else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (isActive) accentColor else contentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = station.name,
                            color = if (isActive) accentColor else contentColor,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = buildString {
                                if (station.bitrate > 0) append("${station.bitrate} kbps")
                                if (station.country.isNotBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(station.country)
                                }
                            },
                            color = contentColor.copy(alpha = 0.45f),
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        androidx.compose.material3.TextButton(
                            onClick = {
                                MangaMusicPlayer.setVolume(settings.volume)
                                MangaMusicPlayer.play(context, station, stations)
                                scope.launch { store.setLastStation(station) }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.music_play),
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── Alt bilgi: kaynak / hata / entegrasyon kısayolu ──────────────────
        Spacer(Modifier.height(6.dp))
        val statusText = when {
            playback.error != null -> stringResource(R.string.music_error_prefix, playback.error!!)
            loadError != null -> stringResource(R.string.music_error_prefix, loadError!!)
            usedFallback -> stringResource(R.string.music_fallback_notice)
            settings.source == MangaMusicSource.RADIO_BROWSER -> stringResource(R.string.music_source_status_radio)
            else -> stringResource(R.string.music_source_status_builtin)
        }
        Text(
            text = statusText,
            color = if (playback.error != null || loadError != null) Color(0xFFFF6B6B)
            else contentColor.copy(alpha = 0.45f),
            fontSize = 10.sp
        )

        if (onOpenIntegrations != null) {
            androidx.compose.material3.TextButton(
                onClick = onOpenIntegrations,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.music_open_integrations),
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private const val MAX_VISIBLE_STATIONS = 6

/**
 * Okuyucu üst çubuğu için tek dokunuşluk müzik düğmesi: mevcut yayını
 * duraklatır/devam ettirir, hiç yayın yoksa etkin ruh hâlinin ilk istasyonunu açar.
 */
@Composable
fun rememberMangaMusicQuickToggle(): MangaMusicQuickToggle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { MangaMusicSettingsStore(context.applicationContext) }
    val settings by store.settingsFlow.collectAsState(initial = MangaMusicSettings())
    val playback by MangaMusicPlayer.state.collectAsState()

    return remember(settings, playback) {
        MangaMusicQuickToggle(
            isPlaying = playback.isPlaying,
            enabled = settings.enabled,
            onToggle = {
                scope.launch {
                    if (!settings.enabled) store.setEnabled(true)
                    MangaMusicPlayer.setVolume(settings.volume)
                    val fallback = MangaMusicCatalog.builtInFor(settings.mood).firstOrNull()
                    MangaMusicPlayer.toggle(context, fallback)
                }
            }
        )
    }
}

data class MangaMusicQuickToggle(
    val isPlaying: Boolean,
    val enabled: Boolean,
    val onToggle: () -> Unit
)

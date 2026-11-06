package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.music.MangaMusicCatalog
import com.kitsugi.animelist.core.music.MangaMusicMood
import com.kitsugi.animelist.core.music.MangaMusicPlayer
import com.kitsugi.animelist.core.music.MangaMusicSettings
import com.kitsugi.animelist.core.music.MangaMusicSettingsStore
import com.kitsugi.animelist.core.music.MangaMusicSource
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch

/**
 * Entegrasyonlar → **Müzik** sekmesi.
 *
 * Okuma müziğinin kaynağını (radio-browser.info açık API'si / uygulamaya gömülü liste),
 * ruh hâlini, ses seviyesini ve okuyucu otomatik başlatmasını yönetir; ayrıca gerçek
 * bir ağ isteğiyle "Bağlantıyı test et" sağlar. Ayar deposu burada doğrudan kullanılır:
 * ana ayar ağacına 10+ parametre eklemek yerine sekme kendi durumunu yönetir.
 */
@Composable
fun MusicIntegrationTab(
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { MangaMusicSettingsStore(context.applicationContext) }
    val settings by store.settingsFlow.collectAsState(initial = MangaMusicSettings())
    val playback by MangaMusicPlayer.state.collectAsState()

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testFailed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.music_integration_section)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.music_reading_title),
                description = stringResource(R.string.music_reading_desc),
                icon = Icons.Rounded.MusicNote,
                iconColor = accentColor,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        store.setEnabled(enabled)
                        if (!enabled) MangaMusicPlayer.stop()
                    }
                }
            )

            KitsugiSettingsDivider()

            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.music_autoplay),
                description = stringResource(R.string.music_autoplay_desc),
                icon = Icons.Rounded.PlayCircle,
                iconColor = accentColor,
                checked = settings.autoplay,
                onCheckedChange = { autoplay -> scope.launch { store.setAutoplay(autoplay) } }
            )
        }

        KitsugiSettingsSection(title = stringResource(R.string.music_source_section)) {
            // Kaynak seçimi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    MangaMusicSource.RADIO_BROWSER to stringResource(R.string.music_source_radio),
                    MangaMusicSource.BUILT_IN to stringResource(R.string.music_source_builtin)
                ).forEach { (source, label) ->
                    FilterChip(
                        selected = settings.source == source,
                        onClick = { scope.launch { store.setSource(source) } },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.22f),
                            selectedLabelColor = accentColor,
                            containerColor = KitsugiColors.SurfaceSoft,
                            labelColor = KitsugiColors.TextSecondary
                        )
                    )
                }
            }

            // Ruh hâli seçimi
            Text(
                text = stringResource(R.string.music_mood_section),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    MangaMusicMood.LO_FI to stringResource(R.string.music_mood_lofi),
                    MangaMusicMood.CLASSICAL to stringResource(R.string.music_mood_classical),
                    MangaMusicMood.JAZZ to stringResource(R.string.music_mood_jazz),
                    MangaMusicMood.AMBIENT to stringResource(R.string.music_mood_ambient)
                ).forEach { (mood, label) ->
                    FilterChip(
                        selected = MangaMusicMood.fromTag(settings.mood) == mood,
                        onClick = { scope.launch { store.setMood(mood) } },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.22f),
                            selectedLabelColor = accentColor,
                            containerColor = KitsugiColors.SurfaceSoft,
                            labelColor = KitsugiColors.TextSecondary
                        )
                    )
                }
            }

            // Ses seviyesi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.music_volume),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
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
                        inactiveTrackColor = KitsugiColors.SurfaceStrong
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                Text(
                    text = "${(settings.volume * 100).toInt()}%",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            KitsugiSettingsDivider()

            // Bağlantı testi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.music_test_connection),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (testing) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    IconButton(onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = MangaMusicCatalog.probe(settings.mood, settings.source)
                            testing = false
                            testFailed = result.usedFallback || result.stations.isEmpty()
                            testResult = when {
                                result.usedFallback && result.error != null ->
                                    context.getString(R.string.music_test_fail, result.error)
                                result.usedFallback ->
                                    context.getString(R.string.music_test_source_builtin, result.stations.size)
                                result.stations.isEmpty() ->
                                    context.getString(R.string.music_station_empty)
                                else ->
                                    context.getString(R.string.music_test_ok, result.stations.size)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = stringResource(R.string.music_test_connection),
                            tint = accentColor
                        )
                    }
                }
            }

            testResult?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (testFailed) Color(0x33FF6B6B) else accentColor.copy(alpha = 0.14f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        color = if (testFailed) Color(0xFFFF8A8A) else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = stringResource(
                    if (settings.source == MangaMusicSource.BUILT_IN)
                        R.string.music_source_status_builtin
                    else R.string.music_source_status_radio
                ),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Şu an çalıyorsa kısa durum satırı
        playback.station?.let { station ->
            KitsugiSettingsSection(title = stringResource(R.string.music_stations)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.name,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (playback.isPlaying) stringResource(R.string.music_play)
                            else stringResource(R.string.music_pause),
                            color = KitsugiColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

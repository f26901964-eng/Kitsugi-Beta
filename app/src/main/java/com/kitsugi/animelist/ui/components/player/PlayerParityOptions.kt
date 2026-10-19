package com.kitsugi.animelist.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.launch

/** Real persisted options, not a second disconnected preference model. */
@Composable
fun PlayerParityOptions(settings: AppSettings, store: SettingsDataStore) {
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.player_parity_options), style = MaterialTheme.typography.titleMedium)
            SavedTextOption(stringResource(R.string.player_audio_languages), settings.preferredAudioLanguages) {
                scope.launch { store.setPreferredAudioLanguages(it) }
            }
            SavedTextOption(stringResource(R.string.player_subtitle_whitelist), settings.subtitleWhitelist) {
                scope.launch { store.setSubtitleWhitelist(it) }
            }
            SavedTextOption(stringResource(R.string.player_subtitle_blacklist), settings.subtitleBlacklist) {
                scope.launch { store.setSubtitleBlacklist(it) }
            }
            SavedTextOption(stringResource(R.string.player_subtitle_font), settings.subtitleFont) {
                scope.launch { store.setSubtitleFont(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.player_pitch_correction))
                Switch(settings.audioPitchCorrection, { scope.launch { store.setAudioPitchCorrection(it) } })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.player_ass_override))
                Switch(settings.subtitleOverrideAss, { scope.launch { store.setSubtitleOverrideAss(it) } })
            }
            var scale by remember(settings.subtitleFontScale) { mutableFloatStateOf(settings.subtitleFontScale) }
            Text(stringResource(R.string.player_font_scale, scale))
            Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.5f..3f,
                onValueChangeFinished = { scope.launch { store.setSubtitleFontScale(scale) } })
            var position by remember(settings.subtitlePos) { mutableFloatStateOf(settings.subtitlePos.toFloat()) }
            Text(stringResource(R.string.player_sub_position, position.toInt()))
            Slider(value = position, onValueChange = { position = it }, valueRange = 0f..150f,
                onValueChangeFinished = { scope.launch { store.setSubtitlePos(position.toInt()) } })
            Text(stringResource(R.string.player_border_style))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("outline-and-shadow", "opaque-box", "background-box").forEach { style ->
                    FilterChip(selected = settings.subtitleBorderStyle == style,
                        onClick = { scope.launch { store.setSubtitleBorderStyle(style) } }, label = { Text(style) })
                }
            }

            HorizontalDivider()

            // ── OSD, Görünürlük ve Hareket Seçenekleri ────────────────────────
            ParitySwitchRow("Yükleme halkasını göster", settings.showLoadingCircle) {
                scope.launch { store.setShowLoadingCircle(it) }
            }
            ParitySwitchRow("Aktif bölüm (chapter) başlığını göster", settings.showCurrentChapter) {
                scope.launch { store.setShowCurrentChapter(it) }
            }
            ParitySwitchRow("Kalan süreyi ters göster (-DK:SN)", settings.invertDurationTimer) {
                scope.launch { store.setInvertDurationTimer(it) }
            }
            ParitySwitchRow("Ses seviyesini yüzde olarak göster", settings.displayVolumeAsPercentage) {
                scope.launch { store.setDisplayVolumeAsPercentage(it) }
            }
            ParitySwitchRow("Yatay kaydırmada ilerleme çubuğunu göster", settings.showSeekBarOnGesture) {
                scope.launch { store.setShowSeekBarOnGesture(it) }
            }
            ParitySwitchRow("Oynatıcı animasyonlarını azalt (Reduce Motion)", settings.reducePlayerMotion) {
                scope.launch { store.setReducePlayerMotion(it) }
            }
            ParitySwitchRow("Sistem durum çubuğunu göster", settings.showSystemStatusBar) {
                scope.launch { store.setShowSystemStatusBar(it) }
            }
            ParitySwitchRow("Çentik (cutout) alanını kullan", settings.playerFullscreenCutout) {
                scope.launch { store.setPlayerFullscreenCutout(it) }
            }
            ParitySwitchRow("İzleme konumunu hatırla", settings.preserveWatchingPosition) {
                scope.launch { store.setPreserveWatchingPosition(it) }
            }
            ParitySwitchRow("Son parlaklık değerini hatırla", settings.rememberPlayerBrightness) {
                scope.launch { store.setRememberPlayerBrightness(it) }
            }
            ParitySwitchRow("Son ses seviyesini hatırla", settings.rememberPlayerVolume) {
                scope.launch { store.setRememberPlayerVolume(it) }
            }
            ParitySwitchRow("PiP bölüm geçiş bildirimi", settings.pipEpisodeToasts) {
                scope.launch { store.setPipEpisodeToasts(it) }
            }
            ParitySwitchRow("Çıkışta otomatik PiP (Home tuşu)", settings.pipOnExit) {
                scope.launch { store.setPipOnExit(it) }
            }

            var hideSec by remember(settings.playerTimeToDisappearMs) {
                mutableFloatStateOf((settings.playerTimeToDisappearMs / 1000f).coerceIn(1f, 15f))
            }
            Text("Kontrolleri gizleme süresi: ${hideSec.toInt()} sn")
            Slider(
                value = hideSec,
                onValueChange = { hideSec = it },
                valueRange = 1f..15f,
                steps = 13,
                onValueChangeFinished = { scope.launch { store.setPlayerTimeToDisappearMs((hideSec.toInt() * 1000)) } }
            )

            var opacity by remember(settings.panelOpacity) {
                mutableFloatStateOf(settings.panelOpacity.toFloat().coerceIn(40f, 100f))
            }
            Text("Panel / Menü opaklığı: %${opacity.toInt()}")
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = 40f..100f,
                onValueChangeFinished = { scope.launch { store.setPanelOpacity(opacity.toInt()) } }
            )

            var scrobblePct by remember(settings.scrobbleThreshold) {
                mutableFloatStateOf((settings.scrobbleThreshold * 100f).coerceIn(50f, 100f))
            }
            Text("İzlendi işaretleme eşiği (Scrobble): %${scrobblePct.toInt()}")
            Slider(
                value = scrobblePct,
                onValueChange = { scrobblePct = it },
                valueRange = 50f..100f,
                onValueChangeFinished = { scope.launch { store.setScrobbleThreshold(scrobblePct / 100f) } }
            )

            HorizontalDivider()

            // ── AniSkip & Giriş Atlama Paritesi ───────────────────────────────
            ParitySwitchRow("Giriş atlama butonunu göster", settings.enableSkipIntro) {
                scope.launch { store.setEnableSkipIntro(it) }
            }
            ParitySwitchRow("Gömülü chapter varsa AniSkip'i devre dışı bırak", settings.aniSkipDisableOnChapters) {
                scope.launch { store.setAniSkipDisableOnChapters(it) }
            }
            ParitySwitchRow("Netflix tarzı geri sayımlı otomatik atlama", settings.enableNetflixStyleIntroSkip) {
                scope.launch { store.setEnableNetflixStyleIntroSkip(it) }
            }
            if (settings.enableNetflixStyleIntroSkip) {
                var waitSec by remember(settings.waitingTimeIntroSkip) {
                    mutableFloatStateOf(settings.waitingTimeIntroSkip.toFloat().coerceIn(1f, 15f))
                }
                Text("Otomatik atlama bekleme süresi: ${waitSec.toInt()} sn")
                Slider(
                    value = waitSec,
                    onValueChange = { waitSec = it },
                    valueRange = 1f..15f,
                    steps = 13,
                    onValueChangeFinished = { scope.launch { store.setWaitingTimeIntroSkip(waitSec.toInt()) } }
                )
            }

            HorizontalDivider()

            // ── Çift Dokunma Bölgesi Özelleştirme ─────────────────────────────
            val gestureOptions = listOf(
                "SEEK" to "Sar",
                "PLAY_PAUSE" to "Oynat/Duraklat",
                "CUSTOM" to "Özel Buton",
                "SWITCH_SUB" to "Altyazı Değiştir",
                "NONE" to "Kapalı"
            )
            Text("Sol Çift Dokunma", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                gestureOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.leftDoubleTapGesture == key,
                        onClick = { scope.launch { store.setLeftDoubleTapGesture(key) } },
                        label = { Text(label) }
                    )
                }
            }
            Text("Orta Çift Dokunma", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                gestureOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.centerDoubleTapGesture == key,
                        onClick = { scope.launch { store.setCenterDoubleTapGesture(key) } },
                        label = { Text(label) }
                    )
                }
            }
            Text("Sağ Çift Dokunma", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                gestureOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = settings.rightDoubleTapGesture == key,
                        onClick = { scope.launch { store.setRightDoubleTapGesture(key) } },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParitySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SavedTextOption(label: String, value: String, onSave: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    TextButton(onClick = { onSave(draft.trim()) }, enabled = draft.trim() != value) {
        Text(stringResource(R.string.action_save))
    }
}

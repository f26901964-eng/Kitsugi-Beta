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
            listOf("outline-and-shadow", "opaque-box", "background-box").forEach { style ->
                FilterChip(selected = settings.subtitleBorderStyle == style,
                    onClick = { scope.launch { store.setSubtitleBorderStyle(style) } }, label = { Text(style) })
            }
        }
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

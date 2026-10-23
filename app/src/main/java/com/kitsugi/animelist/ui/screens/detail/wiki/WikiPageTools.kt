package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.DisableDialogWindowForceDark
import com.kitsugi.animelist.ui.theme.KitsugiColors

@Composable
internal fun WikiPageTools(
    session: WikiOriginalSession,
    preferences: WikiPagePreferences?,
    assets: WikiPageAssets?,
    config: WikiPageToolConfig,
    savingPreference: Boolean,
    preferenceFailed: Boolean,
    onSaveAppearance: (String, Int) -> Unit,
    onToggleAds: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = KitsugiColors.Surface,
        labelColor = KitsugiColors.TextPrimary,
        selectedContainerColor = KitsugiColors.AccentMuted,
        selectedLabelColor = KitsugiColors.TextPrimary,
        disabledLabelColor = KitsugiColors.TextMuted
    )
    Column {
        // Both primary actions remain visible on narrow screens; advanced tools use a menu.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.translate,
                enabled = preferences != null && assets != null,
                onClick = { session.translationOverride = !config.translate },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = chipColors,
                label = { Text(stringResource(if (config.translate) R.string.wiki_show_original else R.string.wiki_translate_page),
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
            FilterChip(
                selected = config.blockAds,
                enabled = preferences != null && !savingPreference,
                onClick = onToggleAds,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = chipColors,
                label = { Text(stringResource(if (config.blockAds) R.string.wiki_ads_on else R.string.wiki_ads_off, session.blockedRequests),
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.wiki_more_tools), tint = KitsugiColors.TextPrimary)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = KitsugiColors.Surface) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.wiki_reading_appearance), color = KitsugiColors.TextPrimary) },
                        enabled = preferences != null && !savingPreference,
                        onClick = { showMenu = false; showAppearance = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.wiki_find_in_page), color = KitsugiColors.TextPrimary) },
                        enabled = session.view != null,
                        onClick = { showMenu = false; session.findOpen = true })
                    if (session.translationOverride != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.wiki_follow_auto_translate), color = KitsugiColors.TextPrimary) },
                            onClick = { showMenu = false; session.translationOverride = null })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.wiki_tools_info), color = KitsugiColors.TextPrimary) },
                        onClick = { showMenu = false; showInfo = true })
                }
            }
        }
        if (config.translate) {
            Text(
                stringResource(if (session.translating) R.string.wiki_dom_translating else R.string.wiki_dom_translated,
                    config.target, session.translatedNodes),
                style = MaterialTheme.typography.labelSmall,
                color = KitsugiColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        if (preferences?.pageTheme != "SITE" && !session.pageThemeSupported) {
            Text(stringResource(R.string.wiki_theme_limited), modifier = Modifier.padding(horizontal = 12.dp),
                color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        if (config.translate && session.progress == 100 && !session.articleAvailable && !session.enhancementError) {
            Text(stringResource(R.string.wiki_dom_no_article), modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelSmall)
        }
        if (preferenceFailed) {
            Text(stringResource(R.string.wiki_tools_save_failed), modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelSmall)
        }
        if (session.enhancementError || (config.translate && (session.translationFailures > 0 || session.translationLimited))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(stringResource(when {
                    session.enhancementError -> R.string.wiki_tools_script_failed
                    session.translationLimited -> R.string.wiki_dom_limit
                    else -> R.string.wiki_dom_partial
                }), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                if (session.enhancementError || session.translationFailures > 0) {
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { session.translationRetry++ }) { Text(stringResource(R.string.yenile)) }
                }
            }
        }
        if (showAppearance && preferences != null) {
            WikiReadingSettingsDialog(preferences, onDismiss = { showAppearance = false }) { mode, zoom ->
                showAppearance = false
                onSaveAppearance(mode, zoom)
            }
        }
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                containerColor = KitsugiColors.Surface,
                titleContentColor = KitsugiColors.TextPrimary,
                textContentColor = KitsugiColors.TextSecondary,
                title = { DisableDialogWindowForceDark(); Text(stringResource(R.string.wiki_tools_info), color = KitsugiColors.TextPrimary) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.wiki_tools_explanation))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.wiki_filter_attribution, assets?.rules?.ruleCount ?: 0, assets?.filterRevision.orEmpty()))
                        Spacer(Modifier.height(12.dp))
                        Text(assets?.license.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { showInfo = false }) { Text(stringResource(R.string.kapat)) } }
            )
        }
    }
}

@Composable
private fun WikiReadingSettingsDialog(
    preferences: WikiPagePreferences,
    onDismiss: () -> Unit,
    onApply: (String, Int) -> Unit
) {
    var mode by remember { mutableStateOf(WikiReadingAppearance.normalize(preferences.pageTheme)) }
    var zoom by remember { mutableStateOf(WikiReadingAppearance.textPercent(preferences.textZoom).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        titleContentColor = KitsugiColors.TextPrimary,
        textContentColor = KitsugiColors.TextSecondary,
        title = { DisableDialogWindowForceDark(); Text(stringResource(R.string.wiki_reading_appearance), color = KitsugiColors.TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.wiki_theme_explanation))
                Column(Modifier.selectableGroup()) {
                    WikiReadingAppearance.modes.forEach { value ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = mode == value, role = Role.RadioButton, onClick = { mode = value }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == value, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = KitsugiColors.TextPrimary,
                                    unselectedColor = KitsugiColors.TextSecondary))
                            Text(stringResource(when (value) {
                                "APP" -> R.string.wiki_theme_app
                                "LIGHT" -> R.string.wiki_theme_light
                                "DARK" -> R.string.wiki_theme_dark
                                else -> R.string.wiki_theme_site
                            }), color = KitsugiColors.TextPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.wiki_text_percent, zoom.toInt()), color = KitsugiColors.TextPrimary)
                Slider(value = zoom, onValueChange = { zoom = WikiReadingAppearance.textPercent(it.toInt()).toFloat() },
                    valueRange = 80f..180f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = KitsugiColors.TextPrimary,
                        activeTrackColor = KitsugiColors.TextPrimary))
                Text(stringResource(R.string.wiki_text_scale_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { mode = "SITE"; zoom = 100f }) { Text(stringResource(R.string.wiki_reading_reset)) }
            }
        },
        confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = { onApply(mode, zoom.toInt()) }) { Text(stringResource(R.string.wiki_reading_apply)) } },
        dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = KitsugiColors.TextPrimary), onClick = onDismiss) { Text(stringResource(R.string.kapat)) } }
    )
}

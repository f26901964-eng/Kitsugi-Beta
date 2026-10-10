package com.kitsugi.animelist.ui.screens.search.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import com.kitsugi.animelist.ui.theme.gradient.FilterChip
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaType

// ─── Format Chip ─────────────────────────────────────────────────────────────

@Composable
fun KitsugiSearchFormatChip(
    mediaType: MediaType,
    selectedFormats: List<KitsugiMediaFormat>,
    onFormatsChanged: (List<KitsugiMediaFormat>) -> Unit,
) {
    var openDialog by remember { mutableStateOf(false) }

    if (openDialog) {
        DialogWithCheckboxSelection(
            values = if (mediaType == MediaType.Manga) KitsugiMediaFormat.mangaEntries
                     else KitsugiMediaFormat.animeEntries,
            defaultValues = selectedFormats,
            title = "Format",
            onConfirm = { openDialog = false; onFormatsChanged(it) },
            onDismiss = { openDialog = false }
        )
    }

    FilterChip(
        selected = selectedFormats.isNotEmpty(),
        onClick = { openDialog = true },
        label = { Text(text = "Format") }
    )
}

// ─── Status Chip ─────────────────────────────────────────────────────────────

@Composable
fun KitsugiSearchStatusChip(
    selectedStatuses: List<KitsugiMediaStatus>,
    onStatusesChanged: (List<KitsugiMediaStatus>) -> Unit,
) {
    var openDialog by remember { mutableStateOf(false) }

    if (openDialog) {
        DialogWithCheckboxSelection(
            values = KitsugiMediaStatus.entries,
            defaultValues = selectedStatuses,
            title = "Durum",
            onConfirm = { openDialog = false; onStatusesChanged(it) },
            onDismiss = { openDialog = false }
        )
    }

    FilterChip(
        selected = selectedStatuses.isNotEmpty(),
        onClick = { openDialog = true },
        label = { Text(text = "Durum") }
    )
}

// ─── Country Chip ─────────────────────────────────────────────────────────────

@Composable
fun KitsugiSearchCountryChip(
    selectedCountry: KitsugiCountryOfOrigin?,
    onCountryChanged: (KitsugiCountryOfOrigin?) -> Unit,
) {
    KitsugiChipWithMenu(
        title = "Ülke",
        values = KitsugiCountryOfOrigin.entries,
        selectedValue = selectedCountry,
        onValueSelected = onCountryChanged,
        valueString = { it.localized() }
    )
}

// ─── Source Chip ─────────────────────────────────────────────────────────────

@Composable
fun KitsugiSearchSourceChip(
    selectedSources: List<KitsugiMediaSource>,
    onSourcesChanged: (List<KitsugiMediaSource>) -> Unit,
) {
    var openDialog by remember { mutableStateOf(false) }

    if (openDialog) {
        DialogWithCheckboxSelection(
            values = KitsugiMediaSource.entries,
            defaultValues = selectedSources,
            title = "Kaynak",
            onConfirm = { openDialog = false; onSourcesChanged(it) },
            onDismiss = { openDialog = false }
        )
    }

    FilterChip(
        selected = selectedSources.isNotEmpty(),
        onClick = { openDialog = true },
        label = { Text(text = "Kaynak") }
    )
}

// ─── Date Chip ───────────────────────────────────────────────────────────────

private val SEASON_YEARS: List<Int> by lazy {
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    (currentYear downTo 1960).toList()
}

@Composable
fun KitsugiSearchDateChip(
    startYear: Int?,
    endYear: Int?,
    season: KitsugiMediaSeason?,
    onStartYearChanged: (Int?) -> Unit,
    onEndYearChanged: (Int?) -> Unit,
    onSeasonChanged: (KitsugiMediaSeason?) -> Unit,
) {
    val startYears = remember { SEASON_YEARS }
    val endYears = remember(startYear) {
        if (startYear != null) startYears.filter { it >= startYear } else startYears
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KitsugiChipWithMenu(
            title = "Başlangıç",
            values = startYears,
            selectedValue = startYear,
            onValueSelected = onStartYearChanged,
            valueString = { it.toString() }
        )
        Text(text = "–")
        KitsugiChipWithMenu(
            title = "Bitiş",
            values = endYears,
            selectedValue = endYear,
            onValueSelected = onEndYearChanged,
            valueString = { it.toString() }
        )
        KitsugiChipWithMenu(
            title = "Sezon",
            values = KitsugiMediaSeason.entries,
            selectedValue = season,
            onValueSelected = onSeasonChanged,
            modifier = Modifier.padding(start = 4.dp),
            valueString = { it.localized() }
        )
    }
}

// ─── Episodes / Duration / Chapters / Volumes Chip ───────────────────────────

private const val MAX_EPISODES = 150f
private const val MAX_DURATION = 170f
private const val MAX_CHAPTERS = 500f
private const val MAX_VOLUMES = 50f

@Composable
fun KitsugiSearchEpChDurationChip(
    mediaType: MediaType,
    minEpCh: Int?,
    maxEpCh: Int?,
    minDuration: Int?,
    maxDuration: Int?,
    onEpChChanged: (IntRange?) -> Unit,
    onDurationChanged: (IntRange?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mediaType == MediaType.Manga) {
            KitsugiChipWithRange(
                title = "Bölümler",
                startValue = minEpCh?.toFloat(),
                endValue = maxEpCh?.toFloat(),
                minValue = 0f,
                maxValue = MAX_CHAPTERS,
                onValueChanged = onEpChChanged
            )
            KitsugiChipWithRange(
                title = "Ciltler",
                startValue = minDuration?.toFloat(),
                endValue = maxDuration?.toFloat(),
                minValue = 0f,
                maxValue = MAX_VOLUMES,
                onValueChanged = onDurationChanged
            )
        } else {
            KitsugiChipWithRange(
                title = "Bölümler",
                startValue = minEpCh?.toFloat(),
                endValue = maxEpCh?.toFloat(),
                minValue = 0f,
                maxValue = MAX_EPISODES,
                onValueChanged = onEpChChanged
            )
            KitsugiChipWithRange(
                title = "Süre (dk)",
                startValue = minDuration?.toFloat(),
                endValue = maxDuration?.toFloat(),
                minValue = 0f,
                maxValue = MAX_DURATION,
                onValueChanged = onDurationChanged
            )
        }
    }
}

// ─── AniHyou Tri-Filter Chip (null -> true -> false -> null) ──────────────────

@Composable
fun KitsugiTriFilterChip(
    text: String,
    value: Boolean?,
    onValueChanged: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val accentColor = com.kitsugi.animelist.ui.theme.LocalKitsugiAccent.current
    com.kitsugi.animelist.ui.theme.gradient.FilterChip(
        selected = value != null,
        onClick = {
            onValueChanged(
                when (value) {
                    null -> true
                    true -> false
                    false -> null
                }
            )
        },
        label = { Text(text = text) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = {
            if (value == true) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Evet"
                )
            } else if (value == false) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Hayır"
                )
            }
        },
        colors = if (value == false) {
            androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentRed.copy(alpha = 0.2f),
                selectedLabelColor = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentRed,
                selectedLeadingIconColor = com.kitsugi.animelist.ui.theme.KitsugiColors.AccentRed,
            )
        } else {
            androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                selectedLabelColor = accentColor,
                selectedLeadingIconColor = accentColor,
            )
        }
    )
}


package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryCard
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Status-grouped list content rendering for MyListScreen.
 * Renders entries grouped by watch status with section headers.
 * Supports all layout IDs including responsive dynamic poster grid.
 */
internal fun LazyListScope.MyListGroupedContent(
    groupedEntries: List<Pair<WatchStatus, List<MediaEntry>>>,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int = 3,
    sourceBadgesByEntryId: Map<Int, List<String>> = emptyMap(),
    onEntryClick: (MediaEntry) -> Unit,
    onIncrementProgress: (MediaEntry) -> Unit,
    onPosterLongClick: (String) -> Unit
) {
    val isGrid = selectedListLayoutId == "grid_2col"

    groupedEntries.forEach { (status, itemsForStatus) ->
        item(key = "header_${status.name}") {
            val headerTitle = when (status) {
                WatchStatus.Watching   -> "İzleniyor"
                WatchStatus.Repeating  -> "Yeniden İzleniyor"
                WatchStatus.Planned    -> "Planlandı"
                WatchStatus.Paused     -> "Durduruldu"
                WatchStatus.Dropped    -> "Bırakıldı"
                WatchStatus.Completed  -> "İzlendi"
            }
            Text(
                text = headerTitle,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp)
            )
        }

        if (isGrid) {
            val rows = itemsForStatus.chunked(gridColumns)
            items(
                items = rows,
                key = { row -> "grid_row_${status.name}_${row.firstOrNull()?.let { "${it.source}_${it.id}" }}" }
            ) { row ->
                // NOT: burada eskiden `height(IntrinsicSize.Min)` + kartlarda
                // `weight(1f).fillMaxHeight()` kullanılıyordu. Ağırlıklı çocukların
                // iç (intrinsic) yükseklik ölçümünde genişliği 0 geldiğinden posterin
                // `aspectRatio`'su 0 yükseklik hesaplıyor, TÜM satır 0 piksele çöküyordu
                // ("844 sonuç" yazıyor ama hiçbir kart görünmüyordu). Satır artık kendi
                // içeriğiyle ölçülüyor; posterler üstten hizalı (uygulamadaki diğer tüm
                // poster ızgaralarıyla aynı davranış).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { entry ->
                        KitsugiMediaEntryCard(
                            entry = entry,
                            layoutId = selectedListLayoutId,
                            modifier = Modifier.weight(1f),
                            onClick = { onEntryClick(entry) },
                            titleLanguage = titleLanguage,
                            scoreFormat = scoreFormat,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia,
                            onPosterLongClick = { imageUrl -> onPosterLongClick(imageUrl) },
                            sourceBadges = sourceBadgesByEntryId[entry.id]
                        )
                    }
                    if (row.size < gridColumns) {
                        repeat(gridColumns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            // ─── Normal tek sütun liste ───────────────────────────────
            items(
                items = itemsForStatus,
                key = { entry -> "${entry.source}_${entry.id}" }
            ) { entry ->
                KitsugiMediaEntryCard(
                    entry = entry,
                    layoutId = selectedListLayoutId,
                    onClick = { onEntryClick(entry) },
                    onIncrementClick = { onIncrementProgress(entry) },
                    titleLanguage = titleLanguage,
                    scoreFormat = scoreFormat,
                    hideScores = hideScores,
                    blurAdultMedia = blurAdultMedia,
                    onPosterLongClick = { imageUrl -> onPosterLongClick(imageUrl) },
                    sourceBadges = sourceBadgesByEntryId[entry.id]
                )
                Spacer(modifier = Modifier.height(cardSpacingForLayout(selectedListLayoutId)))
            }
        }
    }
}

/**
 * Flat (non-grouped) list content rendering for MyListScreen.
 * Used when the Completed filter is active.
 * Supports all layout IDs including responsive dynamic poster grid.
 */
internal fun LazyListScope.MyListFlatContent(
    visibleEntries: List<MediaEntry>,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int = 3,
    sourceBadgesByEntryId: Map<Int, List<String>> = emptyMap(),
    onEntryClick: (MediaEntry) -> Unit,
    onIncrementProgress: (MediaEntry) -> Unit,
    onPosterLongClick: (String) -> Unit
) {
    val isGrid = selectedListLayoutId == "grid_2col"

    if (isGrid) {
        val rows = visibleEntries.chunked(gridColumns)
        items(
            items = rows,
            key = { row -> "flat_grid_row_${row.firstOrNull()?.let { "${it.source}_${it.id}" }}" }
        ) { row ->
            // Bkz. MyListGroupedContent: `IntrinsicSize.Min` + `fillMaxHeight()` +
            // `weight` birleşimi aspectRatio posterlerde satırı 0 yüksekliğe çökertip
            // listeyi boş gösteriyordu. İçerikle ölçülen satır + üstten hizalama kullanılır.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { entry ->
                    KitsugiMediaEntryCard(
                        entry = entry,
                        layoutId = selectedListLayoutId,
                        modifier = Modifier.weight(1f),
                        onClick = { onEntryClick(entry) },
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        blurAdultMedia = blurAdultMedia,
                        onPosterLongClick = { imageUrl -> onPosterLongClick(imageUrl) },
                        sourceBadges = sourceBadgesByEntryId[entry.id]
                    )
                }
                if (row.size < gridColumns) {
                    repeat(gridColumns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    } else {
        // ─── Normal tek sütun liste ───────────────────────────────────────
        items(
            items = visibleEntries,
            key = { entry -> "${entry.source}_${entry.id}" }
        ) { entry ->
            KitsugiMediaEntryCard(
                entry = entry,
                layoutId = selectedListLayoutId,
                onClick = { onEntryClick(entry) },
                onIncrementClick = { onIncrementProgress(entry) },
                titleLanguage = titleLanguage,
                scoreFormat = scoreFormat,
                hideScores = hideScores,
                blurAdultMedia = blurAdultMedia,
                onPosterLongClick = { imageUrl -> onPosterLongClick(imageUrl) },
                sourceBadges = sourceBadgesByEntryId[entry.id]
            )
            Spacer(modifier = Modifier.height(cardSpacingForLayout(selectedListLayoutId)))
        }
    }
}

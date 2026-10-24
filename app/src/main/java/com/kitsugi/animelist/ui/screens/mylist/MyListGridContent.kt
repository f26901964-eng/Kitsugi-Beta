package com.kitsugi.animelist.ui.screens.mylist

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    groupedEntries.forEach { (status, itemsForStatus) ->
        myListSectionContent(
            sectionKey = "status_${status.name}",
            headerTitle = when (status) {
                WatchStatus.Watching   -> "İzleniyor"
                WatchStatus.Repeating  -> "Yeniden İzleniyor"
                WatchStatus.Planned    -> "Planlandı"
                WatchStatus.Paused     -> "Durduruldu"
                WatchStatus.Dropped    -> "Bırakıldı"
                WatchStatus.Completed  -> "İzlendi"
            },
            headerRes = null,
            sectionEntries = itemsForStatus,
            selectedListLayoutId = selectedListLayoutId,
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            blurAdultMedia = blurAdultMedia,
            gridColumns = gridColumns,
            sourceBadgesByEntryId = sourceBadgesByEntryId,
            onEntryClick = onEntryClick,
            onIncrementProgress = onIncrementProgress,
            onPosterLongClick = onPosterLongClick
        )
    }
}

/**
 * "Son eklenen / İlk eklenen" görünümü: düz global tarih sıralaması, dönem
 * başlıklarıyla (Bugün, Bu Hafta, Bu Ay…). Bu iki sıralamanın ÖZEL mantığıdır —
 * durum bölümleri tarihi bölemez; kullanıcı neyi ne zaman eklediğini tek
 * zaman çizelgesinde görür. Bölüm üretimi: [groupMyListEntriesByAddedPeriod].
 */
internal fun LazyListScope.MyListAddedPeriodContent(
    sections: List<Pair<MyListAddedPeriod, List<MediaEntry>>>,
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
    sections.forEach { (period, periodEntries) ->
        myListSectionContent(
            sectionKey = "added_${period.name}",
            headerTitle = null,
            headerRes = period.titleRes,
            sectionEntries = periodEntries,
            selectedListLayoutId = selectedListLayoutId,
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            blurAdultMedia = blurAdultMedia,
            gridColumns = gridColumns,
            sourceBadgesByEntryId = sourceBadgesByEntryId,
            onEntryClick = onEntryClick,
            onIncrementProgress = onIncrementProgress,
            onPosterLongClick = onPosterLongClick
        )
    }
}

/**
 * Ortak bölüm çizicisi: bir başlık + o bölümün kartları (ızgara ya da tek sütun).
 * Başlık metni düz metin ([headerTitle], durum bölümleri — tarihsel davranış) ya da
 * yerelleştirilmiş kaynak ([headerRes], eklenme-dönemi bölümleri) ile verilebilir.
 */
private fun LazyListScope.myListSectionContent(
    sectionKey: String,
    headerTitle: String?,
    @StringRes headerRes: Int?,
    sectionEntries: List<MediaEntry>,
    selectedListLayoutId: String,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    gridColumns: Int,
    sourceBadgesByEntryId: Map<Int, List<String>>,
    onEntryClick: (MediaEntry) -> Unit,
    onIncrementProgress: (MediaEntry) -> Unit,
    onPosterLongClick: (String) -> Unit
) {
    val isGrid = selectedListLayoutId == "grid_2col"

    item(key = "header_$sectionKey") {
        Text(
            text = if (headerRes != null) stringResource(headerRes) else headerTitle.orEmpty(),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp)
        )
    }

    if (isGrid) {
        val rows = sectionEntries.chunked(gridColumns)
        items(
            items = rows,
            key = { row -> "grid_row_${sectionKey}_${row.firstOrNull()?.let { "${it.source}_${it.id}" }}" }
        ) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { entry ->
                    KitsugiMediaEntryCard(
                        entry = entry,
                        layoutId = selectedListLayoutId,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
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
            items = sectionEntries,
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

/**
 * Flat (non-grouped) list content rendering for MyListScreen.
 * Used whenever a user-selected sort owns the global order
 * (see myListUsesFlatSortedLayout) or the separated list style is off.
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
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { entry ->
                    KitsugiMediaEntryCard(
                        entry = entry,
                        layoutId = selectedListLayoutId,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
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

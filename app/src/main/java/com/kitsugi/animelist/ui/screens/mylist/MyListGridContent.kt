package com.kitsugi.animelist.ui.screens.mylist

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiMediaEntryCard
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Tek bir liste bölümü: başlık + (ızgara veya tek sütun) kartlar.
 *
 * Aynı gövde hem durum gruplarında ([MyListGroupedContent]) hem de eklenme tarihi
 * bölümlerinde ([MyListDateGroupedContent]) kullanılır; iki kopya arasında düzen
 * farkı oluşmasın diye tek yerde tutulur.
 */
private fun LazyListScope.myListEntrySection(
    sectionKey: String,
    headerTitle: @Composable () -> String,
    itemsForSection: List<MediaEntry>,
    isGrid: Boolean,
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
    item(key = "header_$sectionKey") {
        Text(
            text = headerTitle(),
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp)
        )
    }

    if (isGrid) {
        val rows = itemsForSection.chunked(gridColumns)
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
            items = itemsForSection,
            key = { entry -> "entry_${sectionKey}_${entry.source}_${entry.id}" }
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
        val headerTitle = when (status) {
            WatchStatus.Watching   -> "İzleniyor"
            WatchStatus.Repeating  -> "Yeniden İzleniyor"
            WatchStatus.Planned    -> "Planlandı"
            WatchStatus.Paused     -> "Durduruldu"
            WatchStatus.Dropped    -> "Bırakıldı"
            WatchStatus.Completed  -> "İzlendi"
        }
        myListEntrySection(
            sectionKey = status.name,
            headerTitle = { headerTitle },
            itemsForSection = itemsForStatus,
            isGrid = isGrid,
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
 * "Son eklenen / İlk eklenen" sıralamasında kullanılan tarih bölümleri:
 * Bugün → Dün → Bu Hafta → Geçen Hafta → Daha Eski → Eklendiği Tarih Bilinmiyor.
 *
 * Bölümler kullanıcının yapımı listesine eklediği ana göre hesaplanır
 * ([MediaEntry.addedAt]); durum (İzleniyor/Planlandı…) değişiklikleri bölümü kaydırmaz.
 */
internal fun LazyListScope.MyListDateGroupedContent(
    groupedEntries: List<Pair<MyListDateBucket, List<MediaEntry>>>,
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

    groupedEntries.forEach { (bucket, itemsForBucket) ->
        myListDateSection(
            bucket = bucket,
            itemsForBucket = itemsForBucket,
            isGrid = isGrid,
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

/** [MyListDateGroupedContent] içindeki tek bölüm; başlık çevirisi item içinde çözülür. */
private fun LazyListScope.myListDateSection(
    bucket: MyListDateBucket,
    itemsForBucket: List<MediaEntry>,
    isGrid: Boolean,
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
    myListEntrySection(
        sectionKey = "date_${bucket.name}",
        headerTitle = { stringResource(bucket.labelRes) },
        itemsForSection = itemsForBucket,
        isGrid = isGrid,
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

package com.kitsugi.animelist.ui.screens.mylist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StopCircle
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.KitsugiPremiumSheet
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

data class StatusItemData(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int
)

/**
 * "Listem" kategori seçici (Tümü / İzleniyor / Tamamlandı / …).
 *
 * Eskiden düz bir `ModalBottomSheet` içinde, başlıksız ve karartmasız açılıyordu;
 * aynı ekrandaki "Filtrele & Sırala" paneliyle görsel olarak hiç alakası yoktu.
 * Artık o panelle **ayını** yüzey dilini kullanır: [KitsugiPremiumSheet] — yay fiziğiyle
 * seken açılış, gradyan sürükleme kulpu, ikon karosu + başlık + çarpı, aurora saç
 * çizgisi, karartılmış arka plan ve altta "Tümü / Bitti" eylem çifti.
 *
 * Seçim anında uygulanır ve panel kapanır (eskisi gibi); "Tümü" sıfırlar, "Bitti" kapatır.
 */
@Composable
fun KitsugiListStatusBottomSheet(
    entries: List<MediaEntry>,
    selectedStatusFilterId: String,
    showAdultContent: Boolean = false,
    onStatusSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val statusLabels = mapOf(
        "all" to stringResource(R.string.mylist_filter_all),
        "watching" to stringResource(R.string.mylist_filter_watching),
        "completed" to stringResource(R.string.mylist_filter_completed),
        "planned" to stringResource(R.string.mylist_filter_planned),
        "paused" to stringResource(R.string.mylist_filter_paused),
        "dropped" to stringResource(R.string.mylist_filter_dropped),
        "adult" to stringResource(R.string.filter_adult),
        "favorites" to stringResource(R.string.mylist_filter_favorites)
    )

    val watchingCount = entries.count { it.status == WatchStatus.Watching }
    val completedCount = entries.count { it.status == WatchStatus.Completed }
    val plannedCount = entries.count { it.status == WatchStatus.Planned }
    val pausedCount = entries.count { it.status == WatchStatus.Paused }
    val droppedCount = entries.count { it.status == WatchStatus.Dropped }
    val adultCount = entries.count { it.isAdult }
    val favoritesCount = entries.count { it.isFavorite }

    val statusItems = mutableListOf(
        StatusItemData("all", statusLabels.getValue("all"), Icons.Rounded.FormatListBulleted, entries.size),
        StatusItemData("watching", statusLabels.getValue("watching"), Icons.Rounded.PlayCircle, watchingCount),
        StatusItemData("completed", statusLabels.getValue("completed"), Icons.Rounded.CheckCircle, completedCount),
        StatusItemData("planned", statusLabels.getValue("planned"), Icons.Rounded.Schedule, plannedCount),
        StatusItemData("paused", statusLabels.getValue("paused"), Icons.Rounded.PauseCircle, pausedCount),
        StatusItemData("dropped", statusLabels.getValue("dropped"), Icons.Rounded.StopCircle, droppedCount)
    )

    if (showAdultContent && adultCount > 0) {
        statusItems.add(
            StatusItemData("adult", statusLabels.getValue("adult"), Icons.Rounded.Star, adultCount)
        )
    }

    if (favoritesCount > 0) {
        statusItems.add(
            StatusItemData("favorites", statusLabels.getValue("favorites"), Icons.Rounded.Star, favoritesCount)
        )
    }

    KitsugiPremiumSheet(
        onDismiss = onDismissRequest,
        title = stringResource(R.string.search_filter_category),
        icon = Icons.Rounded.FormatListBulleted,
        badgeText = if (selectedStatusFilterId != "all") {
            statusLabels[selectedStatusFilterId]
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statusItems.forEach { item ->
                ListStatusRow(
                    item = item,
                    selected = selectedStatusFilterId == item.id,
                    onClick = {
                        onStatusSelected(item.id)
                        onDismissRequest()
                    }
                )
            }

            // ── Alt eylemler: Tümü + Bitti (filtre paneliyle aynı düzen) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val accentColor = LocalKitsugiAccent.current
                val actionShape = RoundedCornerShape(16.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(actionShape)
                        .background(KitsugiColors.SurfaceSoft, actionShape)
                        .tvClickable(shape = actionShape, onClick = { onStatusSelected("all") })
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusLabels.getValue("all"),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(actionShape)
                        .background(LocalKitsugiAccentBrush.current, actionShape)
                        .tvClickable(shape = actionShape, onClick = onDismissRequest)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.mylist_filter_done),
                        color = LocalKitsugiOnAccent.current,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/**
 * Panel içi kategori satırı: küçük ikon karosu + etiket + sağda adet rozeti.
 * Seçili satır, filtre panelindeki seçili haplarla aynı gradyan dolguyu taşır.
 */
@Composable
private fun ListStatusRow(
    item: StatusItemData,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val accentBrush = LocalKitsugiAccentBrush.current
    val onAccent = LocalKitsugiOnAccent.current
    val rowShape = RoundedCornerShape(16.dp)
    val tileShape = CircleShape
    val countShape = RoundedCornerShape(999.dp)

    // Renk ve gradyan aynı `background` overload'undan geçsin diye düz renk de
    // SolidColor ile Brush'a çevrilir (seçili satır gradyan, diğerleri mat yüzey).
    val rowBrush: Brush = if (selected) {
        accentBrush
    } else {
        SolidColor(KitsugiColors.SurfaceSoft.copy(alpha = 0.45f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBrush, rowShape)
            .tvClickable(shape = rowShape, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(tileShape)
                    .background(
                        if (selected) Color.White.copy(alpha = 0.18f) else KitsugiColors.Surface,
                        tileShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (selected) onAccent else KitsugiColors.TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = item.title,
                color = if (selected) onAccent else KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .clip(countShape)
                .background(
                    if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                    countShape
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = item.count.toString(),
                color = if (selected) onAccent else if (item.count == 0) KitsugiColors.TextMuted.copy(alpha = 0.6f) else accentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

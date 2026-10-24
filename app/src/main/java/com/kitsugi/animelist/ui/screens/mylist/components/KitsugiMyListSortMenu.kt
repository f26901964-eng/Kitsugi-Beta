package com.kitsugi.animelist.ui.screens.mylist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Listem'de seçilebilen TEK bir sıralama ölçütü.
 *
 * Aynı liste üç ayrı yerde kullanılıyor (satır içi çipler, sıralama çekmecesi ve filtre
 * sayfası). Daha önce üçü ayrı listelerden besleniyordu ve birbirinden koptu:
 * "Favoriler", "Yayın yılı" ve "Öncelik" yalnızca birinde vardı; kullanıcı hangi ekrandan
 * bakarsa baksın aynı seçenekleri görmeli. Etiketler `@StringRes` üzerinden gelir, bu
 * yüzden TR/EN sürümleri ayrışamaz.
 *
 * [ascId] `null` ise ölçütün ters yönü yoktur (ör. Favoriler): tekrar dokunmak seçimi
 * korur, ok simgesi de daima "azalan" olur.
 */
data class MyListSortEntry(
    val descId: String,
    val ascId: String?,
    @StringRes val titleRes: Int
)

val myListSortEntries = listOf(
    MyListSortEntry("newest", "oldest", R.string.mylist_sort_added),
    MyListSortEntry("title", "title_desc", R.string.mylist_sort_title),
    MyListSortEntry("score", "score_asc", R.string.detail_stat_score),
    MyListSortEntry("progress", "progress_asc", R.string.mylist_sort_progress),
    MyListSortEntry("favorites", null, R.string.mylist_sort_favorites),
    MyListSortEntry("start_date_desc", "start_date_asc", R.string.mylist_sort_start),
    MyListSortEntry("end_date_desc", "end_date_asc", R.string.mylist_sort_end),
    MyListSortEntry("year_desc", "year_asc", R.string.mylist_sort_year),
    MyListSortEntry("updated_desc", "updated_asc", R.string.mylist_sort_updated),
    MyListSortEntry("repeat_desc", "repeat_asc", R.string.mylist_sort_repeat),
    MyListSortEntry("priority_desc", "priority_asc", R.string.mylist_sort_priority)
)

/** Dokunulduğunda sıradaki sıralama kimliği: azalan → artan → azalan. */
fun MyListSortEntry.nextSortId(current: String): String = when {
    ascId != null && current == ascId -> descId
    ascId != null && current == descId -> ascId
    else -> descId
}

data class SortMenuItem(
    val descId: String,
    val ascId: String,
    val title: String
)

val myListSortMenuItems = listOf(
    SortMenuItem("default", "default", KitsugiStrings.get(R.string.mylist_sort_default)),
    SortMenuItem("title", "title_desc", KitsugiStrings.get(R.string.ui_account_title)),
    SortMenuItem("score", "score_asc", "Puan"),
    SortMenuItem("progress", "progress_asc", "İlerleme"),
    SortMenuItem("updated_desc", "updated_asc", "Güncelleme tarihi"),
    SortMenuItem("newest", "oldest", "Eklenme tarihi"),
    SortMenuItem("start_date_desc", "start_date_asc", "Başlangıç tarihi"),
    SortMenuItem("end_date_desc", "end_date_asc", "Bitiş tarihi"),
    SortMenuItem("repeat_desc", "repeat_asc", "Tekrar Sayısı")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiMyListSortMenu(
    expanded: Boolean,
    selectedSortId: String,
    onSortSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!expanded) return

    val accentColor = LocalKitsugiAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = KitsugiColors.SurfaceStrong,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.45f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            myListSortEntries.forEach { item ->
                val isDescActive = selectedSortId == item.descId
                val isAscActive = item.ascId != null && selectedSortId == item.ascId
                val isSelected = isDescActive || isAscActive

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) KitsugiColors.SurfaceSoft else KitsugiColors.SurfaceStrong)
                        .tvClickable(shape = RoundedCornerShape(18.dp)) {
                            onSortSelected(item.nextSortId(selectedSortId))
                            onDismissRequest()
                        }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(item.titleRes),
                        color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = if (isAscActive) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = if (isAscActive) "Artan" else "Azalan",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

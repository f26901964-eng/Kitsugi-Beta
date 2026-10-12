package com.kitsugi.animelist.ui.screens.mylist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiPremiumSheet
import com.kitsugi.animelist.ui.screens.mylist.ExtraFilterRow
import com.kitsugi.animelist.ui.screens.mylist.MyListContentTypeRow
import com.kitsugi.animelist.ui.screens.mylist.ScoreFilterRow
import com.kitsugi.animelist.ui.screens.mylist.SortFilterRow
import com.kitsugi.animelist.ui.screens.mylist.StatusFilterRow
import com.kitsugi.animelist.ui.screens.mylist.TypeFilterRow
import com.kitsugi.animelist.ui.screens.mylist.YearFilterRow
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccentBrush
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * 🌌 "Listem" ekranının filtre & sıralama bölümünü taşıyan yeni premium açılır panel.
 *
 * Eskiden başlığın altında satır içi (inline) açılan filtre alanı artık
 * [KitsugiPremiumSheet] üzerinde: yay fiziğiyle seken açılış, gradyan kenarlık,
 * aurora parlaması ve tema vurgusuyla uyumlu modern bölüm başlıkları.
 *
 * Filtreler anında uygulanır; "Temizle" tümünü sıfırlar, "Bitti" paneli kapatır.
 */
@Composable
fun KitsugiMyListFilterSheet(
    selectedStatusFilterId: String,
    selectedTypeFilterId: String,
    selectedScoreFilterId: String,
    selectedYearFilterId: String,
    selectedExtraFilterId: String,
    selectedSortId: String,
    onStatusSelected: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onScoreSelected: (String) -> Unit,
    onYearSelected: (String) -> Unit,
    onExtraSelected: (String) -> Unit,
    onSortSelected: (String) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeFilterCount = listOf(
        selectedStatusFilterId,
        selectedTypeFilterId,
        selectedScoreFilterId,
        selectedYearFilterId,
        selectedExtraFilterId
    ).count { it != "all" }

    KitsugiPremiumSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.mylist_filter_sheet_title),
        icon = Icons.Rounded.Tune,
        badgeText = if (activeFilterCount > 0) {
            stringResource(R.string.mylist_filter_active_count, activeFilterCount)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── İçerik türü hapları ──
            MyListContentTypeRow(
                selectedTypeFilterId = selectedTypeFilterId,
                onTypeSelected = onTypeSelected
            )

            FilterSheetSection(title = "Durum") {
                StatusFilterRow(
                    selectedStatusFilterId = selectedStatusFilterId,
                    onStatusSelected = onStatusSelected
                )
            }

            FilterSheetSection(title = "Medya Türü") {
                TypeFilterRow(
                    selectedTypeFilterId = selectedTypeFilterId,
                    onTypeSelected = onTypeSelected
                )
            }

            FilterSheetSection(title = "Puan Aralığı") {
                ScoreFilterRow(
                    selectedScoreFilterId = selectedScoreFilterId,
                    onScoreSelected = onScoreSelected
                )
            }

            FilterSheetSection(title = stringResource(R.string.yayin_yili)) {
                YearFilterRow(
                    selectedYearFilterId = selectedYearFilterId,
                    onYearSelected = onYearSelected
                )
            }

            FilterSheetSection(title = "Ekstra / Özel") {
                ExtraFilterRow(
                    selectedExtraFilterId = selectedExtraFilterId,
                    onExtraSelected = onExtraSelected
                )
            }

            FilterSheetSection(title = stringResource(R.string.siralama)) {
                SortFilterRow(
                    selectedSortId = selectedSortId,
                    onSortSelected = onSortSelected
                )
            }

            // ── Alt eylemler: Temizle + Bitti ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val accentColor = LocalKitsugiAccent.current
                val actionShape = RoundedCornerShape(16.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(actionShape)
                        .background(KitsugiColors.SurfaceSoft, actionShape)
                        .tvClickable(shape = actionShape, onClick = onResetFilters)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.temizle),
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
                        .tvClickable(shape = actionShape, onClick = onDismiss)
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
 * Premium panel içi filtre bölümü: gradyan nokta + büyük harfli etiket ve
 * altına yatay kaydırılabilir hap satırı.
 */
@Composable
private fun FilterSheetSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentBrush = LocalKitsugiAccentBrush.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentBrush, CircleShape)
            )
            Text(
                text = title.uppercase(),
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        content()
    }
}

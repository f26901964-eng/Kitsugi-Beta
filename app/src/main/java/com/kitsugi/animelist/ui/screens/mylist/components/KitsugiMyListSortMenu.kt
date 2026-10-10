package com.kitsugi.animelist.ui.screens.mylist.components

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiMenuItem
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

data class SortMenuItem(
    val descId: String,
    val ascId: String,
    val title: String
)

val myListSortMenuItems = listOf(
    SortMenuItem("title", "title_desc", "Başlık"),
    SortMenuItem("score", "score_asc", "Puan"),
    SortMenuItem("progress", "progress_asc", "İlerleme"),
    SortMenuItem("updated_desc", "updated_asc", "Güncelleme tarihi"),
    SortMenuItem("newest", "oldest", "Eklenme tarihi"),
    SortMenuItem("start_date_desc", "start_date_asc", "Başlangıç tarihi"),
    SortMenuItem("end_date_desc", "end_date_asc", "Bitiş tarihi"),
    SortMenuItem("repeat_desc", "repeat_asc", "Tekrar Sayısı")
)

@Composable
fun KitsugiMyListSortMenu(
    expanded: Boolean,
    selectedSortId: String,
    onSortSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    KitsugiDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        myListSortMenuItems.forEach { item ->
            val isDescActive = selectedSortId == item.descId
            val isAscActive = selectedSortId == item.ascId
            val isSelected = isDescActive || isAscActive

            KitsugiMenuItem(
                text = {
                    Text(
                        text = item.title,
                        color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                selected = isSelected,
                trailingIcon = if (isSelected) ({
                    Icon(
                        imageVector = if (isAscActive) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = if (isAscActive) "Artan" else "Azalan",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }) else null,
                onClick = {
                    val nextSortId = if (isDescActive) {
                        item.ascId
                    } else {
                        item.descId
                    }
                    onSortSelected(nextSortId)
                    onDismissRequest()
                }
            )
        }
    }
}

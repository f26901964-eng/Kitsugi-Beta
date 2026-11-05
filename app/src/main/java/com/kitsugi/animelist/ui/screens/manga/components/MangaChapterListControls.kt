package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ChapterFilter
import com.kitsugi.animelist.data.manga.ChapterSort
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Bölüm listesi için arama alanı + sıralama düğmesi.
 *
 * Kullanıcının bildirdiği hata: "Bölümler (109)" başlığındaki ok ikonu hiçbir iş
 * yapmıyordu. Bu düğme artık gerçekten sıralama yönünü değiştiriyor ve altında
 * aktif sıralamanın adı gösteriliyor (dokununca tüm seçenekler açılır).
 */
@Composable
fun ChapterSearchAndSortBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: ChapterSort,
    onSortClick: () -> Unit,
    onToggleDirection: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.chapter_search_hint), color = KitsugiColors.TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .tvClickable(shape = CircleShape) { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.temizle), tint = KitsugiColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = KitsugiColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = KitsugiColors.Border,
                cursorColor = accent,
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary,
                focusedContainerColor = KitsugiColors.Surface.copy(0.6f),
                unfocusedContainerColor = KitsugiColors.Surface.copy(0.35f)
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        // Sıralama düğmesi — tek dokunuşta yön değişir, uzun basışta seçenek paneli.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .tvClickable(shape = RoundedCornerShape(14.dp), onLongClick = onSortClick) { onToggleDirection() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (sort == ChapterSort.NewestFirst || sort == ChapterSort.TitleAsc) {
                        Icons.AutoMirrored.Rounded.Sort
                    } else {
                        Icons.Rounded.SwapVert
                    },
                    contentDescription = stringResource(R.string.chapter_sort),
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = sort.label(),
                color = KitsugiColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Bölüm filtresi çipleri: okuma durumu (Tümü / Okunmamış / Okunmuş) ve
 * tarama grupları (scanlator). Kaynakta birden fazla grup varsa çipler gösterilir.
 */
@Composable
fun ChapterFilterChips(
    filter: ChapterFilter,
    scanlators: List<String>,
    selectedScanlator: String?,
    unreadCount: Int,
    onFilterSelected: (ChapterFilter) -> Unit,
    onScanlatorSelected: (String?) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "all") {
                ChapterChip(
                    label = stringResource(R.string.chapter_filter_all) + " (${unreadCount})",
                    selected = filter == ChapterFilter.All && selectedScanlator == null,
                    accent = accent
                ) { onFilterSelected(ChapterFilter.All); onScanlatorSelected(null) }
            }
            item(key = "unread") {
                ChapterChip(
                    label = stringResource(R.string.chapter_filter_unread),
                    selected = filter == ChapterFilter.Unread,
                    accent = accent
                ) { onFilterSelected(ChapterFilter.Unread) }
            }
            item(key = "read") {
                ChapterChip(
                    label = stringResource(R.string.chapter_filter_read),
                    selected = filter == ChapterFilter.Read,
                    accent = accent
                ) { onFilterSelected(ChapterFilter.Read) }
            }
            if (scanlators.size > 1) {
                item(key = "misc") {
                    ChapterChip(
                        label = stringResource(R.string.chapter_filter_hide_misc),
                        selected = filter == ChapterFilter.HideMisc,
                        accent = accent
                    ) { onFilterSelected(ChapterFilter.HideMisc) }
                }
                items(scanlators, key = { "sl_$it" }) { name ->
                    ChapterChip(
                        label = name,
                        selected = selectedScanlator?.equals(name, true) == true,
                        accent = accent
                    ) { onScanlatorSelected(name) }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ChapterChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.18f) else KitsugiColors.Surface.copy(0.4f))
            .tvClickable(shape = RoundedCornerShape(50)) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) accent else KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Sıralama düğmesinin altındaki kısa etiket. */
fun ChapterSort.label(): String = when (this) {
    ChapterSort.NewestFirst -> "Yeni"
    ChapterSort.NumberDesc -> "137→1"
    ChapterSort.NumberAsc -> "1→137"
    ChapterSort.TitleAsc -> "A→Z"
}

/**
 * Sıralama seçenekleri paneli (uzun basış veya başlıktaki "Sırala" düğmesi).
 * Seçenekler Mihon'un bölüm sıralama menüsüyle aynı mantıkta.
 */
@Composable
fun ChapterSortSheet(
    current: ChapterSort,
    onSelect: (ChapterSort) -> Unit,
    onDismiss: () -> Unit
) {
    com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.chapter_sort),
                color = KitsugiColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            val options = listOf(
                ChapterSort.NewestFirst to R.string.chapter_sort_newest,
                ChapterSort.NumberDesc to R.string.chapter_sort_number_desc,
                ChapterSort.NumberAsc to R.string.chapter_sort_number_asc,
                ChapterSort.TitleAsc to R.string.chapter_sort_title
            )
            options.forEach { (sort, labelRes) ->
                val selected = sort == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) KitsugiColors.Accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .tvClickable(shape = RoundedCornerShape(14.dp)) {
                            onSelect(sort)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(labelRes),
                        color = if (selected) KitsugiColors.Accent else KitsugiColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = KitsugiColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

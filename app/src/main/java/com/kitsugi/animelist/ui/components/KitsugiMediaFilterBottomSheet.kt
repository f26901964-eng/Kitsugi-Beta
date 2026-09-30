@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

data class KitsugiGenreItem(
    val id: String,
    val emoji: String,
    val name: String,
    val keywords: List<String>
) {
    val displayLabel: String get() = "$emoji $name"
}

val KITSUGI_MEDIA_GENRES = listOf(
    KitsugiGenreItem("ALL", "✨", "Tümü", emptyList()),
    KitsugiGenreItem("DRAMA", "🎭", "Drama", listOf("drama", "dram")),
    KitsugiGenreItem("ACTION", "💥", "Aksiyon", listOf("action", "aksiyon")),
    KitsugiGenreItem("ROMANCE", "💖", "Romantik", listOf("romance", "romantik", "romantizm")),
    KitsugiGenreItem("COMEDY", "😂", "Komedi", listOf("comedy", "komedi")),
    KitsugiGenreItem("FANTASY", "🧙‍♂️", "Fantastik", listOf("fantasy", "fantastik")),
    KitsugiGenreItem("SCIFI", "🚀", "Bilim Kurgu", listOf("sci-fi", "scifi", "science fiction", "bilim kurgu")),
    KitsugiGenreItem("MYSTERY", "🔍", "Gizem", listOf("mystery", "gizem")),
    KitsugiGenreItem("HORROR", "👻", "Korku", listOf("horror", "korku")),
    KitsugiGenreItem("SLICEOFLIFE", "☕", "Günlük Yaşam", listOf("slice of life", "günlük yaşam", "iyileştirici", "iyilestirici")),
    KitsugiGenreItem("ADVENTURE", "🧭", "Macera", listOf("adventure", "macera")),
    KitsugiGenreItem("SUPERNATURAL", "🔮", "Doğaüstü", listOf("supernatural", "doğaüstü", "dogaustu")),
    KitsugiGenreItem("PSYCHOLOGICAL", "🧠", "Psikolojik", listOf("psychological", "psikolojik")),
    KitsugiGenreItem("SPORTS", "⚽", "Spor", listOf("sports", "spor")),
    KitsugiGenreItem("THRILLER", "🕵️‍♂️", "Gerilim", listOf("thriller", "suspense", "gerilim")),
    KitsugiGenreItem("MECHA", "🤖", "Mecha", listOf("mecha", "robot")),
    KitsugiGenreItem("MUSIC", "🎵", "Müzik", listOf("music", "müzik", "muzik")),
    KitsugiGenreItem("HISTORICAL", "📜", "Tarihi", listOf("historical", "tarihi", "tarih")),
    KitsugiGenreItem("MILITARY", "⚔️", "Askeri / Savaş", listOf("military", "askeri", "savaş", "savas")),
    KitsugiGenreItem("SCHOOL", "🎒", "Okul", listOf("school", "okul"))
)

enum class KitsugiGridSortOption(val emoji: String, val title: String) {
    DEFAULT("🏆", "Varsayılan Sıralama"),
    SCORE_DESC("⭐", "Puan: Yüksekten Düşüğe"),
    SCORE_ASC("📉", "Puan: Düşükten Yükseğe"),
    POPULARITY_DESC("👥", "Popülerlik: En Çok Takip"),
    YEAR_DESC("📅", "Yıl: Yeniden Eskiye"),
    YEAR_ASC("⏳", "Yıl: Eskiden Yeniye"),
    TITLE_ASC("🔤", "İsim: A'dan Z'ye"),
    TITLE_DESC("🔠", "İsim: Z'den A'ya");

    val displayLabel: String get() = "$emoji $title"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiMediaFilterBottomSheet(
    initialGenreId: String,
    initialSortOption: KitsugiGridSortOption,
    onDismissRequest: () -> Unit,
    onApply: (genreId: String, sortOption: KitsugiGridSortOption) -> Unit,
    onReset: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    var selectedGenreId by remember { mutableStateOf(initialGenreId) }
    var selectedSortOption by remember { mutableStateOf(initialSortOption) }

    KitsugiSheetOrDialog(onDismiss = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header Row (Sıfırla - Başlık - Uygula)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        selectedGenreId = "ALL"
                        selectedSortOption = KitsugiGridSortOption.DEFAULT
                        onReset()
                        onDismissRequest()
                    }
                ) {
                    Text(
                        text = "Sıfırla",
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Filtre ve Sıralama",
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        onApply(selectedGenreId, selectedSortOption)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Uygula",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // ── 1. SIRALAMA VE AYIRMA ────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sıralama ve Ayırma",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiGridSortOption.entries.forEach { sort ->
                        val isSelected = selectedSortOption == sort
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSortOption = sort }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sort.displayLabel,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── 2. TÜR FİLTRESİ ──────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FilterAlt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tür Filtresi",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KITSUGI_MEDIA_GENRES.forEach { genre ->
                        val isSelected = selectedGenreId == genre.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                    else KitsugiColors.SurfaceSoft
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedGenreId = genre.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = genre.displayLabel,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

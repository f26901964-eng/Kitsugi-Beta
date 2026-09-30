package com.kitsugi.animelist.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/**
 * 7 Katalog Motoruna Özel Kapsamlı Filtreleme Bottom Sheet'i.
 * Seçilen motora uygun ince ayarları (puan, yıl, süre, etiket güven oranı, yaş sınırı vb.) sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceEngineFilterSheet(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onOpenGenresTags: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Başlık & Sıfırla
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${uiState.selectedEngine.emoji} ${uiState.selectedEngine.label} Filtreleri",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = KitsugiColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Arama kriterlerini özelleştirin",
                        style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted)
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetEngineFilters(uiState.selectedEngine) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sıfırla", color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            // Motor Gövdesi
            when (uiState.selectedEngine) {
                SearchSourceEngine.ANILIST -> AniListFullFiltersContent(uiState, viewModel, onOpenGenresTags)
                SearchSourceEngine.MAL -> MalFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.TMDB -> TmdbFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.SHIKIMORI -> ShikimoriFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.KITSU -> KitsuFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.SIMKL -> SimklFullFiltersContent(uiState, viewModel)
                SearchSourceEngine.ALL -> AllFullFiltersContent()
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Uygula Butonu
            Button(
                onClick = {
                    viewModel.search()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = "Sonuçları Göster",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANILIST İÇERİK
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AniListFullFiltersContent(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onOpenGenresTags: () -> Unit
) {
    val filters = uiState.aniListSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // Türler ve Etiketler Seçici
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceElevated)
            .clickable(onClick = onOpenGenresTags)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "🎭 Türler & 🏷️ Etiketler",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                )
            )
            val selectedCount = filters.genres.size + filters.tags.size + filters.excludedGenres.size
            Text(
                text = if (selectedCount > 0) "$selectedCount etiket/tür seçili" else "Tümü seçili (Filtrelemek için dokunun)",
                style = MaterialTheme.typography.bodySmall.copy(color = if (selectedCount > 0) accentColor else KitsugiColors.TextMuted)
            )
        }
        Text("Düzenle ❯", color = accentColor, style = MaterialTheme.typography.labelMedium)
    }

    // Min Tag Rank
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val currentRank = filters.minimumTagRank ?: 18
        Text(
            text = "🏷️ Minimum Etiket Güven Oranı: %$currentRank",
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = currentRank.toFloat(),
            onValueChange = {
                viewModel.updateAniListFilters(filters.copy(minimumTagRank = it.toInt()))
            },
            valueRange = 0f..100f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
    }

    // Min Puan
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val minScore = filters.minScore ?: 0
        Text(
            text = "⭐ Minimum Ortalama Puan: ${if (minScore > 0) "%$minScore" else "Tümü"}",
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = minScore.toFloat(),
            onValueChange = {
                viewModel.updateAniListFilters(filters.copy(minScore = if (it.toInt() == 0) null else it.toInt()))
            },
            valueRange = 0f..100f,
            steps = 9,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // Lisansörler (Licensed By)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("📺 Resmi Yayıncı (Lisans):", style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val platforms = listOf("Crunchyroll", "Netflix", "HIDIVE", "Hulu", "Disney Plus")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            platforms.forEach { lic ->
                val isSelected = filters.licensedBy.contains(lic)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            val newLic = if (isSelected) filters.licensedBy - lic else filters.licensedBy + lic
                            viewModel.updateAniListFilters(filters.copy(licensedBy = newLic))
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(lic, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }

    // 18+ Yetişkin İçerik
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("🔞 Yetişkin İçerik (18+ / Hentai)", style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
            Text("Arama sonuçlarına 18+ yapımları dahil et", style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted))
        }
        Switch(
            checked = filters.isAdult ?: false,
            onCheckedChange = { viewModel.updateAniListFilters(filters.copy(isAdult = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAL İÇERİK
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MalFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.malSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // Min Puan (0..10)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val minScore = filters.minScore ?: 0.0
        Text(
            text = "⭐ Minimum MAL Puanı: ${if (minScore > 0.0) "%.1f+".format(minScore) else "Tümü"}",
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = minScore.toFloat(),
            onValueChange = {
                val sc = (it * 10).toInt() / 10.0
                viewModel.updateMalFilters(filters.copy(minScore = if (sc <= 0.0) null else sc))
            },
            valueRange = 0f..9f,
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // Alfabe Harf Filtresi
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🔤 Baş Harfe Göre Filtrele:", style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M")
            letters.forEach { l ->
                val isSelected = filters.letter == l
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) accentColor else KitsugiColors.SurfaceElevated)
                        .clickable { viewModel.updateMalFilters(filters.copy(letter = if (isSelected) null else l)) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(l, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) Color.White else KitsugiColors.TextSecondary))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TMDB İÇERİK
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TmdbFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.tmdbSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    // Minimum Puan
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val minSc = filters.minScore ?: 0.0
        Text(
            text = "⭐ Minimum TMDB Puanı: ${if (minSc > 0.0) "%.1f+".format(minSc) else "Tümü"}",
            style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary)
        )
        Slider(
            value = minSc.toFloat(),
            onValueChange = {
                val sc = (it * 10).toInt() / 10.0
                viewModel.updateTmdbFilters(filters.copy(minScore = if (sc <= 0.0) null else sc))
            },
            valueRange = 0f..9f,
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
        )
    }

    // Min Oy Sayısı
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("👥 Güvenilirlik (Minimum Oy Sayısı):", style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val counts = listOf(null to "Tümü", 50 to "50+ Oy", 200 to "200+ Oy", 1000 to "1.000+ Oy")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            counts.forEach { (count, label) ->
                val isSelected = filters.minVoteCount == count
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateTmdbFilters(filters.copy(minVoteCount = count)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }

    // 18+ Yetişkin
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔞 Yetişkin İçerik Dahil Et", style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
        Switch(
            checked = filters.includeAdult,
            onCheckedChange = { viewModel.updateTmdbFilters(filters.copy(includeAdult = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHIKIMORI, KITSU, SIMKL, ALL İÇERİKLERİ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShikimoriFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.shikimoriSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("🛡️ Güvenli İçerik / Sansür", style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextPrimary))
            Text("18+ içerikleri filtrele", style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted))
        }
        Switch(
            checked = filters.censored,
            onCheckedChange = { viewModel.updateShikimoriFilters(filters.copy(censored = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun KitsuFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.kitsuSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🏷️ Yaş Sınırı (Age Rating):", style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val ratings = listOf(null to "Tümü", "G" to "Genel (G)", "PG" to "Rehberlik (PG)", "R" to "17+ (R)", "R18" to "18+ (R18)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ratings.forEach { (code, label) ->
                val isSelected = filters.ageRating == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateKitsuFilters(filters.copy(ageRating = code)) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }
}

@Composable
private fun SimklFullFiltersContent(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.simklSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("📅 Trend Dönemi:", style = MaterialTheme.typography.labelMedium.copy(color = KitsugiColors.TextPrimary))
        val periods = listOf("today" to "🔥 Bugün", "week" to "📅 Bu Hafta", "month" to "🗓️ Bu Ay")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            periods.forEach { (p, label) ->
                val isSelected = filters.trendingPeriod == p
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceElevated)
                        .border(1.dp, if (isSelected) accentColor else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSimklFilters(filters.copy(trendingPeriod = p)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) accentColor else KitsugiColors.TextSecondary))
                }
            }
        }
    }
}

@Composable
private fun AllFullFiltersContent() {
    Text(
        text = "🌐 Tümü modunda arama yaparken sorgunuz eşzamanlı olarak AniList, MyAnimeList, TMDB, Shikimori, Kitsu ve Simkl motorlarına iletilir. İnce detaylı filtreler için arama çubuğundan doğrudan hedef motoru seçebilirsiniz.",
        style = MaterialTheme.typography.bodyMedium.copy(color = KitsugiColors.TextSecondary),
        lineHeight = 20.sp
    )
}

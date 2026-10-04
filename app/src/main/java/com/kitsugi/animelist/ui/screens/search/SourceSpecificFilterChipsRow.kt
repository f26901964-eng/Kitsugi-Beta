package com.kitsugi.animelist.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

/**
 * Kaynağa ve alt kapsama özel zengin filtreleme & sıralama kontrol çubuğu.
 * Emojili filtre seçenekleri, sıralama seçici ve daraltılabilir hızlı filtre çipleri içerir.
 */
@Composable
fun SourceSpecificFilterChipsRow(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onOpenFullFilterSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current
    var showSortSheet by remember { mutableStateOf(false) }

    val activeCount = viewModel.getActiveFilterCountForEngine(uiState.selectedEngine)
    val hasFilters = activeCount > 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Üst Kontrol Çubuğu (Sıralama, Filtreler Toggle, Sıfırla) ────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sıralama Çipi
            val currentSortLabel = getSortDisplayLabel(uiState)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(KitsugiColors.SurfaceElevated)
                    .border(1.dp, KitsugiColors.Border, RoundedCornerShape(14.dp))
                    .clickable { showSortSheet = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentSortLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = KitsugiColors.TextPrimary
                        )
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Sırala",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Filtreler Toggle Çipi
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (hasFilters || uiState.showMoreFilters) accentColor.copy(alpha = 0.16f)
                        else KitsugiColors.SurfaceElevated
                    )
                    .border(
                        width = if (hasFilters) 1.5.dp else 1.dp,
                        color = if (hasFilters) accentColor else KitsugiColors.Border,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        viewModel.setShowMoreFilters(!uiState.showMoreFilters)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Filtreler",
                        tint = if (hasFilters) accentColor else KitsugiColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (activeCount > 0) "🎛️ Filtreler ($activeCount)" else "🎛️ Filtreler",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (hasFilters) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasFilters) accentColor else KitsugiColors.TextPrimary
                        )
                    )
                }
            }

            // Gelişmiş / Tam Filtre Butonu
            TextButton(
                onClick = onOpenFullFilterSheet,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "⚙️ Tüm Seçenekler",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Sıfırla Butonu (Eğer filtre varsa)
            if (hasFilters) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(KitsugiColors.SurfaceElevated)
                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(14.dp))
                        .clickable { viewModel.resetEngineFilters(uiState.selectedEngine) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Sıfırla",
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Temizle",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = KitsugiColors.TextMuted
                            )
                        )
                    }
                }
            }
        }

        // ── Daraltılabilir Hızlı Filtre Çipleri ────────────────────────────────
        AnimatedVisibility(
            visible = uiState.showMoreFilters,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            when (uiState.selectedEngine) {
                SearchSourceEngine.ANILIST -> AniListQuickChips(uiState, viewModel)
                SearchSourceEngine.MAL -> MalQuickChips(uiState, viewModel)
                SearchSourceEngine.TMDB -> TmdbQuickChips(uiState, viewModel)
                SearchSourceEngine.SHIKIMORI -> ShikimoriQuickChips(uiState, viewModel)
                SearchSourceEngine.KITSU -> KitsuQuickChips(uiState, viewModel)
                SearchSourceEngine.SIMKL -> SimklQuickChips(uiState, viewModel)
                SearchSourceEngine.ALL -> AllEnginesQuickChips(uiState, viewModel)
            }
        }
    }

    if (showSortSheet) {
        EngineSortPickerSheet(
            engine = uiState.selectedEngine,
            currentSort = getCurrentSortKey(uiState),
            onSelectSort = { sortKey ->
                applyEngineSort(uiState.selectedEngine, sortKey, viewModel)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MOTOR HIZLI ÇİP BİLEŞENLERİ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AniListQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.aniListSpecificFilters
    val accentColor = LocalKitsugiAccent.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Formatlar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val formats = listOf(
                "TV" to "📺 TV Dizi",
                "MOVIE" to "🎬 Film",
                "TV_SHORT" to "⏱️ Kısa Dizi",
                "OVA" to "💿 OVA",
                "ONA" to "🌐 ONA",
                "SPECIAL" to "✨ Özel",
                "MANGA" to "📖 Manga",
                "NOVEL" to "📚 Light Novel",
                "ONE_SHOT" to "🎯 One-Shot"
            )
            formats.forEach { (key, label) ->
                val isSelected = filters.formats.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newFormats = if (isSelected) filters.formats - key else filters.formats + key
                        viewModel.updateAniListFilters(filters.copy(formats = newFormats))
                    }
                )
            }
        }

        // Durumlar & Köken
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "RELEASING" to "🟢 Devam Ediyor",
                "FINISHED" to "🏁 Tamamlandı",
                "NOT_YET_RELEASED" to "⏳ Yakında",
                "CANCELLED" to "🚫 İptal"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.statuses.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newStatuses = if (isSelected) filters.statuses - key else filters.statuses + key
                        viewModel.updateAniListFilters(filters.copy(statuses = newStatuses))
                    }
                )
            }

            // Ülke
            val countries = listOf(
                "JP" to "🇯🇵 Japonya",
                "KR" to "🇰🇷 Kore",
                "CN" to "🇨🇳 Çin"
            )
            countries.forEach { (code, label) ->
                val isSelected = filters.country == code
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateAniListFilters(filters.copy(country = if (isSelected) null else code))
                    }
                )
            }
        }
    }
}

@Composable
private fun MalQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.malSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val formats = listOf(
                "tv" to "📺 TV", "movie" to "🎬 Film", "ova" to "💿 OVA",
                "special" to "✨ Özel", "ona" to "🌐 ONA", "music" to "🎵 Müzik",
                "manga" to "📖 Manga", "novel" to "📚 Light Novel"
            )
            formats.forEach { (key, label) ->
                val isSelected = filters.type == key
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateMalFilters(filters.copy(type = if (isSelected) null else key))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "airing" to "🟢 Devam Ediyor", "complete" to "🏁 Tamamlandı", "upcoming" to "⏳ Yakında"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.status == key
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateMalFilters(filters.copy(status = if (isSelected) null else key))
                    }
                )
            }

            val ratings = listOf(
                "g" to "👶 Tüm Yaşlar (G)",
                "pg" to "🧒 Çocuklar (PG)",
                "pg13" to "🧑 13+ (PG-13)",
                "r17" to "🔞 17+ (R)"
            )
            ratings.forEach { (key, label) ->
                val isSelected = filters.rating == key
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateMalFilters(filters.copy(rating = if (isSelected) null else key))
                    }
                )
            }
        }
    }
}

@Composable
private fun TmdbQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.tmdbSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val types = listOf(
                true to "🎬 Filmler",
                false to "📺 TV Dizileri"
            )
            types.forEach { (isMovie, label) ->
                val isSelected = filters.isMovie == isMovie
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(isMovie = isMovie))
                    }
                )
            }

            val countries = listOf(
                "TR" to "🇹🇷 Türkiye", "US" to "🇺🇸 ABD", "KR" to "🇰🇷 G. Kore",
                "JP" to "🇯🇵 Japonya", "GB" to "🇬🇧 Birleşik Krallık", "FR" to "🇫🇷 Fransa"
            )
            countries.forEach { (code, label) ->
                val isSelected = filters.originCountry == code
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(originCountry = if (isSelected) null else code))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val scores = listOf(
                7.0 to "⭐ 7.0+", 8.0 to "🌟 8.0+", 8.5 to "🏆 8.5+"
            )
            scores.forEach { (minSc, label) ->
                val isSelected = filters.minScore == minSc
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(minScore = if (isSelected) null else minSc))
                    }
                )
            }

            val years = listOf(
                2024 to "📅 2024", 2023 to "📅 2023", 2020 to "📅 2020+"
            )
            years.forEach { (yr, label) ->
                val isSelected = filters.startYear == yr
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        viewModel.updateTmdbFilters(filters.copy(startYear = if (isSelected) null else yr))
                    }
                )
            }
        }
    }
}

@Composable
private fun ShikimoriQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.shikimoriSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val kinds = listOf(
                "tv" to "📺 TV", "movie" to "🎬 Film", "ova" to "💿 OVA",
                "ona" to "🌐 ONA", "manga" to "📖 Manga", "manhwa" to "🇰🇷 Manhwa",
                "light_novel" to "📚 Light Novel"
            )
            kinds.forEach { (key, label) ->
                val isSelected = filters.kinds.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newKinds = if (isSelected) filters.kinds - key else filters.kinds + key
                        viewModel.updateShikimoriFilters(filters.copy(kinds = newKinds))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "ongoing" to "🟢 Devam Ediyor", "released" to "🏁 Tamamlandı", "anons" to "⏳ Yakında"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.statuses.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newStatuses = if (isSelected) filters.statuses - key else filters.statuses + key
                        viewModel.updateShikimoriFilters(filters.copy(statuses = newStatuses))
                    }
                )
            }
        }
    }
}

@Composable
private fun KitsuQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.kitsuSpecificFilters

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val subtypes = listOf(
                "TV" to "📺 TV", "movie" to "🎬 Film", "OVA" to "💿 OVA",
                "ONA" to "🌐 ONA", "special" to "✨ Özel", "manga" to "📖 Manga", "novel" to "📚 Novel"
            )
            subtypes.forEach { (key, label) ->
                val isSelected = filters.subtypes.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newSubs = if (isSelected) filters.subtypes - key else filters.subtypes + key
                        viewModel.updateKitsuFilters(filters.copy(subtypes = newSubs))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf(
                "current" to "🟢 Devam Ediyor", "finished" to "🏁 Tamamlandı", "upcoming" to "⏳ Yakında"
            )
            statuses.forEach { (key, label) ->
                val isSelected = filters.statuses.contains(key)
                QuickFilterToggleChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        val newStats = if (isSelected) filters.statuses - key else filters.statuses + key
                        viewModel.updateKitsuFilters(filters.copy(statuses = newStats))
                    }
                )
            }
        }
    }
}

@Composable
private fun SimklQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    val filters = uiState.simklSpecificFilters

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val periods = listOf(
            "today" to "🔥 Bugün",
            "week" to "📅 Bu Hafta",
            "month" to "🗓️ Bu Ay"
        )
        periods.forEach { (period, label) ->
            val isSelected = filters.trendingPeriod == period
            QuickFilterToggleChip(
                label = label,
                selected = isSelected,
                onClick = {
                    viewModel.updateSimklFilters(filters.copy(trendingPeriod = period))
                }
            )
        }

        val types = listOf(
            "anime" to "⚡ Anime",
            "tv" to "📺 Dizi",
            "movies" to "🎬 Film"
        )
        types.forEach { (typ, label) ->
            val isSelected = filters.subtype == typ
            QuickFilterToggleChip(
                label = label,
                selected = isSelected,
                onClick = {
                    viewModel.updateSimklFilters(filters.copy(subtype = typ))
                }
            )
        }
    }
}

@Composable
private fun AllEnginesQuickChips(uiState: SearchUiState, viewModel: SearchViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "🌐 6 platformun (AniList, MAL, TMDB, Shikimori, Kitsu, Simkl) tüm kaynakları eşzamanlı taranıyor.",
            style = MaterialTheme.typography.bodySmall.copy(color = KitsugiColors.TextMuted),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GENEL YARDIMCI COMPOSABLE VE METODLAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickFilterToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.2f)
                else KitsugiColors.SurfaceElevated.copy(alpha = 0.6f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else KitsugiColors.TextSecondary
            )
        )
    }
}

private fun getSortDisplayLabel(uiState: SearchUiState): String {
    return when (uiState.selectedEngine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> {
            when (uiState.aniListSpecificFilters.sort) {
                "POPULARITY_DESC" -> "👥 Popülerlik"
                "SCORE_DESC" -> "⭐ Puan: Yüksek"
                "TRENDING_DESC" -> "🔥 Trendler"
                "FAVOURITES_DESC" -> "💖 Favoriler"
                "START_DATE_DESC" -> "📅 Yeni Çıkanlar"
                "TITLE_ROMAJI" -> "🔤 İsim: A-Z"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.MAL -> {
            when (uiState.malSpecificFilters.orderBy) {
                "members" -> "👥 Popülerlik"
                "score" -> "⭐ Puan"
                "title" -> "🔤 İsim: A-Z"
                "start_date" -> "📅 Yayın Tarihi"
                "favorites" -> "💖 Favoriler"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.TMDB -> {
            when (uiState.tmdbSpecificFilters.sortBy) {
                "popularity.desc" -> "👥 Popülerlik"
                "vote_average.desc" -> "⭐ Puan"
                "primary_release_date.desc" -> "📅 Yeni Çıkanlar"
                "revenue.desc" -> "💰 Hasılat"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.SHIKIMORI -> {
            when (uiState.shikimoriSpecificFilters.order) {
                "popularity" -> "👥 Popülerlik"
                "ranked" -> "⭐ Puan"
                "aired_on" -> "📅 Yayın Tarihi"
                "name" -> "🔤 İsim"
                else -> "👥 Popülerlik"
            }
        }
        SearchSourceEngine.KITSU -> {
            when (uiState.kitsuSpecificFilters.sort) {
                "trending" -> "🔥 Trendler"
                "-userCount" -> "👥 Popülerlik"
                "-averageRating" -> "⭐ Puan"
                "-createdAt" -> "📅 Yeni Eklenenler"
                else -> "🔥 Trendler"
            }
        }
        SearchSourceEngine.SIMKL -> {
            when (uiState.simklSpecificFilters.sort) {
                "rank" -> "🏆 Sıralama"
                "votes" -> "👥 Oylar"
                "rating" -> "⭐ Puan"
                else -> "🏆 Sıralama"
            }
        }
    }
}

private fun getCurrentSortKey(uiState: SearchUiState): String {
    return when (uiState.selectedEngine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> uiState.aniListSpecificFilters.sort
        SearchSourceEngine.MAL -> uiState.malSpecificFilters.orderBy ?: "members"
        SearchSourceEngine.TMDB -> uiState.tmdbSpecificFilters.sortBy
        SearchSourceEngine.SHIKIMORI -> uiState.shikimoriSpecificFilters.order
        SearchSourceEngine.KITSU -> uiState.kitsuSpecificFilters.sort
        SearchSourceEngine.SIMKL -> uiState.simklSpecificFilters.sort
    }
}

private fun applyEngineSort(engine: SearchSourceEngine, sortKey: String, viewModel: SearchViewModel) {
    when (engine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> {
            viewModel.updateAniListFilters(viewModel.uiState.value.aniListSpecificFilters.copy(sort = sortKey))
        }
        SearchSourceEngine.MAL -> {
            viewModel.updateMalFilters(viewModel.uiState.value.malSpecificFilters.copy(orderBy = sortKey, sortDirection = "desc"))
        }
        SearchSourceEngine.TMDB -> {
            viewModel.updateTmdbFilters(viewModel.uiState.value.tmdbSpecificFilters.copy(sortBy = sortKey))
        }
        SearchSourceEngine.SHIKIMORI -> {
            viewModel.updateShikimoriFilters(viewModel.uiState.value.shikimoriSpecificFilters.copy(order = sortKey))
        }
        SearchSourceEngine.KITSU -> {
            viewModel.updateKitsuFilters(viewModel.uiState.value.kitsuSpecificFilters.copy(sort = sortKey))
        }
        SearchSourceEngine.SIMKL -> {
            viewModel.updateSimklFilters(viewModel.uiState.value.simklSpecificFilters.copy(sort = sortKey))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineSortPickerSheet(
    engine: SearchSourceEngine,
    currentSort: String,
    onSelectSort: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    val sortOptions = when (engine) {
        SearchSourceEngine.ANILIST, SearchSourceEngine.ALL -> listOf(
            "POPULARITY_DESC" to "👥 En Popüler",
            "SCORE_DESC" to "⭐ En Yüksek Puanlı",
            "TRENDING_DESC" to "🔥 Şimdi Trend Olanlar",
            "FAVOURITES_DESC" to "💖 En Çok Favorilenenler",
            "START_DATE_DESC" to "📅 En Yeniler (Yayın Tarihi)",
            "START_DATE_ASC" to "📜 En Eskiler (Klasikler)",
            "TITLE_ROMAJI" to "🔤 İsim: A-Z"
        )
        SearchSourceEngine.MAL -> listOf(
            "members" to "👥 Popülerlik (Üye Sayısı)",
            "score" to "⭐ Puan: Yüksekten Düşüğe",
            "favorites" to "💖 En Çok Favorilenenler",
            "start_date" to "📅 Yayın Tarihi: Yeniden Eskiye",
            "title" to "🔤 İsim: A-Z"
        )
        SearchSourceEngine.TMDB -> listOf(
            "popularity.desc" to "👥 Popülerlik",
            "vote_average.desc" to "⭐ Puan: Yüksekten Düşüğe",
            "primary_release_date.desc" to "📅 Çıkış Tarihi: En Yeniler",
            "revenue.desc" to "💰 Gişe / Hasılat"
        )
        SearchSourceEngine.SHIKIMORI -> listOf(
            "popularity" to "👥 Popülerlik",
            "ranked" to "⭐ Puan Sıralaması",
            "aired_on" to "📅 Yayın Tarihi",
            "name" to "🔤 İsim: A-Z"
        )
        SearchSourceEngine.KITSU -> listOf(
            "trending" to "🔥 Trend Olanlar",
            "-userCount" to "👥 Kullanıcı Sayısı (Popülerlik)",
            "-averageRating" to "⭐ Ortalama Puan",
            "-createdAt" to "📅 En Yeni Eklenenler"
        )
        SearchSourceEngine.SIMKL -> listOf(
            "rank" to "🏆 Genel Sıralama",
            "votes" to "👥 Oy Sayısı",
            "rating" to "⭐ Puan"
        )
    }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "${engine.emoji} Sıralama Seçenekleri",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            sortOptions.forEach { (key, label) ->
                val isSelected = key == currentSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.15f)
                            else KitsugiColors.SurfaceElevated.copy(alpha = 0.4f)
                        )
                        .clickable { onSelectSort(key) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accentColor else KitsugiColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Seçili",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

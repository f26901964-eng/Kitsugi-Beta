package com.kitsugi.animelist.ui.screens.profile.tabs

import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.app.AniListProfileState
import com.kitsugi.animelist.ui.app.RankedStatItem
import com.kitsugi.animelist.ui.screens.profile.*
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.toEnglishGenreForSearch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun AniListStatsTab(
    state: AniListProfileState,
    accentColor: Color,
    statsMediaType: Int,
    statsSubTab: Int,
    scoreDistType: Int,
    lengthDistType: Int,
    releaseYearDistType: Int,
    startYearDistType: Int,
    statSortType: Int,
    onStatsMediaTypeChange: (Int) -> Unit,
    onScoreDistTypeChange: (Int) -> Unit,
    onLengthDistTypeChange: (Int) -> Unit,
    onReleaseYearDistTypeChange: (Int) -> Unit,
    onStartYearDistTypeChange: (Int) -> Unit,
    onStatSortTypeChange: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onFavoriteStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onFavoriteStudioClick: ((studioId: Int, source: String, name: String?, imageUrl: String?) -> Unit)? = null
) {
    val overview = if (statsMediaType == 0) state.animeOverviewStats else state.mangaOverviewStats

    if (statsSubTab == 0) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(KitsugiColors.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Media Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KitsugiColors.SurfaceStrong)
                    .padding(4.dp)
            ) {
                listOf("Anime", "Manga").forEachIndexed { idx, label ->
                    val isSel = statsMediaType == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) accentColor else Color.Transparent)
                            .clickable { onStatsMediaTypeChange(idx) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) KitsugiColors.Background else KitsugiColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (overview != null) {
                // 1. Key Stats Grid (3x2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(stringResource(R.string.profile_stat_total_entries), overview.count.toString())
                        StatCard(if (statsMediaType == 0) stringResource(R.string.profile_stat_episodes_watched) else stringResource(R.string.profile_stat_chapters_read), overview.episodesWatched.toString())
                        StatCard(if (statsMediaType == 0) stringResource(R.string.profile_stat_days_watched_short) else stringResource(R.string.profile_stat_volumes_read), "%.1f".format(overview.daysWatched))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(if (statsMediaType == 0) stringResource(R.string.profile_stat_days_planned) else stringResource(R.string.profile_stat_episodes_planned), "%.1f".format(overview.plannedDaysOrCount))
                        StatCard(stringResource(R.string.profile_stat_average_score), "%.2f".format(overview.meanScore))
                        StatCard(stringResource(R.string.profile_stat_standard_deviation), "%.1f".format(overview.standardDeviation))
                    }
                }

                HorizontalDivider(color = KitsugiColors.SurfaceStrong)

                // 2. Score Distribution
                if (overview.scoreList.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.detail_stat_score), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChipItem(selected = scoreDistType == 0, text = stringResource(R.string.baslik_sayisi), onClick = { onScoreDistTypeChange(0) }, accentColor = accentColor)
                            FilterChipItem(selected = scoreDistType == 1, text = stringResource(R.string.harcanan_sure), onClick = { onScoreDistTypeChange(1) }, accentColor = accentColor)
                        }
                        val mappedStats = overview.scoreList.map { item ->
                            val valFloat = if (scoreDistType == 0) item.count.toFloat() else (item.minutesWatched / 60.0f)
                            item.score.toString() to valFloat
                        }
                        VerticalStatsBar(
                            stats = mappedStats,
                            accentColor = accentColor,
                            mapColorTo = { scoreStr ->
                                val scoreNum = scoreStr.toIntOrNull() ?: 0
                                when (scoreNum) {
                                    in 1..3 -> Color(0xFFE57373)
                                    in 4..5 -> Color(0xFFFFB74D)
                                    in 6..7 -> Color(0xFFFFD54F)
                                    in 8..9 -> Color(0xFF81C784)
                                    10      -> Color(0xFF4FC3F7)
                                    else    -> accentColor
                                }
                            }
                        )
                    }
                }

                // 3. Episode / Chapter Length Distribution
                if (overview.lengthList.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (statsMediaType == 0) stringResource(R.string.ui_stats_episode_count) else stringResource(R.string.ui_stats_volume_episode_count), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChipItem(selected = lengthDistType == 0, text = stringResource(R.string.baslik_sayisi), onClick = { onLengthDistTypeChange(0) }, accentColor = accentColor)
                            FilterChipItem(selected = lengthDistType == 1, text = stringResource(R.string.harcanan_sure), onClick = { onLengthDistTypeChange(1) }, accentColor = accentColor)
                            FilterChipItem(selected = lengthDistType == 2, text = KitsugiStrings.get(R.string.ortalama_puan), onClick = { onLengthDistTypeChange(2) }, accentColor = accentColor)
                        }
                        val mappedLength = overview.lengthList.map { item ->
                            val valFloat = when (lengthDistType) { 0 -> item.count.toFloat(); 1 -> (item.minutesWatched / 60.0f); else -> item.meanScore.toFloat() }
                            item.length to valFloat
                        }
                        VerticalStatsBar(stats = mappedLength, accentColor = accentColor)
                    }
                }

                // 4. Status Distribution
                if (overview.statusList.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.durum_dagilimi), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val statusItems = overview.statusList.map { item ->
                            val (label, color) = when (item.status.uppercase()) {
                                "CURRENT"   -> (if (statsMediaType == 0) stringResource(R.string.profile_status_watching) else stringResource(R.string.profile_status_reading)) to Color(0xFF81C784)
                                "COMPLETED" -> stringResource(R.string.profile_status_completed) to Color(0xFF64B5F6)
                                "PLANNING"  -> (if (statsMediaType == 0) stringResource(R.string.profile_status_plan_to_watch_short) else stringResource(R.string.profile_status_plan_to_read_short)) to Color(0xFFA1887F)
                                "PAUSED"    -> stringResource(R.string.profile_status_on_hold) to Color(0xFFFFB74D)
                                "DROPPED"   -> stringResource(R.string.profile_status_dropped) to Color(0xFFE57373)
                                "REPEATING" -> (if (statsMediaType == 0) stringResource(R.string.profile_status_rewatching) else stringResource(R.string.profile_status_rereading)) to Color(0xFFBA68C8)
                                else        -> stringResource(R.string.profile_status_unknown) to accentColor
                            }
                            Triple(label, item.count, color)
                        }
                        HorizontalStatsBar(stats = statusItems)
                    }
                }

                // 5. Format Distribution
                if (overview.formatList.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tur_dagilimi), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val formatItems = overview.formatList.map { item ->
                            val (label, color) = when (item.format.uppercase()) {
                                "TV"       -> "TV"         to Color(0xFF5C6BC0)
                                "TV_SHORT" -> stringResource(R.string.search_format_tv_short) to Color(0xFF7E57C2)
                                "MOVIE"    -> stringResource(R.string.search_format_movie) to Color(0xFF26A69A)
                                "SPECIAL"  -> stringResource(R.string.search_format_special) to Color(0xFFFFA726)
                                "OVA"      -> "OVA"        to Color(0xFFFF7043)
                                "ONA"      -> "ONA"        to Color(0xFFEC407A)
                                "MUSIC"    -> stringResource(R.string.search_filter_music_video) to Color(0xFFAB47BC)
                                "MANGA"    -> stringResource(R.string.profile_manga_list) to Color(0xFF42A5F5)
                                "NOVEL"    -> stringResource(R.string.search_format_novel) to Color(0xFF8D6E63)
                                "ONE_SHOT" -> "One-Shot"   to Color(0xFF78909C)
                                else       -> item.format  to accentColor
                            }
                            Triple(label, item.count, color)
                        }
                        HorizontalStatsBar(stats = formatItems)
                    }
                }

                // 6. Country Distribution
                if (overview.countryList.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ulke_dagilimi), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val countryItems = overview.countryList.map { item ->
                            val (label, color) = when (item.country.uppercase()) {
                                "JP" -> stringResource(R.string.search_country_japan) to Color(0xFF5C6BC0)
                                "KR" -> stringResource(R.string.search_country_south_korea) to Color(0xFF26A69A)
                                "CN" -> stringResource(R.string.search_country_china) to Color(0xFFFF7043)
                                "TW" -> stringResource(R.string.search_country_taiwan) to Color(0xFFAB47BC)
                                else -> item.country  to accentColor
                            }
                            Triple(label, item.count, color)
                        }
                        HorizontalStatsBar(stats = countryItems)
                    }
                }

                // 7. Release Year Distribution
                if (overview.releaseYearList.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.yayin_yili), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChipItem(selected = releaseYearDistType == 0, text = stringResource(R.string.baslik_sayisi), onClick = { onReleaseYearDistTypeChange(0) }, accentColor = accentColor)
                            FilterChipItem(selected = releaseYearDistType == 1, text = stringResource(R.string.harcanan_sure), onClick = { onReleaseYearDistTypeChange(1) }, accentColor = accentColor)
                            FilterChipItem(selected = releaseYearDistType == 2, text = KitsugiStrings.get(R.string.ortalama_puan), onClick = { onReleaseYearDistTypeChange(2) }, accentColor = accentColor)
                        }
                        val mappedYears = overview.releaseYearList
                            .filter { it.releaseYear > 0 }
                            .sortedBy { it.releaseYear }
                            .map { item ->
                                val valFloat = when (releaseYearDistType) { 0 -> item.count.toFloat(); 1 -> (item.minutesWatched / 60.0f); else -> item.meanScore.toFloat() }
                                item.releaseYear.toString() to valFloat
                            }
                        VerticalStatsBar(stats = mappedYears, accentColor = accentColor)
                    }
                }

                // 8. Watch / Read Year Distribution
                if (overview.startYearList.isNotEmpty()) {
                    HorizontalDivider(color = KitsugiColors.SurfaceStrong)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (statsMediaType == 0) stringResource(R.string.ui_stats_watch_year) else stringResource(R.string.ui_stats_read_year), color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChipItem(selected = startYearDistType == 0, text = stringResource(R.string.baslik_sayisi), onClick = { onStartYearDistTypeChange(0) }, accentColor = accentColor)
                            FilterChipItem(selected = startYearDistType == 1, text = stringResource(R.string.harcanan_sure), onClick = { onStartYearDistTypeChange(1) }, accentColor = accentColor)
                            FilterChipItem(selected = startYearDistType == 2, text = KitsugiStrings.get(R.string.ortalama_puan), onClick = { onStartYearDistTypeChange(2) }, accentColor = accentColor)
                        }
                        val mappedStartYears = overview.startYearList
                            .filter { it.startYear > 0 }
                            .sortedBy { it.startYear }
                            .map { item ->
                                val valFloat = when (startYearDistType) { 0 -> item.count.toFloat(); 1 -> (item.minutesWatched / 60.0f); else -> item.meanScore.toFloat() }
                                item.startYear.toString() to valFloat
                            }
                        VerticalStatsBar(stats = mappedStartYears, accentColor = accentColor)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.istatistik_verisi_hazirlanamadi), color = KitsugiColors.TextMuted)
                }
            }
        }
    } else {
        // Ranked sub-tabs: Genres, Tags, Staff, VAs, Studios
        val currentList: List<RankedStatItem> = when (statsSubTab) {
            1 -> overview?.genreList.orEmpty()
            2 -> overview?.tagList.orEmpty()
            3 -> overview?.staffList.orEmpty()
            4 -> overview?.voiceActorList.orEmpty()
            5 -> overview?.studioList.orEmpty()
            else -> emptyList()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(KitsugiColors.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Media Switcher for tabs 1..3
            if (statsSubTab in 1..3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(4.dp)
                ) {
                    listOf("Anime", "Manga").forEachIndexed { idx, label ->
                        val isSel = statsMediaType == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) accentColor else Color.Transparent)
                                .clickable { onStatsMediaTypeChange(idx) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) KitsugiColors.Background else KitsugiColors.TextMuted,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Sort Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ProfileFilterChip(isSelected = statSortType == 0, text = stringResource(R.string.baslik_sayisi), onClick = { onStatSortTypeChange(0) }, accentColor = accentColor)
                ProfileFilterChip(isSelected = statSortType == 1, text = stringResource(R.string.harcanan_sure), onClick = { onStatSortTypeChange(1) }, accentColor = accentColor)
                ProfileFilterChip(isSelected = statSortType == 2, text = KitsugiStrings.get(R.string.ortalama_puan), onClick = { onStatSortTypeChange(2) }, accentColor = accentColor)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val sortedList = when (statSortType) {
            0    -> currentList.sortedByDescending { it.count }
            1    -> currentList.sortedByDescending { it.timeSpentMinutes ?: it.chaptersRead ?: 0 }
            2    -> currentList.sortedByDescending { it.meanScore }
            else -> currentList
        }

        if (sortedList.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.istatistik_verisi_bulunamadi), color = KitsugiColors.TextMuted)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedList.forEachIndexed { idx, item ->
                    PositionalStatItemCard(
                        rank = idx + 1,
                        title = if (statsSubTab == 1) item.name else item.name,
                        count = item.count,
                        meanScore = item.meanScore,
                        timeSpentMinutes = item.timeSpentMinutes,
                        chaptersRead = item.chaptersRead,
                        imageUrl = item.imageUrl,
                        accentColor = accentColor,
                        onClick = {
                            when (statsSubTab) {
                                1    -> onGenreClick(item.name.toEnglishGenreForSearch())
                                2    -> onTagClick(item.name)
                                3, 4 -> if (item.id != null) onFavoriteStaffClick(item.id, "anilist", item.name, item.imageUrl)
                                5    -> if (item.id != null) onFavoriteStudioClick?.invoke(item.id, "anilist", item.name, item.imageUrl) else onTagClick(item.name)
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    }
}

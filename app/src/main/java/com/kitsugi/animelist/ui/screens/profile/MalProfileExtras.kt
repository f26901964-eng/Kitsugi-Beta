package com.kitsugi.animelist.ui.screens.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.app.MalActivityEntry
import com.kitsugi.animelist.ui.app.MalProfileState
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/*
 * MAL profilinin cihaz üzeri HTML ayrıştırma / miribyou ile gelen ek bölümleri:
 * "Hakkında" bilgileri (cinsiyet, doğum günü, son görülme, hakkında yazısı) ve
 * AniList tarzı "Aktivite" akışı (son güncellemeler + bölüm bölüm geçmiş).
 */

private fun formatLocalizedDate(value: OffsetDateTime, pattern: String): String =
    value.atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, KitsugiStrings.locale()))

private fun parseIso(iso: String?): OffsetDateTime? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** Localized relative timestamp or localized calendar date. */
internal fun malRelativeTime(iso: String?): String? {
    val t = parseIso(iso) ?: return null
    val now = OffsetDateTime.now()
    val d = Duration.between(t, now)
    return when {
        d.isNegative || d.toMinutes() < 1 -> KitsugiStrings.get(R.string.profile_time_just_now)
        d.toMinutes() < 60 -> KitsugiStrings.get(R.string.profile_time_minutes_ago, d.toMinutes())
        d.toHours() < 24 -> KitsugiStrings.get(R.string.profile_time_hours_ago, d.toHours())
        d.toDays() < 2 -> KitsugiStrings.get(R.string.profile_time_yesterday)
        d.toDays() < 7 -> KitsugiStrings.get(R.string.profile_time_days_ago, d.toDays())
        t.year == now.year -> formatLocalizedDate(t, "d MMM")
        else -> formatLocalizedDate(t, "d MMM yyyy")
    }
}

private fun malDate(iso: String?): String? {
    val t = parseIso(iso) ?: return null
    return formatLocalizedDate(t, "d MMMM yyyy")
}

private fun malAge(iso: String?): Int? {
    val birth = parseIso(iso)?.toLocalDate() ?: return null
    val today = LocalDate.now()
    // Yılı gizli doğum günlerinde ayrıştırıcı içinde bulunulan yılı kullanır → yaş anlamsız.
    if (birth.year >= today.year) return null
    var age = today.year - birth.year
    if (today.dayOfYear < birth.dayOfYear) age--
    return age.takeIf { it in 1..120 }
}

/** Localized MAL list status. */
internal fun malStatusLabel(status: String, isManga: Boolean): String = when (status.lowercase(Locale.US)) {
    "watching" -> KitsugiStrings.get(R.string.profile_status_watching)
    "reading" -> KitsugiStrings.get(R.string.profile_status_reading)
    "completed" -> KitsugiStrings.get(R.string.profile_status_completed)
    "on-hold" -> KitsugiStrings.get(R.string.profile_status_on_hold)
    "dropped" -> KitsugiStrings.get(R.string.profile_status_dropped)
    "plan to watch" -> KitsugiStrings.get(R.string.profile_status_plan_to_watch)
    "plan to read" -> KitsugiStrings.get(R.string.profile_status_plan_to_read)
    "rewatching" -> KitsugiStrings.get(R.string.profile_status_rewatching)
    "rereading" -> KitsugiStrings.get(R.string.profile_status_rereading)
    else -> KitsugiStrings.get(if (isManga) R.string.profile_status_reading else R.string.profile_status_watching)
}

private fun genderLabel(raw: String): String = when (raw.lowercase(Locale.US)) {
    "male" -> KitsugiStrings.get(R.string.profile_gender_male)
    "female" -> KitsugiStrings.get(R.string.profile_gender_female)
    "non-binary" -> "Non-binary"
    else -> when {
        raw.equals(KitsugiStrings.get(R.string.profile_gender_male), ignoreCase = true) -> KitsugiStrings.get(R.string.profile_gender_male)
        raw.equals(KitsugiStrings.get(R.string.profile_gender_female), ignoreCase = true) -> KitsugiStrings.get(R.string.profile_gender_female)
        else -> raw
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** "Hakkında" sekmesine eklenen kart: kişisel bilgiler + hakkında yazısı. */
@Composable
fun MalAboutExtras(state: MalProfileState, accentColor: Color) {
    val rows = buildList {
        if (state.gender.isNotBlank()) add(KitsugiStrings.get(R.string.profile_label_gender) to genderLabel(state.gender))
        malDate(state.birthday)?.let { date ->
            val age = malAge(state.birthday)
            add(KitsugiStrings.get(R.string.profile_label_birthday) to if (age != null) "$date ($age)" else date.substringBeforeLast(" "))
        }
        if (state.location.isNotBlank()) add(KitsugiStrings.get(R.string.profile_label_location) to state.location)
        malDate(state.joinedAt)?.let { add(KitsugiStrings.get(R.string.profile_label_joined) to it) }
        malRelativeTime(state.lastOnline)?.let { add(KitsugiStrings.get(R.string.profile_label_last_seen) to it) }
        state.animeStats?.let { s ->
            if (s.minutesWatched > 0) add(KitsugiStrings.get(R.string.profile_label_watch_time) to KitsugiStrings.get(R.string.profile_stat_days_watched_decimal, s.minutesWatched / 60.0 / 24.0))
            if (s.episodesWatched > 0) add(KitsugiStrings.get(R.string.profile_label_episodes_watched) to s.episodesWatched.toString())
        }
        state.mangaStats?.let { s ->
            if (s.episodesWatched > 0) add(KitsugiStrings.get(R.string.profile_label_chapters_read) to s.episodesWatched.toString())
        }
    }

    if (rows.isEmpty() && state.about.isBlank()) {
        if (state.isExtraLoading) LoadingHint()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(KitsugiColors.Surface)
            .padding(18.dp)
    ) {
        if (rows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.profile_label_information),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            rows.forEach { (label, value) -> InfoRow(label, value) }
        }
        if (state.about.isNotBlank()) {
            if (rows.isNotEmpty()) Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.profile_label_about),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(
                text = state.about,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis
            )
            if (state.about.length > 400 || state.about.count { it == '\n' } > 7) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (expanded) stringResource(R.string.profile_show_less) else stringResource(R.string.profile_show_more),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }
    }
}

@Composable
private fun LoadingHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = KitsugiColors.TextMuted)
        Spacer(Modifier.width(10.dp))
        Text(text = stringResource(R.string.profile_mal_loading), color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = KitsugiColors.TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

/** AniList aktivite kartına benzeyen tek satır. */
@Composable
private fun MalActivityCard(
    entry: MalActivityEntry,
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!entry.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = entry.title,
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        malRelativeTime(entry.dateIso)?.let { time ->
            Spacer(Modifier.width(8.dp))
            Text(text = time, color = KitsugiColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun updateText(e: MalActivityEntry): String {
    val label = malStatusLabel(e.status, e.isManga)
    val progress = when {
        e.progress != null && e.total != null && e.total > 0 -> {
            val count = "${e.progress}/${e.total}"
            KitsugiStrings.get(if (e.isManga) R.string.profile_chapter_progress else R.string.profile_episode_progress, count)
        }
        e.progress != null && e.progress > 0 -> {
            KitsugiStrings.get(if (e.isManga) R.string.profile_chapter_progress else R.string.profile_episode_progress, e.progress.toString())
        }
        else -> null
    }
    val score = if (e.score > 0) KitsugiStrings.get(R.string.profile_score_label, e.score.toString()) else null
    return listOfNotNull(label, progress, score).joinToString(" • ")
}

private fun historyText(e: MalActivityEntry): String {
    val n = e.progress
    return when {
        e.isManga && n != null -> KitsugiStrings.get(R.string.profile_chapter_read_history, n)
        n != null -> KitsugiStrings.get(R.string.profile_episode_watched_history, n)
        e.isManga -> KitsugiStrings.get(R.string.profile_status_read)
        else -> KitsugiStrings.get(R.string.profile_status_watched_past)
    }
}

/**
 * "Aktivite" sekmesi. MAL verisi (son güncellemeler / geçmiş) varsa onu gösterir,
 * yoksa [fallback] (yerel liste kayıtları) çizilir.
 */
@Composable
fun MalActivitySection(
    state: MalProfileState,
    accentColor: Color,
    onOpenMedia: (entry: MalActivityEntry) -> Unit,
    fallback: @Composable () -> Unit
) {
    if (state.recentUpdates.isEmpty() && state.history.isEmpty()) {
        if (state.isExtraLoading) LoadingHint() else fallback()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.recentUpdates.isNotEmpty()) {
            SectionTitle(stringResource(R.string.profile_last_updates))
            state.recentUpdates.forEach { e ->
                MalActivityCard(e, updateText(e), accentColor) { onOpenMedia(e) }
            }
        }
        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SectionTitle(stringResource(R.string.profile_history))
            state.history.take(40).forEach { e ->
                MalActivityCard(e, historyText(e), accentColor) { onOpenMedia(e) }
            }
        }
    }
}

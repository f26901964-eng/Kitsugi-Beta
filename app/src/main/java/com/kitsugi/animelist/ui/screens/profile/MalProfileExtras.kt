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

/*
 * MAL profilinin cihaz üzeri HTML ayrıştırma / miribyou ile gelen ek bölümleri:
 * "Hakkında" bilgileri (cinsiyet, doğum günü, son görülme, hakkında yazısı) ve
 * AniList tarzı "Aktivite" akışı (son güncellemeler + bölüm bölüm geçmiş).
 */

private val TR = Locale("tr", "TR")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", TR)
private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", TR)
private val DAY_MONTH_YEAR_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", TR)

private fun parseIso(iso: String?): OffsetDateTime? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** "az önce", "5 dk önce", "3 saat önce", "Dün", "12 Eki", "12 Eki 2024". */
internal fun malRelativeTime(iso: String?): String? {
    val t = parseIso(iso) ?: return null
    val now = OffsetDateTime.now()
    val d = Duration.between(t, now)
    return when {
        d.isNegative || d.toMinutes() < 1 -> "az önce"
        d.toMinutes() < 60 -> "${d.toMinutes()} dk önce"
        d.toHours() < 24 -> "${d.toHours()} saat önce"
        d.toDays() < 2 -> "Dün"
        d.toDays() < 7 -> "${d.toDays()} gün önce"
        t.year == now.year -> t.atZoneSameInstant(ZoneId.systemDefault()).format(DAY_MONTH)
        else -> t.atZoneSameInstant(ZoneId.systemDefault()).format(DAY_MONTH_YEAR)
    }
}

private fun malDate(iso: String?): String? {
    val t = parseIso(iso) ?: return null
    return t.toLocalDate().format(DAY_MONTH_YEAR_LONG)
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

/** MAL durum metnini Türkçeleştirir. */
internal fun malStatusLabel(status: String, isManga: Boolean): String = when (status.lowercase(Locale.US)) {
    "watching" -> "İzliyor"
    "reading" -> "Okuyor"
    "completed" -> "Tamamladı"
    "on-hold" -> "Durdurdu"
    "dropped" -> "Bıraktı"
    "plan to watch" -> "İzleyecek"
    "plan to read" -> "Okuyacak"
    "rewatching" -> "Tekrar izliyor"
    "rereading" -> "Tekrar okuyor"
    else -> if (isManga) "Okuyor" else "İzliyor"
}

private fun genderLabel(raw: String): String = when (raw.lowercase(Locale.US)) {
    "male" -> "Erkek"
    "female" -> "Kadın"
    "non-binary" -> "Non-binary"
    else -> raw
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
        if (state.gender.isNotBlank()) add("Cinsiyet" to genderLabel(state.gender))
        malDate(state.birthday)?.let { date ->
            val age = malAge(state.birthday)
            add("Doğum günü" to if (age != null) "$date ($age)" else date.substringBeforeLast(" "))
        }
        if (state.location.isNotBlank()) add("Konum" to state.location)
        malDate(state.joinedAt)?.let { add("Katılım" to it) }
        malRelativeTime(state.lastOnline)?.let { add("Son görülme" to it) }
        state.animeStats?.let { s ->
            if (s.minutesWatched > 0) add("İzleme süresi" to "%.1f gün".format(s.minutesWatched / 60.0 / 24.0))
            if (s.episodesWatched > 0) add("İzlenen bölüm" to s.episodesWatched.toString())
        }
        state.mangaStats?.let { s ->
            if (s.episodesWatched > 0) add("Okunan bölüm" to s.episodesWatched.toString())
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
                text = "Bilgiler",
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
                text = "Hakkında",
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
                    text = if (expanded) "Daha az göster" else "Devamını göster",
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
        Text(text = "MAL profili yükleniyor…", color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodySmall)
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
    val unit = "bölüm"
    val progress = when {
        e.progress != null && e.total != null && e.total > 0 -> "${e.progress}/${e.total} $unit"
        e.progress != null && e.progress > 0 -> "${e.progress}. $unit"
        else -> null
    }
    val score = if (e.score > 0) "Puan ${e.score}" else null
    return listOfNotNull(label, progress, score).joinToString(" • ")
}

private fun historyText(e: MalActivityEntry): String {
    val n = e.progress
    return when {
        e.isManga && n != null -> "$n. bölümü okudu"
        n != null -> "$n. bölümü izledi"
        e.isManga -> "Okudu"
        else -> "İzledi"
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
            SectionTitle("Son güncellemeler")
            state.recentUpdates.forEach { e ->
                MalActivityCard(e, updateText(e), accentColor) { onOpenMedia(e) }
            }
        }
        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SectionTitle("Geçmiş")
            state.history.take(40).forEach { e ->
                MalActivityCard(e, historyText(e), accentColor) { onOpenMedia(e) }
            }
        }
    }
}

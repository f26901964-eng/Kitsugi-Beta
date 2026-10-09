package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiReleaseDates
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Yayın bilgisinin kaynaktan bağımsız, hazır (ham değerden türetilmiş) hâli.
 *
 * @property title         kart başlığı ("Yaklaşan Yayın" / "Yayın Tarihi" / "Yayın Bilgisi")
 * @property primaryText   en belirgin satır — tarih + (varsa) bölüm ve saat
 * @property targetEpochSec geri sayımın hedefi (bilinmiyorsa null)
 * @property dateOnly      hedef için saat bilinmiyor (yalnız tarih)
 * @property rows          açılır bölümdeki "etiket → değer" satırları
 */
internal data class UpcomingReleaseInfo(
    val title: String,
    val primaryText: String,
    val targetEpochSec: Long?,
    val dateOnly: Boolean,
    val rows: List<Pair<String, String>>
)

/**
 * Detaydaki yayın bilgisini üretir. Yalnızca "yaklaşan" durumlarda (gelecekteki bölüm, gelecekteki
 * başlangıç tarihi veya "henüz yayınlanmadı" durumu) non-null döner.
 *
 * Girdi her kaynaktan gelen aynı alanlardır: [KitsugiMediaDetail.nextAiringEpisode] ("bölüm|epoch"),
 * [KitsugiMediaDetail.startDate] (YYYY-MM-DD / kısmi) ve [KitsugiMediaDetail.status].
 */
internal fun buildUpcomingReleaseInfo(
    detail: KitsugiMediaDetail,
    nowEpochSec: Long = System.currentTimeMillis() / 1000L,
    todayTr: LocalDate = LocalDate.now(KitsugiReleaseDates.TR_ZONE)
): UpcomingReleaseInfo? {
    // 1) Bir sonraki bölüm / yayın anı ("bölüm|epoch"; bölüm -1 → yalnız tarih, TMDB)
    val slot = parseAiringSlot(detail.nextAiringEpisode)?.takeIf { it.second > nowEpochSec }

    // 2) Başlangıç tarihi (tam gün biliniyorsa gelecekte mi?)
    val startParsed = KitsugiReleaseDates.parse(detail.startDate)
    val startFullDate = startParsed?.takeIf { it.hasMonth && it.hasDay }?.date
    val startFuture = startFullDate?.isAfter(todayTr) == true

    // 3) Durum metni (Jikan/AniList/Kitsu/… farklı yazar; Türkçe ya da İngilizce)
    val status = detail.status.orEmpty()
    val notYetAired = listOf("not yet", "not_yet", "henüz", "unreleased", "upcoming", "yakında")
        .any { status.contains(it, ignoreCase = true) }

    if (slot == null && !startFuture && !notYetAired) return null

    val rows = mutableListOf<Pair<String, String>>()
    val title: String
    val primaryText: String
    val targetEpochSec: Long?
    val dateOnly: Boolean

    if (slot != null) {
        val (episode, epochSec) = slot
        dateOnly = KitsugiReleaseDates.isDateOnlyEpoch(epochSec)
        val episodeLabel = episode?.takeIf { it > 0 }?.let { "Bölüm $it · " }.orEmpty()
        title = "Yaklaşan Yayın"
        primaryText = episodeLabel + KitsugiReleaseDates.formatEpochTr(epochSec, dateOnly)
        targetEpochSec = epochSec
        if (episode != null && episode > 0) rows += "Bölüm" to episode.toString()
        rows += "Yayın tarihi" to KitsugiReleaseDates.formatDateWithWeekday(
            java.time.Instant.ofEpochSecond(epochSec).atZone(KitsugiReleaseDates.TR_ZONE).toLocalDate()
        )
        val timeText = KitsugiReleaseDates.formatTimeTr(epochSec, dateOnly)
        if (timeText != null) rows += "Yayın saati (TR)" to timeText
    } else if (startFullDate != null && startFuture) {
        dateOnly = true
        title = "Yayın Tarihi"
        primaryText = KitsugiReleaseDates.formatDateWithWeekday(startFullDate)
        targetEpochSec = startFullDate.atStartOfDay(KitsugiReleaseDates.TR_ZONE).toEpochSecond()
        // Tarih, aşağıdaki "Başlangıç" satırında gösterilir (tekrar etmesin).
    } else {
        dateOnly = true
        title = "Yayın Bilgisi"
        primaryText = "Yayın tarihi henüz açıklanmadı"
        targetEpochSec = null
    }

    val startRaw = detail.startDate
    if (!startRaw.isNullOrBlank()) {
        val formattedStart = KitsugiReleaseDates.formatTr(startRaw)
        if (formattedStart != null && formattedStart != primaryText) rows += "Başlangıç" to formattedStart
    }
    val statusRaw = detail.status
    if (!statusRaw.isNullOrBlank()) rows += "Durum" to statusRaw

    return UpcomingReleaseInfo(
        title = title,
        primaryText = primaryText,
        targetEpochSec = targetEpochSec,
        dateOnly = dateOnly,
        rows = rows
    )
}

/** "bölüm|epoch" → (bölüm?, epoch). Bilinmeyen biçimde null. */
internal fun parseAiringSlot(raw: String?): Pair<Int?, Long>? {
    if (raw.isNullOrBlank() || !raw.contains("|")) return null
    val parts = raw.split("|")
    val episode: Int? = parts.getOrNull(0)?.trim()?.toIntOrNull()
    val epoch: Long = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
    return Pair(episode, epoch)
}

/**
 * Açılır "Yaklaşan Yayın" kartı — tüm kaynaklarda aynı görünür.
 * Kapalıyken: başlık, tarih ve canlı geri sayım. Açıkken: bölüm, tarih, saat (TR), başlangıç, durum.
 */
@Composable
internal fun UpcomingReleaseCard(
    detail: KitsugiMediaDetail,
    modifier: Modifier = Modifier
) {
    val info = remember(detail) { buildUpcomingReleaseInfo(detail) } ?: return
    val accent = LocalKitsugiAccent.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    val target = info.targetEpochSec
    if (target != null) {
        LaunchedEffect(target) {
            while (true) {
                nowSec = System.currentTimeMillis() / 1000L
                val remaining = target - nowSec
                if (remaining <= 0L) break
                delay(
                    when {
                        remaining < 3_600L -> 1_000L
                        remaining < 86_400L -> 10_000L
                        else -> 60_000L
                    }
                )
            }
        }
    }
    val countdown = target?.let {
        KitsugiReleaseDates.countdownText(targetEpochSec = it, nowEpochSec = nowSec, episode = null)
    }

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KitsugiColors.Surface)
            .border(1.dp, accent.copy(alpha = 0.15f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvClickable(shape = shape, onClick = { expanded = !expanded })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = info.primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                )
                if (!countdown.isNullOrBlank()) {
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.AccentOrange
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Ayrıntıları kapat" else "Ayrıntıları aç",
                tint = accent
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                HorizontalDivider(
                    color = KitsugiColors.Background.copy(alpha = 0.5f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                info.rows.forEach { (label, value) ->
                    InfoRow(label = label, value = value)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

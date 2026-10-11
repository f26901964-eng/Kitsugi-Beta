@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.profile

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

// ─────────────────────────────────────────────────────────────────────────────
// ExternalProfileWrapper — not connected / loading / error durumlarını gösterir
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ExternalProfileWrapper(
    isConnected: Boolean,
    isLoading: Boolean,
    error: String?,
    onConnectClick: () -> Unit,
    accentColor: Color,
    platformName: String,
    content: @Composable () -> Unit
) {
    if (!isConnected) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KitsugiPlatformLogo(
                        platformId = platformName,
                        size = 56.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.inline_1_s_account_not_connected_086498f, platformName),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profil_istatistiklerinizi_favorilerinizi_ve_sosyal_aktivitelerin),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    KitsugiButton(
                        onClick = onConnectClick
                    ) {
                        Text(text = stringResource(R.string.hesabi_bagla), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = accentColor)
        }
    } else if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ProfileServiceErrorCard(
                platformName = platformName,
                rawError = error.orEmpty()
            )
        }
    } else {
        content()
    }
}

@Composable
private fun ProfileServiceErrorCard(
    platformName: String,
    rawError: String
) {
    val summary = remember(rawError) { classifyProfileError(rawError) }
    val titleRes = when (summary.kind) {
        ProfileErrorKind.RATE_LIMIT -> R.string.profile_error_rate_limit_title
        ProfileErrorKind.AUTHENTICATION -> R.string.profile_error_auth_title
        ProfileErrorKind.PERMISSION -> R.string.profile_error_permission_title
        ProfileErrorKind.NOT_FOUND -> R.string.profile_error_not_found_title
        ProfileErrorKind.TIMEOUT -> R.string.profile_error_timeout_title
        ProfileErrorKind.SERVER -> R.string.profile_error_server_title
        ProfileErrorKind.NETWORK -> R.string.profile_error_network_title
        ProfileErrorKind.REQUEST_REJECTED -> R.string.profile_error_request_title
        ProfileErrorKind.UNKNOWN -> R.string.profile_error_unknown_title
    }
    val turkishExplanationRes = when (summary.kind) {
        ProfileErrorKind.RATE_LIMIT -> R.string.profile_error_rate_limit_tr
        ProfileErrorKind.AUTHENTICATION -> R.string.profile_error_auth_tr
        ProfileErrorKind.PERMISSION -> R.string.profile_error_permission_tr
        ProfileErrorKind.NOT_FOUND -> R.string.profile_error_not_found_tr
        ProfileErrorKind.TIMEOUT -> R.string.profile_error_timeout_tr
        ProfileErrorKind.SERVER -> R.string.profile_error_server_tr
        ProfileErrorKind.NETWORK -> R.string.profile_error_network_tr
        ProfileErrorKind.REQUEST_REJECTED -> R.string.profile_error_request_tr
        ProfileErrorKind.UNKNOWN -> R.string.profile_error_unknown_tr
    }
    val englishExplanationRes = when (summary.kind) {
        ProfileErrorKind.RATE_LIMIT -> R.string.profile_error_rate_limit_en
        ProfileErrorKind.AUTHENTICATION -> R.string.profile_error_auth_en
        ProfileErrorKind.PERMISSION -> R.string.profile_error_permission_en
        ProfileErrorKind.NOT_FOUND -> R.string.profile_error_not_found_en
        ProfileErrorKind.TIMEOUT -> R.string.profile_error_timeout_en
        ProfileErrorKind.SERVER -> R.string.profile_error_server_en
        ProfileErrorKind.NETWORK -> R.string.profile_error_network_en
        ProfileErrorKind.REQUEST_REJECTED -> R.string.profile_error_request_en
        ProfileErrorKind.UNKNOWN -> R.string.profile_error_unknown_en
    }
    val accentColor = LocalKitsugiAccent.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.AccentRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = stringResource(R.string.profile_error_icon_description),
                        tint = KitsugiColors.AccentRed,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = stringResource(titleRes),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                summary.httpStatusCode?.let { statusCode ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = KitsugiColors.AccentRed.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_error_http_status, statusCode),
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.55f))

            ProfileErrorTranslation(
                languageLabel = stringResource(R.string.profile_error_language_tr),
                explanation = stringResource(turkishExplanationRes),
                accentColor = accentColor
            )
            ProfileErrorTranslation(
                languageLabel = stringResource(R.string.profile_error_language_en),
                explanation = stringResource(englishExplanationRes),
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun ProfileErrorTranslation(
    languageLabel: String,
    explanation: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = languageLabel,
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp).padding(top = 2.dp)
        )
        Text(
            text = explanation,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProfileHeaderIconTabs — ikonlu üst sekme çubuğu
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProfileHeaderIconTabs(
    tabs: List<Pair<ImageVector, String>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KitsugiColors.Surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(if (isSelected) 1.6f else 1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) KitsugiColors.Background else KitsugiColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    if (isSelected) {
                        Text(
                            text = label,
                            color = KitsugiColors.Background,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProfileFilterChip — filtre chip bileşeni
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProfileFilterChip(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accentColor else KitsugiColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterChipItem — ikonlu filtre chip bileşeni (istatistikler için)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FilterChipItem(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    accentColor: Color = LocalKitsugiAccent.current
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentColor.copy(alpha = 0.22f) else KitsugiColors.SurfaceStrong)
            .border(
                width = 1.dp,
                color = if (selected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = if (selected) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatCard — istatistik kart bileşeni
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RowScope.StatCard(
    label: String,
    value: String
) {
    Card(
        modifier = Modifier.weight(1f).padding(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = KitsugiColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VerticalStatsBar — dikey bar grafik (skor, yıl dağılımı vb.)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VerticalStatsBar(
    stats: List<Pair<String, Float>>,
    accentColor: Color = LocalKitsugiAccent.current,
    maxHeightDp: Int = 90,
    modifier: Modifier = Modifier,
    mapColorTo: ((String) -> Color)? = null
) {
    if (stats.isEmpty()) return
    val maxValue = remember(stats) { stats.maxOfOrNull { it.second } ?: 1f }.coerceAtLeast(1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEach { (label, value) ->
            val barHeight = ((value / maxValue) * maxHeightDp).coerceAtLeast(4f).dp
            val barColor = mapColorTo?.invoke(label) ?: accentColor

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(barColor)
                )
                Text(
                    text = label,
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HorizontalStatsBar — yatay dağılım bar grafik
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HorizontalStatsBar(
    stats: List<Triple<String, Int, Color>>,
    modifier: Modifier = Modifier
) {
    if (stats.isEmpty()) return
    val total = remember(stats) { stats.sumOf { it.second } }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stats.forEach { (label, count, color) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KitsugiColors.SurfaceStrong)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$count $label",
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(KitsugiColors.SurfaceStrong)
        ) {
            stats.forEach { (_, count, color) ->
                val weight = count.toFloat() / total.toFloat()
                if (weight > 0) {
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SegmentedDistributionBar — MAL için durum dağılım bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SegmentedDistributionBar(
    watching: Int,
    completed: Int,
    planned: Int,
    paused: Int,
    dropped: Int,
    total: Int,
    accentColor: Color
) {
    if (total <= 0) return

    val wPct = watching.toFloat() / total.toFloat()
    val cPct = completed.toFloat() / total.toFloat()
    val plPct = planned.toFloat() / total.toFloat()
    val paPct = paused.toFloat() / total.toFloat()
    val dPct = dropped.toFloat() / total.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(KitsugiColors.Background)
    ) {
        if (wPct > 0) Box(modifier = Modifier.weight(wPct).fillMaxHeight().background(accentColor))
        if (cPct > 0) Box(modifier = Modifier.weight(cPct).fillMaxHeight().background(KitsugiColors.AccentGreen))
        if (plPct > 0) Box(modifier = Modifier.weight(plPct).fillMaxHeight().background(KitsugiColors.TextMuted))
        if (paPct > 0) Box(modifier = Modifier.weight(paPct).fillMaxHeight().background(KitsugiColors.AccentOrange))
        if (dPct > 0) Box(modifier = Modifier.weight(dPct).fillMaxHeight().background(KitsugiColors.AccentPink))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatItemRow — MAL stats satır bileşeni
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatItemRow(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val pct = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$count (${"%.1f".format(pct * 100f)}%)",
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = KitsugiColors.Background
        )
    }
}

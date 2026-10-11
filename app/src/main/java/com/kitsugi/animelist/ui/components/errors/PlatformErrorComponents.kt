package com.kitsugi.animelist.ui.components.errors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import com.kitsugi.animelist.data.diagnostics.PlatformErrorSeverity
import com.kitsugi.animelist.ui.theme.KitsugiColors

/** Small reusable code chip for Manga Source and CloudStream diagnostics. */
@Composable
fun PlatformErrorCodeHint(
    code: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val definition = PlatformErrorCode.fromId(code) ?: return
    val tint = definition.severity.tint()
    val title = stringResource(definition.titleRes)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .semantics { contentDescription = "${definition.id}: $title" }
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = definition.id,
            color = tint,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (!compact) {
            Text(
                text = title,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Shared, localized explanation for a known platform error. The raw provider message remains
 * visible as diagnostic evidence, while the cause/action text is stable and safe to translate.
 */
@Composable
fun PlatformErrorExplanationBlock(
    code: String?,
    modifier: Modifier = Modifier,
    rawReason: String? = null,
    cooldownUntil: Long? = null,
    now: Long = System.currentTimeMillis(),
) {
    val definition = PlatformErrorCode.fromId(code) ?: return
    val tint = definition.severity.tint()
    val remainingMs = (cooldownUntil ?: 0L) - now
    val remainingText = if (remainingMs > 0L) formatRemainingCooldown(remainingMs) else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.07f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformErrorCodeHint(code = definition.id)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(definition.titleRes),
                color = KitsugiColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ExplanationLine(
            label = stringResource(R.string.platform_error_cause_label),
            value = stringResource(definition.causeRes),
        )
        ExplanationLine(
            label = stringResource(R.string.platform_error_action_label),
            value = stringResource(definition.actionRes),
        )
        if (remainingText != null) {
            Text(
                text = stringResource(R.string.platform_error_cooldown_remaining, remainingText),
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (!rawReason.isNullOrBlank()) {
            ExplanationLine(
                label = stringResource(R.string.platform_error_raw_reason_label),
                value = rawReason,
                subdued = true,
            )
        }
    }
}

@Composable
private fun ExplanationLine(
    label: String,
    value: String,
    subdued: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = if (subdued) KitsugiColors.TextSecondary else KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

private fun PlatformErrorSeverity.tint(): Color = when (this) {
    PlatformErrorSeverity.Info -> Color(0xFF64B5F6)
    PlatformErrorSeverity.Warning -> Color(0xFFFFB74D)
    PlatformErrorSeverity.Error -> KitsugiColors.AccentRed
}

@Composable
private fun formatRemainingCooldown(remainingMs: Long): String {
    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    val hours = (totalMinutes / 60L).toInt()
    val minutes = (totalMinutes % 60L).toInt()
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.platform_error_duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.platform_error_duration_hours, hours)
        else -> stringResource(R.string.platform_error_duration_minutes, totalMinutes.toInt())
    }
}

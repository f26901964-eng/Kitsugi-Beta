package com.kitsugi.animelist.ui.components.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.accentBrushWithAlpha
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable

internal enum class PluginInstallState { IDLE, LOADING, SUCCESS, FAILURE }

internal data class AddonSubsectionTab(
    val label: String,
    val icon: ImageVector
)

/** Compact, shared repo/installed selector. Its selected state follows the active accent, including gradients. */
@Composable
internal fun AddonSubsectionTabs(
    tabs: List<AddonSubsectionTab>,
    selectedIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(14.dp)
    val selectedShape = RoundedCornerShape(11.dp)
    val selectedFill = accentBrushWithAlpha(0.14f)
    val selectedOutline = accentBrushWithAlpha(0.48f)

    Row(
        modifier = modifier
            .clip(containerShape)
            .background(KitsugiColors.SurfaceSoft, containerShape)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.65f), containerShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(selectedShape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(selectedFill, selectedShape)
                                .border(1.dp, selectedOutline, selectedShape)
                        } else {
                            Modifier
                        }
                    )
                    .tvClickable(shape = selectedShape) { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else KitsugiColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = tab.label,
                    color = if (selected) accentColor else KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun formatFileSize(size: Long?): String {
    if (size == null) return ""
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

@Composable
internal fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentColor.copy(alpha = 0.2f) else KitsugiColors.SurfaceSoft)
            .tvClickable(shape = RoundedCornerShape(12.dp)) { onClick() }
            .border(
                width = 1.dp,
                color = if (selected) accentColor else KitsugiColors.Border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (selected) accentColor else KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

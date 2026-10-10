package com.kitsugi.animelist.ui.screens.fullscreen.controls.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.accentBrushWithAlpha

@Composable
fun AutoPlaySwitch(
    isChecked: Boolean,
    onToggleAutoPlay: ((Boolean) -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val thumbSize = 20.dp
    val trackWidth = 36.dp
    val trackHeight = 12.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (isChecked) trackWidth - thumbSize else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "thumbOffset",
    )

    val activeTrackBrush = accentBrushWithAlpha(0.38f)
    val activeThumbBrush = KitsugiColors.AccentBrush
    val iconColor = if (isChecked) KitsugiColors.OnAccent else Color(0xCC000000)
    val autoplayDescription = "Otomatik Oynat"

    Box(
        modifier = modifier
            .size(width = trackWidth, height = thumbSize)
            .semantics { contentDescription = autoplayDescription }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = { (onToggleAutoPlay ?: onCheckedChange)?.invoke(!isChecked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track
        Canvas(modifier = Modifier.size(width = trackWidth, height = trackHeight)) {
            if (isChecked) {
                drawRoundRect(
                    brush = activeTrackBrush,
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            } else {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.3f),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            }
        }

        // Thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .then(
                    if (isChecked) Modifier.background(activeThumbBrush, CircleShape)
                    else Modifier.background(Color.White.copy(alpha = 0.9f), CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Icon inside thumb (Play or Pause)
            Canvas(modifier = Modifier.size(10.dp)) {
                if (isChecked) {
                    // Draw Play triangle with rounded corners
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.15f)
                        lineTo(size.width * 0.85f, size.height * 0.5f)
                        lineTo(size.width * 0.25f, size.height * 0.85f)
                        close()
                    }
                    withTransform({
                        translate(left = size.width * 0.05f)
                    }) {
                        drawPath(path = path, color = iconColor)
                        drawPath(
                            path = path,
                            color = iconColor,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                } else {
                    // Draw Pause bars
                    val barWidth = size.width * 0.15f
                    val barHeight = size.height * 0.7f
                    val topOffset = (size.height - barHeight) / 2f

                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(size.width * 0.2f, topOffset),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(size.width * 0.65f, topOffset),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
            }
        }
    }
}

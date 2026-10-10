package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ripple
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.delay

/**
 * Uiverse (vinodjangid07) referanslı, animasyonlu genişleyen silme butonu.
 *
 * Normal durumda kompakt yuvarlak koyu bir buton ve kırmızı çöp kutusu ikonudur.
 * Tıklandığında / hover / TV focus durumunda yumuşak bir animasyonla kapsüle genişler,
 * kırmızıya döner ve Türkçe/İngilizce "Sil" / "Delete" metnini gösterir.
 *
 * @param onDelete          Silme işlemi onaylandığında çalışacak callback.
 * @param modifier          Dış düzenleyici modifier.
 * @param size              Normal durumdaki dairesel boyut (varsayılan: 36.dp).
 * @param expandedWidth     Genişlemiş durumdaki genişlik (varsayılan: 92.dp).
 * @param requireConfirm    Dokunmatik ekranlarda güvenli 2 aşamalı silme onayı (varsayılan: true).
 *                          İlk tıklamada "Sil" olarak genişler, 2. tıklamada siler.
 *                          3.5 saniye içinde basılmazsa kendiliğinden kapanır.
 * @param contentDescription Erişilebilirlik açıklaması.
 */
@Composable
fun KitsugiAnimatedDeleteButton(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    expandedWidth: Dp = 92.dp,
    requireConfirm: Boolean = true,
    contentDescription: String? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val activeState = isExpanded || isHovered || isFocused

    // 2 aşamalı onayda açık kaldıysa 3.5 sn sonra otomatik geri kapanır
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(3500)
            isExpanded = false
        }
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (activeState) expandedWidth else size,
        animationSpec = tween(durationMillis = 300),
        label = "deleteBtnWidth"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (activeState) Color(0xFFFF3B3B) else KitsugiColors.SurfaceSoft,
        animationSpec = tween(durationMillis = 280),
        label = "deleteBtnBg"
    )

    val iconTint by animateColorAsState(
        targetValue = if (activeState) Color.White else KitsugiColors.AccentRed,
        animationSpec = tween(durationMillis = 250),
        label = "deleteBtnIconTint"
    )

    val cornerRadius = size / 2
    val shape = RoundedCornerShape(cornerRadius)
    val deleteText = stringResource(R.string.action_delete)

    Box(
        modifier = modifier
            .width(animatedWidth)
            .height(size)
            .clip(shape)
            .shadow(if (activeState) 8.dp else 2.dp, shape = shape, spotColor = if (activeState) Color(0x66FF3B3B) else Color.Transparent)
            .background(animatedBgColor)
            .border(
                width = 1.dp,
                color = if (activeState) Color(0xFFFF6B6B).copy(alpha = 0.6f) else KitsugiColors.Border.copy(alpha = 0.45f),
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                onClick = {
                    if (requireConfirm) {
                        if (isExpanded) {
                            isExpanded = false
                            onDelete()
                        } else {
                            isExpanded = true
                        }
                    } else {
                        onDelete()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = contentDescription ?: deleteText,
                tint = iconTint,
                modifier = Modifier.size((size.value * 0.52f).dp)
            )

            AnimatedVisibility(
                visible = activeState,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = deleteText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

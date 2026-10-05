package com.kitsugi.animelist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Uiverse.io (Galahhad) tasarımı "Day & Night Sun-Moon Theme Switch" bileşeni.
 * Android dokunmatik ekranlar ve TV için özel yaylı fizik (spring bounce) ve
 * dokunmatik ergonomiyle uyarlanmıştır.
 *
 * @param checked true ise Gece (Koyu / Ay / Yıldızlar), false ise Gündüz (Açık / Güneş / Bulutlar)
 * @param onCheckedChange Tema durumu değiştiğinde tetiklenir
 * @param height Butonun yüksekliği (Genişlik altın orana göre height * 2.25 olarak otomatik hesaplanır)
 */
@Composable
fun KitsugiThemeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 32.dp
) {
    val haptic = LocalHapticFeedback.current
    val width = height * 2.25f // Uiverse CSS oranı: 5.625em / 2.5em = 2.25

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Dokunma anında yaylı küçülme (tactile press bounce)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ThemeSwitchPressScale"
    )

    // Gündüz <-> Gece animasyon ilerlemesi (0f = Gündüz, 1f = Gece)
    // CSS'teki cubic-bezier(0, -0.02, 0.4, 1.25) yaylı overshoot hissi
    val animProgress by animateFloatAsState(
        targetValue = if (checked) 1.0f else 0.0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ThemeSwitchAnimProgress"
    )

    // Gövde Arka Plan Rengi: Gündüz gökyüzü mavisi (#3D7EAE) -> Gece lacivert gökyüzü (#1D1F2C)
    val containerBgColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF1D1F2C) else Color(0xFF3D7EAE),
        animationSpec = tween(400),
        label = "ThemeSwitchBgColor"
    )

    // Güneş / Ay Renkleri
    val sunColor = Color(0xFFECCA2F)
    val moonColor = Color(0xFFC4C9D1)
    val craterColor = Color(0xFF959DB1)
    val cloudsFront = Color(0xFFF3FDFF)
    val cloudsBack = Color(0xFFAACADF)
    val starColor = Color(0xFFFFFFFF)

    Box(
        modifier = modifier
            .scale(pressScale)
            .size(width = width, height = height)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = h / 2f

            // ── 1. Ana Kapsayıcı Gövde ──
            drawRoundRect(
                color = containerBgColor,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(radius, radius)
            )

            // Üst & Alt hafif derinlik çizgileri
            drawRoundRect(
                color = Color(0x33000000),
                topLeft = Offset(0f, 1f),
                size = Size(w, h),
                cornerRadius = CornerRadius(radius, radius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )

            // Tüm iç elemanları yuvarlak kapsayıcı içinde tut (overflow: hidden)
            clipRect(0f, 0f, w, h) {
                // ── 2. Gece Yıldızları (animProgress ile yukarıdan aşağı süzülür) ──
                val starOffsetY = lerp(-h * 0.8f, 0f, animProgress)
                val starAlpha = animProgress.coerceIn(0f, 1f)

                if (starAlpha > 0.01f) {
                    // Büyük 4-köşeli parıltı yıldızı
                    drawSparkleStar(
                        center = Offset(w * 0.22f, h * 0.35f + starOffsetY),
                        radius = h * 0.16f,
                        color = starColor,
                        alpha = starAlpha
                    )

                    // Orta 4-köşeli parıltı yıldızı
                    drawSparkleStar(
                        center = Offset(w * 0.40f, h * 0.65f + starOffsetY),
                        radius = h * 0.12f,
                        color = starColor,
                        alpha = starAlpha * 0.9f
                    )

                    // Küçük nokta yıldızlar
                    drawCircle(
                        color = starColor.copy(alpha = starAlpha * 0.8f),
                        radius = h * 0.035f,
                        center = Offset(w * 0.14f, h * 0.68f + starOffsetY)
                    )
                    drawCircle(
                        color = starColor.copy(alpha = starAlpha * 0.85f),
                        radius = h * 0.04f,
                        center = Offset(w * 0.32f, h * 0.22f + starOffsetY)
                    )
                    drawCircle(
                        color = starColor.copy(alpha = starAlpha * 0.75f),
                        radius = h * 0.03f,
                        center = Offset(w * 0.46f, h * 0.36f + starOffsetY)
                    )
                }

                // ── 3. Gündüz Bulutları (animProgress ile aşağı doğru batar) ──
                val cloudOffsetY = lerp(0f, h * 1.3f, animProgress)
                val cloudAlpha = (1f - animProgress * 1.2f).coerceIn(0f, 1f)

                if (cloudAlpha > 0.01f) {
                    // Arka Bulutlar (#AACADF)
                    drawCircle(
                        color = cloudsBack.copy(alpha = cloudAlpha),
                        radius = h * 0.32f,
                        center = Offset(w * 0.58f, h * 0.96f + cloudOffsetY)
                    )
                    drawCircle(
                        color = cloudsBack.copy(alpha = cloudAlpha),
                        radius = h * 0.38f,
                        center = Offset(w * 0.72f, h * 0.90f + cloudOffsetY)
                    )
                    drawCircle(
                        color = cloudsBack.copy(alpha = cloudAlpha),
                        radius = h * 0.32f,
                        center = Offset(w * 0.88f, h * 0.94f + cloudOffsetY)
                    )

                    // Ön Bulutlar (#F3FDFF)
                    drawCircle(
                        color = cloudsFront.copy(alpha = cloudAlpha),
                        radius = h * 0.34f,
                        center = Offset(w * 0.46f, h * 1.10f + cloudOffsetY)
                    )
                    drawCircle(
                        color = cloudsFront.copy(alpha = cloudAlpha),
                        radius = h * 0.40f,
                        center = Offset(w * 0.64f, h * 1.04f + cloudOffsetY)
                    )
                    drawCircle(
                        color = cloudsFront.copy(alpha = cloudAlpha),
                        radius = h * 0.44f,
                        center = Offset(w * 0.80f, h * 1.00f + cloudOffsetY)
                    )
                    drawCircle(
                        color = cloudsFront.copy(alpha = cloudAlpha),
                        radius = h * 0.36f,
                        center = Offset(w * 0.94f, h * 1.06f + cloudOffsetY)
                    )
                }

                // ── 4. Güneş / Ay Küresi ve Dış Hale ──
                val orbDiameter = h * (2.125f / 2.5f) // 0.85 * h
                val orbRadius = orbDiameter / 2f
                val inset = (h - orbDiameter) / 2f
                val travelDist = w - orbDiameter - (inset * 2)
                val orbCenterX = inset + orbRadius + (travelDist * animProgress)
                val orbCenterY = h / 2f
                val orbCenter = Offset(orbCenterX, orbCenterY)

                // Dış Işık Halesi (Aura Rings - rgba(255, 255, 255, 0.1))
                val haloRadius = h * (3.375f / 2.5f) / 2f // 0.675 * h
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = haloRadius * 1.25f,
                    center = orbCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = haloRadius,
                    center = orbCenter
                )

                // Küre Arka Planı (Güneş sarısı -> Ay grisi geçişi)
                val orbBgColor = lerpColor(sunColor, moonColor, animProgress)
                drawCircle(
                    color = orbBgColor,
                    radius = orbRadius,
                    center = orbCenter
                )

                // Güneş/Ay 3D Kenar Işığı (Üst-sol parıltı & alt-sağ gölge)
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = orbRadius - 0.7f,
                    center = Offset(orbCenterX - 0.8f, orbCenterY - 0.8f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
                )

                // ── Ay Kraterleri (CSS .theme-switch__moon translateX mantığı) ──
                // animProgress ile sağdan sola kürenin üzerine kayar
                if (animProgress > 0.01f) {
                    val moonOffset = (1f - animProgress) * orbDiameter

                    // Kraterleri sadece küre içinde sınırla
                    val craterClipPath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center = orbCenter,
                                radius = orbRadius
                            )
                        )
                    }

                    clipPath(craterClipPath) {
                        // 1. Büyük Krater (Sol-alt)
                        val c1 = Offset(
                            orbCenterX - (orbRadius * 0.30f) + moonOffset,
                            orbCenterY + (orbRadius * 0.12f)
                        )
                        drawCircle(
                            color = craterColor.copy(alpha = animProgress),
                            radius = orbRadius * 0.36f,
                            center = c1
                        )
                        drawCircle(
                            color = Color(0x33000000),
                            radius = orbRadius * 0.36f,
                            center = c1,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                        )

                        // 2. Orta Krater (Sağ-alt)
                        val c2 = Offset(
                            orbCenterX + (orbRadius * 0.35f) + moonOffset,
                            orbCenterY + (orbRadius * 0.32f)
                        )
                        drawCircle(
                            color = craterColor.copy(alpha = animProgress),
                            radius = orbRadius * 0.18f,
                            center = c2
                        )

                        // 3. Küçük Krater (Üst-orta)
                        val c3 = Offset(
                            orbCenterX + (orbRadius * 0.05f) + moonOffset,
                            orbCenterY - (orbRadius * 0.40f)
                        )
                        drawCircle(
                            color = craterColor.copy(alpha = animProgress),
                            radius = orbRadius * 0.12f,
                            center = c3
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4-köşeli parıltı yıldızı çizimi
 */
private fun DrawScope.drawSparkleStar(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    if (alpha <= 0.01f) return
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}

/**
 * İki Compose Rengi arasında akıcı lineer geçiş
 */
private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = lerp(start.red, stop.red, f),
        green = lerp(start.green, stop.green, f),
        blue = lerp(start.blue, stop.blue, f),
        alpha = lerp(start.alpha, stop.alpha, f)
    )
}

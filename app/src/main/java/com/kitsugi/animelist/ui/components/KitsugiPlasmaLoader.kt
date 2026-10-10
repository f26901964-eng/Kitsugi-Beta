package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Uiverse (andrew-manzyk) referanslı, organik sıvı lav/plazma küresi (Amber Plasma Lava Orb)
 * yüklenme animasyonu bileşeni.
 *
 * Standart dönen yuvarlak `CircularProgressIndicator` yerine kullanılır.
 * 7 farklı eksende zıt yönlerde dönen plazma odakları (metaball simülasyonu),
 * 6 saniyelik kehribar-lav renk döngüsü (colorize hue-shift), dış magma ışıması ve
 * derinlik gradyanları ile büyüleyici bir yüklenme efekti sunar.
 *
 * @param modifier  Bileşenin dış modifier'ı.
 * @param size      Kürenin çap boyutu (varsayılan: 42.dp).
 * @param color     İsteğe bağlı birincil renk (null bırakılırsa Uiverse #FFBF48 amber kullanılır).
 * @param secondaryColor İsteğe bağlı ikincil renk (null bırakılırsa Uiverse #BE4A1D magma kırmızısı kullanılır).
 */
@Composable
fun KitsugiPlasmaLoader(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    color: Color? = null,
    secondaryColor: Color? = null
) {
    val baseOne = color ?: com.kitsugi.animelist.ui.theme.LocalKitsugiAccent.current
    val baseTwo = secondaryColor ?: com.kitsugi.animelist.ui.theme.LocalKitsugiAccent2.current ?: Color(0xFFBE4A1D)

    val transition = rememberInfiniteTransition(label = "kitsugiPlasmaAnim")

    // 1. Ana Dönüş (2.0s loop - Uiverse --time-animation)
    val mainRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mainRot"
    )

    // 2. Renk Döngüsü (6.0s loop - Uiverse colorize calc(2s * 3))
    // 0deg -> -30deg -> -60deg -> -90deg -> -45deg -> 0deg
    val colorPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorPhase"
    )

    // 3. Akışkan Çekirdek Nabzı (1.0s loop - Uiverse roundness calc(2s / 2))
    val coreGlowPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    // Uiverse renklerinin dinamik renk geçişi (Amber altın <-> Derin kor ateşi)
    val c1 = remember(baseOne, colorPhase) {
        interpolateColor(baseOne, Color(0xFFFF8A3D), colorPhase)
    }
    val c2 = remember(baseTwo, colorPhase) {
        interpolateColor(baseTwo, Color(0xFF8E1B00), colorPhase)
    }
    val c3 = c1.copy(alpha = 0.50f)
    val c4 = c2.copy(alpha = 0.55f)
    val c5 = c1.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val px = size.toPx()
                // CSS: box-shadow: 0 0 25px 0 var(--color-three), 0 20px 50px 0 var(--color-four)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c3.copy(alpha = 0.65f * coreGlowPulse.coerceIn(0.8f, 1.2f)),
                            c4.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = px * 0.95f
                    ),
                    radius = px * 0.95f,
                    center = center
                )

                // Alt lav derinlik düşüm ışığı (0 20px 50px)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c4.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(center.x, center.y + px * 0.20f),
                        radius = px * 0.85f
                    ),
                    radius = px * 0.85f,
                    center = Offset(center.x, center.y + px * 0.20f)
                )
            }
            .clip(CircleShape)
            // CSS: .loader::before { background: linear-gradient(180deg, var(--color-five), var(--color-four)); }
            .background(
                Brush.verticalGradient(
                    colors = listOf(c5, c4)
                )
            )
            // CSS: border-top: solid 1px var(--color-one); border-bottom: solid 1px var(--color-two);
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(c1.copy(alpha = 0.92f), c2.copy(alpha = 0.92f))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val centerOffset = Offset(w / 2f, h / 2f)

            // CSS Uiverse 7 Rotating Polygons / Metaballs:
            // 1. Dönen Arka Plan Gradye Kutusu (.loader .box)
            drawRect(
                brush = Brush.verticalGradient(
                    0.30f to c1.copy(alpha = 0.85f),
                    0.70f to c2.copy(alpha = 0.85f)
                )
            )

            // Poligon 2: origin: 50% 50%, reverse
            rotate(-mainRotation, pivot = centerOffset) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c1, Color.Transparent),
                        center = centerOffset,
                        radius = w * 0.45f
                    ),
                    radius = w * 0.45f,
                    center = centerOffset,
                    blendMode = BlendMode.Screen
                )
            }

            // Poligon 3: origin: 50% 60%, rotation forward, delay -1/3 (120deg phase)
            val p3Angle = mainRotation + 120f
            val p3Pivot = Offset(w * 0.50f, h * 0.60f)
            rotate(p3Angle, pivot = p3Pivot) {
                val rad = Math.toRadians(p3Angle.toDouble())
                val orbitX = (w * 0.50f + cos(rad) * (w * 0.16f)).toFloat()
                val orbitY = (h * 0.60f + sin(rad) * (h * 0.16f)).toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(orbitX, orbitY),
                        radius = w * 0.36f
                    ),
                    radius = w * 0.36f,
                    center = Offset(orbitX, orbitY),
                    blendMode = BlendMode.Screen
                )
            }

            // Poligon 4 & 5: origin: 40% 40%, reverse, phase delays
            val p4Angle = -mainRotation + 60f
            val p4Pivot = Offset(w * 0.40f, h * 0.40f)
            rotate(p4Angle, pivot = p4Pivot) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c2, Color.Transparent),
                        center = Offset(w * 0.38f, h * 0.38f),
                        radius = w * 0.38f
                    ),
                    radius = w * 0.38f,
                    center = Offset(w * 0.38f, h * 0.38f),
                    blendMode = BlendMode.Plus
                )
            }

            // Poligon 6 & 7: origin: 60% 40%, forward, phase delays
            val p6Angle = mainRotation + 240f
            val p6Pivot = Offset(w * 0.60f, h * 0.40f)
            rotate(p6Angle, pivot = p6Pivot) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(w * 0.62f, h * 0.38f),
                        radius = w * 0.35f
                    ),
                    radius = w * 0.35f,
                    center = Offset(w * 0.62f, h * 0.38f),
                    blendMode = BlendMode.Screen
                )
            }

            // Akkor Çekirdek (Merkez sıvı plazma nabzı)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c1,
                        c2.copy(alpha = 0.7f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = (w * 0.32f) * coreGlowPulse
                ),
                radius = (w * 0.32f) * coreGlowPulse,
                center = centerOffset
            )

            // CSS .loader::before İç Gölgeler (inset 0 10px 10px c3, inset 0 -10px 10px c4)
            // Üst parlak hilal
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(c1.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.35f
                ),
                radius = w * 0.50f,
                center = centerOffset
            )
            // Alt koyu lav gölgesi
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, c2.copy(alpha = 0.45f)),
                    startY = h * 0.65f,
                    endY = h
                ),
                radius = w * 0.50f,
                center = centerOffset
            )
        }
    }
}

/**
 * Renkler arasında yumuşak geçiş hesaplar (Hue-shift/Colorize interpolasyonu için).
 */
private fun interpolateColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}

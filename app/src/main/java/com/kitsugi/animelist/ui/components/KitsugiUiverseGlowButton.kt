package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.utils.tvClickable
import kotlin.math.cos
import kotlin.math.max

/**
 * Uiverse.io (Ashon-G) tasarımı baz alınarak uyarlanan, akışkan renkli metaball küreleri
 * ve ışıltılı radyal gradyan katmanına sahip lüks animasyonlu buton bileşeni.
 */
data class UiverseButtonPalette(
    val radialInner: Color,
    val radialOuter: Color,
    val color1: Color,
    val color2: Color,
    val color3: Color,
    val color4: Color,
    val shadowColor: Color,
    val shadowInsetTop: Color,
    val shadowInsetBottom: Color,
    val contentColor: Color
) {
    companion object {
        /**
         * Uiverse Ashon-G orijinal renk paleti (Kehribar, Elektrik Mavisi, Canlı Macenta ve Güneş Sarısı).
         */
        val Default = UiverseButtonPalette(
            radialInner = Color(0xFFFFD215),
            radialOuter = Color(0xFFFFF172),
            color1 = Color(0xB3FFA31A), // rgba(255, 163, 26, 0.7)
            color2 = Color(0xFF1A23FF), // #1a23ff
            color3 = Color(0xFFE21BDA), // #e21bda
            color4 = Color(0xB3FFE81A), // rgba(255, 232, 26, 0.7)
            shadowColor = Color(0x80FFDF57), // rgba(255, 223, 87, 0.5)
            shadowInsetTop = Color(0xE6FFDF34), // rgba(255, 223, 52, 0.9)
            shadowInsetBottom = Color(0xCCFFDFD7), // rgba(255, 250, 215, 0.8)
            contentColor = Color.White
        )

        /**
         * Uygulama vurgu (accent) rengine göre uyarlanmış dinamik palet oluşturucu.
         */
        fun fromAccent(accent: Color): UiverseButtonPalette {
            return UiverseButtonPalette(
                radialInner = accent,
                radialOuter = accent.copy(alpha = 0.85f),
                color1 = Color(0xB3FFA31A),
                color2 = Color(0xFF1A23FF),
                color3 = Color(0xFFE21BDA),
                color4 = Color(0xB3FFE81A),
                shadowColor = accent.copy(alpha = 0.45f),
                shadowInsetTop = Color.White.copy(alpha = 0.40f),
                shadowInsetBottom = Color.Black.copy(alpha = 0.25f),
                contentColor = Color.White
            )
        }
    }
}

private data class CircleSpec(
    val colorIndex: Int, // 1, 2, 3, or 4
    val p0: Offset,      // 0% and 100% position (in 132x48 reference space)
    val p33: Offset,     // 33% position
    val p66: Offset,     // 66% position
    val blurRadiusDp: Float = 14f
)

// Uiverse CSS'deki 12 halkanın tam koordinat ve renk haritası
private val circleSpecs = listOf(
    // circle-1: color-4, 0%:(0, -40), 33%:(0, 16), 66%:(12, 64)
    CircleSpec(4, Offset(0f, -40f), Offset(0f, 16f), Offset(12f, 64f), 10f),
    // circle-2: color-1, 0%:(92, 8), 33%:(80, -10), 66%:(72, -48)
    CircleSpec(1, Offset(92f, 8f), Offset(80f, -10f), Offset(72f, -48f), 14f),
    // circle-3: color-2, 0%:(-12, -12), 33%:(20, 12), 66%:(12, 4)
    CircleSpec(2, Offset(-12f, -12f), Offset(20f, 12f), Offset(12f, 4f), 16f),
    // circle-4: color-2, 0%:(80, -12), 33%:(76, -12), 66%:(112, -8)
    CircleSpec(2, Offset(80f, -12f), Offset(76f, -12f), Offset(112f, -8f), 16f),
    // circle-5: color-3, 0%:(12, -4), 33%:(84, 28), 66%:(40, -32)
    CircleSpec(3, Offset(12f, -4f), Offset(84f, 28f), Offset(40f, -32f), 18f),
    // circle-6: color-3, 0%:(56, 16), 33%:(28, -16), 66%:(76, -56)
    CircleSpec(3, Offset(56f, 16f), Offset(28f, -16f), Offset(76f, -56f), 18f),
    // circle-7: color-1, 0%:(8, 28), 33%:(8, 28), 66%:(20, -60)
    CircleSpec(1, Offset(8f, 28f), Offset(8f, 28f), Offset(20f, -60f), 14f),
    // circle-8: color-1, 0%:(28, -4), 33%:(32, -4), 66%:(56, -20)
    CircleSpec(1, Offset(28f, -4f), Offset(32f, -4f), Offset(56f, -20f), 14f),
    // circle-9: color-4, 0%:(20, -12), 33%:(20, -12), 66%:(80, -8)
    CircleSpec(4, Offset(20f, -12f), Offset(20f, -12f), Offset(80f, -8f), 10f),
    // circle-10: color-4, 0%:(64, 16), 33%:(68, 20), 66%:(100, 28)
    CircleSpec(4, Offset(64f, 16f), Offset(68f, 20f), Offset(100f, 28f), 10f),
    // circle-11: color-1, 0%:(4, 4), 33%:(4, 4), 66%:(68, 20)
    CircleSpec(1, Offset(4f, 4f), Offset(4f, 4f), Offset(68f, 20f), 14f),
    // circle-12: color-1, 0%:(52, 4), 33%:(56, 0), 66%:(60, -32)
    CircleSpec(1, Offset(52f, 4f), Offset(56f, 0f), Offset(60f, -32f), 16f)
)

/**
 * Üç anahtar kare (0%, 33%, 66%, 100%) arasında yumuşak trigonometrik yumuşatma (smooth cosine easing) ile
 * koordinat hesaplar.
 */
private fun interpolatePosition(spec: CircleSpec, progress: Float): Offset {
    val (pStart, pEnd, subT) = when {
        progress < 0.3333f -> Triple(spec.p0, spec.p33, progress / 0.3333f)
        progress < 0.6666f -> Triple(spec.p33, spec.p66, (progress - 0.3333f) / 0.3333f)
        else -> Triple(spec.p66, spec.p0, (progress - 0.6666f) / 0.3334f)
    }
    // Cosine S-Curve Easing
    val eased = (1f - cos(subT * Math.PI.toFloat())) / 2f
    val x = pStart.x + (pEnd.x - pStart.x) * eased
    val y = pStart.y + (pEnd.y - pStart.y) * eased
    return Offset(x, y)
}

/**
 * Uiverse Ashon-G efektine sahip gelişmiş buton bileşeni.
 */
@Composable
fun KitsugiUiverseGlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    palette: UiverseButtonPalette = UiverseButtonPalette.Default,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: Dp = 14.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    // CSS: .uiverse:hover { --duration: 1400ms; } (Odakta veya basılıyken animasyon hızlanır)
    val durationMs = if (isPressed || isFocused) 1800 else 7000

    val transition = rememberInfiniteTransition(label = "uiverseButtonTransition")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "uiverseCircleProgress"
    )

    val scale by animateFloatAsState(
        targetValue = if (!enabled) 1.0f else if (isPressed) 0.965f else if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "uiverseScale"
    )

    val clickModifier = if (enabled) {
        Modifier.tvClickable(shape = shape, onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            // CSS: box-shadow: 0 0 14px var(--c-shadow);
            .drawBehind {
                if (enabled) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.shadowColor.copy(alpha = if (isFocused) 0.75f else 0.45f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.maxDimension * 0.75f
                        ),
                        size = Size(size.width + 16.dp.toPx(), size.height + 16.dp.toPx()),
                        topLeft = Offset(-8.dp.toPx(), -8.dp.toPx()),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                }
            }
            .clip(shape)
            // CSS: border ve inset cam parlaması
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (enabled) 0.55f else 0.20f),
                        palette.shadowInsetTop.copy(alpha = if (enabled) 0.35f else 0.10f),
                        Color.White.copy(alpha = if (enabled) 0.15f else 0.05f)
                    )
                ),
                shape = shape
            )
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        // 1. CSS: Radial gradient arka plan (inner -> outer 80%)
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            if (!enabled) {
                drawRect(color = Color(0xFF2A2A2A))
                return@Canvas
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.radialInner,
                        palette.radialOuter,
                        palette.radialOuter
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = max(w, h) * 0.85f
                )
            )

            // 2. CSS: 12 adet serbest yüzen renkli metaball küresi
            val scaleX = w / 132f
            val scaleY = h / 48f
            val baseRadius = (h * 0.50f).coerceAtLeast(18.dp.toPx())

            circleSpecs.forEach { spec ->
                val baseColor = when (spec.colorIndex) {
                    1 -> palette.color1
                    2 -> palette.color2
                    3 -> palette.color3
                    else -> palette.color4
                }

                val pos = interpolatePosition(spec, animProgress)
                val centerOffset = Offset(
                    x = pos.x * scaleX,
                    y = pos.y * scaleY
                )
                val radius = baseRadius * (spec.blurRadiusDp / 14f)

                // Yumuşak odaklı radyal parıltı (Doğal donanım hızlandırmalı blur simülasyonu)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            baseColor,
                            baseColor.copy(alpha = baseColor.alpha * 0.65f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = radius
                    ),
                    center = centerOffset,
                    radius = radius,
                    blendMode = BlendMode.Screen
                )
            }

            // 3. CSS: .uiverse:before - inset üst parlama ve alt derinlik gölgesi
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to palette.shadowInsetTop.copy(alpha = 0.55f),
                    0.35f to Color.Transparent,
                    0.70f to Color.Transparent,
                    1.0f to palette.shadowInsetBottom.copy(alpha = 0.50f)
                )
            )
        }

        // 4. Ön plan içeriği (İkon & Metin)
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Detay sayfalarında "İzle" veya "Oku" gibi ana aksiyonlar için hazır Uiverse animasyonlu buton.
 */
@Composable
fun KitsugiDetailActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    palette: UiverseButtonPalette = UiverseButtonPalette.Default,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val contentColor = if (enabled) palette.contentColor else Color.Gray

    KitsugiUiverseGlowButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        palette = palette,
        shape = shape,
        contentPadding = 14.dp
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            style = TextStyle(
                shadow = if (enabled) {
                    Shadow(
                        color = Color(0x66000000),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 4f
                    )
                } else null
            )
        )
    }
}

/**
 * Standart Material3 [Button] yerine geçen, tam Uiverse animasyonlu buton.
 * Modifier ve içerik API'si orijinal Button ile birebir uyumludur.
 */
@Composable
fun KitsugiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    palette: UiverseButtonPalette = UiverseButtonPalette.Default,
    shape: Shape = RoundedCornerShape(16.dp),
    // Eski Button(colors = ButtonDefaults.buttonColors(...)) çağrılarıyla uyumluluk için
    // Bu parametre Uiverse paleti lehine göz ardı edilir.
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.buttonColors(),
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    KitsugiUiverseGlowButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        palette = palette,
        shape = shape,
        contentPadding = 12.dp,
        content = content
    )
}

/**
 * Secondary / tonal aksiyon butonu — Uiverse efekti ile ama daha sakin koyu palet.
 * İkincil aksiyonlar ("Filtrele", "Geri Dön", "Ayarlar" vb.) için uygundur.
 */
@Composable
fun KitsugiTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.buttonColors(),
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    KitsugiUiverseGlowButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        palette = UiverseButtonPalette(
            radialInner       = Color(0xFF2C2C3E),
            radialOuter       = Color(0xFF1C1C2A),
            color1            = Color(0x44A0A0FF),
            color2            = Color(0x331A23FF),
            color3            = Color(0x33E21BDA),
            color4            = Color(0x44FFD215),
            shadowColor       = Color(0x30A0A0FF),
            shadowInsetTop    = Color(0x40FFFFFF),
            shadowInsetBottom = Color(0x20000000),
            contentColor      = Color(0xFFE0E0FF)
        ),
        shape = shape,
        contentPadding = 12.dp,
        content = content
    )
}

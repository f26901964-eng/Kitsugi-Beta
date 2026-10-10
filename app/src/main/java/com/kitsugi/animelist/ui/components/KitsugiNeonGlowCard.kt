package com.kitsugi.animelist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalCardFramesEnabled
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * Uiverse (Tiagoadag) referanslı, dokunmatik ekranlara ve TV'ye tam uyarlanmış
 * 163° neon degrade kenarlıklı ve basma animasyonlu sarmalayıcı kart bileşeni.
 *
 * Dokunmatik çalışma prensibi:
 * 1. Durağan durumda (Ambient): Kartın etrafında 1.5 dp şık bir tema/neon degrade
 *    çerçevesi ve hafif derinlik gölgesi daima görünür (fareye ihtiyaç duymaz).
 * 2. Dokunulduğu / Basıldığı an (Touch-down / Press):
 *    - Kart scale değeri 0.98f'e yumuşakça küçülür (fiziksel buton basma hissi).
 *    - Çerçeve ışıması ve gölge tam parlaklığa (glow) çıkar.
 * 3. Bırakıldığında:
 *    - Yaylı animasyonla (spring) 1.0f'e geri döner ve işlem tetiklenir.
 * 4. Android TV / Fare: Odak (D-pad focus) veya hover durumunda da tam parlama tetiklenir.
 */
@Composable
fun KitsugiNeonGlowCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.6.dp,
    containerColor: Color = KitsugiColors.Surface,
    customGradientColors: List<Color>? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val isInteracting = isPressed || isHovered || isFocused

    val accentColor = LocalKitsugiAccent.current
    val accent2 = com.kitsugi.animelist.ui.theme.LocalKitsugiAccent2.current
    val framesEnabled = LocalCardFramesEnabled.current

    // Uiverse'in 163 derece degrade renk paleti (Temanın accent rengiyle uyumlu dinamik harman)
    val gradientColors = customGradientColors ?: remember(accentColor, accent2) {
        listOf(
            accentColor,
            accent2 ?: Color(0xFF3700FF) // Electric blue / purple
        )
    }

    // Basıldığında 0.98f ölçeğe esneme (CSS transform: scale(0.98) karşılığı)
    val animatedScale by animateFloatAsState(
        targetValue = if (isInteracting) 0.98f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "neonCardScale"
    )

    // Dokunulduğunda parlayan gölge derinliği (CSS box-shadow: 0px 0px 30px ... karşılığı)
    val shadowElevation by animateDpAsState(
        targetValue = if (isInteracting) 16.dp else 4.dp,
        animationSpec = tween(durationMillis = 220),
        label = "neonCardShadow"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1.0f else 0.55f,
        animationSpec = tween(durationMillis = 200),
        label = "neonBorderAlpha"
    )

    // 163 derecelik açı için çapraz degrade fırçası
    val gradientBrush = remember(gradientColors, borderAlpha) {
        Brush.linearGradient(
            colors = gradientColors.map { it.copy(alpha = (it.alpha * borderAlpha).coerceIn(0f, 1f)) },
            start = Offset(0f, 0f),
            end = Offset(400f, 1000f) // ~163 derece açılı eğim
        )
    }

    // Dış Sarmalayıcı (.card)
    Box(
        modifier = modifier
            .scale(animatedScale)
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                spotColor = accentColor.copy(alpha = if (isInteracting) 0.45f else 0.15f),
                ambientColor = Color(0x333700FF)
            )
            .then(
                if (framesEnabled) Modifier.border(width = borderWidth, brush = gradientBrush, shape = shape)
                else Modifier
            )
            .clip(shape)
            .background(containerColor)
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
                interactionSource = interactionSource,
                indication = ripple(color = accentColor.copy(alpha = 0.2f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // İç Kart Gövdesi (.card2)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(containerColor)
        ) {
            content()
        }
    }
}

/**
 * Mevcut Card bileşenlerinin etrafına doğrudan Uiverse 163° neon degrade kenarlık
 * ve derinlik parıltısı kazandıran yardımcı Modifier.
 */
@Composable
fun Modifier.kitsugiNeonGlow(
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.6.dp,
    customGradientColors: List<Color>? = null
): Modifier {
    val accentColor = LocalKitsugiAccent.current
    val accent2 = com.kitsugi.animelist.ui.theme.LocalKitsugiAccent2.current
    val framesEnabled = LocalCardFramesEnabled.current
    val gradientColors = customGradientColors ?: remember(accentColor, accent2) {
        listOf(accentColor, accent2 ?: Color(0xFF3700FF))
    }
    val gradientBrush = remember(gradientColors) {
        Brush.linearGradient(
            colors = gradientColors.map { it.copy(alpha = 0.65f) },
            start = Offset(0f, 0f),
            end = Offset(400f, 1000f)
        )
    }

    // Çerçeveler kapalıyken (Görünüm ayarı) gölge ve degrade kenarlık tamamen atlanır.
    if (!framesEnabled) return this

    return this
        .shadow(
            elevation = 6.dp,
            shape = shape,
            spotColor = accentColor.copy(alpha = 0.22f),
            ambientColor = Color(0x333700FF)
        )
        .border(
            width = borderWidth,
            brush = gradientBrush,
            shape = shape
        )
}

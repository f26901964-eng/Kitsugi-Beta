package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Buton paleti tanımı — görsel efektler sadeleştirildiği için yalnızca API
 * uyumluluğu amacıyla korunur. Yeni sade tasarım renklerini temadan
 * (koyu/açık) otomatik aldığı için palet değerleri kullanılmaz.
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
        val Default = UiverseButtonPalette(
            radialInner = Color(0xFFFFD215),
            radialOuter = Color(0xFFFFF172),
            color1 = Color(0xB3FFA31A),
            color2 = Color(0xFF1A23FF),
            color3 = Color(0xFFE21BDA),
            color4 = Color(0xB3FFE81A),
            shadowColor = Color(0x80FFDF57),
            shadowInsetTop = Color(0xE6FFDF34),
            shadowInsetBottom = Color(0xCCFFDFD7),
            contentColor = Color.White
        )

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

/**
 * Verilen zemin renginin üzerinde okunaklı kalacak metin/ikon rengini seçer
 * (açık zeminde koyu metin, koyu zeminde beyaz metin).
 */
internal fun Color.onAccentColor(): Color =
    if (this.luminance() > 0.55f) Color(0xFF14181F) else Color.White

/**
 * Sade, tema uyumlu (koyu/açık) dolgulu buton.
 * Eski animasyonlu "glow" tasarımı yerine tek renkli temiz bir yüzey ve
 * basınca hafif küçülme ile dokunsal geri bildirim kullanır.
 */
@Composable
fun KitsugiUiverseGlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") palette: UiverseButtonPalette = UiverseButtonPalette.Default,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: Dp = 12.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "kitsugiButtonScale"
    )

    val backgroundColor = when {
        !enabled -> KitsugiColors.SurfaceStrong
        isPressed -> accent.copy(alpha = 0.85f)
        else -> accent
    }
    val contentColor = if (enabled) backgroundColor.onAccentColor() else KitsugiColors.TextMuted

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor, shape)
            .then(if (enabled) Modifier.tvClickable(shape = shape, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/**
 * Detay sayfalarında "İzle" / "Oku" gibi ana aksiyonlar için sade dolgulu buton.
 */
@Composable
fun KitsugiDetailActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    @Suppress("UNUSED_PARAMETER") palette: UiverseButtonPalette = UiverseButtonPalette.Default,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val accent = LocalKitsugiAccent.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "detailActionScale"
    )

    val backgroundColor = when {
        !enabled -> KitsugiColors.SurfaceStrong
        isPressed -> accent.copy(alpha = 0.85f)
        else -> accent
    }
    val contentColor = if (enabled) backgroundColor.onAccentColor() else KitsugiColors.TextMuted

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor, shape)
            .then(if (enabled) Modifier.tvClickable(shape = shape, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
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
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

/**
 * Standart Material [Button] yerine geçen sade dolgulu buton.
 * Çağrı tarafı özel `colors` verirse (containerColor/contentColor) ona uyulur;
 * verilmezse tema vurgu rengi ile doldurulur.
 */
@Composable
fun KitsugiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") palette: UiverseButtonPalette = UiverseButtonPalette.Default,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Unspecified,
        contentColor = Color.Unspecified,
        disabledContainerColor = Color.Unspecified,
        disabledContentColor = Color.Unspecified
    ),
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "kitsugiButtonScale"
    )

    val specifiedContainer = colors.containerColor(enabled)
    val specifiedContent = colors.contentColor(enabled)

    val backgroundColor = when {
        specifiedContainer != Color.Unspecified -> specifiedContainer
        !enabled -> KitsugiColors.SurfaceStrong
        isPressed -> accent.copy(alpha = 0.85f)
        else -> accent
    }
    val contentColor = when {
        specifiedContent != Color.Unspecified -> specifiedContent
        !enabled -> KitsugiColors.TextMuted
        else -> backgroundColor.onAccentColor()
    }

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor, shape)
            .then(if (enabled) Modifier.tvClickable(shape = shape, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/**
 * İkincil aksiyonlar için sakin, tonal (yüzey renkli) buton.
 * Koyu temada koyu yüzey, açık temada açık yüzey ile otomatik uyum sağlar.
 */
@Composable
fun KitsugiTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.buttonColors(),
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "tonalButtonScale"
    )

    val backgroundColor = if (enabled) KitsugiColors.SurfaceSoft else KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
    val contentColor = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor, shape)
            .border(width = 1.dp, color = KitsugiColors.Border, shape = shape)
            .then(if (enabled) Modifier.tvClickable(shape = shape, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

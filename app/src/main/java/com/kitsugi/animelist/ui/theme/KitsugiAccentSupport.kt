package com.kitsugi.animelist.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vurgu (accent) rengiyle ilgili ortak yardımcılar:
 *
 * - [onAccentColor]: Vurgu rengi üzerine çizilen TÜM yazı/ikonların rengi, zeminin
 *   koyuluğuna/açıklığına göre siyah ↔ beyaz arasında yumuşak bir geçişle otomatik seçilir.
 * - [LocalKitsugiOnAccent]: Tema kökünde hesaplanan, o anki vurgu rengine göre
 *   zıt kontrastlı metin rengi.
 * - [LocalKitsugiAccentBrush]: Düz veya açılı lineer gradyan vurgu fırçası.
 * - [Modifier.accentBackground]: Vurgu arka planlarını (düz/gradyan) tek noktadan çizer.
 */

/**
 * Vurgu zemininin üstünde okunacak metin/ikon rengi.
 *
 * Zemin koyulaştıkça metin beyaza, açıldıkça siyaha doğru SIYAH ↔ BEYAZ arasında
 * yumuşak (smoothstep) bir geçişle kayar. Geçiş bandı, kontrastın en kritik olduğu
 * orta koyuluk bölgesine denk gelecek şekilde dar tutulmuştur — böylece neredeyse
 * her vurgu renginde metin saf siyah veya saf beyaz okunur, nadir ara tonlarda ise
 * siyah-beyaz ara gri geçişi görülür.
 */
fun onAccentColor(background: Color): Color {
    val l = background.luminance().coerceIn(0f, 1f)
    // 0.15 altı koyu zeminler → beyaz; 0.25 üstü açık zeminler → siyah
    val t = ((l - 0.15f) / 0.10f).coerceIn(0f, 1f)
    val s = t * t * (3f - 2f * t) // smoothstep
    val v = 1f - s // 1 = beyaz, 0 = siyah
    return Color(v, v, v)
}

/** İki vurgu renginin (gradyan) orta harmanı üzerinden otomatik metin rengi. */
fun onAccentColor(start: Color, end: Color?): Color =
    onAccentColor(if (end == null) start else lerp(start, end, 0.5f))

/** Vurgu rengi üstündeki metin/ikon rengi — temada hesaplanmış hâli. */
val LocalKitsugiOnAccent = compositionLocalOf { Color.White }

/** Vurgu rengi arka plan fırçası — düz renk veya açılı lineer gradyan. */
val LocalKitsugiAccentBrush = compositionLocalOf<Brush> {
    SolidColor(Color(0xFFC8F4EF))
}

/** Açılı lineer gradyan fırçası — çizim alanının gerçek boyutuna göre hesaplanır. */
class AngleLinearGradientBrush(
    private val colors: List<Color>,
    private val angleDegrees: Float
) : ShaderBrush() {

    override fun createShader(size: Size): Shader {
        val w = if (size.width > 0f) size.width else 1f
        val h = if (size.height > 0f) size.height else 1f
        val rad = Math.toRadians(angleDegrees.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // İpucu: merkezden köşelere tam kapsayan yarı uzunluk
        val half = (abs(dx) * w + abs(dy) * h) / 2f
        val startX = cx - dx * half
        val startY = cy - dy * half
        val endX = cx + dx * half
        val endY = cy + dy * half
        return android.graphics.LinearGradient(
            startX, startY, endX, endY,
            colors.map { it.toArgb() }.toIntArray(),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AngleLinearGradientBrush) return false
        return colors == other.colors && angleDegrees == other.angleDegrees
    }

    override fun hashCode(): Int = 31 * colors.hashCode() + angleDegrees.hashCode()
}

/**
 * Vurgu arka planı için fırça üretir.
 * [end] null ise düz renk, değilse [angleDegrees] açılı iki renkli gradyan.
 */
fun accentBackgroundBrush(start: Color, end: Color?, angleDegrees: Float = 135f): Brush {
    return if (end == null) {
        SolidColor(start)
    } else {
        AngleLinearGradientBrush(listOf(start, end), angleDegrees)
    }
}

/**
 * Vurgu (düz veya gradyan) arka planı uygular.
 * Kullanım: `Modifier.accentBackground(RoundedCornerShape(16.dp))`
 */
@Composable
fun Modifier.accentBackground(shape: Shape = RectangleShape): Modifier {
    val brush = LocalKitsugiAccentBrush.current
    return this
        .clip(shape)
        .drawBehind { drawRect(brush = brush) }
}

/** Vurgu zemininde kullanılacak otomatik kontrastlı metin rengi. */
val onAccentTextColor: Color
    @Composable get() = LocalKitsugiOnAccent.current

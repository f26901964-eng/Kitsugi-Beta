package com.kitsugi.animelist.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * - [LocalKitsugiAccent2]: Gradyan bitiş rengi (`null` ise düz renk).
 * - [LocalKitsugiAccentAngle]: Gradyan açısı (derece).
 * - [LocalKitsugiOnAccent]: Tema kökünde hesaplanan, o anki vurgu rengine göre
 *   zıt kontrastlı metin rengi.
 * - [LocalKitsugiAccentBrush]: Düz veya açılı lineer gradyan vurgu fırçası.
 * - [LocalInsideAccentSurface]: Dolgulu vurgu yüzeyi (buton/rozet) içindeyken true.
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

/** Gradyan bitiş rengi (`null` ise düz vurgu rengi aktif). */
val LocalKitsugiAccent2 = compositionLocalOf<Color?> { null }

/** Gradyan açısı (derece, varsayılan 135°). */
val LocalKitsugiAccentAngle = compositionLocalOf { 135f }

/** Vurgu rengi üstündeki metin/ikon rengi — temada hesaplanmış hâli. */
val LocalKitsugiOnAccent = compositionLocalOf { Color.White }

/** Vurgu rengi arka plan fırçası — düz renk veya açılı lineer gradyan. */
val LocalKitsugiAccentBrush = compositionLocalOf<Brush> {
    SolidColor(Color(0xFFC8F4EF))
}

/**
 * Vurgu rengiyle tam doldurulmuş bir buton/rozet yüzeyi içindeyken `true` olur;
 * içerideki yazı ve ikonların otomatik olarak [LocalKitsugiOnAccent] kontrast
 * rengini almasını sağlar.
 */
val LocalInsideAccentSurface = compositionLocalOf { false }

/**
 * Verilen [Color] değerinin o anki tema vurgu rengiyle (veya `accent.copy(alpha = ...)`
 * ile türetilmiş yarı saydam bir kopyasıyla) aynı RGB tonuna sahip olup olmadığını döner.
 */
fun Color.isSameAccentRgb(accent: Color): Boolean {
    if (this == Color.Unspecified || this.alpha <= 0f) return false
    return (this.toArgb() and 0x00FFFFFF) == (accent.toArgb() and 0x00FFFFFF)
}

/**
 * Gradyan aktifse ve [color] tema vurgu renginin (tam veya yarı saydam) bir varyantıysa
 * aynı alfa değerine sahip açılı gradyan fırçasını döner; aksi halde `null` döner.
 *
 * PERF: Fırça her çağrıda yeniden oluşturulmaz; aynı (renk, açı) kombinasyonu için
 * süreç genelinde TEK bir [AngleLinearGradientBrush] instance'ı döndürülür
 * ([GradientBrushCache]). [ShaderBrush] shader önbelleğini instance başına tuttuğu
 * için yeni instance = her çizimde yeni `android.graphics.LinearGradient` shader'ı
 * demekti; gradyan tema açıkken yüzlerce bileşen her karede shader üretiyordu
 * (kasma/donma raporlarının kök nedeni). Instance sabitleyince shader önbelleği
 * gerçekten çalışır ve çizim başına tahsisat sıfırlanır.
 */
fun resolveAccentBrush(
    color: Color,
    accent: Color,
    accent2: Color?,
    angleDegrees: Float
): Brush? {
    if (accent2 == null || color == Color.Unspecified || color.alpha <= 0f) return null
    if (!color.isSameAccentRgb(accent)) return null
    val a = color.alpha.coerceIn(0f, 1f)
    return if (a >= 0.999f) {
        angleLinearGradientBrush(listOf(accent, accent2), angleDegrees)
    } else {
        angleLinearGradientBrush(
            listOf(accent.copy(alpha = a), accent2.copy(alpha = a)),
            angleDegrees
        )
    }
}

/** Composable bağlamda [color] için aktif gradyan fırçasını çözer. */
@Composable
fun resolveCurrentAccentBrush(color: Color): Brush? {
    val accent = LocalKitsugiAccent.current
    val accent2 = LocalKitsugiAccent2.current
    val angle = LocalKitsugiAccentAngle.current
    // remember: aynı girdilerde aynı instance — hem önbellek isabeti hem de
    // TextStyle.copy(brush = ...) karşılaştırmalarının kararlı kalması için.
    return remember(color, accent, accent2, angle) {
        resolveAccentBrush(color, accent, accent2, angle)
    }
}

/** İstenen [alpha] saydamlığında düz veya gradyan vurgu fırçası üretir. */
@Composable
fun accentBrushWithAlpha(alpha: Float = 1f): Brush {
    val a = alpha.coerceIn(0f, 1f)
    val c1Raw = LocalKitsugiAccent.current
    val c2Raw = LocalKitsugiAccent2.current
    val angle = LocalKitsugiAccentAngle.current
    // remember: FilterChip gibi sık recompose olan bileşenlerde her karede
    // fırça çözümüne girme; önbellekli fabrika zaten instance döndürür.
    return remember(a, c1Raw, c2Raw, angle) {
        accentBackgroundBrush(c1Raw.copy(alpha = a), c2Raw?.copy(alpha = a), angle)
    }
}

// ─── Gradyan Fırça Instance Önbelleği ──────────────────────────────────────
// Aynı (renkler, açı) kombinasyonu için uygulama genelinde TEK instance kullanılır.
// Bu olmadan her resolveAccentBrush / drawBehind / draw çağrısı yeni bir fırça
// nesnesi üretiyordu. ShaderBrush'ın shader önbelleği instance'a bağlı olduğundan
// her yeni nesne, çizim başına yepyeni bir android.graphics.LinearGradient
// üretilmesi demekti — kaydırma sırasında yüzlerce gradyan bileşen her karede
// shader tahsis ediyordu (CPU + GC + RenderThread baskısı → kasma/donma).

private data class GradientBrushKey(val start: ULong, val end: ULong, val angle: Float)

private object GradientBrushCache {
    private const val MAX_ENTRIES = 64
    private val cache = object : LinkedHashMap<GradientBrushKey, AngleLinearGradientBrush>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<GradientBrushKey, AngleLinearGradientBrush>?
        ): Boolean = size > MAX_ENTRIES
    }

    fun obtain(colors: List<Color>, angleDegrees: Float): AngleLinearGradientBrush {
        if (colors.isEmpty()) {
            return AngleLinearGradientBrush(listOf(Color(0xFFC8F4EF), Color(0xFFC8F4EF)), angleDegrees)
        }
        val safeAngle = if (angleDegrees.isFinite()) angleDegrees else 135f
        val key = GradientBrushKey(
            start = colors[0].value,
            end = colors.getOrNull(1)?.value ?: colors[0].value,
            angle = safeAngle
        )
        return synchronized(cache) {
            cache.getOrPut(key) { AngleLinearGradientBrush(colors.toList(), safeAngle) }
        }
    }
}

/**
 * Önbellekli fabrika: aynı (renkler, açı) için her zaman aynı
 * [AngleLinearGradientBrush] instance'ını döndürür. Yeni fırça gerekiyorsa
 * doğrudan constructor yerine BU fonksiyon kullanılmalı.
 */
fun angleLinearGradientBrush(colors: List<Color>, angleDegrees: Float): AngleLinearGradientBrush =
    GradientBrushCache.obtain(colors, angleDegrees)

/**
 * Açılı lineer gradyan fırçası — çizim alanının gerçek boyutuna göre hesaplanır.
 *
 * NOT: Compose [ShaderBrush] yalnızca son tek `createdSize` değerini saklar; aynı fırça
 * farklı boyutlardaki bileşenlerde (buton, kart, ikon, metin) paylaşıldığında `createdSize`
 * her bileşende değiştiği için `createShader(size)` her bileşende tekrar çağrılır.
 * Burada boyut bazlı (`w x h`) ikinci seviye LRU önbellek tutularak JNI üzerinden
 * tekrar tekrar `android.graphics.LinearGradient` üretilmesi ve dejenere (0/NaN/Infinity)
 * boyutlarda bozuk SkShader oluşması tamamen engellenir.
 */
class AngleLinearGradientBrush(
    private val colors: List<Color>,
    private val angleDegrees: Float
) : ShaderBrush() {

    private val argbColors: IntArray = when {
        colors.size >= 2 -> IntArray(colors.size) { colors[it].toArgb() }
        colors.size == 1 -> intArrayOf(colors[0].toArgb(), colors[0].toArgb())
        else -> intArrayOf(0xFFC8F4EF.toInt(), 0xFFC8F4EF.toInt())
    }

    private val shaderCache = object : LinkedHashMap<Long, Shader>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Shader>?): Boolean = size > 96
    }

    override fun createShader(size: Size): Shader {
        val w = if (size.width.isFinite() && size.width >= 1f) size.width.coerceAtMost(16384f) else 1f
        val h = if (size.height.isFinite() && size.height >= 1f) size.height.coerceAtMost(16384f) else 1f
        val wInt = w.toInt().coerceAtLeast(1)
        val hInt = h.toInt().coerceAtLeast(1)
        val sizeKey = (wInt.toLong() shl 32) or (hInt.toLong() and 0xFFFFFFFFL)

        synchronized(shaderCache) {
            shaderCache[sizeKey]?.let { return it }
        }

        val safeAngle = if (angleDegrees.isFinite()) angleDegrees else 135f
        val rad = Math.toRadians(safeAngle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // İpucu: merkezden köşelere tam kapsayan yarı uzunluk (asla 0 olmasın)
        val half = ((abs(dx) * w + abs(dy) * h) / 2f).coerceAtLeast(0.5f)
        val startX = cx - dx * half
        val startY = cy - dy * half
        val endX = cx + dx * half
        val endY = cy + dy * half
        val created = android.graphics.LinearGradient(
            startX, startY, endX, endY,
            argbColors,
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        synchronized(shaderCache) {
            shaderCache[sizeKey] = created
        }
        return created
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
 * Gradyan instance'ları önbellekten gelir (bkz. [GradientBrushCache]).
 */
fun accentBackgroundBrush(start: Color, end: Color?, angleDegrees: Float = 135f): Brush {
    return if (end == null) {
        SolidColor(start)
    } else {
        angleLinearGradientBrush(listOf(start, end), angleDegrees)
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
        .drawBehind {
            if (size.width > 0f && size.height > 0f) {
                drawRect(brush = brush)
            }
        }
}

/** Vurgu zemininde kullanılacak otomatik kontrastlı metin rengi. */
val onAccentTextColor: Color
    @Composable get() = LocalKitsugiOnAccent.current

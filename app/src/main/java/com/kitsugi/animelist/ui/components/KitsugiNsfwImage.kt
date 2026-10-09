package com.kitsugi.animelist.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.kitsugi.animelist.ui.theme.LocalBlurAdultMedia
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.BlurTransformation

/**
 * Donanım bulanıklık (RenderEffect / Modifier.blur) desteği API 31+ gerektirir.
 * Bu seviyenin altındaki cihazlarda Modifier.blur sessizce hiçbir şey yapmaz;
 * bu yüzden resmin kendisine bitmap seviyesinde bulanıklık uygulanmalıdır.
 */
object KitsugiBlurSupport {
    /** true → GPU RenderEffect bulanıklığı kullanılabilir */
    val hasRenderEffectBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Bitmap fallback bulanıklığı için Coil dönüşümü (stack blur).
     * Cihaz desteklemiyorsa otomatik olarak resme uygulanır; Coil disk belleğinde
     * cacheKey ile saklandığı için yalnızca bir kez hesaplanır.
     */
    fun bitmapBlurTransformation(radiusPx: Int): BlurTransformation =
        BlurTransformation(radiusPx.coerceIn(1, 250))
}

/**
 * Centralized NSFW-aware image composable.
 *
 * Reads [LocalBlurAdultMedia] from the composition. If the setting is enabled AND
 * [isAdult] is true the image is blurred no matter what:
 *  - API 31+ cihazlarda tüm kutu (placeholder/shimmer dahil) GPU ile [Modifier.blur] uygulanır.
 *  - Bulanıklık desteği olmayan cihazlarda (API < 31) Coil [BlurTransformation] ile
 *    resmin bitmap'i decode edilirken bulanıklaştırılır — hiçbir koşulda ham görüntü sızdırılmaz.
 *
 * Usage: replace every manual `AsyncImage + Modifier.blur(...)` pair in media cards
 * with this composable to get consistent, zero-per-component NSFW protection.
 *
 * @param model         Coil image model URL (nullable – shows placeholder initials when null/blank)
 * @param contentDescription Accessibility description for the image
 * @param isAdult       Whether this media item is tagged as adult/NSFW content
 * @param modifier      Modifier applied to the outer container Box
 * @param contentScale  How the image is scaled inside its bounds (default: Crop)
 * @param blurRadius    Amount of blur applied when NSFW blur is active (default: 24.dp)
 * @param initials      Fallback text shown when there is no image (default: empty — shows nothing)
 */
@Composable
fun KitsugiNsfwImage(
    model: Any?,
    contentDescription: String?,
    isAdult: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    blurRadius: Dp = 24.dp,
    /** Explicit per-screen override; the app-wide setting remains enabled when true. */
    blurAdultMedia: Boolean? = null,
    initials: String = "",
    initialsColor: Color? = null,
    initialsStyle: androidx.compose.ui.text.TextStyle? = null,
    /** Görsel yüklenemezse çağrılır — çağıran taraf yedek bir kaynaka düşebilir. */
    onLoadingFailed: (() -> Unit)? = null
) {
    // Components that already receive the setting pass it through explicitly. Keep the
    // composition-local as a fallback (and OR it in) so nested screens cannot accidentally
    // disable a user-enabled blur by relying on a default `false` parameter.
    val shouldBlur = (LocalBlurAdultMedia.current || blurAdultMedia == true) && isAdult
    val accentColor = LocalKitsugiAccent.current
    val finalColor = initialsColor ?: accentColor
    val finalStyle = initialsStyle ?: MaterialTheme.typography.titleMedium

    // Donanım bulanıklığı: yalnızca destekleniyorsa Modifier.blur kullan.
    val useRenderEffectBlur = shouldBlur && KitsugiBlurSupport.hasRenderEffectBlur
    // Cihaz bulanıklığı desteklemiyorsa resmin bitmap'i stack-blur ile bulanıklaştırılır.
    val useBitmapBlur = shouldBlur && !KitsugiBlurSupport.hasRenderEffectBlur

    val context = LocalContext.current
    val isBlankModel = model == null || (model is String && model.isBlank())
    val imageModel: Any? = if (isBlankModel) {
        null
    } else if (useBitmapBlur) {
        // blurRadius.dp → px dönüşümü; yoğun ekranda daha yumuşak görünür
        val density = LocalDensity.current
        val radiusPx = with(density) { blurRadius.toPx() }.toInt()
        remember(model, radiusPx) {
            ImageRequest.Builder(context)
                .data(model)
                .transformations(KitsugiBlurSupport.bitmapBlurTransformation(radiusPx))
                .build()
        }
    } else {
        model
    }

    // Apply the blur at the Box level so that BOTH the placeholder background/shimmer
    // AND the loaded image are blurred — nothing leaks through during loading.
    Box(
        modifier = modifier.then(
            if (useRenderEffectBlur) Modifier.blur(blurRadius) else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                onError = { onLoadingFailed?.invoke() }
            )
        } else if (initials.isNotEmpty()) {
            Text(
                text = initials.take(2).uppercase(),
                color = finalColor,
                style = finalStyle,
                fontWeight = FontWeight.Black
            )
        }
    }
}

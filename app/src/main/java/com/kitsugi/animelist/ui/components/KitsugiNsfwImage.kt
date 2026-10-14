package com.kitsugi.animelist.ui.components

import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
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

    /**
     * OEM ROM tespiti. MIUI/HyperOS, Realme UI, ColorOS, OriginOS, FunTouchOS, OneUI'nin
     * bazı sürümlerinde özelleştirilmiş `libhwui.so`, RenderEffect bulanıklığında (GPU katmanı
     * ayrımı / imhası) RenderThread üzerinde `fault_addr=0x20` imzalı SIGSEGV üretiyor.
     * Bu cihazlarda GPU bulanıklığını TAMAMEN kapatıp bitmap stack-blur'a düşmek tek güvenli
     * yoldur; aksi takdirde ağır çekim çizim anında "pat diye kapanma" kaçınılmazdır.
     */
    private val isProblematicOem: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val mfr = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val knownBad = listOf("xiaomi", "redmi", "poco", "realme", "oppo", "oneplus",
            "vivo", "iqoo", "meizu", "infinix", "tecno", "itel", "honor", "hmd")
        knownBad.any { it in mfr || it in brand }
            || !System.getProperty("ro.miui.ui.version.name").isNullOrBlank()
            || !System.getProperty("ro.mi.os.version.name").isNullOrBlank()
            || !System.getProperty("ro.vivo.os.version").isNullOrBlank()
            || !System.getProperty("ro.build.version.opporom").isNullOrBlank()
            || !System.getProperty("ro.rom.version").isNullOrBlank()
    }

    /** true → GPU RenderEffect bulanıklığı kullanılabilir */
    val hasRenderEffectBlur: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && !isProblematicOem
            // RenderEffect ile "fault_addr=0x20" imzalı RenderThread SIGSEGV'si
            // bilinen olarak GPU sürücüsü eski/düzensiz cihazlarda da tetikleniyor.
            // Düşük RAM veya düşük-end GPU'larda bitmap blur'a geri düşmek güvenli.
            && Runtime.getRuntime()?.maxMemory()?.let { it >= 256L * 1024 * 1024 } ?: true

    /**
     * RenderEffect yarıçapı için güvenli üst sınır.
     *
     * Özelleştirilmiş `libhwui` sürümlerinde (özellikle MIUI/HyperOS) büyük yarıçaplı
     * bulanıklıklar GPU katmanı ayırırken RenderThread içinde SIGSEGV üretebiliyor.
     * 24dp ve üzeri değerler çökme raporlarında görülen senaryolarla örtüşüyor.
     *
     * NOT: Sorunlu OEM'lerde `hasRenderEffectBlur` zaten `false` olduğu için bu üst sınıra
     * hiç ulaşılmaz — tüm bulanıklıklar bitmap stack-blur olarak çalışır.
     */
    val MAX_SAFE_BLUR_RADIUS: Dp = 12.dp

    /**
     * Bitmap fallback bulanıklığı için Coil dönüşümü (stack blur).
     * Cihaz desteklemiyorsa otomatik olarak resme uygulanır; Coil disk belleğinde
     * cacheKey ile saklandığı için yalnızca bir kez hesaplanır.
     */
    fun bitmapBlurTransformation(radiusPx: Int): BlurTransformation =
        BlurTransformation(radiusPx.coerceIn(1, 250))
}

/**
 * GÜVENLİ BULANIKLIK — `Modifier.blur()` yerine BUNU kullanın.
 *
 * NEDEN VAR?
 * ──────────
 * `Modifier.blur()` arka planda `RenderEffect.createBlurEffect()` çağırır ve
 * GPU'da ayrı bir katman (layer) açar. Kullanıcıdan gelen çökme raporunda
 * **7/7 native çökme birebir aynı imzaya** sahipti:
 *
 *     signal=SIGSEGV (11)   fault_addr=0x20
 *     thread=RenderThread
 *     /system/lib64/libhwui.so  (+0x1ff650, +0x779c4, +0x109550, ...)
 *
 * `fault_addr=0x20`, null bir RenderNode/SkImageFilter üzerinden alan okuma
 * demektir — yani RenderThread yok edilmiş (ya da hiç oluşturulmamış) bir GPU
 * katmanını çizmeye çalışıyor. Activity yeniden kurulurken (dil değişimi →
 * `recreate()`), ekran dönerken veya büyük yarıçaplı katman ayrılırken olur.
 *
 * Bu sarmalayıcı üç şey yapar:
 *   1. API 31 altında hiçbir şey uygulamaz (RenderEffect yok, boşa katman açmasın)
 *   2. Yarıçapı [KitsugiBlurSupport.MAX_SAFE_BLUR_RADIUS] ile sınırlar
 *   3. `enabled=false` ile tamamen devre dışı bırakılabilir
 */
fun Modifier.safeBlur(radius: Dp, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    if (!KitsugiBlurSupport.hasRenderEffectBlur) return this
    if (radius <= 0.dp) return this
    return this.blur(
        if (radius > KitsugiBlurSupport.MAX_SAFE_BLUR_RADIUS) KitsugiBlurSupport.MAX_SAFE_BLUR_RADIUS
        else radius
    )
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
    onLoadingFailed: (() -> Unit)? = null,
    /** Görsel başarıyla yüklendiğinde gerçek en/boy oranını (width / height) bildirir. */
    onImageAspectResolved: ((Float) -> Unit)? = null,
    /**
     * Tam kalite modu — **yalnızca vitrin (hero) gibi ekranda tek ve büyük duran
     * görsellerde** kullanılır.
     *
     * Uygulamanın genel [coil3.ImageLoader]'ı bellek tasarrufu için `allowRgb565(true)`
     * ile kuruludur. RGB_565 kanal başına 5/6/5 bit olduğu için yumuşak gradyanlarda
     * (gökyüzü, sakura, ışık geçişleri) bantlaşmaya yol açar. Bu mod vitrin görselini
     * tam 8-bit renkte ve sınırlı ölçüde decode eder; ayrıntılar [HeroArtworkImagePolicy].
     */
    highQuality: Boolean = false
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
    val imageModel: Any? = when {
        isBlankModel -> null
        useBitmapBlur -> {
            // blurRadius.dp → px dönüşümü; yoğun ekranda daha yumuşak görünür
            val density = LocalDensity.current
            val radiusPx = with(density) { blurRadius.toPx() }.toInt()
            remember(model, radiusPx) {
                // +18 UYUMU (2026-10-14): çağran taraf özel başlıklı (Referer/User-Agent)
                // bir ImageRequest verdiyse istek kimliğini KORU — yalnızca dönüşümü ekle.
                // Aksi halde hotlink korumalı site görselleri bitmap-blur yolunda 403 olurdu.
                when (model) {
                    is ImageRequest -> model.newBuilder(context)
                        .transformations(KitsugiBlurSupport.bitmapBlurTransformation(radiusPx))
                        .build()
                    else -> ImageRequest.Builder(context)
                        .data(model)
                        .size(480, 720)
                        .precision(coil3.size.Precision.INEXACT)
                        .transformations(KitsugiBlurSupport.bitmapBlurTransformation(radiusPx))
                        .build()
                }
            }
        }
        // Vitrin: RGB_565 degrade'ini kapat, sınırlı ve sabit anahtarlı ölçü kullan.
        highQuality && model is String -> remember(model) {
            HeroArtworkImagePolicy.request(context, model)
        }
        else -> model
    }

    // Apply the blur at the Box level so that BOTH the placeholder background/shimmer
    // AND the loaded image are blurred — nothing leaks through during loading.
    Box(
        // ÇÖKME ÖNLEMİ: doğrudan Modifier.blur() yerine güvenli sarmalayıcı.
        // RenderEffect katmanı yalnızca API 31+ ve sınırlanmış yarıçapla açılır.
        modifier = modifier.then(
            Modifier.safeBlur(blurRadius, enabled = useRenderEffectBlur)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            val nsfwMainHandler = remember { Handler(Looper.getMainLooper()) }
            // PERF: callback'ler her recomposition'da yeni lambda üretmesin;
            // ayrıca AsyncImage onSuccess/onError çağrılarını çizim evresinde
            // tetikleyebildiği için state değişikliğini bir kare sonraya post'luyoruz
            // → RenderThread libhwui.so SIGSEGV (fault_addr=0x20) yarışı kapanır.
            val successCallback = remember(onImageAspectResolved, nsfwMainHandler) {
                onImageAspectResolved?.let { cb ->
                    { state: coil3.compose.AsyncImagePainter.State.Success ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()) {
                            val ratio = size.width / size.height
                            nsfwMainHandler.post { cb(ratio) }
                        }
                    }
                }
            }
            val errorCallback = remember(onLoadingFailed, nsfwMainHandler) {
                onLoadingFailed?.let { cb ->
                    { _: coil3.compose.AsyncImagePainter.State.Error ->
                        nsfwMainHandler.post { cb() }
                        Unit
                    }
                }
            }
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                onSuccess = successCallback,
                onError = errorCallback
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

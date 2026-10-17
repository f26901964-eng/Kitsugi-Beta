package com.kitsugi.animelist.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment

// Shared DETAIL artwork alignment. Home heroes use a fixed-height, fully cropped
// (ContentScale.Crop) artwork strip plus a blurred ambient backdrop, so changing or
// decoding an image never changes the home carousel's geometry or crop.

/**
 * Vitrin görselinin kırpma hizalaması:
 *
 * Gerçek en-boy oranı (`imageAspect`) biliniyorsa kırpma dinamik olarak hesaplanır:
 * yatayda görselin merkezine, dikeyde ise karakterlerin başını/yüzünü korumak için
 * üstten küçük bir pay (`topMargin = 5%` yatay, `2%` poster) bırakılacak şekilde odaklanır.
 *
 * Görsel kutudan daha darsa (ör. yatay arka plan dikey telefonda) yatay kırpma her iki yandan eşit
 * kaydırılır ve yatayda ortalanır. Böylece karakterlerin başı/yüzü asla üstten kesilmez,
 * aynı zamanda görsel gereğinden fazla "yukarı kaymış" görünmez.
 *
 * @param chosenIsPoster Dikey görsel mi?
 * @param heroAspect     Kutunun en-boy oranı (genişlik / yükseklik).
 * @param imageAspect    Görselin gerçek en-boy oranı; yüklenmediyse `null` (eski davranış).
 */
internal fun heroImageAlignment(
    chosenIsPoster: Boolean,
    heroAspect: Float,
    imageAspect: Float? = null
): Alignment {
    if (imageAspect != null && imageAspect.isFinite() && imageAspect > 0.05f &&
        heroAspect.isFinite() && heroAspect > 0.05f
    ) {
        val ratio = heroAspect / imageAspect
        return if (ratio > 1f) {
            // Kutu görselden geniş → görselin ALT/ÜST kısmı kırpılır.
            // Görünen yükseklik oranı: 1 / ratio
            val visible = (1f / ratio).coerceIn(0.05f, 1f)
            // Üstte korunacak pay: posterlerde 2%, yatay görsellerde 5%
            val topMargin = if (chosenIsPoster) 0.02f else 0.05f
            val bias = (topMargin + visible / 2f) - 0.5f
            BiasAlignment(0f, bias.coerceIn(-0.36f, 0.08f))
        } else {
            // Kutu görselden dar → görselin SAĞ/SOL kısmı kırpılır: yatayda ortala.
            // Posterlerde dikeyde hafif üst odak (yüzler genelde üst yarıda).
            BiasAlignment(0f, if (chosenIsPoster) -0.10f else 0f)
        }
    }
    // Gerçek oran henüz bilinmiyor → kanıtlanmış eski davranış (görsel yüklenince düzeltilir).
    return when {
        chosenIsPoster && heroAspect >= 1.15f -> BiasAlignment(0f, -0.25f)
        chosenIsPoster -> BiasAlignment(0f, -0.20f)
        heroAspect >= 1.85f -> BiasAlignment(0f, -0.32f)
        else -> Alignment.Center
    }
}


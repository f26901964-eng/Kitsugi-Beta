package com.kitsugi.animelist.ui.components

/**
 * Vitrin (hero) görselinin kutuya yerleşme biçimi.
 *
 * - [COVER]: Görsel kutuyu tamamen kaplar, taşan kısım kırpılır. En-boy oranı kutununkine
 *   yakınsa (ör. 16:9 arka plan + 16:9 yatay bant) kayıp neredeyse sıfırdır.
 * - [CONTAIN_WITH_FILL]: Görsel hiç kırpılmadan tamamen gösterilir; arkası aynı görselin
 *   yumuşak (karartılmış) bir dolgusuyla kapatılır. En-boy oranları çok farklıysa
 *   (ör. dikey poster + geniş yatay bant, ya da yatay 16:9 görsel + dikey uzun panel)
 *   "burnundan kırpılmış" görünümünü engeller.
 */
internal enum class HeroArtworkFit {
    COVER,
    CONTAIN_WITH_FILL
}

/**
 * En-boy oranı uyumsuzluğu bu oranı aşarsa kırpmak yerine "tam görünür + yumuşak dolgu"
 * moduna geçilir. 1.38 → 16:9 bir görselin en fazla ~%28'i kırpılır.
 */
internal const val HERO_FIT_TOLERANCE = 1.38f

/**
 * Detay sayfasının 16:9 sinematik bandı için **daha sıkı** uyum eşiği.
 * Bu eşik aşılırsa görsel kırpılmaz; "tam görünür + yumuşak dolgu" gösterilir.
 * 1.20 → 16:9 bir görselin en fazla ~%20'si kırpılır (ör. 2:1 görselde yalnızca %11).
 */
internal const val HERO_BANNER_FIT_TOLERANCE = 1.20f

/**
 * Görselin kutuya yerleşme biçimini gerçek en-boy oranlarına göre seçer.
 *
 * @param boxAspect   Kutu en-boy oranı (genişlik / yükseklik).
 * @param imageAspect Görselin gerçek en-boy oranı (genişlik / yükseklik); henüz yüklenmediyse `null`.
 */
internal fun heroArtworkFit(
    boxAspect: Float,
    imageAspect: Float?,
    tolerance: Float = HERO_FIT_TOLERANCE
): HeroArtworkFit {
    if (imageAspect == null || !imageAspect.isFinite() || imageAspect <= 0.05f) return HeroArtworkFit.COVER
    if (!boxAspect.isFinite() || boxAspect <= 0.05f) return HeroArtworkFit.COVER
    val mismatch = if (boxAspect >= imageAspect) boxAspect / imageAspect else imageAspect / boxAspect
    return if (mismatch > tolerance) HeroArtworkFit.CONTAIN_WITH_FILL else HeroArtworkFit.COVER
}

/** Kutu görselden belirgin şekilde geniş (yatay şerit) ise true. */
internal fun isWideHeroBox(boxAspect: Float, minAspect: Float = 1.15f): Boolean =
    boxAspect.isFinite() && boxAspect >= minAspect

/**
 * Device-based home hero height, shared with its loading skeleton.
 * Image dimensions, selected page and image/logo loading state are deliberately NOT
 * inputs: swiping or decoding artwork must never resize the band or the list below it.
 */
internal fun heroBandHeightDp(
    screenHeightDp: Float,
    isLandscape: Boolean,
    isTablet: Boolean,
    isTv: Boolean
): Float = when {
    isTv -> (screenHeightDp * 0.68f).coerceIn(380f, 500f)
    isLandscape && isTablet -> (screenHeightDp * 0.62f).coerceIn(360f, 520f)
    isLandscape -> (screenHeightDp * 0.74f).coerceIn(280f, 420f)
    isTablet -> (screenHeightDp * 0.46f).coerceIn(440f, 560f)
    else -> (screenHeightDp * 0.55f).coerceIn(430f, 530f)
}

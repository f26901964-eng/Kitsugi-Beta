package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.model.MediaType
import kotlin.math.max

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
 * Yatay/dikey ekran ve görsel tipine göre vitrin bandının yüksekliğini verir.
 *
 * Amaç: yatay modda 16:9 arka planların dikey kırpmasını en aza indirmek için bandı
 * mümkün olduğunca ekran yüksekliğine yakın tutmak; dikey modda ise poster alanını korumak.
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

// ─────────────────────────────────────────────────────────────────────────────────
// ANA SAYFA VİTRİNİ (KitsugiHeroSection): OTOMATİK GÖRSEL BOYUTU
// ─────────────────────────────────────────────────────────────────────────────────
// Detay sayfasında oturan mantığın birebir aynısı: vitrin bandı sabit bir yükseklik
// DEĞİL, gösterdiği görselin GERÇEK en-boy oranına kilitlenen bir kutudur.
//  - Dikey poster  → uzun-dar kutu,
//  - Yatay arka plan → geniş-kısa bant,
//  - Yatay mod     → mevcut ekran-duyarlı bant tavan olarak korunur (poster yoksa
//    "tam ortada contain" fallback mantığı bozulmasın diye bant uzamaz).

/** Dikey modda "uzun-dar telefon" eşiği (detay sayfasıyla aynı kural: ≤0.55 → poster). */
internal const val HOME_HERO_PORTRAIT_TALL_RATIO = 0.55f

/** Vitrin kutusunun kabul ettiği en küçük en-boy oranı (çok uzun poster taşmasın). */
internal const val HOME_HERO_MIN_ASPECT = 0.55f

/** Vitrin kutusunun kabul ettiği en büyük en-boy oranı (ultra-geniş banner taşmasın). */
internal const val HOME_HERO_MAX_ASPECT = 2.35f

/**
 * Dikey modda bant yüksekliğinin TABANI (ekran yüksekliğinin oranı).
 * 16:9 arka plan dar telefonda bandı aşırı kısaltmasın: başlık, rozetler ve gösterge
 * çubuğu daima görselin üstünde okunur kalsın.
 */
internal const val HOME_HERO_PORTRAIT_MIN_SCREEN_FRACTION = 0.34f

/** Dikey modda bant yüksekliğinin TAVANI (ekran yüksekliğinin oranı; detay ile aynı). */
internal const val HOME_HERO_PORTRAIT_MAX_SCREEN_FRACTION = 0.62f

/** Yatay modda bantın kısalabileceği en düşük oran (mevcut bant yüksekliğine göre). */
internal const val HOME_HERO_LANDSCAPE_MIN_BAND_FRACTION = 0.78f

/**
 * Ana sayfa vitrininde yatay arka planın BİRİNCİL olup olmayacağına karar veren
 * STABİL tercih (ekran oranından türer; kutu ölçüsünden türemez çünkü kutu zaten
 * gösterilen görselin oranına göre boyutlanır — döngüsel olurdu).
 *
 * - Manga/manhwa → her zaman dikey kapak birincil.
 * - Yatay mod → yatay arka plan birincil (bulunamazsa poster, contain ile oturur).
 * - Dikey mod → uzun-dar telefonda dikey poster, kare/tablet benzeri cihazlarda
 *   yatay arka plan (detay sayfasıyla aynı kural).
 */
internal fun homeHeroPrefersBackdrop(
    screenWidthDp: Int,
    screenHeightDp: Int,
    isLandscape: Boolean,
    mediaType: MediaType
): Boolean {
    if (mediaType == MediaType.Manga) return false
    if (isLandscape) return true
    val w = max(1, screenWidthDp).toFloat()
    val h = max(1, screenHeightDp).toFloat()
    return w / h > HOME_HERO_PORTRAIT_TALL_RATIO
}

/**
 * Vitrin bandının yüksekliğini (dp) gösterilen görselin gerçek en-boy oranından üretir.
 *
 * @param heroWidthDp             Vitrin kutusunun genişliği (dp).
 * @param screenHeightDp          Ekran yüksekliği (dp) — dikey mod tavan/tabanı için.
 * @param isLandscape             Yatay mod mu?
 * @param landscapeBandHeightDp   Yatay modda mevcut ekran-duyarlı bant yüksekliği (tavan).
 * @param imageAspect             Öndeki sayfanın yüklenen görselinin gerçek oranı; yoksa `null`.
 * @param defaultAspect           Görsel henüz yüklenmediyse varsayılan oran (2:3 / 16:9).
 */
internal fun homeHeroHeightDp(
    heroWidthDp: Float,
    screenHeightDp: Float,
    isLandscape: Boolean,
    landscapeBandHeightDp: Float,
    imageAspect: Float?,
    defaultAspect: Float
): Float {
    if (!heroWidthDp.isFinite() || heroWidthDp <= 0f) return landscapeBandHeightDp
    val aspect = (imageAspect ?: defaultAspect).coerceIn(HOME_HERO_MIN_ASPECT, HOME_HERO_MAX_ASPECT)
    val desired = heroWidthDp / aspect
    return if (isLandscape) {
        if (!landscapeBandHeightDp.isFinite() || landscapeBandHeightDp <= 0f) {
            desired
        } else {
            desired.coerceIn(
                landscapeBandHeightDp * HOME_HERO_LANDSCAPE_MIN_BAND_FRACTION,
                landscapeBandHeightDp
            )
        }
    } else {
        if (!screenHeightDp.isFinite() || screenHeightDp <= 0f) {
            desired
        } else {
            desired.coerceIn(
                screenHeightDp * HOME_HERO_PORTRAIT_MIN_SCREEN_FRACTION,
                screenHeightDp * HOME_HERO_PORTRAIT_MAX_SCREEN_FRACTION
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────
// BEKLENEN (ÖLÇÜLMEDEN) GÖRSEL ORANI — VİTRİN SARSINTISINI ÖNLEYEN SABİT
// ─────────────────────────────────────────────────────────────────────────────────
// Bant yüksekliği yalnızca yüklenen görselin oranına bırakılırsa; sayfa değişiminde
// görselin yüklenmesi beklenir, ölçüm gelince kutu bir kare sonra "yerine oturur".
// Gözle görülen bu sarsıntının tek çözümü, kutuyu görsel doğmadan önce de DOĞRU
// şekilde kurmaktır: poster her zaman 2:3, yatay arka plan her zaman 16:9'dur.
// Zaman uyumsuz ölçüm geldiğinde fark yoksa hiçbir yeniden yerleşim olmaz.

/** Dikey posterlerin varsayılan en-boy oranı (genişlik / yükseklik): 2:3. */
internal const val HERO_EXPECTED_POSTER_ASPECT = 2f / 3f

/** Yatay arka planların varsayılan en-boy oranı (genişlik / yükseklik): 16:9. */
internal const val HERO_EXPECTED_BACKDROP_ASPECT = 16f / 9f

/**
 * Görsel henüz ölçülmeden kullanılacak **beklenen** en-boy oranını verir.
 *
 * @param primaryIsPoster Vitrin planının ilk adayı dikey bir poster mi?
 */
internal fun expectedHeroAspect(primaryIsPoster: Boolean): Float =
    if (primaryIsPoster) HERO_EXPECTED_POSTER_ASPECT else HERO_EXPECTED_BACKDROP_ASPECT

/**
 * Vitrin görsel planının İLK adayı dikey poster mi?
 *
 * Plan mantığı (`heroImageCandidates`): tercih edilen tür elde varsa o birincildir,
 * yoksa diğeri gösterilir. Görsel henüz indirilmediği için oran ölçülemez; bant
 * yüksekliğini ilk kareden doğru kurabilmek adına beklenen oran bu tür bilgisinden
 * türetilir ([expectedHeroAspect]).
 */
internal fun primaryHeroArtworkIsPoster(
    hasPoster: Boolean,
    hasBackdrop: Boolean,
    preferBackdrop: Boolean
): Boolean = when {
    preferBackdrop && hasBackdrop -> false // yatay arka plan birincil
    !preferBackdrop && hasPoster -> true   // dikey poster birincil
    hasPoster -> true                      // tercih edilen tür yok → eldeki poster
    else -> false                          // yalnızca yatay arka plan var
}

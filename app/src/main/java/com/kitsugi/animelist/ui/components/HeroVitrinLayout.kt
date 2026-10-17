package com.kitsugi.animelist.ui.components

/**
 * Keşfet vitrinin (home hero) yerleşim politikası.
 *
 * ── Referans ──────────────────────────────────────────────────────────────
 * Açık kaynak Nuvio uygulamalarının vitrini referans alındı:
 *
 *  - **NuvioMobile** `composeApp/.../features/home/components/HomeHeroSection.kt`
 *    → Tek, tam kaplayan `ContentScale.Crop` backdrop; üstte bant, altta dikey
 *      gradyan scrim; içerik bloğu bandın altında; gösterge (dot) satırı içerik
 *      bloğunun içinde.
 *  - **NuvioTV** `ui/screens/home/ModernHomeHero.kt`
 *    → `ContentScale.Crop` backdrop + **yatay** gradyan (solda opak) + alttaki
 *      dikey şerit.
 *
 * ── Sorun (bu revizyonun sebebi) ───────────────────────────────────────────
 * Vitrin görseli `ContentScale.Fit` ile **çok geniş** (≈2.1) bir kutuya
 * sığdırılıyordu. Bu yüzden:
 *
 *  - 16:9 bir görsel yükseklikten sıkışıp **yanlarda boş kutu** bırakıyor,
 *  - 3:1 bir banner ince bir şeride düşüp gerisini düşük opaklıklı, **bulanık
 *    olmayan** hayalet kopyayla dolduruyor,
 *  - metin bloğu (≈190 dp) kendi yuvasına (174 dp) sığmadığı için görselin
 *    üstüne biniyor.
 *
 * ── Çözüm ─────────────────────────────────────────────────────────────────
 * Görsel **her zaman** kutuyu tamamen kaplar (`Crop`); kutu oranı cihaz
 * geometrisinden türetilir, görselden değil. Metin için ayrılmış sabit yuva
 * vardır ve metin görselin üstüne binmez.
 *
 *  - [HeroVitrinMode.STACKED] — dikey telefon: üstte oran sabit **görsel
 *    şeridi**, altta metin için ayrılmış yuva. Görsel asla yüklemez, asla
 *    kutu bırakmaz.
 *  - [HeroVitrinMode.OVERLAY] — yatay / TV / tablet: görsel bandın tamamını
 *    kaplar, metin Nuvio'daki gibi gradyan scrim'in üstüne bindirilir.
 *
 * Band yüksekliği, görsel şeridi yüksekliği ve metin yuvası **yalnızca cihaz
 * geometrisinden** türetilir. Görselin oranı, decode durumu veya sayfa değişimi
 * hiçbir ölçüyü değiştiremez (bkz. `HomeHeroPolicyTest`).
 */

/** Vitrinin yerleşim modu. */
internal enum class HeroVitrinMode {
    /** Dikey telefon: görsel şeridi üstte, metin altta ayrı yuvada. */
    STACKED,

    /** Yatay / TV / tablet: görsel bandı kaplar, metin scrim üzerinde. */
    OVERLAY
}

/** Vitrinin ölçüleri (dp). Hepsi cihaz geometrisinden türetilir. */
internal data class HeroVitrinLayout(
    val bandHeightDp: Float,
    val artworkHeightDp: Float,
    val textZoneHeightDp: Float,
    val mode: HeroVitrinMode,
    val screenWidthDp: Float
) {
    /** Görsel şeridinin kutu oranı (genişlik / yükseklik). */
    val artworkAspect: Float
        get() = if (artworkHeightDp > 0f) screenWidthDp / artworkHeightDp else HERO_ARTWORK_ASPECT
}

/** Sinematik görsel şeridi oranı (16:9). */
internal const val HERO_ARTWORK_ASPECT = 16f / 9f

// ── Metin yuvası bileşenleri (dp) ───────────────────────────────────────────
private const val HERO_SOURCE_ROW_HEIGHT_DP = 28f
private const val HERO_TITLE_SLOT_DP = 84f
private const val HERO_TITLE_SLOT_LANDSCAPE_DP = 64f
private const val HERO_SUBTITLE_DP = 40f
private const val HERO_SUBTITLE_LANDSCAPE_DP = 22f
private const val HERO_META_DP = 20f
private const val HERO_ITEM_SPACING_DP = 6f
private const val HERO_ITEM_SPACING_LANDSCAPE_DP = 4f
private const val HERO_BOTTOM_PADDING_DP = 44f

/** Görsel şeridinin alabileceği en küçük yükseklik; daha kısası görseli bozar. */
internal const val HERO_MIN_ARTWORK_HEIGHT_DP = 120f

/**
 * Metin bloğunun (kaynak rozeti + başlık/logo + alt başlık + meta) ihtiyaç
 * duydugu **sabit** yükseklik.
 *
 * `KitsugiHeroSection` içindeki yuvalarla birebir aynı değerleri kullanır; her
 * satır kendi yuvasını korur, böylece eksik alt başlık/puan veya geç yüklenen
 * logo başka sayfanın ölçüsünü kaydıramaz.
 */
internal fun heroTextZoneHeightDp(isLandscape: Boolean, fontScale: Float = 1f): Float {
    val scale = fontScale.coerceAtLeast(1f)
    val title = (if (isLandscape) HERO_TITLE_SLOT_LANDSCAPE_DP else HERO_TITLE_SLOT_DP) * scale
    val subtitle = if (isLandscape) HERO_SUBTITLE_LANDSCAPE_DP else HERO_SUBTITLE_DP
    val spacing = if (isLandscape) HERO_ITEM_SPACING_LANDSCAPE_DP else HERO_ITEM_SPACING_DP
    return HERO_SOURCE_ROW_HEIGHT_DP + spacing + title + spacing + subtitle + spacing +
        HERO_META_DP + HERO_BOTTOM_PADDING_DP
}

/**
 * Cihaz geometrisinden vitrin yerleşimini üretir.
 *
 * @param screenWidthDp  Ekran genişliği (dp).
 * @param screenHeightDp Ekran yüksekliği (dp).
 * @param isLandscape    Yatay mod.
 * @param isTablet       Tablet (kısa kenar ≥ 600 dp).
 * @param isTv           Android TV.
 * @param fontScale      Sistem yazı ölçeği.
 */
internal fun heroVitrinLayout(
    screenWidthDp: Float,
    screenHeightDp: Float,
    isLandscape: Boolean,
    isTablet: Boolean,
    isTv: Boolean,
    fontScale: Float = 1f
): HeroVitrinLayout {
    val textZone = heroTextZoneHeightDp(isLandscape, fontScale)
    // Büyük yazı ölçeğinde metin yuvası banttan fazla yer isteyebilir; bant o zaman
    // gereken minimuma göre büyür. Yine de cihaz geometrisinden türetilir.
    val band = maxOf(
        heroBandHeightDp(screenHeightDp, isLandscape, isTablet, isTv),
        textZone + HERO_MIN_ARTWORK_HEIGHT_DP
    )
    // Yatay, TV ve tablet modunda metin görselin üstüne bindirilir (Nuvio TV).
    // Dikey telefonda ise metin kendi yuvasında kalır; görsel üstte tam kaplar.
    val mode = if (isLandscape || isTv || isTablet) HeroVitrinMode.OVERLAY else HeroVitrinMode.STACKED
    val artwork = if (mode == HeroVitrinMode.STACKED) {
        (band - textZone).coerceAtLeast(HERO_MIN_ARTWORK_HEIGHT_DP)
    } else {
        band
    }
    return HeroVitrinLayout(
        bandHeightDp = band,
        artworkHeightDp = artwork,
        textZoneHeightDp = textZone,
        mode = mode,
        screenWidthDp = screenWidthDp
    )
}

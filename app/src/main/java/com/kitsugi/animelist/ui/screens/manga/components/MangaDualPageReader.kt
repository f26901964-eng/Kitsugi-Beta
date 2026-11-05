package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPagePairing
import com.kitsugi.animelist.data.manga.MangaTapAction
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2

/**
 * ÇİFT SAYFA (2-up / spread) okuyucu — tablet ve yatay (landscape) ekranlar için.
 *
 * Mantık:
 *  1. Sayfalar [MangaPagePairing.smartSpreads] ile eşleştirilir: yalnızca DİKİNE
 *     (portre) ve ardışık iki sayfa yan yana gelir. Araya giren geniş/yatay sayfa
 *     tek başına tam genişlikte gösterilir → poster tarzı sayfalar ikişer ikişer,
 *     çift sayfa taramaları bozulmadan okunur.
 *  2. Her sütun `weight(1f)` ile eşit bölünür ve yükseklik sütunla sınırlıdır;
 *     görsel `Fit` ölçeğiyle yerleşir → **hangi ekran boyutunda olursa olsun taşma yok**.
 *  3. RTL manga'da çiftin ilk sayfası SAĞ sütunda gösterilir ve kaydırma da
 *     sağdan sola olur; LTR'de tersi. Yani sıralama değil, okuma yönü belirler.
 *  4. Pager sayfa sayısı = çift (spread) sayısıdır. Ekran katmanı pager indeksini
 *     gerçek sayfa indeksine çevirir; böylece ilerleme kaydı, "sayfaya git" ve
 *     bölüm geçişi tek sayfa moduyla birebir aynı davranır.
 *
 * Jestler korunur: her sayfa [MangaPageItem] ile çizilir (Telephoto zoom,
 * dokunma alanları, iki parmakla parlaklık, uzun basış araçları).
 */
@Composable
fun MangaDualPageReader(
    pages: List<MangaPage>,
    spreads: List<List<Int>>,
    pagerState: PagerState,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    tapActions: Triple<MangaTapAction, MangaTapAction, MangaTapAction> =
        Triple(MangaTapAction.Previous, MangaTapAction.Menu, MangaTapAction.Next),
    brightnessGesture: Boolean = true,
    onBrightnessDrag: (Float) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    cropBorders: Boolean = false,
    splitWidePages: Boolean = false,
    rotateWidePages: Boolean = false,
    sidePaddingDp: Int = 0,
    showPageNumber: Boolean = true,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRtl = readingMode == ReadingMode.RightToLeft
    // RTL'de kaydırma yönü de sağdan sola olur (sayfa sırası değişmez).
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            key = { spreadIndex ->
                spreads.getOrNull(spreadIndex)?.joinToString("-") ?: "spread_$spreadIndex"
            },
            // Komşu çiftleri hazırda tut: hızlı sayfa geçişinde bekleme olmaz.
            beyondViewportPageCount = 2
        ) { spreadIndex ->
            val spread = spreads.getOrNull(spreadIndex) ?: return@HorizontalPager
            SpreadRow(
                spread = spread,
                pages = pages,
                isRtl = isRtl,
                loader = loader,
                readingMode = readingMode,
                colorFilterType = colorFilterType,
                fitMode = fitMode,
                tapActions = tapActions,
                brightnessGesture = brightnessGesture,
                onBrightnessDrag = onBrightnessDrag,
                onLongPress = onLongPress,
                cropBorders = cropBorders,
                splitWidePages = splitWidePages,
                rotateWidePages = rotateWidePages,
                sidePaddingDp = sidePaddingDp,
                showPageNumber = showPageNumber,
                onCenterTap = onCenterTap,
                onNextPage = onNextPage,
                onPrevPage = onPrevPage
            )
        }
    }
}

/**
 * Bir çifti — ya da yanına eşi olmayan geniş sayfayı — ekrana yerleştirir.
 * Sütunlar eşit ağırlıklıdır: içerik asla ekran dışına taşmaz.
 */
@Composable
private fun SpreadRow(
    spread: List<Int>,
    pages: List<MangaPage>,
    isRtl: Boolean,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode,
    colorFilterType: ColorFilterType,
    fitMode: MangaFitMode,
    tapActions: Triple<MangaTapAction, MangaTapAction, MangaTapAction>,
    brightnessGesture: Boolean,
    onBrightnessDrag: (Float) -> Unit,
    onLongPress: (() -> Unit)?,
    cropBorders: Boolean,
    splitWidePages: Boolean,
    rotateWidePages: Boolean,
    sidePaddingDp: Int,
    showPageNumber: Boolean,
    onCenterTap: () -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit
) {
    // Ekranda soldan sağa gösterim sırası: RTL'de çiftin ikinci sayfası solda.
    val ordered = MangaPagePairing.displayOrder(spread, isRtl)
    val twoUp = ordered.size == 2
    // Kenar boşluğu: her sütunun kendi içine yarım uygulanır (taşmaya yol açmaz).
    val horizontalInset = if (sidePaddingDp > 0) (sidePaddingDp.coerceIn(0, 120) / 2).dp else 0.dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ordered.forEachIndexed { slot, pageIndex ->
            val page = pages.getOrNull(pageIndex) ?: return@forEachIndexed

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = horizontalInset),
                contentAlignment = Alignment.Center
            ) {
                // Her sayfa kendi LTR yönünde çizilir: zoom/görsel yönü bozulmaz.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    MangaPageItem(
                        page = page,
                        loader = loader,
                        readingMode = readingMode,
                        colorFilterType = colorFilterType,
                        // Çift sayfada kesin sığdırma: oran korunur, kırpma yapılmaz.
                        fitMode = if (twoUp) MangaFitMode.FitScreen else fitMode,
                        tapActions = tapActions,
                        brightnessGesture = brightnessGesture,
                        onBrightnessDrag = onBrightnessDrag,
                        onLongPress = onLongPress,
                        cropBorders = if (twoUp) false else cropBorders,
                        splitWidePages = splitWidePages,
                        rotateWidePages = if (twoUp) false else rotateWidePages,
                        sidePaddingDp = 0,
                        showPageNumber = showPageNumber,
                        onCenterTap = onCenterTap,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Sütunlar arası ince ayırıcı: iki sayfa birbirine yapışmaz.
            if (slot < ordered.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF101014))
                )
            }
        }
    }
}

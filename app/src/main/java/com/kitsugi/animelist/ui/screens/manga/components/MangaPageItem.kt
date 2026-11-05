package com.kitsugi.animelist.ui.screens.manga.components

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.HourglassBottom
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaPageStatus
import com.kitsugi.animelist.data.manga.ReadingMode
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.kitsugi.animelist.data.manga.ColorFilterType
import com.kitsugi.animelist.data.manga.MangaFitMode
import com.kitsugi.animelist.data.manga.MangaTapAction
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Yatay (LTR/RTL) ve dikey pager okuma modları için tek sayfa bileşeni.
 *
 * — Telephoto'nun ZoomableAsyncImage bileşeni ile:
 *     • Çift tıklama ile yakınlaştırma/uzaklaştırma
 *     • Parmakla pinch-to-zoom
 *     • Büyük çözünürlüklü resimlerin kiremit döşemeli (subsampling) çizimi
 *     • OOM (Out of Memory) riskini sıfıra indirme
 *
 * — Sayfa Ready olana kadar yükleme göstergesi gösterilir.
 * — Hata durumunda "Tekrar Dene" butonu görünür.
 */
@Composable
fun MangaPageItem(
    page: MangaPage,
    loader: MangaPageLoaderV2,
    readingMode: ReadingMode = ReadingMode.RightToLeft,
    colorFilterType: ColorFilterType = ColorFilterType.Normal,
    fitMode: MangaFitMode = MangaFitMode.FitScreen,
    onCenterTap: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    /** Kenar/orta dokunma davranışları (Mihon "tap zones" paritesi). */
    tapActions: Triple<MangaTapAction, MangaTapAction, MangaTapAction> =
        Triple(MangaTapAction.Previous, MangaTapAction.Menu, MangaTapAction.Next),
    /** İki parmakla dikey kaydırma ile parlaklık değiştirme açık mı. */
    brightnessGesture: Boolean = true,
    /** Dokunulan sayfaya uzun basınca çağrılır (sayfayı kaydet / paylaş). */
    onLongPress: (() -> Unit)? = null,
    /** İki parmak dikey kaydırma miktarı (negatif = aşağı → karart). */
    onBrightnessDrag: (Float) -> Unit = {},
    /** Beyaz kenarları kırp (tarama temizliği). */
    cropBorders: Boolean = false,
    /** Geniş (çift sayfa) görselleri dikeyde ikiye böl. */
    splitWidePages: Boolean = false,
    /** Geniş görselleri 90° döndürerek ekrana sığdır. */
    rotateWidePages: Boolean = false,
    /** Kenar boşluğu (dp) — ince ekranlarda panelleri rahatlatır. */
    sidePaddingDp: Int = 0,
    /** Küçük sayfa numarası rozetini göster. */
    showPageNumber: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Sayfa nesnesi ilk kez görüldüğünde yüklemeyi başlat.
    // Key olarak identityHashCode kullanıyoruz: aynı sayfa nesnesi varsa yeniden tetiklenmez,
    // ama yeni bir bölüme geçildiğinde (yeni page nesneleri) effect yeniden çalışır.
    LaunchedEffect(System.identityHashCode(page)) {
        loader.loadPage(page)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(readingMode, tapActions, onLongPress) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        val leftEdge = width * 0.33f
                        val rightEdge = width * 0.66f
                        val action = when {
                            offset.x < leftEdge -> tapActions.first
                            offset.x > rightEdge -> tapActions.third
                            else -> tapActions.second
                        }
                        when (action) {
                            MangaTapAction.Previous -> onPrevPage()
                            MangaTapAction.Next -> onNextPage()
                            MangaTapAction.Menu -> onCenterTap()
                            MangaTapAction.None -> Unit
                        }
                    },
                    onLongPress = { onLongPress?.invoke() }
                )
            }
            // İki parmakla dikey kaydırma → parlaklık (tek parmak pager/zoom'da kalır).
            // Yalnızca iki veya daha fazla parmak basılıyken tüketilir; böylece
            // tek parmakla sayfa çevirme ve pinch-zoom davranışı bozulmaz.
            .pointerInput(brightnessGesture) {
                if (!brightnessGesture) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            val dy = event.changes.first().positionChange().y
                            if (dy != 0f) {
                                event.changes.forEach { change -> change.consume() }
                                // Yukarı kaydırma (dy<0) = parlaklığı artır.
                                onBrightnessDrag(-dy / 700f)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (page.status) {
            MangaPageStatus.Ready -> {
                val imageUrl = page.imageUrl ?: page.url
                val cacheFile = loader.cache.getImageFile(imageUrl)
                val modelToLoad: Any = if (cacheFile.exists()) cacheFile else imageUrl

                val context = LocalContext.current
                val imageRequest = remember(modelToLoad) {
                    ImageRequest.Builder(context)
                        .data(modelToLoad)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200)
                        .build()
                }

                val zoomableState = rememberZoomableState()
                val imageState    = rememberZoomableImageState(zoomableState)

                val contentScale = when (fitMode) {
                    MangaFitMode.FitScreen -> ContentScale.Fit
                    MangaFitMode.FitWidth -> ContentScale.FillWidth
                    MangaFitMode.FitHeight -> ContentScale.FillHeight
                }

                val composeColorFilter = when (colorFilterType) {
                    ColorFilterType.Normal -> null
                    ColorFilterType.Grayscale -> {
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                                0.2126f, 0.7152f, 0.0722f, 0f, 0f,
                                0f,      0f,      0f,      1f, 0f
                            )
                        )
                        ColorFilter.colorMatrix(matrix)
                    }
                    ColorFilterType.Sepia -> {
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                0.393f, 0.769f, 0.189f, 0f, 0f,
                                0.349f, 0.686f, 0.168f, 0f, 0f,
                                0.272f, 0.534f, 0.131f, 0f, 0f,
                                0f,     0f,     0f,     1f, 0f
                            )
                        )
                        ColorFilter.colorMatrix(matrix)
                    }
                    ColorFilterType.Invert -> {
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                -1f,  0f,  0f, 0f, 1f,
                                 0f, -1f,  0f, 0f, 1f,
                                 0f,  0f, -1f, 0f, 1f,
                                 0f,  0f,  0f, 1f, 0f
                            )
                        )
                        ColorFilter.colorMatrix(matrix)
                    }
                }

                // ─── Görüntü işleme: kırpma / döndürme / yan boşluk ─────────
                // `cropBorders`: kenar boşluklarını keser (ContentScale.Crop + boyut
                // kırpma etkisi). `rotateWidePages`: geniş sayfayı 90° çevirerek
                // dikey ekranda okunur hâle getirir (Mihon "Rotate wide pages").
                val pageModifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = sidePaddingDp.coerceIn(0, 120).dp)

                val rotated = rotateWidePages
                Box(
                    modifier = pageModifier
                        .graphicsLayer { if (rotated) rotationZ = 90f }
                ) {
                    ZoomableAsyncImage(
                        model       = imageRequest,
                        contentDescription = stringResource(R.string.manga_sayfasi_x, page.index + 1),
                        state       = imageState,
                        contentScale = if (cropBorders && fitMode == MangaFitMode.FitScreen) ContentScale.Crop else contentScale,
                        colorFilter = composeColorFilter,
                        // `splitWidePages` açıkken geniş sayfalar Crop ile ekranı doldurur;
                        // kullanıcı iki parmakla kaydırarak diğer yarıyı okur.
                        modifier    = Modifier.fillMaxSize()
                    )
                }
            }

            MangaPageStatus.Error -> {
                ErrorPageContent(
                    pageIndex = page.index,
                    onRetry   = { loader.retryPage(page) }
                )
            }

            else -> {
                LoadingPageContent(page = page)
            }
        }

        // Sağ alt köşede küçük sayfa numarası (ayarla kapatılabilir).
        if (showPageNumber) {
            Text(
                text     = "${page.index + 1}",
                color    = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun LoadingPageContent(page: MangaPage) {
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .background(Color(0xFF0A0A0A)),
        contentAlignment  = Alignment.Center
    ) {
        when (page.status) {
            MangaPageStatus.DownloadImage,
            MangaPageStatus.LoadPage -> {
                KitsugiPlasmaLoader(size = 46.dp)
            }
            MangaPageStatus.Queue -> {
                Icon(
                    imageVector         = Icons.Default.HourglassBottom,
                    contentDescription  = null,
                    tint                = Color.White.copy(alpha = 0.3f),
                    modifier            = Modifier.size(40.dp)
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun ErrorPageContent(pageIndex: Int, onRetry: () -> Unit) {
    Box(
        modifier          = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFF120A0A)),
        contentAlignment  = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector        = Icons.Default.BrokenImage,
                contentDescription = null,
                tint               = Color(0xFFEF5350),
                modifier           = Modifier.size(52.dp)
            )
            Text(
                text      = stringResource(R.string.inline_page_1_s_couldn_t_11d924a, pageIndex + 1),
                color     = Color.White.copy(alpha = 0.7f),
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onRetry) {
                Text("Tekrar Dene", color = Color(0xFF7C4DFF))
            }
        }
    }
}

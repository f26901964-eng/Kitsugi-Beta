package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Aşağı çekerek yenileme göstergesi: uygulamanın her yerinde aynı görünür.
 *
 * Varsayılan Material göstergesi (siyah yuvarlak içinde dönen ok) yerine, tam ekran
 * yüklenme animasyonundaki plazma kürenin ([KitsugiPlasmaLoader]) aynısı kullanılır —
 * kullanıcı çekmeye başlayınca küre belirir, çekiş mesafesiyle büyür; yenileme
 * sürerken tam boyunda nabız gibi atmaya devam eder. Çekiş yokken hiç çizilmez,
 * böylece boşta duran sayfalarda sonsuz animasyon maliyeti oluşmaz.
 *
 * `PullToRefreshBox`'ın `indicator` yuvasında, üst ortada hizalanarak kullanılır:
 * ```
 * indicator = {
 *     KitsugiPullToRefreshIndicator(
 *         state = pullRefreshState,
 *         isRefreshing = isRefreshing,
 *         modifier = Modifier.align(Alignment.TopCenter)
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
    if (fraction <= 0.01f) return

    Box(
        modifier = modifier.padding(top = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Çekişin başında minik bir tohum gibi belirir, eşikte tam boyuna ulaşır.
        val scale = if (isRefreshing) 1f else 0.35f + 0.65f * fraction
        KitsugiPlasmaLoader(
            size = 40.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = fraction
            }
        )
    }
}

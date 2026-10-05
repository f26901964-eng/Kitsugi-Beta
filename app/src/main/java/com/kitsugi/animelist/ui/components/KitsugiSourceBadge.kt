package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Platform kaynak rozeti — poster görselinin sol-alt köşesine yerleştirilmek üzere tasarlanmıştır.
 * Harfler veya gereksiz arka plan kutusu olmadan, doğrudan platformun orijinal logosunu
 * rozet şekline (sol-alt ve sağ-üst köşe yuvarlatmalı) uyarlanmış olarak gösterir.
 *
 * Desteklenen kaynaklar: anilist, mal/jikan, tmdb, simkl, kitsu, shikimori.
 * Bilinmeyen kaynaklar için hiçbir şey gösterilmez.
 */
@Composable
fun KitsugiSourceBadge(
    source: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val normalized = source.lowercase()

    // Bilinmeyen kaynak için rozet gösterme
    if (KitsugiPlatformLogos.resFor(normalized) == null) return

    val badgeShape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 6.dp)

    KitsugiPlatformLogo(
        platformId = normalized,
        modifier = modifier,
        size = size,
        shape = badgeShape
    )
}

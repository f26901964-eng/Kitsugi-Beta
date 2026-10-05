package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Platform kaynak rozeti — poster görselinin sol-alt köşesine yerleştirilmek üzere tasarlanmıştır.
 * Artık harfler yerine her platformun orijinal logosunu gösterir (KitsugiPlatformLogo aracılığıyla).
 *
 * Desteklenen kaynaklar: anilist, mal/jikan, tmdb, simkl, kitsu, shikimori.
 * Bilinmeyen kaynaklar için hiçbir şey gösterilmez.
 */
@Composable
fun KitsugiSourceBadge(
    source: String,
    modifier: Modifier = Modifier
) {
    val normalized = source.lowercase()

    // Bilinmeyen kaynak için rozet gösterme
    val logoRes = KitsugiPlatformLogos.resFor(normalized) ?: return

    // Logo arka planı: platformun marka rengi
    val bgColor = when (normalized) {
        "anilist"          -> Color(0xFF02A9FF)
        "mal", "jikan"     -> Color(0xFF2E51A2)
        "tmdb"             -> Color(0xFF032541)
        "simkl"            -> Color(0xFF1A1A1A)
        "kitsu"            -> Color(0xFFE35A02)
        "shikimori"        -> Color(0xFF4C86C8)
        else               -> Color(0xFF1A1A1A)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomStart = 6.dp))
            .background(bgColor.copy(alpha = 0.88f))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        KitsugiPlatformLogo(
            platformId = normalized,
            size = 18.dp,
            cornerRadius = 3.dp
        )
    }
}

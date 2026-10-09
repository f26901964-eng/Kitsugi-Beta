package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.toFriendlySourceLabel

/**
 * Platform kaynak rozeti — poster görselinin sol-alt köşesine yerleştirilmek üzere tasarlanmıştır.
 * Harfler veya gereksiz arka plan kutusu olmadan, doğrudan platformun orijinal logosunu
 * rozet şekline (sol-alt ve sağ-üst köşe yuvarlatmalı) uyarlanmış olarak gösterir.
 *
 * Desteklenen kaynaklar: anilist, mal/jikan, tmdb, simkl, kitsu, shikimori, bangumi.
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

/**
 * Kaynak adını GÖRSEL olarak gösteren ortak çip: platformun orijinal logosu +
 * dostça adı (örn. "Bangumi", "MyAnimeList", "Shikimori"). Vitrin (hero),
 * detay yükleme ekranı ve detay sayfaları dahil tüm kaynaklarda aynı biçimde
 * kullanılır — böylece her kaynakta isim+logo tutarlı şekilde görünür.
 *
 * Bilinmeyen kaynaklar için hiçbir şey çizilmez.
 */
@Composable
fun KitsugiSourceNamePill(
    source: String,
    modifier: Modifier = Modifier,
    logoSize: Dp = 17.dp
) {
    val normalized = source.trim().lowercase()
    val label = normalized.toFriendlySourceLabel()
    val known = KitsugiPlatformLogos.resFor(normalized) != null || label != normalized
    if (!known) return

    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.Background.copy(alpha = 0.72f))
            .border(1.dp, KitsugiColors.TextPrimary.copy(alpha = 0.16f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KitsugiPlatformLogo(platformId = normalized, size = logoSize)
        Text(
            text = label,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Platform kaynak rozeti — AniHyou + MoeList referans tasarımından ilham alınmıştır.
 * Poster görselinin sol-alt köşesine yerleştirilmek üzere tasarlanmıştır.
 *
 * Desteklenen kaynaklar:
 *  - "anilist"           → mavi arka plan, "AL" etiketi
 *  - "mal" / "jikan"     → lacivert arka plan, "MAL" etiketi
 *  - "tmdb"              → koyu lacivert arka plan, "TMDB" etiketi
 *  - "simkl"             → koyu arka plan, "SK" etiketi
 *  - "kitsu"             → turuncu arka plan, "KT" etiketi
 *  - "shikimori"         → mavi arka plan, "SHI" etiketi
 *  - diğerleri           → şeffaf / gösterilmez
 */
@Composable
fun KitsugiSourceBadge(
    source: String,
    modifier: Modifier = Modifier
) {
    val normalized = source.lowercase()

    // Platform renklerini ve etiketlerini tanımla
    val (label, bgColor, textColor) = when (normalized) {
        "anilist"          -> Triple("AL",   Color(0xFF02A9FF), Color.White) // AniList mavi
        "mal", "jikan"     -> Triple("MAL",  Color(0xFF2E51A2), Color.White) // MyAnimeList lacivert
        "tmdb"             -> Triple("TMDB", Color(0xFFFFB800), Color.Black) // TMDB sarı
        "simkl"            -> Triple("SK",   Color(0xFF1F1F1F), Color.White) // Simkl koyu
        "kitsu"            -> Triple("KT",   Color(0xFFE35A02), Color.White) // Kitsu turuncu
        "shikimori"        -> Triple("SHI",  Color(0xFF4C86C8), Color.White) // Shikimori mavi
        else -> return                                      // Bilinmeyen kaynak → gösterme
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 10.dp, bottomStart = 6.dp))
            .background(bgColor.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.5.sp
        )
    }
}

package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.utils.toFriendlySourceLabel

@Composable
fun HeroTopPill(
    text: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KitsugiColors.Background.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HeroActionButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    primary && enabled -> accentColor
                    primary && !enabled -> KitsugiColors.AccentGreen
                    else -> KitsugiColors.Surface.copy(alpha = 0.88f)
                }
            )
            .tvClickable(
                enabled = enabled,
                shape = RoundedCornerShape(999.dp),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) {
                KitsugiColors.Background
            } else {
                KitsugiColors.TextPrimary
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}

fun buildHeroMeta(
    result: JikanSearchResult,
    scoreFormat: String,
    hideScores: Boolean
): String {
    val parts = buildList {
        val typeLabel = when (result.type) {
            MediaType.Anime -> "Anime"
            MediaType.Manga -> "Manga"
            MediaType.Movie -> "Film"
            MediaType.TvShow -> "Dizi"
        }
        add(typeLabel)
        val sourceLabel = result.source.toFriendlySourceLabel()
        add(sourceLabel)
        if (result.year != null) add(result.year.toString())
        if (!hideScores) {
            val scoreStr = result.getDisplayScore(scoreFormat, hideScores)
            if (scoreStr.isNotEmpty() && scoreStr != "N/A" && scoreStr != "0") {
                add(scoreStr)
            }
        }
        // Vitrin seçim kriterlerini görünür kıl: üye/izlenme ve favorileme sayısı.
        result.members?.takeIf { it > 0 }?.let { add("${compactStat(it)} üye") }
        result.favorites?.takeIf { it > 0 }?.let { add("${compactStat(it)} favori") }
        if (result.total != null) add("Toplam ${result.total}")
    }

    return parts.joinToString(" • ")
}

/** Büyük sayıları kısa gösterir: 3_400_000 → "3.4M", 128_000 → "128K". */
private fun compactStat(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

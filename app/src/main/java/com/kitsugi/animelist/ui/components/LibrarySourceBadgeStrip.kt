package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border

/** Extra memberships belong inside the card, never in a detached row below its frame. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySourceBadgeStrip(
    sources: List<String>
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.SurfaceSoft)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        sources.forEach { source ->
            val shape = RoundedCornerShape(7.dp)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(shape)
                    .background(KitsugiColors.Surface)
                    .border(1.dp, KitsugiColors.Border, shape),
                contentAlignment = Alignment.Center
            ) {
                // Keep the original platform artwork; theme only the surrounding tile.
                KitsugiPlatformLogo(platformId = source, size = 18.dp)
            }
        }
    }
}

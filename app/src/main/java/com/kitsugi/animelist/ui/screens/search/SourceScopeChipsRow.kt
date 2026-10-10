package com.kitsugi.animelist.ui.screens.search

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * Seçilen katalog motoruna ait alt kapsam sekmeleri (Anime, Manga, Karakter vb.).
 * Kalabalık 10 sekmeli karmaşık yapının yerine seçili motora özel temiz ve odaklı sekmeler sunar.
 */
@Composable
fun SourceScopeChipsRow(
    selectedEngine: SearchSourceEngine,
    selectedScope: SearchScope,
    onScopeSelected: (SearchScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val scopes = selectedEngine.availableScopes()
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scopes.forEach { scope ->
            val isSelected = scope == selectedScope

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.18f)
                        else KitsugiColors.SurfaceElevated.copy(alpha = 0.7f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accentColor else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onScopeSelected(scope) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = scope.displayLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accentColor else KitsugiColors.TextSecondary
                    )
                )
            }
        }
    }
}

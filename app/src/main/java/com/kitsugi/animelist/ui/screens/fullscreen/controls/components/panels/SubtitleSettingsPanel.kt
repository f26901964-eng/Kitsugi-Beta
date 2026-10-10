package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.panels

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Slider
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings

/**
 * Altyazı stil penceresi.
 *
 * Önceki sürümde landscape (yatay) modda kart kolonu ConstraintLayout içinde yalnızca
 * üst/sağ kenarına bağlıydı ve yüksekliği sınırlı değildi; içerik ekran dışına taşıyordu.
 * Artık tüm panel ekran boyutuna sabitlenir, güvenli alan (çentik / sistem çubukları)
 * dikkate alınır ve içerik dikey olarak kaydırılabilir. Yatay modda sağ kenara, dikey
 * modda ortaya hizalanır; genişlik her iki modda da sınırlıdır.
 */
@Composable
fun SubtitleSettingsPanel(
    subtitleStyle: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismissRequest)
    val isPortrait = LocalConfiguration.current.orientation == ORIENTATION_PORTRAIT

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = if (isPortrait) Alignment.TopCenter else Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = if (isPortrait) PORTRAIT_PANEL_MAX_WIDTH else LANDSCAPE_PANEL_MAX_WIDTH)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = if (isPortrait) Icons.AutoMirrored.Default.ArrowBack else Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Text(
                    text = "Altyazı Ayarları",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = Shadow(blurRadius = 20f)),
                )
                // Simetri için görünmez yer tutucu
                Spacer(Modifier.height(48.dp).widthIn(min = 48.dp))
            }

            SubtitleTypographyCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
            SubtitleMiscCard(subtitleStyle, onStyleChange, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val PORTRAIT_PANEL_MAX_WIDTH = 560.dp
private val LANDSCAPE_PANEL_MAX_WIDTH = CARDS_MAX_WIDTH + 80.dp

@Composable
private fun SubtitleTypographyCard(
    style: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(colors = panelCardsColors(), modifier = modifier.widthIn(max = CARDS_MAX_WIDTH)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yazı Tipi", style = MaterialTheme.typography.titleMedium)

            Text("Boyut: ${style.size}sp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = style.size.toFloat(),
                onValueChange = { onStyleChange(style.copy(size = it.toInt())) },
                valueRange = 10f..60f,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Kalın (Bold)")
                Switch(
                    checked = style.bold,
                    onCheckedChange = { onStyleChange(style.copy(bold = it)) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Kenarlık")
                Switch(
                    checked = style.outlineEnabled,
                    onCheckedChange = { onStyleChange(style.copy(outlineEnabled = it)) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleMiscCard(
    style: SubtitleStyleSettings,
    onStyleChange: (SubtitleStyleSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(colors = panelCardsColors(), modifier = modifier.widthIn(max = CARDS_MAX_WIDTH)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Çeşitli", style = MaterialTheme.typography.titleMedium)

            Text("Dikey Konum: ${style.verticalOffset}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = style.verticalOffset.toFloat(),
                onValueChange = { onStyleChange(style.copy(verticalOffset = it.toInt())) },
                valueRange = -200f..200f,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sadece tercih edilen dil")
                Switch(
                    checked = style.showOnlyPreferredLanguages,
                    onCheckedChange = { onStyleChange(style.copy(showOnlyPreferredLanguages = it)) },
                )
            }
        }
    }
}

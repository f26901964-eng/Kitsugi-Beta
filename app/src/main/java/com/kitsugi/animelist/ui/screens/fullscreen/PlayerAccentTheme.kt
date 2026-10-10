package com.kitsugi.animelist.ui.screens.fullscreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Oynatıcı (fullscreen video player) içindeki TÜM Material3 bileşenlerinin
 * (Slider, Switch, Checkbox, RadioButton, FilterChip, Button, ProgressIndicator,
 * OutlinedTextField odak kenarlığı vb.) uygulamanın o anki vurgu rengine
 * (`KitsugiColors.Accent`) otomatik uyum sağlaması için sarmalayıcı tema.
 *
 * Oynatıcı her zaman koyu yüzeyde çalıştığından `darkColorScheme` tabanlıdır;
 * ancak `primary`, `secondary`, `tertiary` ve konteyner renkleri doğrudan
 * seçili tema vurgusundan türetilir. Vurgu rengi ve arka plan yüzeyleri ise
 * seçili vurguyla hafifçe harmanlanarak (tint) oynatıcı genelinde bütünleşik bir
 * renk atmosferi oluşturur.
 */
@Composable
fun PlayerAccentTheme(
    content: @Composable () -> Unit
) {
    val accent = KitsugiColors.Accent
    val accent2 = KitsugiColors.Accent2 ?: accent
    val onAccent = KitsugiColors.OnAccent
    val tintedSurface = playerSurfaceColor()
    val tintedContainer = playerSurfaceContainerColor()

    val base = MaterialTheme.colorScheme
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.24f),
        onPrimaryContainer = Color.White,
        secondary = accent2,
        onSecondary = onAccent,
        secondaryContainer = accent2.copy(alpha = 0.20f),
        onSecondaryContainer = Color.White,
        tertiary = accent2,
        onTertiary = onAccent,
        tertiaryContainer = accent2.copy(alpha = 0.18f),
        onTertiaryContainer = Color.White,
        background = tintedSurface,
        onBackground = Color.White,
        surface = tintedSurface,
        onSurface = Color.White,
        surfaceVariant = tintedContainer,
        onSurfaceVariant = Color.White.copy(alpha = 0.78f),
        surfaceTint = accent,
        outline = accent.copy(alpha = 0.40f),
        outlineVariant = Color.White.copy(alpha = 0.14f)
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = base.typography,
        shapes = base.shapes,
        content = content
    )
}

/**
 * Vurgu rengi (`KitsugiColors.Accent` / gradyan) üzerinde kullanılacak metin/ikon rengi.
 * Ortak `KitsugiColors.OnAccent` hesabını kullanır (siyah ↔ beyaz yumuşak geçiş).
 */
@Composable
fun playerOnAccentColor(): Color = KitsugiColors.OnAccent

/**
 * Oynatıcı içindeki kart, panel ve sheet arka planları için seçili vurgu rengiyle
 * hafifçe harmanlanmış koyu yüzey rengi.
 */
@Composable
fun playerSurfaceColor(): Color =
    lerp(Color(0xFF0E131B), KitsugiColors.Accent, 0.08f)

/**
 * Oynatıcı içindeki ikincil kutu / konteyner yüzeyleri için vurgulu koyu renk.
 */
@Composable
fun playerSurfaceContainerColor(): Color =
    lerp(Color(0xFF171E29), KitsugiColors.Accent, 0.12f)

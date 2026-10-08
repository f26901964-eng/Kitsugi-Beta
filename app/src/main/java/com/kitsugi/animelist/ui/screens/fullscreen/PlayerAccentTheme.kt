package com.kitsugi.animelist.ui.screens.fullscreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Medya oynatıcının (ana ekran, sheet'ler, dialog'lar, sliderlar, butonlar) tamamını
 * uygulamada seçili tema rengiyle (KitsugiColors.Accent) uyumlu hale getirir.
 *
 * Material3 varsayılan `primary` rengi Android 12+ dinamik renklerinden (duvar kağıdı)
 * gelebildiği için oynatıcıda uygulama temasıyla uyuşmuyordu. Bu sarmalayıcı, oynatıcı
 * alt ağacındaki tüm `MaterialTheme.colorScheme.primary` kullanımlarını seçili vurgu rengine
 * bağlar.
 */
@Composable
fun PlayerAccentTheme(content: @Composable () -> Unit) {
    val accent = KitsugiColors.Accent
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val base = MaterialTheme.colorScheme
    val scheme = base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(Color.Black, accent, 0.30f),
        onPrimaryContainer = Color.White,
        secondary = accent,
        onSecondary = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
        surfaceTint = accent,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/**
 * Oynatıcı panel / sheet / dialog yüzey rengi: koyu zemin, seçili vurgu renginden hafif tonlanmış.
 */
@Composable
fun playerSurfaceColor(alpha: Float = 1f): Color =
    lerp(Color(0xFF0D0D16), KitsugiColors.Accent, 0.10f).copy(alpha = alpha)

/** Oynatıcı içinde kullanılan vurgu renginin üstündeki okunabilir metin/ikon rengi. */
@Composable
fun playerOnAccentColor(): Color =
    if (KitsugiColors.Accent.luminance() > 0.5f) Color.Black else Color.White

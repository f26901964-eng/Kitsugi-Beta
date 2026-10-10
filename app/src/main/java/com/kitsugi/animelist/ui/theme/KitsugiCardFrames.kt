package com.kitsugi.animelist.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Kart çerçevelerinin (neon degrade kenarlık) global açık/kapalı durumu.
 * MainActivity, `AppSettings.cardFramesEnabled` değerini bu yerel değere bağlar;
 * `kitsugiNeonGlow` ve `KitsugiNeonGlowCard` bu değere göre çerçeveyi çizer ya da atlar.
 */
val LocalCardFramesEnabled = compositionLocalOf { true }

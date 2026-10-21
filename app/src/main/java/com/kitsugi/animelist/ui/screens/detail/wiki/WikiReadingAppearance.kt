package com.kitsugi.animelist.ui.screens.detail.wiki

import kotlin.math.roundToInt

/** Pure policy shared by UI and WebView. SITE remains the fidelity-first default. */
internal object WikiReadingAppearance {
    val modes = listOf("SITE", "APP", "LIGHT", "DARK")
    fun normalize(mode: String) = mode.takeIf { it in modes } ?: "SITE"
    fun resolve(mode: String, appDark: Boolean): String = when (normalize(mode)) {
        "APP" -> if (appDark) "DARK" else "LIGHT"
        "LIGHT" -> "LIGHT"
        "DARK" -> "DARK"
        else -> "SITE"
    }
    fun textPercent(value: Int): Int = ((value.coerceIn(80, 180) / 10f).roundToInt() * 10)
    fun engineTextZoom(percent: Int, systemFontScale: Float): Int {
        val scale = systemFontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (textPercent(percent) * scale.coerceIn(0.5f, 3f)).roundToInt().coerceIn(50, 300)
    }
}

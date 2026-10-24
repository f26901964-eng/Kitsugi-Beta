package com.kitsugi.animelist.ui.screens.detail.wiki

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure policy shared by UI and WebView. SITE remains the fidelity-first default.
 *
 * v2.4.341: LIGHT/DARK no longer rely on the wiki theme class alone. Big wikis hard-code
 * colours in community CSS, so the page could look identical in every mode. The palette
 * below is handed to `page-appearance.js`, which switches the site contract *and* paints a
 * small, explicit colour layer. SITE still returns the document untouched.
 */
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

    /** `#rrggbb` for a packed ARGB colour. Alpha is irrelevant for an opaque canvas. */
    fun hex(argb: Int): String = String.format(Locale.ROOT, "#%06x", argb and 0xFFFFFF)

    private val hexPattern = Regex("#[0-9a-fA-F]{6}")
    private const val LIGHT_BACKGROUND = "#ffffff"
    private const val LIGHT_SURFACE = "#ffffff"
    private const val LIGHT_TEXT = "#171a1f"
    private const val DARK_BACKGROUND = "#0e1116"
    private const val DARK_SURFACE = "#141922"
    private const val DARK_TEXT = "#e9edf3"

    private fun validHex(value: String, fallback: String): String =
        value.takeIf { hexPattern.matches(it) }?.lowercase(Locale.ROOT) ?: fallback

    /** Linear blend from [from] towards [to]; every colour is `#rrggbb`. */
    fun mix(from: String, to: String, amount: Float): String {
        val source = validHex(from, DARK_BACKGROUND)
        val target = validHex(to, "#ffffff")
        val ratio = amount.coerceIn(0f, 1f)
        fun channel(hex: String, index: Int) = hex.substring(1 + index * 2, 3 + index * 2).toInt(16)
        fun blend(index: Int): Int =
            (channel(source, index) + (channel(target, index) - channel(source, index)) * ratio)
                .roundToInt().coerceIn(0, 255)
        return String.format(Locale.ROOT, "#%02x%02x%02x", blend(0), blend(1), blend(2))
    }

    /**
     * App palette when the app is already in the requested scheme, otherwise neutral
     * light/dark defaults. Keeping the seed colours makes "Uygulama temasını izle" feel
     * like the reader belongs to Kitsugi instead of being a foreign white/dark island.
     */
    fun palette(
        mode: String,
        appDark: Boolean,
        appBackground: String,
        appSurface: String,
        appText: String
    ): WikiAppearancePalette {
        val dark = resolve(mode, appDark) == "DARK"
        val useApp = appDark == dark
        val background = if (useApp) validHex(appBackground, if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND)
            else if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND
        val seededText = if (useApp) validHex(appText, if (dark) DARK_TEXT else LIGHT_TEXT)
            else if (dark) DARK_TEXT else LIGHT_TEXT
        // Invisible text is worse than a neutral palette: never paint text on its own colour.
        val text = if (seededText == background) if (dark) DARK_TEXT else LIGHT_TEXT else seededText
        val seeded = if (useApp) validHex(appSurface, if (dark) DARK_SURFACE else LIGHT_SURFACE)
            else if (dark) DARK_SURFACE else LIGHT_SURFACE
        // A pure-black app surface would erase every card; nudge it away from the canvas.
        val surface = if (seeded == background) mix(background, if (dark) "#ffffff" else "#000000", 0.05f)
            else seeded
        return WikiAppearancePalette(
            background = background,
            surface = surface,
            raised = mix(surface, if (dark) "#ffffff" else "#000000", if (dark) 0.06f else 0.04f),
            text = text,
            muted = mix(text, background, 0.42f),
            border = mix(surface, if (dark) "#ffffff" else "#000000", if (dark) 0.16f else 0.12f),
            link = if (dark) "#8cc8ff" else "#0b57d0"
        )
    }
}

/** Immutable colour set handed to the page. Values are validated `#rrggbb` strings. */
internal data class WikiAppearancePalette(
    val background: String,
    val surface: String,
    val raised: String,
    val text: String,
    val muted: String,
    val border: String,
    val link: String
) {
    val signature: String
        get() = listOf(background, surface, raised, text, muted, border, link).joinToString("|")

    /** Compact JSON, safe to embed in `evaluateJavascript` without escaping. */
    fun toJson(): String = buildString {
        append("{\"background\":\"").append(background)
        append("\",\"surface\":\"").append(surface)
        append("\",\"raised\":\"").append(raised)
        append("\",\"text\":\"").append(text)
        append("\",\"muted\":\"").append(muted)
        append("\",\"border\":\"").append(border)
        append("\",\"link\":\"").append(link).append("\"}")
    }
}

/**
 * Desktop layout is 1200 CSS px wide; a phone is ~360 dp. Without an explicit fit the page
 * opens at 100% and only a third of the article is visible until the user pinches out, then
 * the text becomes too small to read. Both halves of that trade-off are solved here:
 * the scale fits the width, and text zoom compensates so body text stays readable.
 */
internal object WikiReaderFit {
    const val DESKTOP_LAYOUT_WIDTH = 1200
    private const val MIN_SCALE = 0.2f
    private const val MIN_READABLE_SCALE = 0.75f
    private const val MAX_TEXT_BOOST = 2.2f

    fun layoutWidth(desktop: Boolean, deviceWidthDp: Float): Int = if (desktop) {
        DESKTOP_LAYOUT_WIDTH
    } else {
        deviceWidthDp.takeIf { it.isFinite() && it > 0f }?.roundToInt()?.coerceIn(320, 2000) ?: 360
    }

    fun scale(deviceWidthDp: Float, layoutWidth: Int): Float {
        if (!deviceWidthDp.isFinite() || deviceWidthDp <= 0f || layoutWidth <= 0) return 1f
        return (deviceWidthDp / layoutWidth).coerceIn(MIN_SCALE, 1f)
    }

    fun initialScalePercent(deviceWidthDp: Float, layoutWidth: Int): Int =
        (scale(deviceWidthDp, layoutWidth) * 100f).roundToInt().coerceIn(10, 100)

    /** 1.0 when the layout already fits; grows as the desktop layout is scaled down. */
    fun textBoost(scale: Float): Float {
        val safe = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (MIN_READABLE_SCALE / safe).coerceIn(1f, MAX_TEXT_BOOST)
    }

    /** Keeps effective body text near [MIN_READABLE_SCALE] of the layout after fitting. */
    fun readingTextZoom(baseEngineZoom: Int, scale: Float): Int =
        (baseEngineZoom * textBoost(scale)).roundToInt().coerceIn(50, 300)

    /** User-facing percentage: what the reader actually sees after fit compensation. */
    fun effectivePercent(userPercent: Int, scale: Float): Int =
        (WikiReadingAppearance.textPercent(userPercent) * textBoost(scale)).roundToInt().coerceIn(80, 396)
}

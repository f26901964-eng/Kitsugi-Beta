package com.kitsugi.animelist.ui.screens.detail.wiki

import android.os.SystemClock
import android.webkit.WebView
import kotlin.math.abs

/**
 * Applies the fit-to-screen scale to a live WebView.
 *
 * WebView exposes no `zoomTo`: the public path is `zoomBy(ratio)` relative to `getScale()`.
 * A programmatic zoom also fires `onScaleChanged`, so a short grace window marks our own
 * change; afterwards any scale change is treated as a user pinch and stops automatic
 * refitting for the rest of the document. The explicit "fit to screen" action re-enables it.
 *
 * Desktop layout is 1200 CSS px wide, which is ~3.3x a phone. Fitting it without a text
 * boost would make the article unreadable, so [WikiReaderFit.readingTextZoom] compensates.
 */
internal class WikiViewportFit(
    private val view: WebView,
    private val desktop: Boolean
) {
    private var ignoreScaleUntil = 0L
    private var userZoomed = false

    fun onScaleChanged() {
        if (SystemClock.uptimeMillis() < ignoreScaleUntil) return
        userZoomed = true
    }

    fun resetForDocument() {
        userZoomed = false
        // Yeni belge yüklenirken motor kendi ölçeğini birkaç kez değiştirir; bu değişimler
        // kullanıcı yakınlaştırması sayılmamalı, yoksa sığdırma hiç uygulanmaz.
        ignoreScaleUntil = SystemClock.uptimeMillis() + 2500L
    }

    /** @return the scale that was applied, or `null` when the page already fits. */
    fun apply(force: Boolean = false): Float? {
        // Mobile layout owns its viewport; only an explicit request touches the zoom.
        if (!desktop && !force) return null
        if (!force && userZoomed) return null
        if (view.width <= 0) return null
        val density = view.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val deviceWidthDp = view.width / density
        val target = if (desktop) {
            WikiReaderFit.scale(deviceWidthDp, WikiReaderFit.DESKTOP_LAYOUT_WIDTH)
        } else {
            1f
        }
        val current = view.scale
        if (current <= 0f) return null
        if (abs(target - current) <= current * 0.02f) {
            if (force) userZoomed = false
            return null
        }
        ignoreScaleUntil = SystemClock.uptimeMillis() + 400L
        view.zoomBy(target / current)
        if (force) userZoomed = false
        return target
    }

    companion object {
        fun forDevice(view: WebView, desktop: Boolean) = WikiViewportFit(view, desktop)
    }
}

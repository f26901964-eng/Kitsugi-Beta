package com.kitsugi.animelist.ui.screens.detail.wiki

import java.util.Locale

internal data class WikiPageToolConfig(
    val translate: Boolean = false,
    val automaticTranslate: Boolean = false,
    val source: String = "auto",
    val target: String = "tr",
    val blockAds: Boolean = true,
    val disabledAdHosts: Set<String> = emptySet(),
    val retry: Int = 0
)

internal data class WikiPagePreferences(
    val autoTranslate: Boolean,
    val source: String,
    val target: String,
    val disabledAdHosts: Set<String>,
    val pageTheme: String = "SITE",
    val textZoom: Int = 100
) {
    val translationKey get() = "$autoTranslate:$source:$target"

    fun configFor(host: String, manual: Boolean?, previousKey: String?, retry: Int): WikiPageToolConfig {
        val manualChoice = manual.takeUnless { previousKey != null && previousKey != translationKey }
        return WikiPageToolConfig(
            translate = manualChoice ?: autoTranslate,
            automaticTranslate = autoTranslate,
            source = language(source, "auto"),
            target = language(target.takeUnless { it.equals("auto", true) }, "tr"),
            blockAds = host.lowercase(Locale.ROOT) !in disabledAdHosts,
            disabledAdHosts = disabledAdHosts,
            retry = retry
        )
    }

    private fun language(value: String?, fallback: String): String = value?.trim()?.takeIf {
        it.matches(Regex("[a-zA-Z]{2,8}(?:-[a-zA-Z0-9]{2,8})*"))
    } ?: fallback
}

package com.kitsugi.animelist.ui.screens.detail.wiki

import java.net.URI
import java.util.Locale

/** Immutable, thread-safe DNS-style matcher. No regex engine or I/O on a request callback.
 * This is NOT an EasyList/ABP parser; unsupported filter syntax is never misinterpreted.
 */
internal class WikiAdBlockRules private constructor(private val domains: Set<String>) {
    val ruleCount: Int get() = domains.size

    fun shouldBlock(url: String, isMainFrame: Boolean): Boolean {
        if (isMainFrame || (!url.startsWith("https://", true) && !url.startsWith("http://", true))) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        // Scoped first-party advertising routes, not whole Fandom service/CDN origins.
        if (host == "services.fandom.com" && uri.path.orEmpty().startsWith("/ads/")) return true
        if (host == "ads.fandom.com") return true
        if (PROTECTED.any { host == it || host.endsWith(".$it") }) return false
        var suffix = host
        while (suffix.contains('.')) {
            if (suffix in domains) return true
            suffix = suffix.substringAfter('.')
        }
        return false
    }

    companion object {
        private val PROTECTED = setOf("fandom.com", "wikia.com", "wikia.nocookie.net", "wikimedia.org", "wikipedia.org", "mediawiki.org")
        // Kitsugi-maintained advertising-only base domains supplement the contributed
        // StevenBlack list, which is not the aggregate list of all its upstream projects.
        private val AD_NETWORKS = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagservices.com", "adnxs.com", "adsrvr.org", "rubiconproject.com",
            "pubmatic.com", "openx.net", "openx.com", "amazon-adsystem.com",
            "casalemedia.com", "indexww.com", "criteo.com", "criteo.net",
            "taboola.com", "outbrain.com", "yieldmo.com", "sharethrough.com",
            "advertising.com", "adform.net", "smartadserver.com", "lijit.com",
            "33across.com", "triplelift.com", "sovrn.com", "adlightning.com",
            "ad-delivery.net", "teads.tv", "undertone.com", "videoamp.com"
        )
        private val DOMAIN = Regex("(?=.{3,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")

        fun fromHosts(text: String): WikiAdBlockRules {
            val domains = AD_NETWORKS.toMutableSet()
            text.lineSequence().forEach { line ->
                val parts = line.substringBefore('#').trim().split(Regex("\\s+"))
                if (parts.size == 2 && parts[0] in setOf("0.0.0.0", "127.0.0.1")) {
                    val host = parts[1].lowercase(Locale.ROOT)
                    if (DOMAIN.matches(host)) domains.add(host)
                }
            }
            return WikiAdBlockRules(domains.toSet())
        }
    }
}

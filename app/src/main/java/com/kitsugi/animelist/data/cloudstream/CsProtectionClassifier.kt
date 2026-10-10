package com.kitsugi.animelist.data.cloudstream

import java.util.Locale

/**
 * Shared, deliberately conservative detection for explicit anti-bot/WAF challenge evidence.
 * Generic HTTP failures and connectivity errors are not proof of a Cloudflare/WAF block.
 */
internal object CsProtectionClassifier {

    private val webChallengeMarkers = listOf(
        "cloudflare",
        "cf-ray",
        "cf_clearance",
        "challenge-platform",
        "__waf_challenge",
        "cf-challenge",
        "cf-cookie-error",
        "ray id:",
        "just a moment",
        "checking your browser",
        "turnstile",
        "captcha",
        "security verification",
        "sucuri",
        "waf challenge",
        "firewall challenge",
        "verify you are human",
        "are you human",
        "browser verification",
        "bot verification",
        "bot protection",
        "robot olmadığınızı",
        "insan olduğunuzu",
        "tarayıcı doğrulama",
        "güvenlik doğrulaması"
    )

    fun isWafChallenge(message: String): Boolean {
        val normalized = message.lowercase(Locale.ROOT)
        return webChallengeMarkers.any { marker -> normalized.contains(marker) } ||
            (normalized.contains("challenge") &&
                (normalized.contains("waf") || normalized.contains("firewall")))
    }

    fun isDdosGuard(message: String): Boolean {
        val normalized = message.lowercase(Locale.ROOT)
        return normalized.contains("ddos-guard") ||
            normalized.contains("ddosguard") ||
            normalized.contains("d-d-o-s") ||
            normalized.contains("anti-ddos")
    }
}

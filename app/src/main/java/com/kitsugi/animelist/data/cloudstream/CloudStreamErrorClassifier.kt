package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.data.diagnostics.PlatformErrorCode
import java.util.Locale

/**
 * Shared CloudStream error mapping. Explicit protection signatures win first, followed by
 * recognizable HTTP limits, exception/parser/network evidence, empty results, then unknown.
 */
object CloudStreamErrorClassifier {

    fun classify(error: Throwable?): PlatformErrorCode {
        if (error == null) return PlatformErrorCode.CloudStreamUnknown
        val messages = mutableListOf<String>()
        val classNames = mutableListOf<String>()
        val seen = HashSet<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            messages += current.message.orEmpty()
            messages += current.localizedMessage.orEmpty()
            classNames += current.javaClass.name
            current = current.cause
        }
        return classifyEvidence(
            text = messages.joinToString(" "),
            classNames = classNames,
            noStreams = false,
        )
    }

    fun classifyText(
        text: String?,
        noStreams: Boolean = false,
        pluginUnsupported: Boolean = false,
    ): PlatformErrorCode = classifyEvidence(
        text = text.orEmpty(),
        classNames = emptyList(),
        noStreams = noStreams,
        pluginUnsupported = pluginUnsupported,
    )

    /** Classifies the failed phase details collected by the in-app plugin diagnostic runner. */
    fun classifyDiagnostic(
        phaseEvidence: List<String>,
        error: String?,
        noStreams: Boolean,
    ): PlatformErrorCode = classifyEvidence(
        text = (phaseEvidence + error.orEmpty()).joinToString(" "),
        classNames = phaseEvidence,
        noStreams = noStreams,
    )

    private fun classifyEvidence(
        text: String,
        classNames: List<String>,
        noStreams: Boolean,
        pluginUnsupported: Boolean = false,
    ): PlatformErrorCode {
        val normalized = text.lowercase(Locale.ROOT)
        val classes = classNames.joinToString(" ").lowercase(Locale.ROOT)

        return when {
            CsProtectionClassifier.isWafChallenge(normalized) ||
                CsProtectionClassifier.isDdosGuard(normalized) ||
                containsAny(classes, "cloudflareblockexception") ->
                PlatformErrorCode.CloudStreamProtection

            Regex("\\b(?:http\\s*)?429\\b").containsMatchIn(normalized) ||
                containsAny(
                    normalized,
                    "too many requests", "rate limit", "rate-limited", "rate limited",
                    "çok fazla istek", "cok fazla istek", "istek limiti", "istek sınırı", "istek siniri",
                ) ->
                PlatformErrorCode.CloudStreamRateLimited

            Regex("\\b(?:http\\s*)?403\\b").containsMatchIn(normalized) ->
                PlatformErrorCode.CloudStreamPluginUnsupported

            Regex("\\b(?:http\\s*)?(?:408|504)\\b").containsMatchIn(normalized) ||
                containsAny(
                    normalized,
                    "timeout", "timed out", "time out", "deadline exceeded",
                    "zaman aşımı", "zaman asimi", "süre doldu", "sure doldu",
                ) ||
                classes.contains("timeout") -> PlatformErrorCode.CloudStreamTimeout

            Regex("\\b(?:http\\s*)?401\\b").containsMatchIn(normalized) ->
                PlatformErrorCode.CloudStreamPluginUnsupported

            Regex("\\b(?:http\\s*)?(?:500|502|503)\\b").containsMatchIn(normalized) ->
                PlatformErrorCode.CloudStreamNetwork

            pluginUnsupported || containsAny(classes, "notimplementederror", "abstractmethoderror") || containsAny(
                normalized,
                "notimplementederror",
                "not implemented",
                "unsupported method",
                "metot desteklenmiyor",
                "eklenti engellendi",
                "plugin blocked",
                "desteklenmiyor",
            ) -> PlatformErrorCode.CloudStreamPluginUnsupported

            containsAny(
                normalized,
                "unable to resolve host",
                "unknown host",
                "failed to connect",
                "connection refused",
                "connection reset",
                "network is unreachable",
                "dns lookup",
                "ssl handshake",
                "dns çözümlenemedi",
                "dns cozulemedi",
                "bağlantı kurulamadı",
                "baglanti kurulamadi",
            ) || containsAny(classes, "unknownhost", "connectexception", "socketexception", "ssl") ->
                PlatformErrorCode.CloudStreamNetwork

            containsAny(
                normalized,
                "parser error",
                "parse error",
                "parsing error",
                "failed to parse",
                "could not parse",
                "malformed html",
                "malformed json",
                "unexpected token",
                "selector not found",
                "unable to extract",
                "failed to extract",
                "ayrıştırma hatası",
                "ayristirma hatasi",
                "ayrıştırılamadı",
                "ayristirilamadi",
            ) || containsAny(classes, "parseexception", "jsonparse", "jsonmapping", "selector") ->
                PlatformErrorCode.CloudStreamParser

            noStreams || containsAny(
                normalized,
                "no streams",
                "no stream found",
                "streams not found",
                "no results",
                "no result found",
                "nothing found",
                "empty search",
                "akış bulunamadı",
                "akış yok",
                "stream yok",
                "stream bulunamadı",
                "sonuç bulunamadı",
                "sonuc bulunamadi",
                "arama sonucu yok",
                "arama sonucu bulunamadı",
                "arama sonucu bulunamadi",
                "boş sonuç",
                "bos sonuc",
                "stream list empty",
                "empty stream list",
                "result list empty",
                "search results empty",
            ) -> PlatformErrorCode.CloudStreamNoStreams

            else -> PlatformErrorCode.CloudStreamUnknown
        }
    }

    private fun containsAny(value: String, vararg markers: String): Boolean = markers.any(value::contains)
}

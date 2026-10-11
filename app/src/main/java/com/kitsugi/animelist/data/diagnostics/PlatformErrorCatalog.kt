package com.kitsugi.animelist.data.diagnostics

import androidx.annotation.StringRes
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.model.SourceErrorCategory

/** Platforms that expose stable, user-facing diagnostic codes. */
enum class PlatformErrorPlatform {
    Manga,
    CloudStream,
}

/** Generates stable public IDs and guards the reserved numeric ranges for each platform. */
object PlatformErrorCodeGenerator {
    fun generate(platform: PlatformErrorPlatform, number: Int): String {
        val prefix = when (platform) {
            PlatformErrorPlatform.Manga -> "SRC"
            PlatformErrorPlatform.CloudStream -> "CSP"
        }
        val allowedRange = when (platform) {
            PlatformErrorPlatform.Manga -> 9001..9040
            PlatformErrorPlatform.CloudStream -> 9101..9140
        }
        require(number in allowedRange) {
            "$platform error-code number $number is outside the reserved range $allowedRange"
        }
        return "KTS-$prefix-$number"
    }
}

/** Shared severity values used by code chips and explanation blocks. */
enum class PlatformErrorSeverity {
    Info,
    Warning,
    Error,
}

/**
 * Stable diagnostic-code catalogue. Numbers are part of the public support/reporting format;
 * do not renumber existing entries. Unused values in each range remain reserved for later use.
 */
enum class PlatformErrorCode(
    val platform: PlatformErrorPlatform,
    val id: String,
    val category: String,
    @StringRes val titleRes: Int,
    @StringRes val causeRes: Int,
    @StringRes val actionRes: Int,
    val severity: PlatformErrorSeverity,
) {
    MangaTimeout(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9001), "Timeout",
        R.string.platform_error_src_timeout_title,
        R.string.platform_error_src_timeout_cause,
        R.string.platform_error_src_timeout_action,
        PlatformErrorSeverity.Warning,
    ),
    MangaRateLimited(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9002), "RateLimited",
        R.string.platform_error_src_rate_limited_title,
        R.string.platform_error_src_rate_limited_cause,
        R.string.platform_error_src_rate_limited_action,
        PlatformErrorSeverity.Warning,
    ),
    MangaCaptcha(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9003), "Captcha",
        R.string.platform_error_src_captcha_title,
        R.string.platform_error_src_captcha_cause,
        R.string.platform_error_src_captcha_action,
        PlatformErrorSeverity.Warning,
    ),
    MangaNotFound(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9004), "NotFound",
        R.string.platform_error_src_not_found_title,
        R.string.platform_error_src_not_found_cause,
        R.string.platform_error_src_not_found_action,
        PlatformErrorSeverity.Info,
    ),
    MangaNetwork(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9005), "Network",
        R.string.platform_error_src_network_title,
        R.string.platform_error_src_network_cause,
        R.string.platform_error_src_network_action,
        PlatformErrorSeverity.Error,
    ),
    MangaAuth(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9006), "Auth",
        R.string.platform_error_src_auth_title,
        R.string.platform_error_src_auth_cause,
        R.string.platform_error_src_auth_action,
        PlatformErrorSeverity.Warning,
    ),
    MangaParser(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9007), "Parser",
        R.string.platform_error_src_parser_title,
        R.string.platform_error_src_parser_cause,
        R.string.platform_error_src_parser_action,
        PlatformErrorSeverity.Error,
    ),
    MangaUnknown(
        PlatformErrorPlatform.Manga, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.Manga, 9008), "Unknown",
        R.string.platform_error_src_unknown_title,
        R.string.platform_error_src_unknown_cause,
        R.string.platform_error_src_unknown_action,
        PlatformErrorSeverity.Warning,
    ),

    CloudStreamProtection(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9101), "Protection",
        R.string.platform_error_csp_protection_title,
        R.string.platform_error_csp_protection_cause,
        R.string.platform_error_csp_protection_action,
        PlatformErrorSeverity.Warning,
    ),
    CloudStreamPluginUnsupported(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9102), "PluginUnsupported",
        R.string.platform_error_csp_plugin_unsupported_title,
        R.string.platform_error_csp_plugin_unsupported_cause,
        R.string.platform_error_csp_plugin_unsupported_action,
        PlatformErrorSeverity.Error,
    ),
    CloudStreamNoStreams(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9103), "NoStreams",
        R.string.platform_error_csp_no_streams_title,
        R.string.platform_error_csp_no_streams_cause,
        R.string.platform_error_csp_no_streams_action,
        PlatformErrorSeverity.Info,
    ),
    CloudStreamTimeout(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9104), "Timeout",
        R.string.platform_error_csp_timeout_title,
        R.string.platform_error_csp_timeout_cause,
        R.string.platform_error_csp_timeout_action,
        PlatformErrorSeverity.Warning,
    ),
    CloudStreamRateLimited(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9105), "RateLimited",
        R.string.platform_error_csp_rate_limited_title,
        R.string.platform_error_csp_rate_limited_cause,
        R.string.platform_error_csp_rate_limited_action,
        PlatformErrorSeverity.Warning,
    ),
    CloudStreamNetwork(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9106), "Network",
        R.string.platform_error_csp_network_title,
        R.string.platform_error_csp_network_cause,
        R.string.platform_error_csp_network_action,
        PlatformErrorSeverity.Error,
    ),
    CloudStreamParser(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9107), "Parser",
        R.string.platform_error_csp_parser_title,
        R.string.platform_error_csp_parser_cause,
        R.string.platform_error_csp_parser_action,
        PlatformErrorSeverity.Error,
    ),
    CloudStreamUnknown(
        PlatformErrorPlatform.CloudStream, PlatformErrorCodeGenerator.generate(PlatformErrorPlatform.CloudStream, 9108), "Unknown",
        R.string.platform_error_csp_unknown_title,
        R.string.platform_error_csp_unknown_cause,
        R.string.platform_error_csp_unknown_action,
        PlatformErrorSeverity.Warning,
    );

    companion object {
        private val byId = entries.associateBy { it.id }
        private val byNumericId = entries.associateBy { it.id.substringAfterLast('-') }

        fun fromId(value: String?): PlatformErrorCode? {
            val normalized = value?.trim()?.uppercase().orEmpty()
            if (normalized.isBlank()) return null
            return byId[normalized] ?: normalized
                .takeIf { value -> value.all { it.isDigit() } }
                ?.let(byNumericId::get)
        }

        fun forMangaCategory(category: SourceErrorCategory): PlatformErrorCode? = when (category) {
            SourceErrorCategory.None -> null
            SourceErrorCategory.Timeout -> MangaTimeout
            SourceErrorCategory.RateLimited -> MangaRateLimited
            SourceErrorCategory.Captcha -> MangaCaptcha
            SourceErrorCategory.NotFound -> MangaNotFound
            SourceErrorCategory.Network -> MangaNetwork
            SourceErrorCategory.Auth -> MangaAuth
            SourceErrorCategory.Parser -> MangaParser
            SourceErrorCategory.Unknown -> MangaUnknown
        }

        /** Backfills the stable code for report rows created by app versions predating the code column. */
        fun fromMangaState(code: String?, categoryName: String?): PlatformErrorCode? {
            fromId(code)?.takeIf { it.platform == PlatformErrorPlatform.Manga }?.let { return it }
            val category = categoryName
                ?.let { raw -> runCatching { SourceErrorCategory.valueOf(raw) }.getOrNull() }
                ?: return null
            return forMangaCategory(category)
        }

        fun all(platform: PlatformErrorPlatform? = null): List<PlatformErrorCode> =
            entries.filter { platform == null || it.platform == platform }
    }
}

package com.kitsugi.animelist.ui.screens.profile

/** Error categories suitable for a concise, user-facing account-profile message. */
internal enum class ProfileErrorKind {
    RATE_LIMIT,
    AUTHENTICATION,
    PERMISSION,
    NOT_FOUND,
    TIMEOUT,
    SERVER,
    NETWORK,
    REQUEST_REJECTED,
    UNKNOWN
}

internal data class ProfileErrorSummary(
    val kind: ProfileErrorKind,
    val httpStatusCode: Int? = null
)

private val profileHttpStatusRegex = Regex(
    """(?:\bHTTP(?:/\d(?:\.\d)?)?\s+|\bstatus\b\s*["']?\s*[:=]\s*|:\s*)([1-5]\d{2})\b""",
    RegexOption.IGNORE_CASE
)

/**
 * Turns provider exceptions/JSON payloads into a stable category. The raw text is deliberately
 * not part of the returned model: profile screens must not render server JSON or implementation
 * details as their primary user-facing error.
 */
internal fun classifyProfileError(rawMessage: String?): ProfileErrorSummary {
    val message = rawMessage.orEmpty().trim()
    val normalized = message.lowercase()
    val statusCode = profileHttpStatusRegex.find(message)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    val kind = when {
        statusCode == 429 || normalized.contains("too many requests") ||
            normalized.contains("rate limit") || normalized.contains("rate-limit") ->
            ProfileErrorKind.RATE_LIMIT

        statusCode == 401 || normalized.contains("unauthorized") ||
            normalized.contains("invalid_token") || normalized.contains("token expired") ||
            normalized.contains("session expired") ->
            ProfileErrorKind.AUTHENTICATION

        statusCode == 403 || normalized.contains("forbidden") ||
            normalized.contains("permission denied") || normalized.contains("access denied") ->
            ProfileErrorKind.PERMISSION

        statusCode == 404 || normalized.contains("not found") ||
            normalized.contains("user not found") ->
            ProfileErrorKind.NOT_FOUND

        statusCode == 408 || normalized.contains("timeout") ||
            normalized.contains("timed out") || normalized.contains("sockettimeoutexception") ->
            ProfileErrorKind.TIMEOUT

        (statusCode != null && statusCode in 500..599) || normalized.contains("service unavailable") ||
            normalized.contains("temporarily unavailable") ->
            ProfileErrorKind.SERVER

        statusCode == 400 || statusCode == 422 || normalized.contains("bad request") ||
            normalized.contains("invalid request") ->
            ProfileErrorKind.REQUEST_REJECTED

        listOf(
            "unknownhostexception",
            "unable to resolve host",
            "no address associated with hostname",
            "failed to connect",
            "connection refused",
            "connection reset",
            "network is unreachable",
            "no internet",
            "network error"
        ).any { phrase -> normalized.contains(phrase) } ->
            ProfileErrorKind.NETWORK

        else -> ProfileErrorKind.UNKNOWN
    }

    return ProfileErrorSummary(kind = kind, httpStatusCode = statusCode)
}

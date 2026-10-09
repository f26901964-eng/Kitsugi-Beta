package com.kitsugi.animelist.data.remote

/** Prevent API keys and bearer-like values in query strings from reaching Logcat. */
internal object SensitiveUrlRedactor {
    private val sensitiveQueryParameter = Regex(
        "(?i)([?&](?:api[_-]?key|apikey|key|token|access[_-]?token|client[_-]?secret|authorization)=)[^&#]*"
    )

    fun redact(url: String): String = sensitiveQueryParameter.replace(url) { match ->
        "${match.groupValues[1]}[REDACTED]"
    }
}

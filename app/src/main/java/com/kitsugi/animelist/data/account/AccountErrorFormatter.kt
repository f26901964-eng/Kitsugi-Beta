package com.kitsugi.animelist.data.account

/**
 * Converts account/network exceptions into text that is safe to show in the UI.
 *
 * Ktor can include the complete request URL and headers in a PostgREST exception.
 * That text must never reach the account screen because the Authorization header
 * contains the current Supabase access token.
 */
object AccountErrorFormatter {
    private const val MAX_LENGTH = 400

    private val bearerPattern = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*")
    private val credentialPattern = Regex(
        "(?i)((?:access|refresh|id)[_\\s-]?token|client[_\\s-]?secret|api[_\\s-]?key|password|passwd|oauth[_\\s-]?code)(\\s*[:=]\\s*)(?:Bearer\\s+)?[^\\s,;&]+"
    )
    private val queryCredentialPattern = Regex(
        "(?i)([?&](?:access_token|refresh_token|token|client_secret|api_key|code)=)[^&#\\s]+"
    )
    private val requestDetailsPattern = Regex("(?is)\\s+(?:URL|Headers?)\\s*:.*$")
    private val urlPattern = Regex("(?i)https?://[^\\s\\]}>)]+")
    private val controlCharacterPattern = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val whitespacePattern = Regex("\\s+")

    /**
     * Finds the useful exception message, translates the known backend setup error,
     * and redacts credentials/request details from all other messages.
     */
    fun userMessage(error: Throwable): String {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .toList()
        val raw = messages.firstOrNull().orEmpty()

        if (messages.any { it.contains("permission denied for table user_data", ignoreCase = true) }) {
            return "Kitsugi sunucusunda user_data izinleri eksik. Supabase SQL Editor'da " +
                "20261010_user_data_authenticated_grants.sql migration'ını çalıştırıp tekrar giriş yap."
        }

        val safe = raw
            .replace(requestDetailsPattern, "")
            .replace(bearerPattern) { match -> "${match.groupValues[1]}[REDACTED]" }
            .replace(credentialPattern) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
            }
            .replace(queryCredentialPattern) { match -> "${match.groupValues[1]}[REDACTED]" }
            .replace(urlPattern, "[URL REDACTED]")
            .replace(controlCharacterPattern, "")
            .replace(whitespacePattern, " ")
            .trim()

        return safe.ifBlank { "Bilinmeyen hesap hatası." }.take(MAX_LENGTH)
    }
}

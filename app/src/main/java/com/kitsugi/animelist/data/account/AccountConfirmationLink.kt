package com.kitsugi.animelist.data.account

import java.net.URI
import java.net.URLDecoder

/** Never import bearer tokens from an unsolicited custom-scheme link. Only PKCE is automatic. */
internal object AccountConfirmationLink {
    sealed interface Result {
        data class Code(val value: String) : Result
        data object ManualSignIn : Result
        data object Invalid : Result
        data object NotAccountLink : Result
    }

    fun parse(raw: String): Result {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return Result.Invalid
        if (uri.scheme != "kitsugi" || uri.host != "account-confirm") return Result.NotAccountLink
        if (uri.userInfo != null || uri.port != -1 || uri.path.orEmpty() !in listOf("", "/")) return Result.Invalid
        val query = runCatching {
            uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { parameter ->
                val parts = parameter.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
        }.getOrElse { return Result.Invalid }
        if (query.any { it.first == "error" || it.first == "error_code" } ||
            uri.rawFragment.orEmpty().contains("error=")) return Result.Invalid
        val codes = query.filter { it.first == "code" }
        if (codes.isEmpty()) return Result.ManualSignIn // Old/resend links: confirm email, then sign in normally.
        if (codes.size != 1 || codes.single().second.isBlank() || codes.single().second.length > 4096) return Result.Invalid
        return Result.Code(codes.single().second)
    }
}

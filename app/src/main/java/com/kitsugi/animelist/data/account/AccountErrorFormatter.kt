package com.kitsugi.animelist.data.account

/** Network exceptions can contain complete headers, URLs and JSON credentials. Never show them raw. */
object AccountErrorFormatter {
    private const val MAX_LENGTH = 400
    private val bearerPattern = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*")
    private val credentialPattern = Regex(
        """(?i)(["']?(?:(?:access|refresh|id)[_\s-]?token|client[_\s-]?secret|api[_\s-]?key|password|passwd|oauth[_\s-]?code|authorization)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|(?:Bearer\s+)?[^\s,;&}\]]+)"""
    )
    private val jwtPattern = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")
    private val secretKeyPattern = Regex("\\bsb_secret_[A-Za-z0-9_-]+\\b")
    private val requestDetailsPattern = Regex("(?is)\\s+(?:URL|Headers?)\\s*:.*$")
    private val urlPattern = Regex("(?i)[a-z][a-z0-9+.-]*://[^\\s\\]}>)]+")
    private val controlCharacterPattern = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val whitespacePattern = Regex("\\s+")

    fun userMessage(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val messages = causes.mapNotNull { it.message?.takeIf(String::isNotBlank) }
        val all = messages.joinToString(" ").lowercase(java.util.Locale.ROOT)
        fun containsAny(vararg values: String) = values.any { it in all }

        if (containsAny("permission denied for table user_data")) {
            return "Kitsugi sunucusunda hesap verisi izinleri eksik. Sunucu yöneticisi " +
                "20261010_user_data_authenticated_grants.sql ve 20261021_account_privilege_hardening.sql güncellemelerini kontrol etmeli."
        }
        if (("kitsugi_vault_cas" in all && containsAny("could not find", "does not exist", "schema cache")) || "pgrst202" in all) {
            return "Şifreli kasa için gereken sunucu güncellemesi eksik. Sunucu yöneticisi 20261009_vault_compare_and_swap.sql güncellemesini uygulamalı. Mevcut yedek değiştirilmedi."
        }
        if (containsAny("email_not_confirmed", "email not confirmed")) {
            return "E-posta adresi henüz doğrulanmadı. Gelen kutunu ve spam klasörünü kontrol et veya doğrulama e-postasını yeniden gönder."
        }
        if (containsAny("invalid_credentials", "invalid login credentials")) {
            return "E-posta veya şifre hatalı. Bilgilerini kontrol ederek tekrar dene."
        }
        if (containsAny("over_email_send_rate_limit", "over_request_rate_limit", "email rate limit exceeded", "too many requests", "email rate limit")) {
            return "Çok fazla istek gönderildi. E-posta gönderim sınırına ulaşılmış olabilir; biraz bekleyip tekrar dene."
        }
        if (containsAny("email_address_not_authorized", "email address not authorized", "error sending confirmation email")) {
            return "Doğrulama e-postası gönderilemedi. Sunucunun e-posta gönderici (SMTP) ayarları kontrol edilmeli."
        }
        if (containsAny("otp_expired", "flow_state_expired", "flow_state_not_found", "bad_code_verifier", "code verifier", "code_verifier")) {
            return "Doğrulama bağlantısının süresi dolmuş veya bu cihazda başlatılmamış olabilir. E-postan onaylandıysa normal giriş yap; onaylanmadıysa yeni doğrulama e-postası iste."
        }
        if (containsAny("refresh_token_not_found", "refresh_token_already_used", "jwt expired", "session_not_found")) {
            return "Hesap oturumunun süresi doldu. E-posta ve şifrenle yeniden giriş yap."
        }
        if (causes.any { it is javax.crypto.AEADBadTagException }) {
            return "Şifreli kasa bu şifreyle açılamadı veya yedek doğrulanamadı. Şifre sıfırlandıysa eski kasa için önceki şifre gerekir; mevcut yedek silinmedi."
        }
        if (causes.any { it is java.io.IOException } || containsAny("timeout", "timed out", "unable to resolve host")) {
            return "Sunucuya ulaşılamadı. İnternet bağlantını kontrol edip tekrar dene; mevcut yedek korundu."
        }
        if (causes.any { it is LinkageError }) {
            return "Hesap bileşenleri yüklenemedi. Uygulamayı en güncel sürüme güncelleyip tekrar dene."
        }

        val safe = messages.firstOrNull().orEmpty()
            .replace(requestDetailsPattern, "")
            .replace(credentialPattern) { "${it.groupValues[1]}[REDACTED]" }
            .replace(bearerPattern) { "${it.groupValues[1]}[REDACTED]" }
            .replace(jwtPattern, "[REDACTED]")
            .replace(secretKeyPattern, "[REDACTED]")
            .replace(urlPattern, "[URL REDACTED]")
            .replace(controlCharacterPattern, "")
            .replace(whitespacePattern, " ")
            .trim()
        return safe.ifBlank { "Hesap işlemi tamamlanamadı. Lütfen tekrar dene." }.take(MAX_LENGTH)
    }
}

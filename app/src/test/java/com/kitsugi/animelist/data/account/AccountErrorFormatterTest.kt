package com.kitsugi.animelist.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountErrorFormatterTest {

    @Test
    fun permissionErrorsBecomeAnActionableMessageWithoutRequestDetails() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"
        val error = IllegalStateException(
            "permission denied for table user_data; URL: https://example.supabase.co/rest/v1/user_data " +
                "Headers: Authorization: Bearer $token"
        )

        val message = AccountErrorFormatter.userMessage(error)

        assertTrue(message.contains("20261010_user_data_authenticated_grants.sql"))
        assertFalse(message.contains(token))
        assertFalse(message.contains("Authorization"))
        assertFalse(message.contains("https://"))
    }

    @Test
    fun missingRpcAndAuthErrorsHaveActionableMessages() {
        assertTrue(AccountErrorFormatter.userMessage(IllegalStateException(
            "PGRST202: Could not find public.kitsugi_vault_cas in schema cache"
        )).contains("20261009_vault_compare_and_swap.sql"))
        assertTrue(AccountErrorFormatter.userMessage(IllegalStateException("email_not_confirmed")).contains("doğrulanmadı"))
        assertTrue(AccountErrorFormatter.userMessage(IllegalStateException("Invalid login credentials")).contains("şifre hatalı"))
        assertTrue(AccountErrorFormatter.userMessage(IllegalStateException("over_email_send_rate_limit")).contains("bekleyip"))
    }

    @Test
    fun quotedJsonSecretsAndBareTokensAreRedacted() {
        val error = IllegalStateException(
            """Failure: {"password":"my secret password", "refresh_token":"private-refresh", "apikey":"private-api"} standalone eyJhbGciOiJIUzI1NiJ9.payload.signature sb_secret_private"""
        )
        val message = AccountErrorFormatter.userMessage(error)
        for (secret in listOf("my secret password", "private-refresh", "private-api", "eyJhbGciOiJIUzI1NiJ9.payload.signature", "sb_secret_private")) {
            assertFalse(secret, message.contains(secret))
        }
    }

    @Test
    fun cancellationMessagesAndExceptionsAreNotLoggedWithCallbackUrls() {
        val message = AccountErrorFormatter.userMessage(IllegalStateException("Failed kitsugi://account-confirm?code=private-code"))
        assertFalse(message.contains("private-code"))
        assertFalse(message.contains("kitsugi://"))
        assertTrue(AccountErrorFormatter.userMessage(java.io.IOException("token=private")).contains("İnternet"))
        assertTrue(AccountErrorFormatter.userMessage(NoClassDefFoundError("private")).contains("güncel"))
        assertTrue(AccountErrorFormatter.userMessage(IllegalStateException("x".repeat(500))).length <= 400)
    }

    @Test
    fun genericMessagesRedactBearerTokensAndUrls() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"
        val error = IllegalStateException(
            "request failed URL: https://example.test?token=$token Headers: Authorization: Bearer $token"
        )

        val message = AccountErrorFormatter.userMessage(error)

        assertFalse(message.contains(token))
        assertFalse(message.contains("https://"))
        assertTrue(message.contains("request failed"))
    }
}

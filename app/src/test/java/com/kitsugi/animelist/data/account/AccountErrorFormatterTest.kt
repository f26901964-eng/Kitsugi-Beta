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

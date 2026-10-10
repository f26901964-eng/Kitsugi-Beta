package com.kitsugi.animelist.data.cloudstream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsProtectionClassifierTest {

    @Test
    fun recognizesExplicitCloudflareAndHumanVerificationMarkers() {
        listOf(
            "Cloudflare challenge detected",
            "cf-ray: 123456789",
            "cf_clearance cookie missing",
            "Just a moment...",
            "Turnstile verification required",
            "Please complete the CAPTCHA",
            "Security Verification required",
            "__waf_challenge is pending",
            "Verify you are human",
            "Tarayıcı doğrulama gerekiyor"
        ).forEach { message ->
            assertTrue("Expected challenge marker in: $message", CsProtectionClassifier.isWafChallenge(message))
        }
    }

    @Test
    fun recognizesDdosGuardSeparately() {
        assertTrue(CsProtectionClassifier.isDdosGuard("DDoS-Guard verification"))
        assertFalse(CsProtectionClassifier.isWafChallenge("DDoS-Guard verification"))
    }

    @Test
    fun genericHttpAndNetworkFailuresAreNotProtectionEvidence() {
        listOf(
            "HTTP 403 Forbidden",
            "HTTP 503 Service Unavailable",
            "Connection refused",
            "Socket timeout",
            "Unable to resolve host",
            "SSL handshake failed",
            "Access denied"
        ).forEach { message ->
            assertFalse("Unexpected protection classification for: $message", CsProtectionClassifier.isWafChallenge(message))
            assertFalse("Unexpected DDoS classification for: $message", CsProtectionClassifier.isDdosGuard(message))
        }
    }
}

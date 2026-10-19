package com.kitsugi.animelist.core.player.engine

import org.junit.Assert.*
import org.junit.Test

/** No CA-path shortcut is allowed to turn certificate validation off. */
class MpvTlsPolicyTest {
    @Test fun verificationRemainsEnabledWithoutExplicitPath() {
        for (path in listOf(null, "", " ", "\t")) {
            val options = mpvTlsOptions(path).toMap()
            assertEquals("yes", options["tls-verify"])
            assertFalse(options.containsKey("tls-ca-file"))
        }
    }

    @Test fun explicitTrustStoreIsUsed() {
        val path = "/data/user/0/com.kitsugi.animelist/files/mpv/android-ca.pem"
        assertEquals(mapOf("tls-verify" to "yes", "tls-ca-file" to path), mpvTlsOptions(path).toMap())
    }

    @Test fun writesOnlyTlsOptions() {
        val written = linkedMapOf<String, String>()
        applyMpvTlsOptions { name, value -> written[name] = value }
        assertEquals(mapOf("tls-verify" to "yes"), written)
    }

    @Test fun writesVerificationBeforeCaPath() {
        val writes = mutableListOf<Pair<String, String>>()
        applyMpvTlsOptions("/private/ca.pem") { name, value -> writes += name to value }
        assertEquals(listOf("tls-verify" to "yes", "tls-ca-file" to "/private/ca.pem"), writes)
    }
}

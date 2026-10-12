package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertEquals
import org.junit.Test

class MalAccountMatchTest {

    @Test
    fun matchesWhenBothIdentitiesNameTheSameAccount() {
        assertEquals(
            MalAccountMatch.Status.MATCHED,
            MalAccountMatch.status("KitsugiFan", "KitsugiFan", oauthAvailable = true)
        )
    }

    @Test
    fun matchesIgnoringCaseAndSurroundingWhitespace() {
        assertEquals(
            MalAccountMatch.Status.MATCHED,
            MalAccountMatch.status("  KitsugiFan ", "kitsugifan", oauthAvailable = true)
        )
    }

    @Test
    fun mismatchesWhenTheWebSessionBelongsToAnotherAccount() {
        assertEquals(
            MalAccountMatch.Status.MISMATCHED,
            MalAccountMatch.status("KitsugiFan", "SomeoneElse", oauthAvailable = true)
        )
    }

    @Test
    fun isUnverifiedWhenTheSessionOwnerCannotBeRead() {
        assertEquals(
            MalAccountMatch.Status.UNVERIFIED,
            MalAccountMatch.status("KitsugiFan", null, oauthAvailable = true)
        )
        assertEquals(
            MalAccountMatch.Status.UNVERIFIED,
            MalAccountMatch.status("KitsugiFan", "   ", oauthAvailable = true)
        )
    }

    @Test
    fun isUnverifiedWhenOauthNameIsMissingButATokenExists() {
        assertEquals(
            MalAccountMatch.Status.UNVERIFIED,
            MalAccountMatch.status(null, "KitsugiFan", oauthAvailable = true)
        )
    }

    @Test
    fun hasNoOauthAccountWhenNoTokenIsLinked() {
        assertEquals(
            MalAccountMatch.Status.NO_OAUTH_ACCOUNT,
            MalAccountMatch.status(null, "KitsugiFan", oauthAvailable = false)
        )
    }
}

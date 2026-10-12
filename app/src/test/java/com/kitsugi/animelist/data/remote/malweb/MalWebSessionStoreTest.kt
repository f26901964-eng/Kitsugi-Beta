package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalWebSessionStoreTest {
    @Test
    fun onlyAcceptsTheExactSignedInCookie() {
        assertTrue(MalWebSessionStore.isAuthenticatedCookie("other=value; is_signed_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("not_is_signed_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("is_signed_in=0"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie(null))
    }
}

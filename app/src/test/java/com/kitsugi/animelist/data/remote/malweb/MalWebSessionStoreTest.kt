package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalWebSessionStoreTest {
    @Test
    fun acceptsMALsRealSignedInSessionCookies() {
        // MAL giriş yapılmış oturumu MALHLOGSESSID ile taşır; is_signed_in diye bir çerezi yoktur.
        assertTrue(
            MalWebSessionStore.isAuthenticatedCookie(
                "MALSESSIONID=; MALHLOGSESSID=abc123def; m_gdpr_mdl_2=1; is_logged_in=1"
            )
        )
        assertTrue(MalWebSessionStore.isAuthenticatedCookie("MALSESSIONID=session-value"))
    }

    @Test
    fun acceptsLegacySignedInFlagOnlyNextToASessionCookie() {
        // Eski Kitsugi sürümünün kabul ettiği bayrak, oturum çerezi yanındayken geçerli sayılır.
        assertTrue(MalWebSessionStore.isAuthenticatedCookie("is_signed_in=1; MALHLOGSESSID=abc"))
        assertTrue(MalWebSessionStore.isAuthenticatedCookie("other=value; is_logged_in=1; MALSESSIONID=x"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("not_is_signed_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("is_signed_in=0; MALHLOGSESSID=abc"))
    }

    @Test
    fun rejectsFlagOnlyOrEmptySessionCookies() {
        // Çıkış sonrası kalıntı: bayrak durur ama oturum çerezlerinin değeri boştur.
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("is_logged_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("MALSESSIONID=; MALHLOGSESSID=; is_logged_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("cf_clearance=abc; m_gdpr_mdl_2=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie(""))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie(null))
    }

    @Test
    fun reportsOnlyCookieNamesForDiagnostics() {
        val names = MalWebSessionStore.presentCookieNames(
            "MALHLOGSESSID=secret-value; is_logged_in=1; anilist_token=leak"
        )
        assertTrue(names.contains("malhlogsessid"))
        assertTrue(names.contains("is_logged_in=1"))
        // Değerler asla dışarı verilmez.
        assertFalse(names.any { it.contains("secret-value") })
        assertFalse(names.any { it.contains("leak") })
    }
}

package com.kitsugi.animelist.data.remote.malweb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalWebSessionStoreTest {
    @Test
    fun acceptsMALsRealSignedInSessionCookies() {
        // MAL giriş yapılmış oturumu MALHLOGSESSID (ve MALSESSIONID) ile taşır.
        assertTrue(
            MalWebSessionStore.isAuthenticatedCookie(
                "MALSESSIONID=; MALHLOGSESSID=abc123def; m_gdpr_mdl_2=1; is_logged_in=1"
            )
        )
        assertTrue(MalWebSessionStore.isAuthenticatedCookie("MALSESSIONID=session-value"))
    }

    @Test
    fun rejectsFlagOnlyCookieHeaders() {
        // Eski sürümün hatası: MALHLOGSESSID yokken oturum "bağlı" sayılıyordu.
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("is_signed_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("is_logged_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("is_logged_in=1; is_signed_in=1"))
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("not_is_signed_in=1"))
    }

    @Test
    fun rejectsEmptySessionCookieLeftovers() {
        // Çıkış sonrası kalıntı: bayrak durur, oturum çerezlerinin değeri boştur.
        assertFalse(
            MalWebSessionStore.isAuthenticatedCookie("MALSESSIONID=; MALHLOGSESSID=; is_logged_in=1")
        )
        assertFalse(MalWebSessionStore.isAuthenticatedCookie("MALHLOGSESSID=   "))
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
        // Çerez değerleri asla dışarı verilmez.
        assertFalse(names.any { it.contains("secret-value") })
        assertFalse(names.any { it.contains("leak") })
    }
}

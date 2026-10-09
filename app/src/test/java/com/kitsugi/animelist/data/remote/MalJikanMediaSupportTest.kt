package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MalJikanMediaSupportTest {

    @Test
    fun `non MAL provider ID is not assumed to be a MAL ID`() {
        assertNull(MalJikanMediaSupport.resolveMalId("simkl", externalId = 12345, realMalId = null))
        assertNull(MalJikanMediaSupport.resolveMalId("tmdb", externalId = 12345, realMalId = null))
    }

    @Test
    fun `verified MAL mapping is accepted for another provider`() {
        assertEquals(5114, MalJikanMediaSupport.resolveMalId("simkl", externalId = 12345, realMalId = 5114))
    }

    @Test
    fun `MAL source ID is used directly`() {
        assertEquals(5114, MalJikanMediaSupport.resolveMalId("jikan", externalId = 5114, realMalId = null))
    }
}

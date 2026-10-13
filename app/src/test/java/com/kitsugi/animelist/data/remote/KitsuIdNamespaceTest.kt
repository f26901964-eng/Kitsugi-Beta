package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KitsuIdNamespaceTest {
    @Test
    fun `Kitsu stable ID conversion is bounded and cannot overflow`() {
        assertEquals(300_000_042, KitsuIdNamespace.stableIdFromRaw(42))
        assertEquals(42, KitsuIdNamespace.rawIdFromStable(300_000_042))
        assertNull(KitsuIdNamespace.stableIdFromRaw(0))
        assertNull(KitsuIdNamespace.stableIdFromRaw(Int.MAX_VALUE))
        assertNull(KitsuIdNamespace.stableIdFromRaw(100_000_000))
    }
}

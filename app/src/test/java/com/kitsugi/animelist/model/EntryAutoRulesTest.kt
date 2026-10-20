package com.kitsugi.animelist.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryAutoRulesTest {

    private val fixedToday = "2026-10-20"

    @Test
    fun testIsBlankDate() {
        assertTrue(EntryAutoRules.isBlankDate(null))
        assertTrue(EntryAutoRules.isBlankDate(""))
        assertTrue(EntryAutoRules.isBlankDate("   "))
        assertTrue(EntryAutoRules.isBlankDate("0000-00-00"))
        assertTrue(EntryAutoRules.isBlankDate("----"))
        assertFalse(EntryAutoRules.isBlankDate("2026-10-20"))
    }

    @Test
    fun testWatchingFillsStartDateWhenEmpty() {
        val resolved = EntryAutoRules.resolveOnStatusChange(
            status = WatchStatus.Watching,
            startDate = null,
            endDate = null,
            progress = 3,
            total = 12,
            statusChanged = true,
            today = fixedToday
        )
        assertEquals(fixedToday, resolved.startDate)
        assertNull(resolved.endDate)
        assertEquals(3, resolved.progress)
    }

    @Test
    fun testWatchingPreservesExistingStartDate() {
        val resolved = EntryAutoRules.resolveOnStatusChange(
            status = WatchStatus.Watching,
            startDate = "2025-01-01",
            endDate = null,
            progress = 5,
            total = 12,
            statusChanged = true,
            today = fixedToday
        )
        assertEquals("2025-01-01", resolved.startDate)
        assertNull(resolved.endDate)
    }

    @Test
    fun testCompletedFillsBothDatesAndCompletesProgressWhenStatusChanged() {
        val resolved = EntryAutoRules.resolveOnStatusChange(
            status = WatchStatus.Completed,
            startDate = null,
            endDate = null,
            progress = 4,
            total = 12,
            statusChanged = true,
            today = fixedToday
        )
        assertEquals(fixedToday, resolved.startDate)
        assertEquals(fixedToday, resolved.endDate)
        assertEquals(12, resolved.progress)
    }

    @Test
    fun testCompletedDoesNotTouchProgressWhenStatusAlreadyCompleted() {
        val resolved = EntryAutoRules.resolveOnStatusChange(
            status = WatchStatus.Completed,
            startDate = "2026-01-01",
            endDate = "2026-02-01",
            progress = 4,
            total = 12,
            statusChanged = false,
            today = fixedToday
        )
        // Progress should stay 4 because status was already completed (e.g. only score edited)
        assertEquals(4, resolved.progress)
        assertEquals("2026-01-01", resolved.startDate)
        assertEquals("2026-02-01", resolved.endDate)
    }

    @Test
    fun testPlannedDoesNotTouchDatesOrProgress() {
        val resolved = EntryAutoRules.resolveOnStatusChange(
            status = WatchStatus.Planned,
            startDate = null,
            endDate = null,
            progress = 0,
            total = 24,
            statusChanged = true,
            today = fixedToday
        )
        assertNull(resolved.startDate)
        assertNull(resolved.endDate)
        assertEquals(0, resolved.progress)
    }
}

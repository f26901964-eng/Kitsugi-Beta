package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomReaderBrowserTest {
    @Test
    fun backAndForwardFollowBrowserHistoryAndNewNavigationClearsForward() {
        val browser = FandomReaderBrowserController()
        val home = FandomReaderLocation("example", "Home")
        val pageA = FandomReaderLocation("example", "Page A")
        val pageB = FandomReaderLocation("example", "Page B")
        val pageC = FandomReaderLocation("example", "Page C")

        browser.navigate(home, pushHistory = false)
        browser.navigate(pageA, pushHistory = true)
        browser.navigate(pageB, pushHistory = true)
        assertTrue(browser.snapshot.canGoBack)
        assertFalse(browser.snapshot.canGoForward)

        assertEquals(pageA, browser.back())
        assertEquals(home, browser.back())
        assertFalse(browser.snapshot.canGoBack)
        assertTrue(browser.snapshot.canGoForward)
        assertEquals(pageA, browser.forward())

        browser.navigate(pageC, pushHistory = true)
        assertEquals(pageC, browser.activeLocation)
        assertFalse(browser.snapshot.canGoForward)
        assertEquals(pageA, browser.back())
    }

    @Test
    fun tabsKeepIndependentHistoryAndClosingActiveTabSelectsAnother() {
        val browser = FandomReaderBrowserController()
        val firstPage = FandomReaderLocation("example", "First")
        val secondPage = FandomReaderLocation("example", "Second")
        val thirdPage = FandomReaderLocation("example", "Third")

        browser.navigate(firstPage, pushHistory = false)
        val firstTabId = browser.snapshot.activeTabId!!
        assertEquals(firstPage, browser.openNewTab())
        val secondTabId = browser.snapshot.activeTabId!!
        browser.navigate(secondPage, pushHistory = true)

        assertEquals(firstPage, browser.switchTab(firstTabId))
        browser.navigate(thirdPage, pushHistory = true)
        assertEquals(secondPage, browser.switchTab(secondTabId))
        assertEquals(thirdPage, browser.closeTab(secondTabId))

        assertEquals(firstTabId, browser.snapshot.activeTabId)
        assertEquals(1, browser.snapshot.tabs.size)
        assertEquals(thirdPage, browser.activeLocation)
        assertNull(browser.closeTab(firstTabId))
        assertTrue(browser.snapshot.tabs.isEmpty())
    }

    @Test
    fun newTabsStopAtThePracticalLimit() {
        val browser = FandomReaderBrowserController()
        browser.navigate(FandomReaderLocation("example", "Home"), pushHistory = false)

        repeat(FandomReaderBrowserController.MAX_TABS - 1) {
            assertNotNull(browser.openNewTab())
        }

        assertEquals(FandomReaderBrowserController.MAX_TABS, browser.snapshot.tabs.size)
        assertFalse(browser.snapshot.canOpenNewTab)
        assertNull(browser.openNewTab())
    }
}

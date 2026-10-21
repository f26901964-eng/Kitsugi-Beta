package com.kitsugi.animelist.ui.screens.detail.wiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FandomReaderBrowserTest {
    @Test
    fun webNavigationSynchronizesDuplicateTargetWithoutDuplicatingNativeHistory() {
        val browser = FandomReaderBrowserController()
        browser.navigate(FandomReaderLocation("you-zitsu", "Home"), false)
        val target = FandomReaderLocation("tr.onepiece", "Ana Sayfa")
        assertTrue(browser.updateWebLocation(browser.snapshot.activeTabId!!, target))
        assertEquals(target, browser.activeLocation)
        assertFalse(browser.snapshot.canGoBack)
        assertFalse(browser.updateWebLocation(browser.snapshot.activeTabId!!, target))
        assertEquals(target, browser.openNewTab())
    }

    @Test
    fun disposedWebViewCannotRenameAnotherTab() {
        val browser = FandomReaderBrowserController()
        val home = FandomReaderLocation("you-zitsu", "Home")
        browser.navigate(home, false)
        val firstId = browser.snapshot.activeTabId!!
        browser.openNewTab()
        val target = FandomReaderLocation("you-zitsu", "Character")
        assertFalse(browser.updateWebLocation(firstId, target))
        browser.closeTab(firstId)
        assertFalse(browser.updateWebLocation(firstId, target))
        assertEquals(home, browser.activeLocation)
        browser.reset()
        assertFalse(browser.updateWebLocation(firstId, target))
    }

    @Test
    fun webAddressSyncPreservesExistingNativeBackStack() {
        val browser = FandomReaderBrowserController()
        val home = FandomReaderLocation("you-zitsu", "Home")
        val target = FandomReaderLocation("you-zitsu", "Character")
        browser.navigate(home, false)
        browser.navigate(FandomReaderLocation("you-zitsu", "Synopsis"), true)
        browser.updateWebLocation(browser.snapshot.activeTabId!!, target)
        assertEquals(home, browser.back())
        assertEquals(target, browser.forward())
    }

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

    // ── Chrome/Brave sekme düzeni ────────────────────────────────────────────

    private fun loc(title: String, wikiId: String = "hunter-x-hunter") =
        FandomReaderLocation(wikiId, title)

    private fun controllerWith(vararg titles: String): FandomReaderBrowserController {
        val controller = FandomReaderBrowserController()
        controller.navigate(loc(titles.first()), pushHistory = false)
        titles.drop(1).forEach { controller.openInNewTab(loc(it)) }
        return controller
    }

    private fun titles(controller: FandomReaderBrowserController): List<String> =
        controller.snapshot.tabs.map { it.location.title }

    @Test
    fun `link opens in new tab immediately to the right of the active tab`() {
        val controller = controllerWith("A", "B", "C")
        // Ortadaki sekmeye dönüp bağlantıya tıklamak sona değil sağa eklemeli (Chrome).
        val bId = controller.snapshot.tabs[1].id
        controller.switchTab(bId)

        val opened = controller.openInNewTab(loc("Gon Freecss"))

        assertEquals(loc("Gon Freecss"), opened)
        assertEquals(listOf("A", "B", "Gon Freecss", "C"), titles(controller))
        assertEquals("Gon Freecss", controller.snapshot.activeTab?.location?.title)
    }

    @Test
    fun `first link creates the first tab and source page survives`() {
        val controller = FandomReaderBrowserController()
        controller.openInNewTab(loc("Yoshihiro Togashi"))
        controller.openInNewTab(loc("Gon Freecss"))

        assertEquals(listOf("Yoshihiro Togashi", "Gon Freecss"), titles(controller))
        // Kaynak sekme yerinde duruyor; tek dokunuşla geri dönülebilir.
        val sourceId = controller.snapshot.tabs[0].id
        assertEquals(loc("Yoshihiro Togashi"), controller.switchTab(sourceId))
    }

    @Test
    fun `opening an already open page switches to its tab instead of duplicating`() {
        val controller = controllerWith("A", "B")
        val aId = controller.snapshot.tabs[0].id

        val opened = controller.openInNewTab(loc("a")) // büyük/küçük harf duyarsız

        assertEquals(loc("A"), opened)
        assertEquals(listOf("A", "B"), titles(controller))
        assertEquals(aId, controller.snapshot.activeTabId)
    }

    @Test
    fun `link to the page already being read is a no-op`() {
        val controller = controllerWith("A")
        val activeBefore = controller.snapshot.activeTabId

        val opened = controller.openInNewTab(loc("A"))

        assertEquals(loc("A"), opened)
        assertEquals(1, controller.snapshot.tabs.size)
        assertEquals(activeBefore, controller.snapshot.activeTabId)
    }

    @Test
    fun `tab limit makes openInNewTab return null so caller can fall back to same tab`() {
        val controller = controllerWith("A")
        repeat(FandomReaderBrowserController.MAX_TABS - 1) {
            controller.openInNewTab(loc("Page $it"))
        }
        assertEquals(FandomReaderBrowserController.MAX_TABS, controller.snapshot.tabs.size)
        assertNull(controller.openInNewTab(loc("Overflow")))
        // Zaten açık sayfa sınır doluyken bile sekme değiştirerek açılabilir.
        assertEquals(loc("A"), controller.openInNewTab(loc("A")))
    }

    @Test
    fun `duplicate tab button also inserts to the right of the active tab`() {
        val controller = controllerWith("A", "B")
        controller.switchTab(controller.snapshot.tabs[0].id)

        controller.openNewTab()

        assertEquals(listOf("A", "A", "B"), titles(controller))
        assertEquals(1, controller.snapshot.tabs.indexOfFirst { it.id == controller.snapshot.activeTabId })
    }

    @Test
    fun `closing the active tab activates the right neighbour then the left one`() {
        val controller = controllerWith("A", "B", "C")
        controller.switchTab(controller.snapshot.tabs[1].id) // B aktif

        assertEquals(loc("C"), controller.closeTab(controller.snapshot.activeTabId!!))
        assertEquals(listOf("A", "C"), titles(controller))

        // Son sekmeyi (C) kapatmak solundaki A'yı etkinleştirir.
        assertEquals(loc("A"), controller.closeTab(controller.snapshot.activeTabId!!))
        assertTrue(controller.snapshot.tabs.single().location.title == "A")
    }

    @Test
    fun `closing a background tab keeps the active page`() {
        val controller = controllerWith("A", "B")
        val aId = controller.snapshot.tabs[0].id

        assertNull(controller.closeTab(aId))
        assertEquals("B", controller.snapshot.activeTab?.location?.title)
    }

    @Test
    fun `closing other tabs keeps only the long pressed tab and activates it`() {
        val controller = controllerWith("A", "B", "C")
        val aId = controller.snapshot.tabs[0].id

        assertEquals(loc("A"), controller.closeOtherTabs(aId))
        assertEquals(listOf("A"), titles(controller))
        assertEquals(aId, controller.snapshot.activeTabId)
    }

    @Test
    fun `history stays per tab when new tabs open`() {
        val controller = controllerWith("A")
        controller.navigate(loc("A2"), pushHistory = true) // aynı sekmede geçmişli gezinme
        controller.openInNewTab(loc("B"))
        // Yeni sekmenin kendi geçmişi boş; ilk sekmenin geçmişi ise olduğu gibi durur.
        assertNull(controller.back())
        controller.switchTab(controller.snapshot.tabs[0].id)
        assertEquals(loc("A"), controller.back())
        assertEquals(loc("A2"), controller.forward())
    }
}

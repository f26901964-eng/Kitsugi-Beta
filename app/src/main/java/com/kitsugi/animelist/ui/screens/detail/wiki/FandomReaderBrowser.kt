package com.kitsugi.animelist.ui.screens.detail.wiki

/** A page address inside one Fandom wiki. */
data class FandomReaderLocation(
    val wikiId: String,
    val title: String
)

/** Browser history for one reader tab. */
data class FandomReaderTab(
    val id: Long,
    val location: FandomReaderLocation,
    val backStack: List<FandomReaderLocation> = emptyList(),
    val forwardStack: List<FandomReaderLocation> = emptyList()
) {
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()
}

/** Immutable snapshot consumed by the reader toolbar and tab strip. */
data class FandomReaderBrowserState(
    val tabs: List<FandomReaderTab> = emptyList(),
    val activeTabId: Long? = null
) {
    val activeTab: FandomReaderTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    val canGoBack: Boolean get() = activeTab?.canGoBack == true
    val canGoForward: Boolean get() = activeTab?.canGoForward == true
    val canOpenNewTab: Boolean get() = activeTab != null && tabs.size < FandomReaderBrowserController.MAX_TABS

    companion object {
        val Empty = FandomReaderBrowserState()
    }
}

/**
 * Small, UI-independent browser history used by both media and character wiki readers.
 * Navigation is deliberately title-based: Fandom's repository cache resolves pages and
 * redirects, while this controller only owns tab/history semantics.
 */
internal class FandomReaderBrowserController {
    companion object {
        const val MAX_TABS = 12
    }

    private var nextTabId = 1L
    private var tabs = emptyList<FandomReaderTab>()
    private var activeTabId: Long? = null

    val snapshot: FandomReaderBrowserState
        get() = FandomReaderBrowserState(tabs = tabs, activeTabId = activeTabId)

    val activeLocation: FandomReaderLocation?
        get() = snapshot.activeTab?.location

    fun reset() {
        tabs = emptyList()
        activeTabId = null
        nextTabId = 1L
    }

    /** Navigate in the active tab; a normal link adds history and clears forward history. */
    fun navigate(location: FandomReaderLocation, pushHistory: Boolean) {
        val current = snapshot.activeTab
        if (current == null) {
            val tab = FandomReaderTab(id = nextTabId++, location = location)
            tabs = listOf(tab)
            activeTabId = tab.id
            return
        }

        val updated = if (pushHistory && current.location != location) {
            current.copy(
                location = location,
                backStack = current.backStack + current.location,
                forwardStack = emptyList()
            )
        } else {
            current.copy(location = location)
        }
        replaceTab(updated)
    }

    /** Replace a redirect/normalized title without creating another history entry. */
    fun normalizeCurrentLocation(location: FandomReaderLocation) {
        val current = snapshot.activeTab ?: return
        replaceTab(current.copy(location = location))
    }

    fun back(): FandomReaderLocation? {
        val current = snapshot.activeTab ?: return null
        val previous = current.backStack.lastOrNull() ?: return null
        replaceTab(
            current.copy(
                location = previous,
                backStack = current.backStack.dropLast(1),
                forwardStack = listOf(current.location) + current.forwardStack
            )
        )
        return previous
    }

    fun forward(): FandomReaderLocation? {
        val current = snapshot.activeTab ?: return null
        val next = current.forwardStack.firstOrNull() ?: return null
        replaceTab(
            current.copy(
                location = next,
                backStack = current.backStack + current.location,
                forwardStack = current.forwardStack.drop(1)
            )
        )
        return next
    }

    /** Open the current location in a fresh tab (the useful browser-style duplicate-tab action). */
    fun openNewTab(): FandomReaderLocation? {
        if (tabs.size >= MAX_TABS) return null
        val location = activeLocation ?: return null
        val tab = FandomReaderTab(id = nextTabId++, location = location)
        tabs = tabs + tab
        activeTabId = tab.id
        return location
    }

    fun switchTab(tabId: Long): FandomReaderLocation? {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        activeTabId = tab.id
        return tab.location
    }

    /**
     * Close a tab. Returns the new active location when the active tab changed, null when
     * a non-active tab was closed or the final tab was closed.
     */
    fun closeTab(tabId: Long): FandomReaderLocation? {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return null
        if (tabs.size == 1) {
            reset()
            return null
        }

        val wasActive = tabId == activeTabId
        tabs = tabs.filterNot { it.id == tabId }
        if (!wasActive) return null

        val newIndex = index.coerceAtMost(tabs.lastIndex)
        val next = tabs[newIndex]
        activeTabId = next.id
        return next.location
    }

    private fun replaceTab(tab: FandomReaderTab) {
        tabs = tabs.map { if (it.id == tab.id) tab else it }
    }
}

/** Stable translation keys keep each tab/page's translated and original text independent. */
internal object FandomReaderTranslationKeys {
    fun description(location: FandomReaderLocation): String =
        "reader-description:${location.wikiId}:${location.title}"

    fun section(location: FandomReaderLocation, index: Int): String =
        "reader-section:${location.wikiId}:$index:${location.title}"

    fun isReaderKey(key: String): Boolean =
        key.startsWith("reader-description:") || key.startsWith("reader-section:")
}

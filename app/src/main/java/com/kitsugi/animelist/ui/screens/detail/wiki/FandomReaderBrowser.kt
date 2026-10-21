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

    /** WebView owns document/fragment history; only synchronize the tab address here.
     * A finishing request from a disposed/background tab must never rename the active tab.
     */
    fun updateWebLocation(tabId: Long, location: FandomReaderLocation): Boolean {
        val current = snapshot.activeTab ?: return false
        if (current.id != tabId || current.location == location) return false
        replaceTab(current.copy(location = location))
        return true
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
        return insertTabAfterActive(location)
    }

    /**
     * Chrome/Brave-style "open link in new tab": the page opens in a new tab inserted
     * immediately to the RIGHT of the active tab (not appended at the end of the strip)
     * and becomes active, so the source page stays untouched one tap away.
     *
     * If the same page is already open this switches to that tab instead of stacking
     * duplicates; a link to the very page being read is a no-op that returns the active
     * location (with no tab yet, that first tab is simply created). Returns null only
     * when [MAX_TABS] is reached — callers then fall back to navigating the current
     * tab (with history) so a tapped link never dies silently.
     */
    fun openInNewTab(location: FandomReaderLocation): FandomReaderLocation? {
        val current = snapshot.activeTab
        if (current == null) {
            val tab = FandomReaderTab(id = nextTabId++, location = location)
            tabs = listOf(tab)
            activeTabId = tab.id
            return location
        }
        if (current.location.isSamePageAs(location)) return current.location
        val existing = tabs.firstOrNull { it.location.isSamePageAs(location) }
        if (existing != null) {
            activeTabId = existing.id
            return existing.location
        }
        if (tabs.size >= MAX_TABS) return null
        return insertTabAfterActive(location)
    }

    /** Inserts a tab directly after the active one and activates it (Chrome tab order). */
    private fun insertTabAfterActive(location: FandomReaderLocation): FandomReaderLocation {
        val tab = FandomReaderTab(id = nextTabId++, location = location)
        val activeIndex = tabs.indexOfFirst { it.id == activeTabId }
        tabs = if (activeIndex >= 0) {
            tabs.toMutableList().apply { add(activeIndex + 1, tab) }
        } else {
            tabs + tab
        }
        activeTabId = tab.id
        return location
    }

    private fun FandomReaderLocation.isSamePageAs(other: FandomReaderLocation): Boolean =
        wikiId.equals(other.wikiId, ignoreCase = true) &&
            title.replace('_', ' ').trim().equals(other.title.replace('_', ' ').trim(), ignoreCase = true)

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

    /** Long-press on a tab: close every other tab and keep (and activate) this one. */
    fun closeOtherTabs(tabId: Long): FandomReaderLocation? {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return null
        tabs = listOf(tab)
        activeTabId = tab.id
        return tab.location
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

    /**
     * İçindekiler satırları (bölüm başlıkları) için anahtar. Okuyucu sayfası açıldığında
     * otomatik çeviri açıksa başlıklar TEK istekte çevrilir; satır sayısı jetonla
     * kanıtlandığı için hiçbir satır kaymaz. Satır çevirileri bu anahtarın çocuklarıdır
     * ([WikiStructuredTranslationKeys.block]).
     */
    fun contents(location: FandomReaderLocation): String =
        "reader-toc:${location.wikiId}:${location.title}"

    fun isReaderKey(key: String): Boolean =
        key.startsWith("reader-description:") || key.startsWith("reader-section:") ||
            key.startsWith("reader-toc:")
}

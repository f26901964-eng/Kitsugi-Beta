package com.kitsugi.animelist.ui.screens.detail.wiki

/** Offline executable checks against production Kotlin, without Android or test doubles.
 * Compile with WikiOriginalPagePolicy.kt and FandomReaderBrowser.kt; run this main.
 */
fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        body()
        passed++
        println("PASS $name")
    }
    fun destination(url: String) = WikiOriginalPagePolicy.destination(url)
    val wiki = WikiOriginalPagePolicy.Destination.WIKI
    val external = WikiOriginalPagePolicy.Destination.EXTERNAL
    val blocked = WikiOriginalPagePolicy.Destination.BLOCKED

    test("real community, language, legacy and Wikipedia destinations") {
        listOf(
            "https://you-zitsu.fandom.com/wiki/You-Zitsu_Wiki",
            "https://onepiece.fandom.com/tr/wiki/Ana_Sayfa#Karakterler",
            "https://naruto.wikia.com/wiki/Naruto",
            "https://en.wikipedia.org/wiki/Classroom_of_the_Elite",
            "https://tr.m.wikipedia.org/wiki/Anime",
            "https://auth.fandom.com/", "https://fandom.com/",
            "https://YOU-ZITSU.FANDOM.COM:443/wiki/Home"
        ).forEach { check(destination(it) == wiki) { it } }
    }
    test("lookalike domains require explicit external-link confirmation") {
        listOf("https://fandom.com.evil.test/", "https://evilfandom.com/",
            "https://en.wikipedia.org.evil.test/", "https://fandom-com.test/",
            "https://example.com/news").forEach { check(destination(it) == external) { it } }
    }
    test("script, intent, file, content, data, cleartext and malformed URLs blocked") {
        listOf("javascript:alert(1)", "intent://wiki/#Intent;end", "file:///sdcard/test",
            "content://contacts/", "data:text/html,hello", "http://you-zitsu.fandom.com/",
            "https://user:password@you-zitsu.fandom.com/", "https://evil.test@fandom.com/",
            "https://fandom.com:8080/", "//fandom.com/wiki/Home", "https:///wiki/Home",
            "https://fandom.com\\@evil.test/", "", "not a URL")
            .forEach { check(destination(it) == blocked) { it } }
    }
    val ua = "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/TEST; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.123 Mobile Safari/537.36"
    test("desktop UA keeps installed Chromium engine version") {
        val desktop = WikiOriginalPagePolicy.desktopUserAgent(ua)
        check(desktop.contains("Chrome/140.0.7339.123"))
        check(desktop.contains("AppleWebKit/537.36"))
        check(desktop.contains("(X11; Linux x86_64)"))
        check(!desktop.contains("Android"))
        check(!desktop.contains("Mobile"))
        check(!desktop.contains("Version/4.0"))
        check(!desktop.contains("; wv"))
    }
    test("desktop UA transformation idempotent") {
        val desktop = WikiOriginalPagePolicy.desktopUserAgent(ua)
        check(WikiOriginalPagePolicy.desktopUserAgent(desktop) == desktop)
    }
    test("desktop viewport carries the fitted scale and keeps user zoom") {
        val script = WikiOriginalPagePolicy.desktopViewportScript(1200, 30)
        check(script.contains("width=1200, initial-scale=0.3"))
        check(script.contains("minimum-scale=0.1"))
        check(script.contains("maximum-scale=5"))
        check(script.contains("user-scalable=yes"))
        check(!script.contains("user-scalable=no"))
        check(!script.contains("__WIDTH__") && !script.contains("__SCALE__"))
        check(WikiOriginalPagePolicy.desktopViewportScript(1200, 300).contains("initial-scale=1.0"))
        check(WikiOriginalPagePolicy.desktopViewportScript(0, 0).contains("width=320"))
        check(WikiOriginalPagePolicy.desktopViewportScript(99999, 50).contains("width=2400"))
    }
    val home = FandomReaderLocation("you-zitsu", "You-Zitsu Wiki")
    val character = FandomReaderLocation("you-zitsu", "Kiyotaka Ayanokōji")
    val otherWiki = FandomReaderLocation("tr.onepiece", "Ana Sayfa")

    test("web navigation updates address without duplicating WebView history") {
        val browser = FandomReaderBrowserController()
        browser.navigate(home, false)
        val id = browser.snapshot.activeTabId!!
        check(browser.updateWebLocation(id, character))
        check(browser.activeLocation == character)
        check(browser.snapshot.activeTab!!.backStack.isEmpty())
        check(browser.snapshot.activeTab!!.forwardStack.isEmpty())
        check(!browser.updateWebLocation(id, character))
    }
    test("redirect and cross-wiki address become duplicate-tab target") {
        val browser = FandomReaderBrowserController()
        browser.navigate(home, false)
        check(browser.updateWebLocation(browser.snapshot.activeTabId!!, otherWiki))
        check(browser.openNewTab() == otherWiki)
        check(browser.snapshot.tabs.map { it.location } == listOf(otherWiki, otherWiki))
    }
    test("late callback from inactive tab cannot overwrite current tab") {
        val browser = FandomReaderBrowserController()
        browser.navigate(home, false)
        val oldId = browser.snapshot.activeTabId!!
        browser.openNewTab()
        val currentId = browser.snapshot.activeTabId!!
        check(!browser.updateWebLocation(oldId, character))
        check(browser.activeLocation == home)
        check(browser.snapshot.activeTabId == currentId)
        check(browser.snapshot.tabs.first().location == home)
    }
    test("closed tab callback is ignored") {
        val browser = FandomReaderBrowserController()
        browser.navigate(home, false)
        val id = browser.snapshot.activeTabId!!
        browser.openNewTab()
        browser.closeTab(id)
        check(!browser.updateWebLocation(id, character))
        check(browser.activeLocation == home)
    }
    test("native history preserved across web address synchronization") {
        val browser = FandomReaderBrowserController()
        browser.navigate(home, false)
        browser.navigate(character, true)
        browser.updateWebLocation(browser.snapshot.activeTabId!!, otherWiki)
        check(browser.back() == home)
        check(browser.forward() == otherWiki)
        check(browser.back() == home)
        browser.navigate(character, true)
        check(!browser.snapshot.canGoForward)
    }
    test("tab switch and close keep independent addresses") {
        val browser = FandomReaderBrowserController()
        browser.navigate(home, false)
        val first = browser.snapshot.activeTabId!!
        browser.openNewTab()
        val second = browser.snapshot.activeTabId!!
        browser.updateWebLocation(second, character)
        check(browser.switchTab(first) == home)
        browser.updateWebLocation(first, otherWiki)
        check(browser.switchTab(second) == character)
        check(browser.closeTab(second) == otherWiki)
        check(browser.snapshot.activeTabId == first)
    }
    test("12-tab limit, close others, reset and no active-tab update") {
        val browser = FandomReaderBrowserController()
        check(!browser.updateWebLocation(1, home))
        browser.navigate(home, false)
        repeat(FandomReaderBrowserController.MAX_TABS - 1) { check(browser.openNewTab() != null) }
        check(browser.openNewTab() == null)
        val active = browser.snapshot.activeTabId!!
        browser.closeOtherTabs(active)
        check(browser.snapshot.tabs.size == 1)
        browser.reset()
        check(browser.snapshot.tabs.isEmpty())
        check(!browser.updateWebLocation(active, character))
    }
    println("$passed wiki original-page checks passed")
}

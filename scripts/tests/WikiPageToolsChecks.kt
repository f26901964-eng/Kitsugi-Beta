package com.kitsugi.animelist.ui.screens.detail.wiki

import java.io.File
import java.util.concurrent.CancellationException

/** Pure production Kotlin checks: no Android mocks or translation/network services. */
suspend fun main() {
    var passed = 0
    suspend fun test(name: String, body: suspend () -> Unit) { body(); passed++; println("PASS $name") }
    val rules = WikiAdBlockRules.fromHosts(File("app/src/main/assets/wiki/stevenblack-hosts.txt").readText())
    test("pinned contributed blocklist parsed and major networks covered") {
        check(rules.ruleCount > 2500)
        listOf("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://ib.adnxs.com/ut/v3", "https://aax.amazon-adsystem.com/e/dtb/bid",
            "https://ad-assets.futurecdn.net/test.js").forEach { check(rules.shouldBlock(it, false)) { it } }
    }
    test("main document always bypasses resource filtering") {
        check(!rules.shouldBlock("https://doubleclick.net/", true))
        check(!rules.shouldBlock("https://ads.fandom.com/", true))
    }
    test("host boundaries prevent false positives") {
        listOf("https://notdoubleclick.net/ad.js", "https://doubleclick.net.evil.test/script.js",
            "https://example.com/path/doubleclick.net", "https://example.com/?next=doubleclick.net")
            .forEach { check(!rules.shouldBlock(it, false)) { it } }
    }
    test("wiki CDNs, ResourceLoader, images, media and content APIs protected") {
        listOf("https://static.wikia.nocookie.net/you-zitsu/images/x.png", "https://vignette.wikia.nocookie.net/image.png",
            "https://you-zitsu.fandom.com/load.php?modules=site.styles", "https://services.fandom.com/content/v1/metadata",
            "https://upload.wikimedia.org/wikipedia/image.jpg", "https://en.wikipedia.org/w/load.php")
            .forEach { check(!rules.shouldBlock(it, false)) { it } }
    }
    test("scoped Fandom ad route blocked without blocking neighboring routes") {
        check(rules.shouldBlock("https://services.fandom.com/ads/v1/config", false))
        check(rules.shouldBlock("https://ads.fandom.com/request", false))
        check(!rules.shouldBlock("https://services.fandom.com/ads-guide/article", false))
        check(!rules.shouldBlock("https://you-zitsu.fandom.com/wiki/Advertising", false))
    }
    test("malformed and non-http resource URLs fail open without exceptions") {
        listOf("", "not a url", "data:image/png,x", "blob:https://you-zitsu.fandom.com/test", "file:///image.png")
            .forEach { check(!rules.shouldBlock(it, false)) }
    }
    test("hosts parser rejects ABP filters, wildcard and TLD rules") {
        val custom = WikiAdBlockRules.fromHosts("0.0.0.0 valid.test\n0.0.0.0 com\n||wildcard.test^\n0.0.0.0 *.example.test\n# ignore\n")
        check(custom.shouldBlock("https://sub.valid.test/", false))
        check(!custom.shouldBlock("https://www.example.test/", false))
        check(!custom.shouldBlock("https://safe.com/", false))
    }
    test("protected wiki host wins even if an upstream list incorrectly blocks it") {
        val custom = WikiAdBlockRules.fromHosts("0.0.0.0 wikia.nocookie.net\n0.0.0.0 wikipedia.org")
        check(!custom.shouldBlock("https://static.wikia.nocookie.net/image.jpg", false))
        check(!custom.shouldBlock("https://en.wikipedia.org/wiki/Example", false))
    }
    test("automatic preference and manual original overrides") {
        val prefs = WikiPagePreferences(true, "auto", "tr", emptySet())
        check(prefs.configFor("wiki.fandom.com", null, null, 0).translate)
        check(!prefs.configFor("wiki.fandom.com", false, prefs.translationKey, 0).translate)
        val off = prefs.copy(autoTranslate = false)
        check(!off.configFor("wiki.fandom.com", null, null, 0).translate)
        check(off.configFor("wiki.fandom.com", true, off.translationKey, 0).translate)
    }
    test("changed app language or auto preference invalidates an older override") {
        val prefs = WikiPagePreferences(true, "auto", "de", emptySet())
        check(prefs.configFor("wiki.fandom.com", false, "true:auto:tr", 0).translate)
        val off = prefs.copy(autoTranslate = false)
        check(!off.configFor("wiki.fandom.com", true, prefs.translationKey, 0).translate)
    }
    test("persistent ad exceptions are exact per-wiki hosts, independent of translation") {
        val prefs = WikiPagePreferences(true, "auto", "tr", setOf("you-zitsu.fandom.com"))
        check(!prefs.configFor("YOU-ZITSU.FANDOM.COM", null, null, 0).blockAds)
        check(prefs.configFor("naruto.fandom.com", null, null, 0).blockAds)
        check(prefs.configFor("you-zitsu.fandom.com", null, null, 0).translate)
    }
    test("language codes sanitized and retry version preserved") {
        val prefs = WikiPagePreferences(true, "en&x=1", "auto", emptySet())
        val config = prefs.configFor("wiki.fandom.com", null, null, 3)
        check(config.source == "auto" && config.target == "tr" && config.retry == 3)
        check(prefs.copy(source = "ja", target = "zh-TW").configFor("wiki.fandom.com", null, null, 0).target == "zh-TW")
    }
    val rows = listOf(WikiDomText(12, 0, "Hello"), WikiDomText(19, 2, "world"))
    test("context batch roundtrip preserves slot order") {
        val packed = WikiDomTranslationBatch.pack(rows)
        check(WikiDomTranslationBatch.unpack(packed, 2) == listOf("Hello", "world"))
        val result = WikiDomTranslationBatch.translate(rows) { it.replace("Hello", "Merhaba").replace("world", "dünya") }
        check(result.map { it.item } == rows)
        check(result.map { it.translated } == listOf("Merhaba", "dünya"))
    }
    test("lost duplicated reordered or extra-output delimiters never guessed") {
        listOf("⟪K0⟫Hello⟪/K0⟫", "⟪K0⟫Hello⟪/K0⟫⟪K0⟫world⟪/K0⟫",
            "⟪K1⟫world⟪/K1⟫⟪K0⟫Hello⟪/K0⟫", "extra ⟪K0⟫Hello⟪/K0⟫⟪K1⟫world⟪/K1⟫",
            "⟪K0⟫⟪/K0⟫⟪K1⟫world⟪/K1⟫").forEach { check(WikiDomTranslationBatch.unpack(it, 2) == null) }
    }
    test("corrupt delimiter falls back per slot without swapping links") {
        var calls = 0
        val result = WikiDomTranslationBatch.translate(rows) { calls++; if (calls == 1) "Broken markers" else "TR:$it" }
        check(calls == 3)
        check(result.map { it.translated } == listOf("TR:Hello", "TR:world"))
    }
    test("outage does not fan out into one request per text node") {
        var calls = 0
        val result = WikiDomTranslationBatch.translate(rows) { calls++; null }
        check(calls == 1)
        check(result.all { it.translated == null })
    }
    test("literal marker collision uses individual slots") {
        val literal = rows.map { it.copy(text = "Literal ⟪K0⟫") }
        var calls = 0
        val result = WikiDomTranslationBatch.translate(literal) { calls++; it }
        check(calls == 2)
        check(result.all { it.translated == "Literal ⟪K0⟫" })
    }
    test("cancellation propagates, never treated as successful original text") {
        var cancelled = false
        try { WikiDomTranslationBatch.translate(rows) { throw CancellationException() } }
        catch (_: CancellationException) { cancelled = true }
        check(cancelled)
    }
    test("batch size and text length have hard limits") {
        var rejected = 0
        for (bad in listOf(emptyList(), List(9) { WikiDomText(it, 0, "text") }, listOf(WikiDomText(1, 0, "x".repeat(8001))))) {
            try { WikiDomTranslationBatch.translate(bad) { it } } catch (_: IllegalArgumentException) { rejected++ }
        }
        check(rejected == 3)
    }
    println("$passed wiki tools checks passed; ${rules.ruleCount} domain rules")
}

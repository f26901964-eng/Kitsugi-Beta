package com.kitsugi.animelist.ui.screens.detail.wiki

/** Run with production appearance + page policy files on a plain JVM. */
fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) { block(); passed++; println("PASS $name") }
    test("SITE default and unknown persisted values preserve source theme") {
        listOf("SITE", "", "invalid", "dark").forEach {
            check(WikiReadingAppearance.resolve(it, true) == "SITE")
            check(WikiReadingAppearance.resolve(it, false) == "SITE")
        }
        check(WikiPagePreferences(false, "auto", "tr", emptySet()).pageTheme == "SITE")
    }
    test("APP follows actual app palette and explicit theme ignores it") {
        check(WikiReadingAppearance.resolve("APP", true) == "DARK")
        check(WikiReadingAppearance.resolve("APP", false) == "LIGHT")
        for (dark in listOf(false, true)) {
            check(WikiReadingAppearance.resolve("LIGHT", dark) == "LIGHT")
            check(WikiReadingAppearance.resolve("DARK", dark) == "DARK")
        }
    }
    test("font preference clamps and snaps to ten-percent steps") {
        check(WikiReadingAppearance.textPercent(Int.MIN_VALUE) == 80)
        check(WikiReadingAppearance.textPercent(Int.MAX_VALUE) == 180)
        check(WikiReadingAppearance.textPercent(124) == 120)
        check(WikiReadingAppearance.textPercent(125) == 130)
        for (n in 80..180 step 10) check(WikiReadingAppearance.textPercent(n) == n)
    }
    test("system accessibility font scale participates in bounded engine zoom") {
        check(WikiReadingAppearance.engineTextZoom(100, 1f) == 100)
        check(WikiReadingAppearance.engineTextZoom(120, 1.5f) == 180)
        check(WikiReadingAppearance.engineTextZoom(180, 3f) == 300)
        check(WikiReadingAppearance.engineTextZoom(80, 0.5f) == 50)
    }
    test("invalid scales do not crash or overflow") {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -2f).forEach {
            check(WikiReadingAppearance.engineTextZoom(100, it) == 100)
        }
        check(WikiReadingAppearance.engineTextZoom(180, Float.MAX_VALUE) == 300)
        check(WikiReadingAppearance.engineTextZoom(100, Float.MIN_VALUE) == 50)
    }
    test("appearance-only changes preserve translation key, manual choice and ad configuration") {
        val original = WikiPagePreferences(true, "en", "tr", setOf("example.fandom.com"))
        val changed = original.copy(pageTheme = "DARK", textZoom = 150)
        check(original.translationKey == changed.translationKey)
        check(original.configFor("example.fandom.com", false, original.translationKey, 4) ==
            changed.configFor("example.fandom.com", false, original.translationKey, 4))
    }
    println("$passed appearance policy checks passed")
}

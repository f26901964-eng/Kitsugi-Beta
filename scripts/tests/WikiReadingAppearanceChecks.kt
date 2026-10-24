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
    test("palette follows the app only when the scheme matches") {
        val dark = WikiReadingAppearance.palette("DARK", true, "#101418", "#161b21", "#eef1f5")
        check(dark.background == "#101418")
        check(dark.text == "#eef1f5")
        check(dark.surface == "#161b21")
        // Uygulama açıkken "Koyu tema" seçilirse uygulamanın beyaz yüzeyi kullanılmaz.
        val forced = WikiReadingAppearance.palette("DARK", false, "#ffffff", "#ffffff", "#101010")
        check(forced.background == "#0e1116")
        check(forced.text == "#e9edf3")
        // Uygulama koyuyken "Açık tema" da aynı şekilde nötr kalır.
        val light = WikiReadingAppearance.palette("LIGHT", true, "#101418", "#161b21", "#eef1f5")
        check(light.background == "#ffffff")
        check(light.text == "#171a1f")
    }
    test("palette validates hostile or malformed colours") {
        for (value in listOf("", "red", "#12345", "#1234567", "url(javascript:alert(1))", "#GGGGGG")) {
            val palette = WikiReadingAppearance.palette("DARK", false, value, value, value)
            check(palette.background == "#0e1116") { value }
            check(palette.text == "#e9edf3") { value }
            check(palette.signature.none { it.isWhitespace() })
        }
        check(WikiReadingAppearance.mix("#000000", "#ffffff", 0.5f) == "#808080")
        check(WikiReadingAppearance.mix("#000000", "#ffffff", -3f) == "#000000")
        check(WikiReadingAppearance.mix("#ffffff", "#000000", 4f) == "#000000")
    }
    test("dark card surfaces stay distinguishable from a pure black canvas") {
        val palette = WikiReadingAppearance.palette("DARK", true, "#000000", "#000000", "#ffffff")
        check(palette.background == "#000000")
        check(palette.surface != palette.background)
        check(palette.raised != palette.surface)
        check(palette.border != palette.surface)
        check(palette.muted != palette.text)
    }
    test("palette JSON stays valid and trimmed") {
        val json = WikiReadingAppearance.palette("DARK", false, "", "", "").toJson()
        check(json.startsWith("{\"background\":\"#"))
        check(json.endsWith("\"}"))
        check(json.count { it == '"' } == 28) { json }
        check(!json.contains(" "))
    }
    test("desktop layout is fitted to the device width") {
        check(WikiReaderFit.layoutWidth(true, 360f) == 1200)
        check(WikiReaderFit.layoutWidth(false, 360f) == 360)
        check(WikiReaderFit.scale(360f, 1200) < 0.31f)
        check(WikiReaderFit.scale(360f, 1200) > 0.29f)
        check(WikiReaderFit.scale(2000f, 1200) == 1f)
        check(WikiReaderFit.scale(Float.NaN, 1200) == 1f)
        check(WikiReaderFit.scale(360f, 0) == 1f)
        check(WikiReaderFit.scale(1f, 1200) == 0.2f)
        check(WikiReaderFit.initialScalePercent(360f, 1200) == 30)
        check(WikiReaderFit.initialScalePercent(360f, 0) == 100)
    }
    test("text zoom compensates the fit without overriding the user") {
        val fit = WikiReaderFit.scale(360f, 1200)
        check(WikiReaderFit.readingTextZoom(100, fit) >= 220)
        check(WikiReaderFit.readingTextZoom(100, fit) <= 300)
        check(WikiReaderFit.readingTextZoom(180, fit) == 300)
        check(WikiReaderFit.readingTextZoom(100, 1f) == 100)
        check(WikiReaderFit.readingTextZoom(100, 0.9f) == 100)
        check(WikiReaderFit.effectivePercent(100, fit) in 220..300)
        check(WikiReaderFit.effectivePercent(100, 1f) == 100)
    }
    println("$passed appearance policy checks passed")
}

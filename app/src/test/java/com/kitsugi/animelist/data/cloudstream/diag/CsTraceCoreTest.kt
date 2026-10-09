package com.kitsugi.animelist.data.cloudstream.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsTraceCoreTest {

    @Test
    fun testFormatLineAndSummarize() {
        val now = 1728470000000L
        val line1 = CsTraceCore.formatLine(now, CsTraceLevel.INFO, "Dizilla", "search", "'Naruto' -> 2 sonuc")
        val line2 = CsTraceCore.formatLine(now + 1000, CsTraceLevel.WARN, "Dizilla", "match", "Eslesme bulunamadi")
        val line3 = CsTraceCore.formatLine(now + 2000, CsTraceLevel.ERROR, "AnimeSiteleri", "load", "Timeout")

        val lines = listOf(line1, line2, line3)
        val summaries = CsTraceCore.summarize(lines)

        assertEquals(2, summaries.size)
        val errorSummary = summaries.first()
        assertEquals("AnimeSiteleri", errorSummary.plugin)
        assertEquals(1, errorSummary.errorCount)
        assertEquals(0, errorSummary.warnCount)

        val warnSummary = summaries[1]
        assertEquals("Dizilla", warnSummary.plugin)
        assertEquals(0, warnSummary.errorCount)
        assertEquals(1, warnSummary.warnCount)
        assertEquals(1, warnSummary.infoCount)
    }

    @Test
    fun testFormatThrowable() {
        val ex = RuntimeException("Test failure", IllegalStateException("Root cause"))
        val formatted = CsTraceCore.formatThrowable(ex)

        assertTrue(formatted.isNotEmpty())
        assertTrue(formatted.first().contains("RuntimeException: Test failure"))
        assertTrue(formatted.any { it.contains("caused by:") && it.contains("IllegalStateException: Root cause") })
    }

    @Test
    fun testBuildReport() {
        val now = 1728470000000L
        val lines = listOf(
            CsTraceCore.formatLine(now, CsTraceLevel.INFO, "SESSION", "-", "Arama baslatildi"),
            CsTraceCore.formatLine(now + 500, CsTraceLevel.ERROR, "4KFilm", "loadLinks", "403 Forbidden")
        )
        val header = listOf("Versiyon" to "2.4.218", "Cihaz" to "Pixel 8")
        val inventory = listOf("4KFilm v1.0", "Dizilla v2.1")

        val report = CsTraceCore.buildReport(header, inventory, lines)

        assertTrue(report.contains("# Kitsugi — CS Eklenti Canlı İzleme Raporu"))
        assertTrue(report.contains("**Versiyon:** 2.4.218"))
        assertTrue(report.contains("4KFilm"))
        assertTrue(report.contains("## Yüklü Eklentiler"))
        assertTrue(report.contains("## Son Olaylar"))
    }
}

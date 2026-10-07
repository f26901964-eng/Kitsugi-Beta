package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.CrossPlatformStats
import com.kitsugi.animelist.model.CrossSyncLogEntry
import com.kitsugi.animelist.model.CrossSyncProgressState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSyncReportFormatterTest {
    @Test
    fun reportIncludesEveryIssueAndFullIdentityDetailsEvenWhenLiveLogIsBounded() {
        val report = CrossSyncReportFormatter.format(
            CrossSyncProgressState(
                isCompleted = true,
                currentStep = "Eşitleme kısmen tamamlandı",
                platformStats = mapOf("AniList" to CrossPlatformStats("AniList", initialCount = 4, errorCount = 1)),
                logs = listOf(CrossSyncLogEntry(platform = "AniList", message = "Son canlı kayıt")),
                reportLogs = listOf(
                    CrossSyncLogEntry(
                        platform = "Eşleştirme",
                        message = "Kimlik çakışması saptandı",
                        isWarning = true,
                        details = "MAL ID: 101 ile MAL ID: 202 uyuşmuyor"
                    ),
                    CrossSyncLogEntry(
                        platform = "AniList",
                        message = "Kayıt eklenemedi",
                        isError = true,
                        details = "java.io.IOException: HTTP 500"
                    ),
                    CrossSyncLogEntry(platform = "Simkl", message = "Başarıyla eklendi")
                )
            ),
            generatedAt = 1234L
        )

        assertTrue(report.contains("SORUNLAR / UYARILAR (2)"))
        assertTrue(report.contains("MAL ID: 101 ile MAL ID: 202 uyuşmuyor"))
        assertTrue(report.contains("java.io.IOException: HTTP 500"))
        assertTrue(report.contains("TÜM İŞLEM VE EŞLEŞTİRME KAYITLARI (3)"))
        assertTrue(report.contains("Başarıyla eklendi"))
        assertTrue(report.contains("AniList: başlangıç=4"))
    }

    @Test
    fun reportRedactsBearerCredentials() {
        val report = CrossSyncReportFormatter.format(
            CrossSyncProgressState(
                reportLogs = listOf(
                    CrossSyncLogEntry(
                        platform = "Simkl",
                        message = "İstek başarısız",
                        isError = true,
                        details = "Authorization: Bearer abc.def.secret"
                    )
                )
            ),
            generatedAt = 1234L
        )

        assertTrue(report.contains("Bearer [REDACTED]"))
        assertFalse(report.contains("abc.def.secret"))
    }

    @Test
    fun reportFileNameUsesExpectedPrefixAndTextExtension() {
        val fileName = CrossSyncReportFormatter.fileName(1234L)
        assertTrue(fileName.startsWith("Kitsugi_CrossSync_Report_"))
        assertTrue(fileName.endsWith(".txt"))
    }
}

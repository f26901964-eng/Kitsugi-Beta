package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.CrossSyncLogEntry
import com.kitsugi.animelist.model.CrossSyncProgressState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Builds a readable, credential-free report for one cross-platform sync run. */
object CrossSyncReportFormatter {
    private const val DATE_PATTERN = "yyyyMMdd_HHmmss_SSS"
    private val bearerPattern = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*")
    private val credentialPattern = Regex(
        "(?i)((?:access|refresh|id)[_\\s-]?token|client[_\\s-]?secret|api[_\\s-]?key|password|passwd|oauth[_\\s-]?code)(\\s*[:=]\\s*)(?:Bearer\\s+)?[^\\s,;&]+"
    )
    private val queryCredentialPattern = Regex("(?i)([?&](?:access_token|refresh_token|token|client_secret|api_key|code)=)[^&#\\s]+")
    private val controlCharacterPattern = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")

    fun fileName(timestamp: Long): String =
        "Kitsugi_CrossSync_Report_${SimpleDateFormat(DATE_PATTERN, Locale.ROOT).format(Date(timestamp))}.txt"

    fun format(state: CrossSyncProgressState, generatedAt: Long = System.currentTimeMillis()): String = buildString {
        val reportEntries = state.reportLogs.ifEmpty { state.logs }
        val issues = reportEntries.filter { it.isError || it.isWarning }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()

        appendLine("Kitsugi — Çapraz Platform Eşitleme Raporu")
        appendLine("Rapor oluşturma zamanı: ${formatter.format(Date(generatedAt))}")
        state.startedAt?.let { appendLine("Eşitleme başlangıcı: ${formatter.format(Date(it))}") }
        state.finishedAt?.let { appendLine("Eşitleme bitişi: ${formatter.format(Date(it))}") }
        appendLine("Sonuç: ${when {
            state.isRunning -> "Devam ediyor (kısmi rapor)"
            state.errorMessage != null -> "Hata ile sonlandı"
            state.isCompleted && issues.any { it.isError } -> "Kısmen tamamlandı; hata var"
            state.isCompleted -> "Tamamlandı"
            else -> "Tamamlanmadı / durduruldu"
        }}")
        if (state.currentStep.isNotBlank()) appendLine("Son adım: ${safeText(state.currentStep)}")
        if (state.currentDetail.isNotBlank()) appendLine("Ayrıntı: ${safeText(state.currentDetail)}")
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine("Genel hata: ${safeText(it)}") }
        val elapsedMs = state.startedAt?.let { start -> (state.finishedAt ?: generatedAt) - start }
        elapsedMs?.takeIf { it >= 0 }?.let { appendLine("Geçen süre: ${it / 1000} sn") }
        appendLine("İlerleme: ${state.processedItems} / ${state.totalItems} ${safeText(state.progressUnit)}")
        appendLine("Oluşturulan işlem kaydı: ${state.totalEventCount}")
        appendLine("Hata/uyarı sayısı: ${state.issueCount}")

        appendLine()
        appendLine("=== PLATFORM ÖZETİ ===")
        if (state.platformStats.isEmpty()) {
            appendLine("Platform istatistiği yok.")
        } else {
            state.platformStats.values.forEach { stats ->
                appendLine(
                    "${safeText(stats.platformName)}: başlangıç=${stats.initialCount}, " +
                        "eklenen=${stats.addedCount}, güncellenen=${stats.updatedCount}, " +
                        "atlanan=${stats.skippedCount}, hata=${stats.errorCount}"
                )
            }
        }

        appendLine()
        appendLine("=== SORUNLAR / UYARILAR (${issues.size}) ===")
        if (issues.isEmpty()) {
            appendLine("Bu eşitleme sırasında kaydedilmiş bir hata veya uyarı yok.")
        } else {
            issues.forEachIndexed { index, entry ->
                appendEntry(index + 1, entry, formatter)
            }
        }

        appendLine()
        appendLine("=== TÜM İŞLEM VE EŞLEŞTİRME KAYITLARI (${reportEntries.size}) ===")
        if (reportEntries.isEmpty()) {
            appendLine("İşlem günlüğü boş.")
        } else {
            reportEntries.forEachIndexed { index, entry ->
                appendEntry(index + 1, entry, formatter)
            }
        }

        appendLine()
        appendLine("Not: Bu rapora OAuth erişim/yenileme anahtarları dahil edilmez. Medya başlıkları ve harici içerik kimlikleri, eşleştirme hatalarını teşhis edebilmek için rapora eklenir.")
    }

    private fun StringBuilder.appendEntry(
        index: Int,
        entry: CrossSyncLogEntry,
        formatter: SimpleDateFormat
    ) {
        val severity = when {
            entry.isError -> "HATA"
            entry.isWarning -> "UYARI"
            else -> "BİLGİ"
        }
        appendLine("$index. [${formatter.format(Date(entry.timestamp))}] [$severity] [${safeText(entry.platform)}] ${safeText(entry.message)}")
        entry.details?.takeIf { it.isNotBlank() }?.let { details ->
            details.lineSequence().forEach { line -> appendLine("   ${safeText(line)}") }
        }
    }

    /** Redact common credential formats in upstream error text before it reaches disk. */
    private fun safeText(value: String): String = value
        .replace(bearerPattern) { match -> "${match.groupValues[1]}[REDACTED]" }
        .replace(credentialPattern) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
        }
        .replace(queryCredentialPattern) { match -> "${match.groupValues[1]}[REDACTED]" }
        .replace(controlCharacterPattern, "")
}

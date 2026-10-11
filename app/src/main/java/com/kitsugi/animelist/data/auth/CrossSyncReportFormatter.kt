package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.CrossSyncLogEntry
import com.kitsugi.animelist.model.CrossSyncProgressState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

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

    fun format(
        state: CrossSyncProgressState,
        generatedAt: Long = System.currentTimeMillis(),
        errorCodeProvider: ((String) -> String?)? = null,
    ): String = buildString {
        val reportEntries = state.reportLogs.ifEmpty { state.logs }
        val issues = reportEntries.filter { it.isError || it.isWarning }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()

        appendLine(KitsugiStrings.get(R.string.cross_sync_rapor_basligi).ifBlank { "Kitsugi — Çapraz Platform Eşitleme Raporu" })
        appendLine(KitsugiStrings.get(R.string.rapor_olusturma_zamani_x, formatter.format(Date(generatedAt))).ifBlank { "Rapor oluşturma zamanı: ${formatter.format(Date(generatedAt))}" })
        state.startedAt?.let { appendLine(KitsugiStrings.get(R.string.esitleme_baslangici_x, formatter.format(Date(it))).ifBlank { "Eşitleme başlangıcı: ${formatter.format(Date(it))}" }) }
        state.finishedAt?.let { appendLine(KitsugiStrings.get(R.string.esitleme_bitisi_x, formatter.format(Date(it))).ifBlank { "Eşitleme bitişi: ${formatter.format(Date(it))}" }) }
        val outcome = when {
            state.isRunning -> KitsugiStrings.get(R.string.sonuc_devam_ediyor_kismi_rapor).ifBlank { "Devam ediyor (kısmi rapor)" }
            state.errorMessage != null -> KitsugiStrings.get(R.string.sonuc_hata_ile_sonlandi).ifBlank { "Hata ile sonlandı" }
            state.isCompleted && issues.any { it.isError } -> KitsugiStrings.get(R.string.sonuc_kismen_tamamlandi_hata_var).ifBlank { "Kısmen tamamlandı; hata var" }
            state.isCompleted -> KitsugiStrings.get(R.string.tamamlandi).ifBlank { "Tamamlandı" }
            else -> KitsugiStrings.get(R.string.sonuc_tamamlanmadi_durduruldu).ifBlank { "Tamamlanmadı / durduruldu" }
        }
        appendLine(KitsugiStrings.get(R.string.sonuc_x, outcome).ifBlank { "Sonuç: $outcome" })
        if (state.currentStep.isNotBlank()) appendLine(KitsugiStrings.get(R.string.son_adim_x, safeText(state.currentStep)).ifBlank { "Son adım: ${safeText(state.currentStep)}" })
        if (state.currentDetail.isNotBlank()) appendLine(KitsugiStrings.get(R.string.ayrinti_x, safeText(state.currentDetail)).ifBlank { "Ayrıntı: ${safeText(state.currentDetail)}" })
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine(KitsugiStrings.get(R.string.genel_hata_x, safeText(it)).ifBlank { "Genel hata: ${safeText(it)}" }) }
        val elapsedMs = state.startedAt?.let { start -> (state.finishedAt ?: generatedAt) - start }
        elapsedMs?.takeIf { it >= 0 }?.let { appendLine(KitsugiStrings.get(R.string.gecen_sure_x_sn, it / 1000).ifBlank { "Geçen süre: ${it / 1000} sn" }) }
        appendLine(KitsugiStrings.get(R.string.ilerleme_x_x_x, state.processedItems, state.totalItems, safeText(state.progressUnit)).ifBlank { "İlerleme: ${state.processedItems} / ${state.totalItems} ${safeText(state.progressUnit)}" })
        appendLine(KitsugiStrings.get(R.string.olusturulan_islem_kaydi_x, state.totalEventCount).ifBlank { "Oluşturulan işlem kaydı: ${state.totalEventCount}" })
        appendLine(KitsugiStrings.get(R.string.hata_uyari_sayisi_x, state.issueCount).ifBlank { "Hata/uyarı sayısı: ${state.issueCount}" })

        appendLine()
        appendLine(KitsugiStrings.get(R.string.platform_ozeti_baslik).ifBlank { "=== PLATFORM ÖZETİ ===" })
        if (state.platformStats.isEmpty()) {
            appendLine(KitsugiStrings.get(R.string.platform_istatistigi_yok).ifBlank { "Platform istatistiği yok." })
        } else {
            state.platformStats.values.forEach { stats ->
                appendLine(
                    KitsugiStrings.get(
                        R.string.platform_ozet_satiri_x,
                        safeText(stats.platformName),
                        stats.initialCount,
                        stats.addedCount,
                        stats.updatedCount,
                        stats.skippedCount,
                        stats.errorCount
                    ).ifBlank {
                        "${safeText(stats.platformName)}: başlangıç=${stats.initialCount}, eklenen=${stats.addedCount}, güncellenen=${stats.updatedCount}, atlanan=${stats.skippedCount}, hata=${stats.errorCount}"
                    }
                )
            }
        }

        appendLine()
        appendLine(KitsugiStrings.get(R.string.sorunlar_uyarilar_baslik_x, issues.size).ifBlank { "=== SORUNLAR / UYARILAR (${issues.size}) ===" })
        if (issues.isEmpty()) {
            appendLine(KitsugiStrings.get(R.string.sorun_uyari_kaydi_yok).ifBlank { "Bu eşitleme sırasında kaydedilmiş bir hata veya uyarı yok." })
        } else {
            issues.forEachIndexed { index, entry ->
                appendEntry(index + 1, entry, formatter, errorCodeProvider)
            }
        }

        appendLine()
        appendLine(KitsugiStrings.get(R.string.tum_islem_kayitlari_baslik_x, reportEntries.size).ifBlank { "=== TÜM İŞLEM VE EŞLEŞTİRME KAYITLARI (${reportEntries.size}) ===" })
        if (reportEntries.isEmpty()) {
            appendLine(KitsugiStrings.get(R.string.islem_gunlugu_bos).ifBlank { "İşlem günlüğü boş." })
        } else {
            reportEntries.forEachIndexed { index, entry ->
                appendEntry(index + 1, entry, formatter, errorCodeProvider)
            }
        }

        appendErrorCodeSection(reportEntries, errorCodeProvider)

        appendLine()
        appendLine(KitsugiStrings.get(R.string.cross_sync_rapor_gizlilik_notu).ifBlank { "Not: Bu rapora OAuth erişim/yenileme anahtarları dahil edilmez. Medya başlıkları ve harici içerik kimlikleri, eşleştirme hatalarını teşhis edebilmek için rapora eklenir." })
    }

    private fun StringBuilder.appendErrorCodeSection(
        entries: List<CrossSyncLogEntry>,
        errorCodeProvider: ((String) -> String?)?,
    ) {
        val codes = entries.mapNotNull { it.errorCode?.takeIf { code -> code.isNotBlank() } }
            .distinct()
            .sorted()
        if (codes.isEmpty()) return

        appendLine()
        appendLine("=== HATA KODLARI (${codes.size}) ===")
        if (errorCodeProvider == null) {
            codes.forEach { appendLine("• $it") }
            return
        }
        codes.forEach { code ->
            val explanation = errorCodeProvider(code)
            if (explanation.isNullOrBlank()) {
                appendLine("• $code")
            } else {
                explanation.lineSequence().forEach { line -> appendLine(safeText(line)) }
                appendLine()
            }
        }
    }

    private fun StringBuilder.appendEntry(
        index: Int,
        entry: CrossSyncLogEntry,
        formatter: SimpleDateFormat,
        errorCodeProvider: ((String) -> String?)? = null,
    ) {
        val severity = when {
            entry.isError -> KitsugiStrings.get(R.string.onem_hata).ifBlank { "HATA" }
            entry.isWarning -> KitsugiStrings.get(R.string.onem_uyari).ifBlank { "UYARI" }
            else -> KitsugiStrings.get(R.string.onem_bilgi).ifBlank { "BİLGİ" }
        }
        appendLine("$index. [${formatter.format(Date(entry.timestamp))}] [$severity] [${safeText(entry.platform)}] ${safeText(entry.message)}")
        entry.errorCode?.takeIf { it.isNotBlank() }?.let { code ->
            appendLine("   Kod: $code")
            errorCodeProvider?.invoke(code)?.takeIf { it.isNotBlank() }?.let { explanation ->
                explanation.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { line -> appendLine("   | ${safeText(line.trim())}") }
            }
        }
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

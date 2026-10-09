package com.kitsugi.animelist.data.cloudstream.diag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trace olay seviyesi. */
enum class CsTraceLevel { INFO, WARN, ERROR }

/**
 * CS eklenti izleme (trace) sisteminin Android'e bağımlı OLMAYAN çekirdeği.
 *
 * Satır biçimi (tek satır = tek olay):
 *   `2026-10-09 12:03:44.120 | WARN | Dizilla | search | 'Naruto' → 0 sonuç`
 *
 * Bir olayın hata/stack bilgisi, olayın altına girintili satırlar olarak yazılır:
 *   `    ! java.net.UnknownHostException: dizilla.club`
 *   `    at com.foo.Bar.baz(Bar.kt:12)`
 *
 * Bu dosya Android API'si kullanmaz; böylece JVM üzerinde birim testi ile
 * derlenip doğrulanabilir. Android tarafı [CsTrace] bu fonksiyonları kullanır.
 */
object CsTraceCore {

    /** Olay satırı ayrıştırma deseni: ts | LEVEL | plugin | stage | message */
    private val EVENT_REGEX = Regex(
        "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\| (INFO|WARN|ERROR) \\| (.*?) \\| (.*?) \\| (.*)$"
    )

    private const val MAX_STACK_FRAMES = 12
    private const val MAX_CAUSE_DEPTH = 4
    private const val MAX_LINE_LENGTH = 600

    /** Rapor için her plugin'in özet satırı. */
    data class PluginSummary(
        val plugin: String,
        val infoCount: Int,
        val warnCount: Int,
        val errorCount: Int,
        val lastWarn: String?,
        val lastError: String?
    )

    fun formatTimestamp(epochMs: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        return f.format(Date(epochMs))
    }

    /** Tek satırlık olay metni. Satır sonları ve ayraç çakışmaları temizlenir. */
    fun formatLine(
        epochMs: Long,
        level: CsTraceLevel,
        plugin: String,
        stage: String,
        message: String
    ): String {
        val safePlugin = sanitize(plugin)
        val safeStage = sanitize(stage)
        val safeMessage = sanitize(message).take(MAX_LINE_LENGTH)
        return "${formatTimestamp(epochMs)} | ${level.name} | $safePlugin | $safeStage | $safeMessage"
    }

    /**
     * Bir Throwable'ı girintili satırlara çevirir: sınıf, mesaj, neden zinciri ve
     * ilk birkaç stack karesi. Rapor boyutunu makul tutmak için sınırlandırılmıştır.
     */
    fun formatThrowable(t: Throwable): List<String> {
        val out = mutableListOf<String>()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            val prefix = if (depth == 0) "    ! " else "    ! caused by: "
            out.add(prefix + sanitize("${current.javaClass.name}: ${current.message ?: "(mesaj yok)"}").take(MAX_LINE_LENGTH))
            current.stackTrace.take(MAX_STACK_FRAMES).forEach { frame ->
                out.add("    at $frame")
            }
            if (current.stackTrace.size > MAX_STACK_FRAMES) {
                out.add("    ... ${current.stackTrace.size - MAX_STACK_FRAMES} kare daha")
            }
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return out
    }

    /**
     * Ham satırlardan (olay + stack satırları karışık) plugin başına özet çıkarır.
     * Girintili satırlar tek başına sayılmaz.
     */
    fun summarize(lines: List<String>): List<PluginSummary> {
        class Acc {
            var info = 0
            var warn = 0
            var error = 0
            var lastWarn: String? = null
            var lastError: String? = null
        }
        val map = LinkedHashMap<String, Acc>()
        for (line in lines) {
            val m = EVENT_REGEX.matchEntire(line) ?: continue
            val level = m.groupValues[2]
            val plugin = m.groupValues[3]
            val stage = m.groupValues[4]
            val message = m.groupValues[5]
            if (plugin == "SESSION") continue
            val acc = map.getOrPut(plugin) { Acc() }
            when (level) {
                "INFO" -> acc.info++
                "WARN" -> {
                    acc.warn++
                    acc.lastWarn = "[$stage] ${message.take(200)}"
                }
                "ERROR" -> {
                    acc.error++
                    acc.lastError = "[$stage] ${message.take(200)}"
                }
            }
        }
        return map.map { (name, a) ->
            PluginSummary(
                plugin = name,
                infoCount = a.info,
                warnCount = a.warn,
                errorCount = a.error,
                lastWarn = a.lastWarn,
                lastError = a.lastError
            )
        }.sortedWith(
            compareByDescending<PluginSummary> { it.errorCount }
                .thenByDescending { it.warnCount }
                .thenBy { it.plugin.lowercase(Locale.ROOT) }
        )
    }

    /**
     * Paylaşılabilir Markdown raporu üretir.
     *
     * @param header    Uygulama / cihaz / zaman gibi üst bilgi çiftleri.
     * @param inventory Yüklü eklentilerin kısa açıklama satırları.
     * @param lines     Ham trace satırları (eskiden yeniye).
     * @param maxEventLines Rapora dahil edilecek en fazla satır sayısı (sondan).
     */
    fun buildReport(
        header: List<Pair<String, String>>,
        inventory: List<String>,
        lines: List<String>,
        maxEventLines: Int = 4000
    ): String {
        val sb = StringBuilder()
        sb.append("# Kitsugi — CS Eklenti Canlı İzleme Raporu\n\n")
        for ((k, v) in header) sb.append("- **$k:** $v\n")

        val eventLines = lines.filter { EVENT_REGEX.matches(it) }
        val errors = eventLines.count { it.contains(" | ERROR | ") }
        val warns = eventLines.count { it.contains(" | WARN | ") }
        val sessions = eventLines.count { it.contains(" | SESSION | ") }
        sb.append("- **Olay sayısı:** ${eventLines.size} (ERROR: $errors, WARN: $warns)\n")
        sb.append("- **Arama oturumu sayısı:** $sessions\n\n")

        sb.append("## Eklenti Özeti\n\n")
        sb.append("| Eklenti | ERROR | WARN | INFO | Son hata | Son uyarı |\n")
        sb.append("|---|---:|---:|---:|---|---|\n")
        for (s in summarize(lines)) {
            sb.append("| ${mdEscape(s.plugin)} | ${s.errorCount} | ${s.warnCount} | ${s.infoCount} | ")
            sb.append("${mdEscape(s.lastError ?: "-")} | ${mdEscape(s.lastWarn ?: "-")} |\n")
        }
        sb.append("\n")

        if (inventory.isNotEmpty()) {
            sb.append("## Yüklü Eklentiler (arama anında)\n\n")
            inventory.forEach { sb.append("- ").append(it).append('\n') }
            sb.append("\n")
        }

        sb.append("## Son Olaylar\n\n")
        sb.append("Aşağıdaki blok, en yeni olaylara kadar uzanan ham izdir.\n\n")
        sb.append("```text\n")
        val tail = lines.takeLast(maxEventLines)
        tail.forEach { sb.append(it.replace("```", "'''")).append('\n') }
        sb.append("```\n")
        return sb.toString()
    }

    private fun sanitize(s: String): String =
        s.replace('\r', ' ')
            .replace('\n', ' ')
            .replace(" | ", " / ")

    private fun mdEscape(s: String): String =
        s.replace("|", "\\|").replace("\n", " ").take(300)
}

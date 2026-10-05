package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.os.Build
import com.kitsugi.animelist.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KitsugiCrashLogger — Merkezi çökme günlükleme sistemi.
 *
 * Görevleri:
 *  • Çökme anında tam ayrıntılı rapor dosyası yazar (senkron, process ölmeden önce)
 *  • Geçmiş çökmeleri `crash_history.txt` dosyasında tutar (max 50 kB × 20 kayıt)
 *  • Çökme anında logcat anlık görüntüsünü yakalar
 *  • Arka plan thread çökmelerini sessizce yutmak yerine kayıt altına alır
 */
object KitsugiCrashLogger {

    private const val CRASH_LOG_FILE     = "crash_log.txt"
    private const val CRASH_HISTORY_FILE = "crash_history.txt"
    private const val LOGCAT_CRASH_FILE  = "logcat_at_crash.txt"
    private const val MAX_HISTORY_BYTES  = 200 * 1024L  // 200 KB
    private const val MAX_LOGCAT_LINES   = 500

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // ── Çökme Raporu Oluşturma ─────────────────────────────────────────────────

    /**
     * Tam ayrıntılı çökme raporu metni oluşturur.
     * Bu fonksiyon senkron çalışır — thread kill edilmeden önce çağrılmalıdır.
     */
    fun buildCrashReport(
        thread: Thread,
        throwable: Throwable,
        isForeground: Boolean
    ): String = buildString {

        val now = dateFmt.format(Date())
        val uptime = (System.currentTimeMillis() - (KitsugiApplication_LaunchTime ?: System.currentTimeMillis())) / 1000L

        // ── Başlık ──
        appendLine("╔══════════════════════════════════════════════════════════════╗")
        appendLine("║           KİTSUGİ CRASH REPORT — $now")
        appendLine("╚══════════════════════════════════════════════════════════════╝")
        appendLine()

        // ── Uygulama Bilgisi ──
        appendLine("▶ UYGULAMA BİLGİSİ")
        appendLine("  Version    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("  Build Type : ${BuildConfig.BUILD_TYPE}")
        appendLine("  Uptime     : ${uptime}s")
        appendLine()

        // ── Cihaz Bilgisi ──
        appendLine("▶ CİHAZ BİLGİSİ")
        appendLine("  Marka      : ${Build.BRAND}")
        appendLine("  Model      : ${Build.MODEL}")
        appendLine("  Üretici    : ${Build.MANUFACTURER}")
        appendLine("  Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("  Cihaz      : ${Build.DEVICE}")
        appendLine("  Ürün       : ${Build.PRODUCT}")
        appendLine("  ABI        : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "N/A"}")
        appendLine()

        // ── Thread Bilgisi ──
        val threadType = if (isForeground) "FOREGROUND (Ana UI Thread)" else "BACKGROUND (${thread.name})"
        appendLine("▶ THREAD BİLGİSİ")
        appendLine("  Thread     : ${thread.name}")
        appendLine("  Thread ID  : ${runCatching { Thread.currentThread().threadId() }.getOrElse { thread.id }}")
        appendLine("  Tip        : $threadType")
        appendLine("  Daemon     : ${thread.isDaemon}")
        appendLine()

        // ── Bellek Bilgisi ──
        val rt = Runtime.getRuntime()
        val totalMb = rt.totalMemory() / 1024 / 1024
        val freeMb  = rt.freeMemory()  / 1024 / 1024
        val usedMb  = totalMb - freeMb
        val maxMb   = rt.maxMemory()   / 1024 / 1024
        appendLine("▶ BELLEK DURUMU")
        appendLine("  Kullanılan : ${usedMb} MB / ${maxMb} MB (max)")
        appendLine("  Toplam JVM : ${totalMb} MB")
        appendLine("  Serbest    : ${freeMb} MB")
        appendLine()

        // ── Hata Zinciri ──
        appendLine("▶ HATA ZİNCİRİ")
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < 10) {
            val prefix = if (depth == 0) "  HATA  " else "  NEDEN $depth"
            appendLine("  $prefix : ${cause.javaClass.name}: ${cause.message}")
            cause = cause.cause
            depth++
        }
        appendLine()

        // ── Tam Stacktrace ──
        appendLine("▶ TAM STACKTRACE")
        appendLine(android.util.Log.getStackTraceString(throwable))

        // ── Aktif Thread Listesi (ilk 30) ──
        try {
            appendLine("▶ AKTİF THREAD LİSTESİ (ilk 30)")
            Thread.getAllStackTraces().keys
                .take(30)
                .sortedByDescending { it.name == "main" }
                .forEach { t ->
                    val state = t.state.name.take(8).padEnd(8)
                    appendLine("  [$state] ${t.name} (daemon=${t.isDaemon})")
                }
            appendLine()
        } catch (_: Exception) {}

        appendLine("═══════════════════════════════════════════════════════════════")
    }

    // ── Senkron Dosya Yazımı (crash anında çağrılır) ──────────────────────────

    /**
     * Çökme raporunu `crash_log.txt` ve `crash_history.txt` dosyalarına SENKRON yazar.
     * Bu metot, process kill edilmeden hemen önce çağrılmalıdır.
     * Hem dahili hem de harici erişilebilir dizinlere yazar, çökme bayrağını işaretler.
     */
    fun writeCrashReport(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        isForeground: Boolean
    ): String {
        val report = buildCrashReport(thread, throwable, isForeground)

        // 1. Ana dahili crash_log.txt
        try {
            File(context.filesDir, CRASH_LOG_FILE).writeText(report)
        } catch (_: Exception) {}

        // 2. Harici uygulama dizini (kullanıcının dosya yöneticisiyle / PC bağlantısıyla görebileceği yer)
        try {
            context.getExternalFilesDir(null)?.let { extDir ->
                File(extDir, CRASH_LOG_FILE).writeText(report)
            }
        } catch (_: Exception) {}

        // 3. Harici önbellek dizini
        try {
            context.externalCacheDir?.let { cacheDir ->
                File(cacheDir, CRASH_LOG_FILE).writeText(report)
            }
        } catch (_: Exception) {}

        // 4. Genel İndirilenler klasörüne doğrudan kaydetmeyi dene
        try {
            val pubDownload = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (pubDownload.exists() || pubDownload.mkdirs()) {
                File(pubDownload, "Kitsugi_Crash_Report.txt").writeText(report)
            }
        } catch (_: Exception) {}

        // 5. crash_history.txt (geçmiş tüm çökmeler — ekleme modunda)
        appendToHistory(context, report, isForeground)

        // 6. Bir sonraki açılışta kullanıcının karşısına anında uyarı ve paylaşım paneli çıkarmak için bayrak yaz
        try {
            context.getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_unread_crash", true)
                .putLong("last_crash_time", System.currentTimeMillis())
                .putString("last_crash_title", "${throwable.javaClass.simpleName}: ${throwable.message}")
                .commit()
        } catch (_: Exception) {}

        return report
    }

    /** Geçmiş çökme kaydını ekler; dosya çok büyürse ilk yarısını keser. */
    private fun appendToHistory(context: Context, report: String, isForeground: Boolean) {
        try {
            val historyFile = File(context.filesDir, CRASH_HISTORY_FILE)
            val tag = if (isForeground) "[FOREGROUND CRASH]" else "[BACKGROUND CRASH]"
            val entry = "\n$tag ${dateFmt.format(Date())}\n$report\n"

            // Boyut kontrolü — çok büyürse yarıya indir
            if (historyFile.exists() && historyFile.length() > MAX_HISTORY_BYTES) {
                val content = historyFile.readText()
                val trimmed = content.drop(content.length / 2)
                historyFile.writeText("=== Eski geçmiş kısaltıldı ===\n$trimmed")
            }

            historyFile.appendText(entry)
        } catch (_: Exception) {}
    }

    /**
     * Çökme anında logcat anlık görüntüsünü yakalar ve dosyaya yazar.
     * Kısa bir zaman aşımıyla çalıştırılır ki çökme aktivitesinin açılmasını geciktirmesin.
     */
    fun captureLogcatSync(context: Context) {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", MAX_LOGCAT_LINES.toString(), "-v", "time", "*:W")
            )
            val output = StringBuilder()
            output.appendLine("=== Çökme Anı Logcat (Son $MAX_LOGCAT_LINES Satır) — ${dateFmt.format(Date())} ===")
            
            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                        lines.forEach { output.appendLine(it) }
                    }
                } catch (_: Exception) {}
            }
            readerThread.start()
            readerThread.join(350)
            process.destroy()

            File(context.filesDir, LOGCAT_CRASH_FILE).writeText(output.toString())
        } catch (_: Exception) {}
    }

    // ── Yardımcı Okuyucular ───────────────────────────────────────────────────

    fun readCrashLog(context: Context): String {
        return try {
            val primary = File(context.filesDir, CRASH_LOG_FILE)
            if (primary.exists() && primary.length() > 0) return primary.readText()

            val ext = context.getExternalFilesDir(null)?.let { File(it, CRASH_LOG_FILE) }
            if (ext != null && ext.exists() && ext.length() > 0) return ext.readText()

            val cache = context.externalCacheDir?.let { File(it, CRASH_LOG_FILE) }
            if (cache != null && cache.exists() && cache.length() > 0) return cache.readText()

            "Henüz çökme kaydı bulunamadı."
        } catch (_: Exception) { "Crash log okunamadı." }
    }

    fun readCrashHistory(context: Context): String {
        return try {
            File(context.filesDir, CRASH_HISTORY_FILE).let {
                if (it.exists()) it.readText() else "Henüz geçmiş çökme kaydı yok."
            }
        } catch (_: Exception) { "Crash geçmişi okunamadı." }
    }

    fun crashHistoryExists(context: Context): Boolean =
        File(context.filesDir, CRASH_HISTORY_FILE).let { it.exists() && it.length() > 50 }

    fun crashHistorySizeKb(context: Context): Long =
        try { File(context.filesDir, CRASH_HISTORY_FILE).length() / 1024 } catch (_: Exception) { 0L }

    fun clearHistory(context: Context) {
        try { File(context.filesDir, CRASH_HISTORY_FILE).delete() } catch (_: Exception) {}
        try { File(context.filesDir, CRASH_LOG_FILE).delete() } catch (_: Exception) {}
        try { File(context.filesDir, LOGCAT_CRASH_FILE).delete() } catch (_: Exception) {}
        try { context.getExternalFilesDir(null)?.let { File(it, CRASH_LOG_FILE).delete() } } catch (_: Exception) {}
        try {
            context.getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("has_unread_crash", false).apply()
        } catch (_: Exception) {}
    }

    /**
     * Raporu kullanıcının doğrudan erişebileceği genel Downloads (İndirilenler) klasörüne yazar.
     */
    fun exportReportToDownloads(context: Context): File? {
        return try {
            val report = readCrashLog(context)
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val target = File(downloadDir, "Kitsugi_Crash_Report.txt")
            target.writeText(report)
            target
        } catch (_: Exception) { null }
    }

    fun hasUnreadCrash(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
            val unread = prefs.getBoolean("has_unread_crash", false)
            val time = prefs.getLong("last_crash_time", 0L)
            unread && (System.currentTimeMillis() - time < 24 * 60 * 60 * 1000L)
        } catch (_: Exception) { false }
    }

    fun markCrashAsRead(context: Context) {
        try {
            context.getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_unread_crash", false)
                .apply()
        } catch (_: Exception) {}
    }

    fun getLogcatCrashFile(context: Context): File = File(context.filesDir, LOGCAT_CRASH_FILE)
    fun getCrashLogFile(context: Context): File    = File(context.filesDir, CRASH_LOG_FILE)
    fun getCrashHistoryFile(context: Context): File = File(context.filesDir, CRASH_HISTORY_FILE)

    // ─── Launch zamanını Application sınıfından alır ──────────────────────────
    @Volatile
    var KitsugiApplication_LaunchTime: Long? = null
}

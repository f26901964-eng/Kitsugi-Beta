package com.kitsugi.animelist.data.cloudstream.diag

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * CS eklenti akışının KALICI, otomatik hata izleyicisi.
 *
 * - Her arama/eklenti olayı (yükleme, arama, eşleşme, link, embed, hata) otomatik kaydedilir.
 * - Kayıtlar `filesDir/cs_trace/trace.log` dosyasına yazılır; uygulama çökse bile kalır.
 * - Dosya ~1.5 MB'ı geçince `trace.1.log` olarak döner (en fazla 2 dosya tutulur).
 * - [writeReport] son olayları + plugin özetini içeren paylaşılabilir Markdown raporu üretir.
 *
 * Kullanım: `CsTrace.init(app)` (Application.onCreate), sonra `CsTrace.info/warn/error(...)`.
 * Rapor paylaşımı: Ayarlar > Plugin Tanı > "Canlı İzleme Raporunu Paylaş".
 */
object CsTrace {

    private const val TAG = "CsTrace"
    private const val DIR_NAME = "cs_trace"
    private const val LIVE_FILE = "trace.log"
    private const val OLD_FILE = "trace.1.log"
    private const val MAX_FILE_BYTES = 1_500_000L
    private const val MAX_REPORTS_KEPT = 5
    private const val MAX_INVENTORY_LINES = 400

    private val lock = Any()

    @Volatile
    private var baseDir: File? = null

    @Volatile
    private var inventory: List<String> = emptyList()

    /** Uygulama başlangıcında bir kez çağrılır. */
    fun init(context: Context) {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        dir.mkdirs()
        baseDir = dir
    }

    /** Rapora yazılacak "yüklü eklentiler" listesi (arama başlarken güncellenir). */
    fun setInventory(lines: List<String>) {
        inventory = lines.take(MAX_INVENTORY_LINES)
    }

    /** Yeni bir arama oturumunu işaretler (rapor okunurken ayırıcı olarak kullanılır). */
    fun session(label: String) {
        write(CsTraceLevel.INFO, "SESSION", "-", label, null)
    }

    fun info(plugin: String, stage: String, message: String) {
        write(CsTraceLevel.INFO, plugin, stage, message, null)
    }

    fun warn(plugin: String, stage: String, message: String, t: Throwable? = null) {
        write(CsTraceLevel.WARN, plugin, stage, message, t)
    }

    fun error(plugin: String, stage: String, message: String, t: Throwable? = null) {
        write(CsTraceLevel.ERROR, plugin, stage, message, t)
    }

    private fun write(
        level: CsTraceLevel,
        plugin: String,
        stage: String,
        message: String,
        t: Throwable?
    ) {
        val now = System.currentTimeMillis()
        val text = buildString {
            append(CsTraceCore.formatLine(now, level, plugin, stage, message)).append('\n')
            if (t != null) {
                CsTraceCore.formatThrowable(t).forEach { append(it).append('\n') }
            }
        }

        val logLine = "[$plugin] $stage: $message"
        when (level) {
            CsTraceLevel.ERROR -> Log.e(TAG, logLine, t)
            CsTraceLevel.WARN -> Log.w(TAG, logLine)
            CsTraceLevel.INFO -> Log.i(TAG, logLine)
        }

        val dir = baseDir ?: return
        synchronized(lock) {
            try {
                val live = File(dir, LIVE_FILE)
                if (live.exists() && live.length() > MAX_FILE_BYTES) {
                    val old = File(dir, OLD_FILE)
                    old.delete()
                    live.renameTo(old)
                }
                live.appendText(text, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "trace dosyasına yazılamadı: ${e.message}")
            }
        }
    }

    /**
     * Güncel rapor dosyasını üretir ve yolunu döndürür.
     * Çağıran iş parçacığını bloklayabilir; IO thread'inden çağırın.
     */
    fun writeReport(context: Context): File {
        val dir = (baseDir ?: File(context.applicationContext.filesDir, DIR_NAME).also { it.mkdirs() })
        val now = System.currentTimeMillis()

        val header = mutableListOf<Pair<String, String>>()
        header += "Oluşturma" to CsTraceCore.formatTimestamp(now)
        header += "Uygulama" to appVersion(context)
        header += "Cihaz" to "${Build.MANUFACTURER} ${Build.MODEL}"
        header += "Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

        val lines = mutableListOf<String>()
        synchronized(lock) {
            lines += readLines(File(dir, OLD_FILE))
            lines += readLines(File(dir, LIVE_FILE))
        }

        val report = CsTraceCore.buildReport(header, inventory, lines)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT).format(java.util.Date(now))
        val out = File(dir, "CS_Tani_Raporu_$stamp.md")
        out.writeText(report, Charsets.UTF_8)
        pruneOldReports(dir)
        return out
    }

    /** Bir önceki oturumun (çökme sonrası) izini de dahil etmek için satırları okur. */
    private fun readLines(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "trace okunamadı (${file.name}): ${e.message}")
            emptyList()
        }
    }

    private fun pruneOldReports(dir: File) {
        val reports = dir.listFiles { f -> f.name.startsWith("CS_Tani_Raporu_") && f.name.endsWith(".md") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        reports.drop(MAX_REPORTS_KEPT).forEach { runCatching { it.delete() } }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${context.packageName} ${info.versionName} (code ${info.versionCode})"
    } catch (e: Exception) {
        context.packageName
    }
}

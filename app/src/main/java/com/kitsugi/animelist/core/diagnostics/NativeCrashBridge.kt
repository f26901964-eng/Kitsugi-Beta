package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.os.Process
import android.util.Log

/**
 * Native (C/C++) çökme köprüsü.
 *
 * `libkitsugi_crash_handler.so` içindeki `constructor` fonksiyonu SIGSEGV/SIGABRT/SIGBUS/SIGILL/
 * SIGFPE gibi sinyalleri yakalayıp `filesDir/native_crash.txt` dosyasına ham geri iz (backtrace)
 * yazar. Java UncaughtExceptionHandler bu sinyalleri ASLA göremez — bu yüzden native çökmeler
 * daha önce tamamen sessiz kalıyordu.
 */
object NativeCrashBridge {

    private const val TAG = "KitsugiNativeCrash"
    private const val LIB_NAME = "kitsugi_crash_handler"
    private const val NATIVE_CRASH_FILE = "native_crash.txt"

    const val UNCLEAN_TRACE_MAX_AGE_MS: Long = 7L * 24L * 60L * 60L * 1000L

    data class NativeCrashMeta(
        val epochSec: Long?,
        val pid: Int?,
        val raw: String
    )

    @Volatile
    private var loaded = false

    @Volatile
    private var installAttempted = false

    fun install(context: Context) {
        if (installAttempted) return
        installAttempted = true
        try {
            System.loadLibrary(LIB_NAME)
            loaded = true
        } catch (t: Throwable) {
            Log.w(TAG, "Native kütüphane yüklenemedi ($LIB_NAME): ${t.message}")
            return
        }
        try {
            val dir = context.filesDir.absolutePath
            nativeInstall(dir)
            Log.i(TAG, "Native çökme yakalayıcı kuruldu → $dir/$NATIVE_CRASH_FILE")
        } catch (t: Throwable) {
            Log.w(TAG, "Native yakalayıcı kurulamadı: ${t.message}")
        }
    }

    fun isLoaded(): Boolean = loaded

    fun nativeCrashFile(context: Context): java.io.File =
        java.io.File(context.filesDir, NATIVE_CRASH_FILE)

    /**
     * `O_APPEND` ile birikmiş eski çoklu kayıtlarda yalnızca EN SON `=== KITSUGI NATIVE CRASH ===`
     * bloğunu ve ona ait `epoch` / `pid` kimliğini ayrıştırır.
     */
    internal fun parseLatestNativeCrash(raw: String): NativeCrashMeta? {
        if (raw.isBlank()) return null
        val marker = "=== KITSUGI NATIVE CRASH ==="
        val lastIdx = raw.lastIndexOf(marker)
        val latestSection = if (lastIdx >= 0) raw.substring(lastIdx) else raw
        var epochSec: Long? = null
        var pid: Int? = null
        for (line in latestSection.lineSequence().take(20)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("epoch=") && epochSec == null) {
                epochSec = trimmed.removePrefix("epoch=").toLongOrNull()
            } else if (trimmed.startsWith("pid=") && pid == null) {
                pid = trimmed.removePrefix("pid=").toIntOrNull()
            }
        }
        return NativeCrashMeta(epochSec = epochSec, pid = pid, raw = latestSection)
    }

    internal fun matchesSession(
        meta: NativeCrashMeta,
        expectedPid: Int?,
        sessionStartMs: Long = 0L,
        sessionEndMs: Long = 0L,
        maxAgeMs: Long = UNCLEAN_TRACE_MAX_AGE_MS,
        fileLastModifiedMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        // 1) PID kaydı varsa KESİN EŞLEŞME şarttır — başka bir oturumun (ör. günler önceki)
        //    SIGSEGV kaydı asla yeni bir Java çökmesine veya farklı bir PID'in raporuna eklenmez.
        if (meta.pid != null && expectedPid != null && expectedPid > 0) {
            return meta.pid == expectedPid
        }
        // 2) Epoch kaydı varsa oturum zaman penceresiyle doğrula.
        if (meta.epochSec != null) {
            val crashMs = meta.epochSec * 1_000L
            if (sessionStartMs > 0L) {
                val endBound = maxOf(sessionEndMs, sessionStartMs) + 120_000L
                return crashMs in (sessionStartMs - 15_000L)..endBound
            }
            return nowMs - crashMs <= maxAgeMs
        }
        // 3) Başlık yoksa yalnızca dosya yaşına bak.
        return fileLastModifiedMs > 0L && (nowMs - fileLastModifiedMs <= maxAgeMs)
    }

    fun readNativeCrash(context: Context): String? {
        return try {
            val f = nativeCrashFile(context)
            if (f.exists() && f.length() > 0) parseLatestNativeCrash(f.readText())?.raw else null
        } catch (_: Throwable) {
            null
        }
    }

    fun hasNativeCrash(context: Context): Boolean = readNativeCrash(context) != null

    /**
     * Yalnızca belirtilen oturuma / sürece ait taze native çökme izini döner.
     * Varsayılan olarak mevcut süreç (`Process.myPid()`) ile eşleştirir; böylece
     * önceki günlerden kalan bayat `native_crash.txt` dosyaları yeni Java çökmelerine sızmaz.
     */
    fun readRecentNativeCrash(
        context: Context,
        maxAgeMs: Long = 30L * 60 * 1000,
        expectedPid: Int? = Process.myPid(),
        sessionStartMs: Long = 0L,
        sessionEndMs: Long = 0L
    ): String? {
        return try {
            val f = nativeCrashFile(context)
            if (!f.exists() || f.length() <= 0) return null
            val meta = parseLatestNativeCrash(f.readText()) ?: return null
            val matched = matchesSession(
                meta = meta,
                expectedPid = expectedPid,
                sessionStartMs = sessionStartMs,
                sessionEndMs = sessionEndMs,
                maxAgeMs = maxAgeMs,
                fileLastModifiedMs = f.lastModified()
            )
            if (matched) meta.raw else null
        } catch (_: Throwable) {
            null
        }
    }

    fun clearNativeCrash(context: Context) {
        try { nativeCrashFile(context).delete() } catch (_: Throwable) {}
    }

    @JvmStatic
    private external fun nativeInstall(filesDir: String)
}

package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log

/**
 * Native (C/C++) çökme köprüsü.
 *
 * `libkitsugi_crash_handler.so` içindeki `constructor` fonksiyonu SIGSEGV/SIGABRT/SIGBUS/SIGILL/
 * SIGFPE gibi sinyalleri yakalayıp `filesDir/native_crash.txt` dosyasına ham geri iz (backtrace)
 * yazar. Java UncaughtExceptionHandler bu sinyalleri ASLA göremez — bu yüzden native çökmeler
 * daha önce tamamen sessiz kalıyordu.
 *
 * Kütüphane yüklenemezse (ör. desteklenmeyen ABI) sessizce devre dışı kalır; uygulama davranışı
 * değişmez.
 */
object NativeCrashBridge {

    private const val TAG = "KitsugiNativeCrash"
    private const val LIB_NAME = "kitsugi_crash_handler"

    private const val NATIVE_CRASH_FILE = "native_crash.txt"

    /**
     * Sessiz (temiz olmayan) kapanma raporlarında yerel iz bu süreye kadar eklenebilir (7 gün).
     * Kullanıcı uygulamayı çökmeden saatler sonra açarsa kanıt kaybolmasın diye 30 dk penceresi kullanılmaz.
     */
    const val UNCLEAN_TRACE_MAX_AGE_MS: Long = 7L * 24L * 60L * 60L * 1000L

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

    fun readNativeCrash(context: Context): String? {
        return try {
            val f = nativeCrashFile(context)
            if (f.exists() && f.length() > 0) f.readText() else null
        } catch (_: Throwable) {
            null
        }
    }

    fun hasNativeCrash(context: Context): Boolean = readNativeCrash(context) != null

    /**
     * Yalnızca YAKIN ZAMANDA oluşmuş native çökme izini döner (varsayılan 30 dk).
     * Böylece haftalar önceki bir native çökme, yeni bir Java çökmesinin raporuna
     * yanlışlıkla eklenmez.
     */
    fun readRecentNativeCrash(context: Context, maxAgeMs: Long = 30L * 60 * 1000): String? {
        return try {
            val f = nativeCrashFile(context)
            if (!f.exists() || f.length() <= 0) return null
            val age = System.currentTimeMillis() - f.lastModified()
            if (age > maxAgeMs) null else f.readText()
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

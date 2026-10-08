package com.kitsugi.animelist.core.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log

/**
 * KitsugiCrashHandler — uygulamanın TEK çökme giriş noktası.
 *
 * Tasarım kuralları (neden böyle?):
 *  1. **Hiçbir şeyi yutmaz, ama önce kanıtı yazar.** Önce minik "pending" işareti (bayrak dosyası +
 *     SharedPreferences) yazılır; rapor dosyaları sonra oluşturulur. Böylece rapor üretimi
 *     yarıda kalsa bile uygulama bir sonraki açılışta "çöktü" diyebilir.
 *  2. **Throwable yakalar** (Exception değil). StackOverflowError/OutOfMemoryError gibi hatalar
 *     `catch (e: Exception)` bloklarından kaçar ve raporun yazılmasını engelliyordu.
 *  3. **Ana iş parçacığını UYUTMAZ.** Eski kod `Thread.sleep(800)` ile ana thread'i bloke ediyordu;
 *     Android'in "activity'yi duraklat + yenisini başlat" el sıkışması tam bu sırada yapıldığı için
 *     çökme ekranı çoğu zaman hiç açılamıyordu. Artık çökme ekranı (ayrı süreç + ayrı görev) açılır
 *     ve eski süreç, arka plandaki bir bekçi thread tarafından (ya da doğrudan çökme ekranı
 *     tarafından) kapatılır.
 *  4. **Ana thread çökmesi asla bastırılmaz.**
 */
object KitsugiCrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "KitsugiCrash"

    @Volatile private var appContext: Context? = null
    @Volatile private var installed = false

    // ── Kurulum ────────────────────────────────────────────────────────────────

    /** Application.attachBaseContext() içinden çağrılır: süreçteki en erken güvenli nokta. */
    fun install(context: Context) {
        // DİKKAT: attachBaseContext() sırasında Context.getApplicationContext() henüz NULL
        // dönebilir (LoadedApk.mApplication henüz atanmamıştır). Bu yüzden asla
        // "applicationContext ?: return" YAPILMAZ — yoksa çökme yakalayıcı sessizce kurulmaz.
        val ctx: Context = context.applicationContext ?: context
        appContext = ctx
        if (!installed) {
            installed = true
            try {
                Thread.setDefaultUncaughtExceptionHandler(this)
                Log.i(TAG, "UncaughtExceptionHandler kuruldu.")
            } catch (t: Throwable) {
                Log.w(TAG, "Handler kurulamadı: ${t.message}")
            }
        }
        try {
            NativeCrashBridge.install(ctx)
        } catch (_: Throwable) {}
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        handle(thread, throwable)
    }

    // ── Ana yol ───────────────────────────────────────────────────────────────

    fun handle(thread: Thread, throwable: Throwable) {
        val ctx = appContext
        val isForegroundThread = thread.name == "main" || thread.name.startsWith("main")

        // 1) Coroutine iptalleri normal akıştır.
        if (isCancellation(throwable)) {
            Log.d(TAG, "Coroutine iptali normal karşılandı: ${throwable.message}")
            return
        }

        // 2) Arka plan thread'lerindeki ağ/pencere hataları kullanıcıyı etkilemez.
        if (!isForegroundThread && isNetworkOrWindow(throwable)) {
            Log.w(TAG, "Arka plan ağ/pencere hatası bastırıldı (${thread.name}): " +
                    "${throwable.javaClass.simpleName}: ${throwable.message}")
            return
        }

        Log.e(TAG, "FATAL ÇÖKME (${thread.name}, anaThread=$isForegroundThread): " +
                "${throwable.javaClass.simpleName}: ${throwable.message}", throwable)

        if (ctx == null) {
            killSelf(delayMs = 200)
            return
        }

        // ── 2a) EN ÖNCE SİNYALİ VER: bayrak dosyası + prefs (bir sonraki açılışta kesin görünür)
        try {
            KitsugiCrashLogger.markCrashPending(ctx, thread, throwable)
        } catch (_: Throwable) {}

        // ── 2b) Eylem izini diske yaz (rapora gömülecek)
        try { KitsugiSessionSupervisor.flushNow() } catch (_: Throwable) {}

        // ── 2c) Native çökme izi var mı? (varsa rapora iliştir)
        try { NativeCrashBridge.readNativeCrash(ctx) } catch (_: Throwable) {}

        // ── 3) Raporu yaz — hata zincirini de değil, HİÇBİR ŞEYİ kaybetmeden
        val report: String = try {
            KitsugiCrashLogger.writeCrashReport(
                context = ctx,
                thread = thread,
                throwable = throwable,
                isForeground = isForegroundThread
            )
        } catch (t: Throwable) {
            // Rapor üretimi başarısız olduysa en azından özeti elle kur
            buildString {
                appendLine("=== KİTSUGİ ÇÖKME (basit rapor) ===")
                appendLine("Thread : ${thread.name} (anaThread=$isForegroundThread)")
                appendLine("Hata   : ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("Rapor  : ayrıntılı rapor üretilemedi (${t.javaClass.simpleName}: ${t.message})")
                appendLine(android.util.Log.getStackTraceString(throwable))
            }
        }

        // ── 4) Logcat anlık görüntüsü (ağır iş — kanıt yazıldıktan SONRA)
        try { KitsugiCrashLogger.captureLogcatSync(ctx) } catch (_: Throwable) {}

        // ── 5) Çökme ekranını BAŞKA bir süreçte ve BAŞKA bir görevde aç
        val started = try {
            startCrashUi(ctx, report)
        } catch (t: Throwable) {
            Log.e(TAG, "Çökme ekranı başlatılamadı: ${t.message}")
            false
        }

        // ── 6) Bekçi: bu süreç ölü bir ana thread ile asılı kalmasın.
        //      Çökme ekranı ayağa kalkar kalkmaz bizi kendisi kapatır (crashed_pid).
        killSelf(delayMs = if (started) 4_000L else 600L)
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private fun startCrashUi(context: Context, report: String): Boolean {
        val intent = Intent(context, com.kitsugi.animelist.ui.screens.crash.KitsugiCrashActivity::class.java).apply {
            putExtra("has_crash", true)
            putExtra("crash_summary", report.take(64_000))
            putExtra("crashed_pid", Process.myPid())
            // Sadece NEW_TASK: çökme ekranının kendi taskAffinity'si olduğu için
            // kendi görevinde açılır ve ölmekte olan uygulama görevinin temizliğinden etkilenmez.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * Bu süreci arka planda kapatır. Ana thread'i ASLA bloklamaz; aksi hâlde Android'in
     * pencere/aktivite devir teslim mesajları işlenemez ve çökme ekranı açılmaz.
     */
    private fun killSelf(delayMs: Long) {
        Thread({
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {}
            try {
                Process.killProcess(Process.myPid())
            } catch (_: Throwable) {}
            try {
                System.exit(10)
            } catch (_: Throwable) {}
        }, "KitsugiCrashWatchdog").apply {
            isDaemon = false
            start()
        }
    }

    private fun isCancellation(throwable: Throwable): Boolean {
        var cur: Throwable? = throwable
        var depth = 0
        while (cur != null && depth < 12) {
            if (cur is kotlinx.coroutines.CancellationException) return true
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun isNetworkOrWindow(throwable: Throwable): Boolean {
        var cur: Throwable? = throwable
        var depth = 0
        while (cur != null && depth < 12) {
            when (cur) {
                is java.io.IOException,
                is java.net.SocketException,
                is java.net.UnknownHostException,
                is javax.net.ssl.SSLException,
                is java.net.ProtocolException,
                is android.view.WindowManager.BadTokenException -> return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }
}

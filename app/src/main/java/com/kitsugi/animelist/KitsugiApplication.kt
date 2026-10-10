package com.kitsugi.animelist

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.repository.AddonStreamRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KitsugiApplication : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val APP_LAUNCH_TIME = System.currentTimeMillis()

        @Volatile
        var activeActivity: android.app.Activity? = null

        @Volatile
        private var instance: KitsugiApplication? = null

        fun getInstance(): KitsugiApplication? = instance

        private var dynamicContextWrapper: KitsugiDynamicContextWrapper? = null

        fun getDynamicContext(context: Context): Context {
            var wrapper = dynamicContextWrapper
            if (wrapper == null) {
                wrapper = KitsugiDynamicContextWrapper(context.applicationContext)
                dynamicContextWrapper = wrapper
            }
            return wrapper
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // ── SÜREÇTEKİ EN ERKEN NOKTA ───────────────────────────────────────────────
        // Çökme yakalayıcı burada kurulur; çünkü ContentProvider'lar (Hilt, WorkManager,
        // tachiyomi/cloudstream init) Application.onCreate'ten ÖNCE çalışır ve orada oluşan
        // çökmeler daha önce hiçbir rapora girmiyordu.
        try { com.kitsugi.animelist.core.diagnostics.KitsugiCrashHandler.install(base) } catch (_: Throwable) {}
        try { com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.install(base) } catch (_: Throwable) {}
    }

    override fun onCreate() {
        // ── 0. :crash Süreci Koruması ────────────────────────────────────────────────
        // Eğer bu süreç çökme ekranı (:crash) için başlatılmışsa, arka plan işçilerini,
        // WorkManager'ı ve Room veritabanı senkronizasyonunu çalıştırma!
        val isCrashProcess = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Application.getProcessName().endsWith(":crash")
            } else {
                val pid = android.os.Process.myPid()
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName?.endsWith(":crash") == true
            }
        }.getOrDefault(false)

        if (isCrashProcess) {
            instance = this
            super.onCreate()
            Thread.setDefaultUncaughtExceptionHandler { crashThread, crashThrowable ->
                android.util.Log.e("KitsugiCrashProcess", "Crash in :crash process!", crashThrowable)
                // Çökme ekranının KENDİSİ çöktü: kanıtı yaz, bayrağı kaldır ve süreci kapat.
                // (Eski kod süreci canlı bırakıyordu → kullanıcı boş/siyah ekranda kilitleniyordu.)
                try {
                    java.io.File(filesDir, "crash_in_crash.txt").writeText(
                        "Çökme ekranı süreci çöktü (${crashThread.name}):\n" +
                            android.util.Log.getStackTraceString(crashThrowable)
                    )
                } catch (_: Throwable) {}
                try {
                    getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unread_crash", true)
                        .putLong("last_crash_time", System.currentTimeMillis())
                        .putString("last_crash_title", "Çökme ekranı hatası: ${crashThrowable.javaClass.simpleName}")
                        .commit()
                } catch (_: Throwable) {}
                Thread({
                    try { Thread.sleep(400) } catch (_: InterruptedException) {}
                    try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
                }, "KitsugiCrashProcessWatchdog").start()
            }
            return
        }

        instance = this
        super.onCreate()

        // Only the main process may register vault listeners and persistent WorkManager jobs.
        runCatching { com.kitsugi.animelist.data.account.LinkedAccountVault.startAutoBackup(this) }

        // ── Bellek koruması ───────────────────────────────────────────────────────────
        // Gezindikçe şişen bellek önbellekleri, heap tavanına yaklaşınca sistem uyarısını
        // beklemeden 15 sn'de bir kontrol edilip küçültülür (bkz. KitsugiMemoryGuard).
        startMemoryWatchdog()

        // Initialize AnimeDownloadManager
        com.kitsugi.animelist.data.local.AnimeDownloadManager.init(this)

        // Initialize WatchHistoryManager
        com.kitsugi.animelist.data.local.WatchHistoryManager.init(this)

        // Initialize custom FileLoggingTree
        com.kitsugi.animelist.core.diagnostics.FileLoggingTree.init(this)

        // CS eklenti canlı izleyicisi (kalıcı trace + paylaşılabilir rapor)
        try { com.kitsugi.animelist.data.cloudstream.diag.CsTrace.init(this) } catch (_: Throwable) {}

        // KitsugiCrashLogger'a başlatma zamanını bildir
        com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger.KitsugiApplication_LaunchTime = APP_LAUNCH_TIME

        // ── Çökme yakalayıcı ─────────────────────────────────────────────────────────
        // attachBaseContext() içinde ZATEN kuruldu (provider/erken init çökmelerini de
        // yakalayabilmek için). Burada yalnızca garantiye alıyoruz.
        com.kitsugi.animelist.core.diagnostics.KitsugiCrashHandler.install(this)

        // ── Ana UI iş parçacığı koruması (Cockroach Looper) ───────────────────────────
        // Bu koruma artık SESSİZCE YUTMUYOR:
        //   • yalnızca çağrı zincirinin İLK kareleri eklenti/cloudstream içindeyse kurtarılır
        //     (eskiden stack trace'in HERHANGİ bir yerinde "com.lagradost.cloudstream3"
        //      geçmesi yeterliydi → uygulamanın kendi gerçek çökmeleri de yutuluyordu),
        //   • kurtarılan her hata çökme raporuna + eylem izine yazılır,
        //   • oturumda en fazla 5 kurtarma yapılır (bozuk/zombi arayüz birikmesin),
        //   • Looper.loop() geri dönerse (kuyruk kapanıyor) döngüden ÇIKILIR — eski kod
        //     burada sonsuz CPU döngüsüne girip ANR üretiyordu ("donup pat diye kapanma").
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            var rescued = 0
            while (rescued < 5) {
                try {
                    android.os.Looper.loop()
                    // Kuyruk kapandı/dispose edildi → ActivityThread'in kendi döngüsüne dön.
                    return@post
                } catch (t: Throwable) {
                    val trace = android.util.Log.getStackTraceString(t)
                    val topFrames = trace.lineSequence().take(14).joinToString("\n")
                    val isPluginFrame = topFrames.contains("com.lagradost.cloudstream3") ||
                        topFrames.contains("CsStreamRunner") ||
                        topFrames.contains("CsPluginLoader") ||
                        topFrames.contains("com.kitsugi.animelist.data.cloudstream")
                    val isWindowError = t is android.view.WindowManager.BadTokenException ||
                        t is kotlinx.coroutines.CancellationException ||
                        trace.contains("not attached to window manager") ||
                        trace.contains("has already been added")

                    if (isPluginFrame || isWindowError) {
                        rescued++
                        android.util.Log.e("KitsugiApplication",
                            "Cockroach ($rescued/5): Kurtarılabilir hata bastırıldı — ${t.javaClass.simpleName}: ${t.message}", t)
                        try {
                            com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger
                                .writeCrashReport(this@KitsugiApplication, Thread.currentThread(), t, isForeground = false)
                        } catch (_: Throwable) {}
                        try {
                            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                                .noteAction("KURTARILDI(${rescued}): ${t.javaClass.simpleName} @ ${KitsugiApplication.activeActivity?.javaClass?.simpleName ?: "?"}")
                        } catch (_: Throwable) {}
                    } else {
                        // Gerçek uygulama hatası — UncaughtExceptionHandler'a ilet.
                        throw t
                    }
                }
            }
            android.util.Log.e("KitsugiApplication",
                "Cockroach: kurtarma limiti (5) doldu — sonraki hatalar normal şekilde raporlanacak.")
        }

        // Start background logcat redirection to app_logs.txt only in debug mode to save IO performance
        if (BuildConfig.DEBUG) {
            try {
                val logFile = java.io.File(filesDir, "app_logs.txt")
                // Run logcat redirection: max 2MB per file, keep 2 rotated backups
                Runtime.getRuntime().exec(arrayOf(
                    "logcat",
                    "-f", logFile.absolutePath,
                    "-r", "2048",
                    "-n", "2",
                    "-v", "time",
                    "*:D"
                ))
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Failed to start logcat file redirection: ${e.message}")
            }
        }

        // Initialize ratings repository room cache context
        KitsugiEpisodeRatingsRepository.init(this)

        // Initialize P2P Torrent Engine & Settings
        com.kitsugi.animelist.core.p2p.P2pSettingsRepository.initialize(this)
        com.kitsugi.animelist.core.p2p.P2pStreamingEngine.initialize(this)

        // T4-11: Initialize DNS Over HTTPS resolver without blocking main thread.
        // DnsManager starts with default=0 immediately; the real persisted value is applied
        // asynchronously once the DataStore flow emits its first value.
        com.kitsugi.animelist.core.network.DnsManager.init(this, 0)
        applicationScope.launch {
            try {
                val dataStore = com.kitsugi.animelist.data.settings.SettingsDataStore(this@KitsugiApplication)
                val settings = dataStore.settingsFlow.first()

                // CsStreamRunner adult content filter init
                com.kitsugi.animelist.data.cloudstream.CsStreamRunner.setShowAdultContent(settings.showAdultContent)

                // Uzak domain listesini uygulama açılışında önceden çek —
                // domain_fixes.json GitHub'dan okunur, eklentiler arama başlamadan doğru domaine sahip olur
                com.kitsugi.animelist.data.cloudstream.CsStreamRunner.triggerRemoteDomainsFetch()

                // DNS init
                if (settings.dnsChoice != 0) {
                    com.kitsugi.animelist.core.network.DnsManager.init(this@KitsugiApplication, settings.dnsChoice)
                }

                // TMDB önbelleği — runBlocking kullanımını ortadan kaldırır
                com.kitsugi.animelist.data.remote.TmdbApiClient.updateCache(
                    apiKey  = settings.tmdbUserApiKey,
                    language = settings.tmdbLanguage
                )
            } catch (e: Exception) {
                android.util.Log.w("KitsugiApplication", "Init async failed: ${e.message}")
            }
        }

        // T3.3: WorkManager tabanlı bildirim planlaması
        applicationScope.launch {
            try {
                val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(this@KitsugiApplication).settingsFlow.first()
                if (settings.airingNotificationsEnabled || settings.aniListNotificationsEnabled || settings.malNotificationsEnabled || settings.simklNotificationsEnabled) {
                    com.kitsugi.animelist.core.notifications.NotificationScheduler.schedule(this@KitsugiApplication, settings.notificationInterval)
                } else {
                    com.kitsugi.animelist.core.notifications.NotificationScheduler.cancel(this@KitsugiApplication)
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Failed to schedule notifications: ${e.message}")
            }
        }

        // Schedule offline mapping database sync periodically (every 14 days)
        applicationScope.launch {
            try {
                val db = KitsugiDatabase.getDatabase(this@KitsugiApplication)
                val count = db.mediaMetaCacheDao().getCount()
                
                // One-time sync on first run if database is empty
                if (count == 0) {
                    val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.kitsugi.animelist.data.local.MappingSyncWorker>()
                        .build()
                    androidx.work.WorkManager.getInstance(this@KitsugiApplication).enqueueUniqueWork(
                        "mapping_sync_one_time",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        oneTimeRequest
                    )
                }

                // Periodic sync
                val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.kitsugi.animelist.data.local.MappingSyncWorker>(
                    14, java.util.concurrent.TimeUnit.DAYS
                )
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED) // Wi-Fi only
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(this@KitsugiApplication).enqueueUniquePeriodicWork(
                    "mapping_sync_work",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Failed to schedule mapping sync: ${e.message}")
            }
        }

        // ── Otomatik Arka Plan Liste Yenileme ──────────────────────────────────────
        // AniHyou / MoeList gibi: uygulama açıldığında bağlı platformların listelerini
        // sessizce arka planda yeniler. Son yenilemeden 24 saat geçmemişse atlar.
        applicationScope.launch {
            try {
                val prefs = getSharedPreferences("kitsugi_auto_sync", Context.MODE_PRIVATE)
                val lastSync = prefs.getLong("last_list_sync_ms", 0L)
                val now = System.currentTimeMillis()
                val twentyFourHours = 24 * 60 * 60 * 1000L

                if (now - lastSync < twentyFourHours) {
                    android.util.Log.d("KitsugiAutoSync", "Son yenilemeden 24 saat geçmedi, atlanıyor.")
                } else {
                    val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(this@KitsugiApplication)
                    val dao = db.mediaEntryDao()
                    val repo = com.kitsugi.animelist.data.local.MediaEntryRepository(dao = dao, context = this@KitsugiApplication)

                    var syncCount = 0

                    // AniList
                    val aniListToken = com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(this@KitsugiApplication)
                    if (!aniListToken.isNullOrBlank()) {
                        try {
                            android.util.Log.d("KitsugiAutoSync", "AniList listesi yenileniyor...")
                            val entries = com.kitsugi.animelist.data.auth.AniListImportManager.fetchAllLists(aniListToken)
                            // +18 işaretleri sil-yaz döngüsünde kaybolmasın
                            repo.replaceSourcePreservingAdultFlags("anilist", entries)
                            syncCount += entries.size
                            android.util.Log.d("KitsugiAutoSync", "AniList: ${entries.size} kayıt yenilendi.")
                        } catch (e: Exception) {
                            android.util.Log.w("KitsugiAutoSync", "AniList yenileme hatası: ${e.message}")
                        }
                    }

                    // MyAnimeList
                    val malToken = com.kitsugi.animelist.data.auth.ExternalAuthManager.getOrRefreshMalToken(this@KitsugiApplication)
                    if (!malToken.isNullOrBlank()) {
                        try {
                            android.util.Log.d("KitsugiAutoSync", "MAL listesi yenileniyor...")
                            val dataStore = com.kitsugi.animelist.data.settings.SettingsDataStore(this@KitsugiApplication)
                            val showAdult = dataStore.settingsFlow.first().showAdultContent
                            val entries = com.kitsugi.animelist.data.auth.MalImportManager.fetchAllLists(malToken, showAdult)
                            repo.replaceSourcePreservingAdultFlags("mal", entries)
                            syncCount += entries.size
                            android.util.Log.d("KitsugiAutoSync", "MAL: ${entries.size} kayıt yenilendi.")
                        } catch (e: Exception) {
                            android.util.Log.w("KitsugiAutoSync", "MAL yenileme hatası: ${e.message}")
                        }
                    }

                    // Simkl
                    val simklToken = com.kitsugi.animelist.data.auth.ExternalAuthManager.getSimklToken(this@KitsugiApplication)
                    if (!simklToken.isNullOrBlank()) {
                        try {
                            android.util.Log.d("KitsugiAutoSync", "Simkl listesi yenileniyor...")
                            val entries = com.kitsugi.animelist.data.auth.SimklImportManager.fetchAllLists(simklToken)
                            // Simkl liste API'si `adult` taşımaz → eski +18 işaretleri
                            // korunmalı ve hemen ardından kimlik üzerinden tamamlanmalı.
                            repo.replaceSourcePreservingAdultFlags("simkl", entries)
                            syncCount += entries.size
                            android.util.Log.d("KitsugiAutoSync", "Simkl: ${entries.size} kayıt yenilendi.")
                        } catch (e: Exception) {
                            android.util.Log.w("KitsugiAutoSync", "Simkl yenileme hatası: ${e.message}")
                        }
                    }

                    if (syncCount > 0) {
                        prefs.edit().putLong("last_list_sync_ms", now).apply()
                        android.util.Log.i("KitsugiAutoSync", "Otomatik liste yenileme tamamlandı: toplam $syncCount kayıt.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiAutoSync", "Otomatik sync hatası: ${e.message}")
            }
        }
        // ─────────────────────────────────────────────────────────────────────────────

        // Initialize Cloudstream runtime singleton context and client
        com.kitsugi.animelist.data.cloudstream.CsRuntimeInit.init(this)

        // Do not pre-create a batch of hidden WebViews during application startup.
        // CloudflareKiller resolves a challenge lazily for the exact host that needs it.
        // The previous startup warmup could create up to 14 Chromium renderers in sequence,
        // adding substantial native memory and RenderThread pressure without user interaction.

        try {
            uy.kohesive.injekt.Injekt.addSingleton(eu.kanade.tachiyomi.network.NetworkHelper::class.java, eu.kanade.tachiyomi.network.NetworkHelper(this))
            uy.kohesive.injekt.Injekt.addSingleton(kotlinx.serialization.json.Json::class.java, kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
            android.util.Log.d("KitsugiApplication", "NetworkHelper and Json registered in Injekt.")
        } catch (e: Exception) {
            android.util.Log.w("KitsugiApplication", "Injekt registration skipped: ${e.message}")
        }

        // Track active activity to provide correct Window Token context for plugin dialogs (e.g. Captchas)
        // Handler to delay-null activeActivity (gives plugin dialogs time to close)
        val activityNullHandler = android.os.Handler(android.os.Looper.getMainLooper())
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {
                // Cancel any pending null — new activity is becoming visible
                activityNullHandler.removeCallbacksAndMessages(null)
                com.lagradost.api.setContext(java.lang.ref.WeakReference(getDynamicContext(activity)))
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                activityNullHandler.removeCallbacksAndMessages(null)
                activeActivity = activity
                com.lagradost.api.setContext(java.lang.ref.WeakReference(getDynamicContext(activity)))
                // Sessiz ölüm dedektörü: ön plan/arka plan durumunu kaydet
                try {
                    com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.noteForeground(true)
                } catch (_: Throwable) {}
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                // DO NOT null activeActivity here! A paused activity still has a valid window
                // token. Nulling here causes BotKontrol (and any plugin dialog) to crash with
                // BadTokenException: "Unable to add window -- token null is not valid".
                // We keep the reference alive so plugin dialogs (captcha, etc.) can still show.
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                try {
                    com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.noteForeground(false)
                } catch (_: Throwable) {}
                // Delay null by 2s — gives async plugin captcha dialogs time to finish
                if (activeActivity === activity) {
                    activityNullHandler.postDelayed({
                        if (activeActivity === activity) {
                            activeActivity = null
                        }
                    }, 2000L)
                }
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                activityNullHandler.removeCallbacksAndMessages(null)
                if (activeActivity === activity) {
                    activeActivity = null
                }
            }
        })

        // Seed default Stremio addons if database is empty
        applicationScope.launch {
            AddonStreamRepository(this@KitsugiApplication).seedPresetsIfEmpty()
        }

        // Pre-load all enabled Cloudstream plugins sequentially on startup immediately on IO thread.
        applicationScope.launch(Dispatchers.IO) {
            try {
                val db = KitsugiDatabase.getDatabase(this@KitsugiApplication)
                val enabledPlugins = db.csPluginDao().getEnabledPlugins()
                android.util.Log.d("KitsugiApplication", "Pre-loading ${enabledPlugins.size} enabled CS plugin(s) sequentially...")
                for (plugin in enabledPlugins) {
                    try {
                        CsPluginLoader.loadExtension(this@KitsugiApplication, plugin.id)
                        android.util.Log.d("KitsugiApplication", "Pre-loaded plugin: ${plugin.id}")
                    } catch (e: Exception) {
                        android.util.Log.w("KitsugiApplication", "Pre-load failed for ${plugin.id}: ${e.message}")
                    }
                    kotlinx.coroutines.delay(50L)
                }
                android.util.Log.d("KitsugiApplication", "Plugin pre-loading complete.")
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Plugin pre-loading error: ${e.message}", e)
            }
        }

        // Pre-load manga extensions on startup immediately on IO thread
        applicationScope.launch(Dispatchers.IO) {
            try {
                com.kitsugi.animelist.data.manga.MangaExtensionLoader.loadAllExtensions(this@KitsugiApplication)
                android.util.Log.d("KitsugiApplication", "Manga eklentileri tarama tamamlandı.")

                // Kotatsu-Redo: tüm 1300+ built-in kaynağı yükle (dil filtresi: TR varsayılan)
                try {
                    com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.initialize(this@KitsugiApplication)
                    android.util.Log.i("KitsugiApplication",
                        "Kotatsu init: ${com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getSourceCount()} kaynak yüklendi.")
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiApplication", "Kotatsu init hatası: ${e.message}", e)
                }

                // Dinamik manga domain kataloğunu internetten çek ve yerel ayarları güncelle
                try {
                    com.kitsugi.animelist.data.manga.MangaCatalogManager.syncCatalog(this@KitsugiApplication)
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiApplication", "Manga katalog güncelleme hatası: ${e.message}", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Manga eklenti tarama hatası: ${e.message}", e)
            }

            // Eklentiler yüklendikten 5 saniye sonra Keiyoushi repo'sundan otomatik
            // güncelleme kontrolü yap (günde en fazla 1 kez çalışır).
            kotlinx.coroutines.delay(5_000L)
            try {
                val result = com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.runIfNeeded(
                    context = this@KitsugiApplication,
                    forceCheck = false
                )
                when (result) {
                    is com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.UpdateResult.Success ->
                        android.util.Log.i("KitsugiApplication",
                            "Manga oto-güncelleme: ${result.updated} güncellendi, ${result.checked} kontrol edildi.")
                    is com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.UpdateResult.Skipped ->
                        android.util.Log.d("KitsugiApplication", "Manga oto-güncelleme atlandı: ${result.reason}")
                    is com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.UpdateResult.Failed ->
                        android.util.Log.w("KitsugiApplication", "Manga oto-güncelleme başarısız: ${result.reason}")
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Manga oto-güncelleme hatası: ${e.message}", e)
            }

            // Kotatsu-Redo kaynaklarını arka planda sessizce güncelle (12 saatte bir)
            kotlinx.coroutines.delay(4_000L)
            try {
                val kotatsuCount = com.kitsugi.animelist.data.manga.MangaCatalogManager.syncKotatsuSources(
                    context = this@KitsugiApplication,
                    forceCheck = false
                )
                android.util.Log.i("KitsugiApplication", "Kotatsu-Redo kaynakları güncellendi: $kotatsuCount kaynak yüklendi.")
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Kotatsu sync hatası: ${e.message}", e)
            }
        }

        // Load locale synchronously so it's available before Activity.attachBaseContext.
        val tag = getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        LocaleCache.localeTag = tag ?: ""
    }

    private fun startMemoryWatchdog() {
        applicationScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                try {
                    com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.watchdogTick()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Android bellek azaldığında bu geri çağrıyla uyarır. Eskiden dinlenmiyordu; uyarıya cevap
     * vermeyen süreç LMKD tarafından hiçbir rapor bırakmadan öldürülüyordu.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try { com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.onTrimMemory(level) } catch (_: Throwable) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        try { com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.onLowMemory() } catch (_: Throwable) {}
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return buildImageLoader(context).also { loader ->
            // Coil görsel önbelleği de bellek baskısında küçültülür / boşaltılır.
            com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.registerClearer("coil.memory") { fraction ->
                val cache = loader.memoryCache ?: return@registerClearer
                if (fraction <= 0f) cache.clear()
                else cache.trimToSize((cache.size * fraction).toLong())
            }
        }
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                }
                add(OkHttpNetworkFetcherFactory(callFactory = { com.kitsugi.animelist.core.network.KitsugiHttpClient.client }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("nuvio_images").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(150)
            .allowHardware(true)
            .allowRgb565(true)           // manga için %50 bellek tasarrufu
            .precision(Precision.INEXACT)
            .build()
    }
}

class KitsugiDynamicContextWrapper(appContext: Context) : android.content.ContextWrapper(appContext) {
    override fun getPackageName(): String {
        return "com.lagradost.cloudstream3"
    }

    override fun getSystemService(name: String): Any? {
        // Delegate to live Activity's service where possible so dialogs get valid window tokens.
        // NO proxy wrapping — Android internals cast WindowManager to WindowManagerImpl (concrete
        // class) inside Window.setWindowManager(), an interface proxy causes ClassCastException.
        val activity = KitsugiApplication.activeActivity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                val service = activity.getSystemService(name)
                if (service != null) return service
            } catch (_: Exception) {}
        }
        return super.getSystemService(name)
    }

    override fun getTheme(): android.content.res.Resources.Theme {
        return KitsugiApplication.activeActivity?.theme ?: super.getTheme()
    }

    override fun getResources(): android.content.res.Resources {
        return KitsugiApplication.activeActivity?.resources ?: super.getResources()
    }

    override fun getAssets(): android.content.res.AssetManager {
        return KitsugiApplication.activeActivity?.assets ?: super.getAssets()
    }
}

object LocaleCache {
    const val UNSET = "__UNSET__"

    @Volatile
    var localeTag: String = UNSET

    fun updateLocale(context: Context, tag: String) {
        val prefsTag = if (tag == "system") "" else tag
        context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .edit()
            .putString("locale_tag", prefsTag)
            .apply()
        localeTag = prefsTag

        // Apply immediately to resources
        val locale = if (tag == "system") java.util.Locale.getDefault() else java.util.Locale.forLanguageTag(tag)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        // Find activity and recreate
        findActivity(context)?.recreate()
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }
}


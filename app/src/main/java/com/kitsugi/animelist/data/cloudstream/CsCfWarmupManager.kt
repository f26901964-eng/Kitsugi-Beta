package com.kitsugi.animelist.data.cloudstream

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.network.CloudflareKiller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Türk Cloudstream eklentilerinin kullandığı CF/WAF korumalı siteleri
 * uygulama başladığında arka planda proaktif olarak ziyaret eder ve
 * CloudflareKiller.savedCookies'e cookie yükler.
 *
 * Bu sayede kullanıcı ilk aramayı yaptığında cookie hazır olur,
 * arama başarısız olmaz veya hızlı tamamlanır.
 *
 * Çalışma şekli:
 *   - Her site için gizli bir WebView açılır
 *   - CF challenge sayfası çözülür (cf_clearance veya WAF session cookie beklenir)
 *   - Cookie CloudflareKiller.savedCookies'e kaydedilir
 *   - WebView kapatılır, bellek serbest bırakılır
 */
object CsCfWarmupManager {

    private const val TAG = "CsCfWarmup"

    /**
     * Yüksek öncelikli siteler — animasyon/film izleme için kritik.
     * En sık kullanılan Türk siteleri buraya eklenir.
     */
    private val WARMUP_SITES = listOf(
        // ── Tanı 2026-08: Doğrulanmış aktif siteler (stream sayısına göre öncelik) ─────────
        // ✅ Üst düzey — stream çıkan aktif sağlayıcılar
        "https://www.filmmodu.one",        // FilmModu — 15 stream (3 repoda) ★★★
        "https://rarefilmm.com",           // RareFilmm — 14 stream (3 repoda) ★★★
        "https://movies2watch.watch",      // Watch2Movies — 5 stream ★★
        "https://www.fullhdfilmizlesene.mx",// FullHDFilmizlesene — 6 stream ★★
        "https://www.diziyou.one",         // DiziYou — 9 stream (3 repoda) ★★
        "https://www.dizimom.rest",        // DiziMom — 3 stream (3 repoda) ★★
        "https://www.hdfilmcehennemi.nl",  // HDFilmCehennemi — 3 stream ★★
        "https://www.sinema.gg",           // SinemaCX — 3 stream (3 repoda) ★★
        "https://diziboxen.help",          // InatBox API — Aktif backend domaini
        "https://kultfilmler.net",         // KultFilmler — 2 stream ★
        "https://www.ddizi.im",            // DDizi/Ddizi — 2 stream ★
        "https://cizgimax.online",         // CizgiMax — 1 stream ★
        "https://animecix.tv",             // AnimeciX — 2 stream (nikyokki) ★
        "https://asyawatch.com",           // AsyaWatch — 1 stream ★
        "https://a.prectv70.lol",          // RecTV — 1 stream (a. subdomain!) ★
        "https://ydfvfdizipanel.ru",       // SineWix/Sinewix — 2 stream ★
        // ✅ Stream çıkan aktif sağlayıcılar (2026-08 tanı doğrulaması)
        "https://dizikorea3.com",          // DiziKorea — feroxx 3 stream ✅ DOĞRULANDI (2026-08)
        // ✅ Arama var ama stream çözülemedi (warmup yine de faydalı)
        "https://filmmakinesi.to",         // FilmMakinesi — 24 arama sonucu
        "https://www.turkanime.tv",        // TurkAnime — 28 arama sonucu
        "https://asyaanimeleri.top",       // AsyaAnimeleri — 3 arama sonucu
        "https://asyalog.co",             // TRasyalog — 1 arama sonucu (CF korumalı)
        // ✅ CF/WAF korumalı — warmup kritik
        "https://tranimaci.com",           // TrAnimeci — WAF PoW, warmup zorunlu
        "https://dizipal1565.com",         // DiziPal — CF korumalı
        "https://www.dizibox.live",        // DiziBox — CF korumalı, ara ara çalışıyor
        "https://www.molystream.org",      // DiziBox embed CDN — loadLinks 25s timeout sebebi
        "https://dizillahd.com",           // Dizilla — CF korumalı
        // ─── 2026-08 Plugin kapsam analizi — yeni eklenen siteler ────────────
        "https://animeworld.ac",           // AnimeWorld — aktif, CF warmup faydalı
        "https://belgeselx.com",           // BelgeselX — aktif Türk belgesel sitesi
        "https://cinemacity.rip",          // CinemaCity — cinemacity.cc→.rip domain fix sonrası aktif
        "https://cizgimax.online",         // CizgiMax — aktif domain, CF korumalı
        // ─── 2026-08 Stream problemi olan aktif siteler — warmup kritik ─────
        "https://sezonlukdizi.cc",         // SezonlukDizi — HTTP 200, embed CF korumalı
        "https://jetfilmizle.now",         // JetFilmizle — HTTP 200, CF korumalı
        "https://www.setfilmizle.uk",      // SetFilmIzle — HTTP 200, CF korumalı
        "https://vctplay.site",            // SetFilmIzle embed — aktif player (setplay.shop yerine)
        "https://dizimag.lol",             // DiziMag — HTTP 200, CF korumalı
        "https://tvdiziler.cc",            // TvDiziler — HTTP 200, CF korumalı
        "https://dizi73.life",             // DiziLife — HTTP 200, CF korumalı
        "https://dizigom101.com"           // DiziGom — TIMEOUT (CF Turnstile), warmup zorunlu
    )

    /** Son warmup zamanı — çok sık çalışmasını engeller (en az 30 dk arayla) */
    private var lastWarmupMs = 0L
    private const val WARMUP_MIN_INTERVAL_MS = 30 * 60 * 1000L // 30 dakika

    /**
     * Warmup'ı başlat. Bu fonksiyon IO coroutine'inde çağrılabilir,
     * her site için Main thread'de WebView spin-up yapar.
     *
     * [maxSites]: Kaç site ısıtılacak (varsayılan: 8, batarya tasarrufu için)
     * [timeoutMs]: Her site için maksimum bekleme süresi
     */
    suspend fun runWarmup(
        context: Context,
        maxSites: Int = 14,
        timeoutMs: Long = 15_000L
    ) {
        val now = System.currentTimeMillis()
        if (now - lastWarmupMs < WARMUP_MIN_INTERVAL_MS) {
            Log.d(TAG, "Warmup atlandı — son warmuptan ${(now - lastWarmupMs) / 60_000}dk geçti.")
            return
        }
        lastWarmupMs = now

        // Zaten cookie'si olan siteleri filtrele
        val sitesToWarm = WARMUP_SITES
            .map { CsStreamRunner.getFixedUrl(it) } // Güncel dinamik domaini kullan
            .filter { url ->
                val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return@filter true
                val existing = CloudflareKiller.savedCookies[host]
                val hasCookie = !existing.isNullOrEmpty()
                if (hasCookie) {
                    Log.d(TAG, "[$host] Zaten cookie var, warmup atlanıyor.")
                }
                !hasCookie
            }
            .take(maxSites)

        if (sitesToWarm.isEmpty()) {
            Log.d(TAG, "Tüm siteler için cookie mevcut, warmup gerekmiyor.")
            return
        }

        Log.i(TAG, "CF Warmup başlatılıyor: ${sitesToWarm.size} site...")

        // Her siteyi sırayla warmup yap (paralel WebView'lar bellek sorununa yol açar)
        for (url in sitesToWarm) {
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: continue
            // molystream.org Turnstile çok daha uzun süreli — 25s
            val siteTimeout = if (host.contains("molystream") || host.contains("dizibox")) 25_000L
                              else timeoutMs
            try {
                Log.d(TAG, "[$host] Warmup başlıyor: $url (timeout: ${siteTimeout}ms)")
                val success = warmupSite(context, url, host, siteTimeout)
                Log.d(TAG, "[$host] Warmup ${if (success) "BAŞARILI ✅" else "başarısız ❌"}")
            } catch (e: Exception) {
                Log.w(TAG, "[$host] Warmup exception: ${e.message}")
            }
        }

        Log.i(TAG, "CF Warmup tamamlandı. Yüklü cookie sayısı: ${CloudflareKiller.savedCookies.size}")
    }

    /**
     * WebView'ı güvenli ve **tek seferlik** biçimde söker/serbest bırakır.
     *
     * Neden kritik: `WebView.destroy()` çağrıldıktan sonra aynı nesne üzerinde yapılan her
     * çağrı (`stopLoading()` dahil) Chromium tarafında "Application attempted to call on a
     * destroyed WebView" üretir ve **RenderThread içinde use-after-free (SIGSEGV)** riski
     * yaratır. 2026-10-09 tarihli sessiz çökme raporlarında (pid 15790 / 18716 / 28270)
     * çökme tam olarak `RenderThread` üzerinde, `libhwui.so` display-list oynatımı sırasında
     * `fault_addr=0x20` ile gerçekleşti ve logcat'te bu uyarı her warmup sonrası tekrarladı.
     *
     * Bu yüzden: (1) `settled` bayrağı ile yalnızca bir kez çalışır, (2) view önce parent'tan
     * sökülür, (3) yük durdurulur, (4) referanslar kesilir, (5) en son `destroy()` çağrılır.
     */
    private fun releaseWebView(webViewRef: WebView?, settled: java.util.concurrent.atomic.AtomicBoolean) {
        if (!settled.compareAndSet(false, true)) return
        if (webViewRef == null) return
        try {
            (webViewRef.parent as? android.view.ViewGroup)?.removeView(webViewRef)
        } catch (_: Throwable) {}
        try { webViewRef.stopLoading() } catch (_: Throwable) {}
        try { webViewRef.loadUrl("about:blank") } catch (_: Throwable) {}
        try { webViewRef.webViewClient = WebViewClient() } catch (_: Throwable) {}
        try { webViewRef.destroy() } catch (_: Throwable) {}
    }

    /**
     * Tek bir siteyi WebView ile warmup eder.
     * Başarı durumunda cookie CloudflareKiller.savedCookies'e kaydedilir.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun warmupSite(
        context: Context,
        url: String,
        host: String,
        timeoutMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val latch = CountDownLatch(1)
        var success = false
        val handler = Handler(Looper.getMainLooper())
        var webViewRef: WebView? = null
        // Tek seferlik teardown garantisi — hem timeout yolu hem başarı yolu aynı nesneyi
        // serbest bırakmaya çalışıyordu; bu çifte destroy çökme üretiyordu.
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        val startMs = System.currentTimeMillis()

        handler.post {
            try {
                val webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CloudflareKiller.UNIFIED_USER_AGENT
                    // Resimleri yükle — Turnstile canvas fingerprint kontrolü bunu gerektirir
                    settings.loadsImagesAutomatically = true

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            // WebView'in kendi yönlendirme ve POST akışını bozmamak için false döndür.
                            // Turnstile callback'leri JS redirect ile çalışır; override edince kırılıyor.
                            return false
                        }

                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            // Cookie kontrolü — cf_clearance veya WAF session cookie'si var mı?
                            val timeDiff = System.currentTimeMillis() - startMs
                            val cookieStr = CookieManager.getInstance().getCookie(pageUrl) ?: return
                            val cookieMap = CloudflareKiller.parseCookieMap(cookieStr)
                            if (cookieMap.isEmpty()) return

                            val hasCfClearance = cookieMap["cf_clearance"]?.isNotBlank() == true
                            val hasWaf = cookieMap.keys.any { k ->
                                k.contains("waf", ignoreCase = true) ||
                                k.contains("clearance", ignoreCase = true) ||
                                k.contains("ddg", ignoreCase = true)
                            }
                            val hasFallback = timeDiff >= 9_000L && cookieMap.isNotEmpty()

                            if (hasCfClearance || hasWaf || hasFallback) {
                                CloudflareKiller.savedCookies[host] = cookieMap
                                // www. versiyonunu da kaydet
                                if (!host.startsWith("www.")) {
                                    CloudflareKiller.savedCookies["www.$host"] = cookieMap
                                } else {
                                    CloudflareKiller.savedCookies[host.removePrefix("www.")] = cookieMap
                                }
                                // Failure cooldown'u temizle — artık cookie var
                                CloudflareKiller.hostFailureCooldown.remove(host)
                                CloudflareKiller.hostFailureCooldown.remove("www.$host")
                                success = true
                                Log.d(TAG, "[$host] Cookie alındı (${timeDiff}ms): ${cookieMap.keys}")
                                view.stopLoading()
                                latch.countDown()
                            }
                        }
                    }
                }
                webViewRef = webView
                webView.loadUrl(url)

                // Timeout handler
                handler.postDelayed({
                    if (!settled.get()) {
                        Log.w(TAG, "[$host] Warmup timeout (${timeoutMs}ms)")
                        // Timeout'ta bile var olan cookie'yi al
                        val cookieStr = CookieManager.getInstance().getCookie(url)
                        if (!cookieStr.isNullOrBlank()) {
                            val cookieMap = CloudflareKiller.parseCookieMap(cookieStr)
                            if (cookieMap.isNotEmpty()) {
                                CloudflareKiller.savedCookies[host] = cookieMap
                                if (!host.startsWith("www.")) {
                                    CloudflareKiller.savedCookies["www.$host"] = cookieMap
                                }
                                CloudflareKiller.hostFailureCooldown.remove(host)
                                success = true
                                Log.d(TAG, "[$host] Timeout'ta cookie kurtarıldı: ${cookieMap.keys}")
                            }
                        }
                    }
                    // Başarı yolunda da timeout yolunda da teardown BURADAN ve TEK KEZ yapılır.
                    releaseWebView(webViewRef, settled)
                    webViewRef = null
                    if (latch.count > 0) latch.countDown()
                }, timeoutMs)
            } catch (e: Exception) {
                Log.e(TAG, "[$host] WebView başlatma hatası: ${e.message}")
                releaseWebView(webViewRef, settled)
                webViewRef = null
                latch.countDown()
            }
        }

        // Main thread bloklamadan bekle
        latch.await(timeoutMs + 2000L, TimeUnit.MILLISECONDS)

        // Güvenlik ağı: latch herhangi bir sebeple sayılmadıysa WebView sızmasın.
        // releaseWebView tek seferlik olduğu için burada çifte destroy oluşmaz.
        handler.post {
            releaseWebView(webViewRef, settled)
            webViewRef = null
        }

        success
    }

    /**
     * Belirli bir host için cookie'yi manuel olarak temizler ve yeniden warmup yapar.
     * Kullanım: Bir site çalışmıyorsa UI'dan "Yenile" butonuna basıldığında.
     */
    suspend fun refreshCookieForHost(context: Context, url: String) {
        val fixedUrl = CsStreamRunner.getFixedUrl(url)
        val host = runCatching { java.net.URI(fixedUrl).host }.getOrNull() ?: return
        CloudflareKiller.savedCookies.remove(host)
        CloudflareKiller.savedCookies.remove("www.$host")
        CloudflareKiller.hostFailureCooldown.remove(host)
        CloudflareKiller.hostFailureCooldown.remove("www.$host")
        Log.d(TAG, "[$host] Cookie temizlendi, yeniden warmup yapılıyor...")
        warmupSite(context, fixedUrl, host, 15_000L)
    }
}

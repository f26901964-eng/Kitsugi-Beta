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
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Optional WebView warm-up for Cloudstream provider sites; cookies are saved for the
 * existing CloudflareKiller interceptor to reuse.
 *
 * This manager is not called during normal application startup: KitsugiApplication
 * intentionally disabled the batch warm-up after it created substantial Chromium
 * renderer/memory pressure. Production requests still use CloudflareKiller's lazy,
 * per-host challenge handling. The explicit warm-up remains available to instrumented
 * tests or a deliberate future on-demand flow.
 *
 * When invoked, this manager:
 *   - opens one hidden WebView at a time
 *   - waits for a usable cookie or the per-site timeout
 *   - stores cookies for CloudflareKiller
 *   - tears down the WebView
 */
object CsCfWarmupManager {

    private const val TAG = "CsCfWarmup"

    // ── v2.4.239 CF GATE ──────────────────────────────────────────────────────
    // Arama öncesi clearance kapısı: host'un çerezi yoksa gizli WebView ile
    // meydan okumayı ARAMA ÖNCESİNDE çözer. Böylece çıplak-isim araması → sonuç →
    // detay → sezon barı → bölümler zincirinin hiçbir halkası CF çözümü yüzünden
    // yarıda kesilmez; kullanıcıya "kaynak bunu zaten kendi yapıyormuş" gibi görünür.
    /** Gate bütçesi — kullanıcının istediği asgari sabır penceresi (90 sn). */
    const val CF_GATE_BUDGET_MS = 90_000L

    /** Aynı anda en fazla 2 gate WebView'ı — bellek/CPU baskısını sınırlar. */
    private val gateSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    /** Meydan okuma içermeyen (sağlıklı) doğrulanan hostlar — 30 dk boyunca gate atlanır. */
    private val healthyHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val HEALTHY_TTL_MS = 30 * 60_000L

    /** Sayfa meydan okuma mı temiz içerik mi? WebView içinde JS ile yoklanır. */
    private const val CHALLENGE_PROBE_JS = """
(function(){
  try {
    var t = (document.title || '').toLowerCase();
    var b = document.body ? (document.body.innerText || '').slice(0, 1500).toLowerCase() : '';
    var h = t + ' ' + b;
    var markers = ['just a moment','checking your browser','verify you are human','cf-challenge','challenge-platform','turnstile','__waf_challenge','security verification','bot verification','browser verification','insan oldugunuzu','robot olmadiginizi','tarayici dogrulama','güvenlik doğrulaması','attention required','cloudflare','ddos-guard'];
    for (var i = 0; i < markers.length; i++) { if (h.indexOf(markers[i]) !== -1) return 'CHALLENGE'; }
    return 'CLEAN';
  } catch (e) { return 'CLEAN'; }
})()
"""

    fun isKnownHealthy(host: String): Boolean {
        val bare = if (host.startsWith("www.")) host.removePrefix("www.") else host
        val ts = healthyHosts[host] ?: healthyHosts["www.$bare"] ?: healthyHosts[bare] ?: return false
        return (System.currentTimeMillis() - ts) < HEALTHY_TTL_MS
    }

    /** Host, yerleşik CF/WAF korumalı site listesinde mi? */
    fun isWarmupListedHost(host: String): Boolean {
        val bare = if (host.startsWith("www.")) host.removePrefix("www.") else host
        return WARMUP_SITES.any { site ->
            val siteHost = runCatching { java.net.URI(site).host }.getOrNull() ?: return@any false
            val siteBare = if (siteHost.startsWith("www.")) siteHost.removePrefix("www.") else siteHost
            siteBare == bare
        }
    }

    /**
     * Arama öncesi clearance kapısı.
     *
     * Hızlı yol: host'un kayıtlı clearance çerezi veya "sağlıklı site" kaydı varsa
     * anında (0 ms) döner — sağlıklı kaynaklara hiçbir gecikme eklemez.
     * Yavaş yol: gizli WebView açar, meydan okumayı en fazla [budgetMs] boyunca
     * çözmeye çalışır; çerezler CloudflareKiller'a kaydedilir ve sonraki tüm
     * aşamalar (arama/load/sezon/bölüm/loadLinks) bunları yeniden kullanır.
     */
    suspend fun ensureClearance(
        context: Context,
        url: String,
        budgetMs: Long = CF_GATE_BUDGET_MS
    ): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        if (CloudflareKiller.getSavedCookiesForHost(host)?.isNotEmpty() == true) return true
        if (isKnownHealthy(host)) return true
        val fixedUrl = CsStreamRunner.getFixedUrl(url)
        val start = System.currentTimeMillis()
        return gateSemaphore.withPermit {
            // Permit beklerken başka coroutine çözmüş olabilir — tekrar kontrol et.
            if (CloudflareKiller.getSavedCookiesForHost(host)?.isNotEmpty() == true) return@withPermit true
            if (isKnownHealthy(host)) return@withPermit true
            Log.i(TAG, "[$host] CF gate başlıyor — clearance arama öncesi çözülüyor (bütçe ${budgetMs}ms)")
            val ok = warmupSite(context, fixedUrl, host, budgetMs)
            Log.i(
                TAG,
                "[$host] CF gate ${if (ok) "BAŞARILI ✅" else "başarısız ❌"} (${System.currentTimeMillis() - start}ms)"
            )
            ok
        }
    }

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
        timeoutMs: Long = CF_GATE_BUDGET_MS
    ) {
        val now = System.currentTimeMillis()
        if (now - lastWarmupMs < WARMUP_MIN_INTERVAL_MS) {
            Log.d(TAG, "Warmup atlandı — son warmuptan ${(now - lastWarmupMs) / 60_000}dk geçti.")
            return
        }
        lastWarmupMs = now

        // Zaten cookie'si olan veya sağlıklı doğrulanmış siteleri filtrele
        val sitesToWarm = WARMUP_SITES
            .map { CsStreamRunner.getFixedUrl(it) } // Güncel dinamik domaini kullan
            .filter { url ->
                val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return@filter true
                val existing = CloudflareKiller.savedCookies[host]
                val hasCookie = !existing.isNullOrEmpty()
                if (hasCookie) {
                    Log.d(TAG, "[$host] Zaten cookie var, warmup atlanıyor.")
                }
                !hasCookie && !isKnownHealthy(host)
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
            // molystream.org / dizibox Turnstile'ı çok uzun sürer — tam CF sabır bütçesi.
            val siteTimeout = if (host.contains("molystream") || host.contains("dizibox"))
                CloudflareKiller.CF_SOLVE_BUDGET_MS else timeoutMs
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
     * Teardown yalnızca ana thread'de yapılır. `about:blank` yükleyip hemen `destroy()` etmekten
     * kaçınıyoruz; bu, kapatma sırasında Chromium'da yeni bir sayfa/render işi başlatabiliyordu.
     * Sıra: parent'tan sök → yükü durdur → client referanslarını kes → destroy.
     */
    private fun releaseWebView(webViewRef: WebView?, settled: java.util.concurrent.atomic.AtomicBoolean) {
        if (!settled.compareAndSet(false, true)) return
        if (webViewRef == null) return
        try {
            (webViewRef.parent as? android.view.ViewGroup)?.removeView(webViewRef)
        } catch (_: Throwable) {}
        try { webViewRef.stopLoading() } catch (_: Throwable) {}
        try { webViewRef.webViewClient = WebViewClient() } catch (_: Throwable) {}
        try { webViewRef.webChromeClient = null } catch (_: Throwable) {}
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

                        /** Çerezleri kaydet + gate'i başarıyla kapat + WebView'ı ANINDA sök. */
                        fun acceptAndFinish(view: WebView, cookieMap: Map<String, String>, timeDiff: Long, how: String) {
                            if (settled.get()) return
                            if (cookieMap.isNotEmpty()) {
                                CloudflareKiller.saveCookiesForHost(host, cookieMap)
                            }
                            // Failure cooldown'u temizle — artık cookie var
                            CloudflareKiller.removeFailureCooldownForHost(host)
                            success = true
                            Log.d(TAG, "[$host] Cookie alındı [$how] (${timeDiff}ms): ${cookieMap.keys}")
                            view.stopLoading()
                            if (latch.count > 0) latch.countDown()
                            // v2.4.239: başarıda WebView'ı bütçe sonuna kadar bekletme —
                            // eski davranış çözümden sonra da WebView'ı 90 sn açık tutuyordu.
                            // Söküm, WebView callback'i içinden destroy etmemek için post edilir.
                            handler.post { releaseWebView(webViewRef, settled) }
                        }

                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            // Cookie kontrolü — cf_clearance veya WAF session cookie'si var mı?
                            val timeDiff = System.currentTimeMillis() - startMs
                            val cookieStr = CookieManager.getInstance().getCookie(pageUrl)
                            val cookieMap = if (cookieStr.isNullOrBlank()) emptyMap()
                                            else CloudflareKiller.parseCookieMap(cookieStr)

                            val hasCfClearance = cookieMap["cf_clearance"]?.isNotBlank() == true
                            val hasWaf = cookieMap.keys.any { k ->
                                k.contains("waf", ignoreCase = true) ||
                                k.contains("clearance", ignoreCase = true) ||
                                k.contains("ddg", ignoreCase = true)
                            }
                            if (hasCfClearance || hasWaf) {
                                acceptAndFinish(view, cookieMap, timeDiff, "kanıt")
                                return
                            }

                            // Kanıt yok → sayfanın kendisine sor: meydan okuma mı, temiz mi?
                            // Temiz sayfa = site CF korumalı değil → 90 sn BEKLEME, gate anında kapanır.
                            // Meydan okuma sayfası = sabır penceresi (90 sn) sonuna kadar bekle.
                            try {
                                view.evaluateJavascript(CHALLENGE_PROBE_JS) { probe ->
                                    if (settled.get()) return@evaluateJavascript
                                    val verdict = probe?.trim('"') ?: "CLEAN"
                                    val elapsed = System.currentTimeMillis() - startMs
                                    if (verdict != "CHALLENGE") {
                                        healthyHosts[host] = System.currentTimeMillis()
                                        acceptAndFinish(view, cookieMap, elapsed, "temiz sayfa")
                                    } else if (elapsed >= CloudflareKiller.CF_SOLVE_FALLBACK_MIN_MS && cookieMap.isNotEmpty()) {
                                        // 90 sn doldu, kanıt hâlâ yok → oturum çerezini son çare kabul et.
                                        acceptAndFinish(view, cookieMap, elapsed, "sabırlı son çare")
                                    } else {
                                        Log.d(TAG, "[$host] Meydan okuma sayfası aktif (${elapsed}ms) — sabır penceresi işliyor")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "[$host] Challenge probe hatası: ${e.message}")
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
        warmupSite(context, fixedUrl, host, CF_GATE_BUDGET_MS)
    }
}

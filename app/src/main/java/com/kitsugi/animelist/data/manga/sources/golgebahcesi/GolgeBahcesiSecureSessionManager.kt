package com.kitsugi.animelist.data.manga.sources.golgebahcesi

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.data.manga.loader.MangaCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Gölge Bahçesi şifreli (`secure`) bölümler için tek-uçuşlu (single-flight),
 * akışlı (streaming) WebView şifre çözme oturumu yöneticisi.
 *
 * Her çözülen sayfa anında [MangaCache] diske yazılır; böylece:
 * - 180+ sayfalık bölümlerde 1. sayfa birkaç saniye içinde ekrana gelir,
 * - JVM belleğinde dev base64 dizileri tutulmaz (OOM önlenir),
 * - Kullanıcı bölüm ortasındaki bir sayfaya atlarsa o sayfa önceliklendirilir,
 * - Cloudflare Turnstile etkileşim isterse aynı canlı WebView doğrulama penceresinde
 *   gösterilir ve doğrulama geçildiği anda otomatik kapanır.
 */
object GolgeBahcesiSecureSessionManager {

    private const val TAG = "GolgeBahcesiSecure"
    private const val MAX_CONCURRENT_SESSIONS = 2
    private const val SESSION_IDLE_TIMEOUT_MS = 90_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionsMutex = Mutex()
    private val sessions = ConcurrentHashMap<String, ChapterCaptureSession>()

    suspend fun awaitPageStream(
        context: Context,
        ref: SecurePageRef,
        baseUrl: String,
        customUserAgent: String? = null,
        timeoutMs: Long = 85_000L,
    ): InputStream {
        val appContext = context.applicationContext
        val cache = MangaCache.getInstance(appContext)
        val pageKeyUrl = GolgeBahcesiParser.buildSecurePageUrl(
            chapterId = ref.chapterId,
            seriesSlug = ref.seriesSlug,
            chapterSlug = ref.chapterSlug,
            pageIndex = ref.pageIndex,
        )

        if (cache.isImageInCache(pageKeyUrl)) {
            return cache.getImageFile(pageKeyUrl).inputStream()
        }

        val session = getOrCreateSession(
            appContext = appContext,
            cache = cache,
            ref = ref,
            baseUrl = baseUrl,
            customUserAgent = customUserAgent,
        )

        return try {
            session.awaitPage(ref.pageIndex, pageKeyUrl, timeoutMs)
        } catch (e: Exception) {
            if (session.isFailedOrClosed()) {
                sessions.remove(ref.chapterId, session)
            }
            throw e
        }
    }

    private suspend fun getOrCreateSession(
        appContext: Context,
        cache: MangaCache,
        ref: SecurePageRef,
        baseUrl: String,
        customUserAgent: String?,
    ): ChapterCaptureSession = sessionsMutex.withLock {
        sessions[ref.chapterId]?.takeIf { !it.isFailedOrClosed() }?.let { existing ->
            existing.touch()
            return@withLock existing
        }

        // Eski/fazla oturumları temizle
        if (sessions.size >= MAX_CONCURRENT_SESSIONS) {
            val victim = sessions.entries
                .minByOrNull { it.value.lastAccessAt.get() }
            if (victim != null) {
                sessions.remove(victim.key)
                victim.value.closeAsync()
            }
        }

        val created = ChapterCaptureSession(
            appContext = appContext,
            cache = cache,
            chapterId = ref.chapterId,
            seriesSlug = ref.seriesSlug,
            chapterSlug = ref.chapterSlug,
            baseUrl = baseUrl,
            customUserAgent = customUserAgent,
            onFinished = { finished ->
                sessions.remove(ref.chapterId, finished)
            },
        )
        sessions[ref.chapterId] = created
        created.start()
        created
    }

    private class ChapterCaptureSession(
        private val appContext: Context,
        private val cache: MangaCache,
        private val chapterId: String,
        private val seriesSlug: String,
        private val chapterSlug: String,
        private val baseUrl: String,
        private val customUserAgent: String?,
        private val onFinished: (ChapterCaptureSession) -> Unit,
    ) {
        val lastAccessAt = AtomicLong(System.currentTimeMillis())

        private val mainHandler = Handler(Looper.getMainLooper())
        private val closed = AtomicBoolean(false)
        private val failed = AtomicBoolean(false)
        @Volatile private var failureCause: Throwable? = null
        @Volatile private var expectedCount: Int = 0
        @Volatile private var replyProxy: JavaScriptReplyProxy? = null
        @Volatile private var webView: WebView? = null
        @Volatile private var verificationDialog: Dialog? = null
        private val pendingPriorityIndices = ConcurrentHashMap.newKeySet<Int>()
        private val pageWaiters = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
        private val pendingPageWrites = java.util.concurrent.atomic.AtomicInteger(0)
        private val completionReceived = AtomicBoolean(false)
        private val finishScheduled = AtomicBoolean(false)
        private var idleWatchdogJob: Job? = null

        fun touch() {
            lastAccessAt.set(System.currentTimeMillis())
        }

        fun isFailedOrClosed(): Boolean = closed.get() || failed.get()

        private fun waiterFor(index: Int): CompletableDeferred<Unit> =
            pageWaiters.computeIfAbsent(index) { CompletableDeferred() }

        fun start() {
            touch()
            idleWatchdogJob = scope.launch {
                while (!closed.get()) {
                    delay(10_000L)
                    val idleMs = System.currentTimeMillis() - lastAccessAt.get()
                    if (idleMs > SESSION_IDLE_TIMEOUT_MS && verificationDialog == null) {
                        Log.d(TAG, "Oturum boşta kaldı, kapatılıyor: chapter=$chapterId")
                        closeAsync()
                        break
                    }
                }
            }
            scope.launch(Dispatchers.Main) {
                try {
                    initWebViewOnMain()
                } catch (t: Throwable) {
                    failSession(IOException("Gölge Bahçesi okuyucu başlatılamadı: ${t.message}", t))
                }
            }
        }

        suspend fun awaitPage(pageIndex: Int, pageKeyUrl: String, timeoutMs: Long): InputStream {
            touch()
            if (cache.isImageInCache(pageKeyUrl)) {
                return cache.getImageFile(pageKeyUrl).inputStream()
            }
            failureCause?.let { throw IOException(it.message ?: "Sayfa çözülemedi", it) }

            val waiter = waiterFor(pageIndex)
            requestPriority(pageIndex)

            withTimeout(timeoutMs) {
                waiter.await()
            }

            touch()
            val file = cache.getImageFile(pageKeyUrl)
            if (file.exists() && file.length() > 0L) {
                return file.inputStream()
            }
            throw IOException("Sayfa ${pageIndex + 1} önbelleğe yazılamadı")
        }

        private fun requestPriority(pageIndex: Int) {
            pendingPriorityIndices.add(pageIndex)
            val proxy = replyProxy ?: return
            flushPriorityRequests(proxy)
        }

        private fun flushPriorityRequests(proxy: JavaScriptReplyProxy) {
            val snapshot = pendingPriorityIndices.toList().sorted()
            if (snapshot.isEmpty()) return
            pendingPriorityIndices.removeAll(snapshot.toSet())
            mainHandler.post {
                for (idx in snapshot) {
                    runCatching { proxy.postMessage("req:$idx") }
                }
            }
        }

        @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
        private fun initWebViewOnMain() {
            if (closed.get()) return

            val initialBase = KitsugiApplication.activeActivity?.takeIf { it.isUsable() } ?: appContext
            val contextWrapper = MutableContextWrapper(initialBase)
            val wv = WebView(contextWrapper)
            webView = wv

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                blockNetworkImage = false
                val ua = customUserAgent?.takeIf { it.isNotBlank() }
                    ?: reducedChromeUserAgent(userAgentString)
                if (!ua.isNullOrBlank()) {
                    userAgentString = ua
                }
            }

            wv.layout(0, 0, 1080, 1920)
            wv.onResume()
            wv.resumeTimers()

            val cleanBaseUrl = baseUrl.trim().trimEnd('/')
            val origin = extractOrigin(cleanBaseUrl)
            val allowedOrigins = setOf(origin)
            val bridgeName = GolgeBahcesiReaderScripts.randomJsIdentifier()
            val channelName = GolgeBahcesiReaderScripts.randomJsIdentifier()
            val docStartScript = GolgeBahcesiReaderScripts.buildDocumentStartScript(
                bridgeName = bridgeName,
                channelName = channelName,
            )

            val hasWebMessageListener = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
            if (hasWebMessageListener) {
                WebViewCompat.addWebMessageListener(
                    wv,
                    bridgeName,
                    allowedOrigins,
                ) { _: WebView, message: WebMessageCompat, _: Uri, isMainFrame: Boolean, proxy: JavaScriptReplyProxy ->
                    if (!isMainFrame) return@addWebMessageListener
                    if (replyProxy == null) {
                        replyProxy = proxy
                        flushPriorityRequests(proxy)
                    } else {
                        replyProxy = proxy
                    }
                    val raw = message.data ?: return@addWebMessageListener
                    handleBridgePayload(raw)
                }
            } else {
                wv.addJavascriptInterface(
                    FallbackJsBridge { raw -> handleBridgePayload(raw) },
                    bridgeName,
                )
            }

            val hasDocStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (hasDocStart) {
                WebViewCompat.addDocumentStartJavaScript(wv, docStartScript, allowedOrigins)
            }

            wv.webChromeClient = WebChromeClient()
            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!hasDocStart && url != null && url.startsWith(origin)) {
                        view?.evaluateJavascript(docStartScript, null)
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (!request.isForMainFrame) return false
                    val host = uri.host?.lowercase().orEmpty()
                    val siteHost = cleanBaseUrl.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
                    val trustedTopLevel = host == siteHost ||
                        host.endsWith(".$siteHost") ||
                        host == "challenges.cloudflare.com"
                    return !trustedTopLevel
                }
            }

            val chapterPageUrl = "$cleanBaseUrl/manga/$seriesSlug/bolum/$chapterSlug"
            Log.d(TAG, "Şifreli bölüm oturumu başlatılıyor: $chapterPageUrl")
            wv.loadUrl(chapterPageUrl, mapOf("Referer" to "$cleanBaseUrl/"))
        }

        private fun handleBridgePayload(raw: String) {
            if (closed.get()) return
            touch()
            try {
                val obj = JSONObject(raw)
                when (obj.optString("type")) {
                    "count" -> {
                        val cnt = obj.optInt("count", 0)
                        if (cnt > 0) {
                            expectedCount = cnt
                            Log.d(TAG, "Okuyucu hazır: $cnt sayfa (chapter=$chapterId)")
                            dismissVerificationOverlay()
                        }
                    }

                    "need_interaction" -> {
                        if (expectedCount <= 0) {
                            Log.i(TAG, "Turnstile etkileşim gerektiriyor, doğrulama penceresi açılıyor")
                            showVerificationOverlay()
                        }
                    }

                    "page" -> {
                        val idx = obj.optInt("index", -1)
                        val base64 = obj.optString("data", "")
                        if (idx >= 0 && base64.isNotEmpty()) {
                            pendingPageWrites.incrementAndGet()
                            scope.launch {
                                try {
                                    persistDecodedPage(idx, base64)
                                } finally {
                                    pendingPageWrites.decrementAndGet()
                                    maybeFinishCompletedSession()
                                }
                            }
                        }
                    }

                    "page_error" -> {
                        val idx = obj.optInt("index", -1)
                        val msg = obj.optString("message", "Sayfa çözülemedi")
                        if (idx >= 0) {
                            waiterFor(idx).completeExceptionally(
                                IOException("Sayfa ${idx + 1} çözülemedi: $msg")
                            )
                        }
                    }

                    "complete" -> {
                        if (completionReceived.compareAndSet(false, true)) {
                            Log.d(TAG, "Bölümdeki tüm sayfalar çözülüyor: chapter=$chapterId")
                        }
                        maybeFinishCompletedSession()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Köprü mesajı ayrıştırılamadı: ${e.message}")
            }
        }

        private fun maybeFinishCompletedSession() {
            if (!completionReceived.get() || pendingPageWrites.get() != 0) return
            if (!finishScheduled.compareAndSet(false, true)) return
            scope.launch {
                // UI/reader coroutine disk cache'i okuyabilsin diye küçük bir grace window bırak
                delay(750L)
                if (completionReceived.get() && pendingPageWrites.get() == 0) {
                    closeAsync()
                } else {
                    finishScheduled.set(false)
                }
            }
        }

        private fun persistDecodedPage(index: Int, base64: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                if (bytes.isEmpty()) {
                    throw IOException("Boş görsel verisi")
                }
                val pageKeyUrl = GolgeBahcesiParser.buildSecurePageUrl(
                    chapterId = chapterId,
                    seriesSlug = seriesSlug,
                    chapterSlug = chapterSlug,
                    pageIndex = index,
                )
                ByteArrayInputStream(bytes).use { input ->
                    cache.putImageToCache(pageKeyUrl, input)
                }
                waiterFor(index).complete(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Sayfa diske yazılamadı [idx=$index]: ${e.message}")
                waiterFor(index).completeExceptionally(
                    IOException("Sayfa ${index + 1} kaydedilemedi: ${e.message}", e)
                )
            }
        }

        private fun showVerificationOverlay() {
            mainHandler.post {
                if (closed.get() || expectedCount > 0 || verificationDialog != null) return@post
                val wv = webView ?: return@post
                val activity = KitsugiApplication.activeActivity?.takeIf { it.isUsable() } ?: return@post

                try {
                    (wv.context as? MutableContextWrapper)?.baseContext = activity
                    (wv.parent as? ViewGroup)?.removeView(wv)

                    val density = activity.resources.displayMetrics.density
                    fun dp(v: Int): Int = (v * density).toInt()

                    val root = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.parseColor("#FF0E1015"))
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }

                    val header = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundColor(Color.parseColor("#FF161922"))
                        setPadding(dp(16), dp(14), dp(12), dp(14))
                    }

                    val titleView = TextView(activity).apply {
                        text = "Güvenlik Doğrulaması — Kutucuğu onaylayın"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        typeface = Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }

                    val cancelBtn = TextView(activity).apply {
                        text = "Kapat"
                        setTextColor(Color.parseColor("#FFFF6B6B"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        setOnClickListener {
                            failSession(IOException("Güvenlik doğrulaması kullanıcı tarafından iptal edildi"))
                        }
                    }

                    header.addView(titleView)
                    header.addView(cancelBtn)

                    val webContainer = FrameLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
                    }
                    webContainer.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                    root.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                    root.addView(webContainer)

                    val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                        window?.apply {
                            setLayout(MATCH_PARENT, MATCH_PARENT)
                            setBackgroundDrawable(ColorDrawable(Color.parseColor("#E6000000")))
                            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                            setDimAmount(0.7f)
                        }
                        setContentView(root)
                        setOnCancelListener {
                            if (expectedCount <= 0) {
                                failSession(IOException("Güvenlik doğrulaması iptal edildi"))
                            }
                        }
                    }
                    verificationDialog = dialog
                    dialog.show()
                } catch (t: Throwable) {
                    Log.w(TAG, "Doğrulama penceresi açılamadı: ${t.message}")
                }
            }
        }

        private fun dismissVerificationOverlay() {
            mainHandler.post {
                val dialog = verificationDialog ?: return@post
                verificationDialog = null
                val wv = webView
                try {
                    dialog.setOnCancelListener(null)
                    dialog.setOnDismissListener(null)
                    if (dialog.isShowing) {
                        runCatching { dialog.dismiss() }
                    }
                } finally {
                    if (wv != null) {
                        runCatching {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            (wv.context as? MutableContextWrapper)?.baseContext = appContext
                            if (!closed.get()) {
                                wv.layout(0, 0, 1080, 1920)
                                wv.onResume()
                                wv.resumeTimers()
                            }
                        }
                    }
                }
            }
        }

        private fun failSession(error: Throwable) {
            if (failed.compareAndSet(false, true)) {
                failureCause = error
                pageWaiters.values.forEach { deferred ->
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(error)
                    }
                }
                closeAsync()
            }
        }

        fun closeAsync() {
            if (!closed.compareAndSet(false, true)) return
            idleWatchdogJob?.cancel()
            if (!completionReceived.get()) {
                val error = failureCause ?: IOException("Gölge Bahçesi okuyucu oturumu kapandı")
                pageWaiters.values.forEach { waiter ->
                    if (!waiter.isCompleted) waiter.completeExceptionally(error)
                }
            }
            onFinished(this)
            mainHandler.post {
                dismissVerificationOverlay()
                val wv = webView
                webView = null
                replyProxy = null
                if (wv != null) {
                    runCatching {
                        CookieManager.getInstance().flush()
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.destroy()
                    }
                }
            }
        }
    }

    private class FallbackJsBridge(private val onPayload: (String) -> Unit) {
        @JavascriptInterface
        fun postMessage(payload: String) {
            onPayload(payload)
        }
    }

    private fun Activity.isUsable(): Boolean = !isFinishing && !isDestroyed

    private fun extractOrigin(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return "https://golgebahcesi.com"
        return if (parsed.port == HttpUrl.defaultPort(parsed.scheme)) {
            "${parsed.scheme}://${parsed.host}"
        } else {
            "${parsed.scheme}://${parsed.host}:${parsed.port}"
        }
    }

    private fun reducedChromeUserAgent(webViewUa: String?): String? {
        val major = Regex("""Chrome/(\d+)""").find(webViewUa.orEmpty())?.groupValues?.getOrNull(1)
            ?: return null
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
    }
}

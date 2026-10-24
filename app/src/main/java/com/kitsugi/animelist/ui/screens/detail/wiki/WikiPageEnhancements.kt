package com.kitsugi.animelist.ui.screens.detail.wiki

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.kitsugi.animelist.data.local.TranslationManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

internal class WikiPageAssets private constructor(
    val translator: String,
    val cosmetic: String,
    val rules: WikiAdBlockRules,
    val license: String,
    val filterRevision: String,
    val appearance: String
) {
    companion object {
        @Volatile private var cached: WikiPageAssets? = null
        /** Call on Dispatchers.IO, before constructing the WebView (no requests escape startup filtering). */
        @Synchronized fun load(context: Context): WikiPageAssets = cached ?: WikiPageAssets(
            context.assets.open("wiki/dom-translator.js").bufferedReader().use { it.readText() },
            context.assets.open("wiki/adblock-cosmetic.js").bufferedReader().use { it.readText() },
            WikiAdBlockRules.fromHosts(context.assets.open("wiki/stevenblack-hosts.txt").bufferedReader().use { it.readText() }),
            context.assets.open("wiki/STEVENBLACK-LICENSE.txt").bufferedReader().use { it.readText() },
            JSONObject(context.assets.open("wiki/hosts-source.json").bufferedReader().use { it.readText() }).getString("commit").take(12),
            context.assets.open("wiki/page-appearance.js").bufferedReader().use { it.readText() }
        ).also { cached = it }
    }
}

/** Main-thread DOM coordinator + immutable off-thread request matcher. No JavascriptInterface.
 * Remote documents cannot invoke native APIs. All page text is pulled via bounded evaluateJavascript
 * results and goes to the existing in-app Google service only when automatic/manual translation is on.
 */
internal class WikiPageEnhancements(
    context: Context,
    private val view: WebView,
    private val session: WikiOriginalSession,
    private val assets: WikiPageAssets,
    initialConfig: WikiPageToolConfig
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translator by lazy { TranslationManager(context.applicationContext) }
    private val blocking = AtomicBoolean(initialConfig.blockAds)
    private val blocked = AtomicInteger(0)
    private var config = initialConfig
    private var job: Job? = null
    private var runGeneration = 0
    private var appearanceJob: Job? = null
    private var appearanceMode = "SITE"
    private var appearanceGeneration = 0
    private var documentId = UUID.randomUUID().toString()
    private var ready = false
    private var foreground = true
    @Volatile private var disposed = false
    @Volatile private var wikiDocument =
        WikiOriginalPagePolicy.destination(session.url) == WikiOriginalPagePolicy.Destination.WIKI

    private companion object {
        /** Aynı anda çevrilen paket sayısı; çevirmenin ağ kapısıyla (4 izin) aynıdır. */
        const val MAX_IN_FLIGHT_BATCHES = 4
    }

    /** WebView invokes this on background threads. No UI, IO, regex or JS evaluation here. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (disposed || !wikiDocument || !blocking.get() ||
            !assets.rules.shouldBlock(request.url.toString(), request.isForMainFrame)) return null
        blocked.incrementAndGet()
        return WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
            mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }

    fun pageStarted(url: String?) {
        job?.cancel()
        appearanceJob?.cancel()
        session.pageThemeSupported = true
        documentId = UUID.randomUUID().toString()
        ready = false
        wikiDocument = url != null && WikiOriginalPagePolicy.destination(url) == WikiOriginalPagePolicy.Destination.WIKI
        blocking.set(android.net.Uri.parse(url.orEmpty()).host.orEmpty().lowercase(java.util.Locale.ROOT) !in config.disabledAdHosts)
        blocked.set(0)
        session.blockedRequests = 0
        session.translating = false
        session.translatedNodes = 0
        session.translationFailures = 0
        session.translationLimited = false
        session.articleAvailable = false
        session.enhancementError = false
        val documentUrl = url?.substringBefore('#')
        if (session.translationDocumentUrl != null && session.translationDocumentUrl != documentUrl) {
            session.translationOverride = null
            config = config.copy(translate = config.automaticTranslate)
        }
        session.translationDocumentUrl = documentUrl
    }

    fun documentAvailable(url: String?) {
        if (disposed || url == null || WikiOriginalPagePolicy.destination(url) != WikiOriginalPagePolicy.Destination.WIKI) return
        wikiDocument = true
        if (ready) { applyAppearance(); return } // Do not erase originals/cache on duplicate finish.
        ready = true
        applyAppearance()
        restart()
    }

    fun setAppearance(mode: String) {
        if (appearanceMode == mode) return
        appearanceMode = mode
        applyAppearance()
    }

    private fun applyAppearance() {
        val generation = ++appearanceGeneration
        appearanceJob?.cancel()
        if (!ready || disposed || !foreground) return
        val id = documentId
        val mode = appearanceMode
        appearanceJob = scope.launch {
            try {
                evaluate(assets.appearance)
                val result = json("window.__kitsugiWikiAppearanceV1(${JSONObject.quote(mode)})")
                if (!disposed && id == documentId && generation == appearanceGeneration) {
                    session.pageThemeSupported = result.optBoolean("supported")
                    val canvas = result.optString("canvas", "#ffffff")
                    if (canvas.matches(Regex("#[0-9a-fA-F]{6}"))) {
                        view.setBackgroundColor(android.graphics.Color.parseColor(canvas))
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (!disposed && id == documentId && generation == appearanceGeneration) session.pageThemeSupported = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!disposed && id == documentId && generation == appearanceGeneration) session.pageThemeSupported = false
            }
        }
    }

    fun configure(next: WikiPageToolConfig) {
        if (disposed || next == config) return
        val reloadAds = blocking.get() != next.blockAds
        val retry = config.retry != next.retry
        config = next
        blocking.set(next.blockAds)
        if (reloadAds && wikiDocument) {
            job?.cancel()
            ready = false
            // Previously blocked scripts cannot be resurrected with CSS alone.
            session.scrollX = view.scrollX
            session.scrollY = view.scrollY
            session.restoreAfterReload = true
            view.reload()
        } else restart(retry)
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) { applyAppearance(); restart() }
        else { runGeneration++; job?.cancel(); appearanceJob?.cancel(); session.translating = false }
    }

    private fun restart(retry: Boolean = false) {
        val run = ++runGeneration
        job?.cancel()
        session.translating = false
        if (!ready || disposed || !foreground) return
        val id = documentId
        val settings = config
        job = scope.launch {
            try {
                evaluate(assets.translator)
                evaluate(assets.cosmetic)
                evaluate("window.__kitsugiWikiDomV1.release(${JSONObject.quote(id)});")
                val status = json("window.__kitsugiWikiDomV1.configure(${JSONObject.quote(id)}," +
                    "${JSONObject.quote(settings.target)},${settings.translate},$retry,${JSONObject.quote(settings.source)})")
                updateStatus(status)
                session.documentTextRevision++
                session.enhancementError = false
                // Boru hattı: eskiden döngü tek paketi alıp ağ yanıtını bekliyor, sonra
                // 120 ms uyuyup sıradakine geçiyordu; 162 metin yirmiden çok ardışık
                // ağ turu demekti. Şimdi en çok MAX_IN_FLIGHT_BATCHES paket aynı anda
                // çevrilir (ağ eşzamanlılığı çevirmenin kapısında zaten sınırlı),
                // JS köprüsü çağrıları (take/apply) bu coroutine'de sıralı kalır ve
                // sonuçlar belge sırasıyla uygulanır.
                val inFlight = mutableListOf<Deferred<List<WikiDomResult>>>()
                var lastAdsAt = 0L

                suspend fun applyOldest() {
                    val translated = inFlight.removeAt(0).await()
                    currentCoroutineContext().ensureActive()
                    val values = JSONArray().apply {
                        translated.forEach { result -> put(JSONObject().apply {
                            put("id", result.item.id)
                            put("rev", result.item.revision)
                            put("source", result.item.text)
                            put("text", result.translated ?: JSONObject.NULL)
                        }) }
                    }
                    // Results are JSON data, never HTML or executable translator output.
                    var applied: JSONObject
                    do {
                        applied = json("window.__kitsugiWikiDomV1.apply(${JSONObject.quote(id)},$values)")
                        if (applied.optBoolean("deferred")) delay(300)
                    } while (currentCoroutineContext().isActive && applied.optBoolean("deferred"))
                    updateStatus(applied.optJSONObject("status"))
                    if (!applied.optBoolean("stale")) session.documentTextRevision++
                    if (!disposed && run == runGeneration && id == documentId) {
                        session.translating = inFlight.isNotEmpty()
                    }
                }

                while (isActive && ready && id == documentId) {
                    session.blockedRequests = blocked.get()
                    // Reklam stilini her turda köprüden göndermek yerine en sık 2 sn'de
                    // bir tazele; betik zaten yalnızca eksik/değişmişse dokunur.
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastAdsAt >= 2000) {
                        lastAdsAt = now
                        evaluate("window.__kitsugiWikiAdsV1(${settings.blockAds});")
                    }
                    if (!settings.translate) { delay(1000); continue }
                    if (inFlight.size >= MAX_IN_FLIGHT_BATCHES) { applyOldest(); continue }
                    val batch = json("window.__kitsugiWikiDomV1.take(${JSONObject.quote(id)})")
                    updateStatus(batch.optJSONObject("status"))
                    val items = decodeItems(batch.optJSONArray("items"))
                    if (items.isEmpty()) {
                        if (inFlight.isNotEmpty()) {
                            applyOldest()
                        } else {
                            session.translating = false
                            delay(if (batch.optJSONObject("status")?.optBoolean("scanning") == true) 120 else 900)
                        }
                        continue
                    }
                    session.translating = true
                    inFlight.add(async {
                        try {
                            WikiDomTranslationBatch.translate(
                                items,
                                cached = { text ->
                                    translator.cachedWebPageText(text, settings.source, settings.target)
                                },
                                store = { pairs ->
                                    translator.storeWebPageTexts(pairs, settings.source, settings.target)
                                }
                            ) { text ->
                                translator.translateWebPageText(text, settings.source, settings.target)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            items.map { WikiDomResult(it, null) }
                        }
                    })
                    // En eski paket biz sıradakini toplarken bitmişse bekletmeden uygula;
                    // bitmemişse sabit gecikme olmadan sonraki paketi toplamaya devam et.
                    if (inFlight.first().isCompleted) applyOldest()
                }
            } catch (_: TimeoutCancellationException) {
                if (!disposed && run == runGeneration && id == documentId) session.enhancementError = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A broken/unavailable document bridge is not a successful translation.
                if (!disposed && run == runGeneration && id == documentId) session.enhancementError = true
            } finally {
                if (!disposed && run == runGeneration && id == documentId && config == settings) session.translating = false
            }
        }
    }

    private fun updateStatus(status: JSONObject?) {
        if (status == null) return
        session.translatedNodes = status.optInt("translated").coerceIn(0, 6000)
        session.translationFailures = status.optInt("failed").coerceIn(0, 6000)
        session.translationLimited = status.optBoolean("limited")
        session.articleAvailable = status.optBoolean("available")
    }

    private fun decodeItems(array: JSONArray?): List<WikiDomText> {
        if (array == null) return emptyList()
        require(array.length() <= WikiDomTranslationBatch.MAX_ROWS)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val text = item.getString("text")
            require(text.length in 1..8000)
            WikiDomText(item.getInt("id"), item.getInt("rev"), text)
        }.also { require(it.map { row -> row.id }.distinct().size == it.size) }
    }

    private suspend fun json(expression: String): JSONObject = JSONObject(evaluate("JSON.stringify($expression)").let { raw ->
        // evaluateJavascript JSON-encodes JS strings a second time.
        JSONArray("[$raw]").getString(0)
    })

    private suspend fun evaluate(script: String): String = withTimeout(7000) {
        suspendCancellableCoroutine { continuation ->
            if (disposed) { continuation.cancel(); return@suspendCancellableCoroutine }
            view.evaluateJavascript(script) { value ->
                if (continuation.isActive) {
                    if (value != null && value.length <= 180_000) continuation.resume(value)
                    else continuation.resume("null")
                }
            }
        }
    }

    fun close() {
        if (session.enhancements === this) session.translating = false
        disposed = true
        blocking.set(false)
        scope.cancel()
    }
}

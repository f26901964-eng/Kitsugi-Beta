package com.kitsugi.animelist.data.remote.malweb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * First-party WebView continuation engine for `https://myanimelist.net/notification`.
 *
 * Captures notifications across ALL possible MAL page mechanisms without guessing:
 *  1. `window.MAL.notification` (`items` + `historyItems`)
 *  2. Live XHR / `fetch()` JSON responses intercepted from MAL's own JS bundle
 *  3. Live Vue 2 / Vue 3 component state (`__vue__` / `__vueParentComponent`) when
 *     `this.items = this.items.concat(...)` replaces the array without mutating `window.MAL`
 *  4. Rendered `#content` HTML (`li.notification-item` + `.notificaiton-category-nav`) across
 *     both in-page AJAX updates and multi-page `.notification-pagination a` navigations.
 *
 * Preserves unread state by intercepting `/notification/api/check-items-as-read.json`.
 */
internal object MalNotificationsWebPager {
    private const val NOTIFICATIONS_URL = "https://myanimelist.net/notification"
    private const val BRIDGE_NAME = "KitsugiMalNotifications"
    private const val TIMEOUT_MS = 32_000L
    private const val MAX_SCROLL_ROUNDS = 24
    private const val DEFAULT_MIN_UNSEEN_TARGET = 40

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(
        context: Context,
        page: Int,
        knownIds: Set<String> = emptySet(),
        startUrl: String = NOTIFICATIONS_URL,
        minUnseenTarget: Int = DEFAULT_MIN_UNSEEN_TARGET
    ): MalNotificationsClient.Feed =
        withContext(Dispatchers.Main.immediate) {
            val safeStart = MalNotificationsClient.normalizeNotificationPageUrl(startUrl) ?: NOTIFICATIONS_URL
            val normalizedKnown = MalNotificationsClient.normalizeKnownIds(knownIds)
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<MalNotificationsClient.Feed> { continuation ->
                    val session = FetchSession(
                        context = context.applicationContext,
                        page = page,
                        initialKnownIds = normalizedKnown,
                        startUrl = safeStart,
                        minUnseenTarget = minUnseenTarget.coerceAtLeast(1),
                        continuation = continuation
                    )
                    continuation.invokeOnCancellation { session.cancel() }
                    session.start()
                }
            } ?: throw MalNotificationsFetchException("MAL bildirimlerinin devamı zaman aşımına uğradı")
        }

    private class FetchSession(
        private val context: Context,
        private val page: Int,
        private val initialKnownIds: Set<String>,
        private val startUrl: String,
        private val minUnseenTarget: Int,
        private val continuation: CancellableContinuation<MalNotificationsClient.Feed>
    ) : WebViewClient() {
        private val handler = Handler(Looper.getMainLooper())
        private val completed = AtomicBoolean(false)
        private var probedNotificationUrl: String? = null
        private var webView: WebView? = null
        private val accumulatedById = LinkedHashMap<String, MalNotificationsClient.Notification>()
        private var latestOwner: String? = null
        private var latestCounts: Map<MalNotificationCategory, Int> = emptyMap()
        private var latestCategoryUrls: Map<MalNotificationCategory, String> = emptyMap()

        private val bridge = object {
            @JavascriptInterface
            fun onSnapshot(rawSnapshot: String?) {
                if (rawSnapshot == null || completed.get()) return
                handler.post { ingestSnapshot(rawSnapshot, terminal = false) }
            }

            @JavascriptInterface
            fun onResult(result: String?) {
                if (result == null || completed.get()) return
                handler.post { ingestSnapshot(result, terminal = true) }
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        fun start() {
            if (!continuation.isActive) return
            try {
                CookieManager.getInstance().setAcceptCookie(true)
                val created = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    val width = context.resources.displayMetrics.widthPixels.coerceAtLeast(360)
                    val height = context.resources.displayMetrics.heightPixels.coerceAtLeast(640)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, width, height)
                    addJavascriptInterface(bridge, BRIDGE_NAME)
                    webViewClient = this@FetchSession
                }
                runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(created, true) }
                webView = created
                created.loadUrl(startUrl)
            } catch (_: Exception) {
                finish(error = MalNotificationsFetchException("MAL web bildirim sayfası açılamadı"))
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isFirstPartyHttps(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url
            val isMarkReadRequest = isFirstPartyHttps(uri.toString()) &&
                uri.path.equals("/notification/api/check-items-as-read.json", ignoreCase = true)
            if (!isMarkReadRequest) return null

            return WebResourceResponse(
                "application/json",
                "UTF-8",
                200,
                "OK",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("{\"result\":false}".toByteArray(Charsets.UTF_8))
            )
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (isFirstPartyHttps(url) && isNotificationOrLoginPage(url)) {
                view.evaluateJavascript(installNetworkHookScript(), null)
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            super.onPageCommitVisible(view, url)
            if (isFirstPartyHttps(url) && isNotificationOrLoginPage(url)) {
                view.evaluateJavascript(installNetworkHookScript(), null)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            val currentUrl = view.url ?: url
            if (!isFirstPartyHttps(currentUrl) || !isNotificationOrLoginPage(currentUrl)) return
            if (probedNotificationUrl == currentUrl) return
            probedNotificationUrl = currentUrl
            val alreadyUnseen = accumulatedById.keys.count { it !in initialKnownIds }
            val remainingTarget = (minUnseenTarget - alreadyUnseen).coerceAtLeast(1)
            val allKnownSoFar = initialKnownIds + accumulatedById.keys
            view.evaluateJavascript(
                scrollAndCollectScript(
                    knownIds = allKnownSoFar,
                    minUnseenTarget = remainingTarget
                ),
                null
            )
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (!request.isForMainFrame) return
            when (errorResponse.statusCode) {
                401, 403 -> finish(error = MalWebSessionRequiredException("MAL web oturumu geçersiz"))
                else -> finish(error = MalNotificationsFetchException("MAL bildirim sayfası HTTP ${errorResponse.statusCode}"))
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                finish(error = MalNotificationsFetchException("MAL bildirim sayfası yüklenemedi"))
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            handler.cancel()
            finish(error = MalNotificationsFetchException("MAL bildirim sayfasının TLS sertifikası doğrulanamadı"))
        }

        fun cancel() {
            completed.set(true)
            handler.post { release() }
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeUnless { it == "null" }

        private fun JsonObject.boolean(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.booleanOrNull

        private fun ingestSnapshot(rawResult: String, terminal: Boolean) {
            if (completed.get()) return
            val envelope = runCatching { json.parseToJsonElement(rawResult).jsonObject }.getOrNull()
                ?: run {
                    if (terminal) finish(error = MalNotificationsParseException("MAL web bildirim yanıtı geçersiz JSON"))
                    return
                }

            val currentUrl = envelope.string("url").orEmpty()
            if (envelope.boolean("signedOut") == true || isLoginUrl(currentUrl)) {
                if (terminal) finish(error = MalWebSessionRequiredException("MAL web oturumu sona ermiş"))
                return
            }

            (envelope["apiUrls"] as? JsonArray)?.forEach { elem ->
                MalNotificationsClient.recordDiscoveredApiUrl((elem as? JsonPrimitive)?.contentOrNull)
            }

            val owner = MalNotificationsClient.parseSessionUsernameFromProfileUrl(envelope.string("ownerHref"))
            if (owner != null) latestOwner = owner

            val contentHtml = envelope.string("contentHtml").orEmpty()
            val document = contentHtml.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { Jsoup.parseBodyFragment(raw, NOTIFICATIONS_URL) }.getOrNull()
                    ?.also { doc -> doc.body().addClass("notification") }
            }
            if (document != null) {
                val sidebar = MalNotificationsClient.parseSidebarMetadata(document)
                if (sidebar.counts.isNotEmpty()) latestCounts = sidebar.counts
                if (sidebar.urls.isNotEmpty()) latestCategoryUrls = sidebar.urls
            }
            val domItems = document?.let(MalNotificationsClient::parseDomNotifications).orEmpty()

            val payloadObj = envelope["notification"] as? JsonObject
            val jsonFeed = payloadObj?.let {
                MalNotificationsClient.parseNotificationPayload(
                    payload = it.toString(),
                    page = page,
                    sessionUsername = latestOwner,
                    hasNextPageOverride = envelope.boolean("mayHaveMore"),
                    categoryCounts = latestCounts,
                    categoryUrls = latestCategoryUrls
                )
            }

            val combinedBatch = buildList {
                if (jsonFeed != null) addAll(jsonFeed.items)
                if (domItems.isNotEmpty()) {
                    val seenIds = jsonFeed?.items?.map { it.id }?.toHashSet().orEmpty()
                    val seenSigs = jsonFeed?.items?.map { it.signatureKey() }?.toHashSet().orEmpty()
                    domItems.forEach { dom ->
                        if (dom.id !in seenIds && dom.signatureKey() !in seenSigs) {
                            add(dom)
                        }
                    }
                }
            }
            combinedBatch.forEach { notif -> accumulatedById[notif.id] = notif }

            if (!terminal) return

            if (accumulatedById.isEmpty() && jsonFeed == null) {
                return finish(error = MalNotificationsParseException("MAL bildirim verisi web sayfasında bulunamadı"))
            }

            val finalItems = accumulatedById.values.toList()
            val sidebarTotal = latestCounts[MalNotificationCategory.ALL]
            val reportedMore = envelope.boolean("mayHaveMore") ?: false
            val finalHasNext = if (sidebarTotal != null && finalItems.size >= sidebarTotal) {
                false
            } else {
                reportedMore
            }

            finish(
                value = MalNotificationsClient.Feed(
                    items = finalItems,
                    sessionUsername = latestOwner,
                    page = page,
                    hasNextPage = finalHasNext,
                    nextPageUrl = jsonFeed?.nextPageUrl,
                    categoryCounts = latestCounts,
                    categoryUrls = latestCategoryUrls
                )
            )
        }

        private fun finish(
            value: MalNotificationsClient.Feed? = null,
            error: Exception? = null
        ) {
            if (!completed.compareAndSet(false, true)) return
            release()
            if (!continuation.isActive) return
            if (error != null) continuation.resumeWithException(error)
            else if (value != null) continuation.resume(value)
            else continuation.resumeWithException(
                MalNotificationsParseException("MAL bildirim yanıtı boş döndü")
            )
        }

        private fun release() {
            val current = webView ?: return
            webView = null
            runCatching { current.stopLoading() }
            runCatching { current.removeJavascriptInterface(BRIDGE_NAME) }
            runCatching { current.webViewClient = WebViewClient() }
            runCatching { current.webChromeClient = null }
            runCatching { (current.parent as? ViewGroup)?.removeView(current) }
            runCatching { current.destroy() }
        }
    }

    private fun isFirstPartyHttps(rawUrl: String): Boolean = runCatching {
        val uri = Uri.parse(rawUrl)
        uri.scheme.equals("https", ignoreCase = true) &&
            (uri.host?.equals("myanimelist.net", ignoreCase = true) == true ||
                uri.host?.equals("www.myanimelist.net", ignoreCase = true) == true) &&
            uri.userInfo.isNullOrEmpty()
    }.getOrDefault(false)

    private fun isNotificationOrLoginPage(rawUrl: String): Boolean = runCatching {
        val uri = Uri.parse(rawUrl)
        val path = uri.path.orEmpty()
        path.equals("/notification", ignoreCase = true) ||
            path.startsWith("/notification/", ignoreCase = true) ||
            isLoginUrl(rawUrl)
    }.getOrDefault(false)

    private fun isLoginUrl(rawUrl: String): Boolean = runCatching {
        Uri.parse(rawUrl).path.orEmpty().contains("login", ignoreCase = true)
    }.getOrDefault(false)

    /**
     * Installs an idempotent hook on `XMLHttpRequest` and `window.fetch` before MAL's scripts
     * execute so every notification batch returned over XHR/fetch is captured directly in memory.
     */
    internal fun installNetworkHookScript(): String = """
        (function() {
          if (window.__kitsugiMalHook) return;
          var hook = {
            itemsById: Object.create(null),
            itemOrder: [],
            inFlight: 0,
            explicitEnd: false,
            apiUrls: []
          };
          function looksLikeNotif(obj) {
            if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return false;
            var rawId = obj.id;
            if (rawId === null || rawId === undefined || String(rawId).trim() === '') return false;
            return typeof obj.typeIdentifier === 'string' ||
              typeof obj.categoryName === 'string' ||
              typeof obj.createdAtForDisplay === 'string' ||
              (obj.anime && typeof obj.anime === 'object') ||
              Array.isArray(obj.animes) ||
              typeof obj.friendName === 'string' ||
              typeof obj.commentUserName === 'string' ||
              typeof obj.quoteUserName === 'string' ||
              typeof obj.postedUserName === 'string' ||
              typeof obj.senderName === 'string';
          }
          hook.recordItem = function(obj) {
            if (!looksLikeNotif(obj)) return;
            var key = String(obj.id).trim();
            if (!hook.itemsById[key]) hook.itemOrder.push(key);
            hook.itemsById[key] = obj;
          };
          hook.ingestJson = function(data, reqUrl, method) {
            if (!data || typeof data !== 'object') return;
            var foundAny = false;
            if (Array.isArray(data)) {
              for (var i = 0; i < data.length; i++) {
                if (looksLikeNotif(data[i])) { hook.recordItem(data[i]); foundAny = true; }
              }
              if (data.length === 0) hook.explicitEnd = true;
            } else {
              var keys = ['items', 'historyItems', 'data', 'notifications', 'list'];
              for (var k = 0; k < keys.length; k++) {
                var arr = data[keys[k]];
                if (Array.isArray(arr)) {
                  for (var j = 0; j < arr.length; j++) {
                    if (looksLikeNotif(arr[j])) { hook.recordItem(arr[j]); foundAny = true; }
                  }
                  if (keys[k] === 'items' && arr.length === 0 && !Array.isArray(data.historyItems)) {
                    hook.explicitEnd = true;
                  }
                }
              }
              if (data.hasNextPage === false || data.has_next_page === false ||
                  data.hasMore === false || data.has_more === false) {
                hook.explicitEnd = true;
              }
            }
            if (foundAny && reqUrl && (!method || String(method).toUpperCase() === 'GET')) {
              if (hook.apiUrls.indexOf(reqUrl) < 0) hook.apiUrls.push(reqUrl);
            }
          };
          function isTrackedUrl(raw) {
            if (!raw) return false;
            var s = String(raw);
            if (/check-items-as-read|\/notification\/setting/i.test(s)) return false;
            return /\/notification(\b|\/|\?)/i.test(s);
          }
          try {
            var origOpen = XMLHttpRequest.prototype.open;
            var origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
              this.__kitsugiMethod = method;
              this.__kitsugiUrl = url;
              return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
              var xhr = this;
              if (isTrackedUrl(xhr.__kitsugiUrl)) {
                hook.inFlight += 1;
                var settled = false;
                function done() {
                  if (settled) return;
                  settled = true;
                  hook.inFlight = Math.max(0, hook.inFlight - 1);
                  try {
                    var txt = xhr.responseType === '' || xhr.responseType === 'text' ? xhr.responseText : null;
                    if (txt && txt.length > 1) {
                      var parsed = JSON.parse(txt);
                      hook.ingestJson(parsed, xhr.responseURL || xhr.__kitsugiUrl, xhr.__kitsugiMethod);
                    } else if (xhr.responseType === 'json' && xhr.response) {
                      hook.ingestJson(xhr.response, xhr.responseURL || xhr.__kitsugiUrl, xhr.__kitsugiMethod);
                    }
                  } catch (ignored) {}
                }
                xhr.addEventListener('loadend', done);
                xhr.addEventListener('error', done);
                xhr.addEventListener('abort', done);
              }
              return origSend.apply(this, arguments);
            };
          } catch (ignored) {}
          try {
            if (typeof window.fetch === 'function') {
              var origFetch = window.fetch;
              window.fetch = function(input, init) {
                var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                var method = (init && init.method) || (input && input.method) || 'GET';
                var tracked = isTrackedUrl(url);
                if (tracked) hook.inFlight += 1;
                return origFetch.apply(this, arguments).then(function(resp) {
                  if (tracked) {
                    try {
                      resp.clone().text().then(function(txt) {
                        hook.inFlight = Math.max(0, hook.inFlight - 1);
                        try { hook.ingestJson(JSON.parse(txt), resp.url || url, method); } catch (e) {}
                      }, function() {
                        hook.inFlight = Math.max(0, hook.inFlight - 1);
                      });
                    } catch (e) {
                      hook.inFlight = Math.max(0, hook.inFlight - 1);
                    }
                  }
                  return resp;
                }, function(err) {
                  if (tracked) hook.inFlight = Math.max(0, hook.inFlight - 1);
                  throw err;
                });
              };
            }
          } catch (ignored) {}
          window.__kitsugiMalHook = hook;
        })();
    """.trimIndent()

    internal fun scrollAndCollectScript(
        knownIds: Set<String> = emptySet(),
        minUnseenTarget: Int = DEFAULT_MIN_UNSEEN_TARGET
    ): String {
        val knownIdsJson = knownIds.joinToString(prefix = "[", postfix = "]") { id ->
            "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
        val hookBootstrap = installNetworkHookScript()
        return """
        (function() {
          $hookBootstrap
          var hook = window.__kitsugiMalHook;
          var knownSet = new Set($knownIdsJson);
          var minUnseen = $minUnseenTarget;
          var rounds = 0;
          var stableRounds = 0;
          var previousTotal = -1;
          var lastClickRound = -100;
          var maxRounds = $MAX_SCROLL_ROUNDS;
          var waitMs = 950;
          var dollarData = '\x24data';
          var dollarChildren = '\x24children';

          function scanObjectTree(value, depth, seen) {
            if (!value || typeof value !== 'object' || depth > 5) return;
            if (value === window || value === document || value.nodeType) return;
            if (seen.has(value)) return;
            seen.add(value);
            if (Array.isArray(value)) {
              for (var i = 0; i < value.length; i++) {
                var elem = value[i];
                if (elem && typeof elem === 'object') {
                  hook.recordItem(elem);
                  if (depth < 3) scanObjectTree(elem, depth + 1, seen);
                }
              }
              return;
            }
            hook.recordItem(value);
            var keys;
            try { keys = Object.keys(value); } catch (e) { return; }
            for (var k = 0; k < keys.length; k++) {
              var prop = keys[k];
              if (prop.charAt(0) === '_' && prop !== '_data' && prop !== '_setupState') continue;
              if (prop.charAt(0) === '\x24') continue;
              var child;
              try { child = value[prop]; } catch (e) { continue; }
              if (child && typeof child === 'object') {
                scanObjectTree(child, depth + 1, seen);
              }
            }
          }

          function harvestAllSources() {
            var feed = window.MAL && window.MAL.notification && typeof window.MAL.notification === 'object'
              ? window.MAL.notification : null;
            if (feed) {
              hook.ingestJson(feed, null, 'GET');
            }
            var seen = typeof WeakSet === 'function' ? new WeakSet() : {
              list: [],
              has: function(v) { return this.list.indexOf(v) >= 0; },
              add: function(v) { this.list.push(v); }
            };
            var roots = document.querySelectorAll('#content, #content div, #content section, body.notification > div');
            for (var i = 0; i < roots.length; i++) {
              var el = roots[i];
              var vm = el.__vue__;
              if (vm && !seen.has(vm)) {
                seen.add(vm);
                if (vm[dollarData]) scanObjectTree(vm[dollarData], 0, seen);
                if (vm._data) scanObjectTree(vm._data, 0, seen);
                var kids = vm[dollarChildren];
                if (Array.isArray(kids)) {
                  for (var c = 0; c < kids.length; c++) {
                    if (kids[c] && kids[c][dollarData]) scanObjectTree(kids[c][dollarData], 0, seen);
                  }
                }
              }
              var v3 = el.__vueParentComponent;
              if (v3 && !seen.has(v3)) {
                seen.add(v3);
                if (v3.data) scanObjectTree(v3.data, 0, seen);
                if (v3.setupState) scanObjectTree(v3.setupState, 0, seen);
              }
            }
          }

          function domItemCount() {
            return document.querySelectorAll(
              'body.notification li.notification-item:not(.empty), .notification-section li.notification-item:not(.empty)'
            ).length;
          }

          function metrics() {
            harvestAllSources();
            var ids = hook.itemOrder;
            var unseen = 0;
            for (var i = 0; i < ids.length; i++) {
              if (!knownSet.has(ids[i])) unseen += 1;
            }
            var domCount = domItemCount();
            var effectiveTotal = Math.max(ids.length, domCount);
            return { total: effectiveTotal, jsonCount: ids.length, domCount: domCount, unseen: unseen };
          }

          function ownerHref() {
            var anchors = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
            var owner = anchors.find(function(anchor) {
              return String(anchor.className || '').toLowerCase().indexOf('header-profile') >= 0 &&
                /\/profile\//i.test(anchor.href || '');
            });
            return owner ? owner.href : null;
          }

          function isSignedOut() {
            return /login/i.test(window.location.pathname || '') ||
              !!document.querySelector('form#loginForm, form[name="loginForm"], form[action*="login.php"]');
          }

          function buildEnvelope(mayHaveMore) {
            harvestAllSources();
            var collected = hook.itemOrder.map(function(id) { return hook.itemsById[id]; });
            var contentRoot = document.querySelector('#content') || document.body;
            return JSON.stringify({
              notification: { items: collected },
              contentHtml: contentRoot ? contentRoot.innerHTML : '',
              ownerHref: ownerHref(),
              url: window.location.href,
              signedOut: isSignedOut(),
              mayHaveMore: mayHaveMore,
              apiUrls: hook.apiUrls,
              rounds: rounds
            });
          }

          function isInsideExcludedChrome(el) {
            return !!el.closest(
              '#headerSmall, header, .header-notification, .header-notification-dropdown, ' +
              '.notificaiton-category-nav, .notification-category-nav, .notification-header, ' +
              '.notification-item, .header-notification-item'
            );
          }

          function isVisibleEnabled(el) {
            if (!el || el.disabled || el.getAttribute('aria-disabled') === 'true') return false;
            if (el.getAttribute('aria-hidden') === 'true') return false;
            var cls = ' ' + String(el.className || '').toLowerCase() + ' ';
            if (/\s(disabled|loading)\s/.test(cls)) return false;
            var cur = el;
            while (cur && cur.nodeType === 1) {
              var st = window.getComputedStyle(cur);
              if (st && (st.display === 'none' || st.visibility === 'hidden')) return false;
              cur = cur.parentElement;
            }
            return true;
          }

          function matchesMoreText(text) {
            if (!text || text.length < 3 || text.length > 48) return false;
            return /^(view\s*more|load\s*more|show\s*more|see\s*more|more(\s+notifications?)?|older(\s+notifications?)?|next(\s+page)?|daha\s*(fazla|eski)|devam(ını\s*gör)?)(\s*\(\d+\+?\))?$/i.test(text);
          }

          function findNextControl() {
            // Priority 1: Official MAL `.notification-pagination` container.
            var pagers = Array.prototype.slice.call(document.querySelectorAll('.notification-pagination, .pagination'));
            for (var p = 0; p < pagers.length; p++) {
              var pager = pagers[p];
              if (!isVisibleEnabled(pager)) continue;
              var activeEl = Array.prototype.slice.call(pager.children).find(function(ch) {
                var tag = String(ch.tagName || '').toLowerCase();
                var cls = String(ch.className || '').toLowerCase();
                return /^\d+$/.test((ch.textContent || '').trim()) &&
                  (tag === 'span' || /\b(active|current|on)\b/.test(cls));
              });
              if (activeEl) {
                var nextPageStr = String(parseInt(activeEl.textContent.trim(), 10) + 1);
                var numTarget = Array.prototype.slice.call(pager.querySelectorAll('a, button, [role="button"]')).find(function(c) {
                  return isVisibleEnabled(c) && (c.textContent || '').trim() === nextPageStr;
                });
                if (numTarget) return numTarget;
              }
              var pagerControls = Array.prototype.slice.call(
                pager.querySelectorAll('a, button, [role="button"], span, div')
              ).filter(function(c) {
                if (!isVisibleEnabled(c)) return false;
                var cls = String(c.className || '').toLowerCase();
                if (/\b(active|current|on)\b/.test(cls)) return false;
                var txt = (c.innerText || c.textContent || '').replace(/\s+/g, ' ').trim();
                if (activeEl && /^\d+$/.test(txt) && parseInt(txt, 10) <= parseInt(activeEl.textContent.trim(), 10)) {
                  return false;
                }
                return matchesMoreText(txt) || /^(next|more|[›»>])$/i.test(txt) ||
                  (/^(a|button)$/i.test(c.tagName || '') && txt.length > 0 && txt.length <= 48);
              });
              // Pick deepest leaf control inside `.notification-pagination`.
              pagerControls = pagerControls.filter(function(c) {
                return !pagerControls.some(function(other) { return other !== c && c.contains(other); });
              });
              if (pagerControls.length > 0) return pagerControls[0];
            }

            // Priority 2: Leaf "View more" / "Load more" control anywhere in `#content` outside cards/header.
            var scope = document.querySelector('#content') || document.body;
            if (!scope) return null;
            var raw = Array.prototype.slice.call(scope.querySelectorAll(
              'button, a, span, div, [role="button"], [data-action*="more"], [class*="more"]'
            )).filter(function(el) {
              if (isInsideExcludedChrome(el) || !isVisibleEnabled(el)) return false;
              var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || '')
                .replace(/\s+/g, ' ').trim();
              return matchesMoreText(txt);
            });
            var leaves = raw.filter(function(el) {
              return !raw.some(function(other) { return other !== el && el.contains(other); });
            });
            return leaves.length > 0 ? leaves[0] : null;
          }

          function willNavigatePage(el) {
            if (!el) return false;
            var anchor = String(el.tagName || '').toLowerCase() === 'a' ? el : el.closest('a[href]');
            if (!anchor) return false;
            var rawHref = anchor.getAttribute('href') || '';
            if (!rawHref || rawHref.charAt(0) === '#' || /^javascript:/i.test(rawHref)) return false;
            return anchor.href && anchor.href !== window.location.href;
          }

          function triggerControl(button) {
            if (!button || button.__kitsugiLoadPending) return false;
            button.__kitsugiLoadPending = true;
            if (willNavigatePage(button)) {
              try { window.$BRIDGE_NAME.onSnapshot(buildEnvelope(true)); } catch (e) {}
            }
            try { button.scrollIntoView({ block: 'center', inline: 'nearest' }); } catch (e) {}
            var events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];
            for (var i = 0; i < events.length; i++) {
              try {
                button.dispatchEvent(new MouseEvent(events[i], { bubbles: true, cancelable: true, view: window }));
              } catch (e) {}
            }
            try { button.click(); } catch (e) {}
            window.setTimeout(function() { button.__kitsugiLoadPending = false; }, waitMs * 3);
            return true;
          }

          function scrollTargets() {
            var root = document.scrollingElement || document.documentElement;
            var nodes = [root].concat(Array.prototype.slice.call(document.querySelectorAll('#content, #content *')));
            return nodes.filter(function(node) {
              if (!node || !node.scrollHeight || node.clientHeight <= 0 || node.scrollHeight <= node.clientHeight + 60) return false;
              if (node === root) return true;
              var style = window.getComputedStyle(node);
              return /(auto|scroll)/i.test(style.overflowY || '');
            }).slice(0, 4);
          }

          function finishCollect(mayHaveMore) {
            window.$BRIDGE_NAME.onResult(buildEnvelope(mayHaveMore));
          }

          function step() {
            rounds += 1;
            scrollTargets().forEach(function(target) {
              target.scrollTop = target.scrollHeight;
              target.dispatchEvent(new Event('scroll', { bubbles: true }));
            });
            window.scrollTo(0, Math.max(document.body ? document.body.scrollHeight : 0, document.documentElement.scrollHeight));
            window.dispatchEvent(new Event('scroll'));
            document.dispatchEvent(new Event('scroll', { bubbles: true }));

            var controlBefore = findNextControl();
            var clicked = false;
            if (controlBefore && hook.inFlight === 0) {
              clicked = triggerControl(controlBefore);
              if (clicked) lastClickRound = rounds;
            }

            window.setTimeout(function() {
              var snap = metrics();
              if (snap.total > previousTotal) {
                previousTotal = snap.total;
                stableRounds = 0;
                var nextAfter = findNextControl();
                if (nextAfter) nextAfter.__kitsugiLoadPending = false;
              } else if (hook.inFlight > 0) {
                stableRounds = 0;
              } else {
                stableRounds += 1;
              }

              var nextControlNow = findNextControl();
              if (hook.explicitEnd && hook.inFlight === 0) {
                finishCollect(false);
                return;
              }
              if (snap.unseen >= minUnseen && hook.inFlight === 0) {
                finishCollect(!!nextControlNow && !hook.explicitEnd);
                return;
              }
              if (hook.inFlight === 0 && !nextControlNow && stableRounds >= (snap.unseen > 0 ? 1 : 2)) {
                finishCollect(false);
                return;
              }
              if (stableRounds >= 4 || rounds >= maxRounds) {
                finishCollect(!hook.explicitEnd && (!!nextControlNow || hook.inFlight > 0));
              } else {
                step();
              }
            }, waitMs);
          }

          function waitForInitial(attempts) {
            var snap = metrics();
            if (isSignedOut()) {
              finishCollect(false);
              return;
            }
            if (snap.total > 0 || findNextControl() || attempts <= 0) {
              previousTotal = snap.total;
              if (snap.unseen >= minUnseen && hook.inFlight === 0) {
                finishCollect(!!findNextControl() && !hook.explicitEnd);
              } else {
                step();
              }
              return;
            }
            window.setTimeout(function() { waitForInitial(attempts - 1); }, 250);
          }

          window.setTimeout(function() { waitForInitial(16); }, 350);
        })();
        """.trimIndent()
    }
}

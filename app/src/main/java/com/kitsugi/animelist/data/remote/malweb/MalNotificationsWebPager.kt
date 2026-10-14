package com.kitsugi.animelist.data.remote.malweb

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MAL's notification page has no published page/offset contract. This small, temporary
 * first-party WebView lets MAL's own frontend run its continuation code when the native list
 * asks for older notifications, then returns the updated `window.MAL.notification` payload.
 *
 * The WebView shares Android's normal CookieManager with the unified MAL login. It never gets
 * the OAuth bearer token, refuses external top-level navigation, and blocks MAL's known
 * notification read-marking route so merely loading this hidden page cannot consume unread
 * notifications.
 */
internal object MalNotificationsWebPager {
    private const val NOTIFICATIONS_URL = "https://myanimelist.net/notification"
    private const val BRIDGE_NAME = "KitsugiMalNotifications"
    private const val TIMEOUT_MS = 22_000L
    private const val MAX_SCROLL_ROUNDS = 14

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(context: Context, page: Int): MalNotificationsClient.Feed =
        withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<MalNotificationsClient.Feed> { continuation ->
                    val session = FetchSession(context.applicationContext, page, continuation)
                    continuation.invokeOnCancellation { session.cancel() }
                    session.start()
                }
            } ?: throw MalNotificationsFetchException("MAL bildirimlerinin devamı zaman aşımına uğradı")
        }

    private class FetchSession(
        private val context: Context,
        private val page: Int,
        private val continuation: Continuation<MalNotificationsClient.Feed>
    ) : WebViewClient() {
        private val handler = Handler(Looper.getMainLooper())
        private val completed = AtomicBoolean(false)
        private var probedNotificationUrl: String? = null
        private var webView: WebView? = null

        private val bridge = object {
            @JavascriptInterface
            fun onResult(result: String?) {
                if (result == null || completed.get()) return
                handler.post { handleResult(result) }
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
                    addJavascriptInterface(bridge, MalNotificationsWebPager.BRIDGE_NAME)
                    webViewClient = this@FetchSession
                }
                webView = created
                created.loadUrl(NOTIFICATIONS_URL)
            } catch (error: Exception) {
                finish(error = MalNotificationsFetchException("MAL web bildirim sayfası açılamadı"))
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !MalNotificationsWebPager.isFirstPartyHttps(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url
            val isMarkReadRequest = MalNotificationsWebPager.isFirstPartyHttps(uri.toString()) &&
                uri.path.equals("/notification/api/check-items-as-read.json", ignoreCase = true)
            if (!isMarkReadRequest) return null

            // The website may mark an item as read when its page is opened. This endpoint is
            // deliberately made a no-op; the native notification list preserves MAL's read state.
            return WebResourceResponse(
                "application/json",
                "UTF-8",
                200,
                "OK",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("{\"result\":false}".toByteArray(Charsets.UTF_8))
            )
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            val currentUrl = view.url ?: url
            if (!MalNotificationsWebPager.isFirstPartyHttps(currentUrl) || !MalNotificationsWebPager.isNotificationOrLoginPage(currentUrl)) return
            if (probedNotificationUrl == currentUrl) return
            probedNotificationUrl = currentUrl
            view.evaluateJavascript(MalNotificationsWebPager.scrollAndCollectScript(), null)
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

        private fun handleResult(rawResult: String) {
            if (completed.get()) return
            val envelope = runCatching { MalNotificationsWebPager.json.parseToJsonElement(rawResult).jsonObject }.getOrNull()
                ?: return finish(error = MalNotificationsParseException("MAL web bildirim yanıtı geçersiz JSON"))

            val currentUrl = envelope.string("url").orEmpty()
            if (envelope.boolean("signedOut") == true || MalNotificationsWebPager.isLoginUrl(currentUrl)) {
                return finish(error = MalWebSessionRequiredException("MAL web oturumu sona ermiş"))
            }

            val payload = envelope["notification"] as? JsonObject
                ?: return finish(error = MalNotificationsParseException("MAL bildirim verisi web sayfasında bulunamadı"))
            val owner = MalNotificationsClient.parseSessionUsernameFromProfileUrl(envelope.string("ownerHref"))
            val feed = MalNotificationsClient.parseNotificationPayload(
                payload = payload.toString(),
                page = page,
                sessionUsername = owner,
                hasNextPageOverride = envelope.boolean("mayHaveMore")
            ) ?: return finish(error = MalNotificationsParseException("MAL bildirim verisi ayrıştırılamadı"))

            finish(value = feed)
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
            runCatching { current.removeJavascriptInterface(MalNotificationsWebPager.BRIDGE_NAME) }
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

    private fun scrollAndCollectScript(): String = """
        (function() {
          var rounds = 0;
          var stableRounds = 0;
          var previousCount = -1;
          var maxRounds = $MAX_SCROLL_ROUNDS;
          var waitMs = 1100;

          function currentFeed() {
            return window.MAL && window.MAL.notification && typeof window.MAL.notification === 'object'
              ? window.MAL.notification : null;
          }
          function itemCount(feed) {
            if (!feed) return 0;
            var items = Array.isArray(feed.items) ? feed.items : [];
            var history = Array.isArray(feed.historyItems) ? feed.historyItems : [];
            var ids = items.concat(history).map(function(item) {
              return item && item.id != null ? String(item.id) : null;
            }).filter(function(id) { return id !== null; });
            return new Set(ids).size;
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
          function collect(mayHaveMore) {
            var feed = currentFeed();
            window.$BRIDGE_NAME.onResult(JSON.stringify({
              notification: feed,
              ownerHref: ownerHref(),
              url: window.location.href,
              signedOut: isSignedOut(),
              mayHaveMore: mayHaveMore,
              rounds: rounds
            }));
          }
          function scrollTargets() {
            var root = document.scrollingElement || document.documentElement;
            var nodes = [root].concat(Array.prototype.slice.call(document.querySelectorAll('body *')));
            return nodes.filter(function(node) {
              if (!node || !node.scrollHeight || node.clientHeight <= 0 || node.scrollHeight <= node.clientHeight + 80) return false;
              if (node === root) return true;
              var style = window.getComputedStyle(node);
              return /(auto|scroll)/i.test(style.overflowY || '') || node.scrollHeight > node.clientHeight + 250;
            }).sort(function(a, b) {
              return (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight);
            }).slice(0, 4);
          }
          function clickLoadMore() {
            var controls = Array.prototype.slice.call(document.querySelectorAll(
              '[data-action*="more"], [class*="load-more"], [class*="show-more"], button, a[role="button"]'
            ));
            var button = controls.find(function(control) {
              var label = ((control.innerText || control.getAttribute('aria-label') || '') + ' ' +
                (control.className || '')).toLowerCase();
              return /load\s*more|show\s*more|older|daha\s*(fazla|eski)/i.test(label) && !control.disabled;
            });
            if (button && !button.disabled && !button.__kitsugiLoadPending) {
              button.__kitsugiLoadPending = true;
              button.click();
            }
          }
          function step() {
            var feed = currentFeed();
            rounds += 1;
            var targets = scrollTargets();
            targets.forEach(function(target) {
              target.scrollTop = target.scrollHeight;
              target.dispatchEvent(new Event('scroll', { bubbles: true }));
            });
            window.scrollTo(0, Math.max(document.body.scrollHeight, document.documentElement.scrollHeight));
            window.dispatchEvent(new Event('scroll'));
            document.dispatchEvent(new Event('scroll', { bubbles: true }));
            clickLoadMore();

            window.setTimeout(function() {
              var count = itemCount(currentFeed());
              if (count > previousCount) {
                previousCount = count;
                stableRounds = 0;
                Array.prototype.forEach.call(document.querySelectorAll('button, a[role="button"]'), function(control) {
                  control.__kitsugiLoadPending = false;
                });
              } else {
                stableRounds += 1;
              }
              if (stableRounds >= 3 || rounds >= maxRounds) {
                collect(rounds >= maxRounds && stableRounds < 3);
              } else {
                step();
              }
            }, waitMs);
          }

          function waitForFeed(attempts) {
            var feed = currentFeed();
            if (feed || isSignedOut() || attempts <= 0) {
              previousCount = itemCount(feed);
              if (!feed && isSignedOut()) collect(false);
              else if (!feed) collect(false);
              else step();
              return;
            }
            window.setTimeout(function() { waitForFeed(attempts - 1); }, 250);
          }
          window.setTimeout(function() { waitForFeed(16); }, 450);
        })();
    """.trimIndent()
}

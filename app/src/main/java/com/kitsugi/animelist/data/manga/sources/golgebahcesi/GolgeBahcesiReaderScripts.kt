package com.kitsugi.animelist.data.manga.sources.golgebahcesi

import org.json.JSONObject

/**
 * Gölge Bahçesi şifreli (`deliverySystem == "secure"`, `.png.enc`/`.jpg.enc`) okuyucu
 * sayfaları için `DOCUMENT_START` JavaScript kancası üretir.
 *
 * Tasarım ilkeleri:
 * 1. Sayfa içi Blob Web Worker oluşturulurken şifre çözme çıktısını (`createImageBitmap(blob)`)
 *    yakalayan `workerHook` betiğini Worker kodunun başına enjekte eder.
 * 2. `IntersectionObserver` vekili kurarak öncelik kuyruğuna (kullanıcının o an açtığı sayfa)
 *    ve kayan pencereye (`BATCH_SIZE = 4`) göre `[data-reader-page]` düğümlerini görünür işaretler;
 *    her çözülen sayfayı tüm bölümü beklemeden anında Kotlin disk önbelleğine akıtır.
 * 3. Cloudflare Turnstile etkileşimli kutucuk bekliyorsa `need_interaction` sinyali gönderir;
 *    Worker sayfa sayısını bildirdiği anda (`count > 0`) doğrulama ekranı otomatik kapanır.
 */
object GolgeBahcesiReaderScripts {

    private const val BATCH_SIZE = 4

    fun randomJsIdentifier(length: Int = 14): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        return buildString(length) {
            repeat(length) {
                append(chars.random())
            }
        }
    }

    fun buildWorkerHookScript(channelName: String): String {
        val quotedChannel = JSONObject.quote(channelName)
        return """
            (() => {
                const channel = new BroadcastChannel($quotedChannel);
                const lengths = new Map();
                let urls = [];

                function matchUrlIndex(responseUrl) {
                    if (!responseUrl || !urls.length) return -1;
                    const exact = urls.indexOf(responseUrl);
                    if (exact !== -1) return exact;
                    try {
                        const targetPath = new URL(responseUrl, self.location.href).pathname;
                        for (let i = 0; i < urls.length; i++) {
                            const u = urls[i];
                            if (!u) continue;
                            if (u === targetPath || u.endsWith(targetPath) || responseUrl.endsWith(u)) {
                                return i;
                            }
                        }
                    } catch (_) {}
                    return -1;
                }

                self.addEventListener("message", (event) => {
                    if (event.data && event.data.type === "store" && Array.isArray(event.data.urls)) {
                        urls = event.data.urls;
                        channel.postMessage({ count: urls.length });
                    }
                });

                const origArrayBuffer = Response.prototype.arrayBuffer;
                Response.prototype.arrayBuffer = function () {
                    const index = matchUrlIndex(this.url);
                    return origArrayBuffer.call(this).then((buffer) => {
                        if (index !== -1) {
                            lengths.set(index, buffer.byteLength);
                        }
                        return buffer;
                    });
                };

                const origCreateImageBitmap = self.createImageBitmap;
                self.createImageBitmap = function (image, ...args) {
                    if (image instanceof Blob) {
                        for (const [index, length] of lengths) {
                            const diff = length - image.size;
                            if (diff === 28 || diff === 0) {
                                lengths.delete(index);
                                channel.postMessage({ index, image });
                                break;
                            }
                        }
                    }
                    return origCreateImageBitmap.call(this, image, ...args);
                };
            })();
        """.trimIndent()
    }

    fun buildDocumentStartScript(bridgeName: String, channelName: String): String {
        val workerHookLiteral = JSONObject.quote(buildWorkerHookScript(channelName) + "\n")
        val quotedBridge = JSONObject.quote(bridgeName)
        val quotedChannel = JSONObject.quote(channelName)

        return """
            (() => {
                const bridgeName = $quotedBridge;
                const channelName = $quotedChannel;
                const workerHook = $workerHookLiteral;
                const BATCH_SIZE = $BATCH_SIZE;

                const observers = [];
                const visibleSince = new Map();
                const captured = new Set();
                const priorityQueue = [];
                let count = 0;
                let pendingBridgePages = 0;
                let completeSent = false;
                let interactionRequested = false;
                const startedAt = Date.now();

                function sendBridge(payload) {
                    try {
                        const b = window[bridgeName];
                        if (b && typeof b.postMessage === "function") {
                            b.postMessage(JSON.stringify(payload));
                        }
                    } catch (_) {}
                }

                function attachReplyListener() {
                    try {
                        const b = window[bridgeName];
                        if (!b || b.__kitsugiBound) return;
                        b.__kitsugiBound = true;
                        b.onmessage = (event) => {
                            const msg = String((event && event.data) || "");
                            if (msg.startsWith("req:")) {
                                const idx = Number(msg.slice(4));
                                if (Number.isInteger(idx) && idx >= 0 && !captured.has(idx)) {
                                    for (let offset = 2; offset >= 0; offset--) {
                                        const candidate = idx + offset;
                                        if (!captured.has(candidate)) {
                                            const existingPos = priorityQueue.indexOf(candidate);
                                            if (existingPos !== -1) priorityQueue.splice(existingPos, 1);
                                            priorityQueue.unshift(candidate);
                                        }
                                    }
                                    pumpVisiblePages();
                                }
                            }
                        };
                    } catch (_) {}
                }

                try {
                    localStorage.setItem("rdr_diag_ovr", JSON.stringify({ prefetch: false }));
                } catch (_) {}

                const OriginalBlob = window.Blob;
                window.Blob = new Proxy(OriginalBlob, {
                    construct(target, [parts, options], newTarget) {
                        const mime = String((options && options.type) || "").toLowerCase();
                        const nextParts = mime.includes("javascript") && Array.isArray(parts)
                            ? [workerHook, ...parts]
                            : parts;
                        return Reflect.construct(target, [nextParts, options], newTarget);
                    },
                });

                const OriginalObserver = window.IntersectionObserver;
                if (typeof OriginalObserver === "function") {
                    window.IntersectionObserver = new Proxy(OriginalObserver, {
                        construct(target, [callback, options], newTarget) {
                            const instance = Reflect.construct(target, [callback, options], newTarget);
                            const targets = new Set();
                            const observer = new Proxy(instance, {
                                get(obj, prop) {
                                    if (prop === "observe") {
                                        return (el) => {
                                            targets.add(el);
                                            return obj.observe(el);
                                        };
                                    }
                                    if (prop === "unobserve") {
                                        return (el) => {
                                            targets.delete(el);
                                            return obj.unobserve(el);
                                        };
                                    }
                                    if (prop === "disconnect") {
                                        return () => {
                                            targets.clear();
                                            return obj.disconnect();
                                        };
                                    }
                                    const value = Reflect.get(obj, prop);
                                    return typeof value === "function" ? value.bind(obj) : value;
                                },
                            });
                            observers.push({ callback, observer, targets });
                            return observer;
                        },
                    });
                }

                function setVisible(element, isIntersecting) {
                    if (!element) return;
                    const rect = element.getBoundingClientRect();
                    const entry = {
                        time: performance.now(),
                        target: element,
                        isIntersecting,
                        intersectionRatio: isIntersecting ? 1 : 0,
                        boundingClientRect: rect,
                        intersectionRect: isIntersecting ? rect : new DOMRectReadOnly(),
                        rootBounds: null,
                    };
                    for (const item of observers) {
                        if (item.targets.has(element)) {
                            try {
                                item.callback([entry], item.observer);
                            } catch (_) {}
                        }
                    }
                }

                function pickWantedIndices() {
                    const wanted = [];
                    // Halen uçuşta olan görünür sayfaları koru (10 sn dolmadıysa)
                    const now = Date.now();
                    for (const [idx, ts] of visibleSince) {
                        if (!captured.has(idx) && idx < count && now - ts < 10000 && wanted.length < BATCH_SIZE) {
                            wanted.push(idx);
                        }
                    }
                    // Kullanıcının o an açtığı öncelikli sayfalar
                    while (priorityQueue.length > 0 && wanted.length < BATCH_SIZE) {
                        const reqIdx = priorityQueue.shift();
                        if (reqIdx >= 0 && reqIdx < count && !captured.has(reqIdx) && !wanted.includes(reqIdx)) {
                            wanted.push(reqIdx);
                        }
                    }
                    // Kalan kapasiteyi baştan sırayla doldur
                    for (let i = 0; i < count && wanted.length < BATCH_SIZE; i++) {
                        if (!captured.has(i) && !wanted.includes(i)) {
                            wanted.push(i);
                        }
                    }
                    return wanted;
                }

                function maybeSendComplete() {
                    if (!completeSent && count > 0 && captured.size >= count && pendingBridgePages === 0) {
                        completeSent = true;
                        try { localStorage.removeItem("rdr_diag_ovr"); } catch (_) {}
                        sendBridge({ type: "complete", count });
                    }
                }

                function pumpVisiblePages() {
                    attachReplyListener();
                    if (!count) return;
                    if (captured.size >= count) {
                        maybeSendComplete();
                        return;
                    }

                    const pages = document.querySelectorAll("[data-reader-page]");
                    if (!pages || pages.length === 0) return;

                    const wanted = pickWantedIndices();
                    if (wanted.length === 0) return;

                    for (const idx of [...visibleSince.keys()]) {
                        if (!wanted.includes(idx)) {
                            if (pages[idx]) setVisible(pages[idx], false);
                            visibleSince.delete(idx);
                        }
                    }

                    const now = Date.now();
                    for (const idx of wanted) {
                        if (now - (visibleSince.get(idx) || 0) < 10000) continue;
                        const el = pages[idx];
                        if (!el) continue;
                        // Yalnızca en az bir IntersectionObserver bu düğümü izlemeye başladıysa işaretle
                        let observed = false;
                        for (const item of observers) {
                            if (item.targets.has(el)) {
                                observed = true;
                                break;
                            }
                        }
                        if (!observed) continue;
                        setVisible(el, true);
                        visibleSince.set(idx, now);
                    }
                }

                function detectTurnstileIfBlocked() {
                    if (count > 0 || interactionRequested) return;
                    const elapsed = Date.now() - startedAt;
                    if (elapsed < 4500) return;
                    const hasChallengeFrame = !!document.querySelector(
                        'iframe[src*="challenges.cloudflare.com"], #cf-turnstile, [data-sitekey]'
                    );
                    const bodyText = (document.body && document.body.innerText) || "";
                    const hasSecurityGate = bodyText.includes("Güvenlik Doğrulaması") ||
                        bodyText.includes("Turnstile") ||
                        bodyText.includes("doğrulamayı tamamlayın");
                    if (hasChallengeFrame || hasSecurityGate) {
                        interactionRequested = true;
                        sendBridge({ type: "need_interaction" });
                    }
                }

                function blobToBase64(blob) {
                    return new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onload = () => {
                            const result = String(reader.result || "");
                            const comma = result.indexOf(",");
                            resolve(comma >= 0 ? result.slice(comma + 1) : result);
                        };
                        reader.onerror = () => reject(reader.error || new Error("Blob okunamadı"));
                        reader.readAsDataURL(blob);
                    });
                }

                const channel = new BroadcastChannel(channelName);
                channel.onmessage = async (event) => {
                    const data = event.data || {};
                    if (typeof data.count === "number" && data.count > 0) {
                        count = data.count;
                        sendBridge({ type: "count", count });
                        pumpVisiblePages();
                        maybeSendComplete();
                        return;
                    }

                    const idx = data.index;
                    const blob = data.image;
                    if (!Number.isInteger(idx) || !(blob instanceof Blob) || captured.has(idx)) {
                        return;
                    }

                    captured.add(idx);
                    pendingBridgePages++;
                    try { localStorage.removeItem("rdr_diag_ovr"); } catch (_) {}
                    // Bir sonraki sayfayı beklemeden 0ms gecikmeyle pencereyi kaydır
                    pumpVisiblePages();

                    try {
                        const base64 = await blobToBase64(blob);
                        sendBridge({ type: "page", index: idx, data: base64 });
                    } catch (err) {
                        sendBridge({
                            type: "page_error",
                            index: idx,
                            message: String((err && err.message) || err || "decode_failed"),
                        });
                    } finally {
                        pendingBridgePages = Math.max(0, pendingBridgePages - 1);
                        maybeSendComplete();
                    }
                };

                const pollTimer = setInterval(() => {
                    attachReplyListener();
                    if (count > 0 && captured.size >= count) {
                        clearInterval(pollTimer);
                        return;
                    }
                    detectTurnstileIfBlocked();
                    pumpVisiblePages();
                }, 250);
            })();
        """.trimIndent()
    }
}

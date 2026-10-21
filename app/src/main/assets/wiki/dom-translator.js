/* Kitsugi original-page translator. Authored for this app; no remote script or native bridge.
 * Changes Text.nodeValue only. Never replaces elements, attributes, links or event handlers.
 */
(function () {
    'use strict';
    if (window.__kitsugiWikiDomV1) return;
    var docId = '', target = '', origin = '', enabled = false, root = null, walker = null;
    var rescan = true, serial = 0, storedChars = 0, limited = false, lastInput = -1000;
    var records = new Map(), byNode = new WeakMap(), observer = null;
    var MAX_NODES = 6000, MAX_CHARS = 1000000;
    var letters;
    try { letters = new RegExp('\\p{L}', 'u'); } catch (_) { letters = /[A-Za-z\u00C0-\u02AF\u0400-\u052F\u0600-\u06FF\u3040-\u30FF\u3400-\u9FFF\uAC00-\uD7AF]/; }
    var sensitive = 'form,textarea,input,select,option,button,[role="textbox"],[contenteditable]:not([contenteditable="false"])';
    var skip = 'script,style,noscript,textarea,input,select,option,button,form,pre,code,kbd,samp,svg,math,iframe,' +
        '[contenteditable]:not([contenteditable="false"]),[role="textbox"],[translate="no"],.notranslate,[hidden],[aria-hidden="true"],' +
        '.mw-editsection,.reference,.mw-cite-backlink,.countdown,.countdown-timer,[role="timer"],[aria-live],.wikia-ad,.gpt-ad,.ad-slot';
    function findRoot() {
        return document.querySelector('#mw-content-text .mw-parser-output') ||
            document.querySelector('.mw-parser-output') || document.querySelector('#mw-content-text');
    }
    function input() { lastInput = performance.now(); rescan = true; }
    window.addEventListener('wheel', input, { passive: true });
    window.addEventListener('touchmove', input, { passive: true });
    window.addEventListener('keydown', input, { passive: true });
    window.addEventListener('scroll', function () { rescan = true; }, { passive: true });
    function anchor() {
        var x = Math.min(window.innerWidth - 1, Math.max(1, window.innerWidth / 2));
        var y = Math.min(window.innerHeight - 1, Math.max(1, window.innerHeight / 3));
        var el = document.elementFromPoint ? document.elementFromPoint(x, y) : null;
        el = el && el.closest('p,li,td,th,h1,h2,h3,h4,h5,h6,figcaption,section,div');
        if (!el || !root || !root.contains(el)) return null;
        return { el: el, top: el.getBoundingClientRect().top, input: lastInput, x: window.scrollX, y: window.scrollY };
    }
    function mutate(work) {
        var saved = anchor();
        work();
        if (saved) requestAnimationFrame(function () {
            // Never fight a gesture or a scroll that occurred after this transaction.
            if (saved.input !== lastInput || !saved.el.isConnected ||
                Math.abs(window.scrollY - saved.y) > window.innerHeight) return;
            var delta = saved.el.getBoundingClientRect().top - saved.top;
            if (Math.abs(delta) > 1) window.scrollBy(0, delta);
        });
    }
    function changed(r) {
        if (r.node.nodeValue === r.rendered) return false;
        storedChars += r.node.nodeValue.length - r.original.length;
        r.original = r.node.nodeValue;
        r.rendered = r.original;
        r.translated = null;
        r.pending = false;
        r.attempts = 0;
        r.retryAt = 0;
        r.rev++;
        return true;
    }
    function write(r, text) {
        if (!r.node.isConnected || !root || !root.contains(r.node) ||
            !r.node.parentElement || r.node.parentElement.closest(sensitive) || changed(r)) return;
        r.rendered = text;
        r.node.nodeValue = text;
    }
    function restore() {
        mutate(function () {
            records.forEach(function (r) { write(r, r.original); r.pending = false; });
        });
    }
    function observe() {
        var next = findRoot();
        if (next === root) return;
        if (observer) observer.disconnect();
        root = next;
        walker = null;
        rescan = true;
        if (!root) return;
        observer = new MutationObserver(function (mutations) {
            mutations.forEach(function (m) {
                if (m.type === 'characterData') {
                    var r = byNode.get(m.target);
                    if (r && m.target.nodeValue === r.rendered) return; // our own write
                    if (r) changed(r);
                }
                rescan = true;
            });
        });
        observer.observe(root, { childList: true, characterData: true, subtree: true, attributes: true,
            attributeFilter: ['class', 'style', 'hidden', 'aria-hidden', 'translate', 'lang'] });
    }
    function language(el) {
        var tagged = el.closest('[lang]');
        return (tagged ? tagged.getAttribute('lang') : document.documentElement.lang || '').toLowerCase().split('-')[0];
    }
    function eligible(node) {
        var el = node.parentElement;
        if (!el || !node.isConnected || el.closest(skip)) return false;
        var text = node.nodeValue.trim();
        // Preserve letter-tile headings, counters, URLs and very large single nodes intact.
        if (text.length < 2 || text.length > 8000 || !letters.test(text) || /^(?:https?:\/\/|www\.)\S+$/i.test(text)) return false;
        if (language(el) === target.toLowerCase().split('-')[0]) return false;
        if (el.getClientRects().length === 0) return false;
        // Ancestor opacity/visibility and CSS-hidden alternate templates must not be sent.
        for (var parent = el; parent; parent = parent.parentElement) {
            var css = getComputedStyle(parent);
            if (css.display === 'none' || css.visibility === 'hidden' || css.opacity === '0') return false;
            if (parent === root) break;
        }
        var box = el.getBoundingClientRect();
        return box.bottom >= -window.innerHeight && box.top <= window.innerHeight * 3;
    }
    function prune() {
        var removed = false;
        records.forEach(function (r, id) {
            if (!r.node.isConnected || !root || !root.contains(r.node) || r.original.length > 8000 || storedChars > MAX_CHARS) {
                // Sites may recycle detached nodes. Do not let our translated string
                // become the new source text when that same node is mounted again.
                if (r.node.nodeValue === r.rendered) r.node.nodeValue = r.original;
                storedChars -= r.original.length;
                removed = true;
                records.delete(id);
                byNode.delete(r.node);
            }
        });
        if (removed && records.size < MAX_NODES && storedChars < MAX_CHARS) limited = false;
    }
    function status() {
        var translated = 0, failed = 0;
        records.forEach(function (r) {
            if (r.translated !== null && r.translated.trim() !== r.original.trim()) translated++;
            if (r.attempts >= 2 && r.translated === null) failed++;
        });
        return { translated: translated, failed: failed, limited: limited, available: !!root, scanning: !!walker || rescan };
    }
    window.__kitsugiWikiDomV1 = {
        configure: function (id, lang, active, retry, source) {
            observe();
            if (id !== docId || lang !== target || origin !== source) {
                restore(); records.clear(); byNode = new WeakMap(); storedChars = 0;
                docId = id; target = lang; origin = source; walker = null; rescan = true; limited = false;
            }
            if (retry) records.forEach(function (r) { r.attempts = 0; r.pending = false; r.retryAt = 0; });
            if (!active && enabled) restore();
            if (active && !enabled) mutate(function () {
                records.forEach(function (r) { if (r.translated !== null) write(r, r.translated); });
            });
            enabled = active;
            if (retry || active) rescan = true;
            return status();
        },
        take: function (id) {
            if (id !== docId || !enabled) return { items: [], status: status() };
            observe(); prune();
            if (!root) return { items: [], status: status() };
            if (!walker && rescan) { walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT); rescan = false; }
            var items = [], chars = 0, steps = 0, node, started = performance.now();
            while (walker && steps++ < 500 && items.length < 8 && chars < 1000 && performance.now() - started < 8) {
                node = walker.nextNode();
                if (!node) { walker = null; break; }
                if (!eligible(node)) continue;
                var r = byNode.get(node);
                if (r) changed(r);
                if (!r) {
                    if (records.size >= MAX_NODES || storedChars + node.nodeValue.length > MAX_CHARS) { limited = true; continue; }
                    r = { id: ++serial, node: node, original: node.nodeValue, rendered: node.nodeValue,
                        translated: null, pending: false, attempts: 0, retryAt: 0, rev: 0 };
                    records.set(r.id, r); byNode.set(node, r); storedChars += r.original.length;
                }
                if (r.translated !== null || r.pending || r.attempts >= 2) continue;
                if (Date.now() < r.retryAt) { rescan = true; continue; }
                r.pending = true;
                items.push({ id: r.id, rev: r.rev, text: r.original.trim() });
                chars += r.original.length;
            }
            return { items: items, status: status() };
        },
        apply: function (id, values) {
            if (id !== docId || !enabled) return { stale: true };
            if (performance.now() - lastInput < 250) return { deferred: true };
            mutate(function () {
                values.forEach(function (v) {
                    var r = records.get(v.id);
                    if (!r || !r.pending || r.rev !== v.rev || r.original.trim() !== v.source || changed(r)) return;
                    r.pending = false;
                    if (!r.node.isConnected || !root || !root.contains(r.node) ||
                        !r.node.parentElement || r.node.parentElement.closest(skip)) { rescan = true; return; }
                    if (typeof v.text !== 'string' || !v.text.trim() || v.text.length > Math.min(40000, r.original.length * 6 + 256)) {
                        r.attempts++; r.retryAt = Date.now() + 5000; rescan = true; return;
                    }
                    r.translated = (r.original.match(/^\s*/)[0]) + v.text.trim() + (r.original.match(/\s*$/)[0]);
                    write(r, r.translated);
                });
            });
            return { status: status() };
        },
        release: function (id) {
            if (id === docId) records.forEach(function (r) { r.pending = false; });
        },
        status: status
    };
})();

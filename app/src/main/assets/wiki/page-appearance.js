/* Use the site's theme classes, never invert imagery or replace article styles.
 * No cookie/localStorage writes: SITE restores our temporary override in this document.
 */
(function () {
    'use strict';
    if (window.__kitsugiWikiAppearanceV1) return;
    var saved = null;
    var fandom = ['theme-fandomdesktop-light', 'theme-fandomdesktop-dark'];
    var wikipedia = ['skin-theme-clientpref-day', 'skin-theme-clientpref-night', 'skin-theme-clientpref-os'];
    function snapshot(el, tokens) {
        return { el: el, tokens: tokens, present: tokens.filter(function (name) { return el.classList.contains(name); }),
            scheme: el.style.getPropertyValue('color-scheme'), priority: el.style.getPropertyPriority('color-scheme') };
    }
    function restore() {
        if (!saved) return;
        saved.forEach(function (s) {
            s.tokens.forEach(function (name) { s.el.classList.remove(name); });
            s.present.forEach(function (name) { s.el.classList.add(name); });
            if (s.el === document.documentElement) {
                if (s.scheme) s.el.style.setProperty('color-scheme', s.scheme, s.priority);
                else s.el.style.removeProperty('color-scheme');
            }
        });
        saved = null;
    }
    function canvas() {
        var elements = [document.documentElement, document.body];
        for (var i = 0; i < elements.length; i++) {
            if (!elements[i]) continue;
            var color = getComputedStyle(elements[i]).backgroundColor;
            // Wide-gamut color() syntax is not an RGB triplet; do not misparse its profile name.
            if (!/^rgba?\(/.test(color)) continue;
            var numbers = color && color.match(/[\d.]+/g);
            if (numbers && numbers.length >= 3 && (numbers.length < 4 || Number(numbers[3]) === 1)) {
                return '#' + numbers.slice(0, 3).map(function (v) {
                    return ('0' + Math.max(0, Math.min(255, Math.round(Number(v)))).toString(16)).slice(-2);
                }).join('');
            }
        }
        // Transparent, unthemed documents use the browser's normal white canvas.
        return '#ffffff';
    }
    window.__kitsugiWikiAppearanceV1 = function (mode) {
        if (!document.body) return { supported: false, canvas: '#ffffff' };
        if (saved && saved[1].el !== document.body) restore();
        if (mode !== 'LIGHT' && mode !== 'DARK') {
            restore();
            return { supported: true, canvas: canvas() };
        }
        var host = location.hostname.toLowerCase();
        var f = /(^|\.)(fandom\.com|wikia\.com)$/.test(host);
        var w = /(^|\.)wikipedia\.org$/.test(host);
        var html = document.documentElement, body = document.body;
        var tokens = f ? fandom : wikipedia;
        // Old/custom skins without a known theme contract are left intact.
        var supported = (f || w) && [html, body].some(function (el) {
            return tokens.some(function (token) { return el.classList.contains(token); });
        });
        if (!supported && !saved) return { supported: false, canvas: canvas() };
        if (!saved) saved = [snapshot(html, tokens), snapshot(body, tokens)];
        var dark = mode === 'DARK';
        saved.forEach(function (s) { s.tokens.forEach(function (name) { s.el.classList.remove(name); }); });
        // Fandom uses body.theme-fandomdesktop-*, modern Wikipedia uses html.skin-theme-clientpref-*.
        (f ? body : html).classList.add(f ? fandom[dark ? 1 : 0] : wikipedia[dark ? 1 : 0]);
        html.style.setProperty('color-scheme', dark ? 'dark' : 'light');
        return { supported: true, canvas: canvas() };
    };
})();

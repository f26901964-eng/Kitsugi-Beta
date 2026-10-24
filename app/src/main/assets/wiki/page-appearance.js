/* Kitsugi original-page appearance v2 — "site first, compatible colour layer second".
 *
 * SITE never touches the document.
 * LIGHT/DARK first switch the wiki's own theme contract (Fandom `theme-fandomdesktop-*`,
 * Wikimedia `skin-theme-clientpref-*`), then add a small, explicit colour layer driven by
 * the app palette. The layer exists because big wikis hard-code colours in their community
 * CSS, so the site-class switch alone can look like "nothing happened".
 *
 * Guarantees:
 *  - Article DOM, text nodes, links, tables, listeners and site scripts are never replaced.
 *  - Images, videos, players and galleries are never filtered, inverted or recoloured.
 *  - No cookie/localStorage/sessionStorage writes; the host app owns the preference.
 *  - SITE restores the exact original theme classes and root color-scheme.
 */
(function () {
    'use strict';
    if (window.__kitsugiWikiAppearanceV2) return;

    var ATTR = 'data-kitsugi-wiki-theme';
    var STYLE_ID = 'kitsugi-wiki-appearance';
    var CONTRACTS = [
        {
            tokens: ['theme-fandomdesktop-light', 'theme-fandomdesktop-dark'],
            pick: function (dark) { return dark ? 'theme-fandomdesktop-dark' : 'theme-fandomdesktop-light'; }
        },
        {
            tokens: ['skin-theme-clientpref-day', 'skin-theme-clientpref-night', 'skin-theme-clientpref-os'],
            pick: function (dark) { return dark ? 'skin-theme-clientpref-night' : 'skin-theme-clientpref-day'; }
        }
    ];
    var LIGHT = {
        background: '#ffffff', surface: '#ffffff', raised: '#f1f3f6',
        text: '#171a1f', muted: '#5b6472', border: '#d6dce5', link: '#0b57d0'
    };
    var DARK = {
        background: '#0e1116', surface: '#141922', raised: '#1c222c',
        text: '#e9edf3', muted: '#a6b0be', border: '#2a3341', link: '#8cc8ff'
    };
    // Containers that carry the page colour.
    var PAGE = ['#content', '#contentInner', '#mw-content-text', '#bodyContent', '.mw-parser-output',
        '.mw-body', '.mw-body-content', '.page', '.page__main', '.page-content', '.page-header',
        '.page-header__title', '.page-header__categories', '.resizable-container', '.global-navigation',
        '.fandom-community-header', '.community-header-wrapper', '.fandom-sticky-header', '.page__right-rail',
        '.rail-module', '.rail-module__header', '.vector-header', '.vector-page-toolbar',
        '.vector-page-titlebar', '.vector-body', '.vector-column-start', '.vector-column-end',
        '#footer', '.global-footer', '.fandom-community-header__local-navigation', '.wds-tabs'];
    // Cards, boxes and controls that commonly ship a hard-coded light background.
    var CARDS = ['.portable-infobox', '.infobox', '.pi-item', '.pi-title', '.pi-header', '.pi-group',
        '.pi-data-label', '.pi-data-value', '.pi-smart-data-label', '.pi-smart-data-value',
        '.navbox', '.toc', '.toc ul', '.thumbinner', '.thumbcaption', '.gallery', '.gallerybox',
        'table', 'blockquote', 'pre', 'code', '.mw-highlight', '.wds-tabber', '.wds-dropdown__content',
        '.mw-notification', '.messagebox', '.ambox', '.mbox', '.mw-references-wrap', '.refbegin',
        'input', 'textarea', 'select', '.wds-input', '.oo-ui-popupWidget-popup'];
    // Cells lose their own background so the table colour always wins; the text colour is
    // inherited from the table instead of forced, so wiki-designed cells keep their contrast.
    var CELLS = ['th', 'td', 'caption'];
    var MUTED = ['hr', '.reference', '.mw-editsection', 'figcaption', 'small'];

    var mode = 'SITE';
    var saved = null;
    var native = false;
    var applied = null;
    var sheet = null;
    var styleEl = null;
    var observer = null;

    function hexColor(value, fallback) {
        return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value) ? value.toLowerCase() : fallback;
    }

    function palette(scheme, input) {
        var base = scheme === 'DARK' ? DARK : LIGHT;
        var source = input && typeof input === 'object' ? input : {};
        return {
            background: hexColor(source.background, base.background),
            surface: hexColor(source.surface, base.surface),
            raised: hexColor(source.raised, base.raised),
            text: hexColor(source.text, base.text),
            muted: hexColor(source.muted, base.muted),
            border: hexColor(source.border, base.border),
            link: hexColor(source.link, base.link)
        };
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

    function snapshotEntry(el) {
        var carries = [];
        CONTRACTS.forEach(function (contract) {
            var present = contract.tokens.filter(function (token) { return el.classList.contains(token); });
            if (present.length) carries.push({ contract: contract, present: present });
        });
        return {
            el: el,
            carries: carries,
            scheme: el.style.getPropertyValue('color-scheme'),
            priority: el.style.getPropertyPriority('color-scheme')
        };
    }

    function take() {
        var nodes = [document.documentElement, document.body].filter(Boolean);
        saved = nodes.map(snapshotEntry);
        native = saved.some(function (entry) { return entry.carries.length > 0; });
    }

    // jsdom and some engines rewrite the class attribute even for a no-op toggle, which
    // would feed our observer with endless self-inflicted mutations. Only touch real changes.
    function setToken(el, token, present) {
        if (el.classList.contains(token) === present) return;
        if (present) el.classList.add(token);
        else el.classList.remove(token);
    }

    function clearTokens(el) {
        CONTRACTS.forEach(function (contract) {
            contract.tokens.forEach(function (token) { setToken(el, token, false); });
        });
    }

    function restore() {
        if (saved) {
            saved.forEach(function (entry) {
                if (!entry.el) return;
                clearTokens(entry.el);
                entry.carries.forEach(function (carried) {
                    carried.present.forEach(function (token) { setToken(entry.el, token, true); });
                });
                if (entry.el === document.documentElement) {
                    if (entry.scheme) entry.el.style.setProperty('color-scheme', entry.scheme, entry.priority);
                    else entry.el.style.removeProperty('color-scheme');
                }
            });
        }
        saved = null;
        native = false;
        applied = null;
        mode = 'SITE';
        if (document.documentElement) document.documentElement.removeAttribute(ATTR);
        stopObserver();
        uninstall();
    }

    function applyTokens(scheme) {
        if (!saved) return;
        var dark = scheme === 'DARK';
        saved.forEach(function (entry) {
            if (!entry.el) return;
            CONTRACTS.forEach(function (contract) {
                var carries = entry.carries.some(function (carried) { return carried.contract === contract; });
                var wanted = carries ? contract.pick(dark) : null;
                contract.tokens.forEach(function (token) {
                    setToken(entry.el, token, token === wanted);
                });
            });
        });
        var root = document.documentElement;
        if (root) {
            // Every call is idempotent: re-writing the same attribute would feed the
            // observer with a self-inflicted mutation forever.
            var value = dark ? 'dark' : 'light';
            if (root.getAttribute(ATTR) !== value) root.setAttribute(ATTR, value);
            if (root.style.getPropertyValue('color-scheme') !== value) {
                root.style.setProperty('color-scheme', value, 'important');
            }
        }
    }

    function scoped(root, list) {
        return list.map(function (selector) { return root + ' ' + selector; }).join(',');
    }

    function stylesheet(scheme, p) {
        var dark = scheme === 'DARK';
        var root = 'html[' + ATTR + '="' + (dark ? 'dark' : 'light') + '"]';
        var tokens = {
            // Fandom / Wikia design tokens.
            '--theme-page-background-color': p.surface,
            '--theme-page-background-color--secondary': p.raised,
            '--theme-body-background-color': p.background,
            '--theme-article-background-color': p.surface,
            '--theme-page-text-color': p.text,
            '--theme-page-text-mix-color': p.muted,
            '--theme-page-secondary-text-color': p.muted,
            '--theme-page-dynamic-color-1': p.text,
            '--theme-page-dynamic-color-2': p.muted,
            '--theme-page-border-color': p.border,
            '--theme-link-color': p.link,
            '--theme-sticky-nav-background-color': p.surface,
            '--theme-community-header-background-color': p.surface,
            '--theme-community-header-color': p.text,
            '--theme-community-header-link-color': p.text,
            // Wikimedia Vector 2022 / Minerva tokens.
            '--background-color-base': p.surface,
            '--background-color-neutral': p.raised,
            '--background-color-neutral-subtle': p.surface,
            '--background-color-interactive': p.raised,
            '--color-base': p.text,
            '--color-emphasized': p.text,
            '--color-subtle': p.muted,
            '--color-placeholder': p.muted,
            '--border-color-base': p.border,
            '--border-color-subtle': p.border,
            '--color-link': p.link,
            '--color-link-visited': p.link
        };
        var declarations = [];
        for (var name in tokens) {
            if (Object.prototype.hasOwnProperty.call(tokens, name)) {
                declarations.push(name + ':' + tokens[name] + ' !important');
            }
        }
        return [
            root + '{color-scheme:' + (dark ? 'dark' : 'light') + ' !important;background-color:' +
                p.background + ' !important;' + declarations.join(';') + '}',
            root + ' body{background-color:' + p.background + ' !important;color:' + p.text + ' !important;}',
            scoped(root, PAGE) + '{background-color:' + p.surface + ' !important;color:' + p.text + ' !important;}',
            scoped(root, CARDS) + '{background-color:' + p.raised + ' !important;color:' + p.text +
                ' !important;border-color:' + p.border + ' !important;}',
            scoped(root, CELLS) + '{background-color:transparent !important;border-color:' + p.border + ' !important;}',
            scoped(root, MUTED) + '{color:' + p.muted + ' !important;border-color:' + p.border + ' !important;}',
            root + ' a:not(.wds-button):not(.mw-ui-button):not([role="button"]){color:' + p.link + ' !important;}',
            root + ' h1,' + root + ' h2,' + root + ' h3,' + root + ' h4,' + root + ' h5,' + root + ' h6' +
                '{color:' + p.text + ' !important;}'
        ].join('\n');
    }

    function uninstall() {
        if (sheet) {
            try {
                document.adoptedStyleSheets = document.adoptedStyleSheets.filter(function (item) {
                    return item !== sheet;
                });
            } catch (ignored) { /* frozen adoptedStyleSheets: fall through to the style element */ }
            sheet = null;
        }
        if (styleEl && styleEl.parentNode) styleEl.parentNode.removeChild(styleEl);
        styleEl = null;
        var stray = document.getElementById(STYLE_ID);
        if (stray && stray.parentNode) stray.parentNode.removeChild(stray);
    }

    function install(css) {
        try {
            if (document.adoptedStyleSheets && window.CSSStyleSheet) {
                if (!sheet) {
                    sheet = new CSSStyleSheet();
                    document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
                }
                if (sheet.replaceSync) {
                    sheet.replaceSync(css);
                    return;
                }
            }
        } catch (ignored) {
            sheet = null;
        }
        if (!styleEl || !styleEl.parentNode) {
            var head = document.head || document.documentElement;
            if (!head) return;
            styleEl = document.getElementById(STYLE_ID) || document.createElement('style');
            styleEl.id = STYLE_ID;
            head.appendChild(styleEl);
        }
        styleEl.textContent = css;
    }

    function stopObserver() {
        if (observer) {
            observer.disconnect();
            observer = null;
        }
    }

    // Wikis re-render their shell (including <body>) long after load. Re-assert the state
    // instead of letting a late render silently undo the user's choice. Every call is
    // idempotent, so the observer settles after one pass.
    function watchHead() {
        if (!observer || !document.head) return;
        // Re-attaching replaces the previous options for this target; it is our own node.
        observer.observe(document.head, { childList: true });
    }

    function watch() {
        if (observer || !window.MutationObserver) return;
        observer = new MutationObserver(function () {
            if (mode === 'SITE' || !document.body) return;
            if (!saved || saved.length < 2 || saved[1].el !== document.body) {
                saved = null;
                take();
            }
            applyTokens(mode);
            if (!sheet && (!styleEl || !styleEl.parentNode) && applied) install(stylesheet(mode, applied));
            watchHead();
        });
        observer.observe(document.documentElement, {
            attributes: true, attributeFilter: ['class', ATTR], childList: true
        });
        if (document.body) observer.observe(document.body, { attributes: true, attributeFilter: ['class'] });
        watchHead();
    }

    window.__kitsugiWikiAppearanceV2 = function (input, colors) {
        if (!document.body) return { supported: false, native: false, canvas: '#ffffff' };
        var want = input === 'LIGHT' || input === 'DARK' ? input : 'SITE';
        if (want === 'SITE') {
            restore();
            return { supported: true, native: false, canvas: document.body ? canvas() : '#ffffff' };
        }
        if (saved && saved.length > 1 && saved[1].el && saved[1].el !== document.body) restore();
        if (!saved) take();
        var isNative = native;
        mode = want;
        applied = palette(want, colors);
        applyTokens(want);
        install(stylesheet(want, applied));
        watch();
        return {
            supported: true,
            native: isNative,
            background: applied.background,
            canvas: canvas()
        };
    };
})();

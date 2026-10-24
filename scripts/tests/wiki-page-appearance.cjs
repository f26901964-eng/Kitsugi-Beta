// Actual shipped theme helper, not an imitation. jsdom verifies DOM contracts, not live CSS rendering.
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { JSDOM } = require('jsdom');
const source = readFileSync(resolve(__dirname, '../../app/src/main/assets/wiki/page-appearance.js'), 'utf8');

const DARK = {
    background: '#101418', surface: '#161b21', raised: '#1d232b',
    text: '#eef1f5', muted: '#a7b0bc', border: '#2b333d', link: '#8cc8ff'
};

function fixture({ host = 'you-zitsu.fandom.com', html = '', body = 'theme-fandomdesktop-light', style = '' } = {}) {
    const dom = new JSDOM(`<!doctype html><html class="${html}" style="${style}"><body class="${body}">
        <main><p>Article text <a href="/wiki/Other">link</a></p><img width="180" src="/image.png">
        <table><tr><td colspan="2">Cell</td></tr></table></main></body></html>`,
        { url: `https://${host}/wiki/Test`, runScripts: 'dangerously' });
    dom.window.eval(source);
    return {
        w: dom.window, d: dom.window.document, apply: dom.window.__kitsugiWikiAppearanceV2,
        sheet: () => dom.window.document.getElementById('kitsugi-wiki-appearance'),
        close: () => dom.window.close()
    };
}

test('SITE default leaves all source markup, styles and storage untouched', () => {
    const f = fixture({ style: 'color-scheme: light !important; background: rgb(30, 40, 50)' });
    const before = f.d.documentElement.outerHTML;
    const result = f.apply('SITE');
    assert.equal(result.supported, true);
    assert.equal(result.canvas, '#1e2832');
    assert.equal(f.d.documentElement.outerHTML, before);
    assert.equal(f.w.localStorage.length, 0);
    assert.equal(f.d.cookie, '');
    f.close();
});

test('Fandom switches only its theme classes and restores the exact original theme and scheme', () => {
    const f = fixture({ html: 'unrelated', body: 'theme-fandomdesktop-light article', style: 'color-scheme: light !important' });
    const result = f.apply('DARK', DARK);
    assert.equal(result.supported, true);
    assert.equal(result.native, true);
    assert.equal(f.d.body.className, 'article theme-fandomdesktop-dark');
    assert.equal(f.d.documentElement.className, 'unrelated');
    assert.equal(f.d.documentElement.style.colorScheme, 'dark');
    assert.equal(f.d.documentElement.getAttribute('data-kitsugi-wiki-theme'), 'dark');
    f.d.body.classList.add('added-by-site');
    f.apply('LIGHT', DARK);
    assert(f.d.body.classList.contains('theme-fandomdesktop-light'));
    assert.equal(f.d.documentElement.getAttribute('data-kitsugi-wiki-theme'), 'light');
    f.apply('SITE');
    assert.equal(f.d.documentElement.className, 'unrelated');
    assert.equal(f.d.documentElement.getAttribute('data-kitsugi-wiki-theme'), null);
    assert.equal(f.sheet(), null);
    assert(f.d.body.classList.contains('added-by-site'));
    assert(f.d.body.classList.contains('theme-fandomdesktop-light'));
    assert.equal(f.d.documentElement.style.getPropertyPriority('color-scheme'), 'important');
    assert.equal(f.d.documentElement.style.colorScheme, 'light');
    f.close();
});

test('compatible layer is injected with the app palette and never filters media', () => {
    const f = fixture();
    f.apply('DARK', DARK);
    const css = f.sheet().textContent;
    assert.match(css, /html\[data-kitsugi-wiki-theme="dark"\]/);
    for (const value of [DARK.background, DARK.surface, DARK.raised, DARK.text, DARK.border, DARK.link]) {
        assert.ok(css.includes(value), `palette value ${value} missing`);
    }
    assert.ok(css.includes('--theme-page-background-color'));
    assert.ok(css.includes('--theme-page-text-color'));
    assert.ok(css.includes('--color-base'));
    assert.ok(!/filter\s*:/.test(css), 'media must never be filtered');
    assert.ok(!/invert\(/i.test(css), 'media must never be inverted');
    assert.equal(f.d.querySelector('img').style.filter, '');
    assert.equal(f.d.querySelector('style').id, 'kitsugi-wiki-appearance');
    f.close();
});

test('custom skins without a known theme contract still get a working theme', () => {
    const f = fixture({ body: 'old-skin' });
    const result = f.apply('DARK', DARK);
    assert.equal(result.supported, true);
    assert.equal(result.native, false);
    assert.equal(f.d.body.className, 'old-skin');
    assert.equal(f.d.documentElement.getAttribute('data-kitsugi-wiki-theme'), 'dark');
    assert.ok(f.sheet() && f.sheet().textContent.includes(DARK.surface));
    f.apply('SITE');
    assert.equal(f.d.body.className, 'old-skin');
    assert.equal(f.sheet(), null);
    f.close();
});

test('images, table geometry, text, links and existing listeners retain identity', () => {
    const f = fixture();
    const main = f.d.querySelector('main');
    const before = main.outerHTML;
    const link = f.d.querySelector('a');
    let clicked = 0;
    link.addEventListener('click', e => { e.preventDefault(); clicked++; });
    for (const mode of ['DARK', 'LIGHT', 'DARK', 'SITE']) f.apply(mode, DARK);
    assert.equal(f.d.querySelector('main'), main);
    assert.equal(main.outerHTML, before);
    assert.equal(f.d.querySelector('a'), link);
    link.click();
    assert.equal(clicked, 1);
    assert.equal(f.d.querySelector('img').style.filter, '');
    assert.equal(f.d.querySelectorAll('style').length, 0);
    f.close();
});

test('repeated asset injection retains the original snapshot rather than snapshotting our override', () => {
    const f = fixture();
    const api = f.apply;
    f.apply('DARK', DARK);
    f.w.eval(source);
    assert.equal(f.w.__kitsugiWikiAppearanceV2, api);
    f.apply('DARK', DARK);
    f.apply('SITE');
    assert.equal(f.d.body.className, 'theme-fandomdesktop-light');
    assert.equal(f.d.documentElement.style.colorScheme, '');
    assert.equal(f.d.documentElement.getAttribute('data-kitsugi-wiki-theme'), null);
    f.close();
});

test('body replacement gets a fresh snapshot and can be restored', () => {
    const f = fixture();
    f.apply('DARK', DARK);
    const body = f.d.createElement('body');
    body.className = 'theme-fandomdesktop-light fresh';
    f.d.body.replaceWith(body);
    f.apply('DARK', DARK);
    assert(body.classList.contains('theme-fandomdesktop-dark'));
    f.apply('SITE');
    assert.equal(body.className, 'fresh theme-fandomdesktop-light');
    f.close();
});

test('Wikipedia follows its html day/night contract and restores the OS preference', () => {
    const f = fixture({ host: 'tr.wikipedia.org', html: 'skin-vector skin-theme-clientpref-os', body: 'mediawiki' });
    const dark = f.apply('DARK', DARK);
    assert.equal(dark.native, true);
    assert(f.d.documentElement.classList.contains('skin-theme-clientpref-night'));
    assert(!f.d.documentElement.classList.contains('skin-theme-clientpref-os'));
    f.apply('LIGHT', DARK);
    assert(f.d.documentElement.classList.contains('skin-theme-clientpref-day'));
    f.apply('SITE');
    assert(f.d.documentElement.classList.contains('skin-theme-clientpref-os'));
    assert.equal(f.d.body.className, 'mediawiki');
    f.close();
});

test('canvas uses an opaque body colour when html is transparent; transparent pages stay white', () => {
    const f = fixture();
    assert.equal(f.apply('SITE').canvas, '#ffffff');
    f.d.body.style.backgroundColor = 'rgb(12, 24, 36)';
    assert.equal(f.apply('SITE').canvas, '#0c1824');
    f.d.body.style.backgroundColor = 'rgba(12, 24, 36, 0.5)';
    assert.equal(f.apply('SITE').canvas, '#ffffff');
    f.close();
});

test('body inline color scheme is not clobbered by restoration', () => {
    const f = fixture();
    f.d.body.style.colorScheme = 'only light';
    f.apply('DARK', DARK);
    f.d.body.style.colorScheme = 'light dark';
    f.apply('SITE');
    assert.equal(f.d.body.style.colorScheme, 'light dark');
    f.close();
});

test('Wikia is recognized; unknown input restores SITE and a missing body reports unsupported', () => {
    const f = fixture({ host: 'sample.wikia.com' });
    assert.equal(f.apply('DARK', DARK).native, true);
    f.apply('unexpected');
    assert(f.d.body.classList.contains('theme-fandomdesktop-light'));
    assert.equal(f.sheet(), null);
    f.d.body.remove();
    assert.equal(f.apply('LIGHT', DARK).supported, false);
    f.close();
});

test('palette input is validated before it reaches CSS', () => {
    const f = fixture();
    f.apply('DARK', {
        background: 'red; } body { display:none', surface: 'url(javascript:alert(1))',
        raised: '#12345', text: '#00ff00', muted: null, border: '#ABCDEF', link: 42
    });
    const css = f.sheet().textContent;
    assert.ok(!css.includes('javascript:'), 'untrusted palette text must not reach the stylesheet');
    assert.ok(!css.includes('display:none'));
    assert.ok(css.includes('#0e1116'), 'invalid values fall back to the dark default');
    assert.ok(css.includes('#00ff00'));
    assert.ok(css.includes('#abcdef'), 'valid values are normalised to lower case');
    f.close();
});

test('theme survives a re-rendered shell and is re-applied when the site strips it', async () => {
    const f = fixture();
    f.apply('DARK', DARK);
    const head = f.d.querySelector('head');
    head.innerHTML = '<meta name="viewport" content="width=device-width">';
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.ok(f.sheet(), 'stylesheet must be restored after the head is replaced');
    f.d.documentElement.removeAttribute('data-kitsugi-wiki-theme');
    f.d.body.className = 'theme-fandomdesktop-light';
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.ok(f.d.documentElement.getAttribute('data-kitsugi-wiki-theme'), 'theme attribute must be re-applied');
    assert(f.d.body.classList.contains('theme-fandomdesktop-dark'));
    f.apply('SITE');
    f.close();
});

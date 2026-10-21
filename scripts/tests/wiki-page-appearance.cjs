// Actual shipped theme helper, not an imitation. jsdom verifies DOM contracts, not live CSS rendering.
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { JSDOM } = require('jsdom');
const source = readFileSync(resolve(__dirname, '../../app/src/main/assets/wiki/page-appearance.js'), 'utf8');
function fixture({ host = 'you-zitsu.fandom.com', html = '', body = 'theme-fandomdesktop-light', style = '' } = {}) {
    const dom = new JSDOM(`<!doctype html><html class="${html}" style="${style}"><body class="${body}">
        <main><p>Article text <a href="/wiki/Other">link</a></p><img width="180" src="/image.png">
        <table><tr><td colspan="2">Cell</td></tr></table></main></body></html>`,
        { url: `https://${host}/wiki/Test`, runScripts: 'dangerously' });
    dom.window.eval(source);
    return { w: dom.window, d: dom.window.document, apply: dom.window.__kitsugiWikiAppearanceV1,
        close: () => dom.window.close() };
}
test('SITE default leaves all source markup, styles and storage untouched', () => {
    const f = fixture({ style: 'color-scheme: light !important; background: rgb(30, 40, 50)' });
    const before = f.d.documentElement.outerHTML;
    const result = f.apply('SITE');
    assert.equal(result.supported, true);
    assert.equal(result.canvas, '#1e2832');
    assert.equal(f.d.documentElement.outerHTML, before);
    assert.equal(f.w.localStorage.length, 0);
    assert.equal(f.d.cookie, ''); f.close();
});
test('Fandom switches only its theme classes and restores exact original theme and scheme', () => {
    const f = fixture({ html: 'unrelated', body: 'theme-fandomdesktop-light article', style: 'color-scheme: light !important' });
    assert.equal(f.apply('DARK').supported, true);
    assert.equal(f.d.body.className, 'article theme-fandomdesktop-dark');
    assert.equal(f.d.documentElement.style.colorScheme, 'dark');
    f.d.body.classList.add('added-by-site');
    f.apply('LIGHT');
    assert(f.d.body.classList.contains('theme-fandomdesktop-light'));
    f.apply('SITE');
    assert.equal(f.d.documentElement.className, 'unrelated');
    assert(f.d.body.classList.contains('added-by-site'));
    assert(f.d.body.classList.contains('theme-fandomdesktop-light'));
    assert.equal(f.d.documentElement.style.getPropertyPriority('color-scheme'), 'important');
    assert.equal(f.d.documentElement.style.colorScheme, 'light'); f.close();
});
test('Wikipedia follows its html day/night contract and restores OS preference', () => {
    const f = fixture({ host: 'tr.wikipedia.org', html: 'skin-vector skin-theme-clientpref-os', body: 'mediawiki' });
    assert.equal(f.apply('DARK').supported, true);
    assert(f.d.documentElement.classList.contains('skin-theme-clientpref-night'));
    assert(!f.d.documentElement.classList.contains('skin-theme-clientpref-os'));
    f.apply('LIGHT'); assert(f.d.documentElement.classList.contains('skin-theme-clientpref-day'));
    f.apply('SITE'); assert(f.d.documentElement.classList.contains('skin-theme-clientpref-os'));
    assert.equal(f.d.body.className, 'mediawiki'); f.close();
});
test('old/custom skins and host lookalikes fail open without generic recoloring', () => {
    for (const options of [{ body: 'old-skin' }, { host: 'fandom.com.example.org' },
        { host: 'notwikipedia.org', html: 'skin-theme-clientpref-day', body: '' }]) {
        const f = fixture(options); const before = f.d.documentElement.outerHTML;
        assert.equal(f.apply('DARK').supported, false);
        assert.equal(f.d.documentElement.outerHTML, before); f.close();
    }
});
test('images, table geometry, text, links and existing listeners retain identity', () => {
    const f = fixture(); const main = f.d.querySelector('main'); const before = main.outerHTML;
    const link = f.d.querySelector('a'); let clicked = 0;
    link.addEventListener('click', e => { e.preventDefault(); clicked++; });
    for (const mode of ['DARK', 'LIGHT', 'DARK', 'SITE']) f.apply(mode);
    assert.equal(f.d.querySelector('main'), main); assert.equal(main.outerHTML, before);
    assert.equal(f.d.querySelector('a'), link); link.click(); assert.equal(clicked, 1);
    assert.equal(f.d.querySelector('img').style.filter, '');
    assert.equal(f.d.querySelectorAll('style').length, 0); f.close();
});
test('repeated asset injection retains original snapshot rather than snapshotting our override', () => {
    const f = fixture(); const api = f.apply;
    f.apply('DARK'); f.w.eval(source);
    assert.equal(f.w.__kitsugiWikiAppearanceV1, api);
    f.apply('DARK'); f.apply('SITE');
    assert.equal(f.d.body.className, 'theme-fandomdesktop-light');
    assert.equal(f.d.documentElement.style.colorScheme, ''); f.close();
});
test('body replacement gets a fresh snapshot and can be restored', () => {
    const f = fixture(); f.apply('DARK');
    const body = f.d.createElement('body'); body.className = 'theme-fandomdesktop-light fresh';
    f.d.body.replaceWith(body);
    f.apply('DARK'); assert(body.classList.contains('theme-fandomdesktop-dark'));
    f.apply('SITE'); assert.equal(body.className, 'fresh theme-fandomdesktop-light'); f.close();
});
test('canvas uses opaque body color when html is transparent; transparent pages stay white', () => {
    const f = fixture(); assert.equal(f.apply('SITE').canvas, '#ffffff');
    f.d.body.style.backgroundColor = 'rgb(12, 24, 36)';
    assert.equal(f.apply('SITE').canvas, '#0c1824');
    f.d.body.style.backgroundColor = 'rgba(12, 24, 36, 0.5)';
    assert.equal(f.apply('SITE').canvas, '#ffffff'); f.close();
});
test('body inline color scheme is not clobbered by restoration', () => {
    const f = fixture(); f.d.body.style.colorScheme = 'only light';
    f.apply('DARK'); f.d.body.style.colorScheme = 'light dark'; f.apply('SITE');
    assert.equal(f.d.body.style.colorScheme, 'light dark'); f.close();
});
test('Wikia is recognized; unknown input restores SITE and missing body reports unsupported', () => {
    const f = fixture({ host: 'sample.wikia.com' });
    assert.equal(f.apply('DARK').supported, true);
    f.apply('unexpected'); assert(f.d.body.classList.contains('theme-fandomdesktop-light'));
    f.d.body.remove(); assert.equal(f.apply('LIGHT').supported, false); f.close();
});

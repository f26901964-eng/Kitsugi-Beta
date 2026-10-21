// Exercises the actual shipped scripts against DOM fixtures, never a reimplementation.
// NODE_PATH=.cache/wiki-checks/node_modules node --test scripts/tests/wiki-dom-tools.cjs
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { JSDOM } = require('jsdom');
const asset = name => readFileSync(resolve(__dirname, '../../app/src/main/assets/wiki/', name), 'utf8');
const translator = asset('dom-translator.js');
const cosmetics = asset('adblock-cosmetic.js');

function fixture(html, lang = 'en') {
    const dom = new JSDOM(`<!doctype html><html lang="${lang}"><head></head><body>
        <div id="mw-content-text"><main class="mw-parser-output">${html}</main></div></body></html>`,
        { url: 'https://you-zitsu.fandom.com/wiki/Home', runScripts: 'dangerously', pretendToBeVisual: true });
    const w = dom.window;
    const frames = [];
    // jsdom layout calls are intentionally slower than Chromium. A deterministic
    // clock isolates DOM correctness; the real script also has an 8ms scan budget.
    w.performance.now = () => 1000;
    w.requestAnimationFrame = fn => frames.push(fn);
    w.document.elementFromPoint = () => null;
    w.HTMLElement.prototype.getClientRects = function () { return [this.getBoundingClientRect()]; };
    w.HTMLElement.prototype.getBoundingClientRect = function () {
        const top = Number(this.dataset.top || 20);
        return { top, bottom: top + 30, left: 0, right: 400, width: 400, height: 30 };
    };
    w.scrollBy = () => {};
    w.eval(translator);
    const api = w.__kitsugiWikiDomV1;
    const configure = (enabled = true, target = 'tr', id = 'doc-1', retry = false, source = 'auto') => api.configure(id, target, enabled, retry, source);
    configure();
    function take(id = 'doc-1') { return JSON.parse(JSON.stringify(api.take(id))); }
    function apply(items, fn = text => 'TR:' + text, id = 'doc-1') {
        return api.apply(id, items.map(item => ({ id: item.id, rev: item.rev, source: item.text, text: fn(item.text) })));
    }
    return { dom, w, api, configure, take, apply, frames, close: () => dom.window.close() };
}
const tick = () => new Promise(resolve => setImmediate(resolve));

test('text slots translate without replacing elements, links, image sizes, table cells or listeners', () => {
    const f = fixture('<p id="p"> Hello <a href="/wiki/Character#History">character</a> world. </p>' +
        '<img src="/portrait.png" width="180"><table><tr><td rowspan="2">First year</td></tr></table>');
    const link = f.w.document.querySelector('a');
    let clicks = 0; link.addEventListener('click', e => { e.preventDefault(); clicks++; });
    const nodes = [...f.w.document.querySelector('#p').childNodes];
    const items = f.take().items;
    f.apply(items);
    assert.equal(f.w.document.querySelector('a'), link);
    assert.equal(link.getAttribute('href'), '/wiki/Character#History');
    assert.equal(f.w.document.querySelector('img').getAttribute('width'), '180');
    assert.equal(f.w.document.querySelector('td').rowSpan, 2);
    nodes.forEach((node, i) => assert.equal(f.w.document.querySelector('#p').childNodes[i], node));
    assert.equal(nodes[0].nodeValue, ' TR:Hello ');
    link.click(); assert.equal(clicks, 1);
    f.close();
});

test('original/translated toggle is lossless and reuses cached text with no new work', () => {
    const f = fixture('<p>  Welcome to the wiki.\n</p>');
    const original = f.w.document.querySelector('p').textContent;
    f.apply(f.take().items);
    f.configure(false);
    assert.equal(f.w.document.querySelector('p').textContent, original);
    f.configure(true);
    assert.equal(f.w.document.querySelector('p').textContent, '  TR:Welcome to the wiki.\n');
    assert.equal(f.take().items.length, 0);
    f.close();
});

test('target/source changes restore original input rather than translating a translation', () => {
    const f = fixture('<p>Original English sentence.</p>');
    f.apply(f.take().items);
    f.configure(true, 'de');
    const german = f.take().items;
    assert.equal(german[0].text, 'Original English sentence.');
    f.apply(german, () => 'German text');
    f.configure(true, 'de', 'doc-1', false, 'en');
    assert.equal(f.take().items[0].text, 'Original English sentence.');
    f.close();
});

test('forms, editable fields, code, hidden templates, translate=no, numbers and letter tiles excluded', () => {
    const f = fixture('<form>Account secret<input value="password"></form><p contenteditable="true">Private draft</p>' +
        '<pre>Source code</pre><script>var hiddenScript="secret"</script><div style="display:none">Hidden duplicate</div>' +
        '<div style="opacity:0">Invisible text</div><div translate="no">Do not translate</div>' +
        '<span>W</span><span>E</span><span>1234</span><p>https://example.com/private</p><p>Visible story.</p>');
    assert.deepEqual(f.take().items.map(x => x.text), ['Visible story.']);
    f.close();
});

test('same-language text skipped, foreign-language child remains eligible', () => {
    const f = fixture('<p>Türkçe içerik.</p><p lang="en">English description.</p>', 'tr');
    assert.deepEqual(f.take().items.map(x => x.text), ['English description.']);
    f.close();
});

test('a stale document id never mutates the new document', () => {
    const f = fixture('<p>Welcome.</p>');
    const batch = f.take().items;
    f.configure(true, 'tr', 'doc-2');
    assert.equal(f.apply(batch, () => 'WRONG', 'doc-1').stale, true);
    assert.equal(f.w.document.querySelector('p').textContent, 'Welcome.');
    f.close();
});

test('site text edits while a request is in flight invalidate that result', async () => {
    const f = fixture('<p>First version.</p>');
    const batch = f.take().items;
    f.w.document.querySelector('p').firstChild.nodeValue = 'Updated by site.';
    await tick();
    f.apply(batch, () => 'Stale result');
    assert.equal(f.w.document.querySelector('p').textContent, 'Updated by site.');
    const fresh = f.take().items;
    assert.equal(fresh[0].text, 'Updated by site.');
    assert.ok(fresh[0].rev > batch[0].rev);
    f.close();
});

test('late response after original toggle cannot overwrite original text', () => {
    const f = fixture('<p>First version.</p>');
    const batch = f.take().items;
    f.configure(false);
    assert.equal(f.apply(batch, () => 'Stale result').stale, true);
    assert.equal(f.w.document.querySelector('p').textContent, 'First version.');
    f.close();
});

test('newly inserted article content is found; own writes do not trigger a translation loop', async () => {
    const f = fixture('<p>First paragraph.</p>');
    f.apply(f.take().items);
    await tick();
    assert.equal(f.take().items.length, 0);
    const p = f.w.document.createElement('p'); p.textContent = 'New paragraph.';
    f.w.document.querySelector('main').append(p);
    await tick();
    assert.deepEqual(f.take().items.map(x => x.text), ['New paragraph.']);
    f.close();
});

test('unsafe-looking translated strings are text, never interpreted as HTML', () => {
    const f = fixture('<p>Hello world.</p>');
    f.apply(f.take().items, () => '<img src=x onerror="alert(1)">');
    assert.equal(f.w.document.querySelector('img'), null);
    assert.equal(f.w.document.querySelector('p').textContent, '<img src=x onerror="alert(1)">');
    f.close();
});

test('network failures keep original, have bounded retries and support explicit retry', () => {
    const f = fixture('<p>Hello world.</p>');
    let now = 100000; f.w.Date.now = () => now;
    f.apply(f.take().items, () => null);
    assert.equal(f.w.document.querySelector('p').textContent, 'Hello world.');
    assert.equal(f.take().items.length, 0);
    now += 6000;
    f.apply(f.take().items, () => null);
    now += 6000;
    assert.equal(f.take().items.length, 0);
    assert.equal(f.api.status().failed, 1);
    f.configure(true, 'tr', 'doc-1', true);
    assert.equal(f.take().items.length, 1);
    f.close();
});

test('scrolling defers translation application until user interaction settles', () => {
    const f = fixture('<p>Hello world.</p>');
    let now = 1000; f.w.performance.now = () => now;
    const batch = f.take().items;
    f.w.dispatchEvent(new f.w.Event('wheel'));
    assert.equal(f.apply(batch).deferred, true);
    assert.equal(f.w.document.querySelector('p').textContent, 'Hello world.');
    now += 300;
    f.apply(batch);
    assert.equal(f.w.document.querySelector('p').textContent, 'TR:Hello world.');
    f.close();
});

test('anchor correction offsets reflow and does not fight a subsequent user gesture', () => {
    const f = fixture('<p id="before">Hello.</p><p id="anchor">Reading here.</p>');
    const before = f.w.document.querySelector('#before'), anchor = f.w.document.querySelector('#anchor');
    f.w.document.elementFromPoint = () => anchor;
    anchor.getBoundingClientRect = () => ({ top: before.textContent.length * 2, bottom: 100 });
    const offsets = []; f.w.scrollBy = (x, y) => offsets.push(y);
    f.apply(f.take().items, text => 'Long translated ' + text);
    f.frames.splice(0).forEach(fn => fn());
    assert.ok(offsets.some(y => y > 0));
    offsets.length = 0;
    f.configure(false);
    f.w.dispatchEvent(new f.w.Event('wheel'));
    f.frames.splice(0).forEach(fn => fn());
    assert.equal(offsets.length, 0);
    f.close();
});

test('offscreen content is queued only when scrolled near the viewport', () => {
    const f = fixture('<p data-top="90000">Far away paragraph.</p><p>Visible paragraph.</p>');
    f.apply(f.take().items);
    f.w.document.querySelector('p').dataset.top = '20';
    f.w.dispatchEvent(new f.w.Event('scroll'));
    assert.deepEqual(f.take().items.map(x => x.text), ['Far away paragraph.']);
    f.close();
});

test('release after a cancelled native job makes pending texts eligible again', () => {
    const f = fixture('<p>Hello world.</p>');
    assert.equal(f.take().items.length, 1);
    f.api.release('doc-1');
    f.configure(true);
    assert.equal(f.take().items.length, 1);
    f.close();
});

test('bounded batches never exceed eight nodes', () => {
    const f = fixture(Array.from({ length: 30 }, (_, i) => `<p>Paragraph ${i}.</p>`).join(''));
    assert.equal(f.take().items.length, 8);
    f.close();
});

test('cosmetic blocker hides ads, not article banners, images, tables or featured video', () => {
    const f = fixture('<div class="top-ads-container">Advert</div><div class="banner">Wiki banner</div>' +
        '<table><tr><td>Content</td></tr></table><video id="featured"></video><img src="/hero.png">');
    f.w.eval(cosmetics); f.w.__kitsugiWikiAdsV1(true);
    assert.equal(f.w.getComputedStyle(f.w.document.querySelector('.top-ads-container')).display, 'none');
    for (const selector of ['.banner', 'table', '#featured', 'img']) {
        assert.notEqual(f.w.getComputedStyle(f.w.document.querySelector(selector)).display, 'none');
    }
    f.w.__kitsugiWikiAdsV1(false);
    assert.notEqual(f.w.getComputedStyle(f.w.document.querySelector('.top-ads-container')).display, 'none');
    f.close();
});

test('cosmetic styles are idempotent and apply to dynamically inserted ad slots', () => {
    const f = fixture('<p>Story text.</p>'); f.w.eval(cosmetics);
    f.w.__kitsugiWikiAdsV1(true); f.w.__kitsugiWikiAdsV1(true);
    assert.equal(f.w.document.querySelectorAll('#kitsugi-wiki-ad-style-v1').length, 1);
    const ad = f.w.document.createElement('div'); ad.className = 'gpt-ad';
    f.w.document.querySelector('main').append(ad);
    assert.equal(f.w.getComputedStyle(ad).display, 'none');
    f.w.__kitsugiWikiAdsV1(false);
    assert.equal(f.w.document.querySelectorAll('#kitsugi-wiki-ad-style-v1').length, 0);
    f.close();
});

test('detached translated nodes are restored before a site recycles them', async () => {
    const f = fixture('<p>Hello world.</p>');
    const p = f.w.document.querySelector('p');
    f.apply(f.take().items);
    p.remove(); await tick();
    f.take(); // pruning must retain the original before dropping the disconnected record
    assert.equal(p.textContent, 'Hello world.');
    f.w.document.querySelector('main').append(p); await tick();
    assert.equal(f.take().items[0].text, 'Hello world.');
    f.close();
});

test('a hidden tab becomes eligible after the site reveals it', async () => {
    const f = fixture('<section hidden><p>Hidden tab story.</p></section>');
    assert.equal(f.take().items.length, 0);
    f.w.document.querySelector('section').hidden = false; await tick();
    assert.equal(f.take().items[0].text, 'Hidden tab story.');
    f.close();
});

test('live countdowns are excluded rather than repeatedly sent to the translator', () => {
    const f = fixture('<div class="countdown">Only one day left.</div><p aria-live="polite">Live announcement.</p><p>Story summary.</p>');
    assert.deepEqual(f.take().items.map(x => x.text), ['Story summary.']);
    f.close();
});

test('reinjection is idempotent and preserves the original-text cache', () => {
    const f = fixture('<p>Hello world.</p>');
    f.apply(f.take().items);
    f.w.eval(translator);
    assert.equal(f.w.__kitsugiWikiDomV1, f.api);
    f.configure(false);
    assert.equal(f.w.document.querySelector('p').textContent, 'Hello world.');
    f.close();
});

test('scanning yields when the time budget is spent instead of walking the whole article', () => {
    const f = fixture(Array.from({ length: 100 }, (_, i) => `<p>Paragraph ${i}.</p>`).join(''));
    let time = 0; f.w.performance.now = () => (time += 5);
    const batch = f.take();
    assert.equal(batch.items.length, 1);
    assert.equal(batch.status.scanning, true);
    f.close();
});

test('an article node moved into an editor while waiting is never overwritten', () => {
    const f = fixture('<p>Hello world.</p>');
    const pending = f.take().items;
    const p = f.w.document.querySelector('p');
    p.setAttribute('contenteditable', 'true');
    f.apply(pending, () => 'Must not enter editor');
    assert.equal(p.textContent, 'Hello world.');
    f.close();
});

test('original toggle respects externally edited text instead of overwriting a draft', () => {
    const f = fixture('<p>Hello world.</p>');
    f.apply(f.take().items);
    const p = f.w.document.querySelector('p');
    p.setAttribute('contenteditable', 'true');
    p.firstChild.nodeValue = 'My draft';
    f.configure(false);
    assert.equal(p.textContent, 'My draft');
    f.close();
});

test('oversized translator output is rejected without replacing original text', () => {
    const f = fixture('<p>Hello.</p>');
    f.apply(f.take().items, () => 'x'.repeat(10000));
    assert.equal(f.w.document.querySelector('p').textContent, 'Hello.');
    assert.equal(f.api.status().translated, 0);
    f.close();
});

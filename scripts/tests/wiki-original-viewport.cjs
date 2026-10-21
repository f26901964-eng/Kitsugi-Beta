// DOM regression checks for the ACTUAL script injected by WikiOriginalPage.
// Requires jsdom@26.1.0; see docs/WIKI_ORIGINAL_LAYOUT_2026-10-21.md.
// This is not an Android/WebView rendering or live-Fandom test.
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const { JSDOM } = require('jsdom');

const source = readFileSync(resolve(__dirname,
  '../../app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiOriginalPagePolicy.kt'), 'utf8');
const script = source.match(/const val DESKTOP_VIEWPORT_SCRIPT = """([\s\S]*?)"""/)[1];

function page(viewport = '<meta name="viewport" content="width=device-width, initial-scale=1">') {
  return new JSDOM(`<!doctype html><html class="theme-dark"><head>${viewport}
    <style>:root { --theme-page-background-color: #202c35; }
    .heading span { display: inline-block; color: #fff; background: #dd0055; }
    .duplicate { display: none; } .characters { display: grid; grid-template-columns: repeat(6, 1fr); }
    </style></head><body><main class="mw-parser-output">
      <h2 class="heading"><span>W</span><span>E</span><span>L</span><span>C</span><span>O</span><span>M</span><span>E</span></h2>
      <div class="duplicate">WELCOME</div>
      <section class="characters"><a href="/wiki/Kiyotaka_Ayanokoji#History"><img src="/portrait.png" width="180" height="360"></a></section>
      <table><tr><td rowspan="2">Light novel</td><td>Japanese</td></tr><tr><td>English</td></tr></table>
      <button id="tab" onclick="document.querySelector('#panel').hidden = false">Show panel</button>
      <div id="panel" hidden>Native site panel</div>
    </main></body></html>`, { url: 'https://you-zitsu.fandom.com/wiki/Home', runScripts: 'dangerously' });
}

test('desktop viewport changes without replacing article DOM or wiki styles', () => {
  const dom = page();
  const { document } = dom.window;
  const body = document.body.innerHTML;
  const style = document.querySelector('style').outerHTML;
  dom.window.eval(script);
  assert.equal(document.querySelector('meta[name=viewport]').content, 'width=1200');
  assert.equal(document.body.innerHTML, body);
  assert.equal(document.querySelector('style').outerHTML, style);
  assert.equal(document.documentElement.className, 'theme-dark');
  dom.window.close();
});

test('repeated finish callbacks do not duplicate viewport or content', () => {
  const dom = page();
  for (let i = 0; i < 5; i++) dom.window.eval(script);
  assert.equal(dom.window.document.querySelectorAll('meta[name=viewport]').length, 1);
  assert.equal(dom.window.document.querySelectorAll('h2').length, 1);
  assert.equal(dom.window.document.querySelector('h2').textContent, 'WELCOME');
  dom.window.close();
});

test('missing viewport is created without disabling user zoom', () => {
  const dom = page('');
  dom.window.eval(script);
  const content = dom.window.document.querySelector('meta[name=viewport]').content;
  assert.equal(content, 'width=1200');
  assert.ok(!content.includes('user-scalable=no'));
  assert.ok(!content.includes('maximum-scale'));
  dom.window.close();
});

test('theme-hidden duplicate remains hidden and article links retain anchors', () => {
  const dom = page();
  dom.window.eval(script);
  assert.equal(dom.window.getComputedStyle(dom.window.document.querySelector('.duplicate')).display, 'none');
  assert.equal(dom.window.document.querySelector('a').href,
    'https://you-zitsu.fandom.com/wiki/Kiyotaka_Ayanokoji#History');
  assert.equal(dom.window.document.querySelector('td').rowSpan, 2);
  assert.equal(dom.window.document.querySelector('img').getAttribute('width'), '180');
  dom.window.close();
});

test('original site event handlers still operate after viewport adjustment', () => {
  const dom = page();
  dom.window.eval(script);
  dom.window.document.querySelector('#tab').click();
  assert.equal(dom.window.document.querySelector('#panel').hidden, false);
  dom.window.close();
});

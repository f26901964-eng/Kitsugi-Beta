#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_embed_scanner.py — EmbedMediaScanner doğrulama koşum takımı
=================================================================

`app/src/main/java/com/kitsugi/animelist/data/cloudstream/embed/EmbedMediaScanner.kt`
mantığının birebir Python ikizidir ve gerçek Türkçe CDN/oynatıcı sayfası örnekleriyle test eder.

Neden var?
  Android/Kotlin derlemesi olmayan ortamlarda (CI, sandbox, kod incelemesi) tarayıcı
  regex'lerinin ve çözümleme mantığının doğruluğunu kanıtlamak için. Kotlin tarafındaki
  gerçek testler: `app/src/test/java/.../EmbedMediaScannerTest.kt`.

Kullanım:
    python3 scripts/verify_embed_scanner.py

Çıkış kodu 0 = tüm senaryolar geçti.
"""
import base64
import re
import sys
from urllib.parse import urlparse

# ── Regex tablosu (Kotlin ile aynı) ───────────────────────────────────────────

QUOTED_URL_REGEX = re.compile(
    r"""["']([^"'\s<>\\]{10,2048}?\.(?:m3u8|mp4|mpd|mkv|webm|ts|flv)(?:\?[^"'\s<>\\]{0,512})?)["']""",
    re.I,
)
BARE_URL_REGEX = re.compile(r"""https?://[^\s"'<>\\]{10,2048}""", re.I)
RELATIVE_URL_REGEX = re.compile(
    r"""["'](/(?:[^"'\s<>\\]{0,256})\.(?:m3u8|mp4|mpd|mkv|webm)(?:\?[^"'\s<>\\]{0,512})?)["']""",
    re.I,
)
STRUCTURED = [
    re.compile(r"""(?:file|source|src|url|hls|hlslink|dash|playlist|video_url|videoUrl|link|m3u8|mp4)\s*[:=]\s*["']([^"'\s]{10,2048})["']"""),
    re.compile(r"""<(?:source|video|iframe)[^>]{0,200}?src\s*=\s*["']([^"']{10,2048})["']""", re.I),
    re.compile(r"""data-(?:file|src|video|url|source|hls)\s*=\s*["']([^"'\s]{10,2048})["']""", re.I),
    re.compile(r"""(?:setSource|loadSource|setUrl|loadUrl|setupVideo|videoPlayer)\s*\(\s*["']([^"'\s]{10,2048})["']""", re.I),
]
IFRAME = re.compile(r"""<iframe[^>]{0,300}?src\s*=\s*["']([^"']{6,2048})["']""", re.I)
PACKED = re.compile(
    r"""}\s*\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:[^'\\]|\\.)*)'\s*\.split\('\|'\)""",
    re.S,
)
ATOB = re.compile(r"""(?:atob|ATOB|decode)\s*\(\s*["']([A-Za-z0-9+/=_\-]{24,8192})["']""", re.I)
BARE64 = re.compile(r"""["']([A-Za-z0-9+/=]{64,4096})["']""")
UNICODE_ESCAPE = re.compile(r"""\\u([0-9a-fA-F]{4})""")
HEX_ESCAPE = re.compile(r"""\\x([0-9a-fA-F]{2})""")
NUMERIC_ENTITY = re.compile(r"""&#(x?[0-9a-fA-F]{1,6});""")

MEDIA_EXT = (".m3u8", ".mp4", ".mpd", ".mkv", ".webm", ".flv", ".ts", ".m4v", ".mov", ".avi",
             "master.txt", "playlist.txt")
HARD_DENY = (".css", ".js", ".json", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
             ".woff", ".woff2", ".ttf", ".eot", ".html", ".htm", ".php", ".xml", ".vtt",
             ".srt", ".ass", ".ssa")
DENY_TOKENS = ("doubleclick", "googlesyndication", "adservice", "adsystem", "adserver",
               "/ads/", "advert", "banner", "popunder", "onclick", "click?",
               "google-analytics", "googletagmanager", "facebook.com/tr", "pixel.gif",
               "beacon", "analytics", "histats", "yandex.ru/metrika", "sentry", "crashlytics")
ALLOW_TOKENS = ("/hls/", "/dash/", "/stream/", "/playlist/", "master.txt", "playlist.txt",
                "index.m3u8", "playlist.m3u8", "manifest.mpd")
BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"


# ── Yardımcı fonksiyonlar (Kotlin ile aynı davranış) ──────────────────────────

def html_unescape(raw):
    s = (raw.replace("&amp;", "&").replace("&quot;", '"').replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", ""))

    def numeric(m):
        body = m.group(1)
        try:
            return chr(int(body[1:], 16) if body.lower().startswith("x") else int(body))
        except Exception:
            return m.group(0)

    s = NUMERIC_ENTITY.sub(numeric, s)
    return s.replace("%2F", "/").replace("%2f", "/").replace("%3F", "?").replace("%3D", "=")


def decode_escapes(raw):
    if "\\" not in raw:
        return raw
    s = (raw.replace("\\/", "/").replace('\\"', '"').replace("\\'", "'")
            .replace("\\n", "").replace("\\r", "").replace("\\t", "")
            .replace("\\u0026", "&").replace("\\u003d", "=").replace("\\u003f", "?"))
    s = UNICODE_ESCAPE.sub(lambda m: chr(int(m.group(1), 16)), s)
    s = HEX_ESCAPE.sub(lambda m: chr(int(m.group(1), 16)), s)
    return s


def absolutize(candidate, base):
    trimmed = candidate.strip().strip("\"'").strip(",;)]}")
    if not trimmed:
        return None
    low = trimmed.lower()
    if low.startswith("http://") or low.startswith("https://"):
        return trimmed
    if trimmed.startswith("//"):
        return "https:" + trimmed
    parsed = urlparse(base)
    if not parsed.hostname:
        return None
    if trimmed.startswith("/"):
        return f"{parsed.scheme}://{parsed.hostname}{trimmed}"
    base_path = parsed.path.rsplit("/", 1)[0] if "/" in parsed.path else ""
    return f"{parsed.scheme}://{parsed.hostname}{base_path}/{trimmed}"


def looks_like_media_url(url):
    low = url.lower()
    if not low.startswith("http"):
        return False
    if low.startswith("data:") or low.startswith("blob:"):
        return False
    path = urlparse(low).path or low.split("?")[0]
    if any(path.endswith(e) for e in HARD_DENY):
        return False
    if any(t in low for t in DENY_TOKENS):
        return False
    if any(path.endswith(e) for e in MEDIA_EXT):
        return True
    if any(t in low for t in ALLOW_TOKENS):
        return True
    return any(t in low for t in (".m3u8", ".mp4", ".mpd"))


def score_for(url):
    low = url.lower()
    path = urlparse(low).path or low.split("?")[0]
    score = 0
    if path.endswith(".m3u8"):
        score += 60
    elif ".m3u8" in low:
        score += 50
    elif path.endswith(".mpd"):
        score += 45
    elif path.endswith(".mp4"):
        score += 40
    elif path.endswith(".mkv"):
        score += 35
    elif path.endswith(".webm"):
        score += 30
    elif path.endswith(".flv"):
        score += 20
    elif path.endswith(".ts"):
        score += 15
    elif path.endswith("master.txt") or path.endswith("playlist.txt"):
        score += 55
    else:
        score += 10
    if "/hls/" in low:
        score += 8
    if "master" in low:
        score += 6
    if "index.m3u8" in low or "playlist.m3u8" in low:
        score += 4
    if "1080" in low or "720" in low:
        score += 3
    if "http://" in low:
        score -= 2
    if any(t in low for t in DENY_TOKENS):
        score -= 100
    if "trailer" in low or "fragman" in low:
        score -= 30
    if "preview" in low or "thumb" in low or "sprite" in low:
        score -= 40
    return score


def looks_like_player_page(text):
    t = text.lower()
    return any(k in t for k in (
        "<video", "jwplayer", "hls.js", "hls.min.js", "dash.js", "videojs", "clappr",
        "player.setup", "hlsurl", "sources:", "fluidplayer", "plyr", "apirequest",
    )) or ("iframe" in t and "player" in t)


def unpack_packed_js(text):
    """Dean Edwards p.a.c.k.e.r çözücüsü — token → sözlük kelimesi."""
    match = PACKED.search(text)
    if not match:
        return None
    payload_escaped, a, c, words_raw = match.group(1), int(match.group(2)), int(match.group(3)), match.group(4)
    words = words_raw.split("|")
    if a < 1 or c <= 0 or not words:
        return None

    def decode_js_string(raw):
        out, i = [], 0
        while i < len(raw):
            ch = raw[i]
            if ch == "\\" and i + 1 < len(raw):
                nxt = raw[i + 1]
                if nxt == "n":
                    out.append("\n"); i += 2
                elif nxt == "r":
                    out.append("\r"); i += 2
                elif nxt in ("'", '"', "\\", "/"):
                    out.append(nxt); i += 2
                elif nxt == "x":
                    try:
                        out.append(chr(int(raw[i + 2:i + 4], 16))); i += 4
                    except Exception:
                        out.append(nxt); i += 2
                elif nxt == "u":
                    try:
                        out.append(chr(int(raw[i + 2:i + 6], 16))); i += 6
                    except Exception:
                        out.append(nxt); i += 2
                else:
                    out.append(nxt); i += 2
            else:
                out.append(ch); i += 1
        return "".join(out)

    payload = decode_js_string(payload_escaped)

    def encode(n):
        base = "" if n < a else encode(n // a)
        digit = n % a
        return base + (chr(digit + 29) if digit > 35 else BASE36[digit])

    dictionary = {}
    for i in range(c - 1, -1, -1):
        word = words[i] if i < len(words) and words[i] else encode(i)
        dictionary[i] = word

    result = payload
    for i in range(c - 1, -1, -1):
        word = dictionary.get(i)
        if not word:
            continue
        token = encode(i)
        if token == word:
            continue
        result = re.sub(r"\b" + re.escape(token) + r"\b", word, result)
    return result


def scan(html, base_url):
    """EmbedMediaScanner.scan() ikizi. (media, iframes, looks_like_player) döner."""
    if not html:
        return [], [], False

    scores, iframes = {}, []
    packed = unpack_packed_js(html)
    has_escapes = ("\\/" in html) or ("\\u002F" in html) or ("\\u002f" in html) or \
                  ("\\x2F" in html) or ("\\x2f" in html)
    parts = [html]
    if packed:
        parts.append(packed)
    if has_escapes:
        parts.append(decode_escapes(html))
    work = "\n".join(parts)

    def consider(raw, reason, bonus=0):
        decoded = decode_escapes(html_unescape(raw)).strip()
        if len(decoded) < 8 or len(decoded) > 4096:
            return
        absolute = absolutize(decoded, base_url)
        if not absolute or not looks_like_media_url(absolute):
            return
        score = score_for(absolute) + bonus
        if score <= 0:
            return
        if absolute not in scores or scores[absolute][0] < score:
            scores[absolute] = (score, reason)

    for m in QUOTED_URL_REGEX.finditer(work):
        consider(m.group(1), "raw")
    for m in BARE_URL_REGEX.finditer(work):
        consider(m.group(0), "raw:bare")
    for m in RELATIVE_URL_REGEX.finditer(work):
        consider(m.group(1), "raw:rel")
    for rx in STRUCTURED:
        for m in rx.finditer(work):
            group = m.group(1) if m.groups() else m.group(0)
            consider(group, "structured", bonus=12)

    for m in IFRAME.finditer(work):
        absolute = absolutize(m.group(1), base_url)
        if absolute and absolute.startswith("http") and base_url.split("?")[0] not in absolute:
            iframes.append(absolute)

    decoded_blobs = []
    for m in ATOB.finditer(work):
        try:
            payload = m.group(1).translate(str.maketrans("-_", "+/"))
            payload += "=" * ((4 - len(payload) % 4) % 4)
            decoded_blobs.append(base64.b64decode(payload).decode("utf-8", "ignore"))
        except Exception:
            pass
    if not decoded_blobs:
        for m in list(BARE64.finditer(work))[:6]:
            try:
                cand = base64.b64decode(m.group(1) + "=" * ((4 - len(m.group(1)) % 4) % 4))
                text = cand.decode("utf-8", "ignore")
                if "http" in text or ".m3u8" in text or ".mp4" in text:
                    decoded_blobs.append(text)
            except Exception:
                pass
    if decoded_blobs:
        blob = "\n".join(decoded_blobs)
        for m in QUOTED_URL_REGEX.finditer(blob):
            consider(m.group(1), "base64")
        for m in BARE_URL_REGEX.finditer(blob):
            consider(m.group(0), "base64:bare")

    media = sorted(((u, v[0], v[1]) for u, v in scores.items()), key=lambda t: (-t[1], t[0]))
    return media, iframes, looks_like_player_page(work)


# ── Senaryolar ────────────────────────────────────────────────────────────────

PACKED_REAL = (
    r"""eval(function(p,a,c,k,e,d){e=function(c){return(c<a?'':e(parseInt(c/a)))+((c=c%a)>35?"""
    r"""String.fromCharCode(c+29):c.toString(36))};while(c--){if(k[c]){p=p.replace("""
    r"""new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}("""
    r"""'0("v").1({2:"3://4.5.6/7/8/9/a.b?c=d"});',36,14,"""
    r"""'jwplayer|setup|file|https|s1|molystream|org|hls|x9|720|index|m3u8|h|abc'.split('|')))"""
)

_ATOB_INNER = '{"sources":[{"file":"https://cdn3.alions.pro/stream/sd/abc/master.m3u8","label":"720p"}]}'
_ATOB_B64 = base64.b64encode(_ATOB_INNER.encode()).decode()

SCENARIOS = {
    "jwplayer_token_hls": (
        """<html><head><script src="/jwplayer.js"></script></head><body>
<div id="player"></div><iframe src="//videoseyred.in/embed/abc123"></iframe>
<script>
jwplayer("player").setup({file:"https://cdn1.videoseyred.in/hls/abc123/1080/master.m3u8?token=eyJhbGciOi&expires=1730000000",
image:"https://cdn1.videoseyred.in/img/poster.jpg",
sources:[{file:"https://cdn1.videoseyred.in/hls/abc123/720/index.m3u8"}]});
</script></body></html>""",
        "https://videoseyred.in/embed/abc123",
        ["https://cdn1.videoseyred.in/hls/abc123/1080/master.m3u8?token=eyJhbGciOi&expires=1730000000"],
    ),
    "packed_js": (
        "<html><body><script>%s</script></body></html>" % PACKED_REAL,
        "https://player.molystream.org/e/1",
        ["https://s1.molystream.org/hls/x9/720/index.m3u8?h=abc"],
    ),
    "atob_base64": (
        '<html><body><script>var data = atob("%s");</script></body></html>' % _ATOB_B64,
        "https://alions.pro/embed/xyz",
        ["https://cdn3.alions.pro/stream/sd/abc/master.m3u8"],
    ),
    "escaped_relative": (
        """<html><body><script>
var src = "https:\\/\\/trstx.org\\/hls\\/live\\/ch1\\/index.m3u8?e=1&a=2";
var other = '\\/\\/closeload.top\\/vod\\/hd\\/film.mp4';
</script></body></html>""",
        "https://trstx.org/player.php?id=5",
        ["https://trstx.org/hls/live/ch1/index.m3u8?e=1&a=2",
         "https://closeload.top/vod/hd/film.mp4"],
    ),
    "video_tag_ads": (
        """<html><body>
<video controls poster="/img/p.jpg"><source src="/videos/film-1080.mp4" type="video/mp4"></video>
<script src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"></script>
<img src="/banner/ads-300x250.png"><a href="/click?ad=1">reklam</a>
<script>var _gaq='https://www.google-analytics.com/analytics.js';</script>
</body></html>""",
        "https://gstore.one/izle/film",
        ["https://gstore.one/videos/film-1080.mp4"],
    ),
    "data_attr": (
        '<html><body><div id="p" data-video="//playmogo.com/e/abc123" data-hls="/hls/abc/master.m3u8"></div></body></html>',
        "https://doodstream.com/e/abc123",
        ["https://doodstream.com/hls/abc/master.m3u8"],
    ),
    "ad_vs_real": (
        """<html><script>
var pre = "https://ads.adserver.com/preroll/trailer.m3u8";
jwplayer("v").setup({file:"https://cdn2.pichive.cc/vod/999/1080p.m3u8"});
</script></html>""",
        "https://pichive.cc/embed/999",
        ["https://cdn2.pichive.cc/vod/999/1080p.m3u8"],
    ),
}

IFRAME_ONLY = (
    '<html><body><iframe src="https://vidmoly.to/embed-x1y2z3.html" allowfullscreen></iframe></body></html>',
    "https://hdfilmcehennemi.nl/film/x",
    ["https://vidmoly.to/embed-x1y2z3.html"],
)


def main():
    passed = failed = 0
    print("=" * 78)
    print("EmbedMediaScanner doğrulama koşum takımı (Kotlin mantığının Python ikizi)")
    print("=" * 78)

    for name, (html, base, expected) in SCENARIOS.items():
        media, iframes, player = scan(html, base)
        urls = [m[0] for m in media]
        ok = bool(urls) and all(e in urls for e in expected) and urls[0] in expected
        print(("✅" if ok else "❌"), f"{name:20s} media={len(urls)} iframe={len(iframes)} player={player}")
        if not ok:
            print("     beklenen:", expected)
            print("     bulunan :", urls[:5])
        passed, failed = (passed + 1, failed) if ok else (passed, failed + 1)

    # iframe-only: medya beklenmez, zincir için iframe beklenir
    html, base, expected_iframes = IFRAME_ONLY
    media, iframes, _ = scan(html, base)
    ok = not media and iframes == expected_iframes
    print(("✅" if ok else "❌"), f"{'iframe_chain':20s} media=0 iframe={len(iframes)} (zincir takibi)")
    passed, failed = (passed + 1, failed) if ok else (passed, failed + 1)

    # packer çözücüsü birebir doğrulama
    expected_js = 'jwplayer("v").setup({file:"https://s1.molystream.org/hls/x9/720/index.m3u8?h=abc"});'
    unpacked = unpack_packed_js(PACKED_REAL)
    ok = unpacked == expected_js
    print(("✅" if ok else "❌"), "packer token→kelime çözümü")
    if not ok:
        print("     beklenen:", expected_js)
        print("     bulunan :", unpacked)
    passed, failed = (passed + 1, failed) if ok else (passed, failed + 1)

    # negatif kontroller
    negatives = [
        ("https://site.com/style.css", False),
        ("https://site.com/app.js?v=3", False),
        ("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", False),
        ("not-a-url", False),
        ("https://cdn/x/master.m3u8?a=1", True),
        ("https://cdn/x/hls/1080/index.m3u8", True),
    ]
    for url, expected in negatives:
        got = looks_like_media_url(url)
        ok = got == expected
        print(("✅" if ok else "❌"), f"looksLikeMediaUrl({url[:46]:46s}) = {got}")
        passed, failed = (passed + 1, failed) if ok else (passed, failed + 1)

    print("-" * 78)
    print(f"SONUÇ: {passed} geçti, {failed} kaldı")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())

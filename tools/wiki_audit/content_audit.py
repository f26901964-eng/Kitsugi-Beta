#!/usr/bin/env python3
"""
Kitsugi - Fandom wiki ICERIK denetimi (canli).

`audit.py` "wiki bulunuyor mu" sorusunu olcer. Bu arac ikinci soruyu olcer:

    "Bulunan sayfada MediaWiki'nin verdigi her sey aliniyor ve uygulama icinde
     gosteriliyor mu? Hangi veri kaybi hangi adimda oluyor?"

Yontem: her ornek sayfa icin
  1. GERCEK (ground truth) toplanir: `action=parse&prop=sections|text|wikitext|images|
     templates|categories|links` ve `action=query&prop=pageprops|pageimages|videoinfo`.
  2. UYGULAMA boru hatti Python'a birebir tasinir: ayni uc noktalar, ayni parametreler,
     ayni cap/sinir sabitleri ve ayni Jsoup secici listeleri
     (bkz. app/src/main/java/com/kitsugi/animelist/data/fandom/FandomApi.kt,
     FandomHtmlText.kt, FandomInfoboxParser.kt, FandomRepository.kt).
  3. Ikisi karsilastirilir; her fark "kayip nedeni" etiketiyle raporlanir.

Kayip nedenleri (Kotlin tarafindaki sabite baglidir):
  SKIP_TEMPLATE_SECTION  : parseSections index'i Int'e ceviremiyor -> T-x bolumleri atiliyor
  SKIP_HIDDEN_SECTION    : FandomSpoilerRules.isHiddenSection -> alt agacla birlikte atiliyor
  CAP_SECTION_IMAGES     : MAX_SECTION_IMAGES = 24
  CAP_BLOCKS             : maxBlocks = 400
  CAP_TABLE_ROWS         : handleContentTable take(150)
  DROP_COLLAPSIBLE       : .mw-collapsible-toggle selector
  DROP_TABBER            : .wds-tabs__wrapper selector
  DROP_NAVBOX_INFOBOX    : .navbox / .portable-infobox / aside selector
  DROP_VIDEO             : data-video-key atlanir, video blogu gosterilmez
  SMALL_IMAGE_FILTER     : width<32 && height<32
  CAP_GALLERY_UI         : ArticleInfoboxCard images.take(8)
  NO_LEAD_IN_READER      : okuyucu giris (section 0) otomatik yuklenmez

Kullanim: python3 content_audit.py [--out DIR] [--wikis w1,w2] [--pages-per-wiki N]
Yalnizca standart kutuphane kullanir.
"""
import argparse
import concurrent.futures as cf
import json
import os
import re
import sys
import threading
import time
import urllib.parse
import urllib.request

# Boruya (tee) yazilirken Python blok tamponlamasi kullanir; ozet satirlari is
# gunlugune hic dusmeyebiliyordu.
sys.stdout.reconfigure(line_buffering=True)

from html import unescape
from html.parser import HTMLParser

UA = "Kitsugi/2.4.283 (Android; +https://github.com/f26901964-eng/Kitsugi-Beta) content-audit"
HERE = os.path.dirname(os.path.abspath(__file__))

# Uygulamadaki sabitler KOTLIN KAYNAGINDAN okunur: arac koddan sapmasin diye.
KOTLIN_ROOT = os.path.join(
    os.path.abspath(os.path.join(HERE, "..", "..")),
    "app", "src", "main", "java", "com", "kitsugi", "animelist")


def _kotlin_text(relative):
    path = os.path.join(KOTLIN_ROOT, relative)
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def _kotlin_const(relative, name, cast=int):
    body = _kotlin_text(relative)
    match = re.search(rf"{name}\s*=\s*([0-9_]+)", body)
    if not match:
        raise SystemExit(f"{relative} icinde {name} bulunamadi")
    return cast(match.group(1).replace("_", ""))


def _kotlin_selector_list(relative, name):
    """Kotlin'deki cok satirli secici sabitini tek listeye cevirir.

    `SECTION_JUNK_SELECTOR = JUNK_SELECTOR + ", ..."` bicimindeki zincirleme tanimlarda
    onceki sabitin adi da gorulur; o sabit ayrica okunup listelenir.
    """
    body = _kotlin_text(relative)
    # YALNIZCA satir basindaki sabit tanimi: `wikiLinks()` icindeki satir ici
    # "JUNK_SELECTOR + …" ifadesi tanim degildir ve yanlis eslesirse arac eski
    # secici listesini okur.
    match = re.search(
        rf'^[ \t]*private const val {name}\s*=\s*(.*?)(?=\n[ \t]*(?:private\s+)?(?:const\s+)?val\s|\n[ \t]*private\s+fun)',
        body, re.S | re.M)
    if not match:
        raise SystemExit(f"{relative} icinde {name} bulunamadi")
    expression = match.group(1)
    # Her Kotlin dizesi virgülle ayrilmis birden cok secici tasir; tek tek bölünür.
    selectors = []
    for literal in re.findall(r'"([^"]*)"', expression):
        for part in literal.split(","):
            part = part.strip()
            if part:
                selectors.append(part)
    base = re.match(r"\s*([A-Z_]+)\s*\+", expression)
    if base:
        return _kotlin_selector_list(relative, base.group(1)) + selectors
    return selectors


HTML_TEXT = os.path.join("data", "fandom", "FandomHtmlText.kt")
MAX_SECTION_IMAGES = _kotlin_const(HTML_TEXT, "MAX_SECTION_IMAGES")
MAX_BLOCKS = _kotlin_const(HTML_TEXT, "MAX_BLOCKS")
MAX_TABLE_ROWS = _kotlin_const(HTML_TEXT, "MAX_TABLE_ROWS")
UI_INFOBOX_IMAGES = 8            # MediaWikiTabContent ArticleInfoboxCard images.take(8)
UI_INFOBOX_FIELDS = 10           # ArticleInfoboxCard minOf(fields.size, 10) + "tumunu goster"
READER_LINK_CHIPS = 12           # ReaderSectionCard links.take(12)
RELATED_TITLES = 30              # loadRelatedPages candidates.take(30)
GALLERY_API_LIMIT = 300          # FandomApi.pageImages(maxImages=300)LEADER_LINK_CHIPS = 12           # ReaderSectionCard links.take(12)
RELATED_TITLES = 30              # loadRelatedPages candidates.take(30)

# FandomHtmlText.JUNK_SELECTOR + SECTION_JUNK_SELECTOR: Kotlin sabitinden okunur.
# (Burada elle liste TUTULMAZ: Kotlin değişince aracın eski davranışı ölçmesi,
#  denetimin kendisini geçersiz kılar.)
JUNK = _kotlin_selector_list(HTML_TEXT, "JUNK_SELECTOR")
SECTION_JUNK = _kotlin_selector_list(HTML_TEXT, "SECTION_JUNK_SELECTOR")

# FandomSpoilerRules (Kotlin'deki listelerin kopyasi)
HIDDEN_SECTIONS = {
    "trivia", "galeri", "gallery", "references", "kaynakca", "kaynakça", "navigation",
    "site navigation", "external links", "disite baglantilar", "dış bağlantılar",
}
SPOILER_SECTIONS = {"plot", "story", "hikaye", "hikâye", "synopsis", "ozet", "özet", "summary"}

VOID_TAGS = {
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
    "source", "track", "wbr",
}

DEFAULT_WIKIS = [
    "naruto", "onepiece", "jujutsu-kaisen", "kimetsu-no-yaiba", "myheroacademia",
    "attackontitan", "gintama", "solo-leveling", "chainsawman", "bleach",
    "fairytail", "onepunchman",
]

_sem = threading.Semaphore(3)
_cache = {}
_cache_lock = threading.Lock()
_host_last = {}
_host_lock = threading.Lock()
_fails = {}


# --------------------------------------------------------------------------- HTTP
def _throttle(host):
    with _host_lock:
        wait = 0.2 - (time.time() - _host_last.get(host, 0))
        _host_last[host] = time.time() + max(wait, 0.0)
    if wait > 0:
        time.sleep(wait)


def http_json(url, retries=3, timeout=25):
    with _cache_lock:
        if url in _cache:
            return _cache[url]
    host = urllib.parse.urlparse(url).netloc
    body = None
    for attempt in range(retries + 1):
        _throttle(host)
        with _sem:
            try:
                req = urllib.request.Request(
                    url, headers={"User-Agent": UA, "Accept": "application/json"})
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    body = r.read().decode("utf-8", "replace")
                break
            except Exception as exc:  # noqa: BLE001
                key = f"{host} {type(exc).__name__}"
                _fails[key] = _fails.get(key, 0) + 1
                if attempt == retries:
                    return None
                time.sleep(1.5 * (attempt + 1))
    if body is None:
        return None
    try:
        parsed = json.loads(body)
    except Exception:  # noqa: BLE001
        return None
    with _cache_lock:
        _cache[url] = parsed
    return parsed


def api(wiki, params):
    base = f"https://{wiki}.fandom.com/api.php"
    qs = urllib.parse.urlencode(params)
    return http_json(f"{base}?{qs}")


# --------------------------------------------------------------------------- mini DOM
class Node:
    __slots__ = ("tag", "attrs", "children", "parent", "text")

    def __init__(self, tag, attrs=None, parent=None):
        self.tag = tag
        self.attrs = dict(attrs or {})
        self.children = []
        self.parent = parent
        self.text = None

    def classes(self):
        return set(self.attrs.get("class", "").split())

    def attr(self, name, default=""):
        return self.attrs.get(name, default)

    def walk(self):
        for child in self.children:
            yield child
            if isinstance(child, Node):
                yield from child.walk()

    def select(self, predicate):
        return [n for n in self.walk() if isinstance(n, Node) and predicate(n)]

    def text_of(self):
        if self.text is not None:
            return self.text
        return "".join(c.text_of() if isinstance(c, Node) else c for c in self.children)


class DomBuilder(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node("#root")
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        node = Node(tag, attrs, self.stack[-1])
        self.stack[-1].children.append(node)
        if tag not in VOID_TAGS:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.stack[-1].children.append(Node(tag, attrs, self.stack[-1]))

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return

    def handle_data(self, data):
        self.stack[-1].children.append(data)


def parse_html(fragment):
    builder = DomBuilder()
    builder.feed(fragment or "")
    builder.close()
    return builder.root


def _match_simple(node, sel):
    sel = sel.strip()
    if not sel:
        return False
    if sel.startswith("#"):
        return node.attr("id") == sel[1:]
    if "[" in sel:
        m = re.match(r"^([a-z0-9]*)\[([a-zA-Z-]+)(\^=)?\"?([^\"\]]*)\"?]$", sel)
        if not m:
            return False
        tag, attr, starts, value = m.groups()
        if tag and node.tag != tag:
            return False
        have = node.attr(attr, None)
        if have is None:
            return False
        return have.startswith(value) if starts else have == value
    if sel.startswith("."):
        return sel[1:] in node.classes()
    return node.tag == sel


def selector_match(node, selector_list):
    return any(_match_simple(node, s) for s in selector_list if s.strip())


def remove_matching(root, selector_list):
    removed = []
    for node in list(root.walk()):
        if not isinstance(node, Node):
            continue
        if selector_match(node, selector_list):
            removed.append(node)
    for node in removed:
        if node.parent is not None and node in node.parent.children:
            node.parent.children.remove(node)
    return removed


# --------------------------------------------------------------------------- uygulama boru hatti
REF_MARKERS = re.compile(
    r"\[\s*(?:\d+|[a-z]|note\s*\d+|citation needed|kaynak belirtilmeli)\s*]", re.I)


def tidy_inline(raw):
    text = REF_MARKERS.sub("", raw or "")
    text = text.replace("\n", " ").replace("\u00a0", " ")
    text = re.sub(r"[ \t\u2009\u200a]+", " ", text)
    return text.strip()


def inline_text(node):
    return tidy_inline(node.text_of())


def is_spoiler_section(title):
    t = title.lower().strip()
    return t in SPOILER_SECTIONS or t.startswith("plot") or t.startswith("story")


def is_hidden_section(title):
    return title.lower().strip() in HIDDEN_SECTIONS


def parse_sections_like_app(payload):
    """FandomApiParsers.parseSections birebir.

    Duzeltme sonrasi davranis: sayisal olmayan kimlikler ("T-1") KORUNUR; yalnizca gizli
    bolumler (Trivia/Galeri/Kaynakca...) ve alt agacları atilir.
    """
    if not payload or "error" in payload:
        return None, []
    sections = (payload.get("parse") or {}).get("sections") or []
    kept, dropped = [], []
    ancestors = []  # (level, spoiler, hidden)
    for section in sections:
        raw_index = str(section.get("index", "")).strip()
        if not raw_index:
            continue
        level = int(section.get("level") or 0) or (int(section.get("toclevel") or 0) + 1)
        title = strip_tags(section.get("line", ""))
        while ancestors and ancestors[-1][0] >= level:
            ancestors.pop()
        parent = ancestors[-1] if ancestors else None
        spoiler = bool(parent and parent[1]) or is_spoiler_section(title)
        hidden = bool(parent and parent[2]) or is_hidden_section(title)
        if title.strip() and not hidden:
            kept.append({"index": raw_index, "title": title, "level": level,
                         "spoiler": spoiler,
                         "template": raw_index.isdigit() is False})
        elif title.strip() and hidden:
            dropped.append({"index": raw_index, "line": title, "level": level,
                            "reason": "SKIP_HIDDEN_SECTION"})
        ancestors.append((level, spoiler, hidden))
    return kept, dropped


def strip_tags(html):
    return tidy_inline(re.sub(r"<[^>]+>", "", unescape(html or "")))


class BlockCollector:
    """FandomHtmlText.BlockCollector'in Python karsiligi."""

    BLOCKISH_INLINE = {"li", "p", "div", "dd", "dt", "tr", "ul", "ol", "dl", "blockquote"}
    INLINE_TAGS = {
        "a", "b", "i", "span", "em", "strong", "small", "sup", "sub", "font", "abbr",
        "code", "s", "u", "q", "cite", "mark", "time", "big", "bdi", "bdo", "kbd",
        "var", "wbr", "ruby", "label", "nobr", "tt", "del", "ins",
    }

    def __init__(self, max_blocks=MAX_BLOCKS, max_images=MAX_SECTION_IMAGES):
        self.out = []
        self.max_blocks = max_blocks
        self.max_images = max_images
        self.images_seen = 0
        self.images_dropped_cap = 0
        self.images_dropped_small = 0
        self.seen_images = set()
        self.table_rows_dropped = 0

    def walk(self, node):
        for child in node.children:
            if len(self.out) >= self.max_blocks:
                return
            if isinstance(child, Node):
                self.handle(child)
            else:
                pass  # TextNode: metin yalnizca p/li/td icinde toplanir

    def handle(self, el):
        tag = el.tag
        if self._is_image_container(el):
            self.emit_images(el)
            return
        if tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            self.add("HEADING", inline_text(el))
        elif tag == "p":
            self.add("PARAGRAPH", inline_text(el))
            self.emit_images(el)
        elif tag in ("ul", "ol"):
            for item in el.children:
                if isinstance(item, Node) and item.tag == "li":
                    self.handle_li(item)
        elif tag == "li":
            self.handle_li(el)
        elif tag == "blockquote":
            self.add("QUOTE", inline_text(el))
            self.emit_images(el)
        elif tag == "dl":
            for child in el.children:
                if not isinstance(child, Node):
                    continue
                if child.tag == "dt":
                    self.add("HEADING", inline_text(child))
                elif child.tag == "dd":
                    self.add("PARAGRAPH", inline_text(child))
                else:
                    self.walk(child)
        elif tag == "table":
            self.handle_table(el)
        elif tag == "img":
            self.emit_images(el)
        elif tag in self.INLINE_TAGS:
            self.add("PARAGRAPH", inline_text(el))
        else:
            self.walk(el)

    def handle_li(self, item):
        self.add("BULLET", inline_text(item))
        self.emit_images(item)
        for child in item.children:
            if isinstance(child, Node) and child.tag in ("ul", "ol"):
                for sub in child.children:
                    if isinstance(sub, Node) and sub.tag == "li":
                        self.handle_li(sub)

    def handle_table(self, table):
        rows = [n for n in table.select(lambda n: n.tag == "tr")][:MAX_TABLE_ROWS]
        all_rows = [n for n in table.select(lambda n: n.tag == "tr")]
        self.table_rows_dropped += max(0, len(all_rows) - len(rows))
        for row in rows:
            if len(self.out) >= self.max_blocks:
                return
            cells = [c for c in row.children if isinstance(c, Node) and c.tag in ("td", "th")]
            values = [inline_text(c) for c in cells]
            if not any(v.strip() for v in values):
                continue
            self.add("BULLET", " · ".join(v for v in values if v.strip()))
            self.emit_images(row)

    def _is_image_container(self, el):
        return el.tag == "figure" or bool(
            el.classes() & {"gallery", "wikia-gallery", "thumb", "gallerybox"})

    def emit_images(self, container):
        images = [container] if container.tag == "img" else \
            [n for n in container.select(lambda n: n.tag == "img")]
        for img in images:
            if self.images_seen >= self.max_images or len(self.out) >= self.max_blocks:
                self.images_dropped_cap += 1
                continue
            url = self._image_url(img)
            if not url or url in self.seen_images:
                continue
            width = img.attr("data-width") or img.attr("width")
            height = img.attr("data-height") or img.attr("height")
            if width.isdigit() and height.isdigit() and int(width) < 32 and int(height) < 32:
                self.images_dropped_small += 1
                continue
            self.seen_images.add(url)
            self.images_seen += 1
            self.out.append({"kind": "IMAGE", "text": "", "url": url})

    @staticmethod
    def _image_url(img):
        for key in ("data-src", "data-original", "src"):
            value = img.attr(key)
            if value and not value.startswith("data:"):
                return value
        srcset = img.attr("data-srcset") or img.attr("srcset")
        if srcset:
            return srcset.split(",")[0].strip().split(" ")[0]
        return ""

    def add(self, kind, text):
        if len(self.out) >= self.max_blocks:
            return
        text = tidy_inline(text)
        if not text or not any(c.isalnum() for c in text):
            return
        self.out.append({"kind": kind, "text": text})


def app_blocks(html):
    root = parse_html(html)
    removed = remove_matching(root, SECTION_JUNK)
    collector = BlockCollector()
    collector.walk(root)
    return collector, removed


def count_visible_text(html):
    """Bolumun, uygulamanin selector'lari sonrasinda kalan gorunur metin hacmi."""
    root = parse_html(html)
    remove_matching(root, SECTION_JUNK)
    return len(tidy_inline(root.text_of()))


def count_all_text(html):
    """MediaWiki'nin gonderdigi ham HTML'in (junk dahil) gorunur metin hacmi."""
    root = parse_html(html)
    remove_matching(root, JUNK)
    return len(tidy_inline(root.text_of()))


def count_table_rows(html):
    root = parse_html(html)
    remove_matching(root, SECTION_JUNK)
    return len([n for n in root.select(lambda n: n.tag == "tr")])


def count_videos(html):
    root = parse_html(html)
    vids = [n for n in root.walk()
            if isinstance(n, Node) and (n.attr("data-video-key") or "video" in n.classes())]
    return len(vids)


def count_internal_links(html):
    root = parse_html(html)
    remove_matching(root, SECTION_JUNK)
    titles = []
    for a in root.select(lambda n: n.tag == "a" and n.attr("href")):
        href = a.attr("href")
        idx = href.find("/wiki/")
        if idx < 0:
            continue
        tail = href[idx + 6:].split("#")[0]
        if not tail:
            continue
        title = urllib.parse.unquote(tail).replace("_", " ")
        titles.append(title)
    return titles


def parse_infobox_json_like_app(raw):
    """FandomInfoboxParser.parse karsiligi: alan/gorsel sayisi."""
    if not raw:
        return {"fields": 0, "images": 0}
    try:
        boxes = json.loads(raw)
    except Exception:  # noqa: BLE001
        return {"fields": 0, "images": 0}
    fields = images = 0

    def walk(items, depth=0):
        nonlocal fields, images
        if not isinstance(items, list) or depth > 6:
            return
        for item in items:
            if not isinstance(item, dict):
                continue
            kind = item.get("type")
            data = item.get("data")
            if kind == "image":
                entries = data if isinstance(data, list) else [data]
                for entry in entries:
                    if isinstance(entry, dict) and not entry.get("isVideo") and entry.get("url"):
                        images += 1
            elif kind == "data":
                if isinstance(data, dict) and str(data.get("value") or "").strip():
                    fields += 1
            elif kind == "group":
                walk((data or {}).get("value"), depth + 1)
            elif kind not in ("title", "header", "navigation"):
                if isinstance(data, dict):
                    walk(data.get("value"), depth + 1)
                elif isinstance(data, list):
                    walk(data, depth + 1)

    for box in boxes if isinstance(boxes, list) else [boxes]:
        walk((box or {}).get("data"))
    return {"fields": fields, "images": min(images, MAX_SECTION_IMAGES)}


# --------------------------------------------------------------------------- sayfa denetimi
def audit_page(wiki, title):
    rec = {"wiki": wiki, "title": title}
    meta = api(wiki, [
        ("action", "query"), ("titles", title), ("redirects", "1"),
        ("prop", "pageprops|pageimages|images|videoinfo"),
        ("ppprop", "infoboxes|fandomdescription|disambiguation"),
        ("piprop", "thumbnail"), ("pithumbsize", "480"),
        ("imlimit", "500"), ("format", "json"), ("formatversion", "2"),
    ])
    pages = ((meta or {}).get("query") or {}).get("pages") or []
    if not pages:
        rec["error"] = "no-page"
        return rec
    page = pages[0]
    if page.get("missing"):
        rec["error"] = "missing"
        return rec
    resolved = page.get("title", title)
    rec["resolved"] = resolved
    rec["files_on_page"] = len(page.get("images") or [])
    rec["videos_on_page"] = len(page.get("videoinfo") or [])

    props = page.get("pageprops") or {}
    raw_infobox = props.get("infoboxes")
    rec["infobox_json"] = parse_infobox_json_like_app(raw_infobox)
    rec["fandomdescription_len"] = len((props.get("fandomdescription") or "").strip())

    parsed = api(wiki, [
        ("action", "parse"), ("page", resolved),
        ("prop", "sections|text|wikitext"), ("redirects", "1"),
        ("format", "json"), ("formatversion", "2"),
    ]) or {}
    parse_body = parsed.get("parse") or {}
    full_html = parse_body.get("text") or ""
    wikitext = parse_body.get("wikitext") or ""

    kept, dropped = parse_sections_like_app(parsed)
    rec["sections_total"] = len(parse_body.get("sections") or [])
    rec["sections_kept"] = len(kept or [])
    rec["sections_dropped"] = dropped
    rec["sections_template"] = len([d for d in dropped if d["reason"] == "SKIP_TEMPLATE_SECTION"])
    rec["sections_hidden"] = len([d for d in dropped if d["reason"] == "SKIP_HIDDEN_SECTION"])

    # wikitext gercekleri
    rec["wt_templates"] = len(set(re.findall(r"\{\{\s*([^|{}\n]+)", wikitext)))
    rec["wt_images"] = len(re.findall(r"\[\[\s*(?:File|Image):", wikitext, re.I))
    rec["wt_galleries"] = len(re.findall(r"<gallery", wikitext, re.I))
    rec["wt_tables"] = len(re.findall(r"^\{\\|", wikitext, re.M)) + wikitext.count("{|")
    rec["wt_categories"] = len(re.findall(r"\[\[\s*Category:", wikitext, re.I))
    rec["wt_videos"] = len(re.findall(r"\{\{\s*(?:Video|FandomVideo)", wikitext, re.I))
    rec["wt_chars"] = len(wikitext)
    rec["wt_collapsible"] = wikitext.lower().count("mw-collapsible") + \
        len(re.findall(r"\{\{\s*Collapsible", wikitext, re.I))
    rec["wt_tabber"] = len(re.findall(r"\{\{\s*Tab", wikitext, re.I)) + \
        wikitext.lower().count("wds-tabber")
    rec["wt_refs"] = len(re.findall(r"<ref", wikitext, re.I))

    # tam sayfa HTML gercekleri
    rec["html_images_all"] = len([n for n in parse_html(full_html).select(
        lambda n: n.tag == "img")])
    rec["html_videos_all"] = count_videos(full_html)
    root_full = parse_html(full_html)
    rec["html_collapsible"] = len([n for n in root_full.select(
        lambda n: "mw-collapsible" in n.classes())])
    rec["html_tabber"] = len([n for n in root_full.select(
        lambda n: bool(n.classes() & {"wds-tabs__wrapper", "wds-tabber", "pi-panel"})
        or "wds-tabs" in n.attr("class"))])
    rec["html_navbox"] = len([n for n in root_full.select(
        lambda n: "navbox" in n.classes())])
    rec["html_tables_all"] = len([n for n in root_full.select(lambda n: n.tag == "table")])
    rec["html_rows_all"] = len([n for n in root_full.select(lambda n: n.tag == "tr")])
    rec["html_text_visible"] = count_all_text(full_html)

    # uygulamanin toplayacagi blok/gorsel/metin (tum sayfa uzerinden)
    collector, removed = app_blocks(full_html)
    rec["app_blocks"] = len(collector.out)
    rec["app_blocks_cap_hit"] = len(collector.out) >= MAX_BLOCKS
    rec["app_images"] = collector.images_seen
    rec["app_images_cap_dropped"] = collector.images_dropped_cap
    rec["app_images_small_dropped"] = collector.images_dropped_small
    rec["app_table_rows_dropped"] = collector.table_rows_dropped
    rec["app_text_visible"] = sum(len(b.get("text", "")) for b in collector.out)
    rec["app_text_coverage"] = round(
        rec["app_text_visible"] / rec["html_text_visible"], 3) if rec["html_text_visible"] else 1.0
    rec["app_links"] = len(set(count_internal_links(full_html)))

    # bolum bazinda kayip
    section_report = []
    for sec in (kept or [])[:12]:
        body = api(wiki, [
            ("action", "parse"), ("page", resolved), ("section", str(sec["index"])),
            ("prop", "text"), ("redirects", "1"), ("disableeditsection", "1"),
            ("disablelimitreport", "1"), ("format", "json"), ("formatversion", "2"),
        ]) or {}
        html = (body.get("parse") or {}).get("text") or ""
        if not html:
            continue
        coll, _ = app_blocks(html)
        section_report.append({
            "index": sec["index"], "title": sec["title"],
            "html_text": count_visible_text(html),
            "app_text": sum(len(b.get("text", "")) for b in coll.out),
            "html_images": len([n for n in parse_html(html).select(lambda n: n.tag == "img")]),
            "app_images": coll.images_seen,
            "cap_images_dropped": coll.images_dropped_cap,
            "table_rows_dropped": coll.table_rows_dropped,
            "html_rows": count_table_rows(html),
            "videos": count_videos(html),
            "cap_hit": len(coll.out) >= MAX_BLOCKS,
        })
    rec["sections_detail"] = section_report
    rec["sections_with_image_loss"] = len([s for s in section_report if s["cap_images_dropped"]])
    rec["sections_with_row_loss"] = len([s for s in section_report if s["table_rows_dropped"]])
    rec["sections_with_video"] = len([s for s in section_report if s["videos"]])

    # galeri (pageImages) karsilastirmasi
    gal = api(wiki, [
        ("action", "query"), ("titles", resolved), ("prop", "images"),
        ("imlimit", "500"), ("format", "json"), ("formatversion", "2"),
    ]) or {}
    gal_pages = ((gal.get("query") or {}).get("pages") or [{}])
    gallery_titles = [i.get("title") for i in (gal_pages[0].get("images") or [])]
    rec["gallery_files_total"] = len(gallery_titles)
    rec["gallery_files_used"] = min(len(gallery_titles), GALLERY_API_LIMIT)
    rec["ui_infobox_images_shown"] = min(rec["infobox_json"]["images"], UI_INFOBOX_IMAGES)
    rec["ui_infobox_images_hidden"] = max(
        0, min(rec["infobox_json"]["images"], UI_INFOBOX_IMAGES and 24) - UI_INFOBOX_IMAGES)
    return rec


# --------------------------------------------------------------------------- rapor
def pick_pages(wiki, per_wiki):
    main = api(wiki, [
        ("action", "query"), ("meta", "siteinfo"), ("siprop", "general"),
        ("format", "json"), ("formatversion", "2"),
    ])
    main_page = (((main or {}).get("query") or {}).get("general") or {}).get("mainpage")
    titles = []
    if main_page:
        titles.append(main_page)
    search = api(wiki, [
        ("action", "query"), ("list", "search"), ("srsearch", "character infobox"),
        ("srnamespace", "0"), ("srlimit", str(max(1, per_wiki))),
        ("format", "json"), ("formatversion", "2"),
    ]) or {}
    for hit in (search.get("query") or {}).get("search") or []:
        if hit.get("title") not in titles:
            titles.append(hit["title"])
    return titles[:per_wiki]


def summarize(records):
    def total(key):
        return sum(r.get(key, 0) for r in records if "error" not in r)

    ok = [r for r in records if "error" not in r]
    cov = [r["app_text_coverage"] for r in ok if r.get("app_text_coverage") is not None]
    return {
        "pages": len(records),
        "pages_ok": len(ok),
        "pages_error": len(records) - len(ok),
        "sections_total": total("sections_total"),
        "sections_kept": total("sections_kept"),
        "sections_template_dropped": total("sections_template"),
        "sections_hidden_dropped": total("sections_hidden"),
        "wt_images": total("wt_images"),
        "html_images_all": total("html_images_all"),
        "app_images": total("app_images"),
        "app_images_cap_dropped": total("app_images_cap_dropped"),
        "app_images_small_dropped": total("app_images_small_dropped"),
        "app_table_rows_dropped": total("app_table_rows_dropped"),
        "html_videos_all": total("html_videos_all"),
        "html_collapsible": total("html_collapsible"),
        "html_tabber": total("html_tabber"),
        "html_navbox": total("html_navbox"),
        "app_blocks_cap_hit": total("app_blocks_cap_hit"),
        "infobox_fields": total("infobox_json_fields") or sum(
            r.get("infobox_json", {}).get("fields", 0) for r in ok),
        "infobox_images": sum(r.get("infobox_json", {}).get("images", 0) for r in ok),
        "ui_infobox_images_shown": total("ui_infobox_images_shown"),
        "ui_infobox_images_hidden": total("ui_infobox_images_hidden"),
        "avg_text_coverage": round(sum(cov) / len(cov), 3) if cov else None,
        "min_text_coverage": min(cov) if cov else None,
        "sections_with_image_loss": total("sections_with_image_loss"),
        "sections_with_row_loss": total("sections_with_row_loss"),
        "sections_with_video": total("sections_with_video"),
    }


def write_report(out_dir, records, summary, elapsed):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "content_results.json"), "w", encoding="utf-8") as fh:
        json.dump({"summary": summary, "records": records}, fh, ensure_ascii=False, indent=1)

    lines = []
    lines.append(f"# Fandom wiki ICERIK denetimi - {time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime())}")
    lines.append("")
    lines.append(f"Sayfa: {summary['pages']} (ok {summary['pages_ok']}); sure {elapsed:.0f} sn; "
                 f"ag hatasi {len(_fails)} {_fails if _fails else ''}")
    lines.append("")
    lines.append("## Ozet")
    lines.append("")
    lines.append("| olcum | deger |")
    lines.append("|---|---|")
    rows = [
        ("MediaWiki bolum sayisi (TOC)", summary["sections_total"]),
        ("Uygulamanin TOC'ta gosterdigi", summary["sections_kept"]),
        ("ATILAN: sablon bolumu (T-x, index Int degil)", summary["sections_template_dropped"]),
        ("ATILAN: gizli bolum (Trivia/Galeri/...)", summary["sections_hidden_dropped"]),
        ("wikitext gorsel sayisi", summary["wt_images"]),
        ("HTML'deki <img> sayisi", summary["html_images_all"]),
        ("Uygulamanin blok olarak aldigi gorsel", summary["app_images"]),
        ("Gorsel kaybi: MAX_SECTION_IMAGES=24", summary["app_images_cap_dropped"]),
        ("Gorsel kaybi: 32x32 filtresi", summary["app_images_small_dropped"]),
        ("Tablo satiri kaybi: take(150)", summary["app_table_rows_dropped"]),
        ("Video yer tutucusu (hic gosterilmez)", summary["html_videos_all"]),
        ("Katlanabilir blok (.mw-collapsible, toggle silinir)", summary["html_collapsible"]),
        ("Sekmeli icerik (wds-tabber, wrapper silinir)", summary["html_tabber"]),
        ("Navbox (bilincli olarak silinir)", summary["html_navbox"]),
        ("maxBlocks=400'e takilan sayfa", summary["app_blocks_cap_hit"]),
        ("Infobox alani (JSON)", summary["infobox_fields"]),
        ("Infobox gorseli (JSON)", summary["infobox_images"]),
        ("Arayuzde gosterilen infobox gorseli", summary["ui_infobox_images_shown"]),
        ("Arayuzde gizli kalan infobox gorseli (take(8))", summary["ui_infobox_images_hidden"]),
        ("Ortalama metin kapsama orani", summary["avg_text_coverage"]),
        ("En dusuk metin kapsama orani", summary["min_text_coverage"]),
        ("Gorsel kaybi olan bolum sayisi", summary["sections_with_image_loss"]),
        ("Satir kaybi olan bolum sayisi", summary["sections_with_row_loss"]),
        ("Video iceren bolum sayisi", summary["sections_with_video"]),
    ]
    for label, value in rows:
        lines.append(f"| {label} | {value} |")
    lines.append("")
    lines.append("## Sayfa ayrintisi")
    lines.append("")
    lines.append("| wiki | sayfa | TOC | TOC app | T-x | gizli | <img> | app img | cap | tablo satiri kaybi | video | kapsama |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for rec in records:
        if "error" in rec:
            lines.append(f"| {rec['wiki']} | {rec['title']} | - | - | - | - | - | - | - | - | - | {rec['error']} |")
            continue
        lines.append(
            f"| {rec['wiki']} | {rec['resolved'][:34]} | {rec['sections_total']} | "
            f"{rec['sections_kept']} | {rec['sections_template']} | {rec['sections_hidden']} | "
            f"{rec['html_images_all']} | {rec['app_images']} | {rec['app_images_cap_dropped']} | "
            f"{rec['app_table_rows_dropped']} | {rec['html_videos_all']} | {rec['app_text_coverage']} |")
    lines.append("")
    lines.append("## Atılan sablon bolumleri (ilk 40)")
    lines.append("")
    count = 0
    for rec in records:
        for drop in rec.get("sections_dropped", []):
            if drop["reason"] != "SKIP_TEMPLATE_SECTION":
                continue
            lines.append(f"- {rec['wiki']} / {rec.get('resolved', rec['title'])}: "
                         f"`index={drop['index']}` **{drop['line']}**")
            count += 1
            if count >= 40:
                break
        if count >= 40:
            break
    with open(os.path.join(out_dir, "content_report.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.join(HERE, "results"))
    parser.add_argument("--wikis", default=",".join(DEFAULT_WIKIS))
    parser.add_argument("--pages-per-wiki", type=int, default=3)
    parser.add_argument("--workers", type=int, default=2)
    args = parser.parse_args()

    started = time.time()
    wikis = [w.strip() for w in args.wikis.split(",") if w.strip()]
    jobs = []
    for wiki in wikis:
        for title in pick_pages(wiki, args.pages_per_wiki):
            jobs.append((wiki, title))
    print(f"{len(jobs)} sayfa denetlenecek", flush=True)

    records = []
    with cf.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(audit_page, wiki, title): (wiki, title) for wiki, title in jobs}
        for future in cf.as_completed(futures):
            wiki, title = futures[future]
            try:
                rec = future.result()
            except Exception as exc:  # noqa: BLE001
                rec = {"wiki": wiki, "title": title, "error": f"{type(exc).__name__}: {exc}"}
            records.append(rec)
            print(f"  {rec['wiki']} / {rec.get('resolved', rec['title'])}: "
                  f"TOC {rec.get('sections_total')}/{rec.get('sections_kept')} "
                  f"img {rec.get('html_images_all')}/{rec.get('app_images')} "
                  f"cov {rec.get('app_text_coverage')}", flush=True)

    records.sort(key=lambda r: (r["wiki"], str(r.get("resolved", r.get("title")))))
    summary = summarize(records)
    write_report(args.out, records, summary, time.time() - started)
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())

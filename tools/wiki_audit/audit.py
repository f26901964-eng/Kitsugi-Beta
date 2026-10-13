#!/usr/bin/env python3
"""
Kitsugi - Fandom wiki kesif denetimi (canli).

Uygulamadaki FandomWikiResolver akisini (override -> Wikidata -> karakter oylamasi ->
iliski yuruyusu -> isim yolu) Python'a birebir tasir ve canli Fandom / Wikidata / AniList
yanitlariyla calistirir. Amac: "wiki var ama uygulama bulamiyor" vakalarini olcmek ve
HANGI asamada dustugunu gostermek. Kotlin kodunun sabitleri (esikler, limitler) aynen
kullanilir; bkz. app/src/main/java/com/kitsugi/animelist/data/fandom/.

Kullanim:  python3 audit.py [--out DIR] [--only substr] [--workers N]
Yalnizca standart kutuphane kullanir.
"""
import argparse
import concurrent.futures as cf
import json
import math
import os
import re
import sys
import threading
import time
import unicodedata
import urllib.parse
import urllib.request

UA = "Kitsugi/2.4.281 (Android; +https://github.com/f26901964-eng/Kitsugi-Beta) wiki-audit"
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))

# --------------------------------------------------------------------------- HTTP

_sem = threading.Semaphore(4)
_fail = {"n": 0}
_al_lock = threading.Lock()
_al_last = [0.0]
_cache = {}
_cache_lock = threading.Lock()


_host_last = {}
_host_lock = threading.Lock()
_HOST_GAP = {"www.wikidata.org": 0.6, "services.fandom.com": 0.15}
_fail_by = {}


def _throttle(host):
    gap = _HOST_GAP.get(host)
    if not gap:
        return
    with _host_lock:
        wait = gap - (time.time() - _host_last.get(host, 0))
        _host_last[host] = time.time() + max(wait, 0)
    if wait > 0:
        time.sleep(wait)


def _note_fail(url, what):
    host = urllib.parse.urlparse(url).netloc
    k = f"{host} {what}"
    _fail_by[k] = _fail_by.get(k, 0) + 1
    _fail["n"] += 1


def http(url, accept="application/json", data=None, headers=None, retries=4, timeout=25):
    key = (url, data)
    with _cache_lock:
        if key in _cache:
            return _cache[key]
    host = urllib.parse.urlparse(url).netloc
    for attempt in range(retries + 1):
        _throttle(host)
        with _sem:
            try:
                req = urllib.request.Request(url, data=data, headers={"User-Agent": UA, "Accept": accept, **(headers or {})})
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    body = r.read().decode("utf-8", "replace")
                    with _cache_lock:
                        _cache[key] = body
                    return body
            except urllib.error.HTTPError as e:
                if e.code in (429, 500, 502, 503, 504) and attempt < retries:
                    try:
                        ra = int(e.headers.get("Retry-After", "0"))
                    except Exception:  # noqa
                        ra = 0
                    time.sleep(min(60, max(ra, 2 ** (attempt + 1))))
                    continue
                if e.code != 404:
                    _note_fail(url, str(e.code))
                break
            except Exception as e:  # noqa
                if attempt < retries:
                    time.sleep(2 ** attempt)
                    continue
                _note_fail(url, type(e).__name__)
    return None


def jget(url, **kw):
    body = http(url, **kw)
    if not body:
        return None
    try:
        return json.loads(body)
    except Exception:
        return None


def anilist(query, variables):
    with _al_lock:
        wait = 0.9 - (time.time() - _al_last[0])
        if wait > 0:
            time.sleep(wait)
        _al_last[0] = time.time()
    payload = json.dumps({"query": query, "variables": variables}).encode()
    for attempt in range(3):
        try:
            req = urllib.request.Request(
                "https://graphql.anilist.co", data=payload,
                headers={"User-Agent": UA, "Content-Type": "application/json", "Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(int(e.headers.get("Retry-After", "20")) + 1)
                continue
            return None
        except Exception:
            time.sleep(2)
    return None


# --------------------------------------------------------------------------- NameVariants (port)

STOP_TOKENS = {"the", "wiki", "wikia", "fandom", "encyclopedia", "database", "db", "official", "unofficial", "community"}
POSSESSIVE = re.compile(r"['’]s\b", re.I)
APOS = re.compile(r"['’`´]")


def fold(s):
    s = (s.replace("ı", "i").replace("İ", "I").replace("ß", "ss").replace("ø", "o").replace("Ø", "O")
         .replace("ł", "l").replace("Ł", "L").replace("đ", "d").replace("Đ", "D").replace("æ", "ae").replace("Æ", "AE"))
    d = unicodedata.normalize("NFD", s)
    d = re.sub(r"[\u0300-\u036F]+", "", d)
    return unicodedata.normalize("NFC", d)


def norm(s):
    s = POSSESSIVE.sub("", s)
    s = fold(s).replace("×", " x ").replace("✕", " x ").replace("&", " and ")
    s = APOS.sub("", s)
    s = re.sub(r"[^\w]+|_", " ", s.lower())
    return re.sub(r"\s+", " ", s).strip()


def tokens(s, drop_stop=True):
    return [t for t in norm(s).split(" ") if t and (not drop_stop or t not in STOP_TOKENS)]


def mostly_latin(s):
    letters = [c for c in s if c.isalpha()]
    if not letters:
        return False
    latin = sum(1 for c in letters if ord(c) < 0x250)
    return latin * 2 >= len(letters)


SEASON = [
    re.compile(r"\b(season|saison|temporada|staffel)\s*\d+\b", re.I),
    re.compile(r"\b\d+(st|nd|rd|th)\s+(season|cour|part)\b", re.I),
    re.compile(r"\b(part|cour)\s*(\d+|i{1,3}|iv|v)\b", re.I),
    re.compile(r"\b(the\s+)?(final|last)\s+(season|chapters?)\b", re.I),
    re.compile(r"\b(the\s+)?movie\b", re.I),
    re.compile(r"\b(ova|ona|oad|specials?|tv)\b", re.I),
    re.compile(r"\(\s*\d{4}\s*\)"),
    re.compile(r"\s+(ii|iii|iv|v|vi|vii|viii|ix|x)$", re.I),
]


def strip_season(t):
    for p in SEASON:
        t = p.sub(" ", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t.strip(":-–—,. ()")


def base_title_variants(titles, mx=4):
    out = {}

    def add(c):
        if not c:
            return
        t = re.sub(r"\s+", " ", c).strip().strip(":- ")
        if len(t) < 2 or not any(ch.isalnum() for ch in t):
            return
        k = norm(t)
        if k and k not in out:
            out[k] = t

    for raw in titles:
        if not mostly_latin(raw):
            continue
        s = strip_season(raw)
        add(s)
        i = s.find(": ")
        if i > 0:
            add(s[:i])
            after = s[i + 2:]
            if len(after.strip().split(" ")) >= 2:
                add(after)
        add(raw)
    return list(out.values())[:mx]


def media_title_variants(titles, mx=12):
    out = {}

    def add(c):
        if not c:
            return
        t = re.sub(r"\s+", " ", c).strip().strip(":- ")
        if len(t) < 2 or not any(ch.isalnum() for ch in t):
            return
        k = norm(t)
        if k and k not in out:
            out[k] = t

    for raw in titles:
        if not raw.strip():
            continue
        if mostly_latin(raw):
            for v in base_title_variants([raw], 4):
                add(v)
        else:
            add(raw)
            i = raw.find(":")
            if i > 0:
                add(raw[:i])
    return list(out.values())[:mx]


SLUG_OK = re.compile(r"^[a-z0-9][a-z0-9-]*[a-z0-9]$")


def slug_candidates(titles, mx=6):
    out = []
    for t in base_title_variants(titles, 4):
        parts = [p for p in norm(t).split(" ") if p]
        if not parts:
            continue
        for slug in ("-".join(parts), "".join(parts)):
            if 3 <= len(slug) <= 60 and SLUG_OK.match(slug) and slug not in out:
                out.append(slug)
    return out[:mx]


def collapse_hyphens(name):
    res = []
    for part in name.split(" "):
        pieces = [p for p in part.split("-") if p]
        res.append(part if len(pieces) <= 1 else pieces[0] + "".join(p.lower() for p in pieces[1:]))
    return " ".join(res)


def simplify_long_vowels(name):
    return name.replace("ou", "o").replace("Ou", "O").replace("uu", "u").replace("Uu", "U")


def character_title_variants(full, alts, mx=4):
    base = re.sub(r"\s+", " ", full or "").strip()
    out = []

    def add(c):
        t = re.sub(r"\s+", " ", c or "").strip()
        if 2 <= len(t) <= 80 and t not in out:
            out.append(t)

    def add_rev(n):
        add(n)
        parts = n.split(" ")
        if len(parts) == 2:
            add(parts[1] + " " + parts[0])

    if base:
        add_rev(base)
        if "-" in base:
            add_rev(collapse_hyphens(base))
        f = fold(base)
        if f != base:
            add_rev(f)
        s = simplify_long_vowels(base)
        if s != base:
            add_rev(s)
    for a in alts[:2]:
        add(a)
    return out[:mx]


KANA_RUBY = re.compile(r"[（(]\s*[\u3040-\u30FF\s]+\s*[）)]")
NATIVE_SEP = re.compile(r"[\s\u3000・･·‧•=＝\"'「」『』]+")


def squash_native(s):
    return NATIVE_SEP.sub("", unicodedata.normalize("NFKC", s))


def has_cjk(t):
    for ch in t:
        c = ord(ch)
        if 0x3040 <= c <= 0x30FF or 0x3400 <= c <= 0x9FFF or 0xF900 <= c <= 0xFAFF or 0xAC00 <= c <= 0xD7AF or 0x1100 <= c <= 0x11FF or 0x3130 <= c <= 0x318F:
            return True
    return False


def native_appears_in(native, hay):
    if not native or not hay:
        return False
    needle = squash_native(KANA_RUBY.sub("", native))
    if len(needle) < 2 or not has_cjk(needle):
        return False
    if needle in squash_native(hay):
        return True
    return needle in squash_native(KANA_RUBY.sub("", hay))


# --------------------------------------------------------------------------- FandomUrls / selector (port)

WIKI_ID = re.compile(r"^(?:([a-z]{2,3}(?:-[a-z0-9]{2,8})?)\.)?([a-z0-9][a-z0-9-]{0,99})$")
LANG_SEG = re.compile(r"^[a-z]{2,3}(?:-[a-z0-9]{2,8})?$")
RESERVED = {"www", "community", "services", "auth", "static", "vignette", "images", "api", "fandom", "about"}


def parse_wiki(w):
    if not w:
        return None
    m = WIKI_ID.match(w.strip().lower())
    if not m:
        return None
    lang = m.group(1) if m.group(1) and m.group(1) != "en" else None
    name = m.group(2)
    if name in RESERVED:
        return None
    return (lang, name)


def normalize_wiki(w):
    p = parse_wiki(w)
    if not p:
        return None
    return p[1] if p[0] is None else f"{p[0]}.{p[1]}"


def is_root(w):
    p = parse_wiki(w)
    return bool(p) and p[0] is None


def lang_of(w):
    p = parse_wiki(w)
    return p[0] if p else None


def base_url(w):
    p = parse_wiki(w)
    if not p:
        return None
    return f"https://{p[1]}.fandom.com" + (f"/{p[0]}" if p[0] else "")


def wiki_from_url(url):
    try:
        u = urllib.parse.urlparse((url or "").strip())
    except Exception:
        return None
    host = (u.hostname or "").lower()
    if host.endswith(".fandom.com"):
        dom = ".fandom.com"
    elif host.endswith(".wikia.com"):
        dom = ".wikia.com"
    else:
        return None
    sub = host[: -len(dom)]
    if not sub or "." in sub:
        return None
    segs = [s for s in u.path.split("/") if s]
    first = segs[0].lower() if segs else ""
    lang = first if first and LANG_SEG.match(first) else None
    return normalize_wiki(f"{lang}.{sub}" if lang else sub)


def parse_article_id(a):
    if not a:
        return None
    i = a.find(":")
    if i <= 0 or i == len(a) - 1:
        return None
    w = normalize_wiki(a[:i])
    t = a[i + 1:].replace("_", " ").strip()
    if not w or not t:
        return None
    return (w, t)


BLOCKED_NAMES = {
    "hero", "heroes", "villains", "villain", "isekai", "cosplay", "dubbing", "dubdb", "netflix", "tropedia", "allthetropes",
    "animanga", "anime", "manga", "vsbattles", "vsdebating", "powerlisting", "superpower", "listofdeaths", "deathbattle",
    "deathbattlefanon", "characterprofile", "aesthetics", "dengeki", "video-games", "videogames", "nintendo", "crossover",
    "fanon", "fanfiction", "lgbtqia-characters", "disney", "dreamworks", "cartoons", "jokebattles", "joke-battles",
    "fictionalcrossover", "the-anime-database", "anime-characters-database", "myanimelist", "anilist",
}
BLOCKED_PATTERNS = [re.compile(p) for p in [
    r"fanon", r"fanfic", r"dub(b)?(ing|er|ers|db)", r"doblaje", r"dublagem", r"doppiaggio", r"roleplay", r"(^|-)rp($|-)",
    r"crossover", r"deathbattle", r"powerlisting", r"vs-?battles"]]


def is_blocked(w):
    p = parse_wiki(w)
    if not p:
        return True
    n = p[1]
    return n in BLOCKED_NAMES or any(r.search(n) for r in BLOCKED_PATTERNS)


def usable(cands):
    out = []
    for c in cands:
        n = normalize_wiki(c)
        if n and n not in out and not is_blocked(n):
            out.append(n)
    return out


def preferred(all_):
    roots = [w for w in all_ if is_root(w)]
    if roots:
        return roots
    tr = [w for w in all_ if lang_of(w) == "tr"]
    return tr or all_


def choose_wiki(cands, counts=None):
    counts = counts or {}
    al = usable(cands)
    if not al:
        return None
    pref = preferred(al)
    if not pref:
        return None
    return max(pref, key=lambda w: counts.get(w, -1))


# --------------------------------------------------------------------------- Fandom API (port)

_siteinfo = {}


def fandom_api(wiki, params):
    b = base_url(wiki)
    if not b:
        return None
    return jget(b + "/api.php?" + urllib.parse.urlencode(params))


def siteinfo(wiki):
    key = normalize_wiki(wiki)
    if not key:
        return None
    if key in _siteinfo:
        return _siteinfo[key]
    j = fandom_api(key, {"action": "query", "meta": "siteinfo", "siprop": "general|statistics", "format": "json", "formatversion": "2"})
    info = None
    if j and "error" not in j and j.get("query", {}).get("general"):
        g = j["query"]["general"]
        st = j["query"].get("statistics") or {}
        real = wiki_from_url((g.get("server") or "") + (g.get("scriptpath") or "") + "/") or key
        info = {"wikiId": real, "siteName": g.get("sitename"), "lang": g.get("lang"),
                "articles": st.get("articles"), "mainPage": g.get("mainpage")}
    _siteinfo[key] = info
    if info:
        _siteinfo[info["wikiId"]] = info
    return info


def community_search(q, lang="en", limit=8):
    u = "https://services.fandom.com/unified-search/community-search?" + urllib.parse.urlencode({"query": q, "lang": lang, "limit": limit})
    j = jget(u)
    hits = {}
    for it in (j or {}).get("results", []) or []:
        w = wiki_from_url(it.get("url"))
        if not w or w in hits:
            continue
        hits[w] = {"wikiId": w, "name": it.get("name") or w, "language": it.get("language"), "hub": it.get("hub"),
                   "pageCount": it.get("pageCount"), "description": it.get("description")}
    return list(hits.values())


def lookup_titles(wiki, titles):
    clean = []
    for t in titles:
        t = t.strip()
        if t and len(t) <= 200 and "|" not in t and t not in clean:
            clean.append(t)
    res = {}
    for i in range(0, len(clean), 50):
        chunk = clean[i:i + 50]
        j = fandom_api(wiki, {"action": "query", "titles": "|".join(chunk), "redirects": "1", "prop": "pageprops",
                              "ppprop": "infoboxes|fandomdescription|disambiguation", "format": "json", "formatversion": "2"})
        q = (j or {}).get("query") or {}
        nmap = {x["from"]: x["to"] for x in q.get("normalized", []) or []}
        rmap = {x["from"]: x["to"] for x in q.get("redirects", []) or []}
        pages = {p.get("title"): p for p in q.get("pages", []) or []}
        for t in chunk:
            nt = nmap.get(t, t)
            ft = rmap.get(nt, nt)
            p = pages.get(ft)
            exists = bool(p) and "missing" not in p and "invalid" not in p and p.get("ns", 0) == 0
            props = (p or {}).get("pageprops") or {} if exists else {}
            res[t] = {"requested": t, "resolved": ft if exists else None, "infobox": props.get("infoboxes"),
                      "desc": props.get("fandomdescription"), "disambig": "disambiguation" in props}
    return res


def infobox_text(raw):
    if not raw:
        return ""
    try:
        data = json.loads(raw)
    except Exception:
        return ""
    out = []

    def walk(o):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in ("value", "label") and isinstance(v, str):
                    out.append(re.sub(r"<[^>]+>", " ", v))
                else:
                    walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)

    walk(data)
    return " ".join(out)


def evidence_text(lk):
    return infobox_text(lk.get("infobox")) + " " + (lk.get("desc") or "")


def has_ext_link(wiki, url_no_proto):
    j = fandom_api(wiki, {"action": "query", "list": "exturlusage", "euquery": url_no_proto, "eunamespace": "0", "eulimit": "1",
                          "format": "json", "formatversion": "2"})
    u = ((j or {}).get("query") or {}).get("exturlusage")
    return bool(u)


# --------------------------------------------------------------------------- Wikidata (port, Action API yolu)

WD = "https://www.wikidata.org/w/api.php"
PARENTS = ["P179", "P8345", "P144", "P361", "P155", "P156"]
QID = re.compile(r"^Q[1-9]\d{0,12}$")


def wd_find_qids(prop, val, limit=2):
    j = jget(WD + "?" + urllib.parse.urlencode({"action": "query", "list": "search", "srsearch": f"haswbstatement:{prop}={val}",
                                                 "srnamespace": "0", "srlimit": limit, "format": "json", "formatversion": "2"}))
    if j is None:
        return None
    return [x["title"] for x in (j.get("query") or {}).get("search", []) if QID.match(x.get("title", ""))]


def claim_strings(claims, prop):
    out = []
    for c in claims.get(prop, []) or []:
        v = ((c.get("mainsnak") or {}).get("datavalue") or {}).get("value")
        if isinstance(v, str):
            out.append(v)
    return out


def claim_items(claims, prop):
    out = []
    for c in claims.get(prop, []) or []:
        v = ((c.get("mainsnak") or {}).get("datavalue") or {}).get("value")
        if isinstance(v, dict) and v.get("id"):
            out.append(v["id"])
    return out


def wd_entities(qids):
    ids = [q for q in dict.fromkeys(qids) if QID.match(q)]
    out = {}
    for i in range(0, len(ids), 50):
        j = jget(WD + "?" + urllib.parse.urlencode({"action": "wbgetentities", "ids": "|".join(ids[i:i + 50]), "props": "claims|labels",
                                                    "languages": "en", "format": "json", "formatversion": "2"}))
        if j is None:
            return None
        for q, e in (j.get("entities") or {}).items():
            cl = e.get("claims") or {}
            parents = []
            for p in PARENTS:
                parents += claim_items(cl, p)
            out[q] = {"wikis": claim_strings(cl, "P4073"), "articles": claim_strings(cl, "P6262"), "parents": list(dict.fromkeys(parents)),
                      "claims": cl, "label": ((e.get("labels") or {}).get("en") or {}).get("value")}
    return out


def wd_lookups(ids, is_manga, is_movie):
    out = []
    if is_manga:
        if ids.get("anilist"): out.append(("P8731", str(ids["anilist"])))
        if ids.get("mal"): out.append(("P4087", str(ids["mal"])))
        if ids.get("kitsu"): out.append(("P11494", str(ids["kitsu"])))
        if ids.get("bangumi"): out.append(("P5732", str(ids["bangumi"])))
    else:
        if ids.get("anilist"): out.append(("P8729", str(ids["anilist"])))
        if ids.get("mal"): out.append(("P4086", str(ids["mal"])))
        if ids.get("kitsu"): out.append(("P11495", str(ids["kitsu"])))
        if ids.get("bangumi"): out.append(("P5732", str(ids["bangumi"])))
        if ids.get("tmdb"): out.append(("P4947" if is_movie else "P4983", str(ids["tmdb"])))
    if ids.get("imdb"):
        out.append(("P345", ids["imdb"].lower()))
    return out


def wd_resolve(lookups, starts_in, trace):
    starts = [q for q in starts_in if QID.match(q)]
    failed = False
    if not starts:
        for p, v in lookups[:6]:
            f = wd_find_qids(p, v, 2)
            if f is None:
                failed = True
                continue
            for q in f:
                if len(starts) < 3 and q not in starts:
                    starts.append(q)
            if len(starts) >= 3:
                break
        if not starts and failed:
            trace.append("wikidata: ag hatasi")
            return None
    if not starts:
        trace.append("wikidata: kimlikle QID yok")
        return {"starts": [], "wikis": {}, "articles": [], "nodes": []}
    wikis, articles, visited, nodes = {}, [], list(starts), []
    lvl0 = wd_entities(starts)
    if lvl0 is None:
        return None
    for e in lvl0.values():
        if e["wikis"]:
            wikis.setdefault(0, []).extend(e["wikis"])
        articles += e["articles"]
    frontier = [p for e in lvl0.values() for p in e["parents"]]
    for depth in (1, 2):
        if wikis:
            break
        nxt = []
        for q in frontier:
            if q not in visited:
                visited.append(q)
                nxt.append(q)
        nxt = nxt[:8]
        if not nxt:
            break
        nodes += nxt
        lvl = wd_entities(nxt)
        if lvl is None:
            break
        for e in lvl.values():
            if e["wikis"]:
                wikis.setdefault(depth, []).extend(e["wikis"])
        frontier = [p for e in lvl.values() for p in e["parents"]]
    trace.append(f"wikidata: starts={starts} p4073={ {k: v for k, v in wikis.items()} } nodes={nodes[:4]}")
    return {"starts": starts, "wikis": wikis, "articles": articles, "nodes": nodes}


def pick_alive(cands):
    us = usable(cands)
    if not us:
        return None
    loc = sorted([w for w in us if not is_root(w)], key=lambda w: 0 if lang_of(w) == "tr" else 1)[:8]

    def load(pool):
        res = []
        for c in pool:
            i = siteinfo(c)
            if i and not is_blocked(i["wikiId"]) and i["wikiId"] not in [r["wikiId"] for r in res]:
                res.append(i)
        return res

    roots = [w for w in us if is_root(w)][:8]
    infos = load(roots)
    if not any(lang_rank(i) <= 1 for i in infos):
        infos = infos + [i for i in load(loc) if i["wikiId"] not in [x["wikiId"] for x in infos]]
    if not infos:
        return None
    best_rank = min(lang_rank(i) for i in infos)
    grp = [i for i in infos if lang_rank(i) == best_rank]
    best = dict(max(grp, key=lambda i: i.get("articles") or 0))
    best["_rank"] = best_rank
    return best


def lang_rank(info):
    lg = (info.get("lang") or "").lower().split("-")[0]
    return 0 if lg == "en" else (1 if lg == "tr" else 2)


# --------------------------------------------------------------------------- Overrides

def load_overrides():
    p = os.path.join(REPO, "app/src/main/assets/fandom/fandom_overrides.json")
    t = {"al": {}, "mal_a": {}, "mal_m": {}, "qid": {}}
    try:
        for e in json.load(open(p, encoding="utf-8")).get("entries", []):
            w = normalize_wiki(e.get("wiki"))
            if not w or is_blocked(w):
                continue
            for i in e.get("anilist", []): t["al"][i] = w
            for i in e.get("mal", []): t["mal_a"][i] = w
            for i in e.get("mal_manga", []): t["mal_m"][i] = w
            for q in e.get("qid", []): t["qid"][q] = w
    except Exception:
        pass
    return t


OVR = load_overrides()


def ovr_lookup(ids, is_manga):
    if ids.get("anilist") in OVR["al"]: return OVR["al"][ids["anilist"]]
    m = OVR["mal_m"] if is_manga else OVR["mal_a"]
    if ids.get("mal") in m: return m[ids["mal"]]
    if ids.get("wikidata") in OVR["qid"]: return OVR["qid"][ids["wikidata"]]
    return None


# --------------------------------------------------------------------------- Name matcher / proof (port)

# Topluluk aramasinin pageCount alani KABA bir kovadir (85 makaleli wiki icin 10 donuyor); gercek makale sayisi degildir.
MIN_PAGES = 10
FAN = re.compile(r"\b(fanon|fan ?fic(tion)?|role ?play|rp|simulator|tycoon|roblox)\b", re.I)


def pre_veto(hit):
    if is_blocked(hit["wikiId"]): return "blocked"
    pages = hit.get("pageCount")
    if pages is not None and pages < MIN_PAGES: return "tiny"
    if FAN.search(hit["name"]): return "fan-content"
    return None


def title_sim(wiki_name, titles, creators=()):
    wt = set(tokens(wiki_name))
    if not wt: return 0.0
    ct = set(t for c in creators for t in tokens(c))
    best = 0.0
    for title in titles:
        tt = set(tokens(title))
        if not tt: continue
        shared = sum(1 for t in tt if t in wt)
        if shared == 0: continue
        extra = sum(1 for t in wt if t not in tt and t not in ct)
        s = shared / (len(tt) + extra)
        best = max(best, s)
    return max(0.0, min(1.0, best))


def hub_score(hub, is_manga):
    h = (hub or "").strip().lower()
    if h == "anime": return 1.0
    if h in ("books", "comics"): return 1.0 if is_manga else 0.7
    if h in ("tv", "movies", "film"): return 0.6
    if h == "": return 0.5
    if h in ("games", "gaming"): return 0.1
    if h in ("lifestyle", "music"): return 0.0
    return 0.3


def size_score(pc):
    if not pc or pc <= 0: return 0.3
    return max(0.0, min(1.0, math.log10(pc) / 4.0))


def quick_score(hit, ev):
    t = title_sim(hit["name"], ev["titles"], ev["creators"])
    h = hub_score(hit.get("hub"), ev["is_manga"])
    s = size_score(hit.get("pageCount"))
    desc = " " + norm(hit.get("description") or "") + " "
    mentions = 0
    for n in ev["characters"][:6] + ev["creators"]:
        nn = norm(n)
        if len(nn) >= 4 and (" " + nn + " ") in desc:
            mentions += 1
    return 0.5 * t + 0.25 * h + 0.15 * s + 0.10 * (min(mentions, 2) / 2.0)


def creator_trace(hit, creators, fp):
    if fp and fp.get("creatorPage"): return True
    hay = " " + norm(hit["name"] + " " + (hit.get("description") or "")) + " "
    for c in creators:
        n = norm(c)
        if len(n) >= 4 and (" " + n + " ") in hay: return True
    return False


def score(hit, ev, fp):
    v = pre_veto(hit)
    if v: return {"wikiId": hit["wikiId"], "score": 0.0, "decision": "REJECT", "veto": v}
    if fp:
        if fp["mainTotal"] >= 2 and fp["mainFound"] == 0:
            return {"wikiId": hit["wikiId"], "score": 0.0, "decision": "REJECT", "veto": "no-main-characters"}
        if fp["mainTotal"] < 2 and fp["anyTotal"] >= 3 and fp["anyFound"] == 0:
            return {"wikiId": hit["wikiId"], "score": 0.0, "decision": "REJECT", "veto": "no-characters"}
    if not fp: share = 0.0
    elif fp["mainTotal"] > 0: share = fp["mainFound"] / fp["mainTotal"]
    elif fp["anyTotal"] > 0: share = fp["anyFound"] / fp["anyTotal"]
    else: share = 0.0
    nc = fp["nativeConfirmed"] if fp else 0
    native = min(nc, 2) / 2.0
    title = title_sim(hit["name"], ev["titles"], ev["creators"])
    hub = hub_score(hit.get("hub"), ev["is_manga"])
    creator = 1.0 if creator_trace(hit, ev["creators"], fp) else 0.0
    size = size_score(hit.get("pageCount"))
    total = max(0.0, min(1.0, 0.40 * share + 0.20 * native + 0.15 * title + 0.10 * hub + 0.10 * creator + 0.05 * size))
    has_ev = fp is not None and fp["anyTotal"] > 0
    strong = fp is not None and fp["anyTotal"] > 0 and fp["anyFound"] >= 4 and share >= 0.75 and title >= 0.9 and hub >= 0.5
    if has_ev and total >= 0.85 and nc >= 1: d = "CERTAIN"
    elif strong: d = "CERTAIN"
    elif total >= 0.65: d = "PROBABLE"
    elif fp is not None and hub >= 0.5 and (
            (fp["mainTotal"] >= 2 and fp["mainFound"] == fp["mainTotal"] and fp["anyFound"] >= 4 and fp["anyFound"] / fp["anyTotal"] >= 0.6)
            or (fp["anyFound"] >= 3 and fp["anyTotal"] > 0 and fp["anyFound"] / fp["anyTotal"] >= 0.5 and title >= 0.99)):
        d = "PROBABLE"
    else: d = "REJECT"
    return {"wikiId": hit["wikiId"], "score": total, "decision": d, "share": share, "title": title}


def pick_best(scores):
    viable = sorted([s for s in scores if s["decision"] != "REJECT"], key=lambda s: -s["score"])
    if not viable: return None
    best = dict(viable[0])
    if len(viable) > 1 and best["decision"] == "CERTAIN" and best["score"] < 1.0 and best["score"] - viable[1]["score"] < 0.05:
        best["decision"] = "PROBABLE"
    return best


def article_choose(source_titles, lookups):
    titles, seen = [], set()
    for t in source_titles:
        t = t.strip()
        if t and norm(t) not in seen:
            seen.add(norm(t)); titles.append(t)
    if not titles: return None
    viable, seen_res = [], set()
    for lk in lookups:
        if not lk["resolved"] or lk["disambig"]: continue
        rk = norm(lk["resolved"])
        if rk in seen_res: continue
        seen_res.add(rk)
        req = norm(lk["requested"])
        exact = any(norm(t) == req for t in titles)
        ts = 1.0 if exact else title_sim(lk["resolved"], titles)
        if ts < 0.55: continue
        viable.append({"lookup": lk, "confidence": "CERTAIN" if (exact or ts >= 0.88) else "PROBABLE", "score": ts})
    viable.sort(key=lambda m: -m["score"])
    if not viable: return None
    if len(viable) == 1: return viable[0]
    if viable[0]["score"] - viable[1]["score"] < 0.06: return None
    return viable[0]


YEAR = re.compile(r"(?<!\d)(18\d{2}|19\d{2}|20\d{2}|21\d{2}|2200)(?!\d)")


def strong_article_proof(hit, info, source_titles, creators, is_manga, year, match):
    if is_blocked(hit["wikiId"]) or is_blocked(info["wikiId"]): return False
    if match["confidence"] != "CERTAIN": return False
    if (info.get("articles") or hit.get("pageCount") or 0) < 20: return False
    if hub_score(hit.get("hub"), is_manga) < 0.5: return False
    root_titles = base_title_variants(source_titles)
    ident = []
    for t in root_titles + media_title_variants(source_titles) + source_titles:
        if norm(t) not in [norm(x) for x in ident]: ident.append(t)
    wiki_name = info.get("siteName") or hit["name"]
    if title_sim(wiki_name, ident, creators) < 0.65: return False
    best_root = max(root_titles, key=lambda t: title_sim(wiki_name, [t], creators)) if root_titles else (source_titles[0] if source_titles else "")
    distinct = len(set(tokens(best_root)))
    y = year if year and 1800 <= year <= 2200 else None
    if y is None:
        for t in source_titles:
            m = YEAR.search(t)
            if m: y = int(m.group(1)); break
    if y is None: return distinct >= 2
    ev = (match["lookup"]["resolved"] or "") + " " + evidence_text(match["lookup"]) + " " + (hit.get("description") or "")
    has_year = re.search(rf"(?<!\d){y}(?!\d)", ev) is not None
    return distinct >= 2 or has_year


def external_link_lookups(ids, is_manga, is_movie, is_tv):
    am = "manga" if is_manga else "anime"
    sk = "manga" if is_manga else "movies" if is_movie else "tv" if is_tv else "anime"
    out = []
    if ids.get("wikidata"): out.append(f"wikidata.org/wiki/{ids['wikidata']}")
    if ids.get("imdb"): out.append(f"imdb.com/title/{ids['imdb'].lower()}")
    if ids.get("anilist"): out.append(f"anilist.co/{am}/{ids['anilist']}")
    if ids.get("mal"): out.append(f"myanimelist.net/{am}/{ids['mal']}")
    if ids.get("tmdb"): out.append(f"themoviedb.org/{'movie' if is_movie else 'tv'}/{ids['tmdb']}")
    if ids.get("kitsu"): out += [f"kitsu.app/{am}/{ids['kitsu']}", f"kitsu.io/{am}/{ids['kitsu']}"]
    if ids.get("simkl"): out.append(f"simkl.com/{sk}/{ids['simkl']}")
    return list(dict.fromkeys(out))


# --------------------------------------------------------------------------- Resolver (port)

AL_QUERY = """
query ($id: Int, $search: String, $type: MediaType, $formatIn: [MediaFormat]) {
  Media(id: $id, search: $search, type: $type, format_in: $formatIn, sort: SEARCH_MATCH) {
    id idMal type format isAdult startDate { year }
    title { romaji english native }
    synonyms
    relations { edges { relationType node { id idMal type } } }
    characters(sort: [ROLE, RELEVANCE, ID], perPage: 12) { edges { role node { id name { full native alternative } } } }
    staff(sort: [RELEVANCE, ID], perPage: 10) { edges { role node { name { full } } } }
  }
}"""
CREATOR_ROLE = re.compile(r"^\s*(original (creator|story|work)|story( ?& ?art)?|art|author)\b", re.I)
RELATION_PRIORITY = ["PARENT", "PREQUEL", "SOURCE", "SEQUEL", "ADAPTATION", "SIDE_STORY", "ALTERNATIVE", "SUMMARY", "COMPILATION", "SPIN_OFF"]


def parse_evidence(media, fallback_manga):
    t = media.get("title") or {}
    titles = [x for x in [t.get("english"), t.get("romaji")] + (media.get("synonyms") or []) if x and mostly_latin(x)]
    titles = list(dict.fromkeys(titles))
    chars = []
    for e in ((media.get("characters") or {}).get("edges") or []):
        n = (e.get("node") or {}).get("name") or {}
        if not n.get("full"): continue
        chars.append({"id": e["node"].get("id"), "full": n["full"], "native": n.get("native"),
                      "alts": [a for a in (n.get("alternative") or []) if mostly_latin(a)][:3], "main": (e.get("role") or "").upper() == "MAIN"})
    creators = []
    for e in ((media.get("staff") or {}).get("edges") or []):
        if e.get("role") and CREATOR_ROLE.search(e["role"]):
            nm = ((e.get("node") or {}).get("name") or {}).get("full")
            if nm and nm not in creators: creators.append(nm)
    rels = []
    for e in ((media.get("relations") or {}).get("edges") or []):
        rt = (e.get("relationType") or "").upper()
        if rt not in RELATION_PRIORITY: continue
        n = e.get("node") or {}
        if not n.get("id"): continue
        rels.append({"anilist": n["id"], "mal": n.get("idMal"), "manga": (n.get("type") or "") == "MANGA", "rt": rt})
    rels.sort(key=lambda r: RELATION_PRIORITY.index(r["rt"]))
    seen, rr = set(), []
    for r in rels:
        if r["anilist"] not in seen:
            seen.add(r["anilist"]); rr.append(r)
    return {"titles": titles, "native": t.get("native"), "creators": creators[:3], "characters": chars, "relations": rr,
            "is_manga": (media.get("type") or "") == "MANGA" if media.get("type") else fallback_manga}


def anilist_title_bridge(titles, is_manga):
    """Kimliksiz kayit icin: basligi BIREBIR eslesen en populer AniList kaydi."""
    wanted = {norm(t) for t in titles[:2] if norm(t)}
    for t in titles[:2]:
        r = anilist(AL_PAGE_QUERY, {"search": t, "type": "MANGA" if is_manga else "ANIME"})
        media = (((r or {}).get("data") or {}).get("Page") or {}).get("media") or []
        ok = []
        for m in media:
            tt = m.get("title") or {}
            names = [tt.get("english"), tt.get("romaji"), tt.get("native")] + (m.get("synonyms") or [])
            if any(n and norm(n) in wanted for n in names): ok.append(m)
        if ok:
            best = max(ok, key=lambda m: m.get("popularity") or 0)
            full = anilist(AL_QUERY, {"id": best["id"]})
            best["_full"] = (((full or {}).get("data") or {}).get("Media"))
            return best
    return None


AL_PAGE_QUERY = """
query ($search: String, $type: MediaType) {
  Page(perPage: 10) { media(search: $search, type: $type, sort: [SEARCH_MATCH]) { id idMal popularity title { romaji english native } synonyms } }
}"""


def resolve(q, trace):
    """q: dict(ids, is_manga, is_movie, is_tv, titles, year, evidence). Returns dict or None."""
    ids = q["ids"]
    weak = [None]
    if not ids and not q["is_movie"] and not q["is_tv"] and q["titles"]:
        br = anilist_title_bridge(q["titles"], q["is_manga"])
        if br:
            ids = q["ids"] = {"anilist": br["id"], "mal": br.get("idMal")}
            if br.get("_full"):
                q["evidence"] = parse_evidence(br["_full"], q["is_manga"])
            trace.append(f"baslik->AniList#{br['id']} kopru")
    ovr = ovr_lookup(ids, q["is_manga"])
    if ovr:
        si = siteinfo(ovr)
        if si:
            return {"wiki": si["wikiId"], "via": "override", "conf": "CERTAIN"}
    lookups = wd_lookups(ids, q["is_manga"], q["is_movie"])
    starts = [ids["wikidata"]] if ids.get("wikidata") else []
    qids_found = []
    if lookups or starts:
        res = wd_resolve(lookups, starts, trace)
        if res:
            qids_found = res["starts"]
            for qid in res["starts"] + res["nodes"]:
                if qid in OVR["qid"]:
                    si = siteinfo(OVR["qid"][qid])
                    if si: return {"wiki": si["wikiId"], "via": "override-qid", "conf": "CERTAIN"}
            for d in (0, 1, 2):
                c = res["wikis"].get(d)
                if c:
                    pk = pick_alive(c)
                    if pk and pk.get("_rank", 0) >= 2:
                        weak[0] = weak[0] or {"wiki": pk["wikiId"], "via": f"wikidata-P4073-depth{d}-yabanci-dil", "conf": "CERTAIN"}
                        trace.append(f"yabanci dilli wikidata sonucu ertelendi: {pk['wikiId']} ({pk.get('lang')})")
                        break
                    if pk: return {"wiki": pk["wikiId"], "via": f"wikidata-P4073-depth{d}", "conf": "CERTAIN"}
            aw = [parse_article_id(a)[0] for a in res["articles"] if parse_article_id(a)]
            if aw and not weak[0]:
                pk = pick_alive(aw)
                if pk and pk.get("_rank", 0) >= 2:
                    weak[0] = {"wiki": pk["wikiId"], "via": "wikidata-P6262-media-yabanci-dil", "conf": "CERTAIN"}
                elif pk: return {"wiki": pk["wikiId"], "via": "wikidata-P6262-media", "conf": "CERTAIN"}
    # karakter oylamasi
    qids = [s for s in starts if QID.match(s)]
    voted = None
    if lookups:
        seen = set(qids)
        for p, v in lookups[:6]:
            f = wd_find_qids(p, v, 2) or []
            for x in f:
                if x not in seen: qids.append(x); seen.add(x)
            if len(qids) >= 3: break
    votes = {}
    for qid in qids[:3]:
        mem = wd_find_qids("P1441", qid, 50) or []
        ents = wd_entities(mem) if mem else {}
        for m_ in mem:
            arts = (ents or {}).get(m_, {}).get("articles") or []
            ws = [parse_article_id(a)[0] for a in arts if parse_article_id(a)]
            ws = [w for w in dict.fromkeys(ws) if not is_blocked(w)]
            ch = choose_wiki(ws) if ws else None
            if ch: votes[ch] = votes.get(ch, 0) + 1
    if votes:
        ranked = sorted(votes.items(), key=lambda kv: (-kv[1], kv[0]))
        top = ranked[0]
        second = ranked[1][1] if len(ranked) > 1 else 0
        trace.append(f"karakter oylari: {dict(ranked[:4])}")
        if top[1] >= 2 and top[1] > second:
            si = siteinfo(top[0])
            if si and not is_blocked(si["wikiId"]) and (si.get("articles") or 0) >= 20:
                return {"wiki": si["wikiId"], "via": "character-votes", "conf": "PROBABLE"}
    else:
        trace.append("karakter oylari: yok")

    ev = q.get("evidence")
    if ev:
        for rel in ev["relations"][:4]:
            rids = {"anilist": rel["anilist"], "mal": rel["mal"]}
            o = ovr_lookup(rids, rel["manga"])
            if o:
                si = siteinfo(o)
                if si: return {"wiki": si["wikiId"], "via": "relation-override", "conf": "CERTAIN"}
            lk = wd_lookups(rids, rel["manga"], False)
            res = wd_resolve(lk, [], [])
            if res:
                for d in (0, 1, 2):
                    c = res["wikis"].get(d)
                    if c:
                        pk = pick_alive(c)
                        if pk: return {"wiki": pk["wikiId"], "via": f"relation-wikidata-{rel['rt']}", "conf": "CERTAIN"}
    r = name_path(q, ev, trace)
    return r or weak[0]


def name_path(q, ev, trace):
    all_titles = []
    seen = set()
    for t in ((ev or {}).get("titles") or []) + ([ev["native"]] if ev and ev.get("native") else []) + q["titles"]:
        t = t.strip()
        if t and len(t) <= 200 and norm(t) not in seen:
            seen.add(norm(t)); all_titles.append(t)
    base_titles = media_title_variants(all_titles)
    if not base_titles:
        return None
    creators = (ev or {}).get("creators") or []
    nev = {"titles": list(dict.fromkeys(base_titles + all_titles)), "creators": creators,
           "characters": [c["full"] for c in (ev or {}).get("characters", [])], "is_manga": q["is_manga"]}
    native_titles = [t for t in media_title_variants(all_titles) if not mostly_latin(t)]
    latin = [t for t in base_titles if mostly_latin(t)]
    latin_limit = 7 if not native_titles else 3
    queries = []
    queries += latin[:latin_limit]
    queries += native_titles[:3]
    if creators: queries.append(f"{base_titles[0]} {creators[0]}")
    if native_titles:
        if len(latin) > 3: queries.append(latin[3])
        if len(native_titles) > 3: queries.append(native_titles[3])
    qq, ks = [], set()
    for x in queries:
        if norm(x) not in ks: ks.add(norm(x)); qq.append(x)
    queries = qq[:8]
    native_keys = {norm(t) for t in native_titles}
    hits, native_hit_ids = {}, []
    for text in queries:
        for h in community_search(text):
            hits.setdefault(h["wikiId"], h)
            if norm(text) in native_keys and h["wikiId"] not in native_hit_ids:
                native_hit_ids.append(h["wikiId"])
    trace.append(f"topluluk aramasi sorgulari={queries} -> {len(hits)} aday: {list(hits)[:10]}")
    has_exact = any(title_sim(h["name"], nev["titles"], creators) >= 0.99 for h in hits.values())
    if not has_exact:
        for slug in [s for s in slug_candidates(base_titles) if s not in hits][:8]:
            si = siteinfo(slug)
            if si and si["wikiId"] not in hits:
                hits[si["wikiId"]] = {"wikiId": si["wikiId"], "name": si.get("siteName") or si["wikiId"], "language": si.get("lang"),
                                      "hub": None, "pageCount": si.get("articles"), "description": None}
                trace.append(f"slug probe bulundu: {si['wikiId']}")
    shortlisted = sorted([h for h in hits.values() if pre_veto(h) is None],
                         key=lambda h: -quick_score(h, nev))[:8]
    q["_hits"] = list(hits)
    q["_shortlist"] = [h["wikiId"] for h in shortlisted]
    if not shortlisted:
        trace.append("kisa liste bos (hepsi veto)")
        return None

    if native_titles:
        prio = [hits[w] for w in native_hit_ids if w in hits] + [h for h in hits.values() if h["wikiId"] not in native_hit_ids]
        cands = [h for h in prio if pre_veto(h) is None][:8]
        verified = []
        for h in cands:
            lks = lookup_titles(h["wikiId"], native_titles)
            ex = next((l for l in lks.values() if l["resolved"] and not l["disambig"] and norm(l["requested"]) in native_keys), None)
            if ex and siteinfo(h["wikiId"]):
                verified.append(h)
        if len(verified) == 1:
            return {"wiki": siteinfo(verified[0]["wikiId"])["wikiId"], "via": "name-native-title", "conf": "PROBABLE"}

    article_titles = media_title_variants(all_titles)
    ident_links = external_link_lookups(q["ids"], q["is_manga"], q["is_movie"], q["is_tv"])[:10]
    if article_titles:
        proofs = []
        for h in shortlisted[:6]:
            lks = lookup_titles(h["wikiId"], article_titles)
            m = article_choose(all_titles, list(lks.values()))
            if not m: continue
            info = siteinfo(h["wikiId"])
            if not info: continue
            if not strong_article_proof(h, info, all_titles, creators, q["is_manga"], q.get("year"), m): continue
            proofs.append((h, m, info))
        uniq = {p[0]["wikiId"]: p for p in proofs}
        if len(uniq) == 1:
            h, m, info = list(uniq.values())[0]
            return {"wiki": info["wikiId"], "via": "name-article-proof", "conf": "PROBABLE"}
        if len(uniq) > 1:
            trace.append(f"madde kaniti belirsiz: {list(uniq)}")

    id_cands = [h["wikiId"] for h in shortlisted[:3]]
    id_matches = set()
    if ident_links:
        linked = []
        for h in shortlisted:
            if h["wikiId"] not in id_cands: continue
            for u in ident_links:
                if has_ext_link(h["wikiId"], u):
                    linked.append((h, u)); break
        id_matches = {h["wikiId"] for h, _ in linked}
        if len(linked) == 1:
            h, u = linked[0]
            si = siteinfo(h["wikiId"])
            if si and not is_blocked(si["wikiId"]):
                return {"wiki": si["wikiId"], "via": "name-identity-link", "conf": "CERTAIN"}

    scores = []
    for h in shortlisted:
        fp = fingerprint(h["wikiId"], ev)
        sc = score(h, nev, fp)
        sc["fp"] = (f"main {fp['mainFound']}/{fp['mainTotal']} any {fp['anyFound']}/{fp['anyTotal']} native {fp['nativeConfirmed']}" if fp else "fp-yok")
        sc["hub"] = h.get("hub"); sc["pc"] = h.get("pageCount")
        scores.append(sc)
    q["_scores"] = [(s["wikiId"], round(s["score"], 2), s["decision"], s.get("veto"), s["fp"], s["hub"], s["pc"]) for s in scores]
    best = pick_best(scores)
    if not best:
        trace.append(f"isim yolu: hepsi RED {q['_scores'][:5]}")
        return None
    if best["decision"] == "PROBABLE" and best["wikiId"] not in id_cands and not id_matches:
        for link in ident_links:
            if has_ext_link(best["wikiId"], link):
                best["decision"] = "CERTAIN"; best["score"] = 1.0
                break
    si = siteinfo(best["wikiId"])
    return {"wiki": (si or {}).get("wikiId") or best["wikiId"], "via": "name-score", "conf": best["decision"], "score": round(best["score"], 2)}


def fingerprint(wiki, ev):
    chars = (ev or {}).get("characters") or []
    if not chars: return None
    main = [c for c in chars if c["main"]][:6]
    others = [c for c in chars if not c["main"]][:4]
    probes = main + others
    var = {id(c): character_title_variants(c["full"], c["alts"], 4) for c in probes}
    creator_titles = ((ev or {}).get("creators") or [])[:2]
    titles = list(dict.fromkeys(creator_titles + [t for c in probes for t in var[id(c)]]))[:50]
    lks = lookup_titles(wiki, titles)
    if not lks: return None
    main_found = any_found = native_conf = 0
    for c in probes:
        hit = next((lks[t] for t in var[id(c)] if t in lks and lks[t]["resolved"] and not lks[t]["disambig"]), None)
        if not hit: continue
        any_found += 1
        if c["main"]: main_found += 1
        if native_appears_in(c["native"], evidence_text(hit)): native_conf += 1
    return {"mainTotal": len(main), "mainFound": main_found, "anyTotal": len(probes), "anyFound": any_found,
            "nativeConfirmed": native_conf, "creatorPage": any(lks.get(t, {}).get("resolved") for t in creator_titles)}


# --------------------------------------------------------------------------- Vakalar

# (sorgu, tur, beklenen wiki | None, not)   tur: anime | manga | ln | tv | movie
CASES = [
    # Populer anime
    ("Naruto", "anime", "naruto"), ("One Piece", "anime", "onepiece"), ("Bleach", "anime", "bleach"),
    ("Attack on Titan", "anime", "attackontitan"), ("Attack on Titan Final Season", "anime", "attackontitan"),
    ("Jujutsu Kaisen", "anime", "jujutsu-kaisen"), ("Jujutsu Kaisen 2nd Season", "anime", "jujutsu-kaisen"),
    ("Demon Slayer: Kimetsu no Yaiba", "anime", "kimetsu-no-yaiba"), ("My Hero Academia", "anime", "myheroacademia"),
    ("My Hero Academia Season 5", "anime", "myheroacademia"),
    ("Fullmetal Alchemist: Brotherhood", "anime", "fma"), ("Death Note", "anime", "deathnote"),
    ("Hunter x Hunter (2011)", "anime", "hunterxhunter"), ("One Punch Man", "anime", "onepunchman"),
    ("Tokyo Ghoul", "anime", "tokyoghoul"), ("Sword Art Online", "anime", "swordartonline"),
    ("Re:Zero kara Hajimeru Isekai Seikatsu", "anime", "rezero"), ("Mob Psycho 100 II", "anime", "mob-psycho-100"),
    ("Dr. Stone", "anime", "dr-stone"), ("Black Clover", "anime", "blackclover"), ("Fairy Tail", "anime", "fairytail"),
    ("Dragon Ball Z", "anime", "dragonball"), ("Cowboy Bebop", "anime", "cowboybebop"), ("Steins;Gate", "anime", None),
    ("Code Geass: Lelouch of the Rebellion", "anime", "codegeass"), ("Neon Genesis Evangelion", "anime", "evangelion"),
    ("Haikyu!!", "anime", "haikyuu"), ("Oshi no Ko", "anime", None), ("Sousou no Frieren", "anime", None),
    ("Vinland Saga", "anime", "vinlandsaga"), ("Dandadan", "anime", None), ("Blue Lock", "anime", None),
    ("Bocchi the Rock!", "anime", None), ("Kaguya-sama: Love is War", "anime", None),
    ("Tensei shitara Slime Datta Ken", "anime", None), ("Mushoku Tensei: Jobless Reincarnation", "anime", None),
    ("Overlord", "anime", None), ("Konosuba", "anime", None), ("Gintama", "anime", "gintama"),
    ("Chainsaw Man", "anime", "chainsaw-man"), ("Spy x Family", "anime", "spy-x-family"), ("Solo Leveling", "anime", "solo-leveling"),
    # Nis anime
    ("Mushishi", "anime", None), ("Monster", "anime", None), ("Ping Pong the Animation", "anime", None),
    ("Ranking of Kings", "anime", None), ("Rurouni Kenshin", "anime", None), ("Yu Yu Hakusho", "anime", None),
    ("Great Teacher Onizuka", "anime", None), ("Planetes", "anime", None), ("Ergo Proxy", "anime", None),
    ("Paranoia Agent", "anime", None), ("Hyouka", "anime", None), ("Barakamon", "anime", None),
    ("Golden Kamuy", "anime", None), ("Kaiji", "anime", None), ("Dungeon Meshi", "anime", None),
    ("Space Brothers", "anime", None), ("Kino's Journey", "anime", None), ("Texhnolyze", "anime", None),
    ("Aria the Animation", "anime", None), ("Nichijou", "anime", None), ("Welcome to the NHK", "anime", None),
    ("Beastars", "anime", None), ("Made in Abyss", "anime", None), ("Dorohedoro", "anime", None),
    ("Soul Eater", "anime", None), ("Sket Dance", "anime", None), ("Mashle", "anime", None),
    # Yetiskin / ecchi
    ("Bible Black", "anime", None), ("Overflow", "anime", None), ("Kiss x Sis", "anime", None),
    ("Prison School", "anime", None), ("High School DxD", "anime", None), ("Redo of Healer", "anime", None),
    # Manga / manhwa / manhua
    ("Berserk", "manga", "berserk"), ("Vagabond", "manga", None), ("Claymore", "manga", None), ("Parasyte", "manga", None),
    ("Tower of God", "manga", "towerofgod"), ("The God of High School", "manga", None), ("Lookism", "manga", None),
    ("Noblesse", "manga", None), ("Solo Leveling", "manga", "solo-leveling"), ("Omniscient Reader", "manga", None),
    ("Eleceed", "manga", None), ("Teenage Mercenary", "manga", None), ("Mo Dao Zu Shi", "manga", None),
    ("Chainsaw Man", "manga", "chainsaw-man"), ("One Piece", "manga", "onepiece"), ("Jujutsu Kaisen", "manga", "jujutsu-kaisen"),
    ("Dandadan", "manga", None), ("Kaiju No. 8", "manga", None), ("Witch Hat Atelier", "manga", None),
    ("Sweet Home", "manga", None), ("Bastard", "manga", None), ("The Beginning After the End", "manga", None),
    ("Villains Are Destined to Die", "manga", None), ("Return of the Mount Hua Sect", "manga", None),
    ("Wind Breaker", "manga", None), ("Weak Hero", "manga", None), ("Soul Land", "manga", None),
    ("Battle Through the Heavens", "manga", None), ("Martial Peak", "manga", None), ("Bakuman.", "manga", None),
    # Light novel
    ("Overlord", "ln", None), ("Spice and Wolf", "ln", None), ("Re:Zero kara Hajimeru Isekai Seikatsu", "ln", "rezero"),
    ("Classroom of the Elite", "ln", None), ("The Melancholy of Haruhi Suzumiya", "ln", None), ("Log Horizon", "ln", None),
    ("Mushoku Tensei: Isekai Ittara Honki Dasu", "ln", None), ("Sword Art Online", "ln", "swordartonline"),
    # TV
    ("Breaking Bad", "tv", "breakingbad"), ("Stranger Things", "tv", "strangerthings"), ("Game of Thrones", "tv", "gameofthrones"),
    ("The Walking Dead", "tv", "walkingdead"), ("Supernatural", "tv", "supernatural"), ("The Witcher", "tv", None),
    ("Squid Game", "tv", None), ("Peaky Blinders", "tv", None), ("Severance", "tv", None), ("The Expanse", "tv", None),
    ("Mr. Robot", "tv", None), ("Black Mirror", "tv", None), ("Dark", "tv", None), ("Arcane", "tv", None),
    ("Avatar: The Last Airbender", "tv", "avatar"), ("The Mandalorian", "tv", None),
    # Film
    ("Inception", "movie", None), ("Interstellar", "movie", None), ("The Dark Knight", "movie", None),
    ("Spirited Away", "movie", None), ("Your Name.", "movie", None), ("A Silent Voice", "movie", None),
    ("Spider-Man: No Way Home", "movie", None), ("Parasite", "movie", None), ("The Lord of the Rings: The Fellowship of the Ring", "movie", None),
]

TV_CLASSES = {"Q5398426", "Q11424", "Q24862", "Q21191270", "Q15416", "Q2431196", "Q202866", "Q1259759", "Q220898", "Q1107", "Q29168811", "Q63952888", "Q117467246", "Q24869"}


def wd_pick_work(title, is_movie):
    j = jget(WD + "?" + urllib.parse.urlencode({"action": "wbsearchentities", "search": title, "language": "en", "type": "item", "limit": 8,
                                                 "format": "json", "formatversion": "2"}))
    cands = [x["id"] for x in (j or {}).get("search", [])]
    ents = wd_entities(cands) or {}
    for q in cands:
        e = ents.get(q)
        if not e: continue
        cl = e["claims"]
        p31 = claim_items(cl, "P31")
        tmdb = claim_strings(cl, "P4947" if is_movie else "P4983")
        imdb = claim_strings(cl, "P345")
        if (tmdb or imdb) and (set(p31) & TV_CLASSES or tmdb):
            return {"qid": q, "tmdb": int(tmdb[0]) if tmdb and tmdb[0].isdigit() else None, "imdb": imdb[0] if imdb else None}
    return None


def build_queries(case):
    title, kind, expected = case
    out = []
    if kind in ("anime", "manga", "ln"):
        is_manga = kind != "anime"
        variables = {"search": title, "type": "MANGA" if is_manga else "ANIME"}
        if kind == "ln": variables["formatIn"] = ["NOVEL"]
        elif kind == "manga": variables["formatIn"] = ["MANGA", "ONE_SHOT"]
        r = anilist(AL_QUERY, variables)
        media = ((r or {}).get("data") or {}).get("Media")
        if not media:
            return [], f"AniList'te bulunamadi: {title}"
        ev = parse_evidence(media, is_manga)
        t = media["title"]
        names = [x for x in [t.get("english"), t.get("romaji"), t.get("native")] if x] + (media.get("synonyms") or [])
        year = (media.get("startDate") or {}).get("year")
        movie = (media.get("format") or "") == "MOVIE"
        info = f"AniList#{media['id']} MAL#{media.get('idMal')} {t.get('romaji')}"
        out.append(("ids", {"ids": {"anilist": media["id"], "mal": media.get("idMal")}, "is_manga": is_manga, "is_movie": movie,
                            "is_tv": False, "titles": names[:12], "year": year, "evidence": ev}))
        # Eklenti benzeri: yalnizca baslik, kimlik yok (MangaDetailScreen)
        out.append(("title-only", {"ids": {}, "is_manga": is_manga, "is_movie": False, "is_tv": False,
                                   "titles": [t.get("english") or t.get("romaji") or title], "year": None, "evidence": None}))
        return out, info
    is_movie = kind == "movie"
    w = wd_pick_work(title, is_movie)
    names = [title]
    info = f"Wikidata {w}" if w else "Wikidata kimligi bulunamadi"
    ids = {}
    if w:
        ids = {"tmdb": w["tmdb"], "imdb": w["imdb"]}
    out.append(("ids", {"ids": ids, "is_manga": False, "is_movie": is_movie, "is_tv": not is_movie, "titles": names, "year": None, "evidence": None}))
    out.append(("title-only", {"ids": {}, "is_manga": False, "is_movie": is_movie, "is_tv": not is_movie, "titles": names, "year": None, "evidence": None}))
    return out, info


def run_case(case):
    title, kind, expected = case
    t0 = time.time()
    res = {"title": title, "kind": kind, "expected": expected, "runs": []}
    try:
        runs, info = build_queries(case)
    except Exception as e:  # noqa
        res["error"] = repr(e)
        return res
    res["source_info"] = info
    exp_alive = None
    if expected:
        exp_alive = bool(siteinfo(expected))
    res["expected_alive"] = exp_alive
    for mode, q in runs:
        trace = []
        try:
            r = resolve(q, trace)
        except Exception as e:  # noqa
            r = None
            trace.append("ISTISNA " + repr(e))
        got = r["wiki"] if r else None
        if expected and exp_alive:
            status = "OK" if got == expected else ("MISS" if got is None else "WRONG")
        else:
            status = "UNVERIFIED-FOUND" if got else "UNVERIFIED-MISS"
        run = {"mode": mode, "status": status, "wiki": got, "via": (r or {}).get("via"), "conf": (r or {}).get("conf"),
               "hits": q.get("_hits"), "shortlist": q.get("_shortlist"), "scores": q.get("_scores"), "trace": trace}
        if expected and exp_alive:
            run["expected_in_hits"] = expected in (q.get("_hits") or [])
            run["expected_in_shortlist"] = expected in (q.get("_shortlist") or [])
        res["runs"].append(run)
    res["secs"] = round(time.time() - t0, 1)
    return res


def report(results, out_dir, started):
    lines = []
    tot = {}
    for r in results:
        for run in r["runs"]:
            tot.setdefault(run["mode"], {}).setdefault(run["status"], 0)
            tot[run["mode"]][run["status"]] += 1
    lines.append(f"# Wiki kesif denetimi - {time.strftime('%Y-%m-%d %H:%M', time.gmtime())} UTC\n")
    lines.append(f"Vaka sayisi: {len(results)}; sure: {int(time.time() - started)} sn; ag hatasi: {_fail['n']} {_fail_by}\n")
    lines.append("## Ozet\n")
    lines.append("| mod | " + " | ".join(sorted({s for m in tot.values() for s in m})) + " |")
    sts = sorted({s for m in tot.values() for s in m})
    lines.append("|---|" + "---|" * len(sts))
    for m, d in tot.items():
        lines.append(f"| {m} | " + " | ".join(str(d.get(s, 0)) for s in sts) + " |")
    lines.append("")
    via = {}
    for r in results:
        for run in r["runs"]:
            if run["wiki"]:
                via.setdefault(run["mode"], {}).setdefault(run["via"], 0)
                via[run["mode"]][run["via"]] += 1
    lines.append("## Hangi asama buldu\n")
    for m, d in via.items():
        lines.append(f"- **{m}**: " + ", ".join(f"{k}={v}" for k, v in sorted(d.items(), key=lambda kv: -kv[1])))
    lines.append("\n## Ayrinti\n")
    lines.append("| baslik | tur | beklenen | mod | sonuc | bulunan | asama | guven | adayda? |")
    lines.append("|---|---|---|---|---|---|---|---|---|")
    for r in results:
        for run in r["runs"]:
            lines.append("| {t} | {k} | {e} | {m} | {s} | {w} | {v} | {c} | {h} |".format(
                t=r["title"], k=r["kind"], e=r["expected"] or "-", m=run["mode"], s=run["status"], w=run["wiki"] or "-",
                v=run["via"] or "-", c=run["conf"] or "-",
                h=("evet" if run.get("expected_in_hits") else "HAYIR") if r["expected"] and r.get("expected_alive") else "-"))
    lines.append("\n## Basarisiz / yanlis vakalarin izleri\n")
    for r in results:
        for run in r["runs"]:
            if run["status"] in ("MISS", "WRONG", "UNVERIFIED-MISS"):
                lines.append(f"### {r['title']} [{r['kind']}/{run['mode']}] {run['status']} (beklenen: {r['expected']}; bulunan: {run['wiki']})")
                lines.append(f"kaynak: {r.get('source_info')}")
                for t in run["trace"]:
                    lines.append(f"- {t}")
                if run.get("scores"):
                    lines.append(f"- skorlar: {run['scores']}")
                lines.append("")
    open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8").write("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "results"))
    ap.add_argument("--only", default=None)
    ap.add_argument("--workers", type=int, default=3)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    cases = [c for c in CASES if not a.only or a.only.lower() in c[0].lower()]
    started = time.time()
    results = []
    with cf.ThreadPoolExecutor(max_workers=a.workers) as ex:
        futs = {ex.submit(run_case, c): c for c in cases}
        for f in cf.as_completed(futs):
            r = f.result()
            results.append(r)
            print(f"[{len(results)}/{len(cases)}] {r['title']} ({r['kind']}): " + ", ".join(f"{x['mode']}={x['status']}:{x['wiki']}" for x in r["runs"]), flush=True)
    order = {c: i for i, c in enumerate(cases)}
    results.sort(key=lambda r: next(i for i, c in enumerate(cases) if c[0] == r["title"] and c[1] == r["kind"]))
    json.dump(results, open(os.path.join(a.out, "results.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    report(results, a.out, started)


if __name__ == "__main__":
    main()

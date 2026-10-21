#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Kurum / etiket adlarının **Latin (TR-EN) karşılığını** üretir ve doğrular.

Sorun
-----
`BangumiOrgDictionary` içinde 9.457 kurum anahtarı var; bunların **5.122'sinde**
"küresel Latin ad" alanı hâlâ CJK (Japonca/Çince) metin taşıyor. Bu değerler
`values/strings.xml` + `values-en/strings.xml` içine de aynen yazıldığı için
ekranda **Japonca/Çince kurum adı** görünüyordu.

Bu betik üç kaynağı birlikte günceller:

  1. `diag/bangumi_master/org_latin.tsv`  — anahtar → Latin ad (kalıcı kaynak)
  2. `utils/BangumiOrgDictionary.kt`      — `etiket|anahtar|küresel ad` 3. alan
  3. `res/values/strings.xml` + `res/values-en/strings.xml` — TR ve EN değerleri

Latin ad seçim sırası
---------------------
  1. **Resmî ad (tercih edilen):** CJK değer, sözlükte başka bir anahtarın takma adıysa
     (ör. `アフタヌーン` → `Afternoon`) o Latin ad kullanılır.
  2. **Önek + resmî ad:** `月刊`/`週刊`/`別冊`/`増刊` gibi önekler ayrılıp kalan ad
     sözlükte bulunursa `Monthly`/`Weekly`/`Bessatsu`/`Extra` önekiyle birleştirilir.
  3. **Romanizasyon:** kana varsa pykakasi (Hepburn), yoksa pypinyin. Başlık biçimine
     getirilir (`Gekkan Afutanuun`).

Kullanım
--------
    python3 scripts/sync_org_latin_strings.py            # yazar
    python3 scripts/sync_org_latin_strings.py --check    # yalnız denetler (CI)
"""

from __future__ import annotations

import argparse
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UTILS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils"
ORG_DICT = UTILS / "BangumiOrgDictionary.kt"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"
LATIN_TSV = ROOT / "diag/bangumi_master/org_latin.tsv"
TAG_TSV = ROOT / "diag/bangumi_master/tag_latin.tsv"

CJK = re.compile(r"[\u3040-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\uAC00-\uD7AF]")
# Romanizasyondan sonra temizlenen CJK noktalama (・ = U+30FB, ー = U+30FC, 「」, 、。 vb.)
CJK_PUNCT = re.compile(r"[\u3000-\u303F\u30FB\uFF01-\uFF65\uFFE0-\uFFEF]")
HANGUL = re.compile(r"[\uAC00-\uD7AF]")
KANA = re.compile(r"[\u3040-\u30FF]")
STRING_RE = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)

# Dergi/dergi benzeri önekler — yalnız kalan ad sözlükte bulunursa uygulanır.
MAGAZINE_PREFIXES = [
    ("週刊少年", "Weekly Shonen"),
    ("月刊少年", "Monthly Shonen"),
    ("別冊少年", "Bessatsu Shonen"),
    ("週刊", "Weekly"),
    ("月刊", "Monthly"),
    ("別冊", "Bessatsu"),
    ("増刊", "Extra"),
    ("隔週", "Biweekly"),
]

ORG_PREFIXES = ("bangumi_studio_", "bangumi_producer_", "bangumi_publisher_",
                "bangumi_magazine_", "bangumi_network_", "bangumi_org_")
TAG_PREFIXES = ("bangumi_tag_",)


def normalize(value: str) -> str:
    """Sözlüğün Kotlin tarafındaki normalize() ile aynı sadeleştirme."""
    value = value.strip()
    for opener, closer in (("「", "」"), ("『", "』"), ("（", "）"), ("(", ")")):
        if value.startswith(opener):
            value = value[len(opener):]
        if value.endswith(closer):
            value = value[: -len(closer)]
    value = value.replace("　", " ")
    return re.sub(r"\s+", " ", value).strip().lower()


def title_case(text: str) -> str:
    """`manga 4 koma paretto` → `Manga 4 Koma Paretto`; kısaltmalar korunur."""
    out = []
    for token in text.split():
        if not token:
            continue
        if token.isupper() and len(token) <= 4:
            out.append(token)
        elif token.isdigit() or re.fullmatch(r"[0-9]+[A-Za-z]*", token):
            out.append(token)
        else:
            out.append(token[:1].upper() + token[1:])
    return " ".join(out)


# ── Hangul → Revize Romanizasyon (kısaltılmış) ────────────────────────────────
_HANGUL_INITIAL = list("gkkknnndddlllmmmbbbsss​jjjcccktttppphhh")
_HANGUL_MEDIAL = list("aaeoaoaeoyeooyeyeoowaeoeowiyuyuieoeeoeoeooeueueueui")
_HANGUL_FINAL = ["", "k", "k", "k", "n", "n", "n", "t", "l", "l", "l", "l",
                 "l", "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t",
                 "k", "t", "p", "t"]

_INITIAL = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "",
            "j", "jj", "ch", "k", "t", "p", "h"]
_MEDIAL = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae",
           "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"]
_FINAL = ["", "k", "k", "k", "n", "n", "n", "t", "l", "l", "l", "l", "l",
          "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t",
          "p", "t"]


def hangul_to_latin(text: str) -> str:
    """Hangul hecelerini Revize Romanizasyon ile Latin harflerine çevirir."""
    out: list[str] = []
    for ch in text:
        code = ord(ch)
        if 0xAC00 <= code <= 0xD7A3:
            index = code - 0xAC00
            initial = index // 588
            medial = (index % 588) // 28
            final = index % 28
            out.append(_INITIAL[initial] + _MEDIAL[medial] + _FINAL[final])
        else:
            out.append(ch)
    return "".join(out)


def _kakasi(text: str) -> str:
    import pykakasi

    result = " ".join(
        item["hepburn"] for item in pykakasi.kakasi().convert(text) if item["hepburn"]
    )
    return result


def _pinyin(text: str) -> str:
    from pypinyin import lazy_pinyin

    return " ".join(lazy_pinyin(text))


def _clean(value: str) -> str:
    value = CJK_PUNCT.sub(" ", value)
    value = re.sub(r"\s+", " ", value).strip(" -_·.")
    return value


# Romanizasyon kütüphanelerinin tanımadığı uyumluluk ideografları (Japonca adlarda sık).
COMPAT_MAP = {"\ufa11": "\u5d0e", "\ufa19": "\u9ad9", "\ufa0c": "\u795e"}


def romanize(text: str) -> str:
    """CJK metni Latin harflerine çevirir (Japonca → Hepburn, Çince → pinyin, Korece → RR)."""
    cleaned = text.strip()
    if not cleaned:
        return ""
    cleaned = unicodedata.normalize("NFKC", cleaned)
    for src, dst in COMPAT_MAP.items():
        cleaned = cleaned.replace(src, dst)
    if HANGUL.search(cleaned):
        cleaned = hangul_to_latin(cleaned)

    # Japonca varsayılan (anime endüstrisi); kanji adlar da Hepburn okumasıyla çıkar.
    # Çince kısa adlar kakasi'de tek heceye düşüyor (`软房子` → "Ko"); bu durumda pinyin
    # okuması daha doğru. Kana varsa metin kesin Japoncadır — pinyin hiç denenmez.
    try:
        kakasi_result = _clean(_kakasi(cleaned))
    except Exception:
        kakasi_result = ""
    kana_present = bool(KANA.search(cleaned))
    han_count = len(CJK.findall(cleaned))

    if kakasi_result and not CJK.search(kakasi_result):
        tokens = kakasi_result.split()
        # Çince kısa adlar kakasi'de tek kısa heceye düşer (`软房子` → "Ko",
        # `电击魔王` → "Maou"); bu durumda pinyin okuması daha doğru.
        truncated = len(tokens) == 1 and len(kakasi_result) <= 4 and han_count >= 2
        if kana_present or not truncated:
            return title_case(kakasi_result)
    if not kana_present:
        try:
            pinyin_result = _clean(_pinyin(cleaned))
        except Exception:
            pinyin_result = ""
        if pinyin_result and not CJK.search(pinyin_result):
            return title_case(pinyin_result)
    if kakasi_result and not CJK.search(kakasi_result):
        return title_case(kakasi_result)

    # Son çare: kalan CJK karakterleri at (Latin kısım korunur).
    stripped = _clean(CJK.sub("", _kakasi(cleaned) if False else cleaned))
    stripped = re.sub(r"\s+", " ", stripped).strip()
    return title_case(stripped) if stripped else ""


def load_dict_rows() -> list[tuple[str, str, str]]:
    text = ORG_DICT.read_text(encoding="utf-8")
    rows = []
    for line in text.splitlines():
        if line.count("|") < 2:
            continue
        label, key, latin = line.split("|", 2)
        if not key.startswith("bangumi_"):
            continue
        rows.append((label, key, latin))
    return rows


def build_alias_index(rows: list[tuple[str, str, str]]) -> dict[str, str]:
    """Latin adı temiz olan kayıtların takma ad → Latin ad haritası."""
    index: dict[str, str] = {}
    for label, _key, latin in rows:
        if CJK.search(latin) or not latin.strip():
            continue
        key = normalize(label)
        if key and key not in index:
            index[key] = latin.strip()
    return index


WRAP_RE = re.compile(r"^[「『\"']+|[」』\"']+$")


def strip_wrapping(name: str) -> str:
    """Kaynak adın başındaki/sonundaki tırnak ve köşeli parantezleri atar.

    Bangumi infobox'ında bazı adlar `「Sunflower Studio」` gibi sarılı gelir;
    Latin adın parçası değildir, ekranda görünmemelidir.
    """
    cleaned = name.strip()
    while True:
        trimmed = WRAP_RE.sub("", cleaned).strip()
        if trimmed == cleaned or not trimmed:
            return cleaned
        cleaned = trimmed


def best_latin(raw: str, latin: str, alias_index: dict[str, str]) -> tuple[str, str]:
    """(yeni Latin ad, yöntem) döndürür. Yöntem: official | prefix | romanized | keep."""
    if not CJK.search(latin):
        return strip_wrapping(latin), "keep"

    alias_hit = alias_index.get(normalize(raw)) or alias_index.get(normalize(latin))
    if alias_hit:
        return strip_wrapping(alias_hit), "official"

    for prefix, replacement in MAGAZINE_PREFIXES:
        if latin.startswith(prefix):
            rest = latin[len(prefix):].strip()
            base = alias_index.get(normalize(rest))
            if base:
                return f"{replacement} {base}", "prefix"

    romanized = romanize(latin)
    if romanized and not CJK.search(romanized):
        return strip_wrapping(romanized), "romanized"
    romanized_raw = romanize(raw)
    if romanized_raw and not CJK.search(romanized_raw):
        return strip_wrapping(romanized_raw), "romanized"
    return strip_wrapping(latin), "unresolved"


ENTITY_RE = re.compile(r"&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9A-Fa-f]+;)")


def xml_text(value: str) -> str:
    """Android string kaynağı için XML kaçışı (`&` → `&amp;`, `<`/`>` kaçırılır)."""
    value = ENTITY_RE.sub("&amp;", value)
    return value.replace("<", "&lt;").replace(">", "&gt;")


def render_strings(path: Path, values: dict[str, str]) -> tuple[int, int]:
    """Var olan `<string name=...>` değerlerini değiştirir; eksikse ekler."""
    text = path.read_text(encoding="utf-8")
    changed = 0

    def repl(match: re.Match[str]) -> str:
        nonlocal changed
        name, body = match.group(1), match.group(2)
        raw = values.get(name)
        if raw is None:
            return match.group(0)
        new = xml_text(raw)
        if body == new:
            return match.group(0)
        changed += 1
        return f'<string name="{name}">{new}</string>'

    text = STRING_RE.sub(repl, text)
    added = 0
    missing = [(name, value) for name, value in values.items() if f'<string name="{name}">' not in text]
    if missing:
        anchor = '    <string name="bangumi_tag_'
        idx = text.find(anchor)
        if idx < 0:
            anchor = "    <string name=\"bangumi_org_"
            idx = text.find(anchor)
        block = "".join(f'    <string name="{n}">{xml_text(v)}</string>\n' for n, v in sorted(missing))
        if idx < 0:
            idx = text.rfind("</resources>")
            text = text[:idx] + block + text[idx:]
        else:
            text = text[:idx] + block + text[idx:]
        added = len(missing)
    path.write_text(text, encoding="utf-8")
    return changed, added


def write_dictionary(rows: list[tuple[str, str, str]], latin_by_key: dict[str, str]) -> int:
    text = ORG_DICT.read_text(encoding="utf-8")
    changed = 0
    out_lines = []
    for line in text.splitlines():
        if line.count("|") < 2 or "|bangumi_" not in line:
            out_lines.append(line)
            continue
        label, key, latin = line.split("|", 2)
        new = latin_by_key.get(key)
        if new and new != latin and "$" not in new and "`" not in new:
            line = f"{label}|{key}|{new}"
            changed += 1
        out_lines.append(line)
    ORG_DICT.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    return changed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="yazma yapmadan denetle")
    args = parser.parse_args()

    rows = load_dict_rows()
    alias_index = build_alias_index(rows)

    # Anahtar bazında en iyi temsili satır (etiket adayları arasından).
    by_key: dict[str, list[tuple[str, str, str]]] = {}
    for label, key, latin in rows:
        by_key.setdefault(key, []).append((label, key, latin))

    latin_by_key: dict[str, str] = {}
    methods = {"official": 0, "prefix": 0, "romanized": 0, "keep": 0, "unresolved": 0}
    for key, entries in by_key.items():
        if not key.startswith(ORG_PREFIXES + TAG_PREFIXES):
            continue
        current = entries[0][2]
        chosen, method = current, "keep"
        # Her satır için dene: ilk resmî eşleşme kazanır.
        for label, _k, latin in entries:
            candidate, candidate_method = best_latin(label, latin, alias_index)
            if candidate_method == "official":
                chosen, method = candidate, "official"
                break
            if candidate_method == "keep" and method == "keep":
                chosen = candidate
            elif candidate_method == "prefix" and method != "official":
                chosen, method = candidate, "prefix"
            elif candidate_method == "romanized" and method not in ("official", "prefix"):
                chosen, method = candidate, "romanized"
            elif candidate_method == "unresolved" and method == "keep":
                chosen, method = candidate, "unresolved"
        if CJK.search(chosen):
            # Hâlâ CJK: etiketlerden birini romanize etmeyi son kez dene.
            for label, _k, _l in entries:
                candidate = romanize(label)
                if candidate and not CJK.search(candidate):
                    chosen, method = candidate, "romanized"
                    break
        latin_by_key[key] = chosen
        methods[method] = methods.get(method, 0) + 1

    # ── Etiketler ────────────────────────────────────────────────────────────
    # `bangumi_tag_cjk_*` anahtarları sözlükte (kurum sözlüğü) yer almaz; değerleri
    # dil dosyasında CJK olarak durur. Onları da romanize ediyoruz.
    tag_fixes: dict[str, str] = {}
    # Önceden üretilmiş etiket eşlemeleri (ör. elle düzeltilmiş romanizasyon) korunur.
    if LATIN_TSV.exists():
        for line in LATIN_TSV.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#") or "\t" not in line:
                continue
            key, value = line.split("\t", 1)
            if key.startswith(TAG_PREFIXES):
                tag_fixes[key] = value
    if TAG_TSV.exists():
        for line in TAG_TSV.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#") or "\t" not in line:
                continue
            key, value = line.split("\t", 1)
            tag_fixes[key] = value
    # Sözlükte yer almayan (yalnız dil dosyasında olan) kurum/etiket anahtarları:
    # değer hâlâ CJK ise doğrudan romanize edilir.
    for name, body in STRING_RE.findall(RES_TR.read_text(encoding="utf-8")):
        if not name.startswith(ORG_PREFIXES + TAG_PREFIXES) or not CJK.search(body):
            continue
        if name in latin_by_key:
            continue
        romanized = romanize(body)
        if romanized and not CJK.search(romanized):
            tag_fixes[name] = romanized
    print("Romanize edilen etiket anahtarı   : {0}".format(len(tag_fixes)))

    dirty = {k: v for k, v in latin_by_key.items() if CJK.search(v)}
    print("Kurum/etiket anahtarı            : {0}".format(len(latin_by_key)))
    print("  resmî ad ile eşleşen           : {0}".format(methods.get("official", 0)))
    print("  önek + resmî ad                : {0}".format(methods.get("prefix", 0)))
    print("  romanize edilen                : {0}".format(methods.get("romanized", 0)))
    print("  zaten Latin                    : {0}".format(methods.get("keep", 0)))
    print("  çözülemeyen (hâlâ CJK)         : {0}".format(len(dirty)))
    if dirty:
        for key, value in sorted(dirty.items())[:10]:
            print("    ! {0} → {1}".format(key, value))

    if args.check:
        # Dil dosyalarındaki CJK sızıntısını da denetle.
        leaks = 0
        for path in (RES_TR, RES_EN):
            for name, body in STRING_RE.findall(path.read_text(encoding="utf-8")):
                if name.startswith(ORG_PREFIXES + TAG_PREFIXES) and CJK.search(body):
                    leaks += 1
        print("Dil dosyalarında CJK kurum/etiket : {0}".format(leaks))
        if dirty or leaks:
            print("HATA: kurum/etiket adlarında CJK metin kaldı.", file=sys.stderr)
            return 1
        print("TÜM KURUM VE ETİKET ADLARI LATİN — TR/EN hazır.")
        return 0

    # Sözlük alanı + dil dosyaları
    dict_changed = write_dictionary(rows, latin_by_key)

    values = {key: value for key, value in latin_by_key.items()}
    values.update(tag_fixes)
    tr_changed, tr_added = render_strings(RES_TR, values)
    en_changed, en_added = render_strings(RES_EN, values)

    LATIN_TSV.parent.mkdir(parents=True, exist_ok=True)
    LATIN_TSV.write_text(
        "# anahtar\tLatin ad\työntem\n"
        + "\n".join(sorted(f"{k}\t{v}" for k, v in latin_by_key.items()))
        + "\n",
        encoding="utf-8",
    )

    print("BangumiOrgDictionary satırı       : {0} güncellendi".format(dict_changed))
    print("values/strings.xml                : {0} değişti, {1} eklendi".format(tr_changed, tr_added))
    print("values-en/strings.xml             : {0} değişti, {1} eklendi".format(en_changed, en_added))
    print("org_latin.tsv                     : {0} anahtar".format(len(latin_by_key)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BangumiMasterDictionaryTest beklentilerinin veri düzeyinde ön denetimi.

Bu betik JUnit testinin YERİNİ TUTMAZ: Kotlin kodunu koşturmaz. Amacı, bu
sandbox'ta JVM/kotlinc kurulamadığı için test beklentilerinin üretilen veriye
gerçekten uyduğunu önceden görmektir — böylece CI'da sürpriz kırmızı yaşanmaz.

`BangumiOrgDictionary.entryFor` mantığını Python'da birebir taklit eder
(normalize → unvan eki soyma → tekrar normalize) ve testteki her iddiayı
verilere karşı sınar.

Çıkış kodu: 0 = tüm beklentiler veriyle uyumlu, 1 = uyumsuzluk.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ORG_KT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt"
TAG_KT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"

ORG_HEAD_RE = re.compile(
    r'^        o\("(?P<key>[a-z0-9_]+)", BangumiOrgRole\.(?P<role>[A-Z]+), '
    r'"(?P<tr>(?:[^"\\]|\\.)*)", "(?P<en>(?:[^"\\]|\\.)*)"',
    re.MULTILINE,
)
TAG_HEAD_RE = re.compile(
    r'^        g\("(?P<key>[a-z0-9_]+)",\s*"(?P<tr>(?:[^"\\]|\\.)*)",\s*"(?P<en>(?:[^"\\]|\\.)*)"',
    re.MULTILINE,
)
LABEL_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')

WRAPPERS = "（([【《「『"
WRAPPERS_CLOSE = "）)]】》」』"
SPACE_PUNCT = "：:・·。,，"
CORPORATE = "合同会社|株式会社|有限会社|有限公司|股份有限公司"


def normalize(value: str) -> str:
    if not value.strip():
        return ""
    out = []
    for ch in value:
        if ch == "　":
            out.append(" ")
        elif ch in WRAPPERS or ch in WRAPPERS_CLOSE:
            continue
        elif ch in SPACE_PUNCT:
            out.append(" ")
        else:
            out.append(ch)
    return re.sub(r"\s+", " ", "".join(out)).strip().lower()


def compact(value: str) -> str:
    return re.sub(r"\s+", "", normalize(value))


def strip_corporate(value: str) -> str:
    text = re.sub(r"【[^】]*】", " ", value)
    text = re.sub(r"\([^)]*\)|（[^）]*）", " ", text)
    text = re.sub(rf"^({CORPORATE})\s*", "", text)
    text = re.sub(rf"\s*({CORPORATE})$", "", text)
    return re.sub(r"\s+", " ", text).strip()


def scan_labels(text: str, start: int) -> list[str]:
    depth = 1
    index = start
    while index < len(text):
        ch = text[index]
        if ch == '"':
            index += 1
            while index < len(text) and text[index] != '"':
                if text[index] == "\\":
                    index += 1
                index += 1
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                break
        index += 1
    return LABEL_RE.findall(text[start:index])


def load(path: Path, head_re: re.Pattern) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    rows = []
    for match in head_re.finditer(text):
        row = {"key": match["key"]}
        for field in ("tr", "en", "role"):
            if field in match.groupdict():
                row[field] = match[field]
        row["labels"] = scan_labels(text, match.end())
        rows.append(row)
    return rows


class Checker:
    def __init__(self) -> None:
        self.failures: list[str] = []
        self.count = 0

    def eq(self, expected, actual, label: str) -> None:
        self.count += 1
        if expected != actual:
            self.failures.append(f"{label}: beklenen {expected!r}, bulunan {actual!r}")

    def ne(self, forbidden, actual, label: str) -> None:
        self.count += 1
        if forbidden == actual:
            self.failures.append(f"{label}: {forbidden!r} olmamalıydı")

    def true(self, condition: bool, label: str) -> None:
        self.count += 1
        if not condition:
            self.failures.append(f"{label}: koşul sağlanmadı")


def main() -> int:
    orgs = load(ORG_KT, ORG_HEAD_RE)
    tags = load(TAG_KT, TAG_HEAD_RE)

    # BangumiOrgDictionary.byLabel birebir taklidi: önce yazımlar, sonra adlar.
    org_by_label: dict[str, dict] = {}
    for row in orgs:
        for label in row["labels"]:
            org_by_label.setdefault(normalize(label), row)
    for row in orgs:
        for name in (row.get("en", ""), row.get("tr", "")):
            org_by_label.setdefault(normalize(name), row)

    def org_entry_for(name: str) -> dict | None:
        raw = name.strip()
        if not raw:
            return None
        hit = org_by_label.get(normalize(raw))
        if hit:
            return hit
        stripped = strip_corporate(raw)
        if stripped and stripped != raw:
            return org_by_label.get(normalize(stripped))
        return None

    # BangumiTagDictionary.entryFor taklidi: normalize → (kalıp atlandı) → compact.
    tag_by_label: dict[str, dict] = {}
    tag_by_compact: dict[str, dict] = {}
    for row in tags:
        for label in row["labels"]:
            tag_by_label.setdefault(normalize(label), row)
            key = compact(label)
            if len(key) >= 3:
                tag_by_compact.setdefault(key, row)
    for row in tags:
        for name in (row["en"], row["tr"]):
            tag_by_label.setdefault(normalize(name), row)
            key = compact(name)
            if len(key) >= 3:
                tag_by_compact.setdefault(key, row)

    def tag_entry_for(label: str) -> dict | None:
        raw = label.strip()
        if not raw:
            return None
        hit = tag_by_label.get(normalize(raw))
        if hit:
            return hit
        key = compact(raw)
        if len(key) >= 3:
            return tag_by_compact.get(key)
        return None

    c = Checker()

    # ── Test: `Japonca studyo adi Latin karsiligina eslenir` ──
    for name, key in (("京都アニメーション", "kyoto_animation"), ("ボンズ", "bones"),
                      ("マッドハウス", "madhouse"), ("幽浮社", "ufotable")):
        got = org_entry_for(name)
        c.eq(key, got["key"] if got else None, f"org {name}")

    # ── Test: `Cince takma adlar da ayni kayda duser` ──
    keys = {org_entry_for(n)["key"] for n in ("京阿尼", "京都动画", "京都動画", "京都アニメーション") if org_entry_for(n)}
    c.eq({"kyoto_animation"}, keys, "Kyoto varyantları")

    # ── Test: `rol siniflandirmasi korunur` ──
    for name, role in (("京都アニメーション", "STUDIO"), ("アニプレックス", "PRODUCER"),
                       ("TBSテレビ", "NETWORK"), ("集英社", "PUBLISHER")):
        got = org_entry_for(name)
        c.eq(role, got.get("role") if got else None, f"rol {name}")

    # ── Test: `sirket unvan eki eslestirmeyi bozmaz` ──
    c.eq("shueisha", (org_entry_for("株式会社集英社") or {}).get("key"), "株式会社集英社")
    c.eq("shueisha", (org_entry_for("集英社") or {}).get("key"), "集英社")

    # ── Test: `bilinmeyen kurum ve uretim komitesi null doner` ──
    for name in ("光坂高校演劇部", "PROJECT-CLS", "Bilinmeyen Stüdyo A.Ş.", ""):
        c.eq(None, org_entry_for(name), f"null beklenen: {name!r}")

    # ── Test: `After Story kisa yazimi AS yakalanir ama ASOBI yakalanmaz` ──
    c.eq("after_story", (tag_entry_for("After Story") or {}).get("key"), "After Story")
    c.eq("after_story", (tag_entry_for("AS") or {}).get("key"), "AS")
    c.ne("after_story", (tag_entry_for("ASOBI") or {}).get("key"), "ASOBI")

    # ── Test: `geleneksel ve Japonca yazim varyantlari eslenir` ──
    c.eq({"tearjerker"}, {tag_entry_for(n)["key"] for n in ("催泪", "催淚", "催涙") if tag_entry_for(n)}, "催泪 varyantları")
    c.eq("kyoto_animation", (tag_entry_for("京都") or {}).get("key"), "京都")
    c.eq("sequel", (tag_entry_for("続編") or {}).get("key"), "続編")

    # ── Test: `huayu etiketi Cince konusulan bolge olarak cevrilir` ──
    for variant in ("华语", "華語"):
        got = tag_entry_for(variant)
        c.eq("chinese_speaking", got["key"] if got else None, f"{variant} key")
        c.eq("Çince Konuşulan Bölge", got["tr"] if got else None, f"{variant} tr")
        c.eq("Chinese-speaking Region", got["en"] if got else None, f"{variant} en")

    # ── Test: `sozlukte hicbir Turkce deger Cince biciminde yazilmaz` ──
    typo = [r["key"] for r in tags if "Çinçe" in r["tr"] or "Çinçe" in r["en"]]
    c.eq([], typo, "Çinçe yazım hatası")

    # ── Test: `ters harem ile kadin haremi ayrik kayitlardir` ──
    rev = tag_entry_for("逆后宫")
    c.eq("reverse_harem", rev["key"] if rev else None, "逆后宫 key")
    c.eq("Ters Harem", rev["tr"] if rev else None, "逆后宫 tr")
    fem = tag_entry_for("Female Harem")
    c.eq("female_harem", fem["key"] if fem else None, "Female Harem key")
    c.eq("Kadın Haremi", fem["tr"] if fem else None, "Female Harem tr")

    print("BangumiMasterDictionaryTest — veri ön denetimi (JVM gerektirmez)")
    print(f"  kurum: {len(orgs)} · etiket: {len(tags)} · sınanan beklenti: {c.count}")
    for failure in c.failures:
        print(f"  ❌ {failure}")
    if c.failures:
        print(f"SONUÇ: {len(c.failures)} BEKLENTİ VERİYLE UYUŞMUYOR")
        return 1
    print("SONUÇ: TÜM TEST BEKLENTİLERİ VERİYLE UYUMLU")
    print("  not: bu bir Kotlin koşumu DEĞİLDİR; gerçek doğrulama CI'da "
          "(bangumi-master.yml → BangumiMasterDictionaryTest) yapılır.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

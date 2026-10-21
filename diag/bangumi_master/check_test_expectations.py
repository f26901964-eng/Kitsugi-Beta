#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BangumiMasterDictionaryTest beklentilerinin veri düzeyinde ön denetimi (`tags.tsv` + `orgs.tsv`).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ORG_TSV = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
TAG_TSV = ROOT / "app/src/main/resources/bangumi/tags.tsv"

WRAPPERS = "（([【《「『"
WRAPPERS_CLOSE = "）)]】》」』"
SPACE_PUNCT = "：:・·。、，,"
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
    return "".join(ch for ch in value if ch.isalnum()).lower()


def strip_corporate(value: str) -> str:
    text = re.sub(r"【[^】]*】", " ", value)
    text = re.sub(r"\([^)]*\)|（[^）]*）", " ", text)
    text = re.sub(rf"^({CORPORATE})\s*", "", text)
    text = re.sub(rf"\s*({CORPORATE})$", "", text)
    return re.sub(r"\s+", " ", text).strip()


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


def main() -> int:
    org_rows = [l.rstrip("\n").split("\t") for l in ORG_TSV.read_text(encoding="utf-8").splitlines()
                if l.strip()]
    tag_rows = [l.rstrip("\n").split("\t") for l in TAG_TSV.read_text(encoding="utf-8").splitlines()
                if l.strip()]

    org_by_label: dict[str, list[str]] = {}
    org_by_compact: dict[str, list[str]] = {}
    for row in org_rows:
        nk = normalize(row[0])
        if nk:
            org_by_label.setdefault(nk, row)
        ck = compact(row[0])
        if len(ck) >= 3:
            org_by_compact.setdefault(ck, row)

    def org_key_for(name: str) -> str | None:
        raw = name.strip()
        if not raw:
            return None
        hit = org_by_label.get(normalize(raw))
        if hit:
            return hit[1]
        ck = compact(raw)
        if len(ck) >= 3 and ck in org_by_compact:
            return org_by_compact[ck][1]
        stripped = strip_corporate(raw)
        if stripped and stripped != raw:
            hit2 = org_by_label.get(normalize(stripped))
            if hit2:
                return hit2[1]
        return None

    tag_by_label: dict[str, dict] = {}
    tag_by_compact: dict[str, dict] = {}
    tags = [{"key": r[0], "tr": r[1], "en": r[2], "labels": r[3:]} for r in tag_rows]
    for row in tags:
        for label in row["labels"]:
            nk = normalize(label)
            if nk:
                tag_by_label.setdefault(nk, row)
            ck = compact(label)
            if len(ck) >= 3:
                tag_by_compact.setdefault(ck, row)
    for row in tags:
        for name in (row["en"], row["tr"]):
            nk = normalize(name)
            if nk:
                tag_by_label.setdefault(nk, row)
            ck = compact(name)
            if len(ck) >= 3:
                tag_by_compact.setdefault(ck, row)

    def tag_entry_for(label: str) -> dict | None:
        raw = label.strip()
        if not raw:
            return None
        hit = tag_by_label.get(normalize(raw))
        if hit:
            return hit
        ck = compact(raw)
        if len(ck) >= 3:
            return tag_by_compact.get(ck)
        return None

    c = Checker()
    for name, expected in (("京アニ", "bangumi_studio_kyoto_animation"),
                           ("京都动画", "bangumi_studio_kyoto_animation"),
                           ("スタジオコロリド", "bangumi_studio_colorido"),
                           ("ボンズ", "bangumi_studio_bones"),
                           ("骨头社", "bangumi_studio_bones"),
                           ("キネマシトラス", "bangumi_studio_kinema_citrus"),
                           ("木棉花", "bangumi_network_muse"),
                           ("TOKYO MX", "bangumi_network_tokyo_mx"),
                           ("BS-i", "bangumi_network_bs_tbs"),
                           ("フジテレビ", "bangumi_network_fuji_tv"),
                           ("dアニメストア", "bangumi_network_d_anime_store"),
                           ("ニコニコ動画", "bangumi_network_niconico")):
        c.eq(expected, org_key_for(name), f"org {name}")

    for name in ("光坂高校演劇部", "PROJECT-XXX", "Clannad製作委員会", ""):
        c.eq(None, org_key_for(name), f"null beklenen: {name!r}")

    c.eq("after_story", (tag_entry_for("After Story") or {}).get("key"), "After Story")
    c.eq("after_story", (tag_entry_for("AS") or {}).get("key"), "AS")
    c.ne("after_story", (tag_entry_for("ASOBI") or {}).get("key"), "ASOBI")
    c.eq({"tearjerker"}, {tag_entry_for(n)["key"] for n in ("催泪", "催淚", "催涙") if tag_entry_for(n)}, "催泪 varyantları")
    c.eq("kyoto_animation", (tag_entry_for("京都") or {}).get("key"), "京都")
    c.eq("sequel", (tag_entry_for("続編") or {}).get("key"), "続編")

    typo = [r["key"] for r in tags if "Çinçe" in r["tr"] or "Çinçe" in r["en"]]
    c.eq([], typo, "Çinçe yazım hatası")

    print("BangumiMasterDictionaryTest — veri ön denetimi (JVM gerektirmez)")
    print(f"  kurum eşleme satırı: {len(org_rows)} · etiket grubu: {len(tags)} · sınanan beklenti: {c.count}")
    for failure in c.failures:
        print(f"  ❌ {failure}")
    if c.failures:
        print(f"SONUÇ: {len(c.failures)} BEKLENTİ VERİYLE UYUŞMUYOR")
        return 1
    print("SONUÇ: TÜM TEST BEKLENTİLERİ VERİYLE UYUMLU")
    return 0


if __name__ == "__main__":
    sys.exit(main())

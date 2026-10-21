#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — kurum sözlüğünü KOD yerine VERİ olarak üretir/doğrular (S2b).

Dosya biçimi (`app/src/main/resources/bangumi/orgs.tsv`, UTF-8, LF, başlık yok):
    <etiket> \t <string_key> \t <en> [ \t <tr> ]

  * 4. sütun (`tr`) yalnızca Türkçe gösterim İngilizce/küresel addan farklıysa yazılır;
    3 sütunlu satırlarda `tr == en` kabul edilir.
  * Sıra KORUNUR ("ilk yazım kazanır" semantiği sıraya bağlıdır).
"""
from __future__ import annotations

import argparse
import gzip
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import android_strings  # noqa: E402
import resource_catalog  # noqa: E402

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
ORG_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
STORE = ROOT / "app/src/main/resources/bangumi/orgs.tsv"


def _tr_catalog() -> dict[str, str]:
    src = resource_catalog.catalog_text(RES_TR)
    return {
        m.group(1): android_strings.unescape(m.group(2))
        for m in re.finditer(r'<string name="(bangumi_[^"]+)">(.*?)</string>', src, re.S)
    }


def rows_from_kotlin() -> list[list[str]]:
    source = ORG_DICT.read_text(encoding="utf-8")
    decl = re.search(r'private val RAW = listOf\((.*?)\)\.joinToString\(""\)', source, re.S)
    if not decl:
        raise SystemExit("BangumiOrgDictionary.kt içinde RAW listOf bloğu bulunamadı")
    chunks = re.findall(r'"""(.*?)"""', decl.group(1), re.S)
    body = "".join(chunks).replace("${'$'}", "$")
    tr_vals = _tr_catalog()
    rows: list[list[str]] = []
    for line in body.splitlines():
        if not line:
            continue
        label, key, en = line.split("|")
        tr = tr_vals.get(key, en)
        rows.append([label, key, en, tr] if tr != en else [label, key, en])
    return rows


def parse_store(text: str) -> list[list[str]]:
    rows: list[list[str]] = []
    for raw in text.splitlines():
        if not raw:
            continue
        rows.append(raw.split("\t"))
    return rows


def render(rows: list[list[str]]) -> str:
    normalized: list[str] = []
    for row in rows:
        label, key, en = row[0], row[1], row[2]
        tr = row[3] if len(row) >= 4 else en
        out = [label, key, en, tr] if tr != en else [label, key, en]
        normalized.append("\t".join(out) + "\n")
    return "".join(normalized)


def validate(rows: list[list[str]]) -> list[str]:
    errors: list[str] = []
    seen_labels: dict[str, str] = {}
    for lineno, row in enumerate(rows, 1):
        if len(row) not in (3, 4):
            errors.append(f"satır {lineno}: 3 veya 4 alan beklenir, {len(row)} bulundu")
            continue
        label, key, en = row[0], row[1], row[2]
        tr = row[3] if len(row) == 4 else en
        if not label or not key or not en or not tr:
            errors.append(f"satır {lineno}: boş alan ({row!r})")
        for value in row:
            if "\t" in value or "\n" in value or "\r" in value:
                errors.append(f"satır {lineno}: alan içinde sekme/satır sonu ({key})")
                break
        if label in seen_labels and seen_labels[label] != key:
            errors.append(f"satır {lineno}: gölgeleme {label!r} -> {seen_labels[label]} vs {key}")
        seen_labels.setdefault(label, key)
    return errors


def stats(rows: list[list[str]]) -> str:
    raw = render(rows).encode("utf-8")
    packed = gzip.compress(raw, 9)
    tr_diff = sum(1 for r in rows if len(r) >= 4 and r[3] != r[2])
    return (
        f"eşleme satırı: {len(rows):,} | TR≠EN satırı: {tr_diff:,}\n"
        f"TSV ham: {len(raw):,} bayt | gzip: {len(packed):,} bayt "
        f"({len(packed) / max(1, len(raw)):.1%})"
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-kotlin", action="store_true",
                    help="geçiş anında BangumiOrgDictionary.kt RAW bloğundan üret")
    ap.add_argument("--out", default=str(STORE), help=f"hedef yol (varsayılan {STORE})")
    ap.add_argument("--check", action="store_true",
                    help="mevcut orgs.tsv dosyasını doğrula (yazma yapmaz)")
    ap.add_argument("--stats", action="store_true")
    args = ap.parse_args()

    out = Path(args.out)
    if args.from_kotlin:
        rows = rows_from_kotlin()
    else:
        if not out.exists():
            print(f"❌ eksik dosya: {out}", file=sys.stderr)
            return 1
        rows = parse_store(out.read_text(encoding="utf-8"))

    problems = validate(rows)
    if problems:
        for problem in problems[:20]:
            print(f"❌ {problem}", file=sys.stderr)
        return 1

    text = render(rows)
    if args.check:
        if not out.exists():
            print(f"❌ eksik dosya: {out}", file=sys.stderr)
            return 1
        current = out.read_text(encoding="utf-8")
        if current == text:
            print(f"✅ {out.name}: birebir geçerli ({len(rows):,} eşleme satırı)")
            if args.stats:
                print(stats(rows))
            return 0
        print(f"❌ {out.name} kanonik biçimle eşleşmiyor", file=sys.stderr)
        return 1

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    print(f"✅ yazıldı: {out} ({len(rows):,} eşleme satırı)")
    if args.stats:
        print(stats(rows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — etiket sözlüğünü KOD yerine VERİ olarak üretir/doğrular (007 mimarisi).

Dosya biçimi (`app/src/main/resources/bangumi/tags.tsv`, UTF-8, LF, başlık yok):
    <key> \t <tr> \t <en> \t <etiket> [ \t <etiket> ... ]

  * Sıra KORUNUR (ilk yazım kazanır semantiği sıraya bağlıdır).
  * Etiketlerde sekme/satır sonu bulunamaz (üreteç doğrular).
  * `#` ile başlayan satırlar yorumdur, boş satırlar atlanır.
"""
from __future__ import annotations

import argparse
import gzip
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import tag_index  # noqa: E402

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"


def rows_from_kotlin() -> list[list[str]]:
    """Mevcut sözlük gruplarını TSV satırlarına çevirir (sıra korunur)."""
    kotlin_groups = tag_index.load_dictionary_from_kotlin()
    groups = kotlin_groups if kotlin_groups else tag_index.load_dictionary()
    return [[g["id"], g["tr"], g["en"], *g["labels"]] for g in groups]


def parse_store(text: str) -> list[list[str]]:
    rows = []
    for raw in text.splitlines():
        if not raw:
            continue
        rows.append(raw.split("\t"))
    return rows


def render(rows: list[list[str]]) -> str:
    return "".join("\t".join(row) + "\n" for row in rows)


def validate(rows: list[list[str]]) -> list[str]:
    errors: list[str] = []
    seen_ids: set[str] = set()
    for lineno, row in enumerate(rows, 1):
        if len(row) < 3:
            errors.append(f"satır {lineno}: en az 3 alan (key/tr/en) beklenir → {row[:1]}")
            continue
        key, tr, en = row[0], row[1], row[2]
        if not key:
            errors.append(f"satır {lineno}: boş key")
        if not tr or not en:
            errors.append(f"satır {lineno}: boş tr/en ({key})")
        if key in seen_ids:
            errors.append(f"satır {lineno}: yinelenen key {key}")
        seen_ids.add(key)
        for value in row:
            if "\t" in value or "\n" in value or "\r" in value:
                errors.append(f"satır {lineno}: alan içinde sekme/satır sonu ({key})")
                break
    return errors


def stats(rows: list[list[str]]) -> str:
    labels = sum(max(0, len(row) - 3) for row in rows)
    raw = render(rows).encode("utf-8")
    packed = gzip.compress(raw, 9)
    return (
        f"grup: {len(rows):,} | etiket: {labels:,}\n"
        f"TSV ham: {len(raw):,} bayt | gzip: {len(packed):,} bayt "
        f"({len(packed) / max(1, len(raw)):.1%})"
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-kotlin", action="store_true",
                    help="varsa Kotlin g(...) çağrılarından, yoksa mevcut TSV'den oku")
    ap.add_argument("--out", default=str(STORE), help=f"çıktı yolu (varsayılan {STORE})")
    ap.add_argument("--check", action="store_true",
                    help="mevcut dosya üretilenle birebir aynı mı? (yazma yapmaz)")
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
            print(f"✅ {out.name}: birebir aynı ({len(rows):,} grup)")
            if args.stats:
                print(stats(rows))
            return 0
        current_rows = parse_store(current)
        print(f"❌ {out.name} güncel değil: dosya {len(current_rows):,} grup, "
              f"üretilen {len(rows):,} grup", file=sys.stderr)
        return 1

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    print(f"✅ yazıldı: {out} ({len(rows):,} grup)")
    if args.stats:
        print(stats(rows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

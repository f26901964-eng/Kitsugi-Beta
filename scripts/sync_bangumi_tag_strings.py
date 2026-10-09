#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bangumi etiket sözlüğünü dil dosyalarıyla eşitler.

`BangumiTagDictionary.kt` içindeki HER giriş için
`res/values/strings.xml` (Türkçe) ve `res/values-en/strings.xml` (İngilizce)
dosyalarına `bangumi_tag_<key>` kaynağı yazar. Kaynaklar çeviri ekibinin
sözlüğü kod değişikliği olmadan düzeltebilmesi için vardır; çalışma zamanında
dil dosyası sözlük tablosundan önceliklidir (bkz. `toLocalizedBangumiTagOrNull`).

Kullanım:
    python3 scripts/sync_bangumi_tag_strings.py          # dosyaları günceller
    python3 scripts/sync_bangumi_tag_strings.py --check  # fark varsa 1 döner (CI)
"""

from __future__ import annotations

import argparse
import io
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICTIONARY = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"
TR_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"

TR_MARKER = "    <!-- Bangumi Etiketleri (Çince/Japonca kaynak değerler) -->"
EN_MARKER = "    <!-- Bangumi Tags (Chinese/Japanese source values) -->"

ENTRY_RE = re.compile(r'g\(\s*("(?:[^"\\]|\\.)*"\s*(?:,\s*"(?:[^"\\]|\\.)*"\s*)*)\)')
STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
KEY_RE = re.compile(r"[a-z0-9][a-z0-9_]*")


def read_entries() -> list[tuple[str, str, str]]:
    """Sözlükten (key, turkish, english) üçlülerini sırayla okur."""
    source = DICTIONARY.read_text(encoding="utf-8")
    body = source.split("private val groups: List<Group> = listOf(", 1)[1]
    body = body.split("\n    /** Normalleştirilmiş", 1)[0]

    entries: list[tuple[str, str, str]] = []
    for raw in ENTRY_RE.findall(body):
        values = STRING_RE.findall(raw)
        if len(values) < 3:
            raise SystemExit(f"Eksik sözlük girişi: {values}")
        key, turkish, english = values[0], values[1], values[2]
        if not KEY_RE.fullmatch(key):
            raise SystemExit(f"Geçersiz kaynak anahtarı (yalnızca a-z0-9_): {key}")
        entries.append((key, turkish, english))

    duplicates = [k for k, c in Counter(k for k, _, _ in entries).items() if c > 1]
    if duplicates:
        raise SystemExit(f"Yinelenen sözlük anahtarı: {duplicates}")
    return entries


def escape(value: str) -> str:
    """Android string kaynağı için kaçış."""
    out = (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", r"\'")
        .replace('"', r"\"")
    )
    return out


def render(entries: list[tuple[str, str, str]], marker: str, turkish: bool) -> str:
    lines = [marker]
    lines.append("    <!-- OTOMATİK ÜRETİLDİ: scripts/sync_bangumi_tag_strings.py -->"
                 if turkish else
                 "    <!-- AUTO-GENERATED: scripts/sync_bangumi_tag_strings.py -->")
    for key, tr, en in entries:
        value = escape(tr if turkish else en)
        lines.append(f'    <string name="bangumi_tag_{key}">{value}</string>')
    return "\n".join(lines)


def patch(path: Path, marker: str, block: str, check: bool) -> bool:
    text = path.read_text(encoding="utf-8")
    if marker not in text:
        raise SystemExit(f"{path} içinde işaret satırı bulunamadı: {marker}")
    head = text.split(marker, 1)[0]
    updated = f"{head}{block}\n\n</resources>\n"
    if updated == text:
        return False
    if not check:
        path.write_text(updated, encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="yalnızca doğrula, yazma")
    args = parser.parse_args()

    entries = read_entries()
    changed = False
    changed |= patch(TR_STRINGS, TR_MARKER, render(entries, TR_MARKER, True), args.check)
    changed |= patch(EN_STRINGS, EN_MARKER, render(entries, EN_MARKER, False), args.check)

    print(f"{len(entries)} Bangumi etiketi işlendi.")
    if args.check and changed:
        print("Dil dosyaları sözlükle uyumsuz: scripts/sync_bangumi_tag_strings.py çalıştırın.", file=sys.stderr)
        return 1
    print("Güncellendi." if changed else "Zaten güncel.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

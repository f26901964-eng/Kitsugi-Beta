#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Rusça (Shikimori) taksonomi sözlüğünü dil dosyalarıyla eşitler.

`RussianTaxonomyDictionary.kt` içindeki HER giriş için
`res/values/strings.xml` (Türkçe) ve `res/values-en/strings.xml` (İngilizce)
dosyalarına `taxonomy_tag_<key>` kaynağı yazar.

Neden dil dosyası?
  Çeviri ekibi sözlüğü **kod değiştirmeden** düzeltebilsin diye. Çalışma zamanında dil
  dosyası sözlük tablosundan önceliklidir (bkz. `taxonomyStringOverride` ve
  `toLocalizedTaxonomyLabel`).

Blok yerleşimi:
  Bu blok, Bangumi bloğunun (`bangumi_tag_*`) **hemen önüne** yazılır. Sebep:
  `sync_bangumi_tag_strings.py` kendi işaretçisinden sonraki her şeyi silip yeniden
  yazdığı için Bangumi bloğu daima dosyanın son bloğu olmalıdır.

Kullanım:
    python3 scripts/sync_taxonomy_tag_strings.py          # dosyaları günceller
    python3 scripts/sync_taxonomy_tag_strings.py --check   # fark varsa 1 döner (CI)
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICTIONARY = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/RussianTaxonomyDictionary.kt"
TR_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"

TR_MARKER = "    <!-- Kitsugi Ortak Taksonomi (Rusça Shikimori + İngilizce kaynak etiketleri) -->"
EN_MARKER = "    <!-- Kitsugi Shared Taxonomy (Russian Shikimori + English source tags) -->"

# Bangumi bloğunun başlangıcı — taksonomi bloğu bundan ÖNCE gelir.
TR_BANGUMI_MARKER = "    <!-- Bangumi Etiketleri (Çince/Japonca kaynak değerler) -->"
EN_BANGUMI_MARKER = "    <!-- Bangumi Tags (Chinese/Japanese source values) -->"

ENTRY_RE = re.compile(
    r'g\(\s*(null|\d+)\s*,\s*"([a-z0-9_]+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"'
)
KEY_RE = re.compile(r"[a-z0-9][a-z0-9_]*")


def read_entries() -> list[tuple[str, str, str]]:
    """Sözlükten (key, turkish, english) üçlülerini sırayla okur."""
    source = DICTIONARY.read_text(encoding="utf-8")
    if "private val groups: List<Group> = listOf(" not in source:
        raise SystemExit("Sözlük gövdesi bulunamadı: private val groups ... listOf(")
    body = source.split("private val groups: List<Group> = listOf(", 1)[1]

    entries: list[tuple[str, str, str]] = []
    for match in ENTRY_RE.finditer(body):
        key, turkish, english = match.group(2), match.group(3), match.group(4)
        if not KEY_RE.fullmatch(key):
            raise SystemExit(f"Geçersiz kaynak anahtarı (yalnızca a-z0-9_): {key}")
        entries.append((key, turkish, english))

    if not entries:
        raise SystemExit("Sözlükten hiç giriş okunamadı — dosya biçimi değişmiş olabilir.")

    duplicates = [k for k, c in Counter(k for k, _, _ in entries).items() if c > 1]
    if duplicates:
        raise SystemExit(f"Yinelenen sözlük anahtarı: {duplicates}")
    return entries


def escape(value: str) -> str:
    """Android string kaynağı için kaçış."""
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", r"\'")
        .replace('"', r"\"")
    )


def render(entries: list[tuple[str, str, str]], marker: str, turkish: bool) -> str:
    lines = [marker]
    lines.append(
        "    <!-- OTOMATİK ÜRETİLDİ: scripts/sync_taxonomy_tag_strings.py -->"
        if turkish
        else "    <!-- AUTO-GENERATED: scripts/sync_taxonomy_tag_strings.py -->"
    )
    for key, tr, en in entries:
        value = escape(tr if turkish else en)
        lines.append(f'    <string name="taxonomy_tag_{key}">{value}</string>')
    return "\n".join(lines)


AUTO_COMMENT_PREFIXES = ("<!-- OTOMATİK ÜRETİLDİ", "<!-- AUTO-GENERATED")

TAXONOMY_TAG_PREFIX = '<string name="taxonomy_tag_'


def _collapse_blank_runs(lines: list[str], max_blank: int = 2) -> list[str]:
    out: list[str] = []
    blanks = 0
    for line in lines:
        if line.strip():
            blanks = 0
            out.append(line)
        else:
            blanks += 1
            if blanks <= max_blank:
                out.append(line)
    return out


def patch(path: Path, marker: str, block: str, fallback_marker: str, check: bool) -> bool:
    """Bloğu **yerinde** değiştirir; dosyanın geri kalanına dokunmaz.

    `sync_bangumi_tag_strings.py` ile aynı yaklaşım: dosyadaki tüm `taxonomy_tag_*`
    kaynakları ve otomatik üretim yorumları toplanır, bulundukları yerden çıkarılır ve
    işaretçi satırının yerine tek parça halinde yeniden yazılır. Böylece iki üreteç
    birbirinin çıktısını bozmadan art arda çalışabilir (idempotent).
    """
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    if marker in text:
        marker_idx = next(i for i, line in enumerate(lines) if line == marker)
    else:
        if fallback_marker not in text:
            raise SystemExit(f"{path} içinde çapa işaretçi bulunamadı: {fallback_marker}")
        marker_idx = next(i for i, line in enumerate(lines) if line == fallback_marker)
        lines = lines[:marker_idx] + block.splitlines() + [""] + lines[marker_idx:]
        marker_idx = next(i for i, line in enumerate(lines) if line == marker)

    rebuilt: list[str] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        # Yalnızca BU betiğin otomatik yorumu + kendi kaynakları kaldırılır; Bangumi
        # üretecinin yorumuna dokunulmaz (iki betik art arda çalışabilir).
        if index != marker_idx and (
            stripped.startswith(TAXONOMY_TAG_PREFIX)
            or ("sync_taxonomy_tag_strings.py" in stripped and stripped.startswith(AUTO_COMMENT_PREFIXES))
        ):
            continue
        if index == marker_idx:
            rebuilt.extend(block.splitlines())
            continue
        rebuilt.append(line)

    rebuilt = _collapse_blank_runs(rebuilt)
    updated = "\n".join(rebuilt)
    if text.endswith("\n"):
        updated += "\n"
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
    changed |= patch(TR_STRINGS, TR_MARKER, render(entries, TR_MARKER, True), TR_BANGUMI_MARKER, args.check)
    changed |= patch(EN_STRINGS, EN_MARKER, render(entries, EN_MARKER, False), EN_BANGUMI_MARKER, args.check)

    print(f"{len(entries)} taksonomi etiketi işlendi.")
    if args.check and changed:
        print(
            "Dil dosyaları sözlükle uyumsuz: scripts/sync_taxonomy_tag_strings.py çalıştırın.",
            file=sys.stderr,
        )
        return 1
    print("Güncellendi." if changed else "Zaten güncel.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

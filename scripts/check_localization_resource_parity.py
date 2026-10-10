#!/usr/bin/env python3
"""Check that Android locale resources stay structurally in sync.

Usage:
    python3 scripts/check_localization_resource_parity.py

The default Turkish resources are the source locale and ``values-en`` is the
English translation. String-array labels may differ by locale; option values
must stay identical so preference persistence is unaffected.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
TR_STRINGS = RES / "values/strings.xml"
EN_STRINGS = RES / "values-en/strings.xml"
TR_ARRAYS = RES / "values/arrays.xml"
EN_ARRAYS = RES / "values-en/arrays.xml"

# Android's formatted string placeholders. ``%%`` is a literal percent sign,
# not an argument, and is deliberately excluded.
FORMAT_TOKEN = re.compile(r"%(?:(\d+)\$)?[-+0-9.]*[sdif]")


def named_resources(path: Path, expected_tag: str) -> dict[str, ET.Element]:
    root = ET.parse(path).getroot()
    values = [node for node in root if node.tag == expected_tag]
    names = [node.attrib.get("name", "") for node in values]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise ValueError(f"{path}: duplicate {expected_tag} names: {duplicates[:10]}")
    return {node.attrib["name"]: node for node in values}


def text(node: ET.Element) -> str:
    return "".join(node.itertext())


def placeholders(value: str) -> list[str]:
    tokens: list[str] = []
    index = 0
    while index < len(value):
        if value.startswith("%%", index):
            index += 2
            continue
        match = FORMAT_TOKEN.match(value, index)
        if match:
            tokens.append(match.group(0))
            index = match.end()
        else:
            index += 1
    return sorted(tokens)


def main() -> int:
    try:
        tr_strings = named_resources(TR_STRINGS, "string")
        en_strings = named_resources(EN_STRINGS, "string")
        tr_arrays = named_resources(TR_ARRAYS, "string-array")
        en_arrays = named_resources(EN_ARRAYS, "string-array")
    except (ET.ParseError, KeyError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1

    tr_keys = set(tr_strings)
    en_keys = set(en_strings)
    tr_arrays_keys = set(tr_arrays)
    en_arrays_keys = set(en_arrays)
    errors: list[str] = []
    if tr_keys != en_keys:
        errors.append(
            f"string key mismatch: missing EN={len(tr_keys - en_keys)}, "
            f"extra EN={len(en_keys - tr_keys)}"
        )
        if tr_keys - en_keys:
            errors.append("missing EN sample: " + ", ".join(sorted(tr_keys - en_keys)[:12]))
        if en_keys - tr_keys:
            errors.append("extra EN sample: " + ", ".join(sorted(en_keys - tr_keys)[:12]))
    if tr_arrays_keys != en_arrays_keys:
        errors.append(
            f"array key mismatch: missing EN={sorted(tr_arrays_keys - en_arrays_keys)}, "
            f"extra EN={sorted(en_arrays_keys - tr_arrays_keys)}"
        )

    for key in sorted(tr_keys & en_keys):
        tr_args = placeholders(text(tr_strings[key]))
        en_args = placeholders(text(en_strings[key]))
        if tr_args != en_args:
            errors.append(f"placeholder mismatch for {key}: TR={tr_args}, EN={en_args}")

    for key in sorted(tr_arrays_keys & en_arrays_keys):
        tr_items = [text(item) for item in tr_arrays[key] if item.tag == "item"]
        en_items = [text(item) for item in en_arrays[key] if item.tag == "item"]
        if len(tr_items) != len(en_items):
            errors.append(f"array item count mismatch for {key}: TR={len(tr_items)}, EN={len(en_items)}")
        if key.endswith("_values") and tr_items != en_items:
            errors.append(f"preference values must match exactly for {key}")

    if errors:
        print("Localization resource validation failed:", file=sys.stderr)
        print("\n".join(f"- {error}" for error in errors), file=sys.stderr)
        return 1

    print(
        f"Localization resources are in sync: {len(tr_keys)} strings and "
        f"{len(tr_arrays_keys)} arrays (including matching format placeholders)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

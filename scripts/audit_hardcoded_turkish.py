#!/usr/bin/env python3
"""Inventory source-code string literals containing Turkish-specific letters.

Comments are ignored, and strings inside Kotlin interpolation expressions are
scanned as separate literals. This is an audit aid, not a localization
completeness proof: Turkish words without diacritics are intentionally not
classified automatically.

Usage:
    python3 scripts/audit_hardcoded_turkish.py [--all] [--top N]
"""
from __future__ import annotations

import argparse
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app/src"
TURKISH = re.compile(r"[çğıöşüÇĞİÖŞÜ]")


@dataclass(frozen=True)
class Literal:
    path: Path
    line: int
    value: str


def skip_block_comment(source: str, index: int) -> int:
    depth = 1
    index += 2
    while index < len(source) and depth:
        if source.startswith("/*", index):
            depth += 1
            index += 2
        elif source.startswith("*/", index):
            depth -= 1
            index += 2
        else:
            index += 1
    return index


def scan_code(source: str, start: int, path: Path, found: list[Literal], stop_at_brace: bool = False) -> int:
    """Scan Kotlin/Java code, recursing into strings and interpolation code."""
    index = start
    brace_depth = 1 if stop_at_brace else 0
    while index < len(source):
        if source.startswith("//", index):
            newline = source.find("\n", index + 2)
            index = len(source) if newline < 0 else newline + 1
            continue
        if source.startswith("/*", index):
            index = skip_block_comment(source, index)
            continue
        if source.startswith('"""', index):
            index = scan_string(source, index, path, found, raw=True)
            continue
        char = source[index]
        if char == '"':
            index = scan_string(source, index, path, found, raw=False)
            continue
        if char == "'":
            index += 1
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == "'":
                    index += 1
                    break
                else:
                    index += 1
            continue
        if stop_at_brace:
            if char == "{":
                brace_depth += 1
            elif char == "}":
                brace_depth -= 1
                if brace_depth == 0:
                    return index + 1
        index += 1
    return index


def scan_string(source: str, start: int, path: Path, found: list[Literal], raw: bool) -> int:
    triple = raw and source.startswith('"""', start)
    index = start + (3 if triple else 1)
    line = source.count("\n", 0, start) + 1
    value: list[str] = []
    closing = '"""' if triple else '"'
    while index < len(source):
        if source.startswith(closing, index):
            index += len(closing)
            if value:
                found.append(Literal(path, line, ''.join(value)))
            return index
        char = source[index]
        if not triple and char == "\\":
            # Preserve escaped characters other than quote delimiters; they
            # cannot introduce a Turkish letter by themselves.
            if index + 1 < len(source):
                escaped = source[index + 1]
                if escaped in "\\\"'":
                    value.append(escaped)
                elif escaped == "n":
                    value.append("\n")
                elif escaped == "t":
                    value.append("\t")
                index += 2
            else:
                index += 1
            continue
        if char == "$" and index + 1 < len(source):
            if source[index + 1] == "{":
                end = scan_code(source, index + 2, path, found, stop_at_brace=True)
                value.append("${…}")
                index = end
                continue
            if re.match(r"[A-Za-z_]", source[index + 1]):
                match = re.match(r"[A-Za-z_][A-Za-z0-9_]*", source[index + 1:])
                value.append("${…}")
                index += 1 + len(match.group(0))
                continue
        value.append(char)
        if char == "\n":
            # Keep the line of the literal's beginning for concise reports.
            pass
        index += 1
    if value:
        found.append(Literal(path, line, ''.join(value)))
    return index


def files_to_scan() -> list[Path]:
    suffixes = {".kt", ".java", ".kts"}
    return sorted(
        path for path in SOURCE_ROOT.rglob("*")
        if path.is_file() and path.suffix in suffixes
        and not any(part in {"build", ".gradle", ".git"} for part in path.parts)
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="print every matching literal")
    parser.add_argument("--top", type=int, default=25, help="number of files to show (default: 25)")
    args = parser.parse_args()

    matches: list[Literal] = []
    for path in files_to_scan():
        try:
            source = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        found: list[Literal] = []
        scan_code(source, 0, path, found)
        matches.extend(literal for literal in found if TURKISH.search(literal.value))

    by_file = Counter(literal.path for literal in matches)
    print(f"{len(matches)} Turkish-character string literals across {len(by_file)} files")
    if args.all:
        for literal in matches:
            relative = literal.path.relative_to(ROOT)
            preview = literal.value.replace("\n", "\\n")
            if len(preview) > 240:
                preview = preview[:237] + "..."
            print(f"{relative}:{literal.line}: {preview}")
    else:
        for path, count in by_file.most_common(max(0, args.top)):
            print(f"{count:4}  {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

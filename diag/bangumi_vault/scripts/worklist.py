#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — çeviri iş listesi üreticisi.

Henüz KARAR verilmemiş (decision alanı olmayan) kayıtları `count` azalan sırada
`worklists/` altına yazar. Çeviri turları (chunk) bu listeler üzerinden yürür.

Kullanım:
  python3 diag/bangumi_vault/scripts/worklist.py [--min-count N] [--role tag|studio|...]
  python3 diag/bangumi_vault/scripts/worklist.py --dump tag 174   # ilk N kaydı ekrana bas

Çıktılar:
  worklists/tags_pending.tsv    key \t count \t ad \t labels
  worklists/orgs_pending.tsv    key \t role \t count \t ad \t labels
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
WORK = VAULT / "worklists"

ROLE_FILES = {
    "studio": "studios.json",
    "producer": "producers.json",
    "network": "networks.json",
    "publisher": "publishers.json",
    "magazine": "magazines.json",
    "committee": "committees.json",
    "tag": "tags.json",
}


def load(role: str) -> list[dict]:
    path = VAULT / ROLE_FILES[role]
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def undecided(records: list[dict]) -> list[dict]:
    return [r for r in records if not r.get("decision")]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--min-count", type=int, default=1)
    ap.add_argument("--role", default=None)
    ap.add_argument("--dump", nargs=2, metavar=("ROLE", "N"), default=None,
                    help="İlk N kaydı ekrana bas (chunk planlaması için)")
    args = ap.parse_args()

    if args.dump:
        role, n = args.dump[0], int(args.dump[1])
        rows = sorted(undecided(load(role)), key=lambda r: (-(r.get("count") or 0), r["key"]))
        for r in rows[:n]:
            name = r.get("tr") or ""
            labels = ";".join(r.get("labels", []))
            print(f"{r['key']}\t{r.get('count') or 0}\t{name}\t{labels[:80]}")
        print(f"-- {role}: toplam kararsız {len(rows)}, gösterilen {min(n, len(rows))}", file=sys.stderr)
        return 0

    WORK.mkdir(exist_ok=True)
    roles = [args.role] if args.role else list(ROLE_FILES)
    total = 0
    tag_body: list[str] = []
    org_body: list[str] = []
    tag_n = org_n = 0
    for role in roles:
        rows = [r for r in sorted(undecided(load(role)), key=lambda r: (-(r.get("count") or 0), r["key"]))
                if (r.get("count") or 0) >= args.min_count]
        if role == "tag":
            tag_body += [f"{r['key']}\t{r.get('count') or 0}\t{r.get('tr') or ''}\t{';'.join(r.get('labels', []))}\n" for r in rows]
            tag_n += len(rows)
        else:
            org_body += [f"{r['key']}\t{role}\t{r.get('count') or 0}\t{r.get('tr') or ''}\t{';'.join(r.get('labels', []))}\n" for r in rows]
            org_n += len(rows)
        total += len(rows)
        print(f"{role:12} → {len(rows):6} kayıt (count>={args.min_count})")
    # DÜZELTME (2026-10-21): tüm kurum rolleri TEK orgs_pending.tsv'de toplanır.
    # Önceki sürüm her rolde dosyanın üzerine yazıyordu → yalnız son rol (committee) kalıyordu.
    if "tag" in roles:
        (WORK / "tags_pending.tsv").write_text(
            "# key \t count \t ad \t labels\t(count azalan; kararsız kayıtlar)\n" + "".join(tag_body),
            encoding="utf-8")
    if any(r != "tag" for r in roles):
        (WORK / "orgs_pending.tsv").write_text(
            "# key \t role \t count \t ad \t labels\t(count azalan; kararsız kayıtlar)\n" + "".join(org_body),
            encoding="utf-8")
    print(f"TOPLAM: {total} (etiket {tag_n}, kurum {org_n})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

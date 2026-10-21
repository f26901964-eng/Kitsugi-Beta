#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — karar uygulayıcı (chunk → vault).

`canon.tsv` (kanonik karşılık tablosu) + `decisions/map_chunk_*.tsv` (vault kaydı → kanon)
dosyalarını okur ve:

  1. vault/*.json kayıtlarına `decision`, `canon`, `string_key`, `chunk` alanlarını işler,
     tr/en değerlerini kanon değerleriyle eşitler (as_is/SKIP hariç),
  2. `translations_done.tsv` üzerine karar dökümü ekler (append-only),
  3. `translations_pending.tsv` içinden karar verilen satırları düşer,
  4. `progress.json` sayaçlarını günceller.

Karar sözlüğü (canon_id alanı):
  SKIP           → kararsız değil ama dil dosyasına GİRMEZ; etiket ham gösterilir (junk/spam/kişisel not).
  <canon_id>     → canon.tsv'teki satıra bağlanır (global karşılık, çeviri ya da as_is kanonu).

Kullanım:
  python3 diag/bangumi_vault/scripts/apply_map.py            # uygula
  python3 diag/bangumi_vault/scripts/apply_map.py --check    # yalnızca doğrula
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
DEC = VAULT / "decisions"
ROLE_FILES = {
    "studio": "studios.json",
    "producer": "producers.json",
    "network": "networks.json",
    "publisher": "publishers.json",
    "magazine": "magazines.json",
    "committee": "committees.json",
    "tag": "tags.json",
}
CHECK = "--check" in sys.argv


def load_canon() -> dict[tuple[str, str], dict]:
    """(role, canon_id) → kanon satırı. Aynı canon_id farklı rollerde tekrar kullanılabilir."""
    path = DEC / "canon.tsv"
    out: dict[tuple[str, str], dict] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) < 6:
            raise SystemExit(f"canon.tsv: 6+ alan beklenir → {raw[:80]}")
        cid, role, tr, en, origin, aliases = parts[:6]
        note = parts[6] if len(parts) > 6 else ""
        string_key = parts[7] if len(parts) > 7 and parts[7] else None
        if (role, cid) in out:
            raise SystemExit(f"canon.tsv: yinelenen (rol, canon_id) ({role}, {cid})")
        out[(role, cid)] = {"id": cid, "role": role, "tr": tr, "en": en, "origin": origin,
                            "aliases": [a for a in aliases.split(";") if a], "note": note,
                            "string_key": string_key}
    return out


def load_maps() -> list[tuple[str, str, str, str]]:
    """(chunk, vault_key, canon_id, note) satırları — tüm map_chunk_*.tsv dosyalarından."""
    rows = []
    for path in sorted(DEC.glob("map_chunk_*.tsv")):
        chunk = path.stem.replace("map_chunk_", "")
        for raw in path.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.lstrip().startswith("#"):
                continue
            parts = raw.split("\t")
            if len(parts) < 2:
                raise SystemExit(f"{path.name}: 2+ alan beklenir → {raw[:80]}")
            key, cid = parts[0], parts[1]
            note = parts[2] if len(parts) > 2 else ""
            rows.append((chunk, key, cid, note))
    return rows


def main() -> int:
    canon = load_canon()
    maps = load_maps()
    print(f"canon satırı: {len(canon)} | map satırı: {len(maps)}")

    # vault kayıtlarını indexle (aynı key committees.json + kategori + vault.json arşivinde kopya olabilir)
    # bellekteki verileri TUT: damgalama bu nesneler üzerinde çalışır, yazma da bunları yazar
    vault: dict[str, list[tuple[str, dict]]] = {}
    file_data: dict[str, list[dict]] = {}
    for role, fn in ROLE_FILES.items():
        with (VAULT / fn).open(encoding="utf-8") as f:
            data = json.load(f)
        file_data[fn] = data
        for rec in data:
            vault.setdefault(rec["key"], []).append((fn, rec))
    archive_path = VAULT / "vault.json"
    archive = json.loads(archive_path.read_text(encoding="utf-8"))
    for bucket in ("orgs", "tags"):
        for rec in archive.get(bucket, []):
            vault.setdefault(rec["key"], []).append(("vault.json", rec))

    errors, applied = [], 0
    done_rows = []
    for chunk, key, cid, note in maps:
        if key not in vault:
            errors.append(f"map_chunk_{chunk}: vault'ta olmayan key {key}")
            continue
        copies = vault[key]
        rec = copies[0][1]

        def stamp(chunk: str, decision: str, canon_id, sk, tr, en) -> None:
            for _, r in copies:
                r["decision"] = decision
                r["canon"] = canon_id
                r["string_key"] = sk
                r["chunk"] = chunk
                if tr is not None:
                    r["tr"], r["en"] = tr, en

        if cid == "SKIP":
            stamp(chunk, "skip", None, None, None, None)
            done_rows.append((key, rec.get("role") or "", "skip", "", "", note))
            continue
        # rol: kayıtta yoksa anahtar önekinden (bangumi_tag_… → tag)
        role_key = rec.get("role") or ("tag" if key.startswith("bangumi_tag_") else "")
        if role_key and (role_key, cid) in canon:
            c = canon[(role_key, cid)]
            stamp(chunk,
                  {"global": "global", "new": "translated", "as_is": "as_is",
                   "existing": "existing"}[c["origin"]],
                  cid,
                  c.get("string_key") or (("bangumi_tag_" + cid) if role_key == "tag"
                                          else f"bangumi_{role_key}_{cid}"),
                  c["tr"], c["en"])
            done_rows.append((key, role_key, c["origin"], c["tr"], c["en"], note or f"canon:{cid}"))
            applied += 1
        else:
            errors.append(f"map_chunk_{chunk}: canon.tsv'te olmayan (rol, canon_id) "
                          f"({role_key}, {cid}) — {key}")

    if errors:
        print("HATA:\n  - " + "\n  - ".join(errors[:30]), file=sys.stderr)
        if len(errors) > 30:
            print(f"  ... +{len(errors) - 30} hata daha", file=sys.stderr)
        return 1

    print(f"uygulanan kayıt: {applied + sum(1 for r in done_rows if r[2] == 'skip')} "
          f"(canon {applied}, skip {sum(1 for r in done_rows if r[2] == 'skip')})")

    if CHECK:
        return 0

    # vault dosyalarını yaz (bellekte damgalanan kopyalar)
    for role, fn in ROLE_FILES.items():
        path = VAULT / fn
        data = file_data[fn]
        if any(r.get("chunk") for r in data):
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if any(r.get("chunk") for bucket in ("orgs", "tags") for r in archive.get(bucket, [])):
        archive_path.write_text(json.dumps(archive, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # done log (append, yinelenen key'ler tekrar yazılmaz)
    done_path = VAULT / "translations_done.tsv"
    seen = set()
    if done_path.exists():
        for raw in done_path.read_text(encoding="utf-8").splitlines():
            if raw and not raw.startswith("#"):
                seen.add(raw.split("\t", 1)[0])
    with done_path.open("a", encoding="utf-8") as f:
        for row in done_rows:
            if row[0] in seen:
                continue
            f.write("\t".join(row) + "\n")
            seen.add(row[0])

    # pending'ten düş
    pending = VAULT / "translations_pending.tsv"
    if pending.exists():
        lines = pending.read_text(encoding="utf-8").splitlines()
        head, body = lines[0], lines[1:]
        kept = [l for l in body if l.split("\t", 1)[0] not in seen]
        pending.write_text(head + "\n" + "\n".join(kept) + ("\n" if kept else ""), encoding="utf-8")
        print(f"translations_pending: {len(body)} → {len(kept)} satır")

    # progress.json
    prog_path = VAULT / "progress.json"
    prog = json.loads(prog_path.read_text(encoding="utf-8")) if prog_path.exists() else {}
    counts = {}
    for role, fn in ROLE_FILES.items():
        with (VAULT / fn).open(encoding="utf-8") as f:
            data = json.load(f)
        counts[role] = {
            "total": len(data),
            "decided": sum(1 for r in data if r.get("decision")),
            "skip": sum(1 for r in data if r.get("decision") == "skip"),
        }
    prog["counts"] = counts
    prog["chunks"] = sorted({c for c, *_ in maps})
    prog_path.write_text(json.dumps(prog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

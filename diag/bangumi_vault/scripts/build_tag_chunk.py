#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — etiket chunk karar uygulayıcısı (chunk 006+).

Elle verilen karar tablosunu (`--decisions`) iki karar dosyasına çevirir:

  * `decisions/canon.tsv`  → yeni `tag` kanon satırları (yalnız eksik cid'ler için),
  * `decisions/map_chunk_<chunk>_tags.tsv` → `vault_key <TAB> canon_id|SKIP <TAB> not`.

Karar tablosu biçimi (TAB, başlık YOK, `#` yorum satırı):

    <i> <TAB> <canon_id|SKIP> <TAB> <TR> <TAB> <EN> <TAB> <origin> <TAB> <not>

`i`, `--min-count/--max-count` aralığındaki KARARSIZ etiket kayıtlarının
`(-count, key)` sırasındaki 0 tabanlı indeksidir (worklist sırasıyla aynı).
SKIP satırlarında TR/EN/origin boş bırakılır.

Koruyucular (hepsi ihlalde SystemExit):
  * her indeks tam bir kez karara bağlanmış olmalı (eksik/fazla/çift → hata),
  * mevcut `(tag, canon_id)` yeniden yazılmaz; TR/EN çelişkisi varsa hata,
  * mevcut `bangumi_tag_<cid>` kaynağının değeri farklıysa hata (kısa cid tuzağı),
  * aynı cid iki farklı TR/EN ile verilemez,
  * SKIP dışı satırlarda TR ve EN boş olamaz.

Kullanım:
  python3 diag/bangumi_vault/scripts/build_tag_chunk.py --chunk 006_a \
      --min-count 60 --max-count 99 --decisions /tmp/dec.tsv [--write]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import resource_catalog  # noqa: E402

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
DEC = VAULT / "decisions"
CANON = DEC / "canon.tsv"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"

ORIGINS = {"existing", "global", "new", "as_is"}


def load_records(min_count: int, max_count: int, snapshot: Path | None = None) -> list[dict]:
    if snapshot is not None:
        rows = json.loads(snapshot.read_text(encoding="utf-8"))
    else:
        with (VAULT / "tags.json").open(encoding="utf-8") as f:
            tags = json.load(f)
        rows = [r for r in tags if not r.get("decision")]
    rows = [r for r in rows if min_count <= (r.get("count") or 0) <= max_count]
    rows.sort(key=lambda r: (-(r.get("count") or 0), r["key"]))
    return rows


def load_canon() -> dict[tuple[str, str], list[str]]:
    out: dict[tuple[str, str], list[str]] = {}
    for raw in CANON.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        out[(parts[1], parts[0])] = parts
    return out


def load_decisions(path: Path) -> dict[int, dict]:
    out: dict[int, dict] = {}
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) < 2:
            raise SystemExit(f"{path.name}:{lineno}: 2+ alan beklenir → {raw[:80]}")
        idx = int(parts[0])
        cid = parts[1].strip()
        tr = parts[2] if len(parts) > 2 else ""
        en = parts[3] if len(parts) > 3 else ""
        origin = parts[4] if len(parts) > 4 else ""
        note = parts[5] if len(parts) > 5 else ""
        if idx in out:
            raise SystemExit(f"{path.name}:{lineno}: yinelenen indeks {idx}")
        if cid == "SKIP":
            out[idx] = {"cid": None, "tr": "", "en": "", "origin": "skip", "note": note}
            continue
        if not re.fullmatch(r"[a-z0-9_]+", cid):
            raise SystemExit(f"{path.name}:{lineno}: geçersiz canon_id {cid!r}")
        if origin not in ORIGINS:
            raise SystemExit(f"{path.name}:{lineno}: geçersiz origin {origin!r}")
        if not tr or not en:
            raise SystemExit(f"{path.name}:{lineno}: TR/EN boş olamaz ({cid})")
        out[idx] = {"cid": cid, "tr": tr, "en": en, "origin": origin, "note": note}
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--chunk", required=True, help="ör. 006_a")
    ap.add_argument("--min-count", type=int, required=True)
    ap.add_argument("--max-count", type=int, required=True)
    ap.add_argument("--decisions", required=True, type=Path)
    ap.add_argument("--snapshot", type=Path, default=None,
                    help="damgalanmış vault yerine sabit kayıt listesi (yeniden üretim)")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()

    records = load_records(args.min_count, args.max_count, args.snapshot)
    decisions = load_decisions(args.decisions)
    errors: list[str] = []

    missing = [i for i in range(len(records)) if i not in decisions]
    extra = [i for i in decisions if i >= len(records)]
    if missing:
        errors.append(f"{len(missing)} indeks karara bağlanmamış: {missing[:10]}")
    if extra:
        errors.append(f"aralık dışı indeks: {extra[:10]}")

    canon = load_canon()
    tr_src = resource_catalog.catalog_text(RES_TR)
    xml_tr = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', tr_src, re.S))
    from android_strings import unescape
    xml_tr = {k: unescape(v) for k, v in xml_tr.items()}

    new_rows: list[list[str]] = []
    by_cid: dict[str, tuple[str, str]] = {}
    emitted: set[str] = set()
    map_rows: list[str] = []
    stats = {"skip": 0, "existing": 0, "global": 0, "new": 0, "as_is": 0}

    for idx, rec in enumerate(records):
        d = decisions.get(idx)
        if d is None:
            continue
        key = rec["key"]
        if d["cid"] is None:
            stats["skip"] += 1
            map_rows.append(f"{key}\tSKIP\t{d['note']}")
            continue
        cid, tr, en, origin = d["cid"], d["tr"], d["en"], d["origin"]
        map_rows.append(f"{key}\t{cid}\t{d['note']}")
        stats[origin] += 1

        if cid in by_cid and by_cid[cid] != (tr, en):
            errors.append(f"{cid}: iki farklı TR/EN ile verilmiş {by_cid[cid]} vs {(tr, en)}")
        by_cid[cid] = (tr, en)

        existing = canon.get(("tag", cid))
        if existing:
            if existing[2] != tr or existing[3] != en:
                errors.append(f"{cid}: canon.tsv'te mevcut TR/EN farklı "
                              f"(canon={existing[2]}/{existing[3]} vs {tr}/{en})")
            continue
        res_key = f"bangumi_tag_{cid}"
        if res_key in xml_tr and xml_tr[res_key] != tr:
            errors.append(f"{res_key}: mevcut kaynak değeri farklı "
                          f"(xml={xml_tr[res_key]!r} vs yeni={tr!r}) — kısa cid tuzağı?")
        if cid in emitted:
            # aynı cid bu bantta ikinci kez karara bağlandı (ör. エンターブレイン + Enterbrain)
            # → canon satırı TEK olmalı; kanon zaten yazıldı.
            continue
        emitted.add(cid)
        labels = [l for l in (rec.get("labels") or []) if l]
        aliases = [l for l in labels if l not in (tr, en)]
        new_rows.append([cid, "tag", tr, en, origin, ";".join(aliases),
                         d["note"] or f"chunk {args.chunk}"])

    if errors:
        print(f"❌ {len(errors)} hata:", file=sys.stderr)
        for e in errors[:40]:
            print("  - " + e, file=sys.stderr)
        return 1

    print(f"{args.chunk}: {len(records)} kayıt → "
          + " ".join(f"{k}={v}" for k, v in stats.items() if v))
    print(f"yeni canon satırı: {len(new_rows)} | map satırı: {len(map_rows)}")
    if not args.write:
        print("(--write yok: yazılmadı)")
        return 0

    if new_rows:
        with CANON.open("a", encoding="utf-8") as f:
            for row in new_rows:
                f.write("\t".join(row) + "\n")
    header = (f"# Bangumi VAULT — chunk {args.chunk} · ETİKET haritası "
              f"(count {args.min_count}–{args.max_count} · {len(map_rows)} kayıt)\n"
              "# Biçim: vault_key <TAB> canon_id|SKIP <TAB> not\n"
              "# Kişisel raf/puan, tarih/dönem, gürültü ve belirsiz kısaltmalar SKIP; bilinmeyen CJK adlar as_is.\n")
    (DEC / f"map_chunk_{args.chunk}_tags.tsv").write_text(
        header + "\n".join(map_rows) + "\n", encoding="utf-8")
    print("yazıldı.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

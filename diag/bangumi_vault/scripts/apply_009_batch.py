#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — 009 serisi batch karar uygulayıcısı.

Amaç: canon.tsv'deki MEVCUT satırların yerinde tr/en/origin alanlarını
güncellemek (yeni satır eklemez; cid silmez/değiştirmez; XML ID YASAK).

Girdi TSV (UTF-8, tab-ayrık, başlık satırı yok):
    rol <TAB> cid <TAB> eski_tr <TAB> yeni_tr <TAB> yeni_en <TAB> origin

Davranış:
  1. canon.tsv'de (role, cid) arar.
  2. Eski TR değeri eşleşmiyorsa İKİZ-ENGELLİ olarak raporlar (değişiklik
     başka bir batch'te uygulanmış ya da bir başkası tarafından sahip çıkılmış).
  3. Eşleşiyorsa tr = yeni_tr, en = yeni_en, origin = yeni_origin yazar.
  4. Aliases/sütun sayısını ORİJİNALDEKİ gibi korur (birebir yazar, rstrip YAPMAZ,
     boş son sekme varsa da korur).
  5. Aynı adlı rapor dosyasını yanına yazar (worklists/009_decisions_XXX_report.tsv).

--mirror-repair modu:
  Ayna satırları = başka roldeki canon satırının col-8 `string_key` alanı
  `bangumi_<rol>_<cid>` formunda olanlar; onların da tr/en'i eşitlenir.
  MASTER kilitli satırları atlar (tags.tsv ve orgs.tsv'den okur); `bangumi_org_*`
  anahtarlı cross-role referansları atlar (MASTER sahipli).
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
DEC = VAULT / "decisions"
CANON_PATH = DEC / "canon.tsv"
MASTER_TAGS = ROOT / "diag/bangumi_master/tags.tsv"
MASTER_ORGS = ROOT / "diag/bangumi_master/orgs.tsv"


def load_master_lock() -> set[tuple[str, str]]:
    """MASTER tarafından sahiplenilen (rol, cid) kümesi — TR/EN'leri kilitli."""
    locked: set[tuple[str, str]] = set()
    # tags.tsv 'new' olanlar VAULT'a aittir; 'existing:<key>' olanlar MASTER sahipli
    if MASTER_TAGS.exists():
        for raw in MASTER_TAGS.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.lstrip().startswith("#"):
                continue
            parts = raw.split("\t")
            if len(parts) < 4:
                continue
            key, tr, en, status = parts[0], parts[1], parts[2], parts[3]
            cid = key[len("bangumi_tag_"):] if key.startswith("bangumi_tag_") else key
            if status.startswith("existing:"):
                # master bu satırı başka bir mevcut gruba bağlar — kilitli
                locked.add(("tag", cid))
            elif status == "new":
                # 'new' master satırlarının TR/EN'i MASTER belirler; kilitli
                locked.add(("tag", cid))
    # orgs.tsv 'o(...)' başlıkları RAW döneminde boş — yine de 'bangumi_org_*'
    # col-8 referansları atlanacak (mirror-repair sırasında prefix ile denetlenir).
    return locked


def parse_canon_lines(text: str):
    """Canon'u (yorum/boş satırlar dahil) koruyarak dönüştürmek için çizgi çizgi oku."""
    lines = text.split("\n")
    # split trailing '\n' produces an empty trailing entry; preserve by tracking
    return lines


def serialize_rows(lines, rows_map, mirror_map, header_line_map):
    """lines dizisini satır satır dolaş, (rol,cid) → dict eşleşmelerine göre
    tr/en/origin alanlarını (3,4,5) güncelle. lines içindeki ilk eşleşme hedef
    canon satırı; yorum/boş satırlar dokunulmadan kalır."""
    updated = 0
    blocked = []
    for i, line in enumerate(lines):
        if not line or line.lstrip().startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 5:
            continue
        cid, role = parts[0], parts[1]
        key = (role, cid)
        target = rows_map.get(key)
        if target is None:
            continue
        # eski_tr eşleşme denetimi
        eski = parts[2]
        if eski != target["old_tr"] and not target.get("_force"):
            blocked.append((role, cid, eski, target["old_tr"], target["new_tr"]))
            continue
        # tr = yeni_tr, en = yeni_en, origin = yeni_origin (alan 3,4,5)
        parts[2] = target["new_tr"]
        parts[3] = target["new_en"]
        parts[4] = target["new_origin"]
        lines[i] = "\t".join(parts)
        updated += 1
        # mirror update: diğer rollerde col-8 bu (role,cid)'ye işaret eden satırlar
        if key in mirror_map:
            for mi in mirror_map[key]:
                if mi == i:
                    continue
                mparts = lines[mi].split("\t")
                if len(mparts) < 8:
                    continue
                mparts[2] = target["new_tr"]
                mparts[3] = target["new_en"]
                # origin mirror'da değişmez
                lines[mi] = "\t".join(mparts)
    return updated, blocked


def load_canon_mirror_index(lines):
    """Her satırı (rol,cid) → index olarak dizinle. Ayrıca col-8 `string_key`
    alanını okuyup hangi (role,cid) için ayna referans olduğunu bul
    (string_key='bangumi_<target_role>_<target_cid>')."""
    primary: dict[tuple[str, str], int] = {}
    mirror: dict[tuple[str, str], list[int]] = {}
    for i, line in enumerate(lines):
        if not line or line.lstrip().startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 5:
            continue
        cid, role = parts[0], parts[1]
        # primary index: ilk görülen (rol,cid) asıl satır
        if (role, cid) not in primary:
            primary[(role, cid)] = i
        # col-8 mirror
        if len(parts) >= 8 and parts[7].startswith("bangumi_"):
            sk = parts[7]
            # format: bangumi_<target_role>_<target_cid>
            rest = sk[len("bangumi_"):]
            if "_" in rest:
                tr_role, tr_cid = rest.split("_", 1)
                # cross-role org → org anahtarıysa atla (mirror-repair kilit)
                if tr_role != "org":
                    mirror.setdefault((tr_role, tr_cid), []).append(i)
    return primary, mirror


def apply(worklist: Path, mirror: bool, dry: bool = False):
    canon_text = CANON_PATH.read_text(encoding="utf-8")
    lines = parse_canon_lines(canon_text)
    primary_idx, mirror_idx = load_canon_mirror_index(lines)
    locked = load_master_lock()

    # worklist oku
    decisions = []
    with worklist.open(encoding="utf-8") as f:
        for raw in f:
            raw = raw.rstrip("\n")
            if not raw or raw.lstrip().startswith("#"):
                continue
            p = raw.split("\t")
            if len(p) != 6:
                print(f"  ⚠ {worklist.name}: 6 sütun beklenir → {raw[:80]}", file=sys.stderr)
                continue
            role, cid, old_tr, new_tr, new_en, new_origin = p
            if new_origin not in {"global", "new", "existing", "as_is"}:
                print(f"  ⚠ origin geçersiz: {new_origin} ({role},{cid})", file=sys.stderr)
                continue
            if (role, cid) in locked:
                print(f"  🔒 kilitli (MASTER) → atla: ({role},{cid})", file=sys.stderr)
                continue
            if (role, cid) not in primary_idx:
                print(f"  ❌ canon'da yok: ({role},{cid})", file=sys.stderr)
                continue
            decisions.append({
                "role": role, "cid": cid, "old_tr": old_tr,
                "new_tr": new_tr, "new_en": new_en, "new_origin": new_origin,
            })

    if not decisions:
        print("işlenecek karar yok.")
        return 0

    # rows_map: primary (role,cid) → decision
    rows_map: dict[tuple[str, str], dict] = {}
    for d in decisions:
        key = (d["role"], d["cid"])
        rows_map[key] = d

    # mirror map: (role,cid) → list of line indices whose col-8 points here
    effective_mirror = mirror_idx if mirror else {}

    updated, blocked = serialize_rows(lines, rows_map, effective_mirror, {})

    # rapor
    report_path = worklist.with_name(worklist.stem + "_report.tsv")
    with report_path.open("w", encoding="utf-8") as f:
        f.write("# rol\tcid\told_tr\tnew_tr\tnew_en\tnew_origin\tdurum\n")
        for d in decisions:
            key = (d["role"], d["cid"])
            status = "uygulandı"
            for b in blocked:
                if b[0] == d["role"] and b[1] == d["cid"]:
                    status = f"ikiz-engelli bulundu={b[2]!r}"
                    break
            f.write(f"{d['role']}\t{d['cid']}\t{d['old_tr']}\t{d['new_tr']}\t{d['new_en']}\t{d['new_origin']}\t{status}\n")

    print(f"uygulanan: {updated} · ikiz-engelli: {len(blocked)} · rapor: {report_path.name}")
    if blocked:
        for role, cid, cur, expected, wanted in blocked[:10]:
            print(f"  🔁 ikiz-engelli ({role},{cid}): mevcut={cur!r} beklenen={expected!r}")
        if len(blocked) > 10:
            print(f"  ... +{len(blocked) - 10}")

    if dry or not updated:
        return 0

    new_text = "\n".join(lines)
    if new_text == canon_text:
        print("değişiklik yok; yazılmadı.")
        return 0
    CANON_PATH.write_text(new_text, encoding="utf-8")
    print(f"canon.tsv güncellendi → {CANON_PATH}")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("worklist", nargs="?", help="worklists/009_decisions_XXX.tsv")
    ap.add_argument("--mirror-repair", action="store_true", help="aynı değerdeki col-8 ayna satırlarını da eşitle")
    ap.add_argument("--dry-run", action="store_true", help="canon.tsv'ye yazmadan raporla")
    args = ap.parse_args()
    if not args.worklist:
        ap.error("worklist gerekli")
    return apply(Path(args.worklist), mirror=args.mirror_repair, dry=args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())

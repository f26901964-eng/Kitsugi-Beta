#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HANDOFF_009 parti uygulayıcısı — `clean_cjk_canon_and_chunk008.py` kalıbı.

Girdi: worklists/009_decisions_XXX.tsv (başlık satırı `#` ile başlar)
    rol <TAB> cid <TAB> beklenen_eski_tr <TAB> yeni_tr <TAB> yeni_en <TAB> origin

Kurallar (devir metni §3, §4):
  * canon.tsv satırı YERİNDE güncellenir (cid silinmez/değiştirilmez, satır sayısı sabit).
  * origin alanı {global, new} — `as_is` yalnızca eski değer olarak doğrulanır.
  * İkiz değer koruması: (rol, tr.lower, en.lower) başka bir canon satırına veya
    strings.xml bangumi anahtarına aitse yazım ENGELLENİR (raporlanır).
  * MASTER kilit tuşları (diag/bangumi_master/tags.tsv + orgs.tsv) ASLA yazılmaz.
  * Beklenen eski TR, canon'daki satırın geçerli TR'siyle birebir eşleşmezse satır
    atlanır ve raporlanır (yanlış hedef yazımı önlenir).
  * 8. alan (cross-role string_key) taşıyan satırlar ancak açık ikiz yoksa yazılır;
    not alanına eski değer eklenir (izlenebilirlik).
"""
from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
DEC = VAULT / "decisions"
WORK = VAULT / "worklists"
MASTER_ORGS = ROOT / "diag/bangumi_master/orgs.tsv"
MASTER_TAGS = ROOT / "diag/bangumi_master/tags.tsv"

sys.path.insert(0, str(VAULT / "scripts"))
from android_strings import unescape  # noqa: E402
from resource_catalog import catalog_text, resource_paths  # noqa: E402

RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

VALID_ORIGINS = {"global", "new"}


def load_locked_master_keys() -> set[str]:
    locked: set[str] = set()
    for p in (MASTER_ORGS, MASTER_TAGS):
        for raw in p.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.lstrip().startswith("#"):
                continue
            locked.add(raw.split("\t")[0])
    return locked


def norm_val(text: str) -> str:
    return unicodedata.normalize("NFKC", text or "").strip().lower()


def main(decisions_path: Path) -> int:
    locked = load_locked_master_keys()

    # strings.xml (tüm kaynaklar: app + shard) rol -> {(trl,enl) -> {cidler}}
    tr_xml = {k: unescape(v) for k, v in re.findall(
        r'<string name="([^"]+)">(.*?)</string>', catalog_text(RES_TR))}
    en_xml = {k: unescape(v) for k, v in re.findall(
        r'<string name="([^"]+)">(.*?)</string>', catalog_text(RES_EN))}
    val_owner: dict[tuple[str, str, str], set[str]] = {}
    for k, tv in tr_xml.items():
        if not k.startswith("bangumi_"):
            continue
        parts = k.split("_", 2)
        if len(parts) < 3:
            continue
        role, cid = parts[1], parts[2]
        ev = en_xml.get(k, tv)
        val_owner.setdefault((role, norm_val(tv), norm_val(ev)), set()).add(cid)

    # canon.tsv satırlarını oku (yorumlar ve sıra korunur)
    canon_lines = (DEC / "canon.tsv").read_text(encoding="utf-8").splitlines()
    canon_by_rc: dict[tuple[str, str], list[str]] = {}
    for raw in canon_lines:
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        p = raw.split("\t")
        while len(p) < 8:
            p.append("")
        canon_by_rc[(p[1], p[0])] = p
    for (role, cid), p in canon_by_rc.items():
        val_owner.setdefault((role, norm_val(p[2]), norm_val(p[3])), set()).add(cid)

    # Kararları oku
    rows = [r for r in decisions_path.read_text(encoding="utf-8").splitlines()
            if r.strip() and not r.lstrip().startswith("#")]

    applied, blocked, stale, locked_rows, not_found = 0, [], [], [], []
    applied_set: set[tuple[str, str]] = set()
    for raw in rows:
        p = raw.split("\t")
        if len(p) != 6:
            raise SystemExit(f"bozuk karar satırı (6 alan beklenirdi): {raw!r}")
        role, cid, old_tr, ntr, nen, origin = [x.strip() for x in p]
        if origin not in VALID_ORIGINS:
            raise SystemExit(f"geçersiz origin {origin!r}: {raw!r}")
        cur = canon_by_rc.get((role, cid))
        if cur is None:
            not_found.append(f"{role}/{cid}: canon satırı yok")
            continue
        if f"bangumi_{role}_{cid}" in locked:
            locked_rows.append(f"{role}/{cid}: MASTER kilitli ({cur[2]})")
            continue
        if (role, cid) in applied_set:
            not_found.append(f"{role}/{cid}: aynı partide mükerrer karar")
            continue
        if cur[2] != old_tr:
            stale.append(f"{role}/{cid}: beklenen TR {old_tr!r} != canon {cur[2]!r}")
            continue
        owners = val_owner.get((role, norm_val(ntr), norm_val(nen)), set()) - {cid}
        if owners:
            blocked.append(f"{role}/{cid}: '{ntr}' değeri zaten {sorted(owners)} sahibinde (ikiz engellendi)")
            continue
        # uygula
        prev_tr, prev_origin = cur[2], cur[4]
        cur[2], cur[3], cur[4] = ntr, nen, origin
        tag = f"009:{origin} ({prev_origin}: {prev_tr})"
        cur[6] = f"{cur[6]} | {tag}" if cur[6].strip() else tag
        val_owner.setdefault((role, norm_val(ntr), norm_val(nen)), set()).add(cid)
        applied_set.add((role, cid))
        applied += 1

    # Günlük: aynı rolde TR sütununda hâlâ CJK + aynı hedefe yazılan çakışma kontrolü burada biter
    out_lines = []
    for raw in canon_lines:
        if not raw.strip() or raw.lstrip().startswith("#"):
            out_lines.append(raw)
            continue
        p = raw.split("\t")
        cur = canon_by_rc[(p[1], p[0])]
        out_lines.append("\t".join(cur))
    (DEC / "canon.tsv").write_text("\n".join(out_lines) + "\n", encoding="utf-8")

    report = WORK / f"{decisions_path.stem}_report.tsv"
    with report.open("w", encoding="utf-8") as f:
        f.write(f"# {decisions_path.name} — uygulama raporu\n")
        f.write(f"# uygulanan: {applied} · ikiz-engelli: {len(blocked)} · bayat-hedef: {len(stale)} "
                f"· kilitli: {len(locked_rows)} · bulunamadı/mükerrer: {len(not_found)}\n")
        for line in blocked + stale + locked_rows + not_found:
            f.write(line + "\n")
    print(f"uygulanan: {applied}")
    for label, bucket in (("ikiz-engelli", blocked), ("bayat-hedef", stale),
                          ("kilitli", locked_rows), ("bulunamadı/mükerrer", not_found)):
        if bucket:
            print(f"{label}: {len(bucket)}")
            for line in bucket[:12]:
                print(f"  - {line}")
    print(f"rapor: {report.name}")
    return 0


CROSS_KEY_RE = re.compile(r"^bangumi_([a-z]+)_(.+)$")


def mirror_repair() -> int:
    """8. alanı cross-role string_key olan canon satırlarının TR/EN aynalarını onarır.

    cross-role satır hedef (rol, cid) canon'u bulunur; hedefin TR/EN'i ile satırın
    TR/EN'i farklıysa satır hedefe senkronlanır (origin `existing` korunur, nota eklenir).
    MASTER kilitli satırlar ve hedefi canon'da olmayan satırlar atlanıp raporlanır.
    """
    locked = load_locked_master_keys()
    canon_lines = (DEC / "canon.tsv").read_text(encoding="utf-8").splitlines()
    canon_by_rc: dict[tuple[str, str], list[str]] = {}
    for raw in canon_lines:
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        p = raw.split("\t")
        while len(p) < 8:
            p.append("")
        canon_by_rc[(p[1], p[0])] = p

    synced, skipped, missing = 0, [], []
    for (role, cid), p in list(canon_by_rc.items()):
        fk = p[7].strip()
        if not fk:
            continue
        m = CROSS_KEY_RE.match(fk)
        if not m:
            continue
        trole, tcid = m.group(1), m.group(2)
        target = canon_by_rc.get((trole, tcid))
        if target is None:
            missing.append(f"{role}/{cid}: hedef {fk} canon'da yok")
            continue
        if f"bangumi_{role}_{cid}" in locked:
            skipped.append(f"{role}/{cid}: MASTER kilitli")
            continue
        if (p[2], p[3]) != (target[2], target[3]):
            p[2], p[3] = target[2], target[3]
            tag = f"009:mirror ({fk})"
            if tag not in p[6]:
                p[6] = f"{p[6]} | {tag}" if p[6].strip() else tag
            synced += 1

    out_lines = []
    for raw in canon_lines:
        if not raw.strip() or raw.lstrip().startswith("#"):
            out_lines.append(raw)
            continue
        p = raw.split("\t")
        cur = canon_by_rc[(p[1], p[0])]
        out_lines.append("\t".join(cur))
    (DEC / "canon.tsv").write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    print(f"cross-role ayna senkronu: {synced}")
    if skipped:
        print(f"kilitli (atlandı): {len(skipped)}")
    if missing:
        print(f"hedefi olmayan (atlandı, ilk 10):")
        for line in missing[:10]:
            print(f"  - {line}")
        print(f"  ... toplam {len(missing)}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--mirror-repair":
        raise SystemExit(mirror_repair())
    if len(sys.argv) != 2:
        raise SystemExit("kullanım: apply_009_batch.py <worklists/009_decisions_XXX.tsv> | --mirror-repair")
    raise SystemExit(main(VAULT / sys.argv[1]))

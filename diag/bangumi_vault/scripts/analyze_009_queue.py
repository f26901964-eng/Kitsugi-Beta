#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HANDOFF_009 kuyruk analizi: canon.tsv içindeki CJK (as_is) ve TR==EN (Latin) kayıtları
vault JSON count değerleriyle birleştirip role göre sıralı iş listesi üretir.
ÇIKTI: worklists/009_queue_<role>.tsv  ->  count <TAB> role <TAB> canon_id <TAB> tr <TAB> en <TAB> origin <TAB> max_vault_key
Yalnız rapor üretir; hiçbir karar/veri dosyasını değiştirmez.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
DEC = VAULT / "decisions"
OUT = VAULT / "worklists"
OUT.mkdir(exist_ok=True)

CJK_RE = re.compile(r"[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
ROLE_FILES = {
    "studio": "studios.json", "producer": "producers.json", "network": "networks.json",
    "publisher": "publishers.json", "magazine": "magazines.json", "committee": "committees.json",
    "tag": "tags.json",
}
ROLES = ["network", "magazine", "studio", "publisher", "producer", "tag"]


def has_cjk(text: str) -> bool:
    return bool(CJK_RE.search(text or ""))


def main() -> int:
    # 1) vault kayıtlarından (role, canon) -> (max_count, ornek_key) indeksi
    idx: dict[tuple[str, str], list] = {}
    for role, fn in ROLE_FILES.items():
        with (VAULT / fn).open(encoding="utf-8") as f:
            for rec in json.load(f):
                rrole = rec.get("role") or role
                canon = rec.get("canon")
                if not canon:
                    continue
                cnt = rec.get("count") or 0
                k = (rrole, canon)
                if k not in idx or cnt > idx[k][0]:
                    idx[k] = [cnt, rec.get("key", "")]

    # 2) canon.tsv taraması
    stats = {r: {"cjk": 0, "treq": 0, "ok": 0} for r in ROLES}
    queues: dict[str, list[tuple]] = {r: [] for r in ROLES}
    for raw in (DEC / "canon.tsv").read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        p = raw.split("\t")
        while len(p) < 8:
            p.append("")
        cid, role, tr, en, origin = p[0], p[1], p[2], p[3], p[4]
        if role not in stats:
            continue
        cjk = has_cjk(tr) or has_cjk(en)
        if cjk:
            stats[role]["cjk"] += 1
            cnt, key = idx.get((role, cid), [0, ""])
            queues[role].append((cnt, role, cid, tr, en, origin, key, "CJK"))
        elif tr.strip() == en.strip():
            stats[role]["treq"] += 1
            cnt, key = idx.get((role, cid), [0, ""])
            queues[role].append((cnt, role, cid, tr, en, origin, key, "TR==EN"))
        else:
            stats[role]["ok"] += 1

    print(f"{'rol':<10} {'CJK':>7} {'TR==EN':>7} {'tamam':>7}")
    total = [0, 0]
    for r in ROLES:
        s = stats[r]
        print(f"{r:<10} {s['cjk']:>7} {s['treq']:>7} {s['ok']:>7}")
        total[0] += s["cjk"]
        total[1] += s["treq"]
    print(f"{'TOPLAM':<10} {total[0]:>7} {total[1]:>7}")

    for r in ROLES:
        queues[r].sort(key=lambda t: (-t[0], t[2]))
        path = OUT / f"009_queue_{r}.tsv"
        with path.open("w", encoding="utf-8") as f:
            f.write("# count\trole\tcanon_id\ttr\ten\torigin\tornek_key\ttip\n")
            for t in queues[r]:
                f.write("\t".join([str(t[0]), t[1], t[2], t[3], t[4], t[5], t[6], t[7]]) + "\n")
        print(f"yazıldı: {path.name} ({len(queues[r])} satır)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

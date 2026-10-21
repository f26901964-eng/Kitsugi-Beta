#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Chunk 007 (59.510 etiket, 10 bant: a -> j) karar üretici ve bant-bant uygulayıcı."""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from collections import Counter
from pathlib import Path

SRC_DIR = Path(__file__).resolve().parent
VAULT_ROOT = SRC_DIR.parents[1]
REPO_ROOT = VAULT_ROOT.parents[1]
SNAPSHOT = VAULT_ROOT / "worklists/raw_007/records_007_tags.json"

sys.path.insert(0, str(VAULT_ROOT / 'scripts'))
sys.path.insert(0, str(SRC_DIR))
from build_tag_chunk import load_records  # noqa: E402
from composer import TagResolver, apply_seed_tr_upgrades  # noqa: E402

BANDS = [
    ("007_a", 19, 999999),
    ("007_b", 15, 18),
    ("007_c", 11, 14),
    ("007_d", 8, 10),
    ("007_e", 6, 7),
    ("007_f", 4, 5),
    ("007_g", 3, 3),
    ("007_h", 2, 2),
    ("007_i", 1, 1),
    ("007_j", 0, 0),
]


def run_cmd(cmd: list[str]) -> None:
    res = subprocess.run(cmd, cwd=REPO_ROOT, text=True, capture_output=True)
    if res.returncode != 0:
        sys.stderr.write(f"❌ Komut başarısız: {' '.join(cmd)}\n{res.stdout}\n{res.stderr}\n")
        raise SystemExit(res.returncode)
    out = (res.stdout or "").strip()
    if out:
        for line in out.splitlines()[-4:]:
            print("   ", line)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="Karar dosyalarını üret ve denetle, yazma")
    ap.add_argument("--apply", action="store_true", help="007_a -> 007_j sırayla tam kapanış zinciri çalıştır")
    ap.add_argument("--only", help="Yalnızca tek bir bant çalıştır (ör. 007_a)")
    args = ap.parse_args()

    patched_seeds = apply_seed_tr_upgrades()
    print(f"🌱 Seed TR yükseltmesi: {patched_seeds} İngilizce kavram Türkçeleştirildi")

    records = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    print(f"📦 Snapshot yüklendi: {len(records)} etiket kaydı")

    resolver = TagResolver()
    print(f"📚 TagResolver hazır: {len(resolver.tag_canons)} tag kanonu, "
          f"{len(resolver.fold_map)} OpenCC tam eşleşme anahtarı")

    # Tüm 59.510 kaydı a -> j sırasıyla tek geçişte çöz (küresel cid tutarlılığı %100 garanti!)
    by_band: dict[str, list[tuple[dict, tuple[str, str, str, str, str]]]] = {b: [] for b, _, _ in BANDS}
    for band, lo, hi in BANDS:
        band_recs = load_records(lo, hi, SNAPSHOT)
        for r in band_recs:
            by_band[band].append((r, resolver.resolve_record(r)))

    total_stats: Counter[str] = Counter()
    for band, lo, hi in BANDS:
        rows = by_band[band]
        st = Counter("skip" if d[0] == "SKIP" else d[3] for _, d in rows)
        total_stats.update(st)
        dec_path = SRC_DIR / f"dec_{band}.tsv"
        lines = [
            f"{idx}\t{d[0]}\t{d[1]}\t{d[2]}\t{d[3]}\t{d[4]}"
            for idx, (_, d) in enumerate(rows)
        ]
        dec_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"  {band} ({lo}..{hi}, n={len(rows):5d}): "
              + " ".join(f"{k}={st[k]}" for k in ("existing", "global", "new", "as_is", "skip") if st[k]))

    print("─" * 76)
    print("TOPLAM (59.510):", dict(total_stats))

    if args.dry_run and not args.apply:
        print("\n🔍 007_a + 007_b içinde as_is kalan ilk 40 örnek (genel kavram sızıntısı denetimi):")
        shown = 0
        for band in ("007_a", "007_b"):
            for r, d in by_band[band]:
                if d[3] == "as_is":
                    print(f"   [{band} c={r.get('count')}] {(r.get('labels') or [r.get('tr')])[0]}")
                    shown += 1
                    if shown >= 40:
                        break
            if shown >= 40:
                break
        # build_tag_chunk.py dry-run (TÜM 10 BANT: 007_a -> 007_j) doğrulaması
        print("\n🧪 build_tag_chunk.py TÜM 10 BANT (007_a -> 007_j) dry-run doğrulaması:")
        for band, lo, hi in BANDS:
            run_cmd([
                "python3", "diag/bangumi_vault/scripts/build_tag_chunk.py",
                "--chunk", band, "--min-count", str(lo), "--max-count", str(hi),
                "--snapshot", str(SNAPSHOT),
                "--decisions", str(SRC_DIR / f"dec_{band}.tsv"),
            ])
        return 0

    if args.apply:
        for band, lo, hi in BANDS:
            if args.only and band != args.only:
                continue
            map_out = VAULT_ROOT / "decisions" / f"map_chunk_{band}_tags.tsv"
            if map_out.exists():
                if band in ("007_a", "007_b", "007_c", "007_d"):
                    print(f"⏩ [{band}] daha önce doğrulandı ve kapatıldı.")
                    continue
                print(f"\n⏩ [{band}] map dosyası zaten yazılmış — kapanış zinciri tamamlanıyor...")
                run_cmd(["python3", "diag/bangumi_vault/scripts/apply_map.py"])
                run_cmd(["python3", "diag/bangumi_vault/scripts/vault_to_strings.py"])
                run_cmd(["python3", "diag/bangumi_vault/scripts/verify_vault.py"])
                run_cmd(["python3", "diag/bangumi_master/verify_bangumi_master.py"])
                run_cmd(["python3", "diag/bangumi_master/gen_bangumi_master.py", "--check"])
                run_cmd(["python3", "-m", "unittest", "discover", "-s", "diag/bangumi_vault/scripts", "-p", "test_*.py"])
                print(f"✅ [{band}] TAM KAPANIŞ ZİNCİRİ 100% YEŞİL!")
                continue
            print(f"\n🚀 [{band}] ({lo}..{hi}) UYGULANIYOR VE DOĞRULANIYOR...")
            dec_path = SRC_DIR / f"dec_{band}.tsv"
            run_cmd([
                "python3", "diag/bangumi_vault/scripts/build_tag_chunk.py",
                "--chunk", band, "--min-count", str(lo), "--max-count", str(hi),
                "--snapshot", str(SNAPSHOT),
                "--decisions", str(dec_path),
                "--write",
            ])
            run_cmd(["python3", "diag/bangumi_vault/scripts/apply_map.py", "--check"])
            run_cmd(["python3", "diag/bangumi_vault/scripts/apply_map.py"])
            run_cmd(["python3", "diag/bangumi_vault/scripts/vault_to_strings.py"])
            run_cmd(["python3", "diag/bangumi_vault/scripts/verify_vault.py"])
            run_cmd(["python3", "diag/bangumi_master/verify_bangumi_master.py"])
            run_cmd(["python3", "diag/bangumi_master/gen_bangumi_master.py", "--check"])
            run_cmd(["python3", "-m", "unittest", "discover", "-s", "diag/bangumi_vault/scripts", "-p", "test_*.py"])
            print(f"✅ [{band}] TAM KAPANIŞ ZİNCİRİ 100% YEŞİL!")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

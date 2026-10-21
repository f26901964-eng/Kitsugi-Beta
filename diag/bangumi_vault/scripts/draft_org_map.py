#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — org chunk taslak üreticisi (kural tabanlı, insan kararı yerine GEÇMEZ).

`decisions/org_global_names.tsv` bilgi tabanı + mekanik kurallarla
`decisions/map_chunk_<ID>_orgs.tsv` taslağı ve eksik `canon.tsv` satırlarını üretir.

Kurallar (sırayla):
  1. SKIP listesindeki adlar (ad parçası/gürültü) → SKIP.
  2. Bilgi tabanında ad geçiyorsa → latin_ad + canon_id (global karşılık).
  3. Ad Latin ise → as_is (tr=en=ad), canon_id = kayıt anahtarı soneki.
  4. Aksi hâlde (bilinmeyen CJK) → as_is (tr=en=ham ad), canon_id = anahtar soneki
     (uydurma romanizasyon YOK).
  5. canon_id + rol bir MEVCUT string anahtarına karşılık geliyorsa → existing
     (tr/en dil dosyasından alınır, mevcut değer korunur).

Çıktı yalnızca TASLAKTIR; `gen_org_chunk.py` sonrası elle gözden geçirilip
`apply_map.py` ile uygulanır.

Kullanım: python3 diag/bangumi_vault/scripts/draft_org_map.py --chunk 001 --min-count 100
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
DEC = VAULT / "decisions"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

ORG_FILES = {
    "studio": "studios.json",
    "producer": "producers.json",
    "network": "networks.json",
    "publisher": "publishers.json",
    "magazine": "magazines.json",
}

# Ad parçası / gürültü — asla string'e girmez
SKIP_NAMES = {
    "エンタテインメント", "エンターテイメント", "エンターテイメントジャパン", "ジャパン",
    "マガジン", "メーカー", "プレス", "インディーズ", "インディーズレーベル",
    "インディペンデントレーベル", "パブリッシャー", "ホーム", "ビデオ", "マーケティング",
    "Inc", "GOLD", "ワーナー", "44℃梅毒", "ピクチャーズ株式会社(Music)", "ユニバーサル",
    # CHUNK 004a (2026-10-21): ad parçası / bölge / belirsiz tek kana — dil dosyasına girmez.
    # Not: bunlar kurum adının kendisi DEĞİL, adın bir parçası (ör. "XX エンターテイメント").
    "INC.", "Inc.", "出版", "コミックス", "ピクチャーズ", "スタジオ", "グループ", "エンターテイメント",
    "ミュージックエンタテインメント", "ソフトウェア", "マガジンズ",
    "エー", "ジェイ", "ジェーン", "メリー", "ケイ", "エス", "エム", "コウ",
    "スター", "週刊", "中国大陆", "BE", "LOVE",
    # CHUNK 004b (2026-10-21): ad parçası / tek kana / bozuk parça — dil dosyasına girmez.
    "株式会社", "ソフト", "放送", "放送網", "ビジュアル", "マンガ", "日本", "メディア",
    "メディアワ)", "オー", "エル", "ティー", "エヌ", "ケー", "ファースト",
    # CHUNK 004c (2026-10-21): bölge/şablon gürültüsü, URL parçası, hukuki kısaltma,
    # belirsiz kısaltma ve tek kana — dil dosyasına girmez (ekranda ham kalır).
    "台湾", "http:", "USA", "LLC", "PC", "SE", "ESP", "エフ",
    "発売元:モモグレ", "(オトメドラマ)", "インディーズ・メーカー",
    # CHUNK 005a (2026-10-21): bölge etiketi / jenerik kelime / kesik ad — dil dosyasına girmez.
    "台灣",
    # CHUNK 005a (2026-10-21): bölge etiketi / jenerik kelime / kesik ad — dil dosyasına girmez.
    "上海", "北京", "北美", "韩国", "(日本)", "中心", "最初版", "新装版", "増刊", "半月刊", "システム", "パブリッシャーズ", "アニメーションスタジオ", "エンタープライズ", "グローバル", "文化传播有限公司", "制作统括：", "发行", "企画协力：", "；宣传制片人：", "；製作協力：", ")；制作制片人：", "発売元:ひつじぐも", "(羽根ロム+α)", "△○□×", "【チーム", "10)", "電撃「マ 王", "→", ")→", "&フラワー", "(パルシィ)", "ヤング", "コミック", "オンライン", "レコード", "テレビ",
    # CHUNK 005b (2026-10-21): kesik ad / platform / zaman dilimi — dil dosyasına girmez.
    "角川書店(角川グループパブリッシング) (角川コミックス", "(PS2", "火曜21時枠", "冠名", "PS2", "土曜ドラマ24", "×DMM", "https:", "有我奉仕", "WAVE", "(ガトー)", "湖南美术出版社 天闻角川策划大陆新版", "游戏天堂", "カンパニー", "(PC)", "Ltd", "ミュージック", "テレビ東京系6局", "Ltd.", "ちょこくらげ 販売:密林社", "文传", "C79", "テレビジョン", "明日工作室 台湾原版", "スターツ出版; 新装版", "22)", "6)", "LTD.", "(株式会社ディアリード)", "制片：", "游戏天堂 中国大陆 ；", "；制作制片人：", "モンスターコミックス(", "蓝色", "制作概括：", "Global", "#627-", "ANIM", "#1-#332", "(1)", "PROJECT", "制作统括：本木夏", "科技有限公司", "「進撃の巨人」製作委員会(", "モンスターコミックス(がうがうモンスター)", "iOS", "文化投资有限公司", "土ドラ", "テレビ東京系6局ネット", "インターナショナル", "(ガンマ)", "国服", "プロジェクト", "コーポレーション", "(欧洲", "コンピュータエンタテインメント", "スーパープレミアム", "4)", "20)", "ゲームス", "アニメーション", "21)", "5150", "(PS2)", "8)", "(5", "LTD", "+音", "増刊号", "；制作统括：", "(简体中文版)", "エンタテイメント",
}

LATIN = re.compile(r"[A-Za-z]")


def slug(value: str) -> str:
    v = unicodedata.normalize("NFKC", value).lower()
    v = re.sub(r"[^a-z0-9]+", "_", v).strip("_")
    return v


def load_knowledge() -> dict[str, tuple[str, str]]:
    out: dict[str, tuple[str, str]] = {}
    for raw in (DEC / "org_global_names.tsv").read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        name, latin = parts[0], parts[1]
        cid = parts[2] if len(parts) > 2 and parts[2] else slug(latin)
        if name in out and out[name] != (latin, cid):
            print(f"uyarı: bilgi tabanı çelişkisi {name!r}: {out[name]} vs {(latin, cid)}",
                  file=sys.stderr)
        out[name] = (latin, cid)
    return out


def load_strings_orgs() -> dict[str, tuple[str, str]]:
    tr = dict(re.findall(r'<string name="(bangumi_(?:studio|producer|network|publisher|magazine)_[^"]+)">(.*?)</string>',
                         RES_TR.read_text(encoding="utf-8")))
    en = dict(re.findall(r'<string name="(bangumi_(?:studio|producer|network|publisher|magazine)_[^"]+)">(.*?)</string>',
                         RES_EN.read_text(encoding="utf-8")))
    def unesc(v: str) -> str:
        return v.replace("\\'", "'").replace('\\"', '"').replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    return {k: (unesc(v), unesc(en.get(k, v))) for k, v in tr.items()}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--chunk", required=True)
    ap.add_argument("--min-count", type=int, default=100)
    # DÜZELTME (2026-10-21): chunk 004+ kurum bantlarını (ör. 60-99) bölerek işlemek için
    # üst sınır eklendi. Varsayılan None = eski davranış (üst sınır yok).
    ap.add_argument("--max-count", type=int, default=None)
    args = ap.parse_args()

    know = load_knowledge()
    strs = load_strings_orgs()
    map_rows: list[tuple[str, str, str]] = []
    canon_rows: dict[tuple[str, str], tuple[str, str, str, str]] = {}  # (role,cid) → (tr,en,origin,note)

    for role, fn in ORG_FILES.items():
        with (VAULT / fn).open(encoding="utf-8") as f:
            records = json.load(f)
        for rec in sorted(records, key=lambda r: (-(r.get("count") or 0), r["key"])):
            count = rec.get("count") or 0
            if rec.get("decision") or count < args.min_count:
                continue
            if args.max_count is not None and count > args.max_count:
                continue
            name = rec.get("tr") or ""
            if name in SKIP_NAMES:
                map_rows.append((rec["key"], "SKIP", f"ad parçası/gürültü: {name}"))
                continue

            latin = note = None
            origin = None
            cid = None
            hit = know.get(name) or next(
                ((know[l]) for l in rec.get("labels", []) if l in know), None)
            if hit:
                latin, cid = hit
                origin = "global"
                note = f"global karşılık: {name} → {latin}"
            else:
                key_suffix = rec["key"].split("_", 2)[2] if rec["key"].startswith("bangumi_") else rec["key"]
                cid = key_suffix
                if LATIN.search(name):
                    latin = name
                    note = "Latin ad — as_is"
                else:
                    latin = name
                    note = "bilinmeyen CJK — as_is (uydurma romanizasyon yok)"
                origin = "as_is"

            sk = f"bangumi_{role}_{cid}"
            string_key = ""
            if sk in strs:
                origin = "existing"
                note = f"mevcut anahtar: {sk}"
                tr_en = strs[sk]
            else:
                # roller arası mevcut anahtar var mı? (ör. producer東映 → bangumi_studio_toei_animation)
                sk_alt = next((f"bangumi_{r2}_{cid}" for r2 in ORG_FILES
                               if r2 != role and f"bangumi_{r2}_{cid}" in strs), None)
                if sk_alt:
                    origin = "existing"
                    string_key = sk_alt
                    note = f"mevcut anahtara birleşim: {sk_alt}"
                    tr_en = strs[sk_alt]
                else:
                    tr_en = (latin, latin)

            map_rows.append((rec["key"], cid, note))
            ck = (role, cid)
            if ck in canon_rows and canon_rows[ck][:2] != tr_en:
                print(f"uyarı: canon çelişkisi {ck}: {canon_rows[ck][:2]} vs {tr_en} ({rec['key']})",
                      file=sys.stderr)
            canon_rows.setdefault(ck, (tr_en[0], tr_en[1], origin, note, string_key))

    out = DEC / f"map_chunk_{args.chunk}_orgs.tsv"
    band = "count>=%d" % args.min_count if args.max_count is None else \
        "count %d-%d" % (args.min_count, args.max_count)
    lines = ["# Bangumi VAULT — chunk %s · ORG haritası (%s · %d kayıt) — TASLAK (draft_org_map.py)"
             % (args.chunk, band, len(map_rows)),
             "# Biçim: vault_key <TAB> canon_id|SKIP <TAB> not"]
    lines += [f"{k}\t{c}\t{n}" for k, c, n in map_rows]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{out.name}: {len(map_rows)} satır")

    # canon.tsv'e eksik satırları ekle (var olanlara dokunma)
    existing_text = (DEC / "canon.tsv").read_text(encoding="utf-8")
    have = set()
    for raw in existing_text.splitlines():
        if raw.strip() and not raw.lstrip().startswith("#"):
            p = raw.split("\t")
            have.add((p[1], p[0]))
    add = []
    for (role, cid), (tr, en, origin, note, string_key) in sorted(canon_rows.items()):
        if (role, cid) not in have:
            row = f"{cid}\t{role}\t{tr}\t{en}\t{origin}\t\t{note}"
            if string_key:
                row += f"\t{string_key}"
            add.append(row)
    if add:
        block = "\n# ── ORG kanonları (chunk %s · draft_org_map.py) ──────────────────────────\n" % args.chunk
        with (DEC / "canon.tsv").open("a", encoding="utf-8") as f:
            f.write(block + "\n".join(add) + "\n")
    print(f"canon.tsv: +{len(add)} satır")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

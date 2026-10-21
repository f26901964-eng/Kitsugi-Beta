#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT doğrulayıcı (S3 veri deposu + dil kaynakları).

Kontroller:
  1. values/strings.xml ↔ values-en/strings.xml `bangumi_*` anahtar simetrisi (BİREBİR).
  2. vault → strings + tags.tsv + orgs.tsv değer eşitliği (mevcut kaynaklar ve TSV depoları birebir).
  3. canon.tsv ↔ map_chunk_*.tsv bütünlüğü (bilinmeyen canon_id / vault key yok).
  4. string_key başına tekil tr/en (birleşen kayıtlar çelişmemeli).
  5. translations_pending.tsv içinde kararlı key bulunmaması.
  6. tags.tsv + orgs.tsv doğrulaması ve gölgeleme denetimi (aynı etiket iki ayrı anahtara gitmemeli).
  7. TERS YÖN: her map_chunk_*.tsv satırının kararı vault JSON'larına damgalanmış mı.

Çıkış: 0 = temiz, 1 = hata.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from android_strings import unescape
import build_org_store
import build_tag_store
from resource_catalog import catalog_text

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
TAG_STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"
ORG_STORE = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

ROLE_FILES = {
    "studio": "studios.json", "producer": "producers.json", "network": "networks.json",
    "publisher": "publishers.json", "magazine": "magazines.json", "committee": "committees.json",
    "tag": "tags.json",
}
KEEP = {"global", "translated", "as_is", "existing"}
errors: list[str] = []

tr_src = catalog_text(RES_TR)
en_src = catalog_text(RES_EN)

# 1) simetri
tr_keys = set(re.findall(r'<string name="(bangumi_[^"]+)"', tr_src))
en_keys = set(re.findall(r'<string name="(bangumi_[^"]+)"', en_src))
only_tr = sorted(tr_keys - en_keys)
only_en = sorted(en_keys - tr_keys)
if only_tr:
    errors.append(f"yalnız TR'de {len(only_tr)} anahtar: {only_tr[:8]}")
if only_en:
    errors.append(f"yalnız EN'de {len(only_en)} anahtar: {only_en[:8]}")

tr_vals = dict(re.findall(r'<string name="(bangumi_[^"]+)">(.*?)</string>', tr_src, re.S))
en_vals = dict(re.findall(r'<string name="(bangumi_[^"]+)">(.*?)</string>', en_src, re.S))

# Veri depolarını yükle ve doğrula
tag_rows = build_tag_store.parse_store(TAG_STORE.read_text(encoding="utf-8"))
errors.extend(f"tags.tsv: {e}" for e in build_tag_store.validate(tag_rows))
tag_groups = {row[0]: (row[1], row[2]) for row in tag_rows if len(row) >= 3}

org_rows = build_org_store.parse_store(ORG_STORE.read_text(encoding="utf-8"))
errors.extend(f"orgs.tsv: {e}" for e in build_org_store.validate(org_rows))
org_by_key: dict[str, tuple[str, str]] = {}
for row in org_rows:
    if len(row) >= 3:
        tr_cell = row[3] if len(row) >= 4 else row[2]
        org_by_key.setdefault(row[1], (tr_cell, row[2]))

# 2-4) vault → strings + veri depoları
string_tr: dict[str, str] = {}
string_en: dict[str, str] = {}
tag_values: dict[str, tuple[str, str]] = {}
org_keys_seen: set[str] = set()
for role, fn in ROLE_FILES.items():
    with (VAULT / fn).open(encoding="utf-8") as f:
        data = json.load(f)
    for rec in data:
        dec = rec.get("decision")
        sk = rec.get("string_key")
        if dec not in KEEP or not sk:
            continue
        t, e = rec.get("tr") or "", rec.get("en") or ""
        rec_role = rec.get("role", role)
        if rec_role == "tag" and rec.get("canon"):
            cid = rec["canon"]
            if cid in tag_values and tag_values[cid] != (t, e):
                errors.append(f"{cid}: tag canon içinde çelişkili tr/en ({rec['key']})")
            tag_values[cid] = (t, e)
        elif rec_role != "tag":
            org_keys_seen.add(sk)
        if sk in string_tr and (string_tr[sk], string_en[sk]) != (t, e):
            errors.append(f"{sk}: vault içinde çelişkili tr/en ({rec['key']})")
        string_tr[sk], string_en[sk] = t, e
        if sk in tr_vals and unescape(tr_vals[sk]) != t:
            errors.append(f"{sk}: strings.xml TR değeri vault ile eşleşmiyor "
                          f"({'xml=' + tr_vals[sk]!r} vs {'vault=' + t!r})")
        if sk in en_vals and unescape(en_vals[sk]) != e:
            errors.append(f"{sk}: strings.xml EN değeri vault ile eşleşmiyor "
                          f"({'xml=' + en_vals[sk]!r} vs {'vault=' + e!r})")
        if rec_role != "tag" and sk not in tr_vals and sk not in org_by_key:
            errors.append(f"{sk}: kararlı kurum ne strings.xml'de ne orgs.tsv'de var (vault_to_strings çalıştırılmalı)")

for cid, (t, e) in tag_values.items():
    tag_key = f"bangumi_tag_{cid}"
    if cid not in tag_groups:
        errors.append(f"{cid}: kararlı etiketin tags.tsv grubu yok (vault_to_strings çalıştırılmalı)")
        continue
    if tag_key in tr_vals or tag_key in en_vals:
        if unescape(tr_vals.get(tag_key, "")) != t:
            errors.append(f"{tag_key}: TR kaynak değeri tag canon ile eşleşmiyor")
        if unescape(en_vals.get(tag_key, "")) != e:
            errors.append(f"{tag_key}: EN kaynak değeri tag canon ile eşleşmiyor")
    else:
        if tag_groups[cid] != (t, e):
            errors.append(f"{cid}: tags.tsv (TR, EN)={tag_groups[cid]!r} != vault {(t, e)!r}")

for sk in org_keys_seen:
    if sk in org_by_key and org_by_key[sk] != (string_tr[sk], string_en[sk]):
        errors.append(f"{sk}: orgs.tsv (TR, EN)={org_by_key[sk]!r} != vault {(string_tr[sk], string_en[sk])!r}")

# 3) canon ↔ map
canon_pairs: set[tuple[str, str]] = set()
for raw in (VAULT / "decisions" / "canon.tsv").read_text(encoding="utf-8").splitlines():
    if raw.strip() and not raw.lstrip().startswith("#"):
        parts = raw.split("\t")
        canon_pairs.add((parts[1], parts[0]))  # (rol, canon_id)

vault_rec: dict[str, str] = {}
for role, fn in ROLE_FILES.items():
    with (VAULT / fn).open(encoding="utf-8") as f:
        for r in json.load(f):
            vault_rec[r["key"]] = r.get("role", role)

map_keys: set[str] = set()
for path in sorted((VAULT / "decisions").glob("map_chunk_*.tsv")):
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if parts[0] in map_keys:
            errors.append(f"{path.name}: yinelenen map anahtarı {parts[0]}")
        map_keys.add(parts[0])
        if parts[0] not in vault_rec:
            errors.append(f"{path.name}: vault'ta olmayan key {parts[0]}")
            continue
        if len(parts) > 1 and parts[1] != "SKIP" and (vault_rec[parts[0]], parts[1]) not in canon_pairs:
            errors.append(f"{path.name}: canon.tsv'te olmayan (rol, canon_id) "
                          f"({vault_rec[parts[0]]}, {parts[1]}) ({parts[0]})")

# 5) pending ↔ done
pending = VAULT / "translations_pending.tsv"
if pending.exists():
    pend_keys = {l.split("\t", 1)[0] for l in pending.read_text(encoding="utf-8").splitlines()[1:] if l}
    overlap = sorted(map_keys & pend_keys)
    if overlap:
        errors.append(f"translations_pending içinde kararlı key: {overlap[:8]}")

# 7) TERS YÖN: map_chunk_*.tsv kararları vault JSON'larına damgalanmış mı?
stamp_idx: dict[str, list[tuple[str, dict]]] = {}
for role, fn in ROLE_FILES.items():
    with (VAULT / fn).open(encoding="utf-8") as f:
        for r in json.load(f):
            stamp_idx.setdefault(r["key"], []).append((fn, r))
_archive = VAULT / "vault.json"
if _archive.exists():
    with _archive.open(encoding="utf-8") as f:
        _arch = json.load(f)
    for _bucket in ("orgs", "tags"):
        for r in _arch.get(_bucket, []):
            stamp_idx.setdefault(r["key"], []).append(("vault.json", r))

stale: list[str] = []
for path in sorted((VAULT / "decisions").glob("map_chunk_*.tsv")):
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        key, cid = parts[0], parts[1]
        for src, rec in stamp_idx.get(key, []):
            if cid == "SKIP":
                if rec.get("decision") != "skip":
                    stale.append(f"{key} ({src}): SKIP beklenir, decision={rec.get('decision')!r}")
            elif rec.get("canon") != cid:
                stale.append(f"{key} ({src}): canon={rec.get('canon')!r} beklenen {cid!r}")
if stale:
    errors.append(f"vault JSON'ları kararlardan GERİDE: {len(stale)} damga uyuşmazlığı "
                  f"→ apply_map.py çalıştırılmalı (ör. {stale[0]})")

if errors:
    print(f"❌ {len(errors)} hata:")
    for e in errors[:40]:
        print("  - " + e)
    if len(errors) > 40:
        print(f"  ... +{len(errors) - 40}")
    sys.exit(1)
print(f"✅ TÜM KONTROLLER GEÇTİ — {len(tr_keys)} bangumi_* anahtar, "
      f"{len(string_tr)} vault kaydı, {len(map_keys)} map satırı, "
      f"{len(stamp_idx)} damga denetlendi")

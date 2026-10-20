#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT doğrulayıcı.

Kontroller:
  1. values/strings.xml ↔ values-en/strings.xml `bangumi_*` anahtar simetrisi (BİREBİR).
  2. vault → strings değer eşitliği (kararlı kayıtların tr/en'i dil dosyasında birebir).
  3. canon.tsv ↔ map_chunk_*.tsv bütünlüğü (bilinmeyen canon_id / vault key yok).
  4. string_key başına tekil tr/en (birleşen kayıtlar çelişmemeli).
  5. translations_pending.tsv içinde kararlı key bulunmaması.
  6. sözlük RAW satırlarında gölgeleme (aynı etiket iki ayrı anahtara gitmemeli).

Çıkış: 0 = temiz, 1 = hata.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
UTILS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"
ORG_DICT = UTILS / "BangumiOrgDictionary.kt"

ROLE_FILES = {
    "studio": "studios.json", "producer": "producers.json", "network": "networks.json",
    "publisher": "publishers.json", "magazine": "magazines.json", "committee": "committees.json",
    "tag": "tags.json",
}
KEEP = {"global", "translated", "as_is", "existing"}
errors: list[str] = []

tr_src = RES_TR.read_text(encoding="utf-8")
en_src = RES_EN.read_text(encoding="utf-8")

# 1) simetri
tr_keys = set(re.findall(r'<string name="(bangumi_[^"]+)"', tr_src))
en_keys = set(re.findall(r'<string name="(bangumi_[^"]+)"', en_src))
only_tr = sorted(tr_keys - en_keys)
only_en = sorted(en_keys - tr_keys)
if only_tr:
    errors.append(f"yalnız TR'de {len(only_tr)} anahtar: {only_tr[:8]}")
if only_en:
    errors.append(f"yalnız EN'de {len(only_en)} anahtar: {only_en[:8]}")

tr_vals = dict(re.findall(r'<string name="(bangumi_[^"]+)">(.*?)</string>', tr_src))
en_vals = dict(re.findall(r'<string name="(bangumi_[^"]+)">(.*?)</string>', en_src))


def unescape(v: str) -> str:
    return (v.replace("\\'", "'").replace('\\"', '"')
             .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"))


# 2-4) vault → strings
string_tr: dict[str, str] = {}
string_en: dict[str, str] = {}
for role, fn in ROLE_FILES.items():
    with (VAULT / fn).open(encoding="utf-8") as f:
        data = json.load(f)
    for rec in data:
        dec = rec.get("decision")
        sk = rec.get("string_key")
        if dec not in KEEP or not sk:
            continue
        t, e = rec.get("tr") or "", rec.get("en") or ""
        if sk in string_tr and (string_tr[sk], string_en[sk]) != (t, e):
            errors.append(f"{sk}: vault içinde çelişkili tr/en ({rec['key']})")
        string_tr[sk], string_en[sk] = t, e
        if sk in tr_vals and unescape(tr_vals[sk]) != t:
            errors.append(f"{sk}: strings.xml TR değeri vault ile eşleşmiyor "
                          f"({'xml=' + tr_vals[sk]!r} vs {'vault=' + t!r})")
        if sk in en_vals and unescape(en_vals[sk]) != e:
            errors.append(f"{sk}: strings.xml EN değeri vault ile eşleşmiyor "
                          f"({'xml=' + en_vals[sk]!r} vs {'vault=' + e!r})")
        if sk not in tr_vals:
            errors.append(f"{sk}: vault kararlı ama strings.xml'te yok (vault_to_strings çalıştırılmalı)")

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

# 6) RAW gölgeleme
org_src = ORG_DICT.read_text(encoding="utf-8")
m = re.search(r'private const val RAW = """\n(.*?)"""', org_src, re.S)
seen_label: dict[str, str] = {}
if m:
    for line in m.group(1).splitlines():
        parts = line.split("|")
        if len(parts) != 3:
            errors.append(f"RAW bozuk satır: {line[:60]}")
            continue
        lab, key, _ = parts
        if lab in seen_label and seen_label[lab] != key:
            errors.append(f"RAW gölgeleme: {lab!r} → {seen_label[lab]} ve {key}")
        seen_label.setdefault(lab, key)

if errors:
    print(f"❌ {len(errors)} hata:")
    for e in errors[:40]:
        print("  - " + e)
    if len(errors) > 40:
        print(f"  ... +{len(errors) - 40}")
    sys.exit(1)
print(f"✅ TÜM KONTROLLER GEÇTİ — {len(tr_keys)} bangumi_* anahtar, "
      f"{len(string_tr)} vault kaydı, {len(map_keys)} map satırı")

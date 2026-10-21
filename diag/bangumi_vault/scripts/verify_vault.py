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
  7. TERS YÖN: her map_chunk_*.tsv satırının kararı vault JSON'larına damgalanmış mı.
     (1-6 yalnız vault→strings yönüne bakar; bu yüzden damgasız/eski bir vault kopyası
     sessizce geçiyordu. Drive'dan taze indirilen JSON'lar chunk geride kalabiliyor →
     `apply_map.py` çalıştırılana dek bu kontrol kırılır. 2026-10-21'de eklendi.)

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
tag_values: dict[str, tuple[str, str]] = {}
for role, fn in ROLE_FILES.items():
    with (VAULT / fn).open(encoding="utf-8") as f:
        data = json.load(f)
    for rec in data:
        dec = rec.get("decision")
        sk = rec.get("string_key")
        if dec not in KEEP or not sk:
            continue
        t, e = rec.get("tr") or "", rec.get("en") or ""
        if role == "tag" and rec.get("canon"):
            cid = rec["canon"]
            if cid in tag_values and tag_values[cid] != (t, e):
                errors.append(f"{cid}: tag canon içinde çelişkili tr/en ({rec['key']})")
            tag_values[cid] = (t, e)
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

# Tag display resolves bangumi_tag_<BangumiTagEntry.key> before its dictionary fallback.
# Cross-role string_key values can be bangumi_studio_*/bangumi_publisher_*; those do not
# satisfy the tag lookup on their own, so require the tag group and tag-specific resource too.
tag_src = (UTILS / "BangumiTagDictionary.kt").read_text(encoding="utf-8")
tag_groups = set(re.findall(r'\bg\("([a-z0-9_]+)"', tag_src))
for cid, (t, e) in tag_values.items():
    tag_key = f"bangumi_tag_{cid}"
    if cid not in tag_groups:
        errors.append(f"{cid}: stable tag has no BangumiTagDictionary group")
    if tag_key not in tr_vals or tag_key not in en_vals:
        errors.append(f"{tag_key}: tag lookup resource missing in TR/EN")
        continue
    if unescape(tr_vals[tag_key]) != t:
        errors.append(f"{tag_key}: TR lookup value does not match tag canon")
    if unescape(en_vals[tag_key]) != e:
        errors.append(f"{tag_key}: EN lookup value does not match tag canon")

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
# (yalnız kategori dosyaları değil, vault.json arşiv kopyaları da denetlenir —
#  apply_map.py kararı TÜM kopyalara yazar)
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

# 6) RAW gölgeleme
org_src = ORG_DICT.read_text(encoding="utf-8")
raw_decl = re.search(r'private val RAW = listOf\((.*?)\)\.joinToString\(""\)', org_src, re.S)
if raw_decl:
    raw_chunks = re.findall(r'"""(.*?)"""', raw_decl.group(1), re.S)
    raw_body = "".join(raw_chunks)
else:
    legacy = re.search(r'private const val RAW = """\n?(.*?)"""', org_src, re.S)
    raw_body = legacy.group(1) if legacy else None
if raw_body is None:
    errors.append("BangumiOrgDictionary RAW bloğu bulunamadı")
else:
    raw_body = raw_body.replace("${'$'}", "$")
seen_label: dict[str, str] = {}
if raw_body is not None:
    for line in raw_body.splitlines():
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
      f"{len(string_tr)} vault kaydı, {len(map_keys)} map satırı, "
      f"{len(stamp_idx)} damga denetlendi")

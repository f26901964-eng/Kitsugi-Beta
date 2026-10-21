#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BANGUMI MASTER doğrulama — Kotlin arama mantığını (`bangumi/tags.tsv` + `bangumi/orgs.tsv`)
birebir simüle eder.

Kontroller:
  1. diag/bangumi_master/tags.tsv: her satır beklenen gruba çözülüyor mu?
  2. §7.4 ekran görüntüsü boşlukları (催淚 · 京都 · AS · 続編)
  3. diag/bangumi_master/orgs.tsv: her etiket beklenen `bangumi_*` anahtarına gidiyor mu?
  4. strings.xml + resource shards: TR/EN simetrisi + MASTER anahtarlarının kapsanması
  5. app/src/main/resources/bangumi/tags.tsv ve orgs.tsv biçim/gölgeleme bütünlüğü

Kullanım: python3 diag/bangumi_master/verify_bangumi_master.py
Çıkış kodu 1 ise sorun var.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "diag/bangumi_vault/scripts"))
import build_org_store
import build_tag_store
from resource_catalog import catalog_text, validate_catalog

DIAG = ROOT / "diag" / "bangumi_master"
TAG_STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"
ORG_STORE = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

problems: list[str] = validate_catalog(RES_TR)
notes: list[str] = []


def normalize(value: str) -> str:
    if not value.strip():
        return ""
    sb = []
    for ch in value:
        if ch == "　":
            sb.append(" ")
        elif ch in "（([【《「『":
            continue
        elif ch in "）)]】》」』":
            continue
        elif ch in "：:・·。、，,":
            sb.append(" ")
        else:
            sb.append(ch)
    return re.sub(r"\s+", " ", "".join(sb)).strip().lower()


def compact(value: str) -> str:
    return "".join(ch for ch in value if ch.isalnum()).lower()


def parse_groups() -> list[tuple[str, list[str]]]:
    """`bangumi/tags.tsv` içindeki grupları Kotlin `byLabel`/`byCompact` sırasıyla döndürür."""
    if not TAG_STORE.exists():
        problems.append(f"eksik etiket deposu: {TAG_STORE}")
        return []
    rows = build_tag_store.parse_store(TAG_STORE.read_text(encoding="utf-8"))
    problems.extend(f"tags.tsv: {e}" for e in build_tag_store.validate(rows))
    return [(r[0], r[3:] + [r[2], r[1]]) for r in rows if len(r) >= 3]


def build_lookup(groups):
    by_label, by_compact = {}, {}
    for key, labels in groups:
        # Kotlin'de önce tüm grupların açık yazımları (`group.labels`), sonra adları (`en`, `tr`) işlenir
        for label in labels[:-2]:
            k = normalize(label)
            if k and k not in by_label:
                by_label[k] = key
    for key, labels in groups:
        for label in labels[-2:]:
            k = normalize(label)
            if k and k not in by_label:
                by_label[k] = key
    for key, labels in groups:
        for label in labels[:-2]:
            k = compact(label)
            if len(k) >= 3 and k not in by_compact:
                by_compact[k] = key
    for key, labels in groups:
        for label in labels[-2:]:
            k = compact(label)
            if len(k) >= 3 and k not in by_compact:
                by_compact[k] = key
    return by_label, by_compact


def entry_for(label: str, by_label, by_compact):
    raw = label.strip()
    if not raw:
        return None
    hit = by_label.get(normalize(raw))
    if hit:
        return hit
    c = compact(raw)
    if len(c) >= 3:
        hit = by_compact.get(c)
        if hit:
            return hit
    return None


# ── 1) Etiketler ─────────────────────────────────────────────────────────────

groups = parse_groups()
by_label, by_compact = build_lookup(groups)
notes.append(f"BangumiTagDictionary (tags.tsv): {len(groups)} grup ayrıştırıldı")

tag_rows = [l.rstrip("\n").split("\t") for l in (DIAG / "tags.tsv").read_text(encoding="utf-8").splitlines()
            if l.strip() and not l.startswith("#")]
checked = 0
for key, tr, en, status, labels in tag_rows:
    short = key[len("bangumi_tag_"):]
    expected = short if status == "new" else status.split(":", 1)[1]
    for label in [l for l in labels.split(";") if l]:
        got = entry_for(label, by_label, by_compact)
        checked += 1
        if got != expected:
            problems.append(f"etiket gölgeleme: '{label}' → {got} (beklenen {expected})")
notes.append(f"tags.tsv: {checked} etiket çözümlemesi doğrulandı")

for label, expected in (("催淚", "tearjerker"), ("京都", "kyoto_animation"),
                        ("AS", "after_story"), ("続編", "sequel"),
                        ("催泪", "tearjerker"), ("催涙", "tearjerker")):
    got = entry_for(label, by_label, by_compact)
    if got != expected:
        problems.append(f"§7.4 boşluğu: '{label}' → {got} (beklenen {expected})")
if entry_for("ASOBI", by_label, by_compact) is not None:
    problems.append("'AS' kısa etiketi 'ASOBI' gibi adları yanlış yakalıyor")

# ── 2) Şirket sözlüğü ────────────────────────────────────────────────────────

if not ORG_STORE.exists():
    problems.append(f"eksik şirket deposu: {ORG_STORE}")
    raw_org_rows = []
else:
    raw_org_rows = build_org_store.parse_store(ORG_STORE.read_text(encoding="utf-8"))
    problems.extend(f"orgs.tsv: {e}" for e in build_org_store.validate(raw_org_rows))

org_by_label, org_by_compact, org_rows = {}, {}, []
for row in raw_org_rows:
    if len(row) < 3:
        continue
    label, key, en = row[0], row[1], row[2]
    org_rows.append((label, key, en))
    k = normalize(label)
    if k and k not in org_by_label:
        org_by_label[k] = (key, en)
    c = compact(label)
    if len(c) >= 3 and c not in org_by_compact:
        org_by_compact[c] = (key, en)
notes.append(f"BangumiOrgDictionary (orgs.tsv): {len(org_rows)} eşleme satırı")


def org_for(value: str):
    raw_v = value.strip()
    if not raw_v:
        return None
    hit = org_by_label.get(normalize(raw_v))
    if hit:
        return hit
    c = compact(raw_v)
    if len(c) >= 3:
        return org_by_compact.get(c)
    return None


org_lookup_fail, shadowed = [], defaultdict(list)
master_org_rows = [l.rstrip("\n").split("\t")
                   for l in (DIAG / "orgs.tsv").read_text(encoding="utf-8").splitlines()
                   if l.strip() and not l.startswith("#")]
for key, role, tr, en, status, labels in master_org_rows:
    if status == "pending":
        continue
    for label in [l for l in labels.split(";") if l]:
        got = org_for(label)
        if got is None or got[0] != key:
            org_lookup_fail.append(f"'{label}' → {got[0] if got else None} (beklenen {key})")
            if got:
                shadowed[key].append((label, got[0]))

key_to_name = {}
for label, key, en in org_rows:
    key_to_name.setdefault(key, en)
_en_vals = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>',
                           catalog_text(RES_EN), re.S))
for _k, _v in _en_vals.items():
    if _k.startswith("bangumi_") and _v.strip():
        key_to_name.setdefault(_k, _v.strip())
real_conflicts = []
for f in org_lookup_fail:
    label, got, expected = f.split("'")[1], f.split("→ ")[1].split(" (")[0], f.split("(beklenen ")[1].rstrip(")")
    got_name, exp_name = key_to_name.get(got), key_to_name.get(expected)
    if got_name is None or exp_name is None or got_name.casefold() != exp_name.casefold():
        real_conflicts.append(f"{f}  [adlar farklı: {got_name!r} vs {exp_name!r}]")
notes.append(f"orgs.tsv: {len(org_rows)} eşleme · aynı-ad rol çakışması {len(org_lookup_fail) - len(real_conflicts)} · GERÇEK çakışma {len(real_conflicts)}")
if real_conflicts:
    problems.extend(real_conflicts[:10])
elif org_lookup_fail:
    notes.append("   ↳ tümü aynı Latin adlı çok-rollü kayıtlar (Production I.G, bilibili, CBC ...)")

# ── 3) Dil dosyaları ─────────────────────────────────────────────────────────

tr_keys = set(re.findall(r'<string name="([^"]+)"', catalog_text(RES_TR)))
en_keys = set(re.findall(r'<string name="([^"]+)"', catalog_text(RES_EN)))
if tr_keys != en_keys:
    problems.append(f"dil dosyası simetrisi bozuk: TR'de fazla {sorted(tr_keys - en_keys)[:5]}, "
                    f"EN'de fazla {sorted(en_keys - tr_keys)[:5]}")
notes.append(f"strings.xml: TR {len(tr_keys)} · EN {len(en_keys)} kayıt (simetri {'OK' if tr_keys == en_keys else 'BOZUK'})")

master_expected_tag_keys = {key for key, _tr, _en, status, _labels in tag_rows if status == "new"}
missing_res = sorted(master_expected_tag_keys - tr_keys)
if missing_res:
    problems.append(f"MASTER etiketlerinde olup dil dosyasında olmayan kaynak ({len(missing_res)}): {missing_res[:8]}")

master_expected_org_keys = {key for key, _role, _tr, _en, status, _labels in master_org_rows if status != "pending"}
org_missing = sorted(master_expected_org_keys - tr_keys)
if org_missing:
    problems.append(f"MASTER şirketlerinde olup dil dosyasında olmayan kaynak ({len(org_missing)}): {org_missing[:8]}")

for f, keys in ((RES_TR, tr_keys), (RES_EN, en_keys)):
    src = catalog_text(f)
    for m in re.finditer(r'<string name="(bangumi_(?:tag|studio|producer|network|publisher|magazine)_[^"]+)">(.*?)</string>', src):
        if not m.group(2).strip():
            problems.append(f"boş değer: {f.name}:{m.group(1)}")

# ── 3b) Değer eşitliği — dil dosyaları MASTER TSV ile birebir aynı olmalı ────

def xml_escape(value: str) -> str:
    out = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return out.replace("'", "\\'").replace('"', '\\"')


def values_of(path: Path) -> dict:
    src = catalog_text(path)
    return {m.group(1): m.group(2)
            for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', src)}


tr_values, en_values = values_of(RES_TR), values_of(RES_EN)
compared, mismatched = 0, 0
for key, tr, en, status, _labels in tag_rows:
    if status != "new":
        continue
    compared += 1
    if tr_values.get(key) != xml_escape(tr):
        problems.append(f"TR değeri uyuşmuyor: {key} → {tr_values.get(key)!r} (tags.tsv: {tr!r})")
        mismatched += 1
    if en_values.get(key) != xml_escape(en):
        problems.append(f"EN değeri uyuşmuyor: {key} → {en_values.get(key)!r} (tags.tsv: {en!r})")
        mismatched += 1

for key, _role, tr, en, status, _labels in master_org_rows:
    if status == "pending":
        continue
    compared += 1
    if tr_values.get(key) != xml_escape(tr):
        problems.append(f"TR değeri uyuşmuyor: {key} → {tr_values.get(key)!r} (orgs.tsv: {tr!r})")
        mismatched += 1
    if en_values.get(key) != xml_escape(en):
        problems.append(f"EN değeri uyuşmuyor: {key} → {en_values.get(key)!r} (orgs.tsv: {en!r})")
        mismatched += 1
notes.append(f"dil dosyası değer eşitliği: {compared} kayıt TSV ile karşılaştırıldı"
             + (" (uyuşmazlık yok)" if not mismatched else f" · {mismatched} UYUŞMAZLIK"))

for n in notes:
    print("•", n)
if problems:
    print("\n❌ SORUNLAR:")
    for p in problems:
        print("   -", p)
    sys.exit(1)
print("\n✅ Tüm kontroller geçti")

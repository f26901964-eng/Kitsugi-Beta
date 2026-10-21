#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BANGUMI MASTER üreteci — `diag/bangumi_master/orgs.tsv` + `tags.tsv` → veri depoları / dil dosyaları / master.json

S3 sonrasında sözlük verileri `app/src/main/resources/bangumi/tags.tsv` ve `orgs.tsv`
içinde tutulur; bu üreteç VAULT satırlarını SİLMEDEN MASTER kayıtlarını idempotent olarak
senkronlar.

Kullanım:  python3 diag/bangumi_master/gen_bangumi_master.py [--check]
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "diag/bangumi_vault/scripts"))
import build_org_store
import build_tag_store

DIAG = ROOT / "diag" / "bangumi_master"
TAG_STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"
ORG_STORE = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

CHECK_ONLY = "--check" in sys.argv
MASTER_COMMENT = "<!-- Bangumi MASTER 2026-10-11 (diag/bangumi_master) -->"


def xml_escape(value: str) -> str:
    out = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return out.replace("'", "\\'").replace('"', '\\"')


def read_rows(path: Path, width: int):
    rows = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.rstrip("\n").split("\t")
        if len(parts) != width:
            raise SystemExit(f"{path.name}: beklenen {width} alan, {len(parts)} bulundu → {raw[:80]}")
        rows.append(parts)
    return rows


def strings_keys(source: str) -> set[str]:
    return set(re.findall(r'<string name="([^"]+)"', source))


def sync_values(source: str, entries: list[tuple[str, str]]) -> tuple[str, int]:
    marker = source.find(MASTER_COMMENT)
    if marker == -1:
        return source, 0
    head, tail = source[:marker], source[marker:]
    updated = 0
    for name, value in entries:
        pattern = re.compile(r'(<string name="%s">)(.*?)(</string>)' % re.escape(name))
        match = pattern.search(tail)
        if match and match.group(2) != xml_escape(value):
            tail = (tail[:match.start()] + match.group(1) + xml_escape(value)
                    + match.group(3) + tail[match.end():])
            updated += 1
    return head + tail, updated


def insert_strings(source: str, entries: list[tuple[str, str]], anchor_prefix: str) -> tuple[str, int, int]:
    source, updated = sync_values(source, entries)
    present = strings_keys(source)
    lines = [f'    <string name="{name}">{xml_escape(value)}</string>'
             for name, value in entries if name not in present]
    if not lines:
        return source, 0, updated
    out, idx = source.splitlines(), None
    for i, line in enumerate(out):
        if f'name="{anchor_prefix}' in line:
            idx = i
    if idx is None:
        raise SystemExit(f"çapa bulunamadı: {anchor_prefix}")
    block = ["", f"    {MASTER_COMMENT}"] + lines
    out[idx + 1:idx + 1] = block
    return "\n".join(out) + ("\n" if source.endswith("\n") else ""), len(lines), updated


# ── 1) Veriyi oku ────────────────────────────────────────────────────────────

orgs = []
for key, role, tr, en, status, labels in read_rows(DIAG / "orgs.tsv", 6):
    orgs.append({
        "key": key, "role": role, "tr": tr, "en": en, "status": status,
        "labels": [l for l in labels.split(";") if l],
    })

TAG_PREFIX = "bangumi_tag_"
tags = []
for key, tr, en, status, labels in read_rows(DIAG / "tags.tsv", 5):
    if not key.startswith(TAG_PREFIX):
        raise SystemExit(f"tags.tsv anahtarı {TAG_PREFIX} ile başlamalı: {key}")
    tags.append({
        "key": key, "short": key[len(TAG_PREFIX):], "tr": tr, "en": en, "status": status,
        "labels": [l for l in labels.split(";") if l],
    })

active_orgs = [o for o in orgs if o["status"] != "pending"]
pending_orgs = [o for o in orgs if o["status"] == "pending"]
new_tags = [t for t in tags if t["status"] == "new"]
label_add = [t for t in tags if t["status"].startswith("existing:")]
existing_targets = [t["status"].split(":", 1)[1] for t in label_add]

# ── 2) Doğrulama ─────────────────────────────────────────────────────────────

tag_store_rows = build_tag_store.parse_store(TAG_STORE.read_text(encoding="utf-8"))
by_tag_id = {r[0]: r for r in tag_store_rows}
missing_targets = [t for t in existing_targets if t not in by_tag_id]
errors = []
if missing_targets:
    errors.append(f"tags.tsv deposunda olmayan hedef anahtar: {sorted(set(missing_targets))}")

dup_org_keys = {k for k in (o["key"] for o in orgs) if [o["key"] for o in orgs].count(k) > 1}
if dup_org_keys:
    errors.append(f"orgs.tsv içinde yinelenen anahtar: {sorted(dup_org_keys)}")

dup_tag_keys = {k for k in (t["key"] for t in tags) if [t["key"] for t in tags].count(k) > 1}
if dup_tag_keys:
    errors.append(f"tags.tsv içinde yinelenen anahtar: {sorted(dup_tag_keys)}")

already_applied = all(t["short"] in by_tag_id for t in new_tags) if new_tags else True
if already_applied:
    new_tags_to_add = []
else:
    new_tags_to_add = [t for t in new_tags if t["short"] not in by_tag_id]

if errors:
    raise SystemExit("HATA:\n  - " + "\n  - ".join(errors))

# ── 3) tags.tsv yaması ───────────────────────────────────────────────────────

added_labels = 0
for entry in label_add:
    target = entry["status"].split(":", 1)[1]
    row = by_tag_id[target]
    have = set(row[1:])
    extra = [l for l in entry["labels"] if l not in have]
    if extra:
        row.extend(extra)
        added_labels += len(extra)

added_groups = 0
for entry in new_tags_to_add:
    seen, uniq = set(), []
    for l in entry["labels"]:
        if l not in seen:
            seen.add(l)
            uniq.append(l)
    if not uniq:
        uniq = [entry["en"]]
    new_row = [entry["short"], entry["tr"], entry["en"], *uniq]
    tag_store_rows.append(new_row)
    by_tag_id[entry["short"]] = new_row
    added_groups += 1

# ── 4) orgs.tsv senkronu (VAULT satırlarını koruyarak) ───────────────────────

org_store_rows = build_org_store.parse_store(ORG_STORE.read_text(encoding="utf-8"))
have_org_labels = {r[0] for r in org_store_rows}
master_org_lines = 0
added_org_rows = 0
for org in sorted(active_orgs, key=lambda o: (o["key"], o["en"])):
    seen, uniq = set(), []
    for label in org["labels"] + [org["en"], org["tr"]]:
        if label not in seen:
            seen.add(label)
            uniq.append(label)
    for label in uniq:
        master_org_lines += 1
        if label not in have_org_labels:
            have_org_labels.add(label)
            org_store_rows.append(
                [label, org["key"], org["en"], org["tr"]] if org["tr"] != org["en"]
                else [label, org["key"], org["en"]]
            )
            added_org_rows += 1

# ── 5) Dil dosyaları ─────────────────────────────────────────────────────────

sync_tag_tr = [(f"{TAG_PREFIX}{t['short']}", t["tr"]) for t in tags if t["status"] == "new"]
sync_tag_en = [(f"{TAG_PREFIX}{t['short']}", t["en"]) for t in tags if t["status"] == "new"]
org_entries_tr = [(o["key"], o["tr"]) for o in sorted(active_orgs, key=lambda o: o["key"])]
org_entries_en = [(o["key"], o["en"]) for o in sorted(active_orgs, key=lambda o: o["key"])]

tr_src = RES_TR.read_text(encoding="utf-8")
en_src = RES_EN.read_text(encoding="utf-8")

new_tr, n1, u1 = insert_strings(tr_src, sync_tag_tr, "bangumi_tag_instrumental")
new_en, n2, u2 = insert_strings(en_src, sync_tag_en, "bangumi_tag_instrumental")
new_tr, n3, u3 = insert_strings(new_tr, org_entries_tr, "bangumi_tag_")
new_en, n4, u4 = insert_strings(new_en, org_entries_en, "bangumi_tag_")
updated_tr, updated_en = u1 + u3, u2 + u4

if not CHECK_ONLY:
    RES_TR.write_text(new_tr, encoding="utf-8")
    RES_EN.write_text(new_en, encoding="utf-8")
    TAG_STORE.write_text(build_tag_store.render(tag_store_rows), encoding="utf-8")
    ORG_STORE.write_text(build_org_store.render(org_store_rows), encoding="utf-8")

master = {
    "meta": {
        "generated_from": ["orgs.tsv", "tags.tsv"],
        "source": "BANGUMI_STUDYO_YAPIMCI_YAYINCI_ETIKET_MASTER_2026-10-11.md (Drive)",
        "date": "2026-10-11",
        "producer": "diag/bangumi_master/gen_bangumi_master.py",
    },
    "orgs": {"studios": [], "producers": [], "networks": [], "publishers": [], "magazines": []},
    "orgs_pending": [o["key"] for o in pending_orgs],
    "tags": {"new": [], "label_additions": []},
}
bucket = {"studio": "studios", "producer": "producers", "network": "networks",
          "publisher": "publishers", "magazine": "magazines"}
for org in sorted(active_orgs, key=lambda o: o["key"]):
    master["orgs"][bucket[org["role"]]].append(
        {"key": org["key"], "tr": org["tr"], "en": org["en"], "labels": org["labels"]})
for tag in new_tags_to_add:
    master["tags"]["new"].append({"key": tag["key"], "tr": tag["tr"], "en": tag["en"], "labels": tag["labels"]})
for tag in label_add:
    master["tags"]["label_additions"].append(
        {"target": tag["status"].split(":", 1)[1], "labels": tag["labels"]})
if not CHECK_ONLY:
    (DIAG / "master.json").write_text(json.dumps(master, ensure_ascii=False, indent=1), encoding="utf-8")

print(f"orgs.tsv          : {len(orgs)} satır ({len(active_orgs)} etkin, {len(pending_orgs)} ⚠ beklemede)")
print(f"tags.tsv          : {len(tags)} satır ({len(new_tags_to_add)} yeni anahtar, {len(label_add)} mevcut gruba etiket)")
print(f"BangumiTagDictionary: +{added_groups} grup, +{added_labels} etiket")
print(f"strings.xml       : TR +{n1 + n3} kayıt, EN +{n2 + n4} kayıt, "
      f"{updated_tr}/{updated_en} değer güncellendi" + (" (--check: yazılmadı)" if CHECK_ONLY else ""))
print(f"BangumiOrgDictionary: {master_org_lines} MASTER eşleme satırı (+{added_org_rows} yeni, toplam {len(org_store_rows)})"
      + (" (--check: yazılmadı)" if CHECK_ONLY else ""))

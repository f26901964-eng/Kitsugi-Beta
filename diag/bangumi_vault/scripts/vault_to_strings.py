#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT → uygulama üreticisi (idempotent, S3 veri deposu mimarisi).

Kararlı (decision ∈ global/translated/as_is/existing) vault kayıtlarını:
  1. Mevcut `strings.xml` + resource shard anahtarlarında (`58.398` sabit tavan korunur)
     değer değişmişse TR (`values/`) ve EN (`values-en/`) kaynaklarını senkronlar;
     yeni `R$string` ID'si açmaz (Android 65.535 tür tavanı aşılmaz),
  2. `app/src/main/resources/bangumi/tags.tsv` veri deposuna tüm kararlı etiket gruplarını
     (`key \t tr \t en \t etiket...`) yazar / günceller (sıra korunur),
  3. `app/src/main/resources/bangumi/orgs.tsv` veri deposuna tüm kararlı kurum eşlemelerini
     (`etiket \t key \t en [\t tr]`) yazar / günceller (sıra korunur).

Kullanım:
  python3 diag/bangumi_vault/scripts/vault_to_strings.py [--check]
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from android_strings import escape as xml_escape
import build_org_store
import build_tag_store
from resource_catalog import resource_paths

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
TAG_STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"
ORG_STORE = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

BLOCK_MARKER = '<!-- Bangumi VAULT 2026-10-20 (diag/bangumi_vault) — ÜRETİLMİŞ, elle düzenlemeyin -->'
TAG_VAULT_MARKER = "// ── Bangumi VAULT 2026-10-20: vault kanon etiketleri ──"

ROLE_FILES = {
    "studio": "studios.json",
    "producer": "producers.json",
    "network": "networks.json",
    "publisher": "publishers.json",
    "magazine": "magazines.json",
    "committee": "committees.json",
    "tag": "tags.json",
}
KEEP_DECISIONS = {"global", "translated", "as_is", "existing"}

# ── Geriye dönük parçalama yardımcıları (`test_tag_dict_chunks.py` sözleşmesi) ─
GROUP_CHUNK_SIZE = 400
GROUPS_HEAD_RE = re.compile(r'(?m)^    private val groups: List<Group> = listOf\(\n')
GROUPS_CHUNK_RE = re.compile(r'(?m)^    private fun groupChunk(\d+)\(\): List<Group> = listOf\(\n')


def chunk_group_lines(lines: list[str], size: int = GROUP_CHUNK_SIZE) -> list[list[str]]:
    chunks: list[list[str]] = []
    current: list[str] = []
    count = 0
    for line in lines:
        if not line.strip():
            continue
        current.append(line)
        if line.lstrip().startswith('g("'):
            count += 1
            if count == size:
                chunks.append(current)
                current, count = [], 0
    if any(l.lstrip().startswith('g("') for l in current):
        chunks.append(current)
    return chunks


def render_group_chunk(index: int, lines: list[str]) -> str:
    body: list[str] = []
    for line in lines:
        text = line.rstrip()
        if text.lstrip().startswith('g("'):
            text = text.rstrip(",")
        body.append(text)
    for i, text in enumerate(body):
        if not text.lstrip().startswith('g("'):
            continue
        if any(l.lstrip().startswith('g("') for l in body[i + 1:]):
            text += ","
        body[i] = text
    return (f"    private fun groupChunk{index:02d}(): List<Group> = listOf(\n"
            + "\n".join(body) + "\n    )\n")


def ensure_group_chunks(source: str) -> str:
    if GROUPS_CHUNK_RE.search(source):
        return source
    head = GROUPS_HEAD_RE.search(source)
    if not head:
        raise SystemExit("groups listesi başlığı bulunamadı")
    end = source.index("\n    )", head.end())
    body = source[head.end():end]
    chunks = chunk_group_lines(body.splitlines())
    if not chunks:
        raise SystemExit("groups listesi boş")
    calls = " +\n        ".join(f"groupChunk{i + 1:02d}()" for i in range(len(chunks)))
    rendered = "".join(render_group_chunk(i + 1, c) for i, c in enumerate(chunks))
    tail_marker = "\n    )\n"
    if source[end:end + len(tail_marker)] != tail_marker:
        raise SystemExit("groups listesi kapanışı beklenen biçimde değil")
    new_head = "    private val groups: List<Group> =\n        " + calls + "\n\n"
    return source[:head.start()] + new_head + rendered + source[end + len(tail_marker):]


def count_chunk_entries(lines: list[str]) -> int:
    return sum(1 for l in lines if l.lstrip().startswith('g("'))


def rerender_group_chunks(source: str, size: int = GROUP_CHUNK_SIZE) -> str:
    matches = list(GROUPS_CHUNK_RE.finditer(source))
    if not matches:
        raise SystemExit("groupChunk fonksiyonu bulunamadı")
    lines: list[str] = []
    for m in matches:
        close = source.index("\n    )", m.end())
        lines.extend([l for l in source[m.end():close].splitlines() if l.strip()])
    chunks = chunk_group_lines(lines, size)
    calls = " +\n        ".join(f"groupChunk{i + 1:02d}()" for i in range(len(chunks)))
    rendered = "".join(render_group_chunk(i + 1, c) for i, c in enumerate(chunks))
    first, last = matches[0], matches[-1]
    last_close = source.index("\n    )\n", last.end()) + len("\n    )\n")
    head = "    private val groups: List<Group> =\n        " + calls + "\n\n"
    return source[:first.start()] + head + rendered + source[last_close:]


def clean_tsv_cell(value: str) -> str:
    return value.replace("\t", " ").replace("\r", " ").replace("\n", " ")


def patch_existing_strings(source: str, values: dict[str, str]) -> tuple[str, int]:
    """Mevcut string kaynaklarının güncellenmiş değerlerini senkronlar (yeni ID eklemez)."""
    updated = 0

    def repl(match: re.Match) -> str:
        nonlocal updated
        name, cur = match.group(2), match.group(3)
        if name not in values:
            return match.group(0)
        escaped = xml_escape(values[name])
        if cur == escaped:
            return match.group(0)
        updated += 1
        return f"{match.group(1)}{escaped}{match.group(4)}"

    rendered = re.sub(r'(<string name="([^"]+)">)(.*?)(</string>)', repl, source, flags=re.S)
    return rendered, updated


def run(check_only: bool = False) -> int:
    canon_aliases: dict[tuple[str, str], list[str]] = {}
    for raw in (VAULT / "decisions" / "canon.tsv").read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) >= 6:
            canon_aliases[(parts[1], parts[0])] = [a for a in parts[5].split(";") if a]

    entries: dict[str, dict] = {}
    tag_entries: dict[str, dict] = {}
    order: list[str] = []

    for role, fn in ROLE_FILES.items():
        with (VAULT / fn).open(encoding="utf-8") as f:
            data = json.load(f)
        for rec in data:
            dec = rec.get("decision")
            if dec not in KEEP_DECISIONS:
                continue
            key = rec.get("string_key")
            labels = list(rec.get("labels") or [])
            for extra in (rec.get("tr"), rec.get("en")):
                if extra and extra not in labels:
                    labels.append(extra)

            record_role = rec.get("role", role)
            canon_id = rec.get("canon") or ""
            if record_role == "tag" and canon_id:
                tag_tr = clean_tsv_cell(rec.get("tr") or rec.get("en") or "")
                tag_en = clean_tsv_cell(rec.get("en") or rec.get("tr") or "")
                tag_entry = tag_entries.setdefault(
                    canon_id, {"tr": tag_tr, "en": tag_en, "labels": [], "origin": dec}
                )
                if (tag_entry["tr"], tag_entry["en"]) != (tag_tr, tag_en):
                    raise SystemExit(f"{canon_id}: tag canon içinde çelişkili TR/EN")
                for lab in labels + canon_aliases.get((record_role, canon_id), []):
                    cleaned = clean_tsv_cell(lab)
                    if cleaned and cleaned not in tag_entry["labels"]:
                        tag_entry["labels"].append(cleaned)

            if not key:
                continue
            if key not in entries:
                entries[key] = {
                    "tr": clean_tsv_cell(rec.get("tr") or rec.get("en") or ""),
                    "en": clean_tsv_cell(rec.get("en") or rec.get("tr") or ""),
                    "role": rec.get("role", role),
                    "labels": [],
                    "canons": set(),
                    "decisions": set(),
                }
                order.append(key)
            e = entries[key]
            e["decisions"].add(dec)
            if rec.get("canon"):
                e["canons"].add(rec["canon"])
            for lab in labels:
                cleaned = clean_tsv_cell(lab)
                if cleaned and cleaned not in e["labels"]:
                    e["labels"].append(cleaned)
            for lab in canon_aliases.get((rec.get("role", role), rec.get("canon") or ""), []):
                cleaned = clean_tsv_cell(lab)
                if cleaned and cleaned not in e["labels"]:
                    e["labels"].append(cleaned)

    print(f"string anahtarı: {len(entries)}")

    claimed_labels: set[str] = set()
    for k in order:
        e = entries[k]
        kept = []
        for lab in e["labels"]:
            if lab in claimed_labels:
                continue
            claimed_labels.add(lab)
            kept.append(lab)
        e["labels"] = kept
    shadowed = sum(1 for k in order if not entries[k]["labels"])
    if shadowed:
        print(f"not: {shadowed} girişin TÜM etiketleri gölgelendi (değerleri korunur, "
              f"veri deposundan yalnız diğer anahtarlar üzerinden erişilir)")

    # ── 2) Mevcut strings.xml + shard değer senkronu (yeni ID eklenmez) ──────
    tr_vals = {k: e["tr"] for k, e in entries.items()}
    en_vals = {k: e["en"] for k, e in entries.items()}
    for cid, e in tag_entries.items():
        tag_key = f"bangumi_tag_{cid}"
        for values, lang in ((tr_vals, "tr"), (en_vals, "en")):
            value = e[lang]
            if tag_key in values and values[tag_key] != value:
                raise SystemExit(f"{tag_key}: cross-role/tag kaynağında çelişkili {lang.upper()} değeri")
            values[tag_key] = value

    resource_outputs = {}
    resource_changes = []
    for app_file, values in ((RES_TR, tr_vals), (RES_EN, en_vals)):
        sources = {p: p.read_text(encoding="utf-8") for p in resource_paths(app_file)}
        owners = {}
        for path, source in sources.items():
            for key in re.findall(r'<string name="([^"]+)"', source):
                if key in owners:
                    raise SystemExit(f"yinelenen modüller arası kaynak: {key}")
                owners[key] = path
        grouped = {p: {} for p in sources}
        for key, value in values.items():
            if key in owners:
                grouped[owners[key]][key] = value
        updated = 0
        for path, source in sources.items():
            rendered, u = patch_existing_strings(source, grouped[path])
            resource_outputs[path] = rendered
            updated += u
        resource_changes.append((0, updated))
    (a1, u1), (a2, u2) = resource_changes
    print(f"strings.xml (app + shards): TR +{a1} / ~{u1} | EN +{a2} / ~{u2}")

    # ── 3) tags.tsv veri deposu ──────────────────────────────────────────────
    tag_rows = build_tag_store.parse_store(TAG_STORE.read_text(encoding="utf-8"))
    by_tag_id = {row[0]: row for row in tag_rows}
    added_groups = added_labels = updated_groups = 0

    for cid in sorted(tag_entries):
        e = tag_entries[cid]
        if cid in by_tag_id:
            row = by_tag_id[cid]
            # existing origin'de mevcut grup TR/EN korunur; diğerlerinde canon değeri senkronlanır
            if e["origin"] != "existing" and (row[1], row[2]) != (e["tr"], e["en"]):
                row[1], row[2] = e["tr"], e["en"]
                updated_groups += 1
            have = set(row[1:])
            extra = [l for l in e["labels"] if l not in have]
            if extra:
                row.extend(extra)
                added_labels += len(extra)
        else:
            seen, uniq = set(), []
            for l in e["labels"]:
                if l and l not in seen:
                    seen.add(l)
                    uniq.append(l)
            if not uniq:
                uniq = [e["en"]]
            new_row = [cid, e["tr"], e["en"], *uniq]
            tag_rows.append(new_row)
            by_tag_id[cid] = new_row
            added_groups += 1

    tag_problems = build_tag_store.validate(tag_rows)
    if tag_problems:
        raise SystemExit("tags.tsv doğrulama hatası:\n  - " + "\n  - ".join(tag_problems[:20]))
    tag_out_text = build_tag_store.render(tag_rows)
    print(f"tags.tsv: +{added_groups} grup, ~{updated_groups} güncelleme, +{added_labels} etiket (toplam {len(tag_rows)} grup)")

    # ── 4) orgs.tsv veri deposu ──────────────────────────────────────────────
    org_rows = build_org_store.parse_store(ORG_STORE.read_text(encoding="utf-8"))
    have_labels: set[str] = set()
    updated_org_rows = 0
    for row in org_rows:
        have_labels.add(row[0])
        key = row[1]
        if key in entries:
            e = entries[key]
            cur_tr = row[3] if len(row) >= 4 else row[2]
            if (row[2], cur_tr) != (e["en"], e["tr"]):
                row[:] = [row[0], key, e["en"], e["tr"]] if e["tr"] != e["en"] else [row[0], key, e["en"]]
                updated_org_rows += 1

    new_org_rows: list[list[str]] = []
    for key in sorted(entries):
        e = entries[key]
        if e["role"] == "tag":
            continue
        seen: set[str] = set()
        for label in e["labels"] + [e["en"], e["tr"]]:
            if not label or label in have_labels or label in seen:
                continue
            seen.add(label)
            have_labels.add(label)
            new_org_rows.append(
                [label, key, e["en"], e["tr"]] if e["tr"] != e["en"] else [label, key, e["en"]]
            )
    org_rows.extend(new_org_rows)
    org_problems = build_org_store.validate(org_rows)
    if org_problems:
        raise SystemExit("orgs.tsv doğrulama hatası:\n  - " + "\n  - ".join(org_problems[:20]))
    org_out_text = build_org_store.render(org_rows)
    print(f"orgs.tsv: +{len(new_org_rows)} satır, ~{updated_org_rows} güncelleme (toplam {len(org_rows)} satır)")

    if not check_only:
        for path, source in resource_outputs.items():
            path.write_text(source, encoding="utf-8")
        TAG_STORE.write_text(tag_out_text, encoding="utf-8")
        ORG_STORE.write_text(org_out_text, encoding="utf-8")
        print("yazıldı.")
    else:
        print("--check: yazma yok.")
    return 0


if __name__ == "__main__":
    raise SystemExit(run(check_only="--check" in sys.argv))

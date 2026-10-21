#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT → uygulama üreticisi (idempotent).

Kararlı (decision ∈ global/translated/as_is/existing) vault kayıtlarını:
  1. `app/src/main/res/values/strings.xml` (TR) ve `values-en/strings.xml` (EN) içine
     `<!-- Bangumi VAULT -->` bloğuna yazar (mevcut anahtarlar yalnız blok içinde güncellenir;
     MASTER/elle yazılmış kayıtlara dokunulmaz),
  2. `BangumiTagDictionary.kt` içine etiket grupları ekler / mevcut gruplara etiket yazar,
  3. `BangumiOrgDictionary.kt` RAW bloğuna `etiket|anahtar|küresel_ad` satırları ekler.

Birleşim kuralı: aynı `canon` değerini paylaşan kayıtlar TEK string anahtarında toplanır
(global karşılık birleşmesi); etiketler ilk yazım sırasıyla birleştirilir (gölgelenme yok).

Kullanım:
  python3 diag/bangumi_vault/scripts/vault_to_strings.py [--check]
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
UTILS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils"
TAG_DICT = UTILS / "BangumiTagDictionary.kt"
ORG_DICT = UTILS / "BangumiOrgDictionary.kt"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

CHECK = "--check" in sys.argv
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


def xml_escape(value: str) -> str:
    out = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    out = out.replace("'", "\\'").replace('"', '\\"')
    return out


def kotlin_escape(value: str) -> str:
    return value.replace("$", "${'$'}")


MAX_KOTLIN_CONSTANT_MUTF8_BYTES = 50_000


def kotlin_mutf8_length(value: str) -> int:
    size = 0
    for char in value:
        codepoint = ord(char)
        if codepoint == 0:
            size += 2
        elif codepoint <= 0x7F:
            size += 1
        elif codepoint <= 0x7FF:
            size += 2
        elif codepoint <= 0xFFFF:
            size += 3
        else:
            size += 6
    return size


def render_kotlin_raw_chunks(lines: list[str]) -> str:
    chunks: list[list[str]] = []
    current: list[str] = []
    current_size = 0
    for line in lines:
        if '"""' in line:
            raise SystemExit("org etiketi Kotlin üç-tırnaklı ham metin sınırlayıcısını içeriyor")
        line_size = kotlin_mutf8_length(line + "\n")
        if line_size > MAX_KOTLIN_CONSTANT_MUTF8_BYTES:
            raise SystemExit("tek bir RAW satırı Kotlin sabit dizgesi sınırını aşıyor")
        if current and current_size + line_size > MAX_KOTLIN_CONSTANT_MUTF8_BYTES:
            chunks.append(current)
            current = []
            current_size = 0
        current.append(line)
        current_size += line_size
    if current:
        chunks.append(current)
    return ",\n".join('        """' + "\n".join(chunk) + "\n" + '"""' for chunk in chunks)


def extract_raw_declaration(source: str):
    declaration = re.search(
        r'private val RAW = listOf\((.*?)\)\.joinToString\(""\)', source, re.S)
    if declaration:
        chunks = re.findall(r'"""(.*?)"""', declaration.group(1), re.S)
        if not chunks:
            raise SystemExit("RAW listesinde Kotlin metin parçacığı bulunamadı")
        return declaration, chunks
    legacy = re.search(r'private const val RAW = """(.*?)"""', source, re.S)
    if legacy:
        return legacy, [legacy.group(1)]
    raise SystemExit("RAW bloğu bulunamadı")


# ── 1) Kararlı kayıtları topla ────────────────────────────────────────────────

# canon alias'ları: (rol, canon_id) → aliases
canon_aliases: dict[tuple[str, str], list[str]] = {}
for raw in (VAULT / "decisions" / "canon.tsv").read_text(encoding="utf-8").splitlines():
    if not raw.strip() or raw.lstrip().startswith("#"):
        continue
    parts = raw.split("\t")
    if len(parts) >= 6:
        canon_aliases[(parts[1], parts[0])] = [a for a in parts[5].split(";") if a]

entries: dict[str, dict] = {}   # string_key → {tr, en, role, labels, canons, decisions}
# Tag lookup is keyed by BangumiTagDictionary's canon id, not by its localized
# string_key. Cross-role tags (for example, a tag using bangumi_studio_*) still
# need a tag group and bangumi_tag_<canon> resource for KitsugiTranslations.
tag_entries: dict[str, dict] = {}  # canon id → {tr, en, labels}
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
            tag_tr = rec.get("tr") or rec.get("en") or ""
            tag_en = rec.get("en") or rec.get("tr") or ""
            tag_entry = tag_entries.setdefault(canon_id, {"tr": tag_tr, "en": tag_en, "labels": []})
            if (tag_entry["tr"], tag_entry["en"]) != (tag_tr, tag_en):
                raise SystemExit(f"{canon_id}: tag canon içinde çelişkili TR/EN")
            for lab in labels + canon_aliases.get((record_role, canon_id), []):
                if lab and lab not in tag_entry["labels"]:
                    tag_entry["labels"].append(lab)

        if not key:
            continue
        if key not in entries:
            entries[key] = {
                "tr": rec.get("tr") or rec.get("en") or "",
                "en": rec.get("en") or rec.get("tr") or "",
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
        # ilk yazım kazanır: ilk kayıt tr/en'i belirler, sonrakiler yalnız etiket ekler
        for lab in labels:
            if lab and lab not in e["labels"]:
                e["labels"].append(lab)
        for lab in canon_aliases.get((rec.get("role", role), rec.get("canon") or ""), []):
            if lab and lab not in e["labels"]:
                e["labels"].append(lab)

print(f"string anahtarı: {len(entries)}")

# Etiket budaması: ilk yazım kazanır — bir etiket RAW'da yalnız ilk sahibine bağlanır;
# sonraki girişlerdeki gölgelenmiş etiketler düşer (çakışan kısa adlar: CTV, Netflix…).
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
          f"RAW'dan yalnız diğer anahtarlar üzerinden erişilir)")

# ── 2) strings.xml bloğu ──────────────────────────────────────────────────────


def build_block(items: list[tuple[str, str]]) -> str:
    lines = [f'    <string name="{n}">{xml_escape(v)}</string>' for n, v in items]
    return "\n".join(lines)


_STRING_LINE_RE = re.compile(r'^(\s*<string name="([^"]+)">)(.*?)(</string>\s*)$')


def patch_strings(source: str, values: dict[str, str]) -> tuple[str, int, int]:
    """VAULT bloğunu yazar/günceller. (yeni kaynak, eklenen, güncellenen)

    Blok içeriğini tek geçişte satır satır ayrıştırarak günceller — her `<string>`
    için ayrı ayrı regex derlenip çalıştırılmaz (büyük bloklarda O(N·M) patlar).
    """
    present = set(re.findall(r'<string name="([^"]+)"', source))
    added = updated = 0

    if BLOCK_MARKER in source:
        start = source.index(BLOCK_MARKER)
        end = source.index("</resources>", start)
        block = source[start:end]
        new_lines: list[str] = []
        in_block = True
        seen_block_keys: set[str] = set()
        # Bloğu satır satır dolaş; yalnız name values'de varsa değerini değiştir
        for line in block.split("\n"):
            m = _STRING_LINE_RE.match(line)
            if m:
                open_tag, name, val, close_tag = m.group(1), m.group(2), m.group(3), m.group(4)
                seen_block_keys.add(name)
                if name in values:
                    want = xml_escape(values[name])
                    if val != want:
                        line = f"{open_tag}{want}{close_tag}"
                        updated += 1
            new_lines.append(line)
        # Eksik (blokta olmayan) yeni anahtarları bloğun sonuna ekle
        new_items = [(n, v) for n, v in values.items() if n not in seen_block_keys]
        added = len(new_items)
        if new_items:
            tail = "\n".join(new_lines).rstrip("\n")
            new_lines = (tail + "\n" + build_block(new_items) + "\n").split("\n")
        rebuilt_block = "\n".join(new_lines)
        return source[:start] + rebuilt_block + source[end:], added, updated

    new_items = [(n, values[n]) for n in sorted(values) if n not in present]
    added = len(new_items)
    block = BLOCK_MARKER + "\n" + build_block(new_items) + "\n"
    idx = source.rindex("</resources>")
    return source[:idx] + block + source[idx:], added, updated


tr_vals = {k: e["tr"] for k, e in entries.items()}
en_vals = {k: e["en"] for k, e in entries.items()}
for cid, e in tag_entries.items():
    tag_key = f"bangumi_tag_{cid}"
    for values, lang in ((tr_vals, "tr"), (en_vals, "en")):
        value = e[lang]
        if tag_key in values and values[tag_key] != value:
            raise SystemExit(f"{tag_key}: cross-role/tag kaynağında çelişkili {lang.upper()} değeri")
        values[tag_key] = value

tr_src = RES_TR.read_text(encoding="utf-8")
en_src = RES_EN.read_text(encoding="utf-8")
tr_src, a1, u1 = patch_strings(tr_src, tr_vals)
en_src, a2, u2 = patch_strings(en_src, en_vals)
print(f"strings.xml: TR +{a1} / ~{u1} | EN +{a2} / ~{u2}")

# ── 3) BangumiTagDictionary.kt ───────────────────────────────────────────────


_TAG_G_LINE_RE = re.compile(r'^(?P<indent>\s*)g\("(?P<cid>[a-z0-9_]+)",(?P<body>.*)\)\s*,?\s*$')


def patch_tag_dict(source: str) -> tuple[str, int, int]:
    added_groups = added_labels = 0
    known = set(re.findall(r'\bg\("([a-z0-9_]+)"', source))

    # Tag UI lookup is independent of the shared organization string_key:
    # KitsugiTranslations first asks for bangumi_tag_<BangumiTagEntry.key>, then
    # falls back to this group's TR/EN values. Keep tag entries separate from
    # org entries so a cross-role string_key cannot suppress the tag group.

    # (a) mevcut gruplara etiket ekle — TEK GEÇİŞTE satır satır (N×regex yerine)
    pending = {cid: e for cid, e in tag_entries.items() if cid in known}
    if pending:
        out_lines = []
        for line in source.split("\n"):
            m = _TAG_G_LINE_RE.match(line)
            if m and m.group("cid") in pending:
                cid = m.group("cid")
                e = pending[cid]
                # body alanı, açılıştaki iki argümandan ("tr", "en") sonraki ", "lab""ları içerir
                body = m.group("body")
                # Mevcut etiketleri saymak için body içindeki tüm "..." parçalarını al
                have = set(re.findall(r'"((?:[^"\\]|\\.)*)"', body))
                extra = [l for l in e["labels"] if l not in have]
                if extra:
                    appended = "".join(', "%s"' % kotlin_escape(l) for l in extra)
                    # body sonuna ekle; `body` kapanış `)`'dan önceki kısımdır
                    # m.end("body") body'nin son byte'ını, yani `)`'dan hemen öncesini gösterir
                    # Satır orijinali üzerinden konum hesapla:
                    body_start = m.start("body")
                    body_end_in_line = m.end("body")
                    # Body kapanan parantezden hemen önce biter ama regex `\)`'ı tüketmediği
                    # için body'in sonuna ekleyip orijinal `)`'ı korumalıyız.
                    # Ancak `(?P<body>.*)` greedy `)`'dan önce durur, dolayısıyla body sonu
                    # `)`'ın tam başlangıcıdır.
                    line = (line[:body_end_in_line].rstrip()
                            + appended
                            + line[body_end_in_line:])
                    added_labels += len(extra)
            out_lines.append(line)
        source = "\n".join(out_lines)

    # (b) yeni gruplar (canon id'si sözlükte olmayanlar)
    new_lines = []
    for cid in sorted(tag_entries):
        e = tag_entries[cid]
        if cid in known:
            continue
        seen, uniq = set(), []
        for l in e["labels"]:
            if l and l not in seen:
                seen.add(l)
                uniq.append(l)
        if not uniq:
            uniq = [e["en"]]
        new_lines.append('        g("%s", "%s", "%s"%s)' % (
            cid, kotlin_escape(e["tr"]), kotlin_escape(e["en"]),
            "".join(', "%s"' % kotlin_escape(l) for l in uniq)))
        known.add(cid)
        added_groups += 1
    if not new_lines:
        return source, added_groups, added_labels

    if TAG_VAULT_MARKER in source:
        idx = source.index(TAG_VAULT_MARKER) + len(TAG_VAULT_MARKER)
        source = source[:idx] + "\n" + ",\n".join(new_lines) + "," + source[idx:]
    else:
        match = re.search(r'^\s{4}\)\s*$', source, re.M)
        if not match:
            raise SystemExit("groups listesinin kapanışı bulunamadı")
        head = source[:match.start()]
        stripped = head.rstrip()
        if not stripped.endswith(","):
            head = stripped + ",\n"
        block = "        " + TAG_VAULT_MARKER + "\n" + ",\n".join(new_lines)
        source = head + "\n" + block + "\n" + source[match.start():]
    return source, added_groups, added_labels


tag_src = TAG_DICT.read_text(encoding="utf-8")
tag_src, ag, al = patch_tag_dict(tag_src)
print(f"BangumiTagDictionary: +{ag} grup, +{al} etiket")

# ── 4) BangumiOrgDictionary.kt RAW ────────────────────────────────────────────

org_src = ORG_DICT.read_text(encoding="utf-8")
raw_match, raw_chunks = extract_raw_declaration(org_src)
raw_body = "".join(raw_chunks)
raw_values = raw_body.replace("${'$'}", "$")
have_labels = set()
for line in raw_values.splitlines():
    parts = line.split("|")
    if len(parts) == 3:
        have_labels.add(parts[0])

new_raw = []
for key in sorted(entries):
    e = entries[key]
    if e["role"] == "tag":
        continue
    seen = set()
    for label in e["labels"] + [e["en"], e["tr"]]:
        if not label or label in have_labels or label in seen:
            continue
        seen.add(label)
        have_labels.add(label)
        new_raw.append(f"{kotlin_escape(label)}|{key}|{kotlin_escape(e['en'])}")
if new_raw:
    raw_lines = [line for line in raw_body.splitlines() if line] + new_raw
    new_decl = (
        "private val RAW = listOf(\n"
        + render_kotlin_raw_chunks(raw_lines)
        + "\n    ).joinToString(\"\")"
    )
    org_src = org_src[:raw_match.start()] + new_decl + org_src[raw_match.end():]
print(f"BangumiOrgDictionary: +{len(new_raw)} satır")

# ── 5) Yaz ────────────────────────────────────────────────────────────────────

if not CHECK:
    RES_TR.write_text(tr_src, encoding="utf-8")
    RES_EN.write_text(en_src, encoding="utf-8")
    TAG_DICT.write_text(tag_src, encoding="utf-8")
    ORG_DICT.write_text(org_src, encoding="utf-8")
    print("yazıldı.")
else:
    print("--check: yazma yok.")

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BANGUMI MASTER doğrulama — Kotlin arama mantığını birebir simüle eder.

Neden: `BangumiTagDictionary` "ilk yazım kazanır" (`first wins`) kuralıyla çalışır. Yeni gruplar
listenin SONUNA eklendiği için, aynı etiketi taşıyan eski bir grup varsa yeni grup **gölgelenir**
ve ekranda yanlış çip görünür. Bu betik her etiket için gerçek çözümlemeyi hesaplar.

Kontroller:
  1. tags.tsv: her satır (yeni + mevcut hedef) beklenen gruba çözülüyor mu?
  2. §7.4 ekran görüntüsü boşlukları (催淚 · 京都 · AS · 続編)
  3. orgs.tsv: her etiket beklenen `bangumi_*` anahtarına gidiyor mu? (çakışma raporu)
  4. strings.xml: tr/en anahtar simetrisi + sözlük anahtarlarının kapsanması
  5. BangumiOrgDictionary RAW bloğu ↔ orgs.tsv tutarlılığı

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
from resource_catalog import catalog_text, validate_catalog
DIAG = ROOT / "diag" / "bangumi_master"
TAG_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"
ORG_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

problems: list[str] = validate_catalog(RES_TR)
notes: list[str] = []


# ── Kotlin mantığının kopyası ────────────────────────────────────────────────

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


MAX_KOTLIN_CONSTANT_MUTF8_BYTES = 50_000


def kotlin_mutf8_length(value: str) -> int:
    """Return the class-file modified-UTF-8 size of a Kotlin/JVM string constant."""
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
            size += 6  # JVM strings encode supplementary characters as surrogate pairs.
    return size


def extract_org_raw(source: str) -> tuple[str, list[str]]:
    """Extract joined RAW text and validate each generated literal fits the class-file limit."""
    declaration = re.search(
        r'private val RAW = listOf\((.*?)\)\.joinToString\(""\)', source, re.S)
    if not declaration:
        problems.append("BangumiOrgDictionary: parçalı RAW listOf bildirimi bulunamadı")
        return "", []
    chunks = re.findall(r'"""(.*?)"""', declaration.group(1), re.S)
    if not chunks:
        problems.append("BangumiOrgDictionary: RAW içinde Kotlin metin parçacığı bulunamadı")
        return "", []
    # The generator escapes a literal dollar in a Kotlin raw string as ${'$'}.
    chunks = [chunk.replace("${'$'}", "$") for chunk in chunks]
    for index, chunk in enumerate(chunks, start=1):
        size = kotlin_mutf8_length(chunk)
        if size > MAX_KOTLIN_CONSTANT_MUTF8_BYTES:
            problems.append(
                f"BangumiOrgDictionary RAW parçası {index} class-file sınırını aşıyor: {size} bayt")
    return "".join(chunks).strip(), chunks


def parse_groups() -> list[tuple[str, list[str]]]:
    """BangumiTagDictionary.kt içindeki g(...) çağrılarını SIRAYLA döndürür."""
    src = TAG_DICT.read_text(encoding="utf-8")
    groups = []
    for m in re.finditer(r'g\("([a-z0-9_]+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"((?:\s*,\s*"(?:[^"\\]|\\.)*")*)\)', src):
        key = m.group(1)
        names = [m.group(2), m.group(3)]
        labels = names + [x.group(1) for x in re.finditer(r'"((?:[^"\\]|\\.)*)"', m.group(4) or "")]
        groups.append((key, [l.replace('\\"', '"').replace("\\\\", "\\") for l in labels]))
    return groups


def build_lookup(groups):
    by_label, by_compact = {}, {}
    for key, labels in groups:
        for label in labels:
            k = normalize(label)
            if k and k not in by_label:
                by_label[k] = key
    for key, labels in groups:
        for label in labels:
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
notes.append(f"BangumiTagDictionary: {len(groups)} grup ayrıştırıldı")

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

# §7.4 ekran görüntüsü boşlukları
for label, expected in (("催淚", "tearjerker"), ("京都", "kyoto_animation"),
                        ("AS", "after_story"), ("続編", "sequel"),
                        ("催泪", "tearjerker"), ("催涙", "tearjerker")):
    got = entry_for(label, by_label, by_compact)
    if got != expected:
        problems.append(f"§7.4 boşluğu: '{label}' → {got} (beklenen {expected})")
if entry_for("ASOBI", by_label, by_compact) is not None:
    problems.append("'AS' kısa etiketi 'ASOBI' gibi adları yanlış yakalıyor")

# ── 2) Şirket sözlüğü ────────────────────────────────────────────────────────

org_src = ORG_DICT.read_text(encoding="utf-8")
raw, raw_chunks = extract_org_raw(org_src)
org_by_label, org_by_compact, org_rows = {}, {}, []
for line in raw.splitlines():
    label, key, en = line.split("|")
    org_rows.append((label, key, en))
    k = normalize(label)
    if k and k not in org_by_label:
        org_by_label[k] = (key, en)
    c = compact(label)
    if len(c) >= 3 and c not in org_by_compact:
        org_by_compact[c] = (key, en)
notes.append(f"BangumiOrgDictionary: {len(org_rows)} eşleme satırı")
notes.append(
    f"BangumiOrgDictionary RAW: {len(raw_chunks)} parça; en büyük "
    f"{max((kotlin_mutf8_length(chunk) for chunk in raw_chunks), default=0)} modified-UTF-8 bayt"
)


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
for line in (DIAG / "orgs.tsv").read_text(encoding="utf-8").splitlines():
    if not line.strip() or line.startswith("#"):
        continue
    key, role, tr, en, status, labels = line.split("\t")
    if status == "pending":
        continue
    for label in [l for l in labels.split(";") if l]:
        got = org_for(label)
        if got is None or got[0] != key:
            org_lookup_fail.append(f"'{label}' → {got[0] if got else None} (beklenen {key})")
            if got:
                shadowed[key].append((label, got[0]))
# Aynı şirket birden çok rolde listelenebilir (ör. Production I.G hem stüdyo hem yapımcı,
# bilibili hem yapımcı hem ağ). Latin ad AYNIYSA çakışma zararsızdır; FARKLIYSA gerçek hatadır.
key_to_name = {}
for label, key, en in org_rows:
    key_to_name.setdefault(key, en)
# RAW'da hiç satırı kalmayan anahtarlar: aynı kurum birden çok rolde geçerken etiketleri
# ikizi tarafından sahiplenilmiş olabilir (ilk yazım kazanır → bu anahtarın satırı düşer).
# Latin ad o zaman dil dosyasından çözülür; aksi hâlde zararsız çok-rollü çakışma
# "GERÇEK çakışma" diye raporlanıyordu (2026-10-21 düzeltmesi).
_en_vals = dict(re.findall(r'<string name="([^"]+)">(.*?)</string>',
                           catalog_text(RES_EN), re.S))
for _k in set(re.findall(r"\|(bangumi_(?:studio|producer|network|publisher|magazine)_[a-z0-9_]+)\|", raw)):
    key_to_name.setdefault(_k, _en_vals.get(_k))
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

missing_res = [k for k, _ in groups if f"bangumi_tag_{k}" not in tr_keys]
if missing_res:
    problems.append(f"sözlükte olup dil dosyasında olmayan etiket kaynağı ({len(missing_res)}): {missing_res[:8]}")

org_missing = [l.split("|")[1] for l in raw.splitlines() if l.split("|")[1] not in tr_keys]
if org_missing:
    problems.append(f"şirket sözlüğünde olup dil dosyasında olmayan kaynak ({len(org_missing)}): {sorted(set(org_missing))[:8]}")

# boş değer kontrolü
for f, keys in ((RES_TR, tr_keys), (RES_EN, en_keys)):
    src = catalog_text(f)
    for m in re.finditer(r'<string name="(bangumi_(?:tag|studio|producer|network|publisher|magazine)_[^"]+)">(.*?)</string>', src):
        if not m.group(2).strip():
            problems.append(f"boş değer: {f.name}:{m.group(1)}")

# ── 3b) Değer eşitliği — dil dosyaları TSV ile birebir aynı olmalı ───────────
# Konvansiyon: values/ = TR sütunu, values-en/ = EN sütunu. Bu kontrol "TR değerin EN
# dosyasına yazılması" gibi sınıfları yakalar (2026-10-11'de yaşandı: 58 kayıt).

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

org_tag_rows = [l.rstrip("\n").split("\t")
                for l in (DIAG / "orgs.tsv").read_text(encoding="utf-8").splitlines()
                if l.strip() and not l.startswith("#")]
for key, _role, tr, en, status, _labels in org_tag_rows:
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

# ── Rapor ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

for n in notes:
    print("•", n)
if problems:
    print("\n❌ SORUNLAR:")
    for p in problems:
        print("   -", p)
    sys.exit(1)
print("\n✅ Tüm kontroller geçti")

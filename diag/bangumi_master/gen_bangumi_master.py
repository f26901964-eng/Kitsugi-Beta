#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BANGUMI MASTER üreteci — orgs.tsv + tags.tsv → kod / dil dosyaları / master.json

Girdi:
  diag/bangumi_master/orgs.tsv   (stüdyo · yapımcı · yayıncı ağ · yayımcı · dergi)
  diag/bangumi_master/tags.tsv   (§7.2 meta etiketler · §7.3 öneriler · §7.4 boşluklar)

Çıktı:
  app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt  (ÜRETİLMİŞ)
  app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt  (yama: yeni grup + eksik etiket)
  app/src/main/res/values/strings.xml        (TR kayıtları)
  app/src/main/res/values-en/strings.xml     (EN kayıtları)
  diag/bangumi_master/master.json            (makine-okur döküm)

Kurallar:
  • Uydurma çeviri yok: karşılığı olmayan özel ad olduğu gibi kalır.
  • values/ ve values-en/ anahtar kümeleri BİREBİR simetrik kalır.
  • Idempotent: var olan anahtar/grup/etiket ikinci kez eklenmez.
  • `pending` (⚠ doğrulanacak) satırlar dil dosyasına ve sözlüğe ALINMAZ.

Kullanım:  python3 diag/bangumi_master/gen_bangumi_master.py [--check]
           --check: hiçbir şey yazmaz, yalnızca rapor verir (CI/doğrulama).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIAG = ROOT / "diag" / "bangumi_master"
UTILS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils"
TAG_DICT = UTILS / "BangumiTagDictionary.kt"
ORG_DICT = UTILS / "BangumiOrgDictionary.kt"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

CHECK_ONLY = "--check" in sys.argv

ORG_PREFIXES = {
    "studio": "bangumi_studio_",
    "producer": "bangumi_producer_",
    "network": "bangumi_network_",
    "publisher": "bangumi_publisher_",
    "magazine": "bangumi_magazine_",
}


# ── Yardımcılar ──────────────────────────────────────────────────────────────

def xml_escape(value: str) -> str:
    """Android string kaynağı kaçışları (apostrof `\\'` biçiminde — dosya kuralı)."""
    out = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    out = out.replace("'", "\\'").replace('"', '\\"')
    return out


def kotlin_escape(value: str) -> str:
    """Kotlin üç-tırnaklı metin bloğu içi güvenli yazım."""
    return value.replace("$", "${'$'}")


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


def dict_keys(source: str) -> set[str]:
    return set(re.findall(r'\bg\("([a-z0-9_]+)"', source))


def strings_keys(source: str) -> set[str]:
    return set(re.findall(r'<string name="([^"]+)"', source))


MASTER_COMMENT = "<!-- Bangumi MASTER 2026-10-11 (diag/bangumi_master) -->"


def sync_values(source: str, entries: list[tuple[str, str]]) -> tuple[str, int]:
    """MASTER bloğundaki mevcut kayıtların değerini TSV ile eşitler (idempotent).

    Yalnızca işaretçi yorumundan SONRAKİ kayıtlara dokunur; üreticinin yazmadığı hiçbir
    satır değiştirilmez. Böylece TSV'de bir çeviri düzeltildiğinde (ör. `Çinçe` → `Çince`)
    değer dil dosyalarına da yayılır.
    """
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
    """`entries` (ad, değer) kayıtlarını son `anchor_prefix` satırından sonra ekler (idempotent).

    Dönen: (yeni kaynak, eklenen kayıt, değeri güncellenen kayıt).
    """
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
    block = [] if idx is None else ["", "    <!-- Bangumi MASTER 2026-10-11 (diag/bangumi_master) -->"] + lines
    if idx is None:
        raise SystemExit(f"çapa bulunamadı: {anchor_prefix}")
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
        # g() birinci argümanı KISA anahtardır; dil dosyası adı = TAG_PREFIX + short
        "key": key, "short": key[len(TAG_PREFIX):], "tr": tr, "en": en, "status": status,
        "labels": [l for l in labels.split(";") if l],
    })

active_orgs = [o for o in orgs if o["status"] != "pending"]
pending_orgs = [o for o in orgs if o["status"] == "pending"]
new_tags = [t for t in tags if t["status"] == "new"]
label_add = [t for t in tags if t["status"].startswith("existing:")]
existing_targets = [t["status"].split(":", 1)[1] for t in label_add]


# ── 2) Doğrulama ─────────────────────────────────────────────────────────────

tag_src = TAG_DICT.read_text(encoding="utf-8")
known = dict_keys(tag_src)
missing_targets = [t for t in existing_targets if t not in known]
errors = []
if missing_targets:
    errors.append(f"BangumiTagDictionary'de olmayan hedef anahtar: {sorted(set(missing_targets))}")

dup_org_keys = {k for k in (o["key"] for o in orgs) if [o["key"] for o in orgs].count(k) > 1}
if dup_org_keys:
    errors.append(f"orgs.tsv içinde yinelenen anahtar: {sorted(dup_org_keys)}")

dup_tag_keys = {k for k in (t["key"] for t in tags) if [t["key"] for t in tags].count(k) > 1}
if dup_tag_keys:
    errors.append(f"tags.tsv içinde yinelenen anahtar: {sorted(dup_tag_keys)}")

# Yeni etiket anahtarı zaten sözlükte olmamalı (aksi hâlde etiketler mevcut gruba gitmeliydi).
# Üretici daha önce çalıştıysa blok zaten eklenmiştir: bu bir çakışma değil, yinelenen çalıştırmadır.
MARKER = "Bangumi MASTER 2026-10-11: yeni meta/öneri etiketleri"
already_applied = MARKER in tag_src
clash = [t["short"] for t in new_tags if t["short"] in known]
if already_applied:
    not_yet = [t["short"] for t in new_tags if t["short"] not in known]
    if not_yet:
        errors.append(f"MASTER bloğu var ama şu anahtarlar eksik (elle müdahale gerekir): {not_yet}")
    new_tags = []  # yeniden eklenmesin
elif clash:
    errors.append(f"yeni denilen etiket anahtarı sözlükte zaten var: {clash}")

if errors:
    raise SystemExit("HATA:\n  - " + "\n  - ".join(errors))


# ── 3) BangumiTagDictionary.kt yaması ────────────────────────────────────────

def patch_tag_dictionary(source: str) -> tuple[str, int, int]:
    """(yeni_kaynak, eklenen_grup, eklenen_etiket)"""
    added_labels = 0
    for entry in label_add:
        target = entry["status"].split(":", 1)[1]
        pattern = re.compile(r'^(?P<indent>\s*)g\("%s",(?P<body>.*)\)\s*,?\s*$' % re.escape(target), re.M)
        match = pattern.search(source)
        if not match:
            raise SystemExit(f"grup satırı bulunamadı: {target}")
        body = match.group("body")
        have = set(re.findall(r'"([^"]*)"', body))
        extra = [l for l in entry["labels"] if l not in have]
        if not extra:
            continue
        appended = "".join(', "%s"' % kotlin_escape(l) for l in extra)
        source = source[:match.end("body")].rstrip() + appended + source[match.end("body"):]
        added_labels += len(extra)

    added_groups = 0
    if new_tags:
        lines = ["", "        // ── Bangumi MASTER 2026-10-11: yeni meta/öneri etiketleri ──"]
        for entry in new_tags:
            seen, uniq = set(), []
            for l in entry["labels"]:
                if l not in seen:
                    seen.add(l)
                    uniq.append(l)
            if not uniq:
                uniq = [entry["en"]]
            lines.append('        g("%s", "%s", "%s"%s),' % (
                entry["short"], kotlin_escape(entry["tr"]), kotlin_escape(entry["en"]),
                "".join(', "%s"' % kotlin_escape(l) for l in uniq)))
            added_groups += 1
        lines[-1] = lines[-1].rstrip(",")  # liste son elemanı virgülsüz
        match = re.search(r'^\s{4}\)\s*$', source, re.M)
        if not match:
            raise SystemExit("groups listesinin kapanışı bulunamadı")
        head = source[:match.start()]
        # Listeye SON elemandan sonra ekleniyoruz: önceki elemanın sonunda virgül yoksa ekle,
        # aksi hâlde `g(...) g(...)` sözdizimi bozulur (derleyici hatası).
        stripped = head.rstrip()
        if not stripped.endswith(","):
            head = stripped + ",\n"
        source = head + "\n".join(lines) + "\n" + source[match.start():]
    return source, added_groups, added_labels


tag_src_new, added_groups, added_labels = patch_tag_dictionary(tag_src)


# ── 4) BangumiOrgDictionary.kt üretimi ───────────────────────────────────────

org_lines = []
for org in sorted(active_orgs, key=lambda o: (o["key"], o["en"])):
    seen, uniq = set(), []
    for label in org["labels"] + [org["en"], org["tr"]]:
        if label not in seen:
            seen.add(label)
            uniq.append(label)
    for label in uniq:
        org_lines.append(f"{kotlin_escape(label)}|{org['key']}|{kotlin_escape(org['en'])}")

ORG_TEMPLATE = '''package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Bangumi şirket adları sözlüğü — **stüdyo / yapımcı / yayıncı ağ / yayımcı / dergi**.
 *
 * ⚠ **ÜRETİLMİŞ DOSYA — elle düzenlemeyin.**
 * Kaynak: `diag/bangumi_master/orgs.tsv` → `python3 diag/bangumi_master/gen_bangumi_master.py`
 *
 * Bangumi infobox'ında şirket adları CJK (Japonca/Çince) ya da Latin gelir
 * (`京都アニメーション`, `BONES`, `木棉花` ...). Bu sözlük, gelen adı bir `bangumi_*` dil
 * dosyası anahtarına ve **resmî Latin/İngilizce adına** eşler:
 *
 *  • [resourceKeyFor] → `bangumi_studio_*` / `bangumi_producer_*` / `bangumi_network_*` /
 *    `bangumi_publisher_*` / `bangumi_magazine_*` anahtarı (TR/EN değer dil dosyasından gelir),
 *  • [latinNameFor]   → eşleşen kaydın küresel İngilizce adı (arama/dedup için).
 *
 * Eşleşme normalize edilerek yapılır: büyük/küçük harf, boşluk, tam genişlik noktalama ve
 * `「」（）` sarımları yok sayılır; ikinci turda yalnızca harf/rakam bırakan sıkı anahtar
 * denenir (`Production.IG` ≡ `Production I.G`). **Uydurma çeviri yoktur**: eşleşmeyen ad
 * olduğu gibi kalır — komite adları (`PROJECT-XXX`, `光坂高校演劇部`) bilinçli olarak dışarıda.
 *
 * Doğrulanmamış (⚠) nadir adlar bu dosyaya, `orgs.tsv`'de `pending` işaretli oldukları için
 * alınmaz; kaynakta görüldüklerinde işaret kaldırılıp üretici yeniden çalıştırılır.
 */
internal object BangumiOrgDictionary {

    private const val RAW = """
%RAW%"""

    /** `etiket|anahtar|küresel ad` satırları, tembel olarak ayrıştırılır. */
    private val rows: List<Triple<String, String, String>> by lazy {
        RAW.trim().lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) null else Triple(parts[0], parts[1], parts[2])
        }
    }

    private val byLabel: Map<String, Triple<String, String, String>> by lazy {
        val map = HashMap<String, Triple<String, String, String>>(rows.size * 2)
        for (row in rows) {
            val key = normalize(row.first)
            if (key.isNotEmpty() && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val byCompact: Map<String, Triple<String, String, String>> by lazy {
        val map = HashMap<String, Triple<String, String, String>>(rows.size * 2)
        for (row in rows) {
            val key = compact(row.first)
            if (key.length >= 3 && !map.containsKey(key)) map[key] = row
        }
        map
    }

    private val byKey: Map<String, String> by lazy {
        val map = HashMap<String, String>(rows.size * 2)
        for (row in rows) if (!map.containsKey(row.second)) map[row.second] = row.third
        map
    }

    /** Gelen şirket adı için `bangumi_*` dil dosyası anahtarı; bilinmiyorsa `null`. */
    fun resourceKeyFor(raw: String?): String? = rowFor(raw)?.second

    /** Gelen adın küresel İngilizce yazımı (`京阿尼` → `Kyoto Animation`); bilinmiyorsa `null`. */
    fun latinNameFor(raw: String?): String? = rowFor(raw)?.third

    /** Anahtarın küresel İngilizce adı; yoksa `null`. */
    fun canonicalName(key: String): String? = byKey[key]

    /** Eşleşen satır (etiket, anahtar, küresel ad); bilinmiyorsa `null`. */
    private fun rowFor(raw: String?): Triple<String, String, String>? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        byLabel[normalize(cleaned)]?.let { return it }
        val compact = compact(cleaned)
        if (compact.length >= 3) byCompact[compact]?.let { return it }
        return null
    }

    /** Girdi sayısı (test/telemetri için). */
    val size: Int get() = rows.size

    /** Boşluk, büyük/küçük harf, tam genişlik noktalama ve sarımları yok sayan anahtar. */
    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '　' -> sb.append(' ')
                '（', '(', '[', '【', '《', '「', '『' -> Unit
                '）', ')', ']', '】', '》', '」', '』' -> Unit
                '：', ':', '・', '·', '。', '、', '，', ',' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim().replace(Regex("\\\\s+"), " ").lowercase(Locale.ROOT)
    }

    /** Yalnızca harf/rakam bırakan sıkı anahtar: `Production.IG` → `productionig`. */
    private fun compact(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) if (ch.isLetterOrDigit()) sb.append(ch)
        return sb.toString().lowercase(Locale.ROOT)
    }
}
'''

ORG_DICT.write_text(ORG_TEMPLATE.replace("%RAW%", "\n".join(org_lines) + "\n"),
                    encoding="utf-8") if not CHECK_ONLY else None


# ── 5) Dil dosyaları ─────────────────────────────────────────────────────────

# Değer senkronu TÜM `new` satırlarını görür: blok daha önce eklendiyse `new_tags` boşaltılmıştır
# (yeniden eklenmesin diye), ama değer düzeltmeleri yine de dil dosyalarına yayılmalıdır.
sync_tag_tr = [(f"{TAG_PREFIX}{t['short']}", t["tr"]) for t in tags if t["status"] == "new"]
sync_tag_en = [(f"{TAG_PREFIX}{t['short']}", t["en"]) for t in tags if t["status"] == "new"]
# Ekleme için TR karşılığı; EN dosyasına da EN karşılığı yazılır (konvansiyon: values/=TR, values-en/=EN).
tag_entries = [(f"{TAG_PREFIX}{t['short']}", t["tr"]) for t in new_tags]
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
    TAG_DICT.write_text(tag_src_new, encoding="utf-8")


# ── 6) master.json + rapor ───────────────────────────────────────────────────

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
for tag in new_tags:
    master["tags"]["new"].append({"key": tag["key"], "tr": tag["tr"], "en": tag["en"], "labels": tag["labels"]})
for tag in label_add:
    master["tags"]["label_additions"].append(
        {"target": tag["status"].split(":", 1)[1], "labels": tag["labels"]})
if not CHECK_ONLY:
    (DIAG / "master.json").write_text(json.dumps(master, ensure_ascii=False, indent=1), encoding="utf-8")

print(f"orgs.tsv          : {len(orgs)} satır ({len(active_orgs)} etkin, {len(pending_orgs)} ⚠ beklemede)")
print(f"tags.tsv          : {len(tags)} satır ({len(new_tags)} yeni anahtar, {len(label_add)} mevcut gruba etiket)")
print(f"BangumiTagDictionary: +{added_groups} grup, +{added_labels} etiket")
print(f"strings.xml       : TR +{n1 + n3} kayıt, EN +{n2 + n4} kayıt, "
      f"{updated_tr}/{updated_en} değer güncellendi" + (" (--check: yazılmadı)" if CHECK_ONLY else ""))
print(f"BangumiOrgDictionary: {len(org_lines)} eşleme satırı" + (" (--check: yazılmadı)" if CHECK_ONLY else ""))

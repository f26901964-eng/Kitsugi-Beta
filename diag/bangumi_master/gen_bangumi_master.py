#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi MASTER sözlük üreticisi (idempotent).

Girdi
  diag/bangumi_master/orgs.tsv   role/key/tr/en/variants  — kurum adları
  diag/bangumi_master/tags.tsv   key/tr/en/variants       — EK etiket grupları (opsiyonel)

Çıktı
  app/.../utils/BangumiOrgDictionary.kt      (tamamen yeniden yazılır)
  app/.../utils/BangumiTagDictionary.kt      (yalnızca MASTER bloğu + varyant ekleri)
  app/src/main/res/values/strings.xml        (MASTER bloğu yeniden yazılır)
  app/src/main/res/values-en/strings.xml     (MASTER bloğu yeniden yazılır)

Kullanım
  python3 diag/bangumi_master/gen_bangumi_master.py            # üret
  python3 diag/bangumi_master/gen_bangumi_master.py --check    # yazmadan denetle (fark varsa 1)

Kurallar
  • Uydurma çeviri yok: marka adlarında TR = EN; `pending` satırları atlanır.
  • İki dil dosyası her zaman AYNI anahtar kümesini alır; TR değeri orgs.tsv TR
    sütunundan, EN değeri EN sütunundan okunur. (Geçmişteki hata: EN dosyasına
    TR değerlerinin yazılması.)
  • Üreteç `values*/strings.xml` içinde yalnızca kendi işaretlediği MASTER bloğunu
    değiştirir; bloğun dışındaki hiçbir satıra dokunmaz.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIAG = ROOT / "diag/bangumi_master"
ORGS_TSV = DIAG / "orgs.tsv"
TAGS_TSV = DIAG / "tags.tsv"
ROLES_TSV = DIAG / "roles.tsv"
PLACES_TSV = DIAG / "places.tsv"

UTILS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils"
ORG_KT = UTILS / "BangumiOrgDictionary.kt"
TAG_KT = UTILS / "BangumiTagDictionary.kt"
ROLE_KT = UTILS / "BangumiRoleDictionary.kt"

STRINGS_TR = ROOT / "app/src/main/res/values/strings.xml"
STRINGS_EN = ROOT / "app/src/main/res/values-en/strings.xml"

BLOCK_BEGIN = "<!-- BANGUMI_MASTER:BEGIN (üreteç yönetir: diag/bangumi_master/gen_bangumi_master.py) -->"
BLOCK_END = "<!-- BANGUMI_MASTER:END -->"

ROLE_ENUM = {
    "studio": "STUDIO",
    "producer": "PRODUCER",
    "network": "NETWORK",
    "publisher": "PUBLISHER",
    "magazine": "MAGAZINE",
}

TAG_BLOCK_MARK = "// ── MASTER ekleri"


# ── Veri okuma ───────────────────────────────────────────────────────────────

class Org:
    __slots__ = ("role", "key", "tr", "en", "variants", "status")

    def __init__(self, role: str, key: str, tr: str, en: str, variants: list[str], status: str) -> None:
        self.role = role
        self.key = key
        self.tr = tr
        self.en = en
        self.variants = variants
        self.status = status


def read_tsv(path: Path, columns: int) -> list[list[str]]:
    """TSV okur; `#` yorum ve boş satırları atlar. Sütun sayısı eksikse hata verir."""
    if not path.exists():
        return []
    rows: list[list[str]] = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < columns:
            raise SystemExit(f"{path.name}:{lineno}: {columns} sütun bekleniyordu, {len(parts)} bulundu")
        rows.append(parts[:columns])
    return rows


def load_orgs() -> list[Org]:
    orgs: list[Org] = []
    seen_keys: set[str] = set()
    for parts in read_tsv(ORGS_TSV, 5):
        role, key, tr, en, variants_raw = parts
        role = role.strip().lower()
        key = key.strip()
        status = "pending" if role.startswith("pending") else "active"
        role_name = role.removeprefix("pending_").removeprefix("pending")
        if role_name not in ROLE_ENUM:
            raise SystemExit(f"orgs.tsv: bilinmeyen rol '{role}' (key={key})")
        if key in seen_keys:
            raise SystemExit(f"orgs.tsv: yinelenen anahtar '{key}'")
        seen_keys.add(key)
        variants = [v.strip() for v in variants_raw.split("|") if v.strip()]
        orgs.append(Org(ROLE_ENUM[role_name], key, tr.strip(), en.strip(), variants, status))
    return orgs


def load_tag_rows() -> list[tuple[str, str, str, list[str]]]:
    rows: list[tuple[str, str, str, list[str]]] = []
    seen: set[str] = set()
    for parts in read_tsv(TAGS_TSV, 4):
        key, tr, en, variants_raw = parts
        key = key.strip()
        if key in seen:
            raise SystemExit(f"tags.tsv: yinelenen anahtar '{key}'")
        seen.add(key)
        variants = [v.strip() for v in variants_raw.split("|") if v.strip()]
        rows.append((key, tr.strip(), en.strip(), variants))
    return rows


# ── Kotlin kaçışlama ─────────────────────────────────────────────────────────

def kstr(value: str) -> str:
    out = value.replace("\\", "\\\\").replace('"', '\\"')
    out = out.replace("$", "\\$")
    return f'"{out}"'


def xstr(value: str) -> str:
    """Android kaynak dosyası kaçışlaması: &, <, > ve tek tırnak."""
    out = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return out.replace("'", "\\'")


# ── BangumiOrgDictionary.kt ──────────────────────────────────────────────────

ORG_KT_TEMPLATE = '''package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Bangumi (bgm.tv) **kurum adı sözlüğü**: stüdyo, yapımcı, yayıncı ağ, yayımcı ve dergi.
 *
 * Bangumi infobox'ındaki `动画制作` / `製作` / `播放电视台` / `出版社` / `连载杂志` alanları
 * Japonca ya da Çince ham ad döndürür (`京都アニメーション`, `アニプレックス`, `集英社` ...).
 * Bu sözlük o adları arayüz diline (Türkçe / İngilizce) taşır.
 *
 * Kurallar ([BangumiTagDictionary] ile aynı):
 *  • **Uydurma çeviri yok.** Marka adlarında Türkçe değer İngilizce değerle aynıdır.
 *  • Bilinmeyen kurum adı olduğu gibi döner; üretim komiteleri (`PROJECT-XXX`,
 *    `光坂高校演劇部`) sözlüğe alınmaz.
 *  • Eşleştirme normalize edilerek yapılır: boşluk, büyük/küçük harf, tam genişlikli
 *    noktalama, `「」《》（）` sarımları ve `株式会社` gibi unvan ekleri yok sayılır.
 *  • Bu tablo **yalnızca gösterim katmanı** içindir. Arama, kimlik doğrulama ve
 *    tekilleştirme daima Latin adla ([String.toLatinStudioName]) yürür; çünkü
 *    `StudioDetailViewModel.matchesExpectedStudio` ham adla karşılaştırır.
 *
 * ÜRETİCİ YÖNETİMİNDE: bu dosyayı elle düzenlemeyin. `diag/bangumi_master/orgs.tsv`
 * güncellenip `python3 diag/bangumi_master/gen_bangumi_master.py` çalıştırılmalıdır.
 */
internal enum class BangumiOrgRole {
    STUDIO, PRODUCER, NETWORK, PUBLISHER, MAGAZINE
}

internal data class BangumiOrgEntry(
    /** `bangumi_org_<key>` dil dosyası anahtarı. */
    val key: String,
    val role: BangumiOrgRole,
    val turkish: String,
    val english: String
)

internal object BangumiOrgDictionary {

    private class Org(val entry: BangumiOrgEntry, val labels: List<String>)

    private fun o(
        key: String,
        role: BangumiOrgRole,
        turkish: String,
        english: String,
        vararg labels: String
    ) = Org(BangumiOrgEntry(key, role, turkish, english), labels.toList())

    // ── Tablo (üreteç tarafından yazılır) ────────────────────────────────────

    private val orgs: List<Org> = listOf(
__ROWS__
    )

    // ── Eşleştirme ───────────────────────────────────────────────────────────

    private val byLabel: Map<String, Org> by lazy {
        val map = HashMap<String, Org>(orgs.sumOf { it.labels.size } * 2)
        for (org in orgs) {
            for (label in org.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                // Açık yazımlar önce işlendiği için bir takma ad asla gerçek adı gölgelemez.
                if (!map.containsKey(key)) map[key] = org
            }
        }
        // Kurumun Latin/İngilizce adı da eşleşme anahtarıdır: Bangumi'de aynı kurum hem
        // Çince hem Latin yazımıyla bulunabiliyor (`京阿尼` + `Kyoto Animation`).
        for (org in orgs) {
            for (name in listOf(org.entry.english, org.entry.turkish)) {
                val key = normalize(name)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = org
            }
        }
        map
    }

    /** Kurum adının sözlük girişi; bilinmiyorsa null. */
    fun entryFor(name: String?): BangumiOrgEntry? {
        val raw = name?.trim().orEmpty()
        if (raw.isEmpty()) return null
        byLabel[normalize(raw)]?.let { return it.entry }
        val stripped = stripCorporateSuffix(raw)
        if (stripped.isNotEmpty() && stripped != raw) {
            byLabel[normalize(stripped)]?.let { return it.entry }
        }
        return null
    }

    /** Sözlükteki tüm yazımlar (test ve araç kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    /** Sözlükteki tüm kayıtlar (test ve araç kullanımı için). */
    fun allEntries(): List<BangumiOrgEntry> = orgs.map { it.entry }

    private val CORPORATE_PREFIX = Regex("^(合同会社|株式会社|有限会社|有限公司|股份有限公司)\\s*")
    private val CORPORATE_SUFFIX = Regex("\\s*(合同会社|株式会社|有限会社|有限公司|股份有限公司)$")

    /** `株式会社集英社` → `集英社`: unvan ekleri lookup için yok sayılır. */
    private fun stripCorporateSuffix(value: String): String {
        var text = value.replace(Regex("【[^】]*】"), " ")
        text = text.replace(Regex("""\\([^)]*\\)|（[^）]*）"""), " ")
        text = text.replace(CORPORATE_PREFIX, "")
        text = text.replace(CORPORATE_SUFFIX, "")
        return text.replace(Regex("""\\s+"""), " ").trim()
    }

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
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }
}
'''


def render_org_kt(orgs: list[Org]) -> str:
    active = [o for o in orgs if o.status == "active"]
    lines: list[str] = []
    for index, org in enumerate(active):
        labels = ", ".join(kstr(v) for v in org.variants)
        comma = "," if index < len(active) - 1 else ""
        # Son elemandan sonra virgül YOK — ama ekler her zaman blok sonuna geldiği
        # için üreteç satır bazında yazar ve eksik virgül hatası oluşmaz.
        lines.append(
            f"        o({kstr(org.key)}, BangumiOrgRole.{org.role}, "
            f"{kstr(org.tr)}, {kstr(org.en)}, {labels}){comma}"
        )
    return ORG_KT_TEMPLATE.replace("__ROWS__", "\n".join(lines))


# ── BangumiRoleDictionary.kt (tamamen üretilir) ──────────────────────────────

ROLE_KT_TEMPLATE = '''package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Bangumi (bgm.tv) **kadro ve karakter rolü sözlüğü**.
 *
 * Bangumi'nin v0 liste uçları (`/v0/persons/{id}/subjects`, `/v0/characters/{id}/subjects`,
 * `/v0/subjects/{id}/persons`) rol/görev alanlarını Çince ya da Japonca ham metin olarak
 * döndürür: `主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督`, `脚本` ... Bu sözlük o
 * değerleri uygulamanın arayüz diline (Türkçe / İngilizce) çevirir.
 *
 * Kurallar ([BangumiTagDictionary] ile aynı):
 *  • **Uydurma çeviri yok.** Bilinmeyen rol olduğu gibi döner.
 *  • Aynı anlamın basit/geleneksel Çince ve Japonca yazımları AYNI girişte toplanır
 *    (`监督` + `監督`, `音乐` + `音楽`).
 *  • Eşleştirme normalize edilerek yapılır: boşluk, büyük/küçük harf ve tam genişlikli
 *    sarımlar (`「」《》（）【】`) yok sayılır.
 *  • Bir girişin `key` alanı `bangumi_role_<key>` dil dosyası kaynağıyla eşleşir;
 *    kaynak varsa (yalnızca tr/en arayüzünde) o tercih edilir, yoksa bu tablo kullanılır.
 *
 * ÜRETİCİ YÖNETİMİNDE: bu dosyayı elle düzenlemeyin. `diag/bangumi_master/roles.tsv`
 * güncellenip `python3 diag/bangumi_master/gen_bangumi_master.py` çalıştırılmalıdır.
 */
internal data class BangumiRoleEntry(
    /** `bangumi_role_<key>` dil dosyası anahtarı. */
    val key: String,
    val turkish: String,
    val english: String
)

internal object BangumiRoleDictionary {

    private class Group(val entry: BangumiRoleEntry, val labels: List<String>)

    private fun g(key: String, turkish: String, english: String, vararg labels: String) =
        Group(BangumiRoleEntry(key, turkish, english), labels.toList())

    // ── Tablo (üreteç tarafından yazılır) ────────────────────────────────────

    private val groups: List<Group> = listOf(
__ROWS__
    )

    // ── Eşleştirme ───────────────────────────────────────────────────────────

    private val byLabel: Map<String, Group> by lazy {
        val map = HashMap<String, Group>(groups.sumOf { it.labels.size } * 2)
        for (group in groups) {
            for (label in group.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                // Açık yazımlar önce işlendiği için bir kısaltma gerçek rolü gölgeleyemez.
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /** Rol yazımının sözlük girişi; bilinmiyorsa null. */
    fun entryFor(label: String?): BangumiRoleEntry? {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return byLabel[normalize(raw)]?.entry
    }

    /** Sözlükteki tüm yazımlar (test ve araç kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    /** Sözlükteki tüm girişler (test ve araç kullanımı için). */
    fun allEntries(): List<BangumiRoleEntry> = groups.map { it.entry }

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
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }
}
'''


def load_roles() -> list[tuple[str, str, str, list[str]]]:
    rows: list[tuple[str, str, str, list[str]]] = []
    seen: set[str] = set()
    for parts in read_tsv(ROLES_TSV, 4):
        key, tr, en, variants_raw = parts
        key = key.strip()
        if key in seen:
            raise SystemExit(f"roles.tsv: yinelenen anahtar '{key}'")
        seen.add(key)
        rows.append((key, tr.strip(), en.strip(), [v.strip() for v in variants_raw.split("|") if v.strip()]))
    return rows


def render_role_kt(roles: list[tuple[str, str, str, list[str]]]) -> str:
    lines = []
    for index, (key, tr, en, variants) in enumerate(roles):
        labels = ", ".join(kstr(v) for v in variants)
        comma = "," if index < len(roles) - 1 else ""
        lines.append(f"        g({kstr(key)}, {kstr(tr)}, {kstr(en)}, {labels}){comma}")
    return ROLE_KT_TEMPLATE.replace("__ROWS__", "\n".join(lines))


# ── BangumiTagDictionary.kt: MASTER ekleri ───────────────────────────────────

def find_groups_list_bounds(text: str) -> tuple[int, int]:
    """`private val groups: List<Group> = listOf(` bloğunun (başlangıç, kapanış) konumu."""
    marker = "private val groups: List<Group> = listOf("
    start = text.index(marker)
    depth = 0
    for index in range(start + len(marker) - 1, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return start, index
    raise SystemExit("BangumiTagDictionary.kt: groups listesi kapatılamadı")


def apply_tags(text: str, rows: list[tuple[str, str, str, list[str]]]) -> tuple[str, list[str], list[str]]:
    """
    Etiket eklerini uygular.

    Anahtar sözlükte yoksa → MASTER ekleri bloğuna yeni `g(...)` satırı eklenir.
    Anahtar varsa          → yalnızca EKSİK yazım varyantları mevcut gruba eklenir.

    Dönüş: (yeni metin, eklenen anahtarlar, varyant eklenen anahtarlar)
    """
    added_keys: list[str] = []
    extended_keys: list[str] = []
    new_groups: list[str] = []

    for key, tr, en, variants in rows:
        pattern = re.compile(r'^        g\("' + re.escape(key) + r'", .*$', re.MULTILINE)
        match = pattern.search(text)
        if match is None:
            labels = ", ".join(kstr(v) for v in variants)
            new_groups.append(
                f'        g({kstr(key)}, {kstr(tr)}, {kstr(en)}, {labels})'
            )
            added_keys.append(key)
            continue
        line = match.group(0)
        missing = [v for v in variants if f'"{v}"' not in line]
        if not missing:
            continue
        # Satır `)` ya da `),` ile bitebilir (liste içi ayraç). Önce ayraç soyulur.
        stripped = line.rstrip()
        had_comma = stripped.endswith(",")
        if had_comma:
            stripped = stripped[:-1].rstrip()
        if not stripped.endswith(")"):
            raise SystemExit(f"BangumiTagDictionary.kt: '{key}' grubu beklenmedik biçimde bitiyor")
        head = stripped[:-1].rstrip()
        if not head.endswith(","):
            head += ","
        updated = head + " " + ", ".join(kstr(v) for v in missing) + ")" + ("," if had_comma else "")
        text = text[:match.start()] + updated + text[match.end():]
        extended_keys.append(key)

    if new_groups:
        start, close = find_groups_list_bounds(text)
        # Kapanış parantezünden önceki SON ANLAMLI karakter bulunur: liste ayraçlı
        # olduğu için önceki elemanın sonuna virgül gerekir. Boşluk/sekme/satır sonu
        # soyulmadan bakılırsa virgül atlanır ve sözlük derlenmez (geçmiş hata).
        head = text[:close].rstrip()
        needs_comma = bool(head) and not head.endswith(",") and not head.endswith("(")
        block = (
            "\n\n" + TAG_BLOCK_MARK +
            " (üreteç: diag/bangumi_master/tags.tsv) ──\n" +
            ",\n".join(new_groups)
        )
        text = head + ("," if needs_comma else "") + block + "\n    " + text[close:]

    return text, added_keys, extended_keys


# ── strings.xml MASTER bloğu ─────────────────────────────────────────────────

def render_block(
    orgs: list[Org],
    tag_entries: list[tuple[str, str, str]],
    roles: list[tuple[str, str, str, list[str]]],
    lang: str,
    skip_keys: set[str],
) -> str:
    """
    MASTER bloğunu üretir. `lang`: 'tr' → Türkçe sütun, 'en' → İngilizce sütun.

    `skip_keys`, hedef dosyada blok DIŞINDA zaten tanımlı olan `bangumi_tag_*`
    anahtarlarıdır. Bunlar atlanır: aynı adı ikinci kez yazmak yinelenen kaynak
    adı üretir ve aapt2 derlemeyi reddeder.
    """
    lines = [
        "    " + BLOCK_BEGIN,
        f"    <!-- Üretim: python3 diag/bangumi_master/gen_bangumi_master.py · dil={lang} -->",
    ]
    active = [o for o in orgs if o.status == "active"]
    if active:
        lines.append(f"    <!-- Kurum adları ({len(active)} kayıt) -->")
    for org in active:
        value = org.tr if lang == "tr" else org.en
        lines.append(f'    <string name="bangumi_org_{org.key}">{xstr(value)}</string>')
    if roles:
        lines.append(f"    <!-- Kadro / karakter rolleri ({len(roles)} kayıt) -->")
    for key, tr, en, _variants in roles:
        value = tr if lang == "tr" else en
        lines.append(f'    <string name="bangumi_role_{key}">{xstr(value)}</string>')
    fresh = [t for t in tag_entries if f"bangumi_tag_{t[0]}" not in skip_keys]
    if fresh:
        lines.append(f"    <!-- Etiketler ({len(fresh)} yeni kayıt) -->")
    for key, tr, en in fresh:
        value = tr if lang == "tr" else en
        lines.append(f'    <string name="bangumi_tag_{key}">{xstr(value)}</string>')
    lines.append("    " + BLOCK_END)
    return "\n".join(lines)


def upsert_block(path: Path, block: str) -> str:
    text = path.read_text(encoding="utf-8")
    begin = re.escape(BLOCK_BEGIN.split(" (")[0])
    pattern = re.compile(
        r"[ \t]*" + begin + r".*?" + re.escape(BLOCK_END) + r"\n?",
        re.DOTALL,
    )
    if pattern.search(text):
        return pattern.sub(block + "\n", text, count=1)
    # Blok yoksa </resources> öncesine ekle.
    marker = "</resources>"
    index = text.rindex(marker)
    return text[:index] + block + "\n" + text[index:]


# ── Sözlükten etiket okuma (simetri kaynağı) ─────────────────────────────────

TAG_ENTRY_RE = re.compile(
    r'^        g\("(?P<key>[a-z0-9_]+)",\s*"(?P<tr>(?:[^"\\]|\\.)*)",\s*"(?P<en>(?:[^"\\]|\\.)*)"',
    re.MULTILINE,
)
EXISTING_STRING_RE = re.compile(r'<string name="(?P<name>[a-z0-9_]+)">')


def read_tag_entries(text: str) -> list[tuple[str, str, str]]:
    entries: list[tuple[str, str, str]] = []
    for match in TAG_ENTRY_RE.finditer(text):
        entries.append((match["key"], kotlin_unescape(match["tr"]), kotlin_unescape(match["en"])))
    return entries


def existing_resource_names(path: Path) -> set[str]:
    """Hedef dil dosyasında blok DIŞINDA zaten tanımlı kaynak adları."""
    text = path.read_text(encoding="utf-8")
    begin = BLOCK_BEGIN.split(" (")[0]
    start = text.find(begin)
    if start != -1:
        end = text.find(BLOCK_END, start)
        if end != -1:
            text = text[:start] + text[end + len(BLOCK_END):]
    return {m["name"] for m in EXISTING_STRING_RE.finditer(text)}


def sync_tag_values_from_text(text: str, wanted: dict[str, str]) -> tuple[str, int]:
    """[sync_tag_values] mantığının metin üzerinde çalışan biçimi."""
    updated = 0

    def repl(match: re.Match[str]) -> str:
        nonlocal updated
        name = match["name"]
        if name not in wanted:
            return match.group(0)
        new_value = xstr(wanted[name])
        if match["value"] == new_value:
            return match.group(0)
        updated += 1
        return f'<string name="{name}">{new_value}</string>'

    pattern = re.compile(r'<string name="(?P<name>bangumi_tag_[a-z0-9_]+)">(?P<value>.*?)</string>', re.DOTALL)
    return pattern.sub(repl, text), updated


def sync_tag_values(path: Path, wanted: dict[str, str]) -> tuple[str, int]:
    """
    Blok DIŞINDA kalan `bangumi_tag_*` kayıtlarının değerini sözlükle eşitler.

    Sözlük elle düzenlenen bir tablo olduğu için değer değişebiliyor
    (`Ters Harem` → `Kadın Haremi`). Dil dosyası eski değerde kalırsa K5
    doğrulaması kırılır ve arayüzde yanlış metin görünür. Yalnızca sözlükte
    karşılığı OLAN anahtarlar güncellenir; başka hiçbir satıra dokunulmaz.
    """
    return sync_tag_values_from_text(path.read_text(encoding="utf-8"), wanted)


def kotlin_unescape(value: str) -> str:
    if "\\" not in value:
        return value
    return value.replace('\\"', '"').replace("\\\\", "\\").replace("\\$", "$")


# ── Ana akış ─────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description="Bangumi MASTER sözlük üreticisi")
    parser.add_argument("--check", action="store_true", help="yazmadan denetle; fark varsa 1 döner")
    args = parser.parse_args()

    orgs = load_orgs()
    pending = [o for o in orgs if o.status == "pending"]
    tag_rows = load_tag_rows()
    roles = load_roles()

    tag_text = TAG_KT.read_text(encoding="utf-8")
    tag_text, added_keys, extended_keys = apply_tags(tag_text, tag_rows)
    tag_entries = read_tag_entries(tag_text)

    synced = 0
    strings_out: dict[Path, str] = {}
    for path, lang in ((STRINGS_TR, "tr"), (STRINGS_EN, "en")):
        wanted = {f"bangumi_tag_{k}": (tr if lang == "tr" else en) for k, tr, en in tag_entries}
        content = upsert_block(
            path, render_block(orgs, tag_entries, roles, lang, existing_resource_names(path))
        )
        content, updated = sync_tag_values_from_text(content, wanted)
        synced += updated
        strings_out[path] = content

    outputs: dict[Path, str] = {
        ORG_KT: render_org_kt(orgs),
        TAG_KT: tag_text,
        ROLE_KT: render_role_kt(roles),
        **strings_out,
    }

    active = [o for o in orgs if o.status == "active"]
    print("Bangumi MASTER üretimi")
    print(f"  kurum   : {len(active)} aktif / {len(pending)} pending · {sum(len(o.variants) for o in active)} yazım")
    print(f"  etiket  : {len(tag_entries)} grup (bu turda eklenen: {len(added_keys)}, varyant eklenen: {len(extended_keys)})")
    print(f"  rol     : {len(roles)} grup")
    if added_keys:
        print(f"    + anahtar : {', '.join(added_keys)}")
    if extended_keys:
        print(f"    ~ varyant : {', '.join(extended_keys)}")
    if pending:
        print(f"  pending : {', '.join(o.key for o in pending)} (kaynakta doğrulanmadı → dil dosyasına alınmadı)")
    if synced:
        print(f"  senkron : {synced} mevcut bangumi_tag_* değeri sözlükle eşitlendi")

    dirty = 0
    for path, content in outputs.items():
        current = path.read_text(encoding="utf-8") if path.exists() else ""
        if current != content:
            dirty += 1
            if args.check:
                print(f"  DEĞİŞECEK: {path.relative_to(ROOT)}")
            else:
                path.write_text(content, encoding="utf-8")
                print(f"  yazıldı  : {path.relative_to(ROOT)}")

    if args.check and dirty:
        print(f"--check: {dirty} dosya güncel değil → üreticiyi çalıştırın")
        return 1
    print("tamam: her şey güncel" if args.check else f"{len(outputs) - dirty} dosya zaten günceldi")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi MASTER doğrulayıcısı.

`gen_bangumi_master.py` üretiminin tutarlılığını denetler. Her kontrol, geçmişte
yaşanmış gerçek bir hatayı yakalamak için vardır:

  K1  sözlük yapısı      : `groups` / `orgs` listelerinde virgül ve parantez bütünlüğü
                           (geçmiş hata: son elemandan sonra virgülsüz ekleme → derlenmiyor)
  K2  anahtar tekilliği  : aynı `key` iki kayıtta olamaz
  K3  gölgelenme         : bir yazım iki farklı gruba eşlenemez (ilk kazanan belirsizleşir)
  K4  anahtar simetrisi  : values/ ve values-en/ aynı bangumi_* anahtar kümesine sahip olmalı
  K5  değer eşitliği     : TR dosyası sözlüğün TR sütununu, EN dosyası EN sütununu taşımalı
                           (geçmiş hata: EN dosyasına TR değerleri yazılmıştı)
  K6  TSV birebir        : orgs.tsv kayıtları üretilen sözlükle birebir örtüşmeli
  K7  kaynak adı geçerli : Android kaynak adı `[a-z_][a-z0-9_]*` olmalı
                           (rakamla başlayan / çift alt çizgili ad aapt2'yi kırar)

Çıkış kodu: 0 = tüm kontroller geçti, 1 = hata var.
"""
from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIAG = ROOT / "diag/bangumi_master"
UTILS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils"
ORG_KT = UTILS / "BangumiOrgDictionary.kt"
TAG_KT = UTILS / "BangumiTagDictionary.kt"
ROLE_KT = UTILS / "BangumiRoleDictionary.kt"
STRINGS_TR = ROOT / "app/src/main/res/values/strings.xml"
STRINGS_EN = ROOT / "app/src/main/res/values-en/strings.xml"

RESOURCE_NAME_RE = re.compile(r"^[a-z_][a-z0-9_]*$")
# Kotlin normal dizesinde geçerli kaçışlar. `\s`, `\d`, `\w` GİBİ regex kaçışları
# normal dizede GEÇERSİZDİR (ham dize `"""..."""` gerekir) — derleme hatasıdır.
VALID_KOTLIN_ESCAPES = {"t", "b", "n", "r", "f", "'", '"', "\\", "$", "u"}
ORG_ROW_RE = re.compile(
    r'^        o\("(?P<key>[^"]+)", BangumiOrgRole\.(?P<role>[A-Z]+), '
    r'"(?P<tr>(?:[^"\\]|\\.)*)", "(?P<en>(?:[^"\\]|\\.)*)"(?P<rest>.*)\)(?P<comma>,?)\s*$',
    re.MULTILINE,
)
TAG_ROW_RE = re.compile(
    r'^        g\("(?P<key>[^"]+)", "(?P<tr>(?:[^"\\]|\\.)*)", '
    r'"(?P<en>(?:[^"\\]|\\.)*)"(?P<rest>.*)\)(?P<comma>,?)\s*$',
    re.MULTILINE,
)
STRING_RE = re.compile(r'<string name="(?P<name>bangumi_[a-z0-9_]+)">(?P<value>.*?)</string>', re.DOTALL)
ANY_STRING_NAME_RE = re.compile(r'<(?:string|string-array)\s+name="(?P<name>[a-z0-9_A-Z]+)"')
LABEL_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Gruplar tek satırda ya da satıra yayılmış biçimde yazılabiliyor:
#   g("key", "tr", "en", "a", "b"),
#   g("key", "tr", "en",
#       "a", "b"),
# Bu yüzden anahtar/Değer başlığı ayrı, yazım kuyruğu ayrı yakalanır.
TAG_HEAD_RE = re.compile(
    r'^        g\("(?P<key>[a-z0-9_]+)",\s*"(?P<tr>(?:[^"\\]|\\.)*)",\s*"(?P<en>(?:[^"\\]|\\.)*)"',
    re.MULTILINE,
)
# Roller de g(...) biçimindedir; sözlük üreteç tarafından yazılır.
ROLE_HEAD_RE = TAG_HEAD_RE


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.passed: list[str] = []

    def ok(self, message: str) -> None:
        self.passed.append(message)

    def err(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)


# ── Kotlin sözlük okuma ──────────────────────────────────────────────────────

def kotlin_unescape(value: str) -> str:
    if "\\" not in value:
        return value
    return value.replace('\\"', '"').replace("\\\\", "\\").replace("\\$", "$")


def xml_unescape(value: str) -> str:
    return (
        value.replace("\\'", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", '"')
        .replace("&amp;", "&")
    )


def split_labels(rest: str) -> list[str]:
    """`g(...)` satırının vararg kuyruğundaki yazımları ayıklar."""
    body = rest.strip()
    if not body.startswith(","):
        return []
    return [kotlin_unescape(v) for v in LABEL_RE.findall(body[1:])]


def read_orgs() -> list[dict]:
    rows = []
    for match in ORG_ROW_RE.finditer(ORG_KT.read_text(encoding="utf-8")):
        rows.append({
            "key": match["key"],
            "role": match["role"],
            "tr": kotlin_unescape(match["tr"]),
            "en": kotlin_unescape(match["en"]),
            "labels": split_labels(match["rest"]),
            "comma": match["comma"],
        })
    return rows


def _tail_labels(text: str, start: int) -> list[str]:
    """Bir `g(` başlığından sonraki vararg kuyruğunu okur; satıra yayılmış olabilir."""
    depth = 0
    index = start
    depth = 1  # g( / o( açılış parantezi başlıkta tüketildi
    while index < len(text):
        char = text[index]
        if char == '"':
            index += 1
            while index < len(text) and text[index] != '"':
                if text[index] == "\\":
                    index += 1
                index += 1
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                break
        index += 1
    return [kotlin_unescape(v) for v in LABEL_RE.findall(text[start:index])]


def read_tags() -> list[dict]:
    text = TAG_KT.read_text(encoding="utf-8")
    rows = []
    for match in TAG_HEAD_RE.finditer(text):
        tail = _tail_labels(text, match.end())
        rows.append({
            "key": match["key"],
            "tr": kotlin_unescape(match["tr"]),
            "en": kotlin_unescape(match["en"]),
            "labels": tail,
            "comma": "",
        })
    check_list_commas(rows, TAG_HEAD_RE, text)
    return rows


def check_list_commas(rows: list[dict], head_re: re.Pattern, text: str) -> None:
    """
    Satıra yayılmış gruplarda virgül denetimi.

    Her `g(...)` kapanışından sonra ya `,` gelmeli ya da bu listenin SON elemanı
    olmalı. Geçmişte yaşanan hata tam olarak buydu: yeni grup son elemanın ardına
    virgülsüz eklenince sözlük derlenmiyordu.
    """
    matches = list(head_re.finditer(text))
    for position, match in enumerate(matches):
        index = match.end()
        depth = 1  # açılış parantezi başlıkta tüketildi
        while index < len(text):
            char = text[index]
            if char == '"':
                index += 1
                while index < len(text) and text[index] != '"':
                    if text[index] == "\\":
                        index += 1
                    index += 1
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    break
            index += 1
        after = text[index + 1:index + 3].lstrip()
        is_last = position == len(matches) - 1
        rows[position]["comma"] = "" if is_last else ("," if after.startswith(",") else "MISSING")


def read_roles() -> list[dict]:
    text = ROLE_KT.read_text(encoding="utf-8")
    rows = []
    for match in ROLE_HEAD_RE.finditer(text):
        rows.append({
            "key": match["key"],
            "tr": kotlin_unescape(match["tr"]),
            "en": kotlin_unescape(match["en"]),
            "labels": _tail_labels(text, match.end()),
            "comma": "",
        })
    check_list_commas(rows, ROLE_HEAD_RE, text)
    return rows


def read_strings(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    return {m["name"]: xml_unescape(m["value"]) for m in STRING_RE.finditer(text)}


# ── BangumiTagDictionary.normalize / compact birebir Python karşılığı ────────

_WRAPPERS = "（([【《「『"
_WRAPPERS_CLOSE = "）)]】》」』"
_SPACE_PUNCT = "：:・·。,，"


def kotlin_normalize(value: str) -> str:
    """Kotlin `BangumiTagDictionary.normalize` ile aynı davranış."""
    if not value.strip():
        return ""
    out: list[str] = []
    for ch in value:
        if ch == "　":
            out.append(" ")
        elif ch in _WRAPPERS or ch in _WRAPPERS_CLOSE:
            continue
        elif ch in _SPACE_PUNCT:
            out.append(" ")
        else:
            out.append(ch)
    text = "".join(out)
    text = re.sub(r"\s+", " ", text).strip()
    return text.lower()


def kotlin_compact(value: str) -> str:
    """Kotlin `compact`: normalize + tüm boşlukların silinmesi."""
    return re.sub(r"\s+", "", kotlin_normalize(value))


# ── Kontroller ───────────────────────────────────────────────────────────────

def check_structure(report: Report, name: str, rows: list[dict], source: Path) -> None:
    """K1: liste elemanlarının virgül bütünlüğü (son eleman hariç)."""
    if not rows:
        report.err(f"K1 {name}: {source.name} içinde hiçbir kayıt bulunamadı")
        return
    bad = [i for i, row in enumerate(rows[:-1]) if row["comma"] != ","]
    if bad:
        for index in bad[:5]:
            report.err(f"K1 {name}: '{rows[index]['key']}' kaydından sonra virgül yok → derlenmez")
    else:
        report.ok(f"K1 {name}: {len(rows)} kayıt, liste ayraçları tam")


def check_unique_keys(report: Report, name: str, rows: list[dict]) -> None:
    """K2: anahtar tekilliği."""
    seen: dict[str, int] = {}
    for row in rows:
        seen[row["key"]] = seen.get(row["key"], 0) + 1
    dupes = {k: c for k, c in seen.items() if c > 1}
    if dupes:
        for key, count in sorted(dupes.items()):
            report.err(f"K2 {name}: '{key}' {count} kez tanımlı")
    else:
        report.ok(f"K2 {name}: {len(seen)} anahtar benzersiz")


def check_shadowing(report: Report, name: str, rows: list[dict]) -> None:
    """K3: bir yazım iki farklı gruba eşlenmemeli (sözlük "ilk kazanan" der)."""
    owners: dict[str, str] = {}
    conflicts: dict[str, set[str]] = {}
    for row in rows:
        candidates = list(row["labels"]) + [row["en"], row["tr"]]
        for label in candidates:
            for key in (kotlin_normalize(label), kotlin_compact(label)):
                if len(key) < 1 or (key == kotlin_compact(label) and len(key) < 3):
                    continue
                owner = owners.setdefault(key, row["key"])
                if owner != row["key"]:
                    conflicts.setdefault(key, {owner, row["key"]})
    if conflicts:
        for key, keys in sorted(conflicts.items())[:10]:
            report.err(f"K3 {name}: '{key}' birden çok kayıtta: {sorted(keys)}")
    else:
        report.ok(f"K3 {name}: gölgelenme yok")


def check_resource_names(report: Report, name: str, rows: list[dict], prefix: str) -> None:
    """K7: Android kaynak adı geçerliliği."""
    bad = [r["key"] for r in rows if not RESOURCE_NAME_RE.match(f"{prefix}{r['key']}")]
    if bad:
        for key in bad[:10]:
            report.err(f"K7 {name}: geçersiz kaynak adı '{prefix}{key}'")
    else:
        report.ok(f"K7 {name}: {len(rows)} kaynak adı geçerli")


def check_kotlin_escapes(report: Report, name: str, path: Path) -> None:
    """
    K9: üretilen Kotlin dosyalarında geçersiz dize kaçışı denetimi.

    Geçmişte yaşanan hata: üreteç `Regex("\\s+")` yerine `Regex("\s+")` üretti —
    normal Kotlin dizesinde `\s` geçersiz kaçıştır ve dosya derlenmez. Python
    doğrulayıcı sözdizimini görmediği için bunu yalnızca kotlinc yakalamıştı; bu
    kontrol aynı hatayı JVM olmadan yakalar. Ham dize blokları denetim dışıdır.
    """
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    bad: list[str] = []
    for lineno, line in enumerate(text.splitlines(), start=1):
        # Ham dize parçalarını çıkar: onların içindeki \ geçerlidir.
        stripped = re.sub(r'""".*?"""', '""', line)
        index = 0
        while index < len(stripped):
            char = stripped[index]
            if char != '"':
                index += 1
                continue
            index += 1
            while index < len(stripped) and stripped[index] != '"':
                if stripped[index] == "\\":
                    if index + 1 < len(stripped):
                        esc = stripped[index + 1]
                        if esc not in VALID_KOTLIN_ESCAPES:
                            bad.append(f"{lineno}: `\\{esc}` → {line.strip()[:64]}")
                        index += 2
                        continue
                index += 1
            index += 1
    if bad:
        for entry in bad[:5]:
            report.err(f"K9 {name}: geçersiz Kotlin kaçışı {entry}")
    else:
        report.ok(f"K9 {name}: dize kaçışları geçerli")


def check_duplicate_resources(report: Report, label: str, path: Path) -> None:
    """K8: aynı kaynak adı bir dosyada iki kez tanımlanamaz (aapt2 derlemeyi reddeder)."""
    names: dict[str, int] = {}
    for match in ANY_STRING_NAME_RE.finditer(path.read_text(encoding="utf-8")):
        names[match["name"]] = names.get(match["name"], 0) + 1
    dupes = sorted(k for k, c in names.items() if c > 1)
    if dupes:
        for key in dupes[:10]:
            report.err(f"K8 {label}: '{key}' {names[key]} kez tanımlı (toplam {len(dupes)} mükerrer ad)")
    else:
        report.ok(f"K8 {label}: {len(names)} kaynak adı, mükerrer yok")


def main() -> int:
    report = Report()

    orgs = read_orgs()
    tags = read_tags()
    roles = read_roles() if ROLE_KT.exists() else []

    check_kotlin_escapes(report, "BangumiOrgDictionary", ORG_KT)
    check_kotlin_escapes(report, "BangumiRoleDictionary", ROLE_KT)
    check_kotlin_escapes(report, "BangumiTagDictionary", TAG_KT)
    check_duplicate_resources(report, "values/", STRINGS_TR)
    check_duplicate_resources(report, "values-en/", STRINGS_EN)
    check_structure(report, "org", orgs, ORG_KT)
    check_structure(report, "tag", tags, TAG_KT)
    if roles:
        check_structure(report, "role", roles, ROLE_KT)
    check_unique_keys(report, "org", orgs)
    check_unique_keys(report, "tag", tags)
    if roles:
        check_unique_keys(report, "role", roles)
    check_shadowing(report, "org", orgs)
    check_shadowing(report, "tag", tags)
    if roles:
        check_shadowing(report, "role", roles)
    check_resource_names(report, "org", orgs, "bangumi_org_")
    check_resource_names(report, "tag", tags, "bangumi_tag_")
    if roles:
        check_resource_names(report, "role", roles, "bangumi_role_")

    tr_strings = read_strings(STRINGS_TR)
    en_strings = read_strings(STRINGS_EN)

    # K4: anahtar simetrisi
    prefixes = ("bangumi_org_", "bangumi_tag_", "bangumi_role_")
    tr_keys = {k for k in tr_strings if k.startswith(prefixes)}
    en_keys = {k for k in en_strings if k.startswith(prefixes)}
    only_tr, only_en = tr_keys - en_keys, en_keys - tr_keys
    if only_tr or only_en:
        for key in sorted(only_tr)[:10]:
            report.err(f"K4 simetri: '{key}' yalnızca values/ içinde")
        for key in sorted(only_en)[:10]:
            report.err(f"K4 simetri: '{key}' yalnızca values-en/ içinde")
    else:
        report.ok(f"K4 simetri: {len(tr_keys)} bangumi_* anahtarı iki dosyada birebir")

    # K5: değer eşitliği (TR dosyası TR sütunu, EN dosyası EN sütunu)
    mismatch = 0
    for row in orgs:
        name = f"bangumi_org_{row['key']}"
        if tr_strings.get(name) != row["tr"]:
            report.err(f"K5 org TR: '{name}' = {tr_strings.get(name)!r}, sözlük {row['tr']!r}")
            mismatch += 1
        if en_strings.get(name) != row["en"]:
            report.err(f"K5 org EN: '{name}' = {en_strings.get(name)!r}, sözlük {row['en']!r}")
            mismatch += 1
    for row in roles:
        name = f"bangumi_role_{row['key']}"
        if tr_strings.get(name) != row["tr"]:
            report.err(f"K5 role TR: '{name}' = {tr_strings.get(name)!r}, sözlük {row['tr']!r}")
            mismatch += 1
        if en_strings.get(name) != row["en"]:
            report.err(f"K5 role EN: '{name}' = {en_strings.get(name)!r}, sözlük {row['en']!r}")
            mismatch += 1
    for row in tags:
        name = f"bangumi_tag_{row['key']}"
        if name not in tr_keys and name not in en_keys:
            continue  # blok dışı eski kayıtlar bu turun kapsamı dışında
        if tr_strings.get(name) != row["tr"]:
            report.err(f"K5 tag TR: '{name}' = {tr_strings.get(name)!r}, sözlük {row['tr']!r}")
            mismatch += 1
        if en_strings.get(name) != row["en"]:
            report.err(f"K5 tag EN: '{name}' = {en_strings.get(name)!r}, sözlük {row['en']!r}")
            mismatch += 1
    if mismatch == 0:
        report.ok("K5 değer eşitliği: dil dosyaları sözlük sütunlarıyla birebir")

    # K6: orgs.tsv ↔ sözlük birebir
    tsv = DIAG / "orgs.tsv"
    if tsv.exists():
        expected: dict[str, tuple[str, str, str, set[str]]] = {}
        for line in tsv.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 5:
                report.err(f"K6 orgs.tsv: eksik sütun → {line[:60]}")
                continue
            role, key, tr, en, variants = parts[0].strip(), parts[1].strip(), parts[2], parts[3], parts[4]
            if role.lower().startswith("pending"):
                expected[key] = ("PENDING", "", "", set())
                continue
            expected[key] = (role.upper(), tr.strip(), en.strip(), {v for v in variants.split("|") if v.strip()})
        actual = {r["key"]: (r["role"], r["tr"], r["en"], set(r["labels"])) for r in orgs}
        pending_keys = {k for k, v in expected.items() if v[0] == "PENDING"}
        active_expected = {k: v for k, v in expected.items() if k not in pending_keys}
        missing = set(active_expected) - set(actual)
        extra = set(actual) - set(active_expected)
        for key in sorted(missing):
            report.err(f"K6: '{key}' orgs.tsv içinde ama sözlükte yok")
        for key in sorted(extra):
            report.err(f"K6: '{key}' sözlükte var ama orgs.tsv içinde yok")
        for key in sorted(pending_keys & set(actual)):
            report.err(f"K6: '{key}' pending ama sözlüğe alınmış")
        differing = 0
        for key in sorted(set(active_expected) & set(actual)):
            if active_expected[key] != actual[key]:
                report.err(f"K6: '{key}' içeriği TSV ile uyuşmuyor")
                differing += 1
        if not missing and not extra and not differing and not (pending_keys & set(actual)):
            report.ok(f"K6: {len(active_expected)} kayıt orgs.tsv ile birebir ({len(pending_keys)} pending atlandı)")
    else:
        report.warn("K6: orgs.tsv bulunamadı, karşılaştırma atlandı")

    # Özet
    print("Bangumi MASTER doğrulayıcı")
    print(f"  kayıt   : {len(orgs)} kurum · {len(tags)} etiket grubu · {len(roles)} rol grubu")
    for line in report.passed:
        print(f"  ✅ {line}")
    for line in report.warnings:
        print(f"  ⚠️  {line}")
    for line in report.errors:
        print(f"  ❌ {line}")
    if report.errors:
        print(f"SONUÇ: {len(report.errors)} HATA")
        return 1
    print(f"SONUÇ: TÜM KONTROLLER GEÇTİ ({len(report.passed)} kontrol)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

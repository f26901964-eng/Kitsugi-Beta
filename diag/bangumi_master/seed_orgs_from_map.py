#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
orgs.tsv'yi repodaki GERÇEK veriden üretir (bir defalık tohumlama betiği).

Kaynak: app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt
        içindeki `studioLatinNameMap` (CJK ad → Latin ad, 117 eşleme).

Neden bu betik var:
  Uydurma kurum adı üretmemek için tohum verisi repoda zaten doğrulanmış olan
  haritadan türetilir. Latin ada göre gruplanır: aynı Latin ada işaret eden tüm
  CJK varyantları tek kayda iner (京都アニメーション + 京都动画 + 京阿尼 → kyoto_animation).

Çıktı: diag/bangumi_master/orgs.tsv
  role \t key \t tr \t en \t varyant1|varyant2|...

Rol sınıflandırması ROLE_HINTS ile yapılır; listede olmayanlar `studio` sayılır
(yanılgı payı bilinçlidir: bu betik yalnızca tohumlar, orgs.tsv elle düzeltilebilir).
"""
from __future__ import annotations

import re
import sys
import unicodedata
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRANSLATIONS = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt"
OUT = ROOT / "diag/bangumi_master/orgs.tsv"

# ── Rol ipuçları (Latin ad alt dizesi → rol) ─────────────────────────────────
# Bangumi infobox alanları: 动画制作 (stüdyo), 製作/出品 (yapımcı),
# 播放电视台 (yayıncı ağ), 出版社/连载杂志 (yayımcı / dergi).
ROLE_HINTS: list[tuple[str, str]] = [
    # yayıncı ağlar / TV kanalları
    ("nhk", "network"), ("tbs", "network"), ("fuji tv", "network"), ("nippon tv", "network"),
    ("tokyo mx", "network"), ("mbs", "network"), ("yomiuri", "network"), ("asahi", "network"),
    ("tv tokyo", "network"), ("tv asahi", "network"), ("wowow", "network"), ("bs11", "network"),
    ("tokyo broadcast", "network"), ("mainichi", "network"), ("cbc", "network"),
    ("kbs", "network"), ("at-x", "network"), ("animax", "network"), ("crunchyroll", "network"),
    ("netflix", "network"), ("amazon", "network"), ("disney+", "network"), ("hulu", "network"),
    ("abema", "network"), ("toei channel", "network"),
    # yayımcılar (manga/kitap)
    ("shueisha", "publisher"), ("kodansha", "publisher"), ("shogakukan", "publisher"),
    ("kadokawa shoten", "publisher"), ("ichijinsha", "publisher"), ("houbunsha", "publisher"),
    ("shinchosha", "publisher"), ("akita shoten", "publisher"), ("futabasha", "publisher"),
    ("hakusensha", "publisher"), ("tokuma shoten", "publisher"), ("media factory", "publisher"),
    ("square enix", "publisher"), ("mag garden", "publisher"), ("overlap", "publisher"),
    ("takeshobo", "publisher"), ("ichijin", "publisher"), ("ascii media", "publisher"),
    ("viz media", "publisher"), ("yen press", "publisher"),
    # dergiler
    ("weekly shonen jump", "magazine"), ("shonen jump", "magazine"), ("shonen magazine", "magazine"),
    ("shonen sunday", "magazine"), ("dengeki", "magazine"), ("manga time kirara", "magazine"),
    ("comic alive", "magazine"), ("manga life", "magazine"), ("young jump", "magazine"),
    ("young magazine", "magazine"), ("gangan", "magazine"), ("flowers", "magazine"),
    ("bessatsu", "magazine"), ("comic gardo", "magazine"),
    # yapımcılar (yapım komitesi üyeleri / müzik / dağıtım)
    ("aniplex", "producer"), ("bandai", "producer"), ("toho", "producer"), ("shochiku", "producer"),
    ("avex", "producer"), ("dentsu", "producer"), ("sotsu", "producer"), ("movic", "producer"),
    ("pony canyon", "producer"), ("king records", "producer"), ("lantis", "producer"),
    ("flyingdog", "producer"), ("nippon columbia", "producer"), ("starchild", "producer"),
    ("geneon", "producer"), ("warner", "producer"), ("sony", "producer"), ("universal", "producer"),
    ("bushiroad", "producer"), ("frontier works", "producer"), ("happinet", "producer"),
    ("marui", "producer"), ("tms music", "producer"), ("hobunsha", "producer"),
    ("nitroplus", "producer"), ("good smile", "producer"), ("atlas", "producer"),
    ("sega", "producer"), ("capcom", "producer"), ("namco", "producer"), ("konami", "producer"),
    ("square", "producer"), ("pokemon", "producer"), ("shogakukan-shueisha", "producer"),
    ("doga kobo", "studio"),
]

# Elle rol düzeltmeleri (Latin ad tam eşleşme → rol). İpuçlarının yanlış
# yakaladığı kayıtlar burada kesinleştirilir.
ROLE_OVERRIDES: dict[str, str] = {
    "Square Enix": "publisher",
    "Square Enix Holdings": "publisher",
    "Kodansha": "publisher",
    "Shueisha": "publisher",
    "Shogakukan": "publisher",
    "Toho": "producer",
    "Toho Animation": "producer",
    "Shochiku": "producer",
    "Movic": "producer",
    "NHK": "network",
    "TBS": "network",
    "WOWOW": "network",
    "Animax": "network",
    "Crunchyroll": "network",
    "Netflix": "network",
    # Sezgisel eşlemenin yanlış yakaladıkları (elle doğrulandı):
    "Asahi Production": "studio",   # 亜細亜堂 — animasyon stüdyosu, Asahi Broadcasting DEĞİL
    "Dengeki Bunko": "publisher",   # 電撃文庫 — ASCII Media Works baskı dizisi, dergi değil
    "Kadokawa Tsubasa Bunko": "publisher",  # 角川つばさ文庫 — çocuk kitabı baskı dizisi
    "Kadokawa Group": "producer",   # 角川グループ — holding, stüdyo değil
    "Square Enix": "publisher",
}

# Marka adları çevrilmez (politika): TR = EN. Yalnızca gerçekten Türkçede
# farklı yazılan genel adlar buraya alınır.
TR_OVERRIDES: dict[str, str] = {
    "Warner Bros.": "Warner Bros.",
    "DreamWorks Animation": "DreamWorks Animation",
}

PAIR_RE = re.compile(r'"((?:[^"\\]|\\.)*)"\s+to\s+"((?:[^"\\]|\\.)*)"')


_SIMPLE_ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "'": "'"}


def unescape(value: str) -> str:
    """Kotlin dize kaçışlarını geri çözer (\\n, \\t, \\", \\\\, \\uXXXX)."""
    if "\\" not in value:
        return value
    out: list[str] = []
    index = 0
    while index < len(value):
        char = value[index]
        if char != "\\" or index + 1 >= len(value):
            out.append(char)
            index += 1
            continue
        esc = value[index + 1]
        if esc == "u" and index + 5 < len(value) + 1:
            out.append(chr(int(value[index + 2:index + 6], 16)))
            index += 6
        else:
            out.append(_SIMPLE_ESCAPES.get(esc, esc))
            index += 2
    return "".join(out)


def load_pairs() -> "OrderedDict[str, list[str]]":
    """Latin ad → [CJK varyantlar] (dosyadaki sırayı korur)."""
    text = TRANSLATIONS.read_text(encoding="utf-8")
    start = text.index("private val studioLatinNameMap = mapOf(")
    depth = 0
    end = start
    for index in range(start, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                end = index
                break
    block = text[start:end]
    grouped: "OrderedDict[str, list[str]]" = OrderedDict()
    for source, target in PAIR_RE.findall(block):
        latin = unescape(target.strip())
        grouped.setdefault(latin, [])
        variant = unescape(source.strip())
        if variant and variant not in grouped[latin]:
            grouped[latin].append(variant)
    return grouped


def slugify(latin: str) -> str:
    """
    Latin adı snake_case dil dosyası anahtarına çevirir.

    Android kaynak adı kuralları uygulanır: ad `[a-z_][a-z0-9_]*` olmalı, yani
    rakamla başlayamaz ve boş/ardışık alt çizgi içeremez. `J.C.Staff` → `j_c_staff`
    gibi çift alt çizgi üreten adlar ve `8bit` gibi rakamla başlayan adlar
    burada düzeltilir; aksi halde aapt2 derlemeyi reddeder.
    """
    text = unicodedata.normalize("NFKD", latin)
    text = text.encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^A-Za-z0-9]+", "_", text).strip("_").lower()
    text = re.sub(r"_{2,}", "_", text)          # ardışık alt çizgileri teke indir
    text = re.sub(r"^_+", "", text)
    if text and text[0].isdigit():
        text = "_" + text                        # rakamla başlayan ad geçersizdir
    return text or "unknown"


def role_for(latin: str) -> str:
    if latin in ROLE_OVERRIDES:
        return ROLE_OVERRIDES[latin]
    lowered = latin.lower()
    for hint, role in ROLE_HINTS:
        if hint in lowered:
            return role
    return "studio"


def main() -> int:
    grouped = load_pairs()
    used_keys: dict[str, int] = {}
    rows: list[tuple[str, str, str, str, str]] = []
    for latin, variants in grouped.items():
        base = slugify(latin)
        count = used_keys.get(base, 0)
        used_keys[base] = count + 1
        key = base if count == 0 else f"{base}_{count + 1}"
        role = role_for(latin)
        turkish = TR_OVERRIDES.get(latin, latin)
        rows.append((role, key, turkish, latin, "|".join(variants)))

    role_order = {"studio": 0, "producer": 1, "network": 2, "publisher": 3, "magazine": 4}
    rows.sort(key=lambda row: (role_order.get(row[0], 9), row[1]))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as handle:
        handle.write("# role\tkey\ttr\ten\tvariants(| ayraçlı CJK/Japonca adlar)\n")
        handle.write("# Kaynak: KitsugiTranslations.kt studioLatinNameMap (gerçek eşlemeler).\n")
        handle.write("# Politika: marka adlarında TR = EN; uydurma çeviri yoktur.\n")
        for row in rows:
            handle.write("\t".join(row) + "\n")

    counts: dict[str, int] = {}
    for row in rows:
        counts[row[0]] = counts.get(row[0], 0) + 1
    print(f"orgs.tsv yazıldı: {OUT}")
    print(f"kayıt: {len(rows)}  |  eşleme: {sum(len(v) for v in grouped.values())}")
    for role in sorted(counts):
        print(f"  {role:9s}: {counts[role]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

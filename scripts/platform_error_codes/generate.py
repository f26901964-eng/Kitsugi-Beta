# -*- coding: utf-8 -*-
"""
Strings.xml + PlatformErrorCode.kt üretici.

Kullanım (repo kökünden):
    python3 scripts/platform_error_codes/generate.py

Yeni hata kodu eklemek için `error_table.py` içindeki CODES listesine kayıt ekleyip
bu scripti çalıştırın. Script idempotenttir: var olan string anahtarlarını atlar,
enum dosyasını ise baştan üretir.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(__file__))
from error_table import CODES, LABELS, PLATFORM_META, SEVERITY_ORDER  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TR_PATH = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
EN_PATH = os.path.join(ROOT, "app/src/main/res/values-en/strings.xml")
KT_PATH = os.path.join(
    ROOT, "app/src/main/java/com/kitsugi/animelist/data/platform/PlatformErrorCode.kt"
)


def esc(text: str) -> str:
    """Android string kaynağı kaçışları."""
    out = text.replace("&", "&amp;")
    out = out.replace("<", "&lt;").replace(">", "&gt;")
    out = out.replace("'", "\\'").replace('"', '\\"')
    return out


def existing_names(path):
    tree = ET.parse(path)
    return {el.get("name") for el in tree.getroot() if el.tag == "string"}


def build_entries():
    """(name, tr_text, en_text) üçlüleri."""
    out = []
    for c in CODES:
        k = c["key"]
        out.append((f"pf_err_{k}_title", c["tr"][0], c["en"][0]))
        out.append((f"pf_err_{k}_why", c["tr"][1], c["en"][1]))
        out.append((f"pf_err_{k}_fix", c["tr"][2], c["en"][2]))
    for name, tr, en in LABELS:
        out.append((name, tr, en))
    return out


SECTION_HEADER = """
    <!-- ══════════════════════════════════════════════════════════════════════
         LİSTE KAYNAĞI HATA KODLARI (PlatformErrorCatalog)
         AniList · MyAnimeList · Kitsu · Shikimori · Bangumi · TMDB · Simkl
         Her kod için: _title (başlık), _why (neden çıkar), _fix (ne yapmalı)
         Katalog: data/platform/PlatformErrorCatalog.kt
         ══════════════════════════════════════════════════════════════════════ -->
"""


def inject(path, lang_index):
    names = existing_names(path)
    lines = [SECTION_HEADER]
    added = 0
    for name, tr_text, en_text in build_entries():
        if name in names:
            print(f"  ! atlandı (var): {name}")
            continue
        text = tr_text if lang_index == 0 else en_text
        lines.append(f'    <string name="{name}">{esc(text)}</string>\n')
        added += 1
    if added == 0:
        # Idempotent: eklenecek yeni anahtar yoksa dosyaya hiç dokunma.
        return 0
    block = "".join(lines)
    with open(path, "r", encoding="utf-8") as fh:
        content = fh.read()
    idx = content.rfind("</resources>")
    if idx == -1:
        raise SystemExit(f"</resources> bulunamadı: {path}")
    content = content[:idx] + block + content[idx:]
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    return added


# ── Kotlin enum üretimi ─────────────────────────────────────────────────────
PLATFORM_LABEL_RES = {name: res for name, _prefix, res in PLATFORM_META}

HEADER = '''package com.kitsugi.animelist.data.platform

import androidx.annotation.StringRes
import com.kitsugi.animelist.R

/**
 * Kitsugi — liste kaynağı (platform) hata kodları.
 *
 * Amaç: AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB ve Simkl
 * entegrasyonlarında oluşan **her** hatanın (bilinen ya da bilinmeyen) sabit bir
 * kodu, kısa bir başlığı ve "neden çıktı / ne yapmalı" açıklaması olsun; böylece
 * kullanıcı hangi kaynağın nerede, ne şekilde ve neden hata verdiğini görebilir.
 *
 * Kod biçimi: `KTS-<PLATFORM>-<NNNN>`
 *  - `KTS-COM-####` tüm platformlar için ortak (ağ, HTTP, oturum, ayrıştırma…)
 *  - `KTS-ANL-####` AniList      · `KTS-MAL-####` MyAnimeList/Jikan
 *  - `KTS-KIT-####` Kitsu        · `KTS-SHK-####` Shikimori
 *  - `KTS-BGM-####` Bangumi      · `KTS-TMD-####` TMDB
 *  - `KTS-SMK-####` Simkl
 *
 * Metinler dil dosyasındadır (`values/strings.xml` = TR, `values-en/strings.xml` = EN),
 * cihaz dili hangisiyse o okunur. Bu enum yalnızca koda/metne yapısal referans tutar.
 *
 * Sınıflandırma mantığı: [PlatformErrorCatalog]
 */
enum class PlatformErrorCode(
    /** Kalıcı, kullanıcıya gösterilebilir hata kodu, örn. `KTS-MAL-3001`. */
    val code: String,
    /** Hatayı üreten liste kaynağı. */
    val platform: ListPlatform,
    /** Kullanıcıya gösterilen önem derecesi. */
    val severity: PlatformErrorSeverity,
    @StringRes val titleRes: Int,
    @StringRes val whyRes: Int,
    @StringRes val fixRes: Int,
    /** Aynı istek mantıklı bir beklemeden sonra tekrar denenebilir mi? */
    val retryable: Boolean,
) {
'''

FOOTER = '''
    ;

    /** Kodun platforma özel mi yoksa ortak mı olduğunu söyler. */
    val isPlatformSpecific: Boolean get() = platform != ListPlatform.GENERIC

    companion object {
        // lazy: enum sabitleri ile eşzamanlı başlatma sırası sorunlarını elemek için.
        private val byCode: Map<String, PlatformErrorCode> by lazy {
            entries.associateBy { it.code.uppercase() }
        }

        private val byPlatform: Map<ListPlatform, List<PlatformErrorCode>> by lazy {
            entries.groupBy { it.platform }
        }

        /** `KTS-MAL-3001` gibi bir kodu enum değerine çevirir. Bilinmiyorsa null. */
        fun fromCode(code: String?): PlatformErrorCode? {
            val key = code?.trim()?.uppercase() ?: return null
            byCode[key]?.let { return it }
            // Kodsuz ama numarası bilinen girdiler için toleranslı arama
            val digits = key.takeLastWhile { it.isDigit() }
            if (digits.isEmpty()) return null
            return entries.firstOrNull { it.code.endsWith(digits) && key.startsWith(it.code.substringBeforeLast('-')) }
        }

        /** Bir platforma ait tüm kodlar (ortak kodlar için [ListPlatform.GENERIC]). */
        fun forPlatform(platform: ListPlatform): List<PlatformErrorCode> =
            byPlatform[platform].orEmpty()

        /** Platformun kendi kodları + tüm ortak kodlar. */
        fun applicableTo(platform: ListPlatform): List<PlatformErrorCode> =
            forPlatform(ListPlatform.GENERIC) + forPlatform(platform)
    }
}

/** Hata kodunun kullanıcıya gösterilen önem derecesi. */
enum class PlatformErrorSeverity(@StringRes val labelRes: Int) {
    INFO(R.string.pf_err_severity_info),
    WARNING(R.string.pf_err_severity_warning),
    ERROR(R.string.pf_err_severity_error),
    CRITICAL(R.string.pf_err_severity_critical)
}
'''


def gen_kotlin():
    out = [HEADER]
    last_platform = None
    for c in CODES:
        plat = c["platform"]
        if plat != last_platform:
            title = {
                "GENERIC": "Ortak kodlar — her platformda geçerli (ağ / HTTP / oturum / ayrıştırma)",
                "ANILIST": "AniList",
                "MAL": "MyAnimeList + Jikan",
                "KITSU": "Kitsu",
                "SHIKIMORI": "Shikimori",
                "BANGUMI": "Bangumi",
                "TMDB": "TMDB",
                "SIMKL": "Simkl",
            }[plat]
            out.append(f"\n    // ── {title} ──\n")
            last_platform = plat
        key = c["key"].upper()
        out.append(
            f'    {key}(\n'
            f'        code = "KTS-{dict((p, pre) for p, pre, _r in PLATFORM_META)[plat]}-{c["key"].split("_")[1]}",\n'
            f'        platform = ListPlatform.{plat},\n'
            f'        severity = PlatformErrorSeverity.{c["severity"]},\n'
            f'        titleRes = R.string.pf_err_{c["key"]}_title,\n'
            f'        whyRes = R.string.pf_err_{c["key"]}_why,\n'
            f'        fixRes = R.string.pf_err_{c["key"]}_fix,\n'
            f'        retryable = {str(c["retryable"]).lower()}\n'
            f'    ),\n'
        )
    out.append(FOOTER)
    os.makedirs(os.path.dirname(KT_PATH), exist_ok=True)
    with open(KT_PATH, "w", encoding="utf-8") as fh:
        fh.write("".join(out))
    return len(CODES)


if __name__ == "__main__":
    n_tr = inject(TR_PATH, 0)
    n_en = inject(EN_PATH, 1)
    n_codes = gen_kotlin()
    print(f"TR string eklendi: {n_tr}")
    print(f"EN string eklendi: {n_en}")
    print(f"Kotlin kod sayısı: {n_codes}")

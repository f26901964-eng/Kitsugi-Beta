#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""chunk 006 karar üreteci: mekanik sınıflar + elle kararlar → karar TSV.

Girdi : bu klasördeki `auto.py` (mekanik) ve `manual/*.py` (elle) modülleri
Çıktı : `dec_006_<band>.tsv` (build_tag_chunk.py biçimi)

Karar yardımcıları (modüller bunları `d` nesnesi üzerinden çağırır):
    d.skip(i, note="")
    d.existing(i, cid)           # sözlük/kanon/kaynak değerleri otomatik kopyalanır
    d.global_(i, cid, en, tr=None)
    d.new(i, cid, tr, en)
    d.as_is(i, cid=None, value=None)
    d.label(i, cid, tr, en, origin, note="")   # serbest
"""
from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

SCRATCH = Path(__file__).resolve().parent
# vault kökü: bu klasör decisions/006_src altında da, scratch_006 altında da olabilir
VAULT = SCRATCH.parent
while not (VAULT / "tags.json").exists() and VAULT != VAULT.parent:
    VAULT = VAULT.parent
sys.path.insert(0, str(VAULT / "scripts"))

import tag_index as ti  # noqa: E402
from android_strings import unescape  # noqa: E402

BANDS = {"a": (60, 99), "b": (40, 59), "c": (30, 39), "d": (20, 29)}
TAGS = json.loads((VAULT / "tags.json").read_text(encoding="utf-8"))
GROUPS = ti.load_dictionary()
CANON = ti.load_canon()
TR_XML, EN_XML = ti.load_resource_values()
TR_XML = {k: unescape(v) for k, v in TR_XML.items()}
EN_XML = {k: unescape(v) for k, v in EN_XML.items()}
DICT = {g["id"]: g for g in GROUPS}


SNAPSHOT = SCRATCH / "records_006_tags.json"


def band_records(band: str) -> list[dict]:
    """Kayıt listesi: kararlar damgalandıktan sonra da aynı kalması için snapshot önceliklidir."""
    lo, hi = BANDS[band]
    if SNAPSHOT.exists():
        rows = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
    else:
        rows = [r for r in TAGS if not r.get("decision")]
    rows = [r for r in rows if lo <= (r.get("count") or 0) <= hi]
    rows.sort(key=lambda r: (-(r.get("count") or 0), r["key"]))
    return rows


def slug(value: str) -> str:
    text = unicodedata.normalize("NFKC", value).lower()
    text = re.sub(r"[^a-z0-9]+", "_", text).strip("_")
    text = re.sub(r"_{2,}", "_", text)
    if not text:
        return ""
    if not text[0].isalpha():
        text = "n_" + text
    return text


def existing_values(cid: str) -> tuple[str, str, str] | None:
    """(tr, en, origin) mevcut canon → xml → sözlük sırasıyla."""
    row = CANON.get(("tag", cid))
    if row:
        return row["tr"], row["en"], "existing"
    key = f"bangumi_tag_{cid}"
    if key in TR_XML:
        return TR_XML[key], EN_XML.get(key, TR_XML[key]), "existing"
    group = DICT.get(cid)
    if group:
        return group["tr"], group["en"], "existing"
    return None


class Decisions:
    def __init__(self, band: str):
        self.band = band
        self.records = band_records(band)
        self.out: dict[int, dict] = {}
        self.errors: list[str] = []
        self.auto = False
        self.from_auto: set[int] = set()

    # ── yardımcılar ──────────────────────────────────────────────────────────
    def _name(self, i: int) -> str:
        return self.records[i].get("tr") or ""

    def _put(self, i: int, d: dict) -> None:
        if not (0 <= i < len(self.records)):
            self.errors.append(f"indeks aralık dışı: {i}")
            return
        if i in self.out and self.out[i] != d and not self.auto and i not in self.from_auto:
            self.errors.append(f"çift karar: {i} {self._name(i)!r} "
                               f"{self.out[i]['cid']} → {d['cid']}")
        self.out[i] = d
        if self.auto:
            self.from_auto.add(i)

    def skip(self, i: int, note: str = "") -> None:
        self._put(i, {"cid": None, "tr": "", "en": "", "origin": "skip", "note": note})

    def existing(self, i: int, cid: str, note: str = "") -> None:
        values = existing_values(cid)
        if values is None:
            self.errors.append(f"{i} {self._name(i)!r}: existing {cid} bulunamadı")
            return
        tr, en, _origin = values
        self._put(i, {"cid": cid, "tr": tr, "en": en, "origin": "existing",
                      "note": note or f"mevcut {cid} grubu"})

    def global_(self, i: int, cid: str, en: str, tr: str | None = None, note: str = "") -> None:
        self._put(i, {"cid": cid, "tr": tr or en, "en": en, "origin": "global",
                      "note": note or "resmî/global ad"})

    def new(self, i: int, cid: str, tr: str, en: str, note: str = "") -> None:
        self._put(i, {"cid": cid, "tr": tr, "en": en, "origin": "new", "note": note})

    def as_is(self, i: int, cid: str | None = None, value: str | None = None,
              note: str = "") -> None:
        value = value if value is not None else self._name(i)
        cid = cid or slug(value)
        if not cid:
            self.errors.append(f"{i} {self._name(i)!r}: as_is için cid üretilemedi")
            return
        self._put(i, {"cid": cid, "tr": value, "en": value, "origin": "as_is",
                      "note": note or "Latin/CJK ad korundu (uydurma romanizasyon yok)"})

    # kısa takma adlar (elle karar dosyalarında)
    s = skip
    g = global_
    n = new
    e = existing
    a = as_is

    def sm(self, note: str, *indices) -> None:
        for i in indices:
            self.skip(i, note)

    def dump(self) -> str:
        lines = []
        for i in sorted(self.out):
            d = self.out[i]
            if d["cid"] is None:
                lines.append(f"{i}\tSKIP\t\t\t\t{d['note']}")
            else:
                lines.append(f"{i}\t{d['cid']}\t{d['tr']}\t{d['en']}\t{d['origin']}\t{d['note']}")
        return "\n".join(lines) + "\n"


def coverage(d: Decisions) -> list[int]:
    return [i for i in range(len(d.records)) if i not in d.out]


def main() -> int:
    band = sys.argv[1]
    d = Decisions(band)
    autopath = SCRATCH / "auto.py"
    if autopath.exists():
        d.auto = True
        exec(compile(autopath.read_text(encoding="utf-8"), str(autopath), "exec"),
             {"d": d, "re": re, "slug": slug, "band": band, "ti": ti,
              "CANON": CANON, "DICT": DICT, "TR_XML": TR_XML, "EN_XML": EN_XML,
              "existing_values": existing_values, "unicodedata": unicodedata})
        d.auto = False
    manual = sorted((SCRATCH / "manual").glob(f"*{band}*.py"))
    for path in manual:
        exec(compile(path.read_text(encoding="utf-8"), str(path), "exec"),
             {"d": d, "re": re, "slug": slug, "band": band})
    if d.errors:
        print(f"❌ {len(d.errors)} karar hatası:", file=sys.stderr)
        for e in d.errors[:30]:
            print("  - " + e, file=sys.stderr)
        return 1
    missing = coverage(d)
    if missing:
        (SCRATCH / f"manual_{band}.txt").write_text(
            "".join(f"{i}\t{d.records[i]['count']}\t{d._name(i)}\n" for i in missing),
            encoding="utf-8")
        print(f"❌ {len(missing)} kayıt kararsız ({len(d.records)} kayıt):", file=sys.stderr)
        for i in missing[:40]:
            print(f"  {i}\t{d.records[i]['count']}\t{d._name(i)}", file=sys.stderr)
        return 2
    out = SCRATCH / f"dec_006_{band}.tsv"
    out.write_text(d.dump(), encoding="utf-8")
    stats: dict[str, int] = {}
    for row in d.out.values():
        stats[row["origin"]] = stats.get(row["origin"], 0) + 1
    print(f"{band}: {len(d.records)} kayıt → {stats} → {out.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

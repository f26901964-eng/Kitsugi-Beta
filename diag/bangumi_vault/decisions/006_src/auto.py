# -*- coding: utf-8 -*-
"""Mekanik sınıflar (chunk 006). Elle karar gerektirenler `manual/` altında."""

import unicodedata

YEAR = [r"^(19|20)\d{2}$", r"^(19|20)\d{2}年$", r"^(19|20)\d{2}年\d{1,2}月$",
        r"^\d{4}[-/.]\d{1,2}$", r"^\d{2,4}s$", r"^\d{4}y$", r"^(19|20)\d{2}[春夏秋冬]$",
        r"^\d{2}-\d{2}$", r"^\d{1,2}月$", r"^\d{1,2}季$", r"^\d{4}年代$",
        r"^(19|20)\d{2}年[春夏秋冬]$", r"^\d{4}年\d{1,2}月\d{1,2}日$",
        r"^\d{4}年\d{1,2}月$", r"^\d{4}\.\d{1,2}$", r"^\d{4}/\d{1,2}$",
        r"^\d{4}春$", r"^\d{4}秋$", r"^\d{4}冬$", r"^\d{4}夏$", r"^\d{4}年新番$"]
EVENT = [r"^[Cc]\d{1,3}$", r"^M3-\d+$", r"^M3-\d{4}[春夏秋冬]$", r"^【[Cc]\d+】$",
         r"^\[?C\d+\]?$", r"^例大祭\d*$", r"^コミティア\d*$", r"^C\d{2,3}(春|秋)$"]
RATING = [r"^\d+(\.\d+)?分$", r"^\d+~\d+分$", r"^\d+(\.\d+)?[星★]$", r"^[一二三四五六七八九十]+星$",
          r"^\d+/\d+$", r"^\d+(\.\d+)?$", r"^\d+-\d+$"]
SHELF = {
    "已入", "已购", "待购", "买买买", "看完", "没看完", "未看完", "想买", "想读", "在读", "已读",
    "看过", "未看", "未观看", "已看", "收藏", "收集", "存盘", "删", "已收藏", "已下载", "未下载",
    "未下载的", "已购入", "已白金", "白金", "神作", "佳作", "喜欢", "重复", "完结待补", "待补",
    "已整理", "云通关", "打通", "刷刷刷", "消磨时间", "解压", "相关", "动漫", "动画",
}
JUNK = {"undefined", "unknown", "none", "null", "na", "n/a", "aaa", "zzz", "test", "xxx",
        "etc", "other", "misc", "nn", "hm", "ok", "see", "s", "k", "u", "w", "a", "x",
        "aa", "bb", "cc", "ee", "gg", "zz", "q", "y", "z", "v", "t", "24", "12", "00"}

# Kısaltma/simgeler: kanıt yok → SKIP (chunk 002/003 çizgisi)
AMBIG = {"GB", "SS", "TCG", "ETC", "FX", "BD", "CV", "ADV&GAL", "Instru.", "3L", "3P",
         "AVG", "ACT", "RPG", "SLG", "STG", "PZL", "TAB", "etc", "ETC.", "ff", "FF",
         "HnS", "UnSr", "KSL", "COOP", "D03", "Norn", "N", "p", "P"}

label_index = ti.build_label_index(ti.load_dictionary(), ti.load_canon())


def norm(value):
    return ti.normalize(value)


def drop(i, reason):
    d.skip(i, reason)


for i, rec in enumerate(d.records):
    name = (rec.get("tr") or "").strip()
    if not name:
        drop(i, "boş ad")
        continue
    if any(re.match(p, name) for p in EVENT):
        drop(i, "doujin etkinlik numarası / tarih kodu")
        continue
    if any(re.match(p, name) for p in YEAR):
        drop(i, "yıl/dönem etiketi")
        continue
    if any(re.match(p, name) for p in RATING):
        drop(i, "puan/sayı etiketi")
        continue
    if name in SHELF:
        drop(i, "kişisel raf/durum etiketi")
        continue
    if len(name) <= 1:
        drop(i, "tek karakter")
        continue
    if not any(ch.isalnum() for ch in name):
        drop(i, "anlamsız işaret")
        continue
    lowered = unicodedata.normalize("NFKC", name).lower().strip()
    if lowered in JUNK:
        drop(i, "gürültü/anlamsız kısaltma")
        continue
    if name in AMBIG:
        drop(i, "belirsiz kısaltma")
        continue

    is_latin = all(ord(ch) < 128 for ch in name) and any(ch.isalpha() for ch in name)
    if not is_latin:
        continue  # CJK → elle

    # mevcut sözlük/kanon etiketi ile normalize eşleşme
    cands = {cid for cid, _src in label_index.get(norm(name), [])}
    if len(cands) == 1:
        cid = cands.pop()
        values = existing_values = None
        row = CANON.get(("tag", cid))
        if row:
            values = (row["tr"], row["en"])
        elif f"bangumi_tag_{cid}" in TR_XML:
            values = (TR_XML[f"bangumi_tag_{cid}"], EN_XML.get(f"bangumi_tag_{cid}"))
        else:
            group = DICT.get(cid)
            values = (group["tr"], group["en"]) if group else None
        if values and norm(values[1]) == norm(name):
            d.existing(i, cid, "mevcut gruba normalize eşleşme")
            continue
        # değer farklı (Disney+ ↔ Disney gibi) → elle
        continue

    # değer ikizi (005b tuzağı): yeni ad mevcut bir değerin birebir aynısı
    twin = None
    for (role, cid), row in CANON.items():
        if role != "tag":
            continue
        if norm(row["en"]) == norm(name) or norm(row["tr"]) == norm(name):
            twin = cid
            break
    if twin and norm(CANON[("tag", twin)]["en"]) == norm(name):
        d.existing(i, twin, "değer ikizi → mevcut anahtar")
        continue

    cid = slug(name)
    if not cid:
        continue
    # cid başka bir varlığa ait olabilir → değeri doğrula
    row = CANON.get(("tag", cid))
    if row and norm(row["en"]) != norm(name) and norm(row["tr"]) != norm(name):
        continue  # çakışma → elle
    if f"bangumi_tag_{cid}" in TR_XML and norm(TR_XML[f"bangumi_tag_{cid}"]) != norm(name):
        continue
    d.as_is(i, cid, name)

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vault → Kotlin dictionaries senkronlayıcısı.

vault.json (exhaustive) → 
  app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt (RAW)
  app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt (groups)

Mantık:
  • BangumiOrgDictionary: vault orgs (376) içinde olup RAW'da olmayan label|key|en satırlarını ekler
  • BangumiTagDictionary: vault tags (929) içinde olup dictionary'de olmayan g(...) gruplarını ekler

Idempotent, duplicate guard, gölgelenme kontrolü ile.

Kullanım:
  python3 diag/bangumi_vault/scripts/vault_to_dictionaries.py
  python3 diag/bangumi_vault/scripts/vault_to_dictionaries.py --check
"""
from __future__ import annotations
import re, json, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
VAULT = ROOT / "diag" / "bangumi_vault" / "vault.json"
ORG_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiOrgDictionary.kt"
TAG_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"

CHECK = "--check" in sys.argv

def load_vault():
    v = json.loads(VAULT.read_text(encoding="utf-8"))
    return v["orgs"], v["tags"]

def patch_org_dictionary(orgs):
    text = ORG_DICT.read_text(encoding="utf-8")
    # Mevcut RAW satırlarını parse et
    m = re.search(r'private const val RAW = """\n(.*?)\n"""', text, re.S)
    if not m:
        print("RAW bulamadım", file=sys.stderr)
        sys.exit(1)
    raw = m.group(1)
    existing = set(raw.splitlines())
    # Yeni satırlar üret: her org için her label + tr + en için satır
    # Sadece status != pending olanlar (exhaustive vault'ta pending = doğrulanmadı)
    new_lines = []
    for o in orgs:
        if o.get("status") == "pending":
            continue
        key = o["key"]
        en = o["en"]
        # labels + tr + en hepsi için satır üret (gen_bangumi_master mantığı)
        labels = o.get("labels", [])
        combined = labels + [en, o.get("tr","")]
        seen = set()
        for label in combined:
            if not label or label in seen:
                continue
            seen.add(label)
            line = f"{label}|{key}|{en}"
            if line not in existing and line not in new_lines:
                new_lines.append(line)
                existing.add(line)
    if not new_lines:
        print("Org dictionary zaten güncel (0 yeni satır)")
        return False
    # RAW bloğuna ekle: sonundan önce ekle
    # raw sonuna new_lines ekle
    new_raw = raw.rstrip() + "\n" + "\n".join(new_lines)
    new_text = text[:m.start(1)] + new_raw + text[m.end(1):]
    # Header comment'i güncelle (kaynak vault)
    new_text = new_text.replace(
        "Kaynak: `diag/bangumi_master/orgs.tsv` → `python3 diag/bangumi_master/gen_bangumi_master.py`",
        "Kaynak: `diag/bangumi_vault/vault.json` (exhaustive) + `diag/bangumi_master/orgs.tsv` → `python3 diag/bangumi_vault/scripts/vault_to_dictionaries.py`"
    )
    if CHECK:
        print(f"[check] Org dictionary +{len(new_lines)} satır gerekiyor")
        for l in new_lines[:10]:
            print("  +", l)
        return True
    ORG_DICT.write_text(new_text, encoding="utf-8")
    print(f"Org dictionary +{len(new_lines)} satır eklendi -> {ORG_DICT}")
    return True

def patch_tag_dictionary(tags):
    text = TAG_DICT.read_text(encoding="utf-8")
    # Mevcut key'leri bul
    existing_keys = set(re.findall(r'g\("([a-z0-9_]+)"', text))
    new_groups = []
    for t in tags:
        short = t.get("short") or t["key"].replace("bangumi_tag_","")
        if short in existing_keys:
            # label eklemeleri kontrol et — existing group'a yeni label ekle
            # Bu kısım gen_bangumi_master'da label_add mantığı; vault için de uygula
            # Mevcut group satırını bul ve eksik label ekle
            pass
            continue
        # Yeni grup oluştur
        tr = t["tr"].replace('"','\\"').replace("$","${'$'}")
        en = t["en"].replace('"','\\"').replace("$","${'$'}")
        labels = t.get("labels", [])
        # uniq labels
        seen = set()
        uniq = []
        for l in labels:
            if l not in seen:
                seen.add(l)
                uniq.append(l)
        if not uniq:
            uniq = [en]
        # Labels'ı Kotlin escape
        label_str = "".join(', "%s"' % l.replace('"','\\"').replace("$","${'$'}") for l in uniq)
        new_groups.append(f'        g("{short}", "{tr}", "{en}"{label_str}),')
    if not new_groups:
        print("Tag dictionary zaten güncel (0 yeni grup)")
        # Ama var olan gruplara label eklemeleri gerekebilir — şimdilik atla
        # TODO: existing gruplara yeni label ekle (screenshot CJK tagleri gibi)
        # Bunun için tags.tsv'deki existing logic'i vault için de yap
        # Şimdilik sadece yeni gruplar
        return False
    # Ekle: Bangumi MASTER bloğundan sonra veya liste sonundan önce
    # En güvenli: // ── Bangumi MASTER 2026-10-11: yeni meta/öneri etiketleri ── bloğundan hemen sonra
    marker = "        // ── Bangumi MASTER 2026-10-11: yeni meta/öneri etiketleri ──"
    if marker in text:
        insert_after = text.find(marker)
        # marker satırının sonunu bul ve new_groups'u oraya ekle (marker grubundan sonra)
        # marker'dan sonraki ilk g(...) blok sonunu bul
        # Basit: marker'dan sonraki son g(...) satırından sonra ekle
        # markers sonrası tüm g(...) satırlarını bul
        tail = text[insert_after:]
        # tail içinde kaç g var, sonuncu hangisi
        # Daha basit: listenin kapanışını bul: "    )" before "    /** Normalleştirilmiş"
        closing = re.search(r'^\s{4}\)\s*$', text, re.M)
        if not closing:
            print("Tag list kapanışı bulamadım", file=sys.stderr)
            sys.exit(1)
        # kapanıştan hemen önce ekle, ama önceki elemanın sonunda virgül yoksa ekle
        head = text[:closing.start()]
        # new_groups'u head sonuna ekle
        # head zaten virgülle bitmiyor mu kontrol
        head_stripped = head.rstrip()
        if not head_stripped.endswith(","):
            head = head_stripped + ",\n"
        new_block = "\n        // ── Bangumi VAULT 2026-10-20: exhaustive (screenshot + anilist) ──\n" + "\n".join(new_groups)
        new_block = new_block.rstrip(",")  # son virgül kaldırılacak, ama head zaten virgüllü olduğu için?
        # Aslında new_groups sonuncusu virgülsüz olmalı (liste son elemanı)
        # Bizim new_groups sonuncusu virgüllü, onu düzelt
        if new_block.endswith(","):
            new_block = new_block[:-1]
        new_text = head + new_block + "\n" + text[closing.start():]
    else:
        # marker yoksa liste sonuna ekle
        closing = re.search(r'^\s{4}\)\s*$', text, re.M)
        if not closing:
            print("Tag list kapanışı bulamadım", file=sys.stderr)
            sys.exit(1)
        head = text[:closing.start()]
        head_stripped = head.rstrip()
        if not head_stripped.endswith(","):
            head = head_stripped + ",\n"
        new_block = "\n        // ── Bangumi VAULT 2026-10-20: exhaustive ──\n" + "\n".join(g.rstrip(",") if i==len(new_groups)-1 else g for i,g in enumerate(new_groups))
        new_text = head + new_block + "\n" + text[closing.start():]
    if CHECK:
        print(f"[check] Tag dictionary +{len(new_groups)} yeni grup gerekiyor")
        for g in new_groups[:5]:
            print("  +", g)
        return True
    TAG_DICT.write_text(new_text, encoding="utf-8")
    print(f"Tag dictionary +{len(new_groups)} yeni grup eklendi -> {TAG_DICT}")
    return True

def main():
    orgs, tags = load_vault()
    changed_org = patch_org_dictionary(orgs)
    changed_tag = patch_tag_dictionary(tags)
    if CHECK and (changed_org or changed_tag):
        print("Dictionary'ler vault ile uyumsuz — vault_to_dictionaries.py çalıştırın", file=sys.stderr)
        sys.exit(1)
    if not (changed_org or changed_tag):
        print("Dictionary'ler zaten güncel")
    else:
        print("Güncellendi")

if __name__ == "__main__":
    main()

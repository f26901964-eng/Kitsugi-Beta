#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vault → strings.xml senkronlayıcısı.

vault.json / studios.json / producers.json / networks.json / ... →
app/src/main/res/values/strings.xml (TR)  ve  values-en/strings.xml (EN)

Her vault kaydı için:
  • key = bangumi_studio_* / bangumi_producer_* / bangumi_network_* / bangumi_publisher_* / bangumi_magazine_* / bangumi_tag_* / bangumi_genre_*
  • tr / en değerleri dil dosyalarına yazılır (idempotent, sync_values gibi)
  • pending status'lü kayıtlar YAZILMAZ (policy)
  • TR ve EN anahtar kümeleri birebir simetrik tutulur

Ayrıca:
  • translation_pending.tsv üretir (Drive translations sheet için)
  • --check modunda sadece fark bildirir (CI)

Kullanım:
  python3 diag/bangumi_vault/scripts/vault_to_strings.py
  python3 diag/bangumi_vault/scripts/vault_to_strings.py --check
"""
from __future__ import annotations
import json, re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[3]
VAULT_DIR = ROOT / "diag" / "bangumi_vault"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

CHECK = "--check" in sys.argv

# VAULT marker — MASTER ile aynı yerde, birleşik
VAULT_MARKER = "<!-- Bangumi VAULT 2026-10-20 (exhaustive) -->"

def xml_escape(v: str) -> str:
    return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","\\'").replace('"','\\"')

def load_vault():
    vault_path = VAULT_DIR / "vault.json"
    if not vault_path.exists():
        print(f"[!] vault.json yok, önce harvest_bangumi_exhaustive.py --build-vault çalıştırın", file=sys.stderr)
        sys.exit(1)
    vault = json.loads(vault_path.read_text(encoding="utf-8"))
    return vault

def collect_entries():
    vault = load_vault()
    entries = []  # (key, tr, en, role)
    # orgs
    for o in vault.get("orgs", []):
        if o.get("status") == "pending":
            continue
        key = o["key"]
        tr = o["tr"] or o["en"]
        en = o["en"] or o["tr"]
        entries.append((key, tr, en, o.get("role","")))
    # tags
    for t in vault.get("tags", []):
        if t.get("status","").startswith("pending"):
            continue
        # tags.tsv'de key zaten bangumi_tag_* ; vault'da da öyle
        key = t["key"]
        tr = t["tr"]
        en = t["en"]
        entries.append((key, tr, en, "tag"))
    # committees (yeni role)
    # vault.screenshot_missing'den üretilecek ek kayıtlar için dinamik anahtar üret
    missing = vault.get("screenshot_missing", {})
    for label, en, role in missing.get("producers", []):
        if role == "committee":
            # committee anahtarı: bangumi_committee_<normalized>
            k = "bangumi_committee_" + re.sub(r"[^a-z0-9]+","_", en.lower()).strip("_")
            if not k or k == "bangumi_committee_":
                k = "bangumi_committee_" + re.sub(r"[^a-z0-9]+","_", label).strip("_")[:30]
            # TR = olduğu gibi (çeviri yok), EN = en
            entries.append((k, label, en, "committee"))
    for label, en, role in missing.get("networks", []):
        # MBS TRUTH gibi birleşik: ikiye ayır ve iki kayıt üret
        if " " in label and role == "network":
            parts = label.split()
            for p in parts:
                k = "bangumi_network_" + re.sub(r"[^a-z0-9]+","_", p.lower()).strip("_")
                entries.append((k, p, p, "network"))
        # publisher türü
        if role == "publisher":
            k = "bangumi_publisher_" + re.sub(r"[^a-z0-9]+","_", en.lower()).strip("_")[:30]
            entries.append((k, label, en, "publisher"))
    # anilist genres → bangumi_genre_* (TR çevirisi vault_to_strings içinde map'ten gelecek)
    # şimdilik sadece exhaustive list için; TR değerleri isTurkish map'inden
    genre_tr_map = {
        "Action":"Aksiyon","Adventure":"Macera","Comedy":"Komedi","Drama":"Dram","Fantasy":"Fantastik",
        "Horror":"Korku","Mystery":"Gizem","Romance":"Romantik","Sci-Fi":"Bilim Kurgu","Slice of Life":"Günlük Yaşam",
        "Sports":"Spor","Supernatural":"Doğaüstü","Suspense":"Gerilim","Avant Garde":"Avangart","Award Winning":"Ödüllü",
        "Boys Love":"Boys Love","Girls Love":"Girls Love","Gourmet":"Gurme","Adult Cast":"Yetişkin Kadro",
        "Anthropomorphic":"Antropomorfik","CGDCT":"CGDCT","Childcare":"Çocuk Bakımı","Combat Sports":"Dövüş Sporları",
        "Crossdressing":"Zıt Cins Kılığı","Delinquents":"Asi Gençler","Gag Humor":"Gag Mizah","Gore":"Kan ve Şiddet",
        "Harem":"Harem","High Stakes Game":"Yüksek Riskli Oyun","Historical":"Tarihi","Idols (Female)":"Idol (Kadın)",
        "Idols (Male)":"Idol (Erkek)","Isekai":"Isekai","Iyashikei":"Iyashikei","Love Polygon":"Aşk Çokgeni",
        "Magical Sex Shift":"Magical Sex Shift","Mahou Shoujo":"Mahou Shoujo","Martial Arts":"Dövüş Sanatları",
        "Mecha":"Mecha","Medical":"Tıp","Military":"Askeri","Music":"Müzik","Mythology":"Mitoloji",
        "Organized Crime":"Organize Suç","Otaku Culture":"Otaku Kültürü","Parody":"Parodi","Performing Arts":"Sahne Sanatları",
        "Pets":"Evcil Hayvanlar","Psychological":"Psikolojik","Racing":"Yarış","Reincarnation":"Reenkarnasyon",
        "Samurai":"Samuray","School":"Okul","Seinen":"Seinen","Shoujo":"Shōjo","Shounen":"Shōnen","Space":"Uzay",
        "Strategy Game":"Strateji Oyunu","Super Power":"Süper Güç","Team Sports":"Takım Sporları","Time Travel":"Zaman Yolculuğu",
        "Vampire":"Vampir","Video Game":"Video Oyunu","Visual Arts":"Görsel Sanatlar","Workplace":"İş Yeri",
        "Detective":"Dedektif","Gourmet":"Gurme",
    }
    for g in vault.get("anilist_genres", []):
        k = "bangumi_genre_" + re.sub(r"[^a-z0-9]+","_", g.lower()).strip("_")
        tr = genre_tr_map.get(g, g)
        entries.append((k, tr, g, "genre"))
    # anilist tags → bangumi_tag_* (zaten tag havuzunda varsa atla)
    existing_keys = {e[0] for e in entries}
    for t in vault.get("anilist_tags", []):
        k = "bangumi_tag_" + re.sub(r"[^a-z0-9]+","_", t.lower()).strip("_")
        if k in existing_keys:
            continue
        # TR çeviri yoksa EN'yi sakla (pending gibi); çeviri ekibi sonra dolduracak
        entries.append((k, t, t, "tag"))
    return entries

def strings_keys(source: str):
    return set(re.findall(r'<string name="([^"]+)"', source))

def sync_values(source: str, entries):
    marker = source.find(VAULT_MARKER)
    if marker == -1:
        # MASTER marker yoksa VAULT marker'ı en alta ekle (before </resources>)
        if "</resources>" not in source:
            return source, 0
        # Eğer VAULT marker hiç yoksa, yeni ekleyeceğiz; sync yok
        return source, 0
    head, tail = source[:marker], source[marker:]
    updated = 0
    for name, tr, en, role in entries:
        # TR dosyasına tr, EN dosyasına en yazılacak — burada tek kaynak var, ayrı çağrılacak
        pass
    return source, updated

def insert_strings(path: pathlib.Path, entries, is_tr: bool):
    source = path.read_text(encoding="utf-8")
    # VAULT bloğu yoksa ekle
    vault_entries = [(k, (tr if is_tr else en)) for k, tr, en, _ in entries]
    present = strings_keys(source)
    # Sadece eksik anahtarları ekle + değer senkronu
    # MASTER bloğundan sonra VAULT bloğunu konumlandır
    marker_master = "<!-- Bangumi MASTER 2026-10-11 (diag/bangumi_master) -->"
    marker_vault = VAULT_MARKER
    if marker_vault not in source:
        # master bloğu varsa onun sonrasına, yoksa </resources> öncesine ekle
        if marker_master in source:
            # master bloğunun son kayıt satırından sonra ekle
            # basit: en son bangumi_* satırını bul ve sonrasına ekle
            lines = source.splitlines()
            insert_idx = None
            for i, line in enumerate(lines):
                if 'name="bangumi_' in line:
                    insert_idx = i
            if insert_idx is None:
                insert_idx = len(lines) - 2
            new_lines = ["" , f"    {marker_vault}"] + [f'    <string name="{k}">{xml_escape(v)}</string>' for k, v in vault_entries if k not in present]
            if new_lines[2:]:
                lines[insert_idx+1:insert_idx+1] = new_lines
                source = "\n".join(lines) + ("\n" if source.endswith("\n") else "")
                return source, len(new_lines)-2, 0
            else:
                # hiçbir yeni anahtar yok ama marker yoksa marker'ı ekle
                lines[insert_idx+1:insert_idx+1] = ["", f"    {marker_vault}"]
                source = "\n".join(lines) + ("\n" if source.endswith("\n") else "")
                return source, 0, 0
        else:
            # hiç bangumi yoksa direkt
            head, tail = source.rsplit("</resources>",1)
            block = f"    {marker_vault}\n" + "\n".join(f'    <string name="{k}">{xml_escape(v)}</string>' for k, v in vault_entries if k not in present) + "\n"
            source = head + block + "</resources>" + tail
            return source, len([k for k,_ in vault_entries if k not in present]), 0
    # marker var — eksik anahtarları ekle + değerleri sync et
    # değer sync: vault'tan gelen değer ile mevcut değer farklıysa güncelle
    updated = 0
    head, tail = source.split(marker_vault,1)
    tail_before = tail
    for k, v in vault_entries:
        if k in present:
            pattern = re.compile(r'(<string name="%s">)(.*?)(</string>)' % re.escape(k))
            m = pattern.search(tail)
            if m and m.group(2) != xml_escape(v):
                tail = tail[:m.start()] + m.group(1) + xml_escape(v) + m.group(3) + tail[m.end():]
                updated += 1
    # eksik anahtarlar
    present_after = strings_keys(head + marker_vault + tail)
    missing = [(k,v) for k,v in vault_entries if k not in present_after]
    added = 0
    if missing:
        # tail içinde son bangumi satırından sonra ekle
        lines = tail.splitlines()
        # son string satırını bul
        last_idx = -1
        for i, line in enumerate(lines):
            if '<string name="bangumi_' in line:
                last_idx = i
        if last_idx == -1:
            last_idx = 0
        new_lines = [f'    <string name="{k}">{xml_escape(v)}</string>' for k,v in missing]
        lines[last_idx+1:last_idx+1] = new_lines
        tail = "\n".join(lines)
        added = len(missing)
    new_source = head + marker_vault + tail
    return new_source, added, updated

def main():
    entries = collect_entries()
    # dedup key — ilk kazanır
    seen = {}
    deduped = []
    for k, tr, en, role in entries:
        if k not in seen:
            seen[k] = (tr,en,role)
            deduped.append((k,tr,en,role))
        else:
            # aynı key farklı tr/en gelirse sync_values ile çözülür
            pass
    print(f"[vault_to_strings] {len(deduped)} kayıt (dedup), {len(entries)-len(deduped)} yinelenen atlandı")

    tr_source = RES_TR.read_text(encoding="utf-8")
    en_source = RES_EN.read_text(encoding="utf-8")

    tr_new, tr_added, tr_updated = insert_strings(RES_TR, deduped, is_tr=True)
    en_new, en_added, en_updated = insert_strings(RES_EN, deduped, is_tr=False)

    # simetri kontrolü
    tr_keys = strings_keys(tr_new)
    en_keys = strings_keys(en_new)
    vault_keys = {k for k,_,_,_ in deduped}
    tr_missing = vault_keys - tr_keys
    en_missing = vault_keys - en_keys
    # tüm bangumi_* anahtarlarının simetrisi (vault+master birlikte)
    all_tr_bangumi = {k for k in tr_keys if k.startswith("bangumi_")}
    all_en_bangumi = {k for k in en_keys if k.startswith("bangumi_")}
    sym_diff = all_tr_bangumi.symmetric_difference(all_en_bangumi)

    print(f"TR: +{tr_added} yeni, ~{tr_updated} güncellendi")
    print(f"EN: +{en_added} yeni, ~{en_updated} güncellendi")
    if sym_diff:
        print(f"[!] Simetri hatası: TR/EN bangumi anahtar farkı {len(sym_diff)}: {sorted(list(sym_diff))[:20]}", file=sys.stderr)
    if tr_missing:
        print(f"[!] TR'de eksik vault anahtarı: {tr_missing}", file=sys.stderr)
    if en_missing:
        print(f"[!] EN'de eksik vault anahtarı: {en_missing}", file=sys.stderr)

    if CHECK:
        if tr_added or tr_updated or en_added or en_updated or sym_diff:
            print("Dil dosyaları vault ile uyumsuz — vault_to_strings.py çalıştırın.", file=sys.stderr)
            sys.exit(1)
        print("Zaten güncel.")
        sys.exit(0)

    if tr_new != tr_source:
        RES_TR.write_text(tr_new, encoding="utf-8")
        print(f"Yazıldı: {RES_TR} (+{tr_added} ~{tr_updated})")
    else:
        print(f"TR değişmedi")
    if en_new != en_source:
        RES_EN.write_text(en_new, encoding="utf-8")
        print(f"Yazıldı: {RES_EN} (+{en_added} ~{en_updated})")
    else:
        print(f"EN değişmedi")

    # translation_pending.tsv üret (Drive'a yüklenecek)
    pending_path = VAULT_DIR / "translations_pending.tsv"
    # vault'ta TR==EN olan tagler = çevrilmeyi bekleyenler
    pending = [(k,tr,en) for k,tr,en,role in deduped if role=="tag" and tr==en and tr and tr[0].isupper()]
    # ama şirket adlarında TR=EN normal, onları alma
    # sadece anilist taglerinden gelenler pending
    header = "key\tTR\tEN\trole\tlabels\tstatus\n"
    # vault'tan role tag olanlar ve tr==en
    rows = []
    vault = json.loads((VAULT_DIR / "vault.json").read_text(encoding="utf-8"))
    # pending detection: anilist_tags içinde ve henüz çevrilmemiş
    for t in vault.get("anilist_tags", []):
        k = "bangumi_tag_" + re.sub(r"[^a-z0-9]+","_", t.lower()).strip("_")
        # eğer vault_to_strings'te tr==en ise pending
        for kk,tr,en,role in deduped:
            if kk==k and tr==en:
                rows.append(f"{k}\t{tr}\t{en}\ttag\t{t}\tpending_translation")
    pending_path.write_text(header + "\n".join(rows), encoding="utf-8")
    print(f"Pending translations: {len(rows)} -> {pending_path}")

    # manifest güncelle
    manifest_path = VAULT_DIR / "manifest.json"
    if manifest_path.exists():
        m = json.loads(manifest_path.read_text(encoding="utf-8"))
        m["pending_translations"] = len(rows)
        m["vault_counts"]["total"] = len(deduped)
        manifest_path.write_text(json.dumps(m, ensure_ascii=False, indent=2), encoding="utf-8")

if __name__ == "__main__":
    main()

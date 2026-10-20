#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Exhaustive harvest from Bangumi-Subject github dump (/tmp/Bangumi-Subject/data)
→ updates vault.json + splits

Bu script bgm.tv canlı API yerine github dump'ını kullanarak TÜM
yapımcı / yayıncı ağ / stüdyo / yayımcı / dergi / etiket verisini çeker.
Sandbox allowlist (github/codeload) içinde çalışır, bgm.tv'ye gerek yok.
"""
from __future__ import annotations
import json, re, pathlib, collections, sys, time
from html import unescape

ROOT = pathlib.Path(__file__).resolve().parents[3]
VAULT_DIR = ROOT / "diag" / "bangumi_vault"
DUMP_ROOT = pathlib.Path("/tmp/Bangumi-Subject/data")
# Fallback if not exists
if not DUMP_ROOT.exists():
    DUMP_ROOT = pathlib.Path("/tmp/Bangumi-Subject/Bangumi-Subject/data")

# ── Info → role mapping (exhaustive, see scan of 20k dump) ────────────────
INFO_ROLE_MAP = {
    # studio
    "动画制作": "studio",
    "动画製作": "studio",
    "动画工房": "studio",
    "制作公司": "studio",  # anime studio / game dev
    "开发": "studio",
    "开发商": "studio",
    # producer (genel yapım)
    "制作": "producer",
    "製作": "producer",
    "企画": "producer",
    "企划": "producer",
    "出品": "producer",
    "出品人": "producer",
    "制片人": "producer",
    "制作人": "producer",
    "制片人/制作人": "producer",
    "制作协力": "producer",
    "制作進行": "producer",
    "制作进行": "producer",
    "动画制片人": "producer",
    "制作管理": "producer",
    "制作总指挥": "producer",
    "总制片": "producer",
    "总制片人": "producer",
    "制片": "producer",
    "监制": "producer",
    "監修": "producer",
    "监修": "producer",
    "执行制片人": "producer",
    "制作助手": "producer",
    "制作助理": "producer",
    "协力": "producer",
    # network
    "播放电视台": "network",
    "电视台": "network",
    "其他电视台": "network",
    "频道": "network",
    "电视网": "network",
    "播放频道": "network",
    "放送": "network",
    "放送电视台": "network",
    # publisher
    "出版社": "publisher",
    "其他出版社": "publisher",
    "发行商": "publisher",
    "发行": "publisher",
    "发行公司": "publisher",
    "出版": "publisher",
    "出版者": "publisher",
    "出版方": "publisher",
    "发行时间": "publisher",
    "厂牌": "publisher",
    "出版社": "publisher",
    "文库": "publisher",
    "社团": "publisher",
    "Circle": "publisher",
    "游戏出版商": "publisher",
    "出版方": "publisher",
    # magazine
    "连载杂志": "magazine",
    "连载": "magazine",
    "杂志": "magazine",
    "文庫": "publisher",
}

# Staff desc → role (çoğu aynı)
STAFF_ROLE_MAP = {
    "动画制作": "studio",
    "开发": "studio",
    "开发商": "studio",
    "制作": "producer",
    "製作": "producer",
    "企画": "producer",
    "企划": "producer",
    "制作协力": "producer",
    "制片人": "producer",
    "制作人": "producer",
    "出版社": "publisher",
    "出版": "publisher",
    "发行": "publisher",
    "发行商": "publisher",
    "厂牌": "publisher",
    "连载杂志": "magazine",
    "连载": "magazine",
    "出版社": "publisher",
}

# Hangi info key'leri org sayılır (dışındakiler kişi/tekil)
ORG_INFO_KEYS = set(INFO_ROLE_MAP.keys())

# Ayırıcılar
SPLIT_RE = re.compile(r'[、，,／/・\|\s]+')
CJK_SPLIT = re.compile(r'[、，,／/]+')

def clean_label(s: str) -> str:
    s = unescape(s)
    # HTML tag'leri temizle
    s = re.sub(r'<[^>]+>', '', s)
    s = s.strip()
    # Tam genişlik noktalama normalize
    s = s.replace('　',' ').replace('（',' ').replace('）',' ')
    s = re.sub(r'\s+', ' ', s).strip()
    return s

def split_values(raw: str) -> list[str]:
    # raw zaten HTML içinden ayıklanmış text
    # Önce <a> etiketlerini zaten temizledik, kalan text'i ayır
    raw = raw.strip()
    # Eğer "A、B、C" gibi ayracı varsa böl
    # Önce HTML'i temizle
    text = re.sub(r'<[^>]+>', '、', raw)
    text = unescape(text)
    # Yaygın ayracılar
    parts = re.split(r'[、，,／/·・|]+', text)
    out=[]
    for p in parts:
        p = clean_label(p)
        if not p or len(p)<1: continue
        # Tek karakterli Japonca parçalar genelde anlamsız değil, ama 1-karakterli tagler olabilir, yine al
        # Sayı sadece ise (ISBN gibi) atla? Ama org için sayı anlamsız
        if re.fullmatch(r'[0-9\-]+', p): continue
        if len(p)>80: continue # çok uzun summary değil
        out.append(p)
    return out

def extract_info_orgs(info_html: str):
    """info HTML'den (key, [values]) listesi çıkar, sadece ORG keyleri."""
    res=[]
    # <li><span>key: </span>value</li>
    for m in re.finditer(r'<li><span>([^<:：]+?)\s*[:：]\s*</span>(.*?)</li>', info_html, re.S):
        key = m.group(1).strip()
        val_html = m.group(2).strip()
        if key not in ORG_INFO_KEYS:
            continue
        role = INFO_ROLE_MAP.get(key)
        values = split_values(val_html)
        for v in values:
            # Bazı değerler tarih/sayı vs olabilir, org gibi durmayanları ele
            # Org adları en az 2 karakter ve harf içerir
            if len(v) < 2: continue
            if re.fullmatch(r'[0-9\.\-年/月日]+', v): continue
            if v in ("是","否","无","无","其它"): continue
            res.append((key, role, v))
    return res

def extract_staff_orgs(staff_list):
    res=[]
    for s in staff_list or []:
        desc = (s.get("desc") or "").strip()
        name = (s.get("name") or "").strip()
        if not name or not desc: continue
        role = STAFF_ROLE_MAP.get(desc)
        if not role: continue
        # Kişi adları da org gibi gelebilir ama staff'ta kişi/org karışık; sadece org rolündekileri al
        # Eğer desc "原画" gibi kişi rolü ise zaten map'te yok, atlanır
        name = clean_label(name)
        if len(name)<2: continue
        res.append((desc, role, name))
    return res

def slugify(s: str) -> str:
    # basit slug: lowercase, alfanumerik, _ ile
    import unicodedata
    s = s.lower()
    # CJK'yi koru? vault key için ascii slug lazım, CJK'yi pinyin değil direkt hash? Mevcut vault'ta CJK keyler var mı?
    # Mevcut vault'ta keyler hep ascii: bangumi_producer_tbs gibi. CJK isimler label'da duruyor, key ascii.
    # O yüzden CJK org'ları için latin name üzerinden slug üretmeliyiz, ama dump'ta latin name yok.
    # Dump'taki isimler CJK; onları slug'a çevirmek için basit transliteration: sadece ascii harfleri bırak, CJK'yi at ve hash ekle?
    # Mevcut vault'ta CJK isimlerin key'leri nasıl? Örn 光坂高校演劇部 -> bangumi_producer_committee_kosaka_high (latin tabanlı)
    # Dump'ta latin yoksa, CJK'yi doğrudan key'de kullanamayız. En iyisi: CJK string'i normalize edip, eğer ascii yoksa pinyin yerine ilk 8 hash?
    # Ama daha basit: mevcut vault'taki CJK org'ların çoğu elle latin key verilmiş. Dump'tan gelen yeni CJK'lar için key'i CJK'yi temizleyip
    # eğer ascii kalmıyorsa, uniq hash kullan.
    # Önce mevcut vault'taki mapping'i koruyacağız, yeni key üretirken çakışmayı önleyeceğiz.
    s_ascii = re.sub(r'[^a-z0-9]+', '_', s.lower())
    s_ascii = s_ascii.strip('_')
    if not s_ascii:
        # CJK only -> hash
        import hashlib
        h = hashlib.md5(s.encode('utf-8')).hexdigest()[:6]
        s_ascii = f"cjk_{h}"
    # çok uzun kısalt
    if len(s_ascii) > 30:
        s_ascii = s_ascii[:30].strip('_')
    return s_ascii

def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", default=str(DUMP_ROOT))
    ap.add_argument("--build-vault", action="store_true")
    ap.add_argument("--limit", type=int, default=0, help="sadece N dosya tara (test)")
    args = ap.parse_args()
    dump_root = pathlib.Path(args.dump)
    if not dump_root.exists():
        print(f"dump bulunamadı: {dump_root}", file=sys.stderr)
        sys.exit(1)
    files = list(dump_root.rglob("*.json"))
    print(f"Dump dosyaları: {len(files)}  ({dump_root})")
    if args.limit:
        files = files[:args.limit]
        print(f"Limit: {args.limit}")

    org_counter = collections.Counter()  # (role, name) -> count
    tag_counter = collections.Counter()
    # Ayrıca raw mapping: (role, name) -> set of source ids
    org_sources = collections.defaultdict(set)
    tag_sources = collections.defaultdict(set)

    # Hız için tek thread, 340k dosya ~60s
    import time, json
    t0=time.time()
    n=0
    for p in files:
        n+=1
        if n % 50000 == 0:
            print(f"  {n}/{len(files)}  orgs {len(org_counter)} tags {len(tag_counter)}  {time.time()-t0:.1f}s")
        try:
            j = json.load(open(p, encoding='utf-8'))
        except: continue
        sid = j.get("id")
        # info orgs
        info = j.get("info","")
        if info:
            for key, role, name in extract_info_orgs(info):
                org_counter[(role, name)] += 1
                if len(org_sources[(role,name)]) < 3:
                    org_sources[(role,name)].add(str(sid))
        # staff orgs
        for desc, role, name in extract_staff_orgs(j.get("staff",[])):
            org_counter[(role, name)] += 1
            if len(org_sources[(role,name)]) < 3:
                org_sources[(role,name)].add(str(sid))
        # tags
        for t in j.get("tags",[]) or []:
            name = (t.get("name") or "").strip()
            if not name: continue
            # tags için çok kısa veya sayısal tagler de olabilir ama al
            if len(name) > 80: continue
            if name == "": continue
            # count'u kullanabiliriz ama sadece isim yeterli
            tag_counter[name] += 1
            if len(tag_sources[name]) < 3:
                tag_sources[name].add(str(sid))

    print(f"Tarama bitti {n} dosya, {time.time()-t0:.1f}s")
    print(f"Unique org (role,name): {len(org_counter)}")
    for role in ["studio","producer","network","publisher","magazine"]:
        print(f"  {role:10s} {len([k for k in org_counter if k[0]==role])}")
    print(f"Unique tags: {len(tag_counter)}")

    # Vault güncelleme
    if not args.build_vault:
        print("Sadece sayım yapıldı, --build-vault ile vault'a yazılır")
        return

    # Mevcut vault'u oku
    vault_path = VAULT_DIR / "vault.json"
    if vault_path.exists():
        vault = json.load(open(vault_path, encoding='utf-8'))
    else:
        vault = {"meta":{"version":"1.0.0-exhaustive","date":"2026-10-20","source":"github dump","policy":"no_fabricated_translation"}, "orgs":[], "tags":[], "genres":[], "anilist_tags":[]}
    # Mevcut org keys set
    existing_org_keys = set(o["key"] for o in vault.get("orgs",[]))
    existing_tag_shorts = set(t["short"] for t in vault.get("tags",[]))

    # Yardımcı: key üretme çakışma önleme
    def make_org_key(role, name, existing):
        prefix = {"studio":"bangumi_studio_","producer":"bangumi_producer_","network":"bangumi_network_","publisher":"bangumi_publisher_","magazine":"bangumi_magazine_"}[role]
        base = slugify(name)
        # CJK hash ise prefix + cjk_xxx olur
        key = prefix + base
        # committees için özel prefix? Mevcut vault'ta committees ayrı ama key hala producer prefix
        # Eğer isim "制作委员会" içeriyorsa committee say, ama key yine producer_committee
        if "制作委员会" in name or "製作委員会" in name or "製作委員会" in name or "Committee" in name or "PROJECT" in name:
            # committee ise producer_committee_ prefix kullan
            base2 = slugify(name.replace("制作委员会","").replace("製作委員会","").strip() or name)
            key = "bangumi_producer_committee_" + base2
        # çakışma varsa suffix ekle
        orig=key
        i=2
        while key in existing:
            key = f"{orig}_{i}"
            i+=1
        return key

    # tag short üretme (mevcut harvest script ile aynı mantık: slug)
    def make_tag_short(name):
        # aynı slugify ama tag için daha tolerant
        s = name.lower()
        # yüzdeli tagler zaten temizlenmiş değil, dump'ta yüzdeli değil, direkt isim
        # boşlukları _ yap
        s = re.sub(r'[^a-z0-9\u4e00-\u9fff]+', '_', s) # CJK'yi koru? ama short hep ascii olmalı (mevcut vault'ta short ascii)
        # Eğer CJK içeriyorsa, onu da ascii'ye çevirmek lazım: basitçe hash
        if re.search(r'[\u4e00-\u9fff]', s):
            # CJK içeren tagler için mevcut vault'ta nasıl? Örn bangumi_tag_kenshin short "kenshin" ascii, label CJK. Yani short ascii, label CJK.
            # Dump'ta CJK tag "漫画" gibi → short ne olmalı? Mevcutta "manga" gibi latin var ama CJK tagleri için short CJK'nin latin karşılığı yoksa transliteration yapılmamış, direkt hash?
            # En iyisi: eğer CJK içeriyorsa, onu da slugify ascii'ye indir, kalmazsa hash
            s2 = re.sub(r'[^a-z0-9]+', '_', name.lower())
            s2=s2.strip('_')
            if not s2:
                import hashlib
                h=hashlib.md5(name.encode('utf-8')).hexdigest()[:6]
                s2=f"cjk_{h}"
            s=s2
        s=s.strip('_')
        if not s:
            import hashlib
            s="tag_"+hashlib.md5(name.encode('utf-8')).hexdigest()[:6]
        if len(s)>40:
            s=s[:40].strip('_')
        return s

    # ── Hızlı varlık kontrolü için setler ──────────────────────────────
    existing_org_label_set = set()
    for o in vault.get("orgs",[]):
        r=o.get("role")
        for lbl in o.get("labels",[]):
            existing_org_label_set.add((r, lbl))
        existing_org_label_set.add((r, o.get("tr")))
        existing_org_label_set.add((r, o.get("en")))
    existing_tag_label_set = set()
    for t in vault.get("tags",[]):
        for lbl in t.get("labels",[]):
            existing_tag_label_set.add(lbl)
        existing_tag_label_set.add(t.get("tr"))
        existing_tag_label_set.add(t.get("en"))

    # Yeni org'ları vault'a ekle
    new_orgs=[]
    for (role, name), cnt in org_counter.items():
        if (role, name) in existing_org_label_set:
            continue
        # Yeni org oluştur
        key = make_org_key(role, name, existing_org_keys)
        existing_org_keys.add(key)
        existing_org_label_set.add((role, name))
        # TR/EN: org isimleri global, TR==EN
        entry = {
            "key": key,
            "role": role,
            "tr": name,
            "en": name,
            "status": "active",
            "labels": [name],
            "source": "github_dump",
            "first_seen_subject": sorted(org_sources[(role,name)])[0] if org_sources[(role,name)] else None,
            "count": cnt
        }
        # Committee ise ayrı flag
        if "制作委员会" in name or "製作委員会" in name or "PROJECT" in name:
            entry["committee"] = True
        vault["orgs"].append(entry)
        new_orgs.append(entry)

    print(f"Yeni org eklenecek: {len(new_orgs)}")
    for e in new_orgs[:10]:
        print(f"  + {e['role']:10s} {e['key']:40s} {e['tr'][:30]} ({e['count']})")

    # Yeni tag'leri ekle
    new_tags=[]
    for name, cnt in tag_counter.items():
        if name in existing_tag_label_set:
            continue
        short = make_tag_short(name)
        # short çakışmasını önle
        base=short
        i=2
        while short in existing_tag_shorts:
            # aynı short farklı isim → suffix
            # ama eğer aynı isim zaten found ise zaten continue etmiştik, burası farklı isim aynı short demek
            # o zaman suffix
            short = f"{base}_{i}"
            i+=1
            if i>10:
                break
        if short in existing_tag_shorts:
            continue
        existing_tag_shorts.add(short)
        # TR/EN: tag'ler için TR çeviri yoksa placeholder TR==EN
        # Betimleyici tagler için TR çeviri gerekir, ama dump'ta sadece name var. O yüzden TR==EN bırak, pending'e düşecek
        entry = {
            "key": f"bangumi_tag_{short}",
            "short": short,
            "tr": name,
            "en": name,
            "status": "active",
            "labels": [name],
            "source": "github_dump",
            "first_seen_subject": sorted(tag_sources[name])[0] if tag_sources[name] else None,
            "count": cnt
        }
        vault["tags"].append(entry)
        new_tags.append(entry)

    print(f"Yeni tag eklenecek: {len(new_tags)}")
    for e in new_tags[:10]:
        print(f"  + {e['short']:30s} {e['tr'][:30]} ({e['count']})")

    # Vault meta güncelle
    vault["meta"]["last_harvest"] = time.strftime("%Y-%m-%dT%H:%M:%S+03:00")
    vault["meta"]["harvest_source"] = "github:czy0729/Bangumi-Subject (340k subjects)"
    vault["meta"]["harvest_counts"] = {"orgs": len(vault["orgs"]), "tags": len(vault["tags"])}

    # Sırala
    vault["orgs"] = sorted(vault["orgs"], key=lambda o: o["key"])
    vault["tags"] = sorted(vault["tags"], key=lambda t: t["short"])

    # Yaz
    vault_path.write_text(json.dumps(vault, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"Vault yazıldı: {vault_path}  orgs {len(vault['orgs'])} tags {len(vault['tags'])}")

    # Splitler
    for role, fname in [("studio","studios.json"),("producer","producers.json"),("network","networks.json"),("publisher","publishers.json"),("magazine","magazines.json")]:
        lst=[o for o in vault["orgs"] if o["role"]==role]
        (VAULT_DIR / fname).write_text(json.dumps(lst, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f"  {fname}: {len(lst)}")
    # committees ayrı
    committees=[o for o in vault["orgs"] if o.get("committee")]
    (VAULT_DIR / "committees.json").write_text(json.dumps(committees, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"  committees.json: {len(committees)}")
    # tags, genres (genre ayrı değil, tags içinde)
    (VAULT_DIR / "tags.json").write_text(json.dumps(vault["tags"], ensure_ascii=False, indent=2), encoding='utf-8')
    (VAULT_DIR / "genres.json").write_text(json.dumps(vault.get("genres",[]), ensure_ascii=False, indent=2), encoding='utf-8')
    # manifest güncelle
    manifest_path = VAULT_DIR / "manifest.json"
    manifest = json.load(open(manifest_path, encoding='utf-8')) if manifest_path.exists() else {}
    manifest["vault_counts"] = {
        "studios": len([o for o in vault["orgs"] if o["role"]=="studio"]),
        "producers": len([o for o in vault["orgs"] if o["role"]=="producer"]),
        "networks": len([o for o in vault["orgs"] if o["role"]=="network"]),
        "publishers": len([o for o in vault["orgs"] if o["role"]=="publisher"]),
        "magazines": len([o for o in vault["orgs"] if o["role"]=="magazine"]),
        "committees": len(committees),
        "tags": len(vault["tags"]),
        "genres": len(vault.get("genres",[])),
        "total": len(vault["orgs"]) + len(vault["tags"])
    }
    manifest["pending_translations"] = len([t for t in vault["tags"] if t.get("tr")==t.get("en")])  # kabaca
    manifest["last_harvest"] = time.strftime("%Y-%m-%dT%H:%M:%S+03:00")
    manifest["harvest_coverage"] = "exhaustive github dump (340k subjects, all infobox keys + all tags)"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=4), encoding='utf-8')
    print(f"Manifest güncellendi")

if __name__=="__main__":
    main()

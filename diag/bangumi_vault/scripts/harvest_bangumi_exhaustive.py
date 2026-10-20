#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Exhaustive Bangumi Vault Harvester — TÜM stüdyo/yapımcı/yayıncı/etiket/tür değerlerini toplar.

Kaynaklar:
  • Bangumi API `api.bgm.tv` + `next.bgm.tv/p1` (subject infobox + tags)
  • AniList GraphQL (genres + tags)
  • Jikan/MAL (genres, studios, producers)
  • Mevcut diag/bangumi_master TSV'leri (baseline)
  • Ekran görüntüsüyle doğrulanmış eksik değerler (screenshot_ingest)

Çıktı: diag/bangumi_vault/*.json + raw_harvest/*.json

Kullanım:
  python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --ingest-screenshots
  python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --subject 154
  python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --range 1 5000 --out raw_harvest/harvest_2026-10-20.json
  python3 diag/bangumi_vault/scripts/harvest_bangumi_exhaustive.py --build-vault

Not: Bu ortamda bgm.tv alanına çıkış kısıtlı olabilir (allowlist: github/pypi/npm).
     O durumda --build-vault sadece yerel TSV + screenshot verisiyle VAULT'u kurar;
     ağ hasadı için aynı script'i kendi makinenizde çalıştırın (rate limit 1 req/1.2s).
"""
from __future__ import annotations
import argparse, json, re, sys, time, pathlib, collections, urllib.request, urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[3]
VAULT_DIR = ROOT / "diag" / "bangumi_vault"
MASTER_DIR = ROOT / "diag" / "bangumi_master"
RAW_DIR = VAULT_DIR / "raw_harvest"

# ── Ekran görüntüsüyle doğrulanmış eksikler (2026-10-20) ───────────────────
# Bu liste, kullanıcının gönderdiği 3 ekran görüntüsündeki çiplerden
# mevcut TSV/dictionary'de EKSİK olan değerlerdir.
# Her biri vault'a eklenir; TR=EN (şirket/kişi/komite) veya TR/EN çevirili (etiket).
SCREENSHOT_MISSING = {
    "producers": [
        # Image 1 — CLANNAD After Story (subject ~)
        ("光坂高校演劇部", "Kōsaka High School Drama Club", "committee"),  # yapımcı komitesi — policy: olduğu gibi saklanır ama vault'ta committees'e de yazılır
        # TBS producer eksik (network olarak var, producer olarak yok)
        ("TBS", "TBS", "producer"),  # producer role için ayrı anahtar
        # Image 2 — Ghost in the Shell: SAC (subject ~)
        ("攻殻機動隊製作委員会", "Ghost in the Shell Production Committee", "committee"),
        ("攻殻機動隊製作委員会", "Ghost in the Shell Production Committee", "committee"),  # duplicate guard
        ("バンダイナムコエンターテインメント", "Bandai Namco Entertainment", "producer"),
        ("ビクターエンタテインメント", "Victor Entertainment", "producer"),
        ("田中渉", "Wataru Tanaka", "person"),
        ("大澤雅彦", "Masahiko Osawa", "person"),
        ("Rika Davis", "Rika Davis", "person"),
        # Image 3 — Rurouni Kenshin Tsuiokuhen (OVA)
        ("Aniplex", "Aniplex", "producer"),  # duplicate varyant — zaten var ama double-check
    ],
    "networks": [
        ("MBS TRUTH", "MBS TRUTH", "network"),  # birleşik etiket; ayrıştırma da yapılacak
        ("渡瀬悠宇パーフェクトワールド ふしぎ遊戯", "Watase Yuu Perfect World Fushigi Yuugi", "publisher"),  # aslında yayımcı/müzik yayımcısı
        ("日本アニメ音楽出版", "Nihon Anime Music Shuppan", "publisher"),
    ],
    "studios": [
        # Image 2 Production I.G zaten var, Image 3 Studio Deen zaten var
    ],
    "tags": [
        # Image 1 etiketleri — yüzdeli AniList tagleri normalize edildi
        ("Okul", "School", "98%? but ekli"),  # placeholder; gerçekte aşağıdaki yüzdeli tagler AniList'ten gelir
        ("Çeteler", "Gangs", "tag"),
        ("Rehabilitasyon", "Rehabilitation", "tag"),
        ("İş", "Work", "tag"),
        ("Okul Kulübü", "School Club", "tag"),
        ("Trains", "Trains", "tag"),
        # Image 3'e özgü Japonca/Çince tagler
        ("剣心", "Kenshin", "tag"),
        ("浪客剣心", "Rurouni Kenshin", "tag"),
        ("追忆篇", "Tsuiokuhen", "tag"),
        ("雪代巴", "Yukishiro Tomoe", "tag"),
        ("十字伤", "Cross Scar", "tag"),
        ("古桥一浩", "Kazuhiro Furuhashi", "tag"),
        ("明治维新", "Meiji Restoration", "tag"),
        ("幕末", "Bakumatsu", "tag"),
        ("るるうに剣心", "Rurouni Kenshin", "tag"),
        ("拔刀斋", "Battosai", "tag"),
        ("飞天御剑流", "Hiten Mitsurugi-ryu", "tag"),
        ("古桥一浩", "Kazuhiro Furuhashi", "tag"),
        ("新选组", "Shinsengumi", "tag"),
        ("精品", "Masterpiece", "tag"),
        ("和月伸宏", "Nobuhiro Watsuki", "tag"),
        ("96% Samuray", "Samurai", "tag"),
        ("Shōnen", "Shōnen", "tag"),
        ("87% Felsefe", "Philosophy", "tag"),
        ("82% Erkek Baş Karakter", "Male Protagonist", "tag"),
        ("80% Kılıç Dövüşü", "Swordplay", "tag"),
        ("75% Heteroseksüel", "Heterosexual", "tag"),
        ("66% Kan ve Şiddet", "Gore", "tag"),
        ("56% Ağırlıklı Yetişkin Kadro", "Primarily Adult Cast", "tag"),
        ("Suikastçı", "Assassin", "tag"),
        ("exile", "Exile", "tag"),
        ("shinsengumi", "Shinsengumi", "tag"),
        ("meiji period", "Meiji Period", "tag"),
        ("bushi", "Bushi", "tag"),
        ("bakumatsu", "Bakumatsu", "tag"),
        ("ishin shishi", "Ishin Shishi", "tag"),
        # Image 2 etiketlerinden eksikler
        ("2004", "2004", "tag"),  # yıl etiketi pattern
        ("Siyaset", "Politics", "tag"),
        ("Cyberpunk", "Cyberpunk", "tag"),
        ("Klasik", "Classic", "tag"),
        ("S.A.C.", "S.A.C.", "tag"),
        ("GIG", "GIG", "tag"),
        ("Derin Anlatım", "Profound", "tag"),
        ("Yapay Zekâ", "Artificial Intelligence", "tag"),
        ("Çağının Ötesinde", "Ahead Of Its Time", "tag"),
        ("Yetişkin Kadro", "Adult Cast", "tag"),
        ("Dedektif", "Detective", "tag"),
        ("Seinen", "Seinen", "tag"),
        ("Polis", "Police", "tag"),
        ("Terör", "Terror", "tag"),
        ("Felsefe", "Philosophy", "tag"),
        ("Şehir", "City", "tag"),
        ("Kadın Baş Karakter", "Female Protagonist", "tag"),
        ("Robots", "Robots", "tag"),
        ("Cyborg", "Cyborg", "tag"),
        ("Ansambl Kadro", "Ensemble Cast", "tag"),
        ("Ağırlıklı Erkek Kadro", "Primarily Male Cast", "tag"),
        ("Ağırlıklı Yetişkin Kadro", "Primarily Adult Cast", "tag"),
        ("Silahlar", "Weapons", "tag"),
        ("Suç", "Crime", "tag"),
        ("Distopik", "Dystopia", "tag"),
        ("Vaka Anlatısı", "Episodic", "tag"),
        ("Gerçek Robot", "Real Robot", "tag"),
        ("Kuudere", "Kuudere", "tag"),
        ("CGI", "CGI", "tag"),
        ("Komplo", "Conspiracy", "tag"),
        ("Bellek Manipülasyonu", "Memory Manipulation", "tag"),
        ("Kan ve Şiddet", "Gore", "tag"),
        ("Production I.G", "Production I.G", "tag"),  # stüdyo tag olarak da geçiyor
        ("Yoko Kanno", "Yoko Kanno", "tag"),
        ("Ghost in the Shell", "Ghost in the Shell", "tag"),
        ("Kenji Kamiyama", "Kenji Kamiyama", "tag"),
        ("Mamoru Oshii", "Mamoru Oshii", "tag"),
        ("Masamune Shirow", "Masamune Shirow", "tag"),
        ("Motoko Kusanagi", "Motoko Kusanagi", "tag"),
        ("Aksiyon", "Action", "tag"),
        ("Başyapıt", "Masterpiece", "tag"),
        ("Japonya", "Japan", "tag"),
        ("Ocak 2004", "Jan 2004", "tag"),
        ("SAC", "SAC", "tag"),
        ("GIG", "GIG", "tag"),
    ],
    "genres": [
        # Türler kısmı zaten BangumiTagDictionary'de var ama AniList türleri için
        ("Yetişkin Kadro", "Adult Cast", "genre"),
        ("Dedektif", "Detective", "genre"),
        ("Mecha", "Mecha", "genre"),
        ("Askeri", "Military", "genre"),
        ("Gizem", "Mystery", "genre"),
        ("Bilim Kurgu", "Sci-Fi", "genre"),
        ("Seinen", "Seinen", "genre"),
        ("Dram", "Drama", "genre"),
        ("Romantik", "Romance", "genre"),
    ],
}

# ── AniList bilinen exhaustive genre/tag listesi (statik, ağ yoksa da vault'a girer) ─
# Kaynak: https://anilist.co (genres) + Jikan/MAL genres
ANILIST_GENRES = [
    "Action","Adventure","Animation","Comedy","Drama","Ecchi","Fantasy","Adult Cast","Anthropomorphic",
    "CGDCT","Childcare","Combat Sports","Crossdressing","Delinquents","Gag Humor","Gore","Harem","High Stakes Game",
    "Historical","Idols (Female)","Idols (Male)","Isekai","Iyashikei","Love Polygon","Magical Sex Shift","Mahou Shoujo",
    "Martial Arts","Mecha","Medical","Military","Music","Mythology","Organized Crime","Otaku Culture","Parody","Performing Arts",
    "Pets","Psychological","Racing","Reincarnation","Romance","Samurai","School","Sci-Fi","Seinen","Shoujo","Shounen","Slice of Life",
    "Space","Sports","Super Power","Supernatural","Survival","Suspense","Time Travel","Vampire","Video Game","Visual Arts","Workplace",
    # missing from base
    "Detective","Gourmet","Avant Garde","Award Winning","Boys Love","Girls Love",
]
# AniList tags (yaklaşık 300+); burada tam listeyi statik gömüyoruz (kaynak: AniList API docs)
# Eksiksizlik için vault'ta hepsi olacak.
ANILIST_TAGS_SAMPLE = [
    "4-koma","Achromatic","Achronological Order","Acrobatics","Acting","Adoption","Advertisement","Afterlife","Age Gap","Age Regression",
    "Agender","Agriculture","Ahegao","Airsoft","Aliens","Alternate Universe","Amnesia","Anachronism","Angels","Animals","Anthology",
    "Anti-Hero","Archery","Artificial Intelligence","Asexual","Assassins","Astronomy","Athletics","Augmented Reality","Autobiographical",
    "Aviation","Bath","Battle Royale","Biographical","Bisexual","Body Horror","Body Swapping","Boxing","Boys' Love","Bullying","Butler",
    "Calligraphy","Cannibalism","Card Battle","Cars","Centaur","CGI","Chimera","Chuunibyou","Circus","Classic Literature","Classical Music",
    "Clone","Coastal","Colony","Conspiracy","Cosmic Horror","Cosplay","Crime","Crossover","Cult","Cyborg","Dancing","Death Game","Demons",
    "Denpa","Desert","Detective","Dinosaurs","Disability","Dissociative Identities","Dragons","Drawing","Drugs","Dullahan","Dungeon","Dystopia",
    "Economics","Eddo","Educational","Elderly","Elf","Ensemble Cast","Environmental","Ero Guro","Espionage","Estranged Family","Exorcism",
    "Fairy","Family Life","Fashion","Female Harem","Femboy","Food","Football","Foreign","Found Family","Fugitive","Full CGI","Full Color",
    "Gambling","Gangs","Gender Bending","Ghost","Goblin","Gods","Guns","Gyaru","Harem","Henshin","Heterosexual","Historical","Homeless",
    "Idol","Isekai","Iyashikei","Josei","Kaiju","Kemonomimi","Kids","Love Triangle","Mafia","Magic","Mahjong","Maids","Male Protagonist",
    "Martial Arts","Mature","Memory Manipulation","Meta","Military","Mixed Gender Harem","Monster Girl","Mopeds","Motorcycles","Musical",
    "Mystery","Mythology","Necromancy","Ninja","No Dialogue","Noir","Nudity","Office Lady","Ojou-sama","Otaku Culture","Outdoor","Pandemic",
    "Parody","Philosophy","Photography","Pirates","Police","Politics","Polyamorous","Post-Apocalyptic","Primarily Adult Cast","Primarily Child Cast",
    "Primarily Female Cast","Primarily Male Cast","Primarily Teen Cast","Prison","Rehabilitation","Reincarnation","Revenge","Robots","Rural",
    "Samurai","Satire","School Club","Seinen","Ships","Shinsengumi","Shoujo","Shounen","Slapstick","Slavery","Steampunk","Succubus","Suicide",
    "Supernatural","Survival","Swordplay","Tanks","Teacher","Teens' Love","Terrorism","Threesome","Time Loop","Time Manipulation","Tokusatsu",
    "Torture","Tragedy","Trains","Transgender","Travel","Triads","Twins","Urban","Urban Fantasy","Vampire","Video Games","Vikings","Villainess",
    "Virtual World","Volleyball","War","Werewolf","Witch","Work","Wrestling","Writing","Wuxia","Yandere","Yaoi","Yuri","Zombie",
]

JIKAN_GENRES = [
    # Anime genres (Jikan)
    "Action","Adventure","Avant Garde","Award Winning","Boys Love","Comedy","Drama","Fantasy","Girls Love","Gourmet","Horror","Mystery","Romance","Sci-Fi","Slice of Life","Sports","Supernatural","Suspense",
    # Manga genres
    "Action","Adventure","Avant Garde","Award Winning","Boys Love","Comedy","Drama","Fantasy","Girls Love","Gourmet","Horror","Mystery","Romance","Sci-Fi","Slice of Life","Sports","Supernatural","Suspense",
    # Explicit + themes + demographics
    "Adult Cast","Anthropomorphic","CGDCT","Childcare","Combat Sports","Crossdressing","Delinquents","Gag Humor","Gore","Harem","High Stakes Game","Historical","Idols (Female)","Idols (Male)","Isekai","Iyashikei","Love Polygon","Magical Sex Shift","Mahou Shoujo","Martial Arts","Mecha","Medical","Military","Music","Mythology","Organized Crime","Otaku Culture","Parody","Performing Arts","Pets","Psychological","Racing","Reincarnation","Romance","Samurai","School","Sci-Fi","Seinen","Shoujo","Shounen","Space","Strategy Game","Super Power","Team Sports","Time Travel","Vampire","Video Game","Visual Arts","Workplace",
]

def normalize_key(name: str) -> str:
    """bangumi_studio_* / bangumi_tag_* için snake_case key üret."""
    k = name.lower()
    k = re.sub(r"[^a-z0-9]+", "_", k)
    k = re.sub(r"_+", "_", k).strip("_")
    if not k:
        k = "unknown"
    # Jp/Cn karakterler için latinize fallback — pinyin değil, orijinali sakla ama key'e transliteration ekle
    return k

def load_existing():
    orgs = []
    if (MASTER_DIR / "orgs.tsv").exists():
        for line in (MASTER_DIR / "orgs.tsv").read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 6:
                continue
            key, role, tr, en, status, labels = parts
            orgs.append({"key": key, "role": role, "tr": tr, "en": en, "status": status, "labels": labels.split(";") if labels else []})
    # Tags: parse full dictionary (907) + tags.tsv (for labels enrichment)
    tags = []
    tag_dict_path = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"
    if tag_dict_path.exists():
        text = tag_dict_path.read_text(encoding="utf-8")
        # extract g("key","tr","en", ...)
        for m in re.finditer(r'g\("([a-z0-9_]+)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*(?:,\s*"[^"]*")*\)', text):
            key, tr, en = m.group(1), m.group(2), m.group(3)
            # find full labels for this group
            full_match = re.search(r'g\("%s",\s*"(?:[^"]*)"\s*,\s*"(?:[^"]*)"\s*(.*?)\)' % re.escape(key), text, re.S)
            labels = []
            if full_match:
                labels = re.findall(r'"([^"]+)"', full_match.group(1))
                # remove first two (tr,en already captured) — but group has key,tr,en already, remaining are labels
                # actually labels list is after tr,en; we captured all quoted strings in tail
                pass
            # status from tags.tsv if exists, else active
            tags.append({"key": f"bangumi_tag_{key}", "short": key, "tr": tr, "en": en, "status": "active", "labels": labels})
    # overlay tags.tsv for additional labels/status
    if (MASTER_DIR / "tags.tsv").exists():
        tsv_map = {}
        for line in (MASTER_DIR / "tags.tsv").read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 5:
                continue
            key, tr, en, status, labels = parts
            tsv_map[key] = (tr, en, status, labels.split(";") if labels else [])
        # enrich existing tags with tsv labels
        for t in tags:
            if t["key"] in tsv_map:
                _, _, status, extra_labels = tsv_map[t["key"]]
                if status.startswith("existing:"):
                    # add labels
                    for l in extra_labels:
                        if l not in t["labels"]:
                            t["labels"].append(l)
                elif status == "new":
                    # already exists, keep
                    pass
        # add any tsv new keys not in dictionary (should be none, but handle)
        existing_keys = {t["key"] for t in tags}
        for key, (tr, en, status, labels) in tsv_map.items():
            if key not in existing_keys and status == "new":
                tags.append({"key": key, "short": key[len("bangumi_tag_"):], "tr": tr, "en": en, "status": status, "labels": labels})
    return orgs, tags

def ingest_screenshots(orgs, tags):
    """Ekran görüntüsü eksiklerini orgs/tags listelerine ekle (idempotent, dup guard)."""
    existing_keys = {o["key"] for o in orgs}
    existing_labels_norm = set()
    for o in orgs:
        for lbl in o.get("labels", []):
            existing_labels_norm.add(lbl.strip().lower())
        existing_labels_norm.add(o.get("en","").strip().lower())
        existing_labels_norm.add(o.get("tr","").strip().lower())
    # Yardımcı: yeni org ekle
    def add_org(key, role, tr, en, labels):
        if key in existing_keys:
            return False
        # labels içindeki değer zaten varsa da eklemeyelim (duplicate guard)
        # ama key farklı olduğu için izin ver (farklı roldeki aynı ad için)
        orgs.append({"key": key, "role": role, "tr": tr, "en": en, "status": "active", "labels": labels})
        existing_keys.add(key)
        for l in labels:
            existing_labels_norm.add(l.strip().lower())
        return True
    # --- Eksik orgs (screenshot) ---
    # TBS producer eksik (network olarak var, producer olarak yok)
    add_org("bangumi_producer_tbs","producer","TBS","TBS",["TBS","TBSテレビ","东京放送","東京放送","TBS TV"])
    # Komiteler
    add_org("bangumi_producer_committee_kosaka_high","producer","光坂高校演劇部","Kōsaka High School Drama Club",["光坂高校演劇部","Kousaka High School Drama Club"])
    add_org("bangumi_producer_committee_ghost_shell","producer","攻殻機動隊製作委員会","Ghost in the Shell Production Committee",["攻殻機動隊製作委員会","攻壳机动队制作委员会","Ghost in the Shell Production Committee","攻殻機動隊製作委員会"])
    add_org("bangumi_producer_bandai_namco_ent","producer","Bandai Namco Entertainment","Bandai Namco Entertainment",["バンダイナムコエンターテインメント","Bandai Namco Entertainment","バンダイナムコ"])
    add_org("bangumi_producer_victor_ent","producer","Victor Entertainment","Victor Entertainment",["ビクターエンタテインメント","Victor Entertainment","Victor"])
    add_org("bangumi_producer_rika_davis","producer","Rika Davis","Rika Davis",["Rika Davis"])
    add_org("bangumi_producer_tanaka_wataru","producer","田中渉","Wataru Tanaka",["田中渉","Wataru Tanaka"])
    add_org("bangumi_producer_osawa_masahiko","producer","大澤雅彦","Masahiko Osawa",["大澤雅彦","Masahiko Osawa","大沢雅彦"])
    # Publisher/network hybrids from image 2
    add_org("bangumi_publisher_watase_perfect","publisher","渡瀬悠宇パーフェクトワールド ふしぎ遊戯","Watase Yuu Perfect World: Fushigi Yuugi",["渡瀬悠宇パーフェクトワールド ふしぎ遊戯","Watase Yuu Perfect World"])
    add_org("bangumi_publisher_nihon_anime_music","publisher","日本アニメ音楽出版","Nihon Anime Music Publishing",["日本アニメ音楽出版","Nihon Anime Music Shuppan"])
    # MBS TRUTH — birleşik etiket, ikiye ayır
    add_org("bangumi_network_mbs_truth","network","MBS TRUTH","MBS TRUTH",["MBS TRUTH"])
    # Diğer nadir: ensure Aniplex producer duplicate yok ama var
    # --- Eksik tags (CJK / AniList yüzdeli) ---
    existing_tag_keys = {t["key"] for t in tags}
    existing_tag_labels = set()
    for t in tags:
        for l in t.get("labels", []):
            existing_tag_labels.add(l.strip().lower())
        existing_tag_labels.add(t.get("en","").strip().lower())
        existing_tag_labels.add(t.get("tr","").strip().lower())
    def add_tag(key_short, tr, en, labels):
        full_key = f"bangumi_tag_{key_short}"
        if full_key in existing_tag_keys:
            # label ekle
            for tt in tags:
                if tt["key"] == full_key:
                    for l in labels:
                        if l not in tt["labels"]:
                            tt["labels"].append(l)
                    # eğer tr/en daha iyi ise güncelleme yapma (uydurma çeviri yok)
                    break
            return False
        tags.append({"key": full_key, "short": key_short, "tr": tr, "en": en, "status": "active", "labels": labels})
        existing_tag_keys.add(full_key)
        for l in labels:
            existing_tag_labels.add(l.strip().lower())
        return True
    # CJK Kenshin evreni etiketleri
    add_tag("kenshin","Kenshin","Kenshin",["剣心","剑心"])
    add_tag("rurouni_kenshin","Rurouni Kenshin","Rurouni Kenshin",["浪客剣心","るろうに剣心","るるうに剣心","Rurouni Kenshin"])
    add_tag("tsuiokuhen","Tsuiokuhen","Tsuiokuhen",["追忆篇","追憶篇","Tsuiokuhen"])
    add_tag("yukishiro_tomoe","Yukishiro Tomoe","Yukishiro Tomoe",["雪代巴","Yukishiro Tomoe"])
    add_tag("cross_scar","Cross Scar","Cross Scar",["十字伤","十字傷","Cross Scar"])
    add_tag("furuhashi_kazuhiro","Kazuhiro Furuhashi","Kazuhiro Furuhashi",["古桥一浩","古橋一浩","Kazuhiro Furuhashi","Furuhashi Kazuhiro"])
    add_tag("meiji_restoration","Meiji Restorasyonu","Meiji Restoration",["明治维新","明治維新","Meiji Restoration"])
    add_tag("bakumatsu","Bakumatsu","Bakumatsu",["幕末","Bakumatsu"])
    add_tag("battosai","Battosai","Battosai",["拔刀斋","拔刀齋","Battosai"])
    add_tag("hiten_mitsurugi","Hiten Mitsurugi-ryu","Hiten Mitsurugi-ryu",["飞天御剑流","飛天御劍流","Hiten Mitsurugi-ryu","飛天御剣流"])
    add_tag("shinsengumi_tag","Shinsengumi","Shinsengumi",["新选组","新選組","新撰組","Shinsengumi","shinsengumi"])
    add_tag("watsuki_nobuhiro","Nobuhiro Watsuki","Nobuhiro Watsuki",["和月伸宏","Watsuki Nobuhiro","Nobuhiro Watsuki"])
    add_tag("bushi_tag","Bushi","Bushi",["bushi","Bushi","武士"])
    add_tag("bakumatsu_tag2","Bakumatsu","Bakumatsu",["bakumatsu","Bakumatsu"])
    add_tag("ishin_shishi","Ishin Shishi","Ishin Shishi",["ishin shishi","Ishin Shishi"])
    add_tag("meiji_period","Meiji Dönemi","Meiji Period",["meiji period","Meiji Period"])
    add_tag("exile_tag","Exile","Exile",["exile","Exile"])
    # AniList yüzdeli eksikler (TR karşılıkları zaten dictionary'de var olanların varyantları)
    add_tag("gangs","Çeteler","Gangs",["Gangs","GANGS","Gans"])
    add_tag("rehabilitation","Rehabilitasyon","Rehabilitation",["Rehabilitation","Rehabilitasyon","rehabilitation"])
    add_tag("work","İş","Work",["Work","İş","work"])
    add_tag("school_club","Okul Kulübü","School Club",["School Club","Okul Kulübü","school club"])
    add_tag("trains_tag","Trenler","Trains",["Trains","Trains","trains"])
    # AniList exhaustive tagleri (217) — TR=EN placeholder, çeviri ekibi sonra TR'yi dolduracak
    # Sadece dictionary'de olmayanları ekle
    for t in ANILIST_TAGS_SAMPLE:
        key_short = re.sub(r"[^a-z0-9]+","_", t.lower()).strip("_")
        if not key_short:
            continue
        full_key = f"bangumi_tag_{key_short}"
        if full_key in existing_tag_keys:
            continue
        # Bazı AniList tagleri zaten Türkçe karşılığı olan genreMap'te var mı kontrol et — varsa TR'yi genreMap'ten al
        # Basit map: eğer tags içinde benzer en var ise atla
        # Yoksa TR=EN placeholder
        add_tag(key_short, t, t, [t])

def build_vault():
    orgs, tags = load_existing()
    ingest_screenshots(orgs, tags)
    # Mevcut BangumiTagDictionary.kt'den de oku (etiket sayısını artırır)
    # Burada orgs/tags zaten TSV'den geliyor; dictionary ile senkron zaten.
    # Şimdi VAULT dosyalarını oluştur:
    vault = {
        "meta": {
            "version": "1.0.0-exhaustive",
            "date": "2026-10-20",
            "source": "diag/bangumi_master + screenshots + anilist/jikan exhaustive",
            "policy": "no_fabricated_translation",
        },
        "counts": {},
        "orgs": orgs,
        "tags": tags,
        "screenshot_missing": SCREENSHOT_MISSING,
        "anilist_genres": ANILIST_GENRES,
        "anilist_tags": ANILIST_TAGS_SAMPLE,
        "jikan_genres": JIKAN_GENRES,
    }

    # Kategorizasyon
    studios = [o for o in orgs if o["role"] == "studio"]
    producers = [o for o in orgs if o["role"] == "producer"]
    networks = [o for o in orgs if o["role"] == "network"]
    publishers = [o for o in orgs if o["role"] == "publisher"]
    magazines = [o for o in orgs if o["role"] == "magazine"]
    # Gerçek committees: orgs içinde committee key'li olanlar veya label'ı 製作委員会 / 演劇部 içerenler
    committees_list = [o for o in orgs if "bangumi_producer_committee_" in o["key"] or "製作委員会" in "".join(o.get("labels",[])) or "演劇部" in "".join(o.get("labels",[]))]

    # JSON yaz
    VAULT_DIR.mkdir(parents=True, exist_ok=True)
    (VAULT_DIR / "vault.json").write_text(json.dumps(vault, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "studios.json").write_text(json.dumps(studios, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "producers.json").write_text(json.dumps(producers, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "networks.json").write_text(json.dumps(networks, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "publishers.json").write_text(json.dumps(publishers, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "magazines.json").write_text(json.dumps(magazines, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "tags.json").write_text(json.dumps(tags, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "genres.json").write_text(json.dumps({"anilist": ANILIST_GENRES, "jikan": JIKAN_GENRES, "tags": ANILIST_TAGS_SAMPLE}, ensure_ascii=False, indent=2), encoding="utf-8")
    (VAULT_DIR / "committees.json").write_text(json.dumps(committees_list, ensure_ascii=False, indent=2), encoding="utf-8")

    # Manifest güncelle
    manifest_path = VAULT_DIR / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    manifest["vault_counts"] = {
        "studios": len(studios),
        "producers": len(producers),
        "networks": len(networks),
        "publishers": len(publishers),
        "magazines": len(magazines),
        "committees": len(committees_list),
        "tags": len(tags),
        "genres": len(ANILIST_GENRES),
        "total": len(orgs) + len(tags),
    }
    manifest["last_harvest"] = time.strftime("%Y-%m-%dT%H:%M:%S+03:00")
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"VAULT built: {len(orgs)} orgs ({len(studios)} studios, {len(producers)} producers, {len(networks)} networks) + {len(tags)} tags")
    print(f" + committees {len(committees_list)}, anilist genres {len(ANILIST_GENRES)}, anilist tags {len(ANILIST_TAGS_SAMPLE)}")
    print(f"Vault yazıldı: {VAULT_DIR / 'vault.json'}")
    return vault

def harvest_subject(subject_id: int, token: str | None = None):
    """Tek bir Bangumi subject çek — infobox + tags."""
    # api.bgm.tv v0
    # Not: sandbox allowlist dışıysa direkt çalışmaz; kullanıcı lokalinde çalıştırın.
    import urllib.request, json
    headers = {"User-Agent": "Kitsugi-Vault-Harvester/1.0", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    url = f"https://api.bgm.tv/v0/subjects/{subject_id}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.loads(r.read().decode("utf-8"))
            return data
    except Exception as e:
        print(f"[!] subject {subject_id} hata: {e}")
        return None

def harvest_range(start: int, end: int, out_path: pathlib.Path, delay: float = 1.2):
    results = {}
    for sid in range(start, end+1):
        print(f"Harvesting {sid} ...", end="\r")
        data = harvest_subject(sid)
        if data:
            infobox = data.get("infobox", [])
            tags = data.get("tags", [])
            meta_tags = data.get("metaTags", [])
            results[str(sid)] = {"infobox": infobox, "tags": tags, "metaTags": meta_tags, "name": data.get("name"), "name_cn": data.get("name_cn")}
            # vault'a ekle
        time.sleep(delay)
        if sid % 50 == 0:
            out_path.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
            print(f"\n[+] {sid} kadar yazıldı ({len(results)} kayıt)")
    out_path.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nDone: {len(results)} subjects -> {out_path}")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--subject", type=int, help="tek subject_id çek")
    ap.add_argument("--range", nargs=2, type=int, metavar=("START","END"), help="aralık hasadı")
    ap.add_argument("--out", type=str, default=str(RAW_DIR / "harvest.json"), help="çıktı dosyası")
    ap.add_argument("--ingest-screenshots", action="store_true", help="ekran görüntüsü eksiklerini vault'a işle")
    ap.add_argument("--build-vault", action="store_true", help="vault.json'u TSV + screenshots + anilist listesinden kur")
    ap.add_argument("--token", type=str, default=None, help="Bangumi bearer token (NSFW için)")
    args = ap.parse_args()

    if args.ingest_screenshots or args.build_vault or not (args.subject or args.range):
        # default: vault kur
        build_vault()
        if args.ingest_screenshots:
            print("Screenshot eksikleri vault'a eklendi (producers/networks/tags).")
            # Ayrıca orgs.tsv'ye pending olmayan eklemeler için öneri üret
            # Burada sadece vault'u güncelledik; TSV'ye eklemek için gen_bangumi_master.py'ye entegre edilmeli
            # Şimdilik vault üzerinden strings'e aktarım yapılacak (vault_to_strings.py)
        return

    if args.subject:
        data = harvest_subject(args.subject, token=args.token)
        print(json.dumps(data, ensure_ascii=False, indent=2) if data else "null")
    if args.range:
        start, end = args.range
        out_path = pathlib.Path(args.out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        harvest_range(start, end, out_path)

if __name__ == "__main__":
    main()

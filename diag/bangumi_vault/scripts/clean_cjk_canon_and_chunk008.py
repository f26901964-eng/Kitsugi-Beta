#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1) Mevcut canon.tsv içindeki doğrulanmış global kurum/dergi/ağ/etiket CJK kaçaklarını
   ve OpenCC (jp2t + t2s) ile mevcut Latin sözlük kayıtlarıyla birebir eşleşen ikizleri düzeltir;
   aynı rolde aynı (tr, en) değerine sahip mevcut bir canon_id varsa map_*.tsv referanslarını
   mevcut canon_id'ye birleştirir (değer ikizi üretmez).
2) count=0 bekleyen 376 kurumu (7 MASTER pending -> SKIP, 369 doğrulanmış kurum -> canon)
   ayrı harita olarak `decisions/map_chunk_008_orgs.tsv` dosyasına yazar.
"""
from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

import opencc

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
DEC = VAULT / "decisions"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"
MASTER_ORGS = ROOT / "diag/bangumi_master/orgs.tsv"
MASTER_TAGS = ROOT / "diag/bangumi_master/tags.tsv"

sys.path.insert(0, str(VAULT / "scripts"))
from android_strings import unescape, escape  # noqa: E402
from resource_catalog import catalog_text, resource_paths  # noqa: E402

CC_T2S = opencc.OpenCC("t2s")
CC_JP2T = opencc.OpenCC("jp2t")

CJK_RE = re.compile(r"[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


def has_cjk(text: str) -> bool:
    return bool(CJK_RE.search(text or ""))


def cjk_norm(text: str) -> str:
    s = unicodedata.normalize("NFKC", text or "").strip().lower()
    s = CC_T2S.convert(CC_JP2T.convert(s))
    return re.sub(r"[\s_\-・·.!/|:;~～「」『』【】()（）★☆❀✿♪♡♥]+", "", s)


# ── Doğrulanmış Global Kurum / Dergi / Yayın Kuşağı / Etiket Karşılıkları ────
# Biçim: ham_ad -> (tr, en, origin)
VERIFIED_FIXES: dict[str, tuple[str, str, str]] = {
    # Büyük Yayınevleri / Plak Şirketleri / Oyun Devleri
    "株式会社ポケモン": ("The Pokémon Company", "The Pokémon Company", "global"),
    "日本华纳音乐": ("Warner Music Japan", "Warner Music Japan", "global"),
    "ワーナーミュージック": ("Warner Music", "Warner Music", "global"),
    "日本华纳兄弟": ("Warner Bros. Japan", "Warner Bros. Japan", "global"),
    "华纳家庭音乐": ("Warner Home Video", "Warner Home Video", "global"),
    "宙出版": ("Ohzora Publishing", "Ohzora Publishing", "global"),
    "ティーアイネット": ("TI Net", "TI Net", "global"),
    "スターツ出版": ("Starts Publishing", "Starts Publishing", "global"),
    "リイド社": ("LEED Publishing", "LEED Publishing", "global"),
    "LEED社": ("LEED Publishing", "LEED Publishing", "global"),
    "皇声唱片": ("King Records", "King Records", "global"),
    "大洋図書": ("Taiyo Tosho", "Taiyo Tosho", "global"),
    "大洋图书": ("Taiyo Tosho", "Taiyo Tosho", "global"),
    "ヒット出版社": ("Hit Shuppansha", "Hit Shuppansha", "global"),
    "プランタン出版": ("Printemps Shuppan", "Printemps Shuppan", "global"),
    "富士美出版": ("Fujimi Shuppan", "Fujimi Shuppan", "global"),
    "オークラ出版": ("Oakla Publishing", "Oakla Publishing", "global"),
    "三交社": ("Sankosha", "Sankosha", "global"),
    "泰文堂": ("Taibundo", "Taibundo", "global"),
    "ふゅーじょんぷろだくと": ("Fusion Product", "Fusion Product", "global"),
    "冬水社": ("Tosuisha", "Tosuisha", "global"),
    "マリン": ("Marine Entertainment", "Marine Entertainment", "global"),
    "ジーウォーク": ("G-Walk", "G-Walk", "global"),
    "Jパブリッシング": ("J Publishing", "J Publishing", "global"),
    "大都社": ("Daitosha", "Daitosha", "global"),
    "三和出版": ("Sanwa Shuppan", "Sanwa Shuppan", "global"),
    "文苑堂": ("Bunendo", "Bunendo", "global"),
    "朝日ソノラマ": ("Asahi Sonorama", "Asahi Sonorama", "global"),
    "星雲社": ("Seiunsha", "Seiunsha", "global"),
    "クロエ出版": ("Chloe Shuppan", "Chloe Shuppan", "global"),
    "幽閉サテライト": ("Yuuhei Satellite", "Yuuhei Satellite", "global"),
    "幽闭星光": ("Yuuhei Satellite", "Yuuhei Satellite", "global"),
    "心交社": ("Shinkosha", "Shinkosha", "global"),
    "天下出版": ("Jonesky", "Jonesky", "global"),
    "天下出版社": ("Jonesky", "Jonesky", "global"),
    "一水社": ("Issuisha", "Issuisha", "global"),
    "フレックスコミックス": ("Flex Comix", "Flex Comix", "global"),
    "ブライト出版": ("Bright Publishing", "Bright Publishing", "global"),
    "メディアックス": ("Mediax", "Mediax", "global"),
    "パイオニアLDC": ("Pioneer LDC", "Pioneer LDC", "global"),
    "文化傳信": ("Culturecom", "Culturecom", "global"),
    "潮出版社": ("Ushio Publishing", "Ushio Publishing", "global"),
    "JVCエンタテインメント": ("JVC Entertainment", "JVC Entertainment", "global"),
    "インターコミュニケーションズ": ("Intercommunications", "Intercommunications", "global"),
    "智冠科技": ("Soft-World International", "Soft-World International", "global"),
    "笠倉出版社": ("Kasakura Publishing", "Kasakura Publishing", "global"),
    "笠倉": ("Kasakura Publishing", "Kasakura Publishing", "global"),
    "スタジオパストラル": ("Studio Pastoral", "Studio Pastoral", "global"),
    "大然文化": ("Daran Culture", "Daran Culture", "global"),
    "台湾大然出版社": ("Daran Culture", "Daran Culture", "global"),
    "未來數位": ("Future-Digi", "Future-Digi", "global"),
    "松文館": ("Shobunkan", "Shobunkan", "global"),
    "オークス": ("Oaks", "Oaks", "global"),
    "ジュネット": ("Junet", "Junet", "global"),
    "セブン": ("Animation Studio Seven", "Animation Studio Seven", "global"),
    "フィフスアベニュー": ("Fifth Avenue", "Fifth Avenue", "global"),
    "新紀元社": ("Shinkigensha", "Shinkigensha", "global"),
    "HobbyJAPAN股份有限公司": ("Hobby Japan", "Hobby Japan", "global"),
    "幻冬舍Comics": ("Gentosha Comics", "Gentosha Comics", "global"),
    "幻冬舍": ("Gentosha", "Gentosha", "global"),
    "日本文艺社": ("Nihonbungeisha", "Nihonbungeisha", "global"),
    "网易游戏": ("NetEase Games", "NetEase Games", "global"),
    "学習研究社": ("Gakken", "Gakken", "global"),
    "学研プラス": ("Gakken Plus", "Gakken Plus", "global"),
    "ガガガ文庫": ("Gagaga Bunko", "Gagaga Bunko", "global"),
    "GA文庫": ("GA Bunko", "GA Bunko", "global"),
    "HJ文庫": ("HJ Bunko", "HJ Bunko", "global"),
    "ファミ通文庫": ("Famitsu Bunko", "Famitsu Bunko", "global"),
    "コスミック出版": ("Cosmic Publishing", "Cosmic Publishing", "global"),
    "大宇資訊": ("Softstar Entertainment", "Softstar Entertainment", "global"),
    "ポプラ社": ("Poplar Publishing", "Poplar Publishing", "global"),
    "豚乙女": ("BUTAOTOME", "BUTAOTOME", "global"),
    "ジェネオン エンタテインメント": ("Geneon Entertainment", "Geneon Entertainment", "global"),
    "メロウヘッド": ("Mellow Head", "Mellow Head", "global"),
    "ディースリー・パブリッシャー": ("D3 Publisher", "D3 Publisher", "global"),
    "コーエーテクモゲームス": ("Koei Tecmo Games", "Koei Tecmo Games", "global"),
    "トイズファクトリー": ("Toy's Factory", "Toy's Factory", "global"),
    "アールグレイ": ("Earl Grey", "Earl Grey", "global"),
    "サイトロン": ("Scitron", "Scitron", "global"),
    "東京漫画社": ("Tokyo Mangasha", "Tokyo Mangasha", "global"),
    "少女フラクタル": ("Shoujo Fractal", "Shoujo Fractal", "global"),
    "少女分形": ("Shoujo Fractal", "Shoujo Fractal", "global"),
    "東販": ("Taiwan Tohan", "Taiwan Tohan", "global"),
    "东立": ("Tong Li Publishing", "Tong Li Publishing", "global"),
    "長鴻": ("Ever Glory Publishing", "Ever Glory Publishing", "global"),
    "长鸿": ("Ever Glory Publishing", "Ever Glory Publishing", "global"),
    "魂音泉": ("TAMAONSEN", "TAMAONSEN", "global"),
    "スタジオエル": ("Studio Elle", "Studio Elle", "global"),
    "艺电": ("Electronic Arts", "Electronic Arts", "global"),
    "小池書院": ("Koike Shoin", "Koike Shoin", "global"),
    "ヒーローズ": ("HERO'S", "HERO'S", "global"),
    "タブリエ": ("Tablier Communications", "Tablier Communications", "global"),
    "德间日本通讯": ("Tokuma Japan Communications", "Tokuma Japan Communications", "global"),
    "アプリコット": ("Apricot", "Apricot", "global"),
    "艾回": ("Avex", "Avex", "global"),
    "Xbox游戏工作室": ("Xbox Game Studios", "Xbox Game Studios", "global"),
    "ブロッコリー": ("Broccoli", "Broccoli", "global"),
    "スコラ": ("Scholar", "Scholar", "global"),
    "スコラマガジン": ("Scholar Magazine", "Scholar Magazine", "global"),
    "光彩書房": ("Kousai Shobou", "Kousai Shobou", "global"),
    "星海社": ("Seikaisha", "Seikaisha", "global"),
    "ゆずソフト": ("Yuzusoft", "Yuzusoft", "global"),
    "東京創元社": ("Tokyo Sogensha", "Tokyo Sogensha", "global"),
    "ビーイング": ("Being", "Being", "global"),
    "プリズムビジョン": ("Prism Vision", "Prism Vision", "global"),
    "朝日新聞社": ("The Asahi Shimbun", "The Asahi Shimbun", "global"),
    "ステラワース": ("Stellaworth", "Stellaworth", "global"),
    "哔哩哔哩游戏": ("bilibili Games", "bilibili Games", "global"),
    "橙光游戏": ("66RPG (Chengguang)", "66RPG (Chengguang)", "global"),
    "タツノコプロ": ("Tatsunoko Production", "Tatsunoko Production", "global"),
    "シンエイ動画": ("Shin-Ei Animation", "Shin-Ei Animation", "global"),
    "ユニバーサルJ": ("Universal J", "Universal J", "global"),
    "東京三世社": ("Tokyo Sanseisha", "Tokyo Sanseisha", "global"),
    "創美社": ("Soubisha", "Soubisha", "global"),
    "彗星社": ("Suiseisha", "Suiseisha", "global"),
    "スタジオぷYUKAI": ("Studio PuYUKAI", "Studio PuYUKAI", "global"),
    "ダブトゥーンスタジオ": ("Dubtoon Studio", "Dubtoon Studio", "global"),
    "KADOKAWA メディアファクトリー": ("KADOKAWA Media Factory", "KADOKAWA Media Factory", "global"),
    "暁Records": ("Akatsuki Records", "Akatsuki Records", "global"),
    "あかべぇそふとつぅ": ("AKABEiSOFT2", "AKABEiSOFT2", "global"),
    "東京アクティブNEETs": ("Tokyo Active NEETs", "Tokyo Active NEETs", "global"),
    "青磁ビブロス": ("Seiji Biblos", "Seiji Biblos", "global"),
    "HOLP出版": ("Holp Shuppan", "Holp Shuppan", "global"),
    "ソニーPCL": ("Sony PCL", "Sony PCL", "global"),
    "スタジオ1st": ("Studio 1st", "Studio 1st", "global"),
    "DOMO小組": ("DOMO Studio", "DOMO Studio", "global"),
    "キャラメルBOX": ("Caramel BOX", "Caramel BOX", "global"),
    "ALcotハニカム": ("ALcot Honeycomb", "ALcot Honeycomb", "global"),
    "暁WORKS": ("Akatsuki WORKS", "Akatsuki WORKS", "global"),
    "HAL研究所有限公司": ("HAL Laboratory", "HAL Laboratory", "global"),
    "中影年年": ("CMC Media", "CMC Media", "global"),
    "童夢": ("Daume", "Daume", "global"),
    "童梦": ("Daume", "Daume", "global"),
    "两点十分动漫": ("2:10 Animation", "2:10 Animation", "global"),
    "福煦影视": ("Foch Film", "Foch Film", "global"),
    "若鸿文化": ("Ruohong Culture", "Ruohong Culture", "global"),
    "桜桃書房": ("Outou Shobou", "Outou Shobou", "global"),
    "蒼竜社": ("Souryuusha", "Souryuusha", "global"),
    "久保書店": ("Kubo Shoten", "Kubo Shoten", "global"),
    "正文社": ("Rightman Publishing", "Rightman Publishing", "global"),

    # Dergiler & Platformlar
    "LINEマンガ": ("LINE Manga", "LINE Manga", "global"),
    "マガジンポケット": ("Magazine Pocket", "Magazine Pocket", "global"),
    "まんが4コマぱれっと": ("Manga 4-Koma Palette", "Manga 4-Koma Palette", "global"),
    "周刊Young Magazine": ("Weekly Young Magazine", "Weekly Young Magazine", "global"),
    "月刊Young Magazine": ("Monthly Young Magazine", "Monthly Young Magazine", "global"),
    "月刊ガンガンJOKER": ("Monthly Gangan Joker", "Monthly Gangan Joker", "global"),
    "月刊Gファンタジー": ("Monthly G Fantasy", "Monthly G Fantasy", "global"),
    "月刊G Fantasy": ("Monthly G Fantasy", "Monthly G Fantasy", "global"),
    "ちゃお": ("Ciao", "Ciao", "global"),
    "ゲッサン": ("Gessan", "Gessan", "global"),
    "少年マガジンエッジ": ("Shonen Magazine Edge", "Shonen Magazine Edge", "global"),
    "ヤングキング": ("Young King", "Young King", "global"),
    "Comic电击大王": ("Comic Dengeki Daioh", "Comic Dengeki Daioh", "global"),
    "月刊コミックジーン": ("Monthly Comic Gene", "Monthly Comic Gene", "global"),
    "顶通": ("Toptoon", "Toptoon", "global"),
    "咚漫": ("Dongman", "Dongman", "global"),
    "ヤングチャンピオン": ("Young Champion", "Young Champion", "global"),
    "月刊Comic Alive": ("Monthly Comic Alive", "Monthly Comic Alive", "global"),
    "ジャンプスクエア": ("Jump Square", "Jump Square", "global"),
    "ビッグコミック": ("Big Comic", "Big Comic", "global"),
    "ビッグコミックオリジナル": ("Big Comic Original", "Big Comic Original", "global"),
    "少年Jump+": ("Shonen Jump+", "Shonen Jump+", "global"),
    "となりのヤングジャンプ": ("Tonari no Young Jump", "Tonari no Young Jump", "global"),
    "裏サンデー": ("Ura Sunday", "Ura Sunday", "global"),
    "里Sunday": ("Ura Sunday", "Ura Sunday", "global"),
    "月刊COMICリュウ": ("Monthly Comic Ryu", "Monthly Comic Ryu", "global"),
    "月刊Afternoon": ("Monthly Afternoon", "Monthly Afternoon", "global"),
    "月刊Dragon Age": ("Monthly Dragon Age", "Monthly Dragon Age", "global"),
    "少女Comic": ("Sho-Comi", "Sho-Comi", "global"),
    "マンガボックス": ("Manga Box", "Manga Box", "global"),
    "ヤングエースUP": ("Young Ace UP", "Young Ace UP", "global"),
    "腾讯网动漫频道": ("Tencent Comics", "Tencent Comics", "global"),
    "月刊ビッグガンガン": ("Monthly Big Gangan", "Monthly Big Gangan", "global"),
    "月刊BIG GANGAN": ("Monthly Big Gangan", "Monthly Big Gangan", "global"),
    "漫画Action": ("Manga Action", "Manga Action", "global"),
    "コミックDAYS": ("Comic DAYS", "Comic DAYS", "global"),
    "コミックNewtype": ("Comic Newtype", "Comic Newtype", "global"),
    "シルフ": ("Sylph", "Sylph", "global"),
    "月刊flowers": ("Monthly flowers", "Monthly flowers", "global"),
    "マンガワン": ("MangaONE", "MangaONE", "global"),
    "ヤングコミック": ("Young Comic", "Young Comic", "global"),
    "月刊IKKI": ("Monthly IKKI", "Monthly IKKI", "global"),
    "别册Friend": ("Bessatsu Friend", "Bessatsu Friend", "global"),
    "コミックハイ!": ("Comic High!", "Comic High!", "global"),
    "月刊コロコロコミック": ("Monthly CoroCoro Comic", "Monthly CoroCoro Comic", "global"),
    "グランドジャンプ": ("Grand Jump", "Grand Jump", "global"),
    "起点中文网": ("Qidian", "Qidian", "global"),
    "Comic 百合姬": ("Comic Yuri Hime", "Comic Yuri Hime", "global"),
    "玛格丽特": ("Margaret", "Margaret", "global"),
    "别册Margaret": ("Bessatsu Margaret", "Bessatsu Margaret", "global"),
    "カクヨム": ("Kakuyomu", "Kakuyomu", "global"),
    "コミックブレイド": ("Comic Blade", "Comic Blade", "global"),
    "まんがタイムスペシャル": ("Manga Time Special", "Manga Time Special", "global"),
    "月刊少年天狼星": ("Monthly Shonen Sirius", "Monthly Shonen Sirius", "global"),
    "ウィングス": ("Wings", "Wings", "global"),
    "月刊アクション": ("Monthly Action", "Monthly Action", "global"),
    "月刊Action": ("Monthly Action", "Monthly Action", "global"),
    "有妖气": ("U17", "U17", "global"),
    "週刊ヤングサンデー": ("Weekly Young Sunday", "Weekly Young Sunday", "global"),
    "月刊コミックビーム": ("Monthly Comic Beam", "Monthly Comic Beam", "global"),
    "电击魔王": ("Dengeki Maoh", "Dengeki Maoh", "global"),
    "知音漫客": ("Zhiyin Manke", "Zhiyin Manke", "global"),
    "漫友": ("Story100 (Manyou)", "Story100 (Manyou)", "global"),
    "ジーンピクシブ": ("Gene pixiv", "Gene pixiv", "global"),
    "チャンピオンRED いちご": ("Champion RED Ichigo", "Champion RED Ichigo", "global"),
    "月刊！SPIRITS": ("Monthly! Spirits", "Monthly! Spirits", "global"),
    "電撃G's magazine": ("Dengeki G's Magazine", "Dengeki G's Magazine", "global"),
    "电击G's Comic": ("Dengeki G's Comic", "Dengeki G's Comic", "global"),
    "E★エブリスタ": ("E★Everystar", "E★Everystar", "global"),
    "少年Sunday S(Super)": ("Shonen Sunday S", "Shonen Sunday S", "global"),
    "Championタップ!": ("Champion Tap!", "Champion Tap!", "global"),
    "BE・LOVE": ("BE·LOVE", "BE·LOVE", "global"),
    "月刊COMIC ZENON": ("Monthly Comic Zenon", "Monthly Comic Zenon", "global"),
    "COMIC阿吽": ("COMIC Aun", "COMIC Aun", "global"),
    "周刊漫画Goraku": ("Weekly Manga Goraku", "Weekly Manga Goraku", "global"),
    "ネクストF": ("Next F", "Next F", "global"),
    "月刊GUNDAM A": ("Monthly Gundam Ace", "Monthly Gundam Ace", "global"),
    "好朋友": ("Nakayoshi", "Nakayoshi", "global"),
    "月刊Sunday GX": ("Monthly Sunday GX", "Monthly Sunday GX", "global"),
    "マンガMee": ("Manga Mee", "Manga Mee", "global"),
    "月刊マガジンZ": ("Monthly Magazine Z", "Monthly Magazine Z", "global"),
    "モバMAN": ("Moba MAN", "Moba MAN", "global"),
    "COMICリュエル": ("COMIC Ruelle", "COMIC Ruelle", "global"),
    "月刊": ("Aylık Dergi", "Monthly Magazine", "new"),

    # TV Ağları ve Yayın Kuşakları (TR Türkçe + EN İngilizce!)
    "月曜名作劇場": ("Pazartesi Başyapıt Tiyatrosu", "Monday Masterpiece Theater", "new"),
    "ドラマスペシャル": ("Özel Drama", "Drama Special", "new"),
    "スペシャルドラマ": ("Özel Drama", "Special Drama", "new"),
    "土曜オリジナルドラマ": ("Cumartesi Orijinal Draması", "Saturday Original Drama", "new"),
    "日曜オリジナルドラマ": ("Pazar Orijinal Draması", "Sunday Original Drama", "new"),
    "木曜ドラマ": ("Perşembe Draması", "Thursday Drama", "new"),
    "木曜ドラマF": ("Perşembe Draması F", "Thursday Drama F", "new"),
    "金曜ドラマ": ("Cuma Draması", "Friday Drama", "new"),
    "木曜劇場": ("Perşembe Tiyatrosu", "Thursday Theater", "new"),
    "日曜ミステリー": ("Pazar Gizem Kuşağı", "Sunday Mystery", "new"),
    "プレミアムドラマ": ("Premium Drama", "Premium Drama", "new"),
    "水曜ドラマ": ("Çarşamba Draması", "Wednesday Drama", "new"),
    "オトナの土ドラ": ("Yetişkinler İçin Cumartesi Draması", "Saturday Adult Drama", "new"),
    "火曜ドラマ": ("Salı Draması", "Tuesday Drama", "new"),
    "BS時代劇": ("BS Dönem Draması", "BS Period Drama", "new"),
    "水曜ミステリー9": ("Çarşamba Gizem 9", "Wednesday Mystery 9", "new"),
    "日曜ドラマ": ("Pazar Draması", "Sunday Drama", "new"),
    "日曜ワイド": ("Pazar Geniş Kuşağı", "Sunday Wide", "new"),
    "連続テレビ小説": ("Asadora (Sabah Dizisi)", "Asadora (Morning Drama)", "new"),
    "女と愛とミステリー": ("Kadın, Aşk ve Gizem", "Women, Love and Mystery", "new"),
    "特集ドラマ": ("Özel Yapım Drama", "Feature Drama", "new"),
    "ニコニコチャンネル": ("Niconico Channel", "Niconico Channel", "global"),
    "ドラマ25": ("Drama 25", "Drama 25", "global"),
    "月曜プレミア８": ("Pazartesi Prömiyer 8", "Monday Premiere 8", "new"),
    "金曜8時のドラマ": ("Cuma Saat 8 Draması", "Friday 8 PM Drama", "new"),
    "ブレイクマンデー24": ("Break Monday 24", "Break Monday 24", "global"),
    "月曜21時枠連続ドラマ": ("Pazartesi 21.00 Dizisi", "Monday 9 PM Drama Series", "new"),
    "月曜21時枠ドラマ": ("Pazartesi 21.00 Draması", "Monday 9 PM Drama", "new"),
    "木曜ミステリー": ("Perşembe Gizem Kuşağı", "Thursday Mystery", "new"),
    "フジテレビ「＋Ultra」": ("Fuji TV +Ultra", "Fuji TV +Ultra", "global"),
    "シンドラ": ("SHINDORA", "SHINDORA", "global"),
    "韩国SBS电视台": ("SBS", "SBS", "global"),
    "韩国KBS电视台": ("KBS", "KBS", "global"),
    "韩国MBC电视台": ("MBC", "MBC", "global"),
    "土曜ナイトドラマ": ("Cumartesi Gece Draması", "Saturday Night Drama", "new"),
    "木ドラ25": ("Mokudora 25", "Mokudora 25", "global"),
    "水曜21時枠刑事ドラマ": ("Çarşamba 21.00 Polisiye Dizisi", "Wednesday 9 PM Detective Drama", "new"),
    "独立UHF局": ("Bağımsız UHF İstasyonları", "Independent UHF Stations", "new"),
    "ドラマL": ("Drama L", "Drama L", "global"),
    "ドラマパラビ": ("Drama Paravi", "Drama Paravi", "global"),
    "ドラマ特区": ("Drama Tokku", "Drama Tokku", "global"),
    "よるドラ": ("Gece Draması (Yorudora)", "Yorudora (Night Drama)", "new"),
    "NHK-Eテレ": ("NHK E-Tele", "NHK E-Tele", "global"),
    "BSスカパー!": ("BS Sky PerfecTV!", "BS Sky PerfecTV!", "global"),
    "ドラマ特別企画": ("Özel Drama Projesi", "Drama Special Project", "new"),
    "J:COMテレビ": ("J:COM TV", "J:COM TV", "global"),
    "プレミアムよるドラマ": ("Premium Gece Draması", "Premium Night Drama", "new"),
    "フジテレビ noitaminA": ("Fuji TV noitaminA", "Fuji TV noitaminA", "global"),
    "フジテレビ ノイタミナ": ("Fuji TV noitaminA", "Fuji TV noitaminA", "global"),
    "每日放送": ("MBS", "MBS", "global"),
    "真夜中ドラマ": ("Gece Yarısı Draması", "Midnight Drama", "new"),
    "哥伦比亚广播公司 CBS": ("CBS", "CBS", "global"),
    "WEB放送": ("Web Yayını", "Web Broadcast", "new"),
    "NHK教育频道": ("NHK Educational TV", "NHK Educational TV", "global"),
    "ドラマBiz": ("Drama Biz", "Drama Biz", "global"),
    "卡酷少儿": ("KAKU Children's Channel", "KAKU Children's Channel", "global"),
    "优漫卡通": ("Youman Cartoon", "Youman Cartoon", "global"),

    # Önceki Etiket Kanonlarındaki Bilinen Global & Kavram Kaçakları (count>=20)
    "软房子": ("SoftHouseChara", "SoftHouseChara", "global"),
    "设绘": ("Konsept Tasarımı", "Concept Art", "new"),
    "雷火剣": ("Raikaken", "Raikaken", "global"),
    "雷火剑": ("Raikaken", "Raikaken", "global"),
    "佐和真中": ("Manaka Sawa", "Manaka Sawa", "global"),
    "木原音濑": ("Narise Konohara", "Narise Konohara", "global"),
    "木原音瀬": ("Narise Konohara", "Narise Konohara", "global"),
    "水月陵": ("Ryo Mizutsuki", "Ryo Mizutsuki", "global"),
    "御苑生メイ": ("Mei Misonoo", "Mei Misonoo", "global"),
    "秘封组": ("Gizli Mühür Kulübü", "Secret Sealing Club", "new"),
    "桐谷华": ("Hana Kiritani", "Hana Kiritani", "global"),
    "M・A・O": ("M·A·O", "M·A·O", "global"),
    "佐々木未来": ("Mikoi Sasaki", "Mikoi Sasaki", "global"),
    "櫻井真人": ("Masato Sakurai", "Masato Sakurai", "global"),
    "中惠光城": ("Mitsuki Nakae", "Mitsuki Nakae", "global"),
    "蛇足": ("Dasoku", "Dasoku", "global"),
    "星山博之": ("Hiroyuki Hoshiyama", "Hiroyuki Hoshiyama", "global"),
    "綾倉盟": ("Mei Ayakura", "Mei Ayakura", "global"),
    "门胁舞以": ("Mai Kadowaki", "Mai Kadowaki", "global"),
    "元麻布ファクトリー": ("Motoazabu Factory", "Motoazabu Factory", "global"),
    "雪碧社": ("sprite", "sprite", "global"),
    "一本道": ("Doğrusal Kurgu", "Linear Plot", "new"),
    "白发魔女": ("Beyaz Saçlı Cadı", "White-Haired Witch", "new"),
    "箱庭": ("Minyatür Bahçe (Hakoniwa)", "Miniature Garden (Hakoniwa)", "new"),
    "山中真寻": ("Masahiro Yamanaka", "Masahiro Yamanaka", "global"),
    "橘子班": ("Orangeban", "Orangeban", "global"),
    "江口亮": ("Ryo Eguchi", "Ryo Eguchi", "global"),
    "望公太": ("Kota Nozomi", "Kota Nozomi", "global"),
    "夏海公司": ("Koji Natsumi", "Koji Natsumi", "global"),
    "乳袋": ("Göğüs Cebi", "Boob Socks", "new"),
    "小鸟居夕花": ("Yuuka Kotorii", "Yuuka Kotorii", "global"),
    "水城雪可奈": ("Setona Mizushiro", "Setona Mizushiro", "global"),
    "女王": ("Kraliçe", "Queen", "new"),
    "腰乃": ("Koshino", "Koshino", "global"),
    "百鬼夜行抄": ("Hyakki Yakoushou", "Hyakki Yakoushou", "global"),
    "战旗": ("Taktik RYO", "Tactical RPG", "new"),
    "粘土": ("Kil Animasyonu", "Claymation", "new"),
    "皇帝": ("İmparator", "Emperor", "new"),
    "教育片": ("Eğitici Yapım", "Educational", "new"),
    "小黄油": ("Yetişkin Oyun (Eroge)", "Eroge", "new"),
    "秀良子": ("Ryoko Shuu", "Ryoko Shuu", "global"),
    "岸本友彦": ("Tomohiko Kishimoto", "Tomohiko Kishimoto", "global"),
    "蘑菇": ("Kinoko Nasu", "Kinoko Nasu", "global"),
    "市川量也": ("Kazuya Ichikawa", "Kazuya Ichikawa", "global"),
    "石濱翔": ("Kakeru Ishihama", "Kakeru Ishihama", "global"),
    "佐藤健悦": ("Kenetsu Sato", "Kenetsu Sato", "global"),
    "佐本二厘": ("Nirin Samoto", "Nirin Samoto", "global"),
    "藤原祐": ("Yuu Fujiwara", "Yuu Fujiwara", "global"),
    "風見周": ("Meguru Kazami", "Meguru Kazami", "global"),
    "佐藤純一": ("Junichi Sato", "Junichi Sato", "global"),
    "稚野鳥子": ("Toriko Chiya", "Toriko Chiya", "global"),
    "大槻ケンヂ": ("Kenji Ohtsuki", "Kenji Ohtsuki", "global"),
    "逢空万太": ("Manta Aisora", "Manta Aisora", "global"),
    "ななじ眺": ("Nagamu Nanaji", "Nagamu Nanaji", "global"),
    "红心": ("Kupa", "Hearts", "new"),
    "千之ナイフ": ("Knife Senno", "Knife Senno", "global"),
    "潜入": ("Gizlilik", "Stealth", "new"),
    "尾崎武士": ("Takeshi Ozaki", "Takeshi Ozaki", "global"),
    "伊福部武史": ("Takeshi Ifukube", "Takeshi Ifukube", "global"),
    "関島眞頼": ("Mayori Sekijima", "Mayori Sekijima", "global"),
    "秀吉": ("Hideyoshi", "Hideyoshi", "global"),
    "岡田和人": ("Kazuto Okada", "Kazuto Okada", "global"),
    "米光亮": ("Ryo Yonemitsu", "Ryo Yonemitsu", "global"),
    "吉森信": ("Makoto Yoshimori", "Makoto Yoshimori", "global"),
    "池袋": ("Ikebukuro", "Ikebukuro", "global"),
    "ぢゅん子": ("Junko", "Junko", "global"),
    "有肉": ("Erotik Sahneli", "Smut", "new"),
    "海冬レイジ": ("Reiji Kaitou", "Reiji Kaitou", "global"),
    "时代漫画": ("Dönem Mangası", "Period Manga", "new"),
    "有葉": ("Alpha (Aruha)", "Alpha (Aruha)", "global"),
    "高城リョウ": ("Ryou Takagi", "Ryou Takagi", "global"),
    "八宝備仁": ("Happoubi Jin", "Happoubi Jin", "global"),
    "深田愛衣": ("Ai Fukada", "Ai Fukada", "global"),
    "新田佑克": ("Youka Nitta", "Youka Nitta", "global"),
    "美甲": ("Tırnak Sanatı", "Nail Art", "new"),
    "藤村緋二": ("Akeji Fujimura", "Akeji Fujimura", "global"),
    "北都南": ("Minami Hokuto", "Minami Hokuto", "global"),
    "三浦祥朗": ("Hiroaki Miura", "Hiroaki Miura", "global"),
    "山形石雄": ("Ishio Yamagata", "Ishio Yamagata", "global"),
    "安藤高弘": ("Takahiro Ando", "Takahiro Ando", "global"),
    "受苦": ("Zorlu / Cezalandırıcı", "Punishing", "new"),
    "邪道": ("Alışılmışın Dışı", "Unorthodox", "new"),
    "涼香": ("Ryohka", "Ryohka", "global"),
    "冲麻实也": ("Mamiya Oki", "Mamiya Oki", "global"),
    "詩月カオリ": ("Kaori Utatsuki", "Kaori Utatsuki", "global"),
    "竜ノ湖太郎": ("Tarou Tatsunoko", "Tarou Tatsunoko", "global"),
    "女神": ("Tanrıça", "Goddess", "new"),
    "奥井康介": ("Kosuke Okui", "Kosuke Okui", "global"),
    "黑历史": ("Kara Geçmiş", "Dark Past", "new"),
    "樋野茉莉": ("Matsuri Hino", "Matsuri Hino", "global"),
    "御影瑛路": ("Eiji Mikage", "Eiji Mikage", "global"),
    "上古": ("Kadim Dönem", "Ancient Era", "new"),
    "紀律部隊": ("Üniformalı Teşkilat", "Uniformed Services", "new"),
    "鳳乃一真": ("Kazuma Otorino", "Kazuma Otorino", "global"),
    "聴猫芝居": ("Shibai Kineko", "Shibai Kineko", "global"),
    "大川茂伸": ("Shigenobu Okawa", "Shigenobu Okawa", "global"),
    "神葉理世": ("Rize Shinba", "Rize Shinba", "global"),
    "大羽隆廣": ("Takahiro Oba", "Takahiro Oba", "global"),
    "海豹社": ("AZAseal", "AZAseal", "global"),
    "本名ワコウ": ("Wakou Honna", "Wakou Honna", "global"),
    "切符": ("kippu", "kippu", "global"),

    # count=0 seed orgs non-MASTER CJK TR entries
    "攻殻機動隊製作委員会": ("Ghost in the Shell Yapım Komitesi", "Ghost in the Shell Production Committee", "new"),
    "光坂高校演劇部": ("Kōsaka Lisesi Tiyatro Kulübü", "Kōsaka High School Drama Club", "new"),
    "大澤雅彦": ("Masahiko Osawa", "Masahiko Osawa", "global"),
    "田中渉": ("Wataru Tanaka", "Wataru Tanaka", "global"),
    "日本アニメ音楽出版": ("Nihon Anime Music Publishing", "Nihon Anime Music Publishing", "global"),
    "渡瀬悠宇パーフェクトワールド ふしぎ遊戯": ("Watase Yuu Perfect World: Fushigi Yuugi", "Watase Yuu Perfect World: Fushigi Yuugi", "global"),
}


def load_locked_master_keys() -> set[str]:
    locked: set[str] = set()
    for p in (MASTER_ORGS, MASTER_TAGS):
        for raw in p.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.lstrip().startswith("#"):
                continue
            locked.add(raw.split("\t")[0])
    return locked


def load_pending_master_org_keys() -> set[str]:
    pending: set[str] = set()
    for raw in MASTER_ORGS.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) > 4 and parts[4].strip() == "pending":
            pending.add(parts[0])
    return pending


def build_opencc_latin_index() -> dict[str, tuple[str, str]]:
    """Depodaki doğrulanmış Latin kurum/etiket karşılıklarından OpenCC (jp2t+t2s) indeksi."""
    idx: dict[str, tuple[str, str]] = {}
    for raw in (ROOT / "app/src/main/resources/bangumi/orgs.tsv").read_text(encoding="utf-8").splitlines():
        if not raw:
            continue
        p = raw.split("\t")
        lbl, en = p[0], p[2]
        tr = p[3] if len(p) > 3 else en
        if not has_cjk(en) and not has_cjk(tr) and len(cjk_norm(lbl)) >= 3:
            idx.setdefault(cjk_norm(lbl), (tr.strip(), en.strip()))
    for raw in (DEC / "org_global_names.tsv").read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        p = raw.split("\t")
        if not has_cjk(p[1]) and len(cjk_norm(p[0])) >= 2:
            idx.setdefault(cjk_norm(p[0]), (p[1].strip(), p[1].strip()))
    return idx


def main() -> int:
    locked_keys = load_locked_master_keys()
    pending_org_keys = load_pending_master_org_keys()
    opencc_idx = build_opencc_latin_index()

    tr_xml = {k: unescape(v) for k, v in re.findall(r'<string name="([^"]+)">(.*?)</string>', catalog_text(RES_TR))}
    en_xml = {k: unescape(v) for k, v in re.findall(r'<string name="([^"]+)">(.*?)</string>', catalog_text(RES_EN))}

    # 6 non-MASTER CJK TR seed string entries -> clean TR/EN in strings.xml directly first
    xml_updates: dict[str, tuple[str, str]] = {}
    for k, cur_tr in list(tr_xml.items()):
        if k in locked_keys or not k.startswith("bangumi_"):
            continue
        if cur_tr in VERIFIED_FIXES:
            ntr, nen, _ = VERIFIED_FIXES[cur_tr]
            xml_updates[k] = (ntr.strip(), nen.strip())

    if xml_updates:
        for xml_root, pick in [(RES_TR, 0), (RES_EN, 1)]:
            for path in resource_paths(xml_root):
                text = path.read_text(encoding="utf-8")
                changed = False
                for k, vals in xml_updates.items():
                    pat = re.compile(rf'(<string name="{re.escape(k)}">).*?(</string>)')
                    new_text, n = pat.subn(rf"\g<1>{escape(vals[pick])}\g<2>", text)
                    if n:
                        text = new_text
                        changed = True
                if changed:
                    path.write_text(text, encoding="utf-8")
        tr_xml = {k: unescape(v) for k, v in re.findall(r'<string name="([^"]+)">(.*?)</string>', catalog_text(RES_TR))}
        en_xml = {k: unescape(v) for k, v in re.findall(r'<string name="([^"]+)">(.*?)</string>', catalog_text(RES_EN))}

    # Parse canon.tsv lines preserving comments & ordering
    canon_lines = (DEC / "canon.tsv").read_text(encoding="utf-8").splitlines()
    canon_by_rc: dict[tuple[str, str], list[str]] = {}
    val_owner: dict[tuple[str, str, str], str] = {}  # (role, norm_tr, norm_en) -> cid

    for raw in canon_lines:
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        p = raw.split("\t")
        while len(p) < 7:
            p.append("")
        cid, role, tr, en = p[0], p[1], p[2], p[3]
        canon_by_rc[(role, cid)] = p
        if not has_cjk(tr) and not has_cjk(en):
            val_owner.setdefault((role, tr.strip().lower(), en.strip().lower()), cid)

    # Also register existing strings.xml owners per role so we never create duplicate values
    for k, tv in tr_xml.items():
        if not k.startswith("bangumi_"):
            continue
        parts = k.split("_", 2)
        if len(parts) < 3:
            continue
        role, cid = parts[1], parts[2]
        ev = en_xml.get(k, tv)
        if not has_cjk(tv) and not has_cjk(ev):
            val_owner.setdefault((role, tv.strip().lower(), ev.strip().lower()), cid)

    # Remap table for canon rows that merge into an existing owner of same (role, tr, en)
    remap_rc: dict[tuple[str, str], str] = {}  # (role, old_cid) -> existing_cid
    updated_in_place = 0

    for (role, cid), p in list(canon_by_rc.items()):
        full_key = f"bangumi_{role}_{cid}"
        if full_key in locked_keys:
            continue
        tr, en = p[2], p[3]
        if not has_cjk(tr) and not has_cjk(en):
            continue
        fix = VERIFIED_FIXES.get(tr) or VERIFIED_FIXES.get(en)
        if fix is None:
            hit = opencc_idx.get(cjk_norm(tr))
            if hit:
                fix = (hit[0], hit[1], "global")
        if fix is None:
            continue
        ntr, nen, norigin = fix[0].strip(), fix[1].strip(), fix[2]
        p[2] = ntr
        p[3] = nen
        p[4] = norigin
        p[6] = f"global/TR-EN düzeltmesi ({tr})"
        val_owner[(role, ntr.lower(), nen.lower())] = cid
        updated_in_place += 1

    # Build vk -> role lookup for all map_*.tsv rewrites
    vk_role: dict[str, str] = {}
    for role, fn in [
        ("tag", "tags.json"), ("studio", "studios.json"), ("producer", "producers.json"),
        ("publisher", "publishers.json"), ("magazine", "magazines.json"),
        ("network", "networks.json"), ("committee", "committees.json"),
    ]:
        for r in json.load((VAULT / fn).open(encoding="utf-8")):
            vk_role[r["key"]] = r.get("role") or role

    remapped_map_rows = 0
    if remap_rc:
        for mf in sorted(DEC.glob("map_*.tsv")):
            lines = mf.read_text(encoding="utf-8").splitlines()
            changed = False
            new_lines = []
            for raw in lines:
                if not raw.strip() or raw.lstrip().startswith("#"):
                    new_lines.append(raw)
                    continue
                p = raw.split("\t")
                vk, cid = p[0], p[1]
                rrole = vk_role.get(vk, vk.split("_", 2)[1] if vk.startswith("bangumi_") else "")
                if (rrole, cid) in remap_rc:
                    p[1] = remap_rc[(rrole, cid)]
                    if len(p) > 2:
                        p[2] = f"kanon birleştirme ({cid} → {p[1]})"
                    remapped_map_rows += 1
                    changed = True
                new_lines.append("\t".join(p))
            if changed:
                mf.write_text("\n".join(new_lines) + "\n", encoding="utf-8")

    # Rewrite canon.tsv cleanly (dropping remapped duplicate cids)
    out_canon_lines = []
    dropped_canon = 0
    for raw in canon_lines:
        if not raw.strip() or raw.lstrip().startswith("#"):
            out_canon_lines.append(raw)
            continue
        p = raw.split("\t")
        cid, role = p[0], p[1]
        if (role, cid) in remap_rc:
            dropped_canon += 1
            continue
        cur = canon_by_rc[(role, cid)]
        out_canon_lines.append("\t".join(cur))

    # ── Part 2: count=0 undecided orgs -> map_chunk_008_orgs.tsv ─────────────
    map_008_path = DEC / "map_chunk_008_orgs.tsv"
    if not map_008_path.exists():
        map_rows_008: list[str] = [
            "# Bangumi VAULT — chunk 008 · count=0 kurumsal tohum kayıtları (376 kayıt)",
            "# Biçim: vault_key <TAB> canon_id|SKIP <TAB> not",
        ]
        added_008_canon: list[str] = []
        for role, fn in [
            ("studio", "studios.json"), ("producer", "producers.json"),
            ("network", "networks.json"), ("publisher", "publishers.json"),
            ("magazine", "magazines.json"),
        ]:
            records = json.load((VAULT / fn).open(encoding="utf-8"))
            for rec in sorted(records, key=lambda r: (-(r.get("count") or 0), r["key"])):
                if rec.get("decision"):
                    continue
                vk = rec["key"]
                if vk in pending_org_keys:
                    map_rows_008.append(f"{vk}\tSKIP\tMASTER orgs.tsv pending — kaynakta doğrulanmadan alınmaz")
                    continue
                cid = vk.split("_", 2)[2]
                if vk in tr_xml:
                    tr_val = tr_xml[vk]
                    en_val = en_xml.get(vk, tr_val)
                else:
                    tr_val = (rec.get("tr") or "").strip()
                    en_val = (rec.get("en") or tr_val).strip()
                    if tr_val in VERIFIED_FIXES:
                        tr_val, en_val, _ = VERIFIED_FIXES[tr_val]
                # Check if same (role, tr_val, en_val) already owned in canon_by_rc
                owner = val_owner.get((role, tr_val.lower(), en_val.lower()))
                if owner and (role, owner) in canon_by_rc and vk not in locked_keys:
                    cid = owner
                elif (role, cid) not in canon_by_rc:
                    row_str = f"{cid}\t{role}\t{tr_val}\t{en_val}\texisting\t\tcount=0 tohum kurum kaydı"
                    added_008_canon.append(row_str)
                    canon_by_rc[(role, cid)] = row_str.split("\t")
                    val_owner[(role, tr_val.lower(), en_val.lower())] = cid
                map_rows_008.append(f"{vk}\t{cid}\tcount=0 tohum kurum kaydı")

        if added_008_canon:
            out_canon_lines.append("")
            out_canon_lines.append("# ── ORG kanonları (chunk 008 · count=0 tohum kurumlar) ──────────────────")
            out_canon_lines.extend(added_008_canon)
        map_008_path.write_text("\n".join(map_rows_008) + "\n", encoding="utf-8")
        print(f"map_chunk_008_orgs.tsv yazıldı: {len(map_rows_008) - 2} kayıt (+{len(added_008_canon)} yeni canon)")

    (DEC / "canon.tsv").write_text("\n".join(out_canon_lines) + "\n", encoding="utf-8")
    print(f"canon.tsv yerinde güncellenen CJK satırı: {updated_in_place}")
    print(f"mevcut kanona birleştirilen CJK ikizi: {dropped_canon} (güncellenen map satırı: {remapped_map_rows})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

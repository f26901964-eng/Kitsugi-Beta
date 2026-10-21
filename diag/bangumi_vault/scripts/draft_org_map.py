#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bangumi VAULT — org chunk taslak üreticisi (kural tabanlı, insan kararı yerine GEÇMEZ).

`decisions/org_global_names.tsv` bilgi tabanı + mekanik kurallarla
`decisions/map_chunk_<ID>_orgs.tsv` taslağı ve eksik `canon.tsv` satırlarını üretir.

Kurallar (sırayla):
  1. SKIP listesindeki adlar (ad parçası/gürültü) → SKIP.
  2. Bilgi tabanında ad geçiyorsa → latin_ad + canon_id (global karşılık).
  3. Ad Latin ise → as_is (tr=en=ad), canon_id = kayıt anahtarı soneki.
  4. Aksi hâlde (bilinmeyen CJK) → as_is (tr=en=ham ad), canon_id = anahtar soneki
     (uydurma romanizasyon YOK).
  5. canon_id + rol bir MEVCUT string anahtarına karşılık geliyorsa → existing
     (tr/en dil dosyasından alınır, mevcut değer korunur).

Çıktı yalnızca TASLAKTIR; `gen_org_chunk.py` sonrası elle gözden geçirilip
`apply_map.py` ile uygulanır.

Kullanım: python3 diag/bangumi_vault/scripts/draft_org_map.py --chunk 001 --min-count 100
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata

from pathlib import Path

from android_strings import unescape
from org_name_resolver import NameResolver

VAULT = Path(__file__).resolve().parents[1]
ROOT = VAULT.parents[1]
DEC = VAULT / "decisions"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

ORG_FILES = {
    "studio": "studios.json",
    "producer": "producers.json",
    "network": "networks.json",
    "publisher": "publishers.json",
    "magazine": "magazines.json",
}

# Ad parçası / gürültü — asla string'e girmez
SKIP_NAMES = {
    "エンタテインメント", "エンターテイメント", "エンターテイメントジャパン", "ジャパン",
    "マガジン", "メーカー", "プレス", "インディーズ", "インディーズレーベル",
    "インディペンデントレーベル", "パブリッシャー", "ホーム", "ビデオ", "マーケティング",
    "Inc", "GOLD", "ワーナー", "44℃梅毒", "ピクチャーズ株式会社(Music)", "ユニバーサル",
    # CHUNK 004a (2026-10-21): ad parçası / bölge / belirsiz tek kana — dil dosyasına girmez.
    # Not: bunlar kurum adının kendisi DEĞİL, adın bir parçası (ör. "XX エンターテイメント").
    "INC.", "Inc.", "出版", "コミックス", "ピクチャーズ", "スタジオ", "グループ", "エンターテイメント",
    "ミュージックエンタテインメント", "ソフトウェア", "マガジンズ",
    "エー", "ジェイ", "ジェーン", "メリー", "ケイ", "エス", "エム", "コウ",
    "スター", "週刊", "中国大陆", "BE", "LOVE",
    # CHUNK 004b (2026-10-21): ad parçası / tek kana / bozuk parça — dil dosyasına girmez.
    "株式会社", "ソフト", "放送", "放送網", "ビジュアル", "マンガ", "日本", "メディア",
    "メディアワ)", "オー", "エル", "ティー", "エヌ", "ケー", "ファースト",
    # CHUNK 004c (2026-10-21): bölge/şablon gürültüsü, URL parçası, hukuki kısaltma,
    # belirsiz kısaltma ve tek kana — dil dosyasına girmez (ekranda ham kalır).
    "台湾", "http:", "USA", "LLC", "PC", "SE", "ESP", "エフ",
    "発売元:モモグレ", "(オトメドラマ)", "インディーズ・メーカー",
    # CHUNK 005a (2026-10-21): bölge etiketi / jenerik kelime / kesik ad — dil dosyasına girmez.
    "台灣",
    # CHUNK 005a (2026-10-21): bölge etiketi / jenerik kelime / kesik ad — dil dosyasına girmez.
    "上海", "北京", "北美", "韩国", "(日本)", "中心", "最初版", "新装版", "増刊", "半月刊", "システム", "パブリッシャーズ", "アニメーションスタジオ", "エンタープライズ", "グローバル", "文化传播有限公司", "制作统括：", "发行", "企画协力：", "；宣传制片人：", "；製作協力：", ")；制作制片人：", "発売元:ひつじぐも", "(羽根ロム+α)", "△○□×", "【チーム", "10)", "電撃「マ 王", "→", ")→", "&フラワー", "(パルシィ)", "ヤング", "コミック", "オンライン", "レコード", "テレビ",
    # CHUNK 005b (2026-10-21): kesik ad / platform / zaman dilimi — dil dosyasına girmez.
    "角川書店(角川グループパブリッシング) (角川コミックス", "(PS2", "火曜21時枠", "冠名", "PS2", "土曜ドラマ24", "×DMM", "https:", "有我奉仕", "WAVE", "(ガトー)", "湖南美术出版社 天闻角川策划大陆新版", "游戏天堂", "カンパニー", "(PC)", "Ltd", "ミュージック", "テレビ東京系6局", "Ltd.", "ちょこくらげ 販売:密林社", "文传", "C79", "テレビジョン", "明日工作室 台湾原版", "スターツ出版; 新装版", "22)", "6)", "LTD.", "(株式会社ディアリード)", "制片：", "游戏天堂 中国大陆 ；", "；制作制片人：", "モンスターコミックス(", "蓝色", "制作概括：", "Global", "#627-", "ANIM", "#1-#332", "(1)", "PROJECT", "制作统括：本木夏", "科技有限公司", "「進撃の巨人」製作委員会(", "モンスターコミックス(がうがうモンスター)", "iOS", "文化投资有限公司", "土ドラ", "テレビ東京系6局ネット", "インターナショナル", "(ガンマ)", "国服", "プロジェクト", "コーポレーション", "(欧洲", "コンピュータエンタテインメント", "スーパープレミアム", "4)", "20)", "ゲームス", "アニメーション", "21)", "5150", "(PS2)", "8)", "(5", "LTD", "+音", "増刊号", "；制作统括：", "(简体中文版)", "エンタテイメント",
    # CHUNK 005c_a (2026-10-21): reviewed credit/platform/region/version artifacts.
    '#1-#974', '#333-#626', '#333-#940', '#975-',
    '(2)', '(2卷起)', '(4卷起)', '(5)',
    '(5卷起)', '(7)', '(9)', '(GBA',
    '(PC版)', '(PS', '(Xbox 360', '(分镜监修)',
    '(剧本)', '(北美)', '(台湾)', '(总监修)',
    '(番外篇)', '(繁体中文版)', ')×', '02)',
    '11)', '18)', '2)', '7)',
    'BS', 'CS', 'FC', 'C78',
    'PS3', 'PSP', 'Steam版', 'Web',
    'Web版', 'ガンガンONLINEのアプリ版', 'LINE Digital Frontier; 第1版', 'リブレ出版; 〔新装〕版',
    '一迅社; 初回限定版', '少年画報社; Ｂ6判サイズコミックス版', '文庫版', '新潮社; B6版',
    '土曜24時枠の連続ドラマ', '日曜夜9時枠連続ドラマ', '简体中文版', '繁体中文版',
    '英文版', '製作人', '講談社; 新装版', '动画制作统括：',
    '动画制作制片人：松尾洸甫', '；企划协力：', '；企画制片：大森啓幸', '；企画协力：清水広美',
    '；企画协力：阿部知司', '；制片：', '；动画制片人：', '；现场制片人：',
    '制作担当：', '制作担当', 'アニメーションスーパーバイザー', 'エンタテイメント[',
    'テレビ朝日系全国24局ネット“NUMAnimation”枠', 'フジテレビ開局60周年特別企画', 'テレビ東京系列局', 'テレビドラマ',
    'フジテレビ系列局', 'テレビ系', '日本テレビ系列局', 'オムニバステレビドラマ',
    'CS放送', 'TBS系全国28局ネット“スーパーアニメイズム”枠', 'コミュニケーションズ株式会社', 'デジタルエンタテインメント',
    'エンターブレイン; リマスタ-', '科技发展有限公司', '广东', '广州',
    '欧美', '美国', '欧洲', '港台',
    '中国大陆 ；', '亚洲)', '全球', '有限公司',
    '网络', '未知', '工作室', '动画',
    'チーム', 'ティ', '文庫', '少年',
    '「真',
    # CHUNK 005c_b/c (2026-10-21): explicit narrow-pattern artifact review.
    '#1 - #4 →', '#1-#626 →(TMS)', '#1046- ；東京ムービー→TMS', '#5 - #7 →',
    '#627-#992', '#7', '#8-10', '( *)',
    '( 企画协力 )', '(1', '(10卷起)', '(11',
    '(11)', '(13', '(13)', '(13卷起)',
    '(14', '(17)', '(1992)', '(1999)',
    '(2', '(20)', '(2003', '(2003)',
    '(2004)', '(28卷起)', '(3', '(3)',
    '(4', '(4)', '(6)', '(6卷起)',
    '(8', '(8)', '(8卷起)', '(9',
    '(9卷起)', '(Arcade)', '(DOS', '(DOS 中国大陆)',
    '(DOS)', '(DOS版)', '(DS', '(GBA)',
    '(GBC)', '(MD)', '(NS', '(NS)',
    '(Nintendo Switch', '(PC', '(PC Steam版)', '(PC-9801)',
    '(PCE)', '(PS Vita)', '(PS)', '(PS2 日本',
    '(PS3', '(PS4', '(PSP', '(PSP 日本)',
    '(PSP 欧美)', '(PSP)', '(Remastered)', '(Remastered版)',
    '(SS)', '(Wii', '(Wii)', '(Xbox 360)',
    '(Xbox)', '(iOS', '(remaster)', '(中国)',
    '(中国大陆)；拉闊遊戲(Java版)', '(中国大陆2009年9月19日至今)', '(中国大陆首版', '(中文版)',
    '(北美零售版)', '(台湾)；上海育碧', '(台湾初版', '(教科監修)',
    '(日版)', '(番外)', '(短篇)', '(移动版)',
    '(第5工作室) 第1~14话 →', '(美国)', '(责任编辑', ') (PS3 & Xbox 360)',
    ') -many milk-', ') DOS', ') PS2', ') RISE 2018 AUTUMN',
    ') Xbox', ') ×', ') × SHIN-EI ANIMATION(', ') × 花王',
    ') 中国大陆', ') 亚洲', ') ；出品人：', ')×ぶっだ',
    ')‖製作協力：', ')→デンシバーズ(→', ')→月刊ヤングキング(月刊YOUNG KING アワーズGH)→', ')；',
    ')；制作制片人：室井瑛一', ')；制作：菊堂工作室', ')；製作协力：', ')；製作協力：',
    '03)', '08)', '1)', '12)',
    '13)', '130)', '132)', '133)',
    '15)', '17)', '19)', '2002)',
    '2014)', '23)', '26)', '2600)',
    '28)', '29)', '2卷起', '3)',
    '32)', '35)', '5)', '5200)',
    '6卷起', '9)', '; B6版', '; 初版',
    ';Armature Studio;Iron Galaxy Studios', 'Android', 'Android 2014年12月25日', 'Android 中国大陆',
    'Android)', 'C73', 'C75', 'C76',
    'C80', 'C91', 'Co.', 'DOS',
    'DS', 'DS)', 'GBA', 'GBC',
    'KADOKAWA; 特装版', 'NDS and Wii)', 'NDS 欧洲', 'NHK出版; 四六版',
    'NS', 'NS 日本', 'NS)', 'NS中文版',
    'Nintendo Switch', 'Nintendo Switch)', 'Nintendo Switch版)', 'PC 台湾',
    'PC 欧美', 'PC 繁体中文', 'PC)', 'PCE',
    'PC版', 'PC版：企画デザイン工房 戦船', 'PS', 'PS Vita',
    'PS 北美', 'PS2 [Réalta Nua])', 'PS2 欧洲', 'PS2)',
    'PS3 繁体中文版)', 'PS3)', 'PS4', 'PS4)',
    'PS5', 'PSP & PC)', 'PSP)', 'PS版：ボンバーエクスプレス',
    'SS)', 'Wii', 'Wii U', 'Wii U)',
    'Wii)', 'Xbox', 'Xbox App', 'Xbox LIVE Arcade',
    'Xbox One', 'Xbox One 欧美', 'Xbox One)', 'Xbox)',
    '×AIC', "×LandQ Studio's", '×elf', '×gloops',
    '×ジャパングリーンハーツ', '×セヴァ', '×裏少年サンデーコミックス', '→ BLADE-ONLINE',
    '→ Oh SUPER JUMP Oh!', '→ WEBコミックアクション(', '→ web连载', '→ アスキー',
    '→ アフタヌーン増刊→', '→ ウンポコ', '→ 不明', '→ 内田康幸 → 因真一郎 キッズステーション',
    '→ 新潮社', '→ 月刊コミックブレイド', '→ 杉澤義文 ADK', '→ 漫友',
    '→ 移籍', '→ 電撃D-MANGAオンライン', '→@vitamin', '→COMIC 夢幻転生',
    '→Cocohana', '→Comic', '→FlexComixフレア', '→Garena 台湾',
    '→Gファンタジー', '→LINE Digital Frontier', '→LINEマンガ', '→LINEマンガオリジナル',
    '→OGPlanet→Papaya Play', '→QQ阅读', '→Quantize_', '→RADIX',
    '→WEB', '→WEBコミック', '→WEBコミックアクション(→', '→Web',
    '→Webコミック', '→comico', '→flying DOG 14话以后', '→webYJ',
    '→くらげバンチ', '→アクションピザッツスペシャル', '→ガンガンpixiv(', '→キャプテン翼マガジン',
    '→ケータイコミックブレイク→', '→コミックアヴァルス(Web)', '→コミック小学館ブックス(WEB)', '→コミプレ',
    '→コーラス(', '→ゴラクエッグ', '→ザ', '→ジャンプSQ',
    '→スピリッツ増刊 YSスペシャル', '→ゼノン編集部', '→テレまんがヒーローズ', '→デンシバーズ',
    '→ビッグガンガン', '→フレックスコミックス(3卷)', '→ブレイドオンライン', '→プレミア',
    '→ホラー＆ファンタジー倶楽部', '→マガジンGREAT→マガジンイーノ', '→マガジンZ∞', '→マガポケ(',
    '→モバイルフラワー', '→ヤングアニマル', '→ヤングジャンプシード', '→三宅将典',
    '→东立出版社', '→凛花→増刊フラワーズ', '→別冊ヤングマガジン→', '→別冊少年ジャンプ(',
    '→别册花与梦→ 花与梦→别册花与梦', '→双葉社Webマガジン', '→増刊', '→岩田伸一',
    '→怪', '→新雑誌研究所Web', '→日更计划(日更Comics)', '→最強ジャンプ',
    '→月マガ基地', '→月刊コミックブレイドアヴァルス', '→月刊フラワーズ', '→柴田宏明→櫻田博之→小山弘起→櫻田博之 東映動畫',
    '→梅澤淳稔→', '→樂意傳播→Naddic Japan 日本', '→水曜日のシリウス(', '→水曜日のシリウス(ニコニコ静画)',
    '→涉谷謙太郎→情野誠人→髙木明梨須→狩野雄太 富士電視台', '→漫友', '→画楽ノ杜(→', '→神村靖宏',
    '→福建美术出版社→湖南美术出版社→江苏凤凰文艺出版社', '→空中カリカチュア', '→藤山太一郎→小中ももこ', '→赤マルジャンプ→',
    '→週プレNEWS', '→週プレNEWSWeb', '→週刊', '→銀魂公式アプリ',
    '→電撃PlayStation『ソードアート', '→電書バト', '→风炫漫画', 'あさ出版; 四六判版',
    'ふゅーじょんぷろだくと; 特装版', 'アニメーション制作：diomedea(', 'アニメーション制作：シャフト(', 'アニメーション制作：ディオメディア(',
    'アニメーション制作：ボンズ(', 'エンターブレイン; 初版', 'グリーンアロー; B6版', 'フランス書院; 初版',
    'フレックスコミックス; B6判版', 'フロンティアワークス; 2版', 'フロンティアワークス; 初版', 'ブライト出版; A5判',
    'マイクロマガジン社; A5判版', 'マッグガーデン; 新装版', 'ワニマガジン社; A5版', '企画协力：中野博之',
    '企画协力：宮澤震', '企画协力：細野修平', '企画协力：高倉誠司', '共同制作：',
    '共同製作：', '制作：', '制作：スカルマン製作委員会', '制作：上海绘界文化传播有限公司(',
    '制作：原徹', '土曜8時枠の連続ドラマ', '土曜9時枠', '大垣書店; 新装版',
    '大陆', '妖精村; 初版', '宙出版; 新装版', '少年画報社; A5判サイズコミックス版',
    '幻冬舎コミックス; 新装版', '廣済堂出版; 新装版', '徳間書店; 初版版', '徳間書店; 新装版',
    '徳間書店; 第2版', '总制作：北京', '日曜21時ドラマ枠', '日曜23時台ドラマ枠',
    '書肆侃侃房; B6版', '月曜10時枠の連続ドラマ', '月曜21時枠の連続ドラマ', '月曜9時枠の連続ドラマ',
    '月曜夜10時枠の連続ドラマ', '月曜夜９時枠ドラマ', '有限会社', '木曜9時枠の連続ドラマ',
    '水曜10時枠の連続ドラマ', '水曜10時枠の連続ドラマ枠', '泰文堂; Ｂ６版', '火曜10時枠連続ドラマ',
    '火曜21時枠ドラマ', '火曜22時枠', '火曜22時枠の連続ドラマ', '火曜9時枠の連続ドラマ',
    '火曜夜10時枠の連続ドラマ', '火曜夜9時枠の連続ドラマ', '発売元:キュピラボ', '発売元:株式会社叶音',
    '発売元:歴sing♪Project', '発売元：ロータスミュージック', '白泉社; 愛蔵版', '秋田書店; B6判版',
    '秋田書店; 初版版', '联合制作：上海', '股份有限公司', '英国',
    '製作:', '製作：', '製作：OADノラガミ製作委員会', '製作：ねらわれた学園製作委員会',
    '角川書店; 特装版', '講談社; 特装版', '金曜21時枠連続ドラマ', '金曜深夜1時枠の深夜ドラマ',
    '集英社; 新書版', '集英社; 新装版', '香港', '； 企画担当：池内謙一郎',
    '；97話 - 同友アニメーション 63話 -', '；CGI制片人：', '；CG制作人：濱中裕', '；Gary Sierra 英文版',
    '；MFC.編集部', '；MyGO!!!!!制片人：日下亜美', '；NIS America', '；テレビマガジン',
    '；ラインプロデューサー：上坂陽一郎；制作制片人：森岡秀匡；助理制作制片人：斉藤弥生', '；ラインプロデューサー：上坂陽一郎；宣传制片人：古舘凜子', '；ラインプロデューサー：相馬紹二', '；上田太地',
    '；东品游戏', '；企划制片人：', '；企划担当：池内謙一郎', '；企画制片人：若林悠紀',
    '；企画制片：鈴木哲史', '；企画协力：MIXER', '；企画协力：「ウマ娘 プリティーダービー」ゲーム開発', '；企画协力：三村泰之',
    '；企画协力：中山卓也', '；企画协力：中嶋嘉美', '；企画协力：中野博之', '；企画协力：久保田尋木 芳文社编辑',
    '；企画协力：久田和子', '；企画协力：佐藤茂薫', '；企画协力：加藤浩嗣', '；企画协力：北村州識',
    '；企画协力：千葉素久', '；企画协力：吉田和生', '；企画协力：吉田啓悟', '；企画协力：大森啓幸',
    '；企画协力：大河原健', '；企画协力：宇田川芳彦', '；企画协力：宮嶋耕平', '；企画协力：山内真弥',
    '；企画协力：山川浩介', '；企画协力：岩本昌子', '；企画协力：嶋崎英郎', '；企画协力：川窪慎太郎',
    '；企画协力：扶川愛美', '；企画协力：斎藤比香里', '；企画协力：服部尚貴', '；企画协力：木下亮',
    '；企画协力：栗田宏俊', '；企画协力：森田德仁 アイル', '；企画协力：樽見亜紀子', '；企画协力：樽見亜紀子 マッグガーデン',
    '；企画协力：江波戸憲司', '；企画协力：波多野公士', '；企画协力：濵田健二', '；企画协力：矢作康介',
    '；企画协力：神戸優貴', '；企画协力：菊池貞和', '；企画协力：西本修', '；企画协力：野﨑修史',
    '；企画协力：金井暁', '；企画协力：鈴木一司', '；企画协力：長谷川貴広', '；企画协力：飯塚寿雄',
    '；企画协力：飯島江美子', '；企画协力：高橋慎', '；企画协力：高瀬透子 フジテレビ', '；企画协力：鳥山浩',
    '；企画協力：', '；企画協力：塩出達也 ハルタ編集部', '；企画協力：小林由美子(芳文社编辑)', '；企画協力：石村賢',
    '；企画協力：秋元直樹', '；企画担当：池内謙一郎；企画协力：BANDAI SPIRITS', '；企画统括：', '；共同企划：',
    '；共同出品：', '；共同製作：', '；共同製作：植田浩史', '；共同製作：竹内寛',
    '；出品人：青山勝樹', '；出品： 魔女の旅々製作委員会', '；出品：イエスタデイをうたって製作委員会', '；出品：ゾンビランドサガ リベンジ製作委員会(',
    '；初期表记：', '；制作Producer：松永康佑', '；制作制片人：上間康弘', '；制作制片人：中川二郎',
    '；制作制片人：中村陽介', '；制作制片人：久保竣公', '；制作制片人：井上博明', '；制作制片人：伊達毅',
    '；制作制片人：和氣澄賢；宣传制片人：下山永遠', '；制作制片人：小林涼', '；制作制片人：小澤一由', '；制作制片人：岡恒成',
    '；制作制片人：岩佐岳', '；制作制片人：岩瀬安輝', '；制作制片人：松尾亮一郎', '；制作制片人：森岡秀匡；现场制片人：上坂陽一郎',
    '；制作制片人：橋本和典', '；制作制片人：熊谷侑也；制片协力：', '；制作制片人：芦川真理子&豊田智紀', '；制作制片人：豊田智紀；宣传制片人：後藤優；经纪制片人：二方由紀子 マッドハウス',
    '；制作制片人：近藤光', '；制作总指挥：', '；制作概括：', '；制作统括:',
    '；制作统括：和氣澄賢', '；制作统括：徳永智広；宣传制片人：林原祥一', '；制作统括：浄園祐', '；制片人协力：',
    '；制片协力：大島靖', '；制片：EGG FIRM', '；制片：ハピネットファントム', '；加瀬未来',
    '；动作监修：', '；动画', '；动画制作制片人：松尾洸甫', '；动画制片人：今尾圭吾',
    '；动画制片人：寺川英和', '；动画制片人：黒木類', '；协力：', '；協力：',
    '；南谷佳', '；原作企画：', '；吉川広太郎', '；宣传制片人：三輪靖史',
    '；宣传制片人：上原温', '；宣传制片人：八重樫太良', '；宣传制片人：前田吉輝', '；宣传制片人：向井紘一郎',
    '；宣传制片人：塚本祐一朗', '；宣传制片人：大貫佑介', '；宣传制片人：天野望美', '；宣传制片人：太田今日子',
    '；宣传制片人：實川昂太', '；宣传制片人：小西隆之', '；宣传制片人：岩佐貴博', '；宣传制片人：斎木美香',
    '；宣传制片人：曹甲奕', '；宣传制片人：津末達也', '；宣传制片人：湯口隆明', '；宣传制片人：田邊彩樹',
    '；宣传制片人：石川朱里；CGI制作人：', '；宣传制片人：菅健太郎', '；宣传制片人：菅原貴範', '；宣传制片人：菅謙太郎',
    '；宣传制片人：藤島航大', '；宣传制片人：里野健太', '；宣传制片人：野呂瀬友里', '；宣传制片人：関口諒',
    '；宣传制片人：青山哲也', '；宣传制片人：高橋里美', '；山口真', '；平木もも',
    '；建模监修：瀬尾大樹', '；早川忠継', '；映画「けいおん!」製作委員会', '；月刊コミック@バンチ',
    '；松尾英児', '；玄沙愛', '；监制：', '；监制：李蓁',
    '；统括：高瀬透子(フジテレビ)', '；综合制片人：', '；美术监修：佐藤勝', '；英田理志',
    '；茂為科技→樂意傳播 港台', '；荒木美也子', '；行田健太郎', '；製作协力：',
    '；製作协力：木村有子', '；谷尚人', '；辅佐：', '；道具监修：',
    '；金子勉', '；音乐制片人：大隅啓良', '；高橋亚希人', '；高橋亜希人',
    # CHUNK 005c_c: malformed escaped citation / combined network field.
    'Butyes! (Sony BMG)\\"', '\\"No', 'Fox \\ Cartoon Network \\ The Hub Network',
    # CHUNK 005c: reviewed Latin generic fragments (not institution names).
    'COMICS', 'COMPANY', 'CORPORATION', 'Comic',
    'Corporation', 'Digital', 'Entertainment', 'GAMES',
    'Games', 'INC.', 'Inc', 'Inc.',
    'LTD', 'LTD.', 'MUSIC', 'None',
    'Online', 'Productions', 'Publishing', 'STUDIO',
    'Studio', 'TV', 'UNKNOWN', 'Unknown',
    'comic', 'comics', 'game', 'games',
    'inc', 'inc.', 'ltd', 'ltd.',
    'studio',
}

LATIN = re.compile(r"[A-Za-z]")


def slug(value: str) -> str:
    v = unicodedata.normalize("NFKC", value).lower()
    v = re.sub(r"[^a-z0-9]+", "_", v).strip("_")
    return v


def load_knowledge() -> dict[str, tuple[str, str]]:
    out: dict[str, tuple[str, str]] = {}
    for raw in (DEC / "org_global_names.tsv").read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        parts = raw.split("\t")
        name, latin = parts[0], parts[1]
        cid = parts[2] if len(parts) > 2 and parts[2] else slug(latin)
        if name in out and out[name] != (latin, cid):
            print(f"uyarı: bilgi tabanı çelişkisi {name!r}: {out[name]} vs {(latin, cid)}",
                  file=sys.stderr)
        out[name] = (latin, cid)
    return out


def load_strings_orgs() -> dict[str, tuple[str, str]]:
    tr = dict(re.findall(r'<string name="(bangumi_(?:org|tag|studio|producer|network|publisher|magazine)_[^"]+)">(.*?)</string>',
                         RES_TR.read_text(encoding="utf-8")))
    en = dict(re.findall(r'<string name="(bangumi_(?:org|tag|studio|producer|network|publisher|magazine)_[^"]+)">(.*?)</string>',
                         RES_EN.read_text(encoding="utf-8")))
    return {k: (unescape(v), unescape(en.get(k, v))) for k, v in tr.items()
            if not k.startswith("bangumi_tag_") or v == en.get(k, v)}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--chunk", required=True)
    ap.add_argument("--min-count", type=int, required=True)
    # DÜZELTME (2026-10-21): chunk 004+ kurum bantlarını (ör. 60-99) bölerek işlemek için
    # üst sınır eklendi. Varsayılan None = eski davranış (üst sınır yok).
    ap.add_argument("--max-count", type=int, default=None)
    args = ap.parse_args()
    destination = DEC / f"map_chunk_{args.chunk}_orgs.tsv"
    if destination.exists():
        ap.error(f"{destination.name} zaten var; taslağı/damgayı sessizce ezmek yasak")
    if args.max_count is not None and args.max_count < args.min_count:
        ap.error("max-count min-count değerinden küçük olamaz")

    know = load_knowledge()
    strs = load_strings_orgs()
    existing_canon = {}
    for line in (DEC / "canon.tsv").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            fields = line.split("\t")
            existing_canon[(fields[1], fields[0])] = fields
    resolver = NameResolver(strs, existing_canon)
    map_rows: list[tuple[str, str, str]] = []
    canon_rows: dict[tuple[str, str], tuple[str, str, str, str, str]] = {}  # (role,cid) → (tr,en,origin,note)

    for role, fn in ORG_FILES.items():
        with (VAULT / fn).open(encoding="utf-8") as f:
            records = json.load(f)
        for rec in sorted(records, key=lambda r: (-(r.get("count") or 0), r["key"])):
            count = rec.get("count") or 0
            if rec.get("decision") or count < args.min_count:
                continue
            if args.max_count is not None and count > args.max_count:
                continue
            name = rec.get("tr") or ""
            if name in SKIP_NAMES:
                map_rows.append((rec["key"], "SKIP", f"ad parçası/gürültü: {name}"))
                continue

            latin = note = None
            origin = None
            cid = None
            hit = know.get(name) or next(
                ((know[l]) for l in rec.get("labels", []) if l in know), None)
            if hit:
                latin, cid = hit
                origin = "global"
                note = f"global karşılık: {name} → {latin}"
            else:
                key_suffix = rec["key"].split("_", 2)[2] if rec["key"].startswith("bangumi_") else rec["key"]
                cid = key_suffix
                if LATIN.search(name):
                    latin = name
                    note = "Latin ad — as_is"
                else:
                    latin = name
                    note = "bilinmeyen CJK — as_is (uydurma romanizasyon yok)"
                origin = "as_is"

            cid, resolved = resolver.resolve(role, cid, latin, origin, note, raw_name=name)
            tr_en = resolved[:2]
            origin, note, string_key = resolved[2:]

            map_rows.append((rec["key"], cid, note))
            ck = (role, cid)
            if ck in canon_rows and canon_rows[ck][:2] != tr_en:
                print(f"uyarı: canon çelişkisi {ck}: {canon_rows[ck][:2]} vs {tr_en} ({rec['key']})",
                      file=sys.stderr)
            canon_rows.setdefault(ck, (tr_en[0], tr_en[1], origin, note, string_key))

    if not map_rows:
        ap.error("bantta kararsız kayıt yok; damgalı veriden boş map üretilemez")

    out = DEC / f"map_chunk_{args.chunk}_orgs.tsv"
    band = "count>=%d" % args.min_count if args.max_count is None else \
        "count %d-%d" % (args.min_count, args.max_count)
    lines = ["# Bangumi VAULT — chunk %s · ORG haritası (%s · %d kayıt) — TASLAK (draft_org_map.py)"
             % (args.chunk, band, len(map_rows)),
             "# Biçim: vault_key <TAB> canon_id|SKIP <TAB> not"]
    lines += [f"{k}\t{c}\t{n}" for k, c, n in map_rows]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{out.name}: {len(map_rows)} satır")

    # canon.tsv'e eksik satırları ekle (var olanlara dokunma)
    existing_text = (DEC / "canon.tsv").read_text(encoding="utf-8")
    have = set()
    for raw in existing_text.splitlines():
        if raw.strip() and not raw.lstrip().startswith("#"):
            p = raw.split("\t")
            have.add((p[1], p[0]))
    add = []
    for (role, cid), (tr, en, origin, note, string_key) in sorted(canon_rows.items()):
        if (role, cid) not in have:
            row = f"{cid}\t{role}\t{tr}\t{en}\t{origin}\t\t{note}"
            if string_key:
                row += f"\t{string_key}"
            add.append(row)
    if add:
        block = "\n# ── ORG kanonları (chunk %s · draft_org_map.py) ──────────────────────────\n" % args.chunk
        with (DEC / "canon.tsv").open("a", encoding="utf-8") as f:
            f.write(block + "\n".join(add) + "\n")
    print(f"canon.tsv: +{len(add)} satır")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

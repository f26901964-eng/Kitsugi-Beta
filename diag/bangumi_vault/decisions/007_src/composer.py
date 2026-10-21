# -*- coding: utf-8 -*-
"""Chunk 007 (59.510 etiket) — 4 katmanlı deterministik karar üreteci.

Katman 0: MASTER test kilitleri ("ASOBI" -> SKIP vb.)
Katman 1: Mekanik SKIP süzgeci (takvim/etkinlik/puan/kişisel raf/kırpılmış yorum/gürültü)
Katman 2: OpenCC (`jp2t` + `t2s`) + sargı temizleme + CamelCase ayrıştırma ile
          depo çapında tam eşleşme (`tags.tsv`, `canon.tsv`, `orgs.tsv`,
          `org_global_names.tsv`, `VERIFIED_FIXES`, `CJK_LEXICON`)
Katman 3: Sıfır-halüsinasyonlu Morfolojik Kavram & Seri Birleştirici
          (TR gerçek Türkçe + EN gerçek İngilizce)
Katman 4: Saf Latin özel adlar (`global`) ve doğrulanamayan CJK adlar (`as_is`,
          `cjk_<md5[:6]>`, TR=EN=ad, sıfır uydurma romanizasyon)
"""
from __future__ import annotations

import hashlib
import os
import re
import sys
import unicodedata
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape

import opencc

VAULT_ROOT = Path(__file__).resolve().parents[2]
REPO_ROOT = VAULT_ROOT.parents[1]
sys.path.insert(0, str(VAULT_ROOT / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from clean_cjk_canon_and_chunk008 import VERIFIED_FIXES  # noqa: E402
_EAST_ASIAN_RE = re.compile(r'[\u2e80-\u9fff\u3040-\u30ff\u31f0-\u31ff\uf900-\ufaff\uff65-\uff9f\uac00-\ud7af]')
def has_cjk(s: str) -> bool:
    return bool(_EAST_ASIAN_RE.search(s))
from lexicon import CJK_LEXICON, SEED_TR_UPGRADES  # noqa: E402
import resource_catalog  # noqa: E402
from resource_catalog import resource_paths  # noqa: E402
APP_TR = REPO_ROOT / 'app/src/main/res/values/strings.xml'
APP_EN = REPO_ROOT / 'app/src/main/res/values-en/strings.xml'

def _xml_unescape(v: str) -> str:
    return (v.replace("\\'", "'").replace('\\"', '"')
            .replace('\\@', '@').replace('\\?', '?')
            .replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>'))

def load_string_map(root_xml: Path) -> dict[str, str]:
    src = resource_catalog.catalog_text(root_xml)
    return {k: _xml_unescape(v) for k, v in re.findall(r'<string name="([^"]+)">(.*?)</string>', src, re.S)}

CC_JP2T = opencc.OpenCC("jp2t")
CC_T2S = opencc.OpenCC("t2s")

TAGS_TSV = REPO_ROOT / "app/src/main/resources/bangumi/tags.tsv"
ORGS_TSV = REPO_ROOT / "app/src/main/resources/bangumi/orgs.tsv"
CANON_TSV = VAULT_ROOT / "decisions/canon.tsv"
MASTER_TAGS_TSV = REPO_ROOT / "diag/bangumi_master/tags.tsv"

# BangumiMasterDictionaryTest.kt assertNull kilidi
LOCKED_SKIP_LABELS = {"ASOBI", "asobi"}

CJK_DIGITS = {
    "零": 0, "〇": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
    "五": 5, "六": 6, "七": 7, "八": 8, "九": 9, "十": 10,
}


def cjk_fold(text: str) -> str:
    s = unicodedata.normalize("NFKC", text).strip()
    try:
        s = CC_T2S.convert(CC_JP2T.convert(s))
    except Exception:
        pass
    return s.casefold()


def norm_key(text: str) -> str:
    s = cjk_fold(text)
    s = re.sub(r"[\s_\-・·/／\\|+＋&＆:：;；,，.。!！?？'\"`~〜～()（）\[\]【】「」『』《》〈〉<>]+", "", s)
    return s


WRAP_PAIRS = [
    ("《", "》"), ("『", "』"), ("「", "」"), ("【", "】"),
    ("〈", "〉"), ("[", "]"), ("(", ")"), ("（", "）"), ("\"", "\""), ("'", "'"),
]
DECOR_CHARS = "★☆❀✿❁❖◆◇■□●○▲△▼▽♪♫♬♡♥✦✧※→←↑↓"


def strip_wrappers(name: str) -> list[str]:
    """Dekoratif yıldız/parantez ve CamelCase/altçizgi varyantlarını üretir."""
    out: list[str] = []
    cur = unicodedata.normalize("NFKC", name).strip()
    if cur:
        out.append(cur)
    cleaned = cur.strip(DECOR_CHARS + " \t\u3000")
    if cleaned and cleaned not in out:
        out.append(cleaned)
    for lch, rch in WRAP_PAIRS:
        if cleaned.startswith(lch) and cleaned.endswith(rch) and len(cleaned) > 2:
            inner = cleaned[1:-1].strip()
            if inner and inner not in out:
                out.append(inner)
    # Altçizgi -> boşluk ("Studio_Ghibli" -> "Studio Ghibli")
    if "_" in cleaned and not has_cjk(cleaned):
        sp = re.sub(r"_+", " ", cleaned).strip()
        if sp and sp not in out:
            out.append(sp)
    #Saf ASCII CamelCase ("EvanCall" -> "Evan Call", "KeyVisualArts" -> "Key Visual Arts")
    if re.fullmatch(r"[A-Za-z][A-Za-z0-9]{3,}", cleaned):
        cc = re.sub(r"([a-z])([A-Z])", r"\1 \2", cleaned)
        cc = re.sub(r"([A-Z]{2,})([A-Z][a-z])", r"\1 \2", cc)
        if cc != cleaned and cc not in out:
            out.append(cc)
    return out


# ── 0) strings.xml + tags.tsv + canon.tsv İngilizce tohumları Türkçeleştirme ──
def apply_seed_tr_upgrades() -> int:
    """MASTER kilitli olmayan tr==en İngilizce kavramları TR olarak günceller."""
    locked_cids: set[str] = set()
    if MASTER_TAGS_TSV.exists():
        for line in MASTER_TAGS_TSV.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"):
                continue
            cols = line.split("\t")
            if cols:
                locked_cids.add(cols[0].strip())

    upgrades = {
        cid: (tr, en)
        for cid, (tr, en) in SEED_TR_UPGRADES.items()
        if cid not in locked_cids
    }
    if not upgrades:
        return 0

    # 1. TR strings.xml dosyalarını yerinde güncelle (yeni ID eklenmez!)
    entry_re = re.compile(r'(<string name="bangumi_tag_([a-z0-9_]+)">)(.*?)(</string>)')
    xml_patched = 0
    for tr_path in resource_paths(APP_TR):
        txt = tr_path.read_text(encoding="utf-8")
        changed = False

        def repl(m: re.Match[str]) -> str:
            nonlocal changed, xml_patched
            cid = m.group(2)
            if cid in upgrades:
                new_val = xml_escape(upgrades[cid][0]).replace("'", "\\'").replace('"', '\\"')
                if m.group(3) != new_val:
                    changed = True
                    xml_patched += 1
                    return f"{m.group(1)}{new_val}{m.group(4)}"
            return m.group(0)

        new_txt = entry_re.sub(repl, txt)
        if changed:
            tr_path.write_text(new_txt, encoding="utf-8")

    # 2. tags.tsv güncelle
    lines = TAGS_TSV.read_text(encoding="utf-8").splitlines()
    out_tags = []
    tags_patched = 0
    for line in lines:
        if not line:
            continue
        cols = line.split("\t")
        if cols[0] in upgrades:
            tr, _ = upgrades[cols[0]]
            if cols[1] != tr:
                cols[1] = tr
                tags_patched += 1
        out_tags.append("\t".join(cols))
    if tags_patched:
        TAGS_TSV.write_text("\n".join(out_tags) + "\n", encoding="utf-8")

    # 3. canon.tsv güncelle
    c_lines = CANON_TSV.read_text(encoding="utf-8").splitlines()
    out_canon = []
    canon_patched = 0
    for line in c_lines:
        if not line or line.startswith("#"):
            out_canon.append(line)
            continue
        cols = line.split("\t")
        if len(cols) >= 6 and cols[1] == "tag" and cols[0] in upgrades:
            tr, _ = upgrades[cols[0]]
            if cols[2] != tr:
                cols[2] = tr
                canon_patched += 1
        out_canon.append("\t".join(cols))
    if canon_patched:
        CANON_TSV.write_text("\n".join(out_canon) + "\n", encoding="utf-8")

    return max(xml_patched, tags_patched, canon_patched)


# ── Katman 1: Mekanik SKIP Süzgeci ───────────────────────────────────────────
SKIP_EXACT = {
    "已看", "未看", "在看", "想看", "抛弃", "弃番", "搁置", "待看", "待补", "补番", "补",
    "已读", "未读", "未読", "読了", "積読", "既読", "在读", "想读", "待读", "弃书",
    "已玩", "未玩", "在玩", "想玩", "待玩", "弃坑", "填坑", "通关", "已通关", "全通",
    "一周目", "二周目", "三周目", "多周目", "一周目结束", "一周目通", "全CG", "全成就",
    "全收集", "白金", "云通关", "视频通关", "云", "喜加一", "Steam喜加一",
    "已买", "已购", "未买", "待买", "购入", "已入", "已收", "收藏", "个人收藏",
    "实体", "实体书", "电子版", "电子书", "台版已收", "日版已收", "港版已收",
    "已经下载", "已下载", "网盘", "生肉", "熟肉", "汉化", "官中", "机翻", "啃生肉",
    "无汉化", "有汉化", "等汉化", "民间汉化", "汉化补丁", "中日双语",
    "想二刷", "二刷", "三刷", "N刷", "重温", "童年回忆", "童年", "回忆", "情怀",
    "愿望单", "心愿单", "待购", "观望", "预定", "神作", "佳作", "良作", "雷作", "粪作",
    "神作预定", "霸权", "烂尾", "腰斩", "太监", "有生之年", "待完结", "连载中", "未完结",
    "已完结", "完结撒花", "断更", "坑", "天坑", "万年坑",
    "一星", "二星", "三星", "四星", "五星", "半星", "普通", "一般", "还行", "不错", "推荐", "力荐", "较差", "很差",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
    "?", "？", "!", "！", "...", "…", "—", "-", "_", "+", "*", "/", "\\",
    "无", "なし", "無し", "其他", "其它", "その他", "不明", "未知", "待定", "暂无",
    "标记", "mark", "MARK", "Mark", "tag", "TAG", "todo", "TODO", "test",
}

SKIP_SUBSTRINGS = (
    "期待度", "个人向", "自用", "备份", "书单", "片单", "补完计划",
    "看着真", "真不错", "太好看", "值得一看", "非常喜欢", "一设党", "毁机游戏", "歌曲排行", "排行榜",
    "还没看", "没看完", "看不下去", "玩不下去", "买书如山倒", "百度云",
    "夸克", "磁力", "字幕组", "压制", "BDRip", "BDrip", "WEB-DL",
)

RE_SKIP_PATTERNS = [
    # Saf yıl / ay / tarih / çeyrek damgaları ("2014年10月", "1998年", "2022冬", "2020.07", "21年4月")
    re.compile(r"^(?:19|20)?\d{2}\s*[年./\-]\s*(?:\d{1,2}\s*[月日号]?|[春夏秋冬])?\s*(?:新番|番|动画|アニメ|ドラマ|电影|映画|游戏|漫画)?$"),
    re.compile(r"^(?:19|20)\d{2}\s*(?:[春夏秋冬]|Q[1-4]|年[春夏秋冬]|上半期|下半期|度)?(?:新番|番|动画)?$", re.I),
    re.compile(r"^\d{1,2}\s*月(?:新番|番)?$"),
    # Doujin etkinlik damgaları ("C97", "C102", "M3-28", "M3-2019春", "例大祭13", "COMITIA130")
    re.compile(r"^C\d{2,3}(?:[春夏秋冬])?$", re.I),
    re.compile(r"^M3[-\s]?(?:\d{2,4}[春夏秋冬]?|[0-9]{2})$", re.I),
    re.compile(r"^(?:博麗神社)?(?:春季|秋季)?例大祭\s*\d+$"),
    re.compile(r"^COMITIA\s*\d+[A-Z]?$", re.I),
    re.compile(r"^(?:紅楼夢|名華祭|神居祭|文々。新聞友の会|サンクリ|こみトレ|コミティア)\s*\d*$"),
    # Puan / skor / sıra damgaları ("7分", "8.5分", "★4", "☆☆☆☆", "+1", "6/10")
    re.compile(r"^[+-]?\d+(?:\.\d+)?\s*(?:分|点|星|pt|pts|/\d+)$", re.I),
    re.compile(r"^[★☆✦✧]{1,10}$"),
    re.compile(r"^第?\d+[集话話回章部篇页頁]$"),
    # Kişisel raf kalıpları
    re.compile(r"^(?:已|未|待|想|在)(?:看|读|読|玩|买|购|收|补|弃|通|入|下|阅).*$"),
    re.compile(r"^.*(?:已看|未看|已读|未读|已玩|未玩|已买|已购|已收|已入|已通关|一周目|二周目|三周目|喜加一|汉化版|生肉|熟肉)$"),
]


def check_skip(name: str) -> str | None:
    s = unicodedata.normalize("NFKC", name).strip()
    if not s:
        return "SKIP: boş etiket"
    if s in LOCKED_SKIP_LABELS:
        return "SKIP: MASTER test kilidi (belirsiz kısaltma)"
    if s in SKIP_EXACT or cjk_fold(s) in SKIP_EXACT:
        return "SKIP: kişisel durum / gürültü"
    # Yalnız noktalama / sembol / rakam
    if not any(ch.isalpha() for ch in s):
        return "SKIP: salt sayı / sembol"
    # Tek ASCII karakter veya 2 harfli belirsiz gürültü (tanınan kısaltmalar Katman 2'den önce kontrol edilir)
    if len(s) == 1 and ord(s[0]) < 128:
        return "SKIP: tek karakter"
    # Kırık kodlama (mojibake / kontrol karakteri / replacement char)
    if "\ufffd" in s or any(unicodedata.category(ch).startswith("C") for ch in s):
        return "SKIP: bozuk karakter kodlaması"
    for sub in SKIP_SUBSTRINGS:
        if sub in s or cjk_fold(sub) in cjk_fold(s):
            return "SKIP: kişisel raf / sürüm notu"
    for pat in RE_SKIP_PATTERNS:
        if pat.match(s):
            return "SKIP: tarih / etkinlik / raf damgası"
    # Tek karakter CJK belirsiz etiket (Katman 2 sözlüğünde eşleşmediyse gürültü/kesik damgadır)
    if has_cjk(s) and len(s) == 1:
        return "SKIP: tek karakter belirsiz CJK"
    # Kırpılmış kişisel yorum (... / … ile biten uzun CJK etiketi)
    if has_cjk(s) and len(s) >= 10 and (s.endswith("...") or s.endswith("…") or s.endswith("..")):
        return "SKIP: kırpılmış kişisel yorum"
    # Çok uzun kişisel cümle / yorum
    if has_cjk(s) and len(s) >= 24 and any(p in s for p in ("，", "。", "！", "？", "…", "、")):
        return "SKIP: kişisel yorum cümlesi"
    return None


# ── Katman 3: Morfolojik Kavram Birleştirici Kök ve Sonek Tabloları ───────────
# Yalnız %100 anlamı kesin kavram kökleri!
CONCEPT_ROOTS: dict[str, tuple[str, str]] = {
    "恋爱": ("Romantik", "Romance"),
    "爱情": ("Aşk", "Love"),
    "纯爱": ("Saf Aşk", "Pure Love"),
    "青春": ("Gençlik", "Youth"),
    "校园": ("Okul", "School"),
    "学园": ("Akademi / Okul", "Academy"),
    "日常": ("Günlük Yaşam", "Slice of Life"),
    "搞笑": ("Komedi", "Comedy"),
    "喜剧": ("Komedi", "Comedy"),
    "黑色幽默": ("Kara Mizah", "Dark Comedy"),
    "奇幻": ("Fantastik", "Fantasy"),
    "魔幻": ("Büyülü Fantastik", "Magical Fantasy"),
    "玄幻": ("Doğu Fantastiği", "Eastern Fantasy"),
    "仙侠": ("Xianxia", "Xianxia"),
    "武侠": ("Wuxia", "Wuxia"),
    "修真": ("Kültivasyon", "Cultivation"),
    "科幻": ("Bilim Kurgu", "Sci-Fi"),
    "异世界": ("İsekai (Başka Dünya)", "Isekai"),
    "穿越": ("Zaman / Dünya Geçişi", "Time / World Travel"),
    "转生": ("Reenkarnasyon", "Reincarnation"),
    "重生": ("Yeniden Doğuş", "Rebirth"),
    "冒险": ("Macera", "Adventure"),
    "动作": ("Aksiyon", "Action"),
    "战斗": ("Dövüş / Savaş", "Battle"),
    "格斗": ("Dövüş", "Fighting"),
    "热血": ("Ateşli / Coşkulu", "Hot-Blooded"),
    "悬疑": ("Gerilim / Gizem", "Suspense"),
    "推理": ("Polisiye / Çıkarım", "Mystery"),
    "侦探": ("Dedektiflik", "Detective"),
    "犯罪": ("Suç", "Crime"),
    "恐怖": ("Korku", "Horror"),
    "惊悚": ("Gerilim (Thriller)", "Thriller"),
    "灵异": ("Paranormal / Doğaüstü", "Supernatural"),
    "怪谈": ("Hayalet Öyküleri (Kaidan)", "Ghost Stories"),
    "猎奇": ("Grotesk / Tuhaf", "Bizarre / Grotesque"),
    "黑暗": ("Karanlık", "Dark"),
    "治愈": ("İyileştirici", "Healing"),
    "致郁": ("Karamsar", "Depressing"),
    "催泪": ("Göz Yaşartıcı", "Tearjerker"),
    "感动": ("Duygulandırıcı", "Moving"),
    "温馨": ("Sıcak / Samimi", "Heartwarming"),
    "家庭": ("Aile", "Family"),
    "亲情": ("Aile Bağları", "Family Bonds"),
    "友情": ("Dostluk", "Friendship"),
    "百合": ("Yuri (Kız Aşkı)", "Yuri"),
    "耽美": ("BL / Yaoi", "Boys' Love"),
    "腐向": ("BL / Fujoshi", "BL-Oriented"),
    "乙女": ("Otome", "Otome"),
    "女性": ("Kadın", "Female"),
    "男性": ("Erkek", "Male"),
    "少女": ("Şoujo / Genç Kız", "Shoujo"),
    "少年": ("Şounen / Genç Erkek", "Shounen"),
    "青年": ("Seinen / Genç Yetişkin", "Seinen"),
    "成人": ("Yetişkin", "Adult"),
    "全年龄": ("Tüm Yaşlar", "All-Ages"),
    "后宫": ("Harem", "Harem"),
    "逆后宫": ("Ters Harem", "Reverse Harem"),
    "机战": ("Meka Savaşı", "Mecha Combat"),
    "机甲": ("Meka", "Mecha"),
    "机器人": ("Robot", "Robot"),
    "军事": ("Askerî", "Military"),
    "战争": ("Savaş", "War"),
    "历史": ("Tarihî", "Historical"),
    "时代": ("Dönem", "Period"),
    "古风": ("Antik Tarz", "Ancient Style"),
    "和风": ("Geleneksel Japon Tarzı", "Japanese Style"),
    "中华": ("Çin Tarzı", "Chinese Style"),
    "西洋": ("Batı Tarzı", "Western Style"),
    "中世纪": ("Orta Çağ", "Medieval"),
    "末世": ("Kıyamet Sonrası", "Post-Apocalyptic"),
    "废土": ("Çorak Dünya (Wasteland)", "Wasteland"),
    "赛博": ("Siber", "Cyber"),
    "蒸汽": ("Buhar (Steam)", "Steam"),
    "太空": ("Uzay", "Space"),
    "宇宙": ("Evren / Uzay", "Space"),
    "未来": ("Gelecek", "Future"),
    "现代": ("Modern", "Modern"),
    "都市": ("Şehir", "Urban"),
    "职场": ("İş Hayatı", "Workplace"),
    "医疗": ("Tıp", "Medical"),
    "律政": ("Hukuk", "Legal"),
    "刑侦": ("Polisiye Soruşturma", "Criminal Investigation"),
    "政治": ("Politik", "Political"),
    "商战": ("İş Dünyası Rekabeti", "Business Warfare"),
    "美食": ("Gastronomi / Yemek", "Gourmet / Food"),
    "料理": ("Yemek Pişirme", "Cooking"),
    "运动": ("Spor", "Sports"),
    "竞技": ("Rekabetçi Spor", "Competitive"),
    "音乐": ("Müzik", "Music"),
    "偶像": ("İdol", "Idol"),
    "乐队": ("Müzik Grubu", "Band"),
    "舞蹈": ("Dans", "Dance"),
    "戏剧": ("Tiyatro / Drama", "Theater / Drama"),
    "演艺": ("Sahne Sanatları", "Entertainment Industry"),
    "声优": ("Seslendirme Sanatçısı", "Voice Actor"),
    "魔法": ("Büyü", "Magic"),
    "魔法少女": ("Büyülü Kız", "Magical Girl"),
    "超能力": ("Süper Güç", "Superpower"),
    "异能": ("Doğaüstü Yetenek", "Supernatural Ability"),
    "妖怪": ("Yokai / Doğaüstü Varlık", "Yokai"),
    "神魔": ("Tanrılar ve İblisler", "Gods and Demons"),
    "吸血鬼": ("Vampir", "Vampire"),
    "丧尸": ("Zombi", "Zombie"),
    "僵尸": ("Zombi / Jiangshi", "Zombie"),
    "恶魔": ("İblis", "Demon"),
    "天使": ("Melek", "Angel"),
    "魔女": ("Cadı", "Witch"),
    "勇者": ("Kahraman (Yuusha)", "Hero"),
    "魔王": ("İblis Kral", "Demon Lord"),
    "龙": ("Ejderha", "Dragon"),
    "猫": ("Kedi", "Cat"),
    "犬": ("Köpek", "Dog"),
    "动物": ("Hayvanlar", "Animals"),
    "宠物": ("Evcil Hayvan", "Pet"),
    "兽人": ("Antropomorfik (Furry)", "Furry / Kemono"),
    "人外": ("İnsan Dışı Varlık", "Non-Human"),
    "伪娘": ("Kız Kılığında Erkek (Otokonoko)", "Otokonoko"),
    "性转": ("Cinsiyet Değişimi", "Gender Bender"),
    "百合向": ("Yuri Temalı", "Yuri-Oriented"),
    "像素": ("Piksel Sanat", "Pixel Art"),
    "水墨": ("Mürekkep Resim", "Ink-Wash"),
    "黑白": ("Siyah Beyaz", "Black & White"),
    "全彩": ("Tam Renkli", "Full Color"),
    "四格": ("Dört Panelli (4-Koma)", "4-Koma"),
    "短篇": ("Kısa Öykü", "Short"),
    "中篇": ("Orta Uzunlukta", "Medium-Length"),
    "长篇": ("Uzun Soluklu", "Long-Form"),
    "独立": ("Bağımsız", "Indie"),
    "同人": ("Doujin / Hayran Yapımı", "Doujin"),
    "原创": ("Özgün Yapım", "Original"),
    "实验": ("Deneysel", "Experimental"),
    "教育": ("Eğitici", "Educational"),
    "科普": ("Popüler Bilim", "Popular Science"),
    "儿童": ("Çocuk", "Children's"),
    "哲学": ("Felsefi", "Philosophical"),
    "心理": ("Psikolojik", "Psychological"),
    "生存": ("Hayatta Kalma", "Survival"),
    "逃脱": ("Kaçış", "Escape"),
    "密室": ("Kapalı Oda", "Locked-Room"),
    "解谜": ("Bulmaca", "Puzzle"),
    "益智": ("Zekâ / Bulmaca", "Puzzle"),
    "策略": ("Strateji", "Strategy"),
    "战棋": ("Taktik RYO (SRPG)", "Tactical RPG"),
    "塔防": ("Kule Savunma", "Tower Defense"),
    "卡牌": ("Kart", "Card"),
    "养成": ("Yetiştirme", "Raising"),
    "经营": ("Yönetim", "Management"),
    "模拟": ("Simülasyon", "Simulation"),
    "射击": ("Nişancı", "Shooter"),
    "潜行": ("Gizlilik (Stealth)", "Stealth"),
    "弹幕": ("Mermi Cehennemi (Danmaku)", "Bullet Hell"),
    "肉鸽": ("Roguelike", "Roguelike"),
    "回合制": ("Sıra Tabanlı", "Turn-Based"),
    "即时": ("Gerçek Zamanlı", "Real-Time"),
    "开放世界": ("Açık Dünya", "Open World"),
    "角色扮演": ("Rol Yapma (RYO)", "Role-Playing"),
    "视觉": ("Görsel", "Visual"),
    "互动": ("Etkileşimli", "Interactive"),
    "日本": ("Japon", "Japanese"),
    "美国": ("Amerikan", "American"),
    "英国": ("İngiliz", "British"),
    "法国": ("Fransız", "French"),
    "德国": ("Alman", "German"),
    "韩国": ("Kore", "Korean"),
    "国产": ("Çin Yapımı", "Chinese"),
    "欧美": ("Batı Yapımı", "Western"),
    "台湾": ("Tayvan", "Taiwanese"),
    "香港": ("Hong Kong", "Hong Kong"),
}

CONCEPT_SUFFIXES: dict[str, tuple[str, str]] = {
    "动画": ("Animesi", "Anime"),
    "アニメ": ("Animesi", "Anime"),
    "番": ("Animesi", "Anime"),
    "漫画": ("Mangası", "Manga"),
    "コミック": ("Mangası", "Manga"),
    "マンガ": ("Mangası", "Manga"),
    "条漫": ("Webtoonu", "Webtoon"),
    "小说": ("Romanı", "Novel"),
    "小説": ("Romanı", "Novel"),
    "轻小说": ("Hafif Romanı", "Light Novel"),
    "文学": ("Edebiyatı", "Literature"),
    "故事": ("Hikâyesi", "Story"),
    "物语": ("Öyküsü", "Tale"),
    "物語": ("Öyküsü", "Tale"),
    "游戏": ("Oyunu", "Game"),
    "ゲーム": ("Oyunu", "Game"),
    "手游": ("Mobil Oyunu", "Mobile Game"),
    "网游": ("Çevrimiçi Oyunu", "Online Game"),
    "单机": ("Tek Oyunculu Oyunu", "Single-Player Game"),
    "RPG": ("RYO", "RPG"),
    "AVG": ("Macera Oyunu", "Adventure Game"),
    "ADV": (" Görsel Roman / ADV".strip(), "Visual Novel / ADV"),
    "SLG": ("Strateji / Simülasyon", "Strategy / Simulation"),
    "ACT": ("Aksiyon Oyunu", "Action Game"),
    "STG": ("Nişancı Oyunu", "Shooter"),
    "FTG": ("Dövüş Oyunu", "Fighting Game"),
    "GAL": ("Galgame", "Galgame"),
    "gal": ("Galgame", "Galgame"),
    "电影": ("Filmi", "Film"),
    "映画": ("Filmi", "Film"),
    "剧场版": ("Sinema Filmi", "Movie"),
    "劇場版": ("Sinema Filmi", "Movie"),
    "电视剧": ("Dizisi", "TV Series"),
    "日剧": ("Japon Dizisi", "Japanese Drama"),
    "韩剧": ("Kore Dizisi", "Korean Drama"),
    "美剧": ("Amerikan Dizisi", "American TV Series"),
    "英剧": ("İngiliz Dizisi", "British TV Series"),
    "剧": ("Dizisi / Draması", "Drama"),
    "ドラマ": ("Draması", "Drama"),
    "广播剧": ("Sesli Draması", "Audio Drama"),
    "ドラマCD": ("Drama CD'si", "Drama CD"),
    "音乐": ("Müziği", "Music"),
    "歌曲": ("Şarkısı", "Song"),
    "原声": ("Film Müziği (OST)", "Soundtrack"),
    "OST": ("Film Müziği (OST)", "OST"),
    "专辑": ("Albümü", "Album"),
    "短片": ("Kısa Filmi", "Short Film"),
    "短篇集": ("Kısa Öykü Seçkisi", "Short Story Collection"),
    "合集": ("Derlemesi", "Collection"),
    "选集": ("Antolojisi", "Anthology"),
    "アンソロジー": ("Antolojisi", "Anthology"),
    "画集": ("Sanat Kitabı", "Art Book"),
    "设定集": ("Tasarım Kitabı", "Setting Book"),
    "作品": ("Yapımı", "Work"),
    "题材": ("Temalı", "Theme"),
    "元素": ("Unsurları", "Elements"),
    "风格": ("Tarzı", "Style"),
    "风": ("Tarzı", "Style"),
    "系": ("Tarzı", "Type"),
    "向": ("Yönelik", "Oriented"),
    "类": ("Türü", "Genre"),
    "版": ("Sürümü", "Edition"),
    "篇": ("Bölümü / Arkı", "Arc"),
    "改": ("Uyarlaması", "Adaptation"),
    "改编": ("Uyarlaması", "Adaptation"),
}

FRANCHISE_SUFFIXES: dict[str, tuple[str, str]] = {
    "系列": ("{tr} Serisi", "{en} Series"),
    "シリーズ": ("{tr} Serisi", "{en} Series"),
    "剧场版": ("{tr} Filmi", "{en} Movie"),
    "劇場版": ("{tr} Filmi", "{en} Movie"),
    "动画": ("{tr} Animesi", "{en} Anime"),
    "漫画": ("{tr} Mangası", "{en} Manga"),
    "小说": ("{tr} Romanı", "{en} Novel"),
    "游戏": ("{tr} Oyunu", "{en} Game"),
    "原声": ("{tr} Film Müziği", "{en} Soundtrack"),
    "OST": ("{tr} OST", "{en} OST"),
    "OVA": ("{tr} OVA", "{en} OVA"),
    "OAD": ("{tr} OAD", "{en} OAD"),
    "外传": ("{tr} Yan Hikâyesi", "{en} Side Story"),
    "同人": ("{tr} Doujin", "{en} Doujin"),
}

ENGLISH_WORDS_TR: dict[str, str] = {
    "action": "Aksiyon", "adventure": "Macera", "anime": "Anime", "anthology": "Antoloji",
    "art": "Sanat", "artbook": "Sanat Kitabı", "audio": "Sesli", "battle": "Savaş",
    "board": "Masa", "book": "Kitap", "boys": "Erkek", "card": "Kart",
    "casual": "Gündelik", "character": "Karakter", "classic": "Klasik", "collection": "Koleksiyon",
    "comedy": "Komedi", "comic": "Çizgi Roman", "comics": "Çizgi Roman", "complete": "Tam",
    "cyber": "Siber", "dark": "Karanlık", "dating": "Flört", "defense": "Savunma",
    "detective": "Dedektif", "digital": "Dijital", "drama": "Drama", "dungeon": "Zindan",
    "edition": "Baskı", "ending": "Kapanış", "erotic": "Erotik", "escape": "Kaçış",
    "fandisc": "Fan Diski", "fantasy": "Fantastik", "female": "Kadın", "fiction": "Kurgu",
    "fighting": "Dövüş", "film": "Film", "free": "Ücretsiz", "full": "Tam",
    "game": "Oyun", "games": "Oyunları", "girl": "Kız", "girls": "Kız",
    "gothic": "Gotik", "guide": "Rehber", "hardcore": "Sıkı", "healing": "İyileştirici",
    "historical": "Tarihî", "horror": "Korku", "idol": "İdol", "image": "İmaj",
    "indie": "Bağımsız", "interactive": "Etkileşimli", "japanese": "Japon", "kinetic": "Kinetik",
    "light": "Hafif", "live": "Canlı", "love": "Aşk", "magic": "Büyü",
    "magical": "Büyülü", "male": "Erkek", "management": "Yönetim", "manga": "Manga",
    "mecha": "Meka", "medieval": "Orta Çağ", "military": "Askerî", "mini": "Mini",
    "mobile": "Mobil", "modern": "Modern", "monster": "Canavar", "movie": "Film",
    "music": "Müzik", "musical": "Müzikal", "mystery": "Gizem", "novel": "Roman",
    "official": "Resmî", "online": "Çevrimiçi", "open": "Açık", "opening": "Açılış",
    "original": "Orijinal", "ost": "OST", "otome": "Otome", "party": "Parti",
    "pixel": "Piksel", "platform": "Platform", "platformer": "Platform", "point": "Tıkla",
    "pop": "Pop", "post": "Sonrası", "psychological": "Psikolojik", "puzzle": "Bulmaca",
    "racing": "Yarış", "real": "Gerçek", "remake": "Yeniden Yapım", "remaster": "Yenilenmiş Sürüm",
    "retro": "Retro", "rhythm": "Ritim", "rock": "Rock", "roguelike": "Roguelike",
    "role": "Rol", "romance": "Romantik", "romantic": "Romantik", "rpg": "RYO",
    "sandbox": "Sandbox", "school": "Okul", "science": "Bilim", "scifi": "Bilim Kurgu",
    "secret": "Gizli", "series": "Serisi", "shooter": "Nişancı", "shooting": "Nişancı",
    "short": "Kısa", "side": "Yan", "simulation": "Simülasyon", "simulator": "Simülatörü",
    "single": "Tekli", "slice": "Kesit", "social": "Sosyal", "song": "Şarkı",
    "songs": "Şarkıları", "sound": "Ses", "soundtrack": "Film Müziği", "space": "Uzay",
    "special": "Özel", "spin": "Yan", "sports": "Spor", "stage": "Sahne",
    "stealth": "Gizlilik", "story": "Hikâye", "stories": "Hikâyeleri", "strategy": "Strateji",
    "supernatural": "Doğaüstü", "survival": "Hayatta Kalma", "suspense": "Gerilim",
    "tactical": "Taktik", "tactics": "Taktik", "text": "Metin", "theme": "Tema",
    "thriller": "Gerilim", "time": "Zaman", "tower": "Kule", "trading": " Koleksiyonluk".strip(),
    "turn": "Sıra", "tv": "TV", "urban": "Şehir", "version": "Sürümü",
    "video": "Video", "virtual": "Sanal", "visual": "Görsel", "vocal": "Vokal",
    "voice": "Ses", "war": "Savaş", "web": "Web", "western": "Batı", "world": "Dünya",
    "yuri": "Yuri",
}

ENGLISH_PHRASES_TR: dict[str, tuple[str, str]] = {
    "visual novel": ("Görsel Roman", "Visual Novel"),
    "kinetic novel": ("Kinetik Roman", "Kinetic Novel"),
    "sound novel": ("Sesli Roman (Sound Novel)", "Sound Novel"),
    "light novel": ("Hafif Roman", "Light Novel"),
    "slice of life": ("Günlük Yaşam", "Slice of Life"),
    "role playing": ("Rol Yapma Oyunu", "Role-Playing"),
    "role playing game": ("Rol Yapma Oyunu", "Role-Playing Game"),
    "turn based": ("Sıra Tabanlı", "Turn-Based"),
    "turn based strategy": ("Sıra Tabanlı Strateji", "Turn-Based Strategy"),
    "turn based rpg": ("Sıra Tabanlı RYO", "Turn-Based RPG"),
    "real time strategy": ("Gerçek Zamanlı Strateji", "Real-Time Strategy"),
    "tower defense": ("Kule Savunma", "Tower Defense"),
    "open world": ("Açık Dünya", "Open World"),
    "point and click": ("Tıkla ve İlerle", "Point-and-Click"),
    "hack and slash": ("Hack and Slash", "Hack and Slash"),
    "beat em up": ("Beat 'em up", "Beat 'em up"),
    "shoot em up": ("Shoot 'em up", "Shoot 'em up"),
    "bullet hell": ("Mermi Cehennemi (Bullet Hell)", "Bullet Hell"),
    "dating sim": ("Flört Simülasyonu", "Dating Sim"),
    "walking simulator": ("Yürüme Simülatörü", "Walking Simulator"),
    "pixel art": ("Piksel Sanatı", "Pixel Art"),
    "character song": ("Karakter Şarkısı", "Character Song"),
    "image song": ("İmaj Şarkısı", "Image Song"),
    "theme song": ("Tema Şarkısı", "Theme Song"),
    "insert song": ("Sahne İçi Şarkı", "Insert Song"),
    "original soundtrack": ("Orijinal Film Müziği", "Original Soundtrack"),
    "drama cd": ("Drama CD", "Drama CD"),
    "fan disc": ("Fan Diski", "Fan Disc"),
    "side story": ("Yan Hikâye", "Side Story"),
    "short story": ("Kısa Öykü", "Short Story"),
    "science fiction": ("Bilim Kurgu", "Science Fiction"),
    "urban fantasy": ("Şehir Fantastiği", "Urban Fantasy"),
    "dark fantasy": ("Karanlık Fantastik", "Dark Fantasy"),
    "high fantasy": ("Yüksek Fantastik", "High Fantasy"),
    "post apocalyptic": ("Kıyamet Sonrası", "Post-Apocalyptic"),
    "time travel": ("Zaman Yolculuğu", "Time Travel"),
    "parallel world": ("Paralel Dünya", "Parallel World"),
    "magical girl": ("Büyülü Kız", "Magical Girl"),
    "boys love": ("Erkek Aşkı (BL)", "Boys' Love"),
    "girls love": ("Kız Aşkı (GL)", "Girls' Love"),
    "early access": ("Erken Erişim", "Early Access"),
    "free to play": ("Oynaması Ücretsiz", "Free-to-Play"),
    "local multiplayer": ("Yerel Çok Oyunculu", "Local Multiplayer"),
    "co op": ("Eşli Oyun (Co-op)", "Co-op"),
    "deck building": ("Deste Kurma", "Deck Building"),
    "dungeon crawler": ("Zindan Keşfi", "Dungeon Crawler"),
    "interactive fiction": ("Etkileşimli Kurgu", "Interactive Fiction"),
    "live action": ("Canlı Çekim", "Live Action"),
    "music video": ("Müzik Videosu", "Music Video"),
    "art book": ("Sanat Kitabı", "Art Book"),
    "fan book": ("Hayran Kitabı", "Fan Book"),
    "guide book": ("Rehber Kitap", "Guide Book"),
    "all ages": ("Tüm Yaşlar", "All-Ages"),
}


def parse_small_cjk_num(s: str) -> int | None:
    if s.isdigit():
        return int(s)
    if s in CJK_DIGITS:
        return CJK_DIGITS[s]
    if len(s) == 2 and s[0] == "十" and s[1] in CJK_DIGITS:
        return 10 + CJK_DIGITS[s[1]]
    if len(s) == 2 and s[0] in CJK_DIGITS and s[1] == "十":
        return CJK_DIGITS[s[0]] * 10
    if len(s) == 3 and s[0] in CJK_DIGITS and s[1] == "十" and s[2] in CJK_DIGITS:
        return CJK_DIGITS[s[0]] * 10 + CJK_DIGITS[s[2]]
    return None


def make_slug(en_text: str, raw_fallback: str) -> str:
    ascii_norm = unicodedata.normalize("NFKD", en_text).encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-z0-9]+", "_", ascii_norm.lower()).strip("_")
    slug = re.sub(r"_+", "_", slug)
    if len(slug) > 48:
        slug = slug[:48].rstrip("_")
    if len(slug) < 3:
        slug = "tag_" + hashlib.md5(raw_fallback.encode("utf-8")).hexdigest()[:6]
    return slug


class TagResolver:
    """Tüm depo bilgisini yükler ve 59.510 etiketi deterministik olarak çözer."""

    def __init__(self) -> None:
        self.xml_tr = load_string_map(APP_TR)
        self.xml_en = load_string_map(APP_EN)

        # (cid -> (tr, en, origin)) yalnızca role == 'tag' olan kanonlar
        self.tag_canons: dict[str, tuple[str, str, str]] = {}
        # "(tr.lower(), en.lower())" -> cid (mevcut kanonu yeniden kullanmak için!)
        self.pair_to_cid: dict[tuple[str, str], str] = {}

        # Fold edilmiş anahtar -> (cid | None, tr, en, origin, source_note)
        self.fold_map: dict[str, tuple[str | None, str, str, str, str]] = {}
        self.norm_map: dict[str, tuple[str | None, str, str, str, str]] = {}

        self._load_all_stores()

        # Morfolojik tabloları da OpenCC fold ile genişlet
        self.roots_folded: dict[str, tuple[str, str]] = {}
        for k, v in CONCEPT_ROOTS.items():
            self.roots_folded[cjk_fold(k)] = v
        self.suffixes_folded: dict[str, tuple[str, str]] = {}
        for k, v in CONCEPT_SUFFIXES.items():
            self.suffixes_folded[cjk_fold(k)] = v
        self.franchise_sfx_folded: dict[str, tuple[str, str]] = {}
        for k, v in FRANCHISE_SUFFIXES.items():
            self.franchise_sfx_folded[cjk_fold(k)] = v

    def _register_lookup(self, label: str, cid: str | None, tr: str, en: str,
                         origin: str, note: str, overwrite: bool = False) -> None:
        if has_cjk(tr) or has_cjk(en):
            return
        fk = cjk_fold(label)
        nk = norm_key(label)
        if fk and (overwrite or fk not in self.fold_map):
            self.fold_map[fk] = (cid, tr, en, origin, note)
        if len(nk) >= 2 and (overwrite or nk not in self.norm_map):
            self.norm_map[nk] = (cid, tr, en, origin, note)

    def _load_all_stores(self) -> None:
        # 1. Mevcut tags.tsv (3.983 grup — en yüksek öncelik!)
        for raw_line in TAGS_TSV.read_text(encoding="utf-8").splitlines():
            if not raw_line:
                continue
            parts = raw_line.split("\t")
            g_key, g_tr, g_en, g_labels = parts[0], parts[1], parts[2], parts[3:]
            self.tag_canons[g_key] = (g_tr, g_en, "existing")
            self.pair_to_cid.setdefault((g_tr.casefold(), g_en.casefold()), g_key)
            for lbl in (g_tr, g_en, *g_labels):
                self._register_lookup(lbl, g_key, g_tr, g_en, "existing", f"tags.tsv:{g_key}", overwrite=True)

        # 2. Mevcut canon.tsv (role == 'tag' + diğer rollerden CJK olmayan global adlar)
        for line in CANON_TSV.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"):
                continue
            cols = line.split("\t")
            if len(cols) < 6:
                continue
            cid, role, tr, en, origin, aliases = cols[:6]
            if role == "tag":
                self.tag_canons.setdefault(cid, (tr, en, origin))
                if not has_cjk(tr) and not has_cjk(en):
                    self.pair_to_cid.setdefault((tr.casefold(), en.casefold()), cid)
                    for lbl in (tr, en, *[a for a in aliases.split(";") if a]):
                        self._register_lookup(lbl, cid, tr, en, "existing", f"canon(tag):{cid}")
            else:
                if not has_cjk(tr) and not has_cjk(en):
                    for lbl in (tr, en, *[a for a in aliases.split(";") if a]):
                        self._register_lookup(lbl, None, tr, en, "global", f"canon({role}):{cid}")

        # 3. Mevcut orgs.tsv (52.424 satır — stüdyo/yayımcı/plak şirketi global adları!)
        for line in ORGS_TSV.read_text(encoding="utf-8").splitlines():
            if not line:
                continue
            cols = line.split("\t")
            if len(cols) < 4:
                continue
            raw_lbl, cid, tr, en = cols[:4]
            if not has_cjk(tr) and not has_cjk(en):
                self._register_lookup(raw_lbl, None, tr, en, "global", f"orgs.tsv:{cid}")

        # 4. VERIFIED_FIXES + CJK_LEXICON (700+ tek tek doğrulanmış girdi!)
        for cid, (tr, en, origin) in VERIFIED_FIXES.items():
            self.pair_to_cid.setdefault((tr.casefold(), en.casefold()), cid)
        for raw_lbl, (pref_cid, tr, en, origin) in CJK_LEXICON.items():
            self._register_lookup(raw_lbl, pref_cid, tr, en, origin, f"lexicon:{pref_cid}", overwrite=False)

    def allocate_cid(self, preferred: str | None, tr: str, en: str,
                     origin: str, raw_name: str) -> tuple[str, str]:
        """Çakışmasız, deterministik ve `strings.xml` uyumlu `cid` tahsis eder."""
        # 1. Aynı (TR, EN) çiftine sahip mevcut bir tag kanonu zaten varsa onu kullan!
        existing_cid = self.pair_to_cid.get((tr.casefold(), en.casefold()))
        if existing_cid and existing_cid in self.tag_canons:
            c_tr, c_en, _ = self.tag_canons[existing_cid]
            res_key = f"bangumi_tag_{existing_cid}"
            if c_tr == tr and c_en == en and (res_key not in self.xml_tr or self.xml_tr[res_key] == tr):
                return existing_cid, "existing"

        base = preferred if preferred and re.fullmatch(r"[a-z0-9_]{3,64}", preferred) else make_slug(en, raw_name)
        for idx in range(0, 200):
            cand = base if idx == 0 else (f"{base}_tag" if idx == 1 else f"{base}_{idx}")
            res_key = f"bangumi_tag_{cand}"
            # Eğer bu cid zaten aynı (tr, en) ile kayıtlıysa paylaş!
            if cand in self.tag_canons:
                c_tr, c_en, _ = self.tag_canons[cand]
                if c_tr == tr and c_en == en and (res_key not in self.xml_tr or self.xml_tr[res_key] == tr):
                    return cand, origin
                continue
            # strings.xml kısa-cid / cross-role tuzağı kontrolü: XML'de zaten varsa yeni tag cid olarak ASLA alma!
            if res_key in self.xml_tr:
                continue
            self.tag_canons[cand] = (tr, en, origin)
            self.pair_to_cid[(tr.casefold(), en.casefold())] = cand
            return cand, origin
        raise RuntimeError(f"cid üretilemedi: {raw_name!r} -> ({tr!r}, {en!r})")

    def _try_morphology(self, s: str) -> tuple[str | None, str, str, str, str] | None:
        fs = cjk_fold(s)

        # A) Dönemler: "80年代" / "1990年代" / "00年代"
        m_dec = re.fullmatch(r"((?:18|19|20)?\d0)年代", fs)
        if m_dec:
            yr = m_dec.group(1)
            full_yr = yr if len(yr) == 4 else (f"20{yr}" if yr in ("00", "10", "20") else f"19{yr}")
            return (f"decade_{full_yr}s", f"{full_yr}'ler", f"{full_yr}s", "new", "morfoloji:dönem")

        # B) Cilt / Sezon / Bölüm tam etiketleri: "全5卷", "3卷完", "第2期", "全12话"
        m_vol = re.fullmatch(r"全([0-9一二两三四五六七八九十]{1,3})[卷巻册冊]", fs) or re.fullmatch(
            r"([0-9一二两三四五六七八九十]{1,3})[卷巻册冊]完", fs
        )
        if m_vol:
            n = parse_small_cjk_num(m_vol.group(1))
            if n and 1 <= n <= 200:
                tr = "Tek Cilt (Tamamlanmış)" if n == 1 else f"{n} Cilt (Tamamlanmış)"
                en = "1 Volume (Complete)" if n == 1 else f"{n} Volumes (Complete)"
                return (f"volumes_{n}_complete", tr, en, "new", "morfoloji:cilt")

        m_sea = re.fullmatch(r"第?([2-9二三四五六七八九十])(?:期|季)", fs)
        if m_sea:
            n = parse_small_cjk_num(m_sea.group(1))
            if n and 2 <= n <= 15:
                return (f"season_{n}", f"{n}. Sezon", f"Season {n}", "new", "morfoloji:sezon")

        # C) İki kavram kökü birleşimi ("校园恋爱" -> "Okul Romantizmi" ifadesi yerine "Okul / Romantik",
        #    "悬疑推理" -> "Gerilim ve Polisiye", "奇幻冒险" -> "Fantastik Macera")
        for split_i in range(2, len(fs) - 1):
            p1, p2 = fs[:split_i], fs[split_i:]
            if p1 in self.roots_folded and p2 in self.roots_folded:
                tr1, en1 = self.roots_folded[p1]
                tr2, en2 = self.roots_folded[p2]
                if tr1 != tr2:
                    return (None, f"{tr1} · {tr2}", f"{en1} {en2}", "new", "morfoloji:kök+kök")

        # D) <Kavram Kökü> + <Mecra/Biçim Soneki> ("治愈动画", "像素风", "女性向游戏", "四格漫画")
        for split_i in range(len(fs) - 1, 1, -1):
            stem, sfx = fs[:split_i], fs[split_i:]
            if stem in self.roots_folded and sfx in self.suffixes_folded:
                tr_r, en_r = self.roots_folded[stem]
                tr_s, en_s = self.suffixes_folded[sfx]
                return (None, f"{tr_r} {tr_s}", f"{en_r} {en_s}", "new", "morfoloji:kök+sonek")

        # E) <Bilinen Global Seri / Yaratıcı> + <系列 / シリーズ / 剧场版 / OST / OVA / 漫画 / 动画>
        for split_i in range(len(fs) - 1, 1, -1):
            stem, sfx = fs[:split_i], fs[split_i:]
            if sfx in self.franchise_sfx_folded and stem in self.fold_map:
                _, base_tr, base_en, _, _ = self.fold_map[stem]
                tr_tpl, en_tpl = self.franchise_sfx_folded[sfx]
                return (None, tr_tpl.format(tr=base_tr), en_tpl.format(en=base_en), "new", "morfoloji:seri+sonek")

        # F) Saf İngilizce anlamlı tür / kavram tamlamaları
        if not has_cjk(s) and re.fullmatch(r"[A-Za-z0-9 &+'/\-.:]{3,48}", s):
            words = [w for w in re.split(r"[\s/\-_&:+.]+", s.strip()) if w]
            phrase_key = " ".join(w.lower() for w in words)
            if phrase_key in ENGLISH_PHRASES_TR:
                tr, en = ENGLISH_PHRASES_TR[phrase_key]
                return (None, tr, en, "new", "ingilizce-kavram")
            if 1 <= len(words) <= 3 and all(w.lower() in ENGLISH_WORDS_TR for w in words):
                tr = " ".join(ENGLISH_WORDS_TR[w.lower()] for w in words)
                en = " ".join(w.upper() if w.upper() in ("RPG", "OST", "TV") else w.capitalize() for w in words)
                if tr.casefold() != en.casefold():
                    return (None, tr, en, "new", "ingilizce-birebir")

        return None

    def resolve_record(self, rec: dict) -> tuple[str, str, str, str, str]:
        """(cid_or_SKIP, tr, en, origin, note) döndürür."""
        vk = rec["key"]
        raw_labels = [l for l in (rec.get("labels") or []) if l]
        # 0) count=0 (007_j) tohum kaydı (source != 'github_dump') zaten xml_tr içindeyse doğrudan koru/yükselt!
        if rec.get("source") != "github_dump" and vk in self.xml_tr:
            seed_cid = vk.removeprefix("bangumi_tag_")
            tr = self.xml_tr[vk]
            en = self.xml_en.get(vk, rec.get("en") or tr)
            en_clean = en.replace("\\'", "'").replace('\\"', '"').replace("&amp;", "&")
            self.tag_canons[seed_cid] = (tr, en_clean, "existing")
            return (seed_cid, tr, en_clean, "existing", "seed tag")

        name = raw_labels[0] if raw_labels else (rec.get("tr") or rec.get("short") or vk)
        labels = [name, *[l for l in raw_labels[1:] if l != name]]

        # 1) MASTER test kilidi ("ASOBI")
        if name.strip() in LOCKED_SKIP_LABELS:
            return ("SKIP", "", "", "", "SKIP: MASTER test kilidi")

        # 2) Katman 2 — Depo + Sözlük Tam Eşleşmesi (ham + sargısız + OpenCC fold + norm)
        #    Önce tam eşleşmeye bakıyoruz ki "6分" SKIP iken "86" veya "C3" gibi gerçek yapımlar
        #    tags.tsv'de varsa kaçmasın; ama saf tarih/raf SKIP kontrolünü hemen ardından çalıştırıyoruz!
        for cand_raw in labels:
            for variant in strip_wrappers(cand_raw):
                if variant in LOCKED_SKIP_LABELS:
                    continue
                fk = cjk_fold(variant)
                if fk in self.fold_map:
                    pref_cid, tr, en, origin, src_note = self.fold_map[fk]
                    cid, final_origin = self.allocate_cid(pref_cid, tr, en, origin, name)
                    return (cid, tr, en, final_origin, src_note)
                nk = norm_key(variant)
                if len(nk) >= 3 and nk in self.norm_map:
                    pref_cid, tr, en, origin, src_note = self.norm_map[nk]
                    cid, final_origin = self.allocate_cid(pref_cid, tr, en, origin, name)
                    return (cid, tr, en, final_origin, src_note)

        # 3) Katman 1 — Mekanik SKIP Süzgeci
        skip_reason = check_skip(name)
        if skip_reason:
            return ("SKIP", "", "", "", skip_reason)

        # 4) Katman 3 — Morfolojik Kavram & Seri Birleştirici
        for cand_raw in labels:
            for variant in strip_wrappers(cand_raw):
                morph = self._try_morphology(variant)
                if morph:
                    pref_cid, tr, en, origin, m_note = morph
                    cid, final_origin = self.allocate_cid(pref_cid, tr, en, origin, name)
                    return (cid, tr, en, final_origin, m_note)

        # 5) Katman 4A — CJK içermeyen temiz Latin/ASCII özel adlar (seri, grup, stüdyo, sanatçı, platform)
        cleaned = strip_wrappers(name)[-1]
        if not has_cjk(cleaned) and any(ch.isalpha() for ch in cleaned):
            # Çok kısa 2 harfli belirsiz gürültü değilse global Latin özel ad olarak kaydet
            if len(cleaned) >= 2:
                cid, final_origin = self.allocate_cid(None, cleaned, cleaned, "global", name)
                return (cid, cleaned, cleaned, final_origin, "latin global ad")

        # 6) Katman 4B — Doğrulanamayan CJK özel adlar: dürüst `as_is` (SIFIR uydurma romanizasyon!)
        cjk_cid = "cjk_" + hashlib.md5(name.encode("utf-8")).hexdigest()[:6]
        cid, _ = self.allocate_cid(cjk_cid, name, name, "as_is", name)
        return (cid, name, name, "as_is", "as_is: doğrulanmamış CJK özel ad")

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bangumi resmî meta etiket sözlüğü kapsama denetimi.

Bangumi `meta_tags` alanı (anime / book / game / real / music kategorileri) kapalı bir
sözlüktür; bu betik o listenin tamamının `BangumiTagDictionary` tarafından karşılandığını
doğrular. Kullanıcı etiketleri (serbest metin) bu denetimin kapsamı dışındadır.

Kullanım:  python3 scripts/check_bangumi_meta_tag_coverage.py   (eksik varsa çıkış kodu 1)
"""
import io,re,sys
src=io.open('app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt',encoding='utf-8').read()
body=src.split('private val groups: List<Group> = listOf(',1)[1].split('\n    /** Normalleştirilmiş',1)[0]
rows=[re.findall(r'"((?:[^"\\]|\\.)*)"',e) for e in re.findall(r'g\((\s*"(?:[^"\\]|\\.)*"\s*(?:,\s*"(?:[^"\\]|\\.)*"\s*)*)\)', body)]
def norm(v):
    out=[]
    for ch in v:
        if ch=='\u3000': out.append(' ')
        elif ch in '（([【《「『）)]】》」』': pass
        elif ch in '：:・·。、，,': out.append(' ')
        else: out.append(ch)
    return re.sub(r'\s+',' ',''.join(out).strip()).lower()
def comp(v): return ''.join(c for c in v if c.isalnum()).lower()
L={};C={}
for r in rows:
    for lab in r[3:]:
        L.setdefault(norm(lab),r)
        if len(comp(lab))>=3: C.setdefault(comp(lab),r)
for r in rows:
    for nm in (r[2],r[1]):
        L.setdefault(norm(nm),r)
        if len(comp(nm))>=3: C.setdefault(comp(nm),r)
def ent(t):
    r=L.get(norm(t))
    if r: return r
    c=comp(t)
    return C.get(c) if len(c)>=3 else None

# Bangumi resmî "meta_tags" sözlüğü (anime / book / game / real / music kategorileri)
meta = {
 "kaynak":["原创","漫画改","游戏改","小说改","轻小说改","改编","系列续作"],
 "tip":["TV","WEB","OVA","剧场版","动态漫画","短片","特别篇","真人","其他"],
 "bolge":["日本","中国大陆","中国台湾","中国香港","美国","英国","韩国","法国","德国","俄罗斯","苏联","其他"],
 "hedef":["男性向","女性向","子供向","青年向","少年向","少女向","乙女","BL","GL","全年龄","R18","限制级"],
 "tema_anime":["科幻","喜剧","百合","校园","惊悚","后宫","机战","悬疑","恋爱","奇幻","推理","运动",
   "耽美","音乐","战斗","冒险","萌系","治愈","历史","日常","剧情","童话","武侠","玄幻","魔幻","美食",
   "职场","伪娘","搞笑","热血","励志","复仇","悬念","黑暗","魔法","妖怪","偶像","竞技","料理"],
 "real":["动作","爱情","动画","犯罪","同性","歌舞","传记","战争","西部","灾难","情色","家庭","儿童","纪录片","短片"],
 "oyun_tur":["ADV","AVG","RPG","SLG","ACT","STG","PZL","SPG","MUG","TAB","FTG","RCG","MMORPG","RTS","卡牌","角色扮演","策略","模拟","射击","益智","音乐游戏","冒险游戏"],
 "oyun_platform":["PC","PS4","PS5","PS3","PSV","PSP","Nintendo Switch","NS","3DS","Xbox","Xbox One","iOS","Android","街机","主机","掌机","网游","手游"],
 "kitap":["小说","漫画","画集","写真集","图书","杂志","绘本","设定集","轻小说","文库"],
 "muzik":["Pop","Rock","Jazz","Classical","Electronic","Soundtrack","Vocaloid","Anisong","Hip-hop","R&B","Metal","Folk","Ambient","Instrumental"],
}
missing=[]
total=0
for cat,items in meta.items():
    for t in items:
        total+=1
        if ent(t) is None: missing.append((cat,t))
print("kontrol edilen resmî meta etiket:",total)
print("eksik:",len(missing))
for c,t in missing: print("  ",c,t)
sys.exit(1 if missing else 0)

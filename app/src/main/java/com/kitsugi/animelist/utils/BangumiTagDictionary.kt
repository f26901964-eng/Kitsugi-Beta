package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Bangumi (bgm.tv) **tür ve etiket sözlüğü**.
 *
 * Bangumi条目'larında `类型` (tür) ve `标签` (etiket) alanları kullanıcı kaynaklıdır ve
 * neredeyse her zaman Çince (basit + geleneksel karışık) ya da Japonca gelir:
 * `游戏改`, `治愈系`, `催涙`, `2008年10月`, `GAL改`, `续作` ... Bu sözlük o değerleri
 * uygulamanın arayüz diline (Türkçe / İngilizce) çevirir.
 *
 * Kurallar:
 *  • **Uydurma çeviri yok.** Bilinmeyen etiket olduğu gibi döner. Kişi, karakter, stüdyo ve
 *    eser adları çevrilmez; yalnızca **Latin yazımına sabitlenir** (`石原立也` → Tatsuya Ishihara,
 *    `京阿尼` → Kyoto Animation, `攻壳机动队` → Ghost in the Shell). Böylece aynı kişinin
 *    basit/geleneksel Çince ve Japonca yazımları tek çipte toplanır (`菅野洋子` + `菅野よう子`).
 *  • Bir girişin `key` alanı `bangumi_tag_<key>` adlı dil dosyası kaynağıyla eşleşir; kaynak
 *    varsa (yalnızca tr/en arayüzünde) o tercih edilir, yoksa bu tablo kullanılır.
 *  • Eşleştirme normalize edilerek yapılır: boşluk, büyük/küçük harf, tam genişlikli noktalama
 *    ve `「」《》（）` sarımları yok sayılır. ` 恋爱 ` ve `「恋爱」` aynı sonuca verir.
 *  • Yıl/ay etiketleri (`2008年`, `2008年10月`, `2000年代`) tabloya yazılamayacağı için
 *    kalıp eşleşmesiyle çevrilir.
 *  • Aynı anlamın basit/geleneksel Çince ve Japonca yazımları AYNI girişte toplanır; böylece
 *    `催泪` ve `催涙` ekranda tek etiket olarak görünür (bkz. [localizedLabelOf]).
 */
internal data class BangumiTagEntry(
    /** `bangumi_tag_*` dil dosyası anahtarı (snake_case). */
    val key: String,
    val turkish: String,
    val english: String
)

internal object BangumiTagDictionary {

    private class Group(val entry: BangumiTagEntry, val labels: List<String>)

    private fun g(key: String, turkish: String, english: String, vararg labels: String) =
        Group(BangumiTagEntry(key, turkish, english), labels.toList())

    // ── Tablo ─────────────────────────────────────────────────────────────────

    private val groups: List<Group> = listOf(
        // ── Biçim / platform ──
        g("tv", "TV", "TV", "TV", "テレビ", "TV动画", "電視動畫", "番剧", "番劇", "TV SERIES"),
        g("web", "Web", "Web", "Web", "WEB", "网络动画", "網絡動畫", "网番", "網番", "ONA", "ona", "网络番剧"),
        g("ova", "OVA", "OVA", "OVA", "ova", "Original Video Animation"),
        g("oad", "OAD", "OAD", "OAD", "oad", "OAD动画"),
        g("movie", "Film", "Movie", "剧场版", "劇場版", "剧场", "劇場", "剧场动画", "劇場アニメ", "劇場版アニメ", "电影", "電影", "映画", "Movie", "Ani Movie"),
        g("short_film", "Kısa Film", "Short Film", "短片", "短篇动画", "短篇動畫", "Short Film"),
        g("special", "Özel Bölüm", "Special", "SP", "特别篇", "特別篇", "Specials", "SP篇"),
        g("pv", "Tanıtım Videosu", "Promotion Video", "PV", "预告", "預告", "宣传片", "宣傳片"),
        g("cm", "Reklam Klibi", "Commercial Film", "CM", "广告", "廣告"),
        g("music_video", "Müzik Klibi", "Music Video", "MV", "音乐视频", "音樂影片", "MUSIC VIDEO"),
        g("live_action", "Canlı Çekim", "Live Action", "真人版", "真人", "実写", "真人影视", "Live Action"),
        g("drama_series", "Dizi", "Drama Series", "电视剧", "電視劇", "连续剧", "連續劇", "劇集", "剧集", "日剧", "日劇"),
        g("korean_drama", "Kore Dizisi", "Korean Drama", "韩剧", "韓劇", "韩国电视剧", "Korean Drama", "K-Drama", "한국 드라마"),
        g("japanese_drama", "Japon Dizisi", "Japanese Drama", "日本电视剧", "日本劇", "Japanese Drama", "日劇作品"),
        g("chinese_drama", "Çin Dizisi", "Chinese Drama", "国产剧", "國產劇", "大陆剧", "大陸劇", "中国电视剧", "Chinese Drama"),
        g("taiwanese_drama", "Tayvan Dizisi", "Taiwanese Drama", "台剧", "臺劇", "台湾偶像剧", "台灣偶像劇"),
        g("hongkong_drama", "Hong Kong Dizisi", "Hong Kong Drama", "港剧", "港劇", "TVB剧"),
        g("western_series", "Batı Dizisi", "Western Series", "美剧", "美劇", "欧美剧", "歐美劇", "英剧", "英劇"),
        g("thai_drama", "Tay Dizisi", "Thai Drama", "泰剧", "泰劇"),
        g("variety_show", "Eğlence Programı", "Variety Show", "综艺", "綜藝", "综艺节目", "バラエティ"),
        g("reality_show", "Yarışma/Reality", "Reality Show", "真人秀", "実写バラエティ", "Reality"),
        g("documentary", "Belgesel", "Documentary", "纪录片", "紀錄片", "ドキュメンタリー", "Documentary"),
        g("stage_play", "Sahne Oyunu", "Stage Play", "舞台剧", "舞臺劇", "舞台劇", "2.5次元"),
        g("radio_drama", "Radyo Draması", "Radio Drama", "广播剧", "廣播劇", "ドラマCD", "Drama CD"),

        // ── Kaynak malzeme ──
        g("original", "Orijinal", "Original", "原创", "原創", "オリジナル", "Original Work"),
        g("manga_adaptation", "Manga Uyarlaması", "Manga Adaptation", "漫画改", "漫畫改", "漫改", "漫画改编", "漫畫改編", "漫画原作", "マンガ原作", "コミック原作"),
        g("game_adaptation", "Oyun Uyarlaması", "Game Adaptation", "游戏改", "遊戲改", "游戏改编", "遊戲改編", "ゲーム原作"),
        g("novel_adaptation", "Roman Uyarlaması", "Novel Adaptation", "小说改", "小說改", "小说改编", "小說改編", "小説原作"),
        g("light_novel", "Hafif Roman", "Light Novel", "轻小说", "輕小說", "ライトノベル", "Light Novel"),
        g("web_novel", "Web Romanı", "Web Novel", "网文", "網文", "网络小说", "網路小說", "WEB小説"),
        g("visual_novel", "Görsel Roman", "Visual Novel", "视觉小说", "視覺小說", "視覚小说", "ビジュアルノベル"),
        g("galgame_adaptation", "Galgame Uyarlaması", "Galgame Adaptation", "GAL改", "gal改", "美少女游戏改", "エロゲ改"),
        g("galgame", "Galgame", "Galge", "galgame", "GALGAME", "美少女游戏", "美少女遊戲", "エロゲ"),
        g("4koma", "4-Koma Manga", "4-Koma Manga", "四格漫画", "四コマ", "4-koma"),
        g("doujin", "Doujin Kaynak", "Doujin", "同人", "同人作品", "同人誌", "Doujin"),
        g("fan_work", "Hayran Yapımı", "Fan Work", "二创", "二次创作", "二次創作"),
        g("adaptation", "Uyarlama", "Adaptation", "改编", "改編", "改编作品"),
        g("remake", "Yeniden Çevrim", "Remake", "重制", "重製", "リメイク"),
        g("reboot", "Yeniden Başlatma", "Reboot", "重启版", "重啟版"),
        g("picture_book", "Resimli Kitap", "Picture Book", "绘本", "繪本", "絵本"),
        g("comic_strip", "Çizgi Bant", "Comic Strip", "连环画", "連環畫"),
        g("media_mix", "Çoklu Medya Projesi", "Media Mix", "多媒体企划", "多媒體企劃", "メディアミックス"),
        g("unfinished", "Yarım Kaldı", "Unfinished", "未完结", "未完結", "腰斩", "腰斬", "坑"),
        g("hiatus", "Ara Verildi", "On Hiatus", "休载", "休載", "休止"),
        g("completed", "Tamamlandı", "Completed", "完结", "完結", "已完结", "已完結"),
        g("ongoing", "Devam Ediyor", "Ongoing", "连载中", "連載中", "放送中", "更新中"),
        g("cancelled", "İptal Edildi", "Cancelled", "未放送", "取消", "废止", "廢止"),

        // ── Ana türler ──
        g("action", "Aksiyon", "Action", "战斗", "戰鬥", "动作", "動作", "热血", "熱血", "アクション", "打戏"),
        g("adventure", "Macera", "Adventure", "冒险", "冒險", "冒険"),
        g("comedy", "Komedi", "Comedy", "搞笑", "喜剧", "喜劇", "コメディー", "ギャグ", "笑"),
        g("drama", "Dram", "Drama", "剧情", "劇情", "正剧", "正劇", "文戏"),
        g("romance", "Romantik", "Romance", "恋爱", "戀愛", "爱情", "愛情", "恋愛", "言情", "纯爱", "純愛"),
        g("romantic_comedy", "Romantik Komedi", "Romantic Comedy", "爱情喜剧", "愛情喜劇", "ラブコメ", "ラブコメディ", "RomCom"),
        g("sci_fi", "Bilim Kurgu", "Sci-Fi", "科幻", "科学幻想", "SF", "サイエンスフィクション"),
        g("fantasy", "Fantastik", "Fantasy", "奇幻", "玄幻", "魔幻", "幻想", "ファンタジー"),
        g("horror", "Korku", "Horror", "恐怖", "惊悚", "驚悚", "ホラー"),
        g("mystery", "Gizem", "Mystery", "悬疑", "懸疑", "謎", "谜", "ミステリー"),
        g("detective", "Dedektif", "Detective", "推理", "侦探", "偵探", "探偵"),
        g("thriller", "Gerilim", "Thriller", "悬疑惊悚", "懸疑驚悚", "悬念", "懸念", "サスペンス", "惊悚片"),
        g("crime", "Suç", "Crime", "犯罪", "罪案", "クライム"),
        g("sports", "Spor", "Sports", "运动", "運動", "スポーツ", "体育"),
        g("supernatural", "Doğaüstü", "Supernatural", "灵异", "超自然", "オカルト", "灵异事件"),
        g("magic", "Büyü", "Magic", "魔法", "魔術", "魔术", "マジック"),
        g("mecha", "Mecha", "Mecha", "机战", "機戰", "机甲", "機甲", "ロボット", "メカ", "萝卜"),
        g("military", "Askeri", "Military", "军事", "軍事", "军队", "軍隊", "ミリタリー", "战争", "戰爭", "战记", "戰記"),
        g("historical", "Tarihi", "Historical", "历史", "歷史", "时代剧", "時代劇", "古装", "古裝", "古风", "古風", "古代"),
        g("space", "Uzay", "Space", "宇宙", "太空", "スペース"),
        g("school", "Okul", "School", "校园", "校園", "学园", "學園", "学校", "學校", "学園", "スクール"),
        g("slice_of_life", "Günlük Yaşam", "Slice of Life", "日常", "日常系", "生活", "スローライフ"),
        g("music", "Müzik", "Music", "音乐", "音樂", "音楽", "歌曲"),
        g("idol", "Idol", "Idol", "偶像", "アイドル"),
        g("ecchi", "Ecchi", "Ecchi", "杀必死", "サービス", "色气", "色氣"),
        g("hentai", "Hentai", "Hentai", "变态", "變態", "色情", "18禁", "十八禁", "R18"),
        g("erotic", "Erotik", "Erotica", "官能", "成人向", "情色", "性描写"),
        g("gore", "Kan ve Şiddet", "Gore", "血腥", "暴力", "グロ"),
        g("nudity", "Müstehcenlik", "Nudity", "裸露", "裸"),
        g("seinen", "Seinen", "Seinen", "青年", "青年向", "青年漫画"),
        g("shounen", "Shōnen", "Shounen", "少年", "少年向", "少年Jump", "少年ジャンプ"),
        g("shoujo", "Shōjo", "Shoujo", "少女", "少女向", "少女漫画"),
        g("josei", "Josei", "Josei", "女性", "女性向", "女性漫画"),
        g("kids", "Çocuklara Yönelik", "Kids", "儿童", "兒童", "子供向", "亲子", "親子"),
        g("educational", "Eğitici", "Educational", "教育", "科普", "幼儿启蒙"),
        g("avant_garde", "Avangart", "Avant Garde", "avant garde", "实验性", "實驗"),
        g("parody", "Parodi", "Parody", "恶搞", "惡搞", "パロディ", "戏仿"),
        g("award_winning", "Ödüllü", "Award Winning", "获奖", "獲獎", "获奖作品", "获奖短片"),
        g("classic", "Klasik", "Classic", "经典", "經典"),
        g("underrated", "Küçümsenmiş", "Underrated", "冷门", "冷門", "小众", "小众佳作"),
        g("cult", "Kült", "Cult", "邪典", "カルト"),
        g("controversial", "Tartışmalı", "Controversial", "争议", "爭議", "炎上"),

        // ── Alt türler / temalar ──
        g("isekai", "Isekai", "Isekai", "异世界", "異世界", "イセカイ"),
        g("reincarnation", "Reenkarnasyon", "Reincarnation", "转生", "轉生", "重生", "輪廻", "転生"),
        g("time_travel", "Zaman Yolculuğu", "Time Travel", "穿越", "时间旅行", "時間旅行", "タイムリープ", "时空", "時空"),
        g("time_loop", "Zaman Döngüsü", "Time Loop", "轮回", "輪廻転生", "時間循環", "タイムループ"),
        g("parallel_world", "Paralel Dünya", "Parallel World", "平行世界", "平行時空"),
        g("virtual_reality", "Sanal Gerçeklik", "Virtual Reality", "VR", "虚拟现实", "虛擬現實", "网络世界"),
        g("game", "Oyun", "Game", "游戏", "遊戲", "ゲーム", "电竞", "電競", "电子竞技", "e-sports"),
        g("board_game", "Masa Oyunu", "Board Game", "桌游", "桌遊", "卡牌", "卡片战斗", "TAB"),
        g("gambling", "Kumar", "Gambling", "赌博", "賭博", "ギャンブル"),
        g("high_stakes_game", "Yüksek Riskli Oyun", "High Stakes Game", "狂赌", "賭ケグルイ", "赌博默示录"),
        g("puzzle", "Bulmaca", "Puzzle", "益智", "解谜", "謎解き", "PZL"),
        g("mahjong", "Mahjong", "Mahjong", "麻将", "麻雀", "MJ"),
        g("chess", "Satranç", "Chess", "国际象棋", "棋盤"),
        g("card_game", "Kart Oyunu", "Card Game", "卡牌游戏", "卡片戰鬥先導者"),
        g("survival", "Hayatta Kalma", "Survival", "生存", "求生", "サバイバル"),
        g("death_game", "Ölüm Oyunu", "Death Game", "死亡游戏", "死亡遊戲", "デスゲーム", "大逃杀", "大逃殺"),
        g("post_apocalyptic", "Kıyamet Sonrası", "Post-Apocalyptic", "末世", "末日", "后启示录", "終末", "废土", "廢土"),
        g("apocalypse", "Kıyamet", "Apocalypse", "天启", "启示录", "終末論"),
        g("dystopia", "Distopya", "Dystopia", "反乌托邦", "ディストピア"),
        g("cyberpunk", "Cyberpunk", "Cyberpunk", "赛博朋克", "サイバーパンク"),
        g("steampunk", "Steampunk", "Steampunk", "蒸汽朋克", "スチームパンク"),
        g("space_opera", "Uzay Operası", "Space Opera", "太空歌剧", "スペースオペラ"),
        g("space_battle", "Uzay Savaşı", "Space Battle", "宇宙战", "宇宙戦争"),
        g("first_love", "İlk Aşk", "First Love", "初恋", "初戀", "ファースト・ラブ"),
        g("love_polygon", "Aşk Çokgeni", "Love Polygon", "三角关系", "三角關係", "党争", "多角关系"),
        g("love_triangle", "Aşk Üçgeni", "Love Triangle", "三角恋", "三角戀愛"),
        g("unrequited_love", "Karşılıksız Aşk", "Unrequited Love", "单恋", "單戀", "片想い"),
        g("childhood_friends", "Çocukluk Arkadaşı", "Childhood Friend", "青梅竹马", "青梅竹馬", "幼馴染"),
        g("confession", "İtiraf", "Confession", "告白", "表白"),
        g("date", "Buluşma", "Date", "约会", "約會", "데이트", "デート"),
        g("harem", "Harem", "Harem", "后宫", "後宮", "ハーレム"),
        g("reverse_harem", "Ters Harem", "Reverse Harem", "逆后宫", "逆後宮", "逆ハーレム"),
        g("yuri", "Yuri", "Yuri", "百合", "ユリ"),
        g("bl", "Boys Love", "Boys Love", "耽美", "男男", "BL", "ボーイズラブ"),
        g("gl", "Girls Love", "Girls Love", "蕾丝", "蕾丝边", "GL", "レズ"),
        g("forbidden_love", "Yasak Aşk", "Forbidden Love", "禁忌之恋", "禁忌", "义理の兄妹", "兄妹恋"),
        g("ntr", "NTR", "NTR", "NTR", "牛头人", "寝取られ"),
        g("crossdressing", "Zıt Cins Kılığı", "Crossdressing", "女装", "男裝", "伪娘", "男の娘"),
        g("gender_bender", "Cinsiyet Değişimi", "Gender Bender", "性转", "性轉", "性別変換"),
        g("magical_girl", "Sihirli Kız", "Mahou Shoujo", "魔法少女", "セーラー戦士"),
        g("super_power", "Süper Güç", "Super Power", "超能力", "能力者", "异能", "異能", "特殊能力"),
        g("psychological", "Psikolojik", "Psychological", "心理", "心理战", "サイコスリラー"),
        g("mental_illness", "Akıl Sağlığı", "Mental Illness", "精神病", "精神疾病", "抑郁", "抑鬱", "鬱"),
        g("medical", "Tıbbi", "Medical", "医疗", "醫療", "医生", "醫生", "病院", "医院", "法医", "护士", "護理"),
        g("legal", "Hukuk", "Legal", "律政", "法律", "法庭", "裁判", "弁護士"),
        g("prison", "Hapishane", "Prison", "监狱", "監獄", "拘置所"),
        g("escape", "Kaçış", "Escape", "越狱", "脫獄", "逃脱", "逃獄"),
        g("police", "Polis", "Police", "警察", "刑事", "巡警", "刑侦", "刑偵"),
        g("mafia", "Mafya", "Mafia", "黑帮", "黑幫", "黑道", "极道", "マフィア", "ヤクザ"),
        g("spy", "Casusluk", "Spy", "间谍", "間諜", "谍战", "諜報", "スパイ", "特工"),
        g("assassin", "Suikastçı", "Assassin", "刺客", "杀手", "殺手", "暗杀", "暗殺"),
        g("ninjas", "Ninja", "Ninja", "忍者", "ニンジャ"),
        g("samurai", "Samuray", "Samurai", "武士", "侍", "SAMURAI"),
        g("martial_arts", "Dövüş Sanatları", "Martial Arts", "武侠", "武俠", "功夫", "武术", "武術", "格闘", "格斗"),
        g("swordplay", "Kılıç Düellosu", "Swordplay", "剑戟", "剣劇", "决斗", "決闘", "一騎打ち"),
        g("demons", "İblisler", "Demons", "恶魔", "惡魔", "悪魔", "魔物"),
        g("angels", "Melekler", "Angels", "天使", "エンジェル"),
        g("vampire", "Vampir", "Vampire", "吸血鬼", "ヴァンパイア"),
        g("zombie", "Zombi", "Zombie", "丧尸", "喪屍", "僵尸", "ゾンビ"),
        g("werewolf", "Kurt Adam", "Werewolf", "狼人", "ウェアウルフ"),
        g("monster", "Canavar", "Monster", "怪物", "怪兽", "怪獣"),
        g("kaiju", "Kaiju", "Kaiju", "怪獣大戦", "巨神兵"),
        g("mythology", "Mitoloji", "Mythology", "神话", "神話", "克苏鲁", "克蘇魯", "クトゥルフ"),
        g("fairy_tale", "Peri Masalı", "Fairy Tale", "童话", "童話", "お伽話"),
        g("folklore", "Halk Hikâyeleri", "Folklore", "民间故事", "民間故事", "传说", "傳說", "都市传说", "都市傳說"),
        g("religion", "Din", "Religion", "宗教", "佛教", "基督教", "神道", "神社"),
        g("philosophy", "Felsefe", "Philosophy", "哲学", "哲學", "伦理", "倫理", "思想"),
        g("exorcism", "Şeytan Kovma", "Exorcism", "驱魔", "驅魔", "除魔", "祓魔", "エクソシスト"),
        g("occult", "Okültizm", "Occult", "灵异调查", "阴阳", "陰陽師", "符咒"),
        g("magic_school", "Büyü Okulu", "Magic School", "魔法学院", "魔法學校"),
        g("academy", "Akademi", "Academy", "学院", "學院", "士官学校", "アカデミー"),
        g("student_council", "Öğrenci Konseyi", "Student Council", "学生会", "學生會", "生徒会"),
        g("club_activity", "Kulüp Faaliyeti", "Club Activity", "社团", "社團", "部活", "部室"),
        g("graduation", "Mezuniyet", "Graduation", "毕业", "畢業", "卒業"),
        g("school_festival", "Okul Şenliği", "School Festival", "学园祭", "文化祭"),
        g("coming_of_age", "Olgunlaşma", "Coming Of Age", "成长", "成長", "思春期"),
        g("youth", "Gençlik", "Youth", "青春", "青年期"),
        g("workplace", "İş Hayatı", "Workplace", "职场", "職場", "工作", "上班族", "サラリーマン"),
        g("social_issues", "Toplumsal", "Social Issues", "社会", "社會", "社会问题", "社會問題", "现实", "現實"),
        g("family", "Aile", "Family", "家庭", "家族", "亲情", "親情", "家人"),
        g("marriage", "Evlilik", "Marriage", "结婚", "結婚", "婚姻", "婚礼", "婚禮"),
        g("parenting", "Ebeveynlik", "Parenting", "育儿", "育兒", "子育て"),
        g("childcare", "Çocuk Bakımı", "Childcare", "保姆", "保育", "托儿"),
        g("pets", "Evcil Hayvanlar", "Pets", "宠物", "寵物", "ペット"),
        g("animals", "Hayvanlar", "Animals", "动物", "動物", "野生動物"),
        g("anthropomorphic", "Antropomorfik", "Anthropomorphic", "拟人", "擬人", "兽人", "獸人", "ケモノ"),
        g("dinosaurs", "Dinozorlar", "Dinosaurs", "恐龙", "恐龍", "恐竜"),
        g("food", "Yemek", "Food", "美食", "料理", "烹饪", "烹飪", "グルメ", "吃饭"),
        g("gourmet", "Gurme", "Gourmet", "美食家", "吃貨", "大胃王"),
        g("agriculture", "Tarım", "Agriculture", "农业", "農業", "种田", "田舎生活", "牧场"),
        g("fishing", "Balıkçılık", "Fishing", "钓鱼", "釣魚", "釣り"),
        g("travel", "Gezi", "Travel", "旅行", "旅游", "旅遊", "公路片"),
        g("outdoor", "Açık Hava", "Outdoor", "户外", "戶外", "露营", "露營", "登山", "滑雪"),
        g("tournament", "Turnuva", "Tournament", "竞技", "競技", "比赛", "比賽", "锦标赛", "選手権"),
        g("soccer", "Futbol", "Soccer", "足球", "サッカー"),
        g("basketball", "Basketbol", "Basketball", "篮球", "籃球", "バスケットボール"),
        g("volleyball", "Voleybol", "Volleyball", "排球", "バレーボール"),
        g("tennis", "Tenis", "Tennis", "网球", "網球", "テニス"),
        g("baseball", "Beyzbol", "Baseball", "棒球", "ベースボール"),
        g("swimming", "Yüzme", "Swimming", "游泳", "水球"),
        g("track_and_field", "Atletizm", "Track And Field", "田径", "田徑", "陸上"),
        g("cycling", "Bisiklet", "Cycling", "自行车", "自行車", "自転車"),
        g("skating", "Buz Pateni", "Skating", "滑冰", "花样滑冰", "スケート"),
        g("racing", "Yarış", "Racing", "赛车", "賽車", "竞速", "レーシング"),
        g("motorcycle", "Motosiklet", "Motorcycle", "摩托", "摩托車", "バイク"),
        g("combat_sports", "Dövüş Sporları", "Combat Sports", "拳击", "拳擊", "柔道", "空手道", "跆拳道", "综合格斗"),
        g("dance", "Dans", "Dance", "舞蹈", "ダンス"),
        g("performing_arts", "Sahne Sanatları", "Performing Arts", "演剧", "演劇", "宝塚", "歌舞伎"),
        g("art", "Sanat", "Art", "美术", "美術", "艺术", "藝術", "芸術", "绘画", "繪畫", "アート"),
        g("illustration", "İllüstrasyon", "Illustration", "插画", "插畫", "原画"),
        g("photography", "Fotoğrafçılık", "Photography", "摄影", "攝影", "カメラ"),
        g("writing", "Yazarlık", "Writing", "写作", "創作", "小说家", "小説家"),
        g("voice_actor", "Seslendirme", "Voice Acting", "声优", "聲優", "配音", "ボイスアクター"),
        g("showbiz", "Şov Dünyası", "Showbiz", "演艺圈", "演藝圈", "娱乐圈", "娛樂圈"),
        g("comedy_drama", "Dramedi", "Comedy Drama", "喜剧剧情", "喜劇剧情"),
        g("action_comedy", "Aksiyon Komedi", "Action Comedy", "动作喜剧", "戰鬥喜劇"),
        g("sci_fi_horror", "Bilim Kurgu Korku", "Sci-Fi Horror", "科幻恐怖", "SFホラー"),
        g("psychological_horror", "Psikolojik Korku", "Psychological Horror", "心理恐怖", "サイコホラー"),
        g("horror_comedy", "Korku Komedi", "Horror Comedy", "恐怖喜剧", "恐怖喜劇"),
        g("military_sci_fi", "Askeri Bilim Kurgu", "Military Sci-Fi", "军事科幻", "軍事科幻"),
        g("superhero", "Süper Kahraman", "Superhero", "超级英雄", "ヒーロー", "戦隊", "战队"),
        g("tokusatsu", "Tokusatsu", "Tokusatsu", "特摄", "特攝", "假面骑士", "卡美拉"),
        g("villain", "Kötü Adam", "Villain", "反派", "悪役", "ヴィラン"),
        g("anti_hero", "Anti-Kahraman", "Anti-Hero", "反英雄", "ダークヒーロー"),
        g("ensemble_cast", "Ansambıl Kadro", "Ensemble Cast", "群像剧", "群像劇", "群像"),
        g("slow_burn", "Yavaş İşleyen", "Slow Burn", "慢热", "慢熱"),
        g("power_fantasy", "Güç Fantezisi", "Power Fantasy", "爽", "爽番", "爽文", "无双"),
        g("episodic", "Vaka Anlatısı", "Episodic", "单元剧", "單元劇", "オムニバス"),
        g("short_series", "Kısa Dizi", "Short Series", "迷你剧", "迷你劇", "泡面番", "短剧", "短劇"),
        g("long_series", "Uzun Soluklu Seri", "Long Running", "长篇", "長編", "长篇连载"),
        g("sequel", "Devam", "Sequel", "续作", "續作", "系列续作", "系列續作", "续集", "續編", "第二季", "后续"),
        g("prequel", "Öncül", "Prequel", "前传", "前傳"),
        g("spin_off", "Spin-Off", "Spin-off", "外传", "外傳", "衍生", "スピンオフ"),
        g("side_story", "Yan Hikâye", "Side Story", "番外", "番外篇", "小剧场", "小劇場"),
        g("crossover", "Crossover", "Crossover", "联动", "聯動", "コラボ"),
        g("compilation", "Derleme", "Compilation", "总集篇", "總集篇", "精选"),
        g("recap", "Özet", "Recap", "回顾", "回顧"),
        g("behind_the_scenes", "Kamera Arkası", "Behind The Scenes", "幕后", "幕後", "花絮"),
        g("tragedy", "Trajedi", "Tragedy", "悲剧", "悲劇"),
        g("revenge", "İntikam", "Revenge", "复仇", "復仇", "復讐", "报仇"),
        g("redemption", "Kefaret", "Redemption", "救赎", "贖罪"),
        g("friendship", "Dostluk", "Friendship", "友情", "伙伴", "夥伴", "羁绊", "羈絆"),
        g("rivals", "Rakiplik", "Rivalry", "宿敌", "宿敵", "对手"),
        g("betrayal", "İhanet", "Betrayal", "背叛", "背信"),
        g("death", "Ölüm", "Death", "死亡", "便当", "便當"),
        g("suicide", "İntihar", "Suicide", "自杀", "自殺"),
        g("bullying", "Zorbalık", "Bullying", "霸凌", "校园暴力", "校園暴力", "いじめ"),
        g("war_crimes", "Savaş Suçu", "War Crimes", "战争罪", "戰爭罪"),
        g("genocide", "Soykırım", "Genocide", "种族灭绝", "種族滅絕"),
        g("slavery", "Kölelik", "Slavery", "奴隶", "奴隸"),
        g("poverty", "Yoksulluk", "Poverty", "贫穷", "貧困", "贫民"),
        g("orphan", "Yetim", "Orphan", "孤儿", "孤兒", "孤児院"),
        g("adoption", "Evlat Edinme", "Adoption", "收养", "收養", "养子"),
        g("twins", "İkizler", "Twins", "双胞胎", "雙胞胎", "双子"),
        g("siblings", "Kardeşler", "Siblings", "兄妹", "姐弟", "兄弟", "姐妹"),
        g("abuse", "İstismar", "Abuse", "虐待", "家庭暴力"),
        g("pregnancy", "Hamilelik", "Pregnancy", "怀孕", "懷孕", "出産"),
        g("baby", "Bebek", "Baby", "婴儿", "嬰兒", "宝宝"),
        g("grandparents", "Büyükanne/Büyükbaba", "Grandparents", "祖父母", "爷爷奶奶"),
        g("single_parent", "Tek Ebeveyn", "Single Parent", "单亲", "單親"),
        g("broken_family", "Parçalanmış Aile", "Broken Family", "原生家庭", "家暴"),
        g("memory", "Hafıza", "Memory", "记忆", "記憶", "失忆", "失憶", "回忆"),
        g("dream", "Rüya", "Dream", "梦", "梦境", "夢", "ドリーム"),
        g("summer", "Yaz", "Summer", "夏", "夏天", "夏日"),
        g("winter", "Kış", "Winter", "冬", "冬天", "冬季"),
        g("spring", "İlkbahar", "Spring", "春", "春天", "春季"),
        g("autumn", "Sonbahar", "Autumn", "秋", "秋天", "秋季"),
        g("rain", "Yağmur", "Rain", "雨", "雨天", "梅雨"),
        g("snow", "Kar", "Snow", "雪", "下雪"),
        g("sea", "Deniz", "Sea", "海", "海边", "海灘", "海洋"),
        g("sky", "Gökyüzü", "Sky", "天空", "飞行", "飛行"),
        g("train", "Tren", "Train", "列车", "列車", "电车", "電車", "铁道", "鉄道"),
        g("city", "Şehir", "City", "城市", "都市", "都会"),
        g("countryside", "Kır/Kasaba", "Countryside", "乡村", "鄉村", "农村", "田舎", "乡下"),
        g("ruins", "Harabe", "Ruins", "废墟", "廢墟", "廃墟"),
        g("shrine", "Tapınak", "Shrine", "鸟居", "神社境内"),
        g("library", "Kütüphane", "Library", "图书馆", "圖書室", "图书室", "文芸部"),
        g("island", "Ada", "Island", "孤岛", "小島", "无人岛", "無人島"),
        g("beach_episode", "Plaj Bölümü", "Beach Episode", "水着", "泳装", "泳裝"),
        g("hot_spring", "Kaplıca", "Hot Spring", "温泉"),
        g("festival", "Festival", "Festival", "祭", "节日", "節日", "夏祭"),
        g("christmas", "Noel", "Christmas", "圣诞", "聖誕", "クリスマス"),
        g("new_year", "Yeni Yıl", "New Year", "新年", "正月", "跨年"),
        g("valentines", "Sevgililer Günü", "Valentine", "情人节", "情人節", "バレンタイン"),
        g("halloween", "Cadılar Bayramı", "Halloween", "万圣节", "萬聖節", "ハロウィン"),
        g("school_life", "Okul Hayatı", "School Life", "校园生活", "學校生活", "学園生活"),
        g("dormitory", "Yurt", "Dormitory", "宿舍", "寮生活", "女子宿舍"),
        g("teacher", "Öğretmen", "Teacher", "老师", "老師", "教師", "班主任"),
        g("transfer_student", "Transfer Öğrenci", "Transfer Student", "转校生", "轉學生", "転入生"),
        g("delinquents", "Asi Gençler", "Delinquents", "不良", "不良少年", "番长", "ヤンキー", "太妹", "不良少女"),
        g("part_time_job", "Yarı Zamanlı İş", "Part Time Job", "打工", "バイト", "兼职", "兼職"),
        g("office_worker", "Ofis Çalışanı", "Office Worker", "白领", "白領", "OL"),
        g("hikikomori", "Hikikomori", "Hikikomori", "家里蹲", "隠居", "引きこもり"),
        g("otaku_culture", "Otaku Kültürü", "Otaku Culture", "御宅", "オタク", "秋叶原"),
        g("cosplay", "Cosplay", "Cosplay", "角色扮演", "コスプレ"),
        g("martial_master", "Usta", "Martial Master", "师傅", "師父", "高手"),
        g("genius", "Dahi", "Genius", "天才", "高智商"),
        g("idiot_character", "Saf Karakter", "Idiot Character", "笨蛋", "傻瓜", "天然呆", "天然"),
        g("tsundere", "Tsundere", "Tsundere", "傲娇", "傲嬌", "ツンデレ"),
        g("kuudere", "Soğuk Karakter", "Kuudere", "三无", "冷面", "クーデレ"),
        g("yandere", "Yandere", "Yandere", "病娇", "病嬌", "ヤンデレ"),
        g("genki", "Hareketli Karakter", "Genki", "元气", "元気", "活発"),
        g("chunibyo", "Orta 2 Sendromu", "Chuunibyou", "中二病", "中二"),
        g("introvert", "İçe Dönük", "Introvert", "内向", "社恐", "コミュ障"),
        g("miko", "Tapınak Kızı", "Miko", "巫女"),
        g("pilot", "Pilot", "Pilot", "驾驶员", "駕駛員", "パイロット"),
        g("knight", "Şövalye", "Knight", "骑士", "騎士", "ナイト"),
        g("witch", "Cadı", "Witch", "魔女", "ウィッチ"),
        g("wizard", "Büyücü", "Wizard", "魔法师", "魔法使い"),
        g("alchemy", "Simya", "Alchemy", "炼金", "鍊金", "錬金術"),
        g("dungeon", "Zindan", "Dungeon", "迷宫", "迷宮", "地下城", "ダンジョン"),
        g("guild", "Lonca", "Guild", "公会", "公會", "冒险者", "冒險者"),
        g("royalty", "Hanedan", "Royalty", "王室", "宫廷", "宮廷", "贵族", "貴族"),
        g("princess", "Prenses", "Princess", "王女", "公主", "プリンセス"),
        g("elf", "Elf", "Elf", "精灵", "精靈", "エルフ"),
        g("dwarf", "Cüce", "Dwarf", "矮人", "ドワーフ"),
        g("slime", "Slime", "Slime", "史莱姆", "スライム"),
        g("goblin", "Goblin", "Goblin", "哥布林", "哥布爾"),
        g("undead", "Ölümsüz", "Undead", "不死族", "アンデッド"),
        g("necromancy", "Nekromansi", "Necromancy", "死灵法术", "亡灵法师"),
        g("cultivation", "Kültivasyon", "Cultivation", "修炼", "修煉", "修行", "炼丹"),
        g("xianxia", "Xianxia", "Xianxia", "仙侠", "仙俠", "修仙", "修真"),
        g("wuxia", "Wuxia", "Wuxia", "武侠剧", "武俠劇", "江湖"),
        g("gods", "Tanrılar", "Gods", "神明", "神仙", "诸神", "神様"),
        g("afterlife", "Öteki Dünya", "Afterlife", "冥界", "黄泉", "彼岸"),
        g("yokai", "Yokai", "Yokai", "妖怪", "妖怪屋"),
        g("spirit", "Ruh", "Spirit", "灵魂", "靈魂", "亡霊"),
        g("immortality", "Ölümsüzlük", "Immortality", "不老不死", "永生"),
        g("contract", "Sözleşme", "Contract", "契约", "契約"),
        g("sacrifice", "Kurban", "Sacrifice", "牺牲", "犧牲"),
        g("medieval", "Orta Çağ", "Medieval", "中世纪", "中世紀"),
        g("urban_fantasy", "Kent Fantastiği", "Urban Fantasy", "都市奇幻", "現代奇幻", "都市魔法"),
        g("japanese_ancient", "Japon Tarihi", "Japanese Historical", "和风", "和風"),
        g("ancient_china", "Antik Çin", "Ancient China", "国风", "國風", "华夏"),
        g("era_meiji", "Meiji Dönemi", "Meiji Era", "明治"),
        g("era_taisho", "Taisho Dönemi", "Taisho Era", "大正"),
        g("era_showa", "Showa Dönemi", "Showa Era", "昭和"),
        g("era_heisei", "Heisei Dönemi", "Heisei Era", "平成"),
        g("era_reiwa", "Reiwa Dönemi", "Reiwa Era", "令和"),
        g("modern_day", "Günümüz", "Modern Day", "现代", "現代", "当代"),
        g("future", "Gelecek", "Future", "未来", "近未来"),
        g("past", "Geçmiş", "Past", "过去", "過去", "前世"),
        g("sekai_kei", "Sekaikei", "Sekai-kei", "世界系"),
        g("robot", "Robot", "Robot", "机器人", "機器人", "機械人"),
        g("artificial_intelligence", "Yapay Zekâ", "Artificial Intelligence", "AI", "人工智能", "人工知能"),
        g("real_robot", "Gerçek Robot", "Real Robot", "真实系机器人", "リアルロボット"),
        g("super_robot", "Süper Robot", "Super Robot", "超级系机器人", "スーパーロボット"),
        g("firearms", "Ateşli Silahlar", "Firearms", "枪战", "槍戰", "铳", "銃"),
        g("cat", "Kedi", "Cat", "猫", "猫咪", "貓", "ネコ"),
        g("dog", "Köpek", "Dog", "狗", "犬", "柴犬"),
        g("horse", "At", "Horse", "马", "馬", "赛马", "賽馬"),
        g("dragon", "Ejderha", "Dragon", "龙", "龍", "竜"),
        g("fox", "Tilki", "Fox", "狐狸", "妖狐"),
        g("narration", "Anlatıcı", "Narration", "旁白", "独白", "獨白"),
        g("non_linear", "Doğrusal Olmayan Anlatı", "Non-linear", "非线性叙事", "多线叙事"),
        g("multi_perspective", "Çoklu Bakış Açısı", "Multiple Perspectives", "多视角", "多視角"),
        g("documentary_style", "Belgesel Tarzı", "Documentary Style", "伪纪录", "纪实"),
        g("mind_bending", "Kafa Karıştıran", "Mind Screw", "烧脑", "燒腦", "難解", "神展开", "神反轉"),
        g("plot_twist", "Sürpriz Gelişme", "Plot Twist", "反转", "反轉"),
        g("open_ending", "Açık Son", "Open Ending", "开放结局", "开放式结局"),
        g("bad_ending", "Kötü Son", "Bad Ending", "坏结局", "Bad End"),
        g("happy_ending", "Mutlu Son", "Happy Ending", "好结局", "Happy End"),
        g("serious", "Ciddi", "Serious", "严肃", "嚴肅", "硬派"),
        g("light_hearted", "Hafif", "Light Hearted", "轻松", "輕鬆"),
        g("dark", "Karanlık", "Dark", "黑暗", "ダーク", "暗黒"),
        g("beautiful_art", "Görsel Şölen", "Beautiful Art", "画面精美", "作画精美", "神作画"),
        g("great_soundtrack", "Muhteşem Müzik", "Great Soundtrack", "配乐优秀", "配樂優秀", "OST优秀"),
        g("cute", "Sevimli", "Cute", "萌", "可爱", "可愛", "かわいい", "萌え"),
        g("satire", "Hiciv", "Satire", "讽刺", "風刺"),
        g("meta", "Meta Kurgu", "Meta", "メタ", "打破第四面墙", "第四面墙"),
        g("art_house", "Sanat Sineması", "Art House", "文艺", "文藝", "艺术片"),
        g("experimental", "Deneysel", "Experimental", "实验动画", "實驗動畫"),
        g("beautiful_scenery", "Güzel Manzaralar", "Beautiful Scenery", "风景", "風景", "美景"),
        g("dark_comedy", "Kara Mizah", "Dark Comedy", "黑色幽默", "冷笑话"),
        g("slapstick", "Fiziksel Komedi", "Slapstick", "闹剧", "傻瓜喜剧"),
        g("moe", "Moe", "Moe", "萌系", "萌番", "萌豚"),
        g("voting", "Seçim", "Election", "选举", "選舉", "投票"),
        g("politics", "Siyaset", "Politics", "政治", "政治斗争", "權謀"),
        g("conspiracy", "Komplo", "Conspiracy", "阴谋", "陰謀"),
        g("secret_organization", "Gizli Örgüt", "Secret Organization", "秘密组织", "暗部"),
        g("civil_war", "İç Savaş", "Civil War", "内战", "內戰", "动乱"),
        g("revolution", "Devrim", "Revolution", "革命", "起义", "起義"),
        g("epidemic", "Salgın", "Epidemic", "瘟疫", "疫情", "传染病", "傳染病"),
        g("disaster", "Afet", "Disaster", "灾难", "災難", "災害", "地震", "火灾", "火災"),
        g("environment", "Çevre", "Environment", "环保", "環保", "环境", "環境"),
        g("nature", "Doğa", "Nature", "大自然", "森林", "山林"),
        g("locked_room", "Kapalı Oda", "Locked Room", "密室", "密室杀人", "閉鎖空間"),
        g("noir", "Noir", "Noir", "黑色电影", "黑色電影", "ノワール"),
        g("heist", "Soygun", "Heist", "盗贼", "怪盗", "スリ"),

        // ── Duygu / ton ──
        g("healing", "İyileştirici", "Healing", "治愈", "治癒", "治愈系", "療癒", "癒し", "温馨", "溫馨", "温暖", "溫暖"),
        g("depressing", "Kederli", "Downer", "致郁", "致鬱", "压抑", "壓抑", "绝望", "絕望"),
        g("tearjerker", "Duygusal", "Tearjerker", "催泪", "催涙", "感人", "感动", "感動", "泣ける"),
        g("masterpiece", "Başyapıt", "Masterpiece", "神作", "名作", "杰作", "傑作"),
        g("childish", "Çocuksu", "Childish", "幼稚"),

        // ── Yaş / içerik uyarıları ──
        g("all_ages", "Her Yaşa Uygun", "All Ages", "全年龄", "全年齢", "老少咸宜"),
        g("violence", "Şiddet", "Violence", "血腥暴力", "暴力表現", "暴力"),
        g("sexual_content", "Cinsel İçerik", "Sexual Content", "性爱", "性愛"),
        g("smoking", "Sigara", "Smoking", "吸烟", "吸煙", "煙草", "タバコ"),
        g("drinking", "İçki", "Alcohol", "喝酒", "酒精", "酒"),
        g("drugs", "Uyuşturucu", "Drugs", "毒品", "薬物", "ドラッグ"),

        // ── Bölge / ülke ──
        g("japan", "Japonya", "Japan", "日本", "日本动画", "日本製作", "JP"),
        g("china", "Çin", "China", "中国", "中国大陆", "中國大陸", "国产", "國產", "CN"),
        g("korea", "Kore", "Korea", "韩国", "韓國", "南韓", "한국"),
        g("usa", "ABD", "USA", "美国", "美國", "US"),
        g("uk", "İngiltere", "UK", "英国", "英國"),
        g("france", "Fransa", "France", "法国", "法國"),
        g("germany", "Almanya", "Germany", "德国", "德國"),
        g("russia", "Rusya", "Russia", "俄罗斯", "俄羅斯", "苏联", "蘇聯"),
        g("italy", "İtalya", "Italy", "意大利"),
        g("spain", "İspanya", "Spain", "西班牙"),
        g("taiwan", "Tayvan", "Taiwan", "台湾", "臺灣", "中国台湾", "中國台灣"),
        g("hong_kong_region", "Hong Kong", "Hong Kong", "香港", "中国香港", "中國香港"),
        g("thailand", "Tayland", "Thailand", "泰国", "泰國"),
        g("asia", "Asya", "Asia", "亚洲", "亞洲"),
        g("europe", "Avrupa", "Europe", "欧洲", "歐洲"),
        g("western", "Batı", "Western", "欧美", "歐美", "西方"),
        g("middle_east", "Ortadoğu", "Middle East", "中东", "中東"),
        g("africa", "Afrika", "Africa", "非洲"),

        // ── Yayın / dağıtım ──
        g("co_production", "Ortak Yapım", "Co-production", "共同制作", "共同製作", "中日合作", "合拍"),
        g("streaming", "Dijital Yayın", "Streaming", "流媒体", "流媒體", "线上播放", "網絡播放"),
        g("netflix", "Netflix", "Netflix", "Netflix", "网飞", "網飛"),
        g("amazon_prime", "Prime Video", "Prime Video", "亚马逊 Prime", "亞馬遜 Prime"),
        g("crunchyroll", "Crunchyroll", "Crunchyroll", "CR独占", "Crunchyroll独占"),
        g("bilibili", "Bilibili", "Bilibili", "B站", "嗶哩嗶哩"),
        g("iqiyi", "iQIYI", "iQiyi", "爱奇艺", "愛奇藝"),
        g("tencent_video", "Tencent Video", "Tencent Video", "腾讯视频", "騰訊視頻"),
        g("youku", "Youku", "Youku", "优酷", "優酷"),
        g("cctv", "CCTV", "CCTV", "央视", "央視"),
        g("nhk", "NHK", "NHK", "晨间剧", "晨間劇"),
        g("fuji_tv", "Fuji TV", "Fuji Television", "富士电视台", "富士電視台"),
        g("ntv", "NTV", "Nippon TV", "日本电视台", "日本電視台"),
        g("tbs", "TBS", "TBS", "TBS电视台", "TBS電視台"),
        g("tv_asahi", "TV Asahi", "TV Asahi", "朝日电视台", "朝日電視台"),
        g("tv_tokyo", "TV Tokyo", "TV Tokyo", "东京电视台", "東京電視台"),
        g("dubbing", "Dublaj", "Dubbing", "配音版", "国语配音", "英語配音"),
        g("subtitles", "Altyazı", "Subtitles", "字幕", "熟肉", "生肉"),
        g("simulcast", "Eşzamanlı Yayın", "Simulcast", "同步上线", "同步上線"),
        g("remastered", "Yeniden Düzenlenmiş", "Remastered", "修復版", "蓝光修复", "重製版"),
        g("uncut", "Kesintisiz", "Uncut", "未删减", "未刪減", "完全版", "無修正"),
        g("censorship", "Sansür", "Censorship", "删减", "刪減", "审查", "審查"),
        g("box_set", "Kutu Seti", "Box Set", "BD-BOX", "套装", "套裝"),
        g("bonus_footage", "Ekstra İçerik", "Bonus Footage", "特典", "映像特典"),

        // ── Müzik ürünleri ──
        g("opening_theme", "Açılış Teması", "Opening Theme", "OP曲", "オープニング曲"),
        g("ending_theme", "Kapanış Teması", "Ending Theme", "ED曲", "エンディング曲"),
        g("insert_song", "Bölüm İçi Şarkı", "Insert Song", "插曲", "IN曲"),
        g("theme_song", "Tema Şarkısı", "Theme Song", "主题曲", "主題歌"),
        g("character_song", "Karakter Şarkısı", "Character Song", "角色歌", "キャラソン"),
        g("soundtrack", "Film Müziği", "Soundtrack", "OST", "原声带", "原聲帶", "BGM"),
        g("single", "Tekli", "Single", "单曲", "單曲"),
        g("album", "Albüm", "Album", "专辑", "專輯"),
        g("lyrics", "Şarkı Sözleri", "Lyrics", "歌词", "作詞"),
        g("composer", "Besteci", "Composer", "作曲", "作曲家"),
        g("arrangement", "Düzenleme", "Arrangement", "编曲", "編曲"),
        g("singer", "Şarkıcı", "Singer", "歌手", "歌い手", "Vocal"),
        g("band", "Müzik Grubu", "Band", "乐队", "樂隊", "バンド", "軽音"),
        g("live_concert", "Konser", "Live Concert", "演唱会", "演唱會", "ライブ"),
        g("vocaloid", "Vocaloid", "Vocaloid", "V家", "ボーカロイド", "初音未来"),
        g("rock", "Rock", "Rock", "摇滚", "搖滾", "ロック"),
        g("pop_music", "Pop", "Pop", "流行音乐", "ポップス"),
        g("jazz", "Caz", "Jazz", "爵士", "ジャズ"),
        g("classical_music", "Klasik Müzik", "Classical", "古典音乐", "交響樂", "交响乐"),
        g("electronic_music", "Elektronik Müzik", "Electronic Music", "电子音乐", "电音", "Electronic"),
        g("anisong", "Anisong", "Anisong", "动漫歌曲", "アニソン"),

        // ── Kitap / yayın türleri ──
        g("manga", "Manga", "Manga", "漫画", "漫畫", "マンガ"),
        g("manhwa", "Manhwa", "Manhwa", "韩漫", "韓國漫畫"),
        g("manhua", "Manhua", "Manhua", "国漫", "華漫", "中國漫畫"),
        g("webtoon", "Webtoon", "Webtoon", "条漫", "條漫", "網漫"),
        g("comic_book", "Çizgi Roman", "Comic", "美漫", "漫画书", "Comic Book"),
        g("graphic_novel", "Grafik Roman", "Graphic Novel", "图像小说", "圖像小說"),
        g("artbook", "Sanat Kitabı", "Artbook", "画集", "插畫集", "原画集"),
        g("setting_material", "Referans Kitabı", "Setting Material", "设定集", "設定集", "资料集", "公式书"),
        g("novel", "Roman", "Novel", "小说", "小說", "文学", "文學"),
        g("anthology", "Antoloji", "Anthology", "短篇集", "选集", "選集"),
        g("trilogy", "Üçleme", "Trilogy", "三部曲", "三部作"),
        g("biography", "Biyografi", "Biography", "传记", "自传", "人物传记"),
        g("serialization", "Dergi Tefrikası", "Serialized In A Magazine", "连载杂志", "雜志连载", "雜誌連載"),

        // ── Sektör / tanıtım ──
        g("anime_industry", "Animasyon Sektörü", "Animation Industry", "动画业界", "業界番", "制作进行"),
        g("manga_industry", "Manga Sektörü", "Manga Industry", "漫画业界", "編集者"),
        g("toy_promo", "Oyuncak Tanıtımı", "Toy Promo", "玩具广告", "模型宣传", "プラモ"),
        g("figure", "Figür", "Figure", "手办", "手辦"),
        g("gacha", "Gacha", "Gacha", "抽卡", "扭蛋", "ガチャ"),
        g("rhythm_game", "Ritim Oyunu", "Rhythm Game", "音乐游戏", "音游", "MUG"),
        g("esports", "E-Spor", "Esports", "電競比賽", "电竞比赛"),
        g("mobile_game", "Mobil Oyun", "Mobile Game", "手游", "手遊"),
        g("vr_game", "Sanal Gerçeklik Oyunu", "VR Game", "VR游戏", "虚拟现实游戏"),
        g("mmorpg", "MMORPG", "MMORPG", "网游", "網遊", "在线游戏", "MMORPG"),
        g("3d_cg", "3B CG", "3DCG", "3D", "CG动画", "三维动画", "三維動畫"),
        g("traditional_animation", "Geleneksel Animasyon", "Traditional Animation", "手绘", "手繪", "赛璐璐", "2D"),
        g("stop_motion", "Stop Motion", "Stop Motion", "定格动画", "定格"),
        g("motion_comic", "Hareketli Panel", "Motion Comic", "动态漫画", "動態漫畫"),
        g("western_animation", "Batı Animasyonu", "Western Animation", "欧美动画", "美國動畫", "Cartoon"),
        g("independent_work", "Bağımsız Yapım", "Independent", "独立动画", "獨立動畫", "自主制作"),
        g("student_film", "Öğrenci Filmi", "Student Film", "学生作品", "畢製"),
        g("debut_work", "İlk Yapım", "Debut Work", "处女作", "初監督作品"),
        g("box_office_hit", "Gişe Rekortmeni", "Box Office Hit", "票房冠军", "票房大卖"),
        g("festival_winner", "Festival Ödüllü", "Festival Winner", "影展", "电影节获奖"),

        // ── Kurum takma adları (etiket olarak yazılanlar) ──
        g("kyoto_animation", "Kyoto Animation", "Kyoto Animation", "京阿尼", "京都动画", "京都動畫", "京都動画", "京都アニメーション", "京アニ", "KyoAni", "Kyoto Animation"),
        g("key_studio", "Key", "Key", "key", "Key", "Key社", "K社"),
        g("type_moon", "Type-Moon", "Type-Moon", "型月", "TYPE-MOON", "型月世界"),
        g("ghibli", "Studio Ghibli", "Studio Ghibli", "吉卜力", "ジブリ", "吉卜力工作室"),
        g("pixar", "Pixar", "Pixar", "皮克斯", "ピクサー"),
        g("disney", "Disney", "Disney", "迪士尼", "迪斯尼", "ディズニー"),
        g("marvel", "Marvel", "Marvel", "漫威", "マーベル"),
        g("dc_comics", "DC", "DC", "DC漫画", "DC Comics"),
        g("gainax", "GAINAX", "GAINAX", "ガイナックス", "ガンックス"),
        g("trigger_studio", "TRIGGER", "TRIGGER", "扳机社", "扳機社", "トリガー", "トリガー社"),
        g("ufotable", "ufotable", "ufotable", "幽浮社", "飞碟社", "飛碟社"),
        g("wit_studio", "WIT STUDIO", "WIT STUDIO", "霸权社", "ウィットスタジオ"),
        g("madhouse", "Madhouse", "Madhouse", "疯房子", "瘋房子", "マッドハウス"),
        g("bones_studio", "BONES", "BONES", "骨头社", "骨頭社", "ボンズ", "ボンズ社"),
        g("shaft_studio", "SHAFT", "SHAFT", "シャフト", "シャフト社"),
        g("pa_works", "P.A.Works", "P.A.Works", "P.A.WORKS", "PA社", "PAワークス", "ピーエーワークス"),
        g("white_fox", "WHITE FOX", "WHITE FOX", "白狐社", "WHITEFOX"),
        g("jc_staff", "J.C.Staff", "J.C.Staff", "J.C.STAFF", "JC社"),
        g("toei_animation", "Toei Animation", "Toei Animation", "东映动画", "東映動畫"),
        g("sunrise_studio", "Sunrise", "Sunrise", "サンライズ", "サンライズ社", "日升", "日昇", "日升动画"),
        g("a1_pictures", "A-1 Pictures", "A-1 Pictures", "A1社"),
        g("cloverworks", "CloverWorks", "CloverWorks", "Clover Works", "クローバーワークス"),
        g("doga_kobo", "Doga Kobo", "Doga Kobo", "动画工房", "動画工房"),
        g("diomedea", "Diomedéa", "Diomedéa", "ディオメディア"),
        g("silver_link", "Silver Link", "Silver Link", "シルバーリンク"),
        g("pierrot", "Studio Pierrot", "Studio Pierrot", "ぴえろ", "小丑社"),
        g("studio_deen", "Studio Deen", "Studio Deen", "スタジオディーン", "ディーン"),
        g("david_production", "David Production", "David Production", "ダビプロ"),
        g("production_ig", "Production I.G", "Production I.G", "Production.IG", "ProductionI.G", "Production IG", "IG社", "I.G", "プロダクション・アイジー"),

        // ── Kişi adları (etiket olarak geçen bilinen isimler) ──
        g("jun_maeda", "Jun Maeda", "Jun Maeda", "麻枝准", "麻枝準"),
        g("makoto_shinkai", "Makoto Shinkai", "Makoto Shinkai", "新海诚", "新海誠"),
        g("hayao_miyazaki", "Hayao Miyazaki", "Hayao Miyazaki", "宫崎骏", "宮崎駿"),
        g("gen_urobuchi", "Gen Urobuchi", "Gen Urobuchi", "虚渊玄", "虚淵玄", "虛淵玄", "老虚"),
        g("hideaki_anno", "Hideaki Anno", "Hideaki Anno", "庵野秀明"),
        g("monogatari", "Monogatari", "Monogatari", "物语", "物語"),

        // ── Sık geçen tekil etiketler ──
        g("life", "Yaşam", "Life", "人生", "生命"),
        g("seasons", "Mevsimler", "Seasons", "四季", "春夏秋冬"),
        g("cherry_blossom", "Kiraz Çiçeği", "Cherry Blossom", "樱花", "桜", "花见"),

        // ── Kaynak malzeme kısaltmaları (Bangumi kullanıcı etiketleri) ──
        g("light_novel_adaptation", "Hafif Roman Uyarlaması", "Light Novel Adaptation",
            "轻小说改", "輕小說改", "轻改", "輕改", "文库改", "ラノベ改", "ライトノベル原作"),
        g("web_novel_adaptation", "Web Romanı Uyarlaması", "Web Novel Adaptation", "网文改", "網文改", "网络小说改"),
        g("anime_adaptation", "Anime Uyarlaması", "Anime Adaptation", "动画改", "動畫改", "番改"),
        g("drama_adaptation", "Dizi Uyarlaması", "Drama Adaptation", "剧改", "劇改", "电视剧改"),
        g("toy_adaptation", "Oyuncak Uyarlaması", "Toy Adaptation", "玩具改", "模型改"),
        g("original_work", "Orijinal Eser", "Original Story", "原作", "原著"),

        // ── Fandom jargonu / değerlendirme etiketleri ──
        g("profound", "Derin Anlatım", "Profound", "深度", "有深度", "深刻", "内涵", "內涵"),
        g("ahead_of_its_time", "Çağının Ötesinde", "Ahead Of Its Time", "超前", "超前时代", "前卫", "前衛"),
        g("iconic_scene", "Unutulmaz Sahneler", "Iconic Scenes", "名场面", "名場面", "名台词", "名台詞"),
        g("legendary_episode", "Efsane Bölüm", "Legendary Episode", "神回", "名作之壁"),
        g("nostalgia", "Nostalji", "Nostalgia", "情怀", "情懷", "怀旧", "懷舊", "回忆杀", "回憶殺"),
        g("childhood_memory", "Çocukluk Anısı", "Childhood Memory", "童年", "童年回忆", "童年回憶", "童年阴影"),
        g("good_work", "İyi Yapım", "Good Work", "良作", "佳作", "口碑佳", "高分"),
        g("must_watch", "Mutlaka İzlenmeli", "Must Watch", "必看", "必刷", "推荐", "推薦"),
        g("overrated", "Abartılmış", "Overrated", "过誉", "過譽"),
        g("botched_ending", "Final Faciası", "Botched Ending", "烂尾", "爛尾", "结局崩坏"),
        g("hype", "Coşkulu", "Hype", "燃", "燃向", "热血燃", "燃え"),
        g("story_driven", "Hikâye Odaklı", "Story Driven", "剧情向", "劇情向", "故事性"),
        g("worldbuilding", "Dünya Kurgusu", "Worldbuilding", "世界观", "世界觀", "世界观宏大", "设定党"),
        g("setting_design", "Kurgu Tasarımı", "Setting Design", "设定", "設定", "设定考究"),
        g("foreshadowing", "Önceden Serpiştirme", "Foreshadowing", "伏笔", "伏筆", "细节控"),
        g("creative_premise", "Yaratıcı Kurgu", "Creative Premise", "脑洞", "腦洞", "脑洞大开", "脑洞清奇"),
        g("homage", "Saygı Duruşu", "Homage", "致敬", "恶趣味致敬"),
        g("social_realism", "Toplumsal Gerçekçilik", "Social Realism", "社会派", "社會派", "现实主义", "現實主義"),
        g("orthodox", "Klasik Kalıp", "Orthodox", "王道", "王道热血"),
        g("dark_and_brutal", "Karanlık ve Acımasız", "Dark And Brutal", "黑深残", "黑暗系"),
        g("denpa", "Tuhaf/Deneysel", "Denpa", "电波", "電波", "电波系"),
        g("pure_moe", "Saf Moe", "Pure Moe", "废萌", "廢萌", "萌系日常"),
        g("fanservice", "Fan Servisi", "Fanservice", "福利", "福利向", "サービスシーン"),
        g("female_lead", "Kadın Başrol", "Female Lead", "女主", "女主角", "大女主"),
        g("male_lead", "Erkek Başrol", "Male Lead", "男主", "男主角"),
        g("strong_protagonist", "Güçlü Başkahraman", "Strong Protagonist", "主角很强", "无敌流", "開掛"),
        g("underdog", "Sıfırdan Yükseliş", "Underdog Story", "逆袭", "逆襲", "成长流", "成長流"),
        g("slow_pace", "Ağır Tempo", "Slow Pace", "节奏慢", "節奏慢", "慢节奏"),
        g("fast_pace", "Hızlı Tempo", "Fast Pace", "节奏快", "節奏快", "快节奏"),

        // ── Yapım / teknik etiketler ──
        g("animation_quality", "Animasyon Kalitesi", "Animation Quality", "作画", "作畫", "作画优秀", "作畫優秀"),
        g("bad_animation", "Bozuk Animasyon", "Off-Model Animation", "作画崩坏", "作畫崩壞", "崩坏"),
        g("storyboard_tag", "Storyboard", "Storyboard", "分镜", "分鏡", "絵コンテ"),
        g("staging", "Sahneleme", "Direction", "演出", "演出优秀"),
        g("director_tag", "Yönetmen", "Director", "监督", "監督", "导演", "導演"),
        g("script_tag", "Senaryo", "Script", "脚本", "腳本", "剧本", "劇本"),
        g("character_design", "Karakter Tasarımı", "Character Design", "人设", "人設", "人物设定", "キャラデザ"),
        g("sound_direction", "Ses Yönetmenliği", "Sound Direction", "音响监督", "音響監督"),
        g("production_tag", "Yapım", "Production", "制作公司", "製作公司", "製作"),
        g("voice_cast", "Seslendirme Kadrosu", "Voice Cast", "声优阵容", "聲優陣容", "豪华声优"),

        // ── Yönetmenler / senaristler / yapımcılar ──
        g("nagaru_tanigawa", "Nagaru Tanigawa", "Nagaru Tanigawa", "谷川流", "谷川 流"),
        g("tatsuya_ishihara", "Tatsuya Ishihara", "Tatsuya Ishihara", "石原立也"),
        g("yasuhiro_takemoto", "Yasuhiro Takemoto", "Yasuhiro Takemoto", "武本康弘"),
        g("naoko_yamada", "Naoko Yamada", "Naoko Yamada", "山田尚子"),
        g("yoshiji_kigami", "Yoshiji Kigami", "Yoshiji Kigami", "木上益治"),
        g("akiyuki_shinbo", "Akiyuki Shinbo", "Akiyuki Shinbo", "新房昭之"),
        g("tatsuya_oishi", "Tatsuya Oishi", "Tatsuya Oishi", "尾石达也", "尾石達也"),
        g("nisio_isin", "Nisio Isin", "Nisio Isin", "西尾维新", "西尾維新"),
        g("kinoko_nasu", "Kinoko Nasu", "Kinoko Nasu", "奈须蘑菇", "奈須きのこ", "奈须きのこ"),
        g("takashi_takeuchi", "Takashi Takeuchi", "Takashi Takeuchi", "武内崇", "武內崇"),
        g("isao_takahata", "Isao Takahata", "Isao Takahata", "高畑勋", "高畑勲", "高畑勳"),
        g("goro_miyazaki", "Goro Miyazaki", "Goro Miyazaki", "宫崎吾朗", "宮崎吾朗"),
        g("toshio_suzuki", "Toshio Suzuki", "Toshio Suzuki", "铃木敏夫", "鈴木敏夫"),
        g("satoshi_kon", "Satoshi Kon", "Satoshi Kon", "今敏", "今 敏"),
        g("katsuhiro_otomo", "Katsuhiro Otomo", "Katsuhiro Otomo", "大友克洋"),
        g("mamoru_oshii", "Mamoru Oshii", "Mamoru Oshii", "押井守"),
        g("kenji_kamiyama", "Kenji Kamiyama", "Kenji Kamiyama", "神山健治"),
        g("masamune_shirow", "Masamune Shirow", "Masamune Shirow", "士郎正宗"),
        g("yoshiyuki_tomino", "Yoshiyuki Tomino", "Yoshiyuki Tomino", "富野由悠季", "富野喜幸"),
        g("yoshiyuki_sadamoto", "Yoshiyuki Sadamoto", "Yoshiyuki Sadamoto", "贞本义行", "貞本義行"),
        g("mamoru_hosoda", "Mamoru Hosoda", "Mamoru Hosoda", "细田守", "細田守"),
        g("masaaki_yuasa", "Masaaki Yuasa", "Masaaki Yuasa", "汤浅政明", "湯浅政明", "湯淺政明"),
        g("keiichi_hara", "Keiichi Hara", "Keiichi Hara", "原惠一", "原恵一"),
        g("sunao_katabuchi", "Sunao Katabuchi", "Sunao Katabuchi", "片渊须直", "片渕須直"),
        g("shinichiro_watanabe", "Shinichiro Watanabe", "Shinichiro Watanabe", "渡边信一郎", "渡辺信一郎"),
        g("tetsuro_araki", "Tetsuro Araki", "Tetsuro Araki", "荒木哲郎"),
        g("tsutomu_mizushima", "Tsutomu Mizushima", "Tsutomu Mizushima", "水岛努", "水島努"),
        g("seiji_mizushima", "Seiji Mizushima", "Seiji Mizushima", "水岛精二", "水島精二"),
        g("tatsuyuki_nagai", "Tatsuyuki Nagai", "Tatsuyuki Nagai", "长井龙雪", "長井龍雪"),
        g("mari_okada", "Mari Okada", "Mari Okada", "冈田麿里", "岡田麿里", "冈田麻里"),
        g("goro_taniguchi", "Goro Taniguchi", "Goro Taniguchi", "谷口悟朗"),
        g("ichiro_okouchi", "Ichiro Okouchi", "Ichiro Okouchi", "大河内一楼", "大河內一樓"),
        g("kunihiko_ikuhara", "Kunihiko Ikuhara", "Kunihiko Ikuhara", "几原邦彦", "幾原邦彦"),
        g("shoji_kawamori", "Shoji Kawamori", "Shoji Kawamori", "河森正治"),
        g("osamu_dezaki", "Osamu Dezaki", "Osamu Dezaki", "出崎统", "出崎統"),
        g("yoshiaki_kawajiri", "Yoshiaki Kawajiri", "Yoshiaki Kawajiri", "川尻善昭"),
        g("tomohiko_ito", "Tomohiko Ito", "Tomohiko Ito", "伊藤智彦"),
        g("yasuhiro_yoshiura", "Yasuhiro Yoshiura", "Yasuhiro Yoshiura", "吉浦康裕"),
        g("reiko_yoshida", "Reiko Yoshida", "Reiko Yoshida", "吉田玲子"),
        g("jukki_hanada", "Jukki Hanada", "Jukki Hanada", "花田十辉", "花田十輝"),
        g("chiyomaru_shikura", "Chiyomaru Shikura", "Chiyomaru Shikura", "志仓千代丸", "志倉千代丸"),
        g("hiroyuki_imaishi", "Hiroyuki Imaishi", "Hiroyuki Imaishi", "今石洋之"),
        g("kazuya_tsurumaki", "Kazuya Tsurumaki", "Kazuya Tsurumaki", "鹤卷和哉", "鶴巻和哉"),
        g("yutaka_yamamoto", "Yutaka Yamamoto", "Yutaka Yamamoto", "山本宽", "山本寛"),
        g("takahiro_omori", "Takahiro Omori", "Takahiro Omori", "大森贵弘", "大森貴弘"),

        // ── Besteciler / müzik ekibi ──
        g("yoko_kanno", "Yoko Kanno", "Yoko Kanno", "菅野洋子", "菅野よう子"),
        g("kenji_kawai", "Kenji Kawai", "Kenji Kawai", "川井宪次", "川井憲次"),
        g("joe_hisaishi", "Joe Hisaishi", "Joe Hisaishi", "久石让", "久石譲", "久石讓"),
        g("yuki_kajiura", "Yuki Kajiura", "Yuki Kajiura", "梶浦由记", "梶浦由記"),
        g("hiroyuki_sawano", "Hiroyuki Sawano", "Hiroyuki Sawano", "泽野弘之", "澤野弘之"),
        g("shiro_sagisu", "Shiro Sagisu", "Shiro Sagisu", "鹭巣诗郎", "鷺巣詩郎"),
        g("kohei_tanaka", "Kohei Tanaka", "Kohei Tanaka", "田中公平"),
        g("satoru_kousaki", "Satoru Kousaki", "Satoru Kousaki", "神前晓", "神前暁"),
        g("shinji_orito", "Shinji Orito", "Shinji Orito", "折户伸治", "折戸伸治"),
        g("kensuke_ushio", "Kensuke Ushio", "Kensuke Ushio", "牛尾宪辅", "牛尾憲輔"),
        g("taku_iwasaki", "Taku Iwasaki", "Taku Iwasaki", "岩崎琢"),
        g("masaru_yokoyama", "Masaru Yokoyama", "Masaru Yokoyama", "横山克", "橫山克"),
        g("yuki_hayashi", "Yuki Hayashi", "Yuki Hayashi", "林友树", "林ゆうき"),
        g("origa_singer", "Origa", "Origa", "オリガ", "Origa"),

        // ── Mangaka / roman yazarları ──
        g("osamu_tezuka", "Osamu Tezuka", "Osamu Tezuka", "手冢治虫", "手塚治虫"),
        g("akira_toriyama", "Akira Toriyama", "Akira Toriyama", "鸟山明", "鳥山明"),
        g("eiichiro_oda", "Eiichiro Oda", "Eiichiro Oda", "尾田荣一郎", "尾田栄一郎"),
        g("masashi_kishimoto", "Masashi Kishimoto", "Masashi Kishimoto", "岸本齐史", "岸本斉史"),
        g("tite_kubo", "Tite Kubo", "Tite Kubo", "久保带人", "久保帯人"),
        g("hajime_isayama", "Hajime Isayama", "Hajime Isayama", "谏山创", "諫山創"),
        g("koyoharu_gotouge", "Koyoharu Gotouge", "Koyoharu Gotouge", "吾峠呼世晴"),
        g("gege_akutami", "Gege Akutami", "Gege Akutami", "芥见下下", "芥見下々"),
        g("hiromu_arakawa", "Hiromu Arakawa", "Hiromu Arakawa", "荒川弘"),
        g("rumiko_takahashi", "Rumiko Takahashi", "Rumiko Takahashi", "高桥留美子", "高橋留美子"),
        g("takehiko_inoue", "Takehiko Inoue", "Takehiko Inoue", "井上雄彦"),
        g("kentaro_miura", "Kentaro Miura", "Kentaro Miura", "三浦建太郎"),
        g("naoki_urasawa", "Naoki Urasawa", "Naoki Urasawa", "浦泽直树", "浦沢直樹"),
        g("tatsuki_fujimoto", "Tatsuki Fujimoto", "Tatsuki Fujimoto", "藤本树", "藤本タツキ"),
        g("aka_akasaka", "Aka Akasaka", "Aka Akasaka", "赤坂阿卡", "赤坂アカ"),
        g("clamp", "CLAMP", "CLAMP", "CLAMP", "クランプ"),
        g("fujiko_fujio", "Fujiko Fujio", "Fujiko Fujio", "藤子不二雄", "藤子・F・不二雄"),
        g("yoshihiro_togashi", "Yoshihiro Togashi", "Yoshihiro Togashi", "富坚义博", "冨樫義博"),
        g("tsugumi_ohba", "Tsugumi Ohba", "Tsugumi Ohba", "大场鸫", "大場つぐみ"),
        g("takeshi_obata", "Takeshi Obata", "Takeshi Obata", "小畑健"),
        g("hideaki_sorachi", "Hideaki Sorachi", "Hideaki Sorachi", "空知英秋"),
        g("yasuhisa_hara", "Yasuhisa Hara", "Yasuhisa Hara", "原泰久"),
        g("sui_ishida", "Sui Ishida", "Sui Ishida", "石田スイ"),
        g("junji_ito", "Junji Ito", "Junji Ito", "伊藤润二", "伊藤潤二"),
        g("tsutomu_nihei", "Tsutomu Nihei", "Tsutomu Nihei", "贰瓶勉", "弐瓶勉"),
        g("yoshiki_tanaka", "Yoshiki Tanaka", "Yoshiki Tanaka", "田中芳树", "田中芳樹"),
        g("fumiaki_maruto", "Fumiaki Maruto", "Fumiaki Maruto", "丸户史明", "丸戸史明"),
        g("wataru_watari", "Wataru Watari", "Wataru Watari", "渡航"),
        g("hajime_kamoshida", "Hajime Kamoshida", "Hajime Kamoshida", "鸭志田一", "鴨志田一"),
        g("yuyuko_takemiya", "Yuyuko Takemiya", "Yuyuko Takemiya", "竹宫悠由子", "竹宮ゆゆこ"),
        g("reki_kawahara", "Reki Kawahara", "Reki Kawahara", "川原砾", "川原礫"),
        g("tappei_nagatsuki", "Tappei Nagatsuki", "Tappei Nagatsuki", "长月达平", "長月達平"),
        g("fuse_author", "Fuse", "Fuse", "伏濑", "伏瀬"),
        g("keiichi_sigsawa", "Keiichi Sigsawa", "Keiichi Sigsawa", "时雨泽惠一", "時雨沢恵一"),

        // ── Seslendirme sanatçıları ──
        g("aya_hirano", "Aya Hirano", "Aya Hirano", "平野绫", "平野綾"),
        g("minori_chihara", "Minori Chihara", "Minori Chihara", "茅原实里", "茅原実里"),
        g("yuko_goto", "Yuko Goto", "Yuko Goto", "后藤邑子", "後藤邑子"),
        g("tomokazu_sugita", "Tomokazu Sugita", "Tomokazu Sugita", "杉田智和"),
        g("daisuke_ono", "Daisuke Ono", "Daisuke Ono", "小野大辅", "小野大輔"),
        g("atsuko_tanaka", "Atsuko Tanaka", "Atsuko Tanaka", "田中敦子"),
        g("akio_otsuka", "Akio Otsuka", "Akio Otsuka", "大冢明夫", "大塚明夫"),
        g("koichi_yamadera", "Koichi Yamadera", "Koichi Yamadera", "山寺宏一"),
        g("hiroshi_kamiya", "Hiroshi Kamiya", "Hiroshi Kamiya", "神谷浩史"),
        g("rie_kugimiya", "Rie Kugimiya", "Rie Kugimiya", "钉宫理惠", "釘宮理恵"),
        g("nana_mizuki", "Nana Mizuki", "Nana Mizuki", "水树奈奈", "水樹奈々"),
        g("megumi_hayashibara", "Megumi Hayashibara", "Megumi Hayashibara", "林原惠", "林原めぐみ"),
        g("maaya_sakamoto", "Maaya Sakamoto", "Maaya Sakamoto", "坂本真绫", "坂本真綾"),
        g("mamoru_miyano", "Mamoru Miyano", "Mamoru Miyano", "宫野真守", "宮野真守"),
        g("yuki_kaji", "Yuki Kaji", "Yuki Kaji", "梶裕贵", "梶裕貴"),
        g("aoi_yuuki", "Aoi Yuuki", "Aoi Yuuki", "悠木碧"),
        g("saori_hayami", "Saori Hayami", "Saori Hayami", "早见沙织", "早見沙織"),
        g("kana_hanazawa", "Kana Hanazawa", "Kana Hanazawa", "花泽香菜", "花澤香菜"),
        g("miyuki_sawashiro", "Miyuki Sawashiro", "Miyuki Sawashiro", "泽城美雪", "沢城みゆき"),
        g("takahiro_sakurai", "Takahiro Sakurai", "Takahiro Sakurai", "樱井孝宏", "櫻井孝宏"),
        g("yuichi_nakamura", "Yuichi Nakamura", "Yuichi Nakamura", "中村悠一"),
        g("akira_ishida", "Akira Ishida", "Akira Ishida", "石田彰"),
        g("hikaru_midorikawa", "Hikaru Midorikawa", "Hikaru Midorikawa", "绿川光", "緑川光"),
        g("maaya_uchida", "Maaya Uchida", "Maaya Uchida", "内田真礼", "内田真礼"),
        g("aki_toyosaki", "Aki Toyosaki", "Aki Toyosaki", "丰崎爱生", "豊崎愛生"),
        g("yoko_hikasa", "Yoko Hikasa", "Yoko Hikasa", "日笠阳子", "日笠陽子"),
        g("ayana_taketatsu", "Ayana Taketatsu", "Ayana Taketatsu", "竹达彩奈", "竹達彩奈"),
        g("ayane_sakura", "Ayane Sakura", "Ayane Sakura", "佐仓绫音", "佐倉綾音"),
        g("sora_amamiya", "Sora Amamiya", "Sora Amamiya", "雨宫天", "雨宮天"),
        g("ai_kayano", "Ai Kayano", "Ai Kayano", "茅野爱衣", "茅野愛衣"),
        g("haruka_tomatsu", "Haruka Tomatsu", "Haruka Tomatsu", "户松遥", "戸松遥"),
        g("sumire_uesaka", "Sumire Uesaka", "Sumire Uesaka", "上坂堇", "上坂すみれ"),

        // ── Sık etiketlenen karakterler ──
        g("haruhi_character", "Haruhi Suzumiya", "Haruhi Suzumiya",
            "凉宫春日", "涼宮春日", "涼宮ハルヒ", "ハルヒ", "凉宫春日系列", "ハルヒシリーズ"),
        g("yuki_nagato", "Yuki Nagato", "Yuki Nagato", "长门有希", "長門有希", "长门", "長門"),
        g("mikuru_asahina", "Mikuru Asahina", "Mikuru Asahina", "朝比奈实玖瑠", "朝比奈みくる", "朝比奈"),
        g("itsuki_koizumi", "Itsuki Koizumi", "Itsuki Koizumi", "古泉一树", "古泉一樹", "古泉"),
        g("kyon", "Kyon", "Kyon", "阿虚", "阿虛", "囧虚", "囧虛", "キョン"),
        g("moe_goddess", "Moe Tanrıçası", "Great Moe Goddess", "大萌神", "萌神"),
        g("motoko_kusanagi", "Motoko Kusanagi", "Motoko Kusanagi", "草薙素子", "素子", "草薙"),
        g("the_major", "Binbaşı", "The Major", "少佐"),
        g("batou", "Batou", "Batou", "巴特", "バトー"),
        g("togusa", "Togusa", "Togusa", "德古沙", "トグサ"),
        g("tachikoma", "Tachikoma", "Tachikoma", "塔奇克马", "塔奇克馬", "タチコマ"),
        g("daisuke_aramaki", "Daisuke Aramaki", "Daisuke Aramaki", "荒卷大辅", "荒巻大輔"),
        g("laughing_man", "Gülen Adam", "Laughing Man", "笑面男", "笑い男"),
        g("rei_ayanami", "Rei Ayanami", "Rei Ayanami", "绫波丽", "綾波レイ", "绫波"),
        g("asuka_langley", "Asuka Langley", "Asuka Langley", "明日香", "アスカ", "惣流·明日香"),
        g("shinji_ikari", "Shinji Ikari", "Shinji Ikari", "碇真嗣", "真嗣"),
        g("lelouch", "Lelouch", "Lelouch", "鲁路修", "魯路修", "ルルーシュ"),
        g("light_yagami", "Light Yagami", "Light Yagami", "夜神月"),
        g("levi_ackerman", "Levi Ackerman", "Levi Ackerman", "利威尔", "リヴァイ", "兵长"),
        g("saber_character", "Saber", "Saber", "赛巴", "セイバー", "阿尔托莉雅"),

        // ── Sık etiketlenen eserler / seriler ──
        g("sos_brigade", "SOS Tugayı", "SOS Brigade", "SOS团", "SOS団"),
        g("haruhi_melancholy", "Haruhi Suzumiya'nın Melankolisi", "The Melancholy Of Haruhi Suzumiya",
            "凉宫春日的忧郁", "涼宮春日的憂鬱", "涼宮ハルヒの憂鬱"),
        g("haruhi_disappearance", "Haruhi Suzumiya'nın Kayboluşu", "The Disappearance Of Haruhi Suzumiya",
            "凉宫春日的消失", "涼宮春日的消失", "涼宮ハルヒの消失"),
        g("disappearance", "Kayboluş", "Disappearance", "消失"),
        g("ghost_in_the_shell", "Ghost in the Shell", "Ghost in the Shell",
            "攻壳机动队", "攻殻機動隊", "攻殼機動隊", "攻壳", "攻殻", "GITS"),
        g("section_nine", "9. Şube", "Section 9", "公安九课", "公安9課", "公安九課"),
        g("evangelion", "Evangelion", "Evangelion", "新世纪福音战士", "新世紀エヴァンゲリオン", "福音战士", "EVA", "エヴァ"),
        g("gundam", "Gundam", "Gundam", "高达", "高達", "ガンダム", "敢达"),
        g("naruto_series", "Naruto", "Naruto", "火影忍者", "火影", "NARUTO"),
        g("one_piece", "One Piece", "One Piece", "海贼王", "海賊王", "航海王", "ONE PIECE"),
        g("attack_on_titan", "Attack on Titan", "Attack on Titan", "进击的巨人", "進撃の巨人", "巨人"),
        g("demon_slayer", "Demon Slayer", "Demon Slayer", "鬼灭之刃", "鬼滅の刃", "鬼灭"),
        g("jujutsu_kaisen", "Jujutsu Kaisen", "Jujutsu Kaisen", "咒术回战", "呪術廻戦"),
        g("spy_family", "SPY x FAMILY", "SPY x FAMILY", "间谍过家家", "間諜過家家", "SPY×FAMILY"),
        g("dragon_ball", "Dragon Ball", "Dragon Ball", "龙珠", "龍珠", "ドラゴンボール"),
        g("doraemon", "Doraemon", "Doraemon", "哆啦A梦", "多啦A夢", "ドラえもん"),
        g("detective_conan", "Detective Conan", "Detective Conan", "名侦探柯南", "名探偵コナン", "柯南"),
        g("fullmetal_alchemist", "Fullmetal Alchemist", "Fullmetal Alchemist", "钢之炼金术师", "鋼の錬金術師", "钢炼"),
        g("death_note_series", "Death Note", "Death Note", "死亡笔记", "死亡筆記", "DEATH NOTE"),
        g("fate_series", "Fate", "Fate", "命运之夜", "Fate系列", "フェイト"),
        g("madoka_magica", "Puella Magi Madoka Magica", "Puella Magi Madoka Magica",
            "魔法少女小圆", "魔法少女まどか☆マギカ", "まどマギ", "小圆"),
        g("k_on", "K-On!", "K-On!", "轻音少女", "輕音少女", "けいおん"),
        g("lucky_star", "Lucky Star", "Lucky Star", "幸运星", "幸運星", "らき☆すた"),
        g("full_metal_panic", "Full Metal Panic!", "Full Metal Panic!", "全金属狂潮", "フルメタル・パニック"),
        g("bakemonogatari", "Bakemonogatari", "Bakemonogatari", "化物语", "化物語"),
        g("cowboy_bebop", "Cowboy Bebop", "Cowboy Bebop", "星际牛仔", "星際牛仔", "カウボーイビバップ"),
        g("spirited_away", "Spirited Away", "Spirited Away", "千与千寻", "千と千尋の神隠し"),
        g("totoro", "My Neighbor Totoro", "My Neighbor Totoro", "龙猫", "龍貓", "となりのトトロ"),
        g("your_name", "Your Name", "Your Name", "你的名字", "君の名は"),
        g("five_cm", "5 Centimeters per Second", "5 Centimeters per Second", "秒速五厘米", "秒速5センチメートル"),
        g("garden_of_words", "The Garden of Words", "The Garden of Words", "言叶之庭", "言の葉の庭"),
        g("psycho_pass", "Psycho-Pass", "Psycho-Pass", "心理测量者", "心理測量者", "PSYCHO-PASS"),
        g("hyouka", "Hyouka", "Hyouka", "冰菓", "氷菓"),
        g("sound_euphonium", "Sound! Euphonium", "Sound! Euphonium", "吹响吧上低音号", "響け！ユーフォニアム"),
        g("a_silent_voice", "A Silent Voice", "A Silent Voice", "声之形", "聲之形", "聲の形"),
        g("chunibyo_series", "Love, Chunibyo & Other Delusions", "Love, Chunibyo & Other Delusions",
            "中二病也要谈恋爱", "中二病でも恋がしたい"),
        g("your_lie_in_april", "Your Lie in April", "Your Lie in April", "四月是你的谎言", "四月は君の嘘"),
        g("anohana", "Anohana", "Anohana", "未闻花名", "あの花"),
        g("sword_art_online", "Sword Art Online", "Sword Art Online", "刀剑神域", "ソードアート・オンライン", "SAO"),
        g("pokemon", "Pokémon", "Pokémon", "宝可梦", "寶可夢", "神奇宝贝", "ポケモン"),
        g("digimon", "Digimon", "Digimon", "数码宝贝", "數碼寶貝", "デジモン"),
        g("clannad_series", "CLANNAD", "CLANNAD", "小镇有你", "CLANNAD"),
        g("steins_gate", "Steins;Gate", "Steins;Gate", "命运石之门", "命運石之門", "シュタインズ・ゲート"),
        g("code_geass", "Code Geass", "Code Geass", "反叛的鲁路修", "コードギアス"),

        // ── Yayıncı / yapımcı şirketler ──
        g("kadokawa", "KADOKAWA", "KADOKAWA", "角川", "角川书店", "角川書店", "KADOKAWA"),
        g("shueisha", "Shueisha", "Shueisha", "集英社"),
        g("kodansha", "Kodansha", "Kodansha", "讲谈社", "講談社"),
        g("shogakukan", "Shogakukan", "Shogakukan", "小学馆", "小學館"),
        g("aniplex", "Aniplex", "Aniplex", "阿尼普", "アニプレックス"),
        g("bandai", "Bandai", "Bandai", "万代", "萬代", "バンダイ"),
        g("bandai_namco", "Bandai Namco", "Bandai Namco", "万代南梦宫", "バンダイナムコ"),
        g("toho", "Toho", "Toho", "东宝", "東宝", "東寶"),
        g("shochiku", "Shochiku", "Shochiku", "松竹"),
        g("lantis", "Lantis", "Lantis", "兰迪斯", "ランティス"),
        g("mappa", "MAPPA", "MAPPA", "マッパ", "MAPPA"),
        g("studio_bind", "Studio Bind", "Studio Bind", "スタジオバインド"),
        g("kinema_citrus", "Kinema Citrus", "Kinema Citrus", "キネマシトラス"),
        g("klockworx", "THE KLOCKWORX", "THE KLOCKWORX", "克洛克沃克斯", "クロックワークス"),

        // ── Bangumi resmî meta etiketleri: hedef kitle / sınıflandırma ──
        g("other", "Diğer", "Other", "其他", "其它", "そのほか"),
        g("male_audience", "Erkek Kitleye Yönelik", "Male Audience", "男性向", "男性向け"),
        g("female_audience", "Kadın Kitleye Yönelik", "Female Audience", "女性向け"),
        g("otome", "Otome", "Otome", "乙女", "乙女向", "乙女向け"),
        g("restricted", "Yetişkinlere Özel", "Restricted", "限制级", "限制級", "成人指定"),
        g("inspirational", "İlham Verici", "Inspirational", "励志", "勵志", "热血励志"),
        g("animation", "Animasyon", "Animation", "动画", "動畫", "アニメ", "Animation"),
        g("lgbt", "LGBT", "LGBT", "同性", "同性恋", "同性戀", "LGBT"),
        g("musical", "Müzikal", "Musical", "歌舞", "歌舞片", "ミュージカル", "Musical"),
        g("western_movie", "Kovboy Filmi", "Western Film", "西部", "西部片", "西部劇"),

        // ── Oyun türleri (Bangumi `game` meta etiketleri) ──
        g("game_adv", "Macera Oyunu (ADV)", "Adventure Game (ADV)", "ADV", "AVG", "冒险游戏", "冒險遊戲"),
        g("game_rpg", "Rol Yapma Oyunu (RPG)", "Role-Playing Game (RPG)", "RPG", "角色扮演游戏", "角色扮演遊戲"),
        g("game_slg", "Simülasyon Oyunu (SLG)", "Simulation Game (SLG)", "SLG", "模拟", "模擬", "模拟经营"),
        g("game_act", "Aksiyon Oyunu (ACT)", "Action Game (ACT)", "ACT", "动作游戏", "動作遊戲"),
        g("game_stg", "Nişancı Oyunu (STG)", "Shooter Game (STG)", "STG", "射击", "射擊", "射击游戏"),
        g("game_spg", "Spor Oyunu (SPG)", "Sports Game (SPG)", "SPG", "体育游戏", "體育遊戲"),
        g("game_ftg", "Dövüş Oyunu (FTG)", "Fighting Game (FTG)", "FTG", "格斗游戏", "格鬥遊戲"),
        g("game_rcg", "Yarış Oyunu (RCG)", "Racing Game (RCG)", "RCG", "竞速游戏", "賽車遊戲"),
        g("game_rts", "Gerçek Zamanlı Strateji (RTS)", "Real-Time Strategy (RTS)", "RTS", "即时战略", "即時戰略"),
        g("game_strategy", "Strateji", "Strategy", "策略", "策略游戏", "战棋", "戰棋"),
        g("game_mmo", "Çevrimiçi Oyun", "Online Game", "网络游戏", "網路遊戲", "MMO"),

        // ── Oyun platformları ──
        g("platform_pc", "PC", "PC", "PC", "Windows", "Steam", "电脑游戏"),
        g("platform_ps3", "PlayStation 3", "PlayStation 3", "PS3"),
        g("platform_ps4", "PlayStation 4", "PlayStation 4", "PS4"),
        g("platform_ps5", "PlayStation 5", "PlayStation 5", "PS5"),
        g("platform_psv", "PlayStation Vita", "PlayStation Vita", "PSV", "PS Vita"),
        g("platform_psp", "PSP", "PSP", "PSP"),
        g("platform_switch", "Nintendo Switch", "Nintendo Switch", "NS", "Switch", "Nintendo Switch"),
        g("platform_3ds", "Nintendo 3DS", "Nintendo 3DS", "3DS", "N3DS"),
        g("platform_xbox", "Xbox", "Xbox", "XBOX", "Xbox 360", "X360"),
        g("platform_xbox_one", "Xbox One", "Xbox One", "XboxOne", "XBOX ONE"),
        g("platform_ios", "iOS", "iOS", "iOS", "iPhone", "iPad"),
        g("platform_android", "Android", "Android", "Android", "安卓"),
        g("platform_arcade", "Atari Salonu", "Arcade", "街机", "街機", "アーケード"),
        g("platform_console", "Konsol", "Console", "主机", "主機", "家用机"),
        g("platform_handheld", "El Konsolu", "Handheld", "掌机", "掌機", "携帯機"),

        // ── Kitap / yayın meta etiketleri ──
        g("photobook", "Fotoğraf Albümü", "Photobook", "写真集", "寫真集"),
        g("book", "Kitap", "Book", "图书", "圖書", "书籍", "書籍"),
        g("magazine", "Dergi", "Magazine", "杂志", "雜誌", "ムック"),
        g("bunko", "Cep Kitabı", "Bunko", "文库", "文庫", "文库本"),

        // ── Müzik türleri (Bangumi `music` meta etiketleri) ──
        g("hip_hop", "Hip-Hop", "Hip-Hop", "Hip-hop", "HipHop", "嘻哈", "说唱"),
        g("rnb", "R&B", "R&B", "RnB", "节奏布鲁斯"),
        g("metal_music", "Metal", "Metal", "金属", "金屬", "重金属"),
        g("folk_music", "Folk", "Folk", "民谣", "民謠", "民族音乐"),
        g("ambient_music", "Ambient", "Ambient", "氛围音乐", "環境音樂"),
        g("instrumental", "Enstrümantal", "Instrumental", "器乐", "純音樂", "纯音乐")
    )

    /** Normalleştirilmiş kaynak yazımı → giriş. İlk yazım kazanır (aynı anlam, birden çok yazım). */
    private val byLabel: Map<String, Group> by lazy {
        val map = HashMap<String, Group>(groups.sumOf { it.labels.size } * 2)
        for (group in groups) {
            for (label in group.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        // Girişin Latin adı da bir eşleşme anahtarıdır: Bangumi'de aynı etiket hem Çince hem
        // Latin yazımıyla bulunabiliyor (`京阿尼` + `Kyoto Animation`) — tek çipte buluşsunlar.
        // Açık yazımlar önce işlendiği için bir ad asla gerçek bir etiketi gölgeleyemez.
        for (group in groups) {
            for (name in listOf(group.entry.english, group.entry.turkish)) {
                val key = normalize(name)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /**
     * Noktalama/boşluk duyarsız ikinci tur eşleşme anahtarı → giriş.
     * `Production I.G`, `Production.IG` ve `ProductionI.G` aynı çipte toplansın diye.
     */
    private val byCompact: Map<String, Group> by lazy {
        val map = HashMap<String, Group>(groups.sumOf { it.labels.size } * 2)
        for (group in groups) {
            for (label in group.labels) {
                val key = compact(label)
                if (key.length < 3) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        for (group in groups) {
            for (name in listOf(group.entry.english, group.entry.turkish)) {
                val key = compact(name)
                if (key.length < 3) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /** Tablo eşleşmesi; yıl/ay kalıpları dahil. Bilinmeyen etiket için `null`. */
    fun entryFor(label: String?): BangumiTagEntry? {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return null
        byLabel[normalize(raw)]?.let { return it.entry }
        patternEntry(raw)?.let { return it }
        val compact = compact(raw)
        if (compact.length >= 3) byCompact[compact]?.let { return it.entry }
        return null
    }

    /** Sözlükteki tüm Çince/Japonca yazımlar (test ve araç kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    // ── Yardımcılar ────────────────────────────────────────────────────────────

    /**
     * Karşılaştırma anahtarı. Boşluk, büyük/küçük harf, tam genişlikli noktalama ve
     * `「」『』《》（）()[]` sarımları yok sayılır.
     */
    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '　' -> sb.append(' ')
                '（', '(', '[', '【', '《', '「', '『' -> Unit
                '）', ')', ']', '】', '》', '」', '』' -> Unit
                '：', ':', '・', '·', '。', '、', '，', ',' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    /** Yalnızca harf/rakam bırakan sıkı anahtar: `Production.IG` → `productionig`. */
    private fun compact(value: String): String {
        if (value.isBlank()) return ""
        val sb = StringBuilder(value.length)
        for (ch in value) if (ch.isLetterOrDigit()) sb.append(ch)
        return sb.toString().lowercase(Locale.ROOT)
    }

    private val YEAR_MONTH = Regex("^(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月?$")
    private val YEAR_ONLY = Regex("^(\\d{4})\\s*年?$")
    private val DECADE = Regex("^(\\d{3})0\\s*(?:年代|年間)$")
    private val MONTH_ONLY = Regex("^(\\d{1,2})\\s*月$")

    private val monthNamesTurkish = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    private val monthNamesEnglish = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /** `2008年10月`, `2008年`, `2000年代`, `10月` gibi takvim etiketlerini çevirir. */
    private fun patternEntry(raw: String): BangumiTagEntry? {
        YEAR_MONTH.find(raw)?.let { match ->
            val year = match.groupValues[1]
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            if (month in 1..12) {
                return BangumiTagEntry(
                    "date_${year}_$month",
                    "${monthNamesTurkish[month - 1]} $year",
                    "${monthNamesEnglish[month - 1]} $year"
                )
            }
        }
        YEAR_ONLY.find(raw)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            if (year in 1900..2100) {
                return BangumiTagEntry("year_$year", year.toString(), year.toString())
            }
        }
        DECADE.find(raw)?.let { match ->
            val decade = match.groupValues[1].toIntOrNull()?.times(10) ?: return@let
            if (decade in 1900..2100) {
                return BangumiTagEntry("decade_$decade", "${decade}'ler", "${decade}s")
            }
        }
        MONTH_ONLY.find(raw)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            if (month in 1..12) {
                return BangumiTagEntry(
                    "month_$month",
                    monthNamesTurkish[month - 1],
                    monthNamesEnglish[month - 1]
                )
            }
        }
        return null
    }
}

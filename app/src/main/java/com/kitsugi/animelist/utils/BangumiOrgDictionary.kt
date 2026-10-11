package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Bangumi (bgm.tv) **kurum adı sözlüğü**: stüdyo, yapımcı, yayıncı ağ, yayımcı ve dergi.
 *
 * Bangumi infobox'ındaki `动画制作` / `製作` / `播放电视台` / `出版社` / `连载杂志` alanları
 * Japonca ya da Çince ham ad döndürür (`京都アニメーション`, `アニプレックス`, `集英社` ...).
 * Bu sözlük o adları arayüz diline (Türkçe / İngilizce) taşır.
 *
 * Kurallar ([BangumiTagDictionary] ile aynı):
 *  • **Uydurma çeviri yok.** Marka adlarında Türkçe değer İngilizce değerle aynıdır.
 *  • Bilinmeyen kurum adı olduğu gibi döner; üretim komiteleri (`PROJECT-XXX`,
 *    `光坂高校演劇部`) sözlüğe alınmaz.
 *  • Eşleştirme normalize edilerek yapılır: boşluk, büyük/küçük harf, tam genişlikli
 *    noktalama, `「」《》（）` sarımları ve `株式会社` gibi unvan ekleri yok sayılır.
 *  • Bu tablo **yalnızca gösterim katmanı** içindir. Arama, kimlik doğrulama ve
 *    tekilleştirme daima Latin adla ([String.toLatinStudioName]) yürür; çünkü
 *    `StudioDetailViewModel.matchesExpectedStudio` ham adla karşılaştırır.
 *
 * ÜRETİCİ YÖNETİMİNDE: bu dosyayı elle düzenlemeyin. `diag/bangumi_master/orgs.tsv`
 * güncellenip `python3 diag/bangumi_master/gen_bangumi_master.py` çalıştırılmalıdır.
 */
internal enum class BangumiOrgRole {
    STUDIO, PRODUCER, NETWORK, PUBLISHER, MAGAZINE
}

internal data class BangumiOrgEntry(
    /** `bangumi_org_<key>` dil dosyası anahtarı. */
    val key: String,
    val role: BangumiOrgRole,
    val turkish: String,
    val english: String
)

internal object BangumiOrgDictionary {

    private class Org(val entry: BangumiOrgEntry, val labels: List<String>)

    private fun o(
        key: String,
        role: BangumiOrgRole,
        turkish: String,
        english: String,
        vararg labels: String
    ) = Org(BangumiOrgEntry(key, role, turkish, english), labels.toList())

    // ── Tablo (üreteç tarafından yazılır) ────────────────────────────────────

    private val orgs: List<Org> = listOf(
        o("_8bit", BangumiOrgRole.STUDIO, "8bit", "8bit", "エイトビット"),
        o("a_1_pictures", BangumiOrgRole.STUDIO, "A-1 Pictures", "A-1 Pictures", "A-1 Pictures", "A-1", "A1社"),
        o("artland", BangumiOrgRole.STUDIO, "Artland", "Artland", "アートランド"),
        o("asahi_production", BangumiOrgRole.STUDIO, "Asahi Production", "Asahi Production", "亜細亜堂"),
        o("ashi_productions", BangumiOrgRole.STUDIO, "Ashi Productions", "Ashi Productions", "葦プロダクション"),
        o("bones", BangumiOrgRole.STUDIO, "BONES", "BONES", "ボンズ", "骨头社"),
        o("brain_s_base", BangumiOrgRole.STUDIO, "Brain's Base", "Brain's Base", "ブレインズ・ベース"),
        o("cj_enm", BangumiOrgRole.STUDIO, "CJ ENM", "CJ ENM", "CJ ENM"),
        o("cloverworks", BangumiOrgRole.STUDIO, "CloverWorks", "CloverWorks", "クローバーワークス"),
        o("david_production", BangumiOrgRole.STUDIO, "David Production", "David Production", "ダビプロ"),
        o("dc_comics", BangumiOrgRole.STUDIO, "DC Comics", "DC Comics", "DC漫画"),
        o("diomedea", BangumiOrgRole.STUDIO, "Diomedéa", "Diomedéa", "ディオメディア"),
        o("disney", BangumiOrgRole.STUDIO, "Disney", "Disney", "迪士尼", "迪斯尼"),
        o("doga_kobo", BangumiOrgRole.STUDIO, "Doga Kobo", "Doga Kobo", "動画工房"),
        o("dreamworks", BangumiOrgRole.STUDIO, "DreamWorks", "DreamWorks", "梦工厂"),
        o("dreamworks_animation", BangumiOrgRole.STUDIO, "DreamWorks Animation", "DreamWorks Animation", "梦工厂动画"),
        o("feel", BangumiOrgRole.STUDIO, "feel.", "feel.", "feel.", "FEEL"),
        o("gallop", BangumiOrgRole.STUDIO, "Gallop", "Gallop", "ぎゃろっぷ"),
        o("gonzo", BangumiOrgRole.STUDIO, "GONZO", "GONZO", "ゴンゾ"),
        o("j_c_staff", BangumiOrgRole.STUDIO, "J.C.Staff", "J.C.Staff", "J.C.STAFF"),
        o("khara", BangumiOrgRole.STUDIO, "khara", "khara", "カラー"),
        o("kinema_citrus", BangumiOrgRole.STUDIO, "KINEMA CITRUS", "KINEMA CITRUS", "キネマシトラス"),
        o("kyoto_animation", BangumiOrgRole.STUDIO, "Kyoto Animation", "Kyoto Animation", "京都アニメーション", "京都动画", "京都動画", "京阿尼"),
        o("lerche", BangumiOrgRole.STUDIO, "Lerche", "Lerche", "ラルケ"),
        o("lidenfilms", BangumiOrgRole.STUDIO, "LIDENFILMS", "LIDENFILMS", "ライデンフィルム"),
        o("madhouse", BangumiOrgRole.STUDIO, "Madhouse", "Madhouse", "マッドハウス", "疯房子"),
        o("marvel", BangumiOrgRole.STUDIO, "Marvel", "Marvel", "漫威"),
        o("mushi_production", BangumiOrgRole.STUDIO, "Mushi Production", "Mushi Production", "虫プロダクション"),
        o("nippon_animation", BangumiOrgRole.STUDIO, "Nippon Animation", "Nippon Animation", "日本アニメーション"),
        o("olm", BangumiOrgRole.STUDIO, "OLM", "OLM", "オー・エル・エム"),
        o("p_a_works", BangumiOrgRole.STUDIO, "P.A.Works", "P.A.Works", "ピーエーワークス"),
        o("passione", BangumiOrgRole.STUDIO, "Passione", "Passione", "パッショーネ"),
        o("pixar", BangumiOrgRole.STUDIO, "Pixar", "Pixar", "皮克斯", "皮克斯动画工作室"),
        o("production_i_g", BangumiOrgRole.STUDIO, "Production I.G", "Production I.G", "プロダクション・アイジー"),
        o("production_reed", BangumiOrgRole.STUDIO, "Production Reed", "Production Reed", "プロダクションリード"),
        o("shaft", BangumiOrgRole.STUDIO, "Shaft", "Shaft", "シャフト"),
        o("shin_ei_animation", BangumiOrgRole.STUDIO, "Shin-Ei Animation", "Shin-Ei Animation", "シンエイ動画"),
        o("silver_link", BangumiOrgRole.STUDIO, "Silver Link", "Silver Link", "シルバーリンク"),
        o("studio_deen", BangumiOrgRole.STUDIO, "Studio Deen", "Studio Deen", "スタジオディーン", "ディーン"),
        o("studio_ghibli", BangumiOrgRole.STUDIO, "Studio Ghibli", "Studio Ghibli", "ジブリ", "スタジオジブリ", "吉卜力", "吉卜力工作室"),
        o("studio_pierrot", BangumiOrgRole.STUDIO, "Studio Pierrot", "Studio Pierrot", "ぴえろ", "小丑社"),
        o("studio_voln", BangumiOrgRole.STUDIO, "Studio VOLN", "Studio VOLN", "スタジオヴォルン"),
        o("sunrise", BangumiOrgRole.STUDIO, "Sunrise", "Sunrise", "サンライズ", "日升动画", "日昇"),
        o("tatsunoko_production", BangumiOrgRole.STUDIO, "Tatsunoko Production", "Tatsunoko Production", "タツノコプロ"),
        o("telecom_animation_film", BangumiOrgRole.STUDIO, "Telecom Animation Film", "Telecom Animation Film", "テレコム・アニメーションフィルム"),
        o("tezuka_productions", BangumiOrgRole.STUDIO, "Tezuka Productions", "Tezuka Productions", "手塚プロダクション"),
        o("tms_entertainment", BangumiOrgRole.STUDIO, "TMS Entertainment", "TMS Entertainment", "東京ムービー", "トムス・エンタテインメント"),
        o("toei_animation", BangumiOrgRole.STUDIO, "Toei Animation", "Toei Animation", "東映アニメーション", "东映动画"),
        o("triangle_staff", BangumiOrgRole.STUDIO, "Triangle Staff", "Triangle Staff", "トライアングルスタッフ"),
        o("trigger", BangumiOrgRole.STUDIO, "TRIGGER", "TRIGGER", "トリガー", "扳机社"),
        o("troyca", BangumiOrgRole.STUDIO, "TROYCA", "TROYCA", "トロイカ"),
        o("ufotable", BangumiOrgRole.STUDIO, "ufotable", "ufotable", "幽浮社", "飞碟社"),
        o("white_fox", BangumiOrgRole.STUDIO, "WHITE FOX", "WHITE FOX", "白狐社"),
        o("wit_studio", BangumiOrgRole.STUDIO, "WIT STUDIO", "WIT STUDIO", "ウィットスタジオ", "霸权社"),
        o("xebec", BangumiOrgRole.STUDIO, "XEBEC", "XEBEC", "ゼベック"),
        o("aniplex", BangumiOrgRole.PRODUCER, "Aniplex", "Aniplex", "アニプレックス"),
        o("bandai_namco_pictures", BangumiOrgRole.PRODUCER, "Bandai Namco Pictures", "Bandai Namco Pictures", "バンダイナムコピクチャーズ"),
        o("bandai_visual", BangumiOrgRole.PRODUCER, "Bandai Visual", "Bandai Visual", "バンダイビジュアル"),
        o("flyingdog", BangumiOrgRole.PRODUCER, "FlyingDog", "FlyingDog", "フライングドッグ"),
        o("geneon", BangumiOrgRole.PRODUCER, "Geneon", "Geneon", "ジェネオン"),
        o("geneon_entertainment", BangumiOrgRole.PRODUCER, "Geneon Entertainment", "Geneon Entertainment", "ジェネオンエンタテインメント"),
        o("kadokawa_group", BangumiOrgRole.PRODUCER, "Kadokawa Group", "Kadokawa Group", "角川グループ"),
        o("king_records", BangumiOrgRole.PRODUCER, "King Records", "King Records", "キングレコード"),
        o("lantis", BangumiOrgRole.PRODUCER, "Lantis", "Lantis", "ランティス"),
        o("movic", BangumiOrgRole.PRODUCER, "Movic", "Movic", "ムービック"),
        o("nippon_columbia", BangumiOrgRole.PRODUCER, "Nippon Columbia", "Nippon Columbia", "日本コロムビア"),
        o("pony_canyon", BangumiOrgRole.PRODUCER, "Pony Canyon", "Pony Canyon", "ポニーキャニオン"),
        o("shochiku", BangumiOrgRole.PRODUCER, "Shochiku", "Shochiku", "松竹"),
        o("sotsu", BangumiOrgRole.PRODUCER, "Sotsu", "Sotsu", "創通"),
        o("starchild_records", BangumiOrgRole.PRODUCER, "Starchild Records", "Starchild Records", "スターチャイルドレコード"),
        o("toho", BangumiOrgRole.PRODUCER, "Toho", "Toho", "東宝", "東宝映像事業部"),
        o("warner_bros", BangumiOrgRole.PRODUCER, "Warner Bros.", "Warner Bros.", "ワーナー・ブラザース"),
        o("asahi_broadcasting", BangumiOrgRole.NETWORK, "Asahi Broadcasting", "Asahi Broadcasting", "朝日放送"),
        o("fuji_tv", BangumiOrgRole.NETWORK, "Fuji TV", "Fuji TV", "フジテレビジョン", "フジテレビ"),
        o("mainichi_broadcasting", BangumiOrgRole.NETWORK, "Mainichi Broadcasting", "Mainichi Broadcasting", "毎日放送"),
        o("nippon_tv", BangumiOrgRole.NETWORK, "Nippon TV", "Nippon TV", "日本テレビ"),
        o("tbs", BangumiOrgRole.NETWORK, "TBS", "TBS", "TBSテレビ"),
        o("tv_tokyo", BangumiOrgRole.NETWORK, "TV Tokyo", "TV Tokyo", "テレビ東京"),
        o("akita_shoten", BangumiOrgRole.PUBLISHER, "Akita Shoten", "Akita Shoten", "秋田書店"),
        o("dengeki_bunko", BangumiOrgRole.PUBLISHER, "Dengeki Bunko", "Dengeki Bunko", "電撃文庫"),
        o("futabasha", BangumiOrgRole.PUBLISHER, "Futabasha", "Futabasha", "双葉社"),
        o("hakusensha", BangumiOrgRole.PUBLISHER, "Hakusensha", "Hakusensha", "白泉社"),
        o("houbunsha", BangumiOrgRole.PUBLISHER, "Houbunsha", "Houbunsha", "芳文社"),
        o("kadokawa_media_factory", BangumiOrgRole.PUBLISHER, "KADOKAWA Media Factory", "KADOKAWA Media Factory", "KADOKAWA メディアファクトリー"),
        o("kadokawa_shoten", BangumiOrgRole.PUBLISHER, "Kadokawa Shoten", "Kadokawa Shoten", "角川書店"),
        o("kadokawa_tsubasa_bunko", BangumiOrgRole.PUBLISHER, "Kadokawa Tsubasa Bunko", "Kadokawa Tsubasa Bunko", "角川つばさ文庫"),
        o("kodansha", BangumiOrgRole.PUBLISHER, "Kodansha", "Kodansha", "講談社"),
        o("media_factory", BangumiOrgRole.PUBLISHER, "Media Factory", "Media Factory", "メディアファクトリー"),
        o("shinchosha", BangumiOrgRole.PUBLISHER, "Shinchosha", "Shinchosha", "新潮社"),
        o("shogakukan", BangumiOrgRole.PUBLISHER, "Shogakukan", "Shogakukan", "小学館"),
        o("shueisha", BangumiOrgRole.PUBLISHER, "Shueisha", "Shueisha", "集英社"),
        o("square_enix", BangumiOrgRole.PUBLISHER, "Square Enix", "Square Enix", "スクウェア・エニックス"),
        o("mappa", BangumiOrgRole.STUDIO, "MAPPA", "MAPPA", "株式会社マッパ", "マッパ", "MAPPA"),
        o("studio_bind", BangumiOrgRole.STUDIO, "Studio Bind", "Studio Bind", "スタジオバインド"),
        o("nexus", BangumiOrgRole.STUDIO, "Nexus", "Nexus", "ネクサス"),
        o("project_no_9", BangumiOrgRole.STUDIO, "Project No.9", "Project No.9", "project No.9", "プロジェクトナンバーナイン"),
        o("engi", BangumiOrgRole.STUDIO, "ENGI", "ENGI", "エンギ"),
        o("voil", BangumiOrgRole.STUDIO, "Voil", "Voil", "ヴォイル"),
        o("hal_film_maker", BangumiOrgRole.STUDIO, "Hal Film Maker", "Hal Film Maker", "ハルフィルムメーカー"),
        o("studio_3hz", BangumiOrgRole.STUDIO, "Studio 3Hz", "Studio 3Hz", "スタジオ3Hz"),
        o("nut", BangumiOrgRole.STUDIO, "NUT", "NUT", "ナット"),
        o("revoroot", BangumiOrgRole.STUDIO, "Revoroot", "Revoroot", "レヴォルート"),
        o("okuruto_noboru", BangumiOrgRole.STUDIO, "Okuruto Noboru", "Okuruto Noboru", "オクルトノボル"),
        o("bibury_animation", BangumiOrgRole.STUDIO, "Bibury Animation Studios", "Bibury Animation Studios", "バイブリーアニメーションスタジオ"),
        o("studio_comet", BangumiOrgRole.STUDIO, "Studio Comet", "Studio Comet", "スタジオコメット"),
        o("maho_film", BangumiOrgRole.STUDIO, "Maho Film", "Maho Film", "旭プロダクション", "マッハフィルム"),
        o("nhk", BangumiOrgRole.NETWORK, "NHK", "NHK", "NHK", "日本放送協会"),
        o("animax", BangumiOrgRole.NETWORK, "Animax", "Animax", "アニマックス"),
        o("bs_nippon", BangumiOrgRole.NETWORK, "BS Nippon TV", "BS Nippon TV", "BS日テレ"),
        o("bs_tbs", BangumiOrgRole.NETWORK, "BS-TBS", "BS-TBS", "BS-TBS"),
        o("bs_fuji", BangumiOrgRole.NETWORK, "BS Fuji", "BS Fuji", "BSフジ"),
        o("at_x", BangumiOrgRole.NETWORK, "AT-X", "AT-X", "AT-X"),
        o("tokyo_mx", BangumiOrgRole.NETWORK, "TOKYO MX", "TOKYO MX", "東京メトロポリタンテレビジョン", "TOKYO MX"),
        o("avex_pictures", BangumiOrgRole.PRODUCER, "Avex Pictures", "Avex Pictures", "エイベックス・ピクチャーズ"),
        o("dmm_pictures", BangumiOrgRole.PRODUCER, "DMM Pictures", "DMM Pictures", "DMM pictures"),
        o("king_amusement", BangumiOrgRole.PRODUCER, "KING AMUSEMENT CREATIVE", "KING AMUSEMENT CREATIVE", "KING AMUSEMENT CREATIVE")
    )

    // ── Eşleştirme ───────────────────────────────────────────────────────────

    private val byLabel: Map<String, Org> by lazy {
        val map = HashMap<String, Org>(orgs.sumOf { it.labels.size } * 2)
        for (org in orgs) {
            for (label in org.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                // Açık yazımlar önce işlendiği için bir takma ad asla gerçek adı gölgelemez.
                if (!map.containsKey(key)) map[key] = org
            }
        }
        // Kurumun Latin/İngilizce adı da eşleşme anahtarıdır: Bangumi'de aynı kurum hem
        // Çince hem Latin yazımıyla bulunabiliyor (`京阿尼` + `Kyoto Animation`).
        for (org in orgs) {
            for (name in listOf(org.entry.english, org.entry.turkish)) {
                val key = normalize(name)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = org
            }
        }
        map
    }

    /** Kurum adının sözlük girişi; bilinmiyorsa null. */
    fun entryFor(name: String?): BangumiOrgEntry? {
        val raw = name?.trim().orEmpty()
        if (raw.isEmpty()) return null
        byLabel[normalize(raw)]?.let { return it.entry }
        val stripped = stripCorporateSuffix(raw)
        if (stripped.isNotEmpty() && stripped != raw) {
            byLabel[normalize(stripped)]?.let { return it.entry }
        }
        return null
    }

    /** Sözlükteki tüm yazımlar (test ve araç kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    /** Sözlükteki tüm kayıtlar (test ve araç kullanımı için). */
    fun allEntries(): List<BangumiOrgEntry> = orgs.map { it.entry }

    private val CORPORATE_PREFIX = Regex("^(合同会社|株式会社|有限会社|有限公司|股份有限公司)\s*")
    private val CORPORATE_SUFFIX = Regex("\s*(合同会社|株式会社|有限会社|有限公司|股份有限公司)$")

    /** `株式会社集英社` → `集英社`: unvan ekleri lookup için yok sayılır. */
    private fun stripCorporateSuffix(value: String): String {
        var text = value.replace(Regex("【[^】]*】"), " ")
        text = text.replace(Regex("""\([^)]*\)|（[^）]*）"""), " ")
        text = text.replace(CORPORATE_PREFIX, "")
        text = text.replace(CORPORATE_SUFFIX, "")
        return text.replace(Regex("""\s+"""), " ").trim()
    }

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
            .replace(Regex("\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }
}

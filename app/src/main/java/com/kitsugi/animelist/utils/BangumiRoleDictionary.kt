package com.kitsugi.animelist.utils

import java.util.Locale

/**
 * Bangumi (bgm.tv) **kadro ve karakter rolü sözlüğü**.
 *
 * Bangumi'nin v0 liste uçları (`/v0/persons/{id}/subjects`, `/v0/characters/{id}/subjects`,
 * `/v0/subjects/{id}/persons`) rol/görev alanlarını Çince ya da Japonca ham metin olarak
 * döndürür: `主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督`, `脚本` ... Bu sözlük o
 * değerleri uygulamanın arayüz diline (Türkçe / İngilizce) çevirir.
 *
 * Kurallar ([BangumiTagDictionary] ile aynı):
 *  • **Uydurma çeviri yok.** Bilinmeyen rol olduğu gibi döner.
 *  • Aynı anlamın basit/geleneksel Çince ve Japonca yazımları AYNI girişte toplanır
 *    (`监督` + `監督`, `音乐` + `音楽`).
 *  • Eşleştirme normalize edilerek yapılır: boşluk, büyük/küçük harf ve tam genişlikli
 *    sarımlar (`「」《》（）【】`) yok sayılır.
 *  • Bir girişin `key` alanı `bangumi_role_<key>` dil dosyası kaynağıyla eşleşir;
 *    kaynak varsa (yalnızca tr/en arayüzünde) o tercih edilir, yoksa bu tablo kullanılır.
 *
 * ÜRETİCİ YÖNETİMİNDE: bu dosyayı elle düzenlemeyin. `diag/bangumi_master/roles.tsv`
 * güncellenip `python3 diag/bangumi_master/gen_bangumi_master.py` çalıştırılmalıdır.
 */
internal data class BangumiRoleEntry(
    /** `bangumi_role_<key>` dil dosyası anahtarı. */
    val key: String,
    val turkish: String,
    val english: String
)

internal object BangumiRoleDictionary {

    private class Group(val entry: BangumiRoleEntry, val labels: List<String>)

    private fun g(key: String, turkish: String, english: String, vararg labels: String) =
        Group(BangumiRoleEntry(key, turkish, english), labels.toList())

    // ── Tablo (üreteç tarafından yazılır) ────────────────────────────────────

    private val groups: List<Group> = listOf(
        g("main_character", "Ana Karakter", "Main Character", "主角", "主演", "主役", "Lead"),
        g("supporting_character", "Yardımcı Karakter", "Supporting Character", "配角", "助演"),
        g("guest_character", "Konuk Karakter", "Guest Character", "客串", "ゲスト"),
        g("voice_actor", "Seslendirme Sanatçısı", "Voice Actor", "声优", "聲優", "声優", "CV"),
        g("voice_acting", "Seslendirme", "Voice Acting", "配音", "吹き替え", "配音演员"),
        g("theme_song_performance", "Tema Şarkısı Performansı", "Theme Song Performance", "主題歌演出", "主题歌演出"),
        g("theme_song", "Tema Şarkısı", "Theme Song", "主題歌", "主题歌"),
        g("theme_song_composition", "Tema Şarkısı Bestesi", "Theme Song Composition", "主題歌作曲", "主题歌作曲"),
        g("theme_song_lyrics", "Tema Şarkısı Söz Yazarı", "Theme Song Lyrics", "主題歌作詞", "主题歌作词"),
        g("music", "Müzik", "Music", "音乐", "音楽"),
        g("original_creator", "Orijinal Yaratıcı", "Original Creator", "原作"),
        g("director", "Yönetmen", "Director", "导演", "監督"),
        g("chief_director", "Baş Yönetmen", "Chief Director", "总导演", "総監督"),
        g("script", "Senarist", "Script", "脚本", "剧本"),
        g("series_composition", "Seri Düzenlemesi", "Series Composition", "系列构成", "系列構成", "シリーズ構成"),
        g("episode_director", "Bölüm Yönetmeni", "Episode Director", "演出"),
        g("supervisor", "Süpervizör", "Supervisor", "监修", "監修"),
        g("planning", "Planlama", "Planning", "企划", "企画"),
        g("animation_director", "Animasyon Yönetmeni", "Animation Director", "作画监督", "作画監督"),
        g("chief_animation_director", "Baş Animasyon Yönetmeni", "Chief Animation Director", "总作画监督", "総作画監督"),
        g("character_design", "Karakter Tasarımı", "Character Design", "人物设定", "人物設定", "キャラクターデザイン"),
        g("original_character_design", "Orijinal Karakter Tasarımı", "Original Character Design", "人物原案"),
        g("mechanical_design", "Mekanik Tasarım", "Mechanical Design", "机械设计", "機械設計", "メカニカルデザイン"),
        g("color_design", "Renk Tasarımı", "Color Design", "色彩设计", "色彩設計"),
        g("art_director", "Sanat Yönetmeni", "Art Director", "美术", "美術", "美术监督", "美術監督"),
        g("director_of_photography", "Görüntü Yönetmeni", "Director of Photography", "摄影", "撮影"),
        g("sound_director", "Ses Yönetmeni", "Sound Director", "音响监督", "音響監督"),
        g("key_animation", "Anahtar Animasyon", "Key Animation", "原画"),
        g("editing", "Kurgu", "Editing", "剪辑", "編集"),
        g("background_art", "Arka Plan Sanatı", "Background Art", "背景", "背景美术", "背景美術"),
        g("special_effects", "Özel Efektler", "Special Effects", "特效", "特殊効果"),
        g("recording", "Kayıt", "Recording", "录音", "録音"),
        g("3d_cg", "3D CG", "3D CG", "3DCG"),
        g("animation_production", "Animasyon Yapımı", "Animation Production", "动画制作", "アニメーション制作", "アニメーション製作"),
        g("producer", "Yapımcı", "Producer", "制作人", "制片人", "プロデューサー"),
        g("production", "Yapım", "Production", "出品", "製作", "制作"),
        g("publisher", "Yayıncı", "Publisher", "发行", "發行", "配給")
    )

    // ── Eşleştirme ───────────────────────────────────────────────────────────

    private val byLabel: Map<String, Group> by lazy {
        val map = HashMap<String, Group>(groups.sumOf { it.labels.size } * 2)
        for (group in groups) {
            for (label in group.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                // Açık yazımlar önce işlendiği için bir kısaltma gerçek rolü gölgeleyemez.
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /** Rol yazımının sözlük girişi; bilinmiyorsa null. */
    fun entryFor(label: String?): BangumiRoleEntry? {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return byLabel[normalize(raw)]?.entry
    }

    /** Sözlükteki tüm yazımlar (test ve araç kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    /** Sözlükteki tüm girişler (test ve araç kullanımı için). */
    fun allEntries(): List<BangumiRoleEntry> = groups.map { it.entry }

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
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }
}

package com.kitsugi.animelist.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Bangumi (bgm.tv) **kadro ve karakter rolü sözlüğü**.
 *
 * Bangumi'nin v0 liste uçları (`/v0/persons/{id}/subjects`, `/v0/characters/{id}/subjects`,
 * `/v0/subjects/{id}/persons`) rol/görev alanlarını Çince ya da Japonca ham metin olarak
 * döndürür: `主角`, `配角`, `客串`, `主題歌演出`, `原作`, `监督`, `脚本` ... Bu sözlük o
 * değerleri uygulamanın arayüz diline (Türkçe / İngilizce) çevirir; böylece İngilizce
 * arayüzde Çince/Japonca rol etiketi görünmez.
 *
 * Kurallar ([BangumiTagDictionary] ile aynı):
 *  • **Uydurma çeviri yok.** Bilinmeyen rol olduğu gibi döner.
 *  • Aynı anlamın basit/geleneksel Çince ve Japonca yazımları AYNI girişte toplanır
 *    (`监督` + `監督`, `音乐` + `音楽`).
 *  • Eşleştirme normalize edilerek yapılır: boşluk, büyük/küçük harf ve tam genişlikli
 *    sarımlar (`「」《》（）【】`) yok sayılır.
 */
internal data class BangumiRoleEntry(
    val turkish: String,
    val english: String
)

internal object BangumiRoleDictionary {

    private class Group(val entry: BangumiRoleEntry, val labels: List<String>)

    private fun g(turkish: String, english: String, vararg labels: String) =
        Group(BangumiRoleEntry(turkish, english), labels.toList())

    // ── Tablo ─────────────────────────────────────────────────────────────────

    private val groups: List<Group> = listOf(
        // ── Karakter rolleri (v0 `relation` / `staff` alanları) ──
        g("Ana Karakter", "Main Character", "主角", "主演", "主役", "Lead"),
        g("Yardımcı Karakter", "Supporting Character", "配角", "助演"),
        g("Konuk Karakter", "Guest Character", "客串", "ゲスト"),
        g("Seslendirme Sanatçısı", "Voice Actor", "声优", "聲優", "声優", "CV"),
        g("Seslendirme", "Voice Acting", "配音", "吹き替え", "配音演员"),

        // ── Müzik / tema ──
        g("Tema Şarkısı Performansı", "Theme Song Performance", "主題歌演出", "主题歌演出"),
        g("Tema Şarkısı", "Theme Song", "主題歌", "主题歌"),
        g("Tema Şarkısı Bestesi", "Theme Song Composition", "主題歌作曲", "主题歌作曲"),
        g("Tema Şarkısı Söz Yazarı", "Theme Song Lyrics", "主題歌作詞", "主题歌作词"),
        g("Müzik", "Music", "音乐", "音楽"),

        // ── Yönetim / senaryo ──
        g("Orijinal Yaratıcı", "Original Creator", "原作"),
        g("Yönetmen", "Director", "导演", "監督"),
        g("Baş Yönetmen", "Chief Director", "总导演", "総監督"),
        g("Senarist", "Script", "脚本", "剧本"),
        g("Seri Düzenlemesi", "Series Composition", "系列构成", "系列構成", "シリーズ構成"),
        g("Bölüm Yönetmeni", "Episode Director", "演出"),
        g("Süpervizör", "Supervisor", "监修", "監修"),
        g("Planlama", "Planning", "企划", "企画"),

        // ── Animasyon kadrosu ──
        g("Animasyon Yönetmeni", "Animation Director", "作画监督", "作画監督"),
        g("Baş Animasyon Yönetmeni", "Chief Animation Director", "总作画监督", "総作画監督"),
        g("Karakter Tasarımı", "Character Design", "人物设定", "人物設定", "キャラクターデザイン"),
        g("Orijinal Karakter Tasarımı", "Original Character Design", "人物原案"),
        g("Mekanik Tasarım", "Mechanical Design", "机械设计", "機械設計", "メカニカルデザイン"),
        g("Renk Tasarımı", "Color Design", "色彩设计", "色彩設計"),
        g("Sanat Yönetmeni", "Art Director", "美术", "美術", "美术监督", "美術監督"),
        g("Görüntü Yönetmeni", "Director of Photography", "摄影", "撮影"),
        g("Ses Yönetmeni", "Sound Director", "音响监督", "音響監督"),
        g("Anahtar Animasyon", "Key Animation", "原画"),
        g("Kurgu", "Editing", "剪辑", "編集"),
        g("Arka Plan Sanatı", "Background Art", "背景", "背景美术", "背景美術"),
        g("Özel Efektler", "Special Effects", "特效", "特殊効果"),
        g("Kayıt", "Recording", "录音", "録音"),
        g("3D CG", "3D CG", "3DCG"),

        // ── Yapım / yayıncı ──
        g("Animasyon Yapımı", "Animation Production", "动画制作", "アニメーション制作", "アニメーション製作"),
        g("Yapımcı", "Producer", "制作人", "制片人", "プロデューサー"),
        g("Yapım", "Production", "出品", "製作", "制作"),
        g("Yayıncı", "Publisher", "发行", "發行", "配給")
    )

    private val byLabel: Map<String, Group> by lazy {
        val map = HashMap<String, Group>()
        for (group in groups) {
            for (label in group.labels) {
                val key = normalize(label)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = group
            }
            // Latin yazımlar da anahtardır: "Main Character" → aynı giriş.
            for (name in listOf(group.entry.english, group.entry.turkish)) {
                val key = normalize(name)
                if (key.isEmpty()) continue
                if (!map.containsKey(key)) map[key] = group
            }
        }
        map
    }

    /** Rol etiketinin sözlük girişi; bilinmiyorsa null (uydurma çeviri yok). */
    fun entryFor(label: String?): BangumiRoleEntry? {
        val key = normalize(label.orEmpty())
        if (key.isEmpty()) return null
        return byLabel[key]?.entry
    }

    /** Sözlükteki tüm Çince/Japonca yazımlar (test kullanımı için). */
    fun knownLabels(): Set<String> = byLabel.keys.toSet()

    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            when {
                ch.isWhitespace() -> Unit
                ch in "「」《》（）()[]【】・·,，;；" -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

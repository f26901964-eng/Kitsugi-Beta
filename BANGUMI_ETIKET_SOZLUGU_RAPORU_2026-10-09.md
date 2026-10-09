# Bangumi Etiket Sözlüğü Genişletme Raporu — v2.4.226 (2026-10-09)

## Sorun

Bangumi kaynaklı detay sayfalarında **Etiketler** bölümü yarım çeviriliydi:

* *Suzumiya Haruhi no Shoushitsu*: `凉宫春日`, `大萌神`, `凉宫春日的消失`, `轻小说改`, `长门有希`,
  `谷川流`, `石原立也`, `囧虚`, `武本康弘`, `轻改`, `凉宫春日的忧郁`, `剧场`, `消失` çevrilmeden kalıyordu.
* *Ghost in the Shell S.A.C. 2nd GIG*: `菅野洋子`, `菅野よう子`, `攻殻機動隊`, `攻壳机动队`, `神山健治`,
  `押井守`, `士郎正宗`, `草薙素子`, `漫改`, `深度`, `艺术`, `超前` çevrilmiyor; ayrıca
  `Production.IG` / `Production I.G` / `ProductionI.G` **üç ayrı çip** olarak görünüyordu.

## Yapılanlar

### 1. Sözlük genişletildi — `utils/BangumiTagDictionary.kt`

Giriş sayısı **~530 → 771**. Yeni bölümler:

| Bölüm | Örnek |
| --- | --- |
| Kaynak malzeme kısaltmaları | `轻小说改` / `轻改` → Hafif Roman Uyarlaması, `漫改` → Manga Uyarlaması, `动画改`, `网文改` |
| Fandom jargonu | `深度` → Derin Anlatım, `超前` → Çağının Ötesinde, `名场面`, `神回`, `情怀`, `烂尾`, `王道`, `黑深残`, `电波`, `燃` … |
| Yapım/teknik | `作画`, `分镜`, `演出`, `监督`, `脚本`, `人设`, `音响监督` |
| Yönetmen/senaristler (≈45) | `石原立也` → Tatsuya Ishihara, `武本康弘`, `谷川流`, `押井守`, `神山健治`, `新房昭之`, `细田守` … |
| Besteciler (≈14) | `菅野洋子` + `菅野よう子` → Yoko Kanno, `川井宪次`, `久石让`, `梶浦由记`, `泽野弘之` … |
| Mangaka / yazarlar (≈33) | `士郎正宗` → Masamune Shirow, `手冢治虫`, `荒川弘`, `西尾维新`, `藤本树` … |
| Seslendirme sanatçıları (≈32) | `平野绫`, `茅原实里`, `田中敦子`, `大塚明夫`, `神谷浩史` … |
| Karakterler (≈20) | `长门有希` → Yuki Nagato, `囧虚`/`阿虚` → Kyon, `草薙素子` → Motoko Kusanagi, `大萌神` → Moe Tanrıçası |
| Eserler/seriler (≈45) | `攻壳机动队`/`攻殻機動隊` → Ghost in the Shell, `凉宫春日的消失` → Haruhi Suzumiya'nın Kayboluşu, `EVA`, `高达`, `鬼灭之刃` … |
| Yayıncı/şirketler | `角川`, `集英社`, `讲谈社`, `万代`, `东宝`, `MAPPA`, `THE KLOCKWORX` … |

Mevcut gruplara eksik yazımlar eklendi: `剧场`/`劇場` → Film, `艺术` → Sanat, `漫改`, `京アニ`,
`骨头社`, `扳机社`, `疯房子`, `飞碟社`, `日升` …

### 2. Yazım farkları artık tek çipte birleşiyor

* **Latin varyant eşleşmesi**: noktalama/boşluk yok sayan ikinci tur arama eklendi —
  `Production I.G` = `Production.IG` = `ProductionI.G` → tek çip.
* **Latin ad da anahtar**: her girişin Türkçe/İngilizce adı da eşleşme tablosuna yazılıyor,
  böylece `京阿尼` + `Kyoto Animation` aynı çipte buluşuyor.

### 3. Çevrilemeyen etiketler sona alınıyor

Sözlükte karşılığı olmayan Çince/Japonca etiketler (uydurma çeviri üretilmediği için)
artık listenin sonunda gösteriliyor: `isUntranslatedCjkTag()` + `localizedDistinctTags()`
ve `mergeDetail()` sıralaması. Okunabilir etiketler önce görünür.

### 4. Dil dosyaları — 771 etiketin tamamı TR + EN

`res/values/strings.xml` (Türkçe) ve `res/values-en/strings.xml` (İngilizce) dosyalarındaki
`bangumi_tag_*` blokları sözlükten **otomatik üretiliyor**:

```bash
python3 scripts/sync_bangumi_tag_strings.py          # dosyaları günceller
python3 scripts/sync_bangumi_tag_strings.py --check  # CI doğrulaması
```

Önce 60 kaynak vardı, şimdi her iki dilde de 771. Çalışma zamanında dil dosyası sözlükten
önceliklidir; çeviri düzeltmeleri kod değişikliği gerektirmez.

### 5. Performans

`getStringResourceByName` her çip çiziminde `Resources.getIdentifier` çağırıyordu
(771 kaynak × her recomposition). Sonuçlar `dil:kaynakAdı` anahtarıyla önbelleğe alındı
(negatif önbellek dahil); uygulama bağlamı yokken önbelleğe yazılmaz.

## Testler

`app/src/test/.../KitsugiTranslationsTest.kt` içine 6 yeni test eklendi: kısaltma uyarlamaları,
kişi/eser/karakter Latinleştirme, Latin yazım varyantları, çeviri sonrası tekilleştirme,
bilinmeyen CJK etiketlerin sona alınması ve İngilizce arayüz davranışı.

> Not: Bu ortamda Gradle/JDK bulunmadığı için derleme yapılamadı; sözlük yapısı, anahtar
> benzersizliği, XML geçerliliği ve eşleşme mantığı betiklerle doğrulandı.

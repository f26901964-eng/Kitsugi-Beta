# PLAN/TASK — Bangumi Stüdyo / Yapımcı / Yayıncı Ağ / Etiket adları TR+EN entegrasyonu

Tarih: 2026-10-11 · Dal: `arena/dadf086f-kitsugi-beta` · Durum: **Faz 1-4 tamamlandı (v2.4.245)**

Kaynak belge: `BANGUMI_STUDYO_YAPIMCI_YAYINCI_ETIKET_MASTER_2026-10-11.md`
Makine-okur veri: `orgs.tsv` + `tags.tsv` (+ üretici/doğrulayıcı betikleri aynı klasörde)

## Hedef

Bangumi kaynağından detay sayfalarına gelen **stüdyo, yapımcı, yayıncı ağ ve etiket** adları;
hem **detay sayfası çiplerinde** hem de **stüdyo / yapımcı / yayıncı ağ ayrıntı sayfalarında**
gerçek adlarıyla (İngilizce küresel ad esas) **hem Türkçe hem İngilizce** dil dosyaları üzerinden
doğru gösterilsin.

## Mevcut durum (kök analiz)

| Katman | Önceki davranış | Açıklık |
|---|---|---|
| Etiket çipleri | `toLocalizedBangumiTagOrNull()` → `bangumi_tag_*` + `BangumiTagDictionary` | Çalışıyordu; `催淚`, `京都`, `AS` eşleşmiyordu |
| Stüdyo/Yapımcı/Ağ çipleri | Yalnızca `toLatinStudioName()` (CJK → Latin) | TR/EN yerelleştirme yok |
| Stüdyo/Yapımcı/Ağ **detay sayfası** | Ham/Latin `KitsugiStudioDetail.name` | TR/EN yerelleştirme yok |
| Sözlük boşlukları | `催淚`, `京都`, `AS` çipleri çevrilmemiş | Ekran görüntüsüyle doğrulandı (§7.4) |

Infobox alan eşlemesi (`KitsugiBangumiDetailClient.kt`):

* **Stüdyolar:** `动画制作` `動畫製作` `动画制作公司` `アニメーション制作` `制作` `制作公司`
* **Yapımcılar:** `製作` `出品` `发行` `製作委员会` `制作委员会` (manga: `出版社` `出版`)
* **Yayıncı Ağlar:** `播放电视台` `播放電視台` `其他电视台` `其他電視台`
* **Dergiler:** `连载杂志` `連載雜誌` `杂志`

## Fazlar

### Faz 1 — Etiket sözlüğü tamamlama ✅
- [x] §7.4 boşlukları: `催淚`→`tearjerker`, `京都`→`kyoto_animation`, `続編`→`sequel`, yeni `after_story` (`AS`)
- [x] §7.2 meta + §7.3 öneri etiketleri sözlüğe `g(...)` grubu olarak (**+78 anahtar**)
- [x] Mevcut 37 gruba **+95** varyant etiketi
- [x] `values/strings.xml` + `values-en/strings.xml` — **907/907** `bangumi_tag_*` (simetrik)
- [x] Doğrulama: `verify_bangumi_master.py` → etiket gölgelenmesi 0, kapsama tam

### Faz 2 — Şirket adları için dil dosyası mekanizması ✅
- [x] `BangumiOrgDictionary` (üretilmiş): 365 kayıt / **1013 eşleme**, etiket → `bangumi_*` anahtarı + Latin ad
- [x] `toLocalizedOrgName()` + `toLocalizedOrgNameOrNull()` — kaynak → Latin ad → orijinal zinciri
- [x] `toLatinStudioName()` artık önce bu sözlüğe danışır (arama/dedup Latin adla sürer)
- [x] ⚠ işaretli 7 ad dil dosyasına **alınmadı** (`pending`)

### Faz 3 — Detay sayfaları ✅
- [x] `KitsugiStudiosCard` çipleri (stüdyo / yapımcı / ağ) yerelleştirildi
- [x] Bilgi satırları (`KitsugiDetailInfoSection`, `ApiResultDetailComponents`) yerelleştirildi
- [x] `StudioDetailComponents` başlıkları (hero + kompakt) yerelleştirildi
- [x] Dedup davranışı korundu (`namesMatch` / Latin ad ile arama değişmedi)

### Faz 4 — Doğrulama ✅ (cihaz testi kullanıcıda)
- [x] `BangumiMasterDictionaryTest` (JUnit) — boşluklar, yeni etiketler, şirket eşlemeleri, politika
- [x] `verify_bangumi_master.py` — 374 etiket çözümlemesi + dil dosyası simetrisi + kapsama
- [ ] Clannad ~After Story~ ekran görüntüsüyle cihaz üstü karşılaştırma (derleme sonrası)

## Kabul kriterleri

1. Bilinen her stüdyo/şirket/kanal adı TR ve EN'de küresel adıyla görünür; bilinmeyenler olduğu
   gibi kalır (**uydurma çeviri yok**).
2. `values/` ve `values-en/` birebir aynı anahtar kümesine sahiptir (829 + 78 = **907** etiket).
3. Şirket adları hem çiplerde hem **detay sayfası başlığında** aynı sözlükten beslenir.
4. Etiket çiplerinde çevrilmemiş bilinen CJK kalmaz (`催淚`, `京都` dahil).
5. Performans: kaynak aramaları mevcut önbellekli akıştan geçer (`bangumiTagResourceCache`).

## Notlar / riskler

* Komite adları (`PROJECT-XXX`, `光坂高校演劇部`) **çevrilmez** — politika gereği olduğu gibi kalır.
* ⚠ işaretli 7 nadir ad (`Studio Gohan`, `Mouse`, `Studio Nagomu`, `Revootoot`, `Signpost`,
  `Staple Entertainment`, `Nippon Eiga-sha`) kaynakta doğrulanana kadar beklemede.
* Aynı şirket birden çok rolde listelenebilir (`Production I.G`, `bilibili`, `白泉社`, `CBC`);
  sözlük tek anahtara bağlar, ekranda görünen ad aynı olduğu için zararsızdır.
* Bangumi dump'ları bu ortamdan indirilemiyor; nadir etiket görüldükçe `tags.tsv`'ye eklenip
  üretici yeniden çalıştırılmalıdır.

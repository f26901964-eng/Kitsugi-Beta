# BANGUMI STÜDYO / YAPIMCI / YAYINCI AĞI / ETİKET AD SÖZLÜĞÜ — MASTER LİSTE (repo kaydı)

**Tarih:** 2026-10-11 · **Durum:** v2.4.245 ile dil dosyalarına işlendi
**Tam sürüm:** Drive — `Kitsugi-Beta_2026-10-11_Bangumi_TR_EN/BANGUMI_STUDYO_YAPIMCI_YAYINCI_ETIKET_MASTER_2026-10-11.md`
(dosya bağlantısı: <https://drive.google.com/drive/folders/1CF0OxbtqSJV2kr3XIuH5K6obT7rINsRh>)

> **Not:** Bu, kaynak belgenin repo tarafındaki özetidir. Buradaki **asıl kaynak makine-okur
> dosyalardır**: [`orgs.tsv`](orgs.tsv) ve [`tags.tsv`](tags.tsv). Dil dosyalarına giren her kayıt
> `gen_bangumi_master.py` ile bu TSV'lerden üretilir; `verify_bangumi_master.py` denetler.
> Kaynak belgenin §7.6 (mevcut 820 grup dökümü) ve §8 (mevcut CJK→Latin 117 satır eşlemesi)
> bölümleri **bilerek aktarılmadı**: bunlar zaten repodaki `BangumiTagDictionary.kt` ve
> `KitsugiTranslations.kt` dosyalarının kendisidir.

Bu dosya, **Bangumi (bgm.tv) kaynağından** detay sayfalarına gelebilecek **tüm stüdyo, yapımcı,
yayıncı ağ ve etiket adları** için Türkçe + İngilizce karşılıkları tek yerde toplar. Hem **detay
sayfası çipleri** hem de **stüdyo / yapımcı / yayıncı ağ ayrıntı sayfaları** aynı sözlükten
beslenecek şekilde tasarlanmıştır. Şirket/stüdyo adlarında **İngilizce küresel (resmî) ad** esastır;
Türkçe değer marka adları için aynıdır (istisnalar tablolarda belirtilir).

## 0. Kullanım sözleşmesi

| Bölüm | Anahtar öneki | Nerede kullanılır |
|---|---|---|
| Stüdyolar | `bangumi_studio_*` | Detay "Stüdyolar" çipleri + stüdyo detay sayfası başlığı/araması |
| Yapımcılar | `bangumi_producer_*` | Detay "Yapımcılar" çipleri + yapımcı detay sayfası |
| Yayıncı Ağlar | `bangumi_network_*` | Detay "Yayıncı Ağlar" çipleri + ağ detay sayfası |
| Yayımcılar/Dergiler | `bangumi_publisher_*` / `bangumi_magazine_*` | Manga yayımcı/dergi çipleri + detay sayfası |
| Etiketler | `bangumi_tag_*` (mevcut şema) | Etiket çipleri, arama, filtreler |

Dil dosyası biçimi mevcut şemayla uyumludur: `<string name="bangumi_studio_kyoto_animation">Kyoto Animation</string>`

Kurallar (mevcut `BangumiTagDictionary` politikasıyla aynı):

* **Uydurma çeviri yok.** Karşılığı olmayan özel ad (komiteler `PROJECT-XXX`, `光坂高校演劇部`)
  olduğu gibi gösterilir; listeye eklenmez.
* Çince basit/geleneksel ve Japonca yazımlar **aynı girişin etiketlerinde** toplanır
  (`京都动画` + `京都動畫` + `京都アニメーション` + `京アニ` → `Kyoto Animation`).
* Eşleme normalizasyonla yapılır: tam genişlik, sarımlar (`「」（）【】`), büyük/küçük harf yok sayılır;
  ikinci turda yalnızca harf/rakam bırakan sıkı anahtar denenir (`Production.IG` ≡ `Production I.G`).

## 1. Bangumi infobox alan → uygulama bölümü eşlemesi

| Uygulama bölümü | Rol | Bangumi infobox anahtarları |
|---|---|---|
| Stüdyolar | `StudioRole.STUDIO` | `动画制作` `動畫製作` `动画制作公司` `アニメーション制作` `制作` `制作公司` |
| Yapımcılar (anime) | `StudioRole.PRODUCER` | `製作` `出品` `发行` `製作委员会` `制作委员会` |
| Yapımcılar (manga → yayımcı) | `StudioRole.PUBLISHER` | `出版社` `出版` |
| Yayıncı Ağlar | `StudioRole.NETWORK` | `播放电视台` `播放電視台` `其他电视台` `其他電視台` |
| Dergiler | `StudioRole.MAGAZINE` | `连载杂志` `連載雜誌` `杂志` |

## 2. ÖZET — Kapsam raporu (bu sürümde işlenen)

* **Stüdyolar:** 133 kayıt (5'i ⚠ doğrulanmayı bekliyor → dil dosyasına alınmadı)
* **Yapımcılar:** 90 kayıt (1'i ⚠ beklemede)
* **Yayıncı Ağlar:** 96 kayıt (JP/CN/TW/KR/Global TV + dijital platformlar)
* **Yayımcılar/Dergiler:** 24 + 21 kayıt
* **Etiketler:** 78 yeni anahtar + 37 mevcut gruba 95 varyant etiketi
* **Dil dosyası simetrisi:** `values/` = `values-en/` = 6341 kayıt (907 `bangumi_tag_*`)
* **Şirket eşleme satırı:** 1013 (CJK/Japonca varyantlar + Latin adlar)

### 2.1 Ekran görüntüsüyle doğrulanan boşluklar (§7.4)

| Mevcut anahtar | Eklenen etiket | Sonuç |
|---|---|---|
| `tearjerker` | `催淚` (geleneksel) | Duygusal / Tearjerker |
| `kyoto_animation` | `京都` | Kyoto Animation |
| *(yeni)* `after_story` | `AS` `After Story` `アフターストーリー` | After Story (2 harfli etiket yalnızca tam sözcük eşleşir) |
| `sequel` | `続編` | Devam / Sequel |

### 2.2 §7.2 yeni meta etiketler (özet)

Bölge: `华语` · `加拿大` · `捷克` · `马来西亚` · `新西兰` · Tema: `布袋戏` · `少儿`
Oyun türleri: `ARPG` `CRPG` `DBG` `DRPG` `EDU` `FPS` `JRPG` `MOBA` `Platform` `PUZ` `RAC` `RTT`
`Rhythm`(→ mevcut `rhythm_game`) `Roguelike` `SIM` `SRPG` `Sandbox` `TPS` `VN` `休闲` `卡牌对战`

### 2.3 §7.3 kullanıcı etiketi önerileri (özet)

`异世界` · `无限流` · `修仙` · `打脸` · `后宫向` · `青梅竹马` · `物语系列` · `VTuber` · `浪人` ·
`怪盗团` · `黑暗奇幻` · `一口气看完` · `单元剧` · `获奖` … (78 yeni anahtar; geri kalanı mevcut
gruplara varyant olarak eklendi — tam liste `tags.tsv`).

### 2.4 ⚠ Doğrulanmayı bekleyen adlar (`pending`)

`Studio Gohan` · `Studio Nagomu` · `Mouse` · `Revootoot` · `Signpost` · `Staple Entertainment` ·
`Nippon Eiga-sha` — kaynakta görülmeden dil dosyalarına alınmaz.

## 3. Sözlük boşluklarının kapatıldığı yerler (kod)

| Dosya | Değişiklik |
|---|---|
| `utils/BangumiTagDictionary.kt` | +78 grup, +95 etiket (üretici tarafından) |
| `utils/BangumiOrgDictionary.kt` | **Üretilmiş** şirket sözlüğü (1013 eşleme) |
| `utils/KitsugiTranslations.kt` | `toLocalizedOrgName()` / `toLocalizedOrgNameOrNull()`; `toLatinStudioName()` artık sözlüğe danışır |
| `ui/screens/detail/KitsugiDetailComponents.kt` | Stüdyo/yapımcı/ağ çipleri yerelleştirildi |
| `ui/screens/detail/StudioDetailComponents.kt` | Detay sayfası başlıkları yerelleştirildi |
| `ui/screens/detail/KitsugiDetailInfoSection.kt` · `ApiResultDetailComponents.kt` | Bilgi satırları yerelleştirildi |
| `res/values/strings.xml` · `res/values-en/strings.xml` | +436 kayıt/dil (78 etiket + 358 şirket) |
| `test/.../BangumiMasterDictionaryTest.kt` | Sözleşme testleri |

# Kitsugi-Beta v2.4.249 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Vitrin (Keşfet Hero) Yatay Mod Uyumu ve Kırpma Düzeltmesi

- **Yatay modda vitrin bandı büyütüldü:** Yatay telefon/tablet vitrini artık ekran yüksekliğinin ~%74'ünü (tablet ~%62) kaplıyor. 16:9 kapak görselleri daha önce ~2.5–3:1 gibi ince bir şeride kırpılıyordu; yeni bantta en-boy oranı ~2.07:1'e indi ve görselin yaklaşık %86'sı görünüyor — telefon, tablet, yatay ve dikey otomatik uyarlanır.
- **Akıllı kırpma hizalaması:** Görselin **gerçek** en-boy oranı okunup kırpma penceresi ona göre hesaplanıyor; görselin üstünden yalnızca ~%5 pay kesiliyor ve yatayda tam ortalanıyor. Karakterlerin kafası/yüzü artık üstten kesilmiyor, görsel de gereğinden fazla yukarı kaymış görünmüyor.
- **Karartma (scrim) azaltıldı:** Üst üste binen gradyanlar yumuşatıldı; vitrin görseli "karanlık/çamurlu" görünmüyor, metin okunurluğu korunuyor.
- **Manga yatay modda tam ortada:** Dikey poster (manga) geniş yatay vitrinde artık sağa yaslanmıyor; ekranın **tam ortasında**, olabildiğince büyük ve kesilmeden gösteriliyor (arkasında yumuşak atmosferik dolgu).

### 🎬 2. Detay Sayfaları — Yön Bazlı "En Kaliteli Görsel" Mantığı

- **Yatay mod:** En kaliteli **YATAY** görsel (TMDB / Fanart.tv / galeri arka planları) **16:9 sinematik bantta kırpılmadan** gösterilir; başlık, meta satırları ve rozetler bandın altına akar.
- **Dikey mod:** En kaliteli **DİKEY poster** (tam çözünürlüğe yükseltilmiş) tam boy vitrinde gösterilir.
- **Oran uyuşmazlığında kesme yok:** Görsel alanı ile görselin en-boy oranı çok farklıysa (ör. yatay görsel + dikey panel) kırpma yerine "tam görünür + yumuşak dolgu" devreye girer.
- **Kütüphane detayı da aynı mantıkta:** `MediaEntry` detay sayfasının yatay vitrini de kayıtla ilişkili en kaliteli yatay galeri görselini kullanır.

### ✅ 3. Testler

- `HeroImageSelectionTest` yeni dinamik hizalama, oran/uyum (COVER ↔ CONTAIN_WITH_FILL) ve cihaz/yön bazlı bant yüksekliği kurallarıyla genişletildi.

---

## 🇬🇧 English

### 🖼️ 1. Explore Hero Landscape Fit & Cropping Fix

- **Taller landscape hero band:** The landscape phone/tablet hero now uses ~74% (tablet ~62%) of the screen height. 16:9 key visuals used to be cropped into a ~2.5–3:1 sliver; the new band lands at ~2.07:1 so roughly 86% of the artwork is visible — automatically adapting to phone, tablet, landscape and portrait.
- **Smart crop alignment:** The artwork's **real** aspect ratio is read and the crop window is derived from it — only ~5% is trimmed from the top and the image is horizontally centred. Character heads/faces are no longer cut off, and the art no longer drifts too far up.
- **Lighter scrims:** Overlapping gradients were softened; the hero artwork is no longer muddy/dark while text contrast is preserved.
- **Manga centred in landscape:** Vertical posters (manga) are no longer pushed to the right — they are shown **dead centre**, as large as possible and uncropped, over a soft ambient fill.

### 🎬 2. Detail Pages — Orientation-Aware "Best Artwork" Logic

- **Landscape:** The highest-quality **WIDE** artwork (TMDB / Fanart.tv / gallery backdrops) is shown in a **cropped-free 16:9 cinematic band**; title, metadata rows and badges flow below the band.
- **Portrait:** The highest-quality **VERTICAL poster** (upgraded to full resolution) fills the full-height hero.
- **No slicing on aspect mismatch:** When the artwork and the hero area differ wildly (e.g. wide art in a tall panel), the app switches to "fully visible + soft fill" instead of cropping.
- **Library detail matches:** The `MediaEntry` detail page's landscape hero now uses the best wide gallery artwork of the entry.

### ✅ 3. Tests

- `HeroImageSelectionTest` was extended with the new dynamic alignment, fit (COVER ↔ CONTAIN_WITH_FILL) and device/orientation band-height rules.

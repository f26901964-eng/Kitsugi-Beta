# Kitsugi-Beta v2.4.250 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🎬 1. Detay Sayfaları — Her İki Yönde de 16:9 Sinematik Bant (POSTER VİTRİN YOK)

- **Vitrin artık her zaman 16:9 sinematik banttır:** Detay sayfası dikey modda da, yatay modda da **aynı tasarımı** kullanır. Poster artık vitrin paneli olarak kullanılmaz.
- **Bant kırpılmaz:** En kaliteli **YATAY** görsel (TMDB / Fanart.tv / galeri arka planları) 16:9 bantta **kesilmeden** gösterilir. Görselin oranı banttan belirgin farklıysa (~%20'den fazla kırpma gerekiyorsa) kırpmak yerine "tam görünür + yumuşak dolgu" devreye girer — ör. çok geniş bir sinema afişi veya dikey bir poster bandın içinde tamamı görünür.
- **En kalitelisi otomatik seçilir:** Aday sırası kalite sıralıdır → çözümleyicinin TMDB/Fanart.tv sıralaması (4K > 2K > 1080p, yazısız, oylu) → kaynağın kendi arka planı → galeri yatay görselleri (çözünürlüğe göre).
- **Boyut uyumu:** Bant yüksekliği panel genişliğinden türetilir (`genişlik × 9/16`) ve ekran yüksekliğinin %62'sini geçmez → telefon, tablet, TV ve hem dikey hem yatay yönde aynı görünüm.
- **Başlık/meta bandın altında:** Logo/başlık, format-tür sayaç satırı, yayın bilgisi ve rozetler (kaynak, yıl, puan, favori, listede, +18) bandın altındaki bilgi alanına akar.
- **Kütüphane (MediaEntry) detay sayfası** da birebir aynı mantığa geçti: en kaliteli yatay galeri görseli bantta, yoksa poster bandın içinde kesilmeden.

### 🖼️ 2. Keşfet Vitrini (Hero) Yatay/Dikey Uyumu

- **Yatay modda bant büyütüldü:** Yatay telefon vitrini ekran yüksekliğinin ~%74'ü (tablet ~%62). 16:9 kapak görselleri artık ince bir şeride kırpılmıyor; görselin yaklaşık %86'sı görünüyor.
- **Dinamik kırpma hizalaması:** Görselin **gerçek** en-boy oranı okunup kırpma penceresi ona göre hesaplanıyor; görselin üstünden yalnızca ~%5 pay kesiliyor ve yatayda tam ortalanıyor → karakterlerin kafası/yüzü kesilmiyor, görsel fazla yukarı kaymıyor.
- **Karartma (scrim) azaltıldı:** Üst üste binen gradyanlar yumuşatıldı; görsel net ve canlı.
- **Manga yatayda tam ortada:** Dikey poster (manga) geniş yatay vitrinde sağa yaslanmıyor; ekranın **tam ortasında**, olabildiğince büyük ve kesilmeden gösteriliyor (arkasında yumuşak atmosferik dolgu).

### ✅ 3. Testler

- `HeroImageSelectionTest`: dinamik hizalama, oran/uyum (COVER ↔ CONTAIN_WITH_FILL) ve cihaz/yön bazlı bant yüksekliği testleri.

---

## 🇬🇧 English

### 🎬 1. Detail Pages — 16:9 Cinematic Band in BOTH Orientations (NO poster hero)

- **The hero is always a 16:9 cinematic band:** the detail page uses the **same layout** in portrait and landscape. Vertical posters are no longer used as the hero panel.
- **The band is never sliced:** the highest-quality **WIDE** artwork (TMDB / Fanart.tv / gallery backdrops) is shown **uncropped**. When the artwork ratio differs too much from the band (would require >~20% cropping) the app switches to "fully visible + soft fill" — e.g. an ultra-wide key visual or a vertical poster is shown complete inside the band.
- **Best quality auto-selected:** candidates are quality-ordered → resolver's TMDB/Fanart.tv ranking (4K > 2K > 1080p, textless, voted) → native backdrop → wide gallery images (by resolution).
- **Device fit:** band height derives from the panel width (`width × 9/16`) and never exceeds 62% of screen height → identical look on phones, tablets, TV, portrait and landscape.
- **Title/metadata below the band:** logo/title, format & count row, airing info and badges (source, year, score, favourite, in-list, +18) flow into the info area under the band.
- **Library (MediaEntry) detail page** follows the exact same rule: best wide gallery artwork in the band, otherwise the poster shown complete inside the band.

### 🖼️ 2. Explore Hero Landscape/Portrait Fit

- **Taller landscape band:** landscape phone hero now uses ~74% of the screen height (tablet ~62%); 16:9 key visuals are no longer cropped into a sliver — about 86% of the artwork is visible.
- **Dynamic crop alignment:** the artwork's **real** aspect ratio drives the crop window; only ~5% is trimmed from the top and the image is horizontally centred.
- **Lighter scrims:** overlapping gradients softened; the artwork is crisp and vivid.
- **Manga centred in landscape:** vertical posters are no longer pushed to the right — they sit **dead centre**, as large as possible and uncropped, over a soft ambient fill.

### ✅ 3. Tests

- `HeroImageSelectionTest`: dynamic alignment, fit (COVER ↔ CONTAIN_WITH_FILL) and device/orientation band-height rules.

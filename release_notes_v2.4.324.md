# Kitsugi Beta v2.4.324

## 🇹🇷 Türkçe

### 🎠 Vitrin Göstergesi, Otomatik Kaydırma & Dikey Modda Poster İyileştirmeleri

Bu sürüm, ana sayfadaki vitrin (hero banner) bileşeninde yaşanan gösterge çubuğu sayısı hatasını, 5 saniyede bir otomatik kaydırmayı ve dikey telefondaki görsel yerleşimini kapsamlı şekilde düzeltir.

- **Vitrin barı artık gerçek sayfa sayısıyla birebir (`HomeHeroPolicy.kt` & `KitsugiHeroSection.kt`):**
  - **Sorun:** Eski kodda kayan pencere mantığıyla en fazla 7 bar çiziliyordu. 18 veya daha fazla içerik olsa dahi bar 7 adeti geçmiyor, sayfa kaydırıldıkça barlar kayarak "bug" hissi veriyordu.
  - **Çözüm:** Kayan pencere kaldırıldı; kaç içerik varsa satırda o kadar bar çizilir. İçerik sayısı arttıkça barlar orantılı incelir, toplam sayı her zaman doğrudur.
- **5 saniyede bir kesintisiz otomatik kaydırma (`KitsugiHeroSection.kt`):**
  - **Kök Neden:** `LaunchedEffect` anahtarlarında `pagerState.isScrollInProgress` yer alıyordu. Sayfa kaydırma animasyonu başlar başlamaz bu durum `true` oluyor, anahtar değiştiği için effect anında iptal ediliyor ve animasyonu durduruyordu.
  - **Çözüm:** Efekt yalnızca kararlı anahtarlara bağlandı. Kullanıcı dokunmadığı, sürüklemediği ve sayfayı boşta bıraktığı 5 saniye boyunca sayaç dolar ve sayfa yumuşak bir animasyonla kayar. Kenara ulaşıldığında yön değişir (ping-pong).
- **Dikey telefon modunda yüksek çözünürlüklü poster (`HeroVitrinLayout.kt`, `HomeHeroArtworkLoader.kt`):**
  - **Sorun:** Dikey telefon ekranında vitrin bandı dar ve uzun (en-boy < 1) olduğu için 16:9 yatay görsel bantta ince bir şerit olarak kalıyor, altındaki alan siyah boşluk oluyordu.
  - **Çözüm:** Ekran geometrisine göre dikey modda (`prefersPoster = true`) içeriğin en kaliteli posteri (TMDB/Fanart `original` veya yerli kaynağın en yüksek çözünürlüklü posteri) bandı tam kaplayacak şekilde (`Crop` + scrim) yerleşir.
  - **Kendi Kendini Onarma:** Poster yüklenemezse veya decode edilen görsel dikey değilse sayfa otomatik olarak güvenli yatay şerit görünümüne geri döner. Yatay, tablet ve TV modları yatay görselle devam eder.

---

## 🇬🇧 English

### 🎠 Home Hero Indicator, Autoplay & Vertical Poster Overhaul

Resolves showcase banner pagination bar count, restores 5-second automatic carousel sliding, and adapts vertical smartphone screens with crisp full-bleed posters.

- **Exact Indicator Bar Count (`HomeHeroPolicy.kt` & `KitsugiHeroSection.kt`):**
  - Eliminated the sliding 7-bar window limit. The indicator count now strictly mirrors the actual number of showcase pages with dynamically weighted slot widths.
- **Restored 5-Second Autoplay Loop (`KitsugiHeroSection.kt`):**
  - **Root Cause:** Passing `isScrollInProgress` into the `LaunchedEffect` keys aborted the effect as soon as animation started.
  - Refactored autoplay into a single stable coroutine loop that smoothly slides pages after 5 seconds of uninterrupted user idle, pausing on touch/drag.
- **Adaptive Full-Bleed Posters for Portrait Mode (`HeroVitrinLayout.kt`, `HomeHeroArtworkLoader.kt`):**
  - Portrait smartphones now prioritize maximum-resolution vertical key art (TMDB/Fanart original or upscaled provider cover) over squished 16:9 backdrops.
  - Self-healing fallback: smoothly degrades to ambient horizontal backdrop if the poster fails to load or resolves to a non-vertical aspect ratio.

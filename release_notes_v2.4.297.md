# Kitsugi Beta v2.4.297

## 🇹🇷 Türkçe

### ⬆️ Yukarı Hızlı Kaydırma Düğmesi (Scroll-To-Top FAB) Yenilendi
- **Yeniden Tasarlanan Ortak FAB:** Keşfet, Arama ve ortak ekranlarda yer alan yukarı kaydırma düğmesi (`KitsugiScrollToTopFab`) modernize edildi:
  - 56dp boyutunda, `CircleShape` yuvarlak tasarım ve 8dp gölge.
  - Daha belirgin ve zarif `KeyboardArrowUp` ikonu (30dp).
  - Görünme/kaybolma geçişleri yumuşatıldı; ölçeklenme (`scale`) animasyonu eklendi.
  - Alt gezinme çubuğunun (BottomBar) açık/kapalı durumuna göre dinamik alt dolgu korundu.
- **Wiki Okuyucusuna Hızlı Kaydırma FAB'ı:**
  - `FandomWikiReader` sayfalarında okuma esnasında (400px kaydırmadan sonra) sağ altta beliren ve tıklandığında sayfanın en başına pürüzsüz kaydıran yukarı çık düğmesi eklendi.
  - Artık tüm listeler, arama sonuçları ve wiki okuyucusu aynı tutarlı ve şık FAB tasarımına sahip.

---

## 🇬🇧 English

### ⬆️ Scroll-To-Top FAB Redesigned & Added to Wiki Reader
- **Modernized Common FAB:** The scroll-to-top floating button (`KitsugiScrollToTopFab`) used across Explore and Search screens has been revamped:
  - 56dp circular layout (`CircleShape`) with 8dp elevation shadow.
  - Crisp `KeyboardArrowUp` icon (30dp).
  - Smoother fade transitions paired with dynamic entrance/exit scale animations.
  - Dynamic bottom inset padding matching the navigation bar visibility state.
- **Wiki Reader Scroll-To-Top FAB:**
  - Inside `FandomWikiReader`, scrolling past 400px now presents the unified floating scroll-to-top button to smoothly jump back to the header card.
  - Provides a consistent, unified scrolling experience across explore, search, and wiki reading surfaces.

# Kitsugi-Beta v2.4.278 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.278)

### 🪟 Ayarlar Seçim Menüleri AniHyou Stili Modal Pencerelere Dönüştürüldü
- **Ortalanmış Modal Pencereler:** Ayarlar ekranındaki açılır seçimler artık satırların altında/üstünde taşarak açılmıyor; ekranın ortasında, arka planı karartan şık ve modern modal pencereler olarak açılıyor (`KitsugiSettingsDropdownDialog`).
- **Radyo Butonlu Seçenekler:** Tüm seçenekler net radyo düğmeleri ve tema renkleriyle vurgulanıyor.
- **Başlık ve Kapat Butonu:** Her pencereye ilgili ayarın açık başlığı ve sağ altta dokunulabilir / TV odaklanabilir **Kapat** eylemi eklendi.
- **Kaydırılabilir Uzun Listeler:** Uzun dil, format veya filtre listeleri modal pencere içinde kendi alanında dikey kaydırılabiliyor.
- **36 Seçim Menüsü Kapsandı:** Tema modu, uygulama dili, başlangıç sekmesi, anime/dizi başlık dili, ekip/karakter dili, liste düzeni, puanlama adımları, manga okuyucu filtre ve yönleri, varsayılan oynatıcı, intro atlama, MPV ayarları, altyazı stilleri, Stremio ve P2P profilleri dahil 36 aktif ayar menüsü uyarlandı.
- **İzole Davranış:** Arama, liste ve detay sayfalarındaki standart açılır menüler (`AnchoredPopup`) korunarak yalnızca ayarlar ağacı (`SettingsDialog`) dönüştürüldü.
- **Telefon, Tablet ve TV Uyumlu:** Genişlik (en fazla 560 dp) ve yükseklik (%86) sınırlarıyla tüm form faktörlerinde kusursuz görüntü.

### 🛡️ RenderThread Native Çökmesi (MIUI/HyperOS Force Dark) ve Genel Kararlılık Paketi
`crash_log (5).txt` raporundaki SIGSEGV (0x20) RenderThread çökmesi ve kod tabanındaki tüm potansiyel çökme/çakışma kaynakları giderildi:
- **MIUI / HyperOS Force Dark (`TransformCanvas`) Çökmesi:** `values`, `values-v31` ve yeni `values-night` stillerinde `android:forceDarkAllowed="false"` zorlandı; Activity ve Dialog/BottomSheet pencere decorView'larında programatik olarak kapatıldı.
- **AngleLinearGradientBrush Yarışları & 0x0 Boyut Koruması:** `ShaderBrush` singleton yarışları thread-safe LRU shader cache ile çözüldü; Offscreen + SrcIn bileşenlerine `size > 0` kontrolleri eklendi.
- **HorizontalPager & Re-entrant Layout İyileştirmesi:** 9 `HorizontalPager` noktasında `beyondViewportPageCount = 0` yapıldı; profil sekme yükseklikleri `KitsugiProfileViewModel` içinde kalıcı saklanarak re-measure fırtınaları durduruldu.
- **GPU Buffer (AHardwareBuffer) & Bitmap Yaşam Döngüsü:** Coil `.allowHardware(false)` yapılandırılarak galeri kaynaklı GPU buffer tıkanmaları önlendi; boyut sınırları ve bitmap korumaları getirildi.
- **Detay Sayfası Animasyon & State Senkronizasyonu:** Detay sayfalarından çıkış animasyonu tamamlanmadan ekran temizlenmesi engellendi (`delay(350L)`); başlangıç sekmesi disk gecikmesiz senkron yüklendi (Profil sekmesi eklendi).
- **Güvenli Çağrılar & Benzersiz Lazy Anahtarlar:** Lambda/coroutine içi `!!` çağrıları güvenli null kontrollerine çevrildi; listelerde yinelenen anahtar hataları `kitsugiLazyKey` ile engellendi.

---

## 🇬🇧 English (v2.4.278)

### 🪟 Settings Selection Menus Redesigned into AniHyou-Style Modal Dialogs
- **Centered Modal Dialogs:** Settings dropdown panels no longer expand awkwardly below rows; they now present as centered, backdrop-dimmed modal dialogs (`KitsugiSettingsDropdownDialog`).
- **Radio-Button Option Rows:** Options now feature distinct radio buttons colored dynamically by the active theme.
- **Dialog Header & Close Button:** Each modal includes its descriptive setting title and an accessible **Close** action button.
- **Internal Vertical Scrolling:** Long lists (languages, score formats, filters) smoothly scroll within the modal container.
- **Comprehensive Scope (36 Menus):** Covers Theme Mode, App Language, Default Tab, Anime/Drama Title Languages, Staff/Character Name Languages, List Layout, Scoring Steps, Manga Reader Orientation/Color, Default Video Player, Intro Skip, MPV options, Subtitle Styles, Stremio Player, and P2P options.
- **Scoped to Settings:** Keeps search, library, and detail popups compact and unaffected via `LocalKitsugiDropdownPresentation`.
- **Responsive Across Phone, Tablet & TV:** Fitted with responsive maxWidth (560 dp) and maxHeight (86% screen) constraints.

### 🛡️ RenderThread Native Crash (MIUI/HyperOS Force Dark) & Comprehensive Stability Fixes
Comprehensively resolves the SIGSEGV (0x20) RenderThread crash identified in `crash_log (5).txt` along with codebase-wide stability hazards:
- **MIUI / HyperOS Force Dark Crash:** Disabled `android:forceDarkAllowed="false"` across all themes (`values`, `values-v31`, `values-night`) and decor views, resolving `libhwui.so` SIGSEGV (0x20).
- **AngleLinearGradientBrush Race Fix & 0x0 Size Protections:** Rewrote gradient brush with thread-safe LRU caching and added `size > 0` checks on Offscreen + SrcIn components.
- **HorizontalPager & Layout Stabilization:** Set `beyondViewportPageCount = 0` across 9 pagers and cached profile tab heights in `KitsugiProfileViewModel` to eliminate layout storms.
- **GPU Hardware Buffer & Bitmap Lifecycle:** Applied `.allowHardware(false)` to prevent `/dev/kgsl-3d0` buffer exhaustion in image galleries, added bounded size caps, and eliminated unsafe bitmap recycling.
- **Detail Screen Transition & Boot Synchronization:** Deferred `SaveableStateHolder` cleanup until the 280ms exit animation finishes (`delay(350L)`) and ensured instant boot tab resolution (Profile tab added).
- **Null-Safety & Unique Lazy Keys:** Replaced unsafe `!!` operators with safe null checks and ensured unique lazy list keys with `kitsugiLazyKey`.

---

## 🧪 Verification
- Unit test suite validation (`HeroImageSelectionTest`, `MyListIdentityCrosswalkTest`): `BUILD SUCCESSFUL`.
- Comprehensive architecture documents:
  - `PLAN_TASK_AYARLAR_ANIHYOU_MODAL_SECIM_PENCERELERI_2026-10-13.md`
  - `PLAN_TASK_CRASH_LOG_5_VE_TUM_COKMELER_COZUM_2026-10-13.md`

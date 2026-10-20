# Kitsugi Beta v2.4.320

## 🇹🇷 Türkçe

### 🎬 Aniyomi Paritesi: Gelişmiş Altyazı Ayarları & Özelleştirme Paneli

Aniyomi video oynatıcı altyazı ayarları mimarisi eksiksiz olarak Kitsugi'ye uyarlandı. Eski 2 kartlı basit yapı yerine Aniyomi ile birebir 3 kartlı (Topografi, Renkler, Diğer) zengin pager arayüzü ve gelişmiş MPV/Media3 altyazı motoru entegrasyonu getirildi. Ayrıntılı plan: `PLAN_TASK_ANIYOMI_OYNATICI_PARITESI_2026-10-21.md`.

- **1. Topografi Kartı (`TypographyCard`):**
  - **Kalın / İtalik geçişleri:** Tek dokunuşla bağımsız bold ve italic açma/kapama (`IconToggleButton`).
  - **Metin Hizalama:** Sol, Orta, Sağ ve Otomatik hizalama seçenekleri (`FormatAlign` ikonları).
  - **Yazı Tipi Seçici:** 12 temel font (Sans Serif, Serif, Monospace, Arial, Noto, Roboto vb.) + `filesDir/fonts` dizininden dinamik yerel TTF tarama ve yükleme desteği.
  - **Yazı Tipi Boyutu:** 1..100 hassas slider kontrolü (varsayılan: 55).
  - **Sınır Şekli:** Dış hat ve gölge (`outline-and-shadow`), Opak kutu (`opaque-box`), Arka plan kutusu (`background-box`).
  - **Kenar Boyutu & Gölge Uzaklığı:** 0..10 arası hassas slider ayarları ve kartı sıfırlama seçeneği.
- **2. Renkler Kartı (`ColorsCard`):**
  - **Metin, Kenar ve Arka Plan Seçici:** Her öğe için bağımsız renk katmanı.
  - **RGBA Renk Kanalları:** Kırmızı, Yeşil, Mavi ve Alfa (saydamlık) için 0..255 arası renkli slider'lar.
  - **Canlı Önizleme:** Anlık renk önizleme kutusu ve MPV'ye canlı `#AARRGGBB` hex formatında enjeksiyon (`sub-color`, `sub-border-color`, `sub-back-color`).
- **3. Diğer Kartı (`MiscellaneousCard`):**
  - **ASS/SSA Geçersiz Kılma:** `sub-ass-override` (force/scale) toggle desteği.
  - **Ölçek (sub-scale):** 0.2..5.0 aralığında genel altyazı ölçeklendirme.
  - **Konum (sub-pos):** 0..150 dikey yerleşim konumu.
  - **Dikey İnce Ayar:** -200..200 piksel dikey ofset ve sadece tercih edilen dil filtresi.
- **Adaptif Düzen:** Dikey ekranda akıcı `HorizontalPager` ve 3 nokta göstergesi, yatay modda dikey kaydırılabilir 3 kartlı sütun.
- **Media3 & MPV Motor Senkronizasyonu:**
  - MPV tarafında `sub-font`, `sub-font-size`, `sub-bold/italic`, `sub-color/back/border-color`, `sub-border-size/style`, `sub-pos`, `sub-justify` doğrudan stil üzerinden uygulanır.
  - Media3 tarafında `Typeface` kombinasyonu, `CaptionStyleCompat` ile `EDGE_TYPE` kenarlık eşlemesi, SP font hesabı ve `bottomPaddingFraction` konumlandırması tamamlandı.
  - `SettingsDataStore` ile 12 yeni altyazı parametresi yerel olarak kalıcı hale getirildi.

### ⚙️ MPV Yapılandırma & Kullanıcı Dosyaları Desteği (mpv.conf / input.conf)

- **Başlatma Seçenekleri Paritesi:** Aniyomi MPV init profili uygulandı (`profile fast`, `ytdl no`, `keep-open yes`, `input-default-bindings`, `vd-lavc-film-grain cpu`, `msg-level warn`, `screenshot-directory Pictures`).
- **Özel Dosya ve String Desteği:** `SettingsDataStore` üzerinden kullanıcı tanımlı `mpv.conf` ve `input.conf` yapılandırmalarının doğrudan MPV oturumuna yüklenmesi sağlandı.

---

## 🇬🇧 English

### 🎬 Aniyomi Parity: Comprehensive Subtitle Customization Panel

Brings 100% subtitle setting parity with the Aniyomi video player into Kitsugi. Replaces the legacy 2-card popup with Aniyomi's 3-card tabbed pager architecture (Typography, Colors, Miscellaneous) backed by rich MPV and Media3 engine pipelines. Detailed plan in `PLAN_TASK_ANIYOMI_OYNATICI_PARITESI_2026-10-21.md`.

- **1. Typography Card (`TypographyCard`):**
  - **Bold / Italic Toggles:** Independent font weight and slant controls.
  - **Text Justification:** Left, Center, Right, and Auto alignment options.
  - **Font Selector:** 12 curated system fonts with automatic dynamic scanning of custom TTF fonts placed in `filesDir/fonts`.
  - **Font Size:** 1..100 continuous slider (default 55).
  - **Border / Outline Styles:** Outline-and-shadow, opaque-box, and background-box presets.
  - **Border Size & Shadow Offset:** 0..10 sliders with instant reset.
- **2. Colors Card (`ColorsCard`):**
  - **Multi-layer Color Targeting:** Target text, border, or background box.
  - **RGBA Channel Sliders:** Precise Red, Green, Blue, and Alpha (0..255) controls.
  - **Live Preview & MPV Injection:** Real-time color sample box mapped directly to MPV hex formatting (`sub-color`, `sub-border-color`, `sub-back-color`).
- **3. Miscellaneous Card (`MiscellaneousCard`):**
  - **ASS/SSA Override:** Toggle for strict style overriding (`sub-ass-override`).
  - **Scale (sub-scale):** 0.2..5.0 font scale slider.
  - **Position (sub-pos):** 0..150 window position slider.
  - **Vertical Offset:** Fine-grained -200..200 pixel adjustment and preferred language filter.
- **Responsive Layout:** `HorizontalPager` with 3-dot page indicators in portrait; vertical scrolling list in landscape.
- **Engine Parity:** Fully synced across MPV and Media3 engines (`Typeface` bold+italic, `CaptionStyleCompat` edge styles, SP-based size scaling, and persistent `SettingsDataStore` storage).

### ⚙️ MPV Init & Custom Config Support (mpv.conf / input.conf)

- **Initialization Parity:** Adopted Aniyomi MPV init parameters (`profile fast`, `ytdl no`, `keep-open yes`, `input-default-bindings`, `vd-lavc-film-grain cpu`, `msg-level warn`).
- **User Files Support:** Support for custom `mpv.conf` and `input.conf` files and string properties loaded directly via `SettingsDataStore`.

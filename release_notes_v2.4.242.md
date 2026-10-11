# Kitsugi v2.4.242-beta Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe (TR)

### 🎨 Eklentiler & Kaynaklar Sayfası Tema Uyumu ve Kompakt Arayüz

#### 1. 🌈 Tema Rengi ve Gradyan Uyumu
- **Üst Eklenti Sekmeleri:** Torrent & Akış, Video Kaynakları ve Manga Kaynakları sekmelerinin seçili durumu, kullanıcının ayarladığı özel tema rengi ve iki renkli degrade fırçası (`accentBrushWithAlpha`) ile entegre edildi.
- **Tek Tip Alt Sekme Seçicisi (`AddonSegmentedControl`):** Video ve Manga sayfalarındaki "Repolar" ve "Kurulu" geçişleri için ortak, modern ve kompakt bir seçici bileşeni oluşturuldu.
- **P2P ve Eklenti Anahtarları:** Torrent/P2P ayarları, eklenti açma/kapatma anahtarları, bölüm ikonları ve durum göstergeleri temayla tam uyumlu hale getirildi. Hata ve silme gibi semantik uyarı renkleri Material tema renklerine bağlanarak korundu.

#### 2. 📐 Kompakt ve Tutarlı Kart Tasarımı
- **Tutarlı Kenarlıklar & Boşluklar:** Eklenti ve repo kartlarındaki boşluklar, satır yükseklikleri ve ikon boyutları sadeleştirilerek taranabilirliği artırıldı.
- **Temalı Yedek İkonlar:** Eklenti veya repo simgesi bulunamadığında sarı emoji yerine tema vurgusunu taşıyan şık bir yedek ikon gösteriliyor.

---

## 🇬🇧 English (EN)

### 🎨 Addons & Sources UI Theme Alignment & Compact Layout

#### 1. 🌈 Accent Color & Gradient Consistency
- **Top Addon Tabs:** The active tab indicator across Torrent & Stream, Video Sources, and Manga Sources now dynamically adapts to the user's selected accent color and gradient brush (`accentBrushWithAlpha`).
- **Unified Sub-Tab Selector (`AddonSegmentedControl`):** Implemented a shared, compact, and polished segmented selector for switching between "Repositories" and "Installed" across both Video and Manga sources.
- **P2P & Extension Controls:** Switches, section headers, badges, and update actions now align with the current theme palette while preserving semantic error and delete styling.

#### 2. 📐 Compact Card Ergonomics
- **Consistent Borders & Spacing:** Streamlined card paddings, row heights, and icon sizes across extension rows and repository cards for better density and scannability.
- **Themed Fallback Icons:** Replaced plain emoji fallbacks with elegant, theme-accented placeholder icons when extension icons cannot be loaded.

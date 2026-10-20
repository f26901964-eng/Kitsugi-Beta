# Kitsugi Beta v2.4.318

## 🇹🇷 Türkçe

### 🎬 Kompakt & Adaptif Video Kaynak (Stream) Seçim Ekranı

Bu sürüm, çok eklenti kullanan kullanıcılar için video kaynak seçim ekranını (`KitsugiStreamScreen`) daha kompakt, aranabilir, tema uyumlu ve dikey/yatay kullanıma tam adaptif hale getirir. Ayrıntılı tasarım ve test notları: `docs/STREAM_SELECTION_UX.md`.

- **Dikey Kullanım Optimizasyonu:**
  - Aynı başlığı ve görseli tekrar eden büyük poster/başlık alanı kaldırıldı; sonuç alanı doğrudan görünür hale getirildi.
  - Eklenti gruplarına başlık ve canlı sonuç sayacı eklendi.
- **Yatay Kullanım (Landscape) Devrimi:**
  - Büyük poster alanı yerine genişliği sınırlandırılmış (240–300 dp) ve bağımsız kaydırılabilir bir kontrol paneli (sol ray) getirildi.
  - Sol kontrol paneli ile sağdaki video listesi birbirinden tamamen bağımsız olarak kaydırılabiliyor; kontroller ekran döndürüldüğünde dahi erişilebilir kalıyor.
- **Kompakt ve Sade Kaynak Satırları (`StreamCard`):**
  - Kartlardaki tekrarlayan posterler kaldırıldı; video adı, eklenti, kalite ve dil bilgisi en öne alındı.
  - Dosya bilgisi, ses/altyazı dilleri, dosya boyutu ve torrent bilgileri dokunulduğunda akıcı bir şekilde açılan ayrıntılar paneline (`AnimatedVisibility`) taşındı; ayrıntılara dokunmanın yanlışlıkla video başlatması engellendi.
  - Oynat ve indir eylemleri için standart 48 dp dokunma hedefleri ayrıldı.
- **Aranabilir Kaynak Seçim Paneli (`StreamScreenContent`):**
  - Eklenti şeridi, arama yapılabilen modal bir kaynak paneliyle değiştirildi.
  - Kaynak sayısı, sonuç adedi, yüklenme durumu, doğrulama ve hata durumları panelde anlık gösteriliyor.
- **Gelişmiş Sıralama & 4K Filtresi:**
  - Eklenti içinde kaliteleri yüksekten düşüğe sıralama desteği eklendi (bilinmeyen kaliteler en sona yerleştirilir).
  - 4K ve 1080p+ filtreleri netleştirildi.
- **Durum Koruma (`rememberSaveable`):**
  - Arama sorgusu, seçili etiket, sıralama tercihi ve seçilen eklenti filtresi ekran döndürüldüğünde (`orientation change`) eksiksiz olarak korunur.

---

## 🇬🇧 English

### 🎬 Compact & Adaptive Stream Selection Screen

This release delivers an overhauled, responsive, and compact stream selection experience (`KitsugiStreamScreen`) optimized for large add-on catalogs and seamless portrait/landscape adaptability. Detailed specifications in `docs/STREAM_SELECTION_UX.md`.

- **Portrait Layout Optimization:**
  - Eliminated redundant media poster and title headers to prioritize immediate stream result visibility.
  - Added grouped source headers with dynamic stream counts.
- **Landscape Control Rail:**
  - Replaced the full-width poster block with an independently scrollable, bounded control rail (240–300 dp) on the left.
  - Left control panel and right video results list scroll independently, keeping filter controls reachable across wide and ultra-wide screens.
- **Compact Stream Rows (`StreamCard`):**
  - Removed repetitive posters in favor of dense information rows highlighting video title, add-on source, quality badge, and language.
  - Moved secondary details (audio/subtitle tracks, file size, torrent cache state) into an expandable details section; tapping details will not accidentally trigger playback.
  - Provided dedicated 48 dp touch targets for play and download actions.
- **Searchable Source Picker (`StreamScreenContent`):**
  - Replaced horizontal chips with a searchable bottom sheet source picker.
  - Real-time visibility into source status: video count, loading indicators, error notices, and verification requirements.
- **Quality Sorting & 4K Filter:**
  - Introduced toggle for descending quality ordering within each add-on group, positioning unknown qualities at the bottom.
  - Clarified 4K and 1080p+ tag filtering.
- **State Preservation (`rememberSaveable`):**
  - Active search query, quality tags, sorting mode, and active add-on filter survive orientation changes and configuration reloads.

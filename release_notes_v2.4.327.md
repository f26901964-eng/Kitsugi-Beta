# Kitsugi Beta v2.4.327

## 🇹🇷 Türkçe

### 🎬 Detay Sayfası Video & Fragman Oynatıcısı Düzeltmesi
- **YouTube Veri Kaynağı & Başlık İyileştirmesi (`YoutubeChunkedDataSourceFactory.kt`):**
  - YouTube akış isteklerine tarayıcı uyumlu `User-Agent` ve `Referer: https://www.youtube.com/` başlıkları eklendi; başlık eksikliğinden kaynaklanan engelleme ve hız sınırlandırmaları (throttle) engellendi.
  - Parçalı akış URL'lerinde mevcut `range` parametresi temizlenerek yinelenen aralık parametresi sorunu giderildi; videonun `00:00` süresinde takılı kalması çözüldü.
- **Oynatıcı Durum & Yeniden Deneme Arayüzü (`KitsugiDetailPlayerComponents.kt`):**
  - Oynatıcı yükleme durumuna açıklayıcı görsel gösterge ve metin eklendi.
  - Kaynak çekilemediğinde kullanıcıya doğrudan "Yeniden Dene" ve "YouTube'da Aç" seçenekleri sunuldu.

### 🎨 Listem Görünümleri & Ek Kaynak Rozetleri Uyumu
- **5 Görünümde Bütünleşik Ek Kaynak Şeridi (`KitsugiMediaEntryCard.kt` & `LibrarySourceBadgeStrip.kt`):**
  - Kompakt, Rahat, Büyük, Minimalist ve Izgara görünümlerinde ek kaynak rozetleri kart dışına taşan yapıdan çıkarılıp kartın içindeki ortak alt şeride entegre edildi.
  - Orijinal kaynak logoları ve renkleri korunurken ana kaynakla ve birbiriyle tekrarlayan rozetler temizlendi (`LibraryBadgeSources.kt`).
  - Büyük harf ve kaynak adı dönüşümleri Türkçe yerel ayarlardan (`Locale.ROOT`) bağımsızlaştırılarak kararlı hale getirildi.
- **Tutarlı Durum ve Puan Kapsülleri:**
  - Tüm liste görünümlerinde durum rozetleri opak, tema uyumlu ve ince çerçeveli ortak kapsül tasarımına kavuşturuldu; poster renk karmaşasının metin okunurluğunu bozması engellendi.
  - Puan kapsülü ortaklaştırılarak puan gizleme ve biçim tercihleriyle tam uyumlu hale getirildi.
- **Izgara Hizalaması ve Çoklu Satır Desteği (`MyListGridContent.kt`):**
  - Izgara başlık alanı iki satıra sabitlendi; aynı satırdaki kartların yükseklikleri eşitlenerek alt rozet şeridi hizalandı.
  - Dar ekranlarda rozet şeridi ve durum satırları esnek şekilde satır atlayarak taşma yapmaz.

### 📑 Listem Sıralama & Detay Galerisi Bottom Sheet Dönüşümü
- **Listem Sıralama Menüsü Bottom Sheet (`KitsugiMyListSortMenu.kt`):**
  - Eski açılır menü yerine modern, ergonomik ve yuvarlak köşeli `ModalBottomSheet` yapısına geçildi.
  - "Son eklenen" dahil olmak üzere tüm sıralama kriterleri ve sıralama yönü (artan/azalan) alttan açılan şık panel üzerinden seçilebiliyor.
- **Detay > Resimler Kategori & Kaynak Seçici Bottom Sheet (`EntryDetailTabContents.kt`):**
  - Resimler sekmesindeki kategori seçici modern bir bottom sheet'e dönüştürüldü.
  - Görsel kategorileri (Afiş, Fanart, Logo, Banner vb.) ile birlikte MAL, Fanart.tv, TMDB gibi kaynaklar ve görsel adetleri ayrı rozetler halinde listeleniyor.
  - Galeri görünümünde görseller kaynaklarına göre gruplandı; dokunulan kaynağın görselleri doğrudan galeride açılıyor.

### 🌐 Bangumi Bildirimleri Başlık Dili Çözümlemesi
- **Dil Uyumu & Dinamik Güncelleme (`KitsugiNotificationsViewModel.kt`):**
  - Bangumi izleme bildirimlerindeki Japonca/Çince başlıklar kullanıcının tercih ettiği başlık diline (`ROMAJI` veya `ENGLISH`) göre `BangumiTitleCache` ve `BangumiSubjectTitleResolver` ile çözümleniyor (Profil ve Listem sekmeleriyle tam parite).
  - Ayarlar ekranından başlık dili değiştirildiğinde, bildirimler ekranı açıksa başlıklar otomatik olarak yeniden yüklenir.

---

## 🇬🇧 English

### 🎬 Detail Page Video & Trailer Player Fixes
- **YouTube Data Source & Header Parity (`YoutubeChunkedDataSourceFactory.kt`):**
  - Added browser-like `User-Agent` and `Referer: https://www.youtube.com/` headers to prevent YouTube streaming requests from being throttled or rejected.
  - Strips existing `range` query parameters before appending chunk offsets, eliminating the stalled `00:00` playback issue.
- **Player State & Retry UI (`KitsugiDetailPlayerComponents.kt`):**
  - Clear loading feedback and retry actions ("Yeniden Dene" / "Open in YouTube") when source extraction fails.

### 🎨 MyList Views & Additional Source Badges Harmonization
- **Unified In-Card Source Strip Across 5 Views (`KitsugiMediaEntryCard.kt` & `LibrarySourceBadgeStrip.kt`):**
  - Extra source badges across Compact, Comfortable, Large, Minimalist, and Grid views are now housed inside the card within a unified, theme-aware bottom strip.
  - Preserved original source brand colors while deduplicating entries and suppressing the primary source badge (`LibraryBadgeSources.kt`).
  - Locale-safe badge key parsing (`Locale.ROOT`) prevents Turkish locale edge cases.
- **Consistent Status and Score Capsules:**
  - Standardized status badges into crisp, high-contrast, theme-toned capsules unaffected by varied poster colors.
  - Harmonized score capsule across all five layouts, honoring user score display preferences.
- **Grid Alignment & Wrapping (`MyListGridContent.kt`):**
  - Fixed 2-line title bounds with equalized card heights in the same row, aligning source badge strips cleanly.
  - Graceful wrapping for badges and metadata in tight viewports.

### 📑 MyList & Detail Gallery BottomSheet Redesign
- **MyList Sort Options BottomSheet (`KitsugiMyListSortMenu.kt`):**
  - Replaced legacy popup dropdown with a modern, rounded `ModalBottomSheet`.
  - Supports all sorting keys (including "Recently Added") and ascending/descending directions.
- **Detail Gallery Category & Source Picker BottomSheet (`EntryDetailTabContents.kt`):**
  - Transformed the gallery category selector into an interactive `ModalBottomSheet`.
  - Displays image categories alongside provider badges (MAL, Fanart.tv, TMDB) and asset counts per provider.
  - Images are grouped by provider under each category; tapping a source launches the filtered gallery view.

### 🌐 Bangumi Notifications Title Language Resolution
- **Title Language Parity & Dynamic Refresh (`KitsugiNotificationsViewModel.kt`):**
  - Resolves Bangumi notification titles using `BangumiTitleCache` and `BangumiSubjectTitleResolver` to match the user's title language preference (`ROMAJI` / `ENGLISH`).
  - Reactively reloads notification titles when title language is toggled in App Settings.

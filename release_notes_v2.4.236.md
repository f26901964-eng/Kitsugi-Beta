# Kitsugi Beta v2.4.236 — Depolama Şişmesi (~4 GB), Performans, Donma & Dil Düzeltmeleri

## 🇹🇷 Türkçe (v2.4.236)

### 🗄️ 1. Merkezî Depolama Bekçisi (`StorageJanitor`) & Depolama Ayarları (~4 GB Şişme Çözümü)
- **Kök Neden Çözüldü:** P2P torrent önbelleği (2 GB), görsel önbelleği (512 MB), manga önbelleği (~150 MB), video önizlemeleri ve sınırsız büyüyen veritabanı kayıtları merkezî bir temizlik mekanizması olmadığı için kurulumu ~4 GB'a kadar şişiriyordu.
- **Merkezî Bekçi (`StorageJanitor` & `StorageMaintenanceWorker`):** Disk kullanımını kalem kalem ölçen, bütçe ve yaş kurallarına göre sessizce budayan sistem eklendi. Ağ şartı olmayan günlük WorkManager bakımı ve 6 saatte bir hafif açılış bakımı devreye alındı.
- **Ayarlar > Depolama Ekranı (`StorageSettingsContent`):** Kullanıcıların disk kullanımını kalem kalem görmesini ve tek dokunuşla güvenli önbellekleri temizlemesini sağlayan modern depolama paneli eklendi (hesap, indirmeler ve geçmiş asla silinmez).
- **Atıl Temizlik Kodları Canlandırıldı:** Mobil veride çalışmayan detay önbelleği budaması koşulsuz hale getirildi; `video_cache` 30 gün, `translation_cache` 5.000 satır ve arama geçmişi 500 kayıt tavanıyla sınırlandı. Altyazı arşivleri 14 günde bir temizleniyor.

### ⚡ 2. Kasma ve Donma Zinciri Kırıldı (Kare Gecikmesi %96 İyileştirildi)
- **Manga Okuyucu Tarama Yükü Kaldırıldı:** Her manga sayfası yazıldığında tüm klasörün taranıp boyut toplanması yerine süreç genelinde paylaşılan artımlı sayaç getirildi.
- **Kare Gecikmesi ve Jank Azaltıldı:** Logcat/gfxinfo analizlerindeki 1200 ms'lik takılmalar giderildi; p99 kare süresi 1200 ms'den 46 ms'ye düşürüldü, takılma oranı %30'dan %8'e indirildi.
- **İç İçe Looper ve Disk Logcat Kaldırıldı:** `KitsugiApplication` içindeki ek ana looper döngüsü (`Looper.loop()`) ve arka planda diske sürekli yazan `logcat -f` süreci temizlendi.
- **Soğuk Açılış & Oynatma İlerlemesi:** İndirme listesi ve izleme geçmişinin ana iş parçacığında JSON ayrıştırması IO'ya taşındı; oynatma sırasındaki disk yazımları 2,5 sn'lik toplulaştırmaya alındı.
- **Degrade Shader & Brush Önbelleği (`GradientBrushCache`):** Her çizim karesinde yeni shader/fırça tahsisatı engellendi, AniList 429 rate limit bekleme koruması eklendi.

### 🛡️ 3. GPU RenderThread Çökme Koruması (`Modifier.safeBlur`)
- **Donanımsal Bulanıklık Güvenliği:** API 31 altındaki cihazlarda ve riskli GPU sürücülerinde `libhwui` SIGSEGV çökmelerine yol açan koşulsuz `.blur()` çağrıları `Modifier.safeBlur()` ile korumaya alındı; aşırı yarıçap değerleri sınırlandı.
- **Üst Üste Binen GPU Katmanları Temizlendi:** `KitsugiCinematicLoadingScreen` içindeki çift GPU bulanıklık katmanı teke indirildi; `MangaDetailScreen` ve eklenti diyaloglarındaki ağır bulanıklıklar optimize edildi.

### 🌐 4. Android 13+ Dil Değiştirme ve Çökme Düzeltmesi
- **Aktif Dil Senkronizasyonu (`locales_config.xml`):** Android 13+ `LocaleManager` için Türkçe ve İngilizce dilleri açıkça tanımlandı (`locales_config.xml`), manifest entegrasyonu tamamlandı. "İngilizce seçili ama Türkçe görünüyor" sorunu giderildi.
- **API 33+ Modern Dil API'si:** Dil geçişi `AppCompatDelegate.setApplicationLocales()` ile modernize edildi; dil değişimi sonrası çağrılan `recreate()` kuyruğun sonuna alınarak döngü ve çökme engellendi.
- **Dize Formatı Hataları Düzeltildi:** Dil dosyalarındaki hatalı yüzde formatları (`Normal (%0)` -> `Normal (0%)`) düzeltildi.

### 📐 5. Kart Düzeni, Metin Kesilmesi ve Geri Sayım Optimizasyonu
- **Rozet ve Başlık Kesilmesi Çözüldü:** 2 satırlı başlık, alt başlık ve sonraki bölüm rozetinin alt kısımdan taşmasını engellemek için portre kart metin alanı 182 dp'ye (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT`) yükseltildi.
- **10 Saniyelik Donma Döngüsü Kırıldı:** `NextAiringChip` içindeki her 10 saniyede bir ekranı tetikleyen sayaç 30–60 saniyeye çıkarıldı. `KitsugiUpcomingAirEnricher` iptal istisnaları (`CancellationException`) doğru yönetildi.

### 🧲 6. Yatay Satırlarda Akıcı Kart Yaslama (Snap Scrolling)
- **Sola Yapışan Kaydırma Geri Getirildi:** Keşfet vitrinleri, arama sonuçları, detay rafları ve profil sayfalarındaki yatay kart listelerine `KitsugiScrollDefaults.rememberRowSnapFling(state)` eklendi; kartlar kaydırma bitiminde sola akıcı ve muntazam biçimde oturuyor.

### 🔑 7. Liste Anahtarları (Key Collision) & "Tümünü Gör" Sonsuz Yükleme Düzeltmesi
- **Tür Bazlı Kimlik Ayrımı (`KitsugiLazyKeys.kt`):** Keşfet, arama ve ızgara listelerinde aynı sayısal kimliğe sahip anime ve mangaların çakışmasını engellemek için anahtarlara medya türü (`type`) dahil edildi.
- **Sonsuz İstek Döngüsü Sınırlandı:** `FullScreenMediaGridPage` sayfasında kullanıcı kaydırmadan tetiklenen sürekli sayfa çekme döngüsüne kaydırma başına bütçe eklendi.

---

## 🇬🇧 English (v2.4.236)

### 🗄️ 1. Central Storage Janitor (`StorageJanitor`) & Settings > Storage (~4 GB Bloat Fix)
- **Root Cause Resolved:** P2P torrent cache (2 GB), image cache (512 MB), manga cache (~150 MB), video previews, and unconstrained Room cache rows accumulated to ~4 GB installations due to lack of central lifecycle management.
- **Central Storage Janitor (`StorageJanitor` & `StorageMaintenanceWorker`):** Added a unified storage engine measuring disk usage per bucket, pruning data via age and size thresholds. Added network-unconstrained daily WorkManager jobs and 6-hour interval launch maintenance.
- **Settings > Storage Screen (`StorageSettingsContent`):** Added an interactive storage dashboard showing per-category sizes, item counts, and safe one-tap cleanup (user accounts, downloads, and history are strictly preserved).
- **Revived Dormant Cleanup Routines:** Unblocked detail cache pruning by removing Wi-Fi/battery-only restrictions; pruned `video_cache` beyond 30 days, capped `translation_cache` to 5,000 rows, limited search history to 500 rows, and auto-removed subtitle archives older than 14 days.

### ⚡ 2. Broken Jank Chains & Systemwide Frame Latency Fix
- **Manga Reader Cache Overhead Eliminated:** Switched from costly whole-directory walks on every written page to an incremental memory-backed byte counter.
- **96% Frame Latency Reduction:** Eliminated 1200ms frame stalls seen in logcat/gfxinfo; p99 latency dropped from 1200ms to 46ms, and jank rate fell from ~30% to 8.13%.
- **Removed Nested Looper & Disk Logcat:** Removed redundant nested `Looper.loop()` in `KitsugiApplication` and background `logcat -f` disk redirection.
- **Cold Start & Playback Progress:** Deferred download list and watch history JSON parsing off the main thread; debounced video playback watch progress writes to 2.5s with background flush.
- **Cached Gradient Shaders & Brushes (`GradientBrushCache`):** Cached linear gradients and draw node brushes, stopping continuous per-frame shader allocations. Added AniList 429 rate limit cooldown.

### 🛡️ 3. GPU RenderThread Crash Safety (`Modifier.safeBlur`)
- **Hardware Blur Guard:** Replaced unconditional `.blur()` modifiers across screens with `Modifier.safeBlur()`, skipping blur on API < 31 and clamping blur radius to prevent `libhwui` RenderThread SIGSEGV crashes on vulnerable GPUs.
- **Eliminated Redundant GPU Layers:** Removed nested double GPU blur pass in `KitsugiCinematicLoadingScreen`; optimized heavy blur layers in `MangaDetailScreen` and addon dialogs.

### 🌐 4. Android 13+ Per-App Language Switching & Crash Fix
- **Explicit Locale Configuration (`locales_config.xml`):** Declared English and Turkish locales in `res/xml/locales_config.xml` and manifest to satisfy Android 13+ `LocaleManager` requirements, resolving "English selected but Turkish shown" issues.
- **Modernized Locale API for API 33+:** Switched to `AppCompatDelegate.setApplicationLocales()`. Deferred activity `recreate()` call via main looper handler post to prevent activity recreation crashes.
- **Fixed Malformed String Templates:** Corrected invalid percentage token placements in localized resources (`Normal (%0)` -> `Normal (0%)`).

### 📐 5. Card Layout, Text Clipping & Countdown Timer Optimization
- **Badge & Title Overflow Fix:** Increased portrait card text container height to 182dp (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT`), ensuring two-line titles, subtitles, and next-airing chips render cleanly without clipping.
- **Eliminated 10-Second Frame Freeze:** Re-tuned `NextAiringChip` ticker interval from 10s to 30-60s. Handled `CancellationException` properly in `KitsugiUpcomingAirEnricher` to avoid negative caching loops.

### 🧲 6. Horizontal Shelf Snap Scrolling
- **Restored Left-Edge Snap Fling:** Added `KitsugiScrollDefaults.rememberRowSnapFling(state)` (`SnapPosition.Start`) across Explore shelves, search rows, detail recommendations, and profile collections.

### 🔑 7. Lazy Column/Row Key Collision & Infinite Load Loop Fix
- **Type-Aware Identity Keys (`KitsugiLazyKeys.kt`):** Included media type (`type`) in Compose item keys across 11 shelves and grids, preventing collision when anime and manga share the same ID.
- **Bounded Pagination Budget:** Fixed continuous unprompted pagination in `FullScreenMediaGridPage` by requiring active scroll gestures.

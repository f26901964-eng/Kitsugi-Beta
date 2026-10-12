# Kitsugi-Beta v2.4.260 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🧭 1. Ortak Üst Sekme Bandı (Görünüm & Tercihler Mimarisi)
- "Ayarlar > Görünüm & Tercihler" bandı ve yatay kaydırma mantığı ortak bileşene taşındı: `KitsugiSettingsSegmentedTabs`.
- **Oynatıcı Ayarları:** Ana dikey menü listesi kaldırıldı; 8 bölüm (Dahili oynatıcı · Hareketler · Kod çözücü · Alt yazılar · Ses · Özel butonlar · Kod düzenleyici · Gelişmiş) artık **kaydırmalı sekme** ve `HorizontalPager` ile yönetiliyor. Her sekmenin kendi kaydırma konumu korunur.
- **İndirmeler:** Videolar / Altyazılar / Resimler bandı segmentli ortak banda dönüştürüldü.
- **Platform Hata Kodları:** Filtre çipleri yerine 11 sekmeli bant (Tümü, Manga, CloudStream, Genel, AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl); her kategori yalnızca kendi kodlarını listeler ve sekmelerde platformların orijinal logoları yer alır.
- **Hesap Bağlantıları:** Platformlar ve Senkronizasyon sekmeleri ayrıldı.
- **Duyarlı Tasarım (Responsive):** Sekmeler ekrana sığıyorsa eşit bölünür; sığmıyorsa bant kendi içinde yatay kaydırılır. Seçili/odaklanan sekme otomatik ortalanır ve kenar solma efektleri uygulanır. TV'de D-pad odak takibi desteklenir.

### 🔢 2. İlerleme Sayacı: Büyütülmüş Dokunma Alanı ve Doğrudan Değer Girişi
- Manga ve anime detay sayfalarındaki bölüm/bölüm ilerleme sayacında sayıya dokunarak doğrudan değer girilebilmesi için dokunma hedefi en az `48dp × 32dp` boyutuna büyütüldü.
- Sayı alanının üstünde tıklamayı yutan etkisiz `tvClickable` katmanı kaldırıldı.

### 🔲 3. Yakında Yayında ve Sezonluk Kart Çerçeveleri
- Keşfet sekmesindeki yatay "Yakında Yayında" kartına ve yer tutucusuna `KitsugiColors.Border` ile görünür çerçeve eklendi.
- Detaylı sezonluk medya kartlarında varsayılan çerçeve geri getirildi; listede olan kartın yeşil vurgusu korundu.

### 👤 4. Profil ve Listem: Birleşik Bağlantısız Hesap Durumu
- `KitsugiAccountNotConnectedState` ortak bileşeni oluşturuldu.
- AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi için Profil sekmesindeki logo, başlık, açıklama ve "Hesabı Bağla" düzeni Listem sekmesiyle birebir eşitlendi.
- Süresi dolmuş Simkl oturumu Listem'de yeniden bağlanma metni ve butonuyla gösteriliyor.

### 🔔 5. Bildirim Ayarları ve Hesap Bağlantı Kapıları
- Kaynağa özel bildirim anahtarları (AniList, MAL, Simkl, Kitsu, Shikimori) ilgili hesap bağlanmadan önce kapalı ve devre dışı kalır.
- Genel "Yayın Bildirimleri" en az bir desteklenen hesabın bağlı olmasını zorunlu kılar.
- `NotificationScheduler.reconcile` arka plan işi, etkin bildirimleri gerçek kimlik bilgileriyle eşleştirir; hesapsız durumlarda periyodik işleri iptal eder.

### 🎯 6. Vitrin Tıklaması ve Sayfa Katmanı İyileştirmesi
- Ana sayfa vitrininde pager sayfaları arasında görsel olarak önde olan sayfanın tıklamayı doğru alması sağlandı (`HomeHeroGestureModifiers`).
- Çift dokunmayla başlık kopyalama korunurken, tek dokunuş doğrudan ilgili yapımın detay sayfasını açar.

### 💀 7. Detay Ekranları: İçerikle Birebir Eşleşen Shimmer İskeletleri
- Detay sayfalarındaki tüm yükleme durumları içerikle eşleşen shimmer iskeletlerine dönüştürüldü: ilişkiler, öneriler, karakterler ve ekip, istatistikler, bölümler, galeri, puanlar, incelemeler ve sosyal akış.
- Dikey ve yatay ekran yönelimine tam uyum; TV detay dialoglarındaki ana yükleme durumları da içerik biçiminde gösteriliyor.
- Shimmer bloklarına animasyon geçişi sırasında görünür kalan kalıcı bir taban yüzeyi eklendi.

---

## 🇬🇧 English

### 🧭 1. Unified Segmented Top Tab Bar
- Migrated the "Settings > Appearance & Preferences" tab bar into a shared responsive component: `KitsugiSettingsSegmentedTabs`.
- **Player Settings:** Removed vertical menu list; replaced with 8 swipeable sections via `HorizontalPager` (Internal player · Gestures · Decoder · Subtitles · Audio · Custom buttons · Code editor · Advanced), retaining individual scroll states.
- **Downloads:** Replaced standard TabRow with segmented tab bar (Videos / Subtitles / Images).
- **Platform Error Codes:** 11 segmented tabs with authentic platform logos; categories strictly isolate their own error codes.
- **Account Connections:** Split into Platforms and Synchronization tabs.
- **Responsive & TV Friendly:** Auto-distributes when tabs fit; scrolls horizontally when constrained with edge fading. TV D-pad focus auto-scrolls selected tabs into view.

### 🔢 2. Progress Counter Direct Input & Expanded Touch Target
- Expanded progress counter touch targets to at least `48dp × 32dp` across anime and manga editors for instant keyboard input.
- Removed passive interceptor clickable modifier that previously blocked numerical focus.

### 🔲 3. Upcoming & Seasonal Card Borders
- Added visible `KitsugiColors.Border` outline to Explore horizontal "Airing Soon" cards and placeholders.
- Restored default border for detailed seasonal cards while preserving list-membership highlights.

### 👤 4. Unified Not-Connected State in My List & Profile
- Introduced `KitsugiAccountNotConnectedState` across My List and Profile for AniList, MAL, Simkl, Kitsu, Shikimori, and Bangumi.
- Aligned logos, titles, descriptions, and "Connect Account" action buttons.
- Display expired Simkl session prompt in My List.

### 🔔 5. Notification Gates & Account Preconditions
- Source-specific notification toggles are disabled and turned off when their respective accounts are unlinked.
- Global Airing Notifications require at least one linked account.
- `NotificationScheduler.reconcile` cancels background workers when no eligible accounts are authenticated.

### 🎯 6. Home Hero Click & Layering Refinement
- Visually dominant hero page in the carousel reliably captures clicks via `HomeHeroGestureModifiers`.
- Single tap navigates to media details while double tap preserves title copying.

### 💀 7. Detail Screens: Content-Matching Shimmer Skeletons
- Replaced generic spinners across detail tabs with content-matching shimmer skeletons: relations, recommendations, characters & staff, stats, episodes, gallery, scores, reviews, and social feed.
- Fully adaptive across portrait, landscape, and TV dialogs with persistent surface background during sweep transitions.

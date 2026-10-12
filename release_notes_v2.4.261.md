# Kitsugi-Beta v2.4.261 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🎌 1. Bangumi: Listeye Ekleme / Güncelleme Düzeltildi (HTTP 415 Çözümü)
- **Belirti:** Bangumi kaynaklı "Listeye Ekle", durum/puan güncellemesi ve bölüm ilerlemesi istekleri `HTTP 415 Unsupported Media Type — request with body must set "content-type" header to "application/json"` hatasıyla reddediliyordu.
- **Kök Neden:** Bangumi sunucusu `Content-Type` başlığını tam dize eşleşmesiyle kontrol eder (`application/json` veya `application/json; charset=UTF-8`). OkHttp `String.toRequestBody()` çağrısında küçük harfli `; charset=utf-8` parametresi ekliyor ve elle verilen başlığı eziyordu.
- **Düzeltme:** `BangumiApiClient.buildJsonRequestBody()` metodu ham UTF-8 bayt dizisi üzerinden tam `application/json` başlığıyla inşa edildi. Tüm koleksiyon, bölüm ve yazma sondası istekleri tek noktadan düzeltildi.
- **Tanı ve Test:** 415 hataları için özel tanı metinleri eklendi ve `BangumiApiClientWriteTest` birim testi ile doğrulandı.

### 🎭 2. Oyuncu ve Kişi Detayı: Galeri Yükleme Animasyonu & Vitrin Tıklaması
- **Galeri Butonu Yükleme Durumu:** Ek görseller yüklenirken galeri butonu artık gizlenmiyor; yüklenme sürecini gösteren dönen animasyon (`KitsugiGalleryIconButton`) yer alıyor.
- **Vitrin Görseli Tıklaması:** Vitrin fotoğrafına diğer görseller henüz yüklenirken de dokunulabiliyor. Galeri mevcut görselle hemen açılıyor ve arka planda yeni görseller geldikçe liste dinamik olarak güncelleniyor.
- **Duyarlı Kontroller:** Dikey ve yatay ekran modlarında galeri kontrolleri optimize edildi (`StaffGalleryItems`).

### 💬 3. Yorum ve İnceleme Profilleri: Platform Bazlı Yönlendirme & Sosyal Önbellek
- **Doğru Platform Yönlendirmesi:** Yorum ve inceleme kartlarındaki profil tıklamalarında kaynak (`source`) ayrımı yapıldı:
  - `mal` / `jikan` → Uygulama içi yerel MAL profili
  - `kitsu` → Uygulama içi yerel Kitsu profili
  - `simkl` → Uygulama içi yerel Simkl profili
  - `anilist` → Uygulama içi yerel AniList profili
  - `TMDB` / `Shikimori` / `Bangumi` → İlgili platformun web profili
- **PlatformUserProfileScreen:** Yeni kullanıcı profili görüntüleyici ile harici kullanıcıların istatistik, favori, galeri ve arkadaş verileri gösteriliyor.
- **Sosyal Akış Önbelleği:** `SocialFeedCache` ile incelemeler ve forum yorumları önbelleğe alınarak sayfalar arası geçişlerde ve geri dönüşlerde sayfa/scroll konumu korunuyor.

### 🛡️ 4. Profil Sayfası: Yükseklik Çökmesi Giderildi & Başlangıç Sekmesi İyileştirmesi
- **Sınırsız Yükseklik Hatası Çözümü:** `FavoritesExpandedBottomSheet` içindeki lazy grid için sınırlandırılmış yükseklik (`height(favGridHeight)`) tanımlanarak Jetpack Compose sınırsız yükseklik çökmesi önlendi.
- **Null Güvenliği:** Profil ve karakter detay sayfalarındaki potansiyel null işaretçi (`!!`) force-unwrap kullanımları güvenli çağrılara dönüştürüldü.
- **Başlangıç Sekmesi Yarış Durumu:** DataStore'dan son kullanılan sekme okunurken yaşanan yarış durumu düzeltildi; uygulama açılışında son kullanılan sekme tutarlı şekilde geri yükleniyor.

### 🖼️ 5. Detay Vitrini: Dinamik Poster / Backdrop Seçimi
- `HeroArtworkPreference` (POSTER / BACKDROP) ve `resolveHeroArtworkPreference(screenWidthDp, screenHeightDp)` mimarisi eklendi.
- Yatay ekranlar ve dar/uzun telefonlar için (oran ≤ 0.55) tam dikey poster önceliği; tablet ve geniş ekranlar için yatay arka plan optimizasyonu getirildi.

### 🔔 6. MyAnimeList: Web Oturumu Destekli Yerel Bildirimler
- WebView çerez deposu üzerinden MAL bildirimlerini çekebilen `MalNotificationsClient` ve `MalWebSessionStore` altyapısı entegre edildi.
- MAL bildirimleri uygulama içindeki yerel bildirim listesinde görüntülenip okunma durumları güncellenebiliyor.

---

## 🇬🇧 English

### 🎌 1. Bangumi: Add to List / Updates Fixed (HTTP 415 Solution)
- **Symptom:** Bangumi "Add to list", status/score updates, and episode progress writes failed with `HTTP 415 Unsupported Media Type — request with body must set "content-type" header to "application/json"`.
- **Root Cause:** Bangumi's server checks `Content-Type` using exact string equality (`application/json` or `application/json; charset=UTF-8`). OkHttp's `String.toRequestBody()` injected lowercase `; charset=utf-8` and overrode explicit headers.
- **Fix:** `BangumiApiClient.buildJsonRequestBody()` now builds requests from raw UTF-8 bytes with exact `application/json` content type. All collection, episode, and probe endpoints are fixed.
- **Diagnostics & Testing:** Added distinct 415 diagnostic messages and verified with `BangumiApiClientWriteTest`.

### 🎭 2. Voice Actor & Staff Detail: Gallery Loading Spinner & Showcase Tap
- **Loading Spinner:** Extra artwork loading no longer hides the gallery button; an active loading indicator (`KitsugiGalleryIconButton`) is displayed.
- **Showcase Image Interaction:** Showcase hero image is clickable even while extra images are loading. The gallery opens instantly with the current photo and appends new images dynamically as they arrive (`StaffGalleryItems`).
- **Responsive Controls:** Updated gallery controls across both portrait and landscape screen modes.

### 💬 3. Comment & Review Profiles: Platform-Aware Routing & Social Cache
- **Accurate Routing:** Profile clicks on comments and reviews now respect the entry's `source`:
  - `mal` / `jikan` → In-app native MAL profile
  - `kitsu` → In-app native Kitsu profile
  - `simkl` → In-app native Simkl profile
  - `anilist` → In-app native AniList profile
  - `TMDB` / `Shikimori` / `Bangumi` → Platform web profile via browser
- **PlatformUserProfileScreen:** View external user statistics, favorites, galleries, and friends natively.
- **SocialFeedCache:** Caches reviews and forum topics so sheet state and scroll positions are retained when returning from user profiles.

### 🛡️ 4. Profile Screen: Unbounded Height Crash Fix & Initial Tab Restoration
- **Height Constraint Fix:** Wrapped `FavoritesExpandedBottomSheet` lazy grid with explicit bounded height (`height(favGridHeight)`), preventing unbounded height Compose runtime crashes.
- **Null Safety:** Cleaned up unsafe force unwraps (`!!`) across profile and character sheets to safe calls.
- **Startup Race Condition:** Fixed race condition where initial default DataStore emissions overwrote `lastUsedTab` before stored settings finished loading.

### 🖼️ 5. Detail Hero: Dynamic Poster vs. Backdrop Preference
- Introduced `HeroArtworkPreference` (POSTER / BACKDROP) and `resolveHeroArtworkPreference(screenWidthDp, screenHeightDp)`.
- Automatically selects vertical poster for narrow portrait devices (ratio ≤ 0.55) and landscape orientations, reserving backdrops for tablets and wide screens.

### 🔔 6. MyAnimeList: Web Session Native Notifications
- Integrated `MalNotificationsClient` and `MalWebSessionStore` using in-app WebView session cookies.
- Fetch and display MAL notifications natively in-app with read status updates.

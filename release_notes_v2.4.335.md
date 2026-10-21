# Kitsugi Beta v2.4.335

## 🇹🇷 Türkçe

### 🔔 MyAnimeList (MAL) Bildirimlerinin Devamı & Sayfalama Kök Neden Onarımı
- **`mal_web_` Kimlik Öneki Uyuşmazlığı Giderildi (`KitsugiNotificationsViewModel.kt` & `MalNotificationsClient.kt`):**
  - `loadMalMore()` önceki bildirimleri `mal_web_<id>` önekiyle, `MalNotificationsClient` ise ham `<id>` ile karşılaştırdığı için aday sayfaların tamamı "yeni kayıt" sanılıyor, ilk 25 bildirim sonsuz döngüde tekrar çekilip eleniyor ve gerçek devam motoruna hiç ulaşılamıyordu. `normalizeKnownIds` / `rawNotificationId` ile kimlik eşlemesi tekilleştirildi ve ilerleme kilidi eklendi.
- **Çok Katmanlı Devam Motoru (`MalNotificationsWebPager.kt` & `MalNotificationsClient.kt`):**
  - `window.MAL.notification` dışında canlı `XMLHttpRequest` / `fetch()` yanıtları, Vue 2/3 bileşen durumu (`$data`) ve `#content` içindeki `.notification-item` DOM satırları birlikte toplanır.
  - Üst kapsayıcı `<div>` yerine doğrudan `.notification-pagination` ve yaprak `"View more"` kontrolleri hedeflenir; çok sayfalı geçişlerde ara anlık görüntüler korunur.
- **Gerçek Kategori Sayıları & Arşiv Temizliği:**
  - `.notificaiton-category-nav` sol menü sayaçları (`Tümü`, `Yeni Bağlantılı Anime`, `Yayında`, `Arkadaşlık İstekleri`, `Profil Yorumları` vb.) ve kategori adresleri doğrudan okunur; ilk 25 kayıt ekrana çizilir çizilmez kalan bildirimler arka planda tamamlanıp yerel arşive kaydedilir.
  - Gerçek MAL web oturumu aktifken eski takvim yedek kayıtlarının `Tümü` sayısını şişirmesi engellendi.

---

## 🇬🇧 English

### 🔔 MyAnimeList (MAL) Notification Continuation & Pagination Root-Cause Fix
- **Fixed `mal_web_` ID Prefix Mismatch (`KitsugiNotificationsViewModel.kt` & `MalNotificationsClient.kt`):**
  - `loadMalMore()` previously passed `mal_web_<id>` IDs while `MalNotificationsClient` compared raw `<id>` values, causing page-1 duplicates to pass unseen checks in a loop without reaching continuation batches. Normalized all ID checks via `rawNotificationId` / `normalizeKnownIds` with strict progress guards.
- **Multi-Source Continuation Collector (`MalNotificationsWebPager.kt` & `MalNotificationsClient.kt`):**
  - Captures notifications from live `XMLHttpRequest` / `fetch()` payloads, Vue 2/3 reactive component state, `window.MAL.notification`, and rendered `.notification-item` HTML elements, while targeting leaf `.notification-pagination` / `"View more"` controls accurately.
- **Accurate Sidebar Category Counts & Archive Hygiene:**
  - Parses exact category totals and URLs from `.notificaiton-category-nav`, auto-completes older notification batches right after rendering the initial page, and purges stale calendar fallback archive rows when an authenticated MAL web feed is active.

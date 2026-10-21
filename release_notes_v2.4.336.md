# Kitsugi Beta v2.4.336

## 🇹🇷 Türkçe

### 🔔 MyAnimeList (MAL) Bildirimlerinin Devamı & Sayfalama Kök Neden Onarımı
- **`mal_web_` Kimlik Öneki Uyuşmazlığı Giderildi (`KitsugiNotificationsViewModel.kt` & `MalNotificationsClient.kt`):**
  - `loadMalMore()` önceki bildirimleri `mal_web_<id>` önekiyle, `MalNotificationsClient` ise ham `<id>` ile karşılaştırdığı için aday sayfaların tamamı "yeni kayıt" sanılıyor, ilk 25 bildirim sonsuz döngüde tekrar çekilip eleniyor ve gerçek devam motoruna hiç ulaşılamıyordu. `normalizeKnownIds` / `rawNotificationId` ile kimlik eşlemesi tekilleştirildi ve sıfır ilerleme kilidi (`mergedUnfiltered.size > beforeCount`) eklendi.
- **Çok Katmanlı Devam Motoru (`MalNotificationsWebPager.kt` & `MalNotificationsClient.kt`):**
  - `window.MAL.notification` dışında canlı `XMLHttpRequest` / `fetch()` yanıtları, Vue 2/3 bileşen durumu (`__vue__.$data`) ve `#content` içindeki `.notification-item` DOM satırları birlikte toplanır.
  - Üst kapsayıcı `<div>` yerine doğrudan `.notification-pagination` ve yaprak `"View more"` kontrolleri hedeflenir; çok sayfalı geçişlerde ara anlık görüntüler korunur.
- **Gerçek Kategori Sayıları & Arşiv Temizliği:**
  - `.notificaiton-category-nav` sol menü sayaçları (`Tümü: 45`, `Yeni Bağlantılı Anime: 39`, `Yayında: 4`, `Arkadaşlık İstekleri: 1`, `Profil Yorumları: 1` vb.) ve kategori adresleri doğrudan okunur; ilk 25 kayıt ekrana çizilir çizilmez kalan bildirimler arka planda tamamlanıp yerel arşive kaydedilir.
  - Gerçek MAL web oturumu aktifken eski takvim yedek kayıtlarının (`25 + 3 = 28`) `Tümü` sayısını şişirmesi `retainOldWhere` ile temizlendi.
- **Oynatıcı Zaman Damgası İyileştirmesi (`MpvPlayerEngine.kt`):**
  - `lastPositionNotifyAtMs = -1L` ile sıfırıncı saniyede ilk oynatma zaman damgası bildirimi garantilendi.

---

## 🇬🇧 English

### 🔔 MyAnimeList (MAL) Notification Continuation & Pagination Root-Cause Fix
- **Fixed `mal_web_` ID Prefix Mismatch (`KitsugiNotificationsViewModel.kt` & `MalNotificationsClient.kt`):**
  - `loadMalMore()` previously passed `mal_web_<id>` IDs while `MalNotificationsClient` compared raw `<id>` values, causing page-1 duplicates to pass unseen checks in an endless loop without reaching continuation batches. Normalized all ID checks via `rawNotificationId` / `normalizeKnownIds` with strict progress guards (`mergedUnfiltered.size > beforeCount`).
- **Multi-Source Continuation Collector (`MalNotificationsWebPager.kt` & `MalNotificationsClient.kt`):**
  - Captures notifications from live `XMLHttpRequest` / `fetch()` payloads, Vue 2/3 reactive component state (`__vue__.$data`), `window.MAL.notification`, and rendered `#content .notification-item` HTML elements, while targeting leaf `.notification-pagination` / `"View more"` controls accurately.
- **Accurate Sidebar Category Counts & Archive Hygiene:**
  - Parses exact category totals and URLs from `.notificaiton-category-nav` (`All: 45`, `Related Anime: 39`, `Airing: 4`, etc.), auto-completes older notification batches right after rendering the initial page, and purges stale calendar fallback archive rows when an authenticated MAL web feed is active.
- **Player Timestamp Fix (`MpvPlayerEngine.kt`):**
  - Initialized `lastPositionNotifyAtMs = -1L` so that the initial playback position notification is reliably fired.

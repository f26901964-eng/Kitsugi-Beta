# Kitsugi Beta v2.4.326

## 🇹🇷 Türkçe

### 🔔 MyAnimeList (MAL) Bildirimleri Web Uç Noktaları, Kategori Sistemi & "View More" Sayfalama

Bu sürümde, MyAnimeList bildirim sistemi (`myanimelist.net/notification`) resmi web arayüzü ile tam pariteye kavuşturuldu; 11 ayrı kategori filtresi, poster gösterimi ve sayfa yükleme mekanizması baştan aşağı yenilendi.

- **11 Kategori Filtreleme Sistemi (`MalNotificationCategory.kt`):**
  - MAL sol menüsüyle birebir uyumlu kategori desteği: **Tümü**, **Yeni Bağlantılı Anime**, **Yayında**, **Arkadaşlık İstekleri**, **Profil Yorumları**, **Blog**, **Forum Alıntıları**, **Bahsetmeler**, **Takip Edilen Konular**, **Kulüp Mesajları** ve **Ödeme**.
  - Her kategori `typeIdentifier` bazlı yerel filtreleme ile desteklenir; filtre değişiminde gereksiz ağ trafiği oluşturulmaz.
- **Web Uç Noktaları Tek Merkezde (`MalNotificationWebEndpoints.kt`):**
  - Sayfalama (`/notification?offset=20`), kategori parametreleri (`category=related_anime`) ve okundu işaretleme engelleme kuralı tek bir mimaride toplandı.
- **Poster Ayrıştırması & Sayfalama Düzeltmesi (`MalNotificationsClient.kt`):**
  - "Yeni Bağlantılı Anime" kartlarında generic zil simgesi yerine animeye ait gerçek afiş (`imageUrl` / `image_url` / `images.jpg.image_url`) gösterilir.
  - `hasNextPage` mantığı `items.size >= 20` olarak düzeltildi; sayfa sonlarında sonsuz döngü engellendi.
- **"View more" Tıklama Algoritması Yenilendi (`MalNotificationsWebPager.kt`):**
  - Yalnızca "load more" arayan eski regex genişletildi; "View more", "Load more", "Older", "Daha fazla" ve tüm DOM elemanları (`button`, `a`, `div`, `span`, `[role=button]`) taranarak çok sayfalı bildirimlerin (45+ bildirim) tamamının kesintisiz çekilmesi sağlandı.
- **Yenilenen Bildirim Arayüzü (`KitsugiNotificationsScreen.kt` & `KitsugiNotificationsViewModel.kt`):**
  - MAL sekmesine rozetli sayaçlı şık kategori çipleri (`MalFilterHeader`) eklendi.
  - Bildirim yoksa boş durum mesajı ve akıcı geçişler sağlandı.

---

## 🇬🇧 English

### 🔔 MyAnimeList (MAL) Notification Web Endpoints, Categories & "View More" Pagination

Aligns MyAnimeList notifications (`myanimelist.net/notification`) with full web parity, introducing 11 category filters, cover poster extraction, and robust "View more" multi-page loading.

- **11 Category Filtering Engine (`MalNotificationCategory.kt`):**
  - Complete parity with MAL's sidebar: All, Related Anime, Airing, Friend Requests, Profile Comments, Blog, Forum Quotes, Mentions, Watched Topics, Club Messages, and Payment.
  - Instant client-side filtering via `typeIdentifier` without redundant network roundtrips.
- **Unified Web Endpoints (`MalNotificationWebEndpoints.kt`):**
  - Centralized endpoint builder supporting offset-based pagination (`?offset=20`), category parameters, and read-receipt blocker.
- **Poster Extraction & Pagination Integrity (`MalNotificationsClient.kt`):**
  - Extracts key art from `imageUrl`/`image_url`/`images.jpg` payloads so related anime entries feature real anime posters instead of fallback bell icons.
  - Fixed `hasNextPage` condition to prevent runaway scroll loops.
- **Robust "View more" Click Handler (`MalNotificationsWebPager.kt`):**
  - Expands detection across localized text tokens ("View more", "Load more", "Show more", "Older", "Daha fazla") and element selectors (`button`, `a`, `div`, `span`, `[role=button]`), reliably unlocking 45+ notification backlogs.
- **Refined Notification UI (`KitsugiNotificationsScreen.kt` & `KitsugiNotificationsViewModel.kt`):**
  - Horizontal chip header with badge counters (`MalFilterHeader`) and graceful empty state feedback.

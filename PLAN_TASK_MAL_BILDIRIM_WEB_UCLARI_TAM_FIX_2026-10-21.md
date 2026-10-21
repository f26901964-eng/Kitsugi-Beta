# PLAN TASK — MAL Bildirim Web Uçları Tam Fixi (myanimelist.net/notification)

**Tarih:** 2026-10-21  
**Branch:** `arena/78f28994-kitsugi-beta`  
**Commit:** `060719d`  
**Referans Ekranlar:** `image-1.png` … `image-4.png` (45 bildirim, View All, sol menü sayıları)

---

## 1. Problem Özeti

- Kullanıcı MAL web hesabı bağlı olmasına rağmen uygulama bildirimleri eksik gösteriyordu (ilk 9'da kesiliyordu).
- `myanimelist.net/notification` sayfasındaki **45 bildirim** tam çekilemiyor, **View more** sayfalaması çalışmıyordu.
- Kategori filtresi yoktu — sol menüdeki `New Related Anime 39 / Now Airing 4` gibi ayrımlar uygulanamıyordu.
- `New Related Anime` kartlarında poster yoktu, sadece çan ikonu görünüyordu.
- `hasNextPage` her listede `true` dönüyordu → sonsuz yükleme hatası.

## 2. Kök Neden Analizi

| # | Dosya | Kök Neden |
|---|-------|-----------|
| 1 | `MalNotificationsWebPager.kt` | `clickLoadMore()` regex'i sadece `load more\|show more` arıyordu, MAL'ın düğmesi **View more** → hiç tıklanmıyor, sayfalama kırılıyor |
| 2 | `MalNotificationsClient.kt` | `hasNextPage = items.isNotEmpty()` → her sayfada var sanıyor, gerçek bitiş algılanamıyor |
| 3 | `MalNotificationsClient.kt` | `animeImageUrl` alanı yok → `imageUrl` sadece arkadaştan geliyor, anime bildirimlerinde poster null |
| 4 | `MalNotificationsClient.kt` | Offset tahmini yok → `?offset=20` denemesi yapılmıyor, sadece WebView'e düşülüyor |
| 5 | `KitsugiNotificationsViewModel.kt` | Kategori bilgisi tutulmuyor, filtre yok, `NotifItem` içinde `typeIdentifier` yok |
| 6 | `KitsugiNotificationsScreen.kt` | MAL sekmesinde filtre header'ı yok, kullanıcı 39/4 ayrımını göremiyor |

## 3. Çözüm — Oluşturulan Web Uç Noktaları

### 3.1 Yeni Dosyalar

**`MalNotificationCategory.kt`** — 11 kategori, sol menüyle birebir:
```
ALL (45)
NEW_RELATED_ANIME (39) → related_anime_add
NOW_AIRING (4)         → on_air
FRIEND_REQUESTS (1)    → friend_request*
PROFILE_COMMENTS (1)   → profile_comment
BLOG_COMMENTS (0)      → blog_comment
FORUM_QUOTES (0)       → forum_quote
USER_MENTIONS (0)      → user_mention_*
WATCHED_TOPIC_POSTS    → watched_topic_message
CLUB_MESSAGES          → club_mass_message_in_forum
PAYMENT (0)            → payment
```
Her biri `typeIdentifiers: Set<String>` + `webParam` taşır, `fromTypeIdentifier()` ile sınıflandırma.

**`MalNotificationWebEndpoints.kt`** — Tüm URL kuralları tek yerde:
```
GET  /notification
GET  /notification?offset=20
GET  /notification?category=related_anime
GET  /notification?category=on_air&offset=20
WebView: /notification (scroll + View more → window.MAL.notification)
BLOCK /notification/api/check-items-as-read.json
```
- `buildNotificationUrl(offset, category)`
- `filterByCategory(items, category)` — sunucu filtrelemese bile istemci tarafında doğru
- `countsByCategory(items)` — sol menü sayıları rozet olarak gösterilir
- `fetchAll() / fetchCategory() / fetchMore()` wrapper'ları

### 3.2 Değişen Dosyalar

#### `MalNotificationsClient.kt`
- `PAGE_SIZE = 20` eklendi, `hasNextPage` → `items.size >= PAGE_SIZE`
- `Notification` içine `animeImageUrl: String?` eklendi, `imageUrl` getter'ına dahil edildi
- `RelatedAnime` içine `imageUrl` eklendi
- `animeImage` ayrıştırma: `imageUrl / image_url / picture / images.jpg.image_url` fallback zinciri
- `fetchWithUrl(url)` eklendi
- `fetchMore(context, cookie, page, nextUrl, knownIds, category)` overload'u — önce explicit URL, sonra `?offset=` tahmini, sonra WebView

#### `MalNotificationsWebPager.kt`
- `clickLoadMore()` genişletildi: `button,a,div,span,[role=button]` + `view more|load more|show more|daha fazla|devam`
- `View more` tek başına `div` içindeyse de bulunuyor, parent click de tetikleniyor
- `offsetParent !== null` ile görünmeyen elemanlar eleniyor

#### `NotificationArchiveStore.kt`
- `ArchivedNotif` içine `typeIdentifier` + `categoryName` eklendi (varsayılan null → eski JSON bozulmaz)

#### `KitsugiNotificationsViewModel.kt`
- Import: `MalNotificationCategory`, `MalNotificationWebEndpoints`
- `NotifItem` içine `typeIdentifier`, `categoryName` eklendi
- MAL state için ham liste tutma: `malUnfilteredItems`, `malRawHasMore/Page/NextUrl`, `_malCategory: StateFlow`
- `setMalFilter()`, `getMalCategoryCounts()`, `applyMalFilter()`, `setMalStateWithFilter()` eklendi
- `loadMal()` → `setMalStateWithFilter()` kullanıyor
- `loadMalMore()` ham liste üzerinden `existingIds` ve `malRawPage` ile çalışıyor, merge sonrası filtre yeniden uygulanıyor

#### `KitsugiNotificationsScreen.kt`
- Import `MalNotificationCategory`
- MAL Page (1) → `Column { MalFilterHeader + SourceNotifPage }` olarak yeniden yazıldı
- `MalFilterHeader` composable'ı eklendi: yatay chip listesi + sayı rozeti, `counts` map'iyle
- Boş kategori için `mal_notif_filter_empty` mesajı

#### `strings.xml` / `values-en/strings.xml`
- 11 filtre etiketi + boş mesaj + hint eklendi (TR/EN)

## 4. Test Planı

1. MAL web oturumu bağlı hesapta `/notification` → ilk 20 bildirim gelmeli, chip'lerde `Tümü 20` görünmeli
2. Scroll ile View more tetiklenmeli → ikinci sayfa (offset=20) yüklenmeli, toplam 40, sonra 45
3. Filtre `Yeni Bağlantılı Anime` → sadece 39 kayıt gösterilmeli, rozetler güncellenmeli
4. `Yayında` → 4 kayıt
5. Poster kontrol: Fate/strange Fake gibi anime bildirimlerinde çan yerine poster
6. Sayfalama sonu: `hasMore=false` olduğunda "Tekrar dene" hatası çıkmamalı
7. Arşiv: uygulama kapatılıp açılınca 45 kayıt korunmalı

## 5. Değişen Dosyalar Listesi (Zip içeriği)

```
app/src/main/java/com/kitsugi/animelist/data/notifications/NotificationArchiveStore.kt
app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationCategory.kt          (YENİ)
app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationWebEndpoints.kt     (YENİ)
app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsClient.kt
app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsWebPager.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsViewModel.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
PLAN_TASK_MAL_BILDIRIM_WEB_UCLARI_TAM_FIX_2026-10-21.md (bu dosya)
```

## 6. Riskler / Notlar

- MAL sayfa yapısı değişirse `window.MAL.notification` marker'ı güncellenmeli
- `category` query param'ı MAL tarafından yok sayılırsa istemci filtresi devreye girer → güvenli
- Eski arşiv dosyaları yeni alanlar olmadan da okunur (null default)

---
Hazırlayan: Kitsugi Agent (Arena) — 2026-10-21

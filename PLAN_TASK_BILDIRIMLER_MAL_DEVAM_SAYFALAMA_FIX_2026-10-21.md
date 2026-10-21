# Plan / Task — Bildirimler → MyAnimeList Devam Yükleme (Sayfalama) ve Kategori Tamamlama Düzeltmesi

**Tarih:** 2026-10-21 · **Sürüm:** v2.4.335 · **Durum:** Tamamlandı

---

## Kullanıcı Bildirimi ve Ekran Görüntüsü Kanıtı

- **Ekran:** `Bildirimler → MyAnimeList` (`v2.4.334`)
- **Gözlenen Belirtiler:**
  - Liste en alta kaydırıldığında `Tümü 28` (`Yeni Bağlantılı Anime 22`, `Yayında 2`, `Arkadaşlık İstekleri 1`, `Profil Yorumları 0`) noktasında takılıp kalıyor; son kart `Girls Band Cry Movie 2 — Sep 23, 2025 5:40 AM` olarak kalıyor ve daha eski 20 bildirim hiç yüklenmiyor.
  - Durum çubuğunda sürekli ağ trafiği (`66,2 KB/s`) dönmesine rağmen listeye tek bir yeni kayıt eklenmiyor ve hata mesajı da gösterilmiyor.
  - MAL üzerinde gerçek bildirim dağılımı: Toplam `45` (`Yeni Bağlantılı Anime 39`, `Yayında 4`, `Arkadaşlık İstekleri 1`, `Profil Yorumları 1`).

---

## Tespit Edilen Kök Nedenler (Özet)

1. **Kimlik Ön Eki Uyuşmazlığı Nedeniyle Sonsuz Döngü (`mal_web_` vs Ham ID):**
   - `KitsugiNotificationsViewModel` kart kimliklerini `"mal_web_${item.id}"` ön ekiyle saklarken, `loadMalMore()` bu kümeyi olduğu gibi `MalNotificationsClient.fetchMore(knownIds = ...)` metoduna gönderiyordu.
   - `MalNotificationsClient` ise ham `"${item.id}"` ile karşılaştırma yaptığı için `?offset=20`, `?offset=40` gibi tüm aday URL'lerden dönen 1. sayfa kopyalarını `"yeni kayıt"` sanıp anında döndürüyordu (`MalNotificationsWebPager`'a hiç geçemiyordu).
   - Dönen kopyalar ViewModel'de `.distinctBy { it.id }` ile eleniyor, liste boyutu `28`'de sabit kalıyor, `hasMore = true` korunduğu için `SourceNotifPage` `LaunchedEffect(lastVisible, total, hasMore, isLoadingMore)` sonsuz ağ döngüsüne giriyordu.

2. **WebView `MalNotificationsWebPager` Kök Kapsayıcıya Yanlış Tıklama Hatası:**
   - `querySelectorAll('button, a, div, span, ...').find(...)` belge ağacını yukarıdan aşağıya taradığı için, tüm sayfayı saran `<div id="myanimelist">` / `#content` kapsayıcısının `innerText` alanında alt düğümden gelen `"View more"` metnini görüp gerçek buton yerine dış kapsayıcı `<div>` elemanına tıklıyordu.

3. **Yalnızca `window.MAL.notification` Okuma Körlüğü:**
   - MAL'ın `"View more"` aksiyonu `window.MAL.notification` statik ilk-sayfa nesnesini değiştirmek yerine XHR/`fetch()` yanıtları, Vue bileşen durumu (`__vue__.$data`) ve/veya `#content li.notification-item` DOM listesini güncelliyor. Eski betik yalnızca `window.MAL.notification` okuduğu için eklenen eski kayıtları hiç toplayamıyordu.

4. **Eski Takvim Yedek Kayıtlarının (`mal_<id>_<ep>`) Arşivde Kalması:**
   - Web oturumu öncesinden kalan 3 adet takvim kaynaklı `mal_...` kaydı `NotificationArchiveStore` birleştirmesinde korunuyor, `Tümü` sayısını `25 + 3 = 28` olarak şişiriyor ve `typeIdentifier` taşımadığı için kategori filtreleriyle uyuşmuyordu.

---

## Uygulama Planı ve Görev Listesi (Tasks)

- [x] **Task 1 — `MalNotificationsClient` Kimlik Normalizasyonu, DOM + Kenar Çubuğu Ayrıştırıcısı ve Kategori Tarama Yedeği**
  - `WEB_ID_PREFIX = "mal_web_"`, `rawNotificationId(...)` ve `normalizeKnownIds(...)` eklendi; `fetchMore` her zaman ham MAL kimlikleriyle karşılaştırma yapar.
  - `.notificaiton-category-nav` (MAL'ın gerçek HTML yazım hatası dâhil) sol menüsünden kategori sayaçları (`All Notifications`, `New Related Anime`, `Now Airing`, `Friend Requests`, `Profile Comments` vb.) ve kategori URL'leri ayrıştırıldı (`parseSidebarMetadata`).
  - `#content li.notification-item:not(.empty)` satırları için tam DOM ayrıştırıcısı (`parseDomNotifications`) eklendi ve gömülü JSON ile `signatureKey()` üzerinden tekilleştirilerek birleştirildi.
  - `.notification-pagination` bağlantıları güvenli biçimde okundu (`#` ve `javascript:` sahte linkleri reddedildi).
  - Doğrudan URL adaylarının kopya döndürdüğü tespit edildiğinde süreç içi belleğe kaydedilip (`directUrlPaginationWorks = false`) tekrar tekrar boşa HTTP isteği atılması engellendi; WebView sonrası hâlâ eksik kategori varsa paralel kategori sayfası taraması (`sweepCategoryPages`) eklendi.

- [x] **Task 2 — `MalNotificationsWebPager` Gerçek Görünüm Alanı, XHR/`fetch` Kancası, Vue + DOM Toplayıcı ve Yaprak Düğüm Tıklayıcısı**
  - Görünmez `WebView`'a gerçek `measure`/`layout` (`1080×1920`) verildi (`IntersectionObserver` ve görünürlük kontrollerinin çalışması için).
  - `onPageStarted`, `onPageCommitVisible` ve `onPageFinished` aşamalarında erken `XMLHttpRequest` + `window.fetch` JSON yakalama kancası (`installNetworkHookScript`) kuruldu.
  - Vue 2/3 bileşen durumu (`__vue__`, `__vueParentComponent`) ve `#content` DOM anlık görüntüsü (snapshot) toplama katmanı eklendi; sayfa geçişlerinde önceki yığınların kaybolmaması sağlandı.
  - `"View more"` / `.notification-pagination` seçicisi yalnızca en içteki (leaf) tıklanabilir kontrolü seçecek şekilde yeniden yazıldı; kartların veya `#myanimelist` kök kapsayıcısının tıklanması kesin olarak engellendi.

- [x] **Task 3 — `NotificationArchiveStore` Eski Kayıt Temizliği (`retainOldWhere`)**
  - `mergeSourceAsync` ve `mergeLocked` metotlarına isteğe bağlı `retainOldWhere: ((ArchivedNotif) -> Boolean)?` parametresi eklendi; gerçek MAL web akışı aktifken eski `mal_` takvim kayıtları atomik olarak temizlenir.

- [x] **Task 4 — `KitsugiNotificationsViewModel` İlerleme Koruması, Otomatik Devam Tamamlama, Tarih ve Kapak Zenginleştirme**
  - `loadMalMore()` içine kesin ilerleme kontrolü (`mergedUnfiltered.size > beforeCount`) eklendi; yeni öğe gelmezse `hasMore = false` yapılarak sonsuz döngü imkânsız hale getirildi.
  - İlk sayfa ekrana çizildikten hemen sonra sol menü toplamına göre eksik kalan eski bildirimler arka planda otomatik tamamlanır.
  - `parseMalDisplayTimestampMs` (`Locale.US` tarih ayrıştırıcısı) ve yerel listeden anime kapak görseli (`malCoverLookup`) desteği eklendi.

- [x] **Task 5 — `KitsugiNotificationsScreen` Ön Yükleme Penceresi, Kategori Sayaçları ve Hata Durumu Arayüzü**
  - `SourceNotifPage` ön yükleme penceresi `total - 8`'e genişletildi ve seçili kategori filtresi henüz boşken `hasMore == true` ise otomatik devam yüklemesi tetiklendi.
  - `malCounts` hesaplaması `malState` değişimlerine reaktif hale getirildi ve boş listede `loadMoreError` varken `Tekrar Dene` kartı gösterildi.

- [x] **Task 6 — Birim Testleri ve Sürüm Notları (`v2.4.335`)**
  - `MalNotificationsClientTest.kt` ve `NotificationArchiveStoreTest.kt` kapsamlı regresyon testleriyle güncellendi.
  - `app/build.gradle.kts` (`2.4.335`), `release_notes_v2.4.335.md` ve `RELEASE_NOTES.md` güncellendi.

---

## Değişen / Eklenen Dosyalar Listesi

1. `app/build.gradle.kts`
2. `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt` *(ilk `time-pos` bildiriminin `lastPositionNotifyAtMs = 0L` başlangıç değeri yüzünden düşmesi düzeltildi)*
3. `app/src/main/java/com/kitsugi/animelist/data/notifications/NotificationArchiveStore.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationWebEndpoints.kt`
5. `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsClient.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsWebPager.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsScreen.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsViewModel.kt`
9. `app/src/test/java/com/kitsugi/animelist/data/notifications/NotificationArchiveStoreTest.kt`
10. `app/src/test/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsClientTest.kt`
11. `RELEASE_NOTES.md`
12. `release_notes_v2.4.335.md`
13. `PLAN_TASK_BILDIRIMLER_MAL_DEVAM_SAYFALAMA_FIX_2026-10-21.md`
14. `KOK_NEDEN_RAPORU_BILDIRIMLER_MAL_DEVAM_SAYFALAMA_2026-10-21.md`

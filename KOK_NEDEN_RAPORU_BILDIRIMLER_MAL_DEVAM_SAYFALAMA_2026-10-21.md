# Kök Neden Raporu — MyAnimeList Bildirimleri Devam Yükleme (Sayfalama) Sorunu

**Tarih:** 2026-10-21 · **Sürüm:** v2.4.335

---

## 1. Belirti Matematiği (`v2.4.334` Ekran Görüntüsü Doğrulaması)

Kullanıcının `Bildirimler → MyAnimeList` ekran görüntüsündeki her sayı doğrudan kodtaki dört hatanın birleşimiyle birebir örtüşmektedir:

- **MAL Gerçek Hesap Bildirimleri:** Toplam **45** kayıt (`Yeni Bağlantılı Anime: 39`, `Yayında: 4`, `Arkadaşlık İstekleri: 1`, `Profil Yorumları: 1`).
- **MAL İlk Web Sayfası (`https://myanimelist.net/notification`):** En yeni **25** kaydı döndürür (`22` Yeni Bağlantılı Anime + `2` Yayında + `1` Arkadaşlık İsteği = **25**; 25. kayıt: `Girls Band Cry Movie 2 — Sep 23, 2025 5:40 AM`).
- **Arşivdeki Eski Takvim Kayıtları:** Web girişi öncesinden `NotificationArchiveStore` içinde kalan **3** adet `mal_<animeId>_<bölüm>` kaydı `Tümü` sekmesine eklenerek `25 + 3 = 28` sayısını oluşturur; bu 3 kaydın `typeIdentifier` alanı `null` olduğu için kategori çiplerinde yer almaz.
- **Sürekli `66,2 KB/s` Ağ Trafiği:** `loadMalMore()` her seferinde aynı 25 kaydı indirip `"yeni kayıt bulundu"` sanarak `hasMore = true` bırakır ve sonsuz döngüde `myanimelist.net/notification?...` sayfalarını indirir.

---

## 2. Dört Birleşik Kök Neden

### Kök Neden 1 — `mal_web_` Kimlik Ön Eki Uyuşmazlığı (Sonsuz Döngünün Ana Sebebi)
- `KitsugiNotificationsViewModel` her web bildirimini `id = "mal_web_${item.id}"` biçiminde oluşturuyordu.
- `loadMalMore()` ise `knownIds = malUnfilteredItems.map { it.id }.toSet()` kümesini ön eki silmeden doğrudan `MalNotificationsClient.fetchMore(...)` metoduna veriyordu.
- `MalNotificationsClient.fetchMore(...)` aday URL'leri (`?offset=20`, `?offset=40`, ...) denerken `candidate.items.any { it.id !in knownIds }` kontrolü yapıyordu. `"12345" !in setOf("mal_web_12345")` **her zaman `true`** çıktığı için:
  1. İlk aday URL (`?offset=20`, MAL bu parametreyi yok sayıp 1. sayfayı tekrar döndürür) anında `"yeni sayfa bulundu"` kabul edilip döndürülüyordu.
  2. Gerçek tarayıcı devam motoru olan `MalNotificationsWebPager`'a **hiçbir zaman ulaşılamıyordu**.
  3. ViewModel'e dönen 25 kayıt yeniden `"mal_web_..."` kimliğine çevrilip `.distinctBy { it.id }` ile eleniyor, `malUnfilteredItems` yine `28` kalıyor, `malPage` artıyor, `hasMore = true` kaldığı için Compose `LaunchedEffect` anında bir sonraki `loadMalMore()` çağrısını tetikliyordu.

### Kök Neden 2 — `MalNotificationsWebPager` Kök `<div>` Kapsayıcısına Tıklama Hatası
- JavaScript betiği `"View more"` düğmesini bulmak için `Array.from(document.querySelectorAll('button, a, [role="button"], input[type="button"], div, span'))` listesinde `.find(...)` çalıştırıyordu.
- `querySelectorAll` düğümleri **yukarıdan aşağıya (DOM pre-order)** döndürdüğü için, tüm sayfayı saran `<div id="myanimelist">` kapsayıcısı altındaki `"View more"` metnini `innerText` içinde barındırıyor ve `.find(...)` gerçek düğme yerine en dıştaki `<div id="myanimelist">` öğesini seçip ona `.click()` gönderiyordu.

### Kök Neden 3 — Yalnızca `window.MAL.notification` Okunması
- MAL'da `"View more"` tıklandığında sayfa başındaki statik `<script>window.MAL.notification = ...</script>` metni güncellenmez; yeni öğeler XHR/`fetch` JSON yanıtıyla gelir, Vue bileşen durumuna (`__vue__.$data`) eklenir ve `#content li.notification-item` DOM listesine basılır.
- Eski `currentFeed()` yalnızca `window.MAL.notification` okuduğu için eklenen 26..45. bildirimleri göremiyordu.

### Kök Neden 4 — Eski Takvim Yedek Kayıtlarının Arşivde Kalması
- `NotificationArchiveStore.mergeSourceAsync` önceki tüm eski kayıtları koşulsuz koruduğu için web oturumu açıldıktan sonra bile eski `mal_<id>_<ep>` takvim kayıtları listede kalmaya devam ediyordu.

---

## 3. Uygulanan Kalıcı Çözüm Mimarisi

1. **Çift Taraflı Kimlik Koruması + Sıfır İlerleme Kilidi:**
   - `MalNotificationsClient.normalizeKnownIds(...)` tüm `mal_web_` ön eklerini soyar; `KitsugiNotificationsViewModel.loadMalMore()` ise birleştirme sonrası gerçek liste büyümediyse (`mergedUnfiltered.size > beforeCount` değilse) `hasMore = false` yaparak sonsuz döngüyü kesin olarak keser.
2. **Üç Katmanlı WebView + DOM + Kategori Tarama Devam Motoru:**
   - **Katman 1 (WebView Ağı + Vue + DOM):** `MalNotificationsWebPager` gerçek `1080×1920` görünüm alanı kurar, erken `XHR`/`fetch` JSON yanıtlarını yakalar, Vue 2/3 bileşen durumunu tarar, yalnızca yaprak (leaf) `.notification-pagination` / `"View more"` kontrolüne tıklar ve `#content` DOM anlık görüntülerini birleştirir.
   - **Katman 2 (Kategori Sayfası Taraması):** Sol menü (`.notificaiton-category-nav`) toplam sayısına göre hâlâ eksik bildirim kalmışsa `sweepCategoryPages` eksik kategorilerin sayfalarını paralel olarak tarayıp tüm eski bildirimleri tamamlar.
3. **Arşiv Hijyeni, Tarih Sıralaması ve Kapak Görselleri:**
   - Gerçek MAL web oturumu aktifken eski `mal_` takvim kayıtları `retainOldWhere` ile temizlenir; `Sep 23, 2025 5:40 AM` biçimindeki tarihler `parseMalDisplayTimestampMs` ile doğru milisaniyeye çevrilir ve kullanıcının yerel listesindeki kapak görselleriyle eşleştirilir.

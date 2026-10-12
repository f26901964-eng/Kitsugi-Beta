# PLAN / TASK — MAL bildiriminden açılan detay sayfası + bildirimlerin devamı

Tarih: 2026-10-12
Sürüm: v2.4.263

## Belirti (kullanıcı geri bildirimi)

1. Bildirimler > **MyAnimeList** sekmesinde bir karta dokunulduğunda açılan detay sayfasının
   başlığı gerçek anime adı yerine **"New Related Anime"** oluyordu; "bazıları doğru açılıyor
   bazıları yanlış açılıyor" geri bildirimi bununla birlikte geldi.
2. Kullanıcı isteği: bildirim kartı nasıl görünürse görünsün, **detay sayfası normal MAL
   detay sayfası gibi** (normal API üzerinden) açılmalı.
3. Kullanıcı isteği: bildirimler "daha adam akıllı" gelsin ve **kaydırdıkça devamı** görünsün.

## Kök neden

- Bildirim akışındaki `categoryName` MAL'ın **kategori adıdır** ("New Related Anime"; bkz.
  `MalNotificationsClient.Notification.displayTitle()`).
- `KitsugiNotificationsScreen` MAL kartı tıklamasında detay yönlendirmesine `notif.title`
  (yani kategori adı) **başlık olarak** veriliyordu.
- `ApiResultDetailPage.displayResult` başlık seçiminde çağıranın verdiği başlığı API
  başlığına tercih eder:
  `rawTitle = if (result.title.isBlank() || result.title == loading) detail.title else result.title`
  → İçerik doğru yükleniyor olsa bile ekranda **kategori adı** kalıyordu.
- İkinci bir hata kaynağı: medya türü yalnızca `"manga"` metni aranarak belirleniyordu. MAL'ın
  manga evrenindeki **Novel / Light Novel / Manhwa / Manhua / One-shot** türleri "anime"
  sayılıyor, manga kimliği anime aramasına gidiyor ve detay sayfası aynı sayıya sahip
  **başka bir animeyi** açabiliyordu ("bazıları yanlış açılıyor").
- Üçüncü eksik: MAL, okunmuş/eski bildirimleri `window.MAL.notification.historyItems` altında
  döndürür. Parser yalnızca `items` okuduğu için bildirimlerin devamı hiç görünmüyordu;
  ayrıca kaydırınca yeni sayfa istenmiyordu (`hasMore = false` sabitti).

## Uygulanan (v2.4.263)

### 1. Detay yönlendirmesi normal MAL detayı gibi
- `MalNotificationsClient.Notification.mediaTitle`: bildirimin işaret ettiği medyanın
  **gerçek adı** (`anime.title` → `animes[].title`).
- `NotifItem.mediaTitle` alanı eklendi (arşive de yazılır ki eski kayıtlar da doğru açsın).
- `KitsugiNotificationsScreen.openMalNotification()`:
  1. Kimlik (URL'den) varsa → doğrudan normal API detayı (`jikan` kaynağı).
  2. Kimlik yoksa ama ad varsa → `KitsugiNotificationsViewModel.resolveMalMedia()` normal
     arama API'siyle (`JikanSearchClient.searchMALOnly`) kimliği bulur, detay o kimlikle açılır
     (arama sürerken `notif_resolving_media` gösterilir).
  3. Medya bilgisi yoksa (yorum/arkadaşlık/forum) → bağlantı sistem tarayıcısında açılır.

### 2. Medya türü sınıflandırması
- `MANGA_MEDIA_TYPES = manga, novel, manhwa, manhua, one-shot, doujin`.
- Tür payload'da yoksa URL'deki `/manga/` yolundan anlaşılır.

### 3. Bildirimlerin devamı (kaydırdıkça)
- `parseHtml` artık `items` + `historyItems` listelerini aynı şemayla ayrıştırıp `id` ile
  tekilleştirir (kimliksiz geçmiş kaydı uydurulmaz).
- `Feed.page` / `Feed.hasNextPage` + `pageUrlFor(page)`: 1. sayfa parametresiz, sonrakiler
  `?page=N` ile **denenir**. MAL bu akışın sayfalamasını belgelemediği için karar **veriyle**
  verilir: yeni sayfada yeni kimlik yoksa akış biter (`hasMore = false`) ve tekrar kart
  oluşmaz; sayfa hatası liste bozmadan yutulur.
- `KitsugiNotificationsViewModel.writeArchive()` / `archiveAndMerge()`: MAL web akışı da
  yerel arşive yazılır, pencereden düşen bildirimler cihazda kalır.
- `SourceNotifPage`: liste sonuna yaklaşınca `onLoadMore` çağrılır (AniList'teki desenin aynısı).

### 4. Kart düzeni
- Medya bildirimlerinde kart **başlığı medyanın adı**, ikinci satır MAL kategori adı + tür
  (`Umayuru: Full Gate!` / `New Related Anime • ONA`). AniList/Simkl/Kitsu kartlarıyla tutarlı.

## Doğrulama

- [x] Parser ve tür sınıflandırması için birim testleri eklendi/genişletildi
      (`MalNotificationsClientTest`: historyItems birleştirme + tekilleştirme, kimliksiz
      geçmiş kaydı, `pageUrlFor`, `Feed.page`, `mediaTitle`, manga/novel/manhwa türü).
- [x] TR/EN string kaynakları güncellendi (`notif_resolving_media` iki dosyada da var).
- [x] `git diff --check` temiz.
- [ ] **Android Gradle derlemesi ve testler**: bu ortamda Java/JAVA_HOME bulunmadığından
      çalıştırılamadı (önceki sürümlerde de aynı kısıt vardı).
- [ ] Cihazda uçtan uca kontrol:
      1. Bildirimler > MyAnimeList > "New Related Anime" kartı → detay **anime adıyla** ve
         doğru yapımla açılmalı.
      2. Aşağı kaydırınca daha eski bildirimler gelmeli (liste sonu kilitlenmemeli).
      3. Manga/novel bildirimi → detay manga olarak açılmalı (anime kimliğine düşmemeli).

## Sonraki adım adayları (bu sürümde YAPILMADI)

- MAL bildirim JSON'unda medya afişi alanı belgelenmediği için medya kartları hâlâ zil
  ikonuyla görünür; MAL `anime` nesnesine görsel ekliyorsa `imageUrl`/`image_url` savunmacı
  okunabilir (tek ek istek gerekmez).
- `categoryName` İngilizce gelir ("New Related Anime"); istenirse bilinen kategoriler için
  TR/EN etiket eşlemesi eklenebilir.

## Notlar / sınırlar

- MAL'ın bildirim sayfası için belgelenmiş bir sayfalama sözleşmesi bulunamadı (MALClient
  `MalNotificationsQuery`, MAL-Dubs userscript ve GitHub kod aramaları incelendi; yalnızca
  `check-items-as-read.json` uç noktası biliniyor). Bu yüzden `?page=N` **iyimser** bir
  denemedir; desteklenmiyorsa sistem tek istek sonrası kendini kapatır.
- `historyItems` şeması MAL tarafında değişebilir; kimliksiz kayıtlar sessizce atlanır.

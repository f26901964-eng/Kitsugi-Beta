# Bildirim Altyapısı Denetimi — 5 Kaynak (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

Tarih: 2026-10-07  
Başlangıç sürümü: `2.4.190` (commit `de3d6b4`)  
Kapsam: Uygulama içi **Bildirimler** ekranı + arka plan bildirim işçisi (`AiringNotificationWorker`)

## Sonuç (özet)

> "Hangisi gerçekten çalışıyor?" sorusunun kanıtlı cevabı:

| Kaynak | Resmî API'de bildirim ucu var mı? | 2.4.190'daki durum | 2.4.191'deki durum |
|---|---|---|---|
| **AniList** | ✅ **Var** (GraphQL `Page.notifications`, `unreadNotificationCount`) | Çalışıyordu ama **hatalar sessizce yutuluyordu** → "bildirim yok" gibi görünüyordu; okunmamış rozeti kullanılmıyordu | Gerçek hata mesajı gösterilir, okunmamış rozeti eklendi, sayaç sıfırlama yalnızca "Tümü" sekmesinde |
| **MyAnimeList** | ❌ **Yok** (MAL API v2 + resmî uygulama API'si yalnızca anime/manga/liste/kullanıcı uçları sunar) | AniList takviminden "MAL eşleşmesi" üretiliyordu; **takvim haftasına bağlı** olduğu için hafta başında boş görünüyor, sıralama `id`'ye göre yapılıyordu | Kayan **son 7 gün** penceresi, doğru sıralama, ekranın üstünde bu durumu açıklayan bilgi bandı |
| **Simkl / TMDB** | ❌ **Yok** (48 uç noktanın tamamı tarandı: Trending, Calendar, Sync, Scrobble, Playback, Search, AUTH) | **Eski (deprecated) takvim dosyaları** okunuyordu **ve sonuç ilk 50 öğeyle kesiliyordu** → eşleşme neredeyse hiç tutmuyordu; arka planda bölüm başına tekrar bildirim engellenmişti (dizi başına 1 bildirim) | Resmî **v2 takvim** (`{calendar, metadata}`) + kullanıcının **`/sync/all-items/*/watching`** listesi; bölüm başına ayrı anahtar; ±7 gün penceresi |
| **Kitsu** | ❌ **Yok** (JSON:API kaynakları: anime, manga, library-entries, comments, posts…) | Yalnızca "izleme listesinde" satırları vardı (bildirim değil); arka planda hiç çalışmıyordu | Gerçek **`nextRelease`** (sonraki bölüm tarihi) akışı + yeni ayar anahtarı ve arka plan desteği |
| **Shikimori** | ✅ **Var ama kullanılmıyordu**: `GET /api/users/:id/messages?type=notifications` (+ `news`, `inbox`, `private`, `sent`) ve `GET /api/users/:id/unread_messages` | İzleme **geçmişi** (`/history`) "bildirim" gibi gösteriliyordu | **Gerçek bildirim akışı** (notifications + news), okunmamış sayıları, okunmamış işareti; `messages` izni yoksa ne yapılacağını söyleyen bilgi bandı |

**Kritik tarih:** Simkl'in v2 olmayan takvim dosyaları **1 Şubat 2027**'de güncellenmeyi bırakacak. 2.4.190 kodu yalnızca o eski dosyaları okuyordu → bu tarihten sonra Simkl bildirimleri tamamen ölürdü. 2.4.191 v2'ye geçti.

## Yöntem

- Kod tarafı: `KitsugiNotificationsViewModel`, `KitsugiNotificationsScreen`, `KitsugiAniListNotificationClient`, `SimklApiClient.getCalendar`, `KitsuApiClient`, `ShikimoriApiClient`, `AiringNotificationWorker` satır satır incelendi.
- Resmî kaynaklar (canlı çekildi):
  - Simkl: <https://api.simkl.org/> · <https://api.simkl.org/all-endpoints.md> · <https://api.simkl.org/api-reference/calendar> (v2 yolu, deprecation, `{calendar, metadata}` şeması)
  - Shikimori: <https://shikimori.io/api/doc/1.0/users/messages> · <https://shikimori.io/api/doc/1.0/users> (`unread_messages`)
  - MAL: <https://github.com/SuperMarcus/myanimelist-api-specification> (resmî uygulamanın özel API şeması — 617 satır; **"notif" geçen 0 satır**)
  - Kitsu: <https://kitsu.docs.apiary.io/> (kaynak listesi — bildirim kaynağı yok)
- **Sınır:** Bu ortamın ağ izinleri Sağlayıcı alan adlarını (anilist.co, kitsu.app, shikimori.one, api.simkl.com) kapsamıyor; bu yüzden iddialar kaynak kod + resmî API dokümanı kanıtına dayanır. Kullanıcı tarafında **canlı doğrulama** için uygulamaya teşhis aracı eklendi (aşağıya bakın).

## Kullanıcının sorusunun doğrudan cevabı: "Nereden kanaat getireceğim?"

Bildirimler ekranının sağ üst köşesine **Bildirim Teşhisi** (🪲) butonu eklendi. Tek dokunuşla 5 kaynak için **gerçek ağ istekleri** atılır ve her biri için şunlar gösterilir:

- Hesap bağlı mı (token var mı)
- Kullanılan uç nokta
- **HTTP durum kodu**
- Dönen kayıt sayısı
- Süre (ms)
- Hata mesajı ve **ne yapılması gerektiği** (ipucu satırı)

Örnek çıktı satırları:

```
● AniList     ÇALIŞIYOR  graphql.anilist.co (Page.notifications)
              Bağlı · HTTP 200 · Kayıt: 5 · 812 ms
              Son 5 bildirimden 5 kayıt döndü • okunmamış: 3

● MyAnimeList ÇALIŞIYOR  MAL API v2 (bildirim ucu yok) + AniList yayın takvimi
              Bağlı · Kayıt: 2 · 640 ms
              Listenizde 41 MAL kaydı • son 7 günde 2 yayın eşleşmesi

● Shikimori   SORUN      shikimori.one/api/users/:id/(unread_messages|messages)
              Bağlı · HTTP 403 · 310 ms
              Token geçerli ama 'messages' izni yok
              → Kendi Shikimori uygulamanızı 'user_rates messages' izinleriyle oluşturup
                Ayarlar > Platformlar'da bu client bilgileriyle yeniden giriş yapın.
```

Böylece "MAL'da bildirim geliyor mu gelmiyor mu" belirsizliği biter: MAL için hangi kaydın sayıldığı, kaç eşleşme bulunduğu ve **neden 0 olduğu** (ör. "izleme listenizde MAL kaynaklı 'İzleniyor' kaydı yok") yazılı olarak görünür.

## Bulunan ve düzeltilen hatalar (kanıtlı)

### 1. AniList — hata sessizce yutuluyordu (v2.4.190)
`KitsugiAniListNotificationClient.fetchNotifications()` istisnaları yakalayıp **boş liste** döndürüyordu:
```kotlin
runCatching { ... }.getOrElse { KitsugiNotificationPage(emptyList(), false, page) }
```
Sonuç: token süresi dolduğunda, GraphQL hatasında veya ağ kesintisinde ekran "Yeni bildirim yok" diyordu. **Düzeltme:** GraphQL `errors` dizisi ayrıştırılıp `AniListNotificationException` fırlatılıyor; ViewModel bunu gerçek hata metni olarak gösteriyor. Ayrıca `resetNotificationCount` artık yalnızca "Tümü" sekmesinin ilk sayfasında gönderiliyor.

### 2. MAL — hafta sınırı ve sıralama hatası
Eski kod AniList haftalık takvimini (`fetchWeeklySchedule()`, Pazartesi–Pazar) çekip **`triggerMs > now - 7 gün`** koşuluyla süzüyordu; ama takvim yalnızca içinde bulunulan haftayı kapsadığı için hafta başında liste boşalıyordu. Sıralama da `sortedByDescending { it.id }` (string ID) idi. **Düzeltme:** kayan pencere (`fetchAiringWindow(now-7g, now+2s)`), `airingAt`'e göre sıralama, "yayınlandı/yayınlanacak" ayrımı, eşleşme sayısını ve nedeni yazan bilgi bandı.

### 3. Simkl — deprecated uç nokta + 50 öğe kesintisi (en ciddisi)
```kotlin
// 2.4.190
val url = "https://data.simkl.in/calendar/${cdnType}.json"
for (i in 0 until minOf(jsonArray.length(), 50)) { ... }   // ← ilk 50 öğe
```
- Eski (v2 olmayan) dosyalar **1 Şubat 2027'de güncellenmeyi bırakacak** (Simkl resmî dokümanı).
- Kataloglar TV + anime + film olarak ayrı ayrı ilk 50 öğeye kesiliyordu; bir kullanıcının izleme listesindeki yapımın o 50 öğe içinde olma olasılığı çok düşüktür → "Simkl bildirimi gelmiyor" şikâyetinin ana nedeni.
- Arka planda ayrıca `distinctBy { it.malId }` (bu alan aslında Simkl ID'si) kullanılıyor ve anahtar bölüm içermediği için **dizi başına yalnızca 1 bildirim** gönderilebiliyordu.

**Düzeltme:** Yeni `SimklCalendarClient` (resmî v2 şeması `{calendar:[…], metadata:{"<simkl_id>":…}}`, `metadata` anahtarları string, zaman damgaları UTC) + `GET /sync/all-items/{shows|anime|movies}/watching` ile kullanıcının izleme listesi; eşleşme `simkl / tmdb / mal` ID'lerinin üçüyle; bölüm başına benzersiz anahtar; ±7 gün penceresi; 24 saatlik bildirim penceresi.

### 4. Kitsu — bildirim yerine liste; arka planda hiç yok
Kitsu'nun genel API'sinde bildirim kaynağı yoktur. Ancak anime kaynağındaki **`nextRelease`** alanı sonraki bölümün yayın zamanını verir. 2.4.190 bunu hiç okumuyordu; ekran yalnızca "İzleme Listesinde" satırları gösteriyor, arka planda hiç kontrol yapılmıyordu. **Düzeltme:** `KitsuLibraryEntry.nextRelease` ayrıştırılıyor; akışta "Bölüm N • tarih", "yayınlandı/yaklaşan" durumu; yeni `kitsu_notifications_enabled` ayarı ve arka plan işçisi desteği.

### 5. Shikimori — geçmiş, bildirim sanılıyordu
Gerçek uçlar (resmî doküman) mevcut:
```
GET /api/users/:id/messages?type=notifications   (ayrıca inbox, private, sent, news)
GET /api/users/:id/unread_messages               → { messages, news, notifications }
```
İkisi de `messages` OAuth iznini ister. 2.4.190 ise `/api/users/:id/history` çağırıp bunu "bildirim" olarak etiketliyordu. **Düzeltme:** `ShikimoriApiClient.fetchMessages()/fetchUnreadCounts()`, gerçek bildirim akışı, okunmamış rozeti, `messages` izni yoksa açıklayıcı bant + geçmişe güvenli düşüş. Ayrıca:
- OAuth kapsamı artık client_id'ye göre seçiliyor: paylaşılan (varsayılan) Kitsugi uygulaması → `user_rates`; kullanıcının kendi uygulaması → `user_rates messages` (`ShikimoriApiClient.scopesFor`).
- "Yeni uygulama oluştur" bağlantısı artık `user_rates messages` izinlerini ön-dolduruyor.

> Not: Varsayılan paylaşılan istemci ile `messages` izni teknik olarak istenemez (Shikimori "invalid scope" döner), bu yüzden kullanıcının kendi uygulama bilgilerini girmesi gerekir. Ekran bunu yazılı olarak anlatır.

### 6. Sekme/rozet ve "kanıt" eksikleri
- `NotifPlatform.TMDB_SIMKL/KITSU/SHIKIMORI` için `enabled = isXConnected || true` yazılmıştı (yanıltıcı). Temizlendi; bağlı olmayan kaynakta giriş çağrısı gösterilir.
- AniList okunmamış sayacı (`unreadNotificationCount`) hiç çağrılmıyordu → artık sekmede rozet olarak gösteriliyor.
- Hiçbir yerde "neden boş" bilgisi yoktu → her kaynak için bilgi bandı eklendi.

## Yeni/Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/remote/SimklCalendarClient.kt` | **Yeni.** v2 CDN takvim + `/sync/all-items/*/watching` |
| `data/notifications/NotificationDiagnostics.kt` | **Yeni.** 5 kaynak için canlı teşhis motoru |
| `ui/screens/notifications/KitsugiNotificationsViewModel.kt` | MAL/Simkl/Kitsu/Shikimori akışları yeniden yazıldı, teşhis durumu, `notice` alanı |
| `ui/screens/notifications/KitsugiNotificationsScreen.kt` | Bilgi bandı, teşhis paneli, okunmamış rozeti, ortak kaynak sayfası |
| `data/remote/KitsugiAniListNotificationClient.kt` | Hata fırlatma + GraphQL `errors` ayrıştırma |
| `data/remote/KitsugiAiringCalendarClient.kt` | `fetchAiringWindow(from, to)` eklendi |
| `data/remote/SimklApiClient.kt` | `getCalendar()` `@Deprecated` (yeni istemciye yönlendirildi) |
| `data/auth/KitsuApiClient.kt` | `nextRelease` ayrıştırma |
| `data/auth/ShikimoriApiClient.kt` | `fetchMessages`, `fetchUnreadCounts`, `messages` izni, `scopesFor` |
| `core/notifications/AiringNotificationWorker.kt` | Simkl düzeltmesi, Kitsu + Shikimori desteği, MAL için kayan pencere |
| `data/settings/AppSettings.kt`, `SettingsDataStore.kt`, `KitsugiPreferencesSettingsDialog.kt`, `AppRootSettingsExtras.kt`, `SettingsScreen*.kt` | `kitsu_notifications_enabled`, `shikimori_notifications_enabled` anahtarları ve arayüz anahtarları |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Bildirim açıklama/teşhis metinleri (TR + EN) |
| `docs/SETTINGS_KEYS.md` | Bildirim anahtarları tablosu güncellendi |

## Doğrulama adımları (kullanıcı tarafı)

1. **Bildirimler** ekranını aç → sağ üstteki 🪲 → *Testi Çalıştır*.
2. Her kaynak için satırı oku: `ÇALIŞIYOR / SORUN`, HTTP kodu, kayıt sayısı ve ipucu.
3. Bağlı olmayan kaynak için ipucu satırı "Ayarlar > Platformlar'dan giriş yapın" der.
4. Shikimori'de `403` görürseniz: kendi Shikimori uygulamanızı `user_rates messages` izinleriyle oluşturup Ayarlar'da client bilgilerini girin ve yeniden giriş yapın.
5. Arka plan bildirimleri için: Ayarlar → Tercihler → Bildirimler'de ilgili anahtarı açın (Android 13+ bildirim izni istenir).

## Bilinen sınırlar / sonraki adımlar

- **MAL ve Simkl'de kişisel bildirim yoktur** — bu bir hata değil, sağlayıcı kısıtıdır. Ekran bunu artık açıkça yazar.
- Arka plandaki Simkl bölümü hâlâ `SimklImportManager.fetchAllLists` + `deleteBySource("simkl")` + `insertAll` yapıyor (ağırdır ve yerel ilerlemeyi sıfırlayabilir). Ayrı bir iş olarak sıralı fark (delta) senkronizasyona çevrilmelidir.
- Simkl'in v2 olmayan dosyaları 1 Şubat 2027'de kapanıyor; bu paket v2'ye geçtiği için ek iş gerekmiyor. Simkl AUTH V1 ise ~Nisan 2027'de emekli oluyor (OAuth V2 / device flow'a geçiş ayrı bir iş).
- AniList `unreadNotificationCount` rozeti yalnızca ekran açıkken tazelenir; arka plan rozeti (launcher badge) eklenmedi.

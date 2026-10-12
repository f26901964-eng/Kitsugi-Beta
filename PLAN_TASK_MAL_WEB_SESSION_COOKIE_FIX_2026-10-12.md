# PLAN / TASK — MAL bildirimleri: web oturumu neden hiç "bağlı" görünmüyordu

Tarih: 2026-10-12
Sürüm: v2.4.262

## Belirti
Kullanıcı MyAnimeList hesabını uygulama içinden bağlamış olmasına rağmen Bildirimler > MyAnimeList
sekmesinde şu ekran görülüyordu:

- Kart: **"MAL web oturumunu bağla"**
- Bilgi şeridi: "MAL web oturumu bağlı değil; gerçek bildirimleri görmek için üstteki düğmeden bir kez
  giriş yap. Şimdilik uygulama takvimi gösteriliyor…"
- Liste: gerçek MAL bildirimleri yerine 7 günlük yayın takvimi (1 yayın • 99 takip edilen yapım)

## Kök neden
`MalWebSessionStore.isAuthenticatedCookie()` MAL'ın **var olmayan** bir çerezini arıyordu:

```kotlin
.any { it.equals("is_signed_in=1", ignoreCase = true) }   // eski hatalı kontrol
```

MAL'ın web sitesi `is_signed_in` adlı bir çerez yazmaz. MAL giriş yapılmış oturumu şu çerezlerle taşır:

| Çerez | Anlamı |
| --- | --- |
| `MALHLOGSESSID` | Giriş yapılmış kullanıcı oturumu (login.php / OAuth authorize sayfası) |
| `MALSESSIONID` | Oturum kimliği; giriş yapılmışken de bulunur |
| `is_logged_in=1` | MAL arayüzünün yazdığı yardımcı bayrak (tek başına yeterli değildir) |

Bu nedenle `authenticatedCookieHeader()` her zaman `null` dönüyordu:

1. OAuth ile MAL bağlı olsa ve WebView'de gerçek bir MAL oturumu dursa bile `loadMal()` beslemeyi
   hiç denemiyordu.
2. Doğrudan yayın takvimi yedeğine düşüyor, `requiresMalWebLogin = true` işaretleniyordu.
3. Web giriş penceresi (`KitsugiWebViewDialog`) da oturumu doğrulamak için aynı hatalı kontrolü
   kullandığından, giriş yapılsa bile pencere kendiliğinden kapanmıyordu.

Kanıt: MALClient (Drutol) `MALHLOGSESSID` + `is_logged_in=1` ile oturum kurar ve bildirimleri
`myanimelist.net/notification` sayfasındaki `window.MAL.notification` JSON'undan okur.

## Düzeltme
1. **`MalWebSessionStore`** — gerçek çerezler tanınır:
   - Boş olmayan `MALHLOGSESSID` veya `MALSESSIONID` → oturum var.
   - `is_logged_in` / `is_signed_in` bayrakları tek başına oturum SAYILMAZ (eski hata tam olarak
     buydu); yalnızca teşhis satırında raporlanır. Çıkış sonrası `MALHLOGSESSID=; is_logged_in=1`
     kalıntısı da oturum sayılmaz.
   - Yeni `browserCookieHeader()`: kavanozdaki TÜM MAL çerezleri (oturum doğrulanmadan).
   - Yeni `presentCookieNames()`: teşhis için yalnızca çerez ADLARI; değerler asla dışarı verilmez.
2. **`KitsugiNotificationsViewModel.loadMal()`** — karar tahminle değil MAL'a sorularak verilir:
   kavanozda MAL çerezi varsa besleme bir kez denenir; MAL giriş sayfasına düşerse oturum gerçekten
   yoktur. `requiresMalWebLogin` artık yalnızca gerçek "oturum gerekli" sonucunda işaretlenir;
   sayfa ayrıştırma hatası bunun yerine kendi bildirim metnini gösterir.
3. **`MalNotificationsClient`**:
   - `fetch()` içindeki "çerez adına bakarak reddet" kapısı kaldırıldı; yalnızca boş çerez reddedilir.
   - MAL giriş formunu **200** ile döndürürse (`action="/login.php"`, `loginForm`, `csrf_token`)
     artık `MalNotificationsParseException` değil `MalWebSessionRequiredException` fırlatılır.
4. **`KitsugiNotificationsScreen`** — otomatik web oturumu: MAL OAuth bağlı olduğu hâlde web oturumu
   gerçekten yoksa (ör. giriş harici tarayıcıda tamamlandıysa) giriş penceresi kullanıcıyı aramaya
   bırakmadan **bir kez** kendiliğinden açılır; giriş tamamlanınca pencere kapanır ve bildirimler
   otomatik yüklenir. Kart metni bağlantı durumuna göre "bağla / yenile" olarak doğru gösterilir.
5. **`NotificationDiagnostics`** — MAL satırı artık bulunan çerez adlarını raporlar
   (`MALHLOGSESSID`, `is_logged_in=1`, …) ve "is_signed_in=1 cookie missing" yanıltıcı hatası
   kaldırıldı.
6. **Metinler** — TR/EN: `notif_notice_mal_web_parse_failed` eklendi, oturum gerekli metni ve giriş
   ipucu yeni otomatik akışa göre güncellendi.
7. **Testler** — `MalWebSessionStoreTest` gerçek MAL çerezleri, bayrak kalıntısı ve teşhis adları
   için yeniden yazıldı; `MalNotificationsClientTest` giriş formu (200) tespiti ile genişletildi.

## Doğrulama
- [x] `git diff --check` temiz; Kotlin dosyalarında parantez/blok dengesi doğrulandı.
- [ ] GitHub Actions `🏗️ Android Derleme Doğrulama` iş akışı: derleme + birim testleri (push sonrası).
- [ ] Cihazda uçtan uca: MAL hesabı bağlı → Bildirimler > MyAnimeList → gerçek bildirim kartları.
- [ ] Oturum gerçekten yoksa: giriş penceresi bir kez açılır, giriş sonrası liste otomatik dolar.

## Notlar / kalan işler
- MAL API v2 kişisel bildirim ucu sunmadığı için web oturumu zorunludur; OAuth bearer token web
  isteğine gönderilmez.
- OAuth hesabı ile web oturumundaki hesabın aynı olduğu henüz katı biçimde karşılaştırılmıyor
  (teşhis satırı hangi çerezlerin bulunduğunu gösterir).
- Bildirimleri okundu işaretleme ucu (`/notification/api/check-items-as-read.json`) henüz
  kullanılmıyor; kartlar salt okunur listedir.

# PLAN / TASK — MAL bildirimlerinde OAuth ⇄ Web oturumu hesap eşlemesi

Tarih: 2026-10-12 · Hedef sürüm: **v2.4.262**

## Sorun

MAL bildirimleri iki ayrı kimlikle çalışır:

| Kimlik | Nerede durur | Nasıl okunur |
| --- | --- | --- |
| **OAuth hesabı** | `ExternalAuthManager` içindeki MAL erişim jetonu | `GET api.myanimelist.net/v2/users/@me` → `name` |
| **Web oturumu** | WebView çerez deposu (`is_signed_in=1`) | `myanimelist.net/notification` HTML'indeki profil bağlantısı |

Normal akışta ikisi aynı in-app WebView oturumundan doğar; ancak kullanıcı WebView'de **başka bir
MAL hesabı** bırakmışsa bildirim sayfası o yabancı hesabın bildirimlerini döndürür. v2.4.261'de bu
kontrol yoktu: kullanıcı, bağlı hesabının sandığı listede aslında başkasının bildirimlerini
görüyordu (sessiz kimlik karışması).

## Çözüm

1. **Web kullanıcı adı:** `MalNotificationsClient` bildirim HTML'inden oturum sahibini okur.
   - 1. geçiş: sayfa başlığındaki profil bağlantısı (`<a …header-profile…>` → `/profile/{ad}`).
     Gerçek MAL sayfalarında bu sınıfın varlığı `Drutol/MALClient` gibi üçüncü taraf istemcilerde
     de doğrulandı.
   - 2. geçiş (yedek): sayfada en sık geçen profil bağlantısı — başlıkta tekrar eden ad
     oturum sahibidir.
   - `window.MAL.notification` JSON bloğu taramadan **çıkarılır**; aksi hâlde bildirimi gönderen
     kişiler (arkadaş, yorumcu) oturum sahibi sanılırdı.
   - Ayrılmış yollar (`/profile/login` vb.) ve salt sayısal değerler kullanıcı adı sayılmaz.
2. **OAuth kullanıcı adı:** `MalImportManager.fetchUserName()` `users/@me` çağrısından **ham**
   adı döndürür. `fetchUserProfile()` bilinçli olarak kullanılmaz: o işlev ad bulunamadığında
   yerelleştirilmiş "MAL kullanıcısı" metnine düşer ve karşılaştırmayı sürekli "eşleşmiyor"
   yapardı.
3. **Karar:** `MalAccountMatch.status(oauthUsername, webUsername, oauthAvailable)` →
   `MATCHED` · `MISMATCHED` · `UNVERIFIED` · `NO_OAUTH_ACCOUNT` (saf Kotlin, `app/src/test`
   içinde test edilir).
4. **Davranış:**
   - `MATCHED` → bildirimler normal gösterilir.
   - `MISMATCHED` → **bildirimler gösterilmez**; "Bağlı OAuth hesabı ile web oturumu eşleşmiyor,
     lütfen doğru hesapla giriş yapın." uyarısı + iki hesabın adı + "Oturumu temizle ve yeniden
     giriş yap" düğmesi.
   - `UNVERIFIED` → liste gösterilir, "web oturumunun hangi hesaba ait olduğu doğrulanamadı"
     notu düşülür. *Bilerek katı davranılmadı:* sayfa yapısı değişirse özellik tamamen kilitlenmesin.
   - `NO_OAUTH_ACCOUNT` → OAuth bağlı değil, yalnızca web oturumuyla çalışılıyor; ek uyarı yok.
5. **Oturum temizleme:** `MalWebSessionStore.clearSession()` yalnızca `myanimelist.net`
   çerezlerini tek tek süresi geçmiş olarak yazar. `CookieManager.removeAllCookies()`
   kullanılmaz — o çağrı diğer platformların oturumlarını da siler. OAuth jetonlarına dokunulmaz.

## Değişen dosyalar

| Dosya | Değişiklik |
| --- | --- |
| `data/remote/malweb/MalNotificationsClient.kt` | `Feed.sessionUsername`, `parseSessionUsername()` + yardımcıları |
| `data/remote/malweb/MalAccountMatch.kt` | **Yeni** — karşılaştırma mantığı (saf Kotlin) |
| `data/remote/malweb/MalWebSessionStore.kt` | `clearSession()` / `clearSessionBlocking()` |
| `data/auth/MalImportManager.kt` | `fetchUserName()` (ham `users/@me` adı) |
| `ui/screens/notifications/KitsugiNotificationsViewModel.kt` | doğrulama, `malAccountMismatch` durumu, `clearMalWebSession()` |
| `ui/screens/notifications/KitsugiNotificationsScreen.kt` | eşleşmeme kartı + oturum temizleme akışı |
| `data/notifications/NotificationDiagnostics.kt` | teşhis satırına kimlik özeti ve uyarı |
| `res/values/strings.xml`, `res/values-en/strings.xml` | TR/EN metinleri |
| `app/src/test/.../MalNotificationsClientTest.kt` | oturum sahibi ayrıştırma testleri |
| `app/src/test/.../MalAccountMatchTest.kt` | **Yeni** — durum testleri |

## Doğrulama

- [x] `parseSessionUsername` birim testleri (başlık bağlantısı, sıklık yedeği, JSON dışlama, ayrılmış adlar).
- [x] `MalAccountMatch` durum testleri (büyük/küçük harf, boşluk, jeton yok).
- [x] Ayrıştırıcı mantığı Python ile birebir simüle edilerek senaryolar doğrulandı.
- [ ] Android Gradle derlemesi + birim testleri: GitHub Actions (`android-build.yml`) — yerel
      ortamda JDK/Android SDK yok.
- [ ] Gerçek MAL hesabıyla cihazda uçtan uca kontrol (özellikle başka hesapla oturum açıkken
      uyarının çıkması ve "Oturumu temizle" sonrası doğru giriş).

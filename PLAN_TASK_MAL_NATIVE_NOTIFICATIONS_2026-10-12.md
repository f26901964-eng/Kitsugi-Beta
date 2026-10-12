# PLAN / TASK — MAL hesap bildirimlerini yerel ekranda gösterme

Tarih: 2026-10-12

## Hedef
Kitsugi'nin MyAnimeList bildirim sekmesinde, MAL hesabının gerçek bildirimlerini WebView'de sayfa açmadan yerel bildirim kartları olarak göstermek.

## Uygulanan
- MAL OAuth/App API token'ı, uygulamanın bağlı MAL hesabı ve liste/profil işlemleri için ayrı kalır.
- MAL API v2'de kişisel bildirim ucu bulunmadığından `https://myanimelist.net/notification` doğrudan OkHttp ile alınır; sayfadaki `window.MAL.notification` JSON'u ayrıştırılır.
- Web isteği, Android WebView `CookieManager` içindeki `is_signed_in=1` oturum çerezini kullanır. OAuth Bearer token web isteğine gönderilmez; bildirim sayfası WebView'de açılmaz.
- Mevcut in-app OAuth WebView ve ek oturum gerektiğinde açılan giriş WebView'i Android'in paylaşılan WebView çerez deposunu kullanır. Giriş ekranı yalnızca oturum eksik/geçersiz olduğunda açılır.
- Bildirimler yerel modele çevrilir: okundu durumu, tarih, metin, kullanıcı görseli, MAL hedef bağlantısı ve bulunabilen anime/manga kimliği.
- MAL tanılama satırı ile TR/EN arayüz metinleri güncellendi; parser ve oturum çerezi testleri eklendi.

## Hesap eşleşmesi notu
OAuth token ile web oturumu ayrı kimlik doğrulama mekanizmalarıdır. Normal Kitsugi MAL OAuth akışında ikisi aynı in-app WebView oturumundan oluşur; ancak bu değişiklik OAuth `users/@me` kimliği ile web oturumunun kullanıcı adını ayrıca karşılaştırmıyordu. **v2.4.262** ile bu kontrol eklendi: eşleşmeyen hesapta bildirimler gösterilmez ve kullanıcıya "Oturumu temizle ve yeniden giriş yap" sunulur (`PLAN_TASK_MAL_OAUTH_WEB_ACCOUNT_MATCH_2026-10-12.md`).

## Doğrulama
- [x] Bildirim sayfasını WebView'de açan eski eylem kaldırıldı; WebView yalnızca oturum girişi için kullanılır.
- [x] HTML/JSON parser, güvenli MAL bağlantısı ve oturum çerezi için birim testleri eklendi.
- [x] TR ve EN string XML'leri doğrulandı; `git diff --check` temiz.
- [ ] Android Gradle derlemesi ve testleri: ortamda Java/JAVA_HOME bulunmadığından çalıştırılamadı.
- [ ] Gerçek MAL hesabıyla cihaz üzerinde uçtan uca kontrol yapılmalı.
- [x] OAuth hesabı ile web oturumu arasında açık kimlik eşlemesi eklendi (v2.4.262) — ayrıntılı plan: `PLAN_TASK_MAL_OAUTH_WEB_ACCOUNT_MATCH_2026-10-12.md`
